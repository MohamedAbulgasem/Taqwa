"""Indonesia, Kemenag, as the open api.myquran.com republishes bimasislam.kemenag.go.id's
schedule (a republisher's copy of Kemenag's figures, not Kemenag's own page: audit finding F26): the
current month and the next for every fitted kab/kota (id-kemenag.tsv: the eighteen of
September 2026, the five of the round of 29 September among them checked at their own points so the
registry's unit for each is found whichever way the rounds merge, and those of both rounds of 12 November
2026, each named by its unit); from October, the next year once it appears, for the places that lack a
month of it, BACKFILL_PLACES places a run (probed on the first of them; a month the round of 12 November
2026 holds under archive/tables/round/kemenag-kabkota/ is held, audit finding F23); and each run one of the
months held beyond the next, read again for every place and compared with what is held (`reread_months`,
audit finding F28): a held day that changed is noted, in days, never a printed time, and the table then
carries what was read. Six columns as the
gate's rows; the fast begins at Subuh, so the Fajr column is also the end of eating (F+E), as id-kemenag.tsv reads it
since the audit of 3 October 2026. The API's imsak, a precaution the app shows too (Subuh - 10 on the days the gate holds), is
kept beside each table as its own capture since the second audit's G25 (ruling R284): `<key>-<yyyy>-imsak`, two
columns, the Subuh and the imsak as printed, the layout of the archive's hand read-backs
(archive/tables/imsak/id-kemenag/), checked as `- Im`; kept as printed, an imsak more than IMSAK_WIDE minutes before
its Subuh raised for a human, one after it '-' and raised (one at its Subuh is an end at the start, kept) (an error while the day is ahead, a note once past:
common.raise_imsak). The place name each response carries is
checked against the kota asked for, so a renumbered id never checks another kota's table (review M10); an
answer that does not hold every day of the month asked, and no day of another, is an error, and so is a
place no row was read for (audit finding F24)."""
import calendar
import json
import os
import re

from common import FetchError, Table, add_all, norm_time, raise_imsak, read_table_rows

SOURCE = "id-kemenag"
BASE = "https://api.myquran.com/v2/sholat/jadwal"
# Seconds before each request: up to 978 in a row at one host, which is a republisher's free API and not Taqwa's
# to lean on, so never faster than one every two seconds (review of 13 November 2026).
KEMENAG_GAP = 2.0
# Where the round of 12 November 2026 keeps each place's year tables (<key>-<yyyy>.txt, the monitor's own layout).
ROUND = ("archive", "tables", "round", "kemenag-kabkota")
# The next year is fetched for this many places a run (twelve requests each), so a year new to every
# place never takes the run's whole budget (246 places would be 2,952 requests, 98 minutes).
BACKFILL_PLACES = 20
# A month held beyond the next is read again once in this many weekly runs (`reread_months`): three
# years of months, so no two months held share a week.
REREAD_WEEKS = 36

# key, myQuran id, name (as myQuran's `lokasi` names it, case aside), zone, entry (its unit where the
# first proof fitted it, else the entry at the place's own point), lat, lon.
PLACES = [
    ("jakarta", "1301", "Kota Jakarta", "Asia/Jakarta", "id.kemenag/1301", None, None),
    ("bandaaceh", "0119", "Kota Banda Aceh", "Asia/Jakarta", "id.kemenag/0119", None, None),
    ("makassar", "2622", "Kota Makassar", "Asia/Makassar", "id.kemenag/2622", None, None),
    ("denpasar", "1709", "Kota Denpasar", "Asia/Makassar", "id.kemenag/1709", None, None),
    ("jayapura", "3329", "Kota Jayapura", "Asia/Jayapura", "id.kemenag/3329", None, None),
    ("malang", "1634", "Kota Malang", "Asia/Jakarta", "id.kemenag/1634", None, None),
    ("bandung", "1219", "Kota Bandung", "Asia/Jakarta", "id.kemenag/1219", None, None),
    ("jayawijaya", "3308", "Kab. Jayawijaya", "Asia/Jayapura", "id.kemenag/3308", None, None),
    ("batu", "1630", "Kota Batu", "Asia/Jakarta", "id.kemenag/1630", None, None),
    ("bukittinggi", "0313", "Kota Bukittinggi", "Asia/Jakarta", "id.kemenag/0313", None, None),
    ("bogor", "1222", "Kota Bogor", "Asia/Jakarta", "id.kemenag/1222", None, None),
    ("garut", "1208", "Kab. Garut", "Asia/Jakarta", "id.kemenag/1208", None, None),
    ("wonosobo", "1429", "Kab. Wonosobo", "Asia/Jakarta", "id.kemenag/1429", None, None),
    ("surabaya", "1638", "Kota Surabaya", "Asia/Jakarta", "id.kemenag", -7.2575, 112.7521),
    ("medan", "0228", "Kota Medan", "Asia/Jakarta", "id.kemenag", 3.5952, 98.6722),
    ("semarang", "1433", "Kota Semarang", "Asia/Jakarta", "id.kemenag", -6.9667, 110.4167),
    ("palembang", "0816", "Kota Palembang", "Asia/Jakarta", "id.kemenag", -2.9761, 104.7754),
    ("yogyakarta", "1505", "Kota Yogyakarta", "Asia/Jakarta", "id.kemenag", -7.8014, 110.3647),
    # The round of 12 November 2026 (Kemenag.kt, KemenagKabkota.kt): each named by its unit, checked at the unit's point, the
    # app's own point for its kota or regency seat.
    ("kotabekasi", "1221", "Kota Bekasi", "Asia/Jakarta", "id.kemenag/1221", None, None),
    ("depok", "1225", "Kota Depok", "Asia/Jakarta", "id.kemenag/1225", None, None),
    ("kotatangerang", "1107", "Kota Tangerang", "Asia/Jakarta", "id.kemenag/1107", None, None),
    ("tangsel", "1108", "Kota Tangerang Selatan", "Asia/Jakarta", "id.kemenag/1108", None, None),
    ("batam", "0506", "Kota Batam", "Asia/Jakarta", "id.kemenag/0506", None, None),
    ("pekanbaru", "0412", "Kota Pekanbaru", "Asia/Jakarta", "id.kemenag/0412", None, None),
    ("bandarlampung", "1014", "Kota Bandar Lampung", "Asia/Jakarta", "id.kemenag/1014", None, None),
    ("padang", "0314", "Kota Padang", "Asia/Jakarta", "id.kemenag/0314", None, None),
    ("samarinda", "2310", "Kota Samarinda", "Asia/Makassar", "id.kemenag/2310", None, None),
    ("tasikmalaya", "1227", "Kota Tasikmalaya", "Asia/Jakarta", "id.kemenag/1227", None, None),
    ("kotaserang", "1106", "Kota Serang", "Asia/Jakarta", "id.kemenag/1106", None, None),
    ("balikpapan", "2308", "Kota Balikpapan", "Asia/Makassar", "id.kemenag/2308", None, None),
    ("pontianak", "2013", "Kota Pontianak", "Asia/Pontianak", "id.kemenag/2013", None, None),
    ("banjarmasin", "2113", "Kota Banjarmasin", "Asia/Makassar", "id.kemenag/2113", None, None),
    ("jambi", "0610", "Kota Jambi", "Asia/Jakarta", "id.kemenag/0610", None, None),
    ("cimahi", "1223", "Kota Cimahi", "Asia/Jakarta", "id.kemenag/1223", None, None),
    ("surakarta", "1434", "Kota Surakarta", "Asia/Jakarta", "id.kemenag/1434", None, None),
    ("kupang", "1922", "Kota Kupang", "Asia/Makassar", "id.kemenag/1922", None, None),
    ("cilegon", "1105", "Kota Cilegon", "Asia/Jakarta", "id.kemenag/1105", None, None),
    ("manado", "2914", "Kota Manado", "Asia/Makassar", "id.kemenag/2914", None, None),
    ("mataram", "1810", "Kota Mataram", "Asia/Makassar", "id.kemenag/1810", None, None),
    ("bengkulu", "0710", "Kota Bengkulu", "Asia/Jakarta", "id.kemenag/0710", None, None),
    ("palu", "2813", "Kota Palu", "Asia/Makassar", "id.kemenag/2813", None, None),
    ("kotasukabumi", "1226", "Kota Sukabumi", "Asia/Jakarta", "id.kemenag/1226", None, None),
    ("kendari", "2717", "Kota Kendari", "Asia/Makassar", "id.kemenag/2717", None, None),
    ("dumai", "0411", "Kota Dumai", "Asia/Jakarta", "id.kemenag/0411", None, None),
    ("ambon", "3110", "Kota Ambon", "Asia/Jayapura", "id.kemenag/3110", None, None),
    ("kotacirebon", "1224", "Kota Cirebon", "Asia/Jakarta", "id.kemenag/1224", None, None),
    ("kotapekalongan", "1431", "Kota Pekalongan", "Asia/Jakarta", "id.kemenag/1431", None, None),
    ("palangkaraya", "2214", "Kota Palangkaraya", "Asia/Pontianak", "id.kemenag/2214", None, None),
    ("kotakediri", "1632", "Kota Kediri", "Asia/Jakarta", "id.kemenag/1632", None, None),
    ("kotategal", "1435", "Kota Tegal", "Asia/Jakarta", "id.kemenag/1435", None, None),
    ("banjarbaru", "2112", "Kota Banjarbaru", "Asia/Makassar", "id.kemenag/2112", None, None),
    ("binjai", "0226", "Kota Binjai", "Asia/Jakarta", "id.kemenag/0226", None, None),
    ("pematangsiantar", "0230", "Kota Pematangsiantar", "Asia/Jakarta", "id.kemenag/0230", None, None),
    ("tarakan", "2405", "Kota Tarakan", "Asia/Makassar", "id.kemenag/2405", None, None),
    ("singkawang", "2014", "Kota Singkawang", "Asia/Pontianak", "id.kemenag/2014", None, None),
    ("tanjungpinang", "0507", "Kota Tanjung Pinang", "Asia/Jakarta", "id.kemenag/0507", None, None),
    ("pangkalpinang", "0907", "Kota Pangkal Pinang", "Asia/Jakarta", "id.kemenag/0907", None, None),
    ("kotagorontalo", "2506", "Kota Gorontalo", "Asia/Makassar", "id.kemenag/2506", None, None),
    ("kabbekasi", "1203", "Kab. Bekasi", "Asia/Jakarta", "id.kemenag/1203", None, None),
    ("kabbogor", "1204", "Kab. Bogor", "Asia/Jakarta", "id.kemenag/1204", None, None),
    ("kabbandung", "1201", "Kab. Bandung", "Asia/Jakarta", "id.kemenag/1201", None, None),
    ("kabmalang", "1614", "Kab. Malang", "Asia/Jakarta", "id.kemenag/1614", None, None),
    ("jember", "1607", "Kab. Jember", "Asia/Jakarta", "id.kemenag/1607", None, None),
    ("kabsukabumi", "1216", "Kab. Sukabumi", "Asia/Jakarta", "id.kemenag/1216", None, None),
    ("cianjur", "1206", "Kab. Cianjur", "Asia/Jakarta", "id.kemenag/1206", None, None),
    ("karawang", "1210", "Kab. Karawang", "Asia/Jakarta", "id.kemenag/1210", None, None),
    ("kabcirebon", "1207", "Kab. Cirebon", "Asia/Jakarta", "id.kemenag/1207", None, None),
    ("sidoarjo", "1624", "Kab. Sidoarjo", "Asia/Jakarta", "id.kemenag/1624", None, None),
    ("cilacap", "1407", "Kab. Cilacap", "Asia/Jakarta", "id.kemenag/1407", None, None),
    ("kabtasikmalaya", "1218", "Kab. Tasikmalaya", "Asia/Jakarta", "id.kemenag/1218", None, None),
    ("indramayu", "1209", "Kab. Indramayu", "Asia/Jakarta", "id.kemenag/1209", None, None),
    ("banyumas", "1402", "Kab. Banyumas", "Asia/Jakarta", "id.kemenag/1402", None, None),
    ("subang", "1215", "Kab. Subang", "Asia/Jakarta", "id.kemenag/1215", None, None),
    ("gresik", "1606", "Kab. Gresik", "Asia/Jakarta", "id.kemenag/1606", None, None),
    ("klaten", "1414", "Kab. Klaten", "Asia/Jakarta", "id.kemenag/1414", None, None),
    ("jepara", "1410", "Kab. Jepara", "Asia/Jakarta", "id.kemenag/1410", None, None),
    ("sleman", "1504", "Kab. Sleman", "Asia/Jakarta", "id.kemenag/1504", None, None),
    ("bantul", "1501", "Kab. Bantul", "Asia/Jakarta", "id.kemenag/1501", None, None),
    ("kabsemarang", "1423", "Kab. Semarang", "Asia/Jakarta", "id.kemenag/1423", None, None),
    ("kudus", "1415", "Kab. Kudus", "Asia/Jakarta", "id.kemenag/1415", None, None),
    ("demak", "1408", "Kab. Demak", "Asia/Jakarta", "id.kemenag/1408", None, None),
    ("purwakarta", "1214", "Kab. Purwakarta", "Asia/Jakarta", "id.kemenag/1214", None, None),
    ("sumedang", "1217", "Kab. Sumedang", "Asia/Jakarta", "id.kemenag/1217", None, None),
    ("kabpasuruan", "1620", "Kab. Pasuruan", "Asia/Jakarta", "id.kemenag/1620", None, None),
    ("kotapasuruan", "1636", "Kota Pasuruan", "Asia/Jakarta", "id.kemenag/1636", None, None),
    ("kotaprobolinggo", "1637", "Kota Probolinggo", "Asia/Jakarta", "id.kemenag/1637", None, None),
    ("kotamadiun", "1633", "Kota Madiun", "Asia/Jakarta", "id.kemenag/1633", None, None),
    ("kotablitar", "1631", "Kota Blitar", "Asia/Jakarta", "id.kemenag/1631", None, None),
    ("kotamojokerto", "1635", "Kota Mojokerto", "Asia/Jakarta", "id.kemenag/1635", None, None),
    ("kotamagelang", "1430", "Kota Magelang", "Asia/Jakarta", "id.kemenag/1430", None, None),
    ("salatiga", "1432", "Kota Salatiga", "Asia/Jakarta", "id.kemenag/1432", None, None),
    ("bitung", "2912", "Kota Bitung", "Asia/Makassar", "id.kemenag/2912", None, None),
    ("lhokseumawe", "0121", "Kota Lhokseumawe", "Asia/Jakarta", "id.kemenag/0121", None, None),
    ("ternate", "3209", "Kota Ternate", "Asia/Jayapura", "id.kemenag/3209", None, None),
    ("bontang", "2309", "Kota Bontang", "Asia/Makassar", "id.kemenag/2309", None, None),
    ("baubau", "2716", "Kota Bau-Bau", "Asia/Makassar", "id.kemenag/2716", None, None),
    ("parepare", "2624", "Kota Parepare", "Asia/Makassar", "id.kemenag/2624", None, None),
    ("palopo", "2623", "Kota Palopo", "Asia/Makassar", "id.kemenag/2623", None, None),
    ("kotabima", "1809", "Kota Bima", "Asia/Makassar", "id.kemenag/1809", None, None),
    ("metro", "1015", "Kota Metro", "Asia/Jakarta", "id.kemenag/1015", None, None),
    ("kabtegal", "1426", "Kab. Tegal", "Asia/Jakarta", "id.kemenag/1426", None, None),
    ("situbondo", "1625", "Kab. Situbondo", "Asia/Jakarta", "id.kemenag/1625", None, None),
    ("banyuwangi", "1602", "Kab. Banyuwangi", "Asia/Jakarta", "id.kemenag/1602", None, None),
    ("jombang", "1608", "Kab. Jombang", "Asia/Jakarta", "id.kemenag/1608", None, None),
    ("tulungagung", "1629", "Kab. Tulungagung", "Asia/Jakarta", "id.kemenag/1629", None, None),
    ("pati", "1417", "Kab. Pati", "Asia/Jakarta", "id.kemenag/1417", None, None),
    ("pemalang", "1419", "Kab. Pemalang", "Asia/Jakarta", "id.kemenag/1419", None, None),
    ("boyolali", "1405", "Kab. Boyolali", "Asia/Jakarta", "id.kemenag/1405", None, None),
    ("bojonegoro", "1604", "Kab. Bojonegoro", "Asia/Jakarta", "id.kemenag/1604", None, None),
    ("lamongan", "1610", "Kab. Lamongan", "Asia/Jakarta", "id.kemenag/1610", None, None),
    ("tuban", "1628", "Kab. Tuban", "Asia/Jakarta", "id.kemenag/1628", None, None),
    ("kuningan", "1211", "Kab. Kuningan", "Asia/Jakarta", "id.kemenag/1211", None, None),
    ("majalengka", "1212", "Kab. Majalengka", "Asia/Jakarta", "id.kemenag/1212", None, None),
    ("ciamis", "1205", "Kab. Ciamis", "Asia/Jakarta", "id.kemenag/1205", None, None),
    ("lebak", "1101", "Kab. Lebak", "Asia/Jakarta", "id.kemenag/1101", None, None),
    ("pandeglang", "1102", "Kab. Pandeglang", "Asia/Jakarta", "id.kemenag/1102", None, None),
    ("sumenep", "1626", "Kab. Sumenep", "Asia/Jakarta", "id.kemenag/1626", None, None),
    ("pamekasan", "1619", "Kab. Pamekasan", "Asia/Jakarta", "id.kemenag/1619", None, None),
    ("bangkalan", "1601", "Kab. Bangkalan", "Asia/Jakarta", "id.kemenag/1601", None, None),
    ("nganjuk", "1616", "Kab. Nganjuk", "Asia/Jakarta", "id.kemenag/1616", None, None),
    ("ponorogo", "1621", "Kab. Ponorogo", "Asia/Jakarta", "id.kemenag/1621", None, None),
    ("lumajang", "1611", "Kab. Lumajang", "Asia/Jakarta", "id.kemenag/1611", None, None),
    ("bondowoso", "1605", "Kab. Bondowoso", "Asia/Jakarta", "id.kemenag/1605", None, None),
    ("sragen", "1424", "Kab. Sragen", "Asia/Jakarta", "id.kemenag/1424", None, None),
    ("grobogan", "1409", "Kab. Grobogan", "Asia/Jakarta", "id.kemenag/1409", None, None),
    ("kabprobolinggo", "1622", "Kab. Probolinggo", "Asia/Jakarta", "id.kemenag/1622", None, None),
    ("kotasorong", "3413", "Kota Sorong", "Asia/Jayapura", "id.kemenag/3413", None, None),
    # The second round of 12 November 2026 (KemenagKabkota.kt's round2): each named by its unit, checked at the unit's point,
    # the app's own point for its kota or regency seat.
    ("lomboktimur", "1805", "Kab. Lombok Timur", "Asia/Makassar", "id.kemenag/1805", None, None),
    ("asahan", "0201", "Kab. Asahan", "Asia/Jakarta", "id.kemenag/0201", None, None),
    ("lomboktengah", "1804", "Kab. Lombok Tengah", "Asia/Makassar", "id.kemenag/1804", None, None),
    ("langkat", "0210", "Kab. Langkat", "Asia/Jakarta", "id.kemenag/0210", None, None),
    ("purbalingga", "1420", "Kab. Purbalingga", "Asia/Jakarta", "id.kemenag/1420", None, None),
    ("lampungutara", "1002", "Kab. Lampung Utara", "Asia/Jakarta", "id.kemenag/1002", None, None),
    ("blora", "1404", "Kab. Blora", "Asia/Jakarta", "id.kemenag/1404", None, None),
    ("buleleng", "1703", "Kab. Buleleng", "Asia/Makassar", "id.kemenag/1703", None, None),
    ("gunungkidul", "1502", "Kab. Gunungkidul", "Asia/Jakarta", "id.kemenag/1502", None, None),
    ("sambas", "2009", "Kab. Sambas", "Asia/Pontianak", "id.kemenag/2009", None, None),
    ("rembang", "1422", "Kab. Rembang", "Asia/Jakarta", "id.kemenag/1422", None, None),
    ("kabbanjar", "2102", "Kab. Banjar", "Asia/Makassar", "id.kemenag/2102", None, None),
    ("polewalimandar", "3006", "Kab. Polewali Mandar", "Asia/Makassar", "id.kemenag/3006", None, None),
    ("wajo", "2621", "Kab. Wajo", "Asia/Makassar", "id.kemenag/2621", None, None),
    ("tabanan", "1708", "Kab. Tabanan", "Asia/Makassar", "id.kemenag/1708", None, None),
    ("pidie", "0116", "Kab. Pidie", "Asia/Jakarta", "id.kemenag/0116", None, None),
    ("oganilir", "0808", "Kab. Ogan Ilir", "Asia/Jakarta", "id.kemenag/0808", None, None),
    ("lahat", "0803", "Kab. Lahat", "Asia/Jakarta", "id.kemenag/0803", None, None),
    ("malukutengah", "3105", "Kab. Maluku Tengah", "Asia/Jayapura", "id.kemenag/3105", None, None),
    ("labuhanbatu", "0207", "Kab. Labuhanbatu", "Asia/Jakarta", "id.kemenag/0207", None, None),
    ("kapuas", "2205", "Kab. Kapuas", "Asia/Jakarta", "id.kemenag/2205", None, None),
    ("kotawaringintimur", "2208", "Kab. Kotawaringin Timur", "Asia/Pontianak", "id.kemenag/2208", None, None),
    ("sintang", "2012", "Kab. Sintang", "Asia/Pontianak", "id.kemenag/2012", None, None),
    ("luwuutara", "2611", "Kab. Luwu Utara", "Asia/Makassar", "id.kemenag/2611", None, None),
    ("tanjungjabungbarat", "0607", "Kab. Tanjung Jabung Barat", "Asia/Jakarta", "id.kemenag/0607", None, None),
    ("sikka", "1915", "Kab. Sikka", "Asia/Makassar", "id.kemenag/1915", None, None),
    ("jembrana", "1705", "Kab. Jembrana", "Asia/Makassar", "id.kemenag/1705", None, None),
    ("sumbabaratdaya", "1917", "Kab. Sumba Barat Daya", "Asia/Makassar", "id.kemenag/1917", None, None),
    ("bangka", "0901", "Kab. Bangka", "Asia/Jakarta", "id.kemenag/0901", None, None),
    ("niasselatan", "0214", "Kab. Nias Selatan", "Asia/Jakarta", "id.kemenag/0214", None, None),
    ("bengkayang", "2001", "Kab. Bengkayang", "Asia/Pontianak", "id.kemenag/2001", None, None),
    ("karo", "0206", "Kab. Karo", "Asia/Jakarta", "id.kemenag/0206", None, None),
    ("tapanuliutara", "0224", "Kab. Tapanuli Utara", "Asia/Jakarta", "id.kemenag/0224", None, None),
    ("ende", "1903", "Kab. Ende", "Asia/Makassar", "id.kemenag/1903", None, None),
    ("dairi", "0203", "Kab. Dairi", "Asia/Jakarta", "id.kemenag/0203", None, None),
    ("rejanglebong", "0708", "Kab. Rejang Lebong", "Asia/Jakarta", "id.kemenag/0708", None, None),
    ("sinjai", "2616", "Kab. Sinjai", "Asia/Makassar", "id.kemenag/2616", None, None),
    ("manggaraibarat", "1909", "Kab. Manggarai Barat", "Asia/Makassar", "id.kemenag/1909", None, None),
    ("timortengahutara", "1921", "Kab. Timor Tengah Utara", "Asia/Makassar", "id.kemenag/1921", None, None),
    ("hulusungaitengah", "2105", "Kab. Hulu Sungai Tengah", "Asia/Makassar", "id.kemenag/2105", None, None),
    ("poso", "2809", "Kab. Poso", "Asia/Makassar", "id.kemenag/2809", None, None),
    ("torajautara", "2620", "Kab. Toraja Utara", "Asia/Makassar", "id.kemenag/2620", None, None),
    ("sumbatimur", "1919", "Kab. Sumba Timur", "Asia/Makassar", "id.kemenag/1919", None, None),
    ("sijunjung", "0309", "Kab. Sijunjung", "Asia/Jakarta", "id.kemenag/0309", None, None),
    ("merauke", "3315", "Kab. Merauke", "Asia/Jayapura", "id.kemenag/3315", None, None),
    ("hulusungaiutara", "2106", "Kab. Hulu Sungai Utara", "Asia/Makassar", "id.kemenag/2106", None, None),
    ("minahasautara", "2911", "Kab. Minahasa Utara", "Asia/Makassar", "id.kemenag/2911", None, None),
    ("belu", "1902", "Kab. Belu", "Asia/Makassar", "id.kemenag/1902", None, None),
    ("dompu", "1802", "Kab. Dompu", "Asia/Makassar", "id.kemenag/1802", None, None),
    ("lubuklinggau", "0814", "Kota Lubuklinggau", "Asia/Jakarta", "id.kemenag/0814", None, None),
    ("alor", "1901", "Kab. Alor", "Asia/Makassar", "id.kemenag/1901", None, None),
    ("puncakjaya", "3322", "Kab. Puncak Jaya", "Asia/Jayapura", "id.kemenag/3322", None, None),
    ("prabumulih", "0817", "Kota Prabumulih", "Asia/Jakarta", "id.kemenag/0817", None, None),
    ("bangkabarat", "0902", "Kab. Bangka Barat", "Asia/Jakarta", "id.kemenag/0902", None, None),
    ("halmaherautara", "3203", "Kab. Halmahera Utara", "Asia/Jayapura", "id.kemenag/3203", None, None),
    ("acehbarat", "0101", "Kab. Aceh Barat", "Asia/Jakarta", "id.kemenag/0101", None, None),
    ("langsa", "0120", "Kota Langsa", "Asia/Jakarta", "id.kemenag/0120", None, None),
    ("tebingtinggi", "0233", "Kota Tebing Tinggi", "Asia/Jakarta", "id.kemenag/0233", None, None),
    ("nunukan", "2403", "Kab. Nunukan", "Asia/Makassar", "id.kemenag/2403", None, None),
    ("nabire", "3317", "Kab. Nabire", "Asia/Jayapura", "id.kemenag/3317", None, None),
    ("kabjayapura", "3307", "Kab. Jayapura", "Asia/Jayapura", "id.kemenag/3307", None, None),
    ("belitung", "0905", "Kab. Belitung", "Asia/Jakarta", "id.kemenag/0905", None, None),
    ("biaknumfor", "3302", "Kab. Biak Numfor", "Asia/Jayapura", "id.kemenag/3302", None, None),
    ("baritoutara", "2203", "Kab. Barito Utara", "Asia/Pontianak", "id.kemenag/2203", None, None),
    ("acehbaratdaya", "0102", "Kab. Aceh Barat Daya", "Asia/Jakarta", "id.kemenag/0102", None, None),
    ("samosir", "0219", "Kab. Samosir", "Asia/Jakarta", "id.kemenag/0219", None, None),
    ("kepulauansangihe", "2905", "Kab. Kepulauan Sangihe", "Asia/Makassar", "id.kemenag/2905", None, None),
    ("gunungsitoli", "0227", "Kota Gunungsitoli", "Asia/Jakarta", "id.kemenag/0227", None, None),
    ("pagaralam", "0815", "Kota Pagar Alam", "Asia/Jakarta", "id.kemenag/0815", None, None),
    ("sumbawabarat", "1808", "Kab. Sumbawa Barat", "Asia/Makassar", "id.kemenag/1808", None, None),
    ("lembata", "1906", "Kab. Lembata", "Asia/Makassar", "id.kemenag/1906", None, None),
    ("acehsingkil", "0106", "Kab. Aceh Singkil", "Asia/Jakarta", "id.kemenag/0106", None, None),
    ("bulungan", "2401", "Kab. Bulungan", "Asia/Makassar", "id.kemenag/2401", None, None),
    ("payakumbuh", "0317", "Kota Payakumbuh", "Asia/Jakarta", "id.kemenag/0317", None, None),
    ("kepulauansula", "3205", "Kab. Kepulauan Sula", "Asia/Jayapura", "id.kemenag/3205", None, None),
    ("kotamobagu", "2913", "Kota Kotamobagu", "Asia/Makassar", "id.kemenag/2913", None, None),
    ("lannyjaya", "3311", "Kab. Lanny Jaya", "Asia/Jayapura", "id.kemenag/3311", None, None),
    ("belitungtimur", "0906", "Kab. Belitung Timur", "Asia/Jakarta", "id.kemenag/0906", None, None),
    ("sungaipenuh", "0611", "Kota Sungai Penuh", "Asia/Jakarta", "id.kemenag/0611", None, None),
    ("asmat", "3301", "Kab. Asmat", "Asia/Jayapura", "id.kemenag/3301", None, None),
    ("kepulauanaru", "3103", "Kab. Kepulauan Aru", "Asia/Jayapura", "id.kemenag/3103", None, None),
    ("tomohon", "2915", "Kota Tomohon", "Asia/Makassar", "id.kemenag/2915", None, None),
    ("sibolga", "0231", "Kota Sibolga", "Asia/Jakarta", "id.kemenag/0231", None, None),
    ("pariaman", "0316", "Kota Pariaman", "Asia/Jakarta", "id.kemenag/0316", None, None),
    ("fakfak", "3401", "Kab. Fakfak", "Asia/Jayapura", "id.kemenag/3401", None, None),
    ("tual", "3111", "Kota Tual", "Asia/Jayapura", "id.kemenag/3111", None, None),
    ("subulussalam", "0123", "Kota Subulussalam", "Asia/Jakarta", "id.kemenag/0123", None, None),
    ("telukbintuni", "3411", "Kab. Teluk Bintuni", "Asia/Jayapura", "id.kemenag/3411", None, None),
    ("malinau", "2402", "Kab. Malinau", "Asia/Makassar", "id.kemenag/2402", None, None),
    ("buruselatan", "3102", "Kab. Buru Selatan", "Asia/Jayapura", "id.kemenag/3102", None, None),
    ("kotasolok", "0319", "Kota Solok", "Asia/Jakarta", "id.kemenag/0319", None, None),
    ("sorongselatan", "3409", "Kab. Sorong Selatan", "Asia/Jayapura", "id.kemenag/3409", None, None),
    ("padangpanjang", "0315", "Kota Padangpanjang", "Asia/Jakarta", "id.kemenag/0315", None, None),
    ("sabang", "0122", "Kota Sabang", "Asia/Jakarta", "id.kemenag/0122", None, None),
    ("manokwariselatan", "3404", "Kab. Manokwari Selatan", "Asia/Jayapura", "id.kemenag/3404", None, None),
    ("ngawi", "1617", "Kab. Ngawi", "Asia/Jakarta", "id.kemenag/1617", None, None),
    ("sampang", "1623", "Kab. Sampang", "Asia/Jakarta", "id.kemenag/1623", None, None),
    ("trenggalek", "1627", "Kab. Trenggalek", "Asia/Jakarta", "id.kemenag/1627", None, None),
    ("sumbawa", "1807", "Kab. Sumbawa", "Asia/Makassar", "id.kemenag/1807", None, None),
    ("manggarai", "1908", "Kab. Manggarai", "Asia/Makassar", "id.kemenag/1908", None, None),
    ("timortengahselatan", "1920", "Kab. Timor Tengah Selatan", "Asia/Makassar", "id.kemenag/1920", None, None),
    ("bone", "2603", "Kab. Bone", "Asia/Makassar", "id.kemenag/2603", None, None),
    ("muna", "2713", "Kab. Muna", "Asia/Makassar", "id.kemenag/2713", None, None),
    ("banggai", "2801", "Kab. Banggai", "Asia/Makassar", "id.kemenag/2801", None, None),
    ("morowali", "2806", "Kab. Morowali", "Asia/Makassar", "id.kemenag/2806", None, None),
    ("minahasa", "2908", "Kab. Minahasa", "Asia/Makassar", "id.kemenag/2908", None, None),
    ("buru", "3101", "Kab. Buru", "Asia/Jayapura", "id.kemenag/3101", None, None),
    ("manokwari", "3403", "Kab. Manokwari", "Asia/Jayapura", "id.kemenag/3403", None, None),
    ("padangsidempuan", "0229", "Kota Padangsidempuan", "Asia/Jakarta", "id.kemenag/0229", None, None),
    ("bungo", "0602", "Kab. Bungo", "Asia/Jakarta", "id.kemenag/0602", None, None),
    ("ogankomeringulu", "0810", "Kab. Ogan Komering Ulu", "Asia/Jakarta", "id.kemenag/0810", None, None),
    ("bireuen", "0113", "Kab. Bireuen", "Asia/Jakarta", "id.kemenag/0113", None, None),
    ("maros", "2612", "Kab. Maros", "Asia/Makassar", "id.kemenag/2612", None, None),
    ("kotawaringinbarat", "2207", "Kab. Kotawaringin Barat", "Asia/Pontianak", "id.kemenag/2207", None, None),
    ("karimun", "0502", "Kab. Karimun", "Asia/Jakarta", "id.kemenag/0502", None, None),
    ("tabalong", "2108", "Kab. Tabalong", "Asia/Makassar", "id.kemenag/2108", None, None),
    ("tolitoli", "2812", "Kab. Toli-Toli", "Asia/Makassar", "id.kemenag/2812", None, None),
    ("majene", "3001", "Kab. Majene", "Asia/Makassar", "id.kemenag/3001", None, None),
    ("pulaumorotai", "3207", "Kab. Pulau Morotai", "Asia/Jayapura", "id.kemenag/3207", None, None),
]
KEYS = ("subuh", "terbit", "dzuhur", "ashar", "maghrib", "isya")
IMSAK_WIDE = 15  # minutes before Subuh past which a printed imsak is raised for a human (myQuran prints 10)


def fold(name):
    """A place name for comparison: upper case, letters and digits only ("Kab. Jayawijaya" and "KAB. JAYAWIJAYA" agree)."""
    return re.sub(r"[^A-Z0-9]", "", str(name).upper())


def parse(body, expected=None):
    """The month's rows and the place name; with `expected`, a response for another place is an error."""
    j = json.loads(body.decode("utf-8", "replace"))
    try:
        if not j.get("status"):
            raise FetchError("status false (" + str(j)[:120] + ")")
        lokasi = j["data"].get("lokasi", "")
        if expected is not None and fold(lokasi) != fold(expected):
            raise FetchError(f"the response is for {lokasi!r}, not {expected!r}: myQuran's id may have been renumbered")
        rows = {}
        for x in j["data"]["jadwal"]:
            rows[str(x["date"])] = [x[k] for k in KEYS]
    except (AttributeError, TypeError):
        raise FetchError("the answer is not in the shape of a month's schedule")
    return rows, lokasi


def imsak_of(body):
    """The month's printed imsak by date ({} when the answer prints none)."""
    try:
        j = json.loads(body.decode("utf-8", "replace"))
        return {str(x["date"]): x["imsak"] for x in j["data"]["jadwal"] if x.get("imsak") not in (None, "", "-")}
    except (ValueError, KeyError, TypeError, AttributeError):
        return {}


def minutes(t):
    h, m = t.split(":")
    return int(h) * 60 + int(m)


def check_month(rows, year, month):
    """Raises unless `rows` holds every day of the month asked and no other day: a short, empty or
    wrong-month answer is never taken for the month's table (audit finding F24)."""
    days = calendar.monthrange(year, month)[1]
    want = {f"{year}-{month:02d}-{d:02d}" for d in range(1, days + 1)}
    got = set(rows)
    if got != want:
        other = len(got - want)
        raise FetchError(f"the answer holds {len(got & want)} of the month's {days} days"
                         + (f" and {other} that are not the month's" if other else ""))


def months_wanted(today):
    """(year, month) of the current month and the next."""
    y, m = today.year, today.month
    nxt = (y + 1, 1) if m == 12 else (y, m + 1)
    return [(y, m), nxt]


def held_rows(ctx, key, year):
    """What is held of a place's year: the round's table, and the monitor's own over it (the newer wins)."""
    rows = read_table_rows(os.path.join(ctx.official, *ROUND, f"{key}-{year}.txt"))
    rows.update(ctx.held(f"{key}-{year}"))
    return rows


def month_held(ctx, key, year, month, held=None):
    """Whether every day of the month is held, by the monitor or by the round (audit finding F23)."""
    if held is None:
        held = held_rows(ctx, key, year)
    days = calendar.monthrange(year, month)[1]
    return all(f"{year}-{month:02d}-{d:02d}" in held for d in range(1, days + 1))


def reread_months(today, months):
    """Which of `months` (the (year, month)s held beyond the next) this run reads again: the week's
    number picks one place in a cycle of REREAD_WEEKS, and each month has its own, so over REREAD_WEEKS
    weekly runs every month held is read again once, whatever else is held (no state to keep)."""
    turn = (today.toordinal() // 7) % REREAD_WEEKS
    return [(y, m) for y, m in sorted(set(months)) if (y * 12 + m) % REREAD_WEEKS == turn]


def fetch(ctx):
    tables = {}
    held = {}
    taken = set()  # (key, year, month) read this run

    def of(key, year):
        if (key, year) not in held:
            held[(key, year)] = held_rows(ctx, key, year)
        return held[(key, year)]

    def table(key, name, zone, entry, lat, lon, year, imsak=False):
        tid = f"{key}-{year}" + ("-imsak" if imsak else "")
        if tid not in tables:
            tables[tid] = Table(tid, f"{name} {year}" + (" imsak" if imsak else ""), lat, lon, zone, "ID", "- Im" if imsak else "F+E S D A M I",
                                entry=entry, source_line=f"Kemenag as api.myquran.com republishes it ({BASE}/<id>/<yyyy>/<mm>, {name})"
                                + ("; its imsak column beside the Subuh" if imsak else ""))
        return tables[tid]

    def imsak_rows(label, read, body):
        """[Subuh, imsak] by date for the days of `read` the answer prints an imsak for (ruling R284)."""
        printed = imsak_of(body)
        out, bad, wide = {}, [], []
        for date in sorted(read.rows):
            if date not in printed:
                continue
            subuh = read.rows[date][0]
            try:
                imsak = norm_time(printed[date])
            except FetchError:
                bad.append(date)
                continue
            if imsak != "-" and subuh != "-":
                gap = minutes(subuh) - minutes(imsak)
                if gap < 0:
                    bad.append(date)
                    imsak = "-"
                elif gap > IMSAK_WIDE:
                    wide.append((date, gap))
            out[date] = [subuh, imsak]
        raise_imsak(ctx, f"{label} imsak", bad, wide, IMSAK_WIDE)
        return out

    def take(key, cid, name, zone, entry, lat, lon, year, month, keep=True):
        """One month of one place: the days read. A held day that reads otherwise today is noted; with
        `keep` unset (a month read again) the table takes the month only when a held day changed."""
        url = f"{BASE}/{cid}/{year}/{month:02d}"
        body = ctx.http.get(url, gap=KEMENAG_GAP)
        taken.add((key, year, month))
        rows, _ = parse(body, expected=name)
        check_month(rows, year, month)
        label = f"{name} {year}-{month:02d}"
        read = Table("read", label, lat, lon, zone, "ID", "F+E S D A M I")
        if add_all(ctx, read, rows, label) == 0:
            raise FetchError("no day of the answer could be read")
        before = of(key, year)
        changed = sorted(d for d in read.rows if d in before and before[d] != read.rows[d])
        if changed:
            ctx.note(f"{label}: {len(changed)} held day{'s' if len(changed) > 1 else ''} changed (the first {changed[0]}); "
                     "the table now holds what was read today")
        if keep or changed:
            t = table(key, name, zone, entry, lat, lon, year)
            t.rows.update(read.rows)
            t.raw.append((f"{key}-{year}-{month:02d}.json", body))
        imsak = imsak_rows(label, read, body)
        if imsak:
            was = ctx.held(f"{key}-{year}-imsak")
            moved = sorted(d for d in imsak if d in was and was[d] != imsak[d])
            if moved:
                ctx.note(f"{label}: {len(moved)} held imsak day{'s' if len(moved) > 1 else ''} changed (the first {moved[0]}); "
                         "the imsak table now holds what was read today")
            if keep or changed or moved or any(d not in was for d in imsak):
                table(key, name, zone, entry, lat, lon, year, imsak=True).rows.update(imsak)
        return len(read.rows)

    wanted = months_wanted(ctx.today)
    for key, cid, name, zone, entry, lat, lon in PLACES:
        failed = []
        read = 0
        for year, month in wanted:
            try:
                read += take(key, cid, name, zone, entry, lat, lon, year, month)
            except (FetchError, KeyError, ValueError) as e:
                failed.append(f"{year}-{month:02d}: {e}")
        if read == 0:
            # A place nothing was read for is never silence (audit finding F24).
            ctx.error(f"{name}: no rows this run ({'; '.join(failed)})")
        else:
            for failure in failed:
                ctx.error(f"{name} {failure}")
    # From October: the next year, once it is published (probed on the first place that lacks a month of it).
    nxt = ctx.today.year + 1
    if ctx.today.month >= 10:
        def lacking(place):
            return [m for m in range(1, 13) if (place[0], nxt, m) not in taken and not month_held(ctx, place[0], nxt, m, of(place[0], nxt))]
        behind = [(place, lacking(place)) for place in PLACES]
        behind = [(place, months) for place, months in behind if months]
        published = bool(behind)
        if behind:
            (key, cid, name, zone, entry, lat, lon), months = behind[0]
            try:
                take(key, cid, name, zone, entry, lat, lon, nxt, months[0])
            except (FetchError, KeyError, ValueError) as e:
                published = False
                ctx.note(f"{nxt} not published yet on myQuran ({e})")
        if published:
            for (key, cid, name, zone, entry, lat, lon), months in behind[:BACKFILL_PLACES]:
                for month in months:
                    if (key, nxt, month) in taken:
                        continue
                    try:
                        take(key, cid, name, zone, entry, lat, lon, nxt, month)
                    except (FetchError, KeyError, ValueError) as e:
                        ctx.error(f"{name} {nxt}-{month:02d}: {e}")
            if len(behind) > BACKFILL_PLACES:
                ctx.note(f"{nxt}: {len(behind) - BACKFILL_PLACES} more places' year is fetched on the next runs, {BACKFILL_PLACES} places a run")
    # One of the months held beyond the next, read again for every place that holds it (audit finding F28).
    years = range(ctx.today.year, ctx.today.year + 4)
    beyond = {}
    for place in PLACES:
        for year in years:
            rows = of(place[0], year)
            for month in range(1, 13):
                if rows and (year, month) > wanted[-1] and (place[0], year, month) not in taken and month_held(ctx, place[0], year, month, rows):
                    beyond.setdefault((year, month), []).append(place)
    for year, month in reread_months(ctx.today, beyond):
        for key, cid, name, zone, entry, lat, lon in beyond[(year, month)]:
            try:
                take(key, cid, name, zone, entry, lat, lon, year, month, keep=False)
            except (FetchError, KeyError, ValueError) as e:
                ctx.error(f"{name} {year}-{month:02d}, read again: {e}")
    return [t for t in tables.values() if t.rows]
