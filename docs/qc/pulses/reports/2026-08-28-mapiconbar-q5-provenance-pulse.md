# Pulse 2 — MapIconBar, Q5 provenance, and the layout rebuild inputs

**Date:** 2026-08-28. Read-only against `main` at `d432660`. No code changed. Every count below is
re-derived from the current tree, not repeated from `map-redesign.md`, the layout-phase draft, or
the first pulse's chat reply (which was never itself committed as a file — see the note at the end).
Cited file:line throughout, per the dispatch's own instruction.

---

## 1. MapIconBar — the touch-interception check

**What it is: `Surface`, not `Box`** — `AvailabilityScreen.kt:3855-3862`. Its full modifier chain,
read directly: the `Surface` itself carries `shape`, `color`, `contentColor`, `shadowElevation`,
`border`, and `modifier = modifier` (the caller-supplied one, see below) — **no `fillMaxWidth`,
`fillMaxSize`, or `weight` anywhere on it**. Inside it, a `Column` with only
`Modifier.padding(vertical = Spacing.xs)` (`:3863-3867`) — again no fill/weight modifier.

**Is it width-bounded?** Yes, but not via `IntrinsicSize.Max` — that pattern isn't used here at all
(the only occurrence of the string `IntrinsicSize` in this file is a comment on a *different*
composable, `CompassElevationStripContent`, explaining why it *wasn't* reused —
`AvailabilityScreen.kt:4094`). `MapIconBar` doesn't need it: since neither the `Surface` nor its
inner `Column` carries a fill/weight modifier, both size to their content's intrinsic width by
default — a `Column` with no `fillMaxWidth` sizes to its widest child, one `MapBarIconButton` at
`MIN_TOUCH_TARGET` (48dp) plus the Column's own padding. This is structurally different from the
bug pattern Understory's rules exist for (an *unconstrained* `fillMaxWidth()`/`fillMaxSize()` child
inside a `Column`/`Row` with no constraint of its own, stretching the wrapping `Surface`) — that
mechanism simply isn't present here.

**Call-site placement**, `AvailabilityScreen.kt:3702-3730`: `MapIconBar` sits inside the same
`Box(modifier.fillMaxSize())` as the map slot and the compass strip, positioned with
`Modifier.align(Alignment.CenterEnd).padding(Spacing.sm)` (`:3727-3729`) — alignment, not a stretch
modifier. **What it overlays**: only its own rendered rounded-rect area, right-edge, vertically
centered — not derivable as an exact dp figure from source alone (depends on runtime window height
for the centering), and no Robolectric test measures it (see below), so the *precise* overlaid
region is **unverified**, though the *mechanism* by which it could over-claim space (unconstrained
fill) is confirmed absent.

**Robolectric long-press coverage: none exists for this specific defect class.** Three findings,
each checked directly:
- `grep -r "Simulate long press"` and `grep -ri "onLongPress"` across `app/src/test` — **zero
  matches**. This exact fixture string (which the README describes as the historical mechanism)
  no longer exists anywhere in the test tree.
- The only `longClick`-driving test touching this bar is
  `AvailabilityScreenMapIconStackTest.kt:223` ("long-pressing the layers icon toggles night mode
  without also toggling the basemap") — this drives `MapBarIconButton`'s own `combinedClickable`
  long-press (`AvailabilityScreenMapIconStackTest.kt:215`), i.e. it tests that *button's own*
  behavior, not whether the bar intercepts touches meant for the map around/under it.
- The trip-planning and waypoint flows no longer use a long-press gesture at all to test this
  surface. `AvailabilityScreenTripPlanningFlowTest.kt:76-88`'s own doc comment states it plainly:
  "the coordinate now comes from the camera, not a gesture." Its map stub
  (`TriggerableMapSlot`, `:313-317`) exposes a plain `Button` that calls `onCameraIdle(TEST_LOCATION)`
  directly — a programmatic trigger, not a touch/gesture simulation, so it cannot detect a
  touch-interception regression even in principle.

**Net finding for §1**: the bar's *structure* looks safe against the specific defect class this repo
has shipped three times (no unconstrained fill/weight modifier anywhere in its chain), but that
safety is reasoned from source, not measured — **there is no test today that would catch a
regression here if one were introduced**, because the flows that used to exercise this via a real
touch gesture were migrated to a non-gesture (camera-pan + button) interaction model at some point
after the README's own account was written. This is a real coverage gap, independent of the layout
phase, per the dispatch's own framing.

**Scroll modifiers**: none. `grep` for `horizontalScroll`/`verticalScroll` inside `MapIconBar`'s or
`MapBarIconButton`'s bodies (`:3823-3931`, `:3942-4055`) — no matches in either.

**Hit area vs. visible bounds**: identical, not extended. `MapBarIconButton`'s tap target is a
`Box(modifier.size(MIN_TOUCH_TARGET)...)` with `combinedClickable` applied to that same `Box`
(`AvailabilityScreen.kt:3968-3977`) — the clickable region is exactly the 48dp box that's drawn,
no extra invisible padding inside or outside it.

---

## 2. MapIconBar — the inventory

All 8 rows are the same composable, `MapBarIconButton` (`AvailabilityScreen.kt:3942`), each a
`Box.size(MIN_TOUCH_TARGET)` — **48dp, identical for every row, no exceptions**. Read directly from
`MapIconBar`'s body, `AvailabilityScreen.kt:3868-3928`, in bar order:

| # | Row | Icon | Action | Size | Visual differentiation from neighbours |
|---|---|---|---|---|---|
| 1 | Fullscreen | `Icons.Filled.Fullscreen`/`FullscreenExit` | `onToggleFullscreen` | 48dp | None |
| 2 | Orientation-reset | `Icons.Filled.Navigation` | `onResetOrientation` | 48dp | None |
| 3 | GPS / locate-me | `Icons.Filled.MyLocation` | `onLocateMe` | 48dp | None |
| 4 | Map mode (basemap) | `Icons.Filled.Layers` | tap: `onOpenMapModePicker`; long-press: `onToggleNightMode` | 48dp | Only row with a secondary long-press action |
| 5 | Record start/stop | `FiberManualRecord`/`Stop` | `onToggleRecording` | 48dp | **State-differentiated**: filled circle in an error/record accent color while active (`filled = isRecording`, `:3900-3902`) — otherwise identical to row 1-3 |
| 6 | Return-to-vehicle | `Icons.Filled.Directions` | `onToggleReturning` | 48dp | **State-differentiated**: `enabled = isRecording` (dimmed to 40% alpha when nothing is recording, per `MapBarIconButton`'s own `enabled`/`alpha` handling at `:3971`); icon tint (not fill) changes to error/primary color when off-track/returning (`:3910-3914`) |
| 7 | Search | `Icons.Filled.Search` | `onOpenSearchDrawer` | 48dp | None |
| 8 | Add | `Icons.Filled.Add` | `onAdd` | 48dp | **Permanently filled**: green accent circle regardless of state (`filled = true`, `:3925-3927`) |

**Direct answer to the question this section asks**: nothing in the bar differentiates a
per-interaction control (locate-me) from a Return-phase safety control (return-to-vehicle) by size,
position, or grouping. The only differentiation that exists at all is **state-based color/fill**
(rows 5, 6, 8) — which communicates *current status*, not *consequence or frequency of use*. Row
order is a flat top-to-bottom list with even `Spacing.xs` gaps throughout
(`Arrangement.spacedBy(Spacing.xs)`, `:3865`) — no divider, no grouping, no sub-spacing between
e.g. the two safety-adjacent rows (5, 6) and the two everyday-navigation rows (1, 3). Confirms the
layout-phase doc's relocated 1S finding precisely.

---

## 3. Native compass and orientation-reset

**Confirmed in code**: `SightingsMap.kt:304`, `map.uiSettings.isCompassEnabled = false`, with the
doc comment immediately above (`:299-303`) giving the reason: hardware review found the native
compass overlapping the icon bar's own orientation-reset row, and "the SDK's own compass has no
callback this project could otherwise hook into for the icon bar's matching entry, only a tap
target of its own with fixed positioning."

**What the bar's row does or doesn't replace**: the doc comment states the *reason* for disabling
(overlap + no hook), and `MapIconBar`'s orientation-reset row calls `onResetOrientation`, which
increments `resetOrientationRequestId` (`AvailabilityScreen.kt:3709`) — wired through
`MapOverlayContent.resetOrientationRequestId` per `MapIconBar`'s own doc comment
(`:3814-3816`) to `CameraUpdateFactory.bearingTo` easing the camera back to `0.0`
(per `map-redesign.md:363-367`, itself confirmed against the doc comment's own account, not
re-read in `SightingsMap.kt` directly this pass — **flagging that specific mechanism as relayed,
not re-verified in `SightingsMap.kt` this pulse**). What I could **not** confirm either way from
source: whether the native compass's own auto-hide-when-already-north behavior (a standard
MapLibre/Mapbox convention) is replicated by the bar's row, which — being a static list entry —
has no state read anywhere in `MapIconBar`'s parameters indicating current bearing, and therefore
does not hide or gray out when the map is already oriented north. **This reads as a real, unflagged
behavior difference** (native: hidden/inert when not needed; bar row: always visible, always
tappable) — worth a line in the rebuilt 1S/2S if accurate, but I'm reporting it as reasoned from
the absence of any bearing-based conditional in the row's definition, not as directly observed
behavior.

---

## 4. Q5 provenance

**The path and branch the dispatch named don't exist verbatim** — `claude/android-foraging-m3-design`
has no such ref (`git fetch` fails outright: "couldn't find remote ref"). The actual branch is
**`claude/android-foraging-m3-design-o6bu3u`** (confirmed via `git branch -a` + fetch), which does
carry `docs/design/m3-design-direction.md` (and a paired `.artifact.html`) — confirmed via
`git ls-tree -r origin/claude/android-foraging-m3-design-o6bu3u --name-only`. This path does **not**
exist on `main` (`docs/design/` isn't in `main`'s tree at all).

**It contains six open questions, Q1-Q6, and Q5 matches exactly**, `m3-design-direction.md:509-512`
on that branch:

> **Q5 — Map↔list interaction model on compact.** Bottom sheet (map-app convention, map stays
> full-bleed) vs. the current bottom-nav tab switch (explicit, already built, already tested). Item
> 10 is the largest single item in §5 and rests entirely on this.

So Q5 is real, not a lost/uncommitted reference — it just lives on an unmerged design-study branch
under a different literal name than the pulse dispatch gave it.

**Branch status**: not merged (`git merge-base --is-ancestor` returns false), 2 commits ahead of
`main`, **80 commits behind** — stale relative to current `main`. Checked live via the GitHub API:
**no pull request, open or closed, has ever targeted or originated from this branch** — it is not
in flight anywhere, just sitting on `origin` unreferenced. This matches (and is now independently
confirmed, not relayed from) the 2026-08-26 repo-state pulse's characterization of it as one of the
owner's design test-bed branches.

**Verdict for the layout phase**: Q5 is a real, still-unanswered owner question, and it's the direct
antecedent of the layout-phase doc's own "Icon stack: recolour or rearrangement?" — Q5 is broader
(it's asking whether the *entire* compact map/list model should become a bottom sheet, which would
obsolete a large fraction of both `MapIconBar` and the current bottom nav, not just re-weight the
bar's rows). **This phase should not proceed past a bar re-weighting pass without either an answer
to Q5, or an explicit owner decision to scope Q5 out of this phase and answer it separately** — a
bottom-sheet answer would make some of this phase's work moot.

---

## 5. `map-redesign.md` — full read, for the rebuild

Read in full this pass (501 lines). Summary organized by what the layout phase needs, then the
decided-vs-provisional classification the dispatch asked for explicitly.

### What it covers

- **Scope**: this whole document's redesign (full-bleed map, bottom nav, icon stack, fullscreen
  toggle, compass/elevation strip) is **`WindowWidthClass.COMPACT`-only, by explicit decision**
  (`:51-55`) — `MEDIUM`/`EXPANDED` keep `PermanentNavigationDrawer` + `CombinedResultsPane`
  untouched. This scope boundary has held through every later revision recorded in the file.
- **`MEDIUM`'s 360dp/600dp drawer** — a **named, deferred defect**, not fixed:
  `map-redesign.md:238-246` quotes the exact figures (`PermanentNavigationDrawer` at a fixed 360dp,
  60% of the window at `MEDIUM`'s 600dp floor) and names the fix (`NavigationRail` + modal drawer,
  per `understory-design-system.md` §3S) but defers it for three stated reasons (`:252-261`), of
  which reason 3 — no medium-width device or emulator available to verify it on — is called "the
  binding one" (`:263-265`).
- **`EXPANDED`'s destination-nesting question** — **explicitly open, not answered**:
  `map-redesign.md:270-320` poses four numbered sub-questions about whether three of six
  destinations being nested in a drawer panel (vs. three as permanent peers) is deliberate or an
  artifact, and states outright "no answer is being assumed here" (`:274`).
- **Search reachability** — **directly corroborates** the layout-phase doc's own 2S claim.
  `map-redesign.md:220-222`: "search is only reachable from the Maps tab — there is no quick-search
  affordance from List/Seasonal/Journal/Settings." This is stated as a known, accepted-for-now
  trade-off with two possible fixes named and explicitly "not decided here" (`:224-226`) — so the
  layout-phase doc's "largest known placement defect" framing for this item is not an estimate, it's
  citing an already-documented, already-open finding.
- **Icon stack history**: 5 icons (original decision) → 7 (2026-08-26 stopgap, record +
  return-to-vehicle moved out of the compass strip) → 8 (`map-redesign.md:355-373`, same session,
  the individual floating circles replaced by one `MapIconBar` and the native compass disabled in
  favor of its own orientation-reset row).
- **Night-mode colour inversion** — **explicitly deferred** (`:441-480`), out of scope for the
  session that shipped the icon-shape/halo workaround, "if picked up later" language throughout.
  Not a layout item, flagged for completeness only.

### Contradicts Understory or the M3 study — dates given so the newer record wins

- **Icon stack count.** The M3 study (`docs/design/m3-design-direction.md`, unmerged branch,
  undated relative to this but the branch is 80 commits stale) doesn't address the icon stack's
  count directly, so no direct contradiction there. But `map-redesign.md`'s **own** Phase 2 section
  (below) and the current 8-row reality are in tension with anything treating "5 icons" as current —
  which includes README.md's own "How it works" section (`README.md:90-99`, unrevised since before
  the 2026-08-26 changes) and the stale KDoc found in §6 below.
- **Bottom nav destination count**: `map-redesign.md:205` (Phase 2, undated precisely but before
  Workstream G2) says "Bottom nav is now 5 destinations" — itself now stale, since Workstream G2
  added `PHOTOS`/"Album" after that line was written, making it 6. `map-redesign.md` catches this
  exact drift for `ForagerBottomNav`'s own KDoc (`:322-327`, "Stale doc comment noted while reading
  this ... `CompactTab` has six entries") but does not go back and correct its own "5 destinations"
  line at `:205` — the correction is one paragraph away from a mirror-image error in the same
  document.
- **Understory's "Correct" verdict on the bottom nav**: not a figure to compare, but per pulse 1's
  answer to decision #2, `map-redesign.md`'s own still-open `EXPANDED`-nesting question
  (`:270-320`) is evidence against treating that verdict as closed — recorded again here since this
  pulse re-read the section directly rather than relying on the first pass.

### Decided vs. stopgap/provisional — the scope-boundary line

| Decision | Verdict | Quoted language |
|---|---|---|
| Redesign scoped to `COMPACT` only | **DECIDED** | "Decision: this entire redesign ... applies to `WindowWidthClass.COMPACT` only" (`:51-53`) |
| Map full-bleed, icon stack + strip overlay it | **DECIDED** | Listed under "treat these as settled, not open" (`:113-114`) |
| Icon stack as **one consolidated bar** (vs. floating circles) | **DECIDED** | "per the project owner's own request" (`:3808-3809`, current KDoc) — the owner explicitly asked for the panel bar |
| Icon stack's **specific 8-row order/weighting** | **AMBIGUOUS, leaning PROVISIONAL** | Never itself posed as a numbered decision. The 7-row version is called "**Stopgap, not a redesign**" outright (`:347`); the 8-row version is presented as that stopgap's replacement bar shape but its *internal ordering* was inherited, not redesigned — no line anywhere says the ordering itself was chosen for a reason |
| Bottom nav replaces top tab row, original 3 destinations | **DECIDED, then SUPERSEDED** twice (5, then 6) | "supersedes decision #4's 'same three destinations'" (`:206`); 6th (`Photos`) added later, un-reconciled in this doc |
| Fullscreen hides all chrome | **DECIDED**, unchanged | Matches current `CompactMapTab` behavior read this pulse |
| Settings/Trip-Planner/Search in drawer via tune icon, "no 6th floating icon" | **DECIDED, then fully SUPERSEDED** | "Supersedes decision #6 above and part of #4" (`:182`) |
| Top strip: compass + elevation, one bar | **DECIDED**, stands | Confirmed current in `CompassElevationStripContent` |
| Elevation source: GPS altitude, nullable | **DECIDED**, stands (not re-verified this pulse) | `:167-172` |
| "Do not build: track/route recording ... the record button" | **DECIDED at the time — since silently overtaken**, never reconciled in this doc | `:173-177`; a record button exists in `MapIconBar` today via a wholly separate initiative (Forager Navigator plan), and nothing in `map-redesign.md` notes that this line no longer holds |
| `MEDIUM`'s 360dp/600dp drawer defect | **DEFERRED (WIRED-TO-FIX-LATER)** | "this stays a named deferral, not a queued step" (`:265-266`) |
| `EXPANDED` destination-nesting | **OPEN**, genuinely undecided | "recorded as a question rather than a proposal ... no answer is being assumed here" (`:272-274`) |
| Search reachable only from Maps tab | **OPEN**, accepted trade-off, not fixed | "not decided here" (`:226`) |
| Night-mode colour inversion | **DEFERRED (WIRED-TO-FIX-LATER)**, out of layout scope | "out of scope for a session already in progress" (`:461`) |

**For the layout phase's scope boundary specifically**: the DEFERRED/OPEN/AMBIGUOUS rows above are
what's in scope by the dispatch's own rule ("deferred things are in scope by default"). That's:
the `MEDIUM` drawer width, the `EXPANDED` nesting question, search-reachability, the icon bar's
internal ordering, and — new from this pulse — the never-reconciled "no record button" line and the
stale "5 destinations" self-reference.

---

## 6. Stale self-references

Swept doc comments, KDoc, and string resources across the map/nav/chrome surface
(`AvailabilityScreen.kt`, `SightingsMap.kt`, `README.md`) for a count, name, or behavior current
code contradicts.

1. **`AvailabilityScreen.kt:425-438` — the most consequential one found.** `AvailabilityScreen`'s
   own top-level parameter KDoc for `isRecording`/`onToggleRecording` states: *"Rendered inside the
   same compass/elevation/MGRS strip box, on its right-hand side, rather than as a sixth
   `[MapIconStack]` icon."* This is **flatly wrong against current source** — the record toggle
   renders in `MapIconBar` (row 5, §2 above), not the compass strip, and hasn't since the
   2026-08-26 stopgap. This is very likely the *direct source* of the original layout-phase draft's
   central wrong claim: it's the main screen composable's own public parameter documentation, the
   single most likely place anyone (or any pulse) reads first. It also links a symbol,
   `[MapIconStack]`, that no longer exists under that name.
2. **`AvailabilityScreen.kt:4079-4083`** — `CompassElevationStripContent`'s own KDoc gets the
   *conclusion* right ("both moved into `[MapIconStack]`") but the *symbol* wrong — `MapIconStack`
   was renamed to `MapIconBar` (per `map-redesign.md:357-358`, "`MapIconBar` ... replacing
   `MapIconStack`"); this bracketed reference no longer resolves to a real composable. **This
   comment directly contradicts comment #1 above, in the same file** — one correctly says the
   record control moved out of the strip, the other still claims it renders in the strip.
3. **`AvailabilityScreen.kt:592`** — a plain comment, "reachable while fullscreen — see
   `MapIconStack`)" — same stale-name issue as #2, lower stakes (not a doc-linked symbol, just
   prose).
4. **`AvailabilityScreen.kt:434`** — already covered under #1's block, but the specific clause "a
   settled owner decision this doesn't re-litigate" about the stack being "fixed at exactly five"
   is itself now wrong twice over (renamed, and 8 rows not 5).
5. **`AvailabilityScreen.kt:1210-1211`** — `ForagerBottomNav`'s own KDoc: "extended by the project
   owner from 3 destinations to `[CompactTab]`'s 5." `CompactTab` has **6** entries today
   (`:247-261`: `LIST`, `MAP`, `SEASONAL`, `JOURNAL`, `PHOTOS`, `SETTINGS`). This is the exact stale
   reference `map-redesign.md:322-327` already flagged — re-confirmed here directly against current
   source, still uncorrected.
6. **`README.md:90-99`** ("How it works") describes "a right-edge floating icon stack ... fullscreen
   ..., GPS locate-me ..., the topo/regular toggle ..., Search ..., and an add (+) button" — five
   items, no mention of orientation-reset, record, or return-to-vehicle. Stale against the current
   8-row `MapIconBar`; not previously flagged in any doc read this pulse.
7. **`AvailabilityScreenMapIconStackTest.kt:215, 437`** — a test file, not KDoc/doc-comment/string
   resource strictly, but the same class of error and worth naming since it's the file most likely
   to mislead whoever picks up the touch-interception gap from §1: both lines reference
   `MapStackIconButton`, a name that doesn't match either the pre-rename (`MapIconStack`'s
   button, if it had one) or current (`MapBarIconButton`) naming — `grep` for `MapStackIconButton`
   across `app/src` finds only these two comment occurrences, no real symbol. The test class itself
   is still named `AvailabilityScreenMapIconStackTest`, also pre-rename.

No stale count/name found in `SightingsMap.kt` itself beyond what §3 already covers.

---

## 7. RecordToggleButton

**Does not exist.** `grep -r "RecordToggleButton" app/src` — zero matches anywhere in the tree, main
or test. The 2026-08-23 M3 study's 22dp/14dp figure describes a composable that has since been
deleted outright, not resized — its functionality was absorbed into `MapBarIconButton`'s row 5
(§2 above) at 48dp, a **more than 2× target-size increase** (22dp → 48dp) that happened as a side
effect of the icon-bar consolidation, not as a deliberate accessibility fix. Worth noting for the
layout phase: whatever the M3 study's "ship it this week" XS item was scoped against no longer
exists in that form, so that item needs re-scoping against `MapBarIconButton`, not `RecordToggleButton`.

---

## Note on this pulse's own provenance

This dispatch's own text references a `PULSE.md` file ("do not repeat a figure from a document,
including from map-redesign.md or from PULSE.md") as if it were already committed. It wasn't — the
first pulse's findings exist only as a chat reply in this session, not as a file in the repo. Per
`CLAUDE.md`'s own "an audit that lives only in a session transcript is not recorded" pitfall, that
first pulse is being filed alongside this one
(`docs/qc/pulses/reports/2026-08-28-map-icon-pulse.md`) rather than left to the same fate the
project has already lost work to once.
