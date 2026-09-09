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

Local Windows full suite at the change: **167 suites / 1309 tests / 10 failures / 24 skipped.**

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
