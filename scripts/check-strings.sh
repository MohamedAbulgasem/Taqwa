#!/bin/bash
# Every language's strings must carry the same keys and placeholders as English, with the plural
# categories its grammar needs, and the Indonesian Compose twin (values-in) must match values-id.
# The Compose resource accessors are generated per key, so a key missing from one locale compiles
# fine and falls back silently at runtime; this is the check the compiler does not do. It reads the
# watch's own words too: the Galaxy watch app's and the Taqwa face's Android resources, and every
# iPhone and Apple Watch .lproj set (plan 5 Task 8). The check's own tests run first; a failure
# shows their whole output.
#
#   scripts/check-strings.sh
set -eo pipefail
cd "$(dirname "$0")/.."
if ! i18n_test=$(python3 tools/test_i18n_check.py 2>&1); then echo "$i18n_test"; exit 1; fi
echo "test_i18n_check.py: PASS"
python3 tools/i18n-check.py | grep -v "^  note:"
