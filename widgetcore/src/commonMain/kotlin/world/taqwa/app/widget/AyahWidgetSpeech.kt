package world.taqwa.app.widget

/**
 * The one sentence both ayah widgets give a screen reader (accessibility round 1, design §5): the
 * widget's name, the ayah's reference, then its translation when the card shows one — and never
 * the Arabic, which is for the reciters. Here in `:widgetcore` so Glance and WidgetKit cannot
 * drift apart; each platform supplies the localized name and reference from its own resources.
 */
object AyahWidgetSpeech {
    fun label(widgetName: String, reference: String, translation: String?): String {
        val head = "$widgetName, $reference"
        return if (translation.isNullOrBlank()) head else "$head. $translation"
    }
}
