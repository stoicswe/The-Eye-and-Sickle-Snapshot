---
id: gateway
section: 7
name: gateway
canonical: gateway
gloss: The machine your traffic goes through to leave its own network.
status: real
aliases: default gateway, default route
seeAlso: relay(7), terminal(7), store(7), hier(7)
reading: RFC 1122 §3.3.1 (how a host decides where to send a datagram); RFC 1812 (the same devices, renamed from "gateway" to "router")
notes: Exactly one per server on the map, at the edge. It is a signpost, not a prize — it carries no loot and no documents.
revision: 1
---

## DESCRIPTION

Every server on the map has exactly one gateway, and it is the machine at the
edge — the way in from outside and the way out from inside. It is the loudest
thing on its server and the first thing a sweep tends to find, which is the
point: it is a signpost.

It is also deliberately worthless. A gateway carries no loot and no documents.
The first machine you find on a new server should tell you where you are, not
pay you for arriving.

## REAL-WORLD COUNTERPART

real — your own machine has one right now, and it has an address you can read.

When a computer wants to send something, it asks one question first: is the
destination on my own network? If it is, it sends the data straight there. If it
is not — and for anything on the internet it is not — it hands the data to its
**default gateway** and lets that machine worry about the rest. Everything you
have ever loaded left your house through one.

The word is older than the thing it now describes. The early internet
specifications called these devices gateways; the modern ones call them routers,
and RFC 1812 is titled for routers where its predecessor was titled for
gateways. Both words are still in use and they mean the same box.

You can see yours:

    ip route show default          (Linux)
    netstat -rn                    (macOS and BSD — look for the `default` row)
    ipconfig                       (Windows — "Default Gateway")

The address it prints is almost certainly the router in your home, and it is the
single point every packet you send passes through on the way out.
