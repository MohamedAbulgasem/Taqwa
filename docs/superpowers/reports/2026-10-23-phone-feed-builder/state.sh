#!/usr/bin/env bash
# state.sh <dir>: emulator-5558's settings, packages, APKs and stored feed into <dir> (read only).
set -euo pipefail
D="$1"; mkdir -p "$D"
w() { perl -e 'alarm 30; exec @ARGV' adb -s emulator-5558 "$@"; }
{
echo "== $(date)"
echo "auto_time: $(w shell settings get global auto_time | tr -d '\r')"
echo "auto_time_zone: $(w shell settings get global auto_time_zone | tr -d '\r')"
echo "tz: $(w shell getprop persist.sys.timezone | tr -d '\r')"
echo "font_scale: $(w shell settings get system font_scale | tr -d '\r')"
echo "a11y services: $(w shell settings get secure enabled_accessibility_services | tr -d '\r')"
echo "a11y enabled: $(w shell settings get secure accessibility_enabled | tr -d '\r')"
echo "locales: $(w shell settings get system system_locales | tr -d '\r') / $(w shell getprop persist.sys.locale | tr -d '\r')"
echo "time_12_24: $(w shell settings get system time_12_24 | tr -d '\r')"
echo "battery:"; w shell dumpsys battery | tr -d '\r' | grep -E "AC powered|USB powered|status|level" | sed 's/^/ /'
w shell pm list packages | tr -d '\r' | grep -E "^package:world\.taqwa\.app(\.watchfacepush\.taqwa)?$"
} | tee "$D/state.txt"
for p in world.taqwa.app world.taqwa.app.watchfacepush.taqwa; do
  path=$(w shell pm path $p | tr -d '\r' | head -1 | sed 's/^package://')
  w pull "$path" "$D/$p-base.apk" > /dev/null
done
w shell run-as world.taqwa.app cat files/feed.json > "$D/feed.json"
w shell run-as world.taqwa.app ls -la files/ | tr -d '\r' > "$D/files-ls.txt"
w exec-out screencap -p > "$D/screen.png"
(cd "$D" && shasum -a 256 world.taqwa.app-base.apk world.taqwa.app.watchfacepush.taqwa-base.apk feed.json) | tee -a "$D/state.txt"
