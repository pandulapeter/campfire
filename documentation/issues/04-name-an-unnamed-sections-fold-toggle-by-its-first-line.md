# Name an unnamed section's fold toggle by the line it starts with, so a screen reader can tell them apart

**Kind:** accessibility  ·  **Severity:** low  ·  **Platforms:** Android (TalkBack), iOS (VoiceOver), desktop and web screen readers
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt`, `presentation/src/commonMain/composeResources/values/strings.xml`, `presentation/src/commonMain/composeResources/values-hu/strings.xml`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/RenderSectionsTest.kt`
**Challenged:** amended — the name is given through the chevron's own content description (the pill's `Surface(onClick)` already merges it into the button), not through new semantics on the `Surface` plus a hidden chevron; the first line is a property of `RenderSection.Lines`, not of `ChordProBlock.Section`; the Hungarian uses "szakasz", as the existing fold strings do; the cut never splits a surrogate pair; the card path is shown never to carry an unnamed header.

## Problem

Since b0d4cd782 an unnamed paragraph is headed by a pill holding `Text("")` and the chevron
(`SectionHeaderPill` → `SectionTitle`, `header == UNNAMED_SECTION_HEADER`). The pill is a `Surface(onClick = …)`, a
button that merges its children's semantics, so its whole accessible name is the chevron's description from
`FoldChevron(kind = null, …)`: `song_details_section_collapse` / `_expand` ("Hide the section" / "Show the section").
A song of six unnamed paragraphs (common: a file with no section directives at all) gives the screen reader six
identical "Hide the section" buttons with nothing to say which part of the song each one folds.

`CardTitleRow` cannot meet an unnamed header: only choruses (and recalls) are on a card (`isOnCard`), and a chorus is
always headed by its label or `defaultLabels.chorus`. Named pills read "Chorus, Hide the section" and are unchanged.

## Fix

1. In `RenderSection.Lines`, next to `firstEnvironmentLabel`, add a value computed once with the section:

   ```kotlin
   /** The words of the section's first sung line, which name a fold toggle that has no header to read. */
   val firstLyric = lines.firstNotNullOfOrNull { line -> (line as? ChordProLine.Lyrics)?.text?.trim()?.takeIf { it.isNotEmpty() } }
       ?.let(::shortenedForDescription)
   ```

   `ChordProLine.Lyrics.text` already has the chords taken out (they are `chords`, anchored by offset), so a chord-only
   line has a blank text and is skipped. Write `shortenedForDescription` as an `internal` pure function next to it:
   whitespace runs collapsed to one space, cut to 40 chars with `…` appended where it was longer, never cutting between
   a high and a low surrogate (step back one where `this[39].isHighSurrogate()`).

2. Add a `description: String? = null` parameter to `FoldChevron`, used instead of the kind's string when it is not
   null (`FoldToggleRow`, its other caller, passes nothing). Thread a `chevronDescription: String?` through `SectionHeaderPill` and
   `SectionTitle` to the chevron.

3. In `SongSectionContent`, for the `isHeaderShown -> SectionHeaderPill(…)` branch, pass
   `chevronDescription = section.firstLyric?.takeIf { header == UNNAMED_SECTION_HEADER }?.let { textResource(if (sectionToggle.isExpanded) collapse_starting else expand_starting, it) }`
   (only reached with a toggle there, since an unnamed header is shown only with one). Read it with `textResource`,
   since the argument is the user's text. Where it is null the chevron keeps today's description.

No `semantics` / `clearAndSetSemantics` is needed: the empty `Text` contributes nothing and the `Surface` already merges
the chevron's description into the button and gives it the button role.

New strings, in both files next to `song_details_section_collapse`:
- `song_details_section_collapse_starting` — "Hide the section starting “%1$s”" / "A „%1$s” kezdetű szakasz elrejtése"
- `song_details_section_expand_starting` — "Show the section starting “%1$s”" / "A „%1$s” kezdetű szakasz megjelenítése"

## Tests

In `RenderSectionsTest`, through `prepareSongLyrics(…).sections` as the file's `shape` helper does: a paragraph `[Am]There is a [C]house`
gives `firstLyric == "There is a house"`; a paragraph whose first line is only `[Am] [C]` and second `Sung` gives
`"Sung"`; a paragraph of only chords gives null; and `shortenedForDescription` of a 60-character line is 40 characters
plus `…`, and of a line whose 40th char is the first half of an emoji does not end in a lone surrogate.

## Manual check

TalkBack on Android and VoiceOver on iOS over a song with no section directives: each fold button reads its own first
line, and a named section still reads its name and "Hide the section".
