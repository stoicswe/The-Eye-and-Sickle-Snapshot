package io.github.stoicswe.eyeandsickle.protocol.game;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

/**
 * One bot, as the interface sees it — {@code docs/design/10-botnets.md} §2.
 *
 * <p>A bot exists in two states and the wire type has to render both: <em>built but not uploaded</em>
 * (sitting on the player's rig, holding nothing, doing nothing) and <em>live on a host</em>. {@link
 * #live()} is the question every readout asks first, and it is derived from the host address rather
 * than carried as its own flag so the two cannot disagree.
 *
 * @param botId stable identity, and what every action names
 * @param frameType the catalogue id of the chassis — {@code bot-frame-v1}
 * @param frameTier 1, 2 or 3; also how many subjects a Watcher may follow at once (§5.5)
 * @param slots how many functions this chassis holds. ⚠ Carried rather than derived from {@code
 *     frameTier} because the interface must be able to draw an empty slot, and a client that had to
 *     know the tier→slots table would be knowing a balance value
 * @param hostAddress the machine it is running on, or empty when it has not been uploaded
 * @param controlChannelCycles what it holds on the <em>player's</em> rig while live (§2.2). ⚠ This is
 *     never the host's cost — by Invariant I6 the bot's work is charged to the host, and the two must
 *     never be summed into one number for the same reason {@code CONTROL_CHANNEL} and {@code
 *     DEPLOYED_MINER} may not be
 * @param bufferedWei yield sitting on the bot waiting to be collected, from a Miner function
 * @param uploadedAt when it went live, or {@link Instant#EPOCH} when it has not
 */
public record BotView(
        String botId,
        String frameType,
        String frameName,
        int frameTier,
        int slots,
        String hostAddress,
        String hostLabel,
        long controlChannelCycles,
        List<Slot> functions,
        List<Mod> modifiers,
        int modifierSlots,
        boolean damaged,
        boolean discovered,
        String processName,
        BigInteger bufferedWei,
        Instant builtAt,
        Instant uploadedAt) {

    /**
     * One socketed function and the level it was built to.
     *
     * <p>⚠ The level belongs to the <b>instance</b>, not to the player (§5). A level-7 Keylogger is a
     * thing you built and a thing you can lose — if levelling were player-wide knowledge a destroyed
     * bot would cost nothing durable, and §4's whole "botnets are risk" argument would be false.
     *
     * @param effect what this level does, already in words. ⚠ The client is told the effect, it does
     *     not compute one: the tables behind it are balance values
     */
    public record Slot(BotFunction function, int level, String effect) {}

    /**
     * One fitted modifier — {@code docs/design/10} §5a.
     *
     * @param charges a Protector's remaining blocks; zero for every other modifier. ⚠ Published
     *     because a Protector whose charges are spent looks identical to one that is fresh, and the
     *     difference is whether the next removal attempt takes the bot
     */
    public record Mod(BotModifier modifier, int level, int charges, String effect) {}

    /** Whether it is on a machine. Derived, so nothing can report a live bot with nowhere to be. */
    public boolean live() {
        return hostAddress != null && !hostAddress.isBlank();
    }

    /**
     * Whether it could be uploaded — §2.1's opening refusal: a chassis is not a capability.
     *
     * <p>⚠ A damaged frame is refused here too. It is not destroyed and it is not usable; that third
     * state is the whole of what §2.3's repair-or-recycle choice is about.
     */
    public boolean uploadable() {
        return !live() && !damaged && !functions.isEmpty();
    }

    /** Free function slots, never negative on a hand-edited save. */
    public int freeSlots() {
        return Math.max(0, slots - functions.size());
    }

    /** Free modifier slots. Zero on a {@code v1}, which has none at all. */
    public int freeModifierSlots() {
        return Math.max(0, modifierSlots - modifiers.size());
    }
}
