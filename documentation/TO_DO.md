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
- Onboarding: the circular hero element has some inset handling issues
- Export screen FAB is not visible enough on large screens
- Screenshot generator: bottom sheets seem to cover the bottom window chrome
- Tab transposition bug: it doesn't compensate for the digit-count changes
- The Songs / Setlists screens (similarly to other screens in the app) should animate scrolling to the top when their navigation button is reselected. However, at least on the Songs screen (check Setlists too) this animation is not smooth: it animates to a certain sticky header, slows down, then animates to the top. Fix it so that one smooth animation scrolls to the very beginning of the list
- Fix compile-time warnings on all platforms
- PDF floating page controls show / hide should also fade
- Editor bug when copy-pasting grid sections (it tries to use the chords syntax even though we're in a grid)
- Fast scrolling is glitchy, especially on Setlists
- Document all keyboard shortcuts used in the app
- Make sure cover art cache for deleted songs / changed cover art is properly cleared
- Filters bottom sheet tag counter animation glitch (select Romany and see how the counters get updated)
- Share to Android can work unexpectedly if the app is already open
- Select all in input fields when they become focused
- Simplify import and edit flows based on user testing
- Rename master branch to main
- Comments in setlists (between songs)
- Simplify adding comments / annotations to songs
- Editor multi-cursor support
- Streaming zip writer on all platforms
- Fast scroller on the song details screen
- External monitor support for lyrics only...? Maybe as a new window on desktop, lyric projection via AirPlay / Chromecast, etc
- AI skill for exposing direct access to the library folder
- Add support for abcnotation https://abcnotation.com/
- Investigate Google Drive integration, maybe self-hosted sync server
