"""Every Gregorian line that tools/watchface/date-fit.py finds under min= px (7) from Arch's lit stroke:
first by month and weekday (the least room in the group, the days it holds, and the tags and digit
sets that send it), then every form on its own line (its room, its tags and digit sets).

    python3 -I gregfails.py <repo> <dir holding NotoSansBengaliUI-VF.ttf> [<all forms out file>]
"""
import importlib.util, json, os, sys
REPO, fonts = sys.argv[1], sys.argv[2]
spec = importlib.util.spec_from_file_location("date_fit", os.path.join(REPO, "tools/watchface/date-fit.py"))
D = importlib.util.module_from_spec(spec); spec.loader.exec_module(D)
locales = json.load(open(os.path.join(REPO, "tools/watchface/phone-forms.json"), encoding="utf-8"))["locales"]
lines = {rtl: D.Line("greg", rtl, fonts) for rtl in (False, True)}
forms = {}
for tag, loc in locales.items():
    for ds in loc["digitSets"]:
        for wd in loc["weekdays"]:
            for mi, month in enumerate(loc["months"]):
                for day in range(1, 32):
                    text = "%s %s %s" % (wd, D.in_set(day, ds), month)
                    forms.setdefault((loc["rtl"], text, wd, month), set()).add("%s %s" % (tag, D.set_name(ds)))
fails = []
for (rtl, text, wd, month), senders in forms.items():
    line = lines[rtl]
    base = line.baselines[("arch", False)]
    c = D.box_bound(text, line, base)
    if c < 7.5:
        c = D.exact(text, line, base)
    if c < 7:
        fails.append((c, text, wd, month, sorted(senders)))
groups = {}
for c, text, wd, month, senders in fails:
    g = groups.setdefault((month, wd), {"least": 99, "days": 0, "senders": set(), "worst": ""})
    if c < g["least"]:
        g["least"], g["worst"] = c, text
    g["days"] += 1
    g["senders"].update(s.split(" ", 1)[0] for s in senders)
print("%d forms (%d (tag, digit set) sends) under 7 px from Arch's lit stroke, in %d (month, weekday) groups;"
      " -4.3 px is ink on the stroke's centre line" % (len(fails), sum(len(f[4]) for f in fails), len(groups)))
print("  room     month (as sent)      weekday     forms  worst form                    tags (both digit sets each)")
for (month, wd), g in sorted(groups.items(), key=lambda kv: (kv[1]["least"], kv[0])):
    print("  %5.1f px  %-20s %-10s %4d   %-28s %s" % (g["least"], month.replace(" ", "␣"), wd, g["days"], g["worst"].replace(" ", "␣"), ", ".join(sorted(g["senders"]))))
if len(sys.argv) > 3:
    with open(sys.argv[3], "w", encoding="utf-8") as out:
        out.write("Every Gregorian form under 7 px from Arch's lit stroke (date-fit.py's measure; a no-break space shown as ␣): room px, the form, its sends\n")
        for c, text, wd, month, senders in sorted(fails, key=lambda f: (f[0], f[1])):
            out.write("%6.2f  %s  [%s]\n" % (c, text.replace(" ", "␣"), "; ".join(senders)))
