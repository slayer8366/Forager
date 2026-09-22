"""device_guard.py: step 5. The guard shells out to adb, aapt2 and
apksigner; these tests replace all three with fakes whose output is set per
test, so every device answer the guard acts on is one the test chose."""
import os
import shutil
import stat
import tempfile
import unittest
from pathlib import Path

from harness import bash, run_hook

HOOK = "device_guard.py"
FORAGER = "com.zynergylabs.forager.app"
LAUNCHER = "com.sec.android.app.launcher"
LEAVE = "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"

FAKE_ADB = r'''#!/usr/bin/env python3
import os, shutil, sys
a = sys.argv[1:]
while a and a[0] in ("-s", "-t"):
    a = a[2:]
open(os.environ["FAKE_LOG"], "a").write(" ".join(a) + "\n")
if a[:4] == ["shell", "dumpsys", "activity", "activities"]:
    fg = os.environ.get("FAKE_FOREGROUND", "")
    if fg == "ERROR":
        print("error: no devices/emulators found", file=sys.stderr); sys.exit(1)
    print("  mResumedActivity: ActivityRecord{1a2b u0 %s/.Main t9}" % fg if fg else "  (nothing resumed)")
    sys.exit(0)
if a[:3] == ["shell", "pm", "path"]:
    state = os.environ.get("FAKE_INSTALLED", "0")
    if state == "ERROR":
        print("error: device offline", file=sys.stderr); sys.exit(1)
    if state == "1":
        print("package:/data/app/~~x/%s-1/base.apk" % a[3]); sys.exit(0)
    sys.exit(1)
if a[:1] == ["pull"]:
    shutil.copyfile(os.environ["FAKE_INSTALLED_APK"], a[2]); sys.exit(0)
sys.exit(0)
'''
FAKE_AAPT2 = r'''#!/usr/bin/env python3
import os, sys
if os.environ.get("FAKE_PKG") == "ERROR":
    print("error: not an apk", file=sys.stderr); sys.exit(1)
print("package: name='%s' versionCode='7' versionName='1.0'" % os.environ["FAKE_PKG"])
'''
FAKE_APKSIGNER = r'''#!/usr/bin/env python3
import sys
digest = open(sys.argv[-1]).read().strip()
# The shape apksigner 37.0.0 prints, observed on Forager's own debug APK.
print("V2 Signer: certificate DN: CN=test")
print("V2 Signer: certificate SHA-256 digest: " + digest)
'''


class DeviceGuard(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp(prefix="device_guard_test_"))
        tools = {"adb": FAKE_ADB, "aapt2": FAKE_AAPT2, "apksigner": FAKE_APKSIGNER}
        for name, body in tools.items():
            p = self.tmp / name
            p.write_text(body)
            p.chmod(p.stat().st_mode | stat.S_IEXEC)
        self.log = self.tmp / "adb.log"
        self.apk = self.tmp / "app-debug.apk"
        self.apk.write_text("aa11")          # the new APK's signing digest
        self.installed = self.tmp / "installed.apk"
        self.installed.write_text("aa11")    # the installed build's digest
        self.env = {"FORAGER_GUARD_ADB": str(self.tmp / "adb"),
                    "FORAGER_GUARD_AAPT2": str(self.tmp / "aapt2"),
                    "FORAGER_GUARD_APKSIGNER": str(self.tmp / "apksigner"),
                    "FAKE_LOG": str(self.log), "FAKE_PKG": FORAGER,
                    "FAKE_INSTALLED_APK": str(self.installed),
                    "FAKE_FOREGROUND": FORAGER, "FAKE_INSTALLED": "0"}

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    def decide(self, command, **env):
        e = dict(self.env)
        e.update(env)
        return run_hook(HOOK, bash(command, "coder"), env=e)

    # connectedAndroidTest
    def test_connected_android_test_without_flag_denied(self):
        for command in ("./gradlew connectedAndroidTest",
                        "./gradlew connectedAndroidTest --dry-run",
                        "./gradlew :app:connectedDebugAndroidTest"):
            with self.subTest(command):
                decision, reason = self.decide(command)
                self.assertEqual(decision, "deny")
                self.assertIn(LEAVE, reason)

    def test_connected_android_test_with_flag_passes(self):
        decision, reason = self.decide(f"./gradlew connectedDebugAndroidTest {LEAVE}")
        self.assertIsNone(decision, reason)

    # uninstall / clear
    def test_uninstall_and_clear_denied(self):
        for command, word in (("adb uninstall com.example.doesnotexist", "adb uninstall"),
                              ("adb -s R5CT shell pm uninstall " + FORAGER, "pm uninstall"),
                              ("adb shell pm clear " + FORAGER, "pm clear")):
            with self.subTest(command):
                decision, reason = self.decide(command)
                self.assertEqual(decision, "deny")
                self.assertIn(word, reason)

    # install signature
    def test_install_when_not_installed_passes(self):
        decision, reason = self.decide(f"adb install -r {self.apk}", FAKE_INSTALLED="0")
        self.assertIsNone(decision, reason)

    def test_install_same_signature_passes(self):
        decision, reason = self.decide(f"adb install -r {self.apk}", FAKE_INSTALLED="1")
        self.assertIsNone(decision, reason)
        self.assertIn(f"pull /data/app/~~x/{FORAGER}-1/base.apk", self.log.read_text())

    def test_install_different_signature_denied(self):
        self.installed.write_text("ff99")
        decision, reason = self.decide(f"adb -s R5CT install -r {self.apk}", FAKE_INSTALLED="1")
        self.assertEqual(decision, "deny")
        self.assertIn("aa11", reason)
        self.assertIn("ff99", reason)
        self.assertIn(FORAGER, reason)

    def test_install_undeterminable_denied(self):
        for env in ({"FAKE_PKG": "ERROR"}, {"FAKE_INSTALLED": "ERROR"}):
            with self.subTest(env):
                decision, reason = self.decide(f"adb install {self.apk}", **env)
                self.assertEqual(decision, "deny")
                self.assertIn("could not", reason)

    # foreground
    def test_input_and_screencap_blocked_when_forager_not_in_front(self):
        for command in ("adb shell input keyevent 0", "adb exec-out screencap -p",
                        "adb -s R5CT shell screencap /sdcard/s.png"):
            with self.subTest(command):
                decision, reason = self.decide(command, FAKE_FOREGROUND=LAUNCHER)
                self.assertEqual(decision, "deny")
                self.assertIn(LAUNCHER, reason)

    def test_input_allowed_when_forager_in_front(self):
        decision, reason = self.decide("adb shell input keyevent 0", FAKE_FOREGROUND=FORAGER)
        self.assertIsNone(decision, reason)

    def test_foreground_unreadable_denied(self):
        for fg in ("ERROR", ""):
            with self.subTest(fg):
                decision, reason = self.decide("adb shell input keyevent 0", FAKE_FOREGROUND=fg)
                self.assertEqual(decision, "deny")
                # Its own wording: with this branch gone, the fallthrough still
                # denies ("the foreground app is None") and also says foreground.
                self.assertIn("could not read the foreground app", reason)

    def test_unrelated_commands_untouched(self):
        for command in ("git status", "adb devices", "adb shell getprop ro.product.model",
                        "./gradlew testDebugUnitTest"):
            with self.subTest(command):
                decision, reason = self.decide(command)
                self.assertIsNone(decision, reason)

    def test_applies_to_every_role(self):
        e = dict(self.env)
        for who in (None, "pulse", "coder", "general-purpose"):
            with self.subTest(who):
                decision, _ = run_hook(HOOK, bash("adb uninstall x", who), env=e)
                self.assertEqual(decision, "deny")


if __name__ == "__main__":
    unittest.main()
