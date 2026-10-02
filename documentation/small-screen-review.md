<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Smallest supported screen review

Tested on 2026-10-02 at d023a8817. The device was the Android emulator shrunk to 360 × 640 dp (`wm size 720x1280`,
`wm density 320`), in portrait and in landscape (640 × 360 dp), with the Gboard keyboard. In portrait the keyboard
leaves about 330 dp of the screen above it, and in landscape about 110 dp. The list is ordered by severity.

Not covered: the import screen (it needs a real import), the welcome sheet (it needs a fresh install), and the web and
iOS builds.

## Keyboard handling

1. **Dialogs are panned rather than resized when the keyboard comes up, so their buttons end up under it.**
   - Seen in Edit metadata, Edit tags, Edit languages, New setlist, Edit setlist and Delete library, in portrait.
   - `TextFieldDialog` switches to its full-screen form only when its `BoxWithConstraints.maxHeight` drops under
     320 dp. On Android, a dialog window that is taller than the room above the keyboard is panned by the system
     instead of resized, so `maxHeight` never changes.
   - The result is that Save, Cancel, Create, Done and Delete are hidden for as long as the keyboard is up. Delete
     library and New setlist even push their title under the status bar.
   - The short "New song" dialog does switch to the full-screen form, which is why the problem hides in quick tests.
   - *Fix:* read `WindowInsets.ime` inside the dialog, which needs `DialogProperties(decorFitsSystemWindows = false)`
     so the insets reach Compose. Decide on the full-screen form from `maxHeight − ime bottom`, and pad the full-screen
     form by the IME inset (see 2).

2. **The full-screen dialog form does not make room for the keyboard.**
   - In Edit links (portrait) the keyboard covers the bottom of the dialog, so "Add link" and any lower link rows can't
     be reached while typing.
   - *Fix:* add `imePadding()` to the full-screen form's content (and to its start button row). Together with 1, the
     content then ends above the keyboard and the focused field is brought into view inside it.

3. **In landscape, the editor's field gets no height at all once the keyboard is up.**
   - The status bar inset, the title row and the transposition / pane row take more than the ~110 dp the keyboard
     leaves.
   - The window pans, which pushes the stepper row under the status bar. No line of text is visible, only the caret
     handle floating over an empty area.
   - Portrait is only workable: with the Shortcuts rows open, about two lines of text show above the keyboard
     (five with them folded).
   - *Fix:* while the keyboard is up and the window is short (for example under 480 dp tall), drop the second row (the
     stepper and the Edit / Preview choice) and fold the Shortcuts rows automatically. Alternatively, merge the
     stepper and the pane choice into the title row's overflow on short windows.
   - The rule worth stating: the editor's fixed header has to leave at least ~3 lines of text above the keyboard on
     the smallest screen, in both orientations.

4. **In landscape, every sheet whose field gets focus hides that field under the keyboard.**
   - In Song assignments the search field opens focused and sits under the keyboard. The list can't be seen at all.
   - In the cover art sheet, the Title field being typed into is under the keyboard, because the header and the tabs
     alone take more than the room left.
   - *Fix:* on short windows, make the whole sheet one scrolling column, header included (the sheet already fills the
     screen in that case). Alternatively, give up the sheet's top inset gap and fold the subtitle away.
   - Also: don't auto-focus the song picker's search when the window is shorter than ~480 dp. Bringing the keyboard
     up uninvited there hides the very list the sheet was opened for.

5. **The list screens' search shows almost nothing in landscape.**
   - With the keyboard up, the results area under the search bar is about 25 dp, which is not even one card.
   - Portrait shows about 1.7 song cards, which is acceptable.
   - *Fix options:*
     - Collapse the 48 dp status bar gap (the bar could draw under it).
     - Let Android's full-screen "extract" keyboard UI work in landscape: Compose disables it, but for a search field
       a full-screen editor is arguably better than a 25 dp list.
     - Accept it and hide the keyboard on the first scroll, which it already does.

6. **Edit setlist shows its title selected without focus.**
   - The title text is drawn highlighted while no field has focus and no keyboard is up, because the dialog opens
     unfocused "to be looked over".
   - Selection without a caret reads as a glitch. When the user taps into the description, the title stays
     highlighted next to the focused field.
   - *Fix:* select the title only when the dialog opens focused on it, or select it on the title field's first focus.

## Layout

7. **In landscape, the full-screen dialog form leaves gaps that show the screen behind it.**
   - Seen in Edit setlist.
   - The surface stops short of the status bar, of the left display-cutout band and of the bottom edge. The list
     behind it ("Charlie · Em") shows through underneath, and the date field is cut off.
   - *Fix:* in the full-screen form, use `DialogProperties(decorFitsSystemWindows = false, usePlatformDefaultWidth =
     false)` and pad the content by `safeDrawing` itself, so the surface covers the whole window.

8. **On the export screen, the preview and the options are both cramped, and Save covers the options.**
   - In portrait the page preview is about 90 × 125 dp, which is too small to judge anything.
   - The options pane is about 250 dp tall, and at rest the floating Save button sits over the "Letter" segment and
     later over the Text size and Margins steppers. They are reachable only by scrolling.
   - *Fix options:*
     - On compact heights, let the options scroll *together with* the preview, so the preview scrolls away.
     - Shrink the extended Save button to an icon button while the options are being scrolled.
     - Give the options' scroll content bottom padding equal to the button's height, so nothing rests under it.
   - Tapping the preview to open it full screen would also fix the size problem.

9. **The editor's "Preview" segment is truncated to "Previ…".**
   - In portrait, the inline `SegmentedChoice` next to the transposition stepper doesn't have room for the label.
   - *Fix:* drop the stepper's value text width, or use icons for Edit / Preview on compact widths.

10. **Song cards truncate the artist and the title early.**
    - Examples: "100 Folk Ce…", "4 Non Blon…" and, inside setlists, "1 - Wicked Ga…".
    - The cover (44 dp), the star (or drag handle) and the overflow button (2 × 48 dp with the overlap), the card
      padding and the fast scroller's 24 dp column leave the text about 160 dp.
    - *Fix options:*
      - Let the artist line give the key less room (the "•" separator plus the key).
      - Shrink the trailing actions to 40 dp touch targets on compact widths.
      - Let the fast scroller column overlap the card's end padding.

11. **A setlist's description takes most of the visible list.**
    - Most noticeable in a search result with the keyboard up.
    - In portrait, a four-line description pushes the setlist's first song below the keyboard.
    - *Fix:* clamp the description to two lines with an expand-on-tap on compact heights.

12. **In landscape, the song details screen spends about 110 dp of 360 on the status bar and the app bar.**
    - That leaves about 250 dp for the song.
    - *Fix:* hide the app bar on scroll down (and show it again on scroll up or at the top) on compact heights. This
      matters most for reading in landscape on a music stand.

13. **In landscape, the songs list stays one column at 640 dp, about 1.5 cards tall.**
    - This was noted, not judged: two columns would show four cards. `ListColumns`' threshold could take the rail
      width into account.

## Works well

- **Portrait sheets:** the cover art sheet and Song assignments after today's change, the filter sheet, the setlist
  picker, About the song, the sort menu and the overflow menus.
- **Dialogs and pickers:** New song (full-screen form), the Material date picker (in both calendar and typed modes),
  Delete song and Unsaved changes.
- **Settings:** all tabs.
- **Edit languages:** fine once the keyboard is dismissed by scrolling (the keyboard-on-scroll behavior works
  everywhere it was tried).
