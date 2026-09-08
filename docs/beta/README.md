# Beta

What Forager asks of a closed-beta tester, and the two report templates they fill in. Tester-facing
documents live here, apart from the engineering tree; this file is their index and the source of
truth for the text the owner sends out.

## How reports travel

**The template is a block of plain text.** The owner copies the block out of the file below and
pastes it into the invitation on whatever channel they already use with the tester; the tester
answers inline in their messaging app and sends it back the same way. Nobody is asked to open
GitHub, edit a file, or create an account. This file and the templates are the canonical text —
edit them here, send the block from here.

## Before anything else: what the app does not do

Forager has no account and no telemetry, works fully offline, and strips location metadata from
stored photos. **It sends nothing anywhere.** Every field in these reports is something a person
chooses to type and send. The templates never ask where a tester was — foragers mark spots they do
not want found — and ask for terrain and sky instead: "dense fir canopy", "open ridge", "car park".

## The two templates

| File | When | What it asks |
|---|---|---|
| `device-report.md` | Once | A handle the tester chooses, phone make and model, Android version, whether the phone lives near a magnet, whether Do Not Disturb is usual, whether notifications were allowed |
| `trip-report.md` | After each trip | Battery, location quality (including whether the arrow screen's "within …" number ever changes), compass, the off-track alert, offline maps, anything else — with a track file as an explicitly optional, explicitly explained extra |

Two templates rather than one because the device questions asked on every trip invite "same as
before", which is useless as data. The handle is the join: the tester picks it, it need not be a
name, and it appears on both.

**Why the trip report asks whether the track looked shorter than the walk (added 2026-09-08,
after the GPX full-record pre-build report):** the app keeps a track point only when its timestamp
lands on a whole second (`NetworkProviderFix.kt`). That is a proxy for "this fix came from GPS, not
the network provider" — it has held across five data sets, all from one phone. On a phone whose GPS
fixes carry milliseconds, the same rule throws away good fixes, and the symptom is not a spike or a
blank: it is a track that looks fine and is simply shorter than the ground walked, with corners cut
where the missing points were. Nothing in the app can see that; only the walker can, by comparing
the drawn track with the walk they remember. The line is joined to the device report's make and
model, which is what turns "shorter" into "shorter on this chipset family". Once the export carries
every stored point with its kept/excluded verdict (the GPX full-record dispatch), the tester's file
answers it directly: excluded points along a stretch the tester walked are the rule being wrong on
that hardware. Until then the question is the only instrument.

**Why the device report asks for make and model *and* Android version, and why neither should be
trimmed as boilerplate later:** together they identify the phone's GNSS chipset family, which is the
variable behind the app's network-fix rule (a GPS fix's timestamp is second-aligned on the owner's
device and is a property of the chipset, not a guarantee — see `NetworkProviderFix.kt`). A report of
a track drawn short or empty means nothing without knowing which family produced it. The magnet
question exists for the compass: a phone on a magnetic car mount is a permanent distortion and would
explain "Compass unreliable" reports that have nothing to do with power lines.

**Why the trip report asks whether the "within …" number changes, and why that line is the
highest-value one it now carries:** on the owner's phone every GPS fix reports the same horizontal
accuracy — `3.7900925` m, 289 fixes out of 289 on one walk, identical to seven decimal places
(`docs/audits/2026-09-07-fix-log-walk-findings.md`). That field is what the app's 50 m live-fix
gate, its "Approaching" threshold and the arrow screen's "within 12 ft" all read, so on that device
none of them is tracking real fix quality. The "within" number is the only place a tester can see
the field: on a phone that reports a real accuracy it moves with the sky, and on one that reports a
placeholder it is one number forever. "Always the same — 12 ft" from a second make is the answer
that decides whether this is one phone or the platform; "it changed" is just as valuable. It is
gated on the arrow screen because that is the only surface that prints it, and it is joined to the
device report's make and model, which is what makes the answer usable.

## Why it is the length it is

The trip report asks 24 questions including its gate lines (the 22 it was written at, plus the
"within …" question and the "shorter than the walk" question), five of them in the location block —
each question counted once however it wraps, re-counted from this merged template on 2026-09-08. An
earlier figure of 26 counted the two new questions' wrapped lines and is superseded, not confirmed.
Four of its six blocks open with a line that
lets a tester skip the rest of the block ("never navigated back", "skip if you didn't use them",
"no crash", "no spikes"). A plain walk with recording on answers roughly thirteen short lines, most
of them one word. A trip where everything happened answers all of them, and that tester has
something to say.

## If it has to be shorter — the cut order

Decided when the template was written, so whoever shortens it later does not reinvent the
priorities:

1. **First cut:** move the offline-maps block out of the per-trip report into a one-off "the first
   time you use offline maps" mini-report. Most trips download nothing.
2. **Second cut:** fold the two compass questions into one line with two blanks.
3. **Never cut:** the battery block (the fields the owner asked for by name, and the "nothing
   noticeable" line that makes silence into data) and the five location questions — the fourth,
   "within …" line, added after the walk that found the accuracy constant, because the beta is
   the only thing that can say whether other phones do the same, and the fifth, "shorter than the
   walk", the one that can catch the network-fix rule discarding good fixes on a phone unlike the
   owner's, which no test in the repository can. They are the reason the template exists. Below
   about ten lines the report stops answering the questions that motivated it.

## What "no" means here

Every block accepts "no" and "didn't notice" as complete answers and says so at the top. A tester
who walked an hour under canopy and never saw a stale-fix message is the data point most likely to
go unreported; the template asks for it by name in the battery block and by shape everywhere else.
