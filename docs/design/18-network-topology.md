# 18 — Network Topology: shape, depth, and how a map grows

**Status:** **[PROPOSAL]** — the solo half is being built against this document; the multiplayer half
is design only and has no server to hang it on.
**Depends on:** `07-recon-tools.md` §5 (the sweep ladder), `17-bridges-and-surveillance.md` (bridges),
`02-unlock-gates.md` §1.1 (what each gate may sell), `00-vision-and-pillars.md` §4 (**I2**, **I3**)
**Depended on by:** `TopologyGenerator`, `NetRules`, `docs/client/09-network-map-graph.md`

This document answers one question the code has been answering by accident: **what shape is a
network, and how does it grow as the player works through it?** Before it, a server's internal shape
was whatever a random recursive tree produced — depth roughly the logarithm of the machine count, and
a branch factor nobody chose. The map in `docs/client/09` §8 records the measurement: *"layers are
1–5 machines wide, maps are 4–10 columns deep… fan-out does not occur at reachable depth"*. That is
the accident, described.

---

## 1. Vocabulary, because two different things are called depth

| Term | Means | Where it lives |
|---|---|---|
| **Server depth** | How many bridges from home a *server* is | `ServerState.depthFromHome`, 0–4+ |
| **Node depth** | How many hops from the server's gateway a *machine* is | emergent today; **chosen** under this proposal |

The sketch this document is written from labels the second one `Depth_0 … Depth_N`, running left to
right across one server, with `LOCALHOST` at `Depth_0`. Both matter and they do different jobs:
**node depth is how long a server takes to cross; server depth is how dangerous it is.** §4 is the
whole of that distinction.

---

## 2. Solo: a server is a tree of chosen depth

### 2.1 The three rules

1. **Each server picks a node depth of 4–13**, drawn at world generation and never re-drawn.
2. **A machine branches into 1–7 children**, drawn per machine.
3. **At least two machines on a server branch into more than one child.** A server that was one long
   chain would have no choice in it, which is the failure `TopologyGenerator`'s own class note gives
   for rejecting a chain at the *server* level — restated one level down.

### 2.2 ⚠ Depth is the target and the machine budget is the constraint

Depth and branching cannot both be free: a depth-13 tree branching 1–7 the whole way is thousands of
machines, and the brief's hard cap is fifty per server. So the construction is **spine first, then
branches**:

- the machine count comes from `Balance.netMachinesMin/Max(serverDepth)` as it already does (12–20 at
  home, up to 34–50 deep);
- a **spine** of `D` machines is laid from the gateway, which is what makes the depth exact rather
  than emergent;
- the remaining budget is spent attaching branches of 1–7 to machines already placed.

⚠ **The spine may take at most `NET_SPINE_BUDGET_SHARE` (0.6) of the non-gateway machines**, and that
number is a measurement rather than a taste. The first version of the rule was "leave two machines
over", on the reasoning that two is what rule 3 needs. It is arithmetically true and it makes a bad
server: a 13-machine home rolled depth 11, the spine took eleven of the twelve non-gateway machines,
and the whole thing rendered as **an eleven-hop chain with a single fork at the far end**. Dumped and
read, it was a corridor — precisely the shape this section exists to prevent, arrived at from the
other side.

At 0.6 there is a real budget to fan with at every size: a 12-machine home spends at most 7 on depth
and has 4 spare; a 50-machine deep server hits the depth ceiling long before the share binds and has
36 spare to spread over 13 layers.

⚠ **The share is of `count − 1`, not of `count` — the gateway is the root and is not on the spine.**
The off-by-one in the other direction is what produced the corridor above.

⚠ **The clamp is named and tested rather than silent.** A rolled 13 on a small server is not the depth
that server gets, and "the number in the save is not the number in the world" is how a tuning question
becomes a bug hunt.

### 2.4 ⚠ Chords must be same-layer, or the shape is undone after it is built

The intra-server chord pass adds ~22% more links *after* the tree is built, and it runs on every
server. Unconstrained, a single chord from the gateway to a deep machine collapses the spine the
server was built around — and **nothing in the save would show it**, because depth is not stored.

This is not a new argument. `TopologyGenerator`'s own class note has forbidden the same thing between
**servers** since it was written: *"a depth-skipping chord would shorten a BFS path and silently
re-depth a server after its machines had already been generated against the old depth"*. The rule
simply had nothing to apply to at the machine level until there was a spine to shorten.

⚠ **Same layer exactly, not "within one".** Depth preservation only needs `|Δ| ≤ 1` — but a chord to
the layer *below* is indistinguishable from a branch in the finished graph, so allowing one makes the
1–7 rule unobservable in the thing that ships. Measured: a machine with a 7-wide fan and one such
chord reads as fanning 8. **A rule nobody can check on the real object is a rule that will drift.**

### 2.3 What this changes on screen

`docs/client/09` §8 **NM-5** is the open complaint that the map's real unreadability is *horizontal* —
maps grow deep, not wide, and nothing addressed it. This proposal makes both dimensions deliberate:
depth is chosen in a published band, and fan-out is a rule rather than an accident. **The stack fold
(`NET_STACK_THRESHOLD`, built and dormant) stops being dormant** — a 1–7 branch factor produces layers
wide enough to fold, which is what it was built for.

---

## 2.5 A server has a name, and it is what a bridge advertises — **implemented**

`NpcNames.server` — **`adjective-character`**, the same scheme and the same adjective pool as a
machine's `adjective-pioneer`, hashed from the server id and de-collided by walking. It costs no RNG
draws, for the reason machine names cost none.

The characters come from Final Fantasy, Zelda, Cyberpunk 2077/Edgerunners, Cronos: The New Dawn,
Marathon, Portal, Half-Life, Death Stranding, Tomb Raider, Resident Evil, Watch Dogs, Wolfenstein,
Doom, Warhammer 40,000, Warhammer Fantasy/Age of Sigmar and Dune — **878 names** after filtering.

⚠ **Fictional where the machines' pool is real, and both halves of that matter.** `PIONEERS` is
scientists, so it carries a hard "no demeaning adjective" rule — pairing a real name with an insult
is a claim about a person. This pool is characters, so the binding rule is different:

- **No real person.** Three were dropped on review: `blavatsky` (a historical occultist), `zidane`
  (Final Fantasy IX's protagonist, and a famous living footballer — and it is the footballer a
  hostname reads as), `bohemond` (a real crusader).
- **No species.** `necron`, `pfhor` and `jjaro` were harvested and removed: `wicked-necron` names a
  race, not a person.
- **No ordinary given name and no common word.** `wicked-sam` and `wicked-paul` are not references to
  Death Stranding or Dune; they are an adjective and a name. Paul Atreides appears as `muaddib` and
  `atreides`, which are.
- **No collision with the other three pools.** ⚠ Resident Evil Village's Karl **Heisenberg** was
  dropped by that check rather than by review, and it is the sharpest case: `PIONEERS` has Werner
  Heisenberg, and a name in both pools reads as the physicist wherever it appears. Seven more went
  for colliding with the operator pool — a player who has just met an operator called `magnus` and
  then finds a server called `roguish-magnus` will reasonably think the two are connected.

⚠ **What this replaced was seven names shared by every world in existence.** `home-relay`,
`south-exchange`, `north-yard` … the same list, in the same order, on every seed — its own note
conceded it, on the grounds that "nobody replays a world for its place names". That trade stopped
being available the moment servers became **tabs** (§2.6) and a bridge advertised its far side by
name: seven shared names read as furniture. Hashing the id was free the whole time.

## 2.6 The map is one tab per server — **implemented**

`ServerTabs`. One tab per server in `NetMap.knownServers()`, **home first and the rest alphabetical**,
each laying out its own server from its own shallowest known machine.

⚠ **The tab list is what the player has heard of, never the world.** A server reaches `knownServers`
by being swept or by an identified bridge advertising it. Anything else would publish the shape of
the world for free, which is the rule `NetRules` states as *"undiscovered hosts do not exist in
`knownNodes`, and the map draws nothing where they are"*.

⚠ **A tab may legitimately be empty, and it is dimmed rather than hidden or disabled.** An identified
bridge names the server on its far side and that is all the player has until they cross it. "There is
a door there and you have not been through it" is real information — it is the entire product of the
bridge finding (`07` §5.1a) — and a disabled control still asks to be understood when the thing to
understand is "go and cross that bridge".

⚠ **Layers are rebased on the shallowest machine in the filtered map**, not on the rig. `hopsFromRig`
is measured across the whole world, so a foreign server's tab would otherwise open with four or five
empty columns and its content off the right-hand edge. The rebase is a **no-op** for the whole-world
map, which is why it lives in `NetLayout` rather than at the call site. It does **not** rewrite the
sightings: `hopsFromRig` means what it says and several other surfaces read it.

⚠ **A bridge's own edge is dropped by the filter**, because one of its ends is not on the grid. The
failure that prevents is not a crash — `NetLayout`'s adjacency pass would build a neighbour set
containing a machine with no sighting, and the barycentre arrangement would order the layer around
something invisible. Nothing is lost: the bridge is still drawn, still carries its glyph, and still
names the server on its far side.

## 2.7 A bridge is named for where it goes — **implemented**

Every world has **at least one bridge on the home server**, and every bridge runs under an account
taken from the **`CHARACTERS`** pool: the character half of the name of the server on its far side.
A machine whose prompt reads `muaddib@…` is a door to `<adjective>-muaddib`.

⚠ **THE GUARANTEE HOLDS BY CONSTRUCTION AND IS NOW PINNED.** `NET_SERVERS_MIN` is 5 and step 2's
spanning tree attaches every server to one already placed — so server 1's parent is necessarily home
— and step 5 turns every server-graph edge into a pair of bridges. Nothing in the generator announces
this, and the two edits that would break it (a server count that could be 1, a tree not rooted at
home) would fail no other test. `BridgeAndServerTabsTest.homeAlwaysHasABridge` sweeps 400 seeds; a
companion asserts one of them is within two links of the rig, which is what `applyHomeFloor` already
guaranteed. The cost of losing it is not a crash — it is a character whose network half ends at their
own server, permanently, with nothing on screen to explain why.

⚠ **THE ACCOUNT IS STORED, NOT DERIVED, AND THAT IS THE ONE EXCEPTION.** Every other machine's
operator is `NpcNames.operator(address)`, a pure function of one host — which is what lets
`VirtualFs` generate a filesystem without the world being threaded through it. A bridge's account is
a fact about *two* machines on two servers, so it cannot come from this host's address. Storing one
string on `HostState.operator` beat passing the topology into `listHost`, and from there into the
shell, the file manager and the scanner. Empty means "derive it", so every ordinary machine is
untouched.

⚠ **SYMMETRIC.** Both ends of a cross-server link are bridges and each is a door out of where it
stands, so the home-side bridge runs under the far server's character and the far-side one under
home's. Naming only the home end would leave the machine a player meets *after* crossing looking
ordinary, which is exactly when they most want to know they are standing on a way back.

⚠ **ZERO DRAWS**, so every existing world regenerates identically. The name is read off the peer's
server, which was named in step 3 from a hash of its id. ⚠ **It runs after `applyHomeFloor`**, which
may *promote* a nearer machine to a bridge and demote the one that rolled — naming before that leaves
the demoted machine holding a bridge's account and the promoted one an ordinary person's, i.e. the
map advertising the wrong door.

⚠ **THE POOLS ARE DISJOINT AND THAT IS WHAT MAKES THE ACCOUNT A TELL.** `NpcNamesTest
.poolsDoNotOverlap` was written for another reason entirely (a player who just met an operator called
`magnus` would read `roguish-magnus` as connected to them) and pays off here: an account from
`CHARACTERS` is one no ordinary machine could ever have.

⚠ **IT CAN EXCEED THE SEVEN-CHARACTER OPERATOR BUDGET.** `OPERATORS` is capped at seven because the
node box's address line has that much room after the address and a separator; `CHARACTERS` is capped
at twelve, for the tab strip. Measured across seeds, most fit and a few clip by one to three
characters (`noctilus` → `noctilu`, `cunningham` → `cunning`) — the treatment a machine *name*
already gets there, clipped from the right because a name is read from its front, with the full
string on the tooltip, in the host list and in the recon file. Home-server bridges have the shortest
addresses and fit exactly. The alternative was to restrict which characters may name a bridged
server, which would make the server pool depend on a client layout constant.

⚠ **`relabelLegacy` fills it on an existing character**, the same sanctioned exception machine and
server names take — a name has no mechanical consequence, so rewriting one cannot change an outcome.
It also **clears** the account off anything that is no longer a bridge: a stale one is a plain desktop
advertising a server it does not reach, which is worse than an unnamed bridge, because the first is a
lie the map tells confidently and the second is a gap.

## 2.7a A well-mapped server gives up its exits — **implemented** (2026-08-09)

⚠ **THIS FIXES A MEASURED GAP, AND THE GAP WAS NEVER IN THE GENERATOR.** Every world has a bridge on
home — pinned at **400/400 worlds** — so a report of "my home server generated no bridge" is a
discovery failure, not a generation one. Measured over the same 400 worlds: a first **WIDE or DEEP**
sweep from home found home's own bridge in **75%** of them. In the other quarter the exit was simply
inaudible from where the player was standing — outside the hop ceiling, or below the audibility
threshold for that (machine, vantage) pair — and since re-sweeping the same spot is deliberately not
a reroll (§2.7, `NET_SWEEP_VANTAGE_FLOOR`), it stayed invisible until they happened to wander far
enough.

**The rule.** Past `Balance.NET_BRIDGE_REVEAL_SHARE` (**0.73**) of a server's machines discovered, a
sweep of `NET_SWEEP_BRIDGE_MIN_TIER` or better finds **every bridge on the server it is standing on**,
regardless of position, roll or yield cap. Re-measured after: **399/400**.

⚠ **The completion metric is HIDDEN and must stay hidden.** Nothing publishes it — not the map, not
the recon file, not `NetMap`. A number saying "you have found 68% of this server" is a **count of
undiscovered machines** wearing a percentage, and §2.6's standing rule is that an undiscovered host
does not exist: no placeholder, no count. What the player sees is the *consequence* — map more, find
the door — which is legible by playing without ever putting a denominator on screen.

⚠ **It overrides three rules and each is deliberate.** The **audibility threshold**, because the whole
point is that position stops mattering; the **yield cap**, because a bridge detected and then sorted
out of the list would make the rule fire and appear not to; and the **hop ceiling**, because a server
mapped to 73% may have its exit further away than the instrument reaches, and this must not degrade
into "and also be lucky about where it is".

⚠ **It does NOT override the tier gate.** A base sweep still never sees a bridge, so the free
instrument is exactly as it was and this remains something the first upgrade is *for*. ⚠ And a
revealed bridge is **counted as considered**, or the sweep reports `found > inRange` — which
`SweepReport`'s own constructor throws on, because the player reads those two numbers as a fraction.

## 2.7b A sweep never reaches onto another server — **implemented** (2026-08-09)

⚠ **THIS WAS FALSE UNTIL NOW, AND SILENTLY SO.** Hop distance is a BFS over the full link graph and a
cross-server link is an ordinary edge in it, so standing on a bridge with a two-hop ceiling put the
far bridge **and its neighbours** in range. A sweep was quietly delivering a foreign server's machines
with nothing having been opened. Measured: the leak was in the *candidate* set on essentially every
such sweep, and only rarely produced a discovery — which is why no test caught it and why the
regression test asserts on `inRange` rather than on what was found.

**The rule.** A sweep considers machines on the vantage's **own server** only. The single exception is
a **DEEP** sweep taken standing **on a bridge**, which may publish the machine at the far end — that
one machine, by identity against `bridgePeer`, never a neighbour of it.

## 2.7c A crossing is opened with a NET_MAN — **implemented** (2026-08-09)

A breached bridge is a door you are standing in. **Nothing on the far server answers** — no sweep, no
port scan, no breach, no shell — until a **NET_MAN** is running on a breached bridge into it.

| act | what it costs | what it buys |
|---|---|---|
| breach the bridge | a breach | a foothold on the door. Nothing about the far side. |
| **DEEP sweep from the bridge** | a deep sweep's cycles, duration and noise | the far bridge on the map, a **rough count** of what is over there with its accuracy stated, and the server on the tab strip |
| **upload a NET_MAN** | one consumable + 5 minutes of being the loudest thing on the network | the far bridge on the map, the server on the tab strip, and **everything over there becomes actionable** |

⚠ **Reachability is a WALK, not a per-bridge flag.** `NetRules.crossable` runs from the home server
outward over bridges that are breached *and* carrying a NET_MAN, to a fixpoint. Asking only "does this
host's own bridge have one" would let a player who opened one crossing act on a server two crossings
out.

⚠ **Opening a crossing publishes the machine at the far end, and both routes across do.** Without it
an opened crossing opens onto nothing the player can stand on — measured on the walking fixture, a
player's graph stopped dead at the edge of their home server exactly as if the crossing had never been
opened. The peer, and nothing behind it.

⚠ **The estimate is a band and is never rendered as a count.** `Balance.netPeerEstimate` spreads the
true count by `NET_PEER_ESTIMATE_ACCURACY_PERCENT` (60%), symmetrically, floored at 1, **hashed from
the bridge** so re-surveying is not a reroll. The accuracy travels with it on the wire
(`Sighting.peerAccuracyPercent`) precisely so no surface can show one without the other. ⚠ When
`PortScanTarget.PEERS` is finally built (`design/17` §8 PS-4) it must write the **same** field with a
tighter estimate — two fields would be two answers to one question.

⚠ **NET_MAN is ETHECOIN-gated and that is a deliberate reading of I2.** It is a consumable that buys
*access to a region*, once, and the crossing stays open for good — so the total a player ever spends
is bounded by the number of servers in their world rather than by how much they travel. It buys no
ceiling, no compute, no tier. ⚠ **It is the only route to every server but home**, which is why it is
not schematic-gated: putting the world behind a drop would make a whole game's content contingent on a
roll. ⚠ **That also makes its price load-bearing** — see `Balance.NETMAN_PRICE_EC`. If mining income is
ever re-tuned downward this figure moves with it, or the world quietly closes.

⚠ **The upload is loud while it runs and silent once it lands.** The noise rides on the task, and
`NoiseRules` counts a task only while it is running — so an open crossing costs nothing to keep, with
no decay curve to tune and no flag to clear. The item is consumed **at settlement**: an interrupted
upload costs nothing, and one that granted the crossing up front would make its duration and its noise
optional.

## 2.8 A server reaches the tab strip when its bridge is **breached and looked through** — **implemented**

⚠ **NARROWED 2026-08-09, and this section asserted the opposite until then.** Breaching alone used to
be enough, on the reasoning that taking the bridge is "the moment the far side becomes somewhere you
can go". That stopped being true when a sweep lost the ability to reach across (§2.7b): breaching now
tells the player *nothing whatever* about what is behind the door, so a tab put up by the breach alone
would be a named, permanently empty server they had learned nothing about and could not act on. What
earns the tab is **a breach plus one of the two acts that genuinely look across** — a DEEP survey from
the bridge, or a NET_MAN.


⚠ **DISCOVERED *AND* BREACHED, both halves — and now a third.** Until 2026-08-09 `knownServers` came
from sightings alone, so a foreign server reached the strip only when a machine *on* it had been swept — which needs
a foothold on the bridge, a `connect`, **and** a sweep from over there. The door opening and the door
appearing on the strip were three actions apart, and §2.6's own note about a tab that "carries the
name and says it is unexplored" described a state that could not occur. It can now.

⚠ **BREACHED, NOT MERELY IDENTIFIED.** Identifying a bridge tells the player a server is there and
what it is called — and the bridge already says that, *on the bridge*, where it can be acted on. A tab
is a place to **go**; keying on identification would put one on the strip for a door the player cannot
open. Taking the bridge is the moment the far side becomes somewhere they can reach.

⚠ **The discovery half still binds.** A foothold on a bridge nobody has found publishes nothing — so
a hand-edited save, or any future rule that sets a foothold without a sighting, cannot leak the shape
of the world.

⚠ **The developer facility's "reveal the whole map" breaches every bridge AND OPENS EVERY CROSSING**
(the second half added 2026-08-09 — without it a revealed map is a map of one reachable server), on
explicit direction,
because a reveal that left every tab but home missing would not have revealed the map. It is
**bridges only** and it is a real capability grant rather than a display change — a foothold is what
`connect` checks, so every bridge becomes somewhere the vantage can move. Nothing gets `looted`,
which is a one-time payout `reconcileFootholds` would otherwise credit for the whole world at once.
⚠ The foothold is granted **outside** the already-discovered guard, or revealing *after* exploring
would open fewer bridges than revealing first — the cheat doing less the more of the game you had
played.

## 2.9 The player sets the terms at character creation — **implemented**

The solo band is **5–18 servers**, and the setup assistant gains a pane: how many servers, how deep
each one runs, how connected they are, how often the network answers back, and what is in the wallet
on day one. `state/WorldSettings` holds them; `rules/WorldRules` reads them.

⚠ **NOT CHEATS, AND THE SEPARATION IS MECHANICAL RATHER THAN EDITORIAL.** `rules/Cheats` overrides
rules a game is already running under — hidden behind a key sequence, logged on every use, refused
for any character that can reach another player. `WorldRules` holds the terms the world was *built*
under, chosen before the first draw, in the open, at the moment every other game asks the same
question. The test that separates them: **could the player have got here by playing?** A twelve-server
world is one you could have been given. A compute ceiling past the top of the ladder is not.

⚠ **They meet in exactly one place and COMPOSE.** `WorldRules.intrusionChance` applies the world's
scale and then the developer facility's. Letting either win outright would make one of the two
silently do nothing, and which one would depend on an ordering nothing on screen explains.

⚠ **EVERY DEFAULT REPRODUCES THE SHIPPED GAME.** Random on both sizes, the tuned cross-link rate,
100% events, and whatever the game's own starting balance is — `startingEthecoinWei` defaults to
`Balance.STARTING_ETHECOIN_WEI` rather than to a literal zero, so a character created with the default
still gets the game's answer if that ever moves. Pressing Continue through the pane yields the
character the wizard produced before it existed.

⚠ **THE SIZE SETTINGS ARE GENERATION INPUTS, READ ONCE, AND INERT AFTERWARDS** — the pane says so.
`TopologyGenerator.generate` runs once per character and refuses to run twice, which is the guard that
stops a world being re-rolled; settings arriving later change nothing while looking exactly as though
they should. `eventChancePercent` is the exception and keeps applying, because it is a rule rather
than a shape.

⚠ **THE RNG CONTRACT SURVIVES BY DRAWING UNCONDITIONALLY.** The server count is rolled and *then*
overridden, so the default path is bit-for-bit what it was; the per-server depth follows the same
pattern. The generator's promise now reads "a fixed seed **and these settings**", which is the honest
statement and costs nothing — the settings sit on the save beside the seed, written before the first
draw.

⚠ **A CHOSEN DEPTH IS A REQUEST, NOT A GUARANTEE.** `Balance.netNodeDepth` still clamps it against the
server's machine budget (§2.2), because a spine longer than `NET_SPINE_BUDGET_SHARE` turns a small
server into a corridor with one fork at the end. A player asking for 13 on a 6-machine server gets the
deepest it can afford — the same treatment a deep roll already gets.

⚠ **`netServerChordMax` SCALES, AND THE DEFAULT BAND IS BIT-FOR-BIT UNCHANGED.** A flat budget of two
chords is a sensible fraction of a five-server world and almost nothing on an eighteen-server one, so
a fixed cap would have made the cross-link setting invisible at exactly the sizes a player raises it
for. `max(NET_SERVER_CHORD_MAX, servers/3)` leaves 5–7 servers on 2 and gives 18 six.

⚠ **NO SETTING CAN DISCONNECT THE WORLD.** The spanning tree is built before chords are considered and
is never removed, so connectivity is a property of the construction rather than of a roll — "None —
one route to each server" is a legitimate choice and still reaches every server. Tested at 0%, and the
§2.7 bridge guarantee is tested at the smallest, least-connected world.

⚠ **THE MAP'S TAB STRIP HAD TO WRAP.** It was an `HBox`, which lays its children out on one line
whatever the width — so a dozen servers pushed tabs off the right-hand edge with nothing to scroll and
no indication they existed, the map silently losing the only control that reaches half the world. It
is a `FlowPane`; rendered at 18 servers it takes two rows.

## 2.11 The tab strip is ordered by depth from home — **implemented**

Home, then everything one bridge away, then two, and so on. Within a depth, by name.

⚠ **THIS REVERSES §2.6's ORDERING, AND THE REASON THAT RULE GAVE WAS NOT TRUE.** The previous rule was
home-then-alphabetical, and it rejected depth on the grounds that *"depth reorders the strip the moment
a chord changes a server's distance"*. The generator cannot do that. `ServerState.depthFromHome` is
assigned in exactly one place — `TopologyGenerator` step 3, from the spanning tree — and **nothing
anywhere else writes it**, so it is fixed for the life of the character. Chords are a generation-time
pass constrained to `|Δd| ≤ 1` precisely so BFS depth is invariant under them, which
`TopologyGeneratorTest.depthIsInvariantUnderChords` proves over ten thousand seeds. A tab's depth
never moves, so ordering on it is exactly as stable as ordering on a name.

⚠ **THE OTHER HALF OF THAT ARGUMENT STILL HOLDS AND IS PRESERVED.** Discovery order was rejected as "a
private history that makes two players' strips disagree about a world they are both looking at", and
that is still right — so the tiebreak within a depth is the **name**, never the order servers were
found in. Two players who have found the same servers see the same strip.

⚠ **It is the axis the world is built on.** §4 steps difficulty across a bridge and keys the whole
danger gradient on this number, and §2.10's header prints it beside the strip — so a strip ordered by
it is a strip ordered by how far into the game each tab is.

⚠ **Home stays first for free**: it is the only server at depth 0 by construction. The explicit home
tiebreak is insurance for the one other thing that can report depth 0 — the empty `ServerRef` a lookup
miss falls back to.

⚠ **The old test could not tell the two rules apart.** It used depths 0/1/2 named
freeman/atreides/cortana, where alphabetical-after-home and by-depth produce the identical strip. The
replacement builds a fixture where the depth-1 server sorts *last* by name, so only one ordering can
satisfy it.

## 2.10 `DEPTH n FROM HOME` names the tab that is open — **implemented**

The header above the graph reads `SERVER <name> DEPTH n FROM HOME`, both halves off
`NetMap.currentServer()`. A filtered map used to keep the **vantage's** server there, so opening a
server four bridges out still read `DEPTH 0 FROM HOME` under the name of the server the player had
navigated away from.

⚠ The depth is the one number on that strip whose whole job is to say how dangerous this place is —
§4 makes the entire difficulty gradient key on it — so a stale one is worse than a missing one.

⚠ **Nothing is lost by re-pointing it.** Where the player is *standing* is carried separately and
always has been: `vantageAddress`, which the same strip prints as `SWEEPING FROM` and which the graph
marks with the heavy frame. On a filtered map, "current server" can only sensibly mean the one on
screen.

⚠ **`ServerTabs.of` must keep being given the UNFILTERED world**, or every tab reports itself as the
current one. It is, and the re-pointing is what turns that from a theoretical hazard into a live one.

## 3. Multiplayer: the map is built out of noise — [PROPOSAL], not implemented

Nothing in this section exists. It is recorded so that the solo shape above is built in a way the
online shape can reuse, rather than being rediscovered later and found incompatible.

### 3.1 Machines arrive by how loud they are

A server's map is not generated up front. **Noise decides what is on it**: the loudest machines within
reach are immediately available, still subject to the 1–7 rule per node. When a sweep finds more than
seven at one node, **the surplus is assigned to the next depth** rather than widening the layer — and
reaching it costs the full loop: breach, move the vantage, sweep again.

That keeps §2's shape rule true online, and it makes the shape of a live server a *record of how loud
its population has been*, which is a thing the game already measures and currently spends only on
heat.

### 3.2 Population shapes the server — depth grows, width churns

The first version of this section put `Depth_N` at the **largest prime factor of the connected-user
count**, and the arithmetic kills it: 13 users gives depth 13, the fourteenth player takes it to 7,
and a sixteenth takes it to **2**. A server's whole world would reshape, and usually *shallow*,
as it filled up.

⚠ **The fix is not to abandon the prime — it is to give it the job it is actually good at.** The
volatility is the interesting part; it just has to land somewhere that volatility is harmless. So the
population drives two different things, on two different functions:

| | Function of | Behaviour | Why it belongs there |
|---|---|---|---|
| **Depth** | π(high-water mark of connections) | monotone, irregular steps, saturating | a world that got deeper must never get shallower |
| **Layer width** | largest prime factor of the *live* connection count | volatile, 2–7 (24 under §3.4) | the shape may churn freely; nothing is lost when it does |

#### Depth: the prime-counting function, ratcheted

```
depth(server) = clamp(NODE_DEPTH_MIN + π(highWaterMark), NODE_DEPTH_MIN, ONLINE_DEPTH_MAX)
```

π(n) is the count of primes ≤ n — **monotone non-decreasing**, so depth can only ever rise, and its
steps land on the primes, so it grows in an irregular rhythm rather than as a straight line. It is
also naturally *saturating*: π(13) = 6, π(50) = 15, π(100) = 25, so a server deepens quickly while it
is young and then slows to a crawl, which is the shape a long-lived world should have.

Against the high-water mark rather than the live count, because **the depth of a world is a fact about
its history, not about who happens to be logged in tonight.**

#### Width: the largest prime factor, and this is where it earns its keep

```
layerWidth(server) = clamp(largestPrimeFactor(liveConnections), 2, BRANCH_MAX)
```

⚠ **The jumpiness is now a feature rather than a hazard.** A server with 13 people on it is a narrow,
deep, corridor-like place; a sixteenth arrives (lpf 2) and it reads as a broad shallow sprawl; a
seventeenth (lpf 17, clamped to 7) blows it wide open. **Nothing a player has already discovered is
disturbed** — width governs where *new* machines attach, and §3.1's noise ordering decides which ones.
That is the whole reason this is the safe end to put the volatile function on.

⚠ **And it makes §3.4's overflow land beautifully.** The clamp at `BRANCH_MAX` (7) is hiding every
prime above it — 11, 13, 17, 19, 23 all read as 7. When a server meets §3.4's condition the ceiling
rises to **24**, and those primes become visible for the first time: the widths a busy server can take
are exactly the primes its population can produce. Nothing extra had to be invented for that; it falls
out of the two rules meeting.

⚠ **A depth that falls must never delete a machine a player has already discovered** — and under this
construction it cannot, because depth does not fall. The rule is stated anyway, because a future
change to the envelope would reintroduce the hazard silently: `NetRules`' whole discovery discipline
is that a found machine stays found, and a map that forgets what it showed you is indistinguishable
from a bug.

### 3.3 Bridges terminate a depth, whether or not it was reached

- **Depth met** → a bridge is placed at the end, to jump to another server.
- **Depth not met** (not enough machines to fill it) → the depth is **cut short with a bridge**, so a
  server always ends in a door rather than in a dead end.
- **One or more bridges**, one per federated server connected to this one, each carrying an
  **online/offline indicator** so a player can see which doors are shut.

⚠ The indicator is new and is the first thing in this game that publishes another server's liveness.
It is safe because it says exactly one bit about a server the player already knows exists — the same
bar `17` §3.1 sets for the peer count.

### 3.4 The 24 overflow

When the depth is met **and** every node is full at its 1–7 **and** a sweep still finds new machines,
the per-node limit rises to **24**, filled **closest to the player's rig first, by noise at the moment
of sweeping**. This is the pressure valve that lets a long-lived server keep growing after its shape
is otherwise full.

⚠ It is deliberately a *late* rule: 24 is not a normal layer width, it is what a server looks like
when it has been busy for a long time. The map's stack fold is what keeps that readable.

### 3.5 ⚠ A quiet player stays hidden — the 34% ceiling

Deeper traversal makes a *less noisy* machine more likely to be found. But **a very quiet player
remains very hard to find**: against a deliberately quiet machine, even the deep sweep tops out
around a **34% chance of success**.

⚠ **This is a rule about player rigs, not about NPCs, and conflating the two would re-tune the whole
game.** Today a quiet NPC machine is 72% at deep tier, and `07` §5.1a's vantage rule takes it to about
89%. Applying a 34% ceiling to `SignalStrength.LOW` would cut every NPC's findability by more than
half. The shape that works is a **fourth band below `LOW`** — call it what a player *achieves* by
going quiet, not what a machine *is* — with the deep sweep the only instrument that reaches it at all.

⚠ It is also the point where hiding becomes a strategy with a cost, which is what makes it worth
having: staying at 34% means staying quiet, and `08`'s noise economy is what charges for that.

---

## 4. Difficulty: flat within a server, stepped across a bridge

### 4.1 The rule

**Every machine on one server is about as hard as every other.** Difficulty steps **when the player
crosses a bridge**, and only then.

This is close to what the code already does — `Balance.netTier(serverDepth, u)` keys on the *server* —
but not equal to it: the tables carry a real spread inside each depth (home rolls tier 1 or 2 at
70/30; depth 2 rolls 2, 3 or 4). Under this proposal that spread narrows to at most one step, so a
server reads as *a place with a character* rather than as a bag of machines.

⚠ **Infrastructure keeps its +1.** `TopologyGenerator` already lifts gateways and bridges a tier, on
the argument that the two machines a player must get through to make progress should not also be the
softest things on their server. That is a statement about *position*, not about depth, and it
survives.

### 4.2 The tier ladder rises across bridges — and stays on the ethecoin side

"Slowly increasing the base tier of upgrades required to sweep, breach and port scan" is the
progression this document exists to make real. Each bridge crossed should raise the floor of what a
player needs to work effectively.

⚠ **Every depth gate must sit on the ethecoin ladder, never on a schematic.** This is **I2**, and it
is one careless table away:

- **Sensitivity may be gated.** Requiring a wide sweep to work usefully two servers out is the same
  shape as `NET_SWEEP_BRIDGE_MIN_TIER`, and `02` §1.1 step 4 puts breadth on ethecoin.
- **Reach may not.** `NetRules.hopCeiling` takes no sweep tier at any depth and must never learn to.
- ⚠ **A schematic-gated tool must never become *required* at a depth.** A schematic is found, not
  bought, so a depth that demanded one would be a wall with no route through it — content invisible
  rather than content gated, which is exactly the argument `NET_SWEEP_BRIDGE_MIN_TIER` makes for
  being 2 rather than 3.

### 4.3 ⚠ The counterpart already exists and must not be double-counted

`netCounterHackChance`, `MonJobs` density and `netDefendedChance` all already scale on server depth.
"Deeper is more dangerous" is therefore *already* true in three places; this section is about the
**tier floor**, and a fourth independent depth scalar is how a fifth server becomes unplayable
without anybody deciding it should be. Re-check `03`'s income tables against any change here — the
economy numbers are calibrated as a set.

---

## 5. Open questions

- **NT-1** — the solo node-depth band is **4–13** and the machine budget is unchanged, so on small
  servers §2.2's share clamp binds and a rolled depth is not the depth you get: a 12-machine home can
  never exceed 7 however the roll lands, so the top of the published band is unreachable at home.
  The fix would be to raise `netMachinesMin` at home, which changes the tutorial surface — the first
  screen a new player ever sees — so it is left as the clamp. ⚠ **Measured consequence**: home servers
  now run 4–7 deep and deeper servers use the full band.
- **NT-6** *(new)* — a deeper server costs more **positions** to cross, and that is a real change to
  the discovery loop rather than a side effect. Measured: with a one-hop ceiling, walking twelve
  positions at base tier found 10.1 machines under the old bushy random tree and **7.7** under this
  shape, because a spine machine has one parent and one child where a random recursive tree's had
  several. `VantageDiscoveryTest` records both numbers. Whether that is the right price for a legible
  server is open; it is the number to watch if `NET_SPINE_BUDGET_SHARE` rises.
- ~~**NT-2** — §3.2's prime-factor volatility.~~ ✅ **Resolved: the prime keeps its job, and it is
  width rather than depth.** Depth is π(high-water mark) — monotone, irregular, saturating — and the
  largest prime factor of the live count drives layer width, where churn costs nothing and reads as
  the server breathing. What is still open is the two constants: `ONLINE_DEPTH_MAX`, and whether
  width should be the lpf of the live count or of a short rolling window (a single player
  connecting and disconnecting currently flips the width, which may be too twitchy).
- **NT-3** — §3.5's fourth signal band. What *action* puts a player rig into it, what it costs to stay
  there, and whether an NPC can ever be in it. Related to `08` §1's noise economy.
- **NT-4** — §3.4's 24 overflow interacts with `docs/client/09` **NM-5**: a 24-wide layer is well past
  what the map can draw without folding. The stack fold covers it; what is unknown is whether a
  24-machine fold is still legible or is just a number.
- **NT-5** — does the difficulty step at a bridge apply to a bridge crossed *backwards*? Returning to
  home should not be dangerous, and nothing currently makes it so; a naive "difficulty follows the
  deepest server visited" would.
