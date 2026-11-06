# Native debug symbols: Phase A check (read-only)

Dispatch `prompts/preserved/2026-10-12-02.md` (branch records-after-173), intent RECORD -919, owner ruling D3 in -917. Production release task T4.
Written 2026-10-12 (UTC), base origin/main `921aa9c7`. No Gradle ran anywhere, nothing was installed, and no build was started.

## Result: Phase A stops. Phase B did not run.

Two of the dispatch's stop conditions fire:

- **(b), the NDK.** Extracting symbols needs the NDK's `objcopy`, and in plugin 9.3.1 that step doesn't download a missing NDK. Neither this laptop's SDK nor CI has an NDK.
- **(c), a premise that changes the plan.** The plan said one build setting would make crashes inside the map library readable. It won't. The map library's archive ships the copy the app packages already stripped, and the plugin skips already-stripped libraries without saying so at the default log level. Readable symbols for that exact library do exist, but in a different folder of the same archive, which the setting never reads. Using them is a different change from "one build setting", so it is a decision for the owner.

Condition (a) does not fire as worded. One shipped library, a small one from the settings-storage library, carries a `.symtab`. So the setting would produce a symbols file for that library alone, and none for the map library.

## In plain terms (for the owner)

- The app ships five native libraries. The map library is by far the largest, and the warning matters for it.
- The map library's maker publishes two copies of each native library: a slim copy, which goes into the app, and a full copy with all the crash-reading information. They are the same build, with matching IDs.
- The build setting only looks at the slim copy, finds nothing to extract, and quietly moves on. On its own, it wouldn't make map crashes readable.
- It also needs an extra Android build tool (the NDK) on whichever machine builds the release. Neither this laptop nor the CI machine has it.
- Whether Play's warning goes away at all can only be seen on an upload.

## Premise checks (at `921aa9c7`)

1. **Base:** origin/main is `921aa9c796052e5c` after `git fetch`. It has not moved.
2. **No native-symbols setting:** confirmed. `app/build.gradle.kts` has no `ndk`, `debugSymbolLevel` or `ndkVersion`. The only related block is `packaging { resources { excludes … } }` at `:306-310`. The release build type is at `:287-293`, with `isMinifyEnabled = false` at `:288`.
3. **The native code comes from the map library:** only partly true. It is one of five libraries. The other four come from AndroidX libraries (table below). The map library is declared at `gradle/libs.versions.toml:37` (`maplibre = "13.5.0"`) and `:91`, and used at `app/build.gradle.kts:502`.
4. **The signing guard:** confirmed. The task is `verifyReleaseNeverSignsWithDebugKeystore` at `app/build.gradle.kts:405-457`, a `dependsOn` of `assembleRelease`/`bundleRelease` at `:459-460`. That is a little later than the dispatch's "around 397 to 430"; lines 397-404 are its doc comment.
   - **Extraction task names, from the 9.3.1 jar:** `ExtractNativeDebugMetadataTask$FullCreationAction.getName` builds `"extract"` + variant + `"NativeDebugMetadata"`, and `$SymbolTableCreationAction` builds `"extract"` + variant + `"NativeSymbolTables"`. So the tasks are `extractReleaseNativeDebugMetadata` (FULL) and `extractReleaseNativeSymbolTables` (SYMBOL_TABLE).
   - **Their inputs:** `SingleArtifact.MERGED_NATIVE_LIBS` and `InternalArtifactType.STRIPPED_NATIVE_LIBS` (`ExtractNativeDebugMetadataTask$CreationAction`). Neither is a signing or bundling output.
   - **Not run, so not verified:** whether `:app:extractReleaseNativeSymbolTables` runs without the signing guard. The guard is wired only to the two names above, so it should run without it.
5. **CI:** confirmed. `assembleDebug` at `.github/workflows/ci.yml:116`, `testDebugUnitTest` at `:187`, debug APK upload at `:463-474`. "Run this branch on CI" is at `README.md:84`.

## Shipped native libraries

**The APK checked.** `app-debug-apk` from main's CI run 38185042418 (head `921aa9c7`, success), artifact 11719476962. The zip is 50,665,657 bytes, matching the API's figure. `app-debug.apk` has SHA-256 `62278500…6bfc`.

**Libraries in it.** Twenty files, five libraries in each of four ABIs: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`.

**Versions.** They are read from the APK's own `META-INF/*.version` files, which record what the build resolved. The map library's version comes from the catalog.

**Archives.** Each was downloaded with `curl` from the project's repositories (`settings.gradle.kts:3-4, 11-12`: Google's Maven and Maven Central). Each was checked against the size and SHA-256 in the publisher's Gradle module metadata. The map library's archive was also checked against the repository's `.sha256` file and with `unzip -t`.

**APK against archives.** Every APK library is **byte-identical** (SHA-256) to the `jni/` copy in its archive: 20 of 20 matched, 20 archive copies indexed. So the debug build stripped nothing, and the APK shows exactly what the archives ship. Main's CI log (job 114605441516, line 873) agrees: "Unable to strip the following libraries, packaging them as they are: libandroidx.graphics.path.so, libdatastore_shared_counter.so, libimage_processing_util_jni.so, libmaplibre.so, libsurface_util_jni.so."

| Library | Source (version) | How the version was pinned | Sections in the packaged (`jni/`) copy, all 4 ABIs | Built by |
|---|---|---|---|---|
| `libmaplibre.so` (9.5–13.4 MB) | `org.maplibre.gl:android-sdk:13.5.0` | catalog `:37` | `.dynsym` only (5,979–5,988 exported functions); no `.symtab`, no `.debug_*`, no `.gnu_debuglink`/`.gnu_debugdata` | NDK r28c |
| `libdatastore_shared_counter.so` (8–10 KB) | `androidx.datastore:datastore-core-android:1.2.1` (via `datastore-preferences` 1.2.1; transitive) | APK `META-INF/androidx.datastore_datastore-core.version` = 1.2.1 | `.dynsym` + **`.symtab`** (37–78 entries) + `.strtab`; no `.debug_*` | NDK r20 |
| `libimage_processing_util_jni.so` (24–49 KB) | `androidx.camera:camera-core:1.6.2` | catalog `:67`; APK version file = 1.6.2 | `.dynsym` only | NDK r27 |
| `libsurface_util_jni.so` (3–5 KB) | `androidx.camera:camera-core:1.6.2` | as above | `.dynsym` only | NDK r27 |
| `libandroidx.graphics.path.so` (7–11 KB) | `androidx.graphics:graphics-path:1.0.1` (via Compose `ui-graphics` 1.12.0; transitive) | APK `META-INF/androidx.graphics_graphics-path.version` = 1.0.1 | `.dynsym` only | NDK r25c |

**The map library's full copies.** The map library's 197 MB archive also carries a second copy of each library under `prefab/modules/maplibre/libs/android.<abi>/libmaplibre.so`. Prefab is the folder for native code that links against the library, and the app build does not package it. These copies are 171–193 MB each, "with debug_info, not stripped", with full DWARF (`.debug_info`, `.debug_line`, `.debug_loc`, `.debug_ranges`, `.debug_str`, and `.debug_frame` on armeabi-v7a) and a `.symtab`. **Their GNU build IDs match the packaged copies**, ABI by ABI:

| ABI | Build ID (packaged = prefab) | `.symtab` entries in the prefab copy | Prefab size |
|---|---|---|---|
| arm64-v8a | `6a4da5e3f9795251094f54c20e2fb9378919d414` | 112,678 | 192,783,848 |
| armeabi-v7a | `a160cc18899a989615f6675dd236586b5eff08d9` | 99,987 | 173,505,756 |
| x86 | `354f974bc9cbe98ec4c4ed565472537c58168004` | 44,773 | 171,248,632 |
| x86_64 | `64562e9ed1bb08fecd9c4feb3a94c5023779e05d` | 44,754 | 186,145,104 |

**How the sections were read.** `readelf -S -W`, `readelf -s`, `readelf -n` and `file` (binutils 2.46, file 5.46), through a script that prints a total so the count can be checked against its input (20, 4, 16 and 1 × 4).

**The script can see a `.symtab`.** It reported one on the datastore library, and both `.symtab` and `.debug_*` on the prefab copies. That is the positive control.

**The filter drops nothing.** The full section list of the arm64 packaged map library, unfiltered, has no symbol-bearing section beyond `.dynsym`.

## What the setting would do (plugin 9.3.1, read from the jar's bytecode, not run)

**The jar.** `com.android.tools.build:gradle:9.3.1` was read from the local Gradle cache and copied to a scratch directory. Its SHA-1, `c1b6c02a…ea86`, equals Google Maven's published `.sha1`. It was read with `javap`, `strings` and `unzip -l`.

**`ExtractNativeDebugMetadataWorkAction.run`.** For each `.so` in the merged native libraries:

1. **Already stripped.** If `input.length() == strippedCopy.length()`, it logs at **info** level only: "Unable to extract native debug metadata from … because the native debug metadata has already been stripped." It then skips that library. At the default log level, nothing is printed.
2. **No `objcopy`.** Otherwise, if no NDK `objcopy` is found for the ABI, it logs a **warning**: "Unable to extract native debug metadata from … because unable to locate the objcopy executable for the … ABI." It then skips that library.
3. **Extraction.** Otherwise it runs `objcopy --strip-debug` into `<abi>/<lib>.sym` (SYMBOL_TABLE) or `objcopy --only-keep-debug` into `<abi>/<lib>.dbg` (FULL).

**The strip step.** The "stripped copy" comes from `StripDebugSymbolsTask`, which runs the NDK's strip with `--strip-unneeded`. With no strip tool, it copies each library unchanged and warns, as in main's CI log above.

**With no NDK,** every stripped copy equals its input, so step 1 skips every library and **nothing is extracted, without a warning**. Only the strip warning appears.

**With an NDK, inferred.**
- The map library and the three other stripped libraries are already stripped. Re-stripping them with `--strip-unneeded` is expected to leave the length unchanged, so step 1 would skip them.
- The datastore library has a `.symtab` to remove, so it would be the only one extracted.
- **Not verified:** that re-stripping leaves the length unchanged. Seeing it needs the NDK's strip tool.

**No automatic NDK download for this step.**
- `SdkComponentsBuildService$VersionedNdkHandler` builds the `objcopy` map from `NdkHandler.getNdkPlatform()`. That method calls `getNdkPlatform(false)`, so download is not allowed on this path. With no NDK configured, the map is empty.
- `SdkHandler.installNdk` exists, but this path doesn't reach it.

**Default NDK.** `com.android.SdkConstants.NDK_DEFAULT_VERSION` = `"28.2.13676358"` (r28c). It was read from `com.android.tools:common:32.3.1`, the plugin's own dependency, whose SHA-1 was checked against Google Maven. That is the version the plugin would look for in `<sdk>/ndk/` when no `ndkVersion` is set.

**Bundle path.** `PackageBundleTask$BundleToolWorkAction` adds the files as bundle metadata under the namespace `com.android.tools.build.debugsymbols`. It is gated by the plugin option `PACKAGE_NATIVE_DEBUG_METADATA_IN_APP_BUNDLE`.

**The owner's one-line check on a release bundle** is `unzip -l <bundle>.aab | grep -i debugsymbols`. The `BUNDLE-METADATA/` prefix is bundletool's convention; it was not read from bundletool here. Separately, the documentation says the plugin also writes `app/build/outputs/native-debug-symbols/<variant>/native-debug-symbols.zip`.

**Documentation.** The plugin's page (developer.android.com/build/include-native-symbols, read 2026-10-12) agrees on the levels and on the "already been stripped" line, and says to run with `--info` to see it. It gives a 1.6 GB limit for the symbols file. The fetched summary said nothing about the NDK; the requirement above comes from the bytecode.

## The NDK on this laptop

- **No NDK.** The SDK this laptop uses is `$ANDROID_HOME` = `/home/zynergy-labs/Android/sdk`, through the `~/Android` → `/mnt/work/Android` symlink. It has `build-tools` 36.0.0 and 37.0.0, `platforms`, `platform-tools`, `cmdline-tools`, `emulator` and `system-images`. It has **no `ndk/`, no `ndk-bundle/` and no `cmake/`** (`ls`, read-only).
- **Observed, not acted on:** the main checkout's `local.properties` has `sdk.dir=/home/zynergy-labs/Android/Sdk` with a capital S. That directory does not exist. The existing one is lowercase `sdk`. I did not check which one the plugin picks or whether the owner's release builds run on this laptop.
- **What a release build with symbols would need:** the NDK installed on the build machine, side by side at `<sdk>/ndk/28.2.13676358` (the plugin's default), or a pinned `ndkVersion` matching whatever is installed. Installing it is the owner's call. Nothing was installed.
- **CI has no NDK either.** `scripts/setup-android-sdk.sh:45-48` installs only `platforms;android-37.1` and `build-tools;37.0.0`, and main's CI log shows the strip warning. Phase B's CI proof as dispatched would therefore extract nothing, unless the scratch workflow also installed the NDK on the CI runner. The dispatch did not specify that.

## Options for the planner and owner (not chosen here)

1. **The setting plus the NDK on the release machine.**
   - The bundle would carry symbols for the datastore library only. The map library is skipped.
   - Whether Play's warning clears with that is unknown and visible only on upload.
   - Map crashes would not become more readable.
2. **The setting, plus feeding the map library's full prefab copies to the build in place of the slim `jni/` copies.**
   - The plugin would then strip them for packaging and extract their symbols.
   - This is a build-script change to which libraries get packaged, not one setting. It needs the NDK on the release machine. Its CI proof would need the NDK installed on the runner.
   - FULL from these copies would be about 720 MB before compression, under the 1.6 GB limit. SYMBOL_TABLE would be far smaller; its size was not measured.
   - The build IDs match, so the symbols would fit the shipped code. The packaged bytes might differ from today's `jni/` copies after the plugin's own strip. Not checked.
3. **Upload a symbols file by hand in Play Console with each release, built from the prefab copies.** This is an owner step every release. Not checked here: Play's accepted format for a manual upload.
4. **Leave the warning** (the alternative the owner was offered in D3).

## Could not determine

- **Whether Play's warning clears under any option.** Only the owner's upload shows it.
- **Whether `extractReleaseNativeSymbolTables` runs without the signing guard.** Inferred from its inputs, not run.
- **The plugin's behaviour on a real build, with or without the NDK.** No Gradle ran; the behaviour above is read from bytecode.
- **Whether re-stripping an already-stripped library keeps its length**, the inferred reason the map library would be skipped even with the NDK.
- **Which machine and SDK path the owner's release builds use.**
- **Exact file names inside the bundle**, beyond the namespace.

## Not done

- **No branch other than this one was touched**, and nothing was installed or built.
- **No index row** in `docs/audits/README.md`: the dispatch assigns that to Phase B, step 4. The planner may want one for this Phase A report.
- **Scratch files** (downloaded archives, scripts, logs) are under `/tmp/ns-phaseA`, outside any repository.
