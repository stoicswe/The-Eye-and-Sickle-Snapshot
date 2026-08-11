package io.github.stoicswe.eyeandsickle.client.view;

import java.util.function.Consumer;

/**
 * The seam between "somebody is trying to get in" and the window where that is played out.
 *
 * <h2>⚠ Why this exists rather than a direct call</h2>
 *
 * The thing that decides a defence round should happen is a <b>rule</b> — a reprisal, a counter-hack,
 * eventually an NPC raid — and rules live in the engine, which has never known what a window is.
 * The thing that can open a window is {@code EyeAndSickleClient}, which has never known what a
 * reprisal is. {@link BreachArming} is the same shape one door along, and {@code NodeActions} and
 * {@code RigMonitorView.free}'s unmount seam are the same pattern again: the join lives in the one
 * place that holds both halves, and everybody else holds a function.
 *
 * <h2>⚠ The outcome must reach exactly one caller, exactly once</h2>
 *
 * That is {@code DefenseGameView}'s contract and it is the whole reason the prototype was built
 * before the minigame was chosen. A round that resolved twice would apply its consequence twice —
 * and the consequence is whether an intrusion lands.
 */
public final class DefenseArming {

    /** What the deck needs to open a round: the subject, the attacker's virus tier, and the sink. */
    @FunctionalInterface
    public interface Opener {
        void open(String subject, int attackerVirusTier, Consumer<DefenseGameView.Outcome> onResolved);
    }

    private Opener opener = (subject, tier, onResolved) -> {};

    private boolean open;

    /**
     * Points this at the deck.
     *
     * @param opener given what is being defended against and where to send the one outcome
     */
    public void setOpener(Opener opener) {
        this.opener = opener == null ? (subject, tier, onResolved) -> {} : opener;
    }

    /**
     * Opens a defence round.
     *
     * <p>⚠ Fire-and-forget by design: the caller does not wait, because a round takes up to thirty
     * seconds of real time and the thing that started it is usually a rule mid-tick. The consequence
     * is applied from {@code onResolved} when the player is done.
     *
     * @param subject what is coming, in words — an address, a process name
     * @param onResolved handed exactly one outcome, exactly once
     */
    public void open(String subject, int attackerVirusTier, Consumer<DefenseGameView.Outcome> onResolved) {
        Consumer<DefenseGameView.Outcome> sink = onResolved == null ? outcome -> {} : onResolved;
        open = true;
        opener.open(subject, attackerVirusTier, outcome -> {
            open = false;
            sink.accept(outcome);
        });
    }

    /**
     * Whether a round is on screen right now.
     *
     * <p>⚠ Tracked HERE rather than by each caller. Two rounds at once is two clocks, two outcomes and
     * two settlements for one attack — and the ambient trigger fires off a listener that runs on most
     * ticks, so without this it would open a round on top of the round it opened last tick.
     */
    public boolean isOpen() {
        return open;
    }
}
