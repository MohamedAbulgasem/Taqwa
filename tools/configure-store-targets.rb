#!/usr/bin/env ruby
# frozen_string_literal: true

# The App Store settings of iosApp.xcodeproj that Xcode's UI would otherwise hand-edit, kept as a
# script for the same reason as add-widget-target.rb: a reviewable, re-runnable pbxproj change.
# Idempotent — a second run leaves the project byte identical.
#
#   ruby tools/configure-store-targets.rb
#
# It does three things:
#   1. adds each target's PrivacyInfo.xcprivacy to its Resources phase (App Store Connect rejects
#      an upload that uses "required reason" APIs without one);
#   2. adds the app's InfoPlist.strings and the widget's Localizable.strings for every language
#      the app speaks (en, ar, fr, tr, id, ur, bn) and registers those languages with the project;
#   3. makes the app iPhone-only for launch. The layouts are phone layouts; an iPad still installs
#      it as an iPhone app. Set TARGETED_DEVICE_FAMILY back to "1,2" once iPad is designed for.
#
# Requires the `xcodeproj` gem.

require 'xcodeproj'

ROOT         = File.expand_path('..', __dir__)
PROJECT_PATH = File.join(ROOT, 'iosApp', 'iosApp.xcodeproj')
APP_TARGET   = 'iosApp'
EXT_TARGET   = 'TaqwaWidget'
LANGUAGES    = %w[en ar fr tr id ur bn].freeze

project = Xcodeproj::Project.open(PROJECT_PATH)
app = project.targets.find { |t| t.name == APP_TARGET } or abort "No '#{APP_TARGET}' target"
ext = project.targets.find { |t| t.name == EXT_TARGET } or abort "No '#{EXT_TARGET}' target"

def file_ref(group, path)
  group.files.find { |f| f.path == path } || group.new_file(path)
end

def add_once(phase, ref)
  phase.add_file_reference(ref) unless phase.files_references.include?(ref)
end

app_group = project.main_group.find_subpath(APP_TARGET, true)
ext_group = project.main_group.find_subpath(EXT_TARGET, true)

# 1. privacy manifests
add_once(app.resources_build_phase, file_ref(app_group, 'PrivacyInfo.xcprivacy'))
add_once(ext.resources_build_phase, file_ref(ext_group, 'PrivacyInfo.xcprivacy'))

# 2. the localised strings: the app's InfoPlist.strings and the widget's Localizable.strings, one
#    variant group each with a file per language, and every language known to the project
def localise(group, phase, name)
  variant = group.children.find do |child|
    child.is_a?(Xcodeproj::Project::Object::PBXVariantGroup) && child.name == name
  end
  variant ||= group.new_variant_group(name)
  LANGUAGES.each do |lang|
    path = "#{lang}.lproj/#{name}"
    next if variant.files.any? { |f| f.path == path }

    ref = variant.new_reference(path)
    ref.name = lang
    ref.last_known_file_type = 'text.plist.strings'
  end
  phase.add_file_reference(variant) unless phase.files_references.include?(variant)
end

localise(app_group, app.resources_build_phase, 'InfoPlist.strings')
localise(ext_group, ext.resources_build_phase, 'Localizable.strings')
LANGUAGES.each { |lang| project.root_object.known_regions << lang unless project.root_object.known_regions.include?(lang) }

# 3. iPhone only
app.build_configurations.each do |config|
  config.build_settings['TARGETED_DEVICE_FAMILY'] = '1'
end

project.save
puts "saved #{PROJECT_PATH}"
