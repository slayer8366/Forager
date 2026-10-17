# Elder Ford: audit of the second promotion, pre-main `b91de824` into main `969ecf0a` (RECORD -1210)

**Verdict: Pass with items.** Nothing found that risks a user's records, safety or trust. Four notes, none of which a user meets in the app; one owed phone check, marked "not seen on a phone" below, with the one mechanism it exists to catch named. Read-only session: no files written, no PR, no merge, no Gradle.

## Baseline, confirmed

- `git ls-remote`: main `969ecf0a`, pre-main `b91de824`, both equal to the fetched heads. 25 commits, 70 files (+1377/−142).
- CI at `b91de824`: run 39077884108, all three jobs green ("Build, test, publish APK", "Android lint (debug)", "Release compiles and packages (unsigned)"). The `ci.yml` diff is pure addition (two new jobs appended after line 483); the skip allowlist is untouched. -1196 records 4,936 tests, 0 failures, 24 skipped, the same 24 as pre-main's baseline.
- Dispatches read from source: `prompts/preserved/2026-10-17-03.md` and `-04.md`; RECORD -1192 to -1210 on records-after-173; PR #246 body.

## Mechanism prediction (written before the diff) and what the diff showed

Predicted: mechanical lint fixes (locale, window size, Android 12 backup rules), two CI jobs, documents; failure points: window class at the compact/medium boundary, mixed-language text, backup rules reversing -116, the backup file handler, camera capability reading, release-compile vs the signed build, the tab crossfade.

Result: the shape held. Of the seven failure points, (3), (4), (5), (7) were read and are clean (see "What I read"); (6) is as predicted, signing only, R8 untouched; (1) and (2) are addressed below. The prediction missed the one thing that turned out to matter most: because `MainActivity` handles rotation itself (`AndroidManifest.xml:176`, `configChanges="orientation|screenSize|screenLayout|keyboardHidden"`), the new window-size reads depend on `containerDpSize` updating *without* an Activity recreate, and CI cannot show that (item A).

## Not seen on a phone (owed: Hazel's seven-step check, owner on 2026-10-18, S22 first)

**A. Rotation updates the window size without a recreate.** `WindowWidthClass.kt:55`, `ShortWindow.kt:31`, `CartographyEntryReportScreen.kt:397`, `LogEntryReportScreen.kt:378` now read `LocalWindowInfo.current.containerDpSize` instead of `Configuration.screenWidthDp/HeightDp`. `MainActivity` is not recreated on rotation, so the Journal's short-window landscape layout (L2 map cap, sideways cards), the width class, and the find page's 60% photo cap all depend on Compose updating `containerDpSize` in `onConfigurationChanged`. Hazel's doc comment (`WindowWidthClass.kt:42-50`) says it does, citing Compose UI 1.12 source; I did not read that source and have not seen it on a phone. The four rotation tests that simulate a rotation now *supply* the window via `ProvideConfigurationAndWindow` (`FixedWindowInfo.kt:46-56`), so CI exercises the classifier, not the update path. If it fails: rotating the phone leaves the Journal in the portrait layout, or the find-page photo keeps the old cap, until the app is reopened. The user would read that as broken ("Don't make me think"). This is the one step of the phone check that carries a real mechanism; the others (display size, transfer) are confirmations.

**B. Android version boundary.** On Android 14 and below the old configuration figures excluded system bars; `containerDpSize` includes them (targetSdk 37 makes the configuration include them on 15+ too). So on an older-Android phone the short-window test compares a figure taller by the bar heights, and the photo/map caps grow by 60%/40% of that. A phone with a landscape height near 480 dp could flip classification; the S22 Ultra's landscape height is well under it either way. Not an item; worth one glance at the S22's Android version during the check.

**C. Phone-to-phone transfer carries nothing** (`data_extraction_rules.xml`, `AndroidManifest.xml:148-150`). `BackupAndTransferRulesTest` reads the merged manifest and the compiled rules, which is the right check for "the rule is not silently inert"; whether a real new-phone setup transfers nothing is device-only, as the manifest comment itself says.

## Items (owner ships or holds each; none is in the app)

**1. README names a `crash` package and "crash files" that no longer exist.** `README.md:157` (pre-main): `.../crash/` listed among the app's packages, glossed "crash files". `git ls-tree origin/pre-main app/src/main/java/com/zynergylabs/forager/app/` shows no `crash` directory, and `git grep -il crash -- app/src/main` finds no crash-handling code (the hits are words in comments and unrelated identifiers). The repository is PUBLIC (`gh repo view`), so this is public text, and it says the opposite of what the website was corrected to say today (-1201, -1203: the app keeps no crash traces). Principle: A7, what Forager says in public stays true. PR #249 (-1205) set out to remove the dev-tools lines and corrected the `ui/` row on the line above but left this row. The README is not the privacy policy or the store text, so I weigh it as a note, not a block. One line to fix, documents only.

**2. `known-bugs.md` closing line is stale.** `docs/process/known-bugs.md:29`: "confirm the list starts from these two" — the table now has three rows. Documents only.

**3. Known-bug row 3 may describe an unreachable state.** Row 3 says the search summary's month "can stay in the old language until the screen redraws" and honestly marks it "not seen on a phone". `MainActivity`'s `configChanges` (`AndroidManifest.xml:176`) does not include `locale`, so a system or per-app language change recreates the Activity, and `activeSearchSummary` (`AvailabilitySearchUi.kt:685`, still `Locale.getDefault()`) is recomputed with the new default. The same reasoning means the five NonObservableLocale fixes change nothing a user sees on a phone: the words were already right after the recreate. The row is honest as written; I'd leave it until a phone shows either outcome, and note that the "small follow-up fix" (-1197) is hygiene, not a user-visible repair. (`GpxImportActivity`, `:204`, does handle `locale` itself, but it shows no month.)

**4. The seven-step phone check is not written down in the repository.** -1196 and PR #246's body both say the steps are "in Hazel's report"; I searched records-after-173, lint-fixes and pre-main (`docs/`, `prompts/`, `RECORD.md`) and PR #246's comments, and found no file holding them. The QA philosophy (§3) says each check is written before it runs, with a pass condition and evidence per step, and CLAUDE.md's "Push before you tidy" applies to a check that lives only in a session transcript. For the planner: file the steps (e.g. under `prompts/preserved/` or `docs/audits/`) before the owner runs them tomorrow, so the result can be read against a written pass condition. Not a verdict item.

## What would change for a user in the production build made from main after this

- **New-phone setup on Android 12 and later now carries no Forager data** (item C). Lint's position, quoted in the manifest comment, is that `allowBackup="false"` alone no longer stops device-to-device transfer there; the live build may therefore have transferred data in that one flow, and the next one will not. This is the owner's 2026-09-09 ruling (-116) carried out, not reversed, and the QA philosophy says never to flag the switch-off; I record it because it is the one behaviour difference a user could notice, and because it makes the privacy policy's existing sentence (`docs/legal/privacy-policy.md:37-39`: `allowBackup="false"` "turns off both Android Auto Backup and the device-to-device transfer") true on Android 12+ where before it was not reliably so. Forager's own Backup remains the way across phones, as the policy says (`:42-47`).
- **Nothing else.** Every other app-code change is behaviour-preserving on a phone, with reasons below. The lint and release-compile jobs change CI only.

## What I read, and found clean

- **Compass strip re-indent** (`AvailabilityMapControlsUi.kt:722-300` hunk): read in both full files, not just the diff. Old and new both read: not navigating → heading (or its status icon), then "·"; navigating → the note alone. No logic change, as claimed.
- **`leaveNavigation` lost its out-of-range default** (`NavigationView.kt:522`): the one caller, `SightingsMap.kt:1233`, already passed `navigationMaxZoom(basemap, navigating = false)`. No behaviour change.
- **Backup file handler** (`ContentResolverBackupFiles.kt`): `Uri.parse(x)` → `x.toUri()` at six sites, an alias from AndroidX core. Records path unchanged.
- **Bitmap creation** (`CameraXCapabilityReader.kt:130`, `FanOutLayers.kt:112`, `MarkerGlyphs.kt:214,261`, `RouteHomeLayers.kt:207`): `Bitmap.createBitmap` → the core-ktx `createBitmap` with the same arguments.
- **`ModifierParameter` reorders** (eleven composables): `modifier` moved earlier with a default; the two positional call sites (`MapBubble.kt:330,341`) were converted to named arguments; `TabChromeFade`'s three callers (`AvailabilityCompactScaffold.kt:905,934,1701`) name their arguments; CI compiles. `TabChromeFade` applies `modifier.then(zeroRoom)` with `modifier` defaulting to `Modifier`, so the leaving bar's zero-room behaviour (-691) is unchanged.
- **`mutableStateOf(0)` → `mutableIntStateOf`/`mutableFloatStateOf`** (several files): same semantics, less boxing. `navigationViewRequestId` stays inside `rememberSaveable` (`AvailabilityScreen.kt:1036`).
- **Density reads** (`SightingsMap.kt`, five sites): `LocalResources.current.displayMetrics.density` for `LocalContext.current.resources...`; same value, now observed. Hazel lists these as untested (no Robolectric path); they are the kind of read a phone check confirms incidentally.
- **Backup rules vs -116**: `allowBackup="false"` stays; both sections exclude all nine domains; the test asserts no `<include>` and all nine excluded. Does not reverse the ruling; the manifest comment (`:133-144`) says so and distinguishes it from the withdrawn "rules instead of allowBackup" option.
- **Launcher icon folder** `mipmap-anydpi-v26` → `mipmap-anydpi`: `minSdk = 26` (`app/build.gradle.kts:251`), so the qualifier was redundant. No device below 26 can install the app.
- **Lint switched off on CI** (`app/build.gradle.kts:328-331`): LogNotTimber, NewerVersionAvailable, GradleDependency, each with its reason; matches the owner's -1195. No test skipped, weakened or deleted.
- **Known-bug row 3's claim** that the code is in tester build 3292: `git show 497423d6:...AvailabilitySearchUi.kt` line 667 reads `Locale.getDefault()` in `activeSearchSummary`. Confirmed.
- **Copy**: the only new user-visible strings are month names and "Zoom %.1f×" in the configuration's locale; no placeholder, nothing that reads as identification. The zoom label is a screen-reader state description (Appendix B item 3); in French it becomes "Zoom 1,0×", which is correct.
- **Documents**: CLAUDE.md gains the promotion rule as one line inside "Known pitfalls", after line 253, so the two bullets pinned to line 253 do not move. QA philosophy §3 and §6 amended per -1133/-1134, with the originals kept and marked superseded. Trail-navigation plan: the USGS drop (-1176) is applied in the rulings list and the evidence section; the OSM body is described generically. Role profiles: process only.

## Could not determine

- Whether `containerDpSize` updates on a rotation handled by `configChanges` (item A): I did not read Compose UI 1.12's `AndroidWindowInfo.android.kt`; the claim is Hazel's doc comment, unverified by me.
- Whether the live build actually transferred data in Android 12+ new-phone setup (item C): rests on lint's statement, which I did not test against the platform.
- Nothing in this promotion was seen on a phone by anyone yet; the launch check (A1) is part of the owed check.

## Beyond scope

None. I wrote nothing, ran no Gradle, opened no PR.
