package world.taqwa.app.quran

/** Languages whose bundled translation text itself reads right-to-left (spec §5.1) — Arabic (the
 * Muyassar tafsir), Urdu and Farsi. Everything else, including the UI's own direction, is
 * irrelevant here: an English translation must read left-to-right even under an Arabic UI, and an
 * Urdu one right-to-left even under an English UI. Shared by the ayah card, the root's search
 * hits (spec 2b §2.1) and the ayah widget pool mirror writer (design spec §4), which all need the
 * same rule for whichever translation is active. */
internal val RTL_TRANSLATION_LANGUAGES = setOf("ar", "ur", "fa")

/** The Arabic-script translations that are not Arabic: a query in their script is a query in
 * them, not a quotation of the Quran (see `SearchQuery.searchesTranslationFirst`). */
internal val ARABIC_SCRIPT_TRANSLATION_LANGUAGES = setOf("ur", "fa")
