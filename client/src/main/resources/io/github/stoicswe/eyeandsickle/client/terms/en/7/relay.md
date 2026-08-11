---
id: relay
section: 7
name: relay
canonical: relay
gloss: A machine that carries somebody else's traffic onward.
status: real, simplified
aliases: router, hop
seeAlso: gateway(7), terminal(7), store(7), noise(7)
notes: Loud, and usually the best position on its server rather than the best payout. It is the archetype that pays in reach.
revision: 1
---

## DESCRIPTION

A relay is the routing hardware inside a server: not somebody's desk, not a file
store, but a machine whose whole job is passing traffic between other machines.

That makes it worth more than it pays. A relay is wired to more of its server
than anything else on it, so standing on one puts more within reach of the next
sweep. It is the archetype that pays in **position**, and position is the one
thing no amount of ethecoin will sell you.

It is also loud. A machine that everything talks through is a machine whose
owner notices things.

## REAL-WORLD COUNTERPART

real, simplified — relays are real, and the word is broader than one box.

A relay is anything that accepts a message it is not the final destination for
and passes it on. That describes several different machines at several different
levels, which is what this game collapses into one archetype:

- a **switch** moves frames between machines on one network;
- a **router** moves packets between networks — your gateway is one;
- an **application relay** forwards whole messages, understanding what they are:
  a mail server handing an email to the next mail server, or a Tor relay passing
  a stream to the next hop.

The last of those leaves the best evidence, and you can read it. Open any email
you have received and ask your mail client to show the raw or original message.
Near the top is a stack of `Received:` lines, one per relay that handled it,
newest first. Each one names the machine that passed it on and when. That is a
routing history for a single message, written by the machines themselves.

## CAVEATS

The simplification is that the game gives "relay" a machine, and reality gives
it a **role**. One physical box is usually several of the things above at once,
and a mail server relaying your email is an ordinary computer that also does
other work.

The position advantage is real, though, and it is the part worth carrying out of
the game: a machine that carries other machines' traffic is a machine that can
see other machines' traffic. That is why a compromised router matters more than
a compromised desktop, and why "it is only a switch" has never been true.
