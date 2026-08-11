---
id: terminal
section: 7
name: terminal
canonical: terminal
gloss: Here, an ordinary desktop machine. In Unix, the text device itself.
status: real, simplified
aliases: tty, console
seeAlso: shell(7), gateway(7), relay(7), store(7)
notes: A homonym, and an unusually sharp one. This game labels a person's desktop TERMINAL; in Unix a terminal is not a class of computer at all. See CAVEATS.
revision: 1
---

## DESCRIPTION

On the network map, a terminal is somebody's desktop: a clerk's machine, a
citizen's machine. The quietest archetype and the least defended, carrying a
little money and no story.

They are what a first sweep is meant to find. A new operator's opening moves are
against terminals because a terminal is the one machine that will not hurt you
for getting it wrong.

## REAL-WORLD COUNTERPART

real, simplified — the machines are ordinary desktops, which are real enough.
The word is doing something else entirely.

A real terminal is not a kind of computer. It is the **text device** a program
reads from and writes to — originally a physical one, a keyboard and a screen on
the end of a cable, like DEC's VT100. There are no such devices on most desks
any more, so the operating system supplies fake ones: a terminal emulator draws
a window, and the kernel gives it a device file so that programs cannot tell the
difference.

Your shell is talking through one right now. Ask it which:

    tty

It prints something like `/dev/ttys003` or `/dev/pts/3` — a real path to a real
device file, which you can see in a directory listing. Open a second terminal
window and run it again: a different path, because it is a different device.

## CAVEATS

The simplification is the word, not the machine. This game uses TERMINAL for a
class of computer; Unix uses it for the text device your shell reads from and
writes to, and the two senses have almost nothing to do with each other.

The collision is worth knowing rather than avoiding, because both senses are
common in the wild. "A terminal" in a shop or an airport is a whole machine, the
way this game means it. "A terminal" in any Unix documentation is the device —
which is why the setting is called a *terminal emulator* and why the command
above is called `tty`, for teletype, after the printing machines that were the
first of them.
