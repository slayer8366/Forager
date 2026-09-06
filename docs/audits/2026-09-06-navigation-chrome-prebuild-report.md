# Pre-build report: one heading on screen, not three

**Dispatch:** "One heading on screen, not three" (UI consolidation, owner-directed).
**Date:** 2026-09-06. **Status:** report before building — nothing built, no product code changed.
**Base:** `main` at `ed3ac52` (confirmed: `git rev-parse origin/main` = `ed3ac52015aa379f841e70057e4c188ffe41cad9`).
**Branch:** `claude/new-session-102gri`, cut from that commit, zero commits ahead when this was written.

Every claim below names a file and line on `ed3ac52`, or is marked inferred.

---

## 0. The picture on `ed3ac52`

| Surface | Where | Composed when | Reads |
|---|---|---|---|
| Compass strip (`CompassElevationStrip`) | `AvailabilityScreen.kt:3616-3630`, `TopCenter`, `padding(top = topInset)` | always, on the Map tab | heading, `liveAltitudeMeters`, `liveLocation` |
| Navigation HUD | `AvailabilityScreen.kt:3655-3668`, `TopCenter`, `padding(top = topInset + compassStripClearance)` | `if (isReturning)` | heading, `liveFix`, `navigationTarget` |
| `DistanceArm` | `AvailabilityScreen.kt:3929-3941`, inside `TrailheadControls`, inside the icon cluster | `visible = isReturning` | `returnToStart.distanceMeters` |

All three are children of the one `fillMaxSize()` Box that holds `mapSlot` (`AvailabilityScreen.kt:3218-3224`). The strip and the HUD both read the same `State<TrueHeadingReading>` from `rememberTrueHeading` (`AvailabilityScreen.kt:3208`), each in its own leaf.

---

## 1. Report-before-building answers

### 1.1 The HUD's padding once the strip hides

- Today the HUD sits at `topInset + compassStripClearance` (`AvailabilityScreen.kt:3666`) purely to clear the strip. The clearance is a one-time text measurement of `"Mg"` at `labelMedium` (`:3212-3216`), roughly 16dp, while the strip's actual height is its 18dp icon (`:4274`, the tallest child of a Row with no vertical padding). So on `ed3ac52` the HUD's top edge already overlaps the strip's bottom by about 2dp, HUD on top (composed later). Inferred from the layout code, not yet measured; harmless and about to become moot.
- **Proposed:** while navigating the strip is not composed, so the HUD pads by `topInset` alone. While not navigating there is no HUD. No state has both, so `compassStripClearance` drops out of the HUD's path entirely. The HUD continues to follow the search bar's fullscreen slide through `topInset`, as the strip does today.
- `compassStripClearance` keeps its other three consumers unchanged: the observation bubble's `minY` (`:3278`), the taxon filter chip's top padding (`:3644`), and the search dropdown's top offset (`:1857`, `:3357`). While navigating the HUD is taller than the clearance, so those elements clear less than they should, but this is **already true on `ed3ac52`**: the taxon chip is composed before the HUD at `clearance + 8dp` (`:3639-3646`) and the HUD is composed after it at `clearance`, so a chip is hidden under the HUD whenever both exist. Pre-existing, out of scope, reported here rather than fixed.

### 1.2 The map's measured height

- The map's Box is `fillMaxSize()` (`:3219`); its size comes from its constraints, not from its children. The strip is an `align(TopCenter)` child of it (`:3627`). Removing a child of a Box sized by constraints cannot change a sibling's measured size. Confirmed by reading; the test will confirm by measurement.
- The dispatch cites `AvailabilityScreenLayoutTest:373`. On `ed3ac52` the guard at that region is `fullscreen does not change the map's own measured height` (`:387`), and the stage-one extension `opening the navigation HUD does not change the map's own measured height, in or out of fullscreen` is at `:414`. **Proposed:** extend the `:414` test — it already flips `isReturning` through a `mutableStateOf`, so it can also assert the strip is present before, absent while navigating, present again after leaving, with the map height identical at all three points, in and out of fullscreen.

### 1.3 What the strip's leaf does when hidden

- **Proposed: removed from composition, not made invisible** — `if (!isNavigating) CompassElevationStrip(...)`. `CompassElevationStrip` is the leaf that reads `heading.value` (`:4185`). Out of composition it reads nothing and recomposes nothing; only the HUD's leaf reads the heading while navigating, so the sensor-rate work is done once.
- The `produceState` inside `rememberTrueHeading` keeps running in `CompactMapTab` regardless (`TrueHeading.kt:60-79`); that is the one pipeline the HUD needs, not duplicated work. An `alpha(0f)` or `AnimatedVisibility` approach would keep the strip's leaf subscribed and recomposing at 16 Hz — rejected for exactly the reason the dispatch raises.

### 1.4 The redundant exit

Three exits exist today: the HUD's close (`NavigationHud.kt:174-179`), the control pill's lit return toggle (`AvailabilityScreen.kt:3993-4005`, `onClick = onToggleReturning`), and system back (`:3103`). **Recommendation: all three stay, no code change.**

- The pill's return row is the *entry*. A toggle that lights while active and does nothing on the second tap is a worse control than one that toggles off; removing exit-from-the-pill would mean building that.
- The HUD's close is the one guaranteed reachable (the cluster can be minimised or dragged), which is why it exists; it does not make the pill's toggle redundant, it makes the exit reachable when the pill is not.
- Stage two: a picker-chosen target that is not the return leg should not light the return row — that already holds, since the row keys on `isReturning`, not on navigation in general.

This is the owner's decision; the above is a recommendation, not a change made.

### 1.5 Height on a small screen (w360dp × h640dp, the suite's own viewport)

Arithmetic from the layout code, to be confirmed by measurement once the suite is up (see §5):

| State | Stack at the top of the map | Height |
|---|---|---|
| Today, not navigating | strip | ~18dp |
| Today, navigating | strip 18dp + HUD (48dp `IconButton` + 2×4dp padding) | ~74dp (HUD overlaps strip by ~2dp → ~72dp visible) |
| Proposed, not navigating | strip | ~18dp (unchanged) |
| Proposed, navigating | HUD with a second row for elevation · coordinates | ~80dp (see below) |

Two ways to fold elevation and coordinates in:

- **(A) A second full-width row under the existing Row** — `210 m · 10T ER 25118 40235`, coordinates tappable. Row 1 stays 48dp; row 2 is `labelMedium` (16dp) plus 4dp vertical padding each side for a ~24dp tap band; outer padding 8dp. **≈ 80dp**, about 6dp taller than today's strip-plus-HUD stack. The coordinates get the full width, so both MGRS and the labelled decimal pair (~170dp wide at `labelMedium`, inferred) fit at w360 without ellipsis.
- **(B) A third line inside the distance column** — the column becomes 24 + 16 + 16 = 56dp, HUD ≈ 64dp. But the column's width is what is left after two compass columns, the close button and three 12dp gaps — roughly 140dp at w360 (inferred). The labelled decimal pair would ellipsize there. Half a coordinate is not a coordinate; this is the exact finding that removed the strip's combined line (`AvailabilityPureFunctions.kt:71-75`). **Rejected.**

Crowding, with (A): outside fullscreen at h640 the column is status bar (~24dp, device-only) + search bar (~45dp: `compassStripClearance * 2 + 3×4dp + divider`, `:1222`) + HUD 80dp + bottom nav (~80dp plus real inset) → roughly 410dp of map left, versus ~416dp today while navigating. In fullscreen only the HUD remains over the map. The arm's removal also shortens the cluster by its ~32dp. **My read: (A) does not crowd enough to hurt, and the net change against today's navigating state is ~6dp.** I am not invoking the stop clause; the owner can overrule on the measured numbers in §5.

One open sub-decision under (A): the coordinates tap band. The strip's toggle today is an 18dp-tall text `clickable` (`:4326-4332`), well under a 48dp target. Row 2 at ~24dp is better than today and keeps the HUD at ~80dp; a full 48dp band would push the HUD to ~104dp. I would build the ~24dp band unless told otherwise.

---

## 2. Report before removing: does any state show the arm without the HUD?

**No.**

- The arm's only mount is `TrailheadControls` (`:3929`, `visible = isReturning`); `TrailheadControls`' only call site is inside `CompactMapTab`'s icon cluster (`:3594`). The HUD is composed in the same Box on the same `isReturning` (`:3655`). Both conditions are the one `trackUiState.isReturning` from `MainActivity.kt:444`.
- The reverse exists: with the cluster minimised, the HUD shows without the arm. That is the direction the dispatch's rule allows.
- They do not compute the number from the same inputs, which is worth recording: the arm shows `returnToStart.distanceMeters`, computed by `TrackRecordingViewModel.returnToStart` from the track's own fix to `originWaypoint`, falling back to the first breadcrumb when there is no origin (`TrackRecordingViewModel.kt:417-419`); the HUD computes `metersBetween(liveFix, originWaypoint)` (`NavigationHud.kt:245-247`) and says "No origin waypoint for this track" when the origin is missing. Same target whenever an origin exists, so the same number to display precision, as the owner saw. The one divergence — a track with no origin, where the arm points at the first breadcrumb and the HUD refuses — is a stage-one decision, not a reason to keep the arm.

**A blocker the dispatch did not anticipate — see §4.1.** Removing the arm removes its `@Ignore`d test, whose name is in the CI skip allowlist; a stale allowlist entry fails the build. The arm cannot be removed without an allowlist edit, which the dispatch forbids.

---

## 3. Items 4 and 5: premises and decisions

### 3.1 The no-fix message (item 4)

- **Premise gap.** `rememberTrueHeading` emits `NoSensor` whenever the magnetometer reads null, *regardless of whether a fix exists* (`TrueHeading.kt:66-69`); `NeedsFix` is only sensor-present-fix-absent. So a phone with no magnetometer and no fix is `NoSensor`, and a one-message state keyed on `NeedsFix` would still show three fragments there — "Compass unavailable · Elevation unavailable · Coordinates unavailable", exactly what the existing test at `AvailabilityScreenMapIconStackTest.kt:908` asserts today. **Proposed:** key the one message on *no fix* (`location == null`), whatever the heading says; key "Compass unavailable" on `NoSensor` with a fix. Two causes, two messages, and the third combination collapses into the first cause because the fix is what is missing.
- **Wording premise, flagged not blocking.** "No fix yet" is not the same as "location services unavailable": a cold start under canopy has services on and no fix for tens of seconds. The owner chose the wording from the device; I would ship it as written and note it here.
- **The HUD's own no-fix state.** "Compass needs a fix" also lives in the HUD (`NavigationHud.kt:230`) and its status line says "Waiting for a fix" (`:237`). The dispatch replaces the string, not "keeps it alongside". **Decision needed:** what the HUD's north-compass label says with no fix. Proposal: the label shows "—" and the status line carries "Location services unavailable", so the HUD too has one message for one cause (and "Waiting for a fix" goes, as a third wording for the same thing). I have not built this; it is a stop-and-ask.

### 3.2 The needle near the target (item 5)

- **I agree the two thresholds are one number.** `isApproaching` already encodes "twice the reported accuracy" (`domain/NavigationReadout.kt:29-34`). Proposed: the same call gates the needle, so `targetArrowDegrees` is null whenever `isApproaching` is true. No second constant, no smoothing of the bearing.
- Sub-decisions the dispatch does not make, with what I would do:
  1. **The target column's text while suppressed.** "Turn 265°" is the same unstable bearing as the needle and must go with it. Proposal: the dimmed static icon the null-needle states already draw, with the text "—". "Approaching" stays on the status line where it is today.
  2. **No sensor.** The text fallback "Bearing 0° N" (`NavigationHud.kt:254`) is the same bearing; suppress it inside the threshold too.
  3. **Stale fix inside the threshold.** The status line is single and freshness wins it today ("Last fix 45 s ago", `:259-261`), so a suppressed needle would have no "Approaching" beside it — the exact drift the dispatch warns of, caused by the slot, not by two constants. Options: (a) freshness wins the status, needle still suppressed, accepted gap; (b) "Approaching · last fix 45 s ago". I lean (b). **Owner's call.**
  4. **No reported accuracy.** `isApproaching` returns false, so the needle stays drawn at any distance. Consistent with the existing rule ("no basis for the word at all"); noted, not changed.
- Test literals: accuracy 8 m → threshold 16 m. Two fixes offset north of the target by 15.9 m and 16.1 m of latitude (1 m ≈ 8.983×10⁻⁶° of latitude, computed from WGS-84's meridional radius, not from `APPROACHING_ACCURACY_MULTIPLIER`), asserting needle present at 16.1 m and absent at 15.9 m with "Approaching" shown.

---

## 4. Standing rules

### 4.1 The CI skip allowlist — a genuine conflict

`.github/workflows/ci.yml:359-371`: a skipped test not in `SKIPPED_TESTS_ALLOWLIST` fails the build, **and a listed test that is not actually skipped also fails the build** (stale entry). Two entries touch this dispatch:

| Allowlist entry (`ci.yml`) | Test | Effect of this dispatch |
|---|---|---|
| `:250` `a real touch beside the distance arm still reaches the map` | `MapIconStackTest.kt:1278`, `@Ignore`d | Removing the arm removes the test → stale entry → **CI red**. Removing the entry is an allowlist change → **forbidden by the dispatch**. |
| `:288` `tapping the coordinates segment reveals labeled decimal degrees…` | `MapIconStackTest.kt:976`, `@Ignore`d | Untouched: this tests the strip while *not* navigating, which still exists. The new HUD-side toggle test is a new test, not a change to this one. |

**Item 3 (remove the arm) therefore cannot be completed under the standing rules as written.** Deleting a test of a composable that no longer exists is not silencing a test, but the mechanism is the same allowlist edit. Options: (a) the owner authorises removing that one entry together with the test and the arm; (b) the arm stays and item 3 is dropped; (c) the arm is removed and the test is left `@Ignore`d against a tag that no longer exists, which is nonsense. **I recommend (a) and am waiting for it.** Items 1, 2, 4 and 5 do not depend on this.

### 4.2 Existing tests that must change, each with why

`AvailabilityScreenMapIconStackTest` unless stated:

| Line | Test | Why it changes |
|---|---|---|
| `:278` | `the compass strip and the HUD's north compass read the same true heading` | **Finding.** It navigates and asserts the strip *and* the HUD both read `95° E` — the three-headings bug asserted as correct. After this dispatch the strip is absent while navigating; the test becomes "heading text appears exactly once while navigating" (node count), with the one-source claim kept by asserting the HUD's value against the fake. |
| `:297` | `before any fix the strip says the compass needs a fix rather than showing magnetic` | Navigates (strip absent) and asserts a string that is being removed. Splits into: HUD no-fix state (§3.1's decision), and a separate non-navigating strip test for the one message. |
| `:908` | `…explicit unavailable state… with no sensor and no fix yet` | Asserts the three fragments for no-sensor + no-fix; becomes the one message (§3.1). A sibling test asserts no-sensor *with* a fix shows "Compass unavailable" plus elevation and coordinates. |
| `:937` | `a heading with no fix reads needs-a-fix, never the magnetic value` | String replaced; asserts "Location services unavailable" and still `assertCountEquals(0)` on `90° E`. |
| `:993` | `the coordinates segment is not tappable before a first fix arrives` | "Coordinates unavailable" is no longer rendered in the strip's no-fix state. Its claim ("no fabricated decimal pair before a fix") is re-asserted against the one-message strip: a touch on it changes nothing. |
| `:1018` | `recording with no fix yet … and no distance arm yet` | The `distance-arm` `assertDoesNotExist` passes identically before and after the arm's removal — CLAUDE.md says flag it. Drop that assertion; the contentDescription half stays. |
| `:1039` | `…return-to-vehicle active shows the full sentence via contentDescription and the distance visibly` | "1.2 km" was read off the arm. No `liveFix` in that setup, so the HUD shows "—"; becomes a HUD test with a fix, or the visible-distance half moves to the existing HUD distance test at `:287`. |
| `:1145` | `a return distance under a kilometer is shown in meters on the distance arm` | Same as above ("350 m" on the arm). |
| `:1316` | `the distance arm overlaps the pill's own bottom cap…` | Geometry of a composable that no longer exists; deleted with it. |
| `:1278` | `a real touch beside the distance arm still reaches the map` | `@Ignore`d and allowlisted — see §4.1. |
| `:1180`, `:1217` | trailhead touch tests (`@Ignore`d, allowlisted) | Still compile and still test `ControlPill`; their doc comments describe the arm and get corrected. Not un-ignored, not deleted. |
| `NavigationHudReadoutTest.kt:104` | `Compass needs a fix` literal | Follows §3.1's decision. |
| `AvailabilityScreenLayoutTest.kt:414` | HUD-open map-height guard | Extended per §1.2. |

Strip tests that do **not** navigate and are unaffected: `:584` (`@Ignore`d) and `:639` (fullscreen strip position), `:925`, `:942`. They are the "present otherwise" evidence and stay as they are.

### 4.3 Skip count

Baseline stated by the dispatch: 1106 tests, 25 skipped. Measured on `ed3ac52` in this container: **see §5** (run in progress when this file was first written; updated in place once it finishes). `@Ignore` annotations in `app/src/test`: 54 occurrences by grep, which counts the commented-out `// @Ignore:` provenance lines beside each real one — the XML report is the authority.

---

## 5. Environment and verification

- **Device verification: blocked.** `/dev/kvm` does not exist in this container; no emulator. The owner must check on hardware: the strip returns on leaving navigation (close button, pill toggle, and system back — all three exits), the folded coordinates still toggle MGRS ↔ decimal by finger, and the HUD's top edge sits under the search bar outside fullscreen and flush at the top in fullscreen.
- No Android SDK was installed in the container; `scripts/setup-android-sdk.sh` installed platform 37.1 and build-tools 37.0.0 to `/opt/android-sdk` through the proxy. Gradle 9.7.0 bootstrapped. Suite run: pending at time of writing.
- Measured heights (strip, HUD) at w360dp×h640dp: pending the same run; §1.5's numbers are arithmetic until then.

---

## 6. Decisions this dispatch does not make, and what I would do

1. **One navigation predicate.** `AvailabilityScreen` already names the concept `isNavigating` for waypoint display (`AvailabilityScreen.kt:705`, `mapVisibleWaypoints(isNavigating = isReturning, …)`). Define `val isNavigating = isReturning` once there, with a comment that stage two ORs its picker state into this line and nowhere else; pass it to `CompactMapTab`; gate **both** the HUD and the strip on it. This renames the HUD's gate from `isReturning` — one line, and the only way the strip and the HUD cannot drift apart when stage two lands.
2. **The MGRS/decimal toggle state is hoisted and shared.** `showDecimalDegrees` is `remember`ed inside the strip (`:4225`). If the HUD kept its own copy, a format chosen while navigating would revert on exit — a user-set state resetting, which CLAUDE.md's UX default forbids. Hoist it to `CompactMapTab` and pass it to both. (It already resets on a tab change today because `CompactMapTab` unmounts; pre-existing, not widened here.)
3. **The HUD reads elevation and coordinates from the `liveFix` it already has** (`altitude`, `lat`, `lng`), through the same `coordinatesStripText`; no new parameters and no second source.
4. **Splitting `CompactMapTab`.** `AvailabilityScreen.kt` is 5,198 lines; this dispatch touches four separate regions of it (the strip's mount, the HUD's mount, `TrailheadControls`, `CompassElevationStripContent`), and every one of the strip/arm tests can only reach those private composables by standing up the whole screen. More urgent than before this dispatch: yes, moderately. Not done here.

---

## Required disclosure

**Confirmed (read on `ed3ac52`):** every file/line citation above; that the arm and the HUD share one `isReturning`; that the strip is a constraint-sized Box's child; that `NoSensor` is emitted independent of fix; that the allowlist fails on stale entries; that the `:278` test asserts the duplicated heading; that `isApproaching` is the only threshold constant.

**Inferred (not measured yet):** every dp figure in §1.5; the ~2dp strip/HUD overlap; the width available to the distance column.

**Could not determine:** on-device behaviour (no KVM); whether the owner's "0 ft beside 0 ft" ever diverges under a missing-origin track in practice.

**Premises in the dispatch that were wrong or incomplete:** (1) the "no sensor" and "no fix" cases are not disjoint in the code — no-sensor-and-no-fix reads as `NoSensor` (§3.1); (2) item 3 collides with the standing rule on allowlist changes (§4.1); (3) `AvailabilityScreenLayoutTest:373` is the fullscreen guard; the HUD-open guard to extend is at `:414` (§1.2).

**Decided without cover:** nothing built. The proposals in §1, §3 and §6 are what I would do; each is waiting on the owner's answer to the questions marked as decisions.
