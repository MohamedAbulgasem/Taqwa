import WidgetKit
import SwiftUI

@main
struct TaqwaWidgetBundle: WidgetBundle {
    var body: some Widget {
        TaqwaHomeWidget()
        TaqwaLockScreenWidget()
    }
}
