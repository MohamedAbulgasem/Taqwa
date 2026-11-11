# Google's two validators for the Taqwa face, pinned (sourced by validate.sh and push-token.sh, never
# run):
#
#   1. wff-validator (github.com/google/watchface): watchface.xml against the Watch Face Format
#      schema of a format version.
#   2. validator-push-cli (Google Maven, com.google.android.wearable.watchface.validator): the
#      checks Watch Face Push makes before it installs a face, which on success print the face's
#      validation token for the package that pushes it.
#
# They are dev tools, fetched once into ~/.cache/taqwa-watchface-tools (never into the repository)
# and checked against the SHA-256 below. Google publishes wff-validator only under the "release" and
# "latest" tags, which it moves to each new copy: no tag names a version (23 October 2026, and again
# on 25 October 2026, when both still held the copy of 16 October pinned here, and
# validator-push-cli's newest was still 1.1.0-alpha01). So a new copy stops every script that uses
# it, and CI, until its hash is read and pinned here: on purpose, because the hash is the only
# record of which validator passed the face. Neither is run from inside the repository.
VALIDATORS_LIB="${BASH_SOURCE[0]}"
VALIDATOR_CACHE="$HOME/.cache/taqwa-watchface-tools"

PUSH_VERSION=1.1.0-alpha01
PUSH_JAR="$VALIDATOR_CACHE/validator-push-cli-$PUSH_VERSION/validator-push-cli-$PUSH_VERSION.jar"
PUSH_URL="https://dl.google.com/android/maven2/com/google/android/wearable/watchface/validator/validator-push-cli/$PUSH_VERSION/validator-push-cli-$PUSH_VERSION.jar"
PUSH_SHA256=04dc20a0994df3eaebf025e107f7589c147b2ce8a2c628745e01a5c0a63fe4dd

WFF_JAR="$VALIDATOR_CACHE/wff-validator-release/wff-validator.jar"
WFF_URL="https://github.com/google/watchface/releases/download/release/wff-validator.jar"
# wff-validator 1.7.0 (formats 1 to 5), as published on 16 October 2026
WFF_SHA256=fb3262aa146de24c169a1d451901b4df2bfedca28a27c3c168a487e02d393771

# The package of the app that installs the face through Watch Face Push: the watch app's. A token
# is for this package alone.
CLIENT_PACKAGE=world.taqwa.app

fetch() { # url, file, sha256
    if [ ! -f "$2" ]; then
        mkdir -p "$(dirname "$2")"
        echo "fetching $(basename "$2") from $1"
        curl -s -f -L -o "$2.part" "$1"
        mv "$2.part" "$2"
    fi
    local got
    got="$(shasum -a 256 "$2" | cut -d' ' -f1)"
    if [ "$got" != "$3" ]; then
        echo "$(basename "$2") is not the copy this script was written for (SHA-256 $got);" \
            "read what changed, then pin the new hash in $VALIDATORS_LIB" >&2
        exit 1
    fi
}

need_push_validator() { fetch "$PUSH_URL" "$PUSH_JAR" "$PUSH_SHA256"; }
need_wff_validator() { fetch "$WFF_URL" "$WFF_JAR" "$WFF_SHA256"; }

# The Push validator's whole output for the APK at the absolute path $1, for CLIENT_PACKAGE, run from
# the cache, so that nothing it writes lands in the repository. The validator exits 0 whatever it
# finds, so its verdict is in its output: the line "Validation is successful", and on that line's
# heels "No failing checks detected, generated token: <the token>".
run_push_validator() {
    (cd "$VALIDATOR_CACHE" && java -jar "$PUSH_JAR" --apk_path="$1" --package_name="$CLIENT_PACKAGE" 2>&1)
}
