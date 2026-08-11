package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * A sub-function fitted beside a bot's function — {@code docs/design/10-botnets.md} §5a.
 *
 * <h2>⚠ A modifier changes how a bot SURVIVES, never what it achieves</h2>
 *
 * That line is the charter of this enum and it is load-bearing. A function is the capability and its
 * ten-level ladder is a ceiling, which is why levels cost schematic material and never money alone
 * (Invariant <b>I2</b>). Modifiers are horizontal: stealth, noise, speed, resilience. The moment a
 * modifier makes a function <em>do more</em> — a Keylogger that learns two rungs, a Sipper past its
 * hourly ceiling — the function ladder has a second, cheaper entrance and the whole gate assignment
 * in {@code docs/design/02} is void.
 *
 * <p>{@link #EFFICIENT_MULTITHREADING} sits closest to that line and is why the line is written
 * down: it makes a function run <em>more often</em>, not <em>better</em>, and it is paid for in
 * noise. A Sipper with it hits its hourly ceiling sooner and never exceeds it.
 *
 * <h2>The numbers are not here</h2>
 *
 * Levels, chances, percentages and durations are balance values and live in {@code engine/Balance}.
 * This names the six things that can be fitted; it does not know what any of them is worth.
 */
public enum BotModifier {

    /**
     * Makes the bot wear a real system process's name instead of showing as an unregistered process.
     *
     * <p>⚠ Single-level, unlike the other five. It is a binary: either the process table names
     * something plausible or it does not, and a "40% disguised" name is not a thing a table can
     * render. {@code MinerState.disguise} already solved the same problem the same way for parasites,
     * and the name is pinned once for its reason — a disguise that changed between repaints would be
     * unfindable by construction, because the player would compare two readings, see two different
     * lies, and correctly conclude the table is noise.
     */
    EXE_NAME_SCRAMBLER,

    /**
     * Runs the bot's thread less often: slower, and much harder to catch.
     *
     * <p>While asleep the bot is absent from the host's process table entirely. ⚠ The speed penalty
     * is the price and it must stay real — a stealth modifier with no cost is the correct fit on
     * every bot, which makes the slot a formality.
     */
    SLEEPY,

    /**
     * Cuts what the bot contributes to the owner's noise pool.
     *
     * <p>⚠ <b>Never to zero.</b> {@code docs/design/10} §1 pools all bot noise into the player's
     * aggregate — "more bots, louder you" — and a floor is what keeps that true at any level. Without
     * one, a fully dampened network would be free reach, and the noise model's whole answer to
     * "why not run fifty" would be compute alone.
     */
    DAMPENER,

    /**
     * Runs the bot's function more often, and makes it louder.
     *
     * <p>⚠ It buys <b>rate, not capability</b> — see this enum's charter. A function's per-run
     * outcome, its level's chance, and every ceiling it is bound by are untouched.
     */
    EFFICIENT_MULTITHREADING,

    /**
     * Does nothing useful for the bot. Drops confetti, a cake, a unicorn and worse on the target
     * operator's deck — and costs the owner a little Eye attention every time it fires.
     *
     * <h2>⚠ THE HEAT IS HIDDEN FROM THE PLAYER, DELIBERATELY, AND THIS ENUM MUST NOT LEAK IT</h2>
     *
     * {@code docs/design/10} §5a. Nothing player-facing names it: not the effect line the panel
     * draws, not the market description, not the rig log. {@code BotnetTest} pins that, because the
     * way this gets broken is a helpful sentence added later rather than a wrong number.
     *
     * <h2>⚠ This javadoc used to say it had no mechanical effect at all. That was wrong</h2>
     *
     * The old wording ruled out "anything it did to detection, noise or speed", on the grounds that a
     * player who fitted it for the joke would be quietly punished. Heat is none of those three, and
     * the reasoning inverts on inspection: heat is <em>long-horizon Eye attention</em>, and this
     * module's whole function is to announce your bot's presence with a unicorn. Attention is not a
     * stat bolted onto a joke — it is what the joke <em>is</em>, in a game about a surveillance
     * state. What the old sentence was really protecting is intact and still binds: <b>a modifier may
     * impose a cost that follows from its own fiction, and may never grant a benefit.</b>
     *
     * <p>⚠ Its <em>visible</em> half needs a target who is a person — it draws on somebody else's
     * screen. In solo there is nobody there, so it lands nowhere while the heat is still charged: the
     * bot ran the routine, and whether a human was watching is not what makes it conspicuous. See
     * {@code docs/design/10} §6 BN-5.
     */
    BEDAZZLE_PRO,

    /**
     * Blocks removal attempts, and covers the bot's tracks when it does.
     *
     * <p>⚠ It is <b>charges</b>, not permanent protection. A blocked removal spends one, and when
     * they are gone the next attempt takes the bot. Permanent protection at a high level would mean a
     * bot that cannot be lost, and {@code docs/design/10} §1a's total loss is the cost the entire
     * "botnets are risk" argument in §4 rests on.
     */
    PROTECTOR
}
