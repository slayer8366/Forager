# AVD boot measurement — old machine (i7-10510U), 2026-09-17

Measurement only, per the dispatch "boot an AVD and report what it costs". No app code changed;
nothing added to CI, scripts or docs beyond this record. The instrumented suite was not run.

**Purpose of this file:** the baseline the same measurement on the 16-core replacement is compared
against. Rerun the same steps (below) there and put the numbers side by side.

## Host (read on the machine, not taken from the dispatch)

| | |
|---|---|
| CPU | Intel i7-10510U, 4 cores / 8 threads (`lscpu`) |
| RAM | 11 GiB (`free -g`) |
| GPU | Intel UHD (CometLake-U GT2), Mesa, Wayland session (`lspci`, emulator log) |
| KVM | `emulator -accel-check`: "KVM (version 12) is installed and usable"; user in `kvm` group |
| Emulator | 37.1.11.0 (build 15917651) |
| APK | `:app:assembleDebug` at `6ff1262` (`claude/new-session-vto65i`), 80.7 MB |

## AVD

`avdmanager create avd -d pixel_7`, defaults kept: 4 vCPU, 1536 MB RAM, 1080×2400.
**One change from the defaults:** `disk.dataPartition.size` 800M → 6G. With 800M `/data` was 100%
full after first boot and the install failed with `Requested internal only, but not enough space`.
This is storage, not speed; it is what Android Studio's own AVD wizard would have set.

Timing method (`/tmp` scripts, not committed): every boot is `-no-snapshot-load -no-snapshot-save
-no-boot-anim`. `boot_completed` = `sys.boot_completed=1`; `home` = launcher holds window focus.
"First boot" = with `-wipe-data`; "second" = cold boot of the same, already-initialised AVD.
Interaction probe: launch app, grant location, 10 map pans by `input swipe`, 5 bottom-nav taps,
then `dumpsys gfxinfo` for that window.

## Result 1 — the API 37.0 image is unusable, independent of this machine

`system-images;android-37.0;google_apis;x86_64` (rev 6, the app's `targetSdk`) boots, then
`surfaceflinger` aborts every ~30 s for as long as it runs, restarting the framework:
`Abort message: 'Assertion failed: !rcEnc->featureInfo()->hasReadColorBufferDma'`, in
`/vendor/lib64/hw/mapper.ranchu.so` (`GoldfishMapper::readFromHost`). Seen in all three GPU modes:

| API 37.0, GPU mode | home | guest crashes | install / launch |
|---|---|---|---|
| `auto` (fell back to software: "Your GPU drivers may have a bug") | 52.8 s, 59.9 s (two first boots: 800M, then 6G wiped) | ≥2 in first 90 s | install lost to framework restart |
| `host` | 142.7 s | 26 by 120 s uptime | framework down; not measurable |
| `swiftshader_indirect` | 25.4 s | 3 by 120 s, continuing ~every 30 s | install 9.6 s; launches got no answer |

The same emulator with **`system-images;android-36;google_apis;x86_64`** has zero crashes in every
mode, so everything below is API 36. That the fault is the 37.0 image/emulator pairing and not the
host is **inferred** from that one swap, not confirmed further; whether a newer 37.x image or
emulator fixes it was not tried (the only 37.1+ x86_64 images offered are 16 KB-page variants).

## Result 2 — API 36 numbers (the baseline)

| API 36 | home, first boot | home, second cold boot | install (fresh) | cold launch (`am start -W`) | frames | janky | p50 / p90 / p99 frame |
|---|---|---|---|---|---|---|---|
| `-gpu host` | **36.7 s** | **19.6 s** | **5.1 s** | **6.4 s** | 405 | 16.3% | **16 / 32 / 117 ms** |
| `-gpu swiftshader_indirect` | 42.0 s | 31.4 s | 7.6 s | 7.7 / 8.4 / 8.6 s | 96 | 99.0% | 125 / 200 / 700 ms |
| headless, **no display**, swiftshader | — | 29.1 s | — | — | 143 | 96.5% | 73 / 500 / 1650 ms |
| headless, display present, `host` | — | 19.3 s | — | — | 432 | 22.7% | 16 / 46 / 200 ms |
| headless, **no display**, `host` | **fails** | | | | | | |

`adb` sees the device ~10–18 s after launch in every row. Launches 2 and 3 under `host` returned
`TotalTime: 0` and are not counted; only launch 1 is.

**GPU mode:** `host` wins clearly. Boot is ~12 s faster on a second cold boot (19.6 vs 31.4 s) and
~5 s faster on a first. Interaction is where it separates: a 16 ms median frame (≈60 fps) against
125 ms (≈8 fps), with 4× as many frames drawn over the same input. The emulator prints "Your GPU
cannot be used for hardware rendering" under `host`, but its own log shows it rendering through
"Mesa Intel(R) UHD Graphics (CML GT2)", and the frame numbers agree. The warning is wrong here.

**Headless:** `-no-window -no-audio` works **with swiftshader and no desktop session**
(`DISPLAY`/`WAYLAND_DISPLAY` unset): it boots in 29 s and is fully drivable by adb, but renders slowly.
With `-gpu host` and no display, the renderer fails (`GlxEnginegetDefaultDisplay: Failed to open
display 0`, `Could not start renderer`) and **the emulator process does not exit**. It hangs,
ignores SIGTERM, and adb never sees a device. An agent using this form gets silence, not an error.
Headless `host` **with** a desktop session present works as well as windowed.

## Is the app usable on it

Under API 36 + `-gpu host`: **yes**. The map renders with tiles, pans at a 16 ms median frame, the
tab bar responds, and cold launch is ~6 s. Under swiftshader: technically it works, but at ~8 fps
it is too laggy to judge anything visual. "Usable" here comes from frame stats and injected input,
not from a person operating it by hand.

## Judgement

**Worth using on this machine now. It does not need to wait for the new one**, provided it is run
as API 36 x86_64, `-gpu host`, inside the desktop session. Boot is ~20 s once initialised, installs
take ~5 s, and the app runs at a normal frame rate. What it is not good for here: the app's own
target API (37.0 image crash-loops, see Result 1), and fully display-less agent runs, which only
work in swiftshader at a frame rate fit for adb-driven checks, not visual ones.

## Found in passing — not part of the dispatch, not fixed

- **A debug build crashes on a failed diagnostics write.** After an `adb uninstall` + reinstall on
  the same `/data`, the new install (uid 10217) found `Android/data/.../files/diagnostics/diagnostics.log`
  still owned by the previous uid (10216). `DiagnosticsLog.append` (`app/src/debug/.../DiagnosticsLog.kt:66`)
  threw `FileNotFoundException: EACCES` on the `forager-diagnostics` executor, called from
  `DebugDiagnostics.install` (`app/src/debug/.../DebugDiagnostics.kt:186`). It was uncaught and
  killed the app process. Observed once. **Why** the uninstall left the external file behind was not
  determined. It is debug-only source, but a diagnostics write taking the app down with it is worth
  an owner's look. Clean installs after `-wipe-data` showed 0 app fatal exceptions.
- `avdmanager`'s default 800M data partition cannot hold this APK after first boot (above).

## Not done / not verified

- Instrumented suite: not run, per the dispatch.
- No tuning of RAM, cores or other settings beyond the data partition.
- API 37 install and launch timing: not measurable (Result 1).
- Each figure is a single run, not a distribution. Treat differences under a few seconds as noise.
