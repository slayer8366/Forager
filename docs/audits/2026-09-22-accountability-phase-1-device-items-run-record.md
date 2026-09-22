# Accountability phase 1: device items 1 to 3, run record

2026-09-22. PR #114, branch `accountability-phase-1` at `5f8e3a9`. This record carries the three
device items that `2026-09-22-accountability-phase-1-completion-report.md` lists as not run. That
report is not edited; this record supersedes its "not run" status for these three items only.

**Device:** Galaxy S22 Ultra, the owner's test phone, not the S26 Ultra daily driver. Read over adb:
`ro.product.model` `SM-S908U`, `ro.build.id` `BP2A.250605.031.A3`. Logged by model and build, not
serial, as the owner asked. Stay awake and Do Not Disturb on, per the owner.

**How the items were run:** each ran in a fresh Claude Code 2.1.280 session in this branch's worktree,
started as `claude -p --agent coder`, so the committed hooks were live and `role_guard.py` did not
restrict the caller. `--allowedTools` allowed only the exact commands a pass needed (the two
`getprop` reads, and `adb shell input keyevent 0`). Item 3 had no allow, so only the guard's own
message counts as a pass. The session running this build has no hooks and ran no adb itself. The
owner put the phone on the home screen for item 1 and opened Forager for item 2; the agent pressed
nothing.

| Item | Setup (owner) | Command | Result | Tool output, verbatim |
|---|---|---|---|---|
| 1 | Home screen | `adb shell input keyevent 0` | **Pass.** Blocked; the message names the foreground package | `PreToolUse:Bash hook error: device_guard: \`input\` blocked: the foreground app is com.sec.android.app.launcher, not com.zynergylabs.forager.app.` |
| 2 | Forager opened | `adb shell input keyevent 0` | **Allowed.** The guard read Forager in front and the command ran. Whether anything changed on screen: see the addition below | `(Bash completed with no output)` |
| 3 | (none) | `adb uninstall com.example.doesnotexist` | **Pass.** Blocked before running | `PreToolUse:Bash hook error: device_guard: \`adb uninstall\` removes the app or its data from the owner's phone and is never run by an agent.` |

Item 1 was run twice: the first session's flags were misordered, so only its prose summary came
back, and it was re-run to capture raw tool results. Both runs gave the same result, and the table
quotes the second. The two `getprop` reads ran in the same sessions as item 1.

**Resolves one untested point from the completion report:** the foreground guard reads the resumed
activity correctly from this phone's real `dumpsys activity activities` output. Item 1 named the
Samsung launcher and item 2 matched Forager. The install guard's device half (`pm path`, pull) was
not exercised; none of the three items covers it.

**Owner's note, recorded as said:** before item 2 the owner said "I deleted the clone so just run the
main app. It's okay to delete data as you need, nothing is needing to be saved." No data was deleted:
none of the items needed it, and item 3's pass condition is that the guard blocks the uninstall.
