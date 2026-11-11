"""For Ruling 51 item 1: the least room to Arch's lit stroke of every right-to-left Gregorian form in
phone-forms.json, grouped by its length in characters (as the face's textLength() counts), with the
line drawn at sizes from 14 to 11 units, at the 14-unit line's baseline. date-fit.py's measure: its
box bound, then row by row near 7 px. tables.py then measures the chosen tables at each step's own
box and baseline.

    python3 -I bylen.py <repo> <dir holding NotoSansBengaliUI-VF.ttf>
"""
import importlib.util, json, os, sys, collections
REPO, fonts = sys.argv[1], sys.argv[2]
spec = importlib.util.spec_from_file_location("date_fit", os.path.join(REPO, "tools/watchface/date-fit.py"))
D = importlib.util.module_from_spec(spec); spec.loader.exec_module(D)
locales = json.load(open(os.path.join(REPO, "tools/watchface/phone-forms.json"), encoding="utf-8"))["locales"]
texts = sorted({"%s %s %s" % (wd, D.in_set(day, ds), m) for loc in locales.values() if loc["rtl"]
                for ds in loc["digitSets"] for wd in loc["weekdays"] for m in loc["months"] for day in range(1, 32)})
line = D.Line("greg", True, fonts)
base = line.baselines[("arch", False)]
bylen = collections.defaultdict(list)
for t in texts: bylen[len(t)].append(t)
print({n: len(v) for n, v in sorted(bylen.items())})
for units in [14, 13.75, 13.5, 13.25, 13, 12.75, 12.5, 12.25, 12, 11.75, 11.5, 11.25, 11]:
    line.size = units * D.K
    row = []
    for n, ts in sorted(bylen.items()):
        worst = (99, "")
        for t in ts:
            c = D.box_bound(t, line, base)
            if c < 7.5:
                c = D.exact(t, line, base)
            worst = min(worst, (c, t))
        row.append("%d:%.1f" % (n, worst[0]))
    print("%5.2f u  " % units + "  ".join(row), flush=True)
