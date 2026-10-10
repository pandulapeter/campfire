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

The export screen. The PDF it draws is laid out, rendered and written by `ui/print` (see its notes).

## Export

`ui/screens/export/ExportScreen.kt` and `ui/print/`, opened as `DialogType.Export` from the one export entry of a song's
actions ("Export song", which also titles the screen) (with the setlist it was reached through, whose key it then prints
in) and a setlist's ("Export setlist"). It is a dialog to the view model and a screen to the user: `ExportHost`,
composed by `CampfireScreens` after the `NavDisplay` and before the dialogs and the snackbars, deals it in over
everything and takes it away again however the dialog goes, exactly as a destination is pushed and popped: from the
right, on the same spring (`slideFractionSpec`, `slideSpec` as a fraction of the width), with the `NavDisplay` giving
way by the same 12% (`ExportTransition` is the one progress both read), and following a predictive back gesture with the
finger. It is an opaque `Surface` under the host's `NavigationBackHandler`, so that Escape, the browser's Back and the
back gesture close it as they closed every sheet, and the snackbar of a failed export still shows over it. It is a focus
group that cancels every exit while it is the dialog shown (not while it slides away), so Tab never reaches the screen
under it, which `clearAndSetSemantics` only hides from a screen reader. The preview's
pager fades into nothing under the app bar and towards the options (below it, or at its start beside them) as far as a
zoomed, panned or turning page has moved past where it rests, so a page at rest is drawn whole.

**The format is the first option** (`PrintSettings.format`, saved with the rest), a `SegmentedChoice` with a line under
it that says what each is for — PDF for printing, the library's own files (`Format.FILES`) for sharing with other
Campfire users — since the names alone do not tell anybody which to pick. Those files are labelled by what they are:
ChordPro for a song, its `.cho`, and Zip for a setlist, whose line says what is in it — a setlist manifest, which keeps
the running order and every song's key, and the chosen songs as ChordPro files. They are written by the export use cases
(`CampfireViewModel.exportFiles`), and a saved one closes the screen as a saved PDF does.

Every PDF option folds away under them (`PdfOption`) except a setlist's song checkboxes, one selection for both formats,
so switching does not put back the songs that were left out; a zip of some of them is narrowed to those songs, manifest
included (`chosenSongFileNames`, null when all are ticked so the manifest goes out as it is stored), and one of none is
not offered unless the setlist has no songs at all. The layout is not run for them, and the preview crossfades to what
is written (`FilesPreview`): the song's text from the snapshot (`PrintSong.text`), unwrapped in the monospaced font, or
the zip's files (`ZipContents`) — the manifest under its file name, then each chosen song under its `.cho` name, all of
them under one heading, a missing one dimmed as not included. Save and Share are the same two buttons for either.

The screen's state is a `ExportState` from `rememberExportState(dialog)`, which remembers each field on its own: the
selection, the preview's page and how it is zoomed are `rememberSaveable`, so a rotation keeps them; the options, the
source and the Retry count start over. The previous document stays on screen while the next is laid out (120 ms after
the last change), and Save only takes a document laid out from what the screen shows now — a tap made before it is,
which a floating action button has no disabled look to refuse, is kept and carried out once it is (`ExportRequest`).
Options are written to the view model's `pendingPrintSettings` as they change and saved to the preferences once they
have settled for half a second; whatever is still pending is saved as the screen goes, however it goes
(`setVisibleDialog`). On phones the preview is the first item of the options' list, 360dp for the page — large enough
to judge it by — plus the band of the page buttons over it (`STACKED_PREVIEW_HEIGHT`), and scrolls away with them, and
the list ends above the save button (`FLOATING_CONTROLS_CLEARANCE`) rather than under it, so the button never rests on
an option.

From 520dp of width (a phone on its side) the options are 260dp beside the preview instead, 330dp on a window that is
also tall, and the button floats over the preview. **The preview is the whole of its pane** and its controls float over
it: the page buttons are a raised pill at the top end of the preview — under the app bar where the preview is a pane,
and inside the preview item on a phone, scrolling away with it — fading in and out with the PDF format. **Neither control
ever rests on the page**: at a zoom of 1 the page fits the pane less its margin at the sides and one band
(`FLOATING_CONTROLS_CLEARANCE`, the taller control and a margin on either side of it) at each end that a control floats
over — the pill's at the top everywhere, the save button's at the bottom where the preview is a pane (`PageFit`,
decided by the screen, which places the controls, and `fitArea`) — so it is centered between the two with the same room
over it as under it. In a short window (`SHORT_WINDOW_HEIGHT`, a phone on its side) the two bands would leave a page no
taller than a control, so there the page sits beside the two, which stack at the pane's end, in the pane less the column
they take — as wide as the wider of the two, measured, since the pill's width is its text's. A zoomed page grows out to
the pane's edges and under them, which is what says that the sheet of paper is what grew. Pages are turned by a swipe, the
buttons, or the arrow, Page Up / Down, Home and End keys, and zoomed by a pinch (around its centroid), a double tap, on
a touch screen the second tap held and dragged (`doubleTapZoom`: down zooms in, up out, doubling every 120dp, around
where it landed; the second press is consumed so neither the pager nor the pan moves under it) or a touchpad pinch
(`magnifyByTouchpad`, the song details screen's path, around the pointer while it is over the page); Ctrl / Cmd and the
scroll wheel zoom in the desktop application only, which `isLaunchScreenWholeStartup` stands in for, since in a browser
that chord is the page's own zoom.

Zooming grows the whole sheet, edges and all, past the pane that cuts it off, as a document viewer does: content growing
inside a page that kept its size read as the text being enlarged for the file. The zoom is kept as the point of the page
in the middle of the pane (`PageView`) rather than as a pan in pixels, so a pane laid out at another size shows the same
part of the page; turning the page opens the next one whole. The options are composed once whatever the arrangement
(`PrintPanes`), and the preview, which is composed again as it moves between their list and a pane of its own, keeps its
page and zoom in the screen's saved state, so crossing between beside and under keeps the zoom and the options' scroll,
and the options travel to their new place on the spatial spring (`animateBounds`), while a resize within one arrangement
is followed as it comes. A page is described to a screen reader by its number.

Export: `CampfireViewModel.exportPdf` draws the pages off the main thread, counting them into `pdfExportProgress`, which
the save button shows as a ring in place of its icon; while it counts, the button is Cancel (`cancelPdfExport`). Once
the picker is up there is nothing to cancel, and the progress is gone. The button leaves while there are no pages. A
failure, an `OutOfMemoryError` included (except on the web, where it cannot be caught), is reported as a failed export.
A saved file closes that screen (the view model dismisses it, whether or not the screen is composed at that moment, e.g.
after an Activity recreated under the picker); a share leaves it open. Share is an app bar action where
`FilePicker.canShare` (Android and iOS). The file is named by `pdfFileName` the way `ExportFileNames.kt` names a song,
from its header, and a setlist's running order gets a `-running_order` suffix so the two exports of one setlist do not
collide.
