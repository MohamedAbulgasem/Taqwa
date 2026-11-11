import SwiftUI

/// A row's state as its VoiceOver value ("Current prayer", "Passed", "Next prayer"), in the feed's
/// language (plan 4 erratum p4 #5), and none when it has no word.
///
/// The value is always set, empty with no word, so the row keeps one identity while its word comes
/// and goes, as each start makes a row "Next prayer" or takes the word away: the two branches of an
/// if/else would be two views, and VoiceOver loses its focus on a row made anew (Ruling 64 item 5).
/// An empty value is not spoken. Every page whose state word comes and goes takes this modifier
/// (plan 4 erratum p4 #15); `StateValueTests` holds the one identity.
struct StateValue: ViewModifier {
    let word: String?
    let language: String

    func body(content: Content) -> some View {
        content.accessibilityValue(Text(word.map { Speech.line($0, language: language) } ?? AttributedString()))
    }
}
