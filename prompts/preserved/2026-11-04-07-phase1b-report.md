# Phase 1b report: dispatch 2026-11-04-07, as amended without the 80% work

Hazel Cairnwright (coder), 2026-11-04. I'm a fresh session, taking over from the first Hazel's Phase 1 report. I wrote no code or tests and made no branch, commit or push. I ran no Gradle, didn't touch the phone, and edited no file in the repository (the worktree shows 0 changes). My scratch folder, `/mnt/work/scratch/map-type-chip-outlines/`, is deleted.

Tags: **[read]** means read at a file and line. **[observed]** means seen in command output. **[inferred]** means reasoned from what was read, not seen.

**Scope.** Partway through, Rowan sent a message relaying the owner's words: "Keep them solid as they are today." "Chips are the exception to that rule" "As In the opacity rule. Map chrome in general remains 80%". It withdrew A8, A9, B7, r6, C4's 80% items and C5, so this report covers only items 5 and 6. I had already read MapChrome.kt and MapChromeAlphaTest.kt when it arrived. I stopped the 80% work there and report nothing on items 1 to 4.

## 1. The base I read
- **pre-main** is at 9b58c66a4, and **records-after-173** at 47a3f970a, after `git fetch`. Neither has moved [observed].

## 2. What I re-checked from the first report
| Fact | Result |
|---|---|
| MapModeChips.kt: 116 lines. `FilterChip` at :107-113 has no `border`. The grey branch is :99-103, the default colours :105, and the fill semantics :112 | Holds [read] |
| Day `onSurface` Bark (Theme.kt:44), `secondaryContainer` #F6DFC8 (:28), `onSecondaryContainer` #3A2008 (:29). Night `onSecondaryContainer` #F6DFC8 (:78), `onSurface` Cream (:101). `MushroomDeep` (Color.kt:82). `outlineVariant` (Color.kt:158, :160) | Holds [read] |
| `LocalForagerDarkTheme` (Theme.kt:177) is provided from the same `shownDark` that picks the colour scheme (:151-154) | Holds [read] |
| `FilterChipTokens` widths: selected outline `dconst_0`, unselected `dconst_1`. The unselected outline colour is `OutlineVariant` | Holds [read, material3 1.5.0-alpha26 bytecode] |
| `filterChipBorder` defaults to an `outlineVariant` unselected border and a transparent selected one. Width and colour are picked by `selected` | Holds [read, bytecode] |
| `MapTypePanel` has two callers in main (CompactMapPickers.kt:96, CartographyEntryReportScreen.kt:648). `MapModeChipRow` has one (MapTypePanel.kt:117) | Holds [read, `git grep`] |
| 22ae67021's `MapTypePanelSelectedChipTest` exists. `CartographyEntryReportScreen`'s signature (:255) is unchanged since then | Holds [observed, diff] |
| Contrast from the solid colours: 10.18, 11.42, 3.85 and 12.80:1, and the night label (#F6DFC8 on #A2612C) at 3.81:1 | Reproduced [observed, WCAG formula] |

## 3. Mechanism prediction
Every chip fill stays opaque. MapChrome.kt, MapChromeAlphaTest.kt, MapTypePanel.kt, Theme.kt and Color.kt are not touched. Only MapModeChips.kt changes, from about 116 to about 140 lines.

- **Colours.** The `if (standsAside)` grey branch stays exactly as it is. The else branch, `filterChipColors()` at :105, becomes a call to a new function, `normalChipColors()`:
  - at night (`LocalForagerDarkTheme.current`), it returns `filterChipColors(selectedContainerColor = MushroomDeep)`;
  - by day, it returns `filterChipColors()`.
  - Under A7 the night label is Material's own default, #F6DFC8. So only the fill is overridden, and `PureWhite` is not used.
- **Border.** The `FilterChip` call gains `border = mapModeChipBorder(selected, standsAside)`, a new function:
  - for the grey chip (`standsAside`), it returns Material's own `filterChipBorder(enabled = true, selected)`;
  - otherwise, `filterChipBorder(enabled = true, selected, selectedBorderColor = colorScheme.onSurface, selectedBorderWidth = 1.dp)`.
  - The unselected chips keep the default border fields, so they are unchanged by construction [inferred from the bytecode reads].
- **The fill semantics at :112** will record #A2612C at night on every chip, as it records `secondaryContainer` on every chip today [read].
- **Both entry points behave the same,** because both reach the chips through the one call at MapTypePanel.kt:117 [read].
- **Before and after:**
  - The outline is missing at pre-main because the selected border is a 0 dp transparent hairline. After the change, ceil(1 dp) px of `onSurface` sits at the chip's edge.
  - The night fill goes from #6B421A to #A2612C.
  - The night label is #F6DFC8 both before and after, so its case passes by construction (B6).

## 4. Test plan
- **Files:**
  - MapModeChips.kt;
  - MapTypePanelOfflineChipTest.kt (260 lines);
  - two new files under `ui/map/`, each under 400 lines: `MapTypePanelChipHarness.kt` and `MapTypePanelChipOutlineTest.kt`.
- **Fixtures.** The harness calls `mapLayersViewModel` and `MapLayersTestScreen` without editing them (ui/availability/MapLayersUiFixtures.kt:215, :315; both `internal`) [read]. They live in `ui/availability/`, not `ui/map/`. K4's `clickable` goes on the new harness's own entry-map slot, following MapLayersUiFixtures.kt:205-210.
- **Harness.** It follows the first report:
  - xxhdpi, `GraphicsMode.NATIVE`;
  - `ForagerThemeWithNoSignal` as the root;
  - a real coordinate touch on the Layers row;
  - cases iterate `MapMode.entries`, because of the CI job that deletes the Street map (ci.yml:552).
- **Cases, through both entry points:**
  - day: fill #F6DFC8, label #3A2008, outline 3 px #3B2E24 (6 tests);
  - night: fill #A2612C, label #F6DFC8 plus no #FFFFFF pixels, outline 3 px #EDE3D0 (6 tests);
  - unselected chips, both themes: a 1 dp `outlineVariant` outline and no `onSurface` (4 tests);
  - the grey chip with no signal, both themes: fill, label, no outline (4 tests).
- **K3.** A no-outline check joins the existing fallen-back test, MapTypePanelOfflineChipTest.kt:200. That test returns early when no stand-in exists (:202).
- **K1.** :84 becomes `if (LocalForagerDarkTheme.current) MushroomDeep else colorScheme.secondaryContainer`. The night value is the ruled one from Color.kt, not read from the production function, so r2 and r5 can fail it.
- **Density.** That file runs at default density (`@Config(sdk = [36])`, :53). Outline widths are computed from the density, not fixed at 3 px.
- **B2, fail first:**
  - Predicted red:
    - the 4 outline tests;
    - the 2 night-fill tests;
    - 8 of MapTypePanelOfflineChipTest's 10 tests, the ones that assert the normal fill with night as the default theme (:117, :155, :179, :200, :218, :227, :234, :242) [read].
  - Green by construction: day fill and label, night label, unselected, grey, and the fallen-back no-outline check.
  - OwnTrailFallbackRetryTest runs by day only (:140), so it is unaffected [read].

**Reverts.** Each is one line on its own throwaway CI branch. For each I read the compile log first, accept only failures this edit could cause, restore from a saved copy, and confirm the forward change is still there.
| Revert | Edit | Predicted failures |
|---|---|---|
| r1 | delete the `border =` argument (not "width 0", which still draws a hairline) | the 4 outline tests only |
| r2 | the night branch returns `filterChipColors()` | the 2 night-fill tests and K1's night assertions. **Not the night label tests** |
| r3 | `mapModeChipBorder` ignores `standsAside` | the grey outline tests and the fallen-back check |
| r4 | the outlined border also sets `borderColor = onSurface` | the 4 unselected tests only |
| r5 | the day branch returns the night colours | the 2 day-fill tests, K1's day assertions, OwnTrailFallbackRetryTest.kt:174. **Not the day label tests** |
| r7 | the night branch adds `selectedLabelColor = com.zynergylabs.forager.app.ui.theme.PureWhite` (fully qualified, so the edit needs no import) | the 2 night label tests only |

**B4:** full CI green, with counts from the JUnit XML of the build job and of the job that deletes the Street map.

**Contrast:** the solid figures stand as above. With solid chips, only the outline against the panel depends on what is under the panel, because the panel is at 0.8 (MapTypePanel.kt:83) [read]. In the tests I'll read the panel colour from its padding, over the test's own background, and name that background in the report.

## 5. Stops and wrong premises

**S3. Owner-level: which chips "Chips are the exception" covers.** At least two other chips over the map are at 80% today [read]:
- the taxon filter chip, `TaxonMapFilterChip` (AvailabilityMapOverlaysUi.kt:342-345);
- the journal entries chip (JournalEntriesChip.kt:93-94).

Read literally, "Chips are the exception to that rule" covers them too, while "Map chrome in general remains 80%" leaves them as they are. This dispatch touches neither.

Separately, CLAUDE.md's "Nothing fully obstructs the map view" lists "chips" among the 80% surfaces, and says exceptions are recorded in docs/plans/journal-redesign.md. A grep of that file found no chip exception [read, grep only]. Recording one is the planner's job, not mine.

**S4. Owner-visible, small, unruled: the night press ripple turns white** [inferred].
- **Why.** The chip's Surface takes its content colour by default: the default mask, 192, covers `contentColor` at the Surface call in `SelectableChip` [read, bytecode]. So the colour comes from `contentColorFor(fill)`.
- #A2612C is a role only in the day scheme (Theme.kt:26), so at night nothing matches. The colour falls back to the panel's content colour, `Color.White` (MapTypePanel.kt:113) [read].
- Pre-main's #6B421A is the night `secondaryContainer`, so its content colour is #F6DFC8.
- So at night the press ripple goes from pale to white. Day and the grey chip are unchanged.
- It shows only while the chip is pressed, since a tap closes the panel (MapTypePanel.kt:119-121) [read].
- Not read: that the Surface's ripple takes `LocalContentColor`. No test is planned for it.
- **Options, not chosen:**
  - (a) leave it as Material derives it;
  - (b) pin the ripple to the label colour. `FilterChip` takes no content colour, so this would need more than a colour argument.

**W1 and W2. Predictions that A7 made wrong.** These are technical, not owner-level:
- the dispatch's B3 says r2 fails the night "fill and label" tests;
- B5/K2 says r5 fails the day "fill and label" tests.

The forward change no longer overrides any label, so each fails only its fill tests (table above).

**K5. A call for the planner.** No revert now bites the day label, grey fill or grey label tests. Options:
- (a) list them as "not shown to bite". r7 shows the same label reader does bite at night.
- (b) add r8: the day branch given `selectedLabelColor = PureWhite`. It should fail only the 2 day label tests.

**K6. A call for the planner.** Is B8's "over a light and a dark background" withdrawn along with the 80% parts? Or is it kept for the outline-against-the-panel figure? If kept, the cheap way is a stand-in background behind the panel in the direct-panel harness.

## 6. What I could not determine
- **Robolectric's pixels:** whether it draws exact label glyph cores and exact 3 px outline edges. 22ae67021's harness used the same reads, but I didn't check its CI result.
- **The ripple's colour source,** per S4.
- **Taken from the first report, not re-read here:**
  - the resolved foundation version for the hairline rule;
  - the night-mode fade behaviour (ColorSchemeFade.kt);
  - the MainActivity theme root;
  - run 41863141684's counts.

Phase 1b ends here. Phase 2 waits for Rowan's go, and for S3, S4, K5 and K6 as he chooses to rule them.
