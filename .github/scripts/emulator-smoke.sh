#!/usr/bin/env bash
# Runs on a booted emulator (see ci.yml): the instrumented smoke tests, then
# the real UI: share a link into the app, tap Download, check the file landed
# in Downloads/YTDL Manager. Screenshots and logcat go to build/emulator.
set -uo pipefail
OUT=build/emulator
PKG=io.github.azeezhamzat.ytdlmanager.debug
ACTIVITY=io.github.azeezhamzat.ytdlmanager.ui.MainActivity
SAMPLE="https://download.samplelib.com/mp4/sample-5s.mp4"
mkdir -p "$OUT"
status=0

adb logcat -c || true
./gradlew connectedDebugAndroidTest || status=$?
adb logcat -d > "$OUT/logcat-tests.txt" || true

# Tap the element with this exact text that is highest on screen (the
# Download button sits above the "Download" tab label).
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml > "$OUT/ui.xml"
  local xy
  xy=$(python3 - "$1" "$OUT/ui.xml" <<'PY'
import re, sys
text, path = sys.argv[1], sys.argv[2]
xml = open(path, encoding="utf-8", errors="replace").read()
hits = []
for m in re.finditer(r'<node [^>]*>', xml):
    node = m.group(0)
    if f'text="{text}"' in node:
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        if b:
            x1, y1, x2, y2 = map(int, b.groups())
            hits.append(((y1 + y2) // 2, (x1 + x2) // 2))
if hits:
    y, x = min(hits)
    print(x, y)
PY
)
  if [ -z "$xy" ]; then echo "no element with text '$1'"; return 1; fi
  adb shell input tap $xy
}

ABI=$(adb shell getprop ro.product.cpu.abi | tr -d '\r')
adb install -r "app/build/outputs/apk/debug/app-${ABI}-debug.apk" || status=1
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
adb logcat -c || true

adb shell am start -W -n "$PKG/$ACTIVITY"
sleep 25 # first launch unpacks Python and FFmpeg and updates yt-dlp
adb exec-out screencap -p > "$OUT/1-launch.png"

adb shell "am start -W -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT 'Sample clip $SAMPLE' -n $PKG/$ACTIVITY"
sleep 30
adb exec-out screencap -p > "$OUT/2-shared-link.png"

if tap_text "Download"; then
  sleep 60
  adb exec-out screencap -p > "$OUT/3-after-download.png"
  tap_text "History" && sleep 3 && adb exec-out screencap -p > "$OUT/4-history.png"
else
  status=1
fi

adb shell content query --uri content://media/external/downloads \
  --projection _display_name:relative_path:_size > "$OUT/downloads.txt" 2>&1 || true
cat "$OUT/downloads.txt"
if grep -q "YTDL Manager" "$OUT/downloads.txt"; then
  echo "File saved to Downloads/YTDL Manager"
else
  echo "::warning::No file found in Downloads/YTDL Manager (see downloads.txt and logcat)"
  status=1
fi

adb logcat -d > "$OUT/logcat-ui.txt" || true
if ! adb shell pidof "$PKG" >/dev/null; then
  echo "::error::The app is not running (crashed?)"
  status=1
fi
exit $status
