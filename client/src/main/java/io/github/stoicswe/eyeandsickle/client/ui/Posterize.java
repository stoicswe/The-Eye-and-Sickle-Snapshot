package io.github.stoicswe.eyeandsickle.client.ui;

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/**
 * Colour-level quantisation — the deck losing its colour depth as a defence round runs out.
 *
 * <h2>⚠ JavaFX HAS NO POSTERIZE EFFECT, and none of the built-ins approximates one</h2>
 *
 * The effect list is {@code Blend}, {@code Bloom}, {@code BoxBlur}, {@code ColorAdjust},
 * {@code DisplacementMap}, {@code Glow}, {@code Lighting}, {@code SepiaTone} and the shadows. Not one
 * of them reduces the number of levels per channel, and there is no public shader API to write one
 * with. So it is done arithmetically, in the two places where it is affordable.
 *
 * <h2>⚠ TWO PATHS, because the deck and the round have opposite constraints</h2>
 *
 * <ul>
 *   <li><b>The deck</b> is already captured as an image by {@link Dread}, at half scale, roughly nine
 *       times a second. Quantising that image costs one pass over pixels that were going to be
 *       produced anyway, and the deck is not interactive during a round — so an image is the whole
 *       truth about it.
 *   <li><b>The round</b> must stay live: 60 fps and taking key events. It cannot be replaced by a
 *       picture. It is a few dozen shapes drawn from a handful of palette tokens, so it is quantised
 *       at the <b>colour</b> rather than at the pixel — the same arithmetic applied to the fills.
 * </ul>
 *
 * <h2>⚠ The image pass is BULK, never per-pixel calls</h2>
 *
 * {@code PixelReader.getArgb} / {@code PixelWriter.setArgb} per pixel is a method call each way, and
 * at half scale on a large deck that is 400,000 of each, nine times a second, on the FX thread. This
 * reads the whole frame into one {@code int[]}, walks it with a 256-entry lookup table, and writes it
 * back in one call. The inner loop is three array indexes and two shifts.
 */
public final class Posterize {

    private Posterize() {}

    /** The most levels per channel this ever uses — i.e. barely touched. */
    public static final int MAX_LEVELS = 12;

    /** The fewest. Two levels per channel is eight colours, which is as far as this goes. */
    public static final int MIN_LEVELS = 2;

    /**
     * How many levels per channel remain, given how much of the round is left.
     *
     * <h2>⚠ It steps, and the steps are the point</h2>
     *
     * Quantisation is a whole number of levels, so this is a staircase whatever it is written as. Made
     * explicit rather than rounded off a continuous curve: a level lasts several seconds, the change
     * lands as a visible jolt, and the player can feel the clock without reading it. It is also what
     * makes the work bounded — the round re-tints itself about ten times in thirty seconds rather than
     * sixty times a second.
     *
     * @param remaining fraction of the round left, {@code 1} at the start and {@code 0} at the end
     */
    public static int levelsFor(double remaining) {
        double clamped = Math.max(0, Math.min(1, remaining));
        for (int i = 0; i < HOLD.length; i++) {
            if (clamped >= HOLD[i]) {
                return MAX_LEVELS - i;
            }
        }
        return MIN_LEVELS;
    }

    /**
     * The fraction of the round left at which each level is given up — a TABLE, read top down.
     *
     * <h2>⚠ It holds near full depth for most of the round and collapses at the end</h2>
     *
     * A linear mapping — which is what this was — starts eating colour immediately, so the deck is
     * visibly degraded within a couple of seconds and there is nowhere left to go by the halfway
     * mark. The first four levels here cost the first <b>half</b> of the round between them; the last
     * four go in its final fifth. The dread should arrive late.
     *
     * <p>⚠ Written as thresholds rather than a curve for the reason {@code SyncSpin}'s table is: an
     * exponent in the source is a shape somebody will tune by feel and nobody can read. These
     * numbers say, in order, "this is when it gets worse".
     */
    private static final double[] HOLD = {
        0.88, 0.74, 0.62, 0.52, 0.43, 0.35, 0.28, 0.21, 0.15, 0.10, 0.05,
    };

    /**
     * Quantises one colour to {@code levels} steps per channel.
     *
     * <p>⚠ Alpha is left alone. Quantising it would make a translucent overlay jump between opaque and
     * invisible, and every glass surface in this client is built out of exactly that.
     */
    public static Color colour(Color source, int levels) {
        if (source == null || levels >= 256) {
            return source;
        }
        int steps = Math.max(MIN_LEVELS, levels) - 1;
        return new Color(
                Math.round(source.getRed() * steps) / (double) steps,
                Math.round(source.getGreen() * steps) / (double) steps,
                Math.round(source.getBlue() * steps) / (double) steps,
                source.getOpacity());
    }

    /**
     * Pushes a colour away from mid-grey — the round hardening as its clock runs out.
     *
     * <p>⚠ Applied <b>before</b> quantisation, never after: contrast moves a channel by a fraction,
     * quantisation snaps it to a level, and doing it the other way round rounds the value twice and
     * throws away most of the effect. It also clamps rather than wrapping, or a bright token would
     * come out dark.
     *
     * @param amount {@code 0} for untouched; about {@code 0.6} at the end of a round
     */
    public static Color contrast(Color source, double amount) {
        if (source == null || amount <= 0) {
            return source;
        }
        double gain = 1 + amount;
        return new Color(
                clamp((source.getRed() - 0.5d) * gain + 0.5d),
                clamp((source.getGreen() - 0.5d) * gain + 0.5d),
                clamp((source.getBlue() - 0.5d) * gain + 0.5d),
                source.getOpacity());
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    /**
     * Quantises a whole image.
     *
     * <p>⚠ Returns the source unchanged at full depth, so the ordinary case costs nothing at all —
     * the deck is only posterized while a round is running, and this is called on every capture.
     *
     * @return a new image, or {@code source} when there is nothing to do
     */
    public static Image image(Image source, int levels) {
        if (source == null || levels >= 256) {
            return source;
        }
        int w = (int) source.getWidth();
        int h = (int) source.getHeight();
        if (w <= 0 || h <= 0) {
            return source;
        }

        // ⚠ ONE lookup table for all three channels, built once per call. 256 entries against
        // 400,000 pixels — the table is free and it turns two multiplies and a divide per channel
        // into an array index.
        int steps = Math.max(MIN_LEVELS, levels) - 1;
        int[] table = new int[256];
        for (int i = 0; i < 256; i++) {
            table[i] = (int) Math.round(Math.round(i / 255.0d * steps) / (double) steps * 255.0d);
        }

        int[] pixels = new int[w * h];
        PixelReader reader = source.getPixelReader();
        if (reader == null) {
            return source;
        }
        // ⚠ `WritablePixelFormat`, not `PixelFormat` — `getPixels` takes the writable subtype, and the
        // plain one does not compile against it.
        javafx.scene.image.WritablePixelFormat<java.nio.IntBuffer> format = PixelFormat.getIntArgbInstance();
        reader.getPixels(0, 0, w, h, format, pixels, 0, w);
        for (int i = 0; i < pixels.length; i++) {
            int argb = pixels[i];
            pixels[i] = (argb & 0xFF000000)
                    | (table[(argb >> 16) & 0xFF] << 16)
                    | (table[(argb >> 8) & 0xFF] << 8)
                    | table[argb & 0xFF];
        }
        WritableImage out = new WritableImage(w, h);
        out.getPixelWriter().setPixels(0, 0, w, h, format, pixels, 0, w);
        return out;
    }
}
