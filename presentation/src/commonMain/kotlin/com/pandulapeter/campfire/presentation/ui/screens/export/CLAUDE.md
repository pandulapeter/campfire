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

The export screen. Formats, options, and the PDF pipeline are in `ui/print/CLAUDE.md`.

## Entry and sources

- Opened as `DialogType.Export` from a song's "Export song" (with the setlist it was reached through, whose key it then
  prints in), a setlist's "Export setlist", or the selection mode's "Export songs" (`DialogType.Export.songs`, in the
  songs screen's sort order; one ticked song is that row's own Export).
- The screen asks the source two things: `hasRunningOrder` (setlist: content choice, overview, zip manifest) and
  `hasSeveralSongs` (song ticks, Select all, new page per song, Zip). Several songs are unnumbered, in library keys,
  zipped by `ExportSongsUseCase` as `campfire_songs.zip` (even if one is left) and saved as `campfire_songs.pdf`.

## A dialog to the view model, a screen to the user

- `ExportHost`, composed by `CampfireScreens` after the `NavDisplay` and before dialogs and snackbars, slides it in and
  out exactly like a pushed destination: same spring (`slideFractionSpec`), the `NavDisplay` giving way 12% through the
  shared `ExportTransition` progress, following predictive back.
- An opaque `Surface` under the host's `NavigationBackHandler`, so Escape, browser Back, and the gesture close it; a
  failed export's snackbar still shows over it.
- A focus group that cancels every exit while it is the dialog shown, so Tab never reaches the screen beneath
  (`clearAndSetSemantics` only hides that from screen readers).

## Format and files preview

- **The format is the first option**, with a line saying what each is for (the names alone don't tell anybody).
  `Format.FILES` are written by `CampfireViewModel.exportFiles`; a saved one closes the screen like a PDF.
- All PDF options fold away for files except a setlist's song ticks, one selection for both formats (switching doesn't
  restore unticked songs). `chosenSongFileNames` is null when all are ticked, so the manifest goes out as stored. A zip
  of none isn't offered unless the setlist has no songs.
- Files skip the layout; `FilesPreview` shows the song's text (`PrintSong.text`, monospace, unwrapped) or `ZipContents`
  (manifest, then each `.cho`, missing ones dimmed).
- Option and zip rows use `listItemAnimation`, keyed by their place in the source (a setlist may name a song twice).

## State

- `rememberExportState(dialog)`: selection, preview page, and zoom are `rememberSaveable` (survive rotation); options,
  source, and Retry count start over.
- The previous document stays up while the next is laid out (debounced). **Save only takes a document laid out from what
  is on screen**: an earlier tap (a FAB has no disabled look) is kept and carried out once it is (`ExportRequest`).
- Options go to `pendingPrintSettings` as they change, saved after settling, and whatever is pending is saved however
  the screen closes (`setVisibleDialog`).
- `CampfireViewModel.exportPdf` draws off the main thread into `pdfExportProgress` (a ring on the save button, which is
  Cancel, `cancelPdfExport`, until the picker is up). The button leaves while there are no pages. Failures, including
  `OutOfMemoryError` (not catchable on the web), report a failed export.
- A saved file closes the screen from the view model, even if not composed (an Activity recreated under the picker); a
  share leaves it open. Share is an app bar action where `FilePicker.canShare` (Android, iOS).
- `pdfFileName` names it by `LibraryFiles.songBaseName`, like the library; a running order gets `-running_order`, so
  the two exports of one setlist don't collide.

## Layout

- Phone: the preview is the options list's first item (`STACKED_PREVIEW_HEIGHT`) and scrolls away; the list ends above
  the save button (`FLOATING_CONTROLS_CLEARANCE`). From 520dp wide, options sit beside the preview and the button
  floats over the preview.
- **The preview is its whole pane, and no floating control ever rests on the page at zoom 1**: the page fits the pane
  less one `FLOATING_CONTROLS_CLEARANCE` band at each end a control floats over (`PageFit`, `fitArea`). In a short window
  (`SHORT_WINDOW_HEIGHT`) the controls stack at the pane's end and the page sits beside them; the page-count pill is
  sized for its widest possible text so the page doesn't shift as pages turn.
- The options are composed once in any arrangement (`PrintPanes`); the preview keeps page and zoom in saved state across
  arrangements, and the options move on the spatial spring (`animateBounds`).
- The pager fades under the app bar and toward the options only as far as a zoomed, panned, or turning page has moved,
  so a page at rest is drawn whole.

## Zoom and paging

- **Zoom grows the whole sheet**, edges included, past the pane (content growing in a fixed page read as larger text in
  the file). The zoom is stored as the page point at the pane's center (`PageView`), not a pixel pan, so resizing keeps
  the view; turning the page opens the next one whole.
- Turning: swipe, buttons, arrows, Page Up / Down, Home, and End. Zoom: pinch, double tap, double-tap-and-drag
  (`doubleTapZoom`, second press consumed so pager and pan don't move), touchpad pinch (`magnifyByTouchpad`). Ctrl / Cmd
  plus wheel zooms only in the desktop app (`isCtrlWheelZoomOwnedByApp`), since in a browser that is the page's zoom.
- A page is described to screen readers by its number.
