# Group the tools behind the Tools item on any rail too short for every item at the reader's text size

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all with a rail (tablets, desktop, web, phones on their side)
**Files:**
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chrome/NavigationBarItems.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chrome/NavigationChrome.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireContent.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireScreens.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/chrome/NavigationBarItemsTest.kt`,
`tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/chrome/ToolsItemTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/chrome/CLAUDE.md`,
`CLAUDE.md` (the Product rules line on the Tools item)

**Challenged:** amended — checked the rail metrics against `material3` 1.12.0-alpha03 (they hold: item = 40 dp + label line, 4 dp gaps and padding); the insets the plan left as a placeholder are now specified, with how they are read so a resize or rotation is followed; the expanded rail's spacing differs (no gap between items), which the plan now says groups it slightly early rather than claiming the same metrics; both `NavigationChrome` call sites are named.

## Problem

`chrome/NavigationBarItems.kt` groups the tools only on a bar or in a short window:

```kotlin
internal fun areToolsGrouped(kind: NavigationChromeKind, destinations: List<CampfireDestination.TopLevel>, isShortWindow: Boolean) =
    (kind == NavigationChromeKind.BAR || isShortWindow) &&
        destinations.size > MAX_NAVIGATION_BAR_ITEMS &&
        destinations.count { it in CampfireDestination.TopLevel.tools } > 1
```

`isShortWindow` is worked out in `CampfireContent.kt` from the window's height alone:

```kotlin
val isShortWindow by remember(windowInfo) { derivedStateOf { windowInfo.containerDpSize.height < SHORT_WINDOW_HEIGHT } }
```

With every feature on, a rail lists seven items: Songs, Setlists, Metronome, Tuner, Chords, Scales, and Settings.

- `NavigationRail` lays its items in a `Column` with 4 dp between them and 4 dp above and below, inside the system bar insets (`withMinTopEdge`).
- An item is about 56 dp tall at font scale 1, so seven items need 7 × 56 + 6 × 4 + 8 = 424 dp. That fits the 480 dp at which grouping stops.
- The item's label (`labelMedium`, 16 sp line height) grows with the system text size. At font scale 2 an item is about 72 dp, and seven need about 536 dp.

The rail does not scroll. A tablet in landscape, a desktop window, or a web window between 480 and ~540 dp tall, with a large system text size, therefore cuts Settings, and possibly Scales, off the bottom of the rail with no way to reach them. Insets make it worse on a device.

The UI test that pins the rule, `ToolsItemTest`, still lists only three tools:

```kotlin
private fun AppTestScope.tools() = listOf(string(Res.string.metronome), string(Res.string.tuner), string(Res.string.chords))
```

## Fix

Options:

- **Group by computed height (recommended).** Group the tools on a rail whenever its items, at the current text size, are taller than the room the rail has. Keep today's rule (bar or short window) as it is, so nothing that groups today stops grouping.
- Make the rail scroll. Rejected: navigation hidden below a fold is what the Tools item exists to avoid, and the bar already sets the precedent of grouping.
- Group on a rail from seven items on. Rejected: it changes every tall desktop window, which has room for all seven.

Steps:

1. **In `NavigationBarItems.kt`**, add a pure function and its constants:
   ```kotlin
   /**
    * How many items a navigation rail [railHeight] tall holds, its items [itemHeight] tall with Material's gap between
    * them and its padding above and below: what decides, with the reader's text size in [itemHeight], whether the
    * tools must go behind one item on a rail that is not short.
    */
   internal fun railItemCapacity(railHeight: Dp, itemHeight: Dp): Int =
       ((railHeight - RAIL_VERTICAL_PADDING * 2 + RAIL_ITEM_GAP) / (itemHeight + RAIL_ITEM_GAP)).toInt().coerceAtLeast(0)
   ```
   - `RAIL_VERTICAL_PADDING` and `RAIL_ITEM_GAP` are both 4 dp, Material's `NavigationRail` spacing. Checked in
     `material3` 1.12.0-alpha03's `NavigationRail.kt`: the items' `Column` is `padding(vertical = NavigationRailVerticalPadding)`
     with `Arrangement.spacedBy(NavigationRailVerticalPadding)`, both 4 dp, and an item with a label is
     icon 24 + `IndicatorVerticalPaddingWithLabel` 4 + `NavigationRailItemVerticalPadding` 4 + the label + twice a
     content padding of at least 4, which is 56 dp at a 16 dp label: 40 dp plus the label's line.

   Change `areToolsGrouped` so it also groups when the rail cannot hold every destination:
   ```kotlin
   internal fun areToolsGrouped(
       kind: NavigationChromeKind,
       destinations: List<CampfireDestination.TopLevel>,
       isShortWindow: Boolean,
       railCapacity: Int,
   ) = (kind == NavigationChromeKind.BAR || isShortWindow || destinations.size > railCapacity) &&
       destinations.size > MAX_NAVIGATION_BAR_ITEMS &&
       destinations.count { it in CampfireDestination.TopLevel.tools } > 1
   ```
   - Rewrite its KDoc: "…on the bar, on a rail in a short window, and on any rail whose items at the reader's text size would not all fit it ([railItemCapacity])…".
   - The `> MAX_NAVIGATION_BAR_ITEMS` guard stays, so a rail too short even for five items does not group. Those few items are not the tools' problem.
2. **Work out the capacity in `CampfireContent.kt`** next to `isShortWindow`, with the same derived-state care, so a resize recomposes only when the number changes:
   ```kotlin
   val density = LocalDensity.current
   val labelLineHeight = with(density) { MaterialTheme.typography.labelMedium.lineHeight.toDp() }
   // Read through a state: every platform but Android hands out a new, fixed insets instance on every composition (see
   // KeyboardAwarePadding), which a derived state remembered once would never see change.
   val railInsets = rememberUpdatedState(NavigationRailDefaults.windowInsets.withMinTopEdge)
   val railCapacity by remember(windowInfo, density, labelLineHeight) {
       derivedStateOf {
           val insets = railInsets.value
           railItemCapacity(
               railHeight = windowInfo.containerDpSize.height - with(density) { (insets.getTop(density) + insets.getBottom(density)).toDp() },
               itemHeight = RAIL_ITEM_BASE_HEIGHT + labelLineHeight,
           )
       }
   }
   ```
   - The rail's insets are the system bars' (and `MIN_TOP_EDGE`), never the keyboard's, so the grouping cannot flip while
     typing, which is what `isShortWindow` reads the window's height for. The one place the app hides a bar,
     `CompactKeyboardEffect` (the status bar while typing), runs only in a short window, which groups anyway.
   - `RAIL_ITEM_BASE_HEIGHT` is the item's height without its label line: 56 dp − 16 dp = 40 dp at font scale 1 (indicator and paddings). It is an `internal` constant in `NavigationBarItems.kt`.
   - `TextUnit.toDp()` goes through the density's font scale converter, so Android 14's non-linear text scaling is followed.
   - `NavigationItemLabel` auto-sizes a long label down, which can only make an item shorter. The computed height is therefore an upper bound, and the rule groups at worst slightly early.
   - For the expanded rail (`WideNavigationRail`, label beside the icon, `labelLarge`), use the same function with `itemHeight = max(56.dp, labelLarge line height + 16.dp)`.
     Its spacing is not the plain rail's: expanded, it puts no gap between items (`ItemVerticalSpace` is only the
     collapsed rail's) and its top padding is the app's `EXPANDED_NAVIGATION_RAIL_TOP_PADDING`. The 4 dp gaps the
     function assumes over-count six gaps (24 dp), so an expanded rail groups up to that much early. Accepted: at font
     scale 1 seven items need ~424 dp by this count, under the 480 dp below which a rail groups anyway, so no window that
     lists every item today stops listing them.
   - `CampfireContent` does not know the chrome's kind (only `NavigationChromeScaffold`'s lambdas are handed it), and a
     handover composes both kinds at once. So work out both capacities and hand them on together, each chrome taking its
     own kind's: a small `@Immutable` holder in `NavigationBarItems.kt`,
     `internal data class RailCapacity(val rail: Int, val expandedRail: Int) { fun of(kind: NavigationChromeKind) = … }`
     (`Int.MAX_VALUE` for the bar, which ignores it), passed as `railCapacity: RailCapacity` and read as
     `railCapacity.of(kind)` in `NavigationChrome` and `railCapacity.of(chromeKind)` in `CampfireScreens`.
     `areToolsGrouped` itself keeps taking an `Int`, so the unit tests below stand as written.
3. **Thread `railCapacity`** through `NavigationChrome(...)`, where `NavigationChrome.kt` calls `areToolsGrouped(kind, destinations, isShortWindow)`, and through `CampfireScreens(...)`, where `groupedTools` calls `areToolsGrouped(chromeKind, topLevelDestinations, isShortWindow)`.
   - `NavigationChrome` is called twice: from `CampfireContent`'s `chrome` lambda and from `CampfireScreens`'
     `screenChrome` (the copy each screen draws under a card). Both take the new argument.
   - Both must use the same `RailCapacity`, or the tools' tab row and the chrome would disagree.
   - Document the new parameter beside `@param isShortWindow` in `NavigationChrome`'s KDoc.
4. **Docs.**
   - In `chrome/CLAUDE.md`, change "on the bar, and on a rail in a short window (`SHORT_WINDOW_HEIGHT`)" to "on the bar, on a rail in a short window (`SHORT_WINDOW_HEIGHT`), and on any rail too short for every item at the reader's text size (`railItemCapacity`)".
   - In the root `CLAUDE.md` Product rules line, change "…go behind one Tools item on a navigation bar or a short window's rail: `ui/chrome`." to "…on a navigation bar, a short window's rail, or a rail too short for every item: `ui/chrome`."

## Tests

Unit tests in `NavigationBarItemsTest`:

- First, update `shownItems` and every existing `areToolsGrouped` call to pass `railCapacity = Int.MAX_VALUE`, which is today's behaviour.
- `` `a rail too short for every screen groups the tools whatever the window's height` ``: assert `areToolsGrouped(RAIL, all, isShortWindow = false, railCapacity = 6)` and the same for `EXPANDED_RAIL`. Assert `shownItems` gives Songs, Setlists, Tools, and Settings.
- `` `a rail that holds every screen lists them all` ``: assert `areToolsGrouped(RAIL, all, isShortWindow = false, railCapacity = 7)` is false.
- `` `a rail's capacity counts its items and the gaps between them` ``:
  - `railItemCapacity(424.dp, 56.dp) == 7`;
  - `railItemCapacity(423.dp, 56.dp) == 6`;
  - `railItemCapacity(536.dp, 72.dp) == 7`;
  - `railItemCapacity(500.dp, 72.dp) == 6`.

UI tests in `ToolsItemTest`:

- Fix `tools()` to include Scales: `listOf(string(Res.string.metronome), string(Res.string.tuner), string(Res.string.chords), string(Res.string.scales))`. Use the import `com.pandulapeter.campfire.presentation.resources.scales`. The two existing tests then check all four tools.
  - Check first that "Scales" matches only the rail item on the Songs screen these tests stay on, and add `and hasClickAction()` if not.
- New test `` `a large text size groups the tools on a rail too short for them` ``:
  - `runAppTest(window = TestWindow(width = RAIL_WIDTH, height = 500.dp), appearance = TestAppearance(textScale = 2f))`.
  - Assert the Tools item exists, the four tools' items do not, and the Settings item `assertIsDisplayed()`, with its bounds' bottom at or above 500 dp.
  - This fails today: nothing is grouped at 500 dp, and Settings falls below the window.
- The existing `` `a window just tall enough keeps every tool on the rail` `` stays at font scale 1 and must still pass. Seven items need about 424 dp, which leaves 480 dp room. If it fails, the constants in step 1 are wrong, not the test.
