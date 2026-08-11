package io.github.stoicswe.eyeandsickle.protocol.game;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

/**
 * The whole botnet in one read — {@code docs/design/10-botnets.md} §2, §5.
 *
 * @param bots every bot the player owns, built-but-idle ones included
 * @param controlChannelCycles what the live ones hold on the player's own rig, summed. ⚠ This is the
 *     number §3 calls the self-correcting cap on botnet size, so it is published rather than left for
 *     the interface to add up — a cap works only while there is one place that says what it is
 * @param offloadCapacityCycles tool cycles Injectors are currently offering (§5.2). ⚠ Derived on
 *     every read from the live bots, never stored: a stored copy of a derived ceiling is {@code
 *     ChainState.networkHashrate}'s bug waiting to happen, and it is also what would let a
 *     hand-edited save grant the whole ladder
 * @param offloadInUseCycles how much of that is currently carrying work
 * @param bufferedWei everything the Miner functions are holding, summed — what a collect would sweep
 * @param reports what the Watchers have seen, newest first (§5.5)
 */
public record BotnetSnapshot(
        List<BotView> bots,
        long controlChannelCycles,
        long offloadCapacityCycles,
        long offloadInUseCycles,
        BigInteger bufferedWei,
        List<Report> reports) {

    /**
     * One thing a Watcher saw.
     *
     * @param subject what kind of thing — the watcher's three subjects are work queued, value moved,
     *     and (unbuilt, §5.5) INTEL
     * @param copyable whether the player may spend cycles to take a copy. ⚠ Always false today: the
     *     only copyable subject is INTEL and {@code docs/design/14} has not defined it. The field
     *     exists so the seam is visible rather than discovered later
     */
    public record Report(
            Instant at, String botId, String hostAddress, String hostLabel, String subject, String detail,
            boolean copyable) {}

    /** Nothing built yet — what a new character reads. */
    public static BotnetSnapshot empty() {
        return new BotnetSnapshot(List.of(), 0L, 0L, 0L, BigInteger.ZERO, List.of());
    }

    /** Live bots, which is the count §1 makes every other cost scale with. */
    public int liveCount() {
        return (int) bots.stream().filter(BotView::live).count();
    }
}
