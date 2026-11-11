#!/bin/bash
# Builds the Apple Watch app (the TaqwaWatch scheme: the app, its widgets and its UI tests) for a
# watch simulator, Debug, into build/watch-dd. Its arguments go to xcodebuild, as ios-build.sh's do:
#
#   scripts/watch-build.sh                     build
#   scripts/watch-build.sh test                build, then run the UI tests on the simulator
#   scripts/watch-build.sh build-for-testing   what CI runs, with a generic destination
#
# The destination is the SE 3 (40mm) on watchOS 27.0 unless WATCH_DEST names another. `test` boots
# that simulator and installs the app on it: take its lock first (sim-se3, in the execution map),
# and put it back as you found it. CI builds for "generic/platform=watchOS Simulator", which names
# no device or OS, so the image's own watchOS SDK serves.
#
# No -sdk: -destination alone gives each target its own SDK (plan 4's Global Constraints).
# The Xcode comes from scripts/xcode.sh, as for every iOS script.
set -e
cd "$(dirname "$0")/.."
. scripts/xcode.sh
exec xcodebuild -project iosApp/iosApp.xcodeproj -scheme TaqwaWatch \
  -destination "${WATCH_DEST:-platform=watchOS Simulator,name=Apple Watch SE 3 (40mm),OS=27.0}" \
  -configuration Debug -derivedDataPath build/watch-dd "${@:-build}"
