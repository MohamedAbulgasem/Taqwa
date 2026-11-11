#!/bin/bash
# Builds the iOS app for the simulator, with its widget extension and the Apple Watch app and its
# widgets inside it, then guards the extensions' sizes and checks the watch bundles.
# The Xcode comes from scripts/xcode.sh: DEVELOPER_DIR when set (CI sets it), else the one
# xcode-select chose, and never one older than Xcode 26.
#
# No -sdk: it would override SDKROOT for every target in the build, the embedded watchOS ones
# included. -destination alone gives each target its own simulator SDK, and the products land in
# build/ios-dd/Build/Products/Debug-iphonesimulator/ (the iPhone's) and Debug-watchsimulator/ (the
# watch's), with the watch app copied into Taqwa.app/Watch/.
set -e
cd "$(dirname "$0")/.."
. scripts/xcode.sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -destination "${IOS_DEST:-platform=iOS Simulator,name=iPhone 17 Pro}" \
  -configuration Debug -derivedDataPath build/ios-dd "${@:-build}"

PRODUCTS="build/ios-dd/Build/Products/Debug-iphonesimulator"

# guard_size <bundle> <limit in MB> <why>: fails when a built extension outgrows its budget.
guard_size() {
  local bundle=$1 limit_mb=$2 why=$3 size_mb
  [ -d "$bundle" ] || return 0
  size_mb=$(( $(du -sk "$bundle" | cut -f1) / 1024 ))
  if [ "$size_mb" -gt "$limit_mb" ]; then
    echo "ERROR: $(basename "$bundle") is ${size_mb} MB (limit ${limit_mb} MB). $why" >&2
    exit 1
  fi
  echo "$(basename "$bundle"): ${size_mb} MB (limit ${limit_mb} MB)"
}

# Guard the widget extension's size. WidgetKit kills extensions above roughly 30 MB of memory;
# the extension links only the Compose-free :widgetcore framework precisely to stay far below
# that. A regression here (e.g. the extension linking `shared` again) is invisible to every
# Gradle test, so it is checked at the only place it can be seen: the built .appex.
guard_size "$PRODUCTS/Taqwa.app/PlugIns/TaqwaWidget.appex" 15 \
  "It is linking more than :widgetcore."
# The watch's widget extension has less room still, and links no Kotlin at all: the Swift port of
# :watchcore is compiled into it (plan 4, the Lead's call 1).
guard_size "$PRODUCTS/Taqwa.app/Watch/TaqwaWatch.app/PlugIns/TaqwaWatchWidgets.appex" 5 \
  "It is carrying more than the watch's Swift core and its views."

# The watch app and its widgets inside the iPhone app: their ids, one version, no location, no
# network, no Kotlin, the app group alone, the seven languages and the sizes. Only after a build:
# any other action (clean, say) leaves no app to check. Then the same check's Release signal and
# its rules for each signature's keys, on a scratch copy of this build with a stub signature (plan 5
# Task 7): only scripts/ios-release.sh meets a real archive's or export's signature, so this keeps
# the rules that judge them tested on every build.
case " ${*:-build} " in
  *" build "* | *" build-for-testing "*)
    scripts/check-watch-bundle.sh "$PRODUCTS/Taqwa.app"
    scripts/test-watch-bundle-signing.sh "$PRODUCTS/Taqwa.app"
    ;;
esac
