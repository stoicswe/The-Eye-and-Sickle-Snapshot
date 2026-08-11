package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javafx.scene.Group;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

/**
 * The eyes that surface through the deck while somebody is breaking into it.
 *
 * <h2>⚠ It is {@link EyeMark}'s SUBJECT, not a second symbol — and not its widget either</h2>
 *
 * {@code CLAUDE.md} keeps §9's icon-set ban honest with one rule: each drawn mark means one thing and
 * <b>none is reused for a second subject</b>. This is the same subject as the strip's eye — The Eye's
 * attention — arriving instead of being measured, which is why it is an eye at all.
 *
 * <p>It is a separate class rather than a mode on {@code EyeMark} because that widget is a
 * <em>readout</em>: it lives on the top strip for the whole session, rides one shared {@code Pulse}
 * subscription, and its motion is deliberately almost nothing. Teaching it to scribble and drip would
 * put three horror behaviours inside the thing whose job is to be quiet, and every one of them would
 * be reachable from the strip by a wrong argument.
 *
 * <h2>The three kinds, and why there are three</h2>
 *
 * A field of identical eyes reads as wallpaper — the mind stops seeing a repeated element within a
 * second or two. Three behaviours mean no two neighbours are doing the same thing, and each is a
 * different <em>kind</em> of wrong:
 *
 * <ul>
 *   <li>{@link Kind#DARTING} — the iris snaps around the socket, never settling. Something looking
 *       for you.
 *   <li>{@link Kind#SCRIBBLED} — the iris is a knot of lines that redraws itself. Something whose
 *       insides are not right.
 *   <li>{@link Kind#WEEPING} — it blinks, and the iris runs. Something that has been here a while.
 * </ul>
 *
 * <h2>⚠ It is driven, never self-driving</h2>
 *
 * No {@code Pulse}, no {@code Timeline}, no {@code AnimationTimer} — {@link #step(int)} is called by
 * whatever already has a clock, which is {@code Dread}'s stepper. A field of a dozen eyes each owning
 * a timer is a dozen timers to stop, and this project has shipped a leaked subscription more than
 * once. Nothing here can outlive the layer it is on.
 */
public final class DreadEye extends Group {

    /** What this eye does. */
    public enum Kind {
        /** The iris snaps around the socket. */
        DARTING,

        /** The iris is a scribble that redraws itself. */
        SCRIBBLED,

        /** It blinks, and the iris runs. */
        WEEPING
    }

    /** How wide an eye is relative to its height — the almond, not a circle. */
    private static final double ASPECT = 0.52d;

    /** How many strokes a scribbled iris carries. */
    private static final int SCRIBBLE_STROKES = 7;

    /**
     * The blink, as a table of openings. ⚠ A table, never a formula — {@code SyncSpin}'s rule: a
     * curve for "how open is an eye" is an easing function in the source whatever it is called.
     */
    private static final double[] BLINK = {1.0, 0.55, 0.12, 0.0, 0.18, 0.62, 1.0};

    /** Where the iris darts to, in fractions of the socket's half-width. A table for the same reason. */
    private static final double[][] DART = {
        {0.0, 0.0}, {0.55, -0.20}, {0.52, -0.18}, {-0.48, 0.22}, {-0.44, 0.18},
        {0.10, 0.40}, {0.62, 0.05}, {-0.20, -0.36}, {0.0, 0.0}, {-0.58, -0.10},
    };

    private final Kind kind;
    private final double size;
    private final Random random;

    private final Ellipse socket = new Ellipse();
    private final Circle iris = new Circle();
    private final Rectangle lid = new Rectangle();
    private final List<Line> scribble = new ArrayList<>();
    private final List<Rectangle> tears = new ArrayList<>();

    /**
     * @param size the eye's width in pixels
     * @param kind what it does
     * @param seed its own phase, so no two eyes in a field move together
     */
    public DreadEye(double size, Kind kind, long seed) {
        this.kind = kind;
        this.size = size;
        this.random = new Random(seed);

        socket.setRadiusX(size / 2);
        socket.setRadiusY(size / 2 * ASPECT);
        socket.getStyleClass().add("es-dread-eye");

        iris.setRadius(size * 0.17d);
        iris.getStyleClass().add("es-dread-iris");

        getChildren().addAll(socket, iris);

        if (kind == Kind.SCRIBBLED) {
            // ⚠ The strokes are CLIPPED to the iris, so a scribble cannot crawl out over the socket
            // and turn the eye into a smudge. Rebuilt in place each time rather than recreated, so a
            // field of these allocates nothing per frame.
            Circle clip = new Circle(size * 0.17d);
            Group knot = new Group();
            for (int i = 0; i < SCRIBBLE_STROKES; i++) {
                Line line = new Line();
                line.getStyleClass().add("es-dread-scribble");
                scribble.add(line);
                knot.getChildren().add(line);
            }
            knot.setClip(clip);
            getChildren().add(knot);
        }

        if (kind == Kind.WEEPING) {
            for (int i = 0; i < 2; i++) {
                Rectangle tear = new Rectangle(Math.max(1, size * 0.035d), 0);
                tear.getStyleClass().add("es-dread-tear");
                tears.add(tear);
            }
            getChildren().addAll(tears);
        }

        // The lid comes down over everything, so a blink covers the iris and its tears.
        lid.setWidth(size);
        lid.setHeight(size * ASPECT);
        lid.setX(-size / 2);
        lid.setY(-size / 2 * ASPECT);
        lid.getStyleClass().add("es-dread-lid");
        lid.setHeight(0);
        getChildren().add(lid);
    }

    /**
     * Advances this eye to {@code tick}.
     *
     * <p>⚠ Every kind is a pure function of the tick and the seed — nothing accumulates. So an eye
     * rebuilt when the deck is re-captured picks up exactly where it was rather than restarting, which
     * is what stops the field flickering nine times a second.
     */
    public void step(int tick) {
        switch (kind) {
            case DARTING -> {
                // ⚠ Held for a few ticks per entry, or it is a blur rather than a series of glances.
                double[] to = DART[(tick / 4) % DART.length];
                iris.setCenterX(to[0] * size / 2 * 0.55d);
                iris.setCenterY(to[1] * size / 2 * ASPECT);
            }
            case SCRIBBLED -> {
                // Redrawn every few ticks; the seed is the tick, so the knot is different each time
                // and identical if the same tick comes round again.
                if (tick % 3 == 0) {
                    Random pen = new Random(tick / 3L * 31L + random.nextLong(1));
                    double r = size * 0.17d;
                    for (Line line : scribble) {
                        line.setStartX(iris.getCenterX() + (pen.nextDouble() * 2 - 1) * r);
                        line.setStartY(iris.getCenterY() + (pen.nextDouble() * 2 - 1) * r);
                        line.setEndX(iris.getCenterX() + (pen.nextDouble() * 2 - 1) * r);
                        line.setEndY(iris.getCenterY() + (pen.nextDouble() * 2 - 1) * r);
                    }
                }
            }
            case WEEPING -> {
                int phase = tick % 64;
                double open = phase < BLINK.length ? BLINK[phase] : 1.0d;
                lid.setHeight(size * ASPECT * (1 - open));
                lid.setY(-size / 2 * ASPECT);
                // ⚠ The run RESETS on the blink rather than fading — a tear that faded out would read
                // as the drip retracting back into the eye.
                double run = phase < BLINK.length ? 0 : (phase - BLINK.length) / 56.0d;
                for (int i = 0; i < tears.size(); i++) {
                    Rectangle tear = tears.get(i);
                    tear.setX(iris.getCenterX() + (i == 0 ? -size * 0.06d : size * 0.05d));
                    tear.setY(iris.getCenterY());
                    tear.setHeight(run * size * (i == 0 ? 0.62d : 0.44d));
                }
            }
        }
    }

    /** The kinds, in the order a field should hand them out — one of each before any repeats. */
    public static Kind kindFor(int index) {
        return Kind.values()[Math.floorMod(index, Kind.values().length)];
    }
}
