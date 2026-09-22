# 31 · "Imported 1 songs and 0 setlists": counted English sentences that are not plurals

**Severity:** minor (all platforms, English only. Very likely: importing a single file, the most common import,
ends in "Imported 1 songs and 0 setlists · 0 already there · 0 skipped") · **Area:** `:presentation`
(`composeResources/values*/strings.xml`, `ui/dialogs/Dialogs.kt`)

## Symptom
Open one `.cho` file with Campfire, or share or drop one: the snackbar says "Imported 1 songs and 0 setlists · 0
already there · 0 skipped". The conflicts dialog says "1 songs and 0 setlists are new and will be imported either
way." and "1 are already in your library, unchanged, and will be left alone."

## Cause
Root `CLAUDE.md`: "A counted sentence whose singular reads differently is a `<plurals>` with `one` and `other`
items". These are plain strings (`values/strings.xml:29`, `:38`, `:39`) filled with counts at
`ui/CampfireApp.kt:517-523` and `ui/dialogs/Dialogs.kt:395`, `:398`. Hungarian keeps a noun singular after a numeral,
so its texts are right as they are.

## Fix
Word the two sentences that carry several counts so that no noun follows a bare count (as `settings_library_summary`
does), and make the single-count one a `<plurals>` like its neighbour `import_conflicts_skipped`.

1. `values/strings.xml`:
   - `:29` `<string name="import_result">Songs imported: %1$d · Setlists: %2$d · Already there: %3$d · Skipped: %4$d</string>`
   - `:38` `<string name="import_conflicts_new">New songs: %1$d · New setlists: %2$d. These are imported either way.</string>`
   - `:39` replace the string with
     ```xml
     <plurals name="import_conflicts_duplicates">
         <item quantity="one">One file is already in your library, unchanged, and will be left alone.</item>
         <item quantity="other">%1$d files are already in your library, unchanged, and will be left alone.</item>
     </plurals>
     ```
2. `values-hu/strings.xml`: `import_result` and `import_conflicts_new` stay as they are. Replace `:39` with
   ```xml
   <plurals name="import_conflicts_duplicates">
       <item quantity="one">Egy fájl változatlanul megvan már a könyvtáradban, ezt nem bántjuk.</item>
       <item quantity="other">%1$d fájl változatlanul megvan már a könyvtáradban, ezeket nem bántjuk.</item>
   </plurals>
   ```
3. `Dialogs.kt:398`:
   `ImportConflictsNote(pluralStringResource(Res.plurals.import_conflicts_duplicates, summary.duplicateCount, summary.duplicateCount))`,
   The import at `:84` (`com.pandulapeter.campfire.presentation.resources.import_conflicts_duplicates`) stays: the
   plurals accessor has the same import path, as `import_conflicts_skipped` at `:95` shows. `pluralStringResource`
   (`localization`) is already imported at `:158`.

The key count and placeholders stay identical between the two files (the plugin checks neither, so re-check by eye).

## Tests
None (UI).

## Verify
1. English: import one `.cho` file: "Songs imported: 1 · Setlists: 0 · Already there: 0 · Skipped: 0".
2. Import a zip where one file collides with a different library file and one is identical: the dialog reads "New
   songs: … · New setlists: …. These are imported either way." and "One file is already in your library…"; with two
   identical files "2 files are already…".
3. Hungarian: the same flows read as before, with the singular duplicate sentence for one file.
4. Compile: `:presentation:compileKotlinDesktop` (regenerates the resource accessors).

## Docs
None.

## Touches
- `presentation/src/commonMain/composeResources/values/strings.xml`
- `presentation/src/commonMain/composeResources/values-hu/strings.xml`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt`

## Depends on
Nothing. 38 edits other lines of `values-hu/strings.xml`.
