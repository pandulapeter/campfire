<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# :presentation

The whole UI plus the thin per-platform shells. Depends on `:domain:api` and `:libs:chordpro`, never on repositories or
local sources.

- **Shells** (the `ui` package of each platform source set) only do what common Compose cannot; anything non-trivial
  belongs in `commonMain` (`expect`/`actual`, `ui/platform/`). Each provides `LocalFilePicker`, a `Flow` of files the OS
  handed over ("open with", share), and drops; Android's also shared texts, iOS's the share extension's
  `InboxShare`s.

## Package layers

A file imports its own layer and lower ones only; `.github/scripts/check_layers.py` holds the table and fails a commit
that breaks it (main source sets only, tests import from anywhere). A package belongs to the longest prefix listed;
every other `ui.*` package is app.

| Layer | Packages |
|---|---|
| foundation | the root package itself (`Module.kt`, the generated version), `localization`, `mapper`, `resources`, `ui.theme`, `ui.platform`, `ui.search` |
| components | `ui.components`, `ui.settingsPage` |
| app | everything else under `ui.`: `state`, `navigation`, the `ui.dialogs` package itself, `ui.dialogs.sheet`, `metronome`, `chords`, `songView`, … |
| surfaces | `ui.screens`, `ui.chrome`, every other `ui.dialogs.<sub>`, the `ui` package itself |

A file under `ui.screens.<a>` imports nothing of another screen's package (`ui.screens` itself, which holds what several
screens share, is allowed). A declaration a lower layer needs moves down, or is passed in. Only a destination's entry
file (`*Screen.kt` directly in `ui.screens.<a>`, or `ExportHost.kt`) and the `ui` package import `ui.CampfireViewModel`,
which the script checks; everything else is handed the holders, state, and callbacks it uses.

## Text and document imports

- A pasted, dropped, or shared text goes down the import as a `.txt` (`isNewSongFromText`). One song the library does
  not hold (a taken name included) opens the editor on it, unwritten (`EditorSession.openNewSongEditor`), read only mode
  too when shared, with a snackbar saying how the text was read (chords over lyrics, ChordPro, lyrics only, or an ABC
  tune's or block's notation kept); one the library holds is opened; anything else (several ABC tunes among them) is the
  ordinary review.
- The editor waits, as the import screen does (`ImportReportState.awaitNothingInTheWay`), for any open dialog, unsaved
  editor text, and the onboarding; replaces an editor with nothing unsaved; and holds the queue until no editor is left,
  so texts arriving together (an iOS inbox drained at once) open one after another.
- A share's subject (a page title) fills in, selected, the title the text does not declare, and the artist only from
  `<title> Chords by <artist>` (`sharedTextTitle`): a bare " by " or a dash is never split.
- A blank, links-only (an address, alone or beside a one-line title), or over-8 MB paste or share is a snackbar before
  anything is prepared, except that a blank share goes to the import, reported as skipped; several shared texts are one
  import. Nothing is fetched.
- A text that is one Campfire link and nothing else (`sharedLinkIn`) is that link's import; a link beside other words
  stays text. Every way ends in `ImportController.importSharedLink`: a newer version's link is a snackbar, any other is
  downloaded from the address `SharedLink.Readable.downloadUrl` builds (never one the text names) and reviewed, unless
  the library holds all of it as it is: one song is then opened, one setlist scrolled to (`librarySetlist`), and a
  snackbar says it is already there.
- What the review chose is written by `ApplyImportUseCase`, with what it records for the demo and each shared link;
  the link's trust rule (`sharedLinkChoices`) is `:data:model`'s.
- Summaries report unreadable documents separately and append the number of converted songs actually written.

## Strings

- `localization/ApplyLanguagePreference.kt` maps the preference to `AppLocale` (a system code through `:libs:chordpro`'s
  `LanguageCodes`, Android's `iw` being `he`) and sets `currentLanguage` as it is composed, not in an effect, so the
  first frame is already in the language and its direction.
- **The layout direction is the app language's** (`AppLanguage.layoutDirection`), never the system's: `CampfireApp`
  provides `LocalLayoutDirection` once, from `currentLanguage`. The shells provide none.
- The `com.hyperether.localization` plugin's `generateTranslateFile` declares only the resources folder *path* as input,
  so `build.gradle.kts` registers the `strings.xml` files too; without that, new strings render as "???" until a clean
  build. A new `values-xx` folder needs a clean build to regenerate `AppLocale` (the rest: the `add-language` skill).
- Plurals go through `pluralTextResource` because the plugin's own selector knows no language's rule
  (`localization/PluralRules.kt` holds CLDR's; every `AppLocale` needs a row, a test says so).
- Isolation marks (`TextIsolation.kt`) draw as nothing but are characters, so a text that is written, printed, or typed
  into a field takes the `plain…` variant, and the PDF's text layer drops them.
