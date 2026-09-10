# Version-number normalization: Stage 1 audit and Stage 2 outcome

**Date:** 2026-09-10. **Dispatch:** Phase 0, normalize version numbers (revision 3).
**Base:** `main` at `bddbb2a`, tracking `origin/main` — 0 behind, 0 ahead, re-derived with
`rev-list --left-right --count`. **No code was changed, in either stage.** Precondition for
`species-cards-dispatch-v2.md` blocker B1.

> **Deviation from §7.3, stated rather than done quietly.** The dispatch asks for a separate Stage 2
> completion report with its own index row. The owner's ruling reduced Stage 2 to records only —
> nothing to revert — so a second document would have restated this one and added a row saying so.
> Stage 2's outcome is §7 below, and the single index row covers both stages. If a separate report is
> wanted, say so and it costs one commit.

---

## 0. Repository and rulebook

**0.1** Remote `https://github.com/slayer8366/Forager.git`, branch `main`, HEAD `bddbb2a`, upstream
`origin/main`, neither ahead nor behind. The dispatch names "the original repository, active again";
that matches what is here.

**0.2 The governing `CLAUDE.md` is missing one rule this dispatch cites, and is wrong about another.**

| Rule the dispatch cites | In the governing copy? |
|---|---|
| Retired package root — never paste it, write the placeholder | **Absent.** That section was removed on 2026-09-10 when the owner rescinded the forbidden-term rule. The dispatch says its rules come from an older copy; this is one that did not survive. |
| Push before you tidy | Present, Known pitfalls. Unchanged. |
| The audits index is a serialization point, keep every row | Present — and **contradicted by the change committed alongside this report**, which deletes two index rows on the owner's explicit ruling. Disclosed, not reconciled. |
| Reachability before behaviour | Present, Known pitfalls item (4). Unchanged. |
| Verify your base branch | Present. It contains the collision passage audited in §2, **which is wrong on both of its numeric claims.** |

Silence is not contradiction, so the retired-root writing convention was followed here as a courtesy
rather than treated as a stop condition.

**0.3 §8's stop condition fires, and is reported rather than obeyed.** The dispatch expects every
branch's current tree to contain zero occurrences of the retired root. It is **43 of 48 branches**,
up to **433 paths** each.

| Paths carrying it | Branches |
|---|---|
| 433 | `exp-arm-a-present`, `exp-arm-b-substituted`, `flake-bisect-note`, `gpx-namespace-domain`, `gpx-rule-provenance-and-waypoint-id`, `integration-2026-09-09`, `journaltabtest-flake-comparison`, `new-session-3x1aba` |
| 202–432 | 35 further branches |
| 0 | 5 — `main` and the four cut after the rename |

This is an artifact of those branches being cut before the rename. It was already known, the owner
already ruled they must not be rebased, and the term rule itself was rescinded on 2026-09-10.
Stopping here would enforce a rule that has been lifted. **`main` itself is clean.**

## 1. Canonical numbers on the base branch

| Number | Value | Location |
|---|---|---|
| `ForagerDatabase.version` | **15** | `ForagerDatabase.kt:159` |
| `FungiIndexDatabase.version` | **1**, `exportSchema = false` | `FungiIndexDatabase.kt:35-36` |
| Migrations | **12**, `MIGRATION_3_4` through `MIGRATION_14_15`, an unbroken chain | `Migrations.kt:19, 101, 170, 212, 359, 446, 566, 619, 746, 794, 859, 908` |
| Registration | all 12 passed to `addMigrations` in the production builder | `ForagerDatabase.kt:188-191` |
| Exported schemas | **12 files**, `4.json` through `15.json` | `app/schemas/com.zynergylabs.forager.app.data.local.ForagerDatabase/` |
| `versionCode` | **derived, not a literal** — `git rev-list --count HEAD` | `app/build.gradle.kts:72`, assigned at `:246` |
| `versionName` | `1.0.$count+g$shortSha`, plus a dirty suffix | `app/build.gradle.kts:56`, assigned at `:247` |

**Reachability, checked before behaviour, per the standing rule:** all 12 migrations appear in the
production `addMigrations` call. None is unreachable. Every schema file's internal `version` matches
its filename, and each carries a distinct `identityHash`.

**Gap: versions 1 and 2 have neither an exported schema nor a migration path.** The lowest migration
is 3 to 4 and the lowest schema is `4.json`. A device holding v1 or v2 has no route forward: the
release path throws, and the debug path would wipe. `ForagerDatabaseDestructiveFallbackTest` asserts
exactly this pair of behaviours and passes. The owner confirmed on 2026-09-10 that no device holds any version, so this is latent rather than live.

## 2. The collision — resolved, and `CLAUDE.md` is wrong about it twice

`CLAUDE.md` states that PR #26 and the Phase 1 branch each declared `version = 5` with a different
`MIGRATION_4_5`, that this is "still unresolved", and that there are "51 commits of drift". The first
part is true. **Both numeric claims are false.**

Two distinct `MIGRATION_4_5` bodies exist, extracted from each branch's own file and hashed:

| Body | Creates | Where it lives |
|---|---|---|
| `bdee6024`, 42 lines | `tracks`, `track_points`, `waypoints`, and an index | **`main`**, plus `plan-execution-review-3zila4` and `surface-error-states` |
| `05db62a9`, 18 lines | `offline_regions` | `plan-implementation-rjzmkr` only |

**`main` carries the tracks shape at v5, and PR #26's `offline_regions` work was renumbered into
`MIGRATION_5_6`.** That is the resolution: one v5 with one meaning, the other work moved up a number.
It is visible in `Migrations.kt` — `MIGRATION_5_6` creates `offline_regions`, and `MIGRATION_9_10`
later adds its `createdAtEpochMillis` index.

**Drift, re-derived and dated:**

- `plan-implementation-rjzmkr` tip `56cd764` (2026-08-20) against `plan-execution-review-3zila4` tip
  `9fd026e` (2026-08-20): `rev-list --left-right --count` gives **10 / 1**, merge-base `ba9d161`,
  also 2026-08-20. Not 51.
- Neither branch is an ancestor of `main`. Both are **487 commits behind** it.

The passage describes a live hazard that was closed roughly 487 commits ago. §7.3 already requires
correcting it in Stage 2.

## 3. Numbers that moved, and the one that is derived

| Number | Canonical | Current | Moved? | Seen outside the repo | Action |
|---|---|---|---|---|---|
| `ForagerDatabase.version` | 15 | 15 | no | never | **none** |
| Migrations 3_4 to 14_15 | 12, unbroken | 12, unbroken | no | never | **none** |
| Exported schema files | 4 to 15 | 4 to 15 | no | never | **none** |
| `FungiIndexDatabase.version` | 1 | 1 | no | never | **none** |
| **`versionCode`** | commit count | **575** | rose, 563 to 575 | never | **none, it rose** |

**Nothing has touched Room recently at all.** The database, its twelve migrations and its twelve
exported schemas are byte-identical to their state at `443f1fa`. The only code commit since was the
lifecycle gate `5967dd5`, which changed a ViewModel and an Activity.

**`versionCode` is the one number that moved, and it is the one nobody can find by searching.**
It is not a literal: `app/build.gradle.kts:72` computes it as `git rev-list --count HEAD`, so it is a
function of history shape rather than a value anyone edits. It reads **575** now against **563** at
`443f1fa` — it has only ever risen, which is the safe direction and the only direction Android will
accept.

**The owner confirmed on 2026-09-10 that no build has ever been installed on a device and nothing
has ever been uploaded to Play, including drafts.** That settles the whole class of risk this
dispatch was written around: there is no floor to respect, no device holding an unreachable version,
and no data anywhere that a migration could destroy. Every "seen outside the repo" cell above is
**never**, not unknown.

The finding worth keeping past this dispatch is the shape of it. A derived number is a globally
unique claim that no grep will turn up, so the base-branch rule in `CLAUDE.md` — check a
globally-unique value against the base's current state — has a blind spot exactly where the value is
computed rather than written. That is now recorded there.

**Documents citing these numbers:** `ForagerDatabase.kt:40`, a comment naming `room.schemaLocation`
and "only version 4 onward"; and `CLAUDE.md`'s collision passage covered in §2. No audit report states
a database version as a live figure.

## 4. Schema files after the rename

**`main` is clean.** One directory,
`app/schemas/com.zynergylabs.forager.app.data.local.ForagerDatabase`, matching the class's current
fully-qualified name. No orphan directory, no deleted version, no path spelling the retired root.

Across all branches, 43 still carry the old directory name and 5 the new one — the same pre-rename
artifact as §0.3, and out of scope.

**The failure mode §4.4 warns about cannot occur here, because nothing reads these files at all.**
`MigrationTestHelper` appears in **zero** files. The exported schemas have no automated reader: the
only references anywhere are the `room.schemaLocation` argument that writes them
(`app/build.gradle.kts:429`) and one comment. They are generated, committed, and never asserted
against. That is worth a decision of its own, separately from this dispatch.

## 5. Migration tests

Twelve Robolectric classes, `@RunWith(RobolectricTestRunner::class)`, plus
`ForagerDatabaseDestructiveFallbackTest`.

**They do not read the exported schemas.** Each builds its starting point from a hand-written
`@Database` fixture — `LegacyForagerDatabaseV2` through `V14`, one per class — via
`Room.databaseBuilder`, then applies SQL directly and migrates forward. A schema file could be
deleted or misnamed without any test noticing; equally, the rename could not have broken them.

**What a passing one actually asserts:** it opens a legacy fixture, writes representative rows,
migrates to current, and reads them back through current DAOs. Data survival and schema shape, not
merely that nothing threw.

**Run on this Windows host.** Build log checked for compile errors first, per the standing rule:
none, 33 tasks executed, `testDebugUnitTest` reached.

| Class | tests | failures |
|---|---|---|
| `ForagerDatabaseDestructiveFallbackTest` | 2 | **0** |
| `CartographyEntryMigrationTest` | 2 | 1 |
| The other 10 migration classes | 1 each | 1 each |

**11 failures, all carrying one message:** `SQLiteCantOpenDatabaseException: unable to open database
file (code 14 SQLITE_CANTOPEN)`. This is the known Windows MAX_PATH condition, not a migration defect
— the same 11 fail identically on unrelated commits, and **Linux CI passed on this exact tree**
(PR #94, "Build, test, publish APK", SUCCESS). Linux CI is the authority. Suite totals, parsed from
167 JUnit XML files rather than the console line: **167 suites, 1311 tests, 24 skipped, 12 failures**,
the twelfth being `AvailabilityScreenSettingsPanelTest`, also host-specific.

## 6. Disclosure

### 6.1 Confirmed vs inferred

**Confirmed** by direct reading at `bddbb2a`, each check paired with a control that would have caught
a broken detector: every value in §1; both `MIGRATION_4_5` bodies and their hashes; the drift counts;
the branch sweep; the absence of `MigrationTestHelper`; and every test result, parsed from JUnit XML.

**Inferred:** that the 11 failures are host-specific rather than migration defects. Strongly supported
— one shared message, and a green Linux run on this exact tree — but I did not run them on Linux
myself.

### 6.2 Could not determine

**Nothing material remains open.** The §6 owner inputs were answered on 2026-09-10, after the audit
body above was written and before this section was finalised: **no build has ever been installed on
a device, and nothing has ever been uploaded to Play, drafts included.** That answer converts every
"seen outside the repo" cell in §3 from unknown to **never**, removes the v1/v2 question entirely
(no device holds any version), and means no floor constrains any future number.

One item stays inferred rather than undetermined: that the 11 Windows failures are host-specific.
See §6.1.

### 6.3 Premises that were wrong

1. **§4.0.3's "zero is expected"** for the retired root in branch trees. It is 43 of 48.
2. **`CLAUDE.md`'s "51 commits of drift"** — actually 10 / 1 between the two branches.
3. **`CLAUDE.md`'s "still unresolved"** — resolved; `main` carries one v5 and `offline_regions` moved
   to v6.
4. **The dispatch's central framing, that the period under audit moved a number needing correction.**
   For Room, nothing moved. The only number that moved was `versionCode`, which no dispatch named
   because it is derived rather than written down anywhere.
5. **§4.4's premise** that a migration test might silently pass by failing to find a schema file. No
   test reads a schema file at all.

### 6.4 Decided beyond scope

**Empty.** No code changed, no number changed, no test touched.

## 7. Outcome

**The owner ruled on 2026-09-10: no build ever reached a device, and nothing ever went to Play.**
That is the §5 table's first row, the clean case — "revert every moved number to canonical" — and
**there is nothing to revert.** Room never moved, and `versionCode` only rose. No floor was kept,
because none exists.

Stage 2 is therefore records only, and is committed alongside this report: `CLAUDE.md`'s collision
passage corrected to say the instance is resolved, how, by which commits, and which alternative was
rejected and why; plus the standing note that `versionCode` is derived and so invisible to the search
a reader would naturally run.

**Current versions, stated for the species-card dispatch to take its migration number from:**
`ForagerDatabase` is at **15**, so the next migration is `MIGRATION_15_16` and the next database
version is **16**. `FungiIndexDatabase` is at **1** and does not export schemas.

**Blocker B1 is closed** on the numbering question.

Two items outlive this dispatch and are *not* closed by the ruling, because neither depends on what
a device holds:

- **Nothing reads `app/schemas/`.** Twelve exported schemas are generated, committed, and never
  asserted against; `MigrationTestHelper` appears in zero files. Whether to add schema-driven
  migration tests is a decision of its own, and is not made here.
- **Versions 1 and 2 have no migration path.** Harmless while no device holds them — which is now
  established — but it is a real gap the moment a build ships from an older tree.
