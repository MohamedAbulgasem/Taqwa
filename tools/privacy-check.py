#!/usr/bin/env python3
"""The watch release's privacy gate (plan 5 Task 9; Ruling 39, as amended).

Once a phone sends its feed to a watch, the old promises ("Your location is never sent anywhere",
"never leaves your phone", "the one thing it uses the internet for") stop being true. Plan 2's
report, docs/superpowers/reports/2026-10-23-watch-privacy-wording.md, lists every line that
changes, in each of its languages. This script holds one row per line of that report: the file,
the language, the old text that must be gone, the new text that must be there, and whether the
new text is ruled or approved ("approved") or still waits for Mohamed's approval ("awaiting").
Since M3 (31 October 2026), when he approved question 7's lines, every row is approved. It fails
on:

- a row whose old text is still there, or whose new text is not (whitespace and line breaks do
  not count, so a wrapped Markdown paragraph reads as one line);
- a row still awaiting his approval (with --allow-awaiting, a warning instead);
- a PRIVACY*.md whose "Last updated" date is before the watch branch's date;
- a line of plan 2's sweep (its Task 3 Step 7 grep) that is in none of the report's items 1-5,
  nor of its kinds of line about something else (KNOWN below): a promise nobody has looked at.
  Only a row's own words are the row's (Ruling 69 item 1): on a line that holds a row's new text,
  the words beside it are swept as any other line is, and only a KNOWN entry that matches all of
  them excuses them.

    python3 tools/privacy-check.py                    # the cut gate and scripts/test.sh: no row awaiting
    python3 tools/privacy-check.py --allow-awaiting   # only for a row added later, before he answers
    python3 tools/privacy-check.py --pages            # the public pages' rows and files only
    python3 tools/privacy-check.py --pages --root <dir>   # another tree, e.g. origin/main

--pages checks only the rows of the public pages (PRIVACY*.md, site/pages/, README.md,
CONTRIBUTING.md, SECURITY.md) and sweeps only those files, so that the public pages' commit can
be checked on main alone (Task 9 Step 5). Its own tests: python3 tools/test_privacy_check.py.

It needs no git checkout: in a copy that is no git work tree of its own (a git-archive copy, a
source zip), the sweep walks the tree instead of asking git, skipping what .gitignore ignores. It
fails, by the file and line, on a .gitignore it cannot read as git does (a negation, a **, an
escape, or a .gitignore below the root outside the folders the sweep skips), rather than sweep
other files than git would list.

Mohamed answered M3 on 31 October 2026, and each awaiting row took the text he approved, in every
language, and became "approved". A row added later that waits for him is "awaiting" until he
answers, and fails the gate, which scripts/test.sh runs strict since M3. A line added to any of
these files that the sweep catches is added to a row or to KNOWN, with the report's reason, in
the same commit.
"""
import argparse
import collections
import datetime
import fnmatch
import os
import re
import subprocess
import sys
import unicodedata

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# watch-companion branched off main 1dd3208f on 19 October 2026 (the spec's first line): a policy
# dated before it cannot describe the watch.
WATCH_BRANCH_DATE = datetime.date(2026, 10, 19)

APPROVED, AWAITING = "approved", "awaiting"
Row = collections.namedtuple("Row", "id status file lang old new")

PUBLIC = re.compile(r"(PRIVACY(\.(ar|bn|fr|id|tr|ur))?\.md|site/pages/.+|README\.md|CONTRIBUTING\.md|SECURITY\.md)")
POLICIES = ["PRIVACY.md"] + [f"PRIVACY.{lang}.md" for lang in ("ar", "bn", "fr", "id", "tr", "ur")]

# ---------------------------------------------------------------------------------------------
# The rows: one per line of the report, in its order. Item 1 (the location line) and item 2's
# relay sentence are Ruling 39's words, so they were approved as ruled; item 2's heading and
# lead-in and every line of item 3 are question 7's, which Mohamed approved at M3 (31 October
# 2026: "M3 approved, with your two suggested tweaks (the opening and the backups sentence)"):
# each as drafted, but the policy's opening (4i) and the Backups section's earlier sentence (4d),
# which take his words. Every translation has had its native review (plan 5 Task 11): French,
# Turkish, Indonesian, Urdu and Bengali by native readers (Ruling 71), and the Arabic by Mohamed
# at M8 (6 November 2026), and no file marks one as a draft any more.
# ---------------------------------------------------------------------------------------------
ROWS = [Row(*r) for r in [
    # Item 1, the location line, as ruled: "Only your own devices can see your location." takes
    # the old clause's place, and the rest of each text stands.
    ('1-location-permission', APPROVED, 'iosApp/iosApp/Info.plist', 'en',
     '<string>Taqwa uses your location to calculate prayer times on your device. Your location is never sent anywhere.</string>',
     '<string>Taqwa uses your location to calculate prayer times on your device. Only your own devices can see your location.</string>'),
    ('1-location-permission', APPROVED, 'iosApp/iosApp/en.lproj/InfoPlist.strings', 'en',
     '"NSLocationWhenInUseUsageDescription" = "Taqwa uses your location to calculate prayer times on your device. Your location is never sent anywhere.";',
     '"NSLocationWhenInUseUsageDescription" = "Taqwa uses your location to calculate prayer times on your device. Only your own devices can see your location.";'),
    ('1-location-permission', APPROVED, 'iosApp/iosApp/ar.lproj/InfoPlist.strings', 'ar',
     '"NSLocationWhenInUseUsageDescription" = "يستخدم تقوى موقعك لحساب مواقيت الصلاة على جهازك. موقعك لا يغادر هاتفك أبدًا.";',
     '"NSLocationWhenInUseUsageDescription" = "يستخدم تقوى موقعك لحساب مواقيت الصلاة على جهازك. لا ترى موقعك إلا أجهزتك أنت.";'),
    ('1-location-permission', APPROVED, 'iosApp/iosApp/fr.lproj/InfoPlist.strings', 'fr',
     '"NSLocationWhenInUseUsageDescription" = "Taqwa utilise votre position pour calculer les horaires de prière sur votre appareil. Votre position n’est jamais envoyée nulle part.";',
     '"NSLocationWhenInUseUsageDescription" = "Taqwa utilise votre position pour calculer les horaires de prière sur votre appareil. Seuls vos propres appareils peuvent voir votre position.";'),
    ('1-location-permission', APPROVED, 'iosApp/iosApp/tr.lproj/InfoPlist.strings', 'tr',
     '"NSLocationWhenInUseUsageDescription" = "Taqwa, namaz vakitlerini cihazınızda hesaplamak için konumunuzu kullanır. Konumunuz hiçbir yere gönderilmez.";',
     '"NSLocationWhenInUseUsageDescription" = "Taqwa, namaz vakitlerini cihazınızda hesaplamak için konumunuzu kullanır. Konumunuzu yalnızca kendi cihazlarınız görebilir.";'),
    ('1-location-permission', APPROVED, 'iosApp/iosApp/id.lproj/InfoPlist.strings', 'id',
     '"NSLocationWhenInUseUsageDescription" = "Taqwa memakai lokasi Anda untuk menghitung waktu salat di perangkat Anda. Lokasi Anda tidak pernah dikirim ke mana pun.";',
     '"NSLocationWhenInUseUsageDescription" = "Taqwa memakai lokasi Anda untuk menghitung waktu salat di perangkat Anda. Hanya perangkat Anda sendiri yang dapat melihat lokasi Anda.";'),
    ('1-location-permission', APPROVED, 'iosApp/iosApp/ur.lproj/InfoPlist.strings', 'ur',
     '"NSLocationWhenInUseUsageDescription" = "تقویٰ آپ کے مقام سے آپ ہی کے آلے پر نماز کے اوقات نکالتا ہے۔ آپ کا مقام کبھی کہیں نہیں بھیجا جاتا۔";',
     '"NSLocationWhenInUseUsageDescription" = "تقویٰ آپ کے مقام سے آپ ہی کے آلے پر نماز کے اوقات نکالتا ہے۔ آپ کا مقام صرف آپ کے اپنے آلات دیکھ سکتے ہیں۔";'),
    ('1-location-permission', APPROVED, 'iosApp/iosApp/bn.lproj/InfoPlist.strings', 'bn',
     '"NSLocationWhenInUseUsageDescription" = "তাকওয়া আপনার ডিভাইসেই নামাজের সময় হিসাব করতে আপনার অবস্থান ব্যবহার করে। আপনার অবস্থান কখনো কোথাও পাঠানো হয় না।";',
     '"NSLocationWhenInUseUsageDescription" = "তাকওয়া আপনার ডিভাইসেই নামাজের সময় হিসাব করতে আপনার অবস্থান ব্যবহার করে। আপনার অবস্থান শুধু আপনার নিজের ডিভাইসগুলোই দেখতে পারে।";'),
    ('1-onboarding-location-body', APPROVED, 'shared/src/commonMain/composeResources/values/strings.xml', 'en',
     '<string name="onboarding_location_body">Prayer times depend on where you are. Everything is calculated on your device. Your location never leaves your phone.</string>',
     '<string name="onboarding_location_body">Prayer times depend on where you are. Everything is calculated on your device. Only your own devices can see your location.</string>'),
    ('1-onboarding-location-body', APPROVED, 'shared/src/commonMain/composeResources/values-ar/strings.xml', 'ar',
     '<string name="onboarding_location_body">تعتمد المواقيت على موقعك. كل الحساب يجري على جهازك، وموقعك لا يغادر هاتفك.</string>',
     '<string name="onboarding_location_body">تعتمد المواقيت على موقعك. كل الحساب يجري على جهازك. لا ترى موقعك إلا أجهزتك أنت.</string>'),
    ('1-onboarding-location-body', APPROVED, 'shared/src/commonMain/composeResources/values-fr/strings.xml', 'fr',
     '<string name="onboarding_location_body">Les horaires dépendent de votre position. Tout est calculé sur votre appareil, et votre position ne quitte jamais votre téléphone.</string>',
     '<string name="onboarding_location_body">Les horaires dépendent de votre position. Tout est calculé sur votre appareil. Seuls vos propres appareils peuvent voir votre position.</string>'),
    ('1-onboarding-location-body', APPROVED, 'shared/src/commonMain/composeResources/values-tr/strings.xml', 'tr',
     '<string name="onboarding_location_body">Namaz vakitleri konumunuza bağlıdır. Her şey cihazınızda hesaplanır. Konumunuz telefonunuzdan hiç çıkmaz.</string>',
     '<string name="onboarding_location_body">Namaz vakitleri konumunuza bağlıdır. Her şey cihazınızda hesaplanır. Konumunuzu yalnızca kendi cihazlarınız görebilir.</string>'),
    ('1-onboarding-location-body', APPROVED, 'shared/src/commonMain/composeResources/values-id/strings.xml', 'id',
     '<string name="onboarding_location_body">Waktu salat bergantung pada posisi Anda. Semuanya dihitung di perangkat Anda. Lokasi Anda tidak pernah keluar dari ponsel.</string>',
     '<string name="onboarding_location_body">Waktu salat bergantung pada posisi Anda. Semuanya dihitung di perangkat Anda. Hanya perangkat Anda sendiri yang dapat melihat lokasi Anda.</string>'),
    ('1-onboarding-location-body', APPROVED, 'shared/src/commonMain/composeResources/values-in/strings.xml', 'id',
     '<string name="onboarding_location_body">Waktu salat bergantung pada posisi Anda. Semuanya dihitung di perangkat Anda. Lokasi Anda tidak pernah keluar dari ponsel.</string>',
     '<string name="onboarding_location_body">Waktu salat bergantung pada posisi Anda. Semuanya dihitung di perangkat Anda. Hanya perangkat Anda sendiri yang dapat melihat lokasi Anda.</string>'),
    ('1-onboarding-location-body', APPROVED, 'shared/src/commonMain/composeResources/values-ur/strings.xml', 'ur',
     '<string name="onboarding_location_body">نماز کے اوقات آپ کے مقام پر منحصر ہیں۔ سارا حساب آپ کے فون پر ہوتا ہے، اور آپ کا مقام کبھی فون سے باہر نہیں جاتا۔</string>',
     '<string name="onboarding_location_body">نماز کے اوقات آپ کے مقام پر منحصر ہیں۔ سارا حساب آپ کے فون پر ہوتا ہے۔ آپ کا مقام صرف آپ کے اپنے آلات دیکھ سکتے ہیں۔</string>'),
    ('1-onboarding-location-body', APPROVED, 'shared/src/commonMain/composeResources/values-bn/strings.xml', 'bn',
     '<string name="onboarding_location_body">নামাজের সময় আপনার অবস্থানের উপর নির্ভর করে। সব হিসাব আপনার ডিভাইসেই হয়, আর আপনার অবস্থান কখনো ফোন ছেড়ে যায় না।</string>',
     '<string name="onboarding_location_body">নামাজের সময় আপনার অবস্থানের উপর নির্ভর করে। সব হিসাব আপনার ডিভাইসেই হয়। আপনার অবস্থান শুধু আপনার নিজের ডিভাইসগুলোই দেখতে পারে।</string>'),
    ('1-location-privacy-note', APPROVED, 'shared/src/commonMain/composeResources/values/strings.xml', 'en',
     '<string name="location_privacy_note">Coordinates are stored on your device and used only to calculate times. Nothing is sent anywhere.</string>',
     '<string name="location_privacy_note">Coordinates are stored on your device and used only to calculate times. Only your own devices can see your location.</string>'),
    ('1-location-privacy-note', APPROVED, 'shared/src/commonMain/composeResources/values-ar/strings.xml', 'ar',
     '<string name="location_privacy_note">تُحفظ الإحداثيات على جهازك وتُستخدم لحساب المواقيت فقط. لا يُرسل شيء إلى أي جهة.</string>',
     '<string name="location_privacy_note">تُحفظ الإحداثيات على جهازك وتُستخدم لحساب المواقيت فقط. لا ترى موقعك إلا أجهزتك أنت.</string>'),
    ('1-location-privacy-note', APPROVED, 'shared/src/commonMain/composeResources/values-fr/strings.xml', 'fr',
     '<string name="location_privacy_note">Les coordonnées sont enregistrées sur votre appareil et servent uniquement à calculer les horaires. Rien n’est envoyé nulle part.</string>',
     '<string name="location_privacy_note">Les coordonnées sont enregistrées sur votre appareil et servent uniquement à calculer les horaires. Seuls vos propres appareils peuvent voir votre position.</string>'),
    ('1-location-privacy-note', APPROVED, 'shared/src/commonMain/composeResources/values-tr/strings.xml', 'tr',
     '<string name="location_privacy_note">Koordinatlar cihazınızda saklanır ve yalnızca vakitleri hesaplamak için kullanılır. Hiçbir yere hiçbir şey gönderilmez.</string>',
     '<string name="location_privacy_note">Koordinatlar cihazınızda saklanır ve yalnızca vakitleri hesaplamak için kullanılır. Konumunuzu yalnızca kendi cihazlarınız görebilir.</string>'),
    ('1-location-privacy-note', APPROVED, 'shared/src/commonMain/composeResources/values-id/strings.xml', 'id',
     '<string name="location_privacy_note">Koordinat disimpan di perangkat Anda dan hanya dipakai untuk menghitung waktu salat. Tidak ada yang dikirim ke mana pun.</string>',
     '<string name="location_privacy_note">Koordinat disimpan di perangkat Anda dan hanya dipakai untuk menghitung waktu salat. Hanya perangkat Anda sendiri yang dapat melihat lokasi Anda.</string>'),
    ('1-location-privacy-note', APPROVED, 'shared/src/commonMain/composeResources/values-in/strings.xml', 'id',
     '<string name="location_privacy_note">Koordinat disimpan di perangkat Anda dan hanya dipakai untuk menghitung waktu salat. Tidak ada yang dikirim ke mana pun.</string>',
     '<string name="location_privacy_note">Koordinat disimpan di perangkat Anda dan hanya dipakai untuk menghitung waktu salat. Hanya perangkat Anda sendiri yang dapat melihat lokasi Anda.</string>'),
    ('1-location-privacy-note', APPROVED, 'shared/src/commonMain/composeResources/values-ur/strings.xml', 'ur',
     '<string name="location_privacy_note">آپ کے مقام کا طول و عرض آپ کے فون پر محفوظ رہتا ہے اور صرف اوقات کے حساب کے لیے استعمال ہوتا ہے۔ کچھ بھی کہیں نہیں بھیجا جاتا۔</string>',
     '<string name="location_privacy_note">آپ کے مقام کا طول و عرض آپ کے فون پر محفوظ رہتا ہے اور صرف اوقات کے حساب کے لیے استعمال ہوتا ہے۔ آپ کا مقام صرف آپ کے اپنے آلات دیکھ سکتے ہیں۔</string>'),
    ('1-location-privacy-note', APPROVED, 'shared/src/commonMain/composeResources/values-bn/strings.xml', 'bn',
     '<string name="location_privacy_note">স্থানাঙ্ক আপনার ডিভাইসেই জমা থাকে এবং শুধু সময় হিসাব করতে ব্যবহৃত হয়। কোথাও কিছু পাঠানো হয় না।</string>',
     '<string name="location_privacy_note">স্থানাঙ্ক আপনার ডিভাইসেই জমা থাকে এবং শুধু সময় হিসাব করতে ব্যবহৃত হয়। আপনার অবস্থান শুধু আপনার নিজের ডিভাইসগুলোই দেখতে পারে।</string>'),
    ('1-policy-location', APPROVED, 'PRIVACY.md', 'en',
     'Your location is used on the device and never transmitted.',
     'Your location is used on the device. Only your own devices can see your location.'),
    ('1-policy-location', APPROVED, 'PRIVACY.ar.md', 'ar',
     'يُستخدم موقعك على الجهاز ولا يُنقل أبدًا.',
     'يُستخدم موقعك على الجهاز. لا ترى موقعك إلا أجهزتك أنت.'),
    ('1-policy-location', APPROVED, 'PRIVACY.fr.md', 'fr',
     'Votre position est utilisée sur l’appareil et n’est jamais transmise.',
     'Votre position est utilisée sur l’appareil. Seuls vos propres appareils peuvent voir votre position.'),
    ('1-policy-location', APPROVED, 'PRIVACY.tr.md', 'tr',
     'Konumunuz cihaz üzerinde kullanılır ve hiçbir zaman aktarılmaz.',
     'Konumunuz cihaz üzerinde kullanılır. Konumunuzu yalnızca kendi cihazlarınız görebilir.'),
    ('1-policy-location', APPROVED, 'PRIVACY.id.md', 'id',
     'Lokasi Anda dipakai di perangkat dan tidak pernah dikirimkan.',
     'Lokasi Anda dipakai di perangkat. Hanya perangkat Anda sendiri yang dapat melihat lokasi Anda.'),
    ('1-policy-location', APPROVED, 'PRIVACY.ur.md', 'ur',
     'آپ کا مقام آلے ہی پر استعمال ہوتا ہے اور کبھی کہیں منتقل نہیں کیا جاتا۔',
     'آپ کا مقام آلے ہی پر استعمال ہوتا ہے۔ آپ کا مقام صرف آپ کے اپنے آلات دیکھ سکتے ہیں۔'),
    ('1-policy-location', APPROVED, 'PRIVACY.bn.md', 'bn',
     'আপনার অবস্থান ডিভাইসেই ব্যবহৃত হয় এবং কখনো পাঠানো হয় না।',
     'আপনার অবস্থান ডিভাইসেই ব্যবহৃত হয়। আপনার অবস্থান শুধু আপনার নিজের ডিভাইসগুলোই দেখতে পারে।'),
    ('1-listing-location', APPROVED, 'docs/store/listing/en.md', 'en',
     '• Your location is used on your phone to calculate times and never sent anywhere.',
     '• Your location is used on your phone to calculate times. Only your own devices can see your location.'),
    ('1-listing-location', APPROVED, 'docs/store/listing/ar.md', 'ar',
     '• موقعك يُستخدم على هاتفك لحساب المواقيت ولا يُرسل إلى أي مكان.',
     '• موقعك يُستخدم على هاتفك لحساب المواقيت. لا ترى موقعك إلا أجهزتك أنت.'),
    ('1-listing-location', APPROVED, 'docs/store/listing/fr.md', 'fr',
     '• Votre position sert, sur votre téléphone, à calculer les horaires, et n’est jamais envoyée nulle part.',
     '• Votre position sert, sur votre téléphone, à calculer les horaires. Seuls vos propres appareils peuvent voir votre position.'),
    ('1-listing-location', APPROVED, 'docs/store/listing/tr.md', 'tr',
     '• Konumunuz vakitleri hesaplamak için telefonunuzda kullanılır ve hiçbir yere gönderilmez.',
     '• Konumunuz vakitleri hesaplamak için telefonunuzda kullanılır. Konumunuzu yalnızca kendi cihazlarınız görebilir.'),
    ('1-listing-location', APPROVED, 'docs/store/listing/id.md', 'id',
     '• Lokasi Anda dipakai di ponsel Anda untuk menghitung waktu salat dan tidak pernah dikirim ke mana pun.',
     '• Lokasi Anda dipakai di ponsel Anda untuk menghitung waktu salat. Hanya perangkat Anda sendiri yang dapat melihat lokasi Anda.'),
    ('1-listing-location', APPROVED, 'docs/store/listing/ur.md', 'ur',
     '• آپ کا مقام آپ کے فون پر اوقات کے حساب کے لیے استعمال ہوتا ہے اور کبھی کہیں نہیں بھیجا جاتا۔',
     '• آپ کا مقام آپ کے فون پر اوقات کے حساب کے لیے استعمال ہوتا ہے۔ آپ کا مقام صرف آپ کے اپنے آلات دیکھ سکتے ہیں۔'),
    ('1-listing-location', APPROVED, 'docs/store/listing/bn.md', 'bn',
     '• আপনার অবস্থান সময় হিসাব করতে আপনার ফোনেই ব্যবহৃত হয়, কোথাও পাঠানো হয় না।',
     '• আপনার অবস্থান সময় হিসাব করতে আপনার ফোনেই ব্যবহৃত হয়। আপনার অবস্থান শুধু আপনার নিজের ডিভাইসগুলোই দেখতে পারে।'),
    ('1-readme-location', APPROVED, 'README.md', 'en',
     'Location never leaves the phone.',
     'Only your own devices can see your location.'),
    # Item 2: the relay sentence, as ruled, and its heading and lead-in (question 7's), in a
    # "Your watch" section after "What stays on your devices".
    ('2-relay', APPROVED, 'PRIVACY.md', 'en',
     None,
     'When your phone and watch are not directly connected, Google (Android) or Apple (iPhone) may carry this data over the internet, end-to-end encrypted so only your phone and watch can read it.'),
    ('2-watch-heading', APPROVED, 'PRIVACY.md', 'en',
     None,
     '## Your watch'),
    ('2-watch-lead-in', APPROVED, 'PRIVACY.md', 'en',
     None,
     "If you add Taqwa to your watch, your phone gives it your city, the Qibla's direction and distance, your area's compass correction, the coming month's prayer times as the app writes them, and your own tasbeeh phrases, with a random number made once on your phone that tells your watch which phone sent them."),
    ('2-relay', APPROVED, 'PRIVACY.ar.md', 'ar',
     None,
     'وحين لا يكون هاتفك وساعتك متصلين اتصالًا مباشرًا، قد تنقل Google (أندرويد) أو Apple (iPhone) هذه البيانات عبر الإنترنت، مشفّرةً من طرف إلى طرف فلا يقرؤها إلا هاتفك وساعتك.'),
    ('2-watch-heading', APPROVED, 'PRIVACY.ar.md', 'ar',
     None,
     '## ساعتك'),
    ('2-watch-lead-in', APPROVED, 'PRIVACY.ar.md', 'ar',
     None,
     'إن أضفت تقوى إلى ساعتك، يعطيها هاتفك مدينتك، واتجاه القبلة ومسافتها، وتصحيح البوصلة في منطقتك، ومواقيت الصلاة للشهر القادم كما يكتبها التطبيق، وأذكار التسبيح التي كتبتها بنفسك، ومعها رقم عشوائي يُنشأ مرة واحدة على هاتفك، تعرف به ساعتك أيّ هاتف أرسلها.'),
    ('2-relay', APPROVED, 'PRIVACY.fr.md', 'fr',
     None,
     'Quand votre téléphone et votre montre ne sont pas connectés directement, Google (Android) ou Apple (iPhone) peut acheminer ces données par Internet, chiffrées de bout en bout pour que seuls votre téléphone et votre montre puissent les lire.'),
    ('2-watch-heading', APPROVED, 'PRIVACY.fr.md', 'fr',
     None,
     '## Votre montre'),
    ('2-watch-lead-in', APPROVED, 'PRIVACY.fr.md', 'fr',
     None,
     'Si vous ajoutez Taqwa à votre montre, votre téléphone lui donne votre ville, la direction et la distance de la qibla, la correction de boussole de votre région, les horaires de prière du mois à venir tels que l’application les écrit, et vos propres formules de tasbih, avec un nombre aléatoire créé une seule fois sur votre téléphone, qui indique à votre montre quel téléphone les a envoyés.'),
    ('2-relay', APPROVED, 'PRIVACY.tr.md', 'tr',
     None,
     'Telefonunuz ve saatiniz doğrudan bağlı olmadığında Google (Android) ya da Apple (iPhone) bu verileri internet üzerinden taşıyabilir; veriler uçtan uca şifrelidir, böylece yalnızca telefonunuz ve saatiniz okuyabilir.'),
    ('2-watch-heading', APPROVED, 'PRIVACY.tr.md', 'tr',
     None,
     '## Saatiniz'),
    ('2-watch-lead-in', APPROVED, 'PRIVACY.tr.md', 'tr',
     None,
     "Taqwa'yı saatinize eklerseniz telefonunuz ona şehrinizi, kıblenin yönünü ve uzaklığını, bölgenizin pusula düzeltmesini, önümüzdeki ayın namaz vakitlerini uygulamanın yazdığı biçimde ve kendi tesbih ifadelerinizi, bunları hangi telefonun gönderdiğini saatinize bildiren ve telefonunuzda bir kez oluşturulan rastgele bir sayıyla birlikte verir."),
    ('2-relay', APPROVED, 'PRIVACY.id.md', 'id',
     None,
     'Saat ponsel dan jam tangan Anda tidak terhubung langsung, Google (Android) atau Apple (iPhone) dapat membawa data ini lewat internet, terenkripsi end-to-end sehingga hanya ponsel dan jam tangan Anda yang dapat membacanya.'),
    ('2-watch-heading', APPROVED, 'PRIVACY.id.md', 'id',
     None,
     '## Jam tangan Anda'),
    ('2-watch-lead-in', APPROVED, 'PRIVACY.id.md', 'id',
     None,
     'Jika Anda menambahkan Taqwa ke jam tangan Anda, ponsel Anda memberinya kota Anda, arah dan jarak kiblat, koreksi kompas di daerah Anda, waktu salat sebulan ke depan sebagaimana ditulis aplikasi, dan kalimat tasbih Anda sendiri, beserta sebuah angka acak yang dibuat sekali di ponsel Anda, yang memberi tahu jam tangan Anda ponsel mana yang mengirimnya.'),
    ('2-relay', APPROVED, 'PRIVACY.ur.md', 'ur',
     None,
     'جب آپ کا فون اور گھڑی براہِ راست جڑے نہ ہوں تو Google (اینڈرائیڈ) یا Apple (iPhone) یہ ڈیٹا انٹرنیٹ کے ذریعے پہنچا سکتا ہے، شروع سے آخر تک خفیہ کاری کے ساتھ، تاکہ اسے صرف آپ کا فون اور آپ کی گھڑی پڑھ سکیں۔'),
    ('2-watch-heading', APPROVED, 'PRIVACY.ur.md', 'ur',
     None,
     '## آپ کی گھڑی'),
    ('2-watch-lead-in', APPROVED, 'PRIVACY.ur.md', 'ur',
     None,
     'اگر آپ تقویٰ کو اپنی گھڑی میں شامل کریں تو آپ کا فون اسے آپ کا شہر، قبلہ کی سمت اور فاصلہ، آپ کے علاقے کے لیے قطب نما کی تصحیح، آنے والے مہینے کے نماز کے اوقات جیسے ایپ انہیں لکھتی ہے، اور آپ کے اپنے تسبیح کے کلمات دیتا ہے، اور ساتھ ایک بے ترتیب نمبر بھی جو آپ کے فون پر ایک ہی بار بنتا ہے اور گھڑی کو بتاتا ہے کہ یہ سب کس فون نے بھیجا۔'),
    ('2-relay', APPROVED, 'PRIVACY.bn.md', 'bn',
     None,
     'আপনার ফোন ও ঘড়ি সরাসরি যুক্ত না থাকলে Google (অ্যান্ড্রয়েড) বা Apple (iPhone) এই তথ্য ইন্টারনেটের মাধ্যমে বহন করতে পারে, এন্ড-টু-এন্ড এনক্রিপ্ট করা অবস্থায়, যাতে শুধু আপনার ফোন ও ঘড়িই তা পড়তে পারে।'),
    ('2-watch-heading', APPROVED, 'PRIVACY.bn.md', 'bn',
     None,
     '## আপনার ঘড়ি'),
    ('2-watch-lead-in', APPROVED, 'PRIVACY.bn.md', 'bn',
     None,
     'তাকওয়া আপনার ঘড়িতে যোগ করলে আপনার ফোন ঘড়িকে দেয় আপনার শহর, কিবলার দিক ও দূরত্ব, আপনার এলাকার কম্পাস সংশোধন, অ্যাপ যেভাবে লেখে সেভাবে সামনের এক মাসের নামাজের সময়, আর আপনার নিজের তাসবিহর বাক্যগুলো, সঙ্গে একটি এলোমেলো সংখ্যা, যা আপনার ফোনে একবারই তৈরি হয় এবং ঘড়িকে জানায় কোন ফোন এগুলো পাঠিয়েছে।'),
    # Item 3, question 7's lines. 4a: "Stays on your devices" (About, and the home page's row).
    ('3-4a-about-stays', APPROVED, 'shared/src/commonMain/composeResources/values/strings.xml', 'en',
     '<string name="about_stays_title">Stays on your phone</string>',
     '<string name="about_stays_title">Stays on your devices</string>'),
    ('3-4a-about-stays', APPROVED, 'shared/src/commonMain/composeResources/values/strings.xml', 'en',
     '<string name="about_stays_body">Location, settings, bookmarks and counts never leave it.</string>',
     '<string name="about_stays_body">Only your own devices can see your location, settings, bookmarks and counts.</string>'),
    ('3-4a-home-row', APPROVED, 'site/pages/en/home.html', 'en',
     '<b>Stays on your phone</b><span>Location, settings, bookmarks and counts never leave it.</span>',
     '<b>Stays on your devices</b><span>Only your own devices can see your location, settings, bookmarks and counts.</span>'),
    ('3-4a-about-stays', APPROVED, 'shared/src/commonMain/composeResources/values-ar/strings.xml', 'ar',
     '<string name="about_stays_title">يبقى على هاتفك</string>',
     '<string name="about_stays_title">يبقى على أجهزتك</string>'),
    ('3-4a-about-stays', APPROVED, 'shared/src/commonMain/composeResources/values-ar/strings.xml', 'ar',
     '<string name="about_stays_body">الموقع والإعدادات والعلامات والعدّاد لا تغادره.</string>',
     '<string name="about_stays_body">لا ترى موقعك وإعداداتك وعلاماتك وعدّادك إلا أجهزتك أنت.</string>'),
    ('3-4a-home-row', APPROVED, 'site/pages/ar/home.html', 'ar',
     '<b>يبقى على هاتفك</b><span>الموقع والإعدادات والعلامات والعدّاد لا تغادره.</span>',
     '<b>يبقى على أجهزتك</b><span>لا ترى موقعك وإعداداتك وعلاماتك وعدّادك إلا أجهزتك أنت.</span>'),
    ('3-4a-about-stays', APPROVED, 'shared/src/commonMain/composeResources/values-fr/strings.xml', 'fr',
     '<string name="about_stays_title">Tout reste sur votre téléphone</string>',
     '<string name="about_stays_title">Tout reste sur vos appareils</string>'),
    ('3-4a-about-stays', APPROVED, 'shared/src/commonMain/composeResources/values-fr/strings.xml', 'fr',
     '<string name="about_stays_body">Position, paramètres, signets et compteurs ne le quittent jamais.</string>',
     '<string name="about_stays_body">Seuls vos propres appareils peuvent voir votre position, vos paramètres, vos signets et vos compteurs.</string>'),
    ('3-4a-home-row', APPROVED, 'site/pages/fr/home.html', 'fr',
     '<b>Tout reste sur votre téléphone</b><span>Position, paramètres, signets et compteurs ne le quittent jamais.</span>',
     '<b>Tout reste sur vos appareils</b><span>Seuls vos propres appareils peuvent voir votre position, vos paramètres, vos signets et vos compteurs.</span>'),
    ('3-4a-about-stays', APPROVED, 'shared/src/commonMain/composeResources/values-tr/strings.xml', 'tr',
     '<string name="about_stays_title">Telefonunuzda kalır</string>',
     '<string name="about_stays_title">Cihazlarınızda kalır</string>'),
    ('3-4a-about-stays', APPROVED, 'shared/src/commonMain/composeResources/values-tr/strings.xml', 'tr',
     '<string name="about_stays_body">Konum, ayarlar, yer imleri ve sayaçlar telefonunuzdan çıkmaz.</string>',
     '<string name="about_stays_body">Konumunuzu, ayarlarınızı, yer imlerinizi ve sayaçlarınızı yalnızca kendi cihazlarınız görebilir.</string>'),
    ('3-4a-home-row', APPROVED, 'site/pages/tr/home.html', 'tr',
     '<b>Telefonunuzda kalır</b><span>Konum, ayarlar, yer imleri ve sayaçlar telefonunuzdan çıkmaz.</span>',
     '<b>Cihazlarınızda kalır</b><span>Konumunuzu, ayarlarınızı, yer imlerinizi ve sayaçlarınızı yalnızca kendi cihazlarınız görebilir.</span>'),
    ('3-4a-about-stays', APPROVED, 'shared/src/commonMain/composeResources/values-id/strings.xml', 'id',
     '<string name="about_stays_title">Tetap di ponsel Anda</string>',
     '<string name="about_stays_title">Tetap di perangkat Anda</string>'),
    ('3-4a-about-stays', APPROVED, 'shared/src/commonMain/composeResources/values-id/strings.xml', 'id',
     '<string name="about_stays_body">Lokasi, pengaturan, penanda, dan hitungan tidak pernah keluar dari ponsel.</string>',
     '<string name="about_stays_body">Hanya perangkat Anda sendiri yang dapat melihat lokasi, pengaturan, penanda, dan hitungan Anda.</string>'),
    ('3-4a-about-stays', APPROVED, 'shared/src/commonMain/composeResources/values-in/strings.xml', 'id',
     '<string name="about_stays_title">Tetap di ponsel Anda</string>',
     '<string name="about_stays_title">Tetap di perangkat Anda</string>'),
    ('3-4a-about-stays', APPROVED, 'shared/src/commonMain/composeResources/values-in/strings.xml', 'id',
     '<string name="about_stays_body">Lokasi, pengaturan, penanda, dan hitungan tidak pernah keluar dari ponsel.</string>',
     '<string name="about_stays_body">Hanya perangkat Anda sendiri yang dapat melihat lokasi, pengaturan, penanda, dan hitungan Anda.</string>'),
    ('3-4a-home-row', APPROVED, 'site/pages/id/home.html', 'id',
     '<b>Tetap di ponsel Anda</b><span>Lokasi, pengaturan, penanda, dan hitungan tidak pernah keluar dari ponsel.</span>',
     '<b>Tetap di perangkat Anda</b><span>Hanya perangkat Anda sendiri yang dapat melihat lokasi, pengaturan, penanda, dan hitungan Anda.</span>'),
    ('3-4a-about-stays', APPROVED, 'shared/src/commonMain/composeResources/values-ur/strings.xml', 'ur',
     '<string name="about_stays_title">آپ کے فون پر رہتا ہے</string>',
     '<string name="about_stays_title">آپ کے آلات پر رہتا ہے</string>'),
    ('3-4a-about-stays', APPROVED, 'shared/src/commonMain/composeResources/values-ur/strings.xml', 'ur',
     '<string name="about_stays_body">مقام، ترتیبات، محفوظ آیات اور گنتی کبھی فون سے باہر نہیں جاتیں۔</string>',
     '<string name="about_stays_body">آپ کا مقام، ترتیبات، محفوظ آیات اور گنتی صرف آپ کے اپنے آلات دیکھ سکتے ہیں۔</string>'),
    ('3-4a-home-row', APPROVED, 'site/pages/ur/home.html', 'ur',
     '<b>آپ کے فون پر رہتا ہے</b><span>مقام، ترتیبات، محفوظ آیات اور گنتی کبھی فون سے باہر نہیں جاتیں۔</span>',
     '<b>آپ کے آلات پر رہتا ہے</b><span>آپ کا مقام، ترتیبات، محفوظ آیات اور گنتی صرف آپ کے اپنے آلات دیکھ سکتے ہیں۔</span>'),
    ('3-4a-about-stays', APPROVED, 'shared/src/commonMain/composeResources/values-bn/strings.xml', 'bn',
     '<string name="about_stays_title">আপনার ফোনেই থাকে</string>',
     '<string name="about_stays_title">আপনার ডিভাইসেই থাকে</string>'),
    ('3-4a-about-stays', APPROVED, 'shared/src/commonMain/composeResources/values-bn/strings.xml', 'bn',
     '<string name="about_stays_body">অবস্থান, সেটিংস, বুকমার্ক ও গণনা কখনো ফোন ছেড়ে যায় না।</string>',
     '<string name="about_stays_body">আপনার অবস্থান, সেটিংস, বুকমার্ক ও গণনা শুধু আপনার নিজের ডিভাইসগুলোই দেখতে পারে।</string>'),
    ('3-4a-home-row', APPROVED, 'site/pages/bn/home.html', 'bn',
     '<b>আপনার ফোনেই থাকে</b><span>অবস্থান, সেটিংস, বুকমার্ক ও গণনা কখনো ফোন ছেড়ে যায় না।</span>',
     '<b>আপনার ডিভাইসেই থাকে</b><span>আপনার অবস্থান, সেটিংস, বুকমার্ক ও গণনা শুধু আপনার নিজের ডিভাইসগুলোই দেখতে পারে।</span>'),
    # 4b: the policy's "What stays on your devices".
    ('3-4b-policy-heading', APPROVED, 'PRIVACY.md', 'en',
     '## What stays on your phone',
     '## What stays on your devices'),
    ('3-4b-policy-first', APPROVED, 'PRIVACY.md', 'en',
     'Everything the app knows is stored on your device and nowhere else:',
     'Everything the app knows is stored on your own devices:'),
    ('3-4b-policy-none', APPROVED, 'PRIVACY.md', 'en',
     'None of this is sent to us or to anyone.',
     'None of this is sent to us or to anyone else.'),
    ('3-4b-policy-heading', APPROVED, 'PRIVACY.ar.md', 'ar',
     '## ما يبقى على هاتفك',
     '## ما يبقى على أجهزتك'),
    ('3-4b-policy-first', APPROVED, 'PRIVACY.ar.md', 'ar',
     'كل ما يعرفه التطبيق مخزّن على جهازك ولا في أي مكان آخر:',
     'كل ما يعرفه التطبيق مخزّن على أجهزتك أنت:'),
    ('3-4b-policy-none', APPROVED, 'PRIVACY.ar.md', 'ar',
     'لا يُرسل شيء من هذا إلينا ولا إلى أي أحد.',
     'لا يُرسل شيء من هذا إلينا ولا إلى أي أحد آخر.'),
    ('3-4b-policy-heading', APPROVED, 'PRIVACY.fr.md', 'fr',
     '## Ce qui reste sur votre téléphone',
     '## Ce qui reste sur vos appareils'),
    ('3-4b-policy-first', APPROVED, 'PRIVACY.fr.md', 'fr',
     'Tout ce que l’application sait est enregistré sur votre appareil et nulle part ailleurs :',
     'Tout ce que l’application sait est enregistré sur vos propres appareils :'),
    ('3-4b-policy-none', APPROVED, 'PRIVACY.fr.md', 'fr',
     'Rien de tout cela ne nous est envoyé, ni à qui que ce soit.',
     'Rien de tout cela ne nous est envoyé, ni à qui que ce soit d’autre.'),
    ('3-4b-policy-heading', APPROVED, 'PRIVACY.tr.md', 'tr',
     '## Telefonunuzda kalanlar',
     '## Cihazlarınızda kalanlar'),
    ('3-4b-policy-first', APPROVED, 'PRIVACY.tr.md', 'tr',
     'Uygulamanın bildiği her şey cihazınızda saklanır, başka hiçbir yerde değil:',
     'Uygulamanın bildiği her şey kendi cihazlarınızda saklanır:'),
    ('3-4b-policy-none', APPROVED, 'PRIVACY.tr.md', 'tr',
     None,
     'Bunların hiçbiri bize ya da bir başkasına gönderilmez.'),
    ('3-4b-policy-heading', APPROVED, 'PRIVACY.id.md', 'id',
     '## Apa yang tetap di ponsel Anda',
     '## Apa yang tetap di perangkat Anda'),
    ('3-4b-policy-first', APPROVED, 'PRIVACY.id.md', 'id',
     'Semua yang diketahui aplikasi ini tersimpan di perangkat Anda dan tidak di tempat lain:',
     'Semua yang diketahui aplikasi ini tersimpan di perangkat Anda sendiri:'),
    ('3-4b-policy-none', APPROVED, 'PRIVACY.id.md', 'id',
     'Tidak satu pun dari semua itu dikirim kepada kami atau kepada siapa pun.',
     'Tidak satu pun dari semua itu dikirim kepada kami atau kepada orang lain.'),
    ('3-4b-policy-heading', APPROVED, 'PRIVACY.ur.md', 'ur',
     '## جو کچھ آپ کے فون پر رہتا ہے',
     '## جو کچھ آپ کے آلات پر رہتا ہے'),
    ('3-4b-policy-first', APPROVED, 'PRIVACY.ur.md', 'ur',
     'ایپ جو کچھ جانتی ہے وہ سب آپ ہی کے آلے پر محفوظ ہے، اور کہیں نہیں:',
     'ایپ جو کچھ جانتی ہے وہ سب آپ کے اپنے آلات پر محفوظ ہے:'),
    ('3-4b-policy-none', APPROVED, 'PRIVACY.ur.md', 'ur',
     None,
     'اس میں سے کچھ بھی ہمیں یا کسی اور کو نہیں بھیجا جاتا۔'),
    ('3-4b-policy-heading', APPROVED, 'PRIVACY.bn.md', 'bn',
     '## আপনার ফোনে যা থাকে',
     '## আপনার ডিভাইসে যা থাকে'),
    ('3-4b-policy-first', APPROVED, 'PRIVACY.bn.md', 'bn',
     'অ্যাপটি যা কিছু জানে তার সবই আপনার ডিভাইসে জমা থাকে, আর কোথাও নয়:',
     'অ্যাপটি যা কিছু জানে তার সবই আপনার নিজের ডিভাইসগুলোতে জমা থাকে:'),
    ('3-4b-policy-none', APPROVED, 'PRIVACY.bn.md', 'bn',
     None,
     'এর কিছুই আমাদের কাছে বা অন্য কারও কাছে পাঠানো হয় না।'),
    # 4c: deleting Taqwa from the phone and the watch.
    ('3-4c-policy-deleting', APPROVED, 'PRIVACY.md', 'en',
     'Deleting the app deletes all of it.',
     'Deleting Taqwa from your phone and your watch deletes all of it.'),
    ('3-4c-policy-deleting', APPROVED, 'PRIVACY.ar.md', 'ar',
     'وحذف التطبيق يحذف كل ذلك.',
     'وحذف تقوى من هاتفك وساعتك يحذف كل ذلك.'),
    ('3-4c-policy-deleting', APPROVED, 'PRIVACY.fr.md', 'fr',
     'Supprimer l’application supprime tout cela.',
     'Supprimer Taqwa de votre téléphone et de votre montre supprime tout cela.'),
    ('3-4c-policy-deleting', APPROVED, 'PRIVACY.tr.md', 'tr',
     'Uygulamayı silmek bunların hepsini siler.',
     "Taqwa'yı telefonunuzdan ve saatinizden silmek bunların hepsini siler."),
    ('3-4c-policy-deleting', APPROVED, 'PRIVACY.id.md', 'id',
     'Menghapus aplikasinya menghapus semuanya.',
     'Menghapus Taqwa dari ponsel dan jam tangan Anda menghapus semuanya.'),
    ('3-4c-policy-deleting', APPROVED, 'PRIVACY.ur.md', 'ur',
     'ایپ کو حذف کرنا یہ سب حذف کر دیتا ہے۔',
     'تقویٰ کو اپنے فون اور گھڑی سے حذف کرنے سے یہ سب حذف ہو جاتا ہے۔'),
    ('3-4c-policy-deleting', APPROVED, 'PRIVACY.bn.md', 'bn',
     'অ্যাপটি মুছে ফেললে এর সবই মুছে যায়।',
     'আপনার ফোন ও ঘড়ি থেকে তাকওয়া মুছে ফেললে এর সবই মুছে যায়।'),
    # 4d: Backups, one sentence at the end (it stays whatever plan 4's simulator check finds).
    ('3-4d-policy-backups', APPROVED, 'PRIVACY.md', 'en',
     None,
     "If you add Taqwa to an Apple Watch, iOS keeps the last copy your iPhone sent it, which names your city, and that copy may be in your iPhone's backup. An iCloud backup is encrypted with keys Apple keeps, unless you turn on Advanced Data Protection, which encrypts it end to end; a backup to a computer is encrypted only if you choose to encrypt it."),
    # M8 (6 November 2026): Mohamed approved Apple's own Arabic name for Advanced Data Protection,
    # الحماية المتقدمة للبيانات (Apple's page 108756 in ar-sa), in place of the draft's
    # حماية البيانات المتقدمة (point A6: "M8: approved with your recommendations (A1–A7, E4).
    # Changes: none."). The name alone changed; the sentence says what it said. The draft's name is
    # the row's old text, so its return anywhere in the policy fails (Ruling 73 item 5).
    ('3-4d-policy-backups', APPROVED, 'PRIVACY.ar.md', 'ar',
     'حماية البيانات المتقدمة',
     'وإن أضفت تقوى إلى Apple Watch، يحتفظ iOS بآخر نسخة أرسلها إليها iPhone، وفيها اسم مدينتك، وقد تكون تلك النسخة في النسخ الاحتياطي لجهاز iPhone. والنسخ الاحتياطي على iCloud مشفّر بمفاتيح تحتفظ بها Apple، إلا إن فعّلت الحماية المتقدمة للبيانات فيصبح مشفّرًا من طرف إلى طرف؛ أما النسخ الاحتياطي على الكمبيوتر فلا يُشفَّر إلا إن اخترت ذلك.'),
    ('3-4d-policy-backups', APPROVED, 'PRIVACY.fr.md', 'fr',
     None,
     'Si vous ajoutez Taqwa à une Apple Watch, iOS conserve la dernière copie que votre iPhone lui a envoyée, qui indique votre ville, et cette copie peut figurer dans la sauvegarde de votre iPhone. Une sauvegarde iCloud est chiffrée avec des clés que conserve Apple, sauf si vous activez la protection avancée des données, qui la chiffre de bout en bout ; une sauvegarde sur un ordinateur n’est chiffrée que si vous choisissez de la chiffrer.'),
    ('3-4d-policy-backups', APPROVED, 'PRIVACY.tr.md', 'tr',
     None,
     "Taqwa'yı bir Apple Watch'a eklerseniz iOS, iPhone'unuzun ona gönderdiği ve şehrinizin adını taşıyan son kopyayı saklar; bu kopya iPhone'unuzun yedeğinde bulunabilir. iCloud yedeği, anahtarlarını Apple'ın sakladığı bir şifrelemeyle korunur; İleri Düzey Veri Koruma'yı açarsanız uçtan uca şifrelenir. Bilgisayardaki bir yedek ise yalnızca siz seçerseniz şifrelenir."),
    ('3-4d-policy-backups', APPROVED, 'PRIVACY.id.md', 'id',
     None,
     'Jika Anda menambahkan Taqwa ke Apple Watch, iOS menyimpan salinan terakhir yang dikirim iPhone Anda ke jam itu, yang menyebut kota Anda, dan salinan itu mungkin ada di cadangan iPhone Anda. Cadangan iCloud dienkripsi dengan kunci yang disimpan Apple, kecuali Anda menyalakan Perlindungan Data Lanjutan, yang mengenkripsinya secara end-to-end; cadangan di komputer hanya dienkripsi jika Anda memilih untuk mengenkripsinya.'),
    ('3-4d-policy-backups', APPROVED, 'PRIVACY.ur.md', 'ur',
     None,
     'اگر آپ تقویٰ کو Apple Watch میں شامل کریں تو iOS وہ آخری نقل رکھتا ہے جو آپ کے iPhone نے اسے بھیجی، جس میں آپ کے شہر کا نام ہے، اور وہ نقل آپ کے iPhone کے بیک اپ میں ہو سکتی ہے۔ iCloud کا بیک اپ ایسی خفیہ کاری سے محفوظ ہوتا ہے جس کی چابیاں Apple کے پاس رہتی ہیں، الا یہ کہ آپ Advanced Data Protection آن کریں، جس سے وہ شروع سے آخر تک خفیہ کاری سے محفوظ ہو جاتا ہے؛ کمپیوٹر پر لیا گیا بیک اپ صرف تب خفیہ کاری سے محفوظ ہوتا ہے جب آپ خود یہ چنیں۔'),
    ('3-4d-policy-backups', APPROVED, 'PRIVACY.bn.md', 'bn',
     None,
     'তাকওয়া কোনো Apple Watch-এ যোগ করলে আপনার iPhone ঘড়িতে সর্বশেষ যে কপিটি পাঠিয়েছে, iOS সেটি রেখে দেয়, যাতে আপনার শহরের নাম থাকে, আর সেই কপি আপনার iPhone-এর ব্যাকআপে থাকতে পারে। iCloud ব্যাকআপ এমন চাবি দিয়ে এনক্রিপ্ট করা থাকে যা Apple রাখে, যদি না আপনি Advanced Data Protection চালু করেন, যা একে এন্ড-টু-এন্ড এনক্রিপ্ট করে; কম্পিউটারে নেওয়া ব্যাকআপ কেবল আপনি বেছে নিলেই এনক্রিপ্ট হয়।'),
    # 4d at M3 (31 October 2026): Mohamed's tweak to the section's earlier sentence, "the only files
    # of Taqwa's own that are backed up are the widgets' small caches, ...", which now ends at "your
    # location". The clause after it, that the backup "is encrypted by Apple and never visible to us",
    # is the old text, since the sentences after it say how a backup is encrypted.
    ('3-4d-policy-backups-caches', APPROVED, 'PRIVACY.md', 'en',
     'and that backup is encrypted by Apple and never visible to us',
     "the only files of Taqwa's own that are backed up are the widgets' small caches, the upcoming prayer times and the hundred ayahs the daily-ayah widget rotates through, neither of which contains your location."),
    ('3-4d-policy-backups-caches', APPROVED, 'PRIVACY.ar.md', 'ar',
     'وذلك النسخ مشفّر لدى Apple ولا نطّلع عليه أبدًا',
     'وكل ما يُنسخ احتياطيًا من ملفات تقوى نفسه هو ذاكرتا الأدوات الصغيرتان: مواقيت الصلاة القادمة، والآيات المئة التي تتناوب عليها أداة «آية اليوم»، وليس في أيٍّ منهما موقعك.'),
    ('3-4d-policy-backups-caches', APPROVED, 'PRIVACY.fr.md', 'fr',
     'et cette sauvegarde est chiffrée par Apple et ne nous est jamais visible',
     'les seuls fichiers propres à Taqwa qui soient sauvegardés sont les petits caches des widgets, les prochains horaires de prière et les cent versets que le widget du verset du jour fait défiler, dont aucun ne contient votre position.'),
    ('3-4d-policy-backups-caches', APPROVED, 'PRIVACY.tr.md', 'tr',
     'o yedekleme de Apple tarafından şifrelenir ve bize hiçbir zaman görünmez',
     "Taqwa'nın kendi dosyalarından yedeklenen tek şey widget'ların küçük önbellekleri, yani yaklaşan namaz vakitleri ile günün ayeti widget'ının sırayla gösterdiği yüz ayettir. Bunların hiçbiri konumunuzu içermez."),
    ('3-4d-policy-backups-caches', APPROVED, 'PRIVACY.id.md', 'id',
     'cadangan itu pun dienkripsi oleh Apple dan tidak pernah terlihat oleh kami',
     'berkas milik Taqwa sendiri yang dicadangkan hanyalah cache kecil milik widget, yaitu waktu salat yang akan datang dan seratus ayat yang diputar bergiliran oleh widget ayat harian, dan keduanya tidak memuat lokasi Anda.'),
    ('3-4d-policy-backups-caches', APPROVED, 'PRIVACY.ur.md', 'ur',
     'اور وہ بیک اپ Apple کی طرف سے خفیہ کاری شدہ ہے اور ہمیں کبھی نظر نہیں آتا',
     'تقویٰ کی اپنی فائلوں میں سے صرف وہ بیک اپ ہوتی ہیں جو ویجٹ کے چھوٹے ذخیرے ہیں — آنے والی نمازوں کے اوقات اور وہ سو آیات جن میں سے روزانہ کی آیت کا ویجٹ باری باری دکھاتا ہے — اور ان میں سے کسی میں آپ کا مقام نہیں ہوتا۔'),
    ('3-4d-policy-backups-caches', APPROVED, 'PRIVACY.bn.md', 'bn',
     'সেই ব্যাকআপ Apple এনক্রিপ্ট করে রাখে এবং তা আমাদের কাছে কখনো দৃশ্যমান হয় না',
     'তাকওয়ার নিজের ফাইলের মধ্যে যা ব্যাকআপ হয় তা কেবল উইজেটের ছোট ক্যাশ — সামনের নামাজের সময় আর আজকের-আয়াত উইজেট যে একশো আয়াত ঘুরিয়ে দেখায় সেগুলো — আর এর কোনোটিতেই আপনার অবস্থান থাকে না।'),
    # 4e: the support page's "Everything Taqwa knows".
    ('3-4e-support-first', APPROVED, 'site/pages/en/support.html', 'en',
     'Everything Taqwa knows lives on your phone:',
     'Everything Taqwa knows lives on your own devices:'),
    ('3-4e-support-second', APPROVED, 'site/pages/en/support.html', 'en',
     'Uninstalling the app removes all of it.',
     'Uninstalling Taqwa from your phone and your watch removes all of it.'),
    ('3-4e-support-first', APPROVED, 'site/pages/ar/support.html', 'ar',
     'كل ما يعرفه تقوى يبقى على هاتفك:',
     'كل ما يعرفه تقوى يبقى على أجهزتك أنت:'),
    ('3-4e-support-second', APPROVED, 'site/pages/ar/support.html', 'ar',
     'وإزالة التطبيق تزيل كل ذلك.',
     'وإزالة تقوى من هاتفك وساعتك تزيل كل ذلك.'),
    ('3-4e-support-first', APPROVED, 'site/pages/fr/support.html', 'fr',
     'Tout ce que Taqwa sait se trouve sur votre téléphone :',
     'Tout ce que Taqwa sait se trouve sur vos propres appareils :'),
    ('3-4e-support-second', APPROVED, 'site/pages/fr/support.html', 'fr',
     'Désinstaller l’application efface tout.',
     'Désinstaller Taqwa de votre téléphone et de votre montre efface tout.'),
    ('3-4e-support-first', APPROVED, 'site/pages/tr/support.html', 'tr',
     "Taqwa'nın bildiği her şey telefonunuzda kalır:",
     "Taqwa'nın bildiği her şey kendi cihazlarınızda kalır:"),
    ('3-4e-support-second', APPROVED, 'site/pages/tr/support.html', 'tr',
     'Uygulamayı kaldırmak bunların hepsini siler.',
     "Taqwa'yı telefonunuzdan ve saatinizden kaldırmak bunların hepsini siler."),
    ('3-4e-support-first', APPROVED, 'site/pages/id/support.html', 'id',
     'Semua yang diketahui Taqwa ada di ponsel Anda:',
     'Semua yang diketahui Taqwa ada di perangkat Anda sendiri:'),
    ('3-4e-support-second', APPROVED, 'site/pages/id/support.html', 'id',
     'Mencopot aplikasinya menghapus semuanya.',
     'Mencopot Taqwa dari ponsel dan jam tangan Anda menghapus semuanya.'),
    ('3-4e-support-first', APPROVED, 'site/pages/ur/support.html', 'ur',
     'تقویٰ جو کچھ جانتا ہے وہ سب آپ ہی کے فون پر رہتا ہے:',
     'تقویٰ جو کچھ جانتا ہے وہ سب آپ کے اپنے آلات پر رہتا ہے:'),
    ('3-4e-support-second', APPROVED, 'site/pages/ur/support.html', 'ur',
     'ایپ ہٹا دینے سے یہ سب چلا جاتا ہے۔',
     'تقویٰ کو اپنے فون اور گھڑی سے ہٹا دینے سے یہ سب چلا جاتا ہے۔'),
    ('3-4e-support-first', APPROVED, 'site/pages/bn/support.html', 'bn',
     'তাকওয়া যা কিছু জানে সবই আপনার ফোনে থাকে:',
     'তাকওয়া যা কিছু জানে সবই আপনার নিজের ডিভাইসগুলোতে থাকে:'),
    ('3-4e-support-second', APPROVED, 'site/pages/bn/support.html', 'bn',
     'অ্যাপটি সরিয়ে ফেললে সবই মুছে যায়।',
     'আপনার ফোন ও ঘড়ি থেকে তাকওয়া সরিয়ে ফেললে সবই মুছে যায়।'),
    # 4f: "Online only for what you choose" (About, and the home page's row).
    ('3-4f-about-online', APPROVED, 'shared/src/commonMain/composeResources/values/strings.xml', 'en',
     '<string name="about_online_title">Online only when you ask</string>',
     '<string name="about_online_title">Online only for what you choose</string>'),
    ('3-4f-about-online-android', APPROVED, 'shared/src/commonMain/composeResources/values/strings.xml', 'en',
     '<string name="about_online_body">',
     '<string name="about_online_body_android">The internet is used for recitations you choose to download, from Taqwa’s public repository on GitHub. If you add Taqwa to your watch, Google may carry its data over the internet, encrypted so only your phone and watch can read it.</string>'),
    ('3-4f-about-online-ios', APPROVED, 'shared/src/commonMain/composeResources/values/strings.xml', 'en',
     '<string name="about_online_body">',
     '<string name="about_online_body_ios">The internet is used for recitations you choose to download, from Taqwa’s public repository on GitHub. If you add Taqwa to your watch, Apple may carry its data over the internet, encrypted so only your phone and watch can read it.</string>'),
    ('3-4f-home-row', APPROVED, 'site/pages/en/home.html', 'en',
     '<b>Online only when you ask</b><span>The internet is used for one thing: recitations you choose to download, from Taqwa’s public repository on GitHub.</span>',
     '<b>Online only for what you choose</b><span>The internet is used for recitations you choose to download, from Taqwa’s public repository on GitHub. If you add Taqwa to your watch, Google or Apple may carry its data over the internet, encrypted so only your phone and watch can read it.</span>'),
    ('3-4f-about-online', APPROVED, 'shared/src/commonMain/composeResources/values-ar/strings.xml', 'ar',
     '<string name="about_online_title">متصل فقط عندما تطلب</string>',
     '<string name="about_online_title">متصل فقط لما تختاره</string>'),
    ('3-4f-about-online-android', APPROVED, 'shared/src/commonMain/composeResources/values-ar/strings.xml', 'ar',
     '<string name="about_online_body">',
     '<string name="about_online_body_android">يُستخدم الإنترنت للتلاوات التي تختار تنزيلها، من مستودع تقوى العام على GitHub. وإن أضفت تقوى إلى ساعتك، فقد تنقل Google بياناتها عبر الإنترنت، مشفّرةً فلا يقرؤها إلا هاتفك وساعتك.</string>'),
    ('3-4f-about-online-ios', APPROVED, 'shared/src/commonMain/composeResources/values-ar/strings.xml', 'ar',
     '<string name="about_online_body">',
     '<string name="about_online_body_ios">يُستخدم الإنترنت للتلاوات التي تختار تنزيلها، من مستودع تقوى العام على GitHub. وإن أضفت تقوى إلى ساعتك، فقد تنقل Apple بياناتها عبر الإنترنت، مشفّرةً فلا يقرؤها إلا هاتفك وساعتك.</string>'),
    ('3-4f-home-row', APPROVED, 'site/pages/ar/home.html', 'ar',
     '<b>متصل فقط عندما تطلب</b><span>يُستخدم الإنترنت لشيء واحد: التلاوات التي تختار تنزيلها، من مستودع تقوى العام على GitHub.</span>',
     '<b>متصل فقط لما تختاره</b><span>يُستخدم الإنترنت للتلاوات التي تختار تنزيلها، من مستودع تقوى العام على GitHub. وإن أضفت تقوى إلى ساعتك، فقد تنقل Google أو Apple بياناتها عبر الإنترنت، مشفّرةً فلا يقرؤها إلا هاتفك وساعتك.</span>'),
    ('3-4f-about-online', APPROVED, 'shared/src/commonMain/composeResources/values-fr/strings.xml', 'fr',
     '<string name="about_online_title">En ligne seulement quand vous le demandez</string>',
     '<string name="about_online_title">En ligne seulement pour ce que vous choisissez</string>'),
    ('3-4f-about-online-android', APPROVED, 'shared/src/commonMain/composeResources/values-fr/strings.xml', 'fr',
     '<string name="about_online_body">',
     '<string name="about_online_body_android">Internet sert aux récitations que vous choisissez de télécharger, depuis le dépôt public de Taqwa sur GitHub. Si vous ajoutez Taqwa à votre montre, Google peut acheminer ses données par Internet, chiffrées pour que seuls votre téléphone et votre montre puissent les lire.</string>'),
    ('3-4f-about-online-ios', APPROVED, 'shared/src/commonMain/composeResources/values-fr/strings.xml', 'fr',
     '<string name="about_online_body">',
     '<string name="about_online_body_ios">Internet sert aux récitations que vous choisissez de télécharger, depuis le dépôt public de Taqwa sur GitHub. Si vous ajoutez Taqwa à votre montre, Apple peut acheminer ses données par Internet, chiffrées pour que seuls votre téléphone et votre montre puissent les lire.</string>'),
    ('3-4f-home-row', APPROVED, 'site/pages/fr/home.html', 'fr',
     '<b>En ligne seulement quand vous le demandez</b><span>Internet ne sert qu’à une chose\u202f: les récitations que vous choisissez de télécharger, depuis le dépôt public de Taqwa sur GitHub.</span>',
     '<b>En ligne seulement pour ce que vous choisissez</b><span>Internet sert aux récitations que vous choisissez de télécharger, depuis le dépôt public de Taqwa sur GitHub. Si vous ajoutez Taqwa à votre montre, Google ou Apple peut acheminer ses données par Internet, chiffrées pour que seuls votre téléphone et votre montre puissent les lire.</span>'),
    ('3-4f-about-online', APPROVED, 'shared/src/commonMain/composeResources/values-tr/strings.xml', 'tr',
     '<string name="about_online_title">Yalnızca siz isteyince çevrimiçi</string>',
     '<string name="about_online_title">Yalnızca seçtikleriniz için çevrimiçi</string>'),
    ('3-4f-about-online-android', APPROVED, 'shared/src/commonMain/composeResources/values-tr/strings.xml', 'tr',
     '<string name="about_online_body">',
     '<string name="about_online_body_android">İnternet, Taqwa’nın GitHub’daki herkese açık deposundan indirmeyi seçtiğiniz tilavetler için kullanılır. Taqwa’yı saatinize eklerseniz Google onun verilerini internet üzerinden taşıyabilir; veriler şifrelidir, yalnızca telefonunuz ve saatiniz okuyabilir.</string>'),
    ('3-4f-about-online-ios', APPROVED, 'shared/src/commonMain/composeResources/values-tr/strings.xml', 'tr',
     '<string name="about_online_body">',
     '<string name="about_online_body_ios">İnternet, Taqwa’nın GitHub’daki herkese açık deposundan indirmeyi seçtiğiniz tilavetler için kullanılır. Taqwa’yı saatinize eklerseniz Apple onun verilerini internet üzerinden taşıyabilir; veriler şifrelidir, yalnızca telefonunuz ve saatiniz okuyabilir.</string>'),
    ('3-4f-home-row', APPROVED, 'site/pages/tr/home.html', 'tr',
     "<b>Yalnızca siz isteyince çevrimiçi</b><span>İnternet tek bir şey için kullanılır: Taqwa'nın GitHub'daki herkese açık deposundan indirmeyi seçtiğiniz tilavetler.</span>",
     "<b>Yalnızca seçtikleriniz için çevrimiçi</b><span>İnternet, Taqwa'nın GitHub'daki herkese açık deposundan indirmeyi seçtiğiniz tilavetler için kullanılır. Taqwa'yı saatinize eklerseniz Google ya da Apple onun verilerini internet üzerinden taşıyabilir; veriler şifrelidir, yalnızca telefonunuz ve saatiniz okuyabilir.</span>"),
    ('3-4f-about-online', APPROVED, 'shared/src/commonMain/composeResources/values-id/strings.xml', 'id',
     '<string name="about_online_title">Online hanya bila Anda minta</string>',
     '<string name="about_online_title">Online hanya untuk yang Anda pilih</string>'),
    ('3-4f-about-online-android', APPROVED, 'shared/src/commonMain/composeResources/values-id/strings.xml', 'id',
     '<string name="about_online_body">',
     '<string name="about_online_body_android">Internet dipakai untuk tilawah yang Anda pilih untuk diunduh, dari repositori publik Taqwa di GitHub. Jika Anda menambahkan Taqwa ke jam tangan Anda, Google dapat membawa datanya lewat internet, terenkripsi sehingga hanya ponsel dan jam tangan Anda yang dapat membacanya.</string>'),
    ('3-4f-about-online-ios', APPROVED, 'shared/src/commonMain/composeResources/values-id/strings.xml', 'id',
     '<string name="about_online_body">',
     '<string name="about_online_body_ios">Internet dipakai untuk tilawah yang Anda pilih untuk diunduh, dari repositori publik Taqwa di GitHub. Jika Anda menambahkan Taqwa ke jam tangan Anda, Apple dapat membawa datanya lewat internet, terenkripsi sehingga hanya ponsel dan jam tangan Anda yang dapat membacanya.</string>'),
    ('3-4f-about-online', APPROVED, 'shared/src/commonMain/composeResources/values-in/strings.xml', 'id',
     '<string name="about_online_title">Online hanya bila Anda minta</string>',
     '<string name="about_online_title">Online hanya untuk yang Anda pilih</string>'),
    ('3-4f-about-online-android', APPROVED, 'shared/src/commonMain/composeResources/values-in/strings.xml', 'id',
     '<string name="about_online_body">',
     '<string name="about_online_body_android">Internet dipakai untuk tilawah yang Anda pilih untuk diunduh, dari repositori publik Taqwa di GitHub. Jika Anda menambahkan Taqwa ke jam tangan Anda, Google dapat membawa datanya lewat internet, terenkripsi sehingga hanya ponsel dan jam tangan Anda yang dapat membacanya.</string>'),
    ('3-4f-about-online-ios', APPROVED, 'shared/src/commonMain/composeResources/values-in/strings.xml', 'id',
     '<string name="about_online_body">',
     '<string name="about_online_body_ios">Internet dipakai untuk tilawah yang Anda pilih untuk diunduh, dari repositori publik Taqwa di GitHub. Jika Anda menambahkan Taqwa ke jam tangan Anda, Apple dapat membawa datanya lewat internet, terenkripsi sehingga hanya ponsel dan jam tangan Anda yang dapat membacanya.</string>'),
    ('3-4f-home-row', APPROVED, 'site/pages/id/home.html', 'id',
     '<b>Online hanya bila Anda minta</b><span>Internet dipakai untuk satu hal saja: tilawah yang Anda pilih untuk diunduh, dari repositori publik Taqwa di GitHub.</span>',
     '<b>Online hanya untuk yang Anda pilih</b><span>Internet dipakai untuk tilawah yang Anda pilih untuk diunduh, dari repositori publik Taqwa di GitHub. Jika Anda menambahkan Taqwa ke jam tangan Anda, Google atau Apple dapat membawa datanya lewat internet, terenkripsi sehingga hanya ponsel dan jam tangan Anda yang dapat membacanya.</span>'),
    ('3-4f-about-online', APPROVED, 'shared/src/commonMain/composeResources/values-ur/strings.xml', 'ur',
     '<string name="about_online_title">آن لائن صرف جب آپ کہیں</string>',
     '<string name="about_online_title">آن لائن صرف اس کے لیے جو آپ چنیں</string>'),
    ('3-4f-about-online-android', APPROVED, 'shared/src/commonMain/composeResources/values-ur/strings.xml', 'ur',
     '<string name="about_online_body">',
     '<string name="about_online_body_android">انٹرنیٹ اُن تلاوتوں کے لیے استعمال ہوتا ہے جو آپ خود ڈاؤن لوڈ کرنا چاہیں، GitHub پر تقویٰ کے عوامی مخزن سے۔ اگر آپ تقویٰ کو اپنی گھڑی میں شامل کریں تو Google اس کا ڈیٹا انٹرنیٹ کے ذریعے پہنچا سکتا ہے، خفیہ کاری کے ساتھ، تاکہ اسے صرف آپ کا فون اور آپ کی گھڑی پڑھ سکیں۔</string>'),
    ('3-4f-about-online-ios', APPROVED, 'shared/src/commonMain/composeResources/values-ur/strings.xml', 'ur',
     '<string name="about_online_body">',
     '<string name="about_online_body_ios">انٹرنیٹ اُن تلاوتوں کے لیے استعمال ہوتا ہے جو آپ خود ڈاؤن لوڈ کرنا چاہیں، GitHub پر تقویٰ کے عوامی مخزن سے۔ اگر آپ تقویٰ کو اپنی گھڑی میں شامل کریں تو Apple اس کا ڈیٹا انٹرنیٹ کے ذریعے پہنچا سکتا ہے، خفیہ کاری کے ساتھ، تاکہ اسے صرف آپ کا فون اور آپ کی گھڑی پڑھ سکیں۔</string>'),
    ('3-4f-home-row', APPROVED, 'site/pages/ur/home.html', 'ur',
     '<b>آن لائن صرف جب آپ کہیں</b><span>انٹرنیٹ صرف ایک کام کے لیے استعمال ہوتا ہے: وہ تلاوتیں جو آپ خود ڈاؤن لوڈ کرنا چاہیں، GitHub پر تقویٰ کے عوامی مخزن سے۔</span>',
     '<b>آن لائن صرف اس کے لیے جو آپ چنیں</b><span>انٹرنیٹ اُن تلاوتوں کے لیے استعمال ہوتا ہے جو آپ خود ڈاؤن لوڈ کرنا چاہیں، GitHub پر تقویٰ کے عوامی مخزن سے۔ اگر آپ تقویٰ کو اپنی گھڑی میں شامل کریں تو Google یا Apple اس کا ڈیٹا انٹرنیٹ کے ذریعے پہنچا سکتا ہے، خفیہ کاری کے ساتھ، تاکہ اسے صرف آپ کا فون اور آپ کی گھڑی پڑھ سکیں۔</span>'),
    ('3-4f-about-online', APPROVED, 'shared/src/commonMain/composeResources/values-bn/strings.xml', 'bn',
     '<string name="about_online_title">শুধু আপনি চাইলেই অনলাইন</string>',
     '<string name="about_online_title">শুধু আপনার বেছে নেওয়া কাজেই অনলাইন</string>'),
    ('3-4f-about-online-android', APPROVED, 'shared/src/commonMain/composeResources/values-bn/strings.xml', 'bn',
     '<string name="about_online_body">',
     '<string name="about_online_body_android">ইন্টারনেট লাগে আপনি যে তিলাওয়াতগুলো ডাউনলোড করতে চান সেগুলোর জন্য, GitHub-এ তাকওয়ার উন্মুক্ত রিপোজিটরি থেকে। তাকওয়া আপনার ঘড়িতে যোগ করলে এর তথ্য Google ইন্টারনেটের মাধ্যমে বহন করতে পারে, এনক্রিপ্ট করা অবস্থায়, যাতে শুধু আপনার ফোন ও ঘড়িই তা পড়তে পারে।</string>'),
    ('3-4f-about-online-ios', APPROVED, 'shared/src/commonMain/composeResources/values-bn/strings.xml', 'bn',
     '<string name="about_online_body">',
     '<string name="about_online_body_ios">ইন্টারনেট লাগে আপনি যে তিলাওয়াতগুলো ডাউনলোড করতে চান সেগুলোর জন্য, GitHub-এ তাকওয়ার উন্মুক্ত রিপোজিটরি থেকে। তাকওয়া আপনার ঘড়িতে যোগ করলে এর তথ্য Apple ইন্টারনেটের মাধ্যমে বহন করতে পারে, এনক্রিপ্ট করা অবস্থায়, যাতে শুধু আপনার ফোন ও ঘড়িই তা পড়তে পারে।</string>'),
    ('3-4f-home-row', APPROVED, 'site/pages/bn/home.html', 'bn',
     '<b>শুধু আপনি চাইলেই অনলাইন</b><span>ইন্টারনেট একটিমাত্র কাজে লাগে: আপনি যে তিলাওয়াতগুলো ডাউনলোড করতে চান, GitHub-এ তাকওয়ার উন্মুক্ত রিপোজিটরি থেকে সেগুলো আনা।</span>',
     '<b>শুধু আপনার বেছে নেওয়া কাজেই অনলাইন</b><span>ইন্টারনেট লাগে আপনি যে তিলাওয়াতগুলো ডাউনলোড করতে চান সেগুলোর জন্য, GitHub-এ তাকওয়ার উন্মুক্ত রিপোজিটরি থেকে। তাকওয়া আপনার ঘড়িতে যোগ করলে এর তথ্য Google বা Apple ইন্টারনেটের মাধ্যমে বহন করতে পারে, এনক্রিপ্ট করা অবস্থায়, যাতে শুধু আপনার ফোন ও ঘড়িই তা পড়তে পারে।</span>'),
    # 4g: the home page's privacy heading and the end of its lede.
    ('3-4g-home-heading', APPROVED, 'site/pages/en/home.html', 'en',
     '<h2>Offline by design. Online only when you ask.</h2>',
     '<h2>Offline by design. Online only for what you choose.</h2>'),
    ('3-4g-home-lede', APPROVED, 'site/pages/en/home.html', 'en',
     'Until then, it makes no network request at all.',
     'Until then, the app itself makes no network request. If you add Taqwa to your watch, Google or Apple may carry its data between your phone and watch over the internet, encrypted so only your phone and watch can read it.'),
    ('3-4g-home-heading', APPROVED, 'site/pages/ar/home.html', 'ar',
     '<h2>دون اتصال بالتصميم. ومتصل فقط عندما تطلب.</h2>',
     '<h2>دون اتصال بالتصميم. ومتصل فقط لما تختاره.</h2>'),
    ('3-4g-home-lede', APPROVED, 'site/pages/ar/home.html', 'ar',
     'وحتى تستخدم التلاوة للمرة الأولى، لا يرسل أي طلب عبر الشبكة على الإطلاق.',
     'وحتى تستخدم التلاوة للمرة الأولى، لا يرسل التطبيق نفسه أي طلب عبر الشبكة. وإن أضفت تقوى إلى ساعتك، فقد تنقل Google أو Apple بياناتها بين هاتفك وساعتك عبر الإنترنت، مشفّرةً فلا يقرؤها إلا هاتفك وساعتك.'),
    ('3-4g-home-heading', APPROVED, 'site/pages/fr/home.html', 'fr',
     '<h2>Hors ligne par conception. En ligne seulement quand vous le demandez.</h2>',
     '<h2>Hors ligne par conception. En ligne seulement pour ce que vous choisissez.</h2>'),
    ('3-4g-home-lede', APPROVED, 'site/pages/fr/home.html', 'fr',
     'Tant que vous n’avez pas utilisé la récitation, elle ne fait aucune requête réseau.',
     'Tant que vous n’avez pas utilisé la récitation, l’application elle-même ne fait aucune requête réseau. Si vous ajoutez Taqwa à votre montre, Google ou Apple peut acheminer ses données entre votre téléphone et votre montre par Internet, chiffrées pour que seuls votre téléphone et votre montre puissent les lire.'),
    ('3-4g-home-heading', APPROVED, 'site/pages/tr/home.html', 'tr',
     '<h2>Tasarımı gereği çevrimdışı. Yalnızca siz isteyince çevrimiçi.</h2>',
     '<h2>Tasarımı gereği çevrimdışı. Yalnızca seçtikleriniz için çevrimiçi.</h2>'),
    ('3-4g-home-lede', APPROVED, 'site/pages/tr/home.html', 'tr',
     'Tilaveti ilk kez kullanana kadar hiçbir ağ isteği yapmaz.',
     "Tilaveti ilk kez kullanana kadar uygulamanın kendisi hiçbir ağ isteği yapmaz. Taqwa'yı saatinize eklerseniz Google ya da Apple onun verilerini telefonunuzla saatiniz arasında internet üzerinden taşıyabilir; veriler şifrelidir, yalnızca telefonunuz ve saatiniz okuyabilir."),
    ('3-4g-home-heading', APPROVED, 'site/pages/id/home.html', 'id',
     '<h2>Offline sejak dirancang. Online hanya bila Anda minta.</h2>',
     '<h2>Offline sejak dirancang. Online hanya untuk yang Anda pilih.</h2>'),
    ('3-4g-home-lede', APPROVED, 'site/pages/id/home.html', 'id',
     'Sampai Anda pertama kali memakai tilawah, Taqwa tidak melakukan permintaan jaringan sama sekali.',
     'Sampai Anda pertama kali memakai tilawah, aplikasi Taqwa sendiri tidak melakukan permintaan jaringan. Jika Anda menambahkan Taqwa ke jam tangan Anda, Google atau Apple dapat membawa datanya antara ponsel dan jam tangan Anda lewat internet, terenkripsi sehingga hanya ponsel dan jam tangan Anda yang dapat membacanya.'),
    ('3-4g-home-heading', APPROVED, 'site/pages/ur/home.html', 'ur',
     '<h2>بنیادی طور پر آف لائن۔ آن لائن صرف جب آپ کہیں۔</h2>',
     '<h2>بنیادی طور پر آف لائن۔ آن لائن صرف اس کے لیے جو آپ چنیں۔</h2>'),
    ('3-4g-home-lede', APPROVED, 'site/pages/ur/home.html', 'ur',
     'جب تک آپ پہلی بار تلاوت استعمال نہ کریں، ایپ کوئی نیٹ ورک درخواست کرتی ہی نہیں۔',
     'جب تک آپ پہلی بار تلاوت استعمال نہ کریں، ایپ خود کوئی نیٹ ورک درخواست نہیں کرتی۔ اگر آپ تقویٰ کو اپنی گھڑی میں شامل کریں تو Google یا Apple اس کا ڈیٹا آپ کے فون اور گھڑی کے درمیان انٹرنیٹ کے ذریعے پہنچا سکتا ہے، خفیہ کاری کے ساتھ، تاکہ اسے صرف آپ کا فون اور آپ کی گھڑی پڑھ سکیں۔'),
    ('3-4g-home-heading', APPROVED, 'site/pages/bn/home.html', 'bn',
     '<h2>গোড়া থেকেই অফলাইন। শুধু আপনি চাইলেই অনলাইন।</h2>',
     '<h2>গোড়া থেকেই অফলাইন। শুধু আপনার বেছে নেওয়া কাজেই অনলাইন।</h2>'),
    ('3-4g-home-lede', APPROVED, 'site/pages/bn/home.html', 'bn',
     'আপনি প্রথমবার তিলাওয়াত ব্যবহার না করা পর্যন্ত অ্যাপটি কোনো নেটওয়ার্ক অনুরোধই করে না।',
     'আপনি প্রথমবার তিলাওয়াত ব্যবহার না করা পর্যন্ত অ্যাপটি নিজে কোনো নেটওয়ার্ক অনুরোধ করে না। তাকওয়া আপনার ঘড়িতে যোগ করলে এর তথ্য Google বা Apple আপনার ফোন ও ঘড়ির মধ্যে ইন্টারনেটের মাধ্যমে বহন করতে পারে, এনক্রিপ্ট করা অবস্থায়, যাতে শুধু আপনার ফোন ও ঘড়িই তা পড়তে পারে।'),
    # Ruling 69 item 1 (31 October 2026): the lede's recitation sentence, which still said it was
    # the one thing Taqwa uses the internet for, takes the approved store bullet's words (4l);
    # the watch clause is the lede's own approved last sentence (4g-home-lede, above).
    ('3-4g-home-lede-recitation', APPROVED, 'site/pages/en/home.html', 'en',
     'The one thing it uses the internet for is recitation',
     'The internet is used for recitation: fetching a surah when you download it or play one not yet downloaded, and, once you have used recitation, checking once a day for an updated list of reciters.'),
    ('3-4g-home-lede-recitation', APPROVED, 'site/pages/ar/home.html', 'ar',
     'الشيء الوحيد الذي يستخدم الإنترنت لأجله هو التلاوة',
     'يستخدم التطبيق الإنترنت للتلاوة: جلب السورة حين تنزّلها أو تشغّل سورة لم تنزّلها بعد، والتحقق مرة في اليوم من وجود قائمة قرّاء محدّثة بعد أن تستخدم التلاوة.'),
    ('3-4g-home-lede-recitation', APPROVED, 'site/pages/fr/home.html', 'fr',
     'La seule chose pour laquelle l’application utilise Internet, c’est la récitation',
     'Internet sert à la récitation\u202f: récupérer une sourate quand vous la téléchargez ou en écoutez une pas encore téléchargée, et, une fois que vous avez utilisé la récitation, vérifier une fois par jour s’il existe une liste de récitateurs plus récente.'),
    ('3-4g-home-lede-recitation', APPROVED, 'site/pages/tr/home.html', 'tr',
     'İnterneti tek bir şey için kullanır: tilavet',
     'İnternet tilavet için kullanılır. Bir sureyi indirdiğinizde ya da henüz indirmediğiniz bir sureyi dinlediğinizde onu alır; tilaveti kullanmaya başladıktan sonra da günde bir kez güncellenmiş bir kari listesi olup olmadığını kontrol eder.'),
    ('3-4g-home-lede-recitation', APPROVED, 'site/pages/id/home.html', 'id',
     'Satu-satunya hal yang membuatnya memakai internet adalah tilawah',
     'Internet dipakai untuk tilawah: mengambil surah saat Anda mengunduhnya atau memutar surah yang belum diunduh, dan, setelah Anda memakai tilawah, memeriksa sekali sehari apakah ada daftar qari yang diperbarui.'),
    ('3-4g-home-lede-recitation', APPROVED, 'site/pages/ur/home.html', 'ur',
     'انٹرنیٹ صرف ایک کام کے لیے استعمال ہوتا ہے: تلاوت',
     'انٹرنیٹ تلاوت کے لیے استعمال ہوتا ہے۔ جب آپ کوئی سورۃ ڈاؤن لوڈ کریں یا کوئی ایسی سورۃ چلائیں جو ابھی ڈاؤن لوڈ نہیں ہوئی، تو وہ لائی جاتی ہے؛ اور ایک بار تلاوت استعمال کرنے کے بعد دن میں ایک بار قاریوں کی تازہ فہرست دیکھی جاتی ہے۔'),
    ('3-4g-home-lede-recitation', APPROVED, 'site/pages/bn/home.html', 'bn',
     'ইন্টারনেট একটিমাত্র কাজে লাগে — তিলাওয়াত',
     'ইন্টারনেট ব্যবহার হয় তিলাওয়াতের জন্য: আপনি কোনো সূরা ডাউনলোড করলে বা ডাউনলোড হয়নি এমন কোনো সূরা শুনলে সেটি আনা, আর একবার তিলাওয়াত ব্যবহারের পর দিনে একবার কারিদের হালনাগাদ তালিকা দেখে নেওয়া।'),
    # 4h: the privacy page's description.
    ('3-4h-privacy-description', APPROVED, 'site/pages/en/meta.json', 'en',
     '"description": "What Taqwa stores on your phone, the one thing it uses the internet for, and what it never does."',
     '"description": "What Taqwa stores on your devices, what it uses the internet for, and what it never does."'),
    ('3-4h-privacy-description', APPROVED, 'site/pages/ar/meta.json', 'ar',
     '"description": "ما يخزّنه تقوى على هاتفك، والشيء الوحيد الذي يستخدم الإنترنت لأجله، وما لا يفعله أبدًا."',
     '"description": "ما يخزّنه تقوى على أجهزتك، وما يستخدم الإنترنت لأجله، وما لا يفعله أبدًا."'),
    ('3-4h-privacy-description', APPROVED, 'site/pages/fr/meta.json', 'fr',
     '"description": "Ce que Taqwa enregistre sur votre téléphone, la seule chose pour laquelle elle utilise Internet, et ce qu’elle ne fait jamais."',
     '"description": "Ce que Taqwa enregistre sur vos appareils, ce pour quoi elle utilise Internet, et ce qu’elle ne fait jamais."'),
    ('3-4h-privacy-description', APPROVED, 'site/pages/tr/meta.json', 'tr',
     '"description": "Taqwa\'nın telefonunuzda ne sakladığı, interneti kullandığı tek şey ve asla yapmadıkları."',
     '"description": "Taqwa\'nın cihazlarınızda ne sakladığı, interneti ne için kullandığı ve asla yapmadıkları."'),
    ('3-4h-privacy-description', APPROVED, 'site/pages/id/meta.json', 'id',
     '"description": "Apa yang disimpan Taqwa di ponsel Anda, satu-satunya hal yang membuatnya memakai internet, dan apa yang tidak pernah dilakukannya."',
     '"description": "Apa yang disimpan Taqwa di perangkat Anda, untuk apa ia memakai internet, dan apa yang tidak pernah dilakukannya."'),
    ('3-4h-privacy-description', APPROVED, 'site/pages/ur/meta.json', 'ur',
     '"description": "تقویٰ آپ کے فون پر کیا محفوظ رکھتا ہے، انٹرنیٹ کو صرف کس ایک کام کے لیے استعمال کرتا ہے، اور کیا کبھی نہیں کرتا۔"',
     '"description": "تقویٰ آپ کے آلات پر کیا محفوظ رکھتا ہے، انٹرنیٹ کو کس کام کے لیے استعمال کرتا ہے، اور کیا کبھی نہیں کرتا۔"'),
    ('3-4h-privacy-description', APPROVED, 'site/pages/bn/meta.json', 'bn',
     '"description": "তাকওয়া আপনার ফোনে কী জমা রাখে, একটিমাত্র কোন কাজে ইন্টারনেট ব্যবহার করে, আর কী কখনোই করে না।"',
     '"description": "তাকওয়া আপনার ডিভাইসে কী জমা রাখে, কোন কাজে ইন্টারনেট ব্যবহার করে, আর কী কখনোই করে না।"'),
    # 4i: the policy's opening, its last sentence as Mohamed changed it at M3 (31 October 2026).
    ('3-4i-policy-opening', APPROVED, 'PRIVACY.md', 'en',
     'It works offline. The one thing it uses the internet for is downloading Quran recitations, and only when you ask it to.',
     'It works offline. It uses the internet to download Quran recitations, and only when you ask it to. If you add Taqwa to your watch, its data may travel over the internet too, encrypted, as below.'),
    ('3-4i-policy-opening', APPROVED, 'PRIVACY.ar.md', 'ar',
     'يعمل دون اتصال بالإنترنت. الشيء الوحيد الذي يستخدم الإنترنت لأجله هو تنزيل تلاوات القرآن، وفقط حين تطلب ذلك.',
     'يعمل دون اتصال بالإنترنت. ويستخدم الإنترنت لتنزيل تلاوات القرآن، وفقط حين تطلب ذلك. وإن أضفت تقوى إلى ساعتك، فقد تنتقل بياناته عبر الإنترنت أيضًا، مشفّرةً، كما يأتي أدناه.'),
    ('3-4i-policy-opening', APPROVED, 'PRIVACY.fr.md', 'fr',
     'Elle fonctionne hors ligne. La seule chose pour laquelle elle utilise Internet est le téléchargement des récitations du Coran, et uniquement lorsque vous le lui demandez.',
     'Elle fonctionne hors ligne. Elle utilise Internet pour télécharger les récitations du Coran, et uniquement lorsque vous le lui demandez. Si vous ajoutez Taqwa à votre montre, ses données peuvent aussi transiter par Internet, chiffrées, comme expliqué plus bas.'),
    ('3-4i-policy-opening', APPROVED, 'PRIVACY.tr.md', 'tr',
     "Çevrimdışı çalışır. İnterneti tek bir şey için kullanır: Kur'an tilavetlerini indirmek, o da yalnızca siz istediğinizde.",
     "Çevrimdışı çalışır. İnterneti Kur'an tilavetlerini indirmek için kullanır, o da yalnızca siz istediğinizde. Taqwa'yı saatinize eklerseniz, verileri de aşağıda anlatıldığı gibi şifreli olarak internet üzerinden iletilebilir."),
    ('3-4i-policy-opening', APPROVED, 'PRIVACY.id.md', 'id',
     "Aplikasi ini berfungsi offline. Satu-satunya hal yang membuatnya memakai internet adalah mengunduh tilawah Al-Qur'an, dan hanya ketika Anda memintanya.",
     "Aplikasi ini berfungsi offline. Ia memakai internet untuk mengunduh tilawah Al-Qur'an, dan hanya ketika Anda memintanya. Jika Anda menambahkan Taqwa ke jam tangan Anda, datanya juga dapat dikirim lewat internet, secara terenkripsi, seperti dijelaskan di bawah."),
    ('3-4i-policy-opening', APPROVED, 'PRIVACY.ur.md', 'ur',
     'یہ آف لائن کام کرتی ہے۔ انٹرنیٹ کو یہ صرف ایک کام کے لیے استعمال کرتی ہے: قرآن کی تلاوتوں کا ڈاؤن لوڈ، اور وہ بھی صرف اُس وقت جب آپ کہیں۔',
     'یہ آف لائن کام کرتی ہے۔ انٹرنیٹ کو یہ قرآن کی تلاوتیں ڈاؤن لوڈ کرنے کے لیے استعمال کرتی ہے، اور وہ بھی صرف اُس وقت جب آپ کہیں۔ اگر آپ تقویٰ کو اپنی گھڑی میں شامل کریں تو اس کا ڈیٹا بھی انٹرنیٹ کے ذریعے، خفیہ کاری کے ساتھ، منتقل ہو سکتا ہے، جیسا کہ نیچے بیان ہے۔'),
    ('3-4i-policy-opening', APPROVED, 'PRIVACY.bn.md', 'bn',
     'এটি ইন্টারনেট ছাড়াই চলে। ইন্টারনেট এটি একটিমাত্র কাজে ব্যবহার করে: কুরআনের তিলাওয়াত ডাউনলোড, আর তা কেবল আপনি চাইলে।',
     'এটি ইন্টারনেট ছাড়াই চলে। কুরআনের তিলাওয়াত ডাউনলোড করতে এটি ইন্টারনেট ব্যবহার করে, আর তা কেবল আপনি চাইলে। তাকওয়া আপনার ঘড়িতে যোগ করলে এর তথ্যও ইন্টারনেটের মাধ্যমে, এনক্রিপ্ট করা অবস্থায়, যেতে পারে, যেমনটি নিচে বলা আছে।'),
    # Ruling 69 item 1: the drafted opening's last sentence (question 7's draft, before M3) in
    # each language, so that a copy left behind in a policy fails by its row; the new text is his
    # sentence, as the rows above end.
    ('3-4i-policy-opening-drafted', APPROVED, 'PRIVACY.md', 'en',
     'A watch you add Taqwa to may be reached over the internet too, encrypted, as below.',
     'If you add Taqwa to your watch, its data may travel over the internet too, encrypted, as below.'),
    ('3-4i-policy-opening-drafted', APPROVED, 'PRIVACY.ar.md', 'ar',
     'الساعة التي تضيف إليها تقوى قد تصلها بياناته عبر الإنترنت أيضًا، مشفّرةً، كما يأتي أدناه.',
     'وإن أضفت تقوى إلى ساعتك، فقد تنتقل بياناته عبر الإنترنت أيضًا، مشفّرةً، كما يأتي أدناه.'),
    ('3-4i-policy-opening-drafted', APPROVED, 'PRIVACY.fr.md', 'fr',
     'Une montre à laquelle vous ajoutez Taqwa peut aussi être jointe par Internet, de façon chiffrée, comme expliqué plus bas.',
     'Si vous ajoutez Taqwa à votre montre, ses données peuvent aussi transiter par Internet, chiffrées, comme expliqué plus bas.'),
    ('3-4i-policy-opening-drafted', APPROVED, 'PRIVACY.tr.md', 'tr',
     "Taqwa'yı eklediğiniz bir saate de, aşağıda anlatıldığı gibi, şifreli olarak internet üzerinden ulaşılabilir.",
     "Taqwa'yı saatinize eklerseniz, verileri de aşağıda anlatıldığı gibi şifreli olarak internet üzerinden iletilebilir."),
    ('3-4i-policy-opening-drafted', APPROVED, 'PRIVACY.id.md', 'id',
     'Jam tangan yang Anda tambahkan Taqwa juga dapat dijangkau lewat internet, secara terenkripsi, seperti dijelaskan di bawah.',
     'Jika Anda menambahkan Taqwa ke jam tangan Anda, datanya juga dapat dikirim lewat internet, secara terenkripsi, seperti dijelaskan di bawah.'),
    ('3-4i-policy-opening-drafted', APPROVED, 'PRIVACY.ur.md', 'ur',
     'جس گھڑی میں آپ تقویٰ شامل کریں، اس تک بھی انٹرنیٹ کے ذریعے پہنچا جا سکتا ہے، خفیہ کاری کے ساتھ، جیسا کہ نیچے بیان ہے۔',
     'اگر آپ تقویٰ کو اپنی گھڑی میں شامل کریں تو اس کا ڈیٹا بھی انٹرنیٹ کے ذریعے، خفیہ کاری کے ساتھ، منتقل ہو سکتا ہے، جیسا کہ نیچے بیان ہے۔'),
    ('3-4i-policy-opening-drafted', APPROVED, 'PRIVACY.bn.md', 'bn',
     'আপনি যে ঘড়িতে তাকওয়া যোগ করেন, তার কাছেও ইন্টারনেটের মাধ্যমে, এনক্রিপ্ট করা অবস্থায়, তথ্য পৌঁছাতে পারে, যেমনটি নিচে বলা আছে।',
     'তাকওয়া আপনার ঘড়িতে যোগ করলে এর তথ্যও ইন্টারনেটের মাধ্যমে, এনক্রিপ্ট করা অবস্থায়, যেতে পারে, যেমনটি নিচে বলা আছে।'),
    # 4j: "Until you first use recitation".
    ('3-4j-policy-first-request', APPROVED, 'PRIVACY.md', 'en',
     'Until you first use recitation, the app makes no network request at all.',
     'Until you first use recitation, the app itself makes no network request at all.'),
    ('3-4j-policy-first-request', APPROVED, 'PRIVACY.ar.md', 'ar',
     'وحتى تستخدم التلاوة للمرة الأولى، لا يرسل التطبيق أي طلب عبر الشبكة على الإطلاق.',
     'وحتى تستخدم التلاوة للمرة الأولى، لا يرسل التطبيق نفسه أي طلب عبر الشبكة على الإطلاق.'),
    ('3-4j-policy-first-request', APPROVED, 'PRIVACY.fr.md', 'fr',
     'Tant que vous n’avez pas utilisé la récitation une première fois, l’application ne fait aucune requête réseau.',
     'Tant que vous n’avez pas utilisé la récitation une première fois, l’application elle-même ne fait aucune requête réseau.'),
    ('3-4j-policy-first-request', APPROVED, 'PRIVACY.tr.md', 'tr',
     'Tilaveti ilk kez kullanana kadar uygulama hiçbir ağ isteği yapmaz.',
     'Tilaveti ilk kez kullanana kadar uygulamanın kendisi hiçbir ağ isteği yapmaz.'),
    ('3-4j-policy-first-request', APPROVED, 'PRIVACY.id.md', 'id',
     'Sampai Anda pertama kali memakai tilawah, aplikasi ini tidak melakukan permintaan jaringan sama sekali.',
     'Sampai Anda pertama kali memakai tilawah, aplikasi ini sendiri tidak melakukan permintaan jaringan sama sekali.'),
    ('3-4j-policy-first-request', APPROVED, 'PRIVACY.ur.md', 'ur',
     'جب تک آپ پہلی بار تلاوت استعمال نہ کریں، ایپ کوئی نیٹ ورک درخواست کرتی ہی نہیں۔',
     'جب تک آپ پہلی بار تلاوت استعمال نہ کریں، ایپ خود کوئی نیٹ ورک درخواست کرتی ہی نہیں۔'),
    ('3-4j-policy-first-request', APPROVED, 'PRIVACY.bn.md', 'bn',
     'আপনি প্রথমবার তিলাওয়াত ব্যবহার না করা পর্যন্ত অ্যাপটি কোনো নেটওয়ার্ক অনুরোধই করে না।',
     'আপনি প্রথমবার তিলাওয়াত ব্যবহার না করা পর্যন্ত অ্যাপটি নিজে কোনো নেটওয়ার্ক অনুরোধই করে না।'),
    # 4k: "No third-party SDK".
    ('3-4k-policy-sdk', APPROVED, 'PRIVACY.md', 'en',
     '- No third-party SDK that talks to the internet.',
     "- No third-party SDK that talks to the internet. The Android app's one Google Play services library links your phone to your own watch, and Google may relay that link, encrypted, as above."),
    ('3-4k-policy-sdk', APPROVED, 'PRIVACY.ar.md', 'ar',
     '- لا مكتبات من طرف ثالث تتصل بالإنترنت.',
     '- لا مكتبات من طرف ثالث تتصل بالإنترنت. ومكتبة خدمات Google Play الوحيدة في تطبيق أندرويد تربط هاتفك بساعتك أنت، وقد تنقل Google هذا الاتصال، مشفّرًا، كما سبق.'),
    ('3-4k-policy-sdk', APPROVED, 'PRIVACY.fr.md', 'fr',
     '- Aucun SDK tiers qui communique avec Internet.',
     '- Aucun SDK tiers qui communique avec Internet. La seule bibliothèque des services Google Play de l’application Android relie votre téléphone à votre propre montre, et Google peut relayer ce lien, chiffré, comme indiqué plus haut.'),
    ('3-4k-policy-sdk', APPROVED, 'PRIVACY.tr.md', 'tr',
     '- İnternete bağlanan üçüncü taraf SDK yok.',
     '- İnternete bağlanan üçüncü taraf SDK yok. Android uygulamasındaki tek Google Play hizmetleri kütüphanesi telefonunuzu kendi saatinize bağlar; Google bu bağlantıyı, yukarıda anlatıldığı gibi şifreli olarak aktarabilir.'),
    ('3-4k-policy-sdk', APPROVED, 'PRIVACY.id.md', 'id',
     '- Tanpa SDK pihak ketiga yang berhubungan dengan internet.',
     '- Tanpa SDK pihak ketiga yang berhubungan dengan internet. Satu-satunya pustaka layanan Google Play di aplikasi Android menghubungkan ponsel Anda dengan jam tangan Anda sendiri, dan Google dapat meneruskan sambungan itu, terenkripsi, seperti di atas.'),
    ('3-4k-policy-sdk', APPROVED, 'PRIVACY.ur.md', 'ur',
     '- کوئی ایسا فریقِ ثالث SDK نہیں جو انٹرنیٹ سے بات کرے۔',
     '- کوئی ایسا فریقِ ثالث SDK نہیں جو انٹرنیٹ سے بات کرے۔ اینڈرائیڈ ایپ کی واحد Google Play سروسز لائبریری آپ کے فون کو آپ کی اپنی گھڑی سے جوڑتی ہے، اور Google یہ رابطہ، خفیہ کاری کے ساتھ، آگے پہنچا سکتا ہے، جیسا کہ اوپر بیان ہوا۔'),
    ('3-4k-policy-sdk', APPROVED, 'PRIVACY.bn.md', 'bn',
     '- ইন্টারনেটের সঙ্গে কথা বলে এমন কোনো তৃতীয় পক্ষের SDK নেই।',
     '- ইন্টারনেটের সঙ্গে কথা বলে এমন কোনো তৃতীয় পক্ষের SDK নেই। অ্যান্ড্রয়েড অ্যাপের একমাত্র Google Play পরিষেবা লাইব্রেরিটি আপনার ফোনকে আপনার নিজের ঘড়ির সঙ্গে যুক্ত করে, আর Google সেই সংযোগ, এনক্রিপ্ট করা অবস্থায়, পৌঁছে দিতে পারে, যেমনটি ওপরে বলা আছে।'),
    # 4l: the listings' internet bullet, once in each store's own description (plan 5 Task 10):
    # Google's relay in the Play full description and Apple's in the App Store's, as each store
    # names only its own watch (App Store Review Guideline 2.3.10; R63a's forward note).
    ('3-4l-listing-internet-play', APPROVED, 'docs/store/listing/en.md', 'en',
     '• The internet is used only for recitation: the audio you ask for, and a daily check of the reciter list once you have used it.',
     '• The internet is used for recitation: the audio you ask for, and a daily check of the reciter list once you have used it. If you add Taqwa to your watch, Google may carry its data over the internet, encrypted.'),
    ('3-4l-listing-internet-play', APPROVED, 'docs/store/listing/ar.md', 'ar',
     '• لا يستخدم التطبيق الإنترنت إلا للتلاوة: الصوت الذي تطلبه، وفحص يومي لقائمة القرّاء بعد أن تستخدمها.',
     '• يستخدم التطبيق الإنترنت للتلاوة: الصوت الذي تطلبه، وفحص يومي لقائمة القرّاء بعد أن تستخدمها. وإن أضفت تقوى إلى ساعتك، فقد تنقل Google بياناتها عبر الإنترنت، مشفّرةً.'),
    ('3-4l-listing-internet-play', APPROVED, 'docs/store/listing/fr.md', 'fr',
     '• Internet ne sert qu’à la récitation : l’audio demandé et, dès lors, une vérification quotidienne de la liste des récitateurs.',
     '• Internet sert à la récitation : l’audio demandé et, dès lors, une vérification quotidienne de la liste des récitateurs. Si vous ajoutez Taqwa à votre montre, Google peut acheminer ses données par Internet, chiffrées.'),
    ('3-4l-listing-internet-play', APPROVED, 'docs/store/listing/tr.md', 'tr',
     '• İnternet yalnızca tilavet için kullanılır: istediğiniz ses kayıtları ve, tilaveti kullandıktan sonra, kari listesinin günde bir kez denetlenmesi.',
     '• İnternet tilavet için kullanılır: istediğiniz ses kayıtları ve, tilaveti kullandıktan sonra, kari listesinin günde bir kez denetlenmesi. Taqwa’yı saatinize eklerseniz Google onun verilerini internet üzerinden şifreli olarak taşıyabilir.'),
    ('3-4l-listing-internet-play', APPROVED, 'docs/store/listing/id.md', 'id',
     '• Internet hanya dipakai untuk tilawah: audio yang Anda minta, dan pemeriksaan harian daftar qari setelah Anda memakainya.',
     '• Internet dipakai untuk tilawah: audio yang Anda minta, dan pemeriksaan harian daftar qari setelah Anda memakainya. Jika Anda menambahkan Taqwa ke jam tangan, Google dapat membawa datanya lewat internet, secara terenkripsi.'),
    ('3-4l-listing-internet-play', APPROVED, 'docs/store/listing/ur.md', 'ur',
     '• انٹرنیٹ صرف تلاوت کے لیے استعمال ہوتا ہے: وہ آڈیو جو آپ خود مانگیں، اور تلاوت استعمال کرنے کے بعد قاریوں کی فہرست کی روزانہ جانچ۔',
     '• انٹرنیٹ تلاوت کے لیے استعمال ہوتا ہے: وہ آڈیو جو آپ خود مانگیں، اور تلاوت استعمال کرنے کے بعد قاریوں کی فہرست کی روزانہ جانچ۔ اگر آپ تقویٰ کو اپنی گھڑی میں شامل کریں تو Google اس کا ڈیٹا انٹرنیٹ کے ذریعے، خفیہ کاری کے ساتھ، پہنچا سکتا ہے۔'),
    ('3-4l-listing-internet-play', APPROVED, 'docs/store/listing/bn.md', 'bn',
     '• ইন্টারনেট ব্যবহার হয় শুধু তিলাওয়াতের জন্য: আপনি যে অডিও চান, আর তিলাওয়াত একবার ব্যবহারের পর দিনে একবার কারিদের তালিকা যাচাই।',
     '• ইন্টারনেট ব্যবহার হয় তিলাওয়াতের জন্য: আপনি যে অডিও চান, আর তিলাওয়াত একবার ব্যবহারের পর দিনে একবার কারিদের তালিকা যাচাই। তাকওয়া আপনার ঘড়িতে যোগ করলে এর তথ্য Google ইন্টারনেটের মাধ্যমে, এনক্রিপ্ট করা অবস্থায়, বহন করতে পারে।'),
    ('3-4l-listing-internet-app-store', APPROVED, 'docs/store/listing/en.md', 'en',
     '• The internet is used only for recitation: the audio you ask for, and a daily check of the reciter list once you have used it.',
     '• The internet is used for recitation: the audio you ask for, and a daily check of the reciter list once you have used it. If you add Taqwa to your watch, Apple may carry its data over the internet, encrypted.'),
    ('3-4l-listing-internet-app-store', APPROVED, 'docs/store/listing/ar.md', 'ar',
     '• لا يستخدم التطبيق الإنترنت إلا للتلاوة: الصوت الذي تطلبه، وفحص يومي لقائمة القرّاء بعد أن تستخدمها.',
     '• يستخدم التطبيق الإنترنت للتلاوة: الصوت الذي تطلبه، وفحص يومي لقائمة القرّاء بعد أن تستخدمها. وإن أضفت تقوى إلى ساعتك، فقد تنقل Apple بياناتها عبر الإنترنت، مشفّرةً.'),
    ('3-4l-listing-internet-app-store', APPROVED, 'docs/store/listing/fr.md', 'fr',
     '• Internet ne sert qu’à la récitation : l’audio demandé et, dès lors, une vérification quotidienne de la liste des récitateurs.',
     '• Internet sert à la récitation : l’audio demandé et, dès lors, une vérification quotidienne de la liste des récitateurs. Si vous ajoutez Taqwa à votre montre, Apple peut acheminer ses données par Internet, chiffrées.'),
    ('3-4l-listing-internet-app-store', APPROVED, 'docs/store/listing/tr.md', 'tr',
     '• İnternet yalnızca tilavet için kullanılır: istediğiniz ses kayıtları ve, tilaveti kullandıktan sonra, kari listesinin günde bir kez denetlenmesi.',
     '• İnternet tilavet için kullanılır: istediğiniz ses kayıtları ve, tilaveti kullandıktan sonra, kari listesinin günde bir kez denetlenmesi. Taqwa’yı saatinize eklerseniz Apple onun verilerini internet üzerinden şifreli olarak taşıyabilir.'),
    ('3-4l-listing-internet-app-store', APPROVED, 'docs/store/listing/id.md', 'id',
     '• Internet hanya dipakai untuk tilawah: audio yang Anda minta, dan pemeriksaan harian daftar qari setelah Anda memakainya.',
     '• Internet dipakai untuk tilawah: audio yang Anda minta, dan pemeriksaan harian daftar qari setelah Anda memakainya. Jika Anda menambahkan Taqwa ke jam tangan, Apple dapat membawa datanya lewat internet, secara terenkripsi.'),
    ('3-4l-listing-internet-app-store', APPROVED, 'docs/store/listing/ur.md', 'ur',
     '• انٹرنیٹ صرف تلاوت کے لیے استعمال ہوتا ہے: وہ آڈیو جو آپ خود مانگیں، اور تلاوت استعمال کرنے کے بعد قاریوں کی فہرست کی روزانہ جانچ۔',
     '• انٹرنیٹ تلاوت کے لیے استعمال ہوتا ہے: وہ آڈیو جو آپ خود مانگیں، اور تلاوت استعمال کرنے کے بعد قاریوں کی فہرست کی روزانہ جانچ۔ اگر آپ تقویٰ کو اپنی گھڑی میں شامل کریں تو Apple اس کا ڈیٹا انٹرنیٹ کے ذریعے، خفیہ کاری کے ساتھ، پہنچا سکتا ہے۔'),
    ('3-4l-listing-internet-app-store', APPROVED, 'docs/store/listing/bn.md', 'bn',
     '• ইন্টারনেট ব্যবহার হয় শুধু তিলাওয়াতের জন্য: আপনি যে অডিও চান, আর তিলাওয়াত একবার ব্যবহারের পর দিনে একবার কারিদের তালিকা যাচাই।',
     '• ইন্টারনেট ব্যবহার হয় তিলাওয়াতের জন্য: আপনি যে অডিও চান, আর তিলাওয়াত একবার ব্যবহারের পর দিনে একবার কারিদের তালিকা যাচাই। তাকওয়া আপনার ঘড়িতে যোগ করলে এর তথ্য Apple ইন্টারনেটের মাধ্যমে, এনক্রিপ্ট করা অবস্থায়, বহন করতে পারে।'),
    # 4m: the English-only files.
    ('3-4m-contributing-promise', APPROVED, 'CONTRIBUTING.md', 'en',
     '**Nothing leaves the phone.**',
     "**Nothing leaves the person's own devices.**"),
    ('3-4m-contributing-constraints', APPROVED, 'CONTRIBUTING.md', 'en',
     'No network calls beyond the recitation downloads the person asked for, no third-party SDKs that phone home,',
     "No network calls beyond the recitation downloads the person asked for and the link to the person's own watch, no third-party SDKs that phone home (Google's Wear OS library, for the person's own watch, is the one exception),"),
    ('3-4m-readme-constraints', APPROVED, 'README.md', 'en',
     'keep the constraints (no network calls beyond the recitation downloads the person asked for, no third-party SDKs that phone home)',
     "keep the constraints (no network calls beyond the recitation downloads the person asked for and the link to the person's own watch, no third-party SDKs that phone home (Google's Wear OS library, for the person's own watch, is the one exception))"),
    ('3-4m-security-promise', APPROVED, 'SECURITY.md', 'en',
     "Taqwa's promise is that nothing about you leaves your phone.",
     "Taqwa's promise is that only your own devices can see anything about you."),
    ('3-4m-security-network', APPROVED, 'SECURITY.md', 'en',
     'The one network use is downloading the Quran recitations you ask for.',
     "The one network use is downloading the Quran recitations you ask for; the link to your own watch is Google's or Apple's, end-to-end encrypted."),
    ('3-4m-security-data-leaving', APPROVED, 'SECURITY.md', 'en',
     '**Data leaving the phone:** anything that sends off the phone what the [privacy policy](PRIVACY.md) says stays on it, such as your location, settings, bookmarks, counts or the crash report.',
     '**Data leaving your own devices:** anything that sends what the [privacy policy](PRIVACY.md) says stays on them, such as your location, settings, bookmarks, counts or the crash report, to anyone or anywhere else.'),
    ('3-4m-security-third-party', APPROVED, 'SECURITY.md', 'en',
     "GitHub, Google Play, the App Store, Cloudflare and the phone's own text-to-speech engine",
     "GitHub, Google Play, the App Store, Google's and Apple's watch links, Cloudflare and the phone's own text-to-speech engine"),
    ('3-4m-readme-about', APPROVED, 'README.md', 'en',
     'Settings › About Taqwa says what stays on the phone and what the internet is used for,',
     'Settings › About Taqwa says what stays on your own devices and what the internet is used for,'),
    ('3-4m-readme-principle', APPROVED, 'README.md', 'en',
     'The only network use is recitation downloads from a public GitHub repository, on request.',
     "The app's only network use is recitation downloads from a public GitHub repository, on request; the link to the person's own watch is Google's or Apple's."),
    ('3-4m-readme-privacy-request', APPROVED, 'README.md', 'en',
     'The app makes no network request until you use Quran recitation.',
     'The app itself makes no network request until you use Quran recitation; the link to your own watch may be relayed by Google or Apple, encrypted.'),
    ('3-4m-readme-privacy-sdk', APPROVED, 'README.md', 'en',
     'There are no analytics, no crash-reporting SDKs and no third-party SDKs that talk to the internet;',
     "There are no analytics, no crash-reporting SDKs and no third-party SDKs that talk to the internet, apart from Google's watch link on Android;"),
    # Plan 5 Task 10's watch part of the README (P5.10 review round 1): Ruling 39's sentence, then
    # the policy's own list by its link, not a shorter list of the README's. Its first draft, a
    # second promise ("Your location stays yours.") naming the city and the bearing alone, is the
    # old text, so it cannot come back.
    ('3-4m-readme-watch', APPROVED, 'README.md', 'en',
     '**Your location stays yours.**',
     "**Only your own devices can see your location.** Neither watch app asks for it, and the phone never gives the watch your coordinates. What it does give the watch, and who may carry it, is in the privacy policy's [Your watch](PRIVACY.md#your-watch) section."),
    # 4n: the stores' review notes (English in every listing that carries them), the new App
    # Store Connect notes file, and the pointer to it (the pointer is no wording, so it is not
    # awaiting).
    ('3-4n-review-notes', APPROVED, 'docs/store/listing/en.md', 'en',
     'Nothing is collected or sent anywhere;',
     "Nothing is collected, and nothing is sent anywhere but to the user's own watch, if they add Taqwa to it;"),
    ('3-4n-review-notes', APPROVED, 'docs/store/listing/fr.md', 'en',
     'Nothing is collected or sent anywhere;',
     "Nothing is collected, and nothing is sent anywhere but to the user's own watch, if they add Taqwa to it;"),
    ('3-4n-review-notes', APPROVED, 'docs/store/listing/tr.md', 'en',
     'Nothing is collected or sent anywhere;',
     "Nothing is collected, and nothing is sent anywhere but to the user's own watch, if they add Taqwa to it;"),
    ('3-4n-review-notes', APPROVED, 'docs/store/listing/id.md', 'en',
     'Nothing is collected or sent anywhere;',
     "Nothing is collected, and nothing is sent anywhere but to the user's own watch, if they add Taqwa to it;"),
    ('3-4n-review-notes', APPROVED, 'docs/store/listing/ur.md', 'en',
     'Nothing is collected or sent anywhere;',
     "Nothing is collected, and nothing is sent anywhere but to the user's own watch, if they add Taqwa to it;"),
    ('3-4n-review-notes', APPROVED, 'docs/store/listing/bn.md', 'en',
     'Nothing is collected or sent anywhere;',
     "Nothing is collected, and nothing is sent anywhere but to the user's own watch, if they add Taqwa to it;"),
    ('3-4n-notes-file', APPROVED, 'docs/store/app-review/2026-10-30-notes-1.6.0-43.txt', 'en',
     'works offline, has no ads, and collects no data.',
     "works offline, has no ads, and collects no data; it sends only to the user's own Apple Watch."),
    ('3-4n-notes-file', APPROVED, 'docs/store/app-review/2026-10-30-notes-1.6.0-43.txt', 'en',
     None,
     "If the user adds Taqwa to their Apple Watch, the iPhone also sends it, through WatchConnectivity, the month's prayer times, the city, the Qibla's direction and distance, the compass correction and the user's own tasbeeh phrases, with a random number made once on the iPhone that tells the watch which iPhone sent them; Apple may carry them over Wi-Fi or cellular, end-to-end encrypted."),
    ('3-4n-notes-file', APPROVED, 'docs/store/app-review/2026-10-30-notes-1.6.0-43.txt', 'en',
     'from first launch through every main feature.',
     'from first launch through its main features. It predates the adhkar and the Apple Watch app.'),
    # Ruling 69 item 1 (31 October 2026): section 4 still said the only network use is recitation,
    # beside the watch's sentence (the row above). It takes the approved store bullet's words (4l,
    # "The internet is used for recitation"); the watch clause is the notes' own approved ones.
    ('3-4n-notes-file', APPROVED, 'docs/store/app-review/2026-10-30-notes-1.6.0-43.txt', 'en',
     'The only network use is recitation',
     "The internet is used for recitation: playing a surah not yet on the phone downloads its MP3 audio from a public GitHub release (github.com/MohamedAbulgasem/Taqwa-data) mirroring Islamic Network's recitations unmodified; the reciter list is refreshed there at most once a day."),
    ('3-4n-notes-pointer', APPROVED, 'docs/store/listing/en.md', 'en',
     'brought up to date for the engine build on 29 September: `docs/store/app-review/2026-09-29-notes-1.0.0.txt` (the 23 September reply stays as the record of what was sent then)',
     'brought up to date for the watch release: `docs/store/app-review/2026-10-30-notes-1.6.0-43.txt` (the 23 September reply and the earlier notes files stay as the record of what was sent then)'),
]]
# ---------------------------------------------------------------------------------------------

# ---------------------------------------------------------------------------------------------
# The sweep: plan 2's Task 3 Step 7 grep, file filter and pattern exactly as the plan writes them.
# ---------------------------------------------------------------------------------------------
SWEEP_SKIP_DIRS = re.compile(r"^(docs/superpowers|\.superpowers|docs/research|tools)/")
SWEEP_TYPES = re.compile(r"\.(md|html|json|xml|strings|txt|plist)$")
SWEEP_TRANSLATED = re.compile(
    r"/values-[a-z]|\.(ar|bn|fr|id|tr|ur)\.md$|/(ar|bn|fr|id|tr|ur)/|(ar|bn|fr|id|tr|ur)\.lproj/|composeResources/files|vectors/")
SWEEP = re.compile(
    r"leaves? (the|your) phone|never leaves|sent anywhere|nowhere|on your phone|stays on|collect|transmit"
    r"|uninstall|delet(e|es|ing) the app|removes all|network|internet|offline|third-party|sdk", re.I)

# Python's case folding also matches Turkish's dotted capital İ, so the sweep reads one line more
# than BSD grep does (docs/store/listing/tr.md's "İnternet" bullet, row 4l's own line).
#
# Every other line the sweep prints, as the report sorts it (its sections 5, 6 and 8): (why it
# stays, path, a pattern of the line). A row's own words need no entry; the words beside them on
# its line need one that matches all of them (classify's re.fullmatch, Ruling 69 item 1).
KNOWN = [
    # Item 4, the lines that stay (the report's section 5): still true with the watch.
    ("item 4: the crash report, which never goes to the watch", r"PRIVACY\.md",
     r"saves a technical report on your phone|It never leaves the phone unless you choose to email it"),
    ("item 4: the crash report", r"site/pages/en/support\.html", r"keeps a short technical report on your phone"),
    ("item 4: the crash report", r"shared/src/commonMain/composeResources/values/strings\.xml", r'name="crash_sheet_body"'),
    ("item 4: read-aloud, whose text never goes to the watch", r"PRIVACY\.md", r"sends the text nowhere itself"),
    ("item 4: read-aloud's comment", r"androidApp/src/main/AndroidManifest\.xml", r"No permission; nothing is sent anywhere"),
    ("item 4: Taqwa itself receives nothing", r"PRIVACY\.md", r"^Taqwa collects no data from anyone, of any age\.$"),
    ("item 4: Taqwa itself receives nothing", r"README\.md", r"no premium tier, no data collection, no account"),
    ("item 4: the Changes promise, which binds this release", r"PRIVACY\.md",
     r"^If a future version needs the network for anything new"),
    ("item 4: works offline (the watch link is optional and the platform's)", r"README\.md",
     r"No ads, no account, offline by design\.|alt=\"Offline by design\"|^- \*\*Offline first\.\*\*"),
    ("item 4: works offline", r"site/pages/en/home\.html", r"No ads, no account, works offline\."),
    ("item 4: works offline", r"site/pages/en/meta\.json",
     r'"(description|pitch_body|app_body|app_point_qibla)": "[^"]*\boffline\b'),
    ("item 4: works offline", r"shared/src/commonMain/composeResources/values/strings\.xml", r'name="onboarding_welcome_body"'),
    ("item 4: works offline", r"docs/store/listing/en\.md", r"and Qibla\. Works offline\.$|It works offline, costs nothing"),
    ("item 4: works offline", r"docs/store/listing/id\.md", r"dan kiblat\. Tanpa internet\.$|Berfungsi offline, tidak berbayar"),
    ("item 4: plan 5's re-answer of the stores' forms (the whole document)", r"docs/STORE-PRIVACY\.md", r""),
    ("item 4: the forms' answers, unchanged by the re-answer, and the release runbook's other lines",
     r"docs/STORE-CHECKLIST\.md", r""),
    # Item 5, a code comment that names what the watch may never declare.
    ("item 5: the Wear manifest's comment (plan 2 Task 6)", r"wearApp/src/main/AndroidManifest\.xml",
     r"must never be here are the network's and the location"),
    # The lines about something else (the report's section 8), a kind at a time.
    ("records: the build log, not rewritten", r"docs/BUILD-LOG\.md", r""),
    ("records: what was sent to App Review then", r"docs/store/app-review/2026-(09-23-guideline-2\.1-reply|09-29-notes-1\.0\.0|10-02-notes-1\.0\.0-35|11-07-notes-1\.5\.1-42)\.txt", r""),
    ("records: shipped release notes", r"docs/store/release-notes/[^/]+\.txt", r"\boffline\b"),
    ("records: a smoke test's step", r"docs/store/smoke-tests/[^/]+\.md",
     r"No surah stays on its Verifying chip|^- \*\*S23:\*\* uninstall the Play version first: this APK is signed with the upload key, not Play's"
     r"|^- \*\*S23:\*\* before uninstalling, screenshot Mushaf pages 1, 351 and 411 in 1\.5\.0 for section 10\. Then uninstall the$"),
    ("credits", r"docs/licences/.+", r""),
    ("credits", r"docs/ATTRIBUTION\.md", r""),
    ("credits", r"CONTRIBUTING\.md", r"^Third-party material \(text, audio, fonts, data\)"),
    ("credits", r"README\.md", r"^Prayer times from Taqwa's own engine, the Qibla bearing|^Taqwa is free software under the"),
    ("credits: the listing's sources line, which names the Adhan library", r"docs/store/listing/[a-z]{2}\.md", r"\bAdhan\b"),
    ("credits", r"site/pages/en/home\.html", r"<b>Recitations</b><span>The Islamic Network"),
    ("credits", r"shared/src/commonMain/composeResources/values/strings\.xml",
     r'name="(recitation_settings_credit|credit_recitations_detail)"'),
    ("credits", r"docs/store/app-review/2026-10-30-notes-1\.6\.0-43\.txt", r"^6\. Third-party material$|^- Recitations: Islamic Network"),
    ("features that work offline or fetch on request", r"README\.md",
     r"^- \*\*(Prayer times|The Quran|Recitation|Read-aloud\.)\*\* |^\| 2 \| Quran reader"),
    ("features", r"site/pages/en/home\.html", r"^\s*<p>(The Uthmani text in the Madinah|Ten reciters\. Download a surah)"),
    ("features", r"docs/store/listing/(en|id)\.md",
     r"played offline, with lock-screen|diputar offline, dengan kendali|^• The whole Quran offline|tersedia offline, dengan terjemahan"
     r"|^5\. (Listen to ten reciters|Dengarkan sepuluh qari), offline$|^\(Proposal of 29 September"),
    ("features", r"shared/src/commonMain/composeResources/values/strings\.xml",
     r'name="(city_search_hint|recitation_fail_no_network|recitation_read_aloud_detail)"'),
    ("build tools and configuration", r"README\.md", r"^- Android Studio with the Android SDK"),
    ("build tools and configuration", r"docs/MONITOR\.md", r""),
    ("build tools and configuration", r"androidApp/src/debug/.+", r""),
    ("build tools and configuration", r"androidApp/src/main/AndroidManifest\.xml",
     r"first network access: recitation downloads|android\.permission\.INTERNET|android\.hardware\.location\.network"),
    ("build tools and configuration", r"watchface/src/main/res/raw/watchface\.xml", r"uninstall: a font whose file changes"),
    # Plan 5 Task 5 (erratum p5): Watch Face Push's library asks for the system library wear-sdk and
    # API 33; the manifest lets watches without them install the app. Neither is a network or data line.
    ("build tools and configuration: Watch Face Push's library on watches before Wear OS 6",
     r"wearApp/src/main/AndroidManifest\.xml",
     r'<uses-sdk tools:overrideLibrary="androidx\.wear\.watchfacepush" />|android:name="wear-sdk"|requires the system library wear-sdk'),
    ("build tools and configuration: campaign names", r"site/campaigns\.json", r"-offline\""),
    # Plan 5 Task 7: the phone's permission list, which scripts/release.sh holds the store build to.
    # Its lines name what the manifest already asks for, each with its reason; they promise nothing.
    ("build tools and configuration: the phone's permission list (plan 5 Task 7)", r"androidApp/permissions\.txt",
     r"^android\.permission\.(INTERNET|ACCESS_NETWORK_STATE) (manifest|library:\S+) # "),
    ("scope: what Taqwa will not build", r"CONTRIBUTING\.md", r"anything that needs a server"),
    ("scope: what Taqwa will not build", r"README\.md", r"^Deliberately out of scope"),
    ("the watch's own words: a message, not a promise",
     r"wearApp/src/main/res/values/strings\.xml|iosApp/TaqwaWatch(Widgets)?/en\.lproj/Localizable\.strings",
     r"open_taqwa_on_phone"),
    # Plan 5 Task 10: the support page's watch answer quotes the watch's own message, and the Wear
    # OS track's notes say the watch app needs the phone's, which sends it the times (the plan's
    # own English).
    ("the watch's own words, quoted: a message, not a promise", r"site/pages/en/support\.html",
     r"If the watch says “Open Taqwa on your phone”, it has no times yet"),
    ("the watch's own words: the phone app the watch needs, not a promise",
     r"docs/store/release-notes/[^/]+-wear\.txt", r"^Needs Taqwa on your phone, which sends the prayer times\.$|\. Needs Taqwa on your phone, which sends the prayer times\.$"),
    # Ruling 69 item 2 (plan 5 Task 11): the App Review notes say what the Apple Watch shows until
    # the iPhone app has run once with it nearby, quoting the watch's own message.
    ("the watch's own words, quoted: a message, not a promise",
     r"docs/store/app-review/2026-10-30-notes-1\.6\.0-43\.txt",
     r"^- Apple Watch: its app comes with the iPhone app; complications and Today show the iPhone's times once it has run with the watch nearby \(until then, \"Open Taqwa on your phone\"\)\.$"),
    # Ruling 69 item 1: only a row's own words are the row's, so the words beside a row's new text
    # on its line are swept too. These are the ones the report keeps there, each held whole, from
    # the row's edge to the line's (^...$, and classify matches them with re.fullmatch there), so
    # that anything added beside them is seen.
    ("item 3 (4m): the rest of the promise's line, unchanged and still true with the watch",
     r"CONTRIBUTING\.md", r"^ No analytics, no crash-reporting SDKs, no account\. There$"),
    ("item 3 (4m): the out-of-scope list's label; the services after it are the row's",
     r"SECURITY\.md", r"^- \*\*Third-party services:\*\* $"),
    ("item 4: works offline (the watch link needs no internet), in the review notes' sentences that 4n "
     "kept around its change (\"The rest stands\"), as M3 approved them", r"docs/store/listing/(en|fr|tr|id|ur|bn)\.md",
     r"^No account or sign-in\. Location is optional: Settings › Location lets a reviewer choose any city"
     r" instead\. Everything works offline except recitation downloads, which fetch audio from a public"
     r" GitHub release on request\. To see the lock-screen player, play a surah and lock the phone\."
     r" Widgets are added from the home screen's widget gallery\. $"),
    ("item 3 (4n, the pointer): the runbook's instruction to keep the notes current, not a promise",
     r"docs/store/listing/en\.md",
     r"^\. Apple asked for them to stay there for future submissions, so update the file named above and the"
     r" field together when a feature, a network use or a licence changes\. The field holds 4,000 bytes\.$"),
]
# ---------------------------------------------------------------------------------------------


def normalize(text):
    return " ".join(text.split())


def read(root, path):
    with open(os.path.join(root, path), encoding="utf-8") as f:
        return f.read()


def short(text, width=90):
    text = normalize(text)
    return text if len(text) <= width else text[: width - 1] + "…"


def check_rows(root, rows, allow_awaiting):
    """Each row's old text gone and new text there; each awaiting row a problem, or a warning."""
    problems, warnings = [], []
    for r in rows:
        where = f"{r.id} [{r.lang}] {r.file}"
        try:
            text = normalize(read(root, r.file))
        except FileNotFoundError:
            problems.append(f"{where}: the file is missing")
            continue
        new = normalize(r.new)
        if new not in text:
            problems.append(f"{where}: the new text is missing: «{short(r.new)}»")
        # An old text may be the start of its own new text (4k), so it counts only outside it.
        if r.old is not None and normalize(r.old) in text.replace(new, "\0"):
            problems.append(f"{where}: the old text is still there: «{short(r.old)}»")
        if r.status == AWAITING:
            message = f"{where}: awaiting Mohamed's approval (M3): «{short(r.new, 60)}»"
            (warnings if allow_awaiting else problems).append(message)
        elif r.status != APPROVED:
            problems.append(f"{where}: unknown status {r.status!r}")
    return problems, warnings


MONTHS = {
    "en": "January February March April May June July August September October November December",
    "ar": "يناير فبراير مارس أبريل مايو يونيو يوليو أغسطس سبتمبر أكتوبر نوفمبر ديسمبر",
    "fr": "janvier février mars avril mai juin juillet août septembre octobre novembre décembre",
    "tr": "Ocak Şubat Mart Nisan Mayıs Haziran Temmuz Ağustos Eylül Ekim Kasım Aralık",
    "id": "Januari Februari Maret April Mei Juni Juli Agustus September Oktober November Desember",
    "ur": "جنوری فروری مارچ اپریل مئی جون جولائی اگست ستمبر اکتوبر نومبر دسمبر",
    "bn": "জানুয়ারি ফেব্রুয়ারি মার্চ এপ্রিল মে জুন জুলাই আগস্ট সেপ্টেম্বর অক্টোবর নভেম্বর ডিসেম্বর",
}


def number(token):
    if not token or not all(unicodedata.decimal(c, None) is not None for c in token):
        return None
    return int("".join(str(unicodedata.decimal(c)) for c in token))


def policy_date(line, lang):
    """The date of a policy's italic "Last updated" line, written as its language writes it: the
    day, the month's name and the year, in Western or the language's own digits."""
    tokens = [t.strip("_.,:،۔।") for t in line.split()]
    months = MONTHS[lang].split()
    for i, token in enumerate(tokens):
        if token in months and 0 < i < len(tokens) - 1:
            day, year = number(tokens[i - 1]), number(tokens[i + 1])
            if day and year:
                return datetime.date(year, months.index(token) + 1, day)
    return None


def check_dates(root, paths):
    problems = []
    for path in paths:
        lang = "en" if path == "PRIVACY.md" else path.split(".")[1]
        try:
            lines = read(root, path).split("\n")
        except FileNotFoundError:
            problems.append(f"{path}: the policy is missing")
            continue
        meta = next((l for l in lines if l.startswith("_") and l.rstrip().endswith("_")), "")
        date = policy_date(meta, lang)
        if date is None:
            problems.append(f"{path}: no \"Last updated\" date it can read: «{short(meta)}»")
        elif date < WATCH_BRANCH_DATE:
            problems.append(f"{path}: last updated {date}, before the watch branch ({WATCH_BRANCH_DATE})")
    return problems


def is_work_tree_top(root):
    """Whether root is the top of a git work tree of its own (a checkout or a worktree), not a
    plain copy, and not a copy unpacked somewhere inside another repository."""
    try:
        done = subprocess.run(["git", "-C", root, "rev-parse", "--show-toplevel"], capture_output=True)
    except OSError:  # no git at all
        return False
    top = done.stdout.decode("utf-8").strip()
    return done.returncode == 0 and bool(top) and os.path.realpath(top) == os.path.realpath(root)


class UnsupportedIgnore(Exception):
    """A .gitignore line the tree walk cannot read as git does: the gate stops on it, by its file
    and line, rather than sweep a different set of files from the one git would list."""


def ignore_patterns(root, path):
    """The patterns of the .gitignore at path, in the subset git and the walk read alike: a name
    or glob, matched at any depth; a pattern with a slash (leading or inside), anchored to the
    root; a trailing slash for a directory alone. Trailing spaces do not count, as in git. A
    negation (!), a ** and a backslash escape stop the walk (UnsupportedIgnore)."""
    patterns = []
    for number_, line in enumerate(read(root, path).split("\n"), 1):
        if not line.strip() or line.startswith("#"):
            continue
        why = ("a negation (!)" if line.startswith("!") else "a **" if "**" in line
               else "a backslash escape" if "\\" in line else None)
        line = line.rstrip()
        if why:
            raise UnsupportedIgnore(f"{path}:{number_}: «{line}» is a .gitignore pattern the tree walk "
                                    f"does not read ({why}); run the gate in a git checkout, or make "
                                    "the pattern one it reads")
        patterns.append(line)
    return patterns


def ignored_by(patterns, rel, is_dir):
    """Whether patterns (ignore_patterns') ignore rel, as git does: a pattern with no slash but a
    trailing one matches the last name at any depth; one with a slash matches the path from the
    root, name by name, so that its *, ? and [...] never cross a slash."""
    names = rel.split("/")
    for pattern in patterns:
        if pattern.endswith("/") and not is_dir:
            continue
        pattern = pattern.rstrip("/")
        if "/" in pattern:
            parts = pattern.lstrip("/").split("/")
            if len(parts) == len(names) and all(fnmatch.fnmatchcase(n, p) for n, p in zip(names, parts)):
                return True
        elif fnmatch.fnmatchcase(names[-1], pattern):
            return True
    return False


def tree_files(root, prune=None):
    """The files of a tree that is not a git work tree (a git-archive copy or a source zip): every
    file under it, without .git and without what its root .gitignore ignores (the build
    directories, _site/, .superpowers/ and the rest), so that a built copy sweeps as a checkout.

    A directory whose path (with a trailing slash) prune matches is not walked: the sweep passes
    the directories it never reads. A .gitignore anywhere else below the root, or a root pattern
    the walk does not read as git does (ignore_patterns), raises UnsupportedIgnore."""
    try:
        patterns = ignore_patterns(root, ".gitignore")
    except FileNotFoundError:
        patterns = []
    files = []
    for directory, subdirectories, names in os.walk(root):
        base = os.path.relpath(directory, root).replace(os.sep, "/")
        base = "" if base == "." else base + "/"
        if base and ".gitignore" in names:
            raise UnsupportedIgnore(f"{base}.gitignore: a .gitignore below the root, which the tree walk "
                                    "does not read; run the gate in a git checkout, or move its patterns "
                                    "to the root .gitignore")
        subdirectories[:] = sorted(d for d in subdirectories
                                   if d != ".git" and not ignored_by(patterns, base + d, True)
                                   and not (prune and prune.search(base + d + "/")))
        files += [base + n for n in names if n != ".git" and not ignored_by(patterns, base + n, False)]
    return files


def listed_files(root, prune=None):
    """The files the sweep reads: git's own list in a checkout, else the tree walked (without the
    directories prune matches)."""
    if not is_work_tree_top(root):
        return tree_files(root, prune)
    listed = subprocess.run(["git", "-C", root, "ls-files", "-z"], check=True,
                            capture_output=True).stdout.decode("utf-8").split("\0")
    return [p for p in listed if p]


def sweep(root, pages):
    """Plan 2's sweep: (path, line number, line) for every line it prints, in the files it reads
    (with pages, only the public pages among them). In a checkout those are git's tracked files;
    in a copy that is no git work tree, every file but the ignored ones (listed_files)."""
    hits = []
    for path in sorted(listed_files(root, SWEEP_SKIP_DIRS)):
        if SWEEP_SKIP_DIRS.search(path) or not SWEEP_TYPES.search(path) or SWEEP_TRANSLATED.search(path):
            continue
        if pages and not PUBLIC.fullmatch(path):
            continue
        try:
            text = read(root, path)
        except (FileNotFoundError, UnicodeDecodeError):
            continue
        for number_, line in enumerate(text.split("\n"), 1):
            if SWEEP.search(line):
                hits.append((path, number_, line))
    return hits


def new_text_spans(text, rows):
    spans = []
    for r in rows:
        words = r.new.split()
        pattern = r"\s+".join(re.escape(w) for w in words)
        spans += [m.span() for m in re.finditer(pattern, text)]
    return spans


def outside(line, start, spans):
    """The parts of a line (which starts at offset start of its file) outside the spans."""
    marked = list(line)
    for s, e in spans:
        for i in range(max(s, start), min(e, start + len(line))):
            marked[i - start] = "\0"
    return [part for part in "".join(marked).split("\0") if part.strip()]


def classify(root, hits, rows, known=None):
    """Each hit is a row's new text or a line the report keeps (KNOWN); anything else is a
    problem. Only a row's own words are the row's (Ruling 69 item 1): the rest of a line that
    holds a row's new text is swept as any other line is, and a KNOWN entry excuses those words
    only if it matches all of them (re.fullmatch), so a promise left beside it is seen, even next
    to words KNOWN keeps. A line without a row's text is matched anywhere (re.search), as before."""
    known = KNOWN if known is None else known
    problems = []
    by_file = collections.defaultdict(list)
    for r in rows:
        by_file[r.file].append(r)
    starts_cache = {}
    for path, lineno, line in hits:
        if path not in starts_cache:
            text = read(root, path)
            offsets, at = [], 0
            for l in text.split("\n"):
                offsets.append(at)
                at += len(l) + 1
            starts_cache[path] = (offsets, new_text_spans(text, by_file.get(path, [])))
        offsets, spans = starts_cache[path]
        start = offsets[lineno - 1]
        beside = [s for s in spans if s[0] < start + len(line) and start < s[1]]
        kept = re.fullmatch if beside else re.search
        for part in outside(line, start, beside):
            found = SWEEP.search(part)
            if not found:
                continue
            if any(re.fullmatch(p, path) and kept(l, part) for _, p, l in known):
                continue
            # Shown from a little before the sweep's first word, so a long line shows what it caught.
            cut = max(0, found.start() - 40)
            shown = ("…" if cut else "") + short(part[cut:], 110)
            where = " (beside a row's new text)" if beside else ""
            problems.append(f"sweep: {path}:{lineno}: «{shown}»{where} is in no row of the report "
                            "and in none of its lists (items 4 and 5, and the lines about something "
                            "else)")
    return problems


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--pages", action="store_true", help="only the public pages' rows and files")
    parser.add_argument("--root", default=ROOT, help="the tree to read (default: this repository)")
    parser.add_argument("--allow-awaiting", action="store_true",
                        help="report rows awaiting Mohamed's approval as warnings, not failures")
    args = parser.parse_args(argv)
    root = os.path.abspath(args.root)
    rows = [r for r in ROWS if PUBLIC.fullmatch(r.file)] if args.pages else ROWS
    problems, warnings = check_rows(root, rows, args.allow_awaiting)
    problems += check_dates(root, POLICIES)
    try:
        hits = sweep(root, args.pages)
    except UnsupportedIgnore as e:
        hits = []
        problems.append(str(e))
    problems += classify(root, hits, ROWS)
    for w in warnings:
        print(f"warning: {w}")
    for p in problems:
        print(f"FAIL: {p}")
    counts = collections.Counter(r.status for r in rows)
    scope = "the public pages' " if args.pages else ""
    summary = (f"privacy-check: {scope}{len(rows)} rows ({counts[APPROVED]} approved, "
               f"{counts[AWAITING]} awaiting), {len(POLICIES)} policies, {len(hits)} sweep lines")
    print(f"{summary}: {len(problems)} problems" if problems else f"{summary}: clean"
          + (f" but for the {len(warnings)} rows awaiting M3" if warnings else ""))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
