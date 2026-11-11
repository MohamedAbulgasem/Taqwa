#!/bin/bash
# Checks the Apple Watch app and its widgets inside a built Taqwa.app (plan 4, Task 4), from the
# bundles themselves, so no build ships what the project was only meant to do:
#
#   scripts/check-watch-bundle.sh [--release] <Taqwa.app>
#   scripts/check-watch-bundle.sh build/ios-dd/Build/Products/Debug-iphonesimulator/Taqwa.app
#
# scripts/ios-build.sh runs it after every build, and scripts/ios-release.sh, with --release, on the
# archive's Taqwa.app and on the exported IPA's (plan 5 Task 7). It prints one line per broken rule,
# naming it, and exits 1 when any rule is broken:
#   1. Taqwa.app/Watch/TaqwaWatch.app and its PlugIns/TaqwaWatchWidgets.appex exist, with the ids
#      $(BUNDLE_ID).watchkitapp and $(BUNDLE_ID).watchkitapp.widgets (BUNDLE_ID read from
#      iosApp/Configuration/Config.xcconfig), WKApplication YES, WKCompanionAppBundleIdentifier the
#      iPhone app's id and WKRunsIndependentlyOfCompanionApp NO, and the widgets a WidgetKit
#      extension;
#   2. the iPhone app, its widget, the watch app and the watch's widgets carry one
#      CFBundleShortVersionString and one CFBundleVersion;
#   3. no watch bundle's Info.plist or InfoPlist.strings holds an NSLocation*, NSHealth*,
#      NSMotionUsageDescription, NSAppTransportSecurity or UIBackgroundModes key;
#   4. the watch targets' entitlements are the app group alone: (a) always, their entitlements
#      files hold one key, com.apple.security.application-groups, whose one value is the iPhone's
#      group (read from iosApp/iosApp/iosApp.entitlements); (b) on a real signature (codesign -dvv
#      names an Authority), the signed entitlements hold that group, no other capability, and only
#      the keys the signature's first Authority allows:
#        - "Apple Development: ...", the archive's: application-identifier, equal to
#          <TEAM_ID>.<the bundle's id>; com.apple.developer.team-identifier, equal to TEAM_ID
#          (iosApp/Configuration/Config.xcconfig); get-task-allow; and keychain-access-groups only
#          as the one value <TEAM_ID>.<the bundle's id>;
#        - "Apple Distribution: ..." or "iPhone Distribution: ...", the export's: the same keys and
#          beta-reports-active, with get-task-allow absent or false;
#      any other key, or any other Authority, is broken, named. A key Xcode adds by itself that is
#      not listed goes to the Lead with the run's output; it joins the list only by his ruling. A
#      simulator build keeps its entitlements in the binary's simulated-entitlements section, not
#      in an ad hoc signature, so there 4b is skipped and says so, except under --release;
#   5. no location request, no network and no Kotlin, in every Mach-O file at each watch bundle's
#      top level and in its Frameworks/: the executable, <Name>.debug.dylib and __preview.dylib. A
#      Debug build with ENABLE_PREVIEWS puts the code in <Name>.debug.dylib and leaves the
#      executable a stub, so the executable alone would prove nothing. The canary: each bundle's
#      files must name open_taqwa_on_phone, a key both watch targets' own code names, or the scan
#      missed the code and fails, naming the files it read;
#   6. each watch bundle holds PrivacyInfo.xcprivacy and the seven .lproj folders;
#   7. the watch's widgets are at most 5 MB and the watch app, widgets included, at most 15 MB;
#   8. a Release bundle (CONFIGURATION=Release, a Release-* products folder or an .xcarchive)
#      holds no sample-feed file. A Debug bundle may: its "Debug samples" phase copies them.
# --release, given before <Taqwa.app>, is the Release signal: rule 8 always runs, and rule 4b is
# required, so an unsigned or ad hoc bundle is broken ("a release bundle must be signed"). Without
# it the bundle is judged by its own path, as above. TAQWA_CODESIGN (default codesign) is the command
# rule 4b's two reads run, -dvv and -d --entitlements - --xml, so that scripts/test-watch-bundle-
# signing.sh can give a signature of its choosing; scripts/ios-release.sh's archive and export-check
# refuse to run with it set.
set -uo pipefail

RELEASE=false
if [ "${1:-}" = --release ]; then
  RELEASE=true
  shift
fi
APP=${1:?usage: check-watch-bundle.sh [--release] <Taqwa.app>}
APP=${APP%/}
CODESIGN=${TAQWA_CODESIGN:-codesign}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
WATCH="$APP/Watch/TaqwaWatch.app"
WIDGETS="$WATCH/PlugIns/TaqwaWatchWidgets.appex"
PHONE_WIDGET="$APP/PlugIns/TaqwaWidget.appex"
LANGUAGES=(en ar fr tr id ur bn)
WIDGETS_LIMIT_KB=$((5 * 1024))
WATCH_LIMIT_KB=$((15 * 1024))
CANARY=open_taqwa_on_phone
# Words a binary that asks for location or reaches the network names (rule 5).
FORBIDDEN_CALLS=(requestWhenInUseAuthorization requestAlwaysAuthorization startUpdatingLocation
  requestLocation dataTaskWithURL NSURLSession)
# Info keys no watch bundle may carry (rule 3), as one extended regular expression.
FORBIDDEN_KEYS='^(NSLocation.*|NSHealth.*|NSMotionUsageDescription|NSAppTransportSecurity|UIBackgroundModes)$'

BROKEN=0
broken() { echo "check-watch-bundle: rule $1: $2" >&2; BROKEN=$((BROKEN + 1)); }
info() { /usr/libexec/PlistBuddy -c "Print :$2" "$1/Info.plist" 2>/dev/null; }
name() { basename "$1"; }
# Every key, at any depth, of a plist in any format (XML, binary, a .strings table).
keys_of() { plutil -convert xml1 -o - "$1" 2>/dev/null | sed -n 's|.*<key>\(.*\)</key>.*|\1|p'; }
# A plist file as compact JSON, or nothing when it does not parse.
json_of() { plutil -convert json -o - "$1" 2>/dev/null; }

BUNDLE_ID=$(sed -n 's/^BUNDLE_ID=//p' "$ROOT/iosApp/Configuration/Config.xcconfig" | tr -d '[:space:]')
[ -n "$BUNDLE_ID" ] || { broken 1 "no BUNDLE_ID in iosApp/Configuration/Config.xcconfig"; exit 1; }
TEAM_ID=$(sed -n 's/^TEAM_ID=//p' "$ROOT/iosApp/Configuration/Config.xcconfig" | tr -d '[:space:]')

# --- 1. the bundles, where the targets embed them, with their ids and WK keys -------------------

[ -d "$APP" ] || { broken 1 "$APP is not a built app"; exit 1; }
if [ ! -d "$WATCH" ] || [ ! -d "$WIDGETS" ]; then
  broken 1 "$(name "$APP") has no Watch/TaqwaWatch.app with PlugIns/TaqwaWatchWidgets.appex inside"
  exit 1
fi
phone_id=$(info "$APP" CFBundleIdentifier)
[ "$phone_id" = "$BUNDLE_ID" ] ||
  broken 1 "$(name "$APP") is $phone_id, not Config.xcconfig's BUNDLE_ID $BUNDLE_ID"
[ "$(info "$WATCH" CFBundleIdentifier)" = "$BUNDLE_ID.watchkitapp" ] ||
  broken 1 "TaqwaWatch.app is '$(info "$WATCH" CFBundleIdentifier)', not $BUNDLE_ID.watchkitapp"
[ "$(info "$WIDGETS" CFBundleIdentifier)" = "$BUNDLE_ID.watchkitapp.widgets" ] ||
  broken 1 "TaqwaWatchWidgets.appex is '$(info "$WIDGETS" CFBundleIdentifier)', not $BUNDLE_ID.watchkitapp.widgets"
[ "$(info "$WATCH" WKApplication)" = true ] ||
  broken 1 "TaqwaWatch.app's WKApplication is '$(info "$WATCH" WKApplication)', not YES"
[ "$(info "$WATCH" WKCompanionAppBundleIdentifier)" = "$BUNDLE_ID" ] ||
  broken 1 "TaqwaWatch.app's WKCompanionAppBundleIdentifier is '$(info "$WATCH" WKCompanionAppBundleIdentifier)', not $BUNDLE_ID"
[ "$(info "$WATCH" WKRunsIndependentlyOfCompanionApp)" = false ] ||
  broken 1 "TaqwaWatch.app's WKRunsIndependentlyOfCompanionApp is '$(info "$WATCH" WKRunsIndependentlyOfCompanionApp)', not NO"
[ "$(info "$WIDGETS" NSExtension:NSExtensionPointIdentifier)" = com.apple.widgetkit-extension ] ||
  broken 1 "TaqwaWatchWidgets.appex is not a com.apple.widgetkit-extension"

# --- 2. one version for the four bundles ------------------------------------------------------

for key in CFBundleShortVersionString CFBundleVersion; do
  seen=""
  for bundle in "$APP" "$PHONE_WIDGET" "$WATCH" "$WIDGETS"; do
    [ -d "$bundle" ] || { broken 2 "$(name "$bundle") is missing"; continue; }
    seen+="$(name "$bundle")=$(info "$bundle" "$key") "
  done
  distinct=$(echo "$seen" | tr ' ' '\n' | sed -n 's/^[^=]*=//p' | sort -u | wc -l | tr -d ' ')
  [ "$distinct" = 1 ] || broken 2 "$key differs: $seen"
done

# --- 3. no location, health, motion, transport or background keys -----------------------------

for bundle in "$WATCH" "$WIDGETS"; do
  for table in "$bundle/Info.plist" "$bundle"/*.lproj/InfoPlist.strings; do
    [ -f "$table" ] || continue
    bad=$(keys_of "$table" | grep -E "$FORBIDDEN_KEYS" | sort -u | tr '\n' ' ')
    [ -z "$bad" ] || broken 3 "${table#"$APP"/} holds $bad"
  done
done

# --- 4. the app group alone ---------------------------------------------------------------------

# PlistBuddy, not plutil -extract: the key's own dots would read as plutil's key-path separators.
GROUP=$(/usr/libexec/PlistBuddy -c 'Print :com.apple.security.application-groups:0' "$ROOT/iosApp/iosApp/iosApp.entitlements" 2>/dev/null)
[ -n "$GROUP" ] || broken 4a "iosApp/iosApp/iosApp.entitlements names no app group"
WANT="{\"com.apple.security.application-groups\":[\"$GROUP\"]}"
for file in iosApp/TaqwaWatch/TaqwaWatch.entitlements iosApp/TaqwaWatchWidgets/TaqwaWatchWidgets.entitlements; do
  got=$(json_of "$ROOT/$file")
  [ "$got" = "$WANT" ] || broken 4a "$file is '$got', not the app group alone, $WANT"
done
unsigned=""
for bundle in "$WATCH" "$WIDGETS"; do
  # -dvv, not -dv: codesign prints the Authority lines only from verbosity 2. And the output is
  # read whole before the grep: `codesign -dvv … | grep -q` reads as false under pipefail, because
  # grep stops at the first Authority line and codesign, still writing, dies of SIGPIPE (141).
  signature=$("$CODESIGN" -dvv "$bundle" 2>&1)
  authority=$(sed -n '/^Authority=/{s/^Authority=//p;q;}' <<<"$signature")
  if [ -n "$authority" ]; then
    signed=$("$CODESIGN" -d --entitlements - --xml "$bundle" 2>/dev/null | plutil -convert json -o - - 2>/dev/null)
    # One line: the keys the signature carries, then each thing wrong with them, "|" between.
    verdict=$(echo "$signed" | python3 -I -c '
import json, sys
authority, team, bundle_id, group = sys.argv[1:5]
text = sys.stdin.read().strip()
d = json.loads(text) if text else {}  # a signature with no entitlements at all
own = team + "." + bundle_id
development = authority.startswith("Apple Development: ")
distribution = authority.startswith(("Apple Distribution: ", "iPhone Distribution: "))
bad = []
if not team:
    bad.append("no TEAM_ID in iosApp/Configuration/Config.xcconfig to hold the signature to")
if not (development or distribution):
    bad.append("an Authority that is neither Apple Development nor Apple or iPhone Distribution: " + authority)
allowed = {"com.apple.security.application-groups", "application-identifier",
           "com.apple.developer.team-identifier", "get-task-allow", "keychain-access-groups"}
if distribution:
    allowed.add("beta-reports-active")
extra = sorted(k for k in d if k not in allowed)
if extra:
    bad.append("keys this signature may not carry: " + " ".join(extra))
if d.get("com.apple.security.application-groups") != [group]:
    bad.append("com.apple.security.application-groups=" + json.dumps(d.get("com.apple.security.application-groups")))
for key, want in (("application-identifier", own), ("com.apple.developer.team-identifier", team)):
    if d.get(key) != want:
        bad.append(key + "=" + json.dumps(d.get(key)) + ", not " + want)
if "keychain-access-groups" in d and d["keychain-access-groups"] != [own]:
    bad.append("keychain-access-groups=" + json.dumps(d["keychain-access-groups"]) + ", not [" + own + "]")
if distribution and d.get("get-task-allow", False) is not False:
    bad.append("get-task-allow=" + json.dumps(d["get-task-allow"]) + " under a distribution signature")
print(" ".join(sorted(d)) + "|" + "; ".join(bad))' "$authority" "$TEAM_ID" "$(info "$bundle" CFBundleIdentifier)" "$GROUP" 2>&1)
    echo "check-watch-bundle: rule 4b read $(name "$bundle"): signed by $authority, keys ${verdict%%|*}"
    case "$verdict" in
      *"|"*) wrong=${verdict#*|} ;;
      *) wrong="unreadable signed entitlements: $verdict" ;;
    esac
    [ -z "$wrong" ] || broken 4b "$(name "$bundle")'s signature: $wrong"
  else
    unsigned+="$(name "$bundle") "
  fi
done
if [ -n "$unsigned" ]; then
  if $RELEASE; then
    broken 4b "a release bundle must be signed, and ${unsigned% } is unsigned or ad hoc"
  else
    echo "check-watch-bundle: rule 4b skipped: unsigned or ad hoc (${unsigned% })"
  fi
fi

# --- 5. no location request, no network, no Kotlin, and the canary ------------------------------

for bundle in "$WATCH" "$WIDGETS"; do
  read_files=()
  canary=false
  while IFS= read -r file; do
    # Each tool's output is read whole before it is searched, as in rule 4b: a `| grep -q` under
    # pipefail can read as false when grep stops early and the writer dies of SIGPIPE.
    kind=$(file -b "$file")
    [[ $kind == Mach-O* ]] || continue
    read_files+=("${file#"$bundle"/}")
    words=$(strings - "$file")
    for call in "${FORBIDDEN_CALLS[@]}"; do
      if grep -qF "$call" <<<"$words"; then broken 5 "$(name "$bundle")/${file#"$bundle"/} names $call"; fi
    done
    links=$(otool -L "$file" | tail -n +2)
    kotlin=$(grep -E '(^|/)(shared|widgetcore|watchcore)\.framework/' <<<"$links" | awk '{print $1}' | sort -u | tr '\n' ' ')
    [ -z "$kotlin" ] || broken 5 "$(name "$bundle")/${file#"$bundle"/} links a Kotlin framework: $kotlin"
    # A static Kotlin/Native framework is not in otool -L; its runtime names its functions kfun:.
    if grep -qF 'kfun:' <<<"$words"; then broken 5 "$(name "$bundle")/${file#"$bundle"/} carries Kotlin/Native code (kfun:)"; fi
    if grep -qF "$CANARY" <<<"$words"; then canary=true; fi
  done < <(find "$bundle" -maxdepth 1 -type f; [ -d "$bundle/Frameworks" ] && find "$bundle/Frameworks" -type f)
  echo "check-watch-bundle: rule 5 read $(name "$bundle"): ${read_files[*]:-nothing}"
  $canary || broken 5 "the canary $CANARY is in none of $(name "$bundle")'s files read (${read_files[*]:-none}): the scan missed the code"
done

# --- 6. the privacy manifest and the seven languages --------------------------------------------

for bundle in "$WATCH" "$WIDGETS"; do
  [ -f "$bundle/PrivacyInfo.xcprivacy" ] || broken 6 "$(name "$bundle") has no PrivacyInfo.xcprivacy"
  for lang in "${LANGUAGES[@]}"; do
    [ -d "$bundle/$lang.lproj" ] || broken 6 "$(name "$bundle") has no $lang.lproj"
  done
done

# --- 7. the sizes -------------------------------------------------------------------------------

widgets_kb=$(du -sk "$WIDGETS" | cut -f1)
watch_kb=$(du -sk "$WATCH" | cut -f1)
echo "check-watch-bundle: TaqwaWatchWidgets.appex ${widgets_kb} KB (limit ${WIDGETS_LIMIT_KB} KB), TaqwaWatch.app ${watch_kb} KB (limit ${WATCH_LIMIT_KB} KB)"
[ "$widgets_kb" -le "$WIDGETS_LIMIT_KB" ] || broken 7 "TaqwaWatchWidgets.appex is ${widgets_kb} KB, over ${WIDGETS_LIMIT_KB} KB"
[ "$watch_kb" -le "$WATCH_LIMIT_KB" ] || broken 7 "TaqwaWatch.app is ${watch_kb} KB, over ${WATCH_LIMIT_KB} KB"

# --- 8. no sample feed in a Release bundle --------------------------------------------------------

case "$RELEASE:${CONFIGURATION:-}:$APP" in
  true:* | *:Release:* | *Release-*/* | *.xcarchive/*)
    samples=$(find "$APP" -name 'sample-feed*' | sed "s|^$APP/||" | tr '\n' ' ')
    [ -z "$samples" ] || broken 8 "a Release bundle holds $samples"
    ;;
  *) echo "check-watch-bundle: rule 8 skipped: a Debug bundle ($(find "$WATCH" -maxdepth 1 -name 'sample-feed*' | wc -l | tr -d ' ') sample feeds in TaqwaWatch.app)" ;;
esac

if [ "$BROKEN" -ne 0 ]; then
  echo "check-watch-bundle: $BROKEN broken in $APP" >&2
  exit 1
fi
echo "check-watch-bundle: every rule holds in $APP"
