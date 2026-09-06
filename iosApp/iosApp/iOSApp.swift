import SwiftUI
import BackgroundTasks
import shared

private let refreshTaskId = "world.taqwa.app.refresh"

@main
struct iOSApp: App {

	init() {
		BGTaskScheduler.shared.register(forTaskWithIdentifier: refreshTaskId, using: nil) { task in
			Self.handleAppRefresh(task: task as! BGAppRefreshTask)
		}
		Self.scheduleNextRefresh()
	}

	var body: some Scene {
		WindowGroup {
			ContentView()
				.onAppear { Self.scheduleNextRefresh() }
		}
	}

	static func handleAppRefresh(task: BGAppRefreshTask) {
		scheduleNextRefresh()
		task.expirationHandler = { task.setTaskCompleted(success: false) }
		DispatchQueue.global(qos: .background).async {
			let success = BackgroundRefreshBridge.shared.runBackgroundRefresh()
			task.setTaskCompleted(success: success)
		}
	}

	static func scheduleNextRefresh() {
		let request = BGAppRefreshTaskRequest(identifier: refreshTaskId)
		request.earliestBeginDate = Date(timeIntervalSinceNow: 24 * 60 * 60)
		try? BGTaskScheduler.shared.submit(request)
	}
}
