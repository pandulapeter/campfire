# Drop C0 control characters and bidirectional overrides from imported text
**Challenged:** amended — U+200E, U+200F and U+061C (LRM, RLM, ALM) are no longer stripped: they are the marks Hebrew and Arabic text legitimately carries to place neutral punctuation, the same audience plan 16 serves, and the reported fault (a chord line broken by an override) is covered by the embeddings, overrides and isolates; the test also states a concrete expectation.

**Kind:** robustness  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordSheetConverter.kt`, `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordSheetConverterTest.kt`

## Problem
`ChordSheetConverter.clean()` drops soft hyphens, zero-width characters and BOMs and maps spaces, but lets C0 controls
(U+0000–U+001F other than whitespace, U+007F) and the bidi controls U+202A–202E and U+2066–2069 through into the
library file. At 8c267e01a `"‮C   G\nHello world\u0007 x"` converts to `‮C   G\nHello world\u0007 x` — the leading override stops the
first chord line being recognised as a chord line (`C` is no longer a chord token), and the BEL ends up in the file.

## Fix
In `clean()` (the `when (c)` list) drop U+202A–U+202E and U+2066–U+2069 (embeddings, overrides, isolates; NOT the marks U+200E, U+200F and U+061C, which RTL lyrics use for their punctuation), plus every char with
`c.code < 0x20` that is not whitespace (whitespace ones are already mapped to spaces by the `else` arm, since `isWhitespace`
is checked first; keep that order) and U+007F. Do not touch `ChordProLiteralText`.

## Tests
`ChordSheetConverterTest`: with `ChordSheet.ofPlainText("\u202eC   G\nHello world\u0007 x")` the result equals
`"[C]Hello [G]world x\n"` (verify the exact alignment when writing it; the point is that the first line is a chord line
and neither U+202E nor U+0007 survives); a tab and a NBSP in lyrics still become spaces; U+200F in a lyric survives.

## Manual check
None.
