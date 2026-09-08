# Completion report — Dedicated beta signing identity

**Dispatch:** `dispatch-beta-signing-identity.md` (owner's upload, decision made: a dedicated beta
keystore, one identity across beta and release, testers keep their data). **Base:** PR #77 at
`c914a30`; built on PR #77. **No keystore was generated here, no secret enters the repository, no
device result is claimed.** The throwaway key used to verify the pass case lives in the sandbox's
scratch directory and is gone with the session.

---

## Step 0 — report first

**1. Has any build with a non-debug identity been installed on any device?** As far as the
repository and this session can show, **no**. The release build type has never had a signing
config (it gained one only in this change, and only when the secret is present); no release
artifact has ever been assembled here (`app/build/outputs/apk/release/` did not exist before this
change's verification run); every APK this session delivered — CI's, APK-A, APK-B — was
debug-signed, with per-runner debug keys before `5f78323` and the committed one since. No tester
has received anything: the beta template exists, the invitations have not gone out. The owner's
own device history before this session is theirs to confirm, but no build could have carried a
non-debug identity, because none was ever configured. Taken as a plain no; proceeded.

**2. Where signing is configured.** Only `app/build.gradle.kts`. No `gradle.properties` key, no
`local.properties` indirection, no CI variable, no `secrets.*` reference anywhere in
`.github/workflows/ci.yml`. Before this change:

```kotlin
signingConfigs {
    getByName("debug") {
        storeFile = file("debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
    }
}
buildTypes {
    debug { signingConfig = signingConfigs.getByName("debug") }
    release {
        isMinifyEnabled = false
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        // no signingConfig
    }
}
```

**3. How the guard identified the debug keystore:** **by path only** —
`releaseSigningConfig?.storeFile?.canonicalFile == file("debug.keystore").canonicalFile`. So it
would have passed a new keystore at any other path (correct), failed the committed file (correct),
**passed a copy of the debug keystore under another name** (wrong), and **passed an unsigned
release** (wrong — a `null` signing config compared unequal and fell through to "Verified"). Both
gaps closed in step 3.

**4. CI signs nothing but debug**, with the committed `app/debug.keystore`, since `5f78323`; it
holds no secret. PR #78's APK-A is `8eacc91` plus that keystore, debug-signed, certificate
`CB:2F:6D:A5:…:16:26` — the same certificate as APK-B, which is the only property pass 1 needed.

---

## Step 1 — the keystore (owner generates; nothing here)

Run this locally, on the machine that will hold the key. `keytool` prompts for both passwords;
**nothing is typed on the command line**, so nothing lands in shell history:

```
keytool -genkeypair -v -keystore forager-release.jks -alias forager -keyalg RSA -keysize 4096 -validity 10950 -dname "CN=Forager, O=Forager, C=US"
```

- **RSA 4096**, above the dispatch's floor of 2048.
- **`-validity 10950` days is thirty years.** Android's upgrade identity is permanent; a
  certificate that expires strands every install, and Google Play additionally requires validity
  past 2033-10-22 for any app it distributes. Thirty years outlives the app.
- **Alias `forager`**, nothing like `androiddebugkey`.
- Modern `keytool` writes PKCS12 and warns that a key password different from the store
  password is not supported for that format — **use the same password for both prompts**; the
  build reads both and they will be equal.
- Check what was made, and keep the fingerprint line: `keytool -list -v -keystore forager-release.jks`
  prints the certificate's `SHA256:`. That fingerprint is what every future artifact must show
  under `apksigner verify --print-certs`, and what the build guard prints on every release.

**The recovery position, stated plainly: if this keystore file or its password is lost, no future
build can ever update an existing install.** Not "can be recovered with effort" — cannot. Every
tester, and later every user, would have to uninstall and lose their journal to move to a build
signed with a replacement. Before the first build is signed with it, store **both the file and
the password in two places** that do not fail together (a password manager plus an offline copy
is the usual pair). The file is small; the password is the part people lose.

**Play App Signing — resolved by the owner, same day (supersedes the open question this report
first raised here).** The owner's position, citing Android Developers documentation (not
independently verified in this sandbox): Play App Signing is required for any app distributing
through Google Play that was created after August 2021, so a new app cannot opt out — **but at
enrolment the developer may choose the app signing key instead of accepting a Google-generated
one**, by uploading an existing key through the PEPK tool and generating a separate upload key
for submissions. So the continuity path exists and does not force the Play-versus-sideload
decision before the first tester installs:

1. Generate keystore **K** now (the command above). Sideload the beta signed with K.
2. If the app later goes to Play, **enrol with K as the app signing key**, not a Google-generated
   one. Play re-signs releases with K.
3. Testers' sideloaded installs update in place to the Play release. No uninstall, no data loss.

What this changes: the hard deadline the dispatch flagged is gone — Play-versus-sideload need not
be decided before the beta, as long as K is kept and the enrolment is done with K. And **K is more
critical, not less**: it now has to survive until Play enrolment, and losing it forecloses the
upgrade path permanently rather than only breaking local builds. The two-places rule above was
already the recommendation; this is the reason it is not optional.

**The trap, recorded here because it happens once, inside the Play Console, months from now, when
this reasoning is gone:** the default enrolment flow offers a Google-generated app signing key and
recommends it — the right choice for most apps and **wrong for this one**. Accepting it silently
forfeits every tester's data, because the sideloaded beta (signed with K) and the Play release
(signed with Google's key) would be different identities. At enrolment: *choose to upload your own
key, upload K via PEPK, and generate a separate upload key.* The same warning sits in
`app/build.gradle.kts` beside `resolveSigningIdentity()`, where whoever configures the identity
will read it.

---

## Step 2 — wiring (built, `app/build.gradle.kts`)

`resolveSigningIdentity()` reads four values, environment first, then an untracked properties file:

| Environment variable | `signing.properties` key (repository root) |
|---|---|
| `FORAGER_SIGNING_STORE_FILE` | `storeFile` (relative paths resolve against the root) |
| `FORAGER_SIGNING_STORE_PASSWORD` | `storePassword` |
| `FORAGER_SIGNING_KEY_ALIAS` | `keyAlias` |
| `FORAGER_SIGNING_KEY_PASSWORD` | `keyPassword` |

When all four are present, `signingConfigs.create("release")` is declared and the release build
type points at it. **When none is present** — every CI runner, every checkout without the secret
— the release build type has **no** signing config; `assembleRelease` and `bundleRelease` fail at
the guard with a message naming the four values to set; debug builds and the test suite are
untouched (verified below: `assembleDebug` and the full suite ran with nothing configured). There
is no fallback to the debug identity and no fallback to unsigned. **When some but not all four are
present**, configuration fails before any task runs, naming the missing ones — a half-set identity
is the one place a typo strands every future install.

`.gitignore` now carries `*.jks`, `*.keystore`, `!app/debug.keystore` and `/signing.properties`,
with the reasoning. Verified with `git check-ignore` and with the properties file and a keystore
physically present at the root during the V6 run: `git status` showed neither.

## Step 3 — the guard (extended)

`verifyReleaseNeverSignsWithDebugKeystore` now refuses three things and passes one:

| Release signing config | Result |
|---|---|
| none (nothing configured) | **fails** — "has no signing identity", names the four values |
| store file missing on disk, or no store password / alias | **fails**, naming which |
| `app/debug.keystore` by path | **fails** |
| any keystore whose certificate's SHA-256 equals `DEBUG_KEYSTORE_CERTIFICATE_SHA256` | **fails** — a copy of the debug keystore elsewhere no longer passes |
| any other keystore that exists and opens | **passes**, printing the certificate's SHA-256 so the owner can compare it to `keytool -list -v` and to `apksigner verify --print-certs` |

`DEBUG_KEYSTORE_CERTIFICATE_SHA256` is **an independent constant recorded from keytool's own
output** on the committed file (`CB:2F:6D:A5:02:C3:FE:7B:EA:8D:B7:47:41:4B:ED:47:CB:C8:35:09:44:CF:82:91:E9:B0:98:06:C9:4F:16:26`),
not computed from the file at build time — the dispatch's rule that the guard's expected value
is not derived from the code it checks. The guard opens the release keystore with the configured
store password (PKCS12, then JKS), reads the alias's certificate, hashes its DER encoding with
SHA-256 and prints it in keytool's colon-separated form, so the three fingerprints — keytool's,
the guard's, apksigner's — are the same string and can be compared by eye. Still a real
`dependsOn` of `assembleRelease` and `bundleRelease`.

## Verification (sandbox; a throwaway key, never committed)

A throwaway keystore was generated in the session's scratch directory (`keytool`, alias
`throwaway`, 30-day validity, `SHA256: 29:14:8B:05:…:4A:F5`) purely to exercise the pass case; it
is not the beta identity, it is not in the repository, and it dies with the session. A copy of
the committed debug keystore was placed at a second path for the fingerprint case. Each run is a
separate Gradle invocation with the identity supplied, or not, through the environment or the
properties file exactly as a real build would; exit codes and messages read from the logs.

| # | Configuration | Command | Predicted | Actual |
|---|---|---|---|---|
| V1a | nothing | `verifyReleaseNeverSignsWithDebugKeystore` | fail, "no signing identity" | exit 1: "The release build type has no signing identity … set FORAGER_SIGNING_STORE_FILE, … (or the four keys in signing.properties …)" |
| V1b | nothing | `assembleDebug` | succeed, unaffected | exit 0 |
| V2 | `FORAGER_SIGNING_STORE_FILE` only | `help` (any task) | configuration fails naming the missing three | exit 1: "half-configured: missing FORAGER_SIGNING_STORE_PASSWORD / storePassword, FORAGER_SIGNING_KEY_ALIAS / keyAlias, FORAGER_SIGNING_KEY_PASSWORD / keyPassword" |
| V3 | `app/debug.keystore` by path, its alias and password | guard | fail, path and fingerprint | exit 1: "resolves to the committed, public debug signing identity (…/app/debug.keystore, certificate SHA-256 CB:2F:6D:A5:…:16:26)" |
| V4 | a **copy** of the debug keystore at another path | guard | fail on fingerprint alone — the case the old path check passed | exit 1: same message, naming the copy's path and the same `CB:2F:…:16:26` |
| V5a | throwaway via the four environment variables | guard | pass, printing the fingerprint | exit 0: "Verified: the release build type signs with 'release' (…/throwaway-beta.jks, alias 'throwaway'), certificate SHA-256 29:14:8B:05:EB:3B:EA:ED:2E:4A:17:BA:06:01:35:E8:C9:92:43:DC:7E:78:DD:44:F0:16:B1:27:9C:19:4A:F5 -- not the debug identity." |
| V5b | same | `assembleRelease` | a signed release APK | exit 0; `app-release.apk` (68.1 MB); `apksigner verify --print-certs`: `CN=Throwaway verification key`, `SHA-256 29148b05eb3b…9c194af5`; `versionName='1.0.470+g377c495e'` |
| V6 | throwaway via `signing.properties` at the root, **relative** `storeFile`, keystore copied beside it | guard; `git status` with both present | pass; neither file appears | exit 0; `git status --short` empty with both present, empty after removal |
| V7 | nothing | `testDebugUnitTest` (full suite) | unchanged | exit 0: **166 suites, 1277 tests, 0 failures, 0 errors, 24 skipped**, the skip set the CI allowlist's identity set |

**Three sources, one string:** keytool's `SHA256:` on the throwaway keystore, the guard's
`certificate SHA-256` line, and apksigner's `SHA-256 digest` on the artifact are the same 32
bytes (keytool and the guard in colon-separated upper case, apksigner in bare lower-case hex).
That equality is what step 4 asks the owner to check on the real key, and the build now prints
the middle one on every release so the check is a glance, not a computation.

**Not verified, said plainly:** anything on a device; the real keystore (does not exist yet, and
will never exist here); `bundleRelease` beyond its `dependsOn` wiring (the same guard task, not
run separately — the same identity resolution feeds both).

---

## Step 4 — verification the owner performs (device; nothing claimed here)

The identity has not been tested until it has survived an in-place update. Two builds, two
different commits (the version code is the commit count, so the second must be a later commit —
any doc-only commit will do), both signed with the new key:

1. **Configure the identity on the build machine**, once, either as the four environment
   variables or as `signing.properties` at the repository root (it is gitignored; confirm with
   `git status` that it does not appear). Never paste the password into a commit, an issue, or
   this report.
2. **Build #1:** `./gradlew assembleRelease` on commit X. The guard prints
   `Verified: the release build type signs with 'release' (…), certificate SHA-256 <fp>`. Then
   `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk` — its
   `SHA-256 digest` must equal the `<fp>` the guard printed and the `SHA256:` line from
   `keytool -list -v` on the keystore. Three sources, one string. Record it.
3. **Install #1** on a device with no Forager installed (`adb uninstall com.forager.app` first if
   a debug build is there — this is the one time an uninstall is expected, since the debug and
   release identities differ by design). Record a track, drop a waypoint, make an entry with a
   photo. Note the counts.
4. **Build #2** on a later commit Y, same identity. Confirm `apksigner` shows the same
   fingerprint and `aapt2 dump badging` shows a higher `versionCode`.
5. **Install #2 over the top, without uninstalling:** `adb install -r app-release.apk`. It must
   succeed — `INSTALL_FAILED_UPDATE_INCOMPATIBLE` here means the two builds were signed
   differently and step 1 or 2 went wrong; stop and compare fingerprints, do not uninstall.
6. **Verify the data survived:** the track, the waypoint, the entry and its photo are all there
   with the same counts. Then, and only then, the identity is tested.

Report back the fingerprint from step 2 and the counts from steps 3 and 6. The fingerprint goes
into the audit trail as the beta-and-release identity; the beta invitation should carry it too,
so a tester (or the owner, later) can check any build they are handed with `apksigner`.

---

## Out of scope, untouched

`app/debug.keystore` stays (debug builds sharing one identity is its own, still-valid purpose);
Play App Signing raised above, not acted on; the GPX exporter untouched; no test silenced,
modified or skipped.

## Required disclosure

**Confirmed from the code:** every step-0 answer above, with the files named; the old guard's
path-only check and its two gaps; the absence of any signing indirection or CI secret. **Confirmed
from keytool:** the committed keystore's fingerprint (the constant). **Confirmed by running the
build** (verification section): each refusal and the pass, the artifact's certificate matching the
throwaway keystore's, the gitignore rules with the files present, debug and the suite unaffected
with nothing configured.

**Could not determine:** whether the owner's device ever held a non-debug build from outside this
repository — no build from this repository could have; anything on a device (no `/dev/kvm`);
whether the owner intends Play distribution (still open, and — per the owner's same-day ruling on Play App Signing above — no longer a decision the beta has to wait for).

**Premises in this dispatch that were wrong:** one refinement rather than an error — the dispatch
calls `app/debug.keystore` "the conventional public Android debug identity"; it is a keystore
*generated here* (`5f78323`) with the conventional debug alias, password and DN, not the
per-machine `~/.android/debug.keystore` any SDK install has. It is public because it is committed,
and its fingerprint is unique to this repository, which is exactly why a constant fingerprint can
identify it. Nothing in the dispatch's reasoning changes.

**Decided beyond scope:**
1. The environment-variable and properties-key names, and that environment wins over the file.
2. That a half-configured identity fails at configuration time (the dispatch's own suggestion;
   the alternative — treating it as absent — would hide a typo until the guard, and the guard
   would then report "no identity" rather than "you set three of four").
3. `RSA 4096` and thirty years in the recommended command, above the dispatch's floors.
4. The keystore type fallback (PKCS12, then JKS) when the guard opens the release keystore.
5. That the guard also refuses a missing store file, password or alias by name, not only
   "no signing config".
6. Naming the signing config `release` (the Android convention; the build type and the signing
   config sharing a name is the usual shape).
