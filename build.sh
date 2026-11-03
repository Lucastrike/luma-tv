#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
SDK="${ANDROID_SDK_ROOT:-/opt/homebrew/share/android-commandlinetools}"
JDK="${LUMA_JDK:-/opt/homebrew/opt/openjdk@17}"
BT="$SDK/build-tools/36.0.0"
ANDROID_JAR="$SDK/platforms/android-34/android.jar"
export JAVA_HOME="$JDK"
export PATH="$JDK/bin:$PATH"
rm -rf build/classes build/dex build/resources.zip build/resources.apk build/unsigned.apk build/overlay build/overlay.zip
mkdir -p build/classes build/dex dist
# The Home greeting: LUMA_GREETING, else the first line of .local/greeting.txt, else "Hello".
GREETING="${LUMA_GREETING:-$(head -n 1 .local/greeting.txt 2>/dev/null || true)}"
GREETING="${GREETING:-Hello}"
LUMA_PACKAGE="${LUMA_PACKAGE:-dev.luma.tv}"
LUMA_LABEL="${LUMA_LABEL:-Luma TV}"
APK="${LUMA_APK:-dist/luma-tv.apk}"
[[ "$LUMA_PACKAGE" =~ ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$ ]] || { echo 'Invalid LUMA_PACKAGE' >&2; exit 1; }
python3 - "$LUMA_PACKAGE" "$LUMA_LABEL" "${LUMA_TEST:-0}" <<'PY'
import sys, xml.etree.ElementTree as E
ns = 'http://schemas.android.com/apk/res/android'
E.register_namespace('android', ns)
tree = E.parse('AndroidManifest.xml')
root = tree.getroot()
root.set('package', sys.argv[1])
app = root.find('application')
app.set('{'+ns+'}label', sys.argv[2])
for component in app:
    name = component.get('{'+ns+'}name', '')
    if name.startswith('.'):
        component.set('{'+ns+'}name', 'dev.luma.tv'+name)
if sys.argv[3] == '1':
    E.SubElement(root, 'instrumentation', {'{'+ns+'}name': 'dev.luma.tv.LumaRegression', '{'+ns+'}targetPackage': sys.argv[1]})
tree.write('build/AndroidManifest.xml', encoding='utf-8')
PY
mkdir -p build/overlay/values
python3 - "$GREETING" > build/overlay/values/greeting.xml <<'PY'
import sys, html
text = html.escape(sys.argv[1], quote=False)
for raw, escaped in (("\\", "\\\\"), ("'", "\\'"), ('"', '\\"'), ("@", "\\@"), ("?", "\\?")):
    text = text.replace(raw, escaped)
print('<resources><string name="greeting">' + text + '</string></resources>')
PY
"$BT/aapt2" compile --dir res -o build/resources.zip
"$BT/aapt2" compile --dir build/overlay -o build/overlay.zip
"$BT/aapt2" link -o build/resources.apk -I "$ANDROID_JAR" --manifest build/AndroidManifest.xml --custom-package dev.luma.tv --java build/generated --auto-add-overlay build/resources.zip -R build/overlay.zip
find src build/generated -name '*.java' > build/sources.txt
if [ "${LUMA_TEST:-0}" = 1 ]; then echo tools/LumaRegression.java >> build/sources.txt; fi
javac -encoding UTF-8 --release 8 -classpath "$ANDROID_JAR" -d build/classes @build/sources.txt
find build/classes -name '*.class' > build/classes.txt
"$BT/d8" --lib "$ANDROID_JAR" --min-api 26 --output build/dex @build/classes.txt
cp build/resources.apk build/unsigned.apk
(cd build/dex && zip -q -u ../unsigned.apk classes.dex)
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
# LUMA_KEYSTORE picks another signing key, for example a release key: LUMA_KEYSTORE=.signing/release.keystore
KEYSTORE="${LUMA_KEYSTORE:-.signing/local.keystore}"
if [ ! -f .signing/local.keystore ] && [ "$KEYSTORE" = .signing/local.keystore ]; then
    mkdir -p .signing
    keytool -genkeypair -keystore .signing/local.keystore -storepass android -keypass android -alias luma-local -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Luma TV, O=Local build'
fi
mkdir -p "$(dirname "$APK")"
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --ks-key-alias luma-local --out "$APK" build/aligned.apk
"$BT/apksigner" verify "$APK"
ls -lh "$APK"
