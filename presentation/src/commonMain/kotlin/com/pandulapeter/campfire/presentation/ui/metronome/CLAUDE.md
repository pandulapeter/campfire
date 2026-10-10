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
reads `playback` and `beats` back (`metronomePlayback`, `metronomeBeats`); `ui/metronome/` holds the pure helpers and
the shared controls.

### What a click plays for

**What a click plays for** (`MetronomeContext.kt`, tested): the `SongDetails` destination on top of the back stack, at
the page its pager is heading for (`onSongDetailsPageChanged`, from `targetPage`, so paging retargets while the page
slides in) and at the stretch of that song the page is headed for where it changes its tempo or time signature further
down (`SongTiming`: which change, and the tempo and the signature its line shows, from `timingIndexAt` over the stops
and `SongStepper.headedOffset` — a step's or a fling's target, never a finger still dragging; a song paged back to is
put at its end first, so it reports its last stretch; another stretch moves the click like another song, while the same
stretch at a scaled tempo is applied from the next beat, and the panel's beat row and the app bar's tempo follow it), or
`Standalone`, the Metronome tab's own pattern (`metronomePatternOf`, in `MetronomePatterns.kt`: a song's tempo and
`{time}`, 4/4 where it names none, the accents drawn for that signature in any context, everything else from the
settings).

Only the top of the stack is asked, since a click does not outlive the screen it is played from. One collector in the
view model's `init` follows the context, the tempos, the settings and the library: a song paged to moves the click to it
once the bar being played has ended (`Metronome.update`'s `fromNextBar`), from beat one, so the band finishes the bar it
is in; anything else — a tempo stepped, a setting — is applied from the next beat, and the first value is only remembered, so a view model
built again over a playing click does not stop it. The last collector of `init` tells the engine whether the Metronome
tab or a song is on top with the feature on (`isMetronomeStartable`, the only screens whose taps may open the web's
audio device, `Metronome.setStartable`, a no-op elsewhere). `toggleMetronome` starts the context's pattern or stops it,
and starting one over a song opens that screen's panel with it. A click that stops on its own is a
`Message.MetronomeStopped` snackbar naming why.

Space (on the tab) and M (on a song) toggle it on the desktop and the web (`toggleMetronomeByKey`), under no dialog or
menu, only on the first press of a held key. **Three rules stop a click outright**, and between them they are the whole
of the lifecycle: `updateBackStack`, whenever what is on top is neither a song nor the Metronome tab - the editor opened
over a song, a song closed or deleted, a tab selected - so that nothing plays under a screen with no way to stop it;
`setVisibleDialog`, as the export screen or the tuner sheet is dealt in over the song, for the same reason (and, for the
sheet, because it listens through the audio the click plays in), since each is a dialog to the view model and never
reaches the back stack; and `onCleared`, which is the app being left rather than being sent to the
background (a finished Activity rather than a paused one; an Android activity destroyed without finishing, as "Don't
keep activities" does on every trip to the background, keeps a click that can sound), where a singleton metronome would
otherwise go on clicking, with its notification, under a process nobody is looking at.

### The song details screen's metronome

**The song details screen's metronome** (`MetronomePanel.kt`, which the Metronome tab pins at its top too) is a panel
inside that screen's own app bar, under the title row (`CampfireTopAppBar`'s `bottomContent`, the way the editor's
toolbar is part of its bar), which grows and shrinks the bar as it comes and goes — never over its controls: they fade
and squash towards the top on the bar's own timing (the beat row vertically, the play button whole), so they always fit
the room the bar has. Being inside the screen is the whole design: it is laid out, pushed and popped with it, covers
none of the song, and no scaffold wraps the app to make room for it. It holds a small `BeatRow` of the bar the click
counts, taking the row's width — each block lit in the full second accent color as its beat is heard and swelling a
little with it, since in the light theme an accent at rest is already half that color and the color alone is a faint
change — and play and stop at the end of it (`PlayStopMark`, morphing while it turns a quarter
turn clockwise either way, easing in, in a `FilledIconButton`) — at the end because the phone is held there, and the row
it would otherwise push aside is the panel's content — and nothing else: the tempo is a line below it in the song's own
first section, where it is both shown and set, and the sound, the subdivision and the volume are the tab's.

The **accents are tapped here as they are there**, the same `BeatRow` writing the same `withBeatLevels` for the
signature on screen, since they belong to the bar rather than to the screen somebody is on and two rows that disagreed
about 4/4 would be two settings. Each beat's whole column takes the press, not the block drawn in it, which in this row
is a sliver at a muted beat. It reads no tempo at all, only the time signature the row's blocks are drawn for
(`metronomeTimeSignatureOf`, tested, which its two screens feed through `screens/MetronomePanelState.kt`, the panel
itself taking that state and two callbacks rather than the view model). `MetronomeSettings.isSongPanelShown` is what
keeps it up: written by the app bar's button and by a click started here (`CampfireViewModel.toggleMetronomePanel`),
left alone when the screen is left, so the next song opens with the instrument the user last chose - and the next launch
too, since it is saved with the rest of the metronome's settings.

## The click in the app

### A later `{tempo}` or `{time}` is a change from where it stands

**A later `{tempo}` or `{time}` is a change from where it stands** (`ChordProBlock.Timing`), and the page is what says
where the band is: on the song details screen a change starts a page of its own (one written before the song's first
line stands on the first page, played from there), headed by one read only line naming the tempo and the time signature
from there on as the click plays them, and a playing click follows the page being read — the one a step or a fling is
headed for, never one a finger is still dragging past — from beat one of the next bar, the panel's beat row and the app
bar's tempo with it once the bar being played has ended. **Until then the screen says what is heard**: the beat row
draws the bar heard (`Playing.pattern`), and the app bar's tempo reads `96 → 120 BPM`, pulsing (`PendingTempo.kt`,
tested; only where the tempo changes), a tap on it starting the new song or stretch on the next beat
(`applyPendingMetronomeChange`) for a band that has already stopped. A change inside a section cuts it there, the rest heading the new page with its fold toggle alone; a recalled chorus
is played in whatever is in force where it is recalled.

The stepper, a setlist's entry and the library's override still hold one number, the song's opening tempo, and a later
tempo keeps its ratio to the file's opening one (120 → 60, played at 110 → 55), so nothing new is stored. A song that
fits one screen is still cut into pages by a change, since the page is the signal; a songbook of more than 200 sections
that changes its tempo or time is one column whatever the width, the click following the change scrolled past. The
editor offers Tempo and Time signature again and again — into the header first, at the start of the caret's line after
that — and the preview shows each change in place and is never paged; the PDF prints it as a line kept with what follows
it. **With the Metronome feature off none of it exists**: no line, no forced page, the song laid out as if it had none.
A `{key}` further down is still only read past.

### The click belongs to the screen it is played from, and there are two of them

**The click belongs to the screen it is played from, and there are two of them**: the Metronome tab, whose whole screen
is the instrument, and the song details screen, where it is a panel in the app bar. Nowhere else has a metronome, and a
click never outlives the screen it was started on - going back to the songs, selecting a tab, opening the editor, the
export screen or the tuner sheet over the song, deleting it, a song opened over the tab or over another song (an "Open
with", an import's Open), all stop it - so there is never a click playing with nothing on screen to stop it with. On a song details screen
it follows the page the pager is heading for, so paging to the next song moves the click to its tempo from the next bar.
Every way onto the tab clears the back stack.

### Both are played from the same panel

**Both are played from the same panel** (`MetronomePanel`): the least of a metronome that is still one — the bar as it
is heard, with its accents tapped on it, since the accents are the bar's rather than one screen's, and play and stop at
the end of the row. On the **song details screen** it is inside the app bar, under the title row, because a song is what
that screen is for and the tempo is already in the song's own first section a line below it; the bar's own button shows
and hides it, opening it starts nothing, stopping the click leaves it up for the next one, and closing it stops a click
that is playing. **Whether it is up is a preference** (`MetronomeSettings.isSongPanelShown`) rather than something each
screen is asked for again, so a player who reads to a click finds the instrument on the next song and on the next
launch.

On the **Metronome tab** the same panel is pinned at the top and never hidden, drawn larger there (a 56dp row and
button), so a page longer than the screen never has to be scrolled to stop a click, and under it the rest of the
instrument scrolls as the rows of a settings page, in two sections a wide window sets side by side: what is played — the
tempo on the song details screen's own stepper with its Tap segment, its Italian marking and a slider across the range,
the time signature (chips, and two steppers with a slash between them, under a line saying that the bar above is tapped
to accent or mute a beat) and the subdivision as a segmented row of the clicks per beat — and, on a card since it holds
for a song's click too, how it reaches the player: the sound as chips, the volume, and the animate and vibrate switches.

### Performance mode keeps the play button

Performance mode keeps the play button, and the panel has no tempo stepper to hide; the song's own line of text says the
tempo there, as it says the transposition. The tab stays fully usable. Settings (sound, subdivision, accents per
signature, volume, flash, vibrate) are `UserPreferences.metronomeSettings`; there is no mute of its own, since a volume
of zero leaves the click running with nothing sounding — on screen, for the flash and the haptics.
