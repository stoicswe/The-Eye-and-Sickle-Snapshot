package io.github.stoicswe.eyeandsickle.engine.net;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.state.HostState;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * What a machine's operator has been doing — derived, seekable, and stored nowhere.
 *
 * <h2>⚠ ONE derivation, and the reason is that the player can see both consumers at once</h2>
 *
 * {@code docs/design/10-botnets.md} §5.6. A Sipper taxes what a host moves and a Watcher reports it.
 * Two derivations would be two answers to "what did this machine do" — and a tax on a transaction the
 * Watcher never mentioned is exactly the kind of contradiction that makes a whole panel untrustworthy.
 * So both read this, and neither computes its own.
 *
 * <h2>⚠ DERIVED, NEVER DRAWN — the same rule the rest of this package lives under</h2>
 *
 * A draw against {@code save.rngSeed} would shift every later draw in the save, so a bot's income
 * would silently change which puzzle a breach generates. It would also make the past re-rollable: a
 * player could reload to get a richer hour. This is a pure function of (address, slot), so asking
 * twice cannot give two answers, and asking about last Tuesday is as cheap as asking about now.
 *
 * <p>⚠ It is also why nothing here is persisted. The <em>sighting</em> of an event is persisted
 * ({@code BotReportState}), because that is a fact about what a bot happened to be watching; the
 * event itself can always be recomputed.
 *
 * <h2>This is NPC fiction, not the chain</h2>
 *
 * The value here never touches {@code save.ledger} or the mempool. It is what a stranger's machine is
 * doing, which nothing else in the game models — and modelling it as real ledger rows would mean
 * every NPC needed a wallet the whole economy had to keep honest ({@code docs/design/10} §5.4 records
 * that as the road not taken).
 */
public final class HostActivity {

    private HostActivity() {}

    /**
     * How long one slot of host activity lasts.
     *
     * <p>⚠ The unit of <b>derivation</b>, deliberately unrelated to any function's cadence. A Sipper
     * settling every 90 seconds and a Watcher every 10 minutes both integrate over the same slots and
     * therefore agree — which is the whole of §5.6. Tying slots to a cadence would make the two
     * disagree the moment either cadence was re-tuned.
     */
    static final long SLOT_SECONDS = 900L;

    /** What kind of thing happened. */
    public enum Kind {
        /** The operator queued work — a scan, a build, a transfer. */
        WORK,
        /** Value moved. This is what a Sipper takes its share of. */
        VALUE
    }

    /**
     * One thing that happened on a host.
     *
     * @param valueWei zero for {@link Kind#WORK} — work is not money, and a Watcher reporting a
     *     cycle-count as an amount would teach the player to read the two columns wrong
     */
    public record Event(Instant at, Kind kind, BigInteger valueWei, String detail) {}

    /**
     * Total value that moved through {@code host} in {@code [from, to)}.
     *
     * <p>Whole slots are summed exactly and partial slots at each end are prorated, so the answer is
     * additive over adjoining windows: asking twice for two halves gives the same total as asking
     * once for the whole. ⚠ That property is what makes the Sipper's hourly ceiling meaningful — a
     * non-additive integral would let a caller extract more by settling more often, which is the
     * chance-per-tick defect wearing an integration bug's clothes.
     */
    public static BigInteger valueMoved(HostState host, Instant from, Instant to) {
        if (host == null || from == null || to == null || !from.isBefore(to)) {
            return BigInteger.ZERO;
        }
        BigInteger perHour = Balance.botHostValuePerHour(host.tier);
        if (perHour.signum() <= 0) {
            return BigInteger.ZERO;
        }
        long firstSlot = Math.floorDiv(from.getEpochSecond(), SLOT_SECONDS);
        long lastSlot = Math.floorDiv(to.getEpochSecond() - 1, SLOT_SECONDS);
        BigInteger total = BigInteger.ZERO;
        for (long slot = firstSlot; slot <= lastSlot; slot++) {
            long slotStart = slot * SLOT_SECONDS;
            long overlapStart = Math.max(slotStart, from.getEpochSecond());
            long overlapEnd = Math.min(slotStart + SLOT_SECONDS, to.getEpochSecond());
            long overlap = overlapEnd - overlapStart;
            if (overlap <= 0) {
                continue;
            }
            total = total.add(slotValue(host, slot)
                    .multiply(BigInteger.valueOf(overlap))
                    .divide(BigInteger.valueOf(SLOT_SECONDS)));
        }
        return total;
    }

    /**
     * Everything that happened in {@code [from, to)}, oldest first.
     *
     * <p>⚠ Bounded by the window rather than by a count. A caller asking about a four-day absence gets
     * four days of slots, which is a few hundred events — the Watcher's own settle is what limits how
     * many become reports, because how much a bot <em>noticed</em> is a rule and how much
     * <em>happened</em> is not.
     */
    public static List<Event> between(HostState host, Instant from, Instant to) {
        List<Event> events = new ArrayList<>();
        if (host == null || from == null || to == null || !from.isBefore(to)) {
            return events;
        }
        long firstSlot = Math.floorDiv(from.getEpochSecond(), SLOT_SECONDS);
        long lastSlot = Math.floorDiv(to.getEpochSecond() - 1, SLOT_SECONDS);
        for (long slot = firstSlot; slot <= lastSlot; slot++) {
            // The event sits at a stable offset inside its slot, so an event does not jump around
            // when the same window is asked about twice.
            long offset = (long) (AddressHash.unitOf(host.address, "activity-when:" + slot) * SLOT_SECONDS);
            Instant at = Instant.ofEpochSecond(slot * SLOT_SECONDS + offset);
            if (at.isBefore(from) || !at.isBefore(to)) {
                continue;
            }
            boolean money = AddressHash.unitOf(host.address, "activity-kind:" + slot) < 0.45d;
            if (money) {
                BigInteger value = slotValue(host, slot);
                if (value.signum() <= 0) {
                    continue;
                }
                events.add(new Event(at, Kind.VALUE, value, valueDetail(host, slot)));
            } else {
                events.add(new Event(at, Kind.WORK, BigInteger.ZERO, workDetail(host, slot)));
            }
        }
        return events;
    }

    /**
     * A stable {@code 0…1} for one host and one purpose — the roll a bot function makes.
     *
     * <h2>⚠ HASHED, NEVER DRAWN, and that is what makes a bot save-scum-proof</h2>
     *
     * "Chance of success" reads exactly like a per-attempt roll against {@code save.rngSeed}, and
     * implemented as one it makes reloading the cheapest strategy in the game — the same argument
     * {@code NetRules} makes about re-sweeping ("re-sweeping is not a reroll"). Salting with the
     * cadence index is what still lets the <em>next</em> attempt differ.
     *
     * <p>It also leaves the RNG stream alone. A draw taken here would shift every later draw in the
     * save, so a keylogger ticking would silently change which puzzle the next breach generates.
     *
     * <p>Published from this class rather than from {@code AddressHash} so that the mixing keeps one
     * home — {@code AddressHash}'s own javadoc records that a second copy of it is how the
     * {@code String.hashCode} trap bit here twice.
     */
    public static double roll(HostState host, String salt) {
        return AddressHash.unitOf(host == null ? "" : host.address, salt);
    }

    /**
     * What one slot is worth on this host.
     *
     * <p>The tier's published hourly figure, split across the slots in an hour and jittered so a
     * quiet hour and a busy one look different. ⚠ The jitter is <b>symmetric about 1.0</b>: a
     * one-sided spread would make the aggregate quietly disagree with
     * {@code Balance.BOT_HOST_VALUE_PER_HOUR}, which is the number the Sipper's ceiling was
     * calibrated against.
     */
    private static BigInteger slotValue(HostState host, long slot) {
        BigInteger perHour = Balance.botHostValuePerHour(host.tier);
        double jitter = 0.4d + 1.2d * AddressHash.unitOf(host.address, "activity-value:" + slot);
        BigDecimal perSlot = new BigDecimal(perHour)
                .multiply(BigDecimal.valueOf(SLOT_SECONDS))
                .divide(BigDecimal.valueOf(3600L), MathContext.DECIMAL64);
        return perSlot.multiply(BigDecimal.valueOf(jitter)).toBigInteger();
    }

    private static final String[] VALUE_DETAIL = {
        "settled an invoice", "paid a subscription", "moved funds to another wallet",
        "took a payment", "topped up a pool balance", "cleared a pending transfer"
    };

    private static final String[] WORK_DETAIL = {
        "queued a disk scan", "started a long-running build", "kicked off a backup",
        "opened a remote session", "began indexing a share", "scheduled a maintenance job"
    };

    private static String valueDetail(HostState host, long slot) {
        int i = (int) (AddressHash.unitOf(host.address, "activity-vdetail:" + slot) * VALUE_DETAIL.length);
        return VALUE_DETAIL[Math.min(VALUE_DETAIL.length - 1, Math.max(0, i))];
    }

    private static String workDetail(HostState host, long slot) {
        int i = (int) (AddressHash.unitOf(host.address, "activity-wdetail:" + slot) * WORK_DETAIL.length);
        return WORK_DETAIL[Math.min(WORK_DETAIL.length - 1, Math.max(0, i))];
    }
}
