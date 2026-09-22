# 21 · `{meta: title …}`, `{meta: artist …}`, `{meta: key …}` are ignored, though the spec defines them as `{title}` etc.

**Severity:** wrong behaviour (all platforms. Files exported by tools that write the `meta` form, and hand-written ones following chordpro.org's meta page; such a song has no title, artist or key in Campfire) · **Area:** `:chordpro` (`ChordProParser.MetadataBuilder`, `ChordProSyntax.metadataKind`, `ChordProTransposer.rewriteText`)

## Symptom
Import a song whose header is `{meta: title Amazing Grace}` / `{meta: artist John Newton}` / `{meta: key G}`:
- the list titles it by its file name, with no artist and no key;
- it is stored under its file name rather than `john_newton-amazing_grace.cho`;
- transposition has no key to choose sharps or flats from, and the editor's Transpose leaves `{meta: key G}` as `G`
  while every chord moves;
- the editor's toolbar keeps offering Title and Artist as if they were missing.

## Cause
chordpro.org/directives-meta: "`{meta: title X}` functions identically to the standalone `{title: X}`", for title,
sorttitle, subtitle, artist, composer, lyricist, arranger, copyright, album, year, key, time, tempo, duration and
capo. The builder folds only tag and language; every other key lands in `custom`
(`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt:370-379`):

```kotlin
"meta" -> {
    val name = value.substringBefore(' ').trim()
    when {
        name.equals(ChordProSyntax.TAG_NAME, ignoreCase = true) -> ChordProSyntax.tag(directive)?.let(::addTag)
        ChordProSyntax.isLanguageMeta(directive) -> ChordProSyntax.language(directive)?.let(::addLanguage)
        name.isNotEmpty() -> custom.getOrPut(name) { mutableListOf() } += value.substringAfter(' ', missingDelimiterValue = "").trim()
    }
}
```

`ChordProSyntax.metadataKind` (`ChordProSyntax.kt:250-254`) folds only the tag and language `{meta}` forms, so
`ChordProHeader.declaredMetadata` does not count a `{meta: title}`. The text transposition renames only
`directive.name == KEY` (`ChordProTransposer.kt:226`).

## Fix
Fold the standard names the model has a field for; `sorttitle`, `arranger` and `copyright` have none and stay in
`custom` as today.

1. `ChordProSyntax.kt`, next to `tag` / `language`:

   ```kotlin
   /**
    * The names the spec defines `{meta: name value}` to mean exactly what the standalone `{name: value}` means, of the
    * ones the model has a field for; the tag and the language are read by [tag] and [language].
    */
   private val standardMetaNames = setOf(
       "title", "subtitle", "artist", "composer", "lyricist", "album", "year", "key", "capo", "tempo", "time", "duration",
   )

   /**
    * The standalone directive a `{meta}` one stands for (`{meta: title Amazing Grace}` is `{title: Amazing Grace}`), or
    * null for every other directive, the custom metadata a `{meta}` may carry included. The name is matched ignoring
    * case, like every directive name.
    */
   fun standardMeta(directive: Directive): Directive? {
       if (directive.name != META) return null
       val value = directive.value?.trim() ?: return null
       val name = value.substringBefore(' ').trim().lowercase()
       return if (name in standardMetaNames) Directive(name, value.substringAfter(' ', missingDelimiterValue = "").trim()) else null
   }
   ```

   and `metadataKind` (`:250-254`):

   ```kotlin
   fun metadataKind(directive: Directive): String? = when {
       isTagMeta(directive) -> TAG_NAME
       isLanguageMeta(directive) -> LANGUAGE_NAME
       else -> {
           val name = standardMeta(directive)?.name ?: directive.name
           (metadataAliases[name] ?: name).takeIf { it in metadataOrder }
       }
   }
   ```

   Its KDoc: "the short spellings (`{t}`) and the `{meta}` ones (`{meta: language en}`, `{meta: title …}`) fold into
   the long one".

2. `ChordProParser.kt`, `MetadataBuilder.consume`, first line of the `"meta"` branch:

   ```kotlin
   "meta" -> {
       // The spec defines these as the standalone directive, so they are read as one: a song whose header is all
       // `{meta: title …}` lines is titled, named and keyed by it like any other.
       ChordProSyntax.standardMeta(directive)?.let { consume(it); return }
       val name = value.substringBefore(' ').trim()
       ...
   ```

   There is no recursion: `meta` is not a standard name.

3. `ChordProTransposer.kt`, `rewriteText`, replace the key call 24 introduced:

   ```kotlin
   (ChordProSyntax.standardMeta(directive) ?: directive).takeIf { it.name == KEY }?.value?.takeIf { it.isNotEmpty() }?.let { key ->
       lines[index] = transposeKeyLine(rawLine, key, rename)
   }
   ```

   The key of `{meta: key G}` is also the last thing before the closing brace, so 24's end-anchored replacement
   applies unchanged (`{meta: key G}` → `{meta: key A}`).

The model side needs nothing more: `rewriteChords` renames `metadata.key` wherever it came from, and `scan` reads
through the same builder. Songs already in a library whose header uses these forms get a real title on the next scan,
and "Update file name" is offered for them once, which is the documented behaviour.

## Tests
`ChordProParserTest.kt`, new `the standard meta names are read as their own directives`:
```kotlin
val metadata = ChordProParser.parse("{meta: title Amazing Grace}\n{meta: Artist John Newton}\n{meta: key G}\n{meta: capo 2}\n{meta: tuning DADGAD}").metadata
assertEquals("Amazing Grace", metadata.title)
assertEquals("John Newton", metadata.artist)
assertEquals("G", metadata.key)
assertEquals(2, metadata.capo)
assertEquals(mapOf("tuning" to listOf("DADGAD")), metadata.custom)
```
and the same metadata from `ChordProParser.summarize(...)` and `parseMetadata(...)`; `{meta: key H}\n[H]a` summarizes
to key `B` (German detection still applies to a key read this way).

`ChordProHeaderTest.kt`, extend `every spelling of a directive is reported as declared under one name` with
`{meta: title X}` → `"title"` and `{meta: key G}` → `"key"`.

`ChordProTransposerTest.kt`, new `a key written as meta is transposed with the chords`:
`assertEquals("{meta: key A}\n[A]a", ChordProTransposer.transposeText("{meta: key G}\n[G]a", 2))`, and
`prefersFlats(parse("{meta: key F}\n[C]a"), 0)` is true.

## Verify
Import the Amazing Grace file above: the list shows "Amazing Grace", John Newton, key G; the file is stored as
`john_newton-amazing_grace.cho`; the editor's toolbar no longer offers Title or Artist; Transpose +2 writes
`{meta: key A}`. `./gradlew :chordpro:desktopTest`.

## Docs
- `chordpro/CLAUDE.md`, `ChordProParser` bullet, after "Unknown directives are ignored;": add "`{meta: title …}` and
  the other standard names the spec defines as their standalone directive (`subtitle`, `artist`, `composer`,
  `lyricist`, `album`, `year`, `key`, `capo`, `tempo`, `time`, `duration`) are read as that directive;".
- `documentation/file-format.md`, the **Metadata** bullet: change "and `meta` for anything else." to "and `meta`
  for anything else. `{meta: title Wonderwall}` and the other standard names written that way are read as the
  directive of the same name."

## Touches
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntax.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposer.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProHeaderTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposerTest.kt`
- `chordpro/CLAUDE.md`, `documentation/file-format.md`

## Depends on
24 (the value-range `transposeKeyLine`). 19 is independent but touches `ChordProSyntax.kt`; run one after the other.
