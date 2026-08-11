package io.github.stoicswe.eyeandsickle.client.ui.netmap;

import io.github.stoicswe.eyeandsickle.client.session.LocalGameSession;
import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import io.github.stoicswe.eyeandsickle.protocol.game.Sighting;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * How often the map folds anything, measured on worlds the generator actually builds.
 *
 * <h2>Why this exists</h2>
 *
 * {@code docs/client/09-network-map-graph.md} §8 <b>NM-2</b> has said since the stack was designed
 * that {@code NET_STACK_THRESHOLD} is <em>proposed, not measured</em>, and a threshold nobody can
 * measure is a feature nobody can tell is switched off. It was switched off: run against the rule as
 * it stood on 2026-08-08 — a fold of childless siblings, gated at four — this reported <b>one stack
 * across twelve worlds</b>, which is what prompted the branch fold.
 *
 * <p>A fixture cannot answer this. {@code NetFixtures} builds the shapes the design is written
 * against; the question is whether the <em>generator</em> ever produces them, and only a real world
 * walked by a real traversal loop can say. So this opens characters, sweeps, takes footholds and
 * sweeps again, exactly as {@code DeckSnapshot} does, and then asks the layout what it did.
 *
 * <pre>{@code
 * mvn install -DskipTests
 * mvn -pl client test-compile
 * mvn -pl client exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=io.github.stoicswe.eyeandsickle.client.ui.netmap.FoldCensus \
 *     -Dcensus.worlds=12 -Dcensus.hops=8
 * }</pre>
 *
 * <p>⚠ Not a test, and deliberately not one. It builds a dozen worlds and walks each of them, which
 * is seconds rather than milliseconds, and its output is a distribution for a person to read rather
 * than an assertion — the figure it informs is a judgement about legibility, not a property.
 */
public final class FoldCensus {

    private FoldCensus() {}

    /** A clock the walk can wind forward, so a commissioned sweep can actually finish. */
    private static final class Advancing extends Clock {

        private Instant at = Instant.parse("2026-08-08T12:00:00Z");

        void advance(Duration by) {
            at = at.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return at;
        }
    }

    public static void main(String[] args) {
        int worlds = Integer.getInteger("census.worlds", 12);
        int hops = Integer.getInteger("census.hops", 8);
        Path scratch = Path.of(System.getProperty("java.io.tmpdir"), "fold-census");
        scratch.toFile().mkdirs();

        int foldedWorlds = 0;
        int totalStacks = 0;
        Map<Integer, Integer> widths = new TreeMap<>();
        Map<Integer, Integer> columns = new TreeMap<>();
        Map<Integer, Integer> branchSizes = new TreeMap<>();

        for (int world = 0; world < worlds; world++) {
            Advancing clock = new Advancing();
            GameEngine game = io.github.stoicswe.eyeandsickle.client.support.TestSaves.bare(
                    io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(scratch.resolve("save-" + world + ".json")),
                    "census" + world,
                    clock);
            LocalGameSession session = new LocalGameSession(game);
            walk(game, session, clock, hops);

            NetMap map = session.net();
            NetLayout.Result layout = NetLayout.of(map);
            if (!layout.stacks().isEmpty()) {
                foldedWorlds++;
                totalStacks += layout.stacks().size();
            }
            for (NetLayout.Stack branch : layout.branches()) {
                branchSizes.merge(branch.count(), 1, Integer::sum);
            }
            columns.merge(layout.layers(), 1, Integer::sum);

            Map<Integer, Integer> perLayer = new TreeMap<>();
            for (Sighting sighting : map.sightings()) {
                perLayer.merge(sighting.hopsFromRig(), 1, Integer::sum);
            }
            for (int width : perLayer.values()) {
                widths.merge(width, 1, Integer::sum);
            }

            System.out.printf(
                    "world %2d  machines %3d  columns %2d  folded automatically %2d  branches offered %2d%n",
                    world,
                    map.sightings().size(),
                    layout.layers(),
                    layout.foldedMachines(),
                    layout.branches().size());
        }

        System.out.println();
        System.out.println("worlds folding something on their own: " + foldedWorlds + " / " + worlds);
        System.out.println("stacks drawn in total:                 " + totalStacks);
        System.out.println("columns drawn        (n -> worlds):    " + columns);
        System.out.println("layer widths         (n -> layers):    " + widths);
        System.out.println("foldable branch size (n -> branches):  " + branchSizes);
    }

    /**
     * Sweeps, takes a foothold on something not yet swept from, and sweeps again.
     *
     * <p>⚠ A <b>frontier</b>, not "wherever the last sweep found something". Taking the last result
     * bounces between two adjacent machines and re-sweeps positions already exhausted — {@code
     * VantageDiscoveryTest} records the same trap, where it read as the rules not working.
     */
    private static void walk(GameEngine game, LocalGameSession session, Advancing clock, int hops) {
        game.state().schematics.add(io.github.stoicswe.eyeandsickle.engine.net.NetRules.TOPOLOGY_MAPPER);
        for (String owned : List.of("net-sweep-wide", "net-sweep-deep")) {
            io.github.stoicswe.eyeandsickle.engine.Catalogue.byId(owned).ifPresent(offering -> {
                var item = new io.github.stoicswe.eyeandsickle.engine.state.ItemState();
                item.itemType = offering.id();
                item.displayName = offering.name();
                item.tier = io.github.stoicswe.eyeandsickle.protocol.game.StorageTier.VAULT.name();
                game.state().items.add(item);
            });
        }
        session.sweep("--deep");
        settle(session, clock);

        Set<String> sweptFrom = new HashSet<>();
        sweptFrom.add(game.state().topology.playerAddress);
        for (int step = 0; step < hops; step++) {
            String next = "";
            for (var host : game.state().topology.hosts) {
                if (host.discovered && !sweptFrom.contains(host.address)) {
                    next = host.address;
                    break;
                }
            }
            if (next.isEmpty()) {
                return;
            }
            // ⚠ Planted rather than breached — the one shortcut here. It stands in for the puzzle, not
            // for the rule: connect still accepts or refuses on the rules' own terms, and the sweep
            // that follows is a real sweep from the new position.
            for (var host : game.state().topology.hosts) {
                if (host.address.equals(next)) {
                    host.foothold = true;
                }
            }
            io.github.stoicswe.eyeandsickle.engine.net.NetRules.connect(game.state(), next, game.now());
            sweptFrom.add(next);
            session.sweep("--deep");
            settle(session, clock);
        }
    }

    /** Past the sweep, and past the compute recovery, so the next one is not refused for cycles. */
    private static void settle(LocalGameSession session, Advancing clock) {
        clock.advance(Duration.ofSeconds(io.github.stoicswe.eyeandsickle.engine.Balance.NET_SWEEP_DEEP_SECONDS + 1));
        session.tick();
        clock.advance(Duration.ofMinutes(10));
        session.tick();
    }
}
