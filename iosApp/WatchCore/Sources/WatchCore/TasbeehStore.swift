import Foundation

/// Where the watch keeps its counts (plan 4 Task 8): one `tasbeeh.json` in `directory`, each phrase's
/// count and round by its id and the phrase chosen, written whole and atomically on each save. The
/// directory is the app's own Application Support's `Tasbeeh` (`app()`), not the app group's: the
/// complications never read a count. It is marked excluded from backup when it is made, the watch's
/// half of the privacy policy's Backups paragraph (Ruling 39), as `FeedStore`'s is.
///
/// A file that does not read is a fresh start, and a count read back is never below 0 nor a round
/// below 1, as the Galaxy's `TasbeehCodec` reads its own. The counter holds a count to its phrase's
/// target. It is used from the main thread; a lock keeps it whole if it ever is not.
final class TasbeehStore {
    let directory: URL
    var file: URL { directory.appendingPathComponent("tasbeeh.json", isDirectory: false) }

    private let lock = NSLock()
    private var contents: Contents

    /// What the file holds.
    private struct Contents: Codable {
        var selected: String?
        var states: [String: TasbeehState] = [:]
    }

    /// The store on [directory], made when missing and marked excluded from backup either way.
    init(directory: URL) {
        self.directory = directory
        Self.make(directory)
        let url = directory.appendingPathComponent("tasbeeh.json", isDirectory: false)
        contents = (try? Data(contentsOf: url)).flatMap { try? JSONDecoder().decode(Contents.self, from: $0) } ?? Contents()
    }

    /// `<Application Support>/Tasbeeh`, the app's own.
    static var appDirectory: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Tasbeeh", isDirectory: true)
    }

    /// The app's store.
    static func app() -> TasbeehStore { TasbeehStore(directory: appDirectory) }

    /// The phrase [id]'s count and round: 0 of round 1 until it has one.
    func state(for id: String) -> TasbeehState {
        lock.lock()
        defer { lock.unlock() }
        guard let kept = contents.states[id] else { return TasbeehState(presetId: id, count: 0, round: 1) }
        return TasbeehState(presetId: id, count: max(kept.count, 0), round: max(kept.round, 1))
    }

    /// Keeps [state] under its phrase's id, written now.
    func save(_ state: TasbeehState) {
        lock.lock()
        defer { lock.unlock() }
        contents.states[state.presetId] = state
        write()
    }

    /// The phrase chosen: After prayer until one is.
    var selectedId: String {
        get {
            lock.lock()
            defer { lock.unlock() }
            return contents.selected ?? TasbeehPresets.defaultId
        }
        set {
            lock.lock()
            defer { lock.unlock() }
            contents.selected = newValue
            write()
        }
    }

    /// The phrase chosen, found in [list] (`TasbeehPresets.list`); when the phone no longer sends
    /// it, After prayer, which then becomes the choice.
    func selected(in list: [TasbeehPreset]) -> TasbeehPreset {
        let wanted = selectedId
        if let found = list.first(where: { $0.id == wanted }) { return found }
        if wanted != TasbeehPresets.defaultId { selectedId = TasbeehPresets.defaultId }
        return list.first { $0.id == TasbeehPresets.defaultId } ?? TasbeehPresets.default
    }

    /// Forgets every count and the choice (the DEBUG `-taqwaFresh 1`, plan 4 erratum p4 #9).
    func clear() {
        lock.lock()
        defer { lock.unlock() }
        contents = Contents()
        try? FileManager.default.removeItem(at: file)
    }

    /// The whole file, written to a file beside it and renamed over it.
    private func write() {
        guard let data = try? JSONEncoder().encode(contents) else { return }
        if !FileManager.default.fileExists(atPath: directory.path) { Self.make(directory) }
        try? data.write(to: file, options: .atomic)
    }

    /// [directory], made when missing, and marked excluded from backup.
    private static func make(_ directory: URL) {
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var marked = directory
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? marked.setResourceValues(values)
    }
}
