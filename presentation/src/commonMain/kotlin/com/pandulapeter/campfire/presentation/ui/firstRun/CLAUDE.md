<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/firstRun

The demo library.

### `ui/firstRun/DemoLibrary.kt`

`ui/firstRun/DemoLibrary.kt` — the songs and the one setlist the app is shipped with, bundled in
`composeResources/files/demo` and read back as `ImportedFile`s, so that they reach the library through `importFiles`
exactly as a dropped archive does and nothing here writes a file itself. They are named as the library will name them,
which is what lets `isPresentIn` answer whether they are already there from the library's own file names rather than
from anything the app remembers doing — so the offer in the settings screen comes back when one of them is deleted, and
putting them back adds only what is missing, since an import disregards a name taken by identical content. The same
offer is the last of the empty library state's buttons (`components/ListPlaceholder.kt`, after "Create a song" and
"Import files"), where it needs no condition of its own: a library with none of the demo songs in it is exactly what
that state is.

`EmptyState` takes those as a list of `EmptyStateAction`s, the first filled and the rest outlined, laid out in a row
where the window is wider than it is tall and stacked to one width where it is not - three labels this long are a list
rather than a row, and a portrait window has no room for them side by side anyway.
`CampfireViewModel.plantDemoLibraryOnFirstRun` is the one import nobody asks for, and it only happens where
`IsFirstRunUseCase` and an empty library — a whole one (`ScreenData.isWholeLibrary`), not a folder that could not be
read standing in empty — agree that this installation has nothing of its own; it announces nothing, and the preferences
are saved afterwards, whether anything was planted or not, so that the next start is no longer a first one — if they
could be read at all: a first run whose read failed saves nothing, and the launch screen is released before that save
rather than after it. It is imported directly rather than through the import queue, and the queue takes no batch before
that decision is over (`demoLibraryDecision`): a file opened with the app on its first start is queued before the
library has even been read, and it goes into a library that already has the demo rather than the demo being planted
after it.

`isDemoLibraryPending` is what keeps the launch screen up over the whole decision: it starts out true rather than being
set when the planting begins, because the scan can finish before the app has worked out whether it is planting anything,
and an empty library would then be announced a moment before the songs arrived. Reading the bundled files is given ten
seconds (`readDemoLibrary`): on the web they are requests to the site, and one that never answered would hold the launch
screen, and the web's loading page, for good. Both ways of planting them remember what they wrote under the demo's own
names (`RememberDemoLibraryFilesUseCase`, from `applyImportPlan` for a request marked `isDemoLibrary`), which is what
lets sync take another version's untouched demo from the cloud folder instead of a copy of each; the first run's
preferences write that follows is an `updateUserPreferences` rather than a save of the view model's copy, which could
lag the record by a dispatch and write it back out. See the root `CLAUDE.md`.

## The demo library and the first run

### The app is shipped with two songs and one setlist

**The app is shipped with two songs and one setlist**, in `presentation/src/commonMain/composeResources/files/demo`:
public domain campfire standards, bundled as the plain ChordPro and setlist files they are and reaching the library
through the ordinary import, so they collide, are numbered and are disregarded when the same file is already there like
anything else. The songs are few on purpose and chosen so that between them they use the directives the song details
screen draws, and every one carries a `Demo` tag, so that they can be filtered out of a library that has grown past
them. They are planted once, on a run that finds no preferences document *and* an empty library — which is what a fresh
installation looks like from the inside, and is why a library somebody has been using is never touched. That first run
writes the preferences whether it planted anything or not, so an installation that started with an import of its own and
was emptied later is not taken for a fresh one.

Settings offers to add them for as long as the library is missing any of them, so a deleted one comes back by being
asked for rather than on its own. Both ways of planting them remember what they wrote under the demo's own names
(`RememberDemoLibraryFilesUseCase`, a local-only preference), so that a sync run meeting another version's untouched
demo in the cloud folder takes it instead of keeping a copy of each. That first run is also the only one that opens with
the **welcome sheet** over the library: a line about the app, the theme and the color, and the way to Settings, naming
Dropbox sync where the build has it — short on purpose, since the demo songs behind it say the rest. Each file is named
exactly as the library would name the song inside it, which is what lets one list both read the resources and answer
whether they are already there.

### What's new

**What's new** introduces each version once, after the app is on screen and startup import questions have finished.
`UserPreferences.seenWhatsNewVersions` remembers every introduced version and the first installed version, which is
skipped in favor of the welcome. Empty `whats_new_message` resources suppress the dialog and still record the version;
the prepare-release skill replaces or clears that message in both languages for every release.
