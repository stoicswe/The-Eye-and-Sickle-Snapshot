package io.github.stoicswe.eyeandsickle.engine.state;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Generated ground truth for one machine on the network.
 *
 * <h2>⚠ This is NOT the player's knowledge — {@link NodeState} is</h2>
 *
 * The two classes describe the same machine from opposite sides, and keeping them apart is the whole
 * discovery model. This one is written once, at world generation, and is complete: it knows the
 * host's type, its defences, whether it is a honeypot, and the fixed roll that decides whether a
 * given sweep can see it. {@link NodeState} is written by <em>recon</em>, holds only what the player
 * has paid to learn, and is the only list {@code ls /net/}, tab completion, {@code Targets.available}
 * and the network map are built from — {@code NodeState}'s own class javadoc calls that rule
 * load-bearing rather than tidy, and three subsystems depend on it.
 *
 * <p>So a host that has never been detected exists here and nowhere else, and there is deliberately
 * no aggregate anywhere that counts undiscovered hosts by type, tier or value. The single number a
 * sweep may report about what it did <em>not</em> find is how many machines were inside the hop
 * ceiling — the instrument's own sensitivity, which carries no address, type, tier or value.
 *
 * <h2>Two fields that must never be copied across</h2>
 *
 * {@link #defended} and {@link #honeypot} are truth. Their counterparts on {@link NodeState} —
 * {@code trafficAnalyzed} and {@code honeypotSuspected} — are <em>products</em>: the Traffic
 * Analyzer's and the Honeypot Detector's respectively ({@code docs/design/07-recon-tools.md} §1,
 * §2). A generator that set both sides would hand out proof-of-skill credit for free, because
 * {@code Targets.available} reports {@code LIVE} exactly when {@code trafficAnalyzed && defended}
 * and Invariant I7 requires a live or defended target for credit. Nothing in
 * {@code io.github.stoicswe.eyeandsickle.engine.net} writes either counterpart, and a test asserts it.
 *
 * <h2>Strings rather than enums, for the same reason every other {@code state} class uses them</h2>
 *
 * This is a JSON document that outlives the code that wrote it. An unknown enum constant is a hard
 * deserialisation failure — a save the player cannot open — rather than a field the engine can
 * defend against. {@link #kind} holds a {@code HostKind.name()} and {@link #signal} a
 * {@code SignalStrength.name()}; both are translated at the {@code protocol} boundary and clamped
 * on the way out.
 */
public final class HostState {

    /**
     * The join key between ground truth and player knowledge.
     *
     * <p>{@code 10.<server>.<index/254>.<2 + index%254>} — see
     * {@code io.github.stoicswe.eyeandsickle.engine.net.TopologyGenerator}. Unique across the whole
     * topology, which is what lets {@link NodeState#address} be a foreign key without a second id.
     */
    public String address = "";

    /**
     * A neutral machine name: {@code <server name>-<index>}.
     *
     * <p>⚠ It deliberately does <b>not</b> encode {@link #kind}. A label like {@code terminal-07}
     * would name the host's type at the moment a sweep discovered it, and naming types is what the
     * 15 EC Passive Sniffer sells ({@code docs/design/07-recon-tools.md} §1). Deleting a purchased
     * tool's product at the point of rendering is {@code docs/design/02-unlock-gates.md} §5's
     * pricing check failing.
     */
    public String label = "";

    /** Which {@link ServerState} this sits on. */
    public String serverId = "";

    /** {@code HostKind.name()}. Ground truth; the player sees {@code UNKNOWN} until a tool says otherwise. */
    public String kind = "TERMINAL";

    /**
     * Every host this one is linked to, by address. Symmetric — the generator writes both sides.
     *
     * <p>Undiscovered hosts still conduct: hop distance is BFS over <em>this</em> graph, not over
     * the discovered subgraph, so a machine the player has never seen can still be the reason a
     * further one is two hops away rather than three.
     */
    public List<String> links = new ArrayList<>();

    /**
     * The address on the other server this bridge advertises, or {@code ""}.
     *
     * <p>⚠ One host can be picked as the bridge endpoint for more than one server edge — the draw
     * that picks it is unconditional (the RNG contract forbids a rejection loop), so collisions are
     * rare but real. Both links are written and both remain traversable; only the <em>advertised</em>
     * peer is the last one assigned. Nothing reads this field for routing, so the consequence is
     * cosmetic: the map names one of the two networks on the far side rather than both.
     */
    public String bridgePeer = "";

    /**
     * Who runs this machine, when that is not simply derived from its address.
     *
     * <h2>⚠ Empty is the normal case and means "derive it"</h2>
     *
     * Every ordinary machine's account comes from {@code NpcNames.operator(address)} — derived, never
     * stored, so the pool can be edited without a migration. This field exists for the one machine
     * whose account is not a free choice: a <b>bridge</b>, whose account name is the character half of
     * the name of the server on its far side ({@code design/18} §2.7). That is a fact about two
     * machines on two different servers, so it cannot be derived from this host's address alone.
     *
     * <h2>⚠ STORED for {@code VirtualFs}'s sake, and that is the whole reason</h2>
     *
     * {@code VirtualFs.hostUser} takes a {@link HostState} and nothing else — the package is a pure
     * function of one machine, which is what lets a filesystem be generated without the world being
     * threaded through it. Deriving a bridge's account at read time would mean passing the topology
     * into {@code listHost}, and from there into the shell, the file manager and the scanner. Storing
     * one string on the one kind of host that needs it is the smaller price by a wide margin.
     *
     * <p>⚠ It is a <b>name</b>, so it has no mechanical consequence and
     * {@code TopologyGenerator.relabelLegacy} may fill it on an existing character — the same
     * sanctioned exception machine and server names already take.
     */
    public String operator = "";

    /** Breach difficulty, on the shared 1–5 scale. Generated inside range; consumers clamp anyway. */
    public int tier = 1;

    /**
     * {@code SignalStrength.name()} — {@code docs/design/04-mining.md} §2.1's established vocabulary,
     * generalised from miners to hosts.
     *
     * <p>Derived at generation from {@link #kind} rather than drawn, and deliberately not a second
     * {@code noise} field: noise in this game is a <em>player-attribution</em> scalar
     * ({@code docs/design/01-core-resources.md} §3.2, "noise is what reaches other machines"), and
     * giving a node one would quietly redefine the term for everything else that reads it.
     *
     * <p>A host that is currently carrying a deployed miner reads one level louder, which is §2.1's
     * own rule — a bigger, more valuable miner is louder — generalised to the machine under it. That
     * step-up is applied at read time, not stored here, because miners come and go.
     */
    public String signal = "LOW";

    /** 0–3. ⚠ {@code BreachTarget}'s compact constructor throws above 3; never generate a 4. */
    public int firewallTier = 0;

    /** Surcharges every intruder action rather than cutting the budget ({@code docs/design/09} §1). */
    public boolean tarpit = false;

    /** Alerts the owner and tags the toucher's handle — the evidence path in {@code docs/design/12}. */
    public boolean canaries = false;

    /**
     * Whether this machine is actually live and defended.
     *
     * <p>⚠ Ground truth, and never copied to {@link NodeState#trafficAnalyzed}. See the class note.
     */
    public boolean defended = false;

    /**
     * Whether this machine is actually an Eye trap.
     *
     * <p>⚠ Ground truth, and never copied to {@link NodeState#honeypotSuspected}. That flag is the
     * Honeypot Detector's product, and {@code docs/design/07-recon-tools.md} §2 requires the detector
     * to have a false-negative rate — "a perfect detector removes the fear the traps exist to
     * create". A generator that set the suspicion directly would be a perfect detector wearing the
     * generator's clothes.
     */
    public boolean honeypot = false;

    /**
     * The fixed roll a sweep's detection threshold is compared against.
     *
     * <p>⚠ <b>Drawn once, at world generation, and never re-drawn.</b> This is the whole defence
     * against save-scumming discovery, and it is a defence by construction rather than by cooldown:
     * a sweep makes no detection draw at all, it only compares this stored number against a threshold
     * set by the sweep tier, the host's signal and the hop distance. Re-running the same sweep from
     * the same vantage therefore returns a bit-identical candidate set, forever. Quitting without
     * saving changes nothing, because the roll predates the sweep by the whole game.
     *
     * <p>The only two things that move the answer both cost something: a higher sweep tier (ethecoin,
     * plus its own compute, duration and noise) or a closer vantage (a breach, a foothold and a
     * {@code connect}). That is the mechanic, and {@code Rng}'s own javadoc explains why an
     * alternative that re-rolled would make discovery advisory.
     *
     * <p>Defaults to 1.0 — outside every threshold — so a hand-edited or truncated save produces an
     * undiscoverable host rather than a free one.
     */
    public double detectRoll = 1.0;

    /**
     * A one-time payout on a successful breach, in minor units.
     *
     * <p>⚠ <b>A stock, not a flow, and that distinction is the whole economic argument.</b>
     * {@code docs/design/03-economy.md} §5 rule 1 caps any new faucet at 70 EC/hr effective, and
     * §5 rule 3 separates transfers from faucets. This is neither a faucet nor exactly a transfer:
     * it is a finite quantity of currency placed in the world at generation, each unit collectable
     * exactly once ({@link #looted}), which cannot produce a rate at all because nothing about it
     * repeats. The home server's entire pool is ~68 EC — the Passive Sniffer and the T2 sweep with
     * change — and then it is gone.
     *
     * <p>⚠ This reads against {@code BreachRules.resolveOffensive}'s standing note about faucets, and
     * both survive: that engine mints nothing, and the currency here is picked up off the <em>host</em>
     * by {@code NetRules.reconcileFootholds} rather than created by the attempt. Logged in
     * {@code docs/design/15-open-questions.md} §3.
     *
     * <p>⚠ <b>AMENDED 2026-08-09</b> — this said "the breach engine still mints a data cache and no
     * currency". The cache is gone (it was inert and only consumed a storage slot), so <b>this field
     * and the foothold are now the whole reward for taking a machine</b>. Which means the stock/flow
     * argument above is no longer one half of a pair: it is the entire reason an offensive breach can
     * pay at all without becoming the faucet §5 rule 3 forbids. Re-read it before changing this.
     */
    public BigInteger lootWei = BigInteger.ZERO;

    /** Whether the payout has been taken. Checked before crediting, so a host pays exactly once. */
    public boolean looted = false;

    /**
     * The story fragment this host carries, or {@code ""}.
     *
     * <p>An id only — the prose is a client resource. Rules never carry prose, and a document body
     * in the save would be duplicated into every player's disk copy of a file the client already
     * ships. Home carries none at all, which is decision N-4 made structural: the flavour layer
     * starts one bridge out, so nothing on the early critical path depends on it.
     */
    public String documentId = "";

    public boolean documentTaken = false;

    /**
     * When the document was pulled, or null.
     *
     * <p>⚠ Not in the original field list, and added for one reason: {@code NetDocument} carries a
     * {@code recoveredAt}, and {@code TopologyState.documents} stores ids in order without times.
     * Reconstructing the time from the id would be a guess, and reconstructing the <em>source</em>
     * from the id is ambiguous the moment two hosts draw the same fragment — which they will, since
     * twelve ids are spread across up to 350 hosts. Ordering by this field and falling back to the
     * address is the only reading that is correct for duplicates.
     */
    public Instant documentTakenAt;

    /** Whether a sweep has ever detected this host. Gates its existence in {@link NodeState}. */
    public boolean discovered = false;

    /**
     * Whether this host's type is established.
     *
     * <h2>⚠ VESTIGIAL SINCE 2026-08-09 — the answer lives on {@code NodeState.kind} now</h2>
     *
     * "The player has established what this machine is" is the player's knowledge, so it belongs on
     * the row in {@code knownNodes} that the map and {@code Targets.role} both read, and
     * {@code NetRules.identify} is the one writer of it. This flag was the second answer to the same
     * question and reached only the map, so a machine could read TERM in one window and blank in
     * another.
     *
     * <p>The only writer left is {@code TopologyGenerator}, which sets it on the player's own rig —
     * and {@code NetRules.sighting} already answers that case from {@code self}, so nothing depends
     * on it. Kept rather than deleted because it is a persisted field and removing one is a
     * save-shape change; do not add a writer.
     */
    public boolean identified = false;

    /** Whether the player holds a foothold here, and may therefore {@code connect} to it. */
    public boolean foothold = false;

    /**
     * Breached once, and shut out since.
     *
     * <p>⚠ Nothing sets this true yet — no rule patches a host. It is persisted and rendered so the
     * state has one meaning the day a patch mechanic lands; see {@code docs/design/15} for the
     * proposal. A save written today will always read false, which is correct rather than missing.
     */
    public boolean patched = false;

    /**
     * Whether a deep sweep has been run from this bridge, standing on it.
     *
     * <h2>What it buys: sight across, and nothing else</h2>
     *
     * A deep sweep taken with this bridge as the vantage is the reconnaissance step across a
     * crossing. It publishes the machine at the far end — that one bridge, never anything behind it
     * — and a <em>rough</em> count of what is over there, with the accuracy of that count stated
     * beside it ({@link NodeState#peerEstimate}). It is one of the two things that put the far
     * server on the map's tab strip.
     *
     * <p>⚠ It grants no access whatsoever. Everything on the far side stays untouchable until
     * {@link #netMan} is true on a bridge into it — see {@code NetRules.crossable}. Sight and reach
     * are deliberately separate purchases here, which is the same split {@code design/02} §1.1 draws
     * between sensitivity (ethecoin may buy it) and reach (it may not).
     *
     * <p>Meaningful on a bridge and nowhere else; every other host leaves it false forever.
     */
    public boolean surveyed = false;

    /**
     * Whether a NET_MAN has been uploaded to this bridge — i.e. whether the crossing is open.
     *
     * <h2>⚠ It is what makes the far server ACTIONABLE, not merely visible</h2>
     *
     * A breached bridge is a door you are standing in. Until a NET_MAN is running on it, the network
     * on the other side answers nothing: no sweep from it, no port scan, no breach, no shell. See
     * {@code NetRules.crossable}, which is the one place that decides, and which walks these flags
     * from the home server outward so a server three crossings out needs all three open.
     *
     * <p>⚠ <b>One-way and permanent.</b> The NET_MAN item is consumed by the upload and the bridge
     * stays open forever afterwards, so the total cost of travelling the world is bounded by the
     * number of crossings rather than by how often the player uses them. A flag that expired would
     * make the map a thing the player rents.
     *
     * <p>⚠ Set only by {@code NetRules} when the upload task settles — never at the moment the
     * upload starts. An upload that granted the crossing up front would make its duration, and the
     * noise it makes for that duration, entirely optional.
     */
    public boolean netMan = false;
}
