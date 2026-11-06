# Layers to Tools: verify-first report (dispatch 2026-10-28-02, with Amendment 1)

Hazel (coder, Opus), 2026-10-28T17:57:58Z. Dispatch read from `origin/records-after-173` (`5700932b6`, then `9580337fa` for
Amendment 1, RECORD -1764 and -1765). Verify-first only: nothing is built yet.

**Base.** `origin/pre-main` fetched at the start: `e2969abe4f98674c24ce3913ce97be568184a4de`, the base the dispatch names. Branch
`layers-to-tools`, worktree `~/.cache/forager-wt/layers-to-tools`, cut from it. Every `file:line` below is in that tree unless a
commit is named.

## 1. Who calls `MapLayersSheet`, and what only it uses

**Callers (confirmed).** Two in production:
- `ui/availability/CompactMapPickers.kt:99` (the Maps tab), opened from `AvailabilityCompactMapUi.kt:556` (`showLayersSheet = true`)
  and held at `AvailabilityCompactMapUi.kt:264`.
- `ui/log/CartographyEntryReportScreen.kt:651` (the entry map, fullscreen only), opened at `:631`, held at `:319`.

Three tests compose it directly: `MapLayersSheetTest.kt:55`, `MapLayersSheetOfflineChipTest.kt:85`, `ReferenceLayerCardTest.kt:100`.
The imports at `CompactMapBottomChrome.kt:38` and `AvailabilityMapIconCluster.kt:98` are imports only (no call). Every other hit is a
comment that names the sheet as a colour precedent (`MapChrome.kt:91`, `:109`, `JournalEntriesChip.kt:66`, `SearchNotice.kt:48`,
`AvailabilityMapOverlaysUi.kt:146`, `:218`, `AvailabilityScreenDrawerHost.kt:90`, `CentrePinLocationPicker.kt:377`,
`SpeciesSearchField.kt:173`, `RecordDetailsSheet.kt:123`, `CartographyEntryReportScreen.kt:472`, `Basemap.kt:39`, `MapMode.kt:6`).
Those comments need rewording when the sheet goes; no behaviour depends on them.

**What `MapLayersSheet.kt` (593 lines) holds, and what happens to each part:**

| Lines | Symbol | Used by | Fate |
|---|---|---|---|
| 95-110 | `MAP_LAYERS_SHEET_TAG`, `MapLayersSheetContainerColor`, `MapLayersSheetContentColor` | the sheet and tests only | removed with the sheet |
| 113, 120, 123 | `mapLayerSwitchTag`, `mapLayerOpacityTag`, `mapLayerReorderTag` | the sheet's rows, tests | move with the rows |
| 115-117 | `REFERENCE_TRAILS_LABEL` "Official trails", `REFERENCE_TRAILS_DETAIL` "Forest Service and BLM" | the sheet `:301`, `:326-327`; the card's title | label becomes "Trails"; detail removed |
| 133-134 | `layersButtonDescription` | `MapIconBar.kt:197` | stays, wording changes (question Q4) |
| 137-164 | `MapOverlayOption`, `MAPS_TAB_OVERLAYS`, `ENTRY_MAP_OVERLAYS` | the sheet; `ENTRY_MAP_OVERLAYS` only by the entry map's sheet `:653` and `JournalEntriesOnMapTest` | `MAPS_TAB_OVERLAYS` moves to the Tools section with the new labels; `ENTRY_MAP_OVERLAYS` has no caller once the entry map lists no switches, so it goes |
| 181-215 | `MapLayersControls` | the Maps tab's hosts, the legend, the journal chip | stays (the Tools section reads it) |
| 236-339 | `MapLayersSheet` | the two hosts above | removed |
| 345-476 | `LayerSwitchRow`, `ColourFieldRows`, `OpacityRow` (all private) | the sheet only | moved, unchanged in behaviour, to the new Tools section's file |
| 478-593 | legend constants, `MapLegendChip` | the Maps tab | stay |

After the move `MapLayersSheet.kt` holds the controls value and the legend chip (about 230 lines). Its name then misleads; renaming
it is a pure move I would not make unasked.

`MapModeChipRow` (`MapModeChips.kt:82`) is not only the sheet's: it is what the new panel's three chips should be, so it stays and
the panel calls it. Its doc (`:68-79`) measures the grey chip's contrast against the sheet's colour; the panel draws on the icon
bar's fill instead (`MapIconStackButtonColorDark/Light`, Bark or Cream at 0.8), so those figures stop describing it. I will
re-measure them on the panel's fill and record them. That is a figure to restate, not a question.

## 2. Trails' visibility rule: how the Tools drawer can learn it

**How it works today (confirmed).** Each map reports whether its loaded style draws the band: `SightingsMap.kt:317` (state set
on each style load), `:324` (reported to the host through `MapRenderMode.onReferenceTrailsShown`, `MapRenderMode.kt:292`). Each
host keeps its map's report in its own `ReferenceTrailsShown` holder (`ReferenceTrailsVisibility.kt:116-120`):
- the Maps tab: `AvailabilityScreen.kt:453`, already carried into `MapLayersControls.referenceTrailsShown` (`:456`,
  `MapLayersSheet.kt:205`);
- the entry map: `CartographyEntryReportScreen.kt:320`, local to that screen, read only by its sheet (`:662`).

**What the Tools drawer can see.** The drawer is composed by `AvailabilityScreenDrawerHost` (`AvailabilityScreen.kt:691`), which
already has `mapLayersControls` in scope (`AvailabilityScreen.kt:454`). So the Maps tab's report reaches the drawer as **one new
parameter**, with no new condition anywhere in working code. The row's own `if` is the one that exists today
(`MapLayersSheet.kt:320`), moved.

The Maps tab's holder is remembered in `AvailabilityScreen`, above the tabs, so it keeps its last value while the Journal shows.
The Maps tab's map itself is not composed on the Journal (`AvailabilityCompactScaffold.kt:1154-1158`: the tab crossfade drops it
once its fade ends), and nothing resets the report when that map leaves (`SightingsMap.kt:324` has no dispose). So over the
Journal the drawer would show the Trails row when the **Maps tab's** map last drew the band.

**What it cannot see without new plumbing.** The entry map's own report (`CartographyEntryReportScreen.kt:320`). Getting it to the
drawer means a holder above both, written by the entry map: the same shape as `LocalTrackDetailsFold`
(`RecordDetailsParts.kt:131`, provided at `AvailabilityScreenDrawerHost.kt:65`). That is a new path, not a conditional in working
code, but it is a decision about meaning, so it is **question Q1**, not mine to pick.

When the two maps differ: the entry map starts from the Maps tab's choice (`initialMapMode`, `CartographyEntryReportScreen.kt:317`)
but its own Trail/Topo/OSM choice is local to it. They differ only when the user changes the entry map's type, or when the entry
map is on the downloaded areas.

## 3. Credits

**Confirmed: the map's own credit does not depend on the card or its fetch.**
- The caption: `SightingsMapLayout.kt:139` builds it from `activeLayerCredits(MAP_LAYER_REGISTRY, drawnLayersState)`
  (`MapLayerState.kt:80-84`), which reads the band layer's registry credit, `REFERENCE_TRAILS_CREDIT` =
  "Trails: USDA Forest Service, CC BY 4.0 · BLM" (`MapLayers.kt:358`, `:366`). It shows while the band is drawn. No card involved.
- Settings' data credits: `DataSourceCredits.kt:58-64` (Forest Service: name, "CC BY 4.0", a link to the licence, and "Trail lines
  in Forager are simplified from this data."), and `:66` (BLM, public domain). The comment at `:56-57` says what CC BY 4.0 asks
  for: the licence's name, a link to it, and a note of changes. All three are there, with no card involved.

**What only the card carries.** The "Sources:" line (`ReferenceLayerCard.kt:114-124`, text from
`ReferenceTrailsManifest.kt:80-84`): each agency's data date and its licence name again, plus a hint, "Dates show once you've been
online." (`:73`). The licence names are repeated elsewhere. The **data dates** are shown nowhere else. A date is not something
CC BY 4.0 asks for (the licence's own terms, as `DataSourceCredits.kt:56-57` records them; I did not read the licence text this
session). So no licence credit is carried only by the card, and the dispatch's stop does not apply. The owner should still know
the dates go with the card (see "What the owner loses" below).

**Everything only the card uses (confirmed by grep across `app/src/main`):**
- `ui/map/layers/ReferenceLayerCard.kt` (127 lines): the card, the (i) button, `REFERENCE_CARD_WHAT/READING/ROUTES`, the four tags,
  `REFERENCE_TRAILS_INFO_DESCRIPTION` "About Official trails", `LocalReferenceTrailsSources`, `ProvideReferenceTrailsSources`.
- `domain/referencetrails/ReferenceTrailsSources.kt` (107): the fetch-and-keep holder and its two interfaces.
- `domain/referencetrails/ReferenceTrailsManifest.kt` (85): the manifest parser and the Sources text. Its `FOREST_SERVICE_LAYER`
  and `BLM_LAYER` have no other user.
- `data/referencetrails/WorkerReferenceTrailsManifestSource.kt` (41): the one network fetch, of
  `/reference-trails-manifest.json` from the tile Worker (`:37`).
- `data/repository/DataStoreReferenceTrailsDatesStore.kt` (65): its DataStore file `reference_trails_dates` (`:60`). Nothing else
  names that file.
- The wiring: `AppContainer.kt:176-178` (imports) and `:326-330`, and `MainActivity.kt:96` and `:390` (the provider around the app).

`domain/referencetrails/ReferenceTrailRecord.kt` stays: the map's band taps, bubbles and tap precedence use it
(`OverlayFeatures.kt:13`, `TapPrecedence.kt:3-4`, `MapListeners.kt:10`, `MapBubbles.kt:25`, `MapRenderMode.kt:10`,
`MapTapHandler.kt:4`).

Removing the store leaves an orphaned `reference_trails_dates.preferences_pb` on phones that opened the card. It is harmless and
nothing reads it. Deleting it on upgrade would be a new cleanup path, which I would not add unasked. The Worker keeps serving the
manifest; that lives in another repository and is not touched.

**What the owner loses (confirmed):** the only place the Forest Service and BLM data dates appear in the app.

## 4. The fold's holder

**Map Layers (proposed).** One flag, `rememberSaveable { mutableStateOf(false) }`, in `AvailabilityScreen`, beside the walk Details
fold that already works this way (`AvailabilityScreen.kt:317`, "Not across a restart (not asked for)."). It is passed through
`AvailabilityScreenDrawerHost` (`:43`), `CompactToolsDrawerContent` (`AvailabilitySettingsUi.kt:209`) and `SearchControls`
(`SearchControls.kt:45`) to the new section. Held there, it outlives every way the drawer's list leaves composition: closing the
drawer, changing tab, going into Settings and back (`AvailabilitySettingsUi.kt:250-282`: the Tools list is not composed while
Settings shows), and rotating. It does not outlive a cold restart, as the dispatch asks. `CollapsibleSection`
(`SearchControls.kt:75-105`) keeps its own `remember` for its other callers. The Map Layers call gets the same composable with
its open state passed in: a second, hoisted form of it, not a flag inside it.

**Trip Planner's fold today (confirmed in code, not run).** It is `remember` inside `CollapsibleSection` (`SearchControls.kt:81`).
The dispatch's premise "which resets today" is only partly true:
- It **survives** closing the drawer and changing tab. The drawer host is composed unconditionally
  (`AvailabilityScreenDrawerHost.kt:3-6`), and the drawer keeps its sheet composed while closed. That second claim comes from the
  comment at `AvailabilitySettingsUi.kt:231-235`; I did not check it against material3's source, which is not in the local Gradle
  cache.
- It **resets** on a trip into Settings and back, because the list leaves composition (`AvailabilitySettingsUi.kt:257-281`), and
  on rotation, because `remember` is not saved.

Its doc comment records the opposite intent: "Collapsed by default ... per the user's request, rather than remembering whichever
state they were last left in" (`SearchControls.kt:63-65`). That predates the UX-defaults rule in CLAUDE.md, and the two disagree.
Out of scope. Reported, not changed. The same `CollapsibleSection` also draws "Recent searches" over the map
(`SearchDropdown.kt:283`), which behaves the same way.

## 5. Chevrons (no up or down arrow on a fold-open list)

Grep of `app/src/main` for `ExpandMore|ExpandLess|ArrowDropDown|ArrowDropUp|TrailingIcon|KeyboardArrow|Chevron*|NavigateNext|NavigateBefore|ArrowForwardIos|ArrowBackIos|UnfoldMore`,
and of `res/` for chevron, expand_more and arrow_drop drawables. The hits:

| Site | What | Spoken label today | Plan |
|---|---|---|---|
| `SearchControls.kt:91-94` (`CollapsibleSection`) | ExpandLess/More | the icon's description: "Expand/Collapse <title>" | icon removed; the label moves to the header row's click label |
| `RecordDetailsParts.kt:118-121` (walk Details) | ExpandLess/More | already on the row's click label (`:110`) | icon removed |
| `EntryContentsPanel.kt:154` (an entry's contents groups) | ExpandLess/More, leading | already on the row's click label (`:148`) | icon removed |
| `SearchDropdownSections.kt:143` (Month) | the dropdown's trailing icon | the field's own | trailing icon removed; a tap on the field still opens the menu (`:137`, `:146`) |
| `PhotoViewerDialog.kt:184`, `:194` | left and right arrows | — | stay, as the dispatch says |

Beyond the dispatch's list: removing the icon from `CollapsibleSection` also removes it from **"Recent searches"**
(`SearchDropdown.kt:283`). That fits "all chevrons from the drop down lists", so it gets a header-tap test like the others. No
other hits. The species field (`SpeciesSearchField.kt:112`) has no trailing icon.

## 6. Every test touching what goes or changes

31 test files match. "Rewritten" means the same claim is now tested through the new control. "Removed" means the thing it tested
is gone.

**Removed with what they test:**
- `ui/map/MapLayersSheetTest.kt` (90): the sheet's container and content colours. The panel and the Tools section get their own
  colour assertions instead, under the 80% rule (`MapChromeAlphaTest`).
- `ui/map/layers/ReferenceLayerCardTest.kt` (263): the (i), the card and its sources.
- `domain/referencetrails/ReferenceTrailsSourcesTest.kt` (116), `domain/referencetrails/ReferenceTrailsManifestTest.kt` (100),
  `data/referencetrails/WorkerReferenceTrailsManifestSourceTest.kt` (89), `data/repository/DataStoreReferenceTrailsDatesStoreTest.kt`
  (59): the fetch and the store.

**Rewritten for the panel (map type through the Layers button):**
- `ui/map/MapLayersSheetOfflineChipTest.kt` (255): the grey chip. It moves to the panel and keeps no-signal and the fallback.
- `ui/map/OwnStreetFallbackRetryTest.kt:186`: reads `mapModeChipTag` and `MapModeChipSelectedFill`. Unchanged if the panel keeps
  `MapModeChipRow`; I will check that it still opens the chips the new way.
- `ui/availability/MapChromeChipsTest.kt` (113): the chips centred on the sheet. It becomes the chips sitting inside the panel's
  bounds.
- `ui/availability/AvailabilityScreenMapIconStackTest.kt` (3460): `:1137` and `:2462` use the old description; `:2610-2647` touch
  the Layers row with the bar dragged to the bottom on each side. They become coordinate touches that open the panel and pick a
  type.
- `ui/availability/AvailabilityScreenBasemapPersistenceTest.kt` (306): `:156`, `:170`. Open the panel instead of the sheet.
- `ui/availability/HudLandscapeT11FixesTest.kt:558`: the "Layers" control's opened-state predicate becomes the panel's tag.
- `ui/log/CartographyEntryReportScreenFullscreenTest.kt` (248): opens the entry map's Layers. Becomes the panel.
- `ui/availability/MapLayersUiFixtures.kt:101`, `ui/availability/AvailabilityScreenAdaptiveLayoutTest.kt:182`: the exact old
  description text. Updated to the new wording (Q4).
- `ui/availability/MapChromeResumedTest.kt:115`, `ui/availability/LandscapeLRulingsTest.kt:97`: find the row by its "Layers:"
  prefix. Unchanged if the prefix stays.

**Rewritten for the Tools section (switches):**
- `ui/availability/AvailabilityScreenMapLayersTest.kt` (507): switches, opacity and reorder, through the sheet. Its label list at
  `:70` carries the old labels. It moves to Tools > Map Layers with the nine new labels.
- `ui/availability/ReferenceTrailsSwitchTest.kt` (150): the row's rule, its touch, its stored state and "opens no card". It moves
  to Tools, with "Trails" on one line, and asserts no (i) is present.
- `ui/availability/FindPhotosOffMapTest.kt`: two `mapLayerSwitchTag` touches. Reached through Tools.
- `ui/log/CartographyEntryMapLayersTest.kt` (144): `:127`, "its sheet lists only the five record overlays this map draws". That
  claim goes. The entry map lists no switches now; the Tools switches change what it draws (step 7), and `:118` stays.
- `ui/log/CartographyEntryReferenceTrailsTest.kt` (135): the row on the entry map's sheet. Depends on Q1.
- `ui/map/JournalEntriesOnMapTest.kt:142-143`: "the Maps tab's sheet lists Journal entries last, and the entry map's does not".
  Becomes: the Tools list's eighth label is "Journal Entries". The entry-map half goes with `ENTRY_MAP_OVERLAYS`.

**Fold and chevron tests (behaviour unchanged, kept):** `ui/log/TrackSheetDataTest.kt` (`RECORD_DETAILS_FOLD_TAG`),
`ui/log/EntryDataScreensTest.kt` and `ui/availability/LeavingTheJournalFixesTest.kt` (`entryGroupRowTag`),
`ui/availability/MapChromeOverMapTest.kt` and `ui/availability/MonthWordsFollowTheAppLanguageTest.kt` (`MONTH_MENU_TAG`), and the
Trip Planner and Recent searches header taps (`AvailabilityScreenLayoutTest.kt:701-751`, `TripWindowsGuidanceTextTest.kt:113`,
`SearchClearAndFrameTest.kt:138-139`, `MonthWordsFollowTheAppLanguageTest.kt:161`). None of them asserts on an icon, so all are
expected to pass unchanged. That also means **none of them would catch a chevron coming back**. The new no-chevron tests carry
that.

**Comments only (no assertion):** `domain/GetMapRecordsUseCaseTest.kt:102` ("Recording trail") and
`ui/map/layers/MapLayerRegistryTest.kt:330` ("Official trails" in a message string). The wording follows the labels.

**Possibly moved by layout, to watch in CI:** `SundownSettingsTest.kt:270` measures the Trip Planner header's bounds. A new
Map Layers header below it should not move it, but this is unverified until CI runs.

## 7. Amendment 1: Title Case, and where each label is shown

The overlay labels are inline strings at `MapLayersSheet.kt:146-152`, not shared constants. The one constant is
`JOURNAL_ENTRIES_OVERLAY_LABEL` (`JournalEntriesOnMap.kt:15`), read only at `MapLayersSheet.kt:153` (grep of `app/src`). So changing
each label changes only the switch, and no split is needed for these nine:

| # | Today | Map Layers switch | Shared with another screen? |
|---|---|---|---|
| 1 | Finds | Finds | no (inline). Other "Finds" on other screens are their own literals: `EntryContentsPanel.kt:232`, `EntryReportSummary.kt:183`, `RecordsFilterChips.kt:174` |
| 2 | Photos | Photos | no |
| 3 | Waypoints | Waypoints | no |
| 4 | Planned trips | Planned Trips | no |
| 5 | Recording trail | Recording Tracks | no |
| 6 | Tracks | Tracks | no |
| 7 | Offline maps | Offline Maps | no. The Tools drawer's own "Offline maps" row (`AvailabilitySettingsUi.kt:176`) and Records' chips (`RecordsFilterChips.kt:177`, `EntryContentsPanel.kt:233`) are separate literals and stay as they are (question Q3) |
| 8 | Journal entries | Journal Entries | `JOURNAL_ENTRIES_OVERLAY_LABEL` is used once. I change its value; no split |
| 9 | Official trails / Forest Service and BLM | Trails (one line) | `REFERENCE_TRAILS_LABEL` is also the card's title (`MapLayersSheet.kt:301`), which goes. The (i)'s spoken "About Official trails" (`ReferenceLayerCard.kt:52`) goes with the (i) |

**The forecast colour fields are shared, and need a split.** There are two today, both synthetic test layers that a release build
never lists (`MapLayersSheet.kt:174-175`; `MapLayers.kt:372-376`):
- "Test forecast: chicken of the woods (synthetic data)" (`ColourFields.kt:47`)
- "Test forecast: chanterelles (synthetic data)" (`ColourFields.kt:62`)

The one `label` is shown in five places: the switch (`MapLayersSheet.kt:409`), its reorder and opacity descriptions (`:419`,
`:469`), the legend chip's collapsed and expanded text (`MapLegend.kt:68`, `:71`), and a cell's bubble title
(`MapBubbleLayer.kt:99`). Changing only the switch means splitting it: a separate switch label on `ColourFieldSpec` (or in the
section), with the legend and bubble keeping `label`. But Title Case for these two is a wording choice ("of the woods"?
"(synthetic data)"?), and new words are a stop under this dispatch's rules. See Q2.

## 8. (a) How the panel is anchored, on both bar sides and in the landscape L

**The shape the owner asked for (read at `347859d50^:app/src/main/java/com/zynergylabs/forager/app/ui/map/MapChrome.kt:126-193`).**
A full-size box holding two `AnimatedVisibility`s: a scrim (black at 0.32) fading in on `MotionTokens.panelMotionSpec()`, then a
`Surface` panel with three `FilterChip`s, fading and `expandIn(expandFrom = anchor)` in, and the reverse out. It was placed by
`.align(anchor).offset(anchorOffset)`. A chip tap applied the mode and closed it. L0b (`347859d50`, 2026-09-27) replaced it and
`78c90a405` (2026-10-19) deleted it.

**What changed since.** The landscape L came after L0b (dispatch 2026-09-28-160), so the old picker never had to anchor in it.
Its rows have no spacing there (`MAP_ICON_BAR_LANDSCAPE_ROW_SPACING = 0.dp`, `MapIconBarSizes.kt:25`), where portrait has
`Spacing.xs`. The live sibling that already follows today's bar is the add tile, `AddActionTile.kt:97-167`, called at
`CompactMapPickers.kt:68-93`.

**Maps tab (portrait and the L, both sides).** The panel is anchored exactly as the add tile is, with row 4 in place of row 5:
- `anchor = cluster.sideAlignment`: `CenterStart` when the bar is on the left, `CenterEnd` on the right (`MapIconClusterState.kt:144`).
- `anchorOffset = cluster.panelAnchorOffset(density)` (`MapIconClusterState.kt:150-155`): the bar's live side inset and horizontal
  drag, and its live vertical drag (`displayedOffsetPx`). Its centre is measured from the bar itself (`centreInClusterPx`, written
  at `AvailabilityMapIconCluster.kt:598`), so it follows the bar whether the pill sits below it (portrait) or beside "+" (the L).
  Plus the Layers row's own offset from the bar's centre: `mapIconBarRowAnchorOffset(4)` in portrait (52 dp) and
  `mapIconBarRowAnchorOffset(4, MAP_ICON_BAR_LANDSCAPE_ROW_SPACING)` in the L (48 dp) (`MapIconBarSizes.kt:40-45`), chosen by
  `cluster.landscape` as the add tile does (`CompactMapPickers.kt:62`, `:89-91`). These become two constants beside
  `ADD_TILE_ANCHOR_OFFSET(_LANDSCAPE)` (`AddActionTile.kt:186-192`).
- `expandFrom` and `shrinkTowards`: `CenterEnd` on the right and `CenterStart` on the left, so the panel grows sideways out of the
  Layers row's middle, as the old picker did (`expandFrom = anchor`). The add tile uses a bottom corner because it is the last row.
  Layers sits mid-bar.
- The same frame as the add tile: `Modifier.fillMaxSize().padding(controlsPadding)` (`CompactMapPickers.kt:85`), which already
  clears the landscape rail.

The figures are calculated, not measured: the doc at `MapIconBarSizes.kt:34-35` says "Not pixel-exact". The tests will measure
the panel's vertical centre against the Layers row's real centre, on both sides, in portrait and in the L. Where the panel lands on
a real screen goes in the S22 steps.

**Entry map.** Its bar is fixed, on the right only: `MapIconBar(...)` aligned `CenterEnd` with `padding(Spacing.sm)`
(`CartographyEntryReportScreen.kt:625-648`), with default row spacing, and no drag, no side switch and no L (there is one
`MapIconBar` call in that file). The panel is anchored as the old picker was there
(`347859d50^:.../CartographyEntryReportScreen.kt:435-436`): `CenterEnd`, x = `-MAP_ICON_BAR_EDGE_INSET` (which is `Spacing.sm`,
`MapIconBarSizes.kt:19`), y = `mapIconBarRowAnchorOffset(4)`, in the same Box as the bar. While "Use offline tiles" is on, the bar
has no Layers row (`:633`, `mapModePickerEnabled = !useOfflineTiles`), so no panel opens. That is unchanged.

**Motion and reduced motion.** `panelMotionSpec()` (`MotionTokens.kt:56`) for the scrim and the panel, as before. Under reduced
motion, panels "fade or instant" (`docs/motion-spec.md:130`). The panel reads `LocalReduceMotion` (`ReduceMotion.kt:36`) and drops
the expand and shrink, so it only fades. The add tile does not do this today (`AddActionTile.kt:113-147` never reads the local).
I will not change the add tile.

**Back.** The sheet was its own window and took Back itself. The panel is not, so it carries a `BackHandler(enabled = visible)`.
On the Maps tab the existing handler and the fan and bubble flags yield to the add tile through `showActionMenu`
(`AvailabilityCompactMapUi.kt:343`, `:485`, `:529`, `:542`). The panel needs the same yield, by composition order or by the same
flags. I will confirm which in a Back test. On the entry map, `MapBubbleLayer`'s `backEnabled = backEnabled && !showLayersSheet`
(`:621`) keeps its meaning with the panel's flag.

**The scrim.** Composed only while the panel is visible or animating, as the add tile's is, so a long-press on the map with the
panel closed reaches the map (the Surface pitfall). One real long-press test per host.

## 9. (b) Files to create and change (line counts at `e2969abe4`)

**Create (each under 400):**
- `app/src/main/java/com/zynergylabs/forager/app/ui/map/MapTypePanel.kt`: the panel (scrim, chips through `MapModeChipRow`,
  anchoring, Back, reduced motion), with its tags and row-4 anchor constants. About 150.
- `app/src/main/java/com/zynergylabs/forager/app/ui/availability/MapLayersToolsSection.kt`: Tools > Map Layers (the fold, the nine
  switches, Trails' row, colour fields), with `LayerSwitchRow`, `ColourFieldRows` and `OpacityRow` moved from `MapLayersSheet.kt`.
  About 250.
- Tests: `MapTypePanelTest.kt` (both hosts, coordinate touches sampled across each chip, scrim, Back, long-press),
  `MapLayersToolsSectionTest.kt` (fold, persistence across drawer close and tab change, each switch by a real touch, labels), and
  `NoFoldChevronsTest.kt` (each list opens and closes on its header). Each under 400.

**Change:**
| File | Lines | Change |
|---|---|---|
| `ui/map/MapLayersSheet.kt` | 593 | remove the sheet and its tags (about -260); labels move out |
| `ui/map/MapModeChips.kt` | 115 | doc and contrast figures restated for the panel |
| `ui/map/MapIconBar.kt` | 225 | doc wording only |
| `ui/map/JournalEntriesOnMap.kt` | 69 | `JOURNAL_ENTRIES_OVERLAY_LABEL` value |
| `ui/map/layers/ColourFields.kt` | 73 | only if Q2 says split there |
| `ui/availability/CompactMapPickers.kt` | 160 | the sheet call becomes the panel call |
| `ui/availability/AvailabilityCompactMapUi.kt` | 693 | the state renamed; Back yields (a few lines) |
| `ui/availability/AvailabilityScreen.kt` | 722 | the fold flag; pass `mapLayersControls` and the flag to the drawer host (about +4) |
| `ui/availability/AvailabilityScreenDrawerHost.kt` | 129 | two parameters through |
| `ui/availability/AvailabilitySettingsUi.kt` | 284 | two parameters through |
| `ui/availability/SearchControls.kt` | 105 | Map Layers under Trip Planner; chevron removed; hoisted form of `CollapsibleSection` |
| `ui/availability/SearchDropdownSections.kt` | 179 | Month's trailing icon removed |
| `ui/log/CartographyEntryReportScreen.kt` | 787 | the sheet call becomes the panel call; holder per Q1. Net shrink, stays under 800 |
| `ui/log/RecordDetailsParts.kt` | 136 | icon removed |
| `ui/log/EntryContentsPanel.kt` | 312 | icon removed |
| `AppContainer.kt` | 546 | `referenceTrailsSources` removed |
| `MainActivity.kt` | 742 | provider removed |
| comment-only files listed in section 1 | — | wording |

**Delete:** `ui/map/layers/ReferenceLayerCard.kt` (127), `domain/referencetrails/ReferenceTrailsSources.kt` (107),
`domain/referencetrails/ReferenceTrailsManifest.kt` (85), `data/referencetrails/WorkerReferenceTrailsManifestSource.kt` (41),
`data/repository/DataStoreReferenceTrailsDatesStore.kt` (65), and the six test files listed in section 6.

**Not touched:** `AvailabilityCompactScaffold.kt`, which is 1441 lines, already past 800. Nothing above needs it, unless Q1 is
answered "the map behind", which needs a provider and might.

## Confirmed vs inferred
- Confirmed by reading: every `file:line` above.
- Inferred, not run: that the drawer keeps its list composed while closed (from a code comment); that the anchor figures land on
  the row's centre on a phone; that `SundownSettingsTest` is unmoved.

## Could not determine
- How the band and its credit read on the entry map once the panel replaces the sheet. That needs a device.
- Whether Compose's own animator-scale handling already makes the add tile instant under "Remove animations". Not read.

## Premises that were wrong
- "Trip Planner's fold ... resets today": it survives closing the drawer and changing tab, and resets on a trip to Settings and on
  rotation (section 4).
- "The grey offline-tiles chip on the entry map, as today": the offline-tiles grey is not reachable on the entry map, because its
  Layers button is hidden while offline tiles is on (`CartographyEntryReportScreen.kt:633`). The audit index records this
  (`docs/audits/README.md`, 2026-10-23 row, PR #380: "that grey is not reachable on a phone today"), with the owner's "the entry map
  left as it is". Through the real entry point, the entry map's grey chip can be tested for no signal and the fallback, not for
  offline tiles. A test that set offline tiles and opened the panel through the bar could never run.
- The Month picker is not a fold-open list but a dropdown field. Its trailing icon is the arrow in question, and it goes as asked.

## Decided beyond scope
Nothing is built. The plans for "Recent searches" losing its chevron and for the panel fading only under reduced motion follow the
dispatch's own words, and both are listed here so they can be overruled.

## Questions for the owner (stop-and-ask)

**Q1. When does the Trails switch show in Tools?** The switch is offered only while a map is drawing the official-trails band.
The Tools drawer opens over the Maps tab and over the Journal, and the Journal's entry map has its own type choice.
- **A (my recommendation, no new plumbing):** Tools shows Trails when the **Maps tab's** map last drew the band, wherever Tools
  is opened. Over a journal entry whose map has been switched to a different type than the Maps tab, the row can show when that
  entry's map draws no band, or the reverse.
- **B:** Tools follows **whichever map is behind it**. Over the Journal that is the entry's map once it has drawn. This needs a
  shared holder that the entry map writes to, which is a new path.
- **C:** Trails always shows. This reverses the step 8b rule ("never offer a switch that would do nothing").

**Q2. The two forecast test layers' names in Title Case.** They are debug-only test layers, never listed in a release build.
Their name today is also the legend's text and the map bubble's title. Choose one:
- **A:** leave both names as they are (they are not real forecast names);
- **B:** Title Case on the switch only: "Test Forecast: Chicken of the Woods (Synthetic Data)" and "Test Forecast: Chanterelles
  (Synthetic Data)", with the legend and bubble unchanged, so the same layer reads two ways on one screen;
- **C:** the same Title Case everywhere they appear.

**Q3. "Offline Maps" next to "Offline maps".** With the switch retitled, the Tools drawer will show "Offline Maps" (the switch
inside Map Layers) and, lower down, "Offline maps" (the row that opens Records). Amendment 1 says to change only the switch, so
that is the plan. Should the row match?

**Q4. The Layers button's spoken words.** Today TalkBack says "Layers: Trail map. Choose the map type and overlays." The dispatch
asks for map type only. Proposed: "Layers: Trail map. Choose the map type." This needs your word, since new words are a stop.
