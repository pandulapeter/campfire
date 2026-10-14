<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Count the library's tags and languages once, in the domain, and match tags by `tagKey` everywhere

**Kind:** structure (one small decided behaviour change: the song picker's tag order)  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — commit 3 missed the three UI files that compare a selection against tag names
(`components/TagFilters.kt`, `components/SongLabels.kt`, `dialogs/SongPicker.kt`): with the domain filtering by
`tagKey` and the chips still by `.lowercase()`, a decomposed selected tag would filter the list while its chip showed
unselected and the clear action stayed hidden. Added them, said which `.lowercase()` keys are left on purpose, and
stated the order against plans 12 and 13, which move these files. The picker behaviour change itself checks out (same
source, `unfilteredSongs`; same whole-library count; only the equal-count tag order changes).
**Files:**
`domain/api/src/commonMain/kotlin/com/pandulapeter/campfire/domain/api/models/ScreenData.kt`,
`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/GetScreenDataUseCaseImpl.kt`,
`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/SongCatalog.kt`,
`domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/SongCatalogTest.kt`,
`domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/GetScreenDataUseCaseImplTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/search/SongPickerIndex.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/SongPickerState.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/SongListState.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/search/SongPickerIndexTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/TagFilters.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/SongLabels.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SongPicker.kt`,
`domain/implementation/CLAUDE.md`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/CLAUDE.md`

## Problem

The library's tags and languages are counted twice, by two copies of the same rule that already disagree.

1. **The songs screen** gets them from the domain. `GetScreenDataUseCaseImpl.toSongPart` counts the whole library with
   `SongCatalog.kt`'s `toTags(normalizeText::invoke)` / `toLanguages()` (`availableTags` / `availableLanguages`), then
   recounts them over the filtered songs for `ScreenData.tags` / `.languages`. Tag order:

   ```kotlin
   private fun tagOrder(normalize: (String) -> String) =
       compareByDescending<Tag> { it.songCount }.thenBy { normalize(it.name) }.thenBy { it.name }
   ```

   i.e. most used first, then accent- and case-insensitive (`NormalizeTextUseCase`), then the exact name.

2. **The song picker** (the setlist's Choose songs sheet) recounts the same whole library in `:presentation`:
   `SongPickerState.songPickerFilters = allSongs.map(::pickerFilterOptions)`, where `SongPickerIndex.kt`'s
   `pickerFilterOptions` re-implements counting, the first-file-name spelling, and the language order, but sorts tags by

   ```kotlin
   .sortedWith(compareByDescending<Tag> { it.songCount }.thenBy { it.name.lowercase() }),
   ```

   which is accent-sensitive: with equal counts, `Ébredés`, `Ebony`, and `Zene` are `Ebony, Ébredés, Zene` on the songs
   screen and `Ebony, Zene, Ébredés` in the picker (`é` sorts after `z` by code point). The two copies also differ on
   input the library never produces today (`Song.tags` is already `normalizedTags`, one spelling per tag): the picker
   counts a tag once per song and keeps a song's last spelling of it; `toTags` counts every occurrence and keeps the
   first (`SongPickerIndexTest`'s "counted once per song" expects `CHRISTMAS` from `listOf("Christmas", "CHRISTMAS")`).

   The domain's ordering is the right one: it is the order the songs screen's chips have always had, it matches how
   every other list in the app is sorted (`NormalizeTextUseCase`, "accents ignored", `domain/implementation/CLAUDE.md`),
   and the picker's own KDoc says it means to order "the way the songs screen's filters order them".

3. **Tag identity** is `tagKey` (`:data:model`'s `Tags.kt`: `tag.normalizedToNfc().lowercase()`, "What a tag is told
   apart from every other by"), used by the tag colors and the metadata editors, but the filter code compares by bare
   `.lowercase()`: `SongCatalog.kt` (`filterTags`, `recountedTagsOver`, `toTags`), `SongListState.kt`
   (`isSongFilterActive`, `clearTagFilter`, `clearSongFilter`, and `toggleTagFilter`'s `equals(tag, ignoreCase = true)`),
   `SongPickerIndex.kt` (`toPickableSong`'s `tags`), and `SongPickerState.kt` (the restored selection and
   `toggleSongPickerTag`). A tag spelled decomposed (a selection restored from saved state written on a Mac, for
   instance) is then a different tag to the filter than to the tag colors.

## Fix

**Order:** before plans 12 and 13, which move `components/`, `dialogs/` and `ui/state/` files this plan edits (neither
says so itself), and before 15 (which says so).

**Commit 1 — the domain counts the library once and hands it out.**

- `ScreenData`: add two fields after `languages`, defaulted like `sortedSongs`:

  ```kotlin
  /**
   * Every tag of the whole library, counted over all of it and ordered as [tags] is: what a control that ignores the
   * songs screen's filters offers (the song picker), so that it never counts the library a second time by a rule of
   * its own.
   */
  val libraryTags: List<Tag> = emptyList(),
  /** Every language of the whole library, counted over all of it and ordered as [languages] is; see [libraryTags]. */
  val libraryLanguages: List<SongLanguage> = emptyList(),
  ```

- `GetScreenDataUseCaseImpl`: add `libraryTags` / `libraryLanguages` to the private `SongPart`, filled with the
  `availableTags` / `availableLanguages` `toSongPart` already computes (no extra pass), `emptyList()` in
  `EMPTY_SONG_PART`, and passed into both `ScreenData(...)` constructions (the main one and `partialScreenData`).
- `SongCatalog.kt`: key tags by `tagKey` instead of `.lowercase()` in `filterTags`, `recountedTagsOver`, and `toTags`
  (import `com.pandulapeter.campfire.data.model.domain.tagKey`), and make `toTags` count each tag once per song, as
  `recountedTagsOver` already does: iterate `song.tags.distinctBy(::tagKey)` instead of `song.tags`. With the
  library's `normalizedTags` input this changes nothing; it makes the function agree with its own recount on any input.
  Update the file-level KDoc of `filterTags` ("folded to lower case" → "compared by `tagKey`").

**Commit 2 — the picker reads it.**

- `SongPickerState`: replace the `allSongs: StateFlow<List<Song>>` constructor parameter with
  `screenData: StateFlow<DataState<ScreenData>>` (`LibraryState.screenData`, which `LibraryState.allSongs` is mapped from; `CampfireViewModel` passes its private
  `screenData`, `libraryState.screenData`) and build

  ```kotlin
  val songPickerFilters = screenData
      .map { state -> state.data?.let { pickerFilterOptionsOf(it.libraryTags, it.libraryLanguages) } ?: PickerFilterOptions.Empty }
      .asState(scope, PickerFilterOptions.Empty)
  ```

  (no `flowOn`: it no longer walks the library). `allSongs` was used for nothing else in the class.
- `SongPickerIndex.kt`: replace `pickerFilterOptions(songs)` with the presentation-only rule that is left:

  ```kotlin
  /** [languages] is offered only where the library sings in more than one, since one has nothing to choose between. */
  internal fun pickerFilterOptionsOf(tags: List<Tag>, languages: List<SongLanguage>) =
      PickerFilterOptions(languages = if (languages.size > 1) languages else emptyList(), tags = tags)
  ```

  and update `PickerFilterOptions`' KDoc (counted by the domain, `ScreenData.libraryTags`).
- Equality: `PickerFilterOptions` is a data class and `asState` is a `StateFlow`, so a filter change that leaves the
  library's tags equal emits nothing to an open picker, as `allSongs` did.

**Commit 3 — one tag identity in `:presentation`.**

- `SongListState`: `isSongFilterActive`, `clearTagFilter`, `clearSongFilter` → `tagKey(it)` / `tagKey(it.name)` in place
  of `.lowercase()`; `toggleTagFilter` → `filterNotTo(mutableSetOf()) { tagKey(it) == tagKey(tag) }`; update its KDoc
  ("matched the way the filter itself matches it, by `tagKey`").
- `SongPickerIndex.toPickableSong`: `tags = song.tags.mapTo(mutableSetOf(), ::tagKey)`; KDoc of `PickableSong` ("in lower
  case" → "by `tagKey`").
- `SongPickerState`: the restored selection `.mapTo(mutableSetOf(), ::tagKey)` and `toggleSongPickerTag`'s
  `val key = tagKey(tag)`.
- `dialogs/SongPicker.kt`, which matches `SongPickerState`'s keys against the chips: `activeTags`'
  `filters.tags.any { it.name.lowercase() == selected }` → `tagKey(it.name) == selected`, and the chips'
  `val key = tag.name.lowercase()` → `tagKey(tag.name)`.
- `components/TagFilters.kt` (the songs screen's chips, over `SongFilter.selectedTags`): `selected`'s `it.lowercase()`
  → `tagKey(it)`, and `isPinned`'s, the chip `key`'s and `isSelected`'s `it.name.lowercase()` / `tag.name.lowercase()`
  → `tagKey(…)`. Without this a selected tag spelled decomposed narrows the list (the domain now matches it) while its
  chip reads unselected and the clear action is hidden (`selectedChipCount` 0) — `isSongFilterActive` would say the
  filter is on with no chip to turn it off.
- `components/SongLabels.kt`: `isSelected`'s `selectedTags.any { it.equals(tag, ignoreCase = true) }` →
  `selectedTags.any { tagKey(it) == tagKey(tag) }`, for the same reason.
- Left on purpose: `LabelsOnEverySong.kt` / `SongListItem.kt` and `TagsSheet.kt` key only the library's own spellings
  against each other (already NFC, `normalizedTags`, so `.lowercase()` equals `tagKey` there) and never meet a stored
  selection; switching them is harmless but not part of this fix.

Docs: `domain/implementation/CLAUDE.md`'s Screen data section gets one line: "`ScreenData.libraryTags` /
`libraryLanguages` are the whole library's counts the filters start from, for controls that ignore the filters (the
song picker); tags are compared by `tagKey` everywhere." In `ui/dialogs/CLAUDE.md` the song picker bullet's "Rows and
chips are built off the main thread once per library (`SongPickerIndex.kt`)" → "Rows are built off the main thread once
per library (`SongPickerIndex.kt`); the chips are the domain's `ScreenData.libraryTags` / `libraryLanguages`."

Behaviour change, decided here (domain order wins): the song picker's tag chips with equal counts are ordered
accent-insensitively, as the songs screen's are. Nothing else visible changes.

## Tests

- `SongCatalogTest` (domain): move in the two counting cases from `SongPickerIndexTest`, rewritten against
  `toTags` / `toLanguages`:
  - "tags are spelled by the first song by file name and counted once per song": same songs; the expectation for song
    `a.cho` with `listOf("Christmas", "CHRISTMAS")` becomes `Tag("Christmas", 3)` (first spelling, as `normalizedTags`
    keeps the first), `Tag("Rock", 2)`, `Tag("Solo", 1)`.
  - "languages are most used first with the unknown ones last": unchanged expectation, via `toLanguages()`.
  - new: "tags with equal counts sort without regard to accents": `Ébredés`, `Zene`, `Ebony` once each →
    `Ebony, Ébredés, Zene` with `NormalizeTextUseCaseImpl()::invoke`.
  - new: "a decomposed spelling is the same tag": `"café"` on one song and `"Café"` on another count as one tag
    with 2 songs.
- `SongPickerIndexTest`: delete the two moved tests; replace them with "a library in one language has no languages to
  offer" rewritten against `pickerFilterOptionsOf(tags = …, languages = listOf(SongLanguage("en", 2)))` → empty
  languages, and one that two languages pass through unchanged. The `pickable(...)` helper's `tags` uses `tagKey`.
- `GetScreenDataUseCaseImplTest`: one test that a tag filter narrows `ScreenData.tags`' counts but leaves
  `libraryTags` / `libraryLanguages` the whole library's.

Verify with the root CLAUDE.md test command, plus `:app:desktop:compileKotlin`, `:app:android:compileDebugKotlin`,
`:app:web:compileKotlinWasmJs`, `:app:ios:linkDebugFrameworkIosSimulatorArm64`, and `spotlessCheck`.
