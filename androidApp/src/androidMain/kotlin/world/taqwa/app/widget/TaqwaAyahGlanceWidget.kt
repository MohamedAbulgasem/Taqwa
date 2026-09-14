package world.taqwa.app.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import kotlinx.coroutines.flow.first
import world.taqwa.app.MainActivity
import world.taqwa.app.di.appContainer
import world.taqwa.app.i18n.createPlatformFormat
import world.taqwa.app.domain.WidgetBackground
import world.taqwa.app.quran.ReadingSettings
import java.time.LocalDate
import java.util.Locale
import world.taqwa.app.i18n.UiLanguage

/**
 * The daily-ayah home-screen widget (design spec §6): one Uthmani ayah, its translation and the
 * surah's name, drawn into the same card the prayer widgets use.
 *
 * The card's *text* is a bitmap from [AyahCardRenderer] — Glance cannot use a bundled font, and
 * an ayah in anything but the Hafs face is not the thing the app is for. Everything else is real
 * Glance: the background, the corner radius and the tap target, so the Translucent background
 * choice still shows the launcher through the card.
 *
 * [SizeMode.Exact] for the same reason the prayer widget takes it — the bitmap has to be drawn at
 * the cell the launcher actually gave, not at the nearest size the provider declared.
 */
class TaqwaAyahGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Out here because it is the one thing the content lambda cannot do: it may open the
        // Quran database to write the mirror (spec §4), which is suspending work, and
        // `provideContent`'s lambda is not.
        ensureMirror(context)
        // Everything the card actually draws from is read *inside* the lambda, keyed on
        // `WidgetRedraw.ayahRevision`. The whole render used to be resolved out here, on the
        // assumption that a redraw restarts `provideGlance` — it does not (that is D1: Glance
        // leaves a live session's `provideGlance` alone and only recomposes, and a lambda whose
        // parameters have not changed is skipped), so a background or translation change did not
        // reach a widget whose session was still alive. Reading the revision here subscribes this
        // lambda to every bump, and the read that follows it is then genuinely fresh. This is the
        // ayah widget's own counter, not the prayer widgets' — a prayer countdown tick must not
        // invalidate a live ayah session (see [WidgetRedraw]).
        provideContent { AyahWidgetContent(readAyahRender(context, WidgetRedraw.ayahRevision)) }
    }
}

/** Everything one draw of the ayah widget needs, resolved before the content lambda runs. */
internal class AyahWidgetRender(
    val entry: AyahPoolEntry?,
    val languageTag: String,
    val showTranslation: Boolean,
    val translationRtl: Boolean,
    /** The mirror's record of the digit set the app itself draws (D2). */
    val arabicIndicDigits: Boolean,
    val colors: WidgetPaletteColors,
)

/**
 * The tap: open [entry]'s ayah in the reader (spec §8), or just the app when there is no entry.
 *
 * An explicit `Intent` rather than `actionStartActivity<MainActivity>(actionParametersOf(...))`,
 * which is the shorter form and was tried first. Glance does deliver the parameters — it copies
 * each into the launch intent's extras under the key's own name, and `MainActivity` read both
 * back correctly — but the intent it builds carries `FLAG_ACTIVITY_NEW_TASK` alone. Against a
 * `standard` activity that starts a *second* `MainActivity` on top of the one already in the
 * task, and the older instance's composition is still alive underneath: its own collector on
 * `LaunchRequests` took the request and consumed it before the newly created, visible instance
 * ever composed, so the tap opened the app on Today. `CLEAR_TOP or SINGLE_TOP` is the fix —
 * the existing `MainActivity` is brought forward and handed the intent through `onNewIntent`
 * instead of being duplicated — and only the `Intent` overload lets those flags be set.
 *
 * The data URI is per-ayah and makes the `PendingIntent` distinct by `Intent.filterEquals`,
 * which extras alone are not. It is the same shape as the iOS extension's `widgetURL` (spec §7),
 * though nothing parses it: [MainActivity] reads the extras.
 */
private fun openAyahAction(context: Context, entry: AyahPoolEntry?): Action = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP,
        )
        .also { intent ->
            // No entry means the mirror has not been written yet: the card is empty, and the tap
            // is just "open Taqwa" rather than a dead rectangle.
            if (entry == null) return@also
            intent.setAction(ACTION_OPEN_AYAH)
                .setData(Uri.parse("taqwa://ayah/${entry.surah}/${entry.ayah}"))
                .putExtra(EXTRA_OPEN_SURAH, entry.surah)
                .putExtra(EXTRA_OPEN_AYAH, entry.ayah)
        },
)

/** Only ever read by [Intent.filterEquals], to keep this tap distinct from the prayer widgets'. */
private const val ACTION_OPEN_AYAH = "world.taqwa.app.OPEN_AYAH"

/** The extra names `MainActivity` reads; kept in step with its own constants. */
private const val EXTRA_OPEN_SURAH = "open_surah"
private const val EXTRA_OPEN_AYAH = "open_ayah"

/**
 * Writes the pool mirror when there is none, so the first draw has an ayah to show.
 *
 * Spec §4's Android rule: a Glance widget runs in the app's own process, so unlike the iOS
 * extension it can open the Quran database itself. A widget placed before the app was ever opened
 * therefore fills the mirror with the language's default reading settings and draws a real ayah,
 * rather than the placeholder iOS has to show. Best-effort — a database that fails to open leaves
 * [AyahWidgetRender.entry] null and the card empty until the app runs.
 *
 * A mirror from an older wire version deserialises to null (spec §4, `AyahPoolMirror.VERSION` 2),
 * so this is also what rewrites it after an upgrade rather than leaving the card blank.
 */
private suspend fun ensureMirror(context: Context) {
    val store = createWidgetKeyValueStore()
    if (AyahPoolMirror.read(store) != null) return
    val deviceTag = Locale.getDefault().toLanguageTag()
    // The user's own reading settings, not a hardcoded default: a missing mirror here is not
    // necessarily a fresh install — it is just as likely a version-2 rejection of an old-format
    // mirror (spec §4) or a prefs file the system cleared, and either one must not silently hand
    // someone who chose another translation the default one. Only a settings read that actually
    // throws falls back to the language's own defaults, so the widget still draws something.
    val readingSettings = runCatching {
        appContainer.settingsRepository.readingSettings(deviceTag).first()
    }.getOrElse { ReadingSettings.defaultsFor(deviceTag) }
    runCatching {
        AyahPoolMirrorWriter.write(
            store,
            appContainer.quranRepository,
            readingSettings,
            deviceTag,
            createPlatformFormat(),
        )
    }
}

/**
 * Everything one draw needs, read from the mirror at the moment it draws.
 *
 * [redrawRevision] is deliberately unused. Reading [WidgetRedraw.ayahRevision] at the call site —
 * inside the content lambda — is what subscribes that lambda to a mirror change, and taking it as
 * a parameter is what keeps the read from looking like dead code (D1; see [WidgetRedraw]).
 */
private fun readAyahRender(
    context: Context,
    @Suppress("UNUSED_PARAMETER") redrawRevision: Int,
): AyahWidgetRender {
    val store = createWidgetKeyValueStore()
    val deviceTag = Locale.getDefault().toLanguageTag()
    val mirror = AyahPoolMirror.read(store)

    // A mirror written before the seed (an interrupted first write) rotates from 0 rather than
    // failing; the next successful write puts the install's real seed in place.
    val seed = AyahPoolMirror.seed(store) ?: 0L
    val today = LocalDate.now()
    val entry = mirror?.entryFor(
        AyahRotation.epochDay(today.year, today.monthValue, today.dayOfMonth),
        seed,
    )

    val background = store.getString("widget_background")
        ?.let { raw -> WidgetBackground.entries.firstOrNull { it.name == raw } }
        ?: WidgetBackground.FOLLOW_THEME
    val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return AyahWidgetRender(
        entry = entry,
        // The mirror's own tag, not the device's: it is the tag the surah names in it were read
        // under, so the footer's digits follow the script the names are already in.
        languageTag = mirror?.languageTag ?: deviceTag,
        showTranslation = mirror?.showsTranslation == true,
        translationRtl = mirror?.translationRtl == true,
        // The app's own digit set as it recorded it, not the CLDR default for the tag: under an
        // `ar-LY` per-app locale the S23 draws the app's numbers in Arabic-Indic digits and the
        // tag rule says Western, so the footer used to contradict the app (D2).
        arabicIndicDigits = mirror?.arabicIndicDigits == true,
        colors = WidgetPalette.colorsFor(background, nightMode == Configuration.UI_MODE_NIGHT_YES),
    )
}

@Composable
private fun AyahWidgetContent(render: AyahWidgetRender) {
    val context = LocalContext.current
    val size = LocalSize.current
    val entry = render.entry
    WidgetCard(render.colors, openAyahAction(context, entry)) {
        if (entry == null) return@WidgetCard
        val density = context.resources.displayMetrics.density
        // `LocalSize` is in dp and the renderer works in device pixels. Remembered against
        // everything that changes the drawing, so a recomposition that changes none of them —
        // Glance recomposes a live session on every `update()` — does not redraw a megabyte.
        // Every input the renderer draws from is therefore in the key: the cell size, the entry,
        // the palette, and all three text-shaping flags (`arabicUi` is derived from `languageTag`,
        // so that one tag covers it). `density` is not, because it cannot change without a
        // configuration change, which tears the whole Glance session down.
        val bitmap = remember(
            size,
            entry,
            render.colors,
            render.showTranslation,
            render.translationRtl,
            render.languageTag,
            render.arabicIndicDigits,
        ) {
            AyahCardRenderer.render(
                context,
                AyahCardInput(
                    widthPx = (size.width.value * density).toInt(),
                    heightPx = (size.height.value * density).toInt(),
                    density = density,
                    entry = entry,
                    // Arabic and Urdu: the surah name stands alone in the footer, as it does in the app.
                    arabicUi = UiLanguage.of(render.languageTag).arabicScript,
                    arabicIndicDigits = render.arabicIndicDigits,
                    languageTag = render.languageTag,
                    translationRtl = render.translationRtl,
                    showTranslation = render.showTranslation,
                    textArgb = render.colors.textArgb.toInt(),
                    accentArgb = render.colors.accentArgb.toInt(),
                ),
            )
        }
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
            // The bitmap is already the cell's size, so Fit is a no-op in the common case; it
            // only does work for the oversized cells the renderer draws at reduced scale.
            contentScale = ContentScale.Fit,
        )
    }
}
