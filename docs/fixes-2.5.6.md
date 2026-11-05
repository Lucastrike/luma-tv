# Luma 2.5.6 — November 5, 2026

## Input above the current app

The remote shortcut previously started MainActivity, bringing Home to the front before showing Inputs.
It now starts a dedicated translucent InputsActivity in its own temporary task. The app underneath
remains visible. Back returns to that app; another Input press closes the menu; OK opens the chosen
TV input. The menu is excluded from Recents and has no persistent history.

This uses Android's normal activity windows and focus navigation. The accessibility service's key
handling is unchanged: only complete Input key pairs are consumed, and other keys pass through.
The service opens a different activity; it creates no custom accessibility windows. No accessibility
authorization, notification access, or other permission is added.

The input order remains TV, SERIES X, HDMI 1, AV, and Barra de sonido. Up and Down move between those
rows. Left and Right retain the current row. Focus restoration elsewhere in Luma is preserved.

## Validation method

A regression first reproduced the wrong launch target: the Input key pair started MainActivity.
The corrected route is checked alongside real Android windows in the separate QA package. The tests
open Inputs above a foreground fixture, verify that the fixture remains visible without being stopped,
send arrow keys through Android's input system, and verify arrow navigation in the original app after
closing the selector. Other cases cover Input toggling, selecting HDMI with OK, and exiting with Home.

Installing another Home candidate can invalidate Android's preferred Home. The QA runner now reapplies
the previously selected Home immediately after installing QA, as well as restoring it during cleanup.
This avoids routing the Home test to Android's launcher chooser.

QA alone exposes its InputsActivity for external ADB window checks. Production keeps the activity
private to its own package. The production accessibility service is not instrumented or disconnected
during these tests, and its settings are not rewritten.

```sh
LUMA_DEVICE=TV_IP:5555 \
ANDROID_SDK_ROOT="$PWD/.local/toolchain/sdk" \
LUMA_JDK="$PWD/.local/toolchain/jdk/Contents/Home" \
bash tools/test-tv.sh
```

## Playback boundary

The selector keeps the previous activity visible. On Android 9, that activity receives onPause while
the selector owns keyboard focus. Each app controls whether it continues or pauses playback in that
state; keeping every app's video playing is not guaranteed by this change.

The Android window and task behavior is described in the official
[activity manifest reference](https://developer.android.com/guide/topics/manifest/activity-element) and
[windowIsTranslucent reference](https://developer.android.com/reference/android/R.attr#windowIsTranslucent).

## Completed verification

All **42 regression checks passed** on the reference Xiaomi. Separate window-state checks over device
Settings and an HDMI activity found that the original activity stayed visible without being stopped.
These checks do not establish that every app continues video playback while paused.

Production 2.5.6 (`versionCode=33`) was installed in place using the existing certificate. The installed APK
matches the production build. Home, accessibility/notification authorization, and application settings were
preserved, and the QA package was removed. The user confirmed that Input opens above the current app or
input and that the physical arrow keys, OK, and Back all work. The user also confirmed return focus.

Local verification records remain under `.local/diagnostics/inputs-activity/`; screenshots, device
inventories, keys, and raw logs are excluded from Git. No Xiaomi package was changed during the inventory.

The pre-publication [security review](security-review-2.5.6.md) also adds build guards: test instrumentation
cannot use the production package, and custom packages cannot overwrite the production APK. These are
build-time safeguards; they do not change the installed runtime or its remote-key handling.
