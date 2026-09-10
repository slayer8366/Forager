# Completion report — GPX namespace URI to a controlled domain

**Dispatch:** GPX namespace URI to a controlled domain (2026-09-09).
**Base:** `main` @ `28bcc3b` — four commits past the dispatch's `14cc6f3`, which §3's "or later"
permits. Branch `claude/gpx-namespace-domain`.

**The change:** `https://forager.app/gpx/1` → `https://zynergy-labs.com/forager/gpx/1`, one constant,
plus two hand-written assertions added to **existing** tests. Nothing in §2's do-not-touch list moved.
No version bump: the `/1` stays `/1`.

---

## 1. §0's merge-timing constraint is already satisfied

§0 says do not merge while the flake arms are running. **The arms have reported.** PRs #89 and #90
are closed unmerged, PR #91 merged as `aab0dc6`, and `main` has since moved to `28bcc3b`. The
experiment cannot be disturbed by anything landing now.

§6's rule is unaffected and is what governs: not pre-authorized, PR opened, stopped.

## 2. What changed

| file | change |
|---|---|
| `domain/GpxCodec.kt` | the constant, plus a KDoc recording why the domain matters and that `/1` is not bumped |
| `domain/GpxCodecTest.kt` | two assertions added to an existing test — new URI present, old domain absent |
| `export/TrackGpxExporterTest.kt` | one assertion added to an existing test — the **written file** carries the new URI |

Test counts unchanged: `GpxCodecTest` 13, `TrackGpxExporterTest` 5, suite 1309. §7 required that.

**The namespace was completely untested before this change.** No assertion anywhere referenced it —
the only occurrence in the test tree was an input fixture. So changing the constant would have been a
change nothing could see. §7 forbids adding tests, so the assertions went into existing ones, which
keeps every count fixed.

Both literals are **written out by hand**, per §3. An expectation read from `FORAGER_NAMESPACE` would
pass for any value, including the domain this moved off.

## 3. §3's site list — both claims wrong

Relayed, and re-derived here:

| §3 said | at this HEAD |
|---|---|
| constant "used at the `forager:fullRecord` write site (around `:93`)" | **Wrong.** The constant has exactly **one** read site, the `xmlns:forager` declaration (`:72`). Line 93 is the `rule=` append and touches no namespace — `forager:fullRecord` is written as a literal prefixed name. |
| "Decode: the matching namespace match in the parse path (around `:207`)" | **Wrong — there is no namespace match in decode at all.** See §4. |
| "`GpxCodecTest` has 13 `@Test`" | **Confirmed.** Still 13. |
| "Six hand-written `timestampMillisNonZero` literals" | **Confirmed.** Exactly 6. |

Neither wrong claim cost anything: the change is a one-line constant either way. But a session that
had trusted §3 would have gone looking for a decode-side namespace match and found nothing, and the
honest resolution of *that* is §4.

## 4. §4's ruling is moot — decode matches no namespace, old or new

§4 asks whether decode should accept the old namespace as well as the new, and rules **no, match the
new URI only**. That ruling **cannot be implemented, because there is nothing to restrict**:

- `DocumentBuilderFactory.newInstance()` is used with **no `setNamespaceAware(true)`**, so the parser
  is not namespace-aware;
- every lookup is `getElementsByTagName(...)` — `getElementsByTagNameNS` appears nowhere;
- so `getElementsByTagName("forager:fullRecord")` matches the **qualified name**, i.e. the
  `forager:` **prefix**, and the declared URI is never consulted.

**Decode therefore already accepts every namespace, including none and including the old one**, and
did so before this dispatch. The outcome §4 wanted to avoid — untested accept-both tolerance — is not
created by this change; it is pre-existing and total. Nothing was added to implement the ruling, per
§4's instruction to say so rather than build.

**Recorded for the export/import dispatch, which is what §4 asked for.** The real consequence of
prefix-coupling is the *opposite* of tolerance: a GPX file that binds a **different prefix** to the
correct URI — entirely legal XML, and what any general-purpose writer might emit — **will not
decode**, because nothing named `forager:*` appears in it. When import is wired, that is the thing to
fix, and it is a larger question than a namespace string: it is whether the decoder becomes
namespace-aware. Out of scope here, and not attempted.

`GpxCodec.decode` still has **no production caller**, re-confirmed at this HEAD. §4's premise holds.

## 5. Evidence

**One revert check.** The constant restored to `https://forager.app/gpx/1`, build log scanned for
compile errors **before** any result was read, file restored from a copy saved before editing (never
`git checkout --`), forward change confirmed present afterwards by grep.

- **Predicted 2 failures. Observed exactly 2**, and the predicted ones:
  `GpxCodecTest > the full record declares its rule set and names the rule on excluded points only`
  and `TrackGpxExporterTest > the written file's content is exactly what GpxCodec encode produces for
  this track`.
- Messages specific to this edit: *"the forager vocabulary must be bound to the controlled domain"*
  and *"the written file must bind the forager vocabulary to the controlled domain"*.
- No compile errors in the build log; results cited on that basis.

The two `GpxCodecTest` round-trip tests that compare encode against decode did **not** fail on the
revert, correctly — they never mention the namespace, and decode ignores it.

## 6. CI — reported, not fixed

Local Windows full suite at the namespace change alone: **167 suites / 1309 tests / 10 failures /
24 skipped.** (Superseded for the branch as a whole by §8.5 — the `applicationId` change that landed
afterwards moves this host to 12. The 10 here is the figure for the namespace change on its own.)

- **Test count unchanged at 1309**, as §7 required. Nothing unintended happened.
- **Skips 24**, unadjusted, byte-identical to the allowlist in both directions.
- **The 10 failures are the known host-specific Windows set.** Ten this run, and §7's own note
  applies — nine or ten of a named pool is normal, not a change.
- **`JournalTabTest`: green.** Reported as a **draw**, per §7's instruction that a green run is
  evidence about the rate. It is a **local Windows** draw, and the flake is CI-only, so it joins the
  local tally at **0 in 13**, not the CI tally.

## 7. Disclosure

### 7.1 Confirmed vs inferred

Everything in §3 was re-derived at this HEAD; results in §3's table above. The `/1` version segment,
`schema` still absent, and every name in §2's do-not-touch list were checked as unchanged by
inspection of the diff — **3 files, +32/−1 lines**, and no line of it touches an element name, an
attribute name, or the timestamp representation.

### 7.2 Could not determine

- **Whether `zynergy-labs.com` is registered to this project** — taken from the dispatch. Not
  verifiable from the repository, and no network check was made. The repo *is* consistent with it:
  `docs/legal/privacy-policy.md` names `privacy@zynergy-labs.com` and
  `https://zynergy-labs.com/privacy`, and the app's package is `com.zynergylabs.forager.app`.
- **Nothing about the exported file was checked on hardware.** No device, no real export read.

### 7.3 Premises that were wrong

- **Both of §3's code-site claims** (§3 above).
- **§4's premise that a namespace match exists to be restricted** (§4 above). §4's own fallback —
  "if you disagree because of something in the parse code, say so in disclosure rather than adding
  it" — is what was followed.
- §0's merge-timing constraint is **stale, not wrong**: it was correct when written and its condition
  has since been discharged.

### 7.4 Decided beyond scope

- **The old-file fixture in `GpxCodecTest` keeps the old URI, deliberately.** It represents "a file
  this app wrote before `rule` existed", and such a file genuinely carries `https://forager.app/gpx/1`.
  Updating it would make the fixture claim something false about old files. It also costs nothing:
  decode ignores the namespace.
- **The two `docs/audits/` files recording the old URI were not edited.** `docs/audits/README.md`
  states each audit is a historical record of what was observed on its date, superseded rather than
  edited. They correctly record what shipped then.
- **Assertions were added to existing tests rather than as new tests**, to satisfy §7's
  count-must-not-change while still making the change visible to the suite.
- **A KDoc was added to the constant** recording why the domain must be controlled and that `/1` is
  deliberately not bumped. The dispatch scoped "one string constant, its decode counterpart, and the
  test literals"; a comment is not a behaviour change, and CLAUDE.md requires the reasoning behind a
  non-obvious decision be recorded where it lives.

### 7.5 The two specific requirements

- **Push landed:** established by `git ls-remote`, and the pushed head is **not equal to `main`'s** —
  the cheap tell for a push that succeeds carrying nothing. Both SHAs recorded in §8.
- **Checks that did not fire:** recorded in §8 once CI has reported.

---

# 8. Beyond the dispatch — `applicationId` moved to the controlled domain

**Owner instruction, 2026-09-09, not part of the dispatch.** Added to this branch because it is the
same defect one layer down: an identity naming a domain the project does not control.

## 8.1 It was already known, and already had a tripwire

Not a discovery. `docs/legal/privacy-policy.md` has named `com.zynergylabs.forager.app` since it was
written, and `scripts/verify-policy-permissions.sh` **check 4 was failing on `main` on purpose** —
the beta-consent report records it as "correctly" failing and says it "clears when the rename
merges." It now clears.

**It was never dead prose.** `com.forager.app` is the live `namespace` for **433 source files** and
was the live `applicationId`. Renaming it wholesale would have broken the app.

## 8.2 One line, and `namespace` deliberately does not move

| | before | after |
|---|---|---|
| `applicationId` — Play's permanent public identity | `com.forager.app` | **`com.zynergylabs.forager.app`** |
| `namespace` — Kotlin package root, R/BuildConfig | `com.forager.app` | **unchanged** |

They may differ, and here they deliberately do. Moving `namespace` renames 433 files' package
declarations and every import, **and would rewrite every class name in CI's
`SKIPPED_TESTS_ALLOWLIST`** — a set this repo requires stay byte-identical in both directions. All
for a compile-time name no user, store listing or policy ever sees. If it is ever moved, that is a
mechanical refactor on its own, not a rider on an identity change.

**No code changed.** The FileProvider authority follows automatically: the manifest declares
`${applicationId}.fileprovider` and all three call sites use `"${context.packageName}.fileprovider"`,
which is the applicationId at runtime.

## 8.3 Why it had to be now

`applicationId` is **immutable after the first Play upload.** A different one is a different app —
different listing, no upgrade path for anyone who installed the old one. Nothing has been uploaded,
which is the only reason this was correctable. The window closes at the first upload: the same shape
of deadline as the GPX namespace URI and `rule` provenance.

**Consequence to record:** the signed AAB already built carries the old `applicationId` and is now
obsolete. It must be rebuilt before upload. Nothing else about the signing identity changes.

## 8.4 Evidence — the check bites

Reverting the one line and re-running `verify-policy-permissions.sh`:

```
FAILED (4): package name mismatch.
      app/build.gradle.kts applicationId: com.forager.app
      docs/legal/privacy-policy.md says:  com.zynergylabs.forager.app
```

Restored from a copy saved before editing. Checks 1–3 pass in both states, so the failure is
specific to this edit. **This is the check biting, not a synthetic revert** — the tripwire was built
for exactly this and had been red since it was written.

## 8.5 The local Windows baseline moves — and it *confirms* MAX_PATH rather than showing a defect

**Local failures went 10 → 12**, and the two that flipped are `LogPhotoMigrationTest` and
`MushroomLogMigrationTest` — **the only two migration classes that had never failed.**

Not a regression. The length hypothesis making a quantitative prediction and hitting it:

- the package name grew by **exactly 12 characters** (15 → 27), and Robolectric's temp path embeds
  it, so **every database path grew by 12**;
- the boundary previously sat at a **32-character** db filename (27 and 30 always passed, the two
  32s were intermittent, 34–39 always failed);
- 32 − 12 = **20**, and every db filename here is **27 or longer** — so all 11 migration classes
  should now fail.

**Observed: all 11 fail.** Nothing above or below behaved differently, because after the shift there
is no "below" left.

This is stronger evidence than anything in the Windows note so far, and of a different kind. Every
prior observation was **passive**; this is a **controlled perturbation** — path length moved by a
known amount, for an unrelated reason, and the predicted classes flipped. The note asked for
"measure the path length in the real exception text", which the flake pre-registration established
is impossible because the exception carries no path. This obtains the same answer without it.

**Still host-only, and CI is the authority.** Nothing silenced, skipped, weakened or allowlisted.
The Windows baseline is now **eleven or twelve** of a named pool, not nine or ten.

## 8.6 CI

**Green on Linux at `9de2a01`: 167 suites / 1309 / 0 / 0 / 24.** Test and skip counts unchanged,
which is what says the change is functionally inert everywhere but this Windows host's filesystem.
