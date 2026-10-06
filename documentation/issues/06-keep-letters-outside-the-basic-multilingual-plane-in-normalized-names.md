# Keep letters outside the Basic Multilingual Plane in normalized names instead of turning them into separators

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryFiles.kt`,
`data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/FileNamesTest.kt`
(lane B's module — where the `normalizedName` tests live; `:data:model` has no test source set), `data/model/CLAUDE.md`
**Challenged:** amended — Adlam, Osage, Deseret and Old Hungarian are cased scripts, so the name now depends on `String.lowercase()` folding supplementary letters identically on the JVM, Kotlin/Native and Wasm (a difference would give one song two names across devices and break sync's name matching); the plan now asks for the test to be run once on the iOS simulator target as well, and the manual check covers a Dropbox sync of such a name.

## Problem

`LibraryFiles.normalizedName` walks UTF-16 `Char`s (LibraryFiles.kt:113-122 at 8ee010b36):

```kotlin
for (character in base.normalizedToNfc().lowercase()) {
    if (character in APOSTROPHES) continue
    val isForeignCharacter = when {
        character.isMark() -> isAfterForeignCharacter
        character.isLatin() -> false
        else -> character.isLetterOrDigit()
    }
```

A letter outside the BMP is two surrogate `Char`s whose category is `SURROGATE`: `isLetterOrDigit()` is false for
each, `isLatin()` is false, `withoutAccent()` returns them unchanged and not in `a..z`, so both halves become `_`. A
title written in Adlam (Fula), Osage, Old Hungarian (rovás), Gothic, Deseret, or with a CJK Extension B ideograph (common
in Cantonese names) loses those words; one written wholly in such a script is filed as `untitled.cho`, the next as
`untitled_2.cho`. The root `CLAUDE.md` ("Latin letters without their accents and letters of every other script kept as
they are") and `data/model/CLAUDE.md` promise otherwise. No lone surrogate is ever written, so this is a naming loss,
not corruption. `takeBytes` (LibraryFiles.kt:192) would split a pair if pairs were kept, so it has to change with it.

## Fix

Iterate by index and treat a well-formed surrogate pair as one character:

```kotlin
val text = base.normalizedToNfc().lowercase()
var index = 0
while (index < text.length) {
    val character = text[index]
    val low = text.getOrNull(index + 1)
    if (character.isHighSurrogate() && low != null && low.isLowSurrogate()) {
        val codePoint = 0x10000 + ((character.code - 0xD800) shl 10) + (low.code - 0xDC00)
        val isKept = isSupplementaryLetter(codePoint)
        if (isKept) folded.append(character).append(low) else folded.append(NAME_SEPARATOR)
        isAfterForeignCharacter = isKept
        index += 2
        continue
    }
    …the existing body for `character` (with `continue` for apostrophes becoming `index++; continue`)…
    index++
}
```

Common Kotlin has no code point category, so `isSupplementaryLetter` is a range check: kept unless it is in a block of
symbols rather than letters — U+1D000–U+1D24F (musical symbols: a 𝄞 in a title is decoration), U+1D400–U+1D7FF
(mathematical alphanumerics: styled Latin, which only NFKC would fold; a separator, as today), U+1F000–U+1FFFF (emoji,
cards, mahjong, pictographs: a separator, as today) and planes 14–16 (U+E0000 and up: tags, variation selectors,
private use). Everything else above U+FFFF is overwhelmingly letters and ideographs. A lone surrogate keeps falling to
the existing path and becomes a separator.

`takeBytes` counts a pair as 4 bytes and never cuts between its halves:

```kotlin
private fun String.takeBytes(limit: Int): String {
    var bytes = 0
    var end = 0
    while (end < length) {
        val isPair = this[end].isHighSurrogate() && getOrNull(end + 1)?.isLowSurrogate() == true
        val added = when {
            isPair -> 4
            this[end].code < 0x80 -> 1
            this[end].code < 0x800 -> 2
            else -> 3
        }
        if (bytes + added > limit) break
        bytes += added
        end += if (isPair) 2 else 1
    }
    return substring(0, end)
}
```

The word cap already uses `encodeToByteArray().size`, which counts a pair as 4. The result stays idempotent: a kept
pair is lowercase already and is kept again. Existing files are not migrated (root CLAUDE.md: "Nothing is migrated");
`Song.canUpdateFileName` will offer **Update file name** for a song filed as `untitled` whose header now normalizes to
its real name, which is the documented way such a name is moved.

In `data/model/CLAUDE.md`, the `normalizedName` sentence: say that letters outside the BMP are kept too, and which
symbol blocks still become separators.

## Tests

In `FileNamesTest` (`:data:source:local:implementation`): `letters outside the basic multilingual plane are kept` —
an Adlam word (e.g. `"𞤀𞤢"`, U+1E900 U+1E922, lowercased to U+1E922 U+1E922) and an Old Hungarian
one are kept; a CJK Extension B ideograph (`"𠀀"`, U+20000) between two Latin words keeps its place; an emoji
(`"🎸"`, U+1F3B8) is still a separator; normalizing the result again returns it; and a single word of 40 such
letters is cut to at most `MAX_NAME_BYTES` bytes with no lone surrogate at the end (`result.last().isHighSurrogate()`
is false). If the executor of lane A cannot touch that file, the alternative is to give `:data:model` a
`commonTest.dependencies { implementation(kotlin("test")) }` and a `LibraryFilesTest.kt`, which then also has to be
added to the root CLAUDE.md's test command (lane E's file).

**Every platform must fold these the same way.** The name is a song's identity on every device, and the scripts the
fix keeps include cased ones (Adlam, Osage, Deseret, Old Hungarian: `𐲀` U+10C80 lowercases to `𐳀` U+10CC0), so the
result now depends on `String.lowercase()` mapping supplementary code points identically on the JVM, Kotlin/Native
and Wasm. The project's tests run on the desktop target only; run the new `FileNamesTest` case once on Native too
(`./gradlew :data:source:local:implementation:iosSimulatorArm64Test --tests '*FileNamesTest*'`). If Native does not
fold them, lowercase a kept pair explicitly by a small table of the cased supplementary ranges (capital range → offset to add:
Deseret U+10400–10427 +0x28, Osage U+104B0–104D3 +0x28, Vithkuqi U+10570–10595 +0x27 (with its gaps), Old Hungarian
U+10C80–10CB2 +0x40, Warang Citi U+118A0–118BF +0x20, Medefaidrin U+16E40–16E5F +0x20, Adlam U+1E900–1E921 +0x22),
pinned by a desktop test that compares every code point of those ranges with `lowercase()` there, rather than
relying on `lowercase()` on every platform, and say so in the KDoc.

## Manual check

Create a song titled in Adlam (or with a 𠀀 in its title) on any platform: the file in the library folder carries those
letters rather than `untitled`; export it and import it back: it is recognised as already there.
With Dropbox connected, let a sync run upload it, and check from another device (or the Dropbox web UI) that it
arrived under the same name and that the next run moves nothing; a refusal there would fail that file on every run.
