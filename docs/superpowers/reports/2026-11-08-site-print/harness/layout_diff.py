"""Screen-media layout of the same pages from two servers, clock frozen: every element's box and
its computed display, position, visibility, font, colours, margins, paddings, borders and line
height, compared exactly. Deterministic where pixel screenshots carry rasterisation noise."""
import hashlib, json, os, sys, urllib.request
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from cdp_print import WS
port, a, b = sys.argv[1], sys.argv[2], sys.argv[3]
pages = sys.argv[4].split(","); widths = [int(w) for w in sys.argv[5].split(",")]
FREEZE = """(() => { const T = Date.UTC(2026, 10, 8, 10, 0, 0); const R = Date;
  function D(...x) { return x.length ? new R(...x) : new R(T); } D.now = () => T; D.UTC = R.UTC; D.parse = R.parse; D.prototype = R.prototype;
  globalThis.Date = D; })();"""
PROPS = ["display", "position", "visibility", "float", "font-family", "font-size", "font-weight", "line-height",
         "letter-spacing", "text-align", "white-space", "color", "background-color", "margin", "padding", "border",
         "border-radius", "width", "height", "grid-template-columns", "grid-template-areas", "order", "transform", "opacity"]
JS = """(() => { const P = %s; const out = [];
  for (const e of document.querySelectorAll('body *')) { const r = e.getBoundingClientRect(); const cs = getComputedStyle(e);
    out.push([e.tagName, e.className && e.className.baseVal === undefined ? e.className : '', [r.x, r.y, r.width, r.height].map(v => Math.round(v * 100) / 100), P.map(p => cs.getPropertyValue(p))]); }
  return JSON.stringify(out); })()""" % json.dumps(PROPS)
req = urllib.request.Request(f"http://127.0.0.1:{port}/json/new?about:blank", method="PUT"); t = json.load(urllib.request.urlopen(req)); ws = WS(t["webSocketDebuggerUrl"])
ws.call("Page.enable"); ws.call("Page.addScriptToEvaluateOnNewDocument", source=FREEZE)
same = diff = 0; elements = 0
for scheme in ("light", "dark"):
    ws.call("Emulation.setEmulatedMedia", media=os.environ.get("MEDIA", "screen"), features=[{"name": "prefers-color-scheme", "value": scheme}, {"name": "prefers-reduced-motion", "value": "reduce"}])
    for w in widths:
        ws.call("Emulation.setDeviceMetricsOverride", width=w, height=900, deviceScaleFactor=1, mobile=w < 600)
        for p in pages:
            got = []
            for base in (a, b):
                ws.events.clear(); ws.call("Page.navigate", url=f"{base}/{p}"); ws.wait_event("Page.loadEventFired")
                ws.call("Runtime.evaluate", expression="document.fonts.ready.then(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r))))", awaitPromise=True)
                got.append(json.loads(ws.call("Runtime.evaluate", expression=JS, returnByValue=True)["result"]["value"]))
            elements += len(got[0])
            if got[0] == got[1]:
                same += 1
            else:
                diff += 1
                d = next((i for i, (x, y) in enumerate(zip(got[0], got[1])) if x != y), None)
                print("DIFF", scheme, w, p, len(got[0]), len(got[1]), got[0][d] if d is not None else "", got[1][d] if d is not None else "", flush=True)
print(f"{same} page/width/theme layouts identical, {diff} different ({elements} element boxes compared per side)")
urllib.request.urlopen(f"http://127.0.0.1:{port}/json/close/{t['id']}")
