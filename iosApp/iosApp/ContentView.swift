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
            // WindowInsets.systemBars. Letting SwiftUI inset the view as well double-counted the
            // status bar height and pushed the Today header 59pt too far down.
            .ignoresSafeArea(.all)
    }
}
