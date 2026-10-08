<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Song links — implementation plan

Written 2026-10-07 against `a0c6b7789`. A song, or a setlist with its songs, shared as a link or as a QR code that
holds the whole file: compressed into the part of the address after the `#`, which a browser never sends to a server
and a messenger's link preview never sees. Opening the link on a device with Campfire imports it through the ordinary
import. There is no server, no upload and no new request: the page the link opens is a static file of the website.

## 1. What is being built

1. **A third format on the export screen**, **Link**, after PDF and ChordPro (or Zip, for a setlist): the preview is
   the QR code with the link under it, the floating action button is **Copy link**, the app bar's Share on Android and
   iOS shares the link as text, and **Save QR code** among the options writes it as a PNG.
2. **The link format**: `https://campfire-songbook.com/s/#song=1.<payload>` or `#setlist=1.<payload>`, the payload a
   zlib stream of the `.cho` file as the library holds it, or of the setlist's Zip exactly as the Zip format writes it,
   in uppercase base32.
3. **A landing page at `/s/`** in the `campfire-website` repository, seen only where the app is not installed or the
   system did not hand the link over: it names what was shared, and offers the web app, the file itself and the
   stores.
4. **Opening a link** on Android (a verified App Link for `/s/`), on iOS (a Universal Link for `/s/`) and in the web
   app (`/app/#song=…`, where the landing page sends it) imports it the way an "Open with" does: duplicates
   disregarded, collisions asked about, the one song opened, everything else told by the snackbar or the report
   screen.
5. **Nothing on the desktop opens a link**: there the link opens the browser, and the landing page offers the
   ChordPro file, which the desktop app opens as any other. Copy link and Save QR code work there like everywhere.

### What is already there, and what is not

- The export screen (`ui/screens/export/ExportScreen.kt`) already has the format as its first option (`FormatChoice`, a
  `SegmentedChoice` over `PrintSettings.Format`), a preview that crossfades to what the library's own files write
  (`FilesPreview`, `ZipContents`), a setlist's song ticks shared by both formats (`chosenSongFileNames`), Save as its
  floating action button (`SaveButton`) and Share in its app bar on the phones (`PrintTopAppBar`, `canShare`).
- The bytes a link carries already exist: `ExportSongsUseCase` hands a song out as the `.cho` file it is, and
  `ExportSetlistUseCase` a setlist as a stored Zip of its manifest and the chosen songs.
- The way in already exists: `CampfireViewModel.importFiles(files)` is what every "Open with", share and drop goes
  through, with `shouldOpenSong`, and an archive goes through `ArchiveLocalSource.unpack` with every limit an untrusted
  zip needs.
- **The two halves of zlib are in two modules that cannot see each other.** `PrintDeflater` (fixed Huffman codes,
  zlib framing, its Adler-32 a private function) is `internal` to `:presentation`'s `ui/print/`, and the `Inflater`
  (raw DEFLATE after `puff.c`) and `Crc32` are `internal` to `:data:source:local:implementation`'s `zip/`. A link has
  to be written where the export screen is and read where the import is, and both have to be tested together.
- **No QR encoder, no PNG writer and no clipboard** anywhere in the app. `gradle/libs.versions.toml` has no imaging or
  barcode library, and nothing presentation draws comes from one.
- The website already ships `.nojekyll`, so a `.well-known/` folder is published as it is, and its `404.html` keeps
  the fragment when it sends a deep `/app/…` address on to the app.
- Read only mode already imports what the system hands over ("not a tap that can happen by accident",
  `presentation/CLAUDE.md`), so an opened link needs no rule of its own there.

### Defaults this plan assumes — veto any of them before the work starts

1. **Link is a format of the export screen**, for songs and setlists alike, remembered with the others in
   `PrintSettings.format`. No new menu entry: "Export song" and "Export setlist" stay the one way out.
2. **The link points at a small landing page, `/s/`, rather than at the web app.** App Links and Universal Links then
   claim `/s/` alone, so a phone with Campfire installed still opens the web app in its browser, and a person without
   the app downloads a page of a few kilobytes rather than sixteen megabytes before deciding anything. The web app reads
   the same fragment at `/app/#…`, so vetoing the page is changing one constant.
3. **Uppercase base32, not base64url.** Base32 (`A`–`Z`, `2`–`7`) is inside a QR code's alphanumeric mode, which packs
   a character into 5.5 bits rather than 8: a code holds about 17% more song. The text of a link is 20% longer for it,
   which costs nothing below the caps of default 5.
4. **A setlist link is the setlist's Zip**, stored as the Zip format writes it and compressed as one stream, so every
   rule of a setlist archive — the manifest naming only the ticked songs, a missing song still named — is the Zip's,
   and the import reads it as one. No container of its own.
5. **Caps**: a QR code up to version 30 (137 × 137 modules) at error correction level L, which is about 1.5 KB of
   compressed text — a song of about 3 KB of ChordPro, which is most songs; a link up to 32,768 characters, about
   20 KB compressed, a setlist of about a dozen songs; and above 4,096 characters (Telegram's message length) a line
   saying that some messaging apps cut links that long. Constants, to be settled by hand (§9).
6. **Opening a link imports at once**, with no question first, in the native apps and in the web app alike — the
   same as an "Open with". The landing page is the question for anybody who might not want the song where the link
   would put it.
7. **The fixed Huffman codes of today's deflater**, unchanged. Dynamic codes would make a fifth more fit and can come
   later with no change to the format, since every reader inflates both.
8. **An own QR encoder and PNG writer**, pure Kotlin in `:presentation`, written from ISO/IEC 18004, with ZXing as a
   `desktopTest`-only dependency that decodes what the encoder draws. Nothing new is shipped.
9. **A new dependency-free module, `:zlib`**, which the deflater, the inflater, Adler-32 and CRC-32 move into, used by
   `:presentation` (the PDF, the PNG), `:data:source:local:implementation` (the zip reader, the PDF reader) and
   `:domain:implementation` (the link).
10. **A song that declares no `{title}` gets one in its link**, the title the library shows for it, since the name it
    is filed under — which titles it here — does not travel. Every other byte is the file's.
11. **The debug build is not in `assetlinks.json`**: the debug keystore is public, so listing it would let any app
    signed with it claim the links. Debug builds are tested with `adb` (§9).
12. **A second web tab hands its link to the tab that owns the library** over a `BroadcastChannel`, rather than
    asking the user to close one of them.

## 2. Modules

```
zlib                             dependency-free DEFLATE and zlib: the deflater, the inflater, Adler-32 and CRC-32.
                                 Depends on nothing; used by :presentation, :data:source:local:implementation and
                                 :domain:implementation
```

`campfire-library`, `commonTest` with `kotlin("test")` like `:chordpro`, no Koin (stateless objects, reached directly
by the modules that use them, as `:chordpro`'s are). `settings.gradle.kts` includes it.

### 2.1 `:zlib` (`com.pandulapeter.campfire.zlib`)

- `Deflater` — `PrintDeflater` moved and renamed, unchanged inside: the reused hash tables, one fixed-code block,
  lazy matching, the yield every 256 KB. It writes into a `ByteOutput` (an interface with `byte(Int)` and
  `bytes(ByteArray, Int, Int)`), which `PrintBytes` in `PrintPdfWriter.kt` implements, so the PDF writer changes by one
  import. A second entry point, `zlibOf(data: ByteArray): ByteArray`, is what the link and the PNG use.
- `Inflater` — moved from `zip/Inflater.kt`, with a `maxSize: Int` parameter next to `expectedSize` (today the limit
  is either the declared size or `MAX_ENTRY_SIZE`), and throwing `ZlibException`. `inflateZlib(source, maxSize)` checks
  the two header bytes (method 8, a window of at most 32 KB, `(CMF·256 + FLG) % 31 == 0`, no preset dictionary), inflates
  and compares the Adler-32 at the end; anything after it is an error.
- `Adler32`, out of `PrintDeflater`'s companion, and `Crc32`, moved from `zip/Crc32.kt`.
- The callers of `Inflater.inflate` (`ZipReader.readData`, `PdfFilters`' FlateDecode) catch `ZlibException` and throw
  what they threw before, so nothing above them changes. `Inflater.MAX_ENTRY_SIZE` stays where `PdfFilters` reads it.

## 3. The link (`:domain`, `:data:model`, all of it tested)

### 3.1 The format

```
https://campfire-songbook.com/s/#song=1.KBQXG4TT…
                                 ^^^^ ^ ^^^^^^^^^
                                 kind | payload: RFC 4648 base32, uppercase, no padding,
                                  version   of a zlib stream (RFC 1950)
```

- **Kinds**: `song`, the UTF-8 text of one `.cho` file; `setlist`, a zip archive whose bytes start like one
  (`LibraryFiles.isZipArchive`).
- **Version 1** is exactly that. A later version is a new number; a reader that does not know it says so (§5.3)
  rather than calling the link broken.
- **The fragment grammar is strict**: `^(song|setlist)=([1-9][0-9]{0,2})\.([A-Za-z2-7]+)$`, nothing before it and
  nothing after it. Lowercase is read as uppercase, which RFC 4648 allows and the checksum makes safe; the leftover
  bits at the end must be fewer than five and zero.
- **Deterministic**: the same file always makes the same link, so a link opened twice is a duplicate the import
  disregards.

### 3.2 `:data:model`

`domain/ShareLinks.kt`, vocabulary like `LibraryFiles`:

- `ShareLinks.LANDING_URL = "https://campfire-songbook.com/s/"` and `WEB_APP_URL = "https://campfire-songbook.com/app/"`.
- `isShareAddress(url: String): Boolean` — `https`, the host exactly `campfire-songbook.com`, the path `/s/` or
  anything under `/app/` (the site's 404 page brings a deep one back), with a fragment. What the shells ask before
  handing an address on; whether its fragment reads is the codec's business.
- `MAX_LINK_LENGTH = 32_768`, `LONG_LINK_LENGTH = 4_096`.
- `ShareLinkTarget` (`Song(fileName)`, `Setlist(fileName, songFileNames: Set<String>?)`), `ShareLinkResult`
  (`Ready(url: String)`, `TooLong(length: Int)`, `Unavailable`) and `SharedContent` (`Files(files: List<ImportedFile>)`,
  `Unreadable`, `FromNewerVersion`).
- `ExportedFile.PNG_MIME_TYPE`, next to `ZIP_MIME_TYPE`.

### 3.3 `:domain:implementation`

- `shareLink/ShareLinkCodec.kt`, an `internal object`:
  - `encode(kind, bytes): String` — `zlibOf`, base32, the prefix.
  - `decode(fragment: String): SharedContent` — the grammar; a payload of more than 65,536 characters (twice what this
    version writes, so that a later one may raise its cap) is unreadable before anything is decoded; base32;
    `inflateZlib` with `maxSize` of 1 MiB, more than fifty times anything a capped link compresses from; then a
    `song` that is not valid UTF-8 (`decodeToString(throwOnInvalidSequence = true)`), is blank or starts like a zip,
    and a `setlist` that does not start like a zip, are unreadable. What passes is one `ImportedFile`: `song.cho` or
    `setlist.zip`. Those names never show for a song, which the import names by its own header, and for a setlist only
    as the archive the report's lines are grouped under.
- `CreateShareLinkUseCaseImpl` (`@Factory`): a `Song` target through `ExportSongsUseCase` (one file name), a `Setlist`
  through `ExportSetlistUseCase` with the same `songFileNames` the Zip takes; a song whose text declares no `{title}`
  (read with the `:chordpro` parser the domain already uses) gets `{title: …}` as its first line, from `Song.title`.
  Null from either is `Unavailable`; a link over `MAX_LINK_LENGTH` is `TooLong` with its length, so the screen can say
  by how much.
- `ReadShareLinkUseCaseImpl` (`@Factory`): the fragment of an address (or a fragment alone, which is what the web
  shell has) to `SharedContent`, on `Dispatchers.Default`.
- The two interfaces in `:domain:api`, `CreateShareLinkUseCase` and `ReadShareLinkUseCase`, single `operator fun
  invoke`s like every other.
- `:domain:implementation` depends on `:zlib`.

### 3.4 Tests (`desktopTest`)

- `:zlib`: `PrintDeflaterTest`, `PrintDeflaterRoundTripTest` and `InflaterTest` moved here (the last two keep checking
  against `java.util.zip`); `inflateZlib` against JVM-made zlib streams, a wrong Adler-32, a preset dictionary, bytes
  after the end, and a stream that would pass `maxSize` stopped as it passes it rather than once it has ended; CRC-32
  against `java.util.zip.CRC32`.
- `:domain:implementation`: `ShareLinkCodecTest` — round trips of the demo songs and setlist, the same link for the
  same file, every rejection of §3.1 and §3.3 one by one (a kind, a version 0, a leading zero, a character outside the
  alphabet, padding, non-zero leftover bits, a link cut short anywhere, one altered character, a zip bomb in each
  kind, a song that is a zip, a setlist that is not), and the newer version; `CreateShareLinkUseCaseImplTest` with fake
  export use cases — the title added only where there is none, the ticked songs passed through, `TooLong` at the cap.
- `:data:model`: `ShareLinksTest` for `isShareAddress` (another host, `http`, a look-alike host such as
  `campfire-songbook.com.example`, a path outside both, no fragment).

## 4. The QR code (`:presentation`'s `ui/share/`, pure, tested)

### 4.1 `QrCode.kt`

- `QrCode.encode(segments: List<QrSegment>, maxVersion: Int, minimumLevel = QrLevel.LOW): QrCode?` — null when the
  segments do not fit `maxVersion`. The smallest version that holds them, then the highest error correction level that
  still fits in it (free robustness, as the reference encoders do). Byte and alphanumeric segments only; the link is
  always two: the address up to and including the `.` in byte mode, the payload in alphanumeric mode.
- The parts of the standard: the capacity and block tables for versions 1–40, Reed–Solomon over GF(256), the
  interleaving, the function patterns, the format and version information with their BCH codes, all eight masks and
  the four penalty rules. About four hundred lines; no allocation per module.
- `QrCode.size` and `isDark(x, y)`.
- `MAX_QR_VERSION = 30`. For scale: version 30 at level L has 1,735 data codewords, which after the 40-character
  address holds about 2,450 base32 characters, a zlib stream of about 1,530 bytes. The demo's *House of the Rising
  Sun* (3,103 bytes) compresses to 1,536 with the fixed codes and fits; with dynamic codes it would be 1,268.

### 4.2 `QrCodePng.kt`

`qrCodePng(code: QrCode, moduleSize = 8): ByteArray` — a one-bit grayscale PNG (signature, `IHDR`, one `IDAT` from
`Deflater.zlibOf`, `IEND`, each chunk's CRC from `:zlib`), black modules on white with the four-module quiet zone the
standard asks for. A version 30 code is 1,160 pixels square and a few kilobytes.

### 4.3 Tests (`desktopTest`)

- `QrCodeTest`: what the encoder draws is rendered to a `BufferedImage` and decoded with ZXing's
  `QRCodeReader` — every version from 1 to 40 at every level, both segment kinds and the two of them together, every
  mask forced in turn — and the text compared; the version and level chosen at every boundary of the capacity table;
  null one character past version 30.
- `QrCodePngTest`: read back with `javax.imageio`, the size, the quiet zone, every module, and the result decoded
  with ZXing again.
- `com.google.zxing:core` goes into `libs.versions.toml` and into `:presentation`'s `desktopTest` dependencies alone.

## 5. `:presentation`

### 5.1 The export screen

- `PrintSettings.Format` gains `LINK("link")`. An older version reads `link` as PDF (`UserPreferencesMappers` falls
  back to it), which is what it would offer anyway.
- `FormatChoice`: three segments — PDF, ChordPro or Zip, Link — and the Link line under them.
- **The options**, for Link: a setlist's song ticks, the same selection the Zip uses; and **Save QR code**, a list item
  with `ic_save`, present while there is a code. Nothing else, as for the Zip.
- **The preview**, `LinkPreview`, crossfaded in like `FilesPreview`: a card that is white in both themes with the code
  drawn black on it (a `Canvas` of `size × size` rectangles, the quiet zone part of the card, never the theme's
  colors — a scanner needs dark on light, and an inverted code is one many of them refuse), as large as the pane
  allows; under it the link itself in the monospaced font, three lines with the middle left out, selectable; and a
  line of its length. While the link is made the card shows the `LayoutIndicator` the PDF uses.
- **What it says**, in place of the code, by `ShareLinkResult` and the code:

  | State | The preview shows | Copy link | Save QR code |
  | --- | --- | --- | --- |
  | Fits a code | the code, the link, its length | yes | yes |
  | Too long for a code | "Too long for a QR code. The link still works: copy or share it.", the link, its length | yes | no |
  | Over `LONG_LINK_LENGTH` | the above, and "Some messaging apps cut links this long." | yes | no |
  | Too long for a link | "Too long to share as a link. Choose fewer songs, or share the Zip." and its length against the cap | no | no |
  | Song unreadable | what `FilesPreviewMessage` says for the ChordPro format today | no | no |
  | A setlist with no song ticked | as the Zip: not offered unless it has no songs at all | — | — |

- **The floating action button** is **Copy link** (`ic_content_copy`, a new drawable) for Link and leaves where there
  is no link, as Save leaves without pages. Ctrl / Cmd + S saves the QR code where there is one (`saveShortcut`).
- **Share** in the app bar shares the link as text on Android and iOS.
- The link and the code are made off the main thread (`produceState` keyed by the dialog and
  `chosenSongFileNames`, the encoder on `Dispatchers.Default`), and only while Link is the format.

### 5.2 The platforms' halves

- `ui/platform/Clipboard.kt`: `expect fun plainTextClipEntry(text: String): ClipEntry`, set through
  `LocalClipboard.current.setClipEntry` — `ClipData.newPlainText` on Android, `StringSelection` on the desktop,
  `ClipEntry.withPlainText` on iOS and the web (to be checked against Compose 1.12.1, where the last two may still be
  experimental). `isClipboardConfirmedBySystem` is true on Android 13 and later, which shows its own confirmation and
  asks apps not to add one.
- `FilePicker.shareText(text: String, subject: String): Boolean`, defaulting to false with `canShare`:
  `AndroidFilePicker` sends `ACTION_SEND` `text/plain` with `EXTRA_TEXT` and `EXTRA_SUBJECT` through a chooser;
  `IosFilePicker` hands `UIActivityViewController` the address as an `NSURL`, so Messages and Mail show a link rather
  than a string, with the same presentation rules its `shareFile` follows.

### 5.3 The view model

- `createShareLink(dialog: DialogType.Export, songFileNames: Set<String>?): ShareLinkResult`, through
  `CreateShareLinkUseCase`.
- `onShareLinkCopied()` → `Message.ShareLinkCopied`, not sent where the system confirms a copy itself.
- `shareLink(filePicker, dialog, url)` and `saveQrCode(filePicker, dialog, png)`, through `launchFileTransfer` and
  `save(...)` as `exportFiles` is, the PNG an `ExportedFile` named by `qrCodeFileName(source)` beside `pdfFileName` in
  `PrintFileName.kt`; a saved code is `Message.QrCodeSaved` and leaves the screen up, since the link is likely to be
  copied next.
- **`importShareLink(link: String)`**: `ReadShareLinkUseCase`, then `Files` to `enqueueImport(files, shouldOpenSong =
  true)` — the "Open with" path, so the queue, the duplicate and collision rules, the report screen and opening a
  single song (`openImportedSong`, which leaves an editor with unsaved text alone) are all the import's own;
  `Unreadable` to `Message.ShareLinkUnreadable`; `FromNewerVersion` to `Message.ShareLinkFromNewerVersion`.
- `CampfireApp` takes `shareLinks: Flow<String> = emptyFlow()` next to `filesToImport`, collected into
  `importShareLink`.

### 5.4 The web shell

- **`index.html` takes the fragment out of the address before anything else happens**: `sharedLinkFragment(hash)` in
  `<script id="campfire-launch">` (pure, so `offline.test.cjs` runs it) answers whether a hash is `song=` or
  `setlist=` followed by something, and the main script, before `claimLibrary`, appends it to a JSON list in session
  storage (`campfire-shared-links`) and replaces the address without it. So a reload — the user's, or the page's own
  after downloading another build — never imports twice, and never loses a link that was not imported yet: it is still
  in the tab's session storage, and the next start takes it. Wrapped in try/catch; where session storage is refused
  the fragment is kept in a variable for this page alone.
- `ui/platform/ShareLinks.wasmJs.kt`: `sharedLinks(): Flow<String>`, a promise per link like `droppedFiles()`, taking
  one off the stored list as Kotlin hands it to the view model. `CampfireWebApp` passes it as `shareLinks`.
- **A second tab**: `showAlreadyOpen` posts every stored link to the `BroadcastChannel` `campfire-shared-links`. The tab
  holding the library listens from the moment it holds the lock, adds what arrives to its own stored list (so an
  answer survives its own reload too), and answers with the link's id; the second tab then says "The song was sent to
  Campfire in your other tab." and drops it from its own list. With no answer within two seconds (the other tab is
  still downloading, or is a version without the listener) the link stays, and Retry imports it once the tab gets the
  library. The page's strings are added to its English and Hungarian `text`.
- `BrowserHistory.kt` needs nothing: its `replaceHistoryEntry` already writes path and query only, and the fragment is
  gone before its first write. `BrowserRoutes`, `service-worker.js` and `routes.js` are untouched — the fragment is
  never part of a request, and `/app/` is the songs.
- Offline: a kept build opens `/app/#…` with no connection, so a link opened in the web app imports offline.

### 5.5 Strings

Every one in `values` and `values-hu`; none takes somebody else's text, except the share subject, which takes a title
and is read with `textResource`.

| Key | English | Hungarian |
| --- | --- | --- |
| `print_format_link` | Link | Link |
| `print_format_link_song_description` | Ideal for sending to a phone: a link and a QR code that open the song in Campfire. The song is inside the link, and nothing is uploaded. | Telefonra küldéshez ideális: egy link és egy QR-kód, amely megnyitja a dalt a Campfire-ben. A dal magában a linkben van, semmi sem kerül feltöltésre. |
| `print_format_link_setlist_description` | Ideal for sending to a phone: a link and a QR code that open the setlist and its songs in Campfire. Everything is inside the link, and nothing is uploaded. | Telefonra küldéshez ideális: egy link és egy QR-kód, amely megnyitja a listát és a dalait a Campfire-ben. Minden a linkben van, semmi sem kerül feltöltésre. |
| `share_link_copy` | Copy link | Link másolása |
| `share_link_save_qr_code` | Save QR code | QR-kód mentése |
| `share_link_qr_code_description` | QR code of the link | A link QR-kódja |
| `share_link_length` (plurals) | %d character / %d characters | %d karakter |
| `share_link_too_long_for_qr_code` | Too long for a QR code. The link still works: copy or share it. | Túl hosszú egy QR-kódhoz. A link így is működik: másold ki vagy oszd meg. |
| `share_link_long` | Some messaging apps cut links this long. | Egyes üzenetküldő alkalmazások levágják az ilyen hosszú linkeket. |
| `share_link_too_long` | Too long to share as a link. Choose fewer songs, or share the Zip. | Túl hosszú ahhoz, hogy linkként megosszuk. Válassz kevesebb dalt, vagy oszd meg a Zip-et. |
| `share_link_subject` | %s in Campfire | %s a Campfire-ben |
| `message_share_link_copied` | Link copied | Link kimásolva |
| `message_qr_code_saved` | QR code saved | QR-kód elmentve |
| `message_share_link_unreadable` | This link could not be read. It may have been cut short on its way. | Ezt a linket nem sikerült beolvasni. Lehet, hogy útközben megrövidült. |
| `message_share_link_from_newer_version` | This link was made by a newer version of Campfire. Update the app to open it. | Ezt a linket a Campfire egy újabb verziója készítette. Frissítsd az alkalmazást a megnyitásához. |

The Hungarian `share_link_length` has one form, as the language does after a number; both items are written anyway.

## 6. The native shells

### 6.1 Android (`app/android`)

- `AndroidManifest.xml`, on `.CampfireIntentActivity` next to the other filters:

  ```xml
  <intent-filter android:autoVerify="true" android:label="@string/campfire">
      <action android:name="android.intent.action.VIEW" />
      <category android:name="android.intent.category.DEFAULT" />
      <category android:name="android.intent.category.BROWSABLE" />
      <data android:scheme="https" android:host="campfire-songbook.com" android:pathPrefix="/s/" />
  </intent-filter>
  ```

  `/s/` alone, never `/app/`, so the web app stays the web app (default 2). Fragments play no part in matching, and
  arrive whole in `intent.data`.
- `CampfireMainActivity.handle`: an `ACTION_VIEW` whose data `ShareLinks.isShareAddress` accepts goes to
  `importShareLink(data.toString())` in `AndroidFileImport.kt` — a second top-level `Channel` beside `pendingImports`,
  exposed as `shareLinksToImport` and passed through `CampfireAndroidApp` to `CampfireApp`. Everything `handle` already
  does about a recreated activity and one reopened from Recents holds for it.
- `importSharedTexts`: a shared text that is nothing but one share link is sent there too, rather than being reported
  as skipped by `isOnlyLinks` — a link shared from a messenger's menu to Campfire.
- The release build's App Link is verified at install against `assetlinks.json` (§10). The `.debug` package is not in
  it (default 11), so on Android 12 and later its links open the browser unless approved by hand.

### 6.2 iOS (`app/ios`)

- `iosApp/iosApp/iosApp.entitlements`, new, with `com.apple.developer.associated-domains` holding
  `applinks:campfire-songbook.com`; `CODE_SIGN_ENTITLEMENTS` set to it in both configurations of `project.pbxproj`.
  The app has had no entitlements file until now; `share-extension.md` creates the same file for its App Group, and
  whichever plan lands second adds its key to the existing one.
- The App ID gets the Associated Domains capability once, by hand (§10). `publish-ios.yml` lets xcodebuild make the
  App Store profile with `-allowProvisioningUpdates`, so the next run's profile carries it; nothing in the workflow
  changes.
- `IosFileImport.kt`'s `openUrl`: an address `ShareLinks.isShareAddress` accepts goes to a `pendingShareLinks` channel
  rather than being read as a file, exposed beside `filesToImport` and passed through `CampfireIosApp`.
- `iOSApp.swift`: SwiftUI delivers a Universal Link to `.onOpenURL`, which already forwards to `openUrl`. If the check
  in §9 finds otherwise, `.onContinueUserActivity(NSUserActivityTypeBrowsingWeb)` forwards `webpageURL` the same way —
  only one of the two, or a link would be imported twice and reported as a duplicate the second time.
- `Info.plist`: `NSPhotoLibraryAddUsageDescription`, because Save QR code goes through the share sheet, whose
  **Save Image** ends the process without it. English, like the other usage strings: "Campfire saves the QR code of a
  song to your photos when you ask it to."

### 6.3 Desktop (`app/desktop`)

Nothing. The Mac App Store build shares the App ID, but a Compose JVM app cannot take an `NSUserActivity`, and it
claims no associated domain, so macOS keeps opening the links in the browser.

## 7. Corner cases, and what each one does

| Case | Behaviour |
| --- | --- |
| A link opened twice | The same bytes: disregarded as a duplicate, as the same file opened twice is. |
| A song the library holds a different version of | The import's question: keep both, replace, skip, cancel. |
| A link cut short or altered by a messenger | The checksum fails: "could not be read… cut short". Nothing is written. |
| A link made by a later format version | "made by a newer version of Campfire". |
| The landing address with no fragment, opened in the app | Unreadable, said once. |
| A crafted link (zip bomb, huge payload, text that is not UTF-8) | Refused by the codec's limits before the import; whatever passes meets the import's own limits, as a dropped file does. |
| Read only mode | Imported, as "Open with" is. |
| Setlists switched off | A setlist link still writes the setlist, as an imported Zip does; the tab stays hidden. |
| An editor with unsaved text in front | The song is imported and announced; it is not opened over the editor (`openImportedSong`). |
| A song opened from a setlist, exported as a link | The file as the library holds it, as ChordPro is; the setlist's key, tempo and capo travel only in a setlist link. |
| The library's own overrides of a song | Not carried, as ChordPro does not carry them. |
| A cover named by the song | The address travels in the file; the recipient's app downloads it, if its Cover art switch is on. |
| A link opened in a second web tab | Sent to the tab that owns the library; without an answer, kept for Retry (§5.4). |
| A link opened in the web app while an update is downloaded | Kept in session storage across the page's reload, imported by the build that starts. |
| A link opened offline | The web app imports it from the kept build; the landing page is not kept, so `/s/` needs a connection. |
| Universal Link not followed (pasted into Safari, an in-app browser) | The landing page, whose Smart App Banner opens the app with the address (§10). |
| App Link not verified (the debug build, verification failed at install) | The browser and the landing page. |
| A phone's camera scanning the code | It opens the address; with the app installed the system hands it over, as a tapped link. |
| A QR code in the dark theme | Always black on white, on a white card. |
| Ctrl / Cmd + S with no code | Nothing: there is nothing to save. |
| The browser's history | Holds the landing page's address with its fragment, as it holds any address; a browser that syncs history carries it there. Campfire sends nothing. |
| Baseline profile journey | Untouched: it opens no export screen. |

## 8. Order of work

Each step builds and tests green on its own, one commit each. The website files go out before any app that relies on
them, since a link filter is verified at install and an association file is cached by Apple for a day or more.

1. **A measurement**, thrown away: the codec's first draft run over a real library of a few hundred songs, to see how
   many fit a version 30 code. If fewer than four in five do, dynamic Huffman codes go into the `Deflater` before the
   rest, which the format already allows.
2. `:zlib`, with the code and the tests moved and nothing else changed: the PDF tests, `PrintPdfWriterTest` included,
   pass unchanged.
3. `:data:model`'s `ShareLinks`, the codec and the two use cases, with their tests.
4. The QR encoder and the PNG writer, with ZXing in `desktopTest`.
5. The export screen's Link format, the clipboard and `shareText`, on the desktop first.
6. `importShareLink`, `shareLinks` and the messages; the web shell and `index.html`, with the Node tests.
7. The website (§10): the association files, the landing page.
8. Android: the filter, the channel, the shared text.
9. iOS: the entitlement, the capability, `openUrl`, the photo library string.
10. Docs: the root `CLAUDE.md` — a sentence in the opening paragraph that a link carries its song after the `#` and
    reaches no server, the architecture block with `:zlib`, the Printing section's formats, a short Sharing section,
    the test list and command — and `presentation/CLAUDE.md` (Export), `data/source/local/implementation/CLAUDE.md`
    (the `zip/` paragraph, which loses the inflater and keeps saying why the writer stores), `app/web`, `app/android`,
    `app/ios`, `zlib/CLAUDE.md`; the README's feature list.

## 9. Checks owed by hand

- **The caps**, on the 360 × 640 dp screen: a version 30 code scanned off it by the cameras of an iPhone and a Pixel
  at arm's length, and a version 40 one, which decides whether `MAX_QR_VERSION` moves; the three segments of the
  format in Hungarian.
- **Every way a link travels**, a song's and a long setlist's: WhatsApp, Messages, Telegram, Signal, Gmail, Slack, a
  note pasted into a browser — the link arrives whole and opens; the preview shows the landing page's card, never the
  song.
- Android: the App Link verified (`adb shell pm get-app-links com.pandulapeter.campfire` says `verified`), a link
  from a messenger opening the app cold and warm, a scanned code, a shared text; the debug build through
  `adb shell am start -a android.intent.action.VIEW -d '<link>' com.pandulapeter.campfire.debug` and through
  `pm set-app-links-user-selection`; `/app/` still opening in Chrome.
- iOS: the association fetched by Apple's CDN
  (`curl https://app-site-association.cdn-apple.com/a/v1/campfire-songbook.com`) — GitHub Pages serves an extensionless
  file as `application/octet-stream`, which the CDN has been accepting, and this is where that is confirmed; a link
  from Messages and Mail opening the app cold and warm, and whether it arrives at `.onOpenURL` (§6.2); `xcrun simctl
  openurl booted '<link>'` on the simulator; the share sheet's Save Image with its permission prompt; a TestFlight
  build, since the entitlement is checked against the profile there.
- Web: a link in Chrome, Firefox and Safari, cold and with the build kept; reloading after the import; a second tab
  with the first open, and with the first still loading; a link while an update downloads; the address bar without
  the fragment.
- Desktop: Copy link and Save QR code on macOS, Windows and Linux; the landing page's ChordPro file opened with the
  desktop app.
- The PNG printed on paper and scanned.

## 10. Outside the repository

- **`campfire-website`** (a sibling checkout at `../CampfireWebsite`, plain HTML on GitHub Pages, `.nojekyll` already
  there):
  - `.well-known/assetlinks.json`: the `delegate_permission/common.handle_all_urls` relation for
    `com.pandulapeter.campfire`, with the SHA-256 of the certificate Play signs the app with — Play Console's App
    signing page shows it, and the upload key's beside it where Play App Signing is in use; where it is not, the
    release keystore's.
  - `.well-known/apple-app-site-association`, no extension:
    `{"applinks": {"details": [{"appIDs": ["N45A6ZHDGY.com.pandulapeter.campfire"], "components": [{"/": "/s/*"}]}]}}`.
  - `s/index.html`, the landing page, in the site's style and with its mobile/desktop switch: `noindex`, Open Graph
    tags that say "A song shared from Campfire" (the only thing a link preview can see); a script that reads the
    fragment, decodes the base32 and inflates it with the browser's `DecompressionStream('deflate')`, and shows a song's
    title and artist (or a setlist's title, from its stored manifest), always as text, never as markup — where the
    browser has no `DecompressionStream`, the page shows its buttons without the title. The buttons: **Open in
    Campfire for the web** (`/app/` with the same fragment), **Download the ChordPro file** (or the Zip), and the
    stores; on iOS a Smart App Banner (`apple-itunes-app`, app id 6815160850, with the address as its `app-argument`,
    to be verified to reach `openUrl`). English and Hungarian by `navigator.language`, as the app's `index.html` does.
  - The privacy policy: a sentence that a shared link carries the song in the part of the address after the `#`, which
    no browser sends to a server, and that the landing page reads it in the browser. The support page: "How do I send a
    song to somebody?"
  - The README's file list.
- **Apple Developer**: the Associated Domains capability on the App ID `com.pandulapeter.campfire`. That invalidates
  the existing profiles; the iOS one is made again by xcodebuild, and the Mac App Store one by
  `app_store_signing.py`, which only takes an `ACTIVE` profile and makes a new one where there is none.
- **Play Console**: the Deep links page shows the domain verified once the release is out; nothing to fill in. The
  Data safety form is unchanged.
- **App Store Connect**: the review notes gain how to try a link; the privacy label is unchanged.
- A What's new message, by the prepare-release skill.

## 11. Not in this plan

- A scanner inside the app. Every phone's camera reads QR codes, and the system hands the address over.
- Opening a link on the desktop: a `campfire://` scheme, or pasting a link into the import. The landing page's
  ChordPro file is the desktop's way in for now.
- The Web Share API in the web app, and a `campfire://` fallback button on the landing page.
- A confirmation sheet before a link is imported (default 6).
- Dynamic Huffman codes, unless step 1 asks for them.
- A QR code printed into a PDF, a whole library as a link, several songs without a setlist.
- Short links, expiring links, encrypted links — every one of them needs a server.
- Universal Links for the Mac App Store build.
- Deflating the Zip export's entries, which `:zlib` now makes possible and which would halve a library's archive; it
  changes what the export's too-large-to-import warning compares, so it is a change of its own.
