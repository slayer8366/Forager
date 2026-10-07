# Motion scout: what changes on screen without animating (UI sweep 1)

Dispatch 2026-09-28-650 (RECORD intent -650; preserved at `prompts/preserved/2026-10-07-05.md` on
branch `records-after-173`). Read-only survey, written 2026-10-07 (UTC) against `origin/main` at
`aa79f25a` (PR #189), which matched the dispatch's stated base. No code changed, no Gradle, no phone.

## In short

Most of the app changes instantly. A few places already move: the bottom bar, rail and map icon
cluster slide in and out with fullscreen, the "+" add tile fades and grows, the search dropdown
expands, the marker fan spreads, the location dot glides, and the map camera eases into and out of
navigation. Everything else swaps in one frame.

- **Changing tabs** (List, Seasonal, Maps, Journal), and almost every move from a list to a detail page
  inside Journal, is an instant swap. No screen in the app slides or fades into another.
- **The map icon bar's press highlight** is a plain 48 x 48 square on every row. The bar has rounded
  ends, but its middle rows are square, the round green Add badge and red Record badge sit inside a
  square highlight, and in landscape the square's corners stick out past the bar onto the map.
- **Things that appear on the map**, such as the bubble on a tapped find, the filter chips, the legend,
  the centre pin with OK/Cancel, the Return to Route pill and the sundown line, pop in and out.
- **Icons that change state** swap their picture instantly, with no in-between. This covers fullscreen,
  record and stop, return and cancel, the camera's flash, timer and grid, and the search icon.
- **Lists** do not animate rows being added, removed or reordered. A deleted row vanishes and the rows
  below jump up, and on Undo it pops back.

Two things the owner should know before choosing:

1. **The motion system exists but is barely used.** `ui/motion/MotionTokens.kt` holds named motion
   styles: press feedback, panels, navigation chrome, markers and others. Only the map and shell files
   call it, and the whole Journal, camera and backup area uses none of it.
2. **The reduced-motion setting is never wired in.** `LocalReduceMotion` (`ui/motion/ReduceMotion.kt:23`)
   is declared but never provided anywhere. Only the marker fan checks the phone's animation setting
   (`map/fanout/MarkerFanOutState.kt:74-86`), and the restore page checks it its own way
   (`backup/RestoreLoadingPage.kt:96`). Any new animation would have to decide how it honours that
   setting. `docs/motion-spec.md` section 4 already says what reduced motion should look like.

This is a list to choose from, not a design. No durations, curves or proposals are given.

## How to read an item

Each item gives:

- an ID and a category:
  1. moving between screens
  2. presses and toggles
  3. things that appear or disappear
  4. a value that jumps
  5. anything else
- **Now:** what the user sees, written as tap > shows.
- **Code:** where it lives. Paths are relative to `app/src/main/java/com/zynergylabs/forager/app/ui/`.
- **Reuse:** what already animates that could serve.
- **Risk:** what could go wrong.
- **Phone only:** what code cannot show.

Two risk flags recur:

- **[touch]**: the item is drawn over the map, and animating its size, position or presence changes
  where it catches touches. A `Surface`, `clickable` or `pointerInput` takes touches across its whole
  layout box (CLAUDE.md, the `Surface` pitfall). An exit animation keeps that box catching touches
  until the animation ends.
- **[80%]**: the item is map chrome under the "nothing fully obstructs the map" rule
  (`MAP_CHROME_OVER_MAP_ALPHA`, `map/MapChrome.kt:268`). A fade or crossfade changes how dense it
  looks partway through. Two 0.8 layers crossing each other show about 0.96 while they overlap.

Where an item comes from the order of modifiers rather than from reading the library source, it says
"inferred". The survey was done by reading code, by me and by three read-only sub-surveys (shell,
map, Journal). I re-read the lines for the items in the top-five list and a sample of the rest. Every
line number is as of `aa79f25a`.

## What already animates (the app's existing patterns)

| What | Where | Style |
|---|---|---|
| Theme motion scheme: `MotionScheme.expressive()`, marked provisional | `theme/Theme.kt:123`, `:148` | base for every MotionTokens spec |
| Named motion styles (feedback, panel, navigation, marker, selection, reveal, route morph, data overlay) | `motion/MotionTokens.kt:25-95` | most have no caller |
| Maps-tab bottom nav slides away and back with fullscreen | `availability/AvailabilityCompactMapUi.kt:1119-1122` | `AnimatedVisibility` slide, navigation style |
| Landscape rail slides with fullscreen | `availability/AvailabilityCompactMapUi.kt:1156-1159` | same |
| Search bar on Maps slides with fullscreen | `availability/AvailabilityCompactScaffold.kt:1112-1115` | slide, panel style |
| Map top inset and attribution insets glide | `availability/AvailabilityCompactScaffold.kt:421-426`, `:652-687` | `animateDpAsState` |
| Search dropdown expands and fades | `availability/AvailabilityCompactScaffold.kt:1375-1378` | expand + fade, panel style |
| Icon cluster minimise and restore slide | `availability/AvailabilityMapIconCluster.kt:537-558` | slide, navigation style |
| Icon cluster glides up and down when room changes | `availability/AvailabilityMapIconCluster.kt:501-507` | `Animatable`, navigation style |
| "+" add tile: scrim fade, tile fade and grow from its anchor | `availability/AvailabilityMapControlsUi.kt:679-713` | fade + expand, panel style |
| Mode picker with the same fade and grow (no caller, dead code) | `map/MapChrome.kt:159-182` | |
| Marker fan spreads and folds, skipped under reduced motion | `map/fanout/MarkerFanOutState.kt:74-86` | hand-written tween |
| Location dot glide | `map/SightingsMap.kt:1725-1731` | MapLibre duration multiplier |
| Camera eases into and out of navigation | `map/NavigationView.kt:448`, `:456`, `:478`, `:497`, `:541` | MapLibre camera |
| Camera eases north on reset orientation, and zooms on first GPS fix | `map/SightingsMap.kt:1224`, `:1879` | `easeCamera` |
| Nudge give and spring-back | `map/NudgeElastic.kt:164` | `ValueAnimator` |
| Night topo to Street blend as the user zooms out | `map/BasemapStyles.kt:64-83` | driven by zoom, not by time |
| Records and Entries short-window second row expands and shrinks | `log/JournalShortWindow.kt:200` | default specs, not MotionTokens |
| Camera chips and countdown turn with the phone | `log/RotateWithDevice.kt:61` | `animateFloatAsState`, default spring |
| Swipe rows drag, fling and close | `log/TwoStageSwipe.kt:206`, `:212`, `:215` | `anchoredDraggable` |
| Restore page pulse and leave | `backup/RestoreLoadingPage.kt:85-99` | hand-written tweens |
| Settings scrolls to Backup from a notification | `availability/AvailabilitySettingsUi.kt:311` | `animateScrollTo` |

### Default platform motion

These already move because Material 3 or Android draws them, so they are not listed as items below:

- the Tools drawer
- the bottom-nav and rail selection pill
- every `AlertDialog`
- every `ModalBottomSheet`, including the Layers sheet and the record details sheet
- every `DropdownMenu`
- snackbars
- toasts
- `Switch` and `RadioButton`
- the Finds tab indicator and the Entries/Records segmented button's check
- `IconButton` and `Button` ripples, which are round or pill-shaped and match their controls

What each looks like under this Material 3 alpha (`1.5.0-alpha26`, `gradle/libs.versions.toml:19`)
is phone-only.

### Deliberately not animated

Recorded in code comments. A change to any of these reverses a recorded decision:

- **The search field on non-Maps tabs, and its slot on Maps, appears and vanishes on purpose**
  (`availability/AvailabilityCompactScaffold.kt:849-855`, `:1073-1075`, `:1095-1097`). An animated exit
  leaves the field focusable and reopens a race where the dropdown opens over an entry.
- **The camera asks the system for no rotation animation** (`log/CameraWindowChrome.kt:139-183`). The
  reason is in `log/WindowOrientation.kt:21-33`.
- **Marker placement fade is off for the whole map style**, to stop fan flicker
  (`map/PlacementTransitions.kt:20-30`).
- **The camera jumps instantly when it restores on a tab return or frames an entry**
  (`map/SightingsMap.kt:1645-1653`, `:1673-1705`).
- **Arriving on a route swaps the start pin for the ring with no animation**
  (`map/RouteHomeLayers.kt:199-203`).

---

## App-wide: tabs, Back, theme

**S1** (cat 1). **Now:** tap List, Seasonal, Maps or Journal in the bottom bar or rail > the new tab
replaces the old one in one frame. The same applies to every move between tabs the app makes for the
user:

- View on Map
- Navigate
- GPX import goes to Journal
- Restore Done goes to Maps
- a report closing back to Maps
- the bubble's "Open in Journal"

**Code:** `availability/AvailabilityCompactScaffold.kt:936`, a plain `when (compactTab())`.
**Reuse:** the navigation and panel styles. **Risk:** the Maps tab hosts the live map. A transition
between tabs keeps the outgoing tab composed during the exit, so two trees (one with a map) live at
once. The file insists nothing may re-measure the map (`:1047-1053`, `:1081-1085`). [touch] while the
two overlap. **Phone only:** the cost of keeping the map alive through an exit.

**S2** (cat 1). **Now:** Back on a tab other than Maps > Maps appears instantly.
**Code:** `availability/AvailabilityScreen.kt:1226-1231`. **Reuse and risk:** as S1.

**S3** (cat 3). **Now:** tap Maps from another tab > the solid bottom bar vanishes and the Maps tab's
own 80% bar takes its place in the same frame. Leaving Maps reverses it, so the bar's fill jumps
between solid and 80% on every change to or from Maps. **Code:**
`availability/AvailabilityCompactScaffold.kt:787-793`, with the Maps bar at
`availability/AvailabilityCompactMapUi.kt:1118`. **Risk:** [80%]. The bar's measured height drives
several insets (`AvailabilityCompactScaffold.kt:580`, `:991`). **Phone only:** whether it reads as a
flash.

**S4** (cat 3). **Now:** in landscape, switch between Maps and another tab > the rail beside the content
appears or disappears and the content reflows in one frame. **Code:**
`availability/AvailabilityCompactScaffold.kt:807-814`, `:1459-1466`. **Note:** the comment at `:590`
says the rail has no animation in fullscreen. That is stale: the Maps rail now slides
(`AvailabilityCompactMapUi.kt:1156`).

**S5** (cat 4). **Now:** Settings > Night mode > pick Light or Dark > every colour in the app changes at
once. **Code:** `MainActivity.kt:407-411` and `:439`, which swap the colour scheme. **Risk:** the
change is app-wide and includes the 80% map chrome. **Phone only:** whether the system-bar icons flip
on the same frame.

**S6** (cat 4). **Now:** with a snackbar showing, switch tabs, toggle fullscreen or pan away while
navigating > the snackbar jumps to its new height. **Code:**
`availability/AvailabilityCompactScaffold.kt:716-727`. The lift for Return to Route is at `:727`.
**Reuse:** the attribution insets beside it already glide (`:652-687`). **Risk:** [80%] on Maps.

**S7** (cat 2). **Now:** while recording, tap the "tap anywhere" background-run prompt > the press
highlight is a sharp-cornered rectangle that ignores the snackbar's rounded corners. **Code:**
`availability/AvailabilityCompactScaffold.kt:730-736`. The `clickable` sits before the snackbar's own
shape clip (inferred from modifier order). **Risk:** [80%] on Maps.

**S8** (cat 3). **Now:** after a restore, the full-screen restore page appears instantly. Its leaving is
already animated. **Code:** `backup/RestoreLoadingPage.kt:166-174`, called from `MainActivity.kt:746`.
**Reuse:** the page's own `Animatable` (`:85-99`). **Risk:** it is opaque and swallows touches
(`:72-74`, `:108`). An entrance fade must not let touches through early.

## Map icon bar (the owner's named case)

How a press is drawn today: every row in the bar and the record/return pill is `MapBarIconButton`, a
48 x 48 `Box` with a plain `clickable` (`map/MapChrome.kt:801-806`). The default ripple fills the whole
square. No row has a shape, clip or custom highlight, and no row has a "selected" background.

**B1** (cat 2). **Now:** tap orientation reset, locate or Layers > a square highlight fills the bar's
full width with hard top and bottom edges. The portrait bar clips only at its rounded ends
(`map/MapChrome.kt:534-541`), and these rows sit between them. **Code:** rows at
`map/MapChrome.kt:499-518`. **Risk:** shaping the highlight must not shrink the touch box. The rule
quoted at `map/MapChrome.kt:704-712` keeps the touch area and changes only the visible mark. The
highlight paints over the 80% composite. [80%]

**B2** (cat 2). **Now:** tap Fullscreen (top) or Add (bottom) in portrait > the same square, only partly
rounded where it meets the bar's end, because the bar has 4dp of end padding (`map/MapChrome.kt:490`).

**B3** (cat 2). **Now:** tap Add > a square highlight around a round green badge. **Code:** the badge is
at `map/MapChrome.kt:808-813`.

**B4** (cat 2). **Now:** in landscape, tap the top or bottom row of the bar > the square's corners stick
out past the bar's rounded ends onto the map. In landscape the rows sit on top of an empty `Surface`
and are not clipped (`map/MapChrome.kt:522-532`). This is the boxiest case. **Risk:** as B1, plus the
landscape layout exists so its corners can be tapped (`fullSquareHits`).

**B5** (cat 2). **Now:** tap Record > a square highlight. While recording it sits around a round red
badge. In landscape the horizontal pill is unclipped, so the corners stick out as in B4. **Code:**
`availability/AvailabilityMapControlsUi.kt:224-233`. The pill's two layouts are at `:278-287` (unclipped)
and `:289-296` (clipped).

**B6** (cat 2). **Now:** tap Return > the same square. **Code:**
`availability/AvailabilityMapControlsUi.kt:235-248`.

**B7** (cat 2). **Now:** tap the minimise handle > a 20 x 72 dp rectangle lights up around a thin 10 dp
mark, partly over bare map. Its touch box overlaps the outer edge of the locate row, so a press there
lights the handle's rectangle instead of locate's square. **Code:** `map/MapChrome.kt:623-664`.
**Risk:** the 20 dp width is a recorded exception pinned by a test (`:623-639`, `:680-685`). [touch]

**B8** (cat 2). **Now:** tap the restore handle (cluster minimised) > a 48 x 48 square lights up around a
10 dp strip, wholly over the map. **Code:** `map/MapChrome.kt:743-747`.

**B9** (cat 2). **Now:** open Layers > nothing on the Layers icon shows that the sheet is open. There is no
"on" state to animate to. **Code:** row at `map/MapChrome.kt:509-518`.

**B10** (cat 4). **Now:** tap Fullscreen > its icon swaps to Exit fullscreen instantly, and back. **Code:**
`map/MapChrome.kt:495-496`.

**B11** (cat 4). **Now:** tap Record > the dot becomes a Stop icon and the red circle appears in the same
frame, and reverses on stop. **Code:** `availability/AvailabilityMapControlsUi.kt:226-231`, with the
badge at `map/MapChrome.kt:808-813`.

**B12** (cat 4). **Now:** start navigating > Return's icon swaps to Cancel. Its tint jumps between
normal, red (off track) and none. While disabled the whole row jumps to 40% opacity. **Code:**
`availability/AvailabilityMapControlsUi.kt:237`, `:242-246`, and `map/MapChrome.kt:804`.

**B13** (cat 3). **Now:** on an entry's fullscreen map, toggle offline > the Layers row disappears and the
bar is one row shorter instantly. **Code:** `map/MapChrome.kt:509`. **Risk:** the cluster's measured
height drives its drag limits (`availability/AvailabilityMapIconCluster.kt:571-575`).

**B14** (cat 5). **Now:** long-press the cluster and drag it past the threshold to the other side, then let
go > the cluster jumps from the finger to the opposite edge in one frame. Its handle side and the
landscape pill's button order flip at the same moment. **Code:**
`availability/AvailabilityMapIconCluster.kt:452-458`. **Reuse:** the cluster's own vertical glide
(`:501-507`). **Risk:** [touch]. The cluster's `Surface` would sweep its touch box across the map, and
the "+" tile's anchor reads the drag offset (`:222-227`).

**B15** (cat 5). **Now:** let go short of the threshold > the cluster snaps back to its edge. **Code:** same
lines.

**B16** (cat 2). **Note:** the same `MapBarIconButton` is used on an entry's fullscreen map
(`log/CartographyEntryReportScreen.kt:545`), so B1 to B6 apply there too.

## Map: compass strip and the sundown line

**C1** (cat 3). **Now:** while recording, when the sundown window opens > a "start back by" line appears
under the readout and the strip grows instantly. It disappears the same way. **Code:**
`availability/AvailabilityMapControlsUi.kt:563-575`. **Risk:** the strip's height feeds the search slot
below it (`availability/AvailabilityCompactMapUi.kt:726`, `:912`), so that jumps too. The strip takes no
touches, but it reports its bounds to the fan's keep-out area. **Reuse:** the scaffold's gliding top
inset (`availability/AvailabilityCompactScaffold.kt:421-425`).

**C2** (cat 3). **Now:** gain or lose a location fix > the strip swaps whole between the position note,
"Location services unavailable" and the full readout. **Code:**
`availability/AvailabilityMapControlsUi.kt:468`, `:496`, `:511`.

**C3** (cat 4). **Now:** turn the phone > the compass needle steps to each new heading. The heading is
smoothed upstream, but nothing tweens the drawing. **Code:**
`availability/AvailabilityMapControlsUi.kt:465`, smoothing at `:317-325`. **Risk:** any tween must
handle the wrap from 359 degrees to 0.

**C4** (cat 4). **Now:** walk > the heading, elevation and coordinate text change instantly. The figures
keep a steady width. **Code:** `availability/AvailabilityMapControlsUi.kt:536-559`.

**C5** (cat 4). **Now:** tap the coordinates > they swap between MGRS and decimal instantly. **Code:**
`availability/AvailabilityMapControlsUi.kt:552`, `:558`.

**C6** (cat 2). **Now:** tap the coordinates > a rectangular highlight around the text. The strip itself
is square-cornered, so there is no shape mismatch. **Code:** `:558`.

## Map: navigation display

**N1** (cat 1). **Now:** tap Return, or Navigate in a bubble > the compass strip vanishes and the
navigation HUD appears in the same frame, while the map tilts and turns smoothly over its timed
transition. The chrome cuts while the map eases. **Code:**
`availability/AvailabilityCompactMapUi.kt:884` and `:993`, with the camera at
`map/NavigationView.kt:448-503`. **Reuse:** the camera transition's own timing constant
(`map/NavigationView.kt:316`). **Risk:** [80%] (a crossfade stacks two 80% bands). [touch] (both carry a
tappable coordinates segment). The two differ in height. Keeping the strip alive through an exit keeps
it reading the compass at sensor rate (`AvailabilityCompactMapUi.kt:876-883`).

**N2** (cat 1). **Now:** tap X on the HUD or pill, or answer the Back prompt > the HUD vanishes and the
strip returns instantly, while the camera eases flat and north-up. **Code:**
`map/NavigationView.kt:541`.

**N3** (cat 3). **Now:** while navigating, drag the map > the Return to Route pill appears instantly at
bottom centre, and tapping it makes it vanish. **Code:**
`availability/AvailabilityCompactMapUi.kt:1038-1047` and `availability/ReturnToRoutePill.kt:53-76`.
**Risk:** [touch]. It is a `Surface` and the snackbar lift (S6) is tied to the same flag. [80%]

**N4** (cat 3). **Now:** a route fails > a "Try again" row makes the HUD taller instantly. **Code:**
`availability/NavigationHud.kt:315`.

**N5** (cat 3). **Now:** lose or regain the fix while navigating > the elevation and coordinates row comes
and goes, changing the HUD's height. **Code:** `availability/NavigationHud.kt:334`.

**N6** (cat 3). **Now:** the sundown line appears in the HUD instantly, as C1. **Code:**
`availability/NavigationHud.kt:360`.

**N7** (cat 4). **Now:** walk > the north arrow and target arrow step to each new angle. When the compass
is withheld, the needle snaps to north and dims. **Code:** `availability/NavigationHud.kt:278`, `:292-295`.

**N8** (cat 4). **Now:** walk > the distance number changes with each fix. "Arrived", "Unable to calculate
route" and a dash swap in instantly. **Code:** `availability/NavigationHud.kt:309`, `:607-618`.

**N9** (cat 4). **Now:** the fix goes stale > the distance dims to half at once. **Code:**
`availability/NavigationHud.kt:307`.

**N10** (cat 4). **Now:** the status line, including the fix age that ticks every second, the heading label
("Compass calibrating..." and similar) and the "Turn N degrees" text all change instantly. **Code:**
`availability/NavigationHud.kt:239-244`, `:564-568`, `:645`.

**N11** (cat 4). **Now:** the map facing mode changes (facing up, calibrating, north up) > the camera mode is
re-applied. The code says a change between two following modes runs no transition. **Code:**
`map/NavigationView.kt:421-433`, `map/SightingsMap.kt:1157-1163`. **Phone only:** whether the bearing
visibly snaps.

**N12** (cat 2). **Now:** tap the HUD's coordinates or "Try again" > a full-width rectangular band lights
up. **Code:** `availability/NavigationHud.kt:352-356`, `:393`.

## Map: chips, legend, bubbles, centre pin, Layers sheet

**M1** (cat 3). **Now:** tap View on Map, or show a journal entry > the filter chip row pops in. A second
chip wraps to a new line instantly. **Code:** `availability/AvailabilityCompactMapUi.kt:935-981`.
**Risk:** [touch] (both are `Surface`s). [80%]

**M2** (cat 3). **Now:** turn a colour layer on > the legend chip pops in, and the icon cluster then glides
up to make room. That glide is already animated. **Code:**
`availability/AvailabilityCompactMapUi.kt:1067-1082`, glide at
`availability/AvailabilityMapIconCluster.kt:368`, `:502`.

**M3** (cat 3). **Now:** tap the legend > it jumps open to its full size, and tapping again jumps it
closed. **Code:** `map/MapLayersSheet.kt:490-518`. **Risk:** [touch]. A size animation also re-triggers
the cluster glide on every frame, because the glide is keyed on the legend's top
(`AvailabilityMapIconCluster.kt:502`).

**M4** (cat 2). **Now:** tap the legend > the highlight is probably a sharp rectangle over its rounded
corners. `clickable` is applied before the `Surface`'s shape clip (inferred from modifier order).
**Code:** `map/MapLayersSheet.kt:476-481`.

**M5** (cat 3). **Now:** tap a glyph > its bubble appears instantly. Close, or tap empty map > it vanishes.
**Code:** `map/MapBubble.kt:279-341`. **Reuse:** the "+" tile's grow-from-anchor
(`availability/AvailabilityMapControlsUi.kt:698-713`). **Risk:** [touch]. The card swallows taps
(`map/MapBubble.kt:213`). [80%]

**M6** (cat 3). **Now:** with a bubble open, tap another glyph > the content is replaced in place.
**Code:** `availability/AvailabilityCompactMapUi.kt:458`.

**M7** (cat 3). **Now:** tap a forecast cell > nothing shows while it loads, then the bubble pops in.
**Code:** `map/MapBubble.kt:293-294`.

**M8** (cat 5). **Now:** pan with a bubble open > the bubble stays still, then jumps to its glyph when the
map stops. **Code:** `map/SightingsMap.kt:651-653`, `:715`. **Phone only:** how noticeable this is.

**M9** (cat 3). **Now:** Add > Trip, Find or Waypoint, or search "Set on map" > the centre pin and the
OK/Cancel row appear instantly while the "+" tile is still fading out. OK or Cancel > both vanish.
**Code:** `availability/AvailabilityCompactMapUi.kt:1237`, `:1256`, and
`map/CentrePinLocationPicker.kt:305-326`. **Risk:** [touch]. The OK/Cancel row is a full-width
`Surface` just above the nav, and its touch order is deliberate (`AvailabilityCompactMapUi.kt:1084-1102`).
[80%]

**M10** (cat 2). **Now:** tap a row in the Layers sheet > a full-width rectangular highlight. **Code:**
`map/MapLayersSheet.kt:300-307`. This is ordinary for a sheet list row.

**M11** (cat 5). **Now:** in Layers, drag a colour layer to reorder it > the row follows the finger, and on
release it snaps back while the list reorders in one frame. **Code:** `map/MapLayersSheet.kt:346`,
`:372`.

**M12** (cat 2). **Now:** tap "View on iNaturalist" in a bubble > a rectangular highlight around the text,
inside a card rounded at 20 dp. **Code:** `availability/AvailabilityMapOverlaysUi.kt:313-315`.

**M13** (cat 3). **Now:** open the Maps tab > a spinner or error text swaps to the map instantly.
**Code:** `availability/AvailabilityCompactMapUi.kt:515-528`.

## The map itself (MapLibre)

**G1** (cat 5). **Now:** run a search in a new area > the camera jumps there with no travel. **Code:**
`map/SightingsMap.kt:903-915`. **Phone only:** on locate after a search, the jump and a tracking move are
both requested (`AvailabilityCompactMapUi.kt:549-553`). Which one wins on screen is unknown.

**G2** (cat 5). **Now:** switch basemap, night mode or offline > the map blanks, then the new basemap and
every overlay redraw. There is no crossfade between styles. **Code:** `map/SightingsMap.kt:768-861`.

**G3** (cat 3). **Now:** flip a layer switch in Layers > that layer appears or vanishes instantly. Opacity
changes may ease through MapLibre's paint transition (inferred). **Code:** `map/SightingsMap.kt:1496`,
`:1513-1520`.

**G4** (cat 3). **Now:** while navigating, the line ahead and the dashed straight line redraw with each
update. Losing the fix swaps the dashed line to grey instantly. **Code:** `map/RouteHomeLayers.kt:108-113`,
`:189-193`.

**G5** (cat 4). **Now:** tap a sighting > its ring thickens instantly. Selection is part of the data, and
data changes do not transition in MapLibre (inferred). **Code:** `map/SightingsMap.kt:1949`,
`:1989-2006`. `docs/motion-spec.md` section 2 specifies a selection pulse that is not built.

**G6** (cat 4). **Now:** breadcrumbs, tracks and markers redraw with each data push. **Code:**
`map/SightingsMap.kt:872-882`.

**G7** (cat 5). **Now:** tap locate when not navigating > MapLibre's own tracking move runs. No duration is
set here. **Code:** `map/SightingsMap.kt:1109`. Listed so the owner knows it is the SDK's default.

## Search (dropdown and fields)

**Q1** (cat 3). **Now:** tap "Recent searches" or "Advanced search" > the section opens or closes instantly
inside a dropdown that itself expands smoothly. **Code:** `availability/AvailabilitySearchUi.kt:698-705`.
**Reuse:** the dropdown's own expand (`AvailabilityCompactScaffold.kt:1377`). **Risk:** the
scroll-to-coordinates at `:476-483` reads layout after one frame, and the comment at `:381-383` says
positions read mid-animation were stale. [80%] on Maps.

**Q2** (cat 4). **Now:** the same tap > the chevron swaps from down to up with no turn. **Code:**
`availability/AvailabilitySearchUi.kt:694`.

**Q3** (cat 2). **Now:** tap those headers > a tight rectangular highlight around the text and icon.
**Code:** `availability/AvailabilitySearchUi.kt:685-688`.

**Q4** (cat 3). **Now:** a search fails or location is denied > a red notice appears and pushes everything
down instantly. **Code:** `availability/AvailabilitySearchUi.kt:564-587`. **Risk:** [touch] [80%]. Its
height moves the icon cluster (`AvailabilityCompactScaffold.kt:1058-1062`).

**Q5** (cat 3). **Now:** close the search dropdown > its invisible full-screen dismiss layer is removed at
once while the panel is still shrinking. During the shrink, the map and the panel's buttons can be
tapped. **Code:** `availability/AvailabilityCompactScaffold.kt:1307-1359`, `:1378`. **Risk:** [touch].
This is an existing mismatch between a shown thing and its touch layer. **Phone only:** whether a
finger can hit it in that window.

**Q6** (cat 3). **Now:** type a species > in the suggestions menu, "No matches" and result rows swap
instantly. The menu itself has platform motion. **Code:** `availability/AvailabilitySearchUi.kt:947-962`.

**Q7** (cat 4). **Now:** run a search > the summary line changes text instantly. **Code:**
`availability/AvailabilitySearchUi.kt:247`, built at `:526-543`.

## List and Seasonal tabs

**L1** (cat 2). **Now:** List > tap a species card > a sharp rectangular highlight that overhangs the card's
rounded corners. `clickable` is applied before the card's shape (inferred from modifier order; checked
at `:645-649`). **Code:** `availability/AvailabilityResultsUi.kt:645-649`. **Reuse:** the waypoint row,
which clips first (`availability/AvailabilityTripsWaypointsUi.kt:260`).

**L2** (cat 2). **Now:** tap "View on Map" on a card > a tight rectangle, nested inside the card's own
highlight. **Code:** `availability/AvailabilityResultsUi.kt:677-683`.

**L3** (cat 3). **Now:** search > the List swaps spinner to empty text to results instantly. **Code:**
`availability/AvailabilityResultsUi.kt:180-216`.

**L4** (cat 3). **Now:** results update > rows appear and reorder with no movement, although each row has a
stable key that would allow it. **Code:** `availability/AvailabilityResultsUi.kt:210-214`.

**L5** (cat 3). **Now:** go offline > the cached-results banner appears instantly. **Code:** `:114-119`.

**L6** (cat 3). **Now:** Seasonal > the conditions card, the pattern chart, today's forecast and the trip
windows each swap between prompt, spinner, error and content instantly. **Code:** `:263-297`,
`:486-503`, `:528-541`.

**L7** (cat 4). **Now:** seasonal data loads > the chart bars appear at their heights at once. **Code:**
`availability/AvailabilityResultsUi.kt:393-405`.

**L8** (cat 4). **Now:** each species card's likelihood bar is set per result. **Code:** `:664-667`.
**Phone only:** whether the Material bar animates by itself.

## Tools drawer, Settings, Crash logs, Backup

**T1** (cat 1). **Now:** Tools > Settings > Settings replaces the Tools list inside the drawer instantly,
and Back returns instantly. **Code:** `availability/AvailabilitySettingsUi.kt:641-665`. **Risk:** [80%].
Over Maps the drawer sheet is 80% (`availability/AvailabilityScreen.kt:1770`). **Side note, not motion:**
`showSettings` is never reset when the drawer closes, so the drawer may reopen straight onto Settings.

**T2** (cat 1). **Now:** Settings > Crash logs or Diagnostics > the panel replaces Settings instantly, and
Back returns instantly. **Code:** `availability/AvailabilitySettingsUi.kt:212-258`.

**T3** (cat 1). **Now:** Crash logs > tap a crash > the detail replaces the list instantly. "Loading..."
swaps to the text instantly. **Code:** `crash/CrashLogPanel.kt:57-66`, `:151-165`.

**T4** (cat 2). **Now:** tap the drawer header, a settings row, a crash-log header or a crash row > a
full-width sharp rectangle. **Code:** `availability/AvailabilitySettingsUi.kt:127-161`,
`crash/CrashLogPanel.kt:76-136`, `:172-177`. These are list rows, so a rectangle is not a shape
mismatch. Listed for completeness.

**T5** (cat 2). **Now:** Backup > tap a "How often" row > a full-width rectangle, while the radio dot
animates. **Code:** `backup/BackupSection.kt:107-116`.

**T6** (cat 3). **Now:** Backup's status line and chosen folder name appear or change instantly. Crash logs'
empty text swaps in instantly. **Code:** `backup/BackupSection.kt:125-142`, `crash/CrashLogPanel.kt:109-116`.

**T7** (cat 4). **Now:** start a backup > the buttons only grey out. There is no progress shown, so the
state change is a colour jump. **Code:** `backup/BackupSection.kt:89`, `:137`.

**T8** (cat 3). **Now:** restore finishes > the "Done" pill appears and the caption swaps instantly, while
the pulse settles smoothly. **Code:** `backup/RestoreLoadingPage.kt:139-153`.

## Trip planner and waypoint list

**P1** (cat 3). **Now:** delete a planned trip > its card vanishes and the list closes up instantly. The
empty text swaps in. **Code:** `availability/AvailabilityTripsWaypointsUi.kt:84-97`.

**P2** (cat 3). **Now:** swipe-delete a waypoint > the row is removed and the rows below jump up. The swipe
itself animates. **Code:** `availability/AvailabilityTripsWaypointsUi.kt:205-233`.

**P3** (cat 3). **Now:** before and after a search > the trip windows card and its prompt swap. **Code:**
`:64-71`.

**P4** (cat 4). **Now:** at midnight > a trip dated today gains its "Today" label and colour instantly. Low
impact. **Code:** `:116-136`.

## Journal: switching and Entries

**J1** (cat 1). **Now:** tap Entries or Records on the switch > the whole content area swaps instantly. The
switch's own check moves. **Code:** `log/JournalTab.kt:661`. **Risk:** Entries can host an entry map. A
transition keeps two map hosts composed, and the comment at
`log/CartographyEntryReportScreen.kt:472-474` warns that a second call site tears the map down.

**J2** (cat 1). **Now:** Back on Records > Entries appears instantly. **Code:** `log/JournalTab.kt:479-481`.

**J3** (cat 1). **Now:** tap an entry card > the report replaces the list instantly. Edit replaces the
report instantly, and Back reverses each step instantly. **Code:** `log/CartographyScreen.kt:389-452`,
`:245-261`, `:320-322`.

**J4** (cat 1). **Now:** the drafts banner > Continue (more than one draft) > a full-screen drafts list
replaces Entries instantly. Back returns instantly. **Code:** `log/CartographyScreen.kt:471-493`.

**J5** (cat 1). **Now:** tap the timeline/album toggle > the view swaps instantly. The "New entry" and "Add
photo" buttons swap in the same frame. **Code:** `log/CartographyScreen.kt:563-615`,
`log/EntriesAlbum.kt:169-175`.

**J6** (cat 1). **Now:** open Entries > a centred spinner, then the content, both instantly. **Code:**
`log/CartographyScreen.kt:453-460`.

**J7** (cat 1). **Now:** the editor > "+ Add a photo from the Album" > the picker replaces the editor
instantly. **Code:** `log/CartographyEntryEditScreen.kt:190-203`.

**J8** (cat 3). **Now:** save or discard the last draft > the drafts banner and chip appear or vanish
instantly. **Code:** `log/CartographyScreen.kt:537-546`.

**J9** (cat 3). **Now:** delete an entry > its card vanishes from the grid and the cards close up, and on
Undo it pops back. Month headers have no movement either. The album grid is the same. **Code:**
`log/CartographyEntryListScreen.kt:146-147` and `log/EntriesAlbum.kt:148-156`, which have keys but no
item animation.

**J10** (cat 3). **Now:** the list and album swap spinner, empty text and error instantly. **Code:**
`log/CartographyEntryListScreen.kt:99-134` and `log/EntriesAlbum.kt:123-138`.

**J11** (cat 3). **Now:** open Entries > a card's photo or track thumbnail arrives a moment late, and a
card can switch from a collapsed row to a full card after the first frame. **Code:**
`log/CartographyEntryListScreen.kt:118-120`, `:163`, `:171`.

**J12** (cat 4). **Now:** photos everywhere show a grey block until decoded, then the picture appears in one
frame. **Code:** `log/DecodedPhoto.kt` (painter swap at about `:94-103`). **Risk:** the file's comment
(about `:83-93`) requires the tap to stay on one node, so a crossfade must not split the image in two.

**J13** (cat 4). **Now:** in the editor, tap Withhold > the row drops to half opacity with a line-through,
and the buttons collapse from two to one instantly. **Code:**
`log/CartographyEntryEditScreen.kt:604-630`.

**J14** (cat 3). **Now:** open the editor > candidate sections arrive after load and push the content down
instantly. **Code:** `log/CartographyEntryEditScreen.kt:221-223`, `:587-593`.

**J15** (cat 4). **Now:** the drafts count ("N unfinished entries", "N drafts") and the card stats change
instantly. **Code:** `log/EntriesDrafts.kt:52`, `log/JournalShortWindow.kt:157`,
`log/CartographyEntryCard.kt:104-134`.

**J16** (cat 2). **Now:** with a swipe row open on an entry card, tap the card > a sharp rectangular
highlight over the rounded card. This is a true shape mismatch. **Code:** `log/TwoStageSwipe.kt:219`,
`:299-303`, wrapping `log/CartographyEntryListScreen.kt:176`.

**J17** (cat 2). **Now:** tap a kept photo's remove button in the entry editor > a small round highlight
directly over the photo, with no backdrop. The find editor's remove button already does a large touch
area with a small round highlight on a backdrop (`log/LogEntryDetailScreen.kt:350-358`), which could be
reused. **Code:** `log/CartographyEntryEditScreen.kt:423-428`.

**J18** (cat 4). **Now:** tap the toggle > the album/timeline toggle's filled background flips. **Code:**
`log/EntriesToolbar.kt:35-48`. **Phone only:** whether Material animates it.

**J19** (cat 4). **Now:** in the short window, tap search > the icon swaps between Search and Search-off
instantly, and the search icon itself is added or removed when an entry opens. **Code:**
`log/JournalShortWindow.kt:97-103`.

## Journal: entry report with its map

**E1** (cat 1). **Now:** tap the entry's map > it goes fullscreen. The header, offline row and body vanish,
and the map grows, all in one frame. Back or Return reverses it. **Code:**
`log/CartographyEntryReportScreen.kt:361`, `:394`, `:463-470`, `:516-522`, `:547`, `:586`, `:612`.
**Risk:** the map is one view that must not move (`:472-474`), so only its container could change size.
The map would then re-lay out on every frame. [touch] (the tap that enters fullscreen is on the map).
**Phone only:** whether the map can resize smoothly.

**E2** (cat 3). **Now:** open an entry with a location > no map at first, then a map pops in and pushes the
body down. This happens on every open. **Code:** `log/CartographyEntryReportScreen.kt:363-367`, `:461`.

**E3** (cat 3). **Now:** a moment later > the offline-map row pops in, causing a second shift. **Code:**
`log/CartographyEntryReportScreen.kt:588-608`.

**E4** (cat 3). **Now:** in fullscreen, the icon bar and the Layers sheet appear via plain conditions.
**Code:** `log/CartographyEntryReportScreen.kt:544-582`. The bar's presses are B1 to B6.

**E5** (cat 5). **Note:** the menu and the delete dialog on this screen are 80% when the entry has a map
(`log/CartographyEntryReportScreen.kt:413-421`, `:683-698`). Any added motion must keep that fill and
its test hooks. [80%]

## Journal: Records

**R1** (cat 1). **Now:** tap a filter chip (All, Finds, Tracks, Waypoints, Offline maps) > the list swaps
instantly, and Back to All is instant. **Code:** `log/RecordsTab.kt:267-269`, `:297-373`.

**R2** (cat 1). **Now:** tap a find in All > the Finds chip selects and the report replaces the list
instantly. **Code:** `log/RecordsTab.kt:311-314`.

**R3** (cat 2). **Now:** tap a logbook row, a track row or a walk's waypoint row > a full-width sharp
rectangle, including the badge. These are list rows, so it is not a shape mismatch. **Code:**
`log/RecordsLogbookList.kt:257-261`, `track/TrackExportPanel.kt:163-170`,
`log/RecordDetailsSheet.kt:153-154`, `:442-447`.

**R4** (cat 3). **Now:** delete a record or track > the row vanishes and the rows below jump up, and on Undo
it pops back. These lists are plain columns, not lazy lists, so the lazy-list item animation is not
available. **Code:** `log/RecordsLogbookList.kt:139-226`, `track/TrackExportPanel.kt:112-136`.

**R5** (cat 3). **Now:** delete a find > the find pairs in the logbook re-pair instantly. **Code:**
`log/RecordsLogbookList.kt:141-155`.

**R6** (cat 3). **Now:** empty states ("No records yet.", "No recorded tracks yet.", the Finds note) and
the track error line appear instantly. **Code:** `log/RecordsLogbookList.kt:130-138`,
`track/TrackExportPanel.kt:95-111`.

**R7** (cat 4). **Now:** records change > the chip counts and the day header's "N records" jump. **Code:**
`log/RecordsFilterChips.kt:112`, `log/RecordsLogbookList.kt:233-235`.

**R8** (cat 4). **Now:** while recording, the track row's "N points" and the details sheet's points,
distance and duration tick instantly. **Code:** `track/TrackExportPanel.kt:213-226`,
`log/RecordDetailsSheet.kt:391-396`.

**R9** (cat 4). **Now:** tap a chip > its colour flips. **Code:** `log/RecordsFilterChips.kt:95-118`.
**Phone only:** whether Material animates it.

**R10** (cat 5). **Now:** swipe a row and tap Delete > the row snaps shut, then leaves the list. A full
swipe also snaps back once Delete settles. **Code:** `log/TwoStageSwipe.kt:198`, `:283`. **Reuse:**
the animated close beside it (`:206`, `:212`).

**R11** (cat 1). **Now:** in a walk's details sheet, tap a waypoint > the walk sheet is removed with no
slide-down and a new sheet slides up. Back does the same in reverse. **Code:**
`log/RecordDetailsSheet.kt:211-216`, `:254-255`.

**R12** (cat 1). **Now:** in a waypoint sheet, tap Navigate, or delete a track from its sheet > the sheet
vanishes without its own slide-down. **Code:** `log/RecordsTab.kt:402`,
`log/RecordDetailsSheet.kt:240-243`.

**R13** (cat 1). **Now:** Offline maps > tap Download Maps > a dialog appears with platform motion,
recorded here because it is 80% over the picker map. **Code:**
`availability/AvailabilityOfflineMapsUi.kt:256-276`. [80%]

**R14** (cat 3). **Now:** start a download > the progress block appears instantly and vanishes instantly
when done. A failure's text appears instantly. **Code:** `availability/AvailabilityOfflineMapsUi.kt:460-481`.

**R15** (cat 3). **Now:** swipe-delete a region > the row vanishes and the rows below jump. **Code:**
`availability/AvailabilityOfflineMapsUi.kt:547-575`.

**R16** (cat 3). **Now:** a region's "Not downloaded", "Download again" and "Stale" labels swap instantly.
**Code:** `:617-641`.

**R17** (cat 4). **Now:** drag the radius slider > "~N tiles" changes, and turns red at once when over
budget. **Code:** `availability/AvailabilityOfflineMapsUi.kt:231-239`.

**R18** (cat 4). **Now:** pick a point > "No location picked yet" becomes "Download region: ..." instantly.
While downloading, the "x / y tiles" text steps. **Code:** `:195-200`, `:465-469`.

**R19** (cat 5). **Now:** rotate the phone on Offline maps > the stacked and side-by-side layouts swap
instantly. **Risk:** the map moves between layouts as one piece (`:173-178`), and animating would
re-measure it. **Code:** `availability/AvailabilityOfflineMapsUi.kt:295-308`.

**R20** (cat 2). **Now:** tap an offline region row > a full-width rectangle. **Code:**
`availability/AvailabilityOfflineMapsUi.kt:607-610`.

## Journal: Finds

**F1** (cat 1). **Now:** tap a find tile > the report replaces the gallery instantly. **Code:**
`log/JournalTab.kt:584-586`.

**F2** (cat 1). **Now:** report > Edit > Location picker or From Album > each replaces the last instantly,
and Back reverses step by step instantly. **Code:** `log/JournalTab.kt:461-553`. **Risk:** the location
picker is a live map, with the same concern as J1.

**F3** (cat 1). **Now:** a bubble's "Open find" > a full-screen page pops over Journal instantly, and Back
removes it instantly. **Code:** `log/JournalTab.kt:793-798`. **Risk:** it deliberately swallows touches
(`:790-792`). Any entrance or exit must keep that over its full area the whole time it is visible.

**F4** (cat 1). **Now:** tap Log or Drafts > the grid swaps instantly, while the tab underline moves.
**Code:** `log/FindsGalleryScreen.kt:106-115`.

**F5** (cat 3). **Now:** open Finds > a full-screen spinner, then the gallery, instantly. A load error
appears above the grid. **Code:** `log/FindsGalleryScreen.kt:98-122`.

**F6** (cat 3). **Now:** delete a find or switch tabs > tiles and the "+" tile pop with no movement. The
grid has keys but no item animation. **Code:** `log/FindsGalleryScreen.kt:134-143`.

**F7** (cat 3). **Now:** in the find editor, add the first photo > the photo row appears. Remove a photo >
the others reflow instantly. **Code:** `log/LogEntryDetailScreen.kt:263-280`.

**F8** (cat 3). **Now:** the report swaps an empty message for content, and the album picker swaps empty
text for its grid, instantly. **Code:** `log/LogEntryReportScreen.kt:157-191`,
`log/PullPhotoPickerScreen.kt:86-93`.

**F9** (cat 4). **Now:** after picking a location > "Add Location" becomes "Change Location", and the
"Drafts (N)" tab label changes, instantly. **Code:** `log/LogEntryDetailScreen.kt:184`,
`log/FindsGalleryScreen.kt:111`.

## Photo viewer

**V1** (cat 1). **Now:** tap a photo > a full-screen black viewer opens as a dialog window. **Code:**
`log/PhotoViewerDialog.kt:146-149`. **Phone only:** what that window's entrance looks like with
full-width set.

**V2** (cat 4). **Now:** tap next or previous > the photo is replaced instantly, zoom resets instantly, and
the "2 / 3" counter jumps. **Code:** `log/PhotoViewerDialog.kt:183-188`, `:324-340`.

**V3** (cat 4). **Now:** double-tap > the zoom jumps between 1x and 2.5x with no glide. **Code:**
`log/PhotoViewerDialog.kt:390-399`. **Risk:** low. The touch layer sits outside the zoomed layer on
purpose (`:310-313`).

**V4** (cat 3). **Now:** a spinner, then the photo or "Couldn't load", swap instantly. **Code:** `:344-420`.

**V5** (cat 2). **Now:** tap Save > it has no visible disabled state while saving, because the icon stays
white. The only feedback is a toast. **Code:** `log/PhotoViewerDialog.kt:286-287`, `:240`.

## In-app camera

**K1** (cat 1). **Now:** tap Camera > the full-screen camera appears instantly, and Back closes it
instantly. **Code:** `log/InAppCameraHost.kt:118`, `log/InAppCameraDialog.kt:217-249`. **Risk:** it
swallows touches over its full area (`:246`, `:378-384`), and its comment records two touch-through
bugs. Status bar hiding and rotation settings are tied to exactly the time it is on screen
(`:225-226`).

**K2** (cat 3). **Now:** opening > a spinner swaps to the viewfinder instantly. **Code:**
`log/InAppCameraDialog.kt:250-263`.

**K3** (cat 4). **Now:** tap Flash, Timer, Grid or Location > the chip's icon swaps instantly. The chips
already turn smoothly with the phone. **Code:** `log/FlashChip.kt:43-48`, `log/TimerChip.kt:30-35`,
`log/GridChip.kt:30-35`, `log/LocationChip.kt:36-41`.

**K4** (cat 3). **Now:** tap Grid > the grid lines and level line appear or vanish instantly. **Code:**
`log/GridOverlay.kt:41`, `log/LevelLine.kt:53-55`.

**K5** (cat 4). **Now:** level the phone > the level line's colour snaps. **Code:** `log/LevelLine.kt:68`.

**K6** (cat 4). **Now:** start a timer > the countdown number jumps each second. Take a photo > the shutter
dims and undims at once, and the photo count jumps. **Code:** `log/InAppCameraDialog.kt:273-282`,
`:420-424`, `:467`, `:485-489`.

**K7** (cat 3). **Now:** a capture error line appears instantly. **Code:** `log/InAppCameraDialog.kt:412-419`.

---

## Counts

Items above, by screen and category. Default platform motion and things already animating are not
counted.

| Group | 1 screens | 2 presses | 3 appear | 4 values | 5 other | Total |
|---|---|---|---|---|---|---|
| App-wide (S) | 2 | 1 | 3 | 2 | 0 | 8 |
| Map icon bar (B) | 0 | 10 | 1 | 3 | 2 | 16 |
| Compass strip and sundown line (C) | 0 | 1 | 2 | 3 | 0 | 6 |
| Navigation display (N) | 2 | 1 | 4 | 5 | 0 | 12 |
| Map chips, legend, bubbles, pin (M) | 0 | 3 | 8 | 0 | 2 | 13 |
| The map itself (G) | 0 | 0 | 2 | 2 | 3 | 7 |
| Search (Q) | 0 | 1 | 4 | 2 | 0 | 7 |
| List and Seasonal (L) | 0 | 2 | 4 | 2 | 0 | 8 |
| Tools, Settings, Crash, Backup (T) | 3 | 2 | 2 | 1 | 0 | 8 |
| Trip planner and waypoints (P) | 0 | 0 | 3 | 1 | 0 | 4 |
| Journal switching and Entries (J) | 7 | 2 | 5 | 5 | 0 | 19 |
| Entry report with map (E) | 1 | 0 | 3 | 0 | 1 | 5 |
| Records (R) | 5 | 2 | 6 | 5 | 2 | 20 |
| Finds (F) | 4 | 0 | 4 | 1 | 0 | 9 |
| Photo viewer (V) | 1 | 1 | 1 | 2 | 0 | 5 |
| Camera (K) | 1 | 0 | 3 | 3 | 0 | 7 |
| **Total** | **26** | **26** | **55** | **37** | **10** | **154** |

B16 (the icon bar reused on an entry map) is counted under the icon bar and not again under the entry report. The table was tallied by script from the item tags, not by hand.

## What could not be determined from code

Everything below needs a phone:

- **The highlights.** How the default press highlight looks on the 80% bar and the 0.6/0.5 layered
  cluster fills, in light and dark. How much the sharp corners of the species card, the snackbar and
  the legend actually show (inferred from modifier order, not seen). Whether a highlight shows at all
  on the white shutter, on the black photo viewer and over photos.
- **What Material 3 animates on its own** in `1.5.0-alpha26`: the drawer, the nav selection pill,
  dialogs, chip and toggle-button colours, and the determinate progress bars. It is also unknown
  whether they follow the app's expressive motion scheme or their own.
- **The theme flip.** Whether the change between solid and 80% on the bottom bar (S3), or the theme flip
  (S5), reads as a flash.
- **Map performance.** Whether the live map can survive a tab transition (S1, J1) or a size animation
  (E1) without dropped frames or a teardown.
- **MapLibre's own motion.** What its tracking move and paint transitions look like (G3, G5, G7).
  Whether a facing-mode change snaps the bearing (N11). Which camera move wins on locate after a
  search (G1).
- **Search dropdown.** Whether a finger can actually hit the search panel during its shrink, when its
  dismiss layer is already gone (Q5).
- **Insets.** Anything that depends on real system-bar insets (S4, S6). Robolectric reports zero
  (CLAUDE.md).
- **The photo viewer window.** How its entrance and exit look (V1).
