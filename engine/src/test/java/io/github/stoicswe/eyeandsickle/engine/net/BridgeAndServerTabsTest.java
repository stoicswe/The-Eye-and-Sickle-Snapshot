package io.github.stoicswe.eyeandsickle.engine.net;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.HostState;
import io.github.stoicswe.eyeandsickle.engine.state.NodeState;
import io.github.stoicswe.eyeandsickle.engine.state.ServerState;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import io.github.stoicswe.eyeandsickle.protocol.game.ServerRef;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Bridges: that every world has one, that it is named for where it goes, and when its far side
 * reaches the map's tab strip.
 *
 * <p>{@code docs/design/18} §2.7–2.8.
 */
class BridgeAndServerTabsTest {

    private static final Instant T0 = Instant.parse("2026-08-09T12:00:00Z");

    private static GameEngine game(Path dir) {
        return GameEngine.open(
                io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(dir.resolve("save.json")),
                "operator",
                Clock.fixed(T0, ZoneOffset.UTC));
    }

    /** A world built straight from a seed, without a store — for sweeping many of them cheaply. */
    private static GameSave world(long seed) {
        GameSave save = GameEngine.newCharacter("operator", T0);
        save.rngSeed = seed;
        save.topology = null;
        TopologyGenerator.generate(save, T0);
        return save;
    }

    private static List<HostState> bridgesOn(GameSave save, String serverId) {
        return save.topology.hosts.stream()
                .filter(host -> HostKind.BRIDGE.name().equals(host.kind))
                .filter(host -> serverId.equals(host.serverId))
                .toList();
    }

    private static ServerState serverOf(GameSave save, String serverId) {
        return save.topology.servers.stream()
                .filter(server -> serverId.equals(server.serverId))
                .findFirst()
                .orElseThrow();
    }

    @Nested
    @DisplayName("every world has a way out")
    class AlwaysABridge {

        /**
         * ⚠ This holds <b>by construction</b> rather than by a step that was added for it, and that
         * is worth pinning precisely because nothing in the generator announces it. Step 2 attaches
         * every server to one already placed, so server 1's parent is necessarily home; step 5 turns
         * every server-graph edge into a pair of bridges. With {@code NET_SERVERS_MIN} at 5 there is
         * always an edge incident to home, so there is always a bridge on it.
         *
         * <p>The consequence of losing it is not a crash: it is a character whose network half ends
         * at their own server, permanently, with nothing on screen to explain why. The two things
         * that would break it — a server count that could be 1, or a tree that did not root at home
         * — are both one edit away and neither would fail any other test.
         */
        @Test
        @DisplayName("the home server always has at least one bridge, over many seeds")
        void homeAlwaysHasABridge() {
            for (long seed = 1; seed <= 400; seed++) {
                GameSave save = world(seed);
                assertThat(bridgesOn(save, save.topology.homeServerId))
                        .as("seed " + seed + " left home with no way out")
                        .isNotEmpty();
            }
        }

        @Test
        @DisplayName("and one of them is within reach of the rig, so it can actually be found")
        void andItIsReachable() {
            for (long seed = 1; seed <= 200; seed++) {
                GameSave save = world(seed);
                var hops = TopologyGenerator.bfs(
                        save.topology.hosts.stream()
                                .collect(java.util.stream.Collectors.toMap(h -> h.address, h -> h, (a, b) -> a)),
                        save.topology.playerAddress);
                assertThat(bridgesOn(save, save.topology.homeServerId))
                        .as("seed " + seed)
                        .anyMatch(bridge -> hops.getOrDefault(bridge.address, Integer.MAX_VALUE) <= 2);
            }
        }
    }

    @Nested
    @DisplayName("a bridge is named for where it goes")
    class BridgeAccounts {

        /**
         * ⚠ The account is the CHARACTER half of the far server's own name — so a machine whose
         * prompt reads {@code muaddib@…} is a door to {@code <adjective>-muaddib}. Asserted against
         * the server's stored name rather than against a recomputed one, or the test would only be
         * checking that two copies of the same hash agree.
         */
        @Test
        @DisplayName("every bridge's account is the character half of the server on its far side")
        void namedForTheFarSide() {
            for (long seed = 1; seed <= 200; seed++) {
                GameSave save = world(seed);
                for (HostState host : save.topology.hosts) {
                    if (!HostKind.BRIDGE.name().equals(host.kind) || host.bridgePeer.isEmpty()) {
                        continue;
                    }
                    HostState peer = save.topology.hosts.stream()
                            .filter(h -> h.address.equals(host.bridgePeer))
                            .findFirst()
                            .orElseThrow();
                    String farSide = serverOf(save, peer.serverId).name;

                    assertThat(host.operator)
                            .as("seed " + seed + " bridge " + host.address + " → " + farSide)
                            .isEqualTo(NpcNames.bridgeOperator(farSide))
                            .isNotBlank();
                    assertThat(farSide).endsWith("-" + host.operator);
                }
            }
        }

        /**
         * ⚠ The account has to reach the places a player actually reads it — the shell prompt, the
         * home directory, the identity finding — and all three go through {@code VirtualFs.hostUser}.
         * A stored name that only the generator knew about would be a field nothing consumes.
         */
        @Test
        @DisplayName("and it is what the filesystem, the prompt and the scanner all report")
        void reachesTheSurfaces() {
            GameSave save = world(7L);
            HostState bridge = save.topology.hosts.stream()
                    .filter(host -> HostKind.BRIDGE.name().equals(host.kind))
                    .findFirst()
                    .orElseThrow();

            assertThat(io.github.stoicswe.eyeandsickle.engine.fs.VirtualFs.hostUser(bridge))
                    .isEqualTo(bridge.operator);
            // ⚠ And an ordinary machine is untouched: its account is still a pure function of its
            // address, from the OPERATORS pool, exactly as before.
            HostState ordinary = save.topology.hosts.stream()
                    .filter(host -> HostKind.TERMINAL.name().equals(host.kind))
                    .findFirst()
                    .orElseThrow();
            assertThat(ordinary.operator).isEmpty();
            assertThat(io.github.stoicswe.eyeandsickle.engine.fs.VirtualFs.hostUser(ordinary))
                    .isEqualTo(NpcNames.operator(ordinary.address));
        }

        /**
         * ⚠ The pools are disjoint ({@code NpcNamesTest.poolsDoNotOverlap}), so a bridge's account is
         * one no ordinary machine could ever have. That is not decoration — it is what makes the
         * account a reliable tell rather than a coincidence a player learns to distrust.
         */
        @Test
        @DisplayName("a bridge's account is never a name an ordinary machine could have")
        void neverAnOrdinaryName() {
            GameSave save = world(11L);
            for (HostState host : save.topology.hosts) {
                if (HostKind.BRIDGE.name().equals(host.kind) && !host.operator.isEmpty()) {
                    assertThat(NpcNames.operators()).doesNotContain(host.operator);
                    assertThat(NpcNames.characters()).contains(host.operator);
                }
            }
        }
    }

    @Nested
    @DisplayName("a server reaches the tab strip when its bridge is breached")
    class Tabs {

        private static void discover(GameSave save, HostState host) {
            host.discovered = true;
            NodeState node = new NodeState();
            node.address = host.address;
            node.serverId = host.serverId;
            save.knownNodes.add(node);
        }

        private static boolean strip(NetMap map, String serverId) {
            return map.knownServers().stream().map(ServerRef::serverId).anyMatch(serverId::equals);
        }

        @Test
        @DisplayName("finding the bridge is not enough — the far side stays off the strip")
        void discoveryIsNotEnough(@TempDir Path dir) {
            GameEngine game = game(dir);
            GameSave save = game.state();
            HostState bridge = bridgesOn(save, save.topology.homeServerId).getFirst();
            HostState peer = save.topology.hosts.stream()
                    .filter(h -> h.address.equals(bridge.bridgePeer))
                    .findFirst()
                    .orElseThrow();

            discover(save, bridge);
            bridge.identified = true;

            // ⚠ Identifying tells the player a server is there and what it is called — and the bridge
            // already says that, on the bridge, where it can be acted on. A tab is a place to GO, and
            // there is no way over there yet.
            assertThat(strip(game.net(), peer.serverId)).isFalse();
        }

        /**
         * ⚠ NARROWED 2026-08-09, and this test asserted the opposite until then.
         *
         * <p>Breaching a bridge used to be enough to list the far server, on the reasoning that
         * taking the bridge is "the moment the far side becomes somewhere you can go". That stopped
         * being true when a sweep lost the ability to reach across: breaching now tells the player
         * nothing whatever about what is behind the door, so a tab put up by the breach alone would
         * be a named, permanently empty server they had learned nothing about and could not act on.
         * What earns the tab is one of the two acts that genuinely look across.
         */
        @Test
        @DisplayName("breaching alone is NOT enough — the crossing has to be looked through or opened")
        void breachingAloneIsNotEnough(@TempDir Path dir) {
            GameEngine game = game(dir);
            GameSave save = game.state();
            HostState bridge = bridgesOn(save, save.topology.homeServerId).getFirst();
            HostState peer = save.topology.hosts.stream()
                    .filter(h -> h.address.equals(bridge.bridgePeer))
                    .findFirst()
                    .orElseThrow();
            discover(save, bridge);

            bridge.foothold = true;

            assertThat(strip(game.net(), peer.serverId)).isFalse();
        }

        @Test
        @DisplayName("a deep survey from the bridge lists it, and publishes the far bridge and nothing else")
        void surveyingListsIt(@TempDir Path dir) {
            GameEngine game = game(dir);
            GameSave save = game.state();
            HostState bridge = bridgesOn(save, save.topology.homeServerId).getFirst();
            HostState peer = save.topology.hosts.stream()
                    .filter(h -> h.address.equals(bridge.bridgePeer))
                    .findFirst()
                    .orElseThrow();
            discover(save, bridge);
            bridge.foothold = true;
            bridge.surveyed = true;

            NetMap map = game.net();
            assertThat(strip(map, peer.serverId)).isTrue();
            // ⚠ And what is on that tab is the far BRIDGE alone. A survey sells a door and a rough
            // size; the machines behind it are still bought by standing over there and sweeping.
            assertThat(map.sightings().stream()
                            .filter(s -> peer.serverId.equals(s.serverId()))
                            .map(io.github.stoicswe.eyeandsickle.protocol.game.Sighting::address)
                            .toList())
                    .containsExactlyInAnyOrderElementsOf(
                            peer.discovered ? java.util.List.of(peer.address) : java.util.List.of());
        }

        @Test
        @DisplayName("opening the crossing lists it, and publishes the far bridge")
        void openingListsIt(@TempDir Path dir) {
            GameEngine game = game(dir);
            GameSave save = game.state();
            HostState bridge = bridgesOn(save, save.topology.homeServerId).getFirst();
            HostState peer = save.topology.hosts.stream()
                    .filter(h -> h.address.equals(bridge.bridgePeer))
                    .findFirst()
                    .orElseThrow();
            discover(save, bridge);
            bridge.foothold = true;

            NetRules.openCrossing(save, bridge.address, NetTestKit.T0);

            NetMap map = game.net();
            assertThat(strip(map, peer.serverId)).isTrue();
            // ⚠ The far bridge, and only it. Without this the crossing opens onto nothing the player
            // can stand on, so the tab is a named empty server — the exact failure the narrowing
            // above exists to prevent, reintroduced from the other side.
            assertThat(map.sightings())
                    .anyMatch(s -> s.address().equals(peer.address));
            assertThat(map.sightings().stream()
                            .filter(s -> peer.serverId.equals(s.serverId()))
                            .count())
                    .isEqualTo(1);
        }

        /**
         * ⚠ The discovery half of "discovered AND breached" still binds. A hand-edited save, or any
         * future rule that set a foothold without a sighting, must not publish a server behind a
         * bridge the player has never found.
         */
        @Test
        @DisplayName("a breached bridge nobody has found publishes nothing")
        void undiscoveredBridgePublishesNothing(@TempDir Path dir) {
            GameEngine game = game(dir);
            GameSave save = game.state();
            HostState bridge = bridgesOn(save, save.topology.homeServerId).getFirst();
            HostState peer = save.topology.hosts.stream()
                    .filter(h -> h.address.equals(bridge.bridgePeer))
                    .findFirst()
                    .orElseThrow();

            bridge.foothold = true;

            assertThat(strip(game.net(), peer.serverId)).isFalse();
        }

        @Test
        @DisplayName("the developer reveal opens every bridge, so every server is on the strip")
        void revealOpensEverything(@TempDir Path dir) {
            GameEngine game = game(dir);
            GameSave save = game.state();
            assertThat(game.net().knownServers()).hasSize(1);

            io.github.stoicswe.eyeandsickle.engine.rules.Cheats.revealNetwork(save, T0);

            assertThat(game.net().knownServers()).hasSize(save.topology.servers.size());
        }

        /**
         * ⚠ Revealing AFTER exploring must not do less than revealing first. The foothold is granted
         * outside the discovery guard for exactly this: a bridge already found by an ordinary sweep is
         * skipped by "already discovered", and if the breach rode inside that branch the cheat would
         * quietly do less the more of the game the player had played.
         */
        @Test
        @DisplayName("and it opens bridges that were already discovered")
        void revealOpensAlreadyFoundBridges(@TempDir Path dir) {
            GameEngine game = game(dir);
            GameSave save = game.state();
            HostState bridge = bridgesOn(save, save.topology.homeServerId).getFirst();
            discover(save, bridge);
            assertThat(bridge.foothold).isFalse();

            io.github.stoicswe.eyeandsickle.engine.rules.Cheats.revealNetwork(save, T0);

            assertThat(bridge.foothold).isTrue();
            assertThat(game.net().knownServers()).hasSize(save.topology.servers.size());
        }
    }
}
