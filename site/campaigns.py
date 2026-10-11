"""Which way a reader came to the app: the campaign tags on taqwa.world's links to the stores.

site/campaigns.json names every channel a link to the app is handed out through (a poster in a
masjid, a post, a README, a creator's bio, the app's own Share row) and gives it the three tags
Google Play's reports read. Each channel has a short address of its own on the site,
taqwa.world/get/<channel>/ (build.py writes them, and taqwa.world/get/ with no tags at all): a
phone is sent straight on to its store with the channel's tags on the link, and anything else
sees the two badges. The site's own badges are channels too, so installs that begin on
taqwa.world are told apart from the rest: the home pages' as `site-home` (HOME), the city pages'
as `site-city` (CITY). Each channel's utm_campaign is its own name, so Play Console's campaign
column and App Store Connect's campaign list read the same names side by side.

Nothing here counts anyone. Each store counts, in aggregate, in its own console, the visits to
its listing that arrived with a channel's tags and the installs that followed; the pages set no
cookie, store nothing, report nothing, and the /get/ pages load nothing from anywhere else.

Google Play reads the tags from the listing address's `referrer` parameter, the three
`utm_` pairs URL-encoded into one value of at most 512 characters, as Google documents it:
    https://play.google.com/store/apps/details?id=com.sample.package&referrer=utm_source%3Dsearch%26utm_medium%3Dcpc%26utm_campaign%3Dsummerpromo
(developer.android.com/games/playgames/user-acquisition, "The referrer string must be URL
encoded, 512 characters or less"). Play Console's acquisition reports break store-listing visits
down by "UTM source" and "UTM campaign", "the value of the utm_source [utm_campaign] URL parameter
in deep links to your store listing" (Play Console Help, "Understand and grow your app's user
base", answer 9859173). They count visits to the listing in the Play Store app; Google says visits
to play.google.com in a browser are not tracked there (answer 6263332), which is why a phone is
sent on (an Android phone with Google Play opens the link in the Play Store app) and a computer
is only shown the badges.

The App Store reads a campaign link, whose form App Store Connect Help gives as
    https://apps.apple.com/app/apple-store/id123456789?pt=123456&ct=test1234&mt=8
(developer.apple.com/help/app-store-connect-analytics/acquisition/campaign-links): `pt` the
provider token, the same for every campaign of one developer account, `ct` the campaign token,
"up to 30 alphanumeric characters and spaces" and some punctuation, and `mt=8` for an app. The
channel's own name is its campaign token, so a channel name is at most 30 characters of a-z, 0-9
and "-", a subset of what Apple allows. The provider token is "app_store_provider_token" in
site/stores.json: only App Store Connect shows it (Analytics > Acquisition > Campaigns, once the
first campaign link is generated there), and Apple says it cannot be made up. Until it is set
every App Store link stays the bare address, because a campaign token without a provider token
counts nothing.

The links are built here from the bare addresses in site/stores.json, which keep their own strict
validation; the JSON-LD on the home pages carries the bare addresses. test_campaigns.py tests the
builders and the validation.
"""
import json
import os
import re
import sys
from urllib.parse import quote, urlencode

SITE = os.path.dirname(os.path.abspath(__file__))

# The site's own placements: the badges in every home page's hero, and the city pages' two.
HOME = "site-home"
CITY = "site-city"

# A channel's name is its address under /get/ and the App Store's campaign token, so it is short,
# lower case and safe in both: Apple allows at most 30 characters.
CHANNEL = re.compile(r"[a-z0-9]+(?:-[a-z0-9]+)*")
CHANNEL_MAX = 30
TAGS = ("utm_source", "utm_medium", "utm_campaign")
# Play Console lists the values as they arrive, case and all, so they are lower case here too and
# need no escaping inside the referrer ("taqwa.world" keeps its dot).
TAG_VALUE = re.compile(r"[a-z0-9]+(?:[._-][a-z0-9]+)*")
REFERRER_MAX = 512
APP_STORE_ID = re.compile(r"https://apps\.apple\.com/app/id(\d+)")
PROVIDER_TOKEN = re.compile(r"\d+")


def problems(campaigns) -> list:
    """What is wrong with a parsed campaigns.json, as sentences; nothing for a good one."""
    if not isinstance(campaigns, dict) or not campaigns:
        return ["expected an object of channels"]
    found = []
    seen = {}
    for channel, tags in campaigns.items():
        if len(channel) > CHANNEL_MAX or not CHANNEL.fullmatch(channel):
            found.append(f"{channel!r}: a channel is 1 to {CHANNEL_MAX} characters of a-z, 0-9 and single hyphens "
                         "(it is the App Store's campaign token and the address /get/<channel>/)")
            continue
        if not isinstance(tags, dict) or set(tags) != set(TAGS):
            found.append(f"{channel}: expected exactly {', '.join(TAGS)}")
            continue
        bad = [key for key in TAGS if not isinstance(tags[key], str) or not TAG_VALUE.fullmatch(tags[key])]
        if bad:
            found.append(f"{channel}: {', '.join(bad)} must be lower-case letters and digits, joined by single . _ or -")
            continue
        if len(referrer(tags)) > REFERRER_MAX:
            found.append(f"{channel}: the encoded referrer is longer than Google Play's {REFERRER_MAX} characters")
        triple = tuple(tags[key] for key in TAGS)
        if triple in seen:
            found.append(f"{channel}: the same three tags as {seen[triple]}, so Google Play could not tell them apart")
        seen.setdefault(triple, channel)
    for placement in (HOME, CITY):
        if placement not in campaigns:
            found.append(f"no {placement} channel: the site's own badges are tagged with it")
    return found


def refuse_duplicates(pairs: list) -> dict:
    """A json.load hook: a channel named twice would silently keep only its last tags."""
    keys = [key for key, _ in pairs]
    twice = sorted({key for key in keys if keys.count(key) > 1})
    if twice:
        raise ValueError(f"named more than once: {', '.join(twice)}")
    return dict(pairs)


def load(path: str = os.path.join(SITE, "campaigns.json")) -> dict:
    """site/campaigns.json, refused outright if anything in it is malformed, as stores.json is."""
    name = os.path.relpath(path, os.path.dirname(SITE))
    try:
        with open(path, encoding="utf-8") as f:
            campaigns = json.load(f, object_pairs_hook=refuse_duplicates)
    except ValueError as error:
        sys.exit(f"{name}: {error}")
    found = problems(campaigns)
    if found:
        sys.exit(f"{name}: " + "; ".join(found))
    return campaigns


def referrer(tags: dict) -> str:
    """The three tags as Google Play's `referrer` value: utm_source=…&utm_medium=…&utm_campaign=…,
    URL-encoded as one parameter value ("=" as %3D, "&" as %26)."""
    return quote(urlencode([(key, tags[key]) for key in TAGS]), safe="")


def google_play_link(address: str, tags: dict) -> str:
    """The listing at `address` (stores.json's, which already carries ?id=) with the channel's tags."""
    return f"{address}{'&' if '?' in address else '?'}referrer={referrer(tags)}"


def app_store_link(address: str, provider_token, channel: str) -> str:
    """Apple's campaign link for the app at `address` (https://apps.apple.com/app/id<n>), the
    channel's name as its campaign token; the bare address while there is no provider token."""
    if not provider_token:
        return address
    app_id = APP_STORE_ID.fullmatch(address).group(1)
    query = urlencode([("pt", provider_token), ("ct", channel), ("mt", "8")])
    return f"https://apps.apple.com/app/apple-store/id{app_id}?{query}"


def links(stores: dict, campaigns: dict, channel) -> dict:
    """Where each store's badge leads for `channel`: None for a store the app is not in yet, and
    the bare addresses for no channel at all (taqwa.world/get/ itself)."""
    if channel is not None and channel not in campaigns:
        raise KeyError(f"site/campaigns.json has no channel {channel!r}")
    play, apple = stores["google_play"], stores["app_store"]
    if channel is None:
        return {"google_play": play, "app_store": apple}
    return {
        "google_play": play and google_play_link(play, campaigns[channel]),
        "app_store": apple and app_store_link(apple, stores.get("app_store_provider_token"), channel),
    }


def redirect_script(to: dict) -> str:
    """The /get/ pages' one script: an iPhone or iPad goes on to the App Store and an Android phone
    to Google Play, replacing the page in the history so Back does not land on it again. iPadOS
    asks for desktop pages and calls itself "Macintosh"; it is told from a Mac by its touch screen.
    Anything else stays on the page and its badges. A store the app is not in leaves its phones
    on the page too. The links are JSON in the script, escaped so no "</" can close it early."""
    apple = json.dumps(to["app_store"]).replace("</", "<\\/")
    play = json.dumps(to["google_play"]).replace("</", "<\\/")
    return ("(function () {\n"
            "  var ua = navigator.userAgent;\n"
            "  var ios = /iPhone|iPad|iPod/.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1);\n"
            f"  var to = ios ? {apple} : /Android/.test(ua) ? {play} : null;\n"
            "  if (to) location.replace(to);\n"
            "})();")
