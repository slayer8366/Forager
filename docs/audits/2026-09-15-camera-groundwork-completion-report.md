# Camera groundwork: `CaptureOutcome`, one bind function, a shared observable fake, ADR 0003

**Date:** 2026-09-15.
**Request:** the owner's plan following the verification pass on PR #102, tranche two ("next PR,
groundwork only, before tap-to-focus"), with the three conditions of 2026-09-15: stack on
`claude/new-session-vto65i` and retarget to `main` after #102 merges with one rebase; every revert
check runs against committed state; the seam is not widened here, torch waits for #102 to merge.
**Branch:** `claude/camera-groundwork-vto65i`, cut from `0e83bd4` (PR #102's head at the time),
targeting `claude/new-session-vto65i`. No schema change, no dependency change.

---

## What landed

**`CaptureOutcome`.** `capture` returns `Result<CaptureOutcome>` — the destination file and the
`ImageFormat` CameraX reports — instead of `Result<Unit>`
(`CameraCaptureSession.kt`, the `capture` signature and the data class beneath it). Five edit
sites, as the verification pass priced it: the interface, the CameraX resume, the dialog's `fold`
(unchanged in behaviour; the value is ignored), the fake, and the instrument test. `imageFormat`
has no production reader; the class doc and ADR 0003 both say so and why.

**One bind function.** `CameraXCaptureSession.bindUseCases(provider, lifecycleOwner, selector,
useCases)` is now the only place use cases meet a selector and a lifecycle. The caller builds the
use cases and keeps its references; adding one is adding to the list. No test reaches it, which is
structural, not a gap left.

**A shared, observable fake.** `FakeCameraCaptureSession` moves out of `InAppCameraDialogTest` to
`app/src/test/.../photo/`, beside `FileProviderCacheReset`, `internal`, with `state` backed by
`mutableStateOf`. The private original was a plain `var`, so a state flipped after composition
silently did nothing; no test relied on that. One test now asserts the opposite, "a session that
becomes ready after composition enables the shutter", and a revert to a plain `var` fails exactly
it. The fake's partial-file-on-failure behaviour and its `captureCalls` counter carry over
unchanged, with their reasons on the class.

**ADR 0003** (`docs/adr/0003-camera-seam-coordinates-and-capture-modes.md`) records the
conventions the later stages follow: fractions of the field at the seam, converted only inside
it; one method plus one capability read per feature, unsupported exposed rather than guessed
(CLAUDE.md:46–49); the tap gesture in the dialog with one callback added to the viewfinder slot
when tap-to-focus lands; bursts baking rotation and storing `ORIENTATION_NORMAL`; research bundles
as their own directory, never `LogPhoto`s, never swept; OEM Night over an in-app merge when
available; and the two deferrals with their reasons.

## The runner's committed-state guard

Condition two, made mechanical: `revert-check.py` now refuses to run if `git status --porcelain`
reports the target file as modified. Its first live exercise was this dispatch's two reverts, both
run after the commit; the guard let them through because the file was committed, which is the
guard doing nothing visible, and the reason it is recorded here rather than assumed working. It
was not exercised on a dirty file. That is the check that would prove it bites, and it was not
run; noted under "checks that did not fire".

## A citation the ADR could not make

The plan asked ADR 0003 to record that the allowlist scrub reverses "photo-geodata decision 5".
Five source files cite "the photo-geodata amendment" (`MushroomLogViewModel.kt:159`,
`Migrations.kt:724`, `FilePhotoStore.kt`, `PhotoAcquisitionLaunchers.kt`, `LogPhotoEntity.kt`);
**no file under `docs/` contains the word "geodata"** (grep over `docs` and `app/src`,
2026-09-15). The amendment is not in this tree. Decision 5's content is known only from the
sentence `FilePhotoStore.kt`'s header used to carry, quoted in the ADR. Whether it exists on
another branch was not checked. Recorded as unverified in the ADR rather than cited as if read;
the earlier pulse document in this PR turned up on an unmerged branch the same way.

---

## Evidence

### Suite counts, from JUnit XML

| | suites | tests | failures | errors | skipped |
|---|---|---|---|---|---|
| before (`0e83bd4`, PR #102 head) | 180 | 1452 | 0 | 0 | 24 |
| after (`1a8b06e`) | 180 | 1453 | 0 | 0 | 24 |

1452 + 1 (the observability test) = **1453**, counted. Skip count unchanged; CI allowlist
untouched. `assembleDebug` exit 0. Zero `e:` lines. Container runs only.

### Revert checks, both after the commit

| revert | failure produced |
|---|---|
| fake `state` back to a plain `var` | exactly "a session that becomes ready after composition enables the shutter": "Failed to assert the following: (is enabled)" |
| fake reports `YUV_420_888` instead of `JPEG` | exactly the instrument test's "and names what it wrote" |

The second is a test of the fake's contract, which is what it claims to be. The bind function and
the CameraX resume have no revert check and cannot: nothing under Robolectric reaches them.

---

## Disclosure

### Confirmed by observation
- Every count and message above, from XML and build logs in this container.
- `OutputFileResults`' two members, `SessionConfig`, and the `SessionConfig` bind overload in the
  pinned 1.6.2 artifacts, by `javap` in the verification pass.
- The absence of the geodata amendment from `docs/`, by grep.

### Inferred, not observed
- That `results.imageFormat` reports `ImageFormat.JPEG` for a file capture on a device. The fake
  says so; CameraX's writer does; the device has not.

### Could not be determined
- Whether the runner guard refuses on a dirty file. Not exercised.
- Anything about the camera itself, unchanged from the previous reports.

### Premises that were wrong
- None found in this dispatch's own premises. The plan's citation of "decision 5" as a document
  turned out to be a citation of a sentence in a code comment; reported above.

### Decided beyond scope
- **Naming the branch `claude/camera-groundwork-vto65i`** to tie it to this session, matching the
  repo's `claude/<topic>-<suffix>` shape.
- **The `docs/audits/README.md` row lands on the stacked branch**, which PR #102's branch does not
  have. The index is a serialization point (CLAUDE.md); the rebase after #102 merges keeps every
  row, per the rule.

### Checks that did not fire, and empty results
- No CI run at the time of writing.
- The runner guard's refusing path.
- No test asserts anything about `bindUseCases`; it is device-only by construction.
