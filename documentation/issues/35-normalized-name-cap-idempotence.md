# 35 · `normalizedName` is not idempotent when the length cap cuts a word down to `feat`

**Severity:** low · **Area:** `:data:model` (`LibraryFiles.normalizedName`)

`LibraryFiles.kt:93–95`: abbreviations are applied per word before `take(MAX_NAME_LENGTH)`. Input: 115 `a`s +
`" feather"` → first pass ends `…_feat` (cut at 120), second pass maps that word to `ft`. An exported file with such
a name re-imports as a new song instead of `IDENTICAL`.

## Fix

Cap by whole words instead of by characters: join words while the joined length stays within `MAX_NAME_LENGTH`;
only when the very first word alone exceeds the cap, cut that word (a single cut word is stable under a second pass,
since the abbreviation map only holds words shorter than the cap). Keep the "never empty" fallback. Add a test to the
`LibraryFiles` tests (if none exist in `:data:model`, put it next to the `FileNames` tests in
`:data:source:local:implementation`, which already depends on the model): `normalizedName(normalizedName(x)) == normalizedName(x)`
for the input above and for a 300-character title.
