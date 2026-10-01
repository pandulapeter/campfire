# Wrap at the last word boundary, and never inside a grapheme cluster

**Kind:** output quality  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayout.kt`, `presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintLayoutTest.kt`
**Challenged:** amended — the step-back must not end on a lone high surrogate (it keeps today's step forward when it would reach `start`), the "word" after a break opportunity ends at the next U+200B as well as a space, and skin-tone modifiers and flag pairs, which the low-surrogate rule does not catch, are named explicitly.

## Problem

```kotlin
if (end < text.length) {
    val space = maxOf(text.lastIndexOf(' ', end - 1), text.lastIndexOf('​', end - 1))
    if (space >= start && space - start > (end - start) / 2) end = space + 1
}
```

A space in the first half of the line is ignored, so `Oh averyveryverylongwordthatalmostfits` is cut inside the word
although breaking after "Oh " would keep it whole. And only a surrogate pair is protected: a decomposed `ő`
(o + U+030B, what macOS file sources hand out), a ZWJ emoji, a variation selector or a skin-tone modifier can be split
from its base at a column edge.

## Fix

- Take the last break opportunity (`' '` or U+200B, as today) whenever `space > start` **and the word after it would
  fit a line on its own** (`measure(text.substring(space + 1, next)) <= width`, `next` being the next `' '` or U+200B
  after `space`, or the end); otherwise keep the hard cut, which is right for a word longer than a column. `end` only
  ever moves back, so the fragments still concatenate to the input and chord positions still map by character index.
- Then keep grapheme clusters whole: while `end - start > 1` and `end < text.length` and the cut would split a cluster,
  `end--`. The cut splits one when `text[end]` is a low surrogate; or a combining mark (`Char.category` is
  `NON_SPACING_MARK`, `ENCLOSING_MARK` or `COMBINING_SPACING_MARK` — common stdlib); or U+200D, U+FE0E, U+FE0F; or
  `text[end - 1]` is U+200D; or the code point starting at `end` is an emoji modifier (U+1F3FB–U+1F3FF) or a regional
  indicator (U+1F1E6–U+1F1FF) preceded by another regional indicator (code points read with the surrogate pair, not
  the `Char`). If stepping back reaches `start + 1` while still inside a cluster, step **forward** past the end of the
  cluster instead (today's `end + 1` for a pair, generalised), so a fragment never ends on a high surrogate and the
  loop always advances.
- The existing surrogate line folds into this rule.

## Tests

`PrintLayoutTest`: the "Oh averyvery…" case breaks after "Oh "; `"ő"` repeated across a boundary never yields a
fragment starting with U+030B; a ZWJ family emoji, a 👍🏽 and a 🇭🇺 are not split; a width of one character over a string of emoji still
advances and never yields a lone surrogate; the existing surrogate and preserved-spaces tests
still pass (fragments concatenate to the input).

## Manual check

None beyond the tests.
