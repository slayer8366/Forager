# Completion report — source package rename and the forbidden-term gate

> **The gate this report describes does not exist in this repository.** It was built in the retired
> repository and is recorded here as history. It was **deliberately not carried into the migration**:
> a gate has to spell the forbidden term in order to match it, so carrying it would have put that
> string into this repository's first commit permanently — reintroducing on day one the exact
> condition the migration exists to eliminate. In the old repository the trade was worth it against
> 649 contaminated commits; here it inverts. Read every present-tense statement about the gate below
> as describing the retired repository.

**Dispatch:** source package rename, term sweep, repo migration (2026-09-09).
**Base:** stacked on `claude/gpx-namespace-domain` (PR #92 head `84f5437`) — see §6.
**Branch:** `claude/package-rename`. **Prediction pre-registered at `0bf06a7`, before any run.**

**§7 (repo migration) is NOT done.** §7 steps 1, 3 and 4 are merges and an irreversible public
action. See §6.

---

## 1. §0 — both rulings were present

- **Ruling A**, the exact string `com.zynergylabs.forager.app`, is stated in the dispatch. Used
  verbatim; not shortened, not tidied, and its match with `applicationId` was preserved, not removed
  as redundancy.
- **Ruling B**, the allowlist exemption, is stated in the dispatch.

Neither was missing, so the refusal §0 describes was not triggered.

## 2. What was done

**445 renames, 44 modified files, 0 added, 0 deleted.**

| item | outcome |
|---|---|
| `namespace` in `app/build.gradle.kts` | changed; now matches `applicationId`, which did not change |
| Package declarations and imports | 433 files (267 main, 166 test) |
| Directory structure | the source root moved to `app/src/*/java/com/zynergylabs/forager/app/**` (the old path cannot be written here — see §7.4) |
| `SKIPPED_TESTS_ALLOWLIST` | 24 classnames, prefix substitution only — verified as a correspondence, §3 |
| **Room exported schemas** | directory moved with the database class's FQN; **12 migration JSONs moved as pure renames, no content change**, and a compile afterwards produced no second schema tree |
| ProGuard rules | **empty change set — see §5.3** |
| Manifest | **empty change set — see §5.3** |
| Docs, dispatches, audits | 35 documents swept, each carrying a redaction marker (§4) |
| Fixtures | swept with everything else |

**Explicitly not changed**, per §3: `applicationId`, the FileProvider authority (templated off
`${applicationId}`/`context.packageName`), the GPX namespace URI, and the app's name anywhere.

**Verification that the term is gone:** `git grep` over tracked files returns **zero** occurrences of
either the dotted or slashed form, excluding the gate's own definition.

## 3. Ruling B verified as a correspondence, not a count

Normalising the package prefix to a placeholder in the before and after copies of
`.github/workflows/ci.yml` and diffing them:

```
DIFF EMPTY — every entry corresponds one-to-one, prefix substitution only
```

So every entry's `name` is byte-identical and each classname differs only in its prefix.

**Observed count: 24 before, 24 after.** Reported as an observation. It was not used as a target and
nothing was reconciled to it — per §0, a differing count would have been a finding and a stop, not a
discrepancy to fix.

## 4. §2's three overruled exceptions — all three followed

| | done |
|---|---|
| Historical audit docs | **Edited.** 35 documents carry a visible marker naming the date and the policy, stating the file is **not an original record** and that the change is textual only. An altered record does not read as original. |
| The GPX old-file fixture | **Regenerated with the new value.** It loses the "written before" property; that cost was accepted, not re-argued. |
| `verify-policy-permissions.sh` check 4 | Superseded by the §5 gate. |

The arguments for keeping the term were not re-derived. This report does not re-litigate them, per
the dispatch's header.

## 5. The gate (§5), and the prediction (§6)

### 5.1 The gate was demonstrated to fail

A gate never seen to fire is the failure mode this project is named for, so:

1. **Clean tree → PASS.**
2. **One occurrence introduced deliberately → FAIL**, exit 1, printing
   `docs/audits/2026-09-09-package-rename-prediction.md:63:GATE DEMONSTRATION LINE: <term>` and the
   `::error::` annotation.
3. **Test occurrence removed** — restored from a copy saved before editing, never from git —
   **→ PASS.**

The gate spells the term once, deliberately, and excludes itself by pathspec. That is the enforcement
mechanism, not a leftover; a pattern contrived to avoid spelling it would be weaker enforcement, and
§5 reserves that trade to the owner. **`git grep` sees tracked files at this commit and cannot police
history** — which is precisely why §7 exists.


### 5.1a It then fired for real, in CI, on this very document

The synthetic demonstration above is the weaker evidence. The gate's **first CI run failed**, and it
was right:

```
docs/audits/2026-09-09-package-rename-completion-report.md:29:
  | Directory structure | `app/src/*/java/<retired root>/**` → ... |
```

Writing the report, I spelled the retired path in the "what changed" table to describe the move —
**after** running the local check. The local pass was therefore a pass on a tree that was not the
final tree, which is an ordering error of exactly the kind this project keeps naming: the check ran
on a sample that did not include the case that could fail it.

Two things follow, and both are worth more than the demonstration:

1. **The gate caught a real occurrence introduced by the person who built the gate**, in the
   document arguing the gate works. That is the strongest form of evidence it functions.
2. **The rule has a second-order consequence, now demonstrated rather than predicted:** prose
   describing this change cannot name its own subject. The table row was rewritten to give only the
   new path. Any future document explaining the rename inherits the same constraint.

The local check was re-run on the final tree after the fix, and passes.
### 5.2 The §6 prediction, and the outcome — in that order

**Predicted, at `0bf06a7`, before the rename was made:** *no change; 12 failures.* The reasoning was
that `context.getPackageName()` returns `applicationId`, not the Gradle `namespace`, so the
Robolectric database path does not grow; and that even if it did, all 11 migration classes already
fail and nothing is left to flip.

**Outcome: 12 failures.** The same 12 — 11 migration classes plus
`AvailabilityScreenSettingsPanelTest`.

**As predicted, and non-discriminating** — which the pre-registration said in advance. A 12 outcome is
equally consistent with "`namespace` contributes nothing" and "`namespace` contributes but there was
nothing left to flip." Only a count above 12 would have discriminated, and it would have falsified
the prediction. **This run does not strengthen the mechanism claim** and is not offered as if it did.

**The threshold value remains unmeasured.** That the limit is 260 is still not established, and this
dispatch does not upgrade it.

### 5.3 Test counts

| | suites | tests | failures | skipped |
|---|---|---|---|---|
| before (PR #92 head) | 167 | 1309 | 12 | 24 |
| after the rename | 167 | 1309 | 12 | 24 |

**The count did not move**, as §6 required of a pure rename. Nothing needed reconciling.

## 6. §7 — the repo migration is NOT done, and why

§7's ordering is: merge PR #92 → land the rename → seed a new repository → dispose of the old one.

**Steps 1, 3 and 4 were not performed.**

- **Step 1, "merge PR #92 first", is a merge, and §8 of this same dispatch says nothing is
  pre-authorized.** The standing rule in this project is explicit that one must not *act on* a
  dispatch clause that pre-authorizes a merge. Rather than stall, the rename was **stacked on PR
  #92's head**, which yields the same tree §7.1 was protecting — nothing in flight is lost — without
  performing an unauthorized merge. Disclosed under §9.4.
- **Steps 3 and 4 need explicit authorization.** Seeding a new repository and **deleting or
  privatising the existing public one** is irreversible and outward-facing. It is not something to do
  on a dispatch clause.

**The cost §7 states, restated so it is not discovered later:** after migration the audit trail
becomes a set of documents rather than a history. Every SHA in `docs/audits/` and in every completion
report — including the ones in *this* report — will point at a repository that no longer exists. The
index rows survive; their references do not. **No attempt should be made to rewrite those references
to new SHAs**; a fabricated correspondence is worse than a broken one. One row recording the
migration and its date is the correct treatment, and it has not been added because the migration has
not happened.

## 7. Disclosure

### 7.1 Confirmed vs inferred

Every row of §3 was re-derived at HEAD. Counts: 267 main + 166 test package declarations = 433; 433
files under the old path; 12 schema JSONs; 24 allowlist classnames. All confirmed by direct count,
none inferred.

### 7.2 Could not determine

- **Whether `zynergy-labs.com` is registered to this project.** Taken from the owner's ruling; not
  verifiable from the repository and no network check was made.
- **Whether anything outside the tracked tree carries the term** — build outputs, IDE caches, local
  clones, the existing signed AAB. The gate is scoped to tracked files by construction.
- **Nothing was checked on a device.** §4's storage-path risk is reasoned, not observed.

### 7.3 Premises that were wrong

**Two rows of §3 have empty change sets at HEAD**, and the dispatch asked to hear about exactly this:

- **ProGuard rules:** `app/proguard-rules.pro` exists and contains **zero** occurrences of the term.
  Nothing to change.
- **Manifest:** `AndroidManifest.xml` names **no** component by fully-qualified name. Every
  `android:name` in it is an `android.*` permission, `androidx.core.content.FileProvider`,
  `android.support.FILE_PROVIDER_PATHS`, or `org.inaturalist.android`. Components use leading-dot
  shorthand, so nothing to change.

Neither cost anything — the sweep would have caught them had they existed — but §3 warned that prior
versions of its list had been wrong, and these two rows are.

Nothing in §0's rulings, §1's rule, or §2's three overruled exceptions was found wrong.

### 7.4 Decided beyond scope

- **Stacking on PR #92 rather than merging it** (§6 above).
- **Rewriting the `build.gradle.kts` comment.** The comment added yesterday explaining why
  `namespace` would stay was swept into asserting the opposite — that the namespace "stays
  `com.zynergylabs.forager.app`" — and was rewritten rather than left self-contradictory. It is also
  now unable to name what it moved off, which is a general consequence of the rule worth noting: any
  prose describing the change cannot name its own subject.
- **The redaction marker's wording and placement** (after line 1, so it is unmissable). §2 required a
  visible marker naming the date and the policy; the exact text was not specified.
- **The gate's self-exclusion by pathspec**, so it does not match its own definition.

### 7.5 Specific requirements

- **§6 prediction then outcome:** given in that order in §5.2. Predicted 12; observed 12; reported as
  non-discriminating.
- **§5 gate demonstrated to fail:** §5.1, with the exact failing output.
- **Push by `ls-remote`:** `refs/heads/claude/package-rename` = `892883c`, equal to the local head and
  **not** equal to `main`'s `28bcc3b` — the tell for a push that succeeds carrying nothing.
- **Checks that did not fire:** recorded once CI reports.
