# 23 · Android: opening or sharing a file Campfire cannot read brings the app to the front and says nothing at all

**Severity:** wrong behaviour / lost user action (Android; likely for every `file://` open, which the manifest invites
but the app can never read, and for cloud files that are offline or providers that fail) · **Area:** `:app:android`
(`AndroidFileImport.kt`, `AndroidManifest.xml`), `:presentation` androidMain (`FilePicker.android.kt`, `toImportedFiles`)

## Symptom
1. In a file manager that hands out `file://` URIs (several still do, with StrictMode's URI check switched off), or in
   Google Drive with a `.cho` that is not available offline while the phone is offline, choose **Open with →
   Campfire** (or Share → Campfire).
2. Campfire comes to the front. No song, no snackbar, nothing — the tap looks ignored.

iOS reports the same situation as "1 skipped" (`IosFileImport.openUrl` passes `ImportedFile.unread`, from review 2's
plan 51) and the web as well (an unreadable file arrives empty, `FilePicker.wasmJs.kt:151-167`); Android is the one
platform where the user's explicit request is answered with silence.

## Cause
`List<Uri>.toImportedFiles` (`presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/FilePicker.android.kt:232-246`)
leaves an unreadable URI out:

```kotlin
return mapNotNull { uri ->
    try {
        …
    } catch (exception: Exception) {
        println("Could not read \"$uri\": ${exception.message}")
        null
    }
}
```

and `Context.importFiles` (`app/android/src/main/java/com/pandulapeter/campfire/AndroidFileImport.kt:41-49`) then sends
nothing when nothing is left:

```kotlin
val files = uris.toImportedFiles(context)
if (files.isNotEmpty()) {
    pendingImports.send(files)
}
```

The `file://` case is certain rather than occasional: the VIEW filter registers `<data android:scheme="file" />`
(`app/android/src/main/AndroidManifest.xml:74`), but the app holds no storage permission (the manifest's only
permissions are `INTERNET`, the two foreground service ones and `POST_NOTIFICATIONS`, `:17-28`), so on every supported
API level (minSdk 28) a `file://` path into shared storage fails with `EACCES` in `openInputStream`. Campfire is offered
for exactly the opens it cannot perform.

## Fix
1. `toImportedFiles`: keep an unreadable document in the list as an empty one, named as well as the provider allows,
   which is what makes the import count it as skipped — no new string or message, the same as iOS and the web:

   ```kotlin
   /**
    * … One that cannot be read is handed over empty, which the import reports as skipped: the user asked for it,
    * and leaving it out would answer them with nothing at all.
    */
   fun List<Uri>.toImportedFiles(context: Context): List<ImportedFile> {
       val budget = ImportBudget()
       return map { uri ->
           // The query that knows the display name can be what fails, so it is asked on its own.
           val (name, size) = try {
               uri.nameAndSize(context)
           } catch (exception: Exception) {
               uri.fallbackName to null
           }
           try {
               budget.read(name = name, size = size) { limit ->
                   context.contentResolver.openInputStream(uri)?.use { it.readAtMost(limit.toInt() + 1) }
               }
           } catch (exception: Exception) {
               println("Could not read \"$uri\": ${exception.message}")
               null
           } ?: ImportedFile.unread(name)
       }
   }
   ```

   `ImportBudget.read` (`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/ImportLimits.kt`) already answers `unread` for a file it would not look inside
   or that is too large, and `null` only when `readBytes` returned `null` (`openInputStream` answering `null`), which is
   the unreadable case as well. The picker's path goes through the same function; reporting a picked file that could
   not be read as skipped is right there too, and matches the web.

2. `AndroidManifest.xml`: remove `<data android:scheme="file" />` from the ChordPro VIEW filter (`:74`) and adjust the
   comment above the filter to say why: the app holds no storage permission, so a `file://` URI outside its own
   directories cannot be read, and a file manager then offers Campfire for an open that would fail. `content://` stays
   the only scheme, which is what every current file manager, Drive and the Downloads app send.

Do **not** add `READ_EXTERNAL_STORAGE` / `MANAGE_EXTERNAL_STORAGE` to make `file://` readable: a storage permission
for a songbook is not worth the prompt, and Play restricts the latter.

## Tests
None (UI is untested; `:app:android` is a shell).

## Verify
Android emulator, debug build:
1. `adb shell am start -a android.intent.action.VIEW -d content://com.pandulapeter.campfire.debug.files/shared/nope.cho -t '*/*' com.pandulapeter.campfire.debug/com.pandulapeter.campfire.CampfireActivity`
   (a URI of the app's own provider that does not exist): the snackbar says "… 1 skipped" instead of nothing.
2. Before step 2 of the fix, `adb shell am start -a android.intent.action.VIEW -d file:///sdcard/Download/x.cho -t '*/*' …`
   — silent; after it, `adb shell cmd package query-activities -a android.intent.action.VIEW -d file:///sdcard/Download/x.cho -t '*/*'`
   no longer lists Campfire.
3. Open a readable `.cho` from the Files app: imported and opened as before.
4. Pick three files in the system picker where one is a cloud file that fails to download (airplane mode, Drive): two
   imported, one skipped.

## Docs
`app/android/CLAUDE.md`, the manifest paragraph: add "Only `content://` is registered: the app holds no storage
permission, so a `file://` URI into shared storage could not be read." and, in the `CampfireActivity` bullet, "a file
that cannot be read is passed on empty, so the import reports it as skipped rather than the app coming to the front
and saying nothing."
`presentation/CLAUDE.md`, the `ui/platform/FilePicker.kt` bullet: one sentence that `toImportedFiles` hands an
unreadable document over empty.

## Touches
- `presentation/src/androidMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/FilePicker.android.kt`
- `app/android/src/main/AndroidManifest.xml`
- `app/android/src/main/java/com/pandulapeter/campfire/AndroidFileImport.kt` (only its KDoc, which says unreadable
  files are left out)
- `app/android/CLAUDE.md`, `presentation/CLAUDE.md`

## Depends on
Nothing. 26 edits the same `FilePicker.android.kt` (`rememberAndroidFilePicker` and `AndroidFilePicker`, not
`toImportedFiles`); any order, one after the other.
