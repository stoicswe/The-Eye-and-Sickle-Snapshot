package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors;
import io.github.stoicswe.eyeandsickle.protocol.game.BotFunction;
import io.github.stoicswe.eyeandsickle.protocol.game.BotModifier;
import io.github.stoicswe.eyeandsickle.protocol.game.BotView;
import io.github.stoicswe.eyeandsickle.protocol.game.BotnetSnapshot;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.Sighting;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * BOTNET — {@code docs/design/10-botnets.md} §2, §5.
 *
 * <h2>Three sections, and the order is the decision the player is making</h2>
 *
 * What is running, what is built and idle, and what the Watchers saw. The workshop sits under the
 * running bots rather than above them because a botnet's live state is what a player opens this
 * window for; assembling is the thing they do once and then leave alone.
 *
 * <h2>⚠ Repaints on {@code onChange}, never on {@code Pulse}</h2>
 *
 * Nothing here is derived from wall time. A one-second repaint would be work with no subject and
 * would tear down an open {@code MenuButton} popup under the pointer — the defect {@code ReconView}
 * records as <b>UI-7</b> and {@code Views.firewall} avoids the same way.
 */
public final class BotnetView {

    private BotnetView() {}

    /** The window. */
    public static Region create(GameSession session) {
        VBox root = Views.panel("BOTNET");
        Label summary = new Label();
        summary.getStyleClass().add("es-sec-card-state");
        // ⚠ Wraps rather than ellipsising. This is the line that answers "what is my network costing
        // me", and a narrow window turning it into "3 running ..." would elide the compute figure,
        // which is the only number design/10 §3 asks the player to reason about.
        summary.setWrapText(true);

        Label result = new Label();
        result.setWrapText(true);

        VBox body = new VBox(10);
        Runnable[] repaint = new Runnable[1];

        Button collect = new Button(Views.t("ui.botnet.collect", "COLLECT"));
        Cursors.shared().clickable(collect);
        collect.setOnAction(e -> {
            GameSession.Outcome outcome = session.collectBots();
            result.setText(outcome.message());
            Views.styleByOutcome(result, outcome);
            repaint[0].run();
        });

        HBox head = new HBox(10, summary, spacer(), collect);
        head.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        repaint[0] = () -> {
            BotnetSnapshot net = session.botnet();
            summary.setText(summaryOf(net));
            collect.setDisable(net.bufferedWei().signum() <= 0);
            body.getChildren().setAll(sections(session, net, result, repaint[0]));
        };
        repaint[0].run();

        root.getChildren()
                .addAll(
                        Views.wrapped(Views.t(
                                "ui.botnet.intro",
                                "A frame is a chassis and does nothing on its own. What a bot does is "
                                        + "whatever is socketed into it. Every running bot holds cycles on "
                                        + "THIS rig for as long as it is out there, and every one of them "
                                        + "makes you louder.")),
                        head,
                        result,
                        new Separator(),
                        body);

        AutoCloseable handle = session.onChange(ignored -> repaint[0].run());
        Region scroller = Views.scrollable(root);
        Views.releaseOnDetach(scroller, handle);
        return scroller;
    }

    /**
     * The one line that says what the network costs.
     *
     * <p>⚠ It names the control-channel cycles <b>and</b> the offload separately, because they are
     * charged to different machines. The control channel is on this rig; the offload is on somebody
     * else's (Invariant I6), which is exactly why it is absent from the compute grid and has to
     * appear here or nowhere.
     */
    private static String summaryOf(BotnetSnapshot net) {
        StringBuilder text = new StringBuilder();
        text.append(net.liveCount()).append(" running · ").append(net.bots().size()).append(" built · ");
        text.append(net.controlChannelCycles()).append(" cycles held here");
        if (net.offloadCapacityCycles() > 0) {
            text.append(" · ")
                    .append(net.offloadInUseCycles())
                    .append('/')
                    .append(net.offloadCapacityCycles())
                    .append(" borrowed cycles in use");
        }
        if (net.bufferedWei().signum() > 0) {
            text.append(" · ").append(Ethecoin.format(net.bufferedWei())).append(" waiting");
        }
        return text.toString();
    }

    private static List<javafx.scene.Node> sections(
            GameSession session, BotnetSnapshot net, Label result, Runnable repaint) {
        List<javafx.scene.Node> out = new ArrayList<>();

        List<BotView> live = net.bots().stream().filter(BotView::live).toList();
        List<BotView> idle = net.bots().stream().filter(b -> !b.live()).toList();

        out.add(header(Views.t("ui.botnet.running", "RUNNING")));
        if (live.isEmpty()) {
            // ⚠ An empty section says so rather than vanishing. A window that opens blank is
            // indistinguishable from one that failed to build — the defect BreachView's idle panel
            // and the launch panel that shipped permanently inert both record.
            out.add(Views.secondary(Views.t("ui.botnet.none-running", "Nothing is out there.")));
        }
        for (BotView bot : live) {
            out.add(card(session, bot, result, repaint));
        }

        out.add(new Separator());
        out.add(header(Views.t("ui.botnet.workshop", "WORKSHOP")));
        if (idle.isEmpty()) {
            out.add(Views.secondary(Views.t("ui.botnet.none-idle", "No assembled frames waiting.")));
        }
        for (BotView bot : idle) {
            out.add(card(session, bot, result, repaint));
        }
        out.add(assembleRow(session, result, repaint));

        out.add(new Separator());
        out.add(header(Views.t("ui.botnet.reports", "WATCHER REPORTS")));
        if (net.reports().isEmpty()) {
            out.add(Views.secondary(Views.t(
                    "ui.botnet.no-reports", "Nothing reported. A watcher has to be running somewhere first.")));
        }
        for (BotnetSnapshot.Report report : net.reports()) {
            Label line = new Label(NodeReportView.age(report.at(), java.time.Instant.now()) + "  "
                    + report.hostAddress() + "  " + report.detail());
            line.getStyleClass().add("es-kv-value");
            line.setWrapText(true);
            out.add(line);
        }
        return out;
    }

    /** One bot, running or idle. */
    private static Region card(GameSession session, BotView bot, Label result, Runnable repaint) {
        VBox card = new VBox(6);
        card.getStyleClass().add("es-market-card");

        String where = bot.live()
                ? "on " + bot.hostAddress() + (bot.hostLabel().isBlank() ? "" : " (" + bot.hostLabel() + ")")
                : bot.damaged() ? "DAMAGED" : "idle";
        Label title = new Label(bot.frameName() + " — " + where);
        title.getStyleClass().add("es-bot-title");
        card.getChildren().add(title);

        // ⚠ The discovery warning is `-es-alarm`, and it is the ONLY alarm this panel spends.
        // design/design-language §2.1 rations alarm to loss and hostile state at twice a screen, and
        // a bot somebody has found is exactly that — everything else here is neutral so that this
        // line means something when it appears.
        if (bot.discovered()) {
            Label found = new Label("FOUND — somebody over there is trying to get rid of it.");
            found.getStyleClass().add("es-bot-found");
            found.setWrapText(true);
            card.getChildren().add(found);
        }
        if (bot.live() && !bot.processName().isBlank()) {
            card.getChildren().add(Views.secondary("Runs over there as \"" + bot.processName() + "\"."));
        }
        if (bot.damaged()) {
            card.getChildren()
                    .add(Views.secondary("Thrown off a machine. It holds nothing until it is repaired, "
                            + "and whatever was fitted is already gone."));
        }

        Label held = new Label(bot.live()
                ? bot.controlChannelCycles() + " cycles held here · " + Ethecoin.format(bot.bufferedWei()) + " buffered"
                : bot.functions().size() + "/" + bot.slots() + " sockets filled");
        held.getStyleClass().add("es-kv-key");
        card.getChildren().add(held);

        for (BotView.Slot slot : bot.functions()) {
            card.getChildren().add(slotRow(session, bot, slot, result, repaint));
        }
        if (bot.freeSlots() > 0) {
            card.getChildren()
                    .add(Views.secondary(bot.freeSlots() + " empty function socket(s) — a bot with nothing "
                            + "in it cannot be uploaded anywhere."));
        }
        for (BotView.Mod mod : bot.modifiers()) {
            card.getChildren().add(modRow(session, bot, mod, result, repaint));
        }
        if (bot.freeModifierSlots() > 0) {
            card.getChildren().add(Views.secondary(bot.freeModifierSlots() + " empty modifier socket(s)."));
        } else if (bot.modifierSlots() == 0) {
            // ⚠ Said out loud rather than left as an absence. A v1 has no modifier socket at all, and
            // a card that simply never mentions modifiers reads as a panel that forgot them.
            card.getChildren().add(Views.secondary("This chassis has no modifier socket. A v2 is the first "
                    + "that does."));
        }
        card.getChildren().add(actions(session, bot, result, repaint));
        return card;
    }

    /** One socketed module: what it is, what its level does, and the button that compiles it higher. */
    private static Region slotRow(
            GameSession session, BotView bot, BotView.Slot slot, Label result, Runnable repaint) {
        Label name = new Label(label(slot.function()) + "  L" + slot.level());
        name.getStyleClass().add("es-kv-value");
        name.setMinWidth(Region.USE_PREF_SIZE);

        Label effect = new Label(slot.effect());
        effect.getStyleClass().add("es-kv-key");
        effect.setWrapText(true);
        HBox.setHgrow(effect, Priority.ALWAYS);

        Button up = new Button("COMPILE +1");
        Cursors.shared().clickable(up);
        // ⚠ The tooltip names BOTH costs, and the material half is the one worth saying out loud: a
        // player told only about a price will go and mine, and mining does not produce material.
        up.setTooltip(new javafx.scene.control.Tooltip(
                "A level costs ethecoin AND schematic material. Material comes off defended machines."));
        up.setOnAction(e -> {
            GameSession.Outcome outcome = session.levelBotFunction(slot.function(), bot.botId());
            result.setText(outcome.message());
            Views.styleByOutcome(result, outcome);
            repaint.run();
        });

        HBox row = new HBox(10, name, effect, up);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return row;
    }

    /** One fitted modifier and the button that upgrades it. */
    private static Region modRow(
            GameSession session, BotView bot, BotView.Mod mod, Label result, Runnable repaint) {
        Label name = new Label("+ " + label(mod.modifier()) + (mod.level() > 1 ? "  L" + mod.level() : ""));
        name.getStyleClass().add("es-kv-value");
        name.setMinWidth(Region.USE_PREF_SIZE);

        Label effect = new Label(mod.effect());
        effect.getStyleClass().add("es-kv-key");
        effect.setWrapText(true);
        HBox.setHgrow(effect, Priority.ALWAYS);

        Button up = new Button("UPGRADE");
        Cursors.shared().clickable(up);
        // ⚠ Ethecoin only, and the tooltip says so — a modifier is horizontal, where a function's
        // ladder is a ceiling and needs schematic material. Two buttons that look the same and cost
        // different KINDS of thing is exactly where a player learns the rule wrong.
        up.setTooltip(new javafx.scene.control.Tooltip("Ethecoin only. Modifiers are horizontal; "
                + "function levels are the ones that need schematic material."));
        up.setDisable(mod.modifier() == BotModifier.EXE_NAME_SCRAMBLER);
        up.setOnAction(e -> {
            GameSession.Outcome outcome = session.levelBotModifier(mod.modifier(), bot.botId());
            result.setText(outcome.message());
            Views.styleByOutcome(result, outcome);
            repaint.run();
        });

        HBox row = new HBox(10, name, effect, up);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return row;
    }

    /** Upload / recall / fit, depending on where the bot is. */
    private static Region actions(GameSession session, BotView bot, Label result, Runnable repaint) {
        HBox row = new HBox(8);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        if (bot.live()) {
            Button recall = new Button(Views.t("ui.botnet.recall", "RECALL"));
            Cursors.shared().clickable(recall);
            recall.setOnAction(e -> {
                GameSession.Outcome outcome = session.recallBot(bot.botId());
                result.setText(outcome.message());
                Views.styleByOutcome(result, outcome);
                repaint.run();
            });
            row.getChildren().add(recall);
            return row;
        }

        // ── fit a module ────────────────────────────────────────────────────────────────────────
        MenuButton fit = new MenuButton(Views.t("ui.botnet.fit", "FIT MODULE"));
        Cursors.shared().clickable(fit);
        for (GameSession.InventoryItem item : allItems(session)) {
            if (!isModule(item.itemType())) {
                continue;
            }
            MenuItem entry = new MenuItem(item.displayName() + "  " + Views.shortId(item.itemId()));
            entry.setOnAction(e -> {
                GameSession.Outcome outcome = session.socketBot(bot.botId(), item.itemId());
                result.setText(outcome.message());
                Views.styleByOutcome(result, outcome);
                repaint.run();
            });
            fit.getItems().add(entry);
        }
        // ⚠ Disabled with a reason rather than absent. A control that is simply missing reads as a
        // feature that does not exist; one that is present and says why reads as a thing to go and
        // acquire — which is exactly what the player has to do.
        if (fit.getItems().isEmpty()) {
            fit.setDisable(true);
            fit.setText(Views.t("ui.botnet.no-modules", "NO MODULES OWNED"));
        } else if (bot.freeSlots() <= 0) {
            fit.setDisable(true);
            fit.setText(Views.t("ui.botnet.sockets-full", "SOCKETS FULL"));
        }
        row.getChildren().add(fit);

        // ── fit a modifier ──────────────────────────────────────────────────────────────────────
        MenuButton fitMod = new MenuButton(Views.t("ui.botnet.fit-mod", "FIT MODIFIER"));
        Cursors.shared().clickable(fitMod);
        for (GameSession.InventoryItem item : allItems(session)) {
            if (!isModifier(item.itemType())) {
                continue;
            }
            MenuItem entry = new MenuItem(item.displayName() + "  " + Views.shortId(item.itemId()));
            entry.setOnAction(e -> {
                GameSession.Outcome outcome = session.fitBotModifier(bot.botId(), item.itemId());
                result.setText(outcome.message());
                Views.styleByOutcome(result, outcome);
                repaint.run();
            });
            fitMod.getItems().add(entry);
        }
        if (bot.modifierSlots() == 0) {
            fitMod.setDisable(true);
            fitMod.setText(Views.t("ui.botnet.no-mod-socket", "NO MODIFIER SOCKET"));
        } else if (fitMod.getItems().isEmpty()) {
            fitMod.setDisable(true);
            fitMod.setText(Views.t("ui.botnet.no-mods", "NO MODIFIERS OWNED"));
        } else if (bot.freeModifierSlots() <= 0) {
            fitMod.setDisable(true);
        }
        row.getChildren().add(fitMod);

        // ── repair / recycle (§2.3) ─────────────────────────────────────────────────────────────
        if (bot.damaged()) {
            Button repair = new Button(Views.t("ui.botnet.repair", "REPAIR"));
            Cursors.shared().clickable(repair);
            repair.setOnAction(e -> {
                GameSession.Outcome outcome = session.repairBot(bot.botId());
                result.setText(outcome.message());
                Views.styleByOutcome(result, outcome);
                repaint.run();
            });
            row.getChildren().add(repair);
        }
        Button recycle = new Button(Views.t("ui.botnet.recycle", "RECYCLE"));
        Cursors.shared().clickable(recycle);
        // ⚠ Names the cost rather than confirming afterwards. Recycling scraps whatever is fitted,
        // which is not recoverable, and a player who learns that from the result line has already
        // lost the modules.
        recycle.setTooltip(new javafx.scene.control.Tooltip(
                "Breaks the chassis down for parts. Anything fitted is scrapped with it."));
        recycle.setOnAction(e -> {
            GameSession.Outcome outcome = session.recycleBot(bot.botId());
            result.setText(outcome.message());
            Views.styleByOutcome(result, outcome);
            repaint.run();
        });
        row.getChildren().add(recycle);

        // ── upload ──────────────────────────────────────────────────────────────────────────────
        MenuButton upload = new MenuButton(Views.t("ui.botnet.upload", "UPLOAD TO"));
        Cursors.shared().clickable(upload);
        // ⚠ Only machines the player HOLDS are offered. A menu of every discovered address would be
        // a list of refusals — leaving software running on a machine cannot be less gated than
        // breaching it — and the one that matters would be buried among them.
        for (Sighting sighting : session.net().sightings()) {
            if (!sighting.foothold()) {
                continue;
            }
            MenuItem entry = new MenuItem(sighting.address()
                    + (sighting.label().isBlank() ? "" : "  " + sighting.label()));
            entry.setOnAction(e -> {
                GameSession.Outcome outcome = session.uploadBot(bot.botId(), sighting.address());
                result.setText(outcome.message());
                Views.styleByOutcome(result, outcome);
                repaint.run();
            });
            upload.getItems().add(entry);
        }
        if (upload.getItems().isEmpty()) {
            upload.setDisable(true);
            upload.setText(Views.t("ui.botnet.no-footholds", "NO MACHINES HELD"));
        } else if (!bot.uploadable()) {
            upload.setDisable(true);
        }
        row.getChildren().add(upload);
        return row;
    }

    /** The row that turns an owned chassis into a bot. */
    private static Region assembleRow(GameSession session, Label result, Runnable repaint) {
        MenuButton assemble = new MenuButton(Views.t("ui.botnet.assemble", "ASSEMBLE A FRAME"));
        Cursors.shared().clickable(assemble);
        for (GameSession.InventoryItem item : allItems(session)) {
            if (!isFrame(item.itemType())) {
                continue;
            }
            MenuItem entry = new MenuItem(item.displayName() + "  " + Views.shortId(item.itemId()));
            entry.setOnAction(e -> {
                GameSession.Outcome outcome = session.buildBot(item.itemId());
                result.setText(outcome.message());
                Views.styleByOutcome(result, outcome);
                repaint.run();
            });
            assemble.getItems().add(entry);
        }
        if (assemble.getItems().isEmpty()) {
            assemble.setDisable(true);
            assemble.setText(Views.t("ui.botnet.no-frames", "NO FRAMES OWNED — BUY ONE IN THE MARKET"));
        }
        HBox row = new HBox(8, assemble);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return row;
    }

    // ⚠ Matched on the catalogue's ids through the engine's own mapping, never on a string prefix
    // typed here. Catalogue.botFunctionOf is the ONE place that knows which item is which module,
    // and a second answer in a view is how a module added later becomes unfittable with nothing on
    // screen saying why.
    private static boolean isModule(String itemType) {
        return io.github.stoicswe.eyeandsickle.engine.Catalogue.botFunctionOf(itemType)
                .isPresent();
    }

    private static boolean isModifier(String itemType) {
        return io.github.stoicswe.eyeandsickle.engine.Catalogue.botModifierOf(itemType)
                .isPresent();
    }

    private static boolean isFrame(String itemType) {
        return io.github.stoicswe.eyeandsickle.engine.Catalogue.botFrameTier(itemType) > 0;
    }

    private static String label(BotFunction function) {
        return io.github.stoicswe.eyeandsickle.engine.rules.Botnet.label(function).toUpperCase(java.util.Locale.ROOT);
    }

    private static String label(BotModifier modifier) {
        return io.github.stoicswe.eyeandsickle.engine.rules.Botnet.label(modifier).toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * Everything the player owns, across every tier.
     *
     * <p>⚠ Every tier, deliberately. A frame or a module sits wherever the player put it, and a
     * workshop that could only see the vault would silently refuse to assemble something the player
     * can see in their own storage.
     */
    private static List<GameSession.InventoryItem> allItems(GameSession session) {
        List<GameSession.InventoryItem> out = new ArrayList<>();
        for (io.github.stoicswe.eyeandsickle.protocol.game.StorageTier tier :
                io.github.stoicswe.eyeandsickle.protocol.game.StorageTier.values()) {
            out.addAll(session.items(tier));
        }
        return out;
    }

    private static Label header(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("es-botnet-head");
        return label;
    }

    private static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}
