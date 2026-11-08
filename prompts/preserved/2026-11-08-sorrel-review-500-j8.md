# Sorrel Tally: review of PR #500 (next-turn) ef6998ca1..961ff2a16 — the review-item fixes and J8

Reviewer: Sorrel Tally, on Fable (the coders are Opus). Read-only; no code changed, nothing pushed, no Gradle run. Date 2026-11-08.

## Verdict: pass with items

Nothing blocking. Two small test items go back to Hazel (neither needs to hold a build the owner looks at on the phone); three things for the owner to hear in one line each; notes. The release-packaging out-of-memory is, on the evidence, a runner failure and not J8's.

**Mechanism prediction, written before reading the code** (profile step 2): the fix would be a bottom padding on the camera, carried on the same path as D29's sideways padding; the risks would be (a) the padding applying while the walker has panned away, (b) a loop where the camera's move changes a card decision that changes the camera, and (c) tests that read only the padding the map was *asked* for, in settled states. **Scored at the end:** (a) handled, by D29's existing gate; (b) avoided, by two deliberate choices; (c) true, and disclosed by Hazel. Details below.

---

## (1) Defects to go back to Hazel (test-only; nothing a user sees)

### 1.1 "Resting top, not drawn top" is a stated behaviour with no test that can tell the two apart
- **Where:** `CompactMapLandscapeNavigationCard.kt:283-291` (at 961ff2a16) writes the card's *resting* bounds: its height measured up from the bottom edge less its intended move (`liftPx`, where the card is going), not where it is drawn mid-glide. The commit message and the report both claim this. Hazel's own "Could not determine" says it plainly: "Nothing proves the resting (not drawn) top through the real screen: the tests read settled states, where the two agree."
- **Why it matters:** this is exactly the shape CLAUDE.md warns about: a check that passes identically with the mechanism changed. If a later edit read the drawn top instead, every J8 test would stay green, and on the phone the dot would chase the card's glide frame by frame rather than go to its place once.
- **What would show it:** in a rig like `NavigationCardMovesUpTest` (one evening line, font 1.5 or more, the card moving up), pause the test clock part-way through the card's glide and assert that `cluster.navigationCardRestingBoundsInRoot.top` equals the card's final top (bottom edge less the full move less its height), not its drawn top at that frame, with the message naming the frame. Revert by reading `drawn.top`/`drawn.bottom` in the rect; the test must go red for that reason.
- **Small. Not blocking.** The planner's build line asked for the resting top explicitly, so the behaviour is right; only the proof is missing.

### 1.2 Nothing holds "(a) Landscape only"
- **Where:** `AvailabilityCompactMapUi.kt:536`: `applies = navigation.isNavigating && cluster.landscape`. Correct by reading. No test runs the Maps tab in portrait and asserts the view is asked for no bottom padding.
- **Why it matters:** the owner ruled it; a one-word change (`landscape` dropped) would pass every J8 test. A portrait real-screen test may not bite if the portrait card never reaches the dot (then the padding is 0 for the overlap reason, not the ruling's), so the honest check is on the small pure function: `navigationInsetsWithDotAboveCard(side, applies = false, cluster)` with a cluster whose card does cover the dot returns `side` unchanged. One test, a few lines, in `NavigationDotAboveCardInputTest`.
- **Small. Not blocking.**

---

## (2) The owner's calls

### 2.1 The dot's glide and the card's glide run on different clocks
The dot moves on the camera's padding animation (750 ms, linear, D29's existing shift; at once under Reduce Motion), aimed at the card's *resting* top. The card's own move up or down runs on its chrome spring (`navigationCardLift` via `animateFloatAsState`). The owner's J8 build line said "gliding with the card's own movement"; the planner's "J8 answers" then set the resting top through D29's effect, which is what was built. In practice: when the card grows on the bottom edge (Try again appears), the card jumps and the dot glides up over 750 ms; when the card moves up (J3 step 3), the two glide on different curves and may not look locked together. Device-only; worth one look on the S22 (Hazel's step 1 and 2). [Inferred from code; not seen on a phone.]

### 2.2 Three readings Hazel decided beyond scope — each checked against the recorded words
- **D29 sideways move plus a bottom where the navigation card still covers the shifted dot.** The owner: "while the route card is expanded or elsewhere, D29's sideways move as today"; the planner's build: "only when the top card overlaps the dot across and up-and-down, else 0." Reading D29's rule (`RouteCardPlacement.kt:307-316`): it puts the dot in the middle of the open map beside the card, or asks for *nothing* when nothing is left open. So the bottom can only be added in that nothing-open case, where the dot would otherwise sit under the card. **Fits both recorded lines**; D29 is unchanged where it works. Reachable only on very narrow landscape windows; not tested on the real screen.
- **The hold at the top padding's edge, logged.** A guard for a card on top reaching into the map's top quarter; Hazel says unreachable with today's cards (the strip bounds them). Fits CLAUDE.md's "no unlogged fallback". Fine. Not a behaviour.
- **Overlap across uses the navigation card's columns even when another card rests on it.** Fits "(b) Above top card" as the route card rests on the navigation card and D32 narrows both together. #507 should confirm the route card's columns never exceed the navigation card's; if they could, the across-check would miss a dot under the route card's overhang.

### 2.3 The release packaging job's out-of-memory (run 42504332419) — not J8's, but a tripwire
The job: `packageReleaseBundle` and `packageRelease` both failed with `java.lang.OutOfMemoryError: Java heap space` (log lines 193-206, 393, 600); Kotlin compile had succeeded. The same job was green at cafe5849e (42492588638) and green again at 961ff2a16 (42506464973), and 961ff2a16 differs from 1cc818e14 by the report file only (`git diff --stat 1cc818e14..961ff2a16`: one .md). So identical code packaged fine on the next run. J8 adds about a hundred lines of Kotlin and no resources, assets or dependencies. **Read as a runner memory failure, with J8 ruled out by the docs-only re-run.** The owner's call: whether to ask for the packaging step's heap to be raised before the next release, since two packaging tasks ran in one worker and one flake has now been seen.

---

## (3) Notes

- **No loop.** Followed every reader of the two new cluster fields (`git grep` at 961ff2a16): the resting bounds are written by the card's layout callback and read only by `NavigationDotAboveCard.kt:67`; nothing reads the camera's padding back. The card's own decisions (`navigationCardLift`, the evening lines, D42) read the dot's *home* position, a fixed formula from the map box's height (`NavigationCardLift.kt:60-64`), not the moved one. The overlap test itself compares against that home position, so there is no hysteresis either: the card does not "stop covering" the dot once the dot has moved. The resting rect is written on every layout pass but changes only when the card's size, columns or intended move change, so the state does not churn through a glide. [Confirmed by reading.]
- **"Following" is honoured, through D29's gate, not J8's own code.** The shift effect (`MapNavigationView.kt:88-96`) returns before touching the camera unless `view.following`; `shiftPadding` (`NavigationView.kt:530-532`) also refuses during a mode transition or when tracking is off. Re-center / locate re-run `applyView`, which reads the side insets, bottom included, at landing (`NavigationView.kt:501-503`). Both device-only; nothing in Robolectric can show them. Hazel's S22 step 5 covers Re-center.
- **The arithmetic is right.** With top padding T and bottom B on a map H tall, the dot sits at T + (H−T−B)/2. Setting that to (card top − 12 dp) gives B = 2·(dotHome − (top − 12)), which is `wanted` at `NavigationDotAboveCard.kt:47`; the cap keeps the dot 12 dp under the top padding's edge (`:48`). The nudge adds its give on top of the bottom (`NudgeElastic.kt:98`), consistent with the sides.
- **The J8 tests and the red run.** Read from the JUnit XML, not the report: 42502181047 (tests without the mechanism): 6 tests, 2 failed, both "the card … covers none of the walker's dot at y 225.0 / 240.0" — the dot still at home under a card topping 194 / 218 dp; the four font 1.0 cases passed. That red run is the positive control for the whole mechanism. 42502422124 (forward): 61 tests, 0 failed. 42502480738 (the input ignored): 12 tests, 2 failed, both in the input test and both ones that edit produces ("expected 138.0 but was 218.0" is the navigation card's own top less 12; "held" is the cap never reached because the input is ignored). The real-screen classes stayed green in that revert, as they should: they do not exercise the input. **The revert's failures belong to the revert.**
- **Could the real-screen tests pass without the mechanism?** No: they compute the dot from the padding the map is *asked* for, and assert it clear of the card in the font 2.0 case that was covered before, and that the padding is exactly NONE where the card already clears. They do read the request, not the camera (MapLibre is a stub); Hazel says so in the class comment. Whether MapLibre moves as asked is device-only.
- **Full CI by name** (my own comparison of the two artifact sets, 1067 and 1070 XML files): 7316 → 7328 tests, 0 failed, 24 skipped both; 12 new names, all J8's; no name gone; no outcome changed. Matches the report's figures.
- **The review-item fixes bite.** D42 revert 42491765339: 6 tests, 1 failed, `RouteCardSundownStepsAsideFont15Test`: "3 stops, peek: D42, the sundown line steps aside (1 is the line still on the navigation card) expected:<0> but was:<1>" — the message my item asked for, and the five other classes stayed green with D42 unwired, which was the point. E1 revert 42469626623: 13 tests, 2 failed, the new containment test ("at 15.05 m with 8.0 m accuracy the needle is withheld … but the top line does not read Arrived") and the existing within-circle test. The B2 dusk regex now pins the clock's shape (`AvailabilityScreenLandscapeB2Test.kt:974`). My premise that 823 × 384 at font 2.0 or 640 × 360 at font 1.3 would serve as the positive window was wrong; Hazel probed nine windows and chose 823 × 384 at font 1.5, and the class comment records why. Fair correction.
- **The four renames** say what the classes prove ("J6, the line is already off before the card opens …"). The 823 × 384 font 2.0 class now asserts that precondition instead of `null`. Good.
- **File sizes (400/800 rule):** new `NavigationDotAboveCard.kt` 85 lines. `AvailabilityCompactMapUi.kt` 766 → 772, under 800 but close; the next feature into that file should split it first. `CompactMapLandscapeNavigationCard.kt` 303, `MapIconClusterState.kt` 220, `NavigationView.kt` 610 (one line). Nothing over the line.
- **Play users:** the only production change outside landscape navigation is a defaulted fourth field on the padding value, 0 everywhere else; `navigationViewPaddingPx` and the nudge pass it through. In portrait and on every other map the camera's padding is byte-for-byte what it was. The worst case I can construct is the capped hold: the padded area shrinks to 24 dp tall with the dot pinned under the top quarter — unreachable with today's cards, and logged if reached.
- **One stale figure:** my earlier review had the card at 172-360 dp at 780 × 360; at cafe5849e it is 194-360 (J7 moved the next turn to the right part). Still covering the dot at 225, so the case stood. Hazel caught and recorded this.

---

## What I checked and how
- Fetched next-turn, records-after-173 and pre-main; read the full `git diff ef6998ca1..961ff2a16` for main and test sources, the J8 and "J8 answers" rulings in `prompts/preserved/2026-11-06-01-amendment-1.md:132-133`, RECORD -2183's 2026-11-08 bullets, my previous review's items 1.1, 1.2, 3.6, and the two new report sections.
- Read at 961ff2a16: `NavigationDotAboveCard.kt` whole; `CompactMapLandscapeNavigationCard.kt:120-303`; `MapNavigationView.kt:30-96`; `NavigationView.kt:330-350, 425-470, 525-560`; `NavigationCardLift.kt:50-64`; `RouteCardPlacement.kt:300-316`; `SightingsMap.kt:395-420`; both J8 test files whole; `git grep` for every reader of the two new cluster fields and of `shiftPadding`.
- Downloaded the seven JUnit artifacts into their own folders under /mnt/work/scratch/sorrel-j8 and parsed them with my own script under `python3 -I` (counts, failures and messages, a by-name comparison of the two full runs). Pulled the failed-step log of 42504332419 and the job tables of 42504332419, 42506464973 and 42492588638.

## What I could not determine
- Anything on a phone: whether MapLibre moves the camera as asked, how the two glides look together (2.1), Re-center landing the bottom (Hazel's step 5), and the "following" gate. All device-only by construction.
- Whether the packaging OOM will recur; one occurrence, bracketed by two green runs of the same code.
- The route card's side of "(b)": on #507 by design, not here.

## One-line answers
- Does J8 do what the owner ruled? Yes: the dot is kept 12 dp above the card on top's resting top, in landscape only, through the camera's padding, and the card is untouched; "(b)" is built as an input the route card fills on #507.
- Any visible behaviour no ruling asked for? None reachable with today's cards; the two guards are logged or fit the planner's "only where it overlaps".
- Could the tests pass without the mechanism? The real-screen tests, no (red run 42502181047 shows it); the resting-top claim and the landscape-only gate have no test (items 1.1, 1.2).
- Any loop? No.
- The OOM? Runner, not J8.
- Before the merge word: 1.1 and 1.2 to Hazel at her convenience; the owner's S22 steps 1, 2 and 5. Merging is the owner's separate yes.
