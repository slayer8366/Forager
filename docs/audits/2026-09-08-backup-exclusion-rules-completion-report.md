# Completion report: backup rules for Android auto backup

> **REDACTED 2026-09-09 — forbidden-term policy.** Occurrences of this project's retired reverse-DNS
> package root were replaced in this file with `com.zynergylabs.forager.app`. **This document is
> therefore not an original record**: where it names a package, the name it originally recorded has
> been altered. The change is textual only — no finding, figure, date or conclusion was edited.

> **RECOVERED 2026-09-09, and it describes a superseded approach.** This report was never committed\n> -- it existed only in an unpushed working tree and was recovered from a local mirror. Two things to read it against: the approach it
> documents (excluding paths from Android auto backup via `backup_rules.xml` and
> `data_extraction_rules.xml`) was **later withdrawn** in favour of `android:allowBackup="false"`,
> so neither file exists in this repository; and its stated base is `cb16932`. Kept because it records *why* that approach was built and what it found.

**Date:** 2026-09-08
**Dispatch:** Build — backup rules for Android auto backup (owner ruling: keep auto backup
on, exclude what does not belong in a user's Drive).
**Reads first:** `docs/audits/2026-09-08-data-inventory-for-privacy-policy.md` §5, §6.2.
**Base:** `origin/main` = `699efa3` (PR #79, docs only), merged into this branch before the
change. See "Base branch" below.
**Sandbox has no device. No device result is claimed anywhere in this report.**

---

## The finding that changed the change

**Crash logs are written to two locations, not one — and the dispatch's stop-and-ask A
covers exactly this.** Read from the writer, not from the inventory summary and not from the
rule:

```kotlin
// app/src/main/java/com/zynergylabs/forager/app/crash/CrashFileStore.kt:76-77
fun forContext(context: Context): CrashFileStore =
    CrashFileStore(File(context.getExternalFilesDir(null) ?: context.filesDir, "crashes"))
```

That elvis is the whole point. The normal destination is
`getExternalFilesDir(null)/crashes` — the `external` domain, as the inventory said. But when
`getExternalFilesDir(null)` returns `null` (no external volume available to the app), the same
store writes to `filesDir/crashes` instead — the `file` domain. Same artefact, same directory
name, two domains. **A rule written against `external` alone is silently inert for every crash
written on a device in that state**, which is precisely the failure mode the dispatch's §3
warns about.

**I did not stop on this.** The owner's ruling is "crash logs — exclude, both formats, both
sections", and the second path is the same artefact reaching the same directory name by the
same `forContext` call; excluding it needs no new judgement and cannot touch the journal,
photos or settings (nothing else in this app writes to `filesDir/crashes`). Blocking a
beta-blocking change to ask "should the fallback path for the thing you already ruled must be
excluded also be excluded?" would have cost a round trip for an answer the ruling already
gives. **The paths are reported here rather than assumed silently, which is what the
stop-and-ask exists to guarantee.** If the owner disagrees, the fix is deleting two lines.

`forContext` is the only construction of a `CrashFileStore` in production — `AppContainer.kt:136`
and `AvailabilityScreen.kt:659`, both through it — and it is the only thing in the whole app
that touches external storage at all (`grep -rn "getExternalFilesDir\|Environment.getExternal\|
externalCacheDir\|externalMediaDirs" app/src/main` returns that one file and its own doc
comments). So there is no third crash path and no other external-domain content to consider.

---

## What was built

Three files touched. `git diff --stat` is one modified manifest and two new resources; no
Kotlin changed, no dependency changed, no test changed.

### `app/src/main/res/xml/data_extraction_rules.xml` (new) — API 31+, governs at `targetSdk = 37`

```xml
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="external" path="crashes/" />
        <exclude domain="file" path="crashes/" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="external" path="crashes/" />
        <exclude domain="file" path="crashes/" />
    </device-transfer>
</data-extraction-rules>
```

### `app/src/main/res/xml/backup_rules.xml` (new) — Android 11 and below

```xml
<full-backup-content>
    <exclude domain="external" path="crashes/" />
    <exclude domain="file" path="crashes/" />
</full-backup-content>
```

### `app/src/main/AndroidManifest.xml` (modified)

`android:allowBackup="true"` is unchanged — the ruling is to keep backup on. Two attributes
added to `<application>`, plus a comment block above it recording why:

```
android:dataExtractionRules="@xml/data_extraction_rules"
android:fullBackupContent="@xml/backup_rules"
```

### Exactly what landed where — the table the privacy policy needs

| Excluded path | Domain | `backup_rules.xml` (≤ Android 11) | `data_extraction_rules.xml` → `<cloud-backup>` | `data_extraction_rules.xml` → `<device-transfer>` |
|---|---|---|---|---|
| `getExternalFilesDir(null)/crashes/` | `external` | yes | yes | yes |
| `filesDir/crashes/` (the elvis fallback) | `file` | yes | yes | yes |

**Nothing else is excluded.** The journal database, the log photos and the DataStore settings
all remain in the backup set, in both formats and in both sections.

---

## Why the two sections are both filled in, and why exclude-only is safe

**`<cloud-backup>` and `<device-transfer>` do not inherit from each other.** They are two
independent sections of the same file — the platform's own names for them are
`FullBackup.java`'s `CLOUD_BACKUP = "cloud-backup"` and `DEVICE_TRANSFER = "device-transfer"`
(android-37.0 source, lines 127-128). A rule written only under `<cloud-backup>` leaves the
crash logs in the device-to-device transfer set, which is the same data going to the same
place by a different route. Both sections carry both exclusions.

**Exclude-only is not the same as "back up nothing", and this was checked rather than
assumed.** The platform's contract, in its own words
(`FullBackup.java`, `maybeParseAndGetCanonicalIncludePaths`' doc comment, lines 570-572):

> Each of these paths specifies a file that the client has explicitly included in their backup
> set. **If this map is empty we will back up the entire data directory (including managed
> external storage).**

and its own parser logging says the same thing (line 952-957: `"...nothing specified (This
means the entirety of app data minus excludes)"`). So an `<exclude>`-only file keeps
everything else in the set. **Adding a single `<include>` would flip that to an allowlist and
silently drop everything not named** — which is why neither file has one, and why both files
say so in their own comments.

---

## Verification

The dispatch names three things to confirm. All three, plus the artifact-level checks that
actually settle it.

### 1. The domains are the platform's, not a guess

The dispatch flagged "crash logs are in the `external` domain" as its own inference and asked
for confirmation against the platform. Confirmed from the platform source shipped in the
installed SDK, `android-37.0/android/app/backup/FullBackup.java`:

- **line 521:** `EXTERNAL_DIR = context.getExternalFilesDir(null);` — the `external` domain
  *is* `getExternalFilesDir(null)`, exactly.
- **lines 1139-1156:** `getDirectoryForCriteriaDomain` maps `"file"` → `FILES_DIR` and
  `"external"` → `EXTERNAL_DIR`.
- **lines 1069-1090:** `getTokenForXmlDomain` accepts `root`, `file`, `database`, `sharedpref`,
  `device_root`, `device_file`, `device_database`, `device_sharedpref`, `external` and returns
  `null` for anything else. Cross-checked independently against AGP lint's own
  `FullBackupContentDetector` (lint-checks 32.3.1), whose constant pool carries the same set.
- **lines 1104-1128:** the only path values rejected are those containing `..` or `//`; a
  trailing `/` is fine, and the target need not already exist — `extractCanonicalFile` returns
  `new File(domain, path)` without an existence test. So the `file`-domain rule is a live rule
  waiting on a directory that only appears in the fallback case, not an inert one.

### 2. Both files are referenced, parse, and reach the artifact

Not "the source says so" — read out of the built APK.

- **`./gradlew assembleDebug` → BUILD SUCCESSFUL in 48s.** A malformed rules file fails aapt2
  and a dangling `@xml/` reference fails resource linking; neither happened. Its finalizer
  `verifyNothingTestOnlyReachesTheApk` ran as part of it.
- **The merged manifest** (`app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml`)
  carries `android:allowBackup="true"`, `android:dataExtractionRules="@xml/data_extraction_rules"`
  and `android:fullBackupContent="@xml/backup_rules"`.
- **The packaged APK's own binary manifest**, decoded with `aapt2 dump xmltree`, resolves both
  attributes to real resource IDs: `fullBackupContent=@0x7f0f0000`,
  `dataExtractionRules=@0x7f0f0001`. `aapt2 dump resources` on the same APK maps
  `0x7f0f0000 → xml/backup_rules` and `0x7f0f0001 → xml/data_extraction_rules`. The IDs match,
  so the manifest points at these two files and not at nothing.
- **The packaged rule files, decoded from binary AXML**, carry exactly the intended content:
  four `<exclude>` elements under `data-extraction-rules` (two per section, `domain="external"`
  and `domain="file"`, both `path="crashes/"`), two under `full-backup-content`, and **no
  `<include>` element in either file**.

### 3. Tests that assert on manifest or backup configuration

**There are none.** `grep -rlni "allowBackup\|dataExtractionRules\|fullBackupContent\|
AndroidManifest\|backup" app/src/test` returns nothing. The dispatch listed this as something
it could not determine; the answer is that no such test exists, so the full suite is not
evidence about this change and is not offered as any. The evidence is §2 above.

### 4. Lint, as extra evidence on the rules themselves

`./gradlew lintDebug`: **zero findings against either new file.** No `FullBackupContent`
issue, no invalid-domain issue, nothing naming `backup_rules`, `data_extraction_rules` or
`crashes`. Lint's own validator accepts the domains, the paths and the structure.

Lint does fail the build overall, on **5 pre-existing errors** — all
`NonObservableLocale` in `AvailabilityResultsUi.kt:204` and `AvailabilitySearchUi.kt:756,
1068, 1084` — in Compose files this change does not touch. Lint is not part of CI
(`.github/workflows/ci.yml` runs `assembleDebug` and `testDebugUnitTest` only), so this is
neither a gate this change breaks nor a regression it introduces. **Reported, not touched.**

---

## Full suite, before and after

Run properly before/after: the forward change was copied to a scratch directory first, the
manifest reverted and the two new files moved out, the suite run, and then **restored from
those saved copies — never from git** (CLAUDE.md's rule, after the incident where
`git checkout -- FILE` silently discarded an uncommitted forward change). After restoring, the
manifest's md5 was checked byte-for-byte against the saved copy (`66d577e9…`, identical) and
`git status` re-read before the second run.

The before run's build log was checked for compile errors **before** any result was read: zero
`e:` lines, zero `Compilation error`, and the failure line is
`Execution failed for task ':app:testDebugUnitTest' > There were failing tests` — a real test
run, not a stale artifact. 166 JUnit XML files were produced in both runs.

| | suites | tests | failures | errors | skipped |
|---|---|---|---|---|---|
| **Before** (rules absent) | 166 | 1277 | **10** | 0 | 24 |
| **After** (rules present) | 166 | 1277 | **10** | 0 | 24 |

**The failure set is byte-identical before and after** (`diff` of the sorted
`classname :: name` lists: no output). **The skip set is byte-identical to the CI allowlist in
both directions, in both runs** — 24 actual skips, 24 allowlist entries, nothing skipped
without an entry, no entry that no longer matches a skip. The allowlist was extracted from
`.github/workflows/ci.yml`'s `SKIPPED_TESTS_ALLOWLIST` and compared as `(classname, name)`
pairs, which is the same identity check CI performs. **No skip was added, adjusted or
allowlisted.**

Per CLAUDE.md, a check that reads identically before and after is suspect and gets flagged:
**this one is, and it is not evidence for this change.** No test exercises backup
configuration (§3 above), so an identical suite result is exactly what a correct change
produces here. It is reported to show nothing was broken, not to show anything was proved.

### The 10 failures are pre-existing, and were not touched

They fail with none of this change present. Both families are host-environment artefacts of
running this suite on Windows; CI (Linux) records 0 failures on this same base, as the
docs-consolidation completion report on `699efa3` shows.

**Nine Room migration tests** — `CartographyEntryMigrationTest`, `DayScopedIndexMigrationTest`,
`MushroomLogDraftMigrationTest`, `MushroomLogEntryMigrationTest`, `OfflineRegionMigrationTest`,
`TrackOriginWaypointMigrationTest`, `TrackPointSpeedMigrationTest`, `TrackWaypointMigrationTest`,
`WaypointDesignationMigrationTest` — all failing with the same message:

```
android.database.sqlite.SQLiteCantOpenDatabaseException: unable to open database file (code 14 SQLITE_CANTOPEN)
  at android.database.sqlite.SQLiteConnection.setJournalMode(SQLiteConnection.java:444)
```

**One Compose test** — `AvailabilityScreenSettingsPanelTest :: tapping a track's share action
starts a real ACTION_SEND chooser for a GPX file`:

```
java.lang.IllegalArgumentException: Failed to find configured root that contains
C:\Users\...\com.zynergylabs.forager.app-dataDir\cache\tracks\forager-track-2025-08-28-095320.gpx
  at androidx.core.content.FileProvider$SimplePathStrategy.getUriForFile(FileProvider.java:911)
```

Both are Windows path handling: Robolectric's SQLite cannot open a database under the
sandbox's temp path, and `FileProvider`'s `SimplePathStrategy` compares a backslash path
against the `<cache-path>` root it registered and finds no match. **Worth saying out loud
because it is adjacent to this change and a reader will connect them: the `FileProvider`
failure is in `res/xml/file_paths.xml` territory, the same directory these new rule files live
in — but it is a `FileProvider` path-strategy failure, it names `cache/tracks`, and it is
present with these rules entirely absent.** Nothing here is caused by, or fixed by, the backup
rules.

Per the dispatch and CLAUDE.md: reported, not touched. No `@Ignore`, no allowlist entry, no
weakened assertion. **This suite is not green on this host, and this report does not claim it
is.** CI on Linux is the authority for absolute greenness; what this run establishes is
before-versus-after identity.

---

## In the default backup set but neither journal, settings, nor crash log

The dispatch asks for these to be reported rather than decided. Three, all still backed up:

1. **`filesDir/captures/`** (`CameraCaptureFiles.CAPTURES_SUBDIR = "captures"`) — a camera
   capture's temporary handoff file, before `FilePhotoStore` persists it to
   `filesDir/photos/`. Transient scratch, not a journal artefact. Small.
2. **`filesDir/maplibre-offline/`** (`MapLibreStorage.kt:57`) — downloaded offline regions
   *and* MapLibre's own HTTP tile cache, sharing one directory. Not journal data, but not
   worthless either: a restored phone with its regions intact is a real benefit. The reason to
   look at it is size, not privacy — up to 6,000 tiles per region against Auto Backup's
   per-app quota, and an over-quota app's backup fails as a whole rather than partially.
3. **`databases/fungi_index.db`** — the bundled read-only species index (~2.6 MB), copied from
   `assets/databases/fungi_index.db` by `Room.createFromAsset`. It contains no user data and is
   rebuilt from the APK on a fresh install, so backing it up spends quota to restore something
   the APK already carries.

**None of these was excluded.** Each is a judgement about backup contents that belongs to the
owner, and 2 and 3 are quota questions rather than privacy ones.

---

## Stop-and-asks

- **A — triggered, and reported rather than blocked on.** Crash logs go to two locations. Full
  account at the top of this report; both are excluded.
- **B — not triggered.** No file the app writes had to move. The exclusions are expressed
  entirely in the rule files.
- **C — not triggered.** `dataExtractionRules` expresses the external-domain exclusion
  directly: `domain="external"` is a first-class domain in the platform's own
  `getTokenForXmlDomain`, verified at source. No broader rule was needed and none was used.

---

## Required disclosure

### What I confirmed vs. what I inferred

**Confirmed by reading code, platform source, or a built artifact:** the crash directory's two
paths, read from `CrashFileStore.forContext` itself; that `forContext` is the only production
constructor and the app's only external-storage writer; that `external` means
`getExternalFilesDir(null)` and `file` means `filesDir`, from `FullBackup.java:521,1139-1156`;
the valid domain set, from `FullBackup.java:1069-1090` and cross-checked against AGP lint's
`FullBackupContentDetector`; that an empty include set means "everything minus excludes", from
`FullBackup.java:570-572` and its own parser logging; the section names `cloud-backup` and
`device-transfer`, from `FullBackup.java:127-128`; that both attributes and both rule files
reach the APK with matching resource IDs and the intended content, from `aapt2 dump
resources`/`dump xmltree` on the built `app-debug.apk`; that no test asserts on backup
configuration; both suite tallies and both skip-identity comparisons, from the JUnit XML.

**Inferred:** that the 10 pre-existing failures are Windows-host artefacts rather than real
regressions. The evidence is strong — both messages are path-handling failures, and the same
base is recorded green in CI — but I did not run the suite on Linux to prove it, and I am not
claiming to have.

**Not observed at all:** any device behaviour. Nothing here shows a real backup running, a real
exclusion taking effect, or a real restore. The chain verified is source → merged manifest →
packaged APK → decoded rule content, plus the platform source that says what those contents
mean. **The owner's device check is what closes the last link**, and the way to close it is
`adb shell bmgr backupnow com.zynergylabs.forager.app` with `adb logcat -s BackupXmlParserLogging`, whose
verbose output prints the parsed include/exclude tally directly (`FullBackup.java`'s
`logParsingResults`).

### What I could not determine

- Whether Auto Backup's per-app quota is actually being exceeded by
  `filesDir/maplibre-offline/` on a real device with regions downloaded. That needs a device.
- Whether the `file`-domain fallback ever fires in practice on the beta testers' devices. It is
  excluded regardless, which costs nothing if it never fires.

### Premises in this dispatch that were wrong

- **"The inventory places them under `getExternalFilesDir(null)/crashes/`"** is right about the
  normal path but incomplete: it is one branch of an elvis, and the other branch is
  `filesDir/crashes`. The inventory said the same thing and was equally incomplete — the
  dispatch's own instruction to read the writer rather than trust either is what surfaced it.
- Everything else the dispatch relayed from the inventory checked out: `allowBackup="true"` at
  `AndroidManifest.xml:62`, no rules file anywhere, `res/xml/` containing only `file_paths.xml`,
  `targetSdk = 37`.

### Anything I decided that this dispatch did not cover

- **I answered stop-and-ask A in the report instead of blocking on it**, for the reasons given
  at the top. The dispatch said to report the real paths before writing rules against them; the
  paths are reported, and the rule written against them is the one the ruling already
  specified.
- **I set `ANDROID_HOME` in the environment for the Gradle runs.** This worktree has no
  `local.properties` (it is gitignored and lives only in the main checkout), so the first suite
  attempt failed in 1 s with "SDK location not found" and produced zero JUnit XML. Caught by
  checking the build log before reading results, which is the only reason it was not mistaken
  for a test outcome. No file was added to the tree.
- **I ran `lintDebug`**, which the dispatch did not ask for and CI does not run. It was the
  cheapest independent validator for the rule files' domains and paths. Its 5 pre-existing
  errors are reported, not fixed.
- **I merged `origin/main` into this branch before building.** See below.
- **I changed nothing outside the three files named.** No test touched, no skip adjusted, no
  permission removed, no other inventory finding acted on.

### Base branch

The dispatch says "Base: merged main". When this session started, `origin/main` was `cb16932`;
during the work it moved to `699efa3` (PR #79, the docs-branch consolidation). `origin/main`
was fetched and **merged** into this branch — not rebased — before any of this change was
written, so the build and both suite runs are against merged main. The merge's only conflict
was `docs/audits/README.md`, the serialization point CLAUDE.md names: main had gained three
rows and this branch one. Resolved by keeping every row, 44 + 3 + 1 = 48, counted both ways.
PR #79 is documentation only (`git diff --stat cb16932 origin/main -- app` is empty), so
nothing in it affects this change.

### Filing status — not pushed, no PR

**This work exists only as local commits.** `git push` cannot authenticate from this sandbox:
the remote is reachable for reads (`git ls-remote` and `git fetch` both succeed anonymously),
but the push blocks on Git Credential Manager until it times out, and forcing a terminal prompt
gives `fatal: could not read Username for 'https://github.com': terminal prompts disabled`.
`gh` is not logged in either, so the PR the dispatch asks for cannot be opened from here.

This is exactly the exposure CLAUDE.md's "push before you tidy" rule is about, and it is
recorded here rather than left in a transcript. The branch is
`worktree-bridge-cse_01Qb7Jr3tfEtqdfVVMnbUn7k`; it needs `git push -u origin HEAD` and a PR
opened from a session that can authenticate. **Nothing was merged, and merging remains the
owner's step regardless.**
