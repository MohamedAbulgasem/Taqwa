#!/usr/bin/env bash
# Stores the sample feed for the day of a time in Bengali, on emulator-5558, and has the face bind its
# slots again: the prayer names (the phone's values-bn strings, যোহর and on Fridays জুমা), plan 2's
# drafted countdown phrases, the dates and the weekday as a bn-BD phone writes them, in Bengali
# digits or Western ones. The watch app's debug build has no Bengali sample, so this is how the
# Bengali face is looked at until the phone sends one (as arabic.sh is for Arabic):
#
#   tools/watchface/bengali.sh                  # Bengali digits, the mock day
#   tools/watchface/bengali.sh latn 08:30       # Western digits
#   KEEP_FEED=1 tools/watchface/shoot.sh /tmp/shots moon-bn 08:30:30   # shoot it without the sample
#
# The Hijri date is the day and month alone (Ruling 24), "৬ জমাদিউল আউয়াল" on the mock day. Like
# arabic.sh, it writes the debug build's own files/feed.json (run-as), stops the watch app so that
# its store reads the file again, and has the face bind its slots again (lib.sh rebind_face), so
# that it asks its sources for their data; it installs nothing. Set the clock first (tools/watchface/clock.sh): a source answers from the
# time it is asked at.
source "$(dirname "$0")/lib.sh"
need_emulator
DIGITS="${1:-beng}"; AT="$(epoch_of "${2:-14:58}")"
case "$DIGITS" in beng|latn) ;; *) echo "usage: $0 [beng|latn] [HH:MM]" >&2; exit 2 ;; esac

store_feed "$AT"
sleep 1
WORK="$(mktemp -d "${TMPDIR:-/tmp}/taqwa-bengali.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
wadb shell run-as "$APP_PACKAGE" cat files/feed.json > "$WORK/sample.json"
python3 -I -c '
import json, sys
with open(sys.argv[1], encoding="utf-8") as f:
    feed = json.load(f)
beng = sys.argv[2] == "beng"
digits = (lambda s: s.translate(str.maketrans("0123456789", "০১২৩৪৫৬৭৮৯"))) if beng else (lambda s: s)
names = {"fajr": "ফজর", "sunrise": "সূর্যোদয়", "dhuhr": "যোহর", "asr": "আসর", "maghrib": "মাগরিব", "isha": "এশা"}
weekdays = {"SUN": "রবি", "MON": "সোম", "TUE": "মঙ্গল", "WED": "বুধ", "THU": "বৃহস্পতি", "FRI": "শুক্র", "SAT": "শনি"}
months = {"OCT": "অক্টো", "NOV": "নভে"}
hijri_months = {"Jumada al-Ula": "জমাদিউল আউয়াল", "Jumada al-Akhirah": "জমাদিউস সানি"}
ui = feed["ui"]
ui.update(language="bn", rtl=False, digitZero="০" if beng else "0", am="AM", pm="PM", nextIn="%1$s পর্যন্ত বাকি")
ui["countdown"] = {"hoursMinutes": "%1$sঘঃ %2$sমিঃ পরে", "hours": "%1$sঘঃ পরে", "minutes": "%1$sমিঃ পরে",
                   "underAMinute": "এক মিনিটের কম সময়ে"}
for day in feed["days"]:
    day["names"] = dict(names)
    if day["weekday"] == "FRI":
        day["names"]["dhuhr"] = "জুমা"
    wd, num, mon = day["gregorian"].split(" ")
    day["weekday"] = weekdays[wd]
    day["dayNumber"] = digits(num)
    day["gregorian"] = digits("%s %s %s" % (weekdays[wd], num, months[mon]))
    parts = day["hijri"].split(" ")
    month = hijri_months[" ".join(parts[1:-1])]
    day["hijri"] = digits("%s %s %s" % (parts[0], month, parts[-1]))
    day["hijriDayMonth"] = digits("%s %s" % (parts[0], month))
    day["clock"] = {k: digits(v) for k, v in day["clock"].items()}
with open(sys.argv[3], "w", encoding="utf-8") as f:
    f.write(json.dumps(feed, ensure_ascii=False, separators=(",", ":")))
' "$WORK/sample.json" "$DIGITS" "$WORK/feed.json"
wadb shell run-as "$APP_PACKAGE" sh -c "'cat > files/feed.json'" < "$WORK/feed.json"
wadb shell am force-stop "$APP_PACKAGE"
rebind_face
sleep 3
wadb shell input keyevent KEYCODE_WAKEUP
echo "stored: the sample for the day of ${2:-14:58} in Bengali ($DIGITS digits); the face was bound again"
