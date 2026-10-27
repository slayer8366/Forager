# Dispatch 2026-10-27-08: map patterns lost at the reload zoom, logging build on the S22 (a coder, Opus)

Written by Rowan Waymark, 2026-10-27. RECORD -1738. The owner: "Logging build on the S22 (Recommended)". The deadline matters: the owner's early weekly run waits on this fix and must start by Sat 2026-10-31 10:00 PST.

Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.

## What is known
- **The code report:** docs/audits/2026-10-27-night-glyph-zoom-report.md, branch night-glyph-zoom-report, ec698da06. It rules out H1-H3, infers that a pattern is missing from some tiles' image sets, and proposes candidates R (image removed as unused), S (tiles laid out before the image was added) and D (lower level). Its Part B is the logging build.
- **The S22 check:** docs/audits/2026-10-27-s22-check-v3-8d.md, branch s22-check-v3-8d, a5b7318f8. It is not night-only. Turning Night Maps off at full zoom loses the day tree glyphs and the day tufts too, and those come from the style's own sprite. Patterns go missing at the zoom the camera holds when the style reloads, and zooming out brings them back. Step 5 fits S. Hazel's inference: the cause is a style reload at the held zoom.

## Do
1. **The logging build.** On a scratch branch, glyph-logging (never merged), build a debug APK that only adds logging, as the report's Part B describes:
   - on each camera stop and each style load: the zoom; the forest and wetland layers' fill-pattern as read back; whether the sprite image and the night image exist;
   - MapLibre's own listeners: missing image, style image unused, and tile and render events, log only.
2. **Run it on the S22** (plugged in, over adb). Check the database schema before installing: never downgrade it (RECORD -1526). Run the report's Part A steps plus Hazel's added day-mode case. Keep logs and screenshots in /home/zynergy-labs/s22-glyph/, off GitHub.
3. **Name the cause** from the logs. Then propose the cheapest fix, with a test that fails before and passes after. If a JVM test can't fail on it, say so and give a device check that does.
4. **Stop.** Report to Rowan: the cause, the evidence and the fix. The fix itself is built on the owner's word, as its own PR.

## Rules
- **Network:** GitHub only on the laptop.
- **The Restricted Object:** never read, name or quote it.
- **Disk:** /mnt/work has about 7 GB free, so use a sparse worktree where you can.
- **Long conversation:** past about 200k tokens, push and write a handoff.
- **The phone:** restore any phone setting you change. Hazel set the screen timeout to 30 minutes; leave it unless the owner asks.
