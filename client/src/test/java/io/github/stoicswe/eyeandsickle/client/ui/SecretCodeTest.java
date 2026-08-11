package io.github.stoicswe.eyeandsickle.client.ui;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the key sequence that reveals the developer page.
 *
 * <p>⚠ No toolkit. {@code SecretCode.advance} is pure precisely so these can run in CI — this repo
 * has exactly one test that starts JavaFX and it aborts when there is no display, so a rule that
 * lived inside the event handler would be guarded by nothing on the machine that matters.
 */
class SecretCodeTest {

    private static boolean feed(SecretCode code, Object... keys) {
        boolean armed = false;
        for (Object key : keys) {
            armed = key instanceof Shift shift ? code.advance(shift.code(), true) : code.advance((KeyCode) key, false);
        }
        return armed;
    }

    private record Shift(KeyCode code) {}

    private static final Object[] CORRECT = {
        KeyCode.UP, KeyCode.DOWN, KeyCode.LEFT, KeyCode.RIGHT, new Shift(KeyCode.A), new Shift(KeyCode.B), KeyCode.ENTER
    };

    @Test
    @DisplayName("the sequence arms on its last key and not before")
    void arms() {
        SecretCode code = new SecretCode();

        for (int i = 0; i < CORRECT.length - 1; i++) {
            assertThat(feed(code, CORRECT[i])).as("armed early at step " + i).isFalse();
        }
        assertThat(feed(code, CORRECT[CORRECT.length - 1])).isTrue();
    }

    /**
     * ⚠ The detail that makes or breaks the whole sequence. Holding Shift to type {@code Shift+A}
     * fires a KEY_PRESSED for {@link KeyCode#SHIFT} <em>first</em>, so a matcher that reset on
     * anything unexpected would reset on the modifier of the very step it was waiting for — and the
     * code would be impossible to enter, at step five, every time. Verified against a matcher without
     * the {@code isModifierKey} branch, which fails here.
     */
    @Test
    @DisplayName("a modifier press is ignored, not counted as a wrong key")
    void modifiersAreIgnored() {
        SecretCode code = new SecretCode();
        feed(code, KeyCode.UP, KeyCode.DOWN, KeyCode.LEFT, KeyCode.RIGHT);

        // What the OS actually delivers: SHIFT down, then A with shift held.
        assertThat(code.advance(KeyCode.SHIFT, false)).isFalse();
        assertThat(code.progress()).isEqualTo(4);
        assertThat(code.advance(KeyCode.A, true)).isFalse();
        assertThat(code.progress()).isEqualTo(5);

        assertThat(code.advance(KeyCode.SHIFT, true)).isFalse();
        assertThat(code.advance(KeyCode.B, true)).isFalse();
        assertThat(code.advance(KeyCode.ENTER, false)).isTrue();
    }

    @Test
    @DisplayName("a plain letter is not the shifted step")
    void shiftIsPartOfTheStep() {
        SecretCode code = new SecretCode();
        feed(code, KeyCode.UP, KeyCode.DOWN, KeyCode.LEFT, KeyCode.RIGHT);

        // Without this the code is four arrows and two letters, which somebody types into the search
        // box by accident.
        assertThat(code.advance(KeyCode.A, false)).isFalse();
        assertThat(code.progress()).isZero();
    }

    @Test
    @DisplayName("a wrong key is retried against the start rather than thrown away")
    void restarts() {
        SecretCode code = new SecretCode();

        // ↑ ↑ — the second is wrong for step two AND a valid opening. Resetting to zero would make
        // the player release and start over for a reason nothing on screen explains.
        assertThat(code.advance(KeyCode.UP, false)).isFalse();
        assertThat(code.advance(KeyCode.UP, false)).isFalse();
        assertThat(code.progress()).isEqualTo(1);

        assertThat(feed(code, KeyCode.DOWN, KeyCode.LEFT, KeyCode.RIGHT, new Shift(KeyCode.A), new Shift(KeyCode.B)))
                .isFalse();
        assertThat(code.advance(KeyCode.ENTER, false)).isTrue();
    }

    @Test
    @DisplayName("a key that is not an opening resets outright")
    void resets() {
        SecretCode code = new SecretCode();
        feed(code, KeyCode.UP, KeyCode.DOWN);

        assertThat(code.advance(KeyCode.Z, false)).isFalse();
        assertThat(code.progress()).isZero();
    }

    /**
     * ⚠ A matcher parked on the last step would fire again on the next Enter the player pressed
     * anywhere in Settings — and Settings is full of buttons.
     */
    @Test
    @DisplayName("completing resets, so a later Enter does not arm it again")
    void resetsOnCompletion() {
        SecretCode code = new SecretCode();
        assertThat(feed(code, CORRECT)).isTrue();
        assertThat(code.progress()).isZero();

        assertThat(code.advance(KeyCode.ENTER, false)).isFalse();
    }

    @Test
    @DisplayName("a null key is survivable")
    void nullKey() {
        assertThat(new SecretCode().advance(null, false)).isFalse();
    }
}
