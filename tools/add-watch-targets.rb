#!/usr/bin/env ruby
# frozen_string_literal: true

# Adds (or repairs) the Apple Watch targets in iosApp.xcodeproj (plan 4, Task 4), in
# add-widget-target.rb's pattern: a reviewable script instead of a hand-made pbxproj diff no one
# can review. It is idempotent: a second run leaves the project and the scheme byte identical.
#
#   ruby tools/add-watch-targets.rb
#
# Requires the `xcodeproj` gem, 1.28.1 as installed on the Mac mini on 30 October 2026, from
# rubygems.org, with `gem install --user-install --no-document xcodeproj` (plan 4, the Lead's
# call 2). It loads on Homebrew's Ruby 4.0.7.
#
# What it makes:
#   - TaqwaWatch: a single-target SwiftUI watchOS app, $(BUNDLE_ID).watchkitapp, embedded in
#     Taqwa.app/Watch/ by iosApp's "Embed Watch Content". It embeds its widgets in PlugIns/, and its
#     "Debug samples" phase copies watchcore/vectors/sample-feed-capetown*.json into a Debug build
#     only (the DEBUG launch arguments of later tasks store them);
#   - TaqwaWatchWidgets: its WidgetKit extension, $(BUNDLE_ID).watchkitapp.widgets;
#   - TaqwaWatchUITests: the watch app's UI tests, run by the shared TaqwaWatch scheme's test action
#     (scripts/watch-build.sh test), on the Mac only;
#   - iosApp's "Watch face" phase, which copies iosApp/iosApp/WatchFace/Taqwa.watchface into the app
#     when that file exists (plan 4, Task 10), and removes a copy an earlier build left when it does
#     not;
#   - the iPhone's DEBUG link probe in iosApp's sources (plan 4, Task 5B): WatchHarness.swift and
#     WatchProbe.swift, its rules, both wholly `#if DEBUG`; and Settings' "Add to Apple Watch"
#     (plan 4, Task 10): WatchFaceShare.swift, the hook over ClockKit's face library; and
#     WatchKeyFile.swift (plan 4, Task 11), the watch key's file, marked excluded from backup. iosApp
#     lists its files by hand, so these four are named here; nothing else of iosApp's sources is
#     touched.
# Both watch targets compile every Swift file in iosApp/WatchCore/Sources/WatchCore/ (the Swift
# port of :watchcore; the targets compile its files directly and never depend on the package) and
# in iosApp/WatchShared/, and bundle WatchShared/WatchAssets.xcassets and the three Manrope faces
# the type rule uses, referenced from shared/src/commonMain/composeResources/font/ and never
# copied. Each target's own folder adds its Swift files, its privacy manifest and its seven .lproj
# tables. Files are found on disk, so a later task's new file joins its target when this script is
# run again, and a deleted one leaves it.
#
# The watch links no Kotlin framework (the Lead's call 1), so no target here has a "Compile
# Kotlin" phase, and none names a framework search path.
#
# iosApp's build phases end "Embed Watch Content", "Watch face", "Embed Foundation Extensions":
# add-widget-target.rb keeps "Embed Foundation Extensions" last, so this script creates each of
# its two phases just before it, and never moves a phase once it exists. Each script's rerun then
# leaves the other's work alone.

require 'fileutils'
require 'xcodeproj'

ROOT         = File.expand_path('..', __dir__)
IOS          = File.join(ROOT, 'iosApp')                 # SRCROOT
PROJECT_PATH = File.join(IOS, 'iosApp.xcodeproj')
APP_TARGET   = 'iosApp'
WATCH        = 'TaqwaWatch'
WIDGETS      = 'TaqwaWatchWidgets'
UI_TESTS     = 'TaqwaWatchUITests'
SCHEME       = 'TaqwaWatch'
DEPLOYMENT   = '10.0' # plan 4, question 1: every Apple Watch from Series 4 and the first SE
LANGUAGES    = %w[en ar fr tr id ur bn].freeze
CORE_DIR     = 'WatchCore/Sources/WatchCore'
SHARED_DIR   = 'WatchShared'
FONT_DIR     = '../shared/src/commonMain/composeResources/font'
FONTS        = %w[Manrope-Light.ttf Manrope-SemiBold.ttf Manrope-ExtraBold.ttf].freeze
IPHONE_ENTITLEMENTS = File.join(IOS, 'iosApp', 'iosApp.entitlements')
EMBED_EXTENSIONS = 'Embed Foundation Extensions'
EMBED_WATCH  = 'Embed Watch Content'
WATCH_FACE   = 'Watch face'
DEBUG_SAMPLES = 'Debug samples'
HARNESS_FILES = %w[WatchHarness.swift WatchProbe.swift].freeze
FACE_FILES   = %w[WatchFaceShare.swift].freeze
KEY_FILES    = %w[WatchKeyFile.swift].freeze

DEBUG_SAMPLES_SCRIPT = <<~'SH'
  # The phone's four Cape Town sample feeds, in a Debug build only: the DEBUG launch arguments
  # store them (plan 4, Task 5). A Release build gets none, and loses any an earlier build left
  # (scripts/check-watch-bundle.sh, rule 8).
  DEST="$TARGET_BUILD_DIR/$UNLOCALIZED_RESOURCES_FOLDER_PATH"
  rm -f "$DEST"/sample-feed-capetown*.json
  if [ "$CONFIGURATION" = Debug ]; then
    cp "$SRCROOT/../watchcore/vectors/"sample-feed-capetown*.json "$DEST/"
  fi
SH

WATCH_FACE_SCRIPT = <<~'SH'
  # The Taqwa face Settings offers as "Add to Apple Watch" (plan 4, Task 10). The file is made on a
  # real Apple Watch and is absent until then. A copy an earlier build left goes when the file
  # does, so the row is never offered for a face that is no longer bundled.
  SRC="$SRCROOT/iosApp/WatchFace/Taqwa.watchface"
  DEST="$TARGET_BUILD_DIR/$UNLOCALIZED_RESOURCES_FOLDER_PATH/Taqwa.watchface"
  rm -rf "$DEST"
  if [ -e "$SRC" ]; then
    cp -R "$SRC" "$DEST"
  fi
SH

project = Xcodeproj::Project.open(PROJECT_PATH)
app = project.targets.find { |t| t.name == APP_TARGET } or abort "No '#{APP_TARGET}' target"
app.copy_files_build_phases.find { |p| p.name == EMBED_EXTENSIONS } or
  abort "iosApp has no '#{EMBED_EXTENSIONS}' phase: run tools/add-widget-target.rb first"
XCCONFIG_PATH = File.join(IOS, 'Configuration', 'Config.xcconfig')
xcconfig = project.files.find { |f| f.real_path.to_s == XCCONFIG_PATH } or
  abort "No reference to #{XCCONFIG_PATH}: TEAM_ID and BUNDLE_ID come from it"

# --- helpers ------------------------------------------------------------------------------------

def add_once(phase, ref)
  phase.add_file_reference(ref) unless phase.files_references.include?(ref)
end

# A top-level group whose path is `path` (relative to SRCROOT), made when missing.
def group_at(project, path, name = nil)
  group = project.main_group.groups.find { |g| g.path == path }
  group ||= project.main_group.new_group(name, path)
  group.name = name
  group.set_source_tree('<group>')
  group
end

def file_ref(group, path)
  group.files.find { |f| f.path == path } || group.new_reference(path)
end

# Removes the references (and so their build files) whose files are gone from disk.
def prune(group)
  group.files.each do |ref|
    ref.remove_from_project unless File.exist?(File.join(group.real_path, ref.path))
  end
end

# The group's Swift files on disk, sorted, as references.
def swift_refs(group)
  Dir.children(group.real_path).select { |name| name.end_with?('.swift') }.sort.map { |name| file_ref(group, name) }
end

# A localised table: one variant group with a file per language, every one on disk.
def localised(group, name)
  variant = group.children.find do |child|
    child.is_a?(Xcodeproj::Project::Object::PBXVariantGroup) && child.name == name
  end
  variant ||= group.new_variant_group(name)
  LANGUAGES.each do |lang|
    path = "#{lang}.lproj/#{name}"
    abort "missing #{File.join(group.real_path, path)}" unless File.exist?(File.join(group.real_path, path))
    next if variant.files.any? { |f| f.path == path }

    ref = variant.new_reference(path)
    ref.name = lang
    ref.last_known_file_type = 'text.plist.strings'
  end
  variant
end

def target(project, name, type)
  found = project.targets.find { |t| t.name == name }
  return found if found

  puts "created target #{name}"
  project.new_target(type, name, :watchos, DEPLOYMENT, nil, :swift)
end

# `new_target` seeds Foundation.framework by the installed SDK's path (…/WatchOS27.1.sdk/…), which
# breaks under another Xcode; Swift links what it imports, so nothing is listed instead.
def drop_baked_frameworks(project, target)
  target.frameworks_build_phase.files.to_a.each do |build_file|
    ref = build_file.file_ref
    build_file.remove_from_project
    ref.remove_from_project if ref && ref.source_tree == 'DEVELOPER_DIR' && ref.build_files.empty?
  end
  project.frameworks_group.groups.select { |g| g.children.empty? }.each(&:remove_from_project)
end

# Every build setting the target has, written out in full on each run, so the list is the review.
def settings(target, xcconfig, values)
  target.build_configurations.each do |config|
    config.base_configuration_reference = xcconfig
    config.build_settings = values.dup
  end
end

def phase_order(target, phases)
  phases.each_with_index { |phase, index| target.build_phases.move(phase, index) }
end

def embed(phase, product)
  return if phase.files_references.include?(product)

  phase.add_file_reference(product).settings = { 'ATTRIBUTES' => ['RemoveHeadersOnCopy'] }
end

# --- the groups and their files -----------------------------------------------------------------

core_group    = group_at(project, CORE_DIR, 'WatchCore')
shared_group  = group_at(project, SHARED_DIR)
watch_group   = group_at(project, WATCH)
widgets_group = group_at(project, WIDGETS)
tests_group   = group_at(project, UI_TESTS)
font_group    = group_at(project, FONT_DIR, 'Manrope (shared)')
[core_group, shared_group, watch_group, widgets_group, tests_group, font_group].each { |g| prune(g) }

core_sources    = swift_refs(core_group)
shared_sources  = swift_refs(shared_group)
abort "no Swift sources in iosApp/#{CORE_DIR}" if core_sources.empty?
watch_assets    = file_ref(shared_group, 'WatchAssets.xcassets')
fonts = FONTS.map do |name|
  abort "missing #{File.join(font_group.real_path, name)}" unless File.exist?(File.join(font_group.real_path, name))
  file_ref(font_group, name)
end

# --- the targets --------------------------------------------------------------------------------

watch    = target(project, WATCH, :application)
widgets  = target(project, WIDGETS, :app_extension)
ui_tests = target(project, UI_TESTS, :ui_test_bundle)
watch.product_reference.name = "#{WATCH}.app"
widgets.product_reference.name = "#{WIDGETS}.appex"
ui_tests.product_reference.name = "#{UI_TESTS}.xctest"
[watch, widgets, ui_tests].each { |t| drop_baked_frameworks(project, t) }

WATCH_COMMON = {
  'CODE_SIGN_STYLE' => 'Automatic',
  'DEVELOPMENT_TEAM' => '$(TEAM_ID)', # from iosApp/Configuration/Config.xcconfig, never a literal
  'SDKROOT' => 'watchos',
  'SWIFT_VERSION' => '5.0',
  'TARGETED_DEVICE_FAMILY' => '4',
  'WATCHOS_DEPLOYMENT_TARGET' => DEPLOYMENT
}.freeze

settings(watch, xcconfig, WATCH_COMMON.merge(
  'ASSETCATALOG_COMPILER_APPICON_NAME' => 'AppIcon',
  'CODE_SIGN_ENTITLEMENTS' => "#{WATCH}/#{WATCH}.entitlements",
  'ENABLE_PREVIEWS' => 'YES',
  'GENERATE_INFOPLIST_FILE' => 'NO',
  'INFOPLIST_FILE' => "#{WATCH}/Info.plist",
  'LD_RUNPATH_SEARCH_PATHS' => ['$(inherited)', '@executable_path/Frameworks'],
  'PRODUCT_BUNDLE_IDENTIFIER' => '$(BUNDLE_ID).watchkitapp',
  'PRODUCT_NAME' => WATCH,
  # Embedded in the iPhone app; an archive installs it there and nowhere else.
  'SKIP_INSTALL' => 'YES',
  'SWIFT_EMIT_LOC_STRINGS' => 'YES'
))
settings(widgets, xcconfig, WATCH_COMMON.merge(
  'APPLICATION_EXTENSION_API_ONLY' => 'YES',
  'CODE_SIGN_ENTITLEMENTS' => "#{WIDGETS}/#{WIDGETS}.entitlements",
  'ENABLE_PREVIEWS' => 'YES',
  'GENERATE_INFOPLIST_FILE' => 'NO',
  'INFOPLIST_FILE' => "#{WIDGETS}/Info.plist",
  'LD_RUNPATH_SEARCH_PATHS' => ['$(inherited)', '@executable_path/Frameworks', '@executable_path/../../Frameworks'],
  'PRODUCT_BUNDLE_IDENTIFIER' => '$(BUNDLE_ID).watchkitapp.widgets',
  'PRODUCT_NAME' => WIDGETS,
  'SKIP_INSTALL' => 'YES',
  'SWIFT_EMIT_LOC_STRINGS' => 'YES'
))
settings(ui_tests, xcconfig, WATCH_COMMON.merge(
  'GENERATE_INFOPLIST_FILE' => 'YES',
  'PRODUCT_BUNDLE_IDENTIFIER' => '$(BUNDLE_ID).watchkitapp.uitests',
  'PRODUCT_NAME' => UI_TESTS,
  'TEST_TARGET_NAME' => WATCH
))

# Sources: the core and the shared views in both watch targets, each target's own folder in it.
(core_sources + shared_sources + swift_refs(watch_group)).each { |ref| add_once(watch.source_build_phase, ref) }
(core_sources + shared_sources + swift_refs(widgets_group)).each { |ref| add_once(widgets.source_build_phase, ref) }
swift_refs(tests_group).each { |ref| add_once(ui_tests.source_build_phase, ref) }

# Resources.
[
  file_ref(watch_group, 'Assets.xcassets'), watch_assets, file_ref(watch_group, 'PrivacyInfo.xcprivacy'),
  localised(watch_group, 'InfoPlist.strings'), localised(watch_group, 'Localizable.strings'), *fonts
].each { |ref| add_once(watch.resources_build_phase, ref) }
[
  watch_assets, file_ref(widgets_group, 'PrivacyInfo.xcprivacy'),
  localised(widgets_group, 'InfoPlist.strings'), localised(widgets_group, 'Localizable.strings'), *fonts
].each { |ref| add_once(widgets.resources_build_phase, ref) }
# In the navigator only: the build reads them through INFOPLIST_FILE and CODE_SIGN_ENTITLEMENTS.
%w[Info.plist TaqwaWatch.entitlements].each { |name| file_ref(watch_group, name) }
%w[Info.plist TaqwaWatchWidgets.entitlements].each { |name| file_ref(widgets_group, name) }

# The watch app embeds its widgets, and holds the debug samples.
embed_widgets = watch.copy_files_build_phases.find { |p| p.name == EMBED_EXTENSIONS } ||
                watch.new_copy_files_build_phase(EMBED_EXTENSIONS)
embed_widgets.dst_subfolder_spec = '13' # PlugIns
embed_widgets.dst_path = ''
embed(embed_widgets, widgets.product_reference)
samples = watch.shell_script_build_phases.find { |p| p.name == DEBUG_SAMPLES } ||
          watch.new_shell_script_build_phase(DEBUG_SAMPLES)
samples.shell_path = '/bin/sh'
samples.shell_script = DEBUG_SAMPLES_SCRIPT
samples.always_out_of_date = '1'
phase_order(watch, [watch.source_build_phase, watch.frameworks_build_phase, watch.resources_build_phase,
                    samples, embed_widgets])

watch.add_dependency(widgets) unless watch.dependencies.any? { |d| d.target == widgets }
ui_tests.add_dependency(watch) unless ui_tests.dependencies.any? { |d| d.target == watch }

# --- the iPhone app: embed the watch app, and the face --------------------------------------------

app.add_dependency(watch) unless app.dependencies.any? { |d| d.target == watch }

# Creates the named phase just before "Embed Foundation Extensions"; never moves it once it exists.
def before_extensions(app, existing, make)
  return existing if existing

  phase = make.call
  extensions = app.copy_files_build_phases.find { |p| p.name == EMBED_EXTENSIONS }
  app.build_phases.move(phase, app.build_phases.index(extensions))
  phase
end

embed_watch = before_extensions(app, app.copy_files_build_phases.find { |p| p.name == EMBED_WATCH },
                                -> { app.new_copy_files_build_phase(EMBED_WATCH) })
embed_watch.dst_subfolder_spec = '16' # the products directory, so dst_path can name Taqwa.app/Watch
embed_watch.dst_path = '$(CONTENTS_FOLDER_PATH)/Watch'
embed(embed_watch, watch.product_reference)

face = before_extensions(app, app.shell_script_build_phases.find { |p| p.name == WATCH_FACE },
                         -> { app.new_shell_script_build_phase(WATCH_FACE) })
face.shell_path = '/bin/sh'
face.shell_script = WATCH_FACE_SCRIPT
face.always_out_of_date = '1'

# The iPhone's DEBUG link probe (plan 4, Task 5B), its "Add to Apple Watch" (plan 4, Task 10) and
# the watch key's file (plan 4, Task 11), in iosApp's own group (its path is iosApp).
app_group = project.main_group.groups.find { |g| g.path == APP_TARGET } or abort "No '#{APP_TARGET}' group"
(HARNESS_FILES + FACE_FILES + KEY_FILES).each do |name|
  abort "missing #{File.join(app_group.real_path, name)}" unless File.exist?(File.join(app_group.real_path, name))
  add_once(app.source_build_phase, file_ref(app_group, name))
end

# --- the app group, on both watch targets: the iPhone's own (the Lead's call 3) ------------------

groups = Xcodeproj::Plist.read_from_path(IPHONE_ENTITLEMENTS)['com.apple.security.application-groups']
abort "#{IPHONE_ENTITLEMENTS} must name exactly one app group" unless groups.is_a?(Array) && groups.size == 1
wanted = { 'com.apple.security.application-groups' => groups }
[File.join(IOS, WATCH, "#{WATCH}.entitlements"), File.join(IOS, WIDGETS, "#{WIDGETS}.entitlements")].each do |path|
  next if File.exist?(path) && Xcodeproj::Plist.read_from_path(path) == wanted

  Xcodeproj::Plist.write_to_path(wanted, path)
  puts "wrote #{path}"
end

project.save
puts "saved #{PROJECT_PATH}"

# --- the shared scheme: build and run the watch app, and test it with the UI tests ---------------

scheme = Xcodeproj::XCScheme.new
scheme.configure_with_targets(watch, ui_tests, launch_target: true)
# The gem's default lets the debugger simulate a location. The watch app never asks for one
# (plan 4's Global Constraints), so its scheme offers none either.
scheme.launch_action.allow_location_simulation = false
scheme_path = File.join(Xcodeproj::XCScheme.shared_data_dir(PROJECT_PATH), "#{SCHEME}.xcscheme")
unless File.exist?(scheme_path) && File.read(scheme_path) == scheme.to_s
  FileUtils.mkdir_p(File.dirname(scheme_path))
  File.write(scheme_path, scheme.to_s)
  puts "saved #{scheme_path}"
end
