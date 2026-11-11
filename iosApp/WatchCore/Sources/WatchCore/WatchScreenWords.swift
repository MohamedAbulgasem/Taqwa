import Foundation

/// The words of the watch app's screens and complications, in the phone's language (spec 1.3):
/// `watchcore`'s `WatchScreenWords.kt` in Swift, field for field, key for key and default for
/// default (`ui.screens` in the feed). Each is the phone's own string where the phone has one, read
/// as its pattern with its `%1$s` and `%2$s` places kept, which `WatchRules.filled` fills; the rest
/// are the watches' new phrases. Two are the Apple Watch's alone (plan 4 Task 3): `appName`, the
/// phone's `app_name`, and `crownHint`.
///
/// Every field is empty by default, so a feed written before the words (only a development feed
/// ever is) reads with none, and the watch then speaks `english` (`orEnglish`, `WatchFeed.words`).
/// As every type of the feed, each key may be absent and takes its default, and a `null` there is
/// refused, as kotlinx refuses it.
///
/// One difference of form, never of answer: Kotlin's `presets` is an ordered map, in the
/// built-ins' order, and Foundation's `JSONDecoder` keeps no order, so the port's is a dictionary.
/// The watch lists the built-ins in its own order; the map only names them.
struct WatchScreenWords: Equatable {
    /// "Today" (`qibla_back`, the phone's word for the Today tab).
    var today: String = ""
    /// "Tasbeeh" (`tasbeeh_title`).
    var tasbeeh: String = ""
    /// "Qibla" (`qibla_title`).
    var qibla: String = ""
    /// "Taqwa", the app's name in the interface language (`app_name`): تقوى in Arabic.
    var appName: String = ""
    /// "Open Taqwa on your phone": a feed held but spent.
    var openOnPhone: String = ""
    /// "Update Taqwa on your watch": a newer format has been seen.
    var updateOnWatch: String = ""
    /// Today's lit row, to a screen reader: "Current prayer".
    var rowCurrent: String = ""
    /// A row whose start has passed: "Passed".
    var rowPassed: String = ""
    /// The next start's row: "Next prayer".
    var rowNext: String = ""
    /// "ROUND %1$s".
    var round: String = ""
    /// "of %1$s", under the count.
    var of: String = ""
    /// "Turn the bezel or tap", the Galaxy's hint.
    var bezelHint: String = ""
    /// "Turn the crown or tap", the counter's hint on the Apple Watch.
    var crownHint: String = ""
    /// "Reset".
    var reset: String = ""
    /// "Reset the count for this dhikr?".
    var resetConfirm: String = ""
    /// Each built-in phrase's name, by its id: "after_prayer" to "After prayer", and so on.
    var presets: [String: String] = [:]
    /// "%1$s of %2$s": the count and the target, to a screen reader.
    var countSpoken: String = ""
    /// "%1$s. Next, %2$s": the count and the next part's name.
    var nextSpoken: String = ""
    /// "%1$s. Round complete": the count.
    var setCompleteSpoken: String = ""
    /// "Round complete. %1$s, round %2$s": the phrase and the round.
    var roundSpoken: String = ""
    /// "%1$s, round %2$s": the counter's name.
    var nameRoundSpoken: String = ""
    /// "Count": the counter's action.
    var countAction: String = ""
    /// "Take one away": the turn back, as an action.
    var undoAction: String = ""
    /// "Choose a dhikr": the phrase list, as an action.
    var phrasesAction: String = ""
    /// "Facing qibla".
    var facingQibla: String = ""
    /// "Makkah · %1$s km away": the feed's distance.
    var distance: String = ""
    /// "%1$s°": the feed's bearing.
    var bearing: String = ""
    /// "%1$s° · %2$s km to Makkah".
    var qiblaDetail: String = ""
    /// "%1$s km": the distance as the turning dial draws it, the watch's short form (Ruling 66
    /// item 1); a screen reader still hears `distance`.
    var distanceOnDial: String = ""
    /// "%1$s° · %2$s km": the bearing and the distance as the dimmed dial draws them (Ruling 66
    /// item 1); a screen reader still hears `qiblaDetail`.
    var qiblaDetailOnDial: String = ""
    /// The dial's four letters, north, east, south and west.
    var cardinals: [String] = []
    /// "Hold your wrist level".
    var holdLevel: String = ""
    /// "Compass needs calibrating".
    var needsCalibrating: String = ""
    /// "Magnetic interference".
    var interference: String = ""
    /// "Compass unavailable".
    var bestEffort: String = ""
    /// "No compass sensor".
    var noSensor: String = ""
    /// "Turn right about %1$s degrees", in the phrase each number of degrees takes.
    var turnRight: [WatchCount] = []
    /// "Turn left about %1$s degrees", the same way.
    var turnLeft: [WatchCount] = []

    /// The English words, `WatchScreenWords.ENGLISH`'s exactly: the phone's English for its strings,
    /// and the English of the new phrases. A feed without words speaks these. Held to Kotlin's by
    /// the `words` vectors (an older phone's feed has no words, so its words are these).
    static let english = WatchScreenWords(
        today: "Today",
        tasbeeh: "Tasbeeh",
        qibla: "Qibla",
        appName: "Taqwa",
        openOnPhone: "Open Taqwa on your phone",
        updateOnWatch: "Update Taqwa on your watch",
        rowCurrent: "Current prayer",
        rowPassed: "Passed",
        rowNext: "Next prayer",
        round: "ROUND %1$s",
        of: "of %1$s",
        bezelHint: "Turn the bezel or tap",
        crownHint: "Turn the crown or tap",
        reset: "Reset",
        resetConfirm: "Reset the count for this dhikr?",
        presets: [
            "after_prayer": "After prayer",
            "subhanallah": "SubhanAllah",
            "alhamdulillah": "Alhamdulillah",
            "allahu_akbar": "Allahu Akbar",
            "astaghfirullah": "Astaghfirullah",
            "la_ilaha_illallah": "La ilaha illallah",
            "subhanallahi_wa_bihamdihi": "SubhanAllahi wa bihamdihi",
        ],
        countSpoken: "%1$s of %2$s",
        nextSpoken: "%1$s. Next, %2$s",
        setCompleteSpoken: "%1$s. Round complete",
        roundSpoken: "Round complete. %1$s, round %2$s",
        nameRoundSpoken: "%1$s, round %2$s",
        countAction: "Count",
        undoAction: "Take one away",
        phrasesAction: "Choose a dhikr",
        facingQibla: "Facing qibla",
        distance: "Makkah · %1$s km away",
        bearing: "%1$s°",
        qiblaDetail: "%1$s° · %2$s km to Makkah",
        distanceOnDial: "%1$s km",
        qiblaDetailOnDial: "%1$s° · %2$s km",
        cardinals: ["N", "E", "S", "W"],
        holdLevel: "Hold your wrist level",
        needsCalibrating: "Compass needs calibrating",
        interference: "Magnetic interference",
        bestEffort: "Compass unavailable",
        noSensor: "No compass sensor",
        turnRight: [WatchCount(upTo: 180, phrase: "Turn right about %1$s degrees")],
        turnLeft: [WatchCount(upTo: 180, phrase: "Turn left about %1$s degrees")]
    )

    /// These words with `english`'s in place of every one the feed left blank, as Kotlin's
    /// `orEnglish`: a word that is blank (`isBlank`), a list that is empty (a list holding anything
    /// is kept, a blank item and all). `presets` is `english`'s map overlaid by this one's names that
    /// are not blank, so every built-in has a name and an id the English lacks is kept.
    func orEnglish() -> WatchScreenWords {
        let english = WatchScreenWords.english
        func or(_ word: String, _ fallback: String) -> String { WatchScreenWords.isBlank(word) ? fallback : word }
        func or<T>(_ list: [T], _ fallback: [T]) -> [T] { list.isEmpty ? fallback : list }
        var names = english.presets
        for (id, name) in presets where !WatchScreenWords.isBlank(name) { names[id] = name }
        return WatchScreenWords(
            today: or(today, english.today),
            tasbeeh: or(tasbeeh, english.tasbeeh),
            qibla: or(qibla, english.qibla),
            appName: or(appName, english.appName),
            openOnPhone: or(openOnPhone, english.openOnPhone),
            updateOnWatch: or(updateOnWatch, english.updateOnWatch),
            rowCurrent: or(rowCurrent, english.rowCurrent),
            rowPassed: or(rowPassed, english.rowPassed),
            rowNext: or(rowNext, english.rowNext),
            round: or(round, english.round),
            of: or(of, english.of),
            bezelHint: or(bezelHint, english.bezelHint),
            crownHint: or(crownHint, english.crownHint),
            reset: or(reset, english.reset),
            resetConfirm: or(resetConfirm, english.resetConfirm),
            presets: names,
            countSpoken: or(countSpoken, english.countSpoken),
            nextSpoken: or(nextSpoken, english.nextSpoken),
            setCompleteSpoken: or(setCompleteSpoken, english.setCompleteSpoken),
            roundSpoken: or(roundSpoken, english.roundSpoken),
            nameRoundSpoken: or(nameRoundSpoken, english.nameRoundSpoken),
            countAction: or(countAction, english.countAction),
            undoAction: or(undoAction, english.undoAction),
            phrasesAction: or(phrasesAction, english.phrasesAction),
            facingQibla: or(facingQibla, english.facingQibla),
            distance: or(distance, english.distance),
            bearing: or(bearing, english.bearing),
            qiblaDetail: or(qiblaDetail, english.qiblaDetail),
            distanceOnDial: or(distanceOnDial, english.distanceOnDial),
            qiblaDetailOnDial: or(qiblaDetailOnDial, english.qiblaDetailOnDial),
            cardinals: or(cardinals, english.cardinals),
            holdLevel: or(holdLevel, english.holdLevel),
            needsCalibrating: or(needsCalibrating, english.needsCalibrating),
            interference: or(interference, english.interference),
            bestEffort: or(bestEffort, english.bestEffort),
            noSensor: or(noSensor, english.noSensor),
            turnRight: or(turnRight, english.turnRight),
            turnLeft: or(turnLeft, english.turnLeft)
        )
    }

    /// Kotlin's `isBlank` on the JVM: empty, or every UTF-16 unit a `Char.isWhitespace`, which is
    /// a space, line or paragraph separator (U+0020, U+00A0, U+3000, U+2028 …) or one of the
    /// controls U+0009 to U+000D and U+001C to U+001F. Not Foundation's `whitespacesAndNewlines`,
    /// which also takes U+0085 and leaves U+001C to U+001F. A surrogate is never a space.
    static func isBlank(_ text: String) -> Bool {
        text.utf16.allSatisfy { unit in
            switch unit {
            case 0x09...0x0D, 0x1C...0x1F:
                return true
            default:
                guard let scalar = Unicode.Scalar(unit) else { return false }
                switch scalar.properties.generalCategory {
                case .spaceSeparator, .lineSeparator, .paragraphSeparator: return true
                default: return false
                }
            }
        }
    }
}

extension WatchScreenWords: Decodable {
    private enum CodingKeys: String, CodingKey {
        case today, tasbeeh, qibla, appName, openOnPhone, updateOnWatch, rowCurrent, rowPassed, rowNext, round, of
        case bezelHint, crownHint, reset, resetConfirm, presets, countSpoken, nextSpoken, setCompleteSpoken, roundSpoken
        case nameRoundSpoken, countAction, undoAction, phrasesAction, facingQibla, distance, bearing, qiblaDetail
        case distanceOnDial, qiblaDetailOnDial
        case cardinals, holdLevel, needsCalibrating, interference, bestEffort, noSensor, turnRight, turnLeft
    }

    /// Every key as Kotlin's `= ""`, `= emptyList()` or `= emptyMap()`: absent, the default;
    /// present, its value, and a `null` refused (`readDefaulted`).
    init(from decoder: Decoder) throws {
        let json = try decoder.container(keyedBy: CodingKeys.self)
        func word(_ key: CodingKeys) throws -> String { try json.readDefaulted(String.self, key, "") }
        today = try word(.today)
        tasbeeh = try word(.tasbeeh)
        qibla = try word(.qibla)
        appName = try word(.appName)
        openOnPhone = try word(.openOnPhone)
        updateOnWatch = try word(.updateOnWatch)
        rowCurrent = try word(.rowCurrent)
        rowPassed = try word(.rowPassed)
        rowNext = try word(.rowNext)
        round = try word(.round)
        of = try word(.of)
        bezelHint = try word(.bezelHint)
        crownHint = try word(.crownHint)
        reset = try word(.reset)
        resetConfirm = try word(.resetConfirm)
        presets = try json.readDefaulted([String: String].self, .presets, [:])
        countSpoken = try word(.countSpoken)
        nextSpoken = try word(.nextSpoken)
        setCompleteSpoken = try word(.setCompleteSpoken)
        roundSpoken = try word(.roundSpoken)
        nameRoundSpoken = try word(.nameRoundSpoken)
        countAction = try word(.countAction)
        undoAction = try word(.undoAction)
        phrasesAction = try word(.phrasesAction)
        facingQibla = try word(.facingQibla)
        distance = try word(.distance)
        bearing = try word(.bearing)
        qiblaDetail = try word(.qiblaDetail)
        distanceOnDial = try word(.distanceOnDial)
        qiblaDetailOnDial = try word(.qiblaDetailOnDial)
        cardinals = try json.readDefaulted([String].self, .cardinals, [])
        holdLevel = try word(.holdLevel)
        needsCalibrating = try word(.needsCalibrating)
        interference = try word(.interference)
        bestEffort = try word(.bestEffort)
        noSensor = try word(.noSensor)
        turnRight = try json.readDefaulted([WatchCount].self, .turnRight, [])
        turnLeft = try json.readDefaulted([WatchCount].self, .turnLeft, [])
    }
}

extension WatchFeed {
    /// The words of the watch's screens in the phone's language, with the English for any it lacks:
    /// Kotlin's `WatchFeed.words()`.
    var words: WatchScreenWords { ui.screens.orEnglish() }
}
