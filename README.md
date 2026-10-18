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

Campfire is a chord sheet viewer and editor built around the plain-text [ChordPro](https://www.chordpro.org/chordpro/chordpro-introduction/) format.
It runs natively on Android, iOS, macOS, Windows, Linux, and the web. 

<img src="documentation/screenshots/01.webp" width="32%" /> <img src="documentation/screenshots/02.webp" width="32%" /> <img src="documentation/screenshots/03.webp" width="32%" />

The interface is built for real use: organizing a song library, building setlists, and playing on stage.
Chord sheets can be pasted, shared from other apps, or imported from PDF, Word, and other songbook apps' backups, and are converted to ChordPro on the device.
Songs adapt to the screen with multi-column layouts, transpose on the fly, have a lyrics-only view for singers, and show chord diagrams for guitar, ukulele, mandolin, banjo, and keyboard, in alternate tunings too.
A built-in metronome and a tuner are always at hand, setlists keep each song's own key, capo, and tempo, and songs and setlists export to printable PDFs.

<img src="documentation/screenshots/04.webp" width="32%" /> <img src="documentation/screenshots/05.webp" width="32%" /> <img src="documentation/screenshots/06.webp" width="32%" />

The app works offline (the web app after its first visit), requires no account, and doesn't rely on a central server. Syncing the library across devices is handled directly through your own Dropbox storage.
Campfire is free, open-source, and has no ads or tracking. Check out the [Privacy Policy](https://campfire-songbook.com/privacy/) for more information.

## Get Campfire

The app is currently available for the following platforms:

<a href="https://play.google.com/store/apps/details?id=com.pandulapeter.campfire"><img src="documentation/images/badge_android.png" alt="Campfire for Android" height="32px" /></a>
<a href="https://apps.apple.com/app/id6815160850"><img src="documentation/images/badge_ios.png" alt="Campfire for iOS and iPadOS" height="32px" /></a>
<a href="https://apps.apple.com/app/id6815160850"><img src="documentation/images/badge_macos.png" alt="Campfire for macOS" height="32px" /></a>
<a href="https://apps.microsoft.com/detail/9MSR62F288M1"><img src="documentation/images/badge_windows.png" alt="Campfire for Windows" height="32px" /></a>
<a href="https://github.com/pandulapeter/campfire/releases/latest"><img src="documentation/images/badge_linux.png" alt="Campfire for Linux" height="32px" /></a>
<a href="https://campfire-songbook.com/app/"><img src="documentation/images/badge_web.png" alt="Campfire in the browser" height="32px" /></a>

## Under the hood

Campfire is a [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) project targeting Android, iOS, desktop (JVM) and the web (Kotlin/Wasm).
The whole interface is written once with Compose Multiplatform and Material 3 Expressive, with navigation handled by Navigation 3 and dependencies wired together at compile time using Koin Annotations.
Platform-specific code is limited to thin shells: file pickers, system bars, share and "open with" intents, and the storage behind the library, which is a plain folder on Android, iOS and desktop and the Origin Private File System in the browser.

The code is split into small modules with a strict `api` / `implementation` boundary at every layer: a `presentation` module with the screens and the ViewModel, a `domain` layer of single-method use cases, repositories on top of local and remote data sources, and the `app` modules that hold each platform's entry point.
The ChordPro parser, transposer, and serializer live in a dependency-free `chordpro` module, so the format logic can be tested and reused independently of any UI or storage.
The metronome is a self-contained `metronome` module of its own: every click is placed by sample count rather than by a timer, so it never drifts, and its sounds are synthesized in Kotlin behind one audio output per platform. The `tuner` module beside it reads the microphone with the McLeod pitch method and plays its reference tones the same way.

Everything the app owns is a plain file: songs are `.cho` files and setlists are small JSON documents, so nothing is locked inside a database.
Syncing is a pure planning step over file hashes and a remote listing, run against a provider interface that Dropbox implements through OAuth with PKCE, which is why no backend of Campfire's own is needed.

## License

Copyright (c) Péter Pandula 2017-2026. This software is licensed under the
[Mozilla Public License 2.0](LICENSE).

The MPL is a file-level copyleft license, which means you are welcome to reuse any part of Campfire — the ChordPro
parser, the sync engine, a single screen — in your own project, open source or not. If you modify one of the files in
this repository, that file has to stay under the MPL and its source has to be available; anything new you write
alongside it is yours, under whatever license you choose.

The name *Campfire*, the app icon and the other branding assets are **not** covered by the license and remain the
author's. The MPL grants no trademark rights (section 2.3), so a fork has to ship under a name and an icon of its own.

### Third-party software

Campfire is built with the work of others, which keeps its own license. The full list, with each component's copyright
and license, is in [documentation/THIRD_PARTY_SOFTWARE.md](documentation/THIRD_PARTY_SOFTWARE.md).
