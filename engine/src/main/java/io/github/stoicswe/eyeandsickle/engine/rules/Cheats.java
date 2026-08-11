package io.github.stoicswe.eyeandsickle.engine.rules;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.breach.BreachRules;
import io.github.stoicswe.eyeandsickle.engine.breach.Rng;
import io.github.stoicswe.eyeandsickle.engine.net.NetRules;
import io.github.stoicswe.eyeandsickle.engine.net.ReprisalRules;
import io.github.stoicswe.eyeandsickle.engine.state.CheatState;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.HostState;
import io.github.stoicswe.eyeandsickle.engine.state.TaskState;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import java.math.BigInteger;
import java.time.Instant;

/**
 * The developer facility: every cheat, in one place, applied to one character.
 *
 * <h2>⚠ Why this is allowed to step over the invariants</h2>
 *
 * It plainly does. A cycle ceiling set by hand walks past the compute ladder that <b>I1</b> exists
 * to protect; a granted balance walks past the sink <b>I2</b> rests on; disabling thermal recovery
 * removes the pressure the whole Thermal Budget exists to apply. That is not an oversight and it is
 * not a licence to relax those rules anywhere else — the argument is narrow and it is entirely
 * about <em>who is affected</em>.
 *
 * <p>Every invariant these cheats step over exists to keep a <b>shared</b> economy honest: they stop
 * one player buying a ceiling that another player had to earn, and they stop a market being flooded
 * with money nobody mined. None of that is in play on a character that cannot reach anybody. So the
 * load-bearing part of this facility is not in this file at all — it is that
 * {@code RemoteGameSession} refuses every cheat call, so a cheat can never be applied to a character
 * on a home server, and {@code GameSave}'s cheat state can never cross the wire. That refusal is the
 * whole safety argument. <b>Do not make cheats a wire operation.</b>
 *
 * <p>The second half of the argument is that a solo save was <em>always</em> this editable. It is a
 * JSON document inside an H2 file in the player's own profile directory, and CLAUDE.md's own note on
 * <b>I14</b> says so in as many words: "a local H2 file is exactly as editable as JSON was — merely
 * less pleasant to read". What changed on 2026-08-09 is not what a solo player can do to their own
 * character; it is that the game now provides the editor instead of making them find a hex editor.
 *
 * <h2>⚠ Overrides, never writes, wherever a value is derived</h2>
 *
 * The compute ceiling is the case that proves the rule. {@code ComputeLadder.reconcile} recomputes
 * {@code RigState.totalCycles} from the items held on every load and after every upgrade — that is
 * what stops a hand-edited save granting the whole ladder — so a cheat that assigned the field would
 * be reverted by the next reconcile, silently, and read as a slider that does not work. The
 * override is consulted by {@code ComputeLadder.capacityOf} instead, so there is still exactly one
 * answer to "what is this rig's ceiling".
 *
 * <h2>⚠ Every cheat writes to the rig log, and that is not decoration</h2>
 *
 * A player who granted themselves 5000 EC in one session and comes back a week later to a balance
 * they cannot account for will conclude the economy is broken. The log line is the record that says
 * otherwise, and it goes in the same place every other consequential event goes so that the history
 * reads in one order.
 */
public final class Cheats {

    private Cheats() {}

    /** The highest ceiling the slider offers. Not a rule — a bound on a control, so it lives here. */
    public static final long MAX_CYCLE_CEILING = 1024L;

    /** The largest single grant. Bounded so a mis-drag cannot make the ledger unreadable. */
    public static final BigInteger MAX_GRANT_WEI =
            Ethecoin.ofWholeEthecoin(100_000L).wei();

    /** The ceiling on {@link CheatState#eventChancePercent}. */
    public static final int MAX_EVENT_CHANCE_PERCENT = 500;

    /**
     * What every action answers for a character that may not cheat.
     *
     * <p>⚠ A refusal rather than a silent no-op. Nothing in the shipped client can reach these
     * methods for such a character — the facility is absent from the port — so this only fires if
     * something new wires them up server-side, and in that case the caller should be told rather
     * than left believing the cheat landed.
     */
    public static final String REFUSED = "cheats are solo only; this character is not";

    // ================================================================== reading

    /**
     * An untouched state, shared, for characters that may not cheat.
     *
     * <p>⚠ Read-only by convention and by construction: {@link #mayCheat} gates every write, so
     * nothing can reach this instance to mutate it. It exists so the hooks have something to answer
     * from that is <em>exactly</em> the ordinary rules.
     */
    private static final CheatState NONE = new CheatState();

    /**
     * This character's cheat state, never null.
     *
     * <p>⚠ Repairs the field in place rather than returning a throwaway default. Jackson leaves a
     * field absent from the document as null, so a save written before this existed arrives with
     * {@code cheats == null} — and a hook that answered from a fresh instance every time would work
     * perfectly while making every write vanish.
     */
    public static CheatState of(GameSave save) {
        if (save == null) {
            return NONE;
        }
        if (save.cheats == null) {
            save.cheats = new CheatState();
        }
        return save.cheats;
    }

    /**
     * Whether this character may cheat at all — <b>solo only, never multiplayer</b>.
     *
     * <h2>⚠ The engine-tier half of a rule the client already enforces, and it is not redundant</h2>
     *
     * {@code CheatFacility} keeps the facility off the {@code GameSession} port entirely, so today a
     * server-backed character has nothing to call. That is the primary defence and it is a good one
     * — but it is a fact about the <em>client's</em> wiring, and the engine is driven two ways: the
     * client in process for solo, and a home server for LAN and federated play. The day something
     * server-side reaches these methods, "the client would never call them" stops being an argument.
     * So the rules tier answers the question itself.
     *
     * <p>{@code GameSave.federable} is the flag that says this character was created on a home
     * server and its state is somebody else's to trust. A federable character never cheats, and every
     * hook below reads as untouched for one — so even a save that had cheats set and then somehow
     * became federable would play by the ordinary rules rather than carrying the overrides across.
     *
     * <p>⚠ This is <b>I14</b> and <b>I15</b> at their smallest scale. Every invariant these cheats
     * step over exists to keep a shared economy honest; the whole safety argument for the facility is
     * that it cannot reach one.
     */
    public static boolean mayCheat(GameSave save) {
        return save != null && !save.federable;
    }

    /**
     * The state the <em>rules</em> should read — untouched for a character that may not cheat.
     *
     * <p>Every hook goes through this rather than through {@link #of}, so the solo-only rule is
     * enforced once instead of at six call sites where the seventh would be written without it.
     */
    private static CheatState effective(GameSave save) {
        return mayCheat(save) ? of(save) : NONE;
    }

    // ================================================================== the hooks

    /**
     * The compute ceiling in force, given the one the ladder derived.
     *
     * <p>Called from {@code ComputeLadder.capacityOf}. Returns {@code derived} untouched when no
     * override is set, which is every character that has never opened the panel.
     */
    public static long ceiling(GameSave save, long derived) {
        CheatState cheats = effective(save);
        return cheats.cycleCeiling > 0 ? cheats.cycleCeiling : derived;
    }

    /** Whether an allocation released now should go through the Thermal Budget recovery curve. */
    public static boolean thermalRecovery(GameSave save) {
        return effective(save).thermalRecovery;
    }

    /**
     * A counter-hack chance, scaled by the override.
     *
     * <p>Called at the two places anything rolls to put a parasite on the player's rig — a sweep
     * that was noticed ({@code NetRules.beginSweep}) and a breach that was answered
     * ({@code BreachRules}). ⚠ <b>It scales the chance, never the draw.</b> Both call sites draw
     * unconditionally so that a replay from a stored seed stays a replay; a cheat that skipped or
     * added a draw would break that contract for every later roll in the session.
     *
     * <p>⚠ Clamped to {@code [0, 1]}. Above 500% a chance that was already high would exceed 1 and
     * the comparison would still work — but so would a negative percentage, which would silently
     * make the roll impossible rather than certain.
     */
    public static double intrusionChance(GameSave save, double base) {
        int percent = Math.max(0, Math.min(MAX_EVENT_CHANCE_PERCENT, effective(save).eventChancePercent));
        return Math.max(0.0d, Math.min(1.0d, base * percent / 100.0d));
    }

    /**
     * Whether a rule that wants to raise personal heat should be allowed to.
     *
     * <p>⚠ Asked by the rule that would raise it, not applied by clamping afterwards. Clamping after
     * the fact would leave {@code BreachState.resolvedHeat} reporting a rise that did not happen, so
     * the breach summary would name a number the meter never moved by.
     */
    public static boolean heatMayRise(GameSave save) {
        return !effective(save).heatFrozen;
    }

    /** Whether a breach should open with every layer already cleared. */
    public static boolean breachAutoClear(GameSave save) {
        return effective(save).breachAutoClear;
    }

    /**
     * Whether an uploaded Breach Virus is guaranteed to take hold — {@code docs/design/19} §5.
     *
     * <p>⚠ Asked where the roll is made, and it does <b>not</b> skip the draw. Same rule as the
     * intrusion scale one section down: both roll sites draw unconditionally so a replay from a
     * stored seed stays a replay, and only the answer is overridden.
     */
    public static boolean virusAlwaysHolds(GameSave save) {
        return effective(save).virusAlwaysHolds;
    }

    /** Makes every uploaded virus hold. */
    public static String setVirusAlwaysHolds(GameSave save, boolean on, Instant now) {
        if (!mayCheat(save)) {
            return REFUSED;
        }
        of(save).virusAlwaysHolds = on;
        arm(save);
        return log(save, "breach virus always holds: " + (on ? "on" : "off"), now);
    }

    /**
     * Whether a task that has not reached its deadline should finish anyway.
     *
     * <h2>⚠ Asked at the ONE place the engine decides a task is done</h2>
     *
     * {@code GameEngine.settleTasks} is the single gate every timed thing in the game passes
     * through — scans, sweeps, port scans, transfers, extractions, firmware flashes — so the hook
     * goes there and nowhere else. The tempting alternative is to collapse the deadline at each
     * commissioning site, which is eight edits today and is the shape that leaves the ninth kind of
     * task slow with nothing on screen to say why. Asking at settlement also means a task already
     * running when the switch is flipped finishes with it, which is what "skip the wait" has to mean
     * to be useful at all.
     *
     * <p>⚠ Everything a task normally does still happens. The settlement path is untouched — the
     * finding is reported, the held cycles rejoin the Thermal Budget curve, the download's file
     * arrives, the reprisal still rolls. This skips the <em>wait</em>, not the work, for
     * {@link #solveBreach}'s reason: a cheat whose visible effect is that the thing never happened
     * is one the player reads as broken.
     *
     * <p>⚠ <b>A HELD DOWNLOAD IS EXEMPT, and that exemption is load-bearing.</b> The queue expresses
     * a pause by pushing both ends of the task's clock forward on every tick, so a paused transfer is
     * exactly a task whose deadline never arrives — and a rule that ignores deadlines would therefore
     * complete the one download the player explicitly stopped. Two controls, one of them silently
     * overruling the other.
     */
    public static boolean finishesNow(GameSave save, TaskState task) {
        return effective(save).instantTasks && !DownloadQueue.isHeld(save, task);
    }

    /**
     * Whether a bought package is handed over without waiting for its payment to be mined.
     *
     * <h2>⚠ Asked by {@code Repac.locked}, which is the ONE place the hold is decided</h2>
     *
     * Install, resale, the arrival branch in {@code GameEngine.settleTasks} and the package panel's
     * manifest all route through that method, so the switch reaches every surface by being asked
     * once. Asking at those four sites instead would be four chances to miss one, and the one missed
     * would be a refusal the player cannot explain.
     *
     * <h2>⚠ IT WAIVES THE VENDOR'S ESCROW AND DOES NOT TOUCH THE CHAIN</h2>
     *
     * The hold is derived from the ledger row's {@code blockNumber}, so the obvious implementation is
     * to stamp that row confirmed. That is the one thing this must never do. The row is what the
     * block explorer reads, and a transaction claiming a block that never carried it would make the
     * LEDGER window lie about the chain — on the surface whose whole subject is what the chain says,
     * and in the same way {@code ChainRules} refuses to leave a transaction unconfirmed across an
     * absence because <em>that</em> would be the lie. With this on the purchase is still pending,
     * still in the mempool, still confirming at its own fee tier; the seller simply stops waiting.
     *
     * <p>⚠ Consequently the fee tier keeps its one mechanical consequence for anybody not cheating,
     * and gets it back the moment this goes off. Nothing about the mempool is bypassed — only the
     * escrow that hangs off it.
     */
    public static boolean purchasesAreInstant(GameSave save) {
        return effective(save).instantPurchases;
    }

    // ================================================================== the actions

    /**
     * Sets the compute ceiling, or clears the override.
     *
     * @param cycles the ceiling to hold, or {@code 0} to hand the rig back to the ladder
     * @return what happened, for the log and the panel
     */
    public static String setCycleCeiling(GameSave save, long cycles, Instant now) {
        if (!mayCheat(save)) {
            return REFUSED;
        }

        CheatState cheats = of(save);
        long wanted = cycles <= 0 ? 0L : Math.max(Balance.STARTING_CYCLES, Math.min(MAX_CYCLE_CEILING, cycles));
        cheats.cycleCeiling = wanted;
        arm(save);
        // Applied immediately rather than left to the next tick: reconcile is what writes the
        // ceiling into the rig, and a slider whose effect appeared a second later would read as one
        // that had not worked.
        ComputeLadder.reconcile(save);
        String what = wanted == 0
                ? "compute ceiling handed back to the ladder: " + save.rig.totalCycles + " cycles"
                : "compute ceiling forced to " + wanted + " cycles";
        return log(save, what, now);
    }

    /**
     * Adds ethecoin out of nothing.
     *
     * <p>⚠ <b>No ledger row.</b> The ledger is the chain's record of value moving between addresses,
     * and this money did not move — it was invented. A row for it would be a transaction with no
     * counterparty, which is the one thing a block explorer must never show. The rig log is where
     * this belongs and it says plainly where the money came from.
     */
    public static String grant(GameSave save, BigInteger wei, Instant now) {
        if (!mayCheat(save)) {
            return REFUSED;
        }

        if (wei == null || wei.signum() <= 0) {
            return "nothing granted";
        }
        BigInteger amount = wei.min(MAX_GRANT_WEI);
        save.ethecoinWei = save.ethecoinWei.add(amount);
        arm(save);
        return log(save, "granted " + Ethecoin.format(amount) + " out of nothing", now);
    }

    /** Sets the balance outright — the way back down, which {@link #grant} cannot provide. */
    public static String setBalance(GameSave save, BigInteger wei, Instant now) {
        if (!mayCheat(save)) {
            return REFUSED;
        }

        BigInteger wanted = wei == null || wei.signum() < 0 ? BigInteger.ZERO : wei.min(MAX_GRANT_WEI);
        save.ethecoinWei = wanted;
        arm(save);
        return log(save, "balance set to " + Ethecoin.format(wanted), now);
    }

    /** Sets personal heat. Clamped to the same 0–100 band every rule that raises it clamps to. */
    public static String setHeat(GameSave save, int heat, Instant now) {
        if (!mayCheat(save)) {
            return REFUSED;
        }

        int wanted = Math.max(0, Math.min(Balance.PERSONAL_HEAT_MAX, heat));
        save.personalHeat = wanted;
        arm(save);
        return log(save, "personal heat set to " + wanted, now);
    }

    /**
     * Discovers every machine in the world and names it.
     *
     * <p>Delegates to {@code NetRules} rather than walking the topology here: what a discovery
     * consists of — the host flag, the {@code knownNodes} row, the recon file's identity — is a
     * network rule, and a second copy of it would be a machine that appeared on the map behaving
     * unlike one a sweep found.
     *
     * @return how many machines this revealed, phrased for the panel
     */
    public static String revealNetwork(GameSave save, Instant now) {
        if (!mayCheat(save)) {
            return REFUSED;
        }

        int revealed = NetRules.revealAll(save, now);
        arm(save);
        return log(
                save,
                revealed == 0
                        ? "network reveal: every machine was already on the map"
                        : "network reveal: " + revealed + " machine" + (revealed == 1 ? "" : "s") + " added to the map",
                now);
    }

    /**
     * Fills in the recon file of every machine on the map — everything a port scan could establish.
     *
     * <p>⚠ Machines already <b>discovered</b> only, so it composes with {@link #revealNetwork}
     * rather than quietly doing its job as well: press both, in either order, for the whole world
     * fully scanned. A file on a machine the map has never heard of would show in RECON as a report
     * about something not on the map.
     *
     * <p>⚠ It does not count as a scan and does not touch the detection tally — see
     * {@code NodeReports.learnEverything}. And it costs no cycles and makes no noise, which is the
     * whole point: the ladder it skips is priced in cycles, duration and detection risk.
     */
    public static String learnEverything(GameSave save, Instant now) {
        if (!mayCheat(save)) {
            return REFUSED;
        }
        int filled = NetRules.learnEverything(save, now);
        arm(save);
        return log(
                save,
                filled == 0
                        ? "recon fill: no machine is on the map yet"
                        : "recon fill: " + filled + " machine" + (filled == 1 ? "" : "s") + " fully scanned",
                now);
    }

    /**
     * Plants a counter-hack on the player's own rig, now.
     *
     * <p>The manual trigger for the one event the game generates at the player rather than in
     * response to them. It goes through {@code IntrusionRules} — the same call a noticed sweep and an
     * answered breach both make — so a parasite planted from this panel is dressed, allocated and
     * heat-charged exactly like one that arrived on its own. A second planting path would be a
     * second class of parasite, and the one nobody could audit.
     *
     * @param depth 1–3; sets the tier, the appetite, whether it is rootkit-wrapped, and the heat
     */
    public static String triggerIntrusion(GameSave save, int depth, Instant now) {
        if (!mayCheat(save)) {
            return REFUSED;
        }

        int wanted = Math.max(1, Math.min(3, depth));
        var miner = IntrusionRules.plantCounterHack(save, wanted, now);
        arm(save);
        return log(save, "intrusion triggered by hand: tier " + miner.tier + " parasite planted", now);
    }

    /**
     * Rolls a real machine's answer against the player's rig, now.
     *
     * <h2>⚠ Why this exists beside {@link #triggerIntrusion}, which already plants one</h2>
     *
     * They are two different events and only one of them is an <em>attempt</em>.
     * {@code triggerIntrusion} plants a parasite unconditionally, which is the right harness for
     * testing removal — the breach board, the audit, the process table. This rolls
     * {@link ReprisalRules#answer}, which is the whole turn a noticed machine actually takes:
     * <b>mostly nothing, sometimes theft out of Downloads, occasionally a miner</b>, with a defended
     * machine hitting harder. Without it the only reachable path to a theft is to be detected during
     * a real port scan, which is not something a tester can arrange on demand.
     *
     * <h2>⚠ The distribution is NOT flattened for the panel, and that is deliberate</h2>
     *
     * Most presses report "noticed, and let it go", because most detections are. A developer control
     * that made theft likely would be exercising a game nobody plays — and the honest way to see all
     * three arms is to press it a few times, which costs nothing. The result line names which arm
     * came up, so a run of presses reads as a sample rather than as a control that does nothing.
     *
     * <h2>⚠ The attacker is a REAL machine off the map, never a synthetic one</h2>
     *
     * {@code ReprisalRules} reads {@code host.defended} to pick its table and names the address in
     * the log and the access log. A fabricated host would put an address on the player's own access
     * log that they can never go and look at — which is exactly the kind of evidence that surface is
     * for. A discovered machine is preferred, and a defended one before that, so the harsher table is
     * reachable at all; with an empty map it passes {@code null}, which {@code answer} already handles
     * as "somewhere" rather than throwing.
     */
    public static String triggerReprisal(GameSave save, Instant now) {
        if (!mayCheat(save)) {
            return REFUSED;
        }

        HostState from = attacker(save);
        Rng rng = Rng.of(save);
        ReprisalRules.Reprisal answer = ReprisalRules.answer(save, from, rng, now);
        rng.commit(save);
        arm(save);

        String who = from == null ? "an unknown machine" : from.address;
        return log(save, "reprisal rolled by hand from " + who + ": " + answer.message(), now);
    }

    /**
     * Picks the machine that answers: a discovered defended one, else any discovered one, else none.
     *
     * <p>⚠ Deterministic rather than drawn. A draw here would spend an RNG step on <em>choosing the
     * attacker</em> before {@code answer} spends one on the outcome, so the same save pressed twice
     * would roll a different answer for a reason that is not the answer's own roll — which is the
     * shape of a stored seed quietly stopping being a replay.
     *
     * <p>⚠ <b>It falls back to an UNDISCOVERED machine rather than to none, and that is the
     * difference between a control that works on a fresh character and one that does not.</b> Nothing
     * is discovered until the first sweep, so a discovered-only rule answers {@code null} — and
     * {@code answer} then reports the attacker as "somewhere", on exactly the press a tester makes
     * first. The real path cannot reach here undiscovered (a reprisal answers a port scan, and a scan
     * needs a machine on the map), so this fallback exists only for the panel. It hands the player an
     * address they have not swept, which on a developer control is not a leak worth pricing — the
     * button one section up reveals the entire map.
     */
    private static HostState attacker(GameSave save) {
        if (save.topology == null) {
            return null;
        }
        HostState discovered = null;
        HostState any = null;
        for (HostState host : save.topology.hosts) {
            if (HostKind.SELF.name().equals(host.kind)) {
                continue;
            }
            if (host.discovered && host.defended) {
                return host;
            }
            if (host.discovered && discovered == null) {
                discovered = host;
            }
            if (any == null) {
                any = host;
            }
        }
        return discovered != null ? discovered : any;
    }

    /**
     * Clears every layer of the breach that is open and resolves it as a success.
     *
     * <p>⚠ The breach still <em>resolves</em> — loot, heat, noise and the foothold all land, and the
     * counter-hack still rolls. Skipping the resolution instead would leave
     * {@code NetRules.reconcileFootholds} nothing to reconcile, so the machine would read as
     * unbreached on the map and refuse a shell: a cheat whose visible effect is the target not
     * opening.
     */
    public static String solveBreach(GameSave save, Instant now) {
        if (!mayCheat(save)) {
            return REFUSED;
        }

        if (save.activeBreach == null) {
            return "no breach is open";
        }
        boolean solved = BreachRules.solveEverything(save, now);
        arm(save);
        return solved ? log(save, "breach solved from the cheat panel", now) : "that breach has already resolved";
    }

    // ================================================================== the flags

    public static String setThermalRecovery(GameSave save, boolean on, Instant now) {
        if (!mayCheat(save)) {
            return REFUSED;
        }

        of(save).thermalRecovery = on;
        arm(save);
        return log(save, on ? "thermal recovery back on" : "thermal recovery off: cycles return instantly", now);
    }

    public static String setHeatFrozen(GameSave save, boolean frozen, Instant now) {
        if (!mayCheat(save)) {
            return REFUSED;
        }

        of(save).heatFrozen = frozen;
        arm(save);
        return log(save, frozen ? "personal heat frozen" : "personal heat moves normally again", now);
    }

    /**
     * Turns the wait on timed work off, or hands it back.
     *
     * <p>⚠ Nothing already running is edited. The switch changes what settlement asks, not what any
     * task says about itself, so turning it back off puts every task in flight straight back onto its
     * real clock — and a held download's shifted deadline, which is the only expression of its pause,
     * is never touched. See {@link #finishesNow}.
     */
    public static String setInstantTasks(GameSave save, boolean on, Instant now) {
        if (!mayCheat(save)) {
            return REFUSED;
        }

        of(save).instantTasks = on;
        arm(save);
        return log(
                save,
                on
                        ? "timed work finishes on the next tick: scans, sweeps, transfers and flashes skip their wait"
                        : "timed work takes its published duration again",
                now);
    }

    /**
     * Uncouples buying from the chain, or couples it back.
     *
     * <h2>⚠ Turning it ON releases what is already waiting, in the same call</h2>
     *
     * {@code setCycleCeiling}'s rule: a control whose effect appears a tick later, or only on the
     * next thing the player does, reads as one that did not work. The packages sitting in Downloads
     * right now are exactly the ones the player is looking at when they reach for this.
     *
     * <p>⚠ And releasing means <b>renaming</b>, not merely stopping the refusal. The
     * {@code .pkg} → {@code .upg} rename is the lock — there is no second mechanism — and
     * {@code install} checks the resulting kind immediately after the hold, so a package let through
     * by the hold alone would be refused one line later as "not an installable upgrade": a worse
     * message than the honest one it replaced. {@code Repac.releaseUnheld} does the renaming.
     *
     * <p>⚠ Turning it OFF re-locks nothing. Goods already handed over cannot be un-given, and those
     * payments confirm on their own anyway; what comes back is the wait on the <em>next</em>
     * purchase.
     */
    public static String setInstantPurchases(GameSave save, boolean on, Instant now) {
        if (!mayCheat(save)) {
            return REFUSED;
        }

        of(save).instantPurchases = on;
        arm(save);
        if (!on) {
            return log(save, "purchases wait for their payment to be mined again", now);
        }
        int released = Repac.releaseUnheld(save, now);
        return log(
                save,
                released == 0
                        ? "purchases no longer wait for a block; nothing was being held"
                        : "purchases no longer wait for a block; " + released + " package"
                                + (released == 1 ? "" : "s") + " released",
                now);
    }

    public static String setBreachAutoClear(GameSave save, boolean on, Instant now) {
        if (!mayCheat(save)) {
            return REFUSED;
        }

        of(save).breachAutoClear = on;
        arm(save);
        return log(save, on ? "breaches open pre-solved" : "breaches play normally again", now);
    }

    public static String setEventChance(GameSave save, int percent, Instant now) {
        if (!mayCheat(save)) {
            return REFUSED;
        }

        int wanted = Math.max(0, Math.min(MAX_EVENT_CHANCE_PERCENT, percent));
        of(save).eventChancePercent = wanted;
        arm(save);
        return log(save, "counter-hack chance scaled to " + wanted + "% of the tuned rule", now);
    }

    /**
     * Puts every override back to the ordinary rules.
     *
     * <p>⚠ Leaves {@link CheatState#revealed} alone, deliberately. Turning the last cheat off must
     * not take away the page that turned it off — a player who reset and then wanted one back would
     * have to find the key sequence again, and the sequence is precisely the thing nobody remembers.
     */
    public static String reset(GameSave save, Instant now) {
        if (!mayCheat(save)) {
            return REFUSED;
        }

        CheatState cheats = of(save);
        boolean anything = cheats.anyInForce();
        cheats.cycleCeiling = 0L;
        cheats.thermalRecovery = true;
        cheats.heatFrozen = false;
        cheats.breachAutoClear = false;
        cheats.virusAlwaysHolds = false;
        cheats.instantTasks = false;
        // ⚠ Clears the override, and deliberately does not un-release anything it let through. See
        // setInstantPurchases: goods cannot be un-given, and those payments confirm on their own.
        cheats.instantPurchases = false;
        cheats.eventChancePercent = 100;
        ComputeLadder.reconcile(save);
        // ⚠ Logged only when something actually moved. A reset that changed nothing must leave no
        // trace: it is the one action here a player can take without having cheated, and a WARNING
        // line naming this facility would be the single place the game admits the feature exists to
        // somebody who has not used it. Everything else in this class logs unconditionally, because
        // everything else has by definition already changed the character.
        return anything
                ? log(save, "every override cleared; the rig is back on the ordinary rules", now)
                : "nothing was overridden; the rig is already on the ordinary rules";
    }

    /**
     * Turns everything off and hides the page again.
     *
     * <h2>⚠ It RESETS as well as hiding, and that is one act rather than two glued together</h2>
     *
     * {@link CheatState#revealed} exists so a character carrying a disabled thermal budget always
     * has a visible control to re-enable it. Hiding the page while leaving the overrides in force
     * would create precisely the state that flag was added to prevent — a permanently altered
     * character with nothing on screen to say why, recoverable only by remembering a key sequence.
     * So this is: <em>put the character back, and forget I was here.</em>
     *
     * <p>⚠ It writes no log line of its own. {@link #reset} writes one if something was actually in
     * force, which is the honest record of a character that was altered; concealing on one that
     * never was writes nothing at all — otherwise the act of tidying up would be the thing that
     * gives the feature away.
     */
    public static String conceal(GameSave save, Instant now) {
        if (!mayCheat(save)) {
            return REFUSED;
        }
        reset(save, now);
        of(save).revealed = false;
        return "developer options hidden; everything is back to the ordinary rules";
    }

    // ================================================================== plumbing

    /**
     * Pins the page visible from the first cheat applied.
     *
     * <p>⚠ Called by the actions, never by the key sequence. Entering the code is a look; applying a
     * cheat leaves state behind, and a character carrying a disabled thermal budget with no visible
     * control to re-enable it is one that looks broken rather than cheated.
     */
    private static void arm(GameSave save) {
        of(save).revealed = true;
    }

    /**
     * ⚠ WARNING, not notice. The rig log is the record a player reads to work out why their numbers
     * are what they are, and a cheat is the loudest possible answer to that question — it should not
     * sit at the same level as a block landing.
     */
    private static String log(GameSave save, String what, Instant now) {
        EventLog.warning(save, "cheat", what, now);
        return what;
    }
}
