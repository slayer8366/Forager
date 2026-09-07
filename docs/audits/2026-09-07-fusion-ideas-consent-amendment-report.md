# Report: consent and corpus-ownership section added to the fusion ideas document

**Amendment:** "Add a consent and corpus-ownership section to the fusion ideas document" (planner,
owner-directed). Documentation only; no code, no tests.

**Target, confirmed:** `docs/plans/ideas-from-fusion-plan.md` on branch `claude/ideas-fusion-plan`
(re-cut from `main` after the #74 merge was reverted at the owner's request), previously at
`a65d068`; **base `main` at `69e4a7b`.** Unmerged; the owner has said the docs branches go to `main`
together once the open branches are caught up. Not merged here.

**Suite as found, unchanged:** 1201 tests, 0 failed, 24 skipped; skip set byte-identical to the CI
allowlist (last full run, PR #73's head).

## What was added

One section, **"Consent and who owns the corpus"**, inserted after "Explicitly rejected" and before
"Sequencing", in the document's own voice and without quoting the amendment. It covers, in
substance: the two corpora (the owner's own logs as a byproduct of testing versus testers' logs, for
which the debugging switch does not cover a public corpus); that the consent, if ever wanted, is a
second, specific ask naming what the file contains and where it goes, in the register the beta trip
report's track-file paragraph already uses; that location traces cannot be anonymised because the
trace identifies the place; the private-corpus / published-results shape; and the design consequence
of keeping fusion logic free of Forager's domain types, **with the current state of the code
reported and not acted on.** Nothing else in the document moved.

## The code question, answered from reading

*Does the code already keep fusion logic free of domain types?* **In substance yes, in signature
no.** Every piece is pure and Android-free. Most is typed on this app's classes: the network-fix
predicate on PR #73 is a `TrackPoint` extension whose body is one `Long` expression; `acceptLiveFix`
takes `LocationFix.Update`; `LocationSampler.shouldAccept` takes `TrackPoint`s; `CompassTrustJudge
.next` takes `CompassReading`. Already type-free: `isApproaching`, `fixFreshness`,
`relativeBearingDegrees`, `HeadingSmoother.next`. Extraction would change parameter lists, not
logic. Read on `main` at `69e4a7b` and on PR #73's head `51dccb9` (the predicate is not on this docs
branch, which is cut from `main`).

## Verification (nothing to test)

The section is present, in the document's voice; it distinguishes the owner's logs from testers'
logs explicitly; it states that location traces cannot be anonymised; it records the private-corpus
/ published-results shape; a diff of the file against `a65d068` shows one inserted block and no
other change. Not added to `docs/beta/` or the template. Nothing scheduled.

## Required disclosure

**Confirmed:** the path, branch, base and commit; the signatures above by grep on both refs; the
diff scope. **Inferred:** nothing. **Could not determine:** nothing needed. **Premises in this
amendment that were wrong:** none — one small correction of fact: the predicate "was built as a
pure domain function" is right, and it is typed on `TrackPoint`, which is the part the amendment
asked about. **Decided without cover:** the section's heading and its placement of the code-state
paragraph inside the design-consequence subsection rather than as a separate report; the one added
clause in the consent paragraph ("with one more sentence — where the file would end up"), which
follows from the amendment's own text.
