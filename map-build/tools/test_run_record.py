#!/usr/bin/env python3
"""Tests for run_record.py's status rules and manifest, run through its real command line.

Run: python3 -m unittest map-build/tools/test_run_record.py
"""
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


if __name__ == "__main__":
    unittest.main()
