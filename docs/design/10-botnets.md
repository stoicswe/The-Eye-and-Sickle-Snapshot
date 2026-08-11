# 10 — Botnets

**Status:** Established (design sessions 1–5, including session-3 resolutions on loss/counter-attack)
**⚠ §2 AMENDED 2026-08-11** — the six role frames are replaced by a **chassis-and-function** model on
explicit direction. §1, §1a, §1b, §3 and §4 are untouched and still govern. See §2.0 for what changed
and what the change cost; logged in `15-open-questions.md` §3.
**Depends on:** `01-core-resources.md`, `02-unlock-gates.md`, `05-hacking-minigame.md`
**Depended on by:** `03-economy.md` (bot income variance), `11-rig-infrastructure.md` (Isolated Partition)

Bots are the player's force-multiplier and the game's biggest self-inflicted-risk surface. The governing principle overrides every convenience argument:

> **Bots assist, they never substitute** (Invariant I10). They speed things up, reduce risk, and open options. They do **not** solve the puzzle for the player — the puzzle is the game (Pillar 1).

---

## 1. Rules (established)

- Bots are equipped with tools **from the player's own stash**. Assigning a tool pulls it out of the vault and makes it **mid-risk** (`01` §6) — safety and productivity are mutually exclusive.
- Bots are **slower** than manual play.
- Bots are live **only while the player is online** (Invariant I5) — a botnet is not an idle-game overnight farm.
- All bot noise **pools back into the player's aggregate noise** (`01` §3.1). More bots, louder you.
- If a bot is targeted, the player is **notified and may defend or counter-attack** on the bot's behalf.
- **More active bots shortens the timer for responding to each item in the defense backlog.** The penalty scales with bot count directly, so skilled multitaskers can't fully escape it by getting faster.

### 1a. Failed defense — resolution (session-3 decision)

- **Failure means total loss.** An undefended bot is destroyed outright along with **every tool assigned to it**. No degraded-but-surviving state. Overextending on bot count is a hard risk — networks can be wiped in a bad session.
- **Low chance of salvage:** a destroyed bot has a small probability of yielding **generic schematic contribution material** (partial-progress toward schematic unlocks, `02` §2.2).
- **Exploit guard (Invariant I13):** the material drop is gated on **engagement tier** — the bot must have been lost against a defended target above a difficulty threshold. Without this, the optimal play is to build the cheapest junk bot and feed it to a loss, turning bot sacrifice into a grind path toward ceiling raises — the exact failure the gate rule (`02`) exists to prevent. Tier-gating the drop is consistent with proof-of-skill handling; it reuses the same `resolutionRecord.difficultyTier` from `05` §2.

### 1b. Split attention (session-3 decision)

- A bot alert **does not interrupt or queue.** It runs **in parallel** with whatever the player is currently doing, under a **split-attention penalty applied to both engagements**.
- This **stacks intentionally** with the shrinking backlog timer: more bots → less time per item **and** degraded performance on every simultaneous action. Skilled players are still rewarded for multitasking, but the system pushes back as bot count grows rather than capping out at player skill.
- **Playtest watch:** two penalties on the same axis (bot count) could tip bots from "tense" into "not worth running." If that happens, **soften split attention first** — the timer is doing the load-bearing work. (Tracked as a tuning note, not an open question, because the resolution order is already decided.)

---

## 2. Frames and functions

> **A frame is a blueprint.** The schematic/reputation gate unlocks the *ability to build* that frame type permanently. Each running instance is assembled at an EC cost.

This matters because of §1a: total loss destroys the **instance and its socketed functions**, never the blueprint (Invariant I11). Bot loss is therefore an **EC-and-gear** cost — correctly EC-gated since EC covers replaceables — rather than permanent forfeiture of a ceiling-tier asset.

### 2.0 What the 2026-08-11 amendment changed, and what it cost

The old §2 listed six **role** frames — Recon, Miner, Sentinel, Breacher, Mimic, Scavenger — each a
separate blueprint whose role was fixed by which blueprint you held. A frame is now a **chassis**: it
supplies slots and nothing else, and what a bot *does* is decided by the **functions** socketed into
it. The roles did not disappear; they became loadouts.

Three consequences worth stating out loud, because each one is load-bearing:

1. **The bottom rung is now purchasable.** `BotFrame_v1` sits on the ethecoin gate. Every rung above
   it is compiled (`ASSEMBL`, `11`) and **is not for sale at any price**. This is the shape the
   compute ladder's amended I1 and the firewall's *top purchasable* already use: money reaches the
   first rung of a ladder, never a rung above the ladder. ⚠ **A second priced frame rung breaks the
   argument** and must fail the build rather than be a conversation nobody had.
2. **The Breacher is deleted, and that is an improvement.** The old §2 had a frame that "plays the
   `05` minigame on a fixed heuristic with a time penalty". That made **I10** a tuning problem — the
   heuristic had to be kept reliably worse than a human forever, and `15` §2 **P-3** records that the
   margin is unmeasurable until the puzzle is played at scale. **No function in the new model touches
   a breach board.** I10 is now structural: there is no code path from a bot to a puzzle, so there is
   nothing to keep badly tuned.
3. **§1's "tools from the player's own stash" now reads as functions.** A function is an owned item
   in the vault; socketing it pulls it out and makes it mid-risk (`01` §6) exactly as §1 says of
   tools. Nothing about §1a changes: losing the bot destroys the socketed function *instances*.

### 2.1 The chassis ladder

| Frame | Functions | Modifiers | Survives removal | Gate | Control channel |
|---|---|---|---|---|---|
| **BotFrame_v1** | 1 | — | no | Ethecoin, 55 EC | 6 |
| **BotFrame_v2** | 1 | 1 | no | Schematic | 8 |
| **BotFrame_v3** | 1 | 2 | no | Schematic | 10 |
| **BotFrame_v4** | 2 | 2 | no | Schematic | 14 |
| **BotFrame_v5** | 2 | 3 | no | Schematic | 16 |
| **BotFrame_v6** | 2 | 3 | **yes** | Schematic | 18 |
| **BotFrame_v7** | 3 | 3 | no | Schematic | 20 |
| **BotFrame_v8** | 3 | 3 | **yes** | Schematic | 22 |
| **BotFrame_v9** | 3 | 4 | no | Schematic | 24 |
| **BotFrame_v10** | 4 | 4 | **yes** | Schematic | 30 |

A frame with no function socketed **does nothing and cannot be uploaded.** That refusal is the
model's opening statement: the chassis is not the capability.

⚠ **Most rungs buy exactly one thing.** Functions go 1,1,1,2,2,2,3,3,3,4 and modifiers
0,1,2,2,3,3,3,3,4,4 — so a rung is a decision about *which* kind of room you want next, not a number
going up. The three resilient rungs (v6, v8, v10) buy no sockets at all over the rung below; they buy
surviving being caught.

⚠ **Only v1 has a price, at any tier, ever.** That is the whole safety argument for putting the
botnet on the money gate at all, and it is the same shape as the compute ladder's amended I1 and the
firewall's *top purchasable*. A second priced rung is a build failure, not a conversation.

### 2.3 Being removed, and being destroyed

Three outcomes, and collapsing any two of them deletes a tier's reason to exist.

1. **Recalled** — the player takes it back before anyone notices. Chassis and loadout both survive.
   This is the reward for reading the Watcher reports and the discovery warning.
2. **Removed** — the host's operator finds it and throws it out. The **loadout is always lost**, at
   every tier. The **chassis** comes back *damaged* — or, on v6/v8/v10, intact.
3. **Destroyed** — §1a's total loss. The object is gone. ⚠ Still unreachable in play; see §6 BN-4.

A **damaged** chassis holds nothing and cannot be uploaded. It can be **repaired** with ethecoin (or
with parts), or **recycled** into **BotFrame Parts**.

⚠ **A repair is ethecoin-gated and that is not a hole in I2.** It buys back a *replaceable* — the
chassis is exactly what it already was, and the functions and modifiers, which are the ladder, are
gone either way. `docs/design/02` §2.1 puts replaceables on the money gate.
⚠ **A repair must cost more than a recycle returns**, or scrapping and rebuilding dominates repairing
at every tier and "damaged" means "destroyed" with extra steps. Parts-for-repair is likewise dearer
than parts-from-recycling, or the two are a perpetual motion machine.

---

## 5a. Modifiers

Sub-functions fitted beside a bot's functions, from `v2` up. Five levels each, except the exe-name
scrambler, which is a binary.

> ⚠ **A modifier changes how a bot SURVIVES, never what it achieves.** That is the charter, and it is
> what makes modifiers safe on the **ethecoin** gate while function levels are not. A function's
> ten-level ladder is a **ceiling** (I2 — hence schematic material); a modifier is **horizontal**,
> which `docs/design/02` §1.1 puts on the money gate outright. The moment a modifier makes a function
> *do more* — a Keylogger that learns two rungs, a Sipper past its hourly ceiling — the function
> ladder has a second and much cheaper entrance and the whole gate assignment is void.

| Modifier | Levels | What it does |
|---|---|---|
| **Exe Name Scrambler** | 1 | Wears a real system process's name instead of reading as an unregistered process. |
| **Sleepy** | 5 | Much harder to find, and absent from the process table while asleep. Costs real speed. |
| **Dampener** | 5 | Cuts what the bot adds to your noise pool. **Never below 5%.** |
| **EfficientMultiThreading** | 5 | +25% speed a level, and a good deal louder. |
| **BedazzlePro** | 5 | Nothing for the bot. Confetti, a cake, a unicorn, a gold star, emoji — and a little heat, hidden. |
| **Protector** | 5 | Blocks removal attempts, on charges, and covers the bot's tracks. |

- **Sleepy's speed penalty is the price and must stay real.** A stealth modifier with no cost is the
  correct fit on every bot, which makes the slot a formality.
- **The Dampener's floor is 5% and is load-bearing.** §1 pools all bot noise into the player's
  aggregate — *more bots, louder you*. A modifier that reached zero would make a fully dampened
  network free reach, and the noise model's answer to "why not run fifty" would collapse to compute
  alone.
- **EfficientMultiThreading buys rate, not capability.** It scales a *cadence*. A Sipper with it hits
  its hourly ceiling sooner and never exceeds it.
- ⚠ **"Each Protector level increases the protection by 30%" is read as a further 30% ROLL**, not as
  +30 percentage points. Read literally, L4 would be 120% — certain — and L4, L5 and immortality
  would be indistinguishable. Compounding gives 30/51/66/76/83%: every rung buys something, none of
  them buys certainty.
- **A Protector is charges, one per level.** A blocked removal spends one; at zero the next attempt
  takes the bot. Permanent protection would be a bot that cannot be lost, and §1a's total loss is
  what §4's whole argument rests on. A block **resets the discovery** (they think it worked),
  **re-rolls the scrambled name**, and — *only if a Sleepy is also fitted* — hides the bot for 1–5
  minutes by level. The Protector buys the time; Sleepy is what the bot uses it for.
- **BedazzlePro costs the owner a little personal heat every time it fires, and the player is never
  told.** ⚠ **This reverses the rule stated here until 2026-08-11**, which was that the modifier has
  no mechanical effect whatsoever, on the grounds that anything touching detection, noise or speed
  would be a real modifier wearing a joke's clothes. That was wrong on its own terms. Heat is
  **long-horizon Eye attention**, and this module's whole function is to announce your bot's presence
  with a unicorn — in a game about a surveillance state, attention is not a stat bolted onto the joke,
  it is *what the joke is*. What the old sentence was protecting survives and still binds: **a
  modifier may impose a cost that follows from its own fiction, and may never grant a benefit.**
  - ⚠ **Hidden, by decision.** Nothing names it: not the effect line, not the market copy, not the rig
    log. This is `OFFLINE_MINING_WIN_WEIGHT`'s precedent and `docs/design/04` §3.1's teaching — the
    numbers not adding up is something the player *discovers*, not something they are told. The panel
    still says "does nothing useful", which stays literally true: it does nothing useful **for the
    bot**. It is an incomplete truth the player is meant to complete.
  - ⚠ **Level buys the chance, not the price.** The per-trigger heat is one constant; a level-5 module
    fires six times as often and therefore costs six times as much. A second per-level table would
    compound the scaling quadratically.
  - **Measured** (`BedazzleCensus`, 2026-08-11): **0.08 heat/hour at L1 to 0.58 at L5**, one bot with
    one function. Per bot, and once per fitted function — a `v10` with four is sixteen times a `v1`.
  - ⚠ **An absence costs almost nothing** — the roll is capped at four cadences per settle. That is
    I5's shape applied to a *cost*: a fortnight away must not come back as a maxed heat bar.
  - ⚠ **Raising it much turns a joke's price into a trap.** The player cannot attribute the heat, so a
    fast-climbing hidden source reads as a bug in the heat system rather than as their own choice.
  - It is priced at the bottom of the consumable band: priced like a real modifier it would read as a
    stat nobody could find, and a reasonable player would conclude it was broken.
  ⚠ **Its visible half needs a target who is a person.** It draws on somebody else's deck. In solo
  there is nobody there, so it lands nowhere — but the **heat is still charged**, because the bot ran
  the routine and whether a human was watching is not what makes it conspicuous.

### 5a.1 Being found

A bot on a machine is discovered on a **per-hour rate**, reduced multiplicatively by the scrambler
and by Sleepy, and **never to zero**. Discovery and removal are **two steps**: the operator has to
notice, and then has to act. That gap is what a Protector blocks in, and what §1b's alert would be
racing if it were built.

⚠ **This is the loss trigger.** Until it existed, §4's third cost (the socketed modules being at
risk) was purely notional and a botnet was upside with no downside. **Every modifier above is priced
against it.**

### 2.2 Whose cycles a bot holds

A bot runs **on the machine it was uploaded to**. Its work is charged to that **host** — Invariant
**I6**, the same rule that governs a deployed miner — and the player pays a permanent
**control channel** reservation on their own rig for as long as the bot is live.

⚠ **This is what preserves §3.** The self-correcting cap on botnet size is that every live bot is
standing compute the player does not have for anything else. Charging the bot *entirely* to the host
would delete that cap and force a hard bot limit, which §3 says explicitly should never be added.
Charging it *entirely* to the player would make "uploaded to a target" decoration.

⚠ **It is its own consumer** (`BOT_FRAME`), not folded into `CONTROL_CHANNEL`. That one's size is
the self-correcting cap on **deployed miners** (`04` §2.2), and a cap only works while the number
means exactly one thing.

⚠ **`BOT_FRAME` already counts as outward noise** and always has. §1's noise pooling needs no new
mechanism — a live bot is a live connection to somebody else's machine, and the meter says so.

---

## 5. Functions

Five. Each is an owned item, socketed into a frame, carrying a **level 1–10**.

> **A level is a ceiling, so money never buys one.** Level 1 is what you purchase or compile.
> Levels 2–10 cost ethecoin **and schematic material** (`02` §2.2) — and material is not for sale,
> so no amount of ethecoin advances a function by itself. This is **I2** held with the mechanism
> `02` already has, rather than a new one.

⚠ **The level belongs to the function instance, not to the player.** A level-7 Keylogger is a thing
you built and a thing you can lose (§1a). If levelling were player-wide knowledge, a destroyed bot
would cost nothing durable and the loss loop — the whole reason §4 calls a botnet *risk* — would stop
existing.

### 5.1 Keylogger — gate: ethecoin

Fills in the player's recon file on the host it sits on, one finding at a time, for free.

Every cadence it rolls once against an unlearned rung of the port-scan ladder (`17` §8). Level sets
the chance: **10% at L1 rising to 90% at L10.**

- ⚠ **It buys probability, never reach** — the same classification the sweep ladder gets in `02`
  §1.1 step 4, which is why it is on ethecoin. It cannot learn a rung a port scan could not.
- ⚠ **It does not count as a scan.** A finding that arrived without a scan must not increment the
  scan counter, or the detection ratio beside it becomes a fraction of a number that never happened
  — the rule `Cheats`' reveal already had to learn.
- ⚠ **Identity stays write-once.** A keylogged name is pinned like a scanned one.

### 5.2 Injector — gate: **schematic**

Drops a malicious package into the host's `Downloads`. If the operator installs it, the player may
**offload cycle requirements onto that machine** — for tools, and **never for mining**.

Level sets how much: **4 cycles at L1 rising to 40 at L10.**

- ⚠ **This is the one function that hands the player compute, so it is the one function money may
  not reach.** Compute is the master scarcity; an ethecoin-gated Injector is ethecoin buying
  capacity, which is **I1** with extra steps.
- ⚠ **Mining is excluded and the exclusion is the whole safety argument.** Offloaded cycles that
  could mine would close the loop — mine, buy bots, offload, mine faster — which is exactly the
  flywheel I1 exists to prevent. It is enforced at the reservation, by consumer, not by convention.
- ⚠ **The operator's install is a roll the player does not control**, and the package can be found
  and deleted. An offload is a thing you keep only while nobody notices.
- ⚠ **Offloaded capacity is derived from live bots on every tick, never stored as a total.** A stored
  copy of a derived ceiling is `ChainState.networkHashrate`'s bug waiting to happen, and it is also
  what would make a hand-edited save grant the whole ladder.

### 5.3 Miner — gate: ethecoin

Mines on the host, exactly as a deployed miner does (`04` §2), and buffers the yield on the bot.

Level sets the host cycles it draws: **8 at L1 rising to 40 at L10.** **From L5** it also
**auto-deposits** to the owner's ledger — a share rising to a maximum of **45% of the bot's buffer**,
at a frequency that rises with level.

- ⚠ **Auto-deposit is a convenience, never a bypass.** It caps at 45% precisely so that collecting by
  hand is still how most of the money moves, which keeps the buffer seizable (`04` §2) and keeps the
  player looking at the bot.
- ⚠ **I5 binds here in full.** A bot miner keeps hashing for `OFFLINE_MINING_HOURS` after the client
  closes and then stops dead, at `OFFLINE_MINING_WIN_WEIGHT`. Offline income is capped and never
  proportional to absence.
- ⚠ **I4 does not extend to it.** Self-mining is the immune income floor because it is *local*. A bot
  miner is on somebody else's machine, it is loud, and its buffer can be taken.

### 5.4 Sipper — gate: ethecoin

Adds a tax to ledger transactions the host makes. Level sets the rate: **3% at L1 rising to a
maximum of 30% at L10.**

> ⚠ **THE RATE IS NOT THE BOUND. THE RATE IS NEVER THE BOUND.**
>
> NPC transactions in this game are **derived, not stored** — a pure function of height and index.
> A percentage of an invented, unbounded stream is an ethecoin printer, and every screen renders
> correctly while it prints. This is the same failure `MarketDeals` needed an arithmetic ceiling for
> and `ShadowMarket` needed a bounded-noise price function for.
>
> **What actually bounds it is a per-hour ceiling on total sipped value**, derived so that a maxed
> Sipper earns *less per held cycle* than self-mining does — because self-mining is the income floor
> (**I4**) and nothing that costs a control channel, pools noise and can be destroyed may pay better
> than the thing that is free and safe. A test sweeps it and fails the build if it crosses.

### 5.5 Watcher — gate: ethecoin

Reports what the host's operator is doing: work they queue, transactions they make, and — when the
story layer lands — **INTEL** they come into, which the player may spend cycles to copy.

- ⚠ **How many subjects it can watch at once is the FRAME's tier, not the function's level.** One at
  a time on a `v1`. This is the one place the two ladders do different work, and it is why the
  Watcher needs no maximum level in the way the other four do: the level buys fidelity and the INTEL
  copy chance, the chassis buys parallelism.
- ⚠ **The INTEL arm is a documented seam and is not built.** `14` has not defined INTEL. Shipping a
  control that claims to copy something the game has no concept of would be the "stale not-built
  premise" failure `CLAUDE.md` warns about, from the other direction.

### 5.6 One derived activity stream, two consumers

The Sipper taxes what the host does and the Watcher reports it. **Both read the same derived
stream.** Two derivations would be two answers to "what did this machine do", and the player can see
both at once — a tax on a transaction the Watcher never mentioned is the kind of contradiction that
makes an entire panel untrustworthy.

---

## 3. The compute reality of running a botnet

Frames reserve compute permanently while running (like defenses, `09` §3). A three-bot loadout on
`v1` chassis is 18 cycles of control channel before a single function has done anything — plus the
Miner's own control-channel reservations (`04` §2, 3/miner) — and that can consume most of a starting
rig (24 cycles, `01` §1.1) before a defensive tool is armed. This is the self-correcting cap on
botnet size, mirroring the deployed-miner cap (`04` §2.2): **no hard bot limit is needed, and none
should be added.** The rig ceiling is the limit.

The **Isolated Partition** rig upgrade (`11`) lets *one* bot run without contributing to the noise pool — extremely expensive, hard cap of one or two — the single exception that proves the pooling rule.

## 4. Design summary: why botnets are risk, not just power

Every bot simultaneously: (a) reserves scarce compute, (b) pools its noise into your heat exposure, (c) mid-risks the functions socketed into it, (d) shortens your defense-response timer, (e) applies a split-attention penalty to everything you do. Five distinct costs against one benefit (throughput). That five-to-one pressure is why a botnet reads as *overextension you chose*, and why the design never needs a hard cap to stop players from running fifty of them.

---

## 6. Open

- **BN-1 — the backlog timer and split attention are not built.** §1's last bullet and §1b are
  Established and nothing implements them. The defence round (`19`) is the surface they would land
  on; until they exist, running many bots costs compute and noise but not attention.
- **BN-2 — INTEL.** §5.5's third subject waits on `14`.
- **BN-3 — frames v2/v3 are catalogued and unreachable.** Both are schematic-gated with no compiler
  to compile them, exactly as `compute-48`/`compute-64` are. That is the honest state, not a gap.
- **BN-4 — half answered.** §5a.1's discovery-and-removal loop is built, so a bot **can** now be
  found and thrown off, losing its whole loadout and usually damaging the chassis. What is still
  unbuilt is §1a's **total loss** — the outcome that deletes the object — which needs a targeted
  attack rather than an operator tidying up. The path exists in the rules and is reachable only from
  the developer page.
- **BN-5 — BedazzlePro lands nowhere.** It needs a target who is a person; solo has none. The roll is
  made and recorded. Wiring it needs `docs/design/13`.
- **BN-6 — nothing here is measured.** Every number in `Balance`'s botnet block is a first pass
  argued from the invariants, not from play. The discovery rate against the modifier ladder is the
  one most likely to be wrong, and it is the one that decides whether bots read as tense or as
  disposable.
