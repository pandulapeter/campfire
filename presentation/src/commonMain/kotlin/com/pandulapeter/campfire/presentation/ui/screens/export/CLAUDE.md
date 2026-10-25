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

The export screen, called **Share** in the UI: one song, a setlist, or a selection is shared, while the whole library
is exported (Settings' Export library as zip, a backup that pairs with Import). The code keeps the export names. The PDF
pipeline is in `ui/print/CLAUDE.md`.

## Entry and sources

- Opened as `DialogType.Export` from a song's "Share song" (with the setlist it was reached through, whose key it then
  prints in), a setlist's "Share setlist", or the selection mode's "Share songs" (`DialogType.Export.songs`, in the
  songs screen's sort order; one ticked song is that row's own Export).
- Settings' Show QR code and the answer to a consent asked from the Link format open it on Link for that opening only
  (`DialogType.Export.format`, `openingSettingsOf`), the stored format untouched; a format picked on the screen is
  stored as ever, and saved with the screen so a rotation keeps it over the opening's own.
- The screen asks the source two things: `hasRunningOrder` (setlist: running order page, zip manifest) and
  `hasSeveralSongs` (new page per song, Zip). Several songs are unnumbered, in library keys, zipped by
  `ExportSongsUseCase` as `campfire_songs.zip` and saved as `campfire_songs.pdf`.

## Formats and options

- One Share entry per song and setlist (and selection mode's "Share songs") opens the export screen; no separate
  file export entry. Formats, in the order offered and recommended (`exportFormatsOf`): **Link** (below, for Campfire
  users) for one song or setlist where the build has sync, the library's own files (for any ChordPro app): **ChordPro**
  for a song (the `.cho` as stored), **Zip** for a setlist (its `*.setlist.json` manifest plus songs) or for several
  selected songs (no manifest), and **PDF** (for printing). Link is the default; a stored format an export does not
  offer opens it on PDF (`withFormatIn`), the preference kept.
- **Every song goes out**: no song ticks and no choice of the setlist's content, which only cluttered the screen. A
  setlist's PDF is its song sheets, after its running order where Include running order is on; its zip holds the
  stored manifest unchanged.
- `PrintSettings` (format included) are local preferences (`PrintSettingsDocument`), independent of the viewer's text
  size and folded sections.
- **The Features switches reach the export**: an option whose feature is off (chords, which take tablature, diagrams,
  scales, and key; scale suggestions, which take scales; the metronome, which takes tempo) is neither offered nor printed (`PrintSettings.withinFeatures`), but the choice is kept
  for when the switch comes back.
- A setlist export keeps original slot numbers and each entry's transposition; a song reached through a setlist uses that
  entry's key. Missing or unreadable songs keep a visibly marked slot.
- The PDF's options (`PdfOptions`) are **sections of the document, one heading each**: Page (paper, orientation, text
  size, margins, page numbers), Running order for a setlist (whether, then folding out its own columns and its date,
  description, and artists), and Song sheets (their columns, a new page per song, then what a sheet prints in the order
  it prints it: artist, key, tempo, chord diagrams, scales, chords and grids, tablature (apart from the chords, offered
  where a song has any), comments). Columns are a stepper inside the section
  they shape, never a control between sections; a new option goes into the section, and at the place, where it prints.
- **Only what is printed goes into a PDF**: the printed key, the chosen songs and options; no original ChordPro or
  excluded metadata. No platform print service: the file is saved or shared.

## Link

- States (`linkExportStateOf`, pure and tested): the connection first (Connect Dropbox, which says the whole library
  syncs; Connecting with Cancel; a failure in Settings' words), then a share the account lists (`ShareLinkController`,
  `ui/share/`; one whose copy is gone is `CopyMissing`, saying so and offering Create link, which replaces it), then how
  the last Create link went. Each failure is shown by what can be done: Connect again (missing
  scope, refused authorization), Try again (network, anything unforeseen), or nothing. The FAB's action
  (`ExportActionButton`) is `linkActionOf`; Create link is not held for a sync run.
- A creation runs in the view model's scope, one per item (another item's still running does not hold this one's
  Create link), and is forgotten when the screen goes; a share just made shows before the list catches up, and is
  forgotten once the list names it, so a stop on another device clears an open screen. Offered in read only mode.
- Shared: the QR code (white card, black modules, whatever the theme), the link selectable in monospace, the day, and a
  too-large note. Copy link is the FAB (a snackbar where the platform says nothing, `isCopyConfirmedBySystem`); Send
  link in the app bar sends the link as text (`FilePicker.shareText`); Save QR code (`qrCodeFileName`, the screen stays open,
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

- **The format is the first option** (`FormatChoice`): a card per format (`ChoiceCard`), each saying whom it suits (the
  names alone don't tell anybody), all at once so nobody has to try each.
  `Format.FILES` are written by `ExportController.exportFiles`; a saved one closes the screen like a PDF.
- All PDF options fold away for files.
- Files skip the layout; `FilesPreview` is a summary that never scrolls (the name it is saved under, `filesFileName`;
  what it holds and what reads it; how many songs are missing), since the text or a file list was a second scrolling
  area beside the options. The cards say in a short line whom a format suits; the preview under them says the rest.
- Stacked on a phone, only the PDF's pages take a fixed height (`STACKED_PREVIEW_HEIGHT`); the files and the link are
  list rows as tall as what they say (`isInList`: messages start-aligned like the format's descriptions, no centring),
  `LIST_PREVIEW_TOP_PADDING` under the cards, and the pane's crossfade animates the height between formats.
- Option rows use `listItemAnimation`.

## State

- `rememberExportState(dialog)`: preview page and zoom are `rememberSaveable` (survive rotation); options, source, and
  Retry count start over.
- The previous document stays up while the next is laid out (debounced). **Save only takes a document laid out from what
  is on screen**: an earlier tap (a FAB has no disabled look) is kept and carried out once it is (`ExportRequest`).
- Options go to `pendingPrintSettings` as they change, saved after settling, and whatever is pending is saved however
  the screen closes (`setVisibleDialog`).
- `ExportController.exportPdf` draws off the main thread into `pdfExportProgress` (a ring on the save button, which is
  Cancel, `cancelPdfExport`, until the picker is up). The button leaves while there are no pages. Failures, including
  `OutOfMemoryError` (not catchable on the web), report a failed export.
- A saved file closes the screen from the view model, even if not composed (an Activity recreated under the picker); a
  share leaves it open. Share is an app bar action where `FilePicker.canShare` (Android, iOS).
- `pdfFileName` names it by `LibraryFiles.songBaseName`, like the library.

## Layout

- Phone: the preview is an item of the options list, under the format choice that decides it, and scrolls away; from
  520dp wide, options sit beside it. The pages take the keyboard's focus (desktop, web) only beside the options: in the
  list a focused item is scrolled into view, which would carry the formats away as PDF is chosen.
- **The preview is its whole pane, and no floating control ever rests on the page at zoom 1**: the page fits the pane
  less one `FLOATING_CONTROLS_CLEARANCE` band at each end a control floats over (`PageFit`, `fitArea`). In a short window
  (`SHORT_WINDOW_HEIGHT`) the controls stack at the pane's end and the page sits beside them; the page-count pill is
  sized for its widest possible text so the page doesn't shift as pages turn.
- The Link preview follows the same rule (`linkPreviewPaddingFor`), its QR code sized to its pane (`qrCodeSizeFor`, at
  most 240dp, scrolling only below 120dp), so it is shown whole: in a short window beside the options the shared link
  takes the pane less an end column, Copy link collapsing to its icon there; every other state keeps the bottom band,
  its button's label saying what a bare icon would not. Each state keeps its own padding while it fades, and a resize
  animates it.
- **One FAB for every format** (`ExportActionButton`): a change of format or of the link's next step crossfades its
  icon and label inside it, never a button leaving for another. The floating controls (it and the page pill) come and
  go through `FloatingControlVisibility`, which fades each thing drawn (`ModulateAlpha`, no offscreen buffer to cut a
  shadow off) and draws the shadow itself, growing with the fade; the controls draw none of their own. Those of the
  format the screen opened on appear without animating: their pages arriving is data, not something the user did.
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
