"""Tests of site/campaigns.py and the stores' campaign tags: the two stores' link builders against
the forms Google and Apple document, the validation that refuses a malformed campaigns.json or
stores.json, the badges' tagged addresses, and the /get/ pages' script, run in Node where there is
one (skipped where there is not). Standard library only; the Pages workflow runs them before the
build, and so can anyone:

    python3 site/test_campaigns.py
"""
import html
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from urllib.parse import parse_qs, urlsplit

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import campaigns  # noqa: E402
import stores  # noqa: E402

SITE = os.path.dirname(os.path.abspath(__file__))
PLAY = "https://play.google.com/store/apps/details?id=world.taqwa.app"
APPLE = "https://apps.apple.com/app/id6814975544"
POSTER = {"utm_source": "cape-town-masjids", "utm_medium": "print", "utm_campaign": "poster"}


def with_placements(extra: dict) -> dict:
    """A campaigns.json that has the site's own two channels, plus `extra`."""
    return {campaigns.HOME: {"utm_source": "taqwa.world", "utm_medium": "website", "utm_campaign": "site-home"},
            campaigns.CITY: {"utm_source": "taqwa.world", "utm_medium": "website", "utm_campaign": "site-city"},
            **extra}


class GooglePlayLinkTest(unittest.TestCase):
    def test_googles_documented_example_comes_out_exactly(self):
        # developer.android.com/games/playgames/user-acquisition
        self.assertEqual(
            "https://play.google.com/store/apps/details?id=com.sample.package"
            "&referrer=utm_source%3Dsearch%26utm_medium%3Dcpc%26utm_campaign%3Dsummerpromo",
            campaigns.google_play_link("https://play.google.com/store/apps/details?id=com.sample.package",
                                       {"utm_source": "search", "utm_medium": "cpc", "utm_campaign": "summerpromo"}))

    def test_the_referrer_is_one_parameter_that_decodes_to_the_three_tags(self):
        link = campaigns.google_play_link(PLAY, POSTER)
        query = parse_qs(urlsplit(link).query)
        self.assertEqual(["world.taqwa.app"], query["id"])
        self.assertEqual(["referrer", "id"], sorted(query, reverse=True))
        self.assertEqual({key: [value] for key, value in POSTER.items()}, parse_qs(query["referrer"][0]))
        self.assertNotIn("&utm_", link)  # the inner "&" is encoded, so Play reads one referrer

    def test_a_dot_in_a_tag_stays_a_dot(self):
        link = campaigns.google_play_link(PLAY, {"utm_source": "taqwa.world", "utm_medium": "website", "utm_campaign": "site-home"})
        self.assertIn("utm_source%3Dtaqwa.world%26", link)


class AppStoreLinkTest(unittest.TestCase):
    def test_apples_documented_example_comes_out_exactly(self):
        # developer.apple.com/help/app-store-connect-analytics/acquisition/campaign-links
        self.assertEqual("https://apps.apple.com/app/apple-store/id123456789?pt=123456&ct=test1234&mt=8",
                         campaigns.app_store_link("https://apps.apple.com/app/id123456789", "123456", "test1234"))

    def test_without_a_provider_token_the_link_is_the_bare_address(self):
        for token in (None, ""):
            self.assertEqual(APPLE, campaigns.app_store_link(APPLE, token, "poster"))


class LinksTest(unittest.TestCase):
    def test_a_channel_tags_both_stores(self):
        to = campaigns.links({"google_play": PLAY, "app_store": APPLE, "app_store_provider_token": "118491960"},
                             with_placements({"poster": POSTER}), "poster")
        self.assertEqual(campaigns.google_play_link(PLAY, POSTER), to["google_play"])
        self.assertEqual("https://apps.apple.com/app/apple-store/id6814975544?pt=118491960&ct=poster&mt=8", to["app_store"])

    def test_the_app_store_stays_bare_until_the_provider_token_is_set(self):
        for live in ({"google_play": PLAY, "app_store": APPLE}, {"google_play": PLAY, "app_store": APPLE, "app_store_provider_token": None}):
            self.assertEqual(APPLE, campaigns.links(live, with_placements({"poster": POSTER}), "poster")["app_store"])

    def test_no_channel_is_the_bare_addresses(self):
        live = {"google_play": PLAY, "app_store": APPLE, "app_store_provider_token": "118491960"}
        self.assertEqual({"google_play": PLAY, "app_store": APPLE}, campaigns.links(live, with_placements({}), None))

    def test_a_store_the_app_is_not_in_has_no_link(self):
        to = campaigns.links({"google_play": None, "app_store": APPLE}, with_placements({"poster": POSTER}), "poster")
        self.assertIsNone(to["google_play"])
        self.assertEqual(APPLE, to["app_store"])

    def test_a_channel_not_in_the_file_is_refused(self):
        with self.assertRaises(KeyError):
            campaigns.links({"google_play": PLAY, "app_store": APPLE}, with_placements({}), "posters")


class CampaignsFileTest(unittest.TestCase):
    def refused(self, extra: dict, why: str) -> None:
        found = campaigns.problems(with_placements(extra))
        self.assertTrue(any(why in message for message in found), found)

    def test_the_sites_file_is_good_and_has_the_sites_own_placements(self):
        real = campaigns.load()
        self.assertEqual([], campaigns.problems(real))
        self.assertIn(campaigns.HOME, real)
        self.assertIn(campaigns.CITY, real)
        for channel in real:
            self.assertLessEqual(len(channel), 30, channel)  # Apple's campaign token limit

    def test_a_channel_name_apple_or_an_address_could_not_carry_is_refused(self):
        for bad in ("Poster", "poster 2", "-poster", "poster-", "post--er", "poster/1", "a" * 31, ""):
            self.refused({bad: POSTER}, "a channel is 1 to 30 characters")
        self.assertEqual([], campaigns.problems(with_placements({"a" * 30: POSTER})))

    def test_a_channel_without_exactly_the_three_tags_is_refused(self):
        self.refused({"poster": {"utm_source": "x", "utm_medium": "print"}}, "expected exactly")
        self.refused({"poster": {**POSTER, "utm_term": "x"}}, "expected exactly")
        self.refused({"poster": "cape-town-masjids"}, "expected exactly")

    def test_a_tag_that_is_not_lower_case_letters_and_digits_is_refused(self):
        for bad in ("Print", "pr int", "print&x=1", "", "print.", 7, None):
            self.refused({"poster": {**POSTER, "utm_medium": bad}}, "must be lower-case")

    def test_two_channels_with_the_same_tags_are_refused(self):
        self.refused({"poster": POSTER, "poster-2": dict(POSTER)}, "the same three tags as poster")

    def test_a_referrer_longer_than_google_allows_is_refused(self):
        self.refused({"poster": {"utm_source": "a" * 200, "utm_medium": "b" * 200, "utm_campaign": "c" * 200}}, "512")

    def test_the_sites_own_placements_are_required(self):
        found = campaigns.problems({"poster": POSTER})
        self.assertTrue(any(campaigns.HOME in message for message in found), found)
        self.assertTrue(any(campaigns.CITY in message for message in found), found)

    def test_something_that_is_not_an_object_of_channels_is_refused(self):
        for bad in ([], {}, "poster"):
            self.assertEqual(["expected an object of channels"], campaigns.problems(bad))

    def test_a_channel_named_twice_in_the_file_is_refused(self):
        text = '{"site-home": %s, "site-city": %s, "poster": %s, "poster": %s}' % (
            json.dumps({"utm_source": "taqwa.world", "utm_medium": "website", "utm_campaign": "site-home"}),
            json.dumps({"utm_source": "taqwa.world", "utm_medium": "website", "utm_campaign": "site-city"}),
            json.dumps(POSTER), json.dumps({**POSTER, "utm_campaign": "poster-2"}))
        with tempfile.TemporaryDirectory() as tmp:
            path = os.path.join(tmp, "campaigns.json")
            with open(path, "w", encoding="utf-8") as f:
                f.write(text)
            with self.assertRaises(SystemExit) as refusal:
                campaigns.load(path)
        self.assertIn("named more than once: poster", str(refusal.exception))


class StoresFileTest(unittest.TestCase):
    GOOD = {"google_play": PLAY, "app_store": APPLE, "exodus": None}

    def test_the_provider_token_is_optional_and_digits_when_set(self):
        for good in ({}, {"app_store_provider_token": None}, {"app_store_provider_token": "118491960"}):
            self.assertEqual([], stores.problems({**self.GOOD, **good}), good)
        for bad in ("118 491", "pt=118491960", "", "12a", 118491960):
            found = stores.problems({**self.GOOD, "app_store_provider_token": bad})
            self.assertTrue(any("a string of digits" in message for message in found), (bad, found))

    def test_the_addresses_stay_bare(self):
        # A tagged link is built from campaigns.json; stores.json never holds one.
        for key, tagged in (("google_play", campaigns.google_play_link(PLAY, POSTER)),
                            ("app_store", "https://apps.apple.com/app/apple-store/id6814975544?pt=1&ct=poster&mt=8")):
            found = stores.problems({**self.GOOD, key: tagged})
            self.assertTrue(any(f"is not a {key} address" in message for message in found), found)

    def test_an_unknown_key_is_refused(self):
        self.assertTrue(stores.problems({**self.GOOD, "app_store_campaign": "poster"}))


class BadgesTest(unittest.TestCase):
    def setUp(self):
        with open(os.path.join(SITE, "pages", "en", "meta.json"), encoding="utf-8") as f:
            self.cfg = json.load(f)

    def hrefs(self, channel) -> list:
        block = stores.block(self.cfg, "en", beta_anchor=False, channel=channel)
        return [html.unescape(href) for href in re.findall(r'<a class="badge" href="([^"]*)"', block)]

    def test_each_placements_badges_carry_its_own_tags(self):
        for channel in (campaigns.HOME, campaigns.CITY, "poster"):
            to = campaigns.links(stores.STORES, stores.CAMPAIGNS, channel)
            self.assertEqual([to[s] for s in ("google_play", "app_store") if to[s]], self.hrefs(channel), channel)

    def test_an_ampersand_in_a_badges_address_is_escaped(self):
        block = stores.block(self.cfg, "en", beta_anchor=False, channel=campaigns.HOME)
        for href in re.findall(r'href="([^"]*)"', block):
            self.assertNotRegex(href, r"&(?!amp;)", href)

    def test_without_a_channel_the_badges_lead_to_the_bare_addresses(self):
        live = [stores.STORES[s] for s in ("google_play", "app_store") if stores.STORES[s]]
        self.assertEqual(live, self.hrefs(None))


NODE = shutil.which("node")

# Runs the script once per case in a sandbox whose navigator is the case's and whose location
# records where the page would have gone.
HARNESS = r"""
const vm = require("vm");
const input = JSON.parse(require("fs").readFileSync(0, "utf8"));
const out = input.cases.map(c => {
  let went = null;
  vm.runInNewContext(input.script, {
    navigator: { userAgent: c.ua, maxTouchPoints: c.touch },
    location: { replace: url => { went = url; } },
  });
  return went;
});
process.stdout.write(JSON.stringify(out));
"""

IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.6 Mobile/15E148 Safari/604.1"
IPAD = "Mozilla/5.0 (iPad; CPU OS 16_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1"
# iPadOS 13 and later ask for desktop pages: Safari on an iPad says what Safari on a Mac says.
MAC = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.6 Safari/605.1.15"
ANDROID = "Mozilla/5.0 (Linux; Android 15; SM-S911B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"
WINDOWS = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"


@unittest.skipUnless(NODE, "no Node to run the page script in")
class RedirectScriptTest(unittest.TestCase):
    TO = {"app_store": "https://apps.apple.com/app/apple-store/id6814975544?pt=1&ct=poster&mt=8",
          "google_play": campaigns.google_play_link(PLAY, POSTER)}

    def went(self, to: dict, *cases) -> list:
        run = subprocess.run([NODE, "-e", HARNESS], capture_output=True, text=True, timeout=30,
                             input=json.dumps({"script": campaigns.redirect_script(to),
                                               "cases": [{"ua": ua, "touch": touch} for ua, touch in cases]}))
        self.assertEqual(0, run.returncode, run.stderr)
        return json.loads(run.stdout)

    def test_each_phone_goes_to_its_own_store_and_a_computer_stays(self):
        apple, play = self.TO["app_store"], self.TO["google_play"]
        self.assertEqual([apple, apple, apple, play, None, None],
                         self.went(self.TO, (IPHONE, 5), (IPAD, 5), (MAC, 5), (ANDROID, 5), (MAC, 0), (WINDOWS, 0)))

    def test_a_store_the_app_is_not_in_keeps_its_phones_on_the_page(self):
        self.assertEqual([None, self.TO["app_store"]],
                         self.went({**self.TO, "google_play": None}, (ANDROID, 5), (IPHONE, 5)))

    def test_an_address_cannot_close_the_script(self):
        script = campaigns.redirect_script({"app_store": "https://example.com/</script><b>", "google_play": None})
        self.assertNotIn("</script>", script)


if __name__ == "__main__":
    unittest.main()
