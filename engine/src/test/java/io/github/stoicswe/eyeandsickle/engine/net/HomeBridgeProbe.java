package io.github.stoicswe.eyeandsickle.engine.net;

import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.HostState;
import io.github.stoicswe.eyeandsickle.protocol.game.HostKind;
import java.util.HashSet;
import java.util.Set;

/**
 * Throwaway measurement: how often does a first sweep from HOME actually find home's own bridge?
 *
 * <p>Not a test — a probe, run by hand. {@code FoldCensus}' precedent: a green suite cannot see a
 * feature that almost never fires, and the question here is a frequency rather than a property.
 */
final class HomeBridgeProbe {

    private static long seed(int i) {
        return i * 0x2545F4914F6CDD1DL + 0x9E3779B9L;
    }

    public static void main(String[] args) {
        int worlds = 400;
        int homeHasBridge = 0;
        int[] found = new int[SweepTier.values().length];
        int[] mapped = new int[SweepTier.values().length];

        for (int i = 0; i < worlds; i++) {
            GameSave probe = NetTestKit.world(seed(i));
            String homeServer = homeServerOf(probe);
            Set<String> homeBridges = new HashSet<>();
            for (HostState host : probe.topology.hosts) {
                if (HostKind.BRIDGE.name().equals(host.kind) && homeServer.equals(host.serverId)) {
                    homeBridges.add(host.address);
                }
            }
            if (!homeBridges.isEmpty()) {
                homeHasBridge++;
            }

            for (SweepTier tier : SweepTier.values()) {
                GameSave fresh = NetTestKit.world(seed(i));
                NetTestKit.grant(fresh, SweepTier.WIDE);
                NetTestKit.grant(fresh, SweepTier.DEEP);
                var report = NetTestKit.sweep(fresh, tier, NetTestKit.T0);
                if (report.foundAddresses().stream().anyMatch(homeBridges::contains)) {
                    found[tier.ordinal()]++;
                    continue;
                }
                // ⚠ AND THEN THE 73% RULE. A player who keeps mapping their own server rather than
                // giving up finds the exit from anywhere; this measures the second half of the loop
                // the way a player would reach it, by discovering the ordinary machines first.
                for (HostState host : fresh.topology.hosts) {
                    if (homeServer.equals(host.serverId) && !homeBridges.contains(host.address)) {
                        host.discovered = true;
                    }
                }
                var again = NetTestKit.sweep(fresh, tier, NetTestKit.T0.plusSeconds(600));
                if (again.foundAddresses().stream().anyMatch(homeBridges::contains)) {
                    mapped[tier.ordinal()]++;
                }
            }
        }

        System.out.println("worlds:                    " + worlds);
        System.out.println("home server HAS a bridge:  " + homeHasBridge + " / " + worlds);
        for (SweepTier tier : SweepTier.values()) {
            int either = found[tier.ordinal()] + mapped[tier.ordinal()];
            System.out.printf(
                    "  %-5s first sweep: %3d/%d (%3.0f%%)   + after mapping the server: %3d   = %3d/%d (%3.0f%%)%n",
                    tier,
                    found[tier.ordinal()],
                    worlds,
                    100.0 * found[tier.ordinal()] / worlds,
                    mapped[tier.ordinal()],
                    either,
                    worlds,
                    100.0 * either / worlds);
        }
    }

    private static String homeServerOf(GameSave save) {
        for (HostState host : save.topology.hosts) {
            if (host.address.equals(save.topology.playerAddress)) {
                return host.serverId;
            }
        }
        return "";
    }

    private HomeBridgeProbe() {}
}
