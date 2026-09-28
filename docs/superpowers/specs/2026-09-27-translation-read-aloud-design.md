# Taqwa — read the translation aloud after each ayah

**Date:** 27 Sep 2026 · **Status:** approved by Mohamed in chat (27 Sep, 00:50) with three decisions; built overnight on branch `translation-read-aloud`.

An option, off by default: after the reciter recites an ayah, a voice on the phone reads the translation shown under that ayah in the reader. Headphones on a journey, the Quran in Arabic and its meaning in your own language, no network, no cost.

## 1. Decisions

1. **What is read** is the translation the reading sheet has chosen (`ReadingSettings.translationId`), in every reading mode, Mushaf included. Nothing is read when Translation is Off. The transliteration and the Quran text itself are never read.
2. **Tafsir al-Muyassar is read**, with the Quran words it quotes dropped (§3.2). A phone voice never recites the Quran.
3. **The switch lives in two places**: the reading sheet, under Translation, and Settings › Quran › Recitation. There is no "new" pill.
4. **Both platforms ship together.** Where the phone cannot read a language, the switch is not shown at all (§4).
5. **Zero recurring cost.** Voices are the phone's own (Google's or Samsung's engine on Android, Apple's on iOS). Only offline voices are used, so no text leaves the phone.

## 2. What the listener hears

Per ayah, in order: the ayah recited → a 400 ms breath → the translation read → the reciter's usual gap → the next ayah.

- **Shared texts are read once.** Diyanet's Turkish repeats one translation across as many as 12 ayahs (570 runs), and Tafsir al-Muyassar repeats one explanation across as many as 14 (605 runs). When consecutive ayahs of a surah carry the same text, it is read once, after the last ayah of the run. Starting inside a run still reads it after the run's last ayah.
- **The speed** is the phone's own speech rate: Android's text-to-speech setting, or iOS's default rate. There is no slider in this version.
- **Controls:**
  - Play/pause pause the voice too.
  - Next ayah (long press, or the notification's ayah button) skips the rest of the translation.
  - Previous ayah during the translation restarts the ayah just recited, which is the rule gaps already follow.
  - A tap on the progress line always lands on the start of an ayah, never inside a translation.
  - Surah skip, the lock screen and the notification behave as before.
- **With read-aloud off,** playback is as it was, with one small change on Android: the bar's previous during a gap, after an ayah shorter than 2 s, now restarts that ayah instead of going back one. The notification's own previous-ayah button, and iOS, already did.
- **The clock counts what is heard.** With read-aloud on, the surah's clock includes each translation, estimated from its length at load (§5.3). The total, the lock-screen bar and the notification bar then match what the listener hears. This is a deliberate change from the chat assessment ("counts only the recitation"). A clock that stood still for a minute of speech makes Android's media controls run their bar forward and snap it back at the next ayah, because they extrapolate from the last update. With read-aloud off, the clock is exactly what it was.
- **Changes take effect at once.**
  - Turning the switch on or off, or picking another translation, while a surah plays rebuilds the queue around the ayah being heard.
  - Mid-ayah, that ayah carries on from where it is.
  - Mid-translation or mid-gap, playback moves on to the next ayah.
  - At the last ayah there is no next ayah to move on to, and the platforms differ, as built: a new translation picked during the last ayah's translation is read again, in the new language, on Android, while iOS ends the surah there. Aligning the two is left for later.
  - On Android the rebuild is one `setMediaItems` at the current position, so expect a short re-buffer of a fraction of a second.
- **When a voice is missing,** or synthesis fails, that translation is silently skipped and the recitation carries on. The explanation lives in the switch's caption, never in a pop-up mid-listen.

## 3. What is read: preparing the text

A pure function over the text the reader shows (`SpeechText.prepare`), unit-tested against the whole bundled database.

### 3.1 Every translation
- Square brackets, curly braces, the ornate parentheses ﴿ ﴾, backticks and asterisks are removed as characters; the words inside stay. Saheeh's "[All] praise is [due] to Allah" is read "All praise is due to Allah". Junagarhi's Urdu honorific ﴿علیہ السلام﴾ keeps its words.
- A trailing dash, which Saheeh uses to mean "the sentence goes on in the next ayah", is dropped.
- **Bengali repair.** Fifteen or so rows of the bundled Bengali carry HTML character references that were mangled in the source, such as `চিহিߦ#2468;`. A reference in the Bengali block (2432–2559) is decoded, together with the stray character before it. Anything else of that shape is removed. The reader still shows the broken text; fixing the data is a separate job (§9).
- Whitespace is collapsed, leading punctuation dropped, and the result trimmed. An empty result means that ayah has no translation to read.

### 3.2 Tafsir al-Muyassar: the quotations are dropped
Measured over all 6,236 rows. Muyassar marks its Quran quotations in exactly three ways, and each is removed together with its marks:
1. **Braces** `{…}`: verses quoted from elsewhere, each carrying its reference, e.g. 2:37's `{رَبَّنَا ظَلَمْنَا أَنْفُسَنَا … (7:23)}`. There are 9, in 2:37, 9:113–114, 33:37, 37:143–144 and 43:57.
2. **Fully voweled parentheses.** A parenthesis whose Arabic letters carry vowel marks at a ratio of at least 0.5 quotes the ayah itself. Six match: 1:1 (الرَّحْمَنِ)(الرَّحِيمِ), 1:2 (الحَمْدُ للهِ رَبِّ العَالَمِينَ), 1:3 twice, and 2:275.
3. **The disjoined letters** at a surah's opening, unvoweled: (الم) (الر) (المر) (كهيعص) (طه) (طسم) (طس) (يس) (ص) (حم) (حم * عسق) (ق) (ن). They are recognised as a parenthesis whose letters all belong to the disjoined-letter alphabet (ا ل م ر ك ه ي ع ص ط س ح ق ن), found in the first two ayahs of one of the 29 surahs that open with them. 29:1 opens `الم:` without parentheses; the same letters followed by a colon at the very start count too.

Every other parenthesis is explanation and is read. There are about 190: (وهم المسلمون), (آية الكرسي), (آمين), and so on. The lightly voweled single name in 1:1 "(اللهِ) علم على الرب" stays, because it is the subject of the sentence that follows. After a drop, doubled punctuation and spaces are tidied.

### 3.3 Length
The longest prepared text is about 1,700 characters (Indonesian), under Android's 4,000-character input limit. Anything longer is cut at a sentence boundary before 3,900.

## 4. Voices

| Translation | Android | iPhone |
|---|---|---|
| English, French, Turkish, Indonesian | Google engine: English built in, others a free download; Samsung engine varies | Built-in voices |
| Arabic (tafsir) | free download (4.2 MB on Google) | built in |
| Bengali | free download (Bangladesh voice preferred) | only if a Bangla voice is on the phone |
| Urdu | free download (Pakistan voice preferred) | Apple has no Urdu voice → hidden |

A per-language status, asked of the platform and never guessed:

- **Ready(voice)**: an offline voice is installed. The switch shows.
- **Missing(engine)**: Android only. An engine offers the language, but its offline voice isn't installed: the engine lists it as not installed, or `isLanguageAvailable` answers `LANG_MISSING_DATA`. A network-only voice never counts, here or anywhere: it has nothing to download. The switch shows, with the caption "Your phone needs its free French voice, a small one-time download." and a **Get the voice** action. The action opens that engine's own voice installer (`TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA`).
- **Unsupported**: no engine can read it offline. **The switch is hidden**, in the reading sheet and in Settings alike (decision 4).

**Android selection.**
- Engines are tried in order: the user's default engine, then Google's (`com.google.android.tts`), then any other. Each is bound, its `voices` listed, and it is released again.
- A voice qualifies when:
  - its language matches, with Java's legacy `in` treated as `id`;
  - it is offline: `!isNetworkConnectionRequired`, and `networkTts` (the deprecated `KEY_FEATURE_NETWORK_SYNTHESIS`) is not among its features. The timeout and retry settings `networkTimeoutMs` and `networkRetriesCount` say nothing about where a voice runs: Google's engine lists them on every voice, local ones included (measured on the emulator and the S23), and its `-network` twins are the ones that raise `isNetworkConnectionRequired`;
  - its features do not include `KEY_FEATURE_NOT_INSTALLED`.
- Among qualifying voices, the highest quality wins, then a preferred country (bn → BD then IN; ur → PK then IN; en → US then GB; fr → FR; tr → TR; id → ID; ar → any), then an id ending in `-local`, then the id. The order is `VoicePick`'s, and the same on both platforms. So an equal-quality voice still gets Bangladesh or Pakistan, but an Indian Bengali or Urdu voice the engine rates higher wins. The `-local` step is for Google's engine, which names its on-phone voices that way beside aliases such as `en-US-language` that also report themselves offline: an explicitly local voice such as `en-us-x-iob-local` is chosen over the alias when the engine rates the two alike.
- A missing voice is reported when the engine lists an offline voice for the language that is not installed, or when `isLanguageAvailable` returns `LANG_MISSING_DATA`.
- The manifest declares `<queries>` for `android.intent.action.TTS_SERVICE`, which Android 11+ requires before other engines can be seen.
- No new permission.

**iOS selection.**
- Candidates come from `AVSpeechSynthesisVoice.speechVoices()` whose language prefix matches.
- Novelty and personal voices are excluded by identifier.
- `com.apple.voice.*` voices are preferred, then by quality (premium, enhanced, default), then by the same country preferences, then by identifier: `VoicePick`'s order, as on Android.
- There is no Missing state on iOS: Apple's voice downloads live in the Settings app, which cannot be linked to. So a language without an installed voice is hidden, and the switch appears once the voice is on the phone.

**When the status is asked:**
- The reading sheet and Settings › Recitation ask every time they open. They show the switch, so they are where a voice installed or deleted since the last ask is noticed.
- The reader and the Mushaf ask only when the reading language has no cached status yet, so the sheet's switch is ready when it opens.
- A return to the app re-asks only a language whose status is Missing: the round trip through the engine's installer is what it is for. A voice deleted in the system's settings is noticed at the next sheet or Settings open.
- A load with read-aloud on asks when the language has no cached status, and waits for the answer: up to 6 s, the query's timeout, after which the language counts as Unsupported; usually under 1.5 s. Asking earlier for that first play is left for later.
- Asks are de-duplicated: an ask for a language already being asked joins that ask instead of binding the engines again.
- Results are cached in memory per language until the next ask.
- On Android the engine is bound on the main thread, which is cheap and where its start-up callback arrives anyway. The engine list, the voice list and `isLanguageAvailable` are binder calls into the engine's process, and run off the main thread.

## 5. Architecture

### 5.1 The queue (common)
`QueueItem` gains `Speech(n)`. There is no `Breath` type: the breath is a `Gap(400)` (`SPEECH_BREATH_MS`), so every silence is a `Gap`. `RecitationQueue` becomes a list-based model instead of index arithmetic:

- `RecitationQueue(surah, ayahs, gapMs, spoken: Set<Int>)` produces, for each ayah n: `Ayah(n)`, then `Gap(400)` + `Speech(n)` if n ∈ spoken, then `Gap(gapMs)` if a positive gap and not the last ayah.
- The API every caller needs:
  - `size`, `items`, `ayahs`, `ayahCount`
  - `isAyah(i)`, `isSpeech(i)`, `isGap(i)` (any silence: a reciter's gap or a breath), `silenceMs(i)`
  - `ayahAt(i)`, the ayah an item belongs to
  - `ayahIndexAt(i)`, the index of that ayah's own item
  - `indexOfAyah(n)`, `next(i)`, the next ayah's item
  - `previous(i, positionMs)`: a non-ayah item always restarts its ayah
  - `contains(n)`
- The existing `RecitationQueue(surah, ayahs, gapMs)` shape stays available, so every current test and caller keeps compiling.

### 5.2 The timeline (common)
`SurahTimeline.of(queue, ayahMs, speechMs)`: ayahs as before, gaps as before, and breaths at their length. Speech slots come from `SpeechText.estimateMs(text, language)`: characters over a per-language rate (en 16, fr 15, tr 14, id 14, ar 13, ur 13, bn 12 characters per second), at least 1 s. `snapToAyah(ms, isAyah)` walks forward to the next ayah, or back to the last one. `fitToSlot` already absorbs an estimate that is within 2× of the real length.

### 5.3 The player contract
- `PlaybackState.speaking: Boolean` is true while a `Speech` item plays.
- `load(reciter, surah, startAyah, text, speech: SpokenTranslation?)`
- `setSpeech(speech: SpokenTranslation?)` makes the live change of §2.
- `SpokenTranslation(translationId, kind, language, voice: SpeechVoice, texts: Map<Int, String>)` holds the prepared texts, keyed by the ayah they follow.
- `SpeechVoice(engine, id)` carries the Android engine package and `Voice.name`, or iOS's voice identifier with an empty engine.

### 5.4 The controller (common)
A new `ReadAloudPort`:
- the switch (`RecitationSettings.readAloud`, DataStore key `recitation_read_aloud`, default false)
- the reading translation id for the UI language
- `status(language)`
- `installVoice(engine)`

The composition hands the controller the UI language tag, as it already does with `setArabicUi`.

`RecitationState.readAloud: ReadAloudState?` is null when the switch must not show. Otherwise it carries: enabled, the translation's name, its language, its kind (translation or tafsir), and `needsVoice` (the Missing engine).

- `start()` builds the `SpokenTranslation` when the switch is on, a translation is chosen and the voice is Ready. It reads the surah's texts, prepares them, groups runs, and passes them to `load`.
- A collector sends `setSpeech` when the switch, the translation or the voice changes while a surah is loaded.
- `BarState.readingAloud: TextKind?` is set while `speaking`.

### 5.5 Android
- **Speech items**
  - Each is a `MediaItem` `speech://<generation>/<surah>/<ayah>`.
  - Breaths are silence items, like gaps.
  - The script travels on `COMMAND_NOW_PLAYING`: generation, engine, voice, language, the ayahs and their texts, and the timeline.
  - Only the app's own controller may send the script. The session is exported, so `onConnect` grants `COMMAND_NOW_PLAYING` only to a controller from the app's own package (Media3 checks a controller's package against its calling uid). No other app can hand the service text to read. The notification's two ayah commands stay open to every controller, because the system's own controller presses them.
  - Media items still carry ids only.
- **`SpeechDataSource`** in the service synthesizes on open, on ExoPlayer's loading thread, which is already reading ahead while the ayah before it plays.
  - The service holds one `TextToSpeech` bound to the chosen engine and voice. It calls `synthesizeToFile` into `cacheDir/speech/<generation>-<ayah>.wav` and waits for `onDone`.
  - Any failure (timeout, missing voice, engine error) serves a 100 ms silent WAV. The silence is served from memory, never written as a file, so neither two loading threads nor the system clearing the cache can turn it into an error. Speech never throws into ExoPlayer, because an error there would stop the recitation.
  - Every new script clears the whole directory. Generations count from one again in every process, so a file an earlier process left behind would otherwise pass for this queue's. Within a queue, a rolling window keeps the last few.
- **`AyahPlayer`**
  - Rebuilds its queue model from the loaded items' ids: `taqa` = ayah, `silence` = gap or breath, `speech` = speech.
  - Keeps hiding every non-ayah item from the session, so the notification and lock screen never change between an ayah and its translation.
  - Reports the surah clock through all of it.
- **Phase for the app.** The service publishes the real item's phase (ayah, silence or speech) in the session extras whenever it changes. `RecitationPlayer` reads it in `onExtrasChanged` for `speaking`, and for the start point of a live rebuild.

### 5.6 iOS
- A speech item is a timed phase like a gap: the `AVPlayer` pauses and an `AVSpeechSynthesizer` speaks the text with the chosen voice at the default rate.
- It uses the app's audio session (playback / spoken audio), so it keeps speaking in the background.
- Pause pauses the speech at once, and play continues it. The player tracks the pause itself instead of reading the synthesizer's `paused`, which is set only a moment after the pause is asked for: a quick pause and play would otherwise queue the text a second time.
- Any move stops it at once. On iOS 26.2 a stop reports `didFinish`, not cancel. So the player clears its current utterance before stopping, and a finish is matched by identity against the utterance still current: a stopped one matches nothing, and a move never advances twice.
- `didFinish` for the current utterance goes to the next item. The delegate is Kotlin, using `@ObjCSignatureOverride`.
- A watchdog bounds a translation that never finishes. After three times its estimated length, or the estimate and 20 s more if that is longer, of unpaused speaking time, a translation still current is stopped and the queue moves on, as if it had finished (§7).
- The speech's clock is a monotonic mark, like the gap's, so the lock screen's bar keeps moving through the translation.

## 6. UI

- **Settings › Quran › Recitation** gets a new card under the existing one, headed "After each ayah". Its row is "Read the translation aloud", or "Read the tafsir aloud", with a switch.
  - Caption: "Saheeh International, in English. A voice on this phone reads it, so it works offline."
  - When Missing, the caption is "Your phone needs its free French voice, a small one-time download." with a quiet accent row, **Get the voice**, like "Download the whole Quran".
- **Reading sheet:** under the Translation row, "Read it aloud after each ayah" with a switch. When Missing, a one-line caption and the same **Get the voice**. Hidden when Translation is Off or the language is Unsupported.
- **Player bar:** while the voice reads, the caption under the surah name is "Ayah 2 · Translation" (or "· Tafsir"), in the widest form that fits on its one line: "Ayah 56 · Translation", then "56 · Translation", then "Translation" alone. The line keeps moving on the clock.
- **Reader card:** while its translation is being read, a small accent speaker glyph sits before the translation text, with content description "Being read aloud". The playing tint already marks the ayah.
- The lock screen and notification are unchanged.

New strings, each in all 8 `values*` directories, with `values-in` twin to `values-id`:
- `recitation_read_aloud_heading`, `recitation_read_aloud_translation`, `recitation_read_aloud_tafsir`
- `recitation_read_aloud_detail`, `recitation_read_aloud_missing`, `recitation_read_aloud_get_voice`
- `quran_sheet_read_aloud`, `quran_sheet_read_aloud_missing`
- `recitation_bar_translation`, `recitation_bar_tafsir`
- `quran_a11y_read_aloud`

Language names come from `PlatformFormat.languageName`, as the reading sheet already does.

## 7. Error handling

- A missing or failed voice skips that translation silently, both at load and mid-surah. An engine that is slow to initialise shows as buffering (the bar caption goes quiet), never as an error.
- An iOS voice identifier that no longer resolves falls back to `voiceWithLanguage`; if that is nil too, the speech is skipped.
- Deleting the playing surah, switching reciter, auto-advance, the end hold and a surah skip all rebuild or stop through the existing paths. Speech is part of the queue, not a side channel.

## 8. Testing

- **Unit (commonTest):**
  - queue building and navigation with speech (next, previous, snap, `ayahAt`)
  - the timeline with speech slots
  - `SpeechText` rules, with every example in §3
  - a database-backed test over all seven bundled texts: no braces, voweled quotes or opening letters left in Muyassar; no mangled references left in Bengali; nothing longer than the limit; runs grouped
  - the controller building, withholding and live-updating the spoken translation, and the switch's visibility
- **Android emulator:** Google engine, English installed.
  - Play Al-Fatiha with read-aloud on, and measure ayah → breath → translation → gap in the harness log.
  - Bar caption, notification unchanged, shade bar monotonic.
  - Pause, next ayah and previous ayah during speech.
  - Live on/off; switch to French (Missing → Get the voice → installer).
- **S23 (media volume held at 0 at night, restored afterwards):**
  - statuses for all seven languages on Samsung's and Google's engines
  - synthesis on the engine actually chosen
  - a short playback with read-aloud on
- **iOS simulator:** voice list, playback with speech, pause/continue, backgrounding with the home button.
- **Verified tonight**, on the builds before the final fixes (Android up to e864a18, iOS 0de1cf7):
  - Android emulator, Google's engine with English only: Al-Fatiha start to finish, ayah → a 400 ms breath → translation → Alafasy's 300 ms gap → next ayah, the clock moving through the speech; the live changes; previous during a translation; a voice that does not exist, read as silence while the recitation carried on.
  - Android emulator, the UI: the reading sheet's switch, the bar's "Ayah 2 · Translation", the card's mark, the Settings card, French from Missing through **Get the voice** and Google's installer back to Ready and French speech, the tafsir's "Ayah 2 · Tafsir", and the Arabic interface's sheet and Settings, right to left.
  - S23: English Ready on Google's engine, the other six Missing on it.
  - iOS simulator: a voice for every language but Urdu, which is Unsupported (Bengali's voice is Indian); Al-Ikhlas with Saheeh read aloud, ayah → breath → translation → gap → next ayah, the clock moving through the speech; the Mushaf with Bengali, its switch and the bar's "Ayah 1 · Translation"; a pause mid-translation, next, then play: the next translation was read, and nothing hung.
- **Never run:**
  - a real iPhone;
  - playback with the screen locked, on either platform. A harness start on the locked S23 was refused by Android 16 as a background start, which is expected: real use starts in the app;
  - a listening test of the seams between recitation and speech (Android re-creates its audio output at every change between MP3 and WAV, so listen for clicks);
  - Samsung's engine: on the S23 it offered no offline voice for any of the seven languages, so no speech from it was ever heard.

## 9. Out of scope

- Human-recorded translation audio (a later upgrade for English only).
- A speed control.
- Reading the transliteration.
- Changing the lock-screen text.
- A Mushaf-page marker.
- Linking iOS voice downloads.
- Repairing the bundled Bengali text (about 15 mangled references the reader displays) and Indonesian typos (`orang0orang`, `berha]a`, `ma]aikat`, `}` for `)`): a data-pipeline fix, flagged separately.
