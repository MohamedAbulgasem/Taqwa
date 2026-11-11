"""For the Lead (P2.2 review round 3): a size rule for Arch's right-to-left Gregorian line that tests
the month as well as the length. It is NOT a table by length, which Ruling 51 item 1 asks for, so the
face does not take it; it is measured here so the Lead can weigh it beside r51/tables.txt.

The facts it rests on (greg-fails.txt, r51/bylen.txt): at 14 units the only one-word month under 7 px
from Arch's lit stroke is August as ar-SA, ar-EG, ar-LY and ar-AE write it (every Thursday, and the
Saturdays from the 10th), and 19 or more characters are exactly the Levantine and Iraqi two-word
months. Each rule is measured with date-fit.py's own model (its box bound, then row by row near
7.5 px) at each size's own box and baseline, at both of Arch's places (wearer's slots empty and
filled), over every right-to-left Gregorian form in phone-forms.json, as date-fit.py does; then the
dates of 2027 each size draws, by tag (its first digit set), and the overnight size changes, as
r51/tables.py counts them. The built table is measured the same way first, as a check on the method.

    python3 -I monthrule.py <repo> <dir holding NotoSansBengaliUI-VF.ttf>
"""
import collections, datetime, importlib.util, json, os, sys
REPO, fonts = sys.argv[1], sys.argv[2]
spec = importlib.util.spec_from_file_location("date_fit", os.path.join(REPO, "tools/watchface/date-fit.py"))
D = importlib.util.module_from_spec(spec); spec.loader.exec_module(D)
K = D.K
locales = json.load(open(os.path.join(REPO, "tools/watchface/phone-forms.json"), encoding="utf-8"))["locales"]
AUG = locales["ar-SA"]["months"][7]
assert all(locales[t]["months"][7] == AUG for t in ("ar-EG", "ar-LY", "ar-AE"))
texts = sorted({"%s %s %s" % (wd, D.in_set(day, ds), m) for loc in locales.values() if loc["rtl"]
                for ds in loc["digitSets"] for wd in loc["weekdays"] for m in loc["months"] for day in range(1, 32)})
LINES = {u: D.Line("greg", True, fonts, u * K) for u in (14, 13, 11.25)}
SAMPLE = "%s %s %s" % (locales["ar-SA"]["weekdays"][0], D.in_set(18, locales["ar-SA"]["digitSets"][0]),
                       locales["ar-SA"]["months"][9])


def built(t):
    return 14 if len(t) <= 13 else 13 if len(t) <= 18 else 11.25


RULES = {
    "3 steps (built), by length: (13, 14u), (18, 13u), (rest, 11.25u)": built,
    "A: 19+ chars 11.25u; else August (%s) 13u; else 14u" % AUG:
        lambda t: 11.25 if len(t) >= 19 else 13 if t.endswith(" " + AUG) else 14,
    "B: 19+ chars 11.25u; else August of 14+ chars 13u; else 14u":
        lambda t: 11.25 if len(t) >= 19 else 13 if t.endswith(" " + AUG) and len(t) >= 14 else 14,
}
year = [datetime.date(2027, 1, 1) + datetime.timedelta(d) for d in range(365)]
for name, rule in RULES.items():
    worst, under = {}, 0
    for t in texts:
        u = rule(t)
        line = LINES[u]
        c = None
        for base in sorted({line.baselines[("arch", comps)] for comps in (False, True)}):
            r = D.box_bound(t, line, base)
            if r < 7.5:
                r = D.exact(t, line, base)
            c = r if c is None else min(c, r)
        under += c < 7
        old = worst.get(u, (99.0, "", 0))
        worst[u] = (min(old[0], c), t if c < old[0] else old[1], old[2] + 1)
    print("%s: %d forms under 7 px; the sample %s at %g units" % (name, under, SAMPLE, rule(SAMPLE)))
    for u in sorted(worst, reverse=True):
        c, ct, n = worst[u]
        print("  %5.2f u  %6d forms; least room %5.1f px (%s)" % (u, n, c, ct))
    for tag, loc in locales.items():
        if not loc["rtl"]:
            continue
        ds = loc["digitSets"][0]
        sizes = [rule("%s %s %s" % (loc["weekdays"][(d.weekday() + 1) % 7], D.in_set(d.day, ds), loc["months"][d.month - 1]))
                 for d in year]
        c = collections.Counter(sizes)
        print("    %-6s %s;  size changes overnight %3d times" % (
            tag, "  ".join("%5.2f u: %3d days" % (u, c[u]) for u in sorted(c, reverse=True)),
            sum(a != b for a, b in zip(sizes, sizes[1:]))))
    sys.stdout.flush()
