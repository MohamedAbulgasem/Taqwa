#!/usr/bin/env ruby
# frozen_string_literal: true

# Adds (or repairs) the `TaqwaWidget` WidgetKit app-extension target in iosApp.xcodeproj.
#
# The plan for Task 24 says "added via Xcode". Doing it by hand instead produces a pbxproj diff no
# one can review, so the operation lives here: run it, commit the regenerated project file, and the
# next person can re-derive it. It is idempotent — running it twice leaves the project byte
# identical, which is the property that makes it worth committing.
#
#   ruby tools/add-widget-target.rb
#
# Requires the `xcodeproj` gem.

require 'xcodeproj'

ROOT          = File.expand_path('..', __dir__)
PROJECT_PATH  = File.join(ROOT, 'iosApp', 'iosApp.xcodeproj')
APP_TARGET    = 'iosApp'
EXT_TARGET    = 'TaqwaWidget'
EXT_GROUP_DIR = 'TaqwaWidget'                       # relative to SRCROOT (= iosApp/)
APP_GROUP_ID  = 'group.world.taqwa.ios'
EXT_BUNDLE_ID = '$(BUNDLE_ID).widget'          # from iosApp/Configuration/Config.xcconfig
TEAM_ID       = '$(TEAM_ID)'                   # from iosApp/Configuration/Config.xcconfig, never a literal
DEPLOYMENT    = '16.0'

# Swift sources that belong to the extension, and whether the *app* compiles them too. The views
# are shared so the app's `-taqwaWidgetPreview 1` debug route can render the real widget views;
# the bundle carries `@main` and must never enter the app.
EXT_SOURCES = {
  'TaqwaWidgetBundle.swift'   => { app: false },
  'TaqwaWidgetViews.swift'    => { app: true },
  'TaqwaAyahWidget.swift'     => { app: true },
  'TaqwaWidgetPreviews.swift' => { app: true }
}.freeze

EXT_RESOURCES = ['Info.plist', 'TaqwaWidget.entitlements'].freeze

# The extension links `widgetcore.framework`, NOT `shared.framework`. `shared` is a static
# Kotlin/Native framework carrying all of Compose and Skia, which made this .appex ~63 MB; a
# WidgetKit extension runs under a ~30 MB memory ceiling and iOS jetsams one that exceeds it.
# `:widgetcore` is the same widget model with no Compose anywhere in it.
#
# Kotlin/Native produces `widgetcore.framework` into widgetcore/build/xcode-frameworks/<config>/<sdk>.
# Xcode builds the extension *before* the app that embeds it, so the extension cannot rely on the
# app's own "Compile Kotlin" phase having run — it needs its own, or a clean build fails to link.
KOTLIN_FRAMEWORK = 'widgetcore'
KOTLIN_SCRIPT = %(cd "$SRCROOT/.."\n./gradlew :#{KOTLIN_FRAMEWORK}:embedAndSignAppleFrameworkForXcode\n)
FRAMEWORK_SEARCH_PATH =
  "$(SRCROOT)/../#{KOTLIN_FRAMEWORK}/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)"

project = Xcodeproj::Project.open(PROJECT_PATH)
app = project.targets.find { |t| t.name == APP_TARGET } or abort "No '#{APP_TARGET}' target"

# --- the extension target -----------------------------------------------------------------------

ext = project.targets.find { |t| t.name == EXT_TARGET }
unless ext
  ext = project.new_target(:app_extension, EXT_TARGET, :ios, DEPLOYMENT, nil, :swift)
  puts "created target #{EXT_TARGET}"
end
ext.product_type = 'com.apple.product-type.app-extension'
ext.product_reference.name = "#{EXT_TARGET}.appex"

# --- group + files ------------------------------------------------------------------------------

group = project.main_group.find_subpath(EXT_GROUP_DIR, true)
group.set_source_tree('<group>')
group.set_path(EXT_GROUP_DIR)

def file_ref(group, name)
  group.files.find { |f| f.path == name } || group.new_reference(name)
end

def add_once(phase, ref)
  return if phase.files_references.include?(ref)

  phase.add_file_reference(ref)
end

EXT_SOURCES.each do |name, opts|
  ref = file_ref(group, name)
  add_once(ext.source_build_phase, ref)
  add_once(app.source_build_phase, ref) if opts[:app]
end
EXT_RESOURCES.each { |name| file_ref(group, name) }

# --- frameworks ---------------------------------------------------------------------------------

# `Xcodeproj#add_system_framework` bakes the *installed* SDK version into the path
# (…/iPhoneOS18.0.sdk/…), which then breaks under a different Xcode. SDKROOT-relative references
# are what Xcode itself writes and they follow whichever SDK is building.
def system_framework(project, name)
  path = "System/Library/Frameworks/#{name}.framework"
  group = project.frameworks_group
  group.files.find { |f| f.path == path } || group.new_reference(path, :sdk_root)
end

%w[Foundation WidgetKit SwiftUI].each do |name|
  add_once(ext.frameworks_build_phase, system_framework(project, name))
end

# `new_target` seeds Foundation.framework the baked-path way, so strip those out again — one
# leftover .../iPhoneOS18.0.sdk/... reference is enough to fail the build under a newer Xcode.
ext.frameworks_build_phase.files.to_a.each do |build_file|
  ref = build_file.file_ref
  next unless ref && ref.source_tree == 'DEVELOPER_DIR'

  build_file.remove_from_project
  ref.remove_from_project
end
project.frameworks_group.groups.select { |g| g.children.empty? }.each(&:remove_from_project)

# --- "Compile Kotlin" run-script phase ------------------------------------------------------------

kotlin_phase = ext.shell_script_build_phases.find { |p| p.name == 'Compile Kotlin' }
kotlin_phase ||= ext.new_shell_script_build_phase('Compile Kotlin')
kotlin_phase.shell_path = '/bin/sh'
kotlin_phase.shell_script = KOTLIN_SCRIPT
# Must run before Swift compiles against `import widgetcore`. Xcode builds the extension first, so
# waiting for the app's own copy of this phase would be too late.
ext.build_phases.move(kotlin_phase, 0) unless ext.build_phases.first == kotlin_phase

# --- build settings -------------------------------------------------------------------------------

ext.build_configurations.each do |config|
  s = config.build_settings
  s['PRODUCT_NAME'] = EXT_TARGET
  s['PRODUCT_BUNDLE_IDENTIFIER'] = EXT_BUNDLE_ID
  s['INFOPLIST_FILE'] = "#{EXT_GROUP_DIR}/Info.plist"
  s['CODE_SIGN_ENTITLEMENTS'] = "#{EXT_GROUP_DIR}/#{EXT_TARGET}.entitlements"
  s['CODE_SIGN_STYLE'] = 'Automatic'
  s['DEVELOPMENT_TEAM'] = TEAM_ID
  s['IPHONEOS_DEPLOYMENT_TARGET'] = DEPLOYMENT
  s['SWIFT_VERSION'] = '5.0'
  s['TARGETED_DEVICE_FAMILY'] = '1'
  s['SKIP_INSTALL'] = 'YES'
  s['GENERATE_INFOPLIST_FILE'] = 'NO'
  s['SWIFT_EMIT_LOC_STRINGS'] = 'YES'
  # `widgetcore.framework` is a *static* Kotlin/Native framework, so the extension links its own
  # copy and nothing is embedded twice; the app links `shared` (which re-exports widgetcore).
  s['FRAMEWORK_SEARCH_PATHS'] = ['$(inherited)', FRAMEWORK_SEARCH_PATH]
  s['OTHER_LDFLAGS'] = ['$(inherited)', '-framework', KOTLIN_FRAMEWORK]
  s['LD_RUNPATH_SEARCH_PATHS'] = ['$(inherited)', '@executable_path/Frameworks',
                                  '@executable_path/../../Frameworks']
  # Xcode refuses to build an app-extension product type with this set to NO. Nothing the widget
  # itself calls is outside the extension-safe surface; the Kotlin static framework is linked as
  # object code, which the extension-safety check does not apply to.
  s['APPLICATION_EXTENSION_API_ONLY'] = 'YES'
  s['ENABLE_PREVIEWS'] = 'YES'
  # `TaqwaWidgetViews.swift`/`TaqwaWidgetPreviews.swift` are compiled into BOTH this target and
  # the app, and each needs a different module: `widgetcore` here, `shared` (which re-exports it)
  # in the app. `#if canImport(widgetcore)` cannot make that distinction — Kotlin/Native drops
  # `widgetcore.framework` into the shared BUILT_PRODUCTS_DIR, so it is importable from the app
  # too, and importing it there autolinks a *second* Kotlin/Native runtime into the app binary,
  # which aborts at launch in `+[KotlinBase load]` ("runtime injected twice", KT-42254).
  s['SWIFT_ACTIVE_COMPILATION_CONDITIONS'] = ['$(inherited)', 'TAQWA_WIDGET_EXTENSION']
end

# --- app target: entitlements + embed the extension --------------------------------------------

app.build_configurations.each do |config|
  config.build_settings['CODE_SIGN_ENTITLEMENTS'] = 'iosApp/iosApp.entitlements'
  # The app had accumulated `-framework "shared\n$(inherited)" -framework "shared\n"` — embedded
  # newlines from a hand-edit, which the linker word-splits back into TWO `-framework shared`.
  # Linking a static Kotlin/Native framework twice aborts the process at launch with
  # "runtime assert: runtime injected twice" (KT-42254). Normalise it here so it stays fixed.
  #
  # `-lsqlite3` belongs in this list, not only in the project as Xcode last left it: SQLDelight's
  # iOS driver (SQLiter) is a cinterop binding with no library of its own, so every `sqlite3_*`
  # symbol is unresolved until the system library is linked. Rewriting the setting without it —
  # which this line does, by design — used to leave the app failing to link with a page of
  # "_sqlite3_step, referenced from: …" the first time anyone re-ran this script.
  config.build_settings['OTHER_LDFLAGS'] = ['$(inherited)', '-framework', 'shared', '-lsqlite3']
end
file_ref(project.main_group.find_subpath('iosApp', true), 'iosApp.entitlements')

app.add_dependency(ext) unless app.dependencies.any? { |d| d.target == ext }

embed = app.copy_files_build_phases.find { |p| p.name == 'Embed Foundation Extensions' }
unless embed
  embed = app.new_copy_files_build_phase('Embed Foundation Extensions')
  # 13 == PlugIns. Without this the .appex never reaches Taqwa.app/PlugIns and iOS never sees it.
  embed.dst_subfolder_spec = '13'
  embed.dst_path = ''
end
unless embed.files_references.include?(ext.product_reference)
  build_file = embed.add_file_reference(ext.product_reference)
  build_file.settings = { 'ATTRIBUTES' => ['RemoveHeadersOnCopy'] }
end
# The embed must happen after the app's own binary is linked. `move` (not `push` — `ObjectList`
# does not override `push`, so it would add a UUID without re-registering the object).
app.build_phases.move(embed, app.build_phases.count - 1) unless app.build_phases.last == embed

# --- the App Group entitlement, on both targets --------------------------------------------------

ENTITLEMENTS = {
  File.join(ROOT, 'iosApp', 'iosApp', 'iosApp.entitlements') => APP_GROUP_ID,
  File.join(ROOT, 'iosApp', EXT_GROUP_DIR, "#{EXT_TARGET}.entitlements") => APP_GROUP_ID
}.freeze

ENTITLEMENTS.each do |path, group_id|
  plist = File.exist?(path) ? Xcodeproj::Plist.read_from_path(path) : {}
  groups = plist['com.apple.security.application-groups'] || []
  next if groups.include?(group_id)

  plist['com.apple.security.application-groups'] = groups + [group_id]
  Xcodeproj::Plist.write_to_path(plist, path)
  puts "added #{group_id} to #{path}"
end

project.save
puts "saved #{PROJECT_PATH}"
