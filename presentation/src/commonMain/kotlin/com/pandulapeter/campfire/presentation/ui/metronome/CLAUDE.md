<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->


# :presentation — ui/metronome

The metronome's pure helpers and shared controls; the click is `:libs:metronome:api`'s, injected into the view model,
which hands it complete patterns. Where a tempo is stored: `ui/playing/`.

### The click belongs to the screen it is played from

Only the Metronome tab and song details play one. **A click never outlives the screen it was started on**, so nothing
ever plays with no way on screen to stop it. Three rules stop it, and they are the whole lifecycle (the first two are
`SoundLifetime`'s):

- `updateBackStack`: whenever the top is neither a song nor the Metronome tab (the editor over a song, a song closed or
  deleted, a tab selected, a song opened over the tab or another song).
- `setVisibleDialog`: as the export screen or the tuner sheet is dealt over the song (dialogs to the view model, never
  on the back stack; the tuner also listens through the audio the click plays in).
- `onCleared`: the app being left (a finished Activity, not a paused one; one destroyed without finishing, as "Don't
  keep activities" does, keeps its click), where a singleton metronome would otherwise click on, notification and all.

Besides, a click that cannot sound (volume zero or every beat muted) is stopped three seconds after the app goes out of
sight (the grace covers an Android activity recreation), and says so, so no background playback runs for silence; except
on Android with Vibrate on and a beat not muted (iOS has no background haptics).

### What a click plays for (`MetronomeContext.kt`, tested)

- The song on top, at the page its pager is heading for (paging retargets while the page slides in), at the stretch of
  that page (a step's or fling's target, never a finger still dragging; a song paged back to reports its last stretch);
  or the tab's own pattern. A song's pattern: its tempo and `{time}` (4/4 where none), accents for that signature, the
  rest from the settings.
- **A new song or stretch takes effect from beat one of the next bar**, anything else from the next beat; a rebuilt view
  model does not stop a playing click.
- Only the tab's or a song's taps, with the feature on, may open the web's audio device (`Metronome.setStartable`).
- Starting over a song opens its panel. A click that stops on its own is a snackbar naming why.
- Space (tab) and M (tab and song, not an import preview) toggle it on desktop and web, under no dialog or menu, only on
  a held key's first press.

### The panel

- **On song details it is inside the app bar**, so it moves with the screen and covers none of the song. Only the beat
  row and play / stop, at the end because the phone is held there; the tempo is in the song's first section, sound,
  subdivision, and volume are the tab's. A lit beat also swells, since in the light theme an accent at rest is already
  half the lit color.
- **Whether it is up is a preference**, written by the song's button and by a click started there, left alone when the
  screen is left, so the next song and launch keep it. Opening it starts nothing; closing it stops a playing click;
  stopping the click leaves it up. Performance mode keeps the play button.
- The pulsing mark tints from a table of filters at 32 pulse steps, keyed on its two colors, so a playing click makes no
  filter per frame. Not a second draw at the pulse's alpha: that blends in sRGB where `lerp` mixes in Oklab.

### Accents

- Tapped on the beat row for whatever the click plays for: on the tab, the signature's; on a song, that song's own, by
  signature (`UserPreferences.beatLevels`, tested), so a song whose 8/8 is 3+3+2 changes no other song.
- A song's own win, apply wherever it is opened from (the bar is the song's, not a set's reading), are renamed, deleted,
  and synced with it, and **are removed once tapped back to the signature's**, so the song follows the tab again.
- Each beat's whole column takes the press, not the block (a sliver at a muted beat).

### Tempo and time changes inside a song

- **A later `{tempo}` or `{time}` is a change from where it stands**, and the page says where the band is: each change
  starts a page under a read-only line, and a playing click follows the page being read from beat one of the next bar
  (layout: `songView/model/CLAUDE.md`).
- **Until that bar ends the screen shows what is heard**: the beat row draws the playing bar, with its own accents where
  its signature differs, and ignores taps, since the accents would be written for the wrong song.
- Stepper, setlist entry, and library override still hold one number, the opening tempo; a later tempo keeps its ratio
  to the file's opening one (120 → 60 played at 110 is 55), so nothing new is stored.
- **With the Metronome feature off none of it exists**: no line, no forced page. A later `{key}` is only read past.
