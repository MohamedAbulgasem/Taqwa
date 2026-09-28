"""London Unified Prayer Timetable, as London Prayer Times republishes the East London Mosque's
table ("completely free for all use", ruling R33). Its API needs a key issued by hand: with one in
<monitor>/keys.env (`LPT_API_KEY=…`, never in the repository) this reads the coming year month by
month; without one it does nothing and the horizon reminds from 1 December that the next year is
wanted. The API's shape here follows its published parameters (year, month, format=json,
24hours=true); it has not been exercised without a key, so the first run with one may need a fix."""
import json

from common import FetchError, Table

SOURCE = "gb-london-lupt"
API = "https://www.londonprayertimes.com/api/times/"
NAMES = ("fajr", "sunrise", "dhuhr", "asr", "magrib", "isha", "asr_2")


def find_times(obj):
    """A dict of yyyy-mm-dd -> {fajr, sunrise, …} anywhere in the response."""
    if isinstance(obj, dict):
        if obj and all(isinstance(k, str) and len(k) == 10 and k[4] == "-" and isinstance(v, dict) for k, v in obj.items()):
            return obj
        for v in obj.values():
            hit = find_times(v)
            if hit:
                return hit
    if isinstance(obj, list):
        for v in obj:
            hit = find_times(v)
            if hit:
                return hit
    return None


def fetch(ctx):
    key = ctx.keys_env().get("LPT_API_KEY")
    if not key:
        ctx.note("no LPT_API_KEY in keys.env: London Unified's next year is fetched by hand (the horizon reminds from 1 December)")
        return []
    year = ctx.today.year + 1 if ctx.today.month >= 10 else ctx.today.year
    t = Table(f"elm-{year}", f"LUPT {year}", None, None, "Europe/London", "GB", "F+E S D As M I Ah", entry="gb.london.lupt/gb.london.lupt", school="-",
              source_line=f"London Prayer Times API {API} (the East London Mosque's LUPT table, free for all use), year {year}")
    for month in range(1, 13):
        url = f"{API}?format=json&key={key}&year={year}&month={month}&24hours=true"
        try:
            body = ctx.http.get(url)
            times = find_times(json.loads(body.decode("utf-8", "replace")))
            if not times:
                raise FetchError("no dated times in the response")
            for date, v in times.items():
                t.add(date, [v.get(n) or "-" for n in NAMES])
            t.raw.append((f"lpt-{year}-{month:02d}.json", body))
        except (FetchError, ValueError) as e:
            ctx.error(f"{year}-{month:02d}: {e}")
    t.merge = False
    return [t] if t.rows else []
