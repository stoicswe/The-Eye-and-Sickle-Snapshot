# 19 — The Defence Minigame

**Status:** Decided 2026-08-10, on explicit direction. The mechanic was deliberately unchosen until now — `15-open-questions.md` records `DefenseGameView` shipping as a `[PROTOTYPE]` with a WIN button, a FAIL button and nothing between them, on the grounds that inventing one in code is what `CLAUDE.md` asks not to happen.
**Depends on:** `09-defense-and-hardening.md` (the firewall), `01-core-resources.md` §3 (heat), `ui-design-language.md` §5 (motion)
**Depended on by:** `04-mining.md` §5.1 (the crack is the *other* minigame and stays that way)

---

## 1. What it is

A real-time arcade round, in the shape of **Yars' Revenge**. The player is a cube on the right of
the field; a virus sits on the left behind a field of breakable squares and tries to get past. The
player has **30 seconds** to destroy the virus with a laser. Destroy it and the intrusion is denied;
run out of time, or die, and it lands.

It is the **defensive** axis, and it is deliberately nothing like the breach board:

| | Breach (`05`, `16`) | Defence (this) |
|---|---|---|
| Posture | You are on somebody else's machine | Somebody is on yours |
| Time | Turn-based; the board waits | Real-time; 30 seconds, running |
| Resource | Attention, spent per move | Reflex; nothing is spent |
| Heat | Loud, by design | **None, ever — Invariant I9** |
| Failure | You are traced | The attempt gets through |

> ⚠ **They must not converge.** The breach board is a *simultaneity* puzzle — its anti-bot property
> (**I10**) is that a human cross-references two documents at once. This one is a *reflex* test, and
> reflex is the shape automation is best at. That is a real cost and it is accepted here rather than
> hidden: see §7.

---

## 2. The field

A fixed logical field, `480 × 300` units, scaled to whatever the window is. Everything below is in
those units, and every figure is a constant in `Balance`.

```
 0                          240 (midline)                      480
 ┌───────────┬───────────────┬──┬──────────────────────────────┐
 │           │  ▨ ▨   ▨      │▓▓│                              │
 │    ###    │    ▨ ▨   ▨    │▓▓│           ▭ player           │
 │   virus   │  ▨   ▨ ▨      │▓▓│                              │
 │    ###    │    ▨   ▨      │▓▓│         ● circle             │
 │           │  ▨ ▨   ▨      │▓▓│                              │
 └───────────┴───────────────┴──┴──────────────────────────────┘
   corridor      shield      firewall        the player's half
                              band
```

- **The player is confined to the right half** — `x ≥ 240`. That is the whole geometry: the virus is
  unreachable, so the laser is the only thing that crosses.
- **The shield** is a grid of breakable squares in front of the virus, randomly filled. It is what
  makes the laser a problem to solve rather than a button to press. ⚠ It **spans the full height of
  the field**: a gap at either end is a free lane, and a player parked there would have a clear shot
  at a virus that patrols the whole height without ever cutting through anything.
- ⚠ **The virus's own corridor is always clear.** The squares never box it in — it has room to move
  up and down for the whole round, which is what makes it a moving target rather than a stuck one.

---

## 3. The pieces

### 3.1 The player — a cube

Arrow keys: up, down, forward (left, toward the virus), backward (right). Movement is free within
the right half. **Spacebar fires the laser**, one shot in flight at a time.

⚠ **The cube has weight.** It accelerates into a direction and coasts out of one rather than starting
and stopping dead — so it leans into a turn and a dodge has to be started slightly early. The top
speed is unchanged: glide alters how it gets there, never how fast it is, because the circle is only
escapable at all on the strength of the player being faster than it.

> ⚠ **One laser at a time is the difficulty.** Held fire with unlimited shots clears the shield in
> seconds and the round becomes a formality. It is also what makes position matter: a shot is
> committed the moment it leaves, and while it is out there you have nothing.

### 3.2 The virus

Sits at the far left and moves **up and down**, biased away from the player's line — it is trying not
to be lined up with. It fires **triangles**, at most **five** in the air at once.

### 3.3 The triangles

Fired at the player, with **basic homing**: while a triangle is still approaching it steers toward
the player, and **once it has passed the player it stops steering and continues straight**. That is
the whole skill of dodging them — get out of the way late, and it commits.

**Two hits lose the round.**

⚠ **A shot's nose tracks the player while it is approaching**, even where the flight path has not
caught up — which is what makes an incoming triangle read as something hunting rather than something
falling. The instant it passes it **points along its own travel** and stays there: committed, no
longer interested. Both are read off the same condition the homing uses; two conditions would
eventually disagree, and a shot still visibly tracking a player it can no longer turn toward teaches
the dodge wrong.

### 3.4 The circle

One, from the virus, from the start. It follows the player at a **constant pace**, forever, and does
not stop, turn away or expire. **One touch loses the round.**

> ⚠ It is slower than the player. It cannot catch anybody who keeps moving; what it does is take
> away *standing still*, which is what makes the 30 seconds feel short.

⚠ **It grows barbs whenever it can hurt you, and smooths over when it cannot** — keyed on the
player's shelter, which is the rule, and never on where the circle happens to be drawn. The spike is
the only warning the player gets, so it has to track the rule rather than the picture.

### 3.5 The firewall band

⚠ **This is the first mechanical effect the firewall has ever had.** `09` §1 has specified
"flat difficulty increase on incoming breach attempts" since the design sessions, and until now the
tool reserved compute and did nothing else.

A **glitchy vertical band immediately to the right of the midline** — the left edge of the player's
own half. While the player is inside it, **the circle cannot hurt them.** Triangles still can.

| Firewall tier | Band |
|---|---|
| none armed | no band at all |
| T1 | narrow |
| T2 | wider |
| T3 | widest |

Three consequences, all intended:

1. **It is shelter, not safety.** Triangles reach into it, so camping is punished by the thing that
   comes five at a time rather than by the thing that comes one at a time.
2. **It is the furthest point from the fight.** Sitting in it is sitting at the far end of the field
   from a virus you have 30 seconds to kill.
3. **A tier is width, so a better firewall buys a bigger margin for error** rather than a different
   rule. That is the "flat difficulty increase" `09` asks for, expressed as space.

### 3.5a Firing from cover — the shot goes backwards

⚠ **A laser fired from inside the firewall band travels the wrong way**, out of the player's own half
and off the field. It is still spent, and the one-shot rule means they have nothing until it leaves.

That is what stops shelter being free. Before it, the band was a place where the circle could not
reach you *and* your laser still crossed the whole field — safety at no cost, with only the clock
arguing against camping. **Hiding and shooting are now two decisions**, and you have to leave cover
to threaten anything.

⚠ **Spent rather than refused.** A refusal ("you cannot fire in here") is the softer rule and teaches
nothing; this says *you can, and here is what it costs*, which is how the rest of the game talks.

### 3.6 The Tarpit — slows the virus, and nothing else

⚠ **The second armed defence with a mechanical effect** (`09` §1 sells it as *"slows every intruder
action"*). Here the intruder is the **virus**: an armed Tarpit cuts its up-and-down patrol to
`Balance.DEFENSE_TARPIT_VIRUS_SPEED` of normal, making it easier to line up on.

⚠ **It does NOT slow the triangles or the circle**, on explicit direction. That reading was available
and is the wrong one: slowing incoming fire is *damage reduction*, which is the firewall's job, and
the two tools would then do one thing between them. Slowing the target buys you the shot; slowing the
shots would buy you survival.

### 3.7 The Auto-Counter Daemon — it plays the round for you, badly

⚠ **This is the only place in the game where a bot plays a puzzle, and it steps on Invariant I10**
("bots assist, never substitute; a bot never solves the puzzle for the player").

With the daemon armed, the round offers to take itself: one roll, **capped at 50%**
(`Balance.DEFENSE_DAEMON_MAX_ODDS`) and falling against a higher-tier attacking virus — 50 / 40 / 30 /
20% for tiers 1–4. The control states its odds on its face.

What makes it defensible, and the whole of what does:

1. **It is strictly worse than playing.** A coin flip at its very best, against a player who can win
   outright. It answers *"I am not at the keyboard"*, never *"I would rather not play"* — and being
   away is exactly what `09` §1 already sells the tool for: *"launches a weak counter-attack when you
   are raided while logged off."*
2. **It costs 18 standing cycles** and is schematic-gated. It is the most expensive thing on the
   defensive shelf and it buys the worst outcome available.
3. ⚠ **Raising the cap is the edit that deletes the minigame.** At 80% the correct play is to press
   the daemon every round and never touch the arrow keys. `DefenseGameTest.Daemon` holds the ceiling.

---

## 4. Winning and losing

| | |
|---|---|
| **HELD** | The laser hits the virus |
| **BREACHED** | Two triangle hits · one circle touch · the 30 seconds run out |

There is no draw and no partial credit — `DefenseGameView`'s existing contract is that a defence
attempt resolves to exactly one of two outcomes and hands it back **once**.

⚠ **A timeout is a loss, not a stalemate.** The attacker is trying to get in and the player is trying
to stop them; running the clock out is the attacker succeeding. Without that, hiding in the firewall
band for thirty seconds is a winning strategy and the round has no shape.

---

## 5. The Breach Virus — the payload, and a consumable

⚠ **Solving the board is no longer the whole of a breach.** The puzzle gets you *onto* the machine;
uploading a virus is what **takes** it. A virus is a market consumable in four tiers, spent on every
breach of a foreign machine.

| Tier | Price | Solo: a solved board lands | Against a player: virus lives |
|---|---|---|---|
| 1 | 5 EC | 55% | 1 |
| 2 | 14 EC | 70% | 2 |
| 3 | 38 EC | 80% | 3 |
| 4 | 95 EC | 90% | 4 |

⚠ **A crack needs no virus.** Cracking a parasite off your own rig is *defence* — **I9** already gives
it zero heat on every outcome and `04` §5.1 makes it the tutorial for the whole breach system.
Charging a bought consumable for it would put the game's teaching behind a purchase.

⚠ **A tier buys LIVES, never lethality.** Against a defender it adds hits-to-kill and nothing else —
not shot rate, not homing, not the circle. Otherwise an attacker would be buying the *defender's
death*, and a defence would be losable at the shop.

### 5.1 Where the money goes, and why the loop cannot stall

The cheapest tier is priced under a shallow machine's haul (`03` §3: 3–6 EC early), so a successful
breach pays for the next virus and then some. **There is no softlock**: self-mining is the income
floor by **I4**, so a player who spent everything is minutes from a tier-1 virus rather than stuck.
⚠ A 0% floor would be a genuine trap, which is why `BREACH_VIRUS_SUCCESS[1]` is 55% and not lower.

### 5.2 ⚠ This bends Invariant I2, and here is the exact argument

**I2 is "ethecoin never buys a ceiling (only breadth: consumables, replacements, horizontal
options)."** A consumable that raises a success rate is money buying **power**, and calling it
breadth merely because it is spent would hollow the invariant out. What is actually load-bearing:

- ⚠ **Consumed every attempt.** It is a running cost, never an accumulating capability. A rich player
  pays again for every breach and is never *permanently* better than a poor one who saved for the
  same tier. **Making a virus permanent breaks I2 outright** — which is why the catalogue entries are
  `Durability.CONSUMABLE` and why that is not a decorative choice.
- ⚠ **It cannot skip the puzzle.** The roll happens only *after* the board is solved. A tier-4 virus
  against an unsolved board is 90% of nothing, so the meta-rule behind I2 and I7 — **"the puzzle is
  the game"** — is untouched. **I7** is untouched more directly still: this gates nothing.
- ⚠ **The ceiling is 90%, not 100%.** Money never buys certainty, and the last 10% is not for sale at
  any price.

**If that argument ever stops holding — a permanent virus, a tier that raises lethality, a roll that
replaces the board — I2 has been broken rather than bent.**

---

## 6. The loops

### 6.1 Solo — sequential

1. The player breaches: the board, as `05`/`16` describe it.
2. **The board is solved** → the virus is uploaded and **spent**.
3. **The roll** decides whether it took hold. Held → loot, foothold, heat as before. Rejected → the
   attempt resolves as FAILED, the virus is gone, and the consequence line says so.

⚠ The virus is spent **on a solved board only** — never at commission. An aborted or failed attempt
costs no virus, the same rule the firmware flash, the download and the archive all follow: an
interrupted act must cost nothing rather than everything.

### 6.2 Online — simultaneous, and adjudicated by REPLAY

The attacker plays the breach board; the defender plays this round. Each side gets a validation screen
confirming what the other established.

⚠ **The outcome is recomputed, never believed** — `DefenseAdjudicator`. A round is a pure fixed-step
function of `(seed, loadout, inputs)`, so the defender sends **what they did** — one byte per tick,
under 2 KB for a whole round — and whoever needs the answer replays it against their own copy of the
rules. A claimed result is never accepted, so there is nothing to forge.

#### 6.2a This dissolves DEF-2 rather than working around it

`§7` recorded the outcome as "the client's", because a real-time round cannot be adjudicated the way a
turn-based board can. That was about adjudicating it **as it happens**. Adjudicating it **afterwards**
costs one replay — the simulation has no toolkit, no clock and no I/O in it.

⚠ **And it satisfies I15 in I15's own terms.** *"No single arbiter decides cross-server adversarial
outcomes; trust comes from quorum and provenance."* The seed is committed by the attacker's server
before the round begins, the trace is signed by the defender, and **any** party holding both
recomputes the same verdict. No server is trusted; every server can check.

#### 6.2b The asymmetry, and why the timing problem was never real

The two halves are not symmetric and were never simultaneous in the sense that mattered:

- The **attacker's** half already runs on the home server as a sequence of intents. Nothing is claimed
  — the server knows whether the board was solved.
- The **defender's** round starts when the attacker **commits the upload**, i.e. when the board is
  solved — not when the breach opens.

⚠ So *"the attacker took four minutes over a board while the defender's thirty seconds ran out three
minutes ago"* cannot happen: the defender's clock has not started. It is the order the solo loop
already runs (§6.1); in PvP the roll is replaced by a person. **A defender who is away** gets the
Auto-Counter Daemon (§3.7) or, with none armed, §5's roll — both already built.

#### 6.2c What replay does not establish

⚠ **That a human played it.** A scripted trace replays perfectly. That is **DEF-1** and it is
unchanged: replay makes an outcome *verifiable*, not *human*.

#### 6.2d Still unbuilt

The **transport**: matchmaking, the live session between two players, and the validation screens. `13`
is still `[PROPOSAL]` and the federation seams are stubbed. What exists is the part the rest was
blocked on — the rule that decides who won, with its tests.

---

## 7. What it must never do

- ⚠ **Generate heat, on any outcome — Invariant I9.** Defending your own rig is not an act against
  anybody. This is also why losing repeatedly is safe to do while learning.
- ⚠ **Cost compute.** Nothing is reserved and nothing is spent. The *firewall* costs standing cycles
  (`09` §3) and that is the price already paid; charging again at the moment it is used would be
  charging twice for one defence.
- ⚠ **Be the way a parasite is removed.** That is the breach board (`04` §5.1), and two minigames for
  "get this thing off my machine" would be two things to learn for one act. This one runs **before**
  the parasite exists — it is the attempt, not the aftermath.
- ⚠ **Be reachable from the rail.** A defence is always *of something*. A window opened from the
  catalogue with no attacker has nothing to be about.

---

## 8. Motion — an amendment to `ui-design-language.md` §5

§5 is *"step and linear timing only"*, and §5.1 draws the real distinction: motion the player is
**working inside** versus motion they are only **watching**. A minigame is neither. **The motion is
the content** — it is not decorating a readout, it is the thing being played.

What this takes, and what it does not:

- ⚠ **No `Interpolator`, no `KeyValue`, no `AnimationTimer`.** All four are rationed by
  `UiContractTest` and none is touched. The loop is an **action-only `Timeline`**, the same mechanism
  `Frost` (24 fps) and `SyncSpin` (30 fps) already use — a `KeyFrame` with an action and no
  `KeyValue` interpolates nothing, so this is a **sampling rate, not a tween**.
- ⚠ **Nothing is eased and nothing is tweened.** Every position is integrated from a velocity at a
  fixed timestep. That is arithmetic, and it is exactly what §5 means by linear.
- ⚠ **Fixed timestep, so the game is deterministic.** A frame that arrives late advances the world by
  the same step as one that arrives on time — a dropped frame slows the round rather than skipping
  through it, which is both fairer and what makes the whole simulation testable headlessly.

### 8.1 Reduced motion

WCAG 2.2.2 governs motion that is **not essential to the activity**. Here it is the activity; there is
no still version of an arcade round, and freezing it would not be an accommodation, it would be a
loss on the clock.

So the round runs under Reduce motion — and the accommodation is elsewhere and explicit: **the player
may concede**, immediately, at any time, with one control that resolves the attempt as BREACHED. A
player who cannot or does not want to play a reflex game is never made to sit through one.

---

## 9. The window, and what the deck does around it

⚠ **The round is a deck LAYER, not a desk window.** No frame, no title bar, no controls, nothing to
drag; it takes most of the deck with a margin left showing, and the field scales to fill it. A round
is not a tool you keep open — it is thirty seconds that owns the screen.

### 9.1 Readouts

- **A timer bar that empties across the top**, not a number. A figure has to be read and converted; a
  shortening bar is understood without looking away from the field, which is the only place a player
  can afford to be looking.
- **Hearts, drawn, one per hit you can take.** ⚠ A spent heart is **dimmed, never removed** — taking
  it away shortens the row and moves the remaining heart at the exact moment it matters most, and two
  dark hearts read as a score of zero rather than as "no lives left".
- **SUCCESS / FAILED across the field**, over the play area rather than under the controls. The
  verdict of a thirty-second round has to land where the eyes already are.
- **A burst when something dies** — the virus on the killing shot, the player when they are hit.
  Drawn shards on a hand-authored table, thrown outward and fading. ⚠ The burst plays **before** the
  outcome is handed on, or the deck tears the round down mid-blast and the player sees it vanish
  rather than see what happened to it.
- **A kill opens a black hole.** The burst first, then a void disc with a bright rim grows where the
  virus was, and the **virus and the whole shield are drawn into it** — shrinking as they travel, the
  nearer squares falling in first. ⚠ Only on a laser kill: a round lost on the clock or to the circle
  has no virus death to show, and playing one would be the game telling the player they won.
  ⚠ A hole is a **shape**, not an effect — §9 makes blur and shadow build-blocking, so what reads as
  an opening is the field's own void plus a rim, and the rim is what stops it reading as missing
  render.

### 9.2 The dread — what the deck does while you are being broken into

The interface **melts**: it shears sideways in bands, drips, splits into colour fringes, and eyes
surface and sink through it. It runs for as long as the round is open and stops when it resolves.

⚠ **It distorts a PICTURE of the deck, never the deck.** A snapshot is captured and the *image* is
torn — `Frost`'s mechanism, for `Frost`'s reasons. Three things fall out of that, and each is a
defect avoided:

1. **The round cannot be affected**, structurally rather than by care: it is a sibling above the
   layer, so it is not in the captured picture and no offset can reach it.
2. **Nothing can be left behind.** A displaced real node has to be put back, and this project has
   shipped that bug twice. An image is thrown away.
3. **It cannot eat a click.** Mouse-transparent, with the real deck live underneath at its true
   coordinates.

⚠ **§9 is not amended.** No blur, no drop shadow, no gradient — the horror is geometry and tinted
copies, the same decision `RingField`'s datamosh records, at deck scale.

⚠ **Reduce motion turns it off entirely** rather than freezing one distorted frame. A still, sheared,
colour-split deck is not a calmer version of this — it is an interface that looks permanently broken
with no motion to explain it. The round itself is what tells the player they are under attack; the
horror is decoration on top and is safe to lose completely.

### 9.2z The way in and the way out

⚠ **The round does not appear in the frame the attack starts, and the deck does not snap back in the
frame it ends.** Reported as the transitions feeling too quick — and the round's own thirty seconds
were never the problem, so that number is unchanged.

**In:** the horror starts alone and builds for about a second and a half; only then does the round
fade in over a third of a second. That order is what makes it read as an attack — something gets in,
the interface starts coming apart, and *then* the player is handed the thing they can do about it.

**Out:** the reverse, and strictly in sequence. The round fades, then the horror **drains** rather
than being switched off, and only when the deck is its own again does the aftermath pulse begin.
⚠ Two effects fading through each other on one layer read as a rendering fault rather than as one
thing ending and another beginning.

⚠ **The consequence does not wait for any of it.** Whether the intrusion landed is game state and is
applied the moment the round resolves; the exit is an animation. Holding a rules outcome behind one
would make what the save contains depend on how long a fade takes.

⚠ **Reduce motion skips both.** There is no horror to build, so a delay in front of a game with a
clock running would be an empty pause.

### 9.2a It arrives rather than appearing

⚠ **The distortion ramps up to full strength over about two seconds** rather than snapping on.
Snapping reads as the client **breaking** — one frame the deck is fine, the next it is in pieces,
which is what a crash looks like. Coming on gives the player the moment they need to understand that
the window which just appeared is a game they are meant to play.

The deck is also **dimmed** under the distortion, and **blood runs down from the top edge** — of the
deck, and of the round itself. ⚠ The round is not a safe place inside the horror; it is where it is
happening.

### 9.2b The round's own edge

⚠ **A red border round the round, pulsing on the heartbeat.** It is the one border in this client
allowed to move, and §2.1's ration is satisfied twice over: it is the frame around the thing
currently attacking the player, which is both loss and hostile state. ⚠ It never reaches zero while
the round is open — an edge that went fully dark between beats reads as a border flickering off
rather than as a pulse.

### 9.2c The colour drains out as the clock does

⚠ **Everything posterizes as the round runs down** — the deck and the round both, from twelve colour
levels per channel to two. It is the clock made visible without a number: the steps are whole levels,
so each one lasts several seconds and lands as a jolt.

⚠ **JavaFX has no posterize effect and no shader API**, so it is arithmetic, in the two places where
it is affordable — and they are different places, because the deck and the round have opposite
constraints:

- **The deck** is already being captured as an image nine times a second. Quantising that image is one
  bulk pass with a 256-entry lookup table: **measured at 0.96 ms** for a half-scale capture of a
  1600 × 1000 deck, under 1% of a core at that rate. ⚠ Per-pixel `getArgb`/`setArgb` would be 400,000
  method calls each way per frame; this is one bulk read, a tight `int[]` loop, one bulk write.
- **The round** must stay live — 60 fps, taking key events — so it cannot be replaced by a picture. It
  is a few dozen shapes drawn from a handful of palette tokens, so it is quantised at the **colour**
  rather than at the pixel, and only when the level actually changes: about ten times a round rather
  than eighteen hundred.

⚠ **Base colours are read once and re-quantised from the base, never from the current fill** — each
step quantising an already-quantised colour would march the round to black in seconds. ⚠ And they are
read **after CSS has been applied**, or the base is Modena's default and the round is locked to the
wrong palette for the rest of the attempt.

⚠ **Alpha is never quantised.** Every glass surface in this client is built out of translucency, and
stepping alpha makes an overlay jump between opaque and invisible.

### 9.3 The aftermath

⚠ **A failed defence leaves the deck pulsing for fifteen seconds** — a heartbeat, `lub-dub … rest`,
from a table rather than a sine, because an even pulse reads as a warning lamp and this has to read
as a pulse. It runs *after* the horror has stopped: nothing is distorted, the deck is back, and it
simply will not settle. **Only on a loss.**

⚠ **It outlines the EDGES — of the deck and of every open window — and never washes the screen.** A
full-surface flash blows out every readout at the moment the player is working out what the loss cost
them, and over fifteen seconds it reads as a fault rather than as a pulse. The outlines say the same
thing about the same subject and leave the contents legible.

⚠ **And the verdict is held before the round is taken away.** SUCCESS or FAILED across the field is
the answer to the only question the player has; handing the outcome straight on closes the layer
within a few frames of it appearing — long enough to see that something happened and not long enough
to read what.

---

## 10. Unprovoked intrusions — somebody comes for you

⚠ **Every other intrusion in this game is a reprisal.** A sweep that was noticed, a breach that was
answered, a port scan that was detected — so the defence round could only happen to a player who had
just been offensive, and a cautious one never saw it at all. The whole defensive half of the game, and
the standing compute it costs, was something that happened to other people.

**The Eye comes to you.** On the tick, keyed on **personal heat** — which is precisely what heat
measures — from about one attempt per five hours on a clean rig to a little over one an hour at 100.

- ⚠ **A per-HOUR rate, never per tick.** `1 - e^(-rate × hours)` against the tick's own elapsed time,
  so a faster-ticking client does not attack more often and a long absence does not collapse to one
  roll. The shape `ShadowTrading` already uses.
- ⚠ **The floor is not zero**, and that is the decision: being quiet is not the same as being
  invisible. **The ceiling is not high**, for the mirror reason — heat already punishes in four other
  ways, and thirty seconds of arcade every ninety seconds is a client nobody can put down.
- ⚠ **A cooldown**, which is what makes the rate safe to tune: without it an unlucky run stacks two
  rounds back to back.
- ⚠ **It does not fire while the player is away.** A long absence would otherwise arrive as a
  near-certain attack the instant the client opens — a round the player is thrown into before they
  have looked at their own rig. What happens while you are gone is the Auto-Counter Daemon, which is
  the tool that exists for exactly that.
- ⚠ **The attacker is a real machine**, preferring a **defended** one the player has met: this is the
  estate you have been poking at coming back, not a stranger. Its tier sets the Breach Virus it brings
  — which is also the answer to **DEF-3**, "every round is the same round".
- ⚠ **Holding is not a reward.** No loot, no standing, no heat relief; nothing was taken and nothing
  was done to anybody. Paying for a successful defence would make being attacked farmable, and the
  ambient roll an income stream keyed on heat.

---

---

## 11. Open — and the one that matters

- **DEF-1 — I10 does not survive here, and that is known.** The breach puzzle's anti-bot property is
  that cross-referencing two documents is a simultaneity problem. A reflex game is the opposite: it
  is the shape a script is *best* at. What limits the damage today is that the round is worth nothing
  — it denies an intrusion, it does not pay — so there is nothing to farm. **The day this gates
  anything of value, that stops being true.** `17` §7.2's two ways out apply here as well.
- **DEF-2 — ANSWERED 2026-08-10 by replay adjudication (§6.2).** The outcome is recomputed from a
  committed seed and a signed input trace, so it is nobody's claim and every party can check it. What
  remains is the **transport**, not the trust model.
- **DEF-3 — PARTLY ANSWERED.** An unprovoked attacker's Breach Virus tier now scales with the host's
  tier (§10), so a tier-5 estate is a longer round than a desktop. What still does not scale is a
  *reprisal's* difficulty, and nothing scales shot rate, virus speed or shield density.
- **DEF-4 — RESOLVED 2026-08-10.** The Tarpit (§3.6) and the Auto-Counter Daemon (§3.7) are in. What
  is still unconsulted: the Canary Token, the Honeypot Stash and the Detection Array, none of which
  has an obvious reading in a round with one attacker and no files to touch.
- **DEF-5 — the thirty-second clock is only reachable at the top of the firewall ladder.** Measured:
  a scripted player lasts 8.5s with nothing armed and 28.9s at T3. That gradient is intended; a
  timeout being effectively unreachable at tier 0 is not obviously wrong, but it means §4's headline
  rule rarely fires for a player with no firewall. Watch it before tuning anything else.
- **DEF-6 — the virus a breach spends is the best one held, and the player is not asked.** A chooser
  belongs on the launch panel. Until it exists, somebody who bought one tier-4 virus for a deep target
  and then breached a desktop has been robbed by an implementation detail. `BreachVirus.bestHeld` is
  where it slots in.
