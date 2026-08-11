package io.github.stoicswe.eyeandsickle.client.ui;

import io.github.stoicswe.eyeandsickle.client.ui.widgets.DreadEye;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Rectangle2D;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

/**
 * What the deck looks like while somebody is breaking into it.
 *
 * <p>The interface melts: it shears sideways in bands, tears vertically, splits into colour fringes,
 * and eyes surface and sink through it. It runs for as long as a defence round is open, and the
 * round itself is drawn <em>above</em> this and is untouched by it.
 *
 * <h2>⚠ IT DISTORTS A PICTURE OF THE DECK, NEVER THE DECK — and that is the whole design</h2>
 *
 * Nothing here reaches into a window, a label or a meter. It takes a {@link javafx.scene.Node#snapshot}
 * of the deck — {@code Frost}'s mechanism, for {@code Frost}'s reasons — and shears <b>the image</b>.
 * Three things fall out of that, and each one is a defect avoided:
 *
 * <ul>
 *   <li>⚠ <b>The minigame cannot be affected</b>, structurally rather than by care. It is a sibling
 *       added above this layer, so it is not in the captured picture and no offset can reach it. The
 *       alternative — jogging real nodes with {@code translateX}, which {@code CrtOverlay}'s glitch
 *       does — would have to know which nodes to leave alone, and would get it wrong the day
 *       somebody added a window.
 *   <li>⚠ <b>Nothing can be left behind.</b> A displaced real node has to be put back, and this
 *       project has shipped that bug twice ({@code HoverGlitch}'s table "must end at zero";
 *       {@code EyeMark}'s reduced-motion reset). An image is thrown away.
 *   <li>⚠ <b>It cannot eat a click.</b> The layer is mouse-transparent and the real deck is still
 *       underneath, live, at its true coordinates.
 * </ul>
 *
 * <h2>⚠ No blur, no drop shadow, no gradient — §9 is not amended for this</h2>
 *
 * The horror is <b>geometry</b>: bands of the captured image displaced sideways by a hand-authored
 * table, stretched vertically to drip, and drawn three times in three tints to split the colour. That
 * is the same decision {@code RingField}'s datamosh records, at deck scale. {@code UiContractTest}'s
 * scan for {@code dropshadow(} is untouched, and nothing here is eased.
 *
 * <h2>⚠ The eyes are the one borrowed symbol, and it is the SAME subject</h2>
 *
 * {@code CLAUDE.md} records the rule that keeps §9's icon-set ban honest: the client's five drawn
 * marks each mean one thing, and <b>none is reused for a second subject</b>. {@link EyeMark} means
 * "how much attention The Eye is paying to you" on the top strip. The eyes that surface here are that
 * attention <em>arriving</em> — the same subject, at the moment it stops being a number. A second,
 * differently-drawn eye would be the worse outcome: two marks for one thing.
 */
public final class Dread {

    /** How often the picture is re-captured while the effect runs. */
    private static final int REFRESH_MS = 110;

    /** How often the shear steps. Faster than the capture: the tearing is what carries the motion. */
    private static final int STEP_MS = 55;

    /** Captured at a fraction of full size — a sheared, tinted image does not need the pixels. */
    private static final double SCALE = 0.5d;

    /** How many horizontal bands the picture is torn into. */
    private static final int BANDS = 18;

    /**
     * The shear table: how far each band slides, as a fraction of the deck's width.
     *
     * <p>⚠ A TABLE, never a formula — {@code SyncSpin}'s rule, and the reason is the same: a curve
     * spelled as arithmetic is an easing function sitting in the source for somebody to import, at
     * which point §5 has been abandoned rather than worked within. Walked one entry per step, and it
     * <b>returns to zero</b>, so the picture periodically snaps back into register and the next tear
     * reads as a fresh fault rather than as constant noise.
     */
    private static final double[] SHEAR = {
        0.000, 0.004, -0.011, 0.002, 0.026, -0.006, 0.001, -0.038, 0.005, 0.000,
        0.014, -0.002, 0.049, -0.008, 0.003, 0.021, -0.001, 0.007, -0.017, 0.000,
    };

    /**
     * The heartbeat the deck pulses to after a failed defence — {@code lub-dub … rest}.
     *
     * <p>⚠ Also a table, and it is a <b>real</b> heartbeat's shape rather than a sine: two beats
     * close together, the second weaker, then a long rest. A single even pulse reads as a warning
     * lamp; this reads as a pulse, which is the point.
     */
    private static final double[] HEARTBEAT = {
        0.00, 0.55, 0.30, 0.10, 0.34, 0.16, 0.05, 0.02, 0.00, 0.00,
        0.00, 0.00, 0.00, 0.00, 0.00, 0.00,
    };

    /**
     * How long the horror takes to reach full strength, in shear steps.
     *
     * <h2>⚠ It ARRIVES rather than appearing, and that is the difference between dread and a fault</h2>
     *
     * Snapping straight to full intensity reads as the client breaking — one frame the deck is fine,
     * the next it is in pieces, which is what a crash looks like. Coming on over a couple of seconds
     * reads as something getting in, and it gives the player the moment they need to understand that
     * the window which just appeared is a game they are supposed to play.
     */
    private static final int RAMP_STEPS = 74;

    /**
     * How many steps the horror takes to drain away once a round has resolved.
     *
     * <h2>⚠ It LEAVES rather than vanishing, for the mirror of the reason it arrives slowly</h2>
     *
     * Cutting it dead the instant the round ends snaps the deck back in one frame, which reads as the
     * effect having been switched off rather than as the attack being over — and it lands on top of a
     * verdict the player is still reading. Draining takes about as long as a breath.
     */
    private static final int SETTLE_STEPS = 34;

    /** How far the deck is dimmed under the distortion, at full strength. */
    private static final double DIM = 0.42d;

    /** How many drips run down from the top of the deck. */
    private static final int DRIPS = 14;

    private final Pane layer = new Pane();
    private final List<ImageView> bands = new ArrayList<>();
    private final List<ImageView> fringes = new ArrayList<>();
    private final List<DreadEye> eyes = new ArrayList<>();
    private final List<Rectangle> drips = new ArrayList<>();
    private final List<double[]> dripPlan = new ArrayList<>();
    private final Rectangle dim = new Rectangle();
    private final Pane bloomEdges = new Pane();
    private final Random random = new Random(0x5EEDL);

    private Region deck;
    private java.util.function.Supplier<List<javafx.geometry.Bounds>> edges = List::of;
    private Timeline capture;
    private Timeline stepper;
    private Timeline pulse;
    private int step;
    private double intensity;
    private int levels = 256;
    private int settling = -1;
    private Runnable onSettled;

    public Dread() {
        layer.setMouseTransparent(true);
        layer.setVisible(false);
        layer.getStyleClass().add("es-dread");
        // ⚠ The dim is UNDER the distortion and over the deck, so what is torn is also what is
        // darkened. Laid over the bands instead, it would flatten the colour split it is meant to
        // sit behind.
        dim.getStyleClass().add("es-dread-dim");
        dim.setMouseTransparent(true);
        dim.setOpacity(0);
        dim.widthProperty().bind(layer.widthProperty());
        dim.heightProperty().bind(layer.heightProperty());
        bloomEdges.setMouseTransparent(true);
        bloomEdges.setOpacity(0);
        layer.getChildren().addAll(dim, bloomEdges);
    }

    /**
     * Where the aftermath pulse draws its outlines.
     *
     * <p>⚠ Handed in rather than reached for: this class knows about a deck and an image of it, and
     * has no business knowing what a window is. {@code CrtOverlay.setEdgeSource} is the same seam for
     * the same reason.
     */
    public void setEdgeSource(java.util.function.Supplier<List<javafx.geometry.Bounds>> source) {
        this.edges = source == null ? List::of : source;
    }

    /** The node to add to the deck, above everything the effect is meant to reach. */
    public Pane node() {
        return layer;
    }

    /** Points the effect at the deck it distorts. */
    public void attach(Region deck) {
        this.deck = deck;
    }

    /**
     * How many colour levels per channel the captured deck keeps — {@code 256} for untouched.
     *
     * <p>⚠ Applied to the CAPTURE, which is the one place it is free: the picture is produced anyway,
     * and quantising it is a single bulk pass measured at under a millisecond. Nothing about the real
     * deck changes, so there is nothing to put back when the round ends.
     */
    public void setPosterize(int levels) {
        this.levels = levels;
    }

    /** Whether the horror is running. */
    public boolean running() {
        return capture != null || settling >= 0;
    }

    /**
     * Starts it.
     *
     * <p>⚠ Under Reduce motion the layer stays <b>off entirely</b> rather than freezing on one
     * distorted frame. A still, sheared, colour-split picture of the deck is not a calmer version of
     * this effect — it is an interface that looks broken, permanently, with no motion to explain it.
     * The defence round is what tells the player they are being attacked; the horror is decoration on
     * top and is safe to lose completely, which is the test {@code CLAUDE.md} sets for any flourish.
     */
    public void start() {
        if (deck == null || running() || Pulse.shared().reducedMotion()) {
            return;
        }
        step = 0;
        intensity = 0;
        levels = 256;
        settling = -1;
        onSettled = null;
        layer.setVisible(true);
        rebuild();

        capture = new Timeline(new KeyFrame(Duration.millis(REFRESH_MS), e -> rebuild()));
        capture.setCycleCount(Timeline.INDEFINITE);
        capture.play();

        stepper = new Timeline(new KeyFrame(Duration.millis(STEP_MS), e -> advance()));
        stepper.setCycleCount(Timeline.INDEFINITE);
        stepper.play();
    }

    /**
     * Drains the horror away and then tears it down, calling {@code done} when the deck is its own
     * again.
     *
     * <p>⚠ The caller waits for this before doing anything else that draws on the deck — the
     * aftermath pulse in particular. Two effects fading through each other on one layer reads as a
     * rendering fault rather than as one thing ending and another beginning.
     */
    public void settle(Runnable done) {
        if (!running()) {
            if (done != null) {
                done.run();
            }
            return;
        }
        settling = SETTLE_STEPS;
        onSettled = done;
        // ⚠ The capture stops here and the shear does not. Re-snapshotting a deck that is on its way
        // back would keep paying for a picture nobody can see, and the drain has to keep stepping or
        // there is nothing to drain it.
        if (capture != null) {
            capture.stop();
            capture = null;
        }
    }

    /**
     * Stops it and puts everything back, immediately.
     *
     * <p>⚠ Every image is dropped. A held capture is a full-deck bitmap kept alive for a round that
     * ended, and the layer is created once and lives as long as the deck.
     */
    public void stop() {
        if (capture != null) {
            capture.stop();
            capture = null;
        }
        if (stepper != null) {
            stepper.stop();
            stepper = null;
        }
        settling = -1;
        onSettled = null;
        bands.clear();
        fringes.clear();
        eyes.clear();
        drips.clear();
        dripPlan.clear();
        dim.setOpacity(0);
        layer.getChildren().retainAll(dim, bloomEdges);
        layer.setVisible(pulse != null);
    }

    /**
     * The aftermath: a slow heartbeat bloom over the deck for {@code seconds}.
     *
     * <p>⚠ It runs <b>after</b> the horror has stopped and is a different thing — not the attack, the
     * fact that it landed. Nothing is distorted; the deck is back and simply will not settle.
     */
    public void bloom(int seconds) {
        if (Pulse.shared().reducedMotion()) {
            return;
        }
        if (pulse != null) {
            pulse.stop();
        }
        // ⚠ EDGES, NEVER THE WHOLE SCREEN. A full-surface flash reads as a camera shutter and, at
        // fifteen seconds, as a fault — and it washes out every readout on the deck at the moment the
        // player is trying to work out what it just cost them. An outline round the deck and round
        // each open window says the same thing about the same subject and leaves the contents legible.
        //
        // ⚠ Rebuilt from the CURRENT geometry each time it is started rather than tracked live. The
        // pulse lasts fifteen seconds and a window moved during it will have a stale outline — which
        // is the cheaper wrong than a per-frame walk of every frame on the desk for a decoration.
        bloomEdges.getChildren().clear();
        bloomEdges.getChildren().add(outline(0, 0, layer.getWidth(), layer.getHeight()));
        for (javafx.geometry.Bounds bounds : edges.get()) {
            bloomEdges
                    .getChildren()
                    .add(outline(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight()));
        }

        layer.setVisible(true);
        int[] beat = {0};
        int frames = Math.max(1, seconds * 1000 / 90);
        pulse = new Timeline(new KeyFrame(Duration.millis(90), e -> {
            bloomEdges.setOpacity(HEARTBEAT[beat[0] % HEARTBEAT.length]);
            beat[0]++;
        }));
        pulse.setCycleCount(frames);
        // ⚠ The last frame must clear the opacity itself. A Timeline that simply stops leaves the
        // node wherever the final tick put it, and a deck left permanently outlined is the "table
        // must end at zero" defect one layer up.
        pulse.setOnFinished(e -> {
            bloomEdges.setOpacity(0);
            bloomEdges.getChildren().clear();
            pulse = null;
            layer.setVisible(running());
        });
        pulse.play();
    }

    /**
     * Poses the aftermath pulse at one beat of the table — for the render harness.
     *
     * <p>⚠ Reads the real table rather than setting an opacity of its own, so a picture taken through
     * it is evidence about the pulse and not about the harness.
     */
    public void windBloom(int beat) {
        bloomEdges.setOpacity(HEARTBEAT[Math.floorMod(beat, HEARTBEAT.length)]);
    }

    /** One outline, stroked and unfilled — a border round something, never a wash over it. */
    private static Rectangle outline(double x, double y, double w, double h) {
        Rectangle edge = new Rectangle(Math.max(0, w - 2), Math.max(0, h - 2));
        edge.setX(x + 1);
        edge.setY(y + 1);
        edge.getStyleClass().add("es-dread-bloom");
        return edge;
    }

    /**
     * Runs the effect {@code frames} steps forward without a clock — for the render harness.
     *
     * <p>⚠ It captures first if nothing has been captured, so it works on a deck that has never had
     * a Timeline tick. And it bypasses the Reduce-motion gate deliberately: the harness forces that
     * setting on, and a flag whose whole purpose is to photograph the effect must not be defeated by
     * the one condition under which the effect never runs.
     */
    public void wind(int frames) {
        if (deck == null) {
            return;
        }
        layer.setVisible(true);
        if (bands.isEmpty()) {
            rebuild();
        }
        for (int i = 0; i < frames; i++) {
            advance();
        }
    }

    /** Re-captures the deck and rebuilds the torn bands from it. */
    private void rebuild() {
        double w = deck.getWidth();
        double h = deck.getHeight();
        if (w < 1 || h < 1) {
            return;
        }
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setTransform(new Scale(SCALE, SCALE));
        // ⚠ The viewport is in the TRANSFORMED space — Frost records the same trap, which produces a
        // picture of the wrong rectangle plausibly enough to survive review.
        parameters.setViewport(new Rectangle2D(0, 0, w * SCALE, h * SCALE));
        parameters.setFill(Color.TRANSPARENT);
        // ⚠ The layer is hidden for the capture, or it photographs itself and the tearing compounds
        // into mush within a second. The same "hide, capture, reveal" order Frost uses.
        boolean was = layer.isVisible();
        layer.setVisible(false);
        WritableImage shot = deck.snapshot(parameters, null);
        layer.setVisible(was);
        Image picture = Posterize.image(shot, levels);

        bands.clear();
        fringes.clear();
        eyes.clear();
        layer.getChildren().retainAll(dim, bloomEdges);

        double bandHeight = h / BANDS;
        for (int i = 0; i < BANDS; i++) {
            ImageView band = new ImageView(picture);
            band.setViewport(new Rectangle2D(0, i * bandHeight * SCALE, w * SCALE, bandHeight * SCALE));
            band.setFitWidth(w);
            band.setFitHeight(bandHeight);
            band.setY(i * bandHeight);
            bands.add(band);

            // ⚠ Two tinted copies per band, offset the other way — this is the colour split, and it
            // is done with translucent copies rather than a filter because §9 permits neither blur
            // nor a channel effect, and CrtOverlay's own aberration is the same trick.
            for (int c = 0; c < 2; c++) {
                ImageView fringe = new ImageView(picture);
                fringe.setViewport(band.getViewport());
                fringe.setFitWidth(w);
                fringe.setFitHeight(bandHeight);
                fringe.setY(i * bandHeight);
                fringe.setOpacity(0.22d);
                fringe.getStyleClass().add(c == 0 ? "es-dread-fringe-warm" : "es-dread-fringe-cool");
                fringes.add(fringe);
            }
        }
        layer.getChildren().addAll(fringes);
        layer.getChildren().addAll(bands);

        // Eyes, surfacing at rest positions that do not move once placed — a wandering eye reads as
        // a bug rather than as being watched.
        for (int i = 0; i < 6; i++) {
            // ⚠ One of each kind before any repeats — a field of identical eyes reads as wallpaper,
            // and the mind stops seeing a repeated element within a second or two.
            DreadEye eye = new DreadEye(52 + random.nextInt(34), DreadEye.kindFor(i), 0x9E37L + i * 977L);
            eye.setMouseTransparent(true);
            // ⚠ AROUND THE PERIMETER, not across the deck. The round covers most of the screen, so
            // eyes placed anywhere would land behind it — measured by rendering: five of six were
            // invisible. The margin is the only deck a player can actually see during a round, and it
            // is also the better place for them: something at the edge of vision is worse than
            // something in the middle of it.
            double band = Math.min(w, h) * 0.13d;
            boolean vertical = i % 2 == 0;
            if (vertical) {
                eye.setLayoutX(random.nextBoolean() ? random.nextDouble() * band : w - band + random.nextDouble() * band);
                eye.setLayoutY(40 + random.nextDouble() * Math.max(1, h - 120));
            } else {
                eye.setLayoutX(40 + random.nextDouble() * Math.max(1, w - 120));
                eye.setLayoutY(random.nextBoolean() ? random.nextDouble() * band : h - band + random.nextDouble() * band);
            }
            eye.setOpacity(0);
            eyes.add(eye);
        }
        layer.getChildren().addAll(eyes);

        // ⚠ THE DRIPS RUN FROM THE TOP EDGE and are the one part of this that is not a picture of the
        // deck. They are drawn rectangles with a rounded head, planned once per capture so a drip
        // does not restart from the top every time the image is retaken — which would read as
        // flickering rather than as running.
        drips.clear();
        if (dripPlan.isEmpty()) {
            for (int i = 0; i < DRIPS; i++) {
                // {x fraction, width, speed, head start}
                dripPlan.add(new double[] {
                    random.nextDouble(), 2 + random.nextInt(3), 0.6d + random.nextDouble() * 1.9d,
                    random.nextDouble() * 90
                });
            }
        }
        for (double[] plan : dripPlan) {
            Rectangle drip = new Rectangle(plan[1], 0);
            drip.setX(plan[0] * Math.max(1, w));
            drip.setY(0);
            drip.getStyleClass().add("es-dread-drip");
            drips.add(drip);
        }
        layer.getChildren().addAll(drips);

        // ⚠ The dim goes ABOVE the torn bands — it darkens the distorted picture rather than being
        // darkened by it — and the drips and the eyes go above the dim, or the two things that are
        // meant to be the most visible parts of this are the two it dims most.
        dim.toBack();
        for (ImageView fringe : fringes) {
            fringe.toBack();
        }
        for (ImageView band : bands) {
            band.toBack();
        }
        bloomEdges.toFront();
        advance();
    }

    /** One frame of tearing. */
    private void advance() {
        double w = layer.getWidth();
        double h = layer.getHeight();
        // ⚠ THE HORROR ARRIVES RATHER THAN APPEARING — see RAMP_STEPS. Everything below is scaled by
        // this, so at step 0 the deck is untouched and the effect grows into itself.
        if (settling >= 0) {
            // Draining. The same intensity everything else is scaled by, walked back down.
            intensity = Math.max(0, settling / (double) SETTLE_STEPS);
            settling--;
            if (settling < 0) {
                Runnable done = onSettled;
                onSettled = null;
                stop();
                if (done != null) {
                    done.run();
                }
                return;
            }
        } else {
            intensity = Math.min(1.0d, step / (double) RAMP_STEPS);
        }
        dim.setOpacity(DIM * intensity);
        for (int i = 0; i < bands.size(); i++) {
            // Each band reads the table at its own offset, so the picture shears rather than sliding.
            double shear = SHEAR[(step + i * 3) % SHEAR.length] * w * intensity;
            bands.get(i).setTranslateX(shear);
            // ⚠ THE MELT: a band whose neighbour has moved is also stretched downward, so the tear
            // drips instead of merely sliding. Scale rather than height, because an ImageView's
            // height is its layout and changing it per frame would re-lay-out the whole layer.
            bands.get(i).setScaleY(1.0d + Math.abs(shear) / Math.max(1, w) * 6.0d);
            if (i * 2 + 1 < fringes.size()) {
                fringes.get(i * 2).setTranslateX(shear + 3 * intensity);
                fringes.get(i * 2 + 1).setTranslateX(shear - 3 * intensity);
                fringes.get(i * 2).setOpacity(0.22d * intensity);
                fringes.get(i * 2 + 1).setOpacity(0.22d * intensity);
            }
        }
        for (int i = 0; i < eyes.size(); i++) {
            // Each eye surfaces on its own period, so they never fade in unison.
            double phase = (step + i * 11) % 46 / 46.0d;
            eyes.get(i).setOpacity(Math.max(0, Math.sin(phase * Math.PI)) * 0.55d * intensity);
            // ⚠ Driven from HERE rather than each eye owning a clock. A dozen eyes with a dozen
            // timers is a dozen things to stop, and this project has leaked a subscription more than
            // once; nothing on this layer can outlive the layer.
            eyes.get(i).step(step + i * 7);
        }
        for (int i = 0; i < drips.size(); i++) {
            double[] plan = dripPlan.get(i);
            // A drip grows downward, holds, then is cut back to nothing and starts again — the cycle
            // is per-drip so they never run in step.
            double cycle = (step * plan[2] + plan[3]) % 140;
            double length = cycle < 100 ? cycle / 100.0d * h * 0.55d : 0;
            drips.get(i).setHeight(Math.max(0, length * intensity));
        }
        step++;
    }
}
