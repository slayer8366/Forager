---
name: failing-test-first
description: How to build the failing case before the fix and prove it fails for the right reason, including how to construct positive controls for checks, scripts, and guards whose green state cannot distinguish itself from broken. Use this before fixing any bug, before trusting any validation script or CI check, and whenever you are about to report that something works. Use it especially when a test or check has only ever been seen passing, because a green that has never been shown capable of turning red is not evidence.
---

# Failing test first

Write the test that fails. Watch it fail. Read the message. Then fix the thing.

The order matters because a test written after the fix has never been observed doing its job. It passes, and you cannot tell whether that is because the behaviour is correct or because the test is inert. Half the tests that give false confidence would have been caught by seeing them red once.

## The failure must be the right failure

A test failing is not enough; it has to fail for the reason you intend.

- A compile error means the test never ran.
- A null pointer from unrelated setup means the harness is broken, not that the behaviour is missing.
- A timeout might mean anything.

Read the message and say why it is the right one. "The dialog never opened the session because it could not, opening living inside the slot it withheld" is a report of the right failure. "It failed" is not.

## Positive controls for checks that only ever pass

Some things are not tests: validation scripts, CI gates, comparison tools, guards. They report green in normal operation, and their green state looks identical whether they work or not.

For these, construct the case they exist to catch and confirm they catch it. This is more important than for ordinary tests, because nothing else will ever exercise them.

A worked example. A script compares a test run's failures against a recorded baseline of known-bad ones, reporting NEW and ABSENT. On a host with no failures it prints the same thing whether or not it works. Fed a synthetic run containing exactly one baseline failure — the case it exists to recognise — it produced two contradictory wrong answers at once: it cried regression on a known-baseline failure while simultaneously asserting that same test had not failed. The bug was locale collation between `sort` and `comm`. It would never have shown up on a green host.

Build three controls, not one, because a broken check that unconditionally reports success passes the first two:

1. **The case it exists to catch** — does it catch it?
2. **That case plus a genuine problem** — does it still distinguish them?
3. **The normal green case** — is it unchanged?

## When a test passes on the first run

Stop and ask why. There are good reasons: the behaviour was already correct and you are adding coverage; the fix landed in an earlier commit. There are also bad ones, and they look the same from here.

The way to tell is the revert check. If taking the code away leaves the test passing, the test is not connected to the code it names.

A test that has never failed is a hypothesis, not evidence.

## Fixtures must contain the case

A test can be perfectly written and still prove nothing because its input lacks the thing under test. Trailer handling tested on files with no trailers. Multi-chunk parsing tested on single-chunk data. Rotation tested at an angle where the expression cancels.

When you write a fixture, ask what property of it makes the test capable of failing, and say so in the report. If no property does, the fixture is wrong, and fixing the fixture comes before fixing the code.

Correct the fixture first and separately, so the sequence in the record is: fixture fixed, test now fails for the right reason, code fixed, test passes.
