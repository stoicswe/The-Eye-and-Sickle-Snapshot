package io.github.stoicswe.eyeandsickle.engine.state;

import java.math.BigInteger;
import java.time.Instant;

/**
 * One function socketed into a frame — {@code docs/design/10-botnets.md} §5.
 *
 * <h2>⚠ The level is a property of THIS INSTANCE and must never become a property of the player</h2>
 *
 * §5 is explicit and the reason is §1a: total loss destroys the bot and everything socketed into it.
 * If a level-7 Keylogger's level lived on the save as player-wide knowledge, rebuilding after a loss
 * would cost one frame and nothing else — and §4's entire "a botnet is risk, not just power" argument
 * would be false while every screen still rendered. The blueprint survives a loss (Invariant
 * <b>I11</b>); the level does not.
 */
public final class BotFunctionState {

    /** {@code KEYLOGGER}, {@code INJECTOR}, {@code MINER}, {@code SIPPER}, {@code WATCHER}. */
    public String function = "";

    /**
     * 1–10. Level 1 is what was bought or compiled; every level above it cost ethecoin <em>and</em>
     * schematic material, which is what keeps a ladder off the money gate (§5, Invariant I2).
     */
    public int level = 1;

    /**
     * The item that was consumed to socket this, for the record only.
     *
     * <p>Nothing reads it as authority — the function and its level are here, not on the item — but a
     * player who wants to know where a level-6 Sipper came from has nowhere else to look.
     */
    public String itemType = "";

    public Instant socketedAt = Instant.EPOCH;

    // ------------------------------------------------------------------ per-function working state

    /**
     * INJECTOR only: whether the host's operator has actually installed the package.
     *
     * <p>⚠ Until this is true the Injector offers <b>nothing</b> (§5.2). Dropping a package in
     * somebody's Downloads is not the same as them running it, and the roll that decides is
     * deliberately not the player's to force — an offload is a thing you keep only while nobody
     * notices.
     */
    public boolean injectorInstalled = false;

    /** INJECTOR only: when the package was dropped, which is what the install roll is measured from. */
    public Instant injectorDroppedAt = Instant.EPOCH;

    /**
     * MINER only: yield sitting on the bot, waiting to be collected or auto-deposited.
     *
     * <p>⚠ Initialised, never left null. {@code ContributionState.creditedWei} threw an NPE on the
     * login screen for want of exactly this, and every money field in this codebase carries the same
     * rule for the same reason.
     */
    public BigInteger bufferedWei = BigInteger.ZERO;

    /** MINER only: when yield was last swept into the buffer. Drives the bounded offline accrual (I5). */
    public Instant lastAccruedAt = Instant.EPOCH;

    /** MINER only: when the last auto-deposit landed. L5+ only — see {@code Balance.BOT_MINER_AUTODEPOSIT_*}. */
    public Instant lastDepositAt = Instant.EPOCH;

    /** KEYLOGGER / SIPPER / WATCHER: when this function last did its cadence's worth of work. */
    public Instant lastRunAt = Instant.EPOCH;

    /**
     * SIPPER only: total value taken this hour, and when that hour started.
     *
     * <p>⚠ <b>This pair is the bound, and the tax rate is not.</b> §5.4: NPC transactions are derived
     * rather than stored, so a percentage of them is a percentage of an unbounded invented stream.
     * The per-hour ceiling is what makes the Sipper an income source rather than a printer, and it
     * cannot be enforced without remembering how much has already been taken.
     */
    public BigInteger sippedThisWindowWei = BigInteger.ZERO;

    public Instant sipWindowStartedAt = Instant.EPOCH;
}
