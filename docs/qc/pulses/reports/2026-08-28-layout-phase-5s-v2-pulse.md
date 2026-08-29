# Pulse — Layout phase (5S), second draft: correction-notice and §2S rebuild verified against the tree

**Date:** 2026-08-28. Read-only verification, no code changes. Checks the second "Layout phase — 5S
applied to the UI" draft's specific factual claims — the Correction Notice, the rebuilt §2S tables, and
Q5 of its "Decisions needed" list — against the current tree and `docs/plans/map-redesign.md` in full.

---

## Correction Notice — every specific claim checked, all confirmed

- **"The compass strip carries two controls" is false.** Confirmed:
  `CompassElevationStripContent` (`AvailabilityScreen.kt:4095-4176`, doc comment from 4088) is exactly
  heading, elevation, coordinates, one tap-to-toggle-format affordance on the coordinates segment. The
  doc comment itself records why: "The return-to-vehicle status/toggle and the record start/stop
  control used to live in a second row here; both moved into `MapIconBar`."
- **Record and return-to-vehicle live in `MapIconBar`.** Confirmed — both are real rows (`isRecording`
  toggle, `onToggleReturning`) inside the bar, `AvailabilityScreen.kt:3904-3922`.
- **"Record is the smallest interactive target" is false.** Confirmed: `MapBarIconButton`
  (`AvailabilityScreen.kt`, definition just above line 3942) sizes every row with
  `Modifier.size(MIN_TOUCH_TARGET)` unconditionally — no per-row size variation exists in the
  composable's parameter list at all.
- **"Add a find — 5th circle" is false; it's row 8 of 8.** Confirmed by cross-reading
  `map-redesign.md`'s own "Icon stack superseded again: the panel bar landed" section, which names the
  exact 8-row order: *"fullscreen, orientation-reset, GPS/locate-me, topo/plain, record,
  return-to-vehicle, search, add"* — an exact match, row for row, to this draft's own §2S table order.

All four corrected claims hold. Nothing in the Correction Notice needs a further correction.

---

## §2S rebuild — spot-checked against source, all confirmed

| Claim in the draft | Verification |
|---|---|
| 8 `MapIconBar` rows, stated order | Matches `map-redesign.md`'s own record exactly (above) — the rebuild is faithful to its stated source, not just internally consistent. |
| Every row at `MIN_TOUCH_TARGET` (48dp), no size differentiation | Confirmed directly from `MapBarIconButton`'s definition. |
| Permanent drawer: 360dp of a 600dp `MEDIUM` window | Confirmed: `PERMANENT_DRAWER_WIDTH = 360.dp` (`AvailabilityScreen.kt:1263`) and `MEDIUM` starts at 600dp per `map-redesign.md`'s own "Known defect" section. One naming drift worth a footnote: `map-redesign.md` calls this constant `DRAWER_PANEL_WIDTH`; the real name in the current tree is `PERMANENT_DRAWER_WIDTH` — cosmetic, the number and the finding are both right. |
| `showCloseButton` on `drawerSheetContent`, always `false`, one call site | Confirmed verbatim — the code's own comment at that exact call site (`AvailabilityScreen.kt:664-668`) says so explicitly: *"This has exactly one call site — the `PermanentNavigationDrawer` medium+ windows get... `showCloseButton` is therefore always `false` in practice today; it is not dead code removed here only because that is a separate cleanup this pulse's own dispatch did not ask for."* |
| Search "reachable from one of six tabs — the largest placement defect" | Confirmed against `map-redesign.md`'s Phase 2 section verbatim: *"search is only reachable from the Maps tab — there is no quick-search affordance from List/Seasonal/Journal/Settings."* |
| Off-track alert: "detection built, unwired... currently posts nothing" | **Confirmed, with one precision correction.** `DetectOffTrackUseCase` is not literally unwired — it's called from `TrackRecordingViewModel` (line 259) and does drive `TrackRecordingUiState.isOffTrack`. But tracing every consumer of `isOffTrack` in the UI layer finds exactly one: `AvailabilityScreen.kt:3920` tints the return-to-vehicle icon's color to `MaterialTheme.colorScheme.error` — a passive Compose color change, visible only if the app is open, foregrounded, and on the Maps tab. `TrackRecordingService.kt`'s notification builder (`buildNotification()`) has zero reference to `isOffTrack` anywhere. So: the detection *is* wired into app state, but nothing wired to it can reach the user in the Return-phase body state the draft's own reasoning column names ("must work with the screen off") — the practical conclusion "posts nothing" is exactly right, just for a slightly different reason than "unwired" suggests. |
| Crash log panel exists as a distinct surface | Confirmed: `app/src/main/java/com/forager/app/ui/crash/CrashLogPanel.kt` is a real file. |

Nothing checked came back wrong. The rebuild is accurate.

---

## Q5 — "Is `map-redesign.md` still authoritative?"

**Yes**, and this pulse is itself the check: every §1S/§2S claim traced back to it (the drawer width,
the 8-row bar order, the search-reachability defect) checks out against the current tree, and
`map-redesign.md` is the one document in this project's design paper trail that keeps correcting itself
in place with dated sections — "Icon stack: superseded from 5 to a 7-icon stopgap," "superseded again:
the panel bar landed," "Known defect in the untouched path (deferred)," "Deferred: night-mode colour
inversion" — rather than going stale silently. It is 501 lines, read here in full, not sampled.

**One thing worth naming for whoever reopens the frequency/phase columns**: `map-redesign.md`'s own
"Open question, `EXPANDED`: where do the six destinations live?" section (its four sub-questions, none
answered, same "blocked on hardware we don't have" constraint as everything else in this project's
medium/expanded-window work) is adjacent scope to this layout phase's own Q2 ("does the six-tab bottom
nav survive?") — not the same question, since Q2 is about compact's bottom nav specifically, but the
two should probably be decided together rather than separately, since an answer to one constrains the
other's design space.

---

## Q1–Q4 — not pulled forward, and deliberately not answered here

The draft is explicit that Q1 (frequency column) and Q2-Q4 (does the bottom nav survive, recolour vs.
rearrangement, bottom-sheet vs. tab-switch) are **owner input this method cannot derive** — product and
usage-frequency judgment calls, not facts about the tree. Answering them here would be exactly the
"preference wearing a method's clothes" §0 warns against. One factual note that bears on Q4 without
answering it: Pulse 2 (`2026-08-28-mapiconbar-q5-provenance-pulse.md`) already traced this same
question's provenance to `docs/design/m3-design-direction.md`'s own Q5 — *"Bottom sheet (map-app
convention, map stays full-bleed) vs. the current bottom-nav tab switch (explicit, already built,
already tested)"* — confirmed still unanswered there as of that pulse; nothing this pass found changes
that.
