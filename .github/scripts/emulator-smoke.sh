#!/usr/bin/env bash
# Runs on a booted emulator (see ci.yml): the instrumented smoke tests, then
# the real UI: share a link into the app, tap Download, and check that the
# app's queue saved the file in Downloads/YTDL Manager. Screenshots and logcat go to build/emulator.
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

dump_ui() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui.xml > "$OUT/ui.xml"
}

# Prints "tap X Y" for the element with this exact text that sits above the
# bottom navigation bar (so the Download button, not the Download tab; pass
# "tab" as $2 for a tab), "swipe X Y1 Y2" to scroll the screen when it isn't
# visible yet, or nothing.
locate() {
  python3 - "$1" "$OUT/ui.xml" "${2:-button}" <<'PY'
import re, sys
text, path, kind = sys.argv[1], sys.argv[2], sys.argv[3]
xml = open(path, encoding="utf-8", errors="replace").read()
nodes = []
for m in re.finditer(r'<node [^>]*>', xml):
    n = m.group(0)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    t = re.search(r' text="([^"]*)"', n)
    if b:
        nodes.append((t.group(1) if t else "", 'scrollable="true"' in n, tuple(map(int, b.groups()))))
nav_top = min([y1 for t, _, (x1, y1, x2, y2) in nodes if t == "History"], default=10**6)
hits = [(x1, y1, x2, y2) for t, _, (x1, y1, x2, y2) in nodes
        if t == text and (kind == "tab" or y2 < nav_top - 8)]
if hits:
    x1, y1, x2, y2 = min(hits, key=lambda b: b[1])
    print("tap", (x1 + x2) // 2, (y1 + y2) // 2)
else:
    areas = [b for t, s, b in nodes if s]
    if areas:
        x1, y1, x2, y2 = max(areas, key=lambda b: (b[3] - b[1]) * (b[2] - b[0]))
        print("swipe", (x1 + x2) // 2, y1 + (y2 - y1) * 4 // 5, y1 + (y2 - y1) // 5)
PY
}

# Taps the button with this text, scrolling to it and waiting up to $2
# seconds. $3 is "button" (default) or "tab".
tap_button() {
  local deadline=$((SECONDS + ${2:-60})) action
  while [ $SECONDS -lt $deadline ]; do
    dump_ui
    read -r -a action <<<"$(locate "$1" "${3:-button}")"
    case "${action[0]:-}" in
      tap) adb shell input tap "${action[1]}" "${action[2]}"; return 0 ;;
      swipe) adb shell input swipe "${action[1]}" "${action[2]}" "${action[1]}" "${action[3]}" 300 ;;
    esac
    sleep 3
  done
  echo "::error::No '$1' button found on screen"
  return 1
}

saved_files() {
  adb shell content query --uri content://media/external/downloads \
    --projection _display_name:relative_path:_size 2>&1 | grep "relative_path=Download/YTDL Manager/"
}

ABI=$(adb shell getprop ro.product.cpu.abi | tr -d '\r')
adb install -r "app/build/outputs/apk/debug/app-${ABI}-debug.apk" || status=1
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
adb logcat -c || true

adb shell am start -W -n "$PKG/$ACTIVITY"
sleep 20 # first launch unpacks Python and FFmpeg and updates yt-dlp
adb exec-out screencap -p > "$OUT/1-launch.png"

# Sharing a link opens the app and fetches its title.
adb shell "am start -W -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT 'Sample clip $SAMPLE' -n $PKG/$ACTIVITY"
if tap_button "Download" 180; then
  adb exec-out screencap -p > "$OUT/2-download-tapped.png"
  for _ in $(seq 1 60); do
    saved_files >/dev/null && break
    sleep 4
  done
  adb exec-out screencap -p > "$OUT/3-after-download.png"
  tap_button "History" 10 tab && sleep 3 && adb exec-out screencap -p > "$OUT/4-history.png"
else
  adb exec-out screencap -p > "$OUT/2-no-download-button.png"
  status=1
fi

if saved_files | tee "$OUT/downloads.txt"; then
  echo "File saved to Downloads/YTDL Manager through the app's own queue"
else
  echo "::error::No file in Downloads/YTDL Manager after tapping Download (see screenshots and logcat-ui.txt)"
  status=1
fi

adb logcat -d > "$OUT/logcat-ui.txt" || true
if ! adb shell pidof "$PKG" >/dev/null; then
  echo "::error::The app is not running (crashed?)"
  status=1
fi
exit $status
