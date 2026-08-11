package io.github.stoicswe.eyeandsickle.client.session;

import java.math.BigInteger;
import java.util.Optional;

/**
 * The developer/cheat facility, as the client sees it.
 *
 * <h2>⚠ DELIBERATELY NOT ON {@link GameSession}, and that absence is the safety mechanism</h2>
 *
 * Every other capability in this client binds to the port so a view never learns whether it is
 * talking to the in-process engine or a home server. This one must not, for exactly the reason
 * {@code GameEngine.rename} is not on the port either, in its own words: <em>putting it on the port
 * would advertise a capability that must never work online, and the honest way to make something
 * impossible is for it to be absent.</em>
 *
 * <p>A cheat applied to a character on a home server would be forged authoritative state —
 * Invariant <b>I14</b>, and <b>I15</b> the moment that character touched a federated outcome. Making
 * cheats a port method would mean {@code RemoteGameSession} carrying a dozen refusal stubs, and a
 * dozen refusals is a dozen chances for one of them to be implemented "just for testing". There is
 * nothing to refuse if there is nothing to call: {@link #forSession} is the single place that
 * decides a session may cheat, and it answers empty for anything that is not solo.
 *
 * <h2>The panel binds to this and to nothing else</h2>
 *
 * {@code view/CheatsView} takes one of these or is not built at all, so the page cannot exist for a
 * character that has no facility behind it. The engine-side rules all live in
 * {@code engine/rules/Cheats}; this interface is the seam, not a second set of rules.
 */
public interface CheatFacility {

    /**
     * Everything the panel draws, read in one go.
     *
     * <p>One snapshot rather than a dozen getters for the reason every other readout in this client
     * takes one: the panel repaints as a unit, and twelve separate reads can disagree with each
     * other about a state that moved between the first and the last.
     *
     * @param revealed whether the page stays visible without the key sequence
     * @param cycleCeiling the override in force, or {@code 0} when the ladder decides
     * @param effectiveCycles what the rig's ceiling actually is right now, override or not
     * @param ladderCeiling what the ladder would give with no override — the "back to normal" value
     * @param thermalRecovery whether released cycles go through the recovery curve
     * @param heatFrozen whether personal heat is pinned
     * @param breachAutoClear whether a breach opens pre-solved
     * @param instantTasks whether timed work skips its wait
     * @param runningTasks how many timed tasks are in flight right now, so the page can say what the
     *     switch would land immediately rather than leaving the player to guess
     * @param instantPurchases whether a bought package is handed over without waiting for a block
     * @param heldPackages how many bought packages are waiting on a payment right now
     * @param eventChancePercent the counter-hack chance scale; {@code 100} is the tuned rule
     * @param heat personal heat now, 0–100
     * @param balanceWei the balance now
     * @param breachOpen whether there is an unresolved breach to solve
     * @param hiddenMachines how many machines the map has not been shown yet
     * @param unscannedMachines how many machines on the map have an incomplete recon file
     */
    record Snapshot(
            boolean revealed,
            long cycleCeiling,
            long effectiveCycles,
            long ladderCeiling,
            boolean thermalRecovery,
            boolean heatFrozen,
            boolean breachAutoClear,
            boolean instantTasks,
            int runningTasks,
            boolean instantPurchases,
            int heldPackages,
            int eventChancePercent,
            int heat,
            BigInteger balanceWei,
            boolean breachOpen,
            int hiddenMachines,
            int unscannedMachines) {}

    /** What the panel draws. Never null. */
    Snapshot state();

    /** Forces the compute ceiling, or hands the rig back to the ladder with {@code 0}. */
    String setCycleCeiling(long cycles);

    /** Adds ethecoin out of nothing. */
    String grant(BigInteger wei);

    /** Sets the balance outright — the way back down that {@link #grant} cannot give. */
    String setBalance(BigInteger wei);

    /** Sets personal heat, 0–100. */
    String setHeat(int heat);

    /** Puts every machine in the world on the map. */
    String revealNetwork();

    /** Fills in the recon file of every machine already on the map. */
    String learnEverything();

    /** Plants a counter-hack on the player's own rig now. {@code depth} is 1–3. */
    String triggerIntrusion(int depth);

    /**
     * Rolls a real machine's answer against the rig — nothing, theft, or a miner.
     *
     * <p>The <em>attempt</em>, where {@link #triggerIntrusion} is the guaranteed outcome. Most rolls
     * report that the machine noticed and let it go, because that is what the rule does.
     */
    String triggerReprisal();

    /** Clears every layer of the open breach and resolves it as a success. */
    String solveBreach();

    String setThermalRecovery(boolean on);

    String setHeatFrozen(boolean frozen);

    String setBreachAutoClear(boolean on);

    /**
     * Skips the wait on timed work — scans, sweeps, port scans, transfers, extractions, flashes.
     *
     * <p>⚠ The work still happens; only the duration is skipped. And it lands on the engine's next
     * tick rather than in this call, because settlement is where the rule is asked — see
     * {@code Cheats.finishesNow}.
     */
    String setInstantTasks(boolean on);

    /**
     * Hands over a bought package without waiting for its payment to be mined.
     *
     * <p>⚠ It waives the seller's escrow; it does not fake the chain. The ledger row stays pending
     * and confirms in its own time — see {@code Cheats.purchasesAreInstant}. Turning it on releases
     * whatever is already waiting, in the same call.
     */
    String setInstantPurchases(boolean on);

    /** Scales the counter-hack chance. {@code 100} is the tuned rule. */
    String setEventChance(int percent);

    /** Puts every override back to the ordinary rules, leaving the page visible. */
    String reset();

    /**
     * Turns everything off <em>and</em> hides the page again.
     *
     * <p>⚠ Resets as well as hiding, deliberately — hiding while leaving overrides in force is the
     * one state the visibility flag exists to prevent. See {@code Cheats.conceal}.
     */
    String conceal();

    /**
     * The facility for this session, or empty when this session may not cheat.
     *
     * <h2>⚠ The one place the solo-only rule is decided</h2>
     *
     * Every caller asks here rather than testing the mode itself. A second site testing
     * {@code mode() == SOLO} would be a second answer to "may this character cheat", and the day the
     * two disagreed the one that said yes would be the one that mattered.
     *
     * <p>Empty for a null session too — the Settings window opens from the login screen, where there
     * is no character to cheat at.
     *
     * <h2>⚠ Two conditions, and the second is not redundant</h2>
     *
     * The type test alone is sufficient <em>today</em>: {@link LocalGameSession#mode()} hard-returns
     * {@link SessionMode#SOLO} and there is one place in the client that constructs one. But "true by
     * construction today" is the kind of premise this codebase has repeatedly watched go stale —
     * {@code LocalGameSession} is a thin adapter over the engine, and the engine is the same one a
     * home server drives for LAN play. If it is ever pressed into service for a LAN game, the type
     * test would keep saying yes while the answer had become no, and cheats would silently be live in
     * multiplayer. Asking the mode as well means that change makes the page disappear rather than
     * making it dangerous.
     */
    static Optional<CheatFacility> forSession(GameSession session) {
        return session instanceof LocalGameSession local && local.mode() == SessionMode.SOLO
                ? Optional.of(local.cheats())
                : Optional.empty();
    }
}
