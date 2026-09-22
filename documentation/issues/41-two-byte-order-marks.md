# 41 · A file that starts with two byte order marks loses its first directive

**Severity:** wrong behaviour (all platforms; rare — a tool that prepends a BOM to a file that already has one, which
some Windows converters and "save as UTF-8 with BOM" round trips do) · **Area:** `:data:model` (`LibraryText.kt`:
`decodeLibraryText`)

## Symptom
A song file whose bytes start `EF BB BF EF BB BF {title: X}`: the song is listed and opened under its file name
instead of "X", and the viewer shows `{title: X}` as a line of lyrics (the first line starts with an invisible U+FEFF,
so it is not a directive). Whatever the first line was — usually the title — is lost the same way. The first save
from the editor writes one BOM back and the next read strips it, so the file heals once it is edited, but nothing in
the app hints that it should be.

## Cause
`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryText.kt:32` strips one mark:

```kotlin
}.removePrefix(BYTE_ORDER_MARK)
```

and `ChordProSyntax.matchDirective` is handed `line.trim()`, which does not trim U+FEFF (a format character, not
whitespace, on every platform), so the line fails the `trimmedLine[0] != '{'` check.

## Fix
Strip every leading mark (U+FEFF at the start of a text is never content — as a zero-width no-break space it was
superseded by U+2060 decades ago):

```kotlin
}.trimStart(BYTE_ORDER_MARK)
```

with `private const val BYTE_ORDER_MARK = '﻿'` (a `Char` now; it has no other use in the file). The KDoc's "A
byte order mark is stripped" becomes "Byte order marks at the start are stripped — a file that went through two tools
that each added one has two".

## Tests
`data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/LibraryTextDecodingTest.kt`,
next to the existing BOM case (`:29`):
- `every byte order mark at the start is stripped`:
  `"﻿﻿{title: É}".encodeToByteArray().decodeLibraryText() == "{title: É}"`.
- `a byte order mark inside the text is kept`: `"{title: A}\n﻿x".encodeToByteArray().decodeLibraryText()` returns
  its input unchanged (only the start is the file's business).

## Verify
1. `./gradlew :data:source:local:implementation:desktopTest`.
2. `printf '\xef\xbb\xbf\xef\xbb\xbf{title: Two marks}\n[C]La\n' > two_marks.cho`, import it on desktop: it is titled
   "Two marks" and named `two_marks.cho`.

## Docs
None.

## Touches
- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryText.kt`
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/LibraryTextDecodingTest.kt`

## Depends on
Nothing.
