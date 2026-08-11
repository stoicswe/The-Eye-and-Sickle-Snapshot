package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Shape;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;

/**
 * The Security Center's one big state mark: a shield, a warning triangle, or a quarantine trefoil.
 *
 * <h2>⚠ EVERY MARK IS DRAWN. None of them is a glyph.</h2>
 *
 * {@code GlyphCoverageTest} fails the build on any codepoint missing from the two bundled faces, and
 * it has already rejected {@code U+26A0} in this very panel. Shield and biohazard are certainly
 * absent too. So these are {@code Polygon}s, {@code Arc}s and {@code Circle}s — the same decision
 * {@code CLAUDE.md} records for the firmware flash's warning mark, for the carousel's dots and for
 * the credits' network marks. A drawn shape cannot be uncovered and cannot fall back to a host font
 * with different metrics.
 *
 * <h2>⚠ The mark is NEVER the only signal — §4.4</h2>
 *
 * The verdict beside it already says <em>Clear</em>, <em>Check</em> or <em>Quarantine</em> in words,
 * and every mark carries {@code accessibleText}. State that exists only as a picture does not survive
 * greyscale and does not reach a screen reader; this is decoration on top of a sentence, which is the
 * only thing a picture is allowed to be here.
 *
 * <h2>⚠ Motion is STEPPED and decorative</h2>
 *
 * §5 permits no easing anywhere and {@code UiContractTest} rations {@code AnimationTimer} to two
 * files by name, so the shield's sweep and the trefoil's turn move in whole steps on the shared
 * {@link Pulse}. Both are on {@code Pulse.animate}, i.e. <b>decoration</b>: under Reduce motion they
 * never fire and the mark holds one frame — which is WCAG 2.2.2's pause, and is safe here precisely
 * because the shape alone identifies the state. ⚠ That is the test for whether a mark's animation is
 * decoration: <b>if it stopped forever, would the player still know what it says?</b>
 */
public final class SecurityMark extends Pane {

    /** What the rig's security looks like right now. */
    public enum State {
        /** Audited recently, nothing found, something standing guard. */
        CLEAR,

        /**
         * Something to attend to, but nothing has been found.
         *
         * <p>Nothing armed, or the last audit is older than {@code ScanSchedule.STALE_AFTER}, or
         * there has never been one. ⚠ Deliberately not the same as a finding: "nobody has checked"
         * and "something is here" are different sentences and must not share a mark.
         */
        CHECK,

        /** An audit named something. */
        QUARANTINE
    }

    /** How many discrete positions the shield's sweep and the trefoil's turn have. */
    private static final int STEPS = 24;

    /** How many concentric copies make the glow. */
    private static final int GLOW_LAYERS = 3;

    /**
     * The pulse, frame by frame — a slow breath rather than a blink.
     *
     * <p>⚠ Twenty-four entries against {@code STEPS}, so one pass of the ticker is one breath.
     */
    private static final double[] GLOW = {
        0.35, 0.42, 0.52, 0.63, 0.75, 0.86, 0.94, 0.99, 1.00, 0.97, 0.90, 0.81,
        0.72, 0.63, 0.55, 0.48, 0.43, 0.39, 0.36, 0.34, 0.33, 0.33, 0.33, 0.34,
    };

    private final State state;
    private final Group art = new Group();
    private final java.util.List<Shape> glow = new java.util.ArrayList<>();
    private AutoCloseable ticker;
    private int step;

    /**
     * @param state what to draw
     */
    public SecurityMark(State state) {
        this.state = state;
        setMinSize(UiTokens.SECURITY_MARK, UiTokens.SECURITY_MARK);
        setPrefSize(UiTokens.SECURITY_MARK, UiTokens.SECURITY_MARK);
        setMaxSize(UiTokens.SECURITY_MARK, UiTokens.SECURITY_MARK);
        getStyleClass().add("es-secmark");
        getChildren().add(art);
        // ⚠ Mouse-transparent. It is a status picture, not a control, and a 120px target that
        // swallows clicks over the panel's empty half would be a dead zone nobody could explain.
        setMouseTransparent(true);

        switch (state) {
            case CLEAR -> buildShield();
            case CHECK -> buildWarning();
            case QUARANTINE -> buildTrefoil();
        }
        setAccessibleText(describe());

        // ⚠ Follows the SCENE, not construction. A Pulse subscription on a node nobody is looking at
        // is work with no observer, and Pulse needs a live toolkit — subscribing from the
        // constructor would make this widget untestable without starting one.
        sceneProperty().addListener((observable, was, now) -> {
            if (now == null) {
                dispose();
            } else if (ticker == null && moves()) {
                ticker = Pulse.shared().animate(UiTokens.SECURITY_MARK_STEP_MS, this::advance);
            }
        });
    }

    /** ⚠ CHECK does not move. A warning that pulsed would read as an alarm, which it is not. */
    private boolean moves() {
        return state != State.CHECK;
    }

    private void advance() {
        step = (step + 1) % STEPS;
        double fraction = step / (double) STEPS;
        if (state == State.CLEAR) {
            // The sweep travels down the shield and wraps.
            sweep.setTranslateY(-UiTokens.SECURITY_MARK / 2 + fraction * UiTokens.SECURITY_MARK);
        } else if (state == State.QUARANTINE) {
            // ⚠ It PULSES rather than turning, on explicit direction (2026-08-10). A radiation
            // trefoil is a symbol people know at a fixed orientation — spinning one reads as a
            // loading spinner, which is the one thing this mark must not say. The glow breathes
            // instead, in whole steps off a table, so §5 is untouched.
            pulse();
        }
    }

    /** What this mark is showing, so a caller can tell whether it needs replacing. */
    public State state() {
        return state;
    }

    /** Releases the ticker. Called on detach; safe to call twice. */
    public void dispose() {
        if (ticker != null) {
            try {
                ticker.close();
            } catch (Exception ignored) {
                // A subscription that will not close is not worth failing a repaint over.
            }
            ticker = null;
        }
    }

    private String describe() {
        return switch (state) {
            case CLEAR -> "Shield. The rig audited clean and something is standing guard.";
            case CHECK -> "Warning. Nothing has been found, but the rig needs attention.";
            case QUARANTINE -> "Quarantine. An audit named something on this rig.";
        };
    }

    // ── the three marks ───────────────────────────────────────────────────────────────────────

    private Rectangle sweep;

    /**
     * A shield, with a scan line travelling down it.
     *
     * <p>The outline is a polygon rather than a rounded path: §9's radius ban is about the
     * interface's own geometry and this is an emblem, but a straight-edged shield also reads better
     * against a character grid than a soft one would.
     */
    private void buildShield() {
        double s = UiTokens.SECURITY_MARK;
        double half = s / 2;
        Polygon shield = new Polygon(
                0, -half * 0.86,
                half * 0.72, -half * 0.52,
                half * 0.72, half * 0.12,
                0, half * 0.88,
                -half * 0.72, half * 0.12,
                -half * 0.72, -half * 0.52);
        shield.getStyleClass().add("es-secmark-shield");
        shield.setStrokeWidth(2.5);
        shield.setStrokeLineCap(StrokeLineCap.BUTT);

        // The sweep, clipped to the shield so it reads as travelling INSIDE it rather than across it.
        sweep = new Rectangle(s * 0.72 * 2, 3);
        sweep.setX(-half * 0.72);
        sweep.setY(-1.5);
        sweep.getStyleClass().add("es-secmark-sweep");

        Group inner = new Group(sweep);
        Polygon clip = new Polygon(shield.getPoints().stream().mapToDouble(Double::doubleValue).toArray());
        inner.setClip(clip);

        // A tick inside the shield, so a still frame is still obviously "good" rather than an
        // empty outline. ⚠ This is what makes the animation safe to suppress.
        Polygon tick = new Polygon(
                -half * 0.28, 0,
                -half * 0.10, half * 0.20,
                half * 0.32, -half * 0.26,
                half * 0.32, -half * 0.10,
                -half * 0.10, half * 0.38,
                -half * 0.28, half * 0.16);
        tick.getStyleClass().add("es-secmark-tick");

        art.getChildren().addAll(shield, inner, tick);
        art.setTranslateX(half);
        art.setTranslateY(half);
    }

    /**
     * A warning triangle.
     *
     * <p>⚠ A {@code Polygon} plus two {@code Region}-equivalent shapes, exactly as the firmware
     * flash's mark is built, and for the same reason: {@code U+26A0} is in neither bundled face.
     */
    private void buildWarning() {
        double s = UiTokens.SECURITY_MARK;
        double half = s / 2;
        Polygon triangle = new Polygon(0, -half * 0.84, half * 0.90, half * 0.66, -half * 0.90, half * 0.66);
        triangle.getStyleClass().add("es-secmark-warn");
        triangle.setStrokeWidth(2.5);

        Rectangle bar = new Rectangle(4, half * 0.62);
        bar.setX(-2);
        bar.setY(-half * 0.40);
        bar.getStyleClass().add("es-secmark-warn-fill");

        Rectangle dot = new Rectangle(4, 4);
        dot.setX(-2);
        dot.setY(half * 0.34);
        dot.getStyleClass().add("es-secmark-warn-fill");

        art.getChildren().addAll(triangle, bar, dot);
        art.setTranslateX(half);
        art.setTranslateY(half);
    }

    /**
     * The quarantine mark: a <b>radiation trefoil</b>.
     *
     * <h2>⚠ It was a biohazard, and before that it was a clover (2026-08-10)</h2>
     *
     * The first version was three 240° arcs, which at this size are very nearly circles — it rendered
     * as a flower. The second was a genuine biohazard, three annuli cut by a central circle. This one
     * is the radiation trefoil, on explicit direction, and it is the easiest of the three to read at
     * 44 pixels: three solid blades and a hub, with nothing thin in it.
     *
     * <h2>The construction is the ISO one, and the proportions are not free</h2>
     *
     * The standard mark is defined by a single radius {@code R}: a hub of {@code R}, and three blades
     * that are 60°-wide annular sectors running from {@code 1.5R} to {@code 5R}, separated by 60°
     * gaps. Those ratios are the whole recognisability — widen the blades and it becomes a fan, close
     * the inner gap and it becomes a wheel.
     *
     * <p>⚠ Blades at 30°, 150° and 270°, so one points straight <b>down</b> and the gap is straight
     * up. That is the orientation everybody has seen; rotated 60° it reads as an unfamiliar variant
     * of a familiar thing, which is worse than either.
     *
     * <p>⚠ An annular sector cannot be stroked into existence at this size, so the shape is built with
     * boolean subtraction and <b>filled</b> — a pie slice with the hub's circle taken out of it.
     */
    private void buildTrefoil() {
        double s = UiTokens.SECURITY_MARK;
        double half = s / 2;

        // The ISO figure, in terms of the one radius it is defined by.
        double r = half * 0.19;
        double innerRadius = r * 1.5;
        double outerRadius = r * 5;

        Shape mark = new Circle(0, 0, r);
        for (int i = 0; i < 3; i++) {
            double centre = 30 + i * 120;
            // ⚠ ArcType.ROUND is a PIE, which is what a blade is before its inner end is taken off.
            // OPEN would give an outline and CHORD would cut the wide end flat.
            Arc pie = new Arc(0, 0, outerRadius, outerRadius, centre - 30, 60);
            pie.setType(ArcType.ROUND);
            mark = Shape.union(mark, Shape.subtract(pie, new Circle(0, 0, innerRadius)));
        }

        // ⚠ THE GLOW IS CONCENTRIC COPIES, NEVER AN EFFECT. §9 makes blur and drop shadows
        // build-blocking and `UiContractTest` scans every stylesheet for `dropshadow(`, so a glow here
        // is built the way `GlowRing` builds the power-on ring's: the same silhouette drawn larger
        // behind itself, a few times, each fainter than the last. What pulses is their opacity.
        for (int layer = GLOW_LAYERS; layer >= 1; layer--) {
            Shape halo = copyOf(mark, r, innerRadius, outerRadius);
            // ⚠ Kept tight on purpose. The halo is drawn by a Group, which does not clip, so a wide
            // one overflows the widget's own box and paints over the verdict beside it — the mark
            // reserves a column in `SecurityCenterView` and the glow has to live inside it.
            double scale = 1 + layer * 0.06;
            halo.setScaleX(scale);
            halo.setScaleY(scale);
            halo.getStyleClass().add("es-secmark-rad-glow");
            glow.add(halo);
            art.getChildren().add(halo);
        }

        mark.getStyleClass().add("es-secmark-rad");
        art.getChildren().add(mark);
        art.setTranslateX(half);
        art.setTranslateY(half);
        pulse();
    }

    /**
     * Another instance of the same silhouette.
     *
     * <p>⚠ Rebuilt rather than cloned: a JavaFX {@code Shape} is a node and a node is in exactly one
     * place in the scene graph, so the glow layers cannot share one. It costs four boolean unions,
     * once, when the mark is constructed.
     */
    private static Shape copyOf(Shape unused, double r, double innerRadius, double outerRadius) {
        Shape mark = new Circle(0, 0, r);
        for (int i = 0; i < 3; i++) {
            double centre = 30 + i * 120;
            Arc pie = new Arc(0, 0, outerRadius, outerRadius, centre - 30, 60);
            pie.setType(ArcType.ROUND);
            mark = Shape.union(mark, Shape.subtract(pie, new Circle(0, 0, innerRadius)));
        }
        return mark;
    }

    /**
     * Sets the halo opacities for the current step.
     *
     * <h2>⚠ A table, and it never reaches zero</h2>
     *
     * {@code SyncSpin}'s rule — a formula for a pulse is an easing function in the source whatever it
     * is called. And the trough is not zero: a glow that went out entirely would read as the mark
     * blinking rather than breathing, and this is the one mark on the panel that must not look
     * intermittent.
     */
    private void pulse() {
        double level = GLOW[step % GLOW.length];
        for (int i = 0; i < glow.size(); i++) {
            // Outermost layer is faintest. `glow` is filled from the outside in.
            glow.get(i).setOpacity(level * (0.30 - i * 0.07));
        }
    }
}
