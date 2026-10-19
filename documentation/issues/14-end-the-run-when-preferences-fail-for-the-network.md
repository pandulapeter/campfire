<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# End the run with its reason when preferences.json fails for the network or a full folder

**Challenged:** sound

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Lane:** S
**Files:** `data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/scheduling/SyncRunner.kt`,
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/preferences/CLAUDE.md`,
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/SyncRepositoryImplTest.kt`

## Problem

`SyncRunner.synchronizePreferences` (`scheduling/SyncRunner.kt`) lets only an authorization failure through:

```kotlin
} catch (exception: CancellationException) {
    throw exception
} catch (exception: SyncAuthorizationException) {
    throw exception
} catch (exception: Exception) {
    environment.logger.log("Could not sync ${SyncedPreferencesDocument.FILE_NAME}: ${exception.message}")
    null
}
```

Its KDoc says "Only the failures that end a run end this one too", and `SyncNetworkException` and
`SyncRemoteStorageFullException` are `SyncRunEndingException`s just like the authorization one
(`data/source/remote/api/CLAUDE.md`: "the only failures that end a run"). Today they are swallowed. A connection lost
between the library and the document (or on the document's upload) reports `Success(havePreferencesFailed = true)`
rather than `Failure(NETWORK)`. Probe: `lastOutcome = Success(… havePreferencesFailed = true)`, and the stored
`lastSyncedAt` stays 0. The user sees "synced, but the preferences could not be" for what is really "offline", and plan
13's retry, which keys on `Failure(NETWORK)`, would not cover it.

## Fix

Replace the `SyncAuthorizationException` catch with `catch (exception: SyncRunEndingException) { throw exception }`.
`SyncRunEndingException` is already imported. Keep the `SyncAuthorizationException` import, because the outer `catch`
in `run` still checks for it.

What follows was checked against the code, and nothing else needs to change:

- The exception reaches `run`'s `catch (exception: Exception)`, which calls `finishRunCutShort(latestIndex)` under
  `NonCancellable` and sets `Failure(exception.toFailureReason())`, so NETWORK or REMOTE_STORAGE_FULL. A full folder
  on the document's upload thus ends the run the way a full folder on a library upload does, which is the consistent
  answer.
- **No library work is lost or redone.** In the `Completed` branch, `latestIndex` is `{ result.index }`, the engine's
  final index with every completed file operation, before preferences start. Once `SyncedPreferencesSync` has applied
  the folder's document here, `onLocalSettled` turns it into `{ result.index.copy(syncedPreferences = base) }`, with
  the base being the folder's document rather than the merged one. That is exactly the path the existing test
  `a run stopped while preferences go up keeps the folder's document as the base` (`SyncRunnerStoppedLateTest`) proves
  for a Stop. `finishRunCutShort` writes it `markedAsFinished()` and refreshes the lists.
- **`lastSyncedAt`** stays `result.index.lastSyncedAt`, the last run that was in step. That is also what a
  `havePreferencesFailed` run kept, since `SyncSummary.isComplete` is false for it. The failure branch doesn't touch
  the state's `lastSyncedAt`.
- What is given up: the `Failure` outcome carries no `SyncSummary`, so a conflict copy made by this run's library part
  is not named on screen. The copy is in the library under its name, and the next run doesn't name it either. The
  same is true of any network failure later in a library pass today, so this is accepted rather than worked around.

Update `synchronizePreferences`' KDoc to name the change: a failure that ends a run (refused, network, folder full)
ends this one with its reason, and anything else is the one document's problem.

`preferences/CLAUDE.md`, last bullet, becomes: "A value changed here during the merge is kept. A failure that ends a
run (`SyncRunEndingException`) ends this one with its reason, keeping the folder's document as the base once it was
applied here; any other failure sets `SyncSummary.havePreferencesFailed` and leaves the base unchanged."

## Tests

In `SyncRepositoryImplTest`, built like `a run stopped while preferences go up keeps the folder's document as the
base`: a library in step (one indexed song), a transposition set only here so the document must go up, and
`provider.onUploadDocument = { throw SyncNetworkException("Offline") }`.

- `preferences cut off by the network end the run as a network failure`: the outcome is `Failure(NETWORK)`. The stored
  index is not marked running, keeps the song's entry and `lastSyncedAt` as before, and holds the folder's document as
  `syncedPreferences`. Then `onUploadDocument = {}` and run again: `Success` with `havePreferencesFailed = false`, and
  the uploaded document has the transposition.
- `a full folder on preferences ends the run as that`: the same with `SyncRemoteStorageFullException` →
  `Failure(REMOTE_STORAGE_FULL)`.
- The existing `havePreferencesFailed` behaviour for a non-ending failure (the document contested on every attempt)
  stays covered by `SyncedPreferencesTest`. Keep one runner-level case that a plain `Exception` from the document's
  upload still ends in `Success(havePreferencesFailed = true)`.
