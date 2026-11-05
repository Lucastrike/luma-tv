# Restore the Xiaomi MiTV-MSSP3 to its original configuration

This directory preserves recovery for the reference Xiaomi P1E running Android 9. Its destination is the
configuration before cleanup and Luma installation on **November 3, 2026**. FLauncher is not an alternate
recovery destination. The supplemental snapshot from **November 5** covers eight optional Xiaomi apps
and the access changes made for Luma's remote shortcut and media listener.

Published snapshots retain the states needed for restoration without the TV address, installation paths,
accounts, or raw diagnostics. Complete private records remain under the ignored `.local/` directory.

## What the script restores

1. Verify the authorized ADB connection, model, and consistency of all three snapshots before changing anything.
2. Remove only Luma's Input accessibility service and media notification-listener entry. Preserve unrelated
   services configured later. Restore `accessibility_enabled=0` only when no other accessibility service remains.
3. Enable the original Google Home and PatchWall before changing the replacement launcher.
4. Restore `com.google.android.tvlauncher/.MainActivity`, the original four package states, and the eight
   optional Xiaomi app states. The original snapshot takes precedence where records overlap.
5. Restore and verify the three animation settings: `1.0`, `1.0`, and no explicit `animator_duration_scale`.
6. Verify every saved package state and Home resolution, then open the original Home.
7. Remove Luma and any remaining FLauncher added during the intervention. Preserve apps that predated it.

The optional packages are PatchWall, PatchWall Update Service, Mi Channel, Reproductor multimedia,
Mi TV Video Player, Music Player, Galeria, and Fondos de pantalla Mi. Their package IDs and exact enabled
states are recorded in `xiaomi-estado-antes-debloat.json`. Restoration uses the saved state, rather than
assuming every app should have an explicit `enabled` override.

The eight optional packages were subsequently restored to those exact saved default states on
November 5 after a multi-app loading failure. The user confirmed that apps worked again. That rollback
kept Luma as Home and its Input filter inactive; it did not execute the full original-state restoration
below. The snapshots remain unchanged. See the [trial and rollback report](../../docs/xiaomi-optional-apps-2026-11-05.md).

If access removal, package states, animations, or original Home cannot be verified, the script stops
before uninstalling replacement launchers. It does not uninstall any Xiaomi system app or clear app data.
The Luma Input filter was found to suppress held volume/channel repetition on this firmware. Disabling
that filter restored repetition, as confirmed by the user; the global physical Input shortcut then stopped
opening a menu. Restoring to origin removes that Luma filter too. No repeat timing, Bluetooth pairing,
key layout, or firmware was changed. The in-launcher Inputs selector remains available while using Luma.

## Required files

| File | Purpose |
| --- | --- |
| [restaurar-xiaomi.py](restaurar-xiaomi.py) | Recovery script. |
| [xiaomi-estado-antes-optimizacion.json](xiaomi-estado-antes-optimizacion.json) | Original Home, four package states, and animations; authoritative baseline. |
| [xiaomi-estado-antes-luma.json](xiaomi-estado-antes-luma.json) | Confirms Luma was absent initially. Its historical FLauncher state is not a recovery destination. |
| [xiaomi-estado-antes-debloat.json](xiaomi-estado-antes-debloat.json) | Additional package states captured before disabling the eight apps, and the Luma access changes to reverse. |
| [xiaomi-prueba-restauracion-original.json](xiaomi-prueba-restauracion-original.json) | Evidence of the original complete restoration on November 3. It does not test the later extension. |

Keep all three state snapshots beside the script. **Never replace the original snapshots with a later
optimized state.** The script requires the supplemental snapshot too, so a missing copy cannot silently
skip the additional package or remote changes. Device addresses are supplied at execution with `--address`.

## Requirements

- Python 3 and [Android Platform Tools / ADB](https://developer.android.com/tools/releases/platform-tools).
- The computer and TV on the same network, with debugging enabled and this computer authorized on the TV.

ADB is selected from `--adb`, `.local/android-tools/platform-tools/adb`, or `PATH`, in that order. The
server port is **5038**, and local ADB state is under `.local/adb-state/`. A fresh clone needs its own
ADB installation and device authorization. Keys, tools, private backups, and execution logs are not published.

## Preview without contacting the TV

Run from the repository root:

```sh
python3 recovery/xiaomi-mitv-mssp3/restaurar-xiaomi.py --dry-run
```

This works without ADB installed, executes no device commands, and creates no keys or journal. Because
service lists depend on current device settings, the plan describes removal of Luma entries while
preserving other entries; it does not invent a future list of accessibility or notification services.

## Run recovery

```sh
python3 recovery/xiaomi-mitv-mssp3/restaurar-xiaomi.py --address TV_IP:5555
```

Replace `TV_IP:5555` with the current TV address or ADB serial. An actual run without an address stops
before contacting a device. To select another ADB executable, append `--adb /path/to/platform-tools/adb`.
If the TV requests debugging authorization, accept it and rerun the command.

Results are written to `.local/recovery/xiaomi-restauracion-resultado.json`. If recovery stops, the journal
records the last completed step. Remote shell arguments are quoted so empty service lists are preserved
and settings values are not interpreted as shell syntax.

The generic inherited `./luma.sh restore` reverses its own Home changes. **Use the dedicated script above
for this reference TV**, including optional apps, Luma access entries, original analytics states, and animations.

## Recover Home without ADB

1. Open Android Settings, then Apps, and show system apps.
2. Enable **Android TV Home** (`com.google.android.tvlauncher`) and **PatchWall** (`com.mitv.tvhome.atv`).
3. Open the original Home, select it as default if asked, then uninstall Luma TV.

These steps recover Home. To restore exact package/access/animation states too, run the dedicated script
when ADB is available again. Removing Luma through Android also removes its own service authorization.

## Evidence and limits

The original restoration was executed on the TV on November 3: the original Home, four package states,
and three animation values were verified, and Luma/FLauncher were absent. Luma was subsequently installed
again. The extended recovery path is checked with simulated ADB and dry-run; it is not executed on the TV
merely to test it, because that would remove the current launcher and undo the requested configuration.

The backup covers the described settings, not the entire firmware, accounts, deleted files, or data removed
by cleaners. Debugging remains as in the original reference, enabled by the user.

Run the recovery safeguards without a TV:

```sh
python3 -m unittest discover -s recovery/xiaomi-mitv-mssp3 -p 'test_*.py' -v
```
