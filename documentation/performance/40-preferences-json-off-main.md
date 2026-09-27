<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 40 — Preferences JSON off the main thread

| | |
|---|---|
| Lane | E |
| Impact | low-medium (grows with the saved transpositions and folded sections) |
| Confidence | high |
| Platforms | Android, iOS, desktop |
| Files | `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/source/UserPreferencesLocalSourceImpl.kt` |
| Depends on / conflicts with | — |
| Commit message | `Decode and encode the preferences on a background thread.` |

## Problem
**Where the JSON work happens:**
- `UserPreferencesLocalSourceImpl.kt:34-39` (`loadUserPreferences`): `UserPreferencesDocumentFormat.decode(text)` and `.toModel()`.
- `UserPreferencesLocalSourceImpl.kt:41-45` (`saveUserPreferences`): `UserPreferencesDocumentFormat.encode(userPreferences.toDocument())`.
- The JSON is written with `prettyPrint = true` (`UserPreferencesDocumentFormat.kt:39`).
- None of it switches dispatcher. The song and setlist local sources both move to `Dispatchers.Default` for exactly this reason.

**Callers run on Main.**
- The start-up read comes through `LoadScreenDataUseCase` in `viewModelScope`.
- The writes come through `BaseLocalDataRepository.writeData` → `persist(latestData)` (line 134), called from `viewModelScope`:
  - `CampfireViewModel.kt:2139-2140` (`updateUserPreferences`);
  - 1572 (every transposition step);
  - `toggleSectionFold` (2059);
  - the font scale save (947).

**What grows.** The document holds `transpositions: Map<String, Int>` and `foldedSections: Map<String, Set<String>>`, both keyed by song. With hundreds of transposed songs, each tap on a transpose button pretty-prints the whole document on the main thread before the IO hop. Writes are already coalesced (`writeMutex`), so the cost left is the JSON work itself.

## Fix
```kotlin
override suspend fun loadUserPreferences(): UserPreferences {
    val text = fileStorage.readText(StorageDirectory.PREFERENCES, FILE_NAME) ?: return UserPreferencesDocument().toModel()
    val decoded = withContext(Dispatchers.Default) { UserPreferencesDocumentFormat.decode(text) }
    if (!decoded.isIntact && text.isNotBlank()) keepUnreadableDocument()
    return withContext(Dispatchers.Default) { decoded.document.toModel() }   // or fold into the block above
}
override suspend fun saveUserPreferences(userPreferences: UserPreferences) = fileStorage.writeText(
    directory = StorageDirectory.PREFERENCES,
    name = FILE_NAME,
    text = withContext(Dispatchers.Default) { UserPreferencesDocumentFormat.encode(userPreferences.toDocument()) },
)
```

**Keep `prettyPrint`.** `data/source/local/implementation/CLAUDE.md` (the `model/ + mapper/` bullet) describes the preferences as a document a user may edit by hand on iOS and desktop, and the format reads field by field so that a hand edit costs only the broken field. It is meant to stay human-readable.

**Must not change:** the `.bad` copy, the field-by-field fallback, and `BaseLocalDataRepository`'s write ordering. The write still happens inside `persist`, under `writeMutex`.

## Verification
- Tests: `./gradlew :data:source:local:implementation:desktopTest`, where `UserPreferencesLocalSourceTest` covers decode, fallback and the `.bad` copy, and `./gradlew :data:repository:implementation:desktopTest` for `BaseLocalDataRepositoryTest`.
- **Manual check:** Android with a `preferences.json` holding about 1000 transpositions (hand-made). Tap transpose repeatedly with GPU profiling bars on: no main-thread spike per tap. Check `preferences.json` is still pretty-printed.
