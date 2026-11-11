import Accessibility
import Combine
import SwiftUI
import WatchKit

/// The Qibla page's compass while the page is on the screen (plan 4 Task 9): its `HeadingSource`,
/// started only while the page is shown and the app active, and its `QiblaCompass` on the feed's
/// bearing and declination, which rests the page whenever the compass is off (a lowered wrist on an
/// Always On watch keeps the app frontmost and dimmed) and until the first heading of each start.
/// Each heading is taken with the crown's side, so a watch worn with the crown on the left points by
/// the screen's top. Each buzz plays the Lead's call 7's `.success`, as the alignment begins, and
/// each label a heading changes is sent to `announcements`.
@MainActor
final class QiblaSession: ObservableObject {
    @Published private(set) var model: QiblaDialModel
    /// The page's new label after each heading that changed it, for VoiceOver to announce. Nothing
    /// else is sent: a stop, a start or a new feed tells VoiceOver nothing.
    let announcements = PassthroughSubject<String, Never>()

    private let source: HeadingSource
    private let compass: QiblaCompass

    init(feed: WatchFeed, source: HeadingSource = HeadingSources.make()) {
        self.source = source
        compass = QiblaCompass(feed: feed, available: source.available)
        model = compass.model
    }

    /// The feed held changed: a new bearing or declination, or new words. A running compass keeps
    /// running, judged afresh on the new feed.
    func use(_ feed: WatchFeed) {
        guard compass.use(feed) else { return }
        publish()
    }

    /// Starts the compass afresh, the page at rest until its first heading.
    func start() {
        if compass.start() {
            publish()
            source.start { [weak self] sample in
                MainActor.assumeIsolated { self?.take(sample) }
            }
        } else if !compass.available {
            // A watch with no compass starts nothing; it is asked all the same, so that a debug
            // build logs once what Core Location says (`CoreLocationHeading.start`).
            source.start { _ in }
        }
    }

    /// Stops the compass, and rests the page: no heading is known while it is off.
    func stop() {
        guard compass.stop() else { return }
        source.stop()
        publish()
    }

    private func take(_ sample: HeadingSample) {
        guard let change = compass.take(sample, crownOnLeft: WKInterfaceDevice.current().crownOrientation == .left) else { return }
        publish()
        if let line = change.announcement { announcements.send(line) }
        if change.buzz { WKInterfaceDevice.current().play(.success) }
    }

    private func publish() {
        if compass.model != model { model = compass.model }
    }
}

/// The Qibla page for a feed the watch holds (plan 4 Task 9, `faces.js`'s `aQibla`): the dial turned
/// by the watch's compass, then the caption and the detail under it.
///
/// VoiceOver (Ruling 38): the page is one element, `qibla.dial`, labelled with what the page says
/// (the turn while searching, "Facing qibla", a bad state's title) and valued with the detail as it
/// is heard, in the feed's language (plan 4 erratum p4 #5). Both are always set, so the element
/// keeps its identity as they change (Ruling 64 item 5). While VoiceOver runs, each heading that
/// changes what the page says is announced (`QiblaSession.announcements`), so a wearer searching
/// hears each new turn (the tracker holds a turn for 1.5 s and tells only a real change) and then
/// "Facing qibla"; the resting page a stop draws is not announced.
struct QiblaPage: View {
    let feed: WatchFeed
    /// Whether the pager shows this page.
    let visible: Bool
    @StateObject private var session: QiblaSession
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver

    init(feed: WatchFeed, visible: Bool) {
        self.feed = feed
        self.visible = visible
        _session = StateObject(wrappedValue: QiblaSession(feed: feed))
    }

    var body: some View {
        let model = session.model
        QiblaView(model: model)
            .ignoresSafeArea()
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(Speech.line(model.spoken, language: model.language)))
            .accessibilityValue(Text(Speech.line(model.spokenDetail, language: model.language)))
            .accessibilityIdentifier("qibla.dial")
            .onAppear(perform: follow)
            .onDisappear { session.stop() }
            .onChange(of: visible) { _, _ in follow() }
            .onChange(of: scenePhase) { _, _ in follow() }
            .onChange(of: feed) { _, feed in session.use(feed) }
            .onReceive(session.announcements) { spoken in
                guard voiceOver else { return }
                AccessibilityNotification.Announcement(Speech.line(spoken, language: session.model.language)).post()
            }
    }

    /// The compass runs while the page is shown and the app active, and only then.
    private func follow() {
        if visible && scenePhase == .active {
            session.start()
        } else {
            session.stop()
        }
    }
}

/// The page as `aQibla` draws it, in its 172 × 212 units fitted to the screen (`QiblaLayout`): the
/// dial (`QiblaDial`), then the caption at y 180, ExtraBold 14, in the accent when facing the Qibla
/// and in ink otherwise, and the detail at y 196, SemiBold 10, in mist. A bad state's title too wide
/// for one line takes two, growing upward from its last line at y 180, at the drawn size or smaller,
/// never larger, so it stays under the dial. The detail is one line, never cut (Ruling 66 item 8).
struct QiblaView: View {
    let model: QiblaDialModel
    /// The watch's text size, read from a base of 100 as Tasbeeh's lines read it (`TasbeehType`).
    @ScaledMetric(relativeTo: .body) private var bodyPoints = CGFloat(TasbeehType.scaleBase)

    private func grow(_ cap: Double) -> CGFloat {
        CGFloat(TasbeehType.grow(TasbeehType.scale(Double(bodyPoints)), cap: cap))
    }

    var body: some View {
        GeometryReader { box in
            let layout = QiblaLayout(width: Double(box.size.width), height: Double(box.size.height))
            let k = CGFloat(layout.k)
            ZStack(alignment: .top) {
                QiblaDial(model: model, layout: layout, letterGrowth: grow(QiblaType.letters))
                    .frame(width: box.size.width, height: box.size.height)
                caption(k: k, baseline: CGFloat(layout.y(QiblaLayout.captionBaseline)))
                detail(k: k, baseline: CGFloat(layout.y(QiblaLayout.detailBaseline)))
            }
            .frame(width: box.size.width, height: box.size.height, alignment: .top)
        }
    }

    /// The caption on one line, grown with the text size, or at the drawn size; else, a title too
    /// wide for one line, on two (`QiblaCaption`), at the drawn size, each line shrinking to 11 if
    /// it must. ViewThatFits takes the first whose width fits the title's box, 148 units, the
    /// Galaxy's (`QiblaType.title`'s box); each choice draws its lines at their own baselines from
    /// the screen's top, the last at y 180.
    @ViewBuilder private func caption(k: CGFloat, baseline: CGFloat) -> some View {
        let size = 14 * k
        ViewThatFits(in: .horizontal) {
            captionLines([model.caption], size: size * grow(QiblaType.caption), baseline: baseline, k: k, shrink: false)
            captionLines([model.caption], size: size, baseline: baseline, k: k, shrink: false)
            if let lines = QiblaCaption.split(model.caption) {
                captionLines([lines.first, lines.last], size: size, baseline: baseline, k: k, shrink: true)
            } else {
                captionLines([model.caption], size: size, baseline: baseline, k: k, shrink: true)
            }
        }
        .foregroundStyle(model.captionIsAccent ? TaqwaColor.amber : TaqwaColor.ink)
        .frame(width: 148 * k)
    }

    /// [lines] at [size], the last line's baseline at [baseline] and each earlier one
    /// `QiblaCaption.pitch` of the size above the next. The zero-sized anchor keeps the stack's top
    /// at the screen's, so each baseline is the screen's own. A line that may [shrink] does so to 11
    /// in the title's box; one that may not keeps its whole width, which ViewThatFits measures. The
    /// last line is `qibla.caption`.
    private func captionLines(_ lines: [String], size: CGFloat, baseline: CGFloat, k: CGFloat, shrink: Bool) -> some View {
        ZStack(alignment: .top) {
            Color.clear.frame(width: 0, height: 0)
            ForEach(Array(lines.enumerated()), id: \.offset) { index, line in
                let y = baseline - CGFloat(lines.count - 1 - index) * size * CGFloat(QiblaCaption.pitch)
                Text(line)
                    .font(TaqwaFont.of(.extraBold, size, latin: model.latinScript))
                    .lineLimit(1)
                    .fixedSize(horizontal: !shrink, vertical: false)
                    .minimumScaleFactor(shrink ? 11.0 / 14 : 1)
                    .frame(maxWidth: shrink ? 148 * k : nil)
                    .alignmentGuide(.top) { $0[.firstTextBaseline] - y }
                    .accessibilityIdentifier(index == lines.count - 1 ? "qibla.caption" : "qibla.caption.first")
            }
        }
    }

    private func detail(k: CGFloat, baseline: CGFloat) -> some View {
        Text(model.detail)
            .font(TaqwaFont.of(.semiBold, 10 * k * grow(QiblaType.detail), latin: model.latinScript))
            .foregroundStyle(TaqwaColor.mist)
            .lineLimit(1)
            .fixedSize(horizontal: true, vertical: false)
            .alignmentGuide(.top) { $0[.firstTextBaseline] - baseline }
            .accessibilityIdentifier("qibla.detail")
    }
}

/// `qiblaDial`: the rim, 24 ticks (every sixth in mist), the four letters, the needle from the hub
/// to the Kaaba's marker on the rim at the bearing, and, facing the Qibla, the rim lit with its
/// halo. The dial, its ticks and its letters turn by `dialRotation`; the letters stay upright. It
/// is drawn in a fixed left-to-right space, so it never mirrors in a right-to-left language.
struct QiblaDial: View {
    let model: QiblaDialModel
    let layout: QiblaLayout
    /// How much the letters grow with the text size (`QiblaType.letters`).
    let letterGrowth: CGFloat

    var body: some View {
        Canvas { context, _ in
            let k = CGFloat(layout.k)
            let s = CGFloat(QiblaLayout.dialScale)
            let centre = CGPoint(x: layout.x(QiblaLayout.dialCentre.x), y: layout.y(QiblaLayout.dialCentre.y))
            let r = CGFloat(QiblaLayout.dialRadius) * k
            let rotation = model.dialRotation
            func point(_ radius: CGFloat, _ degrees: Double) -> CGPoint {
                let a = (degrees - 90) * .pi / 180
                return CGPoint(x: centre.x + radius * CGFloat(cos(a)), y: centre.y + radius * CGFloat(sin(a)))
            }
            let rim = Path(ellipseIn: CGRect(x: centre.x - r, y: centre.y - r, width: 2 * r, height: 2 * r))
            var dial = context
            if model.dimmed { dial.opacity = 0.4 }
            if model.lit {
                dial.stroke(rim, with: .color(TaqwaColor.amber.opacity(0.18)), lineWidth: 9 * s * k)
            }
            dial.stroke(rim, with: .color(model.lit ? TaqwaColor.amber : TaqwaColor.hairline), lineWidth: (model.lit ? 2.6 : 1.4) * s * k)
            for i in 0..<24 {
                let major = i % 6 == 0
                var tick = Path()
                tick.move(to: point(r - 9 * s * k, Double(i * 15) + rotation))
                tick.addLine(to: point(r - 5 * s * k, Double(i * 15) + rotation))
                dial.stroke(tick, with: .color(major ? TaqwaColor.mist : TaqwaColor.slate), style: StrokeStyle(lineWidth: (major ? 1.4 : 1) * s * k, lineCap: .butt))
            }
            for (index, letter) in model.cardinals.prefix(4).enumerated() {
                let north = index == 0
                let size = (north ? 11 : 9.5) * s * k * letterGrowth
                let text = Text(letter)
                    .font(TaqwaFont.of(north ? .extraBold : .semiBold, size, latin: model.latinScript))
                    .foregroundStyle(north ? TaqwaColor.ink : TaqwaColor.mist)
                dial.draw(text, at: point(r - 19 * s * k, Double(index * 90) + rotation), anchor: .center)
            }
            guard model.needle || model.marker else { return }
            let towards = model.bearing + rotation
            if model.needle {
                var needle = Path()
                needle.move(to: centre)
                needle.addLine(to: point(r - 26 * s * k, towards))
                dial.stroke(needle, with: .color(TaqwaColor.amber), style: StrokeStyle(lineWidth: 3 * s * k, lineCap: .round))
                let hub = 4.6 * s * k
                dial.fill(Path(ellipseIn: CGRect(x: centre.x - hub, y: centre.y - hub, width: 2 * hub, height: 2 * hub)), with: .color(TaqwaColor.amber))
            }
            if model.marker {
                let at = point(r, towards)
                let size = 11 * s * k
                let halo = size * 0.95
                dial.fill(Path(ellipseIn: CGRect(x: at.x - halo, y: at.y - halo, width: 2 * halo, height: 2 * halo)), with: .color(TaqwaColor.amber.opacity(0.22)))
                let cube = CGRect(x: at.x - size / 2, y: at.y - size / 2, width: size, height: size)
                dial.fill(Path(roundedRect: cube, cornerRadius: size * 0.16), with: .color(TaqwaColor.amber))
                dial.fill(Path(CGRect(x: cube.minX, y: cube.minY + size * 0.26, width: size, height: size * 0.17)), with: .color(TaqwaColor.black))
            }
        }
        .environment(\.layoutDirection, .leftToRight)
        .accessibilityHidden(true)
    }
}
