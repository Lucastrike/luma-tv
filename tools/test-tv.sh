#!/bin/bash
# Runs real-view regressions in an isolated package and restores the previous Home afterwards.
set -euo pipefail
cd "$(dirname "$0")/.."
ADB="${LUMA_ADB:-$PWD/.local/android-tools/platform-tools/adb}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$PWD/.local/adb-state}"
PORT="${LUMA_ADB_PORT:-5038}"
DEVICE="${LUMA_DEVICE:?Set LUMA_DEVICE to the TV serial or IP:port}"
tv() { "$ADB" -P "$PORT" -s "$DEVICE" "$@"; }
previous="$(tv shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | tail -n 1)"
[[ "$previous" =~ ^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$ && "$previous" != android/* ]] || { echo 'Resolve a default Home before testing.' >&2; exit 1; }
cleanup() {
    if [ "${LUMA_KEEP_QA:-0}" != 1 ]; then tv uninstall dev.luma.tv.qa >/dev/null || true; fi
    tv shell cmd package set-home-activity --user 0 "$previous" >/dev/null || true
    tv shell am start -n "$previous" >/dev/null || true
}
trap cleanup EXIT
LUMA_PACKAGE=dev.luma.tv.qa LUMA_LABEL='Luma QA' LUMA_TEST=1 LUMA_APK=dist/luma-tv-qa.apk bash build.sh
tv install --no-incremental -r dist/luma-tv-qa.apk
tv shell pm grant dev.luma.tv.qa android.permission.READ_TV_LISTINGS
# Instrumentation dispatches inside the Activity and does not count as physical user activity.
# Dismiss any dream without changing the TV's timeout or opening content.
tv shell input keyevent 224 21
mkdir -p .local/diagnostics
tv shell am instrument -w dev.luma.tv.qa/dev.luma.tv.LumaRegression | tee .local/diagnostics/regression-after.txt
rg -q 'Luma regression failures: 0' .local/diagnostics/regression-after.txt
