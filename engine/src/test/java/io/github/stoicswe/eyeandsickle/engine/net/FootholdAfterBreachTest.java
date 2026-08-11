package io.github.stoicswe.eyeandsickle.engine.net;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.NetMap;
import io.github.stoicswe.eyeandsickle.protocol.game.Sighting;
import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import io.github.stoicswe.eyeandsickle.engine.state.HostState;
import io.github.stoicswe.eyeandsickle.engine.state.ResolutionState;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A breached machine must show as held on the map.
 *
 * <h2>⚠ The bug this exists for: {@code reconcileFootholds} had no caller</h2>
 *
 * {@code NetRules.reconcileFootholds} is what turns a {@code BREACHED} resolution into a foothold and
 * pays the host's one-time loot. It was written, documented, and covered by five tests — and
 * <b>nothing in the shipping game ever called it</b>. Every caller was a test. So a player could clear
 * every layer of a breach, watch the attempt report success, and find the machine still reading
 * {@code contact} on the map, still refusing {@code connect}, and still holding its loot.
 *
 * <p>It is the exact shape of failure a well-tested unit invites: the unit is correct, its tests pass,
 * and the wiring is the part nobody asserted. These tests are deliberately written against
 * {@link GameEngine} rather than {@code NetRules} — one level up from the unit, which is the only level
 * at which the defect is visible at all.
 */
class FootholdAfterBreachTest {

    private static final Instant T0 = Instant.parse("2026-07-29T09:00:00Z");

    private static GameEngine game(Path dir) {
        return GameEngine.open(
                io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(dir.resolve("save.json")),
                "operator",
                Clock.fixed(T0, ZoneOffset.UTC));
    }

    /**
     * A machine on the map that has been found but not yet taken.
     *
     * <p>⚠ Visibility on the map comes from {@code knownNodes}, NOT from {@code host.discovered} —
     * {@code NetRules.view} builds its sighting list from the former. A fixture that sets only the
     * flag produces a host the map has never heard of, and the lookup below fails with
     * {@code NoSuchElement} rather than with anything that names the real problem. Both are set here
     * because a sweep sets both.
     */
    private static HostState aDiscoveredHost(GameEngine game) {
        HostState host = game.state().topology.hosts.stream()
                .filter(entry -> !"SELF".equals(entry.kind))
                .filter(entry -> !entry.foothold)
                .findFirst()
                .orElseThrow();
        host.discovered = true;
        io.github.stoicswe.eyeandsickle.engine.state.NodeState node =
                new io.github.stoicswe.eyeandsickle.engine.state.NodeState();
        node.address = host.address;
        node.label = host.label;
        game.state().knownNodes.add(node);
        return host;
    }

    /**
     * Files a cleared attempt against a host, the way {@code BreachRules.record} does.
     *
     * <p>Written directly rather than by playing a breach: the puzzles are the subject of their own
     * suite, and threading a solved board through here would make this test fail for reasons that
     * have nothing to do with whether a win reaches the map.
     */
    private static void breached(GameEngine game, HostState host) {
        ResolutionState resolution = new ResolutionState();
        resolution.targetId = "node:" + host.address;
        resolution.outcome = "BREACHED";
        resolution.difficultyTier = 1;
        resolution.at = T0;
        game.state().resolutions.add(resolution);
    }

    private static Sighting on(NetMap map, String address) {
        return map.sightings().stream()
                .filter(sighting -> sighting.address().equals(address))
                .findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("a cleared breach shows as a foothold on the map, not as contact")
    void aWinReachesTheMap(@TempDir Path dir) {
        GameEngine game = game(dir);
        HostState host = aDiscoveredHost(game);
        assertThat(on(game.net(), host.address).foothold())
                .as("the fixture must start from a machine that is NOT held")
                .isFalse();

        breached(game, host);
        game.settleBreachOutcomes();

        // This is the whole bug: the STATE column reads `foothold` off the sighting, so a resolution
        // that never becomes one leaves the map saying `contact` on a machine the player just took.
        assertThat(on(game.net(), host.address).foothold())
                .as("a BREACHED resolution must grant the foothold")
                .isTrue();
    }

    @Test
    @DisplayName("the foothold survives a reload, and is not paid for twice")
    void itSettlesOnLoadToo(@TempDir Path dir) {
        GameEngine first = game(dir);
        HostState host = aDiscoveredHost(first);
        breached(first, host);
        first.persist();

        // ⚠ A save written before the fix carries the resolution and no foothold, so the load path
        // has to settle it as well — otherwise the bug is permanent for anyone who already breached
        // something. This is also the idempotence check: reconcileFootholds is safe to replay because
        // `foothold` and `looted` are both one-way, so the loot must not be credited a second time.
        GameEngine reloaded = GameEngine.open(
                io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(dir.resolve("save.json")),
                "operator",
                Clock.fixed(T0, ZoneOffset.UTC));
        assertThat(on(reloaded.net(), host.address).foothold()).isTrue();

        java.math.BigInteger afterFirstLoad = reloaded.balance().wei();
        reloaded.persist();
        GameEngine again = GameEngine.open(
                io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(dir.resolve("save.json")),
                "operator",
                Clock.fixed(T0, ZoneOffset.UTC));
        assertThat(again.balance().wei())
                .as("a host's one-time loot is one-time across loads")
                .isEqualTo(afterFirstLoad);
    }

    /**
     * ⚠ The same defect, reached from a THIRD caller — and it shipped, 2026-08-09.
     *
     * <p>The developer facility's "open every breach pre-solved" resolves the attempt inside
     * {@code BreachRules.begin}, which means <b>begin</b> is the call that clears the last layer. But
     * only {@code breachAction} and {@code resume} settled outcomes; {@code beginBreach} never did,
     * because until auto-clear existed a breach could not possibly be finished by the act of opening
     * it. So the attempt reported success, a {@code BREACHED} resolution was filed, and the machine
     * stayed {@code contact} on the map and refused a shell — reported as "auto breach does not
     * count the breach as solved".
     *
     * <p>This is the third time the join has been the defect rather than either side of it, which is
     * why the fix goes in {@code beginBreach} beside the one in {@code breachAction} rather than in
     * the cheat: any future caller that can finish a breach has the same obligation.
     */
    @Test
    @DisplayName("a breach that RESOLVES AS IT OPENS still reaches the map")
    void aBreachResolvedAtBeginReachesTheMap(@TempDir Path dir) {
        GameEngine game = game(dir);
        HostState host = aDiscoveredHost(game);
        io.github.stoicswe.eyeandsickle.engine.rules.Cheats.setBreachAutoClear(game.state(), true, T0);
        // ⚠ A breach needs a virus to upload now (docs/design/19 §5) and the upload is a roll. Both
        // are supplied here rather than asserted around: this test is about the FOOTHOLD reaching the
        // map, and a refusal at `begin` or a lost roll would fail it for a reason it is not about.
        var virus = new io.github.stoicswe.eyeandsickle.engine.state.ItemState();
        virus.itemType = io.github.stoicswe.eyeandsickle.engine.breach.BreachVirus.idFor(1);
        virus.tier = io.github.stoicswe.eyeandsickle.protocol.game.StorageTier.VAULT.name();
        game.state().items.add(virus);
        io.github.stoicswe.eyeandsickle.engine.rules.Cheats.setVirusAlwaysHolds(game.state(), true, T0);

        var target = io.github.stoicswe.eyeandsickle.engine.breach.Targets.available(game.state()).stream()
                .filter(candidate -> candidate.targetId().equals("node:" + host.address))
                .findFirst()
                .orElseThrow();
        var result = game.beginBreach(target.targetId());

        assertThat(result.applied()).as("the attempt itself must succeed").isTrue();
        assertThat(game.state().activeBreach.outcome)
                .as("the breach must have resolved, and resolved as a win")
                .isEqualTo("BREACHED");
        assertThat(on(game.net(), host.address).foothold())
                .as("a breach solved on the way in must still take the machine")
                .isTrue();
        assertThat(game.connectTo(host.address))
                .as("and the machine it took must be usable")
                .isTrue();
    }

    @Test
    @DisplayName("holding the machine is what lets you connect to it")
    void theFootholdIsUsable(@TempDir Path dir) {
        GameEngine game = game(dir);
        HostState host = aDiscoveredHost(game);
        // Refused before, allowed after — the foothold is not decoration on the map, it is the thing
        // `connect` checks, and a player who cannot connect to a machine they breached is stuck.
        assertThat(game.connectTo(host.address)).isFalse();

        breached(game, host);
        game.settleBreachOutcomes();
        assertThat(game.connectTo(host.address))
                .as("connect must be allowed once the machine is held")
                .isTrue();
    }
}
