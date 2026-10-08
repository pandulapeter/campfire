# Move the shared-text-to-ImportedFile logic out of the Android shell into presentation commonMain and test it

**Kind:** testability  ·  **Severity:** low  ·  **Effort:** S  ·  **Risk:** low  ·  **Platforms:** Android (logic moves to common)
**Files:** `app/android/src/main/java/com/pandulapeter/campfire/AndroidFileImport.kt` (may have moved to
`src/main/kotlin`); new `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/SharedTextImport.kt`;
new `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/platform/SharedTextImportTest.kt`;
`app/android/CLAUDE.md` (the sentence "`importSharedTexts` (`AndroidFileImport.kt`) turns it into an `ImportedFile`
named after `EXTRA_SUBJECT` — or the text's first plain line"); `presentation/CLAUDE.md` (or `ui/platform/CLAUDE.md`
after plan 60)
**Depends on:** none

## Problem

`AndroidFileImport.kt` mixes the Android plumbing (a `Channel`, a scope on `Dispatchers.IO`, `Uri` reading) with pure
rules that decide what a shared text becomes in the library:

```kotlin
internal fun importSharedTexts(texts: List<String>, subject: String?) {
    importScope.launch {
        val files = texts.ifEmpty { listOf("") }.mapIndexed { index, text ->
            val name = subject?.cleanedForFileName()?.let { if (texts.size > 1) "$it ${index + 1}" else it } ?: text.firstPlainLine()
            ImportedFile(
                name = name.orEmpty().ifEmpty { UNTITLED } + LibraryFiles.TEXT_EXTENSION,
                bytes = if (text.isOnlyLinks()) ByteArray(0) else text.encodeToByteArray(),
            )
        }
        pendingImports.send(files)
    }
}

private fun String.isOnlyLinks() = lineSequence().filter { it.isNotBlank() }.let { lines -> lines.any() && lines.all { LINK.matches(it.trim()) } }
private fun String.firstPlainLine() = lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && it.first() !in "{[#" }?.cleanedForFileName()
private fun String.cleanedForFileName() = map { if (it == '/' || it == '\\' || it.isISOControl()) ' ' else it }
    .joinToString("").trim().take(MAX_SHARED_NAME_LENGTH).trim().ifEmpty { null }

private val LINK = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
private const val UNTITLED = "untitled"
private const val MAX_SHARED_NAME_LENGTH = 80
```

`:app:android` has no unit tests and the root `CLAUDE.md` says only pure logic is tested — this is pure logic (the empty
share, link-only texts, numbering several texts under one subject, the 80-char cap, directive/section/comment lines
skipped for the title), it is just in a module nobody tests. Every function used is in the Kotlin common stdlib
(`Char.isISOControl()`, `Regex`, `encodeToByteArray`), and `ImportedFile` / `LibraryFiles` are `:data:model` types
`:presentation` already sees.

## Fix

1. Create `SharedTextImport.kt` in `:presentation` `ui/platform` (beside `FilePicker.kt`, where the shells' shared
   helpers live) with
   ```kotlin
   /** KDoc moved from importSharedTexts: what a shared text is, why it goes down the file import path, the subject. */
   fun sharedTextsToImportedFiles(texts: List<String>, subject: String?): List<ImportedFile>
   ```
   holding the `mapIndexed` body verbatim, and the four private helpers and three constants moved verbatim (with their
   comments: "A link by itself is not a song…", "Where a pasted chord sheet has its title…", "Only what would break a
   file name…"). Keep "a share with nothing in it is still answered" with the `ifEmpty { listOf("") }`.
2. In `AndroidFileImport.kt`, `importSharedTexts` becomes
   `importScope.launch { pendingImports.send(sharedTextsToImportedFiles(texts, subject)) }`, keeping its KDoc's
   first paragraph about the channel. Delete the moved helpers and constants and any import they leave unused.
3. Docs: `app/android/CLAUDE.md` sentence names `sharedTextsToImportedFiles` in `:presentation`; one bullet in the
   `:presentation` doc's `ui/platform` list.

## Tests

`SharedTextImportTest` (commonTest, run with `./gradlew :presentation:desktopTest`):
- no texts → one file `untitled.txt` (use `LibraryFiles.TEXT_EXTENSION`) with empty bytes;
- one text, no subject → named after its first line that is not blank and does not start with `{`, `[` or `#`;
- a text of only `{title: x}` / `[Chorus]` / `# c` lines and no subject → `untitled`;
- subject given → named after the subject; two texts with a subject → `"<subject> 1"`, `"<subject> 2"`;
- subject of only `/` and spaces → falls back to the text's first plain line;
- `/`, `\` and control characters become spaces; a 200-char line is cut to 80 and trimmed;
- a text of only `https://…` lines (blank lines between, mixed case `HTTP://`) → empty bytes; a link plus a lyric line
  → the text's bytes;
- the bytes are the UTF-8 of the text otherwise (a non-ASCII lyric round-trips).

## Manual check

Android emulator: share a selected chord sheet from Chrome to Campfire (subject = page title) — the song is imported
under the page title; share a bare link — the import reports it as skipped; share from a notes app with no subject —
named after the first line.
