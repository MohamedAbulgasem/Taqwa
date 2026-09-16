#!/bin/bash
# Store screenshots on an Android phone running the DEBUG build (the harness lives only there),
# driven by the debug harness (screen, theme, mode, translation by name) so every language runs
# the same flow. The phone must have Alafasy surah 2 on it and the city set beforehand.
#
#   tools/store/capture-android.sh <serial> en ar fr tr id ur bn
#
# Output: build/store-shots/android/<lang>/<n>-<name>.png. Restores Mohamed's S23 state at the end
# (dark theme, English translation, Mushaf mode, no per-app locale); edit that line for another phone.
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
D=$1; shift; PKG=world.taqwa.app; OUT=$(cd "$(dirname "$0")/../.." && pwd)/build/store-shots/android
h() { adb -s $D shell am broadcast -a world.taqwa.app.debug.RECITATION -n $PKG/world.taqwa.app.debug.RecitationHarnessReceiver "$@" >/dev/null 2>&1; sleep ${W:-2.5}; }
shot() { sleep ${2:-1}; adb -s $D exec-out screencap -p > "$L_DIR/$1.png"; echo "  shot $1"; }
ayah() { adb -s $D shell am start -n $PKG/.MainActivity -a world.taqwa.app.OPEN_AYAH -d taqwa://ayah/$1/$2 --ei open_surah $1 --ei open_ayah $2 -f 0x34000000 >/dev/null 2>&1; sleep 4; }
tr_for() { case $1 in en) echo en.sahih;; ar) echo ar.muyassar;; fr) echo fr.hamidullah;; tr) echo tr.diyanet;; id) echo id.indonesian;; ur) echo ur.junagarhi;; bn) echo bn.bengali;; esac; }
for L in "$@"; do
  L_DIR=$OUT/$L; rm -rf "$L_DIR"; mkdir -p "$L_DIR"; echo "$L"
  adb -s $D shell cmd locale set-app-locales $PKG --user 0 --locales "$L" >/dev/null
  adb -s $D shell am force-stop $PKG; sleep 1; adb -s $D shell am start -n $PKG/.MainActivity >/dev/null; sleep 6
  h --es cmd theme --es name light; h --es cmd mode --es name translation; h --es cmd translation --es id "$(tr_for $L)"
  h --es cmd screen --es name prayer; shot 1-prayer
  h --es cmd screen --es name notifications; shot 2-notifications
  ayah 2 255; shot 3-reader
  h --es cmd mode --es name mushaf; ayah 2 255; shot 4-mushaf
  h --es cmd mode --es name translation; ayah 2 255; W=6 h --es cmd load --ei surah 2 --ei ayah 255 --es reciter ar.alafasy; shot 5-recitation
  h --es cmd stop
  h --es cmd screen --es name qibla; shot 6-qibla 3
  h --es cmd screen --es name tasbeeh; shot 7-tasbeeh 2
  h --es cmd theme --es name dark; h --es cmd screen --es name prayer; shot 8-dark 2
  echo "  $(ls $L_DIR | wc -l | tr -d ' ') files"
done
# Mohamed's own state: dark theme, his English translation, the reader in Mushaf mode, no per-app locale.
h --es cmd theme --es name dark; h --es cmd translation --es id en.sahih; h --es cmd mode --es name mushaf
adb -s $D shell cmd locale set-app-locales $PKG --user 0 --locales "" >/dev/null 2>&1; echo "s23 restored"
