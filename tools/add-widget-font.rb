#!/usr/bin/env ruby
# frozen_string_literal: true

# Adds the Uthmani Hafs face and the extension's own `Localizable.strings` to the `TaqwaWidget`
# target (design spec §7).
#
# Companion to `tools/add-widget-target.rb`, which owns the target itself and its Swift sources.
# This one owns the extension's *resources*, for the same reason: a hand-edited pbxproj is a diff
# no one can review. It is idempotent — running it twice leaves the project byte identical.
#
#   ruby tools/add-widget-font.rb
#
# Requires the `xcodeproj` gem.
#
# What it does:
#   1. copies `uthmanic_hafs.ttf` out of the Compose resources into `iosApp/TaqwaWidget/Resources/`
#      (the extension links `:widgetcore`, not `:shared`, so it cannot reach Compose's own copy);
#   2. adds that file and the `en`/`ar` `Localizable.strings` variant group to the extension's
#      "Copy Bundle Resources" phase and to its group in the navigator;
#   3. puts `ar` into the project's `knownRegions`, without which Xcode drops `ar.lproj` from the
#      built product and every Arabic string falls back to English.
#
# `UIAppFonts` in `TaqwaWidget/Info.plist` is what actually registers the face with the extension
# at launch; this script only makes sure the file is in the bundle for it to name.

require 'fileutils'
require 'xcodeproj'

ROOT         = File.expand_path('..', __dir__)
PROJECT_PATH = File.join(ROOT, 'iosApp', 'iosApp.xcodeproj')
EXT_TARGET   = 'TaqwaWidget'
EXT_GROUP_DIR = 'TaqwaWidget' # relative to SRCROOT (= iosApp/)

FONT_FILE   = 'uthmanic_hafs.ttf'
FONT_SOURCE = File.join(ROOT, 'shared', 'src', 'commonMain', 'composeResources', 'font', FONT_FILE)
FONT_DEST   = File.join(ROOT, 'iosApp', EXT_GROUP_DIR, 'Resources', FONT_FILE)

STRINGS_FILE = 'Localizable.strings'
LOCALES      = %w[en ar].freeze

# --- 1. the font file ---------------------------------------------------------------------------

abort "missing #{FONT_SOURCE}" unless File.exist?(FONT_SOURCE)
FileUtils.mkdir_p(File.dirname(FONT_DEST))
if !File.exist?(FONT_DEST) || !FileUtils.identical?(FONT_SOURCE, FONT_DEST)
  FileUtils.cp(FONT_SOURCE, FONT_DEST)
  puts "copied #{FONT_FILE} -> iosApp/#{EXT_GROUP_DIR}/Resources/"
end

LOCALES.each do |locale|
  path = File.join(ROOT, 'iosApp', EXT_GROUP_DIR, "#{locale}.lproj", STRINGS_FILE)
  abort "missing #{path}" unless File.exist?(path)
end

# --- 2. the project -------------------------------------------------------------------------------

project = Xcodeproj::Project.open(PROJECT_PATH)
ext = project.targets.find { |t| t.name == EXT_TARGET } or
  abort "No '#{EXT_TARGET}' target — run tools/add-widget-target.rb first"

group = project.main_group.find_subpath(EXT_GROUP_DIR, true)

def add_once(phase, ref)
  return if phase.files_references.include?(ref)

  phase.add_file_reference(ref)
end

# The font, in its own `Resources/` subgroup so the navigator mirrors the folder on disk.
resources_group = group.find_subpath('Resources', true)
resources_group.set_source_tree('<group>')
resources_group.set_path('Resources')
font_ref = resources_group.files.find { |f| f.path == FONT_FILE } || resources_group.new_reference(FONT_FILE)
add_once(ext.resources_build_phase, font_ref)

# The strings, as a variant group: that is the shape Xcode gives a localised file, and it is what
# makes the build system emit `en.lproj/` and `ar.lproj/` into the .appex rather than one flat file.
variant = group.children.find { |c| c.is_a?(Xcodeproj::Project::Object::PBXVariantGroup) && c.name == STRINGS_FILE }
variant ||= group.new_variant_group(STRINGS_FILE)
LOCALES.each do |locale|
  next if variant.children.any? { |c| c.name == locale }

  ref = variant.new_reference("#{locale}.lproj/#{STRINGS_FILE}")
  # Xcode names the child by its *locale*, not by the file, and uses the name to decide which
  # .lproj the copy lands in.
  ref.name = locale
  ref.last_known_file_type = 'text.plist.strings'
end
add_once(ext.resources_build_phase, variant)

# Without this Xcode treats `ar` as an unknown region and silently leaves `ar.lproj` out of the
# product — the widget then shows English in the gallery on an Arabic phone.
LOCALES.each do |locale|
  project.root_object.known_regions << locale unless project.root_object.known_regions.include?(locale)
end

project.save
puts "saved #{PROJECT_PATH}"
