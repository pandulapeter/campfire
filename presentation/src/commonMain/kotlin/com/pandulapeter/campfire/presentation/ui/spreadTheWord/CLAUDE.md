<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# :presentation — ui/spreadTheWord

Once per device, a regular user is asked in the developer's own words to tell a musician friend about Campfire and,
where the store allows a dialog of the app's own to ask (Play, the Microsoft Store), to rate it. Campfire reaches new
musicians by word of mouth or not at all, and the app sends nothing, so the stores' rating counts and installs are the
only measure.

## Who is asked

All three must hold (`SpreadTheWordRules.kt`):

- **5 songs of their own**: the library's, the demo's two (`DemoLibrary.isDemoSong`, by name, edited or not) aside.
- **10 song details screens opened**, all songs together: each push of a `SongDetails` that is not an import's
  preview. The editor closing over one or a rename rewriting it keeps its id, so neither counts; paging between a
  setlist's songs happens inside one push.
- **On 3 different days**, by the device's local date: the same as "installed three days ago" for a new user, more for
  everyone else, and no install date is kept. A clock moved backwards counts another day, never fewer.

`UserPreferences.songOpens` stops counting once those hold or the request was shown, so a song open then writes nothing.
An existing user starts counting from the version that added it.

## When

- On the way back from song details: a pop from a `SongDetails` that leaves `Songs` or `Setlists` on top, once its
  transition has ended (`SpreadTheWordController.onTransitionEnded`), so the dialog never fights it. Any other change of
  the back stack before then drops it for this return.
- Never over another question (`canShowSpreadTheWord`): what holds What's new back, plus an owed onboarding, What's new
  not yet recorded for this version, and a sync run stopped before deleting most of the library. Conditions met while
  that gate is shut wait for the next return.
- **Recorded as it appears** (`isSpreadTheWordShown`, device-local, never exported or synced): an app ended with it up
  does not ask again.

## The dialog

- A signed note, the same for everyone, never a sentiment question (Play forbids gating a rating on one); the author's
  photo (`author_photo.png`, the website's) beside the title. One ask: no donation.
- Share sends `spread_the_word_share_text` with the website's address, never a store listing; through the share sheet
  where `FilePicker.canShare`, else copied with `Message.LinkCopied`. Nothing is offered for a share.
- Rate opens `Distribution.reviewUrl` (Play's listing, the Microsoft Store's `ms-windows-store://review`), never an
  in-app review API. Apple's platforms, Linux, and the web have none, and the note then leaves the rating out
  (`spread_the_word_message_without_rating`). Settings → About keeps its Share and Rate rows.
- Every button closes it.
