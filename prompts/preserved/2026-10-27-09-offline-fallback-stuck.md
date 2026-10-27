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
