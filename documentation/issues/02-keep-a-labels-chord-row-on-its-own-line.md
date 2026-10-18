# Keep the chord row written after a heading's colon on its own line instead of setting it into the lyric under it

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverterTest.kt`

Run after plan 01: the bracketed-label case below needs `ChordSheetHeadings.parse`.

## Problem

`expandLabels` splits `Verse 1: G D Em C` into a heading line and a chord line, but the chord line keeps the column
positions it had after the label (9 and up):

```kotlin
private fun expandLabels(lines: List<Rendered>): List<Rendered> = lines.flatMap { line ->
    val colon = line.text.indexOf(':')
    if (colon > 0 && section(line.text.substring(0, colon)) != null) {
        val after = colon + 1
        val rest = line.copy(text = line.text.substring(after), positions = line.positions.copyOfRange(after, line.positions.size))
        if (chordTokens(rest) != null) listOf(line.copy(text = line.text.substring(0, colon), positions = line.positions.copyOf(colon)), rest) else listOf(line)
    } else listOf(line)
}
```

`classify` then makes the rest a `Kind.CHORD`, and `writeBody` merges it with the lyric line under it by those
columns:

```kotlin
Kind.CHORD -> {
    if (kinds.getOrNull(index + 1) == Kind.LYRIC && index + 1 !in omitted) {
        output += merge(line, lines[index + 1], convertParentheses)
        index++
    } else output += bracketed(candidates[index]!!)
}
```

Probed: `Verse 1: G D Em C` / `The river runs beside the road` → `The river [G][D]run[Em]s [C]beside the road`;
`Chorus: C G D` / `Oh riverlight` → `Oh river[C]li[G]gh[D]t`. The chords after a label are the section's progression,
never positioned over the lyric under them.

A label in brackets followed by chords, `[Intro] G D Em C`, is not split at all: `expandLabels` only looks for a
colon, so the line becomes `{title: [Intro] G D Em C}` (or an escaped lyric), and in `convertFragment` the line
`(Intro) G D Em C`.

## Fix

1. Give `Rendered` a flag, `val isFromLabel: Boolean = false`, set on the `rest` line `expandLabels` splits off.
2. In `writeBody`'s `Kind.CHORD` branch, take the `bracketed(...)` path when `line.isFromLabel`, whatever follows.
3. In `expandLabels`, also split a line that starts with a bracketed heading (`ChordSheetHeadings.parse` of the
   bracket content is non-null) followed by whitespace and a chord row: `[Intro] G D Em C` → the heading line
   `[Intro]` and the chord line, flagged. Use the bracket's closing index the way the colon's index is used today;
   the heading line keeps its brackets so `headingLabel` strips them as before.
4. `convertFragment` calls `classify` without `expandLabels`; call `expandLabels` there as well, so a pasted
   `Chorus: C G D` fragment becomes a section heading and a chord line (the fragment rules in `CLAUDE.md` already say
   headings become sections).

## Tests

- `a label's chords are their own line, never set into the lyric under it`: `Verse 1: G D Em C` /
  `The river runs beside the road` → `{start_of_verse: Verse 1}` / `[G] [D] [Em] [C]` / the lyric unchanged; same for
  `Chorus: C G D` / `Oh riverlight`.
- `a bracketed label followed by chords is a heading and its chords`: `[Intro] G D Em C` → `{comment: Intro}` /
  `[G] [D] [Em] [C]`; `[Chorus] C G` with lyrics under it → `{start_of_chorus: Chorus}` / `[C] [G]` / the lyric.
- Extend `a fragment's headings become sections and its first line is no title` with `Chorus: C G D` on the first
  line of the fragment.

Keep `sections metadata and recalls become directives` and `derives plain title and expands tabs` green: they pin
the colon split for `Intro: G D` style lines.
