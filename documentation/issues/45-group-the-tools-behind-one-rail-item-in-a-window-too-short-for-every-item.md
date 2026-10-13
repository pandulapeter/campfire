# Group the tools behind one Tools item in a rail too, wherever the window is too short to hold every item

**Kind:** bug  ·  **Severity:** high  ·  **Platforms:** all (phones on their side above all; any window under about 430dp tall that is at least 600dp wide)
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/NavigationBarItems.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/NavigationChrome.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireContent.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireScreens.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/ToolsTabRow.kt` (KDoc),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/ToolScreenFrame.kt` (KDoc, if it says "bar"),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/NavigationBarItemsTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CLAUDE.md` ("The Tools item"),
`CLAUDE.md` (root; the "Six top level screens" product rule only)
**Challenged:** amended — the chrome is handed `isShortWindow` and still works the grouping out for its own kind, rather than being handed one `areToolsGrouped` for the window's kind: during a handover the scaffold composes the outgoing chrome too (`NavigationChromeScaffold`'s `OUTGOING_CHROME`), and a single value would ungroup a leaving bar, or group a leaving tall rail, while it fades. The flag is a derived state, so a resize recomposes only as it crosses 480dp. Tests and the manual check follow: rotating a phone keeps the tools grouped both ways (bar upright, short rail on its side), so nothing shrinks or grows there.

## Problem

A phone on its side (640 × 360dp) is wider than 600dp, so it gets the navigation rail, and the rail lists every top level
screen; the Tools grouping is the bar's alone (`NavigationBarItems.kt`, at 72d48deb7):

```kotlin
internal fun areToolsGrouped(kind: NavigationChromeKind, destinations: List<CampfireDestination.TopLevel>) =
    kind == NavigationChromeKind.BAR &&
        destinations.size > MAX_NAVIGATION_BAR_ITEMS &&
        destinations.count { it in CampfireDestination.TopLevel.tools } > 1
```

```kotlin
} else if (kind == NavigationChromeKind.RAIL) {
    NavigationRail(windowInsets = NavigationRailDefaults.windowInsets.withMinTopEdge) {
        CampfireDestination.TopLevel.entries.forEach { destination -> … NavigationRailItem(…) }
    }
}
```

With every feature on there are six items of about 60dp each under the status bar. In 360dp the rail shows Songs,
Setlists, Metronome, Tuner, and half of Chords; **Settings is not on screen at all**, not even while it is the screen
shown, and the rail does not scroll (live run: `67-settings-land.png`, `68-rail-scrolled.png`, `78-chords-land.png`).
On a phone on its side the only way into Settings is to turn the phone upright. The same happens in any window at least
600dp wide and under roughly 430dp tall (a short desktop or browser window), and on the expanded rail too.

When the tools are grouped: only on the bar (under 600dp wide), with more than five destinations switched on and at least
two tools. When not: every rail, whatever its height.

History: the rail held four items until the tuner (b5c8ed3b5, 2026-10-09) made it five, which still fit a 360dp-tall rail;
the sixth item and the Tools grouping arrived together with the Chords tab in 9b21b09fe (2026-10-13, after 25e6152ea).
So this does not predate 25e6152ea: before 9b21b09fe, Settings was the fifth item and on screen.

## Fix

Options:

- **(a) Recommended: group in a short rail too.** Give `areToolsGrouped` the window height and let a rail group the tools
  where the window is short (`SHORT_WINDOW_HEIGHT`, 480dp, the threshold every other "phone on its side" rule uses):

  ```kotlin
  internal fun areToolsGrouped(kind: NavigationChromeKind, destinations: List<CampfireDestination.TopLevel>, isShortWindow: Boolean) =
      (kind == NavigationChromeKind.BAR || isShortWindow) &&
          destinations.size > MAX_NAVIGATION_BAR_ITEMS &&
          destinations.count { it in CampfireDestination.TopLevel.tools } > 1
  ```

  Six items need about 6 × 60dp plus the top inset, about 400dp; 480dp leaves room for the system's larger font sizes,
  and grouping leaves four items (Songs, Setlists, Tools, Settings), which fit a 360dp rail at any font size.
  - Read the height once in `CampfireContent`, from the window (`LocalWindowInfo.current.containerDpSize.height`, which
    the keyboard does not change, so the grouping never flips while typing), as a derived state so that a window being
    resized recomposes only in the frame it crosses the line:
    `val windowInfo = LocalWindowInfo.current; val isShortWindow by remember(windowInfo) { derivedStateOf { windowInfo.containerDpSize.height < SHORT_WINDOW_HEIGHT } }`.
    Pass `isShortWindow` to `NavigationChrome` (both call sites: `CampfireContent`'s `chrome` lambda and the copy
    `CampfireScreens` draws under a card) and to `CampfireScreens`, and let each work the grouping out for its own kind,
    `areToolsGrouped(kind, destinations, isShortWindow)`, as today. Not one precomputed `areToolsGrouped: Boolean`: the
    scaffold composes the chrome it is leaving for the length of a handover (`chrome(outgoingKind)`), and that chrome
    must keep its own grouping while it fades, or a bar leaving for a tall rail would grow its tools back out on the way.
    `CampfireScreens` computes `groupedTools` from `areToolsGrouped(chromeKind, topLevelDestinations, isShortWindow)`.
  - `NavigationChrome`: draw both rails from `navigationBarItems(destinations, areToolsGrouped)` as the bar is drawn —
    the Tools item with `ic_tools` and `Res.string.tools`, selected while a tool is on top, its click
    `toolsItemDestination(...)?.let(onToolsItemSelected)`, and the tools' own items shrinking into it through
    `NavigationItemPresence` / `collapsingNavigationItem(presence, isHorizontal = false)` as on the bar. Factor the
    `selected` / `onClick` / icon / label `when (item)` blocks into small private helpers so the three chrome kinds share
    them rather than repeating them.
  - Rename nothing else: `navigationBarItems` and `NavigationBarItem` now serve the rails too; update their KDocs
    ("One item of the navigation bar" → "of the navigation bar or rail").
  - The tool screens then carry `ToolsTabRow` (72dp) in the short rail window as they do on the bar. That is a sixth of a
    360dp window; it is what the bar already costs a phone held upright, and the alternative is Settings out of reach.
- **(b) Let the rail scroll** (`Modifier.verticalScroll` on the rail's items). Smallest change, but the sixth item is
  still hidden until somebody thinks to scroll a navigation rail, which nothing suggests; not recommended alone. It is
  worth adding as well as (a) only if a check at the largest font and interface scale shows four items not fitting.
- **(c) Drop the labels in a short rail** (`alwaysShowLabel = false`). Six unlabeled items of about 56dp still do not fit
  360dp under a status bar, and the labels are what tell a wrench from a tuning fork; not recommended.

Docs, in the same change:

- `ui/CLAUDE.md`, "The Tools item": replace "It is only the bar's — a rail lists every screen, so a window that grows past
  the bar hands the tools their own items back — and only while the screens switched on do not fit, so a bar of five or
  fewer never has it." with "It is the bar's, and a rail's in a short window (`SHORT_WINDOW_HEIGHT`: a phone on its side),
  where six items do not fit its height — a taller rail lists every screen, so a window that grows past the bar or the
  short height hands the tools their own items back — and only while the screens switched on do not fit, so five or fewer
  never have it."
- Root `CLAUDE.md`, Product rules: "Six top level screens do not fit a navigation bar, so there the Metronome, the Tuner and
  the Chords are tabs behind one Tools item, which a rail and a bar of five or fewer screens leave out" becomes "Six top
  level screens fit neither a navigation bar nor a rail in a short window, so there the Metronome, the Tuner, and the Chords
  are tabs behind one Tools item, which a taller rail and five or fewer screens leave out".
- The KDocs of `areToolsGrouped`, `NavigationChrome` ("Where the bar has no room…"), `ToolsTabRow` ("behind the navigation
  bar's Tools item"), and the comment over `groupedTools` in `CampfireScreens` ("The tools the bar offers…").

## Tests

`NavigationBarItemsTest`: every existing call, the `shownItems` helper's included, gains `isShortWindow = false`, so they
keep their meaning (`the rails offer every screen` becomes `a tall rail offers every screen`). Add:

- `a rail in a short window groups the tools`: `areToolsGrouped(RAIL, all, isShortWindow = true)` and
  `(EXPANDED_RAIL, all, isShortWindow = true)` are true; `shownItems(all, RAIL, isShortWindow = true)` is Songs, Setlists,
  Tools, Settings.
- `a bar is grouped whatever its height`: `areToolsGrouped(BAR, all, isShortWindow = true)` and `(BAR, all, false)` are true.
- `a short rail with five screens lists them all`: `areToolsGrouped(RAIL, all - Setlists, isShortWindow = true)` is false.

The 480dp line itself is `SHORT_WINDOW_HEIGHT`, read in the composition, and not part of the pure function.

## Manual check

On an Android phone (or the 360 × 640dp emulator), with every feature on, turn the phone on its side: the rail shows Songs,
Setlists, Tools (the wrench), and Settings. Tap Settings: it opens and its item is selected. Tap Tools: the last tool opens
with the Metronome / Tuner / Chords tabs over it. Turn the phone upright and back: the Tools item is there both ways (the
bar upright, the rail on its side), the tool on screen stays selected, and its tabs stay over it. Switch the Tuner off in Settings → Features while on its
side: the rail grows back to five items. On the desktop, make the window 900 × 420dp, then 900 × 600dp: the rail groups,
then ungroups, the tools shrinking into the Tools item and growing back out, and the tool screens' tab row coming and
going with it. Narrow a 900 × 600dp window under 600dp wide: the rail fades out ungrouped while the bar fades in grouped.
