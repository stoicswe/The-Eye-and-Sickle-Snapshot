package io.github.stoicswe.eyeandsickle.engine;

import io.github.stoicswe.eyeandsickle.protocol.game.FeeTier;
import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import java.math.BigInteger;

/**
 * Every tunable number the solo runtime uses, in one place, each cited to the design document that
 * owns it.
 *
 * <h2>Why this class is a wall of constants instead of scattered literals</h2>
 *
 * This module is a <em>second implementation</em> of a subset of the game's rules — the server is the
 * first, and the authoritative one. Duplication is the price of the small footprint (see the module
 * description in {@code solo/pom.xml}), and the only honest way to pay it is to make the duplicated
 * values <em>findable</em>. A number inlined at its use site drifts silently. A number here, with the
 * document and section it came from on the line above it, drifts visibly: anyone re-tuning {@code
 * docs/design/03-economy.md} can grep this file and see the whole blast radius at once.
 *
 * <p>{@code docs/design/03-economy.md} is explicit that the economy figures are calibrated
 * <em>as a set</em>. Changing one here without re-reading that document is how a solo game ends up
 * subtly easier or harder than the real one, which is worse than either.
 *
 * <h2>What is deliberately NOT here</h2>
 *
 * Nothing in this class may be read as authority for online play. A federated server computes its own
 * numbers from its own configuration; these exist so a player with no network can still play a game
 * whose arithmetic matches the design. Solo characters never federate, so the two can never meet in a
 * transaction where a disagreement would matter.
 *
 * @see io.github.stoicswe.eyeandsickle.engine.rules.MiningRules
 */
public final class Balance {

    /**
     * An amount of ethecoin, as wei — the one way a price is written in this file.
     *
     * <h2>⚠ Written as the DECIMAL a designer reads, never as a wei literal</h2>
     *
     * A price is {@code ec("180")}, not {@code 180000000000000000000L}. Eighteen zeros is not a
     * number anybody can check by eye, and every balance figure in this file has to be checkable
     * against {@code docs/design/03-economy.md} — which quotes ethecoin, not wei. The constants were
     * previously hundredths ({@code 18_000L} meaning 180 EC), which had the same readability problem
     * on a smaller scale and produced exactly one confusion per new reader.
     */
    public static BigInteger ec(String amount) {
        return io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin.ofDecimal(amount)
                .wei();
    }

    private Balance() {}

    // ------------------------------------------------------------------ compute

    /**
     * A starting rig has 24 cycles — {@code docs/design/01-core-resources.md} §1.
     *
     * <h2>⚠ WAS 100 UNTIL 2026-08-06, AND THE DENOMINATOR MOVING IS THE POINT</h2>
     *
     * This is the denominator of the entire game, and it is now the <em>bottom</em> of a ladder
     * rather than the whole of it — see {@link #COMPUTE_RUNGS}. Every cost in this file was written
     * against 100 and <b>none of them changed</b>, on explicit direction: a Thorough Scan still costs
     * 35, which a starting rig cannot run at all, and a T3 Firewall still holds 15, which is nearly
     * two thirds of one.
     *
     * <p>⚠ <b>That is deliberate and it is the reason the ladder exists.</b> Costs that a new rig
     * cannot meet are what make capacity worth buying — the alternative, rescaling every cost to fit
     * 24, would leave the player exactly as capable at every rung and the upgrades would buy nothing
     * but bigger numbers. What a rung unlocks is <em>which operations are possible at all</em>.
     *
     * <p>⚠ So a starting rig is genuinely limited: it can run a Quick scan (5) and a Full one (15),
     * hold a T1 firewall (5) and a canary (1), and it cannot do those at the same time. Anything
     * priced above 24 is content behind the first rung, and anything above 32 is behind the second.
     */
    public static final long STARTING_CYCLES = 24L;

    /**
     * The compute ladder — {@code docs/design/01-core-resources.md} §1.1, added 2026-08-06.
     *
     * <p>24 → 32 → 48 → 64. The first rung is bought; the two above it are compiled from a schematic
     * and rare materials. See {@code rules/ComputeLadder}, and see {@link #COMPUTE_32_PRICE} for the
     * invariant amendment that lets the first one be bought at all.
     */
    public static final long[] COMPUTE_RUNGS = {24L, 32L, 48L, 64L};

    /**
     * What the first rung costs, in ethecoin.
     *
     * <h2>⚠ THIS IS AN AMENDMENT TO INVARIANT I1, THE GAME'S LOAD-BEARING RULE</h2>
     *
     * <b>I1 reads "compute is never purchasable with ethecoin"</b>, and
     * {@code docs/design/00-vision-and-pillars.md} §4 gives the reason: otherwise mining buys mining
     * capacity and the master scarcity collapses into a compounding flywheel. This constant is a
     * hole in it, made on explicit direction on 2026-08-06 and logged in {@code design/15} §3.
     *
     * <h2>⚠ THE AMENDMENT IS EXACTLY ONE RUNG WIDE, AND THAT NARROWNESS IS THE WHOLE SAFETY ARGUMENT</h2>
     *
     * The flywheel I1 protects against needs a <em>loop</em>: mine → buy capacity → mine faster →
     * buy more capacity. A single purchasable step cannot close that loop, because the step above it
     * cannot be bought at any price. Money moves a player from 24 to 32 <b>once, ever</b>, and then
     * the ladder is schematic-gated for the rest of its length. That is the difference between a
     * one-time head start and a compounding one.
     *
     * <p>⚠ <b>The narrowing is mechanical, not a promise.</b>
     * {@code ComputeLadderTest.onlyTheFirstRungIsForSale} fails the build if any other rung acquires
     * a price, and {@code CatalogueTest} already refuses a price on anything not ethecoin-gated. If
     * a second compute offering is ever ethecoin-gated, I1 has been abandoned rather than amended —
     * and the difference will be a red build rather than a conversation nobody had.
     *
     * <h2>Why 1200 EC</h2>
     *
     * It has to hurt, or the amendment buys a flywheel after all. Against a starting rig's own
     * income — 24 cycles at {@code SELF_MINING_WEI_PER_CYCLE_HOUR} is about 9.6 EC an hour — this is
     * roughly 125 hours of mining, which nobody will do. It is priced to be paid out of
     * <b>breach loot</b> ({@code design/03} §3's 45–65 EC hauls, so twenty-odd good breaches), which
     * puts the first rung behind the puzzle rather than behind the clock. That is the pillar the
     * whole game rests on, and it is what keeps the purchase from being a mining upgrade even
     * though it is bought with mining's currency.
     */
    public static final BigInteger COMPUTE_32_PRICE = ec("1200"); // 1200 EC

    /**
     * A live deployed miner reserves 3 cycles of the <em>deployer's</em> rig for its control channel,
     * permanently, while it runs — {@code docs/design/01-core-resources.md} §1 and {@code
     * docs/design/04-mining.md} §2.
     *
     * <p>Note which rig pays: the control channel is the deployer's cost, while the miner's actual
     * work is charged to the host (Invariant I6). Getting this backwards would make deployment free,
     * which is the single most load-bearing cost in the mining economy.
     */
    public static final long CONTROL_CHANNEL_CYCLES = 3L;

    // ------------------------------------------------------------------ mining

    /**
     * Self-mining yields 0.4 EC per cycle-hour — derived from {@code docs/design/03-economy.md} §1,
     * which prices a full 100-cycle rig at 40 EC/hr, and stated directly in {@code
     * docs/design/glossary.md}.
     *
     * <p>Expressed in minor units per cycle-hour so the arithmetic stays integral; see {@link
     * io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin} on why money is never a double.
     */
    public static final BigInteger SELF_MINING_WEI_PER_CYCLE_HOUR = ec("0.4");

    // ---------------------------------------------------------------- the chain
    //
    // Self-mining is a real proof-of-work simulation from 2026-07-27 (docs/design/04 §1.3). Every
    // constant below is either a REAL Bitcoin parameter reused verbatim, so the transfer in
    // docs/education/07 §3 is exact, or a game value DERIVED from the anchor above so that the
    // economy table in docs/design/03 §1 keeps its meaning without being re-tuned.
    //
    // ⚠ The anchor is the fixed point and the chain bends to it, never the other way round. If
    // SELF_MINING_WEI_PER_CYCLE_HOUR ever moves, chainNetworkHashrate() follows it
    // automatically and every figure here stays consistent. Hardcoding the network hashrate would
    // silently decouple the two and the first symptom would be an income table that is wrong.

    /**
     * Expected hashes per block at difficulty 1, as {@code 2^32}.
     *
     * <p>⚠ <b>Real, and reused exactly.</b> Bitcoin's difficulty-1 target is
     * {@code 0x00000000FFFF0000...}, which makes the expected number of hashes per block
     * {@code difficulty × 2^48 / 0xffff}, i.e. {@code difficulty × 2^32} to within one part in
     * 65 536. Keeping the real constant is what lets {@code docs/education/07} tell a player to
     * check the arithmetic against a live block explorer and have it come out.
     *
     * <p>Verified against the Bitcoin wiki's Difficulty page, 2026-07-27.
     */
    public static final double HASHES_PER_DIFFICULTY = 4294967296.0d; // 2^32

    /**
     * A cycle is worth 2^20 hashes per second.
     *
     * <p>The one purely conventional number here: it fixes what "hashrate" means in this fiction and
     * nothing downstream depends on its value, because difficulty is derived from it and cancels out
     * of every income figure. Chosen so that network difficulty lands in the hundreds — a number a
     * player can read and compare, rather than the fourteen-digit figure a real chain carries.
     */
    public static final long HASHES_PER_CYCLE_SECOND = 1L << 20;

    /**
     * The chain targets one block every <b>fourteen</b> minutes.
     *
     * <h2>⚠ Deliberately not Bitcoin's ten, and that is the point</h2>
     *
     * The <em>relation</em> {@code difficulty × 2^32} is real and reused exactly; the interval is
     * this chain's own. Ten minutes would have made ethecoin read as a Bitcoin reskin, and a chain
     * that is recognisably one real chain teaches players a specific product rather than the
     * mechanism. Fourteen minutes is unmistakably not Bitcoin's and behaves identically in every way
     * that matters.
     *
     * <p>⚠ <b>The economy does not move when this does.</b> {@link #chainNetworkHashrate()} is solved
     * from this and the {@code docs/design/03-economy.md} §1 anchor, and the product
     * {@code interval × networkHashrate} is fixed by that anchor — so lengthening the interval shrinks
     * the network in exact proportion and every income figure is unchanged. Verified: the 2352-cycle
     * network at ten minutes became a 1680-cycle network at fourteen, and a solo block still takes
     * 4.17 hours at 94 cycles.
     */
    public static final long CHAIN_TARGET_BLOCK_SECONDS = 840L;

    /**
     * Difficulty is recalculated every 1440 blocks.
     *
     * <p>Bitcoin uses 2016 <em>because</em> 2016 × ten minutes is two weeks. This chain keeps the
     * design property and drops the number: 1440 × fourteen minutes is also two weeks, to the hour.
     * A fortnight is long enough for luck to average out and short enough to answer a real change in
     * hashrate, which is the whole reason a retarget window has a length.
     *
     * <p>⚠ A shorter window is a noisier one: the relative spread of {@code n} random block times is
     * {@code 1/√n}, so 1440 jitters about 2.6% per retarget against 2016's 2.2%. That jitter is real
     * and is not a defect — see {@code ChainRules.retarget}.
     */
    public static final long CHAIN_RETARGET_BLOCKS = 1440L;

    /**
     * A retarget may never move difficulty by more than a factor of four in either direction.
     *
     * <p>⚠ <b>Real, and reused exactly.</b> Bitcoin clamps the adjustment to [1/4, 4] so that a
     * sudden hashrate collapse cannot strand the chain and a sudden influx cannot make blocks
     * instantaneous. Verified against the Bitcoin wiki's Difficulty page, 2026-07-27.
     */
    public static final double CHAIN_RETARGET_CLAMP = 4.0d;

    /**
     * A block pays 160 EC.
     *
     * <p><strong>[PROPOSAL]</strong>, and the one knob that sets how much of a lottery solo mining
     * is. It is a <em>lump</em>, not a rate: raising it makes solo blocks rarer and larger without
     * changing anyone's expected income by one minor unit, because
     * {@link #chainNetworkHashrate()} absorbs the change. That is the knob to reach for if solo
     * mining feels too steady or too hopeless — and it is the <b>only</b> one that does not disturb
     * {@code docs/design/03}.
     *
     * <p>At 160 EC a full 100-cycle rig expects a solo block roughly every <b>3 hours 55 minutes</b>
     * — about a 22% chance in any given hour, and about 6% at a quarter rig. That is the reading of
     * "a percent chance of a large payout, and you need a lot of cycles to make it likely".
     */
    public static final BigInteger BLOCK_SUBSIDY_WEI = ec("160");

    /**
     * The pool takes 2%.
     *
     * <p>Real in shape and in size: pay-per-share pools charge roughly 2–4% precisely because the
     * operator is buying the miner's variance and must pay for accepted shares through an unlucky
     * streak in which the pool finds fewer blocks than it owes. Two percent is the bottom of the
     * observed range, which keeps the choice close rather than obvious. Verified against published
     * pool payout-scheme documentation (f2pool, minerstat), 2026-07-27.
     *
     * <p>⚠ <b>This is why solo pays more in expectation</b>, and that is not a bug to balance away.
     * A pool that paid the same as solo would be free insurance and nobody sane would mine solo; the
     * fee is what makes the choice a trade rather than a preference.
     */
    public static final double POOL_FEE = 0.02d;

    /** The pool's cut, as basis points, for the wire and the readout. */
    public static final int POOL_FEE_BASIS_POINTS = (int) Math.round(POOL_FEE * 10_000);

    /**
     * The pool retunes each miner's share difficulty to land a share every 30 seconds.
     *
     * <p>Real, and it is called <b>vardiff</b>. A pool sets each miner's share target from that
     * miner's own hashrate so that shares arrive at a steady rate whatever the rig — a small miner
     * gets an easy target and a large one a hard target, and both submit at about the same pace. It
     * is the reason a pool smooths income for a 10-cycle rig as effectively as for a 100-cycle rig,
     * which a fixed share difficulty would not do.
     *
     * <p>Thirty seconds is a game value. It sets the variance ratio between the two modes directly:
     * pooled income has {@code soloInterval / 30} times less variance, which at a full rig is a
     * factor of about <b>470</b>.
     */
    public static final double POOL_SHARE_SECONDS = 30.0d;

    /**
     * How loud a pooled rig is, in cycle-equivalents, at the reference 30-second share interval.
     *
     * <h2>⚠ Pooled mining is audible and solo mining is not, and that is the right way round</h2>
     *
     * A pooled miner holds an open connection to a pool server and pushes a share up it every thirty
     * seconds, forever. That is outbound traffic to a third party, which is precisely what
     * {@code NoiseRules} counts. A <b>solo</b> miner talks to nobody: the work is local grinding, and
     * the only thing that ever leaves the rig is a block announcement once every few hours. So solo
     * is genuinely silent and pooled genuinely is not.
     *
     * <p><b>Invariant I4 is not violated.</b> I4 makes self-mining immune to detection and seizure and
     * gives it <em>zero heat</em> — and it still has all three. Noise is a rate, heat is what an act
     * leaves behind, and nothing converts this trickle into heat: heat is charged at breach
     * resolution and by counter-hacks, never off the ambient meter. What I4 protects is that going
     * hot cannot take the floor away, and a rig reading 2% on the noise meter has lost nothing.
     *
     * <p>⚠ <b>Deliberately tiny, and deliberately flat.</b> Two cycles on a hundred-cycle rig is 2%
     * of the meter against a sweep's 35 — a sweep is more than seventeen times louder. And it does
     * <em>not</em> scale with allocation, because a share is a small fixed packet however much
     * hashing produced it: doubling your cycles doubles your income and changes your traffic not at
     * all. That is real, and it means the noise-conscious play is to pick a pool, not to mine less.
     *
     * <p>It <em>does</em> scale with how often the pool wants shares, which is the one place a pool's
     * share interval earns its keep as more than flavour — {@code MERIDIAN CLEARING} asks for a share
     * every fifteen seconds and is twice as loud as the reference for it.
     */
    public static final long POOL_SHARE_NOISE_CYCLES = 2L;

    /**
     * How loud buying from a server you do not own is, in cycles, for as long as the download runs.
     *
     * <h2>⚠ The asymmetry is the rule, and it is about WHOSE MACHINE is watching</h2>
     *
     * Noise is outbound traffic to a third party ({@code rules/NoiseRules}). So:
     *
     * <ul>
     *   <li><b>Your own home server</b> — silent. The traffic goes to infrastructure you control, and
     *       an operator does not surveil themselves. This is the same reasoning as <b>I9</b>:
     *       defending your own rig never makes you wanted.
     *   <li><b>A LAN server</b> — loud. It is somebody else's machine on somebody else's network, and
     *       a LAN identity has no proof behind it; a purchase there is exactly the kind of outbound
     *       transaction the Eye reads.
     *   <li><b>A foreign federated server</b> — loud, for the same reason. Shopping somewhere you do
     *       not belong is the observable act.
     *   <li><b>Solo</b> — loud, matching LAN, because the vendor is not the player's own
     *       infrastructure in the fiction either.
     * </ul>
     *
     * <p>⚠ It rides on the download TASK rather than being a heat charge, so it is present-tense and
     * ends by itself when the transfer does. Noise is a rate; heat is what a loud act leaves behind,
     * and a purchase is not an aggression — turning it into heat would make shopping make you wanted.
     *
     * <p>⚠ Rated at the pool-share tier rather than the sweep tier. A purchase is one short outbound
     * conversation, not an interrogation of somebody's estate, and {@code docs/design/08} §1 rates
     * noise by what the act actually looks like from outside.
     */
    public static final long MARKET_FOREIGN_PURCHASE_NOISE_CYCLES = 3L;

    /**
     * How long a Shadow Market seller has to deliver before it costs them.
     *
     * <h2>⚠ Long enough to be a promise, short enough to be a deadline</h2>
     *
     * Six hours spans a session boundary — a seller who lists, logs off and comes back can still
     * honour it — which is the point: the obligation is meant to survive the client being closed, so
     * that closing the client is not a way to escape one. Shorter would punish anybody who logs off;
     * much longer would make defaulting cost nothing that mattered, since the buyer has already lost
     * the money either way.
     */
    public static final long SHADOW_FULFILMENT_HOURS = 6L;

    /**
     * AnonShare's cut of every trade, in basis points — charged on the way IN and on the way OUT.
     *
     * <h2>⚠ THIS IS THE ONLY THING BOUNDING A REAL-PRICE FAUCET</h2>
     *
     * Every other market in this game has a ceiling derived from a number the game controls. This one
     * tracks prices the game does not control and cannot predict, so there is no ceiling to derive —
     * a player who buys before a real rally and sells after it has created ethecoin out of an
     * external event.
     *
     * <p>The commission is what makes that a <em>gamble</em> rather than a <em>printer</em>: charged
     * both ways, a round trip has to beat roughly twice this before it makes anything, so the
     * expected value of trading noise is negative and grinding it is a losing strategy. Wins stay
     * real and stay earned.
     *
     * <p>⚠ Raising the stakes elsewhere is fine; <b>lowering this towards zero re-opens the
     * faucet</b>, and it will not look like it has — every screen will still render correctly.
     */
    public static final int BROKERAGE_COMMISSION_BP = 65;

    /**
     * The pool settles up every sixty seconds.
     *
     * <h2>⚠ This exists because the ledger is the artefact, not because the maths needed it</h2>
     *
     * Crediting each share the instant it lands is arithmetically identical and puts <b>120 rows an
     * hour</b> into {@code ledger(1)} — a readout whose shipped page calls itself "the only record of
     * where your money went". Buried under a wall of identical 0.31 EC rows it records nothing, which
     * is {@code alert-fatigue(7)} again in the one place a player audits.
     *
     * <p>Real pools do exactly this: shares are credited to an internal balance continuously and paid
     * out on a schedule. Sixty seconds is a game value — short enough that the balance visibly moves,
     * long enough that an hour of mining is sixty readable rows instead of a screenful of noise.
     *
     * <p>⚠ <b>The credit and the ledger row happen together, always.</b> Crediting continuously and
     * ledgering periodically would leave the balance ahead of the last row — and
     * {@code docs/design/04-mining.md} §3.1 makes "two readouts disagree" the way a player detects an
     * intruder. Training them to ignore that would cost more than the tidy ledger bought.
     *
     * <p>A <b>solo</b> block never waits: a block is a real coinbase and earns its own row.
     */
    public static final long POOL_SETTLE_SECONDS = 60L;

    // ---------------------------------------------------------------- the fee market

    /**
     * The block height a new character joins the chain at.
     *
     * <p>124 blocks of history exist before the player does — about a day at fourteen minutes — and
     * every one of them is inspectable in the explorer. A chain that began at the player's first
     * session would say it had been waiting for them, which is the opposite of what a shared ledger
     * is. <strong>[PROPOSAL]</strong>.
     */
    public static final long CHAIN_START_HEIGHT = 124L;

    /**
     * Transactions a block can hold — the gas limit divided by the cost of one transfer.
     *
     * <p>This is the number that makes a fee market exist at all. A block that could hold every
     * pending transaction would make fees pointless, and one that held two would make them
     * everything. Two hundred against a mempool that runs a few hundred deep means an ordinary
     * transaction waits a block or two and a priority one does not.
     */
    public static final int BLOCK_TRANSACTION_LIMIT = 200;

    /**
     * The fee floor, in minor units: what {@link FeeTier#ECONOMY} pays.
     *
     * <h2>⚠ Deliberately small enough not to be an economy change</h2>
     *
     * {@code docs/design/03-economy.md} §4 lists the sinks the economy is balanced against, and this
     * is not one of them. Two minor units — 0.02 EC — against a 40 EC/hr income is a rounding error
     * by design: the fee exists to <b>order a queue</b>, not to drain a balance. If it ever grows
     * enough to matter it becomes a sink and §4 has to know about it.
     */
    public static final BigInteger FEE_ECONOMY_WEI = ec("0.02");

    /** What {@link FeeTier#STANDARD} pays. Still negligible against income; still enough to sort on. */
    public static final BigInteger FEE_STANDARD_WEI = ec("0.06");

    /** What {@link FeeTier#PRIORITY} pays. Fifteen times the floor and still under a cycle-minute. */
    public static final BigInteger FEE_PRIORITY_WEI = ec("0.30");

    /** What a tier costs, in wei. */
    public static BigInteger feeFor(FeeTier tier) {
        return switch (tier == null ? FeeTier.STANDARD : tier) {
            case ECONOMY -> FEE_ECONOMY_WEI;
            case STANDARD -> FEE_STANDARD_WEI;
            case PRIORITY -> FEE_PRIORITY_WEI;
        };
    }

    /**
     * How deep the NPC mempool runs, on average.
     *
     * <p>Enough that a block cannot clear it in one go — otherwise there is no queue to be at the
     * front of, and the fee tiers would all confirm identically. Three hundred against a 200-slot
     * block means a standard transaction waits roughly a block and an economy one several.
     */
    public static final int MEMPOOL_BASELINE_DEPTH = 300;

    /**
     * What a block's fees are worth to whoever mines it, on average — <b>derived, never chosen</b>.
     *
     * <h2>⚠ This is mining income, and since 2026-07-27 it is real income</h2>
     *
     * A block pays its miner {@code subsidy + fees}, as on any real chain. Before that date the fees
     * players paid into the mempool were debited and then ceased to exist, which made the fee market
     * a pure sink and left the explorer's "fees 0.38 EC" naming money nobody ever received.
     *
     * <h2>Derived from the two distributions rather than measured and pasted</h2>
     *
     * A block carries {@code 12 + U[0, LIMIT − 12)} transactions and each pays
     * {@code FEE_ECONOMY + U[0, FEE_PRIORITY − FEE_ECONOMY]}, so the expectation is the product of
     * the two means: {@code 105.5 × 16 = 1688}. Writing the number here instead would be a fourth
     * copy of the fee ladder, silently wrong the first time a tier moved —
     * {@code MiningChainTest} asserts this against 20 000 simulated blocks for exactly that reason.
     *
     * <p>⚠ It is <b>10.55% of the subsidy</b>, so it moves mining income by that much. That was a
     * deliberate call on 2026-07-27: {@code chainNetworkHashrate()} was <em>not</em> re-solved to
     * absorb it, so self-mining now pays about a tenth more than {@code design/03} §1's
     * 0.40 EC/cycle-hour anchor. See `03` §1.1 for what that re-rated and what it did not.
     */
    /**
     * The fees an average block carries, in wei.
     *
     * <h2>⚠ BigDecimal, not double, and the reason is now visible on screen</h2>
     *
     * This used to return a {@code double} and that was harmless while an amount had two decimal
     * places: any float noise sat far below the last digit anybody saw. At 18 decimals the formatter
     * prints every significant digit, so a double result of {@code 3.0000000000000004e19} wei would
     * render as <b>{@code 30.000000000000004 EC}</b> — a plausible-looking figure with four digits of
     * arithmetic residue in it. Doubles hold integers exactly only below 2^53 (~9×10^15), and a wei
     * amount passes that at nine thousandths of an ethecoin.
     *
     * <p>The means themselves are genuinely fractional — both are of a {@code floorMod} over a
     * half-open range, so each sits {@code (span − 1) / 2} above its floor, i.e. 93.5 transactions
     * above 12 — so the calculation is done in {@link BigDecimal} and lands on an exact wei count.
     */
    public static BigInteger expectedBlockFeesWei() {
        java.math.BigDecimal meanTransactions =
                java.math.BigDecimal.valueOf(12 + (BLOCK_TRANSACTION_LIMIT - 12 - 1) / 2.0d);
        java.math.BigDecimal meanFee = new java.math.BigDecimal(
                FEE_ECONOMY_WEI.add(FEE_PRIORITY_WEI.subtract(FEE_ECONOMY_WEI).divide(BigInteger.TWO)));
        return meanTransactions.multiply(meanFee).toBigInteger();
    }

    /**
     * The rest of the chain's hashrate, in hashes per second — <b>derived, never chosen</b>.
     *
     * <h2>Why this is a derivation</h2>
     *
     * A miner's income is {@code subsidy × ownHashrate / networkHashrate} per block interval. Three
     * of those four are already fixed: the subsidy above, the rig's hashrate, and the 0.4 EC per
     * cycle-hour that {@code docs/design/03-economy.md} §1 prices the whole economy against. So the
     * network's hashrate is not a free parameter — it is whatever value makes the other three agree,
     * and writing it down as a constant would be writing down an answer that can silently stop being
     * the answer.
     *
     * <p>Solving for it at the <em>pooled</em> rate rather than the solo rate is deliberate:
     * {@code 03} §1's 40 EC/hr is described as a <b>floor</b>, and a floor has to be the guaranteed
     * figure. So pooled pays exactly the documented rate and solo pays it back divided by
     * {@code (1 - fee)} — marginally more, in exchange for all of the variance.
     *
     * <p>It works out at about 2352 cycles: the player's full rig is roughly 4% of the chain. That
     * is a small network, which is correct for a resistance's chain and is what keeps a solo block
     * reachable at all.
     */
    public static double chainNetworkHashrate() {
        // ⚠ A RATIO of two wei amounts, so the scale cancels and a double is exact enough — the
        // subsidy and the per-cycle-hour rate are both in wei and divide into a pure number around
        // 2352. Converting either to double on its own would be the lossy step; dividing them is not.
        double subsidyOverRate = new java.math.BigDecimal(BLOCK_SUBSIDY_WEI)
                .divide(new java.math.BigDecimal(SELF_MINING_WEI_PER_CYCLE_HOUR), java.math.MathContext.DECIMAL64)
                .doubleValue();
        double cycles = subsidyOverRate * (1.0d - POOL_FEE) * 3600.0d / CHAIN_TARGET_BLOCK_SECONDS;
        return cycles * HASHES_PER_CYCLE_SECOND;
    }

    /**
     * The difficulty that holds {@link #CHAIN_TARGET_BLOCK_SECONDS} at a given network hashrate.
     *
     * <p>The real relation, rearranged: expected seconds to a block is
     * {@code difficulty × 2^32 / hashrate}, so holding the interval means
     * {@code difficulty = interval × hashrate / 2^32}.
     */
    public static double chainDifficultyFor(double networkHashrate) {
        return CHAIN_TARGET_BLOCK_SECONDS * networkHashrate / HASHES_PER_DIFFICULTY;
    }

    /**
     * A deployed miner's on-host yield buffer caps at 4 hours — {@code docs/design/04-mining.md} §2.3
     * and {@code docs/design/glossary.md}.
     *
     * <p>This cap is what stops offline income scaling with time away, and it is the prize an attacker
     * takes when they crack a miner. {@code docs/design/15-open-questions.md} OQ-4 flags the figure as
     * a starting value pending session-length telemetry.
     */
    public static final long YIELD_BUFFER_HOURS = 4L;

    /**
     * How long the rig keeps hashing after the client closes — {@code docs/design/04-mining.md} §1.2.
     *
     * <h2>⚠ This is Invariant I5, amended on 2026-07-29, and it used to be zero</h2>
     *
     * I5 read "self-mining runs online-only". It now reads "stops a bounded time after the client
     * closes; all offline income is capped, never proportional to absence" — because the cap, not the
     * online-only rule, was always the thing doing the work. The argument against offline accrual was
     * that absence would out-earn play on an income stream that is also zero-heat and unseizable
     * (I4); past this window a longer absence is worth exactly nothing more, so there is no absence to
     * optimise toward, and an hour played always beats an hour away because play is uncapped.
     *
     * <h2>⚠ Deliberately the same figure as {@link #YIELD_BUFFER_HOURS}, and deliberately not the
     * same constant</h2>
     *
     * They agree today and they are not the same quantity. This one bounds how long a rig the player
     * owns goes on working; that one bounds how much a miner sitting on somebody else's disk may hold
     * before an attacker's prize stops growing ({@code 04} §5.1's crack timing bet is priced on it).
     * Aliasing them would mean a re-tune of the crack window silently re-tuning self-mining, which is
     * the class of coupling {@code CLAUDE.md} warns about for the {@code design/03} tables.
     *
     * <p>⚠ What separates self-mining from a deployed miner is now <b>exposure, not duration</b>: a
     * miner spends the <em>host's</em> compute (I6), so five of them buffer five hosts' worth of the
     * same four hours — and their buffer can be seized, where self-mining cannot be touched.
     */
    public static final long OFFLINE_MINING_HOURS = 4L;

    /** The same window in seconds, which is the unit every caller actually wants. */
    public static long offlineMiningSeconds() {
        return OFFLINE_MINING_HOURS * 3600L;
    }

    /**
     * What an absent rig's hashrate counts for — in <em>every</em> mining mode.
     *
     * <h2>Why an absence pays at half weight</h2>
     *
     * {@link #OFFLINE_MINING_HOURS} caps <em>how long</em> the rig goes on hashing; this caps
     * <em>how well</em> it does while it is. They are separate levers on purpose. The window is what
     * stops a longer absence being worth more — past four hours nothing accrues, so there is no
     * absence to optimise toward. This is the second half of the same argument: within that window,
     * time away should not be worth as much as time played, or the four hours become a thing to
     * collect rather than a courtesy. Play stays strictly better per hour, and it stays better per
     * hour <em>inside</em> the buffered window as well as outside it.
     *
     * <h2>⚠ AMENDED 2026-08-06: it was solo-only and it was named for that</h2>
     *
     * It shipped as {@code OFFLINE_SOLO_WIN_WEIGHT} and this javadoc argued pooled mining had to be
     * exempt — "a pool's hashrate is the pool's, it competes whether or not one member's client is
     * open". That is still true of the <b>pool</b>, and it is why the pool's draw is still untouched
     * (see below). What it does not justify is exempting the <b>player's</b> pooled income, which is
     * what the exemption actually did: a pooled character collected four hours at full rate for
     * closing the client while a solo one collected four at half. Extended to all three modes on
     * explicit direction — {@code docs/design/15-open-questions.md} §3.
     *
     * <p>⚠ <b>One constant, deliberately, and it is the reason for the rename.</b> The quantity is
     * "what this rig's hashrate is worth while nobody is at the keyboard", and it is the same
     * question in all three modes. Two constants would be two figures to re-tune and one to forget,
     * which is how solo and pooled offline income came to differ by a factor of two in the first
     * place.
     *
     * <h2>⚠ Three modes, three places, because the player's hashrate enters three ways</h2>
     *
     * <ul>
     *   <li><b>Solo</b> — {@code ChainRules.drawWinner} scales the player's own share of the draw.
     *       It has to be the draw: a solo block pays the whole subsidy plus its fees, so there is no
     *       cut to scale, and paying half of one would put a block in the explorer whose miner was
     *       credited less than the block was worth.
     *   <li><b>PPLNS</b> — {@code MiningRules.runSelfMining} scales the player's cut of each pool
     *       block that carries {@code Won.offline()}.
     *   <li><b>PPS</b> — the same method scales the share clock's accrual. A share pool pays per
     *       accepted share out of its own balance whether or not anybody found a block, so the draw
     *       is not a lever on PPS income at all and the clock is the only one there is.
     * </ul>
     *
     * <h2>⚠ The POOL'S draw is untouched, and that is not an oversight</h2>
     *
     * The obvious implementation — halve the chosen pool's {@code networkShare()} during a fill —
     * was rejected. A pool does not lose half its hashrate because one member logged off, so it
     * would hand the freed probability to the unpooled population for four hours and leave a block
     * explorer reporting that this player's pool mysteriously underperforms during their absences.
     * It would also halve the pool blocks written to the CONTRIBUTOR record under <b>PPS</b> while
     * reducing PPS income by exactly nothing, since those rows credit zero by construction. Scaling
     * the player's own share of the proceeds costs the chain nothing and reaches every scheme.
     *
     * <h2>⚠ Invariants this must not disturb</h2>
     *
     * <b>I4</b> — self-mining is still immune to detection and seizure and still generates zero heat;
     * a smaller number is not a risk. <b>I5</b> — offline income remains capped and non-proportional,
     * and this only lowers the cap's value. <b>I2</b> — nothing here is purchasable, so no ceiling
     * moved. <b>I6</b> — deployed miners are not in scope: they spend the host's compute and settle
     * out of their own buffer.
     *
     * <p>⚠ <b>It must never change how much RNG is consumed on the chain draw.</b> That draw is one
     * {@code nextDouble} per block whatever the outcome; the solo branch scales the threshold it is
     * compared against, not the number of draws. A generator whose consumption varied with the mode
     * would stop a stored seed being a replay — {@code Rng}'s stated contract, and the reason
     * {@code drawWinner} rolls even for blocks the rig is not contesting. The PPS share clock is a
     * separate stream and already consumes a count that varies with elapsed time.
     *
     * <p>⚠ <b>Deliberately invisible.</b> No readout names it and none should: the SYNCHRONIZING
     * screen reports what the chain did, and a player comparing blocks-won to hashrate share over a
     * few sessions is doing arithmetic on a Poisson process with a sample size of about two.
     */
    public static final double OFFLINE_MINING_WIN_WEIGHT = 0.5d;

    /**
     * The most blocks one synchronisation will fill in, block by block.
     *
     * <p>At a 14-minute interval this is a little over five years of absence, so it is a runaway
     * backstop rather than a limit anybody reaches — the loop is a few arithmetic operations per
     * block and 200 000 of them cost single-digit milliseconds. It exists because the alternative to
     * a bound is a save whose {@code lastPlayedAt} was hand-edited to 1970 hanging the client on
     * load, and because {@code advanceNetwork} already takes the same precaution per tick.
     */
    public static final int CHAIN_SYNC_BLOCK_LIMIT = 200_000;

    /**
     * How much more a resold upgrade fetches per major version, in percent.
     *
     * <p>⚠ <b>The only mechanical consequence a version has.</b> A newer build is worth more and
     * supersedes an older one; it is not a better tool, because a capability that rises with the
     * hardness of the machine you take it off would be a ceiling reachable by grinding with no gate
     * on it — Invariant <b>I2</b>, and <b>I3</b> as well since the item would then sit behind two
     * gates. See {@code solo/rules/Versions} and {@code protocol/game/UpgradeVersion}.
     *
     * <p>Twelve percent per major, so the spread from a tier-1 desktop to a tier-5 estate is about
     * 1.5× — enough that a player prefers the harder target, not so much that raiding stops being
     * about what the tool is. {@code Versions.resaleWei} clamps the result below retail
     * whatever this is set to, because a resale above retail would make buy-to-resell a money printer
     * and that must not be one re-tune away.
     */
    public static final long UPGRADE_VERSION_RESALE_PERCENT_PER_MAJOR = 12L;

    /**
     * The build the vendor ships.
     *
     * <p>⚠ Deliberately in the MIDDLE of the tier ladder, and that placement is the loop. If the
     * market sold the newest build there would be no reason to raid for one; if it sold the oldest,
     * buying would be strictly dominated and the catalogue would be a trap. At three, a tier-4 or
     * tier-5 estate carries something the shop does not, a tier-1 desktop carries something worse
     * than the shop, and both facts are things a player can discover and act on.
     *
     * <p>It buys no capability either way — see {@link #UPGRADE_VERSION_RESALE_PERCENT_PER_MAJOR}.
     * What a newer build is worth is resale and supersession, so this decides how good a deal the
     * shop is, not how good the tool is.
     */
    public static final int MARKET_UPGRADE_VERSION_MAJOR = 3;

    /**
     * What the Firmware Implant image costs at the vendor.
     *
     * <h2>⚠ Firmware is priced well above software, and this is the cheapest firmware there is</h2>
     *
     * 180 EC — roughly three times the deepest sweep tier (55 EC) and above every other single
     * purchase in the catalogue. Three reasons, and the third is the one that sets the floor:
     *
     * <ul>
     *   <li>It is the payload of a <b>permanent</b> capability, not a consumable. Everything else the
     *       vendor sells is losable and replaceable ({@code docs/design/02} §2.1); this is not.
     *   <li>It is inert without the schematic, so a player who buys it speculatively has spent real
     *       money on a file — and the price has to make that a decision rather than a shrug.
     *   <li>⚠ It must stay <b>expensive enough that stealing one is worth the breach</b>. A firmware
     *       image is deliberately available both ways ({@code docs/design/01} §6's raiding route), and
     *       if buying were cheap the raid would be pointless — which would quietly delete the reason
     *       the two-part requirement exists at all.
     * </ul>
     *
     * <p>⚠ It buys <b>no ceiling</b>. The schematic is the gate and no amount of ethecoin produces
     * one ({@code 02} §2.2), so this price can move freely without touching <b>I2</b>. What it must
     * never become is cheap enough to make the raid route dead content.
     */
    public static final BigInteger FIRMWARE_IMPLANT_IMAGE_PRICE = ec("180"); // 180 EC

    /**
     * How long flashing firmware takes.
     *
     * <h2>⚠ Long enough to be a commitment, and it is a commitment with the tool DOWN</h2>
     *
     * Ninety seconds. Every other install in this game is instantaneous because the interesting wait
     * — somebody else's uplink — already happened during the download. Firmware is the exception on
     * purpose: the mining tool is frozen for the whole flash, so the cost is real income foregone
     * rather than a progress bar to watch.
     *
     * <p>The figure is bounded at both ends. Much shorter and freezing the tool costs nothing, so the
     * "stop the tool first" rule becomes ceremony. Much longer and a player flashing between sessions
     * is simply denied their rig, which is a punishment rather than a decision. At a minute and a
     * half it is roughly six blocks of self-mining given up — visible on the ledger, and small enough
     * that nobody plans a day around it.
     *
     * <p>⚠ It is <b>not</b> derived from the image's size. A download is bounded by the far end's
     * uplink and its duration should track bytes; a flash is bounded by the device writing its own
     * memory, and a bigger image does not make a slower flash on any hardware a player has met.
     */
    public static final long FIRMWARE_FLASH_SECONDS = 90L;

    // ------------------------------------------------------------------ scanning

    /**
     * Scan tiers cost 5, 15 and 35 cycles — {@code docs/design/04-mining.md} §3.2.
     *
     * <p>What the player buys with the difference is <em>signal strength</em>, not certainty. The
     * curriculum leans on this hard: {@code docs/education/08-detection-and-defence.md} §3.5 uses
     * these three numbers to teach the false-positive trade, so changing them changes a teaching
     * example as well as a cost.
     */
    /**
     * Cycles one open shell session holds, for as long as it is open.
     *
     * <p>Small on purpose — two cycles is a twentieth of a starting rig, so the first few sessions
     * are effectively free and the twentieth is not. That shape is the whole reason the cost exists
     * rather than a cap: {@code docs/design/00} §4's meta-rule is that compute is the master
     * scarcity, and "how many machines can I sit on at once" should be a question the rig answers.
     * A hard cap would answer it with a number nobody could argue with, which is worse.
     *
     * <p>⚠ Held, never spent, and it does <b>not</b> enter thermal recovery on close — see
     * {@code SessionRules.close}. Recovery is the price of having <em>worked</em> the silicon
     * ({@code docs/design/01} §1.3), and an idle shell has not.
     */
    // ── The link (docs/design/15 — TR-1 is open: whether this is ever upgradable) ─────────────
    //
    // ⚠ DECIMAL bits, because that is what a network is measured in. A file is measured in bytes,
    // and the two units meeting is the single most common place people get transfer arithmetic
    // wrong — 150 Mbit/s is 18.75 MB/s, not 150. Keeping the constants in bits and converting once,
    // here, is what stops that error being made twice in different places.

    /** Downstream, bits per second. Gigabit. */
    public static final long LINK_DOWN_BITS = 1_000_000_000L;

    /** Upstream, bits per second. Asymmetric, the way a real consumer line is. */
    public static final long LINK_UP_BITS = 150_000_000L;

    /**
     * How fast this rig unpacks a {@code .tar.xz}.
     *
     * <h2>⚠ SLOWER THAN THE LINK, on purpose, and that is the whole teaching</h2>
     *
     * {@link #downloadBytesPerSecond()} is 18.75 MB/s. This is deliberately below it, so unpacking
     * an archive takes visibly longer than fetching it did — which is the true and slightly
     * surprising thing about {@code xz}: the format trades expensive decompression for small files,
     * and on a fast line the squeeze, not the wire, is what you wait for.
     *
     * <p>⚠ Re-tuning this means re-checking {@link #transferTime} against it. The relationship
     * between the two is the fact being taught; a decompression rate above the link speed would
     * teach the opposite, with nothing anywhere reporting a problem.
     */
    public static final long EXTRACT_BYTES_PER_SECOND = 6_000_000L;

    /**
     * Connection setup, in milliseconds, before a byte moves.
     *
     * <p>Real: a handshake, a key exchange and a request happen before any payload does. It exists
     * here for an honest reason rather than a cosmetic one — without it a four-kilobyte document
     * transfers in under a millisecond and the progress readout is a flicker, which would read as
     * the game failing to do anything rather than as the transfer being genuinely instant.
     */
    public static final long TRANSFER_SETUP_MS = 400L;

    /**
     * How fast a download from another machine actually goes, in bytes per second.
     *
     * <p>⚠ <b>The bottleneck is the REMOTE END'S UPLOAD, not your download.</b> Gigabit down is
     * irrelevant when the machine you are pulling from can only push 150 Mbit — so every transfer in
     * this game runs at 18.75 MB/s no matter how good your line is. That is the single most useful
     * true thing about file transfers that most people have experienced and few have had named for
     * them, and it is why the two constants above are different numbers rather than one.
     */
    public static long downloadBytesPerSecond() {
        return Math.min(LINK_DOWN_BITS, LINK_UP_BITS) / 8L;
    }

    /** How long moving {@code bytes} takes, setup included. Never zero — see the setup constant. */
    public static java.time.Duration transferTime(long bytes) {
        long payloadMillis = Math.max(0L, bytes) * 1000L / Math.max(1L, downloadBytesPerSecond());
        return java.time.Duration.ofMillis(TRANSFER_SETUP_MS + payloadMillis);
    }

    public static final long SESSION_CYCLES = 2L;

    public static final long SCAN_QUICK_CYCLES = 5L;

    public static final long SCAN_FULL_CYCLES = 15L;
    public static final long SCAN_THOROUGH_CYCLES = 35L;

    /** Wall-clock duration of each scan tier, in seconds — {@code docs/design/04-mining.md} §3.2. */
    public static final long SCAN_QUICK_SECONDS = 30L;

    public static final long SCAN_FULL_SECONDS = 120L;
    public static final long SCAN_THOROUGH_SECONDS = 360L;

    // ------------------------------------------------------------------ defense

    /**
     * Standing compute reservations for armed defences — {@code docs/design/09-defense-and-hardening.md}
     * §1.
     *
     * <p>These are the numbers behind that document's §3 observation that a fully paranoid loadout
     * costs more than a starting rig has. That tension is the point and must survive re-tuning: if
     * every defence can be armed at once, the defensive-budget decision disappears and so does the
     * lesson in {@code docs/education/08-detection-and-defence.md} §3.8.
     */
    public static final long DEFENSE_FIREWALL_T1_CYCLES = 5L;

    public static final long DEFENSE_FIREWALL_T2_CYCLES = 10L;
    public static final long DEFENSE_FIREWALL_T3_CYCLES = 15L;
    public static final long DEFENSE_CANARY_CYCLES = 1L;
    public static final long DEFENSE_TARPIT_CYCLES = 8L;
    public static final long DEFENSE_HONEYPOT_STASH_CYCLES = 12L;
    public static final long DEFENSE_AUTO_COUNTER_CYCLES = 18L;
    public static final long DEFENSE_DETECTION_ARRAY_T1_CYCLES = 6L;
    public static final long DEFENSE_DETECTION_ARRAY_T2_CYCLES = 14L;
    public static final long DEFENSE_DETECTION_ARRAY_T3_CYCLES = 25L;

    // ================================================================== the defence minigame (19)
    //
    // ⚠ EVERY FIGURE HERE IS IN LOGICAL FIELD UNITS PER SECOND, never per frame and never in pixels.
    // The field is a fixed 480 × 300 scaled to whatever the window is, so a speed in pixels would
    // make the round easier on a small window and harder on a large one — the player's cube would
    // cross the field in a different number of seconds depending on how they had dragged the
    // corner. Per second rather than per tick for the same reason one step up: the tick rate is a
    // sampling rate, and a speed written per tick silently re-tunes the whole round if it changes.

    /** The logical field. Everything in this block is in these units. */
    public static final double DEFENSE_FIELD_WIDTH = 480.0d;

    public static final double DEFENSE_FIELD_HEIGHT = 300.0d;

    /**
     * The line the player cannot cross, and the whole geometry of the round.
     *
     * <p>⚠ The player is confined to the RIGHT of it, which is what makes the virus unreachable and
     * the laser the only thing that crosses. Moving it toward the virus would let a player simply fly
     * up to the thing and shoot it point-blank, and there would be no round.
     */
    public static final double DEFENSE_MIDLINE = 240.0d;

    /**
     * How long the player has. A timeout is a LOSS — see {@code docs/design/19} §4.
     *
     * <p>⚠ Thirty, and it went to 45 and back on 2026-08-10. "The mini game felt a little too quick"
     * was read as the round being short; it meant the <b>transitions</b> in and out of it. The round
     * itself was right the first time, and the fix is in {@code Dread}'s ramp and the deck's entry and
     * exit — not here. Re-measured either way: nobody reaches the clock, so its value moves nothing.
     */
    public static final int DEFENSE_ROUND_SECONDS = 30;

    /**
     * The simulation's fixed timestep.
     *
     * <p>⚠ Fixed, so a late frame advances the world by the same step as an on-time one — a dropped
     * frame slows the round rather than skipping physics through it. That is fairer, and it is what
     * makes the whole simulation reproducible from a seed and testable without a toolkit.
     */
    public static final int DEFENSE_TICKS_PER_SECOND = 60;

    /** Speeds, in field units per second. */
    public static final double DEFENSE_PLAYER_SPEED = 168.0d;

    /**
     * How hard the cube accelerates toward the direction being held, in units/s².
     *
     * <h2>⚠ The cube GLIDES now, and that is a rules change rather than a look</h2>
     *
     * It used to move at full speed the instant a key went down and stop dead the instant it came up
     * — precise, and completely weightless. Acceleration plus drag gives it mass: it leans into a
     * turn, it carries, and a dodge has to be started slightly early.
     *
     * <p>⚠ <b>It makes the round harder and the balance was re-measured, not assumed.</b> Momentum is
     * the one change that could quietly break a dodge the whole difficulty rests on.
     *
     * <p>⚠ Chosen so the cube still reaches full speed quickly — about a fifth of a second. A slower
     * ramp reads as input lag rather than as weight, and this is a game where a late dodge is the
     * correct play.
     */
    public static final double DEFENSE_PLAYER_ACCEL = 940.0d;

    /**
     * How quickly the cube sheds speed with nothing held, as a fraction kept per second.
     *
     * <p>⚠ Not zero and not near one. At zero there is no glide at all; near one the cube skates and
     * the player is fighting the controls rather than the virus. This is a short coast — a few tenths
     * of a second — which is enough to feel and not enough to overshoot a dodge.
     */
    public static final double DEFENSE_PLAYER_DRAG = 0.0009d;

    public static final double DEFENSE_VIRUS_SPEED = 62.0d;

    public static final double DEFENSE_TRIANGLE_SPEED = 132.0d;

    /**
     * ⚠ SLOWER THAN THE PLAYER, and that is the entire design of this piece. It cannot catch anybody
     * who keeps moving; what it takes away is standing still. A circle faster than the cube is not a
     * harder round, it is an unwinnable one.
     */
    public static final double DEFENSE_CIRCLE_SPEED = 58.0d;

    public static final double DEFENSE_LASER_SPEED = 620.0d;

    /**
     * How sharply a triangle steers toward the player while it is still approaching, in units/s².
     *
     * <h2>⚠ "BASIC HOMING" IS A TURN RATE, AND THE FIRST VALUE HERE MADE IT A PERFECT TRACKER</h2>
     *
     * The steering rate is {@code homing / speed} radians per second — at the 210 this started as,
     * that is 1.6 rad/s, or **91° per second**, against a flight of about 1.6 seconds. A triangle
     * could therefore turn through 145° while crossing the field, which is not homing, it is a
     * guarantee. <b>Measured over 300 rounds at four firewall tiers: a dodging player survived 0% of
     * them, mean 4.0 seconds.</b> The round was unplayable and no assertion said so — the simulation
     * was working perfectly.
     *
     * <p>At 55 the same arithmetic gives 24°/s, i.e. about 38° of correction over a whole flight:
     * enough that a triangle bends toward where the player is going, and little enough that moving
     * beats it. That is what {@code docs/design/19} §3.3 means by basic.
     *
     * <h2>⚠ 78, raised from 55 on 2026-08-10 — and the METRIC had to be fixed before the value could</h2>
     *
     * Asked for "slightly more homing-ness". Two measurements said nothing before one said something,
     * and both failures are worth keeping:
     *
     * <ul>
     *   <li>A bot's <b>survival rate</b> is a statement about the bot. Raising the homing made the
     *       scripted player <em>safer</em> (0% → 100%), which is not a fact about the game.
     *   <li>A <b>mean distance over every tick</b> is dominated by the approach — a shot is fired from
     *       {@code x=34} at a player near {@code x=400}, so most of its life it is far away whatever
     *       its homing. 55 and 78 read 170 and 166: indistinguishable, and meaningless.
     * </ul>
     *
     * <p>What describes tracking is the <b>closest a shot gets in a whole round</b>, measured at
     * firewall tier 3 so the circle cannot end the round before the triangles have had their say.
     * Over 150 rounds: <b>55 → 19.3 units and ZERO hits landed</b> — a shot that never actually
     * reached a dodging player — against <b>78 → 12.5 units</b> and shots that connect. 105 measures
     * 13.4 and lands the same, so past about 80 the extra turn rate buys nothing and only removes the
     * player's ability to dodge late.
     *
     * <p>⚠ Re-measure with {@code DefenseCensus} after touching this, {@link #DEFENSE_TRIANGLE_SPEED}
     * or {@link #DEFENSE_TRIANGLE_INTERVAL} — the three of them are one difficulty knob wearing three
     * hats, and the round's survivability is not readable from any of them alone.
     */
    public static final double DEFENSE_TRIANGLE_HOMING = 78.0d;

    /** At most five in the air at once — {@code docs/design/19} §3.2. */
    public static final int DEFENSE_MAX_TRIANGLES = 5;

    /**
     * Seconds between the virus's shots.
     *
     * <h2>⚠ 1.6 RATHER THAN 1.15, AND THE FIREWALL IS THE REASON — measured, not felt</h2>
     *
     * At a 1.15s interval the triangle pressure swamps everything else: {@code DefenseCensus} over
     * 300 rounds per tier measured a scripted player surviving <b>9.3s / 10.1s / 10.3s / 9.9s</b> at
     * firewall tiers 0–3. That is a flat line — <b>the firewall bought nothing measurable</b>, which
     * makes the one tool this round exists to showcase decoration.
     *
     * <p>At 1.6 the same measurement gives <b>8.5s / 16.9s / 16.4s / 28.9s</b>. Shelter starts paying,
     * the tiers separate, and the thirty-second clock becomes reachable at the top of the ladder
     * rather than theoretical everywhere.
     *
     * <p>⚠ Re-measure after touching this, {@link #DEFENSE_TRIANGLE_HOMING} or
     * {@link #DEFENSE_TRIANGLE_SPEED}: the three are one difficulty knob, and whether the firewall
     * does anything is not readable from any of them alone.
     */
    public static final double DEFENSE_TRIANGLE_INTERVAL = 1.6d;

    /** Triangle hits the player survives. The second one loses the round. */
    public static final int DEFENSE_TRIANGLE_HITS_ALLOWED = 1;

    /** Half-extents, for collision. The field is small, so these are generous on purpose. */
    public static final double DEFENSE_PLAYER_RADIUS = 7.0d;

    public static final double DEFENSE_TRIANGLE_RADIUS = 5.0d;

    public static final double DEFENSE_CIRCLE_RADIUS = 7.5d;

    public static final double DEFENSE_VIRUS_RADIUS = 14.0d;

    /** The shield: a grid of breakable squares in front of the virus. */
    public static final int DEFENSE_SHIELD_COLUMNS = 4;

    public static final double DEFENSE_SHIELD_LEFT = 96.0d;

    public static final double DEFENSE_SHIELD_CELL = 20.0d;

    /**
     * ⚠ DERIVED, so the shield always spans the field exactly — {@code docs/design/19} §2.
     *
     * It was a literal 11, which covered 220 of the field's 300 units and left a 40-unit gap at the
     * top and the bottom. The gaps were free lanes: a player who parked at either extreme had a clear
     * shot at a virus that patrols the whole height, and never had to cut through anything.
     *
     * <p>⚠ Computed rather than re-typed as 15, because the three figures have to agree. A literal
     * that disagreed with {@code DEFENSE_SHIELD_CELL} or the field height would silently reopen the
     * gap at one end — which is exactly how it came to exist.
     */
    public static final int DEFENSE_SHIELD_ROWS = (int) (DEFENSE_FIELD_HEIGHT / DEFENSE_SHIELD_CELL);

    /**
     * How much of the shield grid is filled.
     *
     * <p>⚠ Not 1.0, and not close to it. The player gets roughly twenty-five shots in thirty seconds
     * and needs to open one clear lane; a solid wall of 44 squares cannot be cut through in the time,
     * and the round becomes a demonstration that it is impossible.
     */
    public static final double DEFENSE_SHIELD_FILL = 0.55d;

    /**
     * The firewall band's width per tier — {@code docs/design/19} §3.5.
     *
     * <p>⚠ Index 0 is "no firewall armed" and is a width of ZERO, i.e. no band at all rather than a
     * thin one. A player with nothing armed must get nothing, or the tool's absence is worth
     * something and the ladder above it is worth less than it costs.
     */
    public static final double[] DEFENSE_FIREWALL_BAND = {0.0d, 26.0d, 40.0d, 56.0d};

    /**
     * How much a Tarpit slows the VIRUS's patrol — {@code docs/design/19} §3.6.
     *
     * <h2>⚠ It slows the virus and NOTHING ELSE, on explicit direction</h2>
     *
     * Not the circle, not the triangles. {@code docs/design/09} §1 sells the Tarpit as "slows every
     * intruder action", and the intruder here is the <b>virus</b> — a slower patrol is a target that
     * is easier to line up on, which is the same favour the tool does everywhere else: it does not
     * stop anything, it buys you time to act. Slowing the projectiles instead would make it a
     * damage-reduction item, which is the firewall's job and would leave the two doing one thing.
     */
    public static final double DEFENSE_TARPIT_VIRUS_SPEED = 0.55d;

    /**
     * The attacking virus's tiers, as LIVES — how many laser hits it takes to put down.
     *
     * <h2>⚠ Index 0 is unused; a tier is 1–4 and the table is read by tier</h2>
     *
     * A higher-tier Breach Virus is a longer round for the defender, not a faster or deadlier one.
     * That is the honest reading of "adds life to the virus that the other player would need to
     * conquer": what a buyer gets is <b>staying power</b>, which costs the defender the one thing the
     * round rations — the thirty seconds.
     *
     * <p>⚠ It deliberately does NOT scale the shot rate, the homing or the circle. Those would make a
     * bought item raise the *lethality* of an attack, and a defender's survival would then be
     * purchasable by the attacker. Lives cost the defender time; they do not make the round more
     * likely to kill them.
     */
    public static final int[] DEFENSE_VIRUS_LIVES = {1, 1, 2, 3, 4};

    /** Lives for an attacking virus of {@code tier}, clamped to the table. */
    public static int defenseVirusLives(int tier) {
        return DEFENSE_VIRUS_LIVES[Math.max(0, Math.min(DEFENSE_VIRUS_LIVES.length - 1, tier))];
    }

    /**
     * The best the Auto-Counter Daemon can do, and the ceiling is the whole point.
     *
     * <h2>⚠ FIFTY PERCENT IS WHAT KEEPS INVARIANT I10 ALIVE IN SPIRIT</h2>
     *
     * I10 is "bots assist, never substitute; a bot never solves the puzzle for the player", and a
     * daemon that plays the round <em>is</em> a bot playing a puzzle. What makes it defensible is
     * that it is <b>strictly worse than playing</b>: a coin flip at its very best, against a player
     * who can win outright. It is the answer to "I am not at the keyboard", which is exactly what
     * {@code docs/design/09} §1 already sells the tool as — "launches a weak counter-attack when
     * raided while offline" — and never the answer to "I would rather not play".
     *
     * <p>⚠ Raising this is the edit that quietly deletes the minigame: at 80% the correct play is to
     * press the daemon every time and never touch the arrow keys.
     */
    public static final double DEFENSE_DAEMON_MAX_ODDS = 0.5d;

    /**
     * What the daemon's odds fall to against each attacking virus tier.
     *
     * <p>⚠ The ceiling is only reachable against the weakest attack. A tier-4 virus is what somebody
     * spent real money on, and a defence nobody was present for should not shrug it off.
     */
    public static final double[] DEFENSE_DAEMON_ODDS = {0.5d, 0.5d, 0.4d, 0.3d, 0.2d};

    /** The daemon's chance against an attacking virus of {@code tier}, clamped to the table. */
    public static double defenseDaemonOdds(int tier) {
        return Math.min(
                DEFENSE_DAEMON_MAX_ODDS,
                DEFENSE_DAEMON_ODDS[Math.max(0, Math.min(DEFENSE_DAEMON_ODDS.length - 1, tier))]);
    }

    /** The band's width for an armed firewall of {@code tier}, clamped to the table. */
    public static double defenseFirewallBand(int tier) {
        return DEFENSE_FIREWALL_BAND[Math.max(0, Math.min(DEFENSE_FIREWALL_BAND.length - 1, tier))];
    }

    // ================================================================== ambient intrusions (19 §9)

    /**
     * How often somebody comes for a CLEAN rig, per hour.
     *
     * <h2>⚠ NOT ZERO, and that is the decision</h2>
     *
     * A careful player would otherwise never once have to defend, and every tool on
     * {@code design/09}'s shelf becomes a purchase with no occasion to use it. About one attempt
     * every five hours of play says that being quiet is not the same as being invisible.
     */
    public static final double AMBIENT_INTRUSION_BASE_PER_HOUR = 0.2d;

    /**
     * How often at maximum personal heat.
     *
     * <p>⚠ Heat already punishes in four other ways. At 100 it must not buy a round every ninety
     * seconds — that is a client nobody can put down, and the round is thirty seconds of arcade each
     * time. Roughly one an hour is pressure; six an hour is a different game.
     */
    public static final double AMBIENT_INTRUSION_HOT_PER_HOUR = 1.4d;

    /**
     * The shortest gap between two unprovoked attempts.
     *
     * <p>⚠ This is what makes the rate safe to tune at all. Without it an unlucky run of rolls stacks
     * two rounds back to back — and however good the arcade is, twice in a minute is not tension.
     */
    public static final long AMBIENT_INTRUSION_COOLDOWN_SECONDS = 600L;

    /**
     * The Breach Virus tier an unprovoked attacker turns up with, by the target's tier.
     *
     * <p>⚠ Index 0 is unused; a host tier is 1–5. A tier-5 estate coming back at you brings something
     * better than a desktop does, which is the same gradient the world already applies to loot and
     * difficulty — and it is what stops the defence round being one difficulty forever (DEF-3).
     */
    public static final int[] AMBIENT_INTRUSION_VIRUS_TIER = {1, 1, 1, 2, 3, 4};

    /** The virus tier a host of {@code tier} attacks with, clamped to the table. */
    public static int ambientIntrusionVirusTier(int tier) {
        return AMBIENT_INTRUSION_VIRUS_TIER[Math.max(0, Math.min(AMBIENT_INTRUSION_VIRUS_TIER.length - 1, tier))];
    }

    // ================================================================== the Breach Virus (19 §5)

    /**
     * How likely a solved breach is to actually take the machine, by Breach Virus tier.
     *
     * <h2>⚠ THIS STEPS ON I2 AND THE READING IS NARROW — {@code docs/design/19} §5.2</h2>
     *
     * I2 is "ethecoin never buys a ceiling (only breadth: consumables, replacements, horizontal
     * options)". A consumable that raises a success rate is money buying <b>power</b>, and calling it
     * breadth because it is spent would be the kind of reasoning that hollows an invariant out.
     *
     * <p>What is defensible, and what this rests on: the virus is <b>consumed every attempt</b>, so it
     * is a running cost rather than an accumulating capability — a rich player pays again for every
     * breach and is never permanently better than a poor one who saved up for the same tier. And it
     * <b>cannot skip the puzzle</b>: the roll happens only after the board is solved, so no amount of
     * money breaches anything on its own. The meta-rule behind I2 and I7 — "the puzzle is the game" —
     * is untouched.
     *
     * <p>⚠ The floor is not zero and must not be. A player with no money still mines (**I4** makes
     * self-mining the income floor), so the cheapest tier is always a few minutes away — but a
     * <b>0% floor would be a softlock</b> for anybody who spent everything, because breaching is how
     * they would earn it back.
     */
    public static final double[] BREACH_VIRUS_SUCCESS = {0.55d, 0.55d, 0.70d, 0.80d, 0.90d};

    /** The chance a solved breach lands, for a virus of {@code tier}. Clamped to the table. */
    public static double breachVirusSuccess(int tier) {
        return BREACH_VIRUS_SUCCESS[Math.max(0, Math.min(BREACH_VIRUS_SUCCESS.length - 1, tier))];
    }

    /**
     * Breach Virus prices — {@code docs/design/19} §5.
     *
     * <p>⚠ Priced against a breach's OWN haul (`03` §3 puts loot at 3–6 EC early and 45–65 EC deep),
     * so the cheapest tier is a rounding error on a successful run and the dearest is a real
     * decision. A virus that cost more than the machine holds would make breaching a net loss and
     * the whole loop would stop.
     */
    // ⚠ 5, not 4. `design/03` §2's consumable band starts at 5 EC and `ShortcutsTest
    // .pricesRespectTheBands` fails the build below it — correctly: a price outside the published
    // bands is a number nobody calibrated, and the fix is to move the item, never to widen the band.
    public static final BigInteger BREACH_VIRUS_T1_PRICE = ec("5"); // 5 EC

    public static final BigInteger BREACH_VIRUS_T2_PRICE = ec("14"); // 14 EC

    public static final BigInteger BREACH_VIRUS_T3_PRICE = ec("38"); // 38 EC

    public static final BigInteger BREACH_VIRUS_T4_PRICE = ec("95"); // 95 EC

    /**
     * The cheapest virus, rendered, for the one refusal that has to name a price.
     *
     * <p>⚠ Through {@code Ethecoin.format} rather than a literal, because a hand-written "4 EC" is a
     * second copy of a tuned number and would go stale on the first re-price — silently, in a
     * sentence whose whole job is to tell the player what to do next.
     */
    public static final String BREACH_VIRUS_T1_PRICE_LABEL =
            io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin.format(BREACH_VIRUS_T1_PRICE);

    /** Ethecoin prices for the purchasable defences — {@code docs/design/09-defense-and-hardening.md} §1. */
    public static final BigInteger DEFENSE_CANARY_PRICE = ec("8"); // 8 EC

    public static final BigInteger DEFENSE_TARPIT_PRICE = ec("70"); // 70 EC

    /**
     * The firewall ladder's prices, straight from {@code docs/design/09} §1's Established table.
     *
     * <p>⚠ <b>T3 is a "top purchasable"</b> ({@code docs/design/03-economy.md} §2) and that phrase is
     * doing invariant work rather than describing a price point. <b>I2</b> says ethecoin never buys a
     * ceiling, and the whole firewall ladder is ethecoin-gated because `09` §2 argues it is
     * horizontal protection with the escalating <em>compute</em> cost as the real limiter: 15
     * permanent cycles is 15 you are not mining or attacking with. Money buys up to the top rung of a
     * ladder; it does not buy a higher ladder.
     */
    public static final BigInteger DEFENSE_FIREWALL_T1_PRICE = ec("40"); // 40 EC

    public static final BigInteger DEFENSE_FIREWALL_T2_PRICE = ec("110"); // 110 EC
    public static final BigInteger DEFENSE_FIREWALL_T3_PRICE = ec("200"); // 200 EC

    /**
     * The Detection Array's two purchasable rungs. <b>T3 has no price and must never acquire one.</b>
     *
     * <h2>⚠ AMENDED 2026-08-06 — the ladder was schematic-gated end to end</h2>
     *
     * {@code docs/design/09} §1 had all three tiers behind the schematic gate. T1 and T2 moved to
     * ethecoin on explicit direction, under a rule stated at the time and now recorded in
     * {@code docs/design/15} §3: <em>low-level base tools and low-level upgrades are purchasable and
     * cost more than a consumable; high-level and rare items need a schematic to compile</em>.
     *
     * <p>⚠ <b>What keeps I2 intact is that the ladder's TOP RUNG stayed behind the schematic.</b>
     * This is exactly the firewall's "top purchasable" shape one item along — money reaches the
     * highest rung below the ceiling and never the ceiling itself. Pricing T3 at any figure at all
     * collapses that, and it would do so silently: the shop would still render, the purchase would
     * still work, and ethecoin would have bought a permanent capability.
     * {@code CatalogueTest.theTopOfEveryDefenceLadderIsNotForSale} fails the build on it.
     *
     * <p>Priced against the ladder they now sit beside: T1 at 6 standing cycles is a little dearer
     * than the 5-cycle Firewall T1, and T2 at 14 sits between Firewall T2 and T3, which is where its
     * compute cost sits too. Both are far above a consumable — the canary is 8 EC — which is the
     * other half of the rule.
     */
    public static final BigInteger DEFENSE_DETECTION_ARRAY_T1_PRICE = ec("50"); // 50 EC

    public static final BigInteger DEFENSE_DETECTION_ARRAY_T2_PRICE = ec("140"); // 140 EC

    /**
     * What it takes for the black-market vendor to notice you — the MARKET's fourth tab.
     *
     * <h2>⚠ This gates a VENDOR, not an item, and that is what keeps I3 true</h2>
     *
     * {@code docs/design/02-unlock-gates.md} §2.5: the heat-state gate "determines what's reachable,
     * never what's ownable", and black-market brokers are the case it names — <em>"only reachable
     * while hot: being hunted opens doors that being clean does not"</em>. So every item behind this
     * tab keeps its own single gate (the Honeypot Stash stays <b>reputation</b>-gated, for §2.3's
     * reason that decoy infrastructure distorts raids if freely bought). Reaching the shelf and being
     * allowed to buy off it are two different questions and this answers only the first.
     *
     * <h2>⚠ BOTH conditions, and the heat one is a FLOOR rather than a ceiling</h2>
     *
     * Standing alone would make the tab a reputation gate by another name. Heat alone would hand it
     * to anyone careless. Together they describe the fiction the tab is for: somebody the factions
     * rate <em>and</em> the Eye is already hunting. ⚠ Note the direction — you need <b>at least</b>
     * this much heat, which is the opposite of every other gate in the game and is §2.5's point:
     * respectable fixers do not meet wanted people, and these are not respectable fixers.
     *
     * <p>Reputation is read as the <b>better</b> of the two faction standings, never their sum: a
     * committed Sickle operative and a committed Eye operative are both somebody worth knowing, and
     * adding them would let a fence-sitter with middling standing on both sides qualify on neither.
     */
    public static final int BLACK_MARKET_MIN_REPUTATION = 40;

    /** ⚠ A FLOOR. See {@link #BLACK_MARKET_MIN_REPUTATION} — this vendor wants you hunted. */
    public static final int BLACK_MARKET_MIN_HEAT = 25;

    // ------------------------------------------------------------------ market price bands

    /**
     * The price bands from {@code docs/design/03-economy.md} §2, in minor units.
     *
     * <p>Bands rather than prices: the document gives ranges because the exact price of any one item
     * is a content decision, and a solo catalogue that invented precise numbers would be asserting
     * authority it does not have. Offerings are priced inside these bands and say so.
     */
    public static final BigInteger PRICE_CONSUMABLE_MIN = ec("5");

    public static final BigInteger PRICE_CONSUMABLE_MAX = ec("15");
    public static final BigInteger PRICE_MID_TIER_MIN = ec("40");
    public static final BigInteger PRICE_MID_TIER_MAX = ec("60");
    public static final BigInteger PRICE_TOP_PURCHASABLE = ec("200");

    /** Relay-chain upkeep, ~8 EC per hop per session — {@code docs/design/03-economy.md} §4. */
    public static final BigInteger RELAY_HOP_UPKEEP = ec("8");

    // ------------------------------------------------------------------ thermal budget

    /**
     * Thermal Budget recovery — {@code docs/design/01-core-resources.md} §1.3, which is explicitly
     * tagged <strong>[PROPOSAL]</strong> with numbers "for playtest".
     *
     * <p>The curve is <b>bounded</b>: recovery is quick in general, slower the closer the rig sits to
     * capacity, and can never take longer than {@link #THERMAL_MAX_CLEAN_SECONDS} on a rig with
     * nothing stealing from it. That <em>shape</em> is the design commitment; the numbers are not.
     *
     * <h2>⚠ The old formulation was unbounded, and that was the bug</h2>
     *
     * It was {@code rate = 0.5 × (1 − load)² × thermalBudget}, with the time as {@code cycles / rate}.
     * The shape was right and the tail was not: as load approaches capacity the rate approaches zero,
     * so the time approaches <em>infinity</em>. Measured on a real save — a Thorough Scan's 35 cycles
     * on a rig at 90% load took <b>36 minutes</b> to come back, and two cycles at 82% load took a
     * hundred seconds. A player who over-commits should be inconvenienced, not benched, and there was
     * no number in the design that said where the ceiling was because the formula had none.
     *
     * <p>The replacement expresses the ceiling directly: the time is a <em>fraction</em> of a
     * published maximum rather than a quotient that can run away. See {@code ThermalRules}.
     *
     * <p>{@code docs/education/02-computer-architecture.md} deliberately states the shape and no
     * number in its {@code thermal-budget(7)} page, precisely so a tuning pass here cannot falsify a
     * teaching page. Keep it that way.
     */
    public static final double THERMAL_LOAD_EXPONENT = 2.0d;

    /**
     * The longest a recovery may take on a rig with nothing stealing from it: <b>five minutes</b>.
     *
     * <p>Reached only in the corner it describes — returning most of the rig's capacity while the
     * rest of it is pinned. It is an asymptote rather than a clip, so load still reads all the way up
     * instead of flattening into a plateau where 80% and 95% feel identical.
     */
    public static final long THERMAL_MAX_CLEAN_SECONDS = 300L;

    /**
     * The longest a recovery may take at all: <b>ten minutes</b>, and only a rig being comprehensively
     * robbed gets near it.
     *
     * <p>⚠ <b>Rogue processes are the only thing that may lift the ceiling above
     * {@link #THERMAL_MAX_CLEAN_SECONDS}, and this is the second of two ways they slow a rig down.</b>
     * The first is ordinary and needs no special case: a parasite holds cycles, so it raises the load
     * factor, so it slows recovery through the curve every other consumer uses. This one is the
     * thermal half — a machine with something else running on it sheds heat worse — and it is
     * separate on purpose, because a player who has cleared their own allocations down to nothing and
     * <em>still</em> sees a slow recovery has been handed the discrepancy
     * {@code docs/design/04-mining.md} §3.1 is built on.
     */
    public static final long THERMAL_MAX_INFESTED_SECONDS = 600L;

    /**
     * What fraction of the ceiling an <em>idle</em> rig still charges, so recovery is never free.
     *
     * <p>Zero here would make an idle rig return cycles instantly, which deletes the resource: the
     * whole point of {@code 01} §1.3 is that spending is a commitment over time rather than a toll.
     */
    public static final double THERMAL_IDLE_FLOOR = 0.12d;

    /** Nothing takes less than this, so a completed recovery is always something the player saw. */
    public static final long THERMAL_MIN_SECONDS = 2L;

    /**
     * How much slower work runs when parasites are eating the rig — {@code 1.0} means a rig with half
     * its capacity stolen runs everything <b>50% slower</b>.
     *
     * <p><strong>[PROPOSAL]</strong>. Proportional and honest: the machine has less of itself to give,
     * so everything it does takes longer. It applies to a task's <em>duration</em> and not to its
     * price, because the cycles a tool needs are a property of the tool and the time it takes is a
     * property of the machine running it.
     *
     * <p>⚠ <b>It applies whether or not the player has found the parasite</b>, which is the point.
     * Alongside the cycles that simply are not there, a rig that has quietly become sluggish is the
     * cheapest possible hint that something is wrong — and unlike a warning, it cannot be dismissed,
     * ignored or read as a false positive.
     */
    public static final double THEFT_SLOWDOWN = 1.0d;

    // ------------------------------------------------------------------ starting position

    /**
     * A new solo character starts with 0 EC and a base rig.
     *
     * <p>{@code docs/design/15-open-questions.md} logs this as <strong>Q-economy-seed</strong> — an
     * undecided question about whether a reset character gets a small onboarding grant. Zero is chosen
     * here because it is the option that cannot be wrong in the direction that matters: a grant can be
     * added later without invalidating anyone's save, whereas taking one away cannot.
     */
    public static final BigInteger STARTING_ETHECOIN_WEI = ec("0");

    /** The Encrypted Vault's starting capacity, in items — {@code docs/design/01-core-resources.md} §6. */
    public static final int STARTING_VAULT_CAPACITY = 6;

    /** Standard Storage — exposed while online. {@code design/01} §6 [PROPOSAL]. */
    public static final int STANDARD_STORAGE_CAPACITY = 20;

    /** The High-Hackable Zone — always exposed, and large because that is the trade. */
    public static final int HIGH_HACKABLE_CAPACITY = 60;

    /**
     * How many slots a tier has, where a slot holds one tool or one stack.
     *
     * <h2>⚠ Published, not yet enforced — and the gap is deliberate rather than forgotten</h2>
     *
     * {@link #STARTING_VAULT_CAPACITY} was declared on the day storage was written and read by
     * nothing for as long as it existed. The STORAGE window now draws a grid against these numbers,
     * so they are finally visible — but {@code moveItem} still does not refuse a move that would
     * overfill a tier, which means a vault can read {@code 8 / 6}. That is rendered honestly as
     * over-capacity rather than hidden, because the alternative is a readout that quietly disagrees
     * with what the player owns.
     *
     * <p>Enforcing it is a <b>rules</b> change — it makes a move fail — and belongs with the
     * Cold Storage Expansion schematic that {@code design/01} §6 pairs it with, since a hard cap of
     * 6 with no way to raise it is a different game from the one that document describes.
     * Invariant I12 constrains how capacity <em>scales</em>, and nothing here sells it.
     */
    public static int storageCapacity(StorageTier tier) {
        return switch (tier) {
            case VAULT -> STARTING_VAULT_CAPACITY;
            case STANDARD_STORAGE -> STANDARD_STORAGE_CAPACITY;
            case HIGH_HACKABLE_ZONE -> HIGH_HACKABLE_CAPACITY;
        };
    }

    // ------------------------------------------------------------------ scan precision

    /**
     * Per-tier false-positive rates — {@code docs/design/04-mining.md} §3.2a, decided 2026-07-26.
     *
     * <p>§3.2a publishes the shape in words and not in numbers: Quick is "cheap, fast, and it will
     * send you chasing ghosts", Full is "the working default", Thorough is "expensive in both
     * compute and attention, and it earns it". These are the first numbers for that shape and are
     * <strong>[PROPOSAL]</strong>.
     *
     * <p>The gap between them is the point rather than the values: a Quick Scan that lies a third of
     * the time and a Thorough Scan that lies one time in twenty-five is what makes the 5-versus-35
     * compute price legible. Compressing the range would make the expensive tier's price
     * unjustifiable; widening it would make the cheap tier useless rather than unreliable, and a
     * tier nobody uses teaches nothing.
     *
     * <p>⚠ These feed {@code docs/education/08-detection-and-defence.md}'s {@code false-positive(7)},
     * {@code base-rate-fallacy(7)} and {@code alert-fatigue(7)} pages, which are three of the
     * curriculum's strongest. Re-tuning here means re-reading those.
     */
    public static final double SCAN_FALSE_POSITIVE_QUICK = 0.35d;

    public static final double SCAN_FALSE_POSITIVE_FULL = 0.15d;
    public static final double SCAN_FALSE_POSITIVE_THOROUGH = 0.04d;

    /**
     * What a standing Detection Array multiplies the rate above by —
     * {@code docs/design/09-defense-and-hardening.md} §2, which closed OQ-6 by redefining the Array
     * as <em>precision</em> rather than sensitivity: "scans buy sensitivity, the Array buys
     * precision, and the two are different axes."
     *
     * <p>Multipliers, not subtractions, and that is the load-bearing choice. A subtraction would let
     * a T3 Array drive the Thorough Scan's 4% to zero and make one tier of one defence into a
     * perfect detector — which removes the doubt the whole detection system exists to create, the
     * same argument {@code docs/design/07-recon-tools.md} §2 makes for the Honeypot Detector's
     * mandatory false-negative rate. Multiplying preserves the ordering of the tiers and can never
     * reach certainty.
     *
     * <p>All three <strong>[PROPOSAL]</strong>. The permanent compute they cost (6 / 14 / 25) is
     * established; what it buys was decided in prose and never in figures.
     */
    public static final double DETECTION_ARRAY_PRECISION_T1 = 0.60d;

    public static final double DETECTION_ARRAY_PRECISION_T2 = 0.35d;
    public static final double DETECTION_ARRAY_PRECISION_T3 = 0.15d;

    /**
     * The chance a Full Scan sees a rootkit-wrapped miner —
     * {@code docs/design/04-mining.md} §3.2, whose Finds column says a Full Scan gets "all unhidden
     * miners; <em>some</em> rootkit-wrapped". This is the number behind "some".
     *
     * <p>Sensitivity, not precision, so the Detection Array does <em>not</em> move it. That
     * separation is what makes the Array non-redundant by construction rather than by tuning
     * ({@code 09} §2) and is the whole content of OQ-6's resolution.
     *
     * <p><strong>[PROPOSAL]</strong>. A coin flip is the honest reading of "some" and keeps
     * {@code docs/design/09}'s Rootkit Wrapper worth its 50 EC without making it a wall — the wall
     * is the deliberate audit ({@code 04} §3.1), which always finds it.
     */
    public static final double SCAN_ROOTKIT_SENSITIVITY_FULL = 0.50d;

    /**
     * The scripted tutorial miner planted on a new character's rig —
     * {@code docs/design/04-mining.md} §5.1, established: "the tutorial flow should <em>plant</em> a
     * weak scripted miner early."
     *
     * <p>Six cycles is deliberately below the 8-cycle default a real deployed miner carries: it is
     * weak, it is meant to be found, and it is the thing the first Quick Scan is wrong <em>about</em>
     * half the time. Tier 1 because the crack it enables is the tutorial for the whole breach system,
     * and because a tier-1 crack can never clear {@link #SCHEMATIC_MATERIAL_MIN_TIER} — the safest
     * introduction in the game must not also be a progression source.
     *
     * <p><strong>[PROPOSAL]</strong> for the figures; the plant itself is established.
     */
    public static final long TUTORIAL_MINER_HOST_CYCLES = 6L;

    public static final int TUTORIAL_MINER_TIER = 1;

    // ------------------------------------------------------------------ schematic material

    /**
     * The engagement tier below which a breach yields no schematic material —
     * {@code docs/design/10-botnets.md} §1a and Invariant I13.
     *
     * <p>§1a's exploit guard, applied to the breach: "the material drop is gated on engagement tier
     * — the bot must have been lost against a defended target above a difficulty threshold. Without
     * this, the optimal play is to build the cheapest junk bot and feed it to a loss." The same
     * failure exists here in a different costume — farm the softest live target for material — and
     * the same guard closes it, reading the same {@code resolutionRecord.difficultyTier}.
     *
     * <p>Tier 3 because that is where {@code docs/design/05-hacking-minigame.md} §3.3's mix first
     * stacks two classes: below it, an attempt tests one kind of thinking, which is not the
     * "engagement" §1a means. <strong>[PROPOSAL]</strong>.
     */
    public static final int SCHEMATIC_MATERIAL_MIN_TIER = 3;

    /** One unit per qualifying breach. <strong>[PROPOSAL]</strong> — see {@link #SCHEMATIC_MATERIAL_PER_UNLOCK}. */
    public static final int SCHEMATIC_MATERIAL_PER_BREACH = 1;

    /**
     * Material for one schematic unlock — {@code docs/design/02-unlock-gates.md} §2.2, decided
     * 2026-07-26 (closing OQ-5).
     *
     * <p>§2.2 fixes the rate against the bot-loss stream: "a schematic costs material equivalent to
     * roughly ten destroyed bot instances", anchored to {@code 10} §2's published 25–35 EC per
     * instance, so about 300 EC of deliberately destroyed value. Twelve qualifying breaches is that
     * figure carried across to the other stream that pays into the same pool.
     *
     * <p>Twelve rather than ten because the two streams cost different things. A bot loss costs
     * ethecoin the player already spent; a tier-3-or-better breach costs an attention budget, a
     * compute reservation, and — on a live target — heat. Pricing them identically would make
     * whichever is cheaper on the day the only one anyone used. ⚠ Both guards from §2.2 stay in
     * force and are what make any rate at this level safe: the tier gate above means material never
     * shortcuts a ceiling the player has not already reached, so this number sets <em>pace</em>,
     * never <em>reach</em>.
     *
     * <p><strong>[PROPOSAL]</strong>, like every other figure in {@code 03}.
     */
    public static final int SCHEMATIC_MATERIAL_PER_UNLOCK = 12;

    // ------------------------------------------------------------------ breach: attention

    /**
     * The published per-action attention costs — {@code docs/design/05-hacking-minigame.md} §4's
     * table, which is <b>decided</b> rather than proposed.
     *
     * <p>These four numbers are the whole loud-versus-patient trade, and §4's own column explains
     * each: a quiet read is "the patient baseline", an ordinary probe is "the default move", a loud
     * tool is "power bought with exposure", and the Side-Channel Reader's zero is "its entire
     * identity" — the only action in the game that costs nothing from the bar.
     *
     * <p>⚠ Do not add a fifth. A new cost tier would need a new row in §4 and a reason a player can
     * feel, and the ratio 1 : 2 : 6 is what makes the choice legible before the click.
     */
    public static final int ATTENTION_QUIET_READ = 1;

    public static final int ATTENTION_PROBE = 2;
    public static final int ATTENTION_LOUD_TOOL = 6;
    public static final int ATTENTION_SIDE_CHANNEL = 0;

    /**
     * The Credential Harvester's in-breach cost — twice an ordinary probe, below a loud tool.
     *
     * <p>The one figure not on {@code docs/design/05-hacking-minigame.md} §4's table, and it needs a
     * reason. {@code docs/design/06-intrusion-tools.md} §2 says harvested credentials "open linked
     * nodes without re-solving the rule", which inside a layer means the tool <em>skips a deduction
     * step</em> — strictly more than a probe buys. But it is a reputation-gated tool, not a loud one,
     * and §1 rates its noise Moderate rather than Very high, so pricing it at the loud tier would
     * make it a worse Fuzzer at the same price.
     *
     * <p>Four is the only value that keeps both relationships true. <strong>[PROPOSAL]</strong>, and
     * flagged in {@code docs/design/16-breach-implementation.md} §7 as the one action cost that is
     * not simply read off §4.
     */
    public static final int ATTENTION_CREDENTIAL_HARVESTER = 4;

    /**
     * What an Overflow Kit bypass costs, as a fraction of the layer's whole budget —
     * {@code docs/design/05-hacking-minigame.md} §4, which prices it as "most of the bar" and adds
     * "the cost is the point".
     *
     * <p>Eighty percent is "most of the bar" made a number, and the residue matters: at 100% the Kit
     * would be a layer-shaped suicide button, and at 50% it would be the default opening on every
     * layer, which is precisely the "panic button with a siren attached, never a default" that
     * {@code docs/design/06-intrusion-tools.md} §2 says it must not become. Twenty percent left is
     * enough to finish a layer you had already half-read and never enough to start one.
     *
     * <p><strong>[PROPOSAL]</strong> for the fraction; the "most of the bar" shape is decided.
     */
    public static final double ATTENTION_BYPASS_FRACTION = 0.80d;

    /**
     * Extra attention a strike burns, on top of whatever the failing action already cost —
     * {@code docs/design/05-hacking-minigame.md} §3.3's "error tolerance (how many wrong probes
     * before an alarm/lockout)", expressed in the only currency §4 leaves.
     *
     * <p>Three is a probe and a half: enough that a guess costs meaningfully more than a deduction,
     * which is the mechanical difference between the Logic class being reasoning and being
     * enumeration, and small enough that a player who miscounts once is not finished.
     * <strong>[PROPOSAL]</strong>.
     */
    public static final int ATTENTION_ALARM_PENALTY = 3;

    /**
     * Attention a Tarpit adds to <em>every</em> action's cost —
     * {@code docs/design/09-defense-and-hardening.md} §1: "Slows every intruder action; doesn't stop
     * them, buys response time."
     *
     * <p>A surcharge on each action rather than a cut to the budget, and the distinction is the
     * whole translation: cutting the budget would make a Tarpit a flat difficulty add, which is the
     * <em>Firewall's</em> published function. A per-action surcharge punishes exactly the play the
     * Tarpit is written to punish — many small moves — and leaves a patient reader who takes few
     * moves nearly untouched.
     *
     * <p>⚠ {@code 09} §2 also ties the Tarpit to the bot-backlog timer in {@code 10} §1, which is
     * real-time and completely unaffected by this. The two effects coexist; neither replaces the
     * other. <strong>[PROPOSAL]</strong>.
     */
    public static final int TARPIT_ATTENTION_SURCHARGE = 1;

    /**
     * Attention a Firewall removes from each layer's budget, per tier —
     * {@code docs/design/09-defense-and-hardening.md} §1, whose Function column for the Firewall is
     * exactly "Flat difficulty increase on incoming breach attempts" and nothing more specific.
     *
     * <p>This is {@code docs/design/05-hacking-minigame.md} §8's unpublished item 7 given a number.
     * Two per tier means a T3 Firewall takes six attention off every layer — roughly three ordinary
     * probes, against a budget of twenty-odd. That is felt without being decisive, which is what
     * "flat difficulty increase" asks for and what keeps the Firewall's real cost its 15 permanent
     * cycles ({@code 09} §2) rather than its effect. <strong>[PROPOSAL]</strong>.
     */
    public static final int FIREWALL_BUDGET_PENALTY_PER_TIER = 2;

    /**
     * The floor a layer's budget can never be pushed below by defences.
     *
     * <p>Without it, a T3 Firewall on a tier-5 target would leave 14, and stacking any future
     * defence on the same axis would eventually produce a layer that cannot be cleared by any
     * sequence of legal moves. An unwinnable board is not difficulty; it is the game deciding, which
     * is the one reading {@code docs/design/05-hacking-minigame.md} §1 constraint 4 forbids
     * outright. <strong>[PROPOSAL]</strong>.
     */
    public static final int BREACH_ATTENTION_FLOOR = 8;

    // ------------------------------------------------------------------ breach: noise

    /**
     * Noise a breach generates just by happening, before any action —
     * {@code docs/design/01-core-resources.md} §3 ("noise is generated by acting") and
     * {@code docs/design/05-hacking-minigame.md} §2, which makes {@code noiseGenerated} a
     * first-class output of every attempt.
     *
     * <p>Non-zero on purpose: an attempt that generated nothing would make an aborted breach free,
     * and {@code 05} §4.1 is explicit that on an abort "the noise already generated stays
     * generated". <strong>[PROPOSAL]</strong> — {@code 01} §3.2's own concrete model is tagged the
     * same way.
     */
    public static final int NOISE_BASE = 2;

    /**
     * Per-action noise, mapping {@code docs/design/06-intrusion-tools.md} §1's qualitative Noise
     * column onto {@code 01} §3.2's "noise is a scalar per player-pool".
     *
     * <p>The column gives None / Low / Moderate / Very high; this is that ladder as 0 / 1 / 5 / 12.
     * The Side-Channel Reader's zero is stated twice in the docs ({@code 06} §1's table and §2's
     * "zero noise"), so it is the one value here that is not an interpretation. The Overflow Kit's
     * "**Very high**" is bolded in the source table and is the loudest thing in the tool set, which
     * is why the gap between it and a loud tool is wider than the gap between loud and quiet.
     * <strong>[PROPOSAL]</strong>.
     */
    public static final int NOISE_QUIET_READ = 0;

    public static final int NOISE_PROBE = 1;
    public static final int NOISE_LOUD_TOOL = 5;
    public static final int NOISE_BYPASS = 12;
    public static final int NOISE_SIDE_CHANNEL = 0;

    /**
     * Noise added per alarm tripped.
     *
     * <p>An alarm is the defender noticing, which is the definition of being loud. Pricing it at
     * four puts a single strike between an ordinary probe and a loud tool, so a clumsy quiet run can
     * end up noisier than a clean loud one — which is the correct lesson and the one
     * {@code docs/design/08-stealth-and-noise.md} §4 assumes when it pairs each offensive escalation
     * with a stealth counter. <strong>[PROPOSAL]</strong>.
     */
    public static final int NOISE_PER_ALARM = 4;

    /**
     * How much noise converts into one point of personal heat.
     *
     * <p>{@code docs/design/01-core-resources.md} §3.2: "noise is per-action and decaying; heat is
     * accumulated standing. Noise is tactical, heat is strategic." A divisor is that sentence made
     * arithmetic — most of a breach's noise never becomes heat at all, and only a genuinely loud
     * attempt leaves a mark that outlives the session.
     *
     * <p>At eight, a clean quiet breach of a small target leaves nothing, and a bypass-and-alarms
     * attempt leaves two or three points on a 0–100 scale. Reaching the named-hacker band ({@code
     * 01} §4.1) therefore takes a campaign rather than an evening, which is what that band is for.
     * <strong>[PROPOSAL]</strong>.
     *
     * <p>⚠ Invariant I9 short-circuits all of this on a miner crack: zero heat, on every outcome,
     * including failure. Defending your own rig never contributes to being wanted.
     */
    public static final int NOISE_PER_HEAT_POINT = 8;

    /** Personal heat is a 0–100 scale — {@code docs/design/01-core-resources.md} §4.1's bands. */
    public static final int PERSONAL_HEAT_MAX = 100;

    // ------------------------------------------------------------------ breach: session cost

    /**
     * Cycles a breach attempt reserves for itself, before the loadout —
     * {@code docs/design/05-hacking-minigame.md} §2, which instantiates an attempt with
     * {@code equippedTools} "each of which modifies the attempt", implying a cost for the attempt
     * itself that the tool list does not carry.
     *
     * <p>Bracketed by numbers that already exist: a Quick Scan is 5 and a Full Scan is 15 ({@code
     * 04} §3.2), and the Overflow Kit — the one tool defined entirely in terms of being inside a
     * breach — is 10 ({@code 06} §1). Ten puts an attempt above a cheap look and below a serious
     * sweep, which is the right relative price for something that is a commitment rather than a
     * glance.
     *
     * <p>Held for the whole attempt and released into recovery at resolution, exactly like a scan
     * (UI-6). <strong>[PROPOSAL]</strong>.
     */
    public static final long BREACH_SESSION_CYCLES = 10L;

    // ------------------------------------------------------------------ breach: tier tables

    /**
     * Layers per attempt, by difficulty tier — {@code docs/design/05-hacking-minigame.md} §3.3,
     * which makes {@code difficultyTier} scale "layer count, class mix, time pressure and error
     * tolerance", and §3.1, where "a given target composes 1-N layers".
     *
     * <p>1, 1, 2, 3, 3. Growth stops at three because attention per layer is already falling across
     * the tiers — a fourth layer would be attrition rather than difficulty. Every layer of an attempt
     * plays the same class ({@code BoardFactory}), so stacking more of them is stacking more of one
     * puzzle, which is a length knob and not a skill one.
     *
     * <p>A method rather than an array constant: {@code public static final int[]} is writable by
     * anyone who holds it, and a balance table that any caller can silently edit is worse than no
     * table. <strong>[PROPOSAL]</strong>.
     */
    public static int breachLayers(int tier) {
        return switch (clampTier(tier)) {
            case 1, 2 -> 1;
            case 3 -> 2;
            default -> 3;
        };
    }

    /**
     * Attention granted per layer, by tier, before defensive modifiers — §3.3's "time pressure" knob
     * translated into the only currency §4 leaves.
     *
     * <p>26, 24, 22, 22, 20. It <em>falls</em> as boards grow, which is the whole difficulty curve:
     * a tier-5 Logic board has a keyspace of 100 000 against a tier-2 board's 1296, and it gets four
     * fewer attention to crack it. Reading the two tables together is the only way either makes
     * sense, which is why they sit next to each other.
     *
     * <p>⚠ {@code 05} §3.3 still says "time pressure (trace timer speed)". That is residual pre-§4
     * wording — §4 removed the wall clock outright and there is no timer. Flagged for the
     * integrator. <strong>[PROPOSAL]</strong>.
     */
    public static int breachAttention(int tier) {
        return switch (clampTier(tier)) {
            case 1 -> 26;
            case 2 -> 24;
            case 3, 4 -> 22;
            default -> 20;
        };
    }

    /**
     * Strikes a layer tolerates before it locks out — §3.3's "error tolerance (how many wrong probes
     * before an alarm/lockout)".
     *
     * <p>4, 3, 3, 2, 2. Four at tier 1 is the tutorial being forgiving on the player's own rig,
     * where Invariant I9 already guarantees a loss costs no heat; two at the top is where a wrong
     * read is genuinely expensive. <strong>[PROPOSAL]</strong>.
     */
    public static int breachStrikeLimit(int tier) {
        return switch (clampTier(tier)) {
            case 1 -> 4;
            case 2, 3 -> 3;
            default -> 2;
        };
    }

    /** Clamps to the 1–5 scale {@code DifficultyTier} publishes, so a hand-edited save cannot crash generation. */
    private static int clampTier(int tier) {
        return Math.max(1, Math.min(5, tier));
    }

    // ------------------------------------------------------------------ breach: board generation

    /**
     * Enumeration board size — {@code docs/design/05-hacking-minigame.md} §3.1 ("map a node's open
     * ports/services before you can act") and §3.3's layer scaling.
     *
     * <p>12 slots at tier 1 growing by 2 a tier, in bands of 4. Twelve is three full bands, which is
     * the smallest board on which a sweep is worth its 1 attention against a probe's 2 — on a
     * smaller board, probing everything is simply cheaper, and the class's central trade would not
     * exist at tier 1 where it is being taught. <strong>[PROPOSAL]</strong>.
     */
    public static final int BREACH_ENUM_SLOTS_BASE = 12;

    public static final int BREACH_ENUM_SLOTS_PER_TIER = 2;
    public static final int BREACH_ENUM_BAND_SIZE = 4;

    /**
     * Logic code length and alphabet size — {@code docs/design/05-hacking-minigame.md} §3.1's
     * "Mastermind-family reasoning".
     *
     * <p>Length {@code 3 + tier/2} against an alphabet of {@code 5 + tier}: keyspaces of 216, 1296,
     * 4096, 6561 and 100 000. The jump at tier 5 is deliberate — it is where the class stops being
     * solvable by holding candidates in your head and starts requiring the readout the board
     * publishes.
     *
     * <p>Ten symbols is the ceiling because the alphabet is a fixed ASCII set (see
     * {@code LogicRules}); adding an eleventh means adding a character, and every character the
     * client draws has to exist in the bundled font. <strong>[PROPOSAL]</strong>.
     */
    public static final int BREACH_LOGIC_LENGTH_BASE = 3;

    public static final int BREACH_LOGIC_ALPHABET_BASE = 5;

    /**
     * The tier at and above which a Logic code is always salted, and the chance it is salted below
     * that — {@code docs/design/06-intrusion-tools.md} §2, which makes the Rainbow Table
     * "hard-countered by salting, by design" and "a conditional power spike: devastating against
     * lazy targets, useless against prepared ones, so it rewards recon".
     *
     * <p>The 30% below tier 3 is what makes it conditional rather than binary. If low tiers were
     * never salted the Table would be an unconditional win on exactly the targets a new owner can
     * reach, and §2's "know before you buy the attempt" would have nothing to know.
     * <strong>[PROPOSAL]</strong>.
     */
    public static final int BREACH_LOGIC_ALWAYS_SALTED_TIER = 3;

    public static final double BREACH_LOGIC_SALT_CHANCE = 0.30d;

    /**
     * Probes in one Fuzzer volley, and the tier at which a volley starts also costing a strike.
     *
     * <p>{@code docs/design/06-intrusion-tools.md} §2 calls the Fuzzer "the entry-level 'I don't know
     * the rule, so I'll hammer it' tool" with "moderate noise as the cost of impatience". Four
     * guesses for 6 attention is breadth at three times a probe's price, paid in quality: a volley
     * returns exact counts only, never partials.
     *
     * <p>From tier 4 the hammer starts setting off alarms, which is where the tool stops scaling and
     * the class stops being brute-forceable — the mechanical form of §3.2's "a defended/high-tier
     * node can be built to defeat a fixed strategy". <strong>[PROPOSAL]</strong>.
     */
    public static final int BREACH_LOGIC_VOLLEY_SIZE = 4;

    public static final int BREACH_LOGIC_VOLLEY_ALARM_TIER = 4;

    /**
     * Positions a Rainbow Table reveals against an unsalted code —
     * {@code docs/design/06-intrusion-tools.md} §1, whose Function column is "instant crack against
     * weak or reused credentials".
     *
     * <p>Two rather than all of them. A full reveal would make the Table skip the layer, which is the
     * Overflow Kit's job and is proof-of-skill-gated for exactly that reason ({@code 02} §2.4) — an
     * EC-plus-schematic item must not do a proof-of-skill item's work. Two positions collapse the
     * keyspace by a factor of the alphabet squared and still leave a deduction to finish.
     * <strong>[PROPOSAL]</strong>.
     */
    public static final int BREACH_RAINBOW_REVEALS = 2;

    /**
     * Traversal lattice shape — {@code docs/design/05-hacking-minigame.md} §3.1 ("route through an
     * internal graph to the data node") and §3.2's decoy requirement.
     *
     * <p>{@code 3 + tier/2} ranks, 3–4 nodes wide, with {@code 2 + tier/2} objective candidates on
     * the final rank. The candidate count is what {@code P-3} is measured against: a fixed heuristic
     * that cannot read the logs must extract at random and averages {@code (K+1)/2} attempts, while
     * a reader gets it in one. That gap is the number, and it must not be tuned away — see
     * {@code docs/design/16-breach-implementation.md} §5. <strong>[PROPOSAL]</strong>.
     */
    public static final int BREACH_TRAVERSAL_RANKS_BASE = 3;

    public static final int BREACH_TRAVERSAL_WIDTH_MIN = 3;
    public static final int BREACH_TRAVERSAL_WIDTH_MAX = 4;
    public static final int BREACH_TRAVERSAL_OBJECTIVES_BASE = 2;

    /**
     * Extra attention a tarpit node charges on entry, and how many ranks a {@code traceroute}
     * reveals ahead of the current node.
     *
     * <p>Two ranks is the Topology Mapper's published reach ({@code docs/design/07-recon-tools.md}
     * §1: "extends graph visibility from one hop to two"), reused here because a loud in-breach tool
     * should buy what the quiet pre-breach tool buys and pay for it in noise instead of a schematic.
     * <strong>[PROPOSAL]</strong>.
     */
    public static final int BREACH_TRAVERSAL_TARPIT_STEP_COST = 2;

    public static final int BREACH_TRACEROUTE_RANKS = 2;

    // ================================================================== the network (design/17)
    //
    // Two sentences hold this whole block together, and every number below is one of them made
    // arithmetic:
    //
    //   1. SCHEMATICS BUY REACH, ETHECOIN BUYS SENSITIVITY. The hop ceiling is 1, or 2 with the
    //      Topology Mapper schematic — docs/design/07-recon-tools.md §2 calls that tool "a CEILING on
    //      information ... hence schematic-gated not purchasable (Invariant I2)". No sweep tier at
    //      any price appears in NetRules.hopCeiling, and there is no constant here it could read.
    //      What the tiers move is the PROBABILITY of detecting what is already in reach, which is
    //      breadth, which docs/design/02-unlock-gates.md §1.1 step 4 puts on ethecoin.
    //
    //   2. DETECTION IS NEVER DRAWN AT SWEEP TIME. Every table below that produces a threshold is
    //      compared against a value that predates the sweep: HostState.detectRoll, which the world
    //      was built with, scaled by a HASH of (machine, vantage). Both halves are fixed before the
    //      player asks, so re-sweeping is never a reroll and save-scumming is defeated by
    //      construction rather than by a cooldown.
    //
    //      ⚠ AMENDED 2026-08-08: the roll used to be the machine's alone. It is now a property of
    //      the PAIR — see NET_SWEEP_VANTAGE_FLOOR — because "what you can hear depends on where you
    //      are standing" is both the truer model and the thing that makes repositioning pay. Nothing
    //      about determinism moved: a hash is not a draw.

    // ------------------------------------------------------------------ network: world size

    /**
     * Five to seven virtual servers — the brief's ceiling, and a floor that keeps the depth gradient
     * usable.
     *
     * <p>Seven is a hard cap on how much world one save may carry: at the machine cap below it is
     * 350 hosts in a file rewritten every thirty seconds. Five is the floor because a depth-biased
     * spanning tree over four servers frequently produces a maximum depth of two, and the difficulty
     * tables run to four — a world whose deep rows never appear is a world where half the tuning is
     * decoration. <strong>[PROPOSAL]</strong>.
     */
    public static final int NET_SERVERS_MIN = 5;

    /**
     * ⚠ <b>RAISED 7 → 18 on 2026-08-09, on explicit direction.</b>
     *
     * <p>The band is what a world rolls within when the player has not chosen a size; a specific
     * count in the same band can be set at character creation ({@code state/WorldSettings}). Existing
     * characters are untouched — {@code TopologyGenerator.generate} returns early once a topology
     * exists, which is the guard that stops a player re-rolling their world.
     *
     * <p>⚠ <b>The server index is the second octet of every address</b> ({@code 10.<server>.0.<host>}),
     * so the ceiling here is really 256 and 18 is nowhere near it. What 18 does cost is the map's tab
     * strip, which is why that strip wraps rather than overflowing.
     */
    public static final int NET_SERVERS_MAX = 18;

    /**
     * The chance a newly placed server attaches to one of the currently deepest servers rather than
     * to any already-placed one.
     *
     * <p>This single number is the shape of the world. At 0.0 the tree is a bush — every server one
     * hop from home, no gradient at all. At 1.0 it is a chain — one path, no choice about which way
     * to push, and a depth that is forced rather than chosen. Sixty percent leans deep while still
     * branching often enough that the player is regularly choosing between two directions, which is
     * the decision the map exists to present. <strong>[PROPOSAL]</strong>.
     */
    public static final double NET_SERVER_DEEPEN_BIAS = 0.60d;

    /**
     * Chance and cap for the extra server-graph edges added on top of the spanning tree.
     *
     * <p>Chords are what make the picture read as a network rather than a taxonomy: without them
     * every route is the unique tree path and the graph view is a family tree. Two is enough to be
     * felt on a five-to-seven node graph and few enough that the tree is still obviously the
     * skeleton.
     *
     * <p>⚠ A chord may only join servers whose depths differ by at most one, and that constraint is
     * load-bearing rather than cosmetic — see {@link io.github.stoicswe.eyeandsickle.engine.state.ServerState}.
     * <strong>[PROPOSAL]</strong>.
     */
    public static final double NET_SERVER_CHORD_CHANCE = 0.35d;

    public static final int NET_SERVER_CHORD_MAX = 2;

    /**
     * How many chords a world of {@code servers} servers may take.
     *
     * <h2>⚠ It scales, and the default band is bit-for-bit unchanged</h2>
     *
     * A flat budget of two is a sensible fraction of a five-server world and almost nothing on an
     * eighteen-server one — so with the band widened, a fixed cap would make the cross-link setting
     * <em>invisible</em> at exactly the sizes a player raises it for. Scaling by a third and flooring
     * at {@link #NET_SERVER_CHORD_MAX} keeps 5–7 servers on 2 chords ({@code 5/3 = 1}, {@code 7/3 =
     * 2}, both floored or equal to 2) and gives 18 servers 6.
     *
     * <p>⚠ Derived rather than exposed as a second setting: two knobs for "how connected is this
     * world" is two numbers that can disagree, and the one a player understands is the chance.
     */
    public static int netServerChordMax(int servers) {
        return Math.max(NET_SERVER_CHORD_MAX, servers / 3);
    }

    /**
     * Chance of an extra link between two machines on the same server, per host.
     *
     * <p>The intra-server graph is a spanning tree first (so every machine is reachable by
     * construction, never by a retry loop) and then this. Without it, every host has exactly one
     * route and taking a foothold never opens more than one new direction, which would make the
     * vantage mechanic — reposition to see further — a straight line instead of a choice.
     * <strong>[PROPOSAL]</strong>.
     */
    public static final double NET_INTRA_CHORD_CHANCE = 0.22d;

    /** The brief's hard ceiling: never more than fifty machines on one server. */
    public static final int NET_MACHINES_HARD_CAP = 50;

    // ------------------------------------------------------------------ network: the shape of a server
    //
    // docs/design/18-network-topology.md §2. NODE depth — hops from a server's gateway — as opposed to
    // SERVER depth, which is bridges from home and is what every difficulty table keys on. The two are
    // different questions and §1 of that document exists because they were being confused.

    /**
     * How deep one server's tree of machines runs, in hops from its gateway.
     *
     * <h2>⚠ CHOSEN, where it used to be an ACCIDENT</h2>
     *
     * The intra-server graph was a random recursive tree — every machine attached to a uniformly
     * chosen predecessor — so its depth was roughly {@code log(count)} and its branch factor was
     * whatever fell out. {@code docs/client/09} §8 measured the result and filed it as a defect:
     * "layers are 1–5 machines wide, maps are 4–10 columns deep… <b>fan-out does not occur at
     * reachable depth</b>". A depth nobody picked, and a width nobody picked either.
     *
     * <p>4 is a floor because a server shallower than that is crossed before the player has decided
     * anything; 13 is a ceiling because the machine budget has to cover the spine and still leave
     * something to branch with. <strong>[PROPOSAL]</strong>.
     */
    public static final int NET_NODE_DEPTH_MIN = 4;

    public static final int NET_NODE_DEPTH_MAX = 13;

    /**
     * How many machines hang off one machine.
     *
     * <p>⚠ Drawn <b>per machine</b>, so a server has a mix of chains and fans rather than a uniform
     * shape. 1 is a corridor and 7 is a room; both should occur on the same server, because what
     * makes a network legible to read is that its parts are not all alike.
     *
     * <p><strong>[PROPOSAL]</strong>.
     */
    public static final int NET_BRANCH_MIN = 1;

    public static final int NET_BRANCH_MAX = 7;

    /**
     * How many machines on a server must branch into more than one child.
     *
     * <p>⚠ A server that was one long chain would have <b>no choice in it</b> — which is exactly the
     * argument {@code TopologyGenerator}'s class note gives for rejecting a chain at the <em>server</em>
     * level ("a chain gives one path and no choice"), restated one level down. Two, not one, because a
     * single fork is a fork and two is a shape.
     *
     * <p><strong>[PROPOSAL]</strong>.
     */
    public static final int NET_MIN_BRANCHING_NODES = 2;

    /**
     * The most of a server's machines that may be spent on its spine.
     *
     * <h2>⚠ MEASURED, AND THE FIRST VERSION OF THIS RULE PRODUCED A CORRIDOR</h2>
     *
     * The clamp was "leave two machines over", on the reasoning that two is what
     * {@link #NET_MIN_BRANCHING_NODES} needs. It is arithmetically true and it makes a bad server: a
     * 13-machine home rolled depth 11, the spine took eleven of the twelve non-gateway machines, and
     * the whole server rendered as an eleven-hop chain with a single fork at the far end. Rendered
     * and dumped, it read as a corridor — which is precisely the shape {@code design/18} §2 exists to
     * stop, arrived at from the other direction.
     *
     * <p>Two-thirds leaves a real budget to fan with at every server size: a 12-machine home spends
     * at most 7 on depth and has 4 spare; a 50-machine deep server hits the depth ceiling long before
     * the share binds and has 36 spare to spread over 13 layers.
     *
     * <p><strong>[PROPOSAL]</strong>.
     */
    public static final double NET_SPINE_BUDGET_SHARE = 0.6d;

    /**
     * The node depth a server actually gets, given its machine budget.
     *
     * <h2>⚠ THE CLAMP IS VISIBLE ON PURPOSE — {@code design/18} §2.2 and NT-1</h2>
     *
     * Depth and branching cannot both be free: a depth-13 tree branching 1–7 the whole way is
     * thousands of machines against a hard cap of fifty. So the budget wins, and it wins twice over —
     * {@link #NET_SPINE_BUDGET_SHARE} caps what the spine may take, and the floor is never breached.
     *
     * <p>⚠ <b>The share is of {@code count − 1}, not of {@code count}. The gateway is the root and is
     * not on the spine</b>, and the off-by-one in the other direction is what produced the corridor
     * {@link #NET_SPINE_BUDGET_SHARE} records.
     *
     * <p>⚠ The alternative — silently ignoring a rolled depth — is how "the number in the save is not
     * the number in the world" becomes a bug hunt. It is named, tested and logged instead.
     *
     * @param count how many machines this server has to spend
     * @param u a stable 0–1
     */
    public static int netNodeDepth(int count, double u) {
        return netNodeDepth(count, u, 0);
    }

    /**
     * As above, with a depth the player chose at character creation.
     *
     * <p>⚠ {@code wanted <= 0} means "use the roll", which is the ordinary path and is bit-for-bit
     * what the two-argument form has always done. A chosen depth replaces the roll and is then put
     * through <b>the same budget clamp</b> — {@link #NET_SPINE_BUDGET_SHARE} is not a preference, it
     * is what stops a small server becoming an n-hop corridor with one fork at the end
     * ({@code design/18} §2.2). So a player asking for 13 on a 6-machine server gets the deepest that
     * server can afford, exactly as an unlucky roll would.
     */
    public static int netNodeDepth(int count, double u, int wanted) {
        int rolled = NET_NODE_DEPTH_MIN
                + (int) (Math.clamp(u, 0.0d, 0.999_999d) * (NET_NODE_DEPTH_MAX - NET_NODE_DEPTH_MIN + 1));
        int chosen = wanted <= 0 ? rolled : Math.max(NET_NODE_DEPTH_MIN, Math.min(NET_NODE_DEPTH_MAX, wanted));
        int affordable = (int) ((count - 1) * NET_SPINE_BUDGET_SHARE);
        return Math.max(NET_NODE_DEPTH_MIN, Math.min(chosen, affordable));
    }

    /** How many children one machine takes, for a rolled {@code u}. */
    public static int netBranchWidth(double u) {
        return NET_BRANCH_MIN + (int) (Math.clamp(u, 0.0d, 0.999_999d) * (NET_BRANCH_MAX - NET_BRANCH_MIN + 1));
    }

    // ------------------------------------------------------------------ network: the home floor

    /**
     * How many machines the player's rig is guaranteed to sit one link from, and how many of those
     * are guaranteed to be workable first contacts.
     *
     * <p>⚠ These two are not tuning. They are the fix for "discovery is unusable at the start", and
     * they are applied deterministically after every roll: the first three non-gateway hosts at one
     * link from the rig are forced to {@code detectRoll = 0.0}, tier 1, firewall 0, undefended, with
     * a payout floor. Zero is below the base T1 sweep's <em>worst</em> threshold (0.35, a quiet
     * machine at one hop), so <b>the first sweep a new player runs always returns at least three
     * breachable, un-firewalled targets worth at least 3 EC each</b>, on every seed, forever.
     *
     * <p>Five neighbours rather than three so the guarantee is not also the whole board — a new
     * player should have something to miss and then find with a better instrument, which is what
     * teaches that sensitivity is a purchase. <strong>[PROPOSAL]</strong> in the figures; the
     * guarantee itself is not.
     */
    public static final int NET_HOME_SEED_NEIGHBOURS = 5;

    public static final int NET_HOME_GUARANTEED_CONTACTS = 3;

    /**
     * The payout floor on a guaranteed first contact, in minor units — 3 EC.
     *
     * <p>The bottom of the T1 loot band, restated as a floor so that the three machines a new player
     * is guaranteed to find are also guaranteed to be worth breaching. Three of them is 9 EC against
     * the 15 EC Passive Sniffer, which is the intended first purchase and the intended second or
     * third session's worth of work. <strong>[PROPOSAL]</strong>.
     */
    public static final BigInteger NET_LOOT_FLOOR_WEI = ec("3");

    // ------------------------------------------------------------------ network: the sweep line

    /**
     * How much a hop of distance costs a sweep's detection chance.
     *
     * <p>{@code docs/design/07-recon-tools.md} §2 makes the Topology Mapper a <em>reach</em>
     * purchase — "extends graph visibility from one hop to two" — and pointedly not a clarity one.
     * So the second hop is real and coarse: everything visible at one hop is more likely to be seen
     * than the same machine at two, and the schematic buys the ability to look at all rather than a
     * better look. <strong>[PROPOSAL]</strong>.
     */
    public static final double NET_HOP_FACTOR_1 = 1.00d;

    public static final double NET_HOP_FACTOR_2 = 0.60d;

    /**
     * Compute each sweep tier holds while it runs — inside {@code docs/design/07-recon-tools.md}
     * §1's established 2–14 recon range.
     *
     * <p>⚠ <b>These are the sweep's price in capacity, and are no longer also its noise.</b> They
     * used to be both, via the {@code CONTROL_CHANNEL} reservation, and the identity was elegant and
     * measurably wrong on screen: noise is drawn as outward cycles over <em>rig capacity</em>, so a
     * base sweep on a 100-cycle rig moved the meter by two percent — indistinguishable from silence,
     * and getting quieter the bigger the player's rig grew, which inverts the reading. Loudness is
     * now stated separately in {@link #NET_SWEEP_BASE_NOISE} and the two are free to differ, because
     * how much of your machine a job occupies and how much racket it makes outside it are genuinely
     * different quantities. See {@code docs/design/08-stealth-and-noise.md} §1: noise is generated by
     * <em>acting</em>, and the act here is touching machines that are not yours.
     */
    public static final long NET_SWEEP_BASE_CYCLES = 2L;

    public static final long NET_SWEEP_WIDE_CYCLES = 5L;
    public static final long NET_SWEEP_DEEP_CYCLES = 9L;

    /**
     * How loud each sweep tier is while it runs, on the same 0–{@code totalCycles} scale the noise
     * meter reads — so 35 on a 100-cycle rig is a bit over a third of the meter.
     *
     * <p><strong>[PROPOSAL]</strong>. A sweep is intrusive by construction: it puts packets on
     * machines the player does not own and has no business touching, and
     * {@code docs/design/08-stealth-and-noise.md} §1 makes that exactly what noise measures. Every
     * tier is therefore loud, and the ladder is loudness, not just sensitivity — a deep sweep listens
     * harder <em>by shouting louder</em>, which is what makes buying up the ladder a decision rather
     * than a strict upgrade. The precedent is {@code docs/design/07-recon-tools.md} §1's Ping Sweep,
     * the one recon tool the table already marks <b>High</b> for exactly this reason.
     *
     * <p>⚠ <b>It is loud only while it runs.</b> The allocation goes into thermal recovery the moment
     * the sweep settles, and recovering cycles are excluded from the noise sum — so the meter drops
     * back to whatever the rig was doing before. Noise is a rate, not a debt; what persists after a
     * loud act is <em>heat</em>, which is a different field and is charged by different rules.
     *
     * <p>⚠ Deliberately below {@code totalCycles} even at the top of the ladder. A tier that pinned
     * the meter would erase the distinction between "loud" and "as loud as this rig gets", and the
     * player needs the headroom to read a sweep running <em>on top of</em> something else.
     */
    public static final long NET_SWEEP_BASE_NOISE = 35L;

    public static final long NET_SWEEP_WIDE_NOISE = 55L;
    public static final long NET_SWEEP_DEEP_NOISE = 80L;

    /**
     * Wall-clock duration of each sweep tier, in seconds.
     *
     * <p>Bracketed by the scan ladder ({@code docs/design/04-mining.md} §3.2: 30 s / 120 s / 360 s).
     * A base sweep is shorter than a Quick Scan because it is the verb a new player runs before they
     * own anything, and a tool whose floor is a thirty-second wait is a tool they run once.
     * <strong>[PROPOSAL]</strong>.
     */
    public static final long NET_SWEEP_BASE_SECONDS = 20L;

    public static final long NET_SWEEP_WIDE_SECONDS = 45L;
    public static final long NET_SWEEP_DEEP_SECONDS = 90L;

    /**
     * The lowest sweep tier that can see a {@code BRIDGE} at all.
     *
     * <h2>⚠ THIS IS A GATE ON CANDIDACY, NOT ON REACH — Invariant I2 is untouched</h2>
     *
     * A bridge is the way <em>onward</em> from a server, and until 2026-08-07 it was the single most
     * reliable thing the free starting instrument could find: bridges are {@code SignalStrength.HIGH},
     * so {@link #netSweepBase} gave them 0.85 at tier 1. Finding the exit was easier than finding
     * anything worth taking.
     *
     * <p>⚠ <b>Withholding it does NOT sell reach.</b> {@code NetRules.hopCeiling} still takes no sweep
     * tier and is still 1, or 2 with the Topology Mapper schematic; crossing a bridge is still earned
     * the way it always was — breach, foothold, {@code connect}, sweep again from there. What a wide
     * sweep buys is <em>knowing the door is there</em>, which is sensitivity, which is what
     * {@code docs/design/02} §1.1 puts on ethecoin. A tier that made the far side reachable would be
     * an I2 violation; this one leaves a player exactly as far from the next server as before, and
     * merely tells them which machine to work on.
     *
     * <p>⚠ <b>2, not 3.</b> At 3 the only route to the rest of the world would sit behind the dearest
     * sweep, and a player who had not bought it would have no way to learn that servers beyond their
     * own exist — which is content invisible rather than content gated. WIDE is the first upgrade a
     * new player buys, so this makes "what is out there" the thing that upgrade is <em>for</em>.
     * <strong>[PROPOSAL]</strong>.
     */
    public static final int NET_SWEEP_BRIDGE_MIN_TIER = 2;

    /**
     * How much of a machine's audibility survives being listened to from an unfavourable position.
     *
     * <h2>What this constant is, in one sentence</h2>
     *
     * The value a sweep's threshold is compared against used to be {@code HostState.detectRoll}
     * alone — a property of the <b>machine</b>. It is now
     * {@code detectRoll × (FLOOR + (1 − FLOOR) × hash(machine, vantage))} — a property of the
     * <b>pair</b>. So the same machine is easier to hear from some positions than from others, and a
     * contact a sweep missed from the rig can be picked up from a foothold next door.
     *
     * <h2>⚠ THE FOUR PROPERTIES THIS IS BUILT TO PRESERVE, none of which is optional</h2>
     *
     * <ol>
     *   <li><b>Re-sweeping is still not a reroll.</b> The perturbation is <b>hashed, never drawn</b>
     *       ({@code AddressHash}), so the same (machine, vantage, tier) gives the same answer forever.
     *       {@code SweepDeterminismTest.resweepingIsNotAReroll} is untouched, and save-scumming still
     *       buys nothing. <b>This is the whole reason it is a hash and not a die.</b>
     *   <li><b>The home floor survives exactly.</b> {@code TopologyGenerator} forces three home
     *       neighbours to {@code detectRoll = 0.0}, and the perturbation is a <b>multiply</b>, so zero
     *       stays zero from every position in the world. An additive spread would have lifted those
     *       three off the floor and broken the one guarantee a new player's first sweep rests on —
     *       which is why it is not additive.
     *   <li><b>A better instrument never loses a contact.</b> The factor does not depend on the tier,
     *       so {@code detected(T1) ⊆ detected(T2) ⊆ detected(T3)} holds as before.
     *   <li><b>Nothing is drawn, so no world is re-rolled.</b> {@code TopologyGenerator}'s draw count
     *       is a pure function of the world's shape and is untouched; existing saves keep every
     *       machine, name and document they had.
     * </ol>
     *
     * <h2>⚠ 0.55 — AND THE SENSITIVITY LADDER IS WHAT SETS IT</h2>
     *
     * The factor is bounded below rather than running to zero, because at {@code [0, 1]} the
     * multiply roughly <em>doubles</em> detection: a quiet machine at base tier would go from 35% to
     * about 72%, and the gap between a base and a deep sweep — which is the thing ethecoin is
     * actually buying (§1.1 step 4) — would collapse. At 0.55 the measured effect is a base sweep
     * finding a quiet machine 35% → ~47% of the time and a deep one 72% → ~89%, so the T1→T3 ladder
     * stays at very nearly its old 2.06×. {@code VantageDiscoveryTest} measures both rather than
     * trusting this paragraph.
     *
     * <p>⚠ It is also what keeps a <b>quiet machine quiet</b>. Past about {@code detectRoll 0.64}
     * nothing is audible at base tier from <em>any</em> position, so repositioning is a second way to
     * find things and never a substitute for the instrument. Lowering this constant is what would
     * make the sweep upgrades pointless, and it would do it silently — every screen still renders.
     *
     * <p><strong>[PROPOSAL]</strong>.
     */
    public static final double NET_SWEEP_VANTAGE_FLOOR = 0.55d;

    /**
     * The audibility of one machine from one position: {@code detectRoll}, scaled by where you stand.
     *
     * <p>⚠ <b>{@code unit} must be a HASH of the (machine, vantage) pair, never a draw</b> — see
     * {@link #NET_SWEEP_VANTAGE_FLOOR}. It is taken as a parameter rather than computed here because
     * {@code Balance} is a table of numbers and {@code AddressHash} lives with the network rules;
     * passing it in keeps this method pure and testable at both ends.
     *
     * @param detectRoll the machine's own 0–1, drawn once at world generation
     * @param unit a stable 0–1 for this machine <em>and this vantage</em>
     */
    public static double netSweepAudibility(double detectRoll, double unit) {
        double u = Math.clamp(unit, 0.0d, 1.0d);
        return detectRoll * (NET_SWEEP_VANTAGE_FLOOR + (1 - NET_SWEEP_VANTAGE_FLOOR) * u);
    }

    /**
     * How much of a server must be on the map before its bridges give themselves up.
     *
     * <h2>What this fixes, measured rather than assumed</h2>
     *
     * A bridge is found like anything else — inside the hop ceiling, past the audibility threshold,
     * inside the yield cap — and over <b>400 generated worlds</b> a first WIDE or DEEP sweep from
     * home found home's own bridge in <b>75%</b> of them. In the other quarter the way out was
     * simply not audible from where the player was standing, and since re-sweeping the same spot is
     * deliberately not a reroll ({@link #NET_SWEEP_VANTAGE_FLOOR}), it stayed invisible until they
     * happened to walk far enough. Every world <em>has</em> a bridge — that guarantee holds at
     * 400/400 — so this was never a generation bug; it was a discovery one.
     *
     * <p>Past this share of a server's machines, a sweep of {@link #NET_SWEEP_BRIDGE_MIN_TIER} or
     * better finds its bridges <b>regardless of position, roll or yield cap</b>. So the exit is
     * earned by mapping the place you are standing in, which is a thing the player can decide to do,
     * rather than by the seed being kind.
     *
     * <p>⚠ <b>It overrides three rules and each is deliberate.</b> The audibility threshold, because
     * the whole point is that position must stop mattering; the yield cap, because a bridge that was
     * detected and then sorted out of the list would make the rule fire and appear not to; and the
     * hop ceiling, because a server mapped to 73% may have its exit further away than the instrument
     * reaches, and this must not degrade into "and also be lucky about where it is". ⚠ It does
     * <b>not</b> override {@link #NET_SWEEP_BRIDGE_MIN_TIER}: a base sweep still never sees a bridge,
     * so the free instrument is unchanged and this remains something the first upgrade is for.
     *
     * <p>⚠ 0.73 rather than a round number, on explicit direction. What matters about the value is
     * that it is high enough that it cannot be reached by the first sweep or two — the share is over
     * <em>discovered</em> machines, and a server is 4–13 deep by 1–7 wide — and low enough that it
     * does not require finding the bridge first, which would make it circular.
     *
     * <p><strong>[PROPOSAL]</strong>.
     */
    public static final double NET_BRIDGE_REVEAL_SHARE = 0.73d;

    /**
     * What a NET_MAN costs. One is consumed per crossing opened, and a crossing stays open forever.
     *
     * <h2>⚠ Priced as a CONSUMABLE, and the total is bounded by the world rather than by travel</h2>
     *
     * {@code docs/design/03} §2 puts consumables well below the permanent tools, and this sits at the
     * top of that band: it is the dearest thing in the game that is used up, because what it buys is
     * a whole server rather than one action. A 5–18 server world therefore costs at most a handful
     * of these in total — the bridge keeps its NET_MAN, so the player is never paying to travel a
     * route they have already opened.
     *
     * <p>⚠ <b>This is the one place ethecoin stands between the player and content, so the number
     * matters more than its band does.</b> At {@code design/03} §1's published 0.4 EC per
     * cycle-hour a 64-cycle rig earns this in a couple of hours, and a single breach's loot
     * (45–65 EC at the top of {@code design/03} §3) covers it outright — so a player who can reach a
     * bridge can afford to open it. If mining income is ever re-tuned downward this figure has to
     * move with it, or the world quietly closes.
     *
     * <p><strong>[PROPOSAL]</strong>.
     */
    public static final BigInteger NETMAN_PRICE_EC = ec("90"); // 90 EC

    /**
     * How long a NET_MAN takes to upload to a bridge, in seconds.
     *
     * <p>⚠ Long enough to be a decision rather than a formality, because the whole duration is
     * <b>loud</b> ({@link #NETMAN_UPLOAD_NOISE_CYCLES}) and the noise is the cost. Comparable to a
     * Thorough Scan's six minutes: this is the loudest sustained thing a player does, and it is done
     * standing on somebody else's machine.
     */
    public static final long NETMAN_UPLOAD_SECONDS = 300L;

    /**
     * How much racket uploading a NET_MAN makes, on the noise meter's scale, <b>while it runs</b>.
     *
     * <p>⚠ Present-tense by construction and that is the requirement: {@code NoiseRules} counts a
     * task only while it is still running, so the meter drops back the instant the upload finishes
     * and an installed NET_MAN is silent forever after. A flag that kept a bridge loud would make
     * opening the world a permanent tax on the player's noise floor, which is a different mechanic
     * from the one being asked for.
     *
     * <p>Louder than the loudest sweep, because it is outbound traffic to a machine the player does
     * not own, sustained for five minutes, and the point of the noise is that crossing is the most
     * conspicuous thing in the game.
     */
    public static final long NETMAN_UPLOAD_NOISE_CYCLES = 14L;

    /**
     * How accurate a deep sweep's estimate of the far side is, as a percentage.
     *
     * <p>⚠ The number is <b>published to the player beside the estimate</b> rather than being an
     * internal fudge factor. An estimate presented bare is read as a count, and a player who later
     * sweeps and finds a different number concludes the map lied to them. Stating the accuracy makes
     * it a band, which is what it is.
     *
     * <p>⚠ It also bounds the error the estimate is allowed to have — {@link #netPeerEstimate} spreads
     * the true count by exactly this much — so the two figures cannot drift apart into a confident
     * accuracy attached to a wild guess.
     */
    public static final int NET_PEER_ESTIMATE_ACCURACY_PERCENT = 60;

    /**
     * A rough count of what is on the far side of a bridge, from the true count.
     *
     * <p>⚠ <b>{@code unit} must be a HASH of the bridge, never a draw.</b> Re-surveying is not a
     * reroll, for the same reason re-sweeping is not: an estimate that moved every time it was asked
     * would make repetition the cheapest way to triangulate the true number, and the accuracy figure
     * beside it would then be a lie by omission.
     *
     * <p>⚠ <b>Never returns a number below 1.</b> A bridge exists, so there is at least one machine
     * over there, and an estimate of zero would read as "nothing to find" about a server the player
     * is being invited to open.
     *
     * @param actual how many machines really are on the far server
     * @param unit a stable 0–1 for this bridge
     */
    public static int netPeerEstimate(int actual, double unit) {
        return roughCount(actual, unit, NET_PEER_ESTIMATE_ACCURACY_PERCENT);
    }

    /**
     * How accurate a sweep's guess at one machine's connection count is, as a percentage.
     *
     * <p>⚠ <b>Tighter than {@link #NET_PEER_ESTIMATE_ACCURACY_PERCENT}, and deliberately not shared
     * with it.</b> That one bands a server's whole population, which runs to twenty-odd, where ±40%
     * is still a useful band. This one bands a <em>link count</em>, which runs 1–7 ({@link
     * #NET_BRANCH_MAX} plus a parent and a chord or two) — and ±40% of 2 is "somewhere between 1 and
     * 3", which is not information, it is noise wearing a number. At ±30% a machine with five
     * connections reads as 4–7, which is enough to decide whether another sweep is worth its cycles
     * and nowhere near enough to skip taking one.
     *
     * <p>⚠ Two constants that happen to be close are not an invitation to merge them: they band two
     * different questions at two different scales, and a single figure would have to be wrong for one
     * of them.
     */
    public static final int NET_LINK_ESTIMATE_ACCURACY_PERCENT = 70;

    /**
     * A rough count of how many machines are attached to one host, from the true count.
     *
     * <h2>⚠ What licenses this to exist at all</h2>
     *
     * It is a claim about machines the player has <b>not</b> discovered, which the discovery model
     * otherwise forbids outright — {@code NetRules}' standing rule is that an undiscovered host does
     * not exist, and {@code design/18} §2.7c refuses to publish a server's completion metric for
     * exactly that reason. What makes this the licensed shape rather than that one is the same
     * argument {@code SweepReport.inRange} already stands on: it is the <b>instrument's own
     * sensitivity</b>, and it carries no address, no type, no tier and no value. A sweep that heard
     * something it could not resolve is allowed to say so; it is not allowed to say what.
     *
     * <p>⚠ <b>{@code unit} must be a HASH of the machine, never a draw</b> — {@link
     * #netPeerEstimate}'s rule, and for the same reason. Re-sweeping is not a reroll, and an estimate
     * that moved every time it was asked would make repetition the cheapest way to triangulate the
     * true number.
     *
     * <p>⚠ <b>Never returns a number below 1.</b> This is only ever published about a machine with at
     * least one connection left to find, so zero would contradict the one thing it is being asked.
     *
     * @param actual how many machines really are attached
     * @param unit a stable 0–1 for this machine
     */
    public static int netLinkEstimate(int actual, double unit) {
        return roughCount(actual, unit, NET_LINK_ESTIMATE_ACCURACY_PERCENT);
    }

    /**
     * The shared arithmetic behind every published estimate: the truth, spread by a stated accuracy.
     *
     * <p>⚠ <b>Symmetric about the truth</b>, so the estimate is as likely to be high as low and a
     * player cannot learn to read it as a floor or as a ceiling. That symmetry is what the accuracy
     * figure beside it actually means, which is why the two are computed from one constant rather
     * than tuned independently.
     */
    private static int roughCount(int actual, double unit, int accuracyPercent) {
        double u = Math.clamp(unit, 0.0d, 1.0d);
        double spread = (100 - accuracyPercent) / 100.0d;
        double error = (u * 2.0d - 1.0d) * spread;
        return Math.max(1, (int) Math.round(actual * (1.0d + error)));
    }

    /**
     * How many machines one sweep may reveal, from one vantage, at one tier.
     *
     * <h2>⚠ A CEILING ON THE YIELD, NOT A ROLL — and it is absolute, not per attempt</h2>
     *
     * {@code NetRules}' spine is that "<b>only two things move the outcome and both cost: a higher
     * sweep tier, or a closer vantage</b>". A cap that reset per sweep would make repetition the
     * cheapest of the three and turn the whole discovery loop into button-mashing. So this is the
     * total a given (vantage, tier) pair will <em>ever</em> hand over: sweep the same spot twice and
     * the second one still says it found nothing new. The rest of the server is reached by moving —
     * which is what the vantage is for — or by buying a better instrument.
     *
     * <h2>⚠ NON-DECREASING IN TIER, which is a required property rather than a nicety</h2>
     *
     * A player who buys a better instrument must never lose a contact they already had. The tier term
     * only ever adds, so {@code detected(T1) ⊆ detected(T2) ⊆ detected(T3)} survives the cap exactly
     * as it survives the detection threshold.
     *
     * <h2>The bands, and why home is generous</h2>
     *
     * <pre>
     *   depth 0 (home)  7–11     depth 3   3–7
     *   depth 1         6–10     depth 4+  1–5
     *   depth 2         5–9
     * </pre>
     *
     * Home is where the game teaches, and a first sweep that returns one machine reads as a broken
     * tool rather than as a quiet neighbourhood — the same argument {@link #NET_COUNTER_HACK_HOME}
     * and {@code MONJOB_DENSITY_HOME} both make for their own floors. Past a bridge the network stops
     * volunteering: fewer machines per look is what makes depth expensive in <em>time</em>, alongside
     * {@link #netCounterHackChance} making it expensive in risk.
     *
     * <h2>⚠ WIDENED 2026-08-08 from 1–7 to 1–11, on explicit direction, and the ceiling is the ask</h2>
     *
     * The requirement was a graph that keeps growing as a player works outward, with "1–11 at a time"
     * named as the limit. So the extremes are exactly that: <b>11 is home at deep tier at the top of
     * its band, 1 is four servers out at base tier at the bottom of its</b>. The shape between them is
     * unchanged — a fixed floor per depth, a three-wide spread, plus the tier — because that shape is
     * what makes depth cost time and the instrument buy breadth.
     *
     * <p>⚠ <b>Raising home's floor is nearly free and that is deliberate.</b> The home server seeds
     * {@link #NET_HOME_SEED_NEIGHBOURS} (5) machines at one link, so at home the cap does not bind
     * and detection is what limits a first sweep — the "something to miss, then find with a better
     * instrument" property lives in the threshold, not here. Where the widening actually pays is
     * <b>depth 1–3</b>, on the larger servers a player reaches by moving, which is exactly where the
     * ask was pointed.
     *
     * @param variation a stable 0–1 for the vantage — hashed, never drawn, so the same spot always
     *     yields the same number and no sweep is a reroll
     * <strong>[PROPOSAL]</strong>.
     */
    public static int sweepYield(int depth, int sweepTier, double variation) {
        int floor =
                switch (netDepth(depth)) {
                    case 0 -> 7;
                    case 1 -> 6;
                    case 2 -> 5;
                    case 3 -> 3;
                    default -> 1;
                };
        // Three-wide band, so every depth has some spread without any of them overlapping into
        // "home might be worse than two servers out".
        int within = (int) (Math.clamp(variation, 0.0d, 1.0d) * 3);
        int tierBonus = Math.max(0, Math.min(3, sweepTier)) - 1;
        return Math.max(SWEEP_YIELD_MIN, Math.min(SWEEP_YIELD_MAX, floor + Math.min(2, within) + tierBonus));
    }

    /**
     * The published limits on one sweep's yield — "1–11 at a time".
     *
     * <p>⚠ Named rather than left as literals inside {@link #sweepYield}, because they are the part
     * of that method somebody asked for by number: a test asserts the band and a re-tune of the
     * per-depth floors must not be able to move it by accident.
     */
    public static final int SWEEP_YIELD_MIN = 1;

    public static final int SWEEP_YIELD_MAX = 11;

    /**
     * How often a bridge on the <em>home</em> server carries somebody else's MonJob. Zero.
     *
     * <h2>⚠ ITS OWN NAMED VALUE, FOR {@link #NET_COUNTER_HACK_HOME}'S REASON, WORD FOR WORD</h2>
     *
     * That constant exists "so a re-tune of {@code netCounterHackChance} cannot make it non-zero by
     * accident", because "<b>a player who has never left home is never counter-hacked</b>: the home
     * server is where the game teaches, and a teaching space that occasionally plants a parasite on
     * the student is a teaching space they learn to avoid."
     *
     * <p>All of that transfers. The first bridge a player ever crosses is clean, always, so the
     * mechanic is introduced by <em>reaching out</em> rather than by being watched at home before
     * they know what a MonJob is. <strong>[PROPOSAL]</strong>.
     */
    public static final double MONJOB_DENSITY_HOME = 0.0d;

    /**
     * The chance a bridge at this depth carries an NPC MonJob.
     *
     * <h2>⚠ THE SAME CURVE SHAPE AS {@link #netCounterHackChance}, NOT A SECOND UNRELATED ONE</h2>
     *
     * Counter-hack runs {@code 0.00 / 0.04 / 0.10 / 0.18 / 0.28}. This is flat at home, accelerating
     * outward, flattening at the top — the same story — so the two read as one escalation rather than
     * as two systems that happen to both notice distance. A player learning "further out is worse"
     * learns it once.
     *
     * <h2>⚠ MUCH HIGHER THAN COUNTER-HACK AT EVERY DEPTH, AND DELIBERATELY</h2>
     *
     * <b>Being watched is not being attacked.</b> A MonJob costs the player nothing directly — it is
     * the thing that makes the <em>later</em> attack legible, and at tier 2 it is the only warning the
     * game gives before one. Tuning it as though it were a hazard would make deep play unbearable
     * while removing the signal that deep play is dangerous, which is exactly backwards.
     * <strong>[PROPOSAL]</strong>.
     */
    public static double monJobDensity(int depth) {
        return switch (netDepth(depth)) {
            case 0 -> MONJOB_DENSITY_HOME;
            case 1 -> 0.10d;
            case 2 -> 0.28d;
            case 3 -> 0.50d;
            default -> 0.70d;
        };
    }

    /**
     * Of the MonJobs at this depth, what fraction are tier 2 — the ones that tell the intruder.
     *
     * <h2>⚠ THE MIX SHIFTS AS WELL AS THE DENSITY, AND WITHOUT THIS THE WHOLE SYSTEM IS INVISIBLE</h2>
     *
     * A <b>tier-1 MonJob is invisible to the intruder by design</b> — it notifies its owner and tells
     * the machine that tripped it nothing at all. So density alone teaches the player nothing: cross
     * ten distant bridges watched by ten tier-1 jobs, learn nothing, and then get counter-hacked with
     * no visible cause. {@code NetRules} already names that failure for sweeps — "<em>a mechanic that
     * punishes without explaining is indistinguishable from a bug</em>".
     *
     * <p>Tier 2 is the only thing that says, in words, that the network noticed. Shifting the mix
     * outward means the warning arrives where the danger is.
     *
     * <h2>⚠ It also fits who is out there</h2>
     *
     * Tier 2's cost is that it <em>reveals the watcher</em> — which a cautious operator minds and an
     * aggressive one does not. Distant NPCs are the aggressive ones ({@code docs/design/17} §6), so
     * the mix is a fact about them rather than a dial with no fiction behind it.
     *
     * <p>Combined with {@link #monJobDensity}, the chance of being watched <em>and told</em> runs
     * about {@code 0 / 1.5% / 8% / 23% / 42%} by depth: near home almost never, deep often enough to
     * be a pattern. ⚠ Not higher — a warning that fires on every crossing stops being read, which is
     * §2.1's rationing argument one system along. <strong>[PROPOSAL]</strong>.
     */
    public static double monJobTierTwoShare(int depth) {
        return switch (netDepth(depth)) {
            case 0 -> 0.0d;
            case 1 -> 0.15d;
            case 2 -> 0.30d;
            case 3 -> 0.45d;
            default -> 0.60d;
        };
    }

    /**
     * Ethecoin prices for the two purchasable sweep tiers.
     *
     * <p>Priced against {@code docs/design/03-economy.md} §2's bands, and 25 EC sits deliberately
     * <em>between</em> the consumable band (5–15) and the mid-tier band (40–60). Both edges are
     * forced: it is a permanent tool, so it must cost more than the 15 EC Passive Sniffer, and it is
     * the first upgrade a new player buys, so pricing it at 40 would put it roughly two hours out of
     * reach on §3's 20–30 EC/hr cautious net. The Provenance Tracer's established 30 EC is the
     * precedent for a permanent tool priced off-band.
     *
     * <p>55 EC is squarely inside the mid-tier band and is about one cautious session — {@code 03}
     * §2's own rule for that band is that losing one costs an evening. <strong>[PROPOSAL]</strong>.
     */
    public static final BigInteger NET_SWEEP_WIDE_PRICE = ec("25"); // 25 EC

    public static final BigInteger NET_SWEEP_DEEP_PRICE = ec("55"); // 55 EC

    /**
     * Depth 0 is never counter-hacked, and this is a constant rather than a table row that happens to
     * be zero.
     *
     * <p>⚠ Stated as its own named value so a re-tune of {@link #netCounterHackChance} cannot make it
     * non-zero by accident. <b>A player who has never left home is never counter-hacked</b>: the home
     * server is where the game teaches, and a teaching space that occasionally plants a parasite on
     * the student is a teaching space they learn to avoid.
     */
    public static final double NET_COUNTER_HACK_HOME = 0.0d;

    /**
     * What a live breach adds to the noise meter before the player has done anything loud in it.
     *
     * <p>⚠ <b>Deliberately well below a sweep.</b> The base sweep sits at
     * {@link #NET_SWEEP_BASE_NOISE} and it is the loudest thing in the game for its cost, because a
     * sweep touches every machine within reach and announces itself to all of them. A breach is one
     * connection to one machine — quieter by nature, and quiet is a strategy inside it: a player who
     * only reads is barely making a sound.
     *
     * <p>But it is not zero, and that is the point of the floor. Being <em>inside</em> somebody
     * else's machine is an act, and {@code docs/design/08-stealth-and-noise.md} §1 charges acts. A
     * breach that registered nothing on the meter would make the safest possible play "stay in a
     * breach forever", which is the opposite of the tension {@code docs/design/05} §4 is built on.
     */
    public static final long BREACH_NOISE_FLOOR = 6L;

    /**
     * The loudest a breach can read on the meter, however badly it goes.
     *
     * <p>Below {@link #NET_SWEEP_BASE_NOISE}, so <b>the cheapest sweep is still louder than the
     * worst breach</b>. That ordering is the balance statement and it must survive re-tuning: a
     * breach that could out-shout a sweep would make the sweep ladder's whole price — 2 cycles for
     * the loudest act available — read as a mistake.
     */
    public static final long BREACH_NOISE_CEILING = 26L;

    /**
     * The burst of noise left behind by <b>abandoning</b> a breach, in cycles.
     *
     * <h2>Why walking away is not free, and why the cost is noise rather than anything else</h2>
     *
     * Aborting is a sanctioned outcome ({@code docs/design/05} §4) and it stays one — the attention
     * already spent stays spent and nothing else is taken. But dropping a live connection mid-session
     * is a conspicuous thing to do on somebody else's machine, and until 2026-07-27 it was the
     * quietest possible exit: the breach's noise contribution simply stopped. That made "open a
     * breach, look at the board, leave if it is ugly" a free reroll on difficulty.
     *
     * <p>So an abandonment radiates for a few seconds afterwards, and being loud is exactly what
     * makes a rig worth a sweep. The penalty is a window in which the player is easier to find,
     * which is a consequence they can play around rather than a number taken off them.
     *
     * <p>⚠ <b>30, which keeps the documented ordering intact.</b> Above
     * {@link #BREACH_NOISE_CEILING} — the exit is louder than the attempt was, which is the point —
     * and still below {@link #NET_SWEEP_BASE_NOISE}, so "the cheapest sweep is still louder than
     * anything a breach can do" survives.
     */
    public static final long BREACH_ABANDON_SPIKE_CYCLES = 30L;

    /** The shortest an abandonment keeps radiating. */
    public static final long BREACH_ABANDON_SPIKE_MIN_SECONDS = 5L;

    /**
     * The longest it does.
     *
     * <p>Drawn per abandonment rather than fixed, so a player cannot learn one number and wait it
     * out precisely. The band is narrow enough to stay a nuisance rather than a punishment.
     */
    public static final long BREACH_ABANDON_SPIKE_MAX_SECONDS = 20L;

    /**
     * How much of a breach's accumulated in-puzzle noise reaches the meter.
     *
     * <p>{@code BreachState.noise} is a puzzle-scale figure — a bypass is 12, an alarm is 4 — and the
     * meter is on the rig's 0-to-capacity scale. One-for-one is the honest mapping: a bypass on a
     * hundred-cycle rig moves the needle twelve percent, which is roughly what "you just kicked the
     * door" should look like from outside.
     */
    public static final double BREACH_NOISE_PER_POINT = 1.0d;

    // ------------------------------------------------------------------ the two minigames

    /**
     * How often an attempt draws Breach Protocol rather than the offset cipher, against a machine
     * <b>nothing is known about</b>.
     *
     * <h2>The offset cipher is the default, and recon is what buys the other one</h2>
     *
     * This used to be an even coin flip and the split is now earned. Walking blind into a machine
     * gets the cipher: it is the puzzle that needs no knowledge of the far side, because working an
     * offset out from ciphertext is exactly what you do when you have nothing else. Breach Protocol
     * is the puzzle of someone who knows the machine — its grid is that host's own protocol surface —
     * so it is what a filled-in port-scan report unlocks.
     *
     * <p>That gives RECON a mechanical consequence it did not have. A report was intelligence a
     * player read and acted on by hand; it now changes what the breach <em>is</em>.
     *
     * <p>⚠ <b>It buys a different puzzle, not an easier one.</b> The two are priced the same — same
     * tier, same attention budget, same strike limit, same layer count — and the intended reading is
     * that a player picks up recon to reach the puzzle they are better at, not to lower the bar. If
     * the two ever stop being comparable in difficulty, this stops being a choice and becomes a
     * discount, which is the thing to watch when either is re-tuned. <strong>[PROPOSAL]</strong>.
     *
     * @see #BREACH_PROTOCOL_SHARE_INFORMED
     */
    public static final double BREACH_PROTOCOL_SHARE = 0.0d;

    /**
     * How often a <b>fully scanned</b> machine draws Breach Protocol.
     *
     * <p>⚠ Not 1.0, deliberately. A complete report should make the protocol grid the overwhelming
     * expectation without making it a certainty — a machine that can still surprise a well-prepared
     * operator once in twenty is the fiction working, and a guaranteed puzzle means the cipher stops
     * being practised by anyone who scans. The player is told which one they drew before they spend
     * anything ({@code BoardFactory}), so the residual is a surprise they can walk away from.
     */
    public static final double BREACH_PROTOCOL_SHARE_INFORMED = 0.95d;

    /**
     * The chance of drawing Breach Protocol against a machine whose report is {@code known} complete.
     *
     * <p>Linear between {@link #BREACH_PROTOCOL_SHARE} and {@link #BREACH_PROTOCOL_SHARE_INFORMED},
     * so each of the seven findings is worth the same increment and there is no threshold to
     * discover — a player who scans one more thing sees the odds move, which is what makes the
     * relationship learnable at all.
     *
     * @param known how much of the report is filled in, 0…1
     */
    public static double breachProtocolShare(double known) {
        double fraction = Math.clamp(known, 0.0d, 1.0d);
        return BREACH_PROTOCOL_SHARE + (BREACH_PROTOCOL_SHARE_INFORMED - BREACH_PROTOCOL_SHARE) * fraction;
    }

    /**
     * How much louder the offset cipher is than Breach Protocol, as a multiplier on the layer's noise.
     *
     * <p>⚠ <b>This is the cipher's price for having no clock.</b> Breach Protocol is bounded by its
     * buffer — a handful of picks and it is over either way — while the cipher lets the player sit
     * there working through sixteen bytes for as long as they like. Something has to answer "why not
     * take all day", and the honest answer is that all day is spent <em>on somebody else's wire</em>.
     * Patience costs exposure rather than time, which keeps {@code docs/design/05} §4's decision to
     * remove the wall clock intact while still charging for the thing the clock used to charge for.
     *
     * <p><strong>[PROPOSAL]</strong>. Kept as a multiplier rather than a flat addition so the
     * relationship survives a re-tune of the underlying noise numbers: the cipher is <em>louder than
     * the grid</em>, whatever the grid turns out to cost.
     */
    public static final double BREACH_CIPHER_NOISE_FACTOR = 1.8d;

    /**
     * A breach's noise points after its puzzle class has had its say — the one place the factor lands.
     *
     * <p>⚠ Applied to the <b>total</b> rather than per action, and that is the difference between
     * "the cipher is louder" and "the cipher punishes you for pressing things". A per-action
     * multiplier would make the cipher quieter overall, because it has far fewer paid moves than a
     * grid does: three commits against eight picks. Scaling the total is what actually delivers the
     * rule, and it flows through {@code NoiseRules} to the meter and through {@code BreachRules} to
     * heat and the counter-hack roll — one number, three consequences, no chance of them disagreeing.
     */
    public static int breachNoisePoints(String puzzleClass, int rawNoise) {
        return "OFFSET_CIPHER".equals(puzzleClass) ? (int) Math.round(rawNoise * BREACH_CIPHER_NOISE_FACTOR) : rawNoise;
    }

    /** The side of a protocol grid: 5 at tier 1, 7 at the top. */
    public static int breachMatrixSize(int tier) {
        return switch (Math.max(1, Math.min(5, tier))) {
            case 1, 2 -> 5;
            case 3 -> 6;
            default -> 7;
        };
    }

    /**
     * How many picks a protocol attempt gets.
     *
     * <p>⚠ Grows with tier even though the puzzle gets harder, and that is not a mistake: a bigger
     * grid with more goals needs a longer buffer to be solvable at all. The difficulty comes from
     * needing to land <em>more sequences</em> inside it, not from having fewer slots.
     */
    public static int breachBufferSize(int tier) {
        return switch (Math.max(1, Math.min(5, tier))) {
            case 1 -> 4;
            case 2 -> 5;
            case 3 -> 6;
            case 4 -> 7;
            default -> 8;
        };
    }

    /** How many sequences a protocol attempt offers. Clearing any one of them clears the layer. */
    public static int breachGoalCount(int tier) {
        return switch (Math.max(1, Math.min(5, tier))) {
            case 1, 2 -> 1;
            case 3, 4 -> 2;
            default -> 3;
        };
    }

    /** How long the {@code goal}-th sequence is. Later goals are longer and worth more. */
    public static int breachGoalLength(int tier, int goal) {
        return Math.min(breachBufferSize(tier), 2 + Math.max(1, Math.min(3, tier)) / 2 + goal);
    }

    /**
     * How many bytes a cipher asks the player to subtract: 6 at tier 1, 16 at the top.
     *
     * <p>The full published range. Sixteen bytes of hex subtraction with borrows is a real piece of
     * work and is meant to be — it is the top of a five-tier scale, not the ordinary case.
     */
    /**
     * The chance an offset board arrives with some columns already solved.
     *
     * <h2>Why a board would come part-done at all</h2>
     *
     * The cipher's difficulty is arithmetic care, and its <em>cost</em> is time — sixteen columns of
     * subtraction is a lot of keystrokes for a layer a player may be doing for the fourth time
     * tonight. Giving a few columns away removes tedium without removing the test: the ones left
     * are the same arithmetic, and a wrong commit still costs a strike.
     *
     * <p>⚠ It does not touch {@code I7}. Proof-of-skill gates are <b>tier-gated, never
     * count-gated</b>, and a partly-filled board is the same tier it always was. What changes is how
     * long a layer takes, not what clearing one proves.
     */
    public static final double CIPHER_PREFILL_CHANCE = 0.55d;

    /** On top of the base give, a rare second helping. See {@link #CIPHER_PREFILL_CHANCE}. */
    public static final double CIPHER_PREFILL_BONUS_CHANCE = 0.12d;

    /** The base give: 1–3 columns when it happens at all. */
    public static final int CIPHER_PREFILL_BASE_MAX = 3;

    /** The bonus give: a further 1–2. */
    public static final int CIPHER_PREFILL_BONUS_MAX = 2;

    /** The most cells the generator will ever hand over, before the per-board cap. */
    public static final int CIPHER_PREFILL_CEILING = CIPHER_PREFILL_BASE_MAX + CIPHER_PREFILL_BONUS_MAX;

    /**
     * How many columns a board of this length may have given away.
     *
     * <h2>⚠ A third, and the cap is the part that matters</h2>
     *
     * Without it a 6-byte tier-1 board could arrive with 5 of its 6 columns done, which is not a
     * shorter puzzle but an absent one. A third scales the relief with the thing it is relieving:
     * the tedium is proportional to length, so the give should be too. In practice that is 2 columns
     * at tier 1 and 5 at tier 5 — so the full 1–3 plus 1–2 only ever lands on the long boards, which
     * are the only ones anybody complained about.
     */
    public static int cipherPrefillCap(int length) {
        return Math.min(CIPHER_PREFILL_CEILING, length / 3);
    }

    public static int breachCipherLength(int tier) {
        return switch (Math.max(1, Math.min(5, tier))) {
            case 1 -> 6;
            case 2 -> 8;
            case 3 -> 10;
            case 4 -> 13;
            default -> 16;
        };
    }

    /**
     * The chance a resolved breach gets you hacked back, given how loud it was and how deep.
     *
     * <h2>Noise is the whole variable, which is what makes quiet play a real strategy</h2>
     *
     * A breach that never went past {@code QUIET_READ} resolves at {@link #NOISE_BASE} and is very
     * nearly safe. One that leant on the Overflow Kit and tripped two canaries is several times that
     * and is genuinely dangerous. {@code docs/design/05} §4 makes trace the in-puzzle cost of being
     * loud; this is the out-of-puzzle one, and having both is what stops "just bypass everything"
     * being free once the trace bar is survivable.
     *
     * <p>⚠ <b>Depth zero is always zero</b>, the same rule {@link #NET_COUNTER_HACK_HOME} fixes for
     * sweeps and for the same reason: the home server is where the game teaches, and a teaching space
     * that occasionally plants a parasite on the student is one they learn to avoid.
     *
     * <p>⚠ <b>A crack is never rolled for at all</b> and the caller must not call this for one. It is
     * a breach against a process on the player's own rig — nothing leaves the machine, so there is
     * nobody to answer. That is Invariant <b>I9</b> and it is what makes the crack safe to lose
     * repeatedly and therefore usable as the tutorial ({@code docs/design/04-mining.md} §5.1).
     *
     * @param noise the breach's resolved noise — {@code BreachState.resolvedNoise}
     * @param depth the target server's depth from home
     */
    public static double breachCounterHackChance(int noise, int depth) {
        if (netDepth(depth) <= 0) {
            return NET_COUNTER_HACK_HOME;
        }
        // Scaled off the same depth table a sweep uses, so the two paths cannot drift apart, and
        // multiplied by how loud this attempt was against a quiet one. A silent breach reads about
        // a third of a sweep's chance; a very loud one reads about double it.
        double loudness = Math.max(0.35d, Math.min(2.0d, noise / (double) BREACH_NOISE_REFERENCE));
        return netCounterHackChance(depth) * loudness;
    }

    /**
     * The noise a merely-competent breach makes — the divisor {@link #breachCounterHackChance} is
     * measured against.
     *
     * <p>Roughly {@link #NOISE_BASE} plus a couple of probes and one loud tool. A breach at exactly
     * this figure carries the same risk a sweep of the same depth does; quieter is safer, louder is
     * not.
     */
    public static final int BREACH_NOISE_REFERENCE = 10;

    // ------------------------------------------------------------------ network: depth tables
    //
    // Methods, never int[]. See breachLayers above: "a balance table that any caller can silently
    // edit is worse than no table." Every one of these clamps its depth through netDepth first, so a
    // hand-edited save carrying a depth of 40 reads the deepest published row rather than crashing.

    /**
     * Clamps a server's BFS depth to the {@code 0..4} range every table below publishes.
     *
     * <p>Deeper servers read the row for 4. A depth-biased tree over seven servers can reach six, and
     * inventing rows for depths the tables were never tuned against would be tuning by extrapolation.
     */
    public static int netDepth(int depth) {
        return Math.max(0, Math.min(4, depth));
    }

    /**
     * Machines per server, by depth — the brief's "max 50 machines per server", with home smallest.
     *
     * <p>Home is 12–20 because it is the tutorial floor and has to be legible: the list view is
     * exhaustive and the graph view clamps at ten rows a layer, so a home server of fifty machines
     * would introduce the overflow marker on the first screen a player ever sees. Deeper servers grow
     * to the cap, which is where the split between the legible surface and the exhaustive one starts
     * doing real work. <strong>[PROPOSAL]</strong>.
     */
    public static int netMachinesMin(int depth) {
        return switch (netDepth(depth)) {
            case 0 -> 12;
            case 1 -> 18;
            case 2 -> 24;
            case 3 -> 30;
            default -> 34;
        };
    }

    /** @see #netMachinesMin */
    public static int netMachinesMax(int depth) {
        return switch (netDepth(depth)) {
            case 0 -> 20;
            case 1 -> 30;
            case 2 -> 38;
            case 3 -> 46;
            default -> NET_MACHINES_HARD_CAP;
        };
    }

    /**
     * Host kind for a rolled {@code u}, by depth — cumulative left to right over TERMINAL, RELAY,
     * STORE, SENTRY.
     *
     * <p>The gradient is the world getting less civilian and more instrumented as you leave home:
     * TERMINAL falls from 0.60 to 0.20 while SENTRY rises from 0.03 to 0.40. A {@code TERMINAL} is a
     * citizen's or clerk's desktop — the bread-and-butter low-level NPC the brief asks for — and a
     * {@code SENTRY} is {@code docs/design/14}'s "new defended infrastructure appearing on the graph".
     *
     * <p>⚠ {@code GATEWAY} and {@code BRIDGE} are assigned structurally and override this. The roll is
     * still made, because the RNG contract is draw-unconditionally-discard-conditionally: a draw
     * count that depended on whether a host happened to be a bridge would make the stream shape
     * depend on earlier draws, and a replay from a stored seed would stop being a replay.
     *
     * @return a {@code HostKind.name()}
     */
    public static String netHostKind(int depth, double u) {
        double terminal;
        double relay;
        double store;
        switch (netDepth(depth)) {
            case 0 -> {
                terminal = 0.60d;
                relay = 0.22d;
                store = 0.15d;
            }
            case 1 -> {
                terminal = 0.50d;
                relay = 0.22d;
                store = 0.20d;
            }
            case 2 -> {
                terminal = 0.38d;
                relay = 0.20d;
                store = 0.25d;
            }
            case 3 -> {
                terminal = 0.28d;
                relay = 0.18d;
                store = 0.26d;
            }
            default -> {
                terminal = 0.20d;
                relay = 0.15d;
                store = 0.25d;
            }
        }
        if (u < terminal) {
            return "TERMINAL";
        }
        if (u < terminal + relay) {
            return "RELAY";
        }
        if (u < terminal + relay + store) {
            return "STORE";
        }
        return "SENTRY";
    }

    /**
     * Difficulty tier for a rolled {@code u}, by depth. Means 1.30 / 1.90 / 2.90 / 3.90 / 4.60.
     *
     * <p>⚠ The bands do not merely shift, they <em>slide</em>: tier 1 is all but unreachable from
     * depth 2 and tier 5 unreachable below depth 3. That is the brief's "the more bridge hops from
     * home, the harder on average" made a floor as well as an average — a player two bridges out
     * cannot stumble onto a tutorial-grade machine, and a player at home cannot stumble onto a wall.
     *
     * <h2>⚠ NARROWED 2026-08-08 — {@code design/18} §4.1: FLAT WITHIN A SERVER, STEPPED ACROSS A
     * BRIDGE</h2>
     *
     * Each row used to spread across three tiers at roughly 40/40/20, so one server could hand a
     * player a tier 2 and a tier 4 next door to each other and the difficulty of a *place* meant
     * nothing. Each row now puts <b>55% on one tier and 40% one step below it</b>, with 5% spilling
     * one step above — so a server reads as somewhere with a character, and the real step happens
     * where the design wants it, at the bridge.
     *
     * <p>⚠ <b>The spill is kept rather than tidied away.</b> A perfectly uniform server is a server
     * with nothing to find in it; one machine in twenty being harder than its neighbours is what
     * makes the port scan's firewall reading worth paying for. Flat is the shape, not the rule.
     *
     * <p>⚠ <b>Depth is still the axis, and it is the SERVER's depth.</b> This function has always
     * taken {@code depthFromHome} and never a machine's position within its server, which is what
     * made §4.1 a narrowing rather than a rewrite — {@code design/18} §1 exists because those two
     * were being called the same word.
     *
     * <p>Home tops out at tier 2 by the table and is clamped to 2 again by the home floor pass, so
     * the clamp is belt and braces on the one server where a bad roll is unrecoverable.
     * <strong>[PROPOSAL]</strong>.
     *
     * @return a tier on the shared 1–5 scale
     */
    public static int netTier(int depth, double u) {
        return switch (netDepth(depth)) {
            case 0 -> u < 0.70d ? 1 : 2;
            case 1 -> u < 0.55d ? 2 : (u < 0.95d ? 1 : 3);
            case 2 -> u < 0.55d ? 3 : (u < 0.95d ? 2 : 4);
            case 3 -> u < 0.55d ? 4 : (u < 0.95d ? 3 : 5);
            default -> u < 0.60d ? 5 : 4;
        };
    }

    /**
     * Firewall tier for a rolled {@code u}, by depth — the flat difficulty add from
     * {@code docs/design/09-defense-and-hardening.md} §1.
     *
     * <p>⚠ <b>Never returns 4.</b> {@code BreachTarget}'s compact constructor throws outside 0..3,
     * so a fourth band here would not be a balance mistake, it would be an exception thrown while
     * building the target list — which is to say, a save that cannot render its own network.
     * <strong>[PROPOSAL]</strong>.
     */
    public static int netFirewallTier(int depth, double u) {
        return switch (netDepth(depth)) {
            case 0 -> u < 0.85d ? 0 : 1;
            case 1 -> u < 0.45d ? 0 : (u < 0.90d ? 1 : 2);
            case 2 -> u < 0.15d ? 0 : (u < 0.55d ? 1 : (u < 0.90d ? 2 : 3));
            case 3 -> u < 0.05d ? 0 : (u < 0.25d ? 1 : (u < 0.70d ? 2 : 3));
            default -> u < 0.10d ? 1 : (u < 0.45d ? 2 : 3);
        };
    }

    /**
     * Chance a host runs a Tarpit, by depth.
     *
     * <p>Zero at home, and that is the home floor rather than the table: a Tarpit surcharges every
     * action, which is exactly the defence that punishes a player still learning to read a board.
     * <strong>[PROPOSAL]</strong>.
     */
    public static double netTarpitChance(int depth) {
        return switch (netDepth(depth)) {
            case 0 -> 0.00d;
            case 1 -> 0.05d;
            case 2 -> 0.15d;
            case 3 -> 0.25d;
            default -> 0.35d;
        };
    }

    /**
     * Chance a host carries canary tokens, by depth.
     *
     * <p>A canary tags the toucher's handle rather than stopping them ({@code docs/design/09} §1), so
     * the gradient is really a gradient in how much of the deep network knows who you are.
     * <strong>[PROPOSAL]</strong>.
     */
    public static double netCanaryChance(int depth) {
        return switch (netDepth(depth)) {
            case 0 -> 0.00d;
            case 1 -> 0.08d;
            case 2 -> 0.20d;
            case 3 -> 0.32d;
            default -> 0.45d;
        };
    }

    /**
     * Chance a host is genuinely live and defended, by depth.
     *
     * <p>⚠ This is <b>ground truth</b> and it is not what the player is told. A target reports
     * {@code LIVE} only once the Traffic Analyzer has established it ({@code docs/design/07} §1,
     * §2 — "directly supports proof-of-skill"), so this number sets how much of the deep network is
     * <em>worth</em> proof-of-skill credit, not how much of it hands credit out. A generator that
     * also set {@code trafficAnalyzed} would give away a reputation-gated tool's entire product and
     * with it Invariant I7. <strong>[PROPOSAL]</strong>.
     */
    public static double netDefendedChance(int depth) {
        return switch (netDepth(depth)) {
            case 0 -> 0.05d;
            case 1 -> 0.25d;
            case 2 -> 0.50d;
            case 3 -> 0.70d;
            default -> 0.85d;
        };
    }

    /**
     * Chance a host is actually an Eye trap, by depth.
     *
     * <p>Ground truth again, and the Honeypot Detector's mandatory false-negative rate
     * ({@code docs/design/07} §2) sits on top of it — so the player's worst case is not fourteen
     * percent of deep machines being traps, it is fourteen percent being traps and a clean reading
     * never being a guarantee. That residual doubt is the product. <strong>[PROPOSAL]</strong>.
     */
    public static double netHoneypotChance(int depth) {
        return switch (netDepth(depth)) {
            case 0 -> 0.00d;
            case 1 -> 0.02d;
            case 2 -> 0.06d;
            case 3 -> 0.10d;
            default -> 0.14d;
        };
    }

    /**
     * Chance a document-eligible host carries a story fragment, by depth.
     *
     * <p>⚠ Home is zero, and that is decision <b>N-4</b> made structural rather than promised: story
     * documents are flavour plus schematic material and must never be a critical path, so the
     * flavour layer starts one bridge out and nothing on the early path can depend on it. Only
     * {@code STORE} and {@code SENTRY} hosts are eligible at all. <strong>[PROPOSAL]</strong>.
     */
    public static double netDocumentChance(int depth) {
        return switch (netDepth(depth)) {
            case 0 -> 0.00d;
            case 1 -> 0.05d;
            case 2 -> 0.15d;
            case 3 -> 0.28d;
            default -> 0.40d;
        };
    }

    /**
     * Chance one sweep provokes a counter-hack, measured against the deepest server the sweep
     * reached.
     *
     * <p>The brief's "the more bridge hops from home, ... the more likely the machine hacks the
     * player back". Rolled against the <em>candidate</em> set rather than the detected set: the
     * machines notice you probing them whether or not you learn anything, which is also the honest
     * reading of a sweep as an intrusive outbound action.
     *
     * <p>⚠ Depth 0 returns {@link #NET_COUNTER_HACK_HOME}, a named constant, and a test asserts it.
     * <strong>[PROPOSAL]</strong> above home.
     */
    public static double netCounterHackChance(int depth) {
        return switch (netDepth(depth)) {
            case 0 -> NET_COUNTER_HACK_HOME;
            case 1 -> 0.04d;
            case 2 -> 0.10d;
            case 3 -> 0.18d;
            default -> 0.28d;
        };
    }

    /**
     * Personal heat a counter-hack leaves, by the depth that provoked it.
     *
     * <p>⚠ The heat lands on the <b>player</b>, and that is correct rather than harsh: the player's
     * own sweep reached another machine, which is an intrusive outbound action and heat-bearing under
     * {@code docs/design/01-core-resources.md} §3. Invariant I9 then applies to what happens next —
     * cracking the planted miner on your own rig generates <b>no heat on any outcome</b>, so getting
     * counter-hacked hands the player the safest teaching target in the game. On a 0–100 scale, one
     * to three points means the named-hacker band still takes a campaign. <strong>[PROPOSAL]</strong>.
     */
    public static int netCounterHackHeat(int depth) {
        return switch (netDepth(depth)) {
            case 0 -> 0;
            case 1, 2 -> 1;
            case 3 -> 2;
            default -> 3;
        };
    }

    /**
     * A host's one-time payout, by breach tier, in minor units — 3–6 / 7–12 / 14–22 / 26–38 / 45–65 EC.
     *
     * <p>⚠ <b>A stock, not a flow.</b> {@code docs/design/03-economy.md} §5 rule 1 caps new faucets at
     * 70 EC/hr effective; nothing here produces a rate, because each host pays exactly once and the
     * world contains a fixed number of hosts. Home's whole pool is roughly fifteen hosts at a mean of
     * about 4.5 EC — call it 68 EC, which buys the 15 EC Passive Sniffer and the 25 EC wide sweep with
     * change, and is then gone. The deep bands are steep because the risk is: a tier-5 SENTRY at depth
     * 4 is 45–65 EC behind a firewall 2–3, likely tarpitted and canaried, with a 14% chance of being a
     * trap and a 28% chance of hacking you back for merely sweeping near it.
     *
     * <p>Interpolated rather than banded so two hosts of the same tier are rarely worth exactly the
     * same, which is what makes a payout read as a thing found rather than a number awarded.
     * <strong>[PROPOSAL]</strong>.
     *
     * @param u a roll in {@code [0, 1)}
     */
    public static BigInteger netLootWei(int tier, double u) {
        // ⚠ Written as the EC bands docs/design quotes, not as wei. These were hundredths (300L for
        // 3 EC), which was already a number nobody could check against the doc without dividing.
        String lo;
        String hi;
        switch (clampTier(tier)) {
            case 1 -> {
                lo = "3";
                hi = "6";
            }
            case 2 -> {
                lo = "7";
                hi = "12";
            }
            case 3 -> {
                lo = "14";
                hi = "22";
            }
            case 4 -> {
                lo = "26";
                hi = "38";
            }
            default -> {
                lo = "45";
                hi = "65";
            }
        }
        BigInteger floor = ec(lo);
        BigInteger span = ec(hi).subtract(floor);
        // ⚠ The roll is a fraction, so it scales the SPAN in BigDecimal rather than being applied to
        // a double amount. Rounded to whole hundredths afterwards: a loot figure with eighteen
        // digits of interpolation residue reads as machine output, and these are meant to read as
        // amounts somebody left lying about.
        BigInteger step = ec("0.01");
        BigInteger scaled = new java.math.BigDecimal(span)
                .multiply(java.math.BigDecimal.valueOf(Math.clamp(u, 0.0d, 1.0d)))
                .toBigInteger()
                .divide(step)
                .multiply(step);
        return floor.add(scaled);
    }

    /**
     * The chance one sweep tier detects one signal strength at one hop, before the hop factor.
     *
     * <p>⚠ <b>Strictly increasing in tier for every signal, and that is a required property rather
     * than a happy accident.</b> A player who buys a better instrument must never lose a contact they
     * already had — the monotonicity is what makes an upgrade legible, and a test asserts
     * {@code detected(T1) ⊆ detected(T2) ⊆ detected(T3)} from the same vantage.
     *
     * <p>The signal axis is {@code docs/design/04-mining.md} §2.1's Low / Moderate / High, generalised
     * from miners to hosts: infrastructure is chatty, stores and sentries middling, a citizen's
     * desktop quiet. So the base sweep reliably finds the loud furniture of a network — gateways,
     * relays, bridges — and unreliably finds the machines actually worth breaching, which is what the
     * upgrade is for.
     *
     * <p>⚠ <b>No entry here, at any tier, changes the hop ceiling.</b> That is Invariant I2 made
     * structural: there is no code path from ethecoin to reach, and this method returns a probability
     * that is multiplied by a hop factor after a hard gate has already decided candidacy.
     * <strong>[PROPOSAL]</strong>.
     *
     * @param sweepTier 1, 2 or 3 — see {@code SweepTier}
     * @param signal a {@code SignalStrength.name()}
     */
    public static double netSweepBase(int sweepTier, String signal) {
        String s = signal == null ? "LOW" : signal.trim().toUpperCase(java.util.Locale.ROOT);
        int t = Math.max(1, Math.min(3, sweepTier));
        return switch (s) {
            case "HIGH" ->
                switch (t) {
                    case 1 -> 0.85d;
                    case 2 -> 0.95d;
                    default -> 0.99d;
                };
            case "MODERATE" ->
                switch (t) {
                    case 1 -> 0.60d;
                    case 2 -> 0.78d;
                    default -> 0.90d;
                };
            default ->
                switch (t) {
                    case 1 -> 0.35d;
                    case 2 -> 0.55d;
                    default -> 0.72d;
                };
        };
    }

    // ------------------------------------------------------------------ botnets (design/10)

    /**
     * ⚠ <b>THESE ARE COMPILE-TIME CONSTANTS AND JAVAC INLINES THEM.</b>
     *
     * <p>Every {@code static final} primitive in this file is copied into the {@code .class} of
     * whatever reads it, so an incremental build that recompiles {@code Balance} and not {@code
     * Botnet} leaves the <em>old</em> values in the class doing the work. That produced two rounds of
     * byte-identical census output when the defence round was being tuned. After touching anything
     * below: {@code mvn -pl engine clean install}, or the numbers are from the build before last.
     */
    public static final int BOT_LEVEL_MAX = 10;

    /** The top of the chassis ladder — {@code docs/design/10} §2.1. */
    public static final int BOT_FRAME_TIER_MAX = 10;

    /**
     * Function sockets by chassis tier — §2.1. Index is the tier; index 0 is unused.
     *
     * <p>⚠ The ladder is deliberately <b>not</b> one more of everything per rung. Functions go
     * 1,1,1,2,2,2,3,3,3,4 and modifiers 0,1,2,2,3,3,3,3,4,4, so most rungs buy exactly one thing —
     * which is what makes each one a decision rather than a number going up.
     */
    public static final int[] BOT_FRAME_FUNCTIONS = {0, 1, 1, 1, 2, 2, 2, 3, 3, 3, 4};

    /** Modifier sockets by chassis tier — §5a. */
    public static final int[] BOT_FRAME_MODIFIERS = {0, 0, 1, 2, 2, 3, 3, 3, 3, 4, 4};

    /**
     * Whether a chassis survives being removed from a host without becoming damaged — §2.3.
     *
     * <p>v6, v8 and v10. ⚠ <b>It survives REMOVAL, never destruction.</b> §1a's total loss is
     * unconditional and applies to every tier: what a resilient chassis buys is that a target who
     * finds it and throws it out sends back a frame that still works, rather than one needing a
     * repair. It <b>always</b> loses its functions and modifiers, at every tier, and that is what
     * keeps §4's third cost real — the socketed things are the expensive half.
     */
    public static final boolean[] BOT_FRAME_RESILIENT = {
        false, false, false, false, false, false, true, false, true, false, true
    };

    /**
     * What a live bot holds on the <b>player's</b> rig, by chassis tier — §2.2.
     *
     * <p>⚠ This is the number {@code docs/design/10} §3 calls the self-correcting cap on botnet size,
     * and it is the reason §3 can say "no hard bot limit is needed, and none should be added". Three
     * {@code v1} bots is 18 of a starting rig's 24 cycles, and a single {@code v10} is 30 of a maxed
     * rig's 64. <b>Lowering these re-opens the question §3 closed</b>, because the only thing
     * stopping fifty bots is that fifty control channels do not fit.
     *
     * <p>⚠ It is the <em>control channel</em>, never the bot's work. The work is the host's by
     * Invariant I6 and is not the player's to account for.
     *
     * <p>⚠ Resilient tiers cost more than the tier below at the same socket count (v6 over v5, v8
     * over v7, v10 over v9), because not-becoming-damaged is a real saving and a free one would make
     * the odd-numbered rungs above them pointless.
     */
    public static final long[] BOT_FRAME_CONTROL_CYCLES = {
        0L, 6L, 8L, 10L, 14L, 16L, 18L, 20L, 22L, 24L, 30L
    };

    /**
     * The one purchasable chassis — {@code docs/design/03} §2's mid-tier band (40–60 EC).
     *
     * <p>⚠ <b>The rung above it is not for sale at any price</b>, which is the whole safety argument
     * for putting any of this on the money gate. It is the shape the compute ladder's amended I1 and
     * the firewall's <em>top purchasable</em> already use: money reaches the first rung of a ladder,
     * never a rung above the ladder. {@code BotnetTest.onlyTheFirstFrameIsForSale} fails the build if
     * a second frame rung acquires a price — if that happens, §2.0's argument has been abandoned
     * rather than amended, and it should be a red build rather than a conversation nobody had.
     */
    public static final BigInteger BOT_FRAME_V1_PRICE = ec("55");

    /** The four purchasable functions. Mid-tier band; the Injector has no price at all — §5.2. */
    public static final BigInteger BOT_KEYLOGGER_PRICE = ec("45");

    public static final BigInteger BOT_MINER_PRICE = ec("50");

    public static final BigInteger BOT_SIPPER_PRICE = ec("60");

    public static final BigInteger BOT_WATCHER_PRICE = ec("40");

    /**
     * The six modifiers — {@code docs/design/03} §2's consumable and mid-tier bands.
     *
     * <p>⚠ They are cheaper than functions, and the ordering is deliberate: a function is the
     * capability and a modifier changes how the bot carrying it survives. A modifier priced above a
     * function would say the opposite about which one matters.
     *
     * <p>⚠ <b>BedazzlePro is the cheapest thing in the game and that is honest pricing.</b> It does
     * nothing. Priced like a real modifier it would read as a stat nobody could find, and a player
     * would reasonably conclude it was broken rather than that it was a joke.
     */
    public static final BigInteger BOT_MOD_SCRAMBLER_PRICE = ec("30");

    public static final BigInteger BOT_MOD_SLEEPY_PRICE = ec("35");

    public static final BigInteger BOT_MOD_DAMPENER_PRICE = ec("42");

    public static final BigInteger BOT_MOD_THREADS_PRICE = ec("38");

    public static final BigInteger BOT_MOD_BEDAZZLE_PRICE = ec("5");

    public static final BigInteger BOT_MOD_PROTECTOR_PRICE = ec("58");

    // ── levelling ───────────────────────────────────────────────────────────────────────────────

    /**
     * What one level costs in ethecoin, multiplied by the level being bought.
     *
     * <p>⚠ <b>Money is not sufficient</b> — see {@link #BOT_LEVEL_MATERIAL}. A level is a ceiling and
     * Invariant I2 forbids buying one, so the ethecoin here is the <em>lesser</em> half of a cost
     * whose binding half cannot be bought at all.
     */
    public static final BigInteger BOT_LEVEL_PRICE_PER_STEP = ec("18");

    /**
     * Schematic material per level — {@code docs/design/02} §2.2's currency, awarded only by
     * {@code SalvageRules} against a live target at or above {@link #SCHEMATIC_MATERIAL_MIN_TIER}.
     *
     * <p>⚠ <b>This is the constant that holds I2 for the whole function ladder.</b> Material is not
     * for sale and cannot be farmed off soft targets (Invariant I13), so no amount of ethecoin
     * advances a function by itself. Delete this requirement and every level in §5 — including the
     * Injector's, which hands out compute — becomes purchasable, silently, with every screen still
     * rendering the right price.
     */
    public static final int BOT_LEVEL_MATERIAL = 2;

    /** Ethecoin for the step from {@code level} to {@code level + 1}. Rises linearly with the level. */
    public static BigInteger botLevelPrice(int level) {
        int from = Math.max(1, Math.min(BOT_LEVEL_MAX, level));
        return BOT_LEVEL_PRICE_PER_STEP.multiply(BigInteger.valueOf(from));
    }

    // ── keylogger (§5.1) ────────────────────────────────────────────────────────────────────────

    /**
     * Chance per cadence that a Keylogger learns one unlearned port-scan rung, by level.
     *
     * <p>Index 0 is unused. 10% at L1 rising to <b>90% at L10</b>, which is the published ceiling —
     * a Keylogger is never certain, because a function that always worked would make the port-scan
     * ladder's prices read as a mistake.
     */
    public static final double[] BOT_KEYLOGGER_CHANCE = {
        0.00d, 0.10d, 0.19d, 0.28d, 0.37d, 0.46d, 0.54d, 0.63d, 0.72d, 0.81d, 0.90d
    };

    /**
     * How often it rolls.
     *
     * <p>⚠ A <b>cadence</b>, never a per-tick chance. Rolling every tick makes a faster-ticking
     * client learn faster and hands a three-day absence exactly one roll — the same defect
     * {@code AmbientIntrusion} records, and invisible in play either way.
     */
    public static final long BOT_KEYLOGGER_PERIOD_SECONDS = 180L;

    // ── injector (§5.2) ─────────────────────────────────────────────────────────────────────────

    /**
     * Tool cycles an installed Injector offloads onto its host, by level. 4 at L1 to 40 at L10.
     *
     * <p>⚠ <b>Never available to mining.</b> The exclusion is enforced at the reservation, by
     * consumer, not by convention — offloaded cycles that could mine would close the flywheel
     * Invariant I1 exists to prevent (mine, buy bots, offload, mine faster). {@code
     * BotnetTest.offloadNeverReachesMining} is what keeps it enforced.
     */
    public static final long[] BOT_INJECTOR_CYCLES = {0L, 4L, 8L, 12L, 16L, 20L, 24L, 28L, 32L, 36L, 40L};

    /**
     * Per-hour chance the host's operator installs the dropped package.
     *
     * <p>⚠ A <b>rate per hour</b> resolved as {@code 1 - e^(-rate × hours)}, never a chance per tick,
     * for {@link #BOT_KEYLOGGER_PERIOD_SECONDS}'s reason.
     *
     * <p>⚠ Deliberately not the player's to force and deliberately not certain. §5.2: an offload is
     * something you keep only while nobody notices, and a package that always ran would make the
     * Injector a purchase rather than a gamble.
     */
    public static final double BOT_INJECTOR_INSTALL_PER_HOUR = 0.45d;

    // ── miner (§5.3) ────────────────────────────────────────────────────────────────────────────

    /**
     * Host cycles a bot Miner draws, by level. 8 at L1 to 40 at L10.
     *
     * <p>⚠ The <b>host's</b> cycles (Invariant I6), which is why these can be large where {@link
     * #BOT_FRAME_CONTROL_CYCLES} must stay small. The player pays the control channel; the machine
     * pays for the mining.
     */
    public static final long[] BOT_MINER_HOST_CYCLES = {0L, 8L, 12L, 16L, 20L, 24L, 28L, 32L, 36L, 38L, 40L};

    /** The level at which auto-deposit starts — §5.3 says L5. */
    public static final int BOT_MINER_AUTODEPOSIT_MIN_LEVEL = 5;

    /**
     * Share of the buffer an auto-deposit moves, by level. Zero below L5, rising to <b>0.45</b>.
     *
     * <p>⚠ The 45% ceiling is the published one and it is not a rounding target. Auto-deposit is a
     * convenience, never a bypass: collecting by hand has to stay how most of the money moves, or the
     * buffer stops being seizable in any way that matters ({@code docs/design/04} §2) and the player
     * stops looking at the bot at all.
     */
    public static final double[] BOT_MINER_AUTODEPOSIT_SHARE = {
        0.00d, 0.00d, 0.00d, 0.00d, 0.00d, 0.15d, 0.20d, 0.26d, 0.32d, 0.39d, 0.45d
    };

    /** Seconds between auto-deposits, by level — falls as the level rises. Zero means never. */
    public static final long[] BOT_MINER_AUTODEPOSIT_SECONDS = {
        0L, 0L, 0L, 0L, 0L, 3600L, 3000L, 2400L, 1800L, 1500L, 1200L
    };

    // ── sipper (§5.4) ───────────────────────────────────────────────────────────────────────────

    /**
     * The tax rate, by level. 3% at L1 to the published maximum of <b>30%</b> at L10.
     *
     * <p>⚠ <b>THE RATE IS NOT THE BOUND. THE RATE IS NEVER THE BOUND.</b> NPC transactions in this
     * game are derived rather than stored — {@code MempoolRules.npcFeeWei} is a pure function of
     * height and index — so a percentage of them is a percentage of an invented, unbounded stream.
     * What bounds this is {@link #BOT_SIPPER_MAX_WEI_PER_HOUR}, and raising the rate without reading
     * that constant changes nothing except on the poorest hosts.
     */
    public static final double[] BOT_SIPPER_TAX = {
        0.00d, 0.03d, 0.06d, 0.09d, 0.12d, 0.15d, 0.18d, 0.21d, 0.24d, 0.27d, 0.30d
    };

    /**
     * ⚠ <b>THE CEILING THAT STOPS THE SIPPER BEING AN ETHECOIN PRINTER — read §5.4 before moving
     * it.</b>
     *
     * <p>Total value a maxed Sipper may take in an hour, whatever the host's traffic. Lower levels
     * are clamped proportionally, so there is exactly one number to tune.
     *
     * <p>It is calibrated against the income floor rather than chosen: a {@code v1} chassis holds
     * {@code BOT_FRAME_CONTROL_CYCLES[1]} = 6 cycles, which self-mining would turn into
     * {@code 6 × SELF_MINING_WEI_PER_CYCLE_HOUR} = 2.4 EC an hour with no noise, no risk and nothing
     * to lose (Invariant <b>I4</b>). A bot pools noise, can be destroyed, and cost 55 EC to put
     * there, so it is allowed to beat that — by {@link #BOT_INCOME_RISK_MULTIPLE} and no more.
     * {@code BotnetTest.theSipperCannotOutEarnTheFloorByMoreThanTheRiskMultiple} derives the bound
     * from those constants rather than restating this number, so a re-tune of self-mining income
     * moves the assertion with it.
     */
    // ⚠ 4.5, NOT 5, AND THE TEST IS WHY. The first version of this constant was 5 EC and
    // `BotnetTest.theSipperCannotOutEarnTheFloor` failed on its first run: 6 control-channel cycles
    // at 0.4 EC per cycle-hour is 2.4 EC, and the risk multiple allows 4.8. It was 0.2 EC over — an
    // amount nobody would ever notice in play, on the one constant in this system whose whole job is
    // to stop an ethecoin printer. The bound is DERIVED in the test rather than restated, which is
    // the only reason a 4% overshoot was visible at all.
    public static final BigInteger BOT_SIPPER_MAX_WEI_PER_HOUR = ec("4.5");

    /**
     * How much better than the safe income floor a risked, noisy, losable asset may pay.
     *
     * <p>⚠ Past about 3 the correct play stops being "mine, and run bots for reach" and becomes "run
     * bots for money", which inverts {@code docs/design/03}'s whole income shape. It is a multiple
     * rather than a target so that the two things it relates stay related when either moves.
     */
    public static final double BOT_INCOME_RISK_MULTIPLE = 2.0d;

    /**
     * Value that moves through an ordinary host in an hour, per tier — the stream a Sipper taxes and
     * a Watcher reports (§5.6).
     *
     * <p>⚠ <b>Derived, never stored, and there is exactly one derivation.</b> Two would be two
     * answers to "what did this machine do", and the player can see both at once: a tax on a
     * transaction the Watcher never mentioned is the kind of contradiction that makes a whole panel
     * untrustworthy.
     *
     * <p>The spread is what makes <em>where</em> you put a Sipper a decision. On a tier-1 machine the
     * tax binds; on a tier-5 machine {@link #BOT_SIPPER_MAX_WEI_PER_HOUR} does.
     */
    public static final BigInteger[] BOT_HOST_VALUE_PER_HOUR = {
        BigInteger.ZERO, ec("20"), ec("35"), ec("55"), ec("80"), ec("110")
    };

    /** Value moving through a host of {@code tier} in an hour, clamped to the table. */
    public static BigInteger botHostValuePerHour(int tier) {
        int t = Math.max(0, Math.min(BOT_HOST_VALUE_PER_HOUR.length - 1, tier));
        return BOT_HOST_VALUE_PER_HOUR[t];
    }

    // ── watcher (§5.5) ──────────────────────────────────────────────────────────────────────────

    /**
     * How often a Watcher files a report.
     *
     * <p>⚠ Slower than the Keylogger's cadence on purpose. A Watcher that filed every few seconds
     * would bury the rig log, which is where the reports land and where {@code Notifications} drains
     * them from — the same "an INFO line every minute buries the client log within an hour" problem
     * the Bluesky poll had to solve.
     */
    public static final long BOT_WATCHER_PERIOD_SECONDS = 600L;

    /** Reports kept before the oldest are trimmed. Bounded like every other log in the save. */
    public static final int BOT_REPORT_LIMIT = 120;

    // ── modifiers (§5a) ─────────────────────────────────────────────────────────────────────────

    /** Every modifier but the scrambler has five levels; the scrambler is a binary — see {@code BotModifier}. */
    public static final int BOT_MODIFIER_LEVEL_MAX = 5;

    /**
     * How likely the host's operator is to notice a bot, per hour, before any modifier.
     *
     * <p>⚠ A <b>rate per hour</b>, resolved as {@code 1 - e^(-rate × hours)}. A chance per tick makes
     * a faster-ticking client's bots get caught more often and hands a three-day absence exactly one
     * roll — the defect this file warns about in four other places.
     *
     * <p>⚠ It is deliberately non-zero with every modifier fitted. A bot that could never be found is
     * a bot that can never be lost, and §1a's total loss is the cost §4's whole argument rests on.
     */
    public static final double BOT_DISCOVERY_PER_HOUR = 0.09d;

    /** What the exe-name scrambler multiplies the discovery rate by. Binary — fitted or not. */
    public static final double BOT_SCRAMBLER_DISCOVERY_FACTOR = 0.55d;

    /**
     * What Sleepy multiplies the discovery rate by, and what it costs in speed, by level.
     *
     * <p>⚠ The two tables move together on purpose. The stealth is bought with the slowdown, and a
     * level that took one without the other would be strictly better than the level below it — at
     * which point nobody chooses, they just fit the highest they own.
     */
    public static final double[] BOT_SLEEPY_DISCOVERY_FACTOR = {1.00d, 0.80d, 0.64d, 0.50d, 0.38d, 0.28d};

    /** Fraction of normal speed at each Sleepy level. L5 is a bot that barely runs and is barely findable. */
    public static final double[] BOT_SLEEPY_SPEED = {1.00d, 0.88d, 0.76d, 0.64d, 0.52d, 0.40d};

    /**
     * The share of a bot's noise a Dampener lets through, by level.
     *
     * <p>⚠ <b>The floor is 5% and it is not a rounding artefact.</b> {@code docs/design/10} §1 pools
     * all bot noise into the player's aggregate — "more bots, louder you". A modifier that reached
     * zero would make a fully dampened network free reach, and the noise model's answer to "why not
     * run fifty of them" would collapse to compute alone.
     */
    public static final double[] BOT_DAMPENER_NOISE_SHARE = {1.00d, 0.70d, 0.50d, 0.32d, 0.18d, 0.05d};

    /** Speed multiplier from EfficientMultiThreading, by level — 25% a rung, as specified. */
    public static final double[] BOT_MULTITHREAD_SPEED = {1.00d, 1.25d, 1.50d, 1.75d, 2.00d, 2.25d};

    /**
     * Extra noise EfficientMultiThreading costs, by level.
     *
     * <p>⚠ It rises faster than the speed does. Speed is linear at +25% a rung and noise is not, so
     * stacking it past L3 is a real decision rather than an obvious one — the shape every "more of
     * the same, louder" trade in this game uses.
     */
    public static final double[] BOT_MULTITHREAD_NOISE = {1.00d, 1.30d, 1.70d, 2.20d, 2.90d, 3.80d};

    /**
     * Per-level chance a Protector blocks one removal attempt, applied once per level.
     *
     * <p>⚠ <b>"Each level increases the protection by 30%" is read as a further 30% ROLL, not as
     * +30 percentage points.</b> Read literally, L4 would be 120% — certain — and L4 and L5 would be
     * indistinguishable from each other and from immortality. Compounding gives 30 / 51 / 66 / 76 /
     * 83%, so every rung buys something and none of them buys certainty. §5a records the reading.
     */
    public static final double BOT_PROTECTOR_BLOCK_PER_LEVEL = 0.30d;

    /** A Protector's charges — one per level. A blocked removal spends one; at zero the next one lands. */
    public static int botProtectorCharges(int level) {
        return Math.max(0, Math.min(BOT_MODIFIER_LEVEL_MAX, level));
    }

    /** The chance a Protector of {@code level} blocks a removal. Compounding — see the constant above. */
    public static double botProtectorBlockChance(int level) {
        int l = Math.max(0, Math.min(BOT_MODIFIER_LEVEL_MAX, level));
        return 1.0d - Math.pow(1.0d - BOT_PROTECTOR_BLOCK_PER_LEVEL, l);
    }

    /**
     * How long a blocked removal hides the bot, in seconds, by Protector level — 1 to 5 minutes.
     *
     * <p>⚠ Only when a Sleepy is also fitted. The Protector buys the <em>time</em>; Sleepy is what
     * the bot uses it for. Without one there is nothing to go quiet with, and granting invisibility
     * anyway would make Sleepy's own speed penalty avoidable.
     */
    public static final long[] BOT_PROTECTOR_HIDE_SECONDS = {0L, 60L, 120L, 180L, 240L, 300L};

    /** Per-run chance BedazzlePro fires, by level. */
    public static final double[] BOT_BEDAZZLE_CHANCE = {0.00d, 0.10d, 0.20d, 0.32d, 0.46d, 0.60d};

    /**
     * Personal heat each BedazzlePro trigger adds — {@code docs/design/10} §5a.
     *
     * <h2>⚠ HIDDEN FROM THE PLAYER BY DECISION, and it is the only cost in this system that is</h2>
     *
     * Nothing names it: not the modifier's effect line, not the rig log, not the market description.
     * That is deliberate and has precedent — {@code OFFLINE_MINING_WIN_WEIGHT} is invisible for the
     * same reason, and an undiscovered parasite's stolen cycles are unattributed on purpose so that
     * {@code docs/design/04} §3.1's "the numbers do not add up" stays a thing a player can discover
     * rather than be told.
     *
     * <h2>⚠ It does NOT scale with level, and it does not need to</h2>
     *
     * {@link #BOT_BEDAZZLE_CHANCE} already rises 0.10 → 0.60, so a level-5 module triggers six times
     * as often and therefore costs six times as much attention. A second per-level table would be a
     * second place for the scaling to live and would compound it quadratically.
     *
     * <h2>The size, and why it is this small — MEASURED, not estimated</h2>
     *
     * {@code BedazzleCensus}, 2026-08-11, one bot with one function over a week of continuous play:
     * <b>0.08 heat/hour at L1 rising to 0.58 at L5</b>. Invisible across a session, real across a
     * campaign — a week of solid play at L5 is 98 points, and a breach leaves two or three.
     *
     * <p>⚠ Raising it much past this stops being a joke's price and becomes a trap: the player has no
     * way to attribute the heat, so a fast-climbing hidden source reads as a bug in the heat system
     * rather than as a consequence of a choice they made. <b>That census is the only feedback channel
     * this constant has</b> — a hidden cost tuned wrong produces no screen anybody could report.
     *
     * <p>⚠ The fractional part is carried on {@code GameSave.heatResidue}. Rounding per trigger gives
     * either zero forever or a whole point every time — the first makes the cost imaginary and the
     * second makes it fifteen times too large.
     */
    public static final double BOT_BEDAZZLE_HEAT = 0.05d;

    /**
     * How often BedazzlePro rolls, per fitted function.
     *
     * <p>⚠ Its <b>own</b> cadence, deliberately not "whenever a function did something". A Keylogger
     * stops returning work once the host's recon file is full, so hooking the roll to a function's
     * success made the module quietly stop costing anything after a few hours — on the bot that had
     * been running longest, which is the worst place for a cost to evaporate.
     *
     * <p>⚠ Rolled <b>once per fitted function</b>, which is what "per function execution" means: a
     * three-function bot is three times as flamboyant as a one-function bot and pays for it.
     */
    public static final long BOT_BEDAZZLE_PERIOD_SECONDS = 180L;

    // ── damage, repair and recycling (§2.3) ─────────────────────────────────────────────────────

    /**
     * Ethecoin to repair a damaged chassis, by tier.
     *
     * <p>⚠ Ethecoin ALONE, and no schematic material, which is the one place this system deliberately
     * lets money undo something. A repair restores a chassis to what it already was — it buys back a
     * <em>replaceable</em>, which is exactly what {@code docs/design/02} §2.1 puts on the ethecoin
     * gate. It raises no ceiling: the functions and modifiers are gone either way, and those are the
     * ladder.
     *
     * <p>⚠ It is priced above what recycling returns, or scrapping a damaged frame and building a
     * fresh one would dominate repairing at every tier and the damaged state would mean "destroyed"
     * with extra steps.
     */
    public static final BigInteger[] BOT_REPAIR_PRICE = {
        BigInteger.ZERO,
        ec("18"), ec("26"), ec("34"), ec("46"), ec("58"),
        ec("70"), ec("86"), ec("102"), ec("124"), ec("150")
    };

    /** Parts a recycled chassis yields, by tier. */
    public static final int[] BOT_RECYCLE_PARTS = {0, 1, 2, 3, 4, 6, 7, 9, 11, 13, 16};

    /**
     * Parts a repair costs instead of ethecoin, when the player would rather spend them.
     *
     * <p>⚠ Deliberately more than {@link #BOT_RECYCLE_PARTS} returns for the same tier. Otherwise
     * recycling a damaged frame and repairing another with the proceeds is a perpetual motion
     * machine — the same arbitrage shape {@code MarketDeals} needed an arithmetic ceiling for.
     */
    public static int botRepairParts(int tier) {
        int t = Math.max(1, Math.min(BOT_FRAME_TIER_MAX, tier));
        return BOT_RECYCLE_PARTS[t] + 2;
    }

    // ── loss (§1a) ──────────────────────────────────────────────────────────────────────────────

    /**
     * Chance a destroyed bot yields schematic material — §1a's "low chance of salvage".
     *
     * <p>⚠ The drop is <b>additionally</b> gated on engagement tier by {@code SalvageRules}
     * (Invariant I13). Without that gate the optimal play is to build the cheapest junk bot and feed
     * it to a loss, turning bot sacrifice into a grind path toward ceiling raises — which is the
     * exact failure the gate rule exists to prevent. This constant is the second condition, never
     * the only one.
     */
    public static final double BOT_LOSS_SALVAGE_CHANCE = 0.20d;
}
