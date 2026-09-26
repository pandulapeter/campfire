<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Campfire
*Your songbook, on every screen you own.*

Campfire keeps your lyrics and chords in plain [ChordPro](https://www.chordpro.org) files and shows them the way you
want to read them while playing: chords above the syllables they belong to, as few and as wide columns as the screen
allows, and the text as large as you need it.

<img src="documentation/screenshots/01.png" width="32%" /> <img src="documentation/screenshots/02.png" width="32%" /> <img src="documentation/screenshots/03.png" width="32%" />

Write songs in the built-in editor, transpose them into your key, put
them into setlists, and read them on your phone, your tablet, your laptop or in a browser.

<img src="documentation/screenshots/04.png" width="32%" /> <img src="documentation/screenshots/05.png" width="32%" /> <img src="documentation/screenshots/06.png" width="32%" />

Campfire works offline, there is no account to make, and there is no server of mine anywhere: songs are synced across your
devices using your Dropbox account. The app is completely free, open-source, and there are no ads.

<!-- Settings links to this heading's anchor (#get-campfire); renaming it means changing SettingsScreen.kt too. -->
## Get Campfire

Campfire is available for the following platforms:

<a href="https://play.google.com/store/apps/details?id=com.pandulapeter.campfire"><img src="documentation/images/badge_android.png" alt="Campfire for Android" height="32px" /></a>
<a href="https://github.com/pandulapeter/campfire/releases/latest"><img src="documentation/images/badge_linux.png" alt="Campfire for Linux" height="32px" /></a>
<a href="https://pandulapeter.com/campfire"><img src="documentation/images/badge_web.png" alt="Campfire in the browser" height="32px" /></a>

Coming soon (under review):

<a href="https://github.com/pandulapeter/campfire/releases/latest"><img src="documentation/images/badge_ios.png" alt="Campfire for iOS" height="32px" /></a>
<a href="https://github.com/pandulapeter/campfire/releases/latest"><img src="documentation/images/badge_macos.png" alt="Campfire for macOS" height="32px" /></a>
<a href="https://github.com/pandulapeter/campfire/releases/latest"><img src="documentation/images/badge_windows.png" alt="Campfire for Windows" height="32px" /></a>

## Documentation

- [Features](documentation/features.md) - everything the app does, at length, and what it does with your data.
- [File format](documentation/file-format.md) - the ChordPro directives Campfire understands, the setlist JSON, and how files are named.
- [Sync](documentation/sync.md) - what it sees, how a run decides, and why it needs no backend.

## To do
- Android dynamic theme detection on Samsung is not working
- Update badge assets in the readme
- .cho file association doesn't work on Samsung devices
- Fast scroll touch target conflicts with back gesture
- Avoid duplicating information on detail screen modals. On other modals make song title / artist appearance consistent
- Song assignments bottom sheet: tag vertical spacing should be incremented
- "Add tag" dialog should be a bottom sheet could be a bottom sheet
- "Language picker" dialog could also be a bottom sheet
- "Song assignments" bottom sheet: open keyboard automatically, hide it on downwards scroll
- Downwards scroll dismissing the keyboard should work on every modal list
- Review icon color saturation
- Back navigation on Settings should pop back to the General tab first
- Tapping on the Song details screen app bar title should scroll to the top
- Song details screen scroll should snap to rows
- Can we close the browser window after the Dropbox redirect?
- Sort and Filter UI should be improved (better animations)
- Haptic effects
- Auto-scroll
- Optional close confirmation dialog on supported platforms
- Add genre tags, similar to language tags
- Add ability to add links as metadata items
- Cover art thumbnails as tags, use https://musicbrainz.org/doc/Cover_Art_Archive/API
- Add support for the Nashville chord system
- Once Microsoft Store, App Store and Mac App Store listings are approved, update included URL-s + this Readme
- Add more automatic sync triggers
- Add support for external control devices with a focus-by-section feature

## License

Copyright (c) Pandula Péter 2017-2026. This software is licensed under the
[Mozilla Public License 2.0](LICENSE).

The MPL is a file-level copyleft license, which means you are welcome to reuse any part of Campfire — the ChordPro
parser, the sync engine, a single screen — in your own project, open source or not. If you modify one of the files in
this repository, that file has to stay under the MPL and its source has to be available; anything new you write
alongside it is yours, under whatever license you choose.

The name *Campfire*, the app icon and the other branding assets are **not** covered by the license and remain the
author's. The MPL grants no trademark rights (section 2.3), so a fork has to ship under a name and an icon of its own.

The web build bundles [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono), copyright The JetBrains Mono
Project Authors, which is licensed under the [SIL Open Font License 1.1](presentation/src/wasmJsMain/composeResources/files/licenses/jetbrains_mono_ofl.txt)
rather than the MPL, and [Inter](https://github.com/rsms/inter), copyright The Inter Project Authors, licensed under the
[same license](presentation/src/wasmJsMain/composeResources/files/licenses/inter_ofl.txt).
