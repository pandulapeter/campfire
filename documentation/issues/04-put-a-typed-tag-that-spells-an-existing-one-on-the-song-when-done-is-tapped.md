# Put a typed tag that spells an existing one on the song when Done is tapped

**Challenged:** amended — `selectedTags` is a `List<String>` (`stringListSaver`, `mutableStateOf(dialog.song.tags)`), not a `Set`, so `selectedTags + spelledTag` would append a duplicate when the tag is already ticked; the fix now guards like Enter does.

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/Dialogs.kt`

## Problem

In the Manage tags dialog (`SongTagsDialog`, `Dialogs.kt` at 9ab7ca54e), Enter on the keyboard takes what is typed
either as an existing tag, spelled however the user spelled it, or as a new one:

```kotlin
val spelledTag = offeredTags.firstOrNull { it.equals(typedTag, ignoreCase = true) }
…
val enterTypedTag = {
    when {
        typedTag.isEmpty() -> keyboardController?.hide()
        spelledTag != null -> if (spelledTag !in selectedTags) selectedTags = selectedTags + spelledTag
        else -> {
            createdTags = createdTags + typedTag
            selectedTags = selectedTags + typedTag
        }
    }
    query = ""
}
```

The Done button means to take a typed tag the same way, but only takes the new-tag half of it:

```kotlin
onClick = {
    // A tag typed but not entered yet is still one the user meant to put on the song.
    val tags = if (typedTag.isNotEmpty() && spelledTag == null) selectedTags + typedTag else selectedTags
    viewModel.setSongTags(fileName = dialog.song.fileName, tags = tags, offeredTags = offeredTags)
    viewModel.dismissDialog()
},
```

Scenario: the library has `Rock`, the song does not. The user types `rock` and taps Done rather than Enter. `spelledTag`
is `Rock`, so the condition is false and `tags` is `selectedTags` alone: the tag is silently dropped, though the
comment above says the opposite, and though Enter would have ticked `Rock`.

## Fix

Take the typed tag the way Enter does, in one expression:

```kotlin
// A tag typed but not entered yet is still one the user meant to put on the song, spelled as the library spells it.
val tags = when {
    typedTag.isEmpty() -> selectedTags
    spelledTag != null -> if (spelledTag in selectedTags) selectedTags else selectedTags + spelledTag
    else -> selectedTags + typedTag
}
```

(`selectedTags` is a `List`, so the guard is what keeps a tag that is already ticked from being listed twice, exactly as `enterTypedTag` does.) If `enterTypedTag`'s `when` can be shared
without a lambda-returning-a-set contortion, share it; otherwise the four lines above are fine.

## Tests

None: the merge is three lines of Compose state. Pulling it into a pure `tagsToSave(selected, typed, spelled)` is not
worth a file.

## Manual check

Desktop build: library has `Rock`, open Manage tags on a song without it, type `rock`, tap Done. The song now carries
`Rock`.
