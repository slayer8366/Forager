# Round 3: the lifecycle gate examined, and three more corrections

**Date:** 2026-09-11
**Type:** investigation + correction record. **No code changed.**
**Follows:** `2026-09-11-maplibre-pmtiles-policy-corrections-round-2.md`.
**Origin:** the owner asked which lifecycle the gate observes, whether a stale fix can reach a
find's coordinate, whether the cited tests cover the wiring, and returned three corrections to
round 2. All answered from the tree. **All three corrections hold.**

---

## 1. It is the Activity lifecycle, and the comment defending that choice is wrong

`MainActivity.kt:223`: `lifecycle.addObserver(object : DefaultLifecycleObserver { ... })`. That is
`ComponentActivity.lifecycle` — **MainActivity's own**. `ProcessLifecycleOwner` appears nowhere in
`app/src/main`, in `app/build.gradle.kts`, or in the version catalog.

So the owner's premise holds: any Activity that covers Forager drives `ON_STOP` and releases the
subscription.

**The comment defending the choice cites the file that disproves it.** `MainActivity.kt:215-218`:

> ON_START/ON_STOP rather than ON_RESUME/ON_PAUSE: [...] it avoids churning the subscription on
> every transient pause (a dialog, the app's own camera launch — the same confusion
> `PhotoAcquisitionLaunchers.kt` documents for its own ON_STOP heuristic).

`PhotoAcquisitionLaunchers.kt:65-76` documents the opposite of what it is cited for:

> True from the moment a launcher below hands control to an external Activity (the camera app, the
> system permission dialog, the photo picker) [...] **both produce identical ON_PAUSE/ON_STOP
> events.** That confusion is what was silently closing the find being edited [...] **on every
> single camera round-trip**: not process death, not a rare low-memory case, but a deliberate
> lifecycle hook firing on a self-initiated launch.

A dialog pauses. A full-screen Activity from another process **stops**. `ACTION_IMAGE_CAPTURE` is
the second kind, and this project already established that on hardware. So the lifecycle gate does
churn the subscription on every camera round-trip, and the named example is exactly the case the
comment gets wrong.

**An inconsistency worth recording alongside it.** `PhotoAcquisitionLaunchers` exists partly to
carry `acquisitionInFlight` up to `AvailabilityScreen`'s ON_STOP heuristic, so that a self-initiated
camera launch is not mistaken for a backgrounding. **`MainActivity`'s observer has no such guard.**
The photo fix protects the find being edited; it does not protect the location subscription.

## 2. The stale-coordinate bug does not materialise, and the reason is structural

Traced end to end, because this is the half that would have been serious.

**A photo's coordinate does not come from `liveFix`.** `MushroomLogViewModel.kt:151`: "`[locationProvider]`
is only ever read from `[patchCameraCaptureLocation]`, never awaited by `[onAddPhoto]`/
`[onAddGalleryPhoto]` themselves." `patchCameraCaptureLocation` (line 658) calls
`locationProvider.getCurrentLocation()` — a fresh one-shot, fired after the capture returns.

**`liveFix` reaches no save path at all.** Its only consumers are display: the compass strip
(`AvailabilityScreen.kt:3757`, via `liveLocation`), the true-heading computation, and the navigation
HUD.

**The one-shot takes no cached fix.** `AndroidLocationProvider.getCurrentLocation()` races
`requestSingleUpdate` across every enabled provider under a 20-second timeout. There is **no
`getLastKnownLocation` call anywhere in the file**, which is precisely where a stale fix would enter.

**A missing fix is reported as absence, not filled.** `patchCameraCaptureLocation`:
`locationProvider.getCurrentLocation() as? LocationResult.Success ?: return@launch`. No fix means no
write; the row's `latitude`/`longitude` stay `null`, which `LogPhoto`'s doc comment calls "the
ordinary case, not an error." That is `CLAUDE.md`'s rule kept: an unsupported capability returns an
explicit nothing rather than a fabricated plausible value.

**So the answer to the question asked is: no stale fix can reach a find's coordinate**, and it is
not a happy accident — the acquisition path and the display path are separate by design, and the
one that writes never reads a cache.

**What does degrade,** stated so it is not lost in the good news: during a camera round-trip the
compass strip's live coordinates go `null` and must re-acquire on return, which on a cold GPS
re-lock is seconds of blank readout. That is a UX cost, not a data-integrity one, and it is the real
consequence of the mistaken comment in §1.

## 3. The cited tests do not cover the wiring, and I miscounted them

**The owner is right on both counts.**

**Two tests, not three.** Lines 301 and 305 are both inside a single test beginning at line 295,
`leaving the foreground releases the fix subscription, and returning re-acquires it`. Line 320 is in
a second, `a permission grant while backgrounded does not re-acquire the subscription`. Round 2 said
"three tests" by counting line numbers rather than test functions — the wrong unit, which is the
same family of error as the rest of this thread and is why the owner's instinct to recount was
right.

**Nothing constructs `MainActivity`.** Searched `app/src/test` for `MainActivity`, `ActivityScenario`
and `launchActivity`: every hit is a doc comment, except the Compose tests, which use
`createComposeRule()` — hosting a generic `ComponentActivity`, not `MainActivity`. So
`MainActivity.onCreate`'s observer is never registered in any test, and
`AvailabilityScreenBackNavigationTest.kt:145`'s `moveToState(Lifecycle.State.CREATED)` moves the
Compose host, not the app's Activity.

The codebase already says so in one place. `CartographyScreenTest.kt:125`: "is MainActivity's own
job, **untestable from here**."

**`MainActivity.kt:225-226` — the two lines the published privacy policy depends on — are
untested.** Reverting line 226 would leave the suite green.

**I did not run the revert, deliberately.** The static fact is stronger and cheaper: a test that
never constructs the class cannot cover a line in it, and no build outcome changes that. `CLAUDE.md`
records two separate ways this project's revert runner has produced false confirmations — stale
JUnit XML after a non-compiling revert, and a `git checkout --` restore that silently discarded the
uncommitted forward change. Spending that risk to re-derive a conclusion already established by
`grep` would be the wrong trade.

**The owner's proposed test is the right one** and closes the gap at its real level: drive an
`ActivityScenario<MainActivity>` to `CREATED` and assert zero registered listeners on a fake
location source. That tests the policy's actual sentence rather than the ViewModel's contract.

## 4. Three corrections to round 2, all accepted

### 4a. "A merged branch leaves no ref" is wrong as stated

Merging does not delete refs; deleting them does. The ref still exists in `forager-bak`, which is
how the owner confirmed `5967dd5` independently.

The correct and narrower lesson: **a missing ref tells you nothing about whether its commits are
reachable.** Reachability is a property of the commit graph, and `git log -S` on the file answers it
directly. Round 2 stated a mechanism where it should have stated an inference rule.

### 4b. The narrowed logging commitment does not match the published text

Round 2 said the privacy commitment "is about *request* logs." `docs/legal/privacy-policy.md:85-86`
is broader:

> **We keep no request logs.** The Worker's own code writes no log lines
> (`server/pmtiles-worker/src/`), and Workers Logs is explicitly disabled rather than left to the
> default.

Two claims, not one. The second is unqualified and covers any log line, error logs included.

The owner's warning is the operative part and is recorded here rather than left implicit: **writing
the narrower reading down as the policy's scope would later appear to license an error log that
breaks the published sentence.** A future session satisfying `CLAUDE.md`'s "no unlogged fallback"
rule with a `console.error` would be following one rule into a violation of a published document.

The response header remains correct precisely because it writes no log line at all.

### 4c. The index counts are dated

Re-derived from the GitHub API this session against `slayer8366/Forager`:

| | `docs/audits/README.md` | Today |
|---|---|---|
| Branches | 45 | **52** |
| Pull requests | 93 | 93 (API, `state=all`) |

The owner's 52 reproduces exactly. Their 94 pull refs against my 93 pull requests is most likely
`refs/pull/N/merge` entering the ref enumeration, and is not worth litigating — the rule is the
point. **Re-derive before citing.** Round 2 quoted 45/93 from the index as though current; they were
a snapshot of 2026-09-10.

## 5. The maxzoom fork: I proposed reversing a decision without checking whether one existed

Round 2 offered "advertise `maxzoom: 14`" as a clean one-line option. **A prior dispatch already
chose 15, deliberately, and recorded why.** `OfflineMapRepository.kt:96-108`:

> `[MAX_ZOOM]` is **15.0**, not 14 — that's the branch's own already-verified value [...] not the
> 14.0 an earlier plan draft assumed. [...] the `us.pmtiles` archive backing the download source is
> built to zoom 14, but the Cloudflare Worker now range-reads and caches individual tiles one level
> beyond that directly from Protomaps' live daily build.

Proposing the reversal without looking for an existing decision is the same error as the rest of
this thread, one level up: not an unchecked fact this time, but an unchecked *decision*.

**The owner's question about stakes is answerable from this project's own recorded numbers**, which
are better than anything I would have estimated. Same file, lines 75-84:

> with zoom 15 in every download a 15 km region at 45°N is **~1 781 tiles, not the ~480** it was at
> ceiling 14, so this budget now holds about **three** such regions rather than about **nine** [...]
> The owner's decision was to shrink the radius ceiling (`[MAX_RADIUS_KM]`) to fit this budget rather
> than raise the budget.

So z15 costs **3.7× the tiles**, two-thirds of the region budget, and a radius ceiling the owner
accepted only as a consequence of the raise.

**And the justification for 15 is the overflow path itself** — the mechanism this audit found
Protomaps discourages. That is the part worth putting in front of the decision: the z15 ceiling rests
on a source that should not be relied on, and dropping to 14 would not only lose detail, it would
recover 3.7× the region budget and let `MAX_RADIUS_KM` go back up. The trade is larger in both
directions than round 2 described.

**On whether z15 carries what a foraging map needs**, the honest answer is that I could not
determine it. Protomaps' layer documentation gives no per-kind road minzooms. It does document one
step-change at that boundary: "z0-14 contains merged buildings, even disconnected ones. z15+
contains individual OSM equivalent buildings" — evidence that z15 is a real data step, not merely
finer rendering, but nothing about paths or trails.

**The owner's trailhead comparison is the right instrument**, and it is cheap: one z14 tile from the
project's own R2 archive against its four z15 children, decoded for layer and kind. Reading the z15
children needs one deliberate request to the daily build, which is a single fetch of the kind the
README's extract workflow already makes, not the sustained per-tile traffic finding #2 is about.
Also worth noting alongside it: `OfflineMapRepository.kt:107-108` already asserts "vector tiles
overzoom cleanly, so a fixed zoom-15 ceiling renders sharp well past it" — the same mechanism a
z14 cap would rely on, already accepted in this codebase for a different ceiling.

---

## 6. Stale project files: one is not stale, and the real one is worse than described

**`CLAUDE.md` in this repository is clean.** One copy exists (`find . -name CLAUDE.md`), and it
contains **zero** occurrences of "retired package root", "forbidden term" or `com.forager.app`. The
09-10 rescission's edit is present here. The stale copy the owner applied the rule from is a local
checkout — the rescission row names two, `forager-app-seed` and `StudioProjects/forager-app`. **The
fix is to pull, not to edit the repository.**

**The Data safety stale claim is real, and it is in the document that already taught this lesson
once.** `2026-09-08-data-inventory-for-privacy-policy.md:396-406`:

> it is collected for **the whole lifetime of an Activity-scoped ViewModel**. Not gated on the Maps
> tab, not gated on a search, not gated on a recording [...] the app holds a 1 Hz two-provider
> location subscription the entire time it is in the foreground.

`5967dd5` falsified the first clause on 2026-09-09 at 21:40, a day after that document was written.
**And its head note still reads "It is not superseded."**

This is the **second** claim in that one document to outlive its mechanism. The `CAMERA` row was the
first, superseded in place on 2026-09-10 with the reason recorded in the index: "that table is an
upstream source the Data safety form is filled from and a claim that outlives its mechanism is
exactly how the CAMERA answer would come back." The same sentence applies unchanged here.

**What changed and what did not, stated precisely so the correction does not overshoot:**

- **Changed:** the subscription is released on `ON_STOP` and re-acquired on `ON_START`. It no longer
  runs for the ViewModel's whole lifetime.
- **Unchanged:** "not gated on the Maps tab, not gated on a search, not gated on a recording" is
  still true while foregrounded. `AvailabilityViewModel.onEnteredForeground`'s own doc comment says
  so: "**Deliberately not need-gating.** This still subscribes on every tab, at the same 1-second
  floor, whether or not anything is consuming fixes."

The owner's summary is exactly right: the subscription now stops in the background but still runs on
every tab while the app is open. A correction that claimed more than that would be its own error.

**Not edited here.** Superseding a claim in that document is the owner's call, it affects a form
already submitted, and this round changed no files outside `docs/audits/`.

## Disclosure

**Confirmed by reading this tree:** `MainActivity.kt:223` attaching to `lifecycle`; no
`ProcessLifecycleOwner` anywhere; the two cited comment blocks verbatim; `patchCameraCaptureLocation`'s
one-shot call and its `?: return@launch`; `AndroidLocationProvider` containing no
`getLastKnownLocation`; `liveFix`'s consumers being display-only; the test-function boundaries at
295/316; no test constructing `MainActivity`; `CartographyScreenTest.kt:125`; `CLAUDE.md`'s zero
hits; the data-inventory lines and its head note; `OfflineMapRepository.kt`'s recorded zoom decision
and its tile figures; `privacy-policy.md:85-86` verbatim.

**Confirmed against an external source:** branch and PR counts via the GitHub API; Protomaps' layer
documentation carrying no per-kind road minzooms.

**Not determined:** whether footpaths and trails first appear at z15 in Protomaps' build, which is
the question the maxzoom fork turns on.

**Deliberately not done:** the revert check in §3, for the reason given there; and no edit to
`2026-09-08-data-inventory-for-privacy-policy.md`, which is the owner's call.
