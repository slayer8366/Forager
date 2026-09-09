# Completion report — path-home monotonicity amendment

**Dispatch:** "Amendment to dispatch 3 — the monotonicity gap, the overclaim, and the outstanding
Filing" (2026-09-08). Amends dispatch 3 of 3, *Build: path home by self-intersection joining*.
**Branch:** `claude/path-home-join`. **Date:** 2026-09-08.

**Status: the build items are done and pushed; the filing is done except the merge, which is
stopped at the owner.** One stop-and-ask is open (B, the merge). Stop-and-ask A was **not**
triggered — the realistic-offset monotonicity test holds at ε = 10 m at all three spacings — but
the measurement it was guarding produced a tighter number than the dispatch's own inference
predicted, and that number is in §2 below.

---

## 0. The state found, which was not the state the dispatch expected

The dispatch expected HEAD at `ef46f1a` with seven modified files, and said to stop and report if
what was found differed. **It differed.** What was actually found:

- HEAD at **`9a1fae7`**, not `ef46f1a`.
- The working tree **clean** of those seven files — no modifications at all.
- The five untracked entries (`.claude/`, the three `*-DISPATCH.md` files,
  `gradle/gradle-daemon-jvm.properties`) present exactly as the dispatch listed them.

The difference was benign and is fully accounted for: `9a1fae7` **is** the dispatch's "commit those
seven files as they stand" step, already performed by a session between the dispatch being written
and this one starting. Its diffstat is exactly the seven paths the dispatch named, at exactly
163 insertions and 6 deletions — the figure the dispatch gave — and its message records that the
untracked files were deliberately left untracked. Nothing was lost and nothing extra was swept in.
No further commit of that work was needed or made.

**What had *not* landed is the push.** `git ls-remote --heads origin claude/path-home-join`
returned nothing: the branch did not exist on the remote, and both `ef46f1a` and `9a1fae7` were
local-only.

**The cause was an environment constraint, not a discipline failure, and the distinction matters.**
The owner confirms the push *was* attempted by the earlier session and could not land because that
session had no remote credentials at the time. This session reproduced the same constraint
independently: the repository's credential helper is `manager` (Git Credential Manager,
GUI-prompting), so in a non-interactive window `git push` blocks on a prompt that cannot be
answered and is killed by timeout — which happened here twice, before the owner granted credentials
access, after which the push succeeded immediately.

An earlier revision of this section wrote the gap up as though `9a1fae7`'s commit message — which
says the work was being committed "so the only copy of that work is on a remote" — had been an
unchecked claim, and pointed at CLAUDE.md's first-listed pitfall. **That was wrong and is
withdrawn.** It inferred a failure mode from an outcome without knowing the cause, which is itself
the error this project's standing notes warn about. Nothing here is a new instance of the named
family in `CLAUDE.md`; that count stands at four and this report does not add to it.

**What does survive is a verification note, and it is smaller.** A push can fail without the
session being able to tell you it failed. So **remote state is established with `git ls-remote`,
never inferred from local history or from what a commit message says was intended.** That is the
only reason the gap was found here.

**The branch is now on the remote**, and every commit described in this report is pushed.

---

## 1. A monotonicity test that actually exercises ε

**The gap, confirmed as the dispatch described it.** Both pre-existing monotonicity tests build
the return leg from the outbound points verbatim — `TrackSelfJoinTest.kt:144`'s
`d + d.dropLast(1).reversed()` and `PathHomeTest`'s returning latitudes being the outbound
latitudes. Exact duplicates join at any ε including zero, so both pass at ε = 0 and neither is
evidence that ε does anything. Neither was deleted or weakened; both bite on the defect dispatch 3
named and both still pass.

**Built:** `TrackSelfJoinTest.a return leg beside the outbound one falls at every point at 10 m and
rises at every point at 0 m`, on a new `returnBeside(spacing, lateral, halfPhase)` fixture — 60
outbound points due north, then 59 returning points on a leg `lateral` metres to one side, **half a
spacing out of phase** with the outbound ones so the nearest stored point is
`hypot(lateral, spacing/2)` away rather than `lateral`. That is the worst along-track alignment, not
the flattering one. The return leg stops short of the origin by construction: a walker's last fix is
not the car, so the way home ends at a few metres rather than at zero.

**Spacings, and the inference behind them.** `HIGH_ACCURACY` gates on 5 s *and* 5 m
(`TrackRecordingMode.kt:27`, enforced at `LocationSampler.kt:41`), so realised spacing is
`max(5 m, speed × 5 s)`. **That is an inference from the two thresholds, not a measurement** — no
track recorded at a known pace was available to this session, and it is carried into the test's own
doc comment marked as inference. On it, the three fixtured spacings are **5 m** (1 m/s and below,
the distance floor governing — where the one real track available, 733 m over 135 points at
~5.4 m, sits), **7 m** (1.4 m/s, ordinary walking, the 5 s interval governing) and **10 m** (2 m/s,
brisk).

**Both sides of ε are pinned, as the dispatch asked, and the pair is the evidence.** At ε = 0 the
test asserts the fixture does *not* collapse: `joinEdgeCount` is 0, the way home equals the full
leg sum, and it **rises** at every returning point. At ε = `SELF_JOIN_EPSILON_METERS` it asserts the
way home **falls** at every returning point and that the route crosses a join at each. Passing at
all three spacings.

The ε = 0 full-leg-sum figure is checked against `retrace()`, the reference haversine already in
that test file, to 1e-6 — independent of the production scan. That the two agree to a micron also
confirms `GeoDistance.metersBetween` and the test's own `haversine` are the same formula on the
same radius, which was verified by reading both.

**No revert check was used**, per the dispatch. A parameter the function already exposes carries
none of the stale-artifact hazard CLAUDE.md records twice for that tool.

### The offset that was reached for: 3.0 m, and the ε = 0 side bit at it immediately

The dispatch asked for the offset that had to be reached before the ε = 0 side genuinely bit, and
warned that a fixture still landing within rounding of the outbound points would need widening.
**The answer is 3.0 m, the first value tried, and it is worth being precise about why that is not
the impressive result it might look like.** At ε = 0 the production scan joins only *exact*
duplicates (`MIN_CELL_METERS`'s own comment). A 3 m offset at 45° N is ~3.8e-5 degrees of
longitude, enormous next to double precision, so **any** true offset breaks the duplicate — the
ε = 0 side would have bitten at 1 cm just as well. It bites for a structural reason, not because
3.0 m is far enough.

3.0 m was chosen as the *realistic* figure rather than the minimum sufficient one: it is of the
order of the only GPS accuracy number this project has, the constant 3.79 m placeholder on the one
device measured (recorded at `SELF_JOIN_EPSILON_METERS`'s own doc, where it is also flagged as a
placeholder rather than a measurement of real fix noise). No widening was needed and the ε = 0
assertion was not weakened, loosened or deleted.

---

## 2. The lateral-offset measurement (item 1b) — the deliverable, as numbers

Printed by `TrackSelfJoinTest.measured lateral offset at which the join stops firing at 10 m -
printed for the report`, read from that test's stdout in the JUnit XML. Bisected to 5 mm; both ends
of every bracket are asserted, so each figure is a checked value rather than the last thing a loop
happened to hold.

**Three criteria, because they give three different numbers**, and the dispatch's phrase "the
offset at which the join stops firing" turned out to name the loosest of them:

- **`join`** — every returning point still has some non-consecutive outbound point within ε. The
  literal reading, and the nearest-stored-point bound. Measured with the test file's own haversine,
  independently of the production scan.
- **`after`** — the way home falls at every returning point *after* the first: the steady state.
- **`turn`** — the way home falls at every returning point *including* the first: the turnaround,
  the moment the walker turns round and the number must start coming down.

| spacing | pace | phasing | `join` | `after` | `turn` |
|---|---|---|---|---|---|
| 5 m | ≤1 m/s, distance floor | worst | 9.68 m | 9.68 m | **6.61 m** |
| 5 m | | best | 8.66 m | 10.00 m | none |
| 7 m | 1.4 m/s walking | worst | 9.37 m | 9.37 m | **6.06 m** |
| 7 m | | best | 7.14 m | 10.00 m | none |
| 10 m | 2 m/s brisk | worst | 8.66 m | 8.66 m | **8.66 m** |
| 10 m | | best | none | 10.00 m | none |

"worst" is half a spacing out of phase; "best" is in phase. Real walking falls between them and the
worst column is the one a safety input should be read against.

**Every figure was checked against independent closed-form geometry and all agree.** Worst-phase
`join` is `sqrt(ε² − (spacing/2)²)` — 9.682, 9.367, 8.660. Best-phase `join` is
`sqrt(ε² − spacing²)` — 8.66, 7.14, 0. Worst-phase `turn` is set by the *best available* join at the
turnaround, which is not always the nearest one: at 7 m it is `sqrt(49 − 12.25) = 6.06` and at 5 m
it is `sqrt(100 − 56.25) = 6.61` (via the outbound point 7.5 m back, not the one 2.5 m back).

### The headline number, and what it means

**The binding figure across the shipping mode's plausible spacings is 6.06 m**, at 7 m spacing —
ordinary walking pace. Above that lateral offset, a returning walker's estimate does not start
falling when they turn round.

Two things about it are worth the owner's attention:

1. **The binding constraint is the turnaround, not the steady state.** In the worst-phased rows
   `after` and `join` are identical, and both are ~1.5 × larger than `turn`. The estimate's
   difficulty is concentrated at exactly the moment it matters most — the walker turning for home
   and looking at the number to decide whether they have time.
2. **The dispatch's own framing would have given the looser number.** Read as "the offset at which
   the join stops firing", the answer is 8.66–9.68 m and looks comfortable against a 3 m realistic
   offset. Read as the property that actually matters, it is 6.06 m. Both are reported above rather
   than only the flattering one.

**Is this alarming?** Not on its face, and stop-and-ask A is therefore not raised: at the 3.0 m
realistic offset there is roughly a 2× margin at the worst spacing. But the margin is smaller than
"ε = 10 m against a 3 m offset" suggests, and this is now a measurement rather than the planner's
inference from two enum thresholds. **No production change follows from it in this amendment**, per
the dispatch — measurement only. It is the number the deferred point-to-segment dispatch turns on.

**Caveat on one cell.** Best-phased `join` at 10 m spacing reads `none`, and that is a knife-edge
rather than an absence: the value is `sqrt(ε² − spacing²) = 0` exactly, so whether it joins at zero
offset is decided by the last bit of the haversine. It is reported as `none` rather than as a
plausible number, and the distinction is recorded in the test's doc comment so the table is not
misread. The other `none`s are genuine absences (see next).

**One geometric fact the sweep surfaced, worth recording.** `turn` is `none` for the entire
best-phased column at every spacing — not a defect. In phase, the first returning fix sits at the
*same along-track position* as the last outbound one, so the walker has made no progress home and
no offset, not even zero, can make the estimate fall there. This is correct behaviour, and it is
why the worst (half-)phase is the fixture the monotonicity test uses.

---

## 3. The `never` overclaim (item 2)

`TrackSelfJoin.kt`'s Cost paragraph said the 1 % cell slack means the projection "can never drop a
pair the exact haversine test would accept". Softened to state the condition it actually holds
under: the projection is local equirectangular **about the track's first point**, so its error
grows with distance from that point, and the 1 % holds over roughly tens of kilometres of latitude
span at mid-latitudes — which covers any walk this app records, which is the claim. The comment now
says so, says the claim is conditional, and says explicitly that an earlier revision said "can
never" and that this was not true of an arbitrary span.

Also recorded there, as the dispatch asked: **nothing tests the condition.** The grid-versus-naive
property test runs in a 150 m box, so it pins the two scans against each other but cannot exercise
the drift the claim is about. No behaviour changed; this is a comment-only edit to a doc comment.

---

## 4. Filing

**Base branch.** `claude/path-home-join` is cut from **`cb16932`** (PR #77) — `main` *before*
dispatch 1's PR #79. Verified this session: `git merge-base HEAD origin/main` is `cb16932`, and the
branch is **3 ahead, 16 behind** `origin/main` (`699efa3`). Everything `origin/main` gained since
`cb16932` is `CLAUDE.md`, `docs/audits/`, `docs/beta/` — nine files, 1,513 insertions, **no
`app/src` file at all**. Zero overlap against the code this branch touches. **Consequence for code
is nil**, reported as the dispatch asked. PR #80 remains unmerged.

**The branch is building against the pre-#79 `CLAUDE.md`**, confirmed by grep: the string
"who calls this" appears 0 times in this branch's `CLAUDE.md` and once in `origin/main`'s. So this
branch lacks the standing reachability note — the note this amendment exists because the planner
ignored. Nothing in this amendment depended on it, but the disclosure is made.

**Line-number spot-check, re-run at HEAD** (the pre-build report's numbers were read at `c914a30`,
several merges back). Every line number the dispatch cited was checked and **all still hold**:

| Claim | Location | Result |
|---|---|---|
| Sole production `startRecording`, `HIGH_ACCURACY` explicit | `MainActivity.kt:415` | confirmed; it is the only call |
| The deliberate-override comment | `MainActivity.kt:408-414` | confirmed, verbatim |
| `EXTRA_MODE = active.mode.name` | `MainActivity.kt:278` | confirmed |
| `?: TrackRecordingMode.BALANCED` fallback | `TrackRecordingService.kt:81` | confirmed |
| Mode thresholds | `TrackRecordingMode.kt:27-29` | confirmed (5 s/5 m, 15 s/15 m, 60 s/30 m) |
| Distance gate enforced | `LocationSampler.kt:41` | confirmed |
| The `never` overclaim | `TrackSelfJoin.kt:57-59` | confirmed (now edited) |
| `MIN_CELL_METERS` comment | `TrackSelfJoin.kt:164` | confirmed |
| Return leg built verbatim | `TrackSelfJoinTest.kt:144` | confirmed |
| No recording-mode picker | 9 files name `TrackRecordingMode`, none a settings surface | confirmed |
| `returnWalkingTime` has no caller | `app/src/main` | confirmed — only its own definition and doc references |

`returnWalkingTime` was left with no caller, per the dispatch's out-of-scope list.

**Full suite, before and after.** Both run on this machine with `./gradlew testDebugUnitTest`.

| | tests | failures | skips |
|---|---|---|---|
| Before (at `9a1fae7`) | 1293 | 10 | 24 |
| After (at `d45dc28`) | 1295 | 10 | 24 |

**Skip count 24 in both directions — the allowlist's identity set, reported and never adjusted.**

**Ten pre-existing failures, present *before* any change in this amendment and untouched.** They
are reported here rather than fixed or silenced, per the dispatch and CLAUDE.md:

- Nine Room migration tests — `CartographyEntryMigrationTest`, `DayScopedIndexMigrationTest`,
  `MushroomLogDraftMigrationTest`, `MushroomLogEntryMigrationTest`, `OfflineRegionMigrationTest`,
  `TrackOriginWaypointMigrationTest`, `TrackPointSpeedMigrationTest`, `TrackWaypointMigrationTest`,
  `WaypointDesignationMigrationTest` — all failing with
  `android.database.sqlite.SQLiteCantOpenDatabaseException`.
- `AvailabilityScreenSettingsPanelTest > tapping a track's share action starts a real ACTION_SEND
  chooser for a GPX file` — `java.lang.IllegalArgumentException at FileProvider.java:911`.

**These were not diagnosed** — doing so was not in this amendment's scope and the failures are
unrelated to the files it touches. The shape of all ten (a database file that cannot be opened, a
`FileProvider` rejecting a path) is consistent with a local environment or temp-directory problem
on this Windows machine rather than a code defect, but **that is a guess and is stated as one.**
The recent completion reports in this directory record 0 failures on comparable suites, which
supports the environment reading without establishing it. Nothing was `@Ignore`d, skipped, weakened
or added to any allowlist.

**`docs/audits/README.md`.** A row for this report is appended. Every existing row is kept.

---

## 5. Stop-and-ask

**A — not triggered.** The realistic-offset monotonicity test holds at ε = 10 m at all three
spacings, turnaround included. ε was not raised and `SELF_JOIN_EPSILON_METERS` was not touched. The
numbers that would have triggered it are in §2, including the tighter-than-expected 6.06 m.

**B — OPEN, and this build stops here.** The branch is 16 behind `origin/main`, and appending the
`docs/audits/README.md` row creates the conflict the dispatch predicted: `origin/main` has added
three rows to that file since `cb16932` and this branch has now added one. **Merging `origin/main`
into `claude/path-home-join` is the owner's decision and was not performed.** The correct
resolution is known — merge, never rebase, keep every row — but knowing it is not permission. A PR
will be opened and **not merged**; it will show the `docs/audits/README.md` conflict, which is
expected and is what stop-and-ask B is about.

---

## 6. The two items for the owner — position only, nothing acted on

**Path home only shows while returning.** `TrackRecordingViewModel.updatePathHome` computes `null`
unless `state.isReturning`. **My position: the narrowing is probably wrong, and the reason is the
one the function's own doc gives.** The straight-line distance to the vehicle is shown regardless
of direction. So while outbound, the *only* home figure the walker can see is the confidently-short
one — which is precisely the failure mode `pathHome`'s doc says the function exists to avoid, and
`ComputeReturnToStartUseCase`'s number is short by exactly the amount the track figure corrects.
Outbound is also when the decision the number informs actually gets made: whether to push another
200 m in with 40 minutes of light left. Once you are already returning, the decision is behind you.
Against that: the HUD line is scarce, and un-narrowing it makes the poll cost (next item) run for
the whole trip rather than the return leg only — the two items interact and are probably one
decision. Not acted on.

**The computation runs on the main dispatcher**, inside `beginPolling`'s `viewModelScope.launch`.
**My position: agreed, this wants a device measurement before beta, not after.** This session
re-ran the cost test on this machine and measured a **14.53 ms median at 2,880 points** (0.17 ms at
135, 2.01 ms at 1,000), close to the 19 ms the dispatch quoted and on the same kind of desktop JVM.
The concern is unchanged: a mid-range Android device is commonly several times slower on
scalar-heavy work like this, so a figure in the low hundreds of milliseconds on the main thread
every 15 s is plausible and would be a visible stall. Deferring the background-dispatcher move
until measured is defensible under "no speculative optimization" — but the measurement is the cheap
part and it has not been taken. If the previous item is decided the other way (compute while
outbound too), this stops being a return-leg cost and becomes a whole-trip one. Not acted on.

---

## Required disclosure

### Confirmed vs inferred

**Confirmed this session, by reading the repository or running the code:** the found-state
divergence in §0 and that `9a1fae7` is the dispatch's own commit step (diffstat matched, seven
paths, 163/6); that the branch had never been pushed (`git ls-remote` empty) and now is; every row
of the line-number spot-check in §4; the base being `cb16932` and the 3-ahead/16-behind position;
the zero code overlap in what `origin/main` gained since; that this branch's `CLAUDE.md` lacks the
"who calls this" note; that both pre-existing monotonicity tests pass at ε = 0 for the
exact-duplicate reason; every number in the §2 table, each also checked against independent
closed-form geometry; the before-suite totals and the identity of the ten pre-existing failures;
that `GeoDistance.metersBetween` and the test file's `haversine` are the same formula on the same
radius; that `returnWalkingTime` still has no caller.

**Inferred, and marked as inference where used:** that realised `HIGH_ACCURACY` spacing is
`max(5 m, speed × 5 s)` — this follows from the two thresholds but was not measured on a real track
at a known pace, and the choice of 5/7/10 m fixtures rests on it. Also inferred: that a device is
several times slower than this desktop JVM on the join computation.

**Guessed, and stated as a guess:** that the ten pre-existing test failures are a local
environment problem rather than a code defect.

### Could not determine

- **The device cost of the computation.** No device was available; the 14.53 ms figure is a desktop
  JVM one and is not a substitute.
- **The cause of the ten pre-existing failures.** Not investigated — out of scope, and CLAUDE.md is
  explicit that diagnosing is the job only when dispatched and that touching them is not.
- **Whether the 6.06 m turnaround figure is comfortable in the field.** It is a geometry
  measurement on a synthetic fixture; what real lateral offset a walker's return leg actually shows
  under canopy is unmeasured, and the project's only accuracy number is a device placeholder.
- **Whether `BALANCED`/`BATTERY_SAVER` are meant to become reachable.** Unchanged from the
  dispatch: nothing in the code says.

### Premises in the dispatch that were wrong

- **The expected working state.** The dispatch expected `ef46f1a` plus seven dirty files; the commit
  had already been made as `9a1fae7`. Benign, and reported before anything was committed (§0).
- **"`ef46f1a` is unpushed"** understated it: the *branch* was unpushed, and so was `9a1fae7`.
  Not a fault in the dispatch's reasoning — the earlier session had attempted the push, and the
  dispatch could not have known it failed for want of credentials. But it shows the general point
  below: the one step the dispatch treated as costless was the one blocked by a capability the
  window did not have.

- **A dispatch should state what capabilities it assumes, rather than treating any step as a
  formality.** This session's capabilities *changed mid-run*: it began with no remote credentials
  (two `git push` attempts died on the GUI credential prompt) and gained them partway through when
  the owner granted access. `gh` auth did **not** arrive with them, so `git push` works and
  `gh pr create` still does not — which is why the PR at the end of the filing could not be opened.
  Repo access arriving mid-session does not imply the rest. A future dispatch that ends in "push"
  or "open a PR" should say what it assumes is available, so a window that lacks it reports the gap
  at the start instead of at the end.
- **"the lateral offset at which the join stops firing"** names the loosest of three distinct
  criteria. Answering it literally would have reported 8.66–9.68 m and missed the 6.06 m figure that
  is the one the point-to-segment decision should turn on. Reported both rather than only one.

### Decided beyond scope

- **Reporting three criteria and two phasings instead of one number per spacing.** The dispatch
  asked for "spacing, failing offset". The single number was ambiguous once measured, and the
  ambiguity was worth more than the tidiness.
- **Reporting `none` rather than a number** for cells where the property never holds, and
  distinguishing the one knife-edge cell from the genuine absences in the test's doc comment.
- **Not diagnosing the ten pre-existing failures**, on CLAUDE.md's rule that an unrelated failing
  test gets reported and not touched.
- **Choosing 3.0 m as the realistic offset** rather than searching for the minimum that makes the
  ε = 0 side bite. §1 explains why the minimum would have been meaninglessly small.
