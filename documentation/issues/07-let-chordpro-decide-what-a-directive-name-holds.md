# Let `:libs:chordpro` decide what a directive's value and a link's or attachment's name may hold, and have the form sheets ask it instead of keeping their own copies

**Challenged:** amended — snippets re-checked at `0e28af38f` (all five filters and both `cleanName`s still as quoted;
no helper was extracted, so the hedge about one is gone). The claim that a name read back is cleaned on read was
false (`ChordProMetaItems.link` / `.attachment` only trim); the stored-format section now says what a re-save does
instead. The links change is no owner decision: `libs/chordpro/CLAUDE.md` already rules "Whitespace is
`Char.isWhitespace`, never regex `\s`", which `ChordProLinks.cleanName` breaks today. `typed` keeps the five filters'
exact rule, so nothing a user can type changes; its KDoc says `ChordProMetadataFields.set` does not apply it.

**Kind:** architecture / duplication  ·  **Severity:** low  ·  **Platforms:** all
**Files:**
- `libs/chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/edit/ChordProDirectiveValues.kt` (new, `explicitApi()`)
- `libs/chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/edit/ChordProAttachments.kt`
- `libs/chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/edit/ChordProLinks.kt`
- `libs/chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/edit/ChordProDirectiveValuesTest.kt` (new)
- `libs/chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/edit/CLAUDE.md` (one line: names are cleaned by `ChordProDirectiveValues`)
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/forms/SongAttachmentsDialog.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/forms/SongLinksDialog.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/forms/SongMetadataField.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/forms/SongPlayingDialog.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/lookup/SongDetailsReview.kt`
- `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/songText/SongAttachmentEditingTest.kt`
- `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/forms/SongLinksDraftTest.kt`

## Problem

What may stand in a directive's value, and what a link's or attachment's name becomes in the file, are rules of the
ChordPro writer, but the presentation keeps its own copies of them, and the copies already disagree.

**The typing filter** (a brace would end the directive or open another; a line break would leave the rest as lyrics)
is written five times at the reviewed commit:

```kotlin
// SongAttachmentsDialog.kt
onValueChange = { name -> onChange(attachment.copy(name = name.filterNot { it == '{' || it == '}' || it == '\n' || it == '\r' })) },
// SongLinksDialog.kt
onValueChange = { name -> onChange(link.copy(name = name.filterNot { it == '{' || it == '}' || it == '\n' || it == '\r' })) },
// SongMetadataField.kt
else -> newValue.filterNot { it == '{' || it == '}' || it == '\n' || it == '\r' }.take(maxLength)
// SongPlayingDialog.kt
newValue.filterNot { it == '{' || it == '}' || it == '\n' || it == '\r' }.take(MAX_KEY_LENGTH)
// lookup/SongDetailsReview.kt
private fun String.cleanedForMetadata() = filterNot { it == '{' || it == '}' || it == '\n' || it == '\r' }.trim()
```

`ChordProMetadataFields.set` itself only turns line breaks into spaces and leaves braces in a value, so for the
metadata field, the playing dialog, and the lookup review these filters are the only guard against a brace.

**The name "as the file will hold it"** is decided by each writer privately and guessed again by each sheet:

```kotlin
// ChordProAttachments.kt (writer and reader)
private fun cleanName(name: String?): String? = name
    ?.filterNot { it == '{' || it == '}' }
    ?.map { if (it.isWhitespace()) ' ' else it }
    ?.joinToString("")
    ?.split(' ')
    ?.filter { it.isNotEmpty() }
    ?.joinToString(" ")
    ?.takeIf { it.isNotEmpty() }

// ChordProLinks.kt (writer and reader)
private fun cleanName(name: String?): String? = name
    ?.filterNot { it == '{' || it == '}' }
    ?.replace(Regex("\\s+"), " ")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

// SongAttachmentsDialog.kt: compares the draft with the song by this to decide whether Save writes anything
private fun ChordProAttachment.normalized() = copy(name = name?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() })

// SongLinksDialog.kt: the same for links
private fun ChordProLink.normalized() = copy(
    url = ChordProLinks.usableUrl(url).orEmpty(),
    name = name?.filterNot { it == '{' || it == '}' }?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() },
)
```

The four disagree: the attachments writer folds every `Char.isWhitespace()` (non-breaking and other Unicode spaces
included), while `Regex("\\s")` in the other three matches only ASCII whitespace on the JVM (and its Unicode reach
differs by Kotlin target), which `libs/chordpro/CLAUDE.md` ("Whitespace is `Char.isWhitespace`, never regex `\s`")
already forbids in the module; the attachments sheet leaves braces in. So a name pasted with a non-breaking space makes
`songAttachmentsToSave` see a change the writer then cleans away (Save writes the file unchanged), and links and
attachments write the same typed name differently. The sheets' "as the file will hold it" is only as true as the
copy someone last kept in step.

## Fix

Add a public object in `:libs:chordpro` (`explicitApi()`, so explicit `public` and KDoc):

```kotlin
public object ChordProDirectiveValues {
    /** [value] as it may be typed into one directive: without braces, which would end it or open another, or line breaks. */
    public fun typed(value: String): String

    /**
     * A link's or an attachment's name as its `{meta: …}` line holds it: no braces, every run of whitespace one space,
     * trimmed; null where nothing is left.
     */
    public fun name(name: String?): String?
}
```

`name` takes the attachments writer's `isWhitespace()` rule (platform-independent, folds every Unicode space).
Options for the links writer:

1. **Recommended:** both writers call `ChordProDirectiveValues.name`, dropping both private `cleanName`s. The only
   behaviour change is that a link name with a non-ASCII space is now written with a plain space, as an attachment
   name already is (and as the module's whitespace rule says it should be on every platform). Nothing stored needs
   migrating. The readers (`ChordProMetaItems.link` / `.attachment`) keep a name as written, only trimmed, and stay
   so; the sheets compare draft and song through `name` on both sides, so an untouched file never looks changed.
   Only a Save of the Links sheet that writes something (`setLinks`, called from `SongMetadataEditing` alone) rewrites
   a link line whose name `name` changes (`replacement != link`), as it already does today for a run of spaces: a file
   with a non-breaking space in a link name gets a plain one on the user's next links Save, which sync then carries
   as part of that edit. Nothing rewrites it unasked.
2. Keep each writer's rule and expose two functions. Rejected: two rules for the same kind of value is the problem.

Then:

- `SongAttachmentsDialog.kt`'s and `SongLinksDialog.kt`'s `normalized()` use `ChordProDirectiveValues.name(name)` for
  the name (links keep `ChordProLinks.usableUrl` for the address).
- The five typing filters call `ChordProDirectiveValues.typed(...)` (`SongDetailsReview.cleanedForMetadata` keeps its
  `.trim()`; the `take(...)` limits stay where they are).
- Keep the explanatory comments (why a brace or a line break is refused) on the chordpro function's KDoc, not at the
  call sites. `typed` is exactly today's filter (`{`, `}`, `\n`, `\r`), so the metadata field, the playing dialog's key
  field, the lookup review, and the two name fields accept what they accept today; its KDoc notes that
  `ChordProMetadataFields.set` does not apply it, so a caller writing a typed value runs it first.
- The attachments sheet's comparison gains the brace removal it lacked: a draft differing from the song only by a
  brace the writer drops no longer offers a Save that writes the file unchanged; no real change is ever hidden, since
  `name` is what the writer writes.

`name` is idempotent and stable through write → parse → write: the writer puts one plain space between source (or
address) and name, the reader takes everything after the first whitespace run and trims it, so it reads back
`name(x)`, which `name` leaves as is.

## Tests

- `ChordProDirectiveValuesTest` (chordpro commonTest): `typed` drops `{`, `}`, `\n`, `\r` and keeps everything else
  (tabs included); `name` turns `"  Live\tat  {the}  Hall \n"` into `"Live at the Hall"`, a blank or all-brace
  name into null, and is idempotent (`name(name(x)) == name(x)`).
- Round trip: for a name with a non-breaking space, a tab, and a brace, `ChordProLinks.setLinks` then
  `ChordProParser.parseMetadata(...).links` reads back `name(x)`, and a second `setLinks` with what was read returns the
  text unchanged (the same for `setAttachments` and the attachments the parsed metadata reads).
- `ChordProLinksTest` / `ChordProAttachmentsTest`: a name with a non-breaking space and a tab is written as single
  plain spaces by both writers (the links case is the behaviour change).
- `SongAttachmentEditingTest`: a draft whose only difference from the offered attachment is a name the writer cleans to
  the offered one (a tab, a brace, a run of spaces, a non-breaking space) gives `songAttachmentsToSave(...) == null`.
- `SongLinksDraftTest`: the same for `songLinksToSave`.

Run `./gradlew :libs:chordpro:desktopTest :presentation:desktopTest`.
