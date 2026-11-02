<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->


# :presentation — ui/components/labels

- `TagFlowRow` / `TagPill` are the one place a tag is drawn. A song row's labels are one sideways-scrolling line whose
  edge fades mask with `BlendMode.DstIn` in an offscreen layer (fading to the card, not punching through).
- Labels on **every song in the library** are omitted, counted over the whole library, not the filtered list, or a
  filter would hide the label it filters by.
- Tapping a row's tag or language toggles its filter chip without scrolling; no row chip edits tags. Setlist rows show
  no labels.
- Pills are outlined (only a selected row pill is filled). Languages carry their own icon, since "Magyar" is otherwise
  ambiguous.
- `TagIcon` fills the mark with the tag's color under an outline in the content color, so color never stands alone.
  `TagColor`'s ids are what devices store; an unknown id draws as no color.
- "Unknown" language stays last in any sorting.
- `LANGUAGE_CODES` (ISO 639-1 plus the 639-2/3 codes it lacks) is all the app ships, since no browser enumerates
  languages. `languageName` asks the platform in the app's language, capitalizes it (CLDR writes `angol`), falls back
  to English, then to the code in caps.
