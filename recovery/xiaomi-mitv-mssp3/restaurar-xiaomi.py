#!/usr/bin/env python3
"""Restore the saved TV launcher, package states and animation settings."""
import argparse
import json
import os
import re
from pathlib import Path
import shlex
import shutil
import subprocess
import sys

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
LOCAL = ROOT / ".local"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true", help="Show commands without changing the TV")
    parser.add_argument("--adb", help="Path to adb instead of the project-local copy or PATH")
    parser.add_argument("--address", help="TV IP:port or ADB serial; required for sanitized snapshots")
    args = parser.parse_args()
    backup = json.loads((HERE / "xiaomi-estado-antes-optimizacion.json").read_text())
    luma_snapshot_path = HERE / "xiaomi-estado-antes-luma.json"
    if not luma_snapshot_path.is_file():
        raise SystemExit("The Luma installation snapshot is missing. Keep xiaomi-estado-antes-luma.json beside this script.")
    luma_snapshot = json.loads(luma_snapshot_path.read_text())
    expected_model = backup.get("model", "MiTV-MSSP3")
    if luma_snapshot.get("model", expected_model) != expected_model:
        raise SystemExit("The saved TV snapshots do not identify the same model.")
    if backup.get("serial") and luma_snapshot.get("serial") != backup["serial"]:
        raise SystemExit("The saved TV snapshots do not identify the same device.")
    serial = args.address or backup.get("serial")
    if not serial:
        if args.dry_run:
            serial = "TV_ADDRESS"
        else:
            raise SystemExit("Supply --address IP:port or an ADB serial. Device addresses are not stored in the repository.")
    bundled = LOCAL / "android-tools/platform-tools/adb"
    executable = args.adb or (str(bundled) if bundled.is_file() else shutil.which("adb"))
    if args.dry_run and not executable:
        executable = "adb"
    if not executable:
        raise SystemExit("ADB is unavailable. Supply --adb /absolute/path/to/adb.")
    state_directory = LOCAL / "adb-state"
    environment = dict(os.environ, ANDROID_USER_HOME=str(state_directory))
    base = [executable, "-P", "5038"]
    journal = []

    def run(arguments, timeout=20, check=True):
        command = base + arguments
        print(shlex.join(command), flush=True)
        if args.dry_run:
            return ""
        state_directory.mkdir(parents=True, exist_ok=True)
        result = subprocess.run(command, env=environment, capture_output=True, text=True, timeout=timeout)
        journal.append({"arguments": arguments, "returncode": result.returncode,
                        "stdout": result.stdout, "stderr": result.stderr})
        journal_path = LOCAL / "recovery/xiaomi-restauracion-resultado.json"
        journal_path.parent.mkdir(parents=True, exist_ok=True)
        journal_path.write_text(json.dumps(journal, indent=2))
        if result.stdout.strip():
            print(result.stdout.strip(), flush=True)
        if result.stderr.strip():
            print(result.stderr.strip(), file=sys.stderr, flush=True)
        if check and (result.returncode or "Error:" in result.stdout or "Exception" in result.stdout or "Failure [" in result.stdout):
            raise RuntimeError("Restoration command failed; the journal records the last completed step.")
        return result.stdout.strip()

    def shell(*arguments, **kwargs):
        return run(["-s", serial, "shell", *arguments], **kwargs)

    run(["connect", serial], check=False)
    state = run(["-s", serial, "get-state"], check=False)
    if not args.dry_run and state != "device":
        raise SystemExit("Authorize debugging on the TV or restore local-network access, then rerun this script.")
    model = shell("getprop", "ro.product.model")
    if not args.dry_run and model != expected_model:
        raise SystemExit("The connected device is not the expected Xiaomi. No changes were made.")

    # Make the original home available before altering the replacement.
    original_home_package = backup["original_home"].split("/", 1)[0]
    shell("pm", "enable", "--user", "0", original_home_package)
    shell("pm", "enable", "--user", "0", "com.mitv.tvhome.atv")
    shell("cmd", "package", "set-home-activity", "--user", "0", backup["original_home"])

    operations = {0: "default-state", 1: "enable", 2: "disable", 3: "disable-user", 4: "disable-until-used"}
    for package, value in backup["packages"].items():
        shell("pm", operations[value], "--user", "0", package)
    for name, value in backup["animations"].items():
        if value == "null":
            shell("settings", "delete", "global", name)
        else:
            shell("settings", "put", "global", name, value)

    resolved = shell("cmd", "package", "resolve-activity", "--brief", "-a",
                     "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
    if not args.dry_run and backup["original_home"] not in resolved:
        raise SystemExit("Original home is not the resolved default. Replacement launchers have been kept installed.")

    # Verify exact saved values before removing either replacement launcher.
    if not args.dry_run:
        for package, value in backup["packages"].items():
            raw = shell("dumpsys", "package", package)
            match = re.search(r"User 0:.*?installed=true.*?enabled=(\d+)", raw)
            if not match or int(match.group(1)) != value:
                raise SystemExit("Package state was not restored: " + package)
        for name, value in backup["animations"].items():
            if shell("settings", "get", "global", name) != value:
                raise SystemExit("Animation setting was not restored: " + name)
    shell("input", "keyevent", "3")
    shell("am", "start", "-W", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")

    # Only remove launchers that were added during this task, after original Home works.
    added_packages = []
    if not luma_snapshot["luma_path"]:
        added_packages.append("dev.luma.tv")
    if not backup["flauncher_present_before"]:
        added_packages.append("me.efesser.flauncher")
    for package in added_packages:
        present = shell("pm", "path", package, check=False)
        if args.dry_run or present.startswith("package:"):
            result = run(["-s", serial, "uninstall", package], timeout=60)
            if not args.dry_run and result != "Success":
                raise SystemExit("The original home works, but removing the added launcher failed: " + package)

    print("Plan displayed." if args.dry_run else "Original home, package states and animation settings restored.")


if __name__ == "__main__":
    main()
