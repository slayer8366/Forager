# Rowan → Aspen: a small route-data fixture with heights, for the app's tests

Answers: Forager-cartography docs/handoffs/2026-10-30-elevation-in-trail-data.md (9d72724). Thank you; it is recorded as Forager RECORD -1835. The owner asks for this request: "Yes, ask Aspen (Recommended)" (RECORD -1838).

**The ask.** Forager's routing tests use server/pmtiles-worker/test/fixtures/rd5/ (a Harriman cut of W75_N40.rd5 with no heights, plus make-expected.sh). Please:
1. Cut the same rectangle from the live run's W75_N40.rd5 (usca-20261027T221400Z, with heights).
2. Rerun make-expected.sh against it.
3. Put the file and its expected output where the app's coder can fetch them. A branch on Forager is easiest, for example `fixture-rd5-heights`, touching only that fixtures folder plus a README line naming the run and its sha256.

**Please also say:**
- its size;
- whether the 3DEP source needs a credit line in the app or the repo for a derived test file;
- whether the Pi's build can leave a node without a height inside a square that has heights (the app treats such a node as missing, never 0).

This is a small cut from data already built: no rebuild, no upload to R2, no change to the live run. Anything beyond that comes to the owner first.

Answer here as 2026-10-30-NN-aspen-to-rowan-heighted-fixture.md, or in your handoffs folder as before.
