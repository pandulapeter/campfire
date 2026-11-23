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
  belongs in `commonMain` (`expect`/`actual`, `ui/platform/`). Each provides `LocalFilePicker` (on Android and iOS
  with the photo library too), a `Flow` of files the OS handed over ("open with", share), and drops; Android's also
  shared texts, iOS's the share extension's `InboxShare`s.

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

- A pasted, dropped, or shared text goes down the import as a `.txt` (`isNewSongFromText`), marked as a page's
  (`ImportedFile.isPageText`) where it is one text, so its page clutter is cropped on the device before it is converted
  (`PageCropperImpl`: the page model's weights, `files/page-model.bin`, read once on the first page, off the main thread;
  any failure, unreadable weights included, keeps the whole text; a PDF's or Word file's lines are scored there too, for the converter to crop each
  song). Several shared texts are not cropped. One song the library does not hold (a taken name included) opens the editor on it, unwritten (`EditorSession.openNewSongEditor`), read only mode too when shared,
  with a snackbar saying how the text was read (chords over lyrics, ChordPro, lyrics only, or an ABC tune's or block's
  notation kept), and **Use the whole page** where the crop left lines out; one the library holds is opened; anything
  else (several ABC tunes among them) is the ordinary review. Both drafts declaring no key are given the one song details
  would infer (`withInferredKey`, `writtenKeyOfText`), where the chords make one clear: the key of the chords as written,
  so over a `{capo}` the shapes', as a typed `{key}` is.
- The editor waits, as the import screen does (`ImportReportState.awaitNothingInTheWay`), for any open dialog, unsaved
  editor text, and the onboarding; replaces an editor with nothing unsaved; and holds the queue until no editor is left,
  so texts arriving together (an iOS inbox drained at once) open one after another.
- A paste, and a drop on Android and the desktop, is read with its HTML flavour too, and is that HTML's chord sheet
  where it draws chords the plain text lost (`pastedText`, `:libs:chordsheet`'s `ChordSheetHtml`), else the text as it
  is. The web's Paste button reads `clipboard.read()` (`readText()` only where there is no `read()`, never after a
  refused one, which would ask twice); iOS reads `public.html` or Safari's web archive in a single `items` access, since
  each access may ask Allow Paste.
- A share's subject (a page title) fills in, selected, the title the text does not declare, and the artist only from
  `<title> Chords by <artist>` (`:libs:chordsheet`'s `PageTitle.of`): a bare " by " or a dash is never split. A cropped
  page's own title and artist lines, read by the same rule (`SongEntry.pageTitle` / `pageArtist`), come first, field by
  field (`pageHeaderOver`); Use the whole page, which keeps those lines, gets only the subject's.
- A blank, links-only (an address, alone or beside a one-line title), or over-8 MB paste or share is a snackbar before
  anything is prepared, except that a blank share goes to the import, reported as skipped; several shared texts are one
  import.
- **A shared link is read once on the device when the switch allows it**: a text that is one page's address
  (`pageAddressIn`: alone, or among at most three short lines of a call to action or a title; never Campfire's own
  site) is `ImportSource.Page`, read by `LoadPageUseCase` (an offscreen web view, `PageSource`) with the import screen's
  loading stage up ("Reading the page…", Cancel), then cropped and converted as a pasted page, titled by the share's
  subject, else the page's title; its song's editor takes the loading screen's place. A page that never arrived (no
  network, a host not answering, nothing drawn in time) is the failure stage's `PAGE_UNREACHABLE` sentence (check the
  connection); one not read otherwise (a bot check, an empty page), or one the page model finds no song on (asked before
  conversion; a model that cannot run throws, and the page is converted whole), is `PAGE_NOT_READ`'s (open it in the
  browser, bring the page itself); both with Try again, which reads it again. With Pages from links off it is the links-only snackbar as before; where the platform cannot
  read pages (the web, a desktop without WebView2 or WebKitGTK) it is `Message.PageLinkNotReadHere`: copy the page
  itself, and on the web, get the app (an action opening the website's downloads).
- A text that is one Campfire link and nothing else (`sharedLinkIn`) is that link's import; a link beside other words
  stays text. Every way ends in `ImportController.importSharedLink`: a newer version's link is a snackbar, any other is
  downloaded from the address `SharedLink.Readable.downloadUrl` builds (never one the text names) and reviewed, unless
  the library holds all of it as it is: one song is then opened, one setlist scrolled to (`librarySetlist`), and a
  snackbar says it is already there.
- What the review chose is written by `ApplyImportUseCase`, with what it records for the demo and each shared link;
  the link's trust rule (`sharedLinkChoices`) is `:data:model`'s.
- Summaries report unreadable documents separately and append the number of converted songs actually written.
- Pictures arriving alone (`ImportPlan.imageFiles`, nothing else in the batch, read behind no screen) are one new song
  in the editor (`pictureSong`): titled by the first file's name, a directive per picture in order, each kept as a draft
  picture as the editor opens, a refused one said why (none kept, no editor), one over 40 MB among them (unread, so an
  oversized name, never a review for it). A picture beside anything else, or with
  the songs off, is reported as skipped. A review row counts the pictures its song brings and the ones it names but
  leaves out.

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
