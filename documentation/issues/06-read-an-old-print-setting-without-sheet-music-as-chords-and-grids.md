<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Read a print setting saved before "Sheet music" existed as the old "Chords and grids" choice

**Kind:** bug (persistence) · **Severity:** medium · **Platforms:** all
**Files:** `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/model/UserPreferencesDocument.kt`,
`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/mapper/UserPreferencesMappers.kt`,
`data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/mapper/UserPreferencesMappersTest.kt`

**Challenged:** amended — the preferences `Json` (`UserPreferencesDocumentFormat`) leaves `encodeDefaults` off, so 5.2.0 (released with e42fe070a) never wrote `showSheetMusic: true`: a missing key means "before 5.2.0" *or* "5.2.0 with the box ticked". The plan now states that ambiguity and the trade-off taken, why the nullable field fixes it from the next save on, the compatibility both ways, and the KDoc to add.

## Problem

At cfcf08150 unticking "Chords and grids" in the PDF options also left every verbatim run out of the PDF (ABC tunes,
engraved or as text, LilyPond, SVG): `PrintRows` had `FoldableKind.VERBATIM -> if (options.showChords) …`. e42fe070a
gave sheet music a checkbox of its own, `PrintSettings.showSheetMusic`, and `PrintRows` / `PrintNotations` now read only
that. The stored field defaults to true:

```kotlin
val showTabs: Boolean? = null,
val showSheetMusic: Boolean = true,
…
showTabs = showTabs ?: showChords,
showSheetMusic = showSheetMusic,
```

A user who printed lyrics only (`showChords = false`) upgrades with a `preferences.json` that has no `showSheetMusic`
key, which decodes as true: their PDFs now hold staves and notation text they had switched off. Tablature, split off
the same way earlier, was migrated (`showTabs ?: showChords`); sheet music was not.

## Fix

Make the document field `val showSheetMusic: Boolean? = null` and map it as `showSheetMusic = showSheetMusic ?: showChords`,
exactly as `showTabs` is, with the same kind of KDoc ("Null in a document from before sheet music had a box of its own,
when [showChords] printed it, so that its choice still holds."). The writing side (`showSheetMusic =
settings.showSheetMusic`) stays. Nothing else reads the document type (print settings are not synced or exported).

How the file is written matters (`UserPreferencesDocumentFormat`'s `Json`: `ignoreUnknownKeys`, `coerceInputValues`,
`encodeDefaults` left off, `explicitNulls` left on):

- Today a field equal to its default is **omitted**, so 5.2.0 wrote `showSheetMusic` only when it was `false`. A file
  with no key is therefore either from before 5.2.0 or from 5.2.0 with the box ticked. With `?: showChords`, the second
  kind with chords unticked reads as sheet music off. Accept it: 5.2.0 shipped on 2026-10-28, so those files are few,
  whereas every lyrics-only user upgrading from 5.1.x and earlier is affected the other way; and the box is visible in
  the PDF options, one tick away. Note the trade-off in the KDoc's last sentence or a comment beside the mapping.
- With the default `null`, a non-null value is never equal to the default, so from the next save the key is always
  written, `true` included, and the ambiguity ends. The value written is never null (`PrintSettings.showSheetMusic`
  is a `Boolean`), so `explicitNulls` never writes a `null`.
- Older versions reading a newer file: the key is still a JSON boolean, which 5.2.0 decodes as before; versions before
  5.2.0 ignore it (`ignoreUnknownKeys`). A `null` hand-written into the file decodes to the default (`coerceInputValues`),
  i.e. the `showChords` fallback.

## Tests

`UserPreferencesMappersTest`: a document decoded from JSON with `"showChords": false` and no `showSheetMusic` maps to
`showSheetMusic = false`, and one with `"showChords": true` and none to true; an explicit `showSheetMusic` wins over
`showChords` both ways. Decode real JSON text through `UserPreferencesDocumentFormat` (the round-trip test cannot catch
a missing key), and check that encoding settings with `showSheetMusic = true` now writes the key.
