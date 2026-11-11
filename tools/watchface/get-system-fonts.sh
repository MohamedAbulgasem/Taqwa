#!/usr/bin/env bash
# The watch's own fonts the fit tools measure with, pinned by SHA-256:
#
#   tools/watchface/get-system-fonts.sh          # pulls any missing one from emulator-5558, checks each
#   tools/watchface/get-system-fonts.sh --check  # checks the cache alone, and touches no device
#
# The face bundles Manrope and Noto Sans Arabic (watchface/src/main/res/font), but a Bengali run is
# drawn in the watch's own Noto Sans Bengali UI, which the face does not bundle. hijri-fit.py,
# window-fit.py, moon-fit.py and date-fit.py read it with fonts=<dir>, and were calibrated on the
# copy in emulator-5558's /system/fonts (Taqwa_Wear_OS_6), so the copy they measure with is that one,
# and no other. This keeps it in ~/.cache/taqwa-watchface-tools/fonts (TAQWA_SYSTEM_FONTS), never in
# the repository, and checks each file against the SHA-256 written below. scripts/face-gate.sh runs
# --check, and refuses to start while a font is missing or changed.
#
# A pull takes the emulator (lib.sh's need_emulator: emulator-5558, the Taqwa AVD), so take the
# wear-5558 lock for it; a pull changes nothing on the watch. A pulled file that is not the pinned one
# is not kept: the emulator's font changed, and the fit tools must be measured again on the new one
# before its hash is pinned here.
#
# Exits 0 when every font is in the cache and pinned, 1 otherwise, 2 on a wrong argument.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
DEST="${TAQWA_SYSTEM_FONTS:-$HOME/.cache/taqwa-watchface-tools/fonts}"

# name, its SHA-256 (emulator-5558's /system/fonts on 25 October 2026, the copy plan 2's fit reports
# measured: docs/superpowers/reports/2026-10-23-phone-forms-fit.md, "SHA-256 2df70a74…")
FONTS=(
    "NotoSansBengaliUI-VF.ttf 2df70a74abd7c41da37684d212700bae874c2e10b0e432120c3fc6f7cdfdd621"
)

CHECK_ONLY=0
case "${1:-}" in
    "") ;;
    --check) CHECK_ONLY=1 ;;
    *) echo "usage: $0 [--check]" >&2; exit 2 ;;
esac

sha() { shasum -a 256 "$1" | cut -d' ' -f1; }

status=0
for entry in "${FONTS[@]}"; do
    name="${entry% *}"
    pinned="${entry#* }"
    file="$DEST/$name"
    if [ -f "$file" ] && [ "$(sha "$file")" = "$pinned" ]; then
        echo "$name: pinned ($pinned)"
        continue
    fi
    if [ -f "$file" ]; then
        what="changed: $file has SHA-256 $(sha "$file"), not the pinned $pinned"
    else
        what="missing: $file"
    fi
    if [ "$CHECK_ONLY" -eq 1 ]; then
        echo "$name: $what (tools/watchface/get-system-fonts.sh pulls it with emulator-5558 up)" >&2
        status=1
        continue
    fi
    # shellcheck source=lib.sh
    source "$HERE/lib.sh"
    need_emulator
    mkdir -p "$DEST"
    rm -f "$file.part"
    wadb pull "/system/fonts/$name" "$file.part" > /dev/null
    got="$(sha "$file.part")"
    if [ "$got" != "$pinned" ]; then
        rm -f "$file.part"
        echo "$name: changed on $SERIAL: SHA-256 $got, not the pinned $pinned; not kept." \
            "Measure the fit tools on it again before pinning its hash in $0" >&2
        status=1
        continue
    fi
    mv "$file.part" "$file"
    echo "$name: pulled from $SERIAL, pinned ($pinned)"
done
exit "$status"
