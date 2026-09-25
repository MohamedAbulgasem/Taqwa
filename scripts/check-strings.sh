#!/bin/bash
# Every language's strings must carry the same keys and placeholders as English, with the plural
# categories its grammar needs, and the Indonesian Compose twin (values-in) must match values-id.
# The Compose resource accessors are generated per key, so a key missing from one locale compiles
# fine and falls back silently at runtime; this is the check the compiler does not do.
#
#   scripts/check-strings.sh
set -eo pipefail
cd "$(dirname "$0")/.."
python3 tools/i18n-check.py | grep -v "^  note:"
