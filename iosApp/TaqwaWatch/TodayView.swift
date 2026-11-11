import SwiftUI

/// The Today page (plan 4 Task 7, `faces.js`'s `aToday`) for a feed the watch holds: drawn again
/// each second, and each minute without the seconds while the always-on screen dims it
/// (`isLuminanceReduced`). Each frame is `Today.screen` at the frame's own date through the app's
/// clock (`AppClock.at`), and reads the feed's words once (plan 4 erratum p4 #12): the ring above
/// the day, or a spent feed's word.
struct TodayPage: View {
    let feed: WatchFeed
    let newerVersionSeen: Bool
    let clock: AppClock
    @Environment(\.isLuminanceReduced) private var isLuminanceReduced

    var body: some View {
        // Whole seconds, or on the always-on screen whole minutes, of the watch's clock: the
        // countdown's second and minute turn there. One schedule type serves both.
        let step: TimeInterval = isLuminanceReduced ? 60 : 1
        let origin = Date(timeIntervalSince1970: (Date().timeIntervalSince1970 / 60).rounded(.down) * 60)
        TimelineView(.periodic(from: origin, by: step)) { context in
            let now = clock.at(context.date)
            switch Today.screen(feed, newerVersionSeen: newerVersionSeen, now: now, seconds: !isLuminanceReduced) {
            case let .ready(model):
                TodayView(model: model)
            case let .spent(word):
                MessageView(feedWord: word, of: feed)
            case let .message(own):
                MessageView(own: own)
            }
        }
    }
}

/// Today, ready (`aToday`, drawn in a 172 × 212 space and scaled by the screen's width / 172): the
/// phone's countdown ring, radius 52 and width 5.4, with the label in the accent, the countdown in
/// Manrope Light 23 with tabular figures, and the next start's time in mist (the update notice in
/// the accent in its place); below it the day's six rows (five where the phone hides Sunrise), a
/// list that opens scrolled to the lit row or the next.
///
/// VoiceOver (Ruling 38, Ruling 42: the prayer first): the ring is one element, labelled with the
/// model's sentence and read first; then each row is one element in the order of the day, labelled
/// as the phone says the row, with its state as the value. Every line carries the feed's language
/// (plan 4 erratum p4 #5). The ring's stroke and the pips are no elements.
struct TodayView: View {
    let model: TodayModel
    /// The watch's text size, for the rows alone, which grow with it up to 1.3 times as the Galaxy's
    /// do (plan 3's call 15); the ring keeps its size.
    @ScaledMetric(relativeTo: .body) private var textScale: CGFloat = 1

    var body: some View {
        GeometryReader { box in
            let k = box.size.width / 172
            VStack(spacing: 0) {
                TodayRing(model: model, k: k)
                    .frame(width: box.size.width, height: TodayLayout.listTop * k, alignment: .top)
                TodayRows(model: model, k: k, grow: min(max(textScale, 1), 1.3))
            }
        }
        .ignoresSafeArea(edges: [.top, .bottom])
    }
}

/// Where `aToday` puts things, in its 172 × 212 units.
enum TodayLayout {
    static let ringCentreY: CGFloat = 66
    static let ringRadius: CGFloat = 52
    static let ringWidth: CGFloat = 5.4
    static let labelBaseline: CGFloat = 52
    static let countdownBaseline: CGFloat = 77
    static let timeBaseline: CGFloat = 92
    /// The update notice in the time's place: up to two lines, set a little smaller and higher than
    /// the time and in a narrower box, so that both lines stay inside the ring, and shrunk to 0.7
    /// at most where a language's notice is longer (French). The drawing has no notice.
    static let noticeSize: CGFloat = 9.5
    static let noticeBaseline: CGFloat = 90
    static let noticeWidth: CGFloat = 64
    /// The list's top: a row is 21 high with its text centred, so the first row's baseline falls
    /// near 144, as the drawing's lit row's does.
    static let listTop: CGFloat = 129
    static let rowPitch: CGFloat = 21
    /// Sunrise's row: 10/12 of a prayer's, as the Galaxy's list sets it (`gTile`).
    static let sunrisePitch: CGFloat = 17
    static let sunriseScale: CGFloat = 10.0 / 12.0
    static let pipX: CGFloat = 20
    static let nameX: CGFloat = 32
    static let timeEnd: CGFloat = 156
}

/// The ring, one element.
private struct TodayRing: View {
    let model: TodayModel
    let k: CGFloat

    var body: some View {
        let latin = model.latinScript
        let side = (2 * TodayLayout.ringRadius + TodayLayout.ringWidth) * k
        ZStack(alignment: .top) {
            RingGauge(progress: model.progress, rtl: model.mirrorsRing, thickness: TodayLayout.ringWidth / (side / k)) { _ in EmptyView() }
                .frame(width: side, height: side)
                .padding(.top, TodayLayout.ringCentreY * k - side / 2)
                .accessibilityHidden(true)
            Text(model.ringLabel)
                .font(TaqwaFont.of(.extraBold, TaqwaFont.labelSize(8.8, latin: latin) * k, latin: latin))
                .tracking(TaqwaFont.tracking(1.2 * k, latin: latin))
                .foregroundStyle(TaqwaColor.amber)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .frame(maxWidth: 92 * k)
                .baseline(at: TodayLayout.labelBaseline * k)
            Text(model.countdown)
                .font(TaqwaFont.of(.light, 23 * k, latin: latin))
                .monospacedDigit()
                .tracking(TaqwaFont.tracking(-0.4 * k, latin: latin))
                .foregroundStyle(TaqwaColor.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .frame(maxWidth: 96 * k)
                .baseline(at: TodayLayout.countdownBaseline * k)
            if model.notice {
                Text(model.time)
                    .font(TaqwaFont.of(.semiBold, TodayLayout.noticeSize * k, latin: latin))
                    .foregroundStyle(TaqwaColor.amber)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .minimumScaleFactor(0.7)
                    .frame(maxWidth: TodayLayout.noticeWidth * k)
                    .baseline(at: TodayLayout.noticeBaseline * k)
            } else {
                Text(model.time)
                    .font(TaqwaFont.of(.semiBold, 10.5 * k, latin: latin))
                    .foregroundStyle(TaqwaColor.mist)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                    .frame(maxWidth: 84 * k)
                    .baseline(at: TodayLayout.timeBaseline * k)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(Speech.line(model.spoken, language: model.language)))
        .accessibilityIdentifier("today.ring")
    }
}

/// The day's rows below the ring, six, or five where the phone hides Sunrise (`Today.screen`), in a
/// list the crown scrolls, opened scrolled to the lit row, or with none lit to the next start's
/// (`TodayModel.focus`). Each row is one element.
///
/// The list goes to its focus whenever it is seen afresh, by `TodayScroll`'s rule (Ruling 64 item
/// 4): when it first appears; when the app is opened again after it went to the background (watchOS
/// keeps the app's views and resumes them, so `onAppear` does not run then); and when the focus
/// moves with the app open (the frames of the `TimelineView` above keep the list's identity): the
/// next start lit, Sunrise ending Fajr, or the day turning over at the feed's midnight. A lowered
/// and raised wrist, or a banner, keeps the wearer's scroll.
private struct TodayRows: View {
    let model: TodayModel
    let k: CGFloat
    let grow: CGFloat
    @Environment(\.scenePhase) private var scenePhase
    @State private var scroll = TodayScroll()

    var body: some View {
        ScrollViewReader { reader in
            ScrollView {
                VStack(spacing: 0) {
                    ForEach(model.rows, id: \.time) { row in
                        TodayRowView(row: row, language: model.language, latin: model.latinScript, k: k, grow: grow)
                            .id(row.time)
                    }
                }
                .padding(.bottom, 2 * TodayLayout.rowPitch * k)
            }
            .scrollIndicators(.hidden)
            .mask(
                // The drawing dims the row at the screen's foot to a third.
                LinearGradient(
                    stops: [.init(color: .black, location: 0), .init(color: .black, location: 0.7), .init(color: .black.opacity(0.32), location: 0.82)],
                    startPoint: .top, endPoint: .bottom
                )
            )
            .onAppear { show(model.focus, in: reader) }
            .onChange(of: scenePhase) { _, phase in
                if scroll.refocuses(entering: TodayScroll.Phase(phase)) { show(model.focus, in: reader) }
            }
            .onChange(of: model.focus) { old, focus in
                if TodayScroll.refocuses(focusFrom: old, to: focus) { show(focus, in: reader) }
            }
        }
    }

    /// Puts [focus]'s row at the list's top.
    private func show(_ focus: WatchTime?, in reader: ScrollViewProxy) {
        if let focus { reader.scrollTo(focus, anchor: .top) }
    }
}

private extension TodayScroll.Phase {
    init(_ phase: ScenePhase) {
        switch phase {
        case .active: self = .active
        case .background: self = .background
        default: self = .inactive
        }
    }
}

/// One row: the pip, the name and the clock time, as `aToday` and `gTile` draw them. Sunrise is
/// smaller, in slate, with no pip.
private struct TodayRowView: View {
    let row: TodayRow
    let language: String
    let latin: Bool
    let k: CGFloat
    let grow: CGFloat

    var body: some View {
        let size = 13 * k * grow * (row.small ? TodayLayout.sunriseScale : 1)
        let pitch = (row.small ? TodayLayout.sunrisePitch : TodayLayout.rowPitch) * k * grow
        HStack(spacing: 0) {
            Pip(status: row.small ? nil : row.status, k: k)
                .frame(width: 2 * (TodayLayout.nameX - TodayLayout.pipX) * k)
                .padding(.leading, (2 * TodayLayout.pipX - TodayLayout.nameX) * k)
            Text(row.name)
                .font(TaqwaFont.of(row.status == .current && !row.small ? .extraBold : .semiBold, size, latin: latin))
                .foregroundStyle(nameColour)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Spacer(minLength: 3 * k)
            Text(row.clock)
                .font(TaqwaFont.of(.semiBold, size, latin: latin))
                .monospacedDigit()
                .foregroundStyle(row.small || row.status == .passed ? TaqwaColor.slate : TaqwaColor.ink)
                .lineLimit(1)
                .fixedSize()
                .padding(.trailing, (172 - TodayLayout.timeEnd) * k)
        }
        .frame(height: pitch)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(Speech.line(row.spoken, language: language)))
        .modifier(StateValue(word: row.stateWord, language: language))
        .accessibilityIdentifier("today.row.\(row.time.rawValue)")
    }

    private var nameColour: Color {
        if row.small { return TaqwaColor.slate }
        switch row.status {
        case .current: return TaqwaColor.amber
        case .passed: return TaqwaColor.slate
        case .upcoming: return TaqwaColor.ink
        }
    }
}

/// `faces.js`'s `pip`: the lit row's accent disc in a halo, a passed row's slate dot, or a hollow
/// ring for one to come; nothing for Sunrise (nil).
private struct Pip: View {
    let status: RowState?
    let k: CGFloat

    var body: some View {
        ZStack {
            switch status {
            case .current:
                Circle().fill(TaqwaColor.amber.opacity(0.2)).frame(width: 11.2 * k, height: 11.2 * k)
                Circle().fill(TaqwaColor.amber).frame(width: 6.6 * k, height: 6.6 * k)
            case .passed:
                Circle().fill(TaqwaColor.slate).frame(width: 4.8 * k, height: 4.8 * k)
            case .upcoming:
                Circle().strokeBorder(TaqwaColor.hairline, lineWidth: 1.3 * k).frame(width: 5.9 * k, height: 5.9 * k)
            case nil:
                EmptyView()
            }
        }
        .accessibilityHidden(true)
    }
}

private extension View {
    /// Places this text so that its first baseline falls [y] below the top of its stack.
    func baseline(at y: CGFloat) -> some View {
        alignmentGuide(.top) { $0[.firstTextBaseline] - y }
    }
}
