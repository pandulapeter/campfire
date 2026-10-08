<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# New song from clipboard — implementation plan

Written 2026-10-07 against `a0c6b7789`. A chord sheet copied anywhere — chords over lyrics from a page in the user's own
browser, a note, a ChordPro file opened as text — becomes a song with one tap, converted by the same
`ChordSheetConverter` path a `.txt` import and Android's shared text already take. Nothing about it reaches the
network: the text is read from the clipboard, and a link in it is never opened.

## 1. What is being built

1. **Paste a chord sheet**, a third entry of the Songs screen's New song menu (`NewItemMenu`), after Create a song and
   Import files, and the same button in the empty library's state.
2. **One song is a New song sheet filled in**: the clipboard's text is converted and compared with the library, and
   where it is one song the library does not already hold, the New song sheet opens with the title, artist and the
   other fields the conversion found, under a line saying how the text was read. Create writes it, named by what the
   sheet says, and opens it in the editor. Closing the sheet writes nothing.
3. **Several songs, or a song already there, are the ordinary import**: a songbook pasted whole is split, numbered,
   questioned and reported exactly as the same text picked as a `.txt` file would be; a song the library already holds
   is opened instead of written again.
4. **Ctrl / Cmd + V on the Songs screen**, on the desktop and the web, with nothing focused, does the same as the menu
   entry; so does **text dropped** onto the window there.
5. **Pasting chords over lyrics into the editor** offers, in a snackbar, to convert the pasted lines into ChordPro. A
   tap converts them in place, as one step of the field's undo history; nothing is converted without it.
6. **One path from a text to a new song** (`CampfireViewModel.newSongFromText`), which the share extension plan
   (`documentation/plans/share-extension.md`) is to call for the iOS share extension and, should its audit decide so,
   for Android's shared text.

### What is already there, and what is not

- **The conversion is done.** `PrepareImportUseCaseImpl.planSongs` runs any `.txt` through
  `ChordSheetConverter.convert(ChordSheet.ofPlainText(…))` — ChordPro is passed through untouched, chords over lyrics
  become bracketed lines, prose is escaped — then `ChordProSplitter`, `ChordProNotation.convertText` and
  `ChordProPrettifier`, and names each song with `SongRepository.importFileName`, the text's own file name standing in
  as the title where the song declares none. `ImportPlanner` then says `NEW`, `IDENTICAL` or `CONFLICTING` for each.
- **Text as a file is done too**, but only on Android: `importSharedTexts` in `app/android`'s `AndroidFileImport.kt`
  names an `ImportedFile` after `EXTRA_SUBJECT` or the text's first plain line (`firstPlainLine`, `cleanedForFileName`,
  `UNTITLED`), empties a text that is nothing but links (`isOnlyLinks`), and hands it to `importFiles(files)`. Those
  helpers are private to the app module, which is the first thing this plan moves (§3.1).
- **The import queue is the place to decide in.** `CampfireViewModel.import(request)` prepares a plan, asks the
  conflicts question or applies it, and owns the progress dialog, its Cancel and the one-import-at-a-time rule. A text
  that turns out to be one new song stops there and becomes a sheet instead of a write (§4.2), so nothing is prepared
  twice and nothing new has to learn to wait for an import.
- **New song writes first.** `createSong` calls `CreateSongUseCase`, which writes the template through
  `SongRepository.createSong` (numbered on a collision, never asked about) and opens the editor on the file. The
  editor itself only ever edits a file that has a name: its field is keyed by `CampfireDestination.SongEditor.fileName`,
  Save writes that name, and a draft whose file is missing is reopened as a file that went away
  (`Message.EditedSongFileGone`). That is why the result is a sheet before the file rather than an unsaved editor
  before the name — see default 2.
- **Nothing reads the clipboard today.** `LocalClipboard` is not used anywhere; the editor's field gets its paste from
  Compose. Compose Multiplatform 1.12.1's `Clipboard.getClipEntry()` exists on every platform, but what a `ClipEntry`
  holds is read differently on each (`clipData` on Android, the experimental `getPlainText()` on iOS,
  `asAwtTransferable` on the desktop, `navigator.clipboard.read()` behind it on the web, which swallows a refusal into
  an empty clipboard). So the reading is one small `expect`/`actual` of the app's own (§4.1).
- **Drops take files only.** `CampfireDesktopApp`'s `dragAndDropTarget` starts a drag only for
  `DataFlavor.javaFileListFlavor`; the web's `listenForDrops` (`FilePicker.wasmJs.kt`) collects only items whose kind is
  `file`. Dragged text is refused on both.

### Defaults this plan assumes — veto any of them before the work starts

1. **The menu entry is always there**, never enabled or hidden by what the clipboard holds. Asking the clipboard
   whether it has text is free on Android and iOS but not on the web, and an entry that comes and goes as the user
   copies things elsewhere is one that moves under the finger. An empty clipboard is answered with a snackbar.
2. **One song is a sheet before the file, not an editor before the name.** The New song sheet already asks for exactly
   what a pasted text most often gets wrong — the title and the artist — and the file is then named by what the user
   confirmed, so a song never starts life offering Update file name. An unsaved editor on a song with no file would
   need an editor that can be renamed by its first save, a draft that knows it never had a file, and a back stack entry
   rewritten under a field keyed by its name; none of that is worth it when the sheet does the job.
3. **Create opens the editor**, as New song does, and not the song details screen: a conversion is a best attempt, and
   the editor is where it is checked. This is not the "converted imports never navigate automatically" rule being
   broken — the user asked for a new song and confirmed it.
4. **A name that is taken is numbered**, as New song does (`SongRepository.createSong`), rather than put as the
   import's keep/replace question: the sheet is New song, and the user has just typed the title it is named by.
5. **Text with no chords is still a song.** A singer pasting lyrics is pasting a song; the converter escapes prose
   rather than guessing at it, and the sheet's line says no chords were found (§5.1).
6. **Several songs and duplicates go through the import as it is**, the report screen, Details and the conflicts
   question included; nothing about a multi-song paste is special.
7. **The editor's offer converts the pasted lines only**, never the rest of the song, and only chord rows over lyric
   rows; sections, headers and escaping are left to the whole-song conversion (§3.2).
8. **Read only mode takes all of it away**: the menu entry, the empty-state button, the shortcut, the text drop, and
   the editor's offer (the editor is not reachable there anyway). File drops and Open with are unchanged.
9. **No iOS `UIPasteControl`**: the deployment target is 15.3 and the control is iOS 16's, inside a Compose screen it
   would be a UIKit view of its own. iOS's own paste prompt is accepted as the price of a button (§4.1).

## 2. Modules

No new module. The work is in `:chordpro` (the fragment conversion), `:domain:api` / `:domain:implementation` (two use
case changes), `:presentation` (nearly all of it) and `app/android` (the helpers moved out).

## 3. Shared logic

### 3.1 `SharedText` (`:presentation`, `commonMain/…/ui/SharedText.kt`)

Moved from `AndroidFileImport.kt`, with its behaviour unchanged, so that the clipboard, a drop, Android's share and the
share extension name a text the same way:

```kotlin
internal object SharedText {
    /** The text as the file an import reads: named after [subject] or its first plain line, ending in `.txt`. */
    fun fileOf(text: String, subject: String? = null, index: Int? = null): ImportedFile
    /** Every non-blank line is an address, which is not a song and is never fetched. */
    fun isOnlyLinks(text: String): Boolean
    /** Whether the text is within what one import reads ([ImportLimits.MAX_TEXT_FILE_SIZE] as UTF-8). */
    fun fitsImport(text: String): Boolean
}
```

It is `internal` to `:presentation`; `importSharedTexts` in `app/android` reaches it through a public wrapper in the
same file (`sharedTextFile(text, subject, index)`), the way `toImportedFiles` is reached today. A name made from the
first line keeps `MAX_SHARED_NAME_LENGTH` (80) and the `untitled` fallback.

### 3.2 `ChordSheetConverter.convertFragment` (`:chordpro`)

```kotlin
/**
 * The chord rows of [text] set into the lyric rows under them, every other line as it was; null where [text] is
 * ChordPro already or has no chord row to convert.
 */
fun convertFragment(text: String, normalize: (String) -> String = { it }): String?
```

The same row classification and snapping `convertSong` uses for a pair of a chord row and a lyric row, and for a chord
row alone (bracketed on a line of its own) — and nothing else: no `{title}` guessed from the first line, no
`{start_of_verse}` for a "Verse 1:" label, no escaping of brackets and braces. It is what the editor's offer writes
into a song that already has its header and sections. `isChordPro` is shared with `convert`, so a ChordPro paste is
never offered a conversion. A domain wrapper, `ConvertChordSheetFragmentUseCase` (`:domain:api`, a `@Factory`
`…Impl` in `:domain:implementation` beside `PrettifyChordProUseCaseImpl`), keeps the presentation layer off the
converter, as the conventions ask.

### 3.3 `CreateSongUseCase` takes a text

```kotlin
suspend operator fun invoke(title: String, artist: String, metadata: Map<Field, String> = emptyMap(), text: String? = null): Song
```

With a `text`, that text is written instead of the template: `ChordProMetadataFields.set(text, metadata + title +
artist)`, a blank value removing the field (the user may have cleared a wrong guess), and the file named by
`displayTitle` as today. No `{key}`, `{capo: 0}`, `{tempo: 120}` or `{time: 4/4}` lines are added: those are the
template's invitation to fill them in, and a pasted song that declares them already has them. `CreateSongUseCaseImplTest`
grows with it.

### 3.4 Tests (`desktopTest`)

- `ChordSheetConverterTest`: `convertFragment` on a chord row over a lyric row, a chord row alone, a fragment with a
  label line and a title-like first line (both left as written), a ChordPro fragment and prose (null), the Latin and
  German rows the whole-sheet tests already pin, tabs and wide chord rows.
- `CreateSongUseCaseImplTest`: a text with its own header, the sheet's values replacing it, a cleared field removed,
  no template lines added, the name from the confirmed title and subtitle, a collision numbered.
- `SharedTextTest` (`:presentation` `commonTest`, new): the subject, the first plain line past directives, sections and
  comments, a numbered subject for several texts, links alone, a name cut at 80, `untitled`, the size limit at its
  boundary in UTF-8 bytes rather than characters.
- `PasteWatcherTest` (`:presentation` `commonTest`, new): which changes count as a paste of lines (§5.3) — a typed
  character, a typed line break and a one-line paste do not; a paste of two lines and more does; a paste replacing a
  selection is recorded at the selection's start.

## 4. `:presentation`

### 4.1 Reading the clipboard (`ui/platform/ClipboardText.kt`)

```kotlin
internal sealed interface ClipboardText {
    data class Text(val value: String) : ClipboardText
    data object None : ClipboardText      // empty, or nothing that is text (an image, a file, a link only on Android)
    data object Refused : ClipboardText   // the platform held text and would not hand it over
}

internal fun interface ClipboardTextReader { suspend fun read(): ClipboardText }

@Composable internal expect fun rememberClipboardTextReader(): ClipboardTextReader
```

| Platform | How | Refused when |
| --- | --- | --- |
| Android | `LocalClipboard.current.nativeClipboard` (`ClipboardManager`): `primaryClipDescription` first, which reads nothing and shows no toast, and `None` unless it has a `text/*` type; then the first item's `text`. A clip that is only a URI is `None`: resolving it would read a file the user did not pick. | never; Android 10+ only refuses an app in the background, and this is a tap |
| iOS | `UIPasteboard.generalPasteboard` directly: `hasStrings` first, which never prompts; then `string`, which on iOS 16+ asks "Allow Paste" unless Settings → Campfire → Paste from Other Apps says otherwise | `hasStrings` was true and `string` came back nil — the user said Don't Allow, or the setting is Deny |
| Desktop | `awtClipboardText()` (`desktopMain/ui/platform/ClipboardText.desktop.kt`): the AWT system clipboard's `DataFlavor.stringFlavor`, on `Dispatchers.IO`, an `IllegalStateException` (the clipboard busy) tried once more after 100 ms | never |
| Web | `navigator.clipboard.readText()` through one `js(...)` block; `NotAllowedError` is `Refused`, a missing `readText` (no secure context, an old browser) is `Refused` too | the permission prompt answered no (Chrome), the Paste callout dismissed (Safari, Firefox) |

Whatever comes back is `None` when blank. Android 12 and later say "Campfire pasted from your clipboard" in a toast of
their own after the read; that is the system's and is not suppressed.

### 4.2 The view model

- **`newSongFromText(text: String, subject: String? = null)`** — the one entry point (§1.6). In order: blank is
  `Message.ClipboardEmpty`; only links is `Message.PastedOnlyLinks`; over the limit is `Message.PastedTextTooLarge`;
  otherwise `SharedText.fileOf(text, subject)` is queued with `enqueueImport(listOf(file), isNewSongFromText = true)`.
- **In read only mode** `newSongFromText` is only reached from the share plan's entries (this plan's own are gone
  there, default 8): the request is an ordinary import with `shouldOpenSong = true` rather than
  `isNewSongFromText`, so the song is written and offered with Open and the New song sheet, which read only mode does
  not show, never opens.
- **`newSongFromClipboard(reader: ClipboardTextReader)`** — launched in `viewModelScope`, since the menu is gone before
  the read returns (iOS's prompt can take as long as the user takes): `Text` goes to `newSongFromText`, `None` is
  `Message.ClipboardEmpty`, `Refused` is `Message.ClipboardRefused`. Not started while `isImporting`, as
  `importFiles(filePicker)` is not.
- **`ImportRequest.isNewSongFromText`**, and in `import(request)`, once the plan is prepared and before the conflicts
  question: a plan of exactly one song entry, no setlist and nothing skipped, whose status is not `IDENTICAL`, is not
  applied. The progress goes away, `isImporting` is released, and `showDialog(DialogType.NewSongFromText(text =
  entry.text, values = …, isConverted = entry.isConverted, hasChords = …))` takes its place, the values read with
  `parseChordPro` and `ChordProMetadataFields.valueOf` over `SONG_METADATA_FIELDS` as `showSongMetadataDialog` reads
  them, and the title, where the text declares none, the file name's stem unless it is `untitled`. `hasChords` comes
  from the same parse. The request is settled there: the sheet is not an import, and nothing waits for it.
- **An `IDENTICAL` song** is applied as any plan is (it writes nothing), and `applyImportPlan`'s `songToOpen` takes it
  for this kind of request whether or not it was converted, so the library's copy is opened in the song details screen
  and `Message.ImportFinished` says it was already there.
- **Everything else** — several songs, a plan with something oversized or skipped — carries on as the import it is.
- **`createSongFromText(values, text)`** — the sheet's Create: `CreateSongUseCase(…, text = text)` through
  `launchLibraryChange`, then `openEditor(fileName)`. Where an editor holding unsaved text is on top (only possible
  through the share plan's entries, which can arrive over anything), the editor is not pushed and the song is announced
  with Open, by the rule `openImportedSong` follows.
- **`isNewSongPasteAvailable`** — the Songs screen on top, no dialog, sheet or overflow menu
  (`visibleDialog`, `isAnyOverflowMenuOpen`), not performance mode. What the shortcut and the text drop ask, the way
  they ask `openCurrentSearch`.

### 4.3 The Songs screen

- `NewItemMenu` gets `onPaste: (() -> Unit)? = null`, a third `DropdownMenuItem` with `ic_content_paste` (new, Material
  Symbols' Content Paste, in `composeResources/drawable` like the others). The Setlists screen passes none. Its KDoc's
  "the first two buttons of the screen's own empty state" becomes three.
- `ListPlaceholder` gets `onPaste` with the other three, and `NO_SONGS` lists Create a song, Import files, Paste a chord
  sheet, Add the demo songs — the same order as the menu, the demo songs still last. `songs_empty_hint` names pasting.
  Both stand or fall with `onNewSong`, so performance mode takes them as it takes the rest.
- `SongsScreen` passes `{ viewModel.newSongFromClipboard(clipboardReader) }` to both, `clipboardReader` from
  `rememberClipboardTextReader()`.

### 4.4 The desktop and the web

- **Desktop shortcut**: `CampfireViewModel.handleKeyEvent` answers Ctrl / Cmd + V (not with Alt, for AltGr's sake as
  the zoom keys say) when `isNewSongPasteAvailable`, with `newSongFromClipboard { awtClipboardText() }`. The handler
  only hears keys nothing focused took, so a focused field still pastes into itself.
- **Web shortcut**: a `PasteShortcutEffect` in a file of its own beside `SearchShortcutEffect.kt`: a `paste` listener on
  the window in the capture phase that leaves alone a paste into an `input`, a `textarea` or anything editable (the
  hidden input of a focused Compose field), and otherwise, when `isNewSongPasteAvailable`, takes
  `event.clipboardData.getData('text/plain')`, calls `preventDefault` and `newSongFromText`. The `paste` event carries
  the text with no permission and no prompt, which is why the shortcut is not `readText()` on the web.
- **Desktop text drop**: `shouldStartDragAndDrop` also accepts `DataFlavor.stringFlavor` where the drag offers no file
  list and `isNewSongPasteAvailable`; `onDrop` reads the string in the same `try` the file list is read in and calls
  `newSongFromText`. A file list still wins over a string (a file dragged from Finder offers both).
- **Web text drop**: `collect` in `listenForDrops` falls back, where no item is a file, to
  `dataTransfer.getData('text/plain')`, handed through the same promise queue (`nextDrop`) as a string rather than an
  array of files. `droppedFiles()` keeps emitting the files to `CampfireWebApp`'s `filesToImport`, and a
  `droppedTexts()` flow next to it carries the strings to a collector in `CampfireWebApp` that calls `newSongFromText`
  when `isNewSongPasteAvailable` and drops the text otherwise. A dragged link (`text/uri-list` with its address as the text) is links only, and is answered so.

### 4.5 Strings

Every one in `values/strings.xml` and `values-hu/strings.xml`; the Hungarian needs writing. A message that takes no
text from elsewhere is a plain `stringResource`.

| Key | English |
| --- | --- |
| `songs_paste_song` | Paste a chord sheet |
| `songs_empty_hint` (changed) | Create a song, paste a chord sheet, import songs, PDF or Word documents and zip archives, or add the demo songs. |
| `songs_paste_converted` | Converted from chords written over the lyrics. Check the title and the artist; the song opens in the editor next. |
| `songs_paste_chordpro` | Kept as the ChordPro it is written in. Check the title and the artist; the song opens in the editor next. |
| `songs_paste_no_chords` | No chords were found, so this is a song of lyrics only. Check the title and the artist; the song opens in the editor next. |
| `paste_clipboard_empty` | There is no text on the clipboard to paste. |
| `paste_only_links` | A link is not a song, and Campfire does not open pages. Copy the chords and the lyrics themselves. |
| `paste_too_large` | The text is longer than one import can take (8 MB). |
| `paste_refused_ios` | Campfire was not allowed to paste. Paste from Other Apps, under Campfire in the Settings app, decides this. |
| `paste_refused_web` | The browser did not let Campfire read the clipboard. Press Ctrl+V (⌘V on a Mac) on the song list instead. |
| `editor_paste_offer` | The pasted lines have chords written over the lyrics. |
| `editor_paste_convert` | Convert |

`paste_too_large` names the limit as the export's 24 MB message does, so it changes with
`ImportLimits.MAX_TEXT_FILE_SIZE`; the comment next to that constant gets the second string.

## 5. The two surfaces

### 5.1 The sheet (`dialogs/CampfireDialogs.kt`)

`NewSongDialog` gains `initialValues: Map<Field, String> = emptyMap()` and `note: String? = null`, and
`DialogType.NewSongFromText` renders it with them, `onCreate` going to `createSongFromText`. Everything else is New
song's own: `TextFieldBottomSheet`, Create in the header (and Ctrl / Cmd + S), the title required, the first field
focused as it is today. The note is the scrolling column's first item — set once, so it does not count against the
pinned height on a 360 × 640 dp phone, where the sheet stays what New song is now: the header and the fields scrolling
under it. Which note is decided by `isConverted` and `hasChords`. The sheet's values are `rememberSaveable` as New
song's are; the text is the `DialogType`'s, which lives in the view model through a rotation. A process the system ends
under the sheet loses it, as it loses any open sheet — nothing was written, and the clipboard still holds the text.

The song from the clipboard often has no artist and a title guessed from its first line. Once it is in the editor, the
lookup of `documentation/plans/song-details-lookup.md` is offered there the normal way; this plan adds nothing for it.

### 5.2 Messages (`Message`, rendered by `messages/Messages.kt`)

`ClipboardEmpty`, `PastedOnlyLinks`, `PastedTextTooLarge`, `ClipboardRefused` (the platform's string chosen by an
`expect val` beside `isDesktopPlatform`, the desktop and Android never sending it), and
`PastedChordSheet(fileName, start, pasted, converted)` with the `editor_paste_convert` action. All short, except the
last, which is long since it has an action.

### 5.3 The editor's offer (`screens/songEditor/PastedChordSheet.kt`)

- **`PasteWatcher`**, an `InputTransformation` on the editor's `BasicTextField` that changes nothing and records the
  last change that inserted two or more lines in one edit, as `(start, text)`. An `InputTransformation` sees only what
  the user put into the field — a paste, the keyboard, a drop — never the editor's own `TextFieldState.edit`s (a
  transposition, a sheet's rewrite, the respelling after a save), so those are never mistaken for a paste.
- **`PastedChordSheetOffer`**, a `LaunchedEffect` over the watcher: for each recorded paste, `ConvertChordSheetFragmentUseCase`
  on `Dispatchers.Default` through `viewModel.convertPastedLines(text)`; a non-null answer sends
  `Message.PastedChordSheet`. Not offered in a field longer than `LONG_DOCUMENT_LENGTH`, where `replaceAll` starts the
  undo history over and the conversion could not be undone.
- **Convert** emits an `EditorTextEdit(fileName)` whose edit replaces `pasted` with `converted` where it still stands at
  `start`, or failing that where it is the only occurrence of it, and otherwise changes nothing. `EditOnRequest` applies
  it as one step of the history (`replaceAll(isSelectionMapped = true)`), so the editor's Undo takes it back. The field
  is in the reader's notation and the fragment is bracketed as written, so the chords mean in the field what they meant
  when pasted.

## 6. Corner cases, and what each one does

| Case | Behaviour |
| --- | --- |
| Empty clipboard, or an image or a file on it | "There is no text on the clipboard to paste." |
| Only an address copied | "A link is not a song…"; nothing fetched, nothing written. |
| Text over 8 MB | Refused before anything is prepared, with its own message, rather than as an oversized file on the report screen. |
| Text that is ChordPro already | Passed through untouched; the sheet says it was kept as written. |
| Prose, a poem, lyrics without chords | One escaped song; the sheet says no chords were found. |
| A songbook (several titled starts, `{new_song}`) | The ordinary import: split, numbered, reported with Details. |
| A song the library already holds | Nothing written; the library's copy opened, the snackbar saying it was already there. |
| A title that names a library song with other text | Numbered (`_2`), as New song does; no question. |
| A pasted HTML table of chords | Android, iOS and the web read the plain text the page put beside it; the converter takes what columns it can. A page that offers no plain text gives nothing to paste. |
| iOS: Don't Allow | "Campfire was not allowed to paste…", naming the setting. |
| Web: permission refused | The message points at Ctrl / Cmd + V, which needs no permission. |
| Ctrl / Cmd + V with the search field focused | The field takes it; nothing else happens. |
| Ctrl / Cmd + V on another screen, or over a sheet | Not answered; the browser or the focused control does what it always does. |
| Text dropped on another screen, or over a sheet | Desktop: the drag is not accepted and the system animates it back. Web: the drop is ignored. |
| An import already running | The menu entry and the shortcut do nothing, as Import files does; a drop is queued like a dropped file. |
| Read only mode | No entry, no shortcut, no text drop. |
| Chords switched off | Unchanged: the conversion still writes the chords into the file, which is what the switch is about — showing, not storing. |
| The sheet closed | Nothing written. |
| The editor's offer, then more typing | Convert still finds the pasted lines where they were or as their only copy; otherwise it does nothing. |
| Sync, export, device backup | Nothing new: a written song is a library file like any other, and the clipboard is never stored. |
| Network | None. A `{meta: cover}` or `{meta: link}` already in a pasted ChordPro text is written like any other line, and the cover is fetched by the existing rules once the song is shown, as for any import. |

## 7. Order of work

Each step builds and tests green on its own, one commit each.

1. `SharedText` moved out of `AndroidFileImport.kt`, with `SharedTextTest`; Android's share unchanged.
2. `CreateSongUseCase`'s `text`, with its tests.
3. `ImportRequest.isNewSongFromText`, `newSongFromText`, `DialogType.NewSongFromText` and the sheet's note, the
   messages and their strings; reached from nowhere yet.
4. `ClipboardText` and its four actuals, the menu entry and the empty state, verified on the desktop first.
5. The desktop shortcut and text drop.
6. The web shortcut and text drop.
7. `convertFragment` with its tests, the use case, `PasteWatcher` with its test, and the editor's offer.
8. Docs: the root `CLAUDE.md` (the import paragraph of Conventions — "Documents are converted locally" — and the first
   paragraph, which says what reaches the network and stays true), `presentation/CLAUDE.md` (the shells' drops, the
   view model's import queue, the editor), `chordpro/CLAUDE.md`, `domain/api` and `domain/implementation`
   (`CreateSongUseCaseImpl`, the new use case), `app/android` (the helpers moved); the README's feature list.

## 8. Checks owed by hand

- **Android**: copy a chord sheet from Chrome and from a notes app, tap the entry; the toast; a copied image; a copied
  link; the sheet on the 360 × 640 dp screen in Hungarian with the keyboard up; Create, then the editor.
- **iOS**: the "Allow Paste" prompt on 16+ and the banner on 15; Don't Allow; the setting at Deny and at Allow; Safari
  as the source.
- **Desktop**: Ctrl+V on Windows and Linux, ⌘V on the Mac, with and without the search field focused; text dragged
  from Firefox, Chrome and a text editor, and a file dragged from Finder (which offers a string too) still importing
  as a file; the Mac App Store build's sandbox, which allows the pasteboard.
- **Web**: Chrome, Firefox and Safari (iOS included): the button's prompt or callout and a refusal; Ctrl / Cmd + V on
  the list with nothing focused, and into the search field; a selection dragged from another tab.
- **The converter on real pages**: three or four chord sites' copied text, with chords over lyrics and with inline
  chords, and a page of lyrics only — and what the sheet guessed for each title.
- **The editor's offer**: paste a chord sheet into a new song, Convert, Undo; paste ChordPro and see no offer; paste,
  type, Convert.

## 9. Not in this plan

- Ctrl / Cmd + V on the Songs screen with a hardware keyboard on Android, ChromeOS, the iPad and the web on a tablet.
- The paste entry on the Setlists screen; a setlist from a pasted list of titles.
- Reading the HTML a page puts on the clipboard (its `<b>` chords, its tables), where the plain text loses positions.
- An editor overflow action that converts the whole text, or a selection, after the fact.
- The iOS share extension and the Android share audit (`documentation/plans/share-extension.md`), which call
  `newSongFromText`; and the song details lookup (`documentation/plans/song-details-lookup.md`).
- Following a copied link to the page it names, which would be the app reaching the network on its own.
