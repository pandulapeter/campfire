<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Tooltips and keyboard shortcuts

Every action drawn as an icon alone gets a tooltip naming it, followed by its keyboard shortcut if it has one. Text
buttons and menu items show their shortcuts too, a few new shortcuts are added, and one sheet lists every shortcut,
including those that belong to no button at all (arrows, Space, M, and Esc).

**Status (November 2026): the tooltips are in** (step 2 below and the tooltip half of step 6), each naming its action
only. Nothing about shortcuts is done yet: no `KeyShortcut` table, no hints, no new shortcuts, no sheet. The rest of
this plan builds on the tooltips as they landed, described under *Tooltips*.

## Decided

### One table of shortcuts

- **Every shortcut is declared once**, in a `KeyShortcut` table next to `AppShortcuts.kt` that holds the keys, the
  platforms where it works, and the action it presses. The handlers (`AppShortcuts`, `SongKeyboardShortcuts`,
  `SaveShortcut`, the editor's shortcuts, and `PrintPages`) match against the table, and tooltips, menu items, and the
  shortcuts sheet read their hints from it. A hint can't name keys that don't work, and a shortcut can't go unlisted.
- **Every shortcut works on every platform with a keyboard.** The app-wide ones (Ctrl+F on lists, zoom, Space and M,
  Ctrl+V, and the new ones), which are wired only on desktop and web today, move to one key handler at the root of the
  common UI, so a hardware keyboard on Android or iOS gets them too. Desktop's window handler and the web's effects
  keep only what is theirs (blocking the browser's defaults, Esc forwarding, and the paste event), and nothing is
  handled twice. On iOS, the shortcuts are also listed for the system's Command-hold overlay (`UIKeyCommand`).
- **A hint appears only where its shortcut works**: a key that the platform keeps for itself (see the web's below) is
  left out there.
- **Keys are written the platform's way**: `⌘S` and `⇧⌘Z` on macOS, iOS, and a web build running on an Apple device;
  `Ctrl+S` and `Ctrl+Shift+Z` elsewhere. Modifier and key names are translated strings (German `Strg`, French `Maj`).
  The formatter is pure and has unit tests. Which way is `multi-caret-editor.md`'s `isMacKeymap` (iOS aside); whichever
  plan lands first adds it.

### Tooltips (done)

- **`CampfireTooltip`** (`components/`) wraps Material 3's `TooltipBox` with a `PlainTooltip` showing the action's
  label, the same string the control gives screen readers. Step 1 adds the shortcut after it in a quieter color (a
  `shortcut` parameter read from the table), so no call site changes for hints.
- **`CampfireIconButton`** (`components/`) is Material's `IconButton` with a tooltip: an `icon` and `label` overload for
  the plain case, and a `content` one for a crossfading or custom icon. Every raw `IconButton` now goes through it.
- **Central wrappers**: `PillIconButton` takes a required `tooltip` (so the `ActionsMenu` buttons, its overflow button,
  sort, search, and filters have one), `SongToolButton` a required `label` (Metronome and Tuner), and
  `EditorToolbarButton` shows its label as a tooltip where it draws an icon (B, I, and U).
- **Controls that are not plain icon buttons are wrapped in `CampfireTooltip`** rather than turned into one: the filled
  buttons (New, the metronome's play, the reference tone and scale play toggles), the step and auto-scroll FABs, the
  stepper's reset mark in a typed stepper, the unselected Tools buttons, and the export button while it shrinks to its
  icon. The last two use `isEnabled`, which keeps the anchor in place, so the control is not composed afresh as its own
  text comes and goes.
- **Material's own tooltip input is off**: `CampfireTooltip` handles hover and long press itself, on a box of its own
  that takes its `modifier`, since `TooltipBox` keeps a parent's `weight` from reaching it and shows a tooltip for good
  whenever the control takes the keyboard focus, eating Escape. A parent's modifier goes on the `CampfireTooltip`, and
  the control fills it (the Tools group); `CampfireIconButton`'s own `modifier` is the button's. A tooltip on keyboard
  focus, if wanted with the shortcut hints, is added there deliberately.
- **Look and timing**: a tonal pill (`surfaceContainerHighest`, the steppers' and app bar pill's tone) with the
  section headers' 2dp shadow, not Material's dark slab. Hover waits 500 ms; right after another tooltip went (600 ms)
  the next shows at once, as along a toolbar; a press hides it.
- **Optional**: Settings → General → Appearance has a Tooltips switch (`UserPreferences.areTooltipsEnabled`, on by
  default, device-local), read through `LocalAreTooltipsEnabled`. Off, no tooltip shows and a long press presses the
  control. The shortcut hints live in the tooltips, so they go with it; the shortcuts sheet and the menus' hints stay.
- **Navigation items get tooltips with the shortcuts** (step 4): an item that shows its label gets one naming only its
  `Ctrl+1` … `Ctrl+6` key (on desktop and web, where those work), the label being on screen already; until then they
  have none.
- **Not covered**: the decorative copies in the pushed section headers, the fast scroller (its bubble already names
  the section), drag handles, and the chevron-only fold pills of a song's
  sections, whose press folds what is under them.
- **Touch**: a long press shows the tooltip, as Material 3 does, and swallows the click. The tempo and reference pitch
  steppers, which repeat while held, show tooltips on hover only (`isTooltipShownOnLongPress`).
- **Toggle labels name the action**, not the current state. `LabelSortingToggle`'s tooltip reads "Sort alphabetically"
  while it is sorted by usage; its content description keeps saying the order the list is in, as its KDoc says.

### Shortcuts on controls that have text

- **Menu items** show their shortcut at the trailing end of the row (`DropdownMenuItem`'s trailing slot): Edit song
  `Ctrl+E`, Find `Ctrl+F`, Find and replace `Ctrl+R`, Paste from clipboard `Ctrl+V`, plus the new ones below.
- **Text buttons with a shortcut** (the sheet header's Save, Create, and Done, the Export button, and the unsaved
  changes dialog's Save) get a tooltip that shows only the keys.

### A sheet that lists every shortcut

- **Keyboard shortcuts sheet**: opened with `Ctrl+/` (and `?` outside text fields), and from Settings → About when the
  device has a hardware keyboard or is a desktop or web build. It lists the shortcuts that work on the current
  platform, grouped by screen, and covers what no button shows: arrows and Page Up / Page Down, Space and M, Esc,
  Home / End in the print preview, and Ctrl + scroll.
- **Android's system list (Meta+/)** is filled from the same table (`onProvideKeyboardShortcuts`).

### New shortcuts

| Keys                       | Where                         | Does                                      |
|----------------------------|-------------------------------|-------------------------------------------|
| Ctrl+N                     | Songs, Setlists               | New song / New setlist                    |
| Ctrl+Z, Ctrl+Shift+Z / Y   | Song editor                   | The editor's own Undo / Redo history      |
| Enter / Shift+Enter, F3 / Shift+F3 | Editor search         | Find next / previous                      |
| Ctrl+A                     | Songs, selection mode         | Select all                                |
| Ctrl+P                     | Song details, setlist         | Print and export                          |
| Ctrl+Shift+↑ / ↓           | Song details                  | Transpose up / down                       |
| T                          | Metronome tab, song details   | Tap tempo (same rules as Space and M)     |
| Ctrl+1 … Ctrl+6            | Main screens (desktop, web)   | Switch to the n-th visible main screen    |
| Ctrl+,                     | Anywhere (desktop, web)       | Settings                                  |
| Ctrl+/                     | Anywhere                      | The keyboard shortcuts sheet              |

Nothing that deletes gets a shortcut. On the web, each new shortcut that the browser also uses (Ctrl+P, Ctrl+1 … 6,
Ctrl+,, Ctrl+A, Ctrl+Y, and F3) has its default blocked only while the app handles that key, the way
`EditShortcutEffect` does today. Chrome lets a page block all of these, but never Ctrl+N, which always opens a new
window before the page sees it (tested in Chrome 157 on macOS with real key presses, November 2026). So the web build
leaves out Ctrl+N and hides its hint; New stays one click away in the list's header.

## Order of work

1. The `KeyShortcut` table, the formatter and its tests, and the key name strings in every language. Move today's
   handlers onto the table, and the app-wide ones to the common root handler, so they work on Android and iOS.
2. ~~`CampfireTooltip`, `CampfireIconButton`, and the central wrappers; then the raw call sites, screen by screen.~~
   Done; step 1 then gives `CampfireTooltip` its hint.
3. Menu item hints and tooltips on text buttons that have a shortcut.
4. The new shortcuts, and the navigation items' tooltips with their `Ctrl+1` … `Ctrl+6` hints.
5. The shortcuts sheet and Android's system list.
6. One UI test (`tools/screenshots`): hovering an icon button shows its label and shortcut. `TooltipTest` already checks
   the label on hover and on a long press; extend it with the shortcut. Update the CLAUDE.md files of the packages
   touched.
