package io.github.stoicswe.eyeandsickle.engine.net;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.HostState;
import io.github.stoicswe.eyeandsickle.engine.state.NodeReportState;
import io.github.stoicswe.eyeandsickle.engine.state.ServerState;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * ⚠ TWO PLAYERS DO NOT LIVE IN THE SAME NAMED WORLD — asserted at the level the defect was visible
 * at, which is not the level it lived at.
 *
 * <h2>The failure this exists for</h2>
 *
 * Reported from a real character: <em>"the home server ends up always being the name
 * candid-noctilus"</em>. It was true of every character ever generated, because
 * {@code NpcNames.server} hashed {@code HostArchetypes.serverId(index)} — the literal string
 * {@code "srv-0"} — which holds an index and nothing about the world. Machines had it identically:
 * an address is {@code 10.<server>.<page>.<2 + index>}, positions all the way down.
 *
 * <p>⚠ <b>Every existing test passed, and would have gone on passing forever.</b>
 * {@code NpcNamesTest} asserts determinism, uniqueness within a world, pool membership, the
 * de-collision walk, and that servers never collide with machines — all of them true of a constant
 * mapping, because every one of them looks at ONE world. {@code TopologyGeneratorTest} sweeps ten
 * thousand seeds and compares worlds against each other for shape, never for names. A property that
 * needs two worlds to be visible cannot be caught by a suite that only ever builds one at a time,
 * however many seeds it builds it from.
 *
 * <p>This class is deliberately the small one that holds two worlds side by side.
 */
class WorldNamesTest {

    private static final Instant T0 = Instant.parse("2026-08-10T12:00:00Z");

    /** A generated world for one character, as {@code GameEngine.newCharacter} would build it. */
    private static GameSave world() {
        GameSave save = GameEngine.newCharacter("operator", T0);
        if (save.topology == null) {
            TopologyGenerator.generate(save, T0);
        }
        return save;
    }

    private static ServerState home(GameSave save) {
        return save.topology.servers.stream()
                .filter(server -> server.serverId.equals(save.topology.homeServerId))
                .findFirst()
                .orElseThrow();
    }

    @Nested
    @DisplayName("a name belongs to a world, not to a position in one")
    class PerWorld {

        /**
         * ⚠ The reported bug, in the form it was reported in. Twenty characters, twenty home servers.
         *
         * <p>Asserted as "not all the same" rather than "all different": two draws from an 878-name
         * pool collide about once in 878, so demanding twenty distinct names is a test that fails on
         * a pool edit for a reason unrelated to what it is about. The constant mapping this replaced
         * produces exactly ONE distinct name, so the assertion has the whole pool as headroom.
         */
        @Test
        @DisplayName("⚠ twenty characters do not all live on candid-noctilus")
        void homeServersDiffer() {
            Set<String> names = new HashSet<>();
            for (int i = 0; i < 20; i++) {
                names.add(home(world()).name);
            }
            assertThat(names).as("distinct home server names across 20 characters").hasSizeGreaterThan(10);
        }

        /**
         * The same property one level down, and the one with the sharper edge: host index 0 is
         * ALWAYS the gateway, so a name fixed to an address is a free and completely reliable "this
         * is the gateway" — the Passive Sniffer's product ({@code docs/design/07} §1), given away.
         * That is the leak this pool replaced {@code <server>-<NN>} to close.
         */
        @Test
        @DisplayName("⚠ the home gateway is not the same machine name in every world")
        void homeGatewaysDiffer() {
            Set<String> names = new HashSet<>();
            for (int i = 0; i < 20; i++) {
                GameSave save = world();
                names.add(save.topology.hosts.stream()
                        .filter(host -> HostKind.GATEWAY.name().equals(host.kind))
                        .filter(host -> host.serverId.equals(save.topology.homeServerId))
                        .findFirst()
                        .orElseThrow()
                        .label);
            }
            assertThat(names).as("distinct home gateway names across 20 characters").hasSizeGreaterThan(10);
        }

        /** ⚠ And the half a salt could break: one world must name itself the same way forever. */
        @Test
        @DisplayName("but one world's names survive a reload unchanged")
        void oneWorldIsStable() {
            GameSave save = world();
            List<String> servers = save.topology.servers.stream().map(s -> s.name).toList();
            List<String> hosts = save.topology.hosts.stream().map(h -> h.label).toList();

            // relabelLegacy is what runs on load. Twice, because idempotence is the claim.
            TopologyGenerator.relabelLegacy(save);
            TopologyGenerator.relabelLegacy(save);

            assertThat(save.topology.servers.stream().map(s -> s.name).toList()).isEqualTo(servers);
            assertThat(save.topology.hosts.stream().map(h -> h.label).toList()).isEqualTo(hosts);
        }
    }

    /**
     * ⚠ The migration half. A character generated before the salt carries names that are perfectly
     * valid members of both pools — so {@code looksLikeServer} and {@code looksGenerated} answer
     * "yes, one of mine" about them, and the guard those used to stand behind could never have
     * caught them. What tells a pre-salt name from a current one is only the derivation.
     */
    @Nested
    @DisplayName("a world named before the salt is corrected on load")
    class Relabelling {

        /** Rewrites a world's names the way the unsalted derivation did: hash the id alone. */
        private GameSave preSalt() {
            GameSave save = world();
            Set<String> taken = new HashSet<>();
            for (ServerState server : save.topology.servers) {
                server.name = NpcNames.server("", server.serverId, taken);
                taken.add(server.name);
            }
            for (HostState host : save.topology.hosts) {
                if (HostKind.SELF.name().equals(host.kind)) {
                    continue;
                }
                host.label = NpcNames.machine("", host.address, taken);
                taken.add(host.label);
            }
            return save;
        }

        @Test
        @DisplayName("⚠ every pre-salt world is renamed, and to this character's own names")
        void relabelsAWorldItCannotTellByInspection() {
            GameSave stale = preSalt();
            String before = home(stale).name;
            // The premise: nothing about the stale name is recognisably stale.
            assertThat(NpcNames.looksLikeServer(before)).isTrue();

            assertThat(TopologyGenerator.relabelLegacy(stale)).as("it reports a change").isTrue();
            assertThat(home(stale).name).isNotEqualTo(before);
            assertThat(home(stale).name)
                    .as("and lands on what generate would have produced for this character")
                    .isEqualTo(NpcNames.server(stale.characterId, stale.topology.homeServerId, new HashSet<>()));

            // ⚠ Idempotent by construction and not by a flag: the second pass has nothing to do.
            assertThat(TopologyGenerator.relabelLegacy(stale)).as("the second pass is a no-op").isFalse();
        }

        /**
         * ⚠ {@code NodeReportState.hostName} is WRITE-ONCE, so it defends whatever it holds against
         * every future scan. A pre-salt name pinned on a recon file passes {@code looksGenerated}
         * happily — so the guard that used to decide whether to correct one would have left the map
         * saying one name and RECON another about the same machine, permanently.
         */
        @Test
        @DisplayName("⚠ and the name pinned on a recon file is corrected with it")
        void correctsThePinnedName() {
            GameSave stale = preSalt();
            HostState host = stale.topology.hosts.stream()
                    .filter(h -> !HostKind.SELF.name().equals(h.kind))
                    .findFirst()
                    .orElseThrow();
            String pinned = host.label;

            NodeReportState report = new NodeReportState();
            report.address = host.address;
            report.hostName = pinned;
            stale.nodeReports.add(report);

            TopologyGenerator.relabelLegacy(stale);

            assertThat(host.label).as("the machine was renamed").isNotEqualTo(pinned);
            assertThat(report.hostName)
                    .as("and the file agrees with the map rather than defending the old name")
                    .isEqualTo(host.label);
        }

        /**
         * ⚠ The rig is skipped on {@code SELF} and never on its label. Without that guard the
         * player's own machine is renamed to something like {@code sultry-adleman}, which is the
         * single most confusing outcome available here.
         */
        @Test
        @DisplayName("the player's own machine is never renamed")
        void theRigKeepsItsName() {
            GameSave stale = preSalt();
            TopologyGenerator.relabelLegacy(stale);
            HostState rig = stale.topology.hosts.stream()
                    .filter(h -> HostKind.SELF.name().equals(h.kind))
                    .findFirst()
                    .orElseThrow();
            assertThat(rig.label).isEqualTo("localhost");
        }

        /** A rename must not hand two machines one name — the whole reason the walk exists. */
        @Test
        @DisplayName("nothing collides after a relabel")
        void namesStayUnique() {
            GameSave stale = preSalt();
            TopologyGenerator.relabelLegacy(stale);
            Set<String> seen = new HashSet<>();
            for (ServerState server : stale.topology.servers) {
                assertThat(seen.add(server.name)).as("server %s", server.serverId).isTrue();
            }
            for (HostState host : stale.topology.hosts) {
                if (HostKind.SELF.name().equals(host.kind)) {
                    continue;
                }
                assertThat(seen.add(host.label)).as("host %s", host.address).isTrue();
            }
        }

        /**
         * ⚠ A bridge's account is the character half of the server it reaches, so renaming servers
         * without renaming bridge accounts leaves every door in the world advertising a server that
         * no longer exists. {@code relabelLegacy} recomputes both in one pass; this is the assertion
         * that it does them in an order where the second sees the first.
         */
        @Test
        @DisplayName("⚠ bridge accounts follow the servers they were renamed for")
        void bridgeAccountsFollow() {
            GameSave stale = preSalt();
            TopologyGenerator.relabelLegacy(stale);

            java.util.Map<String, HostState> byAddress = new java.util.HashMap<>();
            stale.topology.hosts.forEach(h -> byAddress.put(h.address, h));

            int checked = 0;
            for (HostState host : stale.topology.hosts) {
                if (!HostKind.BRIDGE.name().equals(host.kind) || host.bridgePeer.isEmpty()) {
                    continue;
                }
                HostState peer = byAddress.get(host.bridgePeer);
                ServerState far = stale.topology.servers.stream()
                        .filter(s -> s.serverId.equals(peer.serverId))
                        .findFirst()
                        .orElseThrow();
                assertThat(host.operator)
                        .as("%s advertises %s", host.address, far.name)
                        .isEqualTo(NpcNames.bridgeOperator(far.name));
                checked++;
            }
            assertThat(checked).as("every world has at least one bridge").isPositive();
        }
    }

    /**
     * ⚠ Two characters may not share a UUID, so nothing here can rest on the id being unique by
     * luck. This is the assumption the salt is built on, stated where it can fail.
     */
    @Test
    @DisplayName("a character id is unique per character")
    void characterIdsAreUnique() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            ids.add(GameEngine.newCharacter("operator", T0).characterId);
        }
        assertThat(ids).hasSize(50);
        assertThat(UUID.fromString(ids.iterator().next())).isNotNull();
    }
}
