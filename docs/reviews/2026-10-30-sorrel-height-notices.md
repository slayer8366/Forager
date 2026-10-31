# Review 2026-10-30: pi-height-notices, the route-file height notices and the committed upload script

Sorrel Tally, the independent reviewer (Fable), for dispatch `prompts/preserved/2026-10-30-sorrel-height-notices.md`
in Forager-cartography. Started about 23:20 PDT on 2026-10-30, finished about 00:15 PDT on 2026-10-31. Times are
Pacific. Each claim is marked **read**, **computed** or **inferred**. Nothing was uploaded and no rclone call was
made; the only writes were to `/tmp`.

## Prediction (formed on reading the diff, before the staging run and the test reverts)

1. The notice is Juniper's rows verbatim, with the licence URL carried inline on row 1.
2. `routing-manifest` on the 261026 output reproduces the hand-staged manifest entry for entry, plus two keys.
3. The upload script's order and refusals are right; the tests will cover a failed squares read-back but probably not
   a failed NOTICE read-back.
4. A failure in `stage_routing_set` leaves nothing half-published, because the `mv` into the output directory and the
   `current` link come after it.

## Baseline (read)

| Item | Dispatch | Found |
|---|---|---|
| `pi-height-notices` | `3d5e0d90e` | `3d5e0d90e730975f8bd09d9ca629475cbb22b4ac`; two commits (`487dc5182` tests first, `3d5e0d90e`) on `a5bcb0780` |
| `pi-build`, installed | `a5bcb0780` | `origin/pi-build` = `a5bcb0780` (the pi-35 merge); `/opt/forager-build/map-build/COMMIT` = `a5bcb0780`; `pi-build` is an ancestor of the branch, so the merge is a fast-forward in content |
| Juniper's report | `355933b4d` | `docs/research/2026-10-30-nrcan-attribution.md` on `juniper-nrcan-credit`, rows 1 to 3 under "Proposal for the owner" |
| Hand-staged manifest | | `/mnt/archive/aspen-upload-261026/manifest.json`, run `usca-20261027T221400Z`, 187 files and 187 names |

No `build-usca` process; `forager-build-usca.service` inactive. The delta is eight files: the notice, `run_record.py`
(+61), `build-usca.sh` (+10), the new `upload-routing.sh` (98 lines), its new test (97), two extended tests, the README.

**The suites, run in my worktree at `3d5e0d90e` (computed):** `test_run_record` 16 OK, `test_upload_routing` 17
checks, `test_build_usca_functions` 87, `test_build_usca_disk` 95, `test_build_usca_stop` 27, `test_build_usca_run` 70,
`test_build_functions` 14, `test_mvt_decode`, `test_merge_poly` and `test_fnam_build` OK. Willow's counts match.

## 1. The text (read and computed)

`map-build/notices/routing-NOTICE.txt` has four lines, UTF-8, LF, no BOM, a trailing newline. Compared character for
character with Juniper's rows:

- line 1 = row 1's text, a space, row 1's link URL (`Licence – Canada` with U+2013, then
  `https://open.canada.ca/en/open-government-licence-canada`);
- line 2 = row 2's text (`2010-2014`, `2014-2018`, `©` twice, the trailing full stop);
- line 3 = row 2's detail, the liability sentence, with "programme";
- line 4 = row 3's text, which is also the app's `usgs-3dep-heights` row on `pre-main` (`DataSourceCredits.kt:96`),
  word for word, as are the other two rows (lines 79 and 86-91).

The only non-ASCII characters are `©` and `–`. `test_run_record` pins the four lines; changing the en dash to a hyphen
fails two tests (my revert A). **The inline URL:** the licence says "where possible, provide a link to this licence";
in a plain-text NOTICE a URL on the attribution line is the only form a link can take, and it is the licence's own
page. A fair reading (inferred). The 6(b) notice and the 6(c) sentence are present verbatim from the Copernicus
licence as Juniper quoted it.

## 2. The R2 manifest (computed)

I ran `run_record.py routing-manifest` from the branch on the published 261026 output, through a scratch directory
whose `segments` and `names1` are symlinks into `/srv/forager-build/out-usca/current` (nothing was written there;
confirmed by listing it afterwards), with the run id, timestamp and sequence from the hand-staged manifest. 10 s for
1.7 GB of squares and 577 MB of names. Against `/mnt/archive/aspen-upload-261026/manifest.json`:

- `run_id`, `osm_timestamp`, `osm_sequence` equal;
- `files`: 187 and 187, the same paths, the same size and sha256 for every one, the same order, the same keys per
  entry;
- `names`: likewise, 187 and 187;
- keys: the hand manifest's five, plus `notice` (`usca-20261027T221400Z/NOTICE`) and `notices` (the four lines).
  Nothing removed, nothing renamed.

**Against the Worker's parser (read, `server/pmtiles-worker/src/routing/manifest.ts` on `pre-main`; the Worker is
not on `pi-build`):** `parseManifest` reads `run_id` (the same `RUN_ID` regex `run_record.py` now enforces),
`osm_timestamp`, and `files`, each `path` under `<run_id>/segments4/` matching the square-file regex, `size` a positive
safe integer. It reads nothing else, so `names`, `notice` and `notices` are ignored, as `names` already was. The staged
file satisfies every check it makes.

## 3. The upload script (read, and the tests computed)

- **Refusal before any upload.** Step 1 is one Python pass that exits non-zero on: a malformed `run_id`; any square or
  names file on disk not in the manifest, or differing in size or sha256; any listed file not on disk; no squares at
  all; NOTICE's lines not equal to `notices`; `notice` not `<run_id>/NOTICE`. Its failure reaches `die` before the
  first `rclone` call, and the two `[[ -f ]]` guards run before it. U3 and U4 show no `copy` in the stub's call log.
- **The manifest last, never after a failed read-back.** Steps 2 and 3 upload and read back the squares, the names
  and NOTICE, and count objects; each check is `|| die`, so a failed read-back exits before step 4. Step 4 is the only
  `copyto` of `routing/manifest.json` and nothing follows it but its own read-back. U1 pins the order and that no
  `copy` follows the manifest; U2 pins that a failed squares read-back leaves no manifest upload. **My revert C**
  (the manifest `copyto` moved before the read-backs) fails U2 as it should.
- **`--dry-run`.** Before the dry-run exit the script only reads: the two `-f` tests, the Python check (reads and
  hashes), two `ls`. No `rclone` of any kind; U5 shows an empty call log. Side-effect free (read).
- **A gap (computed, my revert B):** with the NOTICE read-back's `|| die` changed to a log line, all 17 checks still
  pass. The stub's failure switch only affects `rclone check`, so neither a NOTICE read-back mismatch nor an object-count
  mismatch is exercised; a names read-back failure is also untested (the switch is keyed on the folder name but no case
  sets it to `names1`). Should-fix 1.
- A note: the script does not compare the staged `routing-manifest.json` with the output's own `manifest.json`
  (run id, timestamp), so a stale staged file beside a newer output would not be caught by the script itself; the
  build stages both in the same publish step, which is why it does not matter today.

## 4. `stage_routing_set` at publish (read)

It is called after `manifest.json` is written and **before** `mv "$run/out" "$OUT/$name"` and the `current` link swap,
with `|| die`. On failure: `die` exits 1, `on_exit` writes the status as failed with the stage, `keep_failed_run`
moves `$run/out` to `failed-usca/latest/out`, `cleanup_scratch` removes the work directory, and `current` is never
touched because the `mv` never ran. The run is cleanly failed and not half-published. The function itself writes only
inside `$run/out` (`NOTICE` by copy, `routing-manifest.json` atomically), and `run_record.py` refuses before writing
anything when the notice file is missing, the run id malformed, the sequence malformed, or there are no squares (the
test "a missing notice file is refused and nothing is staged" covers the first). The test suite's check that the call
precedes the `mv`, by line number, holds.

## 5. The Worker and the app (read)

The Worker: section 2; additive keys only, ignored by construction. The app: no file under `app/src/main` on
`pre-main` reads `routing/manifest.json`, `segments4` or `names1`; the app reaches route data through the Worker, so a
manifest field cannot reach it. (The dispatch to Willow asked to confirm the app's manifest parser; there is none to
confirm, which is the stronger answer.)

## 6. Revert checks (computed)

- **Willow's:** the manifest moved first; U1 and U2 fail (read from the dispatch; my revert C is the same move and
  fails U2).
- **Mine A:** `–` → `-` in the notice file: `test_run_record` fails 2 of 16 (the verbatim pin and the staged
  `notices`); the usca functions suite stays green because it compares the copy to the file, not to the words.
- **Mine B:** the NOTICE read-back silenced: no test fails (should-fix 1).
- **Mine C:** the manifest before the read-backs: U2 fails.

## Verdict

### Blocking

None.

### Should-fix

1. **Test the other read-backs.** Give the stub a way to fail `rclone cat` (NOTICE) and `rclone lsf` (the counts), and
   a case with `STUB_FAIL_CHECK=names1`, each asserting the manifest was not uploaded. Today a regression in any of
   those three `|| die` lines would pass the suite.

### Notes

- The script could cross-check `routing-manifest.json` against the output's `manifest.json` (same `run_id`,
  `osm_timestamp`) as a belt to the build's braces. Not needed while both are staged together.
- `run_record.py`'s docstring points at `manifest.ts` "on pre-main"; the Worker is indeed not on `pi-build`, so a
  reader of `pi-build` alone cannot find it. A sentence saying so would help.
- The hand-staged manifest already carried `names`, which the Worker ignores; the branch keeps that and adds two more
  ignored keys. If the Worker ever starts reading `names`, the staged shape is the one it will meet.

### Prediction, scored

1 held. 2 held, entry for entry. 3 held, including the untested NOTICE read-back. 4 held.

**Is pi-height-notices mergeable for install after Sunday's run? Yes.** The notice is the ruled text to the byte, the
staged manifest is the hand-staged one plus two ignored keys, the upload script refuses before any upload and sends the
manifest last and only after every read-back, and a staging failure fails the run cleanly before anything is
published. Should-fix 1 is a test, not a behaviour. The install after Sunday's run is the owner's go.
