import XCTest
@testable import WatchCore

/// The feeds a phone writes, read as the watch will read them: plan 2's goldens, the phone's own
/// builder's output (`watchcore/vectors/phone/`), and the four samples the debug builds start from.
/// Each is held whole to its text, key by key (`TextValues`).
final class PhoneGoldensTests: XCTestCase {

    func testEveryPhoneGoldenDecodes() throws {
        for name in Vectors.nonEmpty(try Vectors.files(in: "phone", endingIn: ".json"), "phone goldens") {
            let text = try Vectors.data("phone/\(name)")
            guard case let .feed(feed) = FeedDecoder.decode(text) else {
                XCTFail("\(name) is no feed")
                continue
            }
            XCTAssertEqual(feed.days.count, 31, name)
            XCTAssertFalse(feed.ui.language.isEmpty, name)
            TextValues.assertFeedHolds(feed, text, name)
        }
    }

    func testTheFourSamplesDecode() throws {
        let samples = try Vectors.files(in: "", endingIn: ".json").filter { $0.hasPrefix("sample-feed-capetown") }
        XCTAssertEqual(samples.count, 4, "the samples: \(samples)")
        for name in samples {
            let text = try Vectors.data(name)
            guard case let .feed(feed) = FeedDecoder.decode(text) else {
                XCTFail("\(name) is no feed")
                continue
            }
            XCTAssertEqual(feed.days.count, 31, name)
            XCTAssertFalse(feed.place.city.isEmpty, name)
            XCTAssertFalse(feed.ui.language.isEmpty, name)
            TextValues.assertFeedHolds(feed, text, name)
        }
    }
}
