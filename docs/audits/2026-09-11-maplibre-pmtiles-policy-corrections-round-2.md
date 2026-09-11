# Corrections round 2, and the repository question answered

**Date:** 2026-09-11
**Type:** correction record. **No code changed.**
**Follows:** `2026-09-11-maplibre-pmtiles-policy-corrections.md`, same day.
**Origin:** the owner checked round 1's new claims. Three of mine went past the evidence, one of
their round-1 corrections is withdrawn by them, and they raised a repository concern they asked be
settled before anything else.

**The repository concern resolves in the project's favour on both halves, and the second half is
the same failure this project has documented four times.** Taking it first, as asked.

---

## R1. The lifecycle-gate branch: the code is present, and was never stranded

**The concern.** `forager-bak`'s only non-`main` branch is `claude/lifecycle-gate-live-location`,
which does not exist in `Forager`. If stranded, the published privacy policy would describe code
the active repo does not have.

**Answered from the tree, not from the refs.** The code is present, wired, and tested:

- `AvailabilityViewModel.onEnteredForeground()` / `onLeftForeground()` acquire and release the
  live-fix subscription; cancelling the collection completes the `callbackFlow`, which is what runs
  `removeUpdates(listener)`.
- `MainActivity.kt:225-226` drives them from the Activity lifecycle:
  `override fun onStart(owner: LifecycleOwner) = viewModel.onEnteredForeground()` /
  `override fun onStop(owner: LifecycleOwner) = viewModel.onLeftForeground()`.
- Tests exercise the release and re-acquire directly: `AvailabilityViewModelLiveFixTest.kt:301,
  305, 320`.

**It landed as a commit, not as a surviving branch.** `5967dd5`, 2026-09-09 21:40:41 -0700,
"Release the live-fix OS subscription when the app leaves the foreground", reachable from `main` in
this repository. Its message states the same mechanism the privacy policy describes.

**So the published policy is backed by code in the active repository.** Nothing is stranded, and no
port is needed.

**The inference worth naming:** a branch's absence was read as a code absence. A merged branch
leaves no ref. This is `CLAUDE.md`'s reachability rule again, in its mirror image — the rule says
ask who calls this before measuring what it produced, and the same discipline says ask whether the
code is *present* before concluding a missing ref means missing behaviour. One `grep` closed it.
Recorded without any edge: round 1 records me making the same class of error on the User-Agent
finding, and the point of writing it down both times is that it is cheap to hit and cheap to check.

## R2. "A repo with PR refs is the retired one" inverts the project's own record

**The claim.** `slayer8366/Forager` is public with 94 pull-request refs; by `CLAUDE.md`'s test a repo
with PR refs is the retired one; therefore the repository carrying the retired domain claim in its
history is publicly readable.

**Two things are true and the conclusion still does not follow.**

**First, `CLAUDE.md` contains no such test today.** Searched it for `retired`, `forbidden term`,
`pull request` and `PR ref`: zero hits. The section stating that rule was removed by the owner's own
rescission on 2026-09-10. The test now lives only in `docs/audits/README.md`, which is a historical
record of a rule that was lifted, not a standing rule.

**Second, the record says the opposite about which repo the PR refs belong to.**
`docs/audits/README.md`, the last 2026-09-10 row, verbatim:

> **The work done in `forager-bak` is merged back into `Forager`**, which is the live repository and
> holds the history — 45 branches, 93 pull refs, 649 commits, none of them rewritten or deleted.

So `Forager` holding ~94 pull refs is the documented, expected state of the **live** repository. The
"no PR refs" property belonged to the migrated-*to* repository during the migration window, and the
move back on 09-10 deliberately returned the project to the repository that has them. `forager-bak`
acquiring `refs/pull/1/head` is also already on the record, in the rescission row: "this repository
**has had a pull request** (#1, merged, `refs/pull/1/head` permanent) — the property that motivated
the no-PR rule was already spent."

**This is the derived-figure family, sixth form.** A categorical claim, correct in the scope where it
was written (the migration window, about the migrated-to repository), carried into a scope where it
inverts, with nothing in the carrying to mark the change. `CLAUDE.md`'s own entry describes exactly
this: "A number, an interval, or a categorical claim is correct about the thing it was derived from
and stops being correct the moment it is quoted about something larger." The check that catches it
is the same one that caught the other five: read the claim against the record rather than from
memory of the record.

**What is true, stated plainly, because the underlying facts are not in dispute.** `Forager` is
public (`"visibility": "public"`, GitHub API). `com.forager.app` appears in **44 commits** of its
history (`git log --all -S`, on this shallow clone of 217 of the 649 commits, so 44 is a floor).
Anyone can read them.

**And the owner already ruled on exactly that**, 2026-09-10:

> **The forbidden-term rule is rescinded** (owner, 2026-09-10). `com.forager.app` is no longer a
> forbidden term. The substantive fix is **not** rescinded and must not be reverted: `applicationId`
> and `namespace` both remain `com.zynergylabs.forager.app`, because the old value asserted control
> of a domain this project does not own — that was always the defect.

So the public readability of those 44 commits is a state already ruled acceptable, by the person
raising it, one day earlier. **Re-opening it is entirely the owner's call and this document does not
argue against doing so** — a ruling can be revisited, and the migration row itself records that the
first decision was reached by a path later judged too expensive. What this section establishes is
only that it would be a *revision* of a standing ruling rather than the discovery of an unnoticed
exposure, which changes what the decision is, not whether it is available.

---

## What I got wrong in round 1

### W1. OFL reserved font names do not apply here

**The owner is right and this withdraws the whole note.** `openmaptiles/fonts/open-sans/LICENSE.txt`
is the **Apache License 2.0**, which has no reserved-font-name clause. `BasemapStyles.kt:155` names
exactly one fontstack, `AREA_MARKER_FONT_STACK = arrayOf("Open Sans Semibold")`, so OFL never
enters.

Round 1 said "OFL-1.1 carries a reserved-font-name restriction [...] a bundling step that renames or
re-generates them needs checking against that clause." Withdrawn. The upstream README's "either OFL
or Apache" is a statement about the *repository's* fonts collectively; I resolved it to the
stricter of the two rather than checking which applies to the one font this project uses. That is
the same shape as the error round 1 was written to correct: a general claim narrowed to a specific
case by assumption instead of by checking.

**What survives, and it is the actionable half:** bundling adds **Apache-2.0** obligations. Ship the
licence, keep the `NOTICE` if present, and — because the PBFs are generated rather than copied —
mark them as modified per §4(b). It still folds into finding #6.

**The OFL question returns only if a future fontstack is added.** Worth a line in whatever bundles
them.

### W2. "Fall back to the z14 archive" is not a drop-in fix

**The owner is right.** A z14 tile cannot answer a z15 request as served; the Worker would have to
cut and re-encode it, which is real work and new failure modes.

**The option neither of us named, and it may be the actual answer.** The overflow path exists
because the Worker *advertises* z15. `index.ts:315`:

```ts
const advertised = { ...t, maxzoom: Math.max(declaredMaxZoom, OVERFLOW_MAX_ZOOM) };
```

Advertise the archive's real `maxzoom` of 14 instead, and MapLibre over-zooms the z14 tiles
client-side as standard behaviour — it never requests z15, the overflow path is never reached, and
findings #2 and #3 dissolve together with the frozen-cache defect and the 500-on-resolution-failure.

The cost is honest and is the owner's to weigh: over-zooming gives **no new detail**, and real z15
data in Protomaps' build is precisely what the overflow path was built to reach. So this is "delete
the risk surface and lose one zoom level of detail" versus "extract z15 into R2 and keep it." Both
are legitimate; the first is a one-line change and the second is the one the audit recommended.

**On the logging conflict, the owner's resolution is right and the conflict is narrower than it
looks.** `CLAUDE.md` forbids an unlogged fallback; the privacy policy forbids tile *request* logs,
and `wrangler.toml`'s `[observability] enabled = false` exists because invocation logs record which
areas users looked at. A **response header** records nothing server-side, so it satisfies both
without tension. And as they say, the same header can name the build each tile came from, which
closes the provenance gap round 1 opened.

**Their seam point is correct and sharper than round 1's framing.** Round 1 said the overflow layer
"silently diverges over time." The real artefact is worse: because the cache key is per-tile and
date-free, **neighbouring z15 tiles can be frozen at different builds**, so an OSM edit landing
between two builds shows up as a discontinuity at a tile boundary — a road or coastline that does
not meet itself across a seam. That is a visible rendering defect, not a slow drift.

### W3. Four recommended items, not three, and two of them are code

**The owner is right.** The policy's "You should (recommended)" list has four entries:

1. "Avoid hard-coding the tile URL; allow switching without needing a software update."
2. "Add a 'Report a map issue' link to `https://www.openstreetmap.org/fixthemap`."
3. "Publish a contact email on your website or app store listing."
4. "Support HTTP/2 or HTTP/3 for efficient multiplexed downloads."

I omitted (4) and called only the hard-coded URL a code item. The fixthemap link is UI, so **two of
the four are code**.

**Their point about the hard-coded-URL remedy is the one that changes the priority.** "Switching
without a software update" means fetching configuration remotely, which is a **new network request
from a field app that currently has a fully enumerated egress list** — one that
`2026-09-10`'s Data safety verification enumerated host by host, and that
`docs/legal/privacy-policy.md` names in a per-host table. Adding a config fetch means amending both.
That is a disproportionate cost for a *recommended* item, and it is a good argument for leaving the
URL hard-coded and taking the fixthemap link instead.

### W4. The `LICENSES.core.md` count is an upper bound, not the Android set

**The owner is right.** 29 headings, minus MapLibre Native itself (the subject, already covered by
`LICENSE.md`), minus `metal-cpp` (Apple's graphics API, absent from the Android build) leaves **27**.

Adding one caveat in the same direction: 27 is still an upper bound, since components such as
`FSST`, `FastPFOR` and `SIMD Everywhere` may be conditional on build flags this project does not
control and may not be linked into the shipped `.so`. The honest statement for a notices screen is
that the set is "up to 27, to be confirmed against the artifact actually bundled" — and for a
notices screen, over-inclusion is harmless while omission is the risk, so shipping all 27 is the
safe default.

## W5. Prefetch: settled, not filed

**The owner is right and this closes it.** `MapLibreMapOptions.java:77-78`:

```java
private boolean prefetchesTiles = true;
private int prefetchZoomDelta = 4;
```

confirmed by the setter's own javadoc, "Default zoom delta is 4."

A z−4 tile covers **the same ground already on screen** at lower resolution while the real tiles
load. The policy defines bulk downloading as "pre-emptive fetching of tiles other than those a user
is actively viewing" and explicitly permits "modest, short-range look-ahead typical of browsers";
its prohibited examples are pre-seeding, archives, wide scans and offline downloads. One z−4 tile
covers the ground of 256 tiles at the current zoom, so this reduces requests rather than multiplying
them.

Not a finding. Removed from the register rather than carried as an open question.

## Their round-1 correction that they withdrew

Round 1 recorded a partial disagreement about whether the demotiles quote was spliced. The owner has
since confirmed that line 2 of the **main-branch** README matches the quote word for word, and
withdrawn the splice claim. Recorded here so the round-1 section is not read as still contested.

---

## Where the priorities stand

Unchanged from round 1 except where the above moves them:

0. **The visibility decision** — the owner's, informed by R2: it is a revision of their own 09-10
   ruling, not a new exposure. The lifecycle-gate question (R1) is **closed, no action**.
1. **#3 bbox check** — still the same-day stopgap.
2. **#2** — now a genuine fork. Either advertise `maxzoom: 14` and delete the overflow path entirely
   (one line, loses z15 detail, dissolves #2, #3, the frozen-tile seam and the 500), or extract z15
   into R2. The first option is new since round 1.
3. **#1** — unchanged.
4. **#5 + #6** — #5's obligation is **Apache-2.0**, not OFL: licence, notices, and a modification
   mark. #6 ships up to 27 `LICENSES.core.md` components alongside whatever a plugin produces.
5. **#4** — optional; of the four recommended items, take the fixthemap link and leave the
   hard-coded URL, since remote config would amend the privacy policy and the Data safety form.
6. **ODbL** — unchanged.

## Disclosure

**Confirmed this session:** `5967dd5`'s existence, date and message and its reachability from `main`;
`MainActivity.kt:225-226`; the three test line numbers; `CLAUDE.md` containing no retired-repo test
(zero hits on four search terms); the two `docs/audits/README.md` rows quoted verbatim;
`Forager` public via the GitHub API; 44 commits matching `com.forager.app`;
`openmaptiles/fonts/open-sans/LICENSE.txt` as Apache-2.0; `MapLibreMapOptions.java:77-78` and its
javadoc; the policy's four-item recommended list, re-read from the fetched text.

**Stated as a floor, not a count:** the 44 commits, because this clone is shallow — `git rev-parse
--is-shallow-repository` returns true and `main` shows 217 of the recorded 649 commits, with 4 local
refs against the ~94 the owner listed from GitHub. Every claim here about refs and pull requests
comes from the owner's GitHub listing or from `docs/audits/README.md`, never from this clone.

**Not determined:** which of the 27 `LICENSES.core.md` components are actually linked into the
shipped `.so`, which is why W4 gives an upper bound.

**Not argued either way:** the repository visibility decision itself.
