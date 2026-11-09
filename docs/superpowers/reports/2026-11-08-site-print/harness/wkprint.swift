// wkprint <jobs.json>: print pages to PDF with WebKit (WKWebView + NSPrintOperation), as Safari does.
// jobs: [{"url":..., "out":..., "paper":"a4"|"letter", "margin_pt":0, "setup":"<js>"}]
import AppKit
import WebKit

let paper: [String: NSSize] = ["a4": NSSize(width: 595.28, height: 841.89), "letter": NSSize(width: 612, height: 792)]

final class Runner: NSObject, WKNavigationDelegate {
    let jobs: [[String: Any]]
    var i = 0
    let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 1024, height: 1400), styleMask: [.borderless],
                          backing: .buffered, defer: false)
    let web = WKWebView(frame: NSRect(x: 0, y: 0, width: 1024, height: 1400))

    init(jobs: [[String: Any]]) {
        self.jobs = jobs
        super.init()
        window.contentView = web
        web.navigationDelegate = self
    }

    func next() {
        guard i < jobs.count else { NSApp.terminate(nil); return }
        web.load(URLRequest(url: URL(string: jobs[i]["url"] as! String)!))
    }

    var blank = true
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        if webView.url?.absoluteString == "about:blank" || webView.url == nil { return }
        if let ev = jobs[i]["eval"] as? String {
            webView.callAsyncJavaScript(ev, arguments: [:], in: nil, in: .page) { r in
                print("eval", self.jobs[self.i]["url"] as! String, (try? r.get()) ?? "ERR \(r)")
                fflush(stdout)
                self.i += 1
                DispatchQueue.main.async { self.next() }
            }
            return
        }
        let setup = (jobs[i]["setup"] as? String) ?? ""
        let js = "document.fonts.ready.then(() => { \(setup) ; return 1 })"
        webView.callAsyncJavaScript("return await (\(js.isEmpty ? "1" : js))", arguments: [:], in: nil, in: .page) { _ in
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { self.printJob() }
        }
    }

    func printJob() {
        let job = jobs[i]
        let out = URL(fileURLWithPath: job["out"] as! String)
        try? FileManager.default.createDirectory(at: out.deletingLastPathComponent(), withIntermediateDirectories: true)
        let info = NSPrintInfo()
        info.paperSize = paper[job["paper"] as! String]!
        let m = CGFloat((job["margin_pt"] as? Double) ?? 0)
        info.topMargin = m; info.bottomMargin = m; info.leftMargin = m; info.rightMargin = m
        info.horizontalPagination = .automatic
        info.verticalPagination = .automatic
        info.isHorizontallyCentered = false
        info.isVerticallyCentered = false
        info.jobDisposition = .save
        info.dictionary()[NSPrintInfo.AttributeKey.jobSavingURL] = out
        let op = web.printOperation(with: info)
        op.showsPrintPanel = false
        op.showsProgressPanel = false
        op.view?.frame = web.bounds
        op.runModal(for: window, delegate: self, didRun: #selector(done(_:success:contextInfo:)), contextInfo: nil)
    }

    @objc func done(_ op: NSPrintOperation, success: Bool, contextInfo: UnsafeMutableRawPointer?) {
        print(success ? "wrote" : "FAILED", jobs[i]["out"] as! String)
        fflush(stdout)
        i += 1
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { self.web.loadHTMLString("", baseURL: nil); DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { self.blank = false; self.next() } }
    }
}

let jobs = try! JSONSerialization.jsonObject(with: Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[1]))) as! [[String: Any]]
let app = NSApplication.shared
app.setActivationPolicy(.prohibited)
let runner = Runner(jobs: jobs)
DispatchQueue.main.async { runner.next() }
app.run()
