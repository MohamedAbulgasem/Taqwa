#!/usr/bin/env python3
"""The "Join the Android beta" section of the site's home pages, while Play's closed test runs.

    tools/site-beta.py add      writes the section into all seven pages
    tools/site-beta.py remove   takes it out again, at launch

Everything it writes sits between <!-- beta:start --> and <!-- beta:end --> markers, so `remove`
leaves the pages exactly as they were. Run site/build.py afterwards: the hero's "Join the Android
beta" button is the build's, shown exactly while a page carries this section (see store_block).
"""
import re, sys, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
GROUP = "https://groups.google.com/g/taqwa-testers"
OPT_IN = "https://play.google.com/apps/testing/world.taqwa.app"
STORE = "https://play.google.com/store/apps/details?id=world.taqwa.app"
MAIL = '<a href="mailto:support@taqwa.world?subject=Taqwa%20beta">support@taqwa.world</a>'

COPY = {
    "en": dict(link="Join the Android beta", label="Android beta", h2="Help test Taqwa before it launches.",
        lede="Google asks every new app for twelve testers over two weeks before it can be published. If you have an Android phone, joining takes a minute, and it is the biggest help Taqwa can get right now.",
        s1=("Join the testers group", "Use the Google account that is on your phone. The group exists only so Google Play knows who the testers are."),
        s2=("Become a tester", "One tap on Google Play’s testing page."),
        s3=("Install from Google Play", "Then keep it installed for two weeks. Feedback is welcome at {mail}.")),
    "ar": dict(link="انضم إلى النسخة التجريبية لأندرويد", label="النسخة التجريبية لأندرويد", h2="ساعد في اختبار «تقوى» قبل إطلاقه.",
        lede="تشترط Google على كل تطبيق جديد اثني عشر مختبِرًا لمدة أسبوعين قبل نشره. إن كان هاتفك أندرويد فالانضمام يستغرق دقيقة، وهو أكبر عون يمكن أن يناله «تقوى» الآن.",
        s1=("انضم إلى مجموعة المختبِرين", "استخدم حساب Google الموجود على هاتفك. المجموعة موجودة فقط ليعرف Google Play من هم المختبِرون."),
        s2=("كن مختبِرًا", "ضغطة واحدة في صفحة الاختبار على Google Play."),
        s3=("ثبّت التطبيق من Google Play", "ثم أبقِه مثبتًا أسبوعين. ونرحّب بملاحظاتك على {mail}.")),
    "fr": dict(link="Rejoindre la bêta Android", label="Bêta Android", h2="Aidez à tester Taqwa avant son lancement.",
        lede="Google demande à toute nouvelle application douze testeurs pendant deux semaines avant sa publication. Si vous avez un téléphone Android, cela prend une minute, et c’est la plus grande aide que Taqwa puisse recevoir en ce moment.",
        s1=("Rejoignez le groupe des testeurs", "Utilisez le compte Google de votre téléphone. Le groupe sert uniquement à indiquer à Google Play qui sont les testeurs."),
        s2=("Devenez testeur", "Un seul appui sur la page de test de Google Play."),
        s3=("Installez depuis Google Play", "Puis gardez l’application installée deux semaines. Vos retours sont les bienvenus à {mail}.")),
    "tr": dict(link="Android beta sürümüne katılın", label="Android beta", h2="Taqwa yayınlanmadan önce test etmeye yardım edin.",
        lede="Google, her yeni uygulamadan yayınlanmadan önce iki hafta boyunca on iki test kullanıcısı istiyor. Android telefonunuz varsa katılmak bir dakika sürer ve şu anda Taqwa’ya yapılabilecek en büyük yardım budur.",
        s1=("Test grubuna katılın", "Telefonunuzdaki Google hesabını kullanın. Grup yalnızca Google Play’in test kullanıcılarını tanıması içindir."),
        s2=("Test kullanıcısı olun", "Google Play’in test sayfasında tek dokunuş."),
        s3=("Google Play’den yükleyin", "Sonra iki hafta yüklü tutun. Görüşlerinizi {mail} adresine bekliyoruz.")),
    "id": dict(link="Ikut beta Android", label="Beta Android", h2="Bantu menguji Taqwa sebelum dirilis.",
        lede="Google meminta setiap aplikasi baru memiliki dua belas penguji selama dua minggu sebelum boleh diterbitkan. Kalau Anda memakai ponsel Android, bergabung hanya butuh semenit, dan itulah bantuan terbesar bagi Taqwa saat ini.",
        s1=("Gabung ke grup penguji", "Gunakan akun Google yang ada di ponsel Anda. Grup ini hanya agar Google Play tahu siapa saja pengujinya."),
        s2=("Jadi penguji", "Satu ketukan di halaman pengujian Google Play."),
        s3=("Pasang dari Google Play", "Lalu biarkan terpasang selama dua minggu. Masukan Anda kami tunggu di {mail}.")),
    "ur": dict(link="اینڈرائیڈ بیٹا میں شامل ہوں", label="اینڈرائیڈ بیٹا", h2="اجرا سے پہلے تقویٰ کی آزمائش میں مدد کریں۔",
        lede="Google ہر نئی ایپ کی اشاعت سے پہلے دو ہفتے تک بارہ آزمائش کنندگان کا تقاضا کرتا ہے۔ اگر آپ کے پاس اینڈرائیڈ فون ہے تو شامل ہونے میں ایک منٹ لگتا ہے، اور اس وقت تقویٰ کے لیے اس سے بڑی مدد کوئی نہیں۔",
        s1=("آزمائش کنندگان کے گروپ میں شامل ہوں", "وہی Google اکاؤنٹ استعمال کریں جو آپ کے فون پر ہے۔ یہ گروپ صرف اس لیے ہے کہ Google Play کو معلوم ہو کہ آزمائش کنندگان کون ہیں۔"),
        s2=("آزمائش کنندہ بنیں", "Google Play کے آزمائشی صفحے پر ایک ٹیپ۔"),
        s3=("Google Play سے انسٹال کریں", "پھر دو ہفتے انسٹال رہنے دیں۔ اپنی رائے {mail} پر بھیجیں۔")),
    "bn": dict(link="অ্যান্ড্রয়েড বিটায় যোগ দিন", label="অ্যান্ড্রয়েড বিটা", h2="প্রকাশের আগে তাকওয়া পরীক্ষা করতে সাহায্য করুন।",
        lede="প্রকাশের আগে প্রতিটি নতুন অ্যাপের জন্য Google দুই সপ্তাহ ধরে বারোজন পরীক্ষক চায়। আপনার অ্যান্ড্রয়েড ফোন থাকলে যোগ দিতে এক মিনিট লাগে, আর এই মুহূর্তে তাকওয়ার জন্য এটাই সবচেয়ে বড় সাহায্য।",
        s1=("পরীক্ষকদের গ্রুপে যোগ দিন", "আপনার ফোনে যে Google অ্যাকাউন্ট আছে সেটিই ব্যবহার করুন। গ্রুপটি শুধু এজন্য, যাতে Google Play জানে পরীক্ষক কারা।"),
        s2=("পরীক্ষক হোন", "Google Play-এর টেস্টিং পাতায় একটি ট্যাপ।"),
        s3=("Google Play থেকে ইনস্টল করুন", "তারপর দুই সপ্তাহ ইনস্টল করে রাখুন। মতামত পাঠান {mail} ঠিকানায়।")),
}

BLOCK = re.compile(r"[ \t]*<!-- beta:start -->.*?<!-- beta:end -->\n{0,2}", re.S)


def section(c, digits):
    rows = "\n".join(
        f'        <div class="row"><b>{digits[i]}. <a href="{url}">{c[key][0]}</a></b><span>{c[key][1].format(mail=MAIL)}</span></div>'
        for i, (key, url) in enumerate((("s1", GROUP), ("s2", OPT_IN), ("s3", STORE)))
    )
    return f'''  <!-- beta:start -->
  <section class="section" id="beta">
    <div class="wrap split">
      <div>
        <p class="label">{c["label"]}</p>
        <h2>{c["h2"]}</h2>
        <p class="lede" style="margin-bottom: 0; max-width: 40ch;">{c["lede"]}</p>
      </div>
      <div class="rows">
{rows}
      </div>
    </div>
  </section>
  <!-- beta:end -->

'''


def main(action):
    for lang, c in COPY.items():
        path = ROOT / "site" / "pages" / lang / "home.html"
        html = BLOCK.sub("", path.read_text(encoding="utf-8"))
        if action == "add":
            digits = {"ar": "١٢٣", "bn": "১২৩"}.get(lang, "123")
            marker = '  <section class="section" id="features">'
            assert html.count(marker) == 1, f"{lang}: no features section"
            html = html.replace(marker, section(c, digits) + marker)
        path.write_text(html, encoding="utf-8")
        print(f"{lang}: beta section {'added' if action == 'add' else 'removed'}")


if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in ("add", "remove"):
        sys.exit(__doc__)
    main(sys.argv[1])
