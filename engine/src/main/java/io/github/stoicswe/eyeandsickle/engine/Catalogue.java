package io.github.stoicswe.eyeandsickle.engine;

import io.github.stoicswe.eyeandsickle.protocol.game.UnlockGate;
import io.github.stoicswe.eyeandsickle.protocol.game.UpgradeKind;
import java.math.BigInteger;
import java.util.List;

/**
 * What the market offers, and behind which gate.
 *
 * <h2>SOLO-3, closed for solo play</h2>
 *
 * The market refused everything because offerings are content and nobody had written any — the same
 * gap the server has as <b>W-3</b> ({@code GatedOfferingCatalog} is empty). This is a small, honest
 * first catalogue, priced inside the bands {@code docs/design/03-economy.md} §2 publishes and gated by
 * the rule in {@code docs/design/02-unlock-gates.md} §5.
 *
 * <h2>Every entry obeys the gate-assignment rule, not taste</h2>
 *
 * §5's checklist is: classify the gate, price against {@code 03}, add to the right table. The
 * classification is not a judgement call — {@code 02} assigns it:
 *
 * <ul>
 *   <li><b>Ethecoin</b> — consumables, replacements, horizontal options. Never a ceiling (I2).
 *   <li><b>Schematic</b> — permanent capability. Found or earned, never bought. This is what stops
 *       money from becoming progress, so nothing here is purchasable with ethecoin at any price.
 *   <li><b>Reputation</b> — things that would distort the economy if simply free.
 *   <li><b>Proof-of-skill</b> — automation shortcuts, tier-gated never count-gated (I7).
 *   <li><b>Heat-state</b> — access that swings both ways; some contacts only deal with you cold.
 * </ul>
 *
 * <p><b>Nothing here sells compute or vault capacity at any price</b>, which is Invariants I1 and I12
 * made structural: there is no offering to buy, so there is no code path to review.
 *
 * <h2>Why this lives in `solo` and not in the client</h2>
 *
 * Because it is a rule, not a rendering. When the server's own catalogue lands, this is the shape it
 * should take and the two should be reconciled — a single catalogue serving both is the right end
 * state, and W-3 tracks it.
 */
public final class Catalogue {

    private Catalogue() {}

    /**
     * One thing the market can offer.
     *
     * @param priceWei the ethecoin price, or 0 when the gate is not ethecoin — a non-zero
     *     price on a schematic-gated item would be exactly the I2 violation the gate exists to stop
     */
    public record Offering(
            String id,
            String name,
            String description,
            UnlockGate gate,
            BigInteger priceWei,
            long equippedCycles,
            String gateRequirement,
            UpgradeKind kind,
            String requiresSchematic,
            String stopsTool,
            Durability durability,
            java.util.List<String> tags) {

        /**
         * What kind of thing this is — {@code defence}, {@code recon}, {@code stealth}, {@code mining}.
         *
         * <h2>⚠ The FIRST tag, by convention, and the convention is now enforced</h2>
         *
         * Every offering's tag list already opens with the word a player would file it under and
         * continues with search terms ({@code "defence", "detection", "tripwire", "cheap"}). Reading
         * the category off tag zero rather than adding a parallel field is what stops the two
         * disagreeing — a separate {@code category} would be a second answer to a question the tags
         * already settle, and the day somebody edited one and not the other the shelf and the search
         * would file the same item in two places.
         *
         * <p>⚠ {@code CatalogueTest} holds that every offering has one, because an empty list here
         * would silently file an item under "other" and it would be findable only by scrolling.
         *
         * @return the category, or {@code other} when an offering carries no tags at all
         */
        public String category() {
            return tags.isEmpty() ? "other" : tags.getFirst();
        }

        /**
         * An ordinary software offering — the shape every entry had before firmware existed.
         *
         * <p>⚠ Defaults to {@link Durability#PERMANENT}, which is the CAUTIOUS direction: permanent
         * carries the shallower discount band, so an entry added without thinking about durability
         * gets the smaller sale rather than the larger one. A default of consumable would put a new
         * tool on the deepest discount in the game by omission.
         */
        public Offering(
                String id,
                String name,
                String description,
                UnlockGate gate,
                BigInteger priceWei,
                long equippedCycles,
                String gateRequirement) {
            this(
                    id,
                    name,
                    description,
                    gate,
                    priceWei,
                    equippedCycles,
                    gateRequirement,
                    UpgradeKind.SOFTWARE,
                    "",
                    "",
                    Durability.PERMANENT,
                    java.util.List.of());
        }

        /** An ordinary offering with search tags. */
        public Offering(
                String id,
                String name,
                String description,
                UnlockGate gate,
                BigInteger priceWei,
                long equippedCycles,
                String gateRequirement,
                java.util.List<String> tags) {
            this(
                    id,
                    name,
                    description,
                    gate,
                    priceWei,
                    equippedCycles,
                    gateRequirement,
                    UpgradeKind.SOFTWARE,
                    "",
                    "",
                    Durability.PERMANENT,
                    tags);
        }

        /** A consumable — spent, or bought again for the next use. See {@link Durability}. */
        public static Offering consumable(
                String id,
                String name,
                String description,
                BigInteger priceWei,
                long equippedCycles,
                java.util.List<String> tags) {
            return new Offering(
                    id,
                    name,
                    description,
                    UnlockGate.ETHECOIN,
                    priceWei,
                    equippedCycles,
                    "",
                    UpgradeKind.SOFTWARE,
                    "",
                    "",
                    Durability.CONSUMABLE,
                    tags);
        }

        /**
         * Whether this offering answers a search.
         *
         * <p>⚠ Matches the NAME, the DESCRIPTION and the TAGS. Tags alone would miss a player typing
         * a word straight off the card they are looking at, and name alone would make the tags
         * decorative — the point of a tag is to find something whose name you do not know.
         *
         * @param query lower-cased, already trimmed
         * @return whether it matches
         */
        public boolean matches(String query) {
            if (query == null || query.isBlank()) {
                return true;
            }
            String needle = query.toLowerCase(java.util.Locale.ROOT);
            return name.toLowerCase(java.util.Locale.ROOT).contains(needle)
                    || description.toLowerCase(java.util.Locale.ROOT).contains(needle)
                    || tags.stream().anyMatch(tag -> tag.contains(needle));
        }

        public Offering {
            kind = kind == null ? UpgradeKind.SOFTWARE : kind;
            durability = durability == null ? Durability.PERMANENT : durability;
            // ⚠ Lower-cased at construction, so search never has to. A tag that differed only in case
            // would be a second tag nobody could tell from the first — the shelf would show two
            // "Defence" filters returning different sets.
            tags = tags == null
                    ? java.util.List.of()
                    : tags.stream()
                            .map(tag -> tag.toLowerCase(java.util.Locale.ROOT).trim())
                            .filter(tag -> !tag.isBlank())
                            .distinct()
                            .toList();
            requiresSchematic = requiresSchematic == null ? "" : requiresSchematic;
            stopsTool = stopsTool == null ? "" : stopsTool;
            // ⚠ Firmware without a schematic would be a permanent capability reachable with money
            // alone, which is Invariant I2 and docs/design/11 §4 rule 1 ("It MUST be schematic/story-
            // gated. No EC path. No exceptions."). Enforced rather than documented, because the
            // tempting edit is exactly to add a firmware entry and leave this blank.
            if (kind == UpgradeKind.FIRMWARE && requiresSchematic.isBlank()) {
                throw new IllegalArgumentException("firmware must name the schematic that authorises it: " + id);
            }
        }

        /** Whether ethecoin alone unlocks this. */
        public boolean purchasable() {
            return gate == UnlockGate.ETHECOIN;
        }

        /** Whether this is firmware, with everything that implies — see {@link UpgradeKind}. */
        public boolean firmware() {
            return kind == UpgradeKind.FIRMWARE;
        }
    }

    /**
     * The solo catalogue.
     *
     * <p>Deliberately short. A long list of invented items would be content decisions made in code,
     * which is what {@code CLAUDE.md} asks not to happen — every entry here either already exists in
     * a design document's tool tables or is a plain consumable whose only property is its price.
     */
    public static List<Offering> offerings() {
        java.util.List<Offering> out = new java.util.ArrayList<>(coreOfferings());
        out.addAll(botFrames());
        return List.copyOf(out);
    }

    /**
     * The chassis ladder — {@code docs/design/10} §2.1.
     *
     * <h2>⚠ GENERATED FROM THE BALANCE TABLES, and that is not laziness</h2>
     *
     * Ten rungs written out by hand is ten places for the socket counts to disagree with
     * {@code Balance.BOT_FRAME_FUNCTIONS} and {@code BOT_FRAME_MODIFIERS} — and the disagreement
     * would be invisible, because the shop would describe one thing and the rules would enforce
     * another with every screen rendering perfectly. This is the same argument
     * {@code defenceOfferingId} makes for having one mapping rather than three copies.
     *
     * <p>⚠ <b>Exactly one rung has a price.</b> v1 is ethecoin; v2 through v10 are compiled and
     * priced at zero. {@code BotnetTest.onlyTheFirstFrameIsForSale} fails the build if a second one
     * acquires one — see {@code Balance.BOT_FRAME_V1_PRICE} for why the whole "money on the botnet
     * gate at all" argument is one rung wide.
     */
    private static List<Offering> botFrames() {
        java.util.List<Offering> frames = new java.util.ArrayList<>();
        for (int tier = 1; tier <= Balance.BOT_FRAME_TIER_MAX; tier++) {
            int functions = Balance.BOT_FRAME_FUNCTIONS[tier];
            int modifiers = Balance.BOT_FRAME_MODIFIERS[tier];
            boolean resilient = Balance.BOT_FRAME_RESILIENT[tier];
            String sockets = functions + " function" + (functions == 1 ? "" : "s")
                    + (modifiers == 0 ? ", no modifier socket" : ", " + modifiers + " modifier"
                            + (modifiers == 1 ? "" : "s"));
            String description = sockets + ". Holds "
                    + Balance.BOT_FRAME_CONTROL_CYCLES[tier] + " cycles on your own rig while it runs"
                    + (resilient
                            ? ". Comes back intact if somebody throws it off — empty, but not damaged."
                            : ". Comes back damaged if somebody throws it off.");
            frames.add(tier == 1
                    ? new Offering(
                            botFrameId(1),
                            "BotFrame_v1",
                            description + " An empty frame cannot be uploaded anywhere.",
                            UnlockGate.ETHECOIN,
                            Balance.BOT_FRAME_V1_PRICE,
                            // ⚠ Zero. equippedCycles is what a tool holds while EQUIPPED; a frame
                            // holds nothing until it is uploaded, and what it holds then is a
                            // BOT_FRAME control channel reserved against BOT_FRAME_CONTROL_CYCLES. A
                            // number in both places would be two answers to what a bot costs.
                            0,
                            "",
                            java.util.List.of("botnet", "frame", "chassis", "bot", "starter"))
                    : new Offering(
                            botFrameId(tier),
                            "BotFrame_v" + tier,
                            description + " Compiled, not bought.",
                            UnlockGate.SCHEMATIC,
                            BigInteger.ZERO,
                            0,
                            "Requires the v" + tier + " frame schematic and materials, assembled in "
                                    + "the compiler. Chassis above the first rung are never sold.",
                            java.util.List.of("botnet", "frame", "chassis", "bot", "schematic")));
        }
        return List.copyOf(frames);
    }

    /** The catalogue id for a chassis tier. One spelling, for {@code defenceOfferingId}'s reason. */
    public static String botFrameId(int tier) {
        return "bot-frame-v" + Math.max(1, Math.min(Balance.BOT_FRAME_TIER_MAX, tier));
    }

    private static List<Offering> coreOfferings() {
        return List.of(
                // ── the compute ladder (docs/design/01 §1.1) ─────────────────────────────────────
                //
                // ⚠ THE FIRST RUNG IS THE ONE PLACE IN THIS GAME WHERE ETHECOIN BUYS COMPUTE, AND
                // THAT IS INVARIANT I1 AMENDED ON EXPLICIT DIRECTION (2026-08-06, design/15 §3).
                //
                // I1 exists because mining that buys mining capacity is a compounding flywheel. One
                // rung cannot compound: the step above 32 is not for sale at any price, so money
                // moves a player up ONCE, ever. `ComputeLadderTest.onlyTheFirstRungIsForSale` fails
                // the build if a second rung acquires a price — if that ever happens, I1 has been
                // abandoned rather than amended, and it should be a red build rather than a
                // conversation nobody had.
                //
                // ⚠ SOFTWARE, not FIRMWARE, and the distinction is doing real work. Offering's
                // compact constructor REFUSES firmware with no schematic named — that guard is what
                // stops firmware becoming money-reachable, and marking this one firmware would have
                // forced a schematic onto the rung that is meant to be bought. So the shape says
                // what it is: the first rung is a PRODUCT, and the two above it are things you
                // COMPILE.
                new Offering(
                        "compute-32",
                        "Capacity Board — 32C",
                        "A daughter board and the firmware to drive it. Takes this rig from 24 cycles "
                                + "to 32, once. There is no second one, and nothing above it is sold.",
                        UnlockGate.ETHECOIN,
                        Balance.COMPUTE_32_PRICE,
                        0,
                        "",
                        java.util.List.of("rig", "compute", "capacity", "upgrade", "expensive")),
                // ⚠ NO PRICE, EVER. See Balance.COMPUTE_32_PRICE for why exactly one rung has one.
                new Offering(
                        "compute-48",
                        "Capacity Lattice — 48C",
                        "Compiled, not bought. Needs the lattice schematic and the materials to build "
                                + "it, and it will not go on a rig that has not already taken 32.",
                        UnlockGate.SCHEMATIC,
                        BigInteger.ZERO,
                        0,
                        "Requires the 48C lattice schematic and rare materials, assembled in the "
                                + "compiler. Capacity above the first rung is never sold.",
                        java.util.List.of("rig", "compute", "capacity", "upgrade", "schematic")),
                new Offering(
                        "compute-64",
                        "Capacity Lattice — 64C",
                        "The top of the ladder. A rarer schematic, rarer materials, and every rung "
                                + "below it already in place.",
                        UnlockGate.SCHEMATIC,
                        BigInteger.ZERO,
                        0,
                        "Requires the 64C lattice schematic and rare materials, assembled in the "
                                + "compiler. Capacity above the first rung is never sold.",
                        java.util.List.of("rig", "compute", "capacity", "upgrade", "schematic")),
                // ── the firewall ladder (docs/design/09 §1) ──────────────────────────────────────
                //
                // ⚠ ALL THREE ARE ETHECOIN, INCLUDING THE TOP ONE, and that is `09` §2's own
                // classification rather than a relaxation of I2: a firewall is horizontal protection
                // and "the escalating compute cost (5/9/15 while armed) is the real limiter". T3 is
                // what `03` §2 calls a TOP PURCHASABLE — money reaches the highest rung of a ladder,
                // never a rung above the ladder.
                //
                // ⚠ T1 IS THE ONE DEFENCE A NEW CHARACTER ALREADY OWNS. `GameEngine.newCharacter`
                // grants it, so a fresh rig is not defenceless and the FIREWALL panel is not a screen
                // of ten refusals. It is still a catalogue entry with a price, because it is
                // losable, sellable and re-buyable like everything else on the ethecoin gate (`02`
                // §2.1) — being granted is a starting position, not an exemption from the rules.
                new Offering(
                        "firewall-t1",
                        "Firewall T1",
                        "A flat difficulty increase on anything trying to breach this rig. The cheapest "
                                + "standing defence there is, and the one every rig starts with.",
                        UnlockGate.ETHECOIN,
                        Balance.DEFENSE_FIREWALL_T1_PRICE,
                        Balance.DEFENSE_FIREWALL_T1_CYCLES,
                        "",
                        java.util.List.of("defence", "firewall", "barrier", "standing", "starter")),
                new Offering(
                        "firewall-t2",
                        "Firewall T2",
                        "Twice the standing cost of a T1 for a larger flat difficulty add. The cycles "
                                + "are the price you actually pay — they are gone for as long as it is armed.",
                        UnlockGate.ETHECOIN,
                        Balance.DEFENSE_FIREWALL_T2_PRICE,
                        Balance.DEFENSE_FIREWALL_T2_CYCLES,
                        "",
                        java.util.List.of("defence", "firewall", "barrier", "standing")),
                new Offering(
                        "firewall-t3",
                        "Firewall T3",
                        "The hardest wall money buys. Fifteen permanent cycles is fifteen you are not "
                                + "mining or attacking with, which is the whole of the decision.",
                        UnlockGate.ETHECOIN,
                        Balance.DEFENSE_FIREWALL_T3_PRICE,
                        Balance.DEFENSE_FIREWALL_T3_CYCLES,
                        "",
                        java.util.List.of("defence", "firewall", "barrier", "standing", "top-purchasable")),
                // ⚠ THE BREACH VIRUS — docs/design/19 §5. Four tiers, CONSUMABLE, spent on every
                // breach of a foreign machine. What a tier buys is the chance a solved board actually
                // takes the machine (55% → 90%), and against a real player it buys the virus lives
                // their defence round has to get through.
                //
                // ⚠ CONSUMABLE is load-bearing rather than descriptive: it is the whole of why this
                // is not I2's forbidden ceiling. Spent every attempt, it is a running cost that never
                // accumulates into a capability, and it cannot skip the puzzle — the roll happens
                // only after the board is solved. Make one of these PERMANENT and money has bought a
                // permanent success rate, which is exactly what I2 forbids.
                Offering.consumable(
                        "breach-virus-t1",
                        "Breach Virus — Tier 1",
                        "The cheapest thing that will carry a breach. Gets in a little over half the "
                                + "time once you have opened the board, and costs about what a shallow "
                                + "machine holds.",
                        Balance.BREACH_VIRUS_T1_PRICE,
                        0L,
                        java.util.List.of("intrusion", "virus", "breach", "consumable", "cheap", "starter")),
                Offering.consumable(
                        "breach-virus-t2",
                        "Breach Virus — Tier 2",
                        "Better odds on a solved board, and harder to put down if somebody is home to "
                                + "defend against it.",
                        Balance.BREACH_VIRUS_T2_PRICE,
                        0L,
                        java.util.List.of("intrusion", "virus", "breach", "consumable")),
                Offering.consumable(
                        "breach-virus-t3",
                        "Breach Virus — Tier 3",
                        "Takes three hits to put down and lands four times in five. For machines worth "
                                + "the second attempt you will not have to make.",
                        Balance.BREACH_VIRUS_T3_PRICE,
                        0L,
                        java.util.List.of("intrusion", "virus", "breach", "consumable")),
                Offering.consumable(
                        "breach-virus-t4",
                        "Breach Virus — Tier 4",
                        "Nine times in ten, and four lives against anybody defending. Priced so that "
                                + "spending one on a shallow machine is a mistake you only make once.",
                        Balance.BREACH_VIRUS_T4_PRICE,
                        0L,
                        java.util.List.of("intrusion", "virus", "breach", "consumable", "top-purchasable")),
                // ⚠ CONSUMABLE: a canary is planted and spent. It is bought repeatedly, which is
                // what makes it the right side of a sale — a discount changes a decision a player
                // makes often and never accumulates into a capability.
                Offering.consumable(
                        "canary-token",
                        "Canary Token",
                        "A file with no purpose but to tell you somebody touched it, and to tag who. "
                                + "The cheapest useful detection in the game, and the only one with "
                                + "essentially no false alarms — nothing legitimate ever opens it.",
                        Balance.DEFENSE_CANARY_PRICE,
                        Balance.DEFENSE_CANARY_CYCLES,
                        java.util.List.of("defence", "detection", "tripwire", "cheap", "starter")),
                new Offering(
                        "tarpit",
                        "Tarpit",
                        "Does not stop an intruder. Slows every action they take, which buys you the "
                                + "seconds your response actually needs.",
                        UnlockGate.ETHECOIN,
                        Balance.DEFENSE_TARPIT_PRICE,
                        Balance.DEFENSE_TARPIT_CYCLES,
                        "",
                        java.util.List.of("defence", "delay", "intruder", "response")),
                // ⚠ CONSUMABLE, and the only one whose subject is the MAP rather than the rig.
                //
                // One is spent per crossing opened and the crossing then stays open forever, so the
                // total a player ever spends on these is bounded by the number of servers in their
                // world rather than by how much they travel. That bound is what keeps it breadth
                // (I2-legal) rather than a toll: it buys access to a region once, it does not buy a
                // ceiling, and no amount of ethecoin buys the second thing a crossing needs, which is
                // a breached bridge to put it on.
                //
                // ⚠ It is deliberately NOT gated on a schematic. This is the only route to every
                // server but home, and putting the world behind a drop would make a whole game's
                // content contingent on a roll. `docs/design/02` §1.1 puts "access you can buy
                // repeatedly, that accumulates into no capability" on ethecoin, and this is that.
                Offering.consumable(
                        NETMAN_ID,
                        "NET_MAN",
                        "A network manager for somebody else's border machine. Upload it to a bridge "
                                + "you have breached and the network behind that bridge starts "
                                + "answering you — sweeps, scans, breaches, shells. The upload is the "
                                + "loudest thing you will ever do; once it lands it is silent, and the "
                                + "crossing stays open for good.",
                        Balance.NETMAN_PRICE_EC,
                        // Holds nothing while it sits in the vault: an unspent NET_MAN is a file. What
                        // it costs while uploading is noise, not cycles — the link is already held.
                        0,
                        java.util.List.of("network", "crossing", "bridge", "travel", "consumable")),
                // ── the sweep ladder (docs/design/17) ────────────────────────────────────────────
                //
                // ⚠ Both are ETHECOIN-gated, and that classification is the ordered procedure in
                // docs/design/02-unlock-gates.md §1.1 rather than taste. What they buy is the
                // PROBABILITY of detecting what is already within reach — no new hop, no new field,
                // no new class of node — which is breadth, and §1.1 step 4 puts breadth on ethecoin.
                //
                // ⚠ Neither changes the hop ceiling, at any price. Reach is the Topology Mapper's,
                // and docs/design/07-recon-tools.md §2 makes it schematic-gated precisely because
                // Invariant I2 says ethecoin never buys a ceiling. There is no code path from an
                // offering to NetRules.hopCeiling, and a test enumerates this list to prove it.
                //
                // equippedCycles is 0: a sweep tool holds nothing while idle. Its compute is held for
                // the duration of a sweep and released into recovery when the sweep ends.
                new Offering(
                        "net-sweep-wide",
                        "Net Sweep (Wide)",
                        "A wider sweep of the same distance. Finds quieter machines inside the reach "
                                + "you already have. It does not reach further — reach is not for sale.",
                        UnlockGate.ETHECOIN,
                        Balance.NET_SWEEP_WIDE_PRICE,
                        0,
                        "",
                        java.util.List.of("recon", "sweep", "discovery", "network", "scanning")),
                new Offering(
                        "net-sweep-deep",
                        "Net Sweep (Deep)",
                        "The most sensitive instrument money buys. Near-certain on infrastructure, and "
                                + "it finally makes quiet desktops reliable. Still one hop.",
                        UnlockGate.ETHECOIN,
                        Balance.NET_SWEEP_DEEP_PRICE,
                        0,
                        "",
                        java.util.List.of("recon", "sweep", "discovery", "network", "scanning", "sensitive")),
                // ⚠ CONSUMABLE, and the offering's own text says why: "charged per session rather
                // than owned". This is the one entry where the classification is not a judgement.
                Offering.consumable(
                        "relay-hop",
                        "Relay hop (one session)",
                        "One more hop in a relay chain. Harder to trace, slower to act — the trade is "
                                + "the point, and it is charged per session rather than owned.",
                        Balance.RELAY_HOP_UPKEEP,
                        0,
                        java.util.List.of("stealth", "relay", "anonymity", "per-session", "cheap")),
                // ── the detection-array ladder (docs/design/09 §1, AMENDED 2026-08-06) ───────────
                //
                // ⚠ T1 AND T2 MOVED FROM THE SCHEMATIC GATE TO ETHECOIN; T3 DID NOT, AND THE SPLIT
                // IS WHAT KEEPS I2 TRUE. The rule, on explicit direction and logged in
                // `docs/design/15` §3: low-level base tools and low-level upgrades are purchasable
                // and cost more than a consumable; high-level and rare items need a schematic.
                //
                // ⚠ It is the firewall's own shape, one item along — a ladder whose top rung is out
                // of the market's reach. What the Array sells is PRECISION (`09` §2: it "improves
                // the quality of the signal rather than the chance of a hit", cutting the
                // false-positive rate), and the ladder's ceiling is the tier that money cannot get
                // to. Give T3 a price and ethecoin has bought a permanent capability, with the shop
                // still rendering correctly and the purchase still working.
                //
                // ⚠ I3 is untouched: these are three ITEMS, not one item with two gates. Each sits
                // behind exactly one.
                new Offering(
                        "detection-array-t1",
                        "Detection Array T1",
                        "Standing detection. Reserves compute permanently while armed, in exchange for "
                                + "a scan on this rig lying to you less often than a scan on a bare one.",
                        UnlockGate.ETHECOIN,
                        Balance.DEFENSE_DETECTION_ARRAY_T1_PRICE,
                        Balance.DEFENSE_DETECTION_ARRAY_T1_CYCLES,
                        "",
                        java.util.List.of("defence", "detection", "standing", "precision")),
                new Offering(
                        "detection-array-t2",
                        "Detection Array T2",
                        "Better instrumentation, at more than twice the standing cost. You are paying, "
                                + "continuously, not to be sent chasing ghosts.",
                        UnlockGate.ETHECOIN,
                        Balance.DEFENSE_DETECTION_ARRAY_T2_PRICE,
                        Balance.DEFENSE_DETECTION_ARRAY_T2_CYCLES,
                        "",
                        java.util.List.of("defence", "detection", "standing", "precision")),
                // ⚠ NO PRICE, AND IT MUST STAY THAT WAY. See Balance.DEFENSE_DETECTION_ARRAY_T1_PRICE.
                new Offering(
                        "detection-array-t3",
                        "Detection Array T3",
                        "The best instrumentation there is, and twenty-five permanent cycles for it. "
                                + "Compiled from a schematic, never sold.",
                        UnlockGate.SCHEMATIC,
                        BigInteger.ZERO,
                        Balance.DEFENSE_DETECTION_ARRAY_T3_CYCLES,
                        "Requires the Detection Array T3 schematic. The top of a ladder is never for "
                                + "sale — that is what stops ethecoin from buying a ceiling.",
                        java.util.List.of("defence", "detection", "standing", "schematic")),
                // ⚠ NOT PURCHASABLE, AND THE ZERO PRICE IS LOAD-BEARING. See Catalogue.TOR_MODULE.
                // It arrives in the COMS inbox from rules/BlackMarket and by no other route.
                new Offering(
                        TOR_MODULE,
                        "TOR Module",
                        "An onion router. It does not hide you from anybody — what it does is reach "
                                + "addresses ordinary lookups refuse to resolve, which is the only way "
                                + "to see the Marknet board at all.",
                        UnlockGate.HEAT_STATE,
                        BigInteger.ZERO,
                        0,
                        "Sent to you, once the people who run the Marknet decide you are worth "
                                + "talking to. It takes standing with a faction and enough heat to be "
                                + "worth approaching. It is not for sale.",
                        java.util.List.of("access", "darknet", "routing", "marknet")),
                new Offering(
                        "honeypot-stash",
                        "Honeypot Stash",
                        "A decoy store of junk that a raider cannot tell from a real one until they "
                                + "have paid to extract from it.",
                        UnlockGate.REPUTATION,
                        BigInteger.ZERO,
                        Balance.DEFENSE_HONEYPOT_STASH_CYCLES,
                        "Requires standing with a faction. Decoy infrastructure would distort the "
                                + "economy if anyone could simply buy it.",
                        java.util.List.of("defence", "decoy", "deception", "reputation")),
                new Offering(
                        "auto-counter-daemon",
                        "Auto-Counter Daemon",
                        "Fires back on your behalf while you are logged off. In this fiction. See "
                                + "hack-back(7) before you assume that maps onto anything you may do.",
                        UnlockGate.SCHEMATIC,
                        BigInteger.ZERO,
                        Balance.DEFENSE_AUTO_COUNTER_CYCLES,
                        "Requires the schematic, and the heaviest standing compute cost of any defence.",
                        java.util.List.of("defence", "counter-attack", "automation", "schematic")),
                // ── firmware (docs/design/11-rig-infrastructure.md §3) ───────────────────────────
                //
                // ⚠ THE IMAGE IS THE PURCHASABLE HALF; THE SCHEMATIC IS THE CEILING.
                //
                // `11` §1 establishes the Firmware Implant as "recovered from deep inside Eye
                // infrastructure — acquiring it is itself a late-game objective, not a shop
                // transaction", and §4 rule 1 forbids any EC path to a permanent capability. Both
                // still hold: what the market sells here is the firmware IMAGE, which does nothing
                // whatsoever without the schematic that authorises flashing it.
                //
                // That split is `02` §1.1's own sanctioned pattern — "Rainbow Table is EC + schematic
                // (buy the table, but the capability to use it is found)" — under its standing
                // condition that the ceiling component sits on the non-EC side. It does: no amount of
                // ethecoin produces the schematic, and `02` §2.2 keeps schematics unsellable and
                // un-farmable.
                //
                // ⚠ §4 rule 2: it touches mining income and adds NO cycles. Surviving a host wipe
                // changes how long a deployed miner lives, never how much compute exists — so there
                // is no compute-buys-compute loop (I1) and no ceiling bought with money (I2).
                new Offering(
                        "firmware-implant",
                        "Firmware Implant (image)",
                        "The flashable image for the Firmware Implant: deployed miners survive a host "
                                + "wipe. Worthless on its own -- flashing it needs the schematic, which "
                                + "is recovered rather than bought. Mining must be stopped to install, "
                                + "because firmware sits underneath the program using it.",
                        UnlockGate.ETHECOIN,
                        Balance.FIRMWARE_IMPLANT_IMAGE_PRICE,
                        0,
                        "",
                        UpgradeKind.FIRMWARE,
                        FIRMWARE_IMPLANT_SCHEMATIC,
                        MINING_TOOL,
                        // ⚠ PERMANENT, and the shallowest band in the game applies to it. Firmware is
                        // flashed once and kept; a deep discount on it would take a fixed lump out of
                        // the sink for a purchase a player makes exactly one time.
                        Durability.PERMANENT,
                        java.util.List.of("firmware", "mining", "persistence", "schematic", "flash")),
                // ── the botnet (docs/design/10 §2.1, §5) ─────────────────────────────────────────
                //
                // ⚠ EXACTLY ONE CHASSIS HAS A PRICE, AND THAT IS THE WHOLE SAFETY ARGUMENT for
                // putting any of this on the money gate. `BotnetTest.onlyTheFirstFrameIsForSale`
                // fails the build if v2 or v3 acquires one. It is the same shape as the compute
                // ladder and the firewall's TOP PURCHASABLE: money reaches the first rung of a
                // ladder, never a rung above the ladder.
                //
                // ⚠ SOFTWARE, not FIRMWARE. Offering's compact constructor refuses firmware with no
                // schematic named, and marking a chassis firmware would force a schematic onto the
                // one rung that is meant to be bought — the trap `compute-32` already records.
                // ⚠ THE FOUR PURCHASABLE FUNCTIONS BUY LEVEL 1 AND NOTHING ELSE. Levels 2-10 cost
                // ethecoin AND schematic material (Balance.BOT_LEVEL_MATERIAL), and material is not
                // for sale — which is how a ten-rung capability ladder stays off the money gate
                // without needing a new mechanism (I2, docs/design/10 §5).
                new Offering(
                        BOT_FN_KEYLOGGER,
                        "Keylogger Module",
                        "Sits on a machine and fills in your recon file on it, one finding at a time, "
                                + "for nothing. It cannot learn anything a port scan could not — what it "
                                + "buys is not having to run one.",
                        UnlockGate.ETHECOIN,
                        Balance.BOT_KEYLOGGER_PRICE,
                        0,
                        "",
                        java.util.List.of("botnet", "function", "recon", "bot", "passive")),
                // ⚠ SCHEMATIC, AND IT IS THE ONLY FUNCTION THAT MAY NEVER HAVE A PRICE.
                //
                // This is the one that hands the player COMPUTE, and compute is the master scarcity.
                // An ethecoin-gated Injector is ethecoin buying capacity, which is Invariant I1 with
                // extra steps — and unlike the compute ladder's amended first rung, this one would
                // COMPOUND, because the cycles it frees are cycles that can run more bots.
                new Offering(
                        BOT_FN_INJECTOR,
                        "Injector Module",
                        "Drops a package in the host's Downloads. If somebody over there runs it, your "
                                + "tools can borrow that machine's cycles instead of yours. Never your "
                                + "mining — that stays on your own rig, where it can be counted.",
                        UnlockGate.SCHEMATIC,
                        BigInteger.ZERO,
                        0,
                        "Compiled from a schematic and never sold. What it hands out is compute, and "
                                + "compute is not something ethecoin buys.",
                        java.util.List.of("botnet", "function", "compute", "bot", "schematic")),
                new Offering(
                        BOT_FN_MINER,
                        "Miner Module",
                        "Mines on the host and buffers the take on the bot. The cycles are the host's, "
                                + "not yours — but so is the buffer, until you collect it, and anyone "
                                + "who finds the bot finds the money sitting on it.",
                        UnlockGate.ETHECOIN,
                        Balance.BOT_MINER_PRICE,
                        0,
                        "",
                        java.util.List.of("botnet", "function", "mining", "bot", "income")),
                new Offering(
                        BOT_FN_SIPPER,
                        "Sipper Module",
                        "Skims a share off what the host moves. Worth most where money actually flows, "
                                + "and there is a ceiling on what it can take in an hour however rich "
                                + "the machine is.",
                        UnlockGate.ETHECOIN,
                        Balance.BOT_SIPPER_PRICE,
                        0,
                        "",
                        java.util.List.of("botnet", "function", "income", "bot", "tax")),
                new Offering(
                        BOT_FN_WATCHER,
                        "Watcher Module",
                        "Tells you what the operator is doing — what they queue, what they move. How "
                                + "many things at once is the frame's business, not the module's: a v1 "
                                + "watches one.",
                        UnlockGate.ETHECOIN,
                        Balance.BOT_WATCHER_PRICE,
                        0,
                        "",
                        java.util.List.of("botnet", "function", "surveillance", "bot", "passive")),
                // ── modifiers (docs/design/10 §5a) ───────────────────────────────────────────────
                //
                // ⚠ ALL SIX ARE ETHECOIN, AND THAT IS THE GATE RULE RATHER THAN A RELAXATION.
                // docs/design/02 §1.1 puts HORIZONTAL options on the money gate, and a modifier is
                // horizontal by charter: it changes how a bot survives, never what it achieves. The
                // vertical thing — a function's ten-level ladder — is the one that costs schematic
                // material, and nothing here touches it. See BotModifier's class javadoc for why
                // that boundary is the whole reason these can be bought at all.
                new Offering(
                        BOT_MOD_SCRAMBLER,
                        "Exe Name Scrambler",
                        "Your bot stops showing up as an unregistered process and starts wearing the "
                                + "name of something that belongs there. One level; a process either "
                                + "looks plausible or it does not.",
                        UnlockGate.ETHECOIN,
                        Balance.BOT_MOD_SCRAMBLER_PRICE,
                        0,
                        "",
                        java.util.List.of("botnet", "modifier", "stealth", "bot")),
                new Offering(
                        BOT_MOD_SLEEPY,
                        "Sleepy",
                        "Runs the bot's thread far less often. Everything it does takes longer, it is "
                                + "much harder to catch, and while it is asleep it is not in the process "
                                + "table at all.",
                        UnlockGate.ETHECOIN,
                        Balance.BOT_MOD_SLEEPY_PRICE,
                        0,
                        "",
                        java.util.List.of("botnet", "modifier", "stealth", "bot")),
                new Offering(
                        BOT_MOD_DAMPENER,
                        "Dampener",
                        "Cuts what the bot adds to your own noise. Never to nothing — a bot you are "
                                + "running is a bot somebody could hear.",
                        UnlockGate.ETHECOIN,
                        Balance.BOT_MOD_DAMPENER_PRICE,
                        0,
                        "",
                        java.util.List.of("botnet", "modifier", "stealth", "noise", "bot")),
                new Offering(
                        BOT_MOD_THREADS,
                        "EfficientMultiThreading",
                        "The bot's function runs more often. It does not run BETTER — the odds, the "
                                + "ceilings and the levels are all untouched — and it is a great deal "
                                + "louder.",
                        UnlockGate.ETHECOIN,
                        Balance.BOT_MOD_THREADS_PRICE,
                        0,
                        "",
                        java.util.List.of("botnet", "modifier", "speed", "bot")),
                // ⚠ CHEAPEST IN THE GAME, and priced honestly rather than as a trap. It does
                // NOTHING. A joke item priced like a real one would read as a stat nobody could
                // find, and the player would reasonably assume the modifier was broken.
                new Offering(
                        BOT_MOD_BEDAZZLE,
                        "BedazzlePro",
                        "Confetti. A cake. A unicorn. Does not help your bot in any way whatsoever, "
                                + "and everyone who has ever been on the receiving end remembers it.",
                        UnlockGate.ETHECOIN,
                        Balance.BOT_MOD_BEDAZZLE_PRICE,
                        0,
                        "",
                        java.util.List.of("botnet", "modifier", "cosmetic", "bot", "cheap")),
                new Offering(
                        BOT_MOD_PROTECTOR,
                        "Protector",
                        "Blocks removal attempts and covers the bot's tracks when it does — they think "
                                + "it worked. It has charges, and when they run out the next attempt "
                                + "takes the bot.",
                        UnlockGate.ETHECOIN,
                        Balance.BOT_MOD_PROTECTOR_PRICE,
                        0,
                        "",
                        java.util.List.of("botnet", "modifier", "defence", "bot")),
                // ⚠ NOT PURCHASABLE. Parts come from recycling a chassis and from nowhere else — a
                // price would make repairing a frame reachable with money by two routes, one of them
                // priced against nothing. It is in the catalogue so the shop, the inventory and
                // `stat` all have a name and a description for it.
                new Offering(
                        BOT_FRAME_PARTS,
                        "BotFrame Parts",
                        "What is left of a chassis after it is broken down. Repairs a damaged frame, "
                                + "and is the only thing that does besides money.",
                        UnlockGate.SCHEMATIC,
                        BigInteger.ZERO,
                        0,
                        "Recovered by recycling a bot frame. Not sold.",
                        java.util.List.of("botnet", "material", "salvage", "bot")));
    }

    /** The one purchasable chassis — {@code docs/design/10} §2.1. */
    public static final String BOT_FRAME_V1 = "bot-frame-v1";

    /** What a recycled chassis yields, and the only non-money route to a repair. */
    public static final String BOT_FRAME_PARTS = "bot-frame-parts";

    public static final String BOT_MOD_SCRAMBLER = "bot-mod-scrambler";

    public static final String BOT_MOD_SLEEPY = "bot-mod-sleepy";

    public static final String BOT_MOD_DAMPENER = "bot-mod-dampener";

    public static final String BOT_MOD_THREADS = "bot-mod-threads";

    public static final String BOT_MOD_BEDAZZLE = "bot-mod-bedazzle";

    public static final String BOT_MOD_PROTECTOR = "bot-mod-protector";

    public static final String BOT_FN_KEYLOGGER = "bot-fn-keylogger";

    public static final String BOT_FN_INJECTOR = "bot-fn-injector";

    public static final String BOT_FN_MINER = "bot-fn-miner";

    public static final String BOT_FN_SIPPER = "bot-fn-sipper";

    public static final String BOT_FN_WATCHER = "bot-fn-watcher";

    /**
     * The catalogue id for a bot function, and the inverse.
     *
     * <h2>⚠ ONE mapping, for {@link #defenceOfferingId}'s reason</h2>
     *
     * Three callers need it — building a bot consumes the item, the panel says which module a socket
     * wants, and the market sells it. Written out three times, the day somebody adds a function is
     * the day the panel offers a socket nothing fills.
     */
    public static java.util.Optional<String> botFunctionOfferingId(
            io.github.stoicswe.eyeandsickle.protocol.game.BotFunction function) {
        if (function == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(switch (function) {
            case KEYLOGGER -> BOT_FN_KEYLOGGER;
            case INJECTOR -> BOT_FN_INJECTOR;
            case MINER -> BOT_FN_MINER;
            case SIPPER -> BOT_FN_SIPPER;
            case WATCHER -> BOT_FN_WATCHER;
        });
    }

    /** The function an item type provides, or empty when it is not a bot module. */
    public static java.util.Optional<io.github.stoicswe.eyeandsickle.protocol.game.BotFunction> botFunctionOf(
            String itemType) {
        if (itemType == null) {
            return java.util.Optional.empty();
        }
        for (var f : io.github.stoicswe.eyeandsickle.protocol.game.BotFunction.values()) {
            if (botFunctionOfferingId(f).filter(itemType::equals).isPresent()) {
                return java.util.Optional.of(f);
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * The catalogue id for a modifier, and the inverse — {@code docs/design/10} §5a.
     *
     * <p>⚠ ONE mapping, for {@link #defenceOfferingId}'s reason. Three callers need it: fitting one
     * consumes the item, the panel says which socket wants what, and the market sells it.
     */
    public static java.util.Optional<String> botModifierOfferingId(
            io.github.stoicswe.eyeandsickle.protocol.game.BotModifier modifier) {
        if (modifier == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(switch (modifier) {
            case EXE_NAME_SCRAMBLER -> BOT_MOD_SCRAMBLER;
            case SLEEPY -> BOT_MOD_SLEEPY;
            case DAMPENER -> BOT_MOD_DAMPENER;
            case EFFICIENT_MULTITHREADING -> BOT_MOD_THREADS;
            case BEDAZZLE_PRO -> BOT_MOD_BEDAZZLE;
            case PROTECTOR -> BOT_MOD_PROTECTOR;
        });
    }

    /** The modifier an item type provides, or empty when it is not one. */
    public static java.util.Optional<io.github.stoicswe.eyeandsickle.protocol.game.BotModifier> botModifierOf(
            String itemType) {
        if (itemType == null) {
            return java.util.Optional.empty();
        }
        for (var m : io.github.stoicswe.eyeandsickle.protocol.game.BotModifier.values()) {
            if (botModifierOfferingId(m).filter(itemType::equals).isPresent()) {
                return java.util.Optional.of(m);
            }
        }
        return java.util.Optional.empty();
    }

    /** The chassis tier an item type is, or 0 when it is not a frame. */
    public static int botFrameTier(String itemType) {
        if (itemType == null) {
            return 0;
        }
        for (int tier = 1; tier <= Balance.BOT_FRAME_TIER_MAX; tier++) {
            if (botFrameId(tier).equals(itemType)) {
                return tier;
            }
        }
        return 0;
    }

    /**
     * The schematic that authorises flashing the Firmware Implant.
     *
     * <p>Held in {@code GameSave.schematics}. ⚠ Never sold, never RNG-farmable — {@code 02} §2.2, and
     * {@code 11} §1 names where it comes from: deep inside Eye infrastructure, as a late-game
     * objective. Nothing in this class or in {@code Repac} grants it; the progression slice does.
     */
    public static final String FIRMWARE_IMPLANT_SCHEMATIC = "firmware-implant";

    /** The tool that must be stopped before mining firmware can be flashed. */
    public static final String MINING_TOOL = "mining";

    /**
     * The onion router that makes the TOR Marknet reachable.
     *
     * <h2>⚠ HEAT_STATE-gated, which means it is not for sale at ANY price and never will be</h2>
     *
     * {@code docs/design/02} §2.5: the heat-state gate is "vendor and contact <em>access</em>. Never
     * ownership." This item is the access — it does not defend, mine, breach or hold compute, and
     * owning it changes exactly one thing: a tab appears. It arrives in the COMS inbox when
     * {@code rules/BlackMarket} decides the player has been noticed, and there is no other route to
     * it. A price would turn a relationship into a transaction and would let anybody with money skip
     * the standing and the heat that are the whole gate.
     *
     * <p>⚠ <b>The real Tor is a privacy tool, not a crime tool</b>, and the fiction here should not
     * be read as claiming otherwise: what the module does in this game is resolve addresses ordinary
     * lookups will not, which is what onion routing actually provides. Journalists, whistleblowers
     * and people under censorship are its largest real user groups by some margin. If this ever gets
     * a {@code terms/} page, that is the fact the page has to carry.
     */
    public static final String TOR_MODULE = "tor-module";

    /**
     * The consumable that opens a crossing.
     *
     * <p>⚠ Named here and consumed by {@code NetRules.NETMAN_ITEM}, which must stay equal to it. Two
     * spellings of one id is how an item becomes unbuyable or unusable, silently, in a game where
     * both halves render perfectly.
     */
    public static final String NETMAN_ID = "net-man";

    /** Looks an offering up by id. */
    public static java.util.Optional<Offering> byId(String id) {
        return offerings().stream().filter(o -> o.id().equals(id)).findFirst();
    }

    /**
     * The catalogue id for a defence the player arms, from the {@code (kind, tier)} pair the rules use.
     *
     * <h2>⚠ ONE mapping, because there are three callers and they must not drift</h2>
     *
     * {@code LocalGameSession.armIntent} needs it to decide whether the player owns the thing;
     * {@code Views.firewall} needs it to say <em>why</em> a row is locked before the player clicks;
     * and the market needs the offering itself. Written out three times, the day somebody adds a
     * tier is the day the panel offers a row nothing sells and the refusal names an id the shop has
     * never heard of.
     *
     * <p>⚠ <b>The tier is only meaningful for the two ladders.</b> A canary has no tiers, so
     * {@code ("canary", 7)} is still {@code canary-token} rather than an error — the rules call this
     * with whatever the save carries, and a hand-edited tier must not make an owned defence
     * unrecognisable.
     *
     * @return the catalogue id, or empty when the kind is not a defence this game has
     */
    public static java.util.Optional<String> defenceOfferingId(String kind, int tier) {
        if (kind == null) {
            return java.util.Optional.empty();
        }
        // ⚠ Clamped rather than rejected, for the reason above. A ladder has exactly three rungs.
        int rung = Math.max(1, Math.min(3, tier));
        return switch (kind) {
            case "firewall" -> java.util.Optional.of("firewall-t" + rung);
            case "detection-array" -> java.util.Optional.of("detection-array-t" + rung);
            case "canary" -> java.util.Optional.of("canary-token");
            case "tarpit" -> java.util.Optional.of("tarpit");
            case "honeypot-stash" -> java.util.Optional.of("honeypot-stash");
            case "auto-counter-daemon" -> java.util.Optional.of("auto-counter-daemon");
            default -> java.util.Optional.empty();
        };
    }

    /**
     * The one defence a new character already owns — {@code GameEngine.newCharacter} grants it.
     *
     * <h2>⚠ Granted, not exempted</h2>
     *
     * Arming requires owning, with no special cases, so a starting rig needs something to own or the
     * FIREWALL panel opens as ten refusals and the player's first impression of the tool is that
     * none of it works. What this buys is a starting position; the item itself is an ordinary
     * ethecoin-gated one — losable, sellable and re-buyable like everything else on that gate
     * ({@code docs/design/02} §2.1). Selling it and going undefended is a decision the player is
     * allowed to make.
     */
    public static final String STARTING_DEFENCE = "firewall-t1";
}
