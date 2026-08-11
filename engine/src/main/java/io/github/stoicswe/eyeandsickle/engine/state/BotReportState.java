package io.github.stoicswe.eyeandsickle.engine.state;

import java.time.Instant;

/**
 * One thing a Watcher saw — {@code docs/design/10-botnets.md} §5.5.
 *
 * <p>Persisted rather than derived, which is the opposite of the activity stream it is drawn from.
 * Host activity is a pure function of (host, window) and can be recomputed at any time; a
 * <em>sighting</em> of it is a fact about what the player's bot happened to be watching when it
 * happened, and a Watcher socketed later must not retroactively have seen it.
 *
 * <p>Bounded and trimmed from the front, like every other log in this save.
 */
public final class BotReportState {

    public Instant at = Instant.EPOCH;

    public String botId = "";

    public String hostAddress = "";

    /** {@code WORK}, {@code VALUE}, or {@code INTEL} once {@code docs/design/14} defines one. */
    public String subject = "";

    public String detail = "";

    /**
     * Whether the player may spend cycles to take a copy.
     *
     * <p>⚠ Always false today. The only copyable subject is INTEL and it does not exist — see §5.5.
     * The field is here so the seam is visible in the shape of the data rather than discovered by
     * whoever builds INTEL, but nothing sets it and {@code BotnetTest} holds that.
     */
    public boolean copyable = false;
}
