# 15 — Latin letters with no decomposition (ə ɛ ɔ ŋ) become separators in file names

**Severity:** low, optional — a name with holes, and two different titles filed under one name (all platforms) ·
**Area:** `:data:model` (`domain/LibraryFiles.kt`)

**Read, not run.** This was found by reading the code at HEAD (`2065e47f`); it has not been reproduced in a running
build. It is pure logic, and the unit tests below are the confirmation.

Reviewer finding 2-local#7 (flagged low/optional by the reviewer, kept optional here).

## What the user sees

An Azerbaijani song titled **Gəl** is saved as `g_l.cho`, and a Twi or Ewe song titled **Gɛl** — a different word —
also wants `g_l.cho` and lands as `g_l_2.cho`. Titles in Azerbaijani (`ə`), in many West African languages (`ɛ ɔ ŋ`)
and in Sámi/Northern European orthographies (`ŋ`) get file names with underscores where letters were. The songs work;
the names are poor, and two different titles collide into one family, which the import's "same name or a numbered
sibling" rule then treats as one song asked about twice.

## Cause

`LibraryFiles.normalizedName` (`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryFiles.kt:107-129`)
keeps a non-Latin letter as it is, and folds a Latin one through `withoutAccent`, turning anything that is not `a-z`
afterwards into a separator:

```kotlin
            val isForeignCharacter = when {
                character.isMark() -> isAfterForeignCharacter
                character.isLatin() -> false
                else -> character.isLetterOrDigit()
            }
            when {
                isForeignCharacter -> folded.append(character)
                character.isCombiningMark() -> Unit
                character in AND_SIGNS -> folded.append(NAME_SEPARATOR).append("and").append(NAME_SEPARATOR)
                else -> when (val plain = character.withoutAccent()) {
                    'ß' -> folded.append("ss")
                    'æ', 'ǣ', 'ǽ' -> folded.append("ae")
                    'œ' -> folded.append("oe")
                    'þ' -> folded.append("th")
                    'ĳ' -> folded.append("ij")
                    'ǆ', 'ǳ' -> folded.append("dz")
                    'ǉ' -> folded.append("lj")
                    'ǌ' -> folded.append("nj")
                    else -> folded.append(if (plain in 'a'..'z' || plain in '0'..'9') plain else NAME_SEPARATOR)
                }
            }
```

`isLatin()` (`:175-176`) is `this < 'Ͱ' || …`, so `ə` (U+0259), `ɛ` (U+025B), `ɔ` (U+0254) and `ŋ` (U+014B)
are Latin; `withoutAccent` (`Accents.kt`) does not list them, because they are letters of their own rather than
letters with marks; so they fall through to `NAME_SEPARATOR`.

## The change

Invoke the **`code-style`** skill before the first edit.

Fold them in `normalizedName` itself, next to the other letters a keyboard without them spells otherwise (`ß → ss`,
`þ → th`) — not in `withoutAccent`, whose table is "a plain letter with marks on it" and which the search normalizer
shares:

```kotlin
                    'ǌ' -> folded.append("nj")
                    // Letters of their own rather than letters with marks, so withoutAccent does not know them, spelled
                    // the way a keyboard without them spells them: Azerbaijani ə, the open ɛ and ɔ of Twi, Ewe and
                    // Lingala, and the ŋ of those and of Sámi. Left out, each became a hole in the name.
                    'ə', 'ɛ' -> folded.append('e')
                    'ɔ' -> folded.append('o')
                    'ŋ' -> folded.append('n')
```

Uppercase forms (`Ə` U+018F, `Ɛ` U+0190, `Ɔ` U+0186, `Ŋ` U+014A) are lowercased to these before the `when` (the loop
runs over `base.normalizedToNfc().lowercase()`), so they need no entries of their own.

The rule stays idempotent (`gel` normalizes to `gel`).

**Consequence to accept (why this is optional):** a library file named by the old rule (`g_l.cho`) is now offered
**Update file name** (`Song.canUpdateFileName` compares with the new desired name), and an export of such a song
(under plan 25, exports follow the header) is named `gel.cho`, which an import into a library still holding `g_l.cho`
does not recognise as the same song — the family lookup is by the new name. That is the same trade every earlier
naming rule change made ("files that were named before any of this keep their names until …", root `CLAUDE.md`), and
it affects only titles containing these four letters.

Out of scope, noted: the search normalizer (`NormalizeTextUseCaseImpl`, `:domain:implementation`) does not fold these
either — typing `gel` does not find `Gəl`. If wanted, it is a separate one-line change there (the same four
mappings), not a reason to put them into `withoutAccent`.

## Tests

`data/source/local/implementation/src/commonTest/.../FileNamesTest.kt` (where `normalizedName` is tested):

1. `lettersWithNoDecompositionAreSpelledOut`: `LibraryFiles.normalizedName("Gəl") == "gel"`,
   `normalizedName("Ɛdwoa") == "edwoa"`, `normalizedName("Ɔkɔm") == "okom"`, `normalizedName("Ŋgɔnɔ") == "ngono"`.
2. Idempotence: each result normalized again is unchanged.

## Verification

Unit tests; root unit test command. Optionally: create a song titled `Gəl` in the desktop app and check the file is
`gel.cho` in the library folder.

## Docs

- `data/model/CLAUDE.md:16` — "The ligatures and `þ` fold to two letters in `normalizedName` itself." → "The ligatures
  and `þ` fold to two letters in `normalizedName` itself, and the Latin letters that are letters of their own rather
  than letters with marks (`ə ɛ ɔ ŋ`) to the one a keyboard spells them with."
- Root `CLAUDE.md` naming bullet (HEAD `:186-197`) needs no change ("Latin letters without their accents" still
  describes it).

## Files touched

- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryFiles.kt`
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/FileNamesTest.kt`
- `data/model/CLAUDE.md`

## Depends on

Nothing. Touches `FileNamesTest.kt` like plans 14 and 17 (different test functions). If lane C's plan 25
(export names follow the header) is taken, land this before or after it — the interaction described above is the
same either way.
