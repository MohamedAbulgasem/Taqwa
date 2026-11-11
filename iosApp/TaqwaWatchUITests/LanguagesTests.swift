import XCTest

/// VoiceOver's reading order in the phone's other languages (plan 4 Task 11 Step 2): every reading
/// the other suites hold on `capetown` (English, 24 hours), held here again with the phone samples
/// `en12`, `ar-ly12` and `ar-sa`. Today: the ring and the rows, and the ring with a newer format
/// seen beside a feed (on `capetown` too, which no other suite reads with a feed). Tasbeeh: the
/// counter and a tap, the crown's steps across a part's end, the round rolling over, the phrase
/// list and its choice, and Reset. Qibla: searching, facing, no compass and a low compass. The
/// tests that measure places and sizes (the taps' places, the hint and the readout clear of the
/// page dots, the text sizes, the list's scroll) read `capetown`, two of them `ar-ly12` too.
///
/// The watch's own language stays English throughout, so every word heard is the feed's: the names
/// as the phone writes them (never the capitals drawn), every spoken time with its day period on a
/// 12-hour feed, the tasbeeh's spoken phrases (never the drawn label), the Qibla's turn while
/// searching, and its distance as the phone's long phrase, never the dial's short form (Ruling 66).
/// On `ar-sa` the times, counts and degrees are in Arabic-Indic digits and the ring's drawn H:MM:SS
/// in Western ones, as the ar-SA phone's ring is (plan 3's call 9). The expected words are the
/// samples' own (`watchcore/vectors/sample-feed-capetown-*.json`), filled as the phone fills them.
///
/// The simulator has no VoiceOver, so what is held here is the accessibility tree VoiceOver reads
/// (the labels, values and order), not its voice, nor the language each line carries, which
/// XCUITest does not see (`GlanceTests` and `NextPrayerContentTests` hold that, erratum p4 #5):
/// hearing it, in the feed's language on an English watch, is plan 5's gate on her watch (Ruling
/// 38). Every test sets its whole state by launch arguments (erratum p4 #9).
final class LanguagesTests: XCTestCase {
    private static let english = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]

    /// The phrase of his own `TasbeehTests` adds to the list, as typed.
    private static let ownPhrase = "اللهم صل على محمد"

    /// What each sample's surfaces say, at 14:57 on the sample's day, Asr at 16:10 next.
    private struct Sample {
        let name: String
        /// Between a spoken line and the notice after it: the feed's comma.
        let separator: String
        /// The feed's own "Update Taqwa on your watch", said and drawn while a feed is held.
        let updateOnWatch: String
        /// After prayer's spoken count at n of 100, round 1 (or round 2 for 2).
        let counts: [Int: String]
        /// 33 of 100, at the first part's end, naming the part the next count begins.
        let nextAt33: String
        /// The counter's name in round 2.
        let roundTwo: String
        /// The phrase list's rows: the seven built-ins, then his own phrase (`ownPhrase`).
        let phraseRows: [String]
        /// Astaghfirullah, as the counter names it once chosen.
        let chosen: String
        let reset: String
        let resetConfirm: String
        /// Qibla: facing; the distance heard (the long phrase) and drawn (the short form); no
        /// compass, with the bearing and the distance heard and drawn; a low compass.
        let facing: String
        let distance: String
        let distanceDrawn: String
        let noSensor: String
        let detail: String
        let detailDrawn: String
        let calibrating: String
        let ring: String
        let ringDrawn: String
        let rowTimeDrawn: String
        let rows: [String]
        let values: [String]
        let counter: String
        let counted: (before: String, after: String)
        let turnRight: String
    }

    private static let capetown = Sample(
        name: "capetown",
        separator: ", ",
        updateOnWatch: "Update Taqwa on your watch",
        counts: [0: "0 of 100", 1: "1 of 100", 2: "2 of 100", 31: "31 of 100", 32: "32 of 100", 34: "34 of 100", 64: "64 of 100", 99: "99 of 100"],
        nextAt33: "33 of 100. Next, Alhamdulillah",
        roundTwo: "SubhanAllah, round 2",
        phraseRows: ["After prayer, 100", "SubhanAllah, 100", "Alhamdulillah, 100", "Allahu Akbar, 100", "Astaghfirullah, 100", "La ilaha illallah, 100", "SubhanAllahi wa bihamdihi, 100", "اللهم صل على محمد, 33"],
        chosen: "Astaghfirullah",
        reset: "Reset",
        resetConfirm: "Reset the count for this dhikr?",
        facing: "Facing qibla",
        distance: "Makkah · 6,600 km away",
        distanceDrawn: "6,600 km",
        noSensor: "No compass sensor",
        detail: "23° · 6,600 km to Makkah",
        detailDrawn: "23° · 6,600 km",
        calibrating: "Compass needs calibrating",
        ring: "Asr in 1 hour 13 minutes, at 16:10",
        ringDrawn: "1:13:00",
        rowTimeDrawn: "16:10",
        rows: [
            "Fajr, 4:33", "Sunrise, 6:01", "Dhuhr, 12:33", "Asr, 16:10", "Maghrib, 19:04", "Isha, 20:24",
        ],
        values: ["Passed", "Passed", "Current prayer", "Next prayer", "", ""],
        counter: "SubhanAllah",
        counted: ("0 of 100", "1 of 100"),
        turnRight: "Turn right about 45 degrees"
    )

    private static let en12 = Sample(
        name: "en12",
        separator: ", ",
        updateOnWatch: "Update Taqwa on your watch",
        counts: [0: "0 of 100", 1: "1 of 100", 2: "2 of 100", 31: "31 of 100", 32: "32 of 100", 34: "34 of 100", 64: "64 of 100", 99: "99 of 100"],
        nextAt33: "33 of 100. Next, Alhamdulillah",
        roundTwo: "SubhanAllah, round 2",
        phraseRows: ["After prayer, 100", "SubhanAllah, 100", "Alhamdulillah, 100", "Allahu Akbar, 100", "Astaghfirullah, 100", "La ilaha illallah, 100", "SubhanAllahi wa bihamdihi, 100", "اللهم صل على محمد, 33"],
        chosen: "Astaghfirullah",
        reset: "Reset",
        resetConfirm: "Reset the count for this dhikr?",
        facing: "Facing qibla",
        distance: "Makkah · 6,600 km away",
        distanceDrawn: "6,600 km",
        noSensor: "No compass sensor",
        detail: "23° · 6,600 km to Makkah",
        detailDrawn: "23° · 6,600 km",
        calibrating: "Compass needs calibrating",
        ring: "Asr in 1 hour 13 minutes, at \u{2068}4:10\u{00A0}PM\u{2069}",
        ringDrawn: "1:13:00",
        rowTimeDrawn: "4:10\u{00A0}PM",
        rows: [
            "Fajr, \u{2068}4:33\u{00A0}AM\u{2069}", "Sunrise, \u{2068}6:01\u{00A0}AM\u{2069}",
            "Dhuhr, \u{2068}12:33\u{00A0}PM\u{2069}", "Asr, \u{2068}4:10\u{00A0}PM\u{2069}",
            "Maghrib, \u{2068}7:04\u{00A0}PM\u{2069}", "Isha, \u{2068}8:24\u{00A0}PM\u{2069}",
        ],
        values: ["Passed", "Passed", "Current prayer", "Next prayer", "", ""],
        counter: "SubhanAllah",
        counted: ("0 of 100", "1 of 100"),
        turnRight: "Turn right about 45 degrees"
    )

    private static let arLy12 = Sample(
        name: "ar-ly12",
        separator: "، ",
        updateOnWatch: "حدّث تقوى على ساعتك",
        counts: [0: "0 من 100", 1: "1 من 100", 2: "2 من 100", 31: "31 من 100", 32: "32 من 100", 34: "34 من 100", 64: "64 من 100", 99: "99 من 100"],
        nextAt33: "33 من 100. التالي: الحمد لله",
        roundTwo: "سبحان الله، الجولة 2",
        phraseRows: ["بعد الصلاة, 100", "سبحان الله, 100", "الحمد لله, 100", "الله أكبر, 100", "أستغفر الله, 100", "لا إله إلا الله, 100", "سبحان الله وبحمده, 100", "اللهم صل على محمد, 33"],
        chosen: "أستغفر الله",
        reset: "إعادة العدّ",
        resetConfirm: "إعادة العدّ لهذا الذكر؟",
        facing: "أنت باتجاه القبلة",
        distance: "مكة · على بُعد 6,600 كم",
        distanceDrawn: "6,600 كم",
        noSensor: "لا توجد بوصلة في هذا الجهاز",
        detail: "23° · 6,600 كم إلى مكة",
        detailDrawn: "23° · 6,600 كم",
        calibrating: "البوصلة تحتاج إلى معايرة",
        ring: "العصر بعد 1 ساعة و13 دقيقة، الساعة \u{2068}4:10\u{00A0}م\u{2069}",
        ringDrawn: "1:13:00",
        rowTimeDrawn: "4:10\u{00A0}م",
        rows: [
            "الفجر، \u{2068}4:33\u{00A0}ص\u{2069}", "الشروق، \u{2068}6:01\u{00A0}ص\u{2069}",
            "الظهر، \u{2068}12:33\u{00A0}م\u{2069}", "العصر، \u{2068}4:10\u{00A0}م\u{2069}",
            "المغرب، \u{2068}7:04\u{00A0}م\u{2069}", "العشاء، \u{2068}8:24\u{00A0}م\u{2069}",
        ],
        values: ["انقضت", "انقضت", "الصلاة الحالية", "الصلاة التالية", "", ""],
        counter: "سبحان الله",
        counted: ("0 من 100", "1 من 100"),
        turnRight: "استدر يمينًا حوالي 45 درجة"
    )

    private static let arSa = Sample(
        name: "ar-sa",
        separator: "، ",
        updateOnWatch: "حدّث تقوى على ساعتك",
        counts: [0: "٠ من ١٠٠", 1: "١ من ١٠٠", 2: "٢ من ١٠٠", 31: "٣١ من ١٠٠", 32: "٣٢ من ١٠٠", 34: "٣٤ من ١٠٠", 64: "٦٤ من ١٠٠", 99: "٩٩ من ١٠٠"],
        nextAt33: "٣٣ من ١٠٠. التالي: الحمد لله",
        roundTwo: "سبحان الله، الجولة ٢",
        phraseRows: ["بعد الصلاة, ١٠٠", "سبحان الله, ١٠٠", "الحمد لله, ١٠٠", "الله أكبر, ١٠٠", "أستغفر الله, ١٠٠", "لا إله إلا الله, ١٠٠", "سبحان الله وبحمده, ١٠٠", "اللهم صل على محمد, ٣٣"],
        chosen: "أستغفر الله",
        reset: "إعادة العدّ",
        resetConfirm: "إعادة العدّ لهذا الذكر؟",
        facing: "أنت باتجاه القبلة",
        distance: "مكة · على بُعد ٦٬٦٠٠ كم",
        distanceDrawn: "٦٬٦٠٠ كم",
        noSensor: "لا توجد بوصلة في هذا الجهاز",
        detail: "٢٣° · ٦٬٦٠٠ كم إلى مكة",
        detailDrawn: "٢٣° · ٦٬٦٠٠ كم",
        calibrating: "البوصلة تحتاج إلى معايرة",
        // A 24-hour time is digits and a colon alone, so it is heard bare, with no isolates
        // (`WatchRules.spokenClock`), as capetown's "at 16:10" is.
        ring: "العصر بعد ١ ساعة و١٣ دقيقة، الساعة ١٦:١٠",
        ringDrawn: "1:13:00",
        rowTimeDrawn: "١٦:١٠",
        rows: [
            "الفجر، ٤:٣٣", "الشروق، ٦:٠١", "الظهر، ١٢:٣٣", "العصر، ١٦:١٠", "المغرب، ١٩:٠٤", "العشاء، ٢٠:٢٤",
        ],
        values: ["انقضت", "انقضت", "الصلاة الحالية", "الصلاة التالية", "", ""],
        counter: "سبحان الله",
        counted: ("٠ من ١٠٠", "١ من ١٠٠"),
        turnRight: "استدر يمينًا حوالي ٤٥ درجة"
    )

    override func setUp() {
        continueAfterFailure = true
    }

    func testTodayInEn12() throws { try today(Self.en12) }
    func testTodayInArLy12() throws { try today(Self.arLy12) }
    func testTodayInArSa() throws { try today(Self.arSa) }

    func testTasbeehInEn12() { tasbeeh(Self.en12) }
    func testTasbeehInArLy12() { tasbeeh(Self.arLy12) }
    func testTasbeehInArSa() { tasbeeh(Self.arSa) }

    func testQiblaInEn12() { qibla(Self.en12) }
    func testQiblaInArLy12() { qibla(Self.arLy12) }
    func testQiblaInArSa() { qibla(Self.arSa) }

    func testANewerFormatWithAFeedInCapetown() { newerFormat(Self.capetown) }
    func testANewerFormatWithAFeedInEn12() { newerFormat(Self.en12) }
    func testANewerFormatWithAFeedInArLy12() { newerFormat(Self.arLy12) }
    func testANewerFormatWithAFeedInArSa() { newerFormat(Self.arSa) }

    func testTheCrownStepsInEn12() { crownSteps(Self.en12) }
    func testTheCrownStepsInArLy12() { crownSteps(Self.arLy12) }
    func testTheCrownStepsInArSa() { crownSteps(Self.arSa) }

    func testTheRoundRollsOverInEn12() { rollover(Self.en12) }
    func testTheRoundRollsOverInArLy12() { rollover(Self.arLy12) }
    func testTheRoundRollsOverInArSa() { rollover(Self.arSa) }

    func testThePhraseListInEn12() { phraseList(Self.en12) }
    func testThePhraseListInArLy12() { phraseList(Self.arLy12) }
    func testThePhraseListInArSa() { phraseList(Self.arSa) }

    func testResetInEn12() { reset(Self.en12) }
    func testResetInArLy12() { reset(Self.arLy12) }
    func testResetInArSa() { reset(Self.arSa) }

    func testFacingInEn12() { facing(Self.en12) }
    func testFacingInArLy12() { facing(Self.arLy12) }
    func testFacingInArSa() { facing(Self.arSa) }

    func testNoCompassInEn12() { noCompass(Self.en12) }
    func testNoCompassInArLy12() { noCompass(Self.arLy12) }
    func testNoCompassInArSa() { noCompass(Self.arSa) }

    func testALowCompassInEn12() { lowCompass(Self.en12) }
    func testALowCompassInArLy12() { lowCompass(Self.arLy12) }
    func testALowCompassInArSa() { lowCompass(Self.arSa) }

    // MARK: - Today, Tasbeeh and Qibla's first readings

    /// Task 7's reading: the ring first, in one sentence, then each row in the order of the day,
    /// labelled as the phone says the row, with its state as the value.
    private func today(_ sample: Sample, file: StaticString = #filePath, line: UInt = #line) throws {
        let app = launch(sample, ["-taqwaAt", "14:57", "-taqwaFrozen", "1", "-taqwaPage", "today"])
        let ring = app.descendants(matching: .any)["today.ring"]
        XCTAssertTrue(ring.waitForExistence(timeout: 30), "\(sample.name): Today has drawn its ring", file: file, line: line)

        let read = try inOrder(app.snapshot()).filter { $0.identifier.hasPrefix("today.") }
            .map { (id: $0.identifier, label: $0.label, value: ($0.value as? String) ?? "") }
        XCTAssertEqual(read.map(\.id), [
            "today.ring",
            "today.row.FAJR", "today.row.SUNRISE", "today.row.DHUHR",
            "today.row.ASR", "today.row.MAGHRIB", "today.row.ISHA",
        ], "\(sample.name): the ring first, then the rows in the order of the day", file: file, line: line)
        XCTAssertEqual(read.first?.label, sample.ring, "\(sample.name): the ring's sentence", file: file, line: line)
        XCTAssertEqual(read.dropFirst().map(\.label), sample.rows, "\(sample.name): the rows as the phone says them", file: file, line: line)
        XCTAssertEqual(read.dropFirst().map(\.value), sample.values, "\(sample.name): each row's state", file: file, line: line)

        // What is drawn, beside what is heard: the ring's H:MM:SS in the phone ring's digits, and the
        // times in the feed's.
        XCTAssertTrue(app.staticTexts[sample.ringDrawn].exists, "\(sample.name): the ring draws \(sample.ringDrawn)", file: file, line: line)
        // In Asr's row itself: the ring draws the same time under its countdown.
        let asr = app.descendants(matching: .any)["today.row.ASR"].firstMatch
        XCTAssertTrue(asr.staticTexts[sample.rowTimeDrawn].exists, "\(sample.name): Asr's row draws \(sample.rowTimeDrawn)", file: file, line: line)
        app.terminate()
    }

    /// Task 8's reading: the counter is one element, named as the phone says the dhikr and valued
    /// with plan 3's spoken count, before and after a tap.
    private func tasbeeh(_ sample: Sample, file: StaticString = #filePath, line: UInt = #line) {
        let app = launch(sample, ["-taqwaFresh", "1", "-taqwaPage", "tasbeeh"])
        TasbeehTests.waitForThePage(app, file: file, line: line)
        let counter = app.descendants(matching: .any)["tasbeeh.counter"]
        XCTAssertTrue(counter.waitForExistence(timeout: 10), "\(sample.name): the counter has drawn", file: file, line: line)
        XCTAssertEqual(counter.label, sample.counter, "\(sample.name): the dhikr as the phone says it", file: file, line: line)
        XCTAssertEqual(counter.value as? String, sample.counted.before, "\(sample.name): the count before a tap", file: file, line: line)

        counter.tap()
        XCTAssertTrue(QiblaTests.holds(for: 10) { (counter.value as? String) == sample.counted.after },
                      "\(sample.name): one tap, said as the phone says it, not \(String(describing: counter.value))", file: file, line: line)
        app.terminate()
    }

    /// Task 9's reading: while searching, the dial says the turn in the feed's words and digits, and
    /// the distance as the phone's long phrase (Ruling 66), never the short form the dial draws.
    private func qibla(_ sample: Sample, file: StaticString = #filePath, line: UInt = #line) {
        let (app, dial) = qiblaDial(sample, ["-taqwaHeading", "7"], file: file, line: line)
        XCTAssertTrue(QiblaTests.holds(for: 5) { dial.label == sample.turnRight },
                      "\(sample.name): searching says the turn, not '\(dial.label)'", file: file, line: line)
        XCTAssertEqual(dial.value as? String, sample.distance, "\(sample.name): the long phrase is heard", file: file, line: line)
        XCTAssertEqual(app.descendants(matching: .any)["qibla.detail"].label, sample.distanceDrawn,
                       "\(sample.name): the dial's short form is drawn (Ruling 66)", file: file, line: line)
        app.terminate()
    }

    // MARK: - Today with a newer format seen

    /// A newer format seen while a feed is held (plan 3's call 5): Today's ring says its sentence,
    /// then the feed's own "Update Taqwa on your watch", in the feed's words, and draws that line
    /// where the time stands. With no feed the line is the watch's own (`LaunchTests`).
    private func newerFormat(_ sample: Sample, file: StaticString = #filePath, line: UInt = #line) {
        let app = launch(sample, ["-taqwaNewerSeen", "1", "-taqwaAt", "14:57", "-taqwaFrozen", "1", "-taqwaPage", "today"])
        let ring = app.descendants(matching: .any)["today.ring"]
        XCTAssertTrue(ring.waitForExistence(timeout: 30), "\(sample.name): Today has drawn its ring", file: file, line: line)
        XCTAssertEqual(ring.label, sample.ring + sample.separator + sample.updateOnWatch,
                       "\(sample.name): the ring's sentence, then the notice", file: file, line: line)
        XCTAssertTrue(app.staticTexts[sample.updateOnWatch].exists, "\(sample.name): the notice is drawn", file: file, line: line)
        app.terminate()
    }

    // MARK: - Tasbeeh: the crown, the round, the list and Reset

    /// Task 8's crown, heard: each detent's count, the first part's end naming the next part, and a
    /// turn back across that end saying it again (`TasbeehCrownTests` on `capetown`).
    private func crownSteps(_ sample: Sample, file: StaticString = #filePath, line: UInt = #line) {
        let (app, counter) = tasbeehCounter(sample, ["-taqwaTasbeehCount", "31"], file: file, line: line)
        XCTAssertEqual(counter.value as? String, sample.counts[31], "\(sample.name): opened at 31", file: file, line: line)
        for said in [sample.counts[32]!, sample.nextAt33, sample.counts[34]!] {
            XCUIDevice.shared.rotateDigitalCrown(delta: TasbeehCrownTests.crownDetent, velocity: .slow)
            XCTAssertTrue(QiblaTests.holds { (counter.value as? String) == said },
                          "\(sample.name): \(said), not \(String(describing: counter.value))", file: file, line: line)
        }
        XCUIDevice.shared.rotateDigitalCrown(delta: -TasbeehCrownTests.crownDetent, velocity: .slow)
        XCTAssertTrue(QiblaTests.holds { (counter.value as? String) == sample.nextAt33 },
                      "\(sample.name): back across the part's end", file: file, line: line)
        app.terminate()
    }

    /// A fast spin from 99 through the hundred: round 2 opens with every detent counted, and the
    /// counter is named with its round as the phone says it, never the round's drawn capitals.
    private func rollover(_ sample: Sample, file: StaticString = #filePath, line: UInt = #line) {
        let (app, counter) = tasbeehCounter(sample, ["-taqwaTasbeehCount", "99"], file: file, line: line)
        XCTAssertEqual(counter.value as? String, sample.counts[99], "\(sample.name): opened at 99", file: file, line: line)
        XCUIDevice.shared.rotateDigitalCrown(delta: 3 * TasbeehCrownTests.crownDetent, velocity: .fast)
        XCTAssertTrue(QiblaTests.holds { (counter.value as? String) == sample.counts[2] },
                      "\(sample.name): round 2 at 2, not \(String(describing: counter.value))", file: file, line: line)
        XCTAssertEqual(counter.label, sample.roundTwo, "\(sample.name): named with its round", file: file, line: line)
        app.terminate()
    }

    /// The dhikr's line opens the list: each row one element, named as the phone says it with its
    /// target in the feed's digits, the one counted selected; a row chosen is counted from 0, and
    /// the crown counts it (`TasbeehTests` on `capetown`).
    private func phraseList(_ sample: Sample, file: StaticString = #filePath, line: UInt = #line) {
        let (app, counter) = tasbeehCounter(sample, ["-taqwaPhrase", Self.ownPhrase], file: file, line: line)
        TasbeehTests.openTheList(app)
        let rows = app.descendants(matching: .any).matching(NSPredicate(format: "identifier BEGINSWITH 'tasbeeh.phrase.'"))
        XCTAssertTrue(rows.firstMatch.waitForExistence(timeout: 10), "\(sample.name): the list is open", file: file, line: line)
        XCTAssertEqual(rows.allElementsBoundByIndex.map(\.label), sample.phraseRows, "\(sample.name): the rows as the phone says them", file: file, line: line)
        XCTAssertEqual(rows.allElementsBoundByIndex.map(\.isSelected), [true] + Array(repeating: false, count: sample.phraseRows.count - 1),
                       "\(sample.name): After prayer is the one counted", file: file, line: line)

        let astaghfirullah = app.descendants(matching: .any)["tasbeeh.phrase.astaghfirullah"]
        TasbeehTests.reveal(astaghfirullah, in: app)
        astaghfirullah.tap()
        XCTAssertTrue(QiblaTests.holds { counter.exists && counter.label == sample.chosen },
                      "\(sample.name): the phrase chosen is counted", file: file, line: line)
        XCTAssertEqual(counter.value as? String, sample.counts[0], "\(sample.name): from 0", file: file, line: line)
        XCUIDevice.shared.rotateDigitalCrown(delta: TasbeehCrownTests.crownDetent, velocity: .slow)
        XCTAssertTrue(QiblaTests.holds { (counter.value as? String) == sample.counts[1] },
                      "\(sample.name): the crown counts the phrase chosen", file: file, line: line)
        app.terminate()
    }

    /// Reset, named in the feed's words, asks the phone's question in them, and goes back to 0.
    private func reset(_ sample: Sample, file: StaticString = #filePath, line: UInt = #line) {
        let (app, counter) = tasbeehCounter(sample, ["-taqwaTasbeehCount", "64"], file: file, line: line)
        XCTAssertEqual(counter.value as? String, sample.counts[64], "\(sample.name): opened at 64", file: file, line: line)
        TasbeehTests.openTheList(app)
        let reset = app.descendants(matching: .any)["tasbeeh.reset"]
        XCTAssertTrue(reset.waitForExistence(timeout: 10), "\(sample.name): the list ends in Reset", file: file, line: line)
        XCTAssertEqual(reset.label, sample.reset, "\(sample.name): Reset as the phone says it", file: file, line: line)
        TasbeehTests.reveal(reset, in: app)
        reset.tap()
        XCTAssertTrue(app.staticTexts[sample.resetConfirm].waitForExistence(timeout: 10), "\(sample.name): the phone's question", file: file, line: line)
        app.buttons[sample.reset].firstMatch.tap()
        XCTAssertTrue(QiblaTests.holds { counter.exists && (counter.value as? String) == sample.counts[0] },
                      "\(sample.name): back to the start", file: file, line: line)
        app.terminate()
    }

    // MARK: - Qibla: facing, no compass, a low compass

    /// Facing: said in the feed's words, with the long phrase heard and the short form drawn.
    private func facing(_ sample: Sample, file: StaticString = #filePath, line: UInt = #line) {
        let (app, dial) = qiblaDial(sample, ["-taqwaHeading", "50"], file: file, line: line)
        XCTAssertTrue(QiblaTests.holds(for: 5) { dial.label == sample.facing }, "\(sample.name): facing, not '\(dial.label)'", file: file, line: line)
        XCTAssertEqual(dial.value as? String, sample.distance, "\(sample.name): the long phrase is heard", file: file, line: line)
        XCTAssertEqual(app.descendants(matching: .any)["qibla.detail"].label, sample.distanceDrawn,
                       "\(sample.name): the dial's short form is drawn (Ruling 66)", file: file, line: line)
        app.terminate()
    }

    /// No compass: the reason said, then the bearing and the distance as the phone's long phrase, in
    /// the feed's digits; the dial draws their short form.
    private func noCompass(_ sample: Sample, file: StaticString = #filePath, line: UInt = #line) {
        let (app, dial) = qiblaDial(sample, ["-taqwaNoCompass", "1"], file: file, line: line)
        XCTAssertTrue(QiblaTests.holds(for: 5) { dial.label == sample.noSensor }, "\(sample.name): no compass, not '\(dial.label)'", file: file, line: line)
        XCTAssertEqual(dial.value as? String, sample.detail, "\(sample.name): the long phrase is heard", file: file, line: line)
        XCTAssertEqual(app.descendants(matching: .any)["qibla.detail"].label, sample.detailDrawn,
                       "\(sample.name): the dial's short form is drawn (Ruling 66)", file: file, line: line)
        app.terminate()
    }

    /// A low compass: the phone's title for the reason, said, and nothing after it, as the phone
    /// shows no readout.
    private func lowCompass(_ sample: Sample, file: StaticString = #filePath, line: UInt = #line) {
        let (app, dial) = qiblaDial(sample, ["-taqwaHeading", "50", "-taqwaHeadingAccuracy", "60"], file: file, line: line)
        XCTAssertTrue(QiblaTests.holds(for: 5) { dial.label == sample.calibrating }, "\(sample.name): low, not '\(dial.label)'", file: file, line: line)
        XCTAssertEqual((dial.value as? String) ?? "", "", "\(sample.name): no readout is heard", file: file, line: line)
        app.terminate()
    }

    // MARK: - Helpers

    /// Launches [sample] fresh on the Tasbeeh page with [more], and answers the counter once drawn.
    private func tasbeehCounter(_ sample: Sample, _ more: [String], file: StaticString, line: UInt) -> (XCUIApplication, XCUIElement) {
        let app = launch(sample, ["-taqwaFresh", "1", "-taqwaPage", "tasbeeh"] + more)
        TasbeehTests.waitForThePage(app, file: file, line: line)
        let counter = app.descendants(matching: .any)["tasbeeh.counter"]
        XCTAssertTrue(counter.waitForExistence(timeout: 10), "\(sample.name): the counter has drawn", file: file, line: line)
        return (app, counter)
    }

    /// Launches [sample] on the Qibla page with the compass [more], and answers the dial once drawn.
    private func qiblaDial(_ sample: Sample, _ more: [String], file: StaticString, line: UInt) -> (XCUIApplication, XCUIElement) {
        let app = launch(sample, ["-taqwaPage", "qibla"] + more)
        QiblaTests.waitForThePage(app, file: file, line: line)
        let dial = app.descendants(matching: .any)["qibla.dial"]
        XCTAssertTrue(dial.waitForExistence(timeout: 10), "\(sample.name): the dial has drawn", file: file, line: line)
        return (app, dial)
    }

    private func launch(_ sample: Sample, _ more: [String]) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["-taqwaSampleFeed", sample.name] + more + Self.english
        app.launch()
        return app
    }

    /// Every element of [snapshot]'s tree, depth first, each before its children.
    private func inOrder(_ snapshot: XCUIElementSnapshot) -> [XCUIElementSnapshot] {
        [snapshot] + snapshot.children.flatMap { inOrder($0) }
    }
}
