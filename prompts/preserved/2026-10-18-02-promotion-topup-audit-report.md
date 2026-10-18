# Elder Ford: top-up audit of the second promotion, main `969ecf0a` → pre-main `e61e248a` (RECORD -1266)

**Verdict for the whole promotion: Pass with items.** Nothing found that risks a user's records, safety or trust. Six items for the owner to ship or hold, none a block; the two height checks D1 and D2 are "not seen on a phone" because the owner deferred them (-1256, -1258). Read-only session: no files written, no PR, no merge, no Gradle.

## Baseline, confirmed

- `git ls-remote`: main `969ecf0a`, pre-main `e61e248a`, records-after-173 `77bec92a`, each equal to the fetched head.
- The top-up range `b91de824..e61e248a`: 27 commits, 29 files (+1867/−249): 15 app files, 10 test files, README and three process documents. `.github/workflows/ci.yml` is not in the range, so the skip allowlist cannot have grown.
- **CI at `e61e248a`: run 39131042296, all three jobs green** (Build/test/publish APK, Android lint, Release compiles and packages). It finished at 13:32 UTC, during this audit; I waited for it rather than cite the branch run alone. The merge commit's tree is byte-identical to PR #255's head `427effd1` (`a54cc919…` both), whose run 39128621114 was also green; the planner read 5,054 tests, 0 failures, 24 skipped there (-1261). I did not pull the test-summary table myself, so that count is the planner's reading, not mine. Each PR branch head was green before its merge (#251 cb4aa154, #252 92ca1adf, #255 427effd1).
- Tests: 3 `@Test` removed, 42 added, no `@Ignore` or `assume` added. The three removed are the ones the record says were replaced on owner rulings: `L2 the map cap follows the window's own height` (the split no longer reads it) and the two `L1 … slider is reached by scrolling` tests, replaced by `RECORD -1253 … the slider is in view on open and takes real touches` (LandscapeOfflinePickerTest.kt:220-229). J5's tests assert the restored ruling (hidden until the icon; icon or Back hides it; the -1244 slide), not -1238's "always show" (AvailabilityScreenJournalShortWindowTest.kt:149-221).
- Read from source: RECORD -1210 to -1266; dispatch files 2026-10-17-08, -09 and 2026-10-18-01 exist on records-after-173, as do the two phone-check files (-06, -07) the first audit's item 4 asked for; PR bodies #251, #252, #255 (device checks).

## Mechanism prediction (written before the diff) and what the diff showed

Predicted: #251 keys the viewfinder on a stable "could be held" value in `NaturalOrientationHold.kt`, with the risk of leaking onto phones; #252 a shared shape in `MapChrome.kt`, with the pill's clearance from a left navigation bar and the rail's edge depending on real insets Robolectric reports as zero, a possible J5 state reset, the offline panel losing its next step, and the chips crowding at large font; #255 a landscape Row with a 4:3 map, with the fullscreen/Back path at risk; the credit change must leave one visible credit.

Result: the camera shape held exactly (`viewfinderInNaturalFrame`, `NaturalOrientationHold.kt:131-135`; the key at `CameraXCaptureSession.kt:863`). The phone leak is closed by construction: `held` needs `fullScreen` and a non-zero rotation, phones honour the portrait request, and the S22 log showed `SurfaceViewImpl` with no natural-frame line (-1220). The insets failure I predicted did happen, on the owner's S22 (-1231, pill over the navigation bar), and was fixed with a real-inset test. `MapChrome.kt` gained only test semantics; the shape came from passing existing parameters to the three tabs' existing call sites. The J5 reset did not materialise (`searchHeaderRevealed` is in the saveable `JournalScreenState`, L7 test covers the turn). The offline panel's next step did go, by the owner's ruling (item 1). The entry is not a Row but a measure-only `Layout` keeping the one `mapSlot` call (`CartographyEntryReportScreen.kt`, `EntryReportLayout`), which is better than I predicted for rotation.

## Not seen on a phone

- **D1 and D2, the height checks** (preserved/2026-10-17-07 part D): deferred by the owner. The steady-climb figure and a sudden climb's reading are unverified on a phone in this promotion; nothing in the range changes height code.
- **The tablet, for the three UI PRs.** PR #252 step 9 and PR #255's layout were not run on the tablet (-1261: "real tablet hardware" untested). Specifically: the tabs' landscape pill and rail at sw ≥ 600, and the day entry's split where the 640 dp readable cap and the 240 dp info minimum meet (`LANDSCAPE_ENTRY_INFO_MIN_WIDTH`, doc comment names the tablet case). The camera fix was seen on the tablet (27 turns, 0 restarts, -1219).
- **The owner's S26 Ultra:** nothing in the range was seen on it; every phone result is the S22.
- **Largest font size** (Appendix B item 4): the landscape offline panel's one-line OK / Cancel / Download Maps row uses `maxLines = 1` with compact padding in a half-width panel (`CentrePinLocationPicker.kt`, `CENTRE_PIN_COMPACT_BUTTON_PADDING`); Hazel listed "whether Download Maps fits at the phone's real font" as untested (-1254) and the owner saw it at the default size. The Records chips on the switch line have a font-scale 1.15 test that "measures and states" the fit, not a gate.
- **PR #252 steps 6 and 8** (the pill's end against the cut-out when the punch hole is on the left; the first-visit fallback size): -1255 records the owner's overall "Looks good on S22" with no per-step results, so neither is confirmed either way.
- **The tablet's picture at ROTATION_0** now goes through the turnable viewfinder (TextureView, COMPATIBLE) where it used the fast one; the owner reported nothing wrong and declined screenshots; smoothness and battery there are unmeasured (-1222 deferred). Not an A8 item (nothing stays on longer), recorded as unmeasured.

The first audit's phone items are now closed on the S22: (A) rotation updated the find page and Maps without a restart (lint steps 1 and 3 pass, -1258; the day entry's step 2 switched layout but was "squished", replaced by #255 and passed, -1263); (B) the S22 is Android 16, so the inset boundary does not apply; (C) the quick backup-flag read passed (no ALLOW_BACKUP); the long transfer check (section 7, optional) was not run.

## Items (owner ships or holds each)

**1. Offline-maps screen: Download Maps is greyed out until OK is tapped, and nothing now says so.** Records → Offline maps. `AvailabilityOfflineMapsUi.kt`: `enabled = hasValidRegion && !isDownloading`; the two lines that explained the state ("No location picked yet — pan the map above and tap OK." and "Download region: …") were removed in both orientations under -1245, together with the pan instruction. What remains is "Pin at: lat, lng", which follows the map as it pans and does not change when OK is tapped. A first-time user can tap a dead Download Maps button and not know OK is the step they missed. Principle: Don't make me think (a state that could read as broken); honest, then one step beyond. The QA philosophy puts removal of such text with the owner each time, and the owner ruled on Hazel's list naming these lines; I record it so the ruling stands with its consequence known. The confirm dialog ("Download this area?") is unchanged. Not seen failing on a phone; the owner's S22 walk (7f) tapped OK first.

**2. Landscape first visit: the pill is half width until Maps has been shown.** `AvailabilityCompactScaffold.kt`: `lastMapsStripWidth` is 0 until the Maps tab measures its strip in this landscape window; the other tabs then use half the width (the Maps pill's own fallback, -729) and jump to Maps' size after a visit to Maps. The owner's own words in the dispatch comment: "The layout resizing between tabs looks like a UI glitch." This happens once per landscape session when the user turns the phone on List, Seasonal or Journal. Hazel flagged it (PR #252 step 8, observation), the owner did not report it. Principle: polish is even. Minor; the state is also plain `remember`, so a process restart in landscape returns to it.

**3. The known-bugs list was not touched for this promotion.** A6 says the promotion lists the bugs it ships with. Item 2 above and the deferred device-only observations (-1257) live in RECORD only. Row 3 stands unchanged (my first-audit note 3 still applies: it may be unreachable). Documents only; the owner may want item 2 listed or dismissed.

**4. The search pill is now re-created on every tab switch.** `AvailabilityCompactScaffold.kt`: `key(compactTab())` wraps the `AnimatedVisibility` holding `SearchEntryBar`, in portrait too, so arriving on a tab shows no slide. The query text is hoisted in `uiState` and survives; anything remembered inside the bar (focus, a text field's internal cursor state) resets on the switch. Inferred from the code, not seen; low.

**5. Known bugs carried from the first audit, unchanged:** row 3 (search summary month after a language switch) is live and not serious; row 2 is tests only.

**6. Battery/smoothness on the tablet camera at ROTATION_0** (above): unmeasured, not a fail.

## Walked as step paths, clean

- **Journal, landscape:** tap the row's search icon → the pill slides down at the left, Maps' size, solid → tap the pill → the panel opens under it, solid → Back closes the panel; Back again hides the pill with a slide (`JournalTab.kt:912-913` BackHandler; tests at lines 187-221). Turn the phone and back: switch, chip and view survive (L7 test). Owner: 7d passed within "Looks good".
- **Journal → Records, landscape:** chips on the switch line, scrolling, selected chip brought into view (`RecordsFilterChips.kt` bringIntoView; `RecordsTab.kt` hands the row up once with `rememberUpdatedState`, so no recomposition loop). The chips are record types (Finds, Tracks, Waypoints, Offline maps); nothing reads as identification.
- **Day entry, landscape:** open an entry with a map → 4:3 map left (`fillMaxWidth().aspectRatio(4/3)` inside `Constraints(boxWidth, boxHeight)`), header pinned right, info scrolls → tap the map → fullscreen (stacked branch, `fullscreen = true`) → Back → the split. The one `mapSlot` call never moves, so the live map survives the turn without a rebuild. Owner: "Pass 1-3" (-1263).
- **Seasonal credit:** `seasonalShowsOpenMeteoFigures` matches the `when` that draws the pattern and the card's own figure condition exactly, so one credit shows if and only if an Open-Meteo figure is on the tab; it is a 48 dp button with an `onClickLabel` (`OpenMeteoCredit.kt:45-46`). Licence attribution kept.
- **Rail:** `WindowInsets.navigationBars.union(displayCutout)` on the rail's side; the content reserves the cut-out only on the far side (`AvailabilityNavigationUi.kt`, `shortLandscapeContentInsets(railEdge)`); the keyboard is reserved on the content column, not the Row, so the rail keeps its height (-1242). Owner: pass on every tab, both directions (-1263).
- **80% rule:** the pill and panel go through `mapChromeFill(role, overMap)`, 0.8 on Maps and solid elsewhere (`AvailabilitySearchUi.kt`); the Maps tab's values are unchanged. The owner's "solid where no map" is followed.
- **Camera rule (-981):** untouched; the hold stands; phones unchanged by log evidence.
- **Documents:** README's new `importgpx` row names a package that exists (`git ls-tree`); known-bugs' closing line corrected; Ash Transect added to the team table with Hazel scoped to the app. No user meets these.

## What a user meets in the production build made from main after this promotion (969ecf0a → e61e248a)

- New-phone setup on Android 12+ carries no Forager data (from the first audit; Forager's own Backup remains the route, as the privacy policy says).
- On a tablet, the camera no longer restarts with a black flash on each portrait-landscape turn (seen: 0 restarts in 27 turns). Phones unchanged.
- List, Seasonal and Journal: the search line is the map's rounded, outlined pill, solid; the bottom bar has the map's rounded top corners and outline; page content sits about 5 dp higher; no size change on a tab switch. In landscape the pill sits at the left, Maps' size, clear of the navigation bar; the search panel is solid; Journal's hidden pill slides in and out; Records' chips sit on the Entries/Records line and scroll; the rail keeps its full height under the keyboard and reaches the screen edge on every tab.
- Offline-maps screen: the three explanatory lines are gone in both orientations, the continental-US note is last, the budget shows the figure only; in landscape OK, Cancel and Download Maps share one line with the Name box and slider in view on open (and item 1 above).
- Day entry in landscape: a 4:3 map on the left with the info scrolling on the right.
- Seasonal: one Open-Meteo credit, at the bottom.
- CI gains a lint job and a release-compile job (no user effect).

## Could not determine

- The test totals at `e61e248a` from the CI summary table itself (the planner's 5,054 / 0 / 24 at the identical tree is cited as theirs).
- Anything about the tablet's layouts for #252 and #255, the S26, the largest font, or the first-visit pill: not seen on a phone, as listed.
- Whether a user at the offline panel actually stalls on the greyed Download button (item 1): inferred from the code and the removed text, not observed.

## Beyond scope

None. I wrote nothing, ran no Gradle, opened no PR, merged nothing.
