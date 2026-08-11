package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.session.LocalGameSession;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import io.github.stoicswe.eyeandsickle.engine.Catalogue;
import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import io.github.stoicswe.eyeandsickle.engine.state.ItemState;
import io.github.stoicswe.eyeandsickle.protocol.game.BotFunction;
import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javax.imageio.ImageIO;

/**
 * Renders the BOTNET window — empty, loaded and running, and damaged.
 *
 * <h2>Why this needs a picture</h2>
 *
 * The panel is a column of cards whose contents are decided by a chain of conditionals — live vs
 * idle, damaged, discovered, free sockets, no modifier socket at all on a {@code v1} — and this
 * client has shipped that construction wrong more than once. A card that renders blank is
 * indistinguishable from a window that failed to build, and no assertion in this project can see the
 * difference.
 *
 * <pre>{@code
 * mvn install -DskipTests
 * mvn -pl client test-compile
 * mvn -pl client exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=io.github.stoicswe.eyeandsickle.client.view.BotnetSnapshot \
 *     -Dexec.args="/tmp/botnet"
 * }</pre>
 *
 * <p>⚠ <b>A fresh Scene per state</b> — {@code BreachSnapshot} and {@code AuditSnapshot} both record
 * the trap: {@code Scene.snapshot} renders what the scene last laid out, so two synchronous shots of
 * one scene come back identical and "verify" a state never drawn.
 *
 * <p>⚠ <b>THREE independent reasons a naive fixture photographs an empty panel three times</b>, and
 * each of them looks exactly like the feature being absent:
 *
 * <ol>
 *   <li><b>A starting rig is 24 cycles.</b> A bot holds a {@code BOT_FRAME} control channel, and an
 *       upload against a stock rig competing with the tutorial parasite is a refusal. The ladder is
 *       granted — the same trap {@code DeckSnapshot} and {@code BreachSnapshot} were both caught by.
 *   <li><b>A bot needs a machine the player HOLDS.</b> Nothing is breached on a fresh character, so
 *       the upload menu is empty and every bot renders idle. A foothold is forced.
 *   <li><b>Nothing owns a frame.</b> The chassis and the modules are ordinary purchases; without
 *       granting them the workshop is one disabled button.
 * </ol>
 *
 * <p>Each is guarded with a printed warning rather than left to fail silently, which is
 * {@code BreachSnapshot}'s arrangement and for its reason.
 */
public final class BotnetSnapshot {

    private BotnetSnapshot() {}

    private static final Instant T0 = Instant.parse("2026-08-11T09:00:00Z");

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "target/snapshots");
        out.toFile().mkdirs();
        CountDownLatch done = new CountDownLatch(1);
        Platform.startup(() -> {
            try {
                render(out);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                done.countDown();
            }
        });
        done.await();
        Platform.exit();
    }

    private static void render(Path out) throws Exception {
        Path profileDir = out.resolve("profile");
        profileDir.toFile().mkdirs();
        ClientProfile profile = new ClientProfile(profileDir);
        profile.settings().reducedMotionOverride = Boolean.TRUE;
        ThemeManager themes = new ThemeManager(profile);

        Winding clock = new Winding(T0);
        GameEngine game = GameEngine.open(
                io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(profileDir.resolve("save.json")),
                "kyyrell",
                clock);
        for (var rung : io.github.stoicswe.eyeandsickle.engine.rules.ComputeLadder.rungs()) {
            grant(game, rung.itemType());
        }
        io.github.stoicswe.eyeandsickle.engine.rules.ComputeLadder.reconcile(game.state());
        LocalGameSession session = new LocalGameSession(game);

        // ── empty: nothing owned, nothing built. The state a new character opens on.
        shoot(themes, BotnetView.create(session), out.resolve("botnet-empty.png"));

        // ── loaded and running.
        var host = game.state().topology.hosts.stream()
                .filter(h -> !h.address.equals(game.state().topology.playerAddress))
                .filter(h -> !"BRIDGE".equals(h.kind))
                .findFirst()
                .orElseThrow();
        host.discovered = true;
        host.identified = true;
        host.foothold = true;

        grant(game, Catalogue.BOT_FRAME_V1);
        game.buildBot(lastItem(game));
        String v1 = game.state().bots.getFirst().botId;
        grant(game, Catalogue.botFunctionOfferingId(BotFunction.MINER).orElseThrow());
        game.socketBot(v1, lastItem(game));
        if (!game.uploadBot(v1, host.address).ok()) {
            System.out.println("⚠ the v1 did not upload — the running shot will render a workshop");
        }

        // A v3 with two modifiers, so the modifier rows and the level readouts are on screen. The
        // tier is forced rather than bought because v2+ are schematic-gated and the compiler that
        // would grant one is not built (docs/design/10 §6 BN-3) — this is the render photographing a
        // reachable-in-principle state, and it says so rather than pretending the shop sold it.
        grant(game, Catalogue.BOT_FRAME_V1);
        game.buildBot(lastItem(game));
        var deep = game.state().bots.getLast();
        deep.frameTier = 3;
        deep.frameType = Catalogue.botFrameId(3);
        grant(game, Catalogue.botFunctionOfferingId(BotFunction.SIPPER).orElseThrow());
        game.socketBot(deep.botId, lastItem(game));
        grant(game, Catalogue.BOT_MOD_PROTECTOR);
        game.fitBotModifier(deep.botId, lastItem(game));
        grant(game, Catalogue.BOT_MOD_BEDAZZLE);
        game.fitBotModifier(deep.botId, lastItem(game));

        // Something for the workshop's menus to offer, so the disabled captions are not what is drawn.
        grant(game, Catalogue.BOT_FRAME_V1);
        grant(game, Catalogue.BOT_MOD_SLEEPY);

        clock.advance(Duration.ofHours(3));
        game.tick();
        if (game.botnet().reports().isEmpty()) {
            System.out.println("⚠ no watcher reports — that section will render its empty state");
        }
        shoot(themes, BotnetView.create(session), out.resolve("botnet-running.png"));

        // ── damaged: what a player sees after somebody threw their bot off a machine.
        game.recallBot(v1);
        game.state().bots.getFirst().damaged = true;
        game.state().bots.getFirst().functions.clear();
        shoot(themes, BotnetView.create(session), out.resolve("botnet-damaged.png"));
    }

    private static void grant(GameEngine game, String itemType) {
        ItemState item = new ItemState();
        item.itemType = itemType;
        item.displayName = Catalogue.byId(itemType).map(Catalogue.Offering::name).orElse(itemType);
        item.tier = StorageTier.VAULT.name();
        game.state().items.add(item);
    }

    private static String lastItem(GameEngine game) {
        return game.state().items.getLast().itemId;
    }

    private static void shoot(ThemeManager themes, Region panel, Path to) throws Exception {
        int w = 980;
        int h = 900;
        StackPane host = new StackPane(panel);
        host.getStyleClass().add("es-scene-ground");
        Scene scene = new Scene(host, w, h);
        themes.adopt(scene);
        scene.getRoot().applyCss();
        host.layout();
        scene.getRoot().applyCss();
        host.layout();
        WritableImage image = scene.snapshot(new WritableImage(w, h));
        BufferedImage png = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        PixelReader pixels = image.getPixelReader();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                png.setRGB(x, y, pixels.getArgb(x, y));
            }
        }
        ImageIO.write(png, "png", new File(to.toString()));
        System.out.println("wrote " + to);
    }

    private static final class Winding extends Clock {
        private Instant instant;

        Winding(Instant start) {
            this.instant = start;
        }

        void advance(Duration by) {
            instant = instant.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
