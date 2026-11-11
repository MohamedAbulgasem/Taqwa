#!/usr/bin/env bash
# Tests scripts/check-watch-bundle.sh's Release signal and rule 4b's keys (plan 5 Task 7), on a
# scratch copy of a built Taqwa.app, with a stub in place of codesign:
#
#   scripts/test-watch-bundle-signing.sh [<Taqwa.app>]
#
# The app is plan 4's Debug simulator build by default
# (build/ios-dd/Build/Products/Debug-iphonesimulator/Taqwa.app, from scripts/ios-build.sh), which
# carries the watch's sample feeds; scripts/ios-build.sh runs this on the build it has just made.
# The stub (TAQWA_CODESIGN) answers the two reads rule 4b makes as codesign does: `-dvv` names the
# chosen Authority lines and `-dv` none (codesign prints them only from verbosity 2), each line one
# write to stderr, with codesign's later lines after them; and `-d --entitlements - --xml` gives the
# chosen entitlements as a plist on stdout. The bundles are never signed and nothing outside the
# scratch copy is changed:
#   - theReleaseFlagFindsTheSampleFeed: --release with a development Authority and the allowed keys
#     fails rule 8, naming a sample-feed-capetown file; with the sample files deleted it passes;
#   - aReleaseBundleMustBeSigned: --release on an ad hoc signature fails rule 4b; without --release
#     it says "rule 4b skipped", as plan 4's check does;
#   - theDevelopmentSigningKeysAreAllowed: the archive's keys, get-task-allow true, pass;
#   - anExtraCapabilityIsRefused: the same plus com.apple.developer.healthkit fails, naming it, with
#     or without --release;
#   - aDistributionSignatureMayNotBeDebuggable: a distribution Authority with get-task-allow true
#     fails; with beta-reports-active true and no get-task-allow it passes, for Apple Distribution
#     and iPhone Distribution alike; beta-reports-active under a development Authority fails;
#   - anotherTeamIsRefused: a com.apple.developer.team-identifier other than TEAM_ID fails;
#   - and an Authority that is neither, an application-identifier or a keychain group of another
#     id, and another app group, each fail, naming what is wrong.
# Exits 0 when every case passes, 1 when any fails, and 2 when it cannot start.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

APP=${1:-build/ios-dd/Build/Products/Debug-iphonesimulator/Taqwa.app}
APP=${APP%/}
if [ ! -d "$APP/Watch/TaqwaWatch.app" ]; then
  echo "no built Taqwa.app with its watch app at $APP: build it with scripts/ios-build.sh first" >&2
  exit 2
fi
TEAM_ID=$(sed -n 's/^TEAM_ID=//p' iosApp/Configuration/Config.xcconfig | tr -d '[:space:]')
GROUP=$(/usr/libexec/PlistBuddy -c 'Print :com.apple.security.application-groups:0' iosApp/iosApp/iosApp.entitlements)
[ -n "$TEAM_ID" ] && [ -n "$GROUP" ] || { echo "no TEAM_ID in Config.xcconfig or no app group in iosApp.entitlements" >&2; exit 2; }

WORK=$(mktemp -d "${TMPDIR:-/tmp}/taqwa-watch-bundle-signing.XXXXXX")
trap 'rm -rf "$WORK"' EXIT
COPY="$WORK/Taqwa.app"
# A clone where the file system allows it (APFS), so the 140 MB app costs nothing to copy.
cp -Rc "$APP" "$COPY" 2> /dev/null || { rm -rf "$COPY"; cp -R "$APP" "$COPY"; }

FAILS=0
pass() { echo "ok - $*"; }
fail() { echo "FAIL: $*"; FAILS=$((FAILS + 1)); }
indent() { sed 's/^/    /'; }

# The stub codesign. Its signature is chosen by the environment:
#   STUB_AUTHORITY        the first Authority line's value; empty for an ad hoc signature
#   STUB_GET_TASK_ALLOW   true, false, or empty for no get-task-allow key
#   STUB_BETA             true, or empty for no beta-reports-active key
#   STUB_EXTRA            one more key, given the value true
#   STUB_TEAM_IDENTIFIER  com.apple.developer.team-identifier (default TEAM_ID)
#   STUB_APP_ID_PREFIX    the application-identifier's prefix (default TEAM_ID)
#   STUB_KEYCHAIN         keychain-access-groups' one value (default <TEAM_ID>.<the bundle's id>)
#   STUB_GROUP            the app group (default the iPhone's)
STUB="$WORK/codesign"
cat > "$STUB" << 'STUB_END'
#!/bin/bash
set -euo pipefail
bundle="${!#}"
echo "$*" >> "$STUB_LOG"
id=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$bundle/Info.plist")
exe=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleExecutable' "$bundle/Info.plist")
# One write a line, to stderr, as codesign writes them.
say() { printf '%s\n' "$1" >&2; }
case "$1" in
  -dv | -dvv)
    say "Executable=$bundle/$exe"
    say "Identifier=$id"
    say "Format=app bundle with Mach-O thin (arm64)"
    if [ -z "$STUB_AUTHORITY" ]; then
      say "CodeDirectory v=20400 size=436 flags=0x2(adhoc) hashes=3+7 location=embedded"
      say "Signature=adhoc"
      say "Info.plist entries=26"
      say "TeamIdentifier=not set"
    else
      say "CodeDirectory v=20500 size=1043 flags=0x0(none) hashes=21+7 location=embedded"
      say "Signature size=4797"
      if [ "$1" = -dvv ]; then
        say "Authority=$STUB_AUTHORITY"
        say "Authority=Apple Worldwide Developer Relations Certification Authority"
        say "Authority=Apple Root CA"
      fi
      say "Signed Time=6 Nov 2026 at 18:00:00"
      say "Info.plist entries=26"
      say "TeamIdentifier=$STUB_TEAM"
    fi
    say "Sealed Resources version=2 rules=10 files=49"
    say "Internal requirements count=1 size=200"
    if [ "$1" = -dvv ]; then
      say "Total signatures=1"
      say "Chosen signature=1"
    fi
    ;;
  -d)
    [ "$*" = "-d --entitlements - --xml $bundle" ] || { echo "stub codesign: unexpected arguments: $*" >&2; exit 64; }
    say "Executable=$bundle/$exe"
    [ -n "$STUB_AUTHORITY" ] || exit 0
    STUB_ID="$id" python3 -I -c '
import os, plistlib, sys
e = os.environ
team = e["STUB_TEAM"]
d = {
    "application-identifier": (e.get("STUB_APP_ID_PREFIX") or team) + "." + e["STUB_ID"],
    "com.apple.developer.team-identifier": e.get("STUB_TEAM_IDENTIFIER") or team,
    "com.apple.security.application-groups": [e.get("STUB_GROUP") or e["STUB_DEFAULT_GROUP"]],
    "keychain-access-groups": [e.get("STUB_KEYCHAIN") or team + "." + e["STUB_ID"]],
}
if e.get("STUB_GET_TASK_ALLOW"):
    d["get-task-allow"] = e["STUB_GET_TASK_ALLOW"] == "true"
if e.get("STUB_BETA"):
    d["beta-reports-active"] = e["STUB_BETA"] == "true"
if e.get("STUB_EXTRA"):
    d[e["STUB_EXTRA"]] = True
sys.stdout.buffer.write(plistlib.dumps(d, fmt=plistlib.FMT_XML))'
    ;;
  *) echo "stub codesign: unexpected arguments: $*" >&2; exit 64 ;;
esac
STUB_END
chmod +x "$STUB"

DEV="Apple Development: Mohamed Abulgasem (A1B2C3D4E5)"
DIST="Apple Distribution: Mohamed Abulgasem ($TEAM_ID)"
OUT="$WORK/out.txt"
status=0
# check <description of the signature> <env assignments...> -- <check-watch-bundle.sh arguments...>
# Runs the real check with the stub, its output in $OUT and its exit in $status.
check() {
  local -a vars=()
  while [ "$1" != -- ]; do vars+=("$1"); shift; done
  shift
  status=0
  : > "$WORK/stub.log"
  env STUB_LOG="$WORK/stub.log" STUB_TEAM="$TEAM_ID" STUB_DEFAULT_GROUP="$GROUP" STUB_AUTHORITY= \
    STUB_GET_TASK_ALLOW= STUB_BETA= STUB_EXTRA= STUB_TEAM_IDENTIFIER= STUB_APP_ID_PREFIX= STUB_KEYCHAIN= \
    STUB_GROUP= ${vars[@]+"${vars[@]}"} TAQWA_CODESIGN="$STUB" bash scripts/check-watch-bundle.sh "$@" > "$OUT" 2>&1 || status=$?
}
said() { grep -qF -- "$1" "$OUT"; }
# expect_pass <case> / expect_fail <case> <words the failure must name...>
expect_pass() {
  if [ "$status" -eq 0 ] && said "every rule holds"; then pass "$1"; else fail "$1 (exit $status):"; indent < "$OUT"; fi
}
expect_fail() {
  local name=$1 word missing=""
  shift
  for word in "$@"; do said "$word" || missing+="'$word' "; done
  if [ "$status" -eq 1 ] && [ -z "$missing" ]; then pass "$name"; else fail "$name (exit $status, not naming ${missing:-nothing missing}):"; indent < "$OUT"; fi
}

# --- theReleaseFlagFindsTheSampleFeed -------------------------------------------------------------
check STUB_AUTHORITY="$DEV" STUB_GET_TASK_ALLOW=true -- --release "$COPY"
expect_fail "theReleaseFlagFindsTheSampleFeed: --release fails rule 8 on the Debug build's sample feeds" "rule 8" "sample-feed-capetown"
if grep -q -- '-dvv' "$WORK/stub.log"; then
  pass "the check reads the Authority with codesign -dvv"
else
  fail "the check never called the stub with -dvv (calls: $(xargs < "$WORK/stub.log"))"
fi
find "$COPY" -name 'sample-feed*' -delete
check STUB_AUTHORITY="$DEV" STUB_GET_TASK_ALLOW=true -- --release "$COPY"
expect_pass "theReleaseFlagFindsTheSampleFeed: with the sample files deleted, --release passes"

# --- aReleaseBundleMustBeSigned -------------------------------------------------------------------
check -- --release "$COPY"
expect_fail "aReleaseBundleMustBeSigned: --release on an ad hoc signature fails rule 4b" "rule 4b: a release bundle must be signed"
check -- "$COPY"
if [ "$status" -eq 0 ] && said "rule 4b skipped"; then
  pass "aReleaseBundleMustBeSigned: without --release an ad hoc signature says rule 4b skipped"
else
  fail "aReleaseBundleMustBeSigned: without --release, exit $status:"; indent < "$OUT"
fi

# --- theDevelopmentSigningKeysAreAllowed ----------------------------------------------------------
check STUB_AUTHORITY="$DEV" STUB_GET_TASK_ALLOW=true -- --release "$COPY"
expect_pass "theDevelopmentSigningKeysAreAllowed: the archive's keys, get-task-allow true"
if said "Apple Development" && said "get-task-allow"; then
  pass "the check names the Authority and the signed keys it read"
else
  fail "the check does not name the Authority and keys it read:"; indent < "$OUT"
fi

# --- anExtraCapabilityIsRefused -------------------------------------------------------------------
check STUB_AUTHORITY="$DEV" STUB_GET_TASK_ALLOW=true STUB_EXTRA=com.apple.developer.healthkit -- --release "$COPY"
expect_fail "anExtraCapabilityIsRefused: HealthKit beside the development keys" "rule 4b" "com.apple.developer.healthkit"
check STUB_AUTHORITY="$DEV" STUB_GET_TASK_ALLOW=true STUB_EXTRA=com.apple.developer.healthkit -- "$COPY"
expect_fail "anExtraCapabilityIsRefused: without --release too, on a real signature" "rule 4b" "com.apple.developer.healthkit"

# --- aDistributionSignatureMayNotBeDebuggable -----------------------------------------------------
check STUB_AUTHORITY="$DIST" STUB_GET_TASK_ALLOW=true STUB_BETA=true -- --release "$COPY"
expect_fail "aDistributionSignatureMayNotBeDebuggable: get-task-allow true under Apple Distribution" "rule 4b" "get-task-allow"
check STUB_AUTHORITY="$DIST" STUB_BETA=true -- --release "$COPY"
expect_pass "aDistributionSignatureMayNotBeDebuggable: beta-reports-active and no get-task-allow"
check STUB_AUTHORITY="$DIST" STUB_BETA=true STUB_GET_TASK_ALLOW=false -- --release "$COPY"
expect_pass "aDistributionSignatureMayNotBeDebuggable: get-task-allow false is allowed"
check STUB_AUTHORITY="iPhone Distribution: Mohamed Abulgasem ($TEAM_ID)" STUB_BETA=true -- --release "$COPY"
expect_pass "aDistributionSignatureMayNotBeDebuggable: iPhone Distribution is a distribution Authority"
check STUB_AUTHORITY="$DEV" STUB_GET_TASK_ALLOW=true STUB_BETA=true -- --release "$COPY"
expect_fail "beta-reports-active is a distribution key only" "rule 4b" "beta-reports-active"

# --- anotherTeamIsRefused -------------------------------------------------------------------------
check STUB_AUTHORITY="$DEV" STUB_GET_TASK_ALLOW=true STUB_TEAM_IDENTIFIER=5S5P2Q72MV -- --release "$COPY"
expect_fail "anotherTeamIsRefused: another team-identifier" "rule 4b" "com.apple.developer.team-identifier" "5S5P2Q72MV"

# --- what else is wrong ---------------------------------------------------------------------------
check STUB_AUTHORITY="Developer ID Application: Someone Else (ZZZZZZZZZZ)" STUB_GET_TASK_ALLOW=true -- --release "$COPY"
expect_fail "an Authority that is neither development nor distribution is refused" "rule 4b" "Developer ID Application: Someone Else"
check STUB_AUTHORITY="$DEV" STUB_GET_TASK_ALLOW=true STUB_APP_ID_PREFIX=5S5P2Q72MV -- --release "$COPY"
expect_fail "an application-identifier of another team is refused" "rule 4b" "application-identifier" "5S5P2Q72MV"
check STUB_AUTHORITY="$DEV" STUB_GET_TASK_ALLOW=true "STUB_KEYCHAIN=$TEAM_ID.*" -- --release "$COPY"
expect_fail "a keychain group beyond the bundle's own is refused" "rule 4b" "keychain-access-groups"
check STUB_AUTHORITY="$DEV" STUB_GET_TASK_ALLOW=true STUB_GROUP=group.someone.else -- --release "$COPY"
expect_fail "another app group is refused" "rule 4b" "group.someone.else"

if [ "$FAILS" -eq 0 ]; then
  echo "test-watch-bundle-signing: PASS"
else
  echo "test-watch-bundle-signing: $FAILS failed"
  exit 1
fi
