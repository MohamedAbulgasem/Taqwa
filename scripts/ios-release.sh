#!/usr/bin/env bash
# Builds the App Store archive of the iOS app and uploads it to App Store Connect:
#
#   scripts/ios-release.sh                  archive, then export-check, then upload, stopping at the first failure
#   scripts/ios-release.sh archive          only the archive → build/ios-archive/Taqwa-<version>-<build>.xcarchive
#   scripts/ios-release.sh export-check     export the last archive locally and check the IPA; uploads nothing
#   scripts/ios-release.sh upload           upload the archive the last `archive` made
#   scripts/ios-release.sh check <archive>  check an archive's bundles (archive's report runs it)
#
# `check <xcarchive>` exits 1 unless the archive's Taqwa.app holds the Apple Watch app under Watch/
# with its widget extension, every app and app-extension bundle's Info.plist reads the iPhone app's
# version and build (Taqwa.app, its PlugIns/*.appex, Watch/*.app and their PlugIns/*.appex;
# frameworks keep their own and are not read), and scripts/check-watch-bundle.sh --release passes on
# the archive's Taqwa.app (TAQWA_WATCH_BUNDLE_CHECK names another command for the bundle check, for
# scripts/test-release.sh alone). It prints the watch app's and its widgets' sizes. The archive is
# signed with an Apple Development identity (automatic signing, below), so it proves the Release
# bundle (no sample feeds) and the development signature's keys; the App Store signature is applied
# only at export, and `export-check` checks that one: it exports the last archive with method
# app-store-connect and destination export into build/ios-archive/export-check-<version>-<build>/,
# as an upload would sign it, unzips the IPA into a fresh directory there, runs
# check-watch-bundle.sh --release on its Payload/Taqwa.app, and deletes the IPA and its copy, keeping
# the export's logs and DistributionSummary.plist. The Lead runs archive and export-check, each with
# Mohamed's OK in chat (the first archive registers the watch's ids under his team). Both refuse
# (exit 2) a set TAQWA_CODESIGN or TAQWA_WATCH_BUNDLE_CHECK, which only the tests give, and so does
# the bare script (all), before anything is built.
#
# Signing is automatic under the team named in iosApp/Configuration/Config.xcconfig (TEAM_ID),
# through the Apple account signed into Xcode › Settings › Accounts — no credentials pass
# through this script. -allowProvisioningUpdates lets Xcode register world.taqwa.ios, its
# widget and the app group under that team and create the certificates it needs the first time.
# The script refuses an empty TEAM_ID and LOOPDL LIMITED's team (Mohamed's employer, never
# Taqwa's publisher), so nothing can be registered under the wrong team by accident.
#
# Run scripts/test.sh and scripts/ios-build.sh before this, and scripts/bump-version.sh once per
# release; the iOS build number is the Android versionCode of the same commit.
set -euo pipefail
cd "$(dirname "$0")/.."

COMMAND="${1:-all}"
case "$COMMAND" in
    archive | export-check | all)
        for var in TAQWA_CODESIGN TAQWA_WATCH_BUNDLE_CHECK; do
            if [ -n "${!var+set}" ]; then  # set at all, even empty
                echo "$var is set: it gives the bundle check another codesign or check, for scripts/test-release.sh alone; unset it to $COMMAND" >&2
                exit 2
            fi
        done
        ;;
esac
. scripts/xcode.sh

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
    # Spec §5 / ruling R86: refuse to build on a stale or red prayer-time stamp. The Ramadan build
    # (ruling R146) runs as TAQWA_RAMADAN=<Hijri year> scripts/ios-release.sh, e.g. 1448: it then also
    # refuses an A/B unit whose Fajr, end of eating and Maghrib (and its imsak where a table prints one)
    # were not each held out and checked on every day of that Ramadan (rulings R148 and R246), unless
    # the gate has demoted it, or the entry is covered under O4
    # (ruling R185; see scripts/release.sh). Any build refuses an entry
    # the monitor has demoted (a "@demoted" gate line) until it is looked at, and (-Prelease=true)
    # any build is refused while a reach proof is owed (ReachRows.outstanding; see scripts/release.sh).
    if ! ./gradlew -q -p tools/timetables checkStamps -Prelease=true ${TAQWA_RAMADAN:+-Pramadan="$TAQWA_RAMADAN"} ${TAQWA_OFFICIAL:+-Pofficial="$TAQWA_OFFICIAL"}; then
        echo "A prayer-time stamp is stale, red or demoted, its Ramadan is not proven, or a reach proof is owed (see above). " \
            "For a stamp, refresh it first: " \
            "./gradlew -p tools/timetables gate, then ./gradlew -p tools/timetables generateProofStamps." >&2
        exit 1
    fi
    # The second trust audit's G24 (14 Nov 2026): the envelope's promise-only rows are a pure function of the gate's
    # group files, and fall behind when prove appends a year to a group file with units; a release ships only stamps
    # whose envelope rows are current (see scripts/release.sh).
    if ! ./gradlew -q -p tools/timetables checkEnvelopeRows; then
        echo "An envelope file is not what generateEnvelopeRows would write (see above): run ./gradlew -p tools/timetables " \
            "generateEnvelopeRows, then the gate, and commit." >&2
        exit 1
    fi

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
    echo
    check "$ARCHIVE"
}

# check <xcarchive>: the watch app is inside, every bundle is at the iPhone app's version, and the
# bundle check passes on the archive as a release (the header says what each means).
check() {
    local archive="${1%/}"
    local app="$archive/Products/Applications/Taqwa.app"
    local bad=0 bundle want_v want_c v c
    if [ ! -f "$app/Info.plist" ]; then
        echo "ERROR: $archive has no Products/Applications/Taqwa.app." >&2
        return 1
    fi
    if ! compgen -G "$app/Watch/*.app" > /dev/null; then
        echo "ERROR: $(basename "$archive")'s Taqwa.app holds no Apple Watch app under Watch/." >&2
        return 1
    fi
    if ! compgen -G "$app/Watch/*.app/PlugIns/*.appex" > /dev/null; then
        echo "ERROR: the Apple Watch app in $(basename "$archive") holds no widget extension (Watch/*.app/PlugIns/*.appex)." >&2
        bad=1
    fi
    want_v="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$app/Info.plist")"
    want_c="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleVersion' "$app/Info.plist")"
    echo "Check: $archive, the iPhone app at $want_v ($want_c)"
    for bundle in "$app" "$app"/PlugIns/*.appex "$app"/Watch/*.app "$app"/Watch/*.app/PlugIns/*.appex; do
        [ -d "$bundle" ] || continue
        v="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$bundle/Info.plist" 2> /dev/null || echo none)"
        c="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleVersion' "$bundle/Info.plist" 2> /dev/null || echo none)"
        if [ "$v" = "$want_v" ] && [ "$c" = "$want_c" ]; then
            printf '  %-48s %s (%s)\n' "Taqwa.app${bundle#"$app"}" "$v" "$c"
        else
            echo "ERROR: ${bundle#"$archive"/} is $v ($c), not the iPhone app's $want_v ($want_c)." >&2
            bad=1
        fi
    done
    local watch widgets
    for watch in "$app"/Watch/*.app; do
        echo "  size: $(basename "$watch") $(du -sk "$watch" | cut -f1) KB, its widgets included"
        for widgets in "$watch"/PlugIns/*.appex; do
            if [ -d "$widgets" ]; then echo "  size: $(basename "$widgets") $(du -sk "$widgets" | cut -f1) KB"; fi
        done
    done
    if ! "${TAQWA_WATCH_BUNDLE_CHECK:-scripts/check-watch-bundle.sh}" --release "$app"; then
        echo "ERROR: the watch bundle check fails on $app as a release." >&2
        bad=1
    fi
    if [ "$bad" -ne 0 ]; then
        echo "ERROR: $archive does not pass the archive check." >&2
        return 1
    fi
    echo "Check: $(basename "$archive") passes: the watch app inside, one version, and the bundle check as a release."
}

# The App Store signature, checked without an upload: the last archive exported as an upload would
# sign it, its watch bundles checked as a release, and the IPA deleted again.
export_check() {
    if [ ! -d "$ARCHIVE" ]; then
        echo "No archive at $ARCHIVE — run 'scripts/ios-release.sh archive' first." >&2
        exit 1
    fi
    # Globals, not locals: the EXIT trap below runs after this function has returned.
    EXPORT_CHECK_DIR="$OUT/export-check-$VERSION-$BUILD"
    EXPORT_CHECK_COPY=""
    rm -rf "$EXPORT_CHECK_DIR"
    mkdir -p "$EXPORT_CHECK_DIR"
    cat > "$EXPORT_CHECK_DIR/ExportOptions.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>method</key><string>app-store-connect</string>
    <key>destination</key><string>export</string>
    <key>signingStyle</key><string>automatic</string>
    <key>teamID</key><string>$TEAM_ID</string>
    <key>uploadSymbols</key><false/>
    <key>manageAppVersionAndBuildNumber</key><false/>
</dict>
</plist>
PLIST
    # The IPA and its unzipped copy go whatever happens; the logs and the summary stay.
    trap 'rm -rf "$EXPORT_CHECK_DIR"/*.ipa ${EXPORT_CHECK_COPY:+"$EXPORT_CHECK_COPY"}' EXIT
    xcodebuild -exportArchive -archivePath "$ARCHIVE" -exportOptionsPlist "$EXPORT_CHECK_DIR/ExportOptions.plist" \
        -exportPath "$EXPORT_CHECK_DIR" -allowProvisioningUpdates
    local ipa
    ipa="$(find "$EXPORT_CHECK_DIR" -maxdepth 1 -name '*.ipa' | sort | sed -n 1p)"
    if [ -z "$ipa" ]; then
        echo "ERROR: the export left no IPA in $EXPORT_CHECK_DIR." >&2
        exit 1
    fi
    EXPORT_CHECK_COPY="$(mktemp -d "$EXPORT_CHECK_DIR/unzipped.XXXXXX")"
    unzip -q "$ipa" -d "$EXPORT_CHECK_COPY"
    echo
    echo "Export check: $(basename "$ipa"), exported locally ($(($(wc -c < "$ipa") / 1024)) KB); nothing is uploaded."
    if scripts/check-watch-bundle.sh --release "$EXPORT_CHECK_COPY/Payload/Taqwa.app"; then
        echo "Export check: the App Store signature passes the watch bundle check as a release."
    else
        echo "ERROR: the exported IPA fails the watch bundle check as a release (above)." >&2
        exit 1
    fi
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

case "$COMMAND" in
    archive) archive ;;
    export-check) export_check ;;
    upload) upload ;;
    # set -e stops at the first that fails, so nothing is uploaded that export-check did not pass.
    all) archive; export_check; upload ;;
    check)
        if [ "$#" -ne 2 ] || [ ! -d "$2" ]; then
            echo "usage: scripts/ios-release.sh check <xcarchive>" >&2
            exit 2
        fi
        check "$2" ;;
    *) echo "usage: scripts/ios-release.sh [archive|export-check|upload|check <xcarchive>]" >&2; exit 2 ;;
esac
