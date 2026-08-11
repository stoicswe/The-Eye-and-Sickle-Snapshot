package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * What a bot actually does — {@code docs/design/10-botnets.md} §5.
 *
 * <p>A frame is a chassis and supplies slots; the function socketed into it is the capability. The
 * roles the old §2 gave whole blueprints to (recon, mining, surveillance) are loadouts now, which is
 * why this enum exists and a {@code BotFrameKind} does not.
 *
 * <h2>⚠ There is deliberately no constant that touches a breach board</h2>
 *
 * The amended §2.0 deletes the Breacher frame. Invariant <b>I10</b> — "a bot never solves the puzzle
 * for the player" — used to be a tuning problem: a heuristic player had to be kept reliably worse
 * than a human forever, and {@code docs/design/15} §2 <b>P-3</b> records that the margin cannot be
 * measured until the puzzle is played at scale. With no constant here for it, there is no code path
 * from a bot to a puzzle at all. <b>Adding one is not a feature, it is abandoning I10.</b>
 *
 * <h2>The numbers are not here, and that is the module charter</h2>
 *
 * Levels, chances, cycle counts, tax rates and prices are all balance values and all live in {@code
 * engine/Balance}. This names the five things a bot can be; it does not know what any of them is
 * worth. {@code protocol}'s rule is that if a constant changed and a player would gain something, it
 * is a balance value and belongs to the server.
 */
public enum BotFunction {

    /**
     * Fills in the player's recon file on the host it sits on, one finding at a time — §5.1.
     *
     * <p>It rolls against an unlearned rung of the port-scan ladder and buys <em>probability</em>,
     * never reach: it cannot learn a rung a port scan could not. That classification is what puts it
     * on the ethecoin gate ({@code docs/design/02} §1.1 step 4), the same reading the sweep ladder
     * gets.
     */
    KEYLOGGER,

    /**
     * Offloads the player's tool cycles onto the host — §5.2. <b>Never mining.</b>
     *
     * <p>⚠ The only function that hands the player compute, and therefore the only one money may not
     * reach. An ethecoin-gated Injector is ethecoin buying capacity, which is Invariant <b>I1</b>
     * with extra steps; it is schematic-gated for that reason and for no other.
     *
     * <p>⚠ The mining exclusion is enforced at the reservation, by consumer. Offloaded cycles that
     * could mine would close the flywheel — mine, buy bots, offload, mine faster — which is the loop
     * I1 exists to prevent.
     */
    INJECTOR,

    /**
     * Mines on the host and buffers the yield on the bot — §5.3.
     *
     * <p>Invariant <b>I6</b>: the work is the host's cycles, not the player's. Invariant <b>I4</b>
     * does <em>not</em> extend to it — self-mining is immune because it is local, and this is on
     * somebody else's machine, loud, and seizable.
     */
    MINER,

    /**
     * Taxes ledger transactions the host makes — §5.4.
     *
     * <p>⚠ Read §5.4 before touching its numbers. The tax <em>rate</em> is not what bounds this; a
     * percentage of a derived, unbounded NPC transaction stream is an ethecoin printer that renders
     * correctly the whole time it prints. What bounds it is a per-hour ceiling on total value taken.
     */
    SIPPER,

    /**
     * Reports what the host's operator is doing — §5.5.
     *
     * <p>⚠ How many subjects it can follow at once is the <b>frame's</b> tier, not this function's
     * level. That is the one place the two ladders do different work: the level buys fidelity, the
     * chassis buys parallelism.
     */
    WATCHER
}
