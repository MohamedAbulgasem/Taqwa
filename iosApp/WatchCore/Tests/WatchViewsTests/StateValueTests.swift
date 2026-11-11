import AppKit
import SwiftUI
import XCTest
@testable import WatchViews

/// A row keeps one identity while its state word comes and goes (Ruling 64 item 5): at each start
/// a row becomes "Next prayer" or loses its word, and a row whose view is made anew loses
/// VoiceOver's focus. So the value is always set, empty when there is no word, and never added by
/// an if/else, whose two branches are two views. Drawn in an NSHostingView on the Mac, the row's
/// content appears once and never goes while its word changes.
final class StateValueTests: XCTestCase {
    /// How often the row's content has appeared and gone.
    private final class Seen {
        var appeared = 0
        var gone = 0
    }

    private final class Word: ObservableObject {
        @Published var value: String?
    }

    private struct Row: View {
        @ObservedObject var word: Word
        let seen: Seen

        var body: some View {
            Text("Asr, 16:10")
                .onAppear { seen.appeared += 1 }
                .onDisappear { seen.gone += 1 }
                .modifier(StateValue(word: word.value, language: "en"))
        }
    }

    @MainActor func testARowKeepsItsIdentityWhileItsStateWordComesAndGoes() {
        let word = Word()
        let seen = Seen()
        let host = NSHostingView(rootView: Row(word: word, seen: seen))
        host.frame = NSRect(x: 0, y: 0, width: 200, height: 40)
        let window = NSWindow(contentRect: host.frame, styleMask: [.borderless], backing: .buffered, defer: false)
        window.contentView = host
        settle(host)
        XCTAssertEqual(seen.appeared, 1, "the row is drawn")

        for value in ["Next prayer", nil, "Current prayer", "Passed"] as [String?] {
            word.value = value
            settle(host)
        }

        XCTAssertEqual(seen.appeared, 1, "the row was never made anew")
        XCTAssertEqual(seen.gone, 0, "nor taken away")
        window.contentView = nil
    }

    /// Lets SwiftUI draw the change.
    @MainActor private func settle(_ host: NSView) {
        host.layoutSubtreeIfNeeded()
        RunLoop.current.run(until: Date().addingTimeInterval(0.1))
        host.layoutSubtreeIfNeeded()
    }
}
