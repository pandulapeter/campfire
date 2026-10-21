# Campfire promo (updated): cue sheet

The backing track (campfire-promo-backing-track.wav, already in the three videos) is synthesized by
tools/promo-video/music/soundtrack.py in the Campfire repository from this sheet (its docstring has the chords), so its hits are where the table says.

1080 × 1920, 60 fps, 30.0 s (1800 frames). 160 BPM in 4/4: a beat is 0.375 s, a bar is 1.5 s. The video is exactly
20 bars: the first frame is bar 1's downbeat, and the last chord rings through bar 20. Bar N starts at (N − 1) × 1.5 s.
Each scene's exit runs about a quarter of a second into the next, under its devices coming in.

| Bar | Time | On screen | What the music does |
|-----|------|-----------|---------------------|
| 1 | 0:00.0 | The song list's screen filling the frame from the first frame, the phone snapping back into place by b2, where the icon lands above it, "Campfire" letters just after | A ringing: impact, crashes, a rising arpeggio |
| 2 | 0:01.5 | The list scrolls; "Lyrics." · "Chords." · "Wherever you play.", one a beat, held still on b4 | A, G, D/F♯, G: four stabs, the third the big one |
| 3–4½ | 0:03.0 | The phone rises, "Your whole songbook, beautifully organized." under it; the search opens on b1, the keyboard slides up, "h" "o" "u" typed on b2–b4, House of the Rising Sun on top; "Colored tags", "Language filters", "Cover art" with the letters | Groove on A (D on bar 4 b4); a pluck per chip |
| 4½–5 | 0:05.25 | iPad zooms in on bar 4 b3, its song open; "Every chord, with diagrams" on bar 5 b1, "Scale suggestions" b2; page 2 and "Pedal & tap to turn pages" on b3 | Groove; a pluck on each chip; a tom run from the page turn into bar 6 |
| 6 | 0:07.5 | Live: phone from the left; Country Roads transposed A → A♯ → B → C (+1 on b2, +2, +3) | Hard hits on every beat, the chord on screen |
| 7 | 0:09.0 | Live: +4 to C♯ on b1, then Do♯ (Latin), I (Roman), 1 (Nashville), one a beat | Four hits on C♯ |
| 8–9 | 0:10.5 | Metronome playing at 160 BPM; "Tap tempo" on bar 8 b4, "Count-ins" on bar 9 b2 | The app's own click; half time on D, G on bar 9 b4; plucks on the chips |
| 10 | 0:13.5 | Tuner from the top; "Instrument tuner" b2, "Chromatic tuner" b3, "Alternate tunings" b4; hears A, flat | Sparse: bass, soft pad, ride on A; plucks on the chips |
| 11 | 0:15.0 | The A string in tune on the downbeat; the Chords tab zooms in on b3, on the keyboard, "Chord diagrams for your instrument."; Keyboard (b3) | A A A G: impact; a crash on b3; a pluck on the chip |
| 12 | 0:16.5 | Live: the diagrams turn to the ukulele on b1 and stay; Ukulele b1, Guitar b2, Mandolin b3, Banjo b4 | Groove on D; a crash on b1; a pluck per chip |
| 13 | 0:18.0 | Live: the Scales tab from the right, in A on a guitar; A minor pentatonic tapped on b2, its sheet up over the tab by b3; "Any instrument & tuning" b3, "Notes & intervals" b4 | D D D G: a crash under the phone and one on the tap; a fill into the E; a pluck on each chip |
| 14 | 0:19.5 | One thing a beat: the laptop from the top (b1), the light theme sweeping across it over a beat and a half (b2), the upright iPad from the left (b3), the phone from the right (b4) | E: a crash under the laptop, the whoosh alone under the sweep, a crash and a rim shot under the iPad and the phone |
| 15 | 0:21.0 | Platform names, one an eighth note | E E E G: octave bass, high arpeggio, a fill into the A |
| 16 | 0:22.5 | The setlist phone from the right; "Drag to reorder" on b2 as Ring of Fire is lifted; dropped in second place on b4, "Countdown" with it | A: the opening's groove, a crash on the downbeat, a tom run into the drop |
| 17 | 0:24.0 | Live: a chord sheet pasted on the Songs screen on b1, the editor opening on it as ChordPro; "Paste from websites", "PDF & Word", "Other songbook apps", "Zip archives", one a beat | A G D/F♯ G: the groove, a crash on the downbeat, a pluck on each chip |
| 18 | 0:25.5 | Export phone from the left; "Whole setlists" b2, "Individual songs" b3; the PDF swiped to its second page on b2, back on b4 | Groove on A on the ride; plucks on the chips; a snare build |
| 19 | 0:27.0 | "Free." "No ads." "No account." "Offline." | Bar 2's four stabs again, A, G, D/F♯, G, one per word, a line climbing over them |
| 20 | 0:28.5 | The icon and "Campfire" on 1, the URL on 2, the subtitle on 3, held to the end | A, ringing out and fading over the last second |

The chord chart is the owner's, written for guitars recorded over it; the bass may be replaced too. Compared with the
previous track the chart is the same; the setlist is a bar shorter, its drop and tom run on bar 16's fourth beat, and
bar 17 shows songs imported; chips came to the library's search (on its letters' plucks) and the Scales tab (bar 13's
last two beats, with plucks).

Putting a track of your own on a video (WAV/AIFF/MP3, starting with the video, on bar 1's downbeat):

    ffmpeg -i campfire-promo-vertical-9x16.mp4 -i track.wav -map 0:v -map 1:a -c:v copy -c:a aac -b:a 256k -shortest out.mp4

Regenerating anything: see tools/promo-video/CLAUDE.md and regenerate.sh in the Campfire repository.
