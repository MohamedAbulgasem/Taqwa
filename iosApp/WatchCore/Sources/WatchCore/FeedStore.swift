import Foundation

/// What `FeedStore.write` made of the text it was given.
enum FeedWriteResult: Equatable {
    /// A feed this build reads, stored: it is now the feed, and every surface draws again.
    case accepted
    /// The very feed held, under a greater number (its fingerprint the one it replaced): stored,
    /// its newer delivery written, so the echo names it, and nothing drawn changes, so the
    /// widgets are not reloaded.
    case unchanged
    /// A delivery that does not move the watch forward (`FeedIntake.stores`): the same or a lower
    /// number of the origin held, a duplicate or a late one. Nothing is read or touched.
    case stale
    /// A feed in a newer format than this build reads: the stored feed stays the feed, and the
    /// watch says "Update Taqwa on your watch" (spec 1.1 and 2.6). The widgets are reloaded only
    /// when its notice comes (`FeedIntake.reloadsWidgets`), not each time it is sent again.
    case newerVersion
    /// Text that is no feed, or is cut short: nothing is stored, and nothing is said.
    case malformed
}

/// A step of storing a feed, in `WatchLinkContract.STORE_STEPS`' order (Ruling 50).
enum StoreStep: String, CaseIterable {
    /// The delivery held is removed, so for a moment the watch holds none.
    case REMOVE_DELIVERY
    /// The arriving feed is put in place: written aside, then renamed over the old one.
    case PLACE_FEED
    /// The arriving delivery is written beside it, the same way.
    case WRITE_DELIVERY
}

/// The feed the phone sent last, its delivery, the newer-version notice and the handshake's stamp,
/// each a file in one directory (plan 4 Task 5, spec 2.2). A watch away from its phone draws from
/// the feed for the weeks it holds, and the widgets' process reads what the app's wrote, so they
/// are files, never something in memory. The Wear app's `FeedStore`, ported, with Ruling 50's
/// receivers' rules (`FeedIntake`) and `WatchLinkContract.STORE_STEPS`' order.
///
/// - **Only what moves the watch forward is stored.** A write with a delivery that `stores`
///   refuses is `.stale` before the text is read. A text that is no feed is `.malformed` and a feed
///   of a newer format `.newerVersion`: either way the stored feed and its delivery stay. A newer
///   one also leaves a notice, `feed.newer`, that outlives the process (Ruling 13's twin) and
///   names the version it was for, so a watch updated to a build that reads it says nothing more.
/// - **A feed is stored in `steps`' order:** the delivery file is removed, the feed is written to
///   its temporary twin (`partial(of:)`), put on disk and renamed over `feed.json`, then the
///   delivery is written the same way, and the notice is cleared. A rename is atomic, so a reader
///   sees the old feed or the new one, never a part of either. Stopped after any step, by a crash
///   or a write that fails, the watch holds the old feed or the new one and no delivery, never a
///   delivery naming a feed it does not hold: its echo is then empty, and the phone sends once
///   more. A delivery whose write fails leaves none and does not fail the write.
/// - **A feed with no delivery** (the DEBUG samples) is stored whatever is held, its delivery
///   file removed first, so a sample never carries a phone's delivery.
/// - **Nothing goes to a backup** (Ruling 39): the directory is marked `isExcludedFromBackup`
///   when the store opens it, which covers every file in it. That is the watch's half of the
///   privacy policy's Backups paragraph.
/// - **The stamp** `refresh.asked` keeps the time of the last handshake inside the file, never in
///   its date, so no file-timestamp API is used (the privacy manifest).
///
/// Writes wait for each other; reads never wait. Every time is the phone's or the caller's: the
/// store reads no clock.
final class FeedStore {
    /// `WatchLinkContract.STORE_STEPS`, the order `write` stores in.
    static let steps: [StoreStep] = [.REMOVE_DELIVERY, .PLACE_FEED, .WRITE_DELIVERY]

    /// The iPhone's own app group, `group.world.taqwa.ios` (the Lead's call 3), which both watch
    /// targets are signed with. A group's container is per device, so the watch's is its own.
    static let appGroupIdentifier = "group.world.taqwa.ios"

    let directory: URL
    var feedFile: URL { directory.appendingPathComponent("feed.json", isDirectory: false) }
    var deliveryFile: URL { directory.appendingPathComponent("feed.delivery", isDirectory: false) }
    var noticeFile: URL { directory.appendingPathComponent("feed.newer", isDirectory: false) }
    var askedFile: URL { directory.appendingPathComponent("refresh.asked", isDirectory: false) }

    private let writeLock = NSLock()
    /// Said as soon as a newer feed is seen, so the watch says it is out of date for as long as
    /// this process lives even when the notice's file cannot be written.
    private var newerSeenHere = false

    /// The store on `directory`, made when missing, and marked excluded from backup either way.
    init(directory: URL) {
        self.directory = directory
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var marked = directory
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? marked.setResourceValues(values)
    }

    /// The store both watch targets read: `<group container>/Library/Application Support/
    /// TaqwaWatch`, one instance per process. A build with no app group (an unsigned one) keeps it
    /// in the target's own Application Support instead, where the widgets cannot read it.
    static func appGroup() -> FeedStore { shared }

    private static let shared: FeedStore = {
        let base = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupIdentifier)
            .map { $0.appendingPathComponent("Library/Application Support", isDirectory: true) }
            ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return FeedStore(directory: base.appendingPathComponent("TaqwaWatch", isDirectory: true))
    }()

    /// Where a file is written before it is renamed over itself: its name and `.tmp`, beside it.
    static func partial(of file: URL) -> URL {
        file.deletingLastPathComponent().appendingPathComponent(file.lastPathComponent + ".tmp", isDirectory: false)
    }

    // MARK: - Reading

    /// The stored feed, read now; nil when there is none or it cannot be read.
    func read() -> WatchFeed? {
        guard let data = try? Data(contentsOf: feedFile), case let .feed(feed) = FeedDecoder.decode(data) else { return nil }
        return feed
    }

    /// The delivery the stored feed came with, read now as `deliveryOf` reads it; nil when there
    /// is none, or the file holds anything else.
    func delivery() -> Delivery? {
        guard let data = try? Data(contentsOf: deliveryFile), let text = String(data: data, encoding: .utf8) else { return nil }
        return FeedIntake.deliveryOf(text)
    }

    /// Whether a feed in a newer format than this build reads has been seen, and no feed this
    /// build reads stored since.
    var newerVersionSeen: Bool {
        writeLock.lock()
        let here = newerSeenHere
        writeLock.unlock()
        return here || noticeOnDisk
    }

    /// Whether the notice on disk names a format newer than this build reads: all that a process
    /// which has seen no newer feed itself, the widgets' among them, reads as `newerVersionSeen`.
    /// `FeedIntake.take` reads it before and after each intake, to reload the widgets when it comes.
    var noticeOnDisk: Bool { noticeVersion() > Int32(FeedDecoder.version) }

    // MARK: - Writing

    /// Stores `feed`, the text a phone sent, when this build reads it and `delivery` moves the
    /// watch forward, and says what it made of it (the type's notes). A file system error
    /// removing the old delivery or putting the feed in place is thrown, with the old feed kept
    /// and, past the removal, no delivery: the steps' promise.
    func write(_ feed: Data, delivery: Delivery?) throws -> FeedWriteResult {
        writeLock.lock()
        defer { writeLock.unlock() }
        let held = self.delivery()
        if let delivery, !FeedIntake.stores(held: held, arriving: delivery) { return .stale }
        switch FeedDecoder.decode(feed) {
        case .malformed:
            return .malformed
        case .newerVersion:
            newerSeenHere = true
            // Best effort: without the file the notice lasts as long as this process does.
            try? replace(noticeFile, with: Data(String(FeedDecoder.versionOf(feed) ?? Int32(FeedDecoder.version) + 1).utf8))
            return .newerVersion
        case .feed:
            // The same feed is held when the phone's fingerprint is the one it replaces, a feed is
            // there to be the same, and no notice is to go (its going changes what is drawn).
            let same = delivery != nil && held?.fingerprint == delivery?.fingerprint
                && read() != nil && !newerSeenHere && noticeVersion() <= Int32(FeedDecoder.version)
            for step in Self.steps {
                switch step {
                case .REMOVE_DELIVERY: try removeIfThere(deliveryFile)
                case .PLACE_FEED: try replace(feedFile, with: feed)
                case .WRITE_DELIVERY: if let delivery { try? replace(deliveryFile, with: Data(delivery.text.utf8)) }
                }
            }
            // The feed is stored: nothing past this point may fail the write.
            newerSeenHere = false
            try? removeIfThere(noticeFile)
            return same ? .unchanged : .accepted
        }
    }

    /// Forgets everything the store holds, for the DEBUG `-taqwaNoFeed 1` and for tests: the feed,
    /// its delivery, the notice and each one's temporary twin. A watch cleared so is a watch that
    /// has never held a feed (spec 2.6). The handshake's stamp stays: it says when the watch last
    /// asked, not what it holds.
    func clear() throws {
        writeLock.lock()
        defer { writeLock.unlock() }
        for file in [feedFile, deliveryFile, noticeFile] {
            try removeIfThere(file)
            try removeIfThere(Self.partial(of: file))
        }
        newerSeenHere = false
    }

    // MARK: - The handshake's stamp

    /// When the last handshake went out, in epoch seconds, as `stampAsked` wrote it; nil when there
    /// is none to read.
    func lastAskedAt() -> Int64? {
        guard let data = try? Data(contentsOf: askedFile), let text = String(data: data, encoding: .utf8) else { return nil }
        return Int64(text)
    }

    /// Writes the time of a handshake that went out. A stamp that is not written only means the
    /// next chance asks again: one more handshake, never one fewer.
    func stampAsked(_ at: Int64) {
        writeLock.lock()
        defer { writeLock.unlock() }
        try? replace(askedFile, with: Data(String(at).utf8))
    }

    /// Takes back the stamp of a handshake that failed, unless a later one has been stamped since,
    /// so the next chance asks again.
    func unstampAsked(ifAt at: Int64) {
        writeLock.lock()
        defer { writeLock.unlock() }
        if lastAskedAt() == at { try? removeIfThere(askedFile) }
    }

    // MARK: - Files

    private func noticeVersion() -> Int32 {
        guard let data = try? Data(contentsOf: noticeFile), let text = String(data: data, encoding: .utf8) else { return 0 }
        return Int32(text.trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0
    }

    /// Puts `data` in the file's temporary twin, on disk, and renames the twin over the file: the
    /// rename is the only moment the file changes. Whatever the twin held before, from a write cut
    /// short, is replaced; a twin that cannot be written is thrown, and the file is as it was.
    private func replace(_ file: URL, with data: Data) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let partial = Self.partial(of: file)
        do {
            try Self.writeSynced(data, to: partial)
            guard rename(partial.path, file.path) == 0 else { throw Self.posixError() }
        } catch {
            if !Self.isDirectory(partial) { try? FileManager.default.removeItem(at: partial) }
            throw error
        }
    }

    private func removeIfThere(_ file: URL) throws {
        do {
            try FileManager.default.removeItem(at: file)
        } catch CocoaError.fileNoSuchFile {
            // Nothing there: already removed.
        }
    }

    private static func isDirectory(_ url: URL) -> Bool {
        var directory: ObjCBool = false
        return FileManager.default.fileExists(atPath: url.path, isDirectory: &directory) && directory.boolValue
    }

    /// `data` in `url`, created or truncated, and put on disk before it returns.
    private static func writeSynced(_ data: Data, to url: URL) throws {
        let descriptor = open(url.path, O_WRONLY | O_CREAT | O_TRUNC, 0o644)
        guard descriptor >= 0 else { throw posixError() }
        defer { close(descriptor) }
        try data.withUnsafeBytes { (bytes: UnsafeRawBufferPointer) in
            var offset = 0
            while offset < bytes.count {
                let written = Darwin.write(descriptor, bytes.baseAddress! + offset, bytes.count - offset)
                if written < 0 {
                    if errno == EINTR { continue }
                    throw posixError()
                }
                offset += written
            }
        }
        guard fsync(descriptor) == 0 else { throw posixError() }
    }

    private static func posixError() -> Error {
        POSIXError(POSIXErrorCode(rawValue: errno) ?? .EIO)
    }
}
