import UIKit
import SwiftUI
import shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            // Compose owns the whole screen and applies the safe area itself via
            // WindowInsets.safeDrawing. Letting SwiftUI inset the view as well double-counted the
            // status bar height and pushed the Today header 59pt too far down. Held sideways, the
            // same safe area is what keeps content clear of the home indicator and the sensor
            // housing at the ends of the screen.
            .ignoresSafeArea(.all)
    }
}
