# Read a single chord under a section heading as a chord, not as lyric text
**Challenged:** amended — the single chord must be written with a capital (the rule `isStyledChordLine` already uses, because `chord()` also accepts lowercase `a`..`h`, which are common one-word lines), and the `Intro: C` spelling that `expandLabels` splits is added to the tests; depends on 17-strip-brackets-from-converted-section-headings.md for the exact heading text in its expected output.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordSheetConverter.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordSheetConverterTest.kt`, `chordpro/CLAUDE.md`

## Problem
A line of one chord is a chord line only where another line holds two, or it is styled, or (`hasUnambiguousChords`) the
next line is lyrics (`convertSong`'s `Kind.CHORD` condition). So `"[Outro]\nC"` converts at 8c267e01a to
`{comment: [Outro]}\nC`: the `C` stays lyric text. Same for the last section of a sheet ending in one chord.
(Heading bracket handling is a separate plan: 17-strip-brackets-from-converted-section-headings.md.)

## Fix
In the `kinds` computation, also classify `index` as `Kind.CHORD` when `candidates[index]` is non-null with exactly one
chord, the previous line (`index - 1`) is a heading (`section(lines[index-1].text) != null`) the token is written with a capital (`it.text.first().isUpperCase()`, as `isStyledChordLine` requires; a lone lowercase `a`, `e` or
`d` under a heading is a word), and the next line is blank, absent or another heading. The existing output branch for a `Kind.CHORD` with no lyric below already writes
`[C]`. Do not widen beyond a heading directly above (a lone `A` word elsewhere is likely prose). Drop this plan if
the converter test suite shows a lyric line such as `I` or `A` after a heading regressing.

## Tests
`ChordSheetConverterTest`: `[Outro]\nC` gives `{comment: Outro}\n[C]\n` (after plan 17; `{comment: [Outro]}` before it); `[Verse 1]\nC   G\nHello\n\n[Outro]\nC` likewise;
`Intro: C` (which `expandLabels` splits into the heading and a lone `C`) gives `{comment: Intro}\n[C]\n`; `Verse 1\nA\nday`
(single `A` followed by lyrics) and `Verse 1\na` (lowercase, nothing after) are unchanged from today.

## Manual check
None.
