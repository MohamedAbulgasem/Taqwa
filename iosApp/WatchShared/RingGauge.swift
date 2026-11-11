import SwiftUI
import WidgetKit

/// `faces.js`'s `appRing` at any size: a hairline track and an accent arc from the top, its width
/// [thickness] of the ring's size (0.12 by default: 2.4 on a 20-unit ring; Today's is 5.4 on a ring
/// of radius 52), clockwise, and anticlockwise in a right-to-left language as the phone's `RingArc`
/// fills; [content], given the ring's side, sits at its centre.
///
/// The ring itself is drawn left to right whatever the feed's direction, and turned anticlockwise
/// by [rtl]'s flip alone: SwiftUI mirrors a rotation under a right-to-left layout, so the feed's
/// direction and the flip together started the arc at 6 o'clock (P4.6 review round 1). [content]
/// keeps the feed's direction. `RingGaugeTests` holds both ways by drawing it (Ruling 64 item 3); a
/// ring that fills elsewhere, as Task 8's tasbeeh ring does, reuses it (plan 4 erratum p4 #15). The
/// tasbeeh ring fills clockwise in every language: it is given no flip.
///
/// [notches] are `appRing`'s ticks: a radial hairline across the stroke, in the track's colour, at
/// each share of the full turn given, from 12 o'clock the way the ring fills, so a notch shows only
/// where the arc runs under it (the tasbeeh set's parts' ends, plan 4 Task 8).
struct RingGauge<Content: View>: View {
    let progress: Float
    let rtl: Bool
    var thickness: CGFloat = 0.12
    var notches: [Double] = []
    @ViewBuilder let content: (CGFloat) -> Content

    var body: some View {
        GeometryReader { box in
            let side = min(box.size.width, box.size.height)
            let width = side * thickness
            ZStack {
                ZStack {
                    Circle()
                        .stroke(TaqwaColor.hairline, lineWidth: width)
                    if progress > 0 {
                        Circle()
                            .trim(from: 0, to: CGFloat(min(progress, 1)))
                            .stroke(TaqwaColor.amber, style: StrokeStyle(lineWidth: width, lineCap: .round))
                            .rotationEffect(.degrees(-90))
                            .scaleEffect(x: rtl ? -1 : 1, y: 1)
                            .widgetAccentable()
                    }
                    if !notches.isEmpty {
                        RingNotches(notches: notches, width: width)
                            .stroke(TaqwaColor.hairline, style: StrokeStyle(lineWidth: width * RingNotches.share, lineCap: .butt))
                            .scaleEffect(x: rtl ? -1 : 1, y: 1)
                    }
                }
                .environment(\.layoutDirection, .leftToRight)
                content(side)
            }
            .padding(width / 2)
            .frame(width: box.size.width, height: box.size.height)
        }
    }
}

/// `appRing`'s ticks on a ring drawn in this shape's box, its stroke [width] wide on the box's
/// inscribed circle: for each share of the full turn, a radial line from 12 o'clock clockwise, across
/// the stroke and a little past each edge (0.3 units on a 5.2-unit stroke), drawn `share` of the
/// stroke wide (1.1 units on 5.2).
struct RingNotches: Shape {
    let notches: [Double]
    let width: CGFloat

    /// A notch's width, and how far it runs past each edge, as shares of the stroke's width.
    static let share: CGFloat = 1.1 / 5.2
    static let overhang: CGFloat = 0.3 / 5.2

    func path(in rect: CGRect) -> Path {
        let radius = min(rect.width, rect.height) / 2
        let inner = radius - width / 2 - width * Self.overhang
        let outer = radius + width / 2 + width * Self.overhang
        var path = Path()
        for share in notches {
            let angle = share * 2 * .pi
            let x = CGFloat(sin(angle)), y = CGFloat(-cos(angle))
            path.move(to: CGPoint(x: rect.midX + inner * x, y: rect.midY + inner * y))
            path.addLine(to: CGPoint(x: rect.midX + outer * x, y: rect.midY + outer * y))
        }
        return path
    }
}
