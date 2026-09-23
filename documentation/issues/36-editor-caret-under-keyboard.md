# 36 — Editor: the caret goes under the on-screen keyboard anywhere but at the end of the song

**Severity:** typing blind (Android confirmed from the code; iOS to be checked) · **Area:** `:presentation`
(`screens/songEditor/SongEditorScreen.kt`)

**Read, not run.** This was found by reading the code at HEAD (2065e47f) and Compose Foundation 1.12.0's text field
sources; it has not been reproduced on a device. The "Verification" section below is how to confirm it, and
confirming it is the first step of the work.

## What the user sees

A song longer than the screen, opened in the editor on a phone. Tap a line in the lower half of the screen: the
keyboard comes up over that line and the caret stays behind it. Typing, pressing Enter, moving with the arrow keys of
the keyboard — everything happens under the keyboard, and the text only scrolls up once the caret passes the bottom
of the *screen*, which is behind the keyboard too. Only at the very end of the document does the text stop above the
keyboard, which is the case AND-054 step 2 checks.

## Cause

The app is laid out under the keyboard rather than resized by it (edge to edge, `KeyboardAwarePadding` in
`CampfireApp.kt:317-351`): the keyboard reaches the screens only as the bottom of the `contentPadding` they are
handed. The editor's field applies that bottom padding **only when it is scrolled to its end**
(`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt:574-590`,
`:615-619`):

```kotlin
    val insetBottomPadding = contentPadding.calculateBottomPadding() + 32.dp
    val insetBottomPaddingPx = with(density) { insetBottomPadding.roundToPx() }
    ...
    var respectsBottomInset by remember { mutableStateOf(false) }
    LaunchedEffect(scrollState, insetBottomPaddingPx) {
        snapshotFlow { scrollState.value to scrollState.maxValue }.collect { (value, maxValue) ->
            respectsBottomInset = if (respectsBottomInset) value >= maxValue - insetBottomPaddingPx else value >= maxValue
        }
    }
    ...
            .padding(
                start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
                end = contentPadding.calculateEndPadding(layoutDirection) + 16.dp,
                bottom = if (respectsBottomInset) insetBottomPadding else 0.dp,
            ),
```

That conditional exists for the static part — the navigation bar and the 32dp of breathing room after the last line,
which only mean anything once scrolled past (see the long comment at `:576-584`). But the same number also carries
the keyboard, and the keyboard is not something to scroll past: it covers the bottom of the field's viewport whatever
the scroll position. With the padding off, the field's viewport runs down behind the keyboard, and the field's own
bring-into-view only keeps the caret inside that viewport (`TextFieldCoreModifier.updateScrollState`,
Compose Foundation 1.12.0, `TextFieldCoreModifier.kt:416-511`) — which the keyboard is covering. There is no
`imePadding` / `imeNestedScroll` anywhere in `presentation/src`.

The comment at `:610-614` ("applying the inset a second time here shrank the field to a couple of lines") is about
applying the keyboard *twice*; applying it *once*, always, is what this plan does.

iOS: `ComposeUIViewController` is created with the default configuration (`app/ios/.../CampfireViewController.kt:48`),
whose focus behaviour may pan the view to keep a focused field above the keyboard. Whether it does so for a caret deep
inside a tall multiline field is not known from the code; with this fix the caret is kept above the keyboard by the
field itself, so a pan would have nothing to do.

## The change

Invoke the **`code-style`** skill before the first edit.

Split the bottom padding into its two parts: what the keyboard covers beyond the resting edge (always applied, so the
field's viewport ends at the top of the keyboard) and the resting edge plus the breathing room (still only once
scrolled to the end, with the existing hysteresis). When the viewport shrinks as the keyboard comes up, the text field
follows the caret by itself: `updateScrollState` re-runs whenever the container size changes
(`containerSize != previousContainerSize`, `TextFieldCoreModifier.kt:454`), so the caret is scrolled into the part
that is still visible.

The resting edge is the bottom of `WindowInsets.contentEdges` (introduced by plan 35: `systemBars.union(displayCutout)`),
which is exactly what `CampfireApp` builds the editor's `contentPadding` from (`songDetailsContentPadding`, bottom =
that inset, `coveredHeight = 0`). The difference between the two is the keyboard's overlap, never negative.

Replace `:571-590` with:

```kotlin
    val bodyLarge = MaterialTheme.typography.bodyLarge
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    // The bottom of the padding this screen was handed is the navigation bar, or the keyboard wherever that reaches
    // higher, and the two are spent differently here. The keyboard covers the bottom of the field whatever it is
    // scrolled to, so what it covers beyond the bar is always taken off the field: the field then ends at the top of
    // the keyboard, and keeping the caret in view - which it does whenever its size changes - keeps it above it. The
    // bar and the room after the last line only mean anything once scrolled past, so they are spent at the end alone.
    val restingBottomInset = WindowInsets.contentEdges.asPaddingValues().calculateBottomPadding()
    val keyboardPadding = (contentPadding.calculateBottomPadding() - restingBottomInset).coerceAtLeast(0.dp)
    val endPadding = restingBottomInset + 32.dp
    val endPaddingPx = with(density) { endPadding.roundToPx() }
    // Unlike SongPreview's bottom padding, which sits inside its own verticalScroll and is therefore only ever
    // spent once scrolled past the last line, this field's padding is outside the scrolling BasicTextField manages
    // internally - there is no way to hand it a padding that only counts once the content runs out. ...
    // (the rest of the existing comment unchanged)
    var respectsBottomInset by remember { mutableStateOf(false) }
    LaunchedEffect(scrollState, endPaddingPx) {
        snapshotFlow { scrollState.value to scrollState.maxValue }.collect { (value, maxValue) ->
            respectsBottomInset = if (respectsBottomInset) value >= maxValue - endPaddingPx else value >= maxValue
        }
    }
```

and the padding (`:615-619`):

```kotlin
            .padding(
                start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
                end = contentPadding.calculateEndPadding(layoutDirection) + 16.dp,
                bottom = keyboardPadding + if (respectsBottomInset) endPadding else 0.dp,
            ),
```

At the end of the document with the keyboard up the total is the same as today (`keyboard − bar + bar + 32dp` =
`keyboard + 32dp`), and with the keyboard down it is the same as today everywhere (`keyboardPadding` is 0). Reword the
comment at `:610-614` to: "The keyboard reaches the field only through the content padding this screen was handed,
see CampfireApp, and only the part of it that covers the field is applied, once; applying the whole inset a second
time shrank the field to a couple of lines. There is no top padding for the same reason the bottom one is split…".

Imports: `androidx.compose.foundation.layout.WindowInsets`, `androidx.compose.foundation.layout.asPaddingValues`,
`com.pandulapeter.campfire.presentation.ui.contentEdges`.

Reading the insets in composition recomposes `ChordProTextField` on every frame of the keyboard's animation. It does
so already: `contentPadding.calculateBottomPadding()` at `:574` reads the keyboard in composition today. Only this
composable is affected, not the app around it.

The preview pane (`SongPreview`, `:664`) keeps its padding inside its `verticalScroll`, as today; it has no caret.

## Tests

- **No unit test is possible** (UI, untested by policy).
- Compile check: `./gradlew :presentation:compileDebugKotlinAndroid :presentation:compileKotlinIosSimulatorArm64 :presentation:compileKotlinDesktop :presentation:compileKotlinWasmJs`

## Verification

1. Android emulator or phone, portrait. Make a song of ~100 lines (paste a long song, or import
   `documentation/testing/fixtures`' longest `.cho`). Open it in the editor, Edit pane.
2. Scroll so that line ~40 is near the bottom of the screen, tap it.
   - **Before:** the keyboard covers the caret; typing and Enter happen out of sight.
   - **After:** as the keyboard slides up, the text scrolls with it and the caret sits just above the keyboard.
     Enter moves to a new line that stays visible; typing keeps the caret in view.
3. Close the keyboard (Back): the field fills down to the navigation bar again, text scrolling under it edge to edge.
4. Scroll to the very end with the keyboard up: the last line sits 32dp above the keyboard (AND-054 step 2 still
   holds), and scrolling back up by more than that gives the room up, without the two states chasing each other
   (the hysteresis).
5. Landscape on a phone (the editor's shortcuts rows folded): same as step 2.
6. Split pane on a tablet emulator (≥840dp): the field side behaves as step 2; the preview side scrolls under the
   keyboard as before.
7. **iOS** simulator (IOS-041 in `02-ios.md`), same steps 2–4. If iOS already panned the view before the
   fix, check that after it there is no double movement (the view does not also pan).
8. Desktop and web: no keyboard, no change.

Add a step to `documentation/testing/01-android.md` AND-054 and to IOS-041 in `02-ios.md`: "Editor:
tap a line in the lower half of a long song; the caret stays above the keyboard as it opens and while typing."

## Docs

- `presentation/CLAUDE.md`, `screens/songEditor/` bullet, after "follows the caret because the field asks its
  ancestors to bring the caret into view": add "The part of the keyboard that covers the field is always taken off its
  bottom, so the field ends at the top of the keyboard and keeps the caret above it; the navigation bar and the room
  after the last line are only added once scrolled to the end."
- `documentation/testing/01-android.md` (AND-054) and `documentation/testing/02-ios.md` (IOS-041): the step above.

## Files touched

- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt`
- `presentation/CLAUDE.md`
- `documentation/testing/01-android.md`
- `documentation/testing/02-ios.md`

## Depends on

Plan 35 (`WindowInsets.contentEdges`). `SongEditorScreen.kt` order in lane D: **36**, 42 (same modifier chain — 42
removes the `onPreviewKeyEvent` just above this padding), 44. Plan 35 also adds a step to
`documentation/testing/01-android.md`; different entries.
