#!/usr/bin/env bash
# Prints emulator-5558's state as the locks' rule names it (read-only).
A="$HOME/Library/Android/sdk/platform-tools/adb -s emulator-5558"
s() { $A shell "$@" 2>/dev/null | tr -d '\r'; }
echo "time: $(s date)"
# the APK by its sha1, not by its install path, which changes at every install -r
echo "app: $(s dumpsys package world.taqwa.app | grep -m1 versionCode | xargs)"
echo "app sha1: $(s sha1sum $(s pm path world.taqwa.app | head -1 | sed 's/package://') | cut -c1-8)"
echo "face: $(s dumpsys package world.taqwa.app.watchfacepush.taqwa | grep -m1 versionCode | xargs) sha1=$(s sha1sum $(s pm path world.taqwa.app.watchfacepush.taqwa | head -1 | sed 's/package://') 2>/dev/null | cut -c1-8)"
echo "files: $(s run-as world.taqwa.app ls -a files | xargs)"
echo "shared_prefs: $(s run-as world.taqwa.app ls shared_prefs 2>&1 | xargs)"
echo "feed sha1: $(s run-as world.taqwa.app sha1sum files/feed.json | cut -c1-8) writtenAt=$(s run-as world.taqwa.app cat files/feed.json | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d.get("writtenAt"), d["ui"]["language"])' 2>/dev/null)"
echo "nudge.anchor: $(s run-as world.taqwa.app cat files/nudge.anchor 2>/dev/null)"
echo "auto_time=$(s settings get global auto_time) auto_time_zone=$(s settings get global auto_time_zone) tz=$(s getprop persist.sys.timezone)"
echo "font_scale=$(s settings get system font_scale) display_density=$(s wm density | xargs)"
echo "talkback: services=$(s settings get secure enabled_accessibility_services) enabled=$(s settings get secure accessibility_enabled)"
echo "locale: persist.sys.locale=$(s getprop persist.sys.locale) system_locales=$(s settings get system system_locales) app-locales=$(s cmd locale get-app-locales world.taqwa.app 2>&1 | xargs)"
echo "battery: $(s dumpsys battery | grep -E 'AC powered|level' | xargs)"
echo "rotation: user_rotation=$(s settings get system user_rotation) accel_rotation=$(s settings get system accelerometer_rotation)"
# the app's pending alarms alone: the removal history keeps a line for an alarm a force stop removed
echo "alarms (taqwa): $(s dumpsys alarm | grep -c 'Alarm{.* world\.taqwa\.app}')"
echo "tiles: $(s dumpsys activity service com.google.android.wearable.sysui 2>/dev/null | grep -c TodayTileService)"
echo "READ_PHONE_STATE (talkback): $(s dumpsys package com.google.android.marvin.talkback | grep -m1 'READ_PHONE_STATE' | xargs)"
echo "deviceidle: $(s dumpsys deviceidle get deep 2>/dev/null)"
