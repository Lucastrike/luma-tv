# Luma 2.5.5 — November 5, 2026

## Returning to the launcher

Home previously selected YouTube again on every resume. A HOME intent also rebuilt the Home page,
even when the user was returning from another app opened through Apps, Settings, or a widget.

Returning from another activity now keeps the current page. Navigation records the focused component,
widget page, and scroll position. App packages and widget tile keys identify controls across rebuilds;
other controls use their view hierarchy path. The navigation snapshot is included in Android's saved
activity state. A Home press while Luma is already visible still opens Home.

The QA package includes a temporary foreground activity that exercises Android's actual pause, stop,
resume, and singleTask HOME delivery. Five original checks failed before the fix. The final suite adds
Back coverage for every section, activity recreation for Apps and Inputs, and foreground Home behavior.

## Input preference

The shared input list now orders TV, SERIES X, HDMI 1, AV, and Barra de sonido in that order. The widget
and in-app menu use this list. Input IDs, user labels, connection states, and channel intents remain the
same. Unknown devices sort naturally after AV and before audio systems. Preference is applied before
the six-input display limit, keeping TV and preferred devices visible.

## Remote change reverted

An experimental service-owned accessibility overlay attempted to keep the current TV input or app
behind the selector. Automated service-level checks passed, but the user reported that the physical
remote no longer worked, including its arrow keys. Those checks did not establish remote compatibility.

At the user's request, testing stopped. Luma was force-stopped to release its windows. InputKeyService
was restored byte-for-byte to its previously installed implementation. The experimental selector and
its instrumentation helpers were removed. The focus restoration and preferred input order remain.
The original input service still opens MainActivity, so opening Input over content remains unresolved.
No further overlay experiments are part of this version.

## Verification

The QA suite passed **37 checks** on the reference Xiaomi P1E / MiTV-MSSP3 (Android 9), including return
navigation, activity recreation, the real input order, and existing media, artwork, and movement checks.
This predates the service rollback; it is not a claim that the experimental remote change was safe.

```sh
LUMA_DEVICE=TV_IP:5555 \
ANDROID_SDK_ROOT="$PWD/.local/toolchain/sdk" \
LUMA_JDK="$PWD/.local/toolchain/jdk/Contents/Home" \
bash tools/test-tv.sh
```

The rollback build retains version 2.5.5 (`versionCode=32`) and the original signing certificate, allowing
an in-place update without deleting application data. Final installed-build and device checks are stored
in `.local/diagnostics/navigation-2.5.5-final-state.json`. The user confirmed that the physical remote works again and that focus remains on the last app opened.
No additional remote instrumentation is run after the rollback request.

After rollback, the Input authorization list was observed empty and `accessibility_enabled` was `0`.
The previously authorized component `dev.luma.tv/dev.luma.tv.InputKeyService` and activation flag `1`
were restored. Android reports the original service connected again. The user confirmed that Input and the arrow keys both work after this reactivation; the overlay
change remains reverted.
