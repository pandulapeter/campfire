# Read at most a fixed number of tags and languages per song, and make the language edits keep the ones past it

**Kind:** performance (UI freeze from a crafted or broken file) · **Severity:** medium · **Platforms:** all
**Files:**
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/syntax/ChordProMetadataBuilder.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/model/ChordProMetadata.kt` (the constants and KDoc of `tags` / `languages`),
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/edit/ChordProTags.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/edit/ChordProLanguages.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/songText/SongMetadataEditing.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/songs/SelectionEditing.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/labels/SongLanguagesDialog.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserTest.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/edit/ChordProTagsTest.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/edit/ChordProLanguagesTest.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/songText/SongMetadataEditingTest.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/songs/SelectionEditingTest.kt`,
`chordpro/CLAUDE.md`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CLAUDE.md` (Tags are part of the song file)

## Problem

The parser keeps every distinct tag and language a file declares (`ChordProMetadataBuilder.kt:145-152`):

```kotlin
private fun addTag(value: String) {
    if (tagKeys.add(ChordProMetaItems.caseInsensitiveKey(value))) tags += value
}

private fun addLanguage(value: String) {
    if (languageSet.add(value)) languages += value
}
```

`languageCode` (`ChordProMetaItems.kt:108-113`) accepts any non-empty string, so languages have no bound either. An
existing test even asserts that 100,000 are kept (`ChordProParserTest.kt:1008-1014`, a linearity guard for parsing).
Everything above the parser then draws them all. Verified by reading the code:

- **Every song row**: `SongLabels.kt:68-91` is a plain `Row` with `horizontalScroll`, not lazy. It composes a
  `TagPill` for every tag, each doing `selectedTags.any { tagKey(it) == tagKey(tag) }`, and one for every language.
  Only labels on every song in the library are left out (`labelsOnEverySong`).
- **Song details**: `SongInfoBody.kt:171-179` composes a `TagPill` per tag, and `:194` one per language.
- **The filters**: `ScreenData.libraryTags` is deduplicated by key across the library (`GetScreenDataUseCaseImpl`), so
  the distinct tags of one file all become chips. `CollapsibleChipFlow.kt:61-68` composes every chip (each
  `animateBounds`) and measures every one of them (`chipMeasurables.map { it.measure(…) }`), even when collapsed.
- The tags sheet offers every library tag (`offeredTagsOf`).

A 600 KB file of 50,000 `{tag: tN}` lines composes 50,000 pills in every visible row that shows it, and 50,000 filter
chips. It travels by sync, share links, and import to every device. (The 30-character cap, `TagsSheet.kt:247`, applies
only to typed input; it was accepted, and does not bound the count.)

## Fix

**Recommended: cap at parse time.** One place then bounds every reader: rows, details, filters, sheets, search index,
and `ScreenData` counts.

1. `ChordProMetadata`: `public companion object { public const val MAX_TAGS: Int = …; public const val MAX_LANGUAGES: Int = … }`.
   KDoc of `tags` / `languages`: "the first `MAX_TAGS` distinct ones in file order; the file keeps the rest".
2. `ChordProMetadataBuilder`:

   ```kotlin
   private fun addTag(value: String) {
       if (tags.size < ChordProMetadata.MAX_TAGS && tagKeys.add(ChordProMetaItems.caseInsensitiveKey(value))) tags += value
   }

   private fun addLanguage(value: String) {
       if (languages.size < ChordProMetadata.MAX_LANGUAGES && languageSet.add(value)) languages += value
   }
   ```

3. **Writers must not act on the capped view.** Both editors write text edits, never the model, so the file keeps
   whatever lies past the cap, except in the places below:
   - `ChordProTags.addTag` (`ChordProTags.kt:39`) decides "already carried" from `ChordProParser.parseMetadata(text).tags`.
     Past the cap, that would append a duplicate `{tag}` line. Scan the lines instead, the way `removeTag` already does:
     `ChordProLines.splitLines(text).any { line -> line.tag()?.let(fold)?.equals(key, ignoreCase = true) == true }`.
   - **Languages are rewritten as a whole set** (`ChordProLanguages.setLanguages` keeps exactly `codes`).
     `SongMetadataEditing.setSongLanguages` (`:81-83`) is given the dialog's ticked set, which comes from the capped
     `Song.languages`. `SelectionEditing.setSelectionLanguages` (`:61-65`) starts from
     `ChordProParser.parse(text).metadata.languages`. With a cap, both would **delete every language past it from the
     file** on any language edit. Fix:
     - Add `public fun declaredIn(text: String): List<String>` to `ChordProLanguages`: every language a directive line
       declares, in order and once each, through the existing private `String.language()` (no cap).
     - `SelectionEditing.setSelectionLanguages`: `val codes = ChordProLanguages.declaredIn(text)`.
     - `SongMetadataEditing.setSongLanguages(target, codes, offeredCodes)`, following `setSongTags` / `setSongLinks`:
       `val declared = ChordProLanguages.declaredIn(text); val edited = declared.filterNot { it in offeredCodes && it !in codes } + codes.filterNot { it in declared }`,
       then `if (edited == declared) text else ChordProLanguages.setLanguages(text, edited)`.
       `SongLanguagesDialog` passes `offeredCodes = dialog.song.languages`, which is what the sheet showed ticked.
       This also stops a language that sync added while the sheet was open from being dropped, the same rule tags and
       links already follow.
   - Tag removal (`setSongTags`, `setSelectionTags`) reads the capped list, so a tag past the cap is never removed.
     That fails safe (the file keeps it), so no change is needed.
4. Remaining trade-off: a song already at the cap that gets one more tag in the sheet has it written after the last
   tag, past the cap, so it disappears on the next read. The file still has it, so nothing is lost. With a cap far
   beyond human use this does not happen in practice. A lower cap needs the sheets to disable unticked rows at the
   cap (`TagsSheet`, `LanguagesSheet`, and the selection sheets), which adds work across several files.
5. Docs: a line in `presentation/.../ui/CLAUDE.md` under "Tags are part of the song file" ("a song shows its first
   `MAX_TAGS` tags and `MAX_LANGUAGES` languages; the file keeps the rest, and no edit drops them"), and the same rule
   in `chordpro/CLAUDE.md` next to "Text edits keep every other byte".

**Alternative: cap only what is drawn (+N pill).** This needs a change in `SongLabels`, `SongInfoBody`, the tags and
languages sheets, and the picker chips. It still leaves the filter panel unbounded, because one file's 50,000 distinct
tags are 50,000 library tags, and `CollapsibleChipFlow` composes and measures them all behind "Show all". Rejected.

**Decision for the user:** the cap. The recommendation is 100 each: far beyond human use, so the sheet trade-off in
step 4 never comes up, and 100 pills in a row is bounded. 32 (the reviewer's suggestion) also works, but then add the
sheet guard from step 4.

## Tests

All in commonTest. Each one fails without the fix.

- `ChordProParserTest`: replace `a song with a hundred thousand distinct tags or languages is parsed in linear time`
  with `a song keeps only its first MAX_TAGS tags and MAX_LANGUAGES languages`. Parse 100,000 `{tag: tN}` lines and
  100,000 `{lang: qN}` lines. Assert `tags == (0 until MAX_TAGS).map { "t$it" }`, and the same for languages. It still
  parses the large input, so the linearity guard stays. It fails today, since all 100,000 are kept.
- `ChordProTagsTest`: `a tag the file carries past the cap is not written again`. Build a text with `MAX_TAGS + 1` tags,
  call `addTag(text, "t$MAX_TAGS")`, and assert the text is unchanged. Without the line scan, a second directive is
  appended.
- `ChordProLanguagesTest`: `declaredIn reads every language, past the cap too`, with `MAX_LANGUAGES + 1` codes.
- `SongMetadataEditingTest`: `a language past the cap stays when the sheet saves`. Use a draft with `MAX_LANGUAGES + 1`
  languages, call `setSongLanguages(draftTarget, codes = shown - first, offeredCodes = shown)`, where `shown` is the
  first `MAX_LANGUAGES`. Assert that the last language's line is still in the text and the first one's is gone.
  Also add `a language the sheet never offered stays`, following the existing tag and link tests.
- `SelectionEditingTest`: `a selection's language edit keeps the languages past the cap`. Same setup, through
  `setSelectionLanguages(…, added = setOf("hu"), removed = emptySet())`.
