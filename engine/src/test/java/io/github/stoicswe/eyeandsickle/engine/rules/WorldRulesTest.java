package io.github.stoicswe.eyeandsickle.engine.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import io.github.stoicswe.eyeandsickle.engine.net.TopologyGenerator;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.WorldSettings;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The terms a world is started under.
 *
 * <p>Two properties carry this feature. The first is that <b>the defaults are the game as it
 * ships</b> — every field is read on a generation path, so a default that meant anything else would
 * change every character ever made. The second is that a chosen value is <b>honoured where it can be
 * and clamped where it cannot</b>, because a setting silently ignored is worse than one refused.
 */
class WorldRulesTest {

    private static final Instant T0 = Instant.parse("2026-08-09T12:00:00Z");

    private static GameSave world(long seed, WorldSettings settings) {
        GameSave save = GameEngine.newCharacter("operator", T0, settings);
        // ⚠ Re-generated from a chosen seed so the shape is comparable across cases. newCharacter
        // derives its own seed from the character id, which is a fresh UUID every call — two saves
        // built identically are NOT identical worlds, and comparing them is how a false regression
        // gets reported (ChainSyncTest records the same trap).
        save.rngSeed = seed;
        save.topology = null;
        TopologyGenerator.generate(save, T0);
        return save;
    }

    @Nested
    @DisplayName("a character created with the defaults")
    class Defaults {

        @Test
        @DisplayName("gets the game as it ships, field for field")
        void identity() {
            GameSave save = GameEngine.newCharacter("operator", T0);

            assertThat(WorldRules.of(save).customised()).isFalse();
            assertThat(WorldRules.serverCount(save, 6)).isEqualTo(6);
            assertThat(WorldRules.serverDepth(save, 9)).isEqualTo(9);
            assertThat(WorldRules.crossLinkChance(save)).isEqualTo(Balance.NET_SERVER_CHORD_CHANCE);
            assertThat(WorldRules.intrusionChance(save, 0.42d)).isEqualTo(0.42d);
            assertThat(save.ethecoinWei).isEqualTo(Balance.STARTING_ETHECOIN_WEI);
        }

        /**
         * ⚠ A save written before this existed deserialises {@code world} as null, and every hook
         * reads it on a generation or roll path. {@code of} repairs it in place for the reason
         * {@code Cheats.of} does — answering from a throwaway default reads correctly forever while
         * every write vanishes.
         */
        @Test
        @DisplayName("survives a save written before the field existed")
        void nullSettings() {
            GameSave save = GameEngine.newCharacter("operator", T0);
            save.world = null;

            assertThat(WorldRules.serverCount(save, 6)).isEqualTo(6);
            assertThat(save.world).isNotNull();
        }
    }

    @Nested
    @DisplayName("the size of the world")
    class Size {

        @Test
        @DisplayName("a chosen server count is exactly what gets built")
        void chosenCount() {
            for (int wanted : new int[] {WorldRules.MIN_SERVERS, 9, 13, WorldRules.MAX_SERVERS}) {
                WorldSettings settings = new WorldSettings();
                settings.serverCount = wanted;

                assertThat(world(31L, settings).topology.servers)
                        .as("asked for " + wanted + " servers")
                        .hasSize(wanted);
            }
        }

        @Test
        @DisplayName("a random count stays inside the band, which is now 5 to 18")
        void randomCount() {
            for (long seed = 1; seed <= 60; seed++) {
                assertThat(world(seed, new WorldSettings()).topology.servers.size())
                        .isBetween(Balance.NET_SERVERS_MIN, Balance.NET_SERVERS_MAX);
            }
            assertThat(Balance.NET_SERVERS_MAX).isEqualTo(18);
        }

        @Test
        @DisplayName("an absurd count is clamped rather than obeyed")
        void clamped() {
            WorldSettings settings = new WorldSettings();
            settings.serverCount = 9999;

            assertThat(world(31L, settings).topology.servers).hasSize(WorldRules.MAX_SERVERS);
        }

        /**
         * ⚠ THE GUARANTEE FROM §2.7 MUST SURVIVE EVERY SETTING. A world with no bridge on home is a
         * character whose network half ends at their own server, permanently — and the settings that
         * could plausibly cause it are the smallest server count and no cross-links at all.
         */
        @Test
        @DisplayName("even the smallest, least-connected world has a way out of home")
        void alwaysABridge() {
            WorldSettings settings = new WorldSettings();
            settings.serverCount = WorldRules.MIN_SERVERS;
            settings.crossLinkPercent = 0;

            for (long seed = 1; seed <= 60; seed++) {
                GameSave save = world(seed, settings);
                assertThat(save.topology.hosts)
                        .as("seed " + seed)
                        .anyMatch(host -> "BRIDGE".equals(host.kind)
                                && save.topology.homeServerId.equals(host.serverId));
            }
        }
    }

    @Nested
    @DisplayName("how deep each server runs")
    class Depth {

        /**
         * ⚠ A REQUEST, NOT A GUARANTEE, and the clamp is not a bug. A spine longer than
         * {@code NET_SPINE_BUDGET_SHARE} of a server's machines turns it into a corridor with one
         * fork at the end — the failure {@code design/18} §2.2 exists to prevent — so a deep setting
         * on a small server is clamped exactly as a deep roll already is.
         */
        @Test
        @DisplayName("a chosen depth is honoured up to what the server can afford")
        void chosenDepth() {
            assertThat(Balance.netNodeDepth(40, 0.0d, 11)).isEqualTo(11);
            assertThat(Balance.netNodeDepth(8, 0.0d, 13))
                    .as("a small server cannot afford a 13-deep spine")
                    .isLessThan(13)
                    .isGreaterThanOrEqualTo(Balance.NET_NODE_DEPTH_MIN);
        }

        @Test
        @DisplayName("zero means 'use the roll', which is bit-for-bit the old behaviour")
        void zeroIsTheRoll() {
            for (double u : new double[] {0.0d, 0.25d, 0.5d, 0.9d, 0.999d}) {
                assertThat(Balance.netNodeDepth(40, u, 0)).isEqualTo(Balance.netNodeDepth(40, u));
            }
        }
    }

    @Nested
    @DisplayName("cross-links between servers")
    class CrossLinks {

        /** ⚠ {@code 0} is a real answer — a pure tree — which is why the sentinel is {@code -1}. */
        @Test
        @DisplayName("none means a pure tree, and the world is still fully connected")
        void noneIsATree() {
            WorldSettings settings = new WorldSettings();
            settings.serverCount = 12;
            settings.crossLinkPercent = 0;
            GameSave save = world(31L, settings);

            // A spanning tree over n servers has exactly n-1 edges, so every server is reachable and
            // there is exactly one route to each. Counted as bridge PAIRS, one per server edge.
            assertThat(save.topology.servers).hasSize(12);
            assertThat(WorldRules.crossLinkChance(save)).isZero();
            assertThat(reachableServers(save)).isEqualTo(12);
        }

        @Test
        @DisplayName("dense adds routes without ever disconnecting anything")
        void denseStaysConnected() {
            WorldSettings settings = new WorldSettings();
            settings.serverCount = 12;
            settings.crossLinkPercent = 60;

            for (long seed = 1; seed <= 20; seed++) {
                assertThat(reachableServers(world(seed, settings)))
                        .as("seed " + seed)
                        .isEqualTo(12);
            }
        }

        /** How many servers are reachable from home over the bridge graph. */
        private static int reachableServers(GameSave save) {
            java.util.Map<String, java.util.Set<String>> peers = new java.util.HashMap<>();
            for (var host : save.topology.hosts) {
                if (!"BRIDGE".equals(host.kind) || host.bridgePeer.isEmpty()) {
                    continue;
                }
                var peer = save.topology.hosts.stream()
                        .filter(h -> h.address.equals(host.bridgePeer))
                        .findFirst()
                        .orElse(null);
                if (peer != null) {
                    peers.computeIfAbsent(host.serverId, k -> new java.util.HashSet<>())
                            .add(peer.serverId);
                    peers.computeIfAbsent(peer.serverId, k -> new java.util.HashSet<>())
                            .add(host.serverId);
                }
            }
            java.util.Set<String> seen = new java.util.HashSet<>();
            java.util.Deque<String> queue = new java.util.ArrayDeque<>();
            queue.add(save.topology.homeServerId);
            seen.add(save.topology.homeServerId);
            while (!queue.isEmpty()) {
                for (String next : peers.getOrDefault(queue.poll(), java.util.Set.of())) {
                    if (seen.add(next)) {
                        queue.add(next);
                    }
                }
            }
            return seen.size();
        }
    }

    @Nested
    @DisplayName("the two settings that are not world shape")
    class Rules {

        /**
         * ⚠ The world's scale and the developer override COMPOSE. Either winning outright would make
         * one of the two silently do nothing, and which one would depend on an ordering nothing on
         * screen explains.
         */
        @Test
        @DisplayName("the event scale composes with the developer override rather than being replaced")
        void composes() {
            GameSave save = GameEngine.newCharacter("operator", T0);
            WorldRules.of(save).eventChancePercent = 200;
            assertThat(WorldRules.intrusionChance(save, 0.2d)).isEqualTo(0.4d);

            Cheats.setEventChance(save, 50, T0);
            assertThat(WorldRules.intrusionChance(save, 0.2d))
                    .as("200% of the world's danger, held at half by the panel")
                    .isEqualTo(0.2d);

            // ⚠ Saturates at certainty rather than exceeding it — clamped once at the end, so two
            // scales multiplying past 1 do not wrap.
            WorldRules.of(save).eventChancePercent = WorldRules.MAX_EVENT_CHANCE_PERCENT;
            Cheats.setEventChance(save, Cheats.MAX_EVENT_CHANCE_PERCENT, T0);
            assertThat(WorldRules.intrusionChance(save, 0.9d)).isEqualTo(1.0d);
        }

        @Test
        @DisplayName("a starting balance is the whole balance, applied once at creation")
        void startingBalance() {
            WorldSettings settings = new WorldSettings();
            settings.startingEthecoinWei = Ethecoin.ofWholeEthecoin(2_500L).wei();

            GameSave save = GameEngine.newCharacter("operator", T0, settings);

            assertThat(save.ethecoinWei).isEqualTo(Ethecoin.ofWholeEthecoin(2_500L).wei());
            // ⚠ No ledger row: the money did not move between addresses, it was the terms the
            // character started under. Same reasoning as a granted balance, one tier up.
            assertThat(save.ledger).isEmpty();
        }
    }

    @Nested
    @DisplayName("settings are an input to generation and only that")
    class GenerationOnly {

        /**
         * ⚠ The generator runs once per character and refuses to run twice — the guard that stops a
         * world being re-rolled. So settings arriving after the world exists change nothing, and this
         * pins that rather than leaving it to be discovered by someone editing a save.
         */
        @Test
        @DisplayName("changing them afterwards does not re-shape a world that already exists")
        void inertAfterwards() {
            GameSave save = world(31L, new WorldSettings());
            int built = save.topology.servers.size();

            WorldRules.of(save).serverCount = WorldRules.MAX_SERVERS;
            TopologyGenerator.generate(save, T0);

            assertThat(save.topology.servers).hasSize(built);
        }
    }
}
