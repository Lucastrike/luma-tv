# Luma 2.5.6 security review — November 5, 2026

## Result and scope

The review found no critical or high-severity issue in the pending launcher changes. One build-isolation
weakness was fixed before publication. Existing network and optional House Control limitations remain
documented below; this is not a claim that the entire app or TV firmware is free of vulnerabilities.

Scope: pending changes since `dc04675`, the production/QA build paths, the compiled APKs, and repository
publication hygiene. Runtime changes cover local playback memory, return focus, input ordering, and a
translucent input selector. Adjacent network, WebView, intent, storage, and service boundaries were read
to identify interactions. No Xiaomi package or permission was changed by this review.

## Finding 1 — Moderate: test builds could use production identity or output (fixed)

Previously, `LUMA_TEST=1` could be combined with the default production package. The same flag exposes
InputsActivity for QA and adds instrumentation and a fixture. A custom package could also overwrite the
default production APK path. Incorrect local build flags could therefore produce a test-enabled app
under the production identity or replace the artifact intended for installation. This required a build
operator mistake; it was not an unauthenticated remote build endpoint. The installed production APK did
not contain these test components.

`build.sh:18` now accepts only test mode `0` or `1`; `build.sh:20` requires a separate `.qa` package for
instrumentation; `build.sh:23` rejects custom packages writing to the production APK, resolving path
aliases. These checks run before deleting existing build outputs. Four regressions exercise rejected
configurations and verify that existing artifacts and classes survive. Valid QA and production builds
also complete successfully.

## Finding 2 — Moderate: application-wide cleartext HTTP remains enabled (inherited)

`AndroidManifest.xml:23` allows cleartext traffic application-wide. `HouseWidgets.java:52` uses a loopback
HTTP renderer, while `NowPlayingWidget.java:207` and `UpNextWidget.java:202` accept HTTP artwork supplied
by media apps. An attacker on the network path can observe or modify artwork fetched over HTTP. The
weather and geolocation URLs in Places/WeatherWidget use HTTPS. No disabled certificate or hostname
validation was found in those clients.

Recommended follow-up: replace the global allowance with a network-security configuration limited to
the required local renderer and require HTTPS for remote artwork, checking compatibility with media
providers. This changes inherited connectivity behavior and is outside the installed Input fix. The
review did not capture network traffic or demonstrate a live interception attack.

Android documents the risk and scoped configuration in its
[cleartext communication guidance](https://developer.android.com/privacy-and-security/risks/cleartext-communications).

## Finding 3 — Low, conditional: House Control renderer trusts a local TCP endpoint (inherited)

`HouseWidgets.java:52` loads `127.0.0.1:8788` after connecting to the companion's explicit Binder component.
The WebView enables JavaScript and exposes a small bridge at `HouseWidgets.java:70`. File/content access
is disabled, navigation is blocked, and resource requests are restricted to the fixed loopback origin.
The bridge supplies a home snapshot and rendering properties; scene commands use Binder, not the bridge.

The renderer response is not cryptographically tied to that Binder connection. If an untrusted local
process could occupy the port while an authorized companion connection exists, it could potentially
read the home snapshot through its own page. This is a trust-boundary observation, not a reproduced
exploit. The companion source and its authorization/HTTP-server implementation were not available in
this repository, and the integration is not validated with this fork's signing certificate.

Before enabling that integration, verify the companion's certificate authorization and endpoint
ownership, or serve renderer content through an authenticated bound interface. Relevant Android guidance:
[native WebView bridges](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges).

## Finding 4 — Low: artwork decoding and work queues lack a strict input budget

`NowPlayingWidget.java:208` decodes a supplied image before the new persistence code scales it to 512
pixels. `PlaybackMemory.java:30` uses a serial executor with an unbounded queue. A malicious or broken
media publisher could supply large images or rapid artwork changes and cause memory/CPU pressure.
Exception handling and a small persisted file do not impose a strict peak-memory bound. No code
execution or data-exfiltration path was established, and this review did not run a hostile-input stress test.

Follow-up: bound downloads and decoded dimensions before allocation, coalesce pending artwork writes,
and test repeated metadata changes under a fixed memory budget. Remote-image decoding predates this
release; the persistence queue is new. This is recorded as a resilience limitation of the media path.

## Verified release boundaries

- The compiled production InputsActivity is explicitly `exported=false`, uses an empty task affinity,
  and keeps no history. It ignores external launch data and obtains input IDs from TvInputManager.
- MainActivity remains exported because Android must be able to select it as Home. Navigation state is
  saved in an activity Bundle, not a file path or executable payload supplied by another app.
- InputKeyService and MediaListener retain the platform binding permissions. No permission was added
  relative to the baseline manifest. Input consumes only its existing key pair and requests no screen
  contents; the media listener processes media sessions and has no notification-payload callbacks.
- The production APK has no instrumentation, ReturnFixture, regression/probe class definitions,
  debug flag, or test-only flag. QA has its own package, explicit instrumentation target, and private fixture.
- Playback preferences and the single preview file use internal private storage. The path is fixed,
  metadata does not choose a destination path, writes are atomic, and app backup remains disabled.
- Both rebuilt variants verify with apksigner and the existing signing certificate. Production runtime
  entries match the already installed 2.5.6 build; build guards do not change remote-key handling.
- `.local/`, `.signing/`, `build/`, and `dist/` are ignored and have no tracked files. The local keystore
  has owner-only file permissions. The known local-development keystore password in build.sh is not a
  published private signing key; future updates still depend on protecting that private file.

Explicit export settings follow Android's
[component export guidance](https://developer.android.com/privacy-and-security/risks/android-exported).

## Validation and publication hygiene

The existing TV run passed **42 regression checks** and the user separately confirmed physical Input,
arrows, OK, Back, and return focus. The security-only build guards passed **4 local tests**; all **10
recovery tests** and shell syntax checks passed. Valid QA and production builds were compiled separately
and their manifests, DEX class definitions, permissions, runtime contents, and signatures were checked.
No additional APK installation or production authorization change was needed for the build guards.

Gitleaks **8.30.1** was downloaded from the
[official release](https://github.com/gitleaks/gitleaks/releases/tag/v8.30.1). Its archive SHA-256 was
checked against the official GitHub release-asset digest before execution. Scanning runs locally with
redacted reports; source code is not sent to a scanning service. The pre-commit scan of all **14 existing
commits** returned zero findings. The intended publication tree and staged diff also returned zero
findings before commit/push. A separate location/credential-pattern review found only a sample private IP in an
existing script comment, not a device address, account, token, or private key in the pending files.

Raw scanner output, APK checks, screenshots, TV settings, and the Xiaomi inventory stay under ignored
`.local/diagnostics/`. They are not part of the publication. A clean scanner result cannot prove the
absence of every secret or vulnerability. Firmware, installed vendor apps, companion authorization,
network interception, hostile-input fuzzing, and a full penetration test remain outside this review.
