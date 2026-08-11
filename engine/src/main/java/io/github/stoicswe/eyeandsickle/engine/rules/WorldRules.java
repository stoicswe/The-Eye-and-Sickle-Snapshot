package io.github.stoicswe.eyeandsickle.engine.rules;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.WorldSettings;

/**
 * The terms a character's world was started under — {@code state/WorldSettings} made into rules.
 *
 * <h2>⚠ THE LEGITIMATE COUNTERPART TO {@code Cheats}, AND THE SEPARATION IS THE POINT</h2>
 *
 * These two classes look similar and are opposites. {@code Cheats} overrides rules a game is already
 * running under — hidden, solo-only, logged on every use, and refused outright for a character that
 * can reach another player. This one holds choices made <em>before the first draw</em>: how big the
 * world is, how connected, how often it comes after you, what was in the wallet. A player who picks
 * a twelve-server world has not altered a game; they have started a different one.
 *
 * <p>Keeping them apart matters because they meet in exactly one place — {@link #intrusionChance} —
 * and the temptation there is to collapse them into a single multiplier. That would make the
 * difficulty a player chose indistinguishable from an override somebody is forcing, on the readout
 * where the distinction is most load-bearing: whether the game is being played or inspected.
 *
 * <h2>⚠ GENERATION SETTINGS ARE READ ONCE AND ONLY BY THE GENERATOR</h2>
 *
 * {@link #serverCount}, {@link #serverDepth} and {@link #crossLinkChance} are inputs to
 * {@code TopologyGenerator.generate}, which runs once per character and refuses to run twice. Reading
 * them anywhere else would imply they could be changed later, and they cannot: a world that re-shaped
 * itself under a running character would invalidate every recon file, foothold and folder built
 * against it. {@link #intrusionChance} is the one that keeps applying, because it is a rule rather
 * than a shape.
 */
public final class WorldRules {

    private WorldRules() {}

    /** The lowest server count a player may choose. Below this a world has almost no structure. */
    public static final int MIN_SERVERS = Balance.NET_SERVERS_MIN;

    /** The highest. Raised with {@code NET_SERVERS_MAX} on 2026-08-09. */
    public static final int MAX_SERVERS = Balance.NET_SERVERS_MAX;

    /** The band a per-server spine depth may be chosen in. */
    public static final int MIN_DEPTH = Balance.NET_NODE_DEPTH_MIN;

    public static final int MAX_DEPTH = Balance.NET_NODE_DEPTH_MAX;

    /** The ceiling on {@link WorldSettings#eventChancePercent} a player may set. */
    public static final int MAX_EVENT_CHANCE_PERCENT = 400;

    /** The most ethecoin a character may be started with. */
    public static final java.math.BigInteger MAX_STARTING_WEI =
            io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin.ofWholeEthecoin(50_000L)
                    .wei();

    /**
     * This character's world settings, never null.
     *
     * <p>⚠ Repairs the field in place, for the reason {@code Cheats.of} does: Jackson leaves a field
     * absent from the document as null, and a hook answering from a throwaway default would read
     * correctly forever while every write vanished.
     */
    public static WorldSettings of(GameSave save) {
        if (save == null) {
            return new WorldSettings();
        }
        if (save.world == null) {
            save.world = new WorldSettings();
        }
        return save.world;
    }

    // ================================================================== generation inputs

    /**
     * How many servers to build, given the value the generator rolled.
     *
     * <p>⚠ The roll is passed IN rather than taken here, so the generator draws unconditionally and
     * this only decides whether to use the result. That is the RNG contract — a draw whose existence
     * depends on a setting makes the stream's shape depend on the setting, and every downstream value
     * shifts. The default path is bit-for-bit what it was before this class existed.
     */
    public static int serverCount(GameSave save, int rolled) {
        int chosen = of(save).serverCount;
        return chosen <= 0 ? rolled : Math.max(MIN_SERVERS, Math.min(MAX_SERVERS, chosen));
    }

    /**
     * How deep one server's spine should be, given the depth the generator rolled for it.
     *
     * <p>⚠ A <b>request</b>, not a guarantee. {@code Balance.netNodeDepth} still clamps it against
     * that server's machine budget, because a spine longer than
     * {@code NET_SPINE_BUDGET_SHARE} of the machines turns the server into a corridor with one fork
     * at the end — the exact failure {@code design/18} §2.2 was written to prevent. A player asking
     * for depth 13 on a small server gets the deepest that server can afford, which is the same
     * treatment a deep roll already gets.
     */
    public static int serverDepth(GameSave save, int rolled) {
        int chosen = of(save).serverDepth;
        return chosen <= 0 ? rolled : Math.max(MIN_DEPTH, Math.min(MAX_DEPTH, chosen));
    }

    /**
     * How likely an extra server-to-server link is, beyond the spanning tree.
     *
     * <p>⚠ {@code -1} means the tuned rule and {@code 0} means "a pure tree" — both are real answers,
     * which is why the sentinel is not zero. ⚠ <b>No value here can disconnect the world</b>: the
     * spanning tree is built before this is consulted and is never removed, so connectivity is a
     * property of the construction rather than of a roll.
     */
    public static double crossLinkChance(GameSave save) {
        int chosen = of(save).crossLinkPercent;
        return chosen < 0 ? Balance.NET_SERVER_CHORD_CHANCE : Math.min(100, chosen) / 100.0d;
    }

    // ================================================================== a rule, not a shape

    /**
     * A counter-hack chance, scaled by what the player asked for and then by any developer override.
     *
     * <h2>⚠ THE ONE PLACE THE TWO SCALES MEET, AND THEY COMPOSE RATHER THAN OVERRIDE</h2>
     *
     * A player who started a world at 200% events and then forces 50% in the developer panel has
     * asked two different questions and should get both answers: the world is dangerous, and right
     * now it is being held down. Letting either win outright would make one of the two settings
     * silently do nothing — and which one depends on an ordering nothing on screen explains.
     *
     * <p>⚠ It scales the <b>chance</b> and never the draw. Both call sites — a sweep that was noticed
     * and a breach that was answered — draw unconditionally so a replay from a stored seed stays a
     * replay. ⚠ Clamped to {@code [0, 1]} at the end rather than at each step, so two scales that
     * multiply past certainty saturate instead of wrapping.
     */
    public static double intrusionChance(GameSave save, double base) {
        int percent = Math.max(0, Math.min(MAX_EVENT_CHANCE_PERCENT, of(save).eventChancePercent));
        return Cheats.intrusionChance(save, base * percent / 100.0d);
    }
}
