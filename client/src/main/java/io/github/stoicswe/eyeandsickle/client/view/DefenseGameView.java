package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.ui.Posterize;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.defense.DefenseGame;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * The defence minigame — {@code docs/design/19-defence-minigame.md}, drawn and played.
 *
 * <h2>⚠ IT OWNS NO RULE. Every one of them is in {@code engine/defense/DefenseGame}</h2>
 *
 * This class reads the keyboard, calls {@code tick} once per frame and moves shapes to the positions
 * that come back. It decides nothing about what hits what, who wins or how long the round is — which
 * is what let the entire round be tested headlessly, and this project runs exactly one JUnit test
 * that starts JavaFX. A simulation living here would be verifiable only by playing it.
 *
 * <h2>⚠ The motion policy, and why this does not break it — {@code docs/design/19} §6</h2>
 *
 * {@code ui-design-language.md} §5 is "step and linear timing only", and §5.1 draws the real line:
 * motion the player <em>works inside</em> versus motion they only <em>watch</em>. A minigame is
 * neither — the motion <b>is</b> the content.
 *
 * <ul>
 *   <li>⚠ <b>No {@code AnimationTimer}</b>, which {@code UiContractTest} rations to two files by
 *       name, and <b>no {@code KeyValue}</b>, so nothing interpolates. The loop is an
 *       <b>action-only {@code Timeline}</b> — the same mechanism {@code Frost} (24 fps) and
 *       {@code SyncSpin} (30 fps) already use. A {@code KeyFrame} with an action and no
 *       {@code KeyValue} is a <b>sampling rate, not a tween</b>.
 *   <li>⚠ <b>Nothing is eased.</b> Every position arrives from the simulation, which integrates a
 *       velocity at a fixed step. That is arithmetic.
 *   <li>⚠ <b>Reduce motion does not stop it</b> — there is no still version of an arcade round, and
 *       freezing it would be a loss on the clock rather than an accommodation. The accommodation is
 *       GIVE UP, which is always one press away.
 * </ul>
 *
 * <h2>⚠ Colour is the palette's, and the mapping is §2.1's rather than a game's</h2>
 *
 * §2.1 spends amber on <b>cycles doing work</b> and rations alarm to <b>loss and hostile state</b>.
 * So: the virus and everything it fires are {@code -es-alarm} — they are the hostile thing, and they
 * are one subject rather than three. The <b>firewall band is amber</b>, which is not a liberty: an
 * armed firewall is standing compute doing work, which is precisely what the token is for. The
 * player, the laser and the shield are neutral text tokens. Nothing here invents a colour, so all
 * eight palettes work and uOS Classic inverts for free.
 */
public final class DefenseGameView {

    private DefenseGameView() {}

    /** How a defence attempt ended. */
    public enum Outcome {
        /** The player held. */
        HELD,

        /** The player did not. */
        BREACHED
    }

    /**
     * Builds the round.
     *
     * @param subject what is being defended, in words — the machine that is trying to get in
     * @param firewallTier the armed firewall's tier, or 0. Sets the shelter band's width
     * @param seed the shield layout, so a round is reproducible
     * @param onResolved handed exactly one outcome, exactly once
     */
    public static Region create(
            GameSession session,
            String subject,
            int firewallTier,
            long seed,
            java.util.function.Consumer<Outcome> onResolved) {
        return create(session, subject, firewallTier, false, false, 1, seed, onResolved);
    }

    /**
     * The round with the rig's whole defensive posture and the attack it is facing.
     *
     * @param tarpit whether a Tarpit is armed — slows the virus's patrol
     * @param daemon whether the Auto-Counter Daemon is armed — offers to take the round
     * @param virusTier the attacker's Breach Virus, 1–4
     */
    public static Region create(
            GameSession session,
            String subject,
            int firewallTier,
            boolean tarpit,
            boolean daemon,
            int virusTier,
            long seed,
            java.util.function.Consumer<Outcome> onResolved) {
        return create(session, subject, firewallTier, tarpit, daemon, virusTier, seed, onResolved, l -> {});
    }

    /**
     * @param onPosterize told how many colour levels the round has left, so the deck behind it can
     *     lose its depth in step. ⚠ Pushed from here rather than pulled by the deck: the round owns
     *     the clock, and two things reading a countdown separately is two things that can disagree
     *     about how far through it is.
     */
    public static Region create(
            GameSession session,
            String subject,
            int firewallTier,
            boolean tarpit,
            boolean daemon,
            int virusTier,
            long seed,
            java.util.function.Consumer<Outcome> onResolved,
            java.util.function.IntConsumer onPosterize) {

        DefenseGame game = new DefenseGame(firewallTier, tarpit, virusTier, seed);

        VBox root = new VBox(UiTokens.SPACE_3);
        root.getStyleClass().addAll("es-defensegame", "es-body-pad");

        Label title = new Label(Views.t("ui.defense-game.defence", "DEFENCE"));
        title.getStyleClass().add("es-panel-title");

        Label what = new Label(subject);
        what.getStyleClass().addAll("es-defensegame-subject", "es-mono");

        // ⚠ THE CLOCK IS A BAR THAT EMPTIES, not a number that counts down. A figure has to be read
        // and converted; a shortening bar is understood without looking away from the field, which is
        // the only place a player can afford to be looking during a thirty-second round.
        Rectangle timerTrack = new Rectangle(0, UiTokens.DEFENSE_TIMER_HEIGHT);
        timerTrack.getStyleClass().add("es-defensegame-timer-track");
        Rectangle timerFill = new Rectangle(0, UiTokens.DEFENSE_TIMER_HEIGHT);
        timerFill.getStyleClass().add("es-defensegame-timer");
        Pane timer = new Pane(timerTrack, timerFill);
        timer.setMinHeight(UiTokens.DEFENSE_TIMER_HEIGHT);
        timer.setPrefHeight(UiTokens.DEFENSE_TIMER_HEIGHT);
        timer.setMaxHeight(UiTokens.DEFENSE_TIMER_HEIGHT);
        // ⚠ Bound to the track's LIVE width, never to a preferred one. `getWidth()` is 0 before the
        // first layout pass, so a fill sized from it at build time renders empty — the exact defect
        // the firmware flash overlay's bar shipped with, caught only by a render.
        timerTrack.widthProperty().bind(timer.widthProperty());
        // ⚠ THE FILL IS BOUND TO A FRACTION, never assigned from `getWidth()` in the frame loop.
        // `step` runs once at build time, before any layout pass, where the width is 0 — so an
        // assigned fill renders EMPTY on the opening frame and stays empty forever in a synchronous
        // render. Exactly the defect the firmware flash overlay's bar shipped with, and it was found
        // the same way: by looking at a picture.
        javafx.beans.property.DoubleProperty remaining = new javafx.beans.property.SimpleDoubleProperty(1.0d);
        timerFill.widthProperty().bind(timer.widthProperty().multiply(remaining));

        // ⚠ HEARTS, DRAWN, and counted rather than written. `GlyphCoverageTest` scans source for
        // literals and neither bundled face carries U+2665 — and a heart is the one symbol that says
        // "lives" without a word, in a panel where the player has no attention to spare for reading.
        HBox hearts = new HBox(UiTokens.SPACE_2);
        hearts.setAlignment(Pos.CENTER_LEFT);

        Label shelter = new Label();
        shelter.getStyleClass().addAll("es-mono", "es-defensegame-shelter");
        HBox readouts = Ui.row(UiTokens.SPACE_6, hearts, shelter);

        // ---------------------------------------------------------------- the field
        //
        // ⚠ A Pane of Shapes rather than a Canvas, and the reason is COLOUR. A Canvas cannot resolve
        // a looked-up value, so ShadowMarketView has to read its two colours off invisible probe
        // labels in the live scene — a real trap this project has already been caught by, where a
        // throwaway Scene carries no stylesheet and every candle renders identical. A Shape takes
        // `-fx-fill` from a style class, so the palette applies to a game piece exactly as it does to
        // a chip, in all eight themes, with nothing to keep in step.
        Pane field = new Pane();
        field.getStyleClass().add("es-defensegame-field");
        field.setMinSize(Balance.DEFENSE_FIELD_WIDTH, Balance.DEFENSE_FIELD_HEIGHT);
        field.setPrefSize(Balance.DEFENSE_FIELD_WIDTH, Balance.DEFENSE_FIELD_HEIGHT);
        field.setMaxSize(Balance.DEFENSE_FIELD_WIDTH, Balance.DEFENSE_FIELD_HEIGHT);
        // ⚠ CLIPPED. A triangle that flies past the player keeps going in a straight line by design,
        // and without a clip it is drawn outside the field, over the readouts and the GIVE UP button
        // — the round's own pieces scribbling on its chrome.
        field.setClip(new Rectangle(Balance.DEFENSE_FIELD_WIDTH, Balance.DEFENSE_FIELD_HEIGHT));

        Rectangle band = new Rectangle(Balance.defenseFirewallBand(firewallTier), Balance.DEFENSE_FIELD_HEIGHT);
        band.setX(Balance.DEFENSE_MIDLINE);
        band.getStyleClass().add("es-defensegame-band");
        band.setVisible(Balance.defenseFirewallBand(firewallTier) > 0);

        // ⚠ The band's "glitch" is SLICED GEOMETRY, not a filter. §9 makes blur and drop shadows
        // build-blocking, so the look comes from structure — a handful of narrow slices offset
        // sideways per frame, which is the same trick the ring wallpaper uses for its datamosh.
        List<Rectangle> glitch = new ArrayList<>();
        if (Balance.defenseFirewallBand(firewallTier) > 0) {
            for (int i = 0; i < 9; i++) {
                Rectangle slice = new Rectangle(Balance.defenseFirewallBand(firewallTier), 8);
                slice.setX(Balance.DEFENSE_MIDLINE);
                slice.setY(i * (Balance.DEFENSE_FIELD_HEIGHT / 9.0d));
                slice.getStyleClass().add("es-defensegame-band-slice");
                glitch.add(slice);
            }
        }

        Rectangle player = new Rectangle(
                Balance.DEFENSE_PLAYER_RADIUS * 2, Balance.DEFENSE_PLAYER_RADIUS * 2);
        player.getStyleClass().add("es-defensegame-player");

        List<Rectangle> arms = new ArrayList<>();
        Group virus = virusMark(arms);
        List<Polygon> barbs = new ArrayList<>();
        Group chaser = chaserMark(barbs);
        Rectangle beam = new Rectangle(14, 2);
        beam.getStyleClass().add("es-defensegame-laser");
        beam.setVisible(false);

        Pane blocks = new Pane();
        Pane shots = new Pane();

        // ⚠ CHROMATIC ABERRATION, DONE AS OFFSET COPIES — there is no channel filter available and §9
        // would refuse one anyway. Two tinted ghosts per moving item, drawn UNDER the item and pulled
        // apart as the clock runs down, which is what real convergence error looks like.
        //
        // ⚠ MOVING ITEMS ONLY — the player, the circle and the shots. The shield is static and there
        // are twenty of them; ghosting those would triple the node count of the busiest part of the
        // field for an effect nobody can see on something that never moves. The eye tracks what moves.
        Pane ghosts = new Pane();
        ghosts.setMouseTransparent(true);

        field.getChildren().add(band);
        field.getChildren().addAll(glitch);
        field.getChildren().addAll(blocks, ghosts, shots, chaser, beam, virus, player);

        StackPane framed = new StackPane(field);
        framed.getStyleClass().add("es-well");
        framed.setMaxWidth(Region.USE_PREF_SIZE);
        framed.setAlignment(Pos.TOP_LEFT);

        // ⚠ THE FIELD IS SCALED TO THE ROOM IT IS GIVEN, and it is wrapped in a `Group` to do it.
        //
        // The simulation is in fixed logical units (480 × 300) and must stay that way — a field sized
        // in pixels would make the round easier on a small window and harder on a large one, which is
        // the trap `Balance`'s block warns about. So the geometry never changes; only the picture of
        // it does.
        //
        // ⚠ A `Group` rather than scaling inside a Pane: a Group's bounds FOLLOW its transform, so
        // the holder reserves the scaled size and centres it. Scaling a Pane directly leaves the
        // layout believing it is still 480 wide, and a scaled-up field then overhangs its parent and
        // is clipped on all four sides.
        Group arena = new Group(framed);

        // ⚠ THE EXPLOSION IS DRAWN GEOMETRY ON THE FIELD'S OWN SCALE, never a filter. §9 makes blur
        // and drop shadows build-blocking, so a burst is what it looks like: shards thrown outward
        // from a point, stepped, fading by opacity. `RingField`'s datamosh is the same decision.
        Pane debris = new Pane();
        debris.setMouseTransparent(true);
        field.getChildren().add(debris);

        Label verdict = new Label("");
        verdict.getStyleClass().addAll("es-mono", "es-defensegame-verdict");
        verdict.setWrapText(true);

        Label help = Ui.micro("Arrow keys move · Space fires · you cannot cross the midline");

        BreachView.Chip giveUp = new BreachView.Chip("Give up", "es-breach-chip-quiet");
        giveUp.setAccessibleText("Give the defence up. The attempt gets through, and the round ends now.");

        // ⚠ THE DAEMON'S ODDS ARE ON ITS FACE, and that is the whole of what makes it an honest
        // offer. It is a coin flip at best and worse against a better attack; a control that said
        // only "let the daemon handle it" would read as a free pass, and a player would press it once
        // and never learn what it cost them.
        int daemonPercent = (int) Math.round(Balance.defenseDaemonOdds(virusTier) * 100);
        BreachView.Chip handOver = new BreachView.Chip("Auto-counter (" + daemonPercent + "%)", "es-breach-chip-quiet");
        handOver.setAccessibleText("Hand this round to the Auto-Counter Daemon. It rolls once, at "
                + daemonPercent + " percent against a tier " + virusTier + " virus, and the round ends either way. "
                + "Playing it yourself is better odds.");
        handOver.setVisible(daemon);
        handOver.setManaged(daemon);

        HBox controls = Ui.row(UiTokens.SPACE_3, giveUp, handOver);

        // ⚠ THE BANNER IS OVER THE FIELD, not under it. The verdict of a thirty-second round has to
        // land where the player's eyes already are — a line of text below the controls is read
        // several seconds later, by which time they have worked it out from the wreckage anyway.
        Label banner = new Label("");
        banner.getStyleClass().add("es-defensegame-banner");
        banner.setVisible(false);
        banner.setMouseTransparent(true);
        StackPane framedWithBanner = new StackPane(arena, banner);
        framedWithBanner.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(framedWithBanner, Priority.ALWAYS);
        // ⚠ Recomputed on every layout of the HOLDER, not of the field: the field's own size never
        // changes, so a listener on it would fire once and never again.
        javafx.beans.value.ChangeListener<Object> refit = (o, was, now) -> {
            double factor = Math.min(
                    framedWithBanner.getWidth() / Balance.DEFENSE_FIELD_WIDTH,
                    framedWithBanner.getHeight() / Balance.DEFENSE_FIELD_HEIGHT);
            // ⚠ Floored at 1. A deck too small for the field shows it at its natural size and lets the
            // holder clip, which is legible; scaling BELOW 1 would shrink a character-cell-sized game
            // until the cube and the triangles were the same two pixels.
            framed.setScaleX(Math.max(1.0d, factor));
            framed.setScaleY(Math.max(1.0d, factor));
        };
        framedWithBanner.widthProperty().addListener(refit);
        framedWithBanner.heightProperty().addListener(refit);

        // ⚠ THE ROUND'S OWN EDGE PULSES ON THE HEARTBEAT, and it is the one border in this client
        // allowed to move. §2.1 rations alarm to loss and hostile state; this is the frame around the
        // thing currently attacking the player, which is both. Drawn as a Rectangle over the panel
        // rather than a CSS border, because a border colour cannot be driven on a clock and because a
        // border WIDTH that changed would reflow everything inside it.
        Rectangle edge = new Rectangle();
        edge.getStyleClass().add("es-defensegame-edge");
        edge.setMouseTransparent(true);
        edge.setOpacity(0);

        // ⚠ THE ROOM FILLS WITH RED WHEN THE PLAYER LOSES, over the top of everything including the
        // verdict — the last thing they see is the word FAILED going under. It is a wash rather than
        // a tint on the panel so it covers the field, the readouts and the controls at once: the whole
        // round is what is being taken away.
        Rectangle wash = new Rectangle();
        wash.getStyleClass().add("es-defensegame-wash");
        wash.setMouseTransparent(true);
        wash.setOpacity(0);

        // ⚠ Drips run from the top of the round as well as from the top of the deck. Inside the
        // panel, so they are the one part of the horror that reaches into the thing that is meant to
        // be untouched by it — deliberately: the round is not a safe place, it is where it is
        // happening.
        Pane roundDrips = new Pane();
        roundDrips.setMouseTransparent(true);

        StackPane skin = new StackPane(root, roundDrips, wash, edge);
        skin.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        edge.widthProperty().bind(skin.widthProperty().subtract(2));
        edge.heightProperty().bind(skin.heightProperty().subtract(2));
        wash.widthProperty().bind(skin.widthProperty());
        wash.heightProperty().bind(skin.heightProperty());

        root.getChildren().addAll(title, what, timer, readouts, framedWithBanner, help, controls, verdict);
        // ⚠ The round fills whatever it is put in. As a deck LAYER (not a window) it is handed the
        // whole deck, and a VBox that sized itself to its content would sit in the top-left corner of
        // it — which is exactly what the first render showed.
        root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        // ---------------------------------------------------------------- input
        //
        // ⚠ HELD KEYS, NOT KEY EVENTS. A key repeat is an operating-system setting — it fires once,
        // pauses, then repeats at whatever rate the player's machine is set to — so driving movement
        // from KEY_PRESSED gives a cube that lurches, stops, then glides, differently on every
        // machine. The set of what is currently down is the only thing that reads the same everywhere.
        // ⚠ FOCUS AND THE KEY HANDLERS GO ON `skin` — THE NODE `create` RETURNS — AND NOT ON `root`.
        //
        // This shipped broken. `root` is the inner VBox; `skin` is the wrapper that carries the
        // pulsing edge and the drips, and it is what the deck is handed. The deck opened a round and
        // called `requestFocus()` on it, which was a <b>silent no-op</b>: `skin` was not
        // focus-traversable, so focus stayed wherever it was — usually the command strip — and the
        // arrow keys did nothing until the player clicked the field. Nothing failed and no render
        // could show it; a screenshot of a game nobody can drive looks exactly like a screenshot of a
        // game.
        //
        // ⚠ HANDLERS, NOT FILTERS, and that is deliberate. A key pressed while GIVE UP has focus
        // bubbles up from the chip to here, so the game still reads it — while a filter would consume
        // Space on the way DOWN and take keyboard activation away from the two controls that have it.
        Set<KeyCode> held = EnumSet.noneOf(KeyCode.class);
        skin.setFocusTraversable(true);
        skin.setOnKeyPressed(e -> {
            if (TRACKED.contains(e.getCode())) {
                held.add(e.getCode());
                // ⚠ Consumed, or the arrow keys traverse focus out of the field mid-round and the
                // player's controls stop working with nothing on screen to say why.
                e.consume();
            }
        });
        skin.setOnKeyReleased(e -> {
            if (held.remove(e.getCode())) {
                e.consume();
            }
        });
        // ⚠ Losing focus clears everything held. Without it, alt-tabbing away with a key down leaves
        // the cube travelling in that direction for the rest of the round — the release event goes to
        // whatever took the focus.
        skin.focusedProperty().addListener((o, was, now) -> {
            if (!now) {
                held.clear();
            }
        });
        // The click-to-focus route survives as a fallback, not as the way in.
        field.setOnMousePressed(e -> skin.requestFocus());

        // ---------------------------------------------------------------- the loop
        boolean[] resolved = {false};
        Timeline[] loop = new Timeline[1];
        Runnable settle = () -> {
            if (resolved[0]) {
                return;
            }
            resolved[0] = true;
            if (loop[0] != null) {
                loop[0].stop();
            }
            giveUp.setDisable(true);
            handOver.setDisable(true);
            DefenseGame.Outcome settled = game.outcome();
            boolean won = settled == DefenseGame.Outcome.HELD;
            banner.setText(won ? "SUCCESS" : "FAILED");
            banner.pseudoClassStateChanged(FAILED, !won);
            banner.setVisible(true);
            verdict.setText(verdictFor(game));
            // ⚠ THE BURST PLAYS BEFORE THE CALLER IS TOLD. The outcome is what decides whether an
            // intrusion lands, and handing it over first lets the deck tear the window down mid-blast
            // — the player would see the round vanish rather than see what happened to it.
            // ⚠ THE VIRUS IS COLLAPSED, NOT MERELY BURST, and only when the laser is what killed it.
            // A round lost on the clock or to the circle has no virus death to show, and playing one
            // would be the game telling the player they won.
            boolean killed = game.ending() == DefenseGame.Ending.VIRUS_DESTROYED;
            Runnable finish = () -> onResolved.accept(won ? Outcome.HELD : Outcome.BREACHED);
            // ⚠ ON A LOSS THE VERDICT IS HELD FIRST AND THE ROOM GOES RED SECOND. Washing while
            // FAILED is still arriving would bury the one thing the player is trying to read; the
            // order is see it, then lose the room.
            Runnable ending = won ? finish : () -> drown(wash, finish);
            burst(
                    debris,
                    game.snapshot(),
                    won,
                    killed ? () -> collapse(debris, virus, blocks, game.snapshot(), ending) : ending);
        };

        giveUp.onInvoke(() -> {
            game.concede();
            settle.run();
        });
        handOver.onInvoke(() -> {
            // ⚠ The rules roll it; this only reports. A view that decided the outcome would be the
            // client authoritative over whether an intrusion landed, which is I14 at its sharpest.
            game.runDaemon();
            settle.run();
        });

        // ⚠ THE ROUND POSTERIZES ITS OWN SHAPES, never a snapshot of itself: it has to stay live at
        // 60 fps and take key events, so it is quantised at the COLOUR rather than at the pixel.
        //
        // ⚠ Base colours are read ONCE, lazily, after CSS has been applied — a fill read before that
        // is Modena's default, and a fill overwritten from an un-styled base would lock the round to
        // the wrong palette for the rest of the attempt. And they are re-derived from the base every
        // time rather than from the current fill, or each step would quantise an already-quantised
        // colour and the round would march to black in a few seconds.
        java.util.Map<javafx.scene.shape.Shape, javafx.scene.paint.Color> bases = new java.util.HashMap<>();
        int[] levels = {256};
        double[] pressure = {0};
        java.util.function.IntConsumer repaintLevels = wanted -> {
            if (bases.isEmpty()) {
                collectFills(field, bases);
            }
            // ⚠ CONTRAST FIRST, THEN QUANTISE. Contrast moves a channel by a fraction and
            // quantisation snaps it to a level — the other way round rounds the value twice and
            // throws most of the effect away.
            bases.forEach((shape, base) ->
                    shape.setFill(Posterize.colour(Posterize.contrast(base, pressure[0] * 0.7d), wanted)));
        };

        double[] lastVirusY = {Balance.DEFENSE_FIELD_HEIGHT / 2};
        int[] frame = {0};
        Runnable step = () -> {
            game.tick(new DefenseGame.Input(
                    held.contains(KeyCode.UP),
                    held.contains(KeyCode.DOWN),
                    held.contains(KeyCode.LEFT),
                    held.contains(KeyCode.RIGHT),
                    held.contains(KeyCode.SPACE)));

            DefenseGame.Snapshot s = game.snapshot();
            player.setX(s.player().x() - Balance.DEFENSE_PLAYER_RADIUS);
            player.setY(s.player().y() - Balance.DEFENSE_PLAYER_RADIUS);
            virus.setTranslateX(s.virus().x());
            virus.setTranslateY(s.virus().y());
            // ⚠ THE ARMS TRAIL. Velocity is derived here from two frames rather than carried on the
            // snapshot: it is decoration, and putting it in the wire type would invite a rule to read
            // it. Leading arms compress and trailing ones extend, which is what makes the virus read
            // as something swimming rather than a sprite being translated.
            double vy = s.virus().y() - lastVirusY[0];
            lastVirusY[0] = s.virus().y();
            for (int i = 0; i < arms.size(); i++) {
                double angle = Math.PI * 2 * i / arms.size();
                // The arm points at `angle`; motion is straight down (+y) or up (-y), so how much it
                // leads or trails is just its own sine against the sign of the travel.
                double lead = Math.sin(angle) * Math.signum(vy);
                double push = Math.min(1.0d, Math.abs(vy) / 1.6d);
                arms.get(i).setScaleX(1 - lead * push * 0.45d);
            }
            chaser.setTranslateX(s.circle().x());
            chaser.setTranslateY(s.circle().y());
            // ⚠ THE BARBS ARE OUT WHENEVER THE CIRCLE CAN HURT YOU, which is the player's SHELTER
            // state and not the circle's own position. Shelter is the rule — a player in the band is
            // safe wherever the circle happens to be — so keying the spikes on where the circle is
            // drawn would show a smooth, harmless-looking ball that kills, and a bristling one that
            // cannot. The spike is the only warning the player gets, so it has to track the rule.
            double barb = s.sheltered() ? 0 : BARB[frame[0] % BARB.length];
            for (int i = 0; i < barbs.size(); i++) {
                barbs.get(i).setScaleX(barb);
                barbs.get(i).setScaleY(barb);
            }

            beam.setVisible(s.laser() != null);
            if (s.laser() != null) {
                beam.setX(s.laser().x());
                beam.setY(s.laser().y() - 1);
            }

            // ⚠ Rebuilt rather than pooled, and that is a measured-cost decision rather than a lazy
            // one: the field carries at most five triangles and about twenty squares, so this is a
            // few dozen nodes a frame. A pool would be faster and would also be a second place where
            // "what is on screen" is tracked, which is how a destroyed square comes to still be drawn.
            rebuild(blocks, s.blocks());
            rebuildShots(shots, s.triangles());
            // ⚠ Rebuilt with the shots rather than kept in step by hand: the ghosts must be exactly
            // the items that exist this frame, and two lists that can disagree is a ghost left behind
            // by a triangle that expired.
            rebuildGhosts(ghosts, s, pressure[0]);

            remaining.set(s.secondsLeft() / Balance.DEFENSE_ROUND_SECONDS);
            // ⚠ Only when the LEVEL changes, not every frame. Quantisation is a whole number of
            // steps, so this fires about ten times in a round rather than eighteen hundred.
            // ⚠ Pressure is continuous and drives the ABERRATION, which is redrawn every frame
            // anyway; the LEVEL is a staircase and drives the re-tint, which is not. Two different
            // cadences for two different costs.
            pressure[0] = 1 - Math.max(0, Math.min(1, s.secondsLeft() / Balance.DEFENSE_ROUND_SECONDS));
            int wanted = Posterize.levelsFor(s.secondsLeft() / Balance.DEFENSE_ROUND_SECONDS);
            if (wanted != levels[0]) {
                levels[0] = wanted;
                repaintLevels.accept(wanted);
                onPosterize.accept(wanted);
            }
            int left = Math.max(0, s.hitsAllowed() - s.hitsTaken());
            if (hearts.getChildren().size() != s.hitsAllowed()) {
                hearts.getChildren().clear();
                for (int i = 0; i < s.hitsAllowed(); i++) {
                    hearts.getChildren().add(heart());
                }
            }
            for (int i = 0; i < hearts.getChildren().size(); i++) {
                // ⚠ A SPENT HEART IS DIMMED, NEVER REMOVED. Taking it away makes the row shorter and
                // the remaining hearts move, so the thing that says "you are one hit from losing"
                // shifts position at the exact moment it matters. Two dark hearts also read as a
                // score of zero; one lit beside one dark reads as one left.
                hearts.getChildren().get(i).pseudoClassStateChanged(SPENT, i >= left);
            }
            hearts.setAccessibleText(left + " of " + s.hitsAllowed() + " hits remaining");
            shelter.setText(s.firewallBandWidth() <= 0 ? "NO FIREWALL" : s.sheltered() ? "SHELTERED" : "EXPOSED");
            shelter.pseudoClassStateChanged(SHELTERED, s.sheltered());

            // The glitch: each slice offset by a fixed, cycling amount. A table, not a random walk —
            // two players' rounds look the same and a render can be compared against the last one.
            for (int i = 0; i < glitch.size(); i++) {
                glitch.get(i).setTranslateX(GLITCH[(frame[0] + i * 3) % GLITCH.length]);
            }
            frame[0]++;

            if (!game.playing()) {
                settle.run();
            }
        };

        // ⚠ An ACTION-ONLY KeyFrame. No KeyValue, so nothing is interpolated and neither
        // UiContractTest check fires — see the class note. INDEFINITE, stopped by `settle`.
        Timeline timeline = new Timeline(new KeyFrame(
                Duration.seconds(1.0d / Balance.DEFENSE_TICKS_PER_SECOND), e -> step.run()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        loop[0] = timeline;

        // ⚠ Started and stopped from the SCENE, not from the constructor. A round left running after
        // its window closed would keep ticking, keep a reference to the whole view, and eventually
        // resolve a defence for a window nobody has open — the leak CycleGrid and CoreCage record
        // from the other side, with a consequence.
        boolean[] attached = {false};
        skin.sceneProperty().addListener((o, was, now) -> {
            if (now != null) {
                attached[0] = true;
                timeline.play();
                // ⚠ ASKED TWICE, and the deferred one is the one that works. The layer's visibility
                // is BOUND to it having children, so at the instant this listener fires — during the
                // children mutation that put us here — the subtree may still be invisible, and
                // **JavaFX refuses focus to a node in an invisible subtree**. The direct call covers
                // the case where it is already visible; the deferred one covers the case that broke.
                skin.requestFocus();
                javafx.application.Platform.runLater(skin::requestFocus);
            } else if (attached[0]) {
                timeline.stop();
            }
        });

        // ⚠ `-Ddefense.prewarm=N` WINDS THE REAL SIMULATION FORWARD N TICKS before the first paint,
        // and without it no render this project can produce shows a round in progress. A synchronous
        // `Scene.snapshot` fires no Timeline, so a harness photographs t=0 every time: no triangles in
        // the air, no laser, the circle still sitting on top of the virus — the one state
        // indistinguishable from the whole round being broken. `EyeMark.wind` and
        // `-Ddeck.glitchPhase` exist for exactly this, and both are documented as having been added
        // after a render reported a working feature by capturing its resting pose.
        //
        // ⚠ It drives the REAL tick with a real input rather than posing the shapes. A harness that
        // placed a triangle where it thought one should be would agree with itself and prove nothing.
        // ⚠ `-Ddefense.collapse=N` poses the black hole at frame N of its table. The collapse rides a
        // Timeline that a synchronous render never fires, and it only happens on a laser kill — which
        // a scripted prewarm reaches roughly never. Without it the whole sequence is unphotographable
        // and the render reports it working by showing a round it never played.
        double[] poseShield = {0};
        Integer poseCollapse = Integer.getInteger("defense.collapse");
        if (poseCollapse != null) {
            javafx.scene.shape.Circle hole = new javafx.scene.shape.Circle(
                    COLLAPSE[Math.min(poseCollapse, COLLAPSE.length - 1)][0] * Balance.DEFENSE_VIRUS_RADIUS * 2.6d);
            hole.setCenterX(34);
            hole.setCenterY(Balance.DEFENSE_FIELD_HEIGHT / 2);
            hole.getStyleClass().add("es-defensegame-hole");
            javafx.scene.shape.Circle rim = new javafx.scene.shape.Circle(hole.getRadius() + 1.5d);
            rim.setCenterX(34);
            rim.setCenterY(Balance.DEFENSE_FIELD_HEIGHT / 2);
            rim.getStyleClass().add("es-defensegame-hole-rim");
            debris.getChildren().setAll(hole, rim);
            double scale = COLLAPSE[Math.min(poseCollapse, COLLAPSE.length - 1)][1];
            virus.setScaleX(scale);
            virus.setScaleY(scale);
            virus.setOpacity(scale);
            // The shield is drawn by the frame loop below, so it is posed after `step.run()` — see
            // the end of this method.
            poseShield[0] = 1 - scale;
        }

        int prewarm = Integer.getInteger("defense.prewarm", 0);
        for (int i = 0; i < prewarm; i++) {
            game.tick(new DefenseGame.Input(i % 90 < 45, i % 90 >= 45, false, false, i % 40 == 0));
        }

        // The round's drips and its pulsing edge, on one clock. `Pulse.every` rather than `animate`:
        // this is not decoration on a readout, it is the readout — and a player with Reduce motion on
        // must still be told, in the one place they are looking, that this is an attack.
        List<Rectangle> dripping = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            Rectangle drip = new Rectangle(2 + (i % 3), 0);
            drip.getStyleClass().add("es-defensegame-drip");
            dripping.add(drip);
        }
        roundDrips.getChildren().setAll(dripping);
        int[] beat = {0};
        // ⚠ Pulled out of the Timeline so it can also run on LAYOUT. Everything here is measured off
        // `skin`, which is 0 × 0 until the first pass — so a build-time call places every drip at
        // zero height and a synchronous render photographs a round with no border and no drips, which
        // is the state indistinguishable from neither having been built.
        Runnable paintSkin = () -> {
            edge.setOpacity(EDGE_BEAT[beat[0] % EDGE_BEAT.length]);
            double h = skin.getHeight();
            for (int i = 0; i < dripping.size(); i++) {
                Rectangle drip = dripping.get(i);
                drip.setX((i + 0.5d) / dripping.size() * skin.getWidth());
                double cycle = (beat[0] * (0.7d + i * 0.23d) + i * 17) % 150;
                drip.setHeight(cycle < 105 ? cycle / 105.0d * h * 0.30d : 0);
            }
        };
        skin.layoutBoundsProperty().addListener((o, was, now) -> paintSkin.run());
        Timeline skinClock = new Timeline(new KeyFrame(Duration.millis(90), e -> {
            beat[0]++;
            paintSkin.run();
        }));
        skinClock.setCycleCount(Timeline.INDEFINITE);
        paintSkin.run();

        skin.sceneProperty().addListener((o, was, now) -> {
            if (now != null) {
                skinClock.play();
            } else {
                skinClock.stop();
            }
        });

        step.run();
        if (poseShield[0] > 0) {
            double cx = 34;
            double cy = Balance.DEFENSE_FIELD_HEIGHT / 2;
            for (javafx.scene.Node node : blocks.getChildren()) {
                if (node instanceof Rectangle square) {
                    double eagerness = Math.min(
                            1.0d,
                            1.35d - Math.hypot(square.getX() - cx, square.getY() - cy)
                                    / Balance.DEFENSE_FIELD_WIDTH);
                    double t = Math.min(1.0d, poseShield[0] * eagerness);
                    square.setX(square.getX() + (cx - square.getX()) * t);
                    square.setY(square.getY() + (cy - square.getY()) * t);
                    square.setScaleX(1 - t);
                    square.setScaleY(1 - t);
                }
            }
        }
        return skin;
    }

    private static final Set<KeyCode> TRACKED =
            EnumSet.of(KeyCode.UP, KeyCode.DOWN, KeyCode.LEFT, KeyCode.RIGHT, KeyCode.SPACE);

    private static final javafx.css.PseudoClass SHELTERED = javafx.css.PseudoClass.getPseudoClass("sheltered");

    private static final javafx.css.PseudoClass SPENT = javafx.css.PseudoClass.getPseudoClass("spent");

    private static final javafx.css.PseudoClass FAILED = javafx.css.PseudoClass.getPseudoClass("failed");

    /**
     * The round's edge, pulsing — the same {@code lub-dub … rest} shape the aftermath bloom uses.
     *
     * <p>⚠ A table, never a formula. And it never reaches zero while the round is open: an edge that
     * went fully dark between beats would read as the border flickering off rather than as a pulse.
     */
    /**
     * How long SUCCESS / FAILED stays up before the round is taken away.
     *
     * <p>⚠ Long enough to read, and it is the whole of what the player gets: the layer closes, the
     * horror stops, and the deck comes back. A verdict they did not manage to read is a round whose
     * outcome they have to infer from what the game does next.
     */
    private static final int VERDICT_HOLD_MS = 2600;

    private static final double[] EDGE_BEAT = {
        0.22, 0.70, 0.44, 0.26, 0.50, 0.30, 0.22, 0.20, 0.20, 0.20,
        0.20, 0.20, 0.20, 0.20, 0.20, 0.20,
    };

    /**
     * How the burst decays, frame by frame. A TABLE, never a formula — {@code SyncSpin}'s rule: a
     * curve written as arithmetic is an easing function in the source whatever it is called.
     *
     * <p>Each entry is {@code {distance travelled, opacity}} at that step. It ends at zero opacity,
     * so the shards clear themselves and nothing has to remember to remove them.
     */
    private static final double[][] BURST = {
        {0.10d, 1.00d}, {0.28d, 1.00d}, {0.46d, 0.92d}, {0.62d, 0.80d}, {0.76d, 0.66d},
        {0.88d, 0.52d}, {0.96d, 0.38d}, {1.00d, 0.24d}, {1.00d, 0.12d}, {1.00d, 0.00d},
    };

    /**
     * A heart, drawn.
     *
     * <p>⚠ Never a glyph: {@code U+2665} is in neither bundled face, and {@code GlyphCoverageTest}
     * scans source for literals — it has already rejected {@code U+26A0} and four block elements. Two
     * arcs and a triangle, which is what a heart is.
     */
    /**
     * The collapse: a black hole opens where the virus was, and the virus goes into it.
     *
     * <h2>⚠ A hole is a SHAPE here, not an effect</h2>
     *
     * §9 makes blur and drop shadows build-blocking, so there is no glow to reach for and no
     * distortion filter. What reads as a hole is two circles and a rule already in the palette: a disc
     * of the field's own void that grows, and a thin bright rim that grows with it — the rim is what
     * makes a black disc read as an opening rather than as a piece of missing render.
     *
     * <p>⚠ The virus is <b>pulled in</b>, not faded in place: it shrinks toward the hole's centre while
     * its opacity drops. Fading alone would read as the virus turning off; travelling into something
     * is what makes the hole the cause.
     *
     * <p>⚠ It calls {@code done} exactly once, on the last frame. That callback carries the outcome
     * that decides whether an intrusion landed — dropping it leaves the round unresolved and the layer
     * open forever, and running it twice applies the consequence twice.
     */
    private static void collapse(
            Pane debris, javafx.scene.Node virus, Pane blocks, DefenseGame.Snapshot at, Runnable done) {
        double cx = at.virus().x();
        double cy = at.virus().y();

        // ⚠ THE SHIELD GOES IN TOO, and it can be animated directly because the game loop has already
        // stopped: `settle` halts the Timeline, so nothing rebuilds these rectangles from a snapshot
        // any more and they are ours to move. While the round is live they are rebuilt every frame,
        // and anything set on them here would be thrown away on the next tick.
        List<Rectangle> squares = new ArrayList<>();
        List<double[]> from = new ArrayList<>();
        for (javafx.scene.Node node : blocks.getChildren()) {
            if (node instanceof Rectangle square) {
                squares.add(square);
                from.add(new double[] {square.getX(), square.getY()});
            }
        }

        javafx.scene.shape.Circle hole = new javafx.scene.shape.Circle(0);
        hole.setCenterX(cx);
        hole.setCenterY(cy);
        hole.getStyleClass().add("es-defensegame-hole");

        javafx.scene.shape.Circle rim = new javafx.scene.shape.Circle(0);
        rim.setCenterX(cx);
        rim.setCenterY(cy);
        rim.getStyleClass().add("es-defensegame-hole-rim");

        debris.getChildren().setAll(hole, rim);

        double fromX = virus.getTranslateX();
        double fromY = virus.getTranslateY();
        int[] frame = {0};
        Timeline pull = new Timeline(new KeyFrame(Duration.millis(45), e -> {
            double[] step = COLLAPSE[frame[0]];
            hole.setRadius(step[0] * Balance.DEFENSE_VIRUS_RADIUS * 2.6d);
            rim.setRadius(step[0] * Balance.DEFENSE_VIRUS_RADIUS * 2.6d + 1.5d);
            rim.setOpacity(step[2]);
            virus.setScaleX(step[1]);
            virus.setScaleY(step[1]);
            virus.setOpacity(step[1]);
            // Drawn toward the centre as it shrinks, so it goes IN rather than simply away.
            virus.setTranslateX(fromX + (cx - fromX) * (1 - step[1]));
            virus.setTranslateY(fromY + (cy - fromY) * (1 - step[1]));

            // ⚠ NEARER SQUARES FALL IN FIRST. A uniform pull moves the whole shield as one slab,
            // which reads as the picture sliding rather than as things being drawn in; scaling the
            // pull by distance is what makes it look like a gradient with a centre.
            double drawnIn = 1 - step[1];
            for (int i = 0; i < squares.size(); i++) {
                Rectangle square = squares.get(i);
                double[] origin = from.get(i);
                double distance = Math.hypot(origin[0] - cx, origin[1] - cy);
                // Normalised against the field's own width, so the falloff does not depend on where
                // the virus happened to be standing.
                double eagerness = Math.min(1.0d, 1.35d - distance / Balance.DEFENSE_FIELD_WIDTH);
                double t = Math.min(1.0d, drawnIn * eagerness);
                square.setX(origin[0] + (cx - origin[0]) * t);
                square.setY(origin[1] + (cy - origin[1]) * t);
                square.setScaleX(1 - t);
                square.setScaleY(1 - t);
            }
            frame[0]++;
            if (frame[0] >= COLLAPSE.length) {
                debris.getChildren().clear();
                blocks.getChildren().clear();
                virus.setVisible(false);
                done.run();
            }
        }));
        pull.setCycleCount(COLLAPSE.length);
        pull.play();
    }

    /**
     * The collapse, frame by frame: {@code {hole radius, virus scale, rim opacity}}.
     *
     * <p>⚠ A table, never a formula — {@code SyncSpin}'s rule. ⚠ The hole <b>opens fast and closes
     * slowly</b>, and the virus is gone before the hole is: something that shut at the same moment its
     * contents vanished would read as a wipe rather than as a thing being swallowed.
     */
    private static final double[][] COLLAPSE = {
        {0.25, 0.92, 0.40}, {0.60, 0.78, 0.85}, {0.88, 0.62, 1.00}, {1.00, 0.46, 0.95},
        {1.00, 0.30, 0.80}, {0.96, 0.18, 0.66}, {0.88, 0.09, 0.52}, {0.76, 0.03, 0.38},
        {0.60, 0.00, 0.26}, {0.42, 0.00, 0.16}, {0.24, 0.00, 0.08}, {0.00, 0.00, 0.00},
    };

    /**
     * Floods the round with red, then hands the outcome on.
     *
     * <p>⚠ It runs <b>after</b> the verdict hold, so FAILED has been readable for its full time before
     * anything covers it. What the player sees is the word, and then the room going under.
     *
     * <p>⚠ Under Reduce motion it is skipped entirely and {@code done} runs at once — a full-screen
     * colour flood is exactly the kind of thing that setting exists to refuse, and nothing is lost:
     * the verdict already said it in words.
     */
    private static void drown(Rectangle wash, Runnable done) {
        if (io.github.stoicswe.eyeandsickle.client.ui.Pulse.shared().reducedMotion()) {
            done.run();
            return;
        }
        int[] frame = {0};
        Timeline flood = new Timeline(new KeyFrame(Duration.millis(55), e -> {
            wash.setOpacity(DROWN[frame[0]]);
            frame[0]++;
            if (frame[0] >= DROWN.length) {
                done.run();
            }
        }));
        flood.setCycleCount(DROWN.length);
        flood.play();
    }

    /**
     * The flood, frame by frame. ⚠ It ends at full rather than tailing off: the deck's own fade takes
     * the round away from here, so this hands over a screen that is entirely red and the transition
     * out is a red rectangle disappearing rather than a half-washed game.
     */
    private static final double[] DROWN = {
        0.05, 0.14, 0.26, 0.40, 0.55, 0.68, 0.79, 0.88, 0.94, 0.98, 1.00, 1.00,
    };

    /**
     * Records a node's own fill, and its children's, so it can be re-quantised from the original.
     *
     * <p>⚠ Only {@code Shape}s with a plain {@code Color} fill. A gradient or an image fill has no
     * single colour to step, and the drips and the pulsing edge are deliberately left out — they are
     * the horror reaching into the round, and the horror is what is doing this to it.
     */
    private static void collectFills(
            javafx.scene.Node node, java.util.Map<javafx.scene.shape.Shape, javafx.scene.paint.Color> into) {
        if (node instanceof javafx.scene.shape.Shape shape
                && shape.getFill() instanceof javafx.scene.paint.Color colour
                && !shape.getStyleClass().contains("es-defensegame-drip")) {
            into.putIfAbsent(shape, colour);
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                collectFills(child, into);
            }
        }
    }

    private static Region heart() {
        javafx.scene.shape.SVGPath path = new javafx.scene.shape.SVGPath();
        // Two lobes and a point, at a 14-unit scale. Authored at size rather than scaled: a transform
        // would scale the stroke with it, and these are filled anyway.
        path.setContent("M7 13 C7 13 0 8.5 0 4.2 C0 1.6 1.9 0 3.9 0 C5.4 0 6.5 0.9 7 1.9 "
                + "C7.5 0.9 8.6 0 10.1 0 C12.1 0 14 1.6 14 4.2 C14 8.5 7 13 7 13 Z");
        path.getStyleClass().add("es-defensegame-heart");
        StackPane holder = new StackPane(path);
        holder.getStyleClass().add("es-defensegame-heart-holder");
        return holder;
    }

    /**
     * Throws a burst of shards out of whatever just died, then calls {@code done}.
     *
     * <h2>⚠ It is on the ROUND'S OWN CLOCK and must call back exactly once</h2>
     *
     * The caller hands the outcome on from {@code done}, and that outcome decides whether an
     * intrusion lands. A burst that fired the callback twice would apply the consequence twice; one
     * that never fired it would leave the attempt unresolved and the window open forever. The
     * Timeline stops itself on the last frame of the table and calls back there.
     *
     * <p>⚠ Under Reduce motion there is no burst at all and {@code done} runs immediately — the
     * information is in the verdict banner, which does not move.
     */
    private static void burst(Pane debris, DefenseGame.Snapshot snapshot, boolean virusDied, Runnable done) {
        double originX = virusDied ? snapshot.virus().x() : snapshot.player().x();
        double originY = virusDied ? snapshot.virus().y() : snapshot.player().y();

        // ⚠ THE VERDICT IS HELD BEFORE THE ROUND IS TORN DOWN, and the hold applies on EVERY path
        // including Reduce motion. SUCCESS or FAILED across the field is the answer to the only
        // question the player has, and handing the outcome straight on closes the layer within a few
        // frames of it appearing — long enough to see that something happened and not long enough to
        // read what.
        Runnable held = () -> {
            Timeline hold = new Timeline(new KeyFrame(Duration.millis(VERDICT_HOLD_MS), e -> done.run()));
            hold.play();
        };

        if (io.github.stoicswe.eyeandsickle.client.ui.Pulse.shared().reducedMotion()) {
            held.run();
            return;
        }

        int shards = 14;
        List<Rectangle> pieces = new ArrayList<>(shards);
        double[][] headings = new double[shards][2];
        for (int i = 0; i < shards; i++) {
            double angle = Math.PI * 2 * i / shards;
            // ⚠ Alternating reach, so the burst is ragged rather than a perfect ring. A ring reads as
            // a shockwave graphic; debris reads as something coming apart.
            double reach = (i % 2 == 0 ? 34 : 22) + (i % 3) * 6;
            headings[i][0] = Math.cos(angle) * reach;
            headings[i][1] = Math.sin(angle) * reach;
            Rectangle shard = new Rectangle(i % 2 == 0 ? 5 : 3, 3);
            shard.setX(originX);
            shard.setY(originY);
            shard.getStyleClass().add(virusDied ? "es-defensegame-shard-virus" : "es-defensegame-shard-player");
            pieces.add(shard);
        }
        debris.getChildren().setAll(pieces);

        int[] frame = {0};
        Timeline burst = new Timeline(new KeyFrame(Duration.millis(45), e -> {
            double[] step = BURST[frame[0]];
            for (int i = 0; i < pieces.size(); i++) {
                Rectangle shard = pieces.get(i);
                shard.setX(originX + headings[i][0] * step[0]);
                shard.setY(originY + headings[i][1] * step[0]);
                shard.setOpacity(step[1]);
            }
            frame[0]++;
            if (frame[0] >= BURST.length) {
                debris.getChildren().clear();
                held.run();
            }
        }));
        burst.setCycleCount(BURST.length);
        burst.play();
    }

    /** The band's per-frame slice offsets. A table, never a formula — {@code SyncSpin}'s rule. */
    private static final double[] GLITCH = {0, 2, -1, 3, 0, -2, 1, 4, -1, 0, 2, -3};

    private static String verdictFor(DefenseGame game) {
        return switch (game.ending()) {
            case VIRUS_DESTROYED -> "HELD — the virus is down and the attempt is denied.";
            case SHOT_DOWN -> "BREACHED — shot down. It got through.";
            case RUN_DOWN -> "BREACHED — it ran you down. It got through.";
            case TIME_OUT -> "BREACHED — thirty seconds gone. It got through.";
            case CONCEDED -> "BREACHED — you stood down. It got through.";
            case DAEMON_HELD -> "HELD — the counter-daemon got lucky. The attempt is denied.";
            case DAEMON_FAILED -> "BREACHED — the counter-daemon lost its roll. It got through.";
            case NONE -> "";
        };
    }

    /**
     * The virus: a drawn mark, never a glyph.
     *
     * <p>⚠ {@code GlyphCoverageTest} scans source for literals and has already rejected {@code U+26A0}
     * and four block elements — neither bundled face carries anything that would read as a virus. §9's
     * icon-set ban is not in play either: this is one shape for one subject, the same footing as
     * {@code SecurityMark} and {@code EyeMark}.
     */
    private static Group virusMark(List<Rectangle> arms) {
        Group group = new Group();
        Circle core = new Circle(Balance.DEFENSE_VIRUS_RADIUS * 0.55d);
        core.getStyleClass().add("es-defensegame-virus");
        group.getChildren().add(core);
        // Spikes, as a real virus is drawn — eight, so it reads as a capsid rather than a star.
        //
        // ⚠ `Rotate` WITH AN EXPLICIT (0,0) PIVOT, never `setRotate`. `Node.setRotate` turns a node
        // about the centre of its own bounds, so a spike laid out along +x spins in place instead of
        // swinging around the core — and wrapping it in a Group does not help, because a Group's
        // pivot is the centre of its children's bounds too. Rendered, the first version drew eight
        // spikes stacked on top of each other: one visible mark, pointing right.
        for (int i = 0; i < 8; i++) {
            Rectangle spike = new Rectangle(Balance.DEFENSE_VIRUS_RADIUS * 0.8d, 2);
            spike.setX(Balance.DEFENSE_VIRUS_RADIUS * 0.4d);
            spike.setY(-1);
            spike.getStyleClass().add("es-defensegame-virus");
            spike.getTransforms().add(new javafx.scene.transform.Rotate(360.0d * i / 8, 0, 0));
            // ⚠ Scaled about its INNER end, not its middle: an arm that lengthened from the centre
            // would grow out of both ends and pull free of the capsid.
            spike.setScaleX(1);
            arms.add(spike);
            group.getChildren().add(spike);
        }
        return group;
    }

    /**
     * The circle that chases the player — a core, and barbs that come out when it can hurt you.
     *
     * <p>⚠ Drawn as a Group positioned by {@code translate}, so the barbs can be scaled about the
     * centre without each one needing its own placement maths.
     */
    private static Group chaserMark(List<Polygon> barbs) {
        Group group = new Group();
        Circle core = new Circle(Balance.DEFENSE_CIRCLE_RADIUS);
        core.getStyleClass().add("es-defensegame-circle");
        group.getChildren().add(core);
        for (int i = 0; i < 9; i++) {
            double angle = Math.PI * 2 * i / 9;
            double r = Balance.DEFENSE_CIRCLE_RADIUS;
            double tip = r * 1.95d;
            double spread = 0.28d;
            Polygon barb = new Polygon(
                    Math.cos(angle) * tip, Math.sin(angle) * tip,
                    Math.cos(angle - spread) * r * 0.9d, Math.sin(angle - spread) * r * 0.9d,
                    Math.cos(angle + spread) * r * 0.9d, Math.sin(angle + spread) * r * 0.9d);
            barb.getStyleClass().add("es-defensegame-circle");
            barb.setScaleX(0);
            barb.setScaleY(0);
            barbs.add(barb);
            group.getChildren().add(barb);
        }
        return group;
    }

    /**
     * How far the barbs are out, frame by frame — they breathe rather than sitting rigid.
     *
     * <p>⚠ A table, never a formula ({@code SyncSpin}'s rule), and it never reaches zero: zero is what
     * SHELTERED means, and a barb that retracted fully on its own would read as the player being safe
     * when they are not.
     */
    private static final double[] BARB = {
        0.62, 0.74, 0.88, 1.00, 1.00, 0.92, 0.80, 0.68, 0.58, 0.55, 0.58, 0.68, 0.80, 0.92,
    };

    private static void rebuild(Pane pane, List<DefenseGame.Block> blocks) {
        pane.getChildren().clear();
        for (DefenseGame.Block block : blocks) {
            Rectangle square = new Rectangle(block.size(), block.size());
            square.setX(block.x());
            square.setY(block.y());
            square.getStyleClass().add("es-defensegame-block");
            pane.getChildren().add(square);
        }
    }

    /**
     * Lays the aberration ghosts under the moving items.
     *
     * <p>⚠ The split is {@code ±offset} on x only. Real convergence error is radial, and doing it
     * properly would mean a per-item direction from the field's centre — measurably more arithmetic
     * for something that is meant to be subtle, and {@code CrtOverlay} already records that honest
     * radial aberration is not affordable at this scale either.
     */
    private static void rebuildGhosts(Pane pane, DefenseGame.Snapshot s, double pressure) {
        pane.getChildren().clear();
        if (pressure <= 0.02d) {
            return;
        }
        double offset = 0.7d + pressure * 3.1d;
        double alpha = 0.16d + pressure * 0.34d;
        for (int side = 0; side < 2; side++) {
            double dx = side == 0 ? -offset : offset;
            String tint = side == 0 ? "es-defensegame-ghost-warm" : "es-defensegame-ghost-cool";

            Rectangle cube = new Rectangle(
                    Balance.DEFENSE_PLAYER_RADIUS * 2, Balance.DEFENSE_PLAYER_RADIUS * 2);
            cube.setX(s.player().x() - Balance.DEFENSE_PLAYER_RADIUS + dx);
            cube.setY(s.player().y() - Balance.DEFENSE_PLAYER_RADIUS);
            cube.getStyleClass().add(tint);
            cube.setOpacity(alpha);
            pane.getChildren().add(cube);

            javafx.scene.shape.Circle ring = new javafx.scene.shape.Circle(Balance.DEFENSE_CIRCLE_RADIUS);
            ring.setCenterX(s.circle().x() + dx);
            ring.setCenterY(s.circle().y());
            ring.getStyleClass().add(tint);
            ring.setOpacity(alpha);
            pane.getChildren().add(ring);

            for (DefenseGame.Shot shot : s.triangles()) {
                Polygon ghost = new Polygon(points(shot.x() + dx, shot.y(), shot.heading()));
                ghost.getStyleClass().add(tint);
                ghost.setOpacity(alpha);
                pane.getChildren().add(ghost);
            }
        }
    }

    /**
     * Draws the shots, each turned to face where it is aiming.
     *
     * <p>⚠ The heading comes from the SIMULATION, not from the difference between two frames. A
     * view that derived it from movement would point a shot along its path — which is right only
     * after it has passed the player, and wrong for the whole approach, where the nose is supposed to
     * track a target the flight has not caught up with.
     *
     * <p>⚠ Rotated by building the points, not by {@code setRotate}: a node's rotation pivots on its
     * own bounds centre, and these polygons are positioned in field coordinates rather than at the
     * origin — so a rotation would swing each shot around the middle of its own bounding box and
     * leave it somewhere else entirely.
     */
    private static void rebuildShots(Pane pane, List<DefenseGame.Shot> triangles) {
        pane.getChildren().clear();
        for (DefenseGame.Shot shot : triangles) {
            Polygon triangle = new Polygon(points(shot.x(), shot.y(), shot.heading()));
            triangle.getStyleClass().add("es-defensegame-triangle");
            pane.getChildren().add(triangle);
        }
    }

    /**
     * A triangle's three corners, <b>nose first</b>, turned to {@code heading}.
     *
     * <p>⚠ Package-private so the geometry can be checked without a toolkit — the same seam
     * {@code SecurityCenterView.latestOf} and {@code Anchoring.horizontal} exist for, and for the same
     * reason: a shape this small is not readable from a screenshot, and "it looks about right" is how
     * a nose pointing the wrong way ships.
     */
    static double[] points(double x, double y, double heading) {
        double r = Balance.DEFENSE_TRIANGLE_RADIUS;
        double cos = Math.cos(heading);
        double sin = Math.sin(heading);
        // Nose at +r along the heading; the tail corners behind it, spread across it.
        return new double[] {
            x + cos * r * 1.6d, y + sin * r * 1.6d,
            x - cos * r - sin * r, y - sin * r + cos * r,
            x - cos * r + sin * r, y - sin * r - cos * r,
        };
    }
}
