# 46 · A plain `{comment}` whose first word is a section name vanishes when nothing follows it

**Severity:** medium (a line of the user's file is not rendered) · **Area:** `:chordpro` (`ChordProParser`)

`handleComment` (`ChordProParser.kt:134–147`) turns `{c: Chorus x2}`, `{c: Intro}`, `{c: Bridge}`, `{c: Solo}` into
a legacy heading section whenever the running section is not explicit; `SectionBuilder.close()` (:229–242) then
discards a section with no lines, and the comment text goes with it. Any such comment followed by a blank line, a
directive or end-of-file never reaches the model.

Input: `{sov}\n[Am]la\n{eov}\n{c: Chorus x2}\n{soc}\n[C]lo\n{eoc}` → `[Section(Verse), Section(Chorus)]`; "Chorus x2"
is nowhere.

## Fix

A heading that ends up with no lines degrades to the comment it was:

1. `SectionBuilder` gets `private var headingText: String? = null`; `handleComment` sets it when it opens a legacy
   heading (`section.open(type, text, isExplicit = false); section.headingText = text` — or add a parameter to `open`).
2. In `close()`, where the section is dropped for having no lines:

   ```kotlin
   if (lines.isNotEmpty()) blocks += Section(...)
   else headingText?.let { blocks += ChordProBlock.Comment(it, CommentStyle.PLAIN) }
   ```

   and clear `headingText` with the rest of the state.
3. `addBlock` (the "flush and reopen" path) must carry `headingText` across the reopen, or a `{column_break}` right
   after the heading would lose it.
4. Tests in `ChordProParserTest`: the input above yields `[Section(Verse), Comment("Chorus x2"), Section(Chorus)]`;
   `{c: Chorus x2}\n\n[C]lo` yields `[Comment("Chorus x2"), Section(Paragraph)]`; the existing legacy-heading tests
   (`{c: Chorus}\n[C]la`) are unchanged.
5. `ChordProSerializer` round trip: with the comment now a block, `parse(serialize(parse(x))) == parse(x)` for the
   inputs above; add them to the serializer test.
