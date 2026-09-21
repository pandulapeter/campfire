# 63 · A title, tag or other user text containing `%` comes out mangled wherever the app puts it into a sentence

**Severity:** minor (wrong text, no crash; all platforms; any song, setlist, tag, metadata value, file or account name holding a `%`) · **Area:** `:presentation` (the `com.hyperether.localization` plugin's generated `formatString`; `dialogs/Dialogs.kt`, `components/ListItems.kt`, `screens/songDetails/SongLyrics.kt`, `screens/settings/SyncSettings.kt`)

## Symptom
Name a song `100% sure` and choose Delete from its menu: the confirmation reads
**Delete "100100% sureure"? …**. Duplicate a setlist called `50% off`: the dialog proposes `5050% offff (copy)` as
the new title. A tag `100% live` has "Remove the "100100% liveive" tag" as its button's description, a `{composer}`
of `50% Dylan` is shown mangled under the song's header, and a title containing `%%` loses one of the two. Nothing
crashes, and `$` or `\` in a title (`Ke$ha`) are fine.

## Cause
Every formatted string goes through `formatString` in the generated
`presentation/build/generated/compose/resourceGenerator/kotlin/commonCustomResClass/com/pandulapeter/campfire/presentation/localization/LocalizedStrings.kt:22-63`
(emitted from a template inside `compose-multiplatform-localize-plugin` 2.0.1, `LocalizationPlugin.kt:608-650`). It
fills the positional placeholders and then scans **its own output** for sequential ones:

```kotlin
val positionalPattern = Regex("%(\\d+)\\$([-#+ 0,(]*)\\d*(\\.\\d+)?([dfsioxXeEfFgGaAcspn])")
result = positionalPattern.replace(result) { … formatArgument(argsList[position], type, precision) … }

// Then handle sequential format specifiers with optional precision (%s, %d, %.2f, etc.)
var argIndex = 0
val sequentialPattern = Regex("%([-#+ 0,(]*)\\d*(\\.\\d+)?([dfsioxXeEfFgGaAcspn])")
result = sequentialPattern.replace(result) { … formatArgument(argsList[argIndex], type, precision) … }

// Handle escaped percent signs
result = result.replace("%%", "%")
```

By the second scan the user's text is part of `result`. The pattern allows a space as a flag, so `% s` in
`100% sure` and `% o` in `50% off` are specifiers to it, and each is replaced with the first argument — the title
itself. The final `replace("%%", "%")` then eats half of any `%%` the title held.

Escaping the argument does not get around it: doubling the `%` still leaves `% s` behind the second `%`, and
anything else (a zero width character after every `%`, a private use stand-in swapped back afterwards) puts
characters into user text that a screen reader or a copy would see, or needs the call wrapped on both sides anyway.

The review's suggested fix — `stringResource(Res.string.x).replace("%1\$s", title)` — does not work as written: the
plugin files a string that holds a specifier under `formattedStrings` **only** (`LocalizationPlugin.kt:334-343`), and
the argument-less `stringResource(key)` looks in `strings`, so it answers `"???"`. (`withSyncCounts` gets away with
it because its sentence uses `{completed}` / `{total}`, which the plugin does not take for specifiers.) What does hand
the raw template back is the formatted lookup called with no arguments: `formatString` starts with
`if (args.isEmpty()) return template`.

## Fix
Bypass the plugin's formatter for the strings that take text somebody else wrote, through one helper; the strings
themselves stay ordinary Android-style `%1$s` templates, so `strings.xml` does not change in either language and
there is still one placeholder convention in the project.

1. New file `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/TextResource.kt`
   (MPL header copied from a sibling):

   ```kotlin
   package com.pandulapeter.campfire.presentation.ui.components

   import androidx.compose.runtime.Composable
   import com.pandulapeter.campfire.presentation.localization.LocalizedStrings
   import com.pandulapeter.campfire.presentation.localization.currentLanguage
   import org.jetbrains.compose.resources.StringResource

   /**
    * A string resource with text somebody else wrote put into its `%1$s`, `%2$s`… placeholders: a title, a tag, a
    * value out of a song's header, the name of a file or of an account.
    *
    * The localization plugin's own formatter must not be given such text. It fills the positional placeholders and
    * then scans what it has produced for sequential ones, by which time the text is part of the sentence - and its
    * pattern takes a space for a flag, so the `% s` in "100% sure" is a specifier to it and the title is put into
    * itself. So the template is asked for as it is written, which is what the formatted lookup answers when it is
    * given no arguments, and filled in here in a single pass, where nothing that has been put in is read again.
    *
    * Only `%N$s` is understood, which is all a sentence carrying text needs; a number that has to go into the same
    * sentence is passed as the text it should read as. Strings that only take numbers, or text the app itself
    * wrote (a store's name, the version), keep using `stringResource`.
    */
   @Composable
   internal fun textResource(key: StringResource, vararg texts: String) =
       LocalizedStrings.getFormatted(key, locale = currentLanguage.value).withTexts(*texts)

   /** Fills the `%N$s` placeholders of a template with [texts], leaving one that names no text as it is written. */
   internal fun String.withTexts(vararg texts: String) = TEXT_PLACEHOLDER.replace(this) { match ->
       match.groupValues[1].toIntOrNull()?.let { texts.getOrNull(it - 1) } ?: match.value
   }

   /** Compiled once for the process: this runs for every song row that names a key. */
   private val TEXT_PLACEHOLDER = Regex("""%(\d+)\$s""")
   ```

   `currentLanguage.value` is a snapshot state read, so the result follows a change of the in-app language exactly
   as `stringResource` does. `Regex.replace` with a lambda appends what the lambda returns literally and never
   rescans it, so `%`, `$` and `\` in a text all come through untouched.

2. Switch every call site that passes user text (the import of
   `com.pandulapeter.campfire.presentation.ui.components.textResource` is needed outside the `components` package;
   drop the `localization.stringResource` import where this leaves it unused — in these files it does not):

   | File:line | Key | What is put in |
   | --- | --- | --- |
   | `dialogs/Dialogs.kt:195` | `setlists_duplicate_title` | the setlist's title |
   | `dialogs/Dialogs.kt:256` | `songs_delete_song_confirmation` | the song's title |
   | `dialogs/Dialogs.kt:277` | `setlists_delete_setlist_confirmation` | the setlist's title |
   | `dialogs/Dialogs.kt:288` | `settings_sync_disconnect_confirmation` | the account's name, as the provider reports it |
   | `screens/settings/SyncSettings.kt:186` | `settings_sync_connected_as` | the account's display name |
   | `screens/settings/SyncSettings.kt:255` | `settings_sync_conflicts` | file names, joined (a library folder on the desktop or iOS can hold any name) |
   | `screens/songDetails/SongLyrics.kt:282` | `song_details_tag_remove` | the tag |
   | `screens/songDetails/SongLyrics.kt:300-301` | `song_details_tempo`, `song_details_time` | `{tempo}`, `{time}` |
   | `screens/songDetails/SongLyrics.kt:308-312` | `song_details_composer`, `_lyricist`, `_album`, `_year`, `_duration` | the header values |
   | `components/ListItems.kt:160` | `songs_key` | the key as rendered (`renderKey`): only the root is rewritten, whatever follows it is the file's own text |

   Each is the same one-word change, e.g. `text = textResource(Res.string.songs_delete_song_confirmation, dialog.song.title),`.

   Deliberately **not** switched, since nothing a user wrote reaches them: `song_details_capo` (`SongLyrics.kt:299`,
   an `Int`), `song_details_song_position`, `songs_tags_show_all`, `settings_library_summary`, `import_result` and the
   three `import_conflicts_*` strings and every plural (counts), `settings_version` (a build constant),
   `settings_distribution_current` / `_coming_soon` (a store name out of the resources) and `settings_sync_last_synced`
   / `settings_sync_date_time` (digits).

3. Nothing in `strings.xml`. Check while there that every key in the table still reads `%1$s` in both
   `values/strings.xml` and `values-hu/strings.xml` (they do as of `29820b93`), since the helper understands nothing
   else.

What must not be done instead: no `{title}`-style placeholders for these (two conventions in one file, and thirty
strings to touch in two languages for no gain), no escaping helper around the arguments, no copy of the plugin's
formatter.

Side effect worth knowing: `songs_key` no longer compiles two `Regex`es per song row, which is item 3 of plan 58.

## Tests
None: the helper lives in `:presentation`, which has no test source set, and the brief keeps the UI untested. While
implementing, a scratch `main` (or the debugger's evaluate) should confirm:
`"Delete \"%1\$s\"?".withTexts("100% sure") == "Delete \"100% sure\"?"`,
`"%1\$s (copy)".withTexts("50%% off") == "50%% off (copy)"`,
`"%2\$s-%1\$s".withTexts("a%2\$s", "b") == "b-a%2\$s"` (what is put in is not read again),
`"%3\$s".withTexts("a") == "%3\$s"`.

## Verify
1. `./gradlew :app:desktop:run`.
   - Create songs titled `100% sure`, `50% off`, `%d%s%%` and `Ke$ha \ AC/DC`; delete each and read the dialog.
   - A setlist titled `50% off`: Duplicate proposes `50% off (copy)`; Delete names it correctly.
   - In the editor give a song `{composer: 50% Dylan}`, `{tempo: 120%}`, `{key: 100% sure}` and a tag `100% live`:
     the details header shows them as written, the tag's remove button reads right to a screen reader (VoiceOver on
     the Mac), and the song's row on the Songs screen still shows its key.
   - Switch the app to Hungarian and repeat one of each: the Hungarian sentence, the same text inside it.
2. With sync configured: the "Connected as …" row and the disconnect dialog still name the account.
3. `./gradlew :app:android:assembleDebug :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`

## Docs
- Root `CLAUDE.md`, Conventions, the UI strings bullet: after "formatted strings must always be called with their
  arguments." add "A sentence that takes text somebody else wrote — a title, a tag, a header value, a file or
  account name — is read with `textResource(Res.string.x, text)` (`:presentation`'s `components/TextResource.kt`)
  instead: the plugin's formatter scans its own output a second time, and the `% s` in `100% sure` is a format
  specifier to it."
- `.claude/skills/code-style/SKILL.md`, "User-facing strings": the same sentence after "Formatted strings are always
  called with their arguments…".
- `presentation/CLAUDE.md`, the `composeResources/values[-hu]/strings.xml` bullet: add "`textResource` is the one
  other reader: the same templates with the user's own text put into them, filled in outside the plugin's formatter
  because that one reads what it has put in as a template again."

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/TextResource.kt` (new)
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/settings/SyncSettings.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/ListItems.kt`
- `CLAUDE.md`
- `presentation/CLAUDE.md`
- `.claude/skills/code-style/SKILL.md`

## Depends on
Nothing. Shares `ListItems.kt` with plan 58 (which relies on this plan for its third item), `SongLyrics.kt` with
plans 54, 56, 57 and 61 (this one only touches `SongMetadataHeader`), and `Dialogs.kt` with plan 55.
