# Word the Replace confirmation's message for the number of files it replaces

**Kind:** bug (copy)  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — the message now ends the way the app's other irreversible confirmation does ("This cannot be undone." / "Ez nem vonható vissza.", `settings_library_delete_confirmation`) rather than "cannot be brought back" / "nem lehet visszahozni", which read less naturally, the Hungarian above all.
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/CampfireDialogs.kt`,
`presentation/src/commonMain/composeResources/values/strings.xml`,
`presentation/src/commonMain/composeResources/values-hu/strings.xml`

## Problem

The confirmation before an import overwrites library files has a counted title but borrows the conflict menu's
description, written for all taken names at once, as its message (`CampfireDialogs.kt`, at 72d48deb7):

```kotlin
is DialogType.ConfirmImportReplace -> ConfirmationDialog(
    title = pluralStringResource(Res.plurals.import_replace_title, dialog.count, dialog.count),
    text = stringResource(Res.string.import_conflicts_replace_description),
    …
)
```

```xml
<string name="import_conflicts_replace_description">Write them over what your library has. The replaced files are gone.</string>
```

With one file it reads "Replace 1 file? / Write them over what your library has. The replaced files are gone." (live run:
`103-confirm.png`). It is also phrased as an instruction ("Write them over…"), which is right for a menu option and odd
as the explanation under a question. The Hungarian ("A könyvtáradban lévő fájlok helyére kerülnek. A felülírt fájlok
elvesznek.") has the same plural.

## Fix

Add a counted message of the dialog's own, after `import_replace_title` in both files, and use it in the dialog with
`pluralStringResource(Res.plurals.import_replace_message, dialog.count)`. The menu keeps
`import_conflicts_replace_description`, which describes the choice for any number of rows.

English:

```xml
<plurals name="import_replace_message">
    <item quantity="one">The file in your library is written over. This cannot be undone.</item>
    <item quantity="other">The files in your library are written over. This cannot be undone.</item>
</plurals>
```

Hungarian:

```xml
<plurals name="import_replace_message">
    <item quantity="one">A könyvtáradban lévő fájl felülíródik. Ez nem vonható vissza.</item>
    <item quantity="other">A könyvtáradban lévő fájlok felülíródnak. Ez nem vonható vissza.</item>
</plurals>
```

Neither item takes an argument, so the call passes the count once (as the quantity) and no format arguments.

## Tests

None: a string resource.

## Manual check

Import an archive with one taken name, set it to Replace, press **Import**: the dialog reads "Replace 1 file?" over "The
file in your library is written over. This cannot be undone." With two taken names both set to Replace, the plural
reads. Switch the app to Hungarian and repeat.
