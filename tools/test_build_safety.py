"""Reject unsafe build variants before touching an existing production artifact."""

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


class BuildSafetyTest(unittest.TestCase):
    def rejected(self, overrides, message):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shutil.copyfile(Path(__file__).resolve().parents[1] / "build.sh", root / "build.sh")
            artifact = root / "dist/luma-tv.apk"
            artifact.parent.mkdir()
            artifact.write_bytes(b"existing production artifact")
            classes = root / "build/classes/marker"
            classes.parent.mkdir(parents=True)
            classes.write_bytes(b"existing build")
            env = {key: value for key, value in os.environ.items() if not key.startswith("LUMA_")}
            env.update(overrides)
            result = subprocess.run(["bash", str(root / "build.sh")], env=env, capture_output=True, text=True, timeout=10)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn(message, result.stderr)
            self.assertEqual(artifact.read_bytes(), b"existing production artifact")
            self.assertEqual(classes.read_bytes(), b"existing build")

    def test_instrumentation_cannot_use_production_package(self):
        self.rejected({"LUMA_TEST": "1"}, "separate .qa package")

    def test_qa_cannot_overwrite_production_output(self):
        self.rejected({"LUMA_TEST": "1", "LUMA_PACKAGE": "dev.luma.tv.qa"}, "separate LUMA_APK")

    def test_custom_package_cannot_overwrite_production_output_alias(self):
        self.rejected({"LUMA_PACKAGE": "dev.luma.tv.custom", "LUMA_APK": "dist/../dist/luma-tv.apk"}, "separate LUMA_APK")

    def test_invalid_test_mode_is_rejected(self):
        self.rejected({"LUMA_TEST": "true"}, "LUMA_TEST must be 0 or 1")


if __name__ == "__main__":
    unittest.main()
