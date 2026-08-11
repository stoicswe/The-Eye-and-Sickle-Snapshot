package io.github.stoicswe.eyeandsickle.engine.net;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.breach.Targets;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.HostState;
import io.github.stoicswe.eyeandsickle.engine.state.ItemState;
import io.github.stoicswe.eyeandsickle.engine.state.NodeState;
import io.github.stoicswe.eyeandsickle.engine.state.ResolutionState;
import io.github.stoicswe.eyeandsickle.protocol.game.BreachTarget;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import io.github.stoicswe.eyeandsickle.protocol.game.Sighting;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What establishes a machine's <b>type</b>, and what publishes the server behind a bridge.
 *
 * <h2>⚠ THIS WHOLE FILE EXISTS BECAUSE NOTHING TYPED ANYTHING</h2>
 *
 * Until 2026-08-09 {@code NodeState.kind} was assigned in exactly one place in the codebase — to
 * {@code "UNKNOWN"}, in {@code NetRules.nodeFor} — and {@code HostState.identified} only for the
 * player's own rig and by the developer reveal. The 15 EC Passive Sniffer that nine comments in that
 * package defer to <b>is not in {@code Catalogue}</b>, and no {@code PortScanTarget} rung sells a
 * type either. So every box on the network map read {@code ----} forever and the map's whole type
 * vocabulary — the bridge glyph, the woven bridge frame, the drawbridge markers, the {@code ··} stub,
 * {@code Targets.role} — was unreachable outside the cheat.
 *
 * <p>Measured on a real 575-host save before the fix: one host identified (the rig), all nineteen
 * discovered nodes {@code UNKNOWN}. Nothing failed, every screen rendered, and the whole suite was
 * green — because the only test that looked asserted the type stayed {@code UNKNOWN}, which was true
 * for a reason nobody had checked.
 *
 * <h2>The two rules</h2>
 *
 * <ol>
 *   <li><b>A DEEP sweep types what it picks up, and a foothold types the machine.</b> BASE and WIDE
 *       still sell existence and adjacency and nothing else.
 *   <li><b>The server behind a bridge is published only by a survey across it or a NET_MAN on it</b>
 *       — the same condition that puts its tab on the strip, deliberately, so the name of the far
 *       side and a place to go to it arrive together.
 * </ol>
 */
@DisplayName("identification")
class IdentificationTest {

    private static long seed(int i) {
        return i * 0x9E3779B97F4A7C15L + 0x2545F491L;
    }

    /** A world with every sweep tier and the Topology Mapper, so the ceiling is two hops. */
    private static GameSave equipped(long seed) {
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

    private static NodeState nodeAt(GameSave save, String address) {
        return save.knownNodes.stream()
                .filter(node -> node.address.equals(address))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no knownNodes row for " + address));
    }

    private static Sighting sightingAt(GameSave save, String address) {
        return NetRules.view(save).sightings().stream()
                .filter(s -> s.address().equals(address))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no sighting for " + address));
    }

    private static HostState hostAt(GameSave save, String address) {
        return save.topology.hosts.stream()
                .filter(host -> host.address.equals(address))
                .findFirst()
                .orElseThrow();
    }

    /** Breaches a machine the way the game does: a resolution, then the reconcile that reads it. */
    private static void breach(GameSave save, String address) {
        ResolutionState resolution = new ResolutionState();
        resolution.targetId = "node:" + address;
        resolution.outcome = "BREACHED";
        resolution.at = NetTestKit.T0;
        save.resolutions.add(resolution);
        NetRules.reconcileFootholds(save, NetTestKit.T0);
    }

    private static Optional<HostState> homeBridge(GameSave save) {
        String home = hostAt(save, save.topology.playerAddress).serverId;
        return save.topology.hosts.stream()
                .filter(host -> HostKind.BRIDGE.name().equals(host.kind))
                .filter(host -> home.equals(host.serverId))
                .findFirst();
    }

    @Nested
    @DisplayName("what establishes a type")
    class WhatTypes {

        /**
         * ⚠ The assertion that could not have been made before the fix, in the plainest form: after a
         * DEEP sweep, <b>something</b> on the map is typed. The old code could not satisfy this at any
         * seed, with any tool, by any sequence of actions short of the developer reveal.
         */
        @Test
        @DisplayName("a DEEP sweep types every machine it picks up")
        void aDeepSweepTypes() {
            for (int i = 0; i < 12; i++) {
                GameSave save = equipped(seed(i));
                var report = NetTestKit.sweep(save, SweepTier.DEEP, NetTestKit.T0);
                if (report.found() == 0) {
                    continue;
                }
                for (String address : report.foundAddresses()) {
                    assertThat(nodeAt(save, address).kind)
                            .as("knownNodes row for %s after a DEEP sweep", address)
                            .isNotEqualTo(HostKind.UNKNOWN.name());
                    assertThat(sightingAt(save, address).kind())
                            .as("sighting for %s after a DEEP sweep", address)
                            .isNotEqualTo(HostKind.UNKNOWN);
                }
                // ⚠ And the breach window agrees, which is the half `HostState.identified` could not
                // carry: Targets.role reads NodeState.kind, so writing the other field typed a machine
                // on the map and left it blank here.
                for (BreachTarget target : Targets.available(save)) {
                    if (!target.minerCrack() && report.foundAddresses().contains(target.address())) {
                        assertThat(target.role())
                                .as("breach target role for %s", target.address())
                                .isNotEmpty();
                    }
                }
                return;
            }
            throw new AssertionError("no seed in 12 produced a DEEP sweep that found anything");
        }

        /**
         * The other half, and the one that keeps the tier ladder meaning something. {@code
         * NetRulesTest.aBaseSweepNeverNamesAType} says the same from the read model's side; this says
         * it against the tier that is one rung below the one that does type.
         */
        @Test
        @DisplayName("a WIDE sweep still sells existence and adjacency and nothing else")
        void aWideSweepTypesNothing() {
            for (int i = 0; i < 12; i++) {
                GameSave save = equipped(seed(i));
                var report = NetTestKit.sweep(save, SweepTier.WIDE, NetTestKit.T0);
                if (report.found() == 0) {
                    continue;
                }
                for (String address : report.foundAddresses()) {
                    assertThat(nodeAt(save, address).kind).isEqualTo(HostKind.UNKNOWN.name());
                    assertThat(sightingAt(save, address).kind()).isEqualTo(HostKind.UNKNOWN);
                }
                return;
            }
            throw new AssertionError("no seed in 12 produced a WIDE sweep that found anything");
        }

        @Test
        @DisplayName("a foothold types the machine, whatever found it")
        void aFootholdTypes() {
            GameSave save = equipped(seed(3));
            var report = NetTestKit.sweep(save, SweepTier.WIDE, NetTestKit.T0);
            assertThat(report.found()).isPositive();
            String address = report.foundAddresses().getFirst();
            // A WIDE sweep found it, so it is anonymous — that is the previous test's claim.
            assertThat(sightingAt(save, address).kind()).isEqualTo(HostKind.UNKNOWN);

            breach(save, address);

            assertThat(sightingAt(save, address).kind()).isNotEqualTo(HostKind.UNKNOWN);
        }

        /**
         * ⚠ The retro-fix, and the reason this sits outside {@code reconcileFootholds}' foothold
         * guard. Every existing character breached machines under a build where nothing could type
         * them; that method runs on every resume, so they are typed on the next load rather than
         * staying permanently anonymous. Reproduced here by breaching and then wiping the type back
         * to what an old save carries.
         */
        @Test
        @DisplayName("a machine breached before this existed is typed on the next reconcile")
        void anOldFootholdIsTypedOnLoad() {
            GameSave save = equipped(seed(4));
            var report = NetTestKit.sweep(save, SweepTier.WIDE, NetTestKit.T0);
            assertThat(report.found()).isPositive();
            String address = report.foundAddresses().getFirst();
            breach(save, address);
            nodeAt(save, address).kind = HostKind.UNKNOWN.name(); // what an old save looks like

            NetRules.reconcileFootholds(save, NetTestKit.T0);

            assertThat(nodeAt(save, address).kind).isNotEqualTo(HostKind.UNKNOWN.name());
        }
    }

    @Nested
    @DisplayName("what publishes the server behind a bridge")
    class WhatPublishesTheFarSide {

        /**
         * ⚠ THE RULE THAT HAD TO GET STRICTER WHEN TYPING GOT CHEAPER.
         *
         * <p>The far server used to be published off {@code kind == BRIDGE} alone. That was already
         * the wrong rule by {@code design/18} §2.8 — which reversed "breaching is enough to list the
         * far server" within a day of shipping it — and it became live the moment a foothold started
         * typing a machine: without this narrowing, breaking into a bridge would name the network
         * behind it for free.
         */
        @Test
        @DisplayName("breaching a bridge types it and does NOT name what is on the other side")
        void aBreachedBridgeNamesNothing() {
            for (int i = 0; i < 12; i++) {
                GameSave save = equipped(seed(i));
                Optional<HostState> found = homeBridge(save);
                if (found.isEmpty()) {
                    continue;
                }
                HostState bridge = found.get();
                bridge.discovered = true;
                save.knownNodes.add(bridgeRow(bridge));
                breach(save, bridge.address);

                Sighting sighting = sightingAt(save, bridge.address);
                // Typed — the player is standing in the doorway.
                assertThat(sighting.kind()).isEqualTo(HostKind.BRIDGE);
                // ...and told nothing about where it goes.
                assertThat(sighting.bridgePeerServerName()).isEmpty();
                // ⚠ And no tab, which is the same rule read from the other end. Two conditions here
                // would be two screens disagreeing about whether the player has been told.
                assertThat(NetRules.view(save).knownServers())
                        .as("a breached-but-unsurveyed bridge puts no server on the strip")
                        .hasSize(1);
                return;
            }
            throw new AssertionError("no seed in 12 produced a home bridge");
        }

        @Test
        @DisplayName("opening the crossing names the far side, and puts it on the strip")
        void anOpenedCrossingNames() {
            for (int i = 0; i < 12; i++) {
                GameSave save = equipped(seed(i));
                Optional<HostState> found = homeBridge(save);
                if (found.isEmpty()) {
                    continue;
                }
                HostState bridge = found.get();
                bridge.discovered = true;
                save.knownNodes.add(bridgeRow(bridge));
                breach(save, bridge.address);
                NetRules.openCrossing(save, bridge.address, NetTestKit.T0);

                assertThat(sightingAt(save, bridge.address).bridgePeerServerName())
                        .as("a bridge with a NET_MAN running names its far side")
                        .isNotEmpty();
                assertThat(NetRules.view(save).knownServers()).hasSize(2);
                return;
            }
            throw new AssertionError("no seed in 12 produced a home bridge");
        }

        private static NodeState bridgeRow(HostState bridge) {
            NodeState node = new NodeState();
            node.address = bridge.address;
            node.serverId = bridge.serverId;
            return node;
        }
    }
}
