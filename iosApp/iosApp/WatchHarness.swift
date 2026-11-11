#if DEBUG
import Foundation
import WatchConnectivity
import shared

/// Debug-only probe of WatchConnectivity's size limits (plan 4 Task 5B, plan 2 Task 8's ship
/// gate): the largest payload `updateApplicationContext` and `transferUserInfo` take on this pair,
/// measured against `FEED_BUDGET_BYTES + ENVELOPE_BYTES`. `#if DEBUG`, with its rules in
/// `WatchProbe.swift`, so none of it is in a shipping build. It rides the `taqwa` URL scheme beside
/// `RecitationHarness`, by either of the two ways `simctl` hands the app a URL. On iOS 27 only the
/// launch URLs serve a script: `simctl openurl` waits behind "Open in Taqwa?", which no script
/// answers, while a launch hands each URL in `TAQWA_HARNESS_URLS` here as `onOpenURL` does
/// (`RecitationHarness.runLaunchURLs`). `--terminate-running-process` makes it a new process: a
/// paired watch's handshake can start the phone app in the background first, without the variable.
///
/// ```
/// # iOS 27: a fresh launch with the URL (via=userInfo and fill=zero go the same way)
/// SIMCTL_CHILD_TAQWA_HARNESS_URLS='taqwa://watch/probe?via=context&from=60000&to=300000&step=4096&fill=random' \
///   xcrun simctl launch --terminate-running-process <phone> world.taqwa.ios
/// # openurl, where nothing asks "Open in Taqwa?" first
/// xcrun simctl openurl <phone> "taqwa://watch/probe?via=context&from=60000&to=300000&step=4096&fill=random"
/// xcrun simctl openurl <phone> "taqwa://watch/probe?via=userInfo&from=60000&to=300000&step=4096&fill=random"
/// xcrun simctl openurl <phone> "taqwa://watch/probe?via=context&from=60000&to=300000&step=4096&fill=zero"
/// xcrun simctl spawn <phone> log show --last 10m --predicate 'eventMessage CONTAINS "TaqwaWatch probe"'
/// ```
///
/// It sends `[probe: <n bytes>]` on `WCSession.default` for each size `WatchProbeSearch` gives, one
/// at a time, and logs each answer (`TaqwaWatch probe <via> <n> accepted|refused`). The bytes are
/// random, no compressor shrinks them, whether or not the URL says `fill=random`: WatchConnectivity
/// checks a payload's size after compressing it, so only those meet its limit (`WatchProbeFill`).
/// The plan's zeros (`Data(count: n)`) are sent only by name, `fill=zero`, and measure nothing.
/// - **context:** `updateApplicationContext` throws `WCError.payloadTooLarge` at once, so each
///   size is answered in the call;
/// - **userInfo:** `transferUserInfo` never throws. Its answer comes later, in the session's
///   `session(_:didFinish:error:)`, which `TaqwaWatchSession` (the phone's only delegate) hands to
///   `transferFinished`: `payloadTooLarge` is a refusal and no error an acceptance (the watch app,
///   when it is handed the payload, logs `TaqwaWatch userInfo <bytes>` and drops it). The next
///   size goes only once the last is answered; with no answer in 30 s it logs
///   `TaqwaWatch probe userInfo no-answer <n>` and stops. At the end it cancels every outstanding
///   transfer that holds the probe's key.
///
/// Any other error stops the probe where it stands. Then it logs
/// `TaqwaWatch probe <via> <fill> <largest accepted> <first refused>` (`incomplete` after them when
/// it stopped early, `unmeasured` after a zero run: `WatchProbeSearch.line`), and puts the real
/// context back with `WatchFeedBridge.watchStateChanged()` (WATCH_CHANGED), which forgets the
/// phone's memory and sends the feed again: an ordinary ask would answer UNCHANGED, because the
/// iPhone remembers SENT, and leave a probe's context in place.
/// The watch takes no key of a context but the feed's, so a probe context stores nothing there.
final class WatchHarness {

	static let shared = WatchHarness()

	/// The probe's one key, none of the feed's.
	static let probeKey = "probe"

	/// How long a `transferUserInfo` is waited for before the probe stops.
	static let answerSeconds: TimeInterval = 30

	/// Every field below is read and written on this queue alone: `handle` is called on the main
	/// thread, `transferFinished` on WatchConnectivity's delegate queue.
	private let queue = DispatchQueue(label: "world.taqwa.ios.watch-harness")
	private var running: WatchProbeVia?
	private var fill = WatchProbeFill.random
	private var search = WatchProbeSearch(from: 1, to: 2, step: 1)
	/// The userInfo size sent and not yet answered, and the wait that gives up on it.
	private var waiting: (size: Int, deadline: DispatchWorkItem)?

	/// True when the URL is the harness's (`taqwa://watch/…`), so no other route sees it.
	@discardableResult
	func handle(_ url: URL) -> Bool {
		guard url.scheme == "taqwa", url.host == "watch" else { return false }
		guard let request = WatchProbeRequest(url) else {
			log("TaqwaWatch probe not understood: \(url.absoluteString)")
			return true
		}
		queue.async { self.begin(request) }
		return true
	}

	/// `session(_:didFinish:error:)`'s answer for a transfer. Only a probe's is the harness's.
	func transferFinished(_ transfer: WCSessionUserInfoTransfer, error: Error?) {
		guard let probe = transfer.userInfo[Self.probeKey] as? Data else { return }
		let size = probe.count
		queue.async { self.answered(size, error: error) }
	}

	// MARK: - The probe, on `queue`

	private func begin(_ request: WatchProbeRequest) {
		if let running {
			log("TaqwaWatch probe \(request.via.rawValue) refused: a \(running.rawValue) probe is running")
			return
		}
		guard WCSession.isSupported() else {
			log("TaqwaWatch probe \(request.via.rawValue) not ready: WatchConnectivity is not supported")
			return
		}
		let session = WCSession.default
		let activated = session.activationState == .activated
		guard activated, session.isPaired, session.isWatchAppInstalled else {
			log("TaqwaWatch probe \(request.via.rawValue) not ready: activated=\(activated) paired=\(session.isPaired) installed=\(session.isWatchAppInstalled)")
			return
		}
		running = request.via
		fill = request.fill
		search = WatchProbeSearch(from: request.from, to: request.to, step: request.step)
		log("TaqwaWatch probe \(request.via.rawValue) from=\(request.from) to=\(request.to) step=\(request.step) fill=\(request.fill.rawValue) reachable=\(session.isReachable)")
		advance()
	}

	/// Sends the search's next size, or ends the probe once there is none.
	private func advance() {
		guard let via = running else { return }
		guard let size = search.next else { return finish() }
		let payload: [String: Any] = [Self.probeKey: fill.data(count: size)]
		switch via {
		case .context:
			do {
				try WCSession.default.updateApplicationContext(payload)
				answered(size, error: nil)
			} catch {
				answered(size, error: error)
			}
		case .userInfo:
			WCSession.default.transferUserInfo(payload)
			let deadline = DispatchWorkItem { [weak self] in self?.unanswered(size) }
			waiting = (size, deadline)
			log("TaqwaWatch probe userInfo \(size) sent")
			queue.asyncAfter(deadline: .now() + Self.answerSeconds, execute: deadline)
		}
	}

	private func answered(_ size: Int, error: Error?) {
		guard let via = running else {
			log("TaqwaWatch probe \(size) answered after its probe ended: \(describe(error))")
			return
		}
		if via == .userInfo {
			guard let waiting, waiting.size == size else {
				log("TaqwaWatch probe userInfo \(size) answered out of turn, ignored: \(describe(error))")
				return
			}
			waiting.deadline.cancel()
			self.waiting = nil
		}
		if error == nil {
			search.record(size, accepted: true)
			log("TaqwaWatch probe \(via.rawValue) \(size) accepted")
		} else if (error as? WCError)?.code == .payloadTooLarge {
			search.record(size, accepted: false)
			log("TaqwaWatch probe \(via.rawValue) \(size) refused")
		} else {
			log("TaqwaWatch probe \(via.rawValue) \(size) failed: \(describe(error))")
			return finish()
		}
		advance()
	}

	private func unanswered(_ size: Int) {
		guard running == .userInfo, waiting?.size == size else { return }
		waiting = nil
		log("TaqwaWatch probe userInfo no-answer \(size)")
		finish()
	}

	private func finish() {
		guard let via = running else { return }
		log(search.line(via, fill: fill))
		if via == .userInfo {
			let left = WCSession.default.outstandingUserInfoTransfers.filter { $0.userInfo[Self.probeKey] != nil }
			left.forEach { $0.cancel() }
			log("TaqwaWatch probe userInfo cancelled \(left.count) outstanding")
		}
		running = nil
		WatchFeedBridge.shared.watchStateChanged()
		log("TaqwaWatch probe restore")
	}

	private func describe(_ error: Error?) -> String {
		guard let error else { return "no error" }
		let ns = error as NSError
		return "\(ns.domain) \(ns.code)"
	}

	private func log(_ line: String) {
		NSLog("%@", line)
	}
}
#endif
