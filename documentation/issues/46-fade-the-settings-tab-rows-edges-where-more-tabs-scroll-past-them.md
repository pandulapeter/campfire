# Fade the settings tab row's edges where more tabs scroll past them, so About is seen to be there on a phone

**Kind:** bug (UX)  ·  **Severity:** low  ·  **Platforms:** all (phones, and any window under about 420dp wide)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/settings/SettingsTabRow.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/EdgeFade.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/settings/CLAUDE.md`

## Problem

The settings screen's five tabs are each as wide as their name and the row scrolls where they do not fit
(`SettingsTabRow.kt`, at 72d48deb7):

```kotlin
PrimaryScrollableTabRow(
    modifier = Modifier
        .padding(start = startPadding, end = endPadding)
        .widthIn(max = SETTINGS_TAB_ROW_MAX_WIDTH),
    selectedTabIndex = selectedTab.ordinal,
    containerColor = Color.Transparent,
    edgePadding = 0.dp,
    …
)
```

On a 360dp phone in English the first four tabs, General, Features, Songs, and Library, end almost exactly at the right
edge of the screen, and About starts past it (live run: `92-en.png`, `60-settings.png`, `62-tabs.png`). Nothing says there
is a fifth tab: no part of it shows, and nothing fades. About holds Help, the version, Rate Campfire, the licences, and "Set
up Campfire again", and is reachable only by a swipe of the pages or a sideways drag of a row that does not look like it
moves. The rest of the app marks content that continues past an edge with a fade (root `CLAUDE.md`: "Content scrolled under
a bar fades out into it"; `EdgeFade.kt`), and this row has none.

## Fix

1. `EdgeFade.kt`: add a masked horizontal counterpart of `fadingVerticalEdges(scrolledFromTop, scrolledFromBottom)`:

   ```kotlin
   /**
    * [fadingVerticalEdges] sideways, for a row that scrolls: each end fades as far as there is content left past it, so a
    * row that fits, or rests at an end, is drawn whole there. The start is the right edge in a right to left layout.
    */
   internal fun Modifier.fadingHorizontalEdges(scrollState: ScrollState): Modifier
   ```

   drawn the way `fadingVerticalEdges` is (an offscreen `graphicsLayer` only while either end has content past it, a
   `BlendMode.DstIn` gradient of `EDGE_FADE_SIZE` at each end, its strength `scrolled / EDGE_FADE_SIZE` clamped to 1), with
   `scrollState.value` as the distance from the start and `scrollState.maxValue - scrollState.value` as the distance to
   the end, and the two ends swapped under `LayoutDirection.Rtl` as `fadingUnderStartOverlay` does. Masked rather than
   painted, since the row is transparent over whatever the settings screen is drawn on.
2. `SettingsTabRow`: hold `val scrollState = rememberScrollState()`, pass it to `PrimaryScrollableTabRow(scrollState = scrollState, …)`
   (the parameter Material 3 1.12 has), and add `.fadingHorizontalEdges(scrollState)` to the row's modifier after
   `widthIn`. The row's own scroll-into-view of the selected tab keeps working, and the fade follows it.
3. Settings `CLAUDE.md`: where it describes the tabs (the row that "scrolls where they add up to more than a phone's width"),
   add "and fades at an end past which there are more tabs, so that the last one is seen to be there".

Considered: narrowing the tabs' padding until five fit. In English at 360dp they need about 410dp at Material's 16dp side
padding, more in Hungarian ("Általános Funkciók Dalok Könyvtár Névjegy") and at larger font sizes, so it cannot be relied
on, and the fade is needed wherever it fails anyway.

## Tests

None: a draw modifier reading a scroll state.

## Manual check

On the 360 × 640dp emulator, open Settings: the right end of the tab row fades, with Library dimmed into it and About's
start showing faintly. Drag the row (or swipe to About): the left end fades and the right end is drawn whole once About is
fully in view. In Hungarian the same. On a tablet or the desktop, where all five fit, nothing fades. Switch the app to a
right-to-left system language if one is available (or check the code path), and the fades swap ends.
