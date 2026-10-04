# Fade the welcome sheet's body at its top, and the welcome dialog's body at both edges, as they scroll

**Challenged:** sound

**Kind:** ux  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt`

## Problem

Root `CLAUDE.md`: "Content scrolled under a bar fades out into it rather than the bar lifting … A new scrolling
container gets the same treatment". Every sheet body in `Dialogs.kt` does this
(`Modifier.fadingTopEdge(scrollState).bounceVerticalScroll(scrollState).padding(contentPadding)`), and the What's new
`AlertDialog`'s scrolling text uses `.fadingVerticalEdges(scrollState)`. The welcome screen's two scrolling bodies are
the only two `bounceVerticalScroll(rememberScrollState())` in the file, and neither fades:

- Small window (`isSmallScreen`, under 500dp either way) — the `CampfireBottomSheet` body, in `WelcomeContent`:

  ```kotlin
  ) = Column(
      modifier = Modifier.weight(1f, fill = false).bounceVerticalScroll(rememberScrollState()).padding(contentPadding),
  ) {
  ```

- Larger window — the `AlertDialog`'s `text`:

  ```kotlin
  text = {
      Column(
          modifier = Modifier.bounceVerticalScroll(rememberScrollState()),
      ) {
  ```

On a phone on its side (the welcome sheet; message, theme row, the color row, the hint and the two buttons are taller
than what is left under the header) the rows are cut off hard at the sheet's header as they scroll. On a short desktop
or web window the dialog's text is cut off hard both under its title and above its buttons.

## Fix

1. `WelcomeContent`: hoist the scroll state and fade the top only, the way the other sheets do (its bottom is the
   window's bottom — see the "no bottom fade at the window's bottom" rule; `fadingVerticalEdges` is only for a list
   in the middle of something):

   ```kotlin
   ) {
       val scrollState = rememberScrollState()
       Column(
           modifier = Modifier.weight(1f, fill = false).fadingTopEdge(scrollState).bounceVerticalScroll(scrollState).padding(contentPadding),
       ) { … }
   }
   ```

   (`WelcomeContent` is currently an expression-bodied `= Column(…)`; turn it into a block body so the state can be
   remembered before the `Column`.) Keep the modifier order `weight → fadingTopEdge → bounceVerticalScroll → padding`,
   matching `Dialogs.kt`'s other sheet bodies.

2. The `AlertDialog` branch: the text sits between the title and the buttons, so fade both edges, as the What's new
   dialog does:

   ```kotlin
   text = {
       val scrollState = rememberScrollState()
       Column(
           modifier = Modifier.fadingVerticalEdges(scrollState).bounceVerticalScroll(scrollState),
       ) {
   ```

Both modifiers are already imported in `Dialogs.kt`. No string or CLAUDE.md change.

## Tests

None: modifier wiring in a composable, nothing pure to test.

## Manual check

On an Android phone (or the 360×640dp AVD) turned sideways, start a fresh install (clear app data) so the welcome sheet
opens; scroll its body: the rows fade into nothing under the header, and nothing fades at the bottom. On the desktop,
clear the data directory, open the app in a window just over 500dp tall (the dialog replaces the sheet from 500dp on both sides; raise the text
size if the text still fits) so the welcome dialog's text scrolls: it fades under
the title and above the buttons only while there is more to scroll that way.
