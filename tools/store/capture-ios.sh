#!/bin/bash
# Store screenshots on a booted simulator running the DEBUG build, one folder per language, driven
# entirely by the debug harness URLs (no taps). Sideload Alafasy surah 2 into the app's Application
# Support/quran/audio/ar.alafasy first, set the location (simctl location set 32.8872,13.1913 is
# Tripoli, Libya) and grant notifications once by hand.
#
#   tools/store/capture-ios.sh <udid> en ar fr tr id ur bn
#
# Output: build/store-shots/ios/<lang>/<n>-<name>.png. The simulator has no compass, so 6-qibla is
# dropped by assemble.sh.
U=$1; shift; APP=world.taqwa.app; OUT=$(cd "$(dirname "$0")/../.." && pwd)/build/store-shots/ios
url() { xcrun simctl openurl $U "$1" >/dev/null 2>&1; sleep ${2:-2.5}; }
shot() { sleep ${2:-1}; xcrun simctl io $U screenshot "$D/$1.png" >/dev/null 2>&1; echo "  shot $1"; }
tr_for() { case $1 in en) echo en.sahih;; ar) echo ar.muyassar;; fr) echo fr.hamidullah;; tr) echo tr.diyanet;; id) echo id.indonesian;; ur) echo ur.junagarhi;; bn) echo bn.bengali;; esac; }
locale_for() { case $1 in en) echo en_GB;; ar) echo ar_LY;; fr) echo fr_FR;; tr) echo tr_TR;; id) echo id_ID;; ur) echo ur_PK;; bn) echo bn_BD;; esac; }
for L in "$@"; do
  D=$OUT/$L; mkdir -p "$D"; echo "$L"
  xcrun simctl terminate $U $APP 2>/dev/null; sleep 1
  xcrun simctl launch $U $APP -AppleLanguages "($L)" -AppleLocale "$(locale_for $L)" >/dev/null; sleep 7
  url "taqwa://recite/theme?name=light" 2; url "taqwa://recite/mode?name=translation" 1; url "taqwa://recite/translation?id=$(tr_for $L)" 1
  url "taqwa://recite/screen?name=prayer" 2; shot 1-prayer
  url "taqwa://recite/screen?name=notifications" 3; shot 2-notifications
  url "taqwa://ayah/2/255" 4; shot 3-reader
  url "taqwa://recite/mode?name=mushaf" 1; url "taqwa://ayah/2/255" 4; shot 4-mushaf
  url "taqwa://recite/mode?name=translation" 1; url "taqwa://ayah/2/255" 3; url "taqwa://recite/load?surah=2&ayah=255&reciter=ar.alafasy" 5; shot 5-recitation
  url "taqwa://recite/stop" 1
  url "taqwa://recite/screen?name=qibla" 4; shot 6-qibla
  url "taqwa://recite/screen?name=tasbeeh" 3; shot 7-tasbeeh
  url "taqwa://recite/theme?name=dark" 2; url "taqwa://recite/screen?name=prayer" 3; shot 8-dark
  url "taqwa://recite/theme?name=system" 1
  echo "  $(ls $D | wc -l | tr -d ' ') files"
done
