package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.CheatFacility;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch;
import io.github.stoicswe.eyeandsickle.engine.rules.Cheats;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import java.math.BigInteger;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The developer/cheat page in Settings.
 *
 * <h2>⚠ It is built from a {@link CheatFacility} or not at all</h2>
 *
 * There is no null-facility path and no disabled state. {@code CheatFacility.forSession} answers
 * empty for anything that is not a solo character, and {@code Views.settings} does not put this page
 * in the map when it does — so the category is absent rather than present-and-refusing. A page full
 * of greyed controls would advertise a capability that must never work online, which is the whole
 * thing the facility's absence from {@code GameSession} exists to prevent.
 *
 * <h2>⚠ Every control writes on RELEASE, never while dragging</h2>
 *
 * A {@code Slider} fires continuously, and each of these writes reaches the disk — {@code
 * LocalGameSession} persists on every cheat, deliberately, because a cheat is too consequential to
 * leave to the 30-second autosave. Writing per frame would mean dozens of full serialise-and-move
 * cycles on the FX thread for one drag, and the disk lamp flickering like a fault. The readout beside
 * each slider follows the drag; the write does not. Same split the sound page already makes.
 *
 * <h2>⚠ Nothing here is coloured by outcome</h2>
 *
 * §2.1 spends amber on cycles doing work and rations alarm to loss. "A cheat is on" is neither — it
 * is not a loss and it is not the rig working — so state is carried by the switch position and by
 * the words, which survive greyscale and reach a screen reader (§4.4). The one exception is the
 * header line, which is plain text.
 */
public final class CheatsView {

    private CheatsView() {}

    /** The grants the button row offers, in whole ethecoin. */
    private static final long[] GRANTS = {100L, 1_000L, 10_000L};

    /**
     * Builds the page.
     *
     * @param facility the character's facility — never null; see the class note
     * @param onChanged run after any cheat lands, so Settings can refresh anything outside this page
     * @param onHidden run after the page has hidden itself, so Settings can take it off the sidebar.
     *     ⚠ The view cannot do that itself — the page map and the rail belong to {@code Views}, and a
     *     panel that reached up to edit the window containing it would be a second place that decides
     *     which categories exist.
     */
    public static Region create(CheatFacility facility, Runnable onChanged, Runnable onHidden) {
        return create(facility, onChanged, onHidden, null);
    }

    /**
     * @param defense the door to the defence round, or {@code null} where there is no deck to open a
     *     window on (the render harness, the main menu). ⚠ Null <b>hides</b> the control rather than
     *     showing one that does nothing — a developer button that silently fails is worse than an
     *     absent one, because the reasonable conclusion is that the feature is broken.
     */
    public static Region create(
            CheatFacility facility,
            Runnable onChanged,
            Runnable onHidden,
            io.github.stoicswe.eyeandsickle.client.view.DefenseArming defense) {
        VBox page = new VBox(10);
        Label result = Views.wrapped("");

        // ⚠ Rebuilt from a fresh snapshot after every action rather than from what the control was
        // set to. A cheat is clamped by the rules (the ceiling to the ladder's floor, heat to 0–100,
        // a grant to its maximum), so the value that landed is often not the value asked for — and a
        // panel that showed the request rather than the result would disagree with the game about
        // what the player just did.
        Runnable[] refresh = new Runnable[1];
        boolean[] syncing = {false};

        // ── compute ──────────────────────────────────────────────────────────────────────────
        Slider ceiling = new Slider(0, Cheats.MAX_CYCLE_CEILING, 0);
        ceiling.setMajorTickUnit(256);
        ceiling.setShowTickMarks(true);
        ceiling.setBlockIncrement(8);
        Label ceilingValue = Ui.micro("");
        Button ceilingClear = new Button("Back to the ladder");

        // ── money ────────────────────────────────────────────────────────────────────────────
        HBox grants = new HBox(6);
        Button zeroBalance = new Button("Set to zero");

        // ── heat ─────────────────────────────────────────────────────────────────────────────
        Slider heat = new Slider(0, 100, 0);
        heat.setMajorTickUnit(25);
        heat.setShowTickMarks(true);
        heat.setBlockIncrement(5);
        Label heatValue = Ui.micro("");
        Switch heatFrozen = new Switch("Freeze personal heat where it is");

        // ── events ───────────────────────────────────────────────────────────────────────────
        Slider chance = new Slider(0, Cheats.MAX_EVENT_CHANCE_PERCENT, 100);
        chance.setMajorTickUnit(100);
        chance.setShowTickMarks(true);
        chance.setBlockIncrement(25);
        Label chanceValue = Ui.micro("");
        HBox intrusions = new HBox(6);
        Button reprisal = new Button("Roll an attempt");

        // ── flags ────────────────────────────────────────────────────────────────────────────
        Switch thermal = new Switch("Thermal recovery");
        Switch autoClear = new Switch("Open every breach pre-solved");
        Switch instantTasks = new Switch("Finish timed work immediately");
        Label instantNote = Ui.micro("");
        Switch instantPurchases = new Switch("Hand over purchases without waiting for a block");
        Label purchaseNote = Ui.micro("");

        // ── network ──────────────────────────────────────────────────────────────────────────
        Button reveal = new Button("Reveal the whole map");
        Label revealNote = Ui.micro("");
        Button learn = new Button("Gain all info on every machine");
        Label learnNote = Ui.micro("");
        Button solve = new Button("Solve the open breach");

        Button reset = new Button("Reset to defaults");
        Button hide = new Button("Turn developer options off");

        // Applied through one seam so the "act, report, re-read" order cannot be got wrong at one of
        // the fourteen call sites — and so a control that fires while the panel is syncing itself
        // cannot write back the value it was just handed. The rounded-corners setting records that
        // exact defect: displaying the effective state WROTE it.
        java.util.function.Consumer<java.util.function.Supplier<String>> act = action -> {
            if (syncing[0]) {
                return;
            }
            result.setText(action.get());
            refresh[0].run();
            if (onChanged != null) {
                onChanged.run();
            }
        };

        for (long amount : GRANTS) {
            Button give = new Button("+" + amount + " EC");
            give.setOnAction(e -> act.accept(() -> facility.grant(Ethecoin.ofWholeEthecoin(amount)
                    .wei())));
            grants.getChildren().add(give);
        }
        zeroBalance.setOnAction(e -> act.accept(() -> facility.setBalance(BigInteger.ZERO)));

        for (int depth = 1; depth <= 3; depth++) {
            int tier = depth;
            Button plant = new Button("Tier " + tier);
            plant.setOnAction(e -> act.accept(() -> facility.triggerIntrusion(tier)));
            intrusions.getChildren().add(plant);
        }
        reprisal.setOnAction(e -> act.accept(facility::triggerReprisal));

        // ⚠ THE ROUND IS PLAYED FIRST AND THE CONSEQUENCE APPLIED AFTER, which is the whole shape of
        // the defence loop: a reprisal that is turned back must not land. Losing calls the same
        // `triggerReprisal` the button above it does, so there is one path by which an intrusion
        // reaches the rig rather than two that can come to disagree.
        // The attacker's virus tier is a control here, because it is the one thing a tester cannot
        // otherwise vary: in play it comes from whoever is attacking.
        Slider virusTier = new Slider(1, 4, 1);
        virusTier.setMajorTickUnit(1);
        virusTier.setMinorTickCount(0);
        virusTier.setSnapToTicks(true);
        virusTier.setShowTickMarks(true);
        Label virusTierValue = Ui.micro("");
        virusTier.valueProperty().addListener((o, was, now) -> virusTierValue.setText(
                "attacking with a tier " + (int) Math.round(now.doubleValue()) + " virus"));
        virusTierValue.setText("attacking with a tier 1 virus");
        Button playDefense = new Button("Play a defence round");
        playDefense.setOnAction(e -> {
            if (defense == null) {
                return;
            }
            result.setText("defending…");
            defense.open("a machine answering your scan", (int) Math.round(virusTier.getValue()), outcome -> {
                if (outcome == io.github.stoicswe.eyeandsickle.client.view.DefenseGameView.Outcome.HELD) {
                    result.setText("held — the attempt was turned back, and nothing landed");
                } else {
                    result.setText(facility.triggerReprisal());
                }
                refresh[0].run();
                if (onChanged != null) {
                    onChanged.run();
                }
            });
        });
        playDefense.setVisible(defense != null);
        playDefense.setManaged(defense != null);

        ceiling.valueProperty().addListener((o, was, now) -> {
            long wanted = Math.round(now.doubleValue());
            ceilingValue.setText(wanted <= 0 ? "the ladder decides" : wanted + " cycles");
        });
        ceiling.setOnMouseReleased(e -> act.accept(() -> facility.setCycleCeiling(Math.round(ceiling.getValue()))));
        ceilingClear.setOnAction(e -> act.accept(() -> facility.setCycleCeiling(0)));

        heat.valueProperty()
                .addListener((o, was, now) ->
                        heatValue.setText((int) Math.round(now.doubleValue()) + " of " + 100));
        heat.setOnMouseReleased(e -> act.accept(() -> facility.setHeat((int) Math.round(heat.getValue()))));

        chance.valueProperty().addListener((o, was, now) -> {
            int percent = (int) Math.round(now.doubleValue());
            chanceValue.setText(percent == 0 ? "never" : percent == 100 ? "100% — the tuned rule" : percent + "%");
        });
        chance.setOnMouseReleased(e -> act.accept(() -> facility.setEventChance((int) Math.round(chance.getValue()))));

        thermal.selectedProperty().addListener((o, was, now) -> act.accept(() -> facility.setThermalRecovery(now)));
        heatFrozen.selectedProperty().addListener((o, was, now) -> act.accept(() -> facility.setHeatFrozen(now)));
        autoClear.selectedProperty().addListener((o, was, now) -> act.accept(() -> facility.setBreachAutoClear(now)));
        instantTasks.selectedProperty().addListener((o, was, now) -> act.accept(() -> facility.setInstantTasks(now)));
        instantPurchases
                .selectedProperty()
                .addListener((o, was, now) -> act.accept(() -> facility.setInstantPurchases(now)));

        reveal.setOnAction(e -> act.accept(facility::revealNetwork));
        learn.setOnAction(e -> act.accept(facility::learnEverything));
        solve.setOnAction(e -> act.accept(facility::solveBreach));
        reset.setOnAction(e -> act.accept(facility::reset));
        // ⚠ NOT routed through `act`. That seam ends by refreshing the panel, and by the time this
        // returns the panel is on its way off the sidebar — refreshing a page that is being removed
        // reads every control back out of a facility whose state was just cleared, for a node
        // nothing will show again. The order that matters is: turn everything off, then leave.
        hide.setOnAction(e -> {
            facility.conceal();
            if (onChanged != null) {
                onChanged.run();
            }
            if (onHidden != null) {
                onHidden.run();
            }
        });

        refresh[0] = () -> {
            CheatFacility.Snapshot now = facility.state();
            // ⚠ The guard, not a nicety. Every setSelected/setValue below fires the listener that
            // APPLIES the cheat, so without it painting the current state writes it back — and the
            // three switches would re-apply themselves on every refresh, forever.
            syncing[0] = true;
            try {
                ceiling.setValue(now.cycleCeiling());
                ceilingValue.setText(
                        now.cycleCeiling() <= 0
                                ? "the ladder decides — " + now.effectiveCycles() + " cycles"
                                : now.cycleCeiling() + " cycles (ladder gives " + now.ladderCeiling() + ")");
                ceilingClear.setDisable(now.cycleCeiling() <= 0);
                heat.setValue(now.heat());
                heatValue.setText(now.heat() + " of 100");
                chance.setValue(now.eventChancePercent());
                chanceValue.setText(
                        now.eventChancePercent() == 100 ? "100% — the tuned rule" : now.eventChancePercent() + "%");
                thermal.setSelected(now.thermalRecovery());
                heatFrozen.setSelected(now.heatFrozen());
                autoClear.setSelected(now.breachAutoClear());
                instantTasks.setSelected(now.instantTasks());
                // ⚠ Names what is in flight rather than only what the switch does. A player who
                // turns this on with a six-minute audit running needs to know that audit is about to
                // land — a switch whose effect on work already started is left unstated reads as one
                // that only applies to the next thing.
                instantNote.setText(
                        now.runningTasks() == 0
                                ? "nothing is running; this applies to the next thing you start"
                                : now.runningTasks() + " task" + (now.runningTasks() == 1 ? "" : "s")
                                        + " running — "
                                        + (now.instantTasks()
                                                ? "landing on the next tick"
                                                : "turning this on lands them on the next tick"));
                instantPurchases.setSelected(now.instantPurchases());
                // ⚠ Reports what is being HELD, which is zero whenever the switch is on — the
                // release happens as it is flipped. Saying so is what stops the count reading as a
                // figure that failed to move.
                purchaseNote.setText(
                        now.instantPurchases()
                                ? "nothing is held while this is on"
                                : now.heldPackages() == 0
                                        ? "no purchase is waiting on a block"
                                        : now.heldPackages() + " package"
                                                + (now.heldPackages() == 1 ? " is" : "s are")
                                                + " waiting on a block — turning this on releases "
                                                + (now.heldPackages() == 1 ? "it" : "them"));
                // ⚠ Not offered is not refused, and the count is why the button can say so. A
                // disabled "reveal" with no explanation reads as broken; one that says the map is
                // already complete has answered the question.
                reveal.setDisable(now.hiddenMachines() == 0);
                learn.setDisable(now.unscannedMachines() == 0);
                learnNote.setText(
                        now.unscannedMachines() == 0
                                ? "every machine on the map is fully scanned"
                                : now.unscannedMachines() + " machine"
                                        + (now.unscannedMachines() == 1 ? "" : "s")
                                        + " with something still unknown");
                revealNote.setText(
                        now.hiddenMachines() == 0
                                ? "every machine in the world is already on the map"
                                : now.hiddenMachines() + " machine"
                                        + (now.hiddenMachines() == 1 ? "" : "s") + " not yet on the map");
                solve.setDisable(!now.breachOpen());
                zeroBalance.setDisable(now.balanceWei().signum() == 0);
            } finally {
                syncing[0] = false;
            }
        };
        refresh[0].run();

        page.getChildren()
                .addAll(
                        Views.wrapped("Developer overrides for this character. They are solo only — there is no "
                                + "route from here to a home server, and there must never be one: the invariants "
                                + "these step over exist to keep a shared economy honest, and a cheat that could "
                                + "reach one would be forged authoritative state. Every change below is written "
                                + "to the rig log, so a balance you cannot account for later has an entry saying "
                                + "where it came from."),
                        new Separator(),
                        Ui.label("Compute ceiling"),
                        new HBox(8, ceiling, ceilingValue),
                        ceilingClear,
                        Views.wrapped("Overrides what the compute ladder derives from the hardware you hold. It "
                                + "is an override on the derived figure rather than a write to the rig, so the "
                                + "next reconcile cannot quietly undo it — and clearing it hands the rig straight "
                                + "back to whatever your items actually give."),
                        new Separator(),
                        Ui.label("Ethecoin"),
                        grants,
                        zeroBalance,
                        Views.wrapped("Made out of nothing, so no ledger row is written: the ledger is the chain's "
                                + "record of value moving between addresses and this did not move, it was "
                                + "invented. A row for it would be a transaction with no counterparty."),
                        new Separator(),
                        Ui.label("Personal heat"),
                        new HBox(8, heat, heatValue),
                        heatFrozen,
                        Views.wrapped("Freezing pins the meter where you put it: every rule that would raise heat "
                                + "is asked first and leaves it alone, so a breach summary never claims a rise "
                                + "that did not happen."),
                        new Separator(),
                        Ui.label("Thermal budget"),
                        thermal,
                        Views.wrapped("Off, released cycles come straight back instead of nursing the recovery "
                                + "curve. This is the pressure the whole Thermal Budget exists to apply, so "
                                + "turning it off changes how the game plays more than anything else here."),
                        new Separator(),
                        Ui.label("Timed work"),
                        instantTasks,
                        instantNote,
                        Views.wrapped("Skips the wait on anything with a duration — a scan, a sweep, a port scan, a "
                                + "download, an extraction, a firmware flash. The work itself still happens: the "
                                + "audit reports what it found, the sweep discovers what it discovers, the file "
                                + "arrives, the held cycles rejoin the recovery curve, and a noticed scan is still "
                                + "answered. It is the duration that is skipped, not the outcome."),
                        Views.wrapped("It lands on the engine's next tick — about a second — rather than the instant "
                                + "you press the button, because it is asked at the one place the game decides a "
                                + "task is done rather than at each place one is started. A download you have paused "
                                + "is left alone: a hold is expressed as a deadline that never arrives, so finishing "
                                + "it here would be this switch quietly overruling that one."),
                        new Separator(),
                        Ui.label("Purchases"),
                        instantPurchases,
                        purchaseNote,
                        Views.wrapped("A bought tool arrives as a vendor package and stays one until the payment "
                                + "that bought it is mined — that rename is the whole lock, which is why a held "
                                + "package looks held in `ls` as well as in the file manager. With this on the "
                                + "seller stops waiting: the package is repacked on arrival, or straight away if it "
                                + "is already sitting in Downloads, and it installs and resells at once."),
                        Views.wrapped("It waives the seller's escrow and does not touch the chain. The payment is "
                                + "still pending, still in the mempool and still confirms in its own time at the fee "
                                + "you paid — stamping it into a block it was never in would make the LEDGER window "
                                + "lie about the one thing it exists to report. Turning this back off re-locks "
                                + "nothing already handed over; it restores the wait on the next purchase."),
                        new Separator(),
                        Ui.label("Breaches"),
                        autoClear,
                        solve,
                        Views.wrapped("A solved breach still resolves — loot, noise, heat and the foothold all "
                                + "land, and the counter-hack still rolls. It is the puzzle that is skipped, not "
                                + "the break-in; skipping the resolution would leave the target reading as "
                                + "unbreached on the map and refusing a shell."),
                        new Separator(),
                        Ui.label("Intrusions"),
                        new HBox(8, chance, chanceValue),
                        Views.wrapped("Scales the chance that a sweep which was noticed, or a breach which was "
                                + "answered, puts a parasite on your rig. It scales the chance and never the "
                                + "draw, so a replay from a stored seed is still a replay."),
                        Ui.label("Plant one now"),
                        intrusions,
                        Views.wrapped("Goes through the same rule a real counter-hack does, so it is dressed, "
                                + "allocated and heat-charged like one that arrived on its own — and it can be "
                                + "found by an audit exactly the same way."),
                        Ui.label("Somebody answers"),
                        reprisal,
                        Views.wrapped("The attempt rather than the outcome: a machine you have found rolls the "
                                + "turn it takes when it notices you — usually it logs where you came from and "
                                + "lets it go, sometimes it takes the newest package out of Downloads, "
                                + "occasionally it plants a miner. A defended machine hits harder. Most presses "
                                + "report nothing taken, because most detections are; press it a few times to "
                                + "see the other two."),
                        Views.wrapped("A rig that already carries a parasite is only probed — one at a time is "
                                + "the rule, so the planting arm cannot come up until the one you have is "
                                + "cracked off. Every new character is issued one, so on a fresh rig this button "
                                + "will report a theft or nothing and never a miner."),
                        Ui.label("The defence round"),
                        new HBox(8, virusTier, virusTierValue),
                        playDefense,
                        Views.wrapped("Plays the minigame the attempt would go through, then applies what it "
                                + "decided: hold it and nothing lands, lose it and the same reprisal above is "
                                + "rolled. Arrow keys move, space fires, thirty seconds. An armed firewall puts "
                                + "a band beside the midline where the circle cannot touch you — its width is "
                                + "the tier, and with none armed there is no band at all."),
                        new Separator(),
                        Ui.label("Network"),
                        reveal,
                        revealNote,
                        Views.wrapped("Puts every machine in the world on the map and names it — what a sweep "
                                + "plus a Passive Sniffer would have given. It grants no footholds: those are the "
                                + "product of a breach, and there is a switch above for that."),
                        learn,
                        learnNote,
                        Views.wrapped("Fills in the recon file of every machine already on the map — firewall, OS, "
                                + "cycles, downloads and both vault readings, as if every rung of the port-scan "
                                + "ladder had been run. Machines on the map only, so it pairs with the button "
                                + "above rather than quietly doing its job too. It costs no cycles, makes no "
                                + "noise, and is not counted as a scan: a file reporting scans nobody ran would "
                                + "put a detection ratio beside it that is a fraction of a number that never "
                                + "happened."),
                        new Separator(),
                        reset,
                        Views.wrapped("Puts every override above back to the ordinary rules and leaves this page "
                                + "where it is."),
                        hide,
                        Views.wrapped("Resets everything as well, then hides this page again — it comes back with "
                                + "the same key sequence. It resets rather than only hiding on purpose: a "
                                + "character left with an override in force and no visible control to undo it is "
                                + "one that looks broken rather than one that was cheated."),
                        result);
        return page;
    }
}
