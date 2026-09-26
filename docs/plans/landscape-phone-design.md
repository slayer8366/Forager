# Design: the phone held sideways (landscape on short windows)

Planning doc recording the owner's and the planner's decisions for the app on
a phone in landscape. It is a design record and a task spec for later coder
dispatches, not a replacement for the repo's real `CLAUDE.md`, whose standing
principles govern everything below.

Written 2026-09-26 by a coder dispatch (store copy
`prompts/preserved/2026-09-26-34.md`, record intent `2026-09-26-93`) that
wrote the decisions down and made none. Every decision below is attributed:
**O** to the owner, said in the planner session on 2026-09-26; **P** to the
planner, deciding under O4. Where a decision was ambiguous, or where the code
disagrees with how a decision describes it, the question is recorded under
[Open questions](#open-questions) instead of being resolved here.

**Nothing in this document is built.** Every line number below was re-read at
`pre-main` `f7727fe` (the merge of #125). Re-verify before relying on any of
it: a plan's picture of the tree is a claim about the moment it was written
(CLAUDE.md, "A planner's picture of the repository is a claim about the
past").

## What this is

The owner, on the app turned sideways: "we do need to redesign the sideways
feature … how about you design the sideways layout?" Scope: "The whole app
in landscape". The problem: "Looks broken" and "Not designed at all" (O1).

Today a phone turned sideways does not get a landscape version of the phone
layout. It gets the tablet layout. The S22 Ultra in landscape is 823 x 384 dp;
the layout is chosen by width alone, 823 dp is `MEDIUM`, and `MEDIUM` gets
the wide tree — a 360 dp permanent drawer, a top search bar, a tab row and a
360 dp list pane — with none of the compact map's controls. The capture record
shows what that looks like at both rotations.

This design gives a sideways phone the compact redesign, adapted to a short
window: the bottom navigation bar becomes a navigation rail on the
charger-port side, and the rest of the compact tree is rearranged so the
scarce axis — height — goes to content. Tablets and foldables keep the wide
tree unchanged (O3).

## Scope

- **In:** every window shorter than 480 dp (P1), which on a phone means
  landscape. The whole app in that window: the map and its controls, search,
  the navigation HUD, fullscreen, List, Seasonal, the Journal and Records
  tabs, log entry view and edit, Cartography, Album/Gallery, Settings, and the
  tools drawer (O1, P11, P12).
- **In, both orientations:** the tools drawer's close behaviour (P12), because
  the drawer is one component.
- **Out, unchanged:** portrait, apart from P12; tablets and foldables, which
  keep the wide tree (O3); the photo viewer and the in-app camera (P13).

## Evidence — verified by reading the code at `f7727fe`, re-verify before relying on it

**The capture.** `docs/audits/2026-09-26-landscape-capture-record.md`, with
115 non-location files committed beside it under
`docs/audits/assets/2026-09-26-landscape-capture/` (screenshots, `uiautomator`
dumps, and `dumpsys window displays` lines). Its findings L1 to L15 are the
"looks broken" of O1, measured. The ones this design rests on:

- **L1** (record lines 88-94): at rotation 1 the wide tree's search field runs
  89 px into the nav-bar band.
- **L3** (lines 106-118): the Maps tab draws no map in landscape, and the whole
  map cluster is absent at both rotations — no Fullscreen, Reset orientation,
  Center on my location, Map mode, add button, recording or return control,
  or grid-reference strip in any landscape dump.
- **L12** (lines 178-182): search in landscape raises a floating keyboard over
  the panel.
- **L14** (lines 192-202): the camera with the lock on (see O5).
- **L15** (lines 204-211): no app node intersects either punch-hole today.

**Window geometry on the S22 Ultra (SM-S908U), from the capture's window
dumps.** The dispatch placed these files outside the repo, in
`~/Zynergy/forager-landscape-capture/`; they are also committed,
byte-identical (`cmp`), as
`docs/audits/assets/2026-09-26-landscape-capture/r1-window-displays.txt` and
`r3-window-displays.txt`. The lines relied on, quoted:

Rotation 1 (`r1-window-displays.txt`):

```
3:  overrideConfig={1.0 ?mcc0mnc [en_US] ldltr sw384dp w823dp h384dp 450dpi nrml long hdr widecg land ...
23: ... mDisplayRotation=ROTATION_90 ...
26:      mDisplayCutout=DisplayCutout{insets=Rect(75, 0 - 0, 0) ... boundingRect={Bounds=[Rect(0, 512 - 75, 568), ...
27:        InsetsSource id=eda70000 type=statusBars frame=[0,0][2316,84] visible=true flags= sideHint=TOP boundingRects=null
30:        InsetsSource id=facc0001 type=navigationBars frame=[2181,0][2316,1080] visible=true flags= sideHint=RIGHT boundingRects=null
36:        InsetsSource id=7 type=displayCutout frame=[0,0][75,1080] visible=true flags= sideHint=LEFT boundingRects=null
```

Rotation 3 (`r3-window-displays.txt`):

```
23: ... mDisplayRotation=ROTATION_270 ...
26:      mDisplayCutout=DisplayCutout{insets=Rect(0, 0 - 75, 0) ... boundingRect={Bounds=[Rect(0, 0 - 0, 0), Rect(0, 0 - 0, 0), Rect(2241, 512 - 2316, 568), ...
27:        InsetsSource id=eda70000 type=statusBars frame=[0,0][2316,84] visible=true flags= sideHint=TOP boundingRects=null
30:        InsetsSource id=facc0001 type=navigationBars frame=[0,0][135,1080] visible=true flags= sideHint=LEFT boundingRects=null
36:        InsetsSource id=47 type=displayCutout frame=[2241,0][2316,1080] visible=true flags= sideHint=RIGHT boundingRects=null
```

(`...` marks where a quoted line is cut; the full lines are in the files.)
At 450 dpi (2.8125 px/dp) that is:

| Display rotation | System nav bar (3-button) | Cut-out inset band | Punch-hole | Status bar |
|---|---|---|---|---|
| `ROTATION_90` | **right**, x 2181-2316, 135 px = 48 dp | **left**, x 0-75, 75 px = 27 dp | y 512-568 px | top, 84 px = 30 dp |
| `ROTATION_270` | **left**, x 0-135, 135 px = 48 dp | **right**, x 2241-2316, 75 px = 27 dp | y 512-568 px | top, 84 px = 30 dp |

The system navigation bar sits on the charger-port edge at both rotations; the
cut-out sits on the opposite edge.

**The code.**

- `ui/adaptive/WindowWidthClass.kt:24-46`: `currentWindowWidthClass()` reads
  `LocalConfiguration.current.screenWidthDp` only (line 40). Height is never
  read; nothing in the file mentions it. Breakpoints 600 dp and 840 dp (lines
  24-25).
- `ui/availability/AvailabilityScreen.kt:2081`:
  `if (windowWidthClass == WindowWidthClass.COMPACT)` — the compact tree
  (`ModalNavigationDrawer` plus bottom nav); every other width gets the
  `PermanentNavigationDrawer` at `:2117`.
- `AvailabilityScreen.kt:2246`: `PERMANENT_DRAWER_WIDTH = 360.dp`.
- `ui/availability/AvailabilityResultsUi.kt:303`:
  `READABLE_CONTENT_MAX_WIDTH = 640.dp` (declared `private` to that file).
- `ui/log/CameraArrangement.kt:125`: `cameraArrangement(lockToPortrait,
  windowIsLandscape, displayRotation)`. Its doc comment (lines 28-35) states
  the mapping this design reuses: on a portrait-natural phone the port edge is
  the screen's right at `ROTATION_90` and its left at `ROTATION_270`.
- `ui/availability/AvailabilitySearchUi.kt:875`: the wide top bar pads
  `WindowInsets.statusBars` only, which is why L1's field reaches the nav bar.
- `AvailabilityScreen.kt:2088`: the compact drawer's `gesturesEnabled = false`.
- `AvailabilityScreen.kt:345-351`: `CompactTab` — List, Seasonal, Maps,
  Journal, Tools, in that order. Tools opens the drawer rather than switching
  tab (its doc comment, lines 331-343).
- `AvailabilityScreen.kt:3157-3162`: `MapIconClusterPositionState` — the
  cluster's user-chosen offset, `isOnLeftSide` and `isMinimized`, held in
  `remember` at `:803`, session-only (doc comment, lines 3140-3144).

**The plans this supersedes for short windows.**

- `docs/plans/map-redesign.md:57-61`: the redesign is scoped to `COMPACT`.
- `map-redesign.md:237-274`: the `MEDIUM` defect, recorded 2026-08-25 and
  deferred until a medium-width device was in the loop; the fix it names is a
  `NavigationRail` with the drawer made modal.
- `map-redesign.md:276-326`: the open question on where the destinations live
  in wide windows.
- `docs/plans/understory-design-system.md:555`: "Medium (600–840dp) —
  deferred, not built."
- `understory-design-system.md:832`: "Medium-window navigation rail — not
  approved."

The binding reason both plans gave for deferring was that no medium-width
device was in the loop (`map-redesign.md:264-271`). A sideways phone is one:
the S22 Ultra is in the loop, and the capture above is from it.

## Decisions — from the project owner

- **O1. Redesign the sideways layout, the whole app.** "we do need to
  redesign the sideways feature … how about you design the sideways layout?"
  Scope: "The whole app in landscape". Problem: "Looks broken" and "Not
  designed at all".
- **O2. The navigation stays on the port side.** "Keep the icon strip at the
  port side. That's my design." The owner clarified that "icon strip" means
  the bottom navigation bar. On a sideways phone the bottom navigation bar
  becomes a vertical navigation rail on the charger-port side, so it swaps
  sides between the two landscape rotations.
- **O3. "The redesign, adapted."** A sideways phone gets the compact redesign,
  with layout chosen by height as well as width. Tablets and foldables keep
  the wide tree unchanged.
- **O4. The planner designs the rest.** "You're the designer so pick something
  based on your mobile UX research." Every P decision below is made under
  this.
- **O5. The camera's orientation behaviour is by design.** The camera following
  the phone's orientation with auto-rotate off, and its effect on
  `user_rotation`, are intended (capture L14). "Lock camera to portrait" is
  the way to disable it.

## Decisions — from the planner, under O4

Where the dispatch recorded no reason for a decision, this says so rather than
supplying one.

### P1. Which windows count as short

- A window shorter than 480 dp is **short**, whatever its width. 480 dp is
  Material 3's compact-height boundary.
- A short window uses the compact tree, adapted as below.
- Every other window keeps the existing rule: `COMPACT` below 600 dp wide, the
  wide tree otherwise.
- The mechanism is a new function or class beside `WindowWidthClass`, not a
  conditional threaded through existing code.

*Reason:* the boundary is Material 3's own; the mechanism follows CLAUDE.md,
Building ("New capability is a new function, class, or path").
See open question 1.

### P2. Which side is the port side

- The port side comes from display rotation, using the same rotation-to-edge
  mapping `cameraArrangement` uses (`CameraArrangement.kt:125`, doc comment
  lines 28-35).
- `ROTATION_90` puts the port on the right; `ROTATION_270` puts it on the
  left.
- Portrait is unchanged: the bottom bar stays on the port edge.

*Reason:* this matches where the system navigation bar sits, as the capture
observed (table above). See open question 10.

### P3. The rail

- Material 3 `NavigationRail`, standard 80 dp, on the port side, next to the
  system navigation bar.
- It holds the same destinations as the bottom navigation bar, in the same
  order, top to bottom (today List, Seasonal, Maps, Journal, Tools —
  `AvailabilityScreen.kt:345-351`).
- The rail's header slot holds a Search action (P8).
- The rail takes the `navigationBars` inset on its side.

*Reason:* O2, and P14's reasons for a rail over a bottom bar.

### P4. The punch-hole side

- No interactive element sits within the `displayCutout` inset.
- Map tiles may draw under it; controls are padded by it.

*Reason:* none recorded beyond O4. See open question 2.

### P5. The top edge

- The status bar stays visible.
- No full-width bars in landscape. Every top element is sized to its content.

*Reason:* height is the scarce axis — see [Prior art](#prior-art): the Gaia
GPS and Google Maps reports of stacked horizontal bars squashing the map, and
the Navigation SDK's half-width, start-aligned landscape footers.

### P6. The icon cluster

- In landscape its default side is the punch-hole side, inside the cut-out
  inset.
- Drag, snap and minimise are unchanged.
- Its position, side and minimised flag are remembered separately for
  portrait and for landscape, so turning the phone never discards what the
  user set.
- Persistence is within the session only, the same as today
  (`MapIconClusterPositionState`, `AvailabilityScreen.kt:3140-3162`).

*Reason:* separate memory follows CLAUDE.md, UX defaults (what the user has
set survives within a session; an unrequested reset is a bug). No reason
recorded for the default side beyond O4. See open questions 2 and 3.

### P7. The compass/elevation strip

- Top centre of the map area, sized to its content rather than `fillMaxWidth`
  (today the strip's inner `Row` is `fillMaxWidth`, `AvailabilityScreen.kt:4428-4436`, inside `CompassElevationStripContent` at `:4383`).
- It stays a plain `Box` that passes touches through.

*Reason:* P5. Coder's cross-reference, not a reason the planner gave: CLAUDE.md
"Known pitfalls" on `Surface` intercepting touches across its full bounds is
the rule the second bullet keeps. See open question 13.

### P8. Search

- The rail's Search action opens a side sheet anchored to the rail side.
- The sheet is 360 dp wide, or the available width if smaller, and full height
  below the status bar.
- It holds the search field and its results/dropdown.
- It closes on Back, on the Search action again, or on a scrim tap.
- There is no search bar over the map in landscape.

*Reason:* P5 (no full-width bar). See open questions 6, 7 and 12.

### P9. The navigation HUD

- Docked at the top of the map area, on the rail side, at most 360 dp wide.
- While it shows, the compass strip centres in the remaining width.

*Reason:* P5. See open question 7.

### P10. Fullscreen

- Hides the rail, sliding it toward the port edge. The system navigation bar
  remains.
- Entry, exit and Back behave as in portrait.
- The persisted fullscreen flag is unchanged
  (`MapPreferencesRepository.getMapFullscreen`; CLAUDE.md, UX defaults).

*Reason:* none recorded beyond O4.

### P11. Other destinations

- List, Seasonal, the Journal tabs and Records sub-tabs, log entry view and
  edit, Cartography, Album/Gallery and Settings lay out beside the rail.
- Content is capped at `READABLE_CONTENT_MAX_WIDTH` (640 dp,
  `AvailabilityResultsUi.kt:303`), centred in the remaining width, and scrolls
  vertically.

*Reason:* none recorded beyond O4; the 640 dp cap is the existing
readable-width constant. See open questions 8 and 11.

### P12. The tools drawer

- Opens from the rail side.
- Closes in one action on a scrim tap or on Back, **in both orientations**.

*Reason, as the planner recorded it:* this fixes both drawer findings from
terminal `2026-09-26-91`'s Flags, because the drawer is one component, and it
is **a bug fix that applies to portrait too**:

- a scrim tap does not close the compact drawer (`AvailabilityScreen.kt:2088`,
  `gesturesEnabled = false`);
- Back from the drawer's Settings panel switches to Search instead of closing.

Both findings were each seen once on the device at build `1.0.899+g429edb96`.
The code disagrees with part of this characterisation; see open questions 4
and 5.

### P13. Unchanged

- The photo viewer: already full-window, with controls on `safeDrawing`
  (`ui/log/PhotoViewerDialog.kt:145`; capture L13: Close clear of the cut-out and nav bands at both rotations).
- The in-app camera: it has its own landscape arrangement
  (`cameraArrangement`), and O5 applies.

### P14. Departure from the platform default

- Android's `NavigationSuiteScaffold` default shows a bottom navigation bar
  whenever height is compact.
- This design uses a side rail instead.

*Reasons:*

1. O2 — the owner's design.
2. Material's navigation-rail guidance for phones wider than 600 dp in
   landscape.
3. The map-app evidence that stacked horizontal bars squash the map in
   landscape (Gaia GPS, Google Maps community; [Prior art](#prior-art)).
4. The system's own 3-button bar sitting on the same port edge (window dumps
   above), so the rail and the system bar form one edge of controls rather
   than two.

This app does not use `NavigationSuiteScaffold` today (no reference in `app/`
or `gradle/libs.versions.toml`); the departure is from the platform's
documented default, not from existing code.

### P15. What this fixes by construction

One tree in both orientations removes two failures the planner's code pulse
found:

- a navigation started in portrait has no stop/exit control after rotation;
- portrait state is carried into the wide tree, where the UI cannot show it —
  for example a persisted fullscreen flag making Back toggle an invisible
  state.

The pulse found these by reading `AvailabilityScreen.kt:771-783`, `:896-943`
and `:2915-3126`. **They were not run.** Consistent with that reading at
`f7727fe`: the comment at `:770-774` says the fullscreen flag "stays false"
on `MEDIUM`/`EXPANDED`, while `:793-799` applies a persisted value on load
whatever the width; `:2915` onward is the wide tree's `MapTab`.

## Verification plan

- **Robolectric** at the phone's real landscape shape, `w823dp-h384dp-land`,
  at both `ROTATION_90` and `ROTATION_270`. Assert:
  - the rail is on the port side;
  - no control intersects the cut-out band. Robolectric reports zero insets
    (CLAUDE.md, "Robolectric reports zero window insets"), so this is
    checkable only on the device; the test asserts *which side* the port and
    the punch-hole are on, not inset values;
  - the cluster's default side;
  - content-width top elements;
  - the search sheet;
  - the drawer closes on a scrim tap and on Back, in both orientations.
- **Coordinate touches.** `performTouchInput` long-presses on the map beside
  every control, per CLAUDE.md (a semantic `performClick` asserts wiring, not
  routing; any layout over a map needs a real long-press test).
- **Device check** on the S22 Ultra at both rotations, for insets and the
  cut-out — the part no Robolectric run can show.
- **Screenshots** go to `~/Zynergy/forager-landscape-final/` for the owner.
  The owner deferred inline delivery; it waits on a Claude-kit change.

## Build order

- **B1.** Height-aware window classification (P1), the port-side rail (P2,
  P3), and correct insets at both rotations.
- **B2.** The map: cluster (P6), strip (P7), search sheet (P8), HUD (P9),
  fullscreen (P10).
- **B3.** The other destinations (P11) and the tools drawer (P12).
- **B4.** The device check at both rotations.

## Prior art

Cited as the planner gave them. The dispatch that wrote this document had no
web access and did not re-fetch these; the quotations are the planner's.

- Android Developers, "Build adaptive navigation" — the
  `NavigationSuiteScaffold` rule (P14).
  https://developer.android.com/develop/adaptive-apps/guides/build-adaptive-navigation
- Material 3, Navigation rail guidelines (P3, P14).
  https://m3.material.io/components/navigation-rail/guidelines
- Gaia GPS: Android landscape is "three horizontal ribbons with controls",
  leaving the map "little over an inch high" (P5, P14).
  https://www.territorysupply.com/onx-vs-gaia-gps
- Google Maps community: the landscape map is "too wide and not high enough"
  (P5, P14). https://support.google.com/maps/thread/10909291
- Google Navigation SDK: landscape footers are half width and start-aligned
  (P5, P9).
  https://developers.google.com/maps/documentation/navigation/android-sdk/controls
- OsmAnd: separate landscape widget layouts, with buttons on the sides (P6).
  https://osmand.net/docs/user/widgets/map-buttons/ and
  https://github.com/osmandapp/OsmAnd/issues/20155

## Open questions

Recorded, not resolved. Each needs the planner or the owner before the build
step it names.

1. **Short tablets and foldables (P1 against O3).** P1 makes any window
   shorter than 480 dp short "whatever its width". O3 says tablets and
   foldables keep the wide tree unchanged. A tablet or unfolded foldable in a
   window shorter than 480 dp (split screen, a freeform window, a foldable's
   outer screen in landscape) would get the compact tree under P1 and the wide
   tree under O3. Which wins? Before B1.
2. **"Inside the cut-out inset" (P6 against P4).** P4 says no interactive
   element sits within the `displayCutout` inset; P6 puts the cluster's
   default side "inside the cut-out inset". This document reads P6 as
   *inboard of* the inset (padded by it), which is the only reading
   consistent with P4, but the dispatch does not say so. Before B2.
3. **What the landscape side memory stores (P6).** Today the side is an
   absolute `isOnLeftSide` (`AvailabilityScreen.kt:3160`). P6 remembers
   landscape separately from portrait but not per rotation. Turning from
   `ROTATION_90` to `ROTATION_270` moves the punch-hole and the port to the
   opposite screen edges. Does a user-chosen landscape side stay on the same
   screen edge, or on the same port/punch-hole edge? And the default side
   swaps between the two rotations — does it keep swapping until the user
   first snaps it? Before B2.
4. **Back from the drawer's Settings is documented as intended (P12).** P12
   records one-action Back close as a bug fix. The code documents the current
   behaviour as deliberate: the compact Settings doc comment
   (`AvailabilityScreen.kt:2478-2479`) says the drawer's own `BackHandler`
   "unwinds `showSettings` back to the rest of the Tools drawer", and that
   handler is `BackHandler(enabled = showSettings)` at `:2848`, with the
   precedence explained at `:2841-2846`. (The drawer returns to its main
   panel, which the device reading called "Search".) So the second finding is
   the designed drill-in back step, not a defect; P12 is a change to that
   design, applied in portrait too. The decision stands as written; whether
   Back from a drilled-in panel should close the drawer in one step or
   unwind one level first is the question. Before B3.
5. **The scrim-tap finding is a real mismatch with intent.** The close bar's
   doc comment (`AvailabilityScreen.kt:2335-2338`) names "tapping the scrim"
   as a way out, and the drawer's close effect (`:865`) lists "scrim tap"
   among the ways it closes, yet the device did not close on a scrim tap.
   Whether Material3 gates the scrim on `gesturesEnabled` is unverified
   against the AndroidX source (terminal `2026-09-26-91`, Flags). If it does,
   the fix touches the reason `gesturesEnabled = false` exists — the map pans
   under a horizontal drag (`:2084-2087`, and `map-redesign.md:104-108`). B3
   should see the failure before the fix (CLAUDE.md, Bug fixing).
6. **"Opens from the rail side" and "a side sheet" (P8, P12).** At
   `ROTATION_90` the rail is on the right. Whether Material3
   `1.5.0-alpha26` (`gradle/libs.versions.toml:19`) offers a modal drawer or a
   modal side sheet anchored at the end edge is unverified; nothing in the
   tree implements one (no side-sheet file in `git ls-files`). This is an
   implementation question for B2/B3, recorded because a component gap could
   push back on the design.
7. **Search sheet and HUD on the same side (P8, P9).** Both anchor to the
   rail side. While navigating, opening Search puts a 360 dp sheet over a HUD
   of up to 360 dp. The design does not say how they stack. Before B2.
8. **Settings and Album/Gallery (P11 against the compact tree).** P11 lists
   Settings and Album/Gallery as destinations that lay out beside the rail. In
   the compact tree Settings is not a destination: it sits inside the Tools
   drawer (`AvailabilityScreen.kt:2470-2483`; `CompactTab` doc,
   `:331-343`), and the Album is the Journal's (capture record, surfaces
   table; "Photo Gallery" is a wide-tree entry). Does P11 mean Settings as it
   renders inside the drawer (P12), or a Settings screen beside the rail?
   Before B3.
9. **The keyboard in a 384 dp window (P8).** The capture found search raising
   a floating keyboard over the panel (L12). The design does not say how the
   search sheet behaves with the IME up, which on this window covers most of
   its height. Before B2.
10. **Sharing the port-side mapping (P2).** `cameraArrangement` is `internal`
    in `ui/log/` and returns a camera arrangement, not an edge. Whether the
    rail derives its side from it or from a new function stating the same
    mapping is an implementation choice for B1; P1's rule (a new function, not
    a conditional in existing code) bears on it.
11. **`READABLE_CONTENT_MAX_WIDTH` is private (P11).** It is `private` to
    `AvailabilityResultsUi.kt` (line 303). Using it across destinations means
    changing its visibility or its home. Implementation, for B3.
12. **Robolectric and display rotation (verification plan).** Whether
    Robolectric at `w823dp-h384dp-land` can report `ROTATION_90` and
    `ROTATION_270` distinctly to the code under test is unverified. If it
    cannot, the port-side assertions need the rotation injected. Before B1's
    tests.
13. **Does P7 change portrait?** P7 does not say "in landscape". P5 is scoped
    to landscape, and `understory-design-system.md:548` keeps the compact
    strip full-width. This document reads P7 as landscape-only, which the
    dispatch does not state. Before B2.

## Drift noted while re-checking the citations

Recorded only; no earlier text in either plan is edited.

- `map-redesign.md:245` names the drawer width constant `DRAWER_PANEL_WIDTH`;
  the code's name is `PERMANENT_DRAWER_WIDTH` (`AvailabilityScreen.kt:2246`).
- `map-redesign.md:254` and `:290` speak of six `CompactTab` destinations, and
  `understory-design-system.md:548` and `:715` of a "six-tab bottom nav"; `CompactTab` has
  five (`AvailabilityScreen.kt:345-351`) since Settings moved behind Tools
  (`:2482-2483`).
- The dispatch cited the capture's window dumps as outside the repo; they are
  also committed (see Evidence).

## Resolutions — added 2026-09-26

Appended by the B1 build dispatch (store copy
`prompts/preserved/2026-09-26-35.md`, record intent `2026-09-26-96`). Nothing
above this heading was changed. The coder recorded these and decided none of
them. R1 is the owner's ruling. R2 to R11 are the planner's, as written in the
B1 dispatch under O4. R12 to R18 are the planner's, from its ruling on the B1
coder's stop (planner log line 1427, quoted in full in intent `2026-09-26-96`),
also under O4.

- **R1. P1 against O3 (open question 1).** Owner: "Classify by window". Any
  window under 480 dp tall gets the sideways-phone layout, whatever the
  device. A full-screen tablet is never that short.
- **R2. P12 corrected (open questions 4 and 5).** Back unwinding the drawer's
  Settings panel to the rest of the Tools drawer is documented behaviour
  (`AvailabilityScreen.kt:2478-2479`, `:2848`) and stays. The planner's "bug"
  label was wrong. The only fix P12 keeps is that a scrim tap closes the
  drawer, as `:865` and `:2335-2338` expect. It must not re-enable
  swipe-to-open over the map. That fix belongs to B3, not B1.
- **R3. P6 (open question 2).** "Inside the cut-out inset" means inboard of
  it: the cluster sits clear of the cut-out.
- **R4. Cluster side memory (open question 3).** In landscape the cluster's
  side is remembered as *port side* or *punch-hole side*, not left or right.
  Turning between 90 and 270 keeps it on the same device edge. Portrait keeps
  today's memory. B2.
- **R5. Search sheet and HUD (open question 7).** They share the rail side.
  The search sheet is modal and covers the HUD while it is open. B2.
- **R6. Keyboard (open question 9).** In the search sheet the field sits at the
  top and results shrink with the IME. No full-screen extract UI. B2.
- **R7. Information architecture (open question 8).** Settings and
  Album stay inside the tools drawer, as in the compact tree. No new
  destinations.
- **R8. P7 is landscape-only (open question 13).** Portrait's compass strip is
  unchanged.
- **R9. Shared helpers (open questions 10 and 11).** The rotation-to-edge
  mapping is shared with the camera, with no camera behaviour change (B1).
  `READABLE_CONTENT_MAX_WIDTH` (`AvailabilityResultsUi.kt:303`, `private`)
  becomes shared when B3 needs it, not in B1.
- **R10. Implementation checks, made while building (open questions 6 and
  12).** Whether Material 3 `1.5.0-alpha26` can anchor a drawer or side sheet
  to the end edge (B2/B3 need it; B1 only records what it finds), and whether
  Robolectric can set `ROTATION_90` and `ROTATION_270` distinctly. B1's
  findings are in its completion report.
- **R11. The drift list** above stays as recorded. No edits to older plans.
- **R12. The rail is a layout region, not an overlay.** In a short landscape
  window `compactMainScaffold` lays out one opaque `NavigationRail` on the
  port edge and the content beside it. Neither `ForagerBottomNav` call site
  renders there; the map `Box` and all its controls sit in the area beside the
  rail. *Reason (planner):* a rail is a layout region in Material 3; height,
  not width, is the scarce axis, so 80 dp of width costs little; and it avoids
  the rail covering the cluster at `ROTATION_90`, where both would otherwise
  sit on the right edge. This supersedes the B1 dispatch's "at both call
  sites".
- **R13. Fullscreen in a short landscape window — interim, until B2.** While
  the Map tab is fullscreen the rail is hidden, with no animation, and the
  content picks up the `navigationBars` inset on the port side so no control
  sits under the system bar. P10's slide toward the port edge stays in B2.
- **R14. No Search action in the rail header in B1.** It comes with P8 in B2.
- **R15. What "landscape" means.** `LocalConfiguration.orientation ==
  ORIENTATION_LANDSCAPE`, the camera's own test (`InAppCameraDialog.kt:201`).
- **R16. Where the mapping lives.** `ScreenEdge` stays in
  `ui/log/CameraArrangement.kt`. A new `ui/adaptive` function holds the
  rotation mapping, and `cameraArrangement` calls it.
- **R17. The punch-hole side.** The map content is padded by
  `displayCutout`, so tiles do not draw under the cut-out (P4 permitted it;
  this does not use that permission in B1).
- **R18. No stale bottom band.** `bottomNavHeightPx` is 0 while the rail
  shows, so nothing reserves space at the bottom for a bar that is not there.
