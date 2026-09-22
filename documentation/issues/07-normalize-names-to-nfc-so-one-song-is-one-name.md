# 07 — Normalize names to NFC so one song is one name

## What the user sees

A Russian, Greek, Hebrew, Vietnamese or Hindi song arrives twice.

The user exports `катюша.cho` from their Mac and imports it on their phone. The import is supposed to notice that
the library already holds that song and disregard it — "a song the library already holds under that name … is
disregarded rather than copied". Instead it writes `катюша_2.cho`, and the library now has the same song twice
under two names that look identical in every list, in the picker, and in the setlist editor.

The same thing through sync: the Mac uploads `катюша.cho`, the phone downloads it, the phone's storage hands the
name back in a different Unicode form, and the next run uploads it again under what the service considers a second
name. Two files on Dropbox with the same visible name, and a setlist pointing at one of them.

The cause is invisible: macOS and iOS hand out file names in **NFD** (`и` followed by U+0306 COMBINING BREVE for
`й`), while the same name typed on Android, Windows, Linux or in a browser is **NFC** (a single U+0439). Two byte
sequences, one word, and nothing in the app folds them together.

## Cause

`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryFiles.kt:100-143`, verified
at HEAD `984861e4`. The fold walks the string character by character:

```kotlin
    fun normalizedName(base: String): String {
        val folded = StringBuilder()
        var isAfterForeignCharacter = false
        for (character in base.lowercase()) {
            if (character in APOSTROPHES) continue
            val isForeignCharacter = when {
                character.isMark() -> isAfterForeignCharacter
                character.isLatin() -> false
                else -> character.isLetterOrDigit()
            }
            when {
                isForeignCharacter -> folded.append(character)
                character.isCombiningMark() -> Unit
                …
```

For **Latin** text the decomposed form already works, and there is a test for it
(`FileNamesTest.kt:74-75`):

```kotlin
        assertEquals("edith", LibraryFiles.normalizedName("Édith"))
        assertEquals("edith", LibraryFiles.normalizedName("Édith"))
```

because `e` is Latin (`isLatin()` is true below U+0370), so `isForeignCharacter` is false for the following
U+0301, and line 112 — `character.isCombiningMark() -> Unit` — drops it, arriving at the same `e` that
`withoutAccent('é')` produces.

For **everything else** the two forms stay apart. `и` is U+0438, above U+0370 and outside every range
`isLatin()` names (lines 171-172), so `isForeignCharacter` is true and `isAfterForeignCharacter` becomes true.
The next character, U+0306, is a mark, so line 106 — `character.isMark() -> isAfterForeignCharacter` — makes it
foreign too, and line 111 appends it as it is. The decomposed name folds to `й`; the precomposed one folds to
`й`. Two names, filed separately, forever.

That rule is deliberate and correct — a Devanagari or Hebrew name must keep its marks, and the file
`hindi_geet` test at `FileNamesTest.kt:134` depends on it (`हिन्दी_गीत` keeps its virama). The problem is not the
mark handling; it is that the *input* is never brought to one canonical form first.

The second place the same question is asked is sync's case folding,
`data/repository/implementation/src/commonMain/.../sync/SyncEngine.kt:81`:

```kotlin
private fun SyncKey.folded() = copy(name = name.lowercase())
```

used by `foldRemoteNamesOntoLocal` (line 46) and `foldIndexNamesOntoListings` (lines 67-73). Two spellings of one
name that differ only by Unicode form are two keys here too, which is what makes the sync half of the symptom: the
engine matches remote against local by this key, misses, and plans an upload of a file that is already there.

## The user's decision

Already taken: **normalize to NFC**. This plan implements it; it is not reopening the choice. NFC is the right
one — it is what every non-Apple platform hands out, what Dropbox stores, and the shorter of the two forms, which
matters against `MAX_NAME_BYTES`.

## The change

### 1. An expect/actual NFC normalization in `:data:model`

`:data:model` is a multiplatform library under the `campfire-library` convention plugin, so the four platform
source sets are configured already even though the module has only `commonMain` today. Adding them is the whole
mechanism; it adds no dependency, so the module's "pure Kotlin domain models with no dependencies" claim survives.

`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/Normalization.kt`:

```kotlin
/**
 * [this] in Unicode Normalization Form C: one code point per character wherever the standard has one.
 *
 * The same name arrives in two forms depending on where it was typed. macOS and iOS hand out file names decomposed
 * — `и` followed by U+0306 for `й` — while Android, Windows, Linux and the browser hand out the composed form, and
 * nothing about the two looks different anywhere in the app. Without this, one song imported from a Mac is filed
 * next to itself as `катюша_2.cho`, and one synced from an iPhone goes up a second time.
 *
 * Every platform has this in its standard library; none of them agrees on where.
 */
expect fun String.normalizedToNfc(): String
```

Actuals:

- `androidMain` and `desktopMain` (one copy each, as `JvmFileStorage` already is — the two cannot share a source
  set until the `roomMain` hierarchy template is gone):
  ```kotlin
  actual fun String.normalizedToNfc(): String = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFC)
  ```
  `java.text.Normalizer` is on Android from API 9, well below `android-minSdk`.
- `iosMain`:
  ```kotlin
  @OptIn(BetaInteropApi::class)
  actual fun String.normalizedToNfc(): String = NSString.create(string = this).precomposedStringWithCanonicalMapping
  ```
  `NSString.create(string = …)` is the idiom already used in this repo
  (`SecretStore.ios.kt:102`).
- `wasmJsMain`:
  ```kotlin
  actual fun String.normalizedToNfc(): String = normalizeToNfc(this)

  private fun normalizeToNfc(value: String): String = js("value.normalize('NFC')")
  ```
  A `js(...)` block returning a `String` directly is the existing pattern
  (`BrowserRoutes.kt:202`). No Kotlin lambda crosses the boundary.

Do **not** put the expect in `commonMain` of `:data:model` and reach for `java.text` anywhere in common code —
`commonMain` stays JVM-free.

### 2. `normalizedName` composes first

`LibraryFiles.kt:100-103`:

```kotlin
    fun normalizedName(base: String): String {
        val folded = StringBuilder()
        var isAfterForeignCharacter = false
        // Composed first: the same name is decomposed on macOS and iOS and composed everywhere else, and the fold
        // below keeps a mark that follows a non-Latin letter, so the two forms would be two names. Composing is also
        // what makes the rule idempotent across platforms - a name this function produced on a Mac is handed back to
        // it by the file system in the other form.
        for (character in base.normalizedToNfc().lowercase()) {
```

Order matters: normalize **before** `lowercase()`. NFC and case folding do not commute for every script (the Greek
final sigma and the Turkish dotted I are the usual examples), and composing first is what the file systems and
Dropbox do.

Note what this does *not* change: Latin names are already handled, so every existing assertion in
`FileNamesTest.kt` — `edith`, `gesi_za_woda`, `isik`, `viet_nam` — keeps its result. The Vietnamese case
(`FileNamesTest.kt:171`) is the one to watch: `Việt` composed is U+1EC7, which `isLatin()` matches via
`'Ḁ'..'ỿ'`, and decomposed it is `e` + U+0323 + U+0302; both already fold to `viet`, and NFC leaves the
composed form alone, so the test result is unchanged either way. Run it and confirm.

### 3. Sync's key folding composes too

`SyncEngine.kt:81`:

```kotlin
/**
 * Two spellings a service takes for one file: case, which Dropbox ignores, and Unicode form, which the file systems
 * disagree about - a name an iPhone hands out decomposed is the same file as the composed one the service holds.
 */
private fun SyncKey.folded() = copy(name = name.normalizedToNfc().lowercase())
```

This is what makes `foldRemoteNamesOntoLocal` match a decomposed local name against a composed remote one and
give the remote file the local spelling, and `foldIndexNamesOntoListings` move an index entry across a change of
form. Both functions already have exactly the right shape for it; they were written for case and the same
machinery answers this.

`:data:repository:implementation` depends on `:data:model` already, so the import is free.

### 4. Existing files keep their names

**State this explicitly, in the code and in the docs, because it is the thing a reader will worry about.** Nothing
renames anything. `normalizedName` is consulted when a name is *derived* — a song written in the editor, an import,
an export, a setlist whose title changed — and `Song.canUpdateFileName` compares a file's name against what its own
metadata would name it. A library file whose name is decomposed goes on being decomposed until:

- the user takes **Update file name** from its menu, which is the one thing in the app that renames a song file
  (`RenameSongFileUseCase`), or
- a setlist's title changes, which moves its file because a setlist's name records nothing.

After this change, `canUpdateFileName` *will* start returning true for a decomposed song file whose metadata is
otherwise in step — the derived name is now composed and the file's name is not — so those songs grow an
**Update file name** entry in their menu. That is correct and is the mechanism by which a library converges, one
file at a time, at the user's request. It is also worth checking against `isNamed`
(`FileNames.kt`), which reads `LibraryFiles.withoutCollisionSuffix` to answer whether a file already carries the
name it would be given: **compare the two names after composing both**, or every decomposed file offers a rename
that, once taken, offers itself again. Concretely, wherever `isNamed` or `canUpdateFileName` compares a stored
name to a derived one, compose the stored one first.

### 5. Idempotency

The root `CLAUDE.md` states the invariant: "The rule is idempotent, which it has to be, since a name that left the
app is normalized again on its way back in." NFC is idempotent by definition (`NFC(NFC(x)) == NFC(x)`), and the
rest of `normalizedName` was already idempotent, so the composition is. The test at `FileNamesTest.kt:62-66`
already asserts it over a list of inputs:

```kotlin
            val once = LibraryFiles.normalizedName(base)
            …
            assertEquals(once, LibraryFiles.normalizedName(once))
```

Add the decomposed inputs to whatever list feeds that loop rather than writing a second idempotency test.

## Tests

`data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/FileNamesTest.kt`
— this is where `normalizedName` is tested today, and it runs on the desktop target, so the JVM actual is what is
exercised:

1. `files a decomposed name and its composed twin under one name`:
   ```kotlin
   assertEquals("катюша", LibraryFiles.normalizedName("Катюша"))
   assertEquals("катюша", LibraryFiles.normalizedName("Катюйа".…))  // write the decomposed form explicitly
   ```
   Use explicit escapes for both forms so the test does not depend on the source file's own encoding. Cover
   Cyrillic (`й` = `и` + U+0306), Greek (`ά` = `α` + U+0301 — and check against the existing
   `ελλάδα_μου` assertion at line 130, which must keep its result), Hebrew with points, and Vietnamese
   (`ệ` = `e` + U+0323 + U+0302, which is the three-code-point case).
2. `leaves a name that has no composed form alone` — Devanagari `हिन्दी` (the virama is not a combining mark that
   composes) must still fold to `हिन्दी`, keeping the existing assertion at line 134 green. This is the guard
   against NFC quietly eating a script's structure.
3. Extend the idempotency loop at lines 62-66 with the decomposed inputs from case 1, and assert additionally
   that `normalizedName(decomposed) == normalizedName(composed)` for each.
4. Confirm the byte cap still behaves: `assertEquals("я".repeat(60), LibraryFiles.normalizedName("я".repeat(300)))`
   (line 141) must stay green — NFC does not change the length of an already-composed string, but a decomposed
   input of 300 marks composes to fewer code points and so to a *different* cap boundary. Add one case with a
   decomposed input near the cap and assert the result is the same as for the composed one, which is the property
   that matters.

`data/repository/implementation/src/commonTest/kotlin/.../sync/SyncEngineTest.kt` — next to
`a file whose remote name differs only by case is not uploaded as a new one` (line 646):

5. `a file whose remote name differs only by Unicode form is not uploaded as a new one` — local
   `FakeLibraryFileLocalSource(files = mapOf(song("катюша.cho") to bytes))` with the composed name, provider
   holding the decomposed one. Assert the index has one entry, the local file keeps its name, and
   `provider.files.keys` has one key.
6. `an index entry follows a name across a change of Unicode form` — the `foldIndexNamesOntoListings` case:
   index keyed by the decomposed name, both listings holding the composed one. Assert no download and no
   deletion, and that the entry moved.

There is no test module for `:data:model` itself; its rules are tested from
`:data:source:local:implementation`'s `commonTest`, as its own `CLAUDE.md` says ("It is tested from
`:data:source:local:implementation`, since this module has no tests of its own"). Keep it that way — do not add a
test source set to `:data:model` for this.

## Verification

```
./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
./gradlew :app:android:assembleDebug
./gradlew :app:desktop:run
./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64
./gradlew :app:web:wasmJsBrowserDistribution
```

The four `actual`s each only compile on their own target, so all four build commands are part of the verification,
not optional.

Manual:

1. **Needs a Mac and one other platform.** On the desktop build, create a song titled `Катюша` and export it.
   Import the exported file back into the same library: it must be disregarded as a duplicate, not numbered
   `катюша_2.cho`. (This works today on desktop; it is the baseline.)
2. **Needs an iOS device or simulator.** Create the same song on iOS, export it, and import that file on desktop
   (and on Android, and in the browser). It must be disregarded on all three. Before the change it is numbered.
   The `MEMORY.md` note "iOS target verification" has the simulator recipe.
3. **Needs a Dropbox account and two platforms, one of them iOS or macOS.** Sync the song up from iOS and down
   onto desktop, then sync again from each. After two full rounds, Dropbox must hold exactly one `катюша.cho` and
   each device exactly one file.
4. Check the song's **Update file name** menu entry on a library that already holds a decomposed file: it should
   offer itself once, and after being taken must not offer itself again. This is the check for point 4 above and
   is the most likely place for a bug.
5. Repeat 1 with Greek, Hebrew and Vietnamese titles.

## Docs

`data/model/CLAUDE.md` — this is the sentence that describes the fold and now omits its first step:

> `normalizedName` is the other shared rule — lowercase unaccented words joined with underscores, capped and never
> empty — and `NORMALIZED_ARTIST_TITLE_SEPARATOR` the bare dash that joins the two halves of a song's name once each
> has been through it.

and this one, which says the decomposed case is already handled and is now only half the story:

> Next to it, `isCombiningMark` is what both drop after the fold, so a title that arrives decomposed (an `e`
> followed by U+0301) names and finds itself the way the composed spelling does.

That is true for Latin only; say that the input is brought to NFC first, which is what makes it true for every
script, and name the new `normalizedToNfc` as an expect/actual. Also revisit the module's opening line — "Pure
Kotlin domain models with no dependencies" — which stays true but now has platform source sets behind it.

Root `CLAUDE.md`, the Conventions section — this sentence lists the folding rules and needs NFC among them:

> **Every name the app writes is normalized** — lowercase words joined with underscores, Latin letters without
> their accents and letters of every other script kept as they are (`катюша.cho`), capped at 120 UTF-8 bytes per
> half (`LibraryFiles.normalizedName`) …

and this one, which is the invariant NFC has to preserve:

> The rule is idempotent, which it has to be, since a name that left the app is normalized again on its way back in.

Add one sentence saying that existing files keep their names until **Update file name** or a setlist retitling
moves them, so that a reader does not expect a migration.

Root `CLAUDE.md`, the Sync section — the case-folding sentence in
`data/repository/implementation/CLAUDE.md` is the one that changes:

> Names are matched by case where a service ignores it: a remote name that differs from a local one only by case
> takes the local spelling (`foldRemoteNamesOntoLocal`), and an index entry whose name neither listing has moves to
> the one listed spelling that folds to it (`foldIndexNamesOntoListings`).

"by case" is now "by case or Unicode form".

`documentation/file-format.md` — read it; if it documents what a library file is called, the NFC rule belongs
there too.

## Files touched

- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/Normalization.kt` (new, expect)
- `data/model/src/androidMain/kotlin/com/pandulapeter/campfire/data/model/domain/Normalization.android.kt` (new)
- `data/model/src/desktopMain/kotlin/com/pandulapeter/campfire/data/model/domain/Normalization.desktop.kt` (new)
- `data/model/src/iosMain/kotlin/com/pandulapeter/campfire/data/model/domain/Normalization.ios.kt` (new)
- `data/model/src/wasmJsMain/kotlin/com/pandulapeter/campfire/data/model/domain/Normalization.wasmJs.kt` (new)
- `data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryFiles.kt`
- `data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngine.kt`
- `data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/FileNames.kt` (the `isNamed` comparison)
- `data/source/local/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/FileNamesTest.kt`
- `data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/sync/SyncEngineTest.kt`
- `data/model/CLAUDE.md`, `data/repository/implementation/CLAUDE.md`, `CLAUDE.md`, `documentation/file-format.md`

## Depends on

Nothing. It edits `SyncEngine.kt` — as plans 01 and 03 do, in different functions — so rebase rather than merge.

## Rules

- Load the `code-style` skill before the first edit. Five new files, each needing the MPL-2.0 header.
- `commonMain` stays JVM-free: `java.text.Normalizer` appears only in `androidMain` and `desktopMain`.
- No Kotlin lambda crosses into the `js(...)` block, and the wasm actual is one boundary crossing per call.
- No new UI strings.
- The per-module `CLAUDE.md` files are part of the change.
