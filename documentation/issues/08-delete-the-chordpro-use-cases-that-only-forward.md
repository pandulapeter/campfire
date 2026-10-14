<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Delete the `:chordpro` use cases that only forward, and give `:presentation` the one preference-to-`:chordpro` mapper

**Kind:** structure  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — every one of the eleven implementations was re-read at HEAD and only forwards (the only
defaults are `ParseChordProUseCase`'s `notation = STANDARD`, which `ChordProParser.parse` has too, and the two
preference mappers, which move); fixed the count of importing files (17, not 16), the claim that the test fakes were
all the real functions (two were `error("Not used")`), named the one KDoc gap (`setLanguages`), and stated the order
against plans 12 and 13, which move files this plan edits.
**Files:**
`domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/useCases/{ConvertChordProNotation,ConvertChordProTextNotation,NormalizeLanguageCode,ParseChordPro,PrettifyChordPro,SetChordProCoverArt,SetChordProLanguages,SetChordProLinks,SetChordProMetadata,TransposeChordProText,TransposeChordPro}UseCase.kt` (deleted),
the eleven matching `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/*UseCaseImpl.kt` (deleted),
`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/mapper/NotationMappers.kt` and `AccidentalsMappers.kt` (deleted),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/mapper/ChordProMappers.kt` (new),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chords/SongChords.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/rendering/SongRenderer.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/{SongMetadataEditing,SelectionEditing,CoverArtSearchController}.kt`,
the 17 other `presentation/src/commonMain` files importing `ui.chords.toChordNotation` (listed under Fix, commit 1),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/rendering/TestSongRenderer.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/{SelectionEditingTest,SongMetadataEditingTest}.kt`,
`domain/api/CLAUDE.md`, `domain/implementation/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/CLAUDE.md`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/CLAUDE.md`,
`.github/scripts/size_allowances.json`

User decision 1 (2026-10-14): `:presentation` may call pure `:chordpro` functions directly; a use case that only
forwards to one is deleted.

## Problem

Fourteen use cases sit in front of `:chordpro` or pure text logic. Eleven of them do nothing but forward one call,
some after translating a preference with a `when`; `:presentation` already depends on `:chordpro`
(`implementation(project(":chordpro"))` in `presentation/build.gradle.kts`) and already calls it directly in many places
(`ChordProPrettifier.prettifiedOffset` in `EditorTextReplacement.kt`, `ChordProNotation`, `ChordProTabs`,
`ChordProSummaryCache` in `SongRenderer.kt`), so the wrappers are only indirection: a Koin definition each, constructor
parameters threaded through the view model, and test fakes that re-implement the wrapper by calling the very same
`:chordpro` function (`TestSongRenderer.kt`: "use cases … stood in for by the `:chordpro` objects their
implementations delegate to").

The wrappers, each with its whole implementation body at HEAD:

| Use case | Implementation | Verdict |
|---|---|---|
| `ConvertChordProNotationUseCase` | `ChordProNotation.toNotation(song, spelling.notation.toChordNotation())` | forwards (with the notation mapper) → delete |
| `ConvertChordProTextNotationUseCase` | `ChordProNotation.convertText(text, from.toChordNotation(), to.toChordNotation())` | forwards → delete |
| `NormalizeLanguageCodeUseCase` | `ChordProLanguages.code(value)` | forwards → delete |
| `ParseChordProUseCase` | `ChordProParser.parse(text, notation.toChordNotation())` (interface default `notation = STANDARD`) | forwards → delete |
| `PrettifyChordProUseCase` | `ChordProPrettifier.prettify(text)` | forwards → delete |
| `SetChordProCoverArtUseCase` | `ChordProCoverArt.set(text, url)` | forwards → delete |
| `SetChordProLanguagesUseCase` | `ChordProLanguages.setLanguages(text, codes)` | forwards → delete |
| `SetChordProLinksUseCase` | `ChordProLinks.setLinks(text = text, links = links)` | forwards → delete |
| `SetChordProMetadataUseCase` | `ChordProMetadataFields.set(text = text, values = values)` | forwards → delete |
| `TransposeChordProTextUseCase` | `ChordProTransposer.transposeText(text, semitones, accidentals.toPreferFlats())` | forwards (with the accidentals mapper) → delete |
| `TransposeChordProUseCase` | `ChordProTransposer.transpose(song, semitones, accidentals.toPreferFlats(), tabPlayer)` | forwards → delete |
| `SetChordProTagUseCase` | `ChordProTags.addTag/removeTag(text, tag, fold = String::normalizedToNfc)` | **adds** the library's NFC fold (a `:data:model` rule `:chordpro` cannot know; `chordpro/CLAUDE.md`: "The caller supplies NFC normalization and tag folding") and has its own test (`SetChordProTagUseCaseImplTest`) → **keep** |
| `NormalizeTextUseCase` | its own accent-folding loop over `:data:model`'s `withoutAccent` | not a `:chordpro` wrapper; own logic, also used inside the domain (`GetScreenDataUseCaseImpl`) → **keep** |
| `NormalizeSearchTextUseCase` | `NormalizeTextUseCase` plus its own character filter | own logic, tested → **keep** |

The notation mapper exists twice: `domain/implementation/.../mapper/NotationMappers.kt`

```kotlin
internal fun UserPreferences.Notation.toChordNotation() = when (this) {
    UserPreferences.Notation.STANDARD -> ChordNotation.STANDARD
    ...
```

and, word for word, `presentation/.../ui/chords/SongChords.kt`

```kotlin
/** `:chordpro`'s notation for the one the preferences name. */
internal fun UserPreferences.Notation.toChordNotation() = when (this) {
```

and the accidentals mapper (`AccidentalsMappers.kt`, `toPreferFlats()`) is copied a third time as the private
`UserPreferences.Accidentals.preferFlats` in `TestSongRenderer.kt`. Once the five wrappers above that use them are gone,
nothing in `:domain:implementation` calls either domain mapper (checked: `git grep "toChordNotation()\|toPreferFlats()"`
lists only those five implementations in the domain).

Consumers at HEAD:

- `SongRenderer` (`ui/rendering/SongRenderer.kt`, `@Single`) takes nine: `parseChordPro`, `transposeChordPro`,
  `transposeChordProText`, `convertChordProNotation`, `convertChordProTextNotation`, `prettifyChordPro`, `normalizeText`,
  `normalizeSearchText`, `normalizeLanguageCode`.
- `CampfireViewModel` takes `setChordProCoverArt`, `setChordProLanguages`, `setChordProTag`, `setChordProLinks`,
  `setChordProMetadata`, `normalizeSearchText`, `parseChordPro` and hands them to `SongMetadataEditing` (parse, cover,
  languages, tag, links, metadata), `SelectionEditing` (parse, tag, languages), and `CoverArtSearchController` (parse).
- Tests: `TestSongRenderer.kt` (fakes for all nine), `SelectionEditingTest.kt` (parse, tag, languages fakes),
  `SongMetadataEditingTest.kt` (parse, cover, languages, tag, links, metadata fakes).
- Docs: `domain/api/CLAUDE.md` ("Notation: `ConvertChordProNotationUseCase` is the last rendering step, its text
  variant the editor's two boundaries; `ParseChordProUseCase` takes the notation its text is in"),
  `domain/implementation/CLAUDE.md` ("`:chordpro` use cases (`Transpose*`, `Parse*`, `Convert*Notation`, `Prettify*`,
  `SetChordPro*`) are kept where a preference is translated for `:chordpro` …"), `ui/dialogs/CLAUDE.md` (language
  picker "via `NormalizeLanguageCodeUseCase`"), `ui/screens/songDetails/CLAUDE.md` ("Writes only the fields changed
  (`SetChordProMetadataUseCase`)").
- `app/android/src/main/generated/baselineProfiles/*.txt` name the old classes and `SongRenderer`'s old constructor; a
  stale profile is only less useful, never wrong, and the prepare-release skill regenerates it — leave it.

## Fix

**Order:** before plans 11, 12, 13, 14 and 15. 12 moves `dialogs/` files and 13 the three `ui/state/` classes this plan
edits (and their tests); 11 splits `screens/songDetails/CLAUDE.md`, 14 rewrites the `:chordpro` imports this plan adds,
and 15 would move the use cases this plan deletes. 11, 14 and 15 say so themselves; 12 and 13 do not, so the
README's lane order has to. Plan 01 also
shrinks `CampfireViewModel.kt`: whichever of the two lands second runs `check_sizes.py --update` again.

Behaviour is preserved: every deleted call is replaced by the exact `:chordpro` call its implementation made, with
the same mapping. Four commits, each compiling.

**Commit 1 — one mapper file in `:presentation`.** Create
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/mapper/ChordProMappers.kt` (MPL header,
package `com.pandulapeter.campfire.presentation.mapper`) holding both translations, moved from the domain with their
KDoc:

```kotlin
/** The preference as `:chordpro` takes it, which depends on nothing and so knows no preferences. */
internal fun UserPreferences.Notation.toChordNotation() = when (this) { ... }   // moved from ui/chords/SongChords.kt

/**
 * The preference as `:chordpro` takes it: null for the spelling the song itself asks for, which is what
 * `ChordProTransposer` works out from the key.
 */
internal fun UserPreferences.Accidentals.toPreferFlats(): Boolean? = when (this) { ... }  // from AccidentalsMappers.kt
```

Delete `toChordNotation` from `ui/chords/SongChords.kt` and change the import
`com.pandulapeter.campfire.presentation.ui.chords.toChordNotation` → `com.pandulapeter.campfire.presentation.mapper.toChordNotation`
in every file that has it (HEAD: `dialogs/ChordReferenceSheet.kt`, `dialogs/ChordShapeSheet.kt`, `dialogs/TunerSheet.kt`,
`dialogs/TuningSheet.kt`, `rendering/SongRenderer.kt`, `screens/chords/ChordsScreen.kt`,
`screens/export/ExportController.kt`, `screens/export/PrintLabelsResource.kt`, `screens/onboarding/OnboardingScreen.kt`,
`screens/onboarding/OnboardingSongPreview.kt`, `screens/settings/ChordsSection.kt`,
`screens/songDetails/SongDetailsPage.kt`, `screens/songDetails/SongDetailsScreen.kt`,
`screens/songEditor/LoadedSongEditor.kt`, `screens/songEditor/SongPreview.kt`, `screens/tuner/TunerScreen.kt`,
`tabs/TabReading.kt`, and the tests `rendering/TestSongRenderer.kt`, `state/SelectionEditingTest.kt`,
`state/SongMetadataEditingTest.kt`); nothing in `ui.chords` itself calls it. In `TestSongRenderer.kt` delete the private `preferFlats` copy and use `toPreferFlats()`.

Options considered for where the one mapper lives:
- **(recommended)** `presentation/mapper/` at the module root: the root CLAUDE.md convention is "Layers are crossed
  through `mapper/` packages", it sits outside `ui/` so plans 11–13's package restructure does not move it again, and
  after commit 4 it is the only translation left in the project.
- Keep it in `ui/chords/SongChords.kt` and put `toPreferFlats` beside it: fewer import changes, but a notation mapper in
  a chord-diagram file is where the duplicate came from.
- Make the domain's mappers public in `:domain:api`: keeps them next to `UserPreferences`' other consumers, but the
  domain no longer uses them, and `:domain:api` would export non-use-case functions.

**Commit 2 — `SongRenderer` calls `:chordpro`.** Drop the seven forwarding parameters; keep `normalizeText` and
`normalizeSearchText`. Replace each call:

- `parseChordPro(text, writtenIn)` → `ChordProParser.parse(text, writtenIn.toChordNotation())`
- `transposeChordPro(selected, n, spelling.accidentals, player)` → `ChordProTransposer.transpose(selected, n, spelling.accidentals.toPreferFlats(), player)` (also inside `renderKey`)
- `convertChordProNotation(song, spelling)` → `ChordProNotation.toNotation(song, spelling.notation.toChordNotation())` (in `notatedSong`, `renderKey`, `editorKeyOf`)
- `transposeChordProText(text, semitones, accidentals)` → `ChordProTransposer.transposeText(text, semitones, accidentals.toPreferFlats())`
- `convertChordProTextNotation(text = t, from = a, to = b)` → `ChordProNotation.convertText(t, a.toChordNotation(), b.toChordNotation())` (in `transposeText`, `editorTextOf`, `fileTextOf`)
- `prettifyChordPro(text)` → `ChordProPrettifier.prettify(text)`
- `normalizeLanguageCode(value)` → `ChordProLanguages.code(value)`

`editorKeyOf` built a throwaway `ChordSpelling.Default.copy(notation = notation)` only to feed the use case; call
`ChordProNotation.toNotation(song, notation.toChordNotation())` directly instead (same result: the use case read only
`spelling.notation`). Update the class KDoc ("pure functions of their arguments over stateless use cases" → "… over
`:chordpro` and two text use cases") and `renderKey`'s ("Both use cases rewrite the key …" → "Both steps …").
`TestSongRenderer.kt` shrinks to `SongRenderer(normalizeText = …, normalizeSearchText = …)` with the two existing
fakes; delete its now-wrong KDoc sentence about stood-in use cases. Note for the tests: `prettifyText` and
`languageCode` were identity / `lowercase()` fakes and are now the real functions; no test calls either through
`testSongRenderer()` at HEAD (`git grep "prettifyText(\|languageCode("` in `presentation/src/commonTest` is empty), so
no expectation changes.

**Commit 3 — the state classes and the view model.**

- `SongMetadataEditing`: drop `parseChordPro`, `setChordProCoverArt`, `setChordProLanguages`, `setChordProLinks`,
  `setChordProMetadata` constructor parameters (keep `setChordProTag`). `parseChordPro(text)` →
  `ChordProParser.parse(text)` (every call here uses the default notation; the one at the end,
  `parseChordPro(songRenderer.fileTextOf("{key: $typed}", editorNotation())).metadata.key`, too);
  `setChordProCoverArt(text = text, url = url)` → `ChordProCoverArt.set(text, url)`;
  `setChordProLanguages(text = text, codes = codes)` → `ChordProLanguages.setLanguages(text, codes)`;
  `setChordProLinks(text = text, links = …)` → `ChordProLinks.setLinks(text = text, links = …)`;
  `setChordProMetadata(text = text, values = changed)` → `ChordProMetadataFields.set(text = text, values = changed)`.
- `SelectionEditing`: drop `parseChordPro` and `setChordProLanguages` (keep `setChordProTag`); same replacements.
- `CoverArtSearchController`: drop `parseChordPro`; `parseChordPro(text).metadata` → `ChordProParser.parse(text).metadata`.
- `CampfireViewModel`: drop the constructor parameters `setChordProCoverArt`, `setChordProLanguages`,
  `setChordProLinks`, `setChordProMetadata`, `parseChordPro` and their imports, and the arguments passed on to the three
  classes above. `setChordProTag` and `normalizeSearchText` stay. The file shrinks, so run
  `python3 .github/scripts/check_sizes.py --update` in this commit (it lowers `CampfireViewModel.kt`'s 1,707-line
  allowance).
- Tests: in `SelectionEditingTest.kt` and `SongMetadataEditingTest.kt` delete the fakes of the removed parameters and
  their imports (`ParseChordProUseCase`, `SetChordProLanguagesUseCase`, `SetChordProCoverArtUseCase`,
  `SetChordProLinksUseCase`, `SetChordProMetadataUseCase`, and `toChordNotation` / `ChordProParser` /
  `ChordProLanguages` where now unused). The fakes were the real functions, apart from `SongMetadataEditingTest`'s
  `setChordProCoverArt` and `setChordProLanguages`, which were `error("Not used")`: no test there reaches them (the
  cover and language writes are not exercised), so every assertion holds unchanged, but a future test that does now
  runs the real function rather than failing — which is the point.

**Commit 4 — delete the use cases.** Delete the eleven interfaces in `domain/api/.../useCases/` and their
`*UseCaseImpl.kt` files, and `mapper/NotationMappers.kt` and `mapper/AccidentalsMappers.kt` (`mapper/DocumentMappers.kt`
stays). Nothing registers them by hand: they were `@Factory` classes found by `@ComponentScan`, and `:app:di`'s
compile-time graph check confirms nothing still asks for one. `:domain:api` keeps `api(project(":chordpro"))`:
`CreateSongUseCase` and `DuplicateSongUseCase` still name `:chordpro` types.

Check whether anything in the deleted interfaces' KDoc is not already said by the `:chordpro` function it forwarded to
(the KDoc of `ChordProNotation.toNotation` / `convertText` (with `ChordNotation`'s for the `H` rule),
`ChordProTransposer.transpose`'s `preferFlats`, `ChordProCoverArt.set`, `ChordProLinks.setLinks`, `ChordProLanguages.code`
and `ChordProMetadataFields.set` carry every rule at HEAD); move any sentence that is missing to that function's KDoc
rather than losing it. One is known to be missing: `SetChordProLanguagesUseCase`'s "the codes are normalized on the way
in — folded to lower case and cut down to their primary subtag … a code that names no language (`und`, `zxx`, an empty
string) is dropped rather than written" and "a language the document already declares keeps the line and the spelling
it is written on" are not in `ChordProLanguages.setLanguages`' KDoc; add them there (checking each against the code).

Docs in the same commit:
- `domain/api/CLAUDE.md`: replace the "Text-level `SetChordPro*` use cases …" bullet with one on `SetChordProTagUseCase`
  only (tag matching ignores case and folds NFC); move the language-code and byte-for-byte facts out (they are
  `chordpro/CLAUDE.md`'s already: "Text edits keep every other byte", "Language codes are normalized"); delete the
  "Notation: `ConvertChordProNotationUseCase` …" bullet (the rule is `chordpro/CLAUDE.md`'s "Notation" section); keep
  "Transposition takes `UserPreferences.Accidentals`" only if a remaining use case does — none does, so delete it. Line
  12 ("Exposes `:chordpro` as `api` so the UI can render a `ChordProSong`") becomes "… because `CreateSongUseCase` and
  `DuplicateSongUseCase` take `:chordpro` types; `:presentation` calls `:chordpro`'s pure functions directly (no use case
  only forwards to one)."
- `domain/implementation/CLAUDE.md`: replace the "`:chordpro` use cases (`Transpose*` …) are kept where a preference is
  translated …" bullet with: "`SetChordProTagUseCaseImpl` is the one `:chordpro` wrapper left, for the NFC fold;
  `:presentation` calls the rest directly and owns the preference translation (`presentation/mapper/ChordProMappers.kt`)."
- `ui/dialogs/CLAUDE.md`: "via `NormalizeLanguageCodeUseCase`" → "via `ChordProLanguages.code`".
- `ui/screens/songDetails/CLAUDE.md`: "(`SetChordProMetadataUseCase`)" → "(`ChordProMetadataFields.set`)".
- Grep afterwards for every deleted name across the repository except the baseline profiles:
  `git grep -n "ConvertChordPro\|ParseChordProUseCase\|PrettifyChordProUseCase\|SetChordProCoverArt\|SetChordProLanguages\|SetChordProLinks\|SetChordProMetadata\|TransposeChordPro\|NormalizeLanguageCode\|NotationMappers\|AccidentalsMappers" -- ':!app/android/src/main/generated'`
  must print nothing outside `documentation/` (old plans there are history; leave them).

## Tests

No new test: no logic is added, and every call is replaced by the exact function its fake already ran. The existing
`SongRendererTest`, `SongDetailsTitleTest`, `EditorSessionTest`, `DemoSongTabsTest`, `SelectionEditingTest` and
`SongMetadataEditingTest` now exercise the real `:chordpro` calls instead of copies of them. `SetChordProTagUseCaseImplTest`,
`NormalizeTextUseCaseImplTest` and `NormalizeSearchTextUseCaseImplTest` stay.

Verify with the root CLAUDE.md test command, plus `./gradlew :app:desktop:compileKotlin :app:android:compileDebugKotlin
:app:web:compileKotlinWasmJs :app:ios:linkDebugFrameworkIosSimulatorArm64` (the Koin graph check runs in `:app:di`'s
compilation, which each app compile includes), `./gradlew spotlessCheck`, and `python3 .github/scripts/check_sizes.py`.
