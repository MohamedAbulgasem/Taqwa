#!/usr/bin/env bash
# Every Step 1 shot against its drawing, words masked (erratum p3 #3); prints a TSV.
source "$(dirname "$0")/lib.sh"
R=$DRIVERS/../ref
C=$T/compare.py
printf 'watch\tfeed\tshot\tdrawing\thow\tdiffering px\n'
for W in en ar; do for F in en en12 ar-ly12 ar-sa; do
    D=$P/shots/matrix/watch-$W/$F; rtl=""; mir=""; case $F in ar-*) rtl="-rtl"; mir="mirror" ;; esac
    row() {  # shot ref [extra compare args]
        local s=$1 r=$2; shift 2
        local out; out=$(python3 -I "$C" "$R/$r.png" "$D/$s.png" mask="$R/$r-mask.png" "$@" 2>&1 | tail -1)
        printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$W" "$F" "$s" "$r" "masked $*" "$out"
    }
    for t in 1458 2230 0410; do
        row today-$t today-$t$rtl
        row today-$t-list list-$t $mir
    done
    for s in tasbeeh-0 tasbeeh-27 tasbeeh-33 tasbeeh-33-flash tasbeeh-100-flash tasbeeh-0-round2; do row $s $s; done
    row tasbeeh-list tasbeeh-list $mir
    row qibla-searching qibla-searching
    row qibla-aligned qibla-aligned
    row tile-1458 tile-1458 $mir
    row tile-0640 tile-0640 $mir
done; done
