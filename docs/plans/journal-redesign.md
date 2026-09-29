# Design: the Journal redesign

Planning doc for the Journal destination — Cartography (entries, drafts, album)
and Records (waypoints, offline maps, tracks, finds) — in portrait and in short
(landscape) windows. It is a design record and a task spec for later coder
dispatches, not a replacement for the repo's `CLAUDE.md`, whose standing
principles govern everything below.

Written 2026-09-26 by the planner session in worktree
`bridge-cse_013tPFtYR838ZpDY4QTeK8QA` and committed by a coder dispatch
(its store copy and record intent name it). **O** decisions are the owner's,
said in that planner session; **J** and **L** decisions are the planner's,
made under O2. **Nothing in this document is built.** Every citation was read
at `pre-main` `545258e`, except where marked as read at `main` `76905d4` with
the file unchanged between the two. Re-verify before relying on any of it
(CLAUDE.md, "A planner's picture of the repository is a claim about the past").

## What this is

The owner, with two screenshots of the Journal (Cartography › Entries, and
Records › Waypoint Markers): "The journal menu is mostly finished, but it feels
plain and vanilla. Can you research mobile interface development and design,
and propose a journal interface that is pleasing, easy to navigate, and
fulfills its purposes."

## Decisions — from the project owner

- **O1. Redesign the Journal.** As quoted above.
- **O2. The planner designs it.** "You're the designer so make the
  suggestions."
- **O3. The proposal is approved.** "Let's go with your proposal." That
  covers J1–J10 below as proposed, including the renaming of the Cartography
  tab.
- **O4. Landscape is in scope.** "Also consider the landscape layout." L1–L8
  below were written in answer. The owner has not yet ruled on them
  separately; they are proposed under O2.
- **O5. Hand-off.** "Commit it to the repo for the other planner to find,
  it'll be added into the other session since it's a long ways from
  finishing."

## Evidence — why it reads as plain

Read in code:

- **Two stacked tab rows.** The Journal's top row is a `SecondaryTabRow`
  (Cartography | Records), `JournalTab.kt:398-414`. Under it sit Cartography's
  Entries/Drafts/Album and Records' four sub-tabs (`RecordsSubTab`,
  `JournalTab.kt:243`). With the bottom bar, that is four levels before any
  content, and seven destinations under one bottom-bar icon. Material 3 permits
  secondary tabs under primary tabs, but here both rows look alike, so neither
  reads as primary.
- **Wrapped labels.** Records' labels ("Waypoint Markers", "Offline Maps",
  "Recorded Tracks", "Logged Finds") wrap to two lines on a phone (owner
  screenshot).
- **The entry card is almost empty.** `CartographyEntryTile`
  (`CartographyEntryListScreen.kt:121-161`) shows:
  - `entry.date.toString()`, an ISO date;
  - `entry.text` only if non-blank;
  - the tags;
  - one summed "N kept items" count.

  Two entries on one day look identical (owner screenshot: two
  "2026-09-26" cards).
- **The data exists but isn't shown.** `CartographyEntry`
  (`domain/model/CartographyEntry.kt:50-135`) already carries:
  - kept finds with `ownIdentification` and `hasPhotos`;
  - kept tracks with `distanceMeters` and `durationMillis`;
  - waypoints with names;
  - offline regions with `radiusKm`;
  - attached `photos`.
- **The "+" tile is the largest thing on screen.** It takes the first grid
  cell at full tile size (`CartographyEntryListScreen.kt:91`, `:97-119`,
  aspect ratio `0.85` at `:163`).
- **The palette goes unused.** It has distinct hues (`ui/theme/Color.kt`),
  and Understory already maps them to roles
  (`understory-design-system.md:230-235`):
  - `primary`: dark `#A8CBA0`, lichen;
  - `secondary`: Mushroom;
  - `tertiary`: TrailBlue.

  The Journal uses almost none of it.

Landscape, read in code and plan:

- A window under 480 dp tall is short (`ui/adaptive/ShortWindow.kt`, landed
  with landscape B1). A short window gets the compact tree
  (`landscape-phone-design.md` P1, R1).
- So a phone held sideways shows `JournalTab`, not `LogPanel`, beside an
  opaque navigation rail on the port side (R12 revised,
  `landscape-phone-design.md:600-603`).
- Its content is capped at 640 dp and centred (P11, `:289-298`). The S22 Ultra
  window there is 823 × 384 dp (`:30`).
- `LogPanel` now serves only the wide tree: tablets and foldables.

Not read (known from screenshots and doc comments only):
`CartographyScreen.kt`, `RecordsTab.kt`, `FindsGalleryScreen.kt`,
`PhotoGalleryScreen.kt`, the body of `LogPanel.kt`.

## Decisions — from the planner, portrait (J)

- **J1. One switch at the top.** A single-choice segmented button,
  **Entries | Records**, replaces the `SecondaryTabRow`. Only the on-screen
  label "Cartography" becomes "Entries"; code names such as
  `JournalTopTab.CARTOGRAPHY` and `CartographyEntry` stay.
- **J2. Drafts become a banner** at the top of Entries: "✎ N unfinished
  entries · Continue ›". With one draft, Continue opens it; with more, it
  opens the drafts list. There is no Drafts tab.
- **J3. Album becomes a view of Entries.** A two-button view toggle
  (☰ timeline, ▦ album) in the toolbar row. The album is grouped by day, in 3
  columns, with 3 dp gaps. A 🔗 badge marks a photo attached to an entry,
  replacing the reference-count text.
- **J4. Records gets filter chips.** One row of filter chips, each with an
  icon and a count:
  - All · Finds · Tracks · Waypoints · Offline maps.
  - The row scrolls sideways when it overflows.
  - **All** is the default: one logbook grouped by day, newest first.
  - A single-type chip shows what that type's tab shows today, including the
    Finds editing flow unchanged.
- **J5. Entry cards show the day.**
  - Sticky month headers.
  - A large day numeral with the weekday, instead of the ISO date.
  - The first line of the entry's text as its title.
  - Kept finds' identifications as species chips.
  - A stats row by type: finds count, track distance and duration, waypoint
    count, offline-map count.
  - A hero photo on top when the entry has one attached (subject to J0).
  - An entry with no photo, text or track collapses to one short row.
- **J6. One colour role per record type,** held in one shared style object and
  used by the chips, record badges, card stats and species chips. These are
  theme roles, not raw `Color.kt` values, so night mode and the Understory
  theme carry through:

  | Type | Role |
  |---|---|
  | Finds | `secondary` / `secondaryContainer` |
  | Tracks | `tertiary` / `tertiaryContainer` |
  | Waypoints | `primary` / `primaryContainer` |
  | Offline maps | `onSurfaceVariant` / `surfaceContainerHighest` |

- **J7. New entry is a floating button.** An extended floating action button
  replaces the "+" tile: "✎ New entry" on the timeline, "📷 Add photo" on the
  album.
- **J8. Delete on Records rows.** Swipe-to-dismiss with an Undo Snackbar, if
  J0 finds deletes reversible. Otherwise delete moves into a ⋮ overflow menu
  with a confirm dialog. Either way, the always-visible trash icon goes.
- **J9. Columns.** One full-width column in compact portrait. The existing
  `columns` parameter (`CartographyEntryListScreen.kt:57-58`) stays the only
  width knob.
- **J10. State survives.** The switch side, the Records chip, the Entries
  view mode and scroll position survive a tab change (CLAUDE.md, UX
  defaults) and a rotation (L7). Persistence across app restarts is not in
  scope.

**Components.** J1–J8 use stable Material 3 components:
`SingleChoiceSegmentedButtonRow`, `FilterChip`, `ExtendedFloatingActionButton`,
`SwipeToDismissBox`. Swapping them for Expressive ones (`ButtonGroup`, the FAB
menu) belongs to Understory step 5, which is gated on Gate G
(`understory-design-system.md:703`), not to this plan.

**Left out on purpose:**
- an all-entries map view: new capability, not a restyle, so a later owner
  question;
- a calendar view: speculative;
- the app-wide search header: untouched.

## Decisions — from the planner, short windows (L)

These apply where `isShortWindow()` is true: a phone on its side. The Journal
then sits beside the opaque rail, in at most 640 dp of width and about 360 dp of
height below the status bar. Height is the scarce axis
(`landscape-phone-design.md` P5).

- **L1. One pinned header row, 48 dp tall:** the Entries | Records switch, a
  search icon, and "✎ New" as an icon button.
- **L2. No floating button in short windows.** "✎ New", or "📷" on the album,
  moves into the L1 row. A 56 dp FAB in about 360 dp of height covers too much
  of the list, and sits where Snackbars dock.
- **L3. Drafts become a chip.** "✎ N drafts ›" sits in a second row with the
  view toggle (Entries) or the filter chips (Records). That row hides on
  scroll down and returns on scroll up.
- **L4. Entry cards turn sideways, two columns.** A 72 dp thumbnail on the
  left (photo, or a tinted type-icon panel), text on the right. The content is
  the same as J5; only the arrangement differs. That gives about six cards on
  screen instead of one.
- **L5. Records chips fit on one line** at 640 dp with full labels. Rows are
  one line, with the time right-aligned.
- **L6. The album grid is 5 columns** at 640 dp.
- **L7. State survives rotation.** Both orientations now render `JournalTab`,
  so no second copy of state is involved. What remains is whether `remember`
  state survives the configuration change, which J0 answers. If it does not,
  the J10 state moves to `rememberSaveable` or a hoisted saver.
- **L8. Insets.** No control within the `displayCutout` inset, and the rail
  side keeps the rail's own padding (`landscape-phone-design.md` P4, R17
  revised). Robolectric reports zero insets, so this is device-only.

**Dropped:** a list-detail layout, with the entry opening in a pane beside the
list. It conflicts with P11's 640 dp single-column cap and with R12's "no new
destinations" tree. It may still suit the wide tree (tablets, J6 stage), as a
future owner question.

## Mockups

Portrait, Entries (default):

```
┌──────────────────────────────────────┐
│ ╭──────────────────────────────────╮ │
│ │ 🔍 (app-wide header, unchanged)  │ │
│ ╰──────────────────────────────────╯ │
│ ╭────────────────┬─────────────────╮ │
│ │ ✓  Entries     │     Records     │ │  J1
│ ╰────────────────┴─────────────────╯ │
│ 3 entries · 5 finds         [☰][▦]  │  J3
│ ╭──────────────────────────────────╮ │
│ │ ✎  1 unfinished entry  Continue ›│ │  J2
│ ╰──────────────────────────────────╯ │
│ SEPTEMBER 2026                       │  J5 sticky header
│ ╭──────────────────────────────────╮ │
│ │▓▓▓▓▓▓▓▓ hero photo ▓▓▓▓▓▓▓▓▓▓▓▓▓▓│ │
│ │  26 │ Chanterelles along the ridge│ │
│ │ SAT │ Wet slope under Doug-fir…   │ │
│ │     │ [C. formosus] [B. edulis]   │ │  secondaryContainer
│ │     │ 🍄3  〰 4.2 km · 2h10m  📍1 │ │  J6 roles
│ ╰──────────────────────────────────╯ │
│ ╭──────────────────────────────────╮ │
│ │  14 │ Scouting Molalla  ╭────────────╮
│ │ MON │ 🗺 1 offline map  │✎ New entry │  J7
│ ╰───────────────────────╰────────────╯ │
├──────────────────────────────────────┤
│  ☰     ☀      🗺    (📖)    ⚙       │
└──────────────────────────────────────┘
```

Portrait, Records:

```
┌──────────────────────────────────────┐
│ ╭────────────────┬─────────────────╮ │
│ │    Entries     │  ✓  Records     │ │
│ ╰────────────────┴─────────────────╯ │
│ [All 17][🍄Finds 12][〰Tracks 3][📍Wa→ │  J4
│ Today   Sat, Sep 26 · 5 records      │
│ │[🍄] Cantharellus formosus      › │ │
│ │     6:42 PM · 3 photos           │ │
│ │[〰] Ridge loop                 ⤓ │ │
│ │     6:10 PM · 4.2 km · 2h 10m    │ │
│ │[📍] Start · 6:10 PM ➦ │ 🗑 Delete │  J8 swiped
│ Mon, Sep 14 · 2 records              │
│ │[🗺] Molalla                    ⋮ │ │
│ ┌──────────────────────────────────┐ │
│ │ Waypoint deleted            UNDO │ │
│ └──────────────────────────────────┘ │
└──────────────────────────────────────┘
```

Portrait, Entries › Album view: a 3-column photo grid under day headers, with a
🔗 badge on attached photos and the FAB reading "📷 Add photo".

Short window (phone on its side, rail on the port side, content ≤ 640 dp):

```
┌────┬───────────────────────────────────────────────────────────┐
│ ☰  │  [✓ Entries │ Records]                          🔍   ✎   │ L1
│ ☀  │  ✎ 1 draft ›                                   [☰][▦]    │ L3
│ 🗺 │ ┌───────┐ 26 SAT Chanterelles…  ┌───────┐ 26 SAT Morning…│
│(📖)│ │ photo │ C. formosus · B. edu  │ ~track│ 〰1.8 km · 48m │ L4
│ ⚙  │ └───────┘ 🍄3 〰4.2 km 📍1       └───────┘ 📍1            │
│    │ ┌───────┐ 14 MON Scouting Mol…  ┌───────┐ 09 WED …       │
│    │ │  🗺   │ 🗺 1 offline map       │ photo │ 🍄2            │
└────┴───────────────────────────────────────────────────────────┘
```

## Build order

Stages run in sequence, never in parallel: each appends to
`docs/audits/README.md` (CLAUDE.md, "a serialization point").

- **J0. Pulse (read-only).** Answer:
  1. The structure of `CartographyScreen.kt`, `RecordsTab.kt`,
     `FindsGalleryScreen.kt`, `PhotoGalleryScreen.kt` and `LogPanel.kt`.
  2. Whether each delete (waypoint, track, offline region, find, gallery
     photo) is reversible. This decides J8.
  3. Whether an entry's first attached photo can reach the list without a
     per-card load. This decides J5's hero.
  4. What a static track thumbnail per card would cost.
  5. Every test that selects Journal tabs by label text ("Cartography",
     "Drafts", "Album", "Waypoint Markers", "Offline Maps", "Recorded Tracks",
     "Logged Finds"), with a count.
  6. The callers of `pendingDestination` and `onFindsTabLeft`, and the three
     `BackHandler`s' conditions in `JournalTab.kt`.
  7. Whether the Activity is recreated on rotation (manifest `configChanges`),
     and so whether `remember` state survives it (L7).
  8. Whether the app-wide search header renders on the Journal tab in a short
     window after landscape B1/B2.
- **J1. Shared state and colour roles, then Records.**
  - Hoist the Journal UI state (J10, L7) and add the J6 style object.
  - Then build J4's chips and the All logbook, and the type badges on rows.
  - The Finds editing flow, its incidental-exit rule and `EDIT_NEW_FIND`
    routing must behave as today.
- **J2. The switch, the Drafts banner, the Album toggle and the floating
  button.** J1, J2, J3 and J7.
  - Back from Records steps to Entries.
  - Back from the album view returns to the timeline before leaving the
    Journal.
  - The FAB gets a coordinate-touch test sampling several points on the cards
    around it (CLAUDE.md, "A semantic `performClick` asserts wiring, not
    routing").
- **J3. Entry cards.** J5 and J9, from data `CartographyEntry` already holds.
  The hero photo is included only if J0 found it cheap. Tests assert the
  rendered text: title, species, stats.
- **J4. Delete.** J8, in the form J0 allows.
- **J5. Short windows.** L1–L8. **After landscape B3 lands**, since B3 owns
  the rail layout and the 640 dp cap for the Journal tabs
  (`landscape-phone-design.md` B3). Robolectric at `w823dp-h384dp-land`.
- **J6. The wide tree (tablets).** Bring `LogPanel` up to J1–J9. This is the
  lowest priority, and the list-detail question is raised with the owner
  first.
- **J7. Device check on the S22 Ultra, both orientations and both landscape
  rotations.**
  - Each screen, the Back path, draft Continue.
  - The map's "Log a find" routing.
  - Swipe and Undo.
  - Rotating on each screen and mid-find-edit, with nothing lost.
  - The FAB clear of the gesture bar in portrait.
  - The cut-out and rail sides in landscape, with 3-button and gesture
    navigation.
  - Six or more cards visible in landscape.

## Sequencing against other work

- **Landscape B2/B3.** These are in progress in another session. J1–J4 touch
  `JournalTab.kt`, `RecordsTab.kt` and `CartographyEntryListScreen.kt`. Check
  B3's diff for overlap before dispatching J1, and run J5 only after B3.
- **Understory step 5.** It may later swap J1/J4/J7's components for
  Expressive ones. This plan does not pre-empt that.

## Rules for every build stage

- **Tests.** A test that selects a tab by label is updated in the same commit
  to the new control, and reported with a count. Nothing is disabled, skipped
  or weakened.
- **Revert checks.** Each new test gets a revert check: restore from a saved
  copy, never from git; read the build log for compile errors before the
  results.
- **Push** at every stopping point.
- **Out of scope for every stage:**
  - the Room schema and migrations;
  - the find edit form and the entry report screen, beyond what routing to
    them needs;
  - the map screens.

## Open questions

1. J8's final form, once J0 answers reversibility.
2. The J5 hero photo and the track thumbnail: included or not, once J0
   prices them.
3. L7's mechanism, once J0 answers the rotation question.
4. The app-wide header in a short window (J0 question 8): if it renders, L1's
   48 dp budget needs to be recounted.
5. **Owner, later:** an all-entries map view; list-detail for the wide tree.

## Prior art

Cited as the planner used them in the planning session; not re-fetched by the
dispatch that committed this file.

- Material 3, Tabs guidelines: secondary tabs only below primary tabs, when
  more than one level is needed.
  https://m3.material.io/components/tabs/guidelines
- Nested tabs work only when the hierarchy is clear and shallow (citing
  Nielsen Norman Group). https://www.designmonks.co/blog/nested-tab-ui
- Day One: Timeline, Photos, Map and Calendar as views of one journal,
  switched from the top of the screen.
  http://help.dayoneapp.com/en/articles/840054-journal-views-in-day-one-for-ios
- The landscape rules this plan defers to: `docs/plans/landscape-phone-design.md`
  (P1, P5, P11, R12 revised, R17 revised) and its own prior-art section.

## Addendum, 2026-09-27: owner rulings in the implementing planner session

- Committed from planner session `bridge-cse_01BcqShzosraMo4pUXkqaqRp` by this dispatch. The originating session's commit dispatch (`2026-09-27-04`, in worktree `bridge-cse_013tPFtYR838ZpDY4QTeK8QA`) never ran.
- O6. L1–L8 approved. The owner chose "Approve L1–L8" when asked.
- O7. Order. The owner chose "Landscape B2, B3, then Journal". Journal stages start after landscape B3; the J0 pulse ran in parallel.
- O8. Committed here. The owner chose "A coder here commits it".
- Citation drift found while committing: none; all ten checked at `cff1309` hold. `JournalTab.kt:398-414` holds (the `SecondaryTabRow` opens at 398 and closes at 414); `:243` holds (the `RecordsSubTab`-typed `recordsPendingSubTab` latch); `CartographyEntryListScreen.kt:57-58` holds (`columns: Int = 2` and its doc comment); `:91` holds (the `AddCartographyEntryTile` item); `:97-119` holds (`AddCartographyEntryTile`); `:121-161` holds (`CartographyEntryTile`); `:163` holds (`ENTRY_TILE_ASPECT_RATIO = 0.85f`); `domain/model/CartographyEntry.kt:50-135` holds (`CartographyEntry` through `PhotoAttachment`); `understory-design-system.md:230-235` holds (the `primary` to `tertiaryContainer` role rows, dark primary `#A8CBA0`). None of these four files changed between `545258e` and `cff1309`.

## Addendum, 2026-09-27 (later): two new features, owner rulings in the cloud planner session

Asked by the owner while J4 was running, verbatim: "Add long press options for tiles to edit/delete them. Have an option for cartography entries to show up on the main map so the journal doesn't need to be opened every time. More than one entry may be active at a time on the main map."

Both are new capability, not restyling, so they are stages of their own and do not reopen J1-J4.

### J4b. Long-press menus on tiles (after J4)

- Tiles: **"Album photos, Find tiles, Entry cards"**. Records rows keep swipe only.
- Each opens a menu with Edit and Delete. Delete uses J4's delayed delete and Undo snackbar.
  - Find tiles (Records → Finds, and in All): Edit opens the find's editor. This supersedes J4's "Keep inside the report": delete stays in the report too, and is also on the tile.
  - Entry cards: Edit opens the entry editor. Delete gets the same delayed delete and Undo, which J4 did not cover for Cartography entries.
  - Album photos: Delete gets delayed delete and Undo, with the file delete deferred until the snackbar ends (J0 B1: a photo's file is gone at once today). What Edit means for a photo is **open**: the photo's details and location screen if one exists; the dispatch must find it and stop if there is none.
- Every menu is reachable by TalkBack, as J4's swipe rows are.

### J8. Journal entries on the main map (after J5)

- Content: **"Kept tracks, Kept waypoints, Kept finds, Offline-map outlines"**: an entry shown on the map draws all four kinds of its kept records.
- Toggle: **"Card menu + map chip (Recommended)"**. "Show on map" / "Hide from map" in the entry's long-press menu (J4b) and in its report. On the main map, a small chip shows how many entries are showing; tapping it lists them to hide one or all. More than one entry may show at once.
- Persistence: **"Yes, keep them (Recommended)"**. Stored with the entry in Room, since it belongs to an entry (CLAUDE.md, Room for data that relates). That is a migration: `ForagerDatabase` is at version 15 on this branch's base; the dispatch must check the version against `pre-main` at dispatch time, not this note (CLAUDE.md, verify globally-unique claims), and the new column lands with its reader in the same change.
- Open, for J8's pre-build pass: how entry overlays are told apart from live map markers and from each other (the J6 colour roles and C2 marker palette apply); whether drafts can be shown; what tapping an overlaid record does; where the chip sits in portrait and in short landscape without breaking the map-surface touch rules (CLAUDE.md, Surface intercepts touches); and whether the map's existing marker layers already draw any of these records.

### Order

J4 (running), then J4b, then J5 (short windows, which will need to place the map chip too if J8 lands first, so J5 stays before J8), then J8, then the single Journal PR. J6 (tablets) and J7 (device check) as the plan lists them; J7 gains the long-press menus and the map overlays.

### M1. Tap a map glyph for a bubble (after J5, before J8)

Asked by the owner, verbatim: "Tap glyphs to show a bubble that contains their info. (Finds, photo, track, waypointz etc). Tapping waypoints offers an option to navigate to them."

What the code does today (read by the planner at `journal-redesign` `b65b775`): only sighting dots respond to a tap. `ui/map/SightingsMap.kt:312-325` queries the tapped point against `SIGHTING_LAYER_ID` alone and opens `ObservationBubble` (`ui/availability/AvailabilityMapOverlaysUi.kt:357`); every other glyph layer (`SightingsMap.kt:1142-1159`: planned trips, waypoints, kept tracks, finds, photos, offline-region circles) is drawn and ignores taps. A waypoint row's "Directions" hands the location to an installed navigation app (`launchDirections`, `ui/availability/AvailabilityTripsWaypointsUi.kt:146`); the app also has its own navigation HUD.

Owner rulings:
- Glyphs: **"Finds and photos, Waypoints, Tracks, Planned trips & offline maps"**: every glyph kind gets a bubble with its info on tap, alongside the sighting bubble that exists.
- Navigate: **"In-app HUD, plus Directions (Recommended)"**: a waypoint's bubble offers Navigate, which starts Forager's own navigation HUD to it, and Directions, the existing hand-off to an installed app.
- Placement: **"Journal branch, before J8 (Recommended)"**: stage M1 on `journal-redesign`, after J5, so J8's entry overlays reuse the same bubbles; it ships in the single Journal PR.

Open, for M1's pre-build pass: what each bubble shows per kind (starting from what that record's row already shows); what tapping the bubble itself does (open the find, photo or track; J8 adds entry records); which glyph wins when several overlap at the tap point; how a bubble interacts with the sighting bubble and with long-press (which drops a point today, `SightingsMap.kt:334`); and the map-surface touch rules (CLAUDE.md), with coordinate-touch tests on the map.

### Order, revised

J4 (running), J4b, J5, **M1**, J8, then the single Journal PR.

### Photo details and location (future stage, not scheduled)

J4b found no photo edit screen, so the album's long-press menu is Delete-only. The owner chose **"Later, own stage (Recommended)"**: a photo details and location screen is a stage of its own, to be specified when scheduled (`updatePhotoLocationUseCase` is wired in `MainActivity` and would be where such a screen writes; J4b traced what reaches it).

### Photo editing: sequenced after the camera work (owner, 2026-09-27)

The owner, verbatim, on the photo details and location stage above: "Photo editing is a large project in itself, but not outside the scope of nature photography, so we can add it, but it will come after the central camera improvements land". So photo editing is in scope as its own project, larger than a details-and-location screen, and it starts only after "the central camera improvements" land. No plan in `docs/plans/` is titled for that camera work at the time of writing; which work the phrase names is to be confirmed with the owner when photo editing is scheduled, not assumed.

### Map layering framework (owner, 2026-09-27; to be specified)

The owner, verbatim: "Prepare the map for layering framework also. We are going to improve the forecast methods with layering based on several conditions, similar to how other prediction maps do."

Planner's placement, pending the owner's confirmation: the framework is built before M1 (glyph bubbles) and J8 (entries on the map), since both add map layers and would otherwise be built outside it and reworked. It is a framework for layers, not the forecast method itself; the forecast conditions and how they combine are a later, separate piece of work. Before specifying it, a read-only pulse maps today's layer composition in `ui/map/SightingsMap.kt` and the forecast data the app already has, and a prior-art pass records how established prediction maps structure layer controls. The owner's decisions on scope follow from those.

#### What `slayer8366/forager-forecast` already fixes for the layer framework (read 2026-09-27 at its `main` `876156b`)

The owner, verbatim: "Look at forager-forecast repo for details. It's pure R&D so what's there is very raw so far". Read by the planner; citations are to that repo. Its own status: planning complete, data audits done, no model fit and nothing published yet.

- **What reaches the app (D55, accepted with edits in D56, target confirmed as this app in D57).** Per forager group and ISO week, weather-cell polygons at 0.1 degree, published nightly as GeoJSON split into 1 degree blocks (one file per group per week per block, under a dated path named in a manifest), beside vector PMTiles, with a 250 m raster later. Cell properties by name: `group`, `week`, `chance` (0 to 1), `uncertainty_low`, `uncertainty_high`, `applicable`, `drivers` (a list of `{label, value}`, top first), `weather_through`, `model_version`. The manifest names groups (with GBIF and iNaturalist ids), the current week, the published ecoregions, the attribution text and the layer paths. The reason (D55): MapLibre's offline packs never download a source added at runtime and have no PMTiles path, so for offline use in a saved region the app fetches the blocks touching its areas and **stores them itself**.
- **What the app promises in return (D55).** The number is called "sighting chance" and nothing else, with a unit test searching the app's copy for forbidden terms; shown only beside its reference class; nothing drawn for an unscored cell, with "no forecast here" in the legend; nothing finer than the weather cell until a later decision allows the 250 m raster, labelled "relative habitat" with no percent; the attribution string shown on the map. Tap a cell: chance, uncertainty and data dates (SPEC acceptance; T11).
- **Standing rule for agents in this repo (D58).** Three phrases the forecast project forbids never reach `slayer8366/Forager` in code, strings, docs or commit messages, other than inside a rule or test that names them as forbidden. The planner checked `journal-redesign` on 2026-09-27: zero hits in files and in commit messages. Every dispatch touching forecast copy repeats this check. (D59: this app's own wording rules are the app's to redo after the integration.)
- **Gate.** Nothing derived from non-commercial-licensed records is published or shipped until the owner rules on commercial use (D22, D29). The framework can be built and tested with synthetic cells before that; no real forecast layer ships until it.
- **"Several conditions."** The model is habitat x trigger x observation (START_HERE), and each cell carries its top `drivers`. Whether the app also shows individual conditions as their own layers, as other prediction maps do, is the owner's call after the prior-art pass.

So the framework must give: layers the app feeds from data it stores (re-added after every style reload, basemap change and night-mode change), explicit z-order between the basemap and the markers, per-layer toggles, a legend with a "no forecast here" state and an attribution slot, and tap-to-query per layer, which is what M1's bubbles and J8's overlays need too.

**Correction to M1 above (2026-09-27).** "Long-press (which drops a point today, `SightingsMap.kt:334`)" is wrong: the long-press listener exists, but every production caller passes `{}`, so a long-press does nothing on any map today (`docs/audits/2026-09-27-map-layers-and-forecast-data-pulse.md`). M1 does not need to avoid a long-press action; it only needs to keep long-press free if a later stage wants it.

#### The owner's rulings on the layer framework (2026-09-27)

Asked after the pulse (`docs/audits/2026-09-27-map-layers-and-forecast-data-pulse.md`) and the prior-art pass (`docs/audits/2026-09-27-prediction-map-layers-prior-art.md`), verbatim answers:

1. Colour fields: **"Several, with opacity"**. Like Gaia and CalTopo: any number of colour layers on at once, each with its own opacity slider, and the user can reorder them. (Not Windy's one-at-a-time.)
2. Conditions: **"Both, like Waldschatzfinder (Recommended)"**. Sighting chance plus each condition (rain, soil temperature, soil moisture and the like) as its own layer, and tapping a cell shows the per-condition breakdown. This needs the forecast project to publish condition grids as well as sighting chance, a change to its D55 contract; that is decided in `slayer8366/forager-forecast`, which this session reads but does not write.
3. Layers menu: **"One sheet, two sections (Recommended)"**. The map's existing Layers button opens one sheet: "Map type" (pick one basemap) and "Overlays" (toggles for finds, waypoints, tracks, planned trips, offline maps, journal entries, and later the forecast layers).
4. Stage scope: **Option 1, "Framework + synthetic layer"**, with the owner's addition: "This will also require an update to the seasonal forecast panel to align with the model, otherwise it will be two sources of info competing." So the framework stage builds a generic layer model (order, visibility, per-layer opacity, legend, multi-credit attribution, tap precedence), moves today's markers onto it as toggleable overlays, adds a stored-data interface for downloaded cells, and proves it with a synthetic forecast layer behind a developer flag; no real forecast data. And the Seasonal tab's forecast panel (today's point-based conditions, trip windows and fruiting-lag figures, `ui/availability/AvailabilityResultsUi.kt`) must be brought into line with the model so the two do not compete; its timing is asked separately.

Stage name and place: **L0, the map layer framework**, after J5 and before M1 and J8, which become its first users. Every L0 dispatch repeats the D58 check for the forecast project's forbidden phrases, and no forecast copy lands without it.

Seasonal panel timing, the owner's answer: **"With the first real forecast (Recommended)"**. The Seasonal tab's forecast panel changes in the same stage that turns on real forecast data (after the commercial-use ruling), so the map and the panel switch to the model together; until then both keep today's figures. L0 does not touch the panel.

### Order, revised again

J4b (done), the picker and offline-maps fix stage (running), J5, **L0**, M1, J8, then the single Journal PR. Photo editing after the central camera improvements. Real forecast data, condition layers and the Seasonal panel's alignment wait on the commercial-use ruling and on the forecast project's D55 change.

### Device check timing (owner, 2026-09-27)

The owner, verbatim: "I'll run a device check after we are finished with this journal project". So every device-only item from B3, J1-J4b, the picker fix stage, J5, L0, M1 and J8 goes into one consolidated J7 checklist, run once at the end on the S22 Ultra. The planner assembles that checklist from each stage's completion report before the single Journal PR; items proven only by Robolectric are not claimed as verified until then. First among them: the find picker's pinch-and-pan (the only check that the new user-gesture signal is wired in the live map), and Back from a Records chip returning to All ("We'll try it and see if it works").

### Flake session after the Journal project (owner, 2026-09-27)

The owner, verbatim: "Sharing the root cause is a finding, it may guide us to the actual flake, so I'll run a separate session to run it down once the journal project is finished." So F6 (the intermittent album long-press menu tests, terminal `2026-09-27-64`) and the older `JournalTabTest` photo-pull flake are investigated together in one dedicated session after the Journal PR. Whether they share a root cause is the first question that session answers; either answer is a finding. Its starting data is in terminal `2026-09-27-64` and in `docs/audits/2026-09-27-picker-fixes-completion-report.md` ("Continuation: F6, held by the owner"): 6 failures in 18 class runs, all album long-press tests, the menu not opened in the two failures with detail. Until then every full-suite result is reported with its actual failure count, not as green.

### J5c. Tap a Records row for its details (owner, 2026-09-27)

After keeping today's rows in short windows ("Keep today's rows (Recommended)"), the owner added, verbatim: "Expanding my answer: have them display info upon tapping", and chose **"Bottom sheet of details (Recommended)"**: tapping a waypoint, recorded-track or offline-map row, in portrait and landscape, opens a bottom sheet with the record's full details and its actions. Scheduled as J5c, after J5 and before L0. The M1 ruling for waypoints (Navigate in-app plus Directions) applies to the sheet's waypoint actions too.

### In-app Navigate deferred (owner, 2026-09-27)

J5c's coder stopped on Navigate: starting the app's own navigation HUD for a waypoint from the Records tab needs a path the app does not have. The owner, verbatim: "We can defer the navigation for another time. It's going to need more work anyway", and for M1's waypoint bubble: "Defer for now too. It will go with a review in current waypoint navigation." So J5c's details sheet and M1's waypoint bubble offer **Directions only** (the existing hand-off to an installed navigation app). In-app Navigate, from both places, waits for a later review of the current waypoint navigation, which will specify the route. This supersedes the Navigate half of the M1 ruling "In-app HUD, plus Directions (Recommended)"; Directions stands.

### L0 design rulings (owner, 2026-09-27)

Asked before dispatching L0, verbatim answers:
1. Persistence: **"Across app restarts (Recommended)"**: which overlays are on, their opacity and their order are stored in DataStore (like Night Maps and fullscreen), so the map opens as it was left.
2. Legend: **"Collapsible chip, bottom corner (Recommended)"**: a small chip naming the active colour layer(s) in the bottom corner opposite the attribution; tap to expand the colour scale and "no forecast here"; clear of the icon cluster and the landscape rail.
3. Tap priority: **"Markers, then lines, then colour (Recommended)"**: a marker wins, then a track line or offline-map outline, then a colour cell only if nothing else is under the finger; the topmost layer wins within each group.
4. The synthetic layer's switch: **"Diagnostics screen (Recommended)"**: a toggle in the existing Diagnostics (debug build) screen, debug builds only, never in release.

L0 runs as two sequential stages: **L0a** (the layer model, explicit z-order, visibility and opacity, tap priority, today's markers moved onto it; no new UI) and **L0b** (the Layers sheet, DataStore persistence, the legend chip, the stored-data interface for forecast cells, the synthetic layer).

### Device checks per stage, on the S22 Ultra (owner, 2026-09-28)

Supersedes "Device check timing (owner, 2026-09-27)" above, which stands as the record of what was decided then. The owner, verbatim: "That ruling is now stale. You have direct access to the S22 ultra now, so use that for device checks". The planner session now has the test phone (SM-S908U, serial `R5CT321008R`) attached over adb, so a stage's device-only items are checked on it when the stage closes, rather than collected for one J7 run at the end. First to run: L0a's device-only list (`docs/audits/2026-09-27-map-layers-l0a-completion-report.md`, "Device-only"). The device-only items already collected from B3, J1-J4b, the picker fix stage, J5 and J5c are not yet checked; when they run is to be confirmed with the owner, not assumed from this ruling.

### L0b rulings (owner, 2026-09-28)

A read-only pulse at `f62eb3e` found five premises in "L0 design rulings" that the code does not match: the Layers button opens `MapModePicker`, a basemap-only popover, not a sheet (`ui/map/MapChrome.kt:126-193`); the Maps tab draws only sightings, planned trips, waypoints and the live-recording breadcrumb, while finds, photos, kept tracks and offline circles are drawn only on a Cartography entry map (`ui/availability/AvailabilityCompactMapUi.kt:598-606`, `ui/log/CartographyEntryReportScreen.kt:373-376`); the bottom corner opposite the attribution caption already holds MapLibre's own attribution button (`ui/map/SightingsMap.kt:449`) and, in the wide layout, the add button; the Diagnostics screen has no toggles and exists only in the debug source set; and L0b adds one colour field, so reordering could not be exercised. Asked, verbatim answers:

1. Overlays: **"Also show all finds etc."**: L0b also draws every find, photo, kept track and offline region on the Maps tab, so each overlay toggle acts on something. This widens L0b beyond the framework into a new Maps-tab feature.
2. First run: **"On by default"**: the new Maps-tab overlays are visible the first time the map opens after the update; the user switches off what they do not want, and the choice persists.
3. Drafts: **"Saved entries only (Recommended)"**: finds, photos and tracks from saved entries only, nothing from a draft until it is saved; offline regions are all drawn.
4. Entry map: **"Same sheet (Recommended)"**: the entry map's Layers button opens the same sheet, listing what that map draws, with one set of stored choices shared by both maps.
5. Legend placement: **"Bottom-right, above the 'i' (Recommended)"**: the ruled corner stands; the chip stacks above MapLibre's attribution button, and above the add button in the wide layout.
6. Synthetic switch: **"Debug-only source (Recommended)"**: the synthetic generator and its switch live only in the debug source set; the release build gets a forecast source that reports "no forecast data" explicitly, so no synthetic code ships in release.
7. Colour fields: **"Two synthetic layers (Recommended)"**: the debug switch provides two synthetic colour layers, so reordering, stacked opacity and a two-entry legend can be checked on the S22.
8. Stage size: **"One stage"**: L0b stays one stage with one device check at the end, not split.

Not asked, and so unchanged: the basemap choice stays session-only (`ui/availability/AvailabilityScreen.kt:828`), since the persistence ruling names overlays, their opacity and their order; and a reorder takes effect at the next style load, as accepted in terminal `2026-09-27-72` (Deviations 4). Open for J8: J8 draws the kept records of selected entries, and L0b now draws all saved entries' records by kind, so how the two relate on the Maps tab is J8's pre-build question. Open for M1: tapping a forecast cell (chance, uncertainty, data dates, from forager-forecast's acceptance) has no stage yet; M1's bubbles are the natural home.

**Ruling 3 clarified (owner, 2026-09-28).** A find is its own record with its own draft state (`MushroomLogEntry.isDraft`), and a Journal day entry keeps or withholds references to finds, tracks, waypoints and regions (`domain/model/CartographyEntry.kt`), so "saved entries only" had two readings. Asked, the owner chose **"Every saved record (Recommended)"**: the Maps tab draws every saved (non-draft) find, every track in Records, every album photo with a location and every offline region, whether or not a Journal entry keeps it. A find withheld from an entry still shows on the Maps tab; per-entry display is J8's. This does not touch the rule that an entry's own map geometry comes only from `GetCartographyEntryMapDataUseCase`: the Maps tab draws records, not entries.

### L0b forecast-facing rulings, after reading forager-forecast (owner, 2026-09-28)

The owner, verbatim: "Look at forager-forecast repo for details on what the layering is for." Read at forager-forecast `876156b` (`docs/audits/2026-09-28-forager-forecast-layering-read.md`): the layers exist to show a calibrated weekly **sighting chance** per forager group per 0.1 degree weather cell, only where the model beats a seasonal calendar, blank where it cannot score, beside its reference class, with chance, uncertainty, top drivers and data dates on tap; condition layers have no data source in the D55 contract yet, and R8 gives a percent only to the chance layer. Asked, verbatim answers:

1. First synthetic layer: **"Real format, 'test data' name (Recommended)"**: drawn and labelled exactly as the real layer will be (percent, reference-class sentence, "no forecast here", week and data dates), but named as test data so the fixed term "sighting chance" is never attached to fake numbers. Debug builds only.
2. Second synthetic layer: **"A second group (Recommended)"**: a second group's forecast in the same format, which is the published data's real shape (one file per group per week), instead of a generic condition. Chanterelles and chicken of the woods, the forecast project's first two groups (D9).
3. Cell tap readout: **"In M1, with the bubbles (Recommended)"**: M1 builds the forecast-cell readout (chance, uncertainty, top drivers, data dates) as one more bubble kind; L0b builds no tap UI.
4. Reference class: **"Use that wording (Recommended)"**: the expanded legend carries, verbatim, "Chance this group is reported in each 11 km cell this week, where anyone is reporting fungi. Compare areas, not spots. A high chance is not a find." (`slayer8366/Forager-app` `0172c33`, `presentation/src/main/kotlin/com/zynergy/forager/presentation/SightingChance.kt:33-35`), until the forecast project fixes its own wording.

Planner's additions from the same read, stated in the L0b release message rather than asked: the cell store is keyed by group, week and 1 degree block; synthetic cells are centred on multiples of 0.1 degree (edges at x.x5, as ERA5-Land's grid gives) and belong to the block holding their centre; some synthetic cells carry `applicable` false and some are omitted, both drawing nothing; the lowest ramp colour must read as clearly different from an empty cell. Open, and not this repository's to settle: condition layers (owner ruling 2 on the layer framework) need a new decision in forager-forecast changing D55; group and week selection has no ruling in either repository.

### Commercial use: the owner's ruling (2026-09-28)

Asked what D29 in forager-forecast is about (its licence gate: nothing derived from CC BY-NC records is published or shipped until the owner rules on commercial use; forager-forecast `docs/planning/DECISIONS.md:39` at `876156b`, applied record by record by D48), the owner said, verbatim: "I'll be selling the app, but that particular feature will be free. What license do you recommend?" The planner's answer, not legal advice: a free feature inside a paid app most likely still counts as commercial under CC BY-NC 4.0 ("not primarily intended for or directed towards commercial advantage or monetary compensation"), so the options were a model built only from CC0 and CC BY records, or a separate non-commercial product. The owner, verbatim: **"Commercial-safe model. I'll get an open-meteo subscription for their service"**.

What this means here:
- The forecast Forager ships is trained and scored only on CC0 and CC BY records: D29's secondary, commercial-safe analysis becomes the shipped model, and the all-licence analysis stays research. The ruling belongs to forager-forecast (answering D29 and D22); the handoff `prompts/preserved/2026-09-28-08.md` carries it there, since this repository's sessions only read that one.
- Attribution the app must carry once real data ships: the GBIF download citations with their DOIs (CC BY), the Copernicus text of forager-forecast D53, and CEC ecoregions (CC BY) if drawn. L0b's multi-credit attribution list is where they go.
- **Open-Meteo:** its free API is non-commercial (forager-forecast `docs/planning/DATA_REGISTER.md:9`, quoting "You may only use the free API services for non-commercial purposes"). The app calls the free hosts today: `api.open-meteo.com` (`data/remote/OpenMeteoClient.kt:14`) and `archive-api.open-meteo.com` (`data/remote/OpenMeteoArchiveClient.kt:18`). With the owner's subscription, the app must move to the subscriber endpoints and key before it is sold. **Not scheduled; needed before release.** Its pre-build pass decides, from Open-Meteo's own documentation at the time (unverified here: the subscriber hosts and how the key is passed), how the key is kept out of the repository and whether it is called from the app or through a proxy the owner runs, since a key inside an APK can be extracted.
- **Open, unverified:** iNaturalist's API terms for a commercial app, and the licences of any observation photos the app displays. To check before release.

### Leaving the Journal: the owner's rulings (2026-09-28)

From `docs/audits/2026-09-28-leaving-the-journal-investigation.md` (13 characterisation tests on branch `leave-journal-investigation`, `a89b240`). Asked, verbatim answers:

1. The Discard data loss (viewing a committed find, then leaving the Journal, shows "Saved to Drafts" whose Discard deletes the committed find, no Undo): **"Snackbar only for real drafts (Recommended)"**: only viewing shows no snackbar; the snackbar appears only when a draft was really saved, and its Discard can only delete that draft, never a committed find.
2. A day entry left in its editor: **"Return to the editor (Recommended)"**: returning to the Journal reopens it in its editor with the unsaved changes; the report view never draws unsaved edits as if saved; leaving the editor by Back with unsaved changes asks Save or Discard.
3. Finds across a tab change: **"Keep finds open too (Recommended)"**: a find open in view or edit is still open on return, as day entries now are; the Maps search bar stays visible (the gate at `AvailabilityCompactScaffold.kt:401` must not hide it for a find open on another tab).
4. Order: **"M1 first"**: M1 is built next; these three fixes follow, then the two landscape bugs from backlog Part B (the Offline Maps picker unusable in landscape; the entry report's layout in landscape).

### M1 rulings (owner and planner, 2026-09-28)

After the M1 premise pulse (`docs/audits/2026-09-28-m1-premise-pulse.md`), the owner's verbatim answers:

1. Tap result: **"Bubble only (Recommended)"**: a glyph tap opens its bubble and nothing else; `onTap` no longer fires after a feature tap, as sighting taps already behave (settles terminal `2026-09-27-72` Deviations 3).
2. Bubble tap: **"Open in place (Recommended)"**: waypoints, tracks and offline maps open the J5c details sheet on the Maps tab; a photo opens the photo viewer in place; a find's bubble has "Open in Journal", which switches to the Journal with that find open.
3. Entry map: **"Yes, same bubbles (Recommended)"**.
4. Non-records: **"Not tappable (Recommended)"**: the search-centre reticle and the live recording trail stop taking taps.

Planner's rulings, not asked, open to change:
- one generic bubble shell, reusing `AnchoredAtScreenPoint`, with one bubble on screen at a time (a single tapped-thing state that includes sightings);
- the tail's tip always lands on the tapped feature: when the clamp moves the bubble, the tail moves with the anchor, not with the bubble;
- the anchor is the tap point, re-projected on camera idle for point features;
- forecast cells become tappable (the forecast acceptance "a tapped cell shows the same numbers as the scoring table") in a group that loses to any marker or line within the box, so the owner's "point, then box" rule stands;
- the cell bubble looks the cell up by re-querying the store by group, week and block, not by parsing the id;
- a waypoint bubble offers Directions only (In-app Navigate deferred);
- a planned trip's bubble shows what its Trip Planner row shows, with Directions, and opens nothing further (there is no trip target);
- bubble content per kind starts from what that record's row or sheet already shows.

### Track widths by zoom and the entry map's opening frame (owner, 2026-09-28)

The owner, verbatim, over Maps-tab and entry-map screenshots: "At some point in zooming out, the tracks get muddied up from the thickness + distance. Can we have the track lines thin out as we zoom out?" and "When opening an entry, the user expects to see their tracks on the map, maybe the last tracks they recorded. But instead they're greeted with a blank map as shown. Can the entry map open to their last recorded track location, zoomed in to where you see in the next photo?"

Asked, verbatim answers:
1. Framing: **"Fit all kept records (Recommended)"**: the entry map opens framed to everything the entry keeps (tracks, finds, photos, waypoints), as close as fits them; offline-region circles are left out of the framing unless the entry keeps nothing else.
2. Timing: **"Right after M1 (Recommended)"**: both are built after M1 lands and before the Leaving-the-Journal fixes, since they touch the same map files M1 is changing.

Revised order: M1 (running); then track widths by zoom and the entry map's opening frame; then the three Leaving-the-Journal fixes; then the two landscape bugs. The widths' zoom stops are the planner's to propose in the dispatch and the owner's to judge on the phone.

### Search bar copy: "Search a location" (owner, 2026-09-28)

The owner, verbatim: "the \"September · no location set\" in the search bar is accurate, but it reads awkward, like an error (especially after the user gives their permission to set their location on the map). Change it to \"September · Search a location\"", and the principle: "while bare accuracy is an easy thing to settle on, since it fulfills the honesty part, we need to go one step beyond that and give the user a spark or motivation to action based on that honesty. Like, sure that data exists, but what can they do about it? How can it be useful? In this case, they can search a location to satisfy the \"no location set\"."

The string is at `ui/availability/AvailabilitySearchUi.kt:497` (the `?: "no location set"` fallback); no test pins it (`git grep` over `app/src/test`, 2026-09-28). Built with the track-width and entry-framing stage right after M1. The principle applies to every empty or missing-state string reviewed from here on, within the forecast project's fixed terms.

### J8 rulings (owner, 2026-09-28)

After the J8 premise pulse (`docs/audits/2026-09-28-j8-premise-pulse.md`), which found that the Maps tab already draws every saved record (L0b), that portrait entry cards swipe rather than long-press, and that the map palette has no spare role, the owner's verbatim answers:

1. Standing out: **"Highlight in place (Recommended)"**: a shown entry's tracks, finds, photos and waypoints are highlighted in place with a halo or outline in one shared "journal entry" colour; everything else is unchanged.
2. Toggle: **"Entry report menu (Recommended)", "Maps-tab chip (Recommended)", "Layers sheet switch"**: "Show on map" / "Hide from map" in the entry report's menu; a Maps-tab chip listing the shown entries, to hide one or all; and a "Journal entries" switch in the Layers sheet's Overlays that turns all shown entries' highlights on or off together. Not the card swipe.
3. Bubble: **"Name entry + Open entry (Recommended)"**: a highlighted record's bubble names the entry or entries it belongs to and offers "Open entry".
4. Drafts: **"Saved entries only (Recommended)"**.
5. Chip: **"Top, by the species chip (Recommended)"**: in the row with the taxon chip, under the compass strip or search bar.
6. Geometry: **"Live records (Recommended)"**: the highlight follows the live waypoint and region positions; a kept record since deleted is simply not highlighted.

Earlier J8 rulings stand: content "Kept tracks, Kept waypoints, Kept finds, Offline-map outlines"; more than one entry may show at once; "Yes, keep them" in Room. Planner's rulings: the migration rebuilds `cartography_entries` in the pattern of 12 to 13 and 14 to 15 (the legacy fixtures declare the entity directly, `Migrations.kt:899-906`); the highlight colour is one new palette role, day and night, proposed by the planner and measured in `MapPaletteTest`, for the owner to judge on the phone.

### Map chrome at 80%, nothing fully obstructs the map (owner, 2026-09-28)

The owner first asked about one sheet, verbatim: "Can you set this card at 80% opacity while over the map? In the places that aren't covering a map, they can stay solid. Same for tracks please". That came with screenshots of the waypoint and track details sheets opened from a map bubble. The owner then gave rulings: **"After J8 (Recommended)"** for the timing and **"Yes, all three (Recommended)"** for including the offline-region sheet. The planner queued it as intent `2026-09-28-56`.

The owner then widened it to a principle, verbatim: "My idea is that nothing should fully obstruct the map view. All map chrome gets 80% opacity as a result."

What the planner takes this to mean, pending the owner's answers on edge cases:
- Every surface drawn over a map is at `MAP_CHROME_OVER_MAP_ALPHA` (0.8, `ui/map/MapChrome.kt:239`). That covers bars, strips, chips, clusters, rails, legends, bubbles, sheets, drawers and menus.
- The same component shown where it covers no map stays solid, as in the owner's first message.
- The icon cluster (`MapChrome.kt:233-236`) and the Layers sheet (`MapLayersSheet.kt:221`) already follow it.

Before the dispatch widens, a read-only pulse inventories every surface drawn over a map (`docs/audits/2026-09-28-map-chrome-inventory-pulse.md` when filed). Edge cases the pulse surfaces, such as dialogs, text fields and drawers, go to the owner before anything is built. Dispatch `2026-09-28-56` stays queued behind J8, and it will be widened by a continuation, not rewritten.

**Edge-case rulings (owner, 2026-09-28).** These were asked after the map-chrome inventory pulse (`docs/audits/2026-09-28-map-chrome-inventory-pulse.md`, read at `76a67f3`). Verbatim answers:
1. Dialogs, pop-up menus and the snackbar over a map: **"80% over the map"**. This was chosen against the planner's recommendation to keep them solid.
2. The Tools drawer: **"80% over Maps (Recommended)"**. It is solid over the other tabs.
3. The accent buttons (the + disc, the record disc, the wide Add button): **"Keep them solid (Recommended)"**.
4. The attribution caption at 0.55: **"Leave it at 55% (Recommended)"**.

Planner's readings, stated to the owner, stand unless the owner overrules them:
- Full-screen destinations the user opens stay opaque: the photo viewer, the find-over-view page and the camera. They are destinations, not chrome.
- Scrims are unchanged.
- The cluster's 0.6 container is unchanged, since it composites to 0.8 by design (`MapChromeAlphaTest`).
- The five `0.8f` literals move onto `MAP_CHROME_OVER_MAP_ALPHA`, with no visible change.

Dispatch `2026-09-28-56` is widened by continuation `2026-09-28-58`, and still waits for J8.

**Accent buttons, superseding edge-case ruling 3 (owner, 2026-09-28).** Ruling 3 above ("Keep them solid (Recommended)") stands as the record of what was decided then. The owner then reversed it, verbatim: "Have the colored buttons be at 80% opacity also". That covers the + disc, the record disc while recording, and the wide Add button.

The + disc sits on the icon bar and the record disc on the control pill. Both are already 0.8 composites, so a disc at 0.8 on top would stack to about 0.96. Asked how the discs should reach 80%, the owner answered "[No preference]". The planner ruled **true 80% overall**: the map shows through each disc as much as through the rest of the chrome. The fill beneath is not stacked under the disc, which follows CLAUDE.md's rule that layered fills composite to 0.8 rather than each carrying it. The wide Add button sits straight on the map, so its own fill at 0.8 is already 0.8 overall. Carried by continuation `2026-09-28-59` of `-56`.

**Correction: the "colored buttons" are the bottom tab bar (owner, 2026-09-28).** This supersedes the "Accent buttons" paragraph above, which stands as the record of the planner's misreading.
- The planner's edge-case question 3 named the + disc, the record disc and the wide Add button, and the owner's "Have the colored buttons be at 80% opacity also" was recorded against those.
- The owner then clarified, verbatim: "The bottom colored buttons", "Leave the map icon bar alone", and, with a screenshot circling the bottom tab bar (List, Seasonal, Maps, Journal, Tools): "These buttons at the bottom must be 80% opacity".

So:
- The accent discs and the wide Add button **stay solid**. Edge-case ruling 3 stands again, and the icon bar and control pill are untouched.
- **The bottom tab bar's buttons go to 80% over the map.**
  - The bar's own container is already `surfaceContainer` at a literal 0.8 (`AvailabilityCompactMapUi.kt:1210`).
  - The selected tab's highlight (the brown pill behind Maps) is Material3's default indicator colour, solid. Only the icon and label colours are overridden (`AvailabilityNavigationUi.kt:167-170`). The highlight goes to 80% over the map.
  - Icons and labels stay opaque, as UX defaults require.
- The planner applies the same to the navigation rail, which stands in for the bar in short landscape. That is the planner's reading, stated to the owner.
- Whether the bar's 0.8 container actually shows the map through on the S22 is a device check. The screenshot cannot settle it.

Carried by continuation `2026-09-28-60`, which supersedes `-59`.

**Restart at the edge-case rulings (owner, 2026-09-28).** The owner sent a screenshot of the four edge-case answers as first given, verbatim: "Let's restart from this point and forget everything beyond it:". Those answers were dialogs, menus and snackbar "80% over the map", the drawer "80% over Maps (Recommended)", the accent buttons "Keep them solid (Recommended)" and the caption "Leave it at 55% (Recommended)". So:
- The "Accent buttons" paragraph and the "Correction" paragraph above are **withdrawn**. They stay in the file only as the record of what was withdrawn.
- Edge-case rulings 1 to 4 stand as first answered.
- Continuation `2026-09-28-58` is the governing amendment of `-56`. Continuations `-59` and `-60` are withdrawn (continuation `2026-09-28-61`).

### J6 before the Journal PR (owner, 2026-09-28)

The owner, verbatim: "We should do J6 before the PR". J6, the wide tree for tablets (bringing `LogPanel` up to J1–J9), now runs before the single Journal PR. It is no longer left unscheduled at lowest priority. The planner's placement, stated to the owner, is:
1. J8, then J8's device check.
2. The map-chrome dispatch (`2026-09-28-56` as amended by `-58`), then its device check.
3. Stage device check Part 2.
4. J6.
5. The single Journal PR.

As "Build order" says, the list-detail question goes to the owner before J6 is dispatched. A read-only J6 premise pulse, filed as `docs/audits/2026-09-28-j6-premise-pulse.md`, maps the wide tree first, so the question can be asked with real options. J6's own dispatch re-verifies after J8, since J8 touches `LogPanel.kt` for its Open-entry route.

**J6's placement, revised (owner, 2026-09-28).** This supersedes the placement in the paragraph above, which stands as the record of the planner's first placement. The owner, verbatim: "J6 after the map chrome change, during phone check part 2". The order is now:
1. J8, then J8's device check.
2. The map-chrome dispatch (`-56` as amended by `-58`), then its device check.
3. Stage device check Part 2 on the S22, with J6 building alongside it at the same time.
4. The single Journal PR.

J6 builds under Robolectric and takes no phone, so the two can run together, as J8 and device check Part 1 do now. The planner kept the map-chrome device check as its own step before Part 2 and stated that to the owner. Folding it into Part 2 is the alternative.

**The map-chrome device check stays its own step (owner, 2026-09-28).** Told that the planner had kept the map-chrome device check as its own step before Part 2, rather than folding it into Part 2, the owner answered verbatim: "Confirm your assumption." The planner reads that as confirming it. The order in the paragraph above stands as written.

**J6's device check on the owner's tablet (owner, 2026-09-28).** The owner, verbatim: "When J6 is ready to test let me know. I have a tablet to use for testing". J6's device-only items run on the owner's tablet, not the S22, which has no wide window. The planner tells the owner when J6 is ready to test and does not start a tablet check unprompted. The tablet's model, build and window class are read with `getprop` and `dumpsys window` when it is connected; they are not known yet.

### J8's flags: the owner's rulings (2026-09-28)

Asked after J8's terminal (`2026-09-28-66`), the owner answered verbatim "1 A / 2 A / 3 A / 4 A":
1. **The landscape chip overlap** (in short landscape the chip's ruled spot overlaps the icon cluster on its default side and takes the touch there, as the taxon chip does): **left for J8's device check to judge.** It goes on that check's list.
2. **Entry save failures never shown** (Cartography's `saveErrorMessage` is never displayed or cleared; this predates J8): **fixed as a small stage of its own**, intent `2026-09-28-68`. Build stages run in sequence, so it queues behind the map-chrome build.
3. **"Kept in" twice on a highlighted photo's bubble** (the existing line counts all keeping entries, J8's counts the shown ones): **keep both.**
4. **Start the map-chrome build while the phone is busy with Part 1: yes.** The map-chrome stage (`-56` as amended by `-58`) launches now at `99de6c2`, ahead of J8's device check. It needs no phone.

The order is now:
1. The map-chrome build, and Part 1 finishing on the phone, at the same time.
2. The save-failure stage (`-68`).
3. J8's device check and then the map-chrome device check, each as the phone frees.
4. Device check Part 2, with J6 alongside.
5. The single Journal PR.

**J8 flag 3 revised: one "Kept in" on a highlighted photo (owner, 2026-09-28).** This supersedes ruling 3 in "J8's flags" above ("keep both"), which stands as the record of that answer. After the planner explained the case, the owner answered verbatim: "Option B. Thanks for explaining". The planner had offered B as: "When a photo is highlighted, drop the first line's count and keep only the tappable one."

What that means, as the planner stated it to the owner:
- When a photo's bubble shows J8's keeping-entry lines, its attachment line leaves out its "Kept in N journal entries" part and keeps its "In ..." part.
- If nothing is left, the line is left out, and never falls back to "Not in a find or a journal entry".
- Unhighlighted photos are unchanged.
- Entries not shown on the map are not counted while the photo is highlighted.

Carried by intent `2026-09-28-70`, queued behind `-68`.

### J6 rulings (owner, 2026-09-28)

Asked after the J6 premise pulse (`docs/audits/2026-09-28-j6-premise-pulse.md`, read at `14ea159`), the owner answered verbatim "1 A / 2 A / 3 A":
1. **Layout: list-detail.** The planner offered: "The list stays in the left column, and whatever you open (an entry's report, a find, the editor) opens in the big area on the right while it's open." That is how Gmail, Keep and Samsung Notes work on tablets, and it gives an entry's own map real room.
2. **The old "Photo Gallery" panel is removed.** Only the album remains, as on the phone.
3. **The narrow map on mid-size tablets is fixed as part of J6.** Beside the 360 dp drawer and the 360 dp species list, the map is about 119 dp at 840 dp and nothing at 721 dp or less. That is the pulse's arithmetic, not observed.

The owner added, verbatim: "Before the J6 device check, perform a device check for a sanity test to see how much of a issue the layout sizes are." The planner places this **before J6 is dispatched**, so its measurements feed J6. It runs on the owner's tablet, because the S22 never takes the wide tree. It waits for the owner to connect the tablet and to answer the install, data and first-run-prompt questions. J6's dispatch follows it.

**The sanity test is J6's device check, not a check before it (owner, 2026-09-28).** This supersedes the last paragraph of "J6 rulings" above, which placed a separate tablet check before J6 was dispatched and stands as the record of that misreading. The owner corrected it, verbatim: "Sorry, I meant, "for J6, perform a device check for a sanity test to see how much of a issue the layout sizes are. My answers otherwise are: 1 A 2 A 3 A".

So:
- There is no pre-J6 check.
- **J6's own device check on the owner's tablet includes a layout-size sanity test.** It measures each pane's real width in both orientations: the Journal list, the detail pane, the species list, the map and an entry's own map. It checks them against the pulse's arithmetic and against J6's narrow-map fix, with screenshots for the owner to judge.
- The rulings 1 A, 2 A and 3 A stand.
- The questions about installing on the tablet, test data and first-run prompts wait until J6 is ready to test, when the planner tells the owner.
- J6 can be dispatched from the plan and the pulse, and is queued as before.

**A separate tablet sanity check before J6, and J6 waits for it (owner, 2026-09-28).** This supersedes the paragraph above ("The sanity test is J6's device check, not a check before it"), which stands as the record of that reading. The owner, verbatim: "Do a separate check before J6 / I'll connect the tablet for the check before J6. Do not start J6 without that sanity check".

The owner also answered the planner's tablet questions, verbatim:
- "1 not in yet, I'll work on it and tell you": the tablet is not connected yet.
- "2 B": Forager is already on the tablet; install over it and keep its data.
- "3 A": the agent creates a few items labelled "DEVICE CHECK" inside Forager on the tablet and leaves them for J6's check.
- "4 A": at any first-run or system prompt the agent stops, and the owner taps through.

So:
- The tablet sanity check is intent `2026-09-28-74`. It launches when the owner says the tablet is connected.
- **J6 is not dispatched until that check's run record is in.**
- J6's own tablet check after the build stands as well.

### Map-chrome questions, Part 1's fails, and the night outline (owner, 2026-09-28)

The owner answered verbatim "1 A / 2 A fix all / 3 A rework the outline only. The fill color and opacity is fine as is. The outline should have a white border":
1. **The species-suggestions and Month menus stay stacked.** They sit on the 0.8 search panel, and each is at `MAP_CHROME_OVER_MAP_ALPHA` on its own over the Maps tab. Over the panel the two layers compose to about 0.96. This is the owner's exception to CLAUDE.md's composite line for these two menus. The planner's other answers to the map-chrome coder's stop, stated to the owner and not overruled, are carried by continuation `2026-09-28-77`:
   - "covers a map" means a map is drawn on screen beneath the surface;
   - the Records details sheet is at 0.8 from the Offline maps sub-tab, and its wide case is left to J6;
   - on the Maps tab with no map yet drawn, surfaces follow the tab;
   - the exit prompt on wide follows the same rule.
2. **All six of Part 1's layout fails and flags are fixed in one stage**, intent `2026-09-28-78`:
   - the legend chip over the record button at 270;
   - the cluster over the expanded legend;
   - the portrait search dropdown under the keyboard;
   - the map camera resetting on a tab round trip;
   - MapLibre's "i" under the nav bar;
   - the attribution strip across the cluster at 90.
3. **The night offline region: the outline only is reworked, with a white border.** The fill's colour and opacity stay as they are. The planner reads this as night only, since the question was the night region; the day outline is unchanged. Intent `2026-09-28-79`.

The build queue, one at a time:
1. the save-failure fix (running);
2. the map-chrome stage (resumed);
3. `-70`, one "Kept in";
4. `-78`;
5. `-79`;
6. J6, after the tablet sanity check.

**Night only, confirmed (owner, 2026-09-28).** The owner, verbatim: "Yes the night outline only". The white border in `-79` applies to the night outline alone. The day outline is unchanged.

**The tablet photo-viewer measurement, deferred (owner, 2026-09-28).** The tablet sanity check (terminal `2026-09-28-80`) could not measure the photo viewer, because Forager on the tablet held no photo. The planner offered three options: take a camera photo, import one of the owner's, or leave it unmeasured. The owner, verbatim: "Defer it for now. I'm not near my tablet. I'll let you know when I am and we can revisit that question". It stays open until the owner raises it. It does not block J6, since the terminal cleared J6 to dispatch.

### J8 device check: the owner's rulings (2026-09-28)

Asked after J8's device check (terminal `2026-09-28-81`), the owner answered verbatim "1 A / 2 A / 3 A leave for now, I'll review on device with full colors":
1. **Marker rings move below every line,** so a ring never covers a kept track or other line. This joins `-70`, which becomes **J8 follow-ups** (continuation `2026-09-28-87`), together with the two planner-ruled J8 defects:
   - rings on the ORIGIN and END waypoints the map does not draw;
   - the chip reading about 0.84 against the composite rule.
2. **The chip covering the cluster's reset button at 90 is fixed.** So are the portrait dead touch band between the chip and the coordinate readout, and the cluster's top row over the search bar's ends in landscape. These join `-78` (continuation `2026-09-28-88`).
3. **The highlight colours** (day `#005577`, night `#00DDFF`) **stand for now.** The owner will review them on the device in full colour; that stays open.

Build line, one at a time:
1. `-79`, the night outline (running);
2. `-70`, J8 follow-ups;
3. `-78`, the layout fixes;
4. J6.

The map-chrome device check (`-84`) and the tablet photo-viewer check (`-86`) run on the devices meanwhile.

### Planned trips on the map: the owner's ruling (2026-09-28)

The owner reported that planned trips never appear on the Maps tab ("They do not appear at all", on the S26). The planned-trips pulse (`docs/audits/2026-09-28-planned-trips-pulse.md`) traced it to a gate on the compact Maps tab, added in `7692527` (2026-08-18): `plannedTrips = if (hasSearched) uiState.plannedTrips else emptyList()` (`AvailabilityCompactMapUi.kt:641`).

The owner ruled, verbatim: **"Option A for the fix"**. The planner had offered A as: "Always draw saved trips, like waypoints and finds, whether or not a search has run."
- The fix is intent `2026-09-28-97`. It is queued until device check `-94` (S22) or `-95` (tablet) reproduces the failure, and behind the layout fixes (`-78`), which edit the same file.
- Sightings stay gated on a search.
- The wide tree draws no map at all before a search (`AvailabilityWideLayoutUi.kt:239-243`). That is a layout question for J6, not part of this fix.

**The icon cluster in short landscape (owner, 2026-09-28).** The layout-fix coder found one cause behind five items. In short landscape the icon cluster (the 264 dp MapIconBar, an 8 dp gap and the 108 dp ControlPill, 380 dp in all) is taller than the S22's map area (about 354 dp), so it collides with the bar, chip, legend and caption on its side (stop report `b0fb443`).

The owner ruled, verbatim: **"For icon column in short landscape: option A"**. The planner had offered A as: "Reshape the column in landscape. For example, put the record and return pill beside the icon bar instead of below it, so the column is about 264 dp tall and fits with room to spare. Nothing else moves."

Carried by continuation `2026-09-28-99` of `-78`. Portrait is unchanged.

### A decorations band for the rings (owner, 2026-09-28)

The J8 follow-ups (terminal `2026-09-28-92`) moved the J8 marker rings into the LINES z-group, below every line. Asked whether a band is a height or a kind, the owner chose, verbatim: **"Option C: decorations to keep it separate. We can change it if the forecast layering needs changes"**.

The owner's L0a ruling was "Accept: markers above lines (Recommended)". "The four groups stand as written" was the planner's restatement in `prompts/preserved/2026-09-27-30.md:14`, and the planner told the owner so. The new band does not touch "markers above lines". The bands become colour fields < areas < **decorations** < lines < markers.

The planner's reading, stated to the owner:
- **decorations holds the J8 marker rings** (finds, photos, waypoints);
- the J8 **line halos** (track and region outline) stay in LINES, directly beneath their own lines;
- **nothing on screen changes**.

Intent `2026-09-28-100`. It can be revisited if the forecast layering needs changes.

### Map-chrome device check: the owner's rulings (2026-09-28)

After the map-chrome device check (terminal `2026-09-28-102`), the owner answered verbatim "1 A 2 A":
1. **The Records details sheet from the Offline maps panel is 0.8 only in short landscape,** where the picker map sits beside the list. **It is solid in portrait,** where it lies over the list. This supersedes the planner's Q1 (b) ruling in `-77`, which assumed a map beneath and was the planner's error.
2. **The device check's small layout flags are batched into one fix stage** after the layout fixes (`-78`), as intent `2026-09-28-104`:
   - the search notice under the compass strip or cluster;
   - a bottom sheet's opaque nav-bar band;
   - the species suggestions ignoring Back;
   - the landscape pin row under the nav bar;
   - the portrait snackbar under the system nav buttons;
   - "Show on map" on an entry with nothing kept;
   - the entry map ignoring the basemap and Night Maps.

   Rule 1's sheet change is part of the same stage.

### Offline regions: protect, then fix (owner, 2026-09-28)

The offline-regions start-up pulse (`docs/audits/2026-09-28-offline-regions-startup-pulse.md`) found:
- The start-up read throws before any delete, so nothing is deleted today.
- But `MapLibreOfflineMapRepository.kt:190` prunes every Room region row that is missing from a successful MapLibre read, including an empty one.
- The storage redirect to `filesDir/maplibre-offline` has never taken effect. The live store is `files/mbgl-offline.db`, so making the redirect succeed would empty the list and prune every row.

The owner ruled, verbatim: **"Option A. Protect then fix."** The planner had offered A as:
1. Make the list refuse to delete a region's saved details because MapLibre's list doesn't include it. Only the user's delete removes a region; mismatches are logged. The half-finished-download cleanup is limited to downloads that genuinely never finished.
2. Then fix the start-up error by dropping the storage move and initialising MapLibre once at start-up, before anything reads.

Intent `2026-09-28-106`. It runs ahead of the other queued builds, and an S22 device check with a full backup follows it.

**The layout fixes' held items (owner, 2026-09-28).** The owner answered verbatim "1 A / 2 A / 3 D" (continuation `2026-09-28-109`):
1. **J8's chip over the cluster's reset button at 90: move it to the search bar's other end, away from the icon bar.** The planner reads "the chip" as the chip row, the taxon chip and J8's chip together, because J8's chip was ruled "Top, by the species chip". The row sits at the end of the search bar away from the cluster, following the cluster's side. Portrait is unchanged.
2. **The legend at 270: just inboard of the icon bar,** beside it towards the centre, bottom-aligned, collapsed or expanded, when the cluster is on the legend's side. Otherwise it stays in the corner as today.
3. **The band between the chip and the coordinate readout: left as map.** J8's design, where the chip takes touches only on its pill, stands. A tap there reaches the map.

**Coders run on Sonnet 5.5 (owner, 2026-09-28).** The owner, verbatim: "Switch coders to Sonnet 5.5". Every coder the planner launches from now on sets the Sonnet model. Coders already running when this was said (the layout fixes `-78`, offline safety `-106`, and the S22 planned-trips check `-94`) keep the model they started on, unless the owner asks for a restart. Pulses are not coders and are unaffected unless the owner says so.

**How coders get Sonnet 5.5 (owner, 2026-09-29).** Launching with the Agent tool's "sonnet" choice gave `claude-sonnet-5`, read from the agents' logs, not 5.5. The planner had said 5.5 without checking. The owner chose, verbatim, "I'll do the .config": the owner sets the default subagent model in `/config`.

From here the planner launches coders **without** a model setting, because an explicit one overrides the default. It reads the served model from the first agent's log and reports it.

### Photo export and on-device journal backup (owner, 2026-09-29)

The owner, verbatim: "Let's also add an export option for photos to the device, and on-device backup for journal entries."

These are new features, not yet specified. A read-only pulse maps what exists first: photo storage, any share, export or backup code, the GPX export, Android's backup rules, and the data inventory. Then the design questions go to the owner: where exports go, which photos, the location metadata, what a backup holds, where it is stored, manual or scheduled, and restore. Where they sit relative to J6 and the Journal PR is the owner's to decide.

### How coders run from now on (owner, 2026-09-29)

The owner, verbatim: **"So let's do sessions this way now."**

This supersedes the planner launching coders as Agent-tool subagents:
- **Each coder runs in its own Claude Code window,** opened by the owner, who sets `/model claude-sonnet-5-5` there.
- **The planner writes a paste-ready launch prompt** for each dispatch. It is preserved in `prompts/preserved/` and copied to `~/Zynergy/launch-prompts/`. It carries the coder's rules, names the worktree, says the session is a coder and not the planner, and gives the planner session's ref for hand-back.
- **The coder hands back** by pushing its report and sending the planner session a `SendMessage`. The planner re-runs the suite, merges run records and writes the record, as before.
- **Read-only pulses** may still run as subagents of the planner.
- **The planner can message these windows but cannot drive them.** Stopping one, or answering its prompts, is the owner's.

**Models (owner, 2026-09-29).** The owner, verbatim: "Sonnet and Opus 5.5 just released so I'll use Opus 5.5 to plan and Sonnet 5.5 to code." The planner session runs on Opus 5.5, and coder windows on Sonnet 5.5. The owner sets both.

**MapLibre failing to load at start-up (owner, 2026-09-29).** The offline-safety coder (`-106`) made `ForagerApplication` catch and log a failure to initialise MapLibre (Exception and LinkageError), because under Robolectric the SDK throws UnsatisfiedLinkError. As a result, a device whose map library cannot load starts the app and fails only when a map opens. The planner offered "A. Keep it: the Journal and everything else stay usable, and the failure is logged." The owner, verbatim: **"Option A for the map crash"**.

**Photo export and journal backup: the owner's rulings (2026-09-29).** These sit within the owner's 2026-09-09 backup ruling (`allowBackup=false`; an in-app export and import; "The export stays local unless the user chooses to move it"). They answer the planner's six questions after the premise pulse (`docs/audits/2026-09-29-export-backup-premise-pulse.md`). The owner, verbatim: "1 A / 2 A / 3 B / 4 A / 5 C scheduled set to off by default, must be turned on by user / 6 A - restore to app".
1. **Photo export goes to the phone's Gallery,** in a "Forager" album (MediaStore).
2. **Exported photos carry no location.** Captures stay GPS-free as stored, and the database coordinate is not written back.
3. **A backup holds the journal and everything it refers to:** entries and their ref tables, finds, photos (the files), tracks and points, waypoints, and offline-region details. The map tiles are not included; they are re-downloaded.
4. **The backup is a file saved where the user chooses** (the Storage Access Framework). It survives uninstall and can move to a new phone.
5. **Both a manual backup and a scheduled one.** The scheduled one is **off by default**, and only the user turns it on.
6. **Restore is built in the app,** as part of this work.

Still to rule before a dispatch:
- the button and setting names (copy);
- whether restore replaces or merges;
- the schedule's frequency and destination (a user-chosen folder the app keeps permission for);
- the API 26-28 Gallery path, which needs WRITE_EXTERNAL_STORAGE for devices below Android 10;
- where the controls live;
- the order against J6 and the Journal PR;
- the user documents to update: the privacy policy, the delete-data page ("Nothing is left behind"), and the beta README.

**Photo export and journal backup: second rulings (owner, 2026-09-29).** The owner, verbatim: "1 C ask to replace or merge / 2 C / 3 B / 4 A / 5 A".
1. **Restore asks each time whether to replace or merge.** The planner's reading: merge adds what this phone lacks and keeps what it already has. On a record present on both sides, the phone's copy is kept.
2. **The schedule's frequency is the user's choice:** daily, weekly or monthly. It stays off until turned on.
3. **On Android 9 and below** (API 26-28), photo export saves to a folder the user picks, through SAF, instead of the Gallery. No storage permission is added.
4. **Controls:** backup and restore in Tools, then Settings; "Save to Gallery" on the photo viewer.
5. **Built before the Journal PR.**

The planner's placement: after the trips fix (`-97`) and the map-chrome follow-ups (`-104`), and before J6 and device check Part 2. The UI copy goes to the owner for approval before the dispatch.

**Restore prompt body: the owner's copy (2026-09-29).** It replaces the planner's draft body. The owner, verbatim:

> Select Replace if you want to delete the journal data on this device, and move the backup into its place.
>
> Select Merge if you want to keep the journal data on this device, and restore the rest of the backup, skipping any duplicates.
>
> Select Cancel to go back.

"Skipping any duplicates" agrees with the planner's reading of merge: when a record is on both sides, the device's copy is kept. The rest of the draft copy (titles, buttons, the Backup settings and messages) awaits the owner's confirmation.

**Export and backup copy approved (owner, 2026-09-29).** The owner, verbatim: "A, approve the rest". With the owner's own restore-prompt body above, the approved copy is:
- **Photo viewer:** "Save to Gallery" (Android 9 and below: "Save to folder"); messages "Saved to Gallery", "Saved" and "Couldn't save that photo."
- **Tools, Settings, Backup:** "Back up now" (default file name `forager-backup-<date>.zip`); "Automatic backup" (a switch, off by default); "How often": Daily, Weekly or Monthly; "Backup folder": "Choose folder"; "Restore from backup".
- **Messages:** "Backup saved.", "Couldn't save the backup.", "Automatic backup is off until you choose a folder."
- **Restore prompt:** the title "Restore this backup?", the owner's body, the buttons Replace, Merge and Cancel; afterwards "Restore complete." or "Couldn't restore that backup."

**J8's chip tap area (owner, 2026-09-29).** The chip responds only on its 32 dp pill, J8's "touches only on its pill" design, inside a 48 dp layout box. The owner, verbatim: **"Option A for now. We may need to change it if it's too small"**. It stays pill-only. Device check Part 2 captures how the pill feels to tap, at its edges, for the owner to judge, and the full 48 dp is revisited if it proves too small.

**Photo export: imports leave unchanged (owner, 2026-09-29, via the -126 coder).** Ruling 2 above ("Exported photos carry no location") now covers **photos taken in the app only**. The owner, verbatim, as quoted in `docs/audits/2026-09-29-photo-export-completion-report.md:19`: "Imported photos taken outside the app are not within our scope. They can use a scrubbing app to remove it if they want it removed. All photos taken inside the app are scrubbed either way and that's our scope". It was confirmed with "Export imports unchanged (Recommended)". So every photo is exported as a byte copy of its stored file, and an imported photo may carry its own location or other metadata into the Gallery.

Alternative rejected: stripping imports. The existing scrubber handles JPEG only, while imports can be HEIC, PNG or WebP. The privacy policy and beta README must say this (record 2026-09-28-131).

**Journal backup and restore: third rulings (owner, 2026-09-29).** These answer the -127 coder's stop and the decisions it made itself (record 2026-09-28-132). The owner, verbatim:

> 1 B
> 2 A
> 3 A
> 4 A
> 5 A
> 6 B - instead of showing an app restarting, go into a splash page telling the user the changes are loading, then when loaded, show a Done button for them to tap to return to the app's home map.
> 7 A inform user that a file could not be backed up and offer to try again
> 8 weekly to start, with default off, let the user set the frequency from there

The planner's reading, with the options as they were put:
1. **Restored offline regions are listed in Offline maps as "not downloaded",** each with a re-download from its stored centre, radius and zoom. This needs new copy, to be approved first.
2. **Merge gives each incoming region a new id** and rewrites the entries' references to it.
3. **A backup with missing photo files reports that it is partial,** not "Backup saved." This needs copy.
4. **Planned trips are included** in backup and restore.
5. **Restore is blocked while a track is recording,** with a short message. This needs copy.
6. **After a restore, every screen shows fresh data.** Not by a visible app restart: a loading page says the changes are loading, then a Done button returns to the Maps home. This needs copy.
7. **A backup that fails partway deletes only the file it created.** The user is told the backup could not be completed and is offered a retry. This needs copy. How a scheduled run, which has no screen open, tells the user is not yet ruled.
8. **The default frequency is Weekly.** The schedule stays off until the user turns it on and can then set the frequency.

**Journal backup and restore: copy (owner, 2026-09-29).** The planner's drafts were:
1. A restored region: "Not downloaded", with **Download again**.
2. "Backup saved, but 2 photos couldn't be found and were left out."
3. "Stop recording before restoring a backup."
4. "Loading your restored journal…", then "Your journal is restored." with **Done**.
5. "Couldn't finish the backup. The incomplete file was removed." with **Try again** and **Cancel**.
6. How a failed scheduled backup tells the user: A, a notification with **Try again**, or B, a message at the next launch.

The owner, verbatim:

> 1 approve
> 2 approve
> 3 approve
> 4 approve have a pulsing app icon with Done in the center, be the done button to tap
> 5 approve, add a Continue button, and a  "continue without file(s)" option in case they don't care. Else it will bar them from backup if the file isn't backing up properly
> 6 option A

Item 5's buttons, and which failure they belong to, are put back to the owner before the dispatch (record 2026-09-28-136).

**Restore's Done icon: a tap animation (owner, 2026-09-29).** The owner, verbatim: "Give item 4 a nice animation when tapping it". Which animation is put to the owner as options (record 2026-09-28-138).

**Done icon animation and scheduled-backup photos (owner, 2026-09-29).** The owner, verbatim: "1 A / 2 not pasted yet / 3 yes that sounds good. Tap on the notify to go to the backup page".
- **The animation (A):** tapping the Done icon makes it grow slightly and fade out while the Maps tab fades in, in about 300 ms.
- **A scheduled run with unreadable photos:** it skips them, saves the backup, and notifies "Scheduled backup saved. N photos couldn't be backed up." Tapping the notification opens the Backup section.

**Layers sheet: map-type chips centred (owner, 2026-09-29).** The owner, verbatim, with a screenshot of the Maps tab's Layers sheet on the S26 (kept outside the repo at `~/Zynergy/device-evidence/2026-09-29-owner-layers-sheet-chips.jpg`): "One more thing: have the map street/topo/satellite chips be centered between the panel sides. The height position on the panel is fine as is."

The planner's reading:
- The Street / Topographical / Satellite row is centred horizontally between the sheet's sides.
- Its vertical place is unchanged.
- The code is at `MapLayersSheet.kt:263`: a `Row` with `spacedBy(Spacing.xs)` and no width, so it hugs the start.

**Stage device check Part 2 and J6: how they start (owner, 2026-09-29).** The planner asked two questions:
- whether Part 2 runs backup and restore on the S22 as a full round trip behind a verified full copy (A), restores only on the tablet (B), or only backs up (C);
- whether J6 launches now (A) or after the backup coder (B).

The owner, verbatim: "1 A / 2 A / Wait until the last coder is done building to begin this stage please, so both can be done side by side as planned."

The planner's reading:
- **Part 2 runs the full backup-and-restore round trip on the S22,** behind a verified full copy of the phone's data: a backup, a Replace restore and a Merge restore, each confirmed against that copy.
- **Part 2 and J6 launch together, side by side,** once the backup coder (-137) has finished building. Both prompts are prepared now.

### J6 design rulings (owner, 2026-09-29)

Asked after the J6 refresh pulse (`docs/audits/2026-09-29-j6-premise-refresh-pulse.md`, read at `db756faa`), the owner answered, verbatim:

> 1 to 5 I'll take your recommendations
>
> For smaller calls...
>
> 1 to 6 I'll take your recommendations
>
> 7 if there's room to not need it scroll then it should be used. Tablets allow the space. If not, then have it scroll.
>
> 8 yes
>
> 9 there's no reason not to have them. If the phone has them, add them in. The worst that happens is they don't get used. Now is the time to get it going for tablets.
>
> 10 give the photo album a long press delete option for photos in tablet mode

What that rules, from the options as they were put:
1. **An opened entry or find takes the whole right side, search bar included.** That is about 465 dp in portrait and 958 dp in landscape.
2. **List and Maps become real tabs whenever the combined pane would leave the map narrower than 480 dp** (the planner's suggested minimum, taken with the recommendation). Otherwise they stay side by side. The J8 chip row clears the Layers button either way.
3. **With the Journal open and nothing selected,** the right side is unchanged: the list and the map, under ruling 2.
4. **Columns:** Entries 1, Finds 2, Album 3, as on the phone.
5. **One Back order whatever the route:**
   1. picker;
   2. editor, with its save prompts;
   3. report;
   4. close the detail;
   5. Records to Entries;
   6. Journal to Search;
   7. exit.

   Open items and views survive switching panels, because the existing Journal state holders are passed to the wide tree.
6. **Smaller calls 1-6:**
   1. The record details open in the right side, not as a sheet.
   2. The photo viewer stays full screen.
   3. The map pickers open in the right side.
   4. The drafts list is in the left column.
   5. Tablet finds get the report view and the "+" tile, and the All logbook lists finds.
   6. The tablet draws its map before any search, so planned trips show.
7. **The Records chip row:** no scrolling when the chips fit the column; they scroll only when they do not.
8. **Headers match the phone:** Entries / Records.
9. **Map chrome parity:** the tablet map gets the phone map's controls (locate, compass, record, and the rest the phone has). This reverses the planner's recommendation to leave them out.
10. **The album gets a long-press delete for photos on the tablet,** as on the phone. The old Photo Gallery panel is removed (ruling 2 of 2026-09-28).

**The planner's split, stated to the owner.** J6 is built as two parts, in one coder window, pushed and reported in order:
- **J6a, the Journal:** rulings 1 and 3-8, 10, and J1-J10 parity.
- **J6b, the tablet map:** rulings 2, 6.6 and 9, and the chip row's clearance.

**Backup follow-up and Part 2's shape (owner, 2026-09-29).** These answer the -137 stop and flags (record -150), and Part 2's scope. The owner, verbatim: "1 A / 2 A / 3 A / 4 A / For the previous 4 questions".

1. **A new notification channel named "Backups".**
   - On Android 13 and later, the notification permission is asked when the user turns scheduled backups on.
   - If it is declined, the schedule still runs. A problem is then shown in the app at its next launch, using the approved notification wording.
2. **Replace gives restored offline regions fresh ids,** as Merge does, and rewrites the references. A later MapLibre download can then never collide with a restored row.
3. **Saving over a file that already has contents asks first:** "Replace the existing backup file?"
4. **Part 2 includes the two unassigned checks** (the search bar after an entry, and drawer Back). It is split into sessions by area:
   1. layout and the map;
   2. the Journal flows;
   3. backup and restore last, after the backup follow-up lands.

**The icon bar and the record pill: an L shape (owner, 2026-09-29).** The owner sent a screenshot of the Maps tab, kept outside the repo at `~/Zynergy/device-evidence/2026-09-29-owner-icon-bar-and-pill.jpg`. It shows:
- the vertical icon bar: fullscreen, compass, locate, Layers, and the green "+";
- beside it, the smaller vertical record/return pill;
- a translucent fill above the pill that joins them.

The owner, verbatim: "This can't ship like this.  That's a UX problem. Have the icon bar shrink a little and turn the small pill 90°, then half of the small pill can fit beneath the icon bar, and extend out. / Make the pill the same size as the bar".

The planner's reading, to be confirmed with the owner before any dispatch:
- The bar gets a little shorter.
- The record/return pill turns horizontal and sits beneath the bar. Half of it is under the bar, and the other half extends out toward the middle of the screen, mirrored when the cluster is on the other side.
- The pill is as thick as the bar is wide, so the two read as one L.

**Status:** a ship blocker. The cluster items of Part 2 Session 1 (inventory items 38, 39 and 42) will need re-checking after it.

**The L shape, confirmed from the planner's diagrams (owner, 2026-09-29).** The planner redrew the reading as letter diagrams:
- the bar at the left, with the pill beneath it, record under the bar and return extending out;
- mirrored at the right, with return extending toward the middle.

The owner, verbatim: "Oh yeah on either side it looks like an L. On the right side it just looks like an inverse L". The shape and the mirroring are confirmed. The pill's thickness, the height rule, the fill's removal and which screens are still being asked.

**The L shape: where it applies, and the details (owner, 2026-09-29).** Answering the planner's six follow-up questions, the owner said, verbatim: "4: Only apply on landscape phone mode. Never on portrait or tablet mode. / The rest I'll take what's recommended."

So:
1. **The pill's thickness equals the bar's width,** so the L reads as one piece.
2. **The whole L is no taller than today's bar and pill,** and every button stays at least 48 dp.
3. **The translucent fill that joins them today is removed.**
4. **The L is for phone landscape only** (the compact tree's short window). Phone portrait keeps its current arrangement. The tablet (the wide tree, J6b's controls parity) never takes the L.
5. **Part 2 Session 1 starts now.** Its landscape cluster items are deferred and re-checked after the L lands.
6. **J6's header is a back-arrow row labelled "Journal",** with the Entries / Records switch below it. It returns to the Search panel, as the "Mushroom Log" row does today.

**The L's height (owner, 2026-09-29).** The cluster-geometry pulse (`docs/audits/2026-09-29-icon-cluster-geometry-pulse.md`) showed three things:
- the pill is already 48 dp thick, as the bar is wide;
- stacking cannot keep the L to today's 264 dp in landscape with 48 dp buttons, so the planner's "no taller than today" rule could not be met;
- an L drawn inside today's rectangular container would leave a corner that takes touches.

The planner offered three options:
- A: remove the spacing between the bar's buttons, keep them 48 dp, and keep a gap above the pill, making the L 296 dp;
- B: the same with no gap, 288 dp;
- C: buttons of about 44 dp to stay at 264.

The owner, verbatim: "A".

So:
- The bar is five 48 dp rows with no spacing or end padding: 240 dp.
- An 8 dp gap, then the horizontal pill (48 dp thick): **296 dp in all.**
- Nothing is drawn around the L, and touches outside its two shapes reach the map.

**The L's pill: the icons stack fully (owner, 2026-09-29).** The L coder asked how long the turned pill is:
- 96 dp: two 48 dp buttons with no spacing, exactly half under the bar;
- 108 dp: today's padding, putting record 4 dp off the bar's edge.

The owner, verbatim: "the icons need to stack fully. Make sure that happens and have the pill extend outward like the L".

So:
- **The pill is 96 dp.**
- **Record's 48 dp box sits exactly under the bar's 48 dp column,** so its left and right edges equal the bar's. The record icon stacks fully under the bar's icons.
- Return extends outward, inboard of the screen, forming the L.

**Disk space (owner, 2026-09-29).** The disk filled (about 114 MB free of 67 GB), stopping -160 before any test ran. The planner offered to delete about 4.5 GB of old build output in 28 finished worktrees. The owner, verbatim: "1 C". The owner clears space. The planner deletes nothing.

The owner confirmed the pill's length, verbatim: "2 is  Awith that message". That is option A, 96 dp, together with the "stack fully" message above, which is what -162 carries.

**Part 2 Session 1's questions and J6a's questions (owner, 2026-09-29).** The owner, verbatim: "I'll take your recommendations". That answers the four open recommendations:
1. **"Download Maps" asks first,** with a confirmation that shows the area. Its wording goes to the owner before the dispatch. Part 2 follow-ups.
2. **A track's details sheet gets a Delete.** Part 2 follow-ups.
3. **J6a's record-details pane keeps its back row labelled "Details".**
4. **PhotoGalleryScreen.kt and its test are deleted in J6b.** They have had no production caller since J6a.

**The L's conflicts, J6c, the investigation, and the rest (owner, 2026-09-29).** Asked for all open items, the owner answered, verbatim: "1 2 3  I'll take your recommendations / 4 paste it here / 5 defer for tomorrow / 6 authorized and always allowed now".

1. **The L** (record -170):
   - (a) the top limit pushes the L down, below the search bar;
   - (b) in landscape, the search notice stops before the L's side, the way the legend makes room, so the two never overlap;
   - (c) the minimise handle's touch area is 48 dp tall, level with the locate row;
   - (d) every button's full 48 dp square is its touch target, corners included. Nothing else around the L takes touches.
2. **J6c, the tablet map controls** (record -171):
   - drag, snap and minimise, as on the phone, within the tablet map's edges;
   - fullscreen hides the Journal column and the search bar;
   - the compass strip across the top of the tablet map, with the chip row below it;
   - the phone's cluster extracted into one shared composable, rather than copied;
   - Layers and "+" become bar rows.

   The tablet takes the portrait arrangement, never the L.
3. **The intermittent-failure investigation** is queued after the builds.
4. **Part 2 Session 3** is pasted to the owner.
5. **The owner's judgement of Session 1's captures** (rings, chips, highlight colours, night views) is deferred to 2026-09-30.
6. **The tablet is authorized for USB debugging,** "always allowed". adb reads R52T506412L as a device.

**Tracks thinner when zoomed out, again (owner, 2026-09-29).** The owner sent a screenshot of a journal entry's map at night: a short track under its find and photo glyphs, drawn as a thick lilac line. It is kept outside the repo at `~/Zynergy/device-evidence/2026-09-29-owner-track-thickness.jpg`. The owner, verbatim: "One thing about tracks: when zoomed out they're still thick on the line. Can they be thinned when zoomed out even further? It's hard to read it accurately from a distance".

Today (`ui/map/layers/TrackWidthByZoom.kt`, `TRACK_WIDTH_ZOOM_STOPS`):
- a track line is 6 dp, plus a 1.5 dp casing each side, 9 dp in all, at zoom 15 and above;
- it is 40% of that at zoom 11 and below (2.4 dp line, 5.4 dp in all);
- it is linear in between.

These stops were the planner's proposal of 2026-09-28-34, "for the owner to judge on the phone". The new stops are put to the owner as options.

**The entry map in landscape: the left side cut off (owner, 2026-09-29).** The owner sent a phone screenshot in landscape: a journal entry's map in fullscreen, with the Entries / Records switch above it and the rail on the right. It is kept outside the repo at `~/Zynergy/device-evidence/2026-09-29-owner-landscape-entry-map-cut.jpg`. The owner, verbatim: "On landscape a lot of the map is gone.  The entire left side is cut off for no reason."

What the screenshot shows, read by the planner:
- The map starts about 122 px in from the left edge, the width of the camera cut-out band in that rotation. The band is left black.
- The Entries / Records row sits above the map, in its own band across the top.
- The Maps tab's own map does reach into the cut-out band: Part 2 Session 1 found its "i" there at 270 (item 37). So the entry map differs from Maps.

The fix's shape goes to the owner as options.

The owner then asked, verbatim: "Unless that's a scaffolding for more map functions". The planner checked the code: the band is not reserved for map functions.
- In short landscape, only the Maps tab lets its map run under the cut-out. `AvailabilityCompactScaffold.kt:679-683` sets `contentWindowInsets` to the top only, "the map runs the whole width, under the cut-out", and pads the controls one by one (`mapControlsPadding`, :552-575).
- Every other tab, the Journal included, takes `shortLandscapeContentInsets()` (:684-685). That keeps the whole tab, lists and text included, out of the cut-out band.
- The entry map lives inside the Journal tab, so it inherits that margin.

**The landscape entry map's left band: dropped (owner, 2026-09-29).** Asked whether "Nevermind that part" meant the entry map's black band in landscape, the owner answered, verbatim: "Yes drop that". The request is withdrawn. The entry map keeps the Journal tab's cut-out margin, and nothing is dispatched.

**Tracks by zoom, revised, and Session 3's backup findings (owner, 2026-09-29).** The owner, verbatim: "2 A, 3 I'll take your recommendations".

**2 A: new track width stops,** replacing the 2026-09-28-34 proposal:
- zoom 18 and above: full width, 100%. The line is 6 dp plus a 1.5 dp casing each side, 9 dp in all;
- zoom 16: about 67%;
- zoom 14: about 42%;
- zoom 12 and below: 25%. The line is 1.5 dp, 2.25 dp with its casing;
- linear between stops.

The casing and the highlight halo keep following the stops, as today.

**3: Session 3's backup findings,** the planner's recommendations:
1. **A restore skips a region that matches one already on the phone** (same name, centre and radius), so the phone that made the backup does not get duplicates.
2. **The first scheduled backup waits for its scheduled time.** Turning the schedule on, or off and on, does not run one at once.
3. **Scheduled backups keep the newest 5.** Older scheduled backup files in the chosen folder are deleted. Manual backups are never touched.
4. **The notification permission is asked once,** when scheduled backups are first turned on. If it is declined, it is not asked again for backups; the in-app notice covers it.

**The parked items wait for PR #140 (owner, 2026-09-29).** PR #140 is the single Journal PR: journal-redesign into pre-main, currently a draft. The owner, verbatim: "15 to 22 can come after PR 140 merge". That refers to the planner's open-items list, whose items 15-22 were parked:
- the "MapView destroyed" log flood and the StrictMode disk reads at start-up;
- the offline-region cases not yet tested (P1/P3, and no network);
- the light theme's legibility;
- the portrait dropdown's second Back;
- Android 8-10 with no device;
- non-JPEG imports stored under a .jpg name;
- Session 3's leftovers on the S22 (the Backups channel, and possibly a folder grant);
- the cause of the restore's unequal first pass.

None of these block PR #140.

**"Download Maps" asks first: the approved copy (owner, 2026-09-29).** The owner, verbatim: "Approve the Download Maps wording as is". The dialog opens over the picker when "Download Maps" is tapped:
- **title:** "Download this area?"
- **body:** "<name> · <radius> around the pin · about <N> tiles". Without a name it is "<radius> around the pin · about <N> tiles". The radius follows the units setting.
- **buttons:** "Cancel" and "Download".

**Track delete, built like waypoints (owner, 2026-09-29).** The F1 coder found that no track delete exists in the app. The planner's earlier "swipe in the list" had never been checked (record -190). The owner, verbatim: "Option A". The option was put as:
- tracks delete as waypoints do: a swipe in the Records list and a Delete on the track's details, both with Undo;
- a track still recording is never offered Delete;
- a journal entry that kept the track follows the same rule as an entry that kept a since-deleted waypoint.

This also makes the delete-data page's "Recorded tracks" line true.

**A kept track keeps its path (owner, 2026-09-29).** The F1 coder found that deleting a waypoint leaves an entry's ref rows and snapshot in place, and applied the same to tracks. But a track's snapshot has no path, so an entry that kept a deleted track could no longer draw its line. The planner offered:
- A: accept that;
- B: save the track's path into the entry when it is kept, so a later delete does not affect the entry's map.

The owner, verbatim: "Option B".

The planner's placement: B changes the database (a place for the path), the backup's table list and Merge rules, and the entry map's drawing. So it is its own stage, F3, after F1, built on a read-only pulse of how kept snapshots are stored. F1's track delete lands first, and the two land before PR #140.

**Kept track paths: F3's design (owner, 2026-09-29).** Asked after the kept-track-path pulse (`docs/audits/2026-09-29-kept-track-path-pulse.md`), the owner answered, verbatim: "All recommended. I'll review the other items".
1. **The path is copied only when a track is deleted.** Just before the delete, it goes into every journal entry that has the track, kept or withheld. It is stored in a new table, `cartography_entry_track_paths(entryId, trackId, path)`, as the read-seam-filtered lat/lng in time order. No backfill is needed.
2. **Backup Merge keeps an entry's track refs, and their saved paths, even when the phone lacks the track.** This reverses the backup report's decision 8 for track refs only.
3. **The saved path shows wherever the entry shows the track:** its map, its card thumbnail, and the bubble for a tapped track line. The Maps tab's highlight stays live, per the earlier "Live records" ruling.
4. **Drafts get the saved path too.**

Sequencing: F1's track delete lands first and F3 right after, both before PR #140.

**Stacked map markers fan out on tap (owner, 2026-09-29).** Part 2 Session 2 (item 52) and F1's item 10 found that when markers overlap, for example two photos at one spot, a tap reaches only the top one. The planner offered:
- A: a chooser list;
- B: clustering;
- C: fan out on tap;
- D: leave it.

The owner, verbatim: "Option C / This sounds better than the other options  and honestly, it's like A, but it keeps the icons, which is more robust for UX."

The planner's proposed behaviour, from the common map convention of fanning a stack out around its point, goes to the owner to confirm before a dispatch (F4).

**Fan-out: the behaviour (owner, 2026-09-29).** The owner, verbatim: "Confirm 1 to 5 / 6 Give it a .4s animation speed. / 7 confirm". The behaviour is:
1. **A stack** is markers whose touch areas overlap at the current zoom.
2. **Tapping a stack** fans its markers out in a small ring around their spot. Each keeps its own icon, with a thin line back to its true position. The map does not move.
3. **More than 8 markers** fan out in a spiral instead of a ring.
4. **Tapping a fanned-out marker** opens its bubble as usual.
5. **Tapping the map, panning, zooming or pressing Back** folds them back.
6. **The fan-out animation takes 0.4 s.** With the system's animations off, the markers appear spread out at once.
7. **It works everywhere:** the Maps tab and a journal entry's map, on the phone and on the tablet.

**Fan-out: what fans, and staying on screen (owner, 2026-09-29).** Asked after F4 (record -206), the owner answered, verbatim: "1 A / 2 A".
1. **Only the owner's own records fan out:** finds, photos, waypoints and planned trips. iNaturalist sighting dots keep their existing tap behaviour and are never part of a fan.
2. **The fan is shifted so the whole ring or spiral lands on screen,** clear of the icon cluster, the legend and the chip row. The leader lines still point to the markers' true spot.

**Map chrome: one colour, the navigation bar's (owner, 2026-09-29).** The owner sent a screenshot of the Maps tab with the search panel open over the map, the icon bar showing through it, and the bottom navigation bar below. It is kept outside the repo at `~/Zynergy/device-evidence/2026-09-29-owner-chrome-colours.jpg`. The owner, verbatim: "The map chrome isn't aligned. The search panel and map icon bar are the wrong color. Have them be the same color as the bottom app navigation bar. Make sure any other pop up or bubble, or the tool panel, is the same color as the app navigation bar also please".

The planner's reading:
- Every piece of map chrome takes the bottom navigation bar's colour: the search panel, the icon bar and pill, pop-ups, bubbles, and the Tools panel.
- The 80% opacity over a map (UX default, "Nothing fully obstructs the map view") stays as it is.

A read-only pulse maps every chrome surface's colour and alpha against the navigation bar's first.

The owner then answered the planner's question about the icon bar showing through the open search panel, verbatim: "Option B / That's not a problem. My problem is exactly how I stated: the wrong color. / Opacity for the icon bar is fine as is."

So:
- The bar stays visible through the panel.
- **Only colour changes.** Every alpha, including the icon bar's, is left as it is.
