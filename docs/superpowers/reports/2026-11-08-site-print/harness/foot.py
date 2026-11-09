import json, sys, os, glob, urllib.request
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from cdp_print import WS
port, base, site = sys.argv[1], sys.argv[2], sys.argv[3]; widths = [int(w) for w in sys.argv[4].split(",")]
req = urllib.request.Request(f"http://127.0.0.1:{port}/json/new?about:blank", method="PUT"); t = json.load(urllib.request.urlopen(req)); ws = WS(t["webSocketDebuggerUrl"])
ws.call("Page.enable"); ws.call("Emulation.setEmulatedMedia", media="print")
pages = sorted(os.path.relpath(os.path.dirname(p), site) for p in glob.glob(os.path.join(site, "**/prayer-times/*/index.html"), recursive=True) if "how-taqwa" not in p)
worst = {w: (0, "") for w in widths}
for w in widths:
    ws.call("Emulation.setDeviceMetricsOverride", width=w, height=1100, deviceScaleFactor=1, mobile=False)
    for p in pages:
        ws.events.clear(); ws.call("Page.navigate", url=f"{base}/{p}/"); ws.wait_event("Page.loadEventFired"); ws.call("Runtime.evaluate", expression="document.fonts.ready.then(()=>1)", awaitPromise=True)
        r = ws.call("Runtime.evaluate", expression="(()=>{const f=document.querySelector('.print-foot');const b=f.getBoundingClientRect();return JSON.stringify({h:b.height,bottomGap:innerHeight-b.bottom,pos:getComputedStyle(f).position,lines:f.querySelector('.pf-line').getClientRects().length, lh: f.querySelector('.pf-line').getBoundingClientRect().height})})()", returnByValue=True)
        d = json.loads(r["result"]["value"])
        if d["h"] > worst[w][0]: worst[w] = (d["h"], p, d)
for w in widths: print(w, "tallest foot %.1f px" % worst[w][0], worst[w][1], worst[w][2], "room needed incl 8mm: %.1f mm" % ((worst[w][0] + 8*96/25.4) * 25.4 / 96))
urllib.request.urlopen(f"http://127.0.0.1:{port}/json/close/{t['id']}")
