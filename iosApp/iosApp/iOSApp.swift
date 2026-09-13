import SwiftUI
import BackgroundTasks
import UIKit
import WidgetKit
import shared

private let refreshTaskId = "world.taqwa.app.refresh"

/// The one thing SwiftUI's `App` cannot express: `handleEventsForBackgroundURLSession`.
///
/// iOS relaunches the app when a background download finishes and delivers the news here — to a
/// process that has no URL sessions at all, so nothing would arrive until one with the right
/// identifier is recreated. `wakeRecitationDownloads` does both halves: it recreates the sessions
/// and holds the completion handler, which Foundation requires to be called once every delegate
/// callback has been delivered.
final class AppDelegate: NSObject, UIApplicationDelegate {
	func application(
		_ application: UIApplication,
		handleEventsForBackgroundURLSession identifier: String,
		completionHandler: @escaping () -> Void
	) {
		SurahDownloader_iosKt.wakeRecitationDownloads(completion: completionHandler)
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
		Self.installWidgetPlacementHook()
		Self.scheduleNextRefresh()
	}

	var body: some Scene {
		WindowGroup {
			// Hidden debug route: `xcrun simctl launch booted world.taqwa.app -taqwaWidgetPreview 1`
			// renders the real widget views full screen against the real App Group mirror. There is
			// no simctl command that places a widget on a simulator home screen, so this is how a
			// widget render gets captured. Compiled out of a release build: a user default is
			// writable by anyone with the device, and a route nobody should reach is best absent.
			if Self.widgetPreviewRequested {
				TaqwaWidgetPreviewScreen(section: Self.widgetPreviewSection)
			} else {
				ContentView()
					.onAppear {
						Self.scheduleNextRefresh()
						Self.reloadWidgets()
					}
					.onOpenURL { url in
						#if DEBUG
						if RecitationHarness.handle(url) { return }
						#endif
						Self.openAyah(from: url)
					}
			}
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
	static var widgetPreviewSection: String {
		UserDefaults.standard.string(forKey: "taqwaWidgetPreviewSection")
			?? ProcessInfo.processInfo.environment["TAQWA_WIDGET_PREVIEW_SECTION"]
			?? "all"
	}

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
		request.earliestBeginDate = Date(timeIntervalSinceNow: 24 * 60 * 60)
		try? BGTaskScheduler.shared.submit(request)
	}

	/// `refreshWidgets()` is `expect`/`actual` in shared code, but `WidgetCenter` is Swift-only, so
	/// the iOS actual is a hook the app fills in here — the one place that can actually make the
	/// call. `TodayViewModel.refresh()` runs once a second, so the hook does the de-duplication
	/// Kotlin cannot: it reloads only when the serialised snapshot has genuinely changed.
	static func installWidgetRefreshHook() {
		WidgetRefreshBridge.shared.onRefresh = { reloadWidgets() }
	}

	/// The Appearance screen asks which Taqwa widgets are on a home screen; `WidgetCenter` is the
	/// only thing that knows, it is Swift-only, and it answers asynchronously — so the shared code
	/// hands over a completion and this fills it in.
	///
	/// `prayer` covers both prayer kinds (home and lock screen), `ayah` the ayah card. The
	/// `answered` guard is not defensive dressing: `getCurrentConfigurations` is not contractually
	/// single-shot, and the Kotlin side treats a second resume as a programming error. Any failure
	/// reports both as placed, which is the shared side's `WidgetPlacement.Unknown` — it hides the
	/// offer rather than inviting the user to add a widget they may already have.
	static func installWidgetPlacementHook() {
		WidgetPlacement_iosKt.iosWidgetPlacementHook = { completion in
			var answered = false
			let answer: (Bool, Bool) -> Void = { prayer, ayah in
				guard !answered else { return }
				answered = true
				completion(KotlinBoolean(bool: prayer), KotlinBoolean(bool: ayah))
			}
			WidgetCenter.shared.getCurrentConfigurations { result in
				switch result {
				case .success(let widgets):
					let kinds = Set(widgets.map { $0.kind })
					answer(
						kinds.contains("TaqwaHomeWidget") || kinds.contains("TaqwaLockScreenWidget"),
						kinds.contains("TaqwaAyahWidget")
					)
				case .failure:
					answer(true, true)
				}
			}
		}
	}

	/// Only ever read or written on the main thread — see `reloadWidgets()`. Covers every mirror a
	/// widget draws from: the prayer snapshot, the background choice *and* the ayah pool. Each was
	/// added because leaving it out made a real change invisible — the background choice used to
	/// be missing, so picking Light or Dark in Appearance did nothing on the home screen until
	/// WidgetKit's hourly reload happened to come round, and the ayah pool used to be missing, so
	/// switching translation left the old one on the ayah card until its timeline ran out a week
	/// later. Anything a widget reads out of the App Group has to be represented here.
	private static var lastMirrorKey: String?

	/// `reloadWidgets()` is reached from a Kotlin-invoked closure (`TodayViewModel.refresh()` runs
	/// on whatever dispatcher it is collected on) and from the background-task queue in
	/// `handleAppRefresh`, so neither the `lastSnapshot` dedupe nor `WidgetCenter` can assume the
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
			// The ayah pool mirror is ~30 KB, so it is folded in by hash and length rather than
			// concatenated whole — this key is rebuilt once a second by `TodayViewModel.refresh()`.
			// `hashValue` is per-process seeded, which is fine: `lastMirrorKey` never outlives the
			// process either, and a fresh process reloading once more is the harmless direction.
			let ayah = defaults?.string(forKey: AyahPoolMirror.companion.KEY) ?? ""
			let key = (snapshot ?? "") + "|" + (background ?? "")
				+ "|" + String(ayah.hashValue) + "|" + String(ayah.count)
			guard key != lastMirrorKey else { return }
			lastMirrorKey = key
			// `WidgetSnapshot` carries no timestamp, so stamp the write here: the extension needs it to
			// extrapolate the countdown forward between reloads instead of showing a frozen minute.
			defaults?.set(Date().timeIntervalSince1970, forKey: taqwaWrittenAtKey)
			WidgetCenter.shared.reloadAllTimelines()
		}
	}
}

#if DEBUG
import MediaPlayer

/// Debug-only remote control for the recitation player, the iOS twin of `androidApp`'s
/// `RecitationHarnessReceiver`: slice 3a task 3 has to be verified on a simulator before task 4
/// gives recitation any UI. `#if DEBUG`, so none of it is in a shipping build.
///
/// It rides the `taqwa` URL scheme the widget already registers, because that is the only channel
/// `simctl` has into a running app:
///
/// ```
/// xcrun simctl openurl <udid> "taqwa://recite/load?surah=36&ayah=1&reciter=ar.alafasy"
/// xcrun simctl openurl <udid> "taqwa://recite/next"
/// ```
///
/// Commands: `load`, `play`, `pause`, `toggle`, `next`, `prev`, `seek?ayah=n`, `stop`, `state`,
/// `nowplaying` (dumps `MPNowPlayingInfoCenter`), `reconcile`. Everything is `NSLog`ged with the
/// prefix `TaqwaHarness`, which is how the ayah boundary and the gap are actually measured:
///
/// ```
/// xcrun simctl spawn <udid> log show --last 2m --predicate 'eventMessage CONTAINS "TaqwaHarness"'
/// ```
enum RecitationHarness {

	private static var watcher: Timer?
	private static var last: String = ""

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
			player.load(
				reciter: reciter(reciterId, gapMs: number("gap", -1)),
				surah: surah,
				startAyah: ayah,
				text: NowPlayingText(title: "Surah \(surah)", subtitle: "Mishary Rashid Alafasy")
			) { _ in }
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
				+ "playing=\(state.playing) pos=\(state.positionMs) dur=\(state.durationMs) "
				+ "surah=\(state.surah?.intValue ?? -1)"
			guard line != last else { return }
			last = line
			NSLog("TaqwaHarness state \(line)")
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
