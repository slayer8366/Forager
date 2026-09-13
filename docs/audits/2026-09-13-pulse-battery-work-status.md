# Pulse — where the battery work stands

**Date:** 2026-09-13. **Read only; nothing changed but this report.**
**Dispatch:** `pulse-battery-work-status.md` (planner, no repo access).

## §0 — Base, and every remote head

`origin/main` = **`175b050`** ("Merge pull request #100"). Executing branch
`claude/ios-port-feasibility-mvsjcr` @ `1522c41`, touching nothing under `app/`. **All 52
remote branches were fetched (depth 1) and searched**, per the dispatch; every code claim below is
against `origin/main` explicitly.

## §2 — Is there a plan document? No.

**No document on any of the 52 branches is named for, or is about, a battery or location-efficiency
plan.** File names across every ref matching `battery|power|need-?gat|location-eff|efficien` under
`docs/`: **zero**. Content search across every ref for
`battery|duplicate registration|shareIn|need-?gat|wake ?lock|location efficiency|structural change`:
23 documents, 21 on `main`, 2 not (both mine, on this branch, quoting the ViewModel's own comment).
Read for what they say rather than what they match on:

| Document | Branch / on main | What it actually says |
|---|---|---|
| `2026-09-12-recording-state-resync-completion-report.md` §7, "Two platform registrations, and what that does to the battery figure" | `main` | **The source of every §1 fragment, nearly verbatim** — cold `callbackFlow`, no `shareIn`, "every collector registers independently, on GPS and network both", "at least two independent registrations at a one-second floor, **and possibly three with the compass strip's**". Rules that any trip battery figure is "an overestimate of what a fixed app costs, and should be recorded as that". "Routing this ViewModel off the service's fixes is the structural change, post-beta, alongside item 1" (`:140`). |
| `2026-09-07-fix-log-walk-findings.md` §8, "Duplicate listeners quantified" | `main` | From an instrumented walk log: "Every fix logged **three times**, from a single PID … **three listeners, not four**." "Three listeners is three times the callback work and plausibly a real battery cost. It remains queued rather than urgent, and the standing position holds: **measure battery before optimising**." |
| `2026-08-22-error-presentation-handoff.md:78` | `main` | "The location-fix collector in `TrackRecordingService.startRecording()` escapes its intended scope — root cause not yet diagnosed." The oldest sighting of the family; the orphaned-listener half was closed by #96 on 2026-09-12. |
| `2026-09-06-light-budget-pulse.md:109` | `main` | "no `WAKE_LOCK` permission, no wake lock anywhere" (grep over `app/src/main`). |
| `2026-09-12-pr95-pulse-before-it-ships.md:44,178` | `main` | "No battery figure is given. Nobody here can measure one." Per-tick cost "deliberately" unmeasured. |
| 18 others | `main` | Match only on `BATTERY_SAVER` mode tables or the beta report template's "Battery at start/end" fields. Not about this work. |

So "the owner recalls a plan" resolves to **two paragraphs**: a queued item with a measured
multiplier (09-07) and a one-sentence post-beta deferral (09-12). Nothing planned the work; both
say measure first. Reported as absent, not reconstructed.

## §3 — What is actually built, re-derived

**Three collectors of `LocationTracker.fixes` in `app/src/main`, not two** (control: the interface
declaration `domain/LocationTracker.kt:31` appears in the same grep):

| Collector | Bound by | Runs when |
|---|---|---|
| `TrackRecordingService.kt:116` | the foreground service's lifetime (`ACTION_START` → `ACTION_STOP`) | any recording, screen on or off |
| `TrackRecordingViewModel.kt:507` (`beginLocationTracking`) | `locationJob` — started only from `startRecording`; cancelled at `:257` (clear), `:505` (restart), `:697` (`onCleared`); and since #96, `resyncRecordingState` at both lifecycle edges clears it when the row says the track ended | any recording while the ViewModel lives — **not** a foreground gate, by design (`onLeftForeground` doc: gating it off would lose the canopy origin) |
| `AvailabilityViewModel.kt:205` (`collectLiveFixes`) | `liveFixJob` — acquired on `ON_START`, released on `ON_STOP` (`5967dd5`); "deliberately not need-gating … on every tab" | whenever the app is foregrounded, recording or not |

**Concurrent platform registrations, by state:** foregrounded + recording = **3**; backgrounded +
recording = **2**; foregrounded, not recording = **1**; backgrounded, not recording = **0**.

**One registration shape for all three.** `AndroidLocationTracker.kt:35` is a cold `callbackFlow`;
`:71` `requestedProviders = listOf(GPS_PROVIDER, NETWORK_PROVIDER)`; `:74`
`requestLocationUpdates(provider, MIN_UPDATE_INTERVAL_MILLIS, 0f, listener, …)` for each;
`:103` `MIN_UPDATE_INTERVAL_MILLIS = 1_000L`; `:77` `awaitClose { removeUpdates(listener) }`.
Every `collect` runs the builder, so every collector is a fresh registration on **both** providers
at **1 s / 0 m**. `AppContainer.kt:129` hands out one instance, which changes nothing.

**No sharing operator anywhere.** `shareIn|stateIn` in `app/src/main`: **0** files (control:
`kotlinx.coroutines.flow` imported in 13).

**Nothing changed since the resync fix.** `git log 5515adc..175b050` over `location/`, both
ViewModels, `service/`, `AppContainer.kt`: empty.

**The service exposes nothing to consume.** `onBind` returns `null` (`:69`); it is driven by
`ACTION_START`/`ACTION_STOP` intents (`:51`), holds no `StateFlow`/`SharedFlow`, and publishes
fixes only as persisted rows the ViewModel polls at `POLL_INTERVAL_MILLIS = 15_000L` (`:701`).

## §4 — The measurement

**The device figure is recorded nowhere** on any of the 52 branches (grep for `7.9%|3h17m|81,939|S26 Ultra|GPS 3h`
over `docs/**` on every ref: zero; control: "build 628" appears in 7 documents on `main`). It exists
only in a chat transcript. **Recorded here, with every qualifier, as the before-measurement:**

> **2026-09-12, build 628, Galaxy S26 Ultra.** 7.9% of the day's battery, fourth behind Facebook
> 15.0%, Claude 10.7%, USAA DriveSafe 8.7%. 3 h 47 m total app time: 54 m screen on, 2 h 52 m
> background. **GPS 3 h 17 m**, wake locks 19 m, CPU 26 m, 25 wake-ups. 81,939 mobile-data
> packets **including a one-time offline region download** — without that qualifier the figure
> reads as a leak. Conditions: duplicate registration present (three collectors, per §3);
> four-hour continuous recording; mixed screen use.

Two standing rules attach to it, both from the record: it is "an overestimate of what a fixed app
costs" (resync §7), and it is the first instance of "measure battery before optimising" (09-07 §8)
actually being done.

## §5 — What a fix would and would not buy, from the code only

- **Same interval, same providers, every registration.** One builder; nothing parameterises it.
- **Network is separable from GPS in code**: the builder loops a two-element list. Separating it
  per collector would need a parameter the `LocationTracker` interface does not have — `fixes` is
  a bare `val`.
- **For `TrackRecordingViewModel` to consume the service's fixes**, a seam would have to exist:
  a bound service, or a flow held somewhere both can reach. Today there is none — the service's
  only output is Room rows. That absence is what prevents it, and it is the "structural change,
  post-beta" the resync report names.
- **`shareIn` on the tracker flow is a smaller edit** (a scope and a `SharingStarted` policy on
  one property) **and it would change the contract** `LocationTracker.kt` documents: "Emits
  `PermissionDenied` once and completes", with callers restarting collection after a grant
  (`AvailabilityViewModel.onLocationPermissionGranted`). A shared flow does not complete, and a
  subscriber joining an already-running share would not see an earlier `PermissionDenied`. That
  is the break to name; whether it matters is a design question, not this pulse's.
- **The planner's GPS-vs-network cost reading cannot be confirmed or refuted from code.** The code
  shows only that each registration asks for both providers at 1 s. What the radio does with two
  identical requests is a platform and hardware fact.

## §6 — Report

**1. Confirmed by observation (file and line, `175b050`):** everything in §3; the two source
paragraphs in §2 quoted; the absence of any plan document and of the device figure, each with a
control that hits; `POLL_INTERVAL_MILLIS`/`updateSundown()` (`:701`, `:435`).
**Inferred:** the four-state registration count is arithmetic over the three bounds above, not
observed on a device.

**2. Could not be determined:** any battery cost of any of it; whether two identical registrations
on one provider cost more than one; the source of the "need-gating recorded in the pre-beta audit
as unbuilt" fragment — the phrase exists on `main` only as `AvailabilityViewModel`'s own comment
("Deliberately not need-gating"), and the store-description audit records `LiveFixGate` at 50 m,
not need-gating.

**3. Premises in the dispatch that were wrong:**
- "**Two** independent platform location registrations exist." **Three** collectors. The source
  paragraph itself said "possibly three with the compass strip's" and the 09-07 log measured
  three; the fragment carried "two" and dropped both.
- "`TrackRecordingViewModel` has no lifecycle gate — bounded only by `locationJob`'s lifetime."
  Half stale since #96: `resyncRecordingState` runs at both lifecycle edges and cancels the job
  when the row says the track ended. Still not a foreground gate, and its own doc says why.
- "A 'need-gating' item was recorded in the pre-beta audit as unbuilt." Not found under that
  phrase in any audit on any branch.
- Held: the cold-`callbackFlow`/no-`shareIn`/both-providers description; the "structural change,
  post-beta" deferral (`resync report:140`); the poll-loop figures; and **`AppContainer:129`**, which
  is correct on `175b050` — the tracker sat at `:127` on `4957675` four days ago and the file has
  grown, so this is a reference the world moved *toward*, not a misattribution. Checked rather than
  assumed, because the dispatch said one of its line references was wrong; this one is not.

**4. Decided beyond scope:** nothing. No fix proposed. Every zero above carries its control.
