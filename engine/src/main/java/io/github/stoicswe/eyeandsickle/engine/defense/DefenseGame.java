package io.github.stoicswe.eyeandsickle.engine.defense;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The defence minigame — {@code docs/design/19-defence-minigame.md}, played.
 *
 * <h2>⚠ THIS IS THE WHOLE GAME, AND IT DRAWS NOTHING</h2>
 *
 * Every rule of the round lives here: what moves, what hits what, and which of the two outcomes it
 * ends on. The view feeds it {@link Input} once per tick and renders {@link #snapshot()}. That split
 * is not tidiness — it is what makes a <b>real-time arcade round testable without a toolkit</b>, and
 * this project runs exactly one JUnit test that starts JavaFX. A simulation living in the view would
 * be verifiable only by playing it, which for a thirty-second reflex game means it would never be
 * verified at all.
 *
 * <h2>⚠ FIXED TIMESTEP. The tick is not a frame and the two must never be conflated</h2>
 *
 * {@link #tick} advances the world by exactly {@code 1 / DEFENSE_TICKS_PER_SECOND} of a second,
 * whatever the wall clock did. A frame that arrives late advances the world one step, the same as one
 * that arrives on time — so a slow machine plays a <em>slower</em> round rather than one that skips
 * through collisions, and the same seed with the same inputs produces the same round every time.
 *
 * <p>Integrating against measured elapsed time is the obvious alternative and is wrong twice here: it
 * makes the round unreproducible, and it lets a stalled frame teleport a triangle straight through
 * the player without ever occupying a position where the two overlapped.
 *
 * <h2>⚠ Nothing here is eased — {@code ui-design-language.md} §5</h2>
 *
 * Every position is a velocity integrated at a fixed step. That is arithmetic, not a tween, which is
 * what makes a real-time minigame compatible with a motion policy that bans interpolation. The one
 * thing that <em>looks</em> like a curve — a triangle's homing — is an acceleration applied to a
 * velocity, i.e. the same arithmetic one derivative up.
 *
 * <h2>The coordinate system</h2>
 *
 * A fixed logical field, {@code 480 × 300}, y increasing <b>downward</b> to match every other canvas
 * in this project. The view scales it to whatever the window is; nothing here knows about pixels.
 */
public final class DefenseGame {

    /** How the round ended, or that it has not. */
    public enum Outcome {
        /** Still being played. */
        PLAYING,

        /** The player destroyed the virus. */
        HELD,

        /** Time ran out, or the player was hit. */
        BREACHED
    }

    /** Why the round ended, for the verdict line. Never a rule — only something to read. */
    public enum Ending {
        /** Not over. */
        NONE,

        /** The laser reached the virus. */
        VIRUS_DESTROYED,

        /** Two triangles landed. */
        SHOT_DOWN,

        /** The circle caught the player. */
        RUN_DOWN,

        /** Thirty seconds passed. */
        TIME_OUT,

        /** The player gave up — the {@code §6.1} accommodation. */
        CONCEDED,

        /** The Auto-Counter Daemon was handed the round, and won its roll. */
        DAEMON_HELD,

        /** The Auto-Counter Daemon was handed the round, and lost it. */
        DAEMON_FAILED
    }

    /** What the player is holding down this tick. */
    public record Input(boolean up, boolean down, boolean forward, boolean back, boolean fire) {

        /** Nothing held — what a view with no focus should send. */
        public static final Input IDLE = new Input(false, false, false, false, false);

        /**
         * This input as five bits — the form a round is recorded and transmitted in.
         *
         * <p>⚠ One byte per tick, so a whole thirty-second round is 1,800 bytes before any
         * compression. That is what makes replay adjudication affordable to send at all.
         */
        public byte packed() {
            return (byte) ((up ? 1 : 0) | (down ? 2 : 0) | (forward ? 4 : 0) | (back ? 8 : 0) | (fire ? 16 : 0));
        }

        /** The inverse of {@link #packed()}. */
        public static Input unpack(byte bits) {
            return new Input((bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0, (bits & 8) != 0, (bits & 16) != 0);
        }
    }

    /** A moving thing, in field units. */
    public record Body(double x, double y) {}

    /**
     * One of the virus's shots, and <b>where its nose is pointed</b>.
     *
     * <h2>⚠ The heading is not the velocity, and that is the whole point of carrying it</h2>
     *
     * While a shot is still approaching it <b>aims at the player</b> — the nose tracks them even
     * where the flight path has not caught up, which is what makes an incoming triangle read as
     * something hunting rather than something falling. The instant it passes, it <b>points along its
     * own travel</b> and stays there: committed, no longer interested.
     *
     * <p>⚠ It is computed from the SAME condition the homing uses ({@code x < playerX}), not from a
     * separate one. Two conditions would eventually disagree, and the disagreement would be a shot
     * still visibly tracking a player it can no longer turn towards — which teaches the dodge wrong.
     *
     * @param heading radians; {@code 0} is toward the player's half, y down
     */
    public record Shot(double x, double y, double heading) {}

    /** One breakable square. */
    public record Block(double x, double y, double size) {}

    /** Everything the view needs to draw one frame. Immutable; rebuilt per tick. */
    public record Snapshot(
            Body player,
            Body virus,
            List<Block> blocks,
            List<Shot> triangles,
            Body circle,
            Body laser,
            double firewallBandWidth,
            int virusLives,
            int virusTier,
            int hitsTaken,
            int hitsAllowed,
            double secondsLeft,
            boolean sheltered,
            Outcome outcome,
            Ending ending) {}

    private static final double STEP = 1.0d / Balance.DEFENSE_TICKS_PER_SECOND;

    /**
     * The trace marker for a concession.
     *
     * <p>⚠ Outside the five input bits by construction — {@link Input#packed()} can only set the low
     * five, so no run of play can produce this byte and it needs no escaping.
     */
    static final byte CONCEDE = (byte) 0x80;

    /**
     * The trace marker for handing the round to the Auto-Counter Daemon.
     *
     * <p>⚠ The daemon's roll is taken from the round's own seeded stream, so a replay reaches the same
     * answer — which is what stops "the daemon saved me" being an unverifiable claim.
     */
    static final byte DAEMON = (byte) 0x81;

    private final Random random;
    private final double bandWidth;
    private final double virusSpeed;
    private final int virusTier;
    private int virusLives;

    private double playerX = Balance.DEFENSE_FIELD_WIDTH - 60.0d;
    private double playerY = Balance.DEFENSE_FIELD_HEIGHT / 2.0d;
    private double playerVx;
    private double playerVy;

    private double virusY = Balance.DEFENSE_FIELD_HEIGHT / 2.0d;
    private double virusDirection = 1.0d;

    private final List<Block> blocks = new ArrayList<>();
    private final List<double[]> triangles = new ArrayList<>();

    private double circleX;
    private double circleY;

    private boolean laserOut;
    private double laserX;
    private double laserY;
    private double laserDirection = -1.0d;
    private boolean fireHeld;

    private double sinceShot;
    private int ticks;
    private int hits;
    private final java.io.ByteArrayOutputStream trace = new java.io.ByteArrayOutputStream();
    private Outcome outcome = Outcome.PLAYING;
    private Ending ending = Ending.NONE;

    /**
     * Builds a round.
     *
     * @param firewallTier the armed firewall's tier, or {@code 0} for none. Sets the band's width and
     *     nothing else — see {@code docs/design/19} §3.5
     * @param seed the shield layout. ⚠ Taken as a parameter rather than drawn here so a round can be
     *     replayed exactly: the caller owns the save's RNG and is the only thing that may spend it.
     */
    public DefenseGame(int firewallTier, long seed) {
        this(firewallTier, false, 1, seed);
    }

    /**
     * Builds a round with the whole rig's posture and the attack it is facing.
     *
     * @param firewallTier the armed firewall's tier, or {@code 0}. Sets the shelter band's width
     * @param tarpit whether a Tarpit is armed. Slows the VIRUS's patrol and nothing else
     * @param virusTier the attacker's Breach Virus, 1–4. Sets how many laser hits the virus takes
     * @param seed the shield layout
     */
    public DefenseGame(int firewallTier, boolean tarpit, int virusTier, long seed) {
        this.random = new Random(seed);
        this.bandWidth = Balance.defenseFirewallBand(firewallTier);
        // ⚠ Applied to the PATROL only. See Balance.DEFENSE_TARPIT_VIRUS_SPEED — slowing the
        // projectiles would make the Tarpit a damage-reduction item, which is the firewall's job.
        this.virusSpeed = Balance.DEFENSE_VIRUS_SPEED * (tarpit ? Balance.DEFENSE_TARPIT_VIRUS_SPEED : 1.0d);
        this.virusTier = Math.max(1, virusTier);
        this.virusLives = Balance.defenseVirusLives(this.virusTier);
        this.circleX = virusX();
        this.circleY = virusY;
        buildShield();
    }

    /**
     * Every input this round has seen, one byte per tick — the evidence, not a claim.
     *
     * <p>See {@link DefenseAdjudicator}: a round is a pure function of {@code (seed, loadout, trace)},
     * so whoever needs the outcome recomputes it rather than believing the player who played it.
     */
    public byte[] trace() {
        return trace.toByteArray();
    }

    /** The attacking virus's remaining lives — what the laser has left to get through. */
    public int virusLives() {
        return virusLives;
    }

    /** The attacker's Breach Virus tier, which is what the daemon's odds are read against. */
    public int virusTier() {
        return virusTier;
    }

    /**
     * Hands the round to the Auto-Counter Daemon: one roll, and the round is over either way.
     *
     * <h2>⚠ IT RESOLVES IMMEDIATELY AND IT IS STRICTLY WORSE THAN PLAYING</h2>
     *
     * {@code Balance.DEFENSE_DAEMON_MAX_ODDS} is a coin flip at its very best and falls against a
     * better attack. That ceiling is what keeps Invariant <b>I10</b> alive in spirit — a bot that
     * assists rather than substitutes — and it is what stops the correct play being "press the daemon
     * and never touch the arrow keys". See the constant.
     *
     * <p>⚠ The draw is taken from the round's own seeded stream, not from the save's RNG. Nothing
     * about a defence round is persisted, so spending a committed draw on it would shift every later
     * game outcome for a round that leaves no trace — which is the rule {@code Rng} states about
     * decoration, applied to something that is not decoration but is equally unrecorded.
     *
     * @return whether the daemon held
     */
    public boolean runDaemon() {
        if (!playing()) {
            return outcome == Outcome.HELD;
        }
        trace.write(DAEMON);
        boolean held = random.nextDouble() < Balance.defenseDaemonOdds(virusTier);
        finish(held ? Outcome.HELD : Outcome.BREACHED, held ? Ending.DAEMON_HELD : Ending.DAEMON_FAILED);
        return held;
    }

    /** The virus's fixed x. It moves only up and down. */
    private static double virusX() {
        return 34.0d;
    }

    /**
     * Lays out the shield.
     *
     * <h2>⚠ The virus's corridor is left clear, and that is a rule rather than a happy accident</h2>
     *
     * The squares start well to the right of the virus, so it always has room to move up and down for
     * the whole round. A shield that could box it in would produce rounds where the target cannot
     * move — trivially winnable — and rounds where it is walled off behind squares the player has to
     * clear before they can even aim, which are the same round with the difficulty in the wrong
     * place.
     */
    private void buildShield() {
        for (int column = 0; column < Balance.DEFENSE_SHIELD_COLUMNS; column++) {
            for (int row = 0; row < Balance.DEFENSE_SHIELD_ROWS; row++) {
                if (random.nextDouble() >= Balance.DEFENSE_SHIELD_FILL) {
                    continue;
                }
                double x = Balance.DEFENSE_SHIELD_LEFT + column * Balance.DEFENSE_SHIELD_CELL;
                // ⚠ FROM THE TOP EDGE, not centred. The centred layout left ~40 units clear at each
                // end of the field, and those gaps were free lanes: a player who parked at either
                // extreme had a clear shot at a virus that patrols the whole height, and never had to
                // cut through anything. The shield now spans the field, so a lane has to be made.
                double y = row * Balance.DEFENSE_SHIELD_CELL;
                blocks.add(new Block(x, y, Balance.DEFENSE_SHIELD_CELL - 3.0d));
            }
        }
    }

    /** Whether the round is still being played. */
    public boolean playing() {
        return outcome == Outcome.PLAYING;
    }

    public Outcome outcome() {
        return outcome;
    }

    public Ending ending() {
        return ending;
    }

    /**
     * Gives the round up as a loss — {@code docs/design/19} §6.1.
     *
     * <p>⚠ This is the accessibility accommodation and not a convenience. A reflex round has no still
     * version, so Reduce motion cannot freeze it; what a player who does not want to play one gets
     * instead is the ability to stop immediately rather than sit through thirty seconds.
     */
    public void concede() {
        // ⚠ Recorded as a marker byte so a replay reaches the same ending. Without it a conceded
        // round replays as one that simply stopped early, and the adjudicator sees a player who
        // walked away as a player whose connection dropped.
        if (playing()) {
            trace.write(CONCEDE);
        }
        if (playing()) {
            finish(Outcome.BREACHED, Ending.CONCEDED);
        }
    }

    /** Advances the world one fixed step. Does nothing once the round is over. */
    public void tick(Input input) {
        if (!playing()) {
            return;
        }
        // ⚠ RECORDED BEFORE ANYTHING IS APPLIED, and every tick the round actually advanced — this is
        // the evidence the outcome is adjudicated from, so it has to be exactly the sequence the
        // simulation saw. Recording after the step, or skipping a tick that changed nothing, makes
        // the replay diverge from the round it is supposed to be checking.
        trace.write((input == null ? Input.IDLE : input).packed());
        ticks++;
        movePlayer(input == null ? Input.IDLE : input);
        moveVirus();
        fire(input == null ? Input.IDLE : input);
        moveLaser();
        moveTriangles();
        moveCircle();

        if (playing() && ticks >= Balance.DEFENSE_ROUND_SECONDS * Balance.DEFENSE_TICKS_PER_SECOND) {
            // ⚠ A timeout is a LOSS. The attacker is trying to get in; running the clock out is them
            // succeeding. Scored any other way, hiding in the firewall band for thirty seconds wins.
            finish(Outcome.BREACHED, Ending.TIME_OUT);
        }
    }

    private void movePlayer(Input input) {
        double dx = (input.forward() ? -1 : 0) + (input.back() ? 1 : 0);
        double dy = (input.up() ? -1 : 0) + (input.down() ? 1 : 0);
        // ⚠ Normalised, so a diagonal is not 1.41× faster than a straight line. Without it the
        // optimal way to move anywhere is diagonally, which every player finds and none enjoys.
        double length = Math.hypot(dx, dy);
        if (length > 0) {
            playerVx += dx / length * Balance.DEFENSE_PLAYER_ACCEL * STEP;
            playerVy += dy / length * Balance.DEFENSE_PLAYER_ACCEL * STEP;
        }
        // ⚠ Drag is applied as a fraction kept PER SECOND, raised to the step — not subtracted per
        // tick. A per-tick subtraction ties how fast the cube stops to the tick rate, which is the
        // same class of mistake as a chance-per-tick and just as invisible.
        double keep = Math.pow(Balance.DEFENSE_PLAYER_DRAG, STEP);
        playerVx *= keep;
        playerVy *= keep;

        // ⚠ Capped at the SAME top speed as before, so glide changes how the cube gets there and never
        // how fast it is. A momentum model that also raised the ceiling would silently re-tune the
        // circle, which is only escapable because the player is faster than it.
        double speed = Math.hypot(playerVx, playerVy);
        if (speed > Balance.DEFENSE_PLAYER_SPEED) {
            playerVx = playerVx / speed * Balance.DEFENSE_PLAYER_SPEED;
            playerVy = playerVy / speed * Balance.DEFENSE_PLAYER_SPEED;
        }

        playerX += playerVx * STEP;
        playerY += playerVy * STEP;

        // ⚠ A wall KILLS the velocity into it. Without this the cube keeps accumulating speed against
        // an edge it cannot cross, and peeling away from that edge fires it across the field — which
        // reads as the controls misfiring rather than as momentum.
        double lowX = Balance.DEFENSE_MIDLINE;
        double highX = Balance.DEFENSE_FIELD_WIDTH - Balance.DEFENSE_PLAYER_RADIUS;
        if (playerX <= lowX && playerVx < 0 || playerX >= highX && playerVx > 0) {
            playerVx = 0;
        }
        double lowY = Balance.DEFENSE_PLAYER_RADIUS;
        double highY = Balance.DEFENSE_FIELD_HEIGHT - Balance.DEFENSE_PLAYER_RADIUS;
        if (playerY <= lowY && playerVy < 0 || playerY >= highY && playerVy > 0) {
            playerVy = 0;
        }
        playerX = clamp(playerX, lowX, highX);
        playerY = clamp(playerY, lowY, highY);
    }

    /**
     * The virus runs up and down, biased away from the player's line.
     *
     * <p>⚠ It <b>avoids being lined up with</b> rather than fleeing the player's position, and those
     * are different: the player's y is what the laser will travel along, so what the virus is dodging
     * is the shot it has not been fired yet. It still bounces off the field edges, so a player who
     * pins it into a corner has earned the shot.
     */
    private void moveVirus() {
        double lined = Math.abs(virusY - playerY);
        if (lined < Balance.DEFENSE_VIRUS_RADIUS * 2.5d) {
            virusDirection = virusY < playerY ? -1.0d : 1.0d;
        }
        virusY += virusDirection * virusSpeed * STEP;
        if (virusY < Balance.DEFENSE_VIRUS_RADIUS) {
            virusY = Balance.DEFENSE_VIRUS_RADIUS;
            virusDirection = 1.0d;
        }
        if (virusY > Balance.DEFENSE_FIELD_HEIGHT - Balance.DEFENSE_VIRUS_RADIUS) {
            virusY = Balance.DEFENSE_FIELD_HEIGHT - Balance.DEFENSE_VIRUS_RADIUS;
            virusDirection = -1.0d;
        }

        sinceShot += STEP;
        if (sinceShot >= Balance.DEFENSE_TRIANGLE_INTERVAL && triangles.size() < Balance.DEFENSE_MAX_TRIANGLES) {
            sinceShot = 0;
            double dx = playerX - virusX();
            double dy = playerY - virusY;
            double length = Math.max(1e-6d, Math.hypot(dx, dy));
            triangles.add(new double[] {
                virusX(),
                virusY,
                dx / length * Balance.DEFENSE_TRIANGLE_SPEED,
                dy / length * Balance.DEFENSE_TRIANGLE_SPEED
            });
        }
    }

    /**
     * ⚠ ONE SHOT IN FLIGHT, AND FIRE IS EDGE-TRIGGERED.
     *
     * <p>Reading the key as held would fire on every tick the spacebar was down — sixty shots a
     * second, which clears the shield instantly and makes the round a formality. Requiring the key to
     * be released between shots is what makes a shot a decision. The one-at-a-time rule is the other
     * half: while a shot is out, the player has nothing.
     */
    private void fire(Input input) {
        if (input.fire() && !fireHeld && !laserOut) {
            laserOut = true;
            // ⚠ THE SHOT COMES OUT BACKWARDS FROM INSIDE THE FIREWALL BAND — docs/design/19 §3.5a,
            // on explicit direction, and it is what stops shelter being free.
            //
            // Before this, the band was a place to stand where the circle could not reach you and
            // your laser still crossed the whole field: safety with no cost, and the only thing
            // arguing against camping was the clock. Now hiding and shooting are two different
            // decisions, and the player has to leave cover to threaten anything.
            //
            // ⚠ The shot is still SPENT — it fires, it flies the wrong way, and the one-at-a-time
            // rule means the player has nothing until it leaves the field. A refusal would have been
            // the softer design and says "you cannot do that"; this says "you can, and here is what
            // it costs", which is the same lesson the game teaches everywhere else.
            laserDirection = sheltered() ? 1.0d : -1.0d;
            laserX = playerX + laserDirection * Balance.DEFENSE_PLAYER_RADIUS;
            laserY = playerY;
        }
        fireHeld = input.fire();
    }

    private void moveLaser() {
        if (!laserOut) {
            return;
        }
        // ⚠ Swept in sub-steps rather than moved in one jump. At 620 units a second the laser covers
        // ~10 units per tick, which is half a shield square — a single jump can start one side of a
        // block and end the other, passing through it without ever overlapping. The classic
        // fast-projectile bug, and the reason the round would occasionally shoot through a wall.
        int steps = 6;
        double per = Balance.DEFENSE_LASER_SPEED * STEP / steps;
        for (int i = 0; i < steps && laserOut; i++) {
            laserX += laserDirection * per;
            // ⚠ Both edges. A backwards shot leaves by the right-hand side, and a laser that only
            // ever checked x <= 0 would fly off the field and never expire — after which the
            // one-at-a-time rule means the player can never fire again, which reads as the space bar
            // breaking the moment they stood in their own firewall.
            if (laserX <= 0 || laserX >= Balance.DEFENSE_FIELD_WIDTH) {
                laserOut = false;
                return;
            }
            if (Math.hypot(laserX - virusX(), laserY - virusY) <= Balance.DEFENSE_VIRUS_RADIUS) {
                laserOut = false;
                // ⚠ A higher-tier Breach Virus takes more hits. The shot is spent either way, which
                // is what makes lives cost the defender TIME — the thirty seconds is the resource a
                // bought tier is actually attacking.
                virusLives--;
                if (virusLives <= 0) {
                    finish(Outcome.HELD, Ending.VIRUS_DESTROYED);
                }
                return;
            }
            for (int b = 0; b < blocks.size(); b++) {
                Block block = blocks.get(b);
                if (laserX >= block.x() && laserX <= block.x() + block.size()
                        && laserY >= block.y() && laserY <= block.y() + block.size()) {
                    blocks.remove(b);
                    laserOut = false;
                    return;
                }
            }
        }
    }

    private void moveTriangles() {
        for (int i = triangles.size() - 1; i >= 0; i--) {
            double[] t = triangles.get(i);
            // ⚠ HOMING STOPS ONCE IT HAS PASSED THE PLAYER — docs/design/19 §3.3, and it is the whole
            // skill of dodging one. A triangle that steered forever would be unavoidable rather than
            // hard; committing once it is past is what makes a late dodge the correct play.
            if (t[0] < playerX) {
                double dx = playerX - t[0];
                double dy = playerY - t[1];
                double length = Math.max(1e-6d, Math.hypot(dx, dy));
                t[2] += dx / length * Balance.DEFENSE_TRIANGLE_HOMING * STEP;
                t[3] += dy / length * Balance.DEFENSE_TRIANGLE_HOMING * STEP;
                double speed = Math.max(1e-6d, Math.hypot(t[2], t[3]));
                t[2] = t[2] / speed * Balance.DEFENSE_TRIANGLE_SPEED;
                t[3] = t[3] / speed * Balance.DEFENSE_TRIANGLE_SPEED;
            }
            t[0] += t[2] * STEP;
            t[1] += t[3] * STEP;

            if (hit(t[0], t[1], Balance.DEFENSE_TRIANGLE_RADIUS)) {
                triangles.remove(i);
                hits++;
                if (hits > Balance.DEFENSE_TRIANGLE_HITS_ALLOWED) {
                    finish(Outcome.BREACHED, Ending.SHOT_DOWN);
                    return;
                }
                continue;
            }
            if (t[0] < -20 || t[0] > Balance.DEFENSE_FIELD_WIDTH + 20 || t[1] < -20
                    || t[1] > Balance.DEFENSE_FIELD_HEIGHT + 20) {
                triangles.remove(i);
            }
        }
    }

    /**
     * The circle walks toward the player forever, and the firewall band is the only thing that stops
     * it mattering.
     *
     * <p>⚠ Shelter is tested at the moment of contact, not on entry. A player who is inside the band
     * is safe and one who is a unit outside it is not, which is what makes the edge of the band
     * somewhere to be rather than a state to have entered.
     */
    private void moveCircle() {
        double dx = playerX - circleX;
        double dy = playerY - circleY;
        double length = Math.max(1e-6d, Math.hypot(dx, dy));
        circleX += dx / length * Balance.DEFENSE_CIRCLE_SPEED * STEP;
        circleY += dy / length * Balance.DEFENSE_CIRCLE_SPEED * STEP;

        if (!sheltered() && hit(circleX, circleY, Balance.DEFENSE_CIRCLE_RADIUS)) {
            finish(Outcome.BREACHED, Ending.RUN_DOWN);
        }
    }

    /** Whether the player is standing in the firewall band. Always false with no firewall armed. */
    public boolean sheltered() {
        return bandWidth > 0
                && playerX - Balance.DEFENSE_PLAYER_RADIUS < Balance.DEFENSE_MIDLINE + bandWidth;
    }

    private boolean hit(double x, double y, double radius) {
        return Math.hypot(x - playerX, y - playerY) <= radius + Balance.DEFENSE_PLAYER_RADIUS;
    }

    private void finish(Outcome how, Ending why) {
        outcome = how;
        ending = why;
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    /** Everything the view needs to draw one frame. */
    public Snapshot snapshot() {
        List<Shot> shots = new ArrayList<>(triangles.size());
        for (double[] t : triangles) {
            // ⚠ Same test as moveTriangles' homing gate — see Shot. Still approaching means the nose
            // is on the player; past them means it points where it is actually going.
            boolean approaching = t[0] < playerX;
            double heading = approaching
                    ? Math.atan2(playerY - t[1], playerX - t[0])
                    : Math.atan2(t[3], t[2]);
            shots.add(new Shot(t[0], t[1], heading));
        }
        int remaining = Balance.DEFENSE_ROUND_SECONDS * Balance.DEFENSE_TICKS_PER_SECOND - ticks;
        return new Snapshot(
                new Body(playerX, playerY),
                new Body(virusX(), virusY),
                List.copyOf(blocks),
                List.copyOf(shots),
                new Body(circleX, circleY),
                laserOut ? new Body(laserX, laserY) : null,
                bandWidth,
                virusLives,
                virusTier,
                hits,
                Balance.DEFENSE_TRIANGLE_HITS_ALLOWED + 1,
                Math.max(0, remaining) / (double) Balance.DEFENSE_TICKS_PER_SECOND,
                sheltered(),
                outcome,
                ending);
    }
}
