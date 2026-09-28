# Apply preference edits to the repository's latest state

**Kind:** bug · **Severity:** medium (P2) · **Platforms:** all
**Reviewed at:** `b8cc0bc2`
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`, `data/repository/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/api/UserPreferencesRepository.kt`, `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/UserPreferencesRepositoryImpl.kt`, preference use cases and callers.

## Problem

`CampfireViewModel.updateUserPreferences` (2411–2412) captures `userPreferences.value` and saves a whole copy.
That value is two asynchronous `stateIn` projections away from the repository (438–440). Publishing a preference
immediately in `BaseLocalDataRepository.writeData` does not synchronously update those projections. Two actions
before the collectors catch up therefore build on the same old preferences; the second replaces the first.

This also affects the font-scale save (1024–1028), section folds (2315–2318), and library transposition
(1824–1837). The comment claiming the previous transposition tap is already in `userPreferences` is not guaranteed.
A queued shortcut burst can lose steps; a font-scale save and another setting can revert one another. A mutex
around disk writes cannot repair the already-stale whole document passed to it.

## Evidence / reproduction

A temporary desktop test used the real `UserPreferencesRepositoryImpl`, an in-memory local source, and the same
two `stateIn` projections. After initial collection, it saved `lyricsOnly = true` and `horizontalFlow = true`
from the projected value without running the collectors between them. The persisted result was
`lyricsOnly = false, horizontalFlow = true`. This is a deterministic reproduction of the state-flow wiring,
not a UI automation test; slow input processing or queued events determine when users encounter it.

## Fix

1. Add a repository operation accepting `UserPreferences.() -> UserPreferences`. Apply it atomically to the
   latest repository value and publish that result before persisting it. Keep disk persistence serialized and
   coalesced. Do not hold a disk-write lock merely to make an optimistic UI change visible.
2. Pass transforms through a domain use case. Migrate UI toggles, fold changes, transposition steps, font-scale
   persistence, and song-reference preference updates. Reserve whole-document replacement for initialization
   or an explicit restore.
3. Make transforms pure if they can be retried by a state-flow atomic update. Preserve read-failure handling.

## Verification

- Hold collectors back while applying two different fields; both must persist.
- Apply five transposition steps and two independent folds before collection catches up; none may be lost.
- Delay a disk write and change font scale plus a setting; the final disk and UI values must agree.
- Run repository/domain desktop tests and exercise key-repeat on the transposition controls.

## Conflicts

Touches the ViewModel also changed by 08/09. Implement the repository contract first; update all callers together.
