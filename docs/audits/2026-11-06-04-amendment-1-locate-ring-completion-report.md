# Dispatch 2026-11-06-04, Amendment 1: a ring around the icon bar's locate row (completion report)

Hazel Cairnwright (coder, Opus), 2026-11-06 (Pacific). RECORD -2186. Branch `journal-locate-outline`, cut from origin/pre-main
1f6901a11 (checked against the remote). PR #506 to pre-main, ready, not merged.

The owner, to "The journal map's locate button is one row of the shared icon bar, which already has a thin outline as a
whole. What do you want?": "(b) Ring it on both maps" (the option: "A 1 dp Bark/cream ring around the locate row on the
journal map and the Maps tab, so both bars stay the same").

## Where it is drawn

The locate control is the "Center on my location" row of `MapIconBar` (`ui/map/MapIconBar.kt`), a `MapBarIconButton`.
`MapIconBar` has two production callers:

- the Maps tab, `CompactMapIconCluster.kt:58` (portrait, landscape and the landscape L, fullscreen or not; one call);
- the journal entry's map, `CartographyEntryReportScreen.kt` (about line 625), only while that map is full screen.

Both draw the same row, so the one line gives both the ring.

## What landed

- `ui/map/MapBarIconButton.kt` (172 to 183 lines): an optional `ring: BorderStroke?`, default `null`. When it is set, a
  circle the size of the bar's round badges (`MAP_ICON_BAR_FILL_DIAMETER`, 36 dp, centred, 6 dp in) is drawn with that
  border inside the button's face, under the icon. It has no click or pointer input, so the 48 x 48 touch box is unchanged.
  It dips with the icon on a press, as the badges do.
- `ui/map/MapIconBar.kt` (225 to 232 lines): the locate row passes `ring = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface)`.
  That is #505's Re-center outline, the colour the selected map type chip's outline uses. No hex is copied. The bar's own
  outline, its fill, the other rows, sizes, places and opacity are unchanged.
- `app/src/test/.../ui/map/LocateRowRingTest.kt` (new, 223 lines): five cases.

## Evidence

All runs are on CI. No Gradle was run on the laptop.

- **Red before.** Run 42338309084 (workflow_dispatch, filter `*LocateRowRing*`, tests only, 45867485). 5 tests, 4 failed,
  as predicted: on both maps, day and night, "a #3B2E24 (or #EDE3D0) ring is drawn; the edge reads [...]". The edge read
  only the bar's outline composite and the bar's fill. The touch case passed, as predicted.
- **Green after.** PR run 42339187561 at fe2f49c9, every job success. Its JUnit XML, compared by task, class and test name
  with pre-main's latest run (42337576510 at 1f6901a11, this branch's base):

  | Run | Cases | Pass | Skip |
  | --- | --- | --- | --- |
  | pre-main | 6846 | 6822 | 24 |
  | this PR | 6851 | 6827 | 24 |

  No case is only on pre-main. The five `LocateRowRingTest` cases are only on this PR. No case changed status. Pre-main's
  artifact has 1001 XML files, this PR's 1002.
- **What was measured** (the cases' MEASURED lines): on both maps, day and night, every side of the ring reads the ruled
  colour exactly for 3 px, starting 18 px in. At xxhdpi that is ceil(1 dp) wide at exactly 6 dp. The locate row stayed
  48 x 48 dp. The reset-orientation row beside it carries no ring on any side.
- **Revert check.** Throwaway branch `journal-locate-outline-revert` at 75efc514: fe2f49c9 with the one `ring = ...` line
  deleted (`git diff --stat`: one file, one deletion). It was made in a separate worktree, so the forward file was never
  edited. A copy was saved first anyway; afterwards the forward file matched the copy, and origin/journal-locate-outline
  still carries the line.
  - Run 42339188735, filter `*LocateRowRing*`. Its build log has no `e: file` compile error, and the test task ran ("5 tests
    completed, 4 failed"; the XML is timestamped 2026-11-07T04:41:27 UTC, inside that run).
  - The four failures are the ones this revert can cause: "Maps tab, day, locate row, left side: a #3B2E24 ring is drawn;
    the edge reads [#7E7368 x3, #F5F1E5, ...]", the same for the journal map, and the same at night for #EDE3D0.
  - The touch case passed.
- **Coordinate touches.**
  - The journal map: real touches at five points across the locate row (centre, and 8% in from each side, inside and outside
    the ring). Each asks for the location once (`getCurrentLocation`).
  - The Maps tab: the existing coordinate touches in `AvailabilityScreenMapIconStackTest` pass on this PR's run, unchanged:
    the locate row's centre, 12 dp toward the map, 11 dp toward the screen edge, 12 dp above and 12 dp below. Its semantic
    `the locate-me icon calls onLocateMe` is skipped, as on pre-main.
  - These checks pass the same before and after the ring, by design: they guard the touch area and are no evidence of the
    ring itself.
- **Scan.** `python3 -I ~/.forager-private/scan.py stdin-diff` read 0 on every count on every push.

## Device check steps for the planner

Device-only: Robolectric's pixels are its own renderer's. On the S22, with the app's Night Mode off:

1. Maps tab: screenshot the icon bar. The locate row (crosshair) has a thin round ring inside it, about the size of the green
   Add badge. Zoomed in, sample it: #3B2E24, about 4 px wide (3.75 px per dp). The other rows have none, and the bar's own
   outline is as before.
2. Tap the ring's edge and the row's corners: the map centres on you, as before.
3. Open a journal entry with a map, tap the map to go full screen: the same ring on its locate row, sampled #3B2E24. Tap it:
   the map centres on you.
4. Turn Night Mode on (Settings) and repeat 1 and 3: the ring samples #EDE3D0.
5. Landscape, Maps tab (the L): the ring as in 1, on the locate row.

## Disclosure

### Confirmed and inferred

- Confirmed from code: one row draws locate on both maps; `MapIconBar` has two production callers; the ring reuses
  `onSurface` at 1 dp, as #505.
- Confirmed by CI: the ring's colour, width and place on the drawn pixels on both maps, day and night, in portrait; none on
  the reset row; red before, green after, revert red for its own reason; touches reach the row.
- Inferred, not tested: landscape, the L and the Maps tab's fullscreen. The row takes no orientation input, so they draw the
  same row; no case of this test composes them.

### Could not determine

- How the ring reads over real map tiles, and beside the bar's outline, on a phone. That is device-only (steps above). The
  phone was not touched.

### Premises that were wrong

- The first amendment's premise, that the journal map has its own locate button in `CartographyEntryReportScreen.kt`. It is
  the shared bar's row. Reported as a stop, and corrected by the owner's "(b) Ring it on both maps". The corrected base
  (1f6901a11 or later) matched the remote.

### Decided beyond scope

- **The ring's shape and size:** a circle at the badge diameter (36 dp). The owner's words were "ring" and "1 dp Bark/cream".
  The circle is the bar's existing round shape (the Add and Record badges, `MapBarHighlight.BADGE`). A rounded square would
  instead follow the press highlight. If the owner wants a different shape or size, it is one constant.
- The ring is a parameter on `MapBarIconButton`, not a branch inside it, so no other row changes. `1.dp` is written in place,
  as #505 did, rather than shared.
- Files checked against nav-card-kind-line (#498), next-turn (#500), revert-chip-colours (#488) and the other open PR
  branches before each edit: none touches `MapIconBar.kt`, `MapBarIconButton.kt` or the new test.
  `docs/audits/README.md` was not touched.
