# Dispatch 2026-11-06-02: delete merged and throwaway branches on GitHub (Hazel, Opus)

Written by Rowan Waymark, 2026-11-06. RECORD -2182. Every fact below is a premise to re-check against origin.

## Authorisation
The owner, verbatim, to "Cleaning pre-main and main removes your home location from the app's current code. But GitHub also has 1,315 older branches (merged work, test branches, reverts), and each still shows the values in its current files, not only in history. What should happen to those?": **"Delete the old branches (Recommended)"**. The option read: "After the clean-up, delete merged and throwaway branches on GitHub, keeping the working ones (main, pre-main, the records branch and open PRs). A full local backup …" (RECORD -2182).

The clean-up is done: PR #499 merged into pre-main as 05d0ab53a, and pre-main was merged into records-after-173 as 3ece9d49b.

## What the owner's words cover
- **Delete:** branches that are merged and branches that are throwaway.
- **Keep:** main, pre-main, records-after-173, and every branch with an open PR.
- **The planner adds to the keep list, for the owner to see:**
  - tools/s22-combined, the planner's working build branch;
  - dev, if it exists (CLAUDE.md: "experiments on dev");
  - any branch a Hazel is working on now: nav-card-kind-line (#498) and next-turn (#500), which also have open PRs.
- **Not covered by the owner's words, so not deleted:** a branch that is neither merged nor throwaway, meaning unmerged work with no open PR. For example, route-navigate (fae14e6e7, 2026-11-04, by the next-turn Phase 1 report). These come to the owner as a list.

## Phase 1: inventory and backup; delete nothing
1. **Fetch every branch** (`git fetch origin --prune`), and count them against `git ls-remote --heads origin`. The two counts must agree.
2. **Make a full local backup** of every remote branch: one bundle at `~/Zynergy/forager-repo-backups/all-branches-2026-11-06.bundle`, with all refs. Verify it with `git bundle verify`. Its list of heads must match the ls-remote list exactly (same names, same SHAs). Write a manifest (name and SHA per line) beside it.
3. **Classify every branch** into exactly one class, with the rule used:
   - **keep:** the list above, plus each open PR's head branch (`gh pr list --state open`);
   - **merged:** its tip is an ancestor of origin/main, origin/pre-main or origin/records-after-173 (`git merge-base --is-ancestor`);
   - **merged by content:** not an ancestor, but every commit's patch is already in one of those (`git cherry`), or it was the head of a merged PR (`gh pr list --state merged`, matching head SHA or name). Say which test put it there;
   - **throwaway:** a name that marks it as a probe, revert or scratch (for example `scratch/`, `*-red*`, `*-green*`, `*-probe*`, `*-throwaway*`, `*-measure*`, `d32-revert-*`, `hlo-*`), or a branch whose only commits are reverts of merged work. List the name patterns used, and every branch they caught;
   - **unmerged:** everything else, each with its last commit date and subject, and whether a closed PR exists for it.
4. **Counts:** the classes must add up to the total.
5. **Report** to the planner as your final answer:
   - the counts;
   - the keep list in full;
   - the unmerged list in full;
   - for the merged and throwaway classes, counts with 10 examples each;
   - the bundle path, size and verification;
   - the four disclosure sections.

## Phase 2: on the planner's go only
- Delete the merged and throwaway branches with `git push origin --delete`, in batches. Re-fetch before each batch and skip any branch whose SHA changed since the manifest.
- Afterwards, count what remains against the keep and unmerged lists.
- Nothing in the unmerged class is deleted without the owner's own word on that list.

## Rules
- **Privacy:** the repository is public. The report carries branch names, SHAs, dates and subjects only. If a commit subject or a branch name itself carries a home value, check with `~/.forager-private/scan.py` and leave it out of the report; say how many you left out.
- Never touch main, pre-main or records-after-173 in any way, and never force-push anything.
- No Gradle, no git stash. Scratch goes in /mnt/work/scratch/branch-sweep/, and it is deleted at the end. The bundle stays.
- Commits are none. If you need any, they end with `Co-Authored-By: Claude <noreply@anthropic.com>`, with no model name.
- Never read, name or quote what CLAUDE.md calls the Restricted Object.
- **Stops:** a count that doesn't add up; a bundle that doesn't verify or doesn't match; a branch that fits no class or two; anything that looks like live work not on the keep list.
