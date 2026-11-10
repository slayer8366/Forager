# Dispatch 2026-11-10-02: "weather forecast" on the Forager page, and the site README's beta database section brought up to date (Hazel, Opus)

Written by Rowan Waymark, 2026-11-10. RECORD -2199 (intent). Every fact below was read at zynergy-site main `4d397ac` while this was written, and is a premise to re-check.

## Authorisation
The owner, verbatim: **"Authorized"**. Asked what it covered, the owner chose **"Site wording and README"**, which was offered as: "Hazel makes a small site change: "forecast" becomes "weather forecast" on the Forager page, and the README's out-of-date binding line is fixed. Preview first, published on your word."

## Writing rule
Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.

## Change
1. **The Forager page, `Forager/index.html:193`.** In "What has been found nearby", "the recent rain, soil moisture and forecast there" becomes "the recent rain, soil moisture and weather forecast there". This is the planner's note from -2197, so the word can't be read as a mushroom forecast. Change every copy of that sentence on the site; the planner found only this one. Nothing else on the page changes.
2. **The site README's database section,** `README.md:84-116` at `4d397ac`, says three things that are no longer true. Correct them, keeping the history as history:
   - **":86-89", "Bound on both Production and Preview, and confirmed working at runtime".** No environment binds `BETA_DB` now:
     - Production's binding was removed on 2026-11-09 (Forager RECORD -2193, the owner's "Deleted and deployed").
     - Preview's was removed on 2026-11-10 (RECORD -2198).
   - **The database itself.** `zynergy-site-beta` was deleted. The owner, -2193: "Yes, deleted" and "Deleted and deployed". The deploy failure that RECORD -2193 records quotes Cloudflare, and the preview deploys of 2026-11-10 failed with "D1 binding 'BETA_DB' references database '9cb6b148-…' which was not found" (RECORD -2197).
   - **":114", "The binding above is done. The secrets are not, deliberately".** The project's settings, read on 2026-11-10 (RECORD -2198), have `EXPORT_TOKEN` and `RESEND_API_KEY` set as secrets on both environments. `/api/beta-export` answers 503 because no database is bound, not for want of the token (`functions/api/beta-export.js:23-29`).

   Fix any other README line your reading shows is now false for the same reasons, and list each one. Don't rewrite sections that are still true.

## Scope boundary
- **Repo and branch:** `slayer8366/zynergy-site`, a new branch `claude/forager-page-wording` from main `4d397ac`, in your clone at `~/.cache/forager-wt/forager-page`, refreshed from the remote.
- **Files:** `Forager/index.html` (that sentence only) and `README.md` (the database and secrets lines only).
- **Not:** any other file; the site's main (open a PR, don't merge); Cloudflare's dashboard or API; the Forager repo; RECORD.md.

## Verify first
- That `4d397ac` is still main's head, and that the sentence and the README lines read as quoted above.
- That the sentence isn't in the page's JSON-LD or in `llms.txt`. The planner's grep found it only at `Forager/index.html:193`.

## Predictions
- **Outcome (planner):**
  - The diff is one changed line in `Forager/index.html` and a handful of README lines.
  - Your FAQ checker still prints "FAQ VERDICT: MATCH, word for word".
  - The HTML still parses in strict mode.
  - **The Cloudflare preview deploys successfully.** Previews work again since -2198. Their addresses send visitors to the Cloudflare Access sign-in, so curl can't read the page there. Read the deploy's result from the PR's Cloudflare Pages check instead, and give the preview address for the owner, who can sign in.
- **Mechanism (coder):** in your report.

## Evidence required
- The diff.
- The checker's output.
- The HTML parse result.
- The Cloudflare Pages check's conclusion on your head commit, with the preview address.
- The privacy scan of the diff (`git diff origin/main | python3 ~/.forager-private/scan.py stdin-diff`), first shown catching a planted value. It must read 0.

## Stops
Stop and report on:
- a README statement whose truth you can't settle from the record or the code;
- anything outside the two files;
- a failed preview deploy;
- a wrong premise.

## Finish line
A PR into zynergy-site main, open and unmerged, with its preview address. It goes live only on the owner's word to publish; the planner merges then.

## Final message
- Branch, commit and PR.
- The evidence above.
- The README lines changed, before and after.
- Every call beyond this dispatch.
