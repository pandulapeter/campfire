# Keep Prettify from inserting a blank line that splits a running heading section or paragraph at a comment or a break

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProPrettifier.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProParser.kt` (expose the legacy heading
check), `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProPrettifierTest.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordSheetConverter.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordSheetConverterTest.kt`, `chordpro/CLAUDE.md`
**Challenged:** amended — `ChordSheetConverter` writes an `Interlude` / `Instrumental` heading as a non-heading `{comment: …}` straight after lyrics and relied on Prettify's blank line to make it a heading between sections; step 5 makes the converter write that blank itself. Re-import of files stored by the old Prettify is flagged under Decisions in the challenge report.

## Problem

`ChordProPrettifier.prettify` puts a blank line before every block directive and after every break
(ChordProPrettifier.kt:90-101 at 8ee010b36):

```kotlin
if (gapBeforeNext || (start != null && !isLineMode) || directive?.name in ChordProSyntax.blockNames ||
    directive?.name == "new_song" || directive?.name == "ns") gap()
…
directive?.name in breakNames || end != null -> gapBeforeNext = true
```

Outside an explicit environment a blank line *closes* the running section (`SectionBuilder.addContent`: "A blank line
ends an implicit paragraph or a legacy heading section."), while a comment or a break with no blank line around it
only *cuts* it, the rest being its continuation. So the inserted blank changes the song. Verified with a probe test:

```
{c: Chorus}
Line one
{c: x2}
Line two

{chorus}
```

parses as a Chorus section, an `IN_SECTION` comment, a Chorus continuation, and a recall that repeats all three.
After `prettify` (`{c: Chorus}|Line one||{c: x2}|Line two||{chorus}`) it parses as a Chorus of "Line one", a
`BETWEEN_SECTIONS` comment, an unheaded `Paragraph` "Line two" (off the chorus card, a fold of its own), and a recall
repeating only "Line one". `{np}` / `{column_break}` in the same place does the same (blank before and after it), and
in a plain paragraph (`Line one|{c: note}|Line two`) the continuation becomes a new paragraph. Prettify runs on every
import (`PrepareImportUseCaseImpl`) and from the editor's overflow menu, so a Campfire 3 file — the `{c: Chorus}`
heading dialect the parser still reads — is stored with a different meaning. The module doc already avoids this for
tabs and grids ("No blank line is added around a tab or grid … which a blank line would split") but not for these.

A `{chorus}` recall or a legacy *heading* comment closes the running section anyway, so a blank before them changes
nothing and the existing test `comments separate headings without separating them from their following lyrics`
(`{c: Verse}\nWords\n\n{c: Chorus}\nSing\n\n{chorus}\n\n{np}\n\nMore\n`) stays as it is.

## Fix

The prettifier only ever *adds* blank lines, so leaving one out keeps the input's own adjacency and can never change
the structure. Leave it out exactly where the parser would otherwise be closing a section that a comment or a break
merely cuts:

1. In `ChordProParser`, expose the heading check the prettifier needs:
   `internal fun isLegacyHeading(text: String) = legacyHeading(text.trim()) != null` (next to `private fun legacyHeading`).
2. In `prettify`, track `var isImplicitSectionRunning = false` — a legacy heading section or an implicit paragraph is
   open, which a blank line would close. Outside every environment (the code after the `if (environments.isNotEmpty())`
   block):
   - a blank input line (`gap()` branch) → `false`;
   - a lyric line (`directive == null`) → `true`;
   - a plain `{comment}`/`{c}` whose value `isLegacyHeading` → `true` (it opens a section);
   - `{start_of_tab}` / `{start_of_grid}` → `true` (it opens or continues the paragraph);
   - any other `{start_of_…}`, `{chorus}`, `{new_song}`/`{ns}` → `false`;
   - everything else (non-heading comments, `{highlight}`, breaks, `{transpose}`, metadata) leaves it as it is.
   In the environments branch, set it to `false` when an explicit environment closes (where `gapBeforeNext = true` is
   set at line 80) and leave it alone when a tab or grid closes.
3. Compute `val cutsRunningSection = isImplicitSectionRunning && directive != null && start == null &&
   directive.name != "chorus" && directive.name in ChordProSyntax.blockNames && !(directive.name in setOf("comment", "c") && ChordProParser.isLegacyHeading(directive.value.orEmpty()))`
   and do not call `gap()` for the block directive when it is true; do not set `gapBeforeNext` after a break when it
   is true. (A `gapBeforeNext` left from an earlier explicit environment end still applies; no section is running then.)
4. In `ChordSheetConverter` (the `Kind.SECTION` branch, ~line 286 at 8ee010b36), the `else output += "{comment: ${label}}"`
   path writes a heading the parser does not know as a section (`Interlude`, `Instrumental`, `Interlude 2`: the
   converter's `section` regex accepts them, `sectionType` maps them to null, and `legacyHeading` does not know them).
   Converted text goes through `prettify` on import (`PrepareImportUseCaseImpl`), which until now put a blank line
   before it and so ended the implicit paragraph of unlabelled lyrics before it; after step 3 it would not, and the
   heading would become a comment inside the previous paragraph with the chords after it its continuation. Write the
   blank in the converter instead: `else { if (output.lastOrNull()?.isNotBlank() == true) output += ""; output += "{comment: $label}" }`.
   (`Intro`, `Outro` and `Solo` also come out as `{comment: …}`, but they are legacy headings, which step 3 never
   cuts, so they keep their blank either way.)
5. Extend the "No blank line is added around a tab or grid" sentence in `chordpro/CLAUDE.md`'s `ChordProPrettifier`
   bullet: nor before a comment or a break inside a legacy heading section or an implicit paragraph, which a blank
   line would close rather than cut.

## Tests

In `ChordProPrettifierTest`:
- `a comment or a break inside a heading section stays inside it`: for the three texts above (`{c: x2}`, `{np}`,
  and the plain paragraph with `{c: note}`), assert `parse(prettify(x)).blocks == parse(x).blocks` and that the
  output is idempotent.
- Add `"{c: Chorus}"`, `"{c: x2}"`, `"{ci: softly}"` and `"{np}"` to the fragments of `balanced fragments preserve
  their sections and format idempotently`, which then exercises the rule over 2 000 random documents.
- The existing `comments separate headings without separating them from their following lyrics` must pass unchanged.
- In `ChordSheetConverterTest`: `a heading the parser does not know still ends the lyrics before it` —
  `convert("Hello\nworld\nInterlude\nC   G")` (or `[Interlude]` on its own line) contains `"world\n\n{comment: Interlude}"`,
  and `ChordProParser.parse(ChordProPrettifier.prettify(result))` has the `Interlude` comment as
  `CommentPlacement.BETWEEN_SECTIONS` rather than `IN_SECTION`. Check the existing converter tests still pass (the
  ones at lines 63, 168-170 start with the heading, so no blank is added there).

## Manual check

Import a Campfire 3 style `.cho` with a `{c: Chorus}` heading, a `{c: x2}` comment inside it and a later `{chorus}`:
the song details screen shows the whole chorus on its card and the recall repeats both halves. Run Prettify from the
editor's overflow menu on the same text: no blank line appears before `{c: x2}`.
