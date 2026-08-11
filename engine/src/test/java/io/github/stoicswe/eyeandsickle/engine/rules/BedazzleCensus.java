package io.github.stoicswe.eyeandsickle.engine.rules;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.Catalogue;
import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import io.github.stoicswe.eyeandsickle.engine.rules.ComputeLadder;
import io.github.stoicswe.eyeandsickle.engine.state.BotModifierState;
import io.github.stoicswe.eyeandsickle.engine.state.ItemState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * What BedazzlePro actually costs, per level, in heat per hour of play.
 *
 * <h2>A measurement, not a test — and it is kept for the reason the others are</h2>
 *
 * {@code DefenseCensus}, {@code FoldCensus} and {@code HomeBridgeProbe} exist because a green suite
 * cannot see whether a number is the <em>right</em> number. This one answers a question no assertion
 * can: the cost here is <b>hidden from the player</b> ({@code docs/design/10} §5a), so if it is
 * mistuned there is no screen that says so and no report anyone could file. It is the one constant in
 * the botnet whose only feedback channel is this file.
 *
 * <pre>{@code
 * mvn -pl engine test-compile
 * mvn -q -pl engine exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=io.github.stoicswe.eyeandsickle.engine.rules.BedazzleCensus
 * }</pre>
 *
 * <p><b>Measured 2026-08-11</b>, one bot, one function, over a week of continuous play:
 *
 * <pre>
 * L1  0.08 heat/hour   (14 over a week)
 * L2  0.19 heat/hour   (32 over a week)
 * L3  0.32 heat/hour   (53 over a week)
 * L4  0.45 heat/hour   (75 over a week)
 * L5  0.58 heat/hour   (98 over a week)
 * </pre>
 *
 * <p>⚠ <b>A week of PLAY, not a week of absence.</b> The roll is capped at four cadences per settle
 * ({@code Botnet.settleBedazzle}), so an absence contributes almost nothing however long it is —
 * which is deliberate and is I5's shape applied to a cost rather than to income. A player who leaves
 * the client shut for a fortnight does not come back to a maxed heat bar.
 *
 * <p>⚠ <b>Per bot.</b> Three bedazzled bots is three times these figures, and the roll is also once
 * per fitted <em>function</em>, so a {@code v10} carrying four of them is sixteen times a single
 * {@code v1}. That compounding is intended — flamboyance scales with how much bot there is to be
 * flamboyant with — but it is the number to re-measure first if bots start reading as unaffordable.
 *
 * <p>⚠ <b>{@code Balance.BOT_BEDAZZLE_*} are compile-time constants and javac inlines them.</b> Run
 * {@code mvn -pl engine clean install} after touching one, or this census reports the build before
 * last — the trap that produced two rounds of byte-identical output when the defence round was tuned.
 */
public final class BedazzleCensus {

    private BedazzleCensus() {}

    private static final Instant T0 = Instant.parse("2026-08-11T09:00:00Z");

    public static void main(String[] args) throws Exception {
        int hours = 24 * 7;
        for (int level = 1; level <= Balance.BOT_MODIFIER_LEVEL_MAX; level++) {
            GameEngine game = bedazzledBot(level);
            for (int minute = 1; minute <= hours * 60; minute++) {
                Botnet.settle(game.state(), T0.plus(Duration.ofMinutes(minute)));
            }
            System.out.printf(
                    "L%d  %.2f heat/hour  (%d over a week)%n",
                    level, game.state().personalHeat / (double) hours, game.state().personalHeat);
        }
    }

    /** One live bot with a Miner and a BedazzlePro of {@code level}. */
    private static GameEngine bedazzledBot(int level) throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("bedazzle-census");
        GameEngine game = GameEngine.open(
                io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(dir.resolve("save.json")),
                "operator",
                Clock.fixed(T0, ZoneOffset.UTC));
        // ⚠ The ladder, for BotnetTest's reason: a starting rig is 24 cycles and a bot's control
        // channel competes with the tutorial parasite, so on a stock rig the upload is refused and
        // the census measures a bot that never went anywhere — reporting zero heat at every level,
        // which looks exactly like the feature being absent.
        for (var rung : ComputeLadder.rungs()) {
            grant(game, rung.itemType());
        }
        ComputeLadder.reconcile(game.state());

        var host = game.state().topology.hosts.stream()
                .filter(h -> !h.address.equals(game.state().topology.playerAddress))
                .filter(h -> !"BRIDGE".equals(h.kind))
                .findFirst()
                .orElseThrow();
        host.discovered = true;
        host.identified = true;
        host.foothold = true;

        grant(game, Catalogue.BOT_FRAME_V1);
        game.buildBot(game.state().items.getLast().itemId);
        String botId = game.state().bots.getFirst().botId;
        grant(game, Catalogue.BOT_FN_MINER);
        game.socketBot(botId, game.state().items.getLast().itemId);
        game.uploadBot(botId, host.address);

        var bot = game.state().bots.getFirst();
        // Forced rather than bought: a modifier socket starts at v2 and the compiler that would build
        // one is not built (docs/design/10 §6 BN-3).
        bot.frameTier = 3;
        BotModifierState mod = new BotModifierState();
        mod.modifier = "BEDAZZLE_PRO";
        mod.level = level;
        mod.lastRunAt = T0;
        bot.modifiers.add(mod);
        return game;
    }

    private static void grant(GameEngine game, String itemType) {
        ItemState item = new ItemState();
        item.itemType = itemType;
        item.tier = "VAULT";
        game.state().items.add(item);
    }
}
