#!/usr/bin/env python3
"""Tests for run_record.py's status rules and manifest, run through its real command line.

Run: python3 -m unittest map-build/tools/test_run_record.py
"""
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import unittest

TOOL = os.path.join(os.path.dirname(os.path.abspath(__file__)), "run_record.py")


def run(*args):
    return subprocess.run([sys.executable, TOOL, *args], capture_output=True, text=True)


class StatusTest(unittest.TestCase):

    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.path = os.path.join(self.dir.name, "status.json")

    def tearDown(self):
        self.dir.cleanup()

    def status(self, *args):
        r = run("status", self.path, *args)
        self.assertEqual(r.returncode, 0, r.stderr)
        with open(self.path) as f:
            return json.load(f)

    def publish(self, run_id="r1", warnings=None):
        args = ["--state", "ok_with_warnings" if warnings else "ok", "--stage", "done", "--run-id", run_id,
                "--osm-timestamp", "T1", "--output", f"/out/{run_id}"]
        if warnings:
            args += ["--warnings", warnings]
        return self.status(*args)

    def test_fetch_only_is_not_a_success(self):
        s = self.status("--state", "ok", "--stage", "fetch-only", "--run-id", "f1", "--osm-timestamp", "T1")
        self.assertIsNone(s["last_success"])

    def test_a_published_build_is_the_last_success(self):
        s = self.publish("r1")
        self.assertEqual(s["last_success"]["run_id"], "r1")
        self.assertEqual(s["last_success"]["output"], "/out/r1")

    def test_ok_at_done_without_an_output_is_not_a_success(self):
        s = self.status("--state", "ok", "--stage", "done", "--run-id", "r1")
        self.assertIsNone(s["last_success"])

    def test_failure_and_skip_keep_the_last_success(self):
        self.publish("r1")
        s = self.status("--state", "failed", "--stage", "validate", "--run-id", "r2")
        self.assertEqual(s["last_success"]["run_id"], "r1")
        s = self.status("--state", "skipped", "--stage", "state", "--run-id", "r3")
        self.assertEqual(s["last_success"]["run_id"], "r1")
        s = self.status("--state", "ok", "--stage", "fetch-only", "--run-id", "r4")
        self.assertEqual(s["last_success"]["run_id"], "r1")

    def test_an_old_last_success_without_an_output_is_dropped(self):
        # What the earlier version wrote for the fetch-only run on 2026-10-04.
        with open(self.path, "w") as f:
            json.dump({"state": "failed", "last_success": {"run_id": "20261004T143620Z", "output": None,
                                                           "osm_timestamp": "T1", "warnings": []}}, f)
        s = self.status("--state", "running", "--stage", "state", "--run-id", "r5")
        self.assertIsNone(s["last_success"])

    def test_ok_with_warnings_counts_and_records_them(self):
        s = self.publish("r1", warnings="remove-old-outputs,remove-scratch")
        self.assertEqual(s["state"], "ok_with_warnings")
        self.assertEqual(s["warnings"], ["remove-old-outputs", "remove-scratch"])
        self.assertEqual(s["last_success"]["warnings"], ["remove-old-outputs", "remove-scratch"])

    def test_ok_with_warnings_needs_the_step_names(self):
        r = run("status", self.path, "--state", "ok_with_warnings", "--stage", "done", "--run-id", "r1",
                "--output", "/out/r1")
        self.assertEqual(r.returncode, 2)
        self.assertFalse(os.path.exists(self.path))


class ManifestDroppedTest(unittest.TestCase):

    def test_dropped_squares_are_listed_with_sizes(self):
        with tempfile.TemporaryDirectory() as d:
            for name, body in (("in.pbf", b"abc"), ("src.zip", b"x"), ("out.pmtiles", b"y"), ("pins.env", b"K=v\n")):
                with open(os.path.join(d, name), "wb") as f:
                    f.write(body)
            with open(os.path.join(d, "dropped.tsv"), "w") as f:
                f.write("W130_N45.rd5\t12345\nW150_N60.rd5\t678\n")
            common = ["--pins", os.path.join(d, "pins.env"), "--run-dir", d, "--osm-timestamp", "T1",
                      "--osm-sequence", "1", "--inputs", os.path.join(d, "in.pbf"),
                      "--sources", os.path.join(d, "src.zip"), "--outputs", os.path.join(d, "out.pmtiles")]
            r = run("manifest", os.path.join(d, "m1.json"), *common, "--dropped", os.path.join(d, "dropped.tsv"))
            self.assertEqual(r.returncode, 0, r.stderr)
            with open(os.path.join(d, "m1.json")) as f:
                self.assertEqual(json.load(f)["dropped_rd5"],
                                 [{"name": "W130_N45.rd5", "size": 12345}, {"name": "W150_N60.rd5", "size": 678}])
            r = run("manifest", os.path.join(d, "m2.json"), *common)
            self.assertEqual(r.returncode, 0, r.stderr)
            with open(os.path.join(d, "m2.json")) as f:
                self.assertEqual(json.load(f)["dropped_rd5"], [])


class ManifestSequenceTest(unittest.TestCase):
    """build-usca.sh passes every region's sequence (run usca-20261019T185700Z failed at publish,
    int('us:2415,canada:4945')); build-orwa.sh passes one number."""

    def manifest(self, seq):
        with tempfile.TemporaryDirectory() as d:
            for name, body in (("in.pbf", b"abc"), ("src.zip", b"x"), ("out.pmtiles", b"y"), ("pins.env", b"K=v\n")):
                with open(os.path.join(d, name), "wb") as f:
                    f.write(body)
            out = os.path.join(d, "m.json")
            r = run("manifest", out, "--pins", os.path.join(d, "pins.env"), "--run-dir", d, "--osm-timestamp", "T1",
                    "--osm-sequence", seq, "--inputs", os.path.join(d, "in.pbf"),
                    "--sources", os.path.join(d, "src.zip"), "--outputs", os.path.join(d, "out.pmtiles"))
            if r.returncode != 0:
                return r, None
            with open(out) as f:
                return r, json.load(f)["osm"]["replication_sequence"]

    def test_one_region_is_a_number(self):
        r, seq = self.manifest("4523")
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertEqual(seq, 4523)

    def test_several_regions_are_kept_as_written(self):
        r, seq = self.manifest("us:2415,canada:4945")
        self.assertEqual(r.returncode, 0, r.stderr)
        self.assertEqual(seq, "us:2415,canada:4945")

    def test_anything_else_is_refused(self):
        for bad in ("", "us:", "us:24x5", ":2415", "us:2415,", "us=2415", "2415.0"):
            r, seq = self.manifest(bad)
            self.assertEqual(r.returncode, 2, bad)
            self.assertIn("--osm-sequence", r.stderr)



# The route-file notice (dispatch 2026-10-30-coder-04): Juniper's wording, verbatim, as in the app's Heights credits
# (Forager juniper-nrcan-credit 355933b4d, docs/research/2026-10-30-nrcan-attribution.md, rows 1 to 3). Pinned here so
# an edit to the one notice file has to be an edit to this test too.
NOTICE_LINES = [
    "Heights in Canada: Natural Resources Canada. Contains information licensed under the Open Government Licence \u2013 Canada. https://open.canada.ca/en/open-government-licence-canada",
    "Heights in Canada: produced using Copernicus WorldDEM-30 \u00a9 DLR e.V. 2010-2014 and \u00a9 Airbus Defence and Space GmbH 2014-2018 provided under COPERNICUS by the European Union and ESA; all rights reserved.",
    "The organisations in charge of the Copernicus programme by law or by delegation do not incur any liability for any use of the Copernicus WorldDEM-30.",
    "Heights in the United States: 3DEP elevation data courtesy of the U.S. Geological Survey, public domain",
]
NOTICE_FILE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "notices", "routing-NOTICE.txt")


class RoutingManifestTest(unittest.TestCase):

    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.out = os.path.join(self.dir.name, "out")
        os.makedirs(os.path.join(self.out, "segments"))
        os.makedirs(os.path.join(self.out, "names1"))
        for name, body in [("W125_N45.rd5", b"square one"), ("E170_N50.rd5", b"square two!")]:
            with open(os.path.join(self.out, "segments", name), "wb") as f:
                f.write(body)
        with open(os.path.join(self.out, "names1", "W125_N45.fnm"), "wb") as f:
            f.write(b"names")

    def tearDown(self):
        self.dir.cleanup()

    def stage(self, notice=NOTICE_FILE, run_id="usca-20261101T102042Z"):
        return run("routing-manifest", self.out, "--run-id", run_id, "--osm-timestamp", "2026-10-31T20:20:51Z",
                   "--osm-sequence", "us:2429,canada:4959", "--notice", notice)

    def test_the_notice_file_holds_the_ruled_wording_verbatim(self):
        with open(NOTICE_FILE, encoding="utf-8") as f:
            self.assertEqual([line for line in f.read().split("\n") if line], NOTICE_LINES)

    def test_notice_is_copied_beside_the_manifest_byte_for_byte(self):
        r = self.stage()
        self.assertEqual(r.returncode, 0, r.stderr)
        with open(NOTICE_FILE, "rb") as a, open(os.path.join(self.out, "NOTICE"), "rb") as b:
            self.assertEqual(a.read(), b.read())

    def test_the_routing_manifest_is_in_the_r2_shape_with_the_notices(self):
        self.assertEqual(self.stage().returncode, 0)
        with open(os.path.join(self.out, "routing-manifest.json"), encoding="utf-8") as f:
            m = json.load(f)
        run_id = "usca-20261101T102042Z"
        self.assertEqual(m["run_id"], run_id)
        self.assertEqual(m["osm_timestamp"], "2026-10-31T20:20:51Z")
        self.assertEqual(m["osm_sequence"], "us:2429,canada:4959")
        # The Worker's parseManifest (server/pmtiles-worker/src/routing/manifest.ts) reads files under
        # <run_id>/segments4/, sorted here by path so the file is reproducible.
        self.assertEqual([f["path"] for f in m["files"]], [f"{run_id}/segments4/E170_N50.rd5", f"{run_id}/segments4/W125_N45.rd5"])
        self.assertEqual([f["size"] for f in m["files"]], [11, 10])
        self.assertEqual(m["files"][1]["sha256"], hashlib.sha256(b"square one").hexdigest())
        self.assertEqual([f["path"] for f in m["names"]], [f"{run_id}/names1/W125_N45.fnm"])
        self.assertEqual(m["notice"], f"{run_id}/NOTICE")
        self.assertEqual(m["notices"], NOTICE_LINES)

    def test_a_missing_notice_file_is_refused_and_nothing_is_staged(self):
        r = self.stage(notice=os.path.join(self.dir.name, "absent.txt"))
        self.assertNotEqual(r.returncode, 0)
        self.assertFalse(os.path.exists(os.path.join(self.out, "routing-manifest.json")))
        self.assertFalse(os.path.exists(os.path.join(self.out, "NOTICE")))

    def test_a_malformed_run_id_is_refused(self):
        # The Worker refuses a run_id outside [A-Za-z0-9][A-Za-z0-9._-]{0,63} (manifest.ts RUN_ID).
        self.assertNotEqual(self.stage(run_id="../usca").returncode, 0)


if __name__ == "__main__":
    unittest.main()
