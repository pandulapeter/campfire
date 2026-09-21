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
allows, and the text as large as you need it. Write songs in the built-in editor, transpose them into your key, put
them into setlists, and read them on your phone, your tablet, your laptop or in a browser.

It works **offline**, there is no account to make, and there is no server of mine anywhere. It is free, and there are
no ads.

[<img src="documentation/images/badge_android.png" alt="Download for Android" height="32px" />](https://play.google.com/store/apps/details?id=com.pandulapeter.campfire)
[<img src="documentation/images/badge_linux.png" alt="Download for Linux" height="32px" />](https://github.com/pandulapeter/campfire/releases/latest)
[<img src="documentation/images/badge_web.png" alt="Open in browser" height="32px" />](https://pandulapeter.com/campfire)

The Linux build is a `.deb` package for amd64 and arm64, attached to every
[release](https://github.com/pandulapeter/campfire/releases/latest).

**Coming soon** to the App Store, the Mac App Store and the Microsoft Store:

[<img src="documentation/images/badge_ios.png" alt="Campfire for iOS" height="32px" />](https://github.com/pandulapeter/campfire/releases/latest)
[<img src="documentation/images/badge_macos.png" alt="Campfire for macOS" height="32px" />](https://github.com/pandulapeter/campfire/releases/latest)
[<img src="documentation/images/badge_windows.png" alt="Campfire for Windows" height="32px" />](https://github.com/pandulapeter/campfire/releases/latest)

Until the stores have them, every release also carries these three builds, marked `unsigned` because that is what
they are:

- **macOS** (`.dmg`, Apple silicon and Intel): macOS refuses to open an app it cannot trace to a developer. Open it
  once, then allow it under *System Settings → Privacy & Security → Open Anyway*.
- **Windows** (`.msi`): SmartScreen warns about an unknown publisher; *More info → Run anyway* gets past it.
- **iOS** (`.ipa`): cannot be installed as it is. It is the file a sideloading tool such as
  [AltStore](https://altstore.io) or [Sideloadly](https://sideloadly.io) signs with your own Apple ID.

The Android `.apk` on the same page is the file Google Play gets, under the same signature.

### Screenshots

*These are from Campfire 1.x, which had a built-in online song library. Version 4 is a rewrite and looks nothing like
them any more — new screenshots are on the way.*

<img src="documentation/screenshots/01.png" width="20%" /> <img src="documentation/screenshots/02.png" width="20%" />
<img src="documentation/screenshots/03.png" width="20%" /> <img src="documentation/screenshots/04.png" width="20%" />
<img src="documentation/screenshots/05.png" width="20%" /> <img src="documentation/screenshots/06.png" width="20%" />
<img src="documentation/screenshots/07.png" width="20%" /> <img src="documentation/screenshots/08.png" width="20%" />

### What it does

- **Read** with adjustable text size, a lyrics-only mode, and a performance mode that puts everything which could
  change your library out of the way while you are playing.
- **Transpose** by ear or by key, in sharps, flats or German notation.
- **Write and edit** in a ChordPro editor with syntax highlighting and a live preview.
- **Organize** into setlists, each song with its own transposition, and filter the library by tag or by language.
- **Import and export** single songs, setlists or the whole library as zip archives of plain text — and open a
  ChordPro file straight from a file manager, an email or a browser download.
- **Sync** between your devices through your own Dropbox, if you want to. Off until you turn it on.
- **Fits your setup**: light and dark in eight colour schemes, English and Hungarian, and arrow key control, which is
  what a page turner pedal sends.

More detail in [documentation/features.md](documentation/features.md).

### Your songs are yours

The library is a folder of ordinary text files in Campfire's own storage on your device. Nothing is uploaded, nothing
is analyzed, and the app collects nothing at all — what it does with your data is written out in the
[privacy policy](https://pandulapeter.com/legal/privacy_policy-campfire.html) linked from Settings.

The one thing that ever touches the network is sync, and only after you have connected a cloud folder **you** own,
which Campfire reaches directly with no service of mine in between. Everything can be exported at any time as a zip
any other ChordPro tool can read, so leaving is as easy as arriving.

### Documentation

- [Features](documentation/features.md) — everything the app does, at length.
- [File format](documentation/file-format.md) — the ChordPro directives Campfire understands, the setlist JSON, and
  how files are named.
- [Sync](documentation/sync.md) — what it sees, how a run decides, and why it needs no backend.
- Publishing — what is still to be done before the [iOS App Store](documentation/publishing/ios-app-store.md), the
  [Mac App Store](documentation/publishing/mac-app-store.md) and the
  [Microsoft Store](documentation/publishing/microsoft-store.md) have the app.
- [CLAUDE.md](CLAUDE.md) — the architecture, the module graph and the per-platform build commands.

### Notes

Version 4.0 is a rewrite: the online song library is gone and there is **no migration**. The first launch starts with
an empty library.

### To do
- Rename setlist date sort, maybe expose a date for Setlists?
- New setlist dialog: description + auto assign song if opened from the setlist assignments bottom sheet
- Improve performance mode description
- Editor close confirmation dialog and Save button enabled / disabled state should check for identity with the original file
- Implement .cho (and other) file association
- Songs: sort by year option
- New song sorting option: sort by year
- Fix Songs screen async race animation issues
- Update the screenshots in the Readme
- Integrate Crashlytics
- Create new screenshots for iOS, Android and desktop
- Create iOS store listing
- Create GitHub action for TestFlight releases
- Create App Store and Windows Store listings + update GitHub actions

### License

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
rather than the MPL.
