import Foundation
import XCTest
@testable import WatchCore

/// What the watch takes from an application context and says back in its handshake (plan 4
/// Task 5): the contract's keys and receivers' rules, ported and held to its vectors, never
/// invented (Ruling 50).
final class FeedIntakeTests: XCTestCase {

    func testTheContractKeysAreThePhonesOwn() {
        let keys = Vectors.feeds.contract.keys
        XCTAssertEqual(FeedIntake.feedKey, keys.CONTEXT_FEED_KEY)
        XCTAssertEqual(FeedIntake.deliveryKey, keys.CONTEXT_DELIVERY_KEY)
        XCTAssertEqual(FeedIntake.refreshKey, keys.REFRESH_MESSAGE_KEY)
        XCTAssertEqual(FeedIntake.replyKey, keys.REFRESH_REPLY_KEY)
    }

    func testTheReceiversRulesAreTheContractsVectors() throws {
        let contract = Vectors.feeds.contract
        for fingerprint in Vectors.nonEmpty(contract.fingerprints, "contract fingerprints") {
            XCTAssertEqual(FeedIntake.isFingerprint(fingerprint.text), fingerprint.valid, "'\(fingerprint.text)'")
        }
        for deliveryCase in Vectors.nonEmpty(contract.deliveries, "contract deliveries") {
            let delivery = FeedIntake.deliveryOf(deliveryCase.text)
            XCTAssertEqual(delivery?.origin, deliveryCase.origin, deliveryCase.name)
            XCTAssertEqual(delivery?.seq, deliveryCase.seq, deliveryCase.name)
            XCTAssertEqual(delivery?.fingerprint, deliveryCase.fingerprint, deliveryCase.name)
            if let delivery {
                XCTAssertEqual(delivery.text, deliveryCase.text, "\(deliveryCase.name): one form")
            }
        }
        XCTAssertTrue(contract.deliveries.contains { $0.origin == nil }, "the refused forms are there")
        for storesCase in Vectors.nonEmpty(contract.stores, "contract stores") {
            let held = try storesCase.held.map { try XCTUnwrap(FeedIntake.deliveryOf($0), storesCase.name) }
            let arriving = try XCTUnwrap(FeedIntake.deliveryOf(storesCase.arriving), storesCase.name)
            XCTAssertEqual(FeedIntake.stores(held: held, arriving: arriving), storesCase.stores, storesCase.name)
        }
        for echoCase in Vectors.nonEmpty(contract.echoes, "contract echoes") {
            let held = try echoCase.held.map { try XCTUnwrap(FeedIntake.deliveryOf($0)) }
            XCTAssertEqual(FeedIntake.echoOf(held), echoCase.echo)
        }
        XCTAssertEqual(FeedStore.steps.map(\.rawValue), Vectors.nonEmpty(contract.storeSteps, "contract storeSteps"))
    }

    func testDataUnderTheFeedKeyIsStored() throws {
        let store = FeedStore(directory: try StoreFixtures.directory(self))
        let d5 = StoreFixtures.delivery(5)
        let context: [String: Any] = [FeedIntake.feedKey: try StoreFixtures.sampleText, FeedIntake.deliveryKey: d5.text]

        XCTAssertEqual(try FeedIntake.accept(context, into: store), .accepted)
        XCTAssertEqual(store.read(), try StoreFixtures.sample)
        XCTAssertEqual(store.delivery(), d5)
        XCTAssertEqual(try FeedIntake.accept(context, into: store), .stale, "the same context again is a duplicate")
    }

    func testAUTF8StringUnderTheFeedKeyIsStored() throws {
        let store = FeedStore(directory: try StoreFixtures.directory(self))
        let text = try XCTUnwrap(String(data: try StoreFixtures.otherText, encoding: .utf8))
        let context: [String: Any] = [FeedIntake.feedKey: text, FeedIntake.deliveryKey: StoreFixtures.delivery(5).text]

        XCTAssertEqual(try FeedIntake.accept(context, into: store), .accepted)
        XCTAssertEqual(store.read(), try StoreFixtures.other)
    }

    func testAContextWithoutADeliveryIsNotStored() throws {
        let store = FeedStore(directory: try StoreFixtures.directory(self))
        let feed = try StoreFixtures.sampleText
        for context: [String: Any] in [
            [FeedIntake.feedKey: feed],
            [FeedIntake.feedKey: feed, FeedIntake.deliveryKey: ""],
            [FeedIntake.feedKey: feed, FeedIntake.deliveryKey: "fedcba9876543210"],
            [FeedIntake.feedKey: feed, FeedIntake.deliveryKey: "0123456789abcdef/0/fedcba9876543210"],
            [FeedIntake.feedKey: feed, FeedIntake.deliveryKey: Data(StoreFixtures.delivery(5).text.utf8)],
        ] {
            XCTAssertNil(try FeedIntake.accept(context, into: store), "no phone's: \(context.keys.sorted())")
        }
        XCTAssertNil(store.read())
        XCTAssertFalse(FileManager.default.fileExists(atPath: store.feedFile.path))
    }

    func testAContextWithoutAFeedIsNothing() throws {
        let store = FeedStore(directory: try StoreFixtures.directory(self))
        let d5 = StoreFixtures.delivery(5)
        _ = try FeedIntake.accept([FeedIntake.feedKey: try StoreFixtures.sampleText, FeedIntake.deliveryKey: d5.text], into: store)

        for context: [String: Any] in [
            [:],
            ["probe": Data(count: 70_000)], // Task 5B's probe: the stored feed stays.
            [FeedIntake.deliveryKey: StoreFixtures.delivery(6).text],
            [FeedIntake.feedKey: 42, FeedIntake.deliveryKey: StoreFixtures.delivery(6).text],
        ] {
            XCTAssertNil(try FeedIntake.accept(context, into: store), "\(context.keys.sorted())")
        }
        XCTAssertEqual(store.read(), try StoreFixtures.sample)
        XCTAssertEqual(store.delivery(), d5)
    }

    func testTheHandshakeEchoesTheStoredDelivery() throws {
        let store = FeedStore(directory: try StoreFixtures.directory(self))
        XCTAssertEqual(FeedIntake.handshake(store) as? [String: String], [FeedIntake.refreshKey: ""], "empty with none")

        let d5 = StoreFixtures.delivery(5)
        _ = try store.write(try StoreFixtures.sampleText, delivery: d5)
        XCTAssertEqual(FeedIntake.handshake(store) as? [String: String], [FeedIntake.refreshKey: d5.text])

        _ = try store.write(try StoreFixtures.otherText, delivery: nil)
        XCTAssertEqual(FeedIntake.handshake(store) as? [String: String], [FeedIntake.refreshKey: ""], "a sample is no phone's")

        // A delivery whose feed is gone names nothing the watch holds: the echo is empty, so the
        // phone sends once more and never answers UNCHANGED for a feed the watch lacks.
        try Data(d5.text.utf8).write(to: store.deliveryFile)
        try FileManager.default.removeItem(at: store.feedFile)
        XCTAssertEqual(FeedIntake.handshake(store) as? [String: String], [FeedIntake.refreshKey: ""])
    }

    func testALogLineNamesTheNumberAndFingerprintNeverTheOrigin() throws {
        // Ruling 52 item 4: release log lines carry no origin, city or bearing.
        let d5 = StoreFixtures.delivery(5, origin: "a1b2c3d4e5f60789")
        let context: [String: Any] = [FeedIntake.feedKey: try StoreFixtures.sampleText, FeedIntake.deliveryKey: d5.text]
        let lines = [
            FeedIntake.line(.accepted, context: context, days: 31, via: "context"),
            FeedIntake.line(.unchanged, context: context, days: 31, via: "context"),
            FeedIntake.line(.stale, context: context, days: nil, via: "activation"),
            FeedIntake.line(.newerVersion, context: context, days: nil, via: "context"),
            FeedIntake.line(.malformed, context: context, days: nil, via: "context"),
            FeedIntake.line(nil, context: [FeedIntake.feedKey: try StoreFixtures.sampleText], days: nil, via: "context"),
            FeedIntake.line(nil, context: [:], days: nil, via: "activation"),
            FeedIntake.echoLine(d5.text),
        ]
        XCTAssertEqual(lines[0], "TaqwaWatch accepted 5/fedcba9876543210 31 days via context")
        XCTAssertEqual(lines[1], "TaqwaWatch unchanged 5/fedcba9876543210 31 days via context")
        XCTAssertEqual(lines[2], "TaqwaWatch stale 5/fedcba9876543210 via activation")
        XCTAssertEqual(lines[5], "TaqwaWatch not stored: a feed with no delivery via context")
        XCTAssertEqual(lines[6], "TaqwaWatch nothing to store via activation")
        XCTAssertEqual(lines[7], "5/fedcba9876543210")
        XCTAssertEqual(FeedIntake.echoLine(""), "none")
        for line in lines {
            XCTAssertFalse(line.contains(d5.origin), line)
            XCTAssertFalse(line.contains("Cape Town"), line)
        }
    }

    // MARK: - The widgets' reload

    func testOnlyANewFeedOrANewNoticeReloadsTheWidgets() {
        let results: [FeedWriteResult?] = [.accepted, .unchanged, .stale, .newerVersion, .malformed, nil]
        for result in results {
            for before in [false, true] {
                for after in [false, true] {
                    let expected: Bool
                    switch result {
                    case .accepted: expected = true
                    case .newerVersion: expected = after && !before
                    default: expected = false
                    }
                    XCTAssertEqual(
                        FeedIntake.reloadsWidgets(result, noticeBefore: before, noticeAfter: after), expected,
                        "\(String(describing: result)) notice \(before) -> \(after)"
                    )
                }
            }
        }
    }

    func testANewerFormatReloadsTheWidgetsOnlyWhenItsNoticeComes() throws {
        // Review Focus 1, "with a feed or without": with none, or a spent one, the complications'
        // empty form is a `.never` timeline, so only a reload makes it say "Update Taqwa on your
        // watch" instead of "Open Taqwa on your phone".
        for held in [false, true] {
            let store = FeedStore(directory: try StoreFixtures.directory(self))
            if held {
                XCTAssertEqual(try FeedIntake.take(Self.context(try StoreFixtures.sampleText, 5), into: store),
                               FeedIntake.Outcome(result: .accepted, reloadsWidgets: true))
            }
            let why = held ? "with a feed" : "without one"

            let newer = Self.context(StoreFixtures.newerText, 6, "00000000000000ff")
            XCTAssertEqual(try FeedIntake.take(newer, into: store), FeedIntake.Outcome(result: .newerVersion, reloadsWidgets: true), why)
            XCTAssertTrue(store.noticeOnDisk, why)

            // Its delivery is not stored, so the echo still names the older one, and the phone may
            // send it again at each handshake: the notice is there, and nothing is reloaded.
            XCTAssertEqual(try FeedIntake.take(newer, into: store), FeedIntake.Outcome(result: .newerVersion, reloadsWidgets: false), why)
            XCTAssertEqual(try FeedIntake.take(Self.context(StoreFixtures.newerText, 7, "00000000000000ff"), into: store),
                           FeedIntake.Outcome(result: .newerVersion, reloadsWidgets: false), why)
            let newerStill = Data(#"{"version":3}"#.utf8)
            XCTAssertEqual(try FeedIntake.take(Self.context(newerStill, 8, "00000000000000fe"), into: store),
                           FeedIntake.Outcome(result: .newerVersion, reloadsWidgets: false), "\(why): another newer format says the same")

            // A later process finds the notice an earlier one left.
            let later = FeedStore(directory: store.directory)
            XCTAssertEqual(try FeedIntake.take(newer, into: later), FeedIntake.Outcome(result: .newerVersion, reloadsWidgets: false), why)

            // A feed this build reads ends the notice, and reloads; the same feed again does not;
            // a newer format after it brings the notice back, and reloads.
            XCTAssertEqual(try FeedIntake.take(Self.context(try StoreFixtures.otherText, 9), into: later),
                           FeedIntake.Outcome(result: .accepted, reloadsWidgets: true), why)
            XCTAssertFalse(later.noticeOnDisk, why)
            XCTAssertEqual(try FeedIntake.take(Self.context(try StoreFixtures.otherText, 10), into: later),
                           FeedIntake.Outcome(result: .unchanged, reloadsWidgets: false), why)
            XCTAssertEqual(try FeedIntake.take(Self.context(StoreFixtures.newerText, 11, "00000000000000ff"), into: later),
                           FeedIntake.Outcome(result: .newerVersion, reloadsWidgets: true), why)

            // Nothing stored, a duplicate, a broken text: nothing reloaded.
            XCTAssertEqual(try FeedIntake.take([:], into: later), FeedIntake.Outcome(result: nil, reloadsWidgets: false), why)
            XCTAssertEqual(try FeedIntake.take(Self.context(try StoreFixtures.otherText, 10), into: later),
                           FeedIntake.Outcome(result: .stale, reloadsWidgets: false), why)
            XCTAssertEqual(try FeedIntake.take(Self.context(StoreFixtures.brokenText, 12), into: later),
                           FeedIntake.Outcome(result: .malformed, reloadsWidgets: false), why)
        }
    }

    func testANoticeThatCannotBeWrittenReloadsNothing() throws {
        // The widgets are another process: a notice that is not on disk is not theirs to read.
        let store = FeedStore(directory: try StoreFixtures.directory(self))
        try StoreFixtures.obstruct(FeedStore.partial(of: store.noticeFile))

        XCTAssertEqual(try FeedIntake.take(Self.context(StoreFixtures.newerText, 6), into: store),
                       FeedIntake.Outcome(result: .newerVersion, reloadsWidgets: false))
        XCTAssertTrue(store.newerVersionSeen, "the app still says so, for as long as it lives")
        XCTAssertFalse(store.noticeOnDisk)
        XCTAssertFalse(FeedStore(directory: store.directory).newerVersionSeen, "another process cannot know")
    }

    private static func context(_ feed: Data, _ seq: Int64, _ fingerprint: String = "fedcba9876543210") -> [String: Any] {
        [FeedIntake.feedKey: feed, FeedIntake.deliveryKey: StoreFixtures.delivery(seq, fingerprint).text]
    }
}
