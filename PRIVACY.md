# Taqwa privacy policy

_Last updated 9 November 2026. Applies to Taqwa 1.0.0 and later on Android and iOS._

Taqwa is a free Islamic app with no account, no ads and no analytics. It works offline. It uses
the internet to download Quran recitations, and only when you ask it to. If you add Taqwa to your
watch, its data may travel over the internet too, encrypted, as below.

## What stays on your devices

Everything the app knows is stored on your own devices:

- your location, or the city you chose, used to compute prayer times and the Qibla;
- your settings: prayer timetable, notification choices, adhan voice, theme, reciter;
- your Quran bookmarks and reading position;
- your Tasbeeh counts;
- any recitations you have downloaded.

None of this is sent to us or to anyone else. There is no server behind Taqwa and no account to
create. Deleting Taqwa from your phone and your watch deletes all of it.

If Taqwa crashes, it saves a technical report on your phone (the app version, your phone model
and the error, nothing else). It never leaves the phone unless you choose to email it to us.
On Android, the email from Settings › About Taqwa › Report a problem also says whether Taqwa's
notifications and exact alarms are allowed, how far the phone limits its battery use, whether the
phone is on silent, vibrate or Do Not Disturb, and roughly when the last prayer alert came and when
the app was installed or last updated; you see it before you send it.

## Your watch

If you add Taqwa to your watch, your phone gives it your city, the Qibla's direction and
distance, your area's compass correction, the coming month's prayer times as the app writes them,
and your own tasbeeh phrases, with a random number made once on your phone that tells your watch
which phone sent them. When your phone and watch are not directly connected, Google (Android) or
Apple (iPhone) may carry this data over the internet, end-to-end encrypted so only your phone and
watch can read it.

## Location

The app asks for your location only to compute prayer times and the Qibla direction, and only if
you allow it. Picking a city from the built-in list works just as well and needs no permission.
Your location is used on the device. Only your own devices can see your location.

## Recitation downloads

Recitations are fetched one surah at a time from Taqwa's public data repository on GitHub
(github.com/MohamedAbulgasem/Taqwa-data), and only when you tap Download, tap Play on a
surah you have not downloaded, or choose "Download the whole Quran". By default this
happens over Wi-Fi only; you can allow mobile data in Settings › Quran › Recitation.

Like any download, the request shows GitHub your IP address and which file you asked for,
which reveals the reciter and surah. It carries nothing that identifies you: no account, no
device identifier, no cookie. GitHub's own privacy statement covers what GitHub keeps about
such requests: docs.github.com/site-policy/privacy-policies/github-general-privacy-statement.

Once you have opened anything to do with recitation, whether the reciter list, the Recitation
settings or a download, the app also checks once a day for an updated list of reciters, from the
same repository. That request is the same for everyone and reveals nothing but your IP address.
Until you first use recitation, the app itself makes no network request at all.

Downloaded recitations can be deleted per reciter in Settings › Quran › Recitation ›
Downloads.

## Read-aloud

If you choose to have the translation or tafsir read aloud after each ayah, your phone's own
text-to-speech voice reads it: one of Apple's voices on iPhone; on Android, a voice from the
phone's speech engine, a separate app (usually Google's or Samsung's) with its own settings and
privacy terms. Taqwa picks only voices that work offline and sends the text nowhere itself. If
an Android phone lacks the voice, "Get the voice" opens the speech engine's own download for it;
on iPhone, voices are added in the Settings app.

## Backups

Taqwa opts out of Android's app backup, so your location and settings are not copied to Google.
On iOS, your settings, bookmarks, counts and downloaded recitations are excluded from iCloud
backup; the only files of Taqwa's own that are backed up are the widgets' small caches, the
upcoming prayer times and the hundred ayahs the daily-ayah widget rotates through, neither of
which contains your location. If you add Taqwa to an Apple Watch, iOS keeps the last copy your
iPhone sent it, which names your city, and that copy may be in your iPhone's backup. An iCloud
backup is encrypted with keys Apple keeps, unless you turn on Advanced Data Protection, which
encrypts it end to end; a backup to a computer is encrypted only if you choose to encrypt it.

## What Taqwa never does

- No account, sign-in or profile.
- No ads and no advertising identifier.
- No analytics, crash reporting or usage statistics.
- No third-party SDK that talks to the internet. The Android app's one Google Play services
  library links your phone to your own watch, and Google may relay that link, encrypted, as
  above.
- No sale or sharing of data, because there is none to sell or share.

Your phone may send crash reports to Google or Apple under its own settings; those come from
the phone, not from Taqwa, and contain nothing of yours beyond the technical details of the
crash.

## Children

Taqwa collects no data from anyone, of any age.

## Changes

If a future version needs the network for anything new, this page will say so before that
version ships, with the date above updated.

## Contact

Email support@taqwa.world, or open an issue at github.com/MohamedAbulgasem/Taqwa/issues.
