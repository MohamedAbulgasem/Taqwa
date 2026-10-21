#!/bin/bash
# Runs the shared and widget suites: the iOS simulator tests and the Android host tests. On a Mac
# the Xcode comes from scripts/xcode.sh: DEVELOPER_DIR when set, else the one xcode-select chose,
# and never one older than Xcode 26.
set -e
cd "$(dirname "$0")/.."
if [ "$(uname)" = Darwin ]; then . scripts/xcode.sh; fi
if [ "$#" -eq 0 ]; then
  # `:widgetcore` holds the widget model the iOS extension links on its own; its tests have to
  # run here or nothing covers them. The Android host tests are named as well as allTests, so they
  # run whether or not allTests counts them under the com.android.kotlin.multiplatform.library plugin.
  set -- :shared:allTests :widgetcore:allTests :shared:testAndroidHostTest :widgetcore:testAndroidHostTest
fi
exec ./gradlew "$@"
