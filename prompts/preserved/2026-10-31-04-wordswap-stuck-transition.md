# Dispatch 2026-10-31-04: WordSwap stops spinning when only a number changes (Hazel, Opus)

Written by Rowan Waymark, 2026-10-31. RECORD -1907. The owner: "Build the small fix (Recommended)".

**Base:** origin/pre-main; verify its head before relying on anything below. Every claim here is a premise to check.

**Read:** docs/audits/2026-10-31-recording-screen-off-recomposition.md on origin/recording-screen-off-report (ff7dbefc4), fix A and the "not established" list.

**Build fix A:** WordSwap animates on the text's words as its target state, and the content reads the latest text, so a number-only change never starts a transition. The words-changed fade stays exactly as it is. Nothing on screen changes. Keep it about the size the report says, in WordSwap.kt and WordSwapTest.kt. It covers all 14 call sites.

**Stop and report, without building further, if:**
- the fix needs another file, or changes what any call site shows;
- no test can fail for this reason.

**Tests:**
- Fail first through the real path: stop the activity, change a number, count compositions over several frames. The report says it is unverified whether the Robolectric rule uses the real lifecycle recomposer. Check that first. If the test cannot reproduce the spin on the unfixed code, say so plainly and do not cite it as proof.
- A test that the shown text still updates to the latest number, and one that a real word change still fades.
- Then a revert check. The build log must show 0 compile errors, and restore is byte for byte.

**Out of scope:** StateCrossfade, PageSlide and JournalTab.kt:622 (name anything you notice, change nothing); collectAsState in MainActivity; the phone run, which is a later dispatch.

**Output:** branch wordswap-stuck-transition in its own worktree ~/.cache/forager-wt/wordswap-stuck-transition, a PR to pre-main, full CI green, not merged. Your final message: head, the CI run, verdicts, what is not verified.

## Rules
- CI only, no Gradle on the laptop, no adb. gh is logged in.
- Merge, never rebase or force-push.
- The Restricted Object: never read, name or quote it.
- Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.
