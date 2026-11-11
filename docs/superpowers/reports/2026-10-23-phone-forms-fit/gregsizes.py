"""For the Lead's ruling on Arch's Arabic Gregorian line (plan 2 Task 2's finding 1): the least room
to Arch's lit stroke over every right-to-left Gregorian form the phones send (phone-forms.json), with
the line drawn at each of a few sizes in place of face-centre.py's 31.5 px (14 units), the baseline
kept, and the widest form's width. date-fit.py's measure: its box bound, then row by row near 7 px.

    python3 -I gregsizes.py <repo> <dir holding NotoSansBengaliUI-VF.ttf>
"""
import importlib.util, json, os, sys
REPO, fonts = sys.argv[1], sys.argv[2]
spec = importlib.util.spec_from_file_location("date_fit", os.path.join(REPO, "tools/watchface/date-fit.py"))
D = importlib.util.module_from_spec(spec); spec.loader.exec_module(D)
locales = json.load(open(os.path.join(REPO, "tools/watchface/phone-forms.json"), encoding="utf-8"))["locales"]
texts = sorted({"%s %s %s" % (wd, D.in_set(day, ds), m) for loc in locales.values() if loc["rtl"]
                for ds in loc["digitSets"] for wd in loc["weekdays"] for m in loc["months"] for day in range(1, 32)})
line = D.Line("greg", True, fonts)
base = line.baselines[("arch", False)]
print("%d right-to-left forms, at Arch's baseline %.2f px" % (len(texts), base))
for units in (14, 13, 12.5, 12, 11.5, 11):
    line.size = units * D.K
    worst, wide, under = (99, ""), (0, ""), 0
    for t in texts:
        c = D.box_bound(t, line, base)
        if c < 7.5:
            c = D.exact(t, line, base)
        under += c < 7
        worst = min(worst, (c, t))
        wide = max(wide, (D.layout(t, line)[0], t))
    print("  %5.2f px (%4.1f units): least room %5.1f px (%s), %4d forms under 7 px; widest %.1f px (%s)" % (
        line.size, units, worst[0], worst[1], under, wide[0], wide[1]))
