# Completion report: the beta report template

Follows `2026-09-07-beta-report-template-prebuild-report.md`. Documentation only: **no product code,
no tests, no in-app collection.** Built as the owner decided on every point.

**Base:** `main` at `49b65c7`, confirmed. **Note:** this lands on `claude/new-session-102gri`
alongside the timestamp-filter commits (`0402cc0`, `7db1775`), which are unmerged and awaiting the
owner's device pass; the two dispatches share a branch, not a file. If the owner wants the
documentation to merge independently of the code, say so and it moves to its own branch.

**Suite as found, unchanged:** 1201 tests, 0 failed, 24 skipped, skip set byte-identical to the CI
allowlist — this session's last full run (`7db1775`); nothing here touches code or tests, so the
count is reported as found rather than re-run.

## What the owner decided

`docs/beta/` with its own README; a Markdown file whose body is one fenced plain-text block; two
templates (device once, trip per trip) joined by a tester-chosen handle; the block as the channel
with the repo file as the source of truth; the GPX wording as proposed; both departures accepted
(the expected off-track delay kept out of tester-facing text; "HUD" and "position marker" reworded);
all questions in, with the cut order recorded in the README so whoever shortens it later does not
reinvent the priorities.

## What was written

- **`docs/beta/README.md`** — how reports travel (block out, message back, this folder canonical);
  what the app does not do, once; the two templates and why two; why the trip report is the length
  it is; **the cut order, in three lines** (offline maps to a first-use mini-report; the compass pair
  folded; battery and the location trio never cut, and "below about ten lines it stops answering
  the questions that motivated it"); what "no" means here.
- **`docs/beta/trip-report.md`** — the fenced block: preamble (sends nothing; don't say where;
  terrain and sky; "no" and "didn't notice" are answers we need), the handle, and six blocks —
  Battery, Location, Compass, Off-track alert, Offline maps, Anything else — followed by the optional
  track-file paragraph with the owner's wording and the in-app share path.
- **`docs/beta/device-report.md`** — sent once: handle, make and model, Android version, magnet or
  magnetic mount, Do Not Disturb habit and mode, and whether notifications were allowed when first
  asked.

## Verification (what was checked, since there is nothing to test)

**Every question in the dispatch appears in the trip report.** Battery: start and end percentage,
trip duration, recording on, navigating back open, and the negative asked for by name ("Did the
battery bother you? no, nothing noticeable / yes"); device model is on the device report, once.
Location: how often the no-fix/stale states appeared and what was overhead; whether the blue marker
kept moving while the fix was old; spikes or starburst, still or moving, what the sky was like.
Compass: whether "Compass unreliable" appeared and what was nearby; whether the heading was plainly
wrong without it, marked as the more useful answer. Off-track: fired or not (four-way gate),
pocket and screen off, silenced and felt anyway, Do Not Disturb and which mode, how long after
straying. Offline maps: **the caveat first** ("had you ever looked at this area in Forager while you
had signal", with the one-clause reason), region downloaded and how large, detail at the wanted
zoom, blurred or stretched. Anything else: crashes and what they were doing, whether a recording
resumed after a kill ("we expect no"), free text with the dispatch's own sentence. **None dropped.**

**No question asks for a location, a coordinate, or a place name.** A grep of `docs/beta/` for
"where were you", "coordinat", "latitude", "longitude", "place name" finds nothing; the only two
mentions of position are the preamble's "don't tell us where you were" and the track-file
paragraph's statement of what the file contains.

**The negative-result invitations are there:** the battery line by name; the preamble line ("not
wasted lines"); every block's first option is a "no"/"never"/"didn't notice" that reads as a complete
answer.

**The offline-maps caveat is present**, as the block's first line, so the section's answers can be
read.

**Length as built:** 22 lines including the four gate lines (the pre-build report said 19; laid out
in full, with the off-track gate and the offline caveat counted, it is 22 — corrected here). A plain
walk answers about thirteen.

## Owner's reading notes (deliberately not in the tester-facing text)

- The off-track alert's expected delay is about twenty seconds; the template asks for the observed
  delay without saying so, so the answer is not the expectation reflected back.
- "No usable points" or "network fixes" on a tester's Records row, if the timestamp filter has
  landed on their build, is itself a report — the template does not ask for it because the row
  says it; a tester who mentions it in free text is describing a device whose GPS clock is not
  second-aligned.

## After filing — owner's two additions and the branch move

- `docs/beta/README.md` now says why make/model and Android version exist together (they identify
  the GNSS chipset family, the variable behind the network-fix rule) and why the magnet question
  exists (a magnetic car mount is a permanent compass distortion), so neither is trimmed as
  boilerplate later.
- The documentation moved to its own branch, `claude/beta-report-template`, cut from `main` and
  carrying only the beta pre-build report, this report, and `docs/beta/` — it is verified by
  inspection, not by tests, and the timestamp filter it was sharing a branch with is gated on a
  device pass that could send it back. The base note above ("this lands alongside the
  timestamp-filter commits") describes the state before the move.

## Required disclosure

**Confirmed:** the three files as written; the greps above; the suite figure from the last run.
**Inferred:** that testers return reports by message rather than by editing a file; the
thirteen-line estimate. **Could not determine:** the channel the owner uses, the number of testers.
**Premises in this dispatch that were wrong:** none. **Decided without cover:** the "part of the
time" option on the navigating-back line; the notifications-allowed line on the device report (it
makes the 33+ notifications-off state readable from reports); the off-track gate's fourth option
("I strayed and it did NOT fire"), which is the case the dispatch called the more valuable answer
for the compass and applies equally here; the placement of this documentation on the code branch.
