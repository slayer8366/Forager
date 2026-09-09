# Findings: the `Workers Builds: forager-pmtiles` check is red on every PR and green on main

**Date:** 2026-09-09. **Written by:** the bridge session that landed PRs #81 and #82, after the
check appeared red on both. **Status:** a findings note. Nothing was changed to make it pass, and
the fix is **not** in this repository — see "What cannot be done from here". **Not beta-blocking.**

## The observation

Both PRs opened that day showed `Workers Builds: forager-pmtiles` failing. PR #81 is five Markdown
files under `docs/`. It cannot break a Cloudflare Worker.

## What was verified

| commit / PR | `Workers Builds: forager-pmtiles` |
|---|---|
| `main` | **success**, and the check output carries a `Version ID` |
| PR #81 head (5 Markdown files) | failure |
| PR #82 head | failure |
| PR #79 head (`7819ef6`) | failure |
| PR #78 head (`bd5429e`) | failure |
| PR #77 head (`8dc10d4`) | failure |

Read from `gh api repos/slayer8366/Forager/commits/<sha>/check-runs`. Also verified: neither PR #81
nor PR #82 touches any file under `server/`, nor any file matching `worker`, `wrangler` or
`pmtiles`.

Two further details from the check-run payloads, which are the substance of this note:

- **The failing builds have `started_at` equal to `completed_at`** — zero elapsed time — and carry
  **no `Version ID`** in their output summary. The successful build on `main` carries one.
- The reporting app is `cloudflare-workers-and-pages`; `details_url` points into the Cloudflare
  dashboard under `.../forager-pmtiles/production/builds/<id>`.

## What that supports, and what it does not

**Supported:** the check's result is uncorrelated with the change under review. A five-file
documentation PR and a Kotlin service change fail it identically, on five PR heads across three
different dispatches, while `main` passes. A zero-duration failure that produces no version did not
*build and fail* — it failed before building. So this is not a broken Worker.

**Not established:** *why*. The build log lives in the Cloudflare dashboard and is not reachable
from the check-run payload, so no log was read. The shape is consistent with Workers Builds
deploying only from the production branch and rejecting non-production branch builds immediately,
but that is an inference from timing and the missing `Version ID`, **not** a proven cause. Anyone
picking this up should read one failing build's log in the dashboard first; that is the step this
note could not take.

## Why it is worth a row rather than being left alone

Owner's ruling, 2026-09-09: fix it or make it non-reporting, do not leave it. The reasoning is the
same as for the `JournalTabTest` flake — its companion note,
`2026-09-09-journaltabtest-flake-suite-only.md`. A signal that goes red without reference to the
change under review trains reviewers to skip the check list, at which point a real failure arrives
in a list nobody reads. Two unexplained red signals is a pattern, not two coincidences. Not
beta-blocking, and recorded here so it is not re-diagnosed from scratch.

## What cannot be done from here

**Explicitly unsupported in this session:** the fix. Workers Builds' branch and check-reporting
behaviour is configured in the Cloudflare dashboard for the `forager-pmtiles` Worker, not in
`server/pmtiles-worker/wrangler.toml` and not in `.github/workflows/ci.yml` — there is nothing in
this repository to edit that would change it. This session has no Cloudflare credentials, and the
dashboard flow is interactive. The three options, for whoever has that access:

1. Restrict Workers Builds to the production branch, so non-production pushes produce no build and
   no check.
2. Keep the builds but stop reporting checks to GitHub.
3. Configure non-production builds properly, if per-branch preview deploys are actually wanted.

## Separate finding, noted while looking

`server/pmtiles-worker/package.json` pins nothing: `pmtiles ^4.3.0`,
`@cloudflare/workers-types ^5.20260815.1`, `typescript ^5.6.3`, `wrangler ^4.59.1`. CLAUDE.md
requires exact versions rather than open ranges so a build is reproducible. A `package-lock.json`
is committed, which constrains an `npm ci` install but not an `npm install`. **This is not offered
as the cause of the failures above** — a zero-duration failure never reached dependency resolution.
It is a separate, real deviation from the repo's own rule, recorded so it is not lost.
