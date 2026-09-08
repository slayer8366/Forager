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
- This file and the pre-build report are committed on `claude/new-session-pb8ynb` (the session's
  designated branch, cut from `main` at `8eacc91`), not on `claude/new-session-b7z9bg`. The
  ruling names the latter as the base for all path-home work; the two documents will need to
  reach that branch (or `main` after PR #77 merges) before the build dispatch cites them from its
  own tree.
