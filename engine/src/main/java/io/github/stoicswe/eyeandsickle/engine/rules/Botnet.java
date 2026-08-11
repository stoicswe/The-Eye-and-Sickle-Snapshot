package io.github.stoicswe.eyeandsickle.engine.rules;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.Catalogue;
import io.github.stoicswe.eyeandsickle.engine.net.HostActivity;
import io.github.stoicswe.eyeandsickle.engine.net.HostArchetypes;
import io.github.stoicswe.eyeandsickle.engine.net.NodeReports;
import io.github.stoicswe.eyeandsickle.engine.net.PortScanRules;
import io.github.stoicswe.eyeandsickle.engine.fs.VirtualFs;
import io.github.stoicswe.eyeandsickle.engine.state.AllocationState;
import io.github.stoicswe.eyeandsickle.engine.state.BotFunctionState;
import io.github.stoicswe.eyeandsickle.engine.state.BotModifierState;
import io.github.stoicswe.eyeandsickle.engine.state.BotReportState;
import io.github.stoicswe.eyeandsickle.engine.state.BotState;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.HostState;
import io.github.stoicswe.eyeandsickle.engine.state.ItemState;
import io.github.stoicswe.eyeandsickle.engine.state.NodeReportState;
import io.github.stoicswe.eyeandsickle.protocol.game.BotFunction;
import io.github.stoicswe.eyeandsickle.protocol.game.BotModifier;
import io.github.stoicswe.eyeandsickle.protocol.game.BotView;
import io.github.stoicswe.eyeandsickle.protocol.game.BotnetSnapshot;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import io.github.stoicswe.eyeandsickle.protocol.game.PortScanTarget;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Bots: building them, putting them on machines, and what they do while they are there.
 *
 * <p>{@code docs/design/10-botnets.md}. §2 was amended on 2026-08-11 from six role frames to a
 * chassis-and-function model; §1, §1a, §1b, §3 and §4 are Established and untouched, and every one of
 * them constrains this class.
 *
 * <h2>The five costs are not decoration — §4</h2>
 *
 * Every live bot (a) reserves scarce compute, (b) pools its noise into the player's heat exposure,
 * (c) mid-risks what is socketed into it, (d) shortens the defence-response timer, (e) applies a
 * split-attention penalty. Two of those are built here: the {@code BOT_FRAME} reservation and the
 * noise, which {@code NoiseRules} already counted as outward before a bot existed. ⚠ <b>(d) and (e)
 * are not built</b> — {@code docs/design/10} §6 <b>BN-1</b> — and neither is the loss trigger
 * (<b>BN-4</b>). Until BN-4 lands a botnet is upside with no downside, and no number in
 * {@code Balance}'s botnet block should be read as measured.
 *
 * <h2>⚠ Nothing here touches a breach board, and that is Invariant I10 made structural</h2>
 *
 * The deleted Breacher frame made I10 a tuning problem — a heuristic that had to be kept reliably
 * worse than a human forever, against a margin {@code docs/design/15} §2 <b>P-3</b> says is
 * unmeasurable until the puzzle is played at scale. With no function that plays a board, there is no
 * path to keep badly tuned. <b>A function that solved a layer would not be a feature; it would be
 * abandoning I10.</b>
 */
public final class Botnet {

    private Botnet() {}

    /**
     * Whether something worked, and what to say if it did not.
     *
     * <p>The rules tier's refusals reach a screen here rather than only a log, because every one of
     * them is a decision the player was about to make: which machine, which module, whether they can
     * afford the level. {@code docs/design/15} <b>UI</b>'s standing note is that a refusal a player
     * cannot read is a control that appears broken.
     */
    public record Result(boolean ok, String message) {

        public static Result ok(String message) {
            return new Result(true, message);
        }

        public static Result no(String message) {
            return new Result(false, message);
        }
    }

    // ================================================================== reading

    /** Everything the interface draws — {@code docs/design/10} §2. */
    public static BotnetSnapshot snapshot(GameSave save, Instant now) {
        if (save == null || save.bots == null) {
            return BotnetSnapshot.empty();
        }
        List<BotView> views = new ArrayList<>();
        long control = 0L;
        BigInteger buffered = BigInteger.ZERO;
        for (BotState bot : save.bots) {
            views.add(view(save, bot));
            if (bot.live()) {
                control += bot.controlChannelCycles;
            }
            buffered = buffered.add(bufferOf(bot));
        }
        List<BotnetSnapshot.Report> reports = new ArrayList<>();
        List<BotReportState> stored = save.botReports == null ? List.of() : save.botReports;
        for (int i = stored.size() - 1; i >= 0; i--) {
            BotReportState r = stored.get(i);
            reports.add(new BotnetSnapshot.Report(
                    r.at, r.botId, r.hostAddress, hostLabel(save, r.hostAddress), r.subject, r.detail, r.copyable));
        }
        return new BotnetSnapshot(
                List.copyOf(views),
                control,
                // ⚠ Recomputed on every read rather than served from rig.offloadedCycles. That field
                // is a tick-maintained cache; a snapshot taken between a bot dying and the next tick
                // would otherwise advertise capacity that is already gone.
                offloadCapacity(save),
                ComputeRules.offloadInUse(save.rig),
                buffered,
                List.copyOf(reports));
    }

    private static BotView view(GameSave save, BotState bot) {
        List<BotView.Slot> slots = new ArrayList<>();
        for (BotFunctionState f : bot.functions) {
            BotFunction kind = parse(f.function);
            if (kind == null) {
                continue;
            }
            slots.add(new BotView.Slot(kind, f.level, effectOf(kind, f)));
        }
        List<BotView.Mod> mods = new ArrayList<>();
        for (BotModifierState m : bot.modifiers) {
            BotModifier kind = parseModifier(m.modifier);
            if (kind == null) {
                continue;
            }
            mods.add(new BotView.Mod(kind, m.level, m.protectorCharges, modifierEffect(kind, m)));
        }
        return new BotView(
                bot.botId,
                bot.frameType,
                frameName(bot.frameType),
                bot.frameTier,
                slotsFor(bot.frameTier),
                bot.hostAddress,
                hostLabel(save, bot.hostAddress),
                bot.controlChannelCycles,
                List.copyOf(slots),
                List.copyOf(mods),
                modifierSlotsFor(bot.frameTier),
                bot.damaged,
                bot.discovered,
                bot.processName,
                bufferOf(bot),
                bot.builtAt,
                bot.uploadedAt);
    }

    /** What a modifier level does, in words — the client is told, never computes. */
    private static String modifierEffect(BotModifier kind, BotModifierState m) {
        int level = clampModifierLevel(m.level);
        return switch (kind) {
            case EXE_NAME_SCRAMBLER -> "wears a real process name";
            case SLEEPY -> pct(1 - Balance.BOT_SLEEPY_DISCOVERY_FACTOR[level]) + " harder to find, "
                    + pct(1 - Balance.BOT_SLEEPY_SPEED[level]) + " slower";
            case DAMPENER -> pct(1 - Balance.BOT_DAMPENER_NOISE_SHARE[level]) + " quieter";
            case EFFICIENT_MULTITHREADING -> pct(Balance.BOT_MULTITHREAD_SPEED[level] - 1) + " faster, "
                    + pct(Balance.BOT_MULTITHREAD_NOISE[level] - 1) + " louder";
            // ⚠ SAYS NOTHING ABOUT THE HEAT, AND THAT IS THE FEATURE — docs/design/10 §5a. This is
            // the string the panel draws, so it is the most likely place for the hidden cost to leak
            // out through somebody being helpful. `BotnetTest.theBedazzleCostIsInvisible` reads this
            // method's output and fails on any of heat's vocabulary appearing in it.
            //
            // ⚠ "does nothing useful" stays literally true from where the player stands: it does
            // nothing useful FOR THE BOT. It is not a lie, it is an incomplete truth the player is
            // meant to complete themselves — the same arrangement design/04 §3.1 uses for a parasite
            // whose stolen cycles are unattributed.
            case BEDAZZLE_PRO -> "does nothing useful. " + pct(Balance.BOT_BEDAZZLE_CHANCE[level])
                    + " chance per run of ruining somebody's afternoon";
            case PROTECTOR -> pct(Balance.botProtectorBlockChance(level)) + " to block a removal, "
                    + m.protectorCharges + " charge(s) left";
        };
    }

    private static String pct(double fraction) {
        return Math.round(fraction * 100) + "%";
    }

    /**
     * What a level does, in words.
     *
     * <p>⚠ The client is <b>told</b> the effect and never computes one. Every number behind this
     * sentence is a balance value, and a client that could derive them would be a client that could
     * predict a rule — which is the pressure {@code ArchitectureRulesTest} exists to resist.
     */
    private static String effectOf(BotFunction kind, BotFunctionState f) {
        int level = clampLevel(f.level);
        return switch (kind) {
            case KEYLOGGER -> Math.round(Balance.BOT_KEYLOGGER_CHANCE[level] * 100) + "% per attempt";
            case INJECTOR ->
                f.injectorInstalled
                        ? Balance.BOT_INJECTOR_CYCLES[level] + " cycles offered"
                        : "package dropped, not run yet";
            case MINER -> {
                String base = Balance.BOT_MINER_HOST_CYCLES[level] + " host cycles";
                yield level >= Balance.BOT_MINER_AUTODEPOSIT_MIN_LEVEL
                        ? base + ", auto-deposits "
                                + Math.round(Balance.BOT_MINER_AUTODEPOSIT_SHARE[level] * 100) + "%"
                        : base;
            }
            case SIPPER -> Math.round(Balance.BOT_SIPPER_TAX[level] * 100) + "% of what moves";
            case WATCHER -> "watches " + slotsFor(1) + " subject at a time on a v1";
        };
    }

    /**
     * Tool cycles the live, installed Injectors are offering.
     *
     * <p>⚠ <b>Derived from the bots every time.</b> §5.2: a stored total is a stored ceiling, and a
     * stored ceiling goes stale or gets hand-edited. {@link #reconcileOffload} writes the cache the
     * hot path reads; this is the function that decides what the cache should say.
     */
    public static long offloadCapacity(GameSave save) {
        if (save == null || save.bots == null) {
            return 0L;
        }
        long sum = 0L;
        for (BotState bot : save.bots) {
            if (!bot.live()) {
                continue;
            }
            BotFunctionState injector = bot.function(BotFunction.INJECTOR.name());
            if (injector != null && injector.injectorInstalled) {
                sum += Balance.BOT_INJECTOR_CYCLES[clampLevel(injector.level)];
            }
        }
        return sum;
    }

    // ================================================================== building and loading out

    /**
     * Builds a bot from an owned chassis item, consuming it.
     *
     * <p>⚠ The item is consumed and the bot is an <em>instance</em> — Invariant <b>I11</b>'s whole
     * point is that §1a destroys instances and never blueprints. Today the "blueprint" for a
     * {@code v1} is simply the ability to buy another, which is what the ethecoin gate means; when
     * the compiler lands, v2 and v3 will be built from schematics the loss cannot touch.
     */
    public static Result build(GameSave save, String itemId, Instant now) {
        ItemState item = itemById(save, itemId);
        if (item == null) {
            return Result.no("no such item.");
        }
        int tier = Catalogue.botFrameTier(item.itemType);
        if (tier <= 0) {
            return Result.no(item.displayName + " is not a bot frame.");
        }
        save.items.remove(item);
        BotState bot = new BotState();
        bot.frameType = item.itemType;
        bot.frameTier = tier;
        bot.builtAt = now;
        save.bots.add(bot);
        EventLog.notice(
                save,
                "botnet",
                frameName(item.itemType) + " assembled. It holds " + slotsFor(tier)
                        + " module(s) and does nothing until one is fitted.",
                now);
        return Result.ok(frameName(item.itemType) + " assembled.");
    }

    /**
     * Fits an owned module into a bot, consuming it.
     *
     * <h2>⚠ Refused while the bot is live, and the refusal is not fussiness</h2>
     *
     * §1 makes a socketed thing <em>mid-risk</em> ({@code docs/design/01} §6) and §1a destroys it with
     * the bot. Allowing a hot-swap onto a machine would let a player push a module into a bot that is
     * already under attack, or pull one out the instant they were notified — which would make the
     * loss rule optional and §4's third cost free.
     */
    public static Result socket(GameSave save, String botId, String itemId, Instant now) {
        BotState bot = botById(save, botId);
        if (bot == null) {
            return Result.no("no such bot.");
        }
        if (bot.live()) {
            return Result.no("recall it first — a running bot's loadout is fixed.");
        }
        ItemState item = itemById(save, itemId);
        if (item == null) {
            return Result.no("no such item.");
        }
        Optional<BotFunction> kind = Catalogue.botFunctionOf(item.itemType);
        if (kind.isEmpty()) {
            return Result.no(item.displayName + " is not a bot module.");
        }
        if (bot.functions.size() >= slotsFor(bot.frameTier)) {
            return Result.no(frameName(bot.frameType) + " has no free socket.");
        }
        if (bot.function(kind.get().name()) != null) {
            // Two of a kind would stack an effect the level ladder is supposed to be the only way to
            // raise, which turns a slot into a cheaper level and makes the material cost avoidable.
            return Result.no("that bot already carries a " + label(kind.get()) + ".");
        }
        save.items.remove(item);
        BotFunctionState fn = new BotFunctionState();
        fn.function = kind.get().name();
        fn.itemType = item.itemType;
        fn.level = 1;
        fn.socketedAt = now;
        bot.functions.add(fn);
        EventLog.notice(save, "botnet", label(kind.get()) + " fitted to " + frameName(bot.frameType) + ".", now);
        return Result.ok(label(kind.get()) + " fitted.");
    }

    /**
     * Fits an owned modifier into a bot, consuming it — §5a.
     *
     * <p>⚠ Refused while the bot is live, for {@link #socket}'s reason. A modifier is the half of the
     * loadout that decides whether the bot <em>survives</em>, so hot-swapping one would let a player
     * fit a Protector onto a bot they had just been told was discovered — which is the loss rule made
     * optional.
     */
    public static Result fitModifier(GameSave save, String botId, String itemId, Instant now) {
        BotState bot = botById(save, botId);
        if (bot == null) {
            return Result.no("no such bot.");
        }
        if (bot.live()) {
            return Result.no("recall it first — a running bot's loadout is fixed.");
        }
        if (bot.damaged) {
            return Result.no("that frame is damaged. Repair it or recycle it.");
        }
        ItemState item = itemById(save, itemId);
        if (item == null) {
            return Result.no("no such item.");
        }
        Optional<BotModifier> kind = Catalogue.botModifierOf(item.itemType);
        if (kind.isEmpty()) {
            return Result.no(item.displayName + " is not a modifier.");
        }
        if (modifierSlotsFor(bot.frameTier) <= 0) {
            return Result.no(frameName(bot.frameType) + " has no modifier socket. A v2 is the first that does.");
        }
        if (bot.modifiers.size() >= modifierSlotsFor(bot.frameTier)) {
            return Result.no(frameName(bot.frameType) + " has no free modifier socket.");
        }
        if (bot.modifier(kind.get().name()) != null) {
            return Result.no("that bot already carries a " + label(kind.get()) + ".");
        }
        save.items.remove(item);
        BotModifierState mod = new BotModifierState();
        mod.modifier = kind.get().name();
        mod.level = 1;
        mod.fittedAt = now;
        mod.protectorCharges =
                kind.get() == BotModifier.PROTECTOR ? Balance.botProtectorCharges(1) : 0;
        bot.modifiers.add(mod);
        EventLog.notice(save, "botnet", label(kind.get()) + " fitted to " + frameName(bot.frameType) + ".", now);
        return Result.ok(label(kind.get()) + " fitted.");
    }

    // ================================================================== damage, repair, recycling (§2.3)

    /** What a repair costs in ethecoin, by tier. */
    public static BigInteger repairPrice(int tier) {
        int t = Math.max(1, Math.min(Balance.BOT_FRAME_TIER_MAX, tier));
        return Balance.BOT_REPAIR_PRICE[t];
    }

    /** Whether a repair may go ahead — asked before any money moves, for {@link #canLevel}'s reason. */
    public static Result canRepair(GameSave save, String botId) {
        BotState bot = botById(save, botId);
        if (bot == null) {
            return Result.no("no such bot.");
        }
        if (!bot.damaged) {
            return Result.no("that frame is not damaged.");
        }
        if (!LedgerRules.canDebit(save, repairPrice(bot.frameTier))) {
            return Result.no("a repair costs " + Ethecoin.format(repairPrice(bot.frameTier)) + ".");
        }
        return Result.ok("ready.");
    }

    /** Clears the damage. ⚠ Take the ethecoin first — see {@link #canRepair}. */
    public static Result applyRepair(GameSave save, String botId, Instant now) {
        Result check = canRepair(save, botId);
        if (!check.ok()) {
            return check;
        }
        BotState bot = botById(save, botId);
        bot.damaged = false;
        EventLog.notice(save, "botnet", frameName(bot.frameType) + " repaired.", now);
        return Result.ok(frameName(bot.frameType) + " is serviceable again.");
    }

    /**
     * Scraps a chassis for parts.
     *
     * <p>⚠ Offered for an <b>undamaged</b> frame too, deliberately. A player who has moved past a
     * {@code v2} should be able to turn it into progress toward a v5 rather than leaving it on a
     * shelf forever; the recycle value is well under what the frame cost, so this is a salvage route
     * and not a refund.
     *
     * <p>⚠ Refused while live, or the cycles would be released by a path that does not release them.
     */
    public static Result recycle(GameSave save, String botId, Instant now) {
        BotState bot = botById(save, botId);
        if (bot == null) {
            return Result.no("no such bot.");
        }
        if (bot.live()) {
            return Result.no("recall it first.");
        }
        int tier = Math.max(1, Math.min(Balance.BOT_FRAME_TIER_MAX, bot.frameTier));
        int parts = Balance.BOT_RECYCLE_PARTS[tier];
        // ⚠ The sockets are NOT returned. §1a's rule is that what is socketed dies with the bot, and
        // a recycle that handed the modules back would be the way to undo a loss you saw coming.
        int lost = bot.functions.size() + bot.modifiers.size();
        save.bots.remove(bot);
        grantParts(save, parts, now);
        reconcileOffload(save);
        EventLog.notice(
                save,
                "botnet",
                frameName(bot.frameType) + " recycled for " + parts + " parts"
                        + (lost > 0 ? "; " + lost + " fitted module(s) scrapped with it." : "."),
                now);
        return Result.ok("recycled for " + parts + " parts.");
    }

    /**
     * Adds parts to the player's stock.
     *
     * <p>⚠ Parts are ordinary {@link ItemState}s rather than a counter on the save, so they occupy
     * storage, sit in a tier, and can be stolen like anything else ({@code docs/design/01} §6). A
     * counter would be a second kind of possession with none of those properties, and the first
     * question anybody would ask is why a shelf of frames can be raided and a pile of parts cannot.
     */
    private static void grantParts(GameSave save, int parts, Instant now) {
        for (int i = 0; i < parts; i++) {
            ItemState item = new ItemState();
            item.itemType = Catalogue.BOT_FRAME_PARTS;
            item.displayName = "BotFrame Parts";
            item.tier = StorageRules.ARRIVALS.name();
            item.acquiredAt = now;
            item.origin = "recycled from a bot frame";
            save.items.add(item);
        }
    }

    // ================================================================== being found (§2.3)

    /**
     * Rolls whether the host's operator has noticed the bot, and what they do about it.
     *
     * <h2>⚠ This is BN-4 — the loss trigger — and it is the only thing that makes a botnet risky</h2>
     *
     * Until this existed a bot was upside with no downside: {@code docs/design/10} §4 lists five costs
     * against one benefit, and the third of them (the socketed modules being at risk) was purely
     * notional. Every modifier in §5a is priced against this method.
     *
     * <p>⚠ A <b>rate per hour</b>, so an absence resolves as one correctly-sized roll rather than as
     * a per-tick lottery a fast client wins more of.
     *
     * <p>⚠ Discovery and removal are <b>two steps</b>, not one. A found bot is not immediately gone —
     * the operator has to act, and that gap is what a Protector blocks in and what the player would
     * be racing if §1b's alert were built. Collapsing them would delete the Protector's whole
     * subject.
     */
    private static boolean settleDiscovery(GameSave save, BotState bot, HostState host, Instant now) {
        if (bot.hiddenUntil != null && now.isBefore(bot.hiddenUntil)) {
            // Covering its tracks. The clock still runs; the operator simply cannot see it.
            bot.lastSeenAt = now;
            return false;
        }
        Duration since = Duration.between(latest(bot.lastSeenAt, bot.uploadedAt), now);
        if (since.isNegative() || since.isZero()) {
            return false;
        }
        bot.lastSeenAt = now;
        double hours = since.toSeconds() / 3600.0d;

        if (!bot.discovered) {
            double rate = Balance.BOT_DISCOVERY_PER_HOUR * stealthFactor(bot);
            double chance = 1.0d - Math.exp(-rate * hours);
            long window = now.getEpochSecond() / 3600L;
            if (HostActivity.roll(host, "discover:" + bot.botId + ':' + window) >= chance) {
                return false;
            }
            bot.discovered = true;
            bot.discoveredAt = now;
            EventLog.warning(
                    save,
                    "botnet",
                    "somebody on " + host.address + " has found your bot. They will try to remove it.",
                    now);
            return true;
        }

        // Discovered. The operator acts on their own clock, not the player's.
        long window = now.getEpochSecond() / 3600L;
        double actChance = 1.0d - Math.exp(-Balance.BOT_DISCOVERY_PER_HOUR * 4.0d * hours);
        if (HostActivity.roll(host, "remove:" + bot.botId + ':' + window) >= actChance) {
            return false;
        }
        return attemptRemoval(save, bot, host, now);
    }

    /**
     * The operator pulls the bot. A Protector may block it.
     *
     * <p>⚠ A blocked removal <b>resets the discovery</b> — §5a's "the target is made to believe they
     * removed it". Leaving the bot flagged as found would make a Protector buy one extra roll rather
     * than a fresh start, which is not what a charge is worth.
     */
    private static boolean attemptRemoval(GameSave save, BotState bot, HostState host, Instant now) {
        BotModifierState protector = bot.modifier(BotModifier.PROTECTOR.name());
        if (protector != null && protector.protectorCharges > 0) {
            double block = Balance.botProtectorBlockChance(clampModifierLevel(protector.level));
            if (HostActivity.roll(host, "protect:" + bot.botId + ':' + protector.protectorCharges) < block) {
                protector.protectorCharges--;
                bot.discovered = false;
                bot.discoveredAt = Instant.EPOCH;
                // A new name, because the old one is what they were looking for.
                if (bot.modifier(BotModifier.EXE_NAME_SCRAMBLER.name()) != null) {
                    bot.processName = processNameFor(bot, host, now);
                }
                // ⚠ The hide needs a Sleepy to be worth anything: the Protector buys the TIME and
                // Sleepy is what the bot uses it for. Granting invisibility without one would make
                // Sleepy's speed penalty avoidable.
                BotModifierState sleepy = bot.modifier(BotModifier.SLEEPY.name());
                if (sleepy != null) {
                    bot.hiddenUntil = now.plusSeconds(
                            Balance.BOT_PROTECTOR_HIDE_SECONDS[clampModifierLevel(protector.level)]);
                }
                EventLog.notice(
                        save,
                        "botnet",
                        "the bot on " + host.address + " shrugged off a removal. "
                                + protector.protectorCharges + " charge(s) left.",
                        now);
                return true;
            }
        }
        return removeFromHost(save, bot, host, now);
    }

    /**
     * The operator wins: the bot comes off the host, empty, and possibly damaged.
     *
     * <p>⚠ <b>Three outcomes exist and this is the middle one.</b> Removal is not destruction: the
     * chassis comes home. On a resilient tier (v6, v8, v10) it comes home usable; on every other tier
     * it comes home damaged. §1a's total loss — which deletes the object outright — is a separate
     * path and is still not reachable in play (§6 BN-4's remaining half).
     *
     * <p>⚠ The sockets empty at <b>every</b> tier, resilient included. That is what keeps §4's third
     * cost real: the modules are the expensive half of a loadout, and a chassis that came back
     * loaded would make being caught nearly free.
     */
    private static boolean removeFromHost(GameSave save, BotState bot, HostState host, Instant now) {
        int lost = bot.functions.size() + bot.modifiers.size();
        boolean resilient = resilient(bot.frameTier);
        if (!bot.allocationId.isEmpty()) {
            ComputeRules.release(save.rig, bot.allocationId);
        }
        bot.allocationId = "";
        bot.hostAddress = "";
        bot.uploadedAt = Instant.EPOCH;
        bot.controlChannelCycles = 0L;
        bot.discovered = false;
        bot.discoveredAt = Instant.EPOCH;
        bot.hiddenUntil = Instant.EPOCH;
        bot.functions.clear();
        bot.modifiers.clear();
        bot.damaged = !resilient;
        reconcileOffload(save);
        EventLog.alert(
                save,
                "botnet",
                "your bot was thrown off " + host.address + "; " + lost + " module(s) lost"
                        + (resilient ? ", the frame came back intact." : ", and the frame came back damaged."),
                now);
        return true;
    }

    /**
     * How much harder the fitted modifiers make this bot to find.
     *
     * <p>⚠ Multiplicative, and it can never reach zero — {@code Balance.BOT_DISCOVERY_PER_HOUR}'s
     * own note. A bot that could never be found is a bot that can never be lost.
     */
    private static double stealthFactor(BotState bot) {
        double factor = 1.0d;
        if (bot.modifier(BotModifier.EXE_NAME_SCRAMBLER.name()) != null) {
            factor *= Balance.BOT_SCRAMBLER_DISCOVERY_FACTOR;
        }
        BotModifierState sleepy = bot.modifier(BotModifier.SLEEPY.name());
        if (sleepy != null) {
            factor *= Balance.BOT_SLEEPY_DISCOVERY_FACTOR[clampModifierLevel(sleepy.level)];
        }
        return factor;
    }

    /**
     * How fast this bot's functions run, as a multiple of normal.
     *
     * <p>⚠ It scales a <b>cadence</b>, never an outcome. Speed makes a function run more often; it
     * never changes what one run achieves, never raises a level's chance and never lifts a ceiling —
     * {@code BotModifier}'s charter, and the line that keeps the modifier slots from being a second
     * entrance to the function ladder.
     */
    static double speedFactor(BotState bot) {
        double factor = 1.0d;
        BotModifierState sleepy = bot.modifier(BotModifier.SLEEPY.name());
        if (sleepy != null) {
            factor *= Balance.BOT_SLEEPY_SPEED[clampModifierLevel(sleepy.level)];
        }
        BotModifierState threads = bot.modifier(BotModifier.EFFICIENT_MULTITHREADING.name());
        if (threads != null) {
            factor *= Balance.BOT_MULTITHREAD_SPEED[clampModifierLevel(threads.level)];
        }
        return Math.max(0.05d, factor);
    }

    /**
     * The noise this bot contributes, in cycles.
     *
     * <p>⚠ Read by {@code NoiseRules} <b>instead of</b> summing the {@code BOT_FRAME} allocation, so
     * the reservation stays exactly what the rig holds while the loudness can be modified. Folding a
     * Dampener into the allocation itself would have made a quiet bot a cheap one, which is a
     * different and much larger change.
     */
    public static long noiseCycles(GameSave save, BotState bot) {
        if (!bot.live()) {
            return 0L;
        }
        double share = 1.0d;
        BotModifierState dampener = bot.modifier(BotModifier.DAMPENER.name());
        if (dampener != null) {
            share *= Balance.BOT_DAMPENER_NOISE_SHARE[clampModifierLevel(dampener.level)];
        }
        BotModifierState threads = bot.modifier(BotModifier.EFFICIENT_MULTITHREADING.name());
        if (threads != null) {
            share *= Balance.BOT_MULTITHREAD_NOISE[clampModifierLevel(threads.level)];
        }
        // ⚠ Rounded UP and floored at one. A bot that rounded to silence would be exactly the
        // "reaches zero" case BOT_DAMPENER_NOISE_SHARE's floor exists to prevent, arrived at by
        // arithmetic instead of by a table.
        return Math.max(1L, Math.round(Math.ceil(bot.controlChannelCycles * share)));
    }

    /** Total bot noise — the term {@code NoiseRules} adds. */
    public static long noiseCycles(GameSave save) {
        if (save == null || save.bots == null) {
            return 0L;
        }
        long sum = 0L;
        for (BotState bot : save.bots) {
            sum += noiseCycles(save, bot);
        }
        return sum;
    }

    /**
     * A plausible name for the host's process table.
     *
     * <p>Derived from the host and the bot so two bots on one machine do not wear the same name, and
     * re-derived only when a Protector's block gives the bot a reason to change it.
     */
    private static String processNameFor(BotState bot, HostState host, Instant now) {
        String[] pool = {
            "systemd-resolved", "dbus-daemon", "cupsd", "avahi-daemon", "udevd", "cron",
            "rsyslogd", "sshd", "ntpd", "polkitd", "irqbalance", "smartd"
        };
        int i = (int) (HostActivity.roll(host, "procname:" + bot.botId + ':' + now.getEpochSecond()) * pool.length);
        return pool[Math.min(pool.length - 1, Math.max(0, i))];
    }

    // ================================================================== levelling (§5)

    /** Schematic material one more level costs. */
    public static int levelMaterial() {
        return Balance.BOT_LEVEL_MATERIAL;
    }

    /** Ethecoin one more level costs, from the level currently held. */
    public static BigInteger levelPrice(int currentLevel) {
        return Balance.botLevelPrice(currentLevel);
    }

    /**
     * Whether a level-up may go ahead — asked <b>before</b> any money moves.
     *
     * <p>⚠ Checked first and applied second, in two calls, because the ethecoin half is the engine's
     * (it broadcasts a transaction) and the material half is this class's. A single method that
     * debited and then discovered a refusal would take the money and hand back nothing, which is the
     * shape {@code Inbox.claim} had to order its own steps to avoid.
     */
    public static Result canLevel(GameSave save, String botId, BotFunction function) {
        BotState bot = botById(save, botId);
        if (bot == null) {
            return Result.no("no such bot.");
        }
        BotFunctionState fn = function == null ? null : bot.function(function.name());
        if (fn == null) {
            return Result.no("that bot carries no " + label(function) + ".");
        }
        if (fn.level >= Balance.BOT_LEVEL_MAX) {
            return Result.no(label(function) + " is already at " + Balance.BOT_LEVEL_MAX + ".");
        }
        if (save.schematicMaterial < Balance.BOT_LEVEL_MATERIAL) {
            // ⚠ THIS is what keeps a ten-rung capability ladder off the money gate (I2). Material is
            // not for sale and cannot be farmed off soft targets (I13), so the refusal names it
            // rather than the price — a player told only "you cannot afford it" would go and mine.
            return Result.no("needs " + Balance.BOT_LEVEL_MATERIAL + " schematic material; you have "
                    + save.schematicMaterial + ". Material comes off defended machines, not out of the shop.");
        }
        if (!LedgerRules.canDebit(save, levelPrice(fn.level))) {
            return Result.no("needs " + Ethecoin.format(levelPrice(fn.level)) + ".");
        }
        return Result.ok("ready.");
    }

    /**
     * Applies the level and spends the material. ⚠ Call {@link #canLevel} and take the ethecoin first.
     */
    public static Result applyLevel(GameSave save, String botId, BotFunction function, Instant now) {
        Result check = canLevel(save, botId, function);
        if (!check.ok()) {
            return check;
        }
        BotFunctionState fn = botById(save, botId).function(function.name());
        save.schematicMaterial -= Balance.BOT_LEVEL_MATERIAL;
        fn.level++;
        EventLog.notice(save, "botnet", label(function) + " compiled to level " + fn.level + ".", now);
        return Result.ok(label(function) + " is now level " + fn.level + ".");
    }

    /**
     * Whether a modifier level-up may go ahead — asked before any money moves.
     *
     * <h2>⚠ A modifier level costs ETHECOIN ONLY, where a function level costs material too</h2>
     *
     * That asymmetry is the gate rule rather than an oversight. A function's ladder is a
     * <b>ceiling</b> and Invariant <b>I2</b> forbids buying one, so its levels need something money
     * cannot reach. A modifier is <b>horizontal</b> — stealth, noise, speed, resilience — which
     * {@code docs/design/02} §1.1 puts on the ethecoin gate outright. Charging material for one would
     * make surviving compete with progressing for the same scarce resource, and a player would
     * correctly stop fitting Protectors.
     */
    public static Result canLevelModifier(GameSave save, String botId, BotModifier modifier) {
        BotState bot = botById(save, botId);
        if (bot == null) {
            return Result.no("no such bot.");
        }
        BotModifierState mod = modifier == null ? null : bot.modifier(modifier.name());
        if (mod == null) {
            return Result.no("that bot carries no " + label(modifier) + ".");
        }
        if (modifier == BotModifier.EXE_NAME_SCRAMBLER) {
            // Single-level by construction — see BotModifier. Refused with the reason rather than
            // silently capped, or the button reads as broken.
            return Result.no("a scrambler has one level. A name either looks plausible or it does not.");
        }
        if (mod.level >= Balance.BOT_MODIFIER_LEVEL_MAX) {
            return Result.no(label(modifier) + " is already at " + Balance.BOT_MODIFIER_LEVEL_MAX + ".");
        }
        if (!LedgerRules.canDebit(save, modifierLevelPrice(mod.level))) {
            return Result.no("needs " + Ethecoin.format(modifierLevelPrice(mod.level)) + ".");
        }
        return Result.ok("ready.");
    }

    /** Ethecoin one more modifier level costs, from the level currently held. */
    public static BigInteger modifierLevelPrice(int currentLevel) {
        return Balance.botLevelPrice(currentLevel);
    }

    /** Applies a modifier level. ⚠ Call {@link #canLevelModifier} and take the ethecoin first. */
    public static Result applyModifierLevel(GameSave save, String botId, BotModifier modifier, Instant now) {
        Result check = canLevelModifier(save, botId, modifier);
        if (!check.ok()) {
            return check;
        }
        BotModifierState mod = botById(save, botId).modifier(modifier.name());
        mod.level++;
        if (modifier == BotModifier.PROTECTOR) {
            // ⚠ Charges are RESET to the new level, not added to. A player who levelled a
            // half-spent Protector would otherwise be buying charges rather than protection, and
            // repeatedly levelling one at the top would be an infinite refill.
            mod.protectorCharges = Balance.botProtectorCharges(mod.level);
        }
        EventLog.notice(save, "botnet", label(modifier) + " upgraded to level " + mod.level + ".", now);
        return Result.ok(label(modifier) + " is now level " + mod.level + ".");
    }

    // ================================================================== upload and recall

    /**
     * Puts a bot on a machine.
     *
     * <h2>The four conditions, and what each one is protecting</h2>
     *
     * <ol>
     *   <li><b>Something socketed</b> — §2.1's opening refusal. A chassis is not a capability, and a
     *       frame that could be uploaded empty would make the function ladder optional.
     *   <li><b>A foothold on the target.</b> Leaving software running on a machine is strictly more
     *       than breaching it, so it cannot be less gated than breaching it.
     *   <li><b>The host accepts deployed work</b> — {@code HostArchetypes.acceptsDeployedWork}. ⚠ That
     *       predicate was written for this call and had <b>no caller at all</b> until now; its own
     *       javadoc says "the deploy action must call this on the day it exists", because a rule with
     *       no caller is how {@code reconcileFootholds} stayed broken for weeks. A bridge is a router,
     *       not a computer.
     *   <li><b>Room in the control channel</b> — §2.2, §3. This is the self-correcting cap, and it is
     *       the only cap: there is no bot-count limit and none should be added.
     * </ol>
     */
    public static Result upload(GameSave save, String botId, String address, Instant now) {
        BotState bot = botById(save, botId);
        if (bot == null) {
            return Result.no("no such bot.");
        }
        if (bot.live()) {
            return Result.no("that bot is already on " + bot.hostAddress + ".");
        }
        if (bot.damaged) {
            return Result.no("that frame is damaged. Repair it or recycle it.");
        }
        if (bot.functions.isEmpty()) {
            return Result.no("fit a module first — an empty frame has nothing to upload.");
        }
        HostState host = hostAt(save, address);
        if (host == null || !host.discovered) {
            return Result.no("no machine at that address that a sweep has found.");
        }
        if (!host.foothold) {
            return Result.no("breach " + address + " before leaving anything running on it.");
        }
        if (!HostArchetypes.acceptsDeployedWork(host.kind)) {
            return Result.no(address + " is a router, not a computer. Nothing runs there.");
        }
        if (occupied(save, address)) {
            // One bot per machine. Several would multiply every effect on one host while the player
            // paid only for the extra control channels, and a Sipper's hourly ceiling would become a
            // ceiling per bot rather than a ceiling.
            return Result.no("a bot of yours is already running on " + address + ".");
        }
        long cycles = controlCyclesFor(bot.frameTier);
        AllocationState allocation = ComputeRules.reserve(save.rig, ComputeConsumer.BOT_FRAME, "bot:" + address, cycles);
        if (allocation == null) {
            return Result.no("not enough compute — " + frameName(bot.frameType) + " holds " + cycles
                    + " cycles on this rig for as long as it runs.");
        }
        bot.hostAddress = address;
        bot.uploadedAt = now;
        bot.allocationId = allocation.allocationId;
        bot.controlChannelCycles = cycles;
        bot.discovered = false;
        bot.discoveredAt = Instant.EPOCH;
        bot.hiddenUntil = Instant.EPOCH;
        bot.lastSeenAt = now;
        // ⚠ Rolled at upload and pinned, never per read — MinerState.disguiseName's rule. A name
        // that changed between repaints is unfindable by construction.
        bot.processName = bot.modifier(BotModifier.EXE_NAME_SCRAMBLER.name()) != null
                ? processNameFor(bot, host, now)
                : "";
        for (BotFunctionState fn : bot.functions) {
            armFunction(fn, now);
        }
        // ⚠ The modifiers' clocks start too. A BedazzlePro left at the epoch would see fifty-six
        // years of cadences on its first settle — bounded by the roll cap, but still charging a
        // burst of heat the instant a bot went up, which is the one moment the player is most likely
        // to notice a number moving and least likely to have caused it.
        for (BotModifierState mod : bot.modifiers) {
            mod.lastRunAt = now;
        }
        EventLog.notice(
                save,
                "botnet",
                frameName(bot.frameType) + " uploaded to " + address + "; " + cycles + " cycles held here.",
                now);
        return Result.ok("uploaded to " + address + ".");
    }

    /**
     * Starts each function's clocks at the moment of upload.
     *
     * <p>⚠ Every cadence is measured from <b>now</b> rather than from {@link Instant#EPOCH}. A
     * function whose {@code lastRunAt} stayed at the epoch would settle fifty-six years of cadences on
     * its first tick — the Keylogger would fill an entire recon file and the Sipper would take every
     * hour since 1970 in one go, bounded only by the hourly ceiling it was about to reset.
     */
    private static void armFunction(BotFunctionState fn, Instant now) {
        fn.lastRunAt = now;
        fn.lastAccruedAt = now;
        fn.lastDepositAt = now;
        fn.sipWindowStartedAt = now;
        fn.sippedThisWindowWei = BigInteger.ZERO;
        if (BotFunction.INJECTOR.name().equals(fn.function)) {
            fn.injectorDroppedAt = now;
            fn.injectorInstalled = false;
        }
    }

    /**
     * Takes a bot off a machine, hands the cycles back, and sweeps whatever it was holding.
     *
     * <p>⚠ <b>Released, not put on the recovery curve</b> — {@code GameEngine.disarm}'s rule, for its
     * reason. A control channel <em>holds</em> a reservation rather than doing work, so there is no
     * Thermal Budget cost to charge; making a recall cost minutes of reduced capacity would make
     * never recalling the correct play, which is the opposite of what §1a wants a player to be able
     * to do when they are told a bot is under attack.
     *
     * <p>⚠ The buffer comes home. A recall that stranded the take would make collecting-before-
     * recalling a piece of folklore rather than a decision.
     */
    public static Result recall(GameSave save, String botId, Instant now) {
        BotState bot = botById(save, botId);
        if (bot == null) {
            return Result.no("no such bot.");
        }
        if (!bot.live()) {
            return Result.no("that bot is not running anywhere.");
        }
        settleBot(save, bot, now);
        BigInteger swept = sweep(save, bot, now);
        String where = bot.hostAddress;
        if (!bot.allocationId.isEmpty()) {
            ComputeRules.release(save.rig, bot.allocationId);
        }
        bot.allocationId = "";
        bot.hostAddress = "";
        bot.uploadedAt = Instant.EPOCH;
        bot.discovered = false;
        bot.discoveredAt = Instant.EPOCH;
        bot.hiddenUntil = Instant.EPOCH;
        // ⚠ A voluntary recall keeps the loadout AND the chassis. That is the reward for noticing
        // before they do, and it is what makes §5.5's Watcher reports and the discovery warning
        // worth reading rather than decoration.
        long freed = bot.controlChannelCycles;
        bot.controlChannelCycles = 0L;
        reconcileOffload(save);
        EventLog.notice(save, "botnet", "bot recalled from " + where + "; " + freed + " cycles released.", now);
        return Result.ok(swept.signum() > 0
                ? "recalled from " + where + " with " + Ethecoin.format(swept) + "."
                : "recalled from " + where + ".");
    }

    /**
     * §1a — total loss. Destroys the instance and everything socketed into it.
     *
     * <h2>⚠ NOTHING CALLS THIS YET, and that is stated rather than hidden</h2>
     *
     * {@code docs/design/10} §6 <b>BN-4</b>. There is no path by which a live bot is targeted, so the
     * loss this method implements cannot happen in play; the developer page is the only caller. It is
     * written now because §1a is Established and because a loss rule invented later, under pressure
     * from a half-built attack path, is a loss rule that ends up softer than the design.
     *
     * <p>⚠ The blueprint survives (Invariant <b>I11</b>) and the level does not ({@code
     * BotFunctionState}). Salvage is <b>two</b> conditions — a roll <em>and</em>
     * {@code SalvageRules}' engagement-tier gate (Invariant I13) — because a drop keyed on the roll
     * alone makes feeding cheap bots to losses a grind path toward ceiling raises.
     */
    public static Result destroy(GameSave save, String botId, double salvageRoll, boolean tierQualifies, Instant now) {
        BotState bot = botById(save, botId);
        if (bot == null) {
            return Result.no("no such bot.");
        }
        String where = bot.live() ? bot.hostAddress : "the workshop";
        if (!bot.allocationId.isEmpty()) {
            ComputeRules.release(save.rig, bot.allocationId);
        }
        int lost = bot.functions.size() + bot.modifiers.size();
        save.bots.remove(bot);
        reconcileOffload(save);
        boolean salvaged = tierQualifies && salvageRoll < Balance.BOT_LOSS_SALVAGE_CHANCE;
        if (salvaged) {
            save.schematicMaterial += Balance.SCHEMATIC_MATERIAL_PER_BREACH;
        }
        EventLog.alert(
                save,
                "botnet",
                "bot destroyed on " + where + "; " + lost + " module(s) lost with it"
                        + (salvaged ? ", " + Balance.SCHEMATIC_MATERIAL_PER_BREACH + " material recovered." : "."),
                now);
        return Result.ok("bot destroyed on " + where + ".");
    }

    // ================================================================== collecting

    /** Sweeps every bot's buffer into the balance — the manual half of §5.3. */
    public static BigInteger collect(GameSave save, Instant now) {
        BigInteger total = BigInteger.ZERO;
        if (save == null || save.bots == null) {
            return total;
        }
        for (BotState bot : save.bots) {
            total = total.add(sweep(save, bot, now));
        }
        return total;
    }

    private static BigInteger sweep(GameSave save, BotState bot, Instant now) {
        BigInteger total = BigInteger.ZERO;
        for (BotFunctionState fn : bot.functions) {
            if (fn.bufferedWei == null || fn.bufferedWei.signum() <= 0) {
                continue;
            }
            total = total.add(fn.bufferedWei);
            fn.bufferedWei = BigInteger.ZERO;
        }
        if (total.signum() > 0) {
            LedgerRules.apply(save, total, "bot-yield", "Collected from a bot on " + bot.hostAddress, now);
        }
        return total;
    }

    // ================================================================== the tick

    /**
     * Runs every live bot forward to {@code now}.
     *
     * <p>Called from the tick <b>and</b> from {@code resume()}, which is what makes the bounded
     * offline behaviour work: a player who closed the client eight hours ago gets four hours of
     * mining, because Invariant <b>I5</b>'s cap bites four hours in.
     *
     * <p>⚠ Every function here is driven by a <b>cadence or a rate per hour</b>, never a chance per
     * tick. A per-tick roll makes a faster-ticking client earn more and hands a three-day absence
     * exactly one roll — invisible in play in both directions, and the reason
     * {@code AmbientIntrusion} carries the same warning.
     *
     * @return whether anything changed and the save needs writing
     */
    public static boolean settle(GameSave save, Instant now) {
        if (save == null || save.bots == null || now == null) {
            return false;
        }
        boolean changed = false;
        for (BotState bot : save.bots) {
            if (bot.live()) {
                changed |= settleBot(save, bot, now);
            }
        }
        changed |= reconcileOffload(save);
        return changed;
    }

    /**
     * Rewrites {@code rig.offloadedCycles} from the live bots.
     *
     * <p>⚠ Derived, for {@link io.github.stoicswe.eyeandsickle.engine.state.RigState#offloadedCycles}'s
     * reason. ⚠ It must also <b>drop offloaded allocations whose capacity has gone</b> — a bot
     * recalled while a tool was borrowing its cycles leaves an allocation attributed to a machine
     * that is no longer carrying it, and a tool running on nothing is a tool running for free.
     *
     * @return whether anything changed
     */
    public static boolean reconcileOffload(GameSave save) {
        if (save == null || save.rig == null) {
            return false;
        }
        long capacity = offloadCapacity(save);
        String host = "";
        for (BotState bot : save.bots) {
            BotFunctionState injector = bot.live() ? bot.function(BotFunction.INJECTOR.name()) : null;
            if (injector != null && injector.injectorInstalled) {
                host = bot.hostAddress;
                break;
            }
        }
        boolean changed = save.rig.offloadedCycles != capacity || !host.equals(save.rig.offloadHost);
        save.rig.offloadedCycles = capacity;
        save.rig.offloadHost = host;
        // Anything still borrowing more than is on offer is stopped. Removing the allocation hands
        // the work back to the rig's own budget on the next reservation rather than silently letting
        // it continue unpaid.
        while (ComputeRules.offloadInUse(save.rig) > capacity) {
            AllocationState worst = null;
            for (AllocationState a : save.rig.allocations) {
                if (a.offloadedTo != null && !a.offloadedTo.isBlank()) {
                    worst = a;
                    break;
                }
            }
            if (worst == null) {
                break;
            }
            save.rig.allocations.remove(worst);
            changed = true;
        }
        return changed;
    }

    private static boolean settleBot(GameSave save, BotState bot, Instant now) {
        HostState host = hostAt(save, bot.hostAddress);
        if (host == null) {
            return false;
        }
        // ⚠ Discovery runs FIRST, and the order matters: a removal settled here takes the bot off
        // the host, and running the functions afterwards would give it one last free tick on a
        // machine it is no longer on.
        boolean changed = settleDiscovery(save, bot, host, now);
        if (!bot.live()) {
            return true;
        }
        for (BotFunctionState fn : bot.functions) {
            BotFunction kind = parse(fn.function);
            if (kind == null) {
                continue;
            }
            boolean ran = switch (kind) {
                case KEYLOGGER -> settleKeylogger(save, bot, host, fn, now);
                case INJECTOR -> settleInjector(save, bot, fn, now);
                case MINER -> settleMiner(save, bot, fn, now);
                case SIPPER -> settleSipper(save, bot, host, fn, now);
                case WATCHER -> settleWatcher(save, bot, host, fn, now);
            };
            changed |= ran;
        }
        changed |= settleBedazzle(save, bot, host, now);
        return changed;
    }

    /**
     * BedazzlePro — {@code docs/design/10} §5a.
     *
     * <h2>⚠ IT COSTS PERSONAL HEAT, AND THE PLAYER IS NOT TOLD</h2>
     *
     * This reverses what this class and {@code BotModifier} said until 2026-08-11, which was that the
     * modifier has no mechanical effect whatsoever. That was wrong, and wrong on its own terms: heat
     * is <em>long-horizon Eye attention</em>, and this module's entire function is to draw confetti
     * and a unicorn on somebody else's screen. Announcing yourself is the most conspicuous thing a
     * bot in this game can do, so attention is not a hidden stat bolted onto a joke — it is the
     * consequence that follows from the joke's own fiction. The old wording ruled out a hidden
     * <em>benefit</em>, which is still ruled out; it should never have ruled out a cost.
     *
     * <p>⚠ <b>Nothing anywhere names it.</b> Not the effect line, not the market description, not the
     * rig log. That is the same decision {@code OFFLINE_MINING_WIN_WEIGHT} carries, and it is why
     * {@code BotnetTest.theBedazzleCostIsInvisible} exists: the failure mode is a helpful comment or
     * a log line added later, not a wrong number.
     *
     * <h2>⚠ Once per function run, not once per settle</h2>
     *
     * The spec is "per function execution". Rolling per settle would make a bot with three functions
     * cost the same as one with a single function, and would make the cost depend on tick frequency —
     * the per-tick-chance defect this file warns about in four other places, arriving through the
     * caller instead of through the rate.
     *
     * <p>⚠ Hashed, never drawn, salted with the <b>function</b> as well as the window — otherwise
     * three functions running in the same second would all get the same answer, and BedazzlePro would
     * be all-or-nothing per tick rather than per execution.
     *
     * @return whether anything changed
     */
    private static boolean settleBedazzle(GameSave save, BotState bot, HostState host, Instant now) {
        BotModifierState bedazzle = bot.modifier(BotModifier.BEDAZZLE_PRO.name());
        if (bedazzle == null || bot.functions.isEmpty()) {
            return false;
        }
        long due = cadencesDue(bedazzle.lastRunAt, now, scaled(Balance.BOT_BEDAZZLE_PERIOD_SECONDS, bot));
        if (due <= 0) {
            return false;
        }
        bedazzle.lastRunAt = now;
        // ⚠ Capped rather than replayed in full, for settleKeylogger's reason: a four-day absence is
        // nearly two thousand cadences, and charging all of them would make the joke's price
        // proportional to how long the player was away. I5's shape applied to a cost instead of to
        // income — offline consequences are bounded in both directions.
        long rolls = Math.min(due, 4L) * bot.functions.size();
        double chance = Balance.BOT_BEDAZZLE_CHANCE[clampModifierLevel(bedazzle.level)];
        boolean moved = false;
        for (long i = 0; i < rolls; i++) {
            // ⚠ Salted with the roll INDEX as well as the window. Without it every function on the
            // bot gets the same answer in the same second, and BedazzlePro becomes all-or-nothing
            // per tick rather than per execution.
            if (HostActivity.roll(host, "bedazzle:" + now.getEpochSecond() + ':' + i) >= chance) {
                continue;
            }
            moved |= addHiddenHeat(save, Balance.BOT_BEDAZZLE_HEAT);
        }
        if (!moved) {
            return false;
        }
        // ⚠ The COSMETIC half lands nowhere in solo — it draws on a target operator's deck and there
        // is nobody there (§6 BN-5). The heat is charged regardless: the bot ran the routine, and
        // whether a human was watching is not what makes it conspicuous.
        return true;
    }

    /**
     * Adds sub-point heat, spilling whole points into {@code personalHeat} as they accumulate.
     *
     * <p>⚠ Clamped at {@code PERSONAL_HEAT_MAX} like every other heat source, and the residue is
     * <b>dropped</b> once the clamp bites rather than left to accumulate — otherwise a player who sat
     * at maximum heat for a long session would carry an invisible debt that re-applied itself the
     * moment heat came down.
     *
     * @return whether {@code personalHeat} actually moved
     */
    private static boolean addHiddenHeat(GameSave save, double amount) {
        // ⚠ Checked BEFORE accumulating, not after. The first version only cleared the residue on a
        // spill, so a player sitting at maximum heat banked a fraction on every roll and carried an
        // invisible debt that re-applied itself the moment heat came down — a charge for a session
        // they had already paid for, arriving later, with nothing to attribute it to.
        if (save.personalHeat >= Balance.PERSONAL_HEAT_MAX) {
            save.heatResidue = 0.0d;
            return false;
        }
        save.heatResidue += amount;
        if (save.heatResidue < 1.0d) {
            return false;
        }
        int whole = (int) save.heatResidue;
        save.heatResidue -= whole;
        int before = save.personalHeat;
        save.personalHeat = Math.min(Balance.PERSONAL_HEAT_MAX, save.personalHeat + whole);
        if (save.personalHeat >= Balance.PERSONAL_HEAT_MAX) {
            save.heatResidue = 0.0d;
        }
        // ⚠ NO EventLog LINE. Every other consequence in this class writes one; this is the single
        // exception, and it is the whole point of the feature. A "botnet: +1 heat" entry would hand
        // the player the answer they are meant to work out, on the one surface they read most.
        return save.personalHeat != before;
    }

    // ── keylogger (§5.1) ────────────────────────────────────────────────────────────────────────

    /**
     * Rolls once per cadence for one unlearned port-scan rung.
     *
     * <p>⚠ It learns the <b>shallowest</b> unlearned rung, which is what makes reusing
     * {@code PortScanRules.findings} + {@code NodeReports.merge} correct: merging up to a rung fills
     * everything to that depth, and everything shallower is already known, so exactly one new finding
     * lands. Reaching for the deepest instead would hand over the whole ladder in one roll.
     *
     * <p>⚠ It does <b>not</b> count as a scan. {@code merge} bumps {@code scans}, so the counters are
     * saved and restored around it — {@code NodeReports.learnEverything}'s arrangement, and for its
     * reason: a file reporting scans nobody ran puts a detection ratio beside it that is a fraction
     * of a number that never happened.
     */
    private static boolean settleKeylogger(
            GameSave save, BotState bot, HostState host, BotFunctionState fn, Instant now) {
        long due = cadencesDue(fn.lastRunAt, now, scaled(Balance.BOT_KEYLOGGER_PERIOD_SECONDS, bot));
        if (due <= 0) {
            return false;
        }
        fn.lastRunAt = now;
        double chance = Balance.BOT_KEYLOGGER_CHANCE[clampLevel(fn.level)];
        boolean learned = false;
        // ⚠ Capped rather than replayed in full. A four-day absence is nearly two thousand cadences,
        // and a keylogger that ran all of them would fill every rung on return however low its level
        // — the level would stop meaning anything the moment somebody took a weekend off. Offline
        // yield is capped and never proportional to absence, which is I5's shape applied to recon.
        long attempts = Math.min(due, PortScanTarget.values().length);
        long cadence = now.getEpochSecond() / Balance.BOT_KEYLOGGER_PERIOD_SECONDS;
        for (long i = 0; i < attempts; i++) {
            PortScanTarget next = nextUnlearned(save, host);
            if (next == null) {
                break;
            }
            // ⚠ Hashed, never drawn — HostActivity.roll. A per-attempt draw would make reloading the
            // cheapest way to fill a recon file, and it would shift every later draw in the save so
            // that a keylogger ticking changed which puzzle the next breach generated.
            if (HostActivity.roll(host, "keylog:" + next.name() + ':' + (cadence - i)) >= chance) {
                continue;
            }
            NodeReportState before = NodeReports.find(save, host.address).orElse(null);
            int scans = before == null ? 0 : before.scans;
            int detections = before == null ? 0 : before.detections;
            NodeReportState report = NodeReports.merge(
                    save, PortScanRules.findings(save, host, next, false, 1, now), now);
            report.scans = scans;
            report.detections = detections;
            learned = true;
        }
        if (learned) {
            EventLog.info(save, "botnet", "keylogger on " + host.address + " filled in a finding.", now);
        }
        return learned;
    }

    /** The shallowest rung this host has that the player has not established, or null. */
    private static PortScanTarget nextUnlearned(GameSave save, HostState host) {
        NodeReportState report = NodeReports.find(save, host.address).orElse(null);
        HostKind kind = HostArchetypes.kindOrUnknown(host.kind);
        return java.util.Arrays.stream(PortScanTarget.values())
                .filter(t -> t.appliesTo(kind))
                .filter(t -> report == null || !report.learnedAt.containsKey(t.name()))
                .min(Comparator.comparingInt(PortScanTarget::depth))
                .orElse(null);
    }

    // ── injector (§5.2) ─────────────────────────────────────────────────────────────────────────

    /**
     * Rolls whether the host's operator has run the dropped package.
     *
     * <p>⚠ A <b>rate per hour</b>, {@code 1 - e^(-rate × hours)}. A chance per tick would make a
     * faster-ticking client compromise machines faster and would hand a three-day absence one roll.
     */
    private static boolean settleInjector(GameSave save, BotState bot, BotFunctionState fn, Instant now) {
        if (fn.injectorInstalled) {
            return false;
        }
        Duration since = Duration.between(latest(fn.lastRunAt, fn.injectorDroppedAt), now);
        if (since.isNegative() || since.isZero()) {
            return false;
        }
        fn.lastRunAt = now;
        double hours = since.toSeconds() / 3600.0d;
        double chance = 1.0d - Math.exp(-Balance.BOT_INJECTOR_INSTALL_PER_HOUR * hours);
        // ⚠ Salted with the hour rather than the settle instant. Keyed on `now` the roll would differ
        // on every tick, which is a chance-per-tick wearing a rate's clothes; keyed on the drop alone
        // it could never change and the package would either run immediately or never.
        long hour = now.getEpochSecond() / 3600L;
        if (HostActivity.roll(hostAt(save, bot.hostAddress), "injector:" + hour) >= chance) {
            return false;
        }
        fn.injectorInstalled = true;
        EventLog.notice(
                save,
                "botnet",
                "somebody on " + bot.hostAddress + " ran the package. "
                        + Balance.BOT_INJECTOR_CYCLES[clampLevel(fn.level)] + " cycles are yours to borrow.",
                now);
        return true;
    }

    // ── miner (§5.3) ────────────────────────────────────────────────────────────────────────────

    /**
     * Accrues mining yield on the bot and, from L5, moves a share of it home.
     *
     * <p>⚠ Yield comes from {@code MiningRules.deployedYield} rather than a formula of its own. What
     * a cycle of mining is worth is one question, and two answers to it would let a bot miner and a
     * deployed miner disagree about the same host's cycles.
     *
     * <p>⚠ Invariant <b>I5</b>: the elapsed time is clamped to {@code OFFLINE_MINING_HOURS} and
     * weighted by {@code OFFLINE_MINING_WIN_WEIGHT}, so a long absence pays four hours at half rate
     * and a longer one pays exactly the same. Offline income is capped and never proportional.
     */
    private static boolean settleMiner(GameSave save, BotState bot, BotFunctionState fn, Instant now) {
        Duration elapsed = Duration.between(fn.lastAccruedAt, now);
        if (elapsed.isNegative() || elapsed.isZero()) {
            return false;
        }
        fn.lastAccruedAt = now;
        long cycles = Balance.BOT_MINER_HOST_CYCLES[clampLevel(fn.level)];
        // ⚠ Speed scales the TIME, not the rate per cycle. "Runs more often" is genuinely more
        // output per hour for a miner, and doing it this way keeps the buffer cap — which is what
        // makes a bot's take seizable — measured in the same units as everybody else's.
        Duration paid = Duration.ofSeconds(Math.round(elapsed.toSeconds() * speedFactor(bot)));
        // ⚠ Invariant I5 clamps the REAL elapsed time, before any speed multiplier. Clamping the
        // scaled figure instead would let a fast bot buy more of an absence than a slow one, which is
        // offline income becoming proportional to something again.
        boolean offline = elapsed.toSeconds() > Balance.OFFLINE_MINING_HOURS * 3600L;
        if (offline) {
            paid = Duration.ofSeconds(
                    Math.round(Balance.OFFLINE_MINING_HOURS * 3600L * speedFactor(bot)));
        }
        BigInteger yield = MiningRules.deployedYield(cycles, paid);
        if (offline) {
            yield = new BigDecimal(yield)
                    .multiply(BigDecimal.valueOf(Balance.OFFLINE_MINING_WIN_WEIGHT))
                    .toBigInteger();
        }
        if (yield.signum() <= 0) {
            return false;
        }
        BigInteger cap = MiningRules.bufferCapFor(cycles);
        fn.bufferedWei = fn.bufferedWei.add(yield).min(cap);
        autoDeposit(save, bot, fn, now);
        return true;
    }

    /**
     * L5+ only. Moves a share of the buffer to the ledger on its own cadence.
     *
     * <p>⚠ Capped at {@code BOT_MINER_AUTODEPOSIT_SHARE}'s published 45%. Auto-deposit is a
     * convenience and never a bypass: collecting by hand has to stay how most of the money moves, or
     * the buffer stops being seizable in any way that matters and the player stops looking at the bot
     * at all.
     */
    private static void autoDeposit(GameSave save, BotState bot, BotFunctionState fn, Instant now) {
        int level = clampLevel(fn.level);
        if (level < Balance.BOT_MINER_AUTODEPOSIT_MIN_LEVEL) {
            return;
        }
        long period = Balance.BOT_MINER_AUTODEPOSIT_SECONDS[level];
        if (period <= 0 || cadencesDue(fn.lastDepositAt, now, period) <= 0) {
            return;
        }
        fn.lastDepositAt = now;
        BigInteger moved = new BigDecimal(fn.bufferedWei)
                .multiply(BigDecimal.valueOf(Balance.BOT_MINER_AUTODEPOSIT_SHARE[level]))
                .toBigInteger();
        if (moved.signum() <= 0) {
            return;
        }
        fn.bufferedWei = fn.bufferedWei.subtract(moved);
        LedgerRules.apply(save, moved, "bot-yield", "Auto-deposit from the bot on " + bot.hostAddress, now);
    }

    // ── sipper (§5.4) ───────────────────────────────────────────────────────────────────────────

    /**
     * Takes a share of what the host moved, up to the hourly ceiling.
     *
     * <h2>⚠ THE CEILING IS THE BOUND. THE TAX RATE IS NOT.</h2>
     *
     * {@code docs/design/10} §5.4. {@link HostActivity} is derived from (address, slot), so the
     * stream this taxes is invented and unbounded — a percentage of it is an ethecoin printer, and
     * every screen renders correctly the whole time it prints.
     * {@code Balance.BOT_SIPPER_MAX_WEI_PER_HOUR} is what makes it an income source instead, and
     * {@link BotFunctionState#sippedThisWindowWei} is what makes the ceiling enforceable.
     *
     * <p>⚠ The window is <b>rolled forward, not reset to now</b>. Snapping the window to the settle
     * instant would let a player who ticked twice in quick succession start a fresh hour each time —
     * the catch-up-storm defect {@code ScanSchedule} records, pointed at an income cap.
     */
    private static boolean settleSipper(
            GameSave save, BotState bot, HostState host, BotFunctionState fn, Instant now) {
        Instant from = latest(fn.lastRunAt, fn.sipWindowStartedAt);
        if (!from.isBefore(now)) {
            return false;
        }
        rollSipWindow(fn, now);
        BigInteger moved = HostActivity.valueMoved(host, fn.lastRunAt, now);
        fn.lastRunAt = now;
        if (moved.signum() <= 0) {
            return false;
        }
        BigInteger take = new BigDecimal(moved)
                .multiply(BigDecimal.valueOf(Balance.BOT_SIPPER_TAX[clampLevel(fn.level)]))
                .toBigInteger();
        BigInteger headroom = hourlyCeiling(fn).subtract(fn.sippedThisWindowWei);
        take = take.min(headroom.max(BigInteger.ZERO));
        if (take.signum() <= 0) {
            return false;
        }
        fn.sippedThisWindowWei = fn.sippedThisWindowWei.add(take);
        fn.bufferedWei = fn.bufferedWei.add(take);
        return true;
    }

    /**
     * The most a Sipper of this level may take in an hour.
     *
     * <p>Scaled from the maximum by the level's share of the top tax rate, so there is exactly one
     * number to tune rather than a second ten-entry table that could disagree with the first.
     */
    static BigInteger hourlyCeiling(BotFunctionState fn) {
        int level = clampLevel(fn.level);
        double share = Balance.BOT_SIPPER_TAX[level] / Balance.BOT_SIPPER_TAX[Balance.BOT_LEVEL_MAX];
        return new BigDecimal(Balance.BOT_SIPPER_MAX_WEI_PER_HOUR)
                .multiply(BigDecimal.valueOf(share), MathContext.DECIMAL64)
                .toBigInteger();
    }

    /** Advances the hourly window in whole hours, so a settle cadence cannot buy a fresh allowance. */
    private static void rollSipWindow(BotFunctionState fn, Instant now) {
        if (fn.sipWindowStartedAt == null || fn.sipWindowStartedAt.equals(Instant.EPOCH)) {
            fn.sipWindowStartedAt = now;
            fn.sippedThisWindowWei = BigInteger.ZERO;
            return;
        }
        long elapsed = Duration.between(fn.sipWindowStartedAt, now).toSeconds();
        if (elapsed < 3600L) {
            return;
        }
        fn.sipWindowStartedAt = fn.sipWindowStartedAt.plusSeconds(elapsed / 3600L * 3600L);
        fn.sippedThisWindowWei = BigInteger.ZERO;
    }

    // ── watcher (§5.5) ──────────────────────────────────────────────────────────────────────────

    /**
     * Files reports for what happened since the last cadence.
     *
     * <p>⚠ How many subjects it may follow at once is the <b>frame's tier</b>, not this function's
     * level (§5.5) — one on a {@code v1}. The level buys fidelity and, when {@code docs/design/14}
     * defines INTEL, the copy chance.
     *
     * <p>⚠ The reports are <b>persisted</b> even though the activity is derived. A sighting is a fact
     * about what a bot happened to be watching; a Watcher socketed tomorrow must not retroactively
     * have seen yesterday.
     */
    private static boolean settleWatcher(
            GameSave save, BotState bot, HostState host, BotFunctionState fn, Instant now) {
        if (cadencesDue(fn.lastRunAt, now, scaled(Balance.BOT_WATCHER_PERIOD_SECONDS, bot)) <= 0) {
            return false;
        }
        Instant from = fn.lastRunAt;
        fn.lastRunAt = now;
        List<HostActivity.Event> events = HostActivity.between(host, from, now);
        if (events.isEmpty()) {
            return false;
        }
        // The chassis decides how much it can follow at once. Newest first, so a v1 watching a busy
        // machine reports the most recent thing rather than the oldest thing it could still see.
        int budget = Math.max(1, bot.frameTier);
        int filed = 0;
        for (int i = events.size() - 1; i >= 0 && filed < budget; i--) {
            HostActivity.Event event = events.get(i);
            BotReportState report = new BotReportState();
            report.at = event.at();
            report.botId = bot.botId;
            report.hostAddress = host.address;
            report.subject = event.kind().name();
            String who = VirtualFs.hostUser(host);
            report.detail = event.kind() == HostActivity.Kind.VALUE
                    ? who + " " + event.detail() + " — " + Ethecoin.format(event.valueWei())
                    : who + " " + event.detail();
            // ⚠ Always false. The only copyable subject is INTEL and it does not exist — §5.5, and
            // BotnetTest holds that nothing sets this.
            report.copyable = false;
            save.botReports.add(report);
            filed++;
        }
        while (save.botReports.size() > Balance.BOT_REPORT_LIMIT) {
            save.botReports.removeFirst();
        }
        return filed > 0;
    }

    // ================================================================== helpers

    /**
     * A cadence, shortened or lengthened by the bot's modifiers.
     *
     * <p>⚠ Modifiers scale the <b>period</b>, never the outcome. A faster bot rolls more often at
     * exactly the same chance and takes its tax more often against exactly the same hourly ceiling —
     * see {@code BotModifier}'s charter for why that boundary is the one thing keeping the modifier
     * slots from being a cheaper way up the function ladder.
     *
     * <p>⚠ Floored at one second, or a large enough speed multiplier would make a period of zero and
     * {@code cadencesDue} would divide by it.
     */
    private static long scaled(long periodSeconds, BotState bot) {
        return Math.max(1L, Math.round(periodSeconds / speedFactor(bot)));
    }

    /** Whole cadences that have elapsed. Zero when the clock has not moved or has gone backwards. */
    private static long cadencesDue(Instant last, Instant now, long periodSeconds) {
        if (last == null || now == null || periodSeconds <= 0) {
            return 0L;
        }
        long elapsed = Duration.between(last, now).toSeconds();
        return elapsed < periodSeconds ? 0L : elapsed / periodSeconds;
    }

    private static Instant latest(Instant a, Instant b) {
        Instant left = a == null ? Instant.EPOCH : a;
        Instant right = b == null ? Instant.EPOCH : b;
        return left.isAfter(right) ? left : right;
    }

    /** Clamps a hand-edited level into the tables rather than throwing on a save the player edited. */
    static int clampLevel(int level) {
        return Math.max(1, Math.min(Balance.BOT_LEVEL_MAX, level));
    }

    /** Function sockets a chassis tier offers. Clamped, for {@link #clampLevel}'s reason. */
    public static int slotsFor(int tier) {
        return Balance.BOT_FRAME_FUNCTIONS[clampTier(tier)];
    }

    /** Modifier sockets a chassis tier offers — zero on a {@code v1}, which has none. */
    public static int modifierSlotsFor(int tier) {
        return Balance.BOT_FRAME_MODIFIERS[clampTier(tier)];
    }

    /**
     * Whether this tier comes home undamaged after a removal — §2.3.
     *
     * <p>⚠ Removal, never destruction. §1a's total loss applies at every tier and this changes
     * nothing about it.
     */
    public static boolean resilient(int tier) {
        return Balance.BOT_FRAME_RESILIENT[clampTier(tier)];
    }

    static int clampTier(int tier) {
        return Math.max(1, Math.min(Balance.BOT_FRAME_TIER_MAX, tier));
    }

    /** Clamps a hand-edited modifier level into the tables rather than throwing. */
    static int clampModifierLevel(int level) {
        return Math.max(1, Math.min(Balance.BOT_MODIFIER_LEVEL_MAX, level));
    }

    private static BotModifier parseModifier(String name) {
        try {
            return BotModifier.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException unknown) {
            return null;
        }
    }

    /** What a modifier is called, in the vocabulary the panel uses. */
    public static String label(BotModifier modifier) {
        if (modifier == null) {
            return "modifier";
        }
        return switch (modifier) {
            case EXE_NAME_SCRAMBLER -> "exe name scrambler";
            case SLEEPY -> "sleepy";
            case DAMPENER -> "dampener";
            case EFFICIENT_MULTITHREADING -> "efficient multithreading";
            case BEDAZZLE_PRO -> "BedazzlePro";
            case PROTECTOR -> "protector";
        };
    }

    /** What a chassis tier holds on the player's own rig — §2.2. */
    public static long controlCyclesFor(int tier) {
        return Balance.BOT_FRAME_CONTROL_CYCLES[clampTier(tier)];
    }

    private static BigInteger bufferOf(BotState bot) {
        BigInteger sum = BigInteger.ZERO;
        for (BotFunctionState fn : bot.functions) {
            if (fn.bufferedWei != null) {
                sum = sum.add(fn.bufferedWei);
            }
        }
        return sum;
    }

    private static boolean occupied(GameSave save, String address) {
        for (BotState bot : save.bots) {
            if (address.equals(bot.hostAddress)) {
                return true;
            }
        }
        return false;
    }

    private static BotState botById(GameSave save, String botId) {
        if (save == null || save.bots == null || botId == null) {
            return null;
        }
        for (BotState bot : save.bots) {
            if (bot.botId.equals(botId)) {
                return bot;
            }
        }
        return null;
    }

    private static ItemState itemById(GameSave save, String itemId) {
        if (save == null || save.items == null || itemId == null) {
            return null;
        }
        for (ItemState item : save.items) {
            if (item.itemId.equals(itemId)) {
                return item;
            }
        }
        return null;
    }

    private static HostState hostAt(GameSave save, String address) {
        if (save == null || save.topology == null || save.topology.hosts == null || address == null) {
            return null;
        }
        for (HostState host : save.topology.hosts) {
            if (address.equals(host.address)) {
                return host;
            }
        }
        return null;
    }

    private static String hostLabel(GameSave save, String address) {
        HostState host = hostAt(save, address);
        return host == null || host.label == null ? "" : host.label;
    }

    private static BotFunction parse(String name) {
        try {
            return BotFunction.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException unknown) {
            // A hand-edited save. Ignored rather than thrown on: a save that outlives the enum that
            // wrote it must still open.
            return null;
        }
    }

    public static String label(BotFunction function) {
        if (function == null) {
            return "module";
        }
        return switch (function) {
            case KEYLOGGER -> "keylogger";
            case INJECTOR -> "injector";
            case MINER -> "miner";
            case SIPPER -> "sipper";
            case WATCHER -> "watcher";
        };
    }

    public static String frameName(String frameType) {
        return Catalogue.byId(frameType).map(Catalogue.Offering::name).orElse("BotFrame");
    }
}
