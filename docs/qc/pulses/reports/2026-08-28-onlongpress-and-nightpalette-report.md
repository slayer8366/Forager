# Report — onLongPress comment fix + `MapPalette.NIGHT` question

**Date:** 2026-08-28. Comment-only change for item 1, read-only investigation for item 2. No
behavior change, no deletion, no rename anywhere in this session. Applied directly to `main`
(worktree off `origin/main@bdd5b31`), same low-ceremony pattern as the six-reference sweep, since
this is a two-file comment fix, not a branch's worth of work.

---

## Item 1 — correct the vestigial long-press comments

### The citation caveat, resolved first

`docs/plans/contrast_assertions.md` (confirmed at the correct, underscored path) was read in full
before citing it anywhere. **It does not document the long-press night-mode toggle's removal.**
Its actual and only subject is the night-mode *marker icon-shape swap* (removed 2026-08-27) and the
two `MapPaletteTest` tile-contrast assertions that removal made moot (removed 2026-08-26) — a
neighboring but distinct piece of the same 2026-08-27 rework. Grepped for "long press" / "long-press"
across the whole file: zero matches. **Not cited in either fixed comment**, per the dispatch's own
instruction.

**What actually documents the long-press removal**: commit `aac8520`, "Remove night-mode marker
icon shapes; add Settings' Night Maps checkbox." Its own message states directly: *"Replaced the
map's civil-twilight-automatic/long-press-hold night toggle (MapNightMode) with a single persistent
Settings checkbox... MapIconBar's long-press-to-toggle-night-mode control is removed along with
`combinedClickable`, since night mode is no longer toggled from the map itself."* This is cited by
SHA in the fixed comments below, not by a doc path, since no filed doc records it.

**Important scope note, found while verifying**: `aac8520`'s removal was of the *night-mode toggle*
long-press (a `MapIconBar` row that used to long-press to hold night mode on). That is a different
mechanism from `MapSlot.onLongPress`/`SightingsMap`'s long-press *listener*, which this dispatch
targets — the trip-planning/log-a-find gesture. The two are unrelated code paths that happen to
share the word "long-press" and were retired around the same time for different reasons. Kept
distinct in the fix below rather than conflated.

### Before/after

**`app/src/main/java/com/forager/app/ui/map/MapSlot.kt`** (`MapSlot` typealias's own doc comment,
the `[onLongPress]` sentence within the multi-parameter paragraph):

> Before: *"[onLongPress] is how the map reports a trip-planning gesture back up without knowing
> anything about dates or persistence — the screen owns the date picker and the save call, the map
> only reports where the finger was."*

> After: *"[onLongPress] is how the map* would *report a trip-planning gesture back up, when
> wired — the caller turns the reported point into a plan/log action without this composable
> knowing anything about dates or persistence.* **No production call site wires it as of
> 2026-08-28** *— both `AvailabilityScreen.kt` call sites (`MapTab`, `CompactMapTab`) pass `{}`.
> Kept deliberately, not left by accident (see [SightingsMap]'s own doc comment on the same
> parameter for the fuller reasoning); the interaction it used to drive now goes through panning
> the camera, tapping add (+), and confirming via `CentrePinLocationPickerOverlay` instead."*

Established by: direct read of both `mapSlot(...)` call sites in `AvailabilityScreen.kt` (`:3368-3382`
for `MapTab`, `:3719+` for `CompactMapTab` — both pass `{}` as the 5th positional argument,
`onLongPress`) and `CentrePinLocationPickerOverlay`'s existence (`ui/map/CentrePinLocationPicker.kt:121`).

**`app/src/main/java/com/forager/app/ui/map/SightingsMap.kt`** (the `[onLongPress]` parameter doc):

> Before: *"[onLongPress] fires with the geographic point under a long-press, for the caller to
> turn into a planned trip (via a date picker it owns — this composable knows nothing about dates
> or persistence, only where the gesture happened). Wired through
> `MapLibreMap.addOnMapLongClickListener` rather than a gesture detector on the [AndroidView]
> itself, for the same reason the previous osmdroid implementation used `MapEventsOverlay`:
> MapLibre's own touch handling already owns pan/zoom on this `MapView`, and a second, independent
> gesture detector on top would race it for the same touch stream."*

> After: same first two sentences (mechanism description, still accurate, kept), plus: *"**No
> production call site consumes this as of 2026-08-28.** Both `mapSlot(...)` call sites in
> `AvailabilityScreen.kt` (`MapTab`, `CompactMapTab`) pass `{}` for [onLongPress] — the
> trip-planning/log-a-find interaction this parameter used to drive now goes through panning the
> camera, tapping the add (+) button, and confirming via
> [com.forager.app.ui.map.CentrePinLocationPickerOverlay] instead. Kept wired here deliberately,
> not left by accident: the listener costs nothing while dormant, and whether to remove it or give
> it a new consumer is a product decision this comment fix doesn't make."*

Established by: the same call-site read as above, plus direct read of the listener registration
itself (`SightingsMap.kt`, `currentOnLongPress` / `rememberUpdatedState(onLongPress)` and the
`addOnMapLongClickListener` callback invoking `currentOnLongPress(LatLng(...))`) — confirming the
detection code is still live and wired, just discarded at every call site.

**Verified**: `./gradlew testDebugUnitTest` against this exact two-file diff — real numbers below.

### Found, not fixed — out of this item's scope

`SightingsMap.kt`'s `nightMode: Boolean` parameter carries its own stale doc comment, unrelated to
`onLongPress`: it says night mode "Still drives the map's own twilight trigger and long-press
override" — describing `MapNightMode`'s civil-twilight-automatic/long-press-hold system, which
`aac8520` deleted outright (`MapNightMode.kt` and its test are gone; night mode is now driven
solely by the `nightModeMaps` Settings checkbox — see item 2 below). This is the same class of
error as the seven-reference sweep, on the same file, discovered while verifying this item — but a
different parameter than the dispatch named, so flagged here rather than fixed silently.

---

## Item 2 — is `MapPalette.NIGHT` still live?

**Short answer: yes, it exists; no, nothing in production reads it; and the specific 0.1007
separation figure the planning docs cite describes a since-superseded, two-generations-old design
pass — the redo does need a different starting point than assumed.**

### Does `MapPalette.NIGHT` still exist? Is anything reading it?

**Exists.** `NIGHT`, `NIGHT_WARM`, `NIGHT_INK`, `NIGHT_TILE_REFERENCE`, and `forMode` are all still
declared in `ui/theme/MapPalette.kt`. **The file's own class doc comment already states current
reality accurately** (a "## Markers stay day-only, always (current)" section, dated and explicit —
this file does not need any fix from this session): *"`SightingsMap.kt` reads `[DAY]`
unconditionally now, regardless of `nightMode`... `[NIGHT]`/`[forMode]` still exist and are still
exercised directly by `MapPaletteTest` — only `SightingsMap.kt`'s own call site was changed, not
this type."*

**Nothing in production reads `MapPalette.NIGHT`.** A repo-wide grep for `MapPalette.` outside the
file itself finds exactly two production references: `BasemapStyles.kt` (doc-comment mentions only,
no code read) and `SightingsMap.kt:194-197`, which sets `val mapPalette = MapPalette.DAY` —
unconditionally, with its own comment confirming the same thing: *"Always MapPalette.DAY,
deliberately independent of nightMode... `MapPalette.NIGHT`/`forMode` still exist and are still
tested (`MapPaletteTest`), just not read here any more."* `NIGHT`/`forMode` are exercised only by
`MapPaletteTest` itself.

### Which `MapPaletteTest` assertions still run, which were removed?

Seven `@Test` functions currently exist in `MapPaletteTest.kt`, read directly:

| Test | Subject | Status |
|---|---|---|
| `every day marker clears its contrast ratchet against the day tile reference` | `DAY` only | active |
| `every NIGHT field is NIGHT_WARM or NIGHT_INK, nothing else` | `NIGHT`'s two-colour shape | active |
| `marks drawn on other marks are legible against those marks` | mark-on-mark contrast | active |
| `no two day markers a user must distinguish are too similar` | `DAY` pairwise separation | active |
| `NIGHT_WARM is lighter and NIGHT_INK is darker than every day counterpart` | `NIGHT` vs `DAY` lightness | active |
| `forMode selects the palettes, and the two are not the same object` | `forMode` identity | active |
| `the contrast and distance calculations are correct` | the math helpers themselves | active |

**Removed** (not `@Ignore`d — this repo's CI fails the whole build on any skipped test, so removal
with the code preserved as a comment was the only option, per the test file's own note at
`MapPaletteTest.kt:119-124`): the two tests checking `NIGHT_WARM`'s contrast against
`NIGHT_TILE_REFERENCE` (a `≥4.0:1` floor, and a ratchet against day's weakest-marker bar). Both
would fail today for an understood reason, not a defect: night's basemap dimming was removed
2026-08-26, so `NIGHT_TILE_REFERENCE` now equals the light `DAY_TILE_REFERENCE`, and `NIGHT_WARM`
was never retuned for a light ground (measured ≈1.26:1 at removal time, per
`contrast_assertions.md:97-99`).

### Does the separation ratchet still exist as an assertion, and at what value?

**For `NIGHT`: no — explicitly disclaimed, not merely absent.** `MapPaletteTest.kt:63-68`'s own
doc comment on `minimumMarkerSeparation` (`= 0.099`) states: *"Applies to `[MapPalette.DAY]`
only — see the class doc comment for why `[MapPalette.NIGHT]` no longer makes a colour-separation
claim at all."* This is a deliberate design consequence of the "fifth pass" collapsing `NIGHT` to
exactly two shared colors (`NIGHT_WARM`/`NIGHT_INK`) — a two-colour palette has no multi-marker
separation question left to assert, by construction.

**The 0.1007 figure the planning docs cite is real, but describes an earlier, superseded
iteration.** Found via `git log -S"0.1007"`: it appears in `docs/plans/understory-design-system.md:876`,
introduced by commit `55bb316` ("Map night mode: warm-shift and dim the marker palette",
2026-08-26) — describing the `plannedTrip`/`breadcrumb` pair's separation in the
*warm-shifted-and-dimmed* revision of `NIGHT`, itself one pass before the "fifth pass" that
collapsed `NIGHT` to two colours and retired the separation claim entirely, which was itself before
`aac8520` stopped reading `NIGHT` in production at all. **The design docs describing the basemap
tile inversion are two design generations behind the code they're planning against** — confirming
exactly the risk the scratchpad raised. A redo needs to start from "`NIGHT` is a dormant two-colour
scaffold with no separation claim, unread by production," not from the warm-shifted nine-colour
palette 0.1007 was measured against.

### Is night mode now purely a Settings checkbox, with no other entry point?

**Confirmed, exactly.** `grep` for `nightModeMaps`/`setNightModeMaps`/`onToggleNightMode` across
`app/src/main` finds exactly one write path: `NightModeMapsSection(checked = nightModeMaps,
onCheckedChange = onNightModeMapsChanged)` in Settings (`AvailabilityScreen.kt:1751`), backed by
`AvailabilityUiState.nightModeMaps` / `MapPreferencesRepository.getNightModeMaps`/`setNightModeMaps`
(DataStore). No long-press, no civil-twilight automatic trigger, no other call site — all deleted
by `aac8520` along with `MapNightMode.kt` and its test.

---

## Also open, from the merge report — checked this session

- **`performTouchInput` count across the whole suite: confirmed zero.** `grep -rn
  "performTouchInput" app/src/test` — no matches anywhere, not just in the one file the earlier
  pulse checked. The long-press-toggles-night-mode test (the only prior user of this API) was
  deleted by `aac8520` along with the feature it tested. **No test anywhere in this codebase
  currently routes a touch through screen coordinates.** This sharpens, not just confirms, the
  earlier merge report's conclusion: the touch-routing test isn't just missing coverage for one
  regression class, it's the *only* form that coverage could take today, and there are currently
  zero instances of it.
- **Touch-routing test itself**: not built this session, per both this dispatch's comment-only
  scope and the prior report's own note that it's a layout-phase prerequisite, not a
  drop-in-anywhere fix.
- **Frequency column**: still an owner judgment call, untouched here.

---

## Verification

`./gradlew testDebugUnitTest` against this session's exact two-file diff (`MapSlot.kt`,
`SightingsMap.kt`), in an isolated worktree off `origin/main@bdd5b31`. `BUILD SUCCESSFUL in 1m
41s`. JUnit XML parsed the same way `ci.yml`'s own summary step does:

```
suites=108
{'tests': 742, 'failures': 0, 'errors': 0, 'skipped': 0}
```

Identical to `main`'s own count immediately before this fix — comment-only, confirmed harmless.
Pushed directly to `main` as **`4e72f06`**.
