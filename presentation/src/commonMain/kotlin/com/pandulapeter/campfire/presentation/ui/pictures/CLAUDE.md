<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->


# :presentation — ui/pictures

The pictures a song names (`{image: src=…}`), for the song view and the PDF.

- **Where pictures come from is a `SongImageLoader`** (`LocalSongImageLoader`): the library's (`LibrarySongImages`, over
  the image use cases, whose `loadImage` falls back to the editor's draft shelf), provided at the app's root; an import
  preview provides `ImportedSongImages` (the review's pictures by name, sized from their bytes, then the library's). `cacheKey` keeps two loaders' pictures of one name apart in Coil's memory.
- **A size before its picture**: `sizes` is read from headers, by name, kept for the view model's life (a name is its
  content) and asked again on every `GetImageChangesUseCase` change. `SongTextStore` awaits `loadSizesOf` before it
  holds a text, so a song is laid out with its pictures' room on its first frame; a text that came another way has
  its pictures arrive as data (empty room, then the picture). The model takes only the sizes its text names
  (`imageSizesOf`), so a picture read for another song rebuilds nothing.
- **Fitting** (`fittedImageSize`, tested; the screen in dp, the PDF in points, a pixel as one of either, as ChordPro
  and browsers draw it): proportions kept, only ever smaller, by the least of its own size (never blown up), the
  column's width, the room (one page, so never cut), and `width` / `height` (points, or percent of the column's width
  or of the room); then `scale` of that. `align` left, centre (default), or right, physically.
- Decoded by Coil from the loader's bytes at the size laid out (`SongPicture`, a fetcher on the request rather than the
  app's image loader), never whole. A JPEG's EXIF orientation is applied by the decoder, as the header's size already is: Skia's
  (desktop, iOS, web; tested on the desktop, `SongPictureOrientationTest`) and Coil's BitmapFactory decoder on Android,
  which reads the type from the bytes, so nothing here turns a picture. The viewer (`ImageViewer`, a `Dialog`) decodes at twice the window, which is as far
  as zooming in stays sharp; zoom and pan are `ImageZoom` (tested).
- **A missing picture** (no file, or one some platform cannot show) is `MissingImage`: the sentence, and Add image
  where `LocalSongImageAdder` is given (never in read only mode or a preview), asking Photos or Files in a popup where
  there is a photo library, taking one picture. The button keeps its room where hidden, since read only mode changes
  nothing a song's measurements are kept by. Add stores the picked file and points the
  song's directives at it in one step (`SaveSongImageUseCase`, under `SongTextStore`'s write lock); a refused file is a
  snackbar saying why, and a song that no longer names the picture by then is left alone.
- **A picture named by its address** (`PictureLinks.isLink`: `https` only) is never drawn from the web. Its placeholder
  adds Download before Add image (by the name alone, so the room is the same with the buttons hidden), which downloads
  it (`PictureLinkDownloader`, part of `PictureWork`) and stores it as Add image does. What brings no picture is a
  snackbar: nothing there, a page rather than a picture, or no answer, which on the web is also a site refusing other
  pages its pictures, so that one suggests saving the picture and adding the file.
- **A file the user attaches** (the editor's Attach, the Attachments sheet's Add file) goes through `AttachmentIntake`:
  refused with a snackbar where it is over 50 MB (a link suggested) or of a type no song takes (`isAttachmentFileName`),
  else kept on the draft shelf as it is, never re-encoded, its reading and hashing part of `PictureWork`. The draft shelf
  holds such files beside pictures and lets go of them the same way.
- **A picture the user adds** (the editor's Image button, one opened, shared, or dropped alone, Add image) goes through
  `PictureFitter` first, never one an archive or sync brings: kept as it is where the library takes it (never
  re-encoded); refused as too large (bytes or pixels, the read going up to 40 MB) or as HEIC or AVIF (never SVG, never no
  picture), it is re-encoded on the device (`reencodePicture`, `ui/platform`) and the copy taken by the ordinary intake,
  named by its own content. PNG first for a PNG, GIF, or WebP, else JPEG at 0.85, then smaller JPEGs, a bounded list
  (`pictureAttemptsFor`), at most 4,096 px a side and 12 MP (`reencodedSizeOf`). A snackbar says a copy was made; among
  several pictures, a refusal is said instead. A copy that does not fit keeps the original refusal. Every fitting is
  tracked by the one `PictureWork`, which brings up the window's work bar (`ui/progress`) once one has run 400 ms (a
  large WebP takes seconds on an older phone), never blocking the editor; the picker's own read before it is not counted,
  since the picker's screen is up for an unknown part of that time.
