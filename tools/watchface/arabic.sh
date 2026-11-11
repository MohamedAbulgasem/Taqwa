#!/usr/bin/env bash
# Stores the sample feed for the day of a time in Arabic, on emulator-5558, and has the face bind its
# slots again: the prayer names, the countdown's phrases, the dates and the weekday as faces.js
# writes them in Arabic, with the feed's rtl flag set, in Libya's Western digits or Saudi Arabia's
# Arabic-Indic ones. The watch app's debug build has only the English sample, so this is how the
# Arabic face is looked at until it has an Arabic one:
#
#   tools/watchface/arabic.sh                # Libya (ar): Arabic words, Western digits, mock day
#   tools/watchface/arabic.sh sa 14:58       # Saudi Arabia (ar-SA): Arabic-Indic digits
#   KEEP_FEED=1 tools/watchface/shoot.sh /tmp/shots moon-ar 14:58   # shoot it without the sample
#
# The Hijri date is the day and month alone (Ruling 24), "6 جمادى الأولى" on the mock day. Like
# hijri.sh, it writes the debug build's own files/feed.json (run-as), stops the watch app so that
# its store reads the file again, and has the face bind its slots again (lib.sh rebind_face), so
# that it asks its sources for their data; it installs nothing. Set the clock first (tools/watchface/clock.sh): a source answers from the time it is asked at.
source "$(dirname "$0")/lib.sh"
need_emulator
REGION="${1:-ly}"; AT="$(epoch_of "${2:-14:58}")"
case "$REGION" in ly|sa) ;; *) echo "usage: $0 [ly|sa] [HH:MM]" >&2; exit 2 ;; esac

store_feed "$AT"
sleep 1
WORK="$(mktemp -d "${TMPDIR:-/tmp}/taqwa-arabic.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
wadb shell run-as "$APP_PACKAGE" cat files/feed.json > "$WORK/sample.json"
python3 -I -c '
import json, sys
with open(sys.argv[1], encoding="utf-8") as f:
    feed = json.load(f)
indic = sys.argv[2] == "sa"
digits = (lambda s: s.translate(str.maketrans("0123456789", "٠١٢٣٤٥٦٧٨٩"))) if indic else (lambda s: s)
names = {"fajr": "الفجر", "sunrise": "الشروق", "dhuhr": "الظهر", "asr": "العصر", "maghrib": "المغرب", "isha": "العشاء"}
weekdays = {"SUN": "الأحد", "MON": "الإثنين", "TUE": "الثلاثاء", "WED": "الأربعاء", "THU": "الخميس", "FRI": "الجمعة", "SAT": "السبت"}
months = {"OCT": "أكتوبر", "NOV": "نوفمبر"}
hijri_months = {"Jumada al-Ula": "جمادى الأولى", "Jumada al-Akhirah": "جمادى الآخرة"}
ui = feed["ui"]
ui.update(language="ar", rtl=True, digitZero="٠" if indic else "0", am="ص", pm="م", nextIn="متبقٍّ على %1$s")
ui["countdown"] = {"hoursMinutes": "بعد %1$s س %2$s د", "hours": "بعد %1$s س", "minutes": "بعد %1$s د", "underAMinute": "بعد أقل من دقيقة"}
for day in feed["days"]:
    day["names"] = dict(names)
    if day["weekday"] == "FRI":
        day["names"]["dhuhr"] = "الجمعة"
    wd, num, mon = day["gregorian"].split(" ")
    day["weekday"] = weekdays[wd]
    day["dayNumber"] = digits(num)
    day["gregorian"] = digits("%s %s %s" % (weekdays[wd], num, months[mon]))
    parts = day["hijri"].split(" ")
    day["hijri"] = digits("%s %s" % (parts[0], hijri_months[" ".join(parts[1:-1])]))
    day["clock"] = {k: digits(v) for k, v in day["clock"].items()}
with open(sys.argv[3], "w", encoding="utf-8") as f:
    f.write(json.dumps(feed, ensure_ascii=False, separators=(",", ":")))
' "$WORK/sample.json" "$REGION" "$WORK/feed.json"
wadb shell run-as "$APP_PACKAGE" sh -c "'cat > files/feed.json'" < "$WORK/feed.json"
wadb shell am force-stop "$APP_PACKAGE"
rebind_face
sleep 3
wadb shell input keyevent KEYCODE_WAKEUP
echo "stored: the sample for the day of ${2:-14:58} in Arabic ($REGION); the face was bound again"
