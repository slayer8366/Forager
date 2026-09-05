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
- Silencing a test is never in scope for a dispatch that didn't ask for it.
  A test unrelated to the dispatched task that starts failing mid-task gets
  reported, not touched — no `@Ignore`, no widening the CI skip allowlist,
  no disabling, skipping, or weakening an assertion to reach green, even
  when the failure is diagnosed as harness-only and backed by an audit doc.
  Diagnosing the cause is the job; deciding to reduce coverage belongs to
  the owner.

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
  methods.
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
