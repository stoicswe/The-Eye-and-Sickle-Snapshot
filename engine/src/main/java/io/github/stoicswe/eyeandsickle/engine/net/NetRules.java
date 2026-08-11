package io.github.stoicswe.eyeandsickle.engine.net;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.breach.Rng;
import io.github.stoicswe.eyeandsickle.engine.rules.ComputeRules;
import io.github.stoicswe.eyeandsickle.engine.rules.EventLog;
import io.github.stoicswe.eyeandsickle.engine.rules.LedgerRules;
import io.github.stoicswe.eyeandsickle.engine.state.AllocationState;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.HostState;
import io.github.stoicswe.eyeandsickle.engine.state.ItemState;
import io.github.stoicswe.eyeandsickle.engine.state.NodeReportState;
import io.github.stoicswe.eyeandsickle.engine.state.NodeState;
import io.github.stoicswe.eyeandsickle.engine.state.ResolutionState;
import io.github.stoicswe.eyeandsickle.engine.state.ServerState;
import io.github.stoicswe.eyeandsickle.engine.state.TaskState;
import io.github.stoicswe.eyeandsickle.engine.state.TopologyState;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import io.github.stoicswe.eyeandsickle.protocol.game.DifficultyTier;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import io.github.stoicswe.eyeandsickle.protocol.game.NetDocument;
import io.github.stoicswe.eyeandsickle.protocol.game.NetLink;
import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import io.github.stoicswe.eyeandsickle.protocol.game.ServerRef;
import io.github.stoicswe.eyeandsickle.protocol.game.Sighting;
import io.github.stoicswe.eyeandsickle.protocol.game.SignalStrength;
import io.github.stoicswe.eyeandsickle.protocol.game.SweepReport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Discovery, traversal and settlement over the generated world.
 *
 * <h2>The five sentences this class is written against</h2>
 *
 * <ol>
 *   <li><b>Schematics buy reach, ethecoin buys sensitivity.</b> {@link #hopCeiling} is 1, or 2 with
 *       the Topology Mapper schematic, and takes no sweep tier. There is no code path from ethecoin
 *       to reach — Invariant I2 is structural here, not a rule somebody remembers.
 *   <li><b>The ceiling is measured from the player's <em>vantage</em>, not from their rig.</b>
 *       Traversal is repositioning: breach a host, take a foothold, {@link #connect} to it, sweep
 *       again from there. That is what makes a one-hop ceiling survivable across a seven-server
 *       world, and it is why position is earned rather than bought.
 *   <li><b>Detection is never drawn at sweep time.</b> A sweep compares a threshold against
 *       {@link HostState#detectRoll} — rolled once at world generation — scaled by a <b>hash of the
 *       (machine, vantage) pair</b>. Both halves predate the question. Nothing here draws for
 *       detection, ever. ⚠ The pair half arrived on 2026-08-08; before it the roll was the machine's
 *       alone, so a contact missed from one position was missed from every position at that tier.
 *       See {@code audibility}.
 *   <li><b>Undiscovered hosts do not exist in {@code knownNodes}, and the map draws nothing where
 *       they are.</b> No placeholder, no count, no "three contacts nearby". The single aggregate that
 *       may be published is {@link SweepReport#inRange} — how many machines were inside the ceiling,
 *       which describes the player's own instrument and carries no address, type, tier or value.
 *   <li>Amber is for live, earning data; a network node is not earning. That one is the client's, and
 *       it is repeated here because this class is what feeds it.
 * </ol>
 *
 * <h2>Why repeated sweeps are not free rerolls</h2>
 *
 * Same tier plus same vantage yields a bit-identical candidate set, every time, forever — because
 * every input predates the sweep: the machine's roll by the whole game, and the vantage term by being
 * a hash rather than a draw. Quitting without saving changes nothing. Only two things move the
 * outcome and both cost: a <b>higher sweep tier</b> (ethecoin, plus its own compute, duration and
 * noise) or a <b>different vantage</b> (a breach, a foothold and a {@code connect}). When a sweep
 * finds nothing new it says so in those words, because a mechanic that punishes without explaining is
 * indistinguishable from a bug.
 *
 * <p>⚠ <b>"Different", not merely "closer", since 2026-08-08.</b> A new position used to help only by
 * bringing new machines inside the ceiling; it now also re-decides what is audible among the machines
 * already in range, so two vantages at the same distance from a host are genuinely two chances at it.
 * That is what lets a graph keep growing as a player works outward — and it costs a breach and a
 * foothold every time, so it is still position earned rather than a button pressed twice.
 *
 * <h2>The whole sweep is rolled at begin and persisted</h2>
 *
 * {@link #beginSweep} decides everything — which machines were in range, which were detected, and
 * whether the sweep provoked a counter-hack — and freezes it into {@link TaskState#outcome}. The same
 * rule {@code docs/design/16-breach-implementation.md} §2 gives breach boards and
 * {@code ScanRules.roll} gives scan findings: a result computed at completion would quietly depend on
 * whether the player was watching, and under a persisted RNG it would also be a reroll a player could
 * force by quitting.
 *
 * <h2>A sweep is cheap and loud, and those are two different numbers</h2>
 *
 * Cycles are reserved through {@link ComputeConsumer#CONTROL_CHANNEL} — work that reaches other
 * machines — but the <em>loudness</em> is stated separately on the task
 * ({@link TaskState#noiseCycles}, from {@link SweepTier#noiseCycles()}) rather than inferred from the
 * cycle count. The two were once the same value and the identity was wrong on screen: noise renders
 * as outward cycles over rig capacity, so a two-cycle sweep moved the meter by two percent and got
 * quieter as the player's rig grew. A sweep now costs almost nothing and shouts, which is what
 * {@code docs/design/08-stealth-and-noise.md} §1 describes and what makes it a decision.
 *
 * <p>⚠ It shouts <b>only while it runs</b>. {@code NoiseRules} counts a task while {@code now} is
 * inside its window and the allocation goes into thermal recovery at settlement, so a finished sweep
 * contributes exactly nothing — no trailing figure, no decay curve to tune. What a loud act leaves
 * behind is heat, which is a persisted field charged by different rules.
 *
 * <h2>A sweep never costs ethecoin</h2>
 *
 * ⚠ There is no {@code LedgerRules} call on this path and there must never be one. The tiers are
 * bought once with ethecoin, which is breadth and therefore Invariant <b>I2</b>-legal; <em>running</em>
 * one spends the player's own cycles and their own exposure and nothing else. A per-run charge would
 * make discovery — the thing every other network mechanic is downstream of — meterable in currency,
 * and a player short of ethecoin would be unable to find the machines that are how you earn it.
 */
public final class NetRules {

    private NetRules() {}

    /** The schematic — and the only thing in the game — that moves the hop ceiling. */
    public static final String TOPOLOGY_MAPPER = "topology-mapper";

    /** The ledger type a host's one-time payout is credited under. */
    private static final String LOOT_LEDGER_TYPE = "NET_LOOT";

    // ================================================================== reach

    /**
     * How far a sweep can see from the current vantage: 1 hop, or 2 with the Topology Mapper.
     *
     * <p>⚠ <b>Nothing else may raise this, at any price.</b> {@code docs/design/07-recon-tools.md} §2
     * calls the Topology Mapper "a <b>ceiling</b> on information (1 hop → 2 hops), hence
     * schematic-gated not purchasable (Invariant I2)". No sweep tier is a parameter of this method
     * and no item id but one is consulted, so the invariant holds by the shape of the signature
     * rather than by anyone remembering it. {@code NetRulesTest} enumerates every purchasable
     * offering and asserts none of them moves the answer.
     */
    public static int hopCeiling(GameSave save) {
        return save != null && save.schematics != null && save.schematics.contains(TOPOLOGY_MAPPER) ? 2 : 1;
    }

    /**
     * Hop distance from {@code from} to every reachable host, over the <em>full</em> link graph.
     *
     * <p>⚠ Not over the discovered subgraph. Undiscovered machines still conduct: a host the player
     * has never seen is still the reason a further one is two hops away rather than three. A BFS over
     * what the player knows would make the ceiling widen as they learned things, which is reach for
     * free and therefore an I2 violation wearing a graph algorithm's clothes.
     *
     * <p>Hosts outside the graph are absent from the result rather than present at
     * {@code Integer.MAX_VALUE}, so a caller that forgets to check gets a {@code null} rather than a
     * silently in-range machine.
     */
    public static Map<String, Integer> hopsFrom(GameSave save, String from) {
        TopologyState topology = topology(save);
        if (topology == null) {
            return Map.of();
        }
        return TopologyGenerator.bfs(index(topology), from);
    }

    // ================================================================== the read model

    /**
     * The player's whole visible network: their own rig, every node they have discovered, and the
     * links between them.
     *
     * <p>Built from {@code knownNodes} intersected with the topology, which is what keeps the
     * discovery rule honest in one place — an undiscovered host is not in {@code knownNodes}, so it
     * is not in the map, so the graph draws nothing where it is and {@code ls /net/} does not list it.
     * Never null; an absent topology yields {@link NetMap#empty()}.
     */
    public static NetMap view(GameSave save) {
        TopologyState topology = topology(save);
        if (topology == null) {
            return NetMap.empty();
        }
        Map<String, HostState> hosts = index(topology);
        Map<String, ServerState> servers = servers(topology);
        Map<String, NodeState> known = knownNodes(save);
        String vantage = vantageAddress(save);
        Map<String, Integer> hops = TopologyGenerator.bfs(hosts, vantage);
        // ⚠ A SECOND BFS, from the RIG, and it is not the same walk. The map draws its columns on
        // this one so that layer 0 is always the player's own machine — repositioning must not
        // re-root the picture. See Sighting#hopsFromRig.
        Map<String, Integer> rigHops = TopologyGenerator.bfs(hosts, topology.playerAddress);

        // The rig is always visible; everything else has to have been detected. Ordered so the map is
        // stable across repaints: the vantage first, then by address.
        Set<String> visible = new LinkedHashSet<>();
        visible.add(topology.playerAddress);
        for (NodeState node : known.values()) {
            if (hosts.containsKey(node.address)) {
                visible.add(node.address);
            }
        }

        List<Sighting> sightings = new ArrayList<>();
        for (String address : visible) {
            sightings.add(sighting(
                    save,
                    hosts.get(address),
                    known.get(address),
                    servers,
                    hops,
                    rigHops,
                    vantage,
                    topology,
                    hosts,
                    visible));
        }

        List<NetLink> links = new ArrayList<>();
        for (String address : visible) {
            HostState host = hosts.get(address);
            for (String neighbour : host.links) {
                // Emitted once, from the lower address, so the client never has to de-duplicate an
                // undirected edge it was handed twice.
                if (visible.contains(neighbour) && address.compareTo(neighbour) < 0) {
                    HostState other = hosts.get(neighbour);
                    links.add(new NetLink(address, neighbour, !host.serverId.equals(other.serverId)));
                }
            }
        }

        List<ServerRef> knownServers = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Sighting sighting : sightings) {
            if (seen.add(sighting.serverId())) {
                knownServers.add(serverRef(servers.get(sighting.serverId())));
            }
        }
        // ⚠ A SERVER IS ALSO KNOWN ONCE A BRIDGE TO IT HAS BEEN BREACHED — design/18 §2.8.
        //
        // Until 2026-08-09 this list came from sightings alone, so a foreign server reached the tab
        // strip only when a machine ON it had been swept — which needs a foothold on the bridge AND a
        // connect AND a sweep from over there. The door opening and the door being on the strip were
        // three actions apart, and `ServerTabs`' own note about a tab that "carries the name and says
        // it is unexplored" described a state that could not occur.
        //
        // ⚠ BREACHED, not merely identified. Identifying a bridge tells you a server is there and its
        // name — which the bridge already says, on the bridge, where it can be acted on. Taking the
        // bridge is the moment the far side becomes somewhere you can go, and a tab is a place to go.
        // Keying on identification would put a tab on the strip for a door the player cannot open.
        //
        // ⚠ `visible` is the discovery gate, so this cannot publish a server behind a bridge nobody
        // has found — "discovered AND breached", both halves, and the first one is free here.
        // ⚠ AND SURVEYED OR OPEN — narrowed 2026-08-09. A breached bridge alone no longer lists the
        // far server, because breaching a bridge now tells you nothing about what is behind it: the
        // sweep cannot reach across, so a tab put up by the breach alone would be a named, empty
        // server the player had learned nothing about. What earns the tab is one of the two acts that
        // genuinely look across — a DEEP survey from the bridge, or a NET_MAN opening it.
        for (String address : visible) {
            HostState host = hosts.get(address);
            if (host == null || !host.foothold || !HostKind.BRIDGE.name().equals(host.kind)) {
                continue;
            }
            if (!host.surveyed && !host.netMan) {
                continue;
            }
            HostState peer = hosts.get(host.bridgePeer);
            if (peer != null && seen.add(peer.serverId)) {
                knownServers.add(serverRef(servers.get(peer.serverId)));
            }
        }
        knownServers.sort(Comparator.comparingInt(ServerRef::depthFromHome).thenComparing(ServerRef::serverId));

        HostState vantageHost = hosts.get(vantage);
        ServerRef current = serverRef(servers.get(vantageHost == null ? topology.homeServerId : vantageHost.serverId));
        return new NetMap(current, vantage, hopCeiling(save), knownServers, sightings, links);
    }

    /**
     * One machine as the player knows it.
     *
     * <p>⚠ <b>{@code label} is one of them, and it was the truth until 2026-08-07.</b> A sweep used to
     * copy {@link HostState#label} straight into the player's knowledge, so every machine arrived
     * already named. Its name is now a finding — {@code PortScanTarget.IDENTITY}, the cheapest rung —
     * and until that rung has been paid for, or the machine has been breached, this is empty and the
     * interface shows the address alone. Read from the recon file rather than from a second copy on
     * {@link NodeState}, because one stored answer cannot disagree with itself.
     *
     * <p>Five fields are deliberately the player's knowledge and not the truth beside them:
     * {@code kind} stays {@code UNKNOWN} until a DEEP sweep has picked the machine up or the player
     * holds a foothold on it (see {@link #identify}; a BASE or WIDE sweep still sells existence and
     * adjacency and nothing else), {@code honeypotSuspected} comes off {@link NodeState} and never
     * off {@link HostState#honeypot}, {@code documentAvailable} is only true once the player holds a
     * foothold — knowing a fragment is there before you are inside would be a recon product nobody
     * sold — and the peer server name is published only by a crossing the player has surveyed across
     * or opened.
     *
     * <p>⚠ Those last two moved on 2026-08-09 and they moved in <em>opposite</em> directions, which
     * is the point. Typing a machine got cheaper because nothing in the game could do it at all;
     * naming the network behind a bridge got dearer, because a foothold now types the bridge and the
     * old rule would have handed the far side over with it.
     */
    private static Sighting sighting(
            GameSave save,
            HostState host,
            NodeState node,
            Map<String, ServerState> servers,
            Map<String, Integer> hops,
            Map<String, Integer> rigHops,
            String vantage,
            TopologyState topology,
            Map<String, HostState> hosts,
            Set<String> visible) {

        boolean self = host.address.equals(topology.playerAddress);
        boolean identified = self || host.identified || (node != null && !"UNKNOWN".equals(node.kind));
        HostKind kind = identified ? HostArchetypes.kindOrUnknown(host.kind) : HostKind.UNKNOWN;

        // ⚠ The player's own rig has no breach tier, and null is the honest reading: DifficultyTier
        // is a 1–5 scale for "how hard is this to breach", and the answer for your own machine is not
        // a number on that scale. The list renders it as "--".
        DifficultyTier tier = self
                ? null
                : DifficultyTier.of(Math.max(DifficultyTier.LOWEST, Math.min(DifficultyTier.HIGHEST, host.tier)));

        boolean hostsMiner = node != null && node.deployedMiners != null && !node.deployedMiners.isEmpty();
        SignalStrength signal = HostArchetypes.signalOf(host, hostsMiner);

        // The graph is connected by construction, so the fallback is unreachable in a save this
        // engine wrote. It exists because a hand-edited one is not.
        int distance = hops.getOrDefault(host.address, 0);
        int fromRig = rigHops.getOrDefault(host.address, 0);

        // ⚠ THE FAR SERVER IS PUBLISHED BY THE TWO ACTS THAT LOOK ACROSS, AND BY NOTHING ELSE.
        //
        // This used to be gated on `kind == BRIDGE` alone — i.e. on merely knowing the machine is a
        // door. That was already the wrong rule by `design/18` §2.8, which reversed "breaching is
        // enough to list the far server" within a day of shipping it, and it became load-bearing the
        // moment a foothold started typing a machine (see `identify`): without this, breaking into a
        // bridge would name the network behind it for free.
        //
        // ⚠ It is the SAME condition that puts a tab on the strip — `surveyed || netMan`, checked a
        // few dozen lines below in `view`. One rule, one answer: the name of the far side and a place
        // to go to it arrive together, from a DEEP survey across the crossing or from a NET_MAN
        // running on it. Two conditions here would be two screens disagreeing about whether the
        // player has been told where this door goes.
        //
        // ⚠ The BRIDGE test is kept as well even though `surveyed`/`netMan` are only ever set on a
        // bridge. It costs nothing and it means a hand-edited save cannot make an ordinary machine
        // advertise a server.
        String peerServerName = "";
        if (kind == HostKind.BRIDGE && !host.bridgePeer.isEmpty() && (host.surveyed || host.netMan)) {
            peerServerName = peerServerName(host, servers, topology);
        }

        // Your own rig is never a finding about somebody else's machine — it is called what it is
        // called, from the first second, and gating it would make `localhost` something to scan for.
        // Its operator is the player, and the top strip already says who that is, so it stays empty
        // rather than printing the handle twice.
        Optional<NodeReportState> file = self ? Optional.empty() : NodeReports.find(save, host.address);
        String knownName =
                self ? host.label : file.map(report -> report.hostName).orElse("");
        String knownOperator = file.map(report -> report.operatorName).orElse("");

        return new Sighting(
                host.address,
                knownName,
                host.serverId,
                kind,
                tier,
                signal,
                fromRig,
                distance,
                // ⚠ PUBLISHED, at last. `self` has been computed at the top of this method since it
                // was written and never left it, so every view that needed "is this mine" had only
                // `vantage` to reach for — correct exactly until the vantage moved. See Sighting#self.
                self,
                host.address.equals(vantage),
                host.foothold,
                // ⚠ A patched host is one that WAS breached and is not any more, so it can never be
                // true while the foothold still stands. Reporting both would let the map draw a
                // node as simultaneously open and shut.
                host.patched && !host.foothold,
                host.looted,
                node != null && node.honeypotSuspected,
                hostsMiner,
                host.foothold && !host.documentId.isEmpty() && !host.documentTaken,
                peerServerName,
                NodeReports.any(save, host.address),
                knownOperator,
                host.netMan,
                host.surveyed,
                // ⚠ Read off the player's KNOWLEDGE, not off the world. The estimate is a thing the
                // player was told once; deriving it here from the true count would make it exact, and
                // exact is the one thing it must never be.
                node == null ? -1 : node.peerEstimate,
                node == null ? 0 : node.peerAccuracyPercent,
                linkEstimate(host, hosts, visible));
    }

    /**
     * Roughly how many machines are attached to {@code host}, or {@code -1} when there is nothing to
     * say — {@code Sighting#linkEstimate}.
     *
     * <h2>⚠ SUPPRESSED THE MOMENT THE PLAYER HAS FOUND THEM ALL, and that is the feature</h2>
     *
     * The tag on the map is not the information; its <b>disappearance</b> is. A machine still showing
     * one says another sweep from here might pay for itself, and a machine that has stopped showing
     * one says the lines on screen are the whole story. So the suppression is computed here, from the
     * truth, rather than left to a renderer comparing an estimate against a drawn edge count — a band
     * can sit above or below the real number, so a renderer doing that arithmetic would keep the tag
     * up forever on some machines and drop it early on others.
     *
     * <h2>⚠ CROSS-SERVER LINKS ARE EXCLUDED FROM BOTH SIDES</h2>
     *
     * A bridge's far side is {@code peerEstimate}'s question and it is bought separately and dearly —
     * a DEEP survey from a foothold on the bridge ({@code design/18} §2.7a). Counting the crossing
     * here would answer it for free and give two figures for one question, which is the failure
     * {@code NodeState#peerEstimate} already records for {@code PortScanTarget.PEERS}. So this is
     * strictly a statement about the local neighbourhood, and a bridge whose only unfound neighbour
     * is across the water correctly reports nothing.
     *
     * <h2>⚠ FLOORED ABOVE WHAT IS ALREADY ON SCREEN</h2>
     *
     * The band is symmetric, so on a machine with five links and four found it can land on three —
     * and "about 3" beside four drawn lines reads as a broken instrument, not as an estimate. Since
     * this is only ever published when a real link is still missing, {@code found + 1} is a
     * correction <em>toward</em> the truth. It never reveals more than the fact that there is at
     * least one more, which the tag's presence already says.
     */
    private static int linkEstimate(HostState host, Map<String, HostState> hosts, Set<String> visible) {
        if (host == null || host.links == null) {
            return -1;
        }
        int total = 0;
        int found = 0;
        for (String neighbour : host.links) {
            HostState other = hosts.get(neighbour);
            if (other == null || !other.serverId.equals(host.serverId)) {
                continue;
            }
            total++;
            if (visible != null && visible.contains(neighbour)) {
                found++;
            }
        }
        if (total == 0 || found >= total) {
            return -1;
        }
        return Math.max(
                found + 1, Balance.netLinkEstimate(total, AddressHash.unitOf(host.address, "link-estimate")));
    }

    /**
     * The name of the network on the other side of a bridge — and nothing else about it.
     *
     * <p>A bridge's entire published function is to name what it connects to, so this is legitimate
     * where a peer address, a host count, or anything about what is over there would not be. It is
     * also exactly the shape a federated bridge would take: {@code docs/design/13} §4 lets servers
     * share the minimum needed to recognise identities, never enough for one to grief another's
     * internal state, so a cross-server bridge exposes a handshake and not a topology.
     */
    private static String peerServerName(HostState host, Map<String, ServerState> servers, TopologyState topology) {
        for (HostState candidate : topology.hosts) {
            if (candidate.address.equals(host.bridgePeer)) {
                ServerState server = servers.get(candidate.serverId);
                return server == null ? "" : server.name;
            }
        }
        return "";
    }

    // ================================================================== the sweep

    /**
     * Commissions a sweep: reserves its compute, rolls its entire result, and freezes that into the
     * returned task.
     *
     * <p>Refuses — empty, with nothing spent — when the tool is not owned or the rig cannot afford
     * the cycles. Refusing rather than throwing because the shell prints a refusal, and a rules engine
     * that threw on an unaffordable action would be deciding how the client reports it.
     *
     * <p>Exactly one draw happens here: the counter-hack roll. It is made now, stored, and applied at
     * settlement, so a reload mid-sweep replays nothing. Detection makes no draw at all.
     *
     * @param now the session clock. ⚠ Never {@code Instant.now()} — a rule that reads the wall clock
     *     behind its caller's back reports every task as complete the moment it starts under a test
     *     clock, and agrees with itself only in production
     */
    public static Optional<TaskState> beginSweep(GameSave save, SweepTier tier, Instant now) {
        TopologyState topology = topology(save);
        if (topology == null || tier == null) {
            return Optional.empty();
        }
        if (!owns(save, tier)) {
            return Optional.empty();
        }
        AllocationState allocation =
                ComputeRules.reserve(save.rig, ComputeConsumer.CONTROL_CHANNEL, tier.label(), tier.cycles());
        if (allocation == null) {
            return Optional.empty();
        }
        // Held, not spent: GameEngine.settleTasks hands it to ComputeRules.beginRecovery when the sweep
        // ends, the same shape a scan takes since UI-6. Stamped so the rig monitor can draw the hold.
        allocation.startedAt = now;

        String vantage = vantageAddress(save);
        Map<String, HostState> hosts = index(topology);
        Map<String, ServerState> servers = servers(topology);
        Map<String, Integer> hops = TopologyGenerator.bfs(hosts, vantage);
        int ceiling = hopCeiling(save);

        HostState standingOn = hosts.get(vantage);
        String ownServer = standingOn == null ? topology.homeServerId : standingOn.serverId;

        // ⚠ THE SURVEY: a DEEP sweep taken while standing ON a bridge, which is a different act from
        // an ordinary sweep and is the only one that may look across. Tier-gated at DEEP rather than
        // at the bridge tier because sight across a crossing is the dearest instrument's product —
        // WIDE buys knowing the door is there, DEEP buys knowing what is behind it.
        boolean survey = standingOn != null
                && HostKind.BRIDGE.name().equals(standingOn.kind)
                && tier.tier() >= SweepTier.DEEP.tier();

        List<HostState> candidates = new ArrayList<>();
        int deepestInRange = 0;
        for (HostState host : topology.hosts) {
            Integer distance = hops.get(host.address);
            if (distance == null || distance < 1 || distance > ceiling) {
                continue;
            }
            if (host.address.equals(topology.playerAddress)) {
                continue;
            }
            // ⚠ A SWEEP NEVER REACHES ONTO ANOTHER SERVER, and this is a real narrowing (2026-08-09).
            //
            // `hops` is a BFS over the whole topology and cross-server links are ordinary edges in
            // it, so standing on a bridge with a two-hop ceiling used to put the far bridge AND its
            // neighbours in range — a sweep quietly delivering a foreign server's machines before
            // anything had opened the crossing. A server is now something you get into, not
            // something you overhear.
            //
            // ⚠ THE ONE EXCEPTION IS THE FAR BRIDGE, AND ONLY FROM THE BRIDGE FACING IT. Standing on
            // a crossing and looking across is the reconnaissance step; what it may publish is the
            // machine at the other end and nothing behind it. `crossesFrom` is that test and it is
            // deliberately an identity check against `bridgePeer` rather than a distance one — at
            // ceiling 2 a distance test would also admit whatever is next to the far bridge, which is
            // exactly the leak this rule exists to close.
            if (!host.serverId.equals(ownServer) && !(survey && crossesFrom(standingOn, host))) {
                continue;
            }
            candidates.add(host);
            ServerState server = servers.get(host.serverId);
            deepestInRange = Math.max(deepestInRange, server == null ? 0 : server.depthFromHome);
        }

        // ⚠ COLLECTED AS HOSTS, then ranked and capped — see the yield note below. Building the
        // address list directly would throw away the detectRoll the ranking needs.
        List<HostState> detected = new ArrayList<>();
        for (HostState host : candidates) {
            if (host.discovered) {
                continue;
            }
            // ⚠ A HARD GATE ON WHICH KINDS THIS TIER CAN SEE, applied before the roll and never as a
            // probability. Bridges need a WIDE sweep or better — see HostArchetypes.detectableBySweep.
            //
            // ⚠ It gates candidacy, NOT reach: the host is still inside the hop ceiling either way,
            // and hopCeiling still takes no tier. A base sweep is standing in exactly the same place
            // as a wide one and simply does not hear this kind of machine, which is sensitivity —
            // what ethecoin is allowed to buy — rather than distance, which it is not (I2).
            if (!HostArchetypes.detectableBySweep(host.kind, tier.tier())) {
                continue;
            }
            boolean hostsMiner = false;
            double threshold = Balance.netSweepBase(
                            tier.tier(),
                            HostArchetypes.signalOf(host, hostsMiner).name())
                    * hopFactor(hops.get(host.address));
            if (audibility(host, vantage) < threshold) {
                detected.add(host);
            }
        }

        // ⚠ THE YIELD CAP. A sweep hands over at most `Balance.sweepYield` machines — 5–7 at home,
        // falling to 1–3 past a couple of bridges — so a first look at a new server is a foothold on
        // it rather than the whole thing at once.
        //
        // ⚠ RANKED BY AUDIBILITY FROM THIS VANTAGE, LOWEST FIRST, and that is what keeps every
        // existing guarantee. Both of its inputs predate the sweep — the machine's roll by the whole
        // game, the vantage term by being a hash — so the ranking is fixed too: the same vantage and
        // tier cut at exactly the same place, every time, forever. Nothing here draws, and
        // `SweepDeterminismTest.resweepingIsNotAReroll` still holds — the cap is absolute per
        // (vantage, tier), not per attempt, so sweeping the same spot again finds nothing new.
        //
        // ⚠ IT MUST BE THE SAME FUNCTION THE THRESHOLD USED, not detectRoll. Ranking on the machine's
        // own roll while detecting on its audibility would cut the list in an order unrelated to the
        // one that chose it — so a machine could be detected, sorted below the cap by a number that
        // played no part in detecting it, and dropped. Sorting on the same value is what preserves
        // "a player upgrading their sweep sees what they saw before plus more, never a different set".
        //
        // ⚠ Lowest first also means the cap keeps the machines a WEAKER instrument would also have
        // heard, rather than an arbitrary slice.
        int yield = Balance.sweepYield(deepestInRange, tier.tier(), AddressHash.unitOf(vantage, "sweep-yield"));
        detected.sort(java.util.Comparator.comparingDouble((HostState host) -> audibility(host, vantage))
                .thenComparing(host -> host.address));
        List<String> found = new ArrayList<>();
        for (HostState host : detected) {
            if (found.size() >= yield) {
                break;
            }
            found.add(host.address);
        }

        // ⚠ A WELL-MAPPED SERVER GIVES UP ITS EXITS — design/18 §2.7a, added 2026-08-09.
        //
        // Measured over 400 worlds: a first WIDE or DEEP sweep from home found home's own bridge in
        // 75% of them, and in the other quarter the exit was simply inaudible from where the player
        // stood. Re-sweeping is deliberately not a reroll, so those players had no way to make
        // progress except to wander until the geometry happened to help. Past
        // Balance.NET_BRIDGE_REVEAL_SHARE of a server's machines, its bridges stop hiding.
        //
        // ⚠ IT DELIBERATELY OVERRIDES THE THRESHOLD, THE YIELD CAP AND THE HOP CEILING — hence being
        // applied HERE, after the cut, rather than as another candidate. Overriding the threshold
        // alone would leave the bridge ranked and then dropped by the cap, i.e. the rule firing and
        // appearing not to; and leaving the hop ceiling in place would restore the same "be lucky
        // about where it is" failure one step along, on the servers whose exit is deepest.
        //
        // ⚠ IT DOES NOT OVERRIDE THE TIER GATE. A base sweep still never sees a bridge, so the free
        // instrument is exactly as it was and this is something the first upgrade is for.
        //
        // ⚠ IT NEVER REACHES ONTO ANOTHER SERVER: `ownServer` only. The rule is about the place you
        // have mapped, and a bridge on a server you have never been to is not that.
        if (tier.tier() >= Balance.NET_SWEEP_BRIDGE_MIN_TIER
                && serverCompletion(save, ownServer) >= Balance.NET_BRIDGE_REVEAL_SHARE) {
            for (HostState host : topology.hosts) {
                if (host.discovered
                        || !HostKind.BRIDGE.name().equals(host.kind)
                        || !ownServer.equals(host.serverId)
                        || found.contains(host.address)) {
                    continue;
                }
                found.add(host.address);
                // ⚠ AND COUNTED AS CONSIDERED, or the report is arithmetically impossible. `inRange`
                // is the candidate count, and this rule deliberately reaches past the hop ceiling —
                // so a bridge revealed this way was never a candidate, and the sweep would report
                // "found 2 of 1". SweepReport's compact constructor THROWS on that, and rightly: the
                // player reads those two numbers as a fraction and decides whether to buy a better
                // instrument from it, so found > inRange tells them theirs is better than perfect.
                //
                // ⚠ Caught by that constructor rather than by review, on a walking fixture. It is the
                // one arithmetic error in this file that could not have failed quietly.
                if (!candidates.contains(host)) {
                    candidates.add(host);
                }
            }
        }

        // The one draw. Rolled against the CANDIDATE set rather than the detected set: the machines
        // notice you probing them whether or not you learn anything.
        Rng rng = Rng.of(save);
        double roll = rng.nextDouble();
        rng.commit(save);
        // ⚠ The CHANCE is scaled by the developer facility, never the draw — the line above runs
        // whatever the override is, so a replay from a stored seed stays a replay. Identity at 100%.
        double counterHackChance = io.github.stoicswe.eyeandsickle.engine.rules.WorldRules.intrusionChance(
                save, Balance.netCounterHackChance(deepestInRange));
        int counterHackDepth = roll < counterHackChance ? deepestInRange : -1;

        // ⚠ Longer on an infested rig, baked in at commission — see ComputeRules.slowedSeconds. A
        // sweep that takes 26 seconds instead of 20 is the cheapest hint in the game that something
        // is eating the machine, and it costs nothing to notice.
        long seconds = ComputeRules.slowedSeconds(save.rig, tier.seconds());
        TaskState task = new TaskState(
                "sweep", tier.label(), allocation.allocationId, tier.cycles(), now, now.plusSeconds(seconds));
        // The sweep's loudness, declared on the task rather than derived from its cycles. It is
        // present-tense by construction: NoiseRules counts a task only while it is still running, so
        // the meter drops back the instant this one's countdown reaches zero.
        task.noiseCycles = tier.noiseCycles();
        task.outcome = encode(tier.itemId(), vantage, candidates.size(), counterHackDepth, found, survey);
        save.tasks.add(task);

        EventLog.notice(
                save,
                "net",
                tier.label() + ": " + tier.cycles() + " cycles held, ~" + seconds + "s, and loud the whole time.",
                now);
        return Optional.of(task);
    }

    /**
     * Applies a finished sweep: materialises the nodes it found, plants any counter-hack, and logs.
     *
     * <p>Draws nothing. Everything was decided at {@link #beginSweep}, so this method is a pure
     * application of a frozen result — which is why a sweep that finished while the game was closed
     * reports exactly what it would have reported in session.
     */
    public static SweepReport settleSweep(GameSave save, TaskState task, Instant now) {
        Encoded encoded = decode(task);
        SweepReport report = report(task);
        TopologyState topology = topology(save);
        if (topology == null) {
            return report;
        }
        Map<String, HostState> hosts = index(topology);

        // ⚠ A DEEP SWEEP TYPES WHAT IT PICKS UP. See `identify` for why nothing typed anything before
        // this. Read back from the tool id the task froze at commission rather than from the player's
        // current loadout — the sweep's whole outcome is decided at `beginSweep`, and asking "which
        // sweep do they own now?" five minutes later would answer about whatever they bought while it
        // ran. No new field and no encoding bump: `toolId` has been in `v1` since this was written.
        boolean types = SweepTier.byItemId(encoded.toolId())
                .filter(tier -> tier.tier() >= SweepTier.DEEP.tier())
                .isPresent();

        for (String address : encoded.found()) {
            HostState host = hosts.get(address);
            if (host == null || host.discovered) {
                continue;
            }
            host.discovered = true;
            save.knownNodes.add(nodeFor(host, now));
            // ⚠ Inside the discovery guard, and here that is correct rather than the `revealAll`
            // trap. `found` is built from machines that were NOT yet discovered (see `beginSweep`),
            // so a machine already on the map can never appear in a later sweep's list at all —
            // there is no re-detection case for this to miss. The honest consequence, and it is a
            // real one: a machine found by a BASE or WIDE sweep is never typed by a DEEP sweep
            // afterwards, because no later sweep will ever hand it back. Breaching it is the other
            // route, and re-sweeping is deliberately not a reroll.
            if (types) {
                identify(save, host);
            }
        }

        // ⚠ THE SURVEY LANDS AT SETTLEMENT, not at commission, because it is a product of the sweep
        // rather than a precondition of it — and because the five minutes it takes are exactly when a
        // player would otherwise be tempted to quit and reload for a better number. There is no
        // better number: the estimate is a hash of the bridge.
        if (encoded.survey()) {
            surveyAcross(save, hosts.get(encoded.vantage()), hosts, now);
        }

        if (encoded.counterHackDepth() >= 0) {
            counterHack(save, encoded.counterHackDepth(), now);
        }

        if (report.found() > 0) {
            EventLog.notice(
                    save, "net", task.label + ": " + report.inRange() + " in range, " + report.found() + " new.", now);
        } else {
            EventLog.info(
                    save, "net", task.label + ": " + report.inRange() + " in range, 0 new. " + report.note(), now);
        }
        return report;
    }

    // ================================================================== crossings

    /** The task kind a NET_MAN upload runs as. */
    public static final String NETMAN_KIND = "netman";

    /** The catalogue id of the consumable that opens a crossing. */
    public static final String NETMAN_ITEM = "net-man";

    /**
     * What share of a server's machines the player has found — the hidden completion metric.
     *
     * <h2>⚠ HIDDEN, and it must stay that way</h2>
     *
     * Nothing publishes this. It is not on the map, not in the recon file, not in a readout, and it
     * is deliberately not on {@code NetMap} — because a number saying "you have found 68% of this
     * server" is a <b>count of undiscovered machines</b> wearing a percentage, and this package's
     * standing rule is that an undiscovered host does not exist: no placeholder, no count, no "three
     * contacts nearby". Publishing it would hand over free of charge the one thing every sweep tier
     * in the game is sold to buy.
     *
     * <p>What the player sees instead is the <em>consequence</em>: past
     * {@link Balance#NET_BRIDGE_REVEAL_SHARE} a wide sweep starts finding the exits. That is legible
     * by playing — map more, find the door — without ever putting a denominator on screen.
     *
     * <p>⚠ Counts the rig, and every machine on the server including bridges. The rig is a machine on
     * the home server and is discovered from the first second, so excluding it would make home's
     * denominator differ from every other server's for no reason a player could see.
     *
     * @return 0.0 when the server is unknown or empty, so a missing server can never satisfy a
     *     threshold by accident
     */
    public static double serverCompletion(GameSave save, String serverId) {
        TopologyState topology = topology(save);
        if (topology == null || serverId == null || serverId.isBlank()) {
            return 0.0d;
        }
        int total = 0;
        int found = 0;
        for (HostState host : topology.hosts) {
            if (!serverId.equals(host.serverId)) {
                continue;
            }
            total++;
            if (host.discovered || host.address.equals(topology.playerAddress)) {
                found++;
            }
        }
        return total == 0 ? 0.0d : found / (double) total;
    }

    /**
     * Whether {@code host} is the machine directly across the crossing {@code standingOn} is.
     *
     * <p>⚠ An identity check against {@code bridgePeer}, never a distance one. At a two-hop ceiling
     * "one hop onto another server" also admits whatever sits beside the far bridge, which is exactly
     * the leak the same-server rule exists to close.
     */
    private static boolean crossesFrom(HostState standingOn, HostState host) {
        return standingOn != null
                && HostKind.BRIDGE.name().equals(standingOn.kind)
                && !standingOn.bridgePeer.isEmpty()
                && standingOn.bridgePeer.equals(host.address);
    }

    /**
     * Records what a deep sweep from a bridge learned about the far side.
     *
     * <p>Two things and no more: that the crossing has been surveyed — one of the two ways a server
     * reaches the tab strip — and a <b>rough count</b> of the machines over there, with the accuracy
     * of that count stored beside it. No addresses, no kinds, no tiers: the machines themselves are
     * still behind the crossing, and opening it is what {@code NET_MAN} is for.
     *
     * <p>⚠ The estimate is written onto the <b>bridge's</b> node, not onto anything belonging to the
     * far server — the player has learned a fact about this door, and there is no row in
     * {@code knownNodes} for a machine nobody has found.
     */
    private static void surveyAcross(GameSave save, HostState bridge, Map<String, HostState> hosts, Instant now) {
        if (bridge == null || !HostKind.BRIDGE.name().equals(bridge.kind) || bridge.bridgePeer.isEmpty()) {
            return;
        }
        HostState peer = hosts.get(bridge.bridgePeer);
        if (peer == null) {
            return;
        }
        bridge.surveyed = true;
        publishPeer(save, bridge, hosts, now);

        int actual = 0;
        for (HostState host : hosts.values()) {
            if (peer.serverId.equals(host.serverId)) {
                actual++;
            }
        }
        int estimate = Balance.netPeerEstimate(actual, AddressHash.unitOf(bridge.address, "peer-estimate"));

        for (NodeState node : save.knownNodes) {
            if (node.address.equals(bridge.address)) {
                node.peerEstimate = estimate;
                node.peerAccuracyPercent = Balance.NET_PEER_ESTIMATE_ACCURACY_PERCENT;
            }
        }
        EventLog.notice(
                save,
                "net",
                "survey across " + bridge.address + ": roughly " + estimate + " machines on the far side ("
                        + Balance.NET_PEER_ESTIMATE_ACCURACY_PERCENT + "% accurate). Nothing over there answers "
                        + "until a NET_MAN is running on this bridge.",
                now);
    }

    /**
     * Publishes the machine at the far end of a crossing — that one machine and nothing else.
     *
     * <h2>⚠ BOTH ROUTES ACROSS PUBLISH IT, and without that neither route leads anywhere</h2>
     *
     * A sweep cannot reach onto another server, so the far bridge is the only machine over there
     * that anything can ever hand the player — and until it is on the map they cannot stand on it,
     * which means they cannot sweep the far server, which means an opened crossing opens onto
     * nothing. Measured on the walking fixture: with the crossing open but the peer unpublished, a
     * walking player's graph stopped dead at the edge of their home server exactly as if the
     * crossing had never been opened.
     *
     * <p>⚠ <b>The peer, and nothing behind it.</b> This is the whole of what may cross: no
     * neighbours, no count, no kinds. The far server's own machines are still bought the ordinary
     * way — stand on that bridge and sweep.
     */
    private static void publishPeer(GameSave save, HostState bridge, Map<String, HostState> hosts, Instant now) {
        HostState peer = bridge == null ? null : hosts.get(bridge.bridgePeer);
        if (peer == null || peer.discovered) {
            return;
        }
        peer.discovered = true;
        save.knownNodes.add(nodeFor(peer, now));
    }

    /**
     * Opens a crossing: the NET_MAN is running and the machine at the far end is on the map.
     *
     * <h2>⚠ THE ONE DOOR, and the only PAID route to it is {@link #completeNetMan}</h2>
     *
     * Everything that opens a crossing comes through here — a finished upload, the developer
     * reveal, a fixture that needs to stand somewhere. That is deliberate: a second place that set
     * {@code netMan} would be a crossing that opened without publishing its far side, which is the
     * defect described on {@link #publishPeer} and which renders as the feature simply not working.
     *
     * <p>⚠ It is engine-internal and deliberately absent from {@code GameSession}, so nothing the
     * client can reach opens a crossing without spending the item — the same shape that keeps cheats
     * off the port.
     */
    public static void openCrossing(GameSave save, String address, Instant now) {
        TopologyState topology = topology(save);
        if (topology == null) {
            return;
        }
        Map<String, HostState> hosts = index(topology);
        HostState bridge = hosts.get(address);
        if (bridge == null || !HostKind.BRIDGE.name().equals(bridge.kind)) {
            return;
        }
        bridge.netMan = true;
        publishPeer(save, bridge, hosts, now);
    }

    /**
     * Whether the player may act on machines belonging to this server at all.
     *
     * <h2>⚠ THE ONE PLACE THAT DECIDES, and it is reachability rather than a per-bridge flag</h2>
     *
     * A server is reachable when there is a chain of open crossings from home to it: every bridge on
     * the way breached <em>and</em> carrying a NET_MAN. Asking "does this host's own bridge have a
     * NET_MAN" instead would let a player who opened one crossing act on a server two crossings out,
     * which is the whole cost of the mechanic skipped by a graph the rule never walked.
     *
     * <p>⚠ The home server is always crossable, and the walk starts there rather than at the
     * player's vantage — a vantage is where you are standing, and where you are standing must not
     * decide what the world lets you touch.
     *
     * <p>⚠ Walked over the <b>topology</b>, not over what the player has discovered. Whether a
     * crossing is open is a fact about the world; whether the player knows the machines behind it is
     * a separate question that {@code view} already answers. Mixing them would make a server flicker
     * out of reach every time a sighting was forgotten.
     */
    public static boolean crossable(GameSave save, String serverId) {
        TopologyState topology = topology(save);
        if (topology == null || serverId == null) {
            return false;
        }
        // ⚠ THE HOME TEST COMES FIRST AND IS AN EQUALITY, NOT A BLANK CHECK — and the difference is
        // not academic. A blank serverId means "this save does not name its servers", which is what a
        // hand-built or minimal topology looks like, and treating blank as "not home" locks the
        // player out of every machine in their own world including the one they are standing on.
        // Five assertions in VantageIsOnlyASweepOriginTest failed exactly that way, on a two-machine
        // fixture with no servers at all — a refusal correct for a foreign server, applied to a world
        // that has no foreign servers.
        if (java.util.Objects.equals(serverId, topology.homeServerId)) {
            return true;
        }
        if (serverId.isBlank()) {
            return false;
        }
        Map<String, HostState> hosts = index(topology);
        Set<String> open = new LinkedHashSet<>();
        open.add(topology.homeServerId);
        // A crossing may open a server that opens another, so this runs to a fixpoint rather than in
        // one pass — the bridges are not in any particular order and a single sweep of the list would
        // depend on one.
        boolean grew = true;
        while (grew) {
            grew = false;
            for (HostState host : topology.hosts) {
                if (!HostKind.BRIDGE.name().equals(host.kind)
                        || !host.foothold
                        || !host.netMan
                        || !open.contains(host.serverId)) {
                    continue;
                }
                HostState peer = hosts.get(host.bridgePeer);
                if (peer != null && open.add(peer.serverId)) {
                    grew = true;
                }
            }
        }
        return open.contains(serverId);
    }

    /** Whether the player may act on this machine — {@link #crossable}, by address. */
    public static boolean reachable(GameSave save, String address) {
        TopologyState topology = topology(save);
        if (topology == null) {
            return false;
        }
        HostState host = index(topology).get(address);
        return host != null && crossable(save, host.serverId);
    }

    /**
     * What a refusal says when a machine is behind a crossing that is not open.
     *
     * <p>⚠ Names the <b>bridge</b> and the <b>item</b>, because "you cannot reach that" is a dead end
     * and this is a two-step instruction the player can act on. A refusal that only said no would be
     * indistinguishable from the machine being broken.
     */
    public static String crossingRefusal(GameSave save, String address) {
        TopologyState topology = topology(save);
        HostState host = topology == null ? null : index(topology).get(address);
        if (host == null) {
            return "no route to that machine.";
        }
        ServerState server = servers(topology).get(host.serverId);
        String where = server == null ? "that server" : server.name;
        return "nothing on " + where + " answers yet. Upload a NET_MAN to a breached bridge into it — "
                + "the crossing stays open once it is done.";
    }

    /**
     * Starts uploading a NET_MAN onto a breached bridge.
     *
     * <h2>⚠ Loud for its whole duration and silent afterwards</h2>
     *
     * The noise rides on the task ({@link TaskState#noiseCycles}), which {@code NoiseRules} counts
     * only while the task is running — so the meter falls back the instant the upload lands and an
     * open crossing costs nothing to keep. That is the requirement, and expressing it as a task is
     * what makes it true without a decay curve to tune or a flag to clear.
     *
     * <p>⚠ <b>The item is consumed at SETTLEMENT, not here</b> — {@link #completeNetMan}. An upload
     * that spent the item up front and was then interrupted would cost the player 90 EC for nothing;
     * one that granted the crossing up front would make its duration, and its noise, optional.
     *
     * @return the task, or empty with the reason left in the log
     */
    public static Optional<TaskState> uploadNetMan(GameSave save, String address, Instant now) {
        TopologyState topology = topology(save);
        if (topology == null) {
            return Optional.empty();
        }
        HostState bridge = index(topology).get(address);
        if (bridge == null || !HostKind.BRIDGE.name().equals(bridge.kind)) {
            EventLog.info(save, "net", "a NET_MAN goes on a bridge, and that is not one.", now);
            return Optional.empty();
        }
        if (!bridge.foothold) {
            EventLog.info(save, "net", "breach that bridge before uploading anything to it.", now);
            return Optional.empty();
        }
        if (bridge.netMan) {
            EventLog.info(save, "net", "that crossing is already open.", now);
            return Optional.empty();
        }
        if (save.tasks.stream().anyMatch(task -> NETMAN_KIND.equals(task.kind) && address.equals(task.outcome))) {
            EventLog.info(save, "net", "a NET_MAN is already going up on that bridge.", now);
            return Optional.empty();
        }
        if (heldNetMan(save).isEmpty()) {
            EventLog.info(save, "net", "no NET_MAN in the vault. They are sold in the market.", now);
            return Optional.empty();
        }

        TaskState task = new TaskState(
                NETMAN_KIND,
                "net_man -> " + address,
                "",
                0L,
                now,
                now.plusSeconds(Balance.NETMAN_UPLOAD_SECONDS));
        // ⚠ The bridge's address IS the outcome. A task carries one string and this one has to
        // survive a quit — the alternative, a field on the bridge saying "upload in progress", is a
        // second place for the same fact and would be left set by an interrupted upload.
        task.outcome = address;
        task.noiseCycles = Balance.NETMAN_UPLOAD_NOISE_CYCLES;
        // ⚠ NO COMPUTE HOLD. Pushing a program down a link the player already holds is I/O, not
        // arithmetic — the same reasoning TransferRules records for a download. What it costs is
        // five minutes of being the loudest thing on the network.
        save.tasks.add(task);

        EventLog.notice(
                save,
                "net",
                "uploading NET_MAN to " + address + ": ~" + Balance.NETMAN_UPLOAD_SECONDS
                        + "s, and loud the whole time. The crossing opens when it lands.",
                now);
        return Optional.of(task);
    }

    /** A NET_MAN the player is holding, if any. */
    private static Optional<ItemState> heldNetMan(GameSave save) {
        return save.items.stream()
                .filter(item -> NETMAN_ITEM.equals(item.itemType))
                .findFirst();
    }

    /**
     * Lands a finished NET_MAN upload: the item is spent, the crossing is open for good.
     *
     * <p>⚠ Consumes the item HERE, at the end. ⚠ And if the item has gone in the meantime — sold,
     * deleted, stolen — the upload fails and says so rather than opening the crossing for free: the
     * consumable is the price of the crossing, and a path that opened one without spending one would
     * be the only way in the game to travel free.
     */
    public static boolean completeNetMan(GameSave save, TaskState task, Instant now) {
        TopologyState topology = topology(save);
        if (topology == null || task == null) {
            return false;
        }
        HostState bridge = index(topology).get(task.outcome);
        if (bridge == null) {
            return false;
        }
        Optional<ItemState> item = heldNetMan(save);
        if (item.isEmpty()) {
            EventLog.warning(
                    save,
                    "net",
                    "the NET_MAN upload to " + bridge.address + " ended with no NET_MAN to install. "
                            + "The crossing is still shut.",
                    now);
            return false;
        }
        save.items.remove(item.get());
        openCrossing(save, bridge.address, now);
        EventLog.notice(
                save,
                "net",
                "NET_MAN is running on " + bridge.address + ". The crossing is open, and stays open.",
                now);
        return true;
    }

    /**
     * Puts every machine in the world on the map — the developer facility's seam.
     *
     * <h2>⚠ Here, and not in {@code rules/Cheats}, because a discovery is a network rule</h2>
     *
     * What it takes to be discovered is three things that must stay together: the host's own flag,
     * the {@code knownNodes} row {@link #view} draws from, and the recon file that holds the
     * machine's name. {@link #settleSweep} is the only other place that does it, and a second copy
     * living in the cheat class would be a machine that appeared on the map and then behaved unlike
     * one a sweep had found — the exact failure {@code IntrusionRules} exists to prevent for
     * parasites.
     *
     * <h2>⚠ Discovery, identity, and a foothold on every BRIDGE — and on nothing else</h2>
     *
     * Every machine gets what a sweep plus a Passive Sniffer would have given: it exists, and it is
     * called something. Bridges get one thing more — a <b>foothold</b>, i.e. they are treated as
     * breached — on explicit direction (2026-08-09), because that is what puts every server on the
     * map's tab strip: a server reaches the strip by having a breached bridge pointing at it
     * ({@code design/18} §2.8), and "reveal the whole map" that left every tab but home missing would
     * not have revealed the map.
     *
     * <p>⚠ <b>Bridges only, and this is a real capability grant rather than a display change.</b> A
     * foothold is what {@link #connect} checks, so every bridge in the world becomes a place the
     * player can move their vantage to. That is the honest consequence of the request and it is
     * confined to the machines that carry the servers; ordinary machines still need breaching, and
     * nothing anywhere gets {@code looted} or {@code patched} — the loot is a one-time payout that
     * {@code reconcileFootholds} would otherwise credit for every machine in the world at once.
     *
     * <p>Idempotent: a machine already discovered is skipped, a foothold is a one-way flag, and
     * {@code NodeReports.establishIdentity} is write-once by its own rule.
     *
     * @return how many machines this discovery added
     */
    public static int revealAll(GameSave save, Instant now) {
        TopologyState topology = topology(save);
        if (topology == null) {
            return 0;
        }
        int revealed = 0;
        for (HostState host : topology.hosts) {
            // The player's own rig is not a discovery, and adding a knownNodes row for it would put
            // SELF on the map twice.
            if (host.address == null
                    || host.address.isBlank()
                    || host.address.equals(topology.playerAddress)) {
                continue;
            }
            // ⚠ EVERYTHING EXCEPT THE knownNodes ROW IS OUTSIDE THE DISCOVERY GUARD, and putting
            // any of it inside is a bug this method has already shipped twice.
            //
            // A machine the player found by an ordinary sweep is `discovered` and usually NOT
            // `identified` — a sweep sells existence and adjacency, the Passive Sniffer sells
            // identity. So a reveal that skipped it entirely left it anonymous forever: measured on
            // a render, the machines the harness had swept drew as `----` while the ones the reveal
            // found drew as TERM/STOR/RELA, on the same map, after a "reveal the whole map".
            //
            // The rule is: a reveal grants every machine everything it grants ANY machine. Otherwise
            // the cheat does less the more of the game you have played, which is the shape the
            // foothold half was fixed for and the identity half was not.
            if (HostKind.BRIDGE.name().equals(host.kind)) {
                host.foothold = true;
                // ⚠ AND THE CROSSING IS OPENED — added 2026-08-09, and without it this cheat stopped
                // working the day crossings landed. A breached bridge no longer puts its far server
                // on the tab strip on its own, and nothing on the far side answers until a NET_MAN is
                // running, so "reveal the whole map" would have revealed a map of one server with
                // every other machine drawn and untouchable. This is the same argument the foothold
                // grant itself was added under: a reveal grants every machine everything it grants
                // any machine.
                //
                // ⚠ It spends no NET_MAN and needs none. The cheat is the whole point; charging it an
                // item would make "reveal the map" cost a shopping trip.
                host.surveyed = true;
                // Through the one door, so a revealed crossing behaves exactly like an opened one —
                // including publishing its far side, which the reveal would otherwise do by accident
                // for every machine and therefore never be checked on.
                openCrossing(save, host.address, now);
            }
            NodeReports.establishIdentity(save, host, now);
            // ⚠ Only the sighting itself is once-per-machine: knownNodes is a list and a second row
            // for one address is a machine drawn twice.
            if (!host.discovered) {
                host.discovered = true;
                save.knownNodes.add(nodeFor(host, now));
                revealed++;
            }
            // ⚠ THROUGH `identify`, AND AFTER THE ROW EXISTS. This was `host.identified = true`, which
            // reached the map (`sighting` reads that flag) and reached nothing else — so a revealed
            // machine drew as TERM on the map and blank in the breach window's ROLE column, which
            // reads `NodeState.kind`. Two surfaces disagreeing about what the player knows is the
            // failure this method's own note above was written for, one field along.
            //
            // ⚠ It must come after the `knownNodes` row is added, because `identify` writes onto that
            // row — a newly revealed machine has none until the block above runs. Hence the `continue`
            // became an `if`.
            identify(save, host);
        }
        return revealed;
    }

    /**
     * Fills the recon file of every machine already on the map — the developer facility's seam.
     *
     * <h2>⚠ DISCOVERED MACHINES ONLY, and that is what makes it compose rather than overlap</h2>
     *
     * A recon file for a machine the player has never found would appear in RECON as a report on
     * something absent from the map — the one surface that must never hint at what has not been
     * discovered ({@link #revealAll} is the button for that, and pressing both in either order gives
     * the same result). It also keeps this honest as a <em>scanning</em> cheat rather than a second
     * discovery cheat wearing its clothes.
     *
     * <p>Delegates per machine to {@code NodeReports.learnEverything}, which goes through the same
     * merge a real scan does. Here rather than in {@code rules/Cheats} for {@link #revealAll}'s
     * reason: which machines are on the map is a network question.
     *
     * @return how many files were filled
     */
    public static int learnEverything(GameSave save, Instant now) {
        TopologyState topology = topology(save);
        if (topology == null) {
            return 0;
        }
        int filled = 0;
        for (HostState host : topology.hosts) {
            if (!host.discovered
                    || host.address == null
                    || host.address.isBlank()
                    || host.address.equals(topology.playerAddress)) {
                continue;
            }
            if (NodeReports.learnEverything(save, host, now)) {
                filled++;
            }
        }
        return filled;
    }

    /**
     * Cuts a sweep's frozen result down to the fraction it managed before it was killed.
     *
     * <h2>⚠ A truncation of the stored answer, never a new roll</h2>
     *
     * {@link #beginSweep} decides the whole sweep at commission precisely so that quitting cannot
     * change it. Killing early therefore takes the machines it had already reached — the first
     * {@code round(progress × n)} of them, in the order they were found — and drops the rest. Asking
     * the rules for a fresh, smaller sweep would be a re-roll the player could force at will, which
     * is the exploit every frozen outcome in this engine exists to close.
     *
     * <p>The counter-hack is dropped with the tail. It is the network answering a sweep that ran to
     * completion, and one the player pulled the plug on halfway did not finish provoking anybody —
     * which is a real and legible reason to kill a deep sweep that is making you nervous.
     *
     * @param progress how far it got, {@code [0, 1]}
     */
    public static void truncate(TaskState task, double progress) {
        Encoded encoded = decode(task);
        double fraction = Math.max(0.0d, Math.min(1.0d, progress));
        int keep = (int) Math.round(encoded.found().size() * fraction);
        List<String> kept =
                encoded.found().subList(0, Math.max(0, Math.min(encoded.found().size(), keep)));
        task.outcome = encode(
                encoded.toolId(),
                encoded.vantage(),
                encoded.inRange(),
                fraction >= 1.0d ? encoded.counterHackDepth() : -1,
                kept,
                // ⚠ A truncated sweep keeps its survey flag. Truncation models a sweep cut short —
                // it takes away CONTACTS, and a survey is not a contact. Dropping it here would make
                // an interrupted deep sweep from a bridge silently forget the one thing it was run
                // for, in a method whose whole subject is partial credit.
                encoded.survey());
    }

    /**
     * Decodes a finished sweep's frozen result without applying it — the readout's seam.
     *
     * <p>Tolerant of a malformed line for the same reason {@code ScanRules.finding} is: a save
     * written before this existed, or edited by hand, must open. It reports an empty sweep rather
     * than a confident wrong one.
     */
    public static SweepReport report(TaskState task) {
        Encoded encoded = decode(task);
        return new SweepReport(
                encoded.toolId(),
                encoded.vantage(),
                encoded.inRange(),
                encoded.found().size(),
                encoded.found(),
                encoded.counterHackDepth() >= 0,
                note(encoded));
    }

    /**
     * What the sweep says when it found nothing.
     *
     * <p>⚠ In the player's language, and it must stay there. The mechanic — a fixed roll, compared
     * against a threshold the player can only move by buying a better instrument or standing
     * somewhere closer — is a good one and an invisible one. A sweep that silently returned nothing
     * twice would read as a bug; one that explains why repetition is not the answer teaches the whole
     * model in three lines.
     */
    private static String note(Encoded encoded) {
        if (!encoded.found().isEmpty()) {
            return "";
        }
        if (encoded.inRange() == 0) {
            return "Nothing within reach of this position. A foothold you can connect to is what "
                    + "moves reach; the instrument does not.";
        }
        // ⚠ "A DIFFERENT position", not "a closer" one, since 2026-08-08. What a sweep can hear now
        // depends on where it is standing rather than only on how far away the machine is, so a
        // foothold the same distance away is a genuine second chance — and a player told to get
        // "closer" would read that as pointless and stay put. The sentence is the only place the game
        // teaches the traversal loop at the moment it matters, so it has to describe the real rule.
        return "Nothing at this sensitivity that you have not already seen. A louder instrument or a "
                + "different position is what changes this; running the same sweep again is not.";
    }

    /**
     * Plants a parasite the sweep provoked.
     *
     * <p>The planting itself moved to {@code IntrusionRules}: a loud breach can now provoke one too,
     * and two packages that must not depend on each other both needed it. What stays here is the
     * decision — a sweep's counter-hack is rolled at commission and frozen into the task, so a reload
     * mid-sweep replays nothing.
     */
    private static void counterHack(GameSave save, int depth, Instant now) {
        io.github.stoicswe.eyeandsickle.engine.rules.IntrusionRules.plantCounterHack(save, depth, now);
    }

    /** {@code 1.00} at one hop, {@code 0.60} at two — see {@code Balance.NET_HOP_FACTOR_2}. */
    private static double hopFactor(Integer hops) {
        return hops != null && hops >= 2 ? Balance.NET_HOP_FACTOR_2 : Balance.NET_HOP_FACTOR_1;
    }

    /**
     * How audible one machine is <b>from one position</b> — what a sweep's threshold is compared to.
     *
     * <h2>⚠ THIS USED TO BE {@code host.detectRoll} AND THE DIFFERENCE IS THE WHOLE FEATURE</h2>
     *
     * A machine's own roll is fixed for the life of the world, so under the old rule a contact that a
     * base sweep missed from the rig was missed from <em>everywhere</em> at that tier: moving the
     * vantage brought different machines into range, and never made a machine already in range
     * audible. Repositioning bought reach and nothing else.
     *
     * <p>Scaling by a hash of the <b>pair</b> makes "what you can hear depends on where you are
     * standing" true, which is both the better physical model and the thing that makes working
     * outward pay: sweep, move to a foothold, sweep again, and some of what the first look could not
     * hear comes back. Over many positions that accumulates into a large graph, and because a world
     * is generated per character it is already that player's own — nothing here has to match anybody
     * else's ({@code docs/design/07-recon-tools.md} §1a).
     *
     * <h2>⚠ HASHED, NEVER DRAWN — the single line that keeps save-scumming dead</h2>
     *
     * {@code AddressHash} exists for exactly this: a value fixed before the player asks, that cannot
     * give two answers to the same question. A {@code Rng} draw here would read as the same feature
     * and would make re-sweeping one spot a lottery, which is what {@code SweepDeterminismTest} and
     * the whole "only a louder instrument or a different position" contract forbid.
     *
     * <p>⚠ The key is composed with the salt {@code AddressHash} already requires, so this derivation
     * is uncorrelated with the yield's variation, the machine's name and its MonJob — all of which
     * hash the same addresses.
     */
    private static double audibility(HostState host, String vantage) {
        double unit = AddressHash.unitOf(host.address + " from " + vantage, "sweep-audibility");
        return Balance.netSweepAudibility(host.detectRoll, unit);
    }

    /**
     * Whether the player owns this sweep tier.
     *
     * <p>The base tier is starting kit — the same class as Port Sweep, which
     * {@code docs/design/06-intrusion-tools.md} §2 calls "the free starting enumerator. Everyone has
     * it." Without it a new player has no way to find the machines next to them, which is the problem
     * this whole system exists to fix.
     *
     * <p>⚠ Checked here rather than through {@code Targets.owns}, because that method's map is the
     * <em>breach loadout</em> and a sweep tool must never be in it: adding one would silently raise
     * {@code Targets.attemptCycles} for every breach the player opens.
     */
    public static boolean owns(GameSave save, SweepTier tier) {
        if (tier == SweepTier.BASE) {
            return true;
        }
        if (save == null || save.items == null) {
            return false;
        }
        for (ItemState item : save.items) {
            if (tier.itemId().equals(item.itemType)) {
                return true;
            }
        }
        return false;
    }

    // ================================================================== traversal

    /**
     * Moves the vantage — the whole traversal loop, in one method.
     *
     * <p>Refuses unless the address is a host the player holds a foothold on, or their own rig. That
     * refusal is the reason a one-hop ceiling is survivable and the reason it is not exploitable:
     * position substitutes for reach, and position is earned with a breach rather than bought with
     * ethecoin. See {@code TopologyState}'s class note.
     */
    public static boolean connect(GameSave save, String address, Instant now) {
        TopologyState topology = topology(save);
        if (topology == null || address == null || address.isBlank()) {
            return false;
        }
        HostState host = host(save, address.trim());
        if (host == null) {
            return false;
        }
        boolean ownRig = host.address.equals(topology.playerAddress);
        if (!ownRig && !host.foothold) {
            return false;
        }
        // ⚠ A FOOTHOLD IS NOT A ROUTE. Moving onto a machine behind a shut crossing would put the
        // player's vantage on a server nothing else lets them touch — and, worse, would let them
        // sweep from it, which is the whole thing the crossing withholds. The only footholds behind a
        // shut crossing today are the far bridges a DEEP survey published; this is what stops one of
        // those being a free way in.
        if (!ownRig && !crossable(save, host.serverId)) {
            EventLog.info(save, "net", crossingRefusal(save, host.address), now);
            return false;
        }
        if (!host.address.equals(topology.vantageAddress)) {
            topology.vantageAddress = host.address;
            EventLog.notice(
                    save,
                    "net",
                    "operating from " + host.address + (ownRig ? " (localhost)" : " — sweeps now reach from here."),
                    now);
        }
        return true;
    }

    /**
     * Grants footholds and one-time payouts for every successful breach that has not been settled.
     *
     * <p>⚠ <b>Idempotent by construction, not by bookkeeping.</b> There is no "settled" flag on a
     * resolution; instead a host records that it has a foothold and that it has been looted, and both
     * are one-way. So replaying the entire resolution list on every load is correct rather than merely
     * cheap, and a save whose resolutions were duplicated by a bad merge still pays out once.
     *
     * <p>⚠ The payout is currency, and that reads against {@code BreachRules.resolveOffensive}'s
     * standing note about faucets ({@code docs/design/03-economy.md} §5 rule 3). Both survive, and
     * the distinction is real: the breach engine mints <b>nothing at all</b>, while this credits a
     * <b>finite quantity placed in the world at generation</b> — a stock, not a flow. Home's entire
     * pool is about 68 EC and then it is gone, so no rate exists to compare against §5 rule 1's
     * 70 EC/hr cap. Logged in {@code docs/design/15-open-questions.md} §3.
     *
     * <p>⚠ <b>AMENDED 2026-08-09.</b> This used to read "the breach engine still mints a data cache
     * and no currency". It no longer mints the cache either — that item was inert, unsellable and
     * unusable, and its only effect was to consume a storage slot per breach. So <b>this method and
     * the foothold are now the entire reward for taking a machine</b>, which makes the note above
     * about a finite stock the load-bearing half of the argument rather than half of a pair.
     *
     * @return true if anything changed and the caller should persist
     */
    public static boolean reconcileFootholds(GameSave save, Instant now) {
        TopologyState topology = topology(save);
        if (topology == null || save.resolutions == null) {
            return false;
        }
        boolean changed = false;
        for (ResolutionState resolution : save.resolutions) {
            if (!"BREACHED".equals(resolution.outcome) || resolution.targetId == null) {
                continue;
            }
            if (!resolution.targetId.startsWith("node:")) {
                continue;
            }
            HostState host = host(save, resolution.targetId.substring("node:".length()));
            if (host == null) {
                continue;
            }
            if (!host.foothold) {
                host.foothold = true;
                changed = true;
                EventLog.notice(
                        save,
                        "net",
                        "foothold on " + host.address + "; `connect " + host.address + "` to sweep from it.",
                        now);
            }
            // ⚠ Outside the foothold guard, so a machine breached before identities were recorded
            // gets one on the next load rather than staying permanently anonymous — this method is
            // idempotent by construction and runs on every resume, which is what makes that safe.
            // It is write-once itself, so the name a player learned on the first break-in is the name
            // they keep. See NodeReports#establishIdentity.
            if (NodeReports.establishIdentity(save, host, now)) {
                changed = true;
            }
            // ⚠ AND WHAT IT IS, on exactly `establishIdentity`'s reasoning and in exactly the same
            // place: standing on a machine, its name, its account and its kind are the three facts
            // you cannot avoid learning. This is the half that had no writer at all — see `identify`.
            //
            // ⚠ Outside the foothold guard for this method's stated reason, and it earns it: a
            // machine breached before this existed is typed on the next load rather than staying
            // permanently anonymous. `identify` is idempotent — it writes a value derived from the
            // host, so a second pass writes the same string.
            identify(save, host);
            if (!host.looted) {
                host.looted = true;
                changed = true;
                if (host.lootWei.signum() > 0) {
                    LedgerRules.apply(save, host.lootWei, LOOT_LEDGER_TYPE, "Recovered from " + host.address, now);
                    EventLog.info(
                            save, "net", Ethecoin.format(host.lootWei) + " recovered from " + host.address + ".", now);
                }
            }
        }
        return changed;
    }

    // ================================================================== documents

    /**
     * Pulls a story fragment off a host the player holds.
     *
     * <p>⚠ Schematic material only at tier 3 or above — {@code Balance.SCHEMATIC_MATERIAL_MIN_TIER},
     * the same constant and the same denominator the breach salvage path already uses. Invariant I13:
     * the drop is gated on <em>engagement tier</em>, never on a count, because the alternative is that
     * the optimal play becomes farming the softest thing that qualifies. A deep-but-easy host yields
     * flavour and nothing else, which is exactly the exploit I13 exists to close.
     *
     * <p>Refuses on a host with no foothold, no fragment, or a fragment already taken. Nothing here is
     * required to advance (decision N-4) — the fragments are pull, not path.
     */
    public static Optional<NetDocument> download(GameSave save, String address, Instant now) {
        TopologyState topology = topology(save);
        if (topology == null) {
            return Optional.empty();
        }
        HostState host = host(save, address);
        if (host == null || !host.foothold || host.documentId.isEmpty() || host.documentTaken) {
            return Optional.empty();
        }
        host.documentTaken = true;
        host.documentTakenAt = now;
        topology.documents.add(host.documentId);

        int material = host.tier >= Balance.SCHEMATIC_MATERIAL_MIN_TIER ? Balance.SCHEMATIC_MATERIAL_PER_BREACH : 0;
        save.schematicMaterial += material;

        EventLog.notice(
                save,
                "net",
                "recovered " + DocumentPool.title(host.documentId) + " from " + host.address
                        + (material > 0 ? "; it carried schematic material." : "."),
                now);
        return Optional.of(
                new NetDocument(host.documentId, DocumentPool.title(host.documentId), host.address, now, material));
    }

    /**
     * Every fragment recovered so far, oldest first.
     *
     * <p>Read off the hosts rather than off {@code TopologyState.documents}, because twelve fragment
     * ids are spread across up to 350 machines and two hosts can carry the same one — an id list
     * cannot say which host a duplicate came from or when. Ordered by recovery time with the address
     * as a stable tiebreak, so the list does not reshuffle between repaints.
     */
    public static List<NetDocument> documents(GameSave save) {
        TopologyState topology = topology(save);
        if (topology == null) {
            return List.of();
        }
        List<HostState> taken = new ArrayList<>();
        for (HostState host : topology.hosts) {
            if (host.documentTaken && !host.documentId.isEmpty()) {
                taken.add(host);
            }
        }
        taken.sort(Comparator.comparing((HostState h) -> h.documentTakenAt == null ? Instant.EPOCH : h.documentTakenAt)
                .thenComparing(h -> h.address));

        List<NetDocument> out = new ArrayList<>(taken.size());
        for (HostState host : taken) {
            out.add(new NetDocument(
                    host.documentId,
                    DocumentPool.title(host.documentId),
                    host.address,
                    host.documentTakenAt == null ? Instant.EPOCH : host.documentTakenAt,
                    host.tier >= Balance.SCHEMATIC_MATERIAL_MIN_TIER ? Balance.SCHEMATIC_MATERIAL_PER_BREACH : 0));
        }
        return out;
    }

    // ================================================================== lookups

    /**
     * Ground truth for one address, or null.
     *
     * <p>⚠ Not a read model. Nothing that reaches the client may be built from this — {@link #view}
     * is the seam, and it exists so the fields a player has not paid for cannot leak through a
     * convenient lookup. Public because {@code GameEngine} needs it to answer "is this a real address"
     * before refusing a command.
     */
    public static HostState host(GameSave save, String address) {
        TopologyState topology = topology(save);
        if (topology == null || address == null) {
            return null;
        }
        String wanted = address.trim();
        for (HostState host : topology.hosts) {
            if (host.address.equals(wanted)) {
                return host;
            }
        }
        return null;
    }

    /** Where sweeps are run from right now. Falls back to the player's rig on a truncated save. */
    public static String vantageAddress(GameSave save) {
        TopologyState topology = topology(save);
        if (topology == null) {
            return "";
        }
        return topology.vantageAddress == null || topology.vantageAddress.isBlank()
                ? topology.playerAddress
                : topology.vantageAddress;
    }

    private static TopologyState topology(GameSave save) {
        return save == null ? null : save.topology;
    }

    private static Map<String, HostState> index(TopologyState topology) {
        Map<String, HostState> out = new HashMap<>();
        for (HostState host : topology.hosts) {
            out.put(host.address, host);
        }
        return out;
    }

    private static Map<String, ServerState> servers(TopologyState topology) {
        Map<String, ServerState> out = new HashMap<>();
        for (ServerState server : topology.servers) {
            out.put(server.serverId, server);
        }
        return out;
    }

    private static Map<String, NodeState> knownNodes(GameSave save) {
        Map<String, NodeState> out = new HashMap<>();
        if (save.knownNodes != null) {
            for (NodeState node : save.knownNodes) {
                out.put(node.address, node);
            }
        }
        return out;
    }

    private static ServerRef serverRef(ServerState server) {
        return server == null
                ? new ServerRef("", "", 0, false)
                : new ServerRef(server.serverId, server.name, server.depthFromHome, server.home);
    }

    /**
     * The player's knowledge of a machine a sweep just found.
     *
     * <p>⚠ {@code kind} stays {@code UNKNOWN} here and {@code trafficAnalyzed} and
     * {@code honeypotSuspected} stay false throughout. The latter two are the Traffic Analyzer's and
     * the Honeypot Detector's products; a sweep that set either would delete a gated tool at the
     * point of discovery, and setting {@code trafficAnalyzed} would additionally hand out
     * proof-of-skill credit (Invariant I7). A test asserts all three.
     *
     * <p>⚠ <b>{@code kind} is no longer one of them unconditionally (2026-08-09).</b> This method
     * still leaves it {@code UNKNOWN} — every discovery starts anonymous, whatever found it — but
     * {@code settleSweep} calls {@link #identify} straight afterwards when the tool was a DEEP sweep.
     * The distinction that survives is between the <em>tiers</em>: BASE and WIDE sell existence and
     * adjacency, DEEP also sells the type.
     *
     * <p>⚠ <b>{@code label} is now a fourth, added 2026-08-07.</b> This method used to copy
     * {@link HostState#label} in, which named every machine the moment a sweep touched it. The name
     * is a port-scan finding now ({@code PortScanTarget.IDENTITY}) and lives on the recon file, so
     * this field stays empty and nothing reads it — a sweep's product is existence and adjacency, and
     * a name is neither.
     */
    private static NodeState nodeFor(HostState host, Instant now) {
        NodeState node = new NodeState();
        node.address = host.address;
        node.serverId = host.serverId;
        node.kind = HostKind.UNKNOWN.name();
        // ⚠ Still UNKNOWN here, and `identify` is the only thing that changes it. See that method.
        // ⚠ The default is Instant.now(); a rule that leaves it there dates every discovery to the
        // real world's present regardless of the clock its caller is running on.
        node.discoveredAt = now;
        node.reconLevel = 1;
        node.tier = host.tier;
        node.firewallTier = host.firewallTier;
        node.tarpit = host.tarpit;
        node.canaries = host.canaries;
        node.defended = host.defended;
        return node;
    }

    /**
     * Records that the player has established what a machine <em>is</em>.
     *
     * <h2>⚠ THE ONE WRITER, and before 2026-08-09 there was NO writer at all</h2>
     *
     * {@link NodeState#kind} was assigned exactly once in this whole codebase — to {@code "UNKNOWN"},
     * in {@link #nodeFor} — and {@link HostState#identified} only by {@code TopologyGenerator} (for
     * the player's own rig) and {@link #revealAll} (the developer cheat). The tool nine comments in
     * this package defer to, the 15 EC Passive Sniffer, <b>is not in {@code Catalogue}</b> and there
     * is no {@code PortScanTarget} rung for a machine's type either.
     *
     * <p>So every box on the network map read {@code ----} forever, and the entire type vocabulary
     * built against it — {@code NetGlyphs.NODE_BRIDGE}, the woven bridge frame, the drawbridge
     * markers, the {@code ··} stub, {@code es-netmap-bridge}, {@code es-netmap-identified} and
     * {@code Targets.role} — was unreachable outside the cheat. Measured on a real 575-host save:
     * one identified host (the rig) and all nineteen discovered nodes {@code UNKNOWN}. Nothing
     * failed and every screen rendered; it was only wrong to look at.
     *
     * <h2>⚠ Two acts establish it, and neither is a sweep in general</h2>
     *
     * A <b>DEEP</b> sweep types what it picks up — the dearest instrument, and the same tier gate the
     * survey-across already sits behind. A <b>foothold</b> types the machine outright, on
     * {@code NodeReports.establishIdentity}'s reasoning: standing on a machine, what it is cannot be
     * avoided. A BASE or WIDE sweep still sells existence and adjacency and nothing else, which is
     * what leaves the Passive Sniffer something to sell when it ships — typing a machine you found
     * cheaply and have not broken into.
     *
     * <p>⚠ It writes {@link NodeState#kind} and deliberately <b>not</b> {@link HostState#identified}.
     * That field is on the world object and its only real writer is a cheat; this one is the player's
     * stored knowledge, it is what {@link #sighting} already reads, and it is what {@code
     * Targets.role} reads — so the map and the breach window cannot come to different answers about
     * what the player knows.
     *
     * <p>⚠ It does <b>not</b> touch {@code NodeReportState}, so {@code NodeReports.known} is
     * unmoved and typing a machine does not shift which puzzle a breach draws
     * ({@code Balance.breachProtocolShare}). A type is not a port-scan rung.
     */
    private static void identify(GameSave save, HostState host) {
        if (save == null || host == null) {
            return;
        }
        String typed = HostArchetypes.kindOrUnknown(host.kind).name();
        for (NodeState node : save.knownNodes) {
            if (node.address.equals(host.address)) {
                node.kind = typed;
                return;
            }
        }
    }

    // ================================================================== the frozen result

    /**
     * A sweep's decided outcome, as it sits in {@link TaskState#outcome}.
     *
     * @param counterHackDepth the depth that provoked a counter-hack, or {@code -1} for none — the
     *     depth is carried rather than a bare flag because it sets the planted miner's tier, its host
     *     cycles, whether it is rootkit-wrapped, and how much heat it costs
     */
    private record Encoded(
            String toolId, String vantage, int inRange, int counterHackDepth, List<String> found, boolean survey) {}

    /**
     * One line, pipe-separated, versioned.
     *
     * <p>A string rather than a nested object for the same reason every other {@code state} class
     * uses strings: this lands in a JSON document that outlives the code that wrote it, and
     * {@link TaskState#outcome} is already a {@code String} carrying a scan's frozen finding. The
     * {@code v1} tag is what lets a later shape be added without a save written today decoding as
     * garbage — an unrecognised version reads as an empty sweep, which is honest.
     */
    private static String encode(
            String toolId,
            String vantage,
            int inRange,
            int counterHackDepth,
            List<String> found,
            boolean survey) {
        return String.join(
                "|",
                "sweep",
                // ⚠ v2 carries the survey flag. Whether this sweep was a look ACROSS a crossing is
                // decided at commission like everything else here, rather than recomputed at
                // settlement from the vantage and the tool — the player may have moved their vantage
                // during the five minutes it runs, and a survey that re-asked "am I on a bridge?"
                // afterwards would answer about wherever they ended up.
                "v2",
                toolId,
                vantage,
                Integer.toString(inRange),
                Integer.toString(counterHackDepth),
                String.join(",", found),
                Boolean.toString(survey));
    }

    private static Encoded decode(TaskState task) {
        String raw = task == null || task.outcome == null ? "" : task.outcome;
        String[] parts = raw.split("\\|", -1);
        if (parts.length < 8 || !"sweep".equals(parts[0]) || !"v2".equals(parts[1])) {
            // A sweep from a build that predates this encoding, or a hand-edited save. Reporting an
            // empty sweep is the reading that cannot invent contacts or plant a miner nobody earned.
            return new Encoded("", "", 0, -1, List.of(), false);
        }
        List<String> found = new ArrayList<>();
        for (String address : parts[6].split(",")) {
            if (!address.isBlank()) {
                found.add(address);
            }
        }
        return new Encoded(
                parts[2],
                parts[3],
                parseInt(parts[4], 0),
                parseInt(parts[5], -1),
                List.copyOf(found),
                Boolean.parseBoolean(parts[7]));
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }
}
