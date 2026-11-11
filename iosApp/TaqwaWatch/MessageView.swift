import SwiftUI

/// A page's one line, when it has nothing else to draw (plan 4 Task 7): the watch's own line before
/// it holds a feed ("Open Taqwa on your phone", or "Update Taqwa on your watch" once a newer format
/// has been seen, plan 3's call 5), in the watch's own language and type (spec 1.3); or a word of
/// the feed's, in the feed's language and type: a spent feed's on Today. VoiceOver reads it as one
/// element, a feed's word in the feed's language (plan 4 erratum p4 #5).
struct MessageView: View {
    let text: String
    /// The feed's language tag for a word of the feed's; nil for the watch's own line.
    let language: String?
    /// Whether a word of the feed's is in Latin letters (`ui.latinScript`): Manrope then.
    let latin: Bool

    /// One of the watch's own two lines, from the target's own table in the watch's language.
    init(own message: OwnMessage) {
        switch message {
        case .openOnPhone: text = OwnWords.openTaqwaOnPhone
        case .updateOnWatch: text = OwnWords.updateTaqwaOnWatch
        }
        language = nil
        latin = false
    }

    /// A word of the feed's.
    init(feedWord text: String, of feed: WatchFeed) {
        self.text = text
        language = feed.ui.language
        latin = feed.ui.latinScript
    }

    var body: some View {
        Group {
            if language == nil {
                Text(text)
            } else {
                Text(Speech.line(text, language: language))
                    .font(TaqwaFont.of(.semiBold, 15, latin: latin))
            }
        }
        .foregroundStyle(TaqwaColor.ink)
        .multilineTextAlignment(.center)
        .padding(.horizontal)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
