# Duha and Iqama reminders — design (1.3.0)

Approved by Mohamed on 25 October 2026 from the design round page (scratchpad `duha-iqama/page.html`):
"The designs look right. Go ahead and build Duha and Iqama for 1.3.0 with your recommendations on
the open decisions." Research behind it: `duha-research.md` and `iqama-research.md` in the same
folder. Branch `feature/duha-iqama`, cut from `release-1.3.0` at 37e6657e.

Both reminders are off by default. With both off, the plan, the alarms and every screen except the
two new quiet rows are exactly what 1.2.0 has.

## 1. Decisions, as built

| # | Question | As built | By |
|---|---|---|---|
| 1 | When does Duha ring? | A setting with both. **At its best time** (default) and **Once its time is in**. | Mohamed |
| 2 | One Duha rule, or match JAKIM and Kemenag? | One rule for everyone (§2). No authority data is read beyond the shown sunrise. | Lead |
| 3 | Duha in Urdu and Bengali | Title چاشت / চাশত, and never Ishraq (اشراق / ইশরাক): in subcontinental usage Ishraq is about 15–20 minutes after sunrise, so naming it at 50 minutes or more would mislead. The "Once its time is in" option and its notification say it plainly, with چاشت / চাশত as the prayer's name. Settled, no translator needed for it. | Lead |
| 4 | Diyanet's 40–50 minutes | Checked at the source (§2.4): 40–50, not more. The start rule stays at 50 minutes. | Lead |
| 5 | Iqama: one gap or one per prayer? | One per prayer (option B). | Mohamed |
| 6 | Iqama defaults | Fajr 15, Dhuhr 10, Asr 10, Maghrib 5, Isha 10. | Lead |
| 7 | Minutes on offer | Off, 5, 10, 15, 20, 25, 30. | Lead |
| 8 | Friday Dhuhr | No iqama reminder on Fridays. | Lead |
| 9 | Iqama sound | Notification by default. Silent, Notification or Takbir, as Tahajjud offers. Never the adhan. | Lead |
| 10 | Slots | A 4-day floor, and iqama last in the fill order (§3.3). | Lead |
| 11 | Ramadan | No automatic change. The user edits the minutes. | Lead |

Smaller choices the Lead made while writing this spec. Mohamed can veto any of them:

- **Duha's sound.** Notification by default, with Tahajjud's three levels.
- **Gaps kept while off.** Each prayer's gap stays stored while Iqama is off. Turning it back on brings back the person's own minutes. A gap never set reads as its default.
- **Only chosen gaps are stored.** A gap is written only when it differs from `IqamaDefaults`, and its key is removed when it is set back to its default. Absent means its default, so a later change to the defaults reaches everyone who never chose a gap.
- **Iqama is skipped** where adhan + gap reaches the end of the prayer's time (sunrise for Fajr; for the others the next prayer's start or the next Fajr, whichever comes first), and wherever the prayer itself is skipped (R83).
- **Late-delivery guards on Android** (§3.4). Duha's window ends at its end time and the iqama's 10 minutes after it rings, or at the next adhan if that comes first. A late inexact alarm is then dropped, never posted wrong.
- **Polar skip.** Duha is also skipped on a day with no true sunrise (polar night or midnight sun), since there is no rising to count from.
- **Friday note.** Dhuhr's sheet, while Iqama is on, says there is no iqama reminder on Fridays.
- **One reveal for the whole screen.** Tahajjud and adhkar adopt the reveal of §4.3, so all four cards move alike.

## 2. Duha time rules

All instants are computed at the user's own point. The sun is `SunClock(lat, lon, date, offset at
local noon, SunModel.EXACT)`, as `Engine.sky` and `DayPipeline.realSunset` build it. Altitudes are
geometric, of the sun's centre.

- **True sunrise** = `altitudeTime(-0.8333, morning = true)` (`DayPipeline.REAL_HORIZON_DEG`).
- **Shown sunrise** = `DayPrayerTimes.time(SUNRISE)`, the sunrise the user's authority and adjustments show.
- **Sunrise** = the later of the two. An authority's safety margin can never pull Duha earlier.
- **Sun at 6.5°** = `altitudeTime(6.5, morning = true)`.
- **Solar noon** = `transit()`.

The rules:

- **START** = ceil to the minute of max(sunrise + 50 min, sun at 6.5°).
- **BEST** = max(ceil(the midpoint of sunrise and solar noon), START).
- **END** = floor to the minute of (solar noon − 10 min).
- **The reminder rings at** BEST or START, as the user chose.
- **Skip the day** when:
  - the reminder would fall at or after END;
  - the sun never crosses 6.5° on its way up;
  - or there is no true sunrise.

The END is never later than any authority's end. Taqwa's Dhuhr is never before solar noon, and the
ends are: Ibn ʿUthaymin and Diyanet Dhuhr − 10, Jamiat about noon − 5, Dar al-Iftaʾ Dhuhr − 4,
Deoband midday.

### 2.1 Where it lives

Pure code in `world.taqwa.app.prayer`, beside `NightThirds`:

- `DuhaTimes.of(trueSunrise: Instant?, shownSunrise: Instant, sunAt6_5: Instant?, solarNoon: Instant): Duha?` holds the rules.
- `Duha(start, best, end)` is the result.
- `DuhaTimes.forDay(location, date, shownSunrise)` builds the sun and calls `of`.

### 2.2 Expected figures

| Place, date | Sunrise | 6.5° | START | BEST | END |
|---|---|---|---|---|---|
| Tripoli (32.8872, 13.1913), 25 Oct 2026 | 07:19 | 07:55 | **08:10** (+50 decides) | **10:06** | **12:41** |
| London (51.5074, −0.1278), 21 Dec 2026 | 08:03 | 09:10 | 09:11 (6.5° decides, +67) | 10:02 | 11:48 |
| Oslo (59.9139, 10.7522), 21 Dec 2026 | 09:18 | 11:50 | 11:51 | 11:51 (midpoint is earlier, so START) | 12:05 |
| Oslo, 21 Jun 2026 | 03:53 | 05:18 | 05:19 | 08:37 | 13:08 |
| Tromsø (69.6492, 18.9553), 5 Feb 2026 | 09:02 | never | skip | skip | skip |
| Tromsø, 21 Dec 2026 | none | never | skip | skip | skip |

The figures come from a low-precision check script (±1 min). The tests pin `SunClock`'s own
minutes after checking them against this table.

### 2.3 What the notification says

- It never says "Duha begins now": by most authorities Duha began earlier.
- At START it says "Duha time is in", which is true for everyone at that moment.
- At BEST it says "The best time for Duha".
- Both bodies end with END as a clock time.

### 2.4 Diyanet, checked at the source (decision 4)

On 25 October 2026 the Din İşleri Yüksek Kurulu page was read directly:
[Kuşluk (duha) namazı ne zaman ve nasıl kılınır?](https://kurul.diyanet.gov.tr/Cevap-Ara/223/kusluk-duha-namazi-ne-zaman-ve-nasil-kilinir),
fetva dated 12.07.2017.

- **Start.** Kuşluk begins about 40–50 minutes after sunrise ("güneşin doğuşundan yaklaşık 40-50 dakika geçmesinden itibaren").
- **End.** It lasts until about 10 minutes before öğle.
- **No altitude.** The page gives no degrees.

The Kurul's separate page on the mekruh times says the same 40–50 minutes. 40–50 is not more than
50, so the start rule stays. Diyanet's end (öğle − 10) is later than Taqwa's END, because öğle is
never before solar noon.

## 3. Notifications

### 3.1 Model and persistence

`NotificationSettings` gains these fields:

- `duha: Boolean = false`
- `duhaTiming: DuhaTiming = DuhaTiming.BEST`, with `enum class DuhaTiming { BEST, START }`
- `duhaSound: PrayerSound = NOTIFICATION`
- `iqama: Boolean = false`
- `iqamaMinutes: Map<Prayer, Int>`, defaulting to `IqamaDefaults`, with `fun iqamaMinutesFor(prayer)`. 0 means Off for that prayer.
- `iqamaSound: PrayerSound = NOTIFICATION`

The companion gains:

- `IqamaOptions = listOf(0, 5, 10, 15, 20, 25, 30)`
- `IqamaDefaults = FAJR 15, DHUHR 10, ASR 10, MAGHRIB 5, ISHA 10`
- `DuhaSounds = IqamaSounds = TahajjudSounds`

The new DataStore keys in `SettingsKeys`:

- `duha_enabled`
- `duha_timing`
- `duha_sound`
- `iqama_enabled`
- `iqama_minutes_<prayer>`
- `iqama_sound`

Absent keys read as the defaults. Each `iqama_minutes_<prayer>` is written only while it differs from
`IqamaDefaults` and removed when it equals it, so a later change to a default reaches everyone who
never chose that gap. A stored timing, sound or minute count the setting does not offer
also reads as the default for that field, by the guard Tahajjud's sound uses.

### 3.2 Kinds, ids and copy

New `NotificationKind` entries:

- **`DUHA_BEST` and `DUHA_START`.** Two kinds, one setting, one channel and one tier, as the two adhkar kinds are. `isDuha` marks them.
  - It carries `Prayer.SUNRISE` as a stand-in. Sunrise is never notified, so it cannot be mistaken for an adhan.
  - id `DUHA-<date>`, `windowEnd = END`, and the body's `{time}` is END.
- **`IQAMA`** carries its own prayer.
  - id `<PRAYER>-IQAMA-<date>`, at adhan + gap, `windowEnd = min(instant + 10 min, the bound the instant respects)`: sunrise for Fajr, else the next obligatory start or the next Fajr, whichever comes first.
  - The body's count is the gap, passed as `minutesBefore`.

Copy goes in `EnglishNotificationCopy` and in the `Words` of `LocalizedNotificationCopy`, in all
seven languages:

- `duha`, `duhaBestBody`, `duhaStartBody`, `iqamaBody`, `iqamaBodyOne` and `iqamaChannel`.
- `{time}` is wrapped in `isolatedClock`, and `{minutes}` goes through `localizedDigits`.

Titles:

- **Duha.** Duha's own name stands alone, as Tahajjud's does.
- **Iqama.** The prayer's name with the usual pairing, as the adhan's title is (`Isha · العشاء`).

Channels:

- Duha posts into "{duha} · {sound}" and iqama into "{iqamaChannel} · {sound}".

| | Duha title | At best time | Once its time is in | Iqama body (gap 10) | Iqama channel |
|---|---|---|---|---|---|
| en | Duha | The best time for Duha · until {time} | Duha time is in · until {time} | Iqama reminder · {minutes} minutes after the adhan | Iqama reminder |
| ar | الضحى | أفضل وقت لصلاة الضحى · حتى {time} | دخل وقت الضحى · حتى {time} | تذكير الإقامة · مضت {minutes} دقائق على الأذان | تذكير الإقامة |
| fr* | Douha | Le meilleur moment pour la Douha · jusqu’à {time} | Le temps de la Douha est arrivé · jusqu’à {time} | Rappel de l’iqama · {minutes} minutes après l’adhan | Rappel de l’iqama |
| tr* | Kuşluk | Kuşluk namazı için en faziletli vakit · bitiş {time} | Kuşluk vakti girdi · bitiş {time} | Kamet hatırlatması · ezandan {minutes} dakika sonra | Kamet hatırlatması |
| id* | Dhuha | Waktu terbaik untuk salat Dhuha · hingga pukul {time} | Telah masuk waktu Dhuha · hingga pukul {time} | Pengingat iqamah · {minutes} menit setelah azan | Pengingat iqamah |
| ur* | چاشت | نمازِ چاشت کا افضل وقت · {time} تک | چاشت کا وقت شروع ہو گیا · {time} تک | اقامت کی یاد دہانی · اذان کے {minutes} منٹ بعد | اقامت کی یاد دہانی |
| bn* | চাশত | চাশতের নামাজের উত্তম সময় · {time} পর্যন্ত | চাশতের সময় শুরু হয়েছে · {time} পর্যন্ত | ইকামতের রিমাইন্ডার · আজানের {minutes} মিনিট পর | ইকামতের রিমাইন্ডার |

\* The Lead wrote these, in the app's existing style. They are **for translator review** before
release. The Turkish "bitiş {time}" avoids a suffix that would depend on how the clock time is read
aloud, as the adhkar bodies avoid one.

**Arabic counting** is done in code, as the before-prayer reminder's is:

- 1 → «مضت دقيقة على الأذان»
- 2 → «مضت دقيقتان على الأذان»
- 3–10 → «مضت {minutes} دقائق على الأذان»
- 11 and up → «مضت {minutes} دقيقة على الأذان»

The offered gaps only ever reach the 3–10 and 11-and-up forms.

### 3.3 The planner

**`windowDaysFor`.** Let `base` be the alerts a day without iqama:
`5 × (remind before ? 2 : 1) + tahajjud + 2 × adhkar + duha`. Let `iq` be the number of prayers
with a gap above 0, when Iqama is on. Then:

`windowDays = max(capacity / (base + iq), min(MIN_WINDOW_DAYS = 4, capacity / base)).coerceAtLeast(1)`

| Settings | Alerts a day | windowDays |
|---|---|---|
| Default | 5 | 12 |
| + Duha | 6 | 10 |
| + Iqama | 10 | 6 |
| + Duha + Iqama | 11 | 5 |
| Remind before + Iqama | 15 | 4 |
| Today's maximum | 13 | 4 |
| Today's maximum + Iqama | 18 | 4 |
| Everything on | 19 | 4 |
| Iqama on Fajr and Isha only | — | counts 2 a day |

**`FILL_ORDER`** is PRAYER, REMINDER, TAHAJJUD, ADHKAR, DUHA, IQAMA. Every non-iqama entry of the
4-day window fits in 64, at most 4 × 14 = 56. So when slots run short, only the iqama reminders on
the furthest days are left out. At the heaviest setting about the nearest 1.5–2.5 days of iqama are
armed. `TOP_UP_HORIZON` and the Android alarm gate are unchanged: the window never drops under 4
days, so re-arming stays as it is at today's maximum.

**Duha**, for each date of the loop:

- `DuhaTimes.forDay(location, date, times.time(SUNRISE))`.
- One entry at BEST or START, kept only if that instant is after `from`.

**Iqama**, for each obligatory prayer with gap > 0:

- The entry is at the prayer's instant + gap.
- It is skipped when:
  - Iqama is off;
  - the prayer's window is empty (R83);
  - the prayer is Dhuhr and `date.dayOfWeek == FRIDAY` in the location's zone;
  - the instant is not before the end of the prayer's time: the shown sunrise for Fajr, else `minOf(next obligatory start, nextFajr)`, so no iqama lands at or after the next Fajr either (Isha's next is tomorrow's Fajr; on an R83 polar night, at 66.5° N on 5 June 2026 under other.ummalqura, Isha's start is past the next Fajr, so Maghrib's iqama must come before that Fajr). Far north, Fajr can be shorter than its gap (Reykjavik on 27 May 2026: 15 minutes), and prayer is forbidden while the sun rises;
  - or the instant is not after `from`.
- Its sound is `iqamaSound`, never the adhan.

### 3.4 Android

- **Exact or inexact.** A pure `AlarmPrecision.wantsExact(entry)` in commonMain decides it, and `AndroidNotificationScheduler.schedule` uses it in place of `!entry.kind.isAdhkar`.
  - Exact (when allowed): prayer, reminder, Tahajjud and **iqama**. An inexact iqama can arrive up to an hour late, after the iqama.
  - Always inexact: adhkar.
  - **Duha** is inexact while at least 60 minutes of its window are left when it rings. Under that, which happens only in far-north winters, it is exact, so where the platform allows exact alarms it is never dropped for lateness.
- **Late-delivery guard.** The receiver already drops any entry that arrives at or after its `windowEnd`, and times it out there (`AdhkarReminderDelivery`, which reads `windowEnd` for every kind). So:
  - a late Duha is never posted after its end;
  - a late iqama, possible only with exact alarms revoked, is never posted more than 10 minutes on, nor once the next adhan (or sunrise after Fajr) is in;
  - only the adhkar reminder is tagged.
- **Channels.**
  - Bases `duha_<sound>` and `iqama_<sound>`, through `NotificationChannels.base`.
  - `allDuhaChannelIds()` and `allIqamaChannelIds()` go into `deleteStaleChannels`'s sweep, as Tahajjud's and adhkar's are.
  - `channelKeyOf` folds both Duha kinds into one key, and all five iqama entries into one key, `(FAJR, sound, voice, IQAMA)`.
- **The debug harness** `RecitationHarnessReceiver` is kept compiling.

### 3.5 iOS

`IosNotificationScheduler` needs no change. Its sound mapping is per level, and its 64 clamp is
defensive. A tap on either reminder opens the app, as a prayer's tap does: their ids never match
`adhkarSetOfNotificationId`. Background refresh is unchanged. The 4-day floor is a window of four
dates from today, not four days of lead: with everything on, adhans stay at least about 2¾ days
ahead, as at today's maximum. The worst case is just after Isha, before local midnight, when the
furthest adhan is Isha three dates on (a review sweep of Tripoli, London, Oslo, Tromsø and Reykjavik
on four dates found 2 d 18 h at the least). Duha and the iqama never shorten it, as they come last
in the fill order. Four full days of lead would need more dates and cost iqama slots; that is the
owner's call, not built.

## 4. Settings UI (Settings › Notifications)

### 4.1 Order, top to bottom

1. Prayer notifications.
2. The system notes.
3. Remind me before.
4. **Iqama reminder** (new card).
5. **The Iqama note** (only when Iqama is on).
6. Adhan.
7. PRAYERS, with the five rows.
8. Tahajjud.
9. **Duha** (new card).
10. Morning and evening adhkar.

Cards stay 28 dp apart.

### 4.2 The cards

**Duha.**

- Off, it is the row "Duha" and its switch, nothing else.
- On, three things slide in:
  - the subtitle, which is the chosen timing's detail line;
  - a divider and **When** → the timing's name, which opens the Duha sheet;
  - a divider and **Sound** → the level, which opens `SoundSheet` with `DuhaSounds`.
- The Duha sheet:
  - Title "Duha" (`TaqwaText.screenTitle`), then the section label WHEN.
  - One `selectableGroup` card with two selectable rows, each with its name, its one-line detail and a `CheckMark` on the current one.
  - A pick writes the setting and closes the sheet.

**Iqama.**

- Off, it is the row "Iqama reminder" and its switch. Nothing appears under it, under the prayers' names, or in their sheets.
- On, five things appear:
  - the subtitle, "Minutes after the adhan, not your mosque’s own time";
  - a divider and **Sound** → `SoundSheet` with `IqamaSounds`;
  - the note under the card, "Set each prayer’s minutes in its row under Prayers.";
  - under each prayer whose gap is above 0, the line "Iqama 15 min after the adhan" (no line for an Off prayer);
  - extra content in each prayer's sound sheet, below.
- Each prayer's sound sheet, while Iqama is on:
  - The title becomes the prayer's name, with the section label SOUND over the sound card.
  - After the footnote come the section label IQAMA REMINDER and a `selectableGroup` card of Off, 5 … 30 min, with the same `RadioMark` as the SOUND rows: the sheet has one selection style.
  - Dhuhr's sheet adds the Friday note under that card.
  - A pick in either list writes the setting and closes the sheet, like every sheet in the app.
- Turning Iqama on does not open anything. The defaults are already set.

### 4.3 The reveal

Every row, subtitle, line and note that depends on a switch appears and goes with
`AnimatedVisibility`. Entering, it uses `expandVertically(tween(220)) + fadeIn(tween(220))`;
leaving, `shrinkVertically(tween(220)) + fadeOut(tween(220))`. This lives in one helper,
`SettingsReveal`, which Tahajjud and adhkar adopt too.

**Reduced motion.** When the system asks for it, the helper uses `EnterTransition.None` and
`ExitTransition.None`.

- `expect fun reduceMotion(): Boolean`, read once per screen.
- Android: `Settings.Global.ANIMATOR_DURATION_SCALE == 0f`, which is "Remove animations".
- iOS: `UIAccessibilityIsReduceMotionEnabled()`.

`TaqwaRow` gains `revealSubtitle: Boolean? = null`. Null means as today, so no other row changes.
Given, the subtitle sits in the reveal.

### 4.4 Accessibility

- The sheets' choices are radio rows, as the Remind-me-before sheet's are: `selectableGroup`, `selectable` and `selected`. Each reads as its name, then its detail, then "selected".
- A prayer row with an iqama line reads as one element: name, line, sound.
- Section labels are headings, as `SectionLabel` already makes them.
- Everything hidden while a switch is off leaves the semantics tree too, because `AnimatedVisibility` removes it.
- Nothing changes at default settings except the two new rows.
- Rows wrap at 200 % font size. RTL mirrors in ar and ur.

### 4.5 Strings (Compose, all eight folders, appended at the end; `values-in` byte-identical to `values-id`)

| key | en | ar |
|---|---|---|
| notifications_duha | Duha | الضحى |
| notifications_duha_when | When | وقت التذكير |
| notifications_section_when | WHEN | وقت التذكير |
| notifications_duha_best | At its best time | في أفضل وقتها |
| notifications_duha_best_detail | Midway between sunrise and Dhuhr | منتصف ما بين الشروق والظهر |
| notifications_duha_start | Once its time is in | عند دخول وقتها |
| notifications_duha_start_detail | %1$s minutes or more after sunrise | بعد الشروق بـ%1$s دقيقة أو أكثر |
| notifications_iqama | Iqama reminder | تذكير الإقامة |
| notifications_iqama_subtitle | Minutes after the adhan, not your mosque’s own time | بعد الأذان بدقائق تحدّدها، لا بموعد إقامة مسجدك |
| notifications_iqama_note | Set each prayer’s minutes in its row under Prayers. | تُحدَّد دقائق كل صلاة في سطرها ضمن الصلوات. |
| notifications_iqama_after (plural) | Iqama %1$s min after the adhan | few: الإقامة بعد الأذان بـ%1$s دقائق; zero, one, many and other: …بـ%1$s دقيقة; two: …بـ%1$s دقيقتين |
| notifications_iqama_off | Off | بدون تذكير |
| notifications_section_sound | SOUND | الصوت |
| notifications_section_iqama | IQAMA REMINDER | تذكير الإقامة |
| notifications_iqama_not_friday | No iqama reminder on Fridays: the khutbah comes first. | لا تذكير بالإقامة يوم الجمعة، فالخطبة قبلها. |

`notifications_duha_start_detail` takes the localized 50. Under plain `ar` the digits stay Latin,
and Arabic-Indic digits come under `ar-EG`. "Sound" reuses `notifications_tahajjud_sound`, and the
minutes in the sheet reuse `minutes_count`.

The other five languages, for translator review:

| key | fr | tr | id | ur | bn |
|---|---|---|---|---|---|
| duha | Douha | Kuşluk | Dhuha | چاشت | চাশত |
| duha_when | Quand | Ne zaman | Waktu | کب | কখন |
| section_when | QUAND | NE ZAMAN | WAKTU | کب | কখন |
| duha_best | Au meilleur moment | En faziletli vaktinde | Pada waktu terbaiknya | افضل وقت پر | উত্তম সময়ে |
| duha_best_detail | À mi-chemin entre le lever du soleil et le Dhuhr | Güneşin doğuşu ile öğlenin ortasında | Pertengahan antara terbit dan Dzuhur | طلوعِ آفتاب اور ظہر کے عین درمیان | সূর্যোদয় ও যোহরের মাঝামাঝি |
| duha_start | Dès que son temps est arrivé | Vakti girince | Begitu waktunya masuk | وقت شروع ہوتے ہی | সময় শুরু হলে |
| duha_start_detail | %1$s minutes ou plus après le lever du soleil | Güneş doğduktan %1$s dakika veya daha sonra | %1$s menit atau lebih setelah terbit | طلوعِ آفتاب کے %1$s منٹ یا زیادہ بعد | সূর্যোদয়ের %1$s মিনিট বা তার বেশি পরে |
| iqama | Rappel de l’iqama | Kamet hatırlatması | Pengingat iqamah | اقامت کی یاد دہانی | ইকামতের রিমাইন্ডার |
| iqama_subtitle | Quelques minutes après l’adhan, pas l’heure de votre mosquée | Ezandan belirlediğiniz dakika sonra, caminizin kendi vakti değil | Beberapa menit setelah azan, bukan jadwal masjid Anda | اذان کے چند منٹ بعد، آپ کی مسجد کا اپنا وقت نہیں | আজানের কয়েক মিনিট পরে, আপনার মসজিদের নিজস্ব সময় নয় |
| iqama_note | Réglez les minutes de chaque prière dans sa ligne, sous Prières. | Her vaktin dakikasını Vakitler altındaki satırından ayarlayın. | Atur menit tiap salat di barisnya di bawah Salat. | ہر نماز کے منٹ نمازوں کے تحت اس کی قطار میں مقرر کریں۔ | প্রতিটি নামাজের মিনিট নামাজসমূহ অংশে তার সারিতে ঠিক করুন। |
| iqama_after | Iqama %1$s min après l’adhan | Kamet ezandan %1$s dk sonra | Iqamah %1$s menit setelah azan | اقامت اذان کے %1$s منٹ بعد | আজানের %1$s মিনিট পরে ইকামত |
| iqama_off | Désactivé | Kapalı | Nonaktif | بند | বন্ধ |
| section_sound | SON | SES | SUARA | آواز | শব্দ |
| section_iqama | RAPPEL DE L’IQAMA | KAMET HATIRLATMASI | PENGINGAT IQAMAH | اقامت کی یاد دہانی | ইকামতের রিমাইন্ডার |
| iqama_not_friday | Pas de rappel de l’iqama le vendredi : la khoutba vient d’abord. | Cuma günleri kamet hatırlatması yok: önce hutbe okunur. | Tidak ada pengingat iqamah pada hari Jumat: khotbah lebih dulu. | جمعہ کو اقامت کی یاد دہانی نہیں: پہلے خطبہ ہوتا ہے۔ | শুক্রবার ইকামতের রিমাইন্ডার নেই: আগে খুতবা হয়। |

Every key in this table carries the `notifications_` prefix.

Formatting rules:

- Apostrophes are typographic (’). The space before a French colon is written as the existing French strings write it.
- Bengali is in NFC.
- Indonesian is edited in `values-id`, then `python3 tools/sync-indonesian.py` copies it to `values-in`.
- `scripts/check-strings.sh` must come out clean.

## 5. Tests

**Domain (commonTest):**

- `DuhaTimesTest`:
  - The five places of §2.2.
  - A shown sunrise later than the true one moves START and BEST.
  - START at or after END gives null.
  - BEST is never before START.
  - START and BEST are rounded up and END down, and a START on a whole minute stays.
- `NotificationSettings` defaults.
- `SettingsRepositoryTest`:
  - Every new field survives a round trip.
  - Absent keys read as the defaults.
  - A value the setting does not offer reads as its default.
  - The gaps are kept while Iqama is off.
  - Only the gaps that differ from their defaults are stored, and one set back to its default is removed.

**Planner and copy (commonTest, also run on iOS by `scripts/test.sh`):**

- Duha:
  - The entry at BEST and at START: its id, its body quoting END, and `windowEnd`.
  - None while it is off.
  - None on the Tromsø days.
- Iqama:
  - An entry at adhan + gap for each prayer.
  - None for an Off prayer.
  - None on Friday Dhuhr, while Friday Asr still has one.
  - None where it reaches the next prayer.
  - None at or after the next Fajr on a polar night (66.5° N, 5 June 2026, Maghrib gap 30).
  - None for an R83-skipped prayer.
- Slots:
  - `windowDaysFor` gives the values of §3.3's table.
  - With everything on, the plan is 64 or fewer, holds every non-iqama entry of the 4 days, and drops only the furthest iqama entries.
  - `FILL_ORDER` covers every kind.
- `LocalizedNotificationCopyTest`:
  - Duha's title and both bodies in 7 languages, with the clock isolated.
  - The iqama body in 7 languages.
  - The Arabic counts at 1, 2, 5, 10, 15 and 30.
  - Both channel names.
  - ur and bn call Duha چاشت / চাশত in both bodies, never Ishraq.
- `NotificationChannelsTest`: the new ids are distinct and fully enumerated.
- `AlarmPrecisionTest`: every kind, plus Duha either side of 60 minutes.
- `AdhkarReminderDeliveryTest`: the drop and the timeout for a Duha or iqama `windowEnd`.

**UI (iosTest Compose tests, like `AdhkarReminderCardTest`):**

- `DuhaCardTest`:
  - Off shows only the row.
  - On shows the subtitle, When and Sound.
  - The sheet has two radio rows, one of them selected.
- `IqamaCardTest`:
  - Off shows no subtitle, no note, no line under any prayer, and no list in the prayer sheet.
  - On shows the subtitle, Sound, the note and five lines with the defaults, and Dhuhr's sheet has the Friday note.
- The reveal chooser gives no transitions when motion is reduced.

**Checks and devices:**

- `scripts/check-strings.sh`.
- On emulator-5556 (`adb -s emulator-5556 install -r`):
  - Turn on Duha and Iqama, then check `dumpsys alarm` and the two new channels.
  - Look at the screen in light and dark, en and ar.
- The iPhone simulator, once.

---

## PLAN

Three tasks, in order. Each ends green on `scripts/test.sh` with a WIP commit (Taqwa identity, no
trailer).

### Task 1 — Domain, settings and Duha times

Files: `domain/NotificationSettings.kt`, `settings/SettingsKeys.kt`,
`settings/SettingsRepository.kt`, new `prayer/DuhaTimes.kt`; tests `prayer/DuhaTimesTest.kt` and
`settings/SettingsRepositoryTest.kt`.

1. `DuhaTiming`, and the new fields, options, defaults and sound lists (§3.1).
2. Keys, read with the guards, and write (§3.1).
3. `DuhaTimes.of` and `forDay` (§2), test first:
   - Tripoli 25 Oct 2026: 08:10, 10:06, 12:41.
   - London 21 Dec 2026, decided by 6.5°.
   - Oslo, December (BEST = START) and June.
   - The Tromsø polar skips.
   - The rounding and skip-at-END rules, with injected instants.
4. No test name contains a comma.

### Task 2 — Planner and copy on both platforms

Files: `notifications/ScheduledNotification.kt`, `NotificationPlanner.kt`, `NotificationCopy.kt`,
`LocalizedNotificationCopy.kt`, `NotificationChannels.kt`, new `AlarmPrecision.kt`,
`AdhkarReminderDelivery.kt` (doc only), androidMain `AndroidNotificationScheduler.kt`, the debug
`RecitationHarnessReceiver.kt`; iOS unchanged (§3.5). Tests are the planner and copy list of §5.

1. `NotificationKind.DUHA_BEST`, `DUHA_START` and `IQAMA`, with `isDuha`. Every `when` stays exhaustive.
2. Copy in seven languages (§3.2), including the Arabic counting.
3. The planner (§3.3):
   - `windowDaysFor` with the 4-day floor.
   - `FILL_ORDER` with Duha, then iqama last.
   - Duha entries through `DuhaTimes.forDay`.
   - Iqama entries with the Friday, next-prayer and R83 skips.
4. Android (§3.4): `AlarmPrecision.wantsExact` in `schedule`; the `duha_` and `iqama_` channels, their sweep and the folded keys.
5. On emulator-5556, check one Duha and five iqama alarms with `dumpsys alarm`, and their channels.

### Task 3 — Settings UI

Files: `feature/settings/NotificationSettingsScreen.kt`, `SoundSheet.kt` (the title and the extra
section), `design/components/TaqwaCard.kt` (`revealSubtitle`), a new `SettingsReveal` with
`expect/actual reduceMotion()`, `SettingsRoutes.kt` (the six callbacks, each writing and then
rescheduling), and strings in all eight folders. Tests: `DuhaCardTest` and `IqamaCardTest` in
iosTest.

1. `SettingsReveal` (220 ms, none when motion is reduced), adopted by Tahajjud and adhkar.
2. The Iqama card and note, the prayer lines, and the sheet's Iqama list with Dhuhr's Friday note (§4.2).
3. The Duha card and its When sheet (§4.2).
4. Accessibility (§4.4).
5. Strings (§4.5): values-in synced and `scripts/check-strings.sh` clean.
6. Look on emulator-5556 and the iPhone simulator: light and dark, en and ar, with both reminders off (unchanged apart from the two rows) and on.
