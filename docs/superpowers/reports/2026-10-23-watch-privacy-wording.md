# The watch feed's privacy wording, for plan 5 and Mohamed's approval

Plan 2, Task 3 Step 7 (Lane P, 24 October 2026). *Applied since by plan 5 Task 9 (`docs/superpowers/reports/2026-10-30-watch-privacy-text.md`), approved by Mohamed at M3 (31 October 2026), and held line by line by `tools/privacy-check.py`.* **No app, site or store file changes here**: plan 5 applies these lines, as its ship gate, and no store build carries a sender until every one reads as ruled or as Mohamed approved, in each of its languages.

How to read the drafts:
- **Ruling 39's lines** (sections 2 and 3) are ruled in English, and are not reworded: "Only your own devices can see your location." and the relay sentence.
- **Question 7's lines** (section 4) are drafts **for Mohamed's approval**, in English as plan 2's question 7 words them.
- **Every translation** is marked *draft, to native review (plan 5)*. Each is drafted from the app's own words in that language: device = جهاز, appareil, cihaz, perangkat, آلہ, ডিভাইস; location = موقع, position, konum, lokasi, مقام, অবস্থান; phone = هاتف, téléphone, telefon, ponsel, فون, ফোন. The app has no word for "watch" yet (the watch apps' own strings are English until plan 5), so the drafts use ساعة, montre, saat, jam tangan, گھڑی, ঘড়ি.
- The repository's i18n rules hold for the app strings: Indonesian's text goes in both `values-id` and its twin `values-in`; `strings.xml` keeps Compose's typographic apostrophe (’), as `values-fr` and `values-tr` already do. Each file keeps its own apostrophe: `PRIVACY.tr.md` and the Turkish site pages write `Taqwa'nın`, the Turkish strings and listing `Taqwa’nın`.
- The one sentence Ruling 39 rules, in the six languages, is used word for word wherever it occurs:

| Language | "Only your own devices can see your location." (draft, to native review (plan 5)) |
|---|---|
| Arabic | لا ترى موقعك إلا أجهزتك أنت. |
| French | Seuls vos propres appareils peuvent voir votre position. |
| Turkish | Konumunuzu yalnızca kendi cihazlarınız görebilir. |
| Indonesian | Hanya perangkat Anda sendiri yang dapat melihat lokasi Anda. |
| Urdu | آپ کا مقام صرف آپ کے اپنے آلات دیکھ سکتے ہیں۔ |
| Bengali | আপনার অবস্থান শুধু আপনার নিজের ডিভাইসগুলোই দেখতে পারে। |

## 1. What leaves the phone, as the builder now writes it

Ruling 39's table, field by field, from `WatchFeedBuilder` (`shared/src/commonMain/kotlin/world/taqwa/app/watchfeed/WatchFeedBuilder.kt`) at `838f0c98`:

| What | Fields | As sent |
|---|---|---|
| 31 days of prayer starts, Fajr's and Asr's ends, each date's midnight | `days[].date`, `days[].times.{fajr,sunrise,dhuhr,asr,maghrib,isha,fajrEnd,asrEnd}`, `days[].midnight` | the engine's own instants in epoch seconds, unchanged; each date's start at the place |
| each day's words | `days[].clock`, `names`, `hijri`, `hijriDayMonth`, `gregorian`, `weekday`, `dayNumber`, `gregorianSpoken` | the phone's text, in the app's interface language and the phone's digits |
| the city | `place.city` | the name the Prayer screen shows (`watchCityName`): the bundle's name in the phone's language, else the stored name, else "" |
| the Qibla's bearing | `place.qiblaBearing` | the Qibla card's whole degree |
| the magnetic declination | `place.declination` | a whole degree |
| the distance to the Kaaba | `place.qiblaKm` | two significant figures, in the phone's digits and grouping |
| the hemisphere | `place.southern` | a flag |
| how the phone writes | `ui.{language,rtl,digitZero,twelveHour,am,pm,countdown,nextIn,spoken}` | the words locale's tag (letter case only), the direction, the digits' zero, 12 or 24 hours, the markers, the countdown phrases, the next-in label, the screen reader's sentence and plurals |
| his own tasbeeh phrases | `tasbeeh[].{id,phrase,target}` | his text, the first 100 in order, as many as fit 60,000 bytes |
| the feed's version and time | `version`, `writtenAt` | numbers; beside the feed, never inside it, travel its fingerprint (Task 4), a random per-install number and a counter (Ruling 50's delivery: origin and number) |

Never sent: the coordinates, the timetable chosen, the notification settings, bookmarks, counts, or anything else. One real feed's `place`, the committed samples' Cape Town:

- English (`watchcore/vectors/sample-feed-capetown.json`): `{"city":"Cape Town","qiblaBearing":23.0,"qiblaKm":"6,600","declination":-27.0,"southern":true}`
- Arabic, Saudi Arabia (`sample-feed-capetown-ar-sa.json`): `{"city":"كيب تاون","qiblaBearing":23.0,"qiblaKm":"٦٬٦٠٠","declination":-27.0,"southern":true}`

The point the phone stores is (-33.9249, 18.4241); neither "-33.92" nor "18.42" is anywhere in the feed (`WatchFeedBuilderTest.thePlaceIsWhatRuling39Allows` holds that for seven cities).

## 2. Ruling 39, item 1: the location line

"Only your own devices can see your location." takes the place of the old clause, and the rest of each text stands.

### `NSLocationWhenInUseUsageDescription`

| File | Current | New |
|---|---|---|
| `iosApp/iosApp/Info.plist:43` (the fallback) | Taqwa uses your location to calculate prayer times on your device. Your location is never sent anywhere. | Taqwa uses your location to calculate prayer times on your device. Only your own devices can see your location. |
| `en.lproj/InfoPlist.strings:3` | the same | the same as `Info.plist` |
| `ar.lproj/InfoPlist.strings:3` | يستخدم تقوى موقعك لحساب مواقيت الصلاة على جهازك. موقعك لا يغادر هاتفك أبدًا. | يستخدم تقوى موقعك لحساب مواقيت الصلاة على جهازك. لا ترى موقعك إلا أجهزتك أنت. *(draft, to native review (plan 5))* |
| `fr.lproj/InfoPlist.strings:3` | Taqwa utilise votre position pour calculer les horaires de prière sur votre appareil. Votre position n’est jamais envoyée nulle part. | Taqwa utilise votre position pour calculer les horaires de prière sur votre appareil. Seuls vos propres appareils peuvent voir votre position. *(draft, to native review (plan 5))* |
| `tr.lproj/InfoPlist.strings:3` | Taqwa, namaz vakitlerini cihazınızda hesaplamak için konumunuzu kullanır. Konumunuz hiçbir yere gönderilmez. | Taqwa, namaz vakitlerini cihazınızda hesaplamak için konumunuzu kullanır. Konumunuzu yalnızca kendi cihazlarınız görebilir. *(draft, to native review (plan 5))* |
| `id.lproj/InfoPlist.strings:3` | Taqwa memakai lokasi Anda untuk menghitung waktu salat di perangkat Anda. Lokasi Anda tidak pernah dikirim ke mana pun. | Taqwa memakai lokasi Anda untuk menghitung waktu salat di perangkat Anda. Hanya perangkat Anda sendiri yang dapat melihat lokasi Anda. *(draft, to native review (plan 5))* |
| `ur.lproj/InfoPlist.strings:3` | تقویٰ آپ کے مقام سے آپ ہی کے آلے پر نماز کے اوقات نکالتا ہے۔ آپ کا مقام کبھی کہیں نہیں بھیجا جاتا۔ | تقویٰ آپ کے مقام سے آپ ہی کے آلے پر نماز کے اوقات نکالتا ہے۔ آپ کا مقام صرف آپ کے اپنے آلات دیکھ سکتے ہیں۔ *(draft, to native review (plan 5))* |
| `bn.lproj/InfoPlist.strings:3` | তাকওয়া আপনার ডিভাইসেই নামাজের সময় হিসাব করতে আপনার অবস্থান ব্যবহার করে। আপনার অবস্থান কখনো কোথাও পাঠানো হয় না। | তাকওয়া আপনার ডিভাইসেই নামাজের সময় হিসাব করতে আপনার অবস্থান ব্যবহার করে। আপনার অবস্থান শুধু আপনার নিজের ডিভাইসগুলোই দেখতে পারে। *(draft, to native review (plan 5))* |

### `onboarding_location_body` (`shared/src/commonMain/composeResources/values*/strings.xml`)

| Directory | Current | New |
|---|---|---|
| `values:22` | Prayer times depend on where you are. Everything is calculated on your device. Your location never leaves your phone. | Prayer times depend on where you are. Everything is calculated on your device. Only your own devices can see your location. |
| `values-ar:18` | تعتمد المواقيت على موقعك. كل الحساب يجري على جهازك، وموقعك لا يغادر هاتفك. | تعتمد المواقيت على موقعك. كل الحساب يجري على جهازك. لا ترى موقعك إلا أجهزتك أنت. *(draft, to native review (plan 5))* |
| `values-fr:20` | Les horaires dépendent de votre position. Tout est calculé sur votre appareil, et votre position ne quitte jamais votre téléphone. | Les horaires dépendent de votre position. Tout est calculé sur votre appareil. Seuls vos propres appareils peuvent voir votre position. *(draft, to native review (plan 5))* |
| `values-tr:20` | Namaz vakitleri konumunuza bağlıdır. Her şey cihazınızda hesaplanır. Konumunuz telefonunuzdan hiç çıkmaz. | Namaz vakitleri konumunuza bağlıdır. Her şey cihazınızda hesaplanır. Konumunuzu yalnızca kendi cihazlarınız görebilir. *(draft, to native review (plan 5))* |
| `values-id:18` and `values-in:18` | Waktu salat bergantung pada posisi Anda. Semuanya dihitung di perangkat Anda. Lokasi Anda tidak pernah keluar dari ponsel. | Waktu salat bergantung pada posisi Anda. Semuanya dihitung di perangkat Anda. Hanya perangkat Anda sendiri yang dapat melihat lokasi Anda. *(draft, to native review (plan 5))* |
| `values-ur:18` | نماز کے اوقات آپ کے مقام پر منحصر ہیں۔ سارا حساب آپ کے فون پر ہوتا ہے، اور آپ کا مقام کبھی فون سے باہر نہیں جاتا۔ | نماز کے اوقات آپ کے مقام پر منحصر ہیں۔ سارا حساب آپ کے فون پر ہوتا ہے۔ آپ کا مقام صرف آپ کے اپنے آلات دیکھ سکتے ہیں۔ *(draft, to native review (plan 5))* |
| `values-bn:18` | নামাজের সময় আপনার অবস্থানের উপর নির্ভর করে। সব হিসাব আপনার ডিভাইসেই হয়, আর আপনার অবস্থান কখনো ফোন ছেড়ে যায় না। | নামাজের সময় আপনার অবস্থানের উপর নির্ভর করে। সব হিসাব আপনার ডিভাইসেই হয়। আপনার অবস্থান শুধু আপনার নিজের ডিভাইসগুলোই দেখতে পারে। *(draft, to native review (plan 5))* |

### `location_privacy_note`

| Directory | Current | New |
|---|---|---|
| `values:193` | Coordinates are stored on your device and used only to calculate times. Nothing is sent anywhere. | Coordinates are stored on your device and used only to calculate times. Only your own devices can see your location. |
| `values-ar:177` | تُحفظ الإحداثيات على جهازك وتُستخدم لحساب المواقيت فقط. لا يُرسل شيء إلى أي جهة. | تُحفظ الإحداثيات على جهازك وتُستخدم لحساب المواقيت فقط. لا ترى موقعك إلا أجهزتك أنت. *(draft, to native review (plan 5))* |
| `values-fr:173` | Les coordonnées sont enregistrées sur votre appareil et servent uniquement à calculer les horaires. Rien n’est envoyé nulle part. | Les coordonnées sont enregistrées sur votre appareil et servent uniquement à calculer les horaires. Seuls vos propres appareils peuvent voir votre position. *(draft, to native review (plan 5))* |
| `values-tr:174` | Koordinatlar cihazınızda saklanır ve yalnızca vakitleri hesaplamak için kullanılır. Hiçbir yere hiçbir şey gönderilmez. | Koordinatlar cihazınızda saklanır ve yalnızca vakitleri hesaplamak için kullanılır. Konumunuzu yalnızca kendi cihazlarınız görebilir. *(draft, to native review (plan 5))* |
| `values-id:167` and `values-in:167` | Koordinat disimpan di perangkat Anda dan hanya dipakai untuk menghitung waktu salat. Tidak ada yang dikirim ke mana pun. | Koordinat disimpan di perangkat Anda dan hanya dipakai untuk menghitung waktu salat. Hanya perangkat Anda sendiri yang dapat melihat lokasi Anda. *(draft, to native review (plan 5))* |
| `values-ur:169` | آپ کے مقام کا طول و عرض آپ کے فون پر محفوظ رہتا ہے اور صرف اوقات کے حساب کے لیے استعمال ہوتا ہے۔ کچھ بھی کہیں نہیں بھیجا جاتا۔ | آپ کے مقام کا طول و عرض آپ کے فون پر محفوظ رہتا ہے اور صرف اوقات کے حساب کے لیے استعمال ہوتا ہے۔ آپ کا مقام صرف آپ کے اپنے آلات دیکھ سکتے ہیں۔ *(draft, to native review (plan 5))* |
| `values-bn:169` | স্থানাঙ্ক আপনার ডিভাইসেই জমা থাকে এবং শুধু সময় হিসাব করতে ব্যবহৃত হয়। কোথাও কিছু পাঠানো হয় না। | স্থানাঙ্ক আপনার ডিভাইসেই জমা থাকে এবং শুধু সময় হিসাব করতে ব্যবহৃত হয়। আপনার অবস্থান শুধু আপনার নিজের ডিভাইসগুলোই দেখতে পারে। *(draft, to native review (plan 5))* |

### The policy's Location section, its last sentence

| File | Current | New |
|---|---|---|
| `PRIVACY.md:29` | Your location is used on the device and never transmitted. | Your location is used on the device. Only your own devices can see your location. |
| `PRIVACY.ar.md:27` | يُستخدم موقعك على الجهاز ولا يُنقل أبدًا. | يُستخدم موقعك على الجهاز. لا ترى موقعك إلا أجهزتك أنت. *(draft, to native review (plan 5))* |
| `PRIVACY.fr.md:30-31` | Votre position est utilisée sur l’appareil et n’est jamais transmise. | Votre position est utilisée sur l’appareil. Seuls vos propres appareils peuvent voir votre position. *(draft, to native review (plan 5))* |
| `PRIVACY.tr.md:29` | Konumunuz cihaz üzerinde kullanılır ve hiçbir zaman aktarılmaz. | Konumunuz cihaz üzerinde kullanılır. Konumunuzu yalnızca kendi cihazlarınız görebilir. *(draft, to native review (plan 5))* |
| `PRIVACY.id.md:30` | Lokasi Anda dipakai di perangkat dan tidak pernah dikirimkan. | Lokasi Anda dipakai di perangkat. Hanya perangkat Anda sendiri yang dapat melihat lokasi Anda. *(draft, to native review (plan 5))* |
| `PRIVACY.ur.md:30` | آپ کا مقام آلے ہی پر استعمال ہوتا ہے اور کبھی کہیں منتقل نہیں کیا جاتا۔ | آپ کا مقام آلے ہی پر استعمال ہوتا ہے۔ آپ کا مقام صرف آپ کے اپنے آلات دیکھ سکتے ہیں۔ *(draft, to native review (plan 5))* |
| `PRIVACY.bn.md:29` | আপনার অবস্থান ডিভাইসেই ব্যবহৃত হয় এবং কখনো পাঠানো হয় না। | আপনার অবস্থান ডিভাইসেই ব্যবহৃত হয়। আপনার অবস্থান শুধু আপনার নিজের ডিভাইসগুলোই দেখতে পারে। *(draft, to native review (plan 5))* |

The site's privacy pages are built from these files (`site/build.py`), so they follow.

### The store listings' location bullet

| File | Current | New |
|---|---|---|
| `docs/store/listing/en.md:49` | • Your location is used on your phone to calculate times and never sent anywhere. | • Your location is used on your phone to calculate times. Only your own devices can see your location. |
| `ar.md:44` | • موقعك يُستخدم على هاتفك لحساب المواقيت ولا يُرسل إلى أي مكان. | • موقعك يُستخدم على هاتفك لحساب المواقيت. لا ترى موقعك إلا أجهزتك أنت. *(draft, to native review (plan 5))* |
| `fr.md:47` | • Votre position sert, sur votre téléphone, à calculer les horaires, et n’est jamais envoyée nulle part. | • Votre position sert, sur votre téléphone, à calculer les horaires. Seuls vos propres appareils peuvent voir votre position. *(draft, to native review (plan 5))* |
| `tr.md:47` | • Konumunuz vakitleri hesaplamak için telefonunuzda kullanılır ve hiçbir yere gönderilmez. | • Konumunuz vakitleri hesaplamak için telefonunuzda kullanılır. Konumunuzu yalnızca kendi cihazlarınız görebilir. *(draft, to native review (plan 5))* |
| `id.md:44` | • Lokasi Anda dipakai di ponsel Anda untuk menghitung waktu salat dan tidak pernah dikirim ke mana pun. | • Lokasi Anda dipakai di ponsel Anda untuk menghitung waktu salat. Hanya perangkat Anda sendiri yang dapat melihat lokasi Anda. *(draft, to native review (plan 5))* |
| `ur.md:46` | • آپ کا مقام آپ کے فون پر اوقات کے حساب کے لیے استعمال ہوتا ہے اور کبھی کہیں نہیں بھیجا جاتا۔ | • آپ کا مقام آپ کے فون پر اوقات کے حساب کے لیے استعمال ہوتا ہے۔ آپ کا مقام صرف آپ کے اپنے آلات دیکھ سکتے ہیں۔ *(draft, to native review (plan 5))* |
| `bn.md:47` | • আপনার অবস্থান সময় হিসাব করতে আপনার ফোনেই ব্যবহৃত হয়, কোথাও পাঠানো হয় না। | • আপনার অবস্থান সময় হিসাব করতে আপনার ফোনেই ব্যবহৃত হয়। আপনার অবস্থান শুধু আপনার নিজের ডিভাইসগুলোই দেখতে পারে। *(draft, to native review (plan 5))* |

### `README.md:208` (English only)

- Current: "Location never leaves the phone."
- New: "Only your own devices can see your location." (The same line's other two clauses are question 7's, section 4.)

## 3. Ruling 39, item 2: the relay sentence, in a "Your watch" paragraph

In each policy, a short section after "What stays on your phone" (after its crash-report paragraph, before "Location"). The lead-in is question 7's, **for Mohamed's approval**; the second sentence is the ruled relay sentence. "This data" then names every field of section 1, his phrases among them.

| File | Heading | Paragraph |
|---|---|---|
| `PRIVACY.md` | ## Your watch | If you add Taqwa to your watch, your phone gives it your city, the Qibla's direction and distance, your area's compass correction, the coming month's prayer times as the app writes them, and your own tasbeeh phrases. When your phone and watch are not directly connected, Google (Android) or Apple (iPhone) may carry this data over the internet, end-to-end encrypted so only your phone and watch can read it. |
| `PRIVACY.ar.md` | ## ساعتك | إن أضفت تقوى إلى ساعتك، يعطيها هاتفك مدينتك، واتجاه القبلة ومسافتها، وتصحيح البوصلة في منطقتك، ومواقيت الصلاة للشهر القادم كما يكتبها التطبيق، وأذكار التسبيح التي كتبتها بنفسك. وحين لا يكون هاتفك وساعتك متصلين اتصالًا مباشرًا، قد تنقل Google (أندرويد) أو Apple (iPhone) هذه البيانات عبر الإنترنت، مشفّرةً من طرف إلى طرف فلا يقرؤها إلا هاتفك وساعتك. *(draft, to native review (plan 5))* |
| `PRIVACY.fr.md` | ## Votre montre | Si vous ajoutez Taqwa à votre montre, votre téléphone lui donne votre ville, la direction et la distance de la qibla, la correction de boussole de votre région, les horaires de prière du mois à venir tels que l’application les écrit, et vos propres phrases de tasbih. Quand votre téléphone et votre montre ne sont pas connectés directement, Google (Android) ou Apple (iPhone) peut acheminer ces données par Internet, chiffrées de bout en bout pour que seuls votre téléphone et votre montre puissent les lire. *(draft, to native review (plan 5))* |
| `PRIVACY.tr.md` | ## Saatiniz | Taqwa'yı saatinize eklerseniz telefonunuz ona şehrinizi, kıblenin yönünü ve uzaklığını, bölgenizin pusula düzeltmesini, önümüzdeki ayın namaz vakitlerini uygulamanın yazdığı biçimde ve kendi tesbih ifadelerinizi verir. Telefonunuz ve saatiniz doğrudan bağlı olmadığında Google (Android) ya da Apple (iPhone) bu verileri internet üzerinden taşıyabilir; veriler uçtan uca şifrelidir, böylece yalnızca telefonunuz ve saatiniz okuyabilir. *(draft, to native review (plan 5))* |
| `PRIVACY.id.md` | ## Jam tangan Anda | Jika Anda menambahkan Taqwa ke jam tangan Anda, ponsel Anda memberinya kota Anda, arah dan jarak kiblat, koreksi kompas di daerah Anda, waktu salat sebulan ke depan sebagaimana ditulis aplikasi, dan kalimat tasbih Anda sendiri. Saat ponsel dan jam tangan Anda tidak terhubung langsung, Google (Android) atau Apple (iPhone) dapat membawa data ini lewat internet, terenkripsi end-to-end sehingga hanya ponsel dan jam tangan Anda yang dapat membacanya. *(draft, to native review (plan 5))* |
| `PRIVACY.ur.md` | ## آپ کی گھڑی | اگر آپ تقویٰ کو اپنی گھڑی میں شامل کریں تو آپ کا فون اسے آپ کا شہر، قبلہ کی سمت اور فاصلہ، آپ کے علاقے کی قطب نما کی درستی، آنے والے مہینے کے نماز کے اوقات جیسے ایپ انہیں لکھتی ہے، اور آپ کے اپنے تسبیح کے کلمات دیتا ہے۔ جب آپ کا فون اور گھڑی براہِ راست جڑے نہ ہوں تو Google (اینڈرائیڈ) یا Apple (iPhone) یہ ڈیٹا انٹرنیٹ کے ذریعے پہنچا سکتا ہے، شروع سے آخر تک خفیہ کاری کے ساتھ، تاکہ اسے صرف آپ کا فون اور آپ کی گھڑی پڑھ سکیں۔ *(draft, to native review (plan 5))* |
| `PRIVACY.bn.md` | ## আপনার ঘড়ি | তাকওয়া আপনার ঘড়িতে যোগ করলে আপনার ফোন তাকে দেয় আপনার শহর, কিবলার দিক ও দূরত্ব, আপনার এলাকার কম্পাস সংশোধন, অ্যাপ যেভাবে লেখে সেভাবে সামনের এক মাসের নামাজের সময়, আর আপনার নিজের তাসবিহর বাক্যগুলো। আপনার ফোন ও ঘড়ি সরাসরি যুক্ত না থাকলে Google (অ্যান্ড্রয়েড) বা Apple (iPhone) এই তথ্য ইন্টারনেটের মাধ্যমে বহন করতে পারে, এন্ড-টু-এন্ড এনক্রিপ্ট করা অবস্থায়, যাতে শুধু আপনার ফোন ও ঘড়িই তা পড়তে পারে। *(draft, to native review (plan 5))* |

## 4. Question 7's lines (item 3), for Mohamed's approval

Each English line is plan 2's question 7 draft; each translation is *draft, to native review (plan 5)*.

### 4a. "Stays on your phone": `about_stays_title`, `about_stays_body`, and the site home page's row (`site/pages/<lang>/home.html:105`, `<b>title</b><span>body</span>`)

| Language | Current title / body | New title / body |
|---|---|---|
| English (`values:154-155`, `en/home.html:105`) | Stays on your phone / Location, settings, bookmarks and counts never leave it. | Stays on your devices / Only your own devices can see your location, settings, bookmarks and counts. |
| Arabic (`values-ar:139-140`, `ar/home.html`) | يبقى على هاتفك / الموقع والإعدادات والعلامات والعدّاد لا تغادره. | يبقى على أجهزتك / لا ترى موقعك وإعداداتك وعلاماتك وعدّادك إلا أجهزتك أنت. |
| French (`values-fr:135-136`, `fr/home.html`) | Tout reste sur votre téléphone / Position, paramètres, signets et compteurs ne le quittent jamais. | Tout reste sur vos appareils / Seuls vos propres appareils peuvent voir votre position, vos paramètres, vos signets et vos compteurs. |
| Turkish (`values-tr:136-137`, `tr/home.html`) | Telefonunuzda kalır / Konum, ayarlar, yer imleri ve sayaçlar telefonunuzdan çıkmaz. | Cihazlarınızda kalır / Konumunuzu, ayarlarınızı, yer imlerinizi ve sayaçlarınızı yalnızca kendi cihazlarınız görebilir. |
| Indonesian (`values-id:129-130`, `values-in:129-130`, `id/home.html`) | Tetap di ponsel Anda / Lokasi, pengaturan, penanda, dan hitungan tidak pernah keluar dari ponsel. | Tetap di perangkat Anda / Hanya perangkat Anda sendiri yang dapat melihat lokasi, pengaturan, penanda, dan hitungan Anda. |
| Urdu (`values-ur:131-132`, `ur/home.html`) | آپ کے فون پر رہتا ہے / مقام، ترتیبات، محفوظ آیات اور گنتی کبھی فون سے باہر نہیں جاتیں۔ | آپ کے آلات پر رہتا ہے / آپ کا مقام، ترتیبات، محفوظ آیات اور گنتی صرف آپ کے اپنے آلات دیکھ سکتے ہیں۔ |
| Bengali (`values-bn:131-132`, `bn/home.html`) | আপনার ফোনেই থাকে / অবস্থান, সেটিংস, বুকমার্ক ও গণনা কখনো ফোন ছেড়ে যায় না। | আপনার ডিভাইসেই থাকে / আপনার অবস্থান, সেটিংস, বুকমার্ক ও গণনা শুধু আপনার নিজের ডিভাইসগুলোই দেখতে পারে। |

### 4b. The policy's "What stays on your phone" (`PRIVACY.md:9-19` and each translation's lines 8-19)

| Language | Heading | First line | "None of this…" |
|---|---|---|---|
| English | What stays on your phone → **What stays on your devices** | Everything the app knows is stored on your device and nowhere else: → **Everything the app knows is stored on your own devices:** | None of this is sent to us or to anyone. → **None of this is sent to us or to anyone else.** |
| Arabic | ما يبقى على هاتفك → ما يبقى على أجهزتك | كل ما يعرفه التطبيق مخزّن على جهازك ولا في أي مكان آخر: → كل ما يعرفه التطبيق مخزّن على أجهزتك أنت: | لا يُرسل شيء من هذا إلينا ولا إلى أي أحد. → لا يُرسل شيء من هذا إلينا ولا إلى أي أحد آخر. |
| French | Ce qui reste sur votre téléphone → Ce qui reste sur vos appareils | Tout ce que l’application sait est enregistré sur votre appareil et nulle part ailleurs : → Tout ce que l’application sait est enregistré sur vos propres appareils : | Rien de tout cela ne nous est envoyé, ni à qui que ce soit. → Rien de tout cela ne nous est envoyé, ni à qui que ce soit d’autre. |
| Turkish | Telefonunuzda kalanlar → Cihazlarınızda kalanlar | Uygulamanın bildiği her şey cihazınızda saklanır, başka hiçbir yerde değil: → Uygulamanın bildiği her şey kendi cihazlarınızda saklanır: | Bunların hiçbiri bize ya da bir başkasına gönderilmez. → unchanged (it already says "anyone else") |
| Indonesian | Apa yang tetap di ponsel Anda → Apa yang tetap di perangkat Anda | Semua yang diketahui aplikasi ini tersimpan di perangkat Anda dan tidak di tempat lain: → Semua yang diketahui aplikasi ini tersimpan di perangkat Anda sendiri: | Tidak satu pun dari semua itu dikirim kepada kami atau kepada siapa pun. → Tidak satu pun dari semua itu dikirim kepada kami atau kepada orang lain. |
| Urdu | جو کچھ آپ کے فون پر رہتا ہے → جو کچھ آپ کے آلات پر رہتا ہے | ایپ جو کچھ جانتی ہے وہ سب آپ ہی کے آلے پر محفوظ ہے، اور کہیں نہیں: → ایپ جو کچھ جانتی ہے وہ سب آپ کے اپنے آلات پر محفوظ ہے: | اس میں سے کچھ بھی ہمیں یا کسی اور کو نہیں بھیجا جاتا۔ → unchanged (it already says "anyone else") |
| Bengali | আপনার ফোনে যা থাকে → আপনার ডিভাইসে যা থাকে | অ্যাপটি যা কিছু জানে তার সবই আপনার ডিভাইসে জমা থাকে, আর কোথাও নয়: → অ্যাপটি যা কিছু জানে তার সবই আপনার নিজের ডিভাইসগুলোতে জমা থাকে: | এর কিছুই আমাদের কাছে বা অন্য কারও কাছে পাঠানো হয় না। → unchanged (it already says "anyone else") |

### 4c. Deleting the app (`PRIVACY.md:20`, each translation's line 18-20)

| Language | Current | New |
|---|---|---|
| English | Deleting the app deletes all of it. | Deleting Taqwa from your phone and your watch deletes all of it. |
| Arabic | وحذف التطبيق يحذف كل ذلك. | وحذف تقوى من هاتفك وساعتك يحذف كل ذلك. |
| French | Supprimer l’application supprime tout cela. | Supprimer Taqwa de votre téléphone et de votre montre supprime tout cela. |
| Turkish | Uygulamayı silmek bunların hepsini siler. | Taqwa'yı telefonunuzdan ve saatinizden silmek bunların hepsini siler. |
| Indonesian | Menghapus aplikasinya menghapus semuanya. | Menghapus Taqwa dari ponsel dan jam tangan Anda menghapus semuanya. |
| Urdu | ایپ کو حذف کرنا یہ سب حذف کر دیتا ہے۔ | تقویٰ کو اپنے فون اور گھڑی سے حذف کرنا یہ سب حذف کر دیتا ہے۔ |
| Bengali | অ্যাপটি মুছে ফেললে এর সবই মুছে যায়। | আপনার ফোন ও ঘড়ি থেকে তাকওয়া মুছে ফেললে এর সবই মুছে যায়। |

### 4d. Backups, one sentence added at the end of the section (`PRIVACY.md:61-67` and each translation's Backups section)

The sentence stays whatever plan 4's simulator check finds (plan 4, Ruling 39).

| Language | Added sentence |
|---|---|
| English | If you add Taqwa to an Apple Watch, iOS keeps the last copy your iPhone sent it, and that copy may be in your iPhone's backup, encrypted by Apple. |
| Arabic | وإن أضفت تقوى إلى Apple Watch، يحتفظ iOS بآخر نسخة أرسلها إليها iPhone، وقد تكون تلك النسخة في النسخ الاحتياطي لجهاز iPhone، مشفّرةً لدى Apple. |
| French | Si vous ajoutez Taqwa à une Apple Watch, iOS conserve la dernière copie que votre iPhone lui a envoyée, et cette copie peut figurer dans la sauvegarde de votre iPhone, chiffrée par Apple. |
| Turkish | Taqwa'yı bir Apple Watch'a eklerseniz iOS, iPhone'unuzun ona gönderdiği son kopyayı saklar; bu kopya iPhone'unuzun yedeğinde, Apple tarafından şifrelenmiş olarak bulunabilir. |
| Indonesian | Jika Anda menambahkan Taqwa ke Apple Watch, iOS menyimpan salinan terakhir yang dikirim iPhone Anda ke jam itu, dan salinan itu mungkin ada di cadangan iPhone Anda, dienkripsi oleh Apple. |
| Urdu | اگر آپ تقویٰ کو Apple Watch میں شامل کریں تو iOS وہ آخری نقل رکھتا ہے جو آپ کے iPhone نے اسے بھیجی، اور وہ نقل آپ کے iPhone کے بیک اپ میں ہو سکتی ہے، Apple کی طرف سے خفیہ کاری شدہ۔ |
| Bengali | তাকওয়া কোনো Apple Watch-এ যোগ করলে আপনার iPhone তাকে সর্বশেষ যে কপিটি পাঠিয়েছে iOS সেটি রেখে দেয়, আর সেই কপি আপনার iPhone-এর ব্যাকআপে থাকতে পারে, Apple-এর এনক্রিপশনে। |

### 4e. Support's "Everything Taqwa knows" (`site/pages/<lang>/support.html:37`, both halves)

| Language | First half: current → new | Second half: current → new |
|---|---|---|
| English | Everything Taqwa knows lives on your phone: → Everything Taqwa knows lives on your own devices: | Uninstalling the app removes all of it. → Uninstalling Taqwa from your phone and your watch removes all of it. |
| Arabic | كل ما يعرفه تقوى يبقى على هاتفك: → كل ما يعرفه تقوى يبقى على أجهزتك أنت: | وإزالة التطبيق تزيل كل ذلك. → وإزالة تقوى من هاتفك وساعتك تزيل كل ذلك. |
| French | Tout ce que Taqwa sait se trouve sur votre téléphone : → Tout ce que Taqwa sait se trouve sur vos propres appareils : | Désinstaller l’application efface tout. → Désinstaller Taqwa de votre téléphone et de votre montre efface tout. |
| Turkish | Taqwa'nın bildiği her şey telefonunuzda kalır: → Taqwa'nın bildiği her şey kendi cihazlarınızda kalır: | Uygulamayı kaldırmak bunların hepsini siler. → Taqwa'yı telefonunuzdan ve saatinizden kaldırmak bunların hepsini siler. |
| Indonesian | Semua yang diketahui Taqwa ada di ponsel Anda: → Semua yang diketahui Taqwa ada di perangkat Anda sendiri: | Mencopot aplikasinya menghapus semuanya. → Mencopot Taqwa dari ponsel dan jam tangan Anda menghapus semuanya. |
| Urdu | تقویٰ جو کچھ جانتا ہے وہ سب آپ ہی کے فون پر رہتا ہے: → تقویٰ جو کچھ جانتا ہے وہ سب آپ کے اپنے آلات پر رہتا ہے: | ایپ ہٹا دینے سے یہ سب چلا جاتا ہے۔ → تقویٰ کو اپنے فون اور گھڑی سے ہٹا دینے سے یہ سب چلا جاتا ہے۔ |
| Bengali | তাকওয়া যা কিছু জানে সবই আপনার ফোনে থাকে: → তাকওয়া যা কিছু জানে সবই আপনার নিজের ডিভাইসগুলোতে থাকে: | অ্যাপটি সরিয়ে ফেললে সবই মুছে যায়। → আপনার ফোন ও ঘড়ি থেকে তাকওয়া সরিয়ে ফেললে সবই মুছে যায়। |

### 4f. Online only when you ask: `about_online_title`, `about_online_body`, and the home page's row (`home.html:106`)

| Language | New title / body |
|---|---|
| English (`values:156-157`; was "Online only when you ask" / "The internet is used for one thing: recitations you choose to download, from Taqwa’s public repository on GitHub.") | Online only for what you choose / The internet is used for recitations you choose to download, from Taqwa’s public repository on GitHub. If you add Taqwa to your watch, Google or Apple may carry its data over the internet, encrypted so only your phone and watch can read it. |
| Arabic (`values-ar:141-142`) | متصل فقط لما تختاره / يُستخدم الإنترنت للتلاوات التي تختار تنزيلها، من مستودع تقوى العام على GitHub. وإن أضفت تقوى إلى ساعتك، فقد تنقل Google أو Apple بياناتها عبر الإنترنت، مشفّرةً فلا يقرؤها إلا هاتفك وساعتك. |
| French (`values-fr:137-138`) | En ligne seulement pour ce que vous choisissez / Internet sert aux récitations que vous choisissez de télécharger, depuis le dépôt public de Taqwa sur GitHub. Si vous ajoutez Taqwa à votre montre, Google ou Apple peut acheminer ses données par Internet, chiffrées pour que seuls votre téléphone et votre montre puissent les lire. |
| Turkish (`values-tr:138-139`) | Yalnızca seçtikleriniz için çevrimiçi / İnternet, Taqwa’nın GitHub’daki herkese açık deposundan indirmeyi seçtiğiniz tilavetler için kullanılır. Taqwa’yı saatinize eklerseniz Google ya da Apple onun verilerini internet üzerinden taşıyabilir; veriler şifrelidir, yalnızca telefonunuz ve saatiniz okuyabilir. |
| Indonesian (`values-id:131-132`, `values-in:131-132`) | Online hanya untuk yang Anda pilih / Internet dipakai untuk tilawah yang Anda pilih untuk diunduh, dari repositori publik Taqwa di GitHub. Jika Anda menambahkan Taqwa ke jam tangan Anda, Google atau Apple dapat membawa datanya lewat internet, terenkripsi sehingga hanya ponsel dan jam tangan Anda yang dapat membacanya. |
| Urdu (`values-ur:133-134`) | آن لائن صرف اس کے لیے جو آپ چنیں / انٹرنیٹ اُن تلاوتوں کے لیے استعمال ہوتا ہے جو آپ خود ڈاؤن لوڈ کرنا چاہیں، GitHub پر تقویٰ کے عوامی مخزن سے۔ اگر آپ تقویٰ کو اپنی گھڑی میں شامل کریں تو Google یا Apple اس کا ڈیٹا انٹرنیٹ کے ذریعے پہنچا سکتا ہے، خفیہ کاری کے ساتھ، تاکہ اسے صرف آپ کا فون اور آپ کی گھڑی پڑھ سکیں۔ |
| Bengali (`values-bn:133-134`) | শুধু আপনার বেছে নেওয়া কাজেই অনলাইন / ইন্টারনেট লাগে আপনি যে তিলাওয়াতগুলো ডাউনলোড করতে চান সেগুলোর জন্য, GitHub-এ তাকওয়ার উন্মুক্ত রিপোজিটরি থেকে। তাকওয়া আপনার ঘড়িতে যোগ করলে Google বা Apple এর তথ্য ইন্টারনেটের মাধ্যমে বহন করতে পারে, এনক্রিপ্ট করা অবস্থায়, যাতে শুধু আপনার ফোন ও ঘড়িই তা পড়তে পারে। |

The site's row in each `home.html` takes the same title and body (the Turkish page with its straight apostrophe).

### 4g. The home page's privacy heading and the end of its lede (`site/pages/<lang>/home.html:100-101`)

| Language | Heading | The lede's last sentence, current → new |
|---|---|---|
| English | Offline by design. Online only for what you choose. | Until then, it makes no network request at all. → Until then, the app itself makes no network request. If you add Taqwa to your watch, Google or Apple may carry its data between your phone and watch over the internet, encrypted so only they can read it. |
| Arabic | دون اتصال بالتصميم. ومتصل فقط لما تختاره. | وحتى تستخدم التلاوة للمرة الأولى، لا يرسل أي طلب عبر الشبكة على الإطلاق. → وحتى تستخدم التلاوة للمرة الأولى، لا يرسل التطبيق نفسه أي طلب عبر الشبكة. وإن أضفت تقوى إلى ساعتك، فقد تنقل Google أو Apple بياناتها بين هاتفك وساعتك عبر الإنترنت، مشفّرةً فلا يقرؤها سواهما. |
| French | Hors ligne par conception. En ligne seulement pour ce que vous choisissez. | Tant que vous n’avez pas utilisé la récitation, elle ne fait aucune requête réseau. → Tant que vous n’avez pas utilisé la récitation, l’application elle-même ne fait aucune requête réseau. Si vous ajoutez Taqwa à votre montre, Google ou Apple peut acheminer ses données entre votre téléphone et votre montre par Internet, chiffrées pour que seuls ces deux appareils puissent les lire. |
| Turkish | Tasarımı gereği çevrimdışı. Yalnızca seçtikleriniz için çevrimiçi. | Tilaveti ilk kez kullanana kadar hiçbir ağ isteği yapmaz. → Tilaveti ilk kez kullanana kadar uygulamanın kendisi hiçbir ağ isteği yapmaz. Taqwa'yı saatinize eklerseniz Google ya da Apple onun verilerini telefonunuzla saatiniz arasında internet üzerinden taşıyabilir; veriler şifrelidir, yalnızca bu ikisi okuyabilir. |
| Indonesian | Offline sejak dirancang. Online hanya untuk yang Anda pilih. | Sampai Anda pertama kali memakai tilawah, Taqwa tidak melakukan permintaan jaringan sama sekali. → Sampai Anda pertama kali memakai tilawah, aplikasi Taqwa sendiri tidak melakukan permintaan jaringan. Jika Anda menambahkan Taqwa ke jam tangan Anda, Google atau Apple dapat membawa datanya antara ponsel dan jam tangan Anda lewat internet, terenkripsi sehingga hanya keduanya yang dapat membacanya. |
| Urdu | بنیادی طور پر آف لائن۔ آن لائن صرف اس کے لیے جو آپ چنیں۔ | جب تک آپ پہلی بار تلاوت استعمال نہ کریں، ایپ کوئی نیٹ ورک درخواست کرتی ہی نہیں۔ → جب تک آپ پہلی بار تلاوت استعمال نہ کریں، ایپ خود کوئی نیٹ ورک درخواست نہیں کرتی۔ اگر آپ تقویٰ کو اپنی گھڑی میں شامل کریں تو Google یا Apple اس کا ڈیٹا آپ کے فون اور گھڑی کے درمیان انٹرنیٹ کے ذریعے پہنچا سکتا ہے، خفیہ کاری کے ساتھ، تاکہ اسے صرف یہی دونوں پڑھ سکیں۔ |
| Bengali | গোড়া থেকেই অফলাইন। শুধু আপনার বেছে নেওয়া কাজেই অনলাইন। | আপনি প্রথমবার তিলাওয়াত ব্যবহার না করা পর্যন্ত অ্যাপটি কোনো নেটওয়ার্ক অনুরোধই করে না। → আপনি প্রথমবার তিলাওয়াত ব্যবহার না করা পর্যন্ত অ্যাপটি নিজে কোনো নেটওয়ার্ক অনুরোধ করে না। তাকওয়া আপনার ঘড়িতে যোগ করলে Google বা Apple এর তথ্য আপনার ফোন ও ঘড়ির মধ্যে ইন্টারনেটের মাধ্যমে বহন করতে পারে, এনক্রিপ্ট করা অবস্থায়, যাতে শুধু এ দুটিই তা পড়তে পারে। |

### 4h. The privacy page's description (`site/pages/<lang>/meta.json`, `privacy.description`, line 28)

| Language | Current | New |
|---|---|---|
| English | What Taqwa stores on your phone, the one thing it uses the internet for, and what it never does. | What Taqwa stores on your devices, what it uses the internet for, and what it never does. |
| Arabic | ما يخزّنه تقوى على هاتفك، والشيء الوحيد الذي يستخدم الإنترنت لأجله، وما لا يفعله أبدًا. | ما يخزّنه تقوى على أجهزتك، وما يستخدم الإنترنت لأجله، وما لا يفعله أبدًا. |
| French | Ce que Taqwa enregistre sur votre téléphone, la seule chose pour laquelle elle utilise Internet, et ce qu’elle ne fait jamais. | Ce que Taqwa enregistre sur vos appareils, ce pour quoi elle utilise Internet, et ce qu’elle ne fait jamais. |
| Turkish | Taqwa'nın telefonunuzda ne sakladığı, interneti kullandığı tek şey ve asla yapmadıkları. | Taqwa'nın cihazlarınızda ne sakladığı, interneti ne için kullandığı ve asla yapmadıkları. |
| Indonesian | Apa yang disimpan Taqwa di ponsel Anda, satu-satunya hal yang membuatnya memakai internet, dan apa yang tidak pernah dilakukannya. | Apa yang disimpan Taqwa di perangkat Anda, untuk apa ia memakai internet, dan apa yang tidak pernah dilakukannya. |
| Urdu | تقویٰ آپ کے فون پر کیا محفوظ رکھتا ہے، انٹرنیٹ کو صرف کس ایک کام کے لیے استعمال کرتا ہے، اور کیا کبھی نہیں کرتا۔ | تقویٰ آپ کے آلات پر کیا محفوظ رکھتا ہے، انٹرنیٹ کو کس کام کے لیے استعمال کرتا ہے، اور کیا کبھی نہیں کرتا۔ |
| Bengali | তাকওয়া আপনার ফোনে কী জমা রাখে, একটিমাত্র কোন কাজে ইন্টারনেট ব্যবহার করে, আর কী কখনোই করে না। | তাকওয়া আপনার ডিভাইসে কী জমা রাখে, কোন কাজে ইন্টারনেট ব্যবহার করে, আর কী কখনোই করে না। |

### 4i. The policy's opening (`PRIVACY.md:5-7`; translations 5-7)

| Language | Current (its second and third sentences) | New |
|---|---|---|
| English | It works offline. The one thing it uses the internet for is downloading Quran recitations, and only when you ask it to. | It works offline. It uses the internet to download Quran recitations, and only when you ask it to. A watch you add Taqwa to may be reached over the internet too, encrypted, as below. |
| Arabic | يعمل دون اتصال بالإنترنت. الشيء الوحيد الذي يستخدم الإنترنت لأجله هو تنزيل تلاوات القرآن، وفقط حين تطلب ذلك. | يعمل دون اتصال بالإنترنت. ويستخدم الإنترنت لتنزيل تلاوات القرآن، وفقط حين تطلب ذلك. والساعة التي تضيف إليها تقوى قد تصلها بياناته عبر الإنترنت أيضًا، مشفّرةً، كما يأتي أدناه. |
| French | Elle fonctionne hors ligne. La seule chose pour laquelle elle utilise Internet est le téléchargement des récitations du Coran, et uniquement lorsque vous le lui demandez. | Elle fonctionne hors ligne. Elle utilise Internet pour télécharger les récitations du Coran, et uniquement lorsque vous le lui demandez. Une montre à laquelle vous ajoutez Taqwa peut aussi être jointe par Internet, de façon chiffrée, comme expliqué plus bas. |
| Turkish | Çevrimdışı çalışır. İnterneti tek bir şey için kullanır: Kur'an tilavetlerini indirmek, o da yalnızca siz istediğinizde. | Çevrimdışı çalışır. İnterneti Kur'an tilavetlerini indirmek için kullanır, o da yalnızca siz istediğinizde. Taqwa'yı eklediğiniz bir saate de, aşağıda anlatıldığı gibi, şifreli olarak internet üzerinden ulaşılabilir. |
| Indonesian | Aplikasi ini berfungsi offline. Satu-satunya hal yang membuatnya memakai internet adalah mengunduh tilawah Al-Qur'an, dan hanya ketika Anda memintanya. | Aplikasi ini berfungsi offline. Ia memakai internet untuk mengunduh tilawah Al-Qur'an, dan hanya ketika Anda memintanya. Jam tangan yang Anda tambahkan Taqwa juga dapat dijangkau lewat internet, secara terenkripsi, seperti dijelaskan di bawah. |
| Urdu | یہ آف لائن کام کرتی ہے۔ انٹرنیٹ کو یہ صرف ایک کام کے لیے استعمال کرتی ہے: قرآن کی تلاوتوں کا ڈاؤن لوڈ، اور وہ بھی صرف اُس وقت جب آپ کہیں۔ | یہ آف لائن کام کرتی ہے۔ انٹرنیٹ کو یہ قرآن کی تلاوتیں ڈاؤن لوڈ کرنے کے لیے استعمال کرتی ہے، اور وہ بھی صرف اُس وقت جب آپ کہیں۔ جس گھڑی میں آپ تقویٰ شامل کریں، اس تک بھی انٹرنیٹ کے ذریعے پہنچا جا سکتا ہے، خفیہ کاری کے ساتھ، جیسا کہ نیچے بیان ہے۔ |
| Bengali | এটি ইন্টারনেট ছাড়াই চলে। ইন্টারনেট এটি একটিমাত্র কাজে ব্যবহার করে: কুরআনের তিলাওয়াত ডাউনলোড, আর তা কেবল আপনি চাইলে। | এটি ইন্টারনেট ছাড়াই চলে। কুরআনের তিলাওয়াত ডাউনলোড করতে এটি ইন্টারনেট ব্যবহার করে, আর তা কেবল আপনি চাইলে। আপনি যে ঘড়িতে তাকওয়া যোগ করেন, তার কাছেও ইন্টারনেটের মাধ্যমে, এনক্রিপ্ট করা অবস্থায়, তথ্য পৌঁছাতে পারে, যেমনটি নিচে বলা আছে। |

### 4j. "Until you first use recitation" (`PRIVACY.md:46-47`; ar 42-43, fr 50-51, tr 46-47, id 49-50, ur 48-49, bn 47-48)

| Language | New (was the same sentence without "itself") |
|---|---|
| English | Until you first use recitation, the app itself makes no network request at all. |
| Arabic | وحتى تستخدم التلاوة للمرة الأولى، لا يرسل التطبيق نفسه أي طلب عبر الشبكة على الإطلاق. |
| French | Tant que vous n’avez pas utilisé la récitation une première fois, l’application elle-même ne fait aucune requête réseau. |
| Turkish | Tilaveti ilk kez kullanana kadar uygulamanın kendisi hiçbir ağ isteği yapmaz. |
| Indonesian | Sampai Anda pertama kali memakai tilawah, aplikasi ini sendiri tidak melakukan permintaan jaringan sama sekali. |
| Urdu | جب تک آپ پہلی بار تلاوت استعمال نہ کریں، ایپ خود کوئی نیٹ ورک درخواست کرتی ہی نہیں۔ |
| Bengali | আপনি প্রথমবার তিলাওয়াত ব্যবহার না করা পর্যন্ত অ্যাপটি নিজে কোনো নেটওয়ার্ক অনুরোধই করে না। |

### 4k. "No third-party SDK" (`PRIVACY.md:74`; ar 69, fr 81, tr 75, id 78, ur 77, bn 75)

| Language | New |
|---|---|
| English | - No third-party SDK that talks to the internet. The Android app's one Google Play services library links your phone to your own watch, and Google may relay that link, encrypted, as above. |
| Arabic | - لا مكتبات من طرف ثالث تتصل بالإنترنت. ومكتبة خدمات Google Play الوحيدة في تطبيق أندرويد تربط هاتفك بساعتك أنت، وقد تنقل Google هذا الاتصال، مشفّرًا، كما سبق. |
| French | - Aucun SDK tiers qui communique avec Internet. La seule bibliothèque des services Google Play de l’application Android relie votre téléphone à votre propre montre, et Google peut relayer ce lien, chiffré, comme indiqué plus haut. |
| Turkish | - İnternete bağlanan üçüncü taraf SDK yok. Android uygulamasındaki tek Google Play hizmetleri kütüphanesi telefonunuzu kendi saatinize bağlar; Google bu bağlantıyı, yukarıda anlatıldığı gibi şifreli olarak aktarabilir. |
| Indonesian | - Tanpa SDK pihak ketiga yang berhubungan dengan internet. Satu-satunya pustaka layanan Google Play di aplikasi Android menghubungkan ponsel Anda dengan jam tangan Anda sendiri, dan Google dapat meneruskan sambungan itu, terenkripsi, seperti di atas. |
| Urdu | - کوئی ایسا فریقِ ثالث SDK نہیں جو انٹرنیٹ سے بات کرے۔ اینڈرائیڈ ایپ کی واحد Google Play سروسز لائبریری آپ کے فون کو آپ کی اپنی گھڑی سے جوڑتی ہے، اور Google یہ رابطہ، خفیہ کاری کے ساتھ، آگے پہنچا سکتا ہے، جیسا کہ اوپر بیان ہوا۔ |
| Bengali | - ইন্টারনেটের সঙ্গে কথা বলে এমন কোনো তৃতীয় পক্ষের SDK নেই। অ্যান্ড্রয়েড অ্যাপের একমাত্র Google Play পরিষেবা লাইব্রেরিটি আপনার ফোনকে আপনার নিজের ঘড়ির সঙ্গে যুক্ত করে, আর Google সেই সংযোগ, এনক্রিপ্ট করা অবস্থায়, পৌঁছে দিতে পারে, যেমনটি ওপরে বলা আছে। |

### 4l. The listings' internet bullet (`docs/store/listing/<lang>.md`: en 50, ar 45, fr 48, tr 48, id 45, ur 47, bn 48)

| Language | New |
|---|---|
| English | • The internet is used for recitation: the audio you ask for, and a daily check of the reciter list once you have used it. A watch you add Taqwa to may be reached over the internet by Google or Apple, encrypted. |
| Arabic | • يستخدم التطبيق الإنترنت للتلاوة: الصوت الذي تطلبه، وفحص يومي لقائمة القرّاء بعد أن تستخدمها. والساعة التي تضيف إليها تقوى قد تصلها Google أو Apple عبر الإنترنت، مشفّرةً. |
| French | • Internet sert à la récitation : l’audio demandé et, dès lors, une vérification quotidienne de la liste des récitateurs. Une montre à laquelle vous ajoutez Taqwa peut être jointe par Internet par Google ou Apple, de façon chiffrée. |
| Turkish | • İnternet tilavet için kullanılır: istediğiniz ses kayıtları ve, tilaveti kullandıktan sonra, kari listesinin günde bir kez denetlenmesi. Taqwa’yı eklediğiniz bir saate Google ya da Apple internet üzerinden, şifreli olarak ulaşabilir. |
| Indonesian | • Internet dipakai untuk tilawah: audio yang Anda minta, dan pemeriksaan harian daftar qari setelah Anda memakainya. Jam tangan yang Anda tambahkan Taqwa dapat dijangkau Google atau Apple lewat internet, secara terenkripsi. |
| Urdu | • انٹرنیٹ تلاوت کے لیے استعمال ہوتا ہے: وہ آڈیو جو آپ خود مانگیں، اور تلاوت استعمال کرنے کے بعد قاریوں کی فہرست کی روزانہ جانچ۔ جس گھڑی میں آپ تقویٰ شامل کریں، Google یا Apple اس تک انٹرنیٹ کے ذریعے، خفیہ کاری کے ساتھ، پہنچ سکتے ہیں۔ |
| Bengali | • ইন্টারনেট ব্যবহার হয় তিলাওয়াতের জন্য: আপনি যে অডিও চান, আর তিলাওয়াত একবার ব্যবহারের পর দিনে একবার কারিদের তালিকা যাচাই। আপনি যে ঘড়িতে তাকওয়া যোগ করেন, Google বা Apple ইন্টারনেটের মাধ্যমে, এনক্রিপ্ট করা অবস্থায়, তার কাছে পৌঁছাতে পারে। |

### 4m. The English-only files

| File | Current | New |
|---|---|---|
| `CONTRIBUTING.md:37` | **Nothing leaves the phone.** | **Nothing leaves the person's own devices.** |
| `CONTRIBUTING.md:29-30` | No network calls beyond the recitation downloads the person asked for, no third-party SDKs that phone home, | No network calls beyond the recitation downloads the person asked for and the link to the person's own watch, no third-party SDKs that phone home (Google's Wear OS library, for the person's own watch, is the one exception), |
| `README.md:204` | keep the constraints (no network calls beyond the recitation downloads the person asked for, no third-party SDKs that phone home) | keep the constraints (no network calls beyond the recitation downloads the person asked for and the link to the person's own watch, no third-party SDKs that phone home (Google's Wear OS library, for the person's own watch, is the one exception)) |
| `SECURITY.md:3-5` | Taqwa's promise is that nothing about you leaves your phone. … The one network use is downloading the Quran recitations you ask for. | Taqwa's promise is that only your own devices can see anything about you. … The one network use is downloading the Quran recitations you ask for; the link to your own watch is Google's or Apple's, end-to-end encrypted. |
| `SECURITY.md:57-59` (found by the sweep, in neither of the plan's lists) | **Data leaving the phone:** anything that sends off the phone what the [privacy policy](PRIVACY.md) says stays on it, such as your location, settings, bookmarks, counts or the crash report. | **Data leaving your own devices:** anything that sends what the [privacy policy](PRIVACY.md) says stays on them, such as your location, settings, bookmarks, counts or the crash report, to anyone or anywhere else. |
| `SECURITY.md:70` | GitHub, Google Play, the App Store, Cloudflare and the phone's own text-to-speech engine | GitHub, Google Play, the App Store, Google's and Apple's watch links, Cloudflare and the phone's own text-to-speech engine |
| `README.md:57` | Settings › About Taqwa says what stays on the phone and what the internet is used for, | Settings › About Taqwa says what stays on your own devices and what the internet is used for, |
| `README.md:112` | The only network use is recitation downloads from a public GitHub repository, on request. | The app's only network use is recitation downloads from a public GitHub repository, on request; the link to the person's own watch is Google's or Apple's. |
| `README.md:208` (with item 1's sentence) | The app makes no network request until you use Quran recitation. … There are no analytics, no crash-reporting SDKs and no third-party SDKs that talk to the internet; | The app itself makes no network request until you use Quran recitation; the link to your own watch may be relayed by Google or Apple, encrypted. … There are no analytics, no crash-reporting SDKs and no third-party SDKs that talk to the internet, apart from Google's watch link on Android; |

### 4n. The stores' review notes (English only)

- **Play and App Store review notes,** `docs/store/listing/en.md:115`: "Nothing is collected or sent anywhere;" becomes "**Nothing is collected, and nothing is sent anywhere but to the user's own watch, if they add Taqwa to it;**". The rest stands. The sweep found the same English paragraph copied into five other listings (`fr.md:113`, `tr.md:113`, `id.md:110`, `ur.md:108`, `bn.md:109`, under "Review notes (App Store and Play, English only)"): they take the same change, so the consoles never get two versions. `ar.md` holds no copy.
- **App Store Connect's Notes field:** plan 5 writes a new dated file in `docs/store/app-review/` (`<date>-notes-<version>.txt`), pastes it into the field, and points `en.md:117` at it (its pointer still names the 29 September file, though the 2 October one is newer). *Since plan 5 Task 9 (30 October 2026) the pointer, now `en.md:157`, names `docs/store/app-review/2026-10-30-notes-1.4.0-40.txt`.* Its whole text, drafted from `2026-10-02-notes-1.0.0-35.txt` with question 7's three changes, is below. **3,992 bytes in UTF-8** (the old file is 3,953), within the field's 4,000; measured with Python on 24 October.

```text
Thank you for reviewing Taqwa.

1. Screen recording
Our reply of 24 September attached a screen recording from a physical iPhone (iOS 27) of version 1.0.0 (32), from first launch through every main feature. Taqwa has no account registration, login or account deletion, no user-generated content, and no paid content or in-app purchases.

2. Purpose and audience
Taqwa is a free companion for Muslims' daily worship: prayer times with adhan reminders, the Qibla, the Quran with translations and recitations, and a tasbeeh counter. It is for Muslims of any age, anywhere, who want one simple, private app. Taqwa calculates everything on the device, works offline, has no ads, and collects no data; it sends only to the user's own Apple Watch.

3. Setup and main features
No account, login or sample files are needed. On first launch, a Continue button opens each system request (location, then notifications); if location is declined, a built-in city search opens instead.
- Prayer tab: today's times, the next prayer and the Hijri date; the (i) after the date says whose times these are. The Qibla card opens the compass (physical device needed) and the Tasbeeh card opens the counter.
- Quran tab: all 114 surahs in Translation or Mushaf view, with search, bookmarks and sharing. Play at the top of a surah offers to download its recitation once, then plays it with lock-screen controls.
- Settings tab: adhan notifications per prayer, the adhan voice, the prayer timetable, location, reciter and read-aloud, appearance, and seven interface languages.
- Widgets: next prayer and today's times, and the ayah of the day, on the Home Screen; next prayer on the Lock Screen.

4. External services
Taqwa has no server of its own and no analytics, advertising, crash-reporting, authentication, payment or AI services. Its own engine calculates prayer times on the device; the open-source Adhan library gives the Qibla bearing. Reminders are local notifications; read-aloud uses the iPhone's on-device voices. The Quran text, translations, city list and fonts are bundled. The only network use is recitation: playing a surah not yet on the phone downloads its MP3 audio from a public GitHub release (github.com/MohamedAbulgasem/Taqwa-data) that mirrors Islamic Network's recitations unmodified, and the reciter list is refreshed there at most once a day. If the user adds Taqwa to their Apple Watch, the iPhone also sends it the month's prayer times, the city, the Qibla's direction and distance and the user's own tasbeeh phrases through WatchConnectivity, which Apple may carry over Wi-Fi or cellular, end-to-end encrypted.

5. Regional differences
Only the default prayer times differ by place. Where an authority publishes the timetable, or most local mosques follow one, Taqwa reproduces it on the device and names it under the (i) (About these times says Taqwa is not affiliated with the authority); where mosques differ, each prayer shows once all have begun it; elsewhere Taqwa calculates on the safe side. Users can choose another in Settings.

6. Third-party material
Taqwa is not in a regulated industry. Its content is used under each provider's published terms and credited in the app under Settings › Attribution & licences:
- Quran text: Tanzil Project, reproduced verbatim as its licence requires (tanzil.net/docs/text_license)
- Translations: Tanzil Project, for non-commercial use (tanzil.net/trans)
- Recitations: Islamic Network, licensed by the reciters for free, non-commercial redistribution at the published bitrates (alquran.cloud/terms-and-conditions, section IV)
- Quran font: KFGQPC Uthmanic Script Hafs (King Fahd Complex), free to use unmodified
- Adhan audio: Wikimedia Commons (CC0, CC BY-SA 4.0, CC BY 3.0); city list: GeoNames (CC BY 4.0)
Taqwa is free, with no ads or purchases, so it stays within these terms. Full list: github.com/MohamedAbulgasem/Taqwa/blob/main/docs/ATTRIBUTION.md.

Privacy policy: https://taqwa.world/privacy/
Support: support@taqwa.world
```

## 5. The lines that stay (item 4), and why

| Line | Why it stays |
|---|---|
| "No account, no ads, no analytics" (`home.html:107`), and the policy's other "never" lines | Still true: the watch link adds none of them. |
| `PRIVACY.md:75` "No sale or sharing of data", `:83` "Taqwa collects no data from anyone, of any age.", `README.md:115` "no data collection" | Taqwa itself receives nothing, and the feed goes only to the person's own watch. Plan 5's re-answer checks these against Play's and Apple's own definitions of collecting and sharing. |
| The crash report's lines: `PRIVACY.md:22-23`, `support.html:12`, `crash_sheet_body` (`strings.xml:174`) | Still true: the crash report never goes to the watch. |
| Read-aloud's: `PRIVACY.md:57`, and the comment at `androidApp/src/main/AndroidManifest.xml:29-30` ("nothing is sent anywhere") | Still true: the read-aloud text never goes to the watch. |
| The Android half of `PRIVACY.md:61-67` (Backups) | Still true: the Android phone keeps no copy of a sent message, and the Wear app opts out of backup as the phone does (its `data_extraction_rules.xml`, plan 1). The iPhone half gains 4d's sentence. Plan 4 keeps the Apple Watch's own stored feed out of backup, or the same paragraph says so. |
| `PRIVACY.md:87`, "If a future version needs the network for anything new, this page will say so before that version ships, with the date above updated" | It stays, and it binds plan 5 to apply this list, with the date, before the build ships. |
| "Offline by design" (`README.md:9`, its badge at `:26`) and "works offline" (`site/pages/en/meta.json:18`) | The app works with no connection. The watch link is optional, and its carrying is the platform's. |
| **Found by the sweep, the same kind:** "works offline" in `onboarding_welcome_body` (`strings.xml:19`, and its six translations), `site/pages/en/home.html:8`, `docs/store/listing/en.md:12` and `:17`, the Indonesian short description `id.md:9` ("Tanpa internet."), the city pages' pitch (`meta.json:93`, `:95`, `:98`); and `README.md:113` "Offline first" | The same reason: everything works without a connection, and a watch nearby is reached over Bluetooth. |
| "Exodus Privacy's report finds no trackers" (`CONTRIBUTING.md:38-39`) and the site's Exodus row | Plan 5 runs Exodus on the first build with a sender; the line stays only if it still finds none. |
| `docs/STORE-PRIVACY.md` (`:17-19`, `:96`, `:122`, and the whole document) and `docs/STORE-CHECKLIST.md:267`, `:321` | Plan 5's re-answer of Play's Data safety and Apple's privacy label (Ruling 39) rewrites them against the stores' definitions. |

## 6. The code comments (item 5)

Each is rewritten to quote "Only your own devices can see your location." by the task that already edits its file. They are comments, not copy, and their reasoning for leaving backup off stands.

| Comment | Rewritten by |
|---|---|
| `androidApp/src/main/AndroidManifest.xml:45-51` | plan 2 Task 5 |
| `androidApp/src/main/res/xml/data_extraction_rules.xml:2-7` | plan 2 Task 5 |
| `wearApp/src/main/AndroidManifest.xml:8-18` | plan 2 Task 6 |
| `wearApp/src/main/res/xml/data_extraction_rules.xml:2-7` | plan 2 Task 6 |
| `shared/src/iosMain/kotlin/world/taqwa/app/settings/DataStoreFactory.ios.kt:14-15` ("your location never leaves your phone" has to include Apple's backup) | plan 2 Task 8 |

## 7. The gate plan 5 checks off before a build with a sender ships

- [ ] Every line of sections 2, 3 and 4, in each of its languages (seven, or English alone for the English-only files), as ruled or as Mohamed approved, the six translations after their native review. The Backups sentence (4d) among them, whatever plan 4's simulator check finds.
- [ ] Indonesian in both `values-id` and `values-in`; `tools/i18n-check.py` green.
- [ ] The policy's date updated, in all seven `PRIVACY*.md`, and the site rebuilt from them.
- [ ] `docs/STORE-PRIVACY.md`'s re-answer of Play's Data safety and Apple's privacy label (Ruling 39).
- [ ] Exodus run on the first sender build; `CONTRIBUTING.md:38-39` and the site's Exodus row stay only if it still finds no trackers.
- [ ] The stores' review notes: `en.md:115` (and its five copies) pasted into both consoles; the new App Store notes file written, pasted into App Store Connect's Notes field (at most 4,000 bytes), and `en.md:117` pointing at it (`en.md:157` since plan 5 Task 9).
- [ ] The five code comments of section 6, by plan 2's Tasks 5, 6 and 8.

## 8. The sweep

Run on 24 October at `838f0c98`, from the worktree root, exactly as the plan writes it:

```bash
git ls-files | grep -vE '^(docs/superpowers|\.superpowers|docs/research|tools)/' \
  | grep -E '\.(md|html|json|xml|strings|txt|plist)$' \
  | grep -vE '/values-[a-z]|\.(ar|bn|fr|id|tr|ur)\.md$|/(ar|bn|fr|id|tr|ur)/|(ar|bn|fr|id|tr|ur)\.lproj/|composeResources/files|vectors/' \
  | tr '\n' '\0' | xargs -0 grep -niE 'leaves? (the|your) phone|never leaves|sent anywhere|nowhere|on your phone|stays on|collect|transmit|uninstall|delet(e|es|ing) the app|removes all|network|internet|offline|third-party|sdk'
```

It printed **196 lines**. Every one is in item 1, 2, 3, 4 or 5, or is about something else. Three promises were in neither of the plan's lists, and are now in them with drafts: `SECURITY.md:57-59` (4m), the review notes' five copies in the other listings (4n), and the "works offline" lines (section 5, which stay).

The other kinds, a sentence each:
- **Features that work offline or use the network by request** (`README.md:46`, `:51-53`, `:130`; `home.html:45`, `:50`; the listings' feature bullets and screenshot captions; `strings.xml:199`, `:658`, `:689`; the shipped release notes): they describe what the app does, and none says where data goes.
- **Credits and third-party material** (`CONTRIBUTING.md:103`, `README.md:212`, `:216`, `docs/ATTRIBUTION.md`, `docs/licences/*`, the listings' credit lines, `home.html:123`, `strings.xml:697`, `:704`, the app-review files' "Third-party material" lines): licences and sources.
- **Build tools and configuration** (`README.md:141`, `docs/MONITOR.md`, the debug manifest and its network security config, the main manifest's `INTERNET` permission and location feature, `docs/STORE-CHECKLIST.md`'s other lines, `watchface.xml:154`, `site/campaigns.json`): code identifiers, SDK levels and campaign names.
- **Scope** (`CONTRIBUTING.md:24`, `README.md:134`): what Taqwa will not build, "anything that needs a server".
- **Records** (`docs/BUILD-LOG.md`, 28 lines; the 23 and 29 September app-review files): the history of what was built and sent, which is not rewritten; its lines 1030 and 1119-1126 quote the old promise as it stood then.
- **The watch's own words** (`wearApp/src/main/res/values/strings.xml:9`, `open_taqwa_on_phone`): a message, not a promise.

The promise lines, one at a time:

| Hit | Excerpt | Item |
|---|---|---|
| `CONTRIBUTING.md:29` | No network calls beyond the recitation downloads the person asked for, no third-party SDKs that | 3 (4m, the constraints) |
| `CONTRIBUTING.md:37` | - **Nothing leaves the phone.** No analytics, no crash-reporting SDKs, no account. There is no | 3 (4m) |
| `PRIVACY.md:5` | Taqwa is a free Islamic app with no account, no ads and no analytics. It works offline. | 3 (4i) |
| `PRIVACY.md:6` | The one thing it uses the internet for is downloading Quran recitations, and only when you | 3 (4i) |
| `PRIVACY.md:9` | ## What stays on your phone | 3 (4b) |
| `PRIVACY.md:11` | Everything the app knows is stored on your device and nowhere else: | 3 (4b) |
| `PRIVACY.md:20` | create. Deleting the app deletes all of it. | 3 (4c) |
| `PRIVACY.md:22` | If Taqwa crashes, it saves a technical report on your phone (the app version, your phone model | 4 (the crash report) |
| `PRIVACY.md:23` | and the error, nothing else). It never leaves the phone unless you choose to email it to us. | 4 (the crash report) |
| `PRIVACY.md:29` | permission. Your location is used on the device and never transmitted. | 1 (section 2) |
| `PRIVACY.md:46` | nothing but your IP address. Until you first use recitation, the app makes no network | 3 (4j) |
| `PRIVACY.md:57` | privacy terms. Taqwa picks only voices that work offline and sends the text nowhere itself. If | 4 (read-aloud) |
| `PRIVACY.md:74` | - No third-party SDK that talks to the internet. | 3 (4k) |
| `PRIVACY.md:83` | Taqwa collects no data from anyone, of any age. | 4 (collects no data) |
| `PRIVACY.md:87` | If a future version needs the network for anything new, this page will say so before that | 4 (the Changes promise) |
| `README.md:9` | Free forever. No ads, no account, offline by design. | 4 (offline by design) |
| `README.md:26` | <img alt="Offline by design" src="https://img.shields.io/badge/offline-by_design-B5820B"> | 4 (offline by design, the badge) |
| `README.md:57` | - **Privacy you can check.** Settings › About Taqwa says what stays on the phone and what the internet is u... | 3 (4m) |
| `README.md:112` | - **Zero recurring cost.** Nothing in the app contacts a server of ours; there is none. The only network us... | 3 (4m) |
| `README.md:113` | - **Offline first.** Prayer times and Qibla are mathematics; the city database and the Quran ship inside th... | 4 (offline first; found by the sweep) |
| `README.md:115` | - **Nothing to sell.** No ads, no premium tier, no data collection, no account. | 4 (no data collection) |
| `README.md:204` | Contributions are welcome, from a typo in the Arabic strings to an authority's timetable. Open an issue fir... | 3 (4m, the constraints) |
| `README.md:208` | Taqwa asks for your location only to compute prayer times and the Qibla, and only if you choose to allow it... | 1 (section 2) and 3 (4m) |
| `SECURITY.md:3` | Taqwa's promise is that nothing about you leaves your phone. There is no account, no analytics | 3 (4m) |
| `SECURITY.md:4` | and no tracking, and there is no server behind the app at all. The one network use is | 3 (4m) |
| `SECURITY.md:59` | [privacy policy](PRIVACY.md) says stays on it, such as your location, settings, bookmarks, | 3 (4m; found by the sweep) |
| `SECURITY.md:70` | - **Third-party services:** GitHub, Google Play, the App Store, Cloudflare and the phone's own | 3 (4m) |
| `androidApp/src/main/AndroidManifest.xml:30` | Samsung's — only when it says it looks for them. No permission; nothing is sent anywhere. --> | 4 (read-aloud's comment) |
| `androidApp/src/main/AndroidManifest.xml:47` | chosen city), and "your location never leaves your phone" has to include Google's | 5 (Task 5) |
| `androidApp/src/main/res/xml/data_extraction_rules.xml:5` | allowBackup: the settings file holds the reader's location, and "your location never leaves your | 5 (Task 5) |
| `docs/STORE-CHECKLIST.md:267` | safety (does not collect or share any user data); Government app (no); Financial features | 4 (plan 5's re-answer) |
| `docs/STORE-CHECKLIST.md:321` | no data collected → Publish ("Data Not Collected"). **Trader status** when the banner asks: | 4 (plan 5's re-answer) |
| `docs/STORE-PRIVACY.md:5` | network inventory in 'docs/superpowers/specs/2026-09-13-taqwa-privacy-about-design.md' §1 and | 4 (plan 5's re-answer) |
| `docs/STORE-PRIVACY.md:11` | store-readiness sweep (targetSdk 36, 0.16.0). | 4 (plan 5's re-answer) |
| `docs/STORE-PRIVACY.md:17` | \| Does your app collect or share any of the required user data types? \| **No** \| Play defines "collected... | 4 (plan 5's re-answer) |
| `docs/STORE-PRIVACY.md:18` | \| Is all user data encrypted in transit? \| n/a \| Nothing is collected. (All traffic is HTTPS regardless.... | 4 (plan 5's re-answer) |
| `docs/STORE-PRIVACY.md:19` | \| Do you provide a way to request data deletion? \| n/a \| Nothing is collected. \| | 4 (plan 5's re-answer) |
| `docs/STORE-PRIVACY.md:28` | - **Foreground service permissions** (targetSdk 34+ requires a declaration and a short video for | 4 (plan 5's re-answer) |
| `docs/STORE-PRIVACY.md:43` | - 'INTERNET' — recitation downloads and the catalogue check, on request only. | 4 (plan 5's re-answer) |
| `docs/STORE-PRIVACY.md:52` | - 'ACCESS_NETWORK_STATE', 'WAKE_LOCK' — merged from WorkManager: the Wi-Fi-only download | 4 (plan 5's re-answer) |
| `docs/STORE-PRIVACY.md:66` | - **Hardware features:** 'android.hardware.location', '.location.network', '.location.gps' and | 4 (plan 5's re-answer) |
| `docs/STORE-PRIVACY.md:75` | is collected or sent automatically, so the data-safety and App Privacy answers are unchanged | 4 (plan 5's re-answer) |
| `docs/STORE-PRIVACY.md:76` | ("Data Not Collected"); the policy's paragraph on it exists in all seven languages. | 4 (plan 5's re-answer) |
| `docs/STORE-PRIVACY.md:79` | phone (on Android a voice its engine marks as needing no connection and not network synthesis, | 4 (plan 5's re-answer) |
| `docs/STORE-PRIVACY.md:80` | 'isOfflineVoice' in 'SpeechVoices.android.kt'; on iOS Apple's installed voices) and sends the | 4 (plan 5's re-answer) |
| `docs/STORE-PRIVACY.md:81` | text nowhere itself. On Android the engine is a separate app, Google's or Samsung's, under its | 4 (plan 5's re-answer) |
| `docs/STORE-PRIVACY.md:84` | unchanged ("Data Not Collected"), and 'AVSpeechSynthesizer' is not a required-reason API. The | 4 (plan 5's re-answer) |
| `docs/STORE-PRIVACY.md:96` | - **App Privacy:** Data Not Collected. Same definition as Play: nothing leaves the device except | 4 (plan 5's re-answer) |
| `docs/STORE-PRIVACY.md:99` | 'iosApp/TaqwaWidget/PrivacyInfo.xcprivacy' (widget extension) declare tracking off, no collected | 4 (plan 5's re-answer) |
| `docs/STORE-PRIVACY.md:108` | - **Content rights:** Yes, the app contains third-party content and you have the rights. | 4 (plan 5's re-answer) |
| `docs/STORE-PRIVACY.md:109` | Recitations under the Islamic Network's non-commercial redistribution terms (plus one bundled | 4 (plan 5's re-answer) |
| `docs/STORE-PRIVACY.md:122` | - **Location:** 'NSLocationWhenInUseUsageDescription' says the location is never sent anywhere, | 4 (plan 5's re-answer) |
| `docs/store/app-review/2026-10-02-notes-1.0.0-35.txt:7` | Taqwa is a free companion for Muslims' daily worship: prayer times with adhan reminders, the Qibla, the Qur... | 3 (4n, the new notes file) |
| `docs/store/app-review/2026-10-02-notes-1.0.0-35.txt:17` | Taqwa has no server of its own and no analytics, advertising, crash-reporting, authentication, payment or A... | 3 (4n, the new notes file) |
| `docs/store/listing/bn.md:109` | No account or sign-in. Location is optional: Settings › Location lets a reviewer choose any city instead. E... | 3 (4n, a copy; found by the sweep) |
| `docs/store/listing/en.md:12` | Prayer times, adhan, Quran with recitation, adhkar and Qibla. Works offline. | 4 (works offline; found by the sweep) |
| `docs/store/listing/en.md:17` | Taqwa gives you the essentials of the day in one quiet place: prayer times where you are, a takbir or the o... | 4 (works offline; found by the sweep) |
| `docs/store/listing/en.md:49` | • Your location is used on your phone to calculate times and never sent anywhere. | 1 (section 2) |
| `docs/store/listing/en.md:50` | • The internet is used only for recitation: the audio you ask for, and a daily check of the reciter list on... | 3 (4l) |
| `docs/store/listing/en.md:115` | No account or sign-in. Location is optional: Settings › Location lets a reviewer choose any city instead. E... | 3 (4n) |
| `docs/store/listing/en.md:117` | App Store Connect's Notes field carries the longer answers to App Review's information request of 23 Septem... | 3 (4n, the pointer) |
| `docs/store/listing/fr.md:48` | • Internet ne sert qu’à la récitation : l’audio demandé et, dès lors, une vérification quotidienne de la li... | 3 (4l) |
| `docs/store/listing/fr.md:113` | No account or sign-in. Location is optional: Settings › Location lets a reviewer choose any city instead. E... | 3 (4n, a copy; found by the sweep) |
| `docs/store/listing/id.md:9` | Waktu salat, azan, Al-Qur’an dengan tilawah, zikir, dan kiblat. Tanpa internet. | 4 (works offline, "Tanpa internet"; found by the sweep) |
| `docs/store/listing/id.md:45` | • Internet hanya dipakai untuk tilawah: audio yang Anda minta, dan pemeriksaan harian daftar qari setelah A... | 3 (4l) |
| `docs/store/listing/id.md:110` | No account or sign-in. Location is optional: Settings › Location lets a reviewer choose any city instead. E... | 3 (4n, a copy; found by the sweep) |
| `docs/store/listing/tr.md:113` | No account or sign-in. Location is optional: Settings › Location lets a reviewer choose any city instead. E... | 3 (4n, a copy; found by the sweep) |
| `docs/store/listing/ur.md:108` | No account or sign-in. Location is optional: Settings › Location lets a reviewer choose any city instead. E... | 3 (4n, a copy; found by the sweep) |
| `iosApp/iosApp/Info.plist:43` | <string>Taqwa uses your location to calculate prayer times on your device. Your location is never sent anyw... | 1 (section 2) |
| `iosApp/iosApp/en.lproj/InfoPlist.strings:3` | "NSLocationWhenInUseUsageDescription" = "Taqwa uses your location to calculate prayer times on your device.... | 1 (section 2) |
| `shared/src/commonMain/composeResources/values/strings.xml:19` | <string name="onboarding_welcome_body">Prayer times, qibla and the Quran. Free forever. No ads, no account,... | 4 (works offline; found by the sweep) |
| `shared/src/commonMain/composeResources/values/strings.xml:22` | <string name="onboarding_location_body">Prayer times depend on where you are. Everything is calculated on y... | 1 (section 2) |
| `shared/src/commonMain/composeResources/values/strings.xml:154` | <string name="about_stays_title">Stays on your phone</string> | 3 (4a) |
| `shared/src/commonMain/composeResources/values/strings.xml:157` | <string name="about_online_body">The internet is used for one thing: recitations you choose to download, fr... | 3 (4f) |
| `shared/src/commonMain/composeResources/values/strings.xml:174` | <string name="crash_sheet_body">A technical report was saved on your phone. Sending it to support@taqwa.wor... | 4 (the crash report) |
| `shared/src/commonMain/composeResources/values/strings.xml:193` | <string name="location_privacy_note">Coordinates are stored on your device and used only to calculate times... | 1 (section 2) |
| `site/pages/en/home.html:8` | <p class="lede"><strong>Free forever.</strong> No ads, no account, works offline. For Android and iPhone.</p> | 4 (works offline; found by the sweep) |
| `site/pages/en/home.html:100` | <h2>Offline by design. Online only when you ask.</h2> | 3 (4g) |
| `site/pages/en/home.html:101` | <p class="lede" style="margin-bottom: 0;">There is no server behind Taqwa and no account to create. The one... | 3 (4g) |
| `site/pages/en/home.html:105` | <div class="row"><b>Stays on your phone</b><span>Location, settings, bookmarks and counts never leave it.</... | 3 (4a) |
| `site/pages/en/home.html:106` | <div class="row"><b>Online only when you ask</b><span>The internet is used for one thing: recitations you c... | 3 (4f) |
| `site/pages/en/meta.json:18` | "description": "Free, open-source Islamic app for Android and iPhone: prayer times, adhan, Qibla, Quran wit... | 4 (works offline) |
| `site/pages/en/meta.json:28` | "description": "What Taqwa stores on your phone, the one thing it uses the internet for, and what it never ... | 3 (4h) |
| `site/pages/en/meta.json:93` | "pitch_body": "These minutes, a takbir or the opening of the adhan at each prayer, offline, free, no ads.", | 4 (works offline; found by the sweep) |
| `site/pages/en/meta.json:95` | "app_body": "Taqwa calls you to every prayer in {city}, works offline, and never shows an ad. These minutes... | 4 (works offline; found by the sweep) |
| `site/pages/en/meta.json:98` | "app_point_qibla": "Qibla\| corrected to true north, and the whole Quran offline", | 4 (works offline; found by the sweep) |
| `site/pages/en/support.html:12` | <p>If the app closed unexpectedly, it keeps a short technical report on your phone and offers to email it t... | 4 (the crash report) |
| `site/pages/en/support.html:37` | <p>Everything Taqwa knows lives on your phone: your location or chosen city, your settings, bookmarks, adhk... | 3 (4e) |
| `wearApp/src/main/AndroidManifest.xml:8` | <!-- No permission is declared, and the ones that must never be here are the network's and the | 5 (Task 6) |
| `wearApp/src/main/AndroidManifest.xml:9` | location ones (spec 2.1): the times travel phone to watch over the Data Layer and nowhere | 5 (Task 6) |
| `wearApp/src/main/AndroidManifest.xml:15` | never leaves your phone" has to include the backup. data_extraction_rules.xml says the | 5 (Task 6) |
| `wearApp/src/main/res/xml/data_extraction_rules.xml:5` | allowBackup: the feed names the person's city, and "your location never leaves your phone" has | 5 (Task 6) |

Of the 196 lines: 7 in item 1, 36 in item 3, 43 in item 4, 6 in item 5, 104 about something else. Unclassified: 0.

The excerpts are cut at 110 characters; the lines classed "about something else" (104) are the kinds listed above. No line is in neither list.
