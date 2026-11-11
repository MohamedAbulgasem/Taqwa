#!/usr/bin/env bash
# The emulator's (emulator-5558) recent logcat lines that match a pattern (extended regex, any
# case), the latest last. The face runtime logs as DWF:...; the watch app as world.taqwa...
#
#   tools/watchface/log.sh 'DWF|taqwa' [lines of logcat to scan, default 1500]
source "$(dirname "$0")/lib.sh"
need_emulator
wadb logcat -d -t "${2:-1500}" | grep -i -E "${1:-DWF|taqwa}" | tail -"${TAIL:-60}"
