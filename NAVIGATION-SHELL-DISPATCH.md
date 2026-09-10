# Dispatch B — the navigation shell

Destination-level restructure. This creates the homes; Dispatch C fills them.

**Order:** send after Dispatch A (strip revert + pill). Both touch
`AvailabilityScreen.kt` and A is smaller.

**Known cost, accepted deliberately:** this moves composables between
destinations inside a file past 5,109 lines with a 60-parameter entry point
and 15 test files pointed at it. The split exists to make exactly this
tractable, and it is not happening first because of the OMS deadline. Some of
this work will be redone when the split lands. That is a chosen trade, not an
oversight — record it in the commit message so the next reader knows.

---

```
1. BOTTOM NAV — five destinations, in this order:

     List · Seasonal · Maps · Journal · Tools

   - Maps moves to position 3, a true centre. It is the surface the
     user is actually in; the others are approached from it.
   - The ordering reads as a trip, left to right: Pre-trip surfaces
     (List, Seasonal) on the left, Post-trip and rare (Journal,
     Tools) on the right.
   - "Settings" is renamed "Tools" and opens the new Tools panel.
   - Album is removed as a top-level destination. See item 3.

   Note at the call site that the centred Maps position depends on an
   odd destination count. A sixth destination breaks it.

   CompactTab currently has 6 entries (AvailabilityScreen.kt:247-261
   per the earlier pulse — re-derive, the file has moved). Report the
   before and after.

   Also: ForagerBottomNav's doc comment claimed "5 destinations" when
   there were 6. It was corrected to 6 in the bdd5b31 sweep. It is
   about to be 5 again for real. Make sure it ends up accurate rather
   than accidentally back to its original wrong text.

2. THE TOOLS PANEL
   The page advanced search currently occupies becomes Tools.

   Contents: the tools already there (waypoint, trip planner), plus
   Settings as an entry at the BOTTOM of the panel, opening the
   existing settings page unchanged.

   Tools is deliberately a catch-all for things used but not wanted
   in the immediate way. That makes it prone to becoming a junk
   drawer. Record its inclusion rule next to the panel so the next
   addition has to argue: per-trip and rare items that are not
   destinations in their own right.

   Do NOT move Album here.

3. ALBUM INTO JOURNAL
   Journal already has top tabs. Album joins them as a third
   alongside the existing Log and Drafts.

   Album and log entries share a phase and a frequency — per-find
   capture at the specimen, Post-trip review at home. They were only
   separate destinations because they were built separately.

   Report the resulting tab order and whether the existing Journal
   tab implementation takes a third tab without restructuring.

4. SEARCH ICON OFF MapIconBar
   Remove it. Advanced search moves to the top of the app in
   Dispatch C.

   Report the bar's resulting row count. It was 8; return-to-vehicle
   went in e4e3be6, record goes in Dispatch A, search goes here.
   Re-derive rather than assuming — confirm what is actually left and
   whether the remaining set still reads as a coherent group of
   map verbs.

5. NAVIGATION CORRECTNESS
   Before this lands, the known defect is that search is reachable
   from only one of six tabs. After it lands, verify every
   destination is reachable from every other in one tap, and that
   nothing has become stranded by the Album and Settings moves.

   Add a test asserting that, so the defect class cannot recur
   silently. This is the 4S check the layout plan calls for and it is
   cheap to write now while the nav is already being touched.

6. BACK BEHAVIOUR
   f5d21c4 added a BackHandler to the quick-search panel, closing the
   one gap in an otherwise consistent step-by-step back-navigation
   pattern. The Tools panel and Journal's new Album tab both need to
   fit that same pattern. Check the existing design rather than
   inventing new behaviour, the way f5d21c4 did.

7. TOUCH ROUTING
   Dispatch A introduces the first performTouchInput coverage in this
   suite, scoped to the bottom-right corner. Nothing here adds a
   floating surface over the map, so this dispatch does not extend
   it — but do not remove or weaken what A added, and confirm the
   composition-order note in AvailabilityScreen's Box survives this
   restructure intact.
```

---

## What this does not cover

Panel contents. Advanced search moving to the top, the redundant list removal,
and the weather panel move are Dispatch C, which depends on the destinations
this dispatch creates.
