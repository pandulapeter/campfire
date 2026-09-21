# 04 · Android: a file opened with Campfire is imported again on every rotation

**Severity:** wrong behaviour, leading to duplicates and overwritten edits (android; certain after any "open with" or
share, on the first rotation, theme switch or window resize) · **Area:** `:app:android` — `CampfireActivity`

## Symptom
1. In a file manager, open `song.cho` with Campfire (or share it to Campfire). It is imported.
2. Rotate the phone, switch the system to dark mode, resize a split-screen window, or come back to the app after
   the system destroyed the activity.
3. The same file is read and queued for import again, every time:
   - the song is untouched: the import result snackbar (nothing imported, one file already there) on every rotation;
   - the song has been tagged, transposed in the editor or edited since (the natural thing to do right after opening
     it): the second import now *conflicts*, so the **import conflicts dialog appears out of nowhere on rotation**.
     Confirming it as it is preselected creates `song_2.cho`; **Replace** writes the original file over the user's
     edits. It comes back on every later rotation until the task is closed.
4. Finishing the activity with Back and reopening it from Recents delivers the original intent once more
   (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`), with the same result.

## Cause
`app/android/src/main/java/com/pandulapeter/campfire/CampfireActivity.kt:47-60` handles the activity's intent on every
`onCreate`, and the manifest declares no `configChanges` for the activity, so every configuration change is an
`onCreate`:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    …
    handle(intent)
}
```

`getIntent()` on a recreated activity is the intent it was started with — or, after `onNewIntent` called
`setIntent` (`:84`), that one: `ActivityThread.handleRelaunchActivityInner` carries `activity.mIntent` over a
configuration change on purpose ("Preserve last used intent, it may be set from Activity#setIntent()"). Either way it
is an intent that has been acted on already. The view model survives the recreation with the library the first
import wrote, so the second delivery is an ordinary import of a file that is already there.

There is a second, latent half, which today's behaviour happens to mask: the read runs in `lifecycleScope` (`:140`)
and its result goes into a channel that is a field of the activity instance (`:43`). A rotation while a large file
is still being read cancels the read, and a list already in the old instance's channel is never collected. At the
moment the recreated activity reads the file again, so nobody notices; once the intent is handled only once, that
rotation would lose the import.

## Fix
The guard is `savedInstanceState == null` plus the history flag, and the read moves out of the activity so that
handling an intent once is enough. Why this guard and not "clear the consumed intent"
(`setIntent(Intent(this, javaClass))`): `setIntent` only changes the field of the activity object. It survives a
configuration change (see above) but not a recreation by the system — "Don't keep activities", or the process being
killed in the background — where the `ActivityRecord`'s original intent comes back. Clearing would therefore still
need the saved-state check next to it, and the saved-state check alone already covers every case:

| How `onCreate` is reached | `savedInstanceState` | `getIntent()` | Already handled? | Guard says |
|---|---|---|---|---|
| Cold start by "open with" / share / redirect | null | that intent | no | handle |
| Launcher icon | null | `MAIN` | nothing to handle | handle (no-op) |
| Rotation, theme, resize | non-null | launch intent, or the last `setIntent` | yes | skip |
| Rotation after `onNewIntent` | non-null | the new intent | yes, in `onNewIntent` | skip |
| Recreated after "Don't keep activities" / process death | non-null | the original launch intent | yes, by the first instance | skip |
| Reopened from Recents after Back finished it | null | launch intent + `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY` | yes | skip |
| New file or redirect while the activity is destroyed or the process dead | non-null, then `onNewIntent` | original | the new one: no | `onNewIntent` handles it |

The process-death row is a decision: the file is **not** imported again. In all but a sliver of cases it was
imported by the first instance seconds after it arrived, and importing it again is exactly the bug above. The sliver
is a process killed while the file was still being read, or while the conflicts question was on screen: that import
is lost, and the user opens the file again — the source is still where it was, nothing of the library is affected,
and a question that reappears by itself after the app was restored is worse than one that has to be asked for.

The last row is what makes the OAuth redirect safe (see *Depends on*): an intent that arrives for a `singleTask`
activity whose instance is gone is queued by the system and delivered to `onNewIntent` of the recreated instance,
after `onCreate` and before `onResume` (`ActivityThread.performResumeActivity` → `deliverNewIntents`), so it never
depends on the `onCreate` guard.

1. **New file** `app/android/src/main/java/com/pandulapeter/campfire/AndroidFileImport.kt` (MPL header copied from
   `CampfireActivity.kt`), the counterpart of `app/ios/.../IosFileImport.kt`:

   ```kotlin
   package com.pandulapeter.campfire

   import android.content.Context
   import android.net.Uri
   import com.pandulapeter.campfire.data.model.domain.ImportedFile
   import com.pandulapeter.campfire.presentation.ui.platform.toImportedFile
   import kotlinx.coroutines.CoroutineScope
   import kotlinx.coroutines.Dispatchers
   import kotlinx.coroutines.SupervisorJob
   import kotlinx.coroutines.channels.Channel
   import kotlinx.coroutines.flow.receiveAsFlow
   import kotlinx.coroutines.launch

   /**
    * Files the system handed over, on their way to the UI. Both the channel and the scope the files are read in
    * belong to the process rather than to the activity: an intent is acted on once, so a read that a rotation
    * cancelled, or a list left in the channel of an activity instance that is gone, would be an import the user
    * asked for and never got.
    */
   private val pendingImports = Channel<List<ImportedFile>>(Channel.BUFFERED)

   private val importScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

   internal val filesToImport = pendingImports.receiveAsFlow()

   /**
    * Reads [uris] off the main thread and queues what could be read. The application context is enough to read
    * them with, since the permission an intent grants is held by the app for as long as the activity record lives,
    * not by the activity instance that received it.
    */
   internal fun Context.importFiles(uris: List<Uri>) {
       val context = applicationContext
       importScope.launch {
           val files = uris.mapNotNull { it.toImportedFile(context) }
           if (files.isNotEmpty()) {
               pendingImports.send(files)
           }
       }
   }
   ```

2. **`CampfireActivity.kt`**
   - Delete the `filesToImport` field and the comment above it (`:41-43`); move what the comment says about
     `singleTask` to `onNewIntent` (below).
   - `onCreate`: handle the intent only when it has not been handled, and do it *before* `setContent` (the position
     is irrelevant to this plan and required by plan 11, so both plans produce the same lines):

     ```kotlin
     override fun onCreate(savedInstanceState: Bundle?) {
         super.onCreate(savedInstanceState)
         enableEdgeToEdge()
         keepStartupScreenUntilAppIsReady()
         // An intent is acted on once. A recreated activity is handed the intent of the instance before it - on a
         // rotation, and when the system brings the app back after killing it - and one reopened from Recents the
         // intent it was first started with, marked as history. Neither is something the user has just asked for,
         // and importing the same file again would put the conflicts question up over whatever they did to the
         // song since. An intent that arrives while no instance exists is not lost to this: the system delivers it
         // to onNewIntent once there is one.
         if (savedInstanceState == null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) {
             handle(intent)
         }
         setContent {
             CampfireAndroidApp(
                 urlOpener = ::openUrl,
                 filesToImport = filesToImport,
                 syncNotifier = ::onSyncNotificationChanged,
                 onAppReady = { isAppReady = true },
             )
         }
     }
     ```
     (`filesToImport` is now the top-level flow of step 1, a single instance, so the `LaunchedEffect(filesToImport)`
     in `CampfireApp` is no longer keyed on a new flow object per composition.)
   - `onNewIntent`: drop `setIntent(intent)`. Nothing reads `getIntent()` after `onCreate`, and the guard must not
     come to depend on which of the two intents a recreation reports.

     ```kotlin
     /**
      * The activity is singleTask, so a file opened while Campfire is running arrives here rather than at a new
      * instance - as does one that arrives while the instance is gone, which the system holds until it is back.
      */
     override fun onNewIntent(intent: Intent) {
         super.onNewIntent(intent)
         handle(intent)
     }
     ```
   - `importFrom`: replace the `lifecycleScope.launch { … }` block (`:140-146`) with `importFiles(uris)`, keeping the
     `if (uris.isEmpty()) return` above it. Remove the imports that are now unused: `lifecycleScope`,
     `ImportedFile`, `toImportedFile`, `Dispatchers`, `Channel`, `receiveAsFlow`, `launch`, `withContext`.
3. Do **not** add `android:configChanges` to the manifest to make the recreation go away: the saved-state and
   process-death rows of the table would still be there, and Compose handles a recreation correctly already.

## Tests
None (the UI and the shells are untested).

## Verify
`./gradlew :app:android:assembleDebug`, install the `.debug` build on an emulator, then:
1. `adb push song.cho /sdcard/Download/`, open it from the Files app with Campfire: one import snackbar. Rotate
   twice, toggle dark mode from the quick settings: no further snackbar. Add a tag to the song, rotate: no conflicts
   dialog.
2. With Campfire still running, open a second file: it is imported (`onNewIntent`). Rotate: nothing.
3. Developer options → "Don't keep activities". Open a file with Campfire, press Home, return from Recents: nothing
   is imported again. While on the Home screen, open another file from the Files app: it *is* imported (queued new
   intent). Turn the option off again.
4. `adb shell am kill com.pandulapeter.campfire.debug` with the app in the background after step 1, reopen from
   Recents: the library is there, nothing is imported again.
5. Press Back until the app closes (on Android 11 or below, where that finishes the activity), reopen from Recents:
   nothing is imported.
6. Share a file of ~20 MB to Campfire and rotate at once: the import still arrives.
7. Sync (needs `campfire.dropbox.appKey`): Settings → Connect, approve in the browser: connected. Rotate right after
   returning: still connected, no second attempt.

## Docs
`app/android/CLAUDE.md`, the `CampfireActivity` bullet. Replace "read off the main thread and passed to the UI
through a `Channel`" with: "read off the main thread and passed to the UI through a top level `Channel`
(`AndroidFileImport.kt`) — both the channel and the scope the files are read in belong to the process, so a rotation
in the middle of a read does not lose the import. **An intent is handled once**: a recreated activity
(`savedInstanceState != null`, which is a rotation as much as the system bringing the app back after killing it) and
one reopened from Recents (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`) are handed an intent that was acted on already, and
skip it." The sentence about `singleTask` and `onNewIntent` that follows stays, and gains: "— or, when there is no
instance at that moment, once the system has recreated one".

## Touches
- `app/android/src/main/java/com/pandulapeter/campfire/CampfireActivity.kt`
- `app/android/src/main/java/com/pandulapeter/campfire/AndroidFileImport.kt` (new)
- `app/android/CLAUDE.md`

## Depends on
Nothing. Three later plans edit `CampfireActivity.kt` and should be applied after this one:
- **11** (OAuth redirect lost on a cold start) needs `handle(intent)` to run before `setContent` so the redirect is
  in the authenticator's channel before the view model's `restoreSync()` asks for it; step 2 already puts it there.
  The guard and that plan are compatible in every case: a redirect that starts a fresh task is a cold start with
  `savedInstanceState == null`; a redirect that finds the task alive but the process dead recreates the activity with
  the *old* intent and a non-null state (skipped) and is then delivered to `onNewIntent`, which runs before
  `onResume` and so before the Compose content — and the view model with it — is created on attach. It is handled
  exactly once in both, and never again on a rotation, which also stops a spent redirect from being parked in the
  channel for the next `authorize` to discard.
- **52** (shared text) adds an `EXTRA_TEXT` branch to `importFrom`; it should queue through `pendingImports` (add an
  `internal fun importFile(file: ImportedFile)` next to `importFiles` there) rather than bring the activity's own
  channel back.
- **48** (picker result after process death) merges a second flow into `filesToImport` in `CampfireAndroidApp`;
  unaffected by this plan. **15** changes `Uri.toImportedFile`, which this plan only calls.
