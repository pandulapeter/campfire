<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->


# :presentation — ui/dialogs/lookup

Both sheets only fill the form's draft; Save is the only write.

### Cover search

- Asked as the sheet enters the stack, so its first frame already says "searching", then **only from the search key or
  button, never per keystroke**: MusicBrainz allows one request a second.
- The Web address tab previews after a 500 ms typing pause; an address that does not load can still be saved, since a
  host may refuse only the web build.
- A tile whose thumbnail fails is dropped (that is how a cover-less release group is found out); if any dropped tile
  was unreachable and none is left, the search fails with Retry. Only a loaded tile can be selected, so Save never
  writes an address nobody has seen.
- **The sheet opens at full height**: its content grows after opening, and resizing while sliding stuttered.
- The search lives in the view model to survive activity recreation, cancelled once the sheet is nowhere in the stack.

### Song details lookup

- **Writes nothing.** Over the Edit song details form, which stays open. Fill in hands a new draft to the form and
  bumps `fillCount`, which keys the fields so they reset even to an equal draft. A restored process reopens the form,
  not the lookup.
- A row's review (`SongDetailsReview.kt`, tested): one box per field it would change, ticked only where the form is
  empty and never for the title.
- MusicBrainz songwriters are fetched when a row opens, never again for the row held, so recreation asks nothing.
