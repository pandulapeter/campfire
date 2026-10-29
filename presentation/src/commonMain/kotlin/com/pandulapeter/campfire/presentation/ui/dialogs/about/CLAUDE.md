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

The open source licenses and What's new.

### Open source licenses (`OpenSourceLicensesSheet.kt`, `OpenSourceLicenseSheet.kt`)

- The list is plain rows; selecting one stacks that entry's sheet over it (links, NOTICE, license), so closing it finds
  the list where it was. The list reads every text as it opens and hands an entry's to its sheet, which then opens
  whole; one selected before that reads its own.
- **A build lists only what it ships** (`OpenSourceComponent.builds` against `appBuild`, which follows the screenshot
  tool's impersonation); `documentation/THIRD_PARTY_SOFTWARE.md` lists every build's.
- Google's 220 KB Play license list is the Android build's alone. `OpenSourceComponentsTest` checks every build has
  every text it lists, and the order.
- Skiko's native libraries differ: Windows and the web lack piex and the DNG SDK, and only Linux and the web carry
  FreeType.
- An entry's texts are list items cut at blank lines (`licenseTextChunks`): some run to thousands of lines.

### What's new

- **What's new** (`WhatsNewDialog`; when it shows is `ui/firstRun/`): the version is recorded once the dialog has stayed
  up a few seconds (`onWhatsNewShown`) or as it closes, not on its first frame, since on Android Play's answer can
  arrive after it opened and the update-required screen then ends the process. Body: `• **Headline** Description`
  bullets separated by `\n`.
