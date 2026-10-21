# Sourced by scripts/test.sh, scripts/ios-build.sh and scripts/ios-release.sh: picks the Xcode
# they run with. DEVELOPER_DIR wins when it is set (CI sets it); otherwise it is the Xcode that
# xcode-select chose. Xcode 26 or later is required: Kotlin 2.4.10's platform.CoreLocation klib
# links against _LocationEssentials, a framework only the iOS 26 SDK and later have, so an older
# Xcode fails to link with "framework '_LocationEssentials' not found".
export DEVELOPER_DIR="${DEVELOPER_DIR:-$(xcode-select -p 2>/dev/null || true)}"
xcode_major="$(xcodebuild -version 2>/dev/null | sed -n 's/^Xcode \([0-9][0-9]*\).*/\1/p' || true)"
if [ -z "$xcode_major" ] || [ "$xcode_major" -lt 26 ]; then
  echo "Xcode 26 or later is needed, and '${DEVELOPER_DIR}' is ${xcode_major:+Xcode $xcode_major}${xcode_major:-not a full Xcode}." >&2
  echo "Choose one with: sudo xcode-select -s /Applications/Xcode.app/Contents/Developer (or set DEVELOPER_DIR)." >&2
  exit 1
fi
