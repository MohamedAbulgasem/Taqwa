import SwiftUI
import WidgetKit

/// The Apple Watch's widgets (plan 4): Taqwa on Apple's faces and in the Smart Stack, the next
/// prayer in the four accessory families (Task 6, `NextPrayerWidget.swift`). Its views are
/// `WatchShared/NextPrayerViews.swift`, which the watch app's DEBUG gallery draws too.
@main
struct TaqwaWatchWidgets: WidgetBundle {
    var body: some Widget {
        TaqwaNextPrayer()
    }
}
