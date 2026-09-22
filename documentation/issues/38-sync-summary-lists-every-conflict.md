# 38 · After a sync with many conflicts the account row lists every one of them, and a failed file named with `%` is mangled

**Severity:** minor (all platforms; conflicts in bulk take a first run of a restored or long-offline device that was
edited on both sides; the `%` case needs such a file name) · **Area:** `:presentation`
(`screens/settings/SyncSettings.kt`: `statusText`; `components/TextResource.kt`)

## Symptom
1. Two devices, both edit the same 150 songs while offline (or a phone restored from a backup, which starts
   disconnected and compares by content on its first run), then sync.
2. Settings → Library.

The line under the account is "Kept both versions of a song that changed in two places: a.cho (2), b.cho (2), …" with
all 150 names in one paragraph that runs several screens down the tab, pushing "Sync now" and "Disconnect" out of
sight. The failed-files line next to it names three files and counts the rest; the conflicts line has no such cap.

Separately, a run in which a file named e.g. `100% sure.cho` failed shows the sentence with the count put where the
name should be (`100` + the plugin reading `% s` as a placeholder), the thing plan 63 of the second review (commit
44d0f01f) fixed for plain strings but not for this plural.

## Cause
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/settings/SyncSettings.kt:258-269`:

```kotlin
outcome.summary.failed.takeIf { it.isNotEmpty() }?.let { failed ->
    pluralStringResource(
        Res.plurals.settings_sync_files_failed,
        failed.size,
        failed.size,
        failed.take(MAXIMUM_NAMED_FILES).joinToString(),
    )
},
outcome.summary.conflicts.takeIf { it.isNotEmpty() }?.let {
    textResource(Res.string.settings_sync_conflicts, it.joinToString())
},
```

- The conflicts are joined whole; `SyncSummary.conflicts` accumulates one name per conflict of the run
  (`SyncEngine.kt:507`).
- The failed names go through the localization plugin's `pluralStringResource`, whose `getPlural` runs the same
  `formatString` (`LocalizedStrings.kt`, generated: positional pass, then a sequential pass over the result) that
  `textResource` (`components/TextResource.kt`) exists to avoid. 44d0f01f moved the `settings_sync_conflicts` string
  and the other sentences carrying user text to `textResource`, but there is no plural counterpart, so this one was
  left on the formatter.

## Fix
1. `components/TextResource.kt`: add the plural counterpart of `textResource`, and let the filling understand the
   count's `%N$d` as well:

   ```kotlin
   /**
    * [textResource] for a sentence that counts something: the item for [quantity], filled in the same single pass. The
    * count goes in as the text it reads as, like any other number in such a sentence.
    */
   @Composable
   internal fun pluralTextResource(key: PluralStringResource, quantity: Int, vararg texts: String) =
       LocalizedStrings.getPlural(key, quantity, locale = currentLanguage.value).withTexts(*texts)
   ```

   (`getPlural` with no format arguments returns the item's template untouched.) Widen `TEXT_PLACEHOLDER` to
   `Regex("%(\\d+)\\\$[sd]")` and say so in `textResource`'s KDoc ("Only `%N$s` and `%N$d` are understood").
2. `values/strings.xml` and `values-hu/strings.xml`: replace the `settings_sync_conflicts` string with a plural shaped
   like `settings_sync_files_failed`:
   - en: `one` "Kept both versions of a song that changed in two places: %2$s"; `other` "Kept both versions of %1$d
     songs that changed in two places. Among them: %2$s"
   - hu: `one` "Két helyen is módosult dal mindkét változata megmaradt: %2$s"; `other` "%1$d dal két helyen is
     módosult, mindkét változatuk megmaradt. Köztük: %2$s"
3. `SyncSettings.kt` `statusText()`:
   ```kotlin
   outcome.summary.failed.takeIf { it.isNotEmpty() }?.let { failed ->
       pluralTextResource(
           Res.plurals.settings_sync_files_failed,
           failed.size,
           failed.size.toString(),
           failed.take(MAXIMUM_NAMED_FILES).joinToString(),
       )
   },
   // Capped like the failures: a first run on a device edited on both sides can keep hundreds of copies, and this
   // line is not the place to list them.
   outcome.summary.conflicts.takeIf { it.isNotEmpty() }?.let { conflicts ->
       pluralTextResource(
           Res.plurals.settings_sync_conflicts,
           conflicts.size,
           conflicts.size.toString(),
           conflicts.take(MAXIMUM_NAMED_FILES).joinToString(),
       )
   },
   ```
   and update `MAXIMUM_NAMED_FILES`'s KDoc ("A full disk fails every file of a run, and a first run on a device edited
   on both sides can keep a copy of every song; the line under the account is not the place for all their names.").

Do **not** route the conflicts through `pluralStringResource`, for the reason 44d0f01f gives.

## Tests
None (UI is untested). (`withTexts` is in `:presentation`, which has no tests by design.)

## Verify
1. Build with sync; on two devices (or desktop + web) change the same 10 songs differently while one of them is
   offline, then sync the second. After: "Kept both versions of 10 songs that changed in two places. Among them:
   x (2).cho, y (2).cho, z (2).cho". With exactly one conflict: the `one` sentence with its name.
2. Make a file named `100% sure.cho` fail (e.g. make it unreadable on the desktop with `chmod 000` in the library
   folder) and sync: the failed line names `100% sure.cho` intact.
3. Switch the app to Hungarian: both sentences read in Hungarian with the same numbers and names.
4. The other sentences using `textResource` (song details header, dialogs) look as before.

## Docs
- `presentation/CLAUDE.md`, the `composeResources/values[-hu]/strings.xml` bullet: "`textResource` is the one other
  reader" → "`textResource` and its plural twin `pluralTextResource` are the other readers", and add "a plural that
  carries user text (a file name) is read with `pluralTextResource`, passing the count as text".
- Root `CLAUDE.md`, Conventions, the sentence about `textResource`: append "(`pluralTextResource` for a `<plurals>` that
  carries such text)".
- `.claude/skills/code-style/SKILL.md`, where it describes `textResource` (touched by 44d0f01f): mention
  `pluralTextResource` the same way.

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/TextResource.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/settings/SyncSettings.kt`
- `presentation/src/commonMain/composeResources/values/strings.xml`
- `presentation/src/commonMain/composeResources/values-hu/strings.xml`
- `presentation/CLAUDE.md`, `CLAUDE.md`, `.claude/skills/code-style/SKILL.md`

## Depends on
Nothing. 13 rearranges the rows of the same file; schedule the two one after another.
