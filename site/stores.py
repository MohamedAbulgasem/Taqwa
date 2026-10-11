"""Where the app can be had, and the one block that says so everywhere on taqwa.world.

site/stores.json holds one address per store, null until that store has the app. A store is live
once its address is set: going live is that one edit and a push. Then its official badge replaces
the "coming" wording in every place the block is rendered — the home page's hero (build.py), the
city pages' desktop pitch and app section (timetables.py), and the download pages under /get/
(build.py, in English) — and, for the App Store, Safari's
Smart App Banner appears on every page. `exodus` is Exodus Privacy's report on the Play build,
linked from the home page's privacy section once it exists (build.py's exodus_row).

The block is one function so that launch day is one edit; docs/STORE-CHECKLIST.md names every
place it appears. Each place's badges carry that place's campaign tags (site/campaigns.py), so the
stores' own reports can tell installs that began on taqwa.world from the rest; the addresses here
stay bare, and the structured data and the Smart App Banner use them as they are.

`app_store_provider_token` is the App Store Connect provider token (digits) that Apple's campaign
links need, null until Mohamed copies it from App Store Connect; until then the App Store badges
lead to the bare address (see site/campaigns.py).
"""
import html
import json
import os
import re
import sys

import campaigns

SITE = os.path.dirname(os.path.abspath(__file__))
SOURCE_URL = "https://github.com/MohamedAbulgasem/Taqwa"

STORE_ADDRESSES = {
    "google_play": r"https://play\.google\.com/store/apps/details\?id=world\.taqwa\.app",
    "app_store": r"https://apps\.apple\.com/app/id\d+",
    "exodus": r"https://reports\.exodus-privacy\.eu\.org/[\w/.-]+",
}


PROVIDER_TOKEN = "app_store_provider_token"  # optional: absent and null mean the same


def problems(stores: dict) -> list:
    """What is wrong with a parsed stores.json: a key it should not have or lacks, an address that is
    not one of that store's, a provider token that is not digits. Nothing for a good file."""
    missing, extra = set(STORE_ADDRESSES) - set(stores), set(stores) - set(STORE_ADDRESSES) - {PROVIDER_TOKEN}
    if missing or extra:
        return [f"expected exactly {sorted(STORE_ADDRESSES)} and optionally {PROVIDER_TOKEN}, "
                f"missing {sorted(missing)}, extra {sorted(extra)}"]
    found = [f"{stores[key]!r} is not a {key} address" for key, pattern in STORE_ADDRESSES.items()
             if stores[key] is not None and not (isinstance(stores[key], str) and re.fullmatch(pattern, stores[key]))]
    token = stores.get(PROVIDER_TOKEN)
    if token is not None and not (isinstance(token, str) and campaigns.PROVIDER_TOKEN.fullmatch(token)):
        found.append(f"{PROVIDER_TOKEN} {token!r} is not App Store Connect's provider token, a string of digits")
    return found


def load_stores() -> dict:
    """site/stores.json, refused outright if anything in it is not what that key holds."""
    with open(os.path.join(SITE, "stores.json"), encoding="utf-8") as f:
        stores = json.load(f)
    found = problems(stores)
    if found:
        sys.exit("site/stores.json: " + "; ".join(found))
    return stores


STORES = load_stores()
CAMPAIGNS = campaigns.load()

# The stores' own badges, as their owners publish them, in site/assets/badges/: Apple's from
# tools.applemediaservices.com, black for light pages and white for dark, in the languages Apple
# translates "Download on the" into (English, French, Turkish, Indonesian; Arabic, Urdu and Bengali
# get the English badge, and "App Store" is English everywhere); Google's from play.google.com's
# badge page, in all seven. Both owners forbid redrawing or altering them, so they are only sized.
# Google's PNGs carry their own clear space in one of two shapes, and are scaled so the badge
# itself stands as tall as Apple's, 40 px. Widths are Apple's viewBox at that height.
APP_STORE_BADGE_WIDTH = {"en": 120, "fr": 127, "tr": 151, "id": 120}
GOOGLE_PLAY_BADGE_SIZE = {"fr": (134, 52), "tr": (134, 52), "id": (134, 52)}
GOOGLE_PLAY_BADGE_DEFAULT = (155, 60)


def block(cfg: dict, lang: str, *, beta_anchor: bool, channel) -> str:
    """The ways to get the app: a badge per live store, the Android beta button where the page
    carries the beta section (`beta_anchor`: the home page while tools/site-beta.py has not removed
    it; never a city page), and a line naming the stores still to come. Each badge leads to its
    store with `channel`'s campaign tags (campaigns.HOME on the home pages, campaigns.CITY on the
    city pages, the channel's own on its /get/ page; None for the bare address). `{root}` in the
    badge images is filled by the page writer, like every other placeholder in a body."""
    words = cfg["stores"]
    to = campaigns.links(STORES, CAMPAIGNS, channel)
    items = []
    if to["google_play"]:
        width, height = GOOGLE_PLAY_BADGE_SIZE.get(lang, GOOGLE_PLAY_BADGE_DEFAULT)
        items.append(
            f'<a class="badge" href="{html.escape(to["google_play"])}"><img src="{{root}}assets/badges/google-play-{lang}.png" '
            f'width="{width}" height="{height}" alt="{html.escape(words["google_play_alt"])}"></a>')
    if to["app_store"]:
        badge = lang if lang in APP_STORE_BADGE_WIDTH else "en"
        items.append(
            f'<a class="badge" href="{html.escape(to["app_store"])}"><picture>'
            f'<source srcset="{{root}}assets/badges/app-store-{badge}-white.svg" media="(prefers-color-scheme: dark)">'
            f'<img src="{{root}}assets/badges/app-store-{badge}-black.svg" width="{APP_STORE_BADGE_WIDTH[badge]}" '
            f'height="40" alt="{html.escape(words["app_store_alt"])}"></picture></a>')
    if beta_anchor:
        items.append(f'<a class="pill" href="#beta">{html.escape(words["beta"], quote=False)}</a>')
    coming = {(False, False): "coming_both", (True, False): "coming_app_store",
              (False, True): "coming_google_play"}.get((bool(STORES["google_play"]), bool(STORES["app_store"])))
    line = (html.escape(words[coming], quote=False) + " " if coming else "") + \
        f'<a href="{SOURCE_URL}">{html.escape(words["source"], quote=False)}</a>{words["source_end"]}'
    items.append(f'<span class="soon">{line}</span>')
    return '<div class="stores">\n        ' + "\n        ".join(items) + "\n      </div>"


def app_banner() -> str:
    """Safari's Smart App Banner, on every page once the App Store has the app, and on none before:
    a banner for an app Safari cannot find shows nothing useful.

    It carries no campaign tags, even once the provider token is set. App Store Connect Help says
    to "add your campaign token and provider token when implementing a Smart App Banner" but
    gives no syntax, and Apple's Smart App Banner page (developer.apple.com/documentation/webkit/
    promoting-apps-with-smart-app-banners, read 11 October 2026) lists only app-id and
    app-argument. The `affiliate-data` parameter appeared only in the retired Safari Web Content
    Guide, as "your iTunes affiliate string"; the `affiliate-data=pt=…&ct=…` form is a forum guess
    that one developer reported counting nothing. Tag the banner once Apple documents it."""
    if not STORES["app_store"]:
        return ""
    return f'  <meta name="apple-itunes-app" content="app-id={STORES["app_store"].rsplit("id", 1)[1]}">\n'
