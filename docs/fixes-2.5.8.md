# Luma 2.5.8: project links and donation removal

Settings no longer includes **Support the author**. Its donation screen, payment addresses, QR images,
generator, and unused support artwork have been removed.

**About Luma** now shows `Lucastrike/luma-tv`, its issue tracker, and a QR code for this fork's source.
The original project is acknowledged with one line: `Based on 0xProbabillity/luma-tv`.
Links are configured in `res/raw/about.txt`; `tools/source-qr.sh` regenerates the source QR.
The original MIT license is preserved.

## Verification

- Production APK built and signed as `dev.luma.tv`, version 2.5.8, versionCode 35, with the existing key.
- Installed on the reference Xiaomi TV as an update, preserving app data, default Home, notification
  access, and the disabled accessibility key filter.
- Inspected Settings and About on the TV. Donation entry absent; version, fork URLs, upstream credit,
  and Done visible without clipping. Back restores focus to the About row.
- Decoded both the generated QR and the QR in the actual TV screenshot: both resolve to
  `https://github.com/Lucastrike/luma-tv`.
- Native Input bridge ready after the update. The system TV_INPUT check opens the selector and a second
  Input returns to the original window. This update does not change key handling; physical held-key,
  standby, and reboot validation remains recorded in the [2.5.7 report](fixes-2.5.7.md).
- APK inventory contains the source QR and no donation QR or payment-address resource.
- `git diff --check` passed.
