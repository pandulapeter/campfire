# Flatten every value a SongbookPro import writes into a header directive onto one line without braces

**Kind:** bug · **Severity:** medium · **Platforms:** all
**Challenged:** amended — the line-break regex is written with `\u2028\u2029` escapes (the draft held the raw characters), the encoded link goes through `ChordProLinks.usableUrl` so only an address the parser keeps is written, and the notes on file names, re-imports and the converter's precedent were added.
**Files:**
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/backup/SongbookProBackup.kt`
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/backup/SongbookProBackupTest.kt`
- `data/source/local/implementation/CLAUDE.md` (the `backup/` bullet)

## Problem

`SongbookProBackup.songText` writes what SongbookPro keeps beside a song's text into ChordPro header directives, and
the values go in exactly as SongbookPro stored them, only trimmed (`JsonObject.text` is `content.trim()`). At
1c52e5347, `SongbookProBackup.kt:113-131`:

```kotlin
fun directive(name: String, value: String?, vararg aliases: String) {
    if (value != null && (aliases.toList() + name).none { it in declared }) add("{$name: $value}")
}
directive("title", title, "t")
directive("subtitle", song.text("subTitle"), "st", "su")
directive("artist", song.text("author"))
…
directive("copyright", song.text("Copyright"))
song.text("Url")?.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let { add("{meta: link $it}") }
…
(folders + song.textList("_tags"))
    .distinct()
    .forEach { add("{tag: $it}") }
```

These are free text fields in SongbookPro (a multi-line copyright block is common: "© 1990 X\nAdmin. by Y"), and a
ChordPro directive is one line closed by `}`. Probed at 1c52e5347 by running `SongbookProBackup.read` and then
`ChordProParser.parseMetadata` on its output, with `author = "A\nB"`, `Copyright = "Line1\r\nLine2"`,
`Url = "https://x.com/a b"`, a folder named `"Hymns}\n{title: Hijack"` and a tag `"x}"`, the song file written was:

```
{title: Song}
{artist: A
B}
{copyright: Line1
Line2}
{meta: link https://x.com/a b}
{tag: Hymns}
{title: Hijack}
La
```

and it read back as `title=Hijack, artist=null, tags=[Hymns], links=[ChordProLink(url=https://x.com/a, name=b)]` with
the second tag `x}`. So:

- a line break in the author or the copyright loses the artist (the file is then also named without it) and puts
  `{artist: A` / `B}` and `{copyright: Line1` / `Line2}` on the page as lyrics;
- a brace and a line break in a folder or tag name ends the directive early, and whatever follows is a directive of
  its own — here it retitles the song;
- a `}` stays inside a tag (`x}`), and a URL with a space is read as an address plus a link name.

## Fix

One private function that every value goes through before it is put between braces, used by `directive(...)` and by
the tag and link lines:

```kotlin
/** A value as one directive can hold it: on one line, its line breaks a " / " and every other space one, no braces. */
private fun headerValue(text: String) = text
    .split(LINE_BREAK).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" / ")
    .replace(WHITESPACE, " ")
    .replace('{', '(').replace('}', ')')
    .takeIf { it.isNotBlank() }
```

with `LINE_BREAK = Regex("[\\r\\n\\u2028\\u2029]+")` (written with escapes, not the raw separator characters) and `WHITESPACE = Regex("""\s+""")`. A value that ends up blank is
left out, as a missing one is.

- `directive(name, value, …)` adds `"{$name: ${headerValue(value)}}"` and skips a null result.
- Tags: map each through `headerValue` (`mapNotNull`) *before* `.distinct()`, so two spellings that flatten alike are
  one tag.
- The link: an address cannot contain a space or a brace, so either
  - **(recommended)** percent-encode them (`' '` → `%20`, `{` → `%7B`, `}` → `%7D`, and drop line breaks), which keeps
    the address SongbookPro opened, since browsers accept the unencoded one; or
  - drop a `Url` that contains whitespace or a brace after trimming.

  Either way, the address written is `ChordProLinks.usableUrl(encoded)` and the line is left out where that is null
  (`ChordProSyntax.webUrl`, which the parser reads a link with, is internal to `:chordpro`; `usableUrl` is its public
  form and, for a value already starting with `http://` or `https://`, answers the same). That keeps the rule "only
  write a link the parser takes" in one place rather than in the `startsWith` checks here. The chip names the link by
  its host, which encoding the path does not touch.
- The `title` passed to `directive("title", …)` goes through it too; the batch file name built from it already
  replaces control characters (`UNSAFE_CHARACTERS`) and is unaffected.

Checked and unaffected:

- **File names.** `{artist: A / B}` folds to the same name as `{artist: A B}` would (`LibraryFiles.normalizedName`
  turns `/` and the spaces around it into one separator, so `a_b-song.cho`); the `-` between artist and title is added
  by `songFileName` after each half is folded on its own, so a `/` in either half cannot be mistaken for it.
- **Re-importing the same backup** gives byte-identical text, so it is disregarded as today. A song imported *before*
  this fix from a backup that has one of these values was written with the broken header, so the fixed import's text
  differs and that one song becomes the ordinary keep-both / replace question — only for the songs the bug damaged.
- **Braces into parentheses** is what `ChordSheetConverter.headerValue` already does for the converter's header (it
  joins line breaks with a space instead; " / " is kept here because a multi-line copyright reads as two credits).
  Chords are in square brackets, never braces, so nothing chord-like is changed. `ChordProLinks.cleanName` drops braces
  from a link's name instead; there is no name here.

Update the `backup/` bullet of `data/source/local/implementation/CLAUDE.md` with one clause: every value written into
the header is flattened onto one line, braces turned into parentheses, since SongbookPro's fields are free text.

## Tests

In `SongbookProBackupTest`, a song with `"author": "A\nB"`, `"Copyright": "Line1\r\nLine2"`,
`"Url": "https://x.com/a b"`, a folder `"Hymns}\n{title: Hijack"` and `"_tags": ["x}"]`; assert the exact text:

```
{title: Song}
{artist: A / B}
{copyright: Line1 / Line2}
{meta: link https://x.com/a%20b}
{tag: Hymns) / (title: Hijack}
{tag: x)}
La
```

(the folder's flattened form, whatever it is exactly, must be a single line), and that
`ChordProParser.parseMetadata` of it gives `title = "Song"`, `artist = "A / B"`, one link whose URL is the whole
encoded address and no name. The existing `songsGetAHeaderFromWhatSongbookProKeepsBesideTheirText` must still pass
unchanged.

## Manual check

Import a real SongbookPro backup in which one song has a multi-line copyright and an author with a line break (edit
them in SongbookPro first): the song details show the artist and no stray `{copyright: …` lines in the lyrics.
