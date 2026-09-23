# Pre-release review plans still open

The sixth pre-release review (2026-09-23, taken at `2065e47f`) wrote 49 plans; 48 of them have landed on `master`.
One is left, because carrying it out as written turned out to be impossible:

| # | Title | Severity |
| --- | --- | --- |
| 05 | Chords written in a comment are never transposed or respelled | wrong chords |

**Why 05 did not land.** The plan's own example, `{comment: Intro: [G] [Em] [C] [D]}`, is a plain comment that
starts with a word and a colon, which the parser reads as a Campfire 3 section heading: it becomes a section label
rather than a comment block. The editor's text transposition would then rewrite the chords in that line while the
model leaves the label as it is, and the test the plan requires — that the model and the text agree — fails. Fixing
it needs a model change the plan does not describe: a label has to remember that it came from a comment, so that the
viewer transposes its chords the way the text transposition does. The plan should be rewritten with that in it
before it is carried out. The user confirmed its default (chords in comments are transposed and respelled).

The rules the plans were written under still apply: load the `code-style` skill before editing, run the unit tests
from the root `CLAUDE.md`, update the docs the plan names, and delete the plan file in the commit that fixes it.
