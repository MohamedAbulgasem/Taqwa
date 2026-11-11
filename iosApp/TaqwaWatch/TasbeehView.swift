import Accessibility
import SwiftUI
import WatchKit

/// The tasbeeh counter's life above the pager (plan 4 Task 8): the phrase being counted, its
/// `TasbeehCounter`, the one `TasbeehFeel`, the store, and the one pending task that sleeps until
/// the counter's next deadline, calls `advance(to:)` and saves what it answers. Each change replaces
/// that task. It lives in the app, not on the page, because the pager keeps no page it does not
/// show: a page's own state would end, with the last 0.3 s of counts and the hold, at the first
/// swipe away. The page settles it whenever it is left, and the app whenever it leaves `.active`,
/// so a wrist lowered mid-burst loses nothing (the Galaxy's `WatchTasbeeh`, its twin).
@MainActor
final class TasbeehSession: ObservableObject {
    @Published private(set) var counter: TasbeehCounter
    /// The phrases the list shows: the seven, then his own from the feed held.
    @Published private(set) var list: [TasbeehPreset]

    private var feel = TasbeehFeel()
    private let store: TasbeehStore
    private let clock: () -> Double
    private var pending: Task<Void, Never>?

    /// The session on [store], with [feed]'s phrases, at the phrase chosen; time is the watch's
    /// monotonic clock, `ProcessInfo.systemUptime`, never shown.
    init(store: TasbeehStore, feed: WatchFeed?, clock: @escaping () -> Double = { ProcessInfo.processInfo.systemUptime }) {
        self.store = store
        self.clock = clock
        let list = TasbeehPresets.list(feed)
        self.list = list
        let preset = store.selected(in: list)
        counter = TasbeehCounter(preset: preset, state: store.state(for: preset.id), now: clock())
        schedule()
    }

    /// [feed]'s phrases, when the feed held changes. The phrase chosen is found again: when the phone
    /// no longer sends it, After prayer is counted and becomes the choice; when the phone has changed
    /// its words or its target, the counter shows them, its count held to the new target.
    func use(_ feed: WatchFeed?) {
        let list = TasbeehPresets.list(feed)
        guard list != self.list else { return }
        self.list = list
        let preset = store.selected(in: list)
        if preset.id == counter.preset.id {
            guard preset != counter.preset else { return }
            if counter.writeDue != nil { store.save(counter.state) }
            counter = TasbeehCounter(preset: preset, state: counter.state, now: clock())
        } else {
            settle()
            counter = TasbeehCounter(preset: preset, state: store.state(for: preset.id), now: clock())
        }
        schedule()
    }

    /// [detents]: a tap or a detent up is one count, a detent back one taken away, each through the
    /// one counter, and the turn plays the one haptic `TasbeehFeel` chooses.
    func turn(_ detents: Int) {
        let now = clock()
        let result = counter.turn(detents, now: now)
        if let haptic = feel.choose(result, now: now) { Haptics.play(haptic) }
        schedule()
    }

    /// Counts the phrase [id] from its own count; the one left is settled and saved first.
    func select(_ id: String) {
        guard id != counter.preset.id, let preset = list.first(where: { $0.id == id }) else { return }
        settle()
        store.selectedId = id
        counter = TasbeehCounter(preset: preset, state: store.state(for: preset.id), now: clock())
        schedule()
    }

    /// "Reset the count for this dhikr?", answered yes: 0 of round 1, saved at once.
    func reset() {
        store.save(counter.reset(now: clock()))
        schedule()
    }

    /// The phone's flush: a pending hold rolls over and a pending write is saved now. Called when
    /// the page is left and when the app leaves `.active`.
    func settle() {
        pending?.cancel()
        pending = nil
        let pendingWork = counter.nextDeadline != nil
        let state = counter.settle()
        if pendingWork { store.save(state) }
    }

    private func schedule() {
        pending?.cancel()
        guard let deadline = counter.nextDeadline else {
            pending = nil
            return
        }
        let wait = max(0, deadline - clock())
        pending = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(wait * 1_000_000_000))
            guard !Task.isCancelled else { return }
            self?.due()
        }
    }

    private func due() {
        if let save = counter.advance(to: clock()) { store.save(save) }
        schedule()
    }
}

/// The Tasbeeh page for a feed the watch holds (plan 4 Task 8, `faces.js`'s `aTasbeeh`): the
/// counter, which a tap anywhere and the crown count, and the phrase list behind the dhikr's line.
///
/// The crown is read by `digitalCrownRotation(detent:)` on the focused page, its own haptic off,
/// into `CrownCounter`, seeded afresh when the page appears and when another phrase is chosen; each
/// whole detent goes through the session's one counter. A swipe moves between the pages, so the
/// crown is free here.
///
/// VoiceOver (Ruling 38): the page is one element, `tasbeeh.counter`, labelled with the dhikr's
/// name and valued with the count as the phone says them (`TasbeehSpeech`), in the feed's language.
/// Its default action counts, with the feed's `countAction` as its hint; its named actions are
/// `undoAction` and `phrasesAction`, in that order, so the list is reached without the touch path
/// (plan 4 errata p4 #1 and #6); an adjustable increment counts and a decrement takes one away.
/// After a count or a turn back from the default or a named action it announces the new value, so a
/// part's and the set's end are heard; VoiceOver reads an adjustable change itself. The label and
/// value are always set, so the element keeps its identity as they change (Ruling 64 item 5).
struct TasbeehPage: View {
    let feed: WatchFeed
    @ObservedObject var session: TasbeehSession
    @State private var listShown = false
    @State private var crownValue = 0.0
    @State private var crown = CrownCounter()
    @FocusState private var crownFocused: Bool

    var body: some View {
        // The page's own frame, the safe area the pager leaves it: it stops under the status bar
        // and above the page dots' band (y 40 to 171 of the SE 3's 197 points, 53 to 212 of the
        // Series 12's 248). Its foot is where the hint stands from (`TasbeehFoot`).
        GeometryReader { page in
            counter(pageFoot: page.frame(in: .global).maxY)
        }
    }

    @ViewBuilder private func counter(pageFoot: CGFloat) -> some View {
        let counter = session.counter
        let model = TasbeehScreen.model(feed, preset: counter.preset, state: counter.state)
        TasbeehView(
            model: model, crownOnLeft: WKInterfaceDevice.current().crownOrientation == .left,
            pageFoot: pageFoot, count: { session.turn(1) }, openList: openList
        )
            // The counter draws over the whole screen, beyond the page's own frame: the dhikr's line
            // and the hint stand outside it. A tap counts on the whole drawing (`TasbeehView`),
            // since a modifier after this one would cover the page's frame alone.
            .ignoresSafeArea()
            .focusable()
            .focused($crownFocused)
            .focusEffectDisabled()
            .digitalCrownRotation(
                detent: $crownValue, from: -1_000_000, through: 1_000_000, by: 1,
                sensitivity: .medium, isContinuous: true, isHapticFeedbackEnabled: false
            )
            .onChange(of: crownValue) { _, value in
                let detents = crown.turned(to: value)
                if detents != 0 { session.turn(detents) }
            }
            .onChange(of: counter.preset.id) { _, _ in seedTheCrown() }
            .onAppear {
                seedTheCrown()
                crownFocused = true
            }
            .onDisappear { session.settle() }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(Speech.line(model.spokenName, language: model.language)))
            .accessibilityValue(Text(Speech.line(model.spokenState, language: model.language)))
            .accessibilityHint(Text(Speech.line(model.countAction, language: model.language)))
            .accessibilityAction { change(1) }
            .accessibilityActions {
                ForEach(model.actions, id: \.label) { action in
                    Button {
                        switch action.does {
                        case .undo: change(-1)
                        case .phrases: openList()
                        }
                    } label: {
                        Text(Speech.line(action.label, language: model.language))
                    }
                }
            }
            .accessibilityAdjustableAction { direction in
                switch direction {
                case .increment: session.turn(1)
                case .decrement: session.turn(-1)
                @unknown default: break
                }
            }
            .accessibilityIdentifier("tasbeeh.counter")
            .sheet(isPresented: $listShown) {
                PhraseListView(
                    feed: feed,
                    rows: TasbeehScreen.rows(feed, session.list, selected: counter.preset.id),
                    choose: { session.select($0) },
                    reset: { session.reset() }
                )
            }
    }

    /// A count or a turn back from VoiceOver's actions, then the new value said.
    private func change(_ detents: Int) {
        session.turn(detents)
        let counter = session.counter
        let said = TasbeehScreen.model(feed, preset: counter.preset, state: counter.state)
        AccessibilityNotification.Announcement(Speech.line(said.spokenState, language: said.language)).post()
    }

    private func openList() {
        session.settle()
        listShown = true
    }

    /// The crown starts again from where it stands: nothing is counted for it.
    private func seedTheCrown() {
        crown.reseed()
        _ = crown.turned(to: crownValue)
    }
}

/// Where `aTasbeeh` puts things, in its 172 × 212 units, scaled by the screen's width / 172.
///
/// Two places depart from it. The hint at the foot stands clear of the system's page dots on a
/// screen shorter for its width than the 40mm's (`TasbeehFoot`, in WatchCore). The other is the
/// dhikr's line: the drawing sets it at 30 and 45, under the screen's top edge, where watchOS
/// draws its clock (the status bar is 28 points of the SE 3's 197, 30 units), whose digits take
/// the touches on them, so the line would sit under the clock. It stands lower, at 44 and 57,
/// between the clock and the ring's top (61.4), in a size smaller (14 for 15); two lines of a
/// phrase of his are 12 with their first baseline at 42.
enum TasbeehLayout {
    static let dhikrBaseline: CGFloat = 44
    static let dhikrSize: CGFloat = 14
    static let twoLineBaseline: CGFloat = 42
    static let twoLineSize: CGFloat = 12
    static let transliterationBaseline: CGFloat = 57
    /// The line's box and touch area: from the clock's foot to the ring's top, 136 wide.
    static let lineTop: CGFloat = 30
    static let lineBottom: CGFloat = 61
    static let lineWidth: CGFloat = 136
    static let ringCentreY: CGFloat = 118
    static let ringRadius: CGFloat = 54
    static let ringWidth: CGFloat = 5.2
    static let roundBaseline: CGFloat = 99
    static let countBaseline: CGFloat = 131
    static let ofBaseline: CGFloat = 147
    /// The parts' dots, under "of 100" and inside the ring.
    static let dotsY: CGFloat = 158
    static let dotPitch: CGFloat = 8
    // The hint's baseline is `TasbeehFoot`'s: the drawing's 198, but clear of the page dots.
    /// The crown's two arrows, beside the crown at the top right: up counts one, down takes one back.
    static let arrowsX: ClosedRange<CGFloat> = 161...169
}

/// The counter as `aTasbeeh` draws it: the dhikr in the system font (a built-in's Arabic, his phrase
/// as typed), its transliteration under a Latin interface, the ring filling clockwise in every
/// language with its notches at the parts' ends, "ROUND n" in the accent, the count in Manrope Light
/// 36, "of 100" and the parts' dots, the crown's arrows on the crown's side, and the hint at the
/// foot. The dhikr's line opens the phrase list.
struct TasbeehView: View {
    let model: TasbeehScreenModel
    /// Whether the wearer has the crown on the left (`WKInterfaceDevice.crownOrientation`): the
    /// arrows stay on the crown's side.
    let crownOnLeft: Bool
    /// Where the page's own frame ends, in the screen's points from its top: the top of the page
    /// dots' band, which the hint stands clear of (`TasbeehFoot`).
    let pageFoot: CGFloat
    /// A tap anywhere on the screen but the dhikr's line: one count.
    let count: () -> Void
    let openList: () -> Void
    /// The watch's text size, which the lines grow with up to the Galaxy's caps (`TasbeehType`,
    /// plan 3's call 15), from the drawing's sizes at the watch's own default (Large on 40 to
    /// 42mm, XLarge on 44mm and larger, from which the body style scales); the ring keeps its
    /// size. It is read from a base of 100 points, which the pixel grid's rounding cannot hide a
    /// step of (`TasbeehType.scaleBase`).
    @ScaledMetric(relativeTo: .body) private var bodyPoints = CGFloat(TasbeehType.scaleBase)

    /// How much a line whose cap is [cap] grows at the watch's text size.
    private func grow(_ cap: Double) -> CGFloat {
        CGFloat(TasbeehType.grow(TasbeehType.scale(Double(bodyPoints)), cap: cap))
    }

    var body: some View {
        GeometryReader { box in
            let k = box.size.width / 172
            ZStack(alignment: .top) {
                ring(k)
                dhikr(k)
                labels(k, hintBaseline: hintBaseline(box))
                CrownArrows(k: k, onLeft: crownOnLeft)
            }
            .frame(width: box.size.width, height: box.size.height, alignment: .top)
            // The whole screen counts, the hint and the page dots' row at the foot included: this
            // frame is the screen's, where the page's own stops short of both. The dhikr's line
            // keeps its own tap, which opens the list.
            .contentShape(Rectangle())
            .onTapGesture(perform: count)
        }
        .ignoresSafeArea(edges: [.top, .bottom])
    }

    /// The hint's baseline from this drawing's top, which is the screen's.
    private func hintBaseline(_ box: GeometryProxy) -> CGFloat {
        let foot = pageFoot - box.frame(in: .global).minY
        return CGFloat(TasbeehFoot.hintBaseline(width: Double(box.size.width), pageFoot: Double(foot)))
    }

    private func ring(_ k: CGFloat) -> some View {
        let side = (2 * TasbeehLayout.ringRadius + TasbeehLayout.ringWidth) * k
        return RingGauge(
            progress: Float(model.progress), rtl: false, thickness: TasbeehLayout.ringWidth / (side / k), notches: model.passedNotches
        ) { _ in EmptyView() }
            .frame(width: side, height: side)
            .padding(.top, TasbeehLayout.ringCentreY * k - side / 2)
    }

    /// The dhikr's line, which opens the list: a built-in above its transliteration on one line; a
    /// phrase with none (his own, or any outside Latin letters) on one line when it fits and on two
    /// smaller ones when it does not. The line's touch area runs from under the clock to the ring.
    ///
    /// With the watch's text size the one line grows up to 1.15 times: it is drawn at the largest of
    /// its grown size and two steps between it and the drawing's at which it fits the line, and at
    /// the drawing's size otherwise. Two lines keep theirs: they fill the room between the clock and
    /// the ring at 12, as the Galaxy draws a two-line phrase smaller to end above "ROUND n".
    private func dhikr(_ k: CGFloat) -> some View {
        let transliterated = model.transliteration != nil
        let top = TasbeehLayout.lineTop
        let size = TasbeehLayout.dhikrSize * k
        let grown = grow(TasbeehType.dhikr)
        return ZStack(alignment: .top) {
            ViewThatFits(in: .horizontal) {
                oneLine(size * grown)
                oneLine(size * (1 + (grown - 1) * 2 / 3))
                oneLine(size * (1 + (grown - 1) / 3))
                oneLine(size)
                Text(model.arabic)
                    .font(.system(size: (transliterated ? TasbeehLayout.dhikrSize : TasbeehLayout.twoLineSize) * k, weight: .bold))
                    .multilineTextAlignment(.center)
                    .lineLimit(transliterated ? 1 : 2)
                    .minimumScaleFactor(transliterated ? 0.7 : 0.85)
                    // Two lines stand their first baseline higher, at twoLineBaseline.
                    .alignmentGuide(.firstTextBaseline) { $0[.firstTextBaseline] + (transliterated ? 0 : TasbeehLayout.dhikrBaseline - TasbeehLayout.twoLineBaseline) * k }
            }
            .foregroundStyle(TaqwaColor.ink)
            .baseline(at: (TasbeehLayout.dhikrBaseline - top) * k)
            if let transliteration = model.transliteration {
                Text(transliteration)
                    .font(TaqwaFont.of(.semiBold, 9.5 * k * grow(TasbeehType.transliteration), latin: true))
                    .foregroundStyle(TaqwaColor.mist)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                    .baseline(at: (TasbeehLayout.transliterationBaseline - top) * k)
            }
        }
        .frame(width: TasbeehLayout.lineWidth * k, height: (TasbeehLayout.lineBottom - top) * k, alignment: .top)
        .contentShape(Rectangle())
        .onTapGesture(perform: openList)
        .accessibilityIdentifier("tasbeeh.dhikr")
        .padding(.top, top * k)
    }

    /// The dhikr on one line at [size].
    private func oneLine(_ size: CGFloat) -> some View {
        Text(model.arabic)
            .font(.system(size: size, weight: .bold))
            .lineLimit(1)
            .fixedSize()
    }

    @ViewBuilder private func labels(_ k: CGFloat, hintBaseline: CGFloat) -> some View {
        let latin = model.latinScript
        Text(model.roundLabel)
            .font(TaqwaFont.of(.extraBold, TaqwaFont.labelSize(9.5, latin: latin) * k * grow(TasbeehType.round), latin: latin))
            .tracking(TaqwaFont.tracking(1.2 * k, latin: latin))
            .foregroundStyle(TaqwaColor.amber)
            .lineLimit(1)
            .minimumScaleFactor(0.7)
            .frame(maxWidth: 80 * k)
            .baseline(at: TasbeehLayout.roundBaseline * k)
        Text(model.count)
            .font(TaqwaFont.of(.light, 36 * k * grow(TasbeehType.count), latin: latin))
            .monospacedDigit()
            .foregroundStyle(TaqwaColor.ink)
            .lineLimit(1)
            .minimumScaleFactor(0.6)
            .frame(maxWidth: 90 * k)
            .baseline(at: TasbeehLayout.countBaseline * k)
        Text(model.ofLabel)
            .font(TaqwaFont.of(.semiBold, 10.5 * k * grow(TasbeehType.of), latin: latin))
            .foregroundStyle(TaqwaColor.mist)
            .lineLimit(1)
            .minimumScaleFactor(0.7)
            .frame(maxWidth: 80 * k)
            .baseline(at: TasbeehLayout.ofBaseline * k)
        if model.partDots > 0 {
            PartDots(count: model.partDots, current: model.currentPart, k: k)
                .padding(.top, TasbeehLayout.dotsY * k - 2 * k)
        }
        Text(model.hint)
            .font(TaqwaFont.of(.semiBold, 10 * k * grow(TasbeehType.hint), latin: latin))
            .foregroundStyle(TaqwaColor.mist)
            .lineLimit(1)
            .minimumScaleFactor(0.7)
            .frame(maxWidth: 156 * k)
            .baseline(at: hintBaseline)
            .accessibilityIdentifier("tasbeeh.hint")
    }
}

/// `gTasbeeh`'s parts' dots: the current part's in the accent, a part done in slate, one to come
/// hollow. In a right-to-left language they run right to left, as the phone's part reminder does.
private struct PartDots: View {
    let count: Int
    let current: Int
    let k: CGFloat

    var body: some View {
        HStack(spacing: (TasbeehLayout.dotPitch - 3.6) * k) {
            ForEach(0..<count, id: \.self) { index in
                Group {
                    if index <= current {
                        Circle().fill(index == current ? TaqwaColor.amber : TaqwaColor.slate)
                    } else {
                        Circle().strokeBorder(TaqwaColor.slate, lineWidth: 0.8 * k)
                    }
                }
                .frame(width: 3.6 * k, height: 3.6 * k)
            }
        }
        .frame(height: 4 * k)
    }
}

/// The crown's arrows (`aTasbeeh`): two chevrons in the accent beside the crown, up over down. They
/// stay on the crown's side in every language.
private struct CrownArrows: View {
    let k: CGFloat
    let onLeft: Bool

    var body: some View {
        Path { path in
            path.move(to: point(161, 54))
            path.addLine(to: point(165, 50))
            path.addLine(to: point(169, 54))
            path.move(to: point(161, 64))
            path.addLine(to: point(165, 68))
            path.addLine(to: point(169, 64))
        }
        .stroke(TaqwaColor.amber, style: StrokeStyle(lineWidth: 1.7 * k, lineCap: .round, lineJoin: .round))
        .environment(\.layoutDirection, .leftToRight)
    }

    private func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
        CGPoint(x: (onLeft ? 172 - x : x) * k, y: y * k)
    }
}

private extension View {
    /// Places this text so that its first baseline falls [y] below the top of its stack.
    func baseline(at y: CGFloat) -> some View {
        alignmentGuide(.top) { $0[.firstTextBaseline] - y }
    }
}
