#!/usr/bin/env bash
# Builds the App Store archive of the iOS app and uploads it to App Store Connect:
#
#   scripts/ios-release.sh            archive, then upload
#   scripts/ios-release.sh archive    only the archive → build/ios-archive/Taqwa-<version>-<build>.xcarchive
#   scripts/ios-release.sh upload     upload the archive the last `archive` made
#
# Signing is automatic under the team named in iosApp/Configuration/Config.xcconfig (TEAM_ID),
# through the Apple account signed into Xcode 26 › Settings › Accounts — no credentials pass
# through this script. -allowProvisioningUpdates lets Xcode register world.taqwa.ios, its
# widget and the app group under that team and create the certificates it needs the first time.
# The script refuses an empty TEAM_ID and LOOPDL LIMITED's team (Mohamed's employer, never
# Taqwa's publisher), so nothing can be registered under the wrong team by accident.
#
# Run scripts/test.sh and scripts/ios-build.sh before this, and scripts/bump-version.sh once per
# release; the iOS build number is the Android versionCode of the same commit.
set -euo pipefail
cd "$(dirname "$0")/.."
export DEVELOPER_DIR="/Applications/Xcode 26.app/Contents/Developer"

XCCONFIG=iosApp/Configuration/Config.xcconfig
TEAM_ID="$(sed -n 's/^TEAM_ID=//p' "$XCCONFIG" | tr -d '[:space:]')"
case "$TEAM_ID" in
    "") echo "TEAM_ID is empty in $XCCONFIG — put Mohamed's individual Team ID there first." >&2; exit 1 ;;
    5S5P2Q72MV) echo "TEAM_ID in $XCCONFIG is LOOPDL LIMITED's team. Taqwa is never published under it." >&2; exit 1 ;;
esac

PLIST=iosApp/iosApp/Info.plist
VERSION="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$PLIST")"
BUILD="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleVersion' "$PLIST")"
OUT=build/ios-archive
ARCHIVE="$OUT/Taqwa-$VERSION-$BUILD.xcarchive"
EXPORT="$OUT/export-$VERSION-$BUILD"
APPEX_LIMIT_MB=15

archive() {
    mkdir -p "$OUT"
    rm -rf "$ARCHIVE"
    xcodebuild archive -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release \
        -destination generic/platform=iOS -derivedDataPath build/ios-dd \
        -archivePath "$ARCHIVE" -allowProvisioningUpdates
    report
}

# What the archive actually is — identifiers, versions, team, app group and the widget's size —
# read back from the built bundles, so an upload never rests on what the project was meant to do.
report() {
    local app="$ARCHIVE/Products/Applications/Taqwa.app"
    local appex="$app/PlugIns/TaqwaWidget.appex"
    if [ ! -d "$appex" ]; then
        echo "ERROR: $ARCHIVE has no Taqwa.app with a TaqwaWidget.appex inside." >&2
        exit 1
    fi
    echo
    echo "Archive: $ARCHIVE"
    local bundle
    for bundle in "$app" "$appex"; do
        printf '  %-26s %s (%s)\n' \
            "$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$bundle/Info.plist")" \
            "$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$bundle/Info.plist")" \
            "$(/usr/libexec/PlistBuddy -c 'Print :CFBundleVersion' "$bundle/Info.plist")"
    done
    echo "  team:      $(codesign -dvv "$app" 2>&1 | sed -n 's/^TeamIdentifier=//p') — $(security cms -D -i "$app/embedded.mobileprovision" 2>/dev/null | plutil -extract TeamName raw -o - - 2>/dev/null || echo '?')"
    echo "  signed by: $(codesign -dvv "$app" 2>&1 | sed -n 's/^Authority=//p' | head -1)"
    echo "  app group: $(codesign -d --entitlements :- "$app" 2>/dev/null | grep -A 2 application-groups | sed -n 's/.*<string>\(.*\)<\/string>.*/\1/p' | tr '\n' ' ')"
    # WidgetKit kills extensions above roughly 30 MB of memory; the extension links only the
    # Compose-free :widgetcore framework to stay far below that (see scripts/ios-build.sh).
    local size_mb=$(( $(du -sk "$appex" | cut -f1) / 1024 ))
    if [ "$size_mb" -gt "$APPEX_LIMIT_MB" ]; then
        echo "ERROR: TaqwaWidget.appex is ${size_mb} MB (limit ${APPEX_LIMIT_MB} MB). It is linking more than :widgetcore." >&2
        exit 1
    fi
    echo "  widget:    ${size_mb} MB (limit ${APPEX_LIMIT_MB} MB)"
}

upload() {
    if [ ! -d "$ARCHIVE" ]; then
        echo "No archive at $ARCHIVE — run 'scripts/ios-release.sh archive' first." >&2
        exit 1
    fi
    cat > "$OUT/ExportOptions.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>method</key><string>app-store-connect</string>
    <key>destination</key><string>upload</string>
    <key>signingStyle</key><string>automatic</string>
    <key>teamID</key><string>$TEAM_ID</string>
    <key>uploadSymbols</key><true/>
    <key>manageAppVersionAndBuildNumber</key><false/>
</dict>
</plist>
PLIST
    rm -rf "$EXPORT"
    xcodebuild -exportArchive -archivePath "$ARCHIVE" -exportOptionsPlist "$OUT/ExportOptions.plist" \
        -exportPath "$EXPORT" -allowProvisioningUpdates
    echo
    echo "Uploaded Taqwa $VERSION ($BUILD) to App Store Connect. It shows under TestFlight once Apple has processed it (10–30 minutes)."
}

case "${1:-all}" in
    archive) archive ;;
    upload) upload ;;
    all) archive; upload ;;
    *) echo "usage: scripts/ios-release.sh [archive|upload]" >&2; exit 2 ;;
esac
