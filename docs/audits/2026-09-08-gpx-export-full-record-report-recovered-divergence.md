# Findings — the missing prebuild report, recovered, and where PR #80 diverges from it

**Type:** Findings, produced under the follow-up dispatch ("close the GPX export's evidence
gaps"). **Stop-and-ask A of that dispatch has triggered — this document reports the divergence
and stops, per its own instruction not to amend the code or the report.**

---

## §1 — The report is not missing. It's on an unmerged docs-consolidation PR.

`docs/audits/2026-09-08-gpx-export-full-record-prebuild-report.md` exists, complete, with an
owner addendum, on branch `claude/new-session-wjlg6g` and is folded (as a clean merge, per that
PR's own description) into **PR #79** (`claude/consolidate-docs-2026-09-08`, open, docs-only,
unmerged, base `main`). It is not on `main` and not on PR #80's branch, which is why it read as
missing when the original dispatch pointed at it. Confirmed by reading the file directly from
that branch via the GitHub API — full text available in that PR.

The follow-up dispatch's hypothesis (PR #79, row-count arithmetic, the PR #77 precedent) was
correct on the first branch checked.

---

## §2 — The six sourced items, closed individually

| # | Item | Verdict |
|---|---|---|
| 1 | Filtered read path / where `toDomain` applies the filter | **Confirmed, and the dispatch's own naming was wrong, as already caught.** The report itself corrects the original dispatch: the exporter's chain reads `RoomTrackRepository.getAll` (via `GetTracksUseCase`), not `getForDay`. It further states all three — `getAll`, `getById`, `getForDay` — pass through the one `toDomain` mapping. This matches exactly what the PR #80 completion report already recorded independently ("three filtered surfaces, not one") before this report was found. |
| 2 | Exclusion predicate & location | **Confirmed.** `timestampEpochMillis % 1_000L != 0L`, `NetworkProviderFix.kt:40` (line number matches on both the report's read commit and current `main`). No divergence. |
| 3 | `accuracyMeters` reachable | **Confirmed.** On `TrackPoint` via the seam already, no new read needed — matches what PR #80 built (`TrackPointRecord.point.accuracyMeters`, written directly). |
| 4 | Export is per-track via the share icon only | **Confirmed.** No other `ACTION_SEND`/`ACTION_SEND_MULTIPLE`/SAF path exists; PR #80 didn't add one. |
| 5 | Whole-second timestamps are not a truncation bug | **Confirmed**, and independently re-verified in the PR #80 completion report by the same reasoning (`Instant.toString()` prints milliseconds only when non-zero). |
| 6 | `<extensions>` legal under `<trk>` before `<trkseg>` | **Confirmed against the fetched GPX 1.1 XSD** (`trkType`: `name?, cmt?, desc?, src?, link*, number?, type?, extensions?, trkseg*`). PR #80's placement (`<trk><extensions>` before `<trkseg>`) is schema-legal, matching this. |

**None of the six sourced facts contradicts the code.** The divergence is elsewhere — in the
*format PR #80 shipped* versus the format the report's section B recommended and the owner's
addendum closed. That is §3.

**Which two were reverified before the report was found:** #2 (the predicate and its location)
and #5 (the timestamp non-bug) — named explicitly in the PR #80 completion report's opening
section as independently re-checked against the code.

---

## §3 — Where PR #80 diverges from the report's ruled format

The report's section B.2 is not just a recommendation left open: the owner's addendum states
plainly, **"Section B is therefore closed: B1, inside `<trk><extensions>`, authority declared
in-band, with the track's waypoints as `<wpt>` elements carrying `id`, `trackId` and
`designation` in their own `<extensions>`."** That sentence ratifies the mechanism, not only the
three items the ruling table lists by name (base branch, waypoint inclusion, placement level).
PR #80 was built without this document and independently designed its own format, choosing the
same broad shape (B1, `<trk><extensions>`, one shared namespace, an authority declaration,
per-point verdicts, waypoint extension) but different concrete names and — in at least one place
— different information content.

| | Report's ruled/recommended shape | PR #80's shipped shape |
|---|---|---|
| Namespace URI | `https://github.com/slayer8366/Forager/gpx/1` | `https://forager.app/gpx/1` |
| Root extension element | `<forager:record>` | `<forager:fullRecord>` |
| Root element attributes | `schema="1" authority="record" trksegDerivedFrom="record" trackId="…" startedAt="…" endedAt="…" originWaypointId="…" rule="timestampMillisNonZero" storedPoints="195" keptPoints="135" excludedPoints="60"` | `authoritative="true" trksegDerivedFromFullRecord="true" pointCount="N"` |
| Point element | `<forager:pt>` | `<forager:point>` |
| Point ordinal | `i` (0-based stored order) | *(absent — order is positional in the XML, not an explicit attribute)* |
| Point timestamp | `t`, **fixed three-decimal ISO-8601** (`2026-09-06T12:40:07.000Z`) — a deliberate choice "so the field's form does not depend on the JDK's printing rule" | `timeEpochMillis`, the raw stored `Long` |
| Point accuracy/speed | `acc`, `spd`, `spdAcc` | `accuracyMeters`, `speedMetersPerSecond`, `speedAccuracyMetersPerSecond` |
| Point verdict | `v="kept"` / `v="excluded"` | `kept="true"` / `kept="false"` |
| Waypoint extension | `<forager:waypoint id="…" trackId="…" designation="…"/>` | `<forager:waypoint trackId="…" designation="…"/>` — **`id` is absent** |
| Rule identification | `rule` attribute names the predicate (`timestampMillisNonZero`), "so a future second rule can be told apart" | no equivalent field — a second rule, if ever added, would be undetectable from the file alone |
| Track context on the block | `trackId`, `startedAt`, `endedAt`, `originWaypointId` all present on the root extension element | none of these — a reader needs the surrounding `<trk><name>` and nothing else |

Some of these are re-derivable or equivalent in substance (`pointCount` vs. `storedPoints`/
`keptPoints`/`excludedPoints` — the latter two sum from the former plus a `kept` tally; `kept`
boolean vs. `v` string enum — same information, different encoding). Two are not just naming:

- **The waypoint `id` is genuinely missing from PR #80's output**, and the addendum's closing
  sentence names it explicitly (not only in the illustrative XML block, which is itself
  flagged "illustrative" — this one is stated as plain prose in the ruling). A round-trip
  through PR #80's file cannot recover which stored waypoint row a decoded `<wpt>` came from,
  only its `trackId`/`designation`.
- **The `rule` attribute's absence** means PR #80's file has no way to name which predicate
  produced the verdicts it carries, which the report gives an explicit forward-compatibility
  reason for ("a future second rule can be told apart"). Today there is exactly one rule, so this
  has no live consequence yet — but it's a real content gap, not a stylistic one, against a
  requirement the report reasoned about deliberately.

The timestamp representation is a genuine design fork rather than an oversight: PR #80's
`timeEpochMillis` is the literal stored value with no formatting round-trip to lose precision
through, which arguably satisfies "full millisecond precision" at least as well as the report's
fixed-three-decimal string — but it is a different choice than the one the report reasoned to and
the addendum closed on, made without knowledge that a ruling existed.

---

## Why this stops here, per the follow-up dispatch's own stop-and-ask A

*"If the recovered report contradicts the code. Report the divergence and stop. Do not amend the
code to match the document, and do not amend the document."*

The six sourced facts do not contradict the code — they check out exactly. The **shipped GPX
format** contradicts the **ruled GPX format**, in ways that go beyond naming (the waypoint `id`,
the `rule` identifier). Per instruction, neither PR #80's code nor the recovered report has been
touched to reconcile this. Items 3 (redo stop-and-ask A on content) and 4 (build a device-free
acceptance test) are not attempted here: both would mean writing test assertions against a
specific attribute shape, and doing that against a format whose correctness is now in question
would either bake in PR #80's independently-chosen names as if ratified, or silently prefer the
report's without being asked to. That choice is the owner's.

**What is answered below (§4), because it does not require resolving the format question:**
predicate sharing, the namespace decision already made and why, and the doc-update targets —
none of these depend on which attribute names win.

---

## §4 — What the follow-up dispatch's §5 asked, answered where it doesn't require resolving §3

- **Shared predicate or a copy?** Shared, not copied. `RoomTrackRepository.getFullRecord`
  (`data/repository/RoomTrackRepository.kt:45-49`) calls `point.isNetworkProviderFix()` — the
  same extension function `NetworkProviderFix.kt:40` defines and the same one `toDomain` calls
  via `excludeNetworkProviderFixes`. One definition, two call sites, no second copy to drift.
- **One Forager namespace across `<trk>` and `<wpt>`, or two?** One (`GpxCodec.FORAGER_NAMESPACE`,
  currently `https://forager.app/gpx/1` — see §3 on the URI value itself). Reasoning already
  recorded in `GpxCodec.kt`'s doc comment: neither extension shares fields with the other, so one
  small vocabulary for one app's own data is simpler than maintaining two. This matches the
  report's own approach (it also uses a single `forager:` prefix across both), independent of the
  URI/element-name divergence in §3.
- **The two doc updates.** Both were made in PR #80 — `NetworkProviderFix.kt`'s doc comment now
  points at the export as the instrument for checking the rule on other hardware, and
  `docs/beta/README.md`'s device-report paragraph now says a tester's track file settles what the
  rule did directly. Checked against the report's own addendum, which asks for exactly this
  ("that comment should point at the export as the instrument... and the beta README's... sentence
  should be updated to say the file now is") — the changes made without knowledge of the report
  satisfy what the report asked for. No further action needed on these two.

---

## What happens next

Not decided here — that's the point of stopping. Two live options, stated so the choice is fast
to make:

1. **Ratify PR #80's shipped format as the actual decision**, superseding the report's specific
   naming (the broad shape — B1, `<trk><extensions>`, authority in-band, waypoints included — was
   independently arrived at and matches; only names and two content fields differ). Add the
   waypoint `id` and a `rule` identifier as the two substantive gaps, update the report's own
   status note to point at this document, and proceed to items 3–4 against the corrected format.
2. **Rebuild to the report's exact ruled shape** (`forager:record`/`forager:pt`, `i`, fixed
   three-decimal `t`, `acc`/`spd`/`spdAcc`, `v`, the root context attributes, waypoint `id`) and
   then proceed to items 3–4 against that.

Either way, items 3 and 4 are real work still to do once the format question is settled — this
document does not build the acceptance test or redo stop-and-ask A, per the instruction to stop
rather than build atop an unresolved contradiction.
