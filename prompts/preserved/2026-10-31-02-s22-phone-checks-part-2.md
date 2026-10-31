# Dispatch 2026-10-31-02: the S22 phone checks, part 2 (a helper, Opus)

Written by Rowan Waymark, 2026-10-31. RECORD -1902. The owner: "Yes, fresh helper now (Recommended)".

Continue dispatch 2026-10-31-01 (read it, with its rules) from part 1's report, docs/audits/2026-10-31-s22-phone-checks.md on origin/s22-phone-checks (2de1cced5). Use part 1's scripts under /home/zynergy-labs/s22-2026-10-31/checks/scripts/; sample.sh does not clear logcat.

**Run, cheapest first:**
- **450-1 and 450-2:** a throwaway recording, with the app in front and the screen off for 3 minutes. Report the surface-not-valid lines per minute, the main-thread CPU, and whether the compass listener stays registered, against the 10-30 figures (about 940 a minute; 16% against 4.7%). Then the same with the app backgrounded (450-9).
- **450-6 and 450-7,** as far as adb can do them without a GPS fix. Say which need movement or a fix.
- **449-2, 449-5 and 449-8:** what adb can show.
- **Leftover partials from part 1:** 449-4 (measure the 48 dp fit margin), 449-6 (the pill and chip insets), 449-7(d), 449-10 (tap Discard itself), 449-13 in landscape.
- **F-3:** after a screen-off of more than 10 s, try to catch "—". For example, screenrecord from before the screen lights, or read the strip state from logcat if the app logs it. Report what you can tell.

**Still for the owner:** 450-5, 450-8, 449-12, 449-14, and F-2 by eye.

**Rules:** as in 2026-10-31-01.
- TEST data only, deleted after.
- Nothing cleared or reset; restore every setting.
- End recordings and navigation.
- Evidence goes in /home/zynergy-labs/s22-2026-10-31/checks/part2/, never git.

**Output:** a "Part 2" section in docs/audits/2026-10-31-s22-phone-checks.md on branch s22-phone-checks, pushed. Your final message: the verdict table, the figures, and the phone's end state.

## Rules
- No Gradle. gh is logged in.
- The Restricted Object: never read, name or quote it.
- Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.
