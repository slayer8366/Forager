# Completion report — Track distance label (build)

**Dispatch:** `dispatch-track-distance-label.md` (owner's upload). **Origin:** the track-distance-display
pulse (`2026-09-07-track-distance-display-pulse.md`), whose inference of a second read path was
refuted; the owner then read the captured version-14 database and confirmed the stored figures were
right all along (`distanceMeters = 732.99`, `pointCount = 135` for track A; 91.976 m for track B).
**Base:** PR #77 at `5d7a0f2`; landed on PR #77 as `f6ca6b0` plus this report. Nothing merged.

## What was built

**One function, three sites.** All three track-length rows the pulse counted — the edit screen's
decided row (`CartographyEntryEditScreen.kt:487`), its candidate row (`:502`), and the report
screen's kept row (`CartographyEntryReportScreen.kt:508`) — already called the one formatter,
`trackSubtitle` (`CartographyEntryEditScreen.kt`, now with a doc comment). The change is inside it:

```kotlin
// before: two roundings, the first of which discards everything that matters at track scale
val km = (distanceMeters / 1000.0).roundToInt()
val distanceLabel = formatDistanceKm(km, distanceUnit)
// after
val distanceLabel = formatDistanceMeters(distanceMeters, distanceUnit)
```

`formatDistanceMeters` is the HUD's formatter (`domain/model/DistanceUnit.kt:71`): feet below a
quarter mile, tenths of a mile above; metres below a kilometre, tenths of a kilometre above. It
already reads the user's `DistanceUnit`, which `UnitSystem` derives, so the label goes through the
units system as the dispatch requires. `formatDistanceKm` stays in the file for the two
offline-region rows (`:542`, `:546`), whose radii are whole kilometres by construction — the job it
was built for. **The comment at `trackSubtitle` states why the two formatters are not the same job
and must not be reunified**, with the "0 mi" case as the reason.

On the two real tracks:

| Track | Stored | Before | After (imperial) | After (metric) |
|---|---|---|---|---|
| A | 732.99 m | "1 mi" | "0.5 mi" | "733 m" |
| B | 91.98 m, 26 points | **"0 mi"** | "302 ft" | "92 m" |

Nothing stored changes; nothing is recomputed or migrated. Existing entries display what they
already hold.

**The GPX decoder** (`domain/GpxCodec.kt`, `decode`) now carries the pulse's note as a doc comment:
no caller in the app; if import is ever wired, the decoded track must be persisted and read back
through the repository before any consumer sees it, or it is the one `Track` that bypasses the
filtered seam. Nothing wired.

## Tests

`TrackSubtitleTest` (new, 3 tests, 6 assertions) — every expected string worked by hand from the
stored metres, never from the formatter:

| Input | Imperial | Metric | Arithmetic |
|---|---|---|---|
| 91.976 m, 4 min (track B, the device's own value) | `"302 ft · 4m"` | `"92 m · 4m"` | 91.976 × 3.28084 = 301.76 |
| 732.9925470825974 m, 22 min (track A) | `"0.5 mi · 22m"` | `"733 m · 22m"` | 732.99 / 1609.344 = 0.4555 |
| 1957.4 m, 65 min (the pulse's unfiltered sum, "1 mi" before) | `"1.2 mi · 1h 5m"` | `"2.0 km · 1h 5m"` | 1957.4 / 1609.344 = 1.2163 |

**The sub-500 m case is pinned first and with the real value**, as the dispatch required: a test
of kilometre-scale values alone passes under both formatters for track A ("1 mi" is wrong but
present) and would never have seen the "0 mi" — the family CLAUDE.md names.

No existing test asserted the old label (grep of `app/src/test` for `trackSubtitle(`, `" mi · "`,
`" km · "`: only `TrackExportPanel`'s own, different `trackSubtitle(track)` in
`NetworkFixExclusionPerConsumerTest`, untouched). No test was modified or silenced.

## Verification

### Reverted-variant check

The same discipline as every revert on this branch: the file copied aside before the edit, restored
from that copy (never from git), the build log checked for compile errors before the XML was read,
XML older than the run refused, the restore compared byte for byte. One one-line revert — the old
two roundings put back (`formatDistanceKm((distanceMeters / 1000.0).roundToInt(), distanceUnit)`,
with its import) — predicting all three `TrackSubtitleTest` cases fail:

| Case | Predicted | Actual message |
|---|---|---|
| 92 m | fail | `expected:<[302 ft] · 4m> but was:<[0 mi] · 4m>` — the defect, verbatim |
| 733 m | fail | `expected:<[0.5] mi · 22m> but was:<[1] mi · 22m>` |
| 1957.4 m | fail | `expected:<1[.2] mi · 1h 5m> but was:<1[] mi · 1h 5m>` |

3 predicted, 3 actual, each message the old label and nothing else's; compile log clean; restore
identical. The 92 m case is the one that could only fail on data the old test set never had.

### Full suite

On the forward tree (`f6ca6b0`), compile log clean, exit 0, every XML written by this run:

| Suites | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| 166 | **1277** | 0 | 0 | 24 |

1274 on this branch before this dispatch, plus the three new cases. The 24 skips are the CI
allowlist's identity set exactly, by `(classname, name)`; the skip count was not touched.
Device: not verified — no `/dev/kvm`; the rows' strings are proven at the formatter.

## Required disclosure

**Confirmed from the code:** the three call sites and that they share one function; the old two
roundings; `formatDistanceMeters`'s rules and constants (`METERS_PER_MILE = 1_609.344`,
`FEET_PER_METER = 3.28084`); that `formatDistanceKm` has two remaining callers in the file (the
region rows) and `roundToInt` none; that `GpxCodec.decode` has no caller. **Confirmed from the
owner's capture, as stated in the dispatch:** the two stored figures. **Confirmed by arithmetic:**
the table above.

**Could not determine:** what the rows look like on a device — Robolectric renders the strings the
screen tests assert (which do not include the distance), and the two screen test classes and the
ViewModel's stayed green; the "302 ft" is proven by the formatter's unit test, not by a screenshot.

**Premises in this dispatch that were wrong:** none. The site count is three, and the three are one
function — which the dispatch's "all three, not one" anticipated; the change reaches all three
because they share it, not because three edits were made. Said so that a later reader does not look
for two missing edits.

**Decided beyond scope:** the doc comment's length at `trackSubtitle` (the dispatch asked for a
comment on why the region formatter is not used; this one also records the "0 mi" case and the
pulse, so the reason survives without the audit trail); nothing else.
