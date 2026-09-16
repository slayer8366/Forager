# The 43 Windows-host test failures, classified by mechanism and recorded as a baseline by test ID

> **Superseded in part, 2026-09-15 (same day) — see §6.** §3's environment findings (*"WSL is not
> installed"*, *"this host cannot run the suite under Linux today"*) and §5's *"Not run: the suite on
> Linux"* were accurate when written and no longer hold. Local runs moved to a **native Ubuntu 26.04
> install**, not the WSL2 §3 recommended, and the full suite has now run there at `1d81d71`: 0 failures,
> 24 skipped, all 43 baseline IDs ABSENT. §1's classification is unchanged — it is now measured rather
> than inferred. Nothing below has been edited.

**Date:** 2026-09-15 · **Branch:** `claude/new-session-vto65i` (PR #102) · **Head classified:** `017d6ca` · **Base measured:** `b586195` · **Host:** Windows 11 Home 26200, Android Studio JBR, Robolectric 4.16.1, `%TEMP%` = `C:\Users\<user>\AppData\Local\Temp`.

**The dispatch.** The local suite on this Windows checkout reports 43 failures and every report has been arguing in prose that none of them are ours. Classify all 43 by root cause with a representative test and line; say for each group whether it is a test-harness assumption or a production portability bug, and stop if any is the latter; record the baseline as data under `docs/`, with a cheap comparison if one exists; and answer whether this host can run the suite under Linux. No production changes, no test changes, no fixes.

**One-paragraph outcome.** Three mechanisms, not four signatures, and every one is traced to a line in a library running under a Windows JVM, not to this app: **A**, `androidx.core`'s `FileProvider` matches a file to its configured roots with a hard-coded `/` (20 failures, 3 of them a panel timing out downstream of it); **B**, `androidx.sqlite`'s `SupportSQLiteDriver` compares database names with `substringAfterLast('/')` (12); **C**, Windows' `MAX_PATH` on the Robolectric temp directory, which is named after the test class and method, giving database paths of 260 to 272 characters (11). **All three are harness-only.** No production code builds a path by string concatenation (grep, §1.4), the app hands `File` objects to both libraries, and neither library ever runs on Windows in production. The baseline is `docs/windows-host-test-baseline.tsv`, 43 IDs with their group; `scripts/compare-test-baseline.sh` diffs a run against it by ID and was checked four ways. **Identity, not count:** the 43 at `b586195` and the 43 at `017d6ca`, both full-suite runs in this checkout, are the same 43 by test ID, `comm -3` empty. **This host cannot run the suite under Linux today: WSL is not installed.** It can be, at the cost of an admin install and reboot plus roughly eight gigabytes of the 21 free; §3 prices it. And one correction to the previous addendum, §4: the "deep worktree path" explanation of the 43 was wrong.

---

## §1 — Classification

Every failure's message and stack was read from the JUnit XML of the head run; the library lines were read from the sources jars in the Gradle cache (`core-1.19.0-sources.jar`, `sqlite-framework-android-2.6.2-sources.jar`), not recalled.

### Group A — `FileProvider` matches roots with a hard-coded `/` (17 direct)

**Mechanism: path separator.** `androidx.core.content.FileProvider.SimplePathStrategy.getUriForFile` canonicalises the file (`FileProvider.java:895`) and each configured root (`:882`), then asks `belongsToRoot`, whose whole test is `filePath.startsWith(rootPath + '/')` (`FileProvider.java:972`). On Windows `File.getCanonicalPath()` returns `C:\Users\…\com.zynergylabs.forager.app-dataDir\files\captures\<uuid>.jpg` with backslashes, so no root ever matches, `mostSpecific` stays null, and `:911` throws `Failed to find configured root that contains <path>`. Robolectric's roots are under `%TEMP%\robolectric-<Class>_<method><digits>\com.zynergylabs.forager.app-dataDir\`; the file is under exactly that root; only the separator differs.

**Representative:** `CameraCaptureFilesTest#newCapture issues a content URI under captures and creates the directory but not the file` → `CameraCaptureFiles.newCapture` (`CameraCaptureFiles.kt:53`, `FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)`) → `FileProvider.java:911`. The app's line passes a `File`; the divergence is `:972`.

**Members (17):** `CameraCaptureFilesTest` 1, `FilePhotoStoreTest` 1, `InAppCameraDialogTest` 8, `InAppCameraHostTest` 3, `AvailabilityScreenInAppCameraTest` 2, `AvailabilityScreenSettingsPanelTest` 1 (the GPX share, the "tenth failure" of the 2026-09-09 note, same message), `DiagnosticsPanelTest` 1. Every one reaches `getUriForFile` through `CameraCaptureFiles.newCapture`, `FilePhotoStore`, `TrackGpxExporter`'s share or the Diagnostics panel's share.

**Call: test-harness assumption**, in a library. `FileProvider` is Android's content-URI bridge and runs only on Android, where paths are `/`-separated; the `/` at `:972` is correct there. It runs on Windows only because Robolectric runs it on the host JVM. Nothing in this app assumes a separator: it builds `File(capturesDir, "<uuid>.jpg")` and hands the object over. A contributor on macOS would not see this (`/` paths); only Windows does.

### Group A2 — downstream of A: the Diagnostics panel starts no chooser (3)

**Mechanism:** the same `FileProvider` failure, caught. `DiagnosticsPanel.kt:287` wraps `getUriForFile` in `runCatching` on `Dispatchers.IO`; on failure (`:298–301`) it logs and shows an error and starts no `ACTION_SEND` chooser. The three tests then wait in `awaitStartedActivity` (`DiagnosticsPanelTest.kt:260`, `waitUntil(5_000)` on `nextStartedActivity`) for an activity that is never started, and time out. Not a fourth mechanism: the panel is behaving exactly as designed for a `FileProvider` failure, and the timeout is what that design looks like from the test.

**Representative:** `DiagnosticsPanelTest#sharing a photo starts an ACTION_SEND chooser with a content URI naming that file` → `DiagnosticsPanelTest.kt:159` → `:260`.

**Members (3):** the two share tests and `listing, viewing and sharing leave both directories byte for byte as they were`.

**Call: test-harness assumption**, by inheritance from A.

### Group B — `SupportSQLiteDriver` compares database names with `substringAfterLast('/')` (12)

**Mechanism: path separator.** `SchemaMigrationTest` drives Room's `MigrationTestHelper` (`SchemaMigrationTest.kt:56`, `:141` `helper.runMigrationsAndValidate(name, …)`), which opens the database through Room's connection manager (`RoomConnectionManager.kt:75`) by its **full path**, `context.getDatabasePath(name).path`. `androidx.sqlite.driver.SupportSQLiteDriver.open` accepts that only if `openHelperDatabaseName == fileName || openHelperDatabaseName.substringAfterLast('/') == fileName.substringAfterLast('/')` (`SupportSQLiteDriver.android.kt:44–48`). With a Windows path there is no `/`, `substringAfterLast('/')` returns the entire path, and `require` throws `This driver is configured to open a database named 'm9.db' but 'C:\…\databases\m9.db' was requested`. The other migration classes never reach this line: they open through `Room.databaseBuilder` with the database *name*, through the framework open helper and no driver wrapper, so nothing compares a path; `SchemaMigrationTest` is the one class that goes through the helper's driver wrapper, which passes the full path.

**Representative:** `SchemaMigrationTest#9 to 10 - day-scoped indexes are added and rows are untouched` → `SchemaMigrationTest.kt:141` → `SupportSQLiteDriver.android.kt:48`.

**Members (12):** all twelve `SchemaMigrationTest` cases, `m4.db` through `m14.db` and `chain.db`.

**Call: test-harness assumption**, in a library. `SupportSQLiteDriver` is Room's bridge to the Android framework driver, Android-only in production; the `/` is right there. The app's own database code passes names to `Room.databaseBuilder` and `getDatabasePath`, never a hand-built path.

### Group C — Windows `MAX_PATH` on the Robolectric temp directory (11)

**Mechanism: path length, and the variable is the test method's name.** The exception now carries the path (it did not in the 2026-09-09 note's record, which is why that note could not prove this): `SQLiteCantOpenDatabaseException: … could not open database] 'C:\Users\metal\AppData\Local\Temp\robolectric-<Class>_<method with spaces as underscores><digits>\com.zynergylabs.forager.app-dataDir\databases\<name>.db'`. Robolectric names each test's temp directory after the class and the full method name, and these classes' backtick names are 94 to 157 characters. Measured from the messages, every failing path is **260 to 272 characters**: `CartographyEntryMigrationTest` 267, `DayScopedIndexMigrationTest` 266, `LogPhotoMigrationTest` 260, `MushroomLogDraftMigrationTest` 269, `MushroomLogEntryMigrationTest` 269, `MushroomLogMigrationTest` 263, `OfflineRegionMigrationTest` 265, `TrackOriginWaypointMigrationTest` 272, `TrackPointSpeedMigrationTest` 267, `TrackWaypointMigrationTest` 263, `WaypointDesignationMigrationTest` 271. Windows' classic limit is 260 including the terminator, so 259 usable; SQLite's Win32 `open` on a longer path returns `SQLITE_CANTOPEN`, which is code 14 in the message. The two classes that **passed** on 2026-09-09 (`LogPhotoMigrationTest`, 260 now; `MushroomLogMigrationTest`, 263) fail now because the application id, and so the `-dataDir` segment, grew by twelve characters since — the flip the baseline memory predicted. `SchemaMigrationTest`'s method names are 61 characters and its paths are under the limit, which is why it reaches group B's check instead. The randomised digit suffix varies in length, which is the intermittency the 2026-09-09 note recorded for the two classes then nearest the limit.

**Representative:** `CartographyEntryMigrationTest#a pre-existing mushroom log entry survives the 10 to 11 migration intact, and every new cartography_entry table round-trips through the real repository` (151-character name, `CartographyEntryMigrationTest.kt:67`) → `getDatabasePath` (`:57`) → `SQLiteConnection.open` (`SQLiteConnection.java:276`), path 267 characters.

**Members (11):** the eleven `*MigrationTest` classes other than `SchemaMigrationTest`, one test each.

**Call: test-harness assumption**, in the harness itself. The length comes from Robolectric's naming of the sandbox plus the host's temp path; the app's database names are 5 to 38 characters and `getDatabasePath` on a device is `/data/user/0/<package>/databases/<name>`. Production never builds this path. (Windows can lift the limit with the `LongPathsEnabled` registry policy, and the JVM would then need the same; noted, not done.)

### The call across all three

**No group is a portability bug in production code.** Checked rather than assumed: a grep of `app/src/main` at `017d6ca` for `File("…/…")`, `+ "/"`, `"$x/"` interpolation, `File.separator` and `separatorChar` finds **nothing** — the app constructs paths only as `File(parent, child)` and passes the objects on. Every divergence is in `androidx.core`, `androidx.sqlite` or Robolectric, exercised on a Windows JVM they were not written for. So this dispatch does not stop, and nothing here bites a contributor on macOS or Linux; it bites Windows, and only under Robolectric.

**Trivially fixable, noted and left alone:** A and A2 would go away with a Windows-aware `FileProvider` shadow or by running those tests only on `/`-separated hosts; B with a shadow of the driver's check; C by shortening eleven backtick method names below the limit or by a `LongPathsEnabled` policy on the host. None was done: fixing is a separate decision, and the answer in §3 may make all of them moot.

---

## §2 — The baseline, as data

**`docs/windows-host-test-baseline.tsv`** — 43 lines, `group<TAB>classname#method`, with a header naming the host, the two measured commits and the three mechanisms. Generated from the head run's JUnit XML, not typed.

**Measured at a known-good base and at head, in the same checkout:** full suite at `b586195` (checked out detached in this worktree; 194 suites, 1515 tests, 43 failures, 24 skipped; XML timestamps 18:11–18:14Z) and at `017d6ca` (198 suites, 1540 tests, 43 failures, 24 skipped). Failing IDs extracted from both with the same script and compared with `comm -3`: **empty in both directions, 43 in the intersection.** That is the identity claim, by ID; the two runs also differ by four classes and 25 tests, which the count alone would not have distinguished.

**`scripts/compare-test-baseline.sh [results-dir] [baseline]`** — bash, perl, sort and comm, all present here and on Linux CI. Prints NEW (failing, not in the baseline), ABSENT (in the baseline, did not fail) and counts, exits 1 only on NEW. Checked four ways: head results → 0 NEW, 0 ABSENT, 43/43, exit 0; base results → the same at 194 suites; the baseline with one line removed → exactly that ID reported NEW, exit 1; the results with one failing class's XML removed → exactly that ID reported ABSENT, exit 0. ABSENT does not fail the check on purpose: group C is intermittent at the limit by the record, and a dropped line is a decision. On Linux the whole baseline reads ABSENT and nothing NEW, which is the correct reading there. Cheap enough to keep; the file is the reference and the script is a convenience over it, so a report can now say "43, all in the baseline, none new" and mean it.

---

## §3 — The environment question

**Can this host run the suite under Linux today? No.** `wsl.exe --status` answers *The Windows Subsystem for Linux is not installed.* No distribution, no WSL2 kernel. Docker Desktop would need WSL2 as well.

**Can it? Yes, and the prerequisites are met.** Windows 11 Home 26200 supports WSL2 (Home is not a limitation; Hyper-V proper is, but WSL2 does not need it). `systeminfo` reports a hypervisor detected and virtualization-based security running, so hardware virtualization is enabled in firmware. 12 GB RAM: WSL2 takes half by default, and this suite's Gradle plus Robolectric workers fit in 4–6 GB, so a `.wslconfig` with `memory=6GB` is enough. Disk is the tight number: **21 GB free of 238.** A Ubuntu distribution is about 1.5 GB, Temurin JDK 21 (CI's) 0.3 GB, the Android SDK that `scripts/setup-android-sdk.sh` installs about 1.5 GB, this repository cloned inside the Linux filesystem plus its build outputs about 2 GB, and the Gradle cache 3–4 GB: roughly **8 GB**, leaving 13. Workable, not comfortable.

**What it costs.** One admin session: `wsl --install -d Ubuntu`, a reboot, a first-launch user setup; then inside it, JDK 21, `scripts/setup-android-sdk.sh` (it already targets `/opt/android-sdk` and the CI build-tools version), a clone of the repository **into the Linux filesystem** (`~/`, not `/mnt/c/…`: the Windows mount gives the same `\`-free paths but a slow and case-insensitive filesystem, and a clone is what CI runs against anyway), and `./gradlew testDebugUnitTest`. About an hour of wall clock once the reboot is done, plus the first Gradle download. None of it can be done from this session: the install needs elevation and a reboot, both the owner's.

**The recommendation, plainly.** If the intent is for local runs to be the merge gate, install WSL2 and run there; the baseline then becomes the record of a solved problem, and the script stays only for anyone who still runs on the Windows JVM. If the intent is that CI stays the gate, as it is now, the baseline file and the script are sufficient on their own and cost nothing to keep. Either way the classification in §1 does not change: these are three harness mechanisms, and moving to Linux removes the harness condition rather than fixing anything in the app.

---

## §4 — Correction to the previous addendum, and what else was wrong

The trace addendum in the window-lock report (2026-09-15, "the trace") explained the 43 as *"four times the documented Windows baseline of ten to twelve because this worktree sits under `.claude\worktrees\<44-char id>\`, far longer than the main checkout."* **That was wrong.** The Robolectric temp directory lives under `%TEMP%`, and none of the 43 paths contains the checkout path at all (§1, group C: the variable is the test method's name, and the growth since 2026-09-09 is twelve characters of application id). The count grew from ten to 43 because tests were added: the twelve `SchemaMigrationTest` cases on 2026-09-13, the in-app camera classes from 2026-09-14, the Diagnostics panel on 2026-09-15 — all of which reach `FileProvider` or the driver check on this host. The same addendum then measured "path length is not the driver" for seven classes at a short path and recorded the identical counts, which was correct and already contradicted the sentence before it; the sentence stood anyway. The correction is appended to that addendum and recorded in the index row for this report.

Also recorded: the 2026-09-09 note's standing instruction not to upgrade its claim about `MAX_PATH` was right to stand until now. What upgrades it here is not a better argument but the path in the message — Robolectric 4.16.1 under SDK 36 puts it there, and the lengths are read off eleven of them.

---

## §5 — What was and was not done

- Added: `docs/windows-host-test-baseline.tsv`, `scripts/compare-test-baseline.sh`, this report, an index row, a correction paragraph on the trace addendum. Nothing under `app/`.
- No production change, no test change, no skip, no guard. `git diff --stat` on `app/` is empty.
- Not run: the suite on Linux (no WSL). CI's green on the same commits is the record for that.
- Not proven: that `LongPathsEnabled` would clear group C on this host, or that a Windows-aware shadow would clear A and B. Both named as the cheap fixes they are, for a separate decision.

---

## §6 — Superseded 2026-09-15 (same day): the host moved to native Linux, and the suite ran there

Recorded as a new section rather than edited into §3, per CLAUDE.md: a later record supersedes an
earlier one rather than rewriting it. Everything in §3 was accurate when written; what superseded it
is the event §3 was pricing.

**Withdrawn, with what they said.** The one-paragraph outcome: *"This host cannot run the suite under
Linux today: WSL is not installed."* §3: *"Can this host run the suite under Linux today? No.
`wsl.exe --status` answers The Windows Subsystem for Linux is not installed."* §5: *"Not run: the suite
on Linux (no WSL). CI's green on the same commits is the record for that."* All three were true of the
Windows host on the date written; none is true of the machine local runs now happen on.

**The move went further than the recommendation.** §3 recommended *"install WSL2 and run there"*. What
happened instead is a native **Ubuntu 26.04.1 LTS** install on its own disk — kernel `7.0.0-31-generic`,
8 cores, 11 GB RAM, NVMe. Not WSL2, and not a Windows mount: there is **no `/mnt` on this machine at
all**, so the slow, case-insensitive `/mnt/c/…` path §3 warned against cannot be taken even by accident.
The WSL2-specific reasoning in §3 — Home edition, Hyper-V, a `.wslconfig` memory cap — no longer applies
to anything. The `\`-free paths that clear groups A and B are now the filesystem's own, not a mount's.

**What it actually cost.** §3 priced it at *"roughly 8 GB"*, itemised. Measured after a full build and a
full test run: Android SDK 796 MB, Temurin JDK 21 346 MB, Gradle caches 2.6 GB, Robolectric's android-all
jars in `~/.m2` 481 MB, the clone plus its build outputs 396 MB — **about 4.5 GB**, against 48 GB free.
The estimate was high by roughly half, and §3's "disk is the tight number" caveat does not apply here.

**The measurement §3 said could not be taken.** Full suite at `1d81d71` — the commit this baseline was
established against — with the toolchain matched to CI (Temurin 21, Gradle 9.7.0, cmdline-tools 11076708,
build-tools 37.0.0, `platforms;android-37.1`), installed by `scripts/setup-android-sdk.sh` itself, run
from a clean state:

- **198 suites, 1540 tests, 0 failures, 0 errors, 24 skipped.**
- The 24 skips are **identical by test ID** to CI's `SKIPPED_TESTS_ALLOWLIST`: 0 skipped without an entry,
  0 entries not actually skipped — the both-directions identity CI's summarize step enforces, checked
  here by parsing the allowlist out of `ci.yml` and diffing it against the run's JUnit XML.
- `scripts/compare-test-baseline.sh`: **NEW 0, ABSENT 43**, `run=0 baseline=43 overlap=0 suites=198`,
  exit 0 — every ID in the baseline absent, nothing new, which is the reading the script's own header
  predicts for Linux.
- The APK reports `versionCode=692` against `git rev-list --count HEAD` = 692, and
  `versionName=1.0.692+g1d81d71c` — no `.dirty` suffix, no shallow-clone fallback, so the build identity
  confirms a full clone with a clean tree.

**Same sample, both hosts — which is what makes this a comparison and not two readings.** The Windows
run recorded in §2 was at `017d6ca`, which is an ancestor of `1d81d71` by two commits with **zero files
changed under `app/src/test/`** between them, and it counted **198 suites, 1540 tests** — the totals this
Linux run reports exactly. So the same 1540 tests ran on both hosts; 43 of them failed on Windows and none
fail here, with the host as the only variable. That closes the question CLAUDE.md insists on asking of any
check — what sample did it actually run on, and did that sample include the cases that could have failed
it — affirmatively: the 43 that could have failed are present in this run, and passed.

That is §1's claim measured rather than inferred: the 43 are the host's, and removing the host condition
removes all 43 without touching the app. Nothing under `app/` changed to get here; `git diff --stat` on
`app/` is empty.

**A bug in the comparison script, found by running it here and fixed in the same pass (owner's call,
2026-09-15).** `scripts/compare-test-baseline.sh` sorted both sides with `sort` but compared them with
`comm`. `sort` honours `LC_COLLATE`; `comm` compares bytes and does not. Under `en_US.UTF-8` the collation
folds case, so `...InAppCameraDialogTest#Done dismisses, ...` sorts among the lowercase method names while
comm expects it ahead of them (`D` is 0x44, `a` is 0x61) — one line out of 43, which is enough. Handed
sort's order, comm's merge walks off and mis-classifies.

**Measured, not argued.** A synthetic run whose single failure *was* a baseline ID — the case the script
exists to recognise — came back `NEW: 1`, a regression that is not one, while that same ID was
simultaneously listed under `ABSENT`, "did not fail", and the script exited 1. Two contradictory wrong
answers in one report, on the Windows host, about the one question the script is for.

**Why it survived until now, and why it is closed rather than recorded.** A green run cannot show it: with
no failures the run side is empty, so `NEW` is empty and `ABSENT` is the whole baseline in any order, and
the verdict is right by accident. The Linux run above is green, so it would have stayed invisible here too
— it would have waited for the first host that actually had failures, which is the only host whose answer
matters. That is the family §4 and CLAUDE.md name: a check decoupled from what it checks by a step in
between, with nothing in its own output saying so. The owner's ruling was to close it now rather than let
it sit behind a green wall.

**The fix and its controls.** `export LC_ALL=C`, once, near the top, so a `sort` or `comm` added later
cannot reintroduce it; every ID is ASCII and the perl is byte-oriented, so its output is unchanged. Three
controls, because a fix that reported "no failures" unconditionally would pass the first two: the
baseline-only run now reads `NEW 0, ABSENT 42, overlap 1`, exit 0 (correct, and different from the
unfixed output, so the change bites); a run carrying a baseline failure *and* a genuine regression reads
`NEW 1` naming exactly the regression, `ABSENT 42`, exit 1, so real regressions are still caught; and the
real Linux run above is unchanged at `NEW 0, ABSENT 43`, exit 0, with the warning gone.
