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
  `Flow` of files the OS handed over ("open with", share); Android's also a `Flow` of shared texts, iOS's the share
  extension's `InboxShare`s. Each takes drops (`isDropAvailable`).
  Notes: `src/androidMain/CLAUDE.md`, `src/desktopMain/CLAUDE.md`, `src/iosMain/CLAUDE.md`, `src/wasmJsMain/CLAUDE.md`.
- **Everything else** is `commonMain`, with notes in the `CLAUDE.md` of each package under
  `src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`; `ui/CLAUDE.md` holds the view model and the
  cross-screen product rules; the chrome is `ui/chrome/`.

## Text and document imports

- A pasted, dropped, or shared text (`PastedSongController`, `ui/state/importing/`) goes down the import as a `.txt`
  (`isNewSongFromText`). One song the library does not hold (`newSongOf`, a taken name included) opens the editor on
  it, unwritten (`EditorSession.openNewSongEditor`, see `ui/screens/songEditor/CLAUDE.md`), in read only mode too when
  shared, with a snackbar saying how the text was read (chords over the lyrics, ChordPro, lyrics only, or, with no
  chords, the notation of an ABC tune or block kept); one the library holds is opened; anything else (several ABC
  tunes pasted at once among them) is the ordinary review. The editor waits, as the import screen does (`ImportReportState.awaitNothingInTheWay`), for any open dialog,
  unsaved editor text, and the onboarding, replaces an editor with nothing unsaved, and holds the queue until no editor
  is left, so texts arriving together (an iOS inbox drained at once) open one after another. A share's subject (a page
  title) fills in, selected, the title the text does not declare, and the artist only from `<title> Chords by <artist>`
  (`sharedTextTitle`): a bare " by " or a dash is never split.
- A blank, links-only (a page's address, alone or beside a one-line title), or over-8 MB paste or share is a snackbar
  before anything is prepared, except that a blank share goes to the import, reported as skipped; several shared texts
  are one import. Nothing is fetched.
- A text that is one Campfire link and nothing else (`sharedLinkIn`) is that link's import instead, a link beside other
  words staying text. Links the system opens the app with arrive as `CampfireApp`'s `shareLinks`; every way ends in
  `ImportController.importSharedLink`: a newer version's link is a snackbar, any other is downloaded from the address
  `SharedLink.Readable.downloadUrl` builds (never one the text names) and always reviewed.
- Summaries report unreadable documents separately and append the number of converted songs actually written (English,
  Hungarian, Romanian, German, Spanish, French, and Italian). A document that is one song is previewed like any batch of
  one song (`ui/screens/songDetails/`).

## Strings

- `localization/ApplyLanguagePreference.kt` maps the preference to the generated `AppLocale` (a system code through
  `:chordpro`'s `LanguageCodes` first, Android's `iw` being `he`) and sets `currentLanguage`, which every
  `stringResource` observes.
- **The layout direction is the app language's** (`AppLanguage.layoutDirection`), never the system's: `CampfireApp`
  provides `LocalLayoutDirection` once, from `currentLanguage`, so it turns with the texts. The shells provide none.
- The `com.hyperether.localization` plugin generates the string tables into
  `build/generated/compose/resourceGenerator/kotlin/commonCustomResClass` (package `…presentation.localization`).
- Its `generateTranslateFile` task declares only the resources folder *path* as input, so `build.gradle.kts` registers
  the `strings.xml` files too; without that, new strings render as "???" until a clean build.
- A new `values-xx` folder needs a clean build (to regenerate `AppLocale`), the language in the iOS `Info.plist`'s
  `CFBundleLocalizations`, and an `InfoPlist.strings` for the microphone prompt (see `app/ios`).
- `pluralStringResource(Res.plurals.x, count, count)`: the count is passed twice, once to pick the item and once to
  fill `%1$d`. `pluralTextResource` (for plurals carrying user text) takes the count as text.
