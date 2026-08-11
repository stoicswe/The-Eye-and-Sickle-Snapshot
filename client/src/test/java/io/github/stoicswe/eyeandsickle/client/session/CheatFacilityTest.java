package io.github.stoicswe.eyeandsickle.client.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.support.TestSaves;
import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import io.github.stoicswe.eyeandsickle.engine.rules.ComputeLadder;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The developer facility, and the one rule that matters about it: <b>solo only</b>.
 *
 * <h2>⚠ What this class is really guarding</h2>
 *
 * Not the cheats — {@code engine/rules/CheatsTest} owns those. This guards the <em>gate</em>:
 * {@link CheatFacility#forSession} is the single place that decides a session may cheat, and the
 * whole safety argument for the facility existing at all is that it answers no for anything that can
 * reach another player. A second site testing the mode by hand would be a second answer to that
 * question, and the day the two disagreed the one saying yes would be the one that mattered.
 */
class CheatFacilityTest {

    private static final Instant T0 = Instant.parse("2026-08-09T12:00:00Z");

    private static LocalGameSession solo(Path dir) {
        // ⚠ Keyed on the path, so two calls with different directories are two CHARACTERS rather than
        // two handles on one save — which is what `perCharacter` needs to mean anything.
        GameEngine engine = TestSaves.bare(
                io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(dir.resolve("save.json")),
                "operator",
                Clock.fixed(T0, ZoneOffset.UTC));
        return new LocalGameSession(engine);
    }

    @Test
    @DisplayName("a solo session has a facility")
    void soloHasOne(@TempDir Path dir) {
        try (LocalGameSession session = solo(dir)) {
            assertThat(session.mode()).isEqualTo(SessionMode.SOLO);
            assertThat(CheatFacility.forSession(session)).isPresent();
        }
    }

    /**
     * ⚠ The important one. A cheat applied to a character on a home server would be forged
     * authoritative state — <b>I14</b>, and <b>I15</b> the moment that character touched a federated
     * outcome. The facility is absent from {@link GameSession} entirely so there is nothing to
     * refuse; this pins that the gate agrees.
     */
    @Test
    @DisplayName("a server-backed session has none — there is nothing to call, not something to refuse")
    void notSolo() {
        // The real online session, not a stub: a stub would only prove the gate rejects stubs.
        GameSession online = new RemoteGameSession(java.net.URI.create("https://home.example"));

        assertThat(online.mode()).isNotEqualTo(SessionMode.SOLO);
        assertThat(CheatFacility.forSession(online)).isEmpty();

        // The facility is not a method on the port, so a server-backed session cannot expose one
        // even by accident. Stated as a reflective check because the compiler cannot assert an
        // absence, and "we would notice in review" is what this file exists instead of.
        assertThat(GameSession.class.getMethods())
                .noneMatch(m -> m.getName().toLowerCase(java.util.Locale.ROOT).contains("cheat"));
    }

    /**
     * ⚠ PER CHARACTER, NOT PER INSTALL — asked for on 2026-08-09, and confirmed rather than changed.
     *
     * <p>It falls out of where the state lives: {@code CheatState} is a field on {@code GameSave}, so
     * the page's visibility and every override belong to one character's save file. Nothing about the
     * facility touches {@code ClientProfile}, which is the machine-wide store — and that is the thing
     * worth pinning, because "put it in settings.json" is the obvious-looking place for a UI toggle
     * and would silently make one character's cheats every character's.
     *
     * <p>Two separate stores here, so this is a real second character rather than the same save read
     * twice.
     */
    @Test
    @DisplayName("cheats belong to ONE character — a second save is untouched by the first's")
    void perCharacter(@TempDir Path dir) {
        try (LocalGameSession first = solo(dir.resolve("a"));
                LocalGameSession second = solo(dir.resolve("b"))) {

            CheatFacility cheatedOn = CheatFacility.forSession(first).orElseThrow();
            cheatedOn.setThermalRecovery(false);
            cheatedOn.setCycleCeiling(512L);
            assertThat(cheatedOn.state().revealed()).isTrue();

            CheatFacility other = CheatFacility.forSession(second).orElseThrow();
            assertThat(other.state().revealed())
                    .as("the page stays hidden for a character that has never used it")
                    .isFalse();
            assertThat(other.state().cycleCeiling()).isZero();
            assertThat(other.state().thermalRecovery()).isTrue();
        }
    }

    @Test
    @DisplayName("no session at all — the login screen — has none")
    void noSession() {
        assertThat(CheatFacility.forSession(null)).isEmpty();
    }

    @Test
    @DisplayName("a cheat reaches the game, persists, and is visible in the next snapshot")
    void applies(@TempDir Path dir) {
        try (LocalGameSession session = solo(dir)) {
            CheatFacility cheats = CheatFacility.forSession(session).orElseThrow();
            assertThat(cheats.state().revealed()).isFalse();

            cheats.grant(Ethecoin.ofWholeEthecoin(250L).wei());
            cheats.setCycleCeiling(256L);

            CheatFacility.Snapshot now = cheats.state();
            assertThat(now.revealed()).isTrue();
            assertThat(now.balanceWei()).isEqualTo(Ethecoin.ofWholeEthecoin(250L).wei());
            assertThat(now.cycleCeiling()).isEqualTo(256L);
            assertThat(now.effectiveCycles()).isEqualTo(256L);
            assertThat(session.balance().wei()).isEqualTo(Ethecoin.ofWholeEthecoin(250L).wei());

            // ⚠ ladderCeiling is the RESTORE POINT the panel offers, so it must be what the items
            // give with the override lifted — not what ComputeLadder.capacityOf answers now, which
            // is the override itself. Asserted by actually restoring: the figure the panel showed is
            // the figure the rig lands on.
            long restorePoint = now.ladderCeiling();
            assertThat(restorePoint).isNotEqualTo(256L);
            cheats.setCycleCeiling(0L);
            assertThat(cheats.state().effectiveCycles()).isEqualTo(restorePoint);
            assertThat(ComputeLadder.capacityOf(session.game().state())).isEqualTo(restorePoint);
        }
    }

    @Test
    @DisplayName("concealing turns everything off and takes the page's own visibility with it")
    void conceals(@TempDir Path dir) {
        try (LocalGameSession session = solo(dir)) {
            CheatFacility cheats = CheatFacility.forSession(session).orElseThrow();
            cheats.setCycleCeiling(512L);
            cheats.setThermalRecovery(false);
            assertThat(cheats.state().revealed()).isTrue();

            cheats.conceal();

            CheatFacility.Snapshot now = cheats.state();
            assertThat(now.revealed()).isFalse();
            assertThat(now.cycleCeiling()).isZero();
            assertThat(now.thermalRecovery()).isTrue();
            // ⚠ The rig has to actually be back, not merely the flag cleared — the ceiling is an
            // override on a derived figure, so "reset" means the next reconcile hands the rig what
            // its items give.
            assertThat(now.effectiveCycles()).isEqualTo(now.ladderCeiling());
        }
    }

    @Test
    @DisplayName("the hidden-machine count is what the reveal button reads, and it empties")
    void reveal(@TempDir Path dir) {
        try (LocalGameSession session = solo(dir)) {
            CheatFacility cheats = CheatFacility.forSession(session).orElseThrow();
            assertThat(cheats.state().hiddenMachines()).isPositive();

            cheats.revealNetwork();

            assertThat(cheats.state().hiddenMachines()).isZero();
            assertThat(session.knownNodes()).isNotEmpty();
        }
    }

    /**
     * ⚠ The two buttons compose rather than overlap: revealing puts machines on the map, and this
     * fills the files of what is on it. The count the button reads has to drop to zero, or the
     * control stays enabled forever with nothing left to do.
     */
    @Test
    @DisplayName("gaining all info empties the unscanned count, and needs the map first")
    void learnsEverything(@TempDir Path dir) {
        try (LocalGameSession session = solo(dir)) {
            CheatFacility cheats = CheatFacility.forSession(session).orElseThrow();
            cheats.revealNetwork();
            assertThat(cheats.state().unscannedMachines()).isPositive();

            cheats.learnEverything();

            assertThat(cheats.state().unscannedMachines()).isZero();
            assertThat(session.nodeReports()).isNotEmpty();
        }
    }
}
