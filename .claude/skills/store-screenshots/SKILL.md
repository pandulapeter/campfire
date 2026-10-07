---
name: store-screenshots
description: Retake Campfire's store screenshots for every platform and form factor — render them with the offscreen screenshot tool in tools/screenshots, check them, import them into the Screenshot Bro project row by row and export the finished store images to a new folder on the Desktop. Invoke this skill WHENEVER the user asks to retake, regenerate, refresh or update the store screenshots / store images / listing screenshots, or to add, change or reorder a screenshot of the listing. Not for screenshots taken to verify a change while developing (that is the run skill and the platform verification recipes).
---

<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Campfire store screenshots

Every store image is made from the desktop build drawn offscreen as each platform (the platforms share every line
of the interface), with that platform's fonts, scale and system bars, at the exact pixels of the Screenshot Bro
device frame it goes into. Nothing here touches the production apps: the tool is `tools/screenshots`, which no app
module depends on, and the only seam in the app is `PlatformImpersonation` in `:presentation`'s desktop source set,
which the desktop app never sets. See `tools/screenshots/CLAUDE.md` for how the tool works.

## What a run needs

- **The library**, in `tools/screenshots/library/` (gitignored, never committed): a Campfire data folder's
  `library/`, `covers/` and `preferences/preferences.json`. If it is missing, stop and ask the user — it was copied from
  their desktop library (`~/Library/Application Support/Campfire`), without the sync credentials, the sync index or
  the log. Never copy those, and never write into the user's real data folder.
- **Screenshot Bro** running, with the `Campfire` project (`list_projects`).
- The display does not matter: nothing is drawn on screen and nothing needs the window server.

## Steps

1. **Render.** `./gradlew :tools:screenshots:run` (in the background, one app start per image) writes every shot of
   `Shot.kt` on every `Device`, and the multi-device rows of `Banners.kt`, into the gitignored
   `tools/screenshots/renders/<row label>/`, replacing the last run's. `--args="--only IPHONE,03-metronome,banners"`
   renders part of it (device enum names, shot ids, `listing` or `banners`). A failed render is reported and the run
   goes on.
2. **Check every image before importing anything**: a contact sheet per shot (PIL), then any device that looks off at
   full size — the theme, nothing loading, no stray dialog or snackbar, the shot's rules in `Shot.kt`, text fitting.
   Fix the shot or the tool and render again rather than importing a bad image.
3. **Stage.** Screenshot Bro is sandboxed and reads only its own container: copy `renders/` into
   `~/Library/Containers/xyz.tleskiv.screenshot/Data/tmp/campfire/` (needs Full Disk Access for the IDE running Claude
   Code). The large Android tablet's frames are rotated 270°, so its images are staged rotated a quarter turn clockwise
   (PIL `ROTATE_270`).
4. **Import.** `import_screenshots` fills a row's device frames **one per column, in column order, in the order the
   shapes are stacked (back first)**, and **resizes every frame it fills to the image's aspect ratio** - so after
   every import put each frame's `x`, `width` (and `height`) back from `get_project` taken beforehand. Extra images
   append columns: never pass more than the row has columns.
   - The eight listing rows (one frame per column): import the row's eight images in order.
   - Rows with several frames in a column (the banners, the Play Store banner, the link preview, and Apple's product
     page header, 3840×1646, and search results image, 3840×2560, which App Store Connect takes for iOS 27 and later): give a frame its
     own temporary column (`add_template`, then move the frame's `x` into it), import, move it back, `remove_template`
     the column (and `delete_shape` the default device `add_template` drops into it). Frames that already hold the
     right image can simply be re-imported along with the target. Afterwards restore the original stacking with
     `z_order: front` in back-to-front order, keeping each MacBook's black camera-housing strip on top of it.

   | Row label | Row id |
   |---|---|
   | Play Store - Android Phone | `961E2ED3-1432-46EA-AF1F-5738741BB18E` |
   | Play Store - Android Small Tablet | `C6B36C5A-7011-4721-AD0F-CE92E91DC1F2` |
   | Play Store - Android Large Tablet | `4AB75520-E483-4E51-93A8-568848CE24BB` |
   | Play Store - Chromebook | `15F6D06E-114E-468A-BEA1-E074FA445ECA` |
   | App Store - iPhone | `CA8CB67F-AD3B-4CE6-95F1-1A23555C1320` |
   | App Store - iPad | `5B53E8FA-F8D5-4D72-B0A6-58D1F847CB0E` |
   | Mac App Store - Mac | `09A157EA-A360-4800-B9F6-72E4ED90178F` |
   | Microsoft Store - Windows | `C369F36A-B693-46C7-A53C-E8AA13CC6D53` |
   | GitHub - Banner 1 | `D14A5619-01BE-4E2D-A654-187E8467999A` |
   | GitHub - Banner 2 | `9B2BA297-3919-4691-A358-C90C3C2AAF95` |
   | Play Store - Banner | `7EAB3190-9E8C-478D-B18A-3AD15B16DDA1` |
   | Website - Link preview | `EB3026C2-34AF-48BA-A338-DAD16A8014B9` |
   | App Store - Header | `AACF099E-6929-47B0-8F4E-DF44DFEEC7B2` |
   | App Store - Search results | `D93A0DED-074D-4C47-B097-5CAC6DEC5DCA` |

   The Chromebook and Windows rows are framed in MacBook Pros whose camera housing a black rectangle of the project
   hides; their shots already start the window under a black band of that rectangle's height (`SystemChrome`), so if
   those rectangles or frames are ever moved or resized, measure again and update the band heights there.

   If a row's label or column count is not what this table and `Shot.kt` / `Banners.kt` expect, stop and say so. The
   headlines are the project's text shapes, never part of the images.
5. **Look at every row** with `render_preview` (a column at full size where anything is in doubt: the status bar's
   time and the navigation labels uncut, the image filling its frame, nothing upside down).
6. **Export** with `export_project` (it writes to the app's own temp folder). Copy every row's folder **except the two
   GitHub banners** to a new `~/Desktop/Campfire store images <date>/`. The banners go to the README:
   `python3 -I .claude/skills/store-screenshots/scripts/readme_banners.py <repo root>` followed by banner 1's three
   exported columns and then banner 2's, which overwrites `documentation/screenshots/01–06.webp` (1200×900). A
   non-empty `unrenderable` means holes in the images: report it.
7. **The website.** The `website` set (`Website.kt`) renders campfire-songbook.com's screenshots, light and dark, under
   the names the site uses: the phone ones on the iPhone, the tablet ones on the iPad, the laptop ones on a chromeless
   Mac window (`Device.LAPTOP`). They need no Screenshot Bro: `python3 -I
   .claude/skills/store-screenshots/scripts/website_screenshots.py tools/screenshots/renders/Website <exported link
   preview png> <Desktop fallback folder>` scales each to the size of the file it replaces in the sibling
   `CampfireWebsite/assets/screenshots` as WebP, and writes the link preview as `assets/og-image.jpg`; without that
   repository it writes them to the fallback folder. Songs are shown at 100% text, which lays them out in three
   columns as the site always showed them. Never commit in the website repository either.
8. **Clean up** the staging folder and the export folder in the app's container, and report the Desktop folder, what
   was rendered, and anything that looked off. Never commit; the `.webp` changes are left for the user.

## Changing a shot

The listing is `shots` in `tools/screenshots/src/main/kotlin/com/pandulapeter/campfire/screenshots/Shot.kt`, in store
order. Each shot carries its rules — theme, language, the screen and its state — as its `drive` block and its KDoc,
so a retake is the same picture. A shot drives `CampfireViewModel` (open a song, select a tab, open a sheet) rather
than tapping, and calls `settle()` after anything that animates. Adding or removing a shot also means adding or
removing that column in every row of the project (`add_template` / `remove_template`, copying a neighbor's device
frame and headline), and updating the headlines there.
