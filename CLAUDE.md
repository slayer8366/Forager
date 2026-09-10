# CLAUDE.md

Standing engineering principles for this repo. Adapted from [Evidence Gated
Development](https://github.com/slayer8366/E-GD-Philosophy), keeping only
the principles that generalize to a small Android app — its hardware-driver
layer, content-addressed data hashing, and multi-agent record-store
ceremony are built for a different kind of project and are not adopted
here.

## Working with ambiguity

- An ambiguous requirement or an unmade architectural decision is a
  stop-and-ask, not a judgment call. Surface the options; don't pick one
  silently and build on it.
- A claim about this codebase names a file and location, or is stated as
  unverified. Don't describe code that wasn't actually read.
- Report what was done, what was verified and how, and what was skipped or
  left unverified. A report that only reassures has failed at its job.

## Bug fixing

- See the failure before writing the fix — reproduce it, confirm it fails
  for the reason expected. A failure that doesn't match the prediction
  means the test/check itself is wrong; fix that first.
- Two failed fix attempts on the same symptom means stop guessing. Next
  step is more data — logs, a minimal repro, instrumentation — not a third
  hypothesis.

## Errors and failure paths

- No silently swallowed exceptions, no default fallback that isn't logged
  when it fires.
- Partial or failed results are reported as such, never presented as
  success.
- An unsupported feature or capability returns an explicit "unsupported,"
  never a fabricated plausible value.

## Architecture

- Keep domain logic (species matching, list management, sighting state,
  etc.) free of Android UI framework bindings, so it's unit-testable
  headless without an Activity/Compose tree in the loop.
- Wrap external integrations — the iNaturalist API, camera, location —
  behind an interface this project owns. Domain logic depends on the
  interface, not the vendor SDK or HTTP client directly.
- A device- or API-reported capability range (camera resolution, GPS
  accuracy, rate limits) describes what's possible, not what's safe to
  use. Apply an explicit operating limit rather than trusting the reported
  range as-is.

## Testing

- Assert on actual output — payloads, types, schema — not proxies like "a
  file got created" or "the string appears somewhere in the output."
- Exercise user-triggered behavior through its real entry point (the
  ViewModel/Composable callback, the actual Intent), not by hand-calling
  an inner method with made-up arguments.
- A check that passes identically before and after a code change is
  suspect — flag it as possibly not covering what it claims to.
- **A reverted-variant check is only evidence if the reverted build actually
  ran.** The way this project proves a new test bites is to revert the
  behaviour by a one-line edit, run the affected classes, and read the JUnit
  XML. That XML is left over from the previous run when the reverted build
  fails to compile — and a one-line revert can fail to compile in ways that
  look nothing like the edit (removing a `null` check drops a smart cast three
  lines down). The runner then reports the previous run's failures as if the
  revert produced them, and a check that never ran reads as confirmed. This
  happened once here (compass-reliability dispatch); it was caught only because
  the "failures" named a case that revert could not have caused, not because
  anything flagged it. So: a revert runner must check the build log for
  compile errors before it reads results, and refuse to cite them if it finds
  any; and when reading a revert's failures, ask whether each one is a failure
  this revert could produce — a failure that belongs to a different edit is a
  stale run, not a confirmation. Every revert check before that fix could in
  principle have passed on stale output; the ones recorded in `docs/audits/`
  each name a message specific to their own edit, which is the only reason
  they can be trusted. The same tool lied a second way the same day (two-data-
  corrections dispatch): it restored the reverted file with `git checkout --
  FILE`, which restores the *committed* version, so when the forward change
  under test was still uncommitted the "restore" silently discarded it — the
  runner reported the revert failing for the right reason, the file was then a
  clean copy of HEAD, and the change the check had just vouched for no longer
  existed. Caught only because the next `git diff --stat` came up a file
  short; nothing in the runner's output said so. So: a revert runner restores
  from a copy it saved before editing, never from git, and after any revert
  check confirm the forward change is still present before citing the result.
  Both failures were found by a discrepancy the runner did not report itself;
  the rule is the same in both — the runner's output is not evidence until the
  state it claims to describe has been checked against something outside it.
- Silencing a test is never in scope for a dispatch that didn't ask for it.
  A test unrelated to the dispatched task that starts failing mid-task gets
  reported, not touched — no `@Ignore`, no widening the CI skip allowlist,
  no disabling, skipping, or weakening an assertion to reach green, even
  when the failure is diagnosed as harness-only and backed by an audit doc.
  Diagnosing the cause is the job; deciding to reduce coverage belongs to
  the owner.
- **A semantic `performClick` asserts wiring, not routing.** It invokes the
  node's own click action directly and bypasses hit-testing entirely, so it
  passes even when another composable covers the control completely. Only a
  real `performTouchInput` at screen coordinates tests what a finger gets.
  The map's icon-bar drag handle covered the locate row's whole centre for
  several dispatches while the one locate test in the class — a semantic
  click — would have passed un-ignored; the same distinction plausibly
  explains the three earlier touch-interception bugs on that surface
  surviving green suites. Where a test's claim is "a touch here reaches this
  control", it must be a coordinate touch. And a finger is not a point:
  sample several real touches across the target's own bounds, not one at its
  centre — the broken region on the icon bar was the one region no test
  touched, because every real-touch test picked a centre point outside it.
- **A stalled test run in this repo is an unstopped poll loop, not a slow
  test, and `runTest` will not rescue it.** `TrackRecordingViewModel` polls
  in an unbounded `delay` loop while recording; a test body that throws
  before stopping the recording leaves that loop scheduled, and `runTest`'s
  closing idle-advance spins through virtual time forever. The natural
  assumption that the framework protects you here is wrong: coroutines-test
  1.11's `runTest` timeout did not fire in 77 minutes of real time on that
  spin. The hang is the failing test's own `runTest`, so an `@After` never
  runs either. `TrackRecordingViewModelTest.runRecordingTest` stops every
  recording inside the body, in a `finally`; any new test in that class
  goes through it, and any new ViewModel with a poll loop needs the same
  shape. Diagnose a stall with a thread dump of the test worker, which
  names the test that actually failed — what is lost is its message.
- **A check that passes because it never saw the data that could fail it.**
  Three instances in this project, and they look nothing alike until named as
  one family. (1) The legacy-miles migration test passed with the migration
  removed, because imperial was also the default: the check saw only the
  answer, never the mechanism, so both branches produced it. (2) The revert
  runner reported the previous run's failures as the revert's, because the
  reverted build had not compiled: the check saw stale results, never the
  build it claimed to describe. (3) The instrument-walk log parser confirmed
  the provider/`hasSpeed` correlation at 289/289 with perfect separation —
  on a sample that had silently dropped all 55 network fixes, because they
  log `speed=null` and the pattern required a number. The disconfirming
  cases were exactly the ones the parser could not read, so the check
  passed on the only sample that could not fail it. The shape is the same
  each time: the check and the thing it is checking are decoupled by a step
  in between (a coincident default, a stale artifact, a lossy filter), and
  nothing in the check's own output says so. What caught all three was the
  same act, a count read against something outside the check — the runner's
  tally against the prediction, `git diff --stat` a file short, 289 fixes
  against 344 lines-worth in the log. So: before citing a check as evidence,
  ask what sample it actually ran on and confirm that sample includes the
  cases that could have failed it — a total that matches the source, a
  failure message specific to this edit, a build log with no compile
  errors. A check whose input you have not verified has not been run yet.
  (4) The same family one level up — a check on a value where the question
  was reachability. Pass 1 of the return-estimate device checks was built to
  tell whether point differencing had ever run as primary on real data, by
  comparing a recomputed track snapshot against the stored one; a session
  went into refining that comparison (which could not discriminate anyway:
  both tracks recompute to their stored values by construction). The cheap
  question closed it in one grep: `returnWalkingTime` has no production
  caller, so the path had never run and could not have, and the check was
  designed to detect something unreachable. The distinguishing feature of
  this instance is that no value could ever have carried the answer — the
  first three checked a value through a lossy step; this one checked a
  value for a fact about the *caller*. So: before designing a check for
  whether a path ran, `git grep` its callers and confirm it is reachable
  from production code. "Who calls this?" is asked before "what did it
  produce?", and a path with no caller has an answer before any data does.
  The general form (owner, 2026-09-08): **check reachability before
  measuring behaviour.** Three of the four instances above would have been
  closed by it — the migration test never reached the migration, the
  revert runner never reached the reverted build, Pass 1 never reached the
  pace — and it costs one grep.
- **The cheap question — who calls this? — closes more of that family than
  any amount of careful measurement downstream.** Three pre-build reports on
  2026-09-07/08 (track distance, path home, GPX full record), written for
  different dispatches by different sessions, each found the same class of
  problem before a line was built: a path with no caller, a check that could
  not fail, a figure readable only by coincidence. What caught all three was
  the report-before-building rule and the four disclosure sections — confirmed
  vs inferred, could not determine, premises that were wrong, decided beyond
  scope — forcing the question "who actually calls this, and what does it
  actually read?" to be answered from the code rather than assumed from the
  dispatch. Those are further instances of the family above, beside the
  reachability check written up as (4) — one of the three was that same
  no-caller finding, seen from its pre-build report — and the pattern across
  all of them is that the check and the thing it checks were decoupled by a
  step nobody had traced. So: a pre-build report traces every
  claimed path to its caller and every claimed figure to its reader, and
  states which are unverified, before it prices anything.

## Building

- New capability is a new function, class, or path — not a conditional
  threaded into existing working code.
- Don't build speculative correction or optimization logic without real
  data showing the case it's meant to handle.
- Pin dependency versions (Gradle version catalog, exact versions) rather
  than open ranges, so a build is reproducible.

## UX defaults

- What the user has set survives navigating away and back within a session —
  a position they dragged something to, a side they snapped it to, a panel
  they collapsed. Remembering it is the default, not a feature to be
  requested; a piece of user-set UI state that resets on a tab change is a
  bug unless the exception was stated explicitly for that case. This replaced
  an earlier working rule that "nothing survives a tab change" for the map's
  icon cluster, which had been taken as a way to avoid hoisting state for a
  behaviour nobody had asked for — the owner's ruling is that convenience is
  the service, and an unrequested reset is not a neutral default. Persisting
  across app restarts is a separate, per-case decision (DataStore, see the
  Room/DataStore pitfall below), not implied by this. First per-case "yes":
  the map's fullscreen mode persists across restarts (reasoning recorded on
  `MapPreferencesRepository.getMapFullscreen`); the cluster's position, side
  and minimised flag deliberately do not. Not an exception to this rule:
  leaving the Maps tab exits fullscreen. The bottom nav is off screen in
  fullscreen, so the tab cannot be left from there at all — the tab
  handler's explicit exit holds an invariant rather than resetting anything
  the user could still be relying on.

## Documentation

- Record why a non-obvious decision was made and what alternative was
  rejected — not just what was chosen.

## Known pitfalls

- **Push before you tidy.** Work that exists only in a local commit or an
  uncommitted working tree is one session loss away from gone. This project
  has already lost a Phase 1 code audit outright — run on another account,
  never committed, never pushed, unrecoverable from any reachable session —
  and separately had a test file appear in a working tree with no known
  author, nearly absorbed into another task's commit before its provenance
  was established. A later Phase 2 working environment was archived with no
  unarchive path and survived only by luck; in that same at-risk session,
  four clean local commits were reset to un-bundle a file for correct
  attribution, briefly leaving the only copy of that work in an uncommitted
  tree, inside an unrecoverable environment, for a cosmetic gain. Commit and
  push at every natural stopping point, including work in progress — a
  rough commit on a working branch beats a clean one that does not exist
  yet. Never reset, rebase, amend, or un-bundle unpushed commits to improve
  history, attribution, or commit boundaries: push first, since history is
  editable forever once it's on a remote but unpushed work is not — if
  attribution needs recording before a push, put it in the commit message
  instead, which costs nothing and risks nothing. This applies to artifacts
  as much as code: an audit, a review, a design decision, or a handoff note
  that lives only in a session transcript is not recorded — `docs/audits/`
  exists for exactly this reason.
- **`docs/audits/README.md` is a serialization point.** Every dispatch appends a
  row to the one index, so two coder sessions running in parallel on one
  branch are guaranteed to conflict there even when nothing else they touch
  overlaps — which is exactly what happened on 2026-09-08, when the beta-signing
  session and the path-home sessions landed within minutes of each other and
  every push after the first was rejected until merged. Running dispatches as
  separate sessions was the right call for keeping one report's premises from
  bleeding into another; it was wrong about the merge cost. Either sequence the
  dispatches that touch the index, or split it so each session appends to its
  own fragment. When the conflict does happen: merge (never rebase — the
  push-before-you-tidy rule above), and **keep every row** — there is no case
  in which dropping an index row is the right resolution.
- **Verify your base branch before you start.** Confirm what your branch is
  cut from and that the base is current before writing code — don't assume
  `main` is up to date. This project has had `main` sit multiple phases
  behind an active branch, and PR #26 and the Phase 1 branch each
  independently declared `ForagerDatabase.version = 5` with a different
  `MIGRATION_4_5` body from the same v4 base (one adding track/waypoint
  tables, one adding offline-region tables) — a collision file-level git
  merges cannot detect, since a schema version, a migration number, a port
  assignment, a feature flag, or any other globally-unique claim can have
  two branches each individually correct, merge without conflict, and still
  produce a broken result. As of this writing that specific collision is
  still unresolved, with 51 commits of drift between the two. Where a
  change asserts a globally-unique value, check it against the base's
  current state, not the state you started from. If a dispatch names a
  branch and the session defaults to a different one, that is a
  stop-and-ask, not something to resolve alone — say which two disagree and
  wait.
- **Room for data that relates; DataStore for flat settings.** The deciding
  question is what the data will be queried for, not what it looks like
  today: if a value will be referenced by, joined to, or filtered against
  another entity, it belongs in Room, with its foreign key and index
  designed at creation rather than retrofitted; if it's a standalone
  preference — a threshold, a toggle, a last-used value — it goes in
  DataStore, where adding a key costs no migration and stays out of the
  migration-number sequence. `OfflineRegionEntity` is a flat list with no
  foreign key and no index today, and would be misfiled as a settings table
  by anyone judging on present structure — it's correctly in Room because
  log entries are intended to reference it, and a table that will acquire
  relationships should be designed relationally from the start. This split
  was reasoned from scratch twice — PR #26 arrived at it independently
  (`OfflineRegionEntity` in Room, `MapPreferencesRepository` in DataStore)
  and a later session re-derived the same line for the permission flow's
  explainer flag — so DataStore usage now follows the pattern
  `DataStoreMapPreferencesRepository` established: namespaced keys, a
  `Result`-returning suspend interface matching this repo's other
  repositories, and a per-instance `PreferenceDataStoreFactory.create`
  rather than the `by preferencesDataStore(name = ...)` singleton delegate,
  which caches per-process and breaks Robolectric isolation across `@Test`
  methods. A column with no reader is not added: every Room column lands with
  at least one read path that uses it, in the same change (practised since
  the first migration, written down with the HUD-foundations dispatch).
  `ForagerDatabase` declares no `@ForeignKey` — links are plain, indexed,
  nullable columns joined in code — but that is a rule for this one database,
  not the repo: `FungiIndexDatabase` does declare one.
- **A `Surface` (or any composable that draws a background or attaches
  pointer input) intercepts touches across its full layout bounds, not just
  where something is visually drawn.** An unconstrained `fillMaxWidth()`/
  `fillMaxSize()` child inside a `Column`/`Row` that has no width or height
  constraint of its own stretches the whole container — and the `Surface`
  wrapping it — to fill the available space, silently swallowing touches
  meant for content underneath (a map's long-press, in particular) even in
  regions that look empty. Bound such a container explicitly — e.g.
  `Modifier.width(IntrinsicSize.Max)` on the parent, so it sizes to its
  content instead of expanding to fill — rather than trusting that nothing
  drawn there means nothing intercepted there. This has recurred twice
  within the same project cycle (the compact map's compass strip, then
  independently the return-to-vehicle row added right after it), both times
  over a map surface the UI is meant to let touches pass through to; both
  times a Robolectric test driving the real screen's long-press caught the
  regression outright (green-to-failing, not flaky) before it reached
  hardware. Any layout composed over a map needs at least one such test, not
  just visual review — visual review is exactly what missed this twice.
- **Robolectric reports zero window insets, so anything depending on real
  ones is device-only by construction — a green suite there proves nothing
  about it.** This app calls `enableEdgeToEdge()`, so on a real device
  `Scaffold`'s own `contentWindowInsets` (default `WindowInsets.safeDrawing`)
  and Material3 `NavigationBar`'s own default `windowInsets`
  (`NavigationBarDefaults.windowInsets`) are both real, non-zero values —
  Robolectric simulates neither, reporting zero for both regardless of
  device config. Two concrete failures from this, confirmed against
  AndroidX's own `Scaffold.kt`/`NavigationBar.kt` source rather than
  assumed: (1) `Scaffold` falls back to `insets.calculateBottomPadding()`
  for its own reported content padding whenever `bottomBar` composes no
  content (`bottomBarHeight?.toDp() ?: insets.calculateBottomPadding()`) —
  real and positive on-device, zero under Robolectric, so content that
  should reach the true screen edge when `bottomBar` is deliberately empty
  won't, on-device, unless `contentWindowInsets` is adjusted to match; (2)
  `NavigationBar`'s own rendered height is its nominal content height *plus*
  the real bottom system-bar inset it self-consumes — a flat constant
  standing in for "this bar's height" will match Robolectric's own
  (inset-free) measurement exactly and silently undershoot the real,
  on-device one. Both produced a bug two device screenshots showed clearly
  while 979 Robolectric tests stayed green (fullscreen-fixes dispatch,
  "still shifting") — the fix in both cases was to depend on a live
  measurement or a real `WindowInsets` query, never a value that merely
  happens to match what Robolectric reports. Where a layout's correctness
  depends on real system-bar insets, say so in the fix and in what gets
  reported back — a passing suite is not evidence there, and treating it as
  such is exactly what let this one ship twice.
- **A derived figure carried across a boundary keeps its authority and
  loses its provenance.** A number, an interval, or a categorical claim is
  correct about the thing it was derived from and stops being correct the
  moment it is quoted about something larger, and nothing in the quoting
  marks the difference: the figure looks exactly as authoritative in its
  second home as in its first. Four instances inside one investigation, all
  found in a single reading pass on 2026-09-10, and none of them careless.
  (1) **"Pre-existing."** The `JournalTabTest` photo-pull flake is called
  pre-existing in `6d99a98`, `2876df0` and `DISPATCH-REPORT.md:11`, and
  nothing precedes the first of the three. The word meant "not caused by my
  diff" and was read as "older than this investigation," which is what made
  the origin look unfindable; the failing assertion turned out to be 38
  hours older than the first record of it, introduced whole by `0e2198b`.
  (2) **"Every sighting is CI."**
  `2026-09-09-journaltabtest-flake-comparison-preregistration.md:37`, built
  by reading a two-column tally (CI, local-Windows) as exhaustive when five
  reds across four sessions were container `./gradlew` runs that the table
  never had a column for. `hostname="vm"` was cited in support, and that
  attribute describes a Gradle worker JVM, not GitHub Actions. The claim
  would have ruled out the cheapest probe available. (3) **A Wilson
  interval.** 2.4% to 14.8% is computed correctly for 4/65 in that
  document's §K and attached to 6/79 in its §L.2, then carried into a
  dispatch. For 6/79 it is 3.5% to 15.6%. Sizing off the wrong lower bound
  roughly doubles every derived n. (4) **A corrected heuristic reappearing
  after its own correction.** §J.5 of that same document identifies
  `n ≈ 9/p` as the crude rule behind its original power error and replaces
  it with an exact binomial; §L.2, four sections later, is `9/p` again
  (118 = 9/0.076, 375 = 9/0.024) relabelled "~80% power," when the exact
  figures are 103 and 328 and the stated n's carry about 89%. **A
  correction does not propagate forward through its own document on its
  own.** The rule: when citing a figure, an interval, or a categorical
  claim from another document, or from another section of the one you are
  writing, either re-derive it or quote its scope alongside it, and say
  which you did. Where the claim is that something never happens, name the
  sample it was checked against; "all of them" is a claim about a table's
  columns unless the columns were confirmed exhaustive. The check that
  catches this is not scepticism, it is counting, and it does work: the
  stop-action session had every licence to dismiss its own two reds and
  wrote instead, "It would have been easy, and wrong, to call this
  pre-existing. What ruled it out was counting"
  (`2026-09-09-recording-notification-stop-action-completion-report.md:217`).
  This is the same failure that produced the 294-artifact count and the
  `verify-policy-permissions.sh` tripwire.
