package io.github.stoicswe.eyeandsickle.engine;

import static io.github.stoicswe.eyeandsickle.engine.support.Money.ec;

import io.github.stoicswe.eyeandsickle.engine.rules.ComputeLadder;
import io.github.stoicswe.eyeandsickle.engine.save.SaveStore;
import io.github.stoicswe.eyeandsickle.engine.save.TestSaves;
import io.github.stoicswe.eyeandsickle.engine.state.ItemState;
import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Measures the spread {@code GameEngineTest.offlineSelfMiningIsCappedNotProportional} is banded
 * against, so that band is re-measured rather than re-guessed.
 *
 * <pre>
 * mvn -pl engine exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=io.github.stoicswe.eyeandsickle.engine.OfflineCapCensus -Dexec.args=400
 * </pre>
 *
 * <h2>⚠ The signed bias is the number that matters, not the spread</h2>
 *
 * That test asserts a thirty-day absence pays about what the spin-down window pays, and its band is
 * a tolerance on <em>realised variance</em> — a capped absence buys ~240 pooled shares, so one
 * sample carries σ ≈ 1/√240 and comparing two carries σ ≈ 9.6%. A band chosen off that is only
 * honest while the deviation stays <b>centred on zero</b>: income beginning to track the absence
 * would appear as a positive mean per leg, and a band widened without checking that is a band that
 * hides the invariant breaking. So this reports the signed mean and its standard error per leg
 * alongside the failure rate, and both are read together.
 *
 * <p>Measured 2026-08-10, 400 worlds: bias 24h −0.18% ± 0.39, 7d +0.19% ± 0.45, 30d −0.13% ± 0.48;
 * the ±30% band failed 2/400 and ±45% failed 0/400.
 */
final class OfflineCapCensus {

    private static final Instant T0 = Instant.parse("2026-07-25T12:00:00Z");
    private static final long CAPACITY = Balance.COMPUTE_RUNGS[Balance.COMPUTE_RUNGS.length - 1];

    private static GameEngine bare(SaveStore store, java.time.Clock clock) {
        GameEngine game = GameEngine.open(store, "operator", clock);
        var rig = game.state().rig;
        for (var miner : List.copyOf(rig.foreignMiners)) {
            rig.allocations.removeIf(a -> a.allocationId.equals(miner.allocationId));
        }
        rig.foreignMiners.clear();
        for (var rung : ComputeLadder.rungs()) {
            var item = new ItemState();
            item.itemType = rung.itemType();
            item.tier = StorageTier.VAULT.name();
            game.state().items.add(item);
        }
        ComputeLadder.reconcile(game.state());
        return game;
    }

    public static void main(String[] args) throws Exception {
        int worlds = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        List<Duration> aways = List.of(
                Duration.ofHours(Balance.OFFLINE_MINING_HOURS),
                Duration.ofDays(1),
                Duration.ofDays(7),
                Duration.ofDays(30));

        List<Double> all = new ArrayList<>();
        List<Double> worst = new ArrayList<>();
        java.util.Map<Duration, List<Double>> signed = new java.util.LinkedHashMap<>();
        aways.forEach(a -> signed.put(a, new ArrayList<>()));
        int failures = 0;
        int payoutsSeen = 0;
        for (int w = 0; w < worlds; w++) {
            Path base = Path.of("/census/" + w + "/save.json");
            GameEngine first = bare(TestSaves.at(base), new TestClock(T0));
            first.allocateSelfMining(CAPACITY);
            first.persist();

            double atCap = 0.0d;
            double worstHere = 0.0d;
            for (Duration away : aways) {
                Path each = Path.of("/census/" + w + "/away-" + away.toHours() + ".json");
                TestSaves.copy(base, each);
                GameEngine game = bare(TestSaves.at(each), new TestClock(T0.plus(away)));
                double got = ec(game.balance().wei());
                if (atCap == 0.0d) {
                    atCap = got;
                    payoutsSeen += game.state().rig.miningPayouts;
                    continue;
                }
                signed.get(away).add((got - atCap) / atCap * 100.0d);
                double pct = Math.abs(got - atCap) / Math.abs(atCap) * 100.0d;
                all.add(pct);
                worstHere = Math.max(worstHere, pct);
            }
            worst.add(worstHere);
            if (worstHere > 30.0d) {
                failures++;
            }
        }
        all.sort(Double::compare);
        worst.sort(Double::compare);
        System.out.printf("worlds=%d  comparisons=%d%n", worlds, all.size());
        System.out.printf("mean payouts in the capped window: %.1f%n", payoutsSeen / (double) worlds);
        System.out.printf(
                "per-comparison deviation: median %.1f%%  p90 %.1f%%  p99 %.1f%%  max %.1f%%%n",
                pct(all, 50), pct(all, 90), pct(all, 99), all.get(all.size() - 1));
        System.out.printf(
                "worst-of-three per world:  median %.1f%%  p90 %.1f%%  p99 %.1f%%  max %.1f%%%n",
                pct(worst, 50), pct(worst, 90), pct(worst, 99), worst.get(worst.size() - 1));
        System.out.printf("worlds failing the 30%% band: %d / %d = %.1f%%%n", failures, worlds, 100.0 * failures / worlds);
        signed.forEach((away, xs) -> {
            if (xs.isEmpty()) {
                return;
            }
            double mean = xs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double sd = Math.sqrt(xs.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum() / (xs.size() - 1));
            System.out.printf(
                    "signed bias %-8s mean %+.2f%%  sd %.2f%%  stderr %.2f%%%n",
                    away, mean, sd, sd / Math.sqrt(xs.size()));
        });
        for (int band : new int[] {20, 25, 30, 35, 40, 45, 50, 60}) {
            long n = worst.stream().filter(v -> v > band).count();
            System.out.printf("  band %d%%: %d / %d fail (%.1f%%)%n", band, n, worlds, 100.0 * n / worlds);
        }
    }

    private static double pct(List<Double> sorted, int p) {
        return sorted.get(Math.min(sorted.size() - 1, (int) Math.round(p / 100.0 * (sorted.size() - 1))));
    }
}
