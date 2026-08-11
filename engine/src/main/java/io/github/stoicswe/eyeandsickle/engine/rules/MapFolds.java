package io.github.stoicswe.eyeandsickle.engine.rules;

import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import java.util.Collections;
import java.util.Map;

/**
 * Which branches of the network map the player has folded shut.
 *
 * <h2>Why this is a rule at all, when nothing here is a rule</h2>
 *
 * Folding a branch is a view decision — it changes no cost, no gate, no chance and no yield, and no
 * other class in this package reads {@link GameSave#netFolds} back. It lives behind the engine for
 * one reason, and it is the same reason {@code GameSession}'s filing block gives for folders: a fold
 * names a machine, and <b>"have I discovered this machine" is a rules question the client is
 * specifically not allowed to answer</b> (Invariant <b>I14</b>). A client-side store would either
 * duplicate {@code knownNodes} or accept any address it was handed, and the second is a free oracle
 * for the one thing every sweep tier is sold on.
 *
 * <p>⚠ That is also what bounds the map. An entry may only exist for a discovered machine, so its
 * size is bounded by the world rather than by how often somebody clicks — which is the property
 * {@code ClientProfile.windowSizes} had to acquire the hard way, by growing one entry per machine
 * ever visited.
 *
 * <h2>⚠ NOTHING IS PRUNED ON THE WAY OUT</h2>
 *
 * A stored address whose branch has since changed shape — a sweep found a second parent, a chord
 * landed — simply names no fold, and the renderer ignores it. Dropping it here would delete a
 * preference on a discovery, and the player would find branches they had folded quietly reopening.
 */
public final class MapFolds {

    private MapFolds() {}

    /** What the player has folded and opened, by parent address. {@code true} is folded. */
    public static Map<String, Boolean> of(GameSave save) {
        if (save == null || save.netFolds == null) {
            return Map.of();
        }
        return Collections.unmodifiableMap(save.netFolds);
    }

    /**
     * Records that the branch behind {@code address} is folded, or is open.
     *
     * <p>⚠ Both are <b>stored</b>, and open is not the same as absent. Absent means the player has
     * said nothing and the map's own threshold decides; {@code false} means they opened a branch the
     * map folds on its own, which has to outlive the session or the fold returns on every launch.
     *
     * @return whether anything actually changed. The caller is a click handler and repaints, persists
     *     and publishes on the answer — a toggle that reports a change it did not make lights the disk
     *     lamp every time a player re-picks the state they were already in
     */
    public static boolean set(GameSave save, String address, boolean folded) {
        if (save == null || address == null || address.isBlank()) {
            return false;
        }
        if (save.netFolds == null) {
            save.netFolds = new java.util.LinkedHashMap<>();
        }
        // ⚠ The discovery check, and the whole reason this is not a client-side setting. An address
        // the player has not swept is refused rather than stored — silently, because a refusal would
        // be the client learning that the machine exists, which is precisely what it must not learn.
        if (!discovered(save, address)) {
            return false;
        }
        Boolean was = save.netFolds.put(address, folded);
        return was == null || was != folded;
    }

    private static boolean discovered(GameSave save, String address) {
        if (save.knownNodes == null) {
            return false;
        }
        for (var node : save.knownNodes) {
            if (address.equals(node.address)) {
                return true;
            }
        }
        return false;
    }
}
