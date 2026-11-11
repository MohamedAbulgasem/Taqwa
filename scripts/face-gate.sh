#!/usr/bin/env bash
# The face gate, before every release (plan 5 Task 7's scripts/release.sh runs it first): the Taqwa
# face as committed is the face its generators write, every form the phone can send fits it, and
# Google's validators pass it.
#
#   scripts/face-gate.sh
#
# 1. The five generators' --check, which write nothing: tools/face-arch.py, face-centre.py,
#    face-moon.py and face-sun.py (watchface.xml's blocks) and tools/face-art.py (its pictures, by
#    their decoded pixels, mode and size).
# 2. The fit tools, on the watch's own fonts: hijri-fit.py, window-fit.py, slot-fit.py and
#    moon-fit.py, and plan 2's date-fit.py with its own tests, date-fit-test.py. Each reads
#    phone-forms.json, the phone's forms as committed.
# 3. tools/watchface/validate.sh: wff-validator, and Watch Face Push's validator on the debug face
#    and on a test-signed copy of the release, both built first.
# It runs every check, says which passed, and exits 1 if any failed, or if it changed a tracked file.
#
# The fit tools measure the watch's Noto Sans Bengali UI, which the face does not bundle, so their
# fonts= is the cache tools/watchface/get-system-fonts.sh fills from emulator-5558 and pins by SHA-256
# (TAQWA_SYSTEM_FONTS, default ~/.cache/taqwa-watchface-tools/fonts). The tools were calibrated on
# that font (the Lead's call 4: a Mac-side gate, not CI), so the gate refuses to start, before any
# check, while a pinned font is missing or changed. It needs no device: run get-system-fonts.sh once
# with the emulator up.
set -uo pipefail
cd "$(dirname "$0")/.."

FONTS="${TAQWA_SYSTEM_FONTS:-$HOME/.cache/taqwa-watchface-tools/fonts}"
if ! TAQWA_SYSTEM_FONTS="$FONTS" bash tools/watchface/get-system-fonts.sh --check; then
    echo "face-gate.sh: refused: the fit tools' fonts in $FONTS are missing or not the pinned ones;" \
        "run tools/watchface/get-system-fonts.sh with emulator-5558 up" >&2
    exit 1
fi

TREE_BEFORE="$(git status --porcelain)"
passed=()
failed=()
check() { # what it is, then the command
    local what="$1"
    shift
    echo "== $what"
    if "$@"; then passed+=("$what"); else failed+=("$what"); echo "   FAIL: $what"; fi
}

for generator in face-arch face-centre face-moon face-sun face-art; do
    check "$generator.py --check" python3 -I "tools/$generator.py" --check
done
check "hijri-fit.py" python3 -I tools/watchface/hijri-fit.py "fonts=$FONTS"
check "window-fit.py" python3 -I tools/watchface/window-fit.py "fonts=$FONTS"
check "slot-fit.py" python3 -I tools/watchface/slot-fit.py
check "moon-fit.py" python3 -I tools/watchface/moon-fit.py "fonts=$FONTS"
check "date-fit.py" python3 -I tools/watchface/date-fit.py "fonts=$FONTS"
check "date-fit-test.py" python3 -I tools/watchface/date-fit-test.py
check "validate.sh" bash tools/watchface/validate.sh

if [ "$(git status --porcelain)" != "$TREE_BEFORE" ]; then
    failed+=("the tree as it was")
    echo "   FAIL: the gate changed the tree:"
    git status --porcelain | sed 's/^/     /'
fi

echo
echo "face-gate.sh: ${#passed[@]} passed: ${passed[*]:-none}"
if [ "${#failed[@]}" -gt 0 ]; then
    echo "face-gate.sh: ${#failed[@]} FAILED: ${failed[*]}" >&2
    exit 1
fi
echo "face-gate.sh: PASS"
