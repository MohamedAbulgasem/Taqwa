#!/usr/bin/env python3
"""Print-check matrix for the city pages: prints each scenario to PDF in Chrome (CDP) and/or
WebKit (WKWebView), with visibility-only variants (same layout) to locate the foot, the day rows
and the heading rows on every sheet, then checks:

  overlap   no non-foot text line intersects the foot's box on any sheet
  split     every printed day row is whole on one sheet (one line with all six times)
  rows      every day of each printed month is printed exactly once
  head      every sheet that carries day rows has a heading row above its first row
  sheets    sheets used per month (WebKit must be 1: it splits rows and repeats no heading)
  foot      the foot prints (Chrome path: on every sheet; in-flow path: once, after the months)
  blank     no blank sheet, no sheet that holds only the foot

    matrix.py --base http://127.0.0.1:8792 --site <dir> --out <dir> --engines chrome,wk \
              --pages prayer-times/cape-town-south-africa/,ar/prayer-times/paris-france/ \
              --papers a4,letter --chrome-margins 0,0.4 --wk-margins 0,18,36 --modes whole,m0,dv
"""
import argparse, json, os, re, subprocess, sys, time

HERE = os.path.dirname(os.path.abspath(__file__))
PDFTOOL = os.path.join(HERE, "pdftool")
# PDFKit puts stray spaces (and NULs) inside Bengali numerals: "৫:১ ৯".
WRAPPED = False
TIME = re.compile(r"\d\s?\d?\s?[:٫]\s?\d\s?\d")
VARIANTS = {
    "normal": "",
    "foot": "body *{visibility:hidden!important} .print-foot, .print-foot *{visibility:visible!important}",
    "nofoot": ".print-foot, .print-foot *{visibility:hidden!important}",
    "rows": "body *{visibility:hidden!important} .tt tbody tr, .tt tbody tr *{visibility:visible!important}",
    "head": "body *{visibility:hidden!important} .tt thead, .tt thead *{visibility:visible!important}",
}


def mode_js(mode):
    if mode == "whole":
        return ""
    if mode == "dv":
        return "document.querySelector('main').classList.add('dv');"
    if mode.startswith("m"):
        k = int(mode[1:])
        return ("var s=document.querySelectorAll('section.month')[%d]; if(!s) throw new Error('no month %d');"
                "document.documentElement.setAttribute('data-print', s.id); s.setAttribute('data-print-target','');" % (k, k))
    raise ValueError(mode)


def style_js(css):
    if not css:
        return ""
    return "var st=document.createElement('style'); st.textContent=%s; document.head.appendChild(st);" % json.dumps(css)


def expected(site, page, mode):
    doc = open(os.path.join(site, page, "index.html"), encoding="utf-8").read()
    months = re.findall(r'<section class="month" id="(m-\d{4}-\d{2})".*?</section>', doc, re.S)
    bodies = re.findall(r'<section class="month" id="m-\d{4}-\d{2}".*?</section>', doc, re.S)
    days = [len(re.findall(r'<tr data-i="', b)) for b in bodies]
    expected.times = [len(re.findall(r'<span class="v">', b)) + (len(re.findall(r'<small class="dv" data-w=', b)) if mode == "dv" else 0)
                      for b in bodies]
    if mode.startswith("m") and mode != "m":
        k = int(mode[1:])
        expected.times = [expected.times[k]]
        return [months[k]], [days[k]]
    return months, days


def dump(pdf):
    out = subprocess.run([PDFTOOL, "dump", pdf], capture_output=True, check=True).stdout
    return json.loads(out)["pages"]


def run_chrome(jobs, port):
    path = jobs[0]["out"] + ".jobs.json"
    json.dump(jobs, open(path, "w"))
    subprocess.run([sys.executable, os.path.join(HERE, "cdp_print.py"), str(port), path], check=True,
                   stdout=subprocess.DEVNULL)


def run_wk(jobs):
    path = jobs[0]["out"] + ".jobs.json"
    json.dump(jobs, open(path, "w"))
    p = subprocess.Popen([os.path.join(HERE, "wkprint"), path], stdout=subprocess.DEVNULL)
    try:
        p.wait(timeout=20 + 6 * len(jobs))
    except subprocess.TimeoutExpired:
        p.kill()
        raise


def boxes_intersect(a, b):
    return a["x"] < b["x"] + b["w"] and b["x"] < a["x"] + a["w"] and a["y"] < b["y"] + b["h"] and b["y"] < a["y"] + a["h"]


def analyse(sc, pdfs, months, days):
    res = {"id": sc["id"], "fail": [], "warn": []}
    normal = dump(pdfs["normal"])
    variants = {k: dump(v) for k, v in pdfs.items() if k != "normal"}
    n = len(normal)
    res["sheets"] = n
    for k, v in variants.items():
        if len(v) != n:
            res["fail"].append(f"variant {k} has {len(v)} sheets, normal {n}")
            return res
    mm = 72 / 25.4
    foot_pages = []
    row_pages = []
    for i in range(n):
        foot = variants["foot"][i]["lines"]
        content = variants["nofoot"][i]["lines"]
        rows = variants["rows"][i]["lines"]
        head = variants["head"][i]["lines"]
        if not normal[i]["lines"]:
            res["fail"].append(f"sheet {i+1} blank")
        off = [l["t"][:30] for l in normal[i]["lines"] if l["x"] < 0 or l["x"] + l["w"] > normal[i]["w"] + 0.5]
        if off:
            res["fail"].append(f"sheet {i+1}: {len(off)} line(s) run off the paper: {off[:3]}")
        if foot:
            foot_pages.append(i + 1)
            fx0 = min(l["x"] for l in foot); fy0 = min(l["y"] for l in foot)
            fx1 = max(l["x"] + l["w"] for l in foot); fy1 = max(l["y"] + l["h"] for l in foot)
            # The foot's box: its text, plus the hairline and padding above it (2.5 mm + 1 px).
            fbox = {"x": fx0, "y": fy0 - 3.2 * mm, "w": fx1 - fx0, "h": fy1 - fy0 + 3.2 * mm}
            hits = [l["t"][:40] for l in content if boxes_intersect(l, fbox)]
            if hits:
                res["fail"].append(f"sheet {i+1}: foot covers {len(hits)} line(s): {hits[:3]}")
            if not content:
                res["warn"].append(f"sheet {i+1} holds only the foot")
        full, frags = 0, []
        # PDFKit breaks a day's line into pieces (a cell a little off its baseline such as the
        # Jumuʿah pill, every cell of a WebKit RTL table, Bengali numerals) and gives Nastaliq
        # glyphs boxes taller than a row: pieces whose centres lie within half a line of each
        # other are one printed row.
        clusters = []
        hs = sorted(l["h"] for l in rows)
        band = 0.5 * (hs[len(hs) // 4] if hs else 10)
        for ix, l in sorted(enumerate(rows), key=lambda e: e[1]["y"] + e[1]["h"] / 2):
            mid = l["y"] + l["h"] / 2
            c = clusters[-1] if clusters else None
            if c and mid - c["mid0"] <= band:
                c["parts"].append((ix, l["t"]))
            else:
                clusters.append({"parts": [(ix, l["t"])], "mid0": mid})
        for c in clusters:  # joined in reading order, not in order of height
            c["t"] = " ".join(t for _, t in sorted(c["parts"]))
        for l in clusters:
            # WebKit writes Bengali digits without text, only their ":" survives.
            k = max(len(TIME.findall(l["t"])), l["t"].count(":"))
            if k >= 6:
                full += 1
            else:
                frags.append(l["t"][:30])
        res.setdefault("times", 0)
        res["times"] += sum(len(TIME.findall(l["t"])) for l in rows) if WRAPPED else 0
        if frags and not WRAPPED:
            res["fail"].append(f"sheet {i+1}: {len(frags)} row fragment(s) (split or wrapped row): {frags[:3]}")
        row_pages.append(full)
        if rows:
            first_row_y = min(l["y"] for l in rows)
            if not any(l["y"] < first_row_y for l in head):
                res["fail"].append(f"sheet {i+1}: rows without a heading row above them")
    res["rows_per_sheet"] = row_pages
    if WRAPPED:
        if res["times"] != sum(sc["times"]):
            res["fail"].append(f"printed {res['times']} times, expected {sum(sc['times'])}")
    res["foot_sheets"] = foot_pages
    total = sum(days)
    if not WRAPPED and sum(row_pages) != total:
        res["fail"].append(f"printed {sum(row_pages)} whole rows, expected {total}")
    # Sheets per month: walk the rows in order.
    per_month, i, left = [], 0, 0
    sheets_of = []
    for d in days:
        used = set()
        need = d
        while need > 0 and i < n:
            avail = row_pages[i] - left
            take = min(avail, need)
            if take > 0:
                used.add(i + 1)
            need -= take
            left += take
            if left >= row_pages[i]:
                i, left = i + 1, 0
        sheets_of.append(sorted(used))
    res["month_sheets"] = sheets_of
    if not WRAPPED and sc["engine"] == "wk" and any(len(s) > 1 for s in sheets_of):
        res["fail"].append(f"WebKit month on {[len(s) for s in sheets_of]} sheets (WebKit splits rows, repeats no heading)")
    if sc.get("fixed_foot"):
        missing = [p for p in range(1, n + 1) if p not in foot_pages]
        if missing:
            res["fail"].append(f"foot missing on sheet(s) {missing}")
    else:
        if not foot_pages:
            res["fail"].append("foot not printed")
    return res


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", required=True)
    ap.add_argument("--site", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--engines", default="chrome,wk")
    ap.add_argument("--pages", required=True)
    ap.add_argument("--papers", default="a4,letter")
    ap.add_argument("--chrome-margins", default="0", help="dialog margins, modelled as smaller paper (inches)")
    ap.add_argument("--chrome-scales", default="1")
    ap.add_argument("--wk-margins", default="0,18,36")
    ap.add_argument("--modes", default="whole")
    ap.add_argument("--port", default=9333)
    ap.add_argument("--wrapped", action="store_true", help="rows may wrap (narrow sheets): count every printed time instead of whole one-line rows")
    ap.add_argument("--extra-css", default="", help="CSS injected into every variant (to try a candidate)")
    ap.add_argument("--reanalyse", action="store_true", help="analyse the PDFs already printed")
    ap.add_argument("--chrome-fixed-foot", default="yes", help="expect the foot on every Chrome sheet")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    global WRAPPED
    WRAPPED = a.wrapped
    scenarios = []
    for page in a.pages.split(","):
        slug = page.strip("/").replace("/", "_")
        for mode in a.modes.split(","):
            try:
                months, days = expected(a.site, page, mode)
                times = expected.times
            except IndexError:
                continue
            for paper in a.papers.split(","):
                for eng in a.engines.split(","):
                    margins = a.chrome_margins if eng == "chrome" else a.wk_margins
                    scales = a.chrome_scales.split(",") if eng == "chrome" else ["1"]
                    for m in margins.split(","):
                      for sc in scales:
                        sid = f"{eng}-{slug}-{mode}-{paper}-m{m}" + (f"-s{sc}" if sc != "1" else "")
                        scenarios.append({"id": sid, "engine": eng, "page": page, "mode": mode, "paper": paper,
                                          "margin": float(m), "scale": float(sc), "months": months, "days": days, "times": times,
                                          "fixed_foot": eng == "chrome" and a.chrome_fixed_foot == "yes"})
    jobs = {"chrome": [], "wk": []}
    for sc in scenarios:
        sc["pdfs"] = {}
        for v, css in VARIANTS.items():
            out = os.path.join(a.out, sc["id"] + ("" if v == "normal" else "." + v) + ".pdf")
            sc["pdfs"][v] = out
            job = {"url": a.base.rstrip("/") + "/" + sc["page"], "out": out, "paper": sc["paper"],
                   "setup": mode_js(sc["mode"]) + style_js(a.extra_css) + style_js(css)}
            if sc["engine"] == "chrome":
                job["sim_margin_in"] = sc["margin"]; job["scale"] = sc["scale"]
            else:
                job["margin_pt"] = sc["margin"]
            jobs[sc["engine"]].append(job)
    t = time.time()
    if a.reanalyse:
        jobs = {"chrome": [], "wk": []}
    if jobs["chrome"]:
        run_chrome(jobs["chrome"], a.port)
    if jobs["wk"]:
        # WebKit in batches, so one hang costs one batch.
        for i in range(0, len(jobs["wk"]), 40):
            run_wk(jobs["wk"][i:i + 40])
    results = []
    for sc in scenarios:
        try:
            r = analyse(sc, sc["pdfs"], sc["months"], sc["days"])
        except Exception as e:  # a missing PDF is a failure of the run, reported as such
            r = {"id": sc["id"], "fail": [f"analysis error: {e}"], "warn": []}
        r["pdf"] = sc["pdfs"]["normal"]
        results.append(r)
        status = "FAIL" if r["fail"] else ("warn" if r["warn"] else "ok")
        print(f"{status:4} {sc['id']}: sheets={r.get('sheets')} rows/sheet={r.get('rows_per_sheet')} "
              f"months->sheets={r.get('month_sheets')} foot={r.get('foot_sheets')} "
              + ("; ".join(r["fail"] + r["warn"])))
    json.dump(results, open(os.path.join(a.out, "results.json"), "w"), indent=1, ensure_ascii=False)
    bad = [r for r in results if r["fail"]]
    print(f"\n{len(results)} scenarios, {len(bad)} failing, {sum(1 for r in results if r['warn'])} with warnings, "
          f"{time.time() - t:.0f}s")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
