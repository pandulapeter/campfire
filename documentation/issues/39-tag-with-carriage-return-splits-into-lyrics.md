# 39 · A tag pasted from two lines of Windows text is written into the song as two stray lines of lyrics

**Severity:** wrong behaviour — the user's file is changed in a way the app cannot undo (all platforms; most likely on
Windows desktop, where copied text ends its lines with CRLF; rare) · **Area:** `:chordpro` (`ChordProTags.addTag`),
`:presentation` (`AddSongTagDialog`, `NewSongDialog`)
**Verifier:** extended the dialog half to the new-song dialog's title and artist fields (`Dialogs.kt:632`, `:641`),
which strip only `\n` the same way and are written by `CreateSongUseCaseImpl` into `{title: …}` / `{artist: …}` lines;
the `:chordpro` half was run in a worktree and its three tests pass.

## Symptom
Open a song, tap **Add tag**, and paste text that spans two lines of CRLF text (two cells of a spreadsheet, two lines of
a Notepad or Word document: `Christmas` CRLF `Carols`). The dialog drops the `\n` and keeps the `\r`, and confirming it
writes this into the file (verified on a38dea2f with `ChordProTags.addTag("{title: X}\n{artist: Y}\n\n[C]La la\n", "Christmas\rCarols")`):

```
{title: X}
{artist: Y}
{tag: Christmas\rCarols}

[C]La la
```

Every reader of the file — the app included — splits lines at a lone `\r`, so the song now carries **no** tag and a new
paragraph of two lyric lines, `{tag: Christmas` and `Carols}`, drawn at the top of the song. Tapping the (absent) chip
cannot remove it, and `ChordProTags.removeTag` with the same string turns the `\r` into a `\n` and removes nothing.
The file is on disk and goes out with the next sync run.

A crafted value can also add any directive: `x}\r{title: Hijacked}` sets the tag `x` **and** retitles the song.

## Cause
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt:691`:

  ```kotlin
  onValueChange = { value = it.replace("\n", "").take(MAX_TAG_LENGTH) },
  ```

  strips `\n` only; `singleLine = true` does not stop a paste from carrying line breaks (which is why the `replace`
  exists at all).
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTags.kt:26-31` writes whatever it is given
  into one line of a line-based format, trimming only the ends:

  ```kotlin
  val trimmedTag = tag.trim()
  ...
  lines.add(ChordProSyntax.metadataInsertionIndex(lines, ChordProSyntax.TAG_NAME), "{${ChordProSyntax.TAG_NAME}: $trimmedTag}")
  ```

  while `ChordProSyntax.splitLines` (`ChordProSyntax.kt:75-79`) splits at `\r\n`, `\n` **and** a lone `\r`.

## Fix
1. `:chordpro` is where the rule belongs, since it is the one that knows what ends a line. In `ChordProTags`, fold every
   line break of the tag into a single space before anything else (`addTag` and `removeTag` alike, so that the two
   agree on what the tag is):

   ```kotlin
   /** A tag is one line of the file, so the line breaks a pasted value may carry are the spaces between its words. */
   private fun String.asTag() = replace(lineBreakRegex, " ").trim()
   private val lineBreakRegex = Regex("[\\r\\n]+")
   ```

   and use `tag.asTag()` where `tag.trim()` is used now (`:27`, `:39`). A tag that is empty after that is refused as an
   empty one is today.
2. `:presentation`, `Dialogs.kt`: fold the same way instead of deleting only `\n`, so that what the field shows is
   what gets written, in the three fields whose value becomes a line of a song file — the tag (`:691`) and the new
   song's title (`:632`) and artist (`:641`). A pasted `Amazing\r\nGrace` as a new song's title today writes
   `{title: Amazing\rGrace}`, which reads back as two lyric lines and no title, while the file is named
   `amazing_grace.cho`, so the song opens untitled with an "Update file name" entry in its menu. One private helper at
   the bottom of the file:

   ```kotlin
   /** A field whose value becomes one line of a song file: a pasted line break is the space between two words. */
   private fun String.asSingleLine() = replace(lineBreakRegex, " ")
   private val lineBreakRegex = Regex("[\\r\\n]+")
   ```

   and `value = it.asSingleLine().take(MAX_TAG_LENGTH)`, `title = it.asSingleLine().take(MAX_TITLE_LENGTH)`,
   `artist = it.asSingleLine().take(MAX_TITLE_LENGTH)`. (Deleting rather than folding glued `Christmas` and `Carols`
   into one word, which is worse.) The two search fields (`:786`, `:1032`) write nothing and stay as they are.

Do **not** escape the line break into the directive (`\r` written literally): ChordPro has no escape for it, and every
other ChordPro tool would still see two lines.

## Tests
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTagsTest.kt`:
- `a line break inside a tag becomes a space`: `addTag("{title: X}\n", "Christmas\rCarols")` ==
  `"{title: X}\n{tag: Christmas Carols}\n"`, and `ChordProParser.parse` of it has `tags == listOf("Christmas Carols")`
  and no blocks; the same for `"Christmas\r\nCarols"` and `"Christmas\nCarols"`.
- `a tag cannot write a second directive`: `addTag("{title: X}\n", "x}\r{title: Hijacked}")` parses back with
  `title == "X"`.
- `a tag added with a line break is removed with the same value`: `removeTag(addTag(text, "a\rb"), "a\rb") == text`.

## Verify
1. `./gradlew :chordpro:desktopTest`.
2. Desktop on Windows (or any platform, pasting text copied from a CRLF file): Add tag, paste two lines, confirm — the
   chip reads "Christmas Carols", the song body is unchanged, and removing the chip leaves the file byte for byte as it
   was.
3. New song, paste two CRLF lines into the title: the field shows them with a space between, and the editor opens on
   a song titled that way, with no stray lyric lines and no "Update file name" in its menu.

## Docs
`chordpro/CLAUDE.md`, the `ChordProTags` bullet, after "The value is taken whole, commas included, because the spec calls
a tag arbitrary text.": add "— arbitrary text on one line: a line break in a value handed to `addTag` or `removeTag` is
read as a space, since it would otherwise end the directive and leave the rest of it in the song as lyrics."

## Touches
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTags.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProTagsTest.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt`
- `chordpro/CLAUDE.md`

## Depends on
Nothing. If another plan changes `AddSongTagDialog`, apply both edits to `onValueChange` together.
