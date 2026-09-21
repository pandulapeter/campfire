# 59 · The editor tokenizes the whole song again every time the caret moves or a selection handle is dragged

**Severity:** performance (all platforms, worst with a low-end Android IME and in the web build; every caret move, selection drag and IME composition update on a long song) · **Area:** `:presentation` (`screens/songEditor/ChordProOutputTransformation.kt`, `SongEditorScreen.kt`)

## Symptom
Open a long song (400 lines, 15–18 thousand characters) in the editor. Tap somewhere to place the caret, hold an
arrow key, or drag a selection handle across a few lines: the caret and the handles lag behind the finger, and on a
slow phone the selection visibly stutters. No text has changed during any of this, yet each of those events costs as
much as typing a character does.

## Cause
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/ChordProOutputTransformation.kt:35-49`:

```kotlin
override fun TextFieldBuffer.transformOutput() {
    ChordProHighlighter.tokenize(originalText.toString()).forEach { token ->
        addStyle(…)
    }
}
```

The field runs an output transformation inside a `derivedStateOf` that reads the *whole* value of the state
(Compose 1.12.0, `TransformedTextFieldState.kt:118-129`):

```kotlin
derivedStateOf {
    calculateTransformedText(untransformedValue = textFieldState.value, outputTransformation = transformation, …)
}
```

and `TextFieldState.value` is a `TextFieldCharSequence`: the text **and** the selection **and** the IME composition.
A new one is committed for every caret move, every pointer event of a selection drag and every composition update,
so `transformOutput()` runs for each of them, and each run tokenizes the document from its first character
(`ChordProHighlighter.tokenize`: a `substring`, a `trim` and the directive and chord patterns per line) before it
adds its ~1 500 styles. The tokens are a function of the text alone.

Two details of the finding, corrected: `originalText.toString()` is not a copy — `originalText` is the `String`
the framework itself made with `buffer.toString()` for that value (`TextFieldBuffer.toTextFieldCharSequence`), and
`String.toString()` returns the receiver. For the same reason the text of two values that differ only in their
selection is two *equal* strings rather than one instance, so the check below has to compare contents, which for
16 000 characters is a `memcmp` and far below anything measurable here. And the transformation object is recreated
whenever the colour scheme changes (`SongEditorScreen.kt:465`, `remember(colorScheme)`), which on a theme cross-fade
is every frame — a cache held by the transformation itself would be thrown away exactly then.

## Fix
Keep the last text and its tokens, outside the object that holds the colours.

1. `ChordProOutputTransformation.kt`: add the cache and hand it to the transformation.

   ```kotlin
   /**
    * The tokens of the text the editor holds, kept from one run of [ChordProOutputTransformation] to the next. The
    * field runs its output transformation for every new value of its state, and most of those differ from the one
    * before only in where the caret is: a tap, an arrow key, every pointer event of a selection being dragged. The
    * tokens depend on the text alone, so they are only worked out again once that has changed.
    *
    * It is a holder of its own rather than a field of the transformation, because the transformation carries the
    * colours and is made anew on every frame of a theme change, while this has to outlive all of them.
    */
   internal class ChordProTokenCache {

       private var text: String? = null
       private var tokens = emptyList<ChordProHighlighter.Token>()

       /**
        * Compared by content: the field makes a new string for every value, the ones that only moved the caret
        * included, so the instance says nothing about whether the text is the same.
        */
       fun tokensOf(text: String): List<ChordProHighlighter.Token> {
           if (text != this.text) {
               tokens = ChordProHighlighter.tokenize(text)
               this.text = text
           }
           return tokens
       }
   }
   ```

   The class gets a first constructor parameter `private val tokenCache: ChordProTokenCache,` and `transformOutput`
   becomes:

   ```kotlin
   override fun TextFieldBuffer.transformOutput() {
       tokenCache.tokensOf(originalText.toString()).forEach { token ->
           addStyle(…)                                  // the `when` and the arguments stay as they are
       }
   }
   ```

   `of(…)` takes `tokenCache: ChordProTokenCache` as its first parameter and passes it on
   (`tokenCache = tokenCache,`). Extend the class KDoc with one sentence: "The tokens come through a
   [ChordProTokenCache], so a value that only moved the caret costs the styles and nothing else."

2. `SongEditorScreen.kt`, `ChordProTextField` (`:464-471`):

   ```kotlin
   val colorScheme = MaterialTheme.colorScheme
   val tokenCache = remember { ChordProTokenCache() }
   val outputTransformation = remember(colorScheme, tokenCache) {
       ChordProOutputTransformation.of(
           tokenCache = tokenCache,
           primaryColor = colorScheme.primary,
           secondaryColor = colorScheme.onSurfaceVariant,
           outlineColor = colorScheme.outline,
       )
   }
   ```

What must not change:
- The cache holds plain fields, not snapshot state: it is written inside a `derivedStateOf` calculation, where a
  state write is an error, and nothing has to be invalidated by it — the same text always gives the same tokens.
- Do not key the cache on `destination.fileName` or clear it on a revert / transpose; the content comparison already
  covers every way the text can change.
- Do not try to skip the `addStyle` loop as well. The buffer handed to `transformOutput` is a new one for every
  value, and a transformation that adds nothing makes the field draw unstyled text for that frame.
- The token ranges stay offsets into the raw text; nothing here may trim or normalize `text` before tokenizing it.

What this leaves: a keystroke still tokenizes the whole document (per-line token caching would take that from
O(document) to O(line), but it needs the highlighter to expose its per-line state — the "inside a tab" flag — and is
a larger change than this finding asks for), and a caret move still adds ~1 500 styles. Plan 39 makes the tokenizer's
patterns themselves linear; the two are independent and either can land first.

## Tests
None (UI is untested; `ChordProHighlighter` itself is unchanged, and `ChordProHighlighterTest` keeps pinning it).

## Verify
1. `./gradlew :app:desktop:run`, open the longest song available in the editor.
   - Type inside a chord, a directive, a comment and a tab block: the colours follow every keystroke exactly as
     before (chord bold in the accent colour, directive name bold, value in `onSurfaceVariant`, comment italic in
     the outline colour, nothing coloured inside `{sot}`…`{eot}`).
   - Undo, redo, Revert (overflow menu), and the transposition stepper: the colouring is right after each — these are
     the whole-text replacements.
   - Switch the theme while the editor is open (system dark mode, or Settings on a second run): colours cross-fade,
     the highlighting stays in place.
2. Put a temporary counter or breakpoint in `ChordProHighlighter.tokenize`: it is hit once per text change and not at
   all while clicking around, holding an arrow key, or dragging a selection (on Android: dragging a handle). Remove
   it again.
3. On Android with an IME that composes (Gboard, swipe typing): the underlined composing word is coloured correctly
   while it is composed and after it is committed.
4. `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`

## Docs
`presentation/CLAUDE.md`, `screens/songEditor/` bullet, the sentence "`ChordProOutputTransformation` colours the text
from `ChordProHighlighter`'s tokens in the viewer's own colours, so a chord looks like a chord on both sides of the
divider." — append: "The field runs that transformation for every caret move as well as for every edit, so the tokens
are kept by `ChordProTokenCache` and only worked out again once the text has changed."

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/ChordProOutputTransformation.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing. Plan 39 (`:chordpro`, the tokenizer's patterns) is independent. The editor plans (05 and 46 for certain,
and by their subject 13, 14 and 47) edit `SongEditorScreen.kt` too, in other functions of it; this one only touches
the first lines of `ChordProTextField`, so it has to be scheduled after or before them rather than alongside.
