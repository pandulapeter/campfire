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

How tags and languages are drawn, colored, and ordered.

### Tags and languages (`TagFlowRow.kt`, `TagPill.kt`)

- The one place a tag is drawn. In a song row (`SongLabels`) one line that scrolls sideways, edge fades masked with
  `BlendMode.DstIn` in an offscreen layer (to fade to the card, not punch through). Labels on **every song in the whole
  library** are omitted (`labelsOnEverySong`), counted over the whole library, not the filtered list, or a filter would
  hide the label it filters by.
- Tapping a row's tag or language toggles its filter chip without scrolling; no row chip edits tags. Setlist rows show
  no labels (`shouldShowLabels`).
- Pills are outlined (only a selected row pill is filled). Tags carry `ic_label`, languages `ic_language` — "Magyar" is
  otherwise ambiguous.
- `TagIcon` fills the mark with the tag's color under an outline in the content color, so color never stands alone; from
  `LocalTagColors`, provided once by `CampfireApp`. `TagColor`'s ids are what devices store; an unknown id draws as no
  color. Light/dark tones are chosen by surface luminance.
- `LabelSortingToggle` sorts tags and languages on one preference; "Unknown" stays last.

### Languages (`Languages.kt`)

- `LANGUAGE_CODES` (~620: ISO 639-1 plus the 639-2/3 codes it lacks; no browser enumerates languages) is all the app
  ships. `languageName` asks the platform in the app's language (reactive via `currentLanguage`), capitalizes it (CLDR
  writes `angol`), falls back to English, then to the code in caps.
- `pickableLanguages`: sorted through `NormalizeTextUseCase`, the library's own languages first. `SongLanguage.UNKNOWN`
  is named from string resources.
