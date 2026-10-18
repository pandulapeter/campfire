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

The metronome's pure helpers and shared controls. The click is `:metronome:api`'s `Metronome`, injected into
`CampfireViewModel`, which hands it complete patterns and reads `playback` and `beats` back. The tab is in
`screens/metronome/`; the song page's button in `screens/songDetails/`; where a tempo is stored in `ui/playing/`.

## The click belongs to the screen it is played from

There are exactly two: the Metronome tab and the song details screen. **A click never outlives the screen it was started
on**, so nothing ever plays with no way on screen to stop it. Three rules stop it, and they are the whole lifecycle:

- `updateBackStack`: whenever the top is neither a song nor the Metronome tab (the editor over a song, a song closed or
  deleted, a tab selected, a song opened over the tab or another song).
- `setVisibleDialog`: as the export screen or the tuner sheet is dealt over the song. Both are dialogs to the view model
  and never reach the back stack; the tuner also listens through the audio the click plays in.
- `onCleared`: the app being left (a finished Activity, not a paused one; an activity destroyed without finishing, as
  "Don't keep activities" does, keeps its click), where a singleton metronome would otherwise click on, notification
  and all, under a process nobody is looking at.

Apart from those, a click that cannot sound (volume zero or every beat muted, `MetronomePattern.canSound`) is stopped
three seconds after the app goes out of sight (the grace covers an Android activity recreation), and says so
(`MetronomeController.onAppStopped`), so no background playback runs for silence — except on Android with Vibrate on and
a beat not muted, which plays on (iOS has no background haptics).

## What a click plays for (`MetronomeContext.kt`, tested)

- The `SongDetails` destination on top of the stack, at the page its pager is heading for (`onSongDetailsPageChanged`,
  from `targetPage`, so paging retargets while the page slides in), at the stretch of that song the page is headed for
  (`SongTiming`, from `timingIndexAt` and `SongStepper.headedOffset`: a step's or fling's target, never a finger still
  dragging; a song paged back to reports its last stretch). Or `Standalone`, the tab's pattern.
- `metronomePatternOf` (`MetronomePatterns.kt`): a song's tempo and `{time}` (4/4 where it names none), accents for that
  signature, everything else from the settings.
- One collector in `init` follows the context, tempos, settings, and library:
  - **Another song or another stretch moves the click once the bar being played ends** (`Metronome.update`'s
    `fromNextBar`), from beat one, so the band finishes its bar.
  - Anything else (a tempo stepped, a setting, the same stretch at a scaled tempo) applies from the next beat.
  - The first value is only remembered, so a view model rebuilt over a playing click does not stop it.
- A collector in `init` tells the engine whether the tab or a song is on top with the feature on
  (`isMetronomeStartable` → `Metronome.setStartable`): only those screens' taps may open the web's audio device; a no-op
  elsewhere.
- `toggleMetronome` starts or stops the context's pattern; starting over a song opens its panel. A click that stops on
  its own is a `Message.MetronomeStopped` snackbar naming why.
- Space (on the tab) and M (on a song) toggle it on the desktop and the web (`toggleMetronomeByKey`), under no dialog or
  menu, only on a held key's first press.

## The panel (`MetronomePanel.kt`)

- **On the song details screen it is inside the app bar** (`CampfireTopAppBar`'s `bottomContent`), growing and shrinking
  the bar. Being inside the screen is the design: it is laid out, pushed, and popped with it, covers none of the song,
  and no scaffold wraps the app to make room. Its controls fade and squash on the bar's own timing so they always fit.
- It holds only the `BeatRow` and play / stop (`PlayStopMark`), at the end because the phone is held there. The tempo
  is in the song's first section; sound, subdivision, and volume are the tab's. A lit beat also swells, since in the
  light theme an accent at rest is already half the lit color.
- The panel takes `MetronomePanelState.kt` and two callbacks, not the view model. It reads no tempo, only the
  signature (`metronomeTimeSignatureOf`, tested).
- **Whether it is up is a preference** (`MetronomeSettings.isSongPanelShown`), written by the song's `MetronomeButton`
  and by a click started there, left alone when the screen is left, so the next song and the next launch keep it.
  Opening it starts nothing; closing it stops a playing click; stopping the click leaves it up.
- Performance mode keeps the play button; the song's read only line says the tempo.
- The pulsing mark (`MetronomeIcon.kt`: the navigation item, the song's button) tints from a table of filters at 32
  pulse steps, keyed on its two colors, so a playing click makes no filter per frame. Not a second draw at the pulse's
  alpha: that blends in sRGB where `lerp` mixes in Oklab.

### Accents

- Tapped on the `BeatRow` in both places (`MetronomeController.setBeatLevels`), for
  whatever the click plays for: on the tab, the signature's (`withBeatLevels`); on a song, that song's own
  (`UserPreferences.beatLevels`, `withSongBeatLevels`, tested), by signature, so a song whose 8/8 is 3+3+2 changes no
  other song.
- A song's own win (`beatLevelsOf`), apply wherever the song is opened from (the bar is the song's, not a set's
  reading), are renamed, deleted, and synced with it, and **are removed once tapped back to the signature's**, so the song
  follows the tab again.
- Each beat's whole column takes the press, not the block (a sliver at a muted beat).
- Settings are `UserPreferences.metronomeSettings`, saved once they hold still (`DebouncedPreference`).

## Tempo and time changes inside a song

- **A later `{tempo}` or `{time}` is a change from where it stands** (`ChordProBlock.Timing`), and the page says where
  the band is: on the song details screen each change starts a page, headed by a read-only line, and a playing click
  follows the page being read from beat one of the next bar (layout in `songView/model/CLAUDE.md`).
- **Until that bar ends the screen shows what is heard**: the beat row draws the playing bar (`Playing.pattern`), with
  its own accents where its signature differs from the one waiting (`barBeingLeft`), and ignores taps, since the accents
  would be written for the wrong song.
- The stepper, a setlist's entry, and the library override still hold one number, the opening tempo; a later tempo keeps
  its ratio to the file's opening one (120 → 60 played at 110 is 55), so nothing new is stored.
- A recalled chorus plays in whatever is in force where it is recalled. A song that fits one screen is still cut into
  pages by a change, since the page is the signal.
- The editor offers Tempo and Time signature repeatedly (the header first, then the start of the caret's line); its
  preview shows changes in place and is never paged; the PDF prints each as a line kept with what follows.
- **With the Metronome feature off none of it exists**: no line, no forced page. A later `{key}` is only read past.
