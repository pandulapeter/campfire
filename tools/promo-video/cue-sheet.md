# Campfire promo (updated): cue sheet

The backing track (campfire-promo-backing-track.wav, already in the three videos) is synthesized by
tools/promo-video/music/soundtrack.py in the Campfire repository from this sheet (its docstring has the chords), so its hits are where the table says.

1080 × 1920, 60 fps, 30.0 s (1800 frames). 160 BPM in 4/4: a beat is 0.375 s, a bar is 1.5 s. The video is exactly
20 bars: the first frame is bar 1's downbeat, and the last chord rings through bar 20. Bar N starts at (N − 1) × 1.5 s.
Each scene's exit runs about a quarter of a second into the next, under its devices coming in.

| Bar | Time | On screen | What the music does |
|-----|------|-----------|---------------------|
| 1 | 0:00.0 | Icon lands on beat 1, "Campfire" letters on 2 | Impact, crashes, a rising arpeggio |
| 2 | 0:01.5 | "Lyrics." · "Chords." · "Wherever you play.", one a beat, held still on b4 | Four stabs, the third the big one |
| 3–4½ | 0:03.0 | Song list scrolls; four colored tags pop, one a beat from bar 3 b3 | Groove starts; a chip pluck per tag |
| 4½–6½ | 0:05.25 | iPad zooms in on bar 4 b3, its song already open; "Every chord, with diagrams" on bar 5 b1; page 2 and "Pedal & tap to turn pages" on b3 | Groove; a chip pluck on each chip |
| 6½–7 | 0:08.25 | Live: setlist phone from the right on bar 6 b3; "Drag to reorder" on b4, "Countdown" on bar 7 b1; Ring of Fire dragged 5th → 2nd, dropped on bar 7 b4 | Reversed crash into the phone; plucks on the chips; the tom fill under the drop |
| 8 | 0:10.5 | Live: phone from the left; Country Roads transposed A → A♯ → B → C (+1 on b2, +2, +3) | Hard hits on every beat, the chord on screen |
| 9 | 0:12.0 | Live: +4 to C♯ on b1, then Do♯ (Latin), I (Roman), 1 (Nashville), one a beat | Four hits on C♯ |
| 10–11 | 0:13.5 | Metronome playing at 160 BPM; "Tap tempo" on bar 10 b4, "Count-ins" on bar 11 b2 | The app's own click; half time; plucks on the chips |
| 12 | 0:16.5 | Tuner from the top; "Instrument tuner" b2, "Chromatic tuner" b3, "Alternate tunings" b4; hears A, flat | Sparse: bass, soft pad, ride; plucks on the chips |
| 13 | 0:18.0 | The A string in tune on the downbeat; the Chords tab zooms in on b3, on the keyboard; a chip for every instrument with diagrams (the twelve-string is drawn as a guitar), one a beat: Keyboard (b3), Mandolin (b4) | Impact; a crash on b3; a pluck per chip |
| 14 | 0:19.5 | Live: Ukulele on b1, Guitar on b2 (the screen switching with them), Banjo b3; the A tapped (b2½), another shape picked (b4) | Crashes on b1 and on the pick; a pluck per chip |
| 15 | 0:21.0 | Live: the Scales tab from the right, in A on a guitar; A minor pentatonic tapped on b2, its sheet up over the tab by b3 | Groove on D; a crash under the phone and one on the tap |
| 16 | 0:22.5 | Export phone from the left; "Whole setlists" b2, "Individual songs" b3; the PDF swiped to its second page on b2, back on b4 | Groove on the ride; plucks on the chips; a fill into the light theme |
| 17 | 0:24.0 | One thing a beat: the laptop from the top (b1), the light theme sweeping across it over a beat and a half (b2), the upright iPad from the left (b3), the phone from the right (b4), the two centred under the laptop | A crash under the laptop, the whoosh alone under the sweep, a crash and a rim shot under the iPad and the phone |
| 18 | 0:25.5 | Platform names cascade | Octave bass, high arpeggio |
| 19 | 0:27.0 | Back to dark: "Free." "No ads." "No account." "Offline." | Four stabs, one per beat |
| 20 | 0:28.5 | The icon and "Campfire" on 1, the URL on 2, the subtitle on 3, held to the end | Final chord, ringing out and fading over the last second |

Compared with the original track: no beat of silence before bar 1 and no extra half bar after bar 20; no riser into
bar 5 and no crash on its downbeat; the reversed crash into bar 5 now swells into bar 6 b3 (the setlist); the crash on
bar 14 b3 is on b4 (the shape pick); bar 15 is new, a bar of the D groove for the Scales tab, so the export, the light
theme, and the four promises are a bar later and the icon's bar of eighths and its tom roll is gone; bar 17's riser,
impact, reversed crash, and second crash are gone and its whoosh, longer, is on b2; and every chip (the tags included)
has a pluck of a chord note, in a stem of its own (`stems/chips.wav`). Everything else is the original's.

Putting a track of your own on a video (WAV/AIFF/MP3, starting with the video, on bar 1's downbeat):

    ffmpeg -i campfire-promo-vertical-9x16.mp4 -i track.wav -map 0:v -map 1:a -c:v copy -c:a aac -b:a 256k -shortest out.mp4

Regenerating anything: see tools/promo-video/CLAUDE.md and regenerate.sh in the Campfire repository.
