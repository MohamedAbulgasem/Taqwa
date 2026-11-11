import ClockKit
import Foundation
import WatchConnectivity
import shared

/// Settings' "Add to Apple Watch" (plan 4 Task 10, spec 4.5): `WatchFaceShareBridge`'s hook over
/// ClockKit's `CLKWatchFaceLibrary` and `WCSession.default`. The shared code decides when the row
/// shows and what a failure says (`WatchFaceOffer.ios.kt`, `WatchRows.kt`); this answers in
/// Booleans only, so nothing of the face, its file or its error crosses into Kotlin.
///
/// The face is `Taqwa.watchface`, copied into the app's bundle by iosApp's "Watch face" phase
/// (`tools/add-watch-targets.rb`) from `iosApp/iosApp/WatchFace/` when that file exists. It is made
/// on a real Apple Watch (that folder's README), so until then the row is never offered.
///
/// It never activates the session: `TaqwaWatchSession` does, at launch, and `isPaired` is valid
/// only once it has. Each answer is logged with the prefix `TaqwaWatch face` as
/// `NSLog("%@", line)`, with no value ever in NSLog's format string, and nothing logged names the
/// place or the person.
final class WatchFaceShare: NSObject, WatchFaceShareHook {

	static let shared = WatchFaceShare()

	/// The bundled face, or nil while none is bundled.
	private var faceURL: URL? {
		Bundle.main.url(forResource: "Taqwa", withExtension: "watchface")
	}

	/// The session is activated and a watch is paired, and the face is bundled. A paired watch
	/// without Taqwa's watch app still counts: the system's add-face sheet offers the app the face's
	/// complications need.
	func isAvailable() -> Bool {
		guard WCSession.isSupported() else { return false }
		let session = WCSession.default
		let activated = session.activationState == .activated
		let paired = activated && session.isPaired
		let bundled = faceURL != nil
		let available = paired && bundled
		NSLog("%@", "TaqwaWatch face offered=\(available) activated=\(activated) paired=\(paired) bundled=\(bundled)")
		return available
	}

	/// The system's own add-face sheet. ClockKit's header promises its completion "will always be
	/// called on the main thread", which is where the row's state is set; the hop below holds that
	/// even if it ever did not. The library is kept alive by its own completion until it answers.
	func add(done: @escaping (KotlinBoolean) -> Void) {
		let answer: (Bool) -> Void = { added in
			if Thread.isMainThread {
				done(KotlinBoolean(bool: added))
			} else {
				DispatchQueue.main.async { done(KotlinBoolean(bool: added)) }
			}
		}
		guard let url = faceURL else {
			NSLog("%@", "TaqwaWatch face not bundled, nothing to add")
			answer(false)
			return
		}
		let library = CLKWatchFaceLibrary()
		library.addWatchFace(at: url) { error in
			withExtendedLifetime(library) {
				if let error = error as NSError? {
					NSLog("%@", "TaqwaWatch face refused: \(Self.name(of: error)) code=\(error.code) domain=\(error.domain)")
					answer(false)
				} else {
					NSLog("%@", "TaqwaWatch face added")
					answer(true)
				}
			}
		}
	}

	/// ClockKit's own name for an error of its domain, so a log reads `invalidFile` and not `2`.
	private static func name(of error: NSError) -> String {
		guard error.domain == CLKWatchFaceLibrary.ErrorDomain,
		      let code = CLKWatchFaceLibrary.ErrorCode(rawValue: error.code) else { return "other" }
		switch code {
		case .notFileURL: return "notFileURL"
		case .invalidFile: return "invalidFile"
		case .permissionDenied: return "permissionDenied"
		case .faceNotAvailable: return "faceNotAvailable"
		case .noURL: return "noURL"
		@unknown default: return "unknown"
		}
	}
}
