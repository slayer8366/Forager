# CLAUDE.md

Standing engineering principles for this repo. Adapted from [Evidence Gated
Development](https://github.com/slayer8366/E-GD-Philosophy), keeping only
the principles that generalize to a small Android app — its hardware-driver
layer, content-addressed data hashing, and multi-agent record-store
ceremony are built for a different kind of project and are not adopted
here.

## Working with ambiguity

- An ambiguous requirement or an unmade architectural decision is a
  stop-and-ask, not a judgment call. Surface the options; don't pick one
  silently and build on it.
- A claim about this codebase names a file and location, or is stated as
  unverified. Don't describe code that wasn't actually read.
- Report what was done, what was verified and how, and what was skipped or
  left unverified. A report that only reassures has failed at its job.

## Bug fixing

- See the failure before writing the fix — reproduce it, confirm it fails
  for the reason expected. A failure that doesn't match the prediction
  means the test/check itself is wrong; fix that first.
- Two failed fix attempts on the same symptom means stop guessing. Next
  step is more data — logs, a minimal repro, instrumentation — not a third
  hypothesis.

## Errors and failure paths

- No silently swallowed exceptions, no default fallback that isn't logged
  when it fires.
- Partial or failed results are reported as such, never presented as
  success.
- An unsupported feature or capability returns an explicit "unsupported,"
  never a fabricated plausible value.

## Architecture

- Keep domain logic (species matching, list management, sighting state,
  etc.) free of Android UI framework bindings, so it's unit-testable
  headless without an Activity/Compose tree in the loop.
- Wrap external integrations — the iNaturalist API, camera, location —
  behind an interface this project owns. Domain logic depends on the
  interface, not the vendor SDK or HTTP client directly.
- A device- or API-reported capability range (camera resolution, GPS
  accuracy, rate limits) describes what's possible, not what's safe to
  use. Apply an explicit operating limit rather than trusting the reported
  range as-is.

## Testing

- Assert on actual output — payloads, types, schema — not proxies like "a
  file got created" or "the string appears somewhere in the output."
- Exercise user-triggered behavior through its real entry point (the
  ViewModel/Composable callback, the actual Intent), not by hand-calling
  an inner method with made-up arguments.
- A check that passes identically before and after a code change is
  suspect — flag it as possibly not covering what it claims to.

## Building

- New capability is a new function, class, or path — not a conditional
  threaded into existing working code.
- Don't build speculative correction or optimization logic without real
  data showing the case it's meant to handle.
- Pin dependency versions (Gradle version catalog, exact versions) rather
  than open ranges, so a build is reproducible.

## Documentation

- Record why a non-obvious decision was made and what alternative was
  rejected — not just what was chosen.
