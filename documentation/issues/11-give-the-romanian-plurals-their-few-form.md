<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Give the Romanian plurals their "few" form, so counts of 20 and more read "20 de cântece"

**Kind:** translation · **Severity:** low · **Platforms:** all
**Files:** `presentation/src/commonMain/composeResources/values-ro/strings.xml`, a new
`presentation/src/desktopTest/kotlin/com/pandulapeter/campfire/presentation/localization/PluralItemsTest.kt`
**Challenged:** amended — the existing `PluralRulesTest` checks only the rules' categories, never that a string table
has the items, so a test is added; `tuner_cents` and `song_details_playing_key_transposed` get signed whole numbers,
not decimals (the reasoning about decimals is dropped; CLDR puts decimals under `few` anyway).

## Problem

Romanian takes "de" between a number and its noun from 20 on (CLDR: `one` for 1; `few` for 0, 2–19, and 101–119;
`other` for the rest). Since fffe989b5 `PluralRules.kt` picks `few` for Romanian, but all 51 Romanian `<plurals>` have
only `one` and `other`, so every count falls back to `other`, which is worded without "de": "20 cântece importate"
where Romanian needs "20 de cântece importate". Not a regression (the output is what it was), but the picker now makes
it fixable.

## Fix

For each `<plurals>` in `values-ro/strings.xml`: copy today's `other` item into a new `few` item unchanged, and rewrite
`other` with "de" between the counted number and its noun or nominal adjective: "%1$d de cântece importate", "Acum %1$d
de zile", "Apare de %1$d de ori", "Cele %1$d de cântece ale listei…". Judge each item:

- Only the number the plural counts takes "de". In `settings_sync_deletions_pending` and
  `settings_sync_remote_deletions_pending` ("%1$d din cele %2$d fișiere…") the count is `%1$d`, and "de" before
  "fișiere" depends on `%2$d`, which no item can know: leave those two as they are.
- `tuner_reading_flat` / `tuner_reading_sharp` count `%2$d` ("cu %2$d de cenți"); `tuner_cents` and
  `song_details_playing_key_transposed` format the number as `%1$s`, but it is a signed whole number (`signedCents`:
  "+20", "−5"; `transpositionLabel`), counted by its absolute value, so "+20 de cenți" is right: give them `few` and
  `other` like the rest (a file transposition never reaches 20, so that `other` is never shown, but keep the table
  uniform).
- Where no noun follows the number ("%1$d deja în bibliotecă", "%1$d omise") write "%1$d de fișiere deja în bibliotecă"
  only if it reads naturally; otherwise leave `other` as it is.
- `import_replace_message` has no number: leave it.

Keep each `<plurals>`' items in the order one, few, other.

## Tests

`PluralRulesTest` (`presentation/src/commonTest/.../localization/`) already checks the Romanian rule (1 one; 0, 2–19,
101–119 few; 20, 21, 100, 120 other) but no test reads the string tables, so add `PluralItemsTest` in
`presentation/src/desktopTest` (JVM, reading `src/commonMain/composeResources/values*/strings.xml` by relative path as
`ScriptFaceFilesTest` reads the fonts, parsed with the JDK's XML parser): every `<plurals>` in every table has `one` and
`other`, and every Romanian one also has `few`. (Do not generalize to "every category the rule yields": three Hebrew
plurals deliberately have no `two`, and no Spanish/Italian/French plural has `many`.) Run `:presentation:desktopTest`.
