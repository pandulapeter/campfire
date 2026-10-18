# Recognize the section headings every chord and lyrics site writes, in every language the app's users paste from

**Kind:** bug  ·  **Severity:** high  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt`,
new `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetHeadings.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverterTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/CLAUDE.md`
**Challenged:** amended — the context rule for sung words now has a defined home (`Heading.isAmbiguous`, resolved in
`classify`), `expandLabels` passes the colon so `Hook: C G` splits, `[Chorus] (x2)` is in the grammar, `Parte` is
untyped and the Romance and Dutch bridge words are added, and the repeat-mark pattern is defined once.

## Problem

A pasted text is classified line by line in `ChordSheetConverter.classify`, and a heading is only a heading when the
`section` regex matches the whole line (after `headingLabel` strips `[]`, `()` and a trailing colon):

```kotlin
private val section = Regex("^(Verse|Chorus|Refrain|Bridge|Pre-Chorus|Intro|Outro|Solo|Interlude|Instrumental|Versszak|Refrén|Ref\\.|R\\.)(?:\\s*[0-9]+)?$", RegexOption.IGNORE_CASE)
private fun section(text: String): String? = section.find(headingLabel(text))
    ?.takeIf { it.range.first == 0 }?.groupValues?.get(1)?.lowercase()
private fun sectionType(text: String): String? = when (section(text)) {
    "verse", "versszak" -> "verse"
    "chorus", "refrain", "refrén", "ref.", "r." -> "chorus"
    "bridge", "pre-chorus" -> "bridge"
    else -> null
}
```

Every heading the regex misses becomes `Kind.LYRIC`, and a chord row above it is set into the heading's letters
(`[G]Inst[D]rume[Em]ntal [C]break`). Worse, an open `{start_of_…}` runs until the next *recognized* heading, so one
missed heading puts the rest of the song inside the wrong section. Probed at the reviewed commit (invented content):

- Genius tags every heading with the singer: `[Verse 1: Some Artist]`, `[Chorus: A & B]` → an escaped lyric line
  `(Verse 1: Some Artist)`; the `{start_of_bridge: Pre-Chorus}` before it stays open across the chorus and the next
  verse.
- Ultimate Guitar writes `[Pre chorus]`, `[Prechorus]`, `[Post-Chorus]`, `[Hook]`, `[Chorus x2]`, `[Chorus] (x2)`:
  all lyrics.
- Plain English headings `Hook`, `Tag`, `Coda`, `Ending`, `Break`, `Breakdown`, `Instrumental break`, `Chorus x2`,
  `Chorus (x2)`: all lyrics inside whatever section is open.
- Cifra Club, La Cuerda and the German, Italian, Polish, Dutch, French, and Hungarian sites: `Strophe 1`, `Strofa 1`,
  `Ritornello`, `Zwrotka 1`, `Refren`, `Couplet 1`, `Refrein`, `Estrofa`, `Coro`, `Estribillo`, `Refrão:`,
  `Parte 1 de 3`, `1. versszak`, `R:`, `Vsz.`: all lyrics. (`Refrén:` and `Ref.:` work; `R:` does not, since
  `headingLabel` removes the colon before the regex sees `R\.`.)
- A heading of a type the converter does not know (`Intro`, `Interlude`) becomes `{comment: …}` in `writeBody` but
  does not close the open section, so an interlude ends up inside the verse before it.

## Fix

Move the heading vocabulary out of the regex into a new `internal object ChordSheetHeadings` in its own file (the
converter is past its line budget, and a vocabulary is a thing of its own), and make `ChordSheetConverter.section`
/ `sectionType` / `headingLabel` / `expandLabels` ask it. Keep every existing test green.

1. `ChordSheetHeadings.parse(line: String): Heading?` returns `Heading(label: String, type: String?)` for a line
   whose whole trimmed text is:
   - optional wrapping in `[…]` or `(…)`;
   - optional leading number and dot (`1. versszak`, `2. Strophe`);
   - a heading word, any case, as a whole word (see the lists below);
   - optional number (`Verse 1`, `Verse1`, `Strophe 2`);
   - optional repeat mark: ` x2`, ` (x2)`, ` 2x`, ` (2x)`, ` ×2` (`[0-9]+` either side, parentheses optional), also
     after the closing bracket (`[Chorus] (x2)` → label `Chorus (x2)`). One repeat token is defined once, as
     `ChordSheetHeadings.repeat` (`x2`, `(2x)`, `×2`, any case), and the converter's `furniture` uses it in place of its
     own `repeat` regex, so `recognizes German and lowercase chords and keeps furniture` (`x2`) stays green;
   - optional tail after `:` or ` - ` / ` – ` holding free text (`Verse 1: Some Artist`), accepted **only** when the
     raw line was wrapped in brackets or ends with a colon, so a sung "Chorus of angels sing" stays a lyric;
   - optional trailing colon.
   The label is the text without wrapping and without the trailing colon, as `headingLabel` gives it today.
2. Word lists (match case-insensitively; keep the current entries):
   - verse: `Verse`, `Versszak`, `Vsz.`, `Vsz`, `Strophe`, `Strofa`, `Zwrotka`, `Couplet`, `Estrofa`, `Estrofe`,
     `Verso`;
   - chorus: `Chorus`, `Refrain`, `Refrén`, `Ref.`, `Ref`, `R.`, `R`, `Ritornello`, `Refren`, `Refrein`, `Refrão`,
     `Refrao`, `Coro`, `Estribillo`, `Hook`;
   - bridge: `Bridge`, `Pre-Chorus`, `Pre Chorus`, `Prechorus`, `Post-Chorus`, `Post Chorus`, `Postchorus`, `Puente`,
     `Ponte` (pt, it), `Pont` (fr), `Brug` (nl), `Pré-Refrão`, `Pre-Coro`, `Pre-Estribillo`, `Átkötés`, `Átvezetés`;
   - untyped (a `{comment: …}` heading): `Intro`, `Outro`, `Solo`, `Interlude`, `Instrumental`, `Instrumental break`,
     `Tag`, `Coda`, `Ending`, `Break`, `Breakdown`, `Vamp`, `Turnaround`, `Parte` (Cifra Club's `Parte 1 de 3` splits a
     tablature or a solo, not a verse; its `[Primeira Parte]` is caught by the bracket fallback of step 4).
   Single words that are also sung (`R`, `Coro`, `Parte`, `Tag`, `Break`, `Ending`, `Hook`, `Verso`, `Pont`, `Ponte`)
   count as a heading only when the line is wrapped in brackets, ends with a colon, or is followed by a chord row or a
   blank line. `parse` sees one line only, so it returns such a bare word with `Heading.isAmbiguous = true`; the
   neighbour rule lives in `classify`, which computes the headings of the sheet once at its top
   (`heading(index)`: `parse(...)`, and an ambiguous one kept only where `candidates[index + 1] != null` or the next
   line is blank or the end) and uses that list wherever it calls `section(...)` today (its own `SECTION` branch, the
   lone-chord neighbours, the chord row's next-line check). Outside `classify` (`isTitle`, `expandLabels`) an
   ambiguous heading counts as none. `Parte 1 de 3` (Cifra Club) is a heading with the label `Parte 1 de 3`: allow
   ` de [0-9]+` after the number.
3. `sectionType` maps by the word's list: verse, chorus, bridge, or null. `Hook` is a chorus. A chorus heading with a
   repeat mark and nothing under it still becomes `{chorus}` (the existing `type == "chorus" && (next == null ||
   kinds[next] == Kind.SECTION)` branch); with lines under it, `{start_of_chorus: Chorus x2}`.
4. Generic fallback in `classify`: a line that is exactly `[text]` (brackets around the whole trimmed line), whose
   content is not a chord name (`chord(...)`) nor a chord row, has at most six words, and sits in a sheet that holds
   at least one other `SECTION` or `CHORD` line, is `Kind.SECTION` with `type == null`. `classify` has to know whether
   the sheet has structure: compute `hasUnambiguousChords || lines.any { ChordSheetHeadings.parse(it.text) != null }`
   once before the `mapIndexed`.
5. In `writeBody`'s `Kind.SECTION` branch, call `closeSection()` before writing the `{comment: $label}` of an untyped
   heading too (today only the typed branches close). Keep the blank line before the comment.
6. `expandLabels` uses `ChordSheetHeadings.parse(line.text.substring(0, colon + 1))` (the colon included, so a word
   that needs one, `Hook: C G`, `R: C G`, is not ambiguous there) instead of `section(...)`, so `Refrão: C G Am F` and
   `1. versszak: G D` split the way `Chorus: C G` does. The heading line it keeps still ends before the colon.

Do not add Spanish `Coro` without the context rule of step 2: it is a common lyric word.

## Tests

In `ChordSheetConverterTest` (invented content), one test each:

- `a bracketed heading with a singer after its colon is the section it names`: `[Verse 1: Some Artist]` →
  `{start_of_verse: Verse 1: Some Artist}`; `[Chorus: A & B]` → chorus; `[Pre chorus]` → bridge; `[Chorus x2]` with
  nothing under it → `{chorus}`.
- `an unknown bracketed heading is a heading, not a lyric`: `[Instrumental break]`, `[Vamp]`, `[Riff]` above a chord
  row each become `{comment: …}` and the chord row stays its own `[G] [D]` line; `[Riff]` in a text with no other
  structure stays prose.
- `common English heading words are sections and never take a chord row`: `Hook`, `Tag`, `Coda`, `Ending`, `Break`,
  `Chorus (x2)`, `Instrumental break` followed by `G D Em C`.
- `heading words of the European chord sites are sections`: one sheet per language from the problem list, asserting
  the `{start_of_verse}` / `{start_of_chorus}` / `{start_of_bridge}` it produces.
- `Hungarian R and Vsz headings close the chorus before them`: `Ref.:` … `R:` … `Vsz.` … and assert the verse is not
  inside the chorus (parse the prettified result with `ChordProParser` and check section types).
- `a heading closes the section before it`: `{start_of_verse}` … `Interlude` … chords → the verse's `{end_of_verse}`
  precedes `{comment: Interlude}`.
- `a lyric that starts with a heading word stays a lyric`: `Chorus of angels sing`, `Tag along with me`, `Break my
  heart again` inside lyrics.

Update `convert/CLAUDE.md` with one line: the heading vocabulary lives in `ChordSheetHeadings`, a word that is also
sung needs brackets, a colon, or a chord row or blank line after it.
