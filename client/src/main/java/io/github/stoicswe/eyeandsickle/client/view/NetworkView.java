package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.teaching.TermDatabase;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Region;

/**
 * NETWORK — the map, what has been learned about a target, and the way in.
 *
 * <h2>Why these three are one tool</h2>
 *
 * They are three views of the same subject: a machine out there. The map says where it is, recon
 * says what is known about it, and the breach is what you do about it. Three separate windows made
 * the player assemble that relationship themselves every time.
 *
 * <h2>⚠ THE BREACH IS NOT ONE OF THEM ANY MORE — UI-8, resolved 2026-08-08</h2>
 *
 * It was the fourth tab here, and {@code docs/client/05} §44 argued against that from the day it
 * landed: a breach is meant to span windows <em>the way a real operator's desk does</em> — map for
 * traversal, terminal for the active layer, recon for the logs the human-read steps depend on — and
 * the puzzle's anti-bot property (<b>I10</b>) is precisely that a human cross-references material a
 * fixed heuristic cannot. <em>"Cross-referencing two documents is a simultaneity problem. A tabbed
 * shell makes it a memory problem instead, which is a different and worse game."</em>
 *
 * <p>As a tab, the map and the board could not be on screen together. As a window they can, and the
 * player arranges them the way they arrange everything else on this desk. <b>The breach is the one
 * part of this loop that gets its own window</b>, because it is the one part that is an act with a
 * duration rather than a view onto state.
 *
 * <p>⚠ The deferral this comment used to carry — <em>"nothing breaks today, the minigame is not
 * built"</em> — went void on 2026-08-07 when {@code design/16} landed, and the cost was being paid
 * from then until now.
 *
 * <p>⚠ <b>The node menu's one-gesture breach is unaffected and is better for it.</b> It armed, raised
 * the window the player was already looking at, and then had to select a tab; there was a second
 * door, {@code focusBreach}, existing only to do that. It opens a window now, which is what raising a
 * window has always meant, and the second door is gone.
 */
public final class NetworkView {

    private NetworkView() {}

    /**
     * @param session the session
     * @param arming the breach arming state, shared with the deck
     * @param nodeActions the map's node menu wiring
     * @param terms unused since the breach left this window; kept so the one call site does not have
     *     to change shape for a removal, and because RECON is the next candidate for a teaching layer
     * @param profile unused, as above
     * @return the tabbed network tool
     */
    public static Region create(
            GameSession session,
            BreachArming arming,
            NetMapView.NodeActions nodeActions,
            TermDatabase terms,
            ClientProfile profile) {

        TabPane tabs = new TabPane();
        tabs.getStyleClass().add("es-market-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // ⚠ MAP first. It is the only one of the three that answers "what is out there at all", so
        // it is where somebody with no target starts — and the other two are about a target.
        tabs.getTabs()
                .addAll(
                        new Tab("MAP", NetMapView.create(session, arming, nodeActions)),
                        new Tab("RECON", ReconView.create(session, nodeActions::info)),
                        // ⚠ BOTNET last, and the order is the operational sequence rather than an
                        // alphabet: find a machine, study it, get in, and then what you left running
                        // on it. A bot is the residue of the three tabs to its left.
                        new Tab("BOTNET", BotnetView.create(session)));

        return tabs;
    }
}
