# Dispatch 2026-10-27-05: photos attached to a find get no map icon of their own (a coder, Opus)

Written by Rowan Waymark, 2026-10-27, against pre-main 6064fef86 or later. RECORD -1734.

**Authorisation.** The owner, from S26 screenshots (which stay off GitHub), verbatim: "some of the photo glyphs on the map are misplaced and are apart from their find location. Can we remove the photos attached to Finds from the map so it can reduce clutter?" The step path was confirmed as option 1, with the owner's note: "the photo icon disappears when the photos switch is off. I had it on for those screen shots." The go: "Start now (Recommended)".

Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.

## Change (owner-confirmed)
- Maps > a find with photos > only the find's icon shows at the find's spot. No camera icons are drawn for that find's photos.
- Tap the find > its bubble, with its photos inside the find as today > Back.
- **Standalone photos** (attached to no find) still show as camera icons when Layers > Photos is on, and don't show when it's off, as today.
- This applies on every map that draws photo icons, including the entry map, if it draws them. Name every one.

## Verify first (report before building if any differs)
- Find where photo icons are drawn and what decides a photo "belongs to a find". Is it a link in Room, or a matching location? Cite file:line.
- Check whether the fan or spiral and the photo-ring code (step 5's photo ring, JOURNAL_ENTRY_REGIONS neighbour, MapLayerRegistryTest) count find-attached photos. Say what changes for them.
- Check whether a journal entry's photos are a separate case. If unsure, stop and ask.

## Tests
- **Through the real map entry point** (the screen with real data fixtures):
  - a find with two photos draws one find icon and no photo icons;
  - a standalone photo draws with Photos on and not with Photos off.
- **The spiral:** grouped items no longer include find-attached photos.
- **Fail first, then revert checks:** one revert check per rule, each on its own scratch branch, restored from a saved copy and never from git, with the build log checked for compile errors.
- CI only. A new branch off current pre-main, a PR into pre-main, no merge.

## Rules
- **Network:** none beyond GitHub.
- **Data:** no schema change; if one seems needed, stop.
- **The Restricted Object:** never read, name or quote it.
- **Long conversation:** past about 200k tokens, push and write a handoff.
- **Another coder** is writing a read-only report on the spiral (dispatch 2026-10-27-06). Don't wait on it.

## Report
- the four disclosure sections;
- the PR number and CI runs;
- S22 steps as tap > shows > Back.

## Amendment 1 (2026-10-27): the edge cases (RECORD -1735)
The coder's verify-first: a photo belongs to a find by a Room link (GalleryPhoto.referencingEntryIds, RoomMushroomLogRepository.kt:74-89). Two maps draw photos: the Maps tab (GetMapRecordsUseCase.kt:97-101) and the entry map (GetCartographyEntryMapDataUseCase.kt:102-107). The fan works from what is drawn, so it needs no change. The owner's rulings:
- **"No icon anywhere (Recommended)":** a photo attached to a find and also to a journal entry has no icon of its own, on the Maps tab or on the entry's map.
- **"Show its photos (Recommended)":** while a find has no location, its located photos show as standalone photos, following the Photos switch.
- **"Hide only after Save (Recommended)":** only saved finds hide their photos; a draft's photos keep their icons until Save.
- **"Fine, they reappear (Recommended)":** after a find is deleted and its Undo window ends, its photos are standalone again.

So the rule is: a photo is hidden from every map when at least one saved find that has a location links to it. Filter it in GetMapRecordsUseCase and in GetCartographyEntryMapDataUseCase, with no schema change. Add a test for each ruling, each with a revert check, then build as the dispatch says.
