<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# To do
## Bugs / issues
- Import multiple files -> see details -> open one and delete / edit it from menu -> return to import details screen to see an outdated state
- The Setlist assignments bottom sheet has an overshoot efect that interferes with the bottom sheet's default drag handling, while the Song assignments bottom sheet doesn't. The Links sheet is also inconsistent - review, find all similar issues, and fix.
- The cover art bottom sheet has two primary actions

## Improvements
- Add validators to input fields
- Maybe all bottom sheets with song names in their headers could show the cover art for the song
- The Setlist screen should include song durations on the cards + a total for the setlist (right after the countdown if enabled, or instead of the countdown if it isn't). If some songs don't have a duration set / it's not a valid duration, add a + sign after the total duration of the setlist (if none are valid, don't display duration)
- Reset all filters button
- Add cover arts to the Song assignments bottom sheet list items
- Cover art bottom sheet: do we even need search input fields here?
- Rename master branch to main
- Song search should also match by tag
- Animate editor mode change (split)
- Add overshoot effects on all platforms

## Features
- Multi-select songs for bulk export or bulk edit (assign tags, languages, setlists)
- Implement optionally auto-indexing similar sections within a song
- Metronome: documentation/plans/metronome.md
- Haptic effects, especially for the fast scroller

## Ideas
- Global sync status display ?
- Fast scroller on the song details screen ?
- Simplify adding comments / annotations
- Optional close confirmation dialog on relevant platforms
- Add support for Latin and Nashville notations
- Chord diagrams (guitar, ukulele, keyboard) - user library, variations
- External monitor support for lyrics only...? Maybe as a new window on desktop, lyric projection via AirPlay / Chromecast, etc
- Streaming zip writer on all platforms
- First time user experience tutorial ?
- Native iOS, macOS, feel (overscroll, touch feedback, Liquid Glass)

## Other
- Test support with external control devices
- Improve test coverage
- Each top-level Composable should be defined in a separate file