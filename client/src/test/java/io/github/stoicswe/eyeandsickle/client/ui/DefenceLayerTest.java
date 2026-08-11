package io.github.stoicswe.eyeandsickle.client.ui;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ⚠ THE DEFENCE LAYER MUST NOT EXIST WHEN THERE IS NO DEFENCE — the whole client depends on it.
 *
 * <h2>The bug this is the regression test for</h2>
 *
 * The layer that hosts a defence round is the topmost child of the deck's root, above the CRT overlay
 * — whose own comment records that it is mouse-transparent "or it would eat every click". It was
 * created <b>visible, empty, with no background</b>, on the assumption that a {@code Region} painting
 * nothing is not picked.
 *
 * <p>⚠ <b>{@code pickOnBounds} defaults to TRUE on a {@code Parent}.</b> So the empty layer filled the
 * root and intercepted every click in the client. Nothing was drawn, nothing threw, no test failed,
 * and no render could show it — a screenshot of a deck nobody can click looks exactly like a
 * screenshot of a deck. It was found by a person trying to play the game.
 *
 * <h2>⚠ Why this test is worth having rather than the fix alone</h2>
 *
 * The fix is a <b>binding</b>: visible if and only if a round is present. That is deliberately not two
 * {@code setVisible} calls at the open and close sites, because the failure mode is somebody adding a
 * third path later. This asserts the property the binding exists to guarantee, so the day the binding
 * is replaced by hand-managed visibility the build says so.
 *
 * <p>It needs no toolkit: nodes construct and bind without a Scene, which is what lets a defect that
 * broke the entire client be covered by a test that runs in the fast loop.
 */
class DefenceLayerTest {

    /** Builds the layer exactly as {@code DeckShell} does. */
    private static StackPane layer() {
        return DeckShell.newDefenceLayer();
    }

    @Test
    @DisplayName("⚠ empty means invisible, so it cannot intercept a click")
    void emptyIsInvisible() {
        StackPane layer = layer();

        assertThat(layer.getChildren()).isEmpty();
        assertThat(layer.isVisible())
                .as("an empty layer on top of the deck must not be visible — an invisible node is "
                        + "never picked, and this one covers the whole client")
                .isFalse();
    }

    /**
     * ⚠ The half that documents WHY invisibility is the mechanism: this layer really is picked on its
     * bounds, so nothing about it being empty or unpainted would have saved it.
     */
    @Test
    @DisplayName("⚠ and it really is picked on bounds — being empty would not have been enough")
    void pickOnBoundsIsTheTrap() {
        assertThat(layer().isPickOnBounds())
                .as("Parent.pickOnBounds defaults to true; this is the property that broke the client")
                .isTrue();
    }

    @Test
    @DisplayName("a round makes it visible again")
    void aRoundMakesItVisible() {
        StackPane layer = layer();
        layer.getChildren().setAll(new Region());

        assertThat(layer.isVisible()).isTrue();
    }

    /**
     * ⚠ And closing puts it back. The close handle clears the children and nothing else — if that ever
     * stops implying invisibility, the client is uninteractable again from the first defence round
     * onward, which is a worse failure than the round not opening at all.
     */
    @Test
    @DisplayName("and closing it hands the deck back")
    void closingHandsTheDeckBack() {
        StackPane layer = layer();
        layer.getChildren().setAll(new Region());
        layer.getChildren().clear();

        assertThat(layer.isVisible()).isFalse();
    }
}
