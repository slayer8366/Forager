# The drag test: finding stands, disposition is "run it against a known negative first"

**Date:** 2026-09-12
**Base: `f7c9f15`**, verified against the remote.
**Type:** owner ruling recorded, with the precedents it rests on checked against the tree. No code
changed.

## The ruling

The finding in `2026-09-12-recheck-against-f7c9f15.md` stands: `a9e8a4f` is a routing claim
verified against bytecode and never exercised by a coordinate touch. `performClick` cannot reach the
intercept flag, and a "Simulate pan" button tests the button.

**Disposition (owner): do not ship the test yet.** The sequence is: write the coordinate-drag test,
run it against the code with the fix reverted, and see it fail. If it fails for the right reason,
keep it. If it passes on reverted code, delete it and record that the environment cannot see this
either. Nobody knows which way it goes until it is run against a known negative.

The repo's rule cuts both ways here — "any layout composed over a map needs a coordinate-touch test"
against "a check that passes identically before and after is suspect" — and the revert run is the
only thing that decides between them.

## Both precedents are on the record, verbatim

**The earlier session declined explicitly.** `a9e8a4f`'s own commit message:

> Not verified by a test, and not testable here: the gesture routing itself. No test in the repo
> composes the real SightingsMap — all 17 map-bearing test files pass a stub MapSlot — so the
> AndroidView/MapLibre interop this fix operates on is unreachable from the suite by construction.
> Per the dispatch, no test was written that would pass either way. The owner's device is the
> authority.

**The tab-wrap test passed on defective code, and was removed for it.** `a188d57`'s commit message:

> passed identically on the unmodified, defective code — Robolectric's text-layout measurement in
> this project's config reports implausible glyph widths for these [...] so nothing here can ever
> be measured as wrapping regardless of the fix. Removed that test rather than ship a check that
> cannot fail either way

Same surface, same class of reason, three days apart. That is the whole case for running the
negative rather than assuming.

## One figure that does not reproduce, and one that does

"All 17 map-bearing test files pass a stub MapSlot" was checked against `f7c9f15` rather than
carried:

| Definition | Command (against `origin/main`, `app/src/test/**`) | Count |
|---|---|---|
| Test files referencing `MapSlot` or `SightingsMap` | `git grep -l 'MapSlot\|SightingsMap'` | **23** |
| Test files composing the real `SightingsMap(` | `git grep -l 'SightingsMap('` | **0** |
| Test files matching a stub/fake map-slot pattern | `git grep -il 'stub.*MapSlot\|MapSlot.*stub\|fakeMapSlot\|stubMapSlot\|mapSlot = {'` | **18** |

Neither of my definitions yields 17. **The load-bearing half — zero tests compose the real map — holds
exactly.** The integer is recorded here with its commands so the next reader knows which definition
they are getting rather than inheriting a bare number; it changes nothing about the conclusion, and
my patterns may not be the ones the earlier session used.

## The experiment cannot run in this container

JDK 21 is present. `ANDROID_HOME` and `ANDROID_SDK_ROOT` are unset, there is no `local.properties`,
and no SDK directory exists under the usual paths. Robolectric needs the Android Gradle plugin to
configure and the `android-all` jar to run; without an SDK the build does not reach the test task.
**This goes to a session with an SDK** — the owner's host or CI.

## Protocol for whoever runs it

Written out because `CLAUDE.md` records this project's revert runner producing false confirmations
twice, in two different ways.

1. **Write the test** as a real drag: `performTouchInput { down(mapCentre); moveBy(0f, -200f); up() }`
   on the picker map inside `OfflineMapsPanel`'s `verticalScroll` Column, asserting the panel's
   scroll offset is unchanged. Not `swipeUp()` alone — a drag, with the down inside the map's bounds.
   Sample more than one start point across the map's own bounds; a finger is not a point.
2. **Save a copy of `SightingsMap.kt` before editing it.** Restore from that copy, never from
   `git checkout --`.
3. **Revert the fix** by removing the `setOnTouchListener` block (or forcing it to return without
   calling `requestDisallowInterceptTouchEvent`). A one-line change that compiles.
4. **Run the affected class. Read the build log for compile errors before reading any result.** A
   JUnit XML left over from a previous run reads as a confirmation.
5. **Read the failure message and ask whether this revert could have produced it.** The right
   failure is "panel scrolled / map did not receive the drag." Any other failure is a stale run or
   a different edit.
6. **Restore from the saved copy, re-run, and confirm the forward change is still present** with
   `git diff` before citing anything.
7. **Outcome A — fails on reverted code for the right reason:** keep the test. Robolectric can see
   this path, and the earlier session's "unreachable by construction" was about stubbing, which the
   new test bypasses by composing the real map.
   **Outcome B — passes on reverted code:** delete the test and record it, exactly as `a188d57`
   did. The environment cannot see this either, and the owner's device stays the authority.

## What else this recheck re-derived

Two figures the owner named as worth the discipline even though nothing depended on them:
MapLibre-coupled LOC corrected 2,737 → 2,808 because it had been quoted as exact; the process-death
gap and the attribution handling both re-derived on `f7c9f15` rather than carried, and both stand.

## Disclosure

**Verified on `f7c9f15` / this tree:** both commit messages quoted; the three counts and their
commands; JDK version; absence of SDK env vars, `local.properties`, and SDK directories.

**Not determined:** which definition produces 17; Robolectric's fidelity for the disallow-intercept
path — which is precisely what the protocol exists to determine.

## Addendum (owner, 2026-09-12): the second count no definition reproduces

"17" is the second figure in this project that matches no definition anyone can construct. The
first was **376** — the Data safety draft's "376-artifact classpath," which resolved to 287 distinct
`group:artifact:version` pre-resolution or 157 distinct modules post-resolution, with neither
reproducing the quoted figure (`docs/audits/README.md`, 2026-09-10 row, "a count that no plausible
definition reproduces"). Same repair both times: the load-bearing conclusion re-derived and found to
hold, the figure recorded with its commands rather than as an integer.

The general form, in the owner's words: **a number carried without its definition reads as a
measurement whether or not it ever was one.**

**Where the revert experiment runs.** `.github/workflows/ci.yml` runs `testDebugUnitTest` on pushes
to `main` and on pull requests (deliberately with no base-branch filter, per its own comment about
stacked PRs). A bare branch push does not trigger it. So from a container with no SDK, the
experiment can reach CI only through a PR; absent an instruction to open one, it runs on the owner's
host. Outcome B — the test passing on reverted code — is worth writing down even though it keeps
nothing: it would be the third recorded blind spot on this one surface.

## Addendum 2 (2026-09-12): the CI route does not exist under the constraints

The owner authorised a CI run with four constraints, recorded verbatim so they travel with the
experiment wherever it runs:

1. **Name it for what it is** — e.g. `claude/experiment-revert-drag-fix-do-not-merge`. The name is
   the guard; it is what a future session sees in a branch list.
2. **The revert is the only change on it.** Branch off the current head of the fix branch, revert,
   push. No test edits, no docs, nothing else — otherwise a failure cannot be attributed.
3. **No PR, ever.** A PR is what turns a branch into something that looks mergeable.
4. **Delete the branch after the run** and record the outcome in this document rather than leaving
   the branch as the artifact. This project has already spent a day on branches nobody could
   account for.

Main is untouched and the AAB builds from main, so there is no path from the experiment to a
shipped artifact.

**Read in full, `.github/workflows/ci.yml`'s trigger block is:**

```yaml
on:
  push:
    branches: [main]
  pull_request:
```

No `workflow_dispatch`. A bare branch push does not run CI; only a push to `main` or a pull request
does. **Constraint 3 therefore closes the CI route from a container with no SDK**, exactly as the
owner anticipated: "If it's PR-only, this route doesn't exist without opening one, and then it goes
to the host instead." No branch was created. **The experiment runs on the owner's host**, under the
same four constraints and the seven-step protocol above.

**Side finding from the same read.** The fix branch `claude/new-session-pd5wfd` has a head,
`7aef89a` ("Audit index: a truncated search result and an empty one are the same text"), that is
**not contained in `main`** (`git merge-base --is-ancestor` → no). PR #98 merged an earlier point of
that branch; at least one commit landed on it afterward. Reported, not acted on — it is the class of
thing constraint 4 exists for, and whether it is unmerged work or a stray belongs to whoever owns
that branch. If the experiment branches "off the current head of the fix branch" as constraint 2
says, it inherits that commit; branching off `main` at `f7c9f15` avoids that and still carries the
fix.

## Addendum 3 (owner, 2026-09-12): run on the host; what outcome B would count

**Decision: the experiment runs on the owner's host.** A PR whose purpose is to carry a deliberate
defect through CI creates something that looks mergeable. The host has the SDK, the experiment is
self-contained, and nothing needs to leave the machine.

**Outcome B, underlined.** If the test passes on reverted code it keeps nothing, but it would be the
**third recorded blind spot on one surface**: text metrics (`a188d57`, tab wrap), gesture routing
(`a9e8a4f`, declined as unreachable), and whatever this one turns out to be. One is an environment
quirk. Three is a property of the surface, and the count is what eventually justifies a different
kind of test rather than another attempt at the same one. Instrumented tests on a device would be
that different kind; they are a post-beta conversation.

## Where this work actually lives

The branch is `claude/ios-port-feasibility-mvsjcr`, and only the first document on it is an iOS
port report. The name is a session artifact and will make a later reader hunt, so, derived from
`git diff --name-only origin/main...HEAD` at the time of writing, the branch carries these audit
documents and nothing else outside `docs/audits/`:

- `2026-09-08-data-inventory-for-privacy-policy.md` — **not authored here**; on the branch only because §4 was superseded in place (owner-authorised, round 4)
- `2026-09-11-ios-port-feasibility-report.md`
- `2026-09-11-lifecycle-gate-and-corrections-round-3.md`
- `2026-09-11-lifecycle-gate-and-corrections-round-4.md`
- `2026-09-11-maplibre-pmtiles-policy-compliance.md`
- `2026-09-11-maplibre-pmtiles-policy-corrections-round-2.md`
- `2026-09-11-maplibre-pmtiles-policy-corrections.md`
- `2026-09-12-drag-test-disposition.md`
- `2026-09-12-recheck-against-f7c9f15.md`
- `2026-09-12-round-4-re-derived-against-5515adc.md`

The thread runs: iOS feasibility → MapLibre/PMTiles policy audit → three rounds of owner
corrections → the lifecycle gate and the pocket question → re-derivation after a stale base →
recheck after #97/#98 → this drag-test disposition. The index rows in `README.md` carry the
findings; these files carry the evidence.

## Addendum 4 (2026-09-12): constraint 2 revised, and `7aef89a` identified

**Constraint 2 is revised by the owner:** branch the experiment off **`main` at `f7c9f15`**, not off
the fix branch's head. Main carries the fix and not the trailing commits, so the revert measures
exactly one change.

**`7aef89a` is two commits, not one, and neither is app code.** `git log origin/main..
origin/claude/new-session-pd5wfd`, after deepening the fetch:

| Commit | 2026-09-12 UTC | What | Files |
|---|---|---|---|
| `e8661ed` | 12:27 | New `CLAUDE.md` Known-pitfalls entry, "a planner's picture of the repository decays," plus its index row | `CLAUDE.md` +60, `docs/audits/README.md` +1 |
| `7aef89a` | 12:41 | Second index row, "a truncated search result and an empty one are the same text" — a detector failure (`grep -r ... \| head -5` cutting the output where the answer was), given its own row on the owner's call | `docs/audits/README.md` +1 |

Both say "Documentation only. No code, test or dependency changes." Both are from the session that
wrote `a9e8a4f`. `e8661ed`'s own message: "Branch restarted from main (f7c9f15), since PR #98 is
already in." **It is not the 4:3-on-compact ruling.**

**Was it meant to ship?** By content, yes: a `CLAUDE.md` entry commissioned by the owner, on a
branch restarted for the purpose. **By state, it has not started shipping:** the GitHub API shows
**zero open pull requests in the repository**, none with this head. So it is exactly the shape the
owner named — work reachable from no merge, alive only because a branch still exists — and it is
now identified rather than mysterious, which is the cheap half. Opening the PR is the owner's or that
session's; not done here.

**It will collide with this branch.** `git merge-tree --write-tree HEAD origin/claude/new-session-pd5wfd`
(tree untouched) reports `CONFLICT (content): Merge conflict in docs/audits/README.md` — the
serialization point again, both branches appending index rows. Whichever lands second resolves by
merge and **keeps every row**; there is no case in which dropping one is right.

One thing worth knowing before either lands: `e8661ed`'s entry uses the `CivilTwilight` decay as its
worked instance, and `2026-09-12-round-4-re-derived-against-5515adc.md` on this branch records the
same instance from the other side. Two records of one event, citing different reports, is correct;
they should not be merged into one.

**Open actions are now two, unchanged in number:** the host run, and a PR for `e8661ed`/`7aef89a`
or a decision that they should not ship.

## Addendum 5 (2026-09-12): `e8661ed`/`7aef89a` merged by #99 before a PR was opened here

The owner instructed: open the PR and merge both. Before opening one, the branch state was re-read
against the remote — the rule this thread produced, applied to its own last step — and **`main` had
moved to `5494044`, "Merge pull request #99 from slayer8366/claude/new-session-pd5wfd."** Between
the zero-open-PRs check in Addendum 4 and this one, someone opened and merged #99. Confirmed:

- `git merge-base --is-ancestor e8661ed origin/main` → yes; same for `7aef89a`.
- The branch is now 0 ahead of `main`, 1 behind.
- `origin/main:CLAUDE.md:386` carries the entry: "A planner's picture of the repository is a claim
  about the past..."

**No PR was opened here.** Opening one would have created an empty duplicate. A rule that existed
only on an unmerged branch now exists on `main`, which was the point.

The owner's own note on this belongs in the record: approving a merge and not noticing it never
merged is its own small instance of the thing the entry describes.

**The index conflict landed as designed.** Merging `5494044` into this branch conflicted in
`docs/audits/README.md`: 2 rows from `main`, 9 from this branch, **11 kept, 0 dropped**, 105 rows in
total. The serialization point working, not a problem.

**Registered on its own, as the owner asked:** at the time of Addendum 4 there were zero open pull
requests in the repository. Everything then in flight — the three UI fixes, the two documentation
commits, this disposition work — sat on branches with nothing proposing them. #99 has since closed
one of those; this branch is another.

**Open actions are now one: the host run.**

## Addendum 6 (2026-09-12): the SDK is installed here, and "cannot run in this container" is withdrawn

Owner's instruction: install the SDK. Done, at `/opt/android-sdk` (511M): cmdline-tools build
`16111833` (sdkmanager `1.0.16261425`), `platforms;android-37.0` — Google now publishes platforms
with a minor version, so `compileSdk = 37` resolves to `android-37.0`, not `android-37` —
`build-tools;37.0.0`, `platform-tools`, `android-sdk-license` accepted. `local.properties` points
at it and is ignored at `.gitignore:4`.

**Proof, read in the order the project's rule requires — build log first, JUnit XML second:**

- `./gradlew :app:testDebugUnitTest --tests "*AvailabilityScreenSettingsPanelTest"`, cold:
  `BUILD SUCCESSFUL in 4m 30s`, exit 0.
- Compile errors in the log (`^e: ` / `error:`): **0**.
- `TEST-...AvailabilityScreenSettingsPanelTest.xml`: **21 tests, 0 failures, 0 errors, 0 skipped**,
  24.8 s. That is the class the experiment's scrolling-panel assertion lives in.
- Robolectric's `android-all-instrumented` jar (`16-robolectric-13921718-i7`) was fetched through
  the proxy, so the harness the experiment needs is present.

So the Addendum 2 statement "the experiment cannot run in this container" is **withdrawn** — an
absence claim made false by a later action, recorded rather than left standing. The four
constraints and the seven-step protocol are unchanged; only the location moves. The CI-branch
constraints (name it, revert-only, no PR, delete after) were for a branch; run locally there is no
branch, and the "revert is the only change" rule becomes "the test file and the one-line revert are
the only changes, and the revert is restored from a saved copy, never from git."

## Addendum 7 (2026-09-12): probe 1 — the real `MapView` cannot construct here, observed

Two probes were written as untracked test files under `app/src/test/.../ui/map/`, never committed,
to be deleted once recorded. This addendum records the first; the second is still running.

**Shape.** `MapLibreInitProbeTest`, `@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [36])`
(without that annotation the runner refuses to start on `targetSdkVersion=37 > maxSdkVersion=36` —
the first run died there with `initializationError` before any body ran, a non-result recorded as
one). One test: `MapLibre.getInstance(app)` then `MapView(app)` inside a `try`, `fail()`ing with the
exception's class and message so the JUnit XML carries the cause.

**Result, read after confirming zero compile errors in the build log:**

```
java.lang.UnsatisfiedLinkError: 'void org.maplibre.android.net.NativeConnectivityListener.initialize()'
```

`MapLibre.getInstance()`'s first JNI call. The cached `android-sdk-13.5.0.aar` ships
`jni/{arm64-v8a,armeabi-v7a,x86,x86_64}/` — all Android ABIs against bionic, none loadable by a
desktop JVM. **So the picker test as originally described — composing the real `SightingsMapSlot`
and dragging it — cannot run under Robolectric, and the reason is now an observed error rather
than a claim.**

**This confirms something the tree already said**, which the probe should be read as verifying, not
discovering: `CentrePinLocationPickerTest`'s class doc — "`[SightingsMap]` itself can't be composed
under Robolectric — see `[SightingsMapOverlayDataTest]`'s own doc comment for why (native MapLibre
calls)." The earlier session's "unreachable by construction" was this, with the mechanism unnamed.

**What it does not settle:** whether Robolectric can observe the *mechanism* the fix relies on —
`requestDisallowInterceptTouchEvent` propagating from an `AndroidView`-hosted `View` to a
`verticalScroll` ancestor — which needs no MapLibre at all. That is probe 2, with a built-in control.
Its first attempt was also a non-result (`Unable to resolve activity for Intent {... ComponentActivity}`:
the Compose host activity was never registered with Robolectric's `PackageManager`, which every
Compose test in this suite does through an `ExternalResource` chained ahead of the rule). Fixed to
match, re-running.

Probe file deleted after this record; not committed at any point.

## Addendum 8 (2026-09-12): probe 2 — outcome B, structural, with the mechanism named

**This closes the disposition.** The experiment ran here after the SDK install. The result fell
outside both pre-registered outcomes, and that is stated before the reading rather than after.

### Pre-registered reading, as written before any run

- **A:** control (no listener) scrolls; guarded (listener) holds at zero → Robolectric can see the
  mechanism; keep the test.
- **B:** control does **not** scroll → the environment is blind; delete and record.

### What was observed

Four runs. Each read in the order the project requires — build log for compile errors first,
JUnit XML second — and each non-result recorded as one:

| Run | State | Result | Reading |
|---|---|---|---|
| 1 | no `@Config` | `initializationError`: `targetSdkVersion=37 > maxSdkVersion=36` | non-result; the runner refused to start. Every Robolectric class in the suite (64) carries `@Config(sdk = [36])` |
| 2 | `@Config(sdk = [36])` | `Unable to resolve activity ... ComponentActivity` | non-result; the Compose host activity was never registered with Robolectric's `PackageManager` (`CentrePinLocationPickerTest` does it through an `ExternalResource` chained ahead of the rule) |
| 3 | + host activity registered | control **passed** (scrolled); guarded **failed**: `expected 0 but was 3800` | both states scrolled — neither A nor B |
| 4 | + instrumentation | control `scroll=3800 max=3800 downs=1 moves=0`; guarded `scroll=3800 max=3800 downs=1 moves=0` | **identical in both states** |

**The numbers, as the owner asked, not the pass/fail:**

- Both states scrolled to **3800 px**, which is exactly the column's maximum extent:
  (200 + 300 + 2000 − 600) dp × 2 at xhdpi. Three drags of ~300 px of finger travel each cannot
  produce 3800 px of scroll without a fling; the value does not follow from the input. It is the
  same in both states, so it does not discriminate, but it is the implausibility check the
  tab-wrap case taught, written down.
- **`downs=1 moves=0`, in both states.** Across three injected drags, the hosted `View` received
  exactly one `ACTION_DOWN` and zero `ACTION_MOVE`s.

### Mechanism

The listener **did** run in the guarded state (`downs=1`), so `requestDisallowInterceptTouchEvent(true)`
**was** called on the parent — and the Compose `verticalScroll` ancestor consumed every subsequent
move regardless (`moves=0` at the view; `scroll=3800` at the column). Under Robolectric, a drag
injected with `performTouchInput` on an `AndroidView` inside `verticalScroll` delivers one DOWN to
the hosted view and nothing after it, and the disallow request changes nothing. **The fix's presence
is unobservable in this harness.** That is outcome B in substance: the environment cannot see this
class of defect. It is B with a different symptom from the one pre-registered — the control *does*
scroll — and the widening is recorded here, not assumed.

**Two limits, stacked.** Probe 2 tested the mechanism on a plain `View` carrying a verbatim copy of
`SightingsMap.kt:553-558`, because production's listener sits inside the `AndroidView` factory next
to a `MapView` that cannot construct here (Addendum 7). So even if Compose-interop routing *had*
worked under Robolectric, production's own code path would still be unreachable without a native
host build or an extraction of the listener into a named function. Both walls are structural: the
first is a third-party AAR's native library, which Robolectric shadows nothing of; the second is
the interop dispatch path itself. Nothing in this project's configuration tunes either away.

**The owner's framing, carried verbatim:** the overlay test's doc comment and probe 1 are "two
observations of one boundary from different depths" — `GeoJsonSource` and every `Layer` "call a
`native initialize` from their constructor (verified with `javap`) ... Robolectric shadows the
Android *platform* SDK, not a third-party AAR's native library." Probe 1 hit that boundary one call
earlier, at `MapLibre.getInstance()`'s `NativeConnectivityListener.initialize()`.

### The probe's shape, so it is not written again

`DisallowInterceptProbeTest`: `@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [36],
qualifiers = "w360dp-h640dp-xhdpi")`, `RuleChain.outerRule(declareHostActivity).around(createComposeRule())`.
Composition: `Column(fillMaxWidth, height 600.dp, verticalScroll(rememberScrollState()))` holding a
200 dp spacer, an `AndroidView` (300 dp, `testTag("hosted")`) whose factory returns a plain `View`
with an `OnTouchListener` counting DOWN/MOVE and — in the guarded state only — the verbatim
`SightingsMap.kt:553-558` disallow call, then a 2000 dp spacer. Drive: for x-fractions 0.5, 0.2,
0.8 of the hosted node's width, `performTouchInput { down(x, 0.6·h); moveBy(0, −150); moveBy(0, −150); up() }`
then `waitForIdle()`. Assert: control `scroll > 0`; guarded `scroll == 0`; every message carries
`scroll`, `max`, `downs`, `moves`.

### Three blind spots on one surface, now all named

1. **Text metrics** — `a188d57`: Robolectric's text layout reports implausible glyph widths; the
   tab-wrap test passed identically on defective code and was removed.
2. **MapLibre native** — Addendum 7, probe 1: `UnsatisfiedLinkError` at the first JNI call; the AAR
   ships only Android ABIs.
3. **Compose-interop touch routing** — this addendum, probe 2: one DOWN and no MOVEs reach an
   `AndroidView`-hosted view under a scrolling ancestor, in both states.

One is a quirk. Three is a property of the surface, and the count is what justifies instrumented
tests on a device rather than another attempt at the same kind — a post-beta conversation, as the
owner has said.

### Disposition

**No test is kept.** Both probe files deleted after this record; neither was ever committed. The
owner's device remains the authority for the drag fix, as `a9e8a4f` said. The open action that
survives from this thread is the tile-policy pulse, which has a fourteen-day clock behind it.
