import Foundation
import XCTest
@testable import PhoneHarness

/// The iPhone's watch key (Amendment A3), kept in `taqwa-watch-key` inside WatchConnectivity's
/// watch directory: read when kept, written when not, and the file itself marked excluded from
/// backup whenever it is read or written (Ruling 69 item 3). So the privacy text's Backups sentence
/// no longer rests on `recitationFilesDirectory()` marking all of Application Support as a side
/// effect. Each test's folder is unmarked, so a mark the test reads is the file's own: on the Mac a
/// file under a marked folder reads as excluded too (plan 4 Task 5B's backup check).
final class WatchKeyFileTests: XCTestCase {

    func testANewKeyIsWrittenAndTheFileItselfIsExcludedFromBackup() throws {
        let directory = try folder()
        let answer = WatchKeyFile.key(in: directory, make: { "NEW-KEY" })

        XCTAssertEqual(answer.key, "NEW-KEY")
        XCTAssertEqual(answer.log, ["TaqwaWatch key new"])
        XCTAssertEqual(try String(contentsOf: file(in: directory), encoding: .utf8), "NEW-KEY")
        XCTAssertEqual(try excluded(file(in: directory)), true, "the key's own file is marked")
        XCTAssertNotEqual(try excluded(directory), true, "and not through its folder")
    }

    func testAKeptKeyIsReadAndAnEarlierBuildsUnmarkedFileIsMarked() throws {
        let directory = try folder()
        try "KEPT-KEY".write(to: file(in: directory), atomically: true, encoding: .utf8)
        XCTAssertNotEqual(try excluded(file(in: directory)), true, "an earlier build wrote it unmarked")

        let answer = WatchKeyFile.key(in: directory, make: { XCTFail("a kept key is never made anew"); return "NEW-KEY" })

        XCTAssertEqual(answer.key, "KEPT-KEY")
        XCTAssertEqual(answer.log, ["TaqwaWatch key kept"])
        XCTAssertEqual(try excluded(file(in: directory)), true, "marked when it is read")
    }

    func testAnEmptyFileIsReplacedByANewMarkedKey() throws {
        let directory = try folder()
        try "".write(to: file(in: directory), atomically: true, encoding: .utf8)

        let answer = WatchKeyFile.key(in: directory, make: { "NEW-KEY" })

        XCTAssertEqual(answer.key, "NEW-KEY")
        XCTAssertEqual(answer.log, ["TaqwaWatch key new"])
        XCTAssertEqual(try String(contentsOf: file(in: directory), encoding: .utf8), "NEW-KEY")
        XCTAssertEqual(try excluded(file(in: directory)), true)
    }

    /// A key that cannot be kept is still answered, and differs at the next read: the phone sends
    /// once more, and never takes a confirmation for another watch's.
    func testAKeyThatCannotBeKeptIsStillAnsweredAndSaidSo() throws {
        let missing = try folder().appendingPathComponent("gone", isDirectory: true)

        let answer = WatchKeyFile.key(in: missing, make: { "NEW-KEY" })

        XCTAssertEqual(answer.key, "NEW-KEY")
        XCTAssertEqual(answer.log.count, 1)
        XCTAssertTrue(answer.log[0].hasPrefix("TaqwaWatch key new, not kept: "), answer.log[0])
        XCTAssertFalse(FileManager.default.fileExists(atPath: missing.path))

        // The next read, with the real maker: another key, so no confirmation is taken for it.
        let first = WatchKeyFile.key(in: missing).key
        XCTAssertNotEqual(WatchKeyFile.key(in: missing).key, first, "a key not kept differs at the next read")
    }

    func testTheFileIsNamedAsTheContractSays() {
        XCTAssertEqual(WatchKeyFile.name, "taqwa-watch-key")
    }

    // MARK: - Helpers

    private func folder() throws -> URL {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("taqwa-watch-key-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        addTeardownBlock { try? FileManager.default.removeItem(at: url) }
        return url
    }

    private func file(in directory: URL) -> URL {
        directory.appendingPathComponent(WatchKeyFile.name, isDirectory: false)
    }

    /// Read from a fresh URL, so no cached value answers.
    private func excluded(_ url: URL) throws -> Bool? {
        try URL(fileURLWithPath: url.path).resourceValues(forKeys: [.isExcludedFromBackupKey]).isExcludedFromBackup
    }
}
