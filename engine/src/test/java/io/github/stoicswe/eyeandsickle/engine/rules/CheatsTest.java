package io.github.stoicswe.eyeandsickle.engine.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import io.github.stoicswe.eyeandsickle.engine.save.TestSaves;
import io.github.stoicswe.eyeandsickle.engine.state.DownloadOrderState;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.ItemState;
import io.github.stoicswe.eyeandsickle.engine.state.TaskState;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the developer facility.
 *
 * <p>Two things are worth testing here and they are not the obvious ones. The first is that an
 * <b>untouched</b> character is bit-for-bit the ordinary rules — every hook is on a hot path, so a
 * default that meant anything else would apply a cheat to every character in the game. The second is
 * that each cheat survives the machinery that exists to undo hand-edited saves: the compute ceiling
 * has to outlive {@code ComputeLadder.reconcile}, which is precisely what stomps a written
 * {@code totalCycles}.
 */
class CheatsTest {

    private static final Instant T0 = Instant.parse("2026-08-09T12:00:00Z");

    private static GameSave save() {
        return GameEngine.newCharacter("operator", T0);
    }

    @Nested
    @DisplayName("a character that has never cheated")
    class Untouched {

        @Test
        @DisplayName("gets the derived ceiling, the recovery curve and the tuned chance, unchanged")
        void identity() {
            GameSave save = save();

            assertThat(Cheats.ceiling(save, 48L)).isEqualTo(48L);
            assertThat(Cheats.thermalRecovery(save)).isTrue();
            assertThat(Cheats.heatMayRise(save)).isTrue();
            assertThat(Cheats.breachAutoClear(save)).isFalse();
            assertThat(Cheats.intrusionChance(save, 0.42d)).isEqualTo(0.42d);
            assertThat(Cheats.finishesNow(save, new TaskState())).isFalse();
            assertThat(Cheats.purchasesAreInstant(save)).isFalse();
            assertThat(Cheats.of(save).anyInForce()).isFalse();
        }

        @Test
        @DisplayName("survives a save written before the field existed")
        void nullState() {
            GameSave save = save();
            // What Jackson leaves behind for a document with no `cheats` key. Every hook reads this
            // on a hot path, so answering from a throwaway default would work perfectly and make
            // every later write vanish — which is why `of` repairs the field in place.
            save.cheats = null;

            assertThat(Cheats.thermalRecovery(save)).isTrue();
            assertThat(save.cheats).isNotNull();

            Cheats.setHeatFrozen(save, true, T0);
            assertThat(Cheats.heatMayRise(save)).isFalse();
        }
    }

    @Nested
    @DisplayName("solo only, never multiplayer")
    class SoloOnly {

        /**
         * ⚠ The engine-tier half of a rule the client already enforces by keeping the facility off
         * the {@code GameSession} port. Not redundant: the port is a fact about the <em>client's</em>
         * wiring, and this engine is also driven by a home server. The day something server-side
         * reaches these methods, "the client would never call them" stops being an argument.
         */
        @Test
        @DisplayName("a federable character cannot have a cheat applied")
        void refused() {
            GameSave save = save();
            save.federable = true;
            BigInteger before = save.ethecoinWei;

            assertThat(Cheats.mayCheat(save)).isFalse();
            assertThat(Cheats.grant(save, Ethecoin.ofWholeEthecoin(500L).wei(), T0))
                    .isEqualTo(Cheats.REFUSED);
            assertThat(Cheats.setCycleCeiling(save, 512L, T0)).isEqualTo(Cheats.REFUSED);
            assertThat(Cheats.setThermalRecovery(save, false, T0)).isEqualTo(Cheats.REFUSED);
            assertThat(Cheats.setInstantTasks(save, true, T0)).isEqualTo(Cheats.REFUSED);
            assertThat(Cheats.setInstantPurchases(save, true, T0)).isEqualTo(Cheats.REFUSED);
            assertThat(Cheats.setHeat(save, 90, T0)).isEqualTo(Cheats.REFUSED);

            assertThat(save.ethecoinWei).isEqualTo(before);
            assertThat(Cheats.of(save).anyInForce()).isFalse();
            assertThat(Cheats.of(save).revealed).isFalse();
        }

        @Test
        @DisplayName("cheats already set do not travel with a character that becomes federable")
        void notCarriedAcross() {
            GameSave save = save();
            Cheats.setCycleCeiling(save, 512L, T0);
            Cheats.setThermalRecovery(save, false, T0);
            Cheats.setHeatFrozen(save, true, T0);
            Cheats.setEventChance(save, 0, T0);
            Cheats.setBreachAutoClear(save, true, T0);
            Cheats.setInstantTasks(save, true, T0);
            Cheats.setInstantPurchases(save, true, T0);

            // The state is still in the document — nothing erases it — but every hook must read as
            // untouched, so the character plays by the ordinary rules rather than carrying the
            // overrides into a shared economy.
            save.federable = true;

            assertThat(Cheats.ceiling(save, 48L)).isEqualTo(48L);
            assertThat(Cheats.thermalRecovery(save)).isTrue();
            assertThat(Cheats.heatMayRise(save)).isTrue();
            assertThat(Cheats.breachAutoClear(save)).isFalse();
            assertThat(Cheats.intrusionChance(save, 0.42d)).isEqualTo(0.42d);
            assertThat(Cheats.finishesNow(save, new TaskState())).isFalse();
            assertThat(Cheats.purchasesAreInstant(save)).isFalse();
            assertThat(Cheats.of(save).cycleCeiling).isEqualTo(512L);
        }
    }

    @Nested
    @DisplayName("the compute ceiling")
    class Ceiling {

        @Test
        @DisplayName("survives the reconcile that exists to undo a hand-edited totalCycles")
        void outlivesReconcile(@TempDir Path dir) {
            GameEngine game = GameEngine.open(TestSaves.at(dir.resolve("save.json")), "operator", java.time.Clock.fixed(T0, ZoneOffset.UTC));
            GameSave save = game.state();

            Cheats.setCycleCeiling(save, 512L, T0);
            assertThat(save.rig.totalCycles).isEqualTo(512L);

            // ⚠ The assertion the whole design turns on. reconcile() recomputes the ceiling from the
            // items held and writes it into the rig on every load and after every upgrade; a cheat
            // that had assigned the field would be reverted right here, silently, and the player
            // would report the slider as not working. Negative-tested by writing rig.totalCycles
            // directly instead, which fails on this line.
            ComputeLadder.reconcile(save);
            assertThat(save.rig.totalCycles).isEqualTo(512L);
        }

        @Test
        @DisplayName("clearing hands the rig back to whatever the items actually give")
        void clearing() {
            GameSave save = save();
            ItemState rung = new ItemState();
            rung.itemType = ComputeLadder.rungs().getFirst().itemType();
            rung.tier = StorageTier.VAULT.name();
            save.items.add(rung);
            long ladder = ComputeLadder.rungs().getFirst().capacity();

            Cheats.setCycleCeiling(save, 1000L, T0);
            assertThat(save.rig.totalCycles).isEqualTo(1000L);

            Cheats.setCycleCeiling(save, 0L, T0);
            assertThat(save.rig.totalCycles).isEqualTo(ladder);
            assertThat(Cheats.of(save).cycleCeiling).isZero();
        }

        @Test
        @DisplayName("is clamped, never allowed below a starting rig or past the panel's bound")
        void clamped() {
            GameSave save = save();

            Cheats.setCycleCeiling(save, 1L, T0);
            assertThat(Cheats.of(save).cycleCeiling).isEqualTo(Balance.STARTING_CYCLES);

            Cheats.setCycleCeiling(save, Long.MAX_VALUE, T0);
            assertThat(Cheats.of(save).cycleCeiling).isEqualTo(Cheats.MAX_CYCLE_CEILING);
        }
    }

    @Nested
    @DisplayName("thermal recovery")
    class Thermal {

        @Test
        @DisplayName("off, a released allocation returns its cycles NOW rather than on a later tick")
        void instant() {
            GameSave save = save();
            long before = ComputeRules.availableCycles(save.rig);
            var allocation = ComputeRules.reserve(save.rig, ComputeConsumer.ACTIVE_TOOL, "test", 4L);
            assertThat(ComputeRules.availableCycles(save.rig)).isEqualTo(before - 4L);

            Cheats.setThermalRecovery(save, false, T0);
            var took = ComputeRules.beginRecovery(save, allocation.allocationId, T0);

            assertThat(took).isEqualTo(java.time.Duration.ZERO);
            // ⚠ Not "a zero-length recovery". That would still be a RECOVERING row waiting for the
            // next settleRecovered, so the cycles would come back a tick later — a switch whose
            // effect is a one-second delay instead of no delay. The row has to be gone.
            assertThat(save.rig.allocations).noneMatch(a -> a.allocationId.equals(allocation.allocationId));
            assertThat(ComputeRules.availableCycles(save.rig)).isEqualTo(before);
        }

        @Test
        @DisplayName("on, the curve still applies")
        void curve() {
            GameSave save = save();
            var allocation = ComputeRules.reserve(save.rig, ComputeConsumer.ACTIVE_TOOL, "test", 4L);

            var took = ComputeRules.beginRecovery(save, allocation.allocationId, T0);

            assertThat(took).isNotNull().isGreaterThan(java.time.Duration.ZERO);
            assertThat(save.rig.allocations).anyMatch(a -> "RECOVERING".equals(a.state));
        }
    }

    /**
     * ⚠ Driven through {@link GameEngine}, not through {@link Cheats}, and that is the point.
     *
     * <p>The hook lives in {@code GameEngine.settleTasks} — the one gate every timed thing in the
     * game passes through — so the lowest level this behaviour is visible at is the engine. A unit
     * test of {@code Cheats.finishesNow} on its own would assert that a boolean is true and would
     * have passed just as happily against a build where nothing consulted it, which is the failure
     * shape {@code FootholdAfterBreachTest} exists for: both halves correct, the join missing.
     */
    @Nested
    @DisplayName("instant tasks")
    class InstantTasks {

        private static final long QUICK_SECONDS = GameEngine.ScanTier.QUICK.seconds();

        @Test
        @DisplayName("a scan lands on the next tick instead of after its published duration")
        void skipsTheWait(@TempDir Path dir) {
            Winding clock = new Winding(T0);
            GameEngine game = game(dir, clock);
            Cheats.setInstantTasks(game.state(), true, T0);

            game.scan(GameEngine.ScanTier.QUICK);
            assertThat(game.tasks()).hasSize(1);

            clock.advance(Duration.ofSeconds(1));
            game.tick();

            assertThat(game.tasks()).isEmpty();
        }

        @Test
        @DisplayName("without it, the same scan is still running a second in")
        void otherwiseItWaits(@TempDir Path dir) {
            Winding clock = new Winding(T0);
            GameEngine game = game(dir, clock);

            game.scan(GameEngine.ScanTier.QUICK);
            clock.advance(Duration.ofSeconds(1));
            game.tick();

            assertThat(game.tasks()).hasSize(1);
            // The control on the other side: it is the wait that was skipped above, not the scan
            // being unable to start. Left alone, it finishes when it was always going to.
            clock.advance(Duration.ofSeconds(QUICK_SECONDS));
            game.tick();
            assertThat(game.tasks()).isEmpty();
        }

        /**
         * ⚠ The half that makes this worth having rather than a curiosity: the work still happens.
         * A cheat that deleted the task instead would be one whose visible effect is that the audit
         * never reported and the cycles never came back — {@code solveBreach}'s failure in a new
         * shape, and the player reads it as broken rather than as cheated.
         */
        @Test
        @DisplayName("the scan still reports, and its held cycles are handed back to the budget")
        void theWorkStillHappens(@TempDir Path dir) {
            Winding clock = new Winding(T0);
            GameEngine game = game(dir, clock);
            GameSave save = game.state();
            Cheats.setInstantTasks(save, true, T0);
            int linesBefore = save.log.size();

            var allocation = game.scan(GameEngine.ScanTier.QUICK).orElseThrow();
            clock.advance(Duration.ofSeconds(1));
            game.tick();

            assertThat(save.log.size()).isGreaterThan(linesBefore);
            assertThat(save.log).anyMatch(line -> "scan".equals(line.facility) && line.message.contains("finished"));
            // HELD, not spent: a settled scan hands its allocation to the recovery curve. A task
            // dropped rather than settled would leak the reservation and the rig would shrink by
            // every scan the player ever ran.
            assertThat(save.rig.allocations)
                    .noneMatch(a -> a.allocationId.equals(allocation.allocationId) && "ACTIVE".equals(a.state));
        }

        /**
         * ⚠ Everything downstream of settlement stamps with {@code TaskState.endsAt}, and a task cut
         * short has a deadline in the FUTURE. Leaving it would date the completion log line half a
         * minute ahead and — the one that actually costs the player something — start the Thermal
         * Budget recovery from an instant that has not arrived, so the cycles would sit still until
         * the clock caught up. That is the cheat reinstating exactly the wait it skipped.
         * Negative-tested by removing the bring-forward, which fails on the log stamp.
         */
        @Test
        @DisplayName("a task cut short is settled at NOW, never at the deadline it never reached")
        void settledAtNow(@TempDir Path dir) {
            Winding clock = new Winding(T0);
            GameEngine game = game(dir, clock);
            GameSave save = game.state();
            Cheats.setInstantTasks(save, true, T0);

            game.scan(GameEngine.ScanTier.QUICK);
            Instant tickedAt = T0.plusSeconds(1);
            clock.advance(Duration.ofSeconds(1));
            game.tick();

            assertThat(save.log)
                    .filteredOn(line -> "scan".equals(line.facility) && line.message.contains("finished"))
                    .isNotEmpty()
                    .allMatch(line -> !line.at.isAfter(tickedAt));
        }

        @Test
        @DisplayName("turning it off puts a task already running back on its real clock")
        void reversible(@TempDir Path dir) {
            Winding clock = new Winding(T0);
            GameEngine game = game(dir, clock);
            Cheats.setInstantTasks(game.state(), true, T0);

            game.scan(GameEngine.ScanTier.QUICK);
            Cheats.setInstantTasks(game.state(), false, T0);

            clock.advance(Duration.ofSeconds(1));
            game.tick();

            // Nothing was written to the task, so there is nothing to undo — which is why the switch
            // is a settlement rule rather than a rewrite of `endsAt`.
            assertThat(game.tasks()).hasSize(1);
        }

        /**
         * ⚠ A hold is expressed as a deadline that never arrives — {@code DownloadQueue.settle}
         * pushes both ends of a held transfer's clock forward on every tick. So a rule that ignores
         * deadlines steps straight over the pause, and the player would have two controls with one
         * silently overruling the other.
         */
        @Test
        @DisplayName("a download the player paused is left alone")
        void aHeldDownloadIsExempt() {
            GameSave save = save();
            Cheats.setInstantTasks(save, true, T0);

            TaskState transfer = new TaskState();
            DownloadOrderState order = new DownloadOrderState();
            order.taskId = transfer.taskId;
            order.paused = true;
            save.downloadQueue.add(order);

            assertThat(Cheats.finishesNow(save, transfer)).isFalse();

            order.paused = false;
            assertThat(Cheats.finishesNow(save, transfer)).isTrue();
        }

        @Test
        @DisplayName("is cleared by a reset and by concealing")
        void cleared() {
            GameSave save = save();
            Cheats.setInstantTasks(save, true, T0);
            assertThat(Cheats.of(save).anyInForce()).isTrue();

            Cheats.reset(save, T0);
            assertThat(Cheats.of(save).instantTasks).isFalse();
            assertThat(Cheats.finishesNow(save, new TaskState())).isFalse();
        }

        @Test
        @DisplayName("the purchase switch is cleared by a reset too")
        void purchasesCleared() {
            GameSave save = save();
            Cheats.setInstantPurchases(save, true, T0);
            assertThat(Cheats.purchasesAreInstant(save)).isTrue();
            assertThat(Cheats.of(save).anyInForce()).isTrue();

            Cheats.reset(save, T0);
            assertThat(Cheats.purchasesAreInstant(save)).isFalse();
        }

        private GameEngine game(Path dir, Clock clock) {
            GameEngine game = GameEngine.open(TestSaves.at(dir.resolve("save.json")), "operator", clock);
            // ⚠ The tutorial parasite draws the host's cycles (I6), and a starting rig is 24. Left
            // in, a Quick Scan can be refused outright — and a refused scan looks exactly like an
            // instant one, since both leave the task list empty.
            var rig = game.state().rig;
            for (var miner : java.util.List.copyOf(rig.foreignMiners)) {
                rig.allocations.removeIf(a -> a.allocationId.equals(miner.allocationId));
            }
            rig.foreignMiners.clear();
            return game;
        }
    }

    /** A hand-wound clock; {@link java.time.Clock#fixed} cannot be advanced. */
    private static final class Winding extends Clock {
        private Instant now;

        Winding(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    @Nested
    @DisplayName("personal heat")
    class Heat {

        @Test
        @DisplayName("frozen, an intrusion plants its parasite and charges nothing")
        void frozen() {
            GameSave save = save();
            Cheats.setHeat(save, 20, T0);
            Cheats.setHeatFrozen(save, true, T0);
            int parasitesBefore = save.rig.foreignMiners.size();

            IntrusionRules.plantCounterHack(save, 3, T0);

            assertThat(save.rig.foreignMiners).hasSize(parasitesBefore + 1);
            assertThat(save.personalHeat).isEqualTo(20);
        }

        @Test
        @DisplayName("unfrozen, the same intrusion charges what the depth earns")
        void unfrozen() {
            GameSave save = save();
            Cheats.setHeat(save, 20, T0);

            IntrusionRules.plantCounterHack(save, 3, T0);

            assertThat(save.personalHeat).isEqualTo(20 + Balance.netCounterHackHeat(3));
        }

        @Test
        @DisplayName("is clamped to the same band every rule that raises it clamps to")
        void clamped() {
            GameSave save = save();

            Cheats.setHeat(save, 5000, T0);
            assertThat(save.personalHeat).isEqualTo(Balance.PERSONAL_HEAT_MAX);

            Cheats.setHeat(save, -5, T0);
            assertThat(save.personalHeat).isZero();
        }
    }

    @Nested
    @DisplayName("the intrusion chance")
    class Chance {

        @Test
        @DisplayName("scales, and cannot be pushed outside a probability")
        void scaled() {
            GameSave save = save();

            Cheats.setEventChance(save, 0, T0);
            assertThat(Cheats.intrusionChance(save, 0.5d)).isZero();

            Cheats.setEventChance(save, 200, T0);
            assertThat(Cheats.intrusionChance(save, 0.2d)).isEqualTo(0.4d);

            // A chance already high, scaled up, must saturate at certainty rather than exceed it —
            // the comparison would still work, but so would a negative percentage, which would
            // silently make the roll impossible rather than certain.
            Cheats.setEventChance(save, Cheats.MAX_EVENT_CHANCE_PERCENT, T0);
            assertThat(Cheats.intrusionChance(save, 0.9d)).isEqualTo(1.0d);

            Cheats.setEventChance(save, -100, T0);
            assertThat(Cheats.of(save).eventChancePercent).isZero();
        }
    }

    @Nested
    @DisplayName("ethecoin")
    class Money {

        @Test
        @DisplayName("is granted without writing a ledger row")
        void noLedgerRow() {
            GameSave save = save();
            int rowsBefore = save.ledger.size();

            Cheats.grant(save, Ethecoin.ofWholeEthecoin(500L).wei(), T0);

            assertThat(save.ethecoinWei).isEqualTo(Ethecoin.ofWholeEthecoin(500L).wei());
            // ⚠ The ledger is the chain's record of value moving between addresses, and this money
            // did not move — it was invented. A row for it would be a transaction with no
            // counterparty, which is the one thing a block explorer must never show.
            assertThat(save.ledger).hasSize(rowsBefore);
        }

        @Test
        @DisplayName("can be set back down, which a grant alone cannot do")
        void setDown() {
            GameSave save = save();
            Cheats.grant(save, Ethecoin.ofWholeEthecoin(500L).wei(), T0);

            Cheats.setBalance(save, BigInteger.ZERO, T0);

            assertThat(save.ethecoinWei).isEqualTo(BigInteger.ZERO);
        }
    }

    @Nested
    @DisplayName("the page's own visibility")
    class Revealed {

        @Test
        @DisplayName("is pinned by USING a cheat, and reset does not take it away")
        void pinned() {
            GameSave save = save();
            assertThat(Cheats.of(save).revealed).isFalse();

            Cheats.setThermalRecovery(save, false, T0);
            assertThat(Cheats.of(save).revealed).isTrue();

            // ⚠ Turning the last cheat off must not take away the page that turned it off. A player
            // who reset and then wanted one back would have to find the key sequence again, and the
            // sequence is precisely the thing nobody remembers.
            Cheats.reset(save, T0);
            assertThat(Cheats.of(save).revealed).isTrue();
            assertThat(Cheats.of(save).anyInForce()).isFalse();
        }

        /**
         * ⚠ Concealing resets as well, and that is one act rather than two. Hiding while leaving an
         * override in force creates precisely the state {@code revealed} was added to prevent — a
         * permanently altered character with nothing on screen to say why.
         */
        @Test
        @DisplayName("concealing turns everything off as well as hiding")
        void concealResets() {
            GameSave save = save();
            Cheats.setCycleCeiling(save, 512L, T0);
            Cheats.setThermalRecovery(save, false, T0);
            Cheats.setHeatFrozen(save, true, T0);

            Cheats.conceal(save, T0);

            assertThat(Cheats.of(save).revealed).isFalse();
            assertThat(Cheats.of(save).anyInForce()).isFalse();
            assertThat(Cheats.thermalRecovery(save)).isTrue();
            assertThat(Cheats.ceiling(save, 48L)).isEqualTo(48L);
        }

        /**
         * ⚠ The secrecy rule, and the only place it is mechanical. Concealing is the one action here
         * a player can take on a character that was never altered, and a WARNING line naming this
         * facility — written by the act of tidying it away — would be the single place the game
         * admits the feature exists to somebody who has not used it. Verified against a `reset` that
         * logs unconditionally, which fails here.
         */
        @Test
        @DisplayName("concealing an untouched character leaves NO trace in the rig log")
        void concealLeavesNoTrace() {
            GameSave save = save();
            int linesBefore = save.log.size();

            Cheats.conceal(save, T0);

            assertThat(save.log).hasSize(linesBefore);
            assertThat(save.log).noneMatch(line -> "cheat".equals(line.facility));
        }

        @Test
        @DisplayName("but a character that WAS altered keeps its record")
        void alteredKeepsItsRecord() {
            GameSave save = save();
            Cheats.setHeatFrozen(save, true, T0);

            Cheats.conceal(save, T0);

            assertThat(save.log).anyMatch(line -> "cheat".equals(line.facility));
        }
    }

    @Nested
    @DisplayName("the network reveal")
    class Reveal {

        @Test
        @DisplayName("discovers and names every machine, grants no footholds, and is idempotent")
        void reveal(@TempDir Path dir) {
            GameEngine game = GameEngine.open(TestSaves.at(dir.resolve("save.json")), "operator", java.time.Clock.fixed(T0, ZoneOffset.UTC));
            GameSave save = game.state();
            long total = save.topology.hosts.stream()
                    .filter(h -> !h.address.equals(save.topology.playerAddress))
                    .count();

            String said = Cheats.revealNetwork(save, T0);

            assertThat(said).contains("added to the map");
            assertThat(save.topology.hosts)
                    .filteredOn(h -> !h.address.equals(save.topology.playerAddress))
                    .allMatch(h -> h.discovered);
            assertThat(save.knownNodes).hasSize((int) total);
            // ⚠ NAMED IS ASSERTED ON `NodeState.kind`, NOT ON `HostState.identified` (2026-08-09).
            // The property is unchanged and the field carrying it moved: "the player has established
            // what this machine is" is the player's knowledge, so it belongs on the knownNodes row
            // that the map AND `Targets.role` read. The old flag reached the map only, so a revealed
            // machine drew as TERM there and blank in the breach window. Asserting the row is also
            // the stronger claim — it is what a screen actually renders from.
            assertThat(save.knownNodes)
                    .as("every revealed machine is typed")
                    .isNotEmpty()
                    .allMatch(n -> !"UNKNOWN".equals(n.kind));
            // ⚠ EVERY BRIDGE IS BREACHED, and nothing else is — 2026-08-09, on explicit direction.
            // A server reaches the map's tab strip by having a breached bridge pointing at it, so a
            // reveal that granted no footholds would leave every tab but home missing, which is not
            // a revealed map. Ordinary machines still have to be broken into.
            assertThat(save.topology.hosts)
                    .filteredOn(h -> "BRIDGE".equals(h.kind))
                    .isNotEmpty()
                    .allMatch(h -> h.foothold);
            assertThat(save.topology.hosts)
                    .filteredOn(h -> !"BRIDGE".equals(h.kind) && !"SELF".equals(h.kind))
                    .filteredOn(h -> h.foothold)
                    .as("an ordinary machine is not breached by a reveal")
                    .isEmpty();
            // ⚠ `looted` stays false whatever else changes: it is a one-time payout, and
            // reconcileFootholds would otherwise credit every host in the world at once.
            assertThat(save.topology.hosts).noneMatch(h -> h.looted);

            int rows = save.knownNodes.size();
            Cheats.revealNetwork(save, T0);
            assertThat(save.knownNodes).hasSize(rows);
        }

        /**
         * ⚠ A REVEAL MUST GRANT EVERY MACHINE WHAT IT GRANTS ANY MACHINE, and this shipped wrong.
         *
         * <p>A machine found by an ordinary sweep is {@code discovered} and usually not
         * {@code identified} — a sweep sells existence, the Passive Sniffer sells identity. The
         * reveal skipped anything already discovered, so those stayed anonymous: on a render, the
         * swept machines drew as {@code ----} while the reveal-found ones drew as TERM/STOR/RELA, on
         * the same map, after a "reveal the whole map". The cheat did less the more of the game you
         * had played — the same shape the foothold half was fixed for.
         */
        @Test
        @DisplayName("identifies machines that were ALREADY discovered, not just new ones")
        void identifiesWhatWasAlreadyFound(@TempDir Path dir) {
            GameEngine game = GameEngine.open(
                    TestSaves.at(dir.resolve("save.json")), "operator", java.time.Clock.fixed(T0, ZoneOffset.UTC));
            GameSave save = game.state();
            var alreadyFound = save.topology.hosts.stream()
                    .filter(h -> !h.address.equals(save.topology.playerAddress))
                    .findFirst()
                    .orElseThrow();
            alreadyFound.discovered = true;
            save.knownNodes.add(new io.github.stoicswe.eyeandsickle.engine.state.NodeState());
            save.knownNodes.getLast().address = alreadyFound.address;
            // What an ordinary sweep leaves behind: a row, and no idea what the machine is.
            save.knownNodes.getLast().kind = "UNKNOWN";

            Cheats.revealNetwork(save, T0);

            // ⚠ Asserted on the knownNodes row rather than on `HostState.identified`, which stopped
            // being the answer on 2026-08-09 — see the reveal test above. The property this test
            // exists for is untouched: the cheat must not do less the more of the game you have
            // played.
            assertThat(save.knownNodes.stream()
                            .filter(n -> alreadyFound.address.equals(n.address))
                            .findFirst()
                            .orElseThrow()
                            .kind)
                    .isNotEqualTo("UNKNOWN");
            // ⚠ …and still exactly one row for it. knownNodes is a list, so a second row is a machine
            // drawn twice — which is why that one line stays inside the guard.
            assertThat(save.knownNodes.stream().filter(n -> alreadyFound.address.equals(n.address)))
                    .hasSize(1);
        }
    }

    @Nested
    @DisplayName("gaining all info on a machine")
    class LearnEverything {

        @Test
        @DisplayName("fills every storable rung of the recon file")
        void fillsTheFile(@TempDir Path dir) {
            GameEngine game = GameEngine.open(
                    TestSaves.at(dir.resolve("save.json")), "operator", java.time.Clock.fixed(T0, ZoneOffset.UTC));
            GameSave save = game.state();
            Cheats.revealNetwork(save, T0);

            Cheats.learnEverything(save, T0);

            var host = save.topology.hosts.stream()
                    .filter(h -> !h.address.equals(save.topology.playerAddress))
                    .filter(h -> !"BRIDGE".equals(h.kind))
                    .findFirst()
                    .orElseThrow();
            // ⚠ `known` is the fraction the RECON panel shows, and it counts only the rungs that
            // APPLY to this machine's kind — which is why the fixture takes a non-bridge. A bridge
            // cannot reach 1.0: PEERS and MONITORED have no field to be stored in (design/17 PS-4).
            assertThat(io.github.stoicswe.eyeandsickle.engine.net.NodeReports.known(save, host.address))
                    .isEqualTo(1.0d);

            var report = io.github.stoicswe.eyeandsickle.engine.net.NodeReports.find(save, host.address)
                    .orElseThrow();
            assertThat(report.hostName).isNotBlank();
            assertThat(report.firewallTier).isNotNegative();
            assertThat(report.osName).isNotBlank();
            assertThat(report.cyclesTotal).isPositive();
            assertThat(report.vaultMediumEstimate).isNotNegative();
        }

        /**
         * ⚠ It is not a scan, so it must not claim one. A file reporting scans nobody ran puts a
         * detection ratio beside it that is a fraction of a number that never happened —
         * {@code establishIdentity} declines to bump the counter for the same reason.
         */
        @Test
        @DisplayName("does not count as a scan and does not touch the detection tally")
        void isNotAScan(@TempDir Path dir) {
            GameEngine game = GameEngine.open(
                    TestSaves.at(dir.resolve("save.json")), "operator", java.time.Clock.fixed(T0, ZoneOffset.UTC));
            GameSave save = game.state();
            Cheats.revealNetwork(save, T0);

            Cheats.learnEverything(save, T0);

            assertThat(save.nodeReports).isNotEmpty();
            assertThat(save.nodeReports).allMatch(report -> report.scans == 0 && report.detections == 0);
        }

        /**
         * ⚠ A BRIDGE must read as fully learned too, and it cannot reach {@code known() == 1.0}.
         *
         * <p>Its ladder includes {@code PEERS} and {@code MONITORED}, which the recon file has
         * nowhere to store ({@code design/17} §8 PS-4) — so any control asking "is there anything
         * left to learn" via the fraction answers <em>yes, forever</em>, for every bridge in the
         * world. Measured at 14 on a revealed map before this was fixed. {@code fullyLearned} is the
         * answerable form of the question and is what both the fill and the count use.
         */
        @Test
        @DisplayName("a bridge reads as fully learned even though its fraction cannot reach 1.0")
        void bridgesFinish(@TempDir Path dir) {
            GameEngine game = GameEngine.open(
                    TestSaves.at(dir.resolve("save.json")), "operator", java.time.Clock.fixed(T0, ZoneOffset.UTC));
            GameSave save = game.state();
            Cheats.revealNetwork(save, T0);
            Cheats.learnEverything(save, T0);

            var bridges = save.topology.hosts.stream()
                    .filter(h -> "BRIDGE".equals(h.kind))
                    .toList();
            assertThat(bridges).as("the fixture needs at least one bridge").isNotEmpty();

            for (var bridge : bridges) {
                assertThat(io.github.stoicswe.eyeandsickle.engine.net.NodeReports.fullyLearned(save, bridge))
                        .as("bridge " + bridge.address + " must count as finished")
                        .isTrue();
                assertThat(io.github.stoicswe.eyeandsickle.engine.net.NodeReports.known(save, bridge.address))
                        .as("…while its published fraction still cannot reach 1.0")
                        .isLessThan(1.0d);
            }
        }

        /**
         * ⚠ Discovered machines only. A recon file on a machine the map has never heard of would
         * appear in RECON as a report about something absent from the map — the one surface that must
         * never hint at what has not been discovered.
         */
        @Test
        @DisplayName("touches only machines already on the map")
        void mapOnly(@TempDir Path dir) {
            GameEngine game = GameEngine.open(
                    TestSaves.at(dir.resolve("save.json")), "operator", java.time.Clock.fixed(T0, ZoneOffset.UTC));
            GameSave save = game.state();
            long undiscovered = save.topology.hosts.stream()
                    .filter(h -> !h.discovered)
                    .count();
            assertThat(undiscovered).as("the fixture needs machines still off the map").isPositive();

            Cheats.learnEverything(save, T0);

            for (var host : save.topology.hosts) {
                if (!host.discovered) {
                    assertThat(io.github.stoicswe.eyeandsickle.engine.net.NodeReports.any(save, host.address))
                            .as("no file for a machine that is not on the map: " + host.address)
                            .isFalse();
                }
            }
        }
    }

    /**
     * The manual attempt — {@code Cheats.triggerReprisal}.
     *
     * <h2>⚠ What this is FOR, which is not the same as what it does</h2>
     *
     * It exists so the defence loop can be felt on demand. Every other route into
     * {@link io.github.stoicswe.eyeandsickle.engine.net.ReprisalRules} runs off being detected during
     * a real port scan, which a tester cannot arrange: the detection is rolled at commission and
     * frozen, so there is no way to make one happen. The theft arm in particular was unreachable by
     * hand.
     */
    @Nested
    @DisplayName("rolling a machine's answer by hand")
    class Reprisal {

        private GameEngine engine(Path dir) {
            return GameEngine.open(
                    TestSaves.at(dir.resolve("save.json")), "operator", Clock.fixed(T0, ZoneOffset.UTC));
        }

        /**
         * ⚠ Asserted over MANY presses, on the distribution rather than on one outcome. The rule is
         * weighted heavily toward "noticed and let it go", so a single press asserting a parasite
         * appeared would fail four times in five — a test that reports a defect that is not there.
         *
         * <p>⚠ <b>The tutorial parasite has to be cleared first, and finding that out is what this
         * test was for.</b> {@code ReprisalRules.plant} refuses outright while the rig already
         * carries a foreign miner — "one at a time", deliberately — and every new character is issued
         * one. So on a fresh rig the planting arm is <em>unreachable</em>: two hundred presses land
         * zero miners and report "somebody had already been", which reads as the button not working.
         * That is the state a tester will actually meet, and it is recorded on the panel rather than
         * fixed here: cracking the parasite off first is the point of the exercise.
         */
        @Test
        @DisplayName("reaches the planting arm once the rig is clean")
        void rollsTheRealDistribution(@TempDir Path dir) {
            GameEngine game = engine(dir);
            GameSave save = game.state();
            save.rig.foreignMiners.clear();

            for (int i = 0; i < 200; i++) {
                assertThat(Cheats.triggerReprisal(save, T0))
                        .as("press %d", i)
                        .isNotEqualTo(Cheats.REFUSED);
            }

            assertThat(save.rig.foreignMiners)
                    .as("~5% of 200 presses plant one, so some did")
                    .isNotEmpty();
        }

        /**
         * ⚠ The one-at-a-time rule, from the panel's side. Not a defect and not worked around — a rig
         * that already has a parasite gets a probe and nothing else, which is the rule doing its job.
         */
        @Test
        @DisplayName("a rig that already has a parasite is only probed")
        void oneAtATime(@TempDir Path dir) {
            GameEngine game = engine(dir);
            GameSave save = game.state();
            assertThat(save.rig.foreignMiners).as("a new character is issued one").isNotEmpty();

            for (int i = 0; i < 200; i++) {
                Cheats.triggerReprisal(save, T0);
            }

            assertThat(save.rig.foreignMiners).as("still exactly the one it started with").hasSize(1);
        }

        /**
         * ⚠ The attacker is a machine that exists in the world. {@code ReprisalRules} writes the
         * address into the rig log and the access log, and an address belonging to no machine is
         * evidence pointing at nothing.
         */
        @Test
        @DisplayName("names a real machine as the source")
        void theAttackerIsReal(@TempDir Path dir) {
            GameEngine game = engine(dir);
            GameSave save = game.state();

            String said = Cheats.triggerReprisal(save, T0);

            String address = said.substring(said.indexOf("from ") + 5, said.indexOf(':', said.indexOf("from ")));
            assertThat(save.topology.hosts.stream().map(h -> h.address))
                    .as("%s is a machine in this world", address)
                    .contains(address);
        }

        /**
         * ⚠ The whole safety argument for the facility, restated at the one call that reaches out and
         * changes the rig. A federable character has a route to a server; a cheat that could touch one
         * would be forged authoritative state — <b>I14</b>.
         */
        @Test
        @DisplayName("refused on a federable character, like every other cheat")
        void soloOnly(@TempDir Path dir) {
            GameEngine game = engine(dir);
            GameSave save = game.state();
            save.rig.foreignMiners.clear();
            save.federable = true;

            assertThat(Cheats.triggerReprisal(save, T0)).isEqualTo(Cheats.REFUSED);
            assertThat(save.rig.foreignMiners).isEmpty();
        }

        /**
         * ⚠ A press must consume exactly the draws the rule consumes and commit them. An uncommitted
         * draw is replayed on the next load — the same value, forever — which is the "reload to reroll"
         * failure {@code Rng} exists to prevent, arriving through a developer control.
         */
        @Test
        @DisplayName("commits its draw, so two presses are not the same press")
        void theSeedMoves(@TempDir Path dir) {
            GameEngine game = engine(dir);
            GameSave save = game.state();
            long before = save.rngSeed;

            Cheats.triggerReprisal(save, T0);

            assertThat(save.rngSeed).isNotEqualTo(before);
        }
    }
}
