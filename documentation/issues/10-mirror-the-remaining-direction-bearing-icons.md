<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Mirror the remaining direction-bearing icons in right-to-left languages

**Kind:** bug · **Severity:** low · **Platforms:** all
**Files:** `presentation/src/commonMain/composeResources/drawable/` (`ic_label.xml`, `ic_label_fill.xml`,
`ic_open_in_new.xml`, `ic_manage_search.xml`, `ic_setlists_remove.xml`, `ic_prettify.xml`, `ic_select.xml`; only those
the check below confirms), `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/theme/CLAUDE.md` (its "Icons" section)
**Challenged:** amended — the doc to update is named (theme `CLAUDE.md`'s Icons section, which already lags: it lists
back, previous/next, undo/redo but not the sort, sound, and log icons b395d1dd6 mirrored); `ic_label` is Material's
diagonal `local_offer`/`sell` tag, not the horizontal `label` that Material mirrors, so the expectation for it is
reversed; `ic_label` and `ic_label_fill` must change together.

## Problem

b395d1dd6 set `android:autoMirrored="true"` on back, previous/next, undo/redo, sort, sound, and log icons. Other icons
whose shape points along the reading direction were left alone, so in Hebrew a tag icon points toward its text, an
open-in-new arrow points the wrong way, and list-shaped icons (lines with a trailing mark) read backwards.

## Fix

First check each candidate against Material's own right-to-left list: the icons Material ships under
`Icons.AutoMirrored` (material-icons-extended's `androidx.compose.material.icons.automirrored` package, or the Material
Symbols "Mirror in RTL" flag on fonts.google.com/icons). Identify each drawable's Material
counterpart by its `pathData` (`ic_label` starts `M21.41 11.58L12.41 2.58`, the diagonal `local_offer`/`sell` tag;
`ic_manage_search` is `manage_search`; `ic_select` is `checklist`; `ic_setlists_remove` is `playlist_remove`;
`ic_prettify` is three left-aligned bars like `notes`/`short_text`; `ic_open_in_new` is `open_in_new`). Add
`android:autoMirrored="true"` to each drawable whose Material counterpart is mirrored (expected: manage search and the
list-shaped ones; `local_offer` and `open_in_new` are *not* mirrored in Material, so leave the label pair and open in new
unless the check says otherwise; if the label is mirrored, mirror `ic_label_fill` with it, since it is drawn under the
outline in `TagIcon`). Leave play, search, chord, metronome, and every icon that is not in the list alone. Rewrite the
theme `CLAUDE.md` Icons line to name the whole mirrored set (back, previous/next, undo/redo, sort, sound, log, and what
this adds), keeping "which Compose honors on every platform for RTL".

## Tests

Golden renders (`tools/screenshots`) in Hebrew that show one of the changed icons are updated if any changes; nothing
else can test a drawable flag.
