# Parenthesize the extension of a Nashville number where it starts with a digit (`5(7)`, `b7(7sus4)`, `#4(6/9)`)

**Challenged:** amended — `ChordProTabWrapper.isChordLine` trims `(` and `)` off both ends of every word before asking `isDisplayedChordName`, so `5(7)` became `5(7` and a preformatted row of numbered chords stopped being a chord row (its lyrics wrapped apart from it, probed); the wrapper now also tries the word with only its unmatched parentheses taken off, with a test.

**Kind:** readability  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProNashville.kt`,
`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProTabWrapper.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProNashvilleTest.kt`, `chordpro/CLAUDE.md`,
`CLAUDE.md`

The user chose this (option B of the earlier decision plan): Arabic numbers set the extension off in parentheses.
Roman numerals are unchanged.

## Problem

Under Settings → Songs → Nashville numbers, `ChordProNashville.number` (`:chordpro`, dac1d9d59) writes the step and
the rest of the chord name straight after each other:

```kotlin
val numbered = if (isRoman) numeral(degree, reader.quality, suffix) else nashvilleDegrees[degree] + nashvilleSuffix(reader.quality, suffix)
```

```kotlin
private fun nashvilleSuffix(quality: String?, suffix: String) =
    if (quality != null && quality in minorQualities) MINOR_SIGN + suffix.removePrefix(quality) else suffix
```

with `nashvilleDegrees = listOf("1", "b2", "2", "b3", "3", "4", "#4", "5", "b6", "6", "b7", "7")`. Where the suffix
starts with a digit the two numbers run together: `E7` in A is `57` (fifty-seven, to a screen reader too), `Bb7sus4`
in C is `b77sus4`, `Ab13` is `b613`, `C6` is `16`, `C5` is `15`, `F#6/9` is `#46/9` (which also reads as `#4` over a
`9` bass), `D7#9` is `27#9` (review screenshot `android/70_nashville_lyrics.png`). A handwritten chart raises the
extension, which is what tells it from the step; a text cannot, so it gets parentheses.

Every place a numbered chord appears takes its name from this one function, so the fix is in `:chordpro` alone:

- the song page's chord rows, grids, tab chord rows and comments, and the editor's preview — all via
  `ChordProNotation.toNotation` → `numbering(...)` → `ChordProNashville.number`;
- the Chords section's diagram names and the Chord shapes sheet — `ChordProNotation.shownNames` (same `rename`),
  consumed by `presentation/.../ui/chords/SongChords.kt` (`name = shownNames.getValue(name)`);
- the PDF, its drawn text and its selectable text — `PrintLayout` lays out the song `toNotation` returned; nothing in
  `presentation/.../ui/print` or `ui/screens/songDetails` parses a chord name's parentheses (checked: no `'('` or
  `removeSurrounding` there);
- the screen reader — `SongLyrics.kt` hands the shown lines to `contentDescription`, and
  `SongChordsSection.chordCellDescription` reads `"%1$s, that is %2$s"` with the shown name; `5(7), that is B7` is read
  as "5 7" rather than "fifty-seven", an improvement.

Two readers recognize a numbered chord as a page shows it, and must accept the new form:

- `ChordProNashville.isDegree`, used by `ChordProChordNames.isDisplayedChordName` (which `ChordProTabWrapper` uses to
  keep a row of chords over a staff with its columns) and by `ChordProHighlighter.chordsOfShownText` (coloring `[5]` in a
  numbered comment). It checks the rest after the step with `isChordName("C" + rest)`. Traced against
  `ChordProChordNames.read`/`groupEnd`: `C(7)`, `C(13)`, `C(7sus4)`, `C(7#9)`, `C(7)(b9)` are chord names (a group takes
  a number then alterations), but **`C(6/9)` is not** (`groupEnd` rejects the `/`: it is no `,`, digit, omission or
  alteration) and neither is `C(7alt)` (`alt` is only read right after the number, outside a group). So
  `isDegree("#4(6/9)")` would be false and a tab chord row holding it would stop being a chord row.
- `ChordProTabWrapper.isChordLine` (`wrapPreformatted`, a run of chord names over lyrics with no staff) does not hand
  the word over as it is: `ChordProSyntax.words(line).map { it.value.trim('(', ')') }` strips every parenthesis at both
  ends, meant for an optional chord `(G)` and for a group written across words (`(G  C  D)`). `5(7)` becomes `5(7`,
  `(5(7))` becomes `5(7`, and neither is a chord to anything, so a row holding one is prose: probed at dac1d9d59 with
  this plan's `number`, `wrapPreformatted(listOf("    5(7)       #4(6/9)", "Hello darkness my old friend"), 14)` gives
  `[[    5(7)], [#4(6/9)], [Hello darkness], [my old friend]]` where `57` / `#46/9` gave
  `[[    57, Hello darkness], [#46/9, my old friend]]` — the chords no longer travel with their words. (The same trim
  already breaks a standard `C(add9)` in such a row today; this fixes it too.)
- The document importer does **not** read numbers at all: `ChordSheetConverter.chord` only takes
  `isChordName`/`latinExpanded` names, and `chordpro/CLAUDE.md` says "nor are number charts read (`[1]` is as often an
  ending as a chord)". So a numbered PDF re-imported today brings its number rows in as lyric text, and it will do
  exactly the same after this change — no new loss, nothing to fix there.

Nothing collides with ChordPro syntax: a numbering is only ever shown, never written into a text (`convertText` treats a
numbering as the standard notation), and the parentheses are added after the file has been parsed.

## Fix

In `ChordProNashville` (Arabic numbers only):

1. **Which suffixes get parentheses — exactly those whose text right after the step starts with an ASCII digit.**
   That is the only case where the step and the extension run together; a letter or a sign already separates them.
   With `rest` the text `nashvilleSuffix` would write today:
   - `rest` starts with a digit → parenthesize: `7`, `9`, `13`, `5` (power chord), `2`, `6`, `7sus4`, `9sus4`, `7#9`,
     `7b5`, `7alt`, `6/9`, `13#11` → `5(7)`, `b7(7sus4)`, `b6(13)`, `1(5)`, `1(6)`, `#4(6/9)`, `2(7#9)`.
   - `rest` starts with a letter or a sign → unchanged: `maj7`/`M7`/`Maj7` (`1maj7`, `1M7`), `sus`/`sus4`/`sus2`
     (`5sus4`, `5sus`), `add9` (`1add9`), `dim`/`°`/`ø7` (`7dim`, `7°`, `7ø7`), `aug`/`+` (`1+`), `(add9)` (`1(add9)`,
     already parenthesized by the file — no `1((add9))`).
   - **A minor chord keeps its dash outside and its number after it, unparenthesized**: `6-`, `6-7`, `2-7`, `6-7b5`,
     `6-maj7` stay as they are. The `-` already ends the step, and `2-7` is the most common chord of a Nashville chart,
     written exactly that way. (`6-(7)` is the alternative; see DECISIONS in the report.) Since `nashvilleSuffix` puts
     the `-` first, checking the first character of its result gives this for free.
2. **A suffix that already holds a parenthesized group parenthesizes only what comes before it**, so nothing nests:
   `C7(b9)` → `1(7)(b9)`, `G7sus4(b9)` → `5(7sus4)(b9)`. Every character the file wrote is kept; only `(` and `)` are
   inserted.
3. **A slash bass stays outside**: `G7/B` → `5(7)/7`, `C6/9/E` → `1(6/9)/3` (`notes()` splits only a letter bass off;
   `6/9`'s `9` is an added degree and stays in the suffix, so it lands inside the parentheses).
4. **An optional chord keeps its own parentheses around the whole**: `(G7)` → `(5(7))`, `(G)` → `(5)` as today. This is
   the one place two closing parentheses meet; it stays unambiguous (`isParenthesized` and `isDegree`'s
   `removeSurrounding("(", ")")` take the outer pair only) and optional chords with a numeric extension are rare.
5. **Roman numerals are unchanged** (`V7`, `bVII7sus4`, `IVmaj7`, `ii7`): a numeral is letters, so the digit after it
   is already set off, and Roman analysis writes figures straight after the numeral.

Code (in `number`, replace the Arabic branch; the helper is private):

```kotlin
val numbered = if (isRoman) numeral(degree, reader.quality, suffix) else nashvilleDegrees[degree] + extended(nashvilleSuffix(reader.quality, suffix))
```

```kotlin
/**
 * [rest], what follows a step in numbers, with its extension in parentheses where it starts with a digit: written
 * straight after the step, `57` and `b77sus4` would read as one number. A group the file already wrote (`7(b9)`) is
 * left after the parentheses rather than put inside them.
 */
private fun extended(rest: String): String {
    if (rest.firstOrNull()?.let { it in '0'..'9' } != true) return rest
    val group = rest.indexOf('(').takeIf { it >= 0 } ?: rest.length
    return "(" + rest.substring(0, group) + ")" + rest.substring(group)
}
```

In `isDegree`, accept the step's extension in parentheses by reading it without them as well (the inverse of
`extended`):

```kotlin
val rest = chord.substring(stepEnd)
return ChordProChordNames.isChordName("C" + rest) || ChordProChordNames.isChordName("C" + withoutExtensionParentheses(rest))
```

```kotlin
/** [rest] with the parentheses [extended] puts around an extension taken off (`(6/9)` → `6/9`, `(7)(b9)` → `7(b9)`). */
private fun withoutExtensionParentheses(rest: String): String {
    val close = rest.indexOf(')')
    return if (rest.startsWith('(') && close > 1 && rest[1] in '0'..'9') rest.substring(1, close) + rest.substring(close + 1) else rest
}
```

In `ChordProTabWrapper.isChordLine`, keep the old trim for everything it was for, and also accept a word whose own
parentheses are part of its name, taking off only the ones that open or close a group:

```kotlin
private fun isChordLine(line: String): Boolean {
    val words = ChordProSyntax.words(line).map { it.value }
    return words.any(::isChordWord) && words.all { word ->
        val trimmed = word.trim('(', ')')
        trimmed.isEmpty() || trimmed == ROMAN_ONE || isChordWord(word) || trimmed.none(Char::isLetterOrDigit) || repeatCountRegex.matches(trimmed)
    }
}

/**
 * Whether [word] is a chord name as a page shows it, in parentheses of its own (`(G)`) or opening or closing a group of
 * them written across words (`(G`, `D)`) — the parentheses inside a name kept, since they are part of it (`5(7)`, a
 * numbered extension, and `C(add9)`).
 */
private fun isChordWord(word: String) =
    ChordProChordNames.isDisplayedChordName(word.trim('(', ')')) || ChordProChordNames.isDisplayedChordName(word.withoutUnmatchedParentheses())

/** [this] without the `(` it starts with or the `)` it ends with where nothing in it closes or opens them. */
private fun String.withoutUnmatchedParentheses(): String {
    var word = this
    while (word.startsWith('(') && word.count { it == '(' } > word.count { it == ')' }) word = word.substring(1)
    while (word.endsWith(')') && word.count { it == ')' } > word.count { it == '(' }) word = word.dropLast(1)
    return word
}
```

(`(5(7)` → `5(7)`, `5(7))` → `5(7)`, `(5(7))` stays and `isDegree` takes its outer pair; `(x2)` still reaches the
repeat count through the trimmed form.) Update the KDoc of `isChordLine` ("a name may be put in parentheses") to add
"and keeps its own (`5(7)`)".

The slash handling in `isDegree` needs no change: `#4(6/9)` has its `/` before the closing parenthesis, so
`arabicStepEnd(name, slash + 1) == name.length` is false and the whole word is the chord; `5(7)/7` splits as before.

KDoc of `ChordProNashville`: change the first bullet's example `1- b6 b3 b7 57` to `1- b6 b3 b7 5(7)` and extend the
second bullet: "…every other quality is kept as the file writes it (`2-7`, `4maj7`, `5sus4`, `7dim`), an extension that
starts with a digit in parentheses, since written straight after the step it would read as one number with it (`5(7)`,
`b7(7sus4)`, `1(6/9)`, a group of the file's own left after them: `1(7)(b9)`)."

Docs:

- `chordpro/CLAUDE.md`, the **numberings** paragraph: `` (`Am F C G E7` is `1- b6 b3 b7 57`, … `` →
  `` `1- b6 b3 b7 5(7)` ``, and after "a minor chord `-` in numbers and every other quality kept" add "— an extension that
  starts with a digit in parentheses (`5(7)`, `#4(6/9)`, the minor's number after its dash as it is: `2-7`) —".
- root `CLAUDE.md`, "Every file is in the standard chord notation" bullet, after `(` `1 4 5 6-`, `I IV V vi` `)`: add
  ", an extension that starts with a digit set off in parentheses in numbers (`5(7)`, never `57`)".

Width: numbered names grow by two characters. The song page, the preview and the PDF measure the names they lay out,
so nothing needs adjusting; see plans 35 (Chord shapes sheet names wrap) and 55 (PDF diagram names kept inside their
column), which make the longer names fit there.

## Tests

In `ChordProNashvilleTest` (`./gradlew :chordpro:desktopTest`), update the pinned expectations:

- `a minor chord is written with a dash and every other quality is kept`: `assertEquals("17", nashville("C7"))` →
  `"1(7)"`; `assertEquals("16/9", nashville("C6/9"))` → `"1(6/9)"`. Keep `6-`, `6-7`, `6-7`, `6-7b5`, `6-maj7`,
  `1maj7`, `1M7`, `7dim`, `5sus4` unchanged (they pin that letters, signs and the minor dash take no parentheses).
- `a minor key is numbered from its own tonic`: `listOf("1-", "b6", "b3", "b7", "57")` → `listOf("1-", "b6", "b3", "b7", "5(7)")`.
- `a degree is recognized as a page shows it`: replace `"57"` and `"16/9"` in the accepted list with `"5(7)"` and
  `"1(6/9)"`, and add `"b7(7sus4)"`, `"#4(6/9)"`, `"5(7)/7"`, `"1(7)(b9)"`, `"(5(7))"`, `"2(7alt)"`, `"1(add9)"`.

Add one test, `an extension that starts with a digit is set off in parentheses`:

```kotlin
assertEquals("b7(7sus4)", nashville("Bb7sus4"))
assertEquals("b6(13)", nashville("Ab13"))
assertEquals("#4(6/9)", nashville("F#6/9"))
assertEquals("2(7#9)", nashville("D7#9"))
assertEquals("1(5)", nashville("C5"))
assertEquals("5(7)/7", nashville("G7/B"))
assertEquals("1(6/9)/3", nashville("C6/9/E"))
assertEquals("1(7)(b9)", nashville("C7(b9)"))
assertEquals("1(add9)", nashville("C(add9)"))
assertEquals("(5(7))", nashville("(G7)"))
assertEquals("5sus", nashville("Gsus"))
assertEquals("2-7", nashville("Dm7"))
assertEquals("bVII7sus4", roman("Bb7sus4"))
assertEquals(listOf("1", "5(7)"), lyricChords(shown("{key: C}\n[C]a [G7]b")))
assertEquals(1, ChordProHighlighter.chordsOfShownText("Intro: [#4(6/9)]", ChordNotation.NASHVILLE).size)
```

(`isDegree("#4(6/9)")` and `isDegree("2(7alt)")` in the list above are what fail without the
`withoutExtensionParentheses` branch.)

In `a row of numbers over a staff travels with its columns, and a lyric line of I does not`, add (fails without the
`isChordLine` change, probed):

```kotlin
assertEquals(
    listOf(listOf("    5(7)", "Hello darkness"), listOf("#4(6/9)", "my old friend")),
    ChordProTabWrapper.wrapPreformatted(listOf("    5(7)       #4(6/9)", "Hello darkness my old friend"), maxColumns = 14),
)
assertEquals(
    listOf(listOf("(5(7)", "Hello darkness"), listOf("1(add9))", "my old friend")),
    ChordProTabWrapper.wrapPreformatted(listOf("(5(7)          1(add9))", "Hello darkness my old friend"), maxColumns = 14),
)
assertEquals(
    listOf(listOf("    C(add9)", "Hello darkness"), listOf("(G)  x2", "my old friend")),
    ChordProTabWrapper.wrapPreformatted(listOf("    C(add9)    (G)  x2", "Hello darkness my old friend"), maxColumns = 14),
)
```

Probed at dac1d9d59 with every change of this plan applied: the new test, the added assertions and the rest of
`:chordpro:desktopTest` pass; only the two pinned expectations named above fail until they are updated. (`57` and
`16/9` still pass `isDegree`, so that list's replacements are for meaning, not for a failure.) The Roman test cases stay as they are. No other test pins a
numbered extension (`SongChordsTest` uses triads only; checked).

## Manual check

A song with `{key: C}` and `[C]a [G7]b [Bb7sus4]c [Ab13]d [F#6/9]e [D7#9]f [Am7]g [G7/B]h [(G7)]i` plus a
`{start_of_tab}` run with no staff whose chord row reads `G7          F#6/9` over a line of lyrics, under Settings → Songs → Nashville numbers: the page shows `1 5(7) b7(7sus4)
b6(13) #4(6/9) 2(7#9) 6-7 5(7)/7 (5(7))`, the run's chord row still wraps together with its lyrics on a narrow window (each numbered chord over its word), the Chords section
and the Chord shapes sheet name the diagrams `5(7)` etc. with their letters after them, TalkBack/VoiceOver reads "5 7,
that is G7" rather than "fifty-seven", and the exported PDF shows and copies the same text. Under Roman numerals the
same song still reads `V7 bVII7sus4 …`.
