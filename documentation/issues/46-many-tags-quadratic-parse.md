# 46 · A song carrying tens of thousands of tags or languages stalls the library scan on every launch, and freezes the web app

**Severity:** hang (all platforms; the web page is frozen, since its default dispatcher is its only thread; crafted or
generated file only — nobody types 20,000 tags — but once such a file is in the library it is paid on **every launch**,
on every keystroke in its editor and on every import that names it, and on the web there is no way into the app to
delete it) · **Area:** `:chordpro` (`ChordProParser.MetadataBuilder`)

## Symptom
A song of nothing but distinct tags, generated with
`(0 until N).joinToString("\n") { "{tag: t$it}" }` (languages: `"{meta: language q$it}"` or `"{lang: q$it}"`):

| input | `parse` / `summarize` / `parseMetadata` (desktop JVM, each) |
|---|---|
| 20,000 tags, 270 KB | 0.38 s |
| 100,000 tags, 1.4 MB | 10.8 s |
| 100,000 languages, 2.4 MB | 6.4–8.0 s |
| ≈ 600,000 tags, the 8 MiB `ImportLimits.MAX_TEXT_FILE_SIZE` | ≈ 6 minutes (quadratic extrapolation) |

Every other pass over the same text (`highlight`, `removeTag`, `setLanguages`, `ChordProHeader`, `split`) takes
2–40 ms, so the time is all in one place. Where it is paid:
- `SongLocalSourceImpl.readSong` → `ChordProParser.summarize` for every file of the library scan at every start — the
  batch of 64 holding the file does not finish, so the list stays partly filled;
- `PrepareImportUseCaseImpl` → `songRepository.importFileName` → `parseMetadata` while an import is planned;
- the song screen (`parse`), `ChordProTags.addTag` (`parseMetadata`), and the editor's `derivedStateOf { summarize }`
  (`SongEditorScreen.kt:269`) on every keystroke.

## Cause
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt:386-394` deduplicates by a
linear search of everything collected so far:

```kotlin
private fun addTag(value: String) {
    if (tags.none { it.equals(value, ignoreCase = true) }) tags += value
}

private fun addLanguage(value: String) {
    if (value !in languages) languages += value
}
```

with `tags` and `languages` plain `MutableList`s (`:346-347`), so N distinct directives cost N²/2 comparisons. Every
public entry point of the parser (`parse`, `parseAsWritten`, `summarize`, `parseMetadata`) goes through this builder.

## Fix
In `MetadataBuilder` keep the lists (their order is the file's order, which the header chips and `tags.first()`
semantics rely on) and add a hash set next to each:

```kotlin
private val tags = mutableListOf<String>()
private val tagKeys = mutableSetOf<String>()
private val languages = mutableListOf<String>()
private val languageSet = mutableSetOf<String>()

/** A tag the song already carries in another spelling is not a second tag, see [ChordProMetadata.tags]. */
private fun addTag(value: String) {
    if (tagKeys.add(ChordProSyntax.caseInsensitiveKey(value))) tags += value
}

/** The codes are normalized before they get here, so a repeated language is a repeated string. */
private fun addLanguage(value: String) {
    if (languageSet.add(value)) languages += value
}
```

`caseInsensitiveKey` must mean exactly what `equals(ignoreCase = true)` means, or two spellings that are one tag today
become two: that is **not** `lowercase()` (`"İ".lowercase()` is two characters, `i` + U+0307, while
`"İ".equals("i", ignoreCase = true)` is true). Kotlin's `Char.equals(other, ignoreCase = true)` — the one Native and
Wasm build `String.equals(ignoreCase = true)` from — compares `uppercaseChar()` and then
`uppercaseChar().lowercaseChar()`, so the key is that, character by character, and keeps the length. (On the JVM the
string comparison is `String.equalsIgnoreCase`, which folds the same way per `char` but pairs surrogates into code
points first, so it also equates the two cases of a supplementary-plane letter — Deseret, Osage, Adlam — that the key
keeps apart. Two tags that differ only in the case of such a letter becoming two tags on desktop and Android is
accepted.)

`ChordProParser.MetadataBuilder` is a different class from `ChordProSyntax`, so the key has to be reachable from it:
make it a member of the `ChordProSyntax` object next to `tag()` (a `private` extension there would not compile from
the parser), so that any later case-insensitive tag lookup in the module uses the same rule:

```kotlin
/** Equal for exactly the strings `equals(ignoreCase = true)` calls equal, char by char, so that it can key a hash set. */
fun caseInsensitiveKey(value: String) = String(CharArray(value.length) { value[it].uppercaseChar().lowercaseChar() })
```

and in `addTag`: `if (tagKeys.add(ChordProSyntax.caseInsensitiveKey(value))) tags += value`. Do **not** cap the number
of tags instead: the file is the user's, and the parser is total by design.

## Tests
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserTest.kt` (next to the existing tag
tests, or `ChordProTagsTest.kt`):
- `tags that differ only in case are one tag, the first spelling winning`: `{tag: Demo}\n{tag: DEMO}\n{tag: demo}` →
  `listOf("Demo")`.
- `a dotted capital I and a plain i are the same tag, as equals ignoring case says`: `{tag: İstanbul}\n{tag: istanbul}`
  → `listOf("İstanbul")` (this is today's behaviour; the test pins it for the key function).
- `the order of tags and languages is the order of the file`: three tags and three languages interleaved.
- `a song with a hundred thousand distinct tags is parsed in linear time`:
  `ChordProParser.summarize((0 until 100_000).joinToString("\n") { "{tag: t$it}" }).metadata.tags.size == 100_000`
  — about 11 s before the fix, well under a second after, so a regression shows up as a test run that is suddenly
  slow; the same with `{lang: q$it}` for languages.

## Verify
1. `./gradlew :chordpro:desktopTest`.
2. Generate the 1.4 MB file above, import it on desktop (`./gradlew :app:desktop:run`): the import plans in well
   under a second, the song list fills at start without a pause, and opening the song and typing in its editor stay
   responsive. (What the Songs screen's tag filter and the song header then do with 100,000 chips is a presentation
   question and out of scope here.)
3. Web (`./gradlew :app:web:wasmJsBrowserDevelopmentRun`): the same file does not freeze the page at start.

## Docs
None: `chordpro/CLAUDE.md` already says matching ignores case; the change is how, not what.

## Touches
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntax.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserTest.kt`

## Depends on
Nothing.
