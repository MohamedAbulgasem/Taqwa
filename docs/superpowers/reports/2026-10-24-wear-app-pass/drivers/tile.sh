#!/usr/bin/env bash
# The Tile at 14:58 and 06:40 for each feed on emulator-5558, found by the feed's city:
#   tile.sh <watch: en|ar> <feed>... (the Tile must have been added: add-tile)
source "$(dirname "$0")/lib.sh"
WATCH=$1; shift
for F in "$@"; do
    D=$P/shots/matrix/watch-$WATCH/$F; mkdir -p "$D"
    city="Cape Town"; case "$F" in ar-*) city="كيب تاون" ;; esac
    for t in 14:58 06:40; do
        tt=${t/:/}
        face; sleep 1.5
        clock "${t}:00"; store "$F" "$t"; sleep 22   # the system holds a Tile request for about 20 s after the last
        tile_shot "$D/tile-$tt.png" "$city" "$WATCH"
    done
done
face
