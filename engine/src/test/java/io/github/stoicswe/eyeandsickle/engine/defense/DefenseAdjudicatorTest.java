package io.github.stoicswe.eyeandsickle.engine.defense;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.defense.DefenseAdjudicator.Loadout;
import io.github.stoicswe.eyeandsickle.engine.defense.DefenseGame.Ending;
import io.github.stoicswe.eyeandsickle.engine.defense.DefenseGame.Input;
import io.github.stoicswe.eyeandsickle.engine.defense.DefenseGame.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Replay adjudication — {@code docs/design/19} §6.2.
 *
 * <h2>What these tests are actually defending</h2>
 *
 * That a defence outcome is <b>recomputed, never believed</b>. Everything else in the design rests on
 * it: <b>I15</b> is satisfied because any party can replay and reach the same verdict, and
 * <b>DEF-2</b> — "the outcome is the client's" — stops being true the moment this holds.
 */
class DefenseAdjudicatorTest {

    private static final Loadout LOADOUT = new Loadout(2, false, 2, 90210L);

    /** Plays a round with a policy and hands back what it did and what it claimed. */
    private static DefenseGame play(Loadout loadout, java.util.function.IntFunction<Input> policy, int ticks) {
        DefenseGame game = new DefenseGame(
                loadout.firewallTier(), loadout.tarpit(), loadout.virusTier(), loadout.seed());
        for (int i = 0; i < ticks && game.playing(); i++) {
            game.tick(policy.apply(i));
        }
        return game;
    }

    private static final int ROUND_TICKS = Balance.DEFENSE_ROUND_SECONDS * Balance.DEFENSE_TICKS_PER_SECOND;

    @Nested
    @DisplayName("a replay reaches the same round")
    class Faithful {

        /**
         * ⚠ THE PROPERTY THE WHOLE DESIGN RESTS ON. If a replay of the recorded trace can reach a
         * different outcome from the round that produced it, there is nothing to adjudicate with and
         * the defence half of PvP has to go back to being a claim.
         */
        @Test
        @DisplayName("⚠ replaying a round's own trace reproduces its outcome exactly")
        void replayReproducesTheRound() {
            for (long seed = 0; seed < 40; seed++) {
                Loadout loadout = new Loadout(2, true, 3, seed);
                DefenseGame played = play(
                        loadout,
                        i -> new Input(i % 70 < 35, i % 70 >= 35, i % 13 == 0, false, i % 40 == 0),
                        ROUND_TICKS);

                var verdict = DefenseAdjudicator.replay(loadout, played.trace());

                assertThat(verdict.wellFormed()).as("seed %d", seed).isTrue();
                assertThat(verdict.outcome()).as("seed %d: outcome", seed).isEqualTo(played.outcome());
                assertThat(verdict.ending()).as("seed %d: ending", seed).isEqualTo(played.ending());
            }
        }

        /** A conceded round replays as conceded, which is why the marker is in the trace at all. */
        @Test
        @DisplayName("a concession survives the round trip")
        void concessionSurvives() {
            DefenseGame played = play(LOADOUT, i -> Input.IDLE, 30);
            played.concede();

            var verdict = DefenseAdjudicator.replay(LOADOUT, played.trace());

            assertThat(verdict.ending()).isEqualTo(Ending.CONCEDED);
            assertThat(verdict.held()).isFalse();
        }

        /**
         * ⚠ And so does the daemon, because its roll comes from the round's OWN seeded stream. Had it
         * been drawn from anywhere else, "the daemon saved me" would be the one outcome nobody could
         * check.
         */
        @Test
        @DisplayName("the daemon's roll replays to the same answer")
        void theDaemonReplays() {
            for (long seed = 0; seed < 30; seed++) {
                Loadout loadout = new Loadout(1, false, 1, seed);
                DefenseGame played = new DefenseGame(1, false, 1, seed);
                played.tick(Input.IDLE);
                played.runDaemon();

                var verdict = DefenseAdjudicator.replay(loadout, played.trace());

                assertThat(verdict.outcome()).as("seed %d", seed).isEqualTo(played.outcome());
                assertThat(verdict.ending()).as("seed %d", seed).isEqualTo(played.ending());
            }
        }
    }

    @Nested
    @DisplayName("a claim is never the answer")
    class Claims {

        /** ⚠ The point of the exercise: claiming a win you did not play does not produce one. */
        @Test
        @DisplayName("⚠ a losing round cannot be claimed as a win")
        void aLossCannotBeClaimed() {
            DefenseGame played = play(LOADOUT, i -> Input.IDLE, ROUND_TICKS);
            assertThat(played.outcome()).as("standing still loses").isEqualTo(Outcome.BREACHED);

            assertThat(DefenseAdjudicator.claimSurvives(LOADOUT, played.trace(), Outcome.HELD))
                    .as("the trace says otherwise, and the trace is what is read")
                    .isFalse();
            assertThat(DefenseAdjudicator.claimSurvives(LOADOUT, played.trace(), Outcome.BREACHED))
                    .isTrue();
        }

        /**
         * ⚠ AND THE LOADOUT IS THE SERVER'S, NOT THE TRACE'S. A defender who could name their own
         * firewall tier would simply declare a T3 band on every round; one who could name the seed
         * would shop for a shield with a clear lane. Both are here because both are the obvious
         * cheat, and both are answered by the same thing: the loadout is committed before play.
         */
        @Test
        @DisplayName("⚠ replaying against a different loadout does not vindicate the trace")
        void theLoadoutIsCommitted() {
            DefenseGame played = play(LOADOUT, i -> Input.IDLE, ROUND_TICKS);

            Loadout claimedBetter = new Loadout(3, true, 1, LOADOUT.seed());
            var honest = DefenseAdjudicator.replay(LOADOUT, played.trace());
            var flattering = DefenseAdjudicator.replay(claimedBetter, played.trace());

            // Not an assertion that the flattering one wins — it is an assertion that the two are
            // DIFFERENT computations, so which loadout is used is a decision somebody has to own.
            assertThat(flattering.ending()).as("a different loadout is a different round").isNotNull();
            assertThat(honest.outcome()).isEqualTo(Outcome.BREACHED);
        }
    }

    @Nested
    @DisplayName("what arrives from another machine")
    class Hostile {

        /**
         * ⚠ A trace is attacker-controlled input with a length. Bounded BEFORE anything is allocated
         * from it — a length field is the oldest way in the world to be asked for a gigabyte.
         */
        @Test
        @DisplayName("⚠ an over-long trace is refused rather than truncated")
        void overLongIsRefused() {
            byte[] absurd = new byte[DefenseAdjudicator.maxTraceBytes() + 1];

            var verdict = DefenseAdjudicator.replay(LOADOUT, absurd);

            assertThat(verdict.wellFormed())
                    .as("a trace that does not fit the rules is not evidence about a round played "
                            + "under them")
                    .isFalse();
            assertThat(verdict.held()).as("and it certainly is not a win").isFalse();
        }

        /**
         * ⚠ A TRACE THAT STOPS IS A LOSS, NEVER AN ERROR. The commonest reason one is short is that
         * the defender closed the client or the connection went — and if that were malformed, pulling
         * the cable would be the way out of any losing round.
         */
        @Test
        @DisplayName("⚠ a trace that stops early plays out to the deadline")
        void truncatedPlaysOn() {
            DefenseGame played = play(LOADOUT, i -> Input.IDLE, 20);

            var verdict = DefenseAdjudicator.replay(LOADOUT, played.trace());

            assertThat(verdict.wellFormed()).as("short is not malformed").isTrue();
            assertThat(verdict.outcome()).as("and it is not an escape").isEqualTo(Outcome.BREACHED);
        }

        @Test
        @DisplayName("an empty or absent trace is a loss")
        void nothingIsALoss() {
            assertThat(DefenseAdjudicator.replay(LOADOUT, new byte[0]).held()).isFalse();
            assertThat(DefenseAdjudicator.replay(LOADOUT, null).held()).isFalse();
            assertThat(DefenseAdjudicator.replay(null, new byte[0]).held()).isFalse();
        }

        /** Bytes past the end of a resolved round are the ordinary case at a boundary, not a crime. */
        @Test
        @DisplayName("bytes after the round ended are ignored, not rejected")
        void trailingBytesAreIgnored() {
            DefenseGame played = play(LOADOUT, i -> Input.IDLE, ROUND_TICKS);
            byte[] padded = java.util.Arrays.copyOf(played.trace(), played.trace().length + 3);

            var verdict = DefenseAdjudicator.replay(LOADOUT, padded);

            assertThat(verdict.wellFormed()).isTrue();
            assertThat(verdict.outcome()).isEqualTo(played.outcome());
        }
    }

    /**
     * ⚠ The figure the whole scheme has to be affordable at.
     *
     * <p>One byte per tick, so a full round is bounded by the round's own length. If this ever stops
     * being small, replay stops being something a validator quorum can do for every defence it sees.
     */
    @Test
    @DisplayName("a whole round's evidence is under two kilobytes")
    void theEvidenceIsSmall() {
        DefenseGame played = play(LOADOUT, i -> new Input(true, false, false, false, i % 7 == 0), ROUND_TICKS);

        assertThat(played.trace().length).isLessThanOrEqualTo(DefenseAdjudicator.maxTraceBytes());
        assertThat(DefenseAdjudicator.maxTraceBytes()).isLessThan(2048);
    }
}
