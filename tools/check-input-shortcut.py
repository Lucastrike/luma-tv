#!/usr/bin/env python3
"""Exercise the system TV_INPUT route and assert visible selector/toggle behavior."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import sys
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package", default="dev.luma.tv")
    parser.add_argument("--timeout", type=float, default=6)
    parser.add_argument("--long-press", action="store_true")
    args = parser.parse_args()
    device = os.environ.get("LUMA_DEVICE")
    if not device:
        parser.error("Set LUMA_DEVICE to the TV serial or IP:port")
    if not re.fullmatch(r"[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+", args.package):
        parser.error("Invalid package")
    if not 1 <= args.timeout <= 30:
        parser.error("timeout must be between 1 and 30 seconds")
    root = Path(__file__).resolve().parent.parent
    os.environ.setdefault("ANDROID_USER_HOME", str(root / ".local/adb-state"))
    adb = [os.environ.get("LUMA_ADB", str(root / ".local/android-tools/platform-tools/adb")),
           "-P", os.environ.get("LUMA_ADB_PORT", "5038"), "-s", device]

    def tv(*command):
        return subprocess.check_output(adb + list(command), text=True, timeout=15).replace("\r", "")

    def normalize(component):
        package, name = component.split("/", 1)
        return package + "/" + (package + name if name.startswith(".") else name)

    def window_state():
        state = tv("shell", "dumpsys", "window", "windows")
        window = re.search(r"mCurrentFocus=Window\{[^\n]*? u\d+ ([^\s}]+)", state)
        activity = re.search(r"mFocusedApp=[^\n]*ActivityRecord\{[^\n]*? u\d+ ([^\s}]+)", state)
        return (normalize(window.group(1)) if window and "/" in window.group(1) else "<none>",
                normalize(activity.group(1)) if activity and "/" in activity.group(1) else "<none>")

    def focus():
        return window_state()[0]

    def wait_for(predicate):
        deadline = time.monotonic() + args.timeout
        while time.monotonic() < deadline:
            current = focus()
            if predicate(current):
                return current
            time.sleep(0.15)
        raise AssertionError("Unexpected focused window: " + focus())

    services = tv("shell", "settings", "get", "secure", "enabled_accessibility_services")
    if re.search(r"(?:dev\.luma\.tv|" + re.escape(args.package) + r")/[^:]*InputKeyService", services):
        raise AssertionError("Disable Luma's old accessibility key filter before this check")
    original_window, original = window_state()
    print("Previous focused window: " + original_window + "; activity: " + original)
    if original == "<none>":
        raise AssertionError("A foreground app is required before testing")
    if "InputsActivity" in original:
        raise AssertionError("Close the existing input selector before testing")
    picker = args.package + "/dev.luma.tv.InputsActivity"
    short_picker = args.package + "/.InputsActivity"
    key = ["shell", "input", "keyevent"]
    if args.long_press:
        key.append("--longpress")
    tv(*key, "178")
    opened = False
    try:
        wait_for(lambda current: current in (picker, short_picker))
        opened = True
        time.sleep(0.6)
        assert focus() in (picker, short_picker), "One press opened and immediately closed the selector"
        print("PASS: system TV_INPUT opens the selector without an accessibility key filter")
        tv("shell", "input", "keyevent", "178")
        try:
            wait_for(lambda current: current == original)
        except AssertionError as error:
            raise AssertionError(str(error) + "; expected return to " + original) from error
        opened = False
        print("PASS: second Input closes the selector and restores the original window")
    finally:
        if opened and focus() in (picker, short_picker):
            tv("shell", "input", "keyevent", "4")


if __name__ == "__main__":
    try:
        main()
    except (AssertionError, subprocess.SubprocessError) as error:
        print("FAIL: " + str(error), file=sys.stderr)
        sys.exit(1)
