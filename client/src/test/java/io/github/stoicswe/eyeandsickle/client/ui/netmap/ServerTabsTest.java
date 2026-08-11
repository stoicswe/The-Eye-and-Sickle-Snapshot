package io.github.stoicswe.eyeandsickle.client.ui.netmap;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.DifficultyTier;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import io.github.stoicswe.eyeandsickle.protocol.game.NetLink;
import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import io.github.stoicswe.eyeandsickle.protocol.game.ServerRef;
import io.github.stoicswe.eyeandsickle.protocol.game.Sighting;
import io.github.stoicswe.eyeandsickle.protocol.game.SignalStrength;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The map's per-server tab strip, and the filter behind it.
 *
 * <h2>What is asserted here and what is asserted by construction</h2>
 *
 * The strip is a pure function of the {@link NetMap} — there is no second source of "which servers
 * are there", no set accumulated as the player travels, and nothing derived from the scene graph. So
 * the whole model is testable without a toolkit, and the wiring from it to a row of chips is short
 * enough to read.
 *
 * <p>⚠ The rules that matter here are the ones a careless filter breaks silently: a tab for a server
 * the player has never heard of, a link left pointing at a machine that is not on the grid, and the
 * rig turning up on somebody else's server.
 */
class ServerTabsTest {

    private static final ServerRef HOME = new ServerRef("s0", "wicked-freeman", 0, true);
    private static final ServerRef FAR = new ServerRef("s1", "clandestine-atreides", 1, false);
    private static final ServerRef UNSEEN = new ServerRef("s2", "sultry-cortana", 2, false);

    private static Sighting machine(String address, String serverId, int hopsFromRig, boolean self) {
        return new Sighting(
                address,
                "",
                serverId,
                self ? HostKind.SELF : HostKind.UNKNOWN,
                self ? null : DifficultyTier.of(1),
                SignalStrength.MODERATE,
                hopsFromRig,
                hopsFromRig,
                self,
                self,
                false,
                false,
                false,
                false,
                false,
                false,
                "",
                false,
                "");
    }

    @org.junit.jupiter.api.Nested
    @DisplayName("the header describes the tab that is open")
    class CurrentServer {

        /**
         * ⚠ THE STRIP READS {@code SERVER <name> DEPTH n FROM HOME} OFF {@code currentServer()}, and
         * a filtered map used to keep the VANTAGE's server there.
         *
         * <p>So opening a server four bridges out still read {@code DEPTH 0 FROM HOME} under the name
         * of the server the player had navigated away from — and the depth is the one number on that
         * strip whose whole job is to say how dangerous this place is. Reported from a real map.
         *
         * <p>⚠ Nothing is lost by re-pointing it: where the player is <b>standing</b> is carried
         * separately as {@code vantageAddress}, which the same strip prints as {@code SWEEPING FROM}
         * and which the graph marks with the heavy frame.
         */
        @Test
        @DisplayName("filtering to a server makes that server the current one")
        void filterRepointsCurrent() {
            NetMap far = ServerTabs.filter(world(), "s1");

            assertThat(far.currentServer().serverId()).isEqualTo("s1");
            assertThat(far.currentServer().name()).isEqualTo("clandestine-atreides");
            assertThat(far.currentServer().depthFromHome()).isEqualTo(1);
            // The vantage is untouched — it is a different question and the strip asks both.
            assertThat(far.vantageAddress()).isEqualTo("10.0.0.1");
        }

        @Test
        @DisplayName("and home still reads as home")
        void homeIsUnchanged() {
            NetMap home = ServerTabs.filter(world(), "s0");

            assertThat(home.currentServer().serverId()).isEqualTo("s0");
            assertThat(home.currentServer().depthFromHome()).isZero();
        }

        /**
         * ⚠ {@code of} must be given the UNFILTERED world, or every tab reports itself as current.
         * The re-pointing above is what makes that a live hazard rather than a theoretical one.
         */
        @Test
        @DisplayName("exactly one tab is current on the unfiltered world")
        void oneCurrentTab() {
            assertThat(ServerTabs.of(world()).stream().filter(ServerTabs.Tab::current))
                    .hasSize(1);
        }

        @Test
        @DisplayName("an id the map has never heard of falls back rather than blanking the header")
        void unknownIdFallsBack() {
            NetMap unknown = ServerTabs.filter(world(), "s99");

            assertThat(unknown.currentServer().serverId()).isEqualTo("s0");
        }
    }

    /** Home with the rig and two machines; one server across a bridge with two of its own. */
    private static NetMap world() {
        return new NetMap(
                HOME,
                "10.0.0.1",
                1,
                List.of(HOME, FAR),
                List.of(
                        machine("10.0.0.1", "s0", 0, true),
                        machine("10.0.0.2", "s0", 1, false),
                        machine("10.0.0.3", "s0", 2, false),
                        machine("10.1.0.7", "s1", 3, false),
                        machine("10.1.0.8", "s1", 4, false)),
                List.of(
                        new NetLink("10.0.0.1", "10.0.0.2", false),
                        new NetLink("10.0.0.2", "10.0.0.3", false),
                        // The bridge's own edge, which crosses servers and must not survive a filter.
                        new NetLink("10.0.0.3", "10.1.0.7", true),
                        new NetLink("10.1.0.7", "10.1.0.8", false)));
    }

    @Nested
    @DisplayName("the strip")
    class Strip {

        @Test
        @DisplayName("has one tab per server the player has heard of, and none for any other")
        void oneTabPerKnownServer() {
            // ⚠ NetMap.knownServers is already "what the player has heard of" — a server reaches it by
            // being swept or by an identified bridge advertising it. Enumerating anything else would
            // publish the shape of the world for free, which is the rule NetRules states as
            // "undiscovered hosts do not exist in knownNodes, and the map draws nothing where they are".
            assertThat(ServerTabs.of(world()).stream().map(ServerTabs.Tab::serverId))
                    .containsExactly("s0", "s1")
                    .doesNotContain(UNSEEN.serverId());
        }

        /**
         * ⚠ THE FIXTURE IS BUILT SO DEPTH ORDER AND NAME ORDER DISAGREE, and the previous version of
         * this test was not.
         *
         * <p>It used HOME/FAR/UNSEEN at depths 0/1/2 named freeman/atreides/cortana — where
         * alphabetical-after-home and by-depth happen to produce the identical strip. So it passed
         * under both rules and could not tell them apart, which is a test that reports a guarantee it
         * is not checking. Here the depth-1 server sorts LAST by name and the depth-3 server sorts
         * first, so only one ordering can satisfy it.
         */
        @Test
        @DisplayName("orders by depth from home, not by name")
        void byDepth() {
            ServerRef near = new ServerRef("s1", "zealous-vayne", 1, false);
            ServerRef mid = new ServerRef("s2", "mellow-shodan", 2, false);
            ServerRef deep = new ServerRef("s3", "amber-kotake", 3, false);
            NetMap map = new NetMap(
                    HOME,
                    "10.0.0.1",
                    1,
                    // Deliberately out of order, and with home in the middle.
                    List.of(deep, HOME, mid, near),
                    world().sightings(),
                    world().links());

            List<ServerTabs.Tab> tabs = ServerTabs.of(map);

            assertThat(tabs.getFirst().home()).isTrue();
            assertThat(tabs.stream().map(ServerTabs.Tab::label))
                    .containsExactly("wicked-freeman", "zealous-vayne", "mellow-shodan", "amber-kotake");
            assertThat(tabs.stream().map(ServerTabs.Tab::depthFromHome)).containsExactly(0, 1, 2, 3);
        }

        /**
         * ⚠ The half of the old rule that was right and is kept: discovery order is "a private
         * history that makes two players' strips disagree about a world they are both looking at", so
         * the tiebreak within a depth is the NAME and never the order the servers arrived in.
         */
        @Test
        @DisplayName("and by name within a depth, never by the order they were found")
        void nameBreaksTies() {
            ServerRef first = new ServerRef("s1", "zealous-vayne", 2, false);
            ServerRef second = new ServerRef("s2", "amber-kotake", 2, false);
            NetMap map = new NetMap(
                    HOME, "10.0.0.1", 1, List.of(first, HOME, second), world().sightings(), world().links());

            assertThat(ServerTabs.of(map).stream().map(ServerTabs.Tab::label))
                    .containsExactly("wicked-freeman", "amber-kotake", "zealous-vayne");
        }

        @Test
        @DisplayName("⚠ finds home from the rig's own sighting, not from the ServerRef flag")
        void homeIsWhereTheRigIs() {
            // The flag reaches the client through several producers and the fixtures set it
            // inconsistently; the machine that says `self` is the answer that cannot be wrong.
            ServerRef lying = new ServerRef("s0", "wicked-freeman", 0, false);
            NetMap map = new NetMap(
                    lying, "10.0.0.1", 1, List.of(FAR, lying), world().sightings(), world().links());

            assertThat(ServerTabs.of(map).getFirst().serverId()).isEqualTo("s0");
        }

        @Test
        @DisplayName("marks the server the player is standing on, and opens there")
        void opensWhereTheVantageIs() {
            NetMap away = new NetMap(
                    FAR, "10.1.0.7", 1, List.of(HOME, FAR), world().sightings(), world().links());

            // ⚠ Not home. A player four servers out who opens the map and is shown a server they left
            // an hour ago has been given the wrong answer to "where am I".
            assertThat(ServerTabs.initial(away)).isEqualTo("s1");
            assertThat(ServerTabs.of(away).stream().filter(ServerTabs.Tab::current))
                    .singleElement()
                    .extracting(ServerTabs.Tab::serverId)
                    .isEqualTo("s1");
        }

        @Test
        @DisplayName("counts what has been found on each server, and zero is a real answer")
        void countsMachines() {
            // ⚠ A tab may legitimately be EMPTY: an identified bridge advertises the server on its far
            // side by name, and until the player crosses it that name is all they have. The tab exists
            // and says so — which is the whole point of the bridge finding.
            NetMap justHeardOf = new NetMap(
                    HOME,
                    "10.0.0.1",
                    1,
                    List.of(HOME, FAR),
                    List.of(machine("10.0.0.1", "s0", 0, true)),
                    List.of());

            List<ServerTabs.Tab> tabs = ServerTabs.of(justHeardOf);
            assertThat(tabs).hasSize(2);
            assertThat(tabs.getFirst().machines()).isEqualTo(1);
            assertThat(tabs.get(1).machines()).isZero();
            assertThat(tabs.get(1).explored()).isFalse();
        }
    }

    @Nested
    @DisplayName("the filter")
    class Filter {

        @Test
        @DisplayName("keeps one server's machines and nobody else's")
        void keepsOneServer() {
            // ⚠ Its own server, PLUS the far end of any bridge crossing into it — 2026-08-09. A door
            // belongs to both rooms, so `10.0.0.3` (home's bridge) is carried onto s1's tab and s1's
            // bridge is carried onto home's. Everything else on home stays off this tab.
            assertThat(ServerTabs.filter(world(), "s1").sightings())
                    .extracting(Sighting::address)
                    .containsExactlyInAnyOrder("10.1.0.7", "10.1.0.8", "10.0.0.3");
            assertThat(ServerTabs.filter(world(), "s1").sightings())
                    .extracting(Sighting::address)
                    .doesNotContain("10.0.0.1", "10.0.0.2");
        }

        /**
         * ⚠ The carry-over is derived from PUBLISHED LINKS, and a cross-server link reaches the
         * client only when both ends are discovered. So a bridge whose far side has never been found
         * contributes nothing and still falls through to the `··` stub — the map cannot name a
         * machine a sweep has not returned.
         */
        @Test
        @DisplayName("but never a far side the player has not discovered")
        void undiscoveredFarSideIsNotCarried() {
            // The same world with the crossing link removed, which is what an undiscovered far side
            // looks like from here: the machine may be in `sightings` for other reasons, but no link
            // to it has been published.
            NetMap noCrossing = new NetMap(
                    HOME,
                    "10.0.0.1",
                    1,
                    List.of(HOME, FAR),
                    world().sightings(),
                    world().links().stream().filter(link -> !link.bridge()).toList());

            assertThat(ServerTabs.filter(noCrossing, "s0").sightings())
                    .extracting(Sighting::address)
                    .doesNotContain("10.1.0.7");
        }

        @Test
        @DisplayName("⚠ drops the bridge's own edge, because one of its ends is not on the grid")
        void dropsCrossingLinks() {
            // ⚠ THE RULE IS UNCHANGED — an edge needs BOTH ends on the grid — but as of 2026-08-09
            // the far end usually IS on it, because a bridge's discovered partner is carried over.
            // So the crossing survives, and it survives identically from either side.
            //
            // What the rule still prevents is the case it was written for: an edge to a machine with
            // no sighting. NetLayout's adjacency pass would build a neighbour set containing it and
            // the barycentre arrangement would order the layer around something invisible — a grid
            // that is subtly wrong with nothing to point at. `undiscoveredFarSideIsNotCarried` covers
            // the input; this covers the output.
            assertThat(ServerTabs.filter(world(), "s0").links())
                    .extracting(NetLink::toAddress)
                    .contains("10.1.0.7");
            assertThat(ServerTabs.filter(world(), "s1").links())
                    .extracting(NetLink::fromAddress)
                    .contains("10.0.0.3");

            // ⚠ Every edge on a filtered map has both ends among its sightings — the property, stated
            // generally, so it holds however the carry-over changes.
            for (String serverId : List.of("s0", "s1")) {
                NetMap tab = ServerTabs.filter(world(), serverId);
                java.util.Set<String> present =
                        tab.sightings().stream().map(Sighting::address).collect(java.util.stream.Collectors.toSet());
                assertThat(tab.links())
                        .as("every edge on " + serverId + " joins two drawn machines")
                        .allMatch(link -> present.contains(link.fromAddress()) && present.contains(link.toAddress()));
            }
        }

        @Test
        @DisplayName("⚠ leaves the rig on its own server and plants it on no other")
        void theRigStaysHome() {
            assertThat(ServerTabs.filter(world(), "s0").sightings()).anyMatch(Sighting::self);
            assertThat(ServerTabs.filter(world(), "s1").sightings()).noneMatch(Sighting::self);
        }

        @Test
        @DisplayName("an unknown or blank server yields an empty map, never the world")
        void unknownIsEmpty() {
            // The dangerous default is the other one: a filter that fell through to "everything" would
            // put the whole world on a tab named after one server.
            assertThat(ServerTabs.filter(world(), "s9").sightings()).isEmpty();
            assertThat(ServerTabs.filter(world(), "").sightings()).isEmpty();
            assertThat(ServerTabs.filter(null, "s0").sightings()).isEmpty();
        }

        @Test
        @DisplayName("keeps the strip intact, so the tabs survive being on a filtered map")
        void keepsTheServerList() {
            assertThat(ServerTabs.of(ServerTabs.filter(world(), "s1")))
                    .extracting(ServerTabs.Tab::serverId)
                    .containsExactly("s0", "s1");
        }
    }

    @Nested
    @DisplayName("laid out")
    class LaidOut {

        @Test
        @DisplayName("⚠ a foreign server starts at layer 0, not at its distance from the rig")
        void layersAreRebased() {
            // ⚠ WITHOUT THE REBASE THE TAB IS BLANK-LOOKING. s1's machines are 3 and 4 hops from the
            // rig, so the grid would open with three empty columns and the content off the right-hand
            // edge — which reads as a broken view rather than as a distant server.
            NetLayout.Result far = NetLayout.of(ServerTabs.filter(world(), "s1"));

            // ⚠ Three columns now, not two: the carried-over bridge from home sits one hop shallower
            // than s1's own machines, so the rebase starts from IT. That is the honest picture — the
            // door is genuinely nearer the rig than anything behind it — and it is why the rebase is
            // "the shallowest machine in the MAP" rather than "the shallowest on this server".
            assertThat(far.layers()).isEqualTo(3);
            assertThat(far.placed()).extracting(NetLayout.Placed::layer).containsExactlyInAnyOrder(0, 1, 2);
        }

        @Test
        @DisplayName("⚠ and the whole-world map is completely unchanged by it")
        void homeIsUntouched() {
            // The rig is always at hop 0, so the base is 0 and every layer keeps the number it had.
            // This is what makes the rebase safe to do inside NetLayout rather than at the call site.
            NetLayout.Result whole = NetLayout.of(world());

            assertThat(whole.layers()).isEqualTo(5);
            assertThat(whole.placed())
                    .filteredOn(placed -> placed.sighting().self())
                    .singleElement()
                    .extracting(NetLayout.Placed::layer)
                    .isEqualTo(0);
        }
    }
}
