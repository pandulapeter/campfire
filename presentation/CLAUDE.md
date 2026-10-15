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

The entire UI plus the thin per-platform shells the `:app:*` modules call into. Depends on `:domain:api` and
`:chordpro`, never on repositories or local sources. `CampfireViewModel` is a `@KoinViewModel`, obtained with
`koinViewModel()`.

- **Shells** live in the `ui` package of each platform source set and only do what common Compose cannot; anything
  non-trivial belongs in `commonMain` (with `expect`/`actual`, see `ui/platform/`). Each provides `LocalFilePicker` and a
  `Flow` of files the OS handed over ("open with", share, drop); the phones' shells also a `Flow` of shared texts.
  Notes: `src/androidMain/CLAUDE.md`, `src/desktopMain/CLAUDE.md`, `src/iosMain/CLAUDE.md`, `src/wasmJsMain/CLAUDE.md`.
- **Everything else** is `commonMain`, with notes in the `CLAUDE.md` of each package under
  `src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`; `ui/CLAUDE.md` holds the view model and the
  cross-screen product rules; the chrome is `ui/chrome/`.

## Document imports

- `.pdf`, `.docx`, `.txt`, and shared text from either phone become ordinary songs before the UI sees them. Every
  user-started import is reviewed on the import screen first (`ui/screens/importReport/`).
- A pasted or dropped text (`PastedSongController`, `ui/state/importing/`) goes down the same import as a `.txt`
  (`isNewSongFromText`): one song the library lacks becomes the New song sheet filled in from it
  (`DialogType.NewSongFromText`, holding only what the converted text declares; nothing is written before Create, which
  opens the editor) unless it declares both its title and its artist, when it is written and opened like one the library
  holds (`isLoneSongWrittenAsIs`: the sheet is there for the name); anything else is the ordinary review. Blank, links only, or over 8 MB
  is a snackbar before anything is prepared. With unsaved editor text, or in read only mode, it is an import whose song
  is offered with Open instead of the sheet.
- Text shared from another app (`importSharedText`, a `SharedTextPayload`) takes the same way when it is one text;
  several are one import, a page's address alone is a snackbar. A trailing page address after selected text (Chrome's
  share of a highlight) is dropped with its quotation marks (`withoutSourceLink`), for a paste too.
- A text without a title is named by its share's subject without the site's boilerplate (`sharedTextName`), else by
  its first line that is not chords, an address, or markup; either is `LibraryFiles.normalizedName`d at once.
- Summaries report unreadable documents separately and append the number of converted songs actually written (English
  and Hungarian). A single converted song gets a snackbar **Open** (`Navigator.openImportedSong`); converted
  imports never navigate on their own.

## Strings

- `localization/ApplyLanguagePreference.kt` maps the preference to the generated `AppLocale` and sets
  `currentLanguage`, which every `stringResource` observes.
- The `com.hyperether.localization` plugin generates the string tables into
  `build/generated/compose/resourceGenerator/kotlin/commonCustomResClass` (package `…presentation.localization`).
- Its `generateTranslateFile` task declares only the resources folder *path* as input, so `build.gradle.kts` registers
  the `strings.xml` files too; without that, new strings render as "???" until a clean build.
- A new `values-xx` folder needs a clean build (to regenerate `AppLocale`), the language in the iOS `Info.plist`'s
  `CFBundleLocalizations`, and an `InfoPlist.strings` for the microphone prompt (see `app/ios`).
- `pluralStringResource(Res.plurals.x, count, count)`: the count is passed twice, once to pick the item and once to
  fill `%1$d`. `pluralTextResource` (for plurals carrying user text) takes the count as text.
