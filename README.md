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
It runs natively on Android, iOS, macOS, Windows, Linux and the web. 

<img src="documentation/screenshots/01.png" width="32%" /> <img src="documentation/screenshots/02.png" width="32%" /> <img src="documentation/screenshots/03.png" width="32%" />

The interface is designed around practical use, whether you’re organizing a song library, building setlists, or playing a gig.
Songs automatically adapt with multi-column layouts and customizable section ordering to make reading easier on stage.
It also includes on-the-fly transposition, adjustable font sizing, a lyrics-only view for singers, customizable themes, and a Performance mode to lock the app against accidental edits.

<img src="documentation/screenshots/04.png" width="32%" /> <img src="documentation/screenshots/05.png" width="32%" /> <img src="documentation/screenshots/06.png" width="32%" />

The app works offline, requires no account, and doesn't rely on a central server. Syncing the library across devices is handled directly through your own Dropbox storage.
Campfire is free, open-source, and has no ads or tracking. Check out the [Privacy Policy](https://pandulapeter.com/legal/privacy_policy-campfire.html) for more information.

<!-- Settings links to this heading's anchor (#get-campfire); renaming it means changing SettingsScreen.kt too. -->
## Get Campfire

Campfire is currently available for the following platforms:

<a href="https://play.google.com/store/apps/details?id=com.pandulapeter.campfire"><img src="documentation/images/badge_android.png" alt="Campfire for Android" height="32px" /></a>
<a href="https://github.com/pandulapeter/campfire/releases/latest"><img src="documentation/images/badge_linux.png" alt="Campfire for Linux" height="32px" /></a>
<a href="https://pandulapeter.com/campfire"><img src="documentation/images/badge_web.png" alt="Campfire in the browser" height="32px" /></a>

Other platforms coming really soon (under final review):

<a href="https://github.com/pandulapeter/campfire/releases/latest"><img src="documentation/images/badge_ios.png" alt="Campfire for iOS" height="32px" /></a>
<a href="https://github.com/pandulapeter/campfire/releases/latest"><img src="documentation/images/badge_macos.png" alt="Campfire for macOS" height="32px" /></a>
<a href="https://github.com/pandulapeter/campfire/releases/latest"><img src="documentation/images/badge_windows.png" alt="Campfire for Windows" height="32px" /></a>

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
