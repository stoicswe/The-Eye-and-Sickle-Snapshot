package io.github.stoicswe.eyeandsickle.engine.net;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.HostState;
import io.github.stoicswe.eyeandsickle.engine.state.ItemState;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import io.github.stoicswe.eyeandsickle.protocol.game.Sighting;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@code Sighting.linkEstimate}: roughly how many machines hang off this one, while any are unfound.
 *
 * <h2>What this feature is for</h2>
 *
 * The sweep ladder buys <b>sensitivity, not reach</b> ({@code design/07} §1) — and until this landed
 * there was no way to tell whether a better sweep taken from the same spot would find anything here
 * or nothing at all, so the two ethecoin upgrades were a purchase made blind. The tag answers "is
 * another sweep from here worth its cycles?" without answering "what would it find?".
 *
 * <h2>⚠ The rule it steps over, and the licence it steps over it on</h2>
 *
 * This is the only field on {@code Sighting} that is a claim about machines the player has
 * <b>not</b> discovered. {@code NetRules}' standing rule is that an undiscovered host does not
 * exist, and {@code design/18} §2.7c refuses to publish a server's completion metric on exactly
 * those grounds. The licence is the one {@code SweepReport.inRange} already stands on: it is the
 * <b>instrument's own sensitivity</b>, carrying no address, no type, no tier and no value. The three
 * properties that keep it there are each asserted below — it is suppressed once nothing is missing,
 * it is deliberately wrong, and it never counts across a crossing.
 */
@DisplayName("the link estimate")
class LinkEstimateTest {

    private static long seed(int i) {
        return i * 0x9E3779B97F4A7C15L + 0x2545F491L;
    }

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

    /** Marks every same-server neighbour of {@code address} discovered, the way a sweep would. */
    private static void discoverNeighbours(GameSave save, String address) {
        HostState host = hostAt(save, address);
        for (String neighbour : host.links) {
            HostState other = hostAt(save, neighbour);
            if (!other.serverId.equals(host.serverId)) {
                continue;
            }
            other.discovered = true;
            if (save.knownNodes.stream().noneMatch(node -> node.address.equals(other.address))) {
                var node = new io.github.stoicswe.eyeandsickle.engine.state.NodeState();
                node.address = other.address;
                node.serverId = other.serverId;
                save.knownNodes.add(node);
            }
        }
    }

    @Nested
    @DisplayName("when it is published")
    class WhenPublished {

        /**
         * ⚠ The whole feature in one assertion: after a first sweep, the rig itself has neighbours it
         * has not found — a sweep's yield is capped ({@code Balance.sweepYield}) and its sensitivity
         * is not total — so it wears a tag.
         */
        @Test
        @DisplayName("a machine with unfound neighbours carries an estimate")
        void unfoundNeighboursCarryAnEstimate() {
            for (int i = 0; i < 12; i++) {
                GameSave save = equipped(seed(i));
                NetTestKit.sweep(save, SweepTier.BASE, NetTestKit.T0);
                Optional<Sighting> tagged = NetRules.view(save).sightings().stream()
                        .filter(s -> s.linkEstimate() > 0)
                        .findFirst();
                if (tagged.isEmpty()) {
                    continue;
                }
                assertThat(tagged.get().linkEstimate()).isPositive();
                return;
            }
            throw new AssertionError("no seed in 12 left a machine with an unfound neighbour");
        }

        /**
         * ⚠ The other half, and the one the player actually reads: the tag GOING AWAY is the signal.
         * Asserted by discovering every same-server neighbour of one machine and watching its
         * estimate fall to -1, rather than by finding a machine that happens to be complete.
         */
        @Test
        @DisplayName("it is suppressed the moment every connection has been found")
        void completeMachinesSaySaySilent() {
            GameSave save = equipped(seed(3));
            NetTestKit.sweep(save, SweepTier.BASE, NetTestKit.T0);
            String rig = save.topology.playerAddress;
            // The rig starts with unfound neighbours: a first sweep is capped and is not exhaustive.
            assertThat(sightingAt(save, rig).linkEstimate()).isPositive();

            discoverNeighbours(save, rig);

            assertThat(sightingAt(save, rig).linkEstimate())
                    .as("nothing left to find, so nothing left to say")
                    .isEqualTo(-1);
        }

        /**
         * ⚠ It must never contradict the picture. The band is symmetric, so on a machine with five
         * links and four found it can land on three — and "about 3" beside four drawn lines reads as
         * a broken instrument rather than as an estimate.
         */
        @Test
        @DisplayName("it is never less than the number of connections already on screen")
        void itNeverUndercutsWhatIsDrawn() {
            // ⚠ Counted, and asserted positive at the end. Every assertion in this method is inside a
            // `continue` that skips machines with no estimate — so with the feature switched off the
            // loop body never runs and the test passes having checked nothing, which is the failure
            // mode this repo has hit three times. The counter is what makes it a real check.
            int examined = 0;
            for (int i = 0; i < 40; i++) {
                GameSave save = equipped(seed(i));
                NetTestKit.sweep(save, SweepTier.DEEP, NetTestKit.T0);
                for (Sighting sighting : NetRules.view(save).sightings()) {
                    if (sighting.linkEstimate() <= 0) {
                        continue;
                    }
                    examined++;
                    long drawn = NetRules.view(save).links().stream()
                            .filter(link -> link.fromAddress().equals(sighting.address())
                                    || link.toAddress().equals(sighting.address()))
                            .count();
                    assertThat(sighting.linkEstimate())
                            .as("estimate for %s against %d drawn links", sighting.address(), drawn)
                            .isGreaterThan((int) drawn);
                }
            }
            assertThat(examined)
                    .as("no estimate was published at all, so this checked nothing")
                    .isPositive();
        }
    }

    @Nested
    @DisplayName("what it must not leak")
    class Leaks {

        /**
         * ⚠ A CROSSING IS {@code peerEstimate}'S QUESTION AND IS BOUGHT SEPARATELY AND DEARLY — a
         * DEEP survey taken from a foothold on the bridge ({@code design/18} §2.7a). Counting the
         * crossing here would answer it for free and give two figures for one question.
         *
         * <p>Asserted on a bridge whose home-side neighbours are all discovered: its only remaining
         * link is the crossing, so a rule that counted it would publish an estimate and the correct
         * one publishes nothing.
         */
        @Test
        @DisplayName("a crossing is never counted, so a bridge with only its far side left says nothing")
        void crossingsAreNotCounted() {
            for (int i = 0; i < 12; i++) {
                GameSave save = equipped(seed(i));
                String home = hostAt(save, save.topology.playerAddress).serverId;
                Optional<HostState> found = save.topology.hosts.stream()
                        .filter(host -> HostKind.BRIDGE.name().equals(host.kind))
                        .filter(host -> home.equals(host.serverId))
                        .findFirst();
                if (found.isEmpty()) {
                    continue;
                }
                HostState bridge = found.get();
                bridge.discovered = true;
                var row = new io.github.stoicswe.eyeandsickle.engine.state.NodeState();
                row.address = bridge.address;
                row.serverId = bridge.serverId;
                save.knownNodes.add(row);
                discoverNeighbours(save, bridge.address);

                // ⚠ There has to be a crossing left to count, or this asserts nothing: a bridge whose
                // far side the player had already found would report -1 under either rule.
                assertThat(hostAt(save, bridge.bridgePeer).discovered)
                        .as("the far side must still be unfound for this to be a real check")
                        .isFalse();
                assertThat(sightingAt(save, bridge.address).linkEstimate())
                        .as("the far side of %s is the survey's product, not this one's", bridge.address)
                        .isEqualTo(-1);
                // ⚠ And the feature has to be ON, or the assertion above passes with it deleted.
                NetTestKit.sweep(save, SweepTier.BASE, NetTestKit.T0);
                assertThat(NetRules.view(save).sightings())
                        .as("a control: something in this world does carry an estimate")
                        .anyMatch(s -> s.linkEstimate() > 0);
                return;
            }
            throw new AssertionError("no seed in 12 produced a home bridge");
        }

        /**
         * ⚠ A HASH, NEVER A DRAW — the single line that keeps re-sweeping from being a way to
         * triangulate the truth. An estimate that moved when asked twice would let a player average
         * it down to the exact count, at which point the band is decoration and the figure is a free
         * map.
         */
        @Test
        @DisplayName("asking twice gives the same answer, and the answer is not the truth")
        void itIsAHashAndItIsWrong() {
            GameSave save = equipped(seed(7));
            NetTestKit.sweep(save, SweepTier.BASE, NetTestKit.T0);

            for (Sighting first : NetRules.view(save).sightings()) {
                assertThat(sightingAt(save, first.address()).linkEstimate())
                        .as("re-reading %s", first.address())
                        .isEqualTo(first.linkEstimate());
            }

            // ⚠ And it is genuinely banded rather than the true count with a mark after it. Asserted
            // over the whole spread rather than on one machine: a symmetric band lands ON the truth
            // for some inputs, so "this one differs" is not a property any single machine has.
            int differs = 0;
            int checked = 0;
            for (int i = 0; i < 60; i++) {
                GameSave world = equipped(seed(100 + i));
                NetTestKit.sweep(world, SweepTier.BASE, NetTestKit.T0);
                for (Sighting sighting : NetRules.view(world).sightings()) {
                    if (sighting.linkEstimate() <= 0) {
                        continue;
                    }
                    HostState host = hostAt(world, sighting.address());
                    long real = host.links.stream()
                            .filter(n -> hostAt(world, n).serverId.equals(host.serverId))
                            .count();
                    checked++;
                    if (sighting.linkEstimate() != real) {
                        differs++;
                    }
                }
            }
            assertThat(checked).isPositive();
            assertThat(differs)
                    .as("%d of %d estimates differ from the truth", differs, checked)
                    .isPositive();
        }

        /** The accuracy the band is derived from is the one published for it, not the bridge's. */
        @Test
        @DisplayName("the band is the link accuracy, and it is tighter than the bridge survey's")
        void theBandIsItsOwn() {
            assertThat(Balance.NET_LINK_ESTIMATE_ACCURACY_PERCENT)
                    .as("a link count runs 1-7; a 40% band on 2 is noise wearing a number")
                    .isGreaterThan(Balance.NET_PEER_ESTIMATE_ACCURACY_PERCENT);
            // Symmetric about the truth at the extremes of the unit interval, and never below 1.
            assertThat(Balance.netLinkEstimate(10, 0.0d)).isEqualTo(7);
            assertThat(Balance.netLinkEstimate(10, 1.0d)).isEqualTo(13);
            assertThat(Balance.netLinkEstimate(10, 0.5d)).isEqualTo(10);
            assertThat(Balance.netLinkEstimate(1, 0.0d)).isEqualTo(1);
        }
    }
}
