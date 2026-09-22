# 28 · `{highlight}` comments are silently dropped

**Severity:** minor (all platforms. Files that use the spec's `highlight` spelling of a comment; the note is shown nowhere) · **Area:** `:chordpro` (`ChordProParser.handleDirective`, `ChordProSyntax.blockNames`)

## Symptom
A song has `{highlight: Key change!}` before its last chorus. The song screen shows nothing there.

## Cause
The spec lists `highlight` with the comment directives (the reference implementation treats it as `comment`).
`handleDirective` has no case for it (`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt:139-157`),
so it falls to `metadata.consume`, which ignores it. `ChordProSyntax.blockNames` (`ChordProSyntax.kt:296-299`), which
the text transposition uses to cut a tab where the parser cuts a section and which ends the header for
`metadataInsertionIndex`, does not name it either.

## Fix
1. `ChordProParser.kt`, `handleDirective`, after the `"comment_box", "cb"` case:
   ```kotlin
   // Never a Campfire 3 heading: that dialect wrote its headings as `{comment}` only.
   "highlight" -> section.addBlock(ChordProBlock.Comment(directive.value.orEmpty().trim(), CommentStyle.PLAIN))
   ```
2. `ChordProSyntax.kt`, `blockNames`: add `"highlight"` after `"cb"`.

A `{highlight}` then behaves like a plain `{comment}` everywhere (it interrupts a section, a tab or a grid the same
way), except that it never opens a legacy heading section. The serializer writes it back as `{comment: …}`, which
is its canonical spelling; `parse(serialize(parse(x))) == parse(x)` holds because blocks are joined by a blank line,
after which a `{comment: Chorus}` stays a comment.

## Tests
`ChordProParserTest.kt`, new `a highlight is shown as a comment and never taken for a heading`:
```kotlin
val blocks = ChordProParser.parse("{highlight: Chorus}\n[C]la").blocks
assertEquals(ChordProBlock.Comment("Chorus", CommentStyle.PLAIN), blocks.first())
assertEquals(SectionType.Paragraph, (blocks[1] as ChordProBlock.Section).type)
```
and `{sot}\ne|-3-|\n{highlight: x}\ne|-5-|\n{eot}` gives two sections around the comment, as the `{comment}` version
in `a comment inside a tab environment does not end the tablature` does.

`ChordProTransposerTest.kt`: a copy of `the two halves of a tab cut by a comment are fingerboards of their own in the
model as in the text` with `{highlight: …}` instead of `{comment: …}`.

## Verify
A song with `{highlight: Key change!}` shows the note where it stands. `./gradlew :chordpro:desktopTest`.

## Docs
- `chordpro/CLAUDE.md`: none needed beyond what the tests say; optionally, in the `ChordProParser` bullet, "comments
  (`comment`, `highlight`, `comment_italic`, `comment_box`)".
- `documentation/file-format.md`, **Content** bullet: "`comment` / `highlight` / `comment_italic` / `comment_box` for
  shown ones".

## Touches
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt`
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntax.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTransposerTest.kt`
- `documentation/file-format.md`

## Depends on
None.
