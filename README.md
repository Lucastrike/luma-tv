# Luma TV · Lucastrike fork

An Android TV and Google TV launcher based on [Luma by 0xProbabillity](https://github.com/0xProbabillity/luma-tv).
This fork maintains our fixes and an installation signed with our own key.

The current version is **2.5.1**, with the package ID **`dev.luma.tv`**. On the reference Xiaomi P1E, it replaces
the original author's installation and serves as the default Home app. Subsequent updates use the same
signing key and preserve application data.

<p align="center">
  <img src="docs/home.jpg" width="900" alt="Original Luma promotional image showing favorite apps, scenes, and a house view">
</p>

The image above comes from the original project. It shows its promotional demo with House Control TV;
it is not a screenshot of our installation. Content, devices, and music in other images under `docs/`
may also come from that demo.

## What this fork adds

| Change | Behavior |
| --- | --- |
| Continuous app reordering | Hold OK → **Move**. Arrow keys change the position; OK saves and Back cancels. Starting from Home opens the full app grid so apps can move beyond the six favorites. |
| Up Next management | **Settings → Up Next** explains the data sources and lets you reset what Luma displays. Later activity may appear again. |
| TV inputs | Groups tuners with the same provider, service, and label. Preserves AV/HDMI names and opens the tuner as live TV. |
| Netflix | Uses the installed app's banner, falling back to its icon, instead of drawing an N with a generic font. |
| Now Playing access | Checks whether the permission screen exists. If unavailable, displays an explanation and the corresponding ADB command. |
| Builds and tests | Supports custom package IDs and labels for test installations. The QA suite produces a separate APK from the production build. |
| Xiaomi recovery | Includes the script, guide, and saved states needed to restore the reference TV's original configuration. |

Details and completed checks are recorded in the [fix report](docs/fixes-2026-11-03.md).

## Status and limitations

The tested reference device is a **Xiaomi P1E / MiTV-MSSP3 running Android 9**. The manifest supports
Android 8.0 and later, but this does not establish compatibility with every TV.

- **The physical Input button remains unresolved on this Xiaomi.** The firmware intercepts the key and tries
  to open a panel inside Android TV Home, which is disabled to keep Luma as the Home app. Enabling it forces
  Google's Home app back into use. Luma's handler works when it receives the event; that test does not validate
  the physical remote's event path. The **Inputs** section remains available.
- **Now Playing requires notification access.** This TV does not provide the screen to grant it. Permission
  remains pending, and music playback with that access enabled has not been tested. Displaying access
  instructions does not grant permission.
- **Up Next depends on the apps.** It reads Android TV's list and fills gaps with recent apps. Resetting it
  hides earlier activity in Luma; it does not delete history from Netflix, HBO Max, or other accounts.
- **House Control TV is optional.** The integration comes from the original project and requires House Control
  to trust Luma's signing certificate. It has not been validated with our key.

Inherited features include favorites, the Apps tab, weather, input and control widgets, animated backgrounds,
and appearance changes based on the time of day. Weather uses an automatically detected city or a location
selected in **Settings → Weather location**.

## Build and install

To update **the TV already configured with our signing key**, follow
[Our Luma build](docs/OWN_FORK.md). That guide includes the local SDK, JDK, and ADB paths, the port used for
this TV, and the public certificate identity.

To set up another environment, you need:

- JDK 17 and Python 3.
- Android SDK with `build-tools;36.0.0` and `platforms;android-34`.
- [Android Platform Tools / ADB](https://developer.android.com/tools/releases/platform-tools).
- A TV with debugging enabled and the computer's connection authorized.

Clone this fork:

```sh
git clone https://github.com/Lucastrike/luma-tv.git
cd luma-tv
```

Before updating an existing installation, restore its signing key to `.signing/local.keystore` or set its
location with `LUMA_KEYSTORE`. In a fresh clone, the first build generates a new key if none is found;
that new identity cannot update our existing installation.

Set the tool paths and build:

```sh
export ANDROID_SDK_ROOT=/path/to/android-sdk
export LUMA_JDK=/path/to/jdk-17
bash build.sh
```

The production output is `dist/luma-tv.apk`. The build does not use Gradle: it compiles Java and native views
with `aapt2`, `javac`, and `d8`, then signs and verifies the APK with `apksigner`.

With ADB on your `PATH`, connect and install using your TV's address:

```sh
adb connect TV_IP:5555
adb -s TV_IP:5555 install --no-incremental -r dist/luma-tv.apk
```

`-r` preserves data when the package ID and signature match. The original author's version and our fork share
a package ID but have different signatures: switching between them requires an initial migration rather than
an in-place update. The procedure we completed is documented in
[our installation guide](docs/OWN_FORK.md#sustitución-inicial).

Keys, private backups, local tools, APKs, and TV logs are excluded from Git through `.signing/`, `.local/`,
`build/`, and `dist/`. Keep a private backup of the signing key: future updates require it.

## Home app and recovery

The inherited script can inspect the installation and manage the Home app when ADB is on your `PATH`:

```sh
./luma.sh -s TV_IP:5555 status
./luma.sh -s TV_IP:5555 home
```

`home` tries to select Luma and disables recognized system launchers if the firmware continues to prioritize
them. As the Input issue demonstrates, those packages may contain other TV functions. The state needed to
undo these changes is saved per device under `.local/`.

The generic `./luma.sh -s TV_IP:5555 restore` command reverses the script's own changes. For the
**reference Xiaomi P1E**, use the [dedicated guide and script](recovery/xiaomi-mitv-mssp3/README.md)
to restore the previous installation and settings:

```sh
python3 recovery/xiaomi-mitv-mssp3/restaurar-xiaomi.py --dry-run
```

The `--dry-run` mode displays the plan without contacting the TV. An actual run enables the original Home app
and PatchWall, restores the saved package and animation states, and removes Luma after verifying them.
This backup covers configuration; it cannot recover original data or files that have been deleted.

## Usage and customization

- **Favorites:** hold OK on an app to open it, move it, send it to the beginning, show or hide it on Home,
  or view its information.
- **Widgets:** **Settings → Home widgets** enables, disables, and reorders sections. Hold OK to start moving
  a widget, then use the up/down arrows to reposition it.
- **Up Next:** **Settings → Up Next → Reset Up Next** resets its local presentation.
- **Appearance:** configure backgrounds, the clock, name, motion, and sounds in **Settings**.
- **Weather:** **Weather location** sets a city; **Temperature** selects Celsius or Fahrenheit.
- **In-app attribution:** inherited credits and links are in
  [`res/raw/about.txt`](res/raw/about.txt) and [`res/raw/support.txt`](res/raw/support.txt). Support recipients
  shown in the interface belong to the original author; those donations do not go to this fork.

The local recent-app log only records apps opened by Luma. With usage access, it can also query activity
reported by Android. The inherited `./luma.sh widgets` command grants several permissions together—TV listings,
notifications, and usage access—and is not needed to build or update the app.

## Tests

HDMI-CEC device recognition, without a TV:

```sh
mkdir -p build/test
"$LUMA_JDK/bin/javac" -d build/test src/dev/luma/tv/Devices.java tools/DevicesTest.java
"$LUMA_JDK/bin/java" -cp build/test dev.luma.tv.DevicesTest
```

Recovery checks, with simulated ADB:

```sh
python3 -m unittest discover -s recovery/xiaomi-mitv-mssp3 -p 'test_*.py'
```

Regression tests on the connected Xiaomi, with the tools configured:

```sh
LUMA_DEVICE=TV_IP:5555 bash tools/test-tv.sh
```

The suite builds `dev.luma.tv.qa` as `dist/luma-tv-qa.apk`, grants only TV listing read access to that copy, and
checks eight UI cases. It then removes QA and restores the previous Home app. It does not change production
preferences or overwrite `dist/luma-tv.apk`. `LUMA_ADB` selects the executable and `LUMA_ADB_PORT` selects the
server port, which defaults to 5038 for this environment. The suite expects the notification access screen
to be unavailable, an input named HDMI 1, and at least eight installed apps.

The Input case dispatches the event inside the activity. Input intents are checked for resolution; this does
not establish that an HDMI signal or TV tuning works. These limitations and the results are described in
[the verification report](docs/fixes-2026-11-03.md#verificación).

`LUMA_PACKAGE`, `LUMA_LABEL`, and `LUMA_APK` support other test builds with their own identities and output
paths. The normal installation uses the defaults: `dev.luma.tv` and `dist/luma-tv.apk`.

## Privacy

Luma does not include analytics, its own account system, or an accessibility service. Apps, favorites, the
name, and settings are stored on the TV. Up Next reads data published by apps on Android TV and loads artwork
from their references. Resetting it saves a local cutoff date and clears the local recent-app log.

Weather queries [Open-Meteo](https://open-meteo.com); automatic location uses GeoJS or ipwho.is based on the
TV's network connection. Setting a city avoids that automatic lookup. Day/night calculations use the clock
and time zone. House Control, when installed, uses a local connection and Binder.

Now Playing declares a notification listener to access media sessions. Its implementation does not process
or store notifications, although the permission granted by Android has a broader scope. Building or updating
the APK does not enable it.

## Origin, assets, and license

The original project is [0xProbabillity/luma-tv](https://github.com/0xProbabillity/luma-tv), created by
[@0xProbabillity](https://x.com/0xProbabillity). Its [MIT license](LICENSE) and asset attribution are preserved.
Luma is not affiliated with Google, Apple, Netflix, or TV manufacturers.

App names and assets belong to their respective owners. Netflix uses assets from its installed app;
some other icons retain drawn representations from the original project.

- **Fjord / Dusk:** an image created for the original project and split into layers for the depth effect.
- **Milky Way, Calm Sea, and Dawn:** views from the Poly Haven CC0 panoramas
  [Rogland Clear Night](https://polyhaven.com/a/rogland_clear_night),
  [Kloppenheim 06](https://polyhaven.com/a/kloppenheim_06_puresky), and
  [Kiara 1 Dawn](https://polyhaven.com/a/kiara_1_dawn), processed by
  [`artwork/reproject.py`](artwork/reproject.py).
- **Silver / Silk:** backgrounds rendered by the scripts under [`artwork/`](artwork).
- **Sounds:** synthesized by [`tools/generate_sounds.py`](tools/generate_sounds.py).
- **Original author support icons:** [Web3 Icons](https://github.com/0xa3k5/web3icons), licensed under MIT.
