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

- **A build lists only what it ships** (`OpenSourceComponent.builds` against `appBuild`, which follows the screenshot
  tool's impersonation); `documentation/THIRD_PARTY_SOFTWARE.md` lists every build's. Google's 220 KB Play license list
  is Android's alone. Skiko's native libraries differ: Windows and the web lack piex and the DNG SDK, and only Linux and
  the web carry FreeType. `OpenSourceComponentsTest` checks every text and the order.
- The list reads every text as it opens and hands an entry's to its sheet, which then opens whole. Texts are list items
  cut at blank lines, since some run to thousands of lines.
- **What's new** records the version once the dialog has stayed up a few seconds or as it closes, not on its first
  frame, since on Android Play's answer can arrive after it opened and the update-required screen then ends the
  process. Body: `• **Headline** Description` bullets separated by `\n`.
