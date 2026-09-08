<!-- Filed 2026-09-08 from the owner's ruling document, verbatim below the rule. The filing note at the end is the coder's, not part of the ruling. -->

# Ruling — self-intersection joining is inside the retrace ruling

**Owner ruling, 2026-09-07.** Recorded so it survives into a cold-start session. This is a decision of record, not a dispatch.

---

## What was ruled

The **fourth option** from the path-home pre-build report — joining the track to itself wherever it passes within ε of itself, and taking the shortest route home along the joined track — is **inside the retrace ruling**. It may be built.

It is the only candidate that handles the patch case, a patch 100 m from the car, the return leg, and the loop together, and it has no threshold and therefore no boundary to be knife-edged at.

## The condition the ruling carries

The owner accepted the planner's reading. That reading was **conditional**, and the condition is part of the ruling, not commentary on it:

> ε is acceptable only while the gap it bridges is within GPS error of the walked path anyway — i.e. while stepping across ε is not meaningfully different from standing where the walker already stood.

The retrace ruling exists because a straight line crosses **unknown** ground in failing light. ε metres between two points the walker actually occupied is a different object from a straight line across a valley. But it is not zero, and **ε is a tunable that will want to grow.** Every future request to raise it is a request to re-open this ruling, and must be treated as one.

## What bounds ε

On the one device measured: GPS accuracy is a **constant 3.7900925 m** on all 289 GPS fixes — a placeholder, not a measurement. The sampler gates writes at **30 m**. Between those, the honest window for ε is narrower than it first appears, and the constant means the device cannot tell us the true figure.

**ε is therefore not yet a number.** Setting it is a separate decision, and it should be informed by beta data from hardware whose accuracy is a real measurement rather than a constant. Until then, prefer the smallest ε that closes the patch case at all.

## What this ruling does not do

- It does not reverse the retrace ruling. The walked track remains the path home.
- It does not authorise a straight-line fallback under any condition.
- It does not set ε.
- It does not authorise wiring `returnWalkingTime` to a caller. Nothing is displayed today; that stays true until the build dispatch says otherwise.

## Related findings that must travel with this

- **The estimate never decreases during a recording.** `pathHome` sums every consecutive pair and points only append, so the linear case reads 6.8 km standing at the car. The patch problem and the return-leg problem are one problem. This, not the 115× ratio, is the defect being fixed.
- **Origin accuracy is not persisted.** The 7.279 m figure used in earlier planner reasoning was readable only because the first track point happened to be the same fix. On a future recording that number would not exist. Any argument resting on origin precision is unsound as currently stored.
- **Base branch for all path-home work is `claude/new-session-b7z9bg` (PR #77).** `main` is schema 14 and has no `PathHome`, `ReturnWalkingTime` or `MovingPace`. All three pre-build reports flagged this collision risk independently.

---

## Filing note (coder's, 2026-09-08 — not part of the ruling)

Filed verbatim so the ruling survives the session, per CLAUDE.md ("an audit, a review, a design
decision … that lives only in a session transcript is not recorded"). Read against the code on
`claude/new-session-b7z9bg` at `c914a30` when filing:

- **"The sampler gates writes at 30 m"** is the HIGH_ACCURACY ceiling only.
  `TrackRecordingMode.kt:27-29` on that branch: HIGH_ACCURACY 30 m, BALANCED 50 m, BATTERY_SAVER
  100 m — and BALANCED is `startRecording`'s default (`TrackRecordingViewModel.kt:162`). So the
  upper edge of the window that bounds ε is 50 m in the default mode and 100 m in the saver mode,
  not 30 m. The ruling's conclusion (ε is not yet a number; prefer the smallest that closes the
  patch) is unaffected; the window is wider than stated, which argues the same way.
- The pre-build report's closure test was insensitive to ε across 8–20 m on the synthetic patch
  (`2026-09-08-path-home-ratio-prebuild-report.md`, §C.5); the synthetic switchback joined at 12 m
  and not at 8 m. Synthetic fixtures — the real tracks have not been run.
- This file, the pre-build report and its script are committed on both `claude/new-session-pb8ynb`
  (the session's designated branch, cut from `main` at `8eacc91`) and, at the planner's instruction
  of 2026-09-08, on `claude/new-session-b7z9bg` — the base the ruling names for path-home work.

---

## Planner's correction and real-track results (2026-09-08 — planner's words, filed by the coder)

**The 30 m figure was the planner's error, not the coder's** — the planner wrote the ruling
document and took the HIGH_ACCURACY ceiling for the sampler's gate; the coder caught it. BALANCED
at 50 m is the recording default. This weakens the argument made above for the
window bounding ε being narrow, and the correction is part of the record.

**The candidate was run on both real GPX exports, in the planner's session.** These are the planner's
results, not reproduced in the sandbox (the exports are gitignored and were not available here):

| | before (retrace) | track-network home | walking time |
|---|---|---|---|
| Track A | 733.0 m | **6.4 m** at every ε from 4 m up | 13.7 min → 0.1 min |
| Track B | 92.0 m | **3.0 m** at every ε from 4 m up | — |

Both land on the straight-line distance exactly, because at these densities the walker passed
within ε of the origin often enough that a near-direct route exists along points actually
occupied. ε = 4 m already saturates; nothing changes up to 50 m. The ε insensitivity is stronger
than the synthetic fixtures suggested.

Two cautions on reading this, the planner's:

1. **Saturation is not a licence to raise ε.** It means the patch case is closed at the bottom of
   the range, which is the argument for keeping ε small — the full benefit arrives at 4 m and
   every metre above that is unwalked ground bought for nothing. The synthetic switchback that
   needed 12 m is the case that argues upward, and it is the one that should set the floor, not
   these two tracks.
2. **These tracks cannot test the disqualifying case.** Both are patches. Neither exercises the
   loop, the return leg, or a genuinely distant walker — the cases where a too-large ε would send
   someone across unwalked ground. The candidate is confirmed to fix what it was designed to fix;
   it is not confirmed safe.

**Cost, to be priced before building:** the join scan is O(n²) — 8,911 pairs on 135 points. A
four-hour recording is thousands of points. Coder's notes on that, unverified estimates, not
measurements: (a) the intended caller is the 15 s track poll, not every fix (Items 1–3 completion
report, "What the next dispatch inherits"), which bounds evaluations at four a minute; (b) a
four-hour HIGH_ACCURACY track is at most 2,880 points (5 s floor), 4.1 M pairs per full scan;
(c) points only append, so closures need checking only for the new points against the existing
ones, O(n) per point with a grid hash and O(n) per poll without one — the full O(n²) scan is
never required after the first poll if the graph is kept. Which of these the build uses is the
coder's call and must be measured on a device, not asserted.
