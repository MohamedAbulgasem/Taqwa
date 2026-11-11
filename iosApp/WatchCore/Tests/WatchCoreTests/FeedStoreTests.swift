import Foundation
import XCTest
@testable import WatchCore

/// The watch's store (plan 4 Task 5, Ruling 50): a feed is kept only when its delivery moves the
/// watch forward, it is kept whole, in `WatchLinkContract.STORE_STEPS`' order, and nothing of it
/// goes to a backup.
final class FeedStoreTests: XCTestCase {

    func testWriteThenReadReturnsTheFeedAndItsDelivery() throws {
        let store = FeedStore(directory: try StoreFixtures.directory(self))
        XCTAssertNil(store.read(), "a new store holds no feed")
        XCTAssertNil(store.delivery())

        let d5 = StoreFixtures.delivery(5)
        XCTAssertEqual(try store.write(try StoreFixtures.sampleText, delivery: d5), .accepted)

        XCTAssertEqual(store.read(), try StoreFixtures.sample)
        XCTAssertEqual(store.delivery(), d5)
        XCTAssertEqual(try String(contentsOf: store.deliveryFile, encoding: .utf8), d5.text, "kept in the one form every echo carries")
        XCTAssertFalse(store.newerVersionSeen)
        // Another store on the same directory, as the widgets' process opens it, reads the same.
        let again = FeedStore(directory: store.directory)
        XCTAssertEqual(again.read(), try StoreFixtures.sample)
        XCTAssertEqual(again.delivery(), d5)
    }

    func testAMalformedFeedLeavesTheOldOneInPlace() throws {
        let store = FeedStore(directory: try StoreFixtures.directory(self))
        let d5 = StoreFixtures.delivery(5)
        _ = try store.write(try StoreFixtures.sampleText, delivery: d5)

        let cut = try StoreFixtures.otherText.prefix(5_000)
        XCTAssertEqual(try store.write(Data(cut), delivery: StoreFixtures.delivery(6, "00000000000000ff")), .malformed)
        XCTAssertEqual(try store.write(StoreFixtures.brokenText, delivery: nil), .malformed)

        XCTAssertEqual(store.read(), try StoreFixtures.sample, "the last feed it understood")
        XCTAssertEqual(store.delivery(), d5, "and its delivery, so the echo still names it")
        XCTAssertFalse(store.newerVersionSeen, "a broken text says nothing")
    }

    func testANewerVersionKeepsTheOldFeedAndRemembersTheNotice() throws {
        let store = FeedStore(directory: try StoreFixtures.directory(self))
        let d5 = StoreFixtures.delivery(5)
        _ = try store.write(try StoreFixtures.sampleText, delivery: d5)

        XCTAssertEqual(try store.write(StoreFixtures.newerText, delivery: StoreFixtures.delivery(6, "00000000000000ff")), .newerVersion)

        XCTAssertEqual(store.read(), try StoreFixtures.sample, "the last feed it understood")
        XCTAssertEqual(store.delivery(), d5)
        XCTAssertTrue(store.newerVersionSeen, "Update Taqwa on your watch")
        XCTAssertEqual(try String(contentsOf: store.noticeFile, encoding: .utf8), "2", "the notice names the version it was for")

        // A feed this build reads ends it.
        XCTAssertEqual(try store.write(try StoreFixtures.otherText, delivery: StoreFixtures.delivery(7, "00000000000000ff")), .accepted)
        XCTAssertFalse(store.newerVersionSeen)
        XCTAssertFalse(FileManager.default.fileExists(atPath: store.noticeFile.path))
    }

    func testTheNoticeOutlivesTheStore() throws {
        let directory = try StoreFixtures.directory(self)
        XCTAssertEqual(try FeedStore(directory: directory).write(StoreFixtures.newerText, delivery: nil), .newerVersion)

        let later = FeedStore(directory: directory)
        XCTAssertTrue(later.newerVersionSeen, "a new process still says so")
        XCTAssertNil(later.read(), "with no feed")
    }

    func testANoticeForAVersionThisBuildReadsIsNone() throws {
        let store = FeedStore(directory: try StoreFixtures.directory(self))
        _ = try store.write(StoreFixtures.newerText, delivery: nil)
        // The watch app was updated to a build that reads that version: its notice no longer applies.
        try Data(String(FeedDecoder.version).utf8).write(to: store.noticeFile)
        XCTAssertFalse(FeedStore(directory: store.directory).newerVersionSeen)
        try Data("garbage".utf8).write(to: store.noticeFile)
        XCTAssertFalse(FeedStore(directory: store.directory).newerVersionSeen)
    }

    func testTheSameFeedUnderANewerNumberIsUnchangedAndKeepsTheNewerDelivery() throws {
        let store = FeedStore(directory: try StoreFixtures.directory(self))
        _ = try store.write(try StoreFixtures.sampleText, delivery: StoreFixtures.delivery(5))

        let d6 = StoreFixtures.delivery(6)
        XCTAssertEqual(try store.write(try StoreFixtures.sampleText, delivery: d6), .unchanged, "no widget reload")

        XCTAssertEqual(store.delivery(), d6, "the echo names the newer number")
        XCTAssertEqual(store.read(), try StoreFixtures.sample)
    }

    func testTheSameFeedAfterANewerNoticeIsAccepted() throws {
        // The notice goes, and what the surfaces draw with it: so the widgets are reloaded.
        let store = FeedStore(directory: try StoreFixtures.directory(self))
        _ = try store.write(try StoreFixtures.sampleText, delivery: StoreFixtures.delivery(5))
        _ = try store.write(StoreFixtures.newerText, delivery: StoreFixtures.delivery(6, "00000000000000ff"))

        XCTAssertEqual(try store.write(try StoreFixtures.sampleText, delivery: StoreFixtures.delivery(7)), .accepted)
        XCTAssertFalse(store.newerVersionSeen)
        XCTAssertEqual(store.delivery(), StoreFixtures.delivery(7))
    }

    func testAStaleOrDuplicateDeliveryIsDropped() throws {
        let store = FeedStore(directory: try StoreFixtures.directory(self))
        let d5 = StoreFixtures.delivery(5)
        _ = try store.write(try StoreFixtures.sampleText, delivery: d5)
        let feedBytes = try Data(contentsOf: store.feedFile)

        for (arriving, why) in [
            (StoreFixtures.delivery(5), "the same number, the same feed: a duplicate"),
            (StoreFixtures.delivery(5, "00000000000000ff"), "the same number, another feed"),
            (StoreFixtures.delivery(4, "00000000000000ff"), "a lower number: late"),
        ] {
            XCTAssertEqual(try store.write(try StoreFixtures.otherText, delivery: arriving), .stale, why)
            // Not even read: a newer text or a broken one is stale all the same, and leaves no notice.
            XCTAssertEqual(try store.write(StoreFixtures.newerText, delivery: arriving), .stale, why)
            XCTAssertEqual(try store.write(StoreFixtures.brokenText, delivery: arriving), .stale, why)
            XCTAssertEqual(try Data(contentsOf: store.feedFile), feedBytes, why)
            XCTAssertEqual(store.delivery(), d5, why)
            XCTAssertFalse(store.newerVersionSeen, why)
        }
    }

    func testAnotherOriginsFeedIsStored() throws {
        let store = FeedStore(directory: try StoreFixtures.directory(self))
        _ = try store.write(try StoreFixtures.sampleText, delivery: StoreFixtures.delivery(5))

        // A reinstalled phone app starts its numbers again, under a new origin.
        let fresh = StoreFixtures.delivery(1, "00000000000000ff", origin: "1111111111111111")
        XCTAssertEqual(try store.write(try StoreFixtures.otherText, delivery: fresh), .accepted)
        XCTAssertEqual(store.read(), try StoreFixtures.other)
        XCTAssertEqual(store.delivery(), fresh)
    }

    func testAFeedWithoutADeliveryIsStoredWithNone() throws {
        // The DEBUG samples: stored whatever is held, and never beside a phone's delivery.
        let store = FeedStore(directory: try StoreFixtures.directory(self))
        _ = try store.write(try StoreFixtures.sampleText, delivery: StoreFixtures.delivery(5))

        XCTAssertEqual(try store.write(try StoreFixtures.otherText, delivery: nil), .accepted)
        XCTAssertEqual(store.read(), try StoreFixtures.other)
        XCTAssertNil(store.delivery())
        XCTAssertFalse(FileManager.default.fileExists(atPath: store.deliveryFile.path))
        XCTAssertEqual(try store.write(try StoreFixtures.otherText, delivery: nil), .accepted, "with no delivery there is no fingerprint to compare")
    }

    func testTheStoreFollowsTheContractsSteps() throws {
        XCTAssertEqual(FeedStore.steps.map(\.rawValue), Vectors.feeds.contract.storeSteps, "the steps this store is held to")

        // The feed cannot be put in place (its temporary file's path is taken by a directory), so
        // the write stops after the delivery's removal: the old feed and no delivery, never the old
        // delivery beside a new feed or the new one beside the old feed.
        let store = FeedStore(directory: try StoreFixtures.directory(self))
        _ = try store.write(try StoreFixtures.sampleText, delivery: StoreFixtures.delivery(5))
        try StoreFixtures.obstruct(FeedStore.partial(of: store.feedFile))

        XCTAssertThrowsError(try store.write(try StoreFixtures.otherText, delivery: StoreFixtures.delivery(6, "00000000000000ff")))
        XCTAssertEqual(store.read(), try StoreFixtures.sample, "the old feed")
        XCTAssertNil(store.delivery(), "and no delivery: the handshake says nothing, and the phone sends once more")
        XCTAssertFalse(FileManager.default.fileExists(atPath: store.deliveryFile.path))
    }

    func testAFailedDeliveryWriteLeavesNoneNeverTheOldOne() throws {
        let store = FeedStore(directory: try StoreFixtures.directory(self))
        _ = try store.write(try StoreFixtures.sampleText, delivery: StoreFixtures.delivery(5))
        try StoreFixtures.obstruct(FeedStore.partial(of: store.deliveryFile))

        XCTAssertEqual(
            try store.write(try StoreFixtures.otherText, delivery: StoreFixtures.delivery(6, "00000000000000ff")), .accepted,
            "the feed is stored: nothing past that fails the write"
        )
        XCTAssertEqual(store.read(), try StoreFixtures.other)
        XCTAssertNil(store.delivery(), "none, not number 5")
    }

    func testALeftoverTempFileIsIgnored() throws {
        let store = FeedStore(directory: try StoreFixtures.directory(self))
        let d5 = StoreFixtures.delivery(5)
        _ = try store.write(try StoreFixtures.sampleText, delivery: d5)
        // Writes that died after they began: part of the next feed and a delivery in the temporary files.
        try Data(try StoreFixtures.otherText.prefix(5_000)).write(to: FeedStore.partial(of: store.feedFile))
        try Data("0123456789abcdef/9/00000000000000ff".utf8).write(to: FeedStore.partial(of: store.deliveryFile))

        let later = FeedStore(directory: store.directory)
        XCTAssertEqual(later.read(), try StoreFixtures.sample, "a temporary file is not the feed")
        XCTAssertEqual(later.delivery(), d5)

        let d6 = StoreFixtures.delivery(6, "00000000000000ff")
        XCTAssertEqual(try later.write(try StoreFixtures.otherText, delivery: d6), .accepted, "and the next write replaces it")
        XCTAssertEqual(later.read(), try StoreFixtures.other)
        XCTAssertEqual(later.delivery(), d6)
        XCTAssertFalse(FileManager.default.fileExists(atPath: FeedStore.partial(of: store.feedFile).path), "renamed away, not copied")
        XCTAssertFalse(FileManager.default.fileExists(atPath: FeedStore.partial(of: store.deliveryFile).path))
    }

    func testADeliveryFileThatHoldsNoDeliveryIsNone() throws {
        let store = FeedStore(directory: try StoreFixtures.directory(self))
        _ = try store.write(try StoreFixtures.sampleText, delivery: StoreFixtures.delivery(5))
        for text in ["", "garbage", StoreFixtures.delivery(5).text + "\n", "0123456789abcdef/0/fedcba9876543210", "fedcba9876543210"] {
            try Data(text.utf8).write(to: store.deliveryFile)
            XCTAssertNil(store.delivery(), "'\(text)'")
        }
    }

    func testTheDirectoryIsExcludedFromBackup() throws {
        let directory = try StoreFixtures.directory(self)
        XCTAssertFalse(FileManager.default.fileExists(atPath: directory.path))
        let store = FeedStore(directory: directory)

        let values = try store.directory.resourceValues(forKeys: [.isExcludedFromBackupKey])
        XCTAssertEqual(values.isExcludedFromBackup, true, "made, and marked as it is made")

        // A directory made before the mark (an earlier build's) is marked when the store opens it.
        let unmarked = try StoreFixtures.directory(self)
        try FileManager.default.createDirectory(at: unmarked, withIntermediateDirectories: true)
        XCTAssertNotEqual(try unmarked.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup, true)
        _ = FeedStore(directory: unmarked)
        var fresh = unmarked
        fresh.removeAllCachedResourceValues()
        XCTAssertEqual(try fresh.resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup, true)
    }

    func testEveryFileLivesInTheExcludedDirectory() throws {
        let store = FeedStore(directory: try StoreFixtures.directory(self))
        for file in [store.feedFile, store.deliveryFile, store.noticeFile, store.askedFile] {
            XCTAssertEqual(file.deletingLastPathComponent().standardizedFileURL, store.directory.standardizedFileURL, file.lastPathComponent)
        }
        XCTAssertEqual(store.askedFile.lastPathComponent, "refresh.asked")
    }

    func testClearForgetsEverything() throws {
        let store = FeedStore(directory: try StoreFixtures.directory(self))
        _ = try store.write(try StoreFixtures.sampleText, delivery: StoreFixtures.delivery(5))
        _ = try store.write(StoreFixtures.newerText, delivery: StoreFixtures.delivery(6, "00000000000000ff"))
        try Data("half a feed".utf8).write(to: FeedStore.partial(of: store.feedFile))

        try store.clear()

        XCTAssertNil(store.read())
        XCTAssertNil(store.delivery())
        XCTAssertFalse(store.newerVersionSeen, "a watch that has never held a feed")
        XCTAssertFalse(FeedStore(directory: store.directory).newerVersionSeen)
        let left = try FileManager.default.contentsOfDirectory(atPath: store.directory.path)
        XCTAssertEqual(left, [], "no feed, notice, delivery or temporary file on disk")
    }

    func testTheHandshakeStampKeepsItsTimeInsideTheFile() throws {
        let store = FeedStore(directory: try StoreFixtures.directory(self))
        XCTAssertNil(store.lastAskedAt())

        store.stampAsked(1_792_330_000)
        XCTAssertEqual(FeedStore(directory: store.directory).lastAskedAt(), 1_792_330_000, "it outlives the process")
        XCTAssertEqual(try String(contentsOf: store.askedFile, encoding: .utf8), "1792330000", "the time is in the file, never its date")

        // A handshake that failed is not counted, unless a later one was stamped since.
        store.unstampAsked(ifAt: 1_792_330_001)
        XCTAssertEqual(store.lastAskedAt(), 1_792_330_000)
        store.unstampAsked(ifAt: 1_792_330_000)
        XCTAssertNil(store.lastAskedAt())

        try Data("soon".utf8).write(to: store.askedFile)
        XCTAssertNil(store.lastAskedAt(), "anything but a number is none")
    }

    func testTheAppGroupIsTheIPhones() throws {
        // The Lead's call 3: the watch's targets share the iPhone's own group, read from its
        // entitlements, so the constant can never drift from what the targets are signed with.
        let repository = Vectors.directory.deletingLastPathComponent().deletingLastPathComponent()
        for path in ["iosApp/iosApp/iosApp.entitlements", "iosApp/TaqwaWatch/TaqwaWatch.entitlements", "iosApp/TaqwaWatchWidgets/TaqwaWatchWidgets.entitlements"] {
            let plist = try PropertyListSerialization.propertyList(
                from: try Data(contentsOf: repository.appendingPathComponent(path)), format: nil
            ) as? [String: Any]
            XCTAssertEqual(plist?["com.apple.security.application-groups"] as? [String], [FeedStore.appGroupIdentifier], path)
        }
    }
}
