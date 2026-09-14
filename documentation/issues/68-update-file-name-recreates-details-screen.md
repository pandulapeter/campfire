# 68 · "Update file name" from the details screen recreates the screen

**Severity:** low (cosmetic) · **Area:** `:presentation` (`CampfireDestination`, `CampfireViewModel.updateSongFileName`)

`CampfireViewModel.kt:668–673` rewrites the `SongDetails` entry; its `contentKey` includes the file names, so Nav3
sees a new entry: the renamed song slides in over itself and the scroll position resets.

## Fix

Give `SongDetails` an identity that survives a rename: `val id: String = Uuid.random().toString()` as a constructor
property (default, so every push gets one), and `contentKey = "songDetails|$id"`. `copy(songFileNames = …)` keeps
the id, so the entry is the same to Nav3 and the pager just re-keys its pages. If issue 10 makes the destinations
serializable, the id serializes with them. `SongEditor`'s key is already stable.
