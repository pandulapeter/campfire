---
name: website-screenshots
description: Retake campfire-songbook.com's images — its screenshots in every language of the app and its link preview (og-image.jpg) — and the README banners. Renders them with the offscreen screenshot tool in tools/screenshots, checks them, frames the link preview and the banners in the Screenshot Bro project, and writes the website's images to the sibling CampfireWebsite repository and the banners to documentation/screenshots. Invoke this skill WHENEVER the user asks to retake, regenerate, refresh, or update the website screenshots, the link preview / og-image, or the README banners / README screenshots, or to add, change, or reorder one of them. Not for the store listings and the stores' other images (that is store-screenshots), nor for screenshots taken to verify a change while developing (that is the run skill and the platform verification recipes).
---

<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Campfire website and README images

Made like the store images (the `store-screenshots` skill): the desktop build drawn offscreen as each platform by
`tools/screenshots` (see its `CLAUDE.md`). The website's screenshots go to the site as rendered; its link preview and
the README banners are multi-device rows framed in the same Screenshot Bro project as the store images.

## What a run needs

- **The library**, in `tools/screenshots/library/` (gitignored, never committed): a Campfire data folder's
  `library/`, `covers/`, and `preferences/preferences.json`, copied from the user's desktop library without the sync
  credentials, the sync index, or the log. If it is missing, stop and ask. Never write into the user's real data
  folder.
- **Screenshot Bro** running, with the `Campfire` project (`list_projects`), and **Full Disk Access for the IDE that
  runs Claude Code** (Android Studio). The link preview is part of the website, so a run needs it: without the
  `screenshot-bro` tools, stop and say so (the server may only have failed to connect). Where Screenshot Bro is
  installed but has no `Campfire` project, quit it and run `python3 -I
  .claude/skills/store-screenshots/scripts/screenshot_bro_project.py restore`, then reopen it.
- **Pillow** for the scripts (`python3 -m pip install Pillow` where `import PIL` fails).

## Where everything goes

| What | Made from | Ends up in |
|---|---|---|
| Website screenshots | `Website.kt`, no Screenshot Bro | `../CampfireWebsite/assets/screenshots/*.webp` (or `../campfire-website/`) at each file's own size, the other languages in `assets/screenshots/<language>/` |
| Website link preview | `Banners.kt` → Screenshot Bro (`Website - Link preview`) | `../CampfireWebsite/assets/og-image.jpg`, 1200×630 |
| README banners | `Banners.kt` → Screenshot Bro (`GitHub - Banner 1` / `2`) | `documentation/screenshots/01–06.webp`, 1200×900 |

Nothing is committed, in either repository; the user reviews and commits.

## Steps

1. **Render.** `./gradlew :tools:screenshots:run --args="--only website,banners"` in the background (the `banners`
   set also holds the stores' multi-device rows, a handful of images; leave them). Never rebuild the tool while a
   render is running.
2. **Check every image before importing anything**: a contact sheet per folder (PIL), then anything that looks off at
   full size — the theme, nothing loading, no stray dialog or snackbar, the rules below, text fitting. Fix the shot or
   the tool and render again rather than importing a bad image.
3. **Stage** the three rows' folders of `tools/screenshots/renders/` into
   `~/Library/Containers/xyz.tleskiv.screenshot/Data/tmp/campfire/` (Screenshot Bro is sandboxed).
4. **Import** into these rows only, after `get_project`: **the user's layout is kept exactly**. Each row has several
   frames in a column, and `import_screenshots` fills only the rearmost frame of each column, one per column, resizing
   every frame it fills to the image's aspect ratio. So give each extra frame a temporary column (`add_template`,
   `delete_shape` the default device it drops in, move the frame's `x` there), import, move it back, `remove_template`,
   and put each frame's `x`, `width`, and `height` back. Then restore the stacking with `z_order: front` in
   back-to-front order, keeping every MacBook's black camera-housing strip above its frame.

   | Row label | Row id |
   |---|---|
   | GitHub - Banner 1 | `D14A5619-01BE-4E2D-A654-187E8467999A` |
   | GitHub - Banner 2 | `9B2BA297-3919-4691-A358-C90C3C2AAF95` |
   | Website - Link preview | `EB3026C2-34AF-48BA-A338-DAD16A8014B9` |

   If a row's label or frames are not what this table and `Banners.kt` expect, stop and say so.
5. **Look at each row** with `render_preview`: the status bar's time and the navigation labels uncut, the images
   filling their frames, nothing stretched.
6. **Export** with `export_project` (into the app's temp folder; a non-empty `unrenderable` means holes — report it).
   The banners go to the README: `python3 -I .claude/skills/website-screenshots/scripts/readme_banners.py <repo root>`
   followed by banner 1's three exported images and then banner 2's.
7. **The website**: `python3 -I .claude/skills/website-screenshots/scripts/website_screenshots.py
   tools/screenshots/renders/Website "$HOME/Desktop/Campfire website images" --link-preview "<export folder>/Website -
   Link preview — 1200x630/01_Website - Link preview_en.png"` writes every website screenshot at the size the site
   draws its device at (the script's `SIZES`), as WebP, and the link preview as `og-image.jpg`. The `-beat-<n>` renders
   of a playing metronome become one `<name>-beats-<theme>.webp` instead, the lit beats on a transparent strip the page
   fades in. Each language's renders (`renders/Website/<language>/`) go to `assets/screenshots/<language>/`, where the
   site's pages in that language pick them up; one identical to the English render is left out, so those pages fall
   back to the English file. It finds the website's repository as a sibling folder named `CampfireWebsite` (the
   owner's) or `campfire-website` (a plain clone), or where `--website <path>` says; where there is none it writes to
   the Desktop folder and says so.
8. **Save the project** with `python3 -I .claude/skills/store-screenshots/scripts/screenshot_bro_project.py save`; a
   diff that is not the run's own changes is the user's work in the app: keep it.
9. **Clean up** the staging and export folders in the app's container, and report what was written where and anything
   that looked off.

## The shots and their rules

The README banners and the link preview show the store listing's shots, under its rules (the `store-screenshots`
skill's "The shots and their rules"): the README banners are shots 1, 3, 2, 4, 5 on the Android phone, iPhone, iPad,
small Android tablet, and Mac — dark in banner 1, light and in reverse order in banner 2; the link preview is the Mac's
2 and the iPhone's 1, dark.

The website's shots are in `Website.kt`, every one in every language of the app. Its tablet songs are set at the text
size where page one ends exactly where a section ends, never inside one: Still Alive 110% (page one ends after
Verse 4); Uptown Girl 100% with the metronome panel open (after Verse 3), under the tuner sheet too; I'm Gonna Be
(500 Miles) 110% (after Verse 3), under the Choose chord shape and the scale sheets. A song longer than a page is cut
wherever a column ends, so these were found by rendering every size from 80% to 130% and reading where page one ends;
if a song, a device, or the layout changes, try again the same way. The laptop's text size demo is I'll Be There for
You at every 10% from 100% to 230%, the app's own steps, wherever page one ends. The laptop's editor is scrolled past
Thinking Out Loud's directives. **Never fold the Chords section (or anything else) to make a song fit.** The laptop is
a Mac window with no system chrome at 1.32×, a maximized window on a 14" MacBook Pro (`Device.LAPTOP`).

A new website image also needs the page that shows it, in the website's repository, and a size in the script's `SIZES`
if it is a new kind of device.
