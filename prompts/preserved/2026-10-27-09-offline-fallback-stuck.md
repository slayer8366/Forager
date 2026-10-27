# Dispatch 2026-10-27-09: Trail Map stays on the OpenStreetMap fallback after coming back online, report first (a coder, Opus; report only)

Written by Rowan Waymark, 2026-10-27. RECORD -1738. The owner: "Report, then fix (Recommended)". Build nothing.

**What was seen** (the S22 check, docs/audits/2026-10-27-s22-check-v3-8d.md on branch s22-check-v3-8d, a5b7318f8, card step 1):
1. Clear storage while offline > Trail Map can't load its style > the app draws the OpenStreetMap fallback.
2. Back online, the map stays on the fallback until the app is restarted.

**Report:**
- the fallback path with file:line (OwnStreetFallback.kt and its callers): when it fires, whether it is logged, and why nothing retries Trail Map once the connection returns;
- whether the same holds outside cleared storage, for example on a first install offline, or after the style cache expires while offline;
- the cheapest fix, which the owner's "Every failure has a fallback" and "Don't make me think" suggest is a retry when the connection returns, priced as a tap > shows > Back step path, with a test that fails before and passes after.

Write it to docs/audits/2026-10-27-offline-fallback-stuck-report.md on a new branch, offline-fallback-stuck-report, and push it. Include the four disclosure sections. No device. Network: GitHub only. Never read, name or quote the Restricted Object. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>`, with no model name.

## Amendment 1 (2026-10-27): build the retry (RECORD -1739)
Read the report: docs/audits/2026-10-27-offline-fallback-stuck-report.md, branch offline-fallback-stuck-report, 2bb9f6954. The stuck state follows a planner ruling recorded at OwnStreetFallback.kt:21-22 ("tried again at the next cold start"), not an owner ruling. The owner, asked to change it: "Yes, retry after 30 s (Recommended)".

Build the report's fix:
- `OwnStreetFallback.onSignalReturned()` clears the flag and logs one line.
- `noSignalState` calls it when the no-signal state ends (the existing 30 s held-connection rule).
- It applies to any fallback, the report's choice (b) as recommended.
- If Trail Map still can't load, the map quietly falls back again and logs it.
- Update OwnStreetFallbackTest's expected log line to match its new wording.

**Tests:** the virtual-time test (still on OpenStreetMap at 29 s, Trail Map at 30 s, one log line), failing first; the Compose test that every reader follows; the server-outage guard. Each gets a revert check: restored from a saved copy, the build log checked for compile errors.

A new branch off current pre-main, a PR into pre-main, CI only, no merge. Then report with the four disclosure sections and S22 steps as tap > shows > Back. Keep clear of the buildings, find-photos and glyph-logging coders' files.
