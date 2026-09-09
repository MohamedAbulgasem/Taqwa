import SwiftUI
import BackgroundTasks
import WidgetKit
import shared

private let refreshTaskId = "world.taqwa.app.refresh"

@main
struct iOSApp: App {

	init() {
		BGTaskScheduler.shared.register(forTaskWithIdentifier: refreshTaskId, using: nil) { task in
			Self.handleAppRefresh(task: task as! BGAppRefreshTask)
		}
		Self.installWidgetRefreshHook()
		Self.scheduleNextRefresh()
	}

	var body: some Scene {
		WindowGroup {
			// Hidden debug route: `xcrun simctl launch booted world.taqwa.app -taqwaWidgetPreview 1`
			// renders the real widget views full screen against the real App Group mirror. There is
			// no simctl command that places a widget on a simulator home screen, so this is how a
			// widget render gets captured. Harmless in a shipping build — nothing reaches it.
			if Self.widgetPreviewRequested {
				TaqwaWidgetPreviewScreen(section: Self.widgetPreviewSection)
			} else {
				ContentView()
					.onAppear {
						Self.scheduleNextRefresh()
						Self.reloadWidgets()
					}
					.onOpenURL { url in Self.openAyah(from: url) }
			}
		}
	}

	/// True for `-taqwaWidgetPreview 1` (which lands in `UserDefaults`' argument domain) and for
	/// `SIMCTL_CHILD_TAQWA_WIDGET_PREVIEW=1`, since `simctl launch` swallows some leading-dash
	/// arguments before the app ever sees them.
	static var widgetPreviewRequested: Bool {
		if UserDefaults.standard.bool(forKey: "taqwaWidgetPreview") { return true }
		if ProcessInfo.processInfo.arguments.contains("-taqwaWidgetPreview") { return true }
		return ProcessInfo.processInfo.environment["TAQWA_WIDGET_PREVIEW"] == "1"
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

	/// Only ever read or written on the main thread — see `reloadWidgets()`. Covers the snapshot
	/// *and* the background choice: a change to either must reach the widget, and the background
	/// used to be left out, which is why picking Light or Dark in Appearance did nothing on the
	/// home screen until WidgetKit's hourly reload happened to come round.
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
			let key = (snapshot ?? "") + "|" + (background ?? "")
			guard key != lastMirrorKey else { return }
			lastMirrorKey = key
			// `WidgetSnapshot` carries no timestamp, so stamp the write here: the extension needs it to
			// extrapolate the countdown forward between reloads instead of showing a frozen minute.
			defaults?.set(Date().timeIntervalSince1970, forKey: taqwaWrittenAtKey)
			WidgetCenter.shared.reloadAllTimelines()
		}
	}
}
