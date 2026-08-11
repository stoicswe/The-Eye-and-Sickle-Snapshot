package io.github.stoicswe.eyeandsickle.engine.state;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One bot: a chassis, what is socketed into it, and where it is running.
 *
 * <p>{@code docs/design/10-botnets.md} §2. A frame is a blueprint and this is an <em>instance</em>
 * built from one — which is the whole of Invariant <b>I11</b>: §1a destroys this object and the
 * {@link BotFunctionState}s inside it, and never the ability to build another.
 *
 * <h2>⚠ Two compute facts live here and they are charged to different machines</h2>
 *
 * {@link #controlChannelCycles} is the player's, held on their own rig for as long as the bot is live
 * (§2.2). The bot's <em>work</em> is the host's, by Invariant <b>I6</b>, and is not represented here
 * at all because it is not the player's to account for. Summing them into one number is the mistake
 * {@code ComputeConsumer.CONTROL_CHANNEL} already carries a warning about.
 */
public final class BotState {

    public String botId = UUID.randomUUID().toString();

    /** The chassis's catalogue id — {@code bot-frame-v1}. */
    public String frameType = "";

    /** 1, 2 or 3. Also how many subjects a socketed Watcher may follow at once (§5.5). */
    public int frameTier = 1;

    public Instant builtAt = Instant.EPOCH;

    /**
     * The machine it is running on, or empty when it has been built and not uploaded.
     *
     * <p>⚠ <b>This one field decides whether the bot is live</b>, and nothing else does. A separate
     * {@code live} flag would be a second answer to the same question, and the two would come apart
     * on the first path that forgot one of them — leaving a bot holding a control channel with
     * nowhere to be, or running on a machine with its cycles handed back.
     */
    public String hostAddress = "";

    public Instant uploadedAt = Instant.EPOCH;

    /**
     * The {@code BOT_FRAME} allocation on the player's rig, while live.
     *
     * <p>Empty when the bot is idle: an unuploaded bot holds nothing. Recalling or losing the bot
     * releases this, and the release is what §3's self-correcting cap gives back.
     */
    public String allocationId = "";

    /** What the player's rig holds for it. Reserved on upload, released on recall or loss. */
    public long controlChannelCycles = 0L;

    /**
     * The sockets. ⚠ Never null and never longer than the chassis's slot count.
     *
     * <p>An empty list is a real and important state, not a broken one: §2.1 refuses to upload a
     * frame with nothing in it, and that refusal is the model's opening statement — the chassis is
     * not the capability.
     */
    public List<BotFunctionState> functions = new ArrayList<>();

    /**
     * The modifier sockets — {@code docs/design/10} §5a. ⚠ Never null, never longer than the tier's
     * modifier count.
     */
    public List<BotModifierState> modifiers = new ArrayList<>();

    // ------------------------------------------------------------------ being found (§2.3, §5a)

    /**
     * Whether the host's operator has noticed it.
     *
     * <p>⚠ Discovery is a <b>state</b>, not an event, for {@code GameSave.pendingIntrusion*}'s reason:
     * a player told about it while a window was busy must not lose the warning, and one who closes
     * the client must not escape the consequence by doing so. A removal attempt follows a discovery
     * on the host's own clock, not on the player's attention.
     */
    public boolean discovered = false;

    public Instant discoveredAt = Instant.EPOCH;

    /**
     * Damaged frames need a repair or a recycle before they can hold anything again — §2.3.
     *
     * <p>⚠ A damaged chassis is <b>kept</b>, not deleted. That is the whole of the difference between
     * being <em>removed</em> and being <em>destroyed</em>: the resilient tiers (v6, v8, v10) come back
     * usable, every other tier comes back needing work, and §1a's total loss removes the object
     * outright. Three outcomes, and collapsing any two of them deletes a tier's reason to exist.
     */
    public boolean damaged = false;

    /**
     * Hidden from the host until this instant — what a Protector's block buys, with a Sleepy fitted.
     *
     * <p>⚠ An <b>instant</b> rather than a countdown, so it settles correctly across a quit.
     * {@code GameSave.noiseSpikeUntil} carries the same rule for the same reason: a remaining-seconds
     * field pauses with the game and leaves the hiding waiting to be served on the next launch.
     */
    public Instant hiddenUntil = Instant.EPOCH;

    /**
     * The name it wears in the host's process table, when a scrambler is fitted.
     *
     * <p>⚠ Stored and pinned, never re-derived per read — {@code MinerState.disguiseName}'s rule. A
     * disguise that changed between repaints is unfindable by construction: the player compares two
     * readings, sees two different lies, and correctly concludes the table is noise. It is re-rolled
     * on exactly one event, a Protector blocking a removal, because that is the fiction of covering
     * your tracks.
     */
    public String processName = "";

    /** When the discovery clock was last advanced. Drives the per-hour rate across an absence. */
    public Instant lastSeenAt = Instant.EPOCH;

    /** Convenience for the modifier sockets. */
    public BotModifierState modifier(String kind) {
        if (modifiers == null || kind == null) {
            return null;
        }
        for (BotModifierState m : modifiers) {
            if (kind.equals(m.modifier)) {
                return m;
            }
        }
        return null;
    }

    /** Convenience for the many readers that want one function by kind. */
    public BotFunctionState function(String kind) {
        if (functions == null || kind == null) {
            return null;
        }
        for (BotFunctionState f : functions) {
            if (kind.equals(f.function)) {
                return f;
            }
        }
        return null;
    }

    /** Whether it is on a machine — derived, for {@link #hostAddress}'s reason. */
    public boolean live() {
        return hostAddress != null && !hostAddress.isBlank();
    }
}
