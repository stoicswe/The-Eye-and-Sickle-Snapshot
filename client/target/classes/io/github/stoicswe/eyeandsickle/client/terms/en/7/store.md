---
id: store
section: 7
name: store
canonical: store
gloss: A machine kept for holding files rather than for working at.
status: real, simplified
aliases: file server, fileshare
seeAlso: gateway(7), relay(7), terminal(7), storage-tiers(7)
notes: One of only two archetypes that carry recovered documents. What is on it is the reason to breach it.
revision: 1
---

## DESCRIPTION

A store is a machine nobody sits at. It exists to hold files for other machines
to read, which is why it is one of only two archetypes on the map that carry
anything worth reading.

That is the whole reason to go after one. A terminal pays a little money; a store
is where the documents are.

## REAL-WORLD COUNTERPART

real, simplified — file servers are ordinary and probably on the network you are
reading this from.

A file server is a machine that offers its disks to other machines over the
network, so that a directory on your computer is really storage on somebody
else's. Two protocols do almost all of it: **NFS**, which came from the Unix
world, and **SMB**, which came from the Windows one and is what a shared drive at
work is nearly always using.

You can tell which of your own filesystems are local and which are not:

    df -h

Read the leftmost column. A row whose source is a device path — something under
`/dev` — is a disk in the machine in front of you. A row whose source looks like
`server:/exports/home` or `//server/share` is not a disk at all; it is a
conversation with another computer that is being presented to you as a folder.

## CAVEATS

The simplification is that the game makes "store" a kind of machine, and reality
makes it a **job** a machine is doing. Any computer can serve files, including
the one you are using; a machine bought for the purpose is a convention rather
than a category, and the same box is usually doing several other things.

Where the game is honest is in the consequence. Storage that is reachable over a
network is storage that is reachable by whoever gets onto that network, and the
files on it were put there precisely because more than one person needed them.
That is exactly what makes a file server worth attacking and exactly why it is
hard to lock down.
