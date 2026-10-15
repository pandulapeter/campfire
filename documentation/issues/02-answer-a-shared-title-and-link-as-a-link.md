# Answer a share of a one-line title beside a page address as a link, not as a song

**Challenged:** amended — spelled out how the Android shape differs from the iOS extension's rule, made the paste half
explicit (its own message), covered the same text inside a several-text share (`sharedTextsToImportedFiles`), and named
the exact test files and the one legitimate input it also catches.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** Android (iOS's extension already refuses it); desktop and web
drops and pastes too if the paste half is taken
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/PastedSongController.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/SharedTextImport.kt` (`withoutSourceLink`,
`isOnlyLinks`, `sharedTextsToImportedFiles`),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/platform/SharedTextImportTest.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/PastedSongControllerTest.kt`,
`presentation/CLAUDE.md` (Document imports, last-but-one bullet)

## Problem

`PastedSongController.importSharedText` at HEAD (`51d66ea99`; unchanged since `4bde19afe`):

```kotlin
when {
    texts.isEmpty() -> enqueueImport(sharedTextsToImportedFiles(payload.texts, payload.subject), false)
    texts.all { it.isOnlyLinks() } -> messageSink.sendMessage(Message.SharedLinkNotImported)
    texts.size == 1 -> newSongFromText(texts.single(), payload.subject)
    else -> enqueueImport(sharedTextsToImportedFiles(texts, payload.subject), false)
}
```

Many Android apps share a page as `EXTRA_TEXT = "Wonderwall – Oasis\nhttps://…"`. `isOnlyLinks()` is false, so the text
goes to `newSongFromText`; `sharedTextsToImportedFiles` runs `withoutSourceLink`, which strips the address, and what is
left is one line, which the converter turns into a nearly empty song, and the New song sheet opens on it. The product
rule is that a shared web page is answered with "select the text and share that", never fetched; the iOS extension
already treats "a page's address alone, or beside a one-line title" as a link (`app/ios/CLAUDE.md`).

The iOS rule (`app/ios/iosApp/ShareExtension/ShareInboxWriter.swift`, `write`) sees a different shape: the address is
its own URL attachment (`hasLink`) and the title a text beside it, so it answers `.link` when
`texts.allSatisfy(Self.isOnlyLinks) || hasLink && texts.allSatisfy { !trimmed.contains(where: \.isNewline) }`. On Android
both are in one string, which is exactly what `withoutSourceLink` recognises (last non-blank line an address, the one
before it not).

## Fix

- In `SharedTextImport.kt`, next to `isOnlyLinks`, add a pure `internal fun String.isLinkWithTitle()`: true when
  `withoutSourceLink(this)` differs from `this` (an address was removed) and what remains is exactly one non-blank line.
  Reuse `withoutSourceLink` rather than re-deriving its rule (it also drops quotation marks; irrelevant to the count).
- `importSharedText`: `texts.all { it.isOnlyLinks() || it.isLinkWithTitle() } -> SharedLinkNotImported`.
- `newSongFromText` (paste, Ctrl/Cmd + V, desktop and web drops, and a single share — but the share is caught above
  first): `text.isOnlyLinks() || text.isLinkWithTitle() -> Message.PastedOnlyLinks` ("A link is not a song, and Campfire
  does not open pages. Copy the chords and the lyrics themselves."), which fits the pasted case as written.
- `sharedTextsToImportedFiles` (several texts, some of them songs): it already writes empty bytes for a text that is only
  links, so the import reports it as skipped; do the same for `isLinkWithTitle()` on the original text, so a title +
  address among real songs is not written as a one-line song.
- `presentation/CLAUDE.md` (re-read first; it is being edited): "a page's address alone" becomes "a page's address,
  alone or beside a one-line title".

Also caught, accepted: Chrome's share of a highlight that is a single line (`"one line"\nhttps://…`). A one-line song is
what it would have made, and the sentence tells the user to select the whole sheet. Not caught, out of scope: a title and
an address on the same line ("Song by Artist https://…"), since `withoutSourceLink` only strips an address on a line of
its own.

## Tests

- `SharedTextImportTest`: `isLinkWithTitle` — title + newline + address → true; quoted title + blank line + address →
  true; two lines of lyrics + address → false; address alone → false (that is `isOnlyLinks`); title alone → false;
  `sharedTextsToImportedFiles(listOf("Song\nhttps://x", "[G]la\n[C]la"), null)`: the first file's bytes are empty.
- `PastedSongControllerTest`, beside "a shared page address is answered with a sentence rather than imported": a share of
  `"Wonderwall – Oasis\nhttps://example.com/w"` sends `Message.SharedLinkNotImported` and queues nothing; `newSongFromText`
  of the same text sends `Message.PastedOnlyLinks`.
