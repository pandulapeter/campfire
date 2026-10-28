---
name: store-screenshots
description: Retake Campfire's store images for every platform and form factor — the store listings (Play Store, App Store, Mac App Store, Microsoft Store) in every language of the app, the Play Store feature graphic, and Apple's product page header and search results images. Renders them with the offscreen screenshot tool in tools/screenshots, checks them, frames the listings with their translated headlines and feature chips and composes the multi-device images, into a folder on the Desktop named after the version. Invoke this skill WHENEVER the user asks to retake, regenerate, refresh, or update the store screenshots / store images / listing screenshots, or to add, change, or reorder one of them. Not for the README banners or campfire-songbook.com's screenshots and link preview (that is website-screenshots), nor for screenshots taken to verify a change while developing (that is the run skill and the platform verification recipes).
---

<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->

# Campfire store images

Every image is made from the desktop build drawn offscreen as each platform (the platforms share every line of the
interface and its bundled fonts), with that platform's scale and system bars. Nothing here touches the production apps:
the tool is `tools/screenshots`, which no app module depends on, and the only seam in the app is `PlatformImpersonation`
in `:presentation`'s desktop source set, which the desktop app never sets. See `tools/screenshots/CLAUDE.md` for how the
tool works.

## What a run needs

- **The library**, in `tools/screenshots/library/` (gitignored, never committed): a Campfire data folder's
  `library/`, `covers/` and `preferences/preferences.json`, stripped to what the images show. If it is missing, stop
  and ask. Never write into the user's real data folder. Its setlists were trimmed for the shots (Frey-Tully Nuptials
  and Getting started); keep them that way.
- **Nothing else building in the clone** while a render runs: a jar rebuilt under it fails later shots with a
  `ClassNotFoundException` while the run still reports them written.
- **Pillow** for the contact sheets (`python3 -m pip install Pillow` where `import PIL` fails).

## Where everything goes

Everything lands in `~/Desktop/Campfire store images <version>/` (`campfire.versionName`), replaced by every run, as
PNG with no alpha channel (Apple refuses screenshots that have one, Play a feature graphic) but for Play's icon:

| What | Made from | File |
|---|---|---|
| Store listings (9 devices × 8 shots × every language) | `Shot.kt`, the texts in `documentation/store-listings/screenshot-texts/<language>.md`, `ListingChipStyles.kt` | `<store>/<language>/<device>-<n>.png` |
| Play Store feature graphic (every language) and icon | `Compositions.kt` | `google-play/<language>/feature-graphic.png`, `google-play/icon.png` |
| Apple's product page header and search results image (every language; the caption is `search-results` in the texts) | `Compositions.kt` | `app-store/<language>/header.png`, `app-store/<language>/search-results.png` |
| Microsoft's poster and box art | `Compositions.kt` | `microsoft-store/poster-art.png`, `microsoft-store/box-art.png` |

The README's banners and the website's link preview show the same shots: say so when a shot changed, since they are
the `website-screenshots` skill's to remake. Raw renders go to the gitignored `tools/screenshots/renders/`, replaced on
every run. Nothing is committed unless the user asks.

## Steps

1. **Render.** `./gradlew :tools:screenshots:run --args="--only listing,store-banners" > <scratchpad>/render.log 2>&1` in the
   background, and beside it, under Monitor (30 minutes, re-armed while the render runs),
   `tools/screenshots/progress.sh <scratchpad>/render.log`: it prints the shots done and the time left every two
   minutes, relayed to the user as they come, and stops once the build ends (a render without
   `--out` empties `renders/` first, so nothing else may render in the clone until the framing is done) (one app start
   per image, a few seconds each; the listing is about 500 images, half an hour): every shot of `Shot.kt` on every
   listing `Device` in every language, into `renders/<row label>/<language>/`, and the stores' shots of `Banners.kt`.
   `--only` also takes device enum names and shot ids. A failed render is reported and the run goes on; grep the log
   for `Exception` as well, since a broken class fails a shot that still counts as written.
2. **Check the renders**: a contact sheet per device and language (PIL), then anything that looks off at full size —
   the theme, nothing loading, no stray dialog or snackbar, the shot's rules (below), text fitting in every language.
   Fix the shot or the tool and render again rather than framing a bad image.
3. **Frame**: `./gradlew :tools:screenshots:run --args="--frame store"` (a few minutes, told to the user when it
   starts and ends; it reports any render or text missing). Look at a contact sheet per store and language and at every composed image: the headline on its lines,
   every chip inside the image and readable, its lines balanced, none covering what its shot is about. A translation
   that wraps a chip into too many lines gets shorter words in its `screenshot-texts` file (`check_store_listings.py`
   holds them to their limits); then frame again, no render needed.
4. **Report** the Desktop folder, what was rendered and framed, and anything that looked off.

## The shots and their rules

The listing alternates two dark, two light: 1–2 dark, 3–4 light, 5–6 dark, 7–8 light. Shared rules: the store
screenshots are shown at 9:41 on Wednesday, October 7, 2026 (setlist dates are moved with the run's day, so their
countdowns always read the same); the iPhone Duo is always part of the listing, shown unfolded and on its side
(its inner display, 2853×2007 at 3x, as the iOS 27.1 simulator lays it out: no status bar across the top, but an 84pt
strip along the right edge that the app is inset from and draws under, with the time and the status ring at its top,
insets of 16pt at the top and 34pt at the bottom, and no home indicator drawn); the app icon anywhere in the system chrome is the default gradient one; Share is shown on Android and iOS only; sync is a fake connected
account, "Connected as Péter Pandula"; only public domain songs are opened full screen in the listing.

Each shot's headline and chips are in `documentation/store-listings/screenshot-texts`.

| # | Shot |
|---|---|
| 1 | Songs, English only, by artist, from the top (the library's copy of Gimme! Gimme! Gimme! has no subtitle) |
| 2 | House of the Rising Sun (Traditional American) with the Scales section folded, and on phones the Picking pattern too; the phones at the largest text that keeps the whole first verse on the first page (the least room left spread between its sections): the iPhone at 110%, the Android phone and the Chromebook at 100% |
| 3 | Home on the Range on a guitar, its metronome panel open (not playing: the tuner sheet stops the click) under the tuner sheet, which hears the A string 12 cents flat |
| 4 | Setlists by date: Frey-Tully Nuptials "In 2 days", Friday Night by the Lake "Tomorrow" |
| 5 | Home on the Range in the editor; Split wherever the app offers it, Edit otherwise; Shortcuts always open |
| 6 | Scales tab on a guitar in standard tuning, in A, under the sheet of A minor pentatonic |
| 7 | Chords tab on a ukulele in standard tuning, the chords of C |
| 8 | Home on the Range exported: PDF, A4, landscape (portrait on the small Android tablet), two columns, every option on |

The Play Store feature graphic is the Android phone's shots 1 and 2; Apple's header the iPad's 4, the Mac's 2, and
the iPhone's 1, and its search results image the iPad's 2 and the iPhone's 3, all dark. The README banners and the
link preview show these shots too, so a changed shot changes them: say so, and that the `website-screenshots` skill
publishes them.

## Changing a shot

The listing is `shots` in `Shot.kt`, in store order, and the multi-device rows are in `Banners.kt`. A shot sets the preferences it needs (`preferences`, per device), prepares state a screen only reads as
it is first composed (`prepare`, e.g. a list's scroll position), then drives `CampfireViewModel` or taps what is
labelled with a string resource (`drive`, `tap`, `tapIfPresent`) — so it works in every language — and can catch a
metronome beat (`beat`). Keep each shot's rules in its comment and in the table above. A store takes at most eight shots, so a
new one replaces another: its headline and chips go into every language's `screenshot-texts` file and its chips' icons
and corners into `ListingChipStyles.kt`. The composed images' layouts are `Compositions.kt`, each device a box in the
image's pixels with its tilt, the screen's slot an index into its banner in `Banners.kt`.

The system chrome is `SystemChrome.kt`, measured against the real systems (Android emulators, iOS simulators, the
desktop app's own window).
