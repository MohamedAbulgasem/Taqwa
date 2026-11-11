#!/usr/bin/env bash
# Proves that -Ptaqwa.unsignedRelease=true keeps every signing key out of a release build (plan 5
# Task 3), for the phone (:androidApp) and the Wear app (:wearApp), without any real key:
#   - a decoy keystore.properties is put in the repository's root, as Mohamed's would be. It holds
#     no secret (its store file, /nonexistent/decoy.jks, does not exist) and a trap removes it;
#   - with the property, both release builds pass and apksigner finds no signature on either APK;
#   - without it, the same build fails on the decoy's store file in both modules. So the decoy would
#     have been used, and the property is what kept it out.
# It refuses to start (exit 2) while a keystore.properties file, or a link to one, is in the root:
# that may be the real upload key's, and nothing here reads, moves or replaces it. It runs Gradle in
# the repository and leaves the two modules' release outputs unsigned behind it.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

PROPS=keystore.properties
DECOY_STORE=/nonexistent/decoy.jks
if [ -e "$PROPS" ] || [ -L "$PROPS" ]; then
  echo "$ROOT/$PROPS is there, which may be the upload key's: this test never touches it, so it does not run" >&2
  exit 2
fi
SDK="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' local.properties 2> /dev/null)}"
APKSIGNER="$(ls -d "$SDK"/build-tools/*/apksigner 2> /dev/null | sort -V | tail -1)"
[ -n "$APKSIGNER" ] || { echo "no apksigner under $SDK/build-tools" >&2; exit 2; }

LOG=$(mktemp -d "${TMPDIR:-/tmp}/taqwa-unsigned-release.XXXXXX")
cleanup() { rm -f "$ROOT/$PROPS"; rm -rf "$LOG"; }
trap cleanup EXIT
# The decoy: the shape keystore.properties.example gives, and nothing that opens anything.
cat > "$PROPS" << EOF
storeFile=$DECOY_STORE
storePassword=decoy
keyAlias=decoy
keyPassword=decoy
EOF

FAILS=0
pass() { echo "ok - $*"; }
fail() { echo "FAIL: $*"; FAILS=$((FAILS + 1)); }

TASKS=(:androidApp:assembleRelease :wearApp:assembleRelease)
OUTPUTS=(androidApp/build/outputs/apk/release wearApp/build/outputs/apk/release)

# 1. With the property: both build, and neither APK is signed.
echo "removing the release outputs first, so that only this run's APKs are judged: ${OUTPUTS[*]}"
rm -rf "${OUTPUTS[@]}"
if ./gradlew "${TASKS[@]}" -Ptaqwa.unsignedRelease=true > "$LOG/unsigned.txt" 2>&1; then
  pass "with -Ptaqwa.unsignedRelease=true both release builds pass beside the decoy"
else
  fail "with -Ptaqwa.unsignedRelease=true the release builds failed:"
  tail -30 "$LOG/unsigned.txt" | sed 's/^/    /'
fi
for dir in "${OUTPUTS[@]}"; do
  apks=$(find "$dir" -maxdepth 1 -name '*.apk' 2> /dev/null | sort || true)
  if [ "$(grep -c . <<< "$apks")" -ne 1 ]; then
    fail "expected one APK in $dir, found: $(xargs <<< "$apks")"
    continue
  fi
  case "$apks" in
    *-unsigned.apk) pass "$apks is the build's unsigned output" ;;
    *) fail "$apks is not named unsigned: a signing config was set" ;;
  esac
  if "$APKSIGNER" verify "$apks" > "$LOG/verify.txt" 2>&1; then
    fail "apksigner verifies $apks: it is signed"
  elif grep -q "DOES NOT VERIFY" "$LOG/verify.txt"; then
    pass "apksigner finds no signature on $apks ($(grep -m1 '^ERROR' "$LOG/verify.txt" || true))"
  else
    fail "apksigner could not read $apks:"
    sed 's/^/    /' "$LOG/verify.txt"
  fi
done

# 1b. A misspelt value stops the build in both modules, so it never signs by mistake.
for module in androidApp wearApp; do
  if ./gradlew ":$module:help" -Ptaqwa.unsignedRelease=ture > "$LOG/misspelt-$module.txt" 2>&1; then
    fail ":$module accepted -Ptaqwa.unsignedRelease=ture"
  elif grep -q "taqwa.unsignedRelease is true or false, not 'ture'" "$LOG/misspelt-$module.txt"; then
    pass ":$module stops on -Ptaqwa.unsignedRelease=ture with its message"
  else
    fail ":$module failed on -Ptaqwa.unsignedRelease=ture, but not with its message:"
    tail -20 "$LOG/misspelt-$module.txt" | sed 's/^/    /'
  fi
done

# 2. Without it: the decoy is the signing config of both, and its missing store fails the build.
if ./gradlew "${TASKS[@]}" --continue > "$LOG/signed.txt" 2>&1; then
  fail "without the property the release builds passed beside the decoy: they never read it"
else
  if grep -q "$DECOY_STORE" "$LOG/signed.txt"; then
    pass "without the property the build fails on the decoy's store file, $DECOY_STORE"
  else
    fail "without the property the build failed, but not on the decoy's store file:"
    tail -30 "$LOG/signed.txt" | sed 's/^/    /'
  fi
  for module in androidApp wearApp; do
    if grep -q "Execution failed for task ':$module:validateSigningRelease'" "$LOG/signed.txt"; then
      pass ":$module would have signed with the decoy (validateSigningRelease failed on it)"
    else
      fail ":$module did not try the decoy's signing config"
    fi
  done
fi

if [ "$FAILS" -eq 0 ]; then
  echo "test-unsigned-release: PASS"
else
  echo "test-unsigned-release: $FAILS failed"
  exit 1
fi
