# 07 — A tag typed on a Mac and the same tag typed elsewhere are two tags

**Severity:** minor (wrong filter grouping, a tag that cannot be removed from the picker) · **Area:** `:chordpro`
(`ChordProTags`), `:data:source:local:implementation` (`SongMappers.kt`), `:domain:implementation`
(`SetChordProTagUseCaseImpl`)

**Read, not run.** Found by reading how tags are compared at HEAD (2065e47f). The unit tests reproduce it.

## What the user sees

A tag with an accented letter — `Café`, `Ünnepi`, `Tábortűz` — written into a song on macOS or iOS by a tool that
decomposes (a file name turned into a tag by hand, a text editor that saves NFD, a sync from a Mac) and typed into
another song in the app:

1. The Songs screen's tag filter lists **two chips that look identical**, each counting only its own songs.
2. In the tag picker of the decomposed song, the library's `Café` is not shown as selected, and tapping it **adds a
   second `{tag: Café}`** to the file instead of recognising the one it has; deselecting it later removes nothing.

The project already treats the two forms as one name everywhere else (root `CLAUDE.md`: "macOS and iOS hand out names
decomposed and every other platform composed … so the two forms would be two songs"; `normalizedToNfc` in
`:data:model`, applied to file names and sync names).

## Cause

Tags are compared by case only, never by normalization form:

- `ChordProSyntax.kt:271`, the parser's de-duplication key: `CharArray(value.length) { value[it].uppercaseChar().lowercaseChar() }`
  — `e` + U+0301 and `é` are different keys (`ChordProParser.kt:405-407`).
- `ChordProTags.kt:28` and `:41`, what the app writes and removes:

  ```kotlin
        if (trimmedTag.isEmpty() || ChordProParser.parseMetadata(text).tags.any { it.equals(trimmedTag, ignoreCase = true) }) return text
  ```
  ```kotlin
        val lines = ChordProSyntax.splitLines(text).filterNot { line -> line.tag()?.equals(trimmedTag, ignoreCase = true) == true }
  ```
- Across the library the tags are grouped by `lowercase()` (`GetScreenDataUseCaseImpl.kt:172-176, 213`,
  `CampfireViewModel.kt:488`, `Dialogs.kt:690, 984, 1092`), on `Song.tags` exactly as the scan read them
  (`data/source/local/implementation/.../mapper/SongMappers.kt:34`, `tags = summary.metadata.tags`).

`:chordpro` depends on nothing and cannot normalize Unicode itself; `normalizedToNfc` is an `expect`/`actual` in
`:data:model`.

## The change

Invoke the **`code-style`** skill before the first edit. Normalize where the library meets the file, and let
`ChordProTags` be told how to fold a tag, the way `ChordProTransposer` is told the preferred spelling.

### 1. The library's tags — `SongMappers.kt:34`

```kotlin
        // Composed, so that a tag written on a Mac and the same tag typed anywhere else are one tag to the filter:
        // the two forms look the same and every comparison above this is by case only. See normalizedToNfc.
        tags = summary.metadata.tags.map { it.normalizedToNfc() }.distinctBy { it.lowercase() },
```

(`import com.pandulapeter.campfire.data.model.domain.normalizedToNfc`; `:data:source:local:implementation` already
uses it in `FileNames.kt`.) Every grouping above it is by `lowercase()` of `Song.tags`, so this one line merges the
chips, the counts and the filter.

### 2. Writing a tag — `ChordProTags.kt`

```kotlin
    /**
     * … A blank tag, or one the song already carries, returns the text unchanged.
     *
     * @param fold What two spellings of a tag are compared as, besides their case — the caller's Unicode normalization,
     *   which this module has none of. The tag is written as it was given.
     */
    fun addTag(text: String, tag: String, fold: (String) -> String = { it }): String {
        val trimmedTag = tag.asTag()
        val key = fold(trimmedTag)
        if (trimmedTag.isEmpty() || ChordProParser.parseMetadata(text).tags.any { fold(it).equals(key, ignoreCase = true) }) return text
        …
    }

    fun removeTag(text: String, tag: String, fold: (String) -> String = { it }): String {
        val trimmedTag = tag.asTag()
        if (trimmedTag.isEmpty()) return text
        val key = fold(trimmedTag)
        val lines = ChordProSyntax.splitLines(text).filterNot { line -> line.tag()?.let(fold)?.equals(key, ignoreCase = true) == true }
        return ChordProSyntax.joinLines(lines, text)
    }
```

`removeTag` then drops every spelling of the tag in the file, the decomposed one included, which is what the picker's
"deselect" means.

### 3. The use case — `SetChordProTagUseCaseImpl.kt:18-23`

```kotlin
    override operator fun invoke(text: String, tag: String, isSelected: Boolean) = if (isSelected) {
        ChordProTags.addTag(text, tag, fold = String::normalizedToNfc)
    } else {
        ChordProTags.removeTag(text, tag, fold = String::normalizedToNfc)
    }
```

(`:domain:implementation` reaches `:data:model` through `:data:repository:api`'s `api` dependency.)

**Deliberately left:** the parser's own de-duplication (`caseInsensitiveKey`) stays case-only, since `:chordpro`
cannot normalize. A single file that carries both forms shows two chips in its own details header (read from the
parsed text, `SongLyrics.kt:319`) — removing either one removes both after this change. And a filter selection saved
before this change in the decomposed form stops matching and is dropped by the rule `filterTags` already has for a
tag no song carries (`GetScreenDataUseCaseImpl.kt:165-169`).

## Tests

- `chordpro/src/commonTest/.../ChordProTagsTest.kt` (`./gradlew :chordpro:desktopTest`), with a stand-in fold, since
  the module has no normalization — `val fold = { value: String -> value.replace("é", "é") }`:
  - `a tag is not added again in another normalization form` — `addTag("{tag: Café}", "Café", fold)` returns the
    text unchanged; without `fold` it adds the line (today's behaviour, pinned).
  - `removing a tag removes it in every normalization form` — `removeTag("{tag: Café}\n{tag: Café}\n[C]a", "Café", fold)`
    == `"[C]a"`.
- `data/source/local/implementation/src/commonTest/.../mapper/SongMappersTest.kt` (new; runs on desktop, where
  `normalizedToNfc` is `java.text.Normalizer`): `the tags of a song are composed and merged` —
  `StoredFileInfo("a.cho", 0, 0).toSong(ChordProParser.summarize("{title: A}\n{tag: Café}\n{tag: café}"))`
  has `tags == listOf("Café")`.
- `domain/implementation/src/commonTest/.../useCases/SetChordProTagUseCaseImplTest.kt` (new):
  `a composed tag removes its decomposed spelling` and `a composed tag is not added next to its decomposed spelling`.

Run `./gradlew :chordpro:desktopTest :data:source:local:implementation:desktopTest :domain:implementation:desktopTest`.

## Verification

1. Confirm first: write two songs into the desktop library, one with `{tag: Café}` (write the file with
   `printf '{title: A}\n{tag: Cafe\xcc\x81}\n' > a.cho`), one with `{tag: Café}` typed normally. **Before:** two
   `Café` chips, each with 1.
2. After: one chip with 2.
3. Song A → tag picker: `Café` shown selected; deselect it: the file loses its line.

## Docs

- `chordpro/CLAUDE.md`, the `ChordProTags` bullet: after "Two spellings of the same word are one tag everywhere:
  matching ignores case": add ", and a caller may pass the Unicode normalization this module does not have (`fold`),
  which the app does, so that a tag written on a Mac is the tag typed anywhere else".
- `data/source/local/implementation/CLAUDE.md:100`, the `SongLocalSourceImpl` sentence listing what the scan reads:
  "tags" → "tags (composed to NFC and merged, the way file names are)".

## Files touched

- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTags.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTagsTest.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/mapper/SongMappers.kt`
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/mapper/SongMappersTest.kt` (new)
- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/SetChordProTagUseCaseImpl.kt`
- `domain/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/SetChordProTagUseCaseImplTest.kt` (new)
- `chordpro/CLAUDE.md`, `data/source/local/implementation/CLAUDE.md`

## Depends on

Nothing, and nothing in lane A depends on it. `SongMappers.kt` is also read by lane B/C plans; the one line changed
here is not touched by them as far as the plan index shows (plan 02 only edits a KDoc in `Song.kt`).
