#!/usr/bin/env bash
# Stores the watch app's sample feed (Cape Town, from the day before) re-dated to the day of a
# time, on emulator-5558, through the debug receiver; the face's slots follow at once:
#
#   tools/watchface/feed.sh               # the mock day (14:58 on Sunday 18 October 2026)
#   tools/watchface/feed.sh 04:10         # the mock day too: the feed is dated by the day
#   tools/watchface/feed.sh <epoch s>     # the day of any instant
source "$(dirname "$0")/lib.sh"
need_emulator
store_feed "$(epoch_of "${1:-14:58}")"
echo "sample feed stored for the day of ${1:-14:58}"
