"""London Unified Prayer Timetable, as London Prayer Times republishes the East London Mosque's
table ("completely free for all use", ruling R33). Its API needs a key issued by hand: with one
(`LPT_API_KEY` in <monitor>/keys.env on the Mac, never in the repository; the repository secret in
the cloud) this reads the coming year month by month; without one it does nothing and the horizon
reminds from 1 December that the next year is wanted. The key travels in the query string and is
masked wherever a URL is written (common.redact). The API's shape here follows its published
parameters (year, month, format=json, 24hours=true); it has not been exercised without a key, so
the first run with one may need a fix: a month whose response lacks a field is an error, never a
silent "-", and a year the API does not have yet stops after its first month."""
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


def month_rows(body):
    """{date: [7 times]} of one month's response; a day lacking a field is an error (review Q4)."""
    times = find_times(json.loads(body.decode("utf-8", "replace")))
    if not times:
        raise FetchError("no dated times in the response")
    rows = {}
    for date, v in times.items():
        missing = [n for n in NAMES if not v.get(n)]
        if missing:
            raise FetchError(f"{date}: no {', '.join(missing)} in the response (fields: {', '.join(sorted(v))})")
        rows[date] = [v[n] for n in NAMES]
    return rows


def fetch(ctx):
    key = ctx.keys_env().get("LPT_API_KEY")
    if not key:
        ctx.note("no LPT_API_KEY (keys.env or the environment): London Unified's next year is fetched by hand (the horizon reminds from 1 December)")
        return []
    year = ctx.today.year + 1 if ctx.today.month >= 10 else ctx.today.year
    t = Table(f"elm-{year}", f"LUPT {year}", None, None, "Europe/London", "GB", "F+E S D As M I Ah", entry="gb.london.lupt/gb.london.lupt", school="-",
              source_line=f"London Prayer Times API {API} (the East London Mosque's LUPT table, free for all use), year {year}")
    for month in range(1, 13):
        url = f"{API}?format=json&key={key}&year={year}&month={month}&24hours=true"
        try:
            body = ctx.http.get(url)
            for date, times in month_rows(body).items():
                t.add(date, times)
            t.raw.append((f"lpt-{year}-{month:02d}.json", body))
        except (FetchError, ValueError) as e:
            if month == 1 and str(e).startswith("HTTP 4"):
                ctx.note(f"{year} is not on the API yet ({e}); nothing fetched")
                return []
            ctx.error(f"{year}-{month:02d}: {e}")
    t.merge = False
    return [t] if t.rows else []
