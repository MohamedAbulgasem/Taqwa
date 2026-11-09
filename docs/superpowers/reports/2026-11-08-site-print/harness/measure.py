#!/usr/bin/env python3
"""Measures, in Chrome's print layout at a given width, how tall each printed sheet's content is
on every city page, with the in-flow foot (the WebKit path) forced:

    measure.py <port> <base> <site_dir> <width> [extra_css]

Prints one JSON line per page: sheets [{what, h}] where h is the CSS px from the sheet's top
(main's top for sheet 1, the month's top for later sheets) to the foot's bottom when the foot
ends that sheet, else to the month's last box.
"""
import glob, json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from cdp_print import WS
import urllib.request

port, base, site, width = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
extra = sys.argv[5] if len(sys.argv) > 5 else ""
INFLOW = ("@media print { main.wrap { padding-bottom: 0 !important; }"
          " .print-foot { position: static !important; margin-top: 3mm !important; } }")
JS = r"""
(() => {
  const st = document.createElement('style'); st.textContent = %s + %s; document.head.appendChild(st);
  const main = document.querySelector('main'); const months = [...document.querySelectorAll('section.month')];
  const foot = document.querySelector('.print-foot');
  const top = (e) => e.getBoundingClientRect().top + scrollY, bot = (e) => e.getBoundingClientRect().bottom + scrollY;
  const lastBottom = (sec) => Math.max(...[...sec.querySelectorAll('*')].filter(e => e.getClientRects().length).map(bot));
  const out = [];
  months.forEach((m, i) => {
    const start = i === 0 ? top(main) : top(m);
    const end = (i === months.length - 1) ? bot(foot) : lastBottom(m);
    out.push({what: m.id, h: Math.round(end - start), rows: m.querySelectorAll('tbody tr[data-i]').length,
              notes: [...m.querySelectorAll('p.note')].filter(e => e.getClientRects().length).length});
  });
  // A month printed alone by its button: the h1 block, that month, the foot.
  const h1block = months.length ? top(months[0]) - top(main) : 0;
  const footH = bot(foot) - top(foot) + 3 * 96 / 25.4;
  months.forEach((m, i) => out.push({what: 'button:' + m.id, h: Math.round(h1block + (lastBottom(m) - top(m)) + footH - (i ? 12*96/25.4 - 12 : 0))}));
  return JSON.stringify({sheets: out, foot: Math.round(footH), lang: document.documentElement.lang});
})()
"""

req = urllib.request.Request(f"http://127.0.0.1:{port}/json/new?about:blank", method="PUT")
target = json.load(urllib.request.urlopen(req))
ws = WS(target["webSocketDebuggerUrl"])
ws.call("Page.enable")
ws.call("Emulation.setEmulatedMedia", media="print")
ws.call("Emulation.setDeviceMetricsOverride", width=width, height=1000, deviceScaleFactor=1, mobile=False)
pages = sorted(glob.glob(os.path.join(site, "**/prayer-times/*/index.html"), recursive=True))
for p in pages:
    rel = os.path.relpath(os.path.dirname(p), site)
    if rel.endswith("how-taqwa-checks"):
        continue
    ws.events.clear()
    ws.call("Page.navigate", url=f"{base}/{rel}/")
    ws.wait_event("Page.loadEventFired")
    ws.call("Runtime.evaluate", expression="document.fonts.ready.then(() => 1)", awaitPromise=True)
    r = ws.call("Runtime.evaluate", expression=JS % (json.dumps(INFLOW), json.dumps(extra)), returnByValue=True)
    if "exceptionDetails" in r:
        print(json.dumps({"page": rel, "error": str(r["exceptionDetails"])[:200]}), flush=True)
        continue
    d = json.loads(r["result"]["value"])
    d["page"] = rel
    print(json.dumps(d, ensure_ascii=False), flush=True)
urllib.request.urlopen(f"http://127.0.0.1:{port}/json/close/{target['id']}")
