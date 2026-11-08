# Luma 2.5.7 — native Input shortcut

## Problem and replacement

On the reference Xiaomi MiTV-MSSP3 running Android 9, Input is a global key handled by Xiaomi's
`GlobalKeyReceiver`. Its release handler explicitly starts Android TV Home's input panel. That package
is disabled to preserve Luma as Home, so each attempt ends with `ActivityNotFound`.

The former accessibility key filter opened Luma's selector but prevented native held-volume and
held-channel repetition on this firmware. The custom accessibility window tried earlier also failed
physical navigation. Both paths remain outside this replacement.

The new optional `NativeInputService` observes the native receiver's Input-release message and opens
the existing private `InputsActivity`. Android continues to own the selector's window, task and focus.
The bridge neither consumes nor synthesizes remote events, and creates no accessibility windows.

## Scope and access

This route is restricted to the reference model on Android 9, with Xiaomi's native receiver installed
as a system package under UID 1000. Other devices retain their previous setup path. Compatibility with
another firmware is not established by this restriction alone: the native message must still exist.

`READ_LOGS` needs an explicit one-time grant from a connected computer. This permission permits access
to system logs; the implementation limits its reader to the `GlobalKey` and startup-marker tags. It
accepts Input only from the expected native UID, with an exact tag, priority and message match. It does
not save or transmit log records. The permission is described in the official
[Android permission reference](https://developer.android.com/reference/android/Manifest.permission#READ_LOGS).

Each reader starts with a new random marker written under Luma's own UID. Native records are ignored
until that marker arrives, preventing old buffered presses from opening or toggling the selector after
a restart. A failed reader reconnects with bounded backoff. A stopped bridge leaves the native remote
dispatch path intact; Input then returns to its previous unavailable state.

The foreground service has a quiet ongoing notification with a Turn off action. Saved opt-in is restored
after boot, app replacement, or a new launcher process. Setup is protected by `android.permission.DUMP`
for app-internal or shell use; the boot receiver and production selector remain private. No new
notification-listener or accessibility authorization is requested.

## Setup and rollback

Installing an update does not enable the bridge or grant log access. On a supported TV, grant access
once and then choose **Settings → Remote Input button → Enable remote shortcut**:

```sh
adb shell pm grant dev.luma.tv android.permission.READ_LOGS
adb shell am force-stop dev.luma.tv
adb shell am start -n dev.luma.tv/.MainActivity
```

Restarting the TV instead of the last two commands also refreshes access. Android 9 stores the grant
immediately, but an existing app process keeps its old Linux groups. The device comparison showed
group 1007 absent before restarting Luma and present afterward, when the reader became ready. The app
checks effective group access and shows **Restart Luma** instead of running an unusable reader.
The permission-to-group mapping is defined in
[Android 9's platform permissions](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-9.0.0_r1/data/etc/platform.xml).

Shell setup can enable or disable the same service:

```sh
adb shell am start-foreground-service -n dev.luma.tv/dev.luma.tv.NativeInputService \
  -a dev.luma.tv.action.ENABLE_NATIVE_INPUT
adb shell am start-foreground-service -n dev.luma.tv/dev.luma.tv.NativeInputService \
  -a dev.luma.tv.action.DISABLE_NATIVE_INPUT
```

Turning the shortcut off in Settings or its notification stops the reader and saves the off state.
To remove log access as well, disable it first and then run:

```sh
adb shell pm revoke dev.luma.tv android.permission.READ_LOGS
```

Before the production trial, an APK of the previous source is prepared with the same signing certificate
and an install-compatible version code. It permits an in-place code rollback without removing app data.
Private backups and complete device records remain in `.local/diagnostics/input-repair-20261108/`.

## Validation

The new system-route check first failed on the installed 2.5.6 build: Input left MainActivity focused.
On the separate QA package, the same route opened Inputs and a second press restored the previous window,
with Luma's accessibility filter disabled. Long-press injection and separate runs over YouTube and
Android Settings also passed. A shell-forged native message was rejected. Restarting the service did
not replay buffered Input events.

The parser checks replay the actual native trace and test startup backlog, false senders, unrelated
keys, malformed records and duplicate records. The complete QA suite passed **43 checks**, including
selector windows, focus restoration, input ordering, media memory and the new component boundary.
A sanitized Input-only trace is included at `tools/fixtures/xiaomi-input-release.txt`; its standalone
parser run passes 32 checks. For example:

```sh
mkdir -p .local/native-input-tests
"$LUMA_JDK/bin/javac" --release 8 -d .local/native-input-tests \
  src/dev/luma/tv/NativeInputSignal.java tools/NativeInputSignalTest.java
"$LUMA_JDK/bin/java" -cp .local/native-input-tests dev.luma.tv.NativeInputSignalTest \
  tools/fixtures/xiaomi-input-release.txt
```

System-route checks are available through:

```sh
LUMA_DEVICE=TV_IP:5555 python3 tools/check-input-shortcut.py
LUMA_DEVICE=TV_IP:5555 python3 tools/check-input-shortcut.py --long-press
```

Use `--package dev.luma.tv.qa` while the separate QA build is installed.

An initial return-window check failed without recording its original window. The check now records
that window and reports the expected return target. The subsequent explicit YouTube and Settings
comparisons passed; the first result is not used as acceptance evidence.

## Installed result

Production **2.5.7 (versionCode 34)** is installed in place. Its APK matches the final local build, the
original signing certificate is retained, and QA has been removed. Luma remains the resolved Home.
The existing notification-listener authorization, disabled-package states and inactive accessibility
configuration were preserved. Log access is the new explicit grant used by this bridge.

The final build passed Input opening, toggling and return-window checks over live TV, after standby
and wake, immediately after Home, and after a complete kernel reboot. The service restored itself after
that reboot without a manual service start; it reported effective log access, readiness and zero reader
restarts. Readiness was observed about 69 seconds after the reboot request, including the TV's boot.

In QA, starts requested immediately after Home could be deferred by Android's protection against
switching to another UID. The corresponding code is in
[Android 9's ActivityStarter](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-9.0.0_r1/services/core/java/com/android/server/am/ActivityStarter.java).
The production bridge uses the launcher's UID. Its immediate Home/Input comparison passed. QA also
restored itself from BOOT_COMPLETED and observed physical Input presses before production installation.

After production installation and the full reboot, the user confirmed that Input works from Home and
YouTube, the selector's physical arrows and Back work, and both volume and channel repeat when held.
This real-remote confirmation is separate from the injected-key checks and service request counters.
The Xiaomi RC was also registered in the later post-boot snapshot; the initial snapshot preceded its
reconnection. No Bluetooth pairing or radio setting was changed.
