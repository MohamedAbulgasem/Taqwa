#!/usr/bin/env bash
# Chooses the face's style or colour in the watch's face editor on emulator-5558, as a wearer does:
#
#   tools/watchface/choose.sh style arch|ring|sun|moon
#   tools/watchface/choose.sh accent amber|pearl|sage|night
#   tools/watchface/choose.sh style sun accent sage     # several, in turn
#   tools/watchface/choose.sh left battery right steps  # the wearer's slots, from the system's
#                                                       # chooser (editor.py SOURCES names them)
#
# The editor's controls are found by their accessibility text (editor.py), so a change in where
# the editor draws them does not break it. The choice stays with the face's saved instance, through
# new APKs and restarts, until it is chosen again.
source "$(dirname "$0")/lib.sh"
need_emulator
if [ "$#" -eq 0 ] || [ $(( $# % 2 )) -ne 0 ]; then
    echo "usage: $0 style <arch|ring|sun|moon> | accent <amber|pearl|sage|night> | left|right <source, editor.py SOURCES> [...]" >&2
    exit 2
fi
while [ "$#" -gt 0 ]; do
    ANDROID_HOME="$SDK" python3 -I "$HERE/editor.py" "$1" "$2"
    shift 2
done
