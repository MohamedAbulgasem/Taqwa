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
				TaqwaWidgetPreviewScreen()
			} else {
				ContentView()
					.onAppear {
						Self.scheduleNextRefresh()
						Self.reloadWidgets()
					}
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

	private static var lastSnapshot: String?

	static func reloadWidgets() {
		let defaults = UserDefaults(suiteName: taqwaAppGroupId)
		let snapshot = defaults?.string(forKey: taqwaSnapshotKey)
		guard snapshot != lastSnapshot else { return }
		lastSnapshot = snapshot
		// `WidgetSnapshot` carries no timestamp, so stamp the write here: the extension needs it to
		// extrapolate the countdown forward between reloads instead of showing a frozen minute.
		defaults?.set(Date().timeIntervalSince1970, forKey: taqwaWrittenAtKey)
		WidgetCenter.shared.reloadAllTimelines()
	}
}
