package io.github.stoicswe.eyeandsickle.engine.net;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.Catalogue;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.HostState;
import io.github.stoicswe.eyeandsickle.engine.state.ItemState;
import io.github.stoicswe.eyeandsickle.engine.state.TaskState;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Crossings: what a sweep may see across one, and what it takes to open it.
 *
 * <h2>The four rules this file exists to hold</h2>
 *
 * <ol>
 *   <li>A sweep never reaches onto another server. The one exception is a DEEP sweep taken standing
 *       ON a bridge, which may publish the machine at the far end and nothing behind it.
 *   <li>Past {@link Balance#NET_BRIDGE_REVEAL_SHARE} of a server's machines, a WIDE or DEEP sweep
 *       finds its bridges regardless of position, roll or yield cap.
 *   <li>Nothing on a foreign server answers until a NET_MAN is running on a breached bridge into it,
 *       and that reachability is transitive.
 *   <li>The upload is loud while it runs and silent afterwards, and it spends the item.
 * </ol>
 */
@DisplayName("crossings")
class CrossingTest {

    private static long seed(int i) {
        return i * 0x9E3779B97F4A7C15L + 0x2545F491L;
    }

    /**
     * A world with both sweep upgrades <b>and the Topology Mapper</b>.
     *
     * <h2>⚠ THE MAPPER IS LOAD-BEARING IN THIS FILE, and leaving it out made a test vacuous</h2>
     *
     * {@code NetRules.hopCeiling} is <b>1</b> without it. At one hop the only machine on a foreign
     * server that is ever in range from a bridge is its own peer — which the crossing publishes
     * anyway — so "a sweep never reaches onto another server" had nothing to catch: removing the
     * same-server rule entirely left every assertion in {@link NeverAcross} green. Found by running
     * the negative test, not by reading it. At two hops the peer's neighbours come into range and the
     * rule has something to refuse.
     */
    private static GameSave world(long seed) {
        GameSave save = NetTestKit.world(seed);
        NetTestKit.grant(save, SweepTier.WIDE);
        NetTestKit.grant(save, SweepTier.DEEP);
        ItemState mapper = new ItemState();
        mapper.itemType = NetRules.TOPOLOGY_MAPPER;
        mapper.displayName = "Topology Mapper";
        mapper.acquiredAt = NetTestKit.T0;
        save.items.add(mapper);
        return save;
    }

    private static String homeServer(GameSave save) {
        for (HostState host : save.topology.hosts) {
            if (host.address.equals(save.topology.playerAddress)) {
                return host.serverId;
            }
        }
        throw new IllegalStateException("no rig");
    }

    private static List<HostState> bridgesOn(GameSave save, String serverId) {
        return save.topology.hosts.stream()
                .filter(host -> HostKind.BRIDGE.name().equals(host.kind))
                .filter(host -> serverId.equals(host.serverId))
                .toList();
    }

    /**
     * ⚠ BOTH HALVES. {@code HostState.discovered} gates existence and the {@code knownNodes} row is
     * what the map draws and what a survey writes its estimate onto — a fixture that set only the
     * flag makes every estimate assertion look up a row that is not there.
     */
    private static void discover(GameSave save, HostState host) {
        host.discovered = true;
        if (save.knownNodes.stream().noneMatch(node -> node.address.equals(host.address))) {
            io.github.stoicswe.eyeandsickle.engine.state.NodeState node =
                    new io.github.stoicswe.eyeandsickle.engine.state.NodeState();
            node.address = host.address;
            node.serverId = host.serverId;
            save.knownNodes.add(node);
        }
    }

    /** Hop distance over the full link graph — the same walk the sweep's own ceiling is measured in. */
    private static int hopsFrom(GameSave save, String from, String to) {
        java.util.Map<String, HostState> hosts = new java.util.HashMap<>();
        for (HostState host : save.topology.hosts) {
            hosts.put(host.address, host);
        }
        Integer distance = TopologyGenerator.bfs(hosts, from).get(to);
        return distance == null ? Integer.MAX_VALUE : distance;
    }

    private static HostState peerOf(GameSave save, HostState bridge) {
        return save.topology.hosts.stream()
                .filter(host -> host.address.equals(bridge.bridgePeer))
                .findFirst()
                .orElseThrow();
    }

    @Nested
    @DisplayName("a sweep never reaches onto another server")
    class NeverAcross {

        /**
         * ⚠ The rule the whole feature rests on, and it was <b>false</b> before 2026-08-09. The hop
         * BFS walks the full topology and a cross-server link is an ordinary edge in it, so standing
         * on a bridge with a two-hop ceiling put the far bridge <em>and its neighbours</em> in range —
         * a sweep quietly delivering a foreign server's machines with nothing opened.
         */
        @Test
        @DisplayName("standing on a bridge, a WIDE sweep publishes nothing on the far server")
        void wideSeesNothingAcross() {
            for (int i = 0; i < 60; i++) {
                GameSave save = world(seed(i));
                String home = homeServer(save);
                List<HostState> bridges = bridgesOn(save, home);
                if (bridges.isEmpty()) {
                    continue;
                }
                HostState bridge = bridges.getFirst();
                discover(save, bridge);
                bridge.foothold = true;
                // ⚠ THIS crossing only. `NetTestKit.openCrossings` opens every bridge in the world
                // and publishes every peer — including the peers of bridges ON the far server — so
                // using it here would leave several machines discovered over there and the assertion
                // would be measuring the fixture rather than the rule.
                NetRules.openCrossing(save, bridge.address, NetTestKit.T0);
                NetRules.connect(save, bridge.address, NetTestKit.T0);
                String far = peerOf(save, bridge).serverId;

                var report = NetTestKit.sweep(save, SweepTier.WIDE, NetTestKit.T0);

                // ⚠ THE CANDIDATE COUNT IS THE DECISIVE ASSERTION, and the discovery one below is
                // not. `inRange` is how many machines the sweep CONSIDERED, so a sweep that reached
                // across shows up here even when the far machines then fail their audibility roll —
                // and measured, they nearly always do at two hops. Asserting only on what was
                // discovered left the same-server rule removable with every test still green, which
                // is the exact "reports a guarantee it is not checking" failure this repo keeps
                // hitting. Found by running the negative test, twice.
                long ownServerInRange = save.topology.hosts.stream()
                        .filter(host -> home.equals(host.serverId))
                        .filter(host -> !host.address.equals(save.topology.playerAddress))
                        .filter(host -> hopsFrom(save, bridge.address, host.address) >= 1)
                        .filter(host -> hopsFrom(save, bridge.address, host.address)
                                <= NetRules.hopCeiling(save))
                        .count();
                assertThat((long) report.inRange())
                        .as("world %d: a sweep considers its own server only", i)
                        .isEqualTo(ownServerInRange);

                // Only the far bridge itself may be over there, and it is published by the crossing
                // having been opened rather than by the sweep.
                assertThat(save.topology.hosts.stream()
                                .filter(host -> far.equals(host.serverId))
                                .filter(host -> host.discovered)
                                .map(host -> host.address)
                                .toList())
                        .as("world %d", i)
                        .allMatch(address -> address.equals(bridge.bridgePeer));
            }
        }

        /**
         * ⚠ A DEEP sweep from the bridge is the one act that may look across, and what it publishes
         * is the far bridge — never a neighbour of it. An earlier draft admitted anything one hop
         * onto the far server, which at a two-hop ceiling is the leak this rule exists to close.
         */
        @Test
        @DisplayName("a DEEP sweep from a bridge publishes the far bridge and nothing beside it")
        void deepPublishesOnlyThePeer() {
            int surveyed = 0;
            for (int i = 0; i < 60 && surveyed < 12; i++) {
                GameSave save = world(seed(i));
                String home = homeServer(save);
                List<HostState> bridges = bridgesOn(save, home);
                if (bridges.isEmpty()) {
                    continue;
                }
                HostState bridge = bridges.getFirst();
                discover(save, bridge);
                bridge.foothold = true;
                // ⚠ NOT opened. This is the survey route on its own, which is the whole point: it
                // must publish the far bridge without a NET_MAN having been spent.
                NetRules.connect(save, bridge.address, NetTestKit.T0);
                HostState peer = peerOf(save, bridge);

                NetTestKit.sweep(save, SweepTier.DEEP, NetTestKit.T0);
                if (!bridge.surveyed) {
                    continue;
                }
                surveyed++;

                assertThat(peer.discovered).as("world %d: the far bridge", i).isTrue();
                assertThat(save.topology.hosts.stream()
                                .filter(host -> peer.serverId.equals(host.serverId))
                                .filter(host -> host.discovered)
                                .count())
                        .as("world %d: and nothing else over there", i)
                        .isEqualTo(1);
            }
            assertThat(surveyed).as("some world let a deep survey run").isPositive();
        }

        @Test
        @DisplayName("the estimate is a band, is never zero, and never moves")
        void theEstimateIsStable() {
            GameSave save = world(seed(3));
            String home = homeServer(save);
            HostState bridge = bridgesOn(save, home).getFirst();
            discover(save, bridge);
            bridge.foothold = true;
            NetRules.connect(save, bridge.address, NetTestKit.T0);
            NetTestKit.sweep(save, SweepTier.DEEP, NetTestKit.T0);

            var node = save.knownNodes.stream()
                    .filter(n -> n.address.equals(bridge.address))
                    .findFirst()
                    .orElseThrow();
            assertThat(node.peerEstimate).isPositive();
            assertThat(node.peerAccuracyPercent).isEqualTo(Balance.NET_PEER_ESTIMATE_ACCURACY_PERCENT);

            // ⚠ Re-surveying is not a reroll — the estimate is a hash of the bridge. A number that
            // moved every time it was asked would make repetition the cheapest way to triangulate
            // the truth, and the accuracy figure beside it would then be a lie by omission.
            int was = node.peerEstimate;
            NetTestKit.sweep(save, SweepTier.DEEP, NetTestKit.T0.plusSeconds(600));
            assertThat(node.peerEstimate).isEqualTo(was);
        }
    }

    @Nested
    @DisplayName("a well-mapped server gives up its exits")
    class TheRevealShare {

        /**
         * ⚠ The measured fix. Over 400 worlds a first WIDE sweep from home found home's own bridge in
         * 75% of them; in the rest the exit was inaudible from where the player stood and re-sweeping
         * is deliberately not a reroll. Past the share, it is found from anywhere.
         */
        @Test
        @DisplayName("past the share, a WIDE sweep finds every bridge on the server it is standing on")
        void pastTheShare() {
            for (int i = 0; i < 40; i++) {
                GameSave save = world(seed(i));
                String home = homeServer(save);
                List<HostState> bridges = bridgesOn(save, home);
                if (bridges.isEmpty()) {
                    continue;
                }
                // Map the server past the threshold, without touching its bridges.
                for (HostState host : save.topology.hosts) {
                    if (home.equals(host.serverId) && !HostKind.BRIDGE.name().equals(host.kind)) {
                        host.discovered = true;
                    }
                }
                assertThat(NetRules.serverCompletion(save, home))
                        .as("world %d", i)
                        .isGreaterThanOrEqualTo(Balance.NET_BRIDGE_REVEAL_SHARE);

                NetTestKit.sweep(save, SweepTier.WIDE, NetTestKit.T0);

                assertThat(bridges).as("world %d", i).allMatch(bridge -> bridge.discovered);
            }
        }

        /**
         * ⚠ It does NOT override the tier gate. A base sweep still never sees a bridge, so the free
         * instrument is exactly as it was and this remains something the first upgrade is for.
         * Verified against a build with the tier check removed, which fails here.
         */
        @Test
        @DisplayName("a BASE sweep is unchanged, however well mapped the server is")
        void baseIsUnchanged() {
            for (int i = 0; i < 40; i++) {
                GameSave save = world(seed(i));
                String home = homeServer(save);
                List<HostState> bridges = bridgesOn(save, home);
                if (bridges.isEmpty()) {
                    continue;
                }
                for (HostState host : save.topology.hosts) {
                    if (home.equals(host.serverId) && !HostKind.BRIDGE.name().equals(host.kind)) {
                        host.discovered = true;
                    }
                }

                NetTestKit.sweep(save, SweepTier.BASE, NetTestKit.T0);

                assertThat(bridges).as("world %d", i).noneMatch(bridge -> bridge.discovered);
            }
        }

        /**
         * ⚠ The report stays arithmetically possible. The rule reaches past the hop ceiling, so a
         * bridge it reveals was never a candidate — and {@code SweepReport} THROWS on found > inRange,
         * because the player reads those two numbers as a fraction. This shipped broken once and the
         * record's own constructor is what caught it.
         */
        @Test
        @DisplayName("a revealed bridge is counted as considered, so found never exceeds inRange")
        void theReportStaysPossible() {
            for (int i = 0; i < 40; i++) {
                GameSave save = world(seed(i));
                String home = homeServer(save);
                for (HostState host : save.topology.hosts) {
                    if (home.equals(host.serverId) && !HostKind.BRIDGE.name().equals(host.kind)) {
                        host.discovered = true;
                    }
                }
                var report = NetTestKit.sweep(save, SweepTier.WIDE, NetTestKit.T0);
                assertThat(report.found()).as("world %d", i).isLessThanOrEqualTo(report.inRange());
            }
        }
    }

    @Nested
    @DisplayName("nothing answers until the crossing is open")
    class Reachability {

        @Test
        @DisplayName("the home server is always crossable; a foreign one is not until a bridge is opened")
        void homeIsFree() {
            GameSave save = world(seed(7));
            String home = homeServer(save);
            HostState bridge = bridgesOn(save, home).getFirst();
            HostState peer = peerOf(save, bridge);

            assertThat(NetRules.crossable(save, home)).isTrue();
            assertThat(NetRules.crossable(save, peer.serverId)).isFalse();

            // ⚠ A foothold is not a route. Breaching the bridge is half of it; the NET_MAN is the
            // other half, and neither alone opens anything.
            bridge.foothold = true;
            assertThat(NetRules.crossable(save, peer.serverId)).isFalse();

            NetRules.openCrossing(save, bridge.address, NetTestKit.T0);
            assertThat(NetRules.crossable(save, peer.serverId)).isTrue();
        }

        /**
         * ⚠ Reachability is a WALK, not a per-bridge flag. Asking only "does this host's own bridge
         * have a NET_MAN" would let a player who opened one crossing act on a server two crossings
         * out — the whole cost of the mechanic skipped by a graph the rule never walked.
         */
        @Test
        @DisplayName("it is transitive: a server two crossings out needs both opened")
        void transitive() {
            GameSave save = world(seed(11));
            String home = homeServer(save);
            HostState first = bridgesOn(save, home).getFirst();
            first.foothold = true;
            NetRules.openCrossing(save, first.address, NetTestKit.T0);
            String second = peerOf(save, first).serverId;

            List<HostState> onward = bridgesOn(save, second).stream()
                    .filter(bridge -> !peerOf(save, bridge).serverId.equals(home))
                    .toList();
            if (onward.isEmpty()) {
                return;
            }
            HostState next = onward.getFirst();
            String third = peerOf(save, next).serverId;
            if (third.equals(second) || third.equals(home)) {
                return;
            }

            assertThat(NetRules.crossable(save, second)).isTrue();
            assertThat(NetRules.crossable(save, third)).isFalse();

            next.foothold = true;
            NetRules.openCrossing(save, next.address, NetTestKit.T0);
            assertThat(NetRules.crossable(save, third)).isTrue();
        }

        @Test
        @DisplayName("connect refuses to move the vantage behind a shut crossing")
        void connectRefuses() {
            GameSave save = world(seed(13));
            String home = homeServer(save);
            HostState bridge = bridgesOn(save, home).getFirst();
            discover(save, bridge);
            bridge.foothold = true;
            HostState peer = peerOf(save, bridge);
            discover(save, peer);
            peer.foothold = true;

            assertThat(NetRules.connect(save, peer.address, NetTestKit.T0)).isFalse();
            assertThat(save.topology.vantageAddress).isNotEqualTo(peer.address);

            NetRules.openCrossing(save, bridge.address, NetTestKit.T0);
            assertThat(NetRules.connect(save, peer.address, NetTestKit.T0)).isTrue();
        }

        @Test
        @DisplayName("a port scan behind a shut crossing is refused before it costs anything")
        void portScanRefuses() {
            GameSave save = world(seed(17));
            String home = homeServer(save);
            HostState bridge = bridgesOn(save, home).getFirst();
            HostState peer = peerOf(save, bridge);
            discover(save, peer);
            long before = save.rig.allocations.size();

            var started = PortScanRules.begin(
                    save,
                    peer.address,
                    io.github.stoicswe.eyeandsickle.protocol.game.PortScanTarget.IDENTITY,
                    NetTestKit.T0);

            assertThat(started.succeeded()).isFalse();
            assertThat(started.refusal()).isEqualTo(PortScanRules.Refusal.CROSSING_SHUT);
            // ⚠ Before the reservation. A machine that answers nothing must not charge for silence.
            assertThat(save.rig.allocations).hasSize((int) before);
        }
    }

    @Nested
    @DisplayName("the NET_MAN upload")
    class Upload {

        private static void stock(GameSave save) {
            ItemState item = new ItemState();
            item.itemType = Catalogue.NETMAN_ID;
            item.displayName = "NET_MAN";
            item.acquiredAt = NetTestKit.T0;
            save.items.add(item);
        }

        @Test
        @DisplayName("is loud while it runs, spends the item when it lands, and opens the crossing")
        void theWholeUpload() {
            GameSave save = world(seed(19));
            String home = homeServer(save);
            HostState bridge = bridgesOn(save, home).getFirst();
            discover(save, bridge);
            bridge.foothold = true;
            stock(save);
            HostState peer = peerOf(save, bridge);

            Optional<TaskState> task = NetRules.uploadNetMan(save, bridge.address, NetTestKit.T0);
            assertThat(task).isPresent();
            // ⚠ Loud for the whole duration — that is the price. NoiseRules counts a task only while
            // it is running, which is what makes an installed NET_MAN silent forever after with no
            // decay curve to tune and no flag to clear.
            assertThat(task.get().noiseCycles).isEqualTo(Balance.NETMAN_UPLOAD_NOISE_CYCLES);
            // ⚠ Not yet. An upload that granted the crossing up front would make its duration, and
            // its noise, entirely optional.
            assertThat(bridge.netMan).isFalse();
            assertThat(save.items).anyMatch(item -> Catalogue.NETMAN_ID.equals(item.itemType));

            NetRules.completeNetMan(save, task.get(), task.get().endsAt);

            assertThat(bridge.netMan).isTrue();
            assertThat(save.items).noneMatch(item -> Catalogue.NETMAN_ID.equals(item.itemType));
            assertThat(NetRules.crossable(save, peer.serverId)).isTrue();
            assertThat(peer.discovered).as("and the far bridge is on the map").isTrue();
        }

        @Test
        @DisplayName("is refused without an item, on an unbreached bridge, and on anything not a bridge")
        void refusals() {
            GameSave save = world(seed(23));
            String home = homeServer(save);
            HostState bridge = bridgesOn(save, home).getFirst();

            // Not breached.
            stock(save);
            assertThat(NetRules.uploadNetMan(save, bridge.address, NetTestKit.T0))
                    .isEmpty();

            // Breached, but nothing to upload.
            bridge.foothold = true;
            save.items.removeIf(item -> Catalogue.NETMAN_ID.equals(item.itemType));
            assertThat(NetRules.uploadNetMan(save, bridge.address, NetTestKit.T0))
                    .isEmpty();

            // Not a bridge.
            stock(save);
            HostState ordinary = save.topology.hosts.stream()
                    .filter(host -> "TERMINAL".equals(host.kind))
                    .findFirst()
                    .orElseThrow();
            ordinary.foothold = true;
            assertThat(NetRules.uploadNetMan(save, ordinary.address, NetTestKit.T0))
                    .isEmpty();
        }

        /**
         * ⚠ The item is spent at settlement, so an upload whose item vanished in between must NOT
         * open the crossing. A path that opened one without spending one would be the only way in the
         * game to travel free.
         */
        @Test
        @DisplayName("an upload whose item has gone leaves the crossing shut")
        void noItemAtLanding() {
            GameSave save = world(seed(29));
            String home = homeServer(save);
            HostState bridge = bridgesOn(save, home).getFirst();
            bridge.foothold = true;
            stock(save);
            TaskState task =
                    NetRules.uploadNetMan(save, bridge.address, NetTestKit.T0).orElseThrow();

            save.items.removeIf(item -> Catalogue.NETMAN_ID.equals(item.itemType));
            assertThat(NetRules.completeNetMan(save, task, task.endsAt)).isFalse();
            assertThat(bridge.netMan).isFalse();
        }

        /** ⚠ The two ids must stay equal, or the item is buyable and unusable — both halves render. */
        @Test
        @DisplayName("the catalogue id and the id the rules consume are the same string")
        void oneId() {
            assertThat(NetRules.NETMAN_ITEM).isEqualTo(Catalogue.NETMAN_ID);
            assertThat(Catalogue.byId(Catalogue.NETMAN_ID)).isPresent();
        }
    }
}
