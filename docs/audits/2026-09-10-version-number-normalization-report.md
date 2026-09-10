# Stage 1 audit: version-number normalization

**Date:** 2026-09-10. **Dispatch:** Phase 0, normalize version numbers (revision 3).
**Base:** `main` at `bddbb2a`, tracking `origin/main` — 0 behind, 0 ahead, re-derived with
`rev-list --left-right --count`. **No code was changed.** Precondition for
`species-cards-dispatch-v2.md` blocker B1.

---

## 0. Repository and rulebook

**0.1** Remote `https://github.com/slayer8366/Forager.git`, branch `main`, HEAD `bddbb2a`, upstream
`origin/main`, neither ahead nor behind. The dispatch names "the original repository, active again";
that matches what is here.

**0.2 The governing `CLAUDE.md` is missing one rule this dispatch cites, and is wrong about another.**

| Rule the dispatch cites | In the governing copy? |
|---|---|
| Retired package root — never paste it, write the placeholder | **Absent.** That section was removed on 2026-09-10 when the owner rescinded the forbidden-term rule. The dispatch says its rules come from forager-bak's copy; this is one that did not survive. |
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
exactly this pair of behaviours and passes. Whether any device holds v1 or v2 is an owner input.

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

## 3. Numbers moved during the forager-bak period

| Number | Canonical | Introduced | Where it is now | Seen outside the repo | Proposed action |
|---|---|---|---|---|---|
| `ForagerDatabase.version` | 15 | 15, unchanged | both | not applicable | **none, nothing moved** |
| Migrations 3_4 to 14_15 | 12, unbroken | unchanged | both | not applicable | **none** |
| Exported schema files | 4 to 15 | unchanged | both | not applicable | **none** |
| `FungiIndexDatabase.version` | 1 | unchanged | both | not applicable | **none** |
| **`versionCode`** | commit count | **563 to 575** | here | **unknown, see §6.2** | **none, it rose** |

**Nothing touched Room during that period.** The only code commit was the lifecycle gate `5967dd5`,
which changed a ViewModel and an Activity. The database, its migrations and its schemas are
byte-identical across the whole period.

**`versionCode` is the one number that moved, and it moved in the safe direction.** Because it is the
commit count it is a function of history shape, not a literal anyone edited:

| Tree | `rev-list --count` |
|---|---|
| `443f1fa`, before | 563 |
| `main` now | **575** |
| forager-bak main | **8** |
| forager-bak root | 1 |

The work came back by **merging** rather than replaying, so `main` kept its 563 commits and gained 12.
Had it been cherry-picked onto the short-rooted tree, `versionCode` would have collapsed from 563 to
about 8 — and by §1 item 3, no device on a 563 build could ever have updated again, with Play
rejecting the reused low codes. **That outcome was avoided incidentally, not by design.** No migration
dispatch identified `versionCode` as a derived number at risk, because it does not appear in the tree
as a number.

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

- **Every §6 owner input.** Which builds were installed on a device and which repository each came
  from; which `versionCode` values Play has seen, including drafts and closed-test releases; whether
  any installed device holds data worth keeping. None is answerable from the repository. Until they
  are answered the "seen outside the repo" column in §3 stays **unknown**, and no renumbering decision
  can be made.
- Whether any device holds v1 or v2, the two versions with no migration path.

### 6.3 Premises that were wrong

1. **§4.0.3's "zero is expected"** for the retired root in branch trees. It is 43 of 48.
2. **`CLAUDE.md`'s "51 commits of drift"** — actually 10 / 1 between the two branches.
3. **`CLAUDE.md`'s "still unresolved"** — resolved; `main` carries one v5 and `offline_regions` moved
   to v6.
4. **The dispatch's framing that the forager-bak period moved a number.** For Room, nothing moved. The
   only number that moved was `versionCode`, which no dispatch named because it is derived rather than
   written down anywhere.
5. **§4.4's premise** that a migration test might silently pass by failing to find a schema file. No
   test reads a schema file at all.

### 6.4 Decided beyond scope

**Empty.** No code changed, no number changed, no test touched.

## 7. Stop

Per §4.6 this stops here for the owner's ruling.

On the evidence, the §5 table's **first row** is the one that applies — "no device and no Play upload
ever saw a forager-bak number" — and in that case the prescribed action, "revert every moved number to
canonical", has nothing to revert. Room never moved, and `versionCode` rose rather than fell. That
reading depends entirely on the §6 owner inputs, which is why it is offered as the likely outcome
rather than acted on.

Two items may need work regardless of how the ruling goes, and neither originates in this dispatch:
the **v1/v2 gap** in §1, and the **unread exported schemas** in §4.
