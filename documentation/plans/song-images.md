<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Plan: images in songs

**Status:** not started. Written against the code on 2026-11-11 (b4fea6ab2). `ui/` is
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`.

Campfire learns ChordPro's [`{image}`](https://www.chordpro.org/chordpro/directives-image/) directive: a song can show
pictures kept in the library, beside its text or instead of it. The case that asked for it: a chart that only exists
as a PDF or on someone's paper still belongs in the setlist, as a song whose header names it and whose body is a photo
or an exported page. Camera capture is out of scope; images come from the regular file picker, a drop, a share, or an
import.

## Decisions

- **Images are library files, not embedded data.** ChordPro documents no data URI or base64 form, and a picture inside
  the text would bloat the editor, the sync of every edit, and every diff. Images live in `library/files/`, a third
  `LibraryFileKind` beside songs and setlists, and the song names one with a bare file name: `{image: src=name.jpg}`,
  the form the spec calls portable (it looks a bare name up beside the song and in an `images/` folder). The folder
  is named for files, not images, so that other files kept with a song (a PDF chart opened in the system viewer,
  say) could later use the same storage, sync, and pruning without renaming a folder every user's cloud already
  holds. Archives still put images in `images/`, where ChordPro looks.
- **An image's name is its content.** Campfire names an image it writes by the first 16 hex digits of its SHA-256 plus
  its type's extension (`3f9a…c1.jpg`); a different file that would take an existing name takes more digits. So an
  image is never edited, only added or removed, and the same picture in two songs or on two devices is one file:
  - sync never meets a conflict on an image, and an image never needs `_2`;
  - renaming a song never touches its images;
  - an import rewrites only the `src` of each image directive it brings in, to the name it stored the picture under.
    That is the one change an import makes to a `.cho` file's text, said in the review's row for the song.
- **An image lives while a song names it.** Once the library has been read whole, images no song names are deleted,
  as `covers/` already is (`CoverArtRepositoryImpl`). Deleting the last song that shows a picture deletes the picture;
  there is no image management screen.
- **A reference beats a deletion**, the image twin of "an edit beats a deletion": the sync planner never deletes an
  image a local song names, and uploads it again instead. Without this, a device that dropped its last song naming a
  picture would delete it under another device that just started using it.
- **The library only holds images a saved song names.** A picture picked in the editor waits in
  `preferences/editor-draft-images/` (out of backup and sync, like the draft) and moves into the library on Save; a
  cancelled draft deletes it. "Nothing from outside is written before Save or Import" holds.
- **Formats: PNG, JPEG, GIF (first frame), and WebP**, what Coil (already shipped, for covers) decodes on all four
  platforms. HEIC and SVG are refused when picked, with a sentence saying so; an imported song naming one keeps the
  directive and shows the missing-image placeholder. One image is at most 8 MiB, the sync cap
  (`SyncEngine.MAXIMUM_FILE_SIZE`); nothing is re-encoded, since that would need an encoder per platform.
- **Layout knows an image's size before it is decoded.** A pure-Kotlin reader takes width and height from the file's
  header (PNG `IHDR`, JPEG `SOFn`, GIF, WebP `VP8`/`VP8L`/`VP8X`, with JPEG's EXIF orientation), so the first frame
  reserves the right room and nothing moves when the picture arrives (the UI rule on data arriving).
- **An image is never cut.** Fitted to the column's width, and scaled down to one column's height when taller, so it
  is always one pedal press (`pedal-only` reading); `width`, `height`, and `scale` narrow it further, never widen it
  past the column. A tap opens it full screen, zoomable, for the small print a phone cannot show.
- **Kept, not drawn:** `x`, `y`, `spread`, `border`, `trbl`, `href`, and non-`float` anchors (which would put the image
  on the page background, behind the text) are kept in the file and read as a plain float block. The inline
  `<img …/>` markup stays hidden like other unknown markup. `id=` assets are honoured: `{image: id=a src=…}` defines,
  `{image: id=a}` draws.
- **Missing image, still a song.** A directive naming a picture the library lacks draws a placeholder the picture's
  size would have taken (or a short one), saying "Image not found: name.jpg" with an **Add image** button that picks
  the file and stores it under that directive (rewriting its `src`): an explicit write, like Save. The song keeps
  its title, artist, tags, and text, so it is listed, searched, and in its setlists as before.
- **Older versions pass images through.** `LibraryFileKind.matches()` already leaves unknown cloud folders alone, and
  the parser drops `{image}` today without touching the text, so a device on v5.3 keeps every image directive and
  never deletes `files/`. No stored document gains a field; the sync index only gains `files/<name>` keys (step 4
  checks the v5.3.1 index reader ignores a key of an unknown kind, and bumps nothing if it does).

## Where it hooks in today

- `:libs:chordpro`: `ChordProParser` (the directive `when`, ~lines 245–275) sends `image` to `metadata.consume`, so it is
  dropped; `ChordProBlock` (`model/ChordProBlock.kt`) is where a block type goes, `{new_page}` →
  `ChordProBlock.Break` the precedent; `ChordProSerializer` is the round-trip oracle; new public model classes go in
  `gradle/compose-stability.conf`.
- Storage: `FileStorage.StorageDirectory` (`SONGS`, `SETLISTS`, `PREFERENCES`, `COVERS`) already has
  `readBytes` / `writeBytes`, OPFS included; `LibraryFileLocalSource` is bytes-based.
- Sync: `LibraryFileKind` (`data/model/.../library/LibraryFile.kt`), `DropboxSyncProvider.remotePath`
  (`/<kind>/<name>`), the byte-based `SyncFolder`, `SyncIndexDocument` (keys `songs/<name>`), and the planner.
- Export and shared links: `ArchiveLocalSourceImpl.pack` over the STORED-only `ZipWriter`; `SharedCopySync` packs the
  library's raw files and skips a rebuild by `sharedCopyFingerprint`.
- Import: `ArchiveLocalSourceImpl.unpack` skips every `LibraryFiles.BINARY_EXTENSIONS` entry unread and flattens
  `songs/`; `ImportLimits` (8 MiB text, 24 MiB archive budget).
- Print: `ui/print/PrintRenderer.kt` draws each page into an `ImageBitmap`, `PrintPdfWriter` writes it as a 4-bit gray
  raster with a text layer.
- Song view: `ui/songView` measures blocks by intrinsic height per width; ABC notation (`SongNotationBlock.kt`) is the
  precedent for a block whose content arrives later.
- Covers: Coil from bytes (`ui/components/coverArt`), `CoverArtRepositoryImpl`'s pruning,
  `CoverArtLocalSourceImpl`.
- Editor: `ui/screens/songEditor/toolbar` (a button per directive the app shows), `ui/platform/FilePicker.kt`,
  `EditorDraftLocalSourceImpl`.
- Backup: `app/android/src/main/res/xml/data_extraction_rules.xml` and `full_backup_content.xml` (allow-lists that
  include all of `library/`).

## Steps

### 0. Prerequisite: the streaming zip writer

[streaming-zip-writer.md](streaming-zip-writer.md). Exports and shared copies build their zip in memory today, which is fine for text and is not
for a library with a hundred photos, the web build above all. Images in archives (steps 6 and 7) wait for it; steps
1–5 do not.

### 1. The directive (`:libs:chordpro`, pure)

`ChordProBlock.Image(src, id, width, height, scale, align, attributes)`, `width` and `height` as points or a percent,
the attributes it does not draw kept for the serializer. Parsed in a section and outside one (a song that is only a
picture has no section), the short form `{image: "x.png"}` included; an `id=` definition with no other attribute is
kept and draws nothing. An edit helper beside `ChordProCoverArt` rewrites one directive's `src` and leaves every other
byte as it was. Tests: every attribute form and quoting, round-trip through `ChordProSerializer`, the `src` rewrite.

### 2. Image headers (`data/formats/image/`, pure)

`ImageInfo.of(bytes): ImageInfo?` with format, width, height, and EXIF orientation (width and height swapped for 90°
and 270°), reading only the header and never more than the file's first 64 KiB; anything else is null. Tests: one
small file per format and orientation, truncated and malformed headers.

### 3. The library's images (storage, repository)

- `StorageDirectory.FILES` → `library/files`, `LibraryFileKind.FILE("files")`.
- `ImageRepository`: `add(bytes): name?` (refuses unknown formats and >8 MiB, names by content), `read(name)`,
  `info(name)` (header read once, cached), and pruning of unreferenced images after a full library read, skipping
  every name the open draft's text holds.
- `LibraryFiles`: the image name rule (hex + extension, never normalized), and image extensions out of
  `BINARY_EXTENSIONS`' "never read" use once step 6 reads them.
- Android backup: `library/files` **excluded** in both rules files. Android's Auto Backup stops backing up an app
  whose data passes 25 MB, so photos would cost the user their songs' backup; a restored library shows placeholders
  until sync or a re-import brings the pictures back. iOS keeps them in its backup (no such cap).
- Tests: naming, a collision taking more digits, pruning that keeps referenced and drafted names.

### 4. Sync

- The planner syncs `files/` like the other kinds, by content hash. Since an image never changes, both sides
  creating one name is the same file; a "modified" image on either side is treated as a new file plus a deletion.
- A reference beats a deletion (Decisions); the run that would delete most of the library counts songs and setlists
  only, since images follow them.
- `SharedCopySync` and the index: `files/<name>` keys; check against the v5.3.1 tag that its index reader ignores
  them; a test reading the current index fixture unchanged.
- Tests: the planner cases (added on one side, both, deleted under a reference, deleted unreferenced), and the live
  recipe (`sync-live-testing`) once by hand with two devices.

### 5. Showing it (`ui/songView`, `ui/songView/grid`)

- An image unit sized from `ImageInfo` from the first frame, fitted and capped as Decisions say, decoded by Coil from
  the repository's bytes at the size it is drawn (no full-resolution bitmap of a 12 MP photo).
- The placeholder for a missing or refused image, with **Add image** (hidden in read only mode).
- Full-screen viewer on tap: zoom and pan, closed by back or the close button.
- Lyrics-only mode keeps images (they may be the only content). The audience screen, when it lands, shows them too.
- UI tests (`tools/screenshots`): a song that is only an image, an image between sections, a missing image, and a
  portrait photo on a phone page.

### 6. Import

- `unpack` reads an image entry when a song in the same archive names it: looked up beside the song, then in an
  `images/` folder, then by name anywhere in the archive. Images no song names stay skipped and reported as today.
  Images count against their own archive budget (64 MiB, each ≤ 8 MiB), apart from the 24 MiB text budget.
- The review and preview show the song's images; applying stores them before the songs and rewrites each `src`.
- A lone image file (opened with, dropped, shared, or picked to import) is a new song, the way a pasted chord sheet is:
  the editor opens on `{title: <file name without extension>}` and `{image: src=…}` with the picture waiting as a draft
  image; nothing is written before Save. Registration: PNG and JPEG offered (never default) on Android, iOS (app and
  share extension), desktop, and web.
- Tests: an archive with images beside the song, in `images/`, and missing; a lone image; budgets.

### 7. Export and shared links (after step 0)

- A song naming an image exports as a zip (`song.cho` plus `images/`), so the file opens the same in ChordPro or
  another Campfire; a song without one stays a lone `.cho`. Setlist, selection, and library zips carry `images/` for
  the songs they hold.
- `SharedCopySync` packs the same images; the fingerprint includes their names (enough, since a name is its content).
- Tests: each export shape's entries, a shared copy rebuilt when a song gains an image and not when it is re-saved
  unchanged.

### 8. Print

`PrintRenderer` draws the image into the page like any other block, fitted the same way. Pages holding an image are
written as 8-bit gray, since the 4-bit gray of text pages posterizes a photo; colour would mean an RGB page raster for
every page and is left out. PDF golden: a page with a photo and a page with a placeholder (which prints as its sentence).

### 9. Editor

- Toolbar **Image** button: picks a file through `FilePicker`, refuses with a sentence what step 3 refuses, keeps the
  picture as a draft image, and inserts `{image: src=…}` on its own line at the caret.
- Draft images survive process death beside `editor-draft.json`, and are deleted on Save (moved), on discard, and on
  launch when no draft names them.

### 10. Documentation

- Root `CLAUDE.md`: `library/files/` in the library layout; the privacy section's backup line (images excluded on
  Android); a product rule (images are library files named by content, kept while a song names them); sync's
  invariants (a reference beats a deletion).
- `CLAUDE.md` of `chordpro`, `data/formats`, storage, sync, `ui/songView`, `ui/print`, the editor toolbar, and import.
- At release time: the website's import and editor pages, the store listings, and what's new.

## Size and risk

About fifteen new small files and changes in every layer, most of them pure and tested in `commonTest`. The risks:
memory (decoding large photos and packing archives, handled by drawing at display size and step 0), sync deleting a
picture still in use (the reference rule, tested in the planner and once live), and Android backup size (images
excluded). Not in this plan: the camera, PDF pages as images (needs a PDF renderer on every platform), SVG images, and
colour print.
