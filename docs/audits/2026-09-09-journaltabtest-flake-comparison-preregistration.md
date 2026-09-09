# Pre-registration — JournalTabTest flake, the controlled comparison

**Dispatch:** JournalTabTest flake: the controlled comparison (2026-09-09).
**Base:** `main` @ `14cc6f3`, as the dispatch names. Branch `claude/journaltabtest-flake-comparison`.
**Status when written: NO RUN HAS BEEN EXECUTED.** That is the point of the file — §2 requires the
analysis, the run count and the decision rule to be committed before the first suite executes, and
this document's own commit SHA is the checkable evidence that they were.

**Three amendments to the dispatch are recorded here, all before any arm run: §A is a blocker on
the design, §D.3 corrects what §0 and §3 say a null result means, and §D.5 shows the dispatched run
count is calibrated to a rate this project no longer believes.**

**One finding blocks the design as dispatched.** It is §A below. The comparison cannot be run on
this host as written, and the fix is the owner's call. What *is* pre-registered here and starts
immediately is the cheap probe that decides between the two remaining methods.

---

## A. The blocker: the flake has never been observed on this host

The dispatch does not name an environment, and the natural reading — run the 60 suites where the
coder is — cannot work.

`docs/audits/2026-09-09-journaltabtest-photo-pull-flake.md:11-12` says it outright: this flake
"happens **on Linux, in CI-equivalent full-suite runs**, and never on the host-specific list." Its
own draw table is the evidence:

| draw | where | result |
|---|---|---|
| 1 | **local Windows** | pass |
| 2–8 | CI | 2 fail, 5 pass |

**One local draw, and it passed.** Every sighting — all of them — is CI. Every XML in the preserved
red run carries `hostname="vm"`. Adding my own session's local full-suite run of 2026-09-09 makes it
**two local draws, both green**.

Running 60 local Windows suites would therefore be a check on a sample that has never contained a
single instance of the thing being measured. If both arms come back clean, the comparison is not
"null result, #82's test exonerated" — it is *no measurement at all*, and it would be reported as a
null by anyone reading the numbers without this paragraph. That is exactly the family CLAUDE.md
names: **before citing a check as evidence, confirm the sample includes the cases that could have
failed it.** And the cheaper rule that closes it: **check reachability before measuring behaviour.**

### Why CI is not simply the answer either

`.github/workflows/ci.yml` triggers on `push: branches: [main]` and `pull_request` only. **There is
no `workflow_dispatch`**, so a CI run cannot be summoned for a branch. The only ways to obtain one:

1. push to `main` — out of the question;
2. open a **pull request** for the branch, then take repeated draws with `gh run rerun` (this is
   how the existing draw 4, "PR #83 re-run", was obtained);
3. re-run an existing run — which requires a run to exist, i.e. requires (2) first.

So getting CI draws of the **substituted arm** requires opening a PR for it — and §1 says the
substituted arm "is never pushed as a proposed change." That is a direct conflict between §1 and the
only mechanism that can execute §2. **Not resolved unilaterally; see §E.**

Adding `workflow_dispatch:` to `ci.yml` does not escape this: `gh workflow run` requires the
workflow to exist on the **default branch**, so it would have to land on `main` first — a change to
CI configuration, plainly outside "measurement and one document."

Two facts that bound the cost, so the decision can be priced: the repository is **public**, so
Actions minutes and artifact storage are free; and `concurrency.cancel-in-progress` is true for
every ref except `main`, so draws on one ref must be **sequential** (~5.5 min each → ~2 h 45 m per
30-run arm), while two arms on two different refs would run in parallel.

---

## B. §1's inertness question, answered: the placeholder CAN be genuinely inert

The dispatch says to stop if the class's fixture rather than the test body does the interfering
work. Checked at `14cc6f3`, `app/src/test/java/com/forager/app/service/TrackRecordingServiceTest.kt`:

- `@Before setUp()` (`:72-79`) obtains the `Application` context and the container's
  `trackRepository`. It starts no service, launches no coroutine, touches no file. **Inert.**
- **There is no `@After` in the class at all.**
- Every mechanism the class's own doc comment blames — `Dispatchers.Default` work outliving the
  sandbox, `stopSelf()`'s main-thread posts, the `ServiceController` lifecycle — is reached only
  from inside the body of `the ongoing notification's stop action ends the recorded track`
  (`:121-169`), including its closing `drainBeforeTeardown()`.

So replacing that body with a no-op leaves nothing in the class capable of the interference under
study. **The substituted arm is buildable and vouchable.** Proceeding on that basis.

**A material fact §0 does not mention.** `drainBeforeTeardown`'s own KDoc (`:171-182`) records a
*prior controlled comparison*: "without this, a full-suite run failed `JournalTabTest`'s photo-pull
assertion in **2 of 3** runs while the same suite without this test was green in **5 of 5**." That
is the same experiment this dispatch commissions, already run once — on the **undrained** version of
the test. It implicates this test's behaviour, and the drain was the response. The flake then
continued post-drain (the note's fourth sighting fired on a tree that has the drain). So the live
question is narrower than §0 frames it: not "does this test interfere" but **"does the *drained*
test still interfere."** Recorded because a null result means something different against that
history than against none.

---

## C. Tally: the dispatch's figure is already stale, by more than it says

§5a says update 2-in-8 to **2 in 10**. Counting completed CI full-suite runs on trees carrying #82's
test, from the note's own draw 8 forward, by run ID:

| draw | run | ref | result |
|---|---|---|---|
| 9 | `34366721592` | PR #87 head `e0e553c` | pass |
| 10 | `34367340693` | `main` `becb826` | pass |
| 11 | `34368884708` | PR #88 `77afc12` | pass |
| 12 | `34369687261` | PR #88 `deab086` | pass |
| 13 | `34370339757` | PR #88 `f57bbb5` | pass |
| 14 | `34373669264` | PR #88 `0d9fc0d` | pass |
| 15 | `34374667868` | `main` `14cc6f3` (this dispatch's base) | pass |

**2 failures in 15**, not 2 in 10. Five of those seven are PR #88's own runs from the session
immediately before this one — the dispatch was written at base `14cc6f3`, which *is* draw 15, so
the runs existed but had not been counted.

`34368690983` is **excluded**: it was `cancelled` when a later push superseded it. A cancelled run is
not a draw — it neither passed nor failed, and counting it either way would be an invented
observation.

The mapping of draws 1–8 to run IDs is **relayed from the note, not re-derived**; draws 9–15 I
enumerated myself from `gh run list`. This is precisely the "floor, not a total" behaviour the note
predicted of itself, holding a third time.

---

## D. PRE-REGISTERED: the outcome measure, and Phase 0

### D.1 The outcome measure — fixed now, for every phase

A run is **red** iff `app/build/test-results/testDebugUnitTest/TEST-com.forager.app.ui.log.JournalTabTest.xml`
contains a `<failure>` for
`From Album on the edit form opens the picker and pulls the selected photo into the entry`
(the assertion at `JournalTabTest.kt:374`, confirmed at this HEAD to be
`onNodeWithContentDescription("Log photo").assertIsDisplayed()`).

**A run is not red because the suite failed.** On this Windows host every full suite carries the ten
known host-specific failures (nine Room migration, one FileProvider). They are background and are
explicitly *not* the measure. A local run with those ten and no `JournalTabTest` failure is a
**green draw**.

### D.2 Phase 0 — 10 local present-arm runs, to decide whether local measurement is possible at all

Ten consecutive full-suite runs on `14cc6f3`, unmodified, this host, same JDK, same Gradle
invocation, single fork.

**Decision rule, fixed before the first run:**

- **≥ 1 red in 10** → this host can observe the flake. The 30-per-arm comparison runs locally, no
  PR needed, no CI load, and §A's conflict evaporates.
- **0 red in 10** → local observation is **not demonstrated**, which is *not* the same as "this host
  cannot see it." At a 1-in-10 per-run rate, ten runs miss it with probability 0.9¹⁰ ≈ **0.35**; at
  1-in-3, ≈ 0.017. So 0/10 makes a local 1-in-3 rate unlikely and leaves 1-in-10 entirely live. The
  honest conclusion is "insufficient", and the comparison moves to CI — which needs the owner's
  ruling in §E.
- **No optional stopping.** All ten run regardless of what the early ones do.
- **Every run reported with its ordinal and result, greens included.** These ten are also genuine
  present-arm draws and are added to §C's tally either way.
- **Every red run's `test-results/` is preserved before anything else happens**, along with that
  run's class execution order. Three earlier sightings destroyed theirs; the one that preserved it
  produced the only real disconfirmation this investigation has.
- If two reds show different positions for `JournalTabTest`, that is flagged immediately as a
  finding in its own right.

Phase 0 changes no file. It is the present arm by construction — `git status` clean at `14cc6f3`.


### D.3 PRE-REGISTERED: what each outcome means, given the drain history

Owner ruling, 2026-09-09, on reading §B: the dispatch's §0 and §3 are **amended**, and the amendment
is recorded here *before the data* because it changes what a null result means.

**§0 as dispatched is wrong on one point.** It frames the open question as whether #82's test
interferes. That is **answered, and the answer is yes**: `drainBeforeTeardown`'s KDoc records 2 of 3
runs failing with the test present and undrained, against 5 of 5 green without it. The drain was the
response to that finding, it landed, and the flake continued. So the live question is not "does this
test interfere" but:

> **Does the *drained* test still interfere?**

**§3 as dispatched is therefore too weak about the null.** It says a null exonerates one candidate
and leaves the hypothesis untouched. Against the drain history the null says more than that. Fixed
in advance, the three outcomes and their readings:

| outcome | reading, fixed before data |
|---|---|
| **substituted clean, present red** at a separable rate | the drained test **still** interferes. The drain reduced the rate but did not close it. Implicates the test's behaviour — **still not a mechanism**, per the standing line in the Windows note. |
| **both arms red** at similar rates | **the drain worked**, and the flake that remains has a *different* source among the other 166 suites. This is a stronger and more useful result than a bare null: it does not say "the test was never involved" — the undrained comparison already showed it was — it says the fix for that involvement held and the search must move on. |
| **both arms clean** | **no measurement**, not exoneration. Reported as open, with the draws added to the tally. |

The misreading §3 exists to forbid — "so it isn't interference after all" — remains forbidden, and is
now *doubly* wrong: the hypothesis is interference within a fixed order, and the one candidate that
has ever been tested by manipulation was shown to interfere before it was drained.

### D.4 Follow-up, recorded and deliberately not taken

`workflow_dispatch` on `ci.yml` is the right mechanism for this and for every future flake
investigation — arms get runs on demand, with no PR at all. Owner ruling: **right mechanism, wrong
time.** It is a CI configuration change needing its own PR to `main`, outside this dispatch's
"measurement and one document" scope. Recorded here as a follow-up so it is not re-derived from
scratch next time.
### D.5 PRE-REGISTERED: the comparison's decision rule, stated against the narrowed question

Written after §D.3 narrowed the question, and **before the first arm run**, because a decision rule
inherited from the old framing would be testing something nobody is asking any more. The question
this rule answers is **"does the *drained* test still interfere?"** — not "does this test
interfere," which `drainBeforeTeardown`'s KDoc already answered yes.

**The statistic.** A 2×2 table, arm × (red, green), where red is §D.1's measure. Two-sided Fisher
exact, α = 0.05, fixed now. Both arms' full counts are reported whatever the test says.

**The readings**, which are §D.3's table made numeric:

| result | reading |
|---|---|
| present arm's red rate exceeds substituted, Fisher p < 0.05 | the **drained** test still interferes. Behaviour, not mechanism. |
| both arms red, p ≥ 0.05 | **the drain worked**; the remaining flake has another source among the other 166 suites. |
| both arms 0 red | **no measurement**, not exoneration. |
| any other combination, p ≥ 0.05 | **open / underpowered.** A legitimate result, and the dispatch's §2 requires it be reportable as one. |

**No optional stopping.** Every planned run in both arms executes regardless of the running counts.
Every run is reported with its ordinal and result, greens included.

#### The power problem — the dispatch's 30 per arm is calibrated to a rate we no longer believe

§2 sizes the experiment at "1-in-3 expect ~10 failures per arm; 1-in-10 expect ~3." The best current
estimate of the present arm's rate is **§C's 2 in 15 ≈ 0.133**, and at that rate 30 per arm cannot
separate the arms even if the substituted arm is *perfectly* clean.

Fisher two-sided p for r reds all falling in one arm, n = 30 per arm:

| r | p (two-sided) |
|---|---|
| 3 | 0.237 |
| 4 | 0.112 |
| 5 | 0.052 |
| 6 | **0.024** |

So **r ≥ 6 reds** in the present arm is needed to clear α = 0.05. But at p = 0.133 with n = 30 the
expected count is 4.0, and `P(X ≥ 6) ≈ 0.20` for `X ~ Binomial(30, 0.133)`.

> **Power at the dispatch's own n is about 20 %.** Even if the drained test still interferes and
> accounts for the entire flake, roughly four times in five this experiment returns "open."

Reaching ~80 % power needs the expected red count around 8–9, i.e. **n ≈ 65 per arm** (~130 CI runs,
~6 h with the arms in parallel). Intermediate: n = 45 gives ~45 % power.

This is the same family as a check that cannot fail, one level up — **an experiment that cannot
separate.** Sixty CI runs returning a foregone "open" would look like a result and would consume the
appetite for ever running it properly.

**n is therefore the one parameter left open, and it is the owner's call.** Everything else in this
section is fixed. The chosen n is committed here before the first arm run; no arm run happens until
it is.
---

## E. RESOLVED: how the substituted arm gets its CI draws

**Owner ruling, 2026-09-09.** §1's "never pushed as a proposed change" meant *never proposed as the
fix* — the placeholder must not end up looking like a candidate patch. A **draft PR titled as an
experiment arm, never marked ready, closed when the runs are done, is not a proposed change.**

Chosen: **a draft PR per arm.** Two refs means two concurrency groups, so the arms run in parallel
and are genuinely interleaved against the same environment drift — the requirement §2 makes and the
one the owner least wanted to give up. Titles carry `(do not merge)` so nothing in the history reads
as a proposal.

Both arms branch from this document's commit, **not** from `main`. If arm A were `main` itself the
two arms would differ in the test body *and* in carrying a docs delta — two variables again, which
is the exact mistake §1 exists to prevent. Identical base, one file different, suite still 1309.

`workflow_dispatch` on `ci.yml` is recorded as a follow-up and deliberately not taken — see §D.4.

**Still open, and the only thing still open: `n` per arm (§D.5).** No arm run happens before it is
chosen and committed.

### Base drift during the experiment

PR runs build the **merge ref**, so if `main` moves mid-experiment both arms silently change base.
The arms stay comparable to each other, but the experiment stops describing the tree it claims to.
`main` is recorded at `14cc6f3` at the start; it is re-read at the end, and if it moved, the report
says so and states which draws fall on each side.

---

## F. Phase 0 result — 10 local present-arm runs, all green

Executed against `14cc6f3`'s code, pre-registration `0f33c04`, started 16:30:38Z, finished
16:51:00Z. The code under test was unmodified `main`; this branch's only delta is documentation.

| run | JournalTabTest photo-pull | suites | tests | background failures |
|---|---|---|---|---|
| 1 | green | 167 | 1309 | 10 |
| 2 | green | 167 | 1309 | 10 |
| 3 | green | 167 | 1309 | 10 |
| 4 | green | 167 | 1309 | 10 |
| 5 | green | 167 | 1309 | 10 |
| 6 | green | 167 | 1309 | 10 |
| 7 | green | 167 | 1309 | **9** |
| 8 | green | 167 | 1309 | **9** |
| 9 | green | 167 | 1309 | 10 |
| 10 | green | 167 | 1309 | **9** |

**0 reds in 10.** All ten ran; no optional stopping. No artifacts to preserve, since there was no
red. The greens are reported because a tally built only from failures has no denominator.

**Per §D.2's rule, fixed before the data: local observation is NOT demonstrated, and that is not the
same as "this host cannot see it."** Counting every local draw known — the note's draw 1, PR #88's
session run, and these ten — local now stands at **0 in 12**, against CI's **2 in 14**.

And 0-in-12 does *not* establish that the host is immune. At CI's own rate of 2/14 ≈ 0.143, twelve
local runs come back clean with probability 0.857¹² ≈ **0.155** — about one time in six. A Fisher
exact on local-versus-CI (0/12 against 2/14) gives p ≈ 0.56: **the environment difference is not
statistically established either.** It rests on the flake note's direct claim and on every observed
red carrying `hostname="vm"`, not on these counts. Recorded so nobody later cites "0 in 12" as proof
of a host effect.

The comparison moves to CI, as pre-registered.

### An unrelated finding, reported not fixed (§6)

**The "ten known Windows failures" is not a fixed set of ten.** Three of the ten runs produced
**nine**, and not the same nine: runs 7 and 10 saw `TrackWaypointMigrationTest` pass, run 8 saw
`OfflineRegionMigrationTest` pass. So the Room-migration failures are themselves intermittent —
9 or 10 out of a pool of ten, with which one survives varying per run.

That contradicts the standing description of this baseline as a constant set, and it is consistent
with `SQLiteCantOpenDatabaseException` being a filesystem/timing condition rather than a
deterministic one. It does not touch this experiment — the background failures are constant across
*arms*, never enter §D.1's measure, and `JournalTabTest` is not among them — but a local run showing
nine would previously have looked like a change rather than a normal draw. Reported here; not fixed,
not silenced, and no skip-list touched.
