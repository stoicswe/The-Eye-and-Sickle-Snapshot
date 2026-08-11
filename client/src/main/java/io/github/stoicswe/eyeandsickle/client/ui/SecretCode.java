package io.github.stoicswe.eyeandsickle.client.ui;

import java.util.List;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;

/**
 * The key sequence that reveals the developer/cheat page in Settings.
 *
 * <p>{@code ↑ ↓ ← → Shift+A Shift+B Enter}, in order, while the Settings window has focus.
 *
 * <h2>⚠ The matcher is pure and the install is four lines, deliberately</h2>
 *
 * The same seam {@code SecurityCenterView.latestOf} and {@code DirectView.state} exist for, and for
 * the same reason both of those were extracted: a rule that lives inside a JavaFX event handler can
 * only be checked by starting the toolkit and pressing keys, and this repo runs exactly one test
 * that starts a toolkit — which skips on CI. {@link #advance} needs no scene, no window and no
 * pointer, so every branch below is checkable in a plain unit test.
 *
 * <h2>⚠ A modifier press is NOT a wrong key</h2>
 *
 * This is the detail that makes or breaks the whole sequence. Holding Shift to type {@code Shift+A}
 * fires a {@code KEY_PRESSED} for {@link KeyCode#SHIFT} <em>first</em>, so a matcher that reset on
 * anything unexpected would reset on the modifier of the very step it was waiting for — and the
 * sequence would be impossible to enter, at step five, every time. Modifier presses are ignored
 * rather than counted.
 *
 * <h2>⚠ A mismatch retries the key against the start</h2>
 *
 * Pressing {@code ↑ ↑ ↓ …} has to work. Resetting to zero on the second {@code ↑} would throw away a
 * key that was itself a valid opening, so the player would have to release and start over for a
 * reason nothing on screen explains. On a mismatch the offending key is tried once against step one.
 */
public final class SecretCode {

    /**
     * One step: a key, and whether Shift must be held for it.
     *
     * <p>Shift is part of the step rather than checked separately because {@code A} and
     * {@code Shift+A} are different steps here — a plain {@code a} must not advance the sequence, or
     * the code is four arrows and two letters, which somebody types into the search box by accident.
     */
    private record Step(KeyCode code, boolean shift) {}

    private static final List<Step> SEQUENCE = List.of(
            new Step(KeyCode.UP, false),
            new Step(KeyCode.DOWN, false),
            new Step(KeyCode.LEFT, false),
            new Step(KeyCode.RIGHT, false),
            new Step(KeyCode.A, true),
            new Step(KeyCode.B, true),
            new Step(KeyCode.ENTER, false));

    /** How far into the sequence the player is. Never escapes; the class is one-per-panel. */
    private int at = 0;

    /** The sequence, spelled for a player who has been told there is one. */
    public static String spelled() {
        return "↑ ↓ ← → Shift+A Shift+B Enter";
    }

    /** How many keys are in the sequence. */
    public static int length() {
        return SEQUENCE.size();
    }

    /** How far in the player currently is, {@code 0}–{@link #length()}{@code  - 1}. For tests. */
    public int progress() {
        return at;
    }

    /**
     * Feeds one key press to the matcher.
     *
     * @param code the key pressed
     * @param shift whether Shift was down
     * @return true exactly on the press that completes the sequence
     */
    public boolean advance(KeyCode code, boolean shift) {
        if (code == null || code.isModifierKey()) {
            return false;
        }
        if (matches(SEQUENCE.get(at), code, shift)) {
            at++;
            if (at == SEQUENCE.size()) {
                // ⚠ Reset on completion, not left at the end. The caller may reveal a page that is
                // already revealed, and a matcher parked on the last step would fire again on the
                // next Enter the player pressed anywhere in Settings.
                at = 0;
                return true;
            }
            return false;
        }
        at = matches(SEQUENCE.getFirst(), code, shift) ? 1 : 0;
        return false;
    }

    private static boolean matches(Step step, KeyCode code, boolean shift) {
        return step.code() == code && step.shift() == shift;
    }

    /**
     * Listens for the sequence while {@code root} holds the keyboard focus.
     *
     * <h2>⚠ The filter goes on the panel's own root, and that IS the "while it has focus" gate</h2>
     *
     * A {@code KeyEvent} travels from the Scene root down to the focus owner, so this filter runs if
     * and only if the focused node is inside {@code root} — which is what "the Settings window is in
     * focus" means. The alternative, a Scene-wide filter plus a check of which desk window the deck
     * thinks is focused, would need the deck handed to a view that has never known what a deck is,
     * and it would answer differently from JavaFX the moment the two disagreed.
     *
     * <h2>⚠ Nothing is consumed</h2>
     *
     * The arrows still scroll the page and the letters still reach the search box while the sequence
     * is being entered. Swallowing them would make the panel behave oddly for every player who
     * happened to press an arrow key, in order to keep a secret from the ones who do not know it.
     *
     * @param root the Settings panel's root
     * @param onArmed run on the completing key press, on the FX thread
     */
    public static void install(Region root, Runnable onArmed) {
        SecretCode code = new SecretCode();
        // Without this the root can never hold focus, so on a freshly opened Settings window — where
        // nothing inside has been clicked yet — key events would go to whatever the deck focused
        // last and the filter below would never run.
        root.setFocusTraversable(true);
        root.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            Scene scene = root.getScene();
            // ⚠ Only when focus is not ALREADY inside. Taking it unconditionally on every press
            // would fight the search field: the player clicks it, the root grabs focus, and the
            // caret they were aiming at never arrives.
            if (scene != null && !inside(scene.getFocusOwner(), root)) {
                root.requestFocus();
            }
        });
        root.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (code.advance(event.getCode(), event.isShiftDown())) {
                onArmed.run();
            }
        });
    }

    private static boolean inside(Node node, Node ancestor) {
        for (Node walk = node; walk != null; walk = walk.getParent()) {
            if (walk == ancestor) {
                return true;
            }
        }
        return false;
    }

}
