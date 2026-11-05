"""Exercise recovery safeguards without contacting a TV or running real ADB."""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import shutil
import shlex
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch


SOURCE = Path(__file__).resolve().parent


class SimulatedAdb:
    def __init__(self, model="MiTV-MSSP3", home=None, package_state=0, animation=None, secure=None, ignore_secure_writes=False):
        self.model = model
        self.home = home or "com.google.android.tvlauncher/.MainActivity"
        self.package_state = package_state
        self.animation = animation
        self.calls = []
        self.secure = {
            "enabled_accessibility_services": "dev.luma.tv/dev.luma.tv.InputKeyService",
            "accessibility_enabled": "1",
            "enabled_notification_listeners": "dev.luma.tv/dev.luma.tv.MediaListener:com.google.android.tvrecommendations/com.google.android.tvrecommendations.NotificationsService",
        }
        if secure is not None:
            self.secure.update(secure)
        self.ignore_secure_writes = ignore_secure_writes

    def __call__(self, command, **kwargs):
        self.calls.append((command, kwargs))
        args = command[3:]
        if args[0] == "connect":
            output = "connected\n"
        else:
            assert args[:1] == ["-s"], args
            args = args[2:]
            if args[:1] == ["shell"] and len(args) == 2:
                args = ["shell", *shlex.split(args[1])]
            if args == ["get-state"]:
                output = "device\n"
            elif args[:3] == ["shell", "getprop", "ro.product.model"]:
                output = self.model + "\n"
            elif args[:4] == ["shell", "cmd", "package", "resolve-activity"]:
                output = "priority=2 isDefault=true\n" + self.home + "\n"
            elif args[:3] == ["shell", "dumpsys", "package"]:
                output = f"User 0: installed=true enabled={self.package_state}\n"
            elif args[:4] == ["shell", "settings", "get", "global"]:
                value = "null" if args[4] == "animator_duration_scale" else "1.0"
                output = (self.animation or value) + "\n"
            elif args[:4] == ["shell", "settings", "get", "secure"]:
                output = self.secure.get(args[4], "null") + "\n"
            elif args[:4] == ["shell", "settings", "put", "secure"]:
                if not self.ignore_secure_writes:
                    self.secure[args[4]] = args[5]
                output = ""
            elif args[:3] == ["shell", "pm", "path"]:
                output = "package:/data/app/dev.luma.tv/base.apk\n" if args[3] == "dev.luma.tv" else ""
            else:
                output = "Success\n"
        return SimpleNamespace(returncode=0, stdout=output, stderr="")

    def arguments(self):
        result = []
        for command, _ in self.calls:
            args = command[3:]
            args = args[2:] if args[0] == "-s" else args
            if args[:1] == ["shell"] and len(args) == 2:
                args = ["shell", *shlex.split(args[1])]
            result.append(args)
        return result


class RecoveryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.here = self.root / "recovery/xiaomi-mitv-mssp3"
        self.here.mkdir(parents=True)
        for name in ["xiaomi-estado-antes-optimizacion.json", "xiaomi-estado-antes-luma.json", "xiaomi-estado-antes-debloat.json"]:
            shutil.copyfile(SOURCE / name, self.here / name)
        spec = importlib.util.spec_from_file_location("xiaomi_recovery", SOURCE / "restaurar-xiaomi.py")
        self.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)
        self.module.HERE = self.here
        self.module.ROOT = self.root
        self.module.LOCAL = self.root / ".local"

    def run_recovery(self, adb, *arguments, address=True):
        if address and "--dry-run" not in arguments and "--address" not in arguments:
            arguments = (*arguments, "--address", "TEST-TV")
        output = io.StringIO()
        with patch.object(sys, "argv", ["restaurar-xiaomi.py", *arguments]), \
                patch.object(self.module.shutil, "which", return_value=None), \
                patch.object(self.module.subprocess, "run", side_effect=adb), \
                contextlib.redirect_stdout(output), contextlib.redirect_stderr(output):
            self.module.main()
        return output.getvalue()

    def assert_no_uninstall(self, adb):
        self.assertFalse(any(args[0] == "uninstall" for args in adb.arguments()))

    def test_dry_run_works_without_adb_and_never_contacts_device(self):
        def forbidden(*args, **kwargs):
            self.fail("Dry-run attempted to execute ADB")

        output = self.run_recovery(forbidden, "--dry-run")
        self.assertIn("settings delete global animator_duration_scale", output)
        self.assertIn("uninstall dev.luma.tv", output)
        self.assertIn("com.google.android.tvlauncher/.MainActivity", output)
        self.assertFalse(self.module.LOCAL.exists())

    def test_success_opens_verified_original_home_before_removing_luma(self):
        adb = SimulatedAdb()
        output = self.run_recovery(adb, "--adb", "simulated-adb")
        calls = adb.arguments()
        uninstall = calls.index(["uninstall", "dev.luma.tv"])
        open_home = next(i for i, a in enumerate(calls) if a[:4] == ["shell", "am", "start", "-W"])
        self.assertLess(open_home, uninstall)
        self.assertEqual(sum(a[:3] == ["shell", "dumpsys", "package"] for a in calls[:uninstall]), 11)
        self.assertEqual(sum(a[:4] == ["shell", "settings", "get", "global"] for a in calls[:uninstall]), 3)
        self.assertIn(["shell", "settings", "delete", "global", "animator_duration_scale"], calls)
        self.assertNotIn(["uninstall", "me.efesser.flauncher"], calls)
        self.assertIn("settings restored", output)
        journal = self.module.LOCAL / "recovery/xiaomi-restauracion-resultado.json"
        self.assertTrue(journal.is_file())
        self.assertFalse((self.here / journal.name).exists())
        for _, kwargs in adb.calls:
            self.assertEqual(kwargs["env"]["ANDROID_USER_HOME"], str(self.module.LOCAL / "adb-state"))

    def test_all_optional_apps_are_restored_and_verified_before_uninstall(self):
        adb = SimulatedAdb()
        self.run_recovery(adb, "--adb", "simulated-adb")
        calls = adb.arguments()
        uninstall = calls.index(["uninstall", "dev.luma.tv"])
        for package in self.module.OPTIONAL_PACKAGES:
            restore = calls.index(["shell", "pm", "default-state", "--user", "0", package])
            verify = calls.index(["shell", "dumpsys", "package", package])
            self.assertLess(restore, verify)
            self.assertLess(verify, uninstall)

    def test_original_snapshot_overrides_later_patchwall_state(self):
        path = self.here / "xiaomi-estado-antes-debloat.json"
        snapshot = json.loads(path.read_text())
        snapshot["packages"]["com.mitv.tvhome.atv"] = 1
        path.write_text(json.dumps(snapshot))
        adb = SimulatedAdb()
        self.run_recovery(adb, "--adb", "simulated-adb")
        self.assertIn(["shell", "pm", "default-state", "--user", "0", "com.mitv.tvhome.atv"], adb.arguments())

    def test_luma_access_is_removed_before_any_launcher_is_changed(self):
        adb = SimulatedAdb()
        self.run_recovery(adb, "--adb", "simulated-adb")
        self.assertEqual(adb.secure["enabled_accessibility_services"], "")
        self.assertEqual(adb.secure["accessibility_enabled"], "0")
        self.assertEqual(adb.secure["enabled_notification_listeners"],
                         "com.google.android.tvrecommendations/com.google.android.tvrecommendations.NotificationsService")
        calls = adb.arguments()
        remove = calls.index(["shell", "settings", "put", "secure", "enabled_accessibility_services", ""])
        first_pm = next(i for i, a in enumerate(calls) if a[:2] == ["shell", "pm"])
        self.assertLess(remove, first_pm)

    def test_unrelated_services_survive_and_stay_enabled(self):
        adb = SimulatedAdb(secure={
            "enabled_accessibility_services": "other.reader/.Service:dev.luma.tv/.InputKeyService",
            "enabled_notification_listeners": "other.listener/.Service:dev.luma.tv/.MediaListener",
        })
        self.run_recovery(adb, "--adb", "simulated-adb")
        self.assertEqual(adb.secure["enabled_accessibility_services"], "other.reader/.Service")
        self.assertEqual(adb.secure["enabled_notification_listeners"], "other.listener/.Service")
        self.assertEqual(adb.secure["accessibility_enabled"], "1")
        self.assertFalse(any(a[:5] == ["shell", "settings", "put", "secure", "accessibility_enabled"] for a in adb.arguments()))

    def test_absent_luma_entries_are_idempotent_and_do_not_create_notification_settings(self):
        adb = SimulatedAdb(secure={"enabled_accessibility_services": "null", "accessibility_enabled": "0",
                                   "enabled_notification_listeners": "null"})
        self.run_recovery(adb, "--adb", "simulated-adb")
        self.assertEqual(adb.secure["enabled_notification_listeners"], "null")
        self.assert_no_luma_access(adb)

    def assert_no_luma_access(self, adb):
        self.assertNotIn("dev.luma.tv", adb.secure["enabled_accessibility_services"])
        self.assertNotIn("dev.luma.tv", adb.secure["enabled_notification_listeners"])

    def test_secure_setting_failure_stops_before_package_changes(self):
        adb = SimulatedAdb(ignore_secure_writes=True)
        with self.assertRaisesRegex(SystemExit, "Luma access was not removed"):
            self.run_recovery(adb, "--adb", "simulated-adb")
        self.assertFalse(any(a[:2] == ["shell", "pm"] for a in adb.arguments()))
        self.assert_no_uninstall(adb)

    def test_accessibility_activation_failure_stops_before_package_changes(self):
        adb = SimulatedAdb(secure={"enabled_accessibility_services": ""}, ignore_secure_writes=True)
        with self.assertRaisesRegex(SystemExit, "Accessibility activation was not restored"):
            self.run_recovery(adb, "--adb", "simulated-adb")
        self.assertFalse(any(a[:2] == ["shell", "pm"] for a in adb.arguments()))
        self.assert_no_uninstall(adb)

    def test_missing_or_invalid_supplement_stops_before_contacting_tv(self):
        path = self.here / "xiaomi-estado-antes-debloat.json"
        original = path.read_text()
        for failure in ["missing", "wrong model", "unexpected package", "invalid state", "wrong access"]:
            path.write_text(original)
            snapshot = json.loads(original)
            if failure == "missing":
                path.unlink()
            else:
                if failure == "wrong model": snapshot["model"] = "Different TV"
                if failure == "unexpected package": snapshot["packages"]["com.android.bluetooth"] = 0
                if failure == "invalid state": snapshot["packages"]["com.mitv.gallery"] = 99
                if failure == "wrong access": snapshot["added_accessibility_services"] = ["other/.Service"]
                path.write_text(json.dumps(snapshot))
            adb = SimulatedAdb()
            with self.subTest(failure=failure), self.assertRaises(SystemExit):
                self.run_recovery(adb, "--adb", "simulated-adb")
            self.assertEqual(adb.calls, [])

    def test_wrong_model_cannot_change_packages_or_remove_apps(self):
        adb = SimulatedAdb(model="Different TV")
        with self.assertRaisesRegex(SystemExit, "not the expected Xiaomi"):
            self.run_recovery(adb, "--adb", "simulated-adb")
        self.assertEqual(len(adb.calls), 3)
        self.assert_no_uninstall(adb)

    def test_sanitized_snapshot_requires_address_before_contacting_device(self):
        adb = SimulatedAdb()
        with self.assertRaisesRegex(SystemExit, "Supply --address"):
            self.run_recovery(adb, "--adb", "simulated-adb", address=False)
        self.assertEqual(adb.calls, [])

    def test_explicit_address_is_used_for_every_device_command(self):
        adb = SimulatedAdb()
        self.run_recovery(adb, "--adb", "simulated-adb", "--address", "EXPLICIT-TV")
        for command, _ in adb.calls:
            args = command[3:]
            if args[0] == "connect":
                self.assertEqual(args[1], "EXPLICIT-TV")
            else:
                self.assertEqual(args[:2], ["-s", "EXPLICIT-TV"])

    def test_mismatched_snapshot_models_stop_before_contacting_device(self):
        path = self.here / "xiaomi-estado-antes-luma.json"
        snapshot = json.loads(path.read_text())
        snapshot["model"] = "Different TV"
        path.write_text(json.dumps(snapshot))
        adb = SimulatedAdb()
        with self.assertRaisesRegex(SystemExit, "same model"):
            self.run_recovery(adb, "--adb", "simulated-adb")
        self.assertEqual(adb.calls, [])

    def test_missing_snapshot_stops_before_contacting_device(self):
        (self.here / "xiaomi-estado-antes-luma.json").unlink()
        adb = SimulatedAdb()
        with self.assertRaisesRegex(SystemExit, "snapshot is missing"):
            self.run_recovery(adb, "--adb", "simulated-adb")
        self.assertEqual(adb.calls, [])

    def test_original_home_resolution_failure_keeps_replacement(self):
        adb = SimulatedAdb(home="dev.luma.tv/.MainActivity")
        with self.assertRaisesRegex(SystemExit, "not the resolved default"):
            self.run_recovery(adb, "--adb", "simulated-adb")
        self.assert_no_uninstall(adb)

    def test_package_or_animation_mismatch_keeps_replacement(self):
        for failure, adb in [("Package state", SimulatedAdb(package_state=3)),
                             ("Animation setting", SimulatedAdb(animation="0.5"))]:
            with self.subTest(failure=failure), self.assertRaisesRegex(SystemExit, failure):
                self.run_recovery(adb, "--adb", "simulated-adb")
            self.assert_no_uninstall(adb)

    def test_luma_that_predates_intervention_is_not_removed(self):
        path = self.here / "xiaomi-estado-antes-luma.json"
        snapshot = json.loads(path.read_text())
        snapshot["luma_path"] = "package:/data/app/existing-luma/base.apk"
        path.write_text(json.dumps(snapshot))
        adb = SimulatedAdb()
        self.run_recovery(adb, "--adb", "simulated-adb")
        self.assert_no_uninstall(adb)


if __name__ == "__main__":
    unittest.main()
