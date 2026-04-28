#!/usr/bin/env ruby
# frozen_string_literal: true
# Verified replacement path for this repo when generic `upload_to_play_store` did not actually
# replace screenshots for already populated locales. Keep locale codes aligned with Play Console
# (`en-GB`, `ru-RU`, `sr`), not just local capture/source folders like `en-US` or `sr-RS`.

require "pathname"
require "supply"

ROOT = Pathname.new(__dir__).parent
METADATA_ROOT = ROOT.join("app", "fastlane", "metadata", "android")

package_name = ENV["PLAY_PACKAGE_NAME_FORCE"] || "com.queukat.train"
json_key = ENV["PLAY_KEY_FILE"]
locales = (ENV["PLAY_FORCE_LOCALES"] || "").split(",").map(&:strip).reject(&:empty?)

abort("PLAY_KEY_FILE is required") if json_key.to_s.empty?
abort("PLAY_KEY_FILE points to a missing file: #{json_key}") unless File.exist?(json_key)
abort("PLAY_FORCE_LOCALES is required, e.g. en-GB,ru-RU") if locales.empty?

Supply.config = {
  changes_not_sent_for_review: false,
  rescue_changes_not_sent_for_review: true,
  timeout: 300
}

params = {
  json_key: json_key,
  timeout: 300
}

client = Supply::Client.make_from_config(params: params)
client.begin_edit(package_name: package_name)

begin
  locales.each do |locale|
    screenshots_dir = METADATA_ROOT.join(locale, "images", "phoneScreenshots")
    abort("Missing screenshots directory for #{locale}: #{screenshots_dir}") unless screenshots_dir.directory?

    screenshots = screenshots_dir.children
      .select(&:file?)
      .sort_by { |path| path.basename.to_s }

    abort("No screenshots found for #{locale}: #{screenshots_dir}") if screenshots.empty?

    puts "==> Clearing screenshots for #{locale}"
    client.clear_screenshots(image_type: "phoneScreenshots", language: locale)

    screenshots.each do |image_path|
      puts "==> Uploading #{locale} #{image_path.basename}"
      client.upload_image(
        image_path: image_path.to_s,
        image_type: "phoneScreenshots",
        language: locale
      )
    end

    uploaded = client.fetch_images(image_type: "phoneScreenshots", language: locale)
    puts "==> #{locale} now has #{uploaded.length} screenshot(s) in this edit"
  end

  puts "==> Committing edit"
  client.commit_current_edit!
  puts "==> Done"
rescue StandardError
  begin
    client.abort_current_edit
  rescue StandardError
    nil
  end
  raise
end
