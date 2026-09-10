# Findings: the `JournalTabTest` photo-pull flake, origin traced in the archive

**Date:** 2026-09-10. **Dispatch:** "flake origin: read the archive before spending runs."
**Scope:** a reading pass over the retired history. **No CI runs were spent.** No test, fixture or
build file was changed. The archive was not modified.

**Headline.** The origin does not predate observation. The failing assertion did not exist before
`0e2198b`, 2026-08-25 01:37:21 UTC, so no earlier commit can have introduced this flake. The first
written sighting is 2026-08-26 16:05 UTC, about 38 hours later. Inside that bound there is a
mechanism that explains every recorded property of the flake, and it has never been written down in
this repository before.

---

## 0. How the archive was read, and what was actually read

The preservation set is on this machine at `C:\Users\metal\forager-preservation-2026-09-09`, not on
`E:`. The `README.md` there is the `E:` README, so its two restore warnings (`safe.directory`
entries, the MSYS `/e/...` path form) describe a drive this pass never touched. Neither applied.

Nothing was run against the archive. The mirror's pack, index, `packed-refs` and `HEAD` were copied
out to scratch and a fresh bare repository was assembled from them. Every command below ran against
that copy. `--prune` was never run, no clone was attempted, no shell ever had the archive mounted.

The reconstruction was verified before anything was concluded from it:

| Check | Expected (archive README) | Measured |
|---|---|---|
| `refs/heads/main` | `443f1fa` | `443f1fad37398e252cd5af04cdeba8cfa734524c` |
| commits | 649 | 649 |
| refs | 138 | 138 |
| `refs/pull/*/head` | 93 | 93 |

`pull-requests/ALL-PR-BODIES.md` and `all-prs.json` were read as well. The whole project is 25 days
long: first commit 2026-08-15, last 2026-09-09.

---

## 1. Every sighting found, with date and source

Times are UTC. "Recorded" means written down in a commit message, a PR body or a doc in the
archive. CI logs died with the repository, so nothing here comes from a run log.

| # | When | Where recorded | What it says |
|---|---|---|---|
| 1 | **2026-08-26 16:05** | commit `6d99a98` message, "Night mode: remove dimming outright" | "one unrelated pre-existing `JournalTabTest` flake reproduced once, green on rerun". **Earliest written record found.** Local run. |
| 2 | 2026-08-26, evening | PR #44 body, "Test plan" | "One earlier CI run in this window showed a single `JournalTabTest` failure alongside a passing `assembleDebug`", confirmed from the CI job logs. Window is `866afa2`..`26f4d89`, all on 2026-08-26 between 18:17 and 19:44. |
| 3 | 2026-08-26 18:55 | commit `2876df0` message | "732/733 green, the one failure being the pre-existing `JournalTabTest` flake already confirmed unrelated to this diff earlier in this session." |
| 4 | 2026-08-26, CI run 195 on `fea6f6c` | merge commit `3783275` (PR #42) message; repeated in `docs/qc/pulses/reports/2026-08-26-repo-state-pulse-response.md:17` | "732 tests across 104 suites"; attempt 1 showed a single `JournalTabTest` failure, attempt 2 clean. |
| 5 | 2026-08-29 | `DISPATCH-REPORT.md:10-14`, branch `claude/new-session-vue2za`; PR #52 body; `docs/audits/2026-08-29-pr52-update-report.md:48` | 762 tests, 761 passing. Names the mechanism class: "a leftover async job from an earlier test bleeding into the next test's Robolectric sandbox". |
| 6 | 2026-08-30 / 08-31 | `docs/audits/2026-08-31-session-handoff.md:50-51` and `:104-106`, commit `ac1b474` | Failing at `JournalTabTest.kt:293` on `0ca4eee`, reproducibly across two pushes' CI runs, passing alone. Includes a **CI-history bisect**: green at `113dfaa` and `3df717b`, red from `df03932`. |
| 7 | 2026-09-01 | commit `2d184e0` message | "intermittent `JournalTabTest` failure surfaced once across several runs". |
| 8 | 2026-09-04 | commit `9f906f2` message | Flake named, no connection to the diff. |
| 9 | 2026-09-06 | commit `72e04cf` message; PR #67 body | One run hit it, 1122 tests, reported not touched. |
| 10-13 | 2026-09-09 | `docs/audits/2026-09-09-journaltabtest-photo-pull-flake.md` | The four sightings that note already establishes, on four trees. |
| 14 | 2026-09-09 | same note, first amendment | `main`'s own tip red at `4236e6b`, CI run `34326689055`. |
| 15-16 | 2026-09-09 | preregistration `§K`, PR #89 and #90 | Arm A 4 reds in 65; arm B 1 red in 65 with the stop-action test body inert. |

Recorded non-firings are also evidence and were counted where the archive states them: PR #53, #58,
#68, #69, #75, #76, #80, #86, #88, #92 each record a full suite in which the flake did not fire.
The existing note's tally already carries the CI denominator, so it is not re-derived here.

**Nothing earlier than 2026-08-26 16:05 exists.** Checked two ways: every commit message across all
138 refs (grep for `JournalTabTest` and separately for `flak`), and every distinct markdown blob in
the object store (506 of them) plus all 93 PR bodies. The earliest markdown record of the failure is
`DISPATCH-REPORT.md`, added 2026-08-29.

**Three "pre-existing" claims are unsupported.** Sightings 1, 3 and 5 each call the flake
pre-existing. No record predates sighting 1. The word appears to mean "not caused by my diff", and
read literally it has inflated the flake's apparent age in every summary since.

---

## 2. The origin bound: the assertion is younger than the repository

The failing test method and the string it asserts on were both introduced by a single commit.

```
0e2198b  2026-08-25 01:37:21 +0000  Workstream G3: pull-into-entry and gallery deletion
```

Confirmed two ways. `git log -S'pulls the selected photo into the entry'` across all refs returns
`0e2198b` as the only commit that adds it. Independently, all 19 distinct blobs of
`JournalTabTest.kt` in the object store were read: 12 contain the test, 7 do not, and the earliest
commit containing any of the 12 is `0e2198b`. The same commit is the only one that ever adds
`Log photo` to that file.

As introduced, the test's last two lines were already what fails today:

```kotlin
// Back on the edit form (not stuck in the picker), now showing the pulled-in photo.
composeRule.onNodeWithText("From Album").assertIsDisplayed()
composeRule.onNodeWithContentDescription("Log photo").assertIsDisplayed()   // <- JournalTabTest.kt:374
```

Line 374 at `main` is that second assertion. It has never had a wait of any kind.

**So the search window for the origin is 2026-08-25 01:37 to 2026-08-26 16:05.** About 38 hours.
Section 2 of the dispatch was right to distrust the first-red date, and wrong about which direction
the error ran: the risk was never an origin lost behind sparse observation. The test is newer than
most of the repository.

---

## 3. The candidate, with its mechanism

**Candidate: `DecodedPhoto`'s asynchronous decode, `bcb9d0b` (Workstream G2, 2026-08-25 01:10),
against the unguarded assertion at `JournalTabTest.kt:374`, `0e2198b` (Workstream G3, 27 minutes
later).**

`DecodedPhoto` is the single decode-and-render composable that every photo thumbnail delegates to,
including both the picker tile (`PullPhotoPickerScreen.kt:107`) and the edit form's thumbnail
(`LogEntryDetailScreen.kt:286`). Both use its default `contentDescription = "Log photo"`. It works
like this:

```kotlin
var bitmap by remember(relativePath) { mutableStateOf<ImageBitmap?>(null) }
LaunchedEffect(relativePath) {
    bitmap = withContext(Dispatchers.IO) { ... BitmapFactory.decodeFile(...) ... }
}
val loaded = bitmap
if (loaded != null) {
    Image(bitmap = loaded, contentDescription = contentDescription, ...)   // the "Log photo" node
} else {
    Box(modifier = modifier.background(...))                                // no content description
}
```

The node the assertion looks for is published **only after a `Dispatchers.IO` round trip
completes**. Until then the composable renders a placeholder box carrying no content description at
all. `assertIsDisplayed()` waits for Compose idleness, and Compose idleness does not include an
in-flight coroutine on a shared background pool. There is no idling resource registered here.

The asymmetry inside the test is the tell. The earlier lookup of the same content description, at
line 361, is wrapped in `waitUntil(timeoutMillis = 5_000)`. The one that fails, at line 374, is not.
Clicking the picker tile navigates back to the edit form, which composes a **new** `DecodedPhoto`
whose `remember(relativePath)` starts empty, so the decode runs again from scratch, and this time
nothing waits for it.

That makes the failure a race between one `Dispatchers.IO` hop and one recomposition. Anything that
delays the pool delays the node.

**Why this explains what every other hypothesis has to explain away:**

| Recorded observation | This mechanism |
|---|---|
| Fails only in a full suite, 14/14 alone | A freshly forked JVM has an idle pool. The decode returns immediately. |
| Always the same assertion, never the `waitUntil` above it | Only line 374 is unguarded. |
| "Costs exactly one recomposition" | It is exactly one recomposition: the one that publishes the decoded bitmap. |
| Class order identical in a red run and a green run | The variable is pool latency, not order. |
| `drainBeforeTeardown()` measurably helped, and the flake continued | The drain empties one leaker. Any other pool occupant does the same job. |
| Arm B: the flake fires with `#82`'s test body inert | No specific predecessor is necessary. The mechanism needs a busy pool, not a particular one. |
| 166 suites still candidates | The mechanism says most of them qualify, which is why per-predecessor bisection has not converged. |

The leaking half of the mechanism was demonstrated and written down in this repository **before the
test existed**: `TrackRecordingServiceTest`'s class doc, added `7de6a5c`, 2026-08-22, records a
coroutine on `Dispatchers.Default` that "went on to throw against a torn-down `Context` deep inside
the *next* test class's Robolectric sandbox, failing an unrelated test". `drainBeforeTeardown()`'s
own KDoc calls it "a shared JVM pool that outlives this sandbox". `Dispatchers.IO` and
`Dispatchers.Default` are views over the same scheduler.

**And the immediate predecessor has driven that same decode path since the first sighting.**
Reconstructed execution order at three points:

| Tree | Date | Position | Immediate predecessor |
|---|---|---|---|
| `6d99a98` | 2026-08-26, first sighting | 81 of 104 | `DecodedPhotoTest` |
| `0ca4eee` | 2026-08-30 | 90 of 115 | `DecodedPhotoTest` |
| `main` `443f1fa` | 2026-09-09 | 140 of 167 | `FindsGalleryScreenTest`, with `DecodedPhotoTest` at 138 |

`FindsGalleryScreenTest` drives `FindsGalleryScreen.kt:218`, which is also a `DecodedPhoto` call
site. The class immediately before `JournalTabTest` has exercised `DecodedPhoto`'s
`Dispatchers.IO` decode on every tree where this flake has ever been seen.

**This mechanism appears nowhere in the archive.** `DecodedPhoto` is named once in
`2026-09-09-journaltabtest-photo-pull-flake.md`, as a row in the execution-order table. No flake
document mentions `Dispatchers.IO`, `LaunchedEffect`, or the decode at all, and no PR body mentions
`DecodedPhoto`.

### Secondary candidates, weaker, listed so they are not lost

- **`bcb9d0b` (G2) as the change that made the pattern racy.** G2 consolidated three hand-rolled
  thumbnail decoders into one. Whether the three predecessors were synchronous was not established:
  the test did not exist before G3, so there is no before-and-after to compare on this assertion.
  Recorded as untested, not as ruled out.
- **`df03932` (2026-08-30), the window the 08-31 handoff bisected to.** It re-`@Ignore`d three tests,
  which changes suite composition. It cannot be the origin: sightings 1 through 5 all precede it. It
  remains a plausible **rate** change, and the handoff's own bisect is real evidence that something
  about that commit made the flake reproducible on that branch across two pushes.
- **The 12 suites that landed before `JournalTabTest` in execution order between 2026-08-22 and
  2026-08-26.** Three of them landed inside the 38-hour window: `MushroomLogDraftMigrationTest`
  (`f824e4c`), `CivilTwilightTest` (`02aafdf`), `MapNightModeTest` (`0983745`). Named for
  completeness. Arm B already showed no single predecessor is necessary, so this list is low value.

---

## 4. The dispatch's two questions

### "Was the suite ever meaningfully smaller?"

Yes, and it does not help. The suite was **104 suites / 732 tests** at the first sighting and is
**167 / 1309** now. The flake has fired across that whole range. There is no size threshold to find.

There is a composition jump right at the boundary, worth stating plainly: 83 suites on 2026-08-22,
97 on 2026-08-25 (PR #39, which is the merge that first carried the new test to `main`), 104 on
2026-08-26. Twenty-one suites in four days. But the first sighting is on a **local** run at
`6d99a98`, and the mechanism above does not need a threshold, only a busy pool.

### "Did `JournalTabTest`'s position change over the period?"

Yes, continuously, and this is the part that should change where the runs go.

| Date | Commit | Suites | `JournalTabTest` position |
|---|---|---|---|
| 2026-08-25 01:37 | `0e2198b` (test born) | 96 | 78 |
| 2026-08-26 16:05 | `6d99a98` (first sighting) | 104 | 81 |
| 2026-08-31 | `62a49c1` | 115 | 90 |
| 2026-09-06 | `b79a3a6` | 146 | 122 |
| 2026-09-07 | `cb16932` | 166 | 139 |
| 2026-09-09 | `443f1fa` (`main`) | 167 | 140 |

The position has drifted monotonically from 78 to 140 and it reached the 139-140 neighbourhood only
on **2026-09-07**, twelve days after the first sighting. The position has been stable for three days,
not for the flake's lifetime.

**This is an argument against the position-140 predecessor bisection.** Its premise is that the
classes now at 136-139 are implicated. They were not there for most of the flake's life. Nearly the
whole predecessor set has turned over while the flake persisted, which is the same conclusion arm B
reached from a different direction.

### How the position series was reconstructed, and why it can be trusted

Test classes are enumerated from the tree in path order, counting every non-abstract top-level class
that either carries `@RunWith` or contains `@Test`. The subtlety that makes it correct is
`AvailabilityScreenLayoutTest.kt`, which holds three concrete `@RunWith` classes extending one
abstract base that owns all 18 `@Test` methods. Counting `@Test` alone loses two suites.

Validated against three independent measurements recorded in the archive, none of which were used to
build it:

| Archive record | Its figure | Reconstruction |
|---|---|---|
| Preregistration `§K`, arms A and B, from JUnit XML | 167 suites, `JournalTabTest` at 140 | 167, 140 |
| Merge `3783275` on `fea6f6c`, CI run 195 | "732 tests across **104 suites**" | 104 |
| Flake note's order table, sighting 4 | `CartographyViewModelTest`, `DecodedPhotoTest`, `FindsGalleryScreenTest`, `JournalTabTest` consecutive; `TrackRecordingServiceTest` 38 classes and ~91s earlier | Same sequence; `TrackRecordingServiceTest` 38 positions earlier |

One discrepancy inside the archive, not in the reconstruction: sighting 4 records "139th of 167" and
"`TrackRecordingServiceTest` at position 101", while arms A and B record "position 140 of 167" on
comparable trees. Both of sighting 4's numbers are exactly one lower and its neighbour sequence is
identical, so this reads as an indexing slip in one document rather than a real difference. The
preregistration's 140 is the figure to carry forward.

---

## 5. What the runs should buy now

Section 3c of the dispatch asked whether reading narrows the field enough to justify a run-based
comparison. It does, and the first two steps need no CI at all.

1. **Pair `DecodedPhotoTest` with `JournalTabTest` and run the pair repeatedly, locally.** This is
   the flake note's own step 2 with a named pair, and the pair is the one that has been adjacent
   since the first sighting. Cost: zero CI budget. A reproducing pair converts this from a flake
   into a bug.
2. **Probe the assertion directly.** Insert a temporary log or a counter at `DecodedPhoto`'s decode
   completion and check, on a red run, whether the bitmap arrives after the assertion. This
   discriminates the mechanism rather than the ordering, and it is one full-suite run, not forty.
3. **Only then, if a pre-registered comparison is still wanted**, size it off the lower bound of the
   rate as section 5 of the dispatch requires. At 7.6 % with 6 in 79, the interval's lower end is
   what the arm size must be derived from.

A fourth option is the owner's to make, not a session's. Line 374 could take the same
`waitUntil(5_000)` that line 361 already has. That is a synchronization fix rather than a weakening:
the photo would still have to appear, and a genuinely broken photo-pull would still fail after five
seconds. What it would give up is the test's current, accidental sensitivity to a slow decode. It is
not in scope here and it should not be done quietly, per `CLAUDE.md` and the flake note's own
section on silencing.

---

## 6. Confirmed, inferred, and not determined

**Confirmed, from the archive, each checked two ways:**

- The failing test method and its `Log photo` assertion were introduced by `0e2198b`, 2026-08-25
  01:37:21 UTC, and by no earlier commit.
- The earliest written sighting is `6d99a98`, 2026-08-26 16:05:51 UTC.
- `JournalTabTest.kt:374` is the second of two bare `assertIsDisplayed()` calls, with no wait, while
  the earlier lookup of the same content description has a 5-second `waitUntil`.
- `DecodedPhoto` publishes the `Log photo` node only after `withContext(Dispatchers.IO)` returns,
  and renders a description-less placeholder before that.
- Robolectric has been pinned at **4.16.1** since 2026-08-16 and was never changed. Nine distinct
  `libs.versions.toml` blobs, one value.
- **`forkEvery` and `maxParallelForks` have never appeared in any build file.** Every blob in the
  object store was searched, not just the build files: the only hits are Kotlin comments and audit
  documents.
- There is no `robolectric.properties`, no custom test runner, and no shared test-infrastructure
  file anywhere in the history.
- Suite size and `JournalTabTest`'s position at every first-parent commit on `main`, validated
  against three independent archive measurements.

**Inferred, and labelled as such:**

- That the mechanism is the decode race. Every recorded observation fits it and none contradicts it,
  but nothing here executes a test. It is the best-supported hypothesis on the evidence, not a
  proven cause.
- That `Dispatchers.IO` contention specifically is the delay. `Dispatchers.IO` and
  `Dispatchers.Default` share a scheduler and this repository has measured cross-sandbox leakage onto
  `Dispatchers.Default` twice, but the coupling was not measured here.
- That `df03932` changed the rate rather than the origin. The origin part is confirmed by the earlier
  sightings; the rate part is the handoff's claim, not re-derived.

**Could not determine:**

- Whether the three thumbnail decoders that `bcb9d0b` replaced were synchronous. The test did not
  exist before G3, so the comparison has no fixed point.
- What the CI runs between 2026-08-25 01:37 and 2026-08-26 16:05 actually did. Run logs died with
  the repository, and the ref set carries no artifacts. This is the one place where "a commit nobody
  ran enough times" cannot be ruled out, and it is a 38-hour window rather than the whole history.
- Whether the flake ever fired between the test's introduction and the first record.

**Premises that were wrong:**

- *Dispatch, section 1:* "First **recorded** sighting: 2026-08-27." The first recorded sighting is
  2026-08-26 16:05 UTC, in `6d99a98`'s commit message. 2026-08-27 is the date of the pulse response
  that relays the CI run, not the date of the sighting it relays.
- *Dispatch, section 2:* "an origin that may predate observation entirely." It cannot. The assertion
  is 38 hours older than the first record.
- *Dispatch, section 4:* "If it moved into the 139-140 neighbourhood around the first sighting, its
  new predecessors are the target." It moved into that neighbourhood on 2026-09-07, twelve days
  after. Its predecessors have turned over almost completely since the first sighting.
- *Archive, three places:* "pre-existing flake" at `6d99a98`, `2876df0` and `DISPATCH-REPORT.md`.
  Nothing precedes them.
- *Archive, `2026-08-31-session-handoff.md`:* the bisect to `df03932` reads as an origin and is cited
  that way in PR #57's body ("bisected to a commit well before this branch existed"). Four sightings
  precede `df03932`.

**Decided beyond scope, and left alone:** no test, fixture, build file or CI configuration was
touched. No `@Ignore`, no allowlist entry, no weakened assertion, no retry. The `waitUntil` option in
section 5 is written down as an owner's decision and was not taken.

---

## 7. Archive integrity

**The archive was not modified, and `--prune` was not run against it.** No git command of any kind
ran against `C:\Users\metal\forager-preservation-2026-09-09`. The device shell could not mount the
folder at all, so the only operations performed on it were directory listings and file reads. The
pack, index, `packed-refs` and `HEAD` were copied into a scratch container, a fresh bare repository
was assembled there, and every command in this report ran against that copy. `mirror-2` was not
touched.

---

## Amendment, 2026-09-10: two premises in section 5 were wrong, and the probe is now pre-registered

Section 5 recommended pairing `DecodedPhotoTest` with `JournalTabTest` locally and called it free.
That advice stands, with two corrections that change how it must be run and how its result must be
read. Both corrections, and the pre-registered meaning of a null, are in
[`2026-09-10-journaltabtest-decode-race-probe-preregistration.md`](2026-09-10-journaltabtest-decode-race-probe-preregistration.md).

**1. The flake has been observed off CI, five times.** Sightings 1, 3 and 5 in section 1's table,
and two of the eleven runs behind sighting 10-13, are session-container `./gradlew` runs, not GitHub
Actions. The stop-action report says it in its own words: "No CI run. All figures here come from
this container's `./gradlew --no-daemon testDebugUnitTest`." The existing pre-registration's claim
that "every sighting, all of them, is CI" is false, and "local" in every tally in this repository
means the owner's Windows host, where the flake has genuinely never fired (0 in 12).

This makes the pair probe a valid instrument, but only in a Linux container. On Windows it is not a
measurement.

**2. A local null licenses nothing below about 45 runs.** At the corrected lower-bound rate, a
five-run null is what a live flake looks like 78% of the time. The full table is section E of the
pre-registration.

**3. Section 5's ordering is superseded.** The first probe should be the deterministic one:
instrument `DecodedPhoto`'s decode completion against the assertion, or force contention on
`Dispatchers.IO` and see whether `:374` fails while line 361's `waitUntil` still passes. That probe
involves no rate and therefore no power calculation, which is the only way out of the problem that
defeated the last comparison.

**Separately, an input error found while checking this.** The dispatch's "Wilson 95% on the earlier
6-in-79 was 2.4%-14.8%" is the interval for 4/65, arm A alone. For 6/79 it is 3.5%-15.6%; for the
dispatch's own 8/86 floor it is 4.8%-17.3%. Sizing off 2.4% roughly doubles every derived n. The
earlier pre-registration's §L.2 power table carries the same wrong input.
