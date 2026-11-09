#!/usr/bin/env python3
"""Print pages to PDF with headless Chrome over the DevTools protocol (stdlib only).

    cdp_print.py <port> <jobs.json>

jobs.json: [{"url":..., "out":..., "paper":"a4"|"letter", "margin_in":0, "setup":"<js>"}]
Chrome must already run with --remote-debugging-port=<port>.
"""
import base64, json, os, socket, struct, sys, time, urllib.request
from urllib.parse import urlparse

PAPER = {"a4": (8.2677, 11.6929), "letter": (8.5, 11.0)}


class WS:
    def __init__(self, url):
        u = urlparse(url)
        self.s = socket.create_connection((u.hostname, u.port))
        key = base64.b64encode(os.urandom(16)).decode()
        req = (f"GET {u.path} HTTP/1.1\r\nHost: {u.hostname}:{u.port}\r\nUpgrade: websocket\r\n"
               f"Connection: Upgrade\r\nSec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n")
        self.s.sendall(req.encode())
        buf = b""
        while b"\r\n\r\n" not in buf:
            buf += self.s.recv(4096)
        self.rest = buf.split(b"\r\n\r\n", 1)[1]
        self.id = 0

    def _read(self, n):
        while len(self.rest) < n:
            chunk = self.s.recv(1 << 20)
            if not chunk:
                raise EOFError
            self.rest += chunk
        out, self.rest = self.rest[:n], self.rest[n:]
        return out

    def send(self, obj):
        data = json.dumps(obj).encode()
        head = bytes([0x81])
        n = len(data)
        if n < 126:
            head += bytes([0x80 | n])
        elif n < 65536:
            head += bytes([0x80 | 126]) + struct.pack(">H", n)
        else:
            head += bytes([0x80 | 127]) + struct.pack(">Q", n)
        mask = os.urandom(4)
        self.s.sendall(head + mask + bytes(b ^ mask[i % 4] for i, b in enumerate(data)))

    def recv(self):
        msg = b""
        while True:
            b1, b2 = self._read(2)
            n = b2 & 0x7F
            if n == 126:
                n = struct.unpack(">H", self._read(2))[0]
            elif n == 127:
                n = struct.unpack(">Q", self._read(8))[0]
            msg += self._read(n)
            if b1 & 0x80:
                return json.loads(msg)

    def call(self, method, **params):
        self.id += 1
        me = self.id
        self.send({"id": me, "method": method, "params": params})
        while True:
            m = self.recv()
            if m.get("id") == me:
                if "error" in m:
                    raise RuntimeError(f"{method}: {m['error']}")
                return m.get("result", {})
            self.events.append(m)

    events = []

    def wait_event(self, name, timeout=30):
        end = time.time() + timeout
        for i, m in enumerate(self.events):
            if m.get("method") == name:
                del self.events[i]
                return m
        while time.time() < end:
            m = self.recv()
            if m.get("method") == name:
                return m
        raise TimeoutError(name)


def main():
    port, jobs_path = sys.argv[1], sys.argv[2]
    jobs = json.load(open(jobs_path))
    req = urllib.request.Request(f"http://127.0.0.1:{port}/json/new?about:blank", method="PUT")
    target = json.load(urllib.request.urlopen(req))
    ws = WS(target["webSocketDebuggerUrl"])
    ws.call("Page.enable")
    for job in jobs:
        ws.events.clear()
        ws.call("Page.navigate", url=job["url"])
        ws.wait_event("Page.loadEventFired")
        ws.call("Runtime.evaluate", expression="document.fonts.ready.then(() => 1)", awaitPromise=True)
        if job.get("setup"):
            r = ws.call("Runtime.evaluate", expression=job["setup"], awaitPromise=True, returnByValue=True)
            if "exceptionDetails" in r:
                raise RuntimeError(r["exceptionDetails"])
        w, h = PAPER[job["paper"]]
        mg = job.get("margin_in", 0)
        # The page's @page { margin: 0 } wins over printToPDF's margins, which then only narrow the
        # media-query width; a dialog's Custom margins are modelled as paper that much smaller.
        sim = job.get("sim_margin_in", 0)
        w, h = w - 2 * sim, h - 2 * sim
        pdf = ws.call("Page.printToPDF", paperWidth=w, paperHeight=h, marginTop=mg, marginBottom=mg,
                      marginLeft=mg, marginRight=mg, printBackground=False, preferCSSPageSize=False,
                      displayHeaderFooter=job.get("header_footer", False), scale=job.get("scale", 1))
        os.makedirs(os.path.dirname(job["out"]), exist_ok=True)
        with open(job["out"], "wb") as f:
            f.write(base64.b64decode(pdf["data"]))
        print("wrote", job["out"], flush=True)
    urllib.request.urlopen(f"http://127.0.0.1:{port}/json/close/{target['id']}")


if __name__ == "__main__":
    main()
