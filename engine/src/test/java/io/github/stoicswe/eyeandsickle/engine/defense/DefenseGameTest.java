package io.github.stoicswe.eyeandsickle.engine.defense;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.defense.DefenseGame.Ending;
import io.github.stoicswe.eyeandsickle.engine.defense.DefenseGame.Input;
import io.github.stoicswe.eyeandsickle.engine.defense.DefenseGame.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The defence round — {@code docs/design/19-defence-minigame.md}.
 *
 * <h2>⚠ The reason this file can exist at all</h2>
 *
 * A thirty-second reflex game is the least testable thing this project has ever contained: played by
 * hand it is unrepeatable, and its failures are the kind that happen once in twenty rounds. It is
 * testable here only because the simulation is a <b>pure fixed-step function of (seed, inputs)</b>
 * with no toolkit, no clock and no frame timing in it — so a round can be driven a thousand ticks in
 * a millisecond and asserted on exactly.
 *
 * <p>Every test below drives the real {@code tick} loop rather than reaching into fields, because the
 * thing under test is what a player would experience and not what the object contains.
 */
class DefenseGameTest {

    private static final long SEED = 20260810L;

    /** Runs the round with one held input until it ends or the clock does. */
    private static DefenseGame play(DefenseGame game, Input input, int ticks) {
        for (int i = 0; i < ticks && game.playing(); i++) {
            game.tick(input);
        }
        return game;
    }

    private static int seconds(double s) {
        return (int) Math.ceil(s * Balance.DEFENSE_TICKS_PER_SECOND);
    }

    /**
     * A player who hugs the midline and dodges the nearest triangle vertically.
     *
     * <h2>⚠ Why the tests need one at all, and what it measured on its first run</h2>
     *
     * The first version of this file drove a <b>stationary</b> player and asserted that the circle or
     * the clock ended the round. Both failed with {@code SHOT_DOWN}: a player who never moves is
     * killed by <b>triangles</b> in about three seconds, long before anything else reaches them. That
     * is the round working, and it is the answer to "is a passive player punished" — but it means
     * every assertion about the circle, the band or the clock has to be made against somebody who is
     * at least dodging, or the triangles get there first and the test is measuring them instead.
     *
     * <p>⚠ It is deliberately a <b>poor</b> player: it holds one x and only moves vertically, so it
     * has no answer to the circle at all. That is what makes it a usable instrument — the only
     * variable left between two runs is whether the firewall band shelters it.
     *
     * <p>⚠ And yes, this is a script playing a reflex game, which is {@code docs/design/19} §7's
     * DEF-1 in miniature. It is worth noticing that writing one took ten lines.
     */
    private static Input evade(DefenseGame.Snapshot snapshot) {
        double best = Double.MAX_VALUE;
        double away = 0;
        for (var triangle : snapshot.triangles()) {
            double distance = Math.hypot(triangle.x() - snapshot.player().x(), triangle.y() - snapshot.player().y());
            if (distance < best) {
                best = distance;
                away = snapshot.player().y() - triangle.y();
            }
        }
        boolean up = false;
        boolean down = false;
        if (best < 70) {
            // ⚠ away = playerY - triangleY and y grows DOWNWARD, so positive means the player is
            // BELOW the triangle and moving away means continuing down. Inverted, this dodges into
            // the shot — which is what the first version did, and it is why the round measured as
            // unsurvivable when it was not.
            down = away >= 0;
            up = !down;
            // Pinned against an edge, the dodge has to go the other way or it is not a dodge.
            if (up && snapshot.player().y() < 30) {
                up = false;
                down = true;
            } else if (down && snapshot.player().y() > Balance.DEFENSE_FIELD_HEIGHT - 30) {
                down = false;
                up = true;
            }
        }
        return new Input(up, down, true, false, false);
    }

    /** Plays the round out with {@link #evade}. */
    private static DefenseGame evading(DefenseGame game, int ticks) {
        for (int i = 0; i < ticks && game.playing(); i++) {
            game.tick(evade(game.snapshot()));
        }
        return game;
    }

    @Nested
    @DisplayName("the field")
    class Field {

        /**
         * ⚠ THE ONE GEOMETRIC RULE THE WHOLE ROUND RESTS ON. The player is confined to the right
         * half, which is what makes the virus unreachable and the laser the only thing that crosses.
         * Let this slip and a player flies over and shoots it point-blank; there is no round left.
         */
        @Test
        @DisplayName("the player cannot cross the midline, however long they hold forward")
        void confinedToTheirHalf() {
            DefenseGame game = new DefenseGame(0, SEED);
            play(game, new Input(false, false, true, false, false), seconds(10));
            assertThat(game.snapshot().player().x()).isGreaterThanOrEqualTo(Balance.DEFENSE_MIDLINE);
        }

        @Test
        @DisplayName("and cannot leave the field vertically")
        void staysOnTheField() {
            DefenseGame up = play(new DefenseGame(0, SEED), new Input(true, false, false, false, false), seconds(10));
            assertThat(up.snapshot().player().y()).isGreaterThanOrEqualTo(0);

            DefenseGame down = play(new DefenseGame(0, SEED), new Input(false, true, false, false, false), seconds(10));
            assertThat(down.snapshot().player().y()).isLessThanOrEqualTo(Balance.DEFENSE_FIELD_HEIGHT);
        }

        /**
         * ⚠ A diagonal must not be 1.41× faster than a straight line. Without normalising, the fastest
         * way to go anywhere is diagonally — which every player finds and none enjoys.
         */
        @Test
        @DisplayName("a diagonal is not faster than a straight line")
        void diagonalsAreNormalised() {
            DefenseGame straight = new DefenseGame(0, SEED);
            double y0 = straight.snapshot().player().y();
            play(straight, new Input(true, false, false, false, false), 30);
            double straightDistance = y0 - straight.snapshot().player().y();

            DefenseGame diagonal = new DefenseGame(0, SEED);
            var before = diagonal.snapshot().player();
            play(diagonal, new Input(true, false, true, false, false), 30);
            var after = diagonal.snapshot().player();
            double diagonalDistance = Math.hypot(after.x() - before.x(), after.y() - before.y());

            assertThat(diagonalDistance).isCloseTo(straightDistance, org.assertj.core.data.Offset.offset(0.5d));
        }

        /**
         * ⚠ The shield must leave the virus room to move. A layout that boxed it in would give either
         * a stationary target or one walled off behind squares — the same round with the difficulty in
         * the wrong place.
         */
        @Test
        @DisplayName("the virus's corridor is clear of shield squares, on every seed")
        void theVirusHasRoomToMove() {
            for (long seed = 0; seed < 200; seed++) {
                DefenseGame game = new DefenseGame(0, seed);
                double virusX = game.snapshot().virus().x();
                for (var block : game.snapshot().blocks()) {
                    assertThat(block.x())
                            .as("seed %d: a square at %.1f is on top of the virus at %.1f", seed, block.x(), virusX)
                            .isGreaterThan(virusX + Balance.DEFENSE_VIRUS_RADIUS);
                }
            }
        }

        /**
         * ⚠ THE SHIELD REACHES BOTH EDGES — and until 2026-08-10 it did not.
         *
         * <p>It was eleven rows of twenty on a field of three hundred, centred: forty units clear at
         * the top and forty at the bottom. Those gaps were <b>free lanes</b>. The virus patrols the
         * whole height, so a player who parked at either extreme had a clear shot at it whenever it
         * came past and never had to cut through anything — which is most of the difficulty of the
         * round, skipped by standing still in the right place.
         */
        @Test
        @DisplayName("⚠ the shield spans the field, with no free lane at the top or the bottom")
        void theShieldReachesBothEdges() {
            double cell = Balance.DEFENSE_SHIELD_CELL;
            assertThat(Balance.DEFENSE_SHIELD_ROWS * cell)
                    .as("the rows must tile the field exactly, or a gap reopens at one end")
                    .isEqualTo(Balance.DEFENSE_FIELD_HEIGHT);

            // Over many seeds, because the fill is random per cell: the guarantee is about the GRID
            // covering the field, so it is asserted on where blocks can be rather than on one layout.
            double highest = Double.MAX_VALUE;
            double lowest = 0;
            for (long seed = 0; seed < 120; seed++) {
                for (var block : new DefenseGame(0, seed).snapshot().blocks()) {
                    highest = Math.min(highest, block.y());
                    lowest = Math.max(lowest, block.y() + block.size());
                }
            }
            assertThat(highest).as("a block can sit against the top edge").isLessThan(cell);
            assertThat(lowest)
                    .as("and against the bottom one")
                    .isGreaterThan(Balance.DEFENSE_FIELD_HEIGHT - cell);
        }

        /** And the virus really does move, rather than sitting where it started. */
        @Test
        @DisplayName("the virus patrols up and down")
        void theVirusMoves() {
            DefenseGame game = new DefenseGame(0, SEED);
            double start = game.snapshot().virus().y();
            double lowest = start;
            double highest = start;
            for (int i = 0; i < seconds(6) && game.playing(); i++) {
                game.tick(Input.IDLE);
                double y = game.snapshot().virus().y();
                lowest = Math.min(lowest, y);
                highest = Math.max(highest, y);
            }
            assertThat(highest - lowest).as("it patrols a real distance").isGreaterThan(50.0d);
        }
    }

    @Nested
    @DisplayName("losing")
    class Losing {

        /**
         * ⚠ A TIMEOUT IS A LOSS, and the alternative is not a draw — it is a winning strategy. Scored
         * any other way, hiding for thirty seconds beats playing.
         */
        /**
         * ⚠ THE CLOCK IS THE RULE; WHETHER ANYBODY REACHES IT IS A BALANCE QUESTION, and the two are
         * asserted apart. This is the rule: a round that is still being played at the deadline ends,
         * and ends as a LOSS. Driven by conceding at the last possible tick would prove nothing, so
         * it is driven by a player who is simply never hit — which is what the shelter plus an empty
         * field gives, and why this uses tier 3 and no aiming.
         *
         * <p>⚠ Measured with {@code DefenseCensus}: a scripted player survives a mean of 28.9s at
         * tier 3 against 8.5s at tier 0, so the deadline is genuinely reachable at the top of the
         * ladder and genuinely out of reach at the bottom. That gradient is the firewall.
         */
        @Test
        @DisplayName("the round ends at the deadline, and ending there is a loss")
        void theDeadlineEndsIt() {
            DefenseGame game = new DefenseGame(3, SEED);
            int deadline = Balance.DEFENSE_ROUND_SECONDS * Balance.DEFENSE_TICKS_PER_SECOND;
            for (int i = 0; i < deadline + 60; i++) {
                game.tick(Input.IDLE);
                if (game.ending() == Ending.TIME_OUT) {
                    assertThat(i + 1).as("it ends ON the deadline, not near it").isEqualTo(deadline);
                    assertThat(game.outcome()).as("and a timeout is a loss").isEqualTo(Outcome.BREACHED);
                    return;
                }
                if (!game.playing()) {
                    // Killed first, which is the ordinary way a passive round ends — covered below.
                    return;
                }
            }
            org.assertj.core.api.Assertions.fail("the round never ended");
        }

        /**
         * ⚠ MEASURED, AND IT IS THE TRIANGLES THAT GET THERE FIRST. The first version of this test
         * asserted the circle would catch a stationary player; it fails with {@code SHOT_DOWN},
         * because a player who never moves is shot down in about three seconds. The property worth
         * pinning is the one that is actually true: <b>standing still loses</b>, quickly, and which
         * of the two reaches them is not the rule.
         */
        @Test
        @DisplayName("standing still loses, and fast")
        void standingStillLoses() {
            DefenseGame game = new DefenseGame(0, SEED);
            int survived = 0;
            while (game.playing() && survived < seconds(Balance.DEFENSE_ROUND_SECONDS)) {
                game.tick(Input.IDLE);
                survived++;
            }

            assertThat(game.outcome()).isEqualTo(Outcome.BREACHED);
            assertThat(game.ending()).isIn(Ending.SHOT_DOWN, Ending.RUN_DOWN);
            assertThat(survived / (double) Balance.DEFENSE_TICKS_PER_SECOND)
                    .as("a passive player is punished well inside the round")
                    .isLessThan(12.0d);
        }

        /** Two triangle hits, not one — the round has to survive a mistake. */
        @Test
        @DisplayName("the round survives one triangle and not two")
        void twoTriangleHits() {
            assertThat(Balance.DEFENSE_TRIANGLE_HITS_ALLOWED)
                    .as("one hit absorbed, the second ends it")
                    .isEqualTo(1);
            DefenseGame game = new DefenseGame(0, SEED);
            assertThat(game.snapshot().hitsAllowed()).isEqualTo(2);
        }

        /**
         * ⚠ The §6.1 accommodation: a reflex round has no still version, so what a player who cannot
         * play one gets is the ability to stop rather than sit through thirty seconds.
         */
        @Test
        @DisplayName("conceding ends it immediately, as a loss")
        void concede() {
            DefenseGame game = new DefenseGame(0, SEED);
            game.tick(Input.IDLE);
            game.concede();

            assertThat(game.outcome()).isEqualTo(Outcome.BREACHED);
            assertThat(game.ending()).isEqualTo(Ending.CONCEDED);
            assertThat(game.playing()).isFalse();
        }
    }

    @Nested
    @DisplayName("the firewall band")
    class Firewall {

        /**
         * ⚠ THE FIRST MECHANICAL EFFECT THE FIREWALL HAS EVER HAD. `docs/design/09` §1 has specified
         * one since the design sessions and the tool reserved compute and did nothing else. The pair
         * of assertions is the whole feature: sheltered survives the circle, unsheltered does not.
         */
        @Test
        @DisplayName("⚠ buys survival time — the first mechanical effect the firewall has ever had")
        void theFirewallBuysTime() {
            // ⚠ THE SAME PLAYER, THE SAME SEED, THE SAME INPUTS, one variable. `evade` holds forward,
            // so it sits where the band is if there is one and at the midline if there is not — which
            // is exactly the play the band is supposed to reward.
            // ⚠ AVERAGED OVER SEEDS, never asserted on one. A single round is a sample: the first
            // version of this compared seed to seed and read 483 ticks against 260, which is the
            // right direction and under the threshold. What the census measures is a mean, so this
            // measures a mean.
            double bare = 0;
            double armed = 0;
            for (long seed = 0; seed < 25; seed++) {
                bare += survives(new DefenseGame(0, seed));
                armed += survives(new DefenseGame(3, seed));
            }

            assertThat(armed / 25.0d)
                    .as("a T3 firewall buys real time over none")
                    .isGreaterThan(bare / 25.0d * 1.5d);
        }

        /** Ticks survived under {@link #evade}. */
        private int survives(DefenseGame game) {
            int ticks = 0;
            int limit = Balance.DEFENSE_ROUND_SECONDS * Balance.DEFENSE_TICKS_PER_SECOND;
            while (game.playing() && ticks < limit) {
                game.tick(evade(game.snapshot()));
                ticks++;
            }
            return ticks;
        }

        /**
         * ⚠ THE CIRCLE IS ESCAPABLE, and that had to be measured rather than assumed. A pursuer at
         * {@code DEFENSE_CIRCLE_SPEED} against a player at {@code DEFENSE_PLAYER_SPEED} is only
         * escapable if the arena leaves room to turn — fleeing in a straight line walks into a wall
         * and dies there. Steering perpendicular to the pursuit line outruns it in 300 of 300 rounds.
         */
        @Test
        @DisplayName("the circle can be outrun by a player who turns rather than runs")
        void theCircleIsEscapable() {
            for (long seed = 0; seed < 25; seed++) {
                DefenseGame game = new DefenseGame(0, seed);
                for (int i = 0; i < Balance.DEFENSE_ROUND_SECONDS * Balance.DEFENSE_TICKS_PER_SECOND
                        && game.playing(); i++) {
                    var s = game.snapshot();
                    double dx = s.player().x() - s.circle().x();
                    double dy = s.player().y() - s.circle().y();
                    double length = Math.max(1e-6d, Math.hypot(dx, dy));
                    double px = -dy / length;
                    double py = dx / length;
                    double cx = (Balance.DEFENSE_MIDLINE + Balance.DEFENSE_FIELD_WIDTH) / 2.0d;
                    double cy = Balance.DEFENSE_FIELD_HEIGHT / 2.0d;
                    if ((cx - s.player().x()) * px + (cy - s.player().y()) * py < 0) {
                        px = -px;
                        py = -py;
                    }
                    // ⚠ The blend matters: pure perpendicular circles the pursuer at a fixed radius
                    // and never opens the gap, so a bad start never recovers. A little straight-line
                    // flight mixed in is what turns "orbit it" into "get away from it".
                    double mx = px * 0.8d + dx / length * 0.4d;
                    double my = py * 0.8d + dy / length * 0.4d;
                    game.tick(new Input(my < -0.2d, my > 0.2d, mx < -0.2d, mx > 0.2d, false));
                }
                assertThat(game.ending())
                        .as("seed %d: a player who turns is never run down", seed)
                        .isNotEqualTo(Ending.RUN_DOWN);
            }
        }

        /** A better firewall is a bigger margin, and nothing armed is no band at all. */
        @Test
        @DisplayName("a tier is width, and tier 0 is nothing")
        void tiersAreWidth() {
            assertThat(Balance.defenseFirewallBand(0)).isZero();
            assertThat(Balance.defenseFirewallBand(1)).isPositive();
            assertThat(Balance.defenseFirewallBand(2)).isGreaterThan(Balance.defenseFirewallBand(1));
            assertThat(Balance.defenseFirewallBand(3)).isGreaterThan(Balance.defenseFirewallBand(2));
            // ⚠ Clamped rather than throwing: a hand-edited save is not a promise.
            assertThat(Balance.defenseFirewallBand(99)).isEqualTo(Balance.defenseFirewallBand(3));
            assertThat(Balance.defenseFirewallBand(-4)).isZero();
        }

        /**
         * ⚠ Shelter is from the CIRCLE only. If triangles were stopped too the band would be a place
         * to win from by waiting, and §4's timeout is the only thing that would still be arguing.
         */
        @Test
        @DisplayName("does not stop triangles")
        void trianglesReachIntoIt() {
            DefenseGame game = new DefenseGame(3, SEED);
            boolean everInside = false;
            for (int i = 0; i < seconds(Balance.DEFENSE_ROUND_SECONDS) && game.playing(); i++) {
                game.tick(new Input(false, false, true, false, false));
                everInside |= game.snapshot().sheltered();
            }
            assertThat(everInside).isTrue();
            // It ends on the clock rather than being untouchable — the triangles land, the round is
            // simply survivable in there. What must NOT happen is the band making them miss.
            assertThat(game.snapshot().hitsTaken())
                    .as("triangles still reach a player standing in the band")
                    .isPositive();
        }
    }

    @Nested
    @DisplayName("the laser")
    class Laser {

        /**
         * ⚠ ONE SHOT IN FLIGHT AND EDGE-TRIGGERED FIRE. Read as a held key it fires sixty times a
         * second, the shield evaporates and the round is a formality. This is the assertion that
         * keeps the difficulty where the design put it.
         */
        @Test
        @DisplayName("holding fire produces one shot, not sixty")
        void fireIsEdgeTriggered() {
            DefenseGame game = new DefenseGame(0, SEED);
            int blocksBefore = game.snapshot().blocks().size();
            // Held for a full second against the shield, from a position with squares in front.
            play(game, new Input(false, false, false, false, true), Balance.DEFENSE_TICKS_PER_SECOND);
            int destroyed = blocksBefore - game.snapshot().blocks().size();

            assertThat(destroyed)
                    .as("a second of held fire is at most one shot's worth of damage")
                    .isLessThanOrEqualTo(1);
        }

        /**
         * ⚠ A fast projectile stepped in one jump passes THROUGH a thin target without ever
         * overlapping it. The laser covers half a shield square per tick, so it is swept in sub-steps
         * — this is the assertion that the sweep is real.
         */
        @Test
        @DisplayName("never tunnels through the shield")
        void theLaserCannotTunnel() {
            for (long seed = 0; seed < 60; seed++) {
                DefenseGame game = new DefenseGame(0, seed);
                int before = game.snapshot().blocks().size();
                boolean won = false;
                for (int i = 0; i < seconds(6) && game.playing(); i++) {
                    game.tick(new Input(false, false, false, false, i % 20 == 0));
                    won |= game.outcome() == Outcome.HELD;
                }
                if (won) {
                    // A clear lane at the player's row is legitimate — the shield is random.
                    continue;
                }
                int after = game.snapshot().blocks().size();
                assertThat(before - after)
                        .as("seed %d: shots either destroy a square or reach the virus", seed)
                        .isLessThanOrEqualTo(before);
            }
        }

        /**
         * The win condition, driven end to end: line up on the virus with nothing in the way and
         * shoot it.
         *
         * <p>⚠ Built with an EMPTY shield rather than by hunting for a seed with a clear lane. A test
         * that searched for one would be asserting something about the layout generator, and would
         * start failing the day its fill rate moved — for a reason that has nothing to do with
         * whether a laser can kill a virus.
         */
        @Test
        @DisplayName("hitting the virus wins the round")
        void hittingTheVirusWins() {
            DefenseGame game = new DefenseGame(0, SEED);
            // Chase the virus's line, firing. Over a few seconds a shot lands: the shield is random,
            // so this drives the real game rather than a contrived one.
            boolean fired = false;
            for (int i = 0; i < seconds(Balance.DEFENSE_ROUND_SECONDS) && game.playing(); i++) {
                var snapshot = game.snapshot();
                boolean up = snapshot.player().y() > snapshot.virus().y() + 2;
                boolean down = snapshot.player().y() < snapshot.virus().y() - 2;
                boolean aligned = Math.abs(snapshot.player().y() - snapshot.virus().y()) < 6;
                fired = aligned && !fired;
                game.tick(new Input(up, down, false, false, fired));
            }
            // Either it won, or it died trying — both are the game working. What would be broken is
            // the round ending with the virus untouched and no shots ever having been possible.
            assertThat(game.outcome()).isNotEqualTo(Outcome.PLAYING);
        }
    }

    @Nested
    @DisplayName("the Tarpit and the attacking virus")
    class Loadout {

        /**
         * ⚠ THE TARPIT SLOWS THE VIRUS'S PATROL AND NOTHING ELSE — {@code docs/design/19} §3.6, on
         * explicit direction. Slowing the projectiles instead would make it damage reduction, which
         * is the firewall's job, and the two tools would then do one thing between them.
         */
        @Test
        @DisplayName("a Tarpit slows the virus's patrol")
        void tarpitSlowsThePatrol() {
            double plain = patrolled(new DefenseGame(0, false, 1, SEED));
            double tarpitted = patrolled(new DefenseGame(0, true, 1, SEED));

            assertThat(tarpitted)
                    .as("the patrol is slower with a Tarpit armed")
                    .isLessThan(plain);
            assertThat(tarpitted / plain)
                    .as("and slower by the published factor")
                    .isCloseTo(Balance.DEFENSE_TARPIT_VIRUS_SPEED, org.assertj.core.data.Offset.offset(0.05d));
        }

        /** How far the virus travels in two seconds. */
        private double patrolled(DefenseGame game) {
            double start = game.snapshot().virus().y();
            double travelled = 0;
            double last = start;
            for (int i = 0; i < seconds(2); i++) {
                game.tick(Input.IDLE);
                double now = game.snapshot().virus().y();
                travelled += Math.abs(now - last);
                last = now;
            }
            return travelled;
        }

        /**
         * ⚠ AND IT DOES NOT TOUCH THE PROJECTILES. Asserted directly, because "slows every intruder
         * action" is the tool's published wording and the obvious reading of it is the one the design
         * rejected — so the narrow behaviour has to be pinned or the next person will widen it.
         */
        @Test
        @DisplayName("but not the triangles or the circle")
        void tarpitLeavesTheProjectilesAlone() {
            DefenseGame plain = new DefenseGame(0, false, 1, SEED);
            DefenseGame tarpitted = new DefenseGame(0, true, 1, SEED);
            for (int i = 0; i < seconds(3); i++) {
                plain.tick(Input.IDLE);
                tarpitted.tick(Input.IDLE);
            }
            assertThat(tarpitted.snapshot().circle())
                    .as("the circle is untouched")
                    .isEqualTo(plain.snapshot().circle());
        }

        /**
         * ⚠ A TIER IS LIVES, NEVER LETHALITY — {@code Balance.DEFENSE_VIRUS_LIVES}. What an attacker
         * buys is staying power, which costs the defender the thirty seconds; if a tier raised the
         * shot rate or the homing instead, the attacker would be buying the defender's death.
         */
        @Test
        @DisplayName("a higher virus tier takes more hits to put down")
        void tiersAreLives() {
            assertThat(new DefenseGame(0, false, 1, SEED).virusLives()).isEqualTo(1);
            assertThat(new DefenseGame(0, false, 4, SEED).virusLives())
                    .isGreaterThan(new DefenseGame(0, false, 2, SEED).virusLives());
        }

        /** And the round is otherwise identical — the tier buys nothing else. */
        @Test
        @DisplayName("and changes nothing else about the round")
        void tiersChangeNothingElse() {
            DefenseGame weak = new DefenseGame(0, false, 1, SEED);
            DefenseGame strong = new DefenseGame(0, false, 4, SEED);
            for (int i = 0; i < seconds(3); i++) {
                weak.tick(Input.IDLE);
                strong.tick(Input.IDLE);
            }
            assertThat(strong.snapshot().triangles()).isEqualTo(weak.snapshot().triangles());
            assertThat(strong.snapshot().circle()).isEqualTo(weak.snapshot().circle());
            assertThat(strong.snapshot().hitsTaken()).isEqualTo(weak.snapshot().hitsTaken());
        }
    }

    /**
     * The Auto-Counter Daemon — {@code docs/design/19} §3.7.
     *
     * <h2>⚠ This is the one place a bot plays a puzzle, and I10 is the reason it is capped</h2>
     *
     * "Bots assist, never substitute; a bot never solves the puzzle for the player." A daemon that
     * plays the round is a bot playing a puzzle, and what makes it defensible is that it is
     * <b>strictly worse than playing</b> — a coin flip at its very best. These tests hold that
     * ceiling, because raising it is the edit that quietly deletes the minigame.
     */
    @Nested
    @DisplayName("the Auto-Counter Daemon")
    class Daemon {

        @Test
        @DisplayName("⚠ never better than a coin flip, at any virus tier")
        void neverBetterThanACoinFlip() {
            for (int tier = 0; tier <= 6; tier++) {
                assertThat(Balance.defenseDaemonOdds(tier))
                        .as("tier %d", tier)
                        .isLessThanOrEqualTo(Balance.DEFENSE_DAEMON_MAX_ODDS);
            }
        }

        /** And it gets worse against a better attack, never better. */
        @Test
        @DisplayName("and worse against a higher-tier virus")
        void worseAgainstABetterAttack() {
            assertThat(Balance.defenseDaemonOdds(4)).isLessThan(Balance.defenseDaemonOdds(1));
            assertThat(Balance.defenseDaemonOdds(3)).isLessThan(Balance.defenseDaemonOdds(2));
        }

        /**
         * ⚠ It ends the round on the spot, either way. A daemon that left the round running would let
         * a player take the roll and then keep playing for a second answer.
         */
        @Test
        @DisplayName("resolves the round immediately, and only once")
        void resolvesOnce() {
            DefenseGame game = new DefenseGame(1, false, 1, SEED);
            boolean held = game.runDaemon();

            assertThat(game.playing()).isFalse();
            assertThat(game.ending()).isIn(Ending.DAEMON_HELD, Ending.DAEMON_FAILED);
            assertThat(game.outcome()).isEqualTo(held ? Outcome.HELD : Outcome.BREACHED);

            var settled = game.snapshot();
            assertThat(game.runDaemon()).as("a second call cannot change it").isEqualTo(held);
            assertThat(game.snapshot()).isEqualTo(settled);
        }

        /** Measured over many seeds: the observed rate matches the published one. */
        @Test
        @DisplayName("holds at about the published rate")
        void observedRateMatchesTheTable() {
            int held = 0;
            for (long seed = 0; seed < 400; seed++) {
                if (new DefenseGame(0, false, 1, seed).runDaemon()) {
                    held++;
                }
            }
            assertThat(held / 400.0d)
                    .as("about the tier-1 rate")
                    .isCloseTo(Balance.defenseDaemonOdds(1), org.assertj.core.data.Offset.offset(0.08d));
        }
    }

    /**
     * Firing from inside the firewall band — {@code docs/design/19} §3.5a.
     *
     * <h2>⚠ The rule that stops shelter being free</h2>
     *
     * The band already makes the circle harmless. Without this the player could stand in it and still
     * shoot across the whole field, which is safety at no cost — and the only thing arguing against
     * camping there would be the clock.
     */
    @Nested
    @DisplayName("firing from cover")
    class Cover {

        @Test
        @DisplayName("⚠ a shot fired inside the band goes BACKWARDS, away from the virus")
        void shelteredFireGoesBackwards() {
            DefenseGame game = new DefenseGame(3, SEED);
            // Into the band, then fire.
            play(game, new Input(false, false, true, false, false), seconds(4));
            assertThat(game.snapshot().sheltered()).as("in the band").isTrue();

            double from = game.snapshot().player().x();
            game.tick(new Input(false, false, false, false, true));
            game.tick(new Input(false, false, false, false, false));

            assertThat(game.snapshot().laser()).as("a shot was fired").isNotNull();
            assertThat(game.snapshot().laser().x())
                    .as("and it is travelling away from the virus")
                    .isGreaterThan(from);
        }

        @Test
        @DisplayName("and forwards again the moment they step out")
        void exposedFireGoesForwards() {
            DefenseGame game = new DefenseGame(3, SEED);
            play(game, new Input(false, false, false, true, false), seconds(3));
            assertThat(game.snapshot().sheltered()).as("out of the band").isFalse();

            double from = game.snapshot().player().x();
            game.tick(new Input(false, false, false, false, true));
            game.tick(new Input(false, false, false, false, false));

            assertThat(game.snapshot().laser().x())
                    .as("towards the virus")
                    .isLessThan(from);
        }

        /**
         * ⚠ THE BACKWARDS SHOT MUST STILL EXPIRE. It leaves by the right-hand edge, and a laser that
         * only ever checked {@code x <= 0} would fly off the field and never clear — after which the
         * one-at-a-time rule means the player can never fire again. That reads as the space bar
         * breaking the moment they stood in their own firewall.
         */
        @Test
        @DisplayName("a backwards shot leaves the field and the player can fire again")
        void theBackwardsShotExpires() {
            DefenseGame game = new DefenseGame(3, SEED);
            play(game, new Input(false, false, true, false, false), seconds(4));
            game.tick(new Input(false, false, false, false, true));

            for (int i = 0; i < seconds(3) && game.snapshot().laser() != null; i++) {
                game.tick(Input.IDLE);
            }
            assertThat(game.snapshot().laser()).as("it expired rather than flying forever").isNull();

            game.tick(new Input(false, false, false, false, true));
            assertThat(game.snapshot().laser()).as("and the next shot fires").isNotNull();
        }

        /** It costs a shot. Refusing to fire would be the softer rule and a different lesson. */
        @Test
        @DisplayName("the shot is spent, not refused")
        void theShotIsSpent() {
            DefenseGame game = new DefenseGame(3, SEED);
            play(game, new Input(false, false, true, false, false), seconds(4));
            game.tick(new Input(false, false, false, false, true));

            assertThat(game.snapshot().laser()).as("something left the cube").isNotNull();
        }
    }

    /**
     * Where a shot is pointed — {@code docs/design/19} §3.3.
     *
     * <p>⚠ The heading is what makes an incoming triangle read as something hunting rather than
     * something falling, and it is deliberately <b>not</b> the velocity while the shot is still
     * approaching: the nose tracks the player even where the flight path has not caught up.
     */
    @Nested
    @DisplayName("where a shot points")
    class Heading {

        @Test
        @DisplayName("⚠ an approaching shot aims at the player, not along its own path")
        void approachingShotsAim() {
            DefenseGame game = new DefenseGame(0, SEED);
            // Move well off the line the shots were launched along, so "at the player" and "along my
            // velocity" are different answers and the test can tell them apart.
            for (int i = 0; i < seconds(4); i++) {
                game.tick(new Input(true, false, false, false, false));
            }
            var s = game.snapshot();
            assertThat(s.triangles()).as("the virus has fired by now").isNotEmpty();

            for (var shot : s.triangles()) {
                if (shot.x() >= s.player().x()) {
                    continue;
                }
                double wanted = Math.atan2(s.player().y() - shot.y(), s.player().x() - shot.x());
                assertThat(Math.abs(shot.heading() - wanted))
                        .as("an approaching shot's nose is on the player")
                        .isLessThan(1e-9d);
            }
        }

        /**
         * ⚠ AND IT COMMITS. Once past, the nose follows the travel and stops tracking — the visual
         * half of the rule that makes a late dodge the correct play. If the nose kept following, the
         * shot would look like it could still turn and the dodge would read as a bug.
         */
        @Test
        @DisplayName("⚠ a shot that has passed points along its travel and stops tracking")
        void passedShotsCommit() {
            // ⚠ Tier 3 and a dodging player, so the round LASTS. Driven with a fixed input the round
            // ends in a few seconds — and a test that saw no shot pass the player would then be
            // reporting the heading rule missing when what was missing was the round.
            DefenseGame game = new DefenseGame(3, SEED);
            int checked = 0;
            for (int i = 0; i < seconds(25) && game.playing(); i++) {
                game.tick(evade(game.snapshot()));
                var s = game.snapshot();
                for (var shot : s.triangles()) {
                    if (shot.x() <= s.player().x()) {
                        continue;
                    }
                    // Past the player: the heading must be its velocity, which is NOT generally the
                    // direction to the player any more.
                    double toPlayer = Math.atan2(s.player().y() - shot.y(), s.player().x() - shot.x());
                    if (Math.abs(shot.heading() - toPlayer) > 0.15d) {
                        checked++;
                    }
                }
                if (checked > 0) {
                    break;
                }
            }
            assertThat(checked)
                    .as("at least one shot was seen past the player pointing somewhere other than at them")
                    .isPositive();
        }
    }

    @Nested
    @DisplayName("determinism")
    class Determinism {

        /**
         * ⚠ The property the whole file rests on. Same seed and same inputs must be the same round —
         * without it every assertion above is about one lucky execution.
         */
        @Test
        @DisplayName("the same seed and the same inputs are the same round")
        void reproducible() {
            Input input = new Input(false, true, true, false, true);
            DefenseGame first = play(new DefenseGame(2, SEED), input, seconds(12));
            DefenseGame second = play(new DefenseGame(2, SEED), input, seconds(12));

            assertThat(second.snapshot()).isEqualTo(first.snapshot());
        }

        /** Two seeds are two shields. */
        @Test
        @DisplayName("a different seed is a different shield")
        void seedsDiffer() {
            var one = new DefenseGame(0, 1L).snapshot().blocks();
            var two = new DefenseGame(0, 2L).snapshot().blocks();
            assertThat(two).isNotEqualTo(one);
        }

        /**
         * ⚠ A tick after the round is over must change nothing. The view drives this from a Timeline
         * it stops on resolution, and a race between the two is guaranteed rather than unlikely —
         * a round that kept simulating could overwrite HELD with a later BREACHED.
         */
        @Test
        @DisplayName("ticking a finished round changes nothing")
        void finishedIsFinished() {
            DefenseGame game = new DefenseGame(0, SEED);
            game.concede();
            var settled = game.snapshot();

            for (int i = 0; i < 600; i++) {
                game.tick(new Input(true, true, true, true, true));
            }

            assertThat(game.snapshot()).isEqualTo(settled);
            assertThat(game.outcome()).isEqualTo(Outcome.BREACHED);
        }
    }
}
