package io.github.stoicswe.eyeandsickle.engine.rules;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.breach.Rng;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.HostState;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import java.time.Duration;
import java.time.Instant;

/**
 * Somebody comes for the player without being provoked — {@code docs/design/19} §9.
 *
 * <h2>Why this exists</h2>
 *
 * Every intrusion in the game was a <b>reprisal</b>: a sweep that was noticed, a breach that was
 * answered, a port scan that was detected. So the defence round could only ever happen to a player
 * who had just been offensive, and a cautious one never saw it at all — which makes the whole
 * defensive half of the game, and the compute they spend standing on it, something that happens to
 * other people.
 *
 * <p>This is the other direction: <b>The Eye comes to you</b>. Its rate is a function of personal
 * heat, which is exactly what heat measures — how much attention you are being paid — so the pressure
 * arrives from the thing the player has been managing all along rather than from a new number.
 *
 * <h2>⚠ THE ROLL IS PER HOUR, NEVER PER TICK</h2>
 *
 * A chance-per-tick makes a faster-ticking client attack the player more often and hands a three-day
 * absence exactly one roll — both invisible in play, and both make the tuned figure meaningless. This
 * is {@code 1 - e^(-rate × hours)} against the tick's own elapsed time, the same shape
 * {@code ShadowTrading}'s listings already use, so the frequency of the tick drops out of the answer.
 *
 * <h2>⚠ It does NOT fire while the player is away</h2>
 *
 * The rate is computed from elapsed time, so a long absence would otherwise arrive as a near-certain
 * attack the instant the client opens — a defence round the player is thrown into before they have
 * looked at their own rig. {@code resume()} settles income and tasks; it deliberately does not settle
 * this. What happens while you are gone is {@code design/09}'s Auto-Counter Daemon, which is the tool
 * that exists for exactly that and is already built.
 */
public final class AmbientIntrusion {

    private AmbientIntrusion() {}

    /**
     * Rolls whether somebody comes, and names them.
     *
     * <p>⚠ <b>Draws unconditionally.</b> The roll is taken before any of the conditions below are
     * consulted, so the RNG stream's shape does not depend on the player's heat, their cooldown or
     * whether a breach happens to be open — a stored seed stays a replay. Same rule the intrusion
     * scale and the world generator both follow.
     *
     * @return the machine that is coming, or {@code null} for nobody
     */
    public static HostState rollFor(GameSave save, Duration elapsed, Instant now) {
        if (save == null || elapsed == null || elapsed.isNegative() || elapsed.isZero()) {
            return null;
        }
        Rng rng = Rng.of(save);
        double roll = rng.nextDouble();
        rng.commit(save);

        double hours = elapsed.toMillis() / 3_600_000.0d;
        // ⚠ The cheat scales the CHANCE, not the rate — `Cheats.intrusionChance` clamps to [0,1]
        // because it is written for probabilities, and a per-hour rate is not one. Applying it to the
        // rate would silently cap the rate at 1/hour and make the developer slider do the opposite of
        // what it says at the top of its range.
        double chance = Cheats.intrusionChance(save, 1 - Math.exp(-ratePerHour(save) * hours));
        if (roll >= chance) {
            return null;
        }
        if (!due(save, now)) {
            return null;
        }
        return attacker(save);
    }

    /**
     * How often, per hour, at this character's heat.
     *
     * <h2>⚠ There is a FLOOR and it is not zero</h2>
     *
     * A rate of zero at zero heat would mean a careful player never once has to defend, and every
     * defensive tool in {@code design/09} is then a purchase with no occasion to use it. The floor is
     * the game saying that being quiet is not the same as being invisible.
     *
     * <p>⚠ And there is a ceiling, because heat is already punishing in four other ways. Personal heat
     * at 100 buys a hard time; it must not buy a round every ninety seconds, which is a client nobody
     * can put down.
     */
    public static double ratePerHour(GameSave save) {
        double heat = save == null ? 0 : Math.max(0, Math.min(Balance.PERSONAL_HEAT_MAX, save.personalHeat));
        double scaled = heat / (double) Balance.PERSONAL_HEAT_MAX;
        double rate = Balance.AMBIENT_INTRUSION_BASE_PER_HOUR
                + scaled * (Balance.AMBIENT_INTRUSION_HOT_PER_HOUR - Balance.AMBIENT_INTRUSION_BASE_PER_HOUR);
        return rate;
    }

    /**
     * Whether enough time has passed since the last one.
     *
     * <p>⚠ The cooldown is what makes a rate safe to tune. Without it an unlucky run of rolls stacks
     * two rounds back to back, and thirty seconds of arcade twice in a minute is not tension, it is a
     * client the player cannot use.
     */
    public static boolean due(GameSave save, Instant now) {
        if (save.lastAmbientIntrusionAt == null) {
            return true;
        }
        return !now.isBefore(save.lastAmbientIntrusionAt.plusSeconds(Balance.AMBIENT_INTRUSION_COOLDOWN_SECONDS));
    }

    /** Records that one happened, so the cooldown starts. */
    public static void mark(GameSave save, Instant now) {
        save.lastAmbientIntrusionAt = now;
    }

    /**
     * Who is coming.
     *
     * <h2>⚠ A machine the player has actually met, and a DEFENDED one first</h2>
     *
     * The attacker's address goes into the rig log and the access log, so it has to be somewhere the
     * player can go and look — the same rule the reprisal follows. A defended machine is preferred
     * because it is the one that plausibly noticed: this is the estate you have been poking at coming
     * back, not a stranger.
     *
     * <p>Falls back to any machine in the world, and then to {@code null} — which the caller reads as
     * "nobody", because an attack from nowhere is a log line the player cannot act on.
     */
    private static HostState attacker(GameSave save) {
        if (save.topology == null) {
            return null;
        }
        HostState discovered = null;
        HostState any = null;
        for (HostState host : save.topology.hosts) {
            if (HostKind.SELF.name().equals(host.kind)) {
                continue;
            }
            if (host.discovered && host.defended) {
                return host;
            }
            if (host.discovered && discovered == null) {
                discovered = host;
            }
            if (any == null) {
                any = host;
            }
        }
        return discovered != null ? discovered : any;
    }
}
