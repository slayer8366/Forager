# Completion report — GPX full record: rule provenance and the waypoint id

**Dispatch:** GPX full record: rule provenance and waypoint id (written 2026-09-09).
**Base:** `main` @ `2c3c0f9`, as the dispatch named. **Branch:** `claude/gpx-rule-provenance-and-waypoint-id`.
**Base drift found:** `origin/main` was already at `becb826` when this session started — three commits
ahead of the dispatch's base, all docs (`docs/audits/2026-09-09-journaltabtest-photo-pull-flake.md`
plus one `docs/audits/README.md` row, PR #87). Reported rather than assumed away; merged, see §6.

**Scope held.** Two additive attributes and the waypoint id. Nothing in §0's ratification table was
touched: the namespace, the element and attribute names, the absent `schema`, `pointCount` as the
only count, document order as the ordinal, the absent `startedAt`/`endedAt`, raw `timeEpochMillis`,
and `kept="true"/"false"` are all exactly as PR #80 shipped them.

---

## 1. What was built

### 1a. Record-level: the rule set in force (`rule`)

`<forager:fullRecord>` now carries `rule="timestampMillisNonZero"` (`GpxCodec.encodeFullRecord`).
Space-separated, the XML `NMTOKENS` idiom, so today's single rule is written as the bare literal the
ruling names and a second rule appends without changing the attribute's shape.

The value comes from a new `NETWORK_FIX_EXCLUSION_RULES` in `NetworkProviderFix.kt`, beside the
predicate it describes, and is stamped by `TrackGpxExporter.write` — **not** threaded down through
the UI. Which rules the read seam applies is a property of the build, not of the caller; a parameter
there would only create a way for a screen to omit or mis-state it. That is a deliberate departure
from the "required, not defaulted" argument in `TrackGpxExporter.write`'s existing doc comment, and
the reason is recorded at the function: `fullRecord` is caller data that only one caller can fetch,
the rule set is not caller data at all.

`GpxDocument.exclusionRules` defaults to **empty**, not to the live rule set. A document that
declares no rule set writes no attribute — and, crucially, a file decoded from before this change
comes back declaring none rather than being stamped with today's provenance.

### 1b. Per-point: the rule that excluded it (`excludedByRule`)

Each excluded `<forager:point>` carries `excludedByRule="timestampMillisNonZero"`. Kept points carry
no such attribute — they passed everything, and there is nothing to name.

The value is carried on the record, not derived in the codec: `TrackPointRecord` gains
`excludedByRule: String?`, filled in by `RoomTrackRepository.getFullRecord` — the same place that
applies the predicate. The codec writes what it is handed. Deriving it instead ("excluded, and one
rule exists, therefore that rule") is correct today and becomes a lie the day a second rule ships,
which is precisely the day the attribute matters; naming the wrong rule is worse than the ambiguity
the attribute exists to end.

**`excludedByRule` does not replace `kept`, and `kept` is not derived from it.** They carry different
information for a decoded document: a file written before this change has `kept="false"` points with
no rule attribute, and that pair means "excluded, provenance unrecoverable" — exactly the state §1 of
the dispatch describes. Collapsing the two fields would either fabricate a rule name for those points
or lose the verdict. Recorded at `TrackPointRecord.excludedByRule`.

### 1c. The waypoint id

`encodeWaypoint` now writes `id="…"` on `<wpt><extensions><forager:waypoint>`.

**One behaviour change beyond a bare attribute add, called out because it is not purely additive at
the element level:** that `<extensions>` block used to be omitted entirely when both `trackId` and
`designation` were `null`. `Waypoint.id` is never null, so it cannot follow the omit-when-absent
pattern, and an ordinary, not-track-related waypoint now exports an `<extensions>` block carrying
only its id. GPX 1.1's `wptType` fixes its child list and allows no identifier attribute on `<wpt>`
itself, so the extension is the only valid placement. `trackId` and `designation` keep the
omit-when-absent pattern individually. The existing test that asserted "an ordinary waypoint encodes
no extensions block" was **retargeted, not deleted** — it now pins that the block contains *only* the
id and that the other two are still absent rather than empty.

### 1d. Round-trip (dispatch §4)

`GpxCodec.decode` exists and now reads all three: `rule` into `GpxDocument.exclusionRules` (split on
whitespace, empty when absent), `excludedByRule` into `TrackPointRecord.excludedByRule` (null when
absent), and `id` into `Waypoint.id`, falling back to a fresh `UUID` only when the file carries none.
Round-trip tests cover each.

**But see §7.2 — the decoder has no production caller.** Export is wired; import is not.

---

## 2. Files changed

| File | Change |
|---|---|
| `domain/NetworkProviderFix.kt` | `TIMESTAMP_MILLIS_NON_ZERO_RULE`, `NETWORK_FIX_EXCLUSION_RULES` |
| `domain/model/TrackPointRecord.kt` | `excludedByRule: String? = null` |
| `domain/model/GpxDocument.kt` | `exclusionRules: List<String> = emptyList()` |
| `data/repository/RoomTrackRepository.kt` | `getFullRecord` names the rule on each excluded point |
| `domain/GpxCodec.kt` | encode + decode of `rule`, `excludedByRule`, waypoint `id` |
| `export/TrackGpxExporter.kt` | stamps `exclusionRules` from the build's rule set |
| `domain/GpxCodecTest.kt` | 8 → 13 tests |
| `data/repository/NetworkFixExclusionPerConsumerTest.kt` | +1 test, whole production chain |
| `data/repository/RoomTrackRepositoryTest.kt` | `getFullRecord` expectation now names the rule |
| `export/TrackGpxExporterTest.kt` | two delegation tests updated, plus direct file-text assertions |

---

## 3. Evidence: four revert checks, each matching its prediction

The dispatch's rule was followed — the expected literal `timestampMillisNonZero` is **written out by
hand** in every test, never read from `TIMESTAMP_MILLIS_NON_ZERO_RULE`. A test that takes its
expectation from the code under test passes for any value that code holds.

The harness used here saved a copy of each file **before** editing and restored from that copy (never
`git checkout --`), scanned the build log for compile errors **before** reading any result, and
deleted `app/build/test-results/` before each run so a stale XML would show up as a missing file
rather than as a result. After each restore the forward change was confirmed still present by grep,
and `git diff --stat` was re-checked at the end (10 files, unchanged). Both failure modes CLAUDE.md
records for revert runners are covered.

| # | One-line revert | Predicted | Observed | Message specific to this edit? |
|---|---|---|---|---|
| R1 | drop the `rule` attribute write in `encodeFullRecord` | 3 | 3, exactly the predicted tests | yes — *"the record block must name the rule set in force, got: authoritative=… pointCount=…"*, the tag printed with `rule` missing |
| R2 | drop the `excludedByRule` write in `encodeFullRecordPoint` | 2 | 2, exactly the predicted tests | yes — *"every excluded point names the rule that caught it"* |
| R3 | `RoomTrackRepository.getFullRecord` back to `kept = !isNetworkProviderFix()`, no rule | 2 | 2, exactly the predicted tests | yes — the `TrackPointRecord` list comparison, plus the per-point message from the whole-chain test |
| R4 | drop `id=` from `encodeWaypoint` | 4 | 4, exactly the predicted tests | yes — *expected `b3f1 & <odd> "quoted"` but was `3a49d692-…`*, the fabricated UUID, and *expected ` id="wp-ordinary"` but was ``* |

No compile errors appeared in any of the four build logs; the results are cited on that basis. Every
observed failure is one the edit in question could produce — none named a case belonging to a
different edit.

R1 is the reason `TrackGpxExporterTest`'s two delegation tests gained a direct
`file.readText().contains("rule=\"timestampMillisNonZero\"")` assertion. Those tests compare the file
against `GpxCodec.encode(…)` built in the test, so both sides move together and the comparison alone
would have stayed green through R1 — a check that could not fail. The direct literal assertion is
what makes it bite, and R1 confirms it does.

---

## 4. Test results

Full suite on this Windows host, at the merge head:

**1309 tests / 10 failures / 0 errors / 24 skipped / 167 suite files.**

- **The count reconciles against the dispatch's own baseline.** 1303 (CI, `2c3c0f9`) + 6 new tests
  (`GpxCodecTest` 8→13, `NetworkFixExclusionPerConsumerTest` +1) = 1309. No test was removed.
- **Skips: 24, unchanged.** The set was not adjusted in either direction.
- **The 10 failures are the ten host-specific Windows failures already recorded** in
  `docs/audits/2026-09-09-windows-only-test-failures.md` and its amendment — nine Room migration
  classes on `SQLiteCantOpenDatabaseException`, plus
  `AvailabilityScreenSettingsPanelTest > tapping a track's share action starts a real ACTION_SEND
  chooser for a GPX file` on `IllegalArgumentException` from `FileProvider`.
- **That tenth one was measured, not assumed.** Because it is a *GPX share* test, "pre-existing" was
  not taken on trust: the branch was pushed first, then `2c3c0f9` was checked out detached and that
  one class run on its own. It fails there too, identically, with
  `IllegalArgumentException: Failed to find configured root that contains
  C:\Users\metal\AppData\Local\Temp\robol…` — a FileProvider root/path problem on this host, thrown
  before any file content is read, so it cannot be reached by a change that alters only what the file
  contains. The full message is recorded here because the existing Windows-only note lists that
  message as something it did not have.
- **Nothing was silenced, quarantined, ignored, or weakened**, and no allowlist was touched.
- Two `TrackGpxExporterTest` tests did go red mid-task and were *changed*: they assert the exporter's
  output equals `GpxCodec.encode` of a document the test builds, and the forward change makes the
  exporter add `exclusionRules` to that document. Updating the expected document is what the change
  means; they were also strengthened with direct file-text assertions (see §3). This is a test whose
  subject is the dispatched change, not an unrelated test reduced to reach green.

---

## 5. Design decisions, and what was rejected

- **`rule` on the block *and* `excludedByRule` on the point, not one of them.** The dispatch requires
  both; the reason they are not redundant is recorded in the code. The record-level attribute is what
  makes a file's **kept** points unambiguous — a kept point under one declared rule set is a different
  claim from a kept point under two, and no per-point attribute can express that.
- **Attribute named `rule`, singular, for a set.** `rules` reads better for a space-separated list,
  but the owner's ruling names `rule="timestampMillisNonZero"` literally, and a file format's
  attribute name is not a thing to improvise on. Kept as ruled.
- **`excludedByRule` rather than reusing `rule` on the point.** A parser doing `getElementsByTagName`
  + `getAttribute("rule")` would otherwise conflate the block's declaration with a point's verdict.
  The distinct name also makes the invariant readable in the file: `excludedByRule` is present exactly
  when `kept="false"` and the writer recorded provenance.
- **Rejected: deriving the per-point rule inside the codec** from `kept` plus the declared rule set.
  Correct with one rule, wrong with two, and wrong exactly when it matters.
- **Rejected: defaulting `GpxDocument.exclusionRules` to `NETWORK_FIX_EXCLUSION_RULES`.** It would
  have saved threading one value through `TrackGpxExporter`, at the cost of stamping today's
  provenance onto every decoded old file — inventing the answer to the one question this dispatch
  exists to make answerable.
- **Rejected: putting the waypoint id on `<wpt>` itself.** GPX 1.1's `wptType` allows no such
  attribute; the result would not be a valid GPX file.

---

## 6. `docs/audits/README.md` and the merge

`origin/main` had moved to `becb826` (PR #87) before this work started — docs only, but it appends a
row to `docs/audits/README.md`, the known serialization point. This branch merges `origin/main`
(merge, never rebase).

Verified as a set operation on the row sets, not by reading the merge commit message:

- index rows before the merge, on this branch: **57** (`grep -c "^| 20"`; 67 lines)
- index rows after the merge: **58** (68 lines) — `becb826`'s row added, none dropped
- index rows after this report's own row: **59** (69 lines)
- both sides' filenames present in the merged index: `2026-09-09-journaltabtest-photo-pull-flake.md`
  (from `main`) and `2026-09-09-gpx-rule-provenance-and-waypoint-id-completion-report.md` (this one)

The merge was clean — no conflict arose, because this branch had not yet appended its own row when
the merge was taken. That ordering was deliberate: merge first, then append, so there is no conflict
to resolve and therefore no opportunity to drop a row while resolving one.

---

## 7. Disclosure

### 7.1 Confirmed vs inferred

Every line reference in the dispatch was re-checked at this HEAD (`2c3c0f9`), before any edit:

| Dispatch's claim | At my HEAD | Verdict |
|---|---|---|
| `timestampEpochMillis % 1000 != 0` at `NetworkProviderFix.kt:40` | line **48** | **wrong line**, right code (§7.3) |
| `NetworkProviderFix.kt:34-35` states the rule's own limit | lines 34–38, "The limit this rule is built to be honest about" | confirmed |
| `Waypoint.id: String` at `Waypoint.kt:23` | line 23 | confirmed |
| `encodeWaypoint` at `GpxCodec.kt:122-134` | 122–**137** | start confirmed, end off by 3 |
| `GpxCodec.kt:88-91` argues the raw timestamp correctly | KDoc at 89–94, the precision argument at 90–91 | confirmed in substance |
| `kept` comes from a single predicate | one production construction site, `RoomTrackRepository.kt:48` | confirmed |

**Reachability, checked before behaviour** (CLAUDE.md's cheap question, "who calls this?"):
`GpxCodec.encode` ← `TrackGpxExporter.write` ← `exportAndShareTrack` ← the share `IconButton` in
`TrackExportPanel` — production-reachable, confirmed by grep before anything was built, so both new
attributes will actually reach a tester's file rather than a path nothing runs.

### 7.2 Could not determine

- **`GpxCodec.decode` has no production caller.** `grep -rn "GpxCodec\." app/src/main` returns exactly
  one hit, the `encode` at `TrackGpxExporter.kt:39`. The decoder exists and is exercised by tests, so
  §4's requirement is met at the codec level — but **import is not wired in this app**, and the
  round-trip proved here is a property of the codec, not of a shipped user-facing path. Nothing was
  built to change that; the dispatch says that is a separate decision.
  One consequence to record for whoever does wire import: decode now **restores** a waypoint's id from
  the file instead of always minting a fresh one, so importing a file this app exported would
  re-create waypoints carrying their original ids. That is the correct round-trip behaviour and it
  collides with nothing today, but it is an insert-collision question the day import exists.
- **No device and no CI observation from this session.** The figures in §4 are this Windows host's.
  The dispatch's `1303 / 0 / 0 / 24 / 167` baseline is CI/Linux; it was neither reproduced nor
  contradicted here, only reconciled against by count. Nothing about the shipped file was checked on
  hardware, and no exported GPX from a real device was read.
- **`Workers Builds: forager-pmtiles`** — reported, not chased, per §6 of the dispatch. Not observed
  from this session at all; see "checks that did not fire" below.

### 7.3 Premises that were wrong

- **`NetworkProviderFix.kt:40`** — the predicate is at line **48** at `2c3c0f9`. The dispatch flagged
  its line references as relayed rather than re-derived, so this is the disclosure it asked for, not a
  defect in its reasoning: the code quoted is the code that is there.
- **`encodeWaypoint` at `GpxCodec.kt:122-134`** — the function runs to 137; line 134 is inside it.
- **§0's ratification table: no entry was found to be wrong.** Each of the eight rows was checked
  against the shipped code and each describes it accurately.
- **§3's claim that `Waypoint.id` is unexported: correct.** `encodeWaypoint` wrote `trackId` and
  `designation` only.
- **§3's "`trackId` and `originWaypointId` are largely recoverable from file context" — partly wrong,
  and this is the one the dispatch explicitly asked to hear about.**
  - `trackId` is recoverable **only when the track has at least one waypoint**. `TrackExportPanel`
    filters the exported waypoints to `it.trackId == track.id`, so every `<wpt>` in the file carries
    the track's id — but a track with no waypoints (an ordinary walk with nothing dropped) exports
    zero `<wpt>` elements, and the file then contains the track's id nowhere at all. The filename is
    derived from `Track.startedAtEpochMillis`, not from the id.
  - `originWaypointId` is recoverable **only when the origin waypoint happens to be in the file**.
    Nothing guarantees it is: `Waypoint.trackId` means "dropped *while recording* this track"
    (`Waypoint.kt:12-20`), while `Track.originWaypointId` may point at a waypoint that predates the
    recording — a reused trailhead — whose `trackId` is null and which the export filter therefore
    excludes.
  - Per §3, **neither was added.** This is the disclosure, not a change of scope. Worth noting that
    §1c makes `originWaypointId` recoverable in the common case for the first time, since the origin
    waypoint's own id is now in the file at all.

### 7.4 Decided beyond scope

- The attribute name `excludedByRule` for §2b (§7 leaves per-placement details to the coder).
- Space-separated `NMTOKENS` for the record-level set, so a single-rule file reads exactly as the
  ruling writes it.
- Carrying `excludedByRule` on `TrackPointRecord` and stamping it in `RoomTrackRepository`, rather
  than deriving it in the codec — a new field on a domain model the dispatch did not mention.
- `GpxDocument.exclusionRules` as a new field, defaulted to empty, stamped by `TrackGpxExporter`.
- **The waypoint `<extensions>` block is now emitted for every waypoint** (§1c). This changes the
  output shape for ordinary waypoints, which is more than "an additive attribute"; there is no valid
  GPX alternative, but it is the one place the change is larger than §3's own description of it.
- Retargeting the existing `an ordinary waypoint with no track link encodes no extensions block` test
  rather than deleting it.
- Merging `origin/main` into this branch before appending the index row (§6). §5 says merge, never
  rebase; when to merge was not ruled on.

### 7.5 The two specific requirements

- **Did the push land?** Yes — established by `git ls-remote`, not by local history and not by a
  commit message. `refs/heads/claude/gpx-rule-provenance-and-waypoint-id` was confirmed at the pushed
  SHA after each push.
- **Checks that did not fire.** No CI check was observed from this session at all — the branch was
  pushed and the PR opened, but nothing waited on or read a check result. So `Workers Builds:
  forager-pmtiles` (expected red on every PR head), the Android CI job, and any status on this PR are
  all **unobserved**, not green and not red. `JournalTabTest` did **not** flake in this session's full
  suite run: it passed, which per §6 of the dispatch is itself a sample about the rate and is reported
  for that reason. No device check ran. No lint or static-analysis tool ran, because the project
  configures none (no ktlint, detekt, spotless or `.editorconfig` in the tree).
