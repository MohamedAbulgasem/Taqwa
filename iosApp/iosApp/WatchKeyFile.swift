import Foundation

/// Amendment A3's watch key: a random `UUID().uuidString` kept in a file `taqwa-watch-key` inside
/// WatchConnectivity's watch directory (`WCSession.watchDirectoryURL`), which names one watch and
/// one install of its app. `TaqwaWatchSession.watchKey()` finds the directory and logs the lines
/// given here; what is read, written and marked is decided here, so `swift test` holds it on the Mac
/// (the WatchCore package's `PhoneHarness` target links this file, plan 4 Task 11).
///
/// The file itself is marked `isExcludedFromBackup` whenever it is read or written (Ruling 69
/// item 3): after each write, since an atomic write puts a new file in place, and on each read, so
/// a key an earlier build wrote unmarked is marked too. The privacy text's Backups sentence then
/// no longer rests on `recitationFilesDirectory()` marking all of Application Support as a side
/// effect. A mark that fails is logged and the key still answered.
enum WatchKeyFile {

	static let name = "taqwa-watch-key"

	/// The key, kept when it is, and the log lines that say what was done.
	struct Answer: Equatable {
		let key: String
		let log: [String]
	}

	/// The kept key, or else a new one from `make`, written atomically. A key that cannot be written
	/// is still answered: it differs at the next read, so the phone sends once more and never takes a
	/// confirmation for another watch's.
	static func key(in directory: URL, make: () -> String = { UUID().uuidString }) -> Answer {
		let file = directory.appendingPathComponent(name, isDirectory: false)
		if let kept = try? String(contentsOf: file, encoding: .utf8), !kept.isEmpty {
			return Answer(key: kept, log: ["TaqwaWatch key kept"] + exclude(file))
		}
		let key = make()
		do {
			try key.write(to: file, atomically: true, encoding: .utf8)
			return Answer(key: key, log: ["TaqwaWatch key new"] + exclude(file))
		} catch {
			return Answer(key: key, log: ["TaqwaWatch key new, not kept: \(error.localizedDescription)"])
		}
	}

	/// Marks the file excluded from backup; a line to log only when that fails.
	private static func exclude(_ file: URL) -> [String] {
		var url = file
		var values = URLResourceValues()
		values.isExcludedFromBackup = true
		do {
			try url.setResourceValues(values)
			return []
		} catch {
			return ["TaqwaWatch key not marked for backup: \(error.localizedDescription)"]
		}
	}
}
