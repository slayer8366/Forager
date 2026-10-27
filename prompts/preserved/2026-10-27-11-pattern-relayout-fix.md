# Dispatch 2026-10-27-11: map patterns redraw after a style reload, a workaround (a coder, Opus)

Written by Rowan Waymark, 2026-10-27. RECORD -1742. The owner: "Workaround now + report upstream (Recommended)". The deadline matters: the owner's early weekly run waits on this being verified on the S22, and must start by Sat 2026-10-31 10:00 PST.

Describe products generically. Commits end with `Co-Authored-By: Claude <noreply@anthropic.com>` and no model name.

## The cause (from the S22 session)
Read docs/audits/2026-10-27-glyph-cause.md on branch glyph-logging, 4d9a2eb64. When the style reloads (a Night Maps switch), MapLibre Native 13.5.0 keeps the on-screen tiles at the held zoom and lays them out again. Those kept tiles have the features, the image and the right pattern name, yet draw no fill-pattern, day or night. Any fresh layout draws it. The logging build's probe brought the glyphs back every time, by day and by night: it set each pattern layer to "" and restored it 600 ms later.

## Build (that report's F1)
- **The fix.** After each style reload, once the style callback has run and the sprite has loaded, read every fill layer in the style that has a fill-pattern. Set each to "", then restore its value after the map has rendered.
  - If the style changed in between, restore nothing.
  - Find the shortest gap that works on the S22, starting from the probe's 600 ms. A single frame is better, if it works. State the gap you use, and what you measured.
- **Where it lives.** A new class (CLAUDE.md: new capability is a new path), called from the existing reload path (MapStyleReload.kt and the night image step). Keep it free of UI framework types where it costs nothing. Log one line per reload and per restore.
- **Code comment.** Say that this works around a MapLibre 13.5.0 fault, citing the report, and that it can go once upstream fixes it.
- **The glyph-logging branch** is not merged and nothing comes from it except the idea. Don't copy its logging.

## Tests
- **JVM test:** a fake style, as in NightImageSwitchTest, driven through the reload's sprite-loaded hook.
  - Each pattern layer gets "" and then its original value.
  - Layers without a pattern are untouched.
  - Nothing is restored if the style changed in between.
  - It fails first, then gets a revert check (restored from a saved copy, the build log checked).
- **Device check on the S22,** the real gate. The S22 is plugged in. Check user_version before installing; never downgrade the schema. The report's colour-count steps:
  1. Night on at the maximum zoom: glyph colour above 1%.
  2. Night off at the maximum zoom: day glyph colour above 1%.
  3. Both sides of a tile edge show glyphs.
  4. Zoom out and back in at that edge: glyphs stay on both sides.
  5. Tufts at the maximum zoom, if a wetland can be found.

  On build 4094, steps 1 to 3 read 0. Keep logs and screenshots in /home/zynergy-labs/s22-glyph-fix/, off GitHub. Put the PR build on the phone; leave it there, and say so.
- CI only. A new branch off current pre-main, a PR into pre-main, no merge.

## Also: the upstream bug report (text only)
Write docs/audits/2026-10-27-maplibre-pattern-relayout-upstream-draft.md: a short bug report for maplibre-native, with a minimal repro (a style with a fill-pattern layer; setStyle again at the held zoom; patterns gone on the kept tiles; a fresh tile draws them), the version, and the evidence. Post nothing anywhere; the owner files it.

## Rules
- **Network:** GitHub only on the laptop.
- **The Restricted Object:** never read, name or quote it.
- **Disk:** use a sparse worktree where you can.
- **Long conversation:** past about 200k tokens, push and write a handoff.
- **Other coders' files:** the find-photos and fallback-retry PRs (#427 touches MapListeners.kt's comments and NoSignalTrailMap.kt). If you must touch the same lines, stop and report.

## Report
- the four disclosure sections;
- the PR number and CI runs;
- the device check's figures;
- the upstream draft's path.
