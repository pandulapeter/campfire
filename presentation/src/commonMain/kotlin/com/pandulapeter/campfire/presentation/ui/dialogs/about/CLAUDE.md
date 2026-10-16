<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :presentation — ui/dialogs/about

The open source licenses, What's new, and the add songs hint (`AddSongsHintDialog`, see `ui/screens/songs`).

### Open source licenses (`OpenSourceLicensesSheet.kt`)

- **A build lists only what it ships** (`OpenSourceComponent.builds` against `appBuild`, which follows the screenshot
  tool's impersonation); the README lists every build's.
- **The list is kept by hand**: see the root `CLAUDE.md`'s Build section. License texts live under
  `composeResources/files/licenses` of the source set of the builds that show them (Google's 220 KB Play list is the
  Android build's alone). `OpenSourceComponentsTest` checks every build has every text it lists, and the order.
- The list was compiled from every artifact on the Android, desktop, and web runtime classpaths (jars and AARs searched
  for NOTICE and license files) and from Skiko's native libraries (Skia `third_party`, found with `strings`): Windows
  and the web lack the raw image readers (piex, DNG SDK), and only Linux and the web carry FreeType.
- An open entry's texts are list items cut at blank lines (`licenseTextChunks`): some run to thousands of lines.

### What's new

- **What's new** (`WhatsNewDialog`; when it shows is `ui/firstRun/`): the version is recorded once the dialog has stayed
  up a few seconds (`onWhatsNewShown`) or as it closes, not on its first frame, since on Android Play's answer can
  arrive after it opened and the update-required screen then ends the process. Body: `• **Headline** Description`
  bullets separated by `\n`.
