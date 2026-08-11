package io.github.stoicswe.eyeandsickle.engine.breach;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.ItemState;

/**
 * The payload a breach carries — {@code docs/design/19-defence-minigame.md} §5.
 *
 * <h2>What it is for</h2>
 *
 * Solving the board is no longer the whole of a breach. The puzzle gets you <b>onto</b> the machine;
 * uploading a virus is what takes it, and a virus is a <b>consumable bought in the market</b> in four
 * tiers. In solo the tier is the chance the upload holds (55% → 90%); against a real player it is how
 * many lives their defence round has to get through.
 *
 * <h2>⚠ THE ORDER IS THE WHOLE SAFETY ARGUMENT — puzzle first, roll second</h2>
 *
 * The roll happens <b>after</b> the board is solved and never instead of it. That is what keeps the
 * project's two meta-rules intact while a purchased item raises a success rate:
 *
 * <ul>
 *   <li><b>"The puzzle is the game."</b> No amount of money breaches anything on its own — a tier-4
 *       virus against an unsolved board is 90% of nothing.
 *   <li><b>I7</b>, proof-of-skill gates are tier-gated and never count-gated: this gates nothing. It
 *       is a running cost on an act the player has already earned.
 *   <li><b>I2</b>, ethecoin never buys a ceiling. ⚠ This is the one that is <em>bent</em>, and
 *       {@code Balance.BREACH_VIRUS_SUCCESS} carries the argument: consumed every attempt, it is a
 *       cost rather than an accumulating capability. <b>Making a virus permanent would break I2
 *       outright</b>, which is why the catalogue entries are {@code Durability.CONSUMABLE} and why
 *       that is not a decorative choice.
 * </ul>
 *
 * <h2>⚠ A CRACK NEEDS NO VIRUS, and that is not a convenience</h2>
 *
 * Cracking a parasite off the player's own rig is <b>defence</b> — Invariant <b>I9</b> already gives
 * it zero heat on every outcome, and {@code docs/design/04} §5.1 makes it the tutorial for the whole
 * breach system. Charging a bought consumable for it would put the game's teaching behind a purchase
 * and would price the removal of somebody else's parasite as if it were an intrusion. {@link #needs}
 * is where that exemption lives, once.
 */
public final class BreachVirus {

    private BreachVirus() {}

    /** The catalogue ids, tier 1 to tier 4. */
    private static final String[] IDS = {
        "breach-virus-t1", "breach-virus-t2", "breach-virus-t3", "breach-virus-t4",
    };

    /** The catalogue id for {@code tier}, 1–4. */
    public static String idFor(int tier) {
        return IDS[Math.max(0, Math.min(IDS.length - 1, tier - 1))];
    }

    /** The tier an item id names, or {@code 0} when it is not a virus. */
    public static int tierOf(String itemType) {
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i].equals(itemType)) {
                return i + 1;
            }
        }
        return 0;
    }

    /**
     * Whether this attempt has to spend one.
     *
     * @param minerCrack whether the target is a parasite on the player's own rig — see the class note
     */
    public static boolean needs(boolean minerCrack) {
        return !minerCrack;
    }

    /**
     * The best virus the rig is holding, or {@code 0} for none.
     *
     * <h2>⚠ THE BEST, not the cheapest, and it is deliberately not the player's choice yet</h2>
     *
     * Asking would be the better game and it needs a control on the launch panel that does not exist;
     * spending the dearest one automatically would also be wrong, so this is written where a chooser
     * will slot in. Recorded as <b>DEF-6</b> rather than left as a silent decision — a player who
     * bought one tier-4 virus for a deep target and then breached a desktop with it has been robbed
     * by an implementation detail.
     */
    public static int bestHeld(GameSave save) {
        int best = 0;
        for (ItemState item : save.items) {
            best = Math.max(best, tierOf(item.itemType));
        }
        return best;
    }

    /**
     * Spends one virus of {@code tier}.
     *
     * <p>⚠ Removes exactly one copy. Items do not stack ({@code StorageRules}), so a player holding
     * three tier-1 viruses has three {@code ItemState} rows and must keep two.
     *
     * @return whether one was found and removed
     */
    public static boolean spend(GameSave save, int tier) {
        for (int i = 0; i < save.items.size(); i++) {
            if (tierOf(save.items.get(i).itemType) == tier) {
                save.items.remove(i);
                return true;
            }
        }
        return false;
    }

    /**
     * Rolls whether an uploaded virus of {@code tier} took the machine.
     *
     * <p>⚠ Drawn from the save's committed stream, because this <b>is</b> a game outcome — loot, a
     * foothold and heat all follow from it. The caller commits.
     */
    public static boolean holds(int tier, Rng rng) {
        return rng.nextDouble() < Balance.breachVirusSuccess(tier);
    }
}
