---
name: store-screenshots
description: Retake Campfire's promotional images for every platform and form factor — the store listings (Play Store, App Store, Mac App Store, Microsoft Store), the Play Store feature graphic, Apple's product page header and search results images, the README banners and campfire-songbook.com's screenshots and link preview. Renders them with the offscreen screenshot tool in tools/screenshots, checks them, frames them in the Screenshot Bro project and exports the store images to a new folder on the Desktop, the README banners to documentation/screenshots and the website's images to the sibling CampfireWebsite repository. Invoke this skill WHENEVER the user asks to retake, regenerate, refresh or update the store screenshots / store images / listing screenshots / README or website screenshots, or to add, change or reorder one of them. Not for screenshots taken to verify a change while developing (that is the run skill and the platform verification recipes).
---

<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Campfire promotional images

Every image is made from the desktop build drawn offscreen as each platform (the platforms share every line of the
interface), with that platform's fonts, scale and system bars, at the exact pixels of the Screenshot Bro device frame
it goes into. Nothing here touches the production apps: the tool is `tools/screenshots`, which no app module depends
on, and the only seam in the app is `PlatformImpersonation` in `:presentation`'s desktop source set, which the desktop
app never sets. See `tools/screenshots/CLAUDE.md` for how the tool works.

## What a run needs

- **The library**, in `tools/screenshots/library/` (gitignored, never committed): a Campfire data folder's
  `library/`, `covers/` and `preferences/preferences.json`, copied from the user's desktop library without the sync
  credentials, the sync index or the log. If it is missing, stop and ask. Never write into the user's real data folder.
  Its setlists were trimmed for the shots (Frey-Tully Nuptials and Getting started); keep them that way.
- **Screenshot Bro** running, with the `Campfire` project (`list_projects`), and **Full Disk Access for the IDE that
  runs Claude Code** (Android Studio), without which nothing can be staged in the app's sandbox (step 3).
- The display does not matter: nothing is drawn on screen.

## Where everything goes

| What | Made from | Ends up in |
|---|---|---|
| Store listings (9 rows × 8 shots), Play Store banner, link preview, Apple header and search results | `Shot.kt`, `Banners.kt` → Screenshot Bro | `~/Desktop/Campfire store images <date>/`, one folder per row (with the project's icon, poster and box art rows, which have no shots) |
| README banners (GitHub - Banner 1 / 2) | `Banners.kt` → Screenshot Bro | `documentation/screenshots/01–06.webp`, 1200×900 |
| Website screenshots | `Website.kt`, no Screenshot Bro | `../CampfireWebsite/assets/screenshots/*.webp` at each file's own size |
| Website link preview | the exported `Website - Link preview` row | `../CampfireWebsite/assets/og-image.jpg`, 1200×630 |

Raw renders go to the gitignored `tools/screenshots/renders/`, replaced on every run. Nothing is committed, in either
repository; the user reviews and commits.

## Steps

1. **Render.** `./gradlew :tools:screenshots:run` (in the background; one app start per image, a few seconds each)
   writes every shot of `Shot.kt` on every listing `Device`, the multi-device rows of `Banners.kt` and the website set of
   `Website.kt` into `tools/screenshots/renders/<row label>/`. `--args="--only IPHONE,03-metronome,banners"` renders
   part of it (device enum names, shot ids, or `listing`, `banners`, `website`). A failed render is reported and the
   run goes on. Never rebuild the tool while a render is running.
2. **Check every image before importing anything**: a contact sheet per shot (PIL), then any device that looks off at
   full size — the theme, nothing loading, no stray dialog or snackbar, the shot's rules (below), text fitting. Fix the
   shot or the tool and render again rather than importing a bad image.
3. **Stage.** Screenshot Bro is sandboxed and reads only its own container: copy `renders/` into
   `~/Library/Containers/xyz.tleskiv.screenshot/Data/tmp/campfire/`. The large Android tablet's frames are rotated
   270°, so its images are staged rotated a quarter turn clockwise (PIL `ROTATE_270`).
4. **Import.** Take `get_project` first: the user arranges the rows by hand between runs, and **their layout is kept
   exactly** — positions, sizes, tilts, stacking. `import_screenshots` fills a row's device frames **one per column, in
   column order, the rearmost frame of each column**, and **resizes every frame it fills to the image's aspect ratio**,
   so after every import put each frame's `x`, `width` and `height` back. Extra images append columns: never pass more
   than the row has.
   - The nine listing rows (one frame per column): import the row's eight images in order.
   - Rows with several frames in a column (the README banners, the Play Store banner, the link preview, Apple's header
     and search results): give each extra frame a temporary column (`add_template`, `delete_shape` the default device
     it drops in, move the frame's `x` there), import, move it back, `remove_template`. Then restore the stacking with
     `z_order: front` in back-to-front order, keeping every MacBook's black camera-housing strip above its frame.

   | Row label | Row id |
   |---|---|
   | Play Store - Android Phone | `961E2ED3-1432-46EA-AF1F-5738741BB18E` |
   | Play Store - Android Small Tablet | `C6B36C5A-7011-4721-AD0F-CE92E91DC1F2` |
   | Play Store - Android Large Tablet | `4AB75520-E483-4E51-93A8-568848CE24BB` |
   | Play Store - Chromebook | `15F6D06E-114E-468A-BEA1-E074FA445ECA` |
   | App Store - iPhone | `CA8CB67F-AD3B-4CE6-95F1-1A23555C1320` |
   | App Store - iPhone Duo | `755F5064-0A7F-4F17-828A-E985A26E2A8E` |
   | App Store - iPad | `5B53E8FA-F8D5-4D72-B0A6-58D1F847CB0E` |
   | Mac App Store - Mac | `09A157EA-A360-4800-B9F6-72E4ED90178F` |
   | Microsoft Store - Windows | `C369F36A-B693-46C7-A53C-E8AA13CC6D53` |
   | GitHub - Banner 1 | `D14A5619-01BE-4E2D-A654-187E8467999A` |
   | GitHub - Banner 2 | `9B2BA297-3919-4691-A358-C90C3C2AAF95` |
   | Play Store - Banner | `7EAB3190-9E8C-478D-B18A-3AD15B16DDA1` |
   | Website - Link preview | `EB3026C2-34AF-48BA-A338-DAD16A8014B9` |
   | App Store - Header (21:9, 3840×1646, iOS 27+) | `AACF099E-6929-47B0-8F4E-DF44DFEEC7B2` |
   | App Store - Search results (3:2, 3840×2560, iOS 27+) | `D93A0DED-074D-4C47-B097-5CAC6DEC5DCA` |

   If a row's label or column count is not what this table and the code expect, stop and say so. Headlines are the
   project's text shapes, never part of the images.
5. **Look at every row** with `render_preview`, a column at full size where anything is in doubt: the status bar's
   time and the navigation labels uncut, the image filling its frame, nothing upside down or stretched.
6. **Export** with `export_project` (into the app's temp folder; a non-empty `unrenderable` means holes — report it).
   Copy every row's folder **except the two GitHub banners** to a new `~/Desktop/Campfire store images <date>/`. The
   banners go to the README: `python3 -I .claude/skills/store-screenshots/scripts/readme_banners.py <repo root>`
   followed by banner 1's three exported images and then banner 2's.
7. **The website**: `python3 -I .claude/skills/store-screenshots/scripts/website_screenshots.py
   tools/screenshots/renders/Website "<Desktop folder>/Website - Link preview — 1200x630/01_Website - Link preview_en.png"
   "$HOME/Desktop/Campfire website images"` writes every website screenshot at the size of the file it replaces, as
   WebP, and the link preview as `og-image.jpg`; where the sibling `CampfireWebsite` repository is missing it writes
   them to the Desktop folder instead.
8. **Clean up** the staging and export folders in the app's container, and report the Desktop folder, what was
   rendered and anything that looked off.

## The shots and their rules

The listing alternates two dark, two light: 1–2 dark, 3–4 light, 5–6 dark, 7–8 light. Shared rules: the store
screenshots are shown at 9:41 on Wednesday, October 7, 2026 (setlist dates are moved with the run's day, so their
countdowns always read the same); the iPhone Duo is always part of the listing, shown unfolded and on its side
(its inner display, 2853×2007 at 3x, as iOS 27 lays it out: no status bar across the top, but an 84pt strip along
the right edge that the app is inset from and draws under, with the time and the status ring at its top, and the home
indicator at the bottom); the app icon anywhere in the system chrome is the default gradient one; Android phone frames are Screenshot Bro's Pixel frame; Share is shown on Android and iOS only; sync is a fake connected
account, "Connected as Péter Pandula"; only public domain songs are opened full screen in the listing.

| # | Headline | Shot |
|---|---|---|
| 1 | Your songbook, on any screen | Songs, English only, by artist, scrolled to Bob Dylan (pinned), "+" menu open |
| 2 | Laid out for the way you play | House of the Rising Sun (Traditional American); phones at 100% text with the Picking pattern folded, Chromebook at 100%, so the whole first verse shows |
| 3 | A metronome that knows each song | Metronome tab playing 6/8 at 236, beats 1 and 4 accented, caught on beat 1 |
| 4 | Setlists ready for the gig | Setlists by date: Frey-Tully Nuptials "In 2 days", Friday Night by the Lake "Tomorrow" |
| 5 | ChordPro editor with live preview | Home on the Range in the editor; Split wherever the app offers it, Edit otherwise; Shortcuts always open |
| 6 | Print a song or the whole set | Home on the Range exported: PDF, A4, landscape (portrait on the small Android tablet), two columns, every option on |
| 7 | Offline first, synced across devices | Settings → Library, sync connected |
| 8 | Make it yours, down to the icon | Settings → General |

The README banners are shots 1, 3, 2, 4, 5 on the Android phone, iPhone, iPad, small Android tablet and Mac — dark in
banner 1, light and in reverse order in banner 2. The Play Store banner is the Android phone's shots 1 and 2; the link
preview the Mac's 2 and the iPhone's 1; Apple's header the iPad's 4, the Mac's 2 and the iPhone's 1, and its search
results image the iPad's 2 and the iPhone's 3, all dark.

The website's songs are set at the text size where page one ends exactly where a section ends, never inside one:
Still Alive 90% (Verse 2 in one column), Accidentally in Love 90% with the metronome panel open, I'll Be There for You
105% on the laptop. A song longer than a page is cut wherever a column ends, so these were found by rendering every
size from 80% to 130% in steps of 2.5% and reading where page one ends; if a song, a device or the layout changes, try
again the same way. **Never fold the Chords section (or anything else) to make a song fit.** The laptop is a Mac window
with no system chrome at 1.5× (`Device.LAPTOP`).

## Changing a shot

The listing is `shots` in `Shot.kt`, in store order; the website's are in `Website.kt` and the multi-device rows in
`Banners.kt`. A shot sets the preferences it needs (`preferences`, per device), prepares state a screen only reads as
it is first composed (`prepare`, e.g. a list's scroll position), then drives `CampfireViewModel` or taps what is
labelled with a string resource (`drive`, `tap`, `tapIfPresent`) — so it works in every language — and can catch a
metronome beat (`beat`). Keep each shot's rules in its comment and in the table above. Adding or removing a listing
shot also means adding or removing that column in every listing row of the project (copying a neighbor's frame and
headline), and updating the headlines there.

The system chrome is `SystemChrome.kt`, measured against the real systems (Android emulators, iOS simulators, the
desktop app's own window). The Chromebook and Windows rows are framed in MacBook Pros whose camera housing a black
rectangle of the project hides; their shots start the window under a black band of that rectangle's height
(36 and 38 dp), so if those rectangles or frames are moved or resized, measure again and update the band heights.
