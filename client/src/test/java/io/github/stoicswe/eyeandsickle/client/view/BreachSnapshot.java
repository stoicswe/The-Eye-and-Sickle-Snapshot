package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.profile.ClientProfile;
import io.github.stoicswe.eyeandsickle.client.session.LocalGameSession;
import io.github.stoicswe.eyeandsickle.client.theme.ThemeManager;
import io.github.stoicswe.eyeandsickle.engine.GameEngine;
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
 * Renders the BREACH window's three states — idle, armed, and a live attempt.
 *
 * <h2>Why this needs a picture, and why it did not exist before</h2>
 *
 * The window used to end in a target list, so <em>something</em> was always on screen and "did the
 * panel build" was answered by opening it once. Since the list came out (2026-08-10) the window is
 * the armed target and nothing else, and its three states are mutually exclusive by a chain of
 * {@code visible()} calls — the exact construction this client has shipped wrong more than once, most
 * recently a launch panel that was permanently inert because a freshly-built {@code VBox} reads as
 * already visible. A state that renders blank is indistinguishable from a window that failed to
 * build, and no assertion in this project can see the difference.
 *
 * <pre>{@code
 * mvn install -DskipTests
 * mvn -pl client test-compile
 * mvn -pl client exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=io.github.stoicswe.eyeandsickle.client.view.BreachSnapshot \
 *     -Dexec.args="/tmp/breach"
 * }</pre>
 *
 * <p>⚠ <b>A fresh Scene per state.</b> {@code Scene.snapshot} renders what the scene last laid out,
 * so driving {@code setVisible} between two synchronous snapshots of one scene yields two identical
 * images and "verifies" a state it never drew — {@code AuditSnapshot} records the same trap.
 *
 * <p>⚠ <b>The armed state has to be reached through {@code BreachArming}</b>, which is where the
 * network map puts a target and the only route into this window that exists. Poking the launch panel
 * visible by hand would photograph a state no player can reach.
 */
public final class BreachSnapshot {

    private BreachSnapshot() {}

    private static final Instant T0 = Instant.parse("2026-08-10T09:00:00Z");

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
        // ⚠ NOT `TestSaves.bare` — that fixture removes the tutorial parasite, and the parasite is
        // the one target a fresh character has. With it gone and no sweep run, nothing is
        // attemptable and all three shots render IDLE, which is the state this render exists to tell
        // apart from the other two.
        GameEngine game = GameEngine.open(
                io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(profileDir.resolve("save.json")),
                "kyyrell",
                clock);
        // ⚠ But the rig DOES need the ladder. A starting rig is 24 cycles, a breach reserves its
        // target's compute for the whole attempt, and `BreachTarget.available()` is false when the
        // rig cannot afford it — so on a stock rig the armed and live shots photograph an idle
        // window and report the panel as broken. The same trap `DeckSnapshot` was caught by.
        for (var rung : io.github.stoicswe.eyeandsickle.engine.rules.ComputeLadder.rungs()) {
            var item = new io.github.stoicswe.eyeandsickle.engine.state.ItemState();
            item.itemType = rung.itemType();
            item.tier = io.github.stoicswe.eyeandsickle.protocol.game.StorageTier.VAULT.name();
            game.state().items.add(item);
        }
        io.github.stoicswe.eyeandsickle.engine.rules.ComputeLadder.reconcile(game.state());
        LocalGameSession session = new LocalGameSession(game);

        // ⚠ AND THE PARASITE HAS TO BE FOUND BEFORE IT IS A TARGET. `Targets.available` skips a miner
        // that is not `discovered`, deliberately — listing one the moment it was planted would hand a
        // new character the tutorial crack before they had run a single scan, and would have the
        // breach window and the rig monitor disagreeing about what the player knows. So the render
        // walks the real pipeline: audit, then crack. Without this the fixture is silently
        // target-less and every shot renders IDLE.
        session.scan("full");
        clock.advance(Duration.ofMinutes(5));
        game.tick();

        // ── idle: nothing armed. The state that used to be the target list.
        shoot(themes, BreachView.create(session, null, null, new BreachArming()), out.resolve("breach-idle.png"));

        // ── armed: the launch panel, reached the way the map reaches it.
        //
        // ⚠ The tutorial parasite is the target that is always there on a fresh character, so this
        // needs no sweep and no breach — and it is a CRACK, which is the branch that also renders the
        // "your own rig, no heat" note.
        BreachArming armed = new BreachArming();
        session.breachTargets().stream()
                .filter(t -> t.available())
                .findFirst()
                .ifPresentOrElse(
                        t -> armed.arm(t.targetId()),
                        () -> System.out.println("⚠ no attemptable target — the armed shot will render IDLE"));
        shoot(themes, BreachView.create(session, null, null, armed), out.resolve("breach-armed.png"));

        // ── live: an attempt actually open, so the board, the meter and the cost strip are on screen.
        BreachArming live = new BreachArming();
        session.breachTargets().stream().filter(t -> t.available()).findFirst().ifPresent(t -> {
            live.arm(t.targetId());
            session.beginBreach(t.targetId());
        });
        clock.advance(Duration.ofSeconds(1));
        game.tick();
        if (session.breach().isEmpty()) {
            System.out.println("⚠ no breach opened — the live shot will render ARMED or IDLE");
        }
        shoot(themes, BreachView.create(session, null, null, live), out.resolve("breach-live.png"));
    }

    private static void shoot(ThemeManager themes, Region panel, Path to) throws Exception {
        int w = 1100;
        int h = 820;
        StackPane host = new StackPane(panel);
        host.getStyleClass().add("es-scene-ground");
        Scene scene = new Scene(host, w, h);
        themes.adopt(scene);
        scene.getRoot().applyCss();
        host.layout();
        // Twice: the first pass is what fills the panel from the session, and several of the
        // visibility decisions are taken during it.
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
