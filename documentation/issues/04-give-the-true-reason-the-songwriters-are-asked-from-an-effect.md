# Give the true reason the song details lookup asks for the songwriters from an effect rather than from the tap

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — the suggested wording said the controller "asks once per row" / "does not ask twice for a row", which is false: `loadSongCredits` only skips the row it holds now, so opening A, then B, then A again asks for A twice (B's request cancels and replaces A's). Reworded both passages to "the row it already holds".
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SongDetailsLookupSheet.kt`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/CLAUDE.md`

## Problem

`SongDetailsLookupSheet.kt` says:

```kotlin
// Asked from here rather than from the tap, so that a row open when a restored process comes back asks again.
LaunchedEffect(selected) { selected?.let(viewModel::loadSongCredits) }
```

and `ui/dialogs/CLAUDE.md` says the songwriters are asked for "as the row opens — one request, `LaunchedEffect` keyed by the row so a restored one asks again".

Neither is true:

- A restored process never shows the lookup. `DialogType.toSavedDialog()` (`SavedDialog.kt`) has no case for `DialogType.SongDetailsLookup` (`else -> null`), and the same `CLAUDE.md` says a few lines earlier "A restored process reopens the form rather than the lookup, from the file."
- A recreated Android activity (the one case where the open row, a `rememberSaveable` `selectedKey`, does come back with the sheet) asks nothing again: the songwriters are held by `SongDetailsLookupController`, which outlives the activity, and its `loadSongCredits` returns at once for the row it already holds:

  ```kotlin
  fun loadSongCredits(candidate: SongDetailsCandidate) {
      if (_songCredits.value?.candidateKey == candidate.key) return
  ```

A reader trusting the comment would look for a restoration path that does not exist, or remove the early return to make "asks again" true.

## Fix

Reword both to what the effect actually buys: the open row is the one thing that decides what is asked for, whatever opened it. `selected` is only non-null once the row's key is among the candidates on screen, so the request follows the row being open rather than the tap, and an activity recreated with the row open finds the answer the view model already holds rather than asking twice. Suggested wording:

- `SongDetailsLookupSheet.kt`:

  ```kotlin
  // Asked for whenever a row is open rather than from the tap, so that what is open is the one thing deciding it; an
  // Android activity recreated with a row open finds the answer the view model kept, which asks nothing again for the
  // row it already holds.
  LaunchedEffect(selected) { selected?.let(viewModel::loadSongCredits) }
  ```

- `ui/dialogs/CLAUDE.md`: replace "`LaunchedEffect` keyed by the row so a restored one asks again" with "`LaunchedEffect` keyed by the open row, which an Android activity recreated with the row open finds already answered (`SongDetailsLookupController` asks nothing again for the row it already holds; opening another row replaces it, so coming back to the first asks again)".

No behaviour changes.

## Tests

None: comments and documentation only.

## Manual check

None needed beyond reading the two passages against the code.
