#!/bin/bash
# xcode-select points at Xcode 16.0, which cannot drive simulators on macOS 26.
# Xcode 26.2 is installed; this forces it without needing sudo.
# Permanent fix (needs your password):
#   sudo xcode-select -s "/Applications/Xcode 26.app/Contents/Developer"
# An Xcode already chosen through DEVELOPER_DIR wins, as in scripts/test.sh; CI sets it.
set -e
export DEVELOPER_DIR="${DEVELOPER_DIR:-/Applications/Xcode 26.app/Contents/Developer}"
cd "$(dirname "$0")/.."
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator \
  -destination "${IOS_DEST:-platform=iOS Simulator,name=iPhone 17 Pro}" \
  -configuration Debug -derivedDataPath build/ios-dd "${@:-build}"

# Guard the widget extension's size. WidgetKit kills extensions above roughly 30 MB of memory;
# the extension links only the Compose-free :widgetcore framework precisely to stay far below
# that. A regression here (e.g. the extension linking `shared` again) is invisible to every
# Gradle test, so it is checked at the only place it can be seen: the built .appex.
APPEX="build/ios-dd/Build/Products/Debug-iphonesimulator/Taqwa.app/PlugIns/TaqwaWidget.appex"
APPEX_LIMIT_MB=15
if [ -d "$APPEX" ]; then
  size_mb=$(( $(du -sk "$APPEX" | cut -f1) / 1024 ))
  if [ "$size_mb" -gt "$APPEX_LIMIT_MB" ]; then
    echo "ERROR: TaqwaWidget.appex is ${size_mb} MB (limit ${APPEX_LIMIT_MB} MB). It is linking more than :widgetcore." >&2
    exit 1
  fi
  echo "TaqwaWidget.appex: ${size_mb} MB (limit ${APPEX_LIMIT_MB} MB)"
fi
