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

The export screen. The PDF pipeline is in `ui/print/CLAUDE.md`.

## Entry and sources

- Opened as `DialogType.Export` from a song's "Export song" (with the setlist it was reached through, whose key it then
  prints in), a setlist's "Export setlist", or the selection mode's "Export songs" (`DialogType.Export.songs`, in the
  songs screen's sort order; one ticked song is that row's own Export).
- The screen asks the source two things: `hasRunningOrder` (setlist: content choice, overview, zip manifest) and
  `hasSeveralSongs` (song ticks, Select all, new page per song, Zip). Several songs are unnumbered, in library keys,
  zipped by `ExportSongsUseCase` as `campfire_songs.zip` (even if one is left) and saved as `campfire_songs.pdf`.

## Formats and options

- One export entry per song and setlist (and selection mode's "Export songs") opens the export screen; no separate
  share or file export entry. Formats: **PDF** (for printing), the library's own files (for Campfire users):
  **ChordPro** for a song (the `.cho` as stored), **Zip** for a setlist (its `*.setlist.json` manifest plus songs) or
  for several selected songs (no manifest), and **Link** (below) for one song or setlist where the build has sync
  (`exportFormatsOf`). A stored format an export does not offer opens it on PDF (`withFormatIn`), the preference kept.
- A setlist's song ticks apply to both formats. A partial zip's manifest names only the ticked songs
  (`ExportSetlistUseCase`'s `songFileNames`, rest of the document kept), so the archive never names a song the user left
  out; a ticked song missing from the library is still named. All ticked → the stored manifest unchanged.
- `PrintSettings` (format included) are local preferences (`PrintSettingsDocument`), independent of the viewer's text
  size and folded sections.
- **The Features switches reach the export**: an option whose feature is off (chords, which take diagrams and key; the
  metronome, which takes tempo) is neither offered nor printed (`PrintSettings.withinFeatures`), but the choice is kept
  for when the switch comes back.
- A setlist export keeps original slot numbers and each entry's transposition; a song reached through a setlist uses that
  entry's key. Missing or unreadable songs keep a visibly marked slot.
- **Only what is printed goes into a PDF**: the printed key, the chosen songs and options; no original ChordPro or
  excluded metadata. No platform print service: the file is saved or shared.

## Link

- States (`linkExportStateOf`, pure and tested): the connection first (Connect Dropbox, which says the whole library
  syncs; Connecting with Cancel; a failure in Settings' words), then a share the account lists (`ShareLinkController`,
  `ui/share/`; one whose copy is gone is `CopyMissing`, saying so and offering Create link, which replaces it), then how
  the last Create link went. Each failure is shown by what can be done: Connect again (missing
  scope, refused authorization), Try again (network, anything unforeseen), or nothing. The FAB (`LinkButton`, in
  `SaveButton`'s place) is `linkActionOf`; Create link is not held for a sync run.
- A creation runs in the view model's scope and is forgotten when the screen goes; a share just made shows before the
  list catches up. Song ticks are hidden: a link shares the whole setlist. Offered in read only mode.
- Shared: the QR code (white card, black modules, whatever the theme), the link selectable in monospace, the day, and a
  too-large note. Copy link is the FAB (a snackbar where the platform says nothing, `isCopyConfirmedBySystem`); Share
  in the app bar sends the link as text (`FilePicker.shareText`); Save QR code (`qrCodeFileName`, the screen stays open,
  also Ctrl / Cmd + S) and Stop sharing are options. Stop sharing asks with `StopSharingConfirmation`, composed by the
  screen, not the dialog stack, under which the export would slide away.
- Connect from here keeps `SyncConsentOrigin.Export` (`ui/sync/CLAUDE.md`), so a start up answering the consent page
  reopens the screen on Link.

## A dialog to the view model, a screen to the user

- `ExportHost` (composed by `CampfireScreens` after the `NavDisplay`, before dialogs and snackbars) slides it in and out
  exactly like a pushed destination, following predictive back.
- An opaque `Surface` under the host's `NavigationBackHandler`, so Escape, browser Back, and the gesture close it; a
  failed export's snackbar still shows over it. The handler registers as the screen opens, as a sheet's does: the
  dispatcher asks the newest first, and a screen's own (a settings tab's, an open search's) would take the back.
- A focus group that cancels every exit while it is the dialog shown, so Tab never reaches the screen beneath
  (`clearAndSetSemantics` only hides that from screen readers).

## Format and files preview

- **The format is the first option**, with a line saying what each is for (the names alone don't tell anybody).
  `Format.FILES` are written by `ExportController.exportFiles`; a saved one closes the screen like a PDF.
- All PDF options fold away for files except a setlist's song ticks (switching doesn't restore unticked songs). A zip of
  none isn't offered unless the setlist has no songs.
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
- `ExportController.exportPdf` draws off the main thread into `pdfExportProgress` (a ring on the save button, which is
  Cancel, `cancelPdfExport`, until the picker is up). The button leaves while there are no pages. Failures, including
  `OutOfMemoryError` (not catchable on the web), report a failed export.
- A saved file closes the screen from the view model, even if not composed (an Activity recreated under the picker); a
  share leaves it open. Share is an app bar action where `FilePicker.canShare` (Android, iOS).
- `pdfFileName` names it by `LibraryFiles.songBaseName`, like the library; a running order gets `-running_order`, so
  the two exports of one setlist don't collide.

## Layout

- Phone: the preview is the options list's first item and scrolls away; from 520dp wide, options sit beside it.
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
- Ctrl / Cmd plus wheel zooms only in the desktop app (`isCtrlWheelZoomOwnedByApp`), since in a browser that is the
  page's zoom.
- A page is described to screen readers by its number.
