package io.github.stoicswe.eyeandsickle.engine.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.Catalogue;
import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import io.github.stoicswe.eyeandsickle.engine.save.TestSaves;
import io.github.stoicswe.eyeandsickle.engine.state.BotState;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.HostState;
import io.github.stoicswe.eyeandsickle.engine.state.ItemState;
import io.github.stoicswe.eyeandsickle.protocol.game.BotFunction;
import io.github.stoicswe.eyeandsickle.protocol.game.BotModifier;
import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import io.github.stoicswe.eyeandsickle.protocol.game.UnlockGate;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The botnet — {@code docs/design/10-botnets.md}.
 *
 * <p>Most of what is asserted here is an <b>invariant</b> rather than a behaviour, because that is
 * where this system's failure modes are: every one of them renders correctly on screen while being
 * wrong. A Sipper that mints ethecoin, an Injector that lets a bot buy compute, a level ladder that
 * money alone can climb — none of those would show up as a broken screen or a thrown exception.
 */
@DisplayName("bots")
class BotnetTest {

    private static final Instant T0 = Instant.parse("2026-08-11T09:00:00Z");

    // ================================================================== the invariants

    @Nested
    @DisplayName("the gates")
    class Gates {

        @Test
        @DisplayName("exactly one chassis rung has a price, and it is the first")
        void onlyTheFirstFrameIsForSale() {
            // ⚠ This is the whole safety argument for putting the botnet on the money gate at all —
            // docs/design/10 §2.0. One rung cannot compound: money moves a player up ONCE. A second
            // priced rung means the argument has been abandoned rather than amended, and it should
            // be a red build rather than a conversation nobody had. Same shape as
            // ComputeLadderTest.onlyTheFirstRungIsForSale.
            for (int tier = 1; tier <= Balance.BOT_FRAME_TIER_MAX; tier++) {
                var frame = Catalogue.byId(Catalogue.botFrameId(tier)).orElseThrow();
                if (tier == 1) {
                    assertThat(frame.gate()).isEqualTo(UnlockGate.ETHECOIN);
                    assertThat(frame.priceWei()).isEqualTo(Balance.BOT_FRAME_V1_PRICE);
                } else {
                    assertThat(frame.gate())
                            .as("v%d must not be purchasable", tier)
                            .isNotEqualTo(UnlockGate.ETHECOIN);
                    assertThat(frame.priceWei())
                            .as("v%d must have no price at all", tier)
                            .isEqualTo(BigInteger.ZERO);
                }
            }
        }

        @Test
        @DisplayName("the Injector is never purchasable, because it hands out compute")
        void theInjectorIsNotForSale() {
            // Compute is the master scarcity. An ethecoin-gated Injector is ethecoin buying capacity
            // — Invariant I1 with extra steps — and unlike the compute ladder's amended first rung,
            // this one would COMPOUND: the cycles it frees are cycles that can run more bots.
            var injector = Catalogue.byId(Catalogue.BOT_FN_INJECTOR).orElseThrow();
            assertThat(injector.gate()).isEqualTo(UnlockGate.SCHEMATIC);
            assertThat(injector.priceWei()).isEqualTo(BigInteger.ZERO);
        }

        @Test
        @DisplayName("a function level needs schematic material, so money alone cannot climb it")
        void moneyAloneCannotLevelAFunction(@TempDir Path dir) {
            GameEngine game = withBot(dir, BotFunction.KEYLOGGER);
            GameSave save = game.state();
            // Rich, and no material. This is the exact state a player who has only ever mined is in,
            // and I2 requires it to be a refusal: a level is a ceiling.
            LedgerRules.apply(save, Balance.ec("100000"), "test", "rich", T0);
            save.schematicMaterial = 0;
            var refusal = Botnet.canLevel(save, save.bots.getFirst().botId, BotFunction.KEYLOGGER);
            assertThat(refusal.ok()).isFalse();
            assertThat(refusal.message())
                    .as("the refusal must name the MATERIAL, or the player goes and mines")
                    .contains("material");
        }

        @Test
        @DisplayName("a modifier level is ethecoin only — modifiers are horizontal")
        void aModifierLevelIsMoneyOnly(@TempDir Path dir) {
            // The asymmetry with the test above is the gate rule, not an oversight: a function's
            // ladder is a ceiling (I2), a modifier is horizontal and design/02 §1.1 puts horizontal
            // options on the money gate. Charging material would make surviving compete with
            // progressing for the same scarce thing.
            GameEngine game = withBot(dir, BotFunction.KEYLOGGER);
            GameSave save = game.state();
            // ⚠ Recalled first. A running bot's loadout is fixed — see Loadout.noHotSwapping for why
            // that refusal is load-bearing rather than fussy.
            game.recallBot(save.bots.getFirst().botId);
            save.bots.getFirst().frameTier = 2;
            fit(game, Catalogue.BOT_MOD_DAMPENER, save.bots.getFirst().botId);
            LedgerRules.apply(save, Balance.ec("100000"), "test", "rich", T0);
            save.schematicMaterial = 0;
            assertThat(Botnet.canLevelModifier(save, save.bots.getFirst().botId, BotModifier.DAMPENER)
                            .ok())
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("the economy")
    class Economy {

        @Test
        @DisplayName("a maxed Sipper cannot out-earn the income floor by more than the risk multiple")
        void theSipperCannotOutEarnTheFloor() {
            // ⚠ DERIVED FROM THE CONSTANTS, never restating BOT_SIPPER_MAX_WEI_PER_HOUR. A re-tune
            // of self-mining income has to move this assertion with it, which is the whole point:
            // the Sipper is bounded RELATIVE to the floor (I4), not at an absolute number somebody
            // once liked.
            BigInteger floorPerHour = Balance.SELF_MINING_WEI_PER_CYCLE_HOUR.multiply(
                    BigInteger.valueOf(Balance.BOT_FRAME_CONTROL_CYCLES[1]));
            BigInteger ceiling = new BigDecimal(floorPerHour)
                    .multiply(BigDecimal.valueOf(Balance.BOT_INCOME_RISK_MULTIPLE))
                    .toBigInteger();
            assertThat(Balance.BOT_SIPPER_MAX_WEI_PER_HOUR)
                    .as("a risked, noisy, losable asset may beat the safe floor — but not by much")
                    .isLessThanOrEqualTo(ceiling);
        }

        @Test
        @DisplayName("the Sipper's hourly ceiling binds however rich the host is")
        void theSipperIsCappedNotProportional(@TempDir Path dir) {
            // ⚠ THE FAILURE THIS CATCHES IS A PRINTER, AND IT RENDERS PERFECTLY WHILE IT PRINTS.
            // HostActivity is derived from (address, slot), so the taxed stream is invented and
            // unbounded. Without the ceiling, income scales with the host's tier without limit.
            GameEngine game = withBot(dir, BotFunction.SIPPER);
            GameSave save = game.state();
            BotState bot = save.bots.getFirst();
            var fn = bot.function(BotFunction.SIPPER.name());
            fn.level = Balance.BOT_LEVEL_MAX;
            HostState host = hostOf(save, bot.hostAddress);
            host.tier = 5;

            Botnet.settle(save, T0.plus(Duration.ofHours(1)));

            assertThat(fn.bufferedWei)
                    .as("an hour on the richest machine in the game is still one hour's ceiling")
                    .isLessThanOrEqualTo(Balance.BOT_SIPPER_MAX_WEI_PER_HOUR);
        }

        @Test
        @DisplayName("bot mining is capped across an absence, not proportional to it (I5)")
        void offlineMiningIsCapped(@TempDir Path dir) {
            GameEngine shortAway = withBot(dir.resolve("a"), BotFunction.MINER);
            GameEngine longAway = withBot(dir.resolve("b"), BotFunction.MINER);

            Botnet.settle(shortAway.state(), T0.plus(Duration.ofHours(5)));
            Botnet.settle(longAway.state(), T0.plus(Duration.ofDays(30)));

            BigInteger five = shortAway.state().bots.getFirst().functions.getFirst().bufferedWei;
            BigInteger thirty = longAway.state().bots.getFirst().functions.getFirst().bufferedWei;
            assertThat(thirty)
                    .as("thirty days must not out-earn five hours — I5's cap, not a rate")
                    .isEqualTo(five);
        }
    }

    @Nested
    @DisplayName("compute")
    class Compute {

        @Test
        @DisplayName("a live bot holds a control channel on the player's own rig")
        void aLiveBotHoldsCycles(@TempDir Path dir) {
            // design/10 §3: this reservation is the ONLY cap on botnet size, and §3 says explicitly
            // that no bot-count limit is needed and none should be added.
            GameEngine game = withBot(dir, BotFunction.KEYLOGGER);
            long held = game.state().rig.allocations.stream()
                    .filter(a -> ComputeConsumer.BOT_FRAME.name().equals(a.consumer))
                    .mapToLong(a -> a.cycles)
                    .sum();
            assertThat(held).isEqualTo(Balance.BOT_FRAME_CONTROL_CYCLES[1]);
        }

        @Test
        @DisplayName("offload never reaches mining, at any capacity")
        void offloadNeverReachesMining(@TempDir Path dir) {
            // ⚠ THE FLYWHEEL I1 EXISTS TO PREVENT. Offloaded cycles that could mine would close the
            // loop: mine, buy a bot, offload, mine faster, buy more bots. The exclusion is enforced
            // at the reservation by consumer, and this is what keeps it enforced.
            GameEngine game = withBot(dir, BotFunction.INJECTOR);
            GameSave save = game.state();
            save.bots.getFirst().function(BotFunction.INJECTOR.name()).injectorInstalled = true;
            Botnet.reconcileOffload(save);
            assertThat(save.rig.offloadedCycles).isGreaterThan(0);

            // Fill the rig, so anything that succeeds can only have come from the offload.
            long free = ComputeRules.availableCycles(save.rig);
            ComputeRules.reserve(save.rig, ComputeConsumer.DEFENSIVE_ARRAY, "ballast", free);
            assertThat(ComputeRules.availableCycles(save.rig)).isZero();

            assertThat(ComputeRules.reserve(save.rig, ComputeConsumer.SELF_MINING, "mine", 1))
                    .as("mining may never draw on a bot's offload")
                    .isNull();
            assertThat(ComputeRules.reserve(save.rig, ComputeConsumer.ACTIVE_TOOL, "tool", 1))
                    .as("a tool may")
                    .isNotNull();
        }

        @Test
        @DisplayName("offloaded cycles are absent from the rig's own budget")
        void offloadIsNotOnThisRig(@TempDir Path dir) {
            // ⚠ ComputeBudget's constructor REJECTS over-reconciliation, so a row for cycles the rig
            // does not own would throw — and the readout design/04 §3.1 asks the player to reconcile
            // would stop reconciling. The BOTNET window is where these are legible.
            GameEngine game = withBot(dir, BotFunction.INJECTOR);
            GameSave save = game.state();
            save.bots.getFirst().function(BotFunction.INJECTOR.name()).injectorInstalled = true;
            Botnet.reconcileOffload(save);
            long before = ComputeRules.availableCycles(save.rig);
            ComputeRules.reserve(save.rig, ComputeConsumer.DEFENSIVE_ARRAY, "ballast", before);
            ComputeRules.reserve(save.rig, ComputeConsumer.ACTIVE_TOOL, "borrowed", 2);

            assertThat(ComputeRules.availableCycles(save.rig))
                    .as("borrowing 2 cycles from a bot must not make this rig busier")
                    .isZero();
            assertThat(ComputeRules.offloadInUse(save.rig)).isEqualTo(2);
            // The snapshot must still build, which is the assertion that would have thrown.
            assertThat(ComputeRules.snapshot(save)).isNotNull();
        }

        @Test
        @DisplayName("recalling a bot hands its cycles straight back")
        void recallReleases(@TempDir Path dir) {
            GameEngine game = withBot(dir, BotFunction.KEYLOGGER);
            long before = ComputeRules.availableCycles(game.state().rig);
            game.recallBot(game.state().bots.getFirst().botId);
            assertThat(ComputeRules.availableCycles(game.state().rig))
                    .as("released, not put on the recovery curve — a channel holds, it does not work")
                    .isEqualTo(before + Balance.BOT_FRAME_CONTROL_CYCLES[1]);
        }
    }

    @Nested
    @DisplayName("the loadout")
    class Loadout {

        @Test
        @DisplayName("an empty frame cannot be uploaded anywhere")
        void anEmptyFrameGoesNowhere(@TempDir Path dir) {
            // §2.1's opening statement: the chassis is not the capability.
            GameEngine game = engine(dir);
            GameSave save = game.state();
            grant(save, Catalogue.BOT_FRAME_V1);
            game.buildBot(save.items.getLast().itemId);
            String address = footholdAddress(save);
            var refusal = game.uploadBot(save.bots.getFirst().botId, address);
            assertThat(refusal.ok()).isFalse();
            assertThat(refusal.message()).contains("module");
        }

        @Test
        @DisplayName("a v1 has no modifier socket at all")
        void aV1TakesNoModifier(@TempDir Path dir) {
            GameEngine game = engine(dir);
            GameSave save = game.state();
            grant(save, Catalogue.BOT_FRAME_V1);
            game.buildBot(save.items.getLast().itemId);
            grant(save, Catalogue.BOT_MOD_PROTECTOR);
            var refusal = game.fitBotModifier(save.bots.getFirst().botId, save.items.getLast().itemId);
            assertThat(refusal.ok()).isFalse();
            assertThat(refusal.message()).contains("v2");
        }

        @Test
        @DisplayName("the socket counts on the shelf are the socket counts the rules enforce")
        void theShopAndTheRulesAgree() {
            // ⚠ Ten rungs written out by hand is ten places for the description to disagree with the
            // rule, invisibly — the shop would describe one thing and the engine enforce another
            // with every screen rendering perfectly. The catalogue generates them from these tables,
            // and this is what holds that.
            for (int tier = 1; tier <= Balance.BOT_FRAME_TIER_MAX; tier++) {
                assertThat(Botnet.slotsFor(tier)).isEqualTo(Balance.BOT_FRAME_FUNCTIONS[tier]);
                assertThat(Botnet.modifierSlotsFor(tier)).isEqualTo(Balance.BOT_FRAME_MODIFIERS[tier]);
                assertThat(Catalogue.botFrameTier(Catalogue.botFrameId(tier))).isEqualTo(tier);
            }
        }

        @Test
        @DisplayName("a running bot's loadout is fixed")
        void noHotSwapping(@TempDir Path dir) {
            // Allowing it would let a player fit a Protector onto a bot they had just been told was
            // discovered, or pull a module out the instant they were warned — the loss rule made
            // optional, and §4's third cost made free.
            GameEngine game = withBot(dir, BotFunction.KEYLOGGER);
            GameSave save = game.state();
            grant(save, Catalogue.BOT_FN_MINER);
            var refusal = game.socketBot(save.bots.getFirst().botId, save.items.getLast().itemId);
            assertThat(refusal.ok()).isFalse();
            assertThat(refusal.message()).contains("recall");
        }
    }

    @Nested
    @DisplayName("being found")
    class Found {

        @Test
        @DisplayName("removal empties the sockets and damages an ordinary chassis")
        void removalCostsTheLoadout(@TempDir Path dir) {
            GameEngine game = withBot(dir, BotFunction.KEYLOGGER);
            GameSave save = game.state();
            BotState bot = save.bots.getFirst();
            bot.frameTier = 1;

            // Walk far enough forward that the per-hour discovery and the removal both land.
            for (int day = 1; day <= 60 && bot.live(); day++) {
                Botnet.settle(save, T0.plus(Duration.ofDays(day)));
            }

            assertThat(bot.live()).as("sixty days is long enough to be noticed").isFalse();
            assertThat(bot.functions).isEmpty();
            assertThat(bot.damaged).as("a v1 comes home damaged").isTrue();
            assertThat(save.bots).as("removal is not destruction — the chassis is kept").contains(bot);
        }

        @Test
        @DisplayName("a resilient chassis comes home intact — and still empty")
        void resilientTiersSurviveRemoval(@TempDir Path dir) {
            // ⚠ The sockets empty at EVERY tier, resilient included. That is what keeps §4's third
            // cost real: the modules are the expensive half, and a chassis that came back loaded
            // would make being caught nearly free.
            GameEngine game = withBot(dir, BotFunction.KEYLOGGER);
            GameSave save = game.state();
            BotState bot = save.bots.getFirst();
            bot.frameTier = 6;
            assertThat(Botnet.resilient(6)).isTrue();

            for (int day = 1; day <= 60 && bot.live(); day++) {
                Botnet.settle(save, T0.plus(Duration.ofDays(day)));
            }

            assertThat(bot.live()).isFalse();
            assertThat(bot.damaged).as("v6 does not become damaged").isFalse();
            assertThat(bot.functions).as("but it always loses what was in it").isEmpty();
        }

        @Test
        @DisplayName("stealth modifiers reduce discovery and can never reach zero")
        void stealthIsNeverTotal() {
            // A bot that could never be found is a bot that can never be lost, and §1a's total loss
            // is what §4's whole "botnets are risk" argument rests on.
            double best = Balance.BOT_SCRAMBLER_DISCOVERY_FACTOR
                    * Balance.BOT_SLEEPY_DISCOVERY_FACTOR[Balance.BOT_MODIFIER_LEVEL_MAX];
            assertThat(best).isGreaterThan(0.0d).isLessThan(1.0d);
        }

        @Test
        @DisplayName("the Dampener never silences a bot")
        void dampeningHasAFloor() {
            // §1 pools all bot noise into the player's aggregate — "more bots, louder you". A
            // modifier that reached zero would make a fully dampened network free reach.
            assertThat(Balance.BOT_DAMPENER_NOISE_SHARE[Balance.BOT_MODIFIER_LEVEL_MAX])
                    .isGreaterThanOrEqualTo(0.05d);
        }

        @Test
        @DisplayName("a live bot is louder than no bot, however dampened")
        void aLiveBotIsAlwaysAudible(@TempDir Path dir) {
            GameEngine game = withBot(dir, BotFunction.KEYLOGGER);
            GameSave save = game.state();
            save.bots.getFirst().frameTier = 3;
            var mod = new io.github.stoicswe.eyeandsickle.engine.state.BotModifierState();
            mod.modifier = BotModifier.DAMPENER.name();
            mod.level = Balance.BOT_MODIFIER_LEVEL_MAX;
            save.bots.getFirst().modifiers.add(mod);
            assertThat(Botnet.noiseCycles(save)).isGreaterThan(0L);
        }

        @Test
        @DisplayName("a Protector's block resets the discovery and spends a charge")
        void aBlockCoversTheTracks(@TempDir Path dir) {
            // §5a: "the target is made to believe they removed it". Leaving the bot flagged as found
            // would make a Protector buy one extra roll rather than a fresh start.
            //
            // ⚠ THIS TEST WAS FLAKY AND PASSED THREE RUNS BY LUCK BEFORE THE FULL SUITE CAUGHT IT.
            // The block roll is salted with `bot.botId`, which is a random UUID — correct for the
            // rules (it is stable within a save, so re-loading cannot reroll it) and fatal for a test
            // that watches ONE bot and asserts a block happened. At L5 a block is 83%, so one bot in
            // six was removed instead and the assertion failed. A single-sample assertion on a
            // probabilistic rule is not a flaky test, it is a wrong one.
            //
            // What is deterministic is the PROPERTY: whichever way an attempt goes, it goes one of
            // exactly two ways, and a block has to leave the bot in a specific state. So this runs
            // many bots, asserts both outcomes are well-formed, and asserts that at least one blocked
            // — P(twenty consecutive failures at 83%) is about 1 in 10^15.
            GameEngine game = engine(dir);
            GameSave save = game.state();
            String address = footholdAddress(save);
            int blocked = 0;

            for (int attempt = 0; attempt < 20 && blocked == 0; attempt++) {
                grant(save, Catalogue.BOT_FRAME_V1);
                assertThat(game.buildBot(save.items.getLast().itemId).ok()).isTrue();
                BotState bot = save.bots.getLast();
                bot.frameTier = 3;
                grant(save, Catalogue.BOT_FN_KEYLOGGER);
                assertThat(game.socketBot(bot.botId, save.items.getLast().itemId).ok())
                        .isTrue();
                var mod = new io.github.stoicswe.eyeandsickle.engine.state.BotModifierState();
                mod.modifier = BotModifier.PROTECTOR.name();
                mod.level = Balance.BOT_MODIFIER_LEVEL_MAX;
                mod.protectorCharges = Balance.botProtectorCharges(mod.level);
                bot.modifiers.add(mod);
                assertThat(game.uploadBot(bot.botId, address).ok()).isTrue();

                int charges = mod.protectorCharges;
                for (int day = 1; day <= 200 && bot.live() && mod.protectorCharges == charges; day++) {
                    Botnet.settle(save, T0.plus(Duration.ofDays(day)));
                }

                if (mod.protectorCharges < charges) {
                    blocked++;
                    assertThat(bot.live()).as("a blocked removal leaves the bot where it is").isTrue();
                    assertThat(bot.discovered)
                            .as("and resets the discovery — they think it worked")
                            .isFalse();
                    assertThat(bot.functions).as("and costs it nothing").isNotEmpty();
                } else {
                    // The other outcome, and it must be well-formed too: removed, empty, off the host.
                    assertThat(bot.live()).isFalse();
                    assertThat(bot.functions).isEmpty();
                }
                // ⚠ Recalled or not, the bot is taken off the board before the next attempt: one bot
                // per machine, so a survivor would make every later upload a refusal and the loop
                // would silently stop testing anything.
                if (bot.live()) {
                    game.recallBot(bot.botId);
                }
                save.bots.remove(bot);
            }

            assertThat(blocked).as("a level-5 Protector blocks something within twenty attempts").isPositive();
        }

        @Test
        @DisplayName("the Protector's block chance compounds and never reaches certainty")
        void protectionIsNeverCertain() {
            // ⚠ "+30% a level" read as +30 PERCENTAGE POINTS makes L4 certain and L4, L5 and
            // immortality indistinguishable. Compounding is the reading; see §5a.
            assertThat(Balance.botProtectorBlockChance(1)).isEqualTo(0.30d, org.assertj.core.data.Offset.offset(1e-9));
            assertThat(Balance.botProtectorBlockChance(Balance.BOT_MODIFIER_LEVEL_MAX))
                    .isLessThan(1.0d)
                    .isGreaterThan(0.80d);
        }
    }

    @Nested
    @DisplayName("damage and salvage")
    class Salvage {

        @Test
        @DisplayName("a damaged frame cannot be uploaded until it is repaired")
        void damagedIsNotUsable(@TempDir Path dir) {
            GameEngine game = withBot(dir, BotFunction.KEYLOGGER);
            GameSave save = game.state();
            game.recallBot(save.bots.getFirst().botId);
            save.bots.getFirst().damaged = true;
            var refusal = game.uploadBot(save.bots.getFirst().botId, footholdAddress(save));
            assertThat(refusal.ok()).isFalse();
            assertThat(refusal.message()).contains("damaged");
        }

        @Test
        @DisplayName("repairing costs more than recycling returns, at every tier")
        void repairingBeatsScrapping() {
            // ⚠ Otherwise scrapping a damaged frame and building a fresh one dominates repairing at
            // every tier, and "damaged" means "destroyed" with extra steps. The parts side is the
            // same argument from the other direction: a recycle that funded a repair outright would
            // be a perpetual motion machine.
            for (int tier = 1; tier <= Balance.BOT_FRAME_TIER_MAX; tier++) {
                assertThat(Balance.botRepairParts(tier))
                        .as("repairing a v%d must cost more parts than recycling one yields", tier)
                        .isGreaterThan(Balance.BOT_RECYCLE_PARTS[tier]);
            }
        }

        @Test
        @DisplayName("recycling yields parts and scraps whatever was fitted")
        void recyclingIsNotARefund(@TempDir Path dir) {
            GameEngine game = withBot(dir, BotFunction.KEYLOGGER);
            GameSave save = game.state();
            game.recallBot(save.bots.getFirst().botId);
            assertThat(game.recycleBot(save.bots.getFirst().botId).ok()).isTrue();

            assertThat(save.bots).isEmpty();
            long parts = save.items.stream()
                    .filter(i -> Catalogue.BOT_FRAME_PARTS.equals(i.itemType))
                    .count();
            assertThat(parts).isEqualTo(Balance.BOT_RECYCLE_PARTS[1]);
            assertThat(save.items.stream().noneMatch(i -> Catalogue.BOT_FN_KEYLOGGER.equals(i.itemType)))
                    .as("the module is gone — a recycle is not the way to undo a loss you saw coming")
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("BedazzlePro")
    class Bedazzle {

        @Test
        @DisplayName("it costs personal heat, and the heat is real")
        void itCostsHeat(@TempDir Path dir) {
            GameEngine game = withBot(dir, BotFunction.KEYLOGGER);
            GameSave save = game.state();
            BotState bot = save.bots.getFirst();
            bot.frameTier = 3;
            var mod = new io.github.stoicswe.eyeandsickle.engine.state.BotModifierState();
            mod.modifier = BotModifier.BEDAZZLE_PRO.name();
            mod.level = Balance.BOT_MODIFIER_LEVEL_MAX;
            bot.modifiers.add(mod);
            int before = save.personalHeat;

            for (int minute = 5; minute <= 60 * 24; minute += 5) {
                Botnet.settle(save, T0.plus(Duration.ofMinutes(minute)));
            }

            assertThat(save.personalHeat)
                    .as("a day of a level-5 BedazzlePro is attention the Eye has actually paid")
                    .isGreaterThan(before);
        }

        @Test
        @DisplayName("a bot without one costs no heat at all")
        void withoutItThereIsNoCost(@TempDir Path dir) {
            // ⚠ The partner to the test above. A heat assertion with no negative case passes just as
            // happily if something ELSE in the settle started charging heat — and heat is exactly the
            // kind of thing that acquires a second source quietly.
            GameEngine game = withBot(dir, BotFunction.KEYLOGGER);
            GameSave save = game.state();
            int before = save.personalHeat;
            for (int minute = 5; minute <= 60 * 24; minute += 5) {
                Botnet.settle(save, T0.plus(Duration.ofMinutes(minute)));
            }
            assertThat(save.personalHeat).isEqualTo(before);
            assertThat(save.heatResidue).isZero();
        }

        @Test
        @DisplayName("nothing the player can read mentions the cost")
        void theBedazzleCostIsInvisible(@TempDir Path dir) {
            // ⚠ THE FEATURE IS THE SILENCE, so this is the assertion that actually protects it. The
            // way it breaks is not a wrong number — it is somebody adding a helpful sentence to the
            // effect line or an EventLog entry to the settle, both of which would look like
            // improvements in review. Cheats.concealLeavesNoTrace is the same shape.
            GameEngine game = withBot(dir, BotFunction.KEYLOGGER);
            GameSave save = game.state();
            BotState bot = save.bots.getFirst();
            bot.frameTier = 3;
            var mod = new io.github.stoicswe.eyeandsickle.engine.state.BotModifierState();
            mod.modifier = BotModifier.BEDAZZLE_PRO.name();
            mod.level = Balance.BOT_MODIFIER_LEVEL_MAX;
            bot.modifiers.add(mod);
            int logBefore = save.log.size();

            for (int minute = 5; minute <= 60 * 24; minute += 5) {
                Botnet.settle(save, T0.plus(Duration.ofMinutes(minute)));
            }
            assertThat(save.personalHeat).as("the fixture must actually have charged some").isPositive();

            // The panel's own words for the modifier.
            String shown = Botnet.snapshot(save, T0).bots().stream()
                    .flatMap(b -> b.modifiers().stream())
                    .filter(m -> m.modifier() == BotModifier.BEDAZZLE_PRO)
                    .map(m -> m.effect())
                    .findFirst()
                    .orElseThrow();
            // ...plus the market copy, which is the other place a player reads about it.
            String sold = Catalogue.byId(Catalogue.BOT_MOD_BEDAZZLE).orElseThrow().description();
            // ...plus everything written to the rig log while it was firing.
            String logged = save.log.stream()
                    .skip(logBefore)
                    .map(e -> e.message)
                    .reduce("", (a, b) -> a + " " + b);

            for (String leak : new String[] {"heat", "attention", "eye", "notice", "conspicuous"}) {
                assertThat(shown.toLowerCase(java.util.Locale.ROOT))
                        .as("the effect line must not name the cost")
                        .doesNotContain(leak);
                assertThat(sold.toLowerCase(java.util.Locale.ROOT))
                        .as("neither must the market copy")
                        .doesNotContain(leak);
                assertThat(logged.toLowerCase(java.util.Locale.ROOT))
                        .as("and nothing may be written to the log while it fires")
                        .doesNotContain(leak);
            }
        }

        @Test
        @DisplayName("the fraction is carried, not rounded away or rounded up")
        void theResidueIsCarried(@TempDir Path dir) {
            // ⚠ Rounding per trigger gives zero forever (truncated) or a whole point every time
            // (ceiled) — the first makes the cost imaginary, the second makes it about twelve times
            // what it should be. RigState.miningResidueWei carries the same rule.
            GameEngine game = withBot(dir, BotFunction.KEYLOGGER);
            GameSave save = game.state();
            BotState bot = save.bots.getFirst();
            bot.frameTier = 3;
            var mod = new io.github.stoicswe.eyeandsickle.engine.state.BotModifierState();
            mod.modifier = BotModifier.BEDAZZLE_PRO.name();
            mod.level = Balance.BOT_MODIFIER_LEVEL_MAX;
            bot.modifiers.add(mod);

            // One cadence: far too little for a whole point at BOT_BEDAZZLE_HEAT, so if a point
            // landed the arithmetic is ceiling rather than accumulating.
            Botnet.settle(save, T0.plus(Duration.ofMinutes(4)));
            assertThat(save.personalHeat).as("one trigger is not a whole point").isZero();
        }

        @Test
        @DisplayName("the heat cannot climb past the ceiling, and leaves no invisible debt behind")
        void itRespectsTheCeiling(@TempDir Path dir) {
            // A residue left accumulating at maximum heat would re-apply itself the moment heat came
            // down — a hidden debt from a session the player had already paid for.
            GameEngine game = withBot(dir, BotFunction.KEYLOGGER);
            GameSave save = game.state();
            BotState bot = save.bots.getFirst();
            bot.frameTier = 3;
            var mod = new io.github.stoicswe.eyeandsickle.engine.state.BotModifierState();
            mod.modifier = BotModifier.BEDAZZLE_PRO.name();
            mod.level = Balance.BOT_MODIFIER_LEVEL_MAX;
            bot.modifiers.add(mod);
            save.personalHeat = Balance.PERSONAL_HEAT_MAX;

            for (int minute = 5; minute <= 60 * 24; minute += 5) {
                Botnet.settle(save, T0.plus(Duration.ofMinutes(minute)));
            }

            assertThat(save.personalHeat).isEqualTo(Balance.PERSONAL_HEAT_MAX);
            assertThat(save.heatResidue).as("no debt is carried past the ceiling").isZero();
        }
    }

    @Nested
    @DisplayName("what is deliberately not built")
    class Seams {

        @Test
        @DisplayName("no function touches a breach board — I10 is structural, not tuned")
        void nothingPlaysThePuzzle() {
            // ⚠ The deleted Breacher frame made I10 a TUNING problem: a heuristic that had to be
            // kept reliably worse than a human forever, against a margin design/15 §2 P-3 says
            // cannot be measured until the puzzle is played at scale. With no constant for it there
            // is no code path to keep badly tuned. A constant here that played a board would not be
            // a feature; it would be abandoning I10.
            assertThat(BotFunction.values())
                    .extracting(Enum::name)
                    .doesNotContain("BREACHER", "CRACKER", "SOLVER");
        }

        @Test
        @DisplayName("nothing marks a watcher report copyable, because INTEL does not exist")
        void intelIsASeamNotAFeature(@TempDir Path dir) {
            // design/10 §5.5 and §6 BN-2. A control claiming to copy something the game has no
            // concept of is the "states a verdict the system cannot establish" failure.
            GameEngine game = withBot(dir, BotFunction.WATCHER);
            GameSave save = game.state();
            Botnet.settle(save, T0.plus(Duration.ofDays(2)));
            assertThat(save.botReports).allMatch(r -> !r.copyable);
        }
    }

    // ================================================================== fixture

    /**
     * A rig at the top of the compute ladder, with the tutorial parasite gone.
     *
     * <p>⚠ Both are needed and both are {@code GameEngineTest.bare}'s reasons. A starting rig is 24
     * cycles, and a {@code v10} chassis alone holds 30 — so without the ladder most of this file
     * would be asserting on "not enough compute" rather than on its subject. The parasite draws the
     * host's cycles (I6) and is simply noise here.
     */
    private static GameEngine engine(Path dir) {
        GameEngine game = GameEngine.open(TestSaves.at(dir.resolve("save.json")), "operator", java.time.Clock.fixed(T0, java.time.ZoneOffset.UTC));
        var rig = game.state().rig;
        for (var miner : java.util.List.copyOf(rig.foreignMiners)) {
            rig.allocations.removeIf(a -> a.allocationId.equals(miner.allocationId));
        }
        rig.foreignMiners.clear();
        for (var rung : ComputeLadder.rungs()) {
            grant(game.state(), rung.itemType());
        }
        ComputeLadder.reconcile(game.state());
        return game;
    }

    /** A built, loaded and uploaded bot on a machine the player holds. */
    private static GameEngine withBot(Path dir, BotFunction function) {
        GameEngine game = engine(dir);
        GameSave save = game.state();
        grant(save, Catalogue.BOT_FRAME_V1);
        assertThat(game.buildBot(save.items.getLast().itemId).ok()).isTrue();
        String botId = save.bots.getFirst().botId;
        grant(save, Catalogue.botFunctionOfferingId(function).orElseThrow());
        assertThat(game.socketBot(botId, save.items.getLast().itemId).ok()).isTrue();
        assertThat(game.uploadBot(botId, footholdAddress(save)).ok()).isTrue();
        return game;
    }

    private static void fit(GameEngine game, String itemType, String botId) {
        grant(game.state(), itemType);
        assertThat(game.fitBotModifier(botId, game.state().items.getLast().itemId).ok())
                .isTrue();
    }

    private static void grant(GameSave save, String itemType) {
        ItemState item = new ItemState();
        item.itemType = itemType;
        item.displayName = itemType;
        item.tier = StorageTier.VAULT.name();
        save.items.add(item);
    }

    /**
     * A machine the player holds, forced into existence.
     *
     * <p>⚠ It sets {@code discovered} <b>and</b> {@code foothold}, because those are two different
     * notions of "found" that agree only because a sweep sets both — {@code FootholdAfterBreachTest}
     * records a fixture that set one and failed with {@code NoSuchElement} rather than anything that
     * named the problem.
     */
    private static String footholdAddress(GameSave save) {
        HostState host = save.topology.hosts.stream()
                .filter(h -> !h.address.equals(save.topology.playerAddress))
                .filter(h -> !"BRIDGE".equals(h.kind))
                .findFirst()
                .orElseThrow();
        host.discovered = true;
        host.identified = true;
        host.foothold = true;
        return host.address;
    }

    private static HostState hostOf(GameSave save, String address) {
        return save.topology.hosts.stream()
                .filter(h -> h.address.equals(address))
                .findFirst()
                .orElseThrow();
    }
}
