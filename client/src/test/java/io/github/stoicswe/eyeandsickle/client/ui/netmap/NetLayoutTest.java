package io.github.stoicswe.eyeandsickle.client.ui.netmap;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Where the map puts things.
 *
 * <p>Pure and headless, because {@link NetLayout} is. The one claim worth stating up front is the
 * theorem the whole renderer rests on: in a hop layering, <b>no edge can span more than one
 * column</b>, so there are exactly two edge classes and a skip edge cannot exist. {@code LatticeMap}
 * has the opposite property — it silently drops any {@code rank → rank+2} edge, and no gap ever draws
 * it — and the difference between the two is a rule that holds by construction versus a bug waiting
 * for a graph shaped slightly differently.
 */
class NetLayoutTest {

    private static Map<String, NetLayout.Placed> bySlot(NetLayout.Result result) {
        Map<String, NetLayout.Placed> out = new HashMap<>();
        for (NetLayout.Placed placed : result.placed()) {
            out.put(placed.sighting().address(), placed);
        }
        return out;
    }

    @Nested
    @DisplayName("columns are hop distance, and that is what bounds every edge")
    class Layering {

        @Test
        @DisplayName("no edge spans more than one column — the theorem the router depends on")
        void noEdgeSpansTwoLayers() {
            for (NetMap map : List.of(NetFixtures.opening(), NetFixtures.twoHops(), NetFixtures.crowded(30))) {
                NetLayout.Result result = NetLayout.of(map);
                Map<String, NetLayout.Placed> placed = bySlot(result);
                for (NetLayout.Routed routed : result.routed()) {
                    int from = placed.get(routed.fromAddress()).layer();
                    int to = placed.get(routed.toAddress()).layer();
                    assertThat(Math.abs(from - to))
                            .as("edge %s -> %s spans one column at most", routed.fromAddress(), routed.toAddress())
                            .isLessThanOrEqualTo(1);
                    assertThat(routed.lateral())
                            .as("an edge inside a column is lateral, one across is forward")
                            .isEqualTo(from == to);
                }
            }
        }

        @Test
        @DisplayName("no real edge is dropped — the theorem is asserted, not assumed")
        void everyLinkSurvives() {
            // NetLayout drops an edge it cannot route rather than drawing it wrong. That is the safe
            // failure, and it would also hide a broken hop metric behind a picture that still looks
            // plausible — so the count is checked rather than trusted.
            NetMap map = NetFixtures.twoHops();
            assertThat(NetLayout.of(map).routed()).hasSize(map.links().size());
        }

        @Test
        @DisplayName("the vantage is alone in column zero, at row zero")
        void vantageIsTheOrigin() {
            NetLayout.Result result = NetLayout.of(NetFixtures.twoHops());
            List<NetLayout.Placed> first = result.placed().stream()
                    .filter(placed -> placed.layer() == 0)
                    .toList();
            assertThat(first).hasSize(1);
            assertThat(first.get(0).row()).isZero();
            assertThat(first.get(0).sighting().vantage()).isTrue();
        }

        @Test
        @DisplayName("a symmetric link pair is one edge, not two")
        void symmetricLinksCollapse() {
            // The rules write links both ways. Drawing both is invisible on a merged junction table
            // and quietly wrong in the lane assignment: every edge would consume two lanes, so a
            // three-lane gap would hold at most one and a half edges before it started stacking.
            NetMap map = NetFixtures.map(
                    List.of(
                            NetFixtures.self("10.0.0.1"),
                            NetFixtures.sighting("10.0.0.2", HostKind.UNKNOWN, 1, false, false, false, "")),
                    List.of(NetFixtures.link("10.0.0.1", "10.0.0.2"), NetFixtures.link("10.0.0.2", "10.0.0.1")),
                    1);
            assertThat(NetLayout.of(map).routed()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("rows are assigned once, the same way every time")
    class Rows {

        @Test
        @DisplayName("the same map lays out identically twice")
        void deterministic() {
            // The packet repaints on a timer. A layout that settled differently between two frames
            // would make the whole map crawl, and it would do it only in the running client.
            NetMap map = NetFixtures.twoHops();
            assertThat(NetLayout.of(map)).isEqualTo(NetLayout.of(map));
        }

        @Test
        @DisplayName("rows within a column are a contiguous run from zero")
        void rowsAreContiguous() {
            NetLayout.Result result = NetLayout.of(NetFixtures.twoHops());
            Map<Integer, List<Integer>> rows = new HashMap<>();
            for (NetLayout.Placed placed : result.placed()) {
                rows.computeIfAbsent(placed.layer(), k -> new ArrayList<>()).add(placed.row());
            }
            for (Map.Entry<Integer, List<Integer>> entry : rows.entrySet()) {
                List<Integer> sorted = entry.getValue().stream().sorted().toList();
                for (int i = 0; i < sorted.size(); i++) {
                    assertThat(sorted.get(i))
                            .as("column %d has no gap at row %d", entry.getKey(), i)
                            .isEqualTo(i);
                }
            }
        }

        @Test
        @DisplayName("addresses order numerically, so 10.0.0.9 sits above 10.0.0.10")
        void addressesOrderNumerically() {
            // A plain string compare is deterministic, which is all the tiebreak needs, and it would
            // put .10 above .9. A column of addresses that is almost sorted reads as a broken
            // instrument rather than as a rendering choice.
            assertThat(NetLayout.compareAddresses("10.0.0.9", "10.0.0.10")).isNegative();
            assertThat(NetLayout.compareAddresses("10.0.0.10", "10.0.0.9")).isPositive();
            assertThat(NetLayout.compareAddresses("10.0.0.4", "10.0.0.4")).isZero();
            // Anything that is not a dotted number still orders, because a total order is what the
            // sort contract needs and "unparseable" is not an excuse to throw inside a repaint.
            assertThat(NetLayout.compareAddresses("localhost", "10.0.0.4")).isNotZero();
        }
    }

    @Nested
    @DisplayName("nothing is hidden: a wide fan folds into a stack, and the stack opens")
    class Stacking {

        private static NetLayout.Stack only(NetLayout.Result result) {
            assertThat(result.stacks()).hasSize(1);
            return result.stacks().get(0);
        }

        @Test
        @DisplayName("no machine is ever dropped, however wide the layer")
        void nothingIsEverDropped() {
            // ⚠ The rule the whole feature exists to restore. The old renderer drew the first sixty
            // rows of a layer and put the rest in a header count, so machines the player had found
            // were on the map's data and absent from its picture — docs/client/09 §1.1. Every machine
            // is now either drawn or inside a stack that names its exact count.
            for (NetMap map : List.of(NetFixtures.crowded(50), NetFixtures.estate(20), NetFixtures.twoHops())) {
                NetLayout.Result result = NetLayout.of(map);
                int folded = 0;
                for (NetLayout.Stack stack : result.stacks()) {
                    folded += stack.count();
                }
                assertThat(result.placed().size() + folded)
                        .as("every sighting is drawn or folded")
                        .isEqualTo(map.sightings().size());
                assertThat(result.layerHeaders()).noneMatch(header -> header.contains("MORE"));
            }
        }

        @Test
        @DisplayName("a fan wider than the threshold folds, and the count is exact")
        void wideFansFold() {
            NetLayout.Stack stack = only(NetLayout.of(NetFixtures.estate(7)));
            assertThat(stack.count()).isEqualTo(7);
            assertThat(stack.parentAddress()).isEqualTo("10.0.0.2");
            assertThat(stack.layer()).isEqualTo(2);
            assertThat(stack.expanded()).isFalse();
            // The other neighbour's single child is drawn, not folded: two or three children are more
            // legible drawn than counted, which is what the threshold is for.
            assertThat(bySlot(NetLayout.of(NetFixtures.estate(7)))).containsKey("10.0.2.5");
        }

        @Test
        @DisplayName("a fan at the threshold is drawn, not folded")
        void theThresholdIsAnExclusiveBound() {
            assertThat(NetLayout.of(NetFixtures.estate(UiTokens.NET_STACK_THRESHOLD))
                            .stacks())
                    .isEmpty();
            assertThat(NetLayout.of(NetFixtures.estate(UiTokens.NET_STACK_THRESHOLD + 1))
                            .stacks())
                    .hasSize(1);
        }

        @Test
        @DisplayName("the player's own neighbours are never folded, however many there are")
        void layerOneIsNeverFolded() {
            // ⚠ Layer 1 is what the panel is FOR. With a one-hop ceiling it is the entire map a new
            // character has, and every machine in it hangs off the rig — so a rule that looked at
            // eligibility alone folded the whole neighbourhood into one box and left the headline
            // surface reading "rig, times fifty".
            assertThat(NetLayout.of(NetFixtures.crowded(50)).stacks()).isEmpty();
            assertThat(NetLayout.of(NetFixtures.crowded(50)).placed()).hasSize(51);
        }

        @Test
        @DisplayName("no adjacency is lost to a fold — every link is still drawn, or is inside one box")
        void noAdjacencyIsLost() {
            // ⚠ THE GENERAL FORM OF WHAT FOLDING MAY COST, and the assertion the two eligibility
            // rules exist to satisfy. A folded machine's edges are re-pointed at its stack; the only
            // edge allowed to vanish is one whose BOTH endpoints went into the same box, because then
            // it is inside the box rather than missing from the picture.
            //
            // ⚠ Asserted over every fixture rather than against one shape. The specific cases below
            // are each caught by one of the two rules, and which one catches which is an
            // implementation detail — this is the property neither is allowed to break.
            for (NetMap map : List.of(
                    NetFixtures.twoHops(),
                    NetFixtures.estate(7),
                    NetFixtures.estateWithASharedChild(7),
                    NetFixtures.estateWithSiblingLink(7),
                    NetFixtures.estateWithAGrandchild(7))) {
                NetLayout.Result result = NetLayout.of(map);
                Map<String, String> foldOf = new HashMap<>();
                for (NetLayout.Stack stack : result.stacks()) {
                    for (var member : stack.members()) {
                        foldOf.put(member.address(), stack.id());
                    }
                }
                Set<String> drawn = new java.util.HashSet<>();
                for (NetLayout.Routed routed : result.routed()) {
                    drawn.add(routed.fromAddress() + "|" + routed.toAddress());
                    drawn.add(routed.toAddress() + "|" + routed.fromAddress());
                }
                for (var link : map.links()) {
                    String from = foldOf.getOrDefault(link.fromAddress(), link.fromAddress());
                    String to = foldOf.getOrDefault(link.toAddress(), link.toAddress());
                    if (from.equals(to)) {
                        continue;
                    }
                    assertThat(drawn)
                            .as("%s ↔ %s is neither drawn nor inside one box", link.fromAddress(), link.toAddress())
                            .contains(from + "|" + to);
                }
            }
        }

        @Test
        @DisplayName("a machine with a second parent is not folded — its other edge would be a lie")
        void aSharedChildStaysDrawn() {
            NetLayout.Result result = NetLayout.of(NetFixtures.estateWithASharedChild(7));
            assertThat(only(result).count()).isEqualTo(6);
            assertThat(bySlot(result))
                    .as("the shared machine is drawn, so both of its edges can be")
                    .containsKey("10.0.1.10");
        }

        @Test
        @DisplayName("a machine with a child of its own brings the child INTO the fold")
        void aBranchTakesItsDescendants() {
            // ⚠ THIS TEST'S PREMISE WAS REVERSED ON 2026-08-08, and the old one was not wrong so much
            // as one of two ways to satisfy the same requirement. It read "a machine with a child of
            // its own is not folded — the child's edge would hang", and excluding it is what left the
            // whole feature dormant: the generator builds spines, so almost every machine has a child.
            // The other resolution is to fold the child too, and then there is no hanging edge either.
            NetLayout.Result result = NetLayout.of(NetFixtures.estateWithAGrandchild(7));
            assertThat(only(result).count())
                    .as("seven in the fan and the one behind one of them")
                    .isEqualTo(8);
            assertThat(only(result).members()).anyMatch(member -> member.address().equals("10.0.3.7"));
            assertThat(only(result).layer())
                    .as("the box sits in the shallowest column it holds, not the deepest")
                    .isEqualTo(2);
            assertThat(bySlot(result))
                    .as("neither the machine nor its child is drawn — both are in the box")
                    .doesNotContainKeys("10.0.1.10", "10.0.3.7");
            assertThat(result.routed())
                    .as("that edge is inside the box, so it is drawn nowhere")
                    .noneMatch(routed -> routed.toAddress().equals("10.0.3.7"));
        }

        @Test
        @DisplayName("a chain never folds itself, however long it gets")
        void aChainIsNotAFork() {
            // ⚠ The rule NET_STACK_MIN_FORK exists for, and the reason a branch's SIZE could not be
            // the only criterion. Every server the generator builds is a spine (docs/design/18 §2), so
            // a size-only rule collapses a corridor on sight and the map opens reading "rig → a → ×14"
            // — hiding the one structure the player is walking.
            for (int length : List.of(3, 6, 12)) {
                assertThat(NetLayout.of(NetFixtures.spine(length)).stacks())
                        .as("a spine %d machines long", length)
                        .isEmpty();
                assertThat(NetLayout.of(NetFixtures.spine(length)).placed())
                        .hasSize(length + 2);
            }
        }

        @Test
        @DisplayName("a fork folds on what is BEHIND it, not on how wide it is at the seam")
        void aForkFoldsOnItsBranchSize() {
            // Two children — under any fan-width threshold this project would accept — carrying twelve
            // machines across six columns. What a fold is worth is what it takes off the screen.
            NetLayout.Result result = NetLayout.of(NetFixtures.fork(6));
            NetLayout.Stack fold = only(result);
            assertThat(fold.count()).isEqualTo(12);
            assertThat(fold.parentAddress()).isEqualTo("10.0.0.2");
            assertThat(fold.layer()).isEqualTo(2);
            assertThat(result.placed())
                    .as("the rig and its one neighbour, and nothing else")
                    .hasSize(2);
        }

        @Test
        @DisplayName("folding a deep branch trims the columns it emptied")
        void trailingColumnsAreTrimmed() {
            // ⚠ New with branch folds and easy to miss. A same-layer fold never emptied a column; a
            // branch fold absorbs everything behind it, so a map that ran to eight columns now runs to
            // two. Left in, the map keeps headers and a blank field for a hop range holding nothing —
            // which reads as "there is something out there you cannot see", the one thing this surface
            // may never say.
            assertThat(NetLayout.of(NetFixtures.fork(6)).layers()).isEqualTo(3);
            assertThat(NetLayout.of(NetFixtures.fork(6)).layerHeaders()).hasSize(3);
            assertThat(NetLayout.of(NetFixtures.fork(6), NetLayout.FoldState.opened("10.0.0.2"))
                            .layers())
                    .as("opening it puts the columns back")
                    .isEqualTo(8);
        }

        @Test
        @DisplayName("the player can fold a branch the map would not, and open one it would")
        void theChoiceIsThePlayers() {
            // The whole point of the amendment: the threshold decides what happens on its own, and the
            // player overrides it in BOTH directions.
            NetMap chain = NetFixtures.spine(6);
            NetLayout.Result byHand = NetLayout.of(chain, NetLayout.FoldState.collapsed("10.0.0.2"));
            assertThat(byHand.stacks()).hasSize(1);
            assertThat(byHand.stacks().get(0).count()).isEqualTo(6);
            assertThat(byHand.stacks().get(0).automatic())
                    .as("folded because the player said so, not because the map did")
                    .isFalse();
            assertThat(byHand.placed()).hasSize(2);

            NetLayout.Result opened = NetLayout.of(NetFixtures.estate(7), NetLayout.FoldState.opened("10.0.0.2"));
            assertThat(opened.stacks()).isEmpty();
            assertThat(opened.foldedMachines()).isZero();
        }

        @Test
        @DisplayName("the rig's own branch is never a candidate, so no click can fold the whole map")
        void theWholeMapCannotBeFolded() {
            // ⚠ NET_STACK_MIN_LAYER bounds the CANDIDATES, not only the automatic ones. Once a branch
            // can be folded by hand, a candidate at layer 1 is one menu click that folds the entire
            // discovered world into a single box hanging off SELF — the rig → ×7 defect, reached
            // deliberately rather than by accident.
            for (NetMap map : List.of(NetFixtures.estate(7), NetFixtures.spine(6), NetFixtures.crowded(50))) {
                assertThat(NetLayout.of(map).foldAt("10.0.0.1"))
                        .as("the rig offers no branch to fold")
                        .isNull();
                assertThat(NetLayout.of(map, NetLayout.FoldState.collapsed("10.0.0.1"))
                                .stacks())
                        .as("and asking for one anyway does nothing")
                        .isEqualTo(NetLayout.of(map).stacks());
            }
        }

        @Test
        @DisplayName("a header names the servers a column DRAWS, not the ones it hides")
        void headersFollowWhatIsDrawn() {
            // A branch fold holds machines from columns further out. A header taken off the raw
            // sightings would name a network whose every machine is now in a box two columns left.
            NetLayout.Result result = NetLayout.of(NetFixtures.fork(6));
            assertThat(result.layerHeaders()).hasSize(result.layers());
            for (String header : result.layerHeaders()) {
                assertThat(header).isNotBlank();
            }
        }

        @Test
        @DisplayName("an edge between two members stays inside the fold")
        void siblingLinksDoNotDisqualify() {
            // Both endpoints are in the box, so the collapsed edge into the parent is still the only
            // edge crossing the boundary — which is the criterion, not "the member has no edges".
            NetLayout.Result result = NetLayout.of(NetFixtures.estateWithSiblingLink(7));
            assertThat(only(result).count()).isEqualTo(7);
            for (NetLayout.Routed routed : result.routed()) {
                assertThat(routed.fromAddress()).isNotEqualTo("10.0.1.10");
                assertThat(routed.toAddress()).isNotEqualTo("10.0.1.11");
            }
        }

        @Test
        @DisplayName("a collapsed fan is one edge from its parent, not a bundle")
        void oneEdgePerStack() {
            NetLayout.Result result = NetLayout.of(NetFixtures.estate(7));
            List<NetLayout.Routed> intoStack = result.routed().stream()
                    .filter(routed -> routed.toAddress().startsWith(NetLayout.STACK_PREFIX))
                    .toList();
            assertThat(intoStack).hasSize(1);
            assertThat(intoStack.get(0).fromAddress()).isEqualTo("10.0.0.2");
            assertThat(intoStack.get(0).lateral()).isFalse();
        }

        @Test
        @DisplayName("opening a fold adds exactly its members and moves nothing to another column")
        void expansionAddsAndDoesNotRelocate() {
            // ⚠ WEAKER THAN IT WAS, and the weakening is the honest part. §3.4 promised expansion
            // INSERTS rows at the stack's own row so nothing above it moves, and that was keepable
            // while a fold was one column wide. A branch fold's members belong to columns that were
            // arranged without them, so there is no row to insert them at — the guarantee that
            // survives is that a machine never changes COLUMN, which is what the player is navigating
            // by. Recorded as NM-1 in docs/design/15.
            NetMap map = NetFixtures.estate(7);
            Map<String, NetLayout.Placed> before = bySlot(NetLayout.of(map));
            NetLayout.Result after = NetLayout.of(map, NetLayout.FoldState.opened("10.0.0.2"));
            Map<String, NetLayout.Placed> rows = bySlot(after);

            for (Map.Entry<String, NetLayout.Placed> entry : before.entrySet()) {
                assertThat(rows).containsKey(entry.getKey());
                assertThat(rows.get(entry.getKey()).layer())
                        .as("%s must not change column when a fold opens", entry.getKey())
                        .isEqualTo(entry.getValue().layer());
            }
            assertThat(after.stacks()).isEmpty();
            assertThat(after.branches().get(0).expanded()).isTrue();
            assertThat(after.placed()).hasSize(map.sightings().size());
            assertThat(after.foldedMachines()).isZero();
        }

        @Test
        @DisplayName("an expanded fold draws every edge it was hiding")
        void expansionRestoresEdges() {
            NetMap map = NetFixtures.estateWithSiblingLink(7);
            NetLayout.Result open = NetLayout.of(map, NetLayout.FoldState.opened("10.0.0.2"));
            assertThat(open.routed()).hasSize(map.links().size());
            assertThat(open.routed())
                    .as("the edge that was inside the box is drawn again")
                    .anyMatch(routed -> routed.lateral()
                            && routed.fromAddress().equals("10.0.1.10")
                            && routed.toAddress().equals("10.0.1.11"));
        }

        @Test
        @DisplayName("an expansion id that no longer names a fold is ignored, not an error")
        void staleExpansionIsHarmless() {
            // A sweep can change the grouping under the player at any moment. A set that threw or
            // reset on a stale id would turn a routine discovery into a collapsed map.
            assertThat(NetLayout.of(
                            NetFixtures.estate(7), NetLayout.FoldState.collapsed("10.9.9.9", "nonsense")))
                    .isEqualTo(NetLayout.of(NetFixtures.estate(7)));
        }
    }

    @Nested
    @DisplayName("headers answer \"which network am I looking at\"")
    class Headers {

        @Test
        @DisplayName("one per column, naming the hop and the server")
        void headersNameHopAndServer() {
            NetLayout.Result result = NetLayout.of(NetFixtures.twoHops());
            assertThat(result.layerHeaders()).hasSize(result.layers());
            assertThat(result.layerHeaders().get(0)).startsWith("H0").contains(NetFixtures.HOME.name());
            assertThat(result.layerHeaders().get(1)).startsWith("H1");
        }
    }

    @Nested
    @DisplayName("nothing discovered")
    class Empty {

        @Test
        @DisplayName("an empty map lays out to nothing at all, and does not throw")
        void emptyIsEmpty() {
            // Not a placeholder and not a count. An undiscovered machine has no Sighting, so there is
            // nothing here that could leak even by accident.
            NetLayout.Result result = NetLayout.of(NetMap.empty());
            assertThat(result.layers()).isZero();
            assertThat(result.placed()).isEmpty();
            assertThat(result.routed()).isEmpty();
            assertThat(NetLayout.of(null)).isEqualTo(NetLayout.Result.empty());
        }
    }
}
