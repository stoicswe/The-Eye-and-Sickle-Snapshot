package io.github.stoicswe.eyeandsickle.engine.defense;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.defense.DefenseGame.Ending;
import io.github.stoicswe.eyeandsickle.engine.defense.DefenseGame.Input;
import io.github.stoicswe.eyeandsickle.engine.defense.DefenseGame.Snapshot;

/**
 * Measures whether the defence round is survivable and winnable, over many seeds.
 *
 * <pre>
 * mvn -pl engine exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=io.github.stoicswe.eyeandsickle.engine.defense.DefenseCensus
 * </pre>
 *
 * <h2>⚠ What a number here does and does not say</h2>
 *
 * These are <b>scripted</b> players, so a survival rate is a statement about the difficulty of the
 * <em>simulation</em> and not about how it feels in the hand. What it is genuinely good for is the
 * pair of questions a reflex game can be broken by and a play-test would take an hour to answer:
 * <b>is it survivable at all</b>, and <b>is it winnable at all</b>. A round nobody can last thirty
 * seconds in makes the timeout rule unreachable; one nobody can land a shot in is not a game.
 */
final class DefenseCensus {

    private DefenseCensus() {}

    /**
     * A competent player: picks, from the nine directions available, the one that leaves it furthest
     * from everything that can kill it a moment from now.
     *
     * <h2>⚠ Why the bot had to get better before the BALANCE could be judged</h2>
     *
     * The first two versions were bad in ways that looked like the game being too hard. One dodged
     * <b>into</b> the shot (an inverted sign); the next reacted only within 80 units and pinned itself
     * against the top of the field. Both measured 0% survival, and neither number said anything about
     * the round — they measured the instrument. A lookahead over nine directions is not a good player,
     * but it is a <em>defensible</em> one: it never walks into something it can see.
     */
    private static Input dodge(Snapshot s, boolean fire) {
        int[][] directions = {
            {0, 0}, {0, -1}, {0, 1}, {-1, 0}, {1, 0}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1},
        };
        double bestScore = -Double.MAX_VALUE;
        int[] best = directions[0];
        for (int[] direction : directions) {
            double length = Math.hypot(direction[0], direction[1]);
            double step = length == 0 ? 0 : Balance.DEFENSE_PLAYER_SPEED * 0.35d / length;
            double x = s.player().x() + direction[0] * step;
            double y = s.player().y() + direction[1] * step;
            if (x < Balance.DEFENSE_MIDLINE || x > Balance.DEFENSE_FIELD_WIDTH - 10 || y < 10
                    || y > Balance.DEFENSE_FIELD_HEIGHT - 10) {
                continue;
            }
            double score = Double.MAX_VALUE;
            for (var t : s.triangles()) {
                // Where the shot will be, not where it is: a threat is its future position.
                score = Math.min(score, Math.hypot(t.x() + 46 - x, t.y() - y));
            }
            // ⚠ The circle counts even while sheltered, at a discount. Ignoring it in the band is
            // what the previous version did, and it stood right next to the thing — so the first
            // triangle that pushed it out of the band killed it instantly. Shelter is not a reason to
            // forget where the circle is; it is a reason to weight it less.
            double toCircle = Math.hypot(s.circle().x() - x, s.circle().y() - y);
            score = Math.min(score, s.sheltered() ? toCircle * 2.5d : toCircle);
            // Prefer the middle of the field, gently — being cornered is what kills this bot.
            score -= Math.abs(y - Balance.DEFENSE_FIELD_HEIGHT / 2.0d) * 0.08d;
            // And prefer the firewall band when there is one, since that is the play it is testing.
            if (s.firewallBandWidth() > 0) {
                score -= Math.abs(x - (Balance.DEFENSE_MIDLINE + s.firewallBandWidth() / 2.0d)) * 0.25d;
            }
            if (score > bestScore) {
                bestScore = score;
                best = direction;
            }
        }
        return new Input(best[1] < 0, best[1] > 0, best[0] < 0, best[0] > 0, fire);
    }

    /**
     * Runs from the circle and nothing else, on a TANGENT rather than straight away.
     *
     * <h2>⚠ The measurement the other bot cannot make</h2>
     *
     * "Can the circle be escaped?" is a question about one rule, and the general-purpose bot answers
     * it badly because it is also dodging triangles and gets cornered doing it. Fleeing directly away
     * from a pursuer walks you into a wall and dies there — the classic result — so this steers
     * <b>perpendicular</b> to the pursuit line, picking whichever perpendicular points toward the
     * middle of the arena. That is what a person does without being told, and it is the honest test of
     * whether the circle's speed leaves the player any room.
     */
    private static Input flee(Snapshot s) {
        double dx = s.player().x() - s.circle().x();
        double dy = s.player().y() - s.circle().y();
        double length = Math.max(1e-6d, Math.hypot(dx, dy));
        double cx = (Balance.DEFENSE_MIDLINE + Balance.DEFENSE_FIELD_WIDTH) / 2.0d;
        double cy = Balance.DEFENSE_FIELD_HEIGHT / 2.0d;
        // Both perpendiculars; take the one heading back toward the centre.
        double px = -dy / length;
        double py = dx / length;
        if ((cx - s.player().x()) * px + (cy - s.player().y()) * py < 0) {
            px = -px;
            py = -py;
        }
        // Blend in a little straight-line flight so it opens the gap as well as circling.
        double mx = px * 0.8d + dx / length * 0.4d;
        double my = py * 0.8d + dy / length * 0.4d;
        return new Input(my < -0.2d, my > 0.2d, mx < -0.2d, mx > 0.2d, false);
    }

    /**
     * Flees the circle on a tangent AND dodges the nearest triangle — the two instruments above,
     * combined, which is the closest thing here to a competent human.
     */
    private static Input good(Snapshot s, boolean fire) {
        Input base = flee(s);
        boolean forward = base.forward();
        boolean back = base.back();
        boolean up = base.up();
        boolean down = base.down();
        // ⚠ SEEK the band, do not merely stay in it once there. Keying on `sheltered()` is
        // chicken-and-egg: the flee steering pushes right, away from the midline, so the bot never
        // arrives and the branch never fires — measured as 100% run-down on a tier that should have
        // been untouchable.
        if (s.firewallBandWidth() > 0) {
            forward = true;
            back = false;
        }

        double best = Double.MAX_VALUE;
        double away = 0;
        for (var t : s.triangles()) {
            double d = Math.hypot(t.x() - s.player().x(), t.y() - s.player().y());
            if (d < best) {
                best = d;
                away = s.player().y() - t.y();
            }
        }
        if (best < 95) {
            down = away >= 0;
            up = !down;
            if (up && s.player().y() < 45) {
                up = false;
                down = true;
            } else if (down && s.player().y() > Balance.DEFENSE_FIELD_HEIGHT - 45) {
                down = false;
                up = true;
            }
        }
        return new Input(up, down, forward, back, fire);
    }

    public static void main(String[] args) {
        int rounds = args.length > 0 ? Integer.parseInt(args[0]) : 300;
        for (int tier : new int[] {0, 1, 2, 3}) {
            int survived = 0;
            int shotDown = 0;
            int runDown = 0;
            double totalSeconds = 0;
            for (long seed = 0; seed < rounds; seed++) {
                DefenseGame game = new DefenseGame(tier, seed);
                int ticks = 0;
                int limit = Balance.DEFENSE_ROUND_SECONDS * Balance.DEFENSE_TICKS_PER_SECOND + 10;
                while (game.playing() && ticks < limit) {
                    game.tick(dodge(game.snapshot(), false));
                    ticks++;
                }
                totalSeconds += ticks / (double) Balance.DEFENSE_TICKS_PER_SECOND;
                switch (game.ending()) {
                    case TIME_OUT -> survived++;
                    case SHOT_DOWN -> shotDown++;
                    case RUN_DOWN -> runDown++;
                    default -> {}
                }
            }
            System.out.printf(
                    "tier %d — survived the full %ds: %3d/%d (%.0f%%)  shot down %3d  run down %3d  mean %.1fs%n",
                    tier,
                    Balance.DEFENSE_ROUND_SECONDS, survived, rounds, 100.0 * survived / rounds, shotDown, runDown, totalSeconds / rounds);
        }

        // ⚠ Is the CIRCLE escapable at all? One rule, isolated — this bot ignores triangles entirely,
        // so a death here is the circle and nothing else.
        int outran = 0;
        int fleeLimit = Balance.DEFENSE_ROUND_SECONDS * Balance.DEFENSE_TICKS_PER_SECOND + 10;
        for (long seed = 0; seed < rounds; seed++) {
            DefenseGame game = new DefenseGame(0, seed);
            for (int i = 0; i < fleeLimit && game.playing(); i++) {
                game.tick(flee(game.snapshot()));
            }
            if (game.ending() != Ending.RUN_DOWN) {
                outran++;
            }
        }
        System.out.printf("the circle is outrun in %d/%d rounds (%.0f%%)%n", outran, rounds, 100.0 * outran / rounds);

        // ⚠ The competent player: does the clock ever get to run out?
        for (int tier : new int[] {0, 1, 3}) {
            int timedOut = 0;
            int shot = 0;
            int run = 0;
            for (long seed = 0; seed < rounds; seed++) {
                DefenseGame game = new DefenseGame(tier, seed);
                for (int i = 0; i < fleeLimit && game.playing(); i++) {
                    game.tick(good(game.snapshot(), false));
                }
                switch (game.ending()) {
                    case TIME_OUT -> timedOut++;
                    case SHOT_DOWN -> shot++;
                    case RUN_DOWN -> run++;
                    default -> {}
                }
            }
            System.out.printf(
                    "tier %d — a competent player lasts the full %ds in %3d/%d (%.0f%%)  shot %3d  run %3d%n",
                    tier,
                    Balance.DEFENSE_ROUND_SECONDS, timedOut, rounds, 100.0 * timedOut / rounds, shot, run);
        }

        // ⚠ TRACKING QUALITY, MEASURED DIRECTLY — a bot's survival rate is a statement about the bot.
        // How near a shot gets to the player before it commits is a statement about the HOMING.
        // ⚠ CLOSEST APPROACH PER ROUND, AT TIER 3 — and both halves of that are the measurement.
        //
        // A mean over every tick is dominated by the APPROACH: a shot is fired from x=34 at a player
        // near x=400, so most of its life it is far away whatever its homing, and two very different
        // values read the same (166 against 170). The nearest anything got in a whole round is the
        // number that actually describes tracking.
        //
        // ⚠ Tier 3 so the player is sheltered and the CIRCLE cannot end the round at nine seconds.
        // Otherwise most rounds stop before the triangles have had their say, and the sample is made
        // of the quiet part.
        double closest = 0;
        int landed = 0;
        for (long seed = 0; seed < rounds; seed++) {
            DefenseGame game = new DefenseGame(3, false, 1, seed);
            double min = Double.MAX_VALUE;
            int before = 0;
            for (int i = 0; i < Balance.DEFENSE_ROUND_SECONDS * Balance.DEFENSE_TICKS_PER_SECOND
                    && game.playing(); i++) {
                game.tick(dodge(game.snapshot(), false));
                Snapshot s = game.snapshot();
                for (var t : s.triangles()) {
                    min = Math.min(min, Math.hypot(t.x() - s.player().x(), t.y() - s.player().y()));
                }
                if (s.hitsTaken() > before) {
                    landed += s.hitsTaken() - before;
                    before = s.hitsTaken();
                }
            }
            closest += min == Double.MAX_VALUE ? 0 : min;
        }
        System.out.printf(
                "homing %.0f — closest a shot got, per round: %.1f units; hits landed %d over %d rounds%n",
                Balance.DEFENSE_TRIANGLE_HOMING, closest / rounds, landed, rounds);

        // Winnable? An aimer that lines up on the virus and fires, with no dodging at all.
        for (int tier : new int[] {0, 3}) {
            int won = 0;
            for (long seed = 0; seed < rounds; seed++) {
                DefenseGame game = new DefenseGame(tier, seed);
                boolean firing = false;
                int limit = Balance.DEFENSE_ROUND_SECONDS * Balance.DEFENSE_TICKS_PER_SECOND + 10;
                double lastY = Double.NaN;
                for (int i = 0; i < limit && game.playing(); i++) {
                    Snapshot s = game.snapshot();
                    // ⚠ THE AIMER HAS TO BRAKE NOW THAT THE CUBE GLIDES. A bang-bang policy — full up
                    // or full down until aligned — oscillates around the target under momentum and
                    // never settles, which measures the BOT's inability to damp, not the round's
                    // difficulty. It aims where it will BE, not where it is.
                    double vy = Double.isNaN(lastY) ? 0 : (s.player().y() - lastY) * Balance.DEFENSE_TICKS_PER_SECOND;
                    lastY = s.player().y();
                    double predicted = s.player().y() + vy * 0.22d;
                    boolean up = predicted > s.virus().y() + 2;
                    boolean down = predicted < s.virus().y() - 2;
                    boolean aligned = Math.abs(s.player().y() - s.virus().y()) < 8;
                    firing = aligned && !firing;
                    // ⚠ VERTICAL FROM THE AIM, HORIZONTAL FROM THE DODGE. ORing the two together
                    // sets up and down at once, which cancels to no movement at all — the aimer then
                    // never tracks the virus, never aligns, never fires, and reports the round as
                    // unwinnable. It measured 0/300 that way while the same aimer in isolation won.
                    Input aim = dodge(s, firing);
                    game.tick(new Input(up, down, aim.forward(), aim.back(), firing));
                }
                if (game.ending() == Ending.VIRUS_DESTROYED) {
                    won++;
                }
            }
            System.out.printf("tier %d — an aiming player wins %d/%d (%.0f%%)%n", tier, won, rounds, 100.0 * won / rounds);
        }
    }
}
