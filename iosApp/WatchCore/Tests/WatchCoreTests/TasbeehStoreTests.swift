import Foundation
import XCTest
@testable import WatchCore

/// Where the watch keeps its counts (plan 4 Task 8): one `tasbeeh.json`, written atomically, in a
/// directory of the app's own Application Support marked excluded from backup (Ruling 39's Backups
/// paragraph, the watch's half), holding each phrase's count and round by its id and the phrase
/// chosen. A phrase the phone no longer sends falls back to After prayer.
final class TasbeehStoreTests: XCTestCase {

    private func store() throws -> TasbeehStore {
        TasbeehStore(directory: try StoreFixtures.directory(self))
    }

    func testCountsAreKeptPerPhrase() throws {
        let directory = try StoreFixtures.directory(self)
        let first = TasbeehStore(directory: directory)
        XCTAssertEqual(first.state(for: "subhanallah"), TasbeehState(presetId: "subhanallah", count: 0, round: 1), "0 of round 1 by default")
        first.save(TasbeehState(presetId: "subhanallah", count: 64, round: 2))
        first.save(TasbeehState(presetId: "custom_1", count: 7, round: 1))
        first.save(TasbeehState(presetId: "subhanallah", count: 65, round: 2))

        let again = TasbeehStore(directory: directory)
        XCTAssertEqual(again.state(for: "subhanallah"), TasbeehState(presetId: "subhanallah", count: 65, round: 2), "read back, the last save")
        XCTAssertEqual(again.state(for: "custom_1"), TasbeehState(presetId: "custom_1", count: 7, round: 1))
        XCTAssertEqual(again.state(for: "after_prayer"), TasbeehState(presetId: "after_prayer", count: 0, round: 1))
        XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: directory.path), ["tasbeeh.json"], "one file, and no leftover")
    }

    func testTheStoreIsExcludedFromBackup() throws {
        let tasbeeh = try store()
        tasbeeh.save(TasbeehState(presetId: "subhanallah", count: 1, round: 1))
        let values = try tasbeeh.directory.resourceValues(forKeys: [.isExcludedFromBackupKey])
        XCTAssertEqual(values.isExcludedFromBackup, true)
        XCTAssertEqual(tasbeeh.file.deletingLastPathComponent().standardizedFileURL, tasbeeh.directory.standardizedFileURL, "the file lives in it")
        // Where the app keeps it (read, never made, here on the Mac): its own Application Support.
        XCTAssertEqual(TasbeehStore.appDirectory.lastPathComponent, "Tasbeeh")
        XCTAssertEqual(TasbeehStore.appDirectory.deletingLastPathComponent().lastPathComponent, "Application Support")
        XCTAssertFalse(TasbeehStore.appDirectory.path.contains(FeedStore.appGroupIdentifier), "the app's own, not the group's")
    }

    func testTheSelectionIsReadBack() throws {
        let directory = try StoreFixtures.directory(self)
        let first = TasbeehStore(directory: directory)
        XCTAssertEqual(first.selectedId, TasbeehPresets.defaultId, "After prayer until one is chosen")
        first.selectedId = "astaghfirullah"
        XCTAssertEqual(TasbeehStore(directory: directory).selectedId, "astaghfirullah")
    }

    func testADeletedPhraseFallsBackToAfterPrayer() throws {
        let sample = try Vectors.feed("sample")
        let tasbeeh = try store()
        tasbeeh.selectedId = "custom_1730000000001"
        XCTAssertEqual(tasbeeh.selected(in: TasbeehPresets.list(sample)).id, "custom_1730000000001", "still sent")

        XCTAssertEqual(tasbeeh.selected(in: TasbeehPresets.list(nil)).id, TasbeehPresets.defaultId, "no longer sent")
        XCTAssertEqual(tasbeeh.selectedId, TasbeehPresets.defaultId, "and the fallback is the choice")
    }

    /// A file that does not read, or a count that cannot be, is a fresh start for that phrase,
    /// never a crash: the Galaxy's `TasbeehCodec` rule.
    func testAFileThatDoesNotReadIsAFreshStart() throws {
        let directory = try StoreFixtures.directory(self)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try Data("{ not json".utf8).write(to: directory.appendingPathComponent("tasbeeh.json"))
        let tasbeeh = TasbeehStore(directory: directory)
        XCTAssertEqual(tasbeeh.state(for: "subhanallah"), TasbeehState(presetId: "subhanallah", count: 0, round: 1))
        XCTAssertEqual(tasbeeh.selectedId, TasbeehPresets.defaultId)

        try Data(#"{"selected":"subhanallah","states":{"subhanallah":{"presetId":"subhanallah","count":-3,"round":0}}}"#.utf8)
            .write(to: directory.appendingPathComponent("tasbeeh.json"))
        let held = TasbeehStore(directory: directory)
        XCTAssertEqual(held.state(for: "subhanallah"), TasbeehState(presetId: "subhanallah", count: 0, round: 1), "never below 0 or round 1")
        XCTAssertEqual(held.selectedId, "subhanallah")
    }

    /// The DEBUG `-taqwaFresh 1` (plan 4 erratum p4 #9): every count and the choice forgotten.
    func testClearForgetsEveryCountAndTheChoice() throws {
        let directory = try StoreFixtures.directory(self)
        let tasbeeh = TasbeehStore(directory: directory)
        tasbeeh.save(TasbeehState(presetId: "subhanallah", count: 9, round: 3))
        tasbeeh.selectedId = "subhanallah"
        tasbeeh.clear()
        XCTAssertEqual(tasbeeh.state(for: "subhanallah").count, 0)
        XCTAssertEqual(tasbeeh.selectedId, TasbeehPresets.defaultId)
        let again = TasbeehStore(directory: directory)
        XCTAssertEqual(again.state(for: "subhanallah").count, 0)
        XCTAssertEqual(again.selectedId, TasbeehPresets.defaultId)
    }
}
