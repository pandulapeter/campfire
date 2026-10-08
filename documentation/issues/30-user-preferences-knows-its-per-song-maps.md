# Give UserPreferences withSongRenamed(from, to) and withoutSongOverrides(), so the per-song maps are listed in one place

**Kind:** architecture  ·  **Severity:** low  ·  **Effort:** S  ·  **Risk:** low  ·  **Platforms:** all
**Files:** `data/model/src/commonMain/.../data/model/domain/UserPreferences.kt` (two new members); `domain/implementation/src/commonMain/.../useCases/SongReferences.kt` (the `updateUserPreferences` block and the private `movedTo`); `domain/implementation/src/commonMain/.../useCases/DeleteLibraryUseCaseImpl.kt` (`preferences.copy(transpositions = emptyMap(), tempos = emptyMap(), capos = emptyMap(), foldedSections = emptyMap())`); tests: new `data/model/src/commonTest/.../domain/UserPreferencesSongMapsTest.kt` (needs the `commonTest` source set plan 29 adds; otherwise put it in `domain/implementation/src/commonTest`), existing `DeleteLibraryUseCaseImplTest`, rename/delete tests in `domain/implementation`; `data/model/CLAUDE.md`
**Depends on:** none (shares the `:data:model` test source set with 29; whichever lands first adds it)

## Problem

`UserPreferences` has four maps keyed by a song's file name — `transpositions`, `tempos`, `capos`, `foldedSections` —
and the two operations that must touch all of them list them by hand, each in a different module file:

```kotlin
// SongReferences.kt, when a song is renamed (newFileName) or deleted (null)
preferences.copy(
    transpositions = preferences.transpositions.movedTo(fileName, newFileName),
    tempos = preferences.tempos.movedTo(fileName, newFileName),
    capos = preferences.capos.movedTo(fileName, newFileName),
    foldedSections = preferences.foldedSections.movedTo(fileName, newFileName),
)
```

```kotlin
// DeleteLibraryUseCaseImpl
preferences.copy(transpositions = emptyMap(), tempos = emptyMap(), capos = emptyMap(), foldedSections = emptyMap())
```

The fourth map (`capos`) was added after the first three; the next per-song preference (the metronome and chord work
keep adding them) has to be remembered in both places, and forgetting one leaves an override that follows a deleted
song's name onto the next song written under it. The type that owns the maps is the natural place for "which maps
are per song".

Out of scope, on purpose: `SyncedPreferences.of`/`applyTo` in `:data:repository:implementation` also list three of these
maps, but that is the *synced subset* (no `foldedSections`, plus `chordVoicings`), a different question that should
keep listing its fields explicitly. `demoLibraryContentHashes` is keyed by library path and is not a per-song override.

## Fix

1. In `UserPreferences` (data class in `:data:model`):

   ```kotlin
   /**
    * These preferences with everything kept for the song filed as [fileName] moved to [newFileName], or dropped where
    * that is null - every map keyed by a song's file name, so that a new one is added here and nowhere else.
    */
   fun withSongRenamed(fileName: String, newFileName: String?): UserPreferences = copy(
       transpositions = transpositions.movedTo(fileName, newFileName),
       tempos = tempos.movedTo(fileName, newFileName),
       capos = capos.movedTo(fileName, newFileName),
       foldedSections = foldedSections.movedTo(fileName, newFileName),
   )

   /** These preferences with nothing kept for any song, for a library that has been emptied. */
   fun withoutSongOverrides(): UserPreferences = copy(transpositions = emptyMap(), tempos = emptyMap(), capos = emptyMap(), foldedSections = emptyMap())
   ```

   with the private `movedTo` moved verbatim from `SongReferences.kt` (same semantics: an absent key returns the map
   unchanged, so an unchanged map stays `==` and the transform "changes nothing" as `transformAndWriteData` expects).
   Put the two next to the fields' KDoc, and add a sentence to the class KDoc: "every map keyed by a song's file name is
   listed in `withSongRenamed` and `withoutSongOverrides`".
2. `SongReferences.kt`: `userPreferencesRepository.updateUserPreferences { it.withSongRenamed(fileName, newFileName) }`;
   delete `movedTo`.
3. `DeleteLibraryUseCaseImpl`: `updateUserPreferences { it.withoutSongOverrides() }`.

One commit.

## Tests

- New `UserPreferencesSongMapsTest`: a song with an entry in all four maps is moved in all four; a deletion drops it from
  all four; a rename of a song with no entries returns an equal object (`==`, so no write happens); an existing entry
  under the new name is overwritten by the moved one (current `movedTo` behaviour: `this - fileName + (new to value)`);
  `withoutSongOverrides` empties the four and keeps `chordVoicings`, `demoLibraryContentHashes` and every other field.
- Guards: `DeleteLibraryUseCaseImplTest`, the rename/delete reference tests in `:domain:implementation`.

## Manual check

none — covered by tests
