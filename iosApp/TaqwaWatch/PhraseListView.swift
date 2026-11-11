import SwiftUI

/// The phrase list (plan 4 Task 8, `faces.js`'s `gTasbeehList`), opened from the dhikr's line or by
/// VoiceOver's "Choose a dhikr": the seven built-ins in the phone's order, then his own phrases as
/// typed, each a row with its target ("33·33·34" for the set), the one counted in the accent with an
/// accent border; and at the foot the Galaxy's Reset row (plan 4 erratum p4 #7), which asks the
/// phone's "Reset the count for this dhikr?" and, confirmed, takes the phrase counted back to 0 of
/// round 1. A row chosen is counted from its own count, and the list closes.
///
/// Every word is the feed's, in its language, and the rows take its direction. The sheet's close
/// button and the dialog's dismiss are the system's own, in the watch's language and direction: the
/// feed has no word for them, as on the Galaxy, and the close button stays clear of the clock.
///
/// VoiceOver: each row is one element, its name, ", " and its total, the one counted marked
/// selected (plan 4 erratum p4 #1); the trait is always given, empty when it is not, so a row keeps
/// its identity (Ruling 64 item 5).
///
/// The rows' names and targets and Reset grow with the watch's text size up to 1.3 times, as the
/// Galaxy's do (`TasbeehType`, plan 3's call 15), and keep the drawing's sizes at the watch's own
/// default (Large on 40 to 42mm, XLarge on 44mm and larger); a row grows with its lines. The title
/// is the system's, which follows the text size itself.
struct PhraseListView: View {
    let feed: WatchFeed
    let rows: [PhraseRow]
    let choose: (String) -> Void
    let reset: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var asking = false
    /// The watch's text size, read from a base the pixel grid cannot hide a step of
    /// (`TasbeehType.scaleBase`).
    @ScaledMetric(relativeTo: .body) private var bodyPoints = CGFloat(TasbeehType.scaleBase)

    var body: some View {
        let words = feed.words
        let language = feed.ui.language
        let latin = feed.ui.latinScript
        let scale = TasbeehType.scale(Double(bodyPoints))
        let name = CGFloat(TasbeehType.grow(scale, cap: TasbeehType.rowName))
        let target = CGFloat(TasbeehType.grow(scale, cap: TasbeehType.rowTarget))
        NavigationStack {
            ScrollViewReader { reader in
                // A stack, not a List: every row is made, so the list and its Reset row are whole to
                // VoiceOver and to the UI tests, and the crown scrolls it. Its rows are few: the seven
                // and the phrases his phone keeps.
                ScrollView {
                    VStack(spacing: 4) {
                        ForEach(rows, id: \.id) { row in
                            Button {
                                choose(row.id)
                                dismiss()
                            } label: {
                                PhraseRowView(row: row, latin: latin, name: name, target: target)
                            }
                            .buttonStyle(.plain)
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel(Text(Speech.line(row.spoken, language: language)))
                            .accessibilityAddTraits(row.selected ? [.isButton, .isSelected] : [.isButton])
                            .accessibilityIdentifier("tasbeeh.phrase.\(row.id)")
                            .id(row.id)
                        }
                        Button { asking = true } label: {
                            Text(Speech.line(words.reset, language: language))
                                .font(TaqwaFont.of(.semiBold, 13 * name, latin: latin))
                                .foregroundStyle(TaqwaColor.mist)
                                .frame(maxWidth: .infinity, minHeight: 36)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier("tasbeeh.reset")
                    }
                    .padding(.horizontal, 4)
                }
                // The rows take the feed's direction; the sheet's own close button and title keep
                // the watch's, beside its clock.
                .environment(\.layoutDirection, direction(feed))
                .onAppear {
                    if let selected = rows.first(where: \.selected) { reader.scrollTo(selected.id, anchor: .center) }
                }
            }
            .navigationTitle(Text(Speech.line(words.tasbeeh, language: language)))
            .confirmationDialog(Text(Speech.line(words.resetConfirm, language: language)), isPresented: $asking, titleVisibility: .visible) {
                Button(role: .destructive) {
                    reset()
                    dismiss()
                } label: {
                    Text(Speech.line(words.reset, language: language))
                }
            }
        }
    }
}

/// One row, as `gTasbeehList` draws it: a pill on the surface, the one counted with an accent border
/// and its name in the accent; the name (a built-in's in the phone's type, his own in the system
/// font, as the phone draws his phrases), and the target in mist with tabular figures. [name] and
/// [target] are how much the two lines grow with the watch's text size.
///
/// A grown name stays as large as it can without breaking a word: it is drawn at the largest of
/// three steps from its grown size down at which its widest word fits the room beside the target,
/// and at the drawing's 13 otherwise. "Alhamdulillah" at 1.3 times broke as "Alhamdulilla / h"
/// on the 40mm (fix round 1's first shot).
private struct PhraseRowView: View {
    let row: PhraseRow
    let latin: Bool
    let name: CGFloat
    let target: CGFloat

    var body: some View {
        HStack(spacing: 6) {
            ViewThatFits(in: .horizontal) {
                whole(13 * name)
                whole(13 * (1 + (name - 1) * 2 / 3))
                whole(13 * (1 + (name - 1) / 3))
                nameText(13)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            // The room the drawing leaves between the name and the target: 6, a spacer of 4, and 6.
            .padding(.trailing, 10)
            Text(row.target)
                .font(TaqwaFont.of(.semiBold, 11 * target, latin: latin))
                .monospacedDigit()
                .foregroundStyle(TaqwaColor.mist)
                .lineLimit(1)
                .fixedSize()
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity, minHeight: 36)
        .background(
            RoundedRectangle(cornerRadius: 18)
                .fill(TaqwaColor.surface)
                .overlay(RoundedRectangle(cornerRadius: 18).strokeBorder(TaqwaColor.amber.opacity(row.selected ? 1 : 0), lineWidth: 1.3))
        )
        .contentShape(Rectangle())
    }

    /// The name at [size], in up to two lines, shrinking to 0.8 of it before it is cut.
    private func nameText(_ size: CGFloat) -> some View {
        Text(row.name)
            .font(row.custom ? .system(size: size, weight: .semibold) : TaqwaFont.of(.semiBold, size, latin: latin))
            .foregroundStyle(row.selected ? TaqwaColor.amber : TaqwaColor.ink)
            .lineLimit(2)
            .minimumScaleFactor(0.8)
    }

    /// The name at [size], which fits only where its widest word does: the words, unseen and of no
    /// height, give the line its ideal width, and the name takes the room it is given.
    private func whole(_ size: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(row.name.split(whereSeparator: \.isWhitespace).enumerated()), id: \.offset) { _, word in
                    Text(word)
                        .font(row.custom ? .system(size: size, weight: .semibold) : TaqwaFont.of(.semiBold, size, latin: latin))
                        .fixedSize()
                }
            }
            .frame(height: 0)
            .hidden()
            nameText(size)
                .frame(idealWidth: 0, maxWidth: .infinity, alignment: .leading)
        }
    }
}
