# 49 · Serializer: a tab run with a blank line comes back as two paragraphs

**Severity:** low (no production caller today; the documented invariant fails) · **Area:** `:chordpro` (`ChordProSerializer.serializeLines`)

`serializeLines` (`ChordProSerializer.kt:81–97`) closes and reopens `{start_of_tab}` around every `Blank`, and outside
an explicit environment a blank line ends the paragraph on re-parse. `{sot: Riff}\ne|--0--|\n\ne|--3--|\n{eot}` parses
to one `Section(Paragraph, "Riff", [Tab, Blank, Tab])`; `parse(serialize(…))` gives two paragraphs, the label on the
first.

## Fix

In `serializeLines`, a `Blank` line does not change the open environment: make `lineEnvironmentName(Blank)` return
the *current* `openEnvironment` (pass it in), so the blank is written inside the `{start_of_tab}` … `{end_of_tab}`
pair. The parser keeps blanks inside a line mode (`isExplicit || lineMode != null -> Blank`), so the round trip
closes. Add the case to the serializer test.
