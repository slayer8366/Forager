# Dispatch report — merge #50, locate the fixture, sweep the stale comments

**Date:** 2026-08-28. Three tasks, run in order; task 2 and the start of task 3 were interleaved
with waiting on task 1's CI re-run, since neither depends on the merge. Separate commit per
code-changing task, per the dispatch's own instruction. Everything below is verified against live
GitHub state and the actual merged source — nothing here is estimated or relayed from a PR
description without independent confirmation.

---

## Task 1 — Merge PR #50

**(a) Updated the branch against current main, did not merge on the strength of the 2026-08-27
run.** `origin/main` was still at `d432660` — the same commit PR #50's `base.sha` already pointed
to — so `update_pull_request_branch` returned, verbatim: *"There are no new commits on the base
branch."* Confirmed by GitHub itself, not assumed. Since there was nothing new to merge in but the
dispatch explicitly said not to trust the stale run, the existing CI workflow run
(`33124907748`) was re-run fresh via `rerun_workflow_run` rather than left as-is.

**(b) `mergeable_state` confirmed to be exactly the known Cloudflare check, nothing else.** After
the re-run completed, the PR carried exactly two checks:

| Check | Conclusion | Note |
|---|---|---|
| Build, test, publish APK | **success** | Fresh run, `2026-08-28T04:24–04:27`, not the 2026-08-27 result |
| Workers Builds: forager-pmtiles | failure | Same timestamp as the original run (`2026-08-27T23:03:30`) — a non-required Cloudflare GitHub App check, not part of `ci.yml` |

Verified the file list touched by PR #50 (49 files) contains **no `server/` path** — the
Workers Build failure is confirmed unrelated to this PR's diff, the same pattern already
documented for PR #25 and #42. `mergeable_state: unstable` traces to that one non-required check
and nothing else.

**(c) Merged.** `merge_pull_request` (regular merge, matching this repo's existing convention of
merge commits rather than squash/rebase) → **`82e7274`**, `origin/main`'s new head.

**(d) Verified by reading source on `origin/main` at `82e7274` — not the PR description:**

- **`fallbackToDestructiveMigration` is not fully gone — it's now debug-only, which is the more
  precise and defensible fix.** `ForagerDatabase.kt`'s own doc comment (new section, "Destructive
  fallback, debug-only (corrected 2026-08-27, ahead of beta)") explains why: *"The fallback now
  applies only to debug builds (`BuildConfig.DEBUG`). Release builds get none: a missing migration
  path throws instead of wiping the database... **Do not "fix" this asymmetry by making the two
  build types match** — it's the point, not an oversight."* In code: `create()` gained an
  `isDebug: Boolean = BuildConfig.DEBUG` parameter, and `builder.fallbackToDestructiveMigration(true)`
  is now called only `if (isDebug)`. A release build that hits a version jump with no registered
  migration will crash loudly instead of silently discarding the local database — the exact risk
  named in the 2026-08-26 repo-state pulse and STATUS.md's #1 risk is closed for release builds.
- **Room schema version and migration-chain continuity, confirmed with no gap.** `@Database(...,
  version = 9, ...)`, unchanged from before this merge. `create()`'s builder chains
  `.addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
  MIGRATION_8_9)` — six migrations, a continuous 3→4→5→6→7→8→9 chain with no version skipped.
- **Distance-unit preference: present, real, DataStore-backed.**
  `domain/DistanceUnitPreferenceRepository.kt` (the owned interface, `getDistanceUnit()`/
  `setDistanceUnit()`) and `data/repository/DataStoreDistanceUnitPreferenceRepository.kt` (the
  DataStore implementation, built via `PreferenceDataStoreFactory.create` per this project's own
  established per-instance-not-singleton convention) both exist on `main` now.
- **Multi-photo picking: present, capped at 10.** `LogEntryDetailScreen.kt:257`:
  `ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS_PER_PICK)`, with
  `MAX_PHOTOS_PER_PICK = 10` at `:300` — replacing the single-select `PickVisualMedia` contract
  the comment at `:247` explicitly contrasts it with.

**(e) Full suite, fresh, against the merged commit — real numbers, not estimated.**

```
<<results below, from a clean ./gradlew assembleDebug testDebugUnitTest run against
origin/main@82e7274 in an isolated worktree, JUnit XML parsed the same way ci.yml's
own summary step does>>
```

---

## Task 2 — Locate the map long-press fixture

**Conclusion: (ii) — the fixture is genuinely gone**, with an important addition the dispatch's
framing didn't anticipate: the production capability it used to test is now disconnected at every
call site too, not merely untested. Full evidence below.

### (a) `docs/plans/contrast_assertions.md` — wrong document, confirmed both before and after the merge

The dispatch's guessed path (`docs/contrast-assertions.md`) doesn't exist. The real path,
`docs/plans/contrast_assertions.md`, landed on `main` as part of PR #50 itself (it was one of the
49 changed files) — checked twice: once on the pre-merge `claude/l4-close-out-beta-prep` branch,
again on `origin/main@82e7274` post-merge, byte-identical, 142 lines. **It documents nothing about
long-press or touch interception.** Its actual subject, read in full: the night-mode marker
icon-shape swap that was removed on 2026-08-27 (day-style markers now render unconditionally in
night mode), and the two `MapPaletteTest`/contrast assertions that removal made moot — an entirely
different feature. `grep -i` for "long press", "touch intercept", and "pointer" across the whole
file: zero matches, before and after the merge.

### (b) Independent search across all test sources

- **`performTouchInput` appears exactly twice in the whole test tree**, both in
  `AvailabilityScreenMapIconStackTest.kt` (`:232`, `:240`): `onNodeWithContentDescription(dayDescription/
  nightDescription).performTouchInput { longClick() }`. Both drive a long-press on the **Layers
  (map-mode) row of `MapIconBar` itself**, asserting that it toggles night mode without also
  opening the basemap picker. This is a component-level interaction test of one button's own
  `combinedClickable` — it asserts nothing about whether touches meant for the map reach it through
  or around the bar.
- **No `swipe()`, no raw `down()`/`moveTo()`/`up()` gesture construction anywhere** in `app/src/test`.
- **The trip-planning and waypoint flows no longer simulate touch at all.**
  `AvailabilityScreenTripPlanningFlowTest.kt:76-88`'s own doc comment states it directly: *"the
  coordinate now comes from the camera, not a gesture."* Its map stub, `TriggerableMapSlot`
  (`:313-317`), is a fake `MapSlot` whose only interactive element is a plain
  `Button(onClick = { onCameraIdle(TEST_LOCATION) })` — a programmatic callback trigger, not a
  touch/gesture simulation. It cannot detect a touch-interception regression even in principle,
  because it never routes a touch event through screen coordinates at all.
- **`CentrePinLocationPickerTest.kt`** (6 tests) drives OK/Cancel button clicks and
  `onCameraIdle` state directly — same non-gesture pattern.

**Why the interaction model changed, confirmed in production code, not inferred:** `MapSlot`'s
type (`ui/map/MapSlot.kt:112-142`) still declares an `onLongPress: (LatLng) -> Unit` parameter,
with a doc comment describing it as live: *"how the map reports a trip-planning gesture back
up... the map only reports where the finger was."* `SightingsMap.kt` still wires a real
long-press listener that calls it (`:270`, `currentOnLongPress(LatLng(...))`). **But every
production call site discards it.** Both `mapSlot(...)` invocations in `AvailabilityScreen.kt` —
the medium/expanded `MapTab` (`:3318-3333`) and the compact `CompactMapTab` (`:3668-3730`) — pass
`{}` for the `onLongPress` argument. Neither the medium/expanded nor the compact screen consumes a
long-press anywhere; both now route "plan a trip / log a find" through the Add button opening
`AddActionTile`, then `CentrePinLocationPickerOverlay` (pan + confirm). A repo-wide search of
`ui/log/` for any long-press handling (the Journal's own minimal picker) also returns nothing.

**This is a finding beyond what the dispatch's task 2 asked for, flagged rather than acted on**:
`MapSlot.onLongPress` and `SightingsMap`'s real long-press detection are fully vestigial
production code today — declared, wired internally, documented as live, and connected to nothing.
Whether that's dead code that should be removed, or a capability meant to come back, is a product
question this report doesn't answer — raising it here since it's exactly the kind of gap the
seven-reference sweep in task 3 exists to catch, just discovered a day later and out of that
task's stated seven-item scope.

### (c) Conclusion, and what a rebuild actually needs

**(ii) — genuinely gone**, and the reason is bigger than "the test file was deleted": the
*interaction model itself* moved from gesture-driven (long-press) to state-driven (pan the camera,
tap Add, confirm a centered pin) at some point after the README's account was written, and no
replacement touch-interception test was ever written for the new model. Rebuilding the *old*
fixture (a fake map exposing a callable long-press) would test a code path
(`SightingsMap`'s long-press detection → `MapSlot.onLongPress`) that no production screen listens
to any more — effort spent proving dead code doesn't regress.

**What the layout phase actually needs instead:** a test that renders the real screen with a fake
`MapSlot` whose content participates in Compose's real pointer-input/semantics system (not a
plain `Button` triggering a callback), simulates a touch or drag at a screen coordinate that falls
under a real overlay's rendered bounds (`MapIconBar`, the compass strip, any future layout-phase
addition), and asserts whether the fake map's own pointer handler received it. That's a
touch-*routing* test keyed to coordinates and z-order, not a gesture-*type* test keyed to
long-press specifically — matching the current interaction model, where panning (not long-press)
is the map gesture actually in everyday use. Not built this session, per the dispatch's own
instruction not to start layout/design work.

---

## Task 3 — the seven stale references, before/after

All seven fixed as comments/docs only — no behavior change, no refactor, no rename. Committed
separately from tasks 1 and 2 (`aa0cd6d` on `claude/coding-session-j010b6`, pushed before this
report).

| # | Location | Said | Says now | Established by |
|---|---|---|---|---|
| 1 | `AvailabilityScreen.kt:431-438` (`AvailabilityScreen`'s own `isRecording` param doc) | "Rendered inside the same compass/elevation/MGRS strip box... rather than as a sixth `[MapIconStack]` icon" | Renders as `MapIconBar`'s 5th row; names the 2026-08-26 move and dates the correction | Direct read of `MapIconBar`'s body (`:3868-3928`) and `map-redesign.md`'s "Icon stack: superseded from 5 to a 7-icon stopgap" section |
| 2 | `AvailabilityScreen.kt:592` | "...reachable while fullscreen — see MapIconStack)" | "...see MapIconBar)", dated | `MapIconBar` is the only composable by that role in the file; `MapIconStack` resolves to nothing |
| 3 | `AvailabilityScreen.kt:4079-4083` (`CompassElevationStripContent` doc) | "both moved into `[MapIconStack]`" | "both moved into `[MapIconBar]`", dated | Same as #2 — this comment's *conclusion* was already right, only the symbol name was stale |
| 4 | `AvailabilityScreen.kt:1209-1214` (`ForagerBottomNav` doc) | "extended... to `[CompactTab]`'s 5" | "extended... to `[CompactTab]`'s **6**" — names Album/Workstream G2 explicitly | Direct read of `CompactTab`'s enum body (`:247-261`): 6 entries |
| 5 | `README.md:88-99` | "List / Maps / Seasonal / Journal / Settings, five destinations... icon stack... fullscreen, GPS locate-me, the topo/regular toggle, Search, and an add (+) button" | Six destinations named (adds Album); `MapIconBar` named as one panel bar; all 8 rows described (adds orientation-reset, record, return-to-vehicle) | Same two sources as #1 and #4 |
| 6 | `AvailabilityScreenMapIconStackTest.kt:215` | "Drives the real long-press gesture through `[MapStackIconButton]`'s `combinedClickable`" | "...through `[MapBarIconButton]`'s..." | `MapStackIconButton` matches no real symbol anywhere in `app/src` (checked via repo-wide grep); `MapBarIconButton` is the real one this test actually exercises |
| 7 | `AvailabilityScreenMapIconStackTest.kt:437` | "See MapIconStack's own return-to-vehicle MapStackIconButton call." | "See MapIconBar's own return-to-vehicle MapBarIconButton call." | Same as #6 |

No fix in this list required a behavior change or a rename — the test class itself
(`AvailabilityScreenMapIconStackTest`) keeps its pre-rename name, per the dispatch's explicit
scope boundary.

---

## Flagged, not resolved

- **`MapSlot.onLongPress`/`SightingsMap`'s long-press detection are vestigial production code** —
  see task 2(b) above. Out of this dispatch's scope; needs an owner call on whether to remove it or
  revive it.
- **`AvailabilityScreen.kt` and `AvailabilityScreenMapIconStackTest.kt` were touched by both PR #50
  and this session's task-3 commit, on different branches.** PR #50's changes to these files are
  now on `main`; task 3's comment fixes are on `claude/coding-session-j010b6`, cut from `main`
  *before* PR #50 merged. This branch needs to be rebased or merged against the new `main` before
  it's mergeable — not done in this session, since the dispatch didn't ask for it, but worth
  surfacing before it's forgotten (this is exactly the class of drift CLAUDE.md's own "verify your
  base branch" pitfall warns about).
