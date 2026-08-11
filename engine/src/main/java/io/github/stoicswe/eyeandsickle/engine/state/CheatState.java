package io.github.stoicswe.eyeandsickle.engine.state;

/**
 * The developer/cheat overrides in force on one character.
 *
 * <h2>⚠ Why this is persisted at all</h2>
 *
 * A cheat that lasted one session would be a cheat that silently turned itself off, and the player
 * would read that as the game reverting their money rather than as the facility expiring. More
 * importantly {@link #revealed} has to survive a restart: the panel is reached by a key sequence
 * nobody will remember, and a character left with thermal recovery disabled and no visible way to
 * turn it back on is a character that looks broken. Using any cheat pins the page visible forever
 * after — see {@code engine/rules/Cheats}.
 *
 * <h2>⚠ Every field here is read by a rule, and that is the point</h2>
 *
 * {@code NoteState} carries the opposite promise ("nothing in the notebook is read by any rule").
 * This class is its inverse and the only one of its kind: these <em>are</em> save-editable inputs to
 * the rules, deliberately. That is survivable for exactly one reason, and it is not a property of
 * this file — it is that a character carrying cheats has no route to a server. {@code GameSession}'s
 * remote implementation refuses every cheat call, so nothing here can reach a shared economy, and
 * the invariants those cheats step over ({@code I1}, {@code I2}, {@code I5}) exist to keep a shared
 * economy honest. A solo save was always as editable as this makes it; what changed is that the
 * game now provides the editor.
 *
 * <h2>⚠ Defaults are "no cheat in force", and they must stay that way</h2>
 *
 * Every field's initialiser is the behaviour of a character that has never opened the panel, so a
 * save written before this class existed loads as an ordinary character. A default that meant
 * anything else would apply a cheat to every character in the game.
 */
public final class CheatState {

    /**
     * Whether the cheats page is shown without the key sequence being entered again.
     *
     * <p>Set the first time any cheat is applied, never by the key sequence itself — entering the
     * code is a look, applying a cheat is a commitment, and only the second one leaves state behind
     * that the player will later need a way back to.
     */
    public boolean revealed = false;

    /**
     * The compute ceiling to use instead of the one the ladder derives, or {@code 0} for "no
     * override".
     *
     * <h2>⚠ An override on the DERIVED value, never a write to {@code RigState.totalCycles}</h2>
     *
     * {@code ComputeLadder.reconcile} recomputes the ceiling from the items held and writes it into
     * the rig on every load and after every upgrade — that is what stops a hand-edited
     * {@code totalCycles} granting the whole ladder. So a cheat that set the field directly would be
     * reverted by the next reconcile, silently, and the player would report the slider as not
     * working. {@code ComputeLadder.capacityOf} consults this instead, which keeps one answer to
     * "what is this rig's ceiling" rather than two that can disagree.
     */
    public long cycleCeiling = 0L;

    /**
     * Whether releasing an allocation goes through the Thermal Budget recovery curve.
     *
     * <p>Default {@code true} — the ordinary rule. Off, {@code ComputeRules.beginRecovery} returns
     * the cycles immediately instead of parking them in {@code RECOVERING}.
     */
    public boolean thermalRecovery = true;

    /**
     * Whether personal heat is pinned where the player put it.
     *
     * <p>On, every rule that would raise heat leaves it alone. Deliberately a freeze rather than a
     * "no heat" flag: the slider is the way to choose a value, and a flag that forced zero would
     * quietly overrule it.
     */
    public boolean heatFrozen = false;

    /**
     * Whether a breach opens with every layer already cleared.
     *
     * <p>The breach still runs — it is begun, resolved, and its loot, heat and noise all land — so
     * this is "the puzzle is solved for you", not "the breach is skipped". A skip would leave
     * {@code NetRules.reconcileFootholds} with nothing to reconcile and the machine unbreached.
     */
    public boolean breachAutoClear = false;

    /**
     * Every uploaded Breach Virus takes hold — {@code docs/design/19} §5.
     *
     * <p>⚠ Turns a probabilistic step into a certain one, which is what makes the breach loop
     * <b>testable</b> as well as demonstrable: without it a solved board lands 55–90% of the time and
     * every assertion downstream of a successful breach is flaky by construction.
     */
    public boolean virusAlwaysHolds = false;

    /**
     * Whether a timed task finishes the moment the engine next looks at it, deadline ignored.
     *
     * <h2>⚠ A settlement rule, never a write to {@code TaskState.endsAt}</h2>
     *
     * Collapsing the deadline is the obvious implementation and it is wrong twice. A held market
     * download is paused precisely <em>by</em> having both ends of its clock pushed forward every
     * tick ({@code DownloadQueue.settle}), so rewriting {@code endsAt} would corrupt the one field
     * that expresses the hold; and a task whose deadline had been overwritten would stay instant
     * after the switch went off, because there would be nothing left to say how long it should have
     * taken. The deadline is left alone and {@code GameEngine.settleTasks} is asked instead — so
     * turning this off mid-task hands that task straight back to its real clock.
     *
     * <p>⚠ It is "on the next tick", not "in the same call". Nothing commissions and settles a task
     * in one breath, and the engine ticks once a second, so a scan, sweep, port scan, transfer,
     * extraction or firmware flash lands within about a second of being started rather than at the
     * instant of the call. Making it truly synchronous would mean a hook at every commissioning site
     * — eight of them today — which is the shape that leaves the ninth one slow and nobody knowing
     * why.
     */
    public boolean instantTasks = false;

    /**
     * Whether a bought package is handed over without waiting for its payment to be mined.
     *
     * <h2>⚠ It waives the VENDOR'S escrow. It does not fake the chain.</h2>
     *
     * A purchase is held by {@code Repac.locked}, which derives the hold from the ledger row's
     * {@code blockNumber} — so the tempting implementation is to stamp that row confirmed and be done
     * with it. That is the one thing this must never do: the row is what the block explorer reads,
     * and a transaction reporting itself mined in a block that never carried it would make the
     * LEDGER window lie about the chain, which is the surface whose entire subject is what the chain
     * says. With this on, the ledger still shows the purchase pending, the mempool still carries it,
     * and it still confirms in its own time at its own fee tier. What changes is that the seller
     * stops waiting.
     *
     * <p>⚠ Turning it off does not re-lock anything already handed over. Goods cannot be un-given,
     * and the payments in question confirm on their own anyway.
     */
    public boolean instantPurchases = false;

    /**
     * What the counter-hack chance is scaled by, as a percentage. {@code 100} is the tuned rule.
     *
     * <p>Applied at the two places anything rolls to put a parasite on the player's rig — a sweep
     * that was noticed and a breach that was answered. {@code 0} switches them off; {@code 400}
     * makes the network answer four times as often, which is how the intrusion path gets exercised
     * without waiting for it.
     */
    public int eventChancePercent = 100;

    public CheatState() {}

    /** Whether anything here differs from an untouched character. Drives the panel's own summary. */
    public boolean anyInForce() {
        return cycleCeiling > 0
                || !thermalRecovery
                || heatFrozen
                || breachAutoClear
                || virusAlwaysHolds
                || instantTasks
                || instantPurchases
                || eventChancePercent != 100;
    }
}
