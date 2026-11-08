# Luma 2.5.8 publication security review — November 8, 2026

## Result and scope

No secrets or new critical/high-severity issues were found in the publication candidate. Scope covers
the native Input bridge, About/donation changes, supporting tools, production APK, and publication
history. The optional log permission and inherited risks below remain; this is not a certification
that the application or TV firmware has no vulnerabilities.

## SBP-001 — Moderate privacy boundary: optional system-log access

`AndroidManifest.xml:9` requests `READ_LOGS`. This is a broad permission: filtering the reader does not
narrow the permission itself. Android 9 defines both this permission and the service's `DUMP` gate as
`signature|privileged|development`; logs can contain private information. See the
[Android 9 permission definitions](https://android.googlesource.com/platform/frameworks/base/+/android-9.0.0_r1/core/res/AndroidManifest.xml#2529).

The bridge requires a separate computer grant, effective log-group access, a supported reference TV,
and saved opt-in (`NativeInputService.java:31`, `:48`, `:51`). It runs a fixed argument list restricted
to two log tags (`:94`), processes records in memory, and does not persist or transmit them. Acceptance
requires the native UID, exact priority/tag/message, and a fresh marker from Luma's UID
(`NativeInputSignal.java:15`). The shared system UID is a platform trust boundary, not a guarantee
against a compromised system component. Disable/revoke instructions are in the [Input report](fixes-2.5.7.md).

The service is exported for shell setup but requires `DUMP` (`AndroidManifest.xml:44`). Ordinary apps
without that permission cannot start it. The boot receiver and production selector are explicitly
private (`:45`, `:33`); notification actions use immutable, explicit PendingIntents. This follows
Android's [component export guidance](https://developer.android.com/privacy-and-security/risks/android-exported).

## SBP-002 — Existing network and optional-integration risks remain

Application-wide HTTP is still allowed (`AndroidManifest.xml:27`); remote artwork may use HTTP.
The optional House Control renderer still trusts its local endpoint (`HouseWidgets.java:52`, `:80`),
and media image/queue budgets remain as described in the [2.5.6 review](security-review-2.5.6.md).
These paths were not changed by this release. This review did not perform network interception,
hostile-image fuzzing, or companion/firmware penetration testing.

## Verified code and release boundaries

- Python commands use argument arrays without a shell; the Input check validates its package argument.
  The QR script quotes its URL argument and accepts an HTTPS source URL.
- The production APK is version 2.5.8, code 35, signed with the existing certificate. It has no
  instrumentation, QA fixture/probe classes, or debug/test flags. App backup is disabled. The selector
  is private, the new service is permission-gated, and the boot receiver is private in the compiled manifest.
- The only added requested permissions are the documented `READ_LOGS`, foreground-service, and boot
  permissions. Existing binding permissions on the media and accessibility services remain in place.
- The installed TV APK matches the audited local production APK byte for byte.
- The parser replay passes 32 checks, including forged senders/markers, buffered events, and duplicates.
  All four build-isolation checks pass. Prior real-device and physical-remote validation is recorded
  in the [2.5.7](fixes-2.5.7.md) and [2.5.8](fixes-2.5.8.md) reports.

## Publication hygiene

Gitleaks 8.30.1 ran locally with redacted reports against the complete candidate tree and the 17 existing
branch commits. The final staged tree and outgoing commit were checked separately. No findings were
reported. Additional checks covered private paths, the known device endpoint, key headers, artifact
paths, and commit metadata; the configured commit email is GitHub noreply.

The broad object inventory also found old device information in local Codex checkpoint trees. Those
trees are outside `main`'s ancestry and are excluded from the explicit `HEAD:refs/heads/main` push;
they are not evidence of a leak in the publishable branch. The branch's complete reachable blobs were
checked separately. No checkpoint references, tags, or mirrored refs are included in this push.

The source QR contains only the public fork URL. The Input fixture contains only native key messages;
no network endpoint, serial, account, or user content is included. Supplemental IP matches in the
publication tree are loopback addresses and an existing script's documented example address.
Email-like byte sequences in an unchanged compressed image are false positives.

Signing keys, ADB state, APKs, build output, screenshots, scanner reports, and full device logs remain
under ignored local directories and have no tracked files. The signing key has owner-only permissions.
A clean scan cannot prove the absence of every possible secret or vulnerability.
