# Re-check: Hazel's fixes after the #507 review (route-card 50ae42c64 -> b727d1bd4)

Reviewer: Sorrel Tally (Fable 5.1; the coders were two Hazel sessions, Opus). Nothing changed, nothing pushed. No Gradle run here; CI read from the JUnit XML and the job logs.

## Verdict: pass with items

All three rulings are built as the owner said. The side-bar decision is one function both cards call, not a copy. Every re-claimed test is named in the report and tied to the ruling, the fourth class included. Each red/revert pair failed for its own edit's reason on a build that compiled. Nothing was silenced. Two small items for Hazel (one is wording in the report, one a corner to look at on the S22 before anything is changed), and two notes.

## 1. Items back to Hazel

**1.1 The report calls run 42527216431 "green"; it was red.** The report (completion report, "Sorrel's review items, fixed", the 3.2 and 2.2 bullets) cites "green 42527216431" for both fixes. That run's conclusion is failure: 5 failures, all in `RouteCardOutlineOnNavigationCardTest` — the cases the first session had already re-claimed to -967 while 1.1 was not yet built (read from the run's JUnit; the "Build, test, publish APK" job is marked failure). The 2.2 and 3.2 tests themselves pass in it, which is what Hazel meant. Change the words: "the 2.2 and 3.2 tests pass in 42527216431, which is red on the five re-claimed 1.1 cases, fixed in the second part". A run cited as green that was red is the kind of claim the next reader builds on. While there: the report's test counts (6,936 / 6,957) are Gradle's "tests completed" figures, which leave out the 24 skipped; the XML holds 6,960 and 6,981. Say which count is meant.

**1.2 The route card's rounded top corner at the side bar: look at it on the S22 (step 21) before the owner sees a build.** From the geometry, not seen: the card's top corners are rounded 16 dp (`RouteCard.kt:168`). The fix removes the card's line on the side bar's side with a band one line wide along the card's bounds (`RouteCardFace.kt`, `routeCardBareBand`: x from -1 dp to +1 dp, starting 1 dp below the top). The card's stroke is drawn centred on its edge and clipped to its fill, so it lies in the 1 dp inside the edge; the band takes the straight side and the lowest ~5 dp of the corner's curve, and the curve then ends about 1 dp inside the card's edge, which is about one line's width short of the side bar's line. That is the step the owner reported on the S26 and -990 fixed for the navigation card and the strip: their corners on the shared side "run out to that line's centre, one stroke beyond this piece's own edge" (`MapChromeOutline.kt:63-71, :92-94`, `leftShared`/`rightShared`). The route card draws its own path, not `mapChromeOutlinePath`, so -990's extension does not reach it. Hazel disclosed the corner as "Could not determine ... for the S22" (report, "Could not determine", second bullet). Per "see it on the phone before fixing": no change until the S22 shows it. If it shows: extend the corner's arc on the bare side out to the rail line's centre, as -990 does, and pin it with a pixel read at the arc's end (the row where the curve meets the card's side, the column at the card's edge) expecting the ruled line, with the navigation card's corner at the same edge as the control. The tucked strip is not affected: its side-bar edge is a straight cut at the bounds (`routeCardOutlinedPath`, the intersect with the bounds), so the top line runs to the edge.

## 2. The owner's calls

**2.1 Fullscreen with no system bar on that side leaves the route card's side bare** (Hazel's "Decided beyond scope"). Amendment 5's words were "Plain line all the way round"; the owner's (c) says "by the navigation card's own rule", and that rule leaves the side bare when nothing is between the card and the screen's edge (`RoundedMapCard.kt:283`, `railSideInset > 0.dp`). On both test phones a navigation bar is there, so the line shows. Built to (c) as recorded; listed so the owner knows the one case where (c) and "all the way round" differ. No action unless the owner wants the -988 variant (the strip's: lined on the screen edge too), which is one flag on the shared call.

## 3. Notes (no action)

- **3.1 "Navigated from the card" clears when the navigation stops, not when it is replaced.** If a bubble's Navigate starts a new navigation while the card's is still running, the navigating flag never goes false, so the new one is still counted as the card's (`CompactMapRoutePlotting.kt:425`, keyed on `navigatingToWaypoint`). Same visible result as before (one Back ends it, the card stays). The review's 3.2 case (ended, then a later one) is fixed and tested both ways it can end.
- **3.2 The 2.2 test runs without the app theme, and still tests the real code.** The rig composes the real Maps tab (`RouteCardBarRig.setScreen`, `dark = null` leaves the theme wrapper out, `RouteCardBarRig.kt:120-121`); the strip's touch is the real `RouteCardStripTouch`. Under the theme Material's ripple is a platform drawable Robolectric does not draw (Hazel measured 0 changed pixels on the code with the highlight, run 42525146220), so that version could not fail. Without the theme the ambient indication is Compose's own, drawn in Compose, and the revert (the `indication = null` removed, 39b8c15de) failed with 13,370 band pixels changed (#FEF7FF to #B1ACB2). The mechanism is the same on the phone: the `clickable` overload with an explicit `indication` draws only what it is given, and null is none; it does not read the ambient one. So the test bites on the real line. The phone's look (step 20) is still the proof for the owner's eye.
- **3.3 The shared rule is truly one call.** `landscapeNavigationCardOutlineSides` (`RouteCardRailSide.kt:30-45`) is the navigation card's former inline call moved whole (same arguments; `CompactMapLandscapeNavigationCard.kt:131-133` now calls it), and `routeCardBareSide` (`:53-64`) reads its answer for the rail side. Both cards get the same `isFullscreen` and `controlsPadding` from the same caller (`AvailabilityCompactMapUi.kt:675, :683, :693, :695`). No navigation card test changed outcome (by-name below). The far corner (icon bar on the side bar's side) returns null, every line kept (`:59`), tested once by day.

## 4. Rulings, item by item

- **(1.1) "(c) All three"** -> `routeCardBareSide` chooses the side; `routeCardBareBand` removes the card's line there, resting or tucked (the strip's cut edge is the bounds' side), running down into the corner wedge on the navigation card and stopping above the plotting card's bottom line (`RouteCardFace.kt`, band bottom `size.height + cornerFillPx + strokePx` or `size.height - strokePx`). Tests: `RouteCardOutlineOnNavigationCardTest` (7: resting on the navigation card, day/night, both rotations, fullscreen with and without a bar), `RouteCardOutlineLandscapeTest` (7: plotting card, day/night, both rotations, far-corner control), `RouteCardTabOutlineLandscapeTest` (10: the strip plotting and navigating, day/night, both rotations; the bottom on the navigation card read bare per A1), `RouteCardMeetsNavigationCardTests` (8: the side bar's side bare down into the corner, the map side lined). Confirmed in code and CI.
- **(2.2) "Option 1, but remove the tap highlight"** -> `RouteCardTuck.kt:120`, `clickable(interactionSource = null, indication = null, ...)`, width still `ROUTE_CARD_STRIP_TOUCH_WIDTH` (48 dp). Test `RouteCardStripPressTest` (1): real press held in the band beside the strip, pixel compare, lift brings the card back. Confirmed.
- **(3.2) cleared when navigation ends** -> `RoutePlottingViewModel.onWaypointNavigationOver` (clears the flag only; the Back order is untouched, and `step()` reads the flag through `navigationFromCard`, so a stale order cannot act), told from `RoutePlottingBackHandler`'s `LaunchedEffect(navigatingToWaypoint)`. Tests: `RouteCardNavigatedFromCardEndsTest` (2: ended by Back and by the navigation card's X, then a bubble-style navigation is not the card's), `RoutePlottingViewModelTest` (+1). Confirmed.

## 5. CI, read by me

JUnit zips fetched by the API and parsed in place (python3 -I, never extracted); job logs for the three reverts fetched and grepped.

| run | branch / head | result | what I read |
|---|---|---|---|
| 42532668314 red (1.1, tests only) | hazel-rail2-red c5d9d869d | 6,981 / 21 failed / 24 skipped | 6 Landscape + 5 OnNavigationCard + 10 TabLandscape; every message carries "not bare (a second line beside the side bar's)"; the 4 navigating strip cases also name "bottom ... not the ruled line" (the test error Hazel fixed in 02f87374d). Matches the report. |
| 42532670714 | hazel-rail2-green b69481ee4 | failure | compile: public `RouteCard` exposing internal `ScreenEdge` (as reported; not re-read, the fix is ea7f58182). |
| 42534637801 | hazel-rail2-green ea7f58182 | 6,981 / 8 failed | the 8 `RouteCardMeetsNavigationCard(Short)Test` cases, fix in, claims not yet changed. Matches. |
| 42536206321 revert (1.1) | hazel-rail2-rev 74fd75829 | 6,981 / 29 failed / 24 | the 21 + the 8 Meets; all 29 messages carry "not bare"; the revert is one line (`bareSideOnLeft = null`, read from the commit); the job log has 0 `e:` lines and "6957 tests completed, 29 failed, 24 skipped". |
| 42536203917 green (1.1) | hazel-rail2-green 046b2e168 | 6,981 / 0 / 24 | |
| 42538164195 green (PR, full) | route-card b727d1bd4 | 6,981 / 0 / 24 | the head under review. |
| 42525146220 red (3.2 + first 1.1 re-claim) | hazel-rail-red 6a2388d0d | 6,968 / 7 | 2 NavigatedFromCardEnds ("none is the card's now") + 5 OnNavigationCard. |
| 42527186082 red (2.2) | hazel-rail-red2 e826c2525 | 6,967 / 8 | + StripPress, "#FEF7FF -> #B1ACB2". |
| 42527216431 (cited green) | hazel-rail-fix e9ce2e3bf | **failure**, 6,968 / 5 | the 5 OnNavigationCard re-claims; 2.2 and 3.2 tests pass. Item 1.1 above. |
| 42529011439 revert (3.2) | hazel-rail-rev32 9e4a5386f | 6,968 / 7 | the 2 NavigatedFromCardEnds + the 5 OnNavigationCard (not yet built); the revert removes the one `LaunchedEffect` line; log 0 `e:`. |
| 42529006236 revert (2.2) | hazel-rail-rev22 39b8c15de | 6,968 / 6 | StripPress + the 5; the revert removes `interactionSource = null, indication = null`; log 0 `e:`. |

**By name, 42521097620 (9201b8a3e, the run my first review read) against 42538164195:** 6,960 -> 6,981; 7 names gone (the 2 Landscape, 3 OnNavigationCard, 2 TabLandscape re-claims, each renamed), 28 new (the renamed and added cases above, plus NavigatedFromCardEnds 2, StripPress 1, ViewModel 1); 0 tests changed outcome; the 24 skips are the same set. Tallies with Hazel's "7 gone, 28 new".

## 6. Nothing silenced

No `@Ignore`, `assume`, or skip in the diff; no allowlist or workflow file touched (diff names only `app/src` and the report); skip set identical by name.

## 7. Touch over the map

The face is draw-only (unchanged); the band is a clip inside it. The clickable change alters indication only, not bounds. The effect has no UI. The existing long-press-reaches-the-map cases (`RouteCardTuckCloseTest`) have the same outcome by name.

## 8. What I checked

Diff 50ae42c64..b727d1bd4 in full (7 main files, 7 test files, the report); -2187's three newest bullets and -967 from records-after-173; the shared rule's source and both callers; the three revert commits' patches by the API; ten JUnit zips and three job logs.

## 9. What I could not determine

Anything on a phone: the corner (1.2), the ripple's absence under the real theme (3.2), the side bar's own line at that edge (Hazel's inference and mine, from -967/-990). The compile result of 42532670714 (taken from the report). Whether the far-corner card at night matches (tested by day only).
