# 48 · A blank line inside one `{start_of_tab}` splits it into two fingerboards for the viewer but not for the editor

**Severity:** low (an octave disagreement in a rare shape) · **Area:** `:chordpro` (`ChordProTransposer.rewriteLines`)

`rewriteLines` (`ChordProTransposer.kt:80–88`) flushes the tab run at every non-`Tab` line, `Blank` included;
`transposeText` (:125) collects the whole environment. `{sov}\n{sot}\ne|--0--|\n\ne|--20--|\n{eot}\n{eov}` at −2: the
model gives `e|--10-|` and `e|--18--|` (first system up an octave), the text path leaves the environment untouched
because 0..20 fits in no octave at −2.

## Fix

Let `Blank` lines stay inside a run: collect `Tab` lines and the `Blank`s between them as one run (a run ends at the
first line that is neither), hand only the tab texts to `rewriteTabLines`, and put the blanks back at their indexes.
The same octave decision is then made for the whole system in both paths. Test: the input above yields the same
result through `transpose(parse(x))` and `parse(transposeText(x))`.
