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

- Today the HUD sits at `topInset + compassStripClearance` (`AvailabilityScreen.kt:3666`) purely to clear the strip. The clearance is a one-time text measurement of `"Mg"` at `labelMedium` (`:3212-3216`). **Measured** (Robolectric, w360dp×h640dp, xhdpi, a throwaway test not committed): the clearance is 18dp and the strip is 18dp tall (its icon, `:4274`), so the HUD's top edge (67dp) sits exactly on the strip's bottom edge (67dp) with no overlap. An earlier draft of this report inferred a ~2dp overlap from the code; the measurement says otherwise.
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

Measured on `ed3ac52` (Robolectric, w360dp×h640dp, xhdpi; the throwaway test was run and discarded, not committed) for the states that exist; arithmetic for the proposed one:

| State | Stack at the top of the map | Height |
|---|---|---|
| Search bar (outside fullscreen, both states) | `SearchEntryBar` | **49dp measured** (top 0 → 49) |
| Today, not navigating | strip | **18dp measured** (49 → 67) |
| Today, navigating | strip 18dp + HUD (48dp interactive-size `IconButton` + 2×4dp padding) | **74dp measured** (strip 49 → 67, HUD 67 → 123, HUD 56dp) |
| Proposed, not navigating | strip | 18dp (unchanged) |
| Proposed, navigating | HUD with a second row for elevation · coordinates | ~80dp, arithmetic (see below) |

Also measured, for the arm's removal: `ControlPill` 108dp tall, `DistanceArm` 54dp of layout height of which 24dp hides under the pill's cap, so the cluster shortens by ~30dp when the arm goes. Text *widths* under Robolectric are not trustworthy (the HUD's distance text measured 3.5dp wide, a font-rendering artefact of the harness), so every width figure below stays inferred.

Two ways to fold elevation and coordinates in:

- **(A) A second full-width row under the existing Row** — `210 m · 10T ER 25118 40235`, coordinates tappable. Row 1 stays 48dp; row 2 is `labelMedium` (16dp) plus 4dp vertical padding each side for a ~24dp tap band; outer padding 8dp. **≈ 80dp**, about 6dp taller than today's strip-plus-HUD stack. The coordinates get the full width, so both MGRS and the labelled decimal pair (~170dp wide at `labelMedium`, inferred) fit at w360 without ellipsis.
- **(B) A third line inside the distance column** — the column becomes 24 + 16 + 16 = 56dp, HUD ≈ 64dp. But the column's width is what is left after two compass columns, the close button and three 12dp gaps — roughly 140dp at w360 (inferred). The labelled decimal pair would ellipsize there. Half a coordinate is not a coordinate; this is the exact finding that removed the strip's combined line (`AvailabilityPureFunctions.kt:71-75`). **Rejected.**

Crowding, with (A): outside fullscreen at h640 the column is status bar (~24dp, device-only) + search bar (49dp measured, `:1222`) + HUD ~80dp + bottom nav (~80dp plus real inset, device-only) → roughly 405dp of map left, versus ~411dp today while navigating. In fullscreen only the HUD remains over the map. The arm's removal also shortens the cluster by its ~32dp. **My read: (A) does not crowd enough to hurt, and the net change against today's navigating state is ~6dp.** I am not invoking the stop clause; the owner can overrule on the measured numbers in §5.

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

Baseline stated by the dispatch: 1106 tests, 25 skipped. **Measured on `ed3ac52` in this container: 1106 tests, 0 failures, 0 errors, 25 skipped** (`./gradlew :app:testDebugUnitTest --continue`, 5m 18s, summed from the JUnit XML under `app/build/test-results/testDebugUnitTest`). Matches the dispatch exactly. Nothing was adjusted.

---

## 5. Environment and verification

- **Device verification: blocked.** `/dev/kvm` does not exist in this container; no emulator. The owner must check on hardware: the strip returns on leaving navigation (close button, pill toggle, and system back — all three exits), the folded coordinates still toggle MGRS ↔ decimal by finger, and the HUD's top edge sits under the search bar outside fullscreen and flush at the top in fullscreen.
- No Android SDK was installed in the container; `scripts/setup-android-sdk.sh` installed platform 37.1 and build-tools 37.0.0 to `/opt/android-sdk` through the proxy. Gradle 9.7.0 bootstrapped. Full suite: green, counts in §4.3.
- Heights measured by a throwaway `@Test` appended to `AvailabilityScreenMapIconStackTest` (its `setNavigatingScreen()` fixture, `getUnclippedBoundsInRoot()` on the strip, HUD, search bar, pill and arm), run alone, then the file restored with `git checkout` — the working tree carried no change from it. Numbers in §1.1 and §1.5.

---

## 6. Decisions this dispatch does not make, and what I would do

1. **One navigation predicate.** `AvailabilityScreen` already names the concept `isNavigating` for waypoint display (`AvailabilityScreen.kt:705`, `mapVisibleWaypoints(isNavigating = isReturning, …)`). Define `val isNavigating = isReturning` once there, with a comment that stage two ORs its picker state into this line and nowhere else; pass it to `CompactMapTab`; gate **both** the HUD and the strip on it. This renames the HUD's gate from `isReturning` — one line, and the only way the strip and the HUD cannot drift apart when stage two lands.
2. **The MGRS/decimal toggle state is hoisted and shared.** `showDecimalDegrees` is `remember`ed inside the strip (`:4225`). If the HUD kept its own copy, a format chosen while navigating would revert on exit — a user-set state resetting, which CLAUDE.md's UX default forbids. Hoist it to `CompactMapTab` and pass it to both. (It already resets on a tab change today because `CompactMapTab` unmounts; pre-existing, not widened here.)
3. **The HUD reads elevation and coordinates from the `liveFix` it already has** (`altitude`, `lat`, `lng`), through the same `coordinatesStripText`; no new parameters and no second source.
4. **Splitting `CompactMapTab`.** `AvailabilityScreen.kt` is 5,198 lines; this dispatch touches four separate regions of it (the strip's mount, the HUD's mount, `TrailheadControls`, `CompassElevationStripContent`), and every one of the strip/arm tests can only reach those private composables by standing up the whole screen. More urgent than before this dispatch: yes, moderately. Not done here.

---

## Required disclosure

**Confirmed (read on `ed3ac52`):** every file/line citation above; that the arm and the HUD share one `isReturning`; that the strip is a constraint-sized Box's child; that `NoSensor` is emitted independent of fix; that the allowlist fails on stale entries; that the `:278` test asserts the duplicated heading; that `isApproaching` is the only threshold constant.

**Confirmed by measurement (§1.5):** search bar 49dp, strip 18dp, clearance 18dp, HUD 56dp, pill 108dp, arm 54dp layout / ~30dp visible; suite 1106 / 25 skipped / 0 failures.

**Inferred (not measured):** the ~80dp height of the folded HUD (it does not exist yet); every text *width* figure, since Robolectric's text widths are not usable; the bottom nav's on-device height.

**Could not determine:** on-device behaviour (no KVM); whether the owner's "0 ft beside 0 ft" ever diverges under a missing-origin track in practice.

**A premise of my own that was wrong:** the first draft of this report inferred a ~2dp HUD-over-strip overlap; measurement shows the clearance equals the strip's height exactly (§1.1).

**Premises in the dispatch that were wrong or incomplete:** (1) the "no sensor" and "no fix" cases are not disjoint in the code — no-sensor-and-no-fix reads as `NoSensor` (§3.1); (2) item 3 collides with the standing rule on allowlist changes (§4.1); (3) `AvailabilityScreenLayoutTest:373` is the fullscreen guard; the HUD-open guard to extend is at `:414` (§1.2).

**Decided without cover:** nothing built. The proposals in §1, §3 and §6 are what I would do; each is waiting on the owner's answer to the questions marked as decisions.

---
---

# Completion report (same day, after the owner's answers)

**Owner's decisions, verbatim in effect:** item 3 authorised (arm, its test, exactly one allowlist entry); item 4 keyed on the missing fix, dash + "Location services unavailable" for the HUD; item 5 gated on `isApproaching`, "Approaching · last fix N s ago", the target column shows the distance alone while suppressed, no-sensor bearing text suppressed too; layout confirmed at ~80dp with a ~24dp tap band; all three exits stay.

**Commits on `claude/new-session-102gri`** (each pushed before the next was started):

| Commit | What |
|---|---|
| `9b7edad`, `dabd8b7` | this report, pre-build |
| `6ded66e` | product change: strip gate, HUD second row, hoisted toggle, no-fix message, needle gate, arm removed, one allowlist entry removed |
| `917d298` | tests |
| (this commit) | completion report |

## What was built, by dispatch item

1. **Strip hides while any navigation mode is active.** `val isNavigating = isReturning` is defined once in `AvailabilityScreen` (the name the waypoint filter already used) with a comment that stage two ORs into that line and nowhere else; it is passed to `CompactMapTab` and gates the HUD's presence and the strip's absence together. The strip is `if (!isNavigating)` — out of composition, not invisible, so only the HUD's leaf reads the heading at sensor rate. The HUD's top padding is `topInset` alone.
2. **Elevation and coordinates fold into the HUD** as a second full-width row: the fix's altitude, "·", and `coordinatesStripText` of the fix. The coordinates segment's tap band is the row's remaining width by 24dp (text plus 4dp above and below). `showDecimalDegrees` is hoisted to `CompactMapTab` and shared by the strip and the HUD. With no fix the row is not drawn.
3. **`DistanceArm` removed**, with its two constants, its two tests, and the one allowlist entry at `ci.yml` (the comment there now says twelve and records why). `TrailheadControls` keeps `ControlPill` only; the return row's `contentDescription` sentence is unchanged, so the TalkBack path is intact. Five now-unused imports removed.
4. **One no-fix message.** Strip: `location == null` → the single `NO_FIX_MESSAGE` text, centred, not tappable, whatever the heading says; `NoSensor` with a fix → "Compass unavailable · elevation · coordinates"; `NeedsFix` with a fix (the transient frame between a fix landing and the next sensor emission) → a dash. HUD: heading label "—", status line `NO_FIX_MESSAGE`. "Compass needs a fix" and "Waiting for a fix" no longer exist in `app/src/main`.
5. **Needle suppressed inside the threshold.** `navigationReadout` computes `approaching = freshness != LOST && isApproaching(...)` once and uses it for the needle, the target column's text, and the status word. The bearing is not smoothed.

## Tests

**Suite:** **1114 tests, 0 failures, 0 errors, 24 skipped** on the final tree (`./gradlew :app:testDebugUnitTest --continue`, summed from the JUnit XML), and the skipped set is exactly the CI allowlist — checked by parsing `ci.yml`'s `SKIPPED_TESTS_ALLOWLIST` against the report: no unallowed skip, no stale entry. The baseline was 1106 / 25 skipped; the skip count fell by one because the arm's `@Ignore`d test was removed with the arm (owner-authorised), not silenced. No other skip changed.

**New tests, each run with the behaviour reverted** (a one-line `sed` on the product code, the affected classes run, the file restored with `git checkout`), failing for the predicted reason:

| Reverted behaviour | Tests that went red, and how |
|---|---|
| Strip composed while navigating (`if (true)`) | `while navigating the compass strip is absent and the true heading appears exactly once` (strip node found); `leaving navigation brings the compass strip back…` (strip found before leaving); `with no fix the HUD shows one message…` (**two** "Location services unavailable" nodes — the strip's and the HUD's); `while navigating the HUD's second row shows…` (two MGRS nodes); `entering and leaving navigation does not change the map's own measured height` ×3 subclasses (strip found while navigating). 7 failures. |
| HUD coordinates segment not clickable | `the coordinates toggle is reachable by real touches across its bounds…` (touch 0 did not flip); `a coordinate format chosen in the HUD is the format the strip shows after leaving navigation`. 2 failures. |
| Toggle state not shared (strip passed a constant `false`) | `a coordinate format chosen in the HUD is the format the strip shows after leaving navigation` only — the strip came back on MGRS. 1 failure. (A first attempt at this revert also hit the HUD's call site and failed the touch test too; redone against the strip's line alone.) |
| Strip's no-fix branch disabled (three fragments again) | `with no fix the compass strip shows one message…`; `a heading with no fix reads the one no-fix message…`; `the strip's no-fix message is not a coordinates toggle…` (no `compass-strip-no-fix` node). 3 failures. |
| Needle gate removed (`&& !approaching` dropped) | `just inside the threshold the needle is absent… just outside, present` ("no needle at 15.57 m with 8 m accuracy expected null, but was 315.0"); `approaching inside twice the reported accuracy…`; `stale and approaching…`. 3 failures. |
| HUD no-fix status back to "Waiting for a fix" | `with no fix the HUD shows one message…` (screen); `no fix at all - one message on the status line…` (readout). 2 failures. |

**Boundary literals** (`NavigationHudReadoutTest`): accuracy 8 m; targets 0.000140° and 0.000148° of latitude north of the fix, which on `GeoDistance`'s own radius (6 371 008.8 m → 111 195.08 m per degree) are 15.57 m and 16.46 m. Neither number touches `APPROACHING_ACCURACY_MULTIPLIER`. Needle absent and "Approaching" shown at 15.57 m; needle present, status blank at 16.46 m.

**Existing tests changed** (all in `AvailabilityScreenMapIconStackTest` unless stated), each for the reason given in §4.2 of the pre-build report:

- `the compass strip and the HUD's north compass read the same true heading` → rewritten as `while navigating the compass strip is absent and the true heading appears exactly once`. **A test can encode a defect as a requirement, and this one did**: its stage-one form asserted the strip and the HUD both reading "95° E" while navigating, which is the "heading three times" bug the owner saw on device, pinned as correct. It stayed green through all of stage one because it never questioned that both should be there. The one-source claim survives as the HUD's value against the fake heading; the new claim is a node count of one.
- `before any fix the strip says the compass needs a fix…` → `with no fix the HUD shows one message, a dash for the heading, and no elevation or coordinates row`.
- `the HUD is not composed while not returning` → also asserts the strip is.
- `…explicit unavailable state… with no sensor and no fix yet` (asserted the three fragments) → `with no fix the compass strip shows one message, not three fragments - even with no sensor`, plus the new sibling `with a fix but no sensor the compass strip says the compass is unavailable and still shows elevation and coordinates`.
- `a heading with no fix reads needs-a-fix…` → `…reads the one no-fix message…`.
- `the coordinates segment is not tappable before a first fix arrives` → `the strip's no-fix message is not a coordinates toggle - tapping it reveals nothing`.
- `recording with no fix yet … and no distance arm yet` → arm assertion dropped (it would have passed identically after the removal); renamed.
- `…shows the full sentence via contentDescription and the distance visibly` → visible-distance half dropped (it read the arm); renamed. The visible distance is the HUD's, covered by `the HUD shows the straight-line distance…`.
- `a return distance under a kilometer is shown in meters on the distance arm` → `…in the return row's sentence`.
- `an off-track fix tints the return-to-vehicle button…` → **not in the pre-build list**; it read "500 m" off the arm as visible text and failed on the first run after the removal. Now asserts the return row's own sentence. Reported here as the one test the pre-build audit missed.
- `a real touch beside the distance arm still reaches the map` (`@Ignore`d, allowlisted) and `the distance arm overlaps the pill's own bottom cap…` → deleted with the arm; a comment marks the spot. **Caught and corrected before this commit:** my first deletion span started one doc comment too early and also removed `a real touch in the gap above the control pill still reaches the map` (`@Ignore`d, allowlisted, no arm dependence). The full-suite skip count came back at 23 instead of 24, the allowlist comparison named the missing test, and it was restored verbatim from `ed3ac52`. The allowlist gate exists for exactly this; it worked.
- The two `@Ignore`d trailhead touch tests: doc comments corrected, bodies and `@Ignore` untouched.
- `NavigationHudReadoutTest`: the NeedsFix and no-fix literals; approaching now also asserts no needle and the target column's text.
- `AvailabilityScreenLayoutTest` (three subclasses): the HUD-open height guard renamed `entering and leaving navigation does not change the map's own measured height, in or out of fullscreen`; asserts the strip present → absent → absent (fullscreen) → present, map height 640dp at every step.

**Fixture additions:** `setScreen(returning: State<Boolean>?)` and `setNavigatingScreen(fix, returning)` in the icon-stack test, so one composition can enter and leave navigation and use the Portland fix whose MGRS `MgrsConverterTest` already pins.

## Device verification

Blocked: no `/dev/kvm` in this container, so no emulator. The owner must check on hardware:

1. The strip returns on leaving navigation by each of the three exits (HUD close, pill toggle, system back).
2. The HUD's coordinates toggle flips MGRS ↔ decimal by finger, including in fullscreen with the cluster minimised, and the strip shows the chosen format afterwards.
3. The HUD's top edge sits directly under the search bar outside fullscreen and flush at the top in fullscreen (`topInset` is the search bar's animated height; real system-bar insets are Robolectric-invisible per CLAUDE.md's pitfall, so the fullscreen top edge is device-only).
4. Near the origin: the needle disappears and "Approaching" appears together, at the same step, and the target column reads the distance.
5. With location off: the strip reads one line, "Location services unavailable".

## Required disclosure

**Confirmed vs. inferred.** Confirmed by running: the suite count above; every revert check's failure and its message; the map's measured height at 640dp through enter/leave/fullscreen. Inferred, still: the folded HUD's ~80dp (Robolectric text widths are unusable for the second row and I did not re-measure heights after the build — the second row is 16dp text + 8dp padding by construction, so 80dp follows arithmetically, not by measurement); on-device touch behaviour of the 24dp band.

**Could not determine.** Anything device-only, listed above. Whether "Location services unavailable" is the right wording for a cold-start-no-fix-yet state (premise flagged pre-build; owner chose it).

**Premises in this dispatch that were wrong.** Unchanged from the pre-build report: no-sensor and no-fix are not disjoint; item 3 needed an allowlist edit; the layout guard to extend was at `:414`, not `:373`. One premise of my own: the pre-build list of tests to change missed the off-track tint test.

**Decided without cover.** (1) The HUD's second row is omitted entirely with no fix, rather than showing "Elevation unavailable · Coordinates unavailable" under the one message — the dispatch's "one statement" rule applied to the HUD, not stated for it. (2) `NeedsFix` *with* a fix (the transient frame) shows a dash in the strip, matching the HUD. (3) `coordinatesStripText`'s "Coordinates unavailable" branch is now unreachable from production (both callers pass a non-null location); left in place, since the pure function's contract still admits `null`. (4) `TrailheadControls` kept as a Column around the pill rather than inlined, as the named slot stage two's entry is expected to land in. (5) The `@Ignore`d return-tap test's provenance comment still mentions the arm extending and retracting on device; that is a record of what happened on that date and was left as written.

---
---

# Amendment pre-build report: the duplicate distance, and back

**Applies to:** `claude/new-session-102gri` at `04e22e2` (PR #67). **Status:** report before building — nothing changed yet.

## Fix 1 — which duplicate it is

**The target column is duplicating the distance.** By construction, not by accident: `navigationReadout` sets `targetText = distanceText` whenever `approaching` is true (`NavigationHud.kt`, the `when` under "The one threshold"). The north compass's own label is untouched — it still reads the heading (`52° NE`) or a dash.

How it got there: the completion report's §"Decided without cover" does not list it because I did not treat it as a decision. The owner's reply to my pre-build question "what the target column reads while suppressed" was "the target column shows the distance alone while suppressed, no dash and no placeholder", and I read "the distance" literally as the column's content. The amendment says the decision was that the column shows *nothing*; the owner's "the distance alone" meant the HUD as a whole shows one distance. My reading produced `9 ft · 9 ft · Approaching`. The `NavigationHudReadoutTest` cases I wrote assert the wrong reading (`assertEquals("33 ft", r.targetText)` in three tests) — they pinned my misreading, which is exactly what the amendment's "assert the node count" verification would have caught on screen and the pure tests could not.

**Fix, once cleared:** `approaching -> ""` for `targetText`; the dimmed static icon stays as the column's only content. Screen test: with a fix inside the threshold, `onAllNodesWithText("<distance>")` counts exactly one. Readout tests: the three `targetText` literals become `""`.

## Fix 2 — the back chain on this surface today, back-to-front

Compose's `OnBackPressedDispatcher` invokes the **most recently registered enabled** callback; `BackHandler` registers in composition order, so the deepest composable wins. On `04e22e2`, from the innermost out:

| # | Handler | Where | Enabled when | Does |
|---|---|---|---|---|
| 1 | `CompactMapTab`'s own | `AvailabilityScreen.kt:3114` | `pendingAction != null \|\| pickingSearchLocation \|\| showActionMenu \|\| isReturning` | pops the picker, then the search-location pick, then the action menu, **else `onToggleReturning()` — exits navigation directly** |
| 2 | search dropdown | `:1259`, inside the `compactMainScaffold` lambda (`:1190`), *before* its `Scaffold` (`:1446`) whose content composes `CompactMapTab` (`:1568`) | `showSearchDropdown` | closes the dropdown |
| 3 | drawer | `:840` | `isDrawerOpen` | closes the drawer |
| 4 | fullscreen | `:843` | `!isDrawerOpen && isMapFullscreen` | exits fullscreen |
| 5 | tab | `:847` | `!isDrawerOpen && !isMapFullscreen && compactTab != MAP` | returns to the Maps tab |
| 6 | **go-home / exit** | `:859` | `!isDrawerOpen && !isMapFullscreen && compactTab == MAP` | first press: toast "Tap Back Button Again to Exit"; second within 2 s (`DOUBLE_BACK_EXIT_WINDOW_MS`, `:396`): `Activity.finish()` |

Handlers 3–6 are written so at most one is enabled at a time (each excludes the more-nested states). Handlers 1 and 2 are not part of that exclusion scheme — they rely on registration depth. Dialogs on this surface (`ThreeWayActionDialog`, `TripDatePickerDialog`, `WaypointNameDialog`) are real `Dialog` windows with their own dispatcher; system back reaches them first and never enters this chain. Nothing above `AvailabilityScreen` consumes back: `MainActivity` declares no handler and the manifest sets no `enableOnBackInvokedCallback`.

**Finding — the premise "navigation sits after overlays" is not what the code does.** Because handler 1 is the deepest and its `isReturning` clause has no exclusions, **while navigating, back exits navigation before anything else**: with the search dropdown open (2), the drawer open (3), or fullscreen on (4), the first back press ends the return leg and leaves the dropdown/drawer/fullscreen exactly as they were. That is stage one's coupling, not the amendment's target, but it is the opposite of the owner's order ("navigation is the last thing backed out of"), and it is where the accidental exit is most likely: a stray back while fullscreen, the state a navigating user is most likely in. Verified by reading, not by test — no existing test presses back while navigating with anything else open. **Not in the amendment; flagged, not fixed.**

### Is the go-home fallback reached from here today?

- **Not navigating:** yes — on the Maps tab with nothing nested (no drawer, no fullscreen, no dropdown, no picker/menu), back reaches handler 6: toast, then `finish()` on a second press within two seconds. `AvailabilityScreenBackNavigationTest:949-954` covers exactly this (`ShadowToast` text, then `activity.isFinishing`).
- **Navigating:** never — handler 1 consumes every press first. After the amendment it is still never reached while navigating, which is the owner's stated intent. The handler at `:859` itself is not touched by the proposal below; it acquires one more exclusion, the same way `:847` and `:843` already exclude the states nested inside them.

### Is there a dialog pattern to match?

Yes: Material3 `AlertDialog` with `title` + `text` + `confirmButton`/`dismissButton` as `TextButton`s, used four times on this app's surfaces — `ThreeWayActionDialog` (`AvailabilityScreen.kt:4255`, the wide map's own chooser) and the Cartography editor's delete confirm and leave prompt (`CartographyEntryEditScreen.kt:303`, `:315`, the latter with `testTag`s on each button, the shape the back-navigation tests already drive). The leave prompt is the closest analogue (system back raises it; Cancel keeps editing). No custom dialog style is introduced.

### Proposal (not built)

1. **Move navigation out of handler 1 and into the top-level chain as its own step**, between 5 and 6, so it is last before exit and unwinds *after* dropdown, drawer, fullscreen and tab:
   `BackHandler(enabled = !isDrawerOpen && !isMapFullscreen && compactTab == MAP && isNavigating) { showExitNavigationPrompt = !showExitNavigationPrompt }` — raise on one press, dismiss on the next, never exit; and handler 6 gains `&& !isNavigating`. Handler 1 loses its `isReturning` clause (its picker/menu pops are unchanged). This also fixes the finding above as a consequence: the drawer, fullscreen and the dropdown are backed out of before navigation is asked about. **This changes behaviour the amendment did not name** (back while navigating in fullscreen now exits fullscreen first instead of raising the dialog) — it follows from the owner's own order, but it is a decision: **confirm.**
2. **The prompt** is an `AlertDialog` in the leave-prompt pattern, wording as proposed in the amendment: title "Exit navigation?", text "Your track will keep recording.", confirm "Exit" → `onToggleReturning()` (which is `stopReturn()`, never `stopRecording()`), dismiss "Keep navigating". `onDismissRequest` (outside tap, and the dialog window's own back) = keep navigating. The dialog's dismissal via system back happens in the dialog's own window on device; under Robolectric the test's `onBackPressedDispatcher.onBackPressed()` targets the Activity, where the handler in (1) toggles the flag off — the same outcome by the other path, and the reason the handler toggles rather than only raises.
3. **The reason recorded at the handler**, verbatim from the owner: backing out can kill a process a person is relying on; someone navigating out of the woods must not lose it to a stray press; the raise/dismiss loop is deliberate and back cannot close the app from this screen while navigating; Home and the app switcher remain.
4. **Tests** (in `AvailabilityScreenMapIconStackTest`, which has `setNavigatingScreen`, `pressBack`, and the `onToggleReturning` counter): back raises the prompt and `exits == 0`; back again dismisses it and the HUD is still displayed; five presses in a row, `exits == 0`, HUD still displayed, no exit toast, `activity.isFinishing` false; "Exit" calls `onToggleReturning` once; ✕ and the pill toggle still exit with no prompt composed; the existing `system back while navigating exits the HUD` test inverts (it asserted the behaviour being removed — reported as a change, not absorbed). Back after navigation ends: the existing `AvailabilityScreenBackNavigationTest` chain is unchanged and stays the witness. Each new test reverted and re-run.

### Decisions this amendment does not make — stop-and-flag

- **(A) Order versus fullscreen/drawer/dropdown**, above. My proposal puts navigation after them, per "navigation is the last thing backed out of". The alternative — leave the handler in `CompactMapTab` and only swap the direct exit for the prompt — keeps today's inversion (prompt before fullscreen exit). Owner's call.
- **(B) Where the prompt state lives.** Top-level `AvailabilityScreen` (with the chain) rather than `CompactMapTab`. Compact-only in effect: the wide layout composes no HUD and no return control, so `isNavigating` cannot become true there from the UI; if a phone rotates into the medium width class while returning, the prompt handler would still be enabled with no HUD on screen. Pre-existing gap in stage one (the wide layout has no exit at all in that state); noted, not addressed.
- **(C) Predictive back.** Not enabled in the manifest; no gesture preview to reason about. If it is enabled later, an `AlertDialog` raised from a back callback is the standard shape and needs nothing extra.

## Standing rules

Baseline on this branch, confirmed last pass: 1114 tests, 24 skipped, skip set identical to the allowlist. The over-deletion that check caught is recorded above; the comparison runs again after this amendment.

## Required disclosure (pre-build)

**Confirmed:** the chain order and each handler's condition, by reading; that handler 1 consumes back while navigating before all others, by registration order (comment at `:1253-1258` states the same precedence rule for handler 2). **Inferred:** that the Robolectric back path bypasses the dialog window — from Compose's `Dialog` being its own `ComponentDialog`; the test will show it either way. **Could not determine:** device behaviour. **Premises in the amendment that were wrong:** "any open overlay is dismissed first, as today" — not while navigating; today navigation exits first (the finding). **Decided:** nothing yet.
