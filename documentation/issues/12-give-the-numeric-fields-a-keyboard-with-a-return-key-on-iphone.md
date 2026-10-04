# Give the year and duration fields a keyboard with a return key on iPhone, so Next and Done are reachable

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** iOS (iPhone)
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SongMetadataDialog.kt,
presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/NumericImeOptions.kt (new, expect),
presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/NumericImeOptions.android.kt (new),
presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/NumericImeOptions.desktop.kt (new),
presentation/src/iosMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/NumericImeOptions.ios.kt (new),
presentation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/NumericImeOptions.wasmJs.kt (new),
presentation/CLAUDE.md

**Challenged:** amended — the iOS builder is @ExperimentalComposeUiApi and takes a UIKeyboardType (Long); added the opt-in, and a manual check that setting platformImeOptions does not change the keyboard's appearance (dark theme) or the text input path.

## Problem
`SongMetadataField` (`SongMetadataDialog.kt` ~150-154 at 800ebde0b) asks for a number keyboard for Year and Duration
and relies on the keyboard's action key to walk the form or finish it:

```kotlin
keyboardActions = KeyboardActions(onDone = { if (onDone != null) onDone() else keyboardController?.hide() }),
keyboardOptions = KeyboardOptions(
    capitalization = if (field.isNumeric) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
    keyboardType = if (field.isNumeric) KeyboardType.Number else KeyboardType.Text,
    imeAction = if (field == Field.entries.last()) ImeAction.Done else ImeAction.Next,
),
```

Compose Multiplatform 1.12.1 maps `KeyboardType.Number` to `UIKeyboardTypeNumberPad` on iOS
(`androidx/compose/ui/platform/SkikoUITextInputTraits.ios.kt`: `KeyboardType.Number -> UIKeyboardTypeNumberPad`), and
the iPhone number pad has **no return key**. So on an iPhone, in New song, Year's Next never reaches Duration, and
Duration's Done (which creates the song in New song, or puts the keyboard away in Edit song details) cannot be pressed;
the keyboard can only be left by tapping another field or the header. Android's number keyboard has the action key
(live `09_year.png`, `14_duration.png`), and iPad's number pad is the full keyboard, so only iPhone is affected.

## Fix
On iOS only, ask for `UIKeyboardTypeNumbersAndPunctuation` — the numbers page of the full keyboard, which has the
return key (labelled Next/Done from `imeAction`) — through `KeyboardOptions.platformImeOptions`. The field already
drops everything but digits in `onValueChange`, so the extra keys cost nothing.

1. `ui/platform/NumericImeOptions.kt` (commonMain, MPL header):
   ```kotlin
   /**
    * What a field typed in digits passes as its platform IME options: on iOS the numbers page of the full keyboard,
    * since the iPhone number pad `KeyboardType.Number` maps to has no return key, and a form walked with Next could not
    * leave the field. Null everywhere else, where the number keyboard has its action key.
    */
   internal expect val numericPlatformImeOptions: PlatformImeOptions?
   ```
2. Actuals: `null` on Android, desktop and wasmJs; on iOS
   `internal actual val numericPlatformImeOptions: PlatformImeOptions? = PlatformImeOptions { keyboardType(UIKeyboardTypeNumbersAndPunctuation) }`
   (`androidx.compose.ui.text.input.PlatformImeOptions` and its iOS builder; `platform.UIKit.UIKeyboardTypeNumbersAndPunctuation`).
   Checked against the 1.12.1 iOS klib: the builder is `PlatformImeOptions(configure: PlatformImeOptionsConfiguration.() -> Unit)`
   and `keyboardType(Long?)` takes a `UIKeyboardType` (a `Long` in Kotlin/Native); `PlatformImeOptionsConfiguration`
   is `@ExperimentalComposeUiApi`, so the iOS actual needs `@OptIn(ExperimentalComposeUiApi::class)`. The common
   declaration only names the type (`PlatformImeOptions?`, an `expect class` every target has), so the other three
   actuals are a plain `= null`.
3. `SongMetadataField`: add `platformImeOptions = numericPlatformImeOptions.takeIf { field.isNumeric }` to the
   `KeyboardOptions`. Keep `keyboardType = KeyboardType.Number` (it still decides Android's and the web's keyboards).
4. `presentation/CLAUDE.md`: in the list of `ui/platform/` expect/actuals, add one line for `NumericImeOptions`.

Drop this plan if, on an iPhone simulator with the current Compose version, the Year field's number pad already shows a
return/Next key (a Compose upgrade could add an accessory bar).

## Tests
None: platform keyboard configuration.

## Manual check
iPhone simulator (software keyboard on): New song → type a title → Next through to Year: the keyboard shows digits and a
Next key; Next moves to Duration; Duration's key reads Done and creates the song. Edit song details → Duration → Done
puts the keyboard away. Typing a letter after switching pages does nothing. Android: the number keyboard is unchanged. In the dark theme the Year/Duration
keyboard is as dark as the Title field's (the configuration has a `keyboardAppearance` of its own, which must not
override the window's interface style), and the field still edits, selects and pastes like the others (the
configuration also carries `usingNativeTextInput`; nothing should change by passing options).
