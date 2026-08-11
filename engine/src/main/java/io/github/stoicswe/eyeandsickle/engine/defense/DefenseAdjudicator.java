package io.github.stoicswe.eyeandsickle.engine.defense;

import io.github.stoicswe.eyeandsickle.engine.Balance;

/**
 * Who decides whether a defence was held — {@code docs/design/19} §6.2.
 *
 * <h2>⚠ THE OUTCOME IS RECOMPUTED, NEVER BELIEVED</h2>
 *
 * A defence round is a <b>pure fixed-step function of {@code (seed, loadout, inputs)}</b>. That was a
 * testability decision when the simulation was written and it turns out to be the adjudication one:
 * the player sends what they <em>did</em> — one byte per tick — and whoever needs the answer replays
 * it and reads the outcome off their own copy of the rules. A claimed result is never accepted, so
 * there is nothing to forge. The trace is the evidence; the verdict is a computation.
 *
 * <h2>⚠ This is what dissolves DEF-2, and it does NOT need a move-by-move arbiter</h2>
 *
 * {@code design/19} §7 recorded the outcome as "the client's", on the reasoning that a real-time round
 * cannot be adjudicated the way a turn-based board can. That reasoning was about adjudicating it
 * <em>as it happens</em>. Adjudicating it <b>afterwards</b> costs one replay of 1,800 ticks — measured
 * in microseconds, because the simulation has no toolkit, no clock and no I/O in it.
 *
 * <h2>⚠ And it satisfies I15 rather than working around it</h2>
 *
 * <b>"No single arbiter decides cross-server adversarial outcomes; trust comes from quorum and
 * provenance."</b> Replay is exactly that shape: the seed is committed by the attacker's server
 * before the round begins, the trace is signed by the defender, and <em>any</em> party holding both
 * can recompute the same verdict and get the same answer. No server is being trusted — every server
 * is checking. A validator quorum verifies a defence the same way it verifies anything else.
 *
 * <h2>⚠ THE ASYMMETRY IS THE OTHER HALF, AND IT IS WHY THE TIMING PROBLEM GOES AWAY</h2>
 *
 * The two halves of a PvP breach were never symmetric and never actually simultaneous:
 *
 * <ul>
 *   <li>The <b>attacker's</b> half is the breach board, which already runs on the home server as a
 *       sequence of intents. Nothing about it is claimed — the server knows whether it was solved.
 *   <li>The <b>defender's</b> half is this round, and it starts when the attacker <b>commits the
 *       upload</b> — that is, when the board is solved — not when the breach opens.
 * </ul>
 *
 * <p>So "the attacker took four minutes over a board while the defender's thirty seconds ran out
 * three minutes ago" cannot happen: the defender's clock has not started. It is the same order the
 * solo loop already runs (board → upload → roll); in PvP the roll is replaced by a person.
 *
 * <h2>⚠ What replay does NOT establish</h2>
 *
 * That a human played it. A scripted trace replays perfectly, which is <b>DEF-1</b> — I10 does not
 * survive a reflex game — and is unchanged by any of this. Replay makes the outcome
 * <em>verifiable</em>, not <em>human</em>.
 */
public final class DefenseAdjudicator {

    private DefenseAdjudicator() {}

    /** Everything the round was issued with. Server-known before it starts; committed with the seed. */
    public record Loadout(int firewallTier, boolean tarpit, int virusTier, long seed) {}

    /** What a replay established. */
    public record Verdict(DefenseGame.Outcome outcome, DefenseGame.Ending ending, int ticks, boolean wellFormed) {

        /** Whether the defender turned the attempt back. */
        public boolean held() {
            return outcome == DefenseGame.Outcome.HELD;
        }
    }

    /**
     * The longest trace that can be legitimate: the round's own length, plus one marker byte.
     *
     * <p>⚠ Bounded before anything is allocated from it. A trace arrives from another machine, and a
     * length field is the oldest way in the world to be asked for a gigabyte.
     */
    public static int maxTraceBytes() {
        return Balance.DEFENSE_ROUND_SECONDS * Balance.DEFENSE_TICKS_PER_SECOND + 1;
    }

    /**
     * Replays a trace and reports what actually happened.
     *
     * <h2>⚠ A trace that runs out is a LOSS, never an error</h2>
     *
     * The commonest reason a trace is short is that the defender stopped sending — they closed the
     * client, or the connection went. Treating that as malformed would let anybody escape a losing
     * round by pulling the cable; treating it as a timeout is what the rules already do to a player
     * who does nothing, and it needs no new mechanism.
     *
     * @param trace one byte per tick, as {@link DefenseGame#trace()} produced
     */
    public static Verdict replay(Loadout loadout, byte[] trace) {
        if (loadout == null) {
            return new Verdict(DefenseGame.Outcome.BREACHED, DefenseGame.Ending.TIME_OUT, 0, false);
        }
        byte[] steps = trace == null ? new byte[0] : trace;
        if (steps.length > maxTraceBytes()) {
            // ⚠ Longer than the round can possibly be. Refused rather than truncated: a trace that
            // does not fit the rules is not evidence about a round played under them.
            return new Verdict(DefenseGame.Outcome.BREACHED, DefenseGame.Ending.TIME_OUT, 0, false);
        }

        DefenseGame game = new DefenseGame(
                loadout.firewallTier(), loadout.tarpit(), loadout.virusTier(), loadout.seed());
        int played = 0;
        for (byte step : steps) {
            if (!game.playing()) {
                // ⚠ Bytes after the round ended are not a crime and are not evidence either — a
                // client that sent one more frame than the rules allowed is the ordinary case at a
                // resolution boundary. Stop reading; the verdict is already established.
                break;
            }
            if (step == DefenseGame.CONCEDE) {
                game.concede();
            } else if (step == DefenseGame.DAEMON) {
                game.runDaemon();
            } else {
                game.tick(DefenseGame.Input.unpack(step));
                played++;
            }
        }

        // ⚠ A trace that simply stops is played out to the deadline rather than frozen. The round is
        // still running as far as the rules are concerned, and the rules already know what happens to
        // somebody who stops moving.
        while (game.playing()) {
            game.tick(DefenseGame.Input.IDLE);
        }
        return new Verdict(game.outcome(), game.ending(), played, true);
    }

    /**
     * Whether a claimed outcome survives a replay of the trace behind it.
     *
     * <p>⚠ The claim is only ever an input to this comparison — it is never the source of the answer.
     * A caller that used {@code claimed} for anything but this check has reintroduced exactly the
     * trust the design removed.
     */
    public static boolean claimSurvives(Loadout loadout, byte[] trace, DefenseGame.Outcome claimed) {
        Verdict verdict = replay(loadout, trace);
        return verdict.wellFormed() && verdict.outcome() == claimed;
    }
}
