# Attribution

## What the licence covers

Taqwa's own source code is released under the GNU GPL-3.0 (`LICENSE`). The works listed below
are **not**: they are third-party content redistributed under their own licences, several of which
forbid what the GPL grants (the KFGQPC font may not be modified or sold, the Tanzil text may
not be changed, the translations and recitations are for non-commercial use). If you build
something from this repository, the code is yours under the GPL and each of these works is yours
only on its own terms, or must be replaced. Licence texts that must travel with a copy are kept in
`docs/licences/`.

- **Prayer time calculation**: [Adhan](https://github.com/batoulapps/adhan-kotlin) by Batoul Apps, MIT licence.
- **City database**: [GeoNames](https://www.geonames.org/) `cities15000`, CC BY 4.0.
- **Magnetic declination (iOS compass)**: the [World Magnetic Model 2025](https://www.ncei.noaa.gov/products/world-magnetic-model) by NOAA's National Centers for Environmental Information and the British Geological Survey, for the US National Geospatial-Intelligence Agency and the UK Defence Geographic Centre. Public domain, not subject to copyright; its coefficient file is embedded in `WorldMagneticModel.kt`.
- **Manrope** typeface: SIL Open Font Licence 1.1; the licence text is in `docs/licences/OFL-1.1-Manrope.txt`, as the OFL requires of every copy.
- **Adhan and takbir audio**, three recordings, one chosen in Settings under Notifications, Adhan:
  - *Original*: "Beautiful adhan" by Adam-synagda, CC0 1.0, via Wikimedia Commons.
  - *Aaqib Azeez*: "The Adhan – Muslim Call to Prayer – Aaqib Azeez", CC BY-SA 4.0, via Wikimedia Commons. The clips Taqwa bundles are trimmed and loudness-matched excerpts, and are themselves released under CC BY-SA 4.0 as that licence requires.
  - *Besim Azemi*: "Eid al-Fitr Fajr azan at Malmö Mosque" by Islamic Center Malmö, CC BY 3.0, via Wikimedia Commons.
- **Notification tone**: "Clear announce tones" from [Mixkit](https://mixkit.co/free-sound-effects/tones/), Mixkit Sound Effects Free Licence (use in apps permitted, not redistributable on its own, which is why the source WAV is not in this repository; `assets/audio/source/README.md` says how to regenerate the chime).
- **Quran text**: [Tanzil Project](https://tanzil.net) Quran Text v1.1, [Creative Commons Attribution 3.0](https://creativecommons.org/licenses/by/3.0/). Reproduced verbatim; Tanzil's notice, which must travel with every copy, is in `docs/licences/Tanzil-Quran-Text.txt` and in `quran.db`'s `notice` table.
- **Quran typeface**: KFGQPC Uthmanic Script Hafs by the King Fahd Glorious Quran Printing Complex, free to use and distribute unmodified.
- **Page layout**: Madinah Mushaf page and line breaks from the Quranic Universal Library data, via [zonetecde/mushaf-layout](https://github.com/zonetecde/mushaf-layout).
- **Recitations**: per-ayah recordings from the [Islamic Network](https://alquran.cloud) (`cdn.islamic.network`), licensed to them by the reciters or their estates for free, non-commercial redistribution at the bitrates they publish ([terms](https://alquran.cloud/terms-and-conditions), §IV). Taqwa mirrors them unmodified and at those bitrates on a public GitHub release, and downloads them on demand; no file is re-encoded. Each build also bundles one short preview per reciter (Al-Fatiha 1:1–2, the corpus's own bytes concatenated without re-encoding) so the picker can audition a voice offline. Copyright lies with the reciters, and any of them may ask to be removed — the catalogue is a manifest fetched at runtime, so a withdrawal takes effect without an app update, in the app and in its own Attribution screen alike. The voices carried:
  - Mishary Rashid Alafasy · مشاري راشد العفاسي (64 kbps)
  - Abdul Basit Abdus-Samad · عبد الباسط عبد الصمد (64 kbps)
  - Maher Al Muaiqly · ماهر المعيقلي (64 kbps)
  - Mahmoud Khalil Al-Husary · محمود خليل الحصري (64 kbps)
  - Mohamed Siddiq Al-Minshawi · محمد صديق المنشاوي (128 kbps)
  - Abdur-Rahman As-Sudais · عبد الرحمن السديس (64 kbps)
  - Saud Ash-Shuraim · سعود الشريم (128 kbps)
  - Abu Bakr Ash-Shatri · أبو بكر الشاطري (128 kbps)
  - Ali Al-Hudhaify · علي الحذيفي (128 kbps)
  - Ahmed Al-Ajmi · أحمد العجمي (128 kbps)

  The list above is the launch set; the app's Attribution screen names whichever of them the catalogue in force actually carries.
- **Translations and transliteration**: bundled from the [Tanzil Project](https://tanzil.net/trans) for non-commercial use, each translator credited below:
  - Saheeh International: Saheeh International (English)
  - التفسير الميسر: مجمع الملك فهد لطباعة المصحف الشريف (Arabic)
  - Terjemahan Kementerian Agama: Kementerian Agama Republik Indonesia (Indonesian)
  - ترجمہ محمد جوناگڑھی: محمد جوناگڑھی (Urdu)
  - মুহিউদ্দীন খান: মুহিউদ্দীন খান (Bengali)
  - Diyanet İşleri: Diyanet İşleri Başkanlığı (Turkish)
  - Muhammad Hamidullah: Muhammad Hamidullah (French)
  - Transliteration: Tanzil Project (English)
