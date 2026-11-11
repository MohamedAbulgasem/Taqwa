import Foundation

/// One feed as a phone delivered it (Ruling 50): `origin`, the phone install that sent it (a
/// random number made once per install), `seq`, its delivery number, strictly increasing per
/// origin, and `fingerprint`, the feed's name. `WatchDelivery` in `WatchLinkContract.kt`.
///
/// It travels beside the feed, under the context's `delivery` key, as `text`; the watch stores it
/// beside the feed and echoes it in its handshake. The watch never computes a fingerprint or a
/// number of its own: both are the phone's, read and said back.
///
/// Always well formed: `origin` and `fingerprint` are 16 lowercase hex characters and `seq` is at
/// least 1. The number is an `Int64`, as Kotlin's `Long` is, since watchOS's `Int` can be 32 bits.
struct Delivery: Equatable {
    let origin: String
    let seq: Int64
    let fingerprint: String

    /// Nil unless the three parts are well formed, as `WatchDelivery`'s `init` requires them.
    init?(origin: String, seq: Int64, fingerprint: String) {
        guard FeedIntake.isOrigin(origin), seq >= 1, FeedIntake.isFingerprint(fingerprint) else { return nil }
        self.origin = origin
        self.seq = seq
        self.fingerprint = fingerprint
    }

    /// `origin/seq/fingerprint`, the number in decimal: the one form every echo carries.
    var text: String { "\(origin)/\(seq)/\(fingerprint)" }

    /// `seq/fingerprint`: what a log line may say of a delivery. Never the origin, the install's
    /// one random number (Ruling 52 item 4: release log lines on every platform).
    var logged: String { "\(seq)/\(fingerprint)" }
}

/// What the watch takes from an application context, and what it says back (plan 4 Task 5, the
/// Lead's call 4: the application context is the one channel). The keys and the receivers' three
/// rules are `WatchLinkContract`'s, ported line for line and held to its vectors (`contract` in
/// `feed-vectors.json`), never invented.
///
/// The phone hands WatchConnectivity `[feed: <the feed's UTF-8 bytes>, delivery: <its delivery's
/// text>]` with `updateApplicationContext`: the whole document, the latest winning. The watch
/// stores it only when `stores` says the delivery moves it forward, and shakes hands with
/// `[refresh: echoOf(<the delivery it holds>)]`; the phone replies `[queued: Bool]`. The watch
/// sends nothing else: a random number, a counter and a fingerprint, nothing of the place or the
/// person (Ruling 39).
enum FeedIntake {
    /// `WatchLinkContract.CONTEXT_FEED_KEY`: the feed's UTF-8 bytes, as `Data`.
    static let feedKey = "feed"
    /// `WatchLinkContract.CONTEXT_DELIVERY_KEY`: the feed's delivery, as its text.
    static let deliveryKey = "delivery"
    /// `WatchLinkContract.REFRESH_MESSAGE_KEY`: the handshake, holding `echoOf` what the watch holds.
    static let refreshKey = "refresh"
    /// `WatchLinkContract.REFRESH_REPLY_KEY`: the phone's answer, whether the current feed is the
    /// watch's or on its way.
    static let replyKey = "queued"

    private static let nameLength = 16

    /// Exactly 16 UTF-16 units of `0-9a-f`, as Kotlin counts a `String`'s length.
    private static func isName(_ text: String) -> Bool {
        text.utf16.count == nameLength && text.utf16.allSatisfy { (0x30...0x39).contains($0) || (0x61...0x66).contains($0) }
    }

    /// `WatchLinkContract.isFingerprint`: the only form the watch stores or echoes.
    static func isFingerprint(_ text: String) -> Bool { isName(text) }

    /// `WatchLinkContract.isOrigin`: a phone install's random number, in a fingerprint's form.
    static func isOrigin(_ text: String) -> Bool { isName(text) }

    /// `WatchLinkContract.deliveryOf`: the delivery `text` holds as `Delivery.text`, an origin, a
    /// number from 1 in its one decimal form (no sign, no leading zero, within an `Int64`) and a
    /// fingerprint, joined by `/`. Nil for anything else, the empty echo of a watch that holds
    /// nothing among it.
    static func deliveryOf(_ text: String) -> Delivery? {
        // Kotlin's `split('/')`: every part, the empty ones included.
        let parts = text.split(separator: "/", omittingEmptySubsequences: false).map(String.init)
        guard parts.count == 3 else { return nil }
        let number = parts[1]
        // Kotlin's `toLongOrNull()` takes a sign; the form check then refuses it, as it refuses a
        // leading zero, so one number has one text.
        guard let seq = Int64(number), seq >= 1, String(seq) == number else { return nil }
        return Delivery(origin: parts[0], seq: seq, fingerprint: parts[2])
    }

    /// `WatchLinkContract.stores`, the receivers' rule (Ruling 50 item 2): a watch that holds
    /// `held` stores `arriving` only when its origin differs, or its number is greater. Anything
    /// else is stale or a duplicate, and is dropped unstored. A greater number is stored even for
    /// the very feed held, its delivery written anew, so that the echo names it.
    static func stores(held: Delivery?, arriving: Delivery) -> Bool {
        guard let held else { return true }
        return arriving.origin != held.origin || arriving.seq > held.seq
    }

    /// `WatchLinkContract.echoOf`: the delivery held, or "" when the watch holds none.
    static func echoOf(_ held: Delivery?) -> String { held?.text ?? "" }

    /// Stores the context's feed, when it carries one with a delivery, and says what the store
    /// made of it. The feed is `Data` under `feedKey`, or a UTF-8 `String`; the delivery is a
    /// `String` under `deliveryKey` that `deliveryOf` reads.
    ///
    /// Nil when there is nothing to store: no feed (an empty context, or Task 5B's probe, whose
    /// keys are none of the feed's), and a feed with no delivery, which is no phone's. A store
    /// that cannot write is thrown, as `FeedStore.write` throws it.
    static func accept(_ context: [String: Any], into store: FeedStore) throws -> FeedWriteResult? {
        guard let feed = feedData(context), let delivery = delivery(context) else { return nil }
        return try store.write(feed, delivery: delivery)
    }

    /// What one context taken in made: `accept`'s answer (nil with nothing to store), and whether
    /// the widgets are to be reloaded for it (`reloadsWidgets`).
    struct Outcome: Equatable {
        let result: FeedWriteResult?
        let reloadsWidgets: Bool
    }

    /// `accept`, with the notice on disk (`FeedStore.noticeOnDisk`) read just before and just
    /// after it, and `reloadsWidgets` given the three: what `WatchSession` does with each context.
    static func take(_ context: [String: Any], into store: FeedStore) throws -> Outcome {
        let noticeBefore = store.noticeOnDisk
        let result = try accept(context, into: store)
        return Outcome(result: result, reloadsWidgets: reloadsWidgets(result, noticeBefore: noticeBefore, noticeAfter: store.noticeOnDisk))
    }

    /// Whether the widgets are reloaded (`NextPrayerWidgets.reload()`: every timeline, and the
    /// Smart Stack's relevance) after an intake that made `result`, given whether the newer-version
    /// notice was on disk before it and after it. WidgetKit gives a watch a daily budget of
    /// reloads, so only a change to what the widgets read reloads them:
    /// - `.accepted`: always. The feed drawn is another, or a notice went with it.
    /// - `.newerVersion`: only when the notice has come (`noticeAfter`, not `noticeBefore`). With
    ///   no feed, or a spent one, the complications' empty form is a timeline that never reloads
    ///   itself (plan 4 Task 6), so this is what makes it say "Update Taqwa on your watch" instead
    ///   of "Open Taqwa on your phone" (Review Focus 1, "with a feed or without"). The newer feed's
    ///   delivery is not stored, so the echo still names the older one and the phone may send it
    ///   again at each handshake: those find the notice there, as does a newer format of another
    ///   version, and reload nothing. A notice whose file could not be written is not there for the
    ///   widgets, another process, to read, so it reloads nothing either.
    /// - `.unchanged`, `.stale`, `.malformed` and nil (nothing to store): never.
    static func reloadsWidgets(_ result: FeedWriteResult?, noticeBefore: Bool, noticeAfter: Bool) -> Bool {
        switch result {
        case .accepted: return true
        case .newerVersion: return noticeAfter && !noticeBefore
        case .unchanged, .stale, .malformed, nil: return false
        }
    }

    /// The handshake, `[refreshKey: echoOf(<the delivery held>)]`: "" while the watch holds no
    /// feed, so a delivery left without its feed is never said back for a feed the watch lacks.
    static func handshake(_ store: FeedStore) -> [String: Any] {
        let held = store.read() == nil ? nil : store.delivery()
        return [refreshKey: echoOf(held)]
    }

    /// The log line for one context taken in through `via` (activation, context): the result,
    /// the delivery's number and fingerprint (`none` without one) and, for a feed stored, its day
    /// count. Never the origin, the city or the bearing (Ruling 52 item 4).
    static func line(_ result: FeedWriteResult?, context: [String: Any], days: Int?, via: String) -> String {
        guard let result else {
            let why = feedData(context) == nil ? "nothing to store" : "not stored: a feed with no delivery"
            return "TaqwaWatch \(why) via \(via)"
        }
        let delivery = delivery(context)?.logged ?? "none"
        let count = days.map { " \($0) days" } ?? ""
        return "TaqwaWatch \(result) \(delivery)\(count) via \(via)"
    }

    /// What a log line says of an echo: `seq/fingerprint`, or `none`.
    static func echoLine(_ echo: String) -> String { deliveryOf(echo)?.logged ?? "none" }

    private static func feedData(_ context: [String: Any]) -> Data? {
        if let data = context[feedKey] as? Data { return data }
        if let text = context[feedKey] as? String { return Data(text.utf8) }
        return nil
    }

    private static func delivery(_ context: [String: Any]) -> Delivery? {
        (context[deliveryKey] as? String).flatMap(deliveryOf)
    }
}
