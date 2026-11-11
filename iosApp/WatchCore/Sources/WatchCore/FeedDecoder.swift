import Foundation

/// What a watch makes of a text it is handed, as the Wear app's `FeedStore` reads Kotlin's two
/// answers: a feed it can draw; a feed of a newer format than this build reads, which keeps the
/// last feed and says "Update Taqwa on your watch"; or a text it cannot use, which keeps the last
/// feed and says nothing (spec 1.1 and 2.6).
enum FeedDecodeResult: Equatable {
    case feed(WatchFeed)
    case newerVersion
    case malformed
}

/// The feed's decoder: `WatchFeedJson.decode` and `versionOf` in one answer (Ruling 56 item 4).
///
/// - It reads the root's `version` first, a whole `Int32` as Kotlin's `Int` is. A version above
///   [version] is `.newerVersion` without the body being read, so a newer phone's feed of any
///   shape is newer. A version that is missing, below 1, `null`, quoted, a boolean, a fraction or
///   beyond an `Int32` is `.malformed`, as is a text that is not a JSON object.
/// - It then decodes the body with Foundation's `JSONDecoder` and its default strategies, into the
///   model's strict types (`Feed.swift`). No field is read leniently, so a quoted number or
///   boolean, which kotlinx reads and no phone writes, is refused (the Lead's call 8). Each `Int`
///   and `Long` is checked against the same number read as a `Decimal` (`decodeWhole`), so a
///   fraction the integer read would round away is refused.
///
/// So on every text kotlinx's encoder can write, and on each with one value changed (the vectors'
/// `ENCODER` and `ONE_CHANGE` cases), it gives Kotlin's answer exactly, but where Kotlin reads a
/// value of the wrong type leniently and the port refuses it (the `swift` cases, below). On a text
/// no encoder writes (`FOREIGN`: an exponent, a key written twice, a trailing comma and the like),
/// it gives Foundation's answer, never a crash, and a feed it reads holds the text's values: each
/// `Double` the nearest to the text's number, of a key written twice the copy `JSONDecoder` keeps,
/// the first, and each integer exactly as far as Foundation's `Decimal` keeps a number's digits,
/// about 38 significant ones (Ruling 57 item 1). Past that, a `FOREIGN` number may read as its
/// rounded whole value. The phone writes every integer as plain digits, so no `ENCODER` or
/// `ONE_CHANGE` text meets that limit. It has no JSON grammar scanner of its own.
///
/// The `swift` cases are the safe differences of the Lead's call 8 and Ruling 57 item 2: a quoted
/// number or boolean, or a bare digit for `digitZero`, which kotlinx reads as its value and no
/// phone writes, is refused here, so the watch keeps the last feed it understood.
enum FeedDecoder {
    /// The newest format this build reads: `WatchFeedJson.VERSION`.
    static let version = 1

    static func decode(_ data: Data) -> FeedDecodeResult {
        let decoder = JSONDecoder()
        guard let root = try? decoder.decode(Root.self, from: data) else { return .malformed }
        if root.version > Int32(version) { return .newerVersion }
        if root.version < 1 { return .malformed }
        guard let feed = try? decoder.decode(WatchFeed.self, from: data) else { return .malformed }
        return .feed(feed)
    }

    /// The root's version alone, as `decode` reads it first, or nil where it reads none: what the
    /// store's newer-version notice names (`WatchFeedJson.versionOf`'s use in the Wear store).
    static func versionOf(_ data: Data) -> Int32? {
        (try? JSONDecoder().decode(Root.self, from: data))?.version
    }

    /// The root's version alone: every other key is left unread.
    private struct Root: Decodable {
        let version: Int32

        private enum CodingKeys: String, CodingKey { case version }

        init(from decoder: Decoder) throws {
            version = try decoder.container(keyedBy: CodingKeys.self).decodeWhole(Int32.self, forKey: .version)
        }
    }
}
