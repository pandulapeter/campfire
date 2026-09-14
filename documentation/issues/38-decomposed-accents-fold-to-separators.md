# 38 · Decomposed (NFD) accents fold to separators

**Severity:** low · **Area:** `:data:model` (`Accents.kt`, `LibraryFiles.normalizedName`), `:domain:implementation` (`NormalizeTextUseCase`)

`Accents.kt` folds precomposed characters only. A title written as `e` + U+0301 (a tool that emits NFD, some macOS
paths) becomes `e_dith` in a file name and `e dith` in the search key, so the same title in NFC and NFD files under
two names and does not find itself.

## Fix

There is no normalizer in common Kotlin, but the combining marks are one contiguous block: after the per-character
fold, **drop U+0300–U+036F** (Combining Diacritical Marks). Do it in both `normalizedName` and the text normalizer
behind sorting and searching (`NormalizeTextUseCaseImpl`, `domain/implementation`). One helper in `:data:model`
next to `withoutAccent`:

```kotlin
/** A combining mark of the block that carries every Latin accent; dropped after the base letter has been folded. */
fun Char.isCombiningMark() = this in '̀'..'ͯ'
```

Tests: `normalizedName("Édith") == "edith"`, and the search finds "Édith" when typed as "edith" in either form.
