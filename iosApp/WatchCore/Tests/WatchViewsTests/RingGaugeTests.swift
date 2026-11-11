import AppKit
import SwiftUI
import XCTest
@testable import WatchViews

/// The ring's direction (Ruling 64 item 3), which only the simulator's shots held before: drawn
/// off-device by ImageRenderer and read round its stroke, it fills from 12 o'clock, clockwise, and
/// anticlockwise in a right-to-left language as the phone's `RingArc` does, under the feed's own
/// direction as every complication and page sets it. SwiftUI mirrors a rotation under a
/// right-to-left layout, so without `RingGauge`'s fixed left-to-right drawing the arc began at 6
/// o'clock (P4.6 review round 1). The corner's ring (`thickness` 0.12) and Today's (5.4 of 109.4)
/// are the same view, and Task 8's tasbeeh ring is to reuse it (plan 4 erratum p4 #15).
final class RingGaugeTests: XCTestCase {
    /// Today's ring: 5.4 wide on a ring of radius 52 (`TodayLayout`).
    private let today: CGFloat = 5.4 / 109.4
    /// The tasbeeh ring: 5.2 wide on a ring of radius 54 (`aTasbeeh`).
    private let tasbeeh: CGFloat = 5.2 / 113.2

    @MainActor func testTheRingFillsClockwiseFromTheTop() throws {
        for thickness in [0.12, today] {
            for progress: Float in [27.0 / 217.0, 145.0 / 217.0, 0.5] {
                try assertFills(progress, rtl: false, thickness: thickness)
            }
        }
    }

    @MainActor func testInARightToLeftLanguageTheRingFillsAnticlockwiseFromTheTop() throws {
        for thickness in [0.12, today] {
            for progress: Float in [27.0 / 217.0, 145.0 / 217.0, 0.5] {
                try assertFills(progress, rtl: true, thickness: thickness)
            }
        }
    }

    /// The tasbeeh ring fills clockwise in every language (plan 4's Global Constraints): it is the
    /// same view, given no flip, under an Arabic feed's right-to-left layout as under an English one
    /// (plan 4 Task 8, erratum p4 #15).
    @MainActor func testTheTasbeehRingFillsClockwiseInEveryLanguage() throws {
        for layout in [LayoutDirection.leftToRight, .rightToLeft] {
            for progress: Float in [0.33, 0.64, 0.9] {
                try assertFills(progress, rtl: false, thickness: tasbeeh, layout: layout)
            }
        }
    }

    /// The parts' ends are notches across the full ring, in the track's colour, clockwise from 12
    /// o'clock at their share of the set (`appRing`'s ticks): drawn where the arc has reached them,
    /// they cut it, and in every language at the same place.
    @MainActor func testTheNotchesCutTheArcWhereThePartsEnd() throws {
        for layout in [LayoutDirection.leftToRight, .rightToLeft] {
            let lit = Set(try amber(1, rtl: false, thickness: tasbeeh, layout: layout, notches: [0.33, 0.66], step: 1))
            for notch in [0.33, 0.66] {
                let at = Int((notch * 360).rounded())
                XCTAssertFalse(lit.contains(at), "\(layout): the notch at \(notch) cuts the arc at \(at)°")
                XCTAssertTrue(lit.contains(at - 4) && lit.contains(at + 4), "\(layout): and only there (\(at)°)")
            }
            let cut = Set(0..<360).subtracting(lit)
            XCTAssertTrue(cut.allSatisfy { degrees in [0.33, 0.66].contains { abs(Double(degrees) - $0 * 360) <= 2 } }, "\(layout): two cuts, nothing else: \(cut.sorted())")
        }
    }

    @MainActor func testAnEmptyRingIsItsTrackAlone() throws {
        for rtl in [false, true] {
            XCTAssertEqual(try amber(0, rtl: rtl, thickness: 0.12), [], "rtl \(rtl)")
        }
    }

    /// Every point round the ring, 5° apart, is amber where the fill runs and the track's
    /// otherwise, from 12 o'clock clockwise (or anticlockwise when [rtl]); the points within 15° of
    /// the fill's two ends, where the round caps and the edge's smoothing fall, are not read.
    @MainActor private func assertFills(_ progress: Float, rtl: Bool, thickness: CGFloat, layout: LayoutDirection? = nil, line: UInt = #line) throws {
        let lit = Set(try amber(progress, rtl: rtl, thickness: thickness, layout: layout))
        let fill = Double(progress) * 360
        for degrees in stride(from: 0, to: 360, by: 5) {
            let along = Double(rtl ? (360 - degrees) % 360 : degrees) // degrees along the fill's way
            let fromAnEnd = min(along, abs(along - fill), 360 - along)
            if fromAnEnd < 15 { continue }
            let filled = along < fill
            XCTAssertEqual(lit.contains(degrees), filled,
                           "\(rtl ? "right to left" : "left to right"), \(thickness), \(progress): \(degrees)° clockwise from 12 should be \(filled ? "amber" : "the track")",
                           line: line)
        }
    }

    /// The points round a 200-point ring drawn as the feed's direction sets it (or [layout]), [step]°
    /// apart and clockwise from 12 o'clock, that are amber, each on the stroke's middle line.
    @MainActor private func amber(
        _ progress: Float, rtl: Bool, thickness: CGFloat, layout: LayoutDirection? = nil, notches: [Double] = [], step: Int = 5
    ) throws -> [Int] {
        let side: CGFloat = 200
        let drawn = RingGauge(progress: progress, rtl: rtl, thickness: thickness, notches: notches) { _ in Text(rtl ? "ع" : "A") }
            .frame(width: side, height: side)
            .background(Color.black)
            .environment(\.layoutDirection, layout ?? direction(rtl: rtl))
        let renderer = ImageRenderer(content: drawn)
        renderer.scale = 1
        let bitmap = NSBitmapImageRep(cgImage: try XCTUnwrap(renderer.cgImage, "the ring is drawn"))
        let radius = (side - side * thickness) / 2
        return try stride(from: 0, to: 360, by: step).filter { degrees in
            let angle = Double(degrees) * .pi / 180
            let x = Int((Double(side) / 2 + Double(radius) * sin(angle)).rounded())
            let y = Int((Double(side) / 2 - Double(radius) * cos(angle)).rounded())
            let colour = try XCTUnwrap(bitmap.colorAt(x: x, y: y)?.usingColorSpace(.sRGB))
            // Amber is F0B429; the track, 232825, and the black behind it, are dark.
            return colour.redComponent > 0.7 && colour.blueComponent < 0.4
        }
    }
}
