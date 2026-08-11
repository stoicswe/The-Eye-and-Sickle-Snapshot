package io.github.stoicswe.eyeandsickle.engine.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import io.github.stoicswe.eyeandsickle.engine.state.NodeState;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Which branches of the network map the player has folded shut.
 *
 * <p>Almost nothing here is a game rule, and the two things that are matter a great deal: an entry
 * may only name a machine the player has discovered, and no rule anywhere may read one back.
 */
class MapFoldsTest {

    private static GameSave withNodes(String... addresses) {
        GameSave save = new GameSave();
        for (String address : addresses) {
            NodeState node = new NodeState();
            node.address = address;
            save.knownNodes.add(node);
        }
        return save;
    }

    @Nested
    @DisplayName("only a machine the player has found")
    class Discovery {

        @Test
        @DisplayName("an undiscovered address is refused, and stores nothing")
        void undiscoveredIsRefused() {
            // ⚠ THE ONE REASON THIS IS BEHIND THE ENGINE AT ALL. "Have I discovered this machine" is a
            // rules question the client is specifically not allowed to answer (I14) — a client-side
            // store would either duplicate knownNodes or accept whatever it was handed, and the second
            // is a free oracle for the one thing every sweep tier is sold on.
            GameSave save = withNodes("10.0.0.2");
            assertThat(MapFolds.set(save, "10.9.9.9", true)).isFalse();
            assertThat(MapFolds.of(save)).isEmpty();
        }

        @Test
        @DisplayName("a discovered one is stored, folded or open")
        void discoveredIsStored() {
            GameSave save = withNodes("10.0.0.2", "10.0.0.3");
            assertThat(MapFolds.set(save, "10.0.0.2", true)).isTrue();
            assertThat(MapFolds.set(save, "10.0.0.3", false)).isTrue();
            assertThat(MapFolds.of(save)).containsEntry("10.0.0.2", true).containsEntry("10.0.0.3", false);
        }

        @Test
        @DisplayName("open is not the same as absent")
        void openIsRecorded() {
            // ⚠ Absent means "the player has said nothing and the threshold decides". Storing only the
            // folded ones would make opening a branch the map folds on its own a decision that lasts
            // until the window closes, and the fold would come back on every launch.
            GameSave save = withNodes("10.0.0.2");
            MapFolds.set(save, "10.0.0.2", false);
            assertThat(MapFolds.of(save)).containsKey("10.0.0.2");
            assertThat(MapFolds.of(save).get("10.0.0.2")).isFalse();
        }

        @Test
        @DisplayName("a blank or null address is refused rather than stored")
        void blankIsRefused() {
            GameSave save = withNodes("10.0.0.2");
            assertThat(MapFolds.set(save, "", true)).isFalse();
            assertThat(MapFolds.set(save, null, true)).isFalse();
            assertThat(MapFolds.set(null, "10.0.0.2", true)).isFalse();
            assertThat(MapFolds.of(save)).isEmpty();
        }
    }

    @Nested
    @DisplayName("what the caller is told")
    class Changed {

        @Test
        @DisplayName("re-choosing the state it is already in reports no change")
        void noOpIsReported() {
            // ⚠ The caller is a click handler that repaints, persists and publishes on this answer. A
            // toggle reporting a change it did not make lights the disk lamp every time a player picks
            // the state they were already in — which is the failure markMessageRead and writeNote both
            // record from the other side.
            GameSave save = withNodes("10.0.0.2");
            assertThat(MapFolds.set(save, "10.0.0.2", true)).isTrue();
            assertThat(MapFolds.set(save, "10.0.0.2", true)).isFalse();
            assertThat(MapFolds.set(save, "10.0.0.2", false)).isTrue();
        }
    }

    @Nested
    @DisplayName("nothing folds a rule")
    class Inert {

        @Test
        @DisplayName("a fold is a boolean against an address, and nothing else")
        void foldsAreInert() {
            // ⚠ A STANDING CONSTRAINT, not a description of today. The moment a gate, price, threshold
            // or outcome depends on this map, every entry in it is a save-editable input to the rules —
            // and it is editable by construction, because the save is a file the player owns. Pinned as
            // a shape so a numeric or enum value has to argue with a test first.
            GameSave save = withNodes("10.0.0.2");
            MapFolds.set(save, "10.0.0.2", true);
            assertThat(save.netFolds.values()).allMatch(value -> value instanceof Boolean);
            assertThat(save.netFolds.keySet()).allMatch(key -> key instanceof String);
        }

        @Test
        @DisplayName("a stale key survives, because a sweep must not delete a preference")
        void staleKeysSurvive() {
            // A branch changes shape when a sweep finds a second parent for one of its machines, and
            // the entry then names no fold. It is left alone: the renderer ignores it, and dropping it
            // here would delete a preference on a discovery — the player would find branches they had
            // folded quietly reopening.
            GameSave save = withNodes("10.0.0.2");
            MapFolds.set(save, "10.0.0.2", true);
            save.knownNodes.clear();
            assertThat(MapFolds.of(save)).containsEntry("10.0.0.2", true);
        }
    }

    @Nested
    @DisplayName("a save that has never seen one")
    class Absent {

        @Test
        @DisplayName("a null map reads empty and takes a write")
        void nullIsHandled() {
            GameSave save = withNodes("10.0.0.2");
            save.netFolds = null;
            assertThat(MapFolds.of(save)).isEmpty();
            assertThat(MapFolds.set(save, "10.0.0.2", true)).isTrue();
            assertThat(MapFolds.of(save)).containsEntry("10.0.0.2", true);
        }

        @Test
        @DisplayName("a fresh save folds nothing")
        void freshIsEmpty() {
            assertThat(MapFolds.of(new GameSave())).isEmpty();
            assertThat(List.copyOf(new GameSave().netFolds.keySet())).isEmpty();
        }
    }
}
