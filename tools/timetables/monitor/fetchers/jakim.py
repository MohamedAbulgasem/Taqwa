"""Malaysia, JAKIM e-solat: the year table of every zone of e-solat's list, sixty in all (my-jakim.tsv: the
first thirteen, the 37 of the zone round of 13 Nov 2026 that hold a city of the app's list, and the ten
its review round added, which hold none but whose places the app serves) from e-solat's own API, and from
October the year ahead, from e-solat itself too. The current year is a plain GET (`period=year`). The year
ahead is one `period=duration` request a zone and that request is an HTTP POST: the date-range form
e-solat's own site posts, datestart and dateend in the body, no key, no login (GET answers only the
current year; `ctx.http.get` sends a POST whenever it is given `data`). The public mirror
api.waktusolat.app (which copies e-solat exactly, plain GETs) is the fallback when e-solat cannot be read.
A zone whose year e-solat has not loaded yet (`NO_RECORD!`, the mirror's 404 "No data found") is a note,
never an error. The fast begins at Subuh (e-solat's imsak is a precaution), so the Fajr column is also
the end of eating (F+E), as my-jakim.tsv reads it since the audit of 3 October 2026.

JAKIM's own data carries slips now and then (PLS01's 2025-03-01 Fajr printed 12 hours off; WLY02's 2027
Maghrib in the morning on 20-25 May and its Asr an hour early on 10 January, e-solat's own figures,
12 Nov 2026). `slips` finds them, and the cell is written '-' with a note naming the day and column,
never a time: a morning column after noon or an afternoon one before it, a cell about one or twelve
hours off its column's neighbours, and a day whose cells still do not run in the day's order (left
out whole: every cell '-', so an earlier capture's row for it is not kept by the merge).

A day that is only OUT OF LINE is never left out (ruling R228 as its review round of 13 Nov 2026 left
it): two or more of its cells stand OUT_OF_LINE minutes or more off their columns' neighbours, yet every
cell is a possible time and the day runs in order. Such a day may be a slip or may be what the zone's
mosques follow, and a table cannot say which; leaving it out would list a LATER print away, which is how
an early start is excused. So the day is kept as printed, the gate reads it like any other day, and the
monitor raises it as an error for a human to rule, with the direction and the minutes (never a time).
The one run known is SBH09's 28-31 March 2026, printed LATER than the days either side (Subuh and Syuruk
10-11 min, Zohor 6-7, Asar 19-20, Maghrib 3 min later, Isyak in line; e-solat's own figures, 13 Nov
2026): the engine holds every start of those four days to the print, never before it (JakimZones'
`printedLater`), so they are listed in HELD_AS_PRINTED and are a note, not an error. Across every table
held on 13 Nov 2026 (sixty zones' 2026, forty-three zones' 2027) no other day is out of line; a table
that steps for good (Kedah's shift by about
4 min for a fortnight) stays far inside it. The check holds no time of its own: it compares the table
with itself.

The printed imsak (ruling R284, the second audit's G25): e-solat prints an imsak beside each Subuh, a precaution
(Subuh - 10 on most days held; SBH02 printed 20 min in March 2026), and the app shows one. Each table's imsak is kept as its own capture,
`<zone>-<year>-imsak`, two columns: the Subuh as the day's table holds it (a slip there left '-') and the imsak as
printed, the layout of the archive's hand read-backs (archive/tables/imsak/my-jakim/), checked as `- Im` (Perlis's as
`- E`, as my-jakim.tsv reads it). An imsak is kept as printed whatever its distance from Subuh: one printed earlier
than usual (SBH02's 20 min of March 2026) is an earlier end the app may stand after, so it is never left out (R228's
reasoning, for an end); one more than IMSAK_WIDE minutes before its Subuh is also raised for a human to rule (days
and minutes, never a time), as an error while the day is ahead and a note once it is past. A cell that is not a time,
or after its Subuh, is '-' and raised the same way (common.raise_imsak). One at its Subuh is kept: Perlis prints
no precaution (my-jakim.tsv), so its imsak is its Subuh on most days, and an end at the start is never late."""
import datetime as dt
import json

from common import FetchError, Table, add_all, norm_time, raise_imsak

SOURCE = "my-jakim"
ESOLAT = "https://www.e-solat.gov.my/index.php?r=esolatApi/takwimsolat&period=year&zone="
ESOLAT_DURATION = "https://www.e-solat.gov.my/index.php?r=esolatApi/takwimsolat&period=duration&zone="
MIRROR = "https://api.waktusolat.app/v2/solat/"
MONTHS = {"jan": 1, "feb": 2, "mac": 3, "mar": 3, "apr": 4, "mei": 5, "may": 5, "jun": 6, "jul": 7,
          "ogo": 8, "aug": 8, "sep": 9, "okt": 10, "oct": 10, "nov": 11, "dis": 12, "dec": 12}
ZONES = [
    ("WLY01", "Kuala Lumpur", "Asia/Kuala_Lumpur"), ("SGR01", "Shah Alam", "Asia/Kuala_Lumpur"),
    ("JHR01", "Pulau Aur", "Asia/Kuala_Lumpur"), ("JHR02", "Johor Bahru", "Asia/Kuala_Lumpur"),
    ("KTN01", "Kota Bharu", "Asia/Kuala_Lumpur"), ("PHG01", "Pulau Tioman", "Asia/Kuala_Lumpur"),
    ("PHG06", "Cameron Highlands", "Asia/Kuala_Lumpur"), ("PLS01", "Kangar", "Asia/Kuala_Lumpur"),
    ("PNG01", "George Town", "Asia/Kuala_Lumpur"), ("SBH01", "Sandakan", "Asia/Kuching"),
    ("SBH07", "Kota Kinabalu", "Asia/Kuching"), ("SWK08", "Kuching", "Asia/Kuching"), ("WLY02", "Labuan", "Asia/Kuching"),
    # The zone round of 13 Nov 2026 (ruling R225): every other zone that holds a city of the app's list.
    ("JHR03", "Kluang", "Asia/Kuala_Lumpur"), ("JHR04", "Muar", "Asia/Kuala_Lumpur"),
    ("KDH01", "Alor Setar", "Asia/Kuala_Lumpur"), ("KDH02", "Kota Kuala Muda", "Asia/Kuala_Lumpur"),
    ("KDH03", "Sik", "Asia/Kuala_Lumpur"), ("KDH04", "Kuala Ketil", "Asia/Kuala_Lumpur"),
    ("KDH05", "Kulim", "Asia/Kuala_Lumpur"), ("KDH06", "Kuah", "Asia/Kuala_Lumpur"),
    ("KTN02", "Gua Musang", "Asia/Kuala_Lumpur"), ("MLK01", "Malacca", "Asia/Kuala_Lumpur"),
    ("NGS01", "Rompin", "Asia/Kuala_Lumpur"), ("NGS02", "Rembau", "Asia/Kuala_Lumpur"),
    ("NGS03", "Seremban", "Asia/Kuala_Lumpur"), ("PHG02", "Kuantan", "Asia/Kuala_Lumpur"),
    ("PHG03", "Maran", "Asia/Kuala_Lumpur"), ("PHG04", "Bentong Town", "Asia/Kuala_Lumpur"),
    ("PRK01", "Tanjong Malim", "Asia/Kuala_Lumpur"), ("PRK02", "Ipoh", "Asia/Kuala_Lumpur"),
    ("PRK03", "Gerik", "Asia/Kuala_Lumpur"), ("PRK05", "Teluk Intan", "Asia/Kuala_Lumpur"),
    ("PRK06", "Taiping", "Asia/Kuala_Lumpur"), ("SBH03", "Lahad Datu", "Asia/Kuching"),
    ("SBH04", "Tawau", "Asia/Kuching"), ("SBH05", "Kudat", "Asia/Kuching"), ("SBH08", "Keningau", "Asia/Kuching"),
    ("SBH09", "Sipitang", "Asia/Kuching"), ("SGR02", "Ijok", "Asia/Kuala_Lumpur"),
    ("SGR03", "Kapar", "Asia/Kuala_Lumpur"), ("SWK01", "Limbang", "Asia/Kuching"), ("SWK02", "Miri", "Asia/Kuching"),
    ("SWK03", "Bintulu", "Asia/Kuching"), ("SWK04", "Sibu", "Asia/Kuching"), ("SWK05", "Sarikei", "Asia/Kuching"),
    ("SWK06", "Sri Aman", "Asia/Kuching"), ("SWK07", "Serian", "Asia/Kuching"),
    ("TRG01", "Kuala Terengganu", "Asia/Kuala_Lumpur"), ("TRG04", "Cukai", "Asia/Kuala_Lumpur"),
    # Its review round: the ten zones that hold no city of the app's list, so that no place in Malaysia is
    # served on a neighbouring zone's table while its own goes unread (each named by a place of its list).
    ("KDH07", "Gunung Jerai", "Asia/Kuala_Lumpur"), ("PHG05", "Bukit Tinggi", "Asia/Kuala_Lumpur"),
    ("PHG07", "Kuala Rompin", "Asia/Kuala_Lumpur"), ("PRK04", "Pulau Banding", "Asia/Kuala_Lumpur"),
    ("PRK07", "Bukit Larut", "Asia/Kuala_Lumpur"), ("SBH02", "Beluran", "Asia/Kuching"),
    ("SBH06", "Gunung Kinabalu", "Asia/Kuching"), ("SWK09", "Kampung Patarikan", "Asia/Kuching"),
    ("TRG02", "Jerteh", "Asia/Kuala_Lumpur"), ("TRG03", "Kuala Berang", "Asia/Kuala_Lumpur"),
]
KEYS = ("fajr", "syuruk", "dhuhr", "asr", "maghrib", "isha")
MIRROR_MARK = "(mirror)"  # a capture that may hold a republisher's days (MonitorIndex.kt's MIRROR_MARK, ruling R282)
IMSAK_WIDE = 15  # minutes before Subuh past which a printed imsak is raised for a human (e-solat prints 10)
IMSAK_COLUMNS = {"PLS01": "- E"}  # Perlis's printed imsak is its end of eating (my-jakim.tsv); every other zone's is Im
NAMES = ("Fajr", "Syuruk", "Dhuhr", "Asr", "Maghrib", "Isha")
UTC8 = dt.timezone(dt.timedelta(hours=8))
MORNING = (0, 1)        # columns before noon in Malaysia, whatever the day
AFTERNOON = (3, 4, 5)   # columns after it
NEIGHBOURS = 10         # days either side a cell is compared with (its column's median there)
OFF = 45                # minutes off that median that make a cell a slip (the year's drift over 10 days is far less)
OUT_OF_LINE = 8         # minutes off that median that, in two or more cells of a day, make the day out of line
# The out-of-line days whose starts the engine holds to the print (JakimZones' printedLater; JakimZoneCitiesTest
# holds the two lists equal): zone -> (first day, last day). Such a day is a note; any other out-of-line day is an error.
HELD_AS_PRINTED = {"SBH09": ("2026-03-28", "2026-03-31")}


class NotYet(FetchError):
    """e-solat (NO_RECORD!) or the mirror (404, "No data found") says the year is not loaded yet."""


def minutes(t):
    h, m = t.split(":")
    return int(h) * 60 + int(m)


def slips(rows):
    """{date: [6 'HH:MM']} -> ({date: [cells, slips '-']}, [notes], {dates left out, all '-'},
    {date out of line: what it is, kept as printed}). See the module's doc; a note names the day, the column
    and how far off it is, never a printed time."""
    out = {d: list(v) for d, v in rows.items()}
    notes = []
    out_of_line = {}
    for col in range(6):
        for d in sorted(out):
            cell = out[d][col]
            if cell == "-":
                continue
            m = minutes(cell)
            if col in MORNING and m >= 12 * 60:
                out[d][col] = "-"
                notes.append(f"{d} {NAMES[col]} printed after noon")
                continue
            if col in AFTERNOON and m < 12 * 60:
                out[d][col] = "-"
                notes.append(f"{d} {NAMES[col]} printed before noon")
                continue
    for col in range(6):
        values = {d: minutes(v[col]) for d, v in rows.items() if out[d][col] != "-"}
        for d in sorted(values):
            day = dt.date.fromisoformat(d)
            near = sorted(values[x] for x in (
                (day + dt.timedelta(days=k)).isoformat() for k in range(-NEIGHBOURS, NEIGHBOURS + 1) if k)
                if x in values)
            if len(near) < 4:
                continue
            median = near[len(near) // 2] if len(near) % 2 else (near[len(near) // 2 - 1] + near[len(near) // 2]) / 2
            off = values[d] - median
            if abs(off) >= OFF:
                out[d][col] = "-"
                hours = round(abs(off) / 60)
                notes.append(f"{d} {NAMES[col]} about {hours} hour{'s' if hours != 1 else ''} {'late' if off > 0 else 'early'} against its neighbours")
            elif abs(off) >= OUT_OF_LINE:
                out_of_line.setdefault(d, []).append((NAMES[col], off))
    dropped = set()
    for d in sorted(out):
        m = [minutes(c) for c in out[d] if c != "-"]
        if any(b <= a for a, b in zip(m, m[1:])):
            dropped.add(d)
            notes.append(f"{d} not in the day's order: left out")
    for d in dropped:
        # Written as six '-', not deleted: the live file merges captures, so a deleted day would keep an
        # earlier capture's row for it.
        out[d] = ["-"] * 6
    # Never left out: a later print dropped is an early start listed away (the module's doc).
    standing = {
        d: "printed " + ", ".join(f"{name} about {round(abs(off))} min {'later' if off > 0 else 'earlier'}" for name, off in cells)
           + " than the days either side"
        for d, cells in sorted(out_of_line.items()) if len(cells) >= 2 and d not in dropped}
    return out, notes, dropped, standing


def held_as_printed(zone, date):
    span = HELD_AS_PRINTED.get(zone)
    return span is not None and span[0] <= date <= span[1]


def parse_esolat(body):
    try:
        d = json.loads(body.decode("utf-8", "replace"))
    except ValueError:
        raise FetchError(f"e-solat's answer is not JSON ({len(body)} bytes)")
    if not isinstance(d, dict):
        raise FetchError("e-solat's answer is not an object")
    if "NO_RECORD" in str(d.get("status") or ""):
        raise NotYet(f"e-solat has no record for the request yet (status {str(d.get('status'))[:20]})")
    rows = {}
    for r in d.get("prayerTime") or []:
        dd, mm, yy = r["date"].split("-")
        date = dt.date(int(yy), MONTHS[mm.lower()[:3]], int(dd)).isoformat()
        rows[date] = [r[k] for k in KEYS]
    if not rows:
        raise FetchError("no prayerTime rows (" + str(d)[:120] + ")")
    return rows, d.get("serverTime")


def parse_imsak(body):
    """e-solat's imsak by date, from the same answer `parse_esolat` reads ({} when it prints none)."""
    try:
        d = json.loads(body.decode("utf-8", "replace"))
    except ValueError:
        return {}
    out = {}
    for r in (d.get("prayerTime") or []) if isinstance(d, dict) else []:
        if not isinstance(r, dict) or r.get("imsak") in (None, "", "-"):
            continue
        try:
            dd, mm, yy = r["date"].split("-")
            out[dt.date(int(yy), MONTHS[mm.lower()[:3]], int(dd)).isoformat()] = r["imsak"]
        except (KeyError, ValueError, AttributeError):
            continue
    return out


def imsak_table(ctx, t, printed, label, zone):
    """The printed imsak beside `t` (the day's table, slips already '-'): `<key>-imsak`, [Subuh, imsak] a day, or
    None when nothing is printed. Kept as printed (the module's doc); an unreadable cell, or one after its
    Subuh, is '-' and an error (one at it is kept); one more than IMSAK_WIDE minutes before it is an error and kept."""
    if not printed:
        return None
    columns = IMSAK_COLUMNS.get(zone, "- Im")
    base = t.name[:-len(MIRROR_MARK)].rstrip() if t.name.endswith(MIRROR_MARK) else t.name
    out = Table(t.key + "-imsak", f"{base} imsak" + (" " + MIRROR_MARK if base != t.name else ""), t.lat, t.lon, t.zone, t.cc, columns,
                entry=t.entry, source_line=t.source_line + "; its imsak column beside the Subuh")
    bad, wide = [], []
    for date in sorted(printed):
        if date not in t.rows:
            continue
        subuh = t.rows[date][0]
        try:
            imsak = norm_time(printed[date])
        except FetchError:
            bad.append(date)
            continue
        if imsak != "-" and subuh != "-":
            gap = minutes(subuh) - minutes(imsak)
            if gap < 0:
                bad.append(date)
                imsak = "-"
            elif gap > IMSAK_WIDE:
                wide.append((date, gap))
        out.rows[date] = [subuh, imsak]
    raise_imsak(ctx, label, bad, wide, IMSAK_WIDE)
    return out if any(r[1] != "-" for r in out.rows.values()) else None


def parse_mirror(body, year, month):
    try:
        d = json.loads(body.decode("utf-8", "replace"))
    except ValueError:
        raise FetchError(f"the mirror's answer is not JSON ({len(body)} bytes)")
    rows = {}
    for p in d.get("prayers") or []:
        date = dt.date(year, month, int(p["day"])).isoformat()
        rows[date] = [dt.datetime.fromtimestamp(p[k], UTC8).strftime("%H:%M") if p.get(k) else "-" for k in KEYS]
    if not rows:
        raise FetchError("no prayers rows")
    return rows


def checked(ctx, t, rows, label, zone):
    """`rows` (24-hour cells, normalised) through `slips` into `t`, each slip a note; the count added. An
    out-of-line day is kept as printed: a note when the engine holds it (HELD_AS_PRINTED), else an error
    for a human to rule (the module's doc).

    A cell that is not a time is an error, never a note (ruling R281, the second audit's G19): e-solat writes every
    cell the same way, so one it does not is a change of format, and a format change that left every cell unread
    once reported the zone 'ok' with no table. An answer with days of which none could be read is an error too."""
    normal = {}
    unread = []
    for date, times in rows.items():
        try:
            normal[date] = [norm_time(x) for x in times]
        except FetchError:
            unread.append(date)
    if unread:
        ctx.error(f"{label}: {len(unread)} of {len(rows)} days have a cell that is not a time (the first {min(unread)}): "
                  "e-solat's format may have changed; those days are not taken")
    clean, notes, _, standing = slips(normal)
    if notes:
        ctx.note(f"{label}: JAKIM's own slips left out ({len(notes)}): " + "; ".join(notes))
    known = [f"{d} {what}" for d, what in standing.items() if held_as_printed(zone, d)]
    new = [f"{d} {what}" for d, what in standing.items() if not held_as_printed(zone, d)]
    if known:
        ctx.note(f"{label}: out of line with the days either side, kept as printed and held by the engine ({len(known)}): " + "; ".join(known))
    if new:
        ctx.error(f"{label}: out of line with the days either side, kept as printed, for a human to rule ({len(new)}): " + "; ".join(new))
    added = add_all(ctx, t, clean, label)
    if rows and not added:
        ctx.error(f"{label}: no day of the answer could be read ({len(rows)} days in it)")
    return added


def year_ahead_esolat(ctx, zone, year):
    """(rows, body, url, serverTime) of `zone`'s whole `year` from e-solat (period=duration): an HTTP POST,
    the date-range form e-solat's own site posts (the module's doc). NotYet when e-solat has not loaded it;
    FetchError otherwise."""
    url = ESOLAT_DURATION + zone
    body = ctx.http.get(url, data={"datestart": f"{year}-01-01", "dateend": f"{year}-12-31"})
    rows, server = parse_esolat(body)
    rows = {d: v for d, v in rows.items() if d.startswith(str(year))}
    if not rows:
        raise FetchError(f"no {year} rows in e-solat's answer")
    return rows, body, url, server


def year_ahead_mirror(ctx, zone, year, t):
    """The mirror's twelve months of `zone`'s `year` into `t`, checked for slips as one year (a slip at a
    month's edge still has neighbours both sides); NotYet when its first month is 'No data found'."""
    rows = {}
    for month in range(1, 13):
        try:
            body = ctx.http.get(f"{MIRROR}{zone}?year={year}&month={month}")
            rows.update(parse_mirror(body, year, month))
            t.raw.append((f"mirror-{zone}-{year}-{month:02d}.json", body))
        except (FetchError, KeyError, ValueError) as e:
            if month == 1 and "HTTP 404" in str(e):
                raise NotYet(f"not on the mirror yet ({e})")
            ctx.error(f"{zone} {year}-{month:02d} (mirror): {e}")
    checked(ctx, t, rows, f"{zone} {year} (mirror)", zone)


def fetch(ctx):
    tables = []
    for zone, name, tz in ZONES:
        url = ESOLAT + zone
        try:
            body = ctx.http.get(url)
            rows, server = parse_esolat(body)
        except FetchError as e:
            ctx.error(f"{zone}: {e}")
            continue
        printed = parse_imsak(body)
        by_year = {}
        for date, times in rows.items():
            by_year.setdefault(date[:4], {})[date] = times
        for year, days in by_year.items():
            t = Table(f"{zone}-{year}", f"{name} {zone} {year}", None, None, tz, "MY", "F+E S D A M I", entry=f"my.jakim/{zone}",
                      source_line=f"JAKIM e-solat {url} (serverTime {server})", raw=[(f"esolat-{zone}-{year}.json", body)])
            if checked(ctx, t, days, f"{zone} {year}", zone):
                tables.append(t)
                imsak = imsak_table(ctx, t, {d: v for d, v in printed.items() if d[:4] == year}, f"{zone} {year} imsak", zone)
                if imsak is not None:
                    tables.append(imsak)
    if ctx.today.month < 10:
        return tables
    nxt = ctx.today.year + 1
    waiting = []
    for zone, name, tz in ZONES:
        # e-solat first, every run (one request a zone, so a correction JAKIM makes is seen); the mirror
        # only when e-solat cannot be read and the year is not held whole yet.
        try:
            rows, body, url, server = year_ahead_esolat(ctx, zone, nxt)
            t = Table(f"{zone}-{nxt}", f"{name} {zone} {nxt}", None, None, tz, "MY", "F+E S D A M I", entry=f"my.jakim/{zone}",
                      source_line=f"JAKIM e-solat {url} (POST, its date-range form, datestart {nxt}-01-01, dateend {nxt}-12-31; serverTime {server})",
                      raw=[(f"esolat-{zone}-{nxt}.json", body)])
            # A whole year from e-solat replaces what is held (ruling R282, the second audit's G18): a year read from the
            # mirror before is not merged into the authority's own, so the capture is e-solat's alone, day for day.
            # Whole counts the days taken, after `checked` (a day with an unread cell is not taken, and the held one stands).
            if checked(ctx, t, rows, f"{zone} {nxt}", zone):
                t.merge = len(t.rows) < (dt.date(nxt + 1, 1, 1) - dt.date(nxt, 1, 1)).days
                if t.merge and set(ctx.held(t.key)) - set(t.rows):
                    # Less than the year, merged over held days that may be the mirror's: the capture keeps the mark, so
                    # prove never lays a mirror's day on the authority (R185 waits for e-solat's whole year).
                    t.name += " " + MIRROR_MARK
                    ctx.note(f"{zone} {nxt}: e-solat printed {len(t.rows)} days of the year; merged over the days held, it keeps the mirror's mark")
                tables.append(t)
                imsak = imsak_table(ctx, t, parse_imsak(body), f"{zone} {nxt} imsak", zone)
                if imsak is not None:
                    imsak.merge = t.merge
                    tables.append(imsak)
            continue
        except NotYet:
            waiting.append(zone)
            continue
        except FetchError as e:
            if len(ctx.held(f"{zone}-{nxt}")) >= 365:
                ctx.note(f"{zone} {nxt}: e-solat could not be read ({e}); the year is held whole, so the mirror is not asked")
                continue
            ctx.note(f"{zone} {nxt}: e-solat could not be read ({e}); the mirror is asked instead")
        t = Table(f"{zone}-{nxt}", f"{name} {zone} {nxt} (mirror)", None, None, tz, "MY", "F+E S D A M I", entry=f"my.jakim/{zone}",
                  source_line=f"JAKIM e-solat via the public mirror {MIRROR}{zone}?year={nxt}&month=1..12 (times as Unix seconds, shown in UTC+8)")
        try:
            year_ahead_mirror(ctx, zone, nxt, t)
        except NotYet:
            waiting.append(zone)
            continue
        if t.rows:
            tables.append(t)
    if waiting:
        ctx.note(f"{nxt} not published yet for {', '.join(waiting)} (e-solat NO_RECORD! or the mirror's 404)")
    return tables
