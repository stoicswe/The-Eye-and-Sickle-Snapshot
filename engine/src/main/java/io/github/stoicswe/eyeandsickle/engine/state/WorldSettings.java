package io.github.stoicswe.eyeandsickle.engine.state;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import java.math.BigInteger;

/**
 * How this character's world was set up — chosen once, at creation, before anything is generated.
 *
 * <h2>⚠ THIS IS NOT A CHEAT, AND THE DIFFERENCE IS NOT A MATTER OF DEGREE</h2>
 *
 * {@code CheatState} is an override applied to a game already in progress: it steps over rules the
 * player has been playing under, which is why it is solo-only, why it is hidden behind a key
 * sequence, and why every use of it is written to the rig log. This is the opposite. These are the
 * <em>terms the game was started under</em> — the size of the world, how connected it is, how often
 * it comes after you, and what was in the wallet on day one. Nothing here is applied later, nothing
 * here is hidden, and a character generated against unusual settings is not a character that was
 * altered; it is a different world.
 *
 * <p>The practical test is the one that separates a difficulty option from a cheat: <b>could the
 * player have got here by playing?</b> A twelve-server world is a world you could have been given. A
 * compute ceiling raised past the top of the ladder is not.
 *
 * <h2>⚠ IT IS AN INPUT TO GENERATION, SO IT MUST BE SET BEFORE THE WORLD EXISTS AND NEVER AFTER</h2>
 *
 * {@code TopologyGenerator.generate} is idempotent by guard — it returns immediately once a topology
 * exists, which is what stops a player re-rolling their world. So the generation fields below are
 * read exactly once, at {@code GameEngine.newCharacter}, and editing them afterwards changes
 * <em>nothing</em> about the map. That is the correct behaviour and not a limitation: a world that
 * re-shaped itself under a running character would invalidate every recon file, every foothold and
 * every folder they had built.
 *
 * <p>⚠ The two fields that are <b>not</b> generation inputs — {@link #eventChancePercent} and, at
 * creation, {@link #startingEthecoinWei} — are marked as such below. The first keeps applying for
 * the life of the character, because it is a rule rather than a shape.
 *
 * <h2>⚠ The RNG contract survives, and here is how</h2>
 *
 * The generator's promise is that a fixed seed produces a byte-identical world. That promise now
 * reads "a fixed seed <em>and these settings</em>", which is the honest statement and costs nothing
 * — the settings are on the save, beside the seed, written before the first draw. Where a setting
 * replaces a rolled value the generator still <b>draws and discards</b>, so the default path is
 * bit-for-bit what it was before this class existed.
 *
 * <h2>⚠ Sentinels: {@code 0} means "roll it", except where {@code 0} is a real answer</h2>
 *
 * A world cannot have zero servers or zero-deep servers, so {@code 0} is free to mean "randomise" on
 * those. It is <b>not</b> free on {@link #crossLinkPercent}, where "no cross-links at all" is a
 * legitimate and interesting choice — that one uses {@code -1}. The asymmetry is deliberate and is
 * exactly the kind of thing that gets unified by a later tidy-up into a setting nobody can select.
 */
public final class WorldSettings {

    /**
     * How many servers the world has, or {@code 0} to roll one in the {@code 5–18} band.
     *
     * <p>⚠ Generation input. Read once, at creation.
     */
    public int serverCount = 0;

    /**
     * How deep each server's spine is, in machines, or {@code 0} to roll one per server in the
     * {@code 4–13} band.
     *
     * <p>⚠ Generation input. ⚠ It is a <b>request</b>, not a guarantee: a server's spine may not take
     * more than {@link Balance#NET_SPINE_BUDGET_SHARE} of its machines, or a small server becomes a
     * corridor with one fork at the end — the failure {@code design/18} §2.2 records. A deep setting
     * on a small server is clamped down, exactly as a deep roll already is.
     */
    public int serverDepth = 0;

    /**
     * How likely two servers are to be linked beyond the spanning tree, as a percentage, or
     * {@code -1} for the tuned rule.
     *
     * <p>⚠ Generation input. ⚠ {@code 0} is a real setting — a pure tree, every server reachable by
     * exactly one route — which is why the sentinel here is {@code -1} and not {@code 0}. The
     * spanning tree is built first and is untouched by this, so <b>no setting can disconnect the
     * world</b>: connectivity is a property of the construction, never of a roll.
     */
    public int crossLinkPercent = -1;

    /**
     * What the counter-hack chance is scaled by, as a percentage. {@code 100} is the tuned rule.
     *
     * <p>⚠ <b>Not</b> a generation input — it is read every time anything rolls, for the life of the
     * character, so changing it changes the game from that moment. That is the right behaviour for a
     * difficulty setting and the wrong behaviour for a world shape, which is why the two are
     * described separately here rather than being lumped together as "options".
     *
     * <p>⚠ It composes with the developer facility's own scale rather than being overridden by it —
     * see {@code rules/WorldRules.intrusionChance}. They are different questions ("how dangerous did
     * I ask for" and "what am I forcing right now") and either can be identity.
     */
    public int eventChancePercent = 100;

    /**
     * What the wallet holds on day one.
     *
     * <p>⚠ Applied once, at creation, and it is the whole starting balance rather than a bonus on
     * top of one. ⚠ Defaults to {@link Balance#STARTING_ETHECOIN_WEI} rather than to a literal zero,
     * so that if the game's own starting balance ever moves, a character created with the default
     * still gets the game's answer instead of a stale copy of it.
     *
     * <p>⚠ Never null — money fields on a save carry zero initialisers, which is a null-safety rule
     * this codebase has already paid for once ({@code ContributionState.creditedWei} threw on the
     * login screen for want of one).
     */
    public BigInteger startingEthecoinWei = Balance.STARTING_ETHECOIN_WEI;

    public WorldSettings() {}

    /** Whether anything here differs from the game as it ships. Drives the wizard's summary line. */
    public boolean customised() {
        return serverCount != 0
                || serverDepth != 0
                || crossLinkPercent != -1
                || eventChancePercent != 100
                || !Balance.STARTING_ETHECOIN_WEI.equals(startingEthecoinWei);
    }
}
