---
name: revert-check
description: How to prove a test actually holds the claim it appears to hold, by reverting the fix against committed state and confirming the test fails with a message specific to that edit. Use this after writing or modifying any test that is meant to guard a behaviour, before reporting a fix as done, and whenever you want to know whether a passing suite is evidence of anything. Use it especially when a test passes on the first run, since a test that has never failed has never been shown to be connected to the code it names.
---

# Revert check

A passing test proves nothing on its own. It might be testing the behaviour you think, or it might pass for any of a dozen unrelated reasons: an assertion that is true by coincidence, a value that matches the default, a mock that never gets called, a code path that is never reached.

The revert check is the cheap way to find out. Take the fix away, and see whether the test notices.

## The protocol

1. **Commit first.** The whole check runs against committed state.
2. **Revert exactly one edit.** Not the whole change: the single edit whose claim you are testing. One revert per claim.
3. **Run the test.** It must fail.
4. **Read the failure message.** It must be specific to the reverted edit, not a compile error, not a null pointer from something unrelated, not a timeout.
5. **Restore** and confirm the test passes again.

## Never revert against a dirty tree

`git checkout -- <file>` on a tree with uncommitted work discards that work with no undo. This is the single most expensive mistake available in this protocol, and it is easy to make when the revert feels like a small experiment.

Commit before reverting. If for some reason you cannot, save copies of the files first and restore from those, and say in the report which method you used.

A revert runner that refuses a target file with uncommitted changes is worth building. Note in the report whether the refusing path was actually exercised, because a guard that has never fired is a guard you have not tested.

## The failure must bite for the right reason

Check the build log, not just the test result. A compile error is not a test failure: it means the revert broke the build, and the test never ran at all. That tells you nothing about whether the test holds its claim.

When a revert produces a compile error, the edit you reverted was load-bearing for compilation, which is a different fact from the one you were trying to establish. Either revert a smaller piece, or say plainly that this claim cannot be revert-checked and why.

## Assertions that cannot fail

This is what the check is really for. Three patterns that have shown up repeatedly:

- **The coincidentally-correct value.** An assertion that "nothing rotates" using a device reading equal to the display rotation, where the expression `sensor - display` cancels to zero either way. It passes with the modifier and without it. The fix is a reading a quarter turn away, so the two cases differ.
- **The wrong accessor.** Measuring a rotated element with a bounds accessor that maps only the origin, so a turned node reads as a translated one. The assertion is about the wrong property entirely.
- **The fixture that lacks the case.** A trailer-stripping test whose fixtures all end exactly at the marker, so the code path that handles trailers is never exercised. The test passes before and after the fix, and proves nothing either way.

In each of these the revert check is what exposed the problem, because the test kept passing when the code was gone.

## Reporting

For each revert, name the edit, the test that failed, and the message. "Four reverts, each failing on its own test" is a summary; the reader wants to see that revert three produced the message about the sweep count and not a generic assertion error.

When a revert exposes a weak test, say so and say what you changed. That finding is usually more valuable than the fix it was guarding.
