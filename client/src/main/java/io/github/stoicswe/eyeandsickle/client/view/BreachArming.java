package io.github.stoicswe.eyeandsickle.client.view;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Which target the breach window is <b>pointed at</b>, shared across the windows that can point it.
 *
 * <h2>Aiming and firing are two steps — in the LIST. They are one gesture from the map's node menu.</h2>
 *
 * A breach reserves compute for the whole attempt and cannot be undone into a refund — aborting is a
 * sanctioned outcome, not a free one ({@code docs/design/05-hacking-minigame.md} §4). So in the target
 * <b>list</b> the control that <em>chooses</em> and the control that <em>commits</em> are not the same
 * click. Before that split existed the list began an attempt the instant a row's button was pressed,
 * which put an irreversible spend one mis-click away <em>in a list that reflows whenever a sweep
 * lands</em>. Arming there is free, reversible, and says so; the single START BREACH control is the
 * commitment, and it is one control rather than one per row because there is exactly one thing that
 * can be started.
 *
 * <h2>⚠ THE MAP'S NODE MENU IS EXEMPT (2026-08-07, on explicit direction) — AND THE REASON MATTERS</h2>
 *
 * Right-clicking a machine and choosing <b>Breach</b> now jumps to the BREACH tab and starts the
 * attempt, with no second press. That reverses the rule above for one entry point, so the exemption
 * is fenced by the hazard the rule was actually written against, which was <b>a reflowing list</b>:
 * you aimed at row three, a sweep landed, the rows moved, and row three was a different machine that
 * had already been committed to.
 *
 * <p>A context menu has none of that. It is opened <em>on</em> a specific node, it is anchored where
 * the pointer is, it names the act on its face, and nothing can reflow between choosing the machine
 * and choosing the verb — because they are the <b>same gesture on the same object</b>. The two-step
 * exists to separate "which one" from "go", and a per-node menu has already fused them unambiguously.
 * A second confirmation there is asking a question the player has just answered.
 *
 * <p>⚠ <b>Do not generalise this to the list.</b> The moment a start can be triggered from a control
 * whose subject can move underneath it, the original defect is back — and it costs cycles that no
 * outcome refunds. {@link #takeStartRequest} is deliberately keyed on the <em>target id</em> for the
 * same reason: a request made for one machine can never fire on another.
 *
 * <p>⚠ The map's own BREACH chip is <b>not</b> exempt and still arms only. It acts on whatever was
 * selected earlier, which can be stale by the time it is pressed — the menu's whole safety argument is
 * that the target is the thing under the pointer right now, and the chip cannot claim that.
 *
 * <h2>Why it is a client object and not a rule</h2>
 *
 * Nothing here is game state. An armed target is an intention the player has not acted on — the
 * engine does not know it exists, it is not saved, and closing the window forgets it. The rules learn
 * about it exactly once, when {@code beginBreach} is called with an id, and they refuse or accept on
 * their own terms as always (Invariant <b>I14</b>). If this object ever grows a method that answers
 * "can I breach this", that answer has escaped the engine.
 *
 * <h2>It also carries the door</h2>
 *
 * {@link #open} is how the network map raises the breach window without knowing that a desk, a window
 * registry or a {@code WindowSpec} exist. The alternative was handing {@code NetMapView} the deck,
 * which would make a panel that renders a graph also a thing that manages windows.
 */
public final class BreachArming {

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private String targetId = "";
    private Runnable opener = () -> {};

    /**
     * The target a one-gesture start was asked for, or {@code ""}.
     *
     * <h2>⚠ THE TARGET ID, NOT A BOOLEAN, AND THAT IS THE SAFETY PROPERTY</h2>
     *
     * A flag saying "somebody wants a breach started" can be consumed by whatever happens to be armed
     * when it is next read. The sequence that produces is real and cheap to hit: right-click Breach on
     * a machine the rules will not accept as a target, so nothing starts and the flag survives; then
     * arm a different machine from the list, and <b>that</b> one is committed to with no press at all.
     * The player has just spent cycles they cannot get back on a machine they did not choose.
     *
     * <p>Keying it on the id makes that unrepresentable rather than guarded against.
     */
    private String startRequestFor = "";

    /**
     * Points the breach window at a target id, or clears it with {@code ""}.
     *
     * <p>Takes the {@code targetId} the rules publish ({@code "node:10.0.0.4"}, {@code "miner:<uuid>"})
     * rather than an address, because that is what {@code beginBreach} takes and a client-side
     * translation between the two would be a second place that could get the prefix wrong.
     */
    public void arm(String targetId) {
        String wanted = targetId == null ? "" : targetId;
        if (wanted.equals(this.targetId)) {
            return;
        }
        this.targetId = wanted;
        dropStaleRequest();
        notifyListeners();
    }

    /**
     * Points at a target and <b>always notifies</b>, even if it was already the armed one.
     *
     * <h2>⚠ Why {@link #arm} cannot just do this</h2>
     *
     * {@code arm} deliberately no-ops on an unchanged id, because it is called from inside the breach
     * panel's own refresh — {@code arm("")} runs there whenever an armed target stops being
     * available — and a notification on every refresh would re-enter the refresh that sent it.
     *
     * <p>But the network map's BREACH control is a different statement. It means <em>start fresh on
     * this machine</em>, and a player who presses it twice on the same node means it twice. Under
     * the no-op the second press changed nothing and the breach panel never heard about it, so a
     * resolved outcome from a previous attempt stayed on screen with no way past it but Dismiss.
     */
    public void rearm(String targetId) {
        this.targetId = targetId == null ? "" : targetId;
        dropStaleRequest();
        notifyListeners();
    }

    /**
     * Points at a target <b>and asks for it to be started</b> — the map's node menu, and only it.
     *
     * <p>See the class note for why one entry point is exempt from the two-step. This is a request
     * rather than a call to the rules: the panel that owns the START control is the thing that knows
     * whether a start is possible right now, and it consumes this through
     * {@link #takeStartRequest(String)} exactly once.
     *
     * <p>⚠ It does <b>not</b> begin anything itself. Reaching {@code beginBreach} from here would put
     * a spend in an object whose own class note promises it holds no game state and asks the rules
     * nothing — and would bypass the panel's one-pulse settle, which exists because this is called
     * from inside a click handler that is about to build that panel.
     */
    public void armAndStart(String targetId) {
        String wanted = targetId == null ? "" : targetId;
        this.targetId = wanted;
        this.startRequestFor = wanted;
        notifyListeners();
    }

    /**
     * Answers true at most once, and only for the target the request was made for.
     *
     * <p>⚠ Test-and-clear in one call, the arrangement {@code Sfx.claim} and {@code takeChainSync}
     * both use. A request that could be read twice would start a second breach the next time the
     * panel refreshed; a request that was never cleared would fire on a later, unrelated target.
     *
     * <p>⚠ A non-matching id clears it too, and that is deliberate rather than sloppy: if something
     * else is armed by the time this is asked, the player has moved on and the request is stale.
     */
    public boolean takeStartRequest(String targetId) {
        boolean mine = !startRequestFor.isEmpty() && startRequestFor.equals(targetId);
        startRequestFor = "";
        return mine;
    }

    /**
     * Drops a pending request without acting on it.
     *
     * <p>Called by the panel when the armed target stops being startable at all — the machine is no
     * longer a valid target, or a breach is already running. Without it the request would sit there
     * and fire whenever the next target became live, which is a spend the player never asked for at
     * a moment they were not expecting one.
     */
    public void clearStartRequest() {
        startRequestFor = "";
    }

    /** A request belongs to one machine; anything that re-points the arming invalidates it. */
    private void dropStaleRequest() {
        if (!startRequestFor.equals(targetId)) {
            startRequestFor = "";
        }
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }

    /** The armed id, or {@code ""}. Never null. */
    public String armed() {
        return targetId;
    }

    public boolean isArmed() {
        return !targetId.isEmpty();
    }

    /**
     * Opens the breach window, or raises it if it is already on the desk.
     *
     * <p>⚠ Since UI-8 this genuinely opens a window rather than raising the network tool, and the
     * comment that used to sit on the map's BREACH chip — <em>"must not move the player off the
     * map"</em> — no longer describes a hazard. The concern was that arming, the free and reversible
     * half of the two-step, would yank the view away from the graph the player was reading. A second
     * window does not take the first one away, so both callers can open it and neither loses anything.
     *
     * <p>Does nothing before the deck has wired {@link #setOpener}.
     */
    public void open() {
        opener.run();
    }

    /** Called once by the client, after the deck exists. */
    public void setOpener(Runnable opener) {
        this.opener = opener == null ? () -> {} : opener;
    }

    // ⚠ `focusBreach` / `setBreachFocus` DELETED 2026-08-08 with UI-8.
    //
    // They existed for one reason: `open()` raised the NETWORK window, which opens on MAP, so
    // choosing Breach from a node menu armed a target, raised a window the player was already looking
    // at, and left the breach one tab away with nothing saying so. A second door was needed to select
    // the tab.
    //
    // The breach is its own window now, so raising it IS showing it, and the second door has nothing
    // to do. Deleting it rather than leaving a no-op matters here: it was registered afresh by
    // NetworkView on every build precisely because a stale TabPane reference would silently select a
    // tab in a closed window, and a no-op version of that would be a live hook nobody could see was
    // dead.

    /**
     * Registers a listener called whenever the armed target changes.
     *
     * @return a handle that removes it — held and closed by the panel, because a view that outlives
     *     its own subscription is the leak {@code NetMapView} was fixed for
     */
    public AutoCloseable onChange(Runnable listener) {
        if (listener == null) {
            return () -> {};
        }
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }
}
