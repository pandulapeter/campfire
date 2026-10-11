# Correct ui/CLAUDE.md on what the editor's preview shows for the song defaults and on where Edit song file sits in the menu

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CLAUDE.md`

## Problem

Two passages of `ui/CLAUDE.md` at d650f3cb0 no longer match the code:

1. ~L397–401, under "How a song is played":

   > **Read only mode reads them instead**:
   > performance mode and a song opened from an archived setlist get the same four as one line of accent colored text — which
   > always names the capo, the tempo and the time signature there, "Capo 0" and the click's 120 BPM and 4/4 where the file
   > says nothing, since with
   > no control left on the page an absent value would read as an unknown one — as does the editor's preview, where the text
   > being typed is what says them.

   Since dc0c7de4e the editor's preview draws no such line: `SongMetadataSection` draws only `SongInfoCard` where
   `isSongInfoShown` (`if (isSongInfoShown) SongInfoCard(...) else if (playingControls == null) SongPlayingMetadata(...)`),
   and the card shows the four as its Song defaults tiles (`SongInfoCard.kt`:
   `tempo = ChordProTempo.parse(metadata.tempo) ?: MetronomePattern.DEFAULT_BPM`,
   `time = … ?: TimeSignature.COMMON_TIME.toString()`, but `capo = metadata.capo`). The preview passes no `readsDefaults`
   (`SongPreview.kt` leaves it at `false`), so it never says "Capo 0" for a text that writes no capo; it shows the 120 BPM
   and 4/4 the click starts at, and the capo only where the text writes one. The read only claims themselves hold
   (`SongMetadataSection`: `metadata.capo?.takeIf { readsDefaults || it != 0 }`, `song_details_capo` is "Capo %1$d").
   The sentence is also missing two serial commas.

2. ~L366–367:

   > No menu lists the sheet's editors one by one; the song details screen's Edit song file is
   > the first entry of its overflow menu, and cover art editing follows the cover art setting.

   `SongDetailsAppBar` hands `SongActions` `leadingItems = listOfNotNull(setlistAssignmentsAction(...) or null, tunerAction)`,
   and `SongActions` puts `leadingItems + editItems + …`, so Choose setlists (where the bar has no room for its button) and
   the tuner (where the chords and the metronome are both off) come before Edit song file. `ui/components/CLAUDE.md`
   (~L159) already says it right: "Edit song file first among its own entries".

## Fix

Match each by its text:

1. Replace the whole sentence from "performance mode and a song opened from an archived setlist get the same four" to
   "being typed is what says them." with:

   > performance mode and a song opened from an archived setlist get the same four as one line of accent colored text,
   > which always names the capo, the tempo, and the time signature there ("Capo 0", the click's 120 BPM, and 4/4 where the
   > file says nothing), since with no control left on the page an absent value would read as an unknown one. The editor's
   > preview has no line: its card's Song defaults tiles show the four as the text being typed says them, with the
   > click's 120 BPM and 4/4 where it names no tempo or time, and a capo only where the text writes one.

   (Plan 46 leaves this sentence to this plan.)
2. Replace "the song details screen's Edit song file is the first entry of its overflow menu" with "the song details
   screen's Edit song file is the first of its overflow menu's own entries, after Choose setlists and the tuner where the
   menu holds them".

## Tests

None: documentation only.

## Manual check

None.
