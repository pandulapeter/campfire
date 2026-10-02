# Do not let Prettify put blank lines around tab and grid environments inside an implicit paragraph
**Challenged:** amended — the paragraph-tracking rule left the case of a tab or grid that OPENS the paragraph (`{sot}…{eot}` then a lyric line) split, because the parser lets the following lyric continue the paragraph the tab opened; the rule is now two small conditions ("a tab/grid start adds no blank line of its own, and its end forces none"), proven by a balanced-fragment fuzz (0 of 20000 differ, down from 8275); the plan's own fragment list with stray `{eot}`/`{eog}` could never pass and is replaced by balanced blocks; the effect on the import comparison is stated.

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all (every import and the editor's Prettify menu entry)
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProPrettifier.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProPrettifierTest.kt`, `chordpro/CLAUDE.md`

## Problem
In `ChordProPrettifier.prettify`, `gap()` is called before any `start != null` directive and `gapBeforeNext` is set after
any top-level `end != null`. For `{start_of_tab}`/`{start_of_grid}` that is wrong: the parser (chordpro/CLAUDE.md,
"switch the kind of line … inside whatever section is running") keeps a tab or grid in the running paragraph, but a
blank line ends an implicit paragraph (`ChordProParser.addContent`: "A blank line ends an implicit paragraph"). Verified
with a throwaway probe at 8c267e01a:

```
Hello [G]world\n{start_of_tab}\ne|--0--|\n{end_of_tab}\nMore [C]words
 -> prettify -> Hello [G]world\n\n{start_of_tab}\ne|--0--|\n{end_of_tab}\n\nMore [C]words\n
parse(before).blocks = [Section]      parse(after).blocks = [Section, Section, Section]
```

A fuzz over fragments (lyrics, `{sot}`/`{eot}`/`{sog}`/`{eog}`, tab lines, comments, blanks, `{soc}`, `{sov}`) found 829 of
20000 random documents whose parsed blocks change under prettify; every one with a tab/grid directive next to a
paragraph line (e.g. `[G]one\n{sot}\ne|--0--|`). Folding, labels and column cutting change on every import. Prettify
stays idempotent (0 failures), so the fix must keep that.

**Not a bug (drop that part of the finding):** a `{comment}` between two lines of an implicit paragraph also gets blank
lines, but the only parsed difference is the second section's `isContinuation` (comments already cut a paragraph, no
heading differs); that spacing is pinned by the test "comments separate headings without separating them from their
following lyrics". A comment inside a tab did not move (`IN_SECTION` kept): environments' interiors are copied raw.

## Fix
No paragraph tracking is needed, and tracking only the "opened inside a paragraph" case is wrong: a tab that opens
the paragraph (`{sot}\ne|--0--|\n{eot}\nMore [C]words`) is continued by the next lyric line too (`openLineMode` opens a
`Paragraph` when none is running, and `closeLineMode` leaves it open when it holds lines), and today's prettify splits
it. So, with `val isLineMode = start == "tab" || start == "grid"` (`startOfEnvironment` returns the canonical name,
verified: `sot` gives `tab`):
- in the final `if (gapBeforeNext || start != null || …) gap()` condition, a line-mode start no longer counts as
  `start != null`: `if (gapBeforeNext || (start != null && !isLineMode) || …) gap()`. `gapBeforeNext` still forces the gap, so
  a tab after a closed `{eoc}`/`{eov}` keeps today's blank line (harmless: no paragraph is running there);
- in the `environments.isNotEmpty()` branch, `if (environments.isEmpty() && end != "tab" && end != "grid") gapBeforeNext = true`,
  so the end of a tab or grid does not force a gap before whatever follows.
Blank lines the user wrote stay (they already collapse to one). `sov`/`soc`/delegated starts, comments, `{chorus}` and
breaks keep today's gaps. Tried in a worktree at 8c267e01a with exactly these two changes: every existing `:chordpro`
and `:domain:implementation` desktop test passes unchanged and the fuzz below finds 0 differences.

## Tests
- The sample above: `prettify` leaves it as written, and `parse(prettify(t)).blocks == parse(t).blocks`.
- Also `{sot}\ne|--0--|\n{eot}\nMore [C]words` and `Hello\n{sot}\ne|-0-|\n{eot}\nMore` are left as written (the first is the
  case a paragraph-only rule misses).
- Property-style test in `ChordProPrettifierTest`: documents of 1–6 BALANCED fragments (fixed `Random(1)`, 2000 draws;
  fragments: `Hello [G]world`, `More [C]words`, ``, `{chorus}`, whole blocks `{sot}\ne|--0--|\n{eot}`,
  `{sot}\ne|--0--|\n\ne|-1-|\n{eot}`, `{sog}\n | G . | C . |\n{eog}`, `{soc}\n[C]Sing\n{eoc}`, `{sov}\nVerse\n{eov}`; no
  `{comment}`) satisfy `parse(prettify(t)).blocks == parse(t).blocks` and `prettify(prettify(t)) == prettify(t)`. Do NOT
  put bare `{eot}`/`{eog}` fragments in the list: a stray end directive with no start changes the parse by itself
  (`e|--0--|\n{eog}\ne|--0--|` fails even with the fix), which is not what the test is about. Measured: before the fix
  8275 of 20000 such documents differ, after it 0, and 0 are non-idempotent either way. Existing prettifier tests must still pass (`tab grid and nested environment
  interiors are untouched` has a `{sov}` around the tab, unaffected).
- Update the `ChordProPrettifier` bullet in `chordpro/CLAUDE.md`: no blank line is added around a tab or grid, since
  the parser keeps one in the paragraph that is running (or opens it), and a blank line would split the section.
- **Import comparison:** `ImportPlanner.formattedComparable` prettifies both sides, so a library file an older build
  prettified (blank lines around a tab, splitting the sections) and the same source file imported again now differ
  by those blanks and are put to the user as a conflict instead of IDENTICAL. Accepted and to be said in the commit /
  CLAUDE.md note: the old library file really was a different song structurally (three sections for one), and only files
  with a tab or grid touching a paragraph line were affected. No migration (repo rule).

## Manual check
Editor: type `Hello [G]world`, a `{sot}…{eot}` block, then another lyric line, no blanks; Prettify; the preview still
shows one section, not three.
