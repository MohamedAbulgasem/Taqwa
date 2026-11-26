#!/usr/bin/env bash
# The face gate, before every release (plan 5 Task 7's scripts/release.sh runs it at its step 2): the
# Taqwa face as committed is the face its generators write, every form the phone can send fits it,
# and Google's validators pass it.
#
#   scripts/face-gate.sh              the whole gate, before a release that ships the watch
#   scripts/face-gate.sh --fit-only   parts 1 and 2 alone, before a release that keeps the watch back
#
# A release that keeps the watch back still ships the phone, and the phone writes the words the face
# draws (phone-forms.json, which shared's FaceFormsTest writes from the phone's code, and the Hijri
# names hijri-fit.py reads from shared): the face Play keeps serving has to fit the phone's forms
# as this release writes them. --fit-only runs the generators' --check (the fit tools take the
# face's geometry from the generators, so this ties their verdict to the face as committed) and the
# fit tools, and leaves out part 3, whose face builds are Gradle builds and whose validators judge a
# face that is not shipping. It needs no key and no Gradle.
#
# 1. The five generators' --check, which write nothing: tools/face-arch.py, face-centre.py,
#    face-moon.py and face-sun.py (watchface.xml's blocks) and tools/face-art.py (its pictures, by
#    their decoded pixels, mode and size).
# 2. The fit tools, on the watch's own fonts: hijri-fit.py, window-fit.py, slot-fit.py and
#    moon-fit.py, and plan 2's date-fit.py with its own tests, date-fit-test.py. Each reads
#    phone-forms.json, the phone's forms as committed.
# 3. tools/watchface/validate.sh: wff-validator, and Watch Face Push's validator on the debug face
#    and on a test-signed copy of the release, both built first.
# It runs every check, says which passed, and exits 1 if any failed, or if it changed a tracked file;
# 2 for an argument it does not know.
#
# The fit tools measure the watch's Noto Sans Bengali UI, which the face does not bundle, so their
# fonts= is the cache tools/watchface/get-system-fonts.sh fills from emulator-5558 and pins by SHA-256
# (TAQWA_SYSTEM_FONTS, default ~/.cache/taqwa-watchface-tools/fonts). The tools were calibrated on
# that font (the Lead's call 4: a Mac-side gate, not CI), so the gate refuses to start, before any
# check, while a pinned font is missing or changed. It needs no device: run get-system-fonts.sh once
# with the emulator up.
set -uo pipefail
cd "$(dirname "$0")/.."

FIT_ONLY=false
case "$#:${1-}" in
    0:) ;;
    1:--fit-only) FIT_ONLY=true ;;
    *) echo "face-gate.sh: usage: scripts/face-gate.sh [--fit-only]" >&2; exit 2 ;;
esac

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
if ! $FIT_ONLY; then check "validate.sh" bash tools/watchface/validate.sh; fi

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
if $FIT_ONLY; then
    echo "face-gate.sh: PASS (--fit-only: the generators and the fit tools; no face build, no validators)"
else
    echo "face-gate.sh: PASS"
fi
