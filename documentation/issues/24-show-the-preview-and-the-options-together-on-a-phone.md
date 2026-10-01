# Show the preview and the options together on a phone

**Kind:** usability  ·  **Severity:** medium  ·  **Platforms:** Android, iOS, narrow web/desktop windows
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/PrintExportSheet.kt`, both `strings.xml` (remove `print_preview`, `print_options` if unused afterwards)
**Challenged:** amended — the height breakpoint must use the sheet's stable height (`uncoveredTopInset`), or the layout flips while the sheet slides in.

**Decision D3** — taken 2026-10-01: the recommended option, as written below.

## Problem

Below 760 dp the sheet shows either the preview or the options (`showOptions` chips over a `Crossfade`). A change
cannot be seen while it is made: move the size, switch, look, switch back. A landscape phone (≈850 × 390 dp) takes the
wide branch, where `heightIn(min = 300.dp)` exceeds the room and the page is ~200 dp tall.

## Fix

Choose by both dimensions:

- width ≥ 760 dp **and** height ≥ 480 dp: side by side as today;
- landscape phone (width ≥ 600 dp, short): side by side with the options at 300 dp and no `heightIn(min)`;
- otherwise: the preview on top at 42 % of the height (page fitted to it, paging by swipe — plan 25), the options
  list scrolling below it, a hairline of space between; no switch.

**Measure the height the way `SongFilters` does**: `maxHeight - uncoveredTopInset()` (the sheet content's
`BottomSheetContentScope.uncoveredTopInset`) minus the action row. The height a sheet's content is offered changes as
the sheet slides up and is dragged (see `CampfireBottomSheet`'s `uncoveredTopInset` KDoc), so a breakpoint read from
the raw `maxHeight` would switch layouts, and with them the preview's size, during the opening animation. The 42 % is
taken of that same stable height.

Hoist all state above the branch so crossing a breakpoint (rotation, window resize) loses nothing: `settings`,
`selected` (plan 26) and the page (plan 25) already live in the sheet; the only state left in a branch is
`showOptions`, which goes away with the switch.

## Tests

None (UI).

## Manual check

Phone portrait and landscape, tablet, a desktop window dragged across the breakpoints: the preview updates in view
while options change; nothing resets when the layout switches, and the layout does not change while the sheet opens.
