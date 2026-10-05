# Bring the KDocs and CLAUDE.md files up to date with synced overrides, the feature switches and the Song defaults entry points

**Challenged:** amended — Fix 2 also names the About the song sheet's Song defaults group in the same `presentation/CLAUDE.md` sentence, which still gives the editing menu as the only way in (the stale claim items 5–7 fix in the KDocs); checked against 01: its `data/model/CLAUDE.md` edit ("its last `{capo}`") and root Metronome-bullet edit touch other clauses than 1 and 4 here.

**Decided (user, 2026-10-05):** point 7 — fix the KDoc; overrides are not cleared.

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `data/model/CLAUDE.md`, `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/UserPreferences.kt`,
`presentation/CLAUDE.md`, root `CLAUDE.md` (Web section only),
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SongPlayingDialog.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SongMetadataDialog.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongMetadataActions.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/ListItems.kt`

Lane D, **last** (after 30–37): 33, 34 and 36 touch `SongPlayingDialog.kt` / `presentation/CLAUDE.md` first. Lane B owns
root `CLAUDE.md`'s Sync "per-song overrides travel too" bullet and lane A the Metronome "first `{tempo}`" sentence — do
not edit those; lane A may also reword `data/model/CLAUDE.md`'s "its first `{tempo}` rounded, its first `{time}` as
written" clause, so leave that clause to lane A and only touch the clause below.

## Problem

Since f51b3cc6a the library's transpositions, tempos and capos sync, and since d7729a514 each feature switch hides its
values everywhere, the Song defaults sheet included; several comments and docs still say otherwise:

1. `data/model/CLAUDE.md`, `UserPreferences` entry: "the library-wide transpositions, tempos and capos (`tempos` and
   `capos`, song file name to BPM and to fret for songs opened from the library, never exported or synced)" — they are
   synced (the cloud folder's `preferences.json`); `UserPreferences.kt`'s own KDocs already say "never exported but synced".
2. `presentation/CLAUDE.md`, the Song defaults sheet in the `ui/dialogs/Dialogs.kt` entry: "or for the library (never
   "this device", since the preferences may be synced one day)" — they are synced now.
3. `UserPreferences.kt`, `isMetronomeEnabled`: "The Song defaults sheet still edits both, since that is the file." — the
   sheet leaves the tempo and time fields out with the Metronome off (`SongPlayingDialog`: "Each field goes with its
   feature … so a reader who switched one off is not asked about it here either"; `if (shouldShowTempo)`).
4. Root `CLAUDE.md`, Web section, the address list (`/` is the songs, then `search`, `setlists`, `setlists/search`,
   `settings/{…}`, …) lacks `metronome`, which `BrowserRoutes.kt` (wasmJsMain) writes and reads
   (`private const val METRONOME = "metronome"`).
5. `SongMetadataDialog.kt` KDoc: the Song defaults sheet is "opened from the song's first section, where the controls
   overriding them are" — it is opened from the song details editing menu (`songPlayingAction`) and from the About the
   song sheet's Song defaults group (`Dialogs.kt`, `onEdit = … viewModel.showSongPlayingDialog(…)`), not from the page.
6. `SongMetadataActions.kt`, `songPlayingAction` KDoc: "from the song details editing menu, the one way into it … what
   [setlistFileName] (or this device) overrides" — the About sheet's group is a second way in, and the library's
   overrides are not this device's alone.
7. `SongPlayingDialog.kt`:
   - class KDoc "opened from the song details editing menu" — add the About the song sheet's Song defaults group;
   - class KDoc "Values set here that match an override make it no override at all, since an override equal to the file's
     value is none." — true only for display: `effectiveTempo` hides an override equal to the file's value
     (`takeIf { it != songBpm }`) but it stays stored (and synced) and shows again once the file's value moves away.
     (The capo is not even hidden: `effectiveCapo` keeps an override equal to the file's fret, only `isDefault` reads it
     as none.) See the decision below.
   - `songPlayingOverrides` KDoc "or in the library on this device where it is null" — the library's are synced.
8. "Lyrics only mode" survives the rename to the Chords switch (`areChordsEnabled`):
   - `ListItems.kt`: `@param shouldShowChords False under lyrics only mode…`, `@param duration … even in lyrics only mode`,
     and the KDoc near `SongCardNote` "lyrics only mode takes the key away altogether";
   - `SongDetailsScreen.kt`: the comment "in lyrics only mode too, since the singer is timed by it alike" and the KDoc
     "since lyrics only mode takes the key away altogether";
   - `presentation/CLAUDE.md`: "lyrics only mode empties the key's place altogether", "Lyrics only mode empties the place
     altogether — … the way the mode takes the transposition control off the viewer", "The editor's preview folds nothing,
     for the reason lyrics only mode does not reach it", "Lyrics only mode drops both altogether", "lyrics only mode leaves
     the key out as it does on a card", "Lyrics only mode is the one preference that does not reach the preview at all…"
     (this last one as plan 36 left it). The "Lyrics only" *marker* on a chordless song's card is a real string and stays.

## Fix

Edit the comments and docs only; no code changes.

1. `data/model/CLAUDE.md`: "never exported or synced" → "never exported, but synced through the cloud folder's
   `preferences.json` (see the root Sync section)".
2. `presentation/CLAUDE.md`: "(never "this device", since the preferences may be synced one day)" → "(never "this
   device", since the library's overrides are synced to every device on the account)". In the same sentence, "opened from the song details
   editing menu's "Edit song defaults", after Edit song details (`songPlayingAction`)" gains "and from the About the song
   sheet's Song defaults group" — the second way in, as items 5–7 say in the KDocs.
3. `UserPreferences.isMetronomeEnabled` KDoc: replace the last sentence with "The Song defaults sheet leaves them out
   too; the file keeps them."
4. Root `CLAUDE.md` Web: insert `metronome` after `setlists/search` in the address list.
5. `SongMetadataDialog.kt`: "opened from the song's first section, where the controls overriding them are" → "opened
   from the song details editing menu and from the About the song sheet's Song defaults group".
6. `songPlayingAction` KDoc: "Opens the "Song defaults" sheet from the song details editing menu (the About the song
   sheet's Song defaults group opens it too): what the file declares … next to what [setlistFileName] (or the library)
   overrides of them."
7. `SongPlayingDialog.kt`: add the About sheet group as an entry point; `songPlayingOverrides`: "or in the library where it
   is null"; and for the override sentence, per the decision:
   - **Option KDoc (recommended):** "An override equal to a value set here is no longer shown or named, since it plays
     what the file says; it is kept, so a setlist that was told 100 still says 100 if the file later moves to 110."
   - **Option code:** clear the matching overrides in `CampfireViewModel.setSongPlaying` (a setlist write per setlist
     read through, or a preferences write) — then this plan's sentence becomes "…clears it". Not recommended: it writes
     setlist files as a side effect of editing a song file, and the setlist's choice is the band's.
8. Replace "lyrics only mode" with "the chords switched off" / "with the Chords switch off" in the places listed, keeping
   each sentence's reasoning.

## Tests

None: comments and documentation only.

## Manual check

None.
