<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/screens/export

The export screen, called **Share** in the UI: one song, a setlist, or a selection is shared, while the whole library is
exported (Settings' Export library as zip). The code keeps the export names. The PDF pipeline is in
`ui/print/CLAUDE.md`.

## Sources and formats

- A song reached through a setlist is shared in that entry's key. A selection keeps the songs screen's sort order; one
  ticked song is that row's own Export. Several songs are unnumbered and saved as `campfire_songs.zip` / `.pdf`.
- Settings' Show QR code and a consent answered from Link open the screen on Link for that opening only, the stored
  format untouched; a format picked on the screen is stored, and survives rotation over the opening's own.
- Formats, in the order offered and recommended: **Link** (for Campfire users, one song or setlist, where the build has
  sync), the library's own files (for any ChordPro app: **ChordPro** for a song, **Zip** for a setlist with its manifest
  or for a selection without one), and **PDF** (for printing). Link is the default; a stored format an export does not
  offer opens it on PDF, the preference kept. One Share entry per item; no separate file export entry.
- **The library's own files go out as played** (on by default, remembered): a `.cho` and a selection's zip are rewritten
  into valid ChordPro of what the reader plays (chords, `{key}`, `{define}`s, and tabs as the shapes above the capo
  shown; `{capo}` that capo, else the file's; `{tempo}` the one played, later changes scaled), so any app sounds as
  Campfire does under either capo reading, leaving out what a switch hides. Offered only where a file would change,
  never for a setlist, whose manifest already carries how each song is played (both would apply twice on a reimport).
  A link shares the file as stored. Chosen with As written / As played here segments, never a switch, whose off side
  named no file; the line under them describes the chosen one.
- **Every song goes out**: no song ticks or choice of a setlist's content, which only cluttered the screen. A setlist's
  PDF is its sheets after its running order, or either alone (never both off: each box is disabled while the other is
  unticked); its zip holds the stored manifest unchanged. Original slot numbers and each entry's transposition are kept;
  missing or unreadable songs keep a visibly marked slot.
- `PrintSettings` (format included) are local preferences, independent of the viewer's text size and folded sections.
- **The Features switches and song preferences reach the export**: an option whose switch is off is neither offered nor
  printed (`PrintSettings.withinFeatures`), but the choice is kept for when the switch comes back.
- The PDF's options are **sections of the document, one heading each** (Page, Running order, Song sheets), a sheet's
  items in the order they print. Columns are a stepper inside the section they shape, never between sections; a new
  option goes into the section, and at the place, where it prints.
- **Only what is printed goes into a PDF**: no original ChordPro or excluded metadata. No platform print service: the
  file is saved or shared.

## Link

- States (`linkExportStateOf`, tested): the connection first (Connect Dropbox saying the whole library syncs; Cancel
  while connecting), then a share the account lists (`ui/share/`; one whose copy is gone says so and offers Create link,
  which replaces it), then how the last Create link went. A failure is shown by what can be done: Connect again (missing
  scope, refused authorization), Try again (network, anything unforeseen), or nothing. Create link is not held for a
  sync run.
- A creation runs in the view model's scope, one per item (another item's does not hold this one), forgotten when the
  screen goes; a share just made shows before the list catches up and is forgotten once the list names it, so a stop on
  another device clears an open screen. Offered in read only mode.
- The QR code is a white card with black modules whatever the theme. Copy link is the FAB (a snackbar where the
  platform says nothing); Save QR code keeps the screen open (also Ctrl / Cmd + S). Stop sharing's confirmation is
  composed by the screen, not the dialog stack, under which the export would slide away.
- Connect from here keeps `SyncConsentOrigin.Export` (`ui/sync/CLAUDE.md`), so a start up answering the consent page
  reopens the screen on Link.

## A dialog to the view model, a screen to the user

- `ExportHost` (after the `NavDisplay`, before dialogs and snackbars) slides it exactly like a pushed destination,
  following predictive back.
- An opaque `Surface` under the host's `NavigationBackHandler` (Escape, browser Back, the gesture); a failed export's
  snackbar still shows over it. The handler registers as the screen opens: the dispatcher asks the newest first, and a
  screen's own (a settings tab's, an open search's) would take the back.
- A focus group cancels every exit while it is shown, so Tab never reaches the screen beneath (`clearAndSetSemantics`
  only hides that from screen readers).

## Format and files preview

- **The format is the first option**, a card per format each saying whom it suits (the names alone don't tell anybody),
  all at once so nobody has to try each. All PDF options fold away for files; a saved file closes the screen like a PDF.
- The files' preview is a summary that never scrolls (the file name, what it holds and what reads it, missing songs):
  the text or a file list was a second scrolling area beside the options.
- Stacked on a phone, only the PDF's pages take a fixed height; the files and the link are list rows as tall as what
  they say (start-aligned, no centring), and the pane's crossfade animates the height between formats.

## State

- Preview page and zoom survive rotation; options, source, and Retry count start over.
- The previous document stays up while the next is laid out (debounced). **Save only takes a document laid out from
  what is on screen**: an earlier tap (a FAB has no disabled look) is kept and carried out once it is.
- Options are saved after settling, and whatever is pending is saved however the screen closes.
- The PDF is drawn off the main thread; the save button is Cancel, with a progress ring, until the picker is up, and
  leaves while there are no pages. Failures, `OutOfMemoryError` included (not catchable on the web), report a failed
  export.
- A saved file closes the screen from the view model, even if not composed (an Activity recreated under the picker); a
  share leaves it open. Share is an app bar action only where the platform shares what the format makes (browsers with
  Web Share share a PDF but no zip or `.cho`).

## Layout

- Phone: the preview is an item of the options list, under the format choice, and scrolls away; from 520dp wide,
  options sit beside it. The pages take keyboard focus only beside the options: in the list a focused item is scrolled
  into view, which would carry the formats away as PDF is chosen.
- **No floating control ever rests on the page at zoom 1**: the page fits the pane less a clearance band at each end a
  control floats over. In a short window the controls stack at the pane's end beside the page; the page-count pill is
  sized for its widest text so the page doesn't shift as pages turn.
- The Link preview follows the same rule, its QR code sized to its pane (at most 240dp, scrolling only below 120dp):
  in a short window beside the options the shared link takes the pane less an end column, Copy link collapsing to its
  icon; every other state keeps the bottom band, its label saying what a bare icon would not. Each state keeps its own
  padding while it fades, and a resize animates it.
- **One FAB for every format**: a change of format or of the link's next step crossfades its icon and label inside it,
  never a button leaving for another. `FloatingControlVisibility` scales the floating controls, draws the shadow itself
  fading its colors (a shrinking elevation stays dark on Skia), and fades only the control inside, so no buffer cuts the
  shadow off (never `ModulateAlpha`, which on Skia skips the layers inside). The opening format's controls appear
  without animating: their pages arriving is data, not something the user did.
- **Beside the options in a window that is not short, the FAB is filled with `primary`**: in the far corner of a large
  window the tonal one went unnoticed. A resize across the line animates the color; it sits in the body, which knows
  the arrangement from its first frame.
- The options are composed once in any arrangement and move on the spatial spring (`animateBounds`); page and zoom
  survive arrangements.
- The pager fades under the app bar and toward the options only as far as a zoomed, panned, or turning page has moved,
  so a page at rest is drawn whole.

## Zoom and paging

- **Zoom grows the whole sheet**, edges included, past the pane (content growing in a fixed page read as larger text in
  the file). It is stored as the page point at the pane's center, not a pixel pan, so resizing keeps the view; turning
  the page opens the next one whole.
- Stacked on a phone, a one-finger drag of a zoomed page that its top or bottom edge stops goes on scrolling the options
  list, and its release flings it; a pinch never scrolls the list.
- Ctrl / Cmd plus wheel zooms only in the desktop app, since in a browser that is the page's zoom.
