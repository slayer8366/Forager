# Off-route detection in walking navigation: what others do (research for plan task T21)

**Status:** research, read-only. Nothing built or decided. Filed by the planner on 2026-10-02 (UTC).

**Asked for by the owner,** verbatim (2026-10-02): "We can also research how and when Google determines off-track behavior for their walking navigation to see where we can improve our methods."

**How it was made, and how far to trust it.** A research agent run by the planner searched public sources on 2026-10-02 and read the open-source files it cites from GitHub. **The planner has not re-checked the claims below**; each carries its source, and each is marked as documented, observed by a third party, read in code, or inferred. Some pages could not be fetched (HTTP 403): a widely used hiking app's and a route-planning app's help and the current Mapbox off-route guide; for those only search snippets were seen. **No patent or patent application was consulted.**

**Why it matters here.** Forager's current off-track rule compares the straight-line distance to the start across the last three readings, about one second apart, and alerts when it grew by more than 25 m, with a 120 s cooldown (`domain/DetectOffTrackUseCase.kt`, `domain/ReturnWatch.kt`). By arithmetic, steady walking cannot trip it and one jumpy reading can (dispatch `2026-09-28-400`, Part 1 report, flag 2). The owner ruled that its meaning is to be redefined to fit the user (`RECORD.md` decision `2026-09-28-402`; plan task T21). The way-back route (plan task T5, dispatch `-417`) separately withholds the route when the walker is far from the walked path.

## (a) What Google documents, and what is unknown

- **Documented:** the Navigation SDK has `Navigator.ReroutingListener.onReroutingRequestedByOffRoute()`, "called when a route change is requested in response to vehicle going off the suggested route", with no distance, time or accuracy criterion given. https://developers.google.com/maps/documentation/navigation/android-sdk/reference/com/google/android/libraries/navigation/Navigator.ReroutingListener
- **Documented:** the SDK's positions are "road-snapped" and "may be different from the location returned by the fused location provider"; its events page lists no off-route threshold. https://developers.google.com/maps/documentation/navigation/android-sdk/events
- **Documented:** Google's 2019 research post on Live View says city GPS can be "a few blocks away" and the compass "can be inaccurate by up to 180 degrees"; it says nothing about rerouting. https://research.google/blog/using-global-localization-to-improve-navigation/
- **Observed by a third party, attributed to Google:** a 2019 India taxi feature alerts at 0.5 km off route. A vehicle feature. https://yourstory.com/2019/06/google-maps-off-route-alert-feature-india
- **Unknown:** when a mainstream maps app's walking navigation decides a walker is off route. No official source was found, and no number is guessed.

## (b) Open-source rules (read in code by the research agent)

**An offline maps app** (`RoutingHelper.java`, `RoutingHelperUtils.java`, master as fetched 2026-10-02, from its public source repository)
- Tolerance 30 m plus the reported accuracy, or 60 m with no accuracy (`POS_TOLERANCE = 60`).
- Default deviation limit twice that: 60 m + 2 × accuracy. User-settable ("Minimal distance to recalculate route", in its navigation settings documentation).
- A single fix past the limit triggers a reroute; no persistence. A code comment says a delay was left out because "late detection is worse than false positive".
- A wrong-direction test (bearing more than 90° from the route) applies only when the fix carries a bearing and is beyond the limit.

**Organic Maps** (commit 3f26ce89; `libs/routing/routing_session.cpp`, `route.cpp`, `routing_settings.cpp`): https://github.com/organicmaps/organicmaps/blob/master/libs/routing/routing_session.cpp
- On route if within max(matching threshold, reported accuracy); the pedestrian threshold is 20 m.
- Each fix off route adds to a counter (+2 with a speed, +1 without), only if its distance changed by more than 0.01 m. A rebuild after the counter passes 10: about 6 fixes with speed, 11 without. **One on-route fix resets it.**

**Mapbox, legacy open-source iOS SDK v1.4.2** (`CoreConstants.swift`, `LegacyRouteController.swift`): https://github.com/mapbox/mapbox-navigation-ios/blob/v1.4.2/Sources/MapboxCoreNavigation/CoreConstants.swift
- Off-route radius 50 m, 25 m within 40 m of an intersection.
- A course test after max(4, accuracy ÷ 4) consecutive wrong-course fixes, but only at 3 m/s or faster with accuracy under 20 m, so effectively never at walking pace (inferred).
- No second reroute until the user has moved 50 m from the last one.

**Mapbox, Android 0.42.6** defaults: 50 m, 25 m near an intersection, 40 m intersection radius; the detection itself is closed native code. **Current Mapbox SDK:** an `OffRouteObserver` hook, thresholds not published. **Valhalla:** no navigation client; its map matching takes a per-point `gps_accuracy` and a `search_radius` up to 100 m.

## (c) Techniques that fit a walker off-trail, with noisy GPS and the phone in a pocket

Evidence:
- Android documents reported accuracy as a 68%-confidence radius, so about one fix in three falls outside its own radius even when honest. https://developer.android.com/reference/android/location/Location#getAccuracy() (search snippet; page not fetched)
- The engines handle noise three ways: **widen the corridor by accuracy** (the offline maps app, Organic Maps); **require persistence** (Organic Maps); **rate-limit by distance moved** (Mapbox).
- Heading is used only with a real bearing (the offline maps app) or at 3 m/s and above (Mapbox).
- **Observed by third parties:** off-route warnings under tree cover where no other path exists. https://wahoox.forum.wahoofitness.com/t/off-route-warning-when-in-wooded-area/29495
- A widely used hiking app (search snippet only): alerts about 50 m off the planned route, can be silenced for the outing, less reliable where GPS is weak. A route-planning app (snippet only): alerts need recalculation switched on. A widely used backcountry map app: no documented settings found.

Inferred by the research agent, for the owner and the planner to weigh:
- Measure distance to the **walked path**, not distance to the start.
- Scale the corridor by reported accuracy, with a floor.
- Require persistence measured in seconds, not one jump.
- Network-provider fixes and fixes with very large accuracy neither count toward an alert nor reset the count.
- Hysteresis and one alert per episode, in place of a fixed cooldown.
- No heading: phone in a pocket, walking speed below every engine's bearing gate, and compass error documented up to 180°.

## (d) Candidate rules for Forager, each testable on recorded walks

Each needs the same per-fix log on recorded return walks (distance from the walked path, accuracy, provider, time), with the stretches the walker was really on the path and the deliberate detours labelled by the walker.

1. **Accuracy-scaled corridor** (offline maps app style): outside when the distance from the path exceeds max(25 m, 15 m + 2 × accuracy). Confirm: under 5% of on-path fixes outside, and detours over 50 m flagged. Reject: a fixed 50 m line does as well.
2. **Persistence gate:** alert only after at least 15 s and 5 GPS fixes all outside. Test: the longest run of outside fixes on on-path stretches against the time a real detour takes to show.
3. **Fix filter:** network fixes and fixes with accuracy over 40 m freeze the count. Count against the log's total of fixes, so none is silently dropped (the parser failure recorded in `CLAUDE.md`).
4. **Hysteresis, one alert per episode:** enter "strayed" at rule 1's line; leave only after 10 s below 60% of it; alert once per entry. Target: exactly one alert per labelled detour.
5. **Trend rather than jump:** the slope of distance-from-path over the last 30 s above 0.3 m/s, with the latest distance above the floor. Reject if it does no better than rule 2.

**The current rule can be tested on the same data:** count its alerts on on-path stretches and on detours. That would confirm or overturn the arithmetic claim that steady walking cannot trip it.

## Unverified

Google's walking thresholds; the backcountry map app; the current Mapbox native logic; the full hiking app and route-planning app pages; every claim above that the planner has not re-checked against its source.
