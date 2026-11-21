import SwiftUI
import BackgroundTasks
import UIKit
import UserNotifications
import WatchConnectivity
import WidgetKit
import shared

private let refreshTaskId = "world.taqwa.ios.refresh"

/// The two things SwiftUI's `App` cannot express: the notification-centre delegate and
/// `handleEventsForBackgroundURLSession`.
///
/// iOS relaunches the app when a background download finishes and delivers the news here — to a
/// process that has no URL sessions at all, so nothing would arrive until one with the right
/// identifier is recreated. `wakeRecitationDownloads` does both halves: it recreates the sessions
/// and holds the completion handler, which Foundation requires to be called once every delegate
/// callback has been delivered.
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

	/// The delegate has to be set before the app finishes launching, or a notification that arrives
	/// in the meantime is presented by the default rules — which is to say not at all.
	func application(
		_ application: UIApplication,
		didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
	) -> Bool {
		UNUserNotificationCenter.current().delegate = self
		return true
	}

	/// Without this, iOS shows nothing at all while the app is in the foreground: no banner and, for
	/// a prayer, no adhan. That is exactly the moment a user is most likely to be looking at Taqwa,
	/// so a notification is presented in full whether or not the app happens to be open.
	func userNotificationCenter(
		_ center: UNUserNotificationCenter,
		willPresent notification: UNNotification,
		withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
	) {
		completionHandler([.banner, .list, .sound])
	}

	/// A tap on a notification. An adhkar reminder's opens its set's session (adhkar spec §5);
	/// Kotlin reads the identifier and ignores every other one, so a prayer's tap still just brings
	/// the app forward. Recorded in `LaunchRequests`, not acted on here: on a cold launch this
	/// arrives before `ContentView`'s composition exists to navigate, as a widget's link does.
	func userNotificationCenter(
		_ center: UNUserNotificationCenter,
		didReceive response: UNNotificationResponse,
		withCompletionHandler completionHandler: @escaping () -> Void
	) {
		if response.actionIdentifier == UNNotificationDefaultActionIdentifier {
			LaunchRequests_iosKt.openAdhkarFromNotification(identifier: response.notification.request.identifier)
		}
		completionHandler()
	}

	func application(
		_ application: UIApplication,
		handleEventsForBackgroundURLSession identifier: String,
		completionHandler: @escaping () -> Void
	) {
		// The identifier goes with it: this is called once per background session, each time with a
		// handler belonging to that session alone, and there are two of them (Wi-Fi and cellular).
		SurahDownloader_iosKt.wakeRecitationDownloads(identifier: identifier, completion: completionHandler)
	}
}

@main
struct iOSApp: App {

	@UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

	init() {
		BGTaskScheduler.shared.register(forTaskWithIdentifier: refreshTaskId, using: nil) { task in
			Self.handleAppRefresh(task: task as! BGAppRefreshTask)
		}
		Self.installWidgetRefreshHook()
		Self.installWatchSession()
		Self.installWatchFaceShare()
		Self.installWidgetPlacementHook()
		Self.scheduleNextRefresh()
	}

	var body: some Scene {
		WindowGroup {
			#if DEBUG
			// Hidden debug route: `xcrun simctl launch booted world.taqwa.ios -taqwaWidgetPreview 1`
			// renders the real widget views full screen against the real App Group mirror. There is
			// no simctl command that places a widget on a simulator home screen, so this is how a
			// widget render gets captured. The whole branch is compiled out of a release build, along
			// with `TaqwaWidgetPreviews.swift` itself: a user default is writable by anyone with the
			// device, and a route nobody should reach is best absent rather than merely unreachable.
			if Self.widgetPreviewRequested {
				TaqwaWidgetPreviewScreen(section: Self.widgetPreviewSection)
			} else {
				root
			}
			#else
			root
			#endif
		}
	}

	/// The app itself. Named because `#if` cannot straddle the two halves of an `if`/`else`, so the
	/// debug route above needs something to fall through to that the release build also uses.
	private var root: some View {
		ContentView()
			.onAppear {
				Self.scheduleNextRefresh()
				Self.reloadWidgets()
				#if DEBUG
				RecitationHarness.runLaunchURLs { Self.openAyah(from: $0) }
				#endif
			}
			.onOpenURL { url in
				#if DEBUG
				if RecitationHarness.handle(url) { return }
				if WatchHarness.shared.handle(url) { return }
				#endif
				Self.openAyah(from: url)
			}
	}

	/// True for `-taqwaWidgetPreview 1` (which lands in `UserDefaults`' argument domain) and for
	/// `SIMCTL_CHILD_TAQWA_WIDGET_PREVIEW=1`, since `simctl launch` swallows some leading-dash
	/// arguments before the app ever sees them.
	static var widgetPreviewRequested: Bool {
		#if DEBUG
		if UserDefaults.standard.bool(forKey: "taqwaWidgetPreview") { return true }
		if ProcessInfo.processInfo.arguments.contains("-taqwaWidgetPreview") { return true }
		return ProcessInfo.processInfo.environment["TAQWA_WIDGET_PREVIEW"] == "1"
		#else
		return false
		#endif
	}

	/// Which widgets the route draws: `all` (the default) or `ayah`, from
	/// `-taqwaWidgetPreviewSection ayah`. The ayah card's Large family is 382 pt tall, so the two
	/// families plus the four prayer ones do not fit one screenshot, and `simctl` cannot scroll.
	///
	/// `#if DEBUG` like its caller: the `UserDefaults.standard` read is the app's only one, and a
	/// release binary that does not make it has one fewer required-reason API to declare.
	#if DEBUG
	static var widgetPreviewSection: String {
		UserDefaults.standard.string(forKey: "taqwaWidgetPreviewSection")
			?? ProcessInfo.processInfo.environment["TAQWA_WIDGET_PREVIEW_SECTION"]
			?? "all"
	}
	#endif

	/// The ayah widget's tap target (design spec §7-8): `taqwa://ayah/<surah>/<ayah>`, set as the
	/// widget's `widgetURL`. Anything else on the `taqwa` scheme is ignored rather than guessed at,
	/// so a future link shape cannot silently open the wrong ayah.
	///
	/// `LaunchRequests` only *records* the request; `App` collects it and pushes the reader (or the
	/// Mushaf page, in Mushaf mode). That indirection is what makes a cold launch work — the
	/// request is set here, before `ContentView`'s composition exists to navigate.
	static func openAyah(from url: URL) {
		guard url.scheme == "taqwa", url.host == "ayah" else { return }
		let parts = url.pathComponents.filter { $0 != "/" }
		guard parts.count == 2, let surah = Int32(parts[0]), let ayah = Int32(parts[1]) else { return }
		// A malformed or stale widget URL (e.g. a surah number outside the Quran's own range)
		// must not reach `openAyahFromWidget`, which trusts its arguments rather than re-validating.
		guard (1...114).contains(surah), ayah >= 1 else { return }
		LaunchRequests_iosKt.openAyahFromWidget(surah: surah, ayah: ayah)
	}

	static func handleAppRefresh(task: BGAppRefreshTask) {
		scheduleNextRefresh()
		task.expirationHandler = { task.setTaskCompleted(success: false) }
		DispatchQueue.global(qos: .background).async {
			let success = BackgroundRefreshBridge.shared.runBackgroundRefresh()
			reloadWidgets()
			task.setTaskCompleted(success: success)
		}
	}

	static func scheduleNextRefresh() {
		let request = BGAppRefreshTaskRequest(identifier: refreshTaskId)
		// Six hours, not a day: the task now also notices a journey (spec §16.5), and iOS decides
		// the actual moment anyway — a shorter earliest date only gives it more chances.
		request.earliestBeginDate = Date(timeIntervalSinceNow: 6 * 60 * 60)
		try? BGTaskScheduler.shared.submit(request)
	}

	/// `refreshWidgets()` is `expect`/`actual` in shared code, but `WidgetCenter` is Swift-only, so
	/// the iOS actual is a hook the app fills in here — the one place that can actually make the
	/// call. The Prayer screen's one-second tick (`PrayerViewModel.refresh()`) calls it only when
	/// the widget snapshot it writes has changed: each time its countdown passes a whole minute or
	/// its ring one of the 120 steps it takes from one prayer to the next, mostly at different
	/// moments (`WidgetMirrorWriter`), so while that screen shows, once to twice a minute with the
	/// prayers two hours apart or more, and more often when they are closer (about 2.7 times a
	/// minute at 70 minutes). Every other caller calls it after its own write whether or not the
	/// value changed — the ayah pool mirror at every app start, settings and language changes, the
	/// background refresh — so the hook still de-duplicates: it reloads only when something a
	/// widget reads has changed (`reloadWidgets()`).
	static func installWidgetRefreshHook() {
		WidgetRefreshBridge.shared.onRefresh = { reloadWidgets() }
	}

	/// The watch feed's sender on the iPhone (plan 2 Task 8): WatchConnectivity is Swift-only, as
	/// WidgetKit is, so the session is the hook the shared code sends through.
	static func installWatchSession() {
		TaqwaWatchSession.shared.start()
	}

	/// Settings' "Add to Apple Watch" (plan 4 Task 10): ClockKit's face library and the session's
	/// `isPaired` are Swift-only too, so the row asks through this hook. It reads the session that
	/// `installWatchSession()` activates, and never activates it itself; that session's delegate
	/// says when to ask again (`WatchFaceShareBridge.sessionChanged()`).
	static func installWatchFaceShare() {
		WatchFaceShareBridge.shared.hook = WatchFaceShare.shared
	}

	/// Settings › Widgets and the Settings root ask which Taqwa widgets are placed; `WidgetCenter` is
	/// the only thing that knows, it is Swift-only, and it answers asynchronously, so the shared code
	/// hands over a completion and this fills it in.
	///
	/// It hands back the kind of every placed widget, or nil when WidgetKit failed, and the shared
	/// side decides which widget each kind is (`WidgetPlacement.ios.kt`, where that is tested): the
	/// Lock Screen widget is a kind of its own and must never read as the prayer widget on the Home
	/// Screen. The `answered` guard is not defensive dressing: `getCurrentConfigurations` is not
	/// contractually single-shot, and the Kotlin side treats a second resume as a programming error.
	/// A failure is "cannot tell", which shows no status and offers nothing, never "placed".
	static func installWidgetPlacementHook() {
		WidgetPlacement_iosKt.iosWidgetPlacementHook = { completion in
			var answered = false
			let answer: ([String]?) -> Void = { kinds in
				guard !answered else { return }
				answered = true
				// A Kotlin function type returns Unit, which reaches Swift as a KotlinUnit that says
				// nothing; it is dropped here by name so the build does not warn about it.
				_ = completion(kinds)
			}
			WidgetCenter.shared.getCurrentConfigurations { result in
				switch result {
				case .success(let widgets):
					answer(widgets.map { $0.kind })
				case .failure:
					answer(nil)
				}
			}
		}
	}

	/// Only ever read or written on the main thread — see `reloadWidgets()`. Covers every mirror a
	/// widget draws from: the prayer snapshot, the background choice *and* the ayah pool. Each was
	/// added because leaving it out made a real change invisible — the background choice used to
	/// be missing, so picking Light or Dark for the widget background (Settings › Widgets now,
	/// Appearance then) did nothing on the home screen until WidgetKit's hourly reload happened to
	/// come round, and the ayah pool used to be missing, so switching translation left the old one
	/// on the ayah card until its timeline ran out a week later. Anything a widget reads out of the
	/// App Group has to be represented here.
	private static var lastMirrorKey: String?

	/// `reloadWidgets()` is reached from a Kotlin-invoked closure (`PrayerViewModel.refresh()` runs
	/// on whatever dispatcher it is collected on) and from the background-task queue in
	/// `handleAppRefresh`, so neither the `lastMirrorKey` dedupe nor `WidgetCenter` can assume the
	/// main thread. Everything below therefore runs on it: inline when it already is, and
	/// synchronously otherwise so callers that sequence work after a reload — `handleAppRefresh`
	/// calls `task.setTaskCompleted` next — still see it finished. `sync` cannot deadlock here
	/// because it is never reached from the main thread (that branch runs inline) and the main
	/// thread never blocks on these callers.
	private static func onMain(_ work: () -> Void) {
		if Thread.isMainThread {
			work()
		} else {
			DispatchQueue.main.sync(execute: work)
		}
	}

	static func reloadWidgets() {
		onMain {
			let defaults = UserDefaults(suiteName: taqwaAppGroupId)
			let snapshot = defaults?.string(forKey: taqwaSnapshotKey)
			let background = defaults?.string(forKey: taqwaBackgroundKey)
			// The ayah pool mirror is ~230-310 KB, so it is folded in by hash and length rather than
			// concatenated whole. The key is rebuilt on every call, on the main thread: while the
			// Prayer screen shows, at each change of the snapshot, whose countdown and ring move at
			// different moments (once to twice a minute with the prayers two hours apart or more,
			// more often when they are closer), and once for each other refresh (see
			// `installWidgetRefreshHook`).
			// The length is `utf16.count`, the bridged string's own length, which costs nothing;
			// `count` walked the whole mirror for its graphemes, more than the hash itself.
			// `hashValue` is per-process seeded, which is fine: `lastMirrorKey` never outlives the
			// process either, and a fresh process reloading once more is the harmless direction.
			let ayah = defaults?.string(forKey: AyahPoolMirror.companion.KEY) ?? ""
			let key = (snapshot ?? "") + "|" + (background ?? "")
				+ "|" + String(ayah.hashValue) + "|" + String(ayah.utf16.count)
			guard key != lastMirrorKey else { return }
			lastMirrorKey = key
			WidgetCenter.shared.reloadAllTimelines()
		}
	}
}

/// The iPhone's half of the watch feed (plan 2 Task 8, Ruling 50): `WatchFeedBridge`'s hook over
/// `WCSession.default`. The shared code decides when to send and what (`WatchFeedSync`); this only
/// hands the feed to WatchConnectivity, answers the watch's handshake and says when a watch may be
/// back. Every key it uses is `WatchFeedBridge`'s, `:watchcore`'s contract, so Swift names none of
/// its own.
///
/// Kotlin's `println` does not reach the unified log on iOS, so each decision is logged here, with
/// the prefix `TaqwaWatch`: activation, each `isReady()` with its three facts, the watch key's
/// read, each send with the feed's byte count and its delivery's number and fingerprint, and each
/// call into the bridge. A delivery is logged as `WatchFeedBridge.logged(delivery:)` gives it,
/// never with its origin (the install's one random number), and nothing logged names the place,
/// the bearing or the person (Ruling 52 item 4). Every line is `NSLog("%@", line)`: no value is
/// ever interpolated into NSLog's format string.
///
/// ```
/// xcrun simctl spawn <udid> log show --last 5m --predicate 'eventMessage CONTAINS "TaqwaWatch"'
/// ```
final class TaqwaWatchSession: NSObject, WCSessionDelegate, WatchSessionHook {

	static let shared = TaqwaWatchSession()

	/// Guards `watchKey()`'s read and write: two first reads at once (the sync's worker and a
	/// handshake on WatchConnectivity's queue) would otherwise each write a key of their own.
	///
	/// The key is Amendment A3's file (`WatchKeyFile`), inside `watchDirectoryURL`. Apple: a
	/// directory "specific to the currently paired and active Apple Watch", whose URL "changes when
	/// the active Apple Watch changes", and "If the user uninstalls your app or unpairs their Apple
	/// Watch, iOS deletes this directory and its contents."
	private let keyLock = NSLock()

	/// Activated whenever WatchConnectivity is supported: `isPaired` and `isWatchAppInstalled` are
	/// only valid once it is, so they are checked before each send instead. The hook is installed
	/// before `activate()`, so the activation's ask can never find it missing; until activation
	/// completes `isReady()` is false anyway.
	func start() {
		guard WCSession.isSupported() else {
			NSLog("%@", "TaqwaWatch not supported: no session, no watch")
			return
		}
		WatchFeedBridge.shared.hook = self
		let session = WCSession.default
		session.delegate = self
		session.activate()
		NSLog("%@", "TaqwaWatch activate() asked")
	}

	func isReady() -> Bool {
		let session = WCSession.default
		let activated = session.activationState == .activated
		let paired = session.isPaired
		let installed = session.isWatchAppInstalled
		let ready = activated && paired && installed
		NSLog("%@", "TaqwaWatch isReady()=\(ready) activated=\(activated) paired=\(paired) installed=\(installed)")
		return ready
	}

	/// The watch directory is read only once the session is activated (Ruling 52 item 5). Apple:
	/// while `activationState` is `notActivated` its URL "is undefined and should not be used",
	/// and "If there is no paired watch, the value of this property is nil". The SDK header adds:
	/// "If the watch app is not installed for the selected Watch the value will be nil", and the
	/// directory "will be deleted upon next launch if the watch app is uninstalled for the selected
	/// Watch, or that Watch is unpaired".
	func watchKey() -> String? {
		keyLock.lock()
		defer { keyLock.unlock() }
		let session = WCSession.default
		guard session.activationState == .activated, let directory = session.watchDirectoryURL else { return nil }
		// Read or written, and the file itself marked excluded from backup (Ruling 69 item 3).
		let answer = WatchKeyFile.key(in: directory)
		answer.log.forEach { NSLog("%@", $0) }
		return answer.key
	}

	/// Checked again right before the call: a watch switch can make the session inactive between
	/// `isReady()` and here, and `updateApplicationContext` on an inactive session is a programmer
	/// error Swift may not be able to catch. So nothing is handed over then, and the answer is
	/// `sendFailed`.
	func send(feed: Data, delivery: String) -> Int32 {
		let bridge = WatchFeedBridge.shared
		let session = WCSession.default
		guard session.activationState == .activated, session.isPaired, session.isWatchAppInstalled else {
			NSLog("%@", "TaqwaWatch send refused, the session is not ready: bytes=\(feed.count) delivery=\(bridge.logged(delivery: delivery))")
			return bridge.sendFailed
		}
		do {
			try session.updateApplicationContext([bridge.contextFeedKey: feed, bridge.contextDeliveryKey: delivery])
			NSLog("%@", "TaqwaWatch send queued: bytes=\(feed.count) delivery=\(bridge.logged(delivery: delivery))")
			return bridge.sendQueued
		} catch let error as WCError where error.code == .payloadTooLarge {
			NSLog("%@", "TaqwaWatch send too large: bytes=\(feed.count) delivery=\(bridge.logged(delivery: delivery))")
			return bridge.sendTooLarge
		} catch {
			NSLog("%@", "TaqwaWatch send failed: bytes=\(feed.count) delivery=\(bridge.logged(delivery: delivery)) error=\(error.localizedDescription)")
			return bridge.sendFailed
		}
	}

	// MARK: WCSessionDelegate
	//
	// Each of the four callbacks that can change "Add to Apple Watch" (the session's activation
	// state, or `isPaired`) also tells Settings, which asks the row again if it is in front
	// (`WatchFaceShareBridge.sessionChanged()`, the owner's report of 9 November). It never waits.

	/// Every launch ends here, background refreshes included, so this is the damped ask
	/// (`watchActivated()`), never `watchStateChanged()`: an undamped one would hand the whole feed
	/// over again at every launch and reload every widget timeline on the watch.
	func session(
		_ session: WCSession,
		activationDidCompleteWith activationState: WCSessionActivationState,
		error: Error?
	) {
		let activated = activationState == .activated
		NSLog("%@", "TaqwaWatch activated=\(activated) state=\(activationState.rawValue) error=\(error?.localizedDescription ?? "none"), calling watchActivated()")
		WatchFeedBridge.shared.watchActivated()
		WatchFaceShareBridge.shared.sessionChanged()
	}

	func sessionDidBecomeInactive(_ session: WCSession) {
		NSLog("%@", "TaqwaWatch inactive")
		WatchFaceShareBridge.shared.sessionChanged()
	}

	/// Another watch was switched to: Apple's pattern is to activate the session again for it.
	func sessionDidDeactivate(_ session: WCSession) {
		NSLog("%@", "TaqwaWatch deactivated, activate() again")
		WatchFaceShareBridge.shared.sessionChanged()
		WCSession.default.activate()
	}

	/// The watch app installed or removed, another watch paired, or the watch directory changed:
	/// the confirmation is forgotten first (`watchStateChanged()`), then the feed goes.
	func sessionWatchStateDidChange(_ session: WCSession) {
		NSLog("%@", "TaqwaWatch watch state changed: paired=\(session.isPaired) installed=\(session.isWatchAppInstalled), calling watchStateChanged()")
		WatchFeedBridge.shared.watchStateChanged()
		WatchFaceShareBridge.shared.sessionChanged()
	}

	/// The watch's handshake, `[refresh: <its echo>]`. The reply is whether the watch has, or is
	/// being given, the current feed. The bridge puts the ask on the sync's queue before it returns,
	/// so the handshakes and watch-state changes are served in the order WatchConnectivity delivers
	/// them (Ruling 53 item 4a), and it holds UIKit background time until it replies, so an app this
	/// message woke in the background answers before it is suspended (item 4b). The reply comes
	/// within `WatchFeedSync.CALLER_BOUND`, most often later, from the bridge's own coroutine;
	/// nothing here waits for it, so WatchConnectivity's delegate queue never waits for the sync
	/// (Ruling 52 item 6). The bridge calls the reply exactly once.
	func session(
		_ session: WCSession,
		didReceiveMessage message: [String: Any],
		replyHandler: @escaping ([String: Any]) -> Void
	) {
		let bridge = WatchFeedBridge.shared
		guard let have = message[bridge.refreshMessageKey] else {
			NSLog("%@", "TaqwaWatch message without a handshake, answered empty")
			replyHandler([:])
			return
		}
		let echo = have as? String ?? ""
		NSLog("%@", "TaqwaWatch handshake have=\(bridge.logged(delivery: echo)), calling refreshRequested(have:reply:)")
		bridge.refreshRequested(have: echo) { queued in
			NSLog("%@", "TaqwaWatch handshake answered queued=\(queued.boolValue)")
			replyHandler([bridge.refreshReplyKey: queued.boolValue])
		}
	}

	func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
		let bridge = WatchFeedBridge.shared
		guard let have = message[bridge.refreshMessageKey] else { return }
		let echo = have as? String ?? ""
		NSLog("%@", "TaqwaWatch handshake have=\(bridge.logged(delivery: echo)) without a reply handler, calling refreshRequested(have:reply:)")
		bridge.refreshRequested(have: echo) { queued in
			NSLog("%@", "TaqwaWatch handshake without a reply handler served: queued=\(queued.boolValue)")
		}
	}

	#if DEBUG
	/// `transferUserInfo`'s answer, which only the session's delegate hears, and this is the phone's
	/// only one: handed to the size probe (`WatchHarness`, plan 4 Task 5B). The app itself never
	/// transfers user info, so a release build does not implement it.
	func session(_ session: WCSession, didFinish userInfoTransfer: WCSessionUserInfoTransfer, error: Error?) {
		WatchHarness.shared.transferFinished(userInfoTransfer, error: error)
	}
	#endif
}

#if DEBUG
import MediaPlayer

/// Debug-only remote control for the recitation player, the iOS twin of `androidApp`'s
/// `RecitationHarnessReceiver`: slice 3a task 3 has to be verified on a simulator before task 4
/// gives recitation any UI. `#if DEBUG`, so none of it is in a shipping build.
///
/// It rides the `taqwa` URL scheme the widget already registers, by either of the two ways `simctl`
/// hands the app a URL. On iOS 27 only the launch URLs serve a script: `simctl openurl` waits behind
/// "Open in Taqwa?", which no script answers, while a launch hands each URL in `TAQWA_HARNESS_URLS`
/// here as `onOpenURL` does (`runLaunchURLs`, below). `--terminate-running-process` makes it a new
/// process: one already running has read its launch URLs, or had none:
///
/// ```
/// # iOS 27: a fresh launch with the URLs, space-separated, a second and a half apart
/// SIMCTL_CHILD_TAQWA_HARNESS_URLS='taqwa://recite/load?surah=36&ayah=1&reciter=ar.alafasy taqwa://recite/next' \
///   xcrun simctl launch --terminate-running-process <udid> world.taqwa.ios
/// # openurl, where nothing asks "Open in Taqwa?" first
/// xcrun simctl openurl <udid> "taqwa://recite/load?surah=36&ayah=1&reciter=ar.alafasy"
/// xcrun simctl openurl <udid> "taqwa://recite/next"
/// ```
///
/// Commands: `load` (with `translation=en.sahih` to read that translation aloud after each ayah),
/// `play`, `pause`, `toggle`, `next`, `prev`, `seek?ayah=n`, `stop`, `state`, `nowplaying` (dumps
/// `MPNowPlayingInfoCenter`), `reconcile`, `voices` (what this phone's voices answer for every
/// bundled language), `speech?translation=<id>` (read-aloud changed while a surah plays; `none`
/// turns it off). Everything is `NSLog`ged with the prefix `TaqwaHarness`, which is how the ayah
/// boundary, the gap and the translation are actually measured:
///
/// ```
/// xcrun simctl spawn <udid> log show --last 2m --predicate 'eventMessage CONTAINS "TaqwaHarness"'
/// ```
enum RecitationHarness {

	private static var watcher: Timer?
	private static var last: String = ""
	private static var launchURLsRun = false

	/// The URLs in `TAQWA_HARNESS_URLS` (`SIMCTL_CHILD_TAQWA_HARNESS_URLS="url url …" xcrun simctl launch …`),
	/// each handled as `onOpenURL` would, two seconds after launch and a second and a half apart: recitation's
	/// commands, then the watch probe's (`taqwa://watch/…`, `WatchHarness`), then the ayah route. iOS 27 asks
	/// "Open in Taqwa?" before `simctl openurl` reaches the app, which no script can answer, so the store and
	/// README captures, and the probe on the paired simulators, relaunch with their URLs instead. Once per
	/// process: `onAppear` can run again.
	static func runLaunchURLs(openAyah: @escaping (URL) -> Void) {
		guard !launchURLsRun, let list = ProcessInfo.processInfo.environment["TAQWA_HARNESS_URLS"] else { return }
		launchURLsRun = true
		let urls = list.split(separator: " ").compactMap { URL(string: String($0)) }
		for (i, url) in urls.enumerated() {
			DispatchQueue.main.asyncAfter(deadline: .now() + 2 + 1.5 * Double(i)) {
				NSLog("TaqwaHarness launch url %@", url.absoluteString)
				if !handle(url) && !WatchHarness.shared.handle(url) { openAyah(url) }
			}
		}
	}

	/// True if this URL was a harness command, so the real `taqwa://ayah/...` route never sees it.
	static func handle(_ url: URL) -> Bool {
		guard url.scheme == "taqwa", url.host == "recite" else { return false }
		let command = url.pathComponents.filter { $0 != "/" }.first ?? ""
		let query = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems ?? []
		func value(_ name: String) -> String? { query.first { $0.name == name }?.value }
		func number(_ name: String, _ fallback: Int32) -> Int32 {
			value(name).flatMap { Int32($0) } ?? fallback
		}
		let player = AppContainerKt.appContainer.recitationPlayer
		let reciterId = value("reciter") ?? "ar.alafasy"
		let surah = number("surah", 1)
		let ayah = number("ayah", 1)
		NSLog("TaqwaHarness cmd=\(command) reciter=\(reciterId) surah=\(surah) ayah=\(ayah)")
		switch command {
		case "load":
			watch()
			let playWith = reciter(reciterId, gapMs: number("gap", -1))
			let text = NowPlayingText(
				title: "Surah \(surah)",
				subtitle: "Mishary Rashid Alafasy",
				previousAyahLabel: "",
				nextAyahLabel: ""
			)
			// Read-aloud (spec §5.6): the translation read after each ayah, if the phone has a
			// voice for its language.
			if let id = value("translation") {
				spoken(id, surah: surah) { speech in
					NSLog("TaqwaHarness speech \(speech?.translationId ?? "none") \(speech?.texts.count ?? 0)")
					player.load(reciter: playWith, surah: surah, startAyah: ayah, text: text, speech: speech) { _ in }
				}
			} else {
				player.load(reciter: playWith, surah: surah, startAyah: ayah, text: text, speech: nil) { _ in }
			}
		// Read-aloud live (spec §2): `speech?translation=fr.hamidullah`, or `none` for off.
		case "speech":
			let id = value("translation") ?? "none"
			let surahNow = (player.state.value as? PlaybackState)?.surah?.int32Value ?? surah
			if id == "none" {
				NSLog("TaqwaHarness setSpeech none")
				player.setSpeech(speech: nil)
			} else {
				spoken(id, surah: surahNow) { speech in
					NSLog("TaqwaHarness setSpeech \(speech?.translationId ?? "none") \(speech?.texts.count ?? 0)")
					player.setSpeech(speech: speech)
				}
			}
		// Read-aloud (spec §4): what this phone's voices answer for every bundled language.
		case "voices":
			for language in ["en", "ar", "fr", "tr", "id", "ur", "bn"] {
				AppContainerKt.appContainer.speechVoices.status(language: language) { status, error in
					NSLog("TaqwaHarness voice \(language) \(status.map { "\($0)" } ?? "failed: \(String(describing: error))")")
				}
			}
		case "play": player.play()
		case "pause": player.pause()
		case "toggle": player.toggle()
		case "next": player.next()
		case "prev": player.previous()
		case "seek": player.seekToAyah(n: ayah)
		case "stop": player.stop()
		case "state": NSLog("TaqwaHarness state \(player.state.value as Any)")
		case "nowplaying": logNowPlaying()
		case "reconcile":
			AppContainerKt.appContainer.recitationLibrary.reconcile { _ in
				NSLog("TaqwaHarness reconciled \(reciterId)")
			}
		case "refresh":
			// The background refresh task's body, run on demand after `simctl location set`.
			DispatchQueue.global(qos: .background).async {
				let ok = BackgroundRefreshBridge.shared.runBackgroundRefresh()
				NSLog("TaqwaHarness background refresh ok=\(ok)")
			}
		// The store-screenshot run: `taqwa://recite/screen?name=qibla`, `.../theme?name=dark`,
		// `.../mode?name=mushaf` open a screen, set the theme or the reading mode by name.
		case "screen":
			LaunchRequests.shared.openScreen(name: value("name") ?? "prayer")
		// `taqwa://recite/search?q=%D8%A7%D9%84%D8%B5%D9%84%D8%A7%D8%A9` types a Quran search
		// (open the quran screen first): the results are something a simulator run has to see.
		case "search":
			LaunchRequests.shared.search(query: value("q") ?? "")
		// What iOS is actually holding: every pending notification whose id starts with
		// `prefix` (default: all), soonest first. `taqwa://recite/pending?prefix=TAHAJJUD`.
		case "pending":
			let prefix = value("prefix") ?? ""
			UNUserNotificationCenter.current().getPendingNotificationRequests { requests in
				let mine = requests.filter { $0.identifier.hasPrefix(prefix) }
					.sorted { (($0.trigger as? UNCalendarNotificationTrigger)?.nextTriggerDate() ?? .distantFuture) < (($1.trigger as? UNCalendarNotificationTrigger)?.nextTriggerDate() ?? .distantFuture) }
				NSLog("TaqwaHarness pending total=\(requests.count) matching=\(mine.count)")
				for request in mine.prefix(6) {
					let at = (request.trigger as? UNCalendarNotificationTrigger)?.nextTriggerDate()
					NSLog("TaqwaHarness pending \(request.identifier) at=\(String(describing: at)) title=\(request.content.title) body=\(request.content.body) sound=\(String(describing: request.content.sound))")
				}
			}
		// `taqwa://recite/tahajjud?name=on` (or off): the settings toggle, for the screenshot run.
		case "tahajjud":
			AppContainerKt.appContainer.setTahajjud(on: value("name") == "on") { _ in }
		case "theme":
			let mode: ThemeMode = value("name") == "dark" ? .dark : (value("name") == "light" ? .light : .system)
			AppContainerKt.appContainer.settingsRepository.setThemeMode(mode: mode) { _ in }
		case "mode":
			let mode: ReadingMode = value("name") == "mushaf" ? .mushaf : .translation
			AppContainerKt.appContainer.settingsRepository.setReadingMode(mode: mode) { _ in }
		case "translation":
			AppContainerKt.appContainer.settingsRepository.setTranslation(id: value("id") ?? "en.sahih") { _ in }
		default: NSLog("TaqwaHarness unknown command \"\(command)\"")
		}
		return true
	}

	/// The lock screen's own copy of what is playing. Logged rather than looked at, because a
	/// simulator's lock screen cannot be reached from `simctl`.
	static func logNowPlaying() {
		guard let info = MPNowPlayingInfoCenter.default().nowPlayingInfo else {
			NSLog("TaqwaHarness nowPlayingInfo is nil")
			return
		}
		let artwork = info[MPMediaItemPropertyArtwork] as? MPMediaItemArtwork
		NSLog("""
			TaqwaHarness nowPlayingInfo keys=\(info.keys.count) \
			title=\(info[MPMediaItemPropertyTitle] ?? "nil") \
			artist=\(info[MPMediaItemPropertyArtist] ?? "nil") \
			duration=\(info[MPMediaItemPropertyPlaybackDuration] ?? "nil") \
			elapsed=\(info[MPNowPlayingInfoPropertyElapsedPlaybackTime] ?? "nil") \
			rate=\(info[MPNowPlayingInfoPropertyPlaybackRate] ?? "nil") \
			artwork=\(artwork.map { "\($0.bounds.size)" } ?? "nil")
			""")
		let centre = MPRemoteCommandCenter.shared()
		NSLog("""
			TaqwaHarness commands play=\(centre.playCommand.isEnabled) \
			pause=\(centre.pauseCommand.isEnabled) toggle=\(centre.togglePlayPauseCommand.isEnabled) \
			next=\(centre.nextTrackCommand.isEnabled) prev=\(centre.previousTrackCommand.isEnabled) \
			seek=\(centre.changePlaybackPositionCommand.isEnabled) \
			skipFwd=\(centre.skipForwardCommand.isEnabled)
			""")
	}

	/// `StateFlow` cannot be collected from Swift, so the state is polled and logged on change —
	/// which is all the ayah-boundary measurement needs, at four times the player's own tick.
	private static func watch() {
		guard watcher == nil else { return }
		watcher = Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { _ in
			let state = AppContainerKt.appContainer.recitationPlayer.state.value as! PlaybackState
			let line = "ayah=\(state.ayah?.intValue ?? -1)/\(state.ayahCount) "
				+ "playing=\(state.playing) speaking=\(state.speaking) "
				+ "pos=\(state.positionMs) dur=\(state.durationMs) "
				+ "clock=\(state.surahPositionMs)/\(state.surahDurationMs) "
				+ "surah=\(state.surah?.intValue ?? -1)"
			guard line != last else { return }
			last = line
			NSLog("TaqwaHarness state \(line)")
		}
	}

	/// `buildSpokenTranslation` for the harness. Its completion can arrive on whichever thread the
	/// last of its reads finished on, and the player is driven from the main thread only, so the
	/// answer is handed on from the main queue.
	private static func spoken(_ id: String, surah: Int32, then: @escaping (SpokenTranslation?) -> Void) {
		SpokenTranslationKt.buildSpokenTranslation(
			quran: AppContainerKt.appContainer.quranRepository,
			voices: AppContainerKt.appContainer.speechVoices,
			translationId: id,
			surah: surah
		) { speech, error in
			if let error = error { NSLog("TaqwaHarness speech \(id) failed: \(error)") }
			DispatchQueue.main.async { then(speech) }
		}
	}

	/// The id and the gap are all playback reads of a `Reciter`; the rest is manifest data.
	private static func reciter(_ id: String, gapMs: Int32) -> Reciter {
		let launch = Reciters.shared.LAUNCH.first { $0.id == id }
		return Reciter(
			id: id,
			nameEn: id,
			nameAr: id,
			style: "murattal",
			kbps: 64,
			gapMs: gapMs >= 0 ? gapMs : (launch?.gapMs ?? 300),
			hue: launch?.hue.name ?? "AMBER",
			photo: nil,
			release: "audio-\(id)-v1",
			totalBytes: 0,
			surahs: []
		)
	}
}
#endif
