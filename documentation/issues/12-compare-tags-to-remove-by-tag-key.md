# Compare the tags a sheet takes off by `tagKey` (NFC, then lowercase) rather than by their raw lowercase, so a decomposed tag in a file can be unticked

**Challenged:** amended — the tests' file texts spell the tag with the escape `U\u0308nnep` instead of the invisible decomposed character this plan held, which any copy that normalizes text would turn into a test that passes without the fix.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all (the files that carry decomposed tags mostly come from macOS and iOS)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/SelectionEditing.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/SongMetadataEditing.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/SelectionEditingTest.kt` (new), `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/SongMetadataEditingTest.kt`

## Problem

The tag sheets key every tag by `it.lowercase()` of `Song.tags`, which the local source composes to NFC
(`SongMappers.kt`: `tags = normalizedTags(summary.metadata.tags)`). The edit then looks for the tags to take off among
the tags parsed from the file's raw text, which are not composed, and compares their plain lowercase against those
keys:

- `SelectionEditing.kt` L48–52 at d650f3cb0 (the selection's Manage tags):

  ```kotlin
  fun setSelectionTags(fileNames: List<String>, added: List<String>, removed: Set<String>) = edit(fileNames) { text ->
      val removedTags = parseChordPro(text).metadata.tags.filter { it.lowercase() in removed }
  ```

  where `removed` comes from `BulkLabelDraft` keys built in `TagsSheet.kt` L101:
  `BulkLabelDraft.of(songs.map { song -> song.tags.map { it.lowercase() } })`.
- `SongMetadataEditing.kt` L71–79 (the one-song tag sheet), the same pattern:

  ```kotlin
  val keptKeys = tags.mapTo(mutableSetOf()) { it.lowercase() }
  val offeredKeys = offeredTags.mapTo(mutableSetOf()) { it.lowercase() }
  editSong(target) { text ->
      val removed = parseChordPro(text).metadata.tags.filter { it.lowercase() in offeredKeys && it.lowercase() !in keptKeys }
  ```

Scenario: a song written on a Mac carries `{tag: Ünnep}` with the Ü decomposed (U + U+0308). The sheet lists it as
"Ünnep" (composed), ticked. Untick it and Save: the file's tag lowercases to `ünnep`, which is not the key
`ünnep`, so nothing is removed; the file is unchanged, yet the selection's snackbar says the songs were updated
(`Message.SongsUpdated(fileNames.size)` counts the request, not the changes), and the tag is still shown. The removal
itself would work: `SetChordProTagUseCaseImpl` already calls `ChordProTags.removeTag(text, tag, fold = String::normalizedToNfc)`.
Only the filter in front of it drops the tag. Adding is unaffected (`addTag` folds the same way, so no duplicate).

## Fix

Compare by `com.pandulapeter.campfire.data.model.domain.tagKey` (`tag.normalizedToNfc().lowercase()`, already imported
elsewhere in `:presentation`, for example `ui/components/TagColor.kt`) on both sides:

- `SelectionEditing.setSelectionTags`:

  ```kotlin
  val removedKeys = removed.mapTo(hashSetOf(), ::tagKey)
  ...
  val removedTags = parseChordPro(text).metadata.tags.filter { tagKey(it) in removedKeys }
  ```

  (`removedKeys` computed once, outside the per-file lambda.) Update its KDoc "(folded by case)" to "(folded by case and
  Unicode form, as `tagKey` folds them)".
- `SongMetadataEditing.setSongTags`: `keptKeys` and `offeredKeys` built with `tagKey`, and the filter
  `tagKey(it).let { key -> key in offeredKeys && key !in keptKeys }`. Its KDoc's "compared without regard to case" gains
  "and Unicode form".

The `TagsSheet` keys themselves need no change: they are lowercase NFC already, since `Song.tags` are composed, and
mapping them through `tagKey` again is harmless. No CLAUDE.md paragraph describes the comparison at this level; none is
touched.

## Tests

- New `SelectionEditingTest` (commonTest, `ui/state`), over small fakes: an `EditSongsUseCase` that applies `edit` to a
  `mutableMapOf<String, String>` of texts and returns `EditedSongs(changed, emptyList())`; `ParseChordProUseCase` as
  `SongMetadataEditingTest` stands it in (`ChordProParser.parse`); `SetChordProTagUseCase` as the real implementation
  is, `if (isSelected) ChordProTags.addTag(text, tag, fold = String::normalizedToNfc) else ChordProTags.removeTag(text, tag, fold = String::normalizedToNfc)`;
  `SetChordProLanguagesUseCase` delegating to the `:chordpro` object the real one uses; `MessageSink(backgroundScope)`;
  `scope = backgroundScope`. Test: `a decomposed tag in a file is taken off by its composed key` — file
  `"{title: A}\n{tag: U\u0308nnep}\n{tag: Rock}"`, `setSelectionTags(listOf("a.cho"), added = emptyList(), removed = setOf("ünnep"))`,
  `advanceUntilIdle()`, and the file's tags are `["Rock"]`. It fails at d650f3cb0 (the decomposed tag stays). The keys (`"ünnep"`, `"Ünnep"`) stay composed, as the
  sheet's are; only the file texts are decomposed, and with the escape, since a literal decomposed `Ü` in the source
  survives no editor or tool that normalizes text, and a composed one passes either way.
- `SongMetadataEditingTest`: `a decomposed tag the sheet offered and left unticked is taken off` —
  `setSongTags(draftTarget, tags = emptyList(), offeredTags = listOf("Ünnep"))` applied to
  `"{title: A}\n{tag: U\u0308nnep}"` leaves no tags. Make the test's `setChordProTag` fake fold with
  `String::normalizedToNfc`, as the real use case does.

## Manual check

On a Mac, write a song file whose `{tag: …}` has a decomposed accented letter (for example save it from a tool that
writes NFD, or `printf '{title: A}\n{tag: U\xcc\x88nnep}\n' > a.cho` and import it). In the app, untick that tag both in
the song's own Manage tags and, with two such songs ticked, in the selection's: the tag is gone from the files and from
the song cards.
