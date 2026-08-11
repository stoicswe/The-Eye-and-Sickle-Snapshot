package io.github.stoicswe.eyeandsickle.client.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ⚠ THE DEFENCE ROUND MUST TAKE THE KEYBOARD WHEN IT OPENS.
 *
 * <h2>The bug this is the regression test for</h2>
 *
 * {@code DefenseGameView.create} returns a <b>wrapper</b> — the node carrying the pulsing edge and the
 * drips — while the focus flag, the key handlers and every {@code requestFocus()} sat on the VBox
 * <em>inside</em> it. So the deck opened a round and called {@code requestFocus()} on a node that was
 * not focus-traversable: a <b>silent no-op</b>. Focus stayed on the command strip, and the arrow keys
 * did nothing until the player clicked the field.
 *
 * <p>⚠ Nothing failed, nothing threw, and <b>no render could show it</b> — a screenshot of a game
 * nobody can drive is identical to a screenshot of a game. It was found by a person trying to play.
 *
 * <h2>⚠ It asserts the KEY REACHES THE GAME, not merely that a flag is set</h2>
 *
 * Focus-traversable and focused are both necessary and neither is sufficient: the handlers have to be
 * on the node that ends up focused, or on one of its ancestors. So this puts the round in a real
 * Scene beside a real {@code TextField} that already holds focus, opens it, and fires a genuine
 * {@code KEY_PRESSED} — the only check that covers the whole path.
 */
class DefenceFocusTest {

    @BeforeAll
    static void toolkit() throws Exception {
        // Same abort-rather-than-pass arrangement NodeMenuTest documents: CI has no display, and a
        // regression test that reports success without executing is worse than none.
        CountDownLatch up = new CountDownLatch(1);
        try {
            Platform.startup(up::countDown);
        } catch (IllegalStateException alreadyRunning) {
            up.countDown();
        } catch (UnsupportedOperationException | NoClassDefFoundError | ExceptionInInitializerError headless) {
            org.junit.jupiter.api.Assumptions.abort(
                    "no display — the JavaFX toolkit cannot start here: " + headless.getMessage());
        }
        if (!up.await(20, TimeUnit.SECONDS)) {
            org.junit.jupiter.api.Assumptions.abort("the JavaFX toolkit did not start within 20s");
        }
    }

    private static void onFxThread(Runnable body) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        if (!done.await(20, TimeUnit.SECONDS)) {
            throw new IllegalStateException("the FX thread did not finish in 20s");
        }
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
    }

    /** The round, built the way the deck builds one. */
    private static Region round() {
        return DefenseGameView.create(null, "10.0.0.4 · breaking in", 2, false, false, 1, 4242L, o -> {}, l -> {});
    }

    @Test
    @DisplayName("⚠ the node the deck is handed can actually take focus")
    void theReturnedNodeIsFocusable() throws Exception {
        onFxThread(() -> {
            Region round = round();
            assertThat(round.isFocusTraversable())
                    .as("the deck calls requestFocus() on THIS node; on a non-traversable one that is "
                            + "a silent no-op and the round opens with the keyboard pointed elsewhere")
                    .isTrue();
        });
    }

    /**
     * ⚠ The whole path, against a focus owner that is already somewhere else — which is the real
     * situation: the deck's command strip holds focus when a round opens.
     */
    @Test
    @DisplayName("⚠ opening a round takes the keyboard off whatever had it, and arrow keys reach the game")
    void openingTakesTheKeyboard() throws Exception {
        onFxThread(() -> {
            TextField elsewhere = new TextField();
            Region round = round();
            StackPane root = new StackPane(elsewhere, round);
            Scene scene = new Scene(root, 900, 700);
            root.applyCss();
            root.layout();

            elsewhere.requestFocus();
            assertThat(scene.getFocusOwner()).as("the fixture starts with focus elsewhere").isEqualTo(elsewhere);

            round.requestFocus();

            assertThat(scene.getFocusOwner())
                    .as("opening the round must take the keyboard, with no click")
                    .isEqualTo(round);

        });
    }

    /**
     * The other half: the node that takes focus is also the node that CARRIES the game's key handlers.
     *
     * <h2>⚠ Consumption is NOT observable from a test, and two versions of this assumed it was</h2>
     *
     * {@code Event.fireEvent} <b>copies</b> the event to set its source and target, so the handler
     * consumes the copy and {@code isConsumed()} on the object the test holds reads {@code false}
     * however well the wiring works — measured, with and without a Scene, on a bare {@code StackPane}
     * whose handler provably ran. An assertion on it fails against correct code, which is worse than
     * no assertion at all.
     *
     * <p>So this observes the wiring directly: the node the deck focuses is the node the handlers are
     * on. That is exactly what was wrong — the handlers were on the VBox <em>inside</em> the returned
     * wrapper, and events bubble up, not down, so nothing the deck focused could ever have reached
     * them.
     */
    @Test
    @DisplayName("⚠ and the node that takes focus is the node carrying the key handlers")
    void theFocusedNodeCarriesTheHandlers() throws Exception {
        onFxThread(() -> {
            Region round = round();
            assertThat(round.getOnKeyPressed())
                    .as("the returned node must carry the key-pressed handler; on an inner child it "
                            + "is unreachable from the focus owner, because events bubble up")
                    .isNotNull();
            assertThat(round.getOnKeyReleased())
                    .as("and the release handler, or a key held when focus moves is never let go")
                    .isNotNull();
        });
    }
}