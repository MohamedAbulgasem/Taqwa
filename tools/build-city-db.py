#!/usr/bin/env python3
"""Convert GeoNames cities15000 into the compact CSV Taqwa bundles.

Source: https://download.geonames.org/export/dump/cities15000.zip  (CC BY 4.0)
Output columns: name,region,countryCode,lat,lon,timezone
"""
import csv, io, sys, urllib.request, zipfile

URL = "https://download.geonames.org/export/dump/cities15000.zip"
OUT = "shared/src/commonMain/composeResources/files/cities.csv"

with urllib.request.urlopen(URL) as resp:
    zf = zipfile.ZipFile(io.BytesIO(resp.read()))
    raw = zf.read("cities15000.txt").decode("utf-8")

rows = []
for line in raw.splitlines():
    f = line.split("\t")
    # 1 name, 8 country code, 10 admin1 code, 4 lat, 5 lon, 17 timezone, 14 population
    rows.append((f[1], f[10], f[8], f[4], f[5], f[17], int(f[14] or 0)))

rows.sort(key=lambda r: -r[6])  # population descending: London GB outranks London CA

with open(OUT, "w", newline="", encoding="utf-8") as fh:
    w = csv.writer(fh)
    w.writerow(["name", "region", "country", "lat", "lon", "tz"])
    for r in rows:
        w.writerow(r[:6])

print(f"wrote {len(rows)} cities to {OUT}", file=sys.stderr)
