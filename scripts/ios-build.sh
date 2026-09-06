#!/bin/bash
# xcode-select points at Xcode 16.0, which cannot drive simulators on macOS 26.
# Xcode 26.2 is installed; this forces it without needing sudo.
# Permanent fix (needs your password):
#   sudo xcode-select -s "/Applications/Xcode 26.app/Contents/Developer"
set -e
export DEVELOPER_DIR="/Applications/Xcode 26.app/Contents/Developer"
cd "$(dirname "$0")/.."
exec xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator \
  -destination "${IOS_DEST:-platform=iOS Simulator,name=iPhone 17 Pro}" \
  -configuration Debug -derivedDataPath build/ios-dd "${@:-build}"
