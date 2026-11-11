import Foundation

/// The Qibla page's sample, reading, drawing and words (plan 4 Task 9, `faces.js`'s `aQibla` and
/// `qiblaDial`): Core Location's heading made true by the feed's declination, judged by
/// `WatchQibla`, and drawn by `QiblaScreen`, plan 3's `qiblaScreen` without the tilt, since the
/// Apple Watch reads no Core Motion and never says "Hold your wrist level". The watch never learns
/// where it is (Ruling 39): the bearing is the phone's whole degree and the distance its own figure.

/// One heading as Core Location gives it: `CLHeading.magneticHeading` and `headingAccuracy`, in
/// degrees, at a time on the watch's monotonic clock, in milliseconds.
struct HeadingSample: Equatable {
    let magnetic: Double
    let accuracy: Double
    let millis: Int64
}

/// The iPhone's `IosCompassSource` rule (`CompassSource.ios.kt`): the true heading is the magnetic
/// one corrected by the declination, and Core Location's sentinel, a negative heading or no
/// declination, is sent as 0 and low, never passed on as 359°; a sample is low by
/// `CompassAccuracyRules.iosSampleIsLow`, its reason CALIBRATION, whose words name metal and
/// speakers.
enum HeadingReading {
    static func reading(_ sample: HeadingSample, declination: Double?) -> CompassReading {
        let heading = TrueNorth.fromMagneticOrInvalid(sample.magnetic, declination)
        return CompassReading(
            trueHeadingDegrees: CompassAccuracyRules.iosTrueHeadingIsInvalid(heading) ? 0 : heading,
            timestampMillis: sample.millis,
            isLowAccuracy: CompassAccuracyRules.iosSampleIsLow(heading, sample.accuracy),
            lowReason: .CALIBRATION
        )
    }

    /// Whether the sample gives a direction at all: false for the sentinel, whose reading points
    /// north only because the iPhone's rule writes it as 0.
    static func hasDirection(_ sample: HeadingSample, declination: Double?) -> Bool {
        !CompassAccuracyRules.iosTrueHeadingIsInvalid(TrueNorth.fromMagneticOrInvalid(sample.magnetic, declination))
    }
}

/// What the Qibla page draws and says.
///
/// - `dialRotation`: the dial, its ticks and its letters turned by −heading; 0, north up, when
///   there is no heading. It never mirrors.
/// - `needle` and `marker`: the needle from the hub and the Kaaba's marker on the rim at
///   `bearing` + `dialRotation` (the bearing less the heading), only for a heading worth pointing
///   with. `bearing` is the feed's, in degrees true. `dimmed`: the dial at alpha 0.4; `lit`: the
///   rim lit, facing the Qibla.
/// - `caption` (size 14, weight 800), in the accent when `captionIsAccent`, and `detail` under it
///   (size 10, mist), the watch's short form of the distance (Ruling 66).
/// - `spoken`: VoiceOver's label, which says the turn while searching; `spokenDetail` its value,
///   the detail as it is heard ("" where the label already says it, or there is none).
/// - `cardinals`: the four letters in the phone's language on a dial that points; none at rest,
///   where they would name a north the watch does not know.
/// - `language` and `latinScript`: the feed's, for every spoken line (plan 4 erratum p4 #5) and the
///   type.
struct QiblaDialModel: Equatable {
    var bearing: Double
    var dialRotation: Double
    var marker: Bool
    var needle: Bool
    var dimmed: Bool
    var lit: Bool
    var caption: String
    var captionIsAccent: Bool
    var detail: String
    var spoken: String
    var spokenDetail: String
    var cardinals: [String]
    var language: String
    var latinScript: Bool
}

enum QiblaScreen {
    /// The page for the dial's [state], nil before the first reading, in [feed]'s words (read once,
    /// plan 4 erratum p4 #12) and digits: the bearing is `words.bearing` filled with the feed's
    /// whole degree in its digits, and the distance is `place.qiblaKm` as the feed writes it. The
    /// detail drawn is the watch's short form, the figure and the unit alone (`distanceOnDial`,
    /// "6,600 km", and on a dimmed dial that has given up `qiblaDetailOnDial`, "23° · 6,600 km");
    /// what VoiceOver hears is the phone's long phrase, never the short form (`distance`,
    /// "Makkah · 6,600 km away", and `qiblaDetail`): Ruling 66 items 1 and 8.
    ///
    /// - **nil:** dimmed, no needle and no marker; the bearing over the distance, said together.
    /// - **Searching:** the needle and the marker; the bearing over the distance; said, the turn
    ///   (`turnRight` or `turnLeft`, in the phrase its number takes), or the bearing while there is
    ///   none. A heading that is not finite draws neither needle nor marker nor letters and tells no
    ///   turn, and the dial stays at rest and undimmed (Ruling 58 item 1).
    /// - **Aligned:** lit, the needle and the marker; "Facing qibla" in the accent, said.
    /// - **Low:** dimmed, no needle and no marker (the phone's B41); the phone's title for the
    ///   reason, said; no readout, as the phone shows none.
    /// - **BestEffort and NoSensor:** dimmed, no needle or marker; the title, said, over the bearing
    ///   and the distance, which need no compass.
    static func model(_ feed: WatchFeed, _ state: DialState?) -> QiblaDialModel {
        let words = feed.words
        let degrees = WatchRules.digits(feed, wholeDegree(feed.place.qiblaBearing))
        let bearing = WatchRules.filled(words.bearing, degrees)
        // Drawn, the watch's short forms; heard, the phone's long phrases (Ruling 66 items 1 and 8).
        let distanceDrawn = WatchRules.filled(words.distanceOnDial, feed.place.qiblaKm)
        let bothDrawn = WatchRules.filled(words.qiblaDetailOnDial, degrees, feed.place.qiblaKm)
        let distance = WatchRules.filled(words.distance, feed.place.qiblaKm)
        let both = WatchRules.filled(words.qiblaDetail, degrees, feed.place.qiblaKm)
        var model = QiblaDialModel(
            bearing: feed.place.qiblaBearing, dialRotation: 0, marker: false, needle: false, dimmed: true, lit: false,
            caption: bearing, captionIsAccent: false, detail: distanceDrawn,
            spoken: bearing + separator(feed.ui.rtl) + distance, spokenDetail: "",
            cardinals: [], language: feed.ui.language, latinScript: feed.ui.latinScript
        )
        switch state {
        case nil:
            break
        case let .searching(heading, guidance)?:
            let pointing = heading.isFinite
            let turn = guidance.flatMap { guidance -> String? in
                guard pointing else { return nil }
                return WatchRules.counted(feed, guidance.turn == .RIGHT ? words.turnRight : words.turnLeft, Int64(guidance.degrees))
            }
            model.dimmed = false
            model.needle = pointing
            model.marker = pointing
            model.dialRotation = pointing ? -heading : 0
            model.cardinals = pointing ? words.cardinals : []
            model.spoken = turn ?? bearing
            model.spokenDetail = distance
        case let .aligned(heading)?:
            model.dimmed = false
            model.lit = true
            model.needle = true
            model.marker = true
            model.dialRotation = -heading
            model.cardinals = words.cardinals
            model.caption = words.facingQibla
            model.captionIsAccent = true
            model.spoken = words.facingQibla
            model.spokenDetail = distance
        case let .low(reason)?:
            model.caption = reason == .CALIBRATION ? words.needsCalibrating : words.interference
            model.detail = ""
            model.spoken = model.caption
        case .bestEffort?:
            model.caption = words.bestEffort
            model.detail = bothDrawn
            model.spoken = words.bestEffort
            model.spokenDetail = both
        case .noSensor?:
            model.caption = words.noSensor
            model.detail = bothDrawn
            model.spoken = words.noSensor
            model.spokenDetail = both
        }
        return model
    }

    /// The step to draw for a sample that gives no direction (`HeadingReading.hasDirection`): a
    /// dial the gate still trusts in its first second would point by the 0 the iPhone's rule writes
    /// for Core Location's sentinel, so such a sample is drawn as the searching dial with no
    /// heading, and never buzzes. Every other step is drawn as the dial answered it.
    static func drawn(_ step: DialStep, hasDirection: Bool) -> DialStep {
        guard !hasDirection else { return step }
        switch step.state {
        case .searching, .aligned:
            return DialStep(state: .searching(heading: .nan, guidance: nil), buzz: false)
        case .noSensor, .low, .bestEffort:
            return DialStep(state: step.state, buzz: false)
        }
    }

    /// The bearing's whole degree as Kotlin's `qiblaBearing.toInt()` takes it: toward zero, NaN as
    /// 0, and held to an `Int32`, so no number a text can hold traps.
    static func wholeDegree(_ bearing: Double) -> Int64 {
        guard !bearing.isNaN else { return 0 }
        return Int64(min(max(bearing, Double(Int32.min)), Double(Int32.max)).rounded(.towardZero))
    }

    /// The comma a spoken line's parts are joined by in the feed's direction, as Today's.
    private static func separator(_ rtl: Bool) -> String { rtl ? "، " : ", " }
}

/// One sample's answer: what to draw and say, and whether the watch buzzes (the Lead's call 7: one
/// `.success` as the alignment begins).
struct QiblaStep: Equatable {
    let model: QiblaDialModel
    let buzz: Bool
}

/// The page's compass: each sample made a reading by the feed's declination (`HeadingReading`),
/// judged on the feed's bearing (`WatchQibla`) and drawn (`QiblaScreen`). Made afresh for a feed,
/// and reset on every entry to the page.
final class QiblaTracker {
    private let feed: WatchFeed
    private let dial: WatchQibla

    init(feed: WatchFeed) {
        self.feed = feed
        dial = WatchQibla(bearing: feed.place.qiblaBearing)
    }

    func sample(_ sample: HeadingSample) -> QiblaStep {
        let declination: Double? = feed.place.declination
        let step = QiblaScreen.drawn(
            dial.onReading(HeadingReading.reading(sample, declination: declination)),
            hasDirection: HeadingReading.hasDirection(sample, declination: declination)
        )
        return QiblaStep(model: QiblaScreen.model(feed, step.state), buzz: step.buzz)
    }

    func reset() {
        dial.reset()
    }
}

extension HeadingSample {
    /// The sample for the screen's top. Core Location's heading is the watch's own top "regardless
    /// of device orientation as well as the orientation of the user interface" (`CLHeading.h`), and
    /// a watch set for the crown on the left (`WKInterfaceDevice.crownOrientation`) draws its screen
    /// half round, so there the screen's top is the watch's foot and the heading turns by 180°, as
    /// the Galaxy's turned display reads H + 180 (erratum p3 #11). Core Location's sentinel, a
    /// negative heading, stays as it is, so it is never passed on as a direction, and so does a
    /// heading that is not finite.
    func onScreen(crownOnLeft: Bool) -> HeadingSample {
        guard crownOnLeft, magnetic.isFinite, magnetic >= 0 else { return self }
        return HeadingSample(magnetic: TrueNorth.correct(magnetic, 180), accuracy: accuracy, millis: millis)
    }
}

/// What one heading changed on the page: whether the watch buzzes (the alignment begins), and the
/// page's new label when it changed, for VoiceOver to announce.
struct QiblaChange: Equatable {
    let buzz: Bool
    let announcement: String?
}

/// The Qibla page's compass over the page's life (plan 4 Task 9, review round 1): it runs only
/// between `start` and `stop`, which the page calls as it is shown and the app is active, and each
/// heading it takes while running is turned to the screen's top (`onScreen`), judged by a
/// `QiblaTracker` on the feed's bearing and declination, and drawn.
///
/// While it is off the page draws the resting dial, `QiblaScreen.model(feed, nil)` (no needle, no
/// marker, no lit rim, the bearing over the distance), and from each start until its first heading:
/// no heading is known then. A lowered wrist on an Always On watch leaves the app frontmost, dimmed
/// and inactive, and the wearer may turn meanwhile, so the dimmed page must not keep an old needle
/// or "Facing qibla", drawn or heard; the Galaxy draws its resting dial on every start too
/// (`NO_HEADING_YET`). Only a heading is announced, and only when it changes the label: a stop, a
/// start and a new feed tell VoiceOver nothing, since the page may not be on the screen.
final class QiblaCompass {
    /// Whether the watch has a compass: with none the page says "No compass sensor" and nothing
    /// starts.
    let available: Bool
    private(set) var model: QiblaDialModel
    private(set) var running = false
    private var feed: WatchFeed
    private var tracker: QiblaTracker

    init(feed: WatchFeed, available: Bool) {
        self.feed = feed
        self.available = available
        tracker = QiblaTracker(feed: feed)
        model = QiblaScreen.model(feed, available ? nil : .noSensor)
    }

    /// The page with no heading: at rest, or "No compass sensor" on a watch with none.
    private var resting: QiblaDialModel {
        QiblaScreen.model(feed, available ? nil : .noSensor)
    }

    /// Starts afresh: the last heading is stale after a spell away, so the tracker forgets it and
    /// takes its first sample as it is, and the page rests until it comes. True when the heading
    /// source is to be started: never on a watch with no compass, or twice.
    @discardableResult
    func start() -> Bool {
        guard !running, available else { return false }
        running = true
        tracker.reset()
        model = resting
        return true
    }

    /// Stops, and rests the page. True when the heading source is to be stopped.
    @discardableResult
    func stop() -> Bool {
        guard running else { return false }
        running = false
        model = resting
        return true
    }

    /// A new feed held: a new bearing, declination or words. The tracker is made afresh on it and
    /// the page rests until the next heading; a running compass keeps running. False for the feed
    /// already held.
    @discardableResult
    func use(_ feed: WatchFeed) -> Bool {
        guard feed != self.feed else { return false }
        self.feed = feed
        tracker = QiblaTracker(feed: feed)
        model = resting
        return true
    }

    /// One heading as Core Location gives it, for a watch with the crown on the left or the right;
    /// nil once the compass is off (a heading Core Location hands over after the stop).
    func take(_ sample: HeadingSample, crownOnLeft: Bool) -> QiblaChange? {
        guard running else { return nil }
        let step = tracker.sample(sample.onScreen(crownOnLeft: crownOnLeft))
        let said = step.model.spoken != model.spoken ? step.model.spoken : nil
        model = step.model
        return QiblaChange(buzz: step.buzz, announcement: said)
    }
}

/// A bad state's title too wide for one line ("Compass needs calibrating"): two lines, growing
/// upward from its last line's place, so the detail keeps its own under it, as the Galaxy's titles
/// grow (plan 3 Task 7).
enum QiblaCaption {
    /// How far apart the two lines stand, as a share of their size: the Galaxy's `TITLE_PITCH`, 16
    /// at 15, at which no glyph of one line meets a glyph of the other in Manrope ExtraBold.
    static let pitch = 16.0 / 15

    /// [caption]'s two lines, broken at the space that leaves the longer line shortest (in
    /// characters), the earlier space on a tie; nil for a caption of one word.
    static func split(_ caption: String) -> (first: String, last: String)? {
        let characters = Array(caption)
        var best: (index: Int, longer: Int)?
        for (index, character) in characters.enumerated() where character == " " {
            let longer = max(index, characters.count - index - 1)
            if best.map({ longer < $0.longer }) ?? true { best = (index, longer) }
        }
        guard let index = best?.index else { return nil }
        return (String(characters[..<index]), String(characters[(index + 1)...]))
    }
}

/// How far each Qibla line grows with the watch's text size (plan 3's call 15, as Tasbeeh's lines,
/// `TasbeehType.grow`, and as the Galaxy's `QiblaType`): the caption to 1.2, the detail to 1.3
/// (Ruling 66 item 8: from the drawing's 10, never below) and the dial's letters to 1.3, from the
/// watch's own default size, at which nothing drawn changes.
enum QiblaType {
    static let caption = 1.2
    static let detail = 1.3
    static let letters = 1.3
}

/// Where `aQibla` puts things, in its 172 × 212 units: the dial `qiblaDial(86, 90, 62, …, 0.86)`, the
/// caption's baseline at 180 and the detail's at 196. The drawing is scaled to fit the screen both
/// ways and centred across it, so the readout keeps its place under the dial and above the system's
/// page dots on every watch: the SE 3 (40mm) and the Series 12 (46mm) are both shorter for their
/// width than the drawing, so each is scaled by its height (Tasbeeh's page, scaled by the width,
/// had to lift its hint clear of the 46mm's dots: plan 4 Task 8's fix round 4).
struct QiblaLayout {
    static let width = 172.0
    static let height = 212.0
    static let dialCentre = (x: 86.0, y: 90.0)
    static let dialRadius = 62.0
    /// `qiblaDial`'s own scale for its strokes, letters and marker.
    static let dialScale = 0.86
    static let captionBaseline = 180.0
    static let detailBaseline = 196.0

    /// Points per unit.
    let k: Double
    /// Where the drawing's left edge stands, in points.
    let originX: Double

    init(width: Double, height: Double) {
        k = min(width / Self.width, height / Self.height)
        originX = (width - Self.width * k) / 2
    }

    /// A place across, in points from the screen's left.
    func x(_ units: Double) -> Double { originX + units * k }

    /// A place down, in points from the screen's top.
    func y(_ units: Double) -> Double { units * k }
}
