#!/usr/bin/env bash
# Scratch: records emulator-5558's state and pulls the installed watch app and face APKs and the stored feed into <dir>.
set -uo pipefail
D="$1"; mkdir -p "$D"
w() { adb -s emulator-5558 "$@"; }
{
echo "$2 $(date)"
echo "auto_time: $(w shell settings get global auto_time)"
echo "auto_time_zone: $(w shell settings get global auto_time_zone)"
echo "tz: $(w shell getprop persist.sys.timezone)"
echo "font_scale: $(w shell settings get system font_scale)"
echo "a11y services: $(w shell settings get secure enabled_accessibility_services)"
echo "a11y enabled: $(w shell settings get secure accessibility_enabled)"
echo "locales: $(w shell settings get system system_locales) / $(w shell getprop persist.sys.locale)"
echo "time_12_24: $(w shell settings get system time_12_24)"
echo "battery:"; w shell dumpsys battery | grep -E 'AC powered|USB powered|status|level' | head -5
w shell pm list packages | grep world.taqwa
} > "$D/state.txt"
for p in world.taqwa.app world.taqwa.app.watchfacepush.taqwa; do
  path=$(w shell pm path $p | head -1 | sed 's/^package://' | tr -d '\r')
  [ -n "$path" ] && w pull "$path" "$D/$p-base.apk" > /dev/null
done
w shell run-as world.taqwa.app cat files/feed.json > "$D/feed.json"
w exec-out screencap -p > "$D/screen.png"
shasum -a 256 "$D"/*.apk "$D/feed.json"
cat "$D/state.txt"
