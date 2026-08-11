package io.github.stoicswe.eyeandsickle.engine.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unprovoked intrusions — {@code docs/design/19} §9.
 *
 * <h2>What this is for</h2>
 *
 * Every other intrusion in the game is a <b>reprisal</b>. So a cautious player never saw the defence
 * round at all, and every tool on {@code design/09}'s shelf was a purchase with no occasion to use it.
 * This is the pressure that arrives without being provoked, keyed on the number that already measures
 * how much attention the player is getting.
 */
class AmbientIntrusionTest {

    private static final Instant T0 = Instant.parse("2026-08-10T12:00:00Z");

    private static GameSave save(int heat) {
        GameSave save = GameEngine.newCharacter("operator", T0);
        save.personalHeat = heat;
        return save;
    }

    /** How many of {@code rounds} independent hours produce an attempt at this heat. */
    private static int attemptsPerHour(int heat, int rounds) {
        int fired = 0;
        for (long seed = 0; seed < rounds; seed++) {
            GameSave save = save(heat);
            save.rngSeed = seed * 0x9E3779B97F4A7C15L + 17;
            if (AmbientIntrusion.rollFor(save, Duration.ofHours(1), T0) != null) {
                fired++;
            }
        }
        return fired;
    }

    @Nested
    @DisplayName("the rate")
    class Rate {

        /**
         * ⚠ THE FLOOR IS NOT ZERO, and it is the whole reason this exists. At zero a careful player
         * never once has to defend, and the entire defensive half of the game is content they watch
         * other people have.
         */
        @Test
        @DisplayName("⚠ a clean rig is still visited")
        void aCleanRigIsStillVisited() {
            assertThat(AmbientIntrusion.ratePerHour(save(0)))
                    .as("being quiet is not the same as being invisible")
                    .isPositive();
            assertThat(attemptsPerHour(0, 400))
                    .as("and it really fires, over four hundred hours of clean play")
                    .isPositive();
        }

        /** Heat is what it keys on, because heat is what measures the attention being paid. */
        @Test
        @DisplayName("heat makes it more frequent")
        void heatRaisesIt() {
            assertThat(AmbientIntrusion.ratePerHour(save(Balance.PERSONAL_HEAT_MAX)))
                    .isGreaterThan(AmbientIntrusion.ratePerHour(save(0)));
            assertThat(attemptsPerHour(Balance.PERSONAL_HEAT_MAX, 400))
                    .as("measured, not merely declared")
                    .isGreaterThan(attemptsPerHour(0, 400));
        }

        /**
         * ⚠ AND THERE IS A CEILING. Heat already punishes in four other ways; at 100 it must not buy
         * a thirty-second arcade round every ninety seconds, which is a client nobody can put down.
         */
        @Test
        @DisplayName("⚠ and even at maximum heat it is an hourly event, not a constant one")
        void theCeilingIsSurvivable() {
            assertThat(AmbientIntrusion.ratePerHour(save(Balance.PERSONAL_HEAT_MAX)))
                    .as("attempts per hour at maximum heat")
                    .isLessThanOrEqualTo(2.0d);
        }

        /**
         * ⚠ PER HOUR, NEVER PER TICK. A chance-per-tick makes a faster-ticking client attack more
         * often and hands a three-day absence exactly one roll. Both are invisible in play, and both
         * make the tuned number meaningless.
         */
        @Test
        @DisplayName("⚠ the chance follows elapsed time, so the tick rate drops out")
        void theRateIsPerHour() {
            int inOneHour = 0;
            int inOneSecond = 0;
            for (long seed = 0; seed < 500; seed++) {
                GameSave a = save(50);
                a.rngSeed = seed;
                if (AmbientIntrusion.rollFor(a, Duration.ofHours(1), T0) != null) {
                    inOneHour++;
                }
                GameSave b = save(50);
                b.rngSeed = seed;
                if (AmbientIntrusion.rollFor(b, Duration.ofSeconds(1), T0) != null) {
                    inOneSecond++;
                }
            }
            assertThat(inOneHour)
                    .as("an hour of play is many times more likely to be visited than a second of it")
                    .isGreaterThan(inOneSecond * 10);
        }
    }

    @Nested
    @DisplayName("the cooldown")
    class Cooldown {

        /**
         * ⚠ What makes the rate safe to tune at all. Without it an unlucky run stacks two rounds back
         * to back, and thirty seconds of arcade twice in a minute is not tension.
         */
        @Test
        @DisplayName("⚠ two attempts cannot land back to back")
        void notBackToBack() {
            GameSave save = save(Balance.PERSONAL_HEAT_MAX);
            AmbientIntrusion.mark(save, T0);

            assertThat(AmbientIntrusion.due(save, T0.plusSeconds(30))).isFalse();
            assertThat(AmbientIntrusion.due(save, T0.plusSeconds(Balance.AMBIENT_INTRUSION_COOLDOWN_SECONDS)))
                    .isTrue();
        }

        /** ⚠ A fresh character has served no cooldown, so the first roll is allowed to be the one. */
        @Test
        @DisplayName("a character who has never been attacked is due immediately")
        void neverAttackedIsDue() {
            assertThat(AmbientIntrusion.due(save(0), T0)).isTrue();
        }
    }

    /**
     * ⚠ The RNG contract. The draw is taken before anything branches on heat, the cooldown or the
     * world's shape, so the stream's SHAPE does not depend on the player's situation — which is what
     * keeps a stored seed a replay.
     */
    @Test
    @DisplayName("⚠ it draws unconditionally, whatever the answer turns out to be")
    void drawsUnconditionally() {
        GameSave cold = save(0);
        GameSave hot = save(Balance.PERSONAL_HEAT_MAX);
        cold.rngSeed = 4242L;
        hot.rngSeed = 4242L;

        AmbientIntrusion.rollFor(cold, Duration.ofMinutes(1), T0);
        AmbientIntrusion.rollFor(hot, Duration.ofMinutes(1), T0);

        assertThat(hot.rngSeed)
                .as("the same number of draws whatever the heat, or the stream forks on the player's "
                        + "situation and a replay stops being one")
                .isEqualTo(cold.rngSeed);
    }

    /**
     * ⚠ The attacker has to be a machine that exists, because its address reaches the rig log and the
     * access log — and evidence pointing at nothing is worse than no evidence.
     */
    @Test
    @DisplayName("whoever comes is a real machine in this world")
    void theAttackerIsReal() {
        for (long seed = 0; seed < 200; seed++) {
            GameSave save = save(Balance.PERSONAL_HEAT_MAX);
            save.rngSeed = seed;
            var comer = AmbientIntrusion.rollFor(save, Duration.ofHours(4), T0);
            if (comer != null) {
                assertThat(save.topology.hosts).contains(comer);
                return;
            }
        }
        org.assertj.core.api.Assertions.fail("nothing fired in 200 attempts at maximum heat over four hours");
    }

    /**
     * ⚠ A tier-5 estate coming back at you brings something better than a desktop does — the gradient
     * the world already applies to loot and difficulty, and the answer to DEF-3's "every round is the
     * same round".
     */
    @Test
    @DisplayName("a harder machine attacks with a better virus")
    void harderMachinesBringMore() {
        assertThat(Balance.ambientIntrusionVirusTier(5))
                .isGreaterThan(Balance.ambientIntrusionVirusTier(1));
        assertThat(Balance.ambientIntrusionVirusTier(99))
                .as("clamped: a hand-edited save is not a promise")
                .isEqualTo(Balance.ambientIntrusionVirusTier(5));
    }
}
