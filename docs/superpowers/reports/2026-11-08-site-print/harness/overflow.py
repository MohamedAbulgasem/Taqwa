"""Chrome print-media layout at given widths: how far anything inside <main> pokes past main's
content box (the sheet's text column), worst page per width, with optional extra CSS and mode."""
import glob, json, os, sys, urllib.request
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from cdp_print import WS
port, base, site = sys.argv[1], sys.argv[2], sys.argv[3]
widths = [int(w) for w in sys.argv[4].split(",")]; extra = sys.argv[5] if len(sys.argv) > 5 else ""; mode = sys.argv[6] if len(sys.argv) > 6 else "whole"
JS = r"""(() => { const st = document.createElement('style'); st.textContent = %s; document.head.appendChild(st);
  if (%s === 'dv') document.querySelector('main').classList.add('dv');
  const m = document.querySelector('main'), r = m.getBoundingClientRect(), cs = getComputedStyle(m);
  const L = r.left + parseFloat(cs.paddingLeft), R = r.right - parseFloat(cs.paddingRight);
  let over = 0, what = '';
  for (const e of m.querySelectorAll('.months *')) { if (!e.getClientRects().length) continue; const b = e.getBoundingClientRect();
    const o = Math.max(b.right - R, L - b.left); if (o > over) { over = o; what = e.tagName + '.' + e.className; } }
  const rows = [...document.querySelectorAll('.tt tbody tr[data-i]')].filter(t => t.getClientRects().length);
  const maxRow = Math.max(...rows.map(t => t.getBoundingClientRect().height));
  const tt = document.querySelector('.tt');
  return JSON.stringify({over: Math.round(over), what, col: Math.round(R - L), table: Math.round(tt.getBoundingClientRect().width), hidden: getComputedStyle(document.querySelector('.tt td.h')).display, maxRow: Math.round(maxRow)}); })()"""
req = urllib.request.Request(f"http://127.0.0.1:{port}/json/new?about:blank", method="PUT"); t = json.load(urllib.request.urlopen(req)); ws = WS(t["webSocketDebuggerUrl"])
ws.call("Page.enable"); ws.call("Emulation.setEmulatedMedia", media="print")
pages = sorted(os.path.relpath(os.path.dirname(p), site) for p in glob.glob(os.path.join(site, "**/prayer-times/*/index.html"), recursive=True) if "how-taqwa" not in p)
for w in widths:
    ws.call("Emulation.setDeviceMetricsOverride", width=w, height=1100, deviceScaleFactor=1, mobile=False)
    worst = (-1, ""); n_over = 0; maxrow = 0; hidden = set()
    for p in pages:
        ws.events.clear(); ws.call("Page.navigate", url=f"{base}/{p}/"); ws.wait_event("Page.loadEventFired")
        ws.call("Runtime.evaluate", expression="document.fonts.ready.then(()=>1)", awaitPromise=True)
        d = json.loads(ws.call("Runtime.evaluate", expression=JS % (json.dumps(extra), json.dumps(mode)), returnByValue=True)["result"]["value"])
        if d["over"] > 1: n_over += 1
        if d["over"] > worst[0]: worst = (d["over"], p, d)
        maxrow = max(maxrow, d["maxRow"]); hidden.add(d["hidden"])
    print(f"width {w}: {n_over}/{len(pages)} pages overflow the text column; worst {worst[0]} px {worst[1]} {worst[2]}; tallest row {maxrow}px; hijri display {hidden}", flush=True)
urllib.request.urlopen(f"http://127.0.0.1:{port}/json/close/{t['id']}")
