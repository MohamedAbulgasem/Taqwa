#!/bin/bash
# Runs the shared, widget and watch suites (the iOS simulator tests and the Android host tests) and
# the Wear OS app's JVM tests, and on a Mac, before them, the Apple Watch's Swift core against the
# watch vectors (swift test). On a Mac the Xcode comes from scripts/xcode.sh: DEVELOPER_DIR when
# set, else the one xcode-select chose, and never one older than Xcode 26.
set -e
cd "$(dirname "$0")/.."
# First, and without Gradle: the version is the same in every file that carries it (the phone, the
# Galaxy watch app, the face, the iOS plists, the version strings), and bump-version.sh, tried on a
# scratch copy, still writes every one of them (its lines are shown only when it fails); then the
# privacy lines and the store copy (below).
bash scripts/check-versions.sh
if ! bump_test=$(bash scripts/test-bump-version.sh 2>&1); then echo "$bump_test"; exit 1; fi
echo "test-bump-version.sh: PASS"
# The privacy lines (plan 5 Task 9): the gate's own tests, then every line of plan 2's privacy
# report, as ruled or as Mohamed approved it (M3, 31 October 2026), in each of its languages, with
# the policies' dates and plan 2's sweep. Since M3 this is the cut gate's own strict run: a row
# still awaiting his approval fails it. A failure shows the whole output; a pass, its last line.
if ! privacy_test=$(python3 tools/test_privacy_check.py 2>&1); then echo "$privacy_test"; exit 1; fi
echo "test_privacy_check.py: PASS"
if ! privacy=$(python3 tools/privacy-check.py 2>&1); then echo "$privacy"; exit 1; fi
echo "${privacy##*$'\n'}"
# The store copy (plan 5 Task 10): the check's own tests, then every listing against the stores'
# limits and each store's own watch, the App Store Connect notes en.md points to, and the release
# notes, with the newest code's two files no older than androidApp's code. A failure shows the
# whole output; a pass, its last line.
if ! listing_test=$(python3 tools/test_store_listing_check.py 2>&1); then echo "$listing_test"; exit 1; fi
echo "test_store_listing_check.py: PASS"
if ! listing=$(python3 tools/store-listing-check.py 2>&1); then echo "$listing"; exit 1; fi
echo "${listing##*$'\n'}"
if [ "$(uname)" = Darwin ]; then . scripts/xcode.sh; fi
if [ "$#" -eq 0 ] && [ "$(uname)" = Darwin ]; then
  # The Apple Watch's core, the Swift port of :watchcore, against the vectors Kotlin writes in
  # watchcore/vectors/: seconds, on the Mac, with no simulator and no Gradle. It runs first, and a
  # failure stops the script (set -e), so a rule changed in Kotlin without the port fails here.
  swift test --package-path iosApp/WatchCore
fi
if [ "$#" -eq 0 ]; then
  # The website's timetable generator, tools/timetables, is a build of its own. It compiles the
  # app's prayer, Qibla and Hijri files by path from :shared, :widgetcore and :watchcore, so a file
  # moved between modules can break it with the app still green, and the Pages workflow fails only
  # once the move reaches main (P3.2's move of QiblaAngles to :watchcore did, Ruling 63). The whole
  # run compiles it here, its tests too, without running them: they and the generate step stay the
  # Pages workflow's, and each integration's with site/build.py --check.
  ./gradlew -p tools/timetables compileKotlinJvm compileTestKotlinJvm
fi
if [ "$#" -eq 0 ]; then
  # `:widgetcore` holds the widget model the iOS extension links on its own, and `:watchcore` the
  # watch feed and its rules; their tests have to run here or nothing covers them. The Android
  # host tests are named as well as allTests, so they run whether or not allTests counts them
  # under the com.android.kotlin.multiplatform.library plugin. `:wearApp`, the Wear OS app, has
  # JVM unit tests only, and its debug variant's are the ones named: SampleFeedTest tests the
  # sample feed that only the debug build carries (src/debug), so no other variant compiles it, and
  # the debug variant is the one that runs every test.
  set -- :shared:allTests :widgetcore:allTests :watchcore:allTests \
    :shared:testAndroidHostTest :widgetcore:testAndroidHostTest :watchcore:testAndroidHostTest \
    :wearApp:testDebugUnitTest
fi
exec ./gradlew "$@"
