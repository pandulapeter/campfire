# 33 — The launch screen never goes away when a first run cannot read its preferences

**Severity:** app unusable until restarted (all platforms, rare storage failure on a first run) · **Area:**
`:presentation` (`CampfireViewModel.kt`)

**Read, not run.** This was found by reading the code at HEAD (2065e47f); it has not been reproduced in a running
build. The "Verification" section below is how to confirm it, and confirming it is the first step of the work.

## What the user sees

On a first run (no `preferences/preferences.json` yet) where reading the preferences fails — the storage refusing
the read, the OPFS worker failing on the web — the app stays on its launch mark for good. On Android and the web the
system splash / loading page is held too, since `onAppReady` is only released once the launch screen has faded. The
app behind it has composed (a failed read counts as read, `arePreferencesLoaded`), but it is never uncovered, and the
list's Retry is under the launch screen. On Android and the web only killing the app gets out (on the desktop and
iOS, leaving the window and coming back may, see Cause), and the next start is a first run again.

## Cause

`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt:1486-1512`:

```kotlin
    private suspend fun plantDemoLibraryOnFirstRun() {
        try {
            if (isFirstLaunch.await()) {
                ...
                // Before the preferences are written rather than after: the queue has no reason to wait for those.
                demoLibraryDecision.complete(Unit)
                saveUserPreferences(userPreferences.filterNotNull().first())
            }
        } finally {
            // In a finally rather than at the end: whatever went wrong, the app is no longer waiting for this, and
            // the launch screen is over the whole of it - and neither is the queue of imports.
            demoLibraryDecision.complete(Unit)
            isDemoLibraryPending.update { false }
        }
    }
```

`isFirstLaunch` is `!hasStoredUserPreferences()` (`IsFirstRunUseCaseImpl.kt:21`): true when the file is absent. The
read itself is a separate call; `UserPreferencesLocalSourceImpl.loadUserPreferences` returns the defaults for an
absent file but throws when the storage throws, and `BaseLocalDataRepository.readOnce` turns that into
`DataState.Failure(null)` (`BaseLocalDataRepository.kt:173-177`). The preferences are only read again by
`LoadScreenDataUseCase` (`loadUserPreferencesIfNeeded`, `LoadScreenDataUseCaseImpl.kt:39`), that is by a refresh: the
Retry of a list that failed — under the launch screen, out of reach — or a resume on the desktop and iOS
(`CampfireApp.kt:170-179`, the first resume skipped). On Android and the web nothing ever reads them again, and on the
other two only a trip away from the window and back does. Until then `userPreferences` stays null,
`userPreferences.filterNotNull().first()` suspends, the `finally` does not run, `isDemoLibraryPending` stays true, and
`hasLibraryToShow` (`:323-327`) never turns true — which is what the launch screen waits for.

The `finally` was written to release the launch screen "whatever went wrong"; a suspension that never ends is the one
thing it cannot catch.

## The change

Invoke the **`code-style`** skill before the first edit.

Two changes, either of which alone would unstick the launch screen, both because each is right on its own:

1. Release the launch screen next to the queue, before the preferences are written — neither has any reason to wait
   for that write.
2. Wait for the read to *settle* rather than to *succeed*, and write nothing when it failed. `UserPreferences` has no
   defaults of its own in `:data:model` (they live in `UserPreferencesDocument` in `:data:source:local:implementation`),
   so the reviewer's `userPreferences.value ?: UserPreferences()` does not compile; and a failed read has nothing
   worth writing anyway.

Replace `:1502-1504` with:

```kotlin
                // Before the preferences are written rather than after: neither the queue nor the launch screen has any
                // reason to wait for those.
                demoLibraryDecision.complete(Unit)
                isDemoLibraryPending.update { false }
                // Whatever the read came to, rather than for one that succeeded: a read that failed is only tried again by
                // a refresh, which may never come, and waiting for its value could wait for good. With nothing read there
                // is nothing to write either, and the next start is a first run again - which plants nothing into a
                // library that has songs in it.
                userPreferencesState.first { it !is DataState.Loading }.data?.let { saveUserPreferences(it) }
```

The `finally` stays as it is (completing and clearing twice is harmless, and it still covers an exception from
`import`).

`userPreferencesState` is the private state at `:345`; `DataState` and `first` are already imported.

What a failed read on a first run costs afterwards is what it costs today on any run: the app works in the defaults
and the settings do not take a change until a refresh reads them (`updateUserPreferences` needs a value to build
on). Additionally the next start is still a first run, so
`forgetSyncConnection` runs again then; an account connected in a session whose preferences could not be read is
dropped at the next start. That is the conservative side of the first-run rule (a fresh installation never inherits a
connection) and a storage that fails this way has bigger problems; it is noted rather than engineered around.

## Tests

- **No unit test is possible** (view model, untested by policy).
- Compile check: `./gradlew :presentation:compileKotlinDesktop :presentation:compileKotlinWasmJs :presentation:compileDebugKotlinAndroid :presentation:compileKotlinIosSimulatorArm64`

## Verification

The failure has to be provoked, since a missing file is read as the defaults.

1. **Desktop**, with the app closed: move `~/Library/Application Support/Campfire` aside (macOS) so the start is a
   first run. Temporarily make `UserPreferencesLocalSourceImpl.loadUserPreferences` throw
   (`throw IOException("test")` as its first line; do not commit).
2. `./gradlew :app:desktop:run`, and do not switch away from the window (a resume would re-read, see Cause).
   - **Before:** the window stays on the launch mark.
   - **After:** the app opens on the demo library, in the default theme and language; the settings do not take a
     change (the preferences were never read, as today on any failed read).
3. Remove the temporary throw, restart: a normal first run — the demo is not planted again (the library has it), the
   preferences are written, and the start after that is not a first run.
4. Regression, a genuine first run with nothing provoked (fresh data directory, and on Android
   `adb shell pm clear com.pandulapeter.campfire.debug`): the demo library appears behind the launch screen, no
   "Your library is empty" flashes, and `preferences/preferences.json` exists afterwards.

## Docs

- `presentation/CLAUDE.md`, the `ui/DemoLibrary.kt` bullet, after "the preferences are saved afterwards, whether
  anything was planted or not, so that the next start is no longer a first one" add: "— if they could be read at all:
  a first run whose read failed saves nothing, and the launch screen is released before that save rather than after
  it."

## Files touched

- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/CampfireViewModel.kt`
- `presentation/CLAUDE.md`

## Depends on

Nothing. First of lane D's `CampfireViewModel.kt` plans (33, 31, 30, 29, 34, 44).
