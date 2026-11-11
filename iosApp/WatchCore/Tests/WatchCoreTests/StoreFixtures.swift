import Foundation
import XCTest
@testable import WatchCore

/// What the store's, the intake's and the handshake's tests share: a directory of their own for
/// each test, two feeds that differ, and deliveries in the contract's form.
enum StoreFixtures {
    /// The Cape Town sample as the phone wrote it, and its twelve-hour twin: two feeds that differ.
    static var sampleText: Data { get throws { try Vectors.data("sample-feed-capetown.json") } }
    static var otherText: Data { get throws { try Vectors.data("sample-feed-capetown-en12.json") } }

    static var sample: WatchFeed { get throws { try decoded(sampleText) } }
    static var other: WatchFeed { get throws { try decoded(otherText) } }

    /// A feed in a format newer than this build reads: the version is all the watch reads of it.
    static let newerText = Data(#"{"version":2,"anything":"a later phone's"}"#.utf8)

    /// Text that is no feed: cut short.
    static let brokenText = Data("{".utf8)

    /// A delivery of the origin `0123456789abcdef`, numbered [seq], of the feed [fingerprint].
    static func delivery(_ seq: Int64, _ fingerprint: String = "fedcba9876543210", origin: String = "0123456789abcdef") -> Delivery {
        guard let delivery = FeedIntake.deliveryOf("\(origin)/\(seq)/\(fingerprint)") else {
            fatalError("not a delivery: \(origin)/\(seq)/\(fingerprint)")
        }
        return delivery
    }

    static func decoded(_ text: Data) throws -> WatchFeed {
        guard case let .feed(feed) = FeedDecoder.decode(text) else { throw VectorError("not a feed") }
        return feed
    }

    /// A new, empty directory for one test, removed when the test ends.
    static func directory(_ test: XCTestCase) throws -> URL {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("taqwa-watch-store-\(UUID().uuidString)", isDirectory: true)
        test.addTeardownBlock { try? FileManager.default.removeItem(at: url) }
        return url.appendingPathComponent("TaqwaWatch", isDirectory: true)
    }

    /// Puts a directory, with a file in it, where a file is to be written, so that writing it fails.
    static func obstruct(_ url: URL) throws {
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        try Data("x".utf8).write(to: url.appendingPathComponent("in-the-way"))
    }
}
