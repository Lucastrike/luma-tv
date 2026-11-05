#!/usr/bin/env python3
"""Restore the saved TV launcher, optional apps, Luma access changes and animation settings."""
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
OPTIONAL_PACKAGES = {
    "com.mitv.tvhome.atv", "com.xiaomi.mitv.updateservice", "com.mitv.tvhome.michannel",
    "com.xiaomi.mitv.mediaexplorer", "com.mitv.videoplayer", "com.xiaomi.mimusic2",
    "com.mitv.gallery", "com.mitv.dream",
}


def without_services(value, removed):
    """Recognize Android's short and fully qualified component names without deleting other services."""
    def canonical(component):
        package, separator, name = component.partition("/")
        return package + separator + (package + name if name.startswith(".") else name)

    removed = {canonical(component) for component in removed}
    return ":".join(component for component in value.split(":")
                    if component and component != "null" and canonical(component) not in removed)


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
    extra_path = HERE / "xiaomi-estado-antes-debloat.json"
    if not extra_path.is_file():
        raise SystemExit("The optional-app and remote snapshot is missing. Keep xiaomi-estado-antes-debloat.json beside this script.")
    extra = json.loads(extra_path.read_text())
    expected_model = backup.get("model", "MiTV-MSSP3")
    if luma_snapshot.get("model", expected_model) != expected_model or extra.get("model") != expected_model:
        raise SystemExit("The saved TV snapshots do not identify the same model.")
    if backup.get("serial") and luma_snapshot.get("serial") != backup["serial"]:
        raise SystemExit("The saved TV snapshots do not identify the same device.")
    if extra.get("schema_version") != 1 or set(extra.get("packages", {})) != OPTIONAL_PACKAGES:
        raise SystemExit("The supplemental snapshot does not contain the expected optional apps.")
    if any(type(value) is not int or value not in range(5) for value in extra["packages"].values()):
        raise SystemExit("The supplemental snapshot contains an invalid package state.")
    if extra.get("added_accessibility_services") != ["dev.luma.tv/dev.luma.tv.InputKeyService"] or \
            extra.get("added_notification_listeners") != ["dev.luma.tv/dev.luma.tv.MediaListener"] or \
            extra.get("remote_before_luma") != {"enabled_accessibility_services": "", "accessibility_enabled": "0"}:
        raise SystemExit("The supplemental snapshot does not match the recorded Luma access changes.")
    packages = dict(extra["packages"])
    packages.update(backup["packages"])  # The original baseline always wins over a later snapshot.
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
        # adb joins shell arguments remotely; quote once so an empty service list remains an argument.
        return run(["-s", serial, "shell", shlex.join(arguments)], **kwargs)

    run(["connect", serial], check=False)
    state = run(["-s", serial, "get-state"], check=False)
    if not args.dry_run and state != "device":
        raise SystemExit("Authorize debugging on the TV or restore local-network access, then rerun this script.")
    model = shell("getprop", "ro.product.model")
    if not args.dry_run and model != expected_model:
        raise SystemExit("The connected device is not the expected Xiaomi. No changes were made.")

    # Remove only access granted to Luma; preserve unrelated services configured after the original snapshot.
    for setting, added in [("enabled_accessibility_services", extra["added_accessibility_services"]),
                           ("enabled_notification_listeners", extra["added_notification_listeners"])]:
        current = shell("settings", "get", "secure", setting)
        if args.dry_run:
            print("# Remove only " + ":".join(added) + " from " + setting + "; preserve other entries.")
            continue
        restored = without_services(current, added)
        normalized = "" if current == "null" else current
        if restored != normalized:
            shell("settings", "put", "secure", setting, restored)
        if setting == "enabled_accessibility_services" and not restored:
            shell("settings", "put", "secure", "accessibility_enabled", extra["remote_before_luma"]["accessibility_enabled"])
        actual = shell("settings", "get", "secure", setting)
        if ("" if actual == "null" else actual) != restored:
            raise SystemExit("Luma access was not removed: " + setting)
        if setting == "enabled_accessibility_services" and not restored and \
                shell("settings", "get", "secure", "accessibility_enabled") != "0":
            raise SystemExit("Accessibility activation was not restored. No launcher was removed.")
    if args.dry_run:
        print("# Restore accessibility_enabled=0 only if no unrelated accessibility services remain.")

    # Make the original home available before altering the replacement.
    original_home_package = backup["original_home"].split("/", 1)[0]
    shell("pm", "enable", "--user", "0", original_home_package)
    shell("pm", "enable", "--user", "0", "com.mitv.tvhome.atv")
    shell("cmd", "package", "set-home-activity", "--user", "0", backup["original_home"])

    operations = {0: "default-state", 1: "enable", 2: "disable", 3: "disable-user", 4: "disable-until-used"}
    for package, value in packages.items():
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
        for package, value in packages.items():
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

    print("Plan displayed." if args.dry_run else "Original home, optional apps, Luma access changes and animation settings restored.")


if __name__ == "__main__":
    main()
