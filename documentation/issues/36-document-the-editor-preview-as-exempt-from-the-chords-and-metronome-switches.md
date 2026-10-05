# Document that the editor preview's playing line names the key, capo, tempo and time whatever the Chords and Metronome switches

**Decided (user, 2026-10-05):** option B — keep the preview as it is and only document the exemption in root `CLAUDE.md` and `presentation/CLAUDE.md`; no code change, no change to `SongEditorScreen.kt` or `SongLyrics.kt`. Commit as a docs fix.

**Kind:** bug (consistency) · decision  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songEditor/SongEditorScreen.kt` (`SongPreview`),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt` (`SongLyrics`),
`presentation/CLAUDE.md` (the sentence "Lyrics only mode is the one preference that does not reach the preview at all…"),
root `CLAUDE.md` (Conventions, "How a song is played" bullet, the sentence about the editor's preview — only for option B)

Lane D: apply after 35 (which changes `withMetadataSection`, called from `SongLyrics`). Plan 38 rewrites the
"lyrics only mode" wording in `presentation/CLAUDE.md`; apply 36 first and let 38 reword what is left.

## Problem

Root `CLAUDE.md` (Features): Chords off takes "with the chords the key, the transposition and the capo wherever a song is
read", Metronome off takes "the tempo and the time signature wherever a song is read", and "what it takes away is gone
everywhere". The editor's preview draws the same first section as the song details screen (its card and the playing line,
`isSongInfoShown = true`), but ignores both switches:

```kotlin
// SongEditorScreen.kt, SongPreview
fun inputsOf(text: String, transposition: Int, spelling: UserPreferences.ChordSpelling) = SongLyricsInputs(
    ...
    shouldShowChords = true,
    ...
)
...
SongLyrics(
    ...
    isSongInfoShown = true,
    songInfoEditing = songInfoEditing,
)   // no shouldShowTempo, so its default `true`
```

`SongLyrics` feeds `model.shouldShowChords` into `withMetadataSection(shouldShowChords = …)` and its own `shouldShowTempo`
(default `true`) into `shouldShowTempo`. So with the Metronome switched off the preview still reads "120 BPM • 4/4", and
with the Chords off "G • Capo 2", although no other screen shows them. The chords themselves are kept on purpose
(comment: "chords typed into the field opposite it have to appear, or the editor would answer an edit with nothing"), and
that reason is about chord brackets in the lyrics, not about the playing line.

## Fix — decision

- **A (recommended):** the switches reach the preview's playing line, not its lyrics. Give `SongLyrics` a parameter
  `shouldShowKeyAndCapo: Boolean = model.shouldShowChords` used for `withMetadataSection(shouldShowChords = …)` (and in the
  `remember` keys and `shownPlayingControls` condition in place of `model.shouldShowChords`), leaving `model.shouldShowChords`
  for the chords of the lyrics. In `SongPreview`, read `userPreferences` (already collected there) and pass
  `shouldShowKeyAndCapo = userPreferences?.areChordsEnabled != false` and
  `shouldShowTempo = userPreferences?.isMetronomeEnabled != false`. The song details screen passes nothing new and keeps
  its behaviour. Reword the `presentation/CLAUDE.md` sentence: the Chords switch does not reach the preview's chords (same
  reason as now), but the playing line follows both switches like every other place a song is read.
- **B:** keep the preview as it is and document the exemption: in root `CLAUDE.md`'s "How a song is played" bullet, after
  "as does the editor's preview, where the text being typed is what says them", add that the preview says them whatever
  the switches, since it shows what is being written; same in `presentation/CLAUDE.md`.

Default if nobody answers: **A** — it matches "gone everywhere" and the switches' purpose (a singer has no use for a
tempo), and the editor's text still shows every directive being typed.

## Tests

None for A beyond what `SongMetadataTest` already covers: `withMetadataSection(shouldShowChords = false / shouldShowTempo = false)`
is tested there; the change is which value the preview passes.

## Manual check

Settings → Features: switch the Metronome off, open a song with `{tempo: 96}` and `{time: 6/8}` in the editor: the
preview's line has no tempo or time. Switch the Chords off: the line has no key or capo, but the preview still shows the
chords in the lyrics. Switch both on again: everything returns.
