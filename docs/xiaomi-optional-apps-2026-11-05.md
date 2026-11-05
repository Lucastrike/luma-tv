# Xiaomi optional apps and held remote keys — November 5, 2026

## Current outcome

All eight selected packages have been restored to their saved default state (`0`) at the user's request.
After the disable trial, the user reported YouTube stuck at its logo and another internet app failing to
load. Following restoration, the user confirmed that the apps worked again. Exact package states, Luma
as the resolved Home, the inactive Input filter, and the unchanged disabled states of other packages
were verified. No app data was cleared, and Luma was not removed.

This does not identify a responsible package. The user also restarted the TV while ADB was unavailable,
and app loading was not measured between that reboot and package restoration. An earlier private
November 4 investigation had already recorded reports of intermittent multi-app startup stalls before
this eight-package trial. Any further removal needs an individual comparison against this working baseline.

## Earlier disable trials

In the repeat test with new remote batteries,
the Xiaomi RC remained an Android input device after each package group and reconnected after a full
reboot. Luma remained Home, the eight disabled states persisted, and the Input filter stayed off.

The first disable trial was rolled back after the user reported that every physical remote button had
stopped following reboot. Its input dump contained no Xiaomi RC device: Bluetooth was on, the remote
remained bonded but disconnected, and native dispatch was enabled and unfrozen. The eight captured
package states were restored exactly. A temporary Bluetooth restart was attempted during recovery but
did not complete as a verified fix. No bond was removed, and the radio remained on. The user then reported
replacing the batteries and explicitly requested another eight-package test. This sequence does not
establish a package dependency as the cause of the first disconnection.

The separate repeat-only issue was reproduced before any package disabling: removing Luma's Input
accessibility filter restored held volume/channel actions. That filter remains off, with the global
physical Input shortcut unavailable and Luma's in-app selector available.

## Held volume and channel keys

The user reported that holding volume or channel produced only one action instead of repeated actions.
Before changing any Xiaomi app, the device's input and accessibility configuration was captured locally.
InputDispatcher still reported its usual 500 ms initial repeat timeout and 50 ms repeat interval.

Only Luma's InputKeyService entry was removed from `enabled_accessibility_services`. As it was the sole
enabled accessibility service, `accessibility_enabled` was returned to `0`. The user then confirmed that
**both volume and channel repeated again when held**. No repeat timing, key layout, Bluetooth pairing,
notification-listener access, or firmware setting was changed during this comparison.

This identifies the active accessibility key-filter route as incompatible with native repetition on this
reference firmware. Returning `false` for volume/channel in the Java callback had not been sufficient to
preserve the system's held-key behavior. Earlier callback and UI tests did not exercise this physical path.
The real remote comparison is the evidence for the recovery; it is not replaced by an injected-key test.

The filter remains disabled. The user separately confirmed that the global physical Input shortcut then
opens nothing. This limitation existed before the optional apps were disabled. Luma's in-app Inputs
selector remains available. The current filtering method must not be re-enabled on this TV under a claim
that it preserves held-key repetition. A replacement global shortcut requires separate validation.

## Individual startup comparisons after rollback

Starting from the working configuration with all eight packages at state `0`, each package was disabled
alone, YouTube was force-stopped and launched through its resolved Leanback activity, and its screen
was captured after 15 seconds. The tested package was restored to state `0` before the next comparison;
the disabled-package list was checked against the control after each restoration. No app data was cleared.

All eight individual comparisons passed the reported startup symptom: seven captures showed the loaded
catalog. In the Mi TV Video Player comparison, the capture showed video, and YouTube's playback logs
reported state `3` with an advancing position. These observations do not establish audio output or a
post-reboot guarantee. They do not identify a single required package or reproduce the earlier multi-app
loading failure. Boot comparisons are a separate test.

Manifest and DEX inspection also did not identify a mandatory YouTube dependency on a selected package.
PatchWall exposes account, playback, and recommendation interfaces, including Netflix DET integration.
Update Service shares the system UID. Update Service and Mi Channel bundle Wi-Fi/DNS utility code, but
the presence of that code alone does not establish that either package initializes the TV's network.
Static inspection is not grounds to name a culprit without a failing runtime comparison.

### Boot comparisons

With all eight packages restored, a new boot completed with Luma as Home and the Input filter inactive.
The active default network was validated before app launch. YouTube showed its catalog, and the user
confirmed that Netflix loaded normally.

An initial disabled-package boot comparison was discarded: the expected state `3` did not survive the
immediate reboot, so it was not a valid comparison. Android 9 schedules package-restriction writes with
a ten-second delay in [PackageManagerService](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-9.0.0_r61/services/core/java/com/android/server/pm/PackageManagerService.java).
An immediate ADB reboot may therefore precede persistence; the observed reset alone does not establish
that Xiaomi deliberately re-enabled a package. The repeat waited 20 seconds, ran `sync`, and checked all
eight states again before rebooting.

In that repeat, all eight disabled states persisted through the new boot. Luma remained Home, the Input
filter remained inactive, and the active default network validated before launch. YouTube again showed
its loaded catalog after 15 seconds. Netflix reached its main activity, but its screen could not be
captured, so this run does not independently establish Netflix catalog loading or playback. All eight
packages were then restored and verified at state `0`.

These comparisons did not reproduce the reported multi-app loading failure or identify one necessary
Xiaomi package. They do not rule out an intermittent or delayed fault. A future failing capture needs
to precede reboot, app restart, and package restoration so those changes do not confound the diagnosis.

## Saved state and optional packages

The original November 3 snapshots remain unchanged. A separate sanitized
[`xiaomi-estado-antes-debloat.json`](../recovery/xiaomi-mitv-mssp3/xiaomi-estado-antes-debloat.json)
records the states captured immediately before disabling the eight packages. Full device, service,
memory, Bluetooth, audio, and input records remain private under `.local/diagnostics/xiaomi-disable-20261105/`.
The access comparison is under `.local/diagnostics/remote-repeat-20261105/`.

| Package | App | Before | Current state |
| --- | --- | --- | --- |
| `com.mitv.tvhome.atv` | PatchWall | Default (0) | Restored default (0) |
| `com.xiaomi.mitv.updateservice` | PatchWall Update Service | Default (0) | Restored default (0) |
| `com.mitv.tvhome.michannel` | Mi Channel | Default (0) | Restored default (0) |
| `com.xiaomi.mitv.mediaexplorer` | Reproductor multimedia | Default (0) | Restored default (0) |
| `com.mitv.videoplayer` | Mi TV Video Player | Default (0) | Restored default (0) |
| `com.xiaomi.mimusic2` | Music Player | Default (0) | Restored default (0) |
| `com.mitv.gallery` | Galeria | Default (0) | Restored default (0) |
| `com.mitv.dream` | Fondos de pantalla Mi | Default (0) | Restored default (0) |

The trial used `pm disable-user --user 0`, not uninstall, root, app-data clearing, or firmware changes.
Restoration used `pm default-state --user 0` for the exact eight saved states. The packages and their data
remain on the TV. During the disabled trial, Xiaomi's own media browsing, playback, gallery, screensaver,
and recommendations were intentionally unavailable. No system-wide performance gain is established.

## Original-state recovery

The existing [recovery script](../recovery/xiaomi-mitv-mssp3/restaurar-xiaomi.py) now requires the new snapshot
as well as the two original snapshots. Original values take precedence over a later overlapping record.
It restores and verifies all eleven distinct package states before uninstalling either added launcher.

It also removes Luma's accessibility service and media notification-listener entry, preserving unrelated
services configured later. When no other accessibility service remains, it restores the original inactive
flag. The service settings are verified before any launcher package is changed. No Bluetooth or key-layout
restoration is needed because neither was modified. Empty setting values are quoted safely for the remote shell.

The extension passes **18 simulated-ADB recovery tests**, including missing/invalid snapshots, preservation
of unrelated services, partial setting failures, exact package restoration, and verification before uninstall.
The dry-run executes no ADB commands. The original-state restoration is not run on the TV merely as a test,
because that would undo the requested configuration and remove Luma.

Preview from the repository root:

```sh
python3 recovery/xiaomi-mitv-mssp3/restaurar-xiaomi.py --dry-run
```

To deliberately restore origin, supply the current address:

```sh
python3 recovery/xiaomi-mitv-mssp3/restaurar-xiaomi.py --address TV_IP:5555
```

## Runtime verification

Each package group was checked against the captured baseline. Luma remains Home; hardware input IDs,
native TV/input/settings services, Bluetooth remote support, and Netflix/YouTube launcher resolution remain
available. The configured screensaver points to an already unavailable Dreamy component; that stale
reference predates this operation and was left unchanged.

The eight requested packages were verified at state `3`, with no other additions to the disabled-package
list and no previously disabled package enabled. TV and HDMI 2 both reached the foreground and created
an active session for the selected hardware input. Android Settings, Netflix, and YouTube also reached
the foreground. Streaming apps were started using their resolved component, as Luma does.

A controlled reboot completed with a new boot ID. Luma remained the default Home, all eight package
states remained disabled, and the Input accessibility filter remained off. In the new-battery retest,
Android also listed the physical Xiaomi RC input device after reboot, beyond merely showing a running
Bluetooth service. The native tuner created a fresh session, and core TV services were present. The
in-app selector was opened separately with TV, SERIES X, HDMI 1, AV, and Barra de sonido in order.

Physical picture/sound verification is separate from these checks. An activity reaching the foreground
alone does not establish successful video playback, HDMI picture quality, or sound output.

After the new-battery retest, the user confirmed that the remote (including held volume/channel), TV/HDMI
picture and sound, and streaming playback in Netflix or YouTube all worked with the eight packages
disabled. This was the initial physical acceptance evidence; the later loading failure and restoration
supersede it as the current outcome. The global physical
Input shortcut remains unavailable with its filter disabled; the user was explicitly told this during
the acceptance check.

## Publication checks

The original snapshots are unchanged. Raw device records and screenshots remain in ignored `.local/`;
the additional public snapshot contains package states and Luma service names, without a TV address,
account, pairing address, or installation path. Gitleaks scanning of the intended publication tree
returned zero findings. The recovery changes quote remote shell arguments, validate the supplemental
snapshot, preserve unrelated access entries, and stop before package changes if access restoration fails.
