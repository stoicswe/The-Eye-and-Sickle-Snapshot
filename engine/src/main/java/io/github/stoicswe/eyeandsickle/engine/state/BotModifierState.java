package io.github.stoicswe.eyeandsickle.engine.state;

import java.time.Instant;

/**
 * One modifier fitted beside a bot's functions — {@code docs/design/10-botnets.md} §5a.
 *
 * <p>⚠ Destroyed with the bot exactly as a {@link BotFunctionState} is, at <b>every</b> chassis tier
 * including the resilient ones. §2.3's resilience is about the <em>chassis</em> surviving a removal
 * undamaged; the sockets empty either way, and that is what keeps §4's third cost real.
 */
public final class BotModifierState {

    /** A {@code BotModifier} name. */
    public String modifier = "";

    /**
     * 1–5, or 1 for the exe-name scrambler, which is a binary.
     *
     * <p>⚠ Belongs to the instance, for {@link BotFunctionState}'s reason: a level that survived a
     * loss would make rebuilding cost one frame and nothing else.
     */
    public int level = 1;

    public Instant fittedAt = Instant.EPOCH;

    /**
     * PROTECTOR only: blocks left.
     *
     * <p>⚠ Charges rather than permanent protection. A high-level Protector that could not run out
     * would be a bot that cannot be lost, and §1a's total loss is what §4's whole "botnets are risk"
     * argument rests on.
     */
    public int protectorCharges = 0;

    /**
     * BEDAZZLE_PRO only: when it last rolled.
     *
     * <p>⚠ It needs a cadence of its own rather than riding on whether a function <em>succeeded</em>.
     * The first version hooked it to the settle's return value, and a Keylogger returns false once
     * the host's recon file is full — so the module silently stopped costing anything after a few
     * hours, on the bot that had been running longest. "Per function execution" means the function
     * ran, not that it produced something.
     */
    public Instant lastRunAt = Instant.EPOCH;
}
