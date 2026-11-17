<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: song attachments

**Status:** not started. Written against the code on 2026-11-17 (abb3b3f95). `ui/` is
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`, `model/` is
`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/`.

A song can carry files of any common type (a PDF of the sheet music, a reference recording, a short video of a
fingering, a Guitar Pro file), each at most 50 MB. They are bundled with the song the way its pictures are: library
files named by their content, kept while a song names them, synced, exported, and included in shared links. They are
listed in the song info sheet under Links, added there or from the editor, and opened with the app the operating system
picks for them. Settings → Library also starts showing what the library's size is made of.

## Decisions

- **The directive is a meta item, like links**: `{meta: attachment 3fa9c2e81b7d4f06.pdf Rehearsal take.pdf}`. The
  first token is the library file; the rest is the name the user sees, kept from the picked file (extension
  included, since the opening app shows it too). The name can be edited, and a missing name reads as the type
  ("PDF document"). Other ChordPro programs ignore an unknown meta item, so the file stays readable everywhere.
- **Library files, not a new folder.** Attachments live in `library/files/<sha256 hex>.<ext>` beside the pictures,
  named by `LibraryImages.contentNames` (16 hex digits, growing on a collision). `LibraryFileKind.FILE` already says the
  folder was named "files" for this. Identical bytes are one file, whether two songs attach it or one shows it as a
  picture and another attaches it.
- **One "named by" for every file a song keeps.** Today pruning, sync's local deletions, its listing, its planner,
  export, the shared copy, and the import each compute "referenced" from `{image}` sources alone. An attachment
  missed by any one of them is a deleted file. So `:libs:chordpro` gets one public `libraryFilesNamedBy(text)` (image
  sources plus attachment sources), `ChordProSummary`/`Song` get the union as well, and every one of those callers moves
  to it in the same change that first lets an attachment be stored. Nothing image-specific about *keeping* a file
  stays behind.
- **Extensions are an allow-list**, `LibraryFiles.ATTACHMENT_EXTENSIONS`: documents (`pdf txt md rtf doc docx odt xls
  xlsx ods ppt pptx odp`), audio (`mp3 m4a aac wav flac ogg opus aif aiff mid midi`), video (`mp4 m4v mov webm mkv`),
  pictures (`png jpg jpeg gif webp heic tif tiff`, kept at full size and never re-encoded, unlike a song's picture), and
  music formats (`gp gp3 gp4 gp5 gpx mscz musicxml mxl abc`). Nothing that runs (`exe apk sh js html app dmg …`):
  a shared link or a synced song from someone else must not hand over something that executes on a tap. Anything else
  is refused with a snackbar naming the types taken. The extension is the picked file's, lowercased (`jpeg` stays
  `jpeg`); a name with none is refused.
- **50 MB per file** (`ImportLimits.MAX_ATTACHMENT_FILE_SIZE`). Pictures keep their 8 MB. A larger file is refused with
  a snackbar suggesting a link instead. Files stay whole `ByteArray`s in memory; streaming is not part of this plan.
- **Opening copies the file under its name** into `exports/` (emptied on launch) and hands that copy to the platform,
  so the receiving app shows "Rehearsal take.pdf", not a hash, and can never write back into the library.
- **Shared links include attachments**, within a new budget for the whole copy (below). What does not fit is left
  out of the copy and the share sheet says so, rather than refusing the share.
- **Kept out of iOS device backup** (`keepOutOfDeviceBackup` per attachment file), as Android already leaves out all of
  `library/files/`. Pictures stay in the iOS backup as today. Sync is how attachments move between devices.
- **Behind a Features switch**, "Attachments", on by default. Like every switch it only hides (the sheet's section,
  the editor's button); sync, export, and import still carry the files.
- **No print, no song view.** Attachments appear in the song info sheet (and its editor preview card) only; the PDF and
  the song view do not mention them.
- **Not opened from an import preview.** The review lists how many attachments a song brings and which could not be read;
  they open once imported.

## Steps

Each step compiles, is tested where it has logic, and is committed on its own.

### 1. Settings → Library shows what the size is made of

Independent of the rest and useful now: the library's size today is `songs.sumOf { size } + setlists.sumOf { size }`
(`ui/state/LibraryState.kt`), so pictures are not even counted.

- `ImageRepository` (today) gains a sizes flow: `library/files/` listed through `FileStorage.list` (sizes without reads),
  split by `LibraryFiles` into pictures and, from step 2 on, attachments; refreshed on its `changes`.
- `LibrarySummary` gets `songsSize`, `setlistsSize`, `picturesSize`, and `attachmentsSize`; `size` becomes their sum.
- `LibrarySections.kt`: the library row keeps its total, and under it one line per non-zero part ("Songs 1.2 MB ·
  Pictures 34 MB · Attachments 210 MB"), or a thin segmented bar with the same legend if it reads better at 360 dp. The
  cover cache row stays as it is. Changes animate (`AnimatedSettingsRow`).
- Strings in every language, a plural-free label per part.

### 2. The model: directive, names, and limits

- `:libs:chordpro`: `ChordProMetaItems.ATTACHMENT_NAME = "attachment"`; `ChordProMetadata.attachments:
  List<ChordProAttachment>` (`source`, `name`); `edit/ChordProAttachments.kt` with `setAttachments` (as
  `ChordProLinks.setLinks`, keeping the rest of the file) and `withAttachmentSourcesRewritten`; `libraryFilesNamedBy`.
  `ChordProSummary` gains `attachmentSources`; `SongMappers` maps it to `Song.attachmentNames`. Tests beside the link
  ones: parse, write, rewrite, names with spaces and quotes, a missing name, and an `https` source ignored (attachments
  are never links).
- `model/library/LibraryFiles.kt`: `ATTACHMENT_EXTENSIONS`, `isLibraryAttachmentName`, `isLibraryFileName` (either), and
  `LibraryFileKind.FILE` matching `isLibraryFileName`. `isImageNamedFor` generalises to `isLibraryFileNamedFor`.
- `model/importing/ImportLimits.kt`: `MAX_ATTACHMENT_FILE_SIZE = 50 MiB`.

### 3. Storage, keeping, and pruning

One keeper for every library file: `ImageRepository` and `ImageLocalSource` keep their names for now (their KDoc says
they hold attachments too); a rename is churn this plan does not need.

- `ImageLocalSourceImpl` lists, reads, and deletes `isLibraryFileName` names; writing an attachment to `LIBRARY` calls
  `keepOutOfDeviceBackup`.
- `ImageRepository` gains `addDraftAttachment(bytes, extension)` (no header check; the allow-list, the size, and the
  content name) and `LibraryImageWriter.addAttachment`. `moveDraftImages` already moves whatever names it is given.
- `ImageRepositoryImpl.prune` and the draft shelf's tidying count `libraryFilesNamedBy`; the editor's held names cover
  attachments the same way.
- Use cases: `AddDraftAttachmentUseCase`, `SaveSongAttachmentsUseCase` (the song info sheet's write: stores new files
  and sets the song's attachments in one `withImagesKept`, as `SaveSongImageUseCaseImpl` does), and
  `SaveWithDraftImagesUseCaseImpl` moving `libraryFilesNamedBy(text)`.
- Tests: pruning keeps a file only an attachment names; Save moves a draft attachment; a refused extension and size.

### 4. Opening an attachment

A platform `FileOpener` in `ui/platform` (beside `FilePicker`), `open(name: String, file: ExportedFile)`, after the
library file is copied into `exports/` under its display name (made safe and unique there by `FileNames`).

- **Android**: `ACTION_VIEW` with a `FileProvider` URI, the MIME type from the extension, and
  `FLAG_GRANT_READ_URI_PERMISSION`; the copy goes to `cacheDir/shared/` (the one path `file_paths.xml` already exposes)
  like `toSharedUri`. No app for the type is a snackbar.
- **iOS**: `QLPreviewController` (PDFs, audio, video, documents, pictures), whose share button offers every other app.
- **Desktop**: `Desktop.open`, falling back to `open` / `xdg-open` / `cmd /c start` as `SystemBrowser.desktop.kt` does
  for URLs. Check that the Mac App Store sandbox lets it open a file in the container.
- **Web**: a blob URL opened in a new tab for what browsers show (PDF, audio, video, pictures, text), a download for the
  rest.

### 5. The song info sheet

- `ui/songInfo/SongInfoGroups.kt`: `SongInfoGroupKind.ATTACHMENTS` right after `LINKS`. `SongInfoBody` draws one row per
  attachment: a type icon (document, audio, video, picture, other), the name, and the size; a tap opens it. A file not
  on this device (sync not done yet, a song copied in by hand) is drawn dimmed with "Not on this device", not opened.
- Editing (`SongInfoEditing.onEditAttachments`): an Attachments bottom sheet like `SongLinksDialog`. Add file sits in
  the header (it picks through step 6's picker), each row has its name field and a remove button, Save writes through
  `SaveSongAttachmentsUseCase`, and the close button cancels (picked files stay on the draft shelf until then, and are
  pruned with it). From the editor's preview card, the same sheet edits the draft text, as links do.
- Read only mode and import previews show the rows without editing.
- `songInfo/CLAUDE.md`: the attachments tile and its row height alongside the links tile rule.

### 6. Picking a file and the editor's button

- `FilePicker.pickAttachment(): PickedAttachment?` (name, bytes) on every platform, reading any type up to
  `MAX_ATTACHMENT_FILE_SIZE` (the import's `ImportBudget` would hand an unknown type back unread): Android
  `OpenDocument` with `*/*`, iOS the document picker with `UTType.item`, desktop the file dialog, and the web an
  `<input type=file>`. The allow-list and the size are checked before the bytes are read where the platform says the
  size up front.
- `ui/screens/songEditor/toolbar/EditorToolbar.kt`: an Attach button right after Link in the [Tag, Language, Link]
  group. It picks a file, keeps it on the draft shelf (`EditorImages` grows an `attach` beside `keep`), and inserts
  `{meta: attachment <file> <name>}` where Link inserts its line. A refusal is a snackbar. Large files show the
  `PictureWork` work bar while they are read and hashed.
- Dropping or sharing a file onto the app keeps importing it as today (a PDF is still converted into a song).

### 7. Sync

- `SyncEngine.MAXIMUM_FILE_SIZE` becomes per name: `MAX_ATTACHMENT_FILE_SIZE` for an attachment, 8 MB otherwise, in the
  local listing and in `downloadWithinLimit`; `DropboxDownloads.MAXIMUM_DOWNLOAD_SIZE` takes the caller's limit.
  Uploads stay one `/2/files/upload` call (Dropbox takes up to 150 MB per call); the web's upload timeout already
  scales with size.
- `ImageTransfers.deleteLocally`, `LocalListingReader`'s references, and `SyncPlanner.withImagesKept` use
  `libraryFilesNamedBy`. `isIntact` uses `isLibraryFileNamedFor`, so a copy that is not what its name says still never
  travels.
- Web: `OpfsFileStorage.readBytes`/`writeBytes` go through a Latin-1 string (two extra copies, one twice the size).
  Measure a 50 MB attachment's sync on the web; if it stalls or runs out of memory, copy through the `ArrayBuffer` in
  chunks instead.
- Tests in `data/sync/implementation`: an attachment uploaded, downloaded, kept against a remote deletion while named,
  over the limit reported, and a corrupt copy refused.

### 8. Export, import, and shared links

- **Export** (`ExportedImages.kt`): every library file a written song names goes under `images/` as pictures do today (the
  entry name is the content name, so an importer needs no new folder). Entries stay one in memory at a time.
  `ExportedFile.unpackedImageSize` counts attachments, so `isTooLargeToImport` warns when a library export can no
  longer be imported back in one piece.
- **Import** (`ArchiveSongImages`, `IncomingImages`): attachments a song names are read from the archive, each up to
  `MAX_ATTACHMENT_FILE_SIZE`, within a new `MAX_IMPORT_ATTACHMENTS_SIZE` (200 MiB); `MAX_ARCHIVE_SIZE` grows to match
  (whole archives are still read into memory, so check the smallest supported phone survives it). Missing, too large,
  and refused ones are `ImportedImage.Problem`s worded for attachments in the review. The song keeps its directive
  either way, so a later sync can still bring the file.
- **Shared links** (`SharedCopySync.kt`): the copy includes the attachments its songs name, under `images/`. Its budget
  becomes 24 MiB for songs and pictures (as today) plus 100 MiB for attachments, filled in the order the songs name
  them; what does not fit is left out, and the export screen's share section and Settings → Sharing say how many were
  left out. The fingerprint includes attachment names, so adding one republishes the copy. Opening a link reads the zip
  as any archive, within the import limits above.

### 9. The Features switch

`UserPreferences.isAttachmentsEnabled = true`, its `UserPreferencesDocument` field and mappers,
`PreferencesController.setAttachmentsEnabled`, and a `SwitchListItem` in `FeaturesSections.kt`, following "Pictures from
links" (5f5765bd5). Off hides the sheet's section and the editor's button. Extend `UpgradeFrom540Test` for the new
field (the last tag is 5.4.0).

### 10. Documentation

- Root `CLAUDE.md`: the library layout line for `library/files/` (pictures and attachments), the privacy list's Shared
  links and system backup lines (iOS keeps attachments out), and a product rule for attachments pointing at
  `ui/songInfo`, `ui/platform`, `data/repository/implementation`, and `chordpro`.
- The nearest `CLAUDE.md` of every area touched: `ui/songInfo`, `ui/pictures` (the draft shelf holds attachments too),
  `ui/screens/songEditor`, `ui/screens/settings`, `ui/platform`, `data/model/library`, `data/sync/implementation`,
  `domain/implementation/importing` and `export`.
- Store listings and the website's privacy policy are release-time work.
- Delete this plan once the last step lands.

## Not in this plan

- **Streaming files end to end** (Dropbox upload sessions, downloads into a sink, archives read from a file). Without it,
  a library export holding more than `MAX_ARCHIVE_SIZE` cannot be imported back whole; `isTooLargeToImport` says so,
  and sync is how such a library moves. Worth its own plan if people hit it.
- A Wi-Fi-only or on-demand download of attachments during sync.
