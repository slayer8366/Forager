# BRouter v1.7.10, vendored

BRouter's routing engine, built into Forager as the trail router behind `domain/routing/TrailRouter` (dispatch 2026-10-23-02; engine chosen at RECORD -484).

- **Source:** https://github.com/abrensch/brouter, tag `v1.7.10`, commit `4d2639af77ea5ed9c30d3e400764eb6f9e8522da`.
- **What is here:** `src/main/java` of the five pure-Java modules the router needs, merged into one source tree: `brouter-util`, `brouter-codec`, `brouter-expressions`, `brouter-mapaccess` and `brouter-core`. Their packages do not overlap. Nothing else from the repository (no map creator, server, Android app or tests).
- **Unmodified.** To check, with `B` a clone of BRouter at that commit, from the repository root:

  ```
  for m in brouter-util brouter-codec brouter-expressions brouter-mapaccess brouter-core; do
    (cd "$B/$m/src/main/java" && find . -type f) | while read -r f; do
      cmp -s "$B/$m/src/main/java/$f" "third_party/brouter/src/main/java/$f" || echo "differs: $f"
    done
  done
  find third_party/brouter/src/main/java -type f | wc -l   # 101: 27 + 11 + 10 + 20 + 33
  ```

- **Why vendored:** BRouter publishes no Maven artifact, and JitPack's build of v1.7.10 fails (the engine spike, `docs/navigation/2026-10-03-engine-spike-report.md`). BRouter's own Gradle build is not included, because its root build pulls in a second Android Gradle plugin.
- **Licence:** MIT, `LICENSE` here, copied from the repository. Credited in the app's open-source page (`app/src/main/assets/credits/software.json`).
- **The profile the app routes with** (`hiking-mountain.brf`) and its `lookups.dat` are in `app/src/main/assets/routing/profiles2/`, pinned by git blob hash in `RoutingProfile.kt`, the same pins as the route cutter's proof (`server/pmtiles-worker/test/rd5/fetch-brouter.sh`).
- **Upgrading** means replacing this tree from a new tag, re-pinning the profile, and re-running the router's tests, which compare against BRouter's own command-line router on the same data.
