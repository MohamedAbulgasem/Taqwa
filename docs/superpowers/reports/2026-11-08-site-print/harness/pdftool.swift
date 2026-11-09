// pdftool dump <pdf>                 -> JSON: pages [{w,h,lines:[{t,x,y,w,h}]}] (y from the top)
// pdftool png <pdf> <outprefix> <dpi> -> <outprefix>-<n>.png per page
// pdftool break <pdf> <n> <out.png> <dpi> -> bottom 45% of page n stacked over top 30% of page n+1
import Foundation
import PDFKit
import AppKit

func render(_ page: PDFPage, dpi: CGFloat) -> CGImage {
    let box = page.bounds(for: .mediaBox)
    let s = dpi / 72
    let w = Int(box.width * s), h = Int(box.height * s)
    let ctx = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: 0,
                        space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    ctx.setFillColor(CGColor(red: 1, green: 1, blue: 1, alpha: 1))
    ctx.fill(CGRect(x: 0, y: 0, width: w, height: h))
    ctx.scaleBy(x: s, y: s)
    page.draw(with: .mediaBox, to: ctx)
    return ctx.makeImage()!
}

func save(_ img: CGImage, _ path: String) {
    let rep = NSBitmapImageRep(cgImage: img)
    try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: path))
}

let args = CommandLine.arguments
let doc = PDFDocument(url: URL(fileURLWithPath: args[2]))!
switch args[1] {
case "dump":
    var pages: [[String: Any]] = []
    for i in 0..<doc.pageCount {
        let page = doc.page(at: i)!
        let box = page.bounds(for: .mediaBox)
        var lines: [[String: Any]] = []
        if let sel = page.selection(for: box) {
            for line in sel.selectionsByLine() {
                let b = line.bounds(for: page)
                let t = (line.string ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
                if t.isEmpty { continue }
                lines.append(["t": t, "x": b.minX, "y": box.height - b.maxY, "w": b.width, "h": b.height])
            }
        }
        pages.append(["w": box.width, "h": box.height, "lines": lines])
    }
    let data = try! JSONSerialization.data(withJSONObject: ["pages": pages], options: [])
    FileHandle.standardOutput.write(data)
case "png":
    let dpi = CGFloat(Double(args[4])!)
    for i in 0..<doc.pageCount { save(render(doc.page(at: i)!, dpi: dpi), "\(args[3])-\(i + 1).png") }
case "break":
    let n = Int(args[3])! - 1
    let dpi = CGFloat(Double(args[5])!)
    let a = render(doc.page(at: n)!, dpi: dpi), b = render(doc.page(at: n + 1)!, dpi: dpi)
    let ah = Int(Double(a.height) * 0.45), bh = Int(Double(b.height) * 0.30), gap = 24
    let w = max(a.width, b.width), h = ah + gap + bh
    let ctx = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: 0,
                        space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
    ctx.setFillColor(CGColor(red: 0.55, green: 0.55, blue: 0.55, alpha: 1))
    ctx.fill(CGRect(x: 0, y: 0, width: w, height: h))
    // CG's origin is bottom-left: page n's bottom slice on top, page n+1's top slice below.
    let aCrop = a.cropping(to: CGRect(x: 0, y: a.height - ah, width: a.width, height: ah))!
    let bCrop = b.cropping(to: CGRect(x: 0, y: 0, width: b.width, height: bh))!
    ctx.draw(aCrop, in: CGRect(x: 0, y: bh + gap, width: a.width, height: ah))
    ctx.draw(bCrop, in: CGRect(x: 0, y: 0, width: b.width, height: bh))
    save(ctx.makeImage()!, args[4])
default:
    print("usage"); exit(2)
}
