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

The metronome's shared helpers and controls.

## Metronome

The click is `:metronome:api`'s `Metronome`, injected into `CampfireViewModel`, which hands it complete patterns and
reads `playback` and `beats` back (`metronomePlayback`, `metronomeBeats`); `ui/metronome/` holds the pure helpers and the
shared controls.

- **What a click plays for** (`MetronomeContext.kt`, tested): the `SongDetails` destination on top of the back stack, at
  the page its pager is heading for (`onSongDetailsPageChanged`, from `targetPage`, so paging retargets while the page
  slides in) and at the stretch of that song the page is headed for where it changes its tempo or time signature
  further down (`SongTiming`: which change, and the tempo and the signature its line shows, from `timingIndexAt` over
  the stops and `SongStepper.headedOffset` — a step's or a fling's target, never a finger still dragging; a song paged
  back to is put at its end first, so it reports its last stretch; another stretch restarts the bar like another song,
  while the same stretch at a scaled tempo is applied from the next beat, and the panel's beat row and the app bar's
  tempo follow it), or `Standalone`, the Metronome tab's own pattern (`metronomePatternOf`, in `MetronomePatterns.kt`: a
  song's tempo and `{time}`, 4/4 where it names none, the accents drawn for that signature in any context, everything
  else from the settings). Only the top of the stack is asked, since a click does not outlive the screen it is played
  from. One collector in the view model's `init` follows the context, the tempos, the settings and the library: a song
  paged to moves the click to it from beat one, anything else is applied from the next beat, and the first value is only
  remembered, so a view model built again over a playing click does not stop it. The last collector of `init` tells the
  engine whether the Metronome tab or a song is on top with the feature on (`isMetronomeStartable`, the only screens whose
  taps may open the web's audio device, `Metronome.setStartable`, a no-op elsewhere). `toggleMetronome` starts the context's
  pattern or stops it, and starting one over a song opens that screen's panel with it. A click that stops on its own is
  a `Message.MetronomeStopped` snackbar naming why. Space (on the tab) and M (on a song) toggle it on the desktop and
  the web (`toggleMetronomeByKey`), under no dialog or menu, only on the first press of a held key. **Three rules stop a click outright**, and between them they
  are the whole of the lifecycle: `updateBackStack`, whenever what is on top is neither a song nor the Metronome tab -
  the editor opened over a song, a song closed or deleted, a tab selected - so that nothing plays under a screen with no
  way to stop it; `setVisibleDialog`, as the export screen is dealt in over the song, for the same reason, since it is a
  dialog to the view model and never reaches the back stack; and `onCleared`, which is the app being left rather than being sent to the background (a finished Activity
  rather than a paused one), where a singleton metronome would otherwise go on clicking, with its notification, under a
  process nobody is looking at.
- **The song details screen's metronome** (`MetronomePanel.kt`, which the Metronome tab pins at its top too) is a panel inside that screen's own app bar, under
  the title row (`CampfireTopAppBar`'s `bottomContent`, the way the editor's toolbar is part of its bar), which grows
  and shrinks the bar as it comes and goes — never over its controls: they fade and squash towards the top on the bar's own
  timing (the beat row vertically, the play button whole), so they always fit the room the bar has. Being inside the screen is the whole design: it is laid out, pushed and
  popped with it, covers none of the song, and no scaffold wraps the app to make room for it. It holds a small `BeatRow`
  of the bar the click counts, taking the row's width, and play and stop at the end of it (`PlayStopMark`, morphing while it turns a quarter turn
  clockwise either way, easing in, in a
  `FilledIconButton`) — at the end because the phone is held there, and the row it would otherwise push aside is the
  panel's content — and nothing else: the tempo is a line below it in the song's own first section, where it is both
  shown and set, and the sound, the subdivision and the volume are the tab's. The **accents are tapped here as they are
  there**, the same `BeatRow` writing the same `withBeatLevels` for the signature on screen, since they belong to the
  bar rather than to the screen somebody is on and two rows that disagreed about 4/4 would be two settings. Each beat's
  whole column takes the press, not the block drawn in it, which in this row is a sliver at a muted beat. It reads no
  tempo at all, only the time signature the row's blocks are drawn for (`metronomeTimeSignatureOf`, tested, which its two
  screens feed through `screens/MetronomePanelState.kt`, the panel itself taking that state and two callbacks rather
  than the view model). `MetronomeSettings.isSongPanelShown` is what keeps it up: written by the app bar's button and by
  a click started here (`CampfireViewModel.toggleMetronomePanel`), left alone when the screen is left, so the next song
  opens with the instrument the user last chose - and the next launch too, since it is saved with the rest of the
  metronome's settings.
