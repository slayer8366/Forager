# Dispatch 2026-10-31-03: why the screen recomposes while recording with the screen off (a helper, Opus; look only)

Written by Rowan Waymark, 2026-10-31. RECORD -1905. The owner: "Look-only investigation (Recommended)".

**Base:** origin/pre-main d54189479 (#450 merged). Verify it before relying on anything below; every claim here is a premise to check.

**The finding (F-4):** docs/audits/2026-10-31-s22-phone-checks.md, "Part 2", on origin/s22-phone-checks c90507e71.
- The main thread ran at 29-39% of a core with the screen off while recording, 27.7% with the screen on, 0.7% at Home.
- A profile shows Compose recomposition running frame after frame while nothing is drawn: 42% AnimatedContent, 18% WordSwap.wordsOf at ui/motion/WordSwap.kt:23.
- The report's cause, recording state read in composition with the frame clock running under the always-on display, is inferred, not read.
- It is not like for like with trial C on 10-30, because the phone had no GPS fix.
- Evidence: /home/zynergy-labs/s22-2026-10-31/checks/part2/.

**Answer, from the code and that evidence:**
1. What drives the recomposition? Trace it to the state that changes and to its callers. Name each file and line.
2. Why does it run with the screen off? Is anything drawn at all? Check the always-on-display premise rather than assuming it.
3. Is it old or new? Find the commit that introduced it, and whether it is on main (the released app) or only pre-main.
4. Does it depend on the lack of a GPS fix? What would it cost with a fix?
5. Propose the fix or fixes. For each: what the user would notice, the size, the files, and what test would fail first. Keep to CLAUDE.md's Building section and the modular-code skill.

**Disclosure:** the report has four sections: confirmed vs inferred, could not determine, premises that were wrong, decided beyond scope.

**Output:** docs/audits/2026-10-31-recording-screen-off-recomposition.md on a new branch, recording-screen-off-report, cut from the base, in its own worktree ~/.cache/forager-wt/recording-screen-off-report. Pushed. No PR. Your final message: the answers, in plain language first.

## Rules
- No code changes outside the report. No fix.
- No Gradle, no adb, no phone. If an answer needs the phone, say exactly what measurement, and stop there.
- gh is logged in.
- The Restricted Object: never read, name or quote it.
- Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.
