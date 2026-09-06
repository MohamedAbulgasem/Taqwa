#!/bin/bash
# Kotlin 2.4.10's platform.CoreLocation klib links against _LocationEssentials, a framework that
# only exists in the iOS 26 SDK. xcode-select points at Xcode 16.0, whose simulator SDK does not
# have it, so linkDebugTestIosSimulatorArm64 fails with "framework '_LocationEssentials' not
# found" as soon as any linked code touches CoreLocation (AppContainer does, from Task 13).
# Same reason and same workaround as scripts/ios-build.sh.
# Permanent fix (needs your password):
#   sudo xcode-select -s "/Applications/Xcode 26.app/Contents/Developer"
set -e
export DEVELOPER_DIR="${DEVELOPER_DIR:-/Applications/Xcode 26.app/Contents/Developer}"
cd "$(dirname "$0")/.."
exec ./gradlew "${@:-:shared:allTests}"
