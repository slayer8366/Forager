# Sorrel's review: PR #507, the bubble above / over the route card (cf8b3a07f..074ce1fba)

Reviewer: Sorrel Tally (Fable 5.1). Read-only; nothing changed, nothing pushed. 2026-11-09.

## Verdict

**Approve for the release.** Built as ruled (RECORD -2187: kept above the card; over the card, seen whole, only where there is no room; tap away closes it and the card is whole; tucked, only the strip limits it). The z-order mechanism changes nothing at rest. Tests are real touches and the reverts show each half bites for its own reason. Nothing silenced. Two notes for the owner's awareness, no defect that should hold the release.

## Checked, and how

- **Diff** `git diff cf8b3a07f..origin/route-card` (head 074ce1fba): 6 main files, 2 test files, the report. Read in full.
- **Rulings** read from `origin/records-after-173:RECORD.md` lines 36675-36677 ("Kept above the card", "Make the change this release actually", "Bubble over the card (Recommended)", the planner's calls: sideways overlap only; tucked = strip only; crowded list and navigation card not limited).
- **Placement** (`MapBubbles.kt` `bubblePlacement`): the card's top becomes the clamp box's bottom only where the bubble's placed columns meet the card (`bubbleBottomLimit`, BubbleFloor.kt); `overFloor = cardBottom < maxHeight && cardBottom - bubbleHeight < minY` restores the full box and flags it. Flip-below and the clamp both use the same `bottom`. Correct against the rulings.
- **Z-order** (`CompactMapBubble.kt:77`, `AvailabilityCompactMapUi.kt:693`): the bubble layer takes zIndex 1 only while `overCard`; the three composables after the card (landscape navigation card, bottom chrome, pickers) are wrapped in `Box(Modifier.matchParentSize().zIndex(2))`, always. Confirmed from `AvailabilityCompactMapUi.kt` (saved copy at /mnt/work/scratch/sorrel-507d/ACMU.kt, lines 468-770): the Box's children in order are map slot, Back handler, search slot, bubble, crowded list, icon cluster, chips, top strip, route card, then the wrapper, and the wrapper is the last child; the two dialogs after it are outside the Box. zIndex orders siblings only, ties by composition order, so at rest (bubble at 0) the draw and hit-test order is exactly the previous one. The wrapper Box has no background, no pointer input and no clickable, so it is not a hit target itself (the CLAUDE.md `Surface` pitfall needs a drawn background or pointer input; a bare Box has neither). matchParentSize gives the children the same constraints and the same BoxScope alignment they had. **No change at rest for the navigation card, bottom chrome, pickers, Re-center or the L.**
- **zIndex reaches the right node:** `MapBubbleLayer` passes `modifier` to `MapPopUp`, which puts it on `AnimatedVisibility` inside `NoTouchTargetExpansion`; that is a CompositionLocalProvider (`LeavingTakesNoTouches.kt:96-102`), not a layout node, so the zIndex sits on a direct child of CompactMapTab's Box. Also shown empirically by the z-order revert (below).
- **Tap away / the card whole:** `MapPopUp.kt` has no scrim or pointerInput of its own; dismissal is the map's own tap, so raising the layer puts no invisible touch-catcher over the card, chips or strip. The test's tap away reached the map (`taps + 1`) and the card kept its bounds and expanded state.
- **Crowded list:** `CompactMapCrowdedList.kt:86` shows the list only while `tapped == null`, so a raised bubble never coexists with it; the list is not given the floor, per the planner's call.
- **Tests** (`RouteCardBubbleAboveCardTest.kt`, portrait and landscape at S22 qualifiers): the real compact Maps tab; every step a `performTouchInput` (glyph click, swipe to tuck, tap on the strip, tap on the handle); landscape expanded samples a 5x3 grid of real touches on the bubble-over-card overlap, filtered 12 dp clear of the buttons' 48 dp targets (13 touches in the green run), asserting bubble still shown, map tap count unchanged, card state unchanged; then one tap away. `BubbleFloorTest` 5 cases on `bubblePlacement` directly (an anchor under the card, flip-below blocked, beside the card, no room -> overFloor and same place as with no card, room -> not over).
- **Runs** (JUnit from the `unit-test-report` artifacts via `gh api .../zip`, extracted with python3 -I under /mnt/work/scratch/sorrel-507d/art/):
  - Red 42601824280 (efae17d26, test only on the floor build): 5 tests, 1 fail, landscape "expected:<159.66667> but was:<64.0>" (clamped under/against the card, the predicted reason). Portrait and BubbleFloorTest 3/3 pass.
  - Green 42604906657 (4fb42b5fc): 50/50 across RouteCardBubbleAboveCard (2), BubbleFloorTest (5), MapBubblesTest (25), AvailabilityScreenMapBubblesTest (15), CartographyEntryMapBubblesTest (3).
  - Revert z-order 42604910437 (4673faf4c, one line: `modifier = Modifier`): unit-test job log has 0 `e:` lines, `compileDebugKotlin` and `compileDebugUnitTestKotlin` ran, 6960 tests completed, 1 failed: landscape "a touch at Offset(1537.8, 557.0) on the bubble over the card did not reach the route card" (card state changed). That is a failure only this revert can cause; placement assertions before it passed.
  - Revert floor 42604915206 (31bacd81b, one line: `return maxHeight`): 0 `e:` lines, both compile tasks ran, 6960 completed, 5 failed: landscape at peek (ends at or above 256), portrait tucked over the strip (583), BubbleFloorTest 3 of 5 (anchor-under-card bottom 1507 vs 1400; flip-below bottom 343 vs 300; overFloor false vs true). All follow from no floor; the two beside/room cases pass as they should.
  - Forward lines confirmed present at head 074ce1fba in the diff (`if (overCard) Modifier.zIndex(...)`, `minOf(maxHeight, floor.top)`).
  - Full 42607345748 (074ce1fba, the head) vs baseline 42591776170 (d62e04f52; differs from cf8b3a07f only in the report file): 7007 -> 7014 tests, 24 skipped both, the 7 added are exactly the new tests, **no test removed, none changed status.** Nothing silenced; the diff touches no other test file.
- **Report** section "The bubble above the card, built" matches the runs and the code line for line (checked `:534-535`, `:494`, `:549`, `MapBubble.kt:101,:124`, `MapBubbleLayer.kt:63,:144,:155`, `CompactMapBubble.kt:77,:111`, `AvailabilityCompactMapUi.kt:693`).
- **File sizes:** AvailabilityCompactMapUi.kt 787 lines (wc on the head copy). BubbleFloor.kt 57, new.

## Defects back to Hazel

None that block. (Nothing to change for tonight.)

## Owner's calls

1. **A raised bubble can cover the filter chips.** While over the card (and only then) the bubble is above every sibling at 0, including the chips row under the strip (`CompactMapChips.kt:112`: top = topInset + strip clearance + 8 dp; the bubble's `minY` is topInset + strip clearance, `CompactMapLayoutState.kt:71`). So in landscape with the card expanded, a waypoint tapped near the strip gets a bubble clamped to `minY` that draws over the taxon/Journal chip instead of under it, for as long as it is open; the tap away restores it. At rest the chips win, as before. Not seen on a device (Hazel's report says the same). Narrow case, cosmetic, chips still work once the bubble closes; the S22 geometry in the test (bubble at 159.7 dp) does not reach them. Worth one S22 look if time allows; otherwise accept for this release.
2. **Over-the-card can also arise at peek in landscape for a tall bubble** (a sighting bubble with a photo is taller than a waypoint's 128 dp; room at peek is about 204 dp on the S22). That is the ruling applied, not a bug, but the owner may see it before expanded ever shows it.

## Notes

- `onOverFloor` is called from the layout pass and writes a state read in composition (`CompactMapBubble.kt:77`), so the raise lands one frame after the placement; during the bubble's fade-in that is invisible. Stable thereafter (same value writes do not recompose).
- "No room" is measured with the placeable's full height, which includes the tail margin top and bottom, so a bubble a few dp short of fitting by its visible card goes over the card rather than above it. Conservative, matches the ruling's spirit (seen whole).
- The test's "did not reach the route card" uses card-state equality; a touch on a stateless part of the card would pass regardless. The revert shows the sampled points do reach stateful parts, so it bites as written.
- The chrome-after-card wrapper is raised always rather than only while a bubble is over the card (Hazel's "Decided beyond scope"). Agreed: fewer switches, same order at rest.
- AvailabilityCompactMapUi.kt is at 787, 13 lines under the modular-code split line; the next feature into it should split first.

## Could not determine

- Device behaviour: Robolectric reports zero insets, so where the strip and card sit on the S22 in landscape, and whether the chip case above occurs there, is for S22 step 25.
- Whether the Layers panel (pickers, z 2) over a raised bubble looks right; the order says yes, not run.
- Portrait never produced overFloor in the test (card expanded to 0.5 of the map, `ROUTE_CARD_EXPANDED_FRACTION`); a very short portrait window (split screen) could, and there the raised bubble would also sit over the portrait icon bar. Not tested, not seen.
