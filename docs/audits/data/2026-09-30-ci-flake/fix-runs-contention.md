# Fix-run contention notes (-296)

Times are the JUnit suite `timestamp` (UTC) and the class time from the XML.

- `fix-2cpu` 1-12 (20:51:27 to 21:03:33 UTC) and `fix-1cpu` 1-3 (21:04:20 to 21:07:58 UTC): no other
  coder's Gradle reported in these windows. They are uncontended as far as known.
- `fix-1cpu` 4-6: lost to a container restart before they started. There is no `iter4.log`, so they
  were never run, not run and discarded.
- `fix-1cpu-b` 1 (21:16:08, 60.1 s): **contended.** -310 (topo-night) ran at about 14:16 PDT = 21:16 UTC,
  as the planner reported. The class took 60 s against about 40 s for uncontended 1-CPU runs.
- `fix-1cpu-b` 2 (21:18:06, 50.0 s): **contended.** A -310 Gradle Test Executor started during it,
  seen with `pgrep` at 21:18:12 UTC.
- `fix-1cpu-b` 3 (21:20:59, 46.8 s): overlap unknown. No other worker was checked during it.

All 18 fix runs: 52 tests, 0 failures, 0 compile errors. Extra load widens the race window, so a
contended clean run is not weaker evidence for the fix. It is still not counted as an
uncontended run.
