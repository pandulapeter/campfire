<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Give the song name rule and `Logger.recovering` one home each, in `:data:model`

**Kind:** structure  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** amended — `ui/screens/export/CLAUDE.md` sits exactly at its 832-word allowance, so the rewritten
sentence now keeps its word count (the plan's added four words and would fail `check_sizes.py`). The `:data:model`
dependency checks out: only `commonTest` gains `kotlinx-coroutines-test` (main code uses the stdlib's
`CancellationException`, the type `kotlinx.coroutines.CancellationException` aliases on every target), and `Logger.log`
is public, as a public `inline` function needs.
**Files:**
`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryFiles.kt`,
`data/model/src/commonMain/kotlin/com/pandulapeter/campfire/data/model/domain/Logger.kt`,
`data/model/build.gradle.kts`,
`data/model/src/commonTest/kotlin/com/pandulapeter/campfire/data/model/domain/LibraryFilesTest.kt`,
`data/model/src/commonTest/kotlin/com/pandulapeter/campfire/data/model/domain/RecoveringTest.kt` (new, moved),
`data/model/CLAUDE.md`,
`data/source/local/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/FileNames.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/print/PrintFileName.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/firstRun/DemoLibrary.kt` (KDoc),
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/firstRun/DemoLibraryTest.kt`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/export/CLAUDE.md`,
`data/repository/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/repository/implementation/base/Recovering.kt` (deleted),
`data/repository/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/repository/implementation/base/RecoveringTest.kt` (deleted),
`data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/Recovering.kt` (deleted),
`data/sync/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/sync/implementation/RecoveringTest.kt` (deleted),
the callers of `recovering(`: in `data/repository/implementation/.../implementation/` `CatalogueSearch.kt`,
`CoverArtRepositoryImpl.kt`, `LibraryDeletion.kt`, `SetlistRepositoryImpl.kt`, `SongContentRepositoryImpl.kt`,
`SongDetailsRepositoryImpl.kt`, `SongRepositoryImpl.kt`, and in `data/sync/implementation/.../implementation/`
`ConflictResolver.kt`, `DemoLibraryRepositoryImpl.kt`, `SyncConnectionManager.kt`, `SyncEngine.kt`, `SyncIndexStore.kt`
(imports only)

## Problem

**The song name rule is written out three times.** A song's library name is its artist and title, each run through
`LibraryFiles.normalizedName`, joined by `LibraryFiles.NORMALIZED_ARTIST_TITLE_SEPARATOR`, the title alone when the
artist is blank. It lives in the local source's `FileNames.kt`:

```kotlin
internal fun songFileName(title: String, artist: String, extension: String = SONG_EXTENSION): String {
    val name = LibraryFiles.normalizedName(title)
    val base = if (artist.isBlank()) name else LibraryFiles.normalizedName(artist) + LibraryFiles.NORMALIZED_ARTIST_TITLE_SEPARATOR + name
    return base + extension
}
```

and is copied into `:presentation`'s `print/PrintFileName.kt`, whose KDoc admits it ("That rule lives in the local
source, which this module does not see, so it is spelled out here from [LibraryFiles]"):

```kotlin
song.artist.isNullOrBlank() -> LibraryFiles.normalizedName(song.title)
else -> LibraryFiles.normalizedName(song.artist) + LibraryFiles.NORMALIZED_ARTIST_TITLE_SEPARATOR +
        LibraryFiles.normalizedName(song.title)
```

and again into `DemoLibraryTest.kt`:

```kotlin
val title = LibraryFiles.normalizedName(metadata.displayTitle(fallback = ""))
val artist = metadata.artist.orEmpty()
val expected = if (artist.isBlank()) title else LibraryFiles.normalizedName(artist) + LibraryFiles.NORMALIZED_ARTIST_TITLE_SEPARATOR + title
```

A change to the rule (a new separator, a cap on the joined name) would have to be made in all three, and only the first
is covered by `FileNamesTest`; a PDF named by a stale copy would no longer be the name the song's own export uses
(`ui/screens/export/CLAUDE.md`: "`pdfFileName` names it like `ExportFileNames.kt` names a song").

**`Logger.recovering` exists twice, byte for byte**, with a test each that differs only in its package:
`data/repository/implementation/.../base/Recovering.kt` and `data/sync/implementation/.../Recovering.kt`
(`git diff --no-index` of the two `RecoveringTest.kt` files shows only the `package` line). The sync copy's KDoc
explains the duplication: "The repositories' module has the same eight lines: neither module sees the other's internals,
and a module of its own for them would be more than they are." Both modules already depend on `:data:model`, where
`Logger` itself lives, so the function can sit beside it with no new module.

## Fix

**Commit 1 — `LibraryFiles.songBaseName`.**

- In `LibraryFiles` (`:data:model`, `domain/LibraryFiles.kt`), next to `normalizedName`, add

  ```kotlin
  /**
   * The name a song's own header gives it, without an extension: [artist] and [title] normalized one at a time
   * ([normalizedName]) and joined by [NORMALIZED_ARTIST_TITLE_SEPARATOR], so that the dash survives as structure
   * (`tukorfurogep-arviz`); a song with a blank artist is named after its title alone. The library stores a song under
   * it, every export hands it out under it, and the PDF of a song is named after it.
   */
  fun songBaseName(title: String, artist: String): String {
      val name = normalizedName(title)
      return if (artist.isBlank()) name else normalizedName(artist) + NORMALIZED_ARTIST_TITLE_SEPARATOR + name
  }
  ```

  and make `NORMALIZED_ARTIST_TITLE_SEPARATOR` `private` (after this commit nothing outside `LibraryFiles` reads it;
  check with `git grep NORMALIZED_ARTIST_TITLE_SEPARATOR`). Keep its KDoc.
- `FileNames.songFileName` becomes `LibraryFiles.songBaseName(title, artist) + extension`; its KDoc keeps the
  `@param extension` paragraph and points at `songBaseName` for the rule.
- `PrintFileName.pdfFileName`: the two song branches become one,
  `else -> LibraryFiles.songBaseName(title = song.title, artist = song.artist.orEmpty())` (`PrintSong.artist` is
  `String?`; `isBlank` on `""` is what `isNullOrBlank` was on `null`, so behaviour is identical). Rewrite the KDoc
  sentence quoted above to "by the rule the library and every other export name a song by
  ([LibraryFiles.songBaseName])".
- `DemoLibraryTest`: `val expected = LibraryFiles.songBaseName(title = metadata.displayTitle(fallback = ""), artist = metadata.artist.orEmpty())`.
- `DemoLibrary.kt` KDoc: "(`LibraryFiles.normalizedName(artist)-normalizedName(title).cho`)" →
  "(`LibraryFiles.songBaseName` plus `.cho`)".
- `data/model/CLAUDE.md`, "Identity and names": add "`songBaseName` is the one song naming rule (artist, dash, title,
  each normalized; the title alone without an artist): the local source, the PDF export, and the demo library test use
  it." `ui/screens/export/CLAUDE.md`: "`pdfFileName` names it like `ExportFileNames.kt` names a song" → "`pdfFileName`
  names it by `LibraryFiles.songBaseName`, like the library" — the same eight words, since the file is at its
  allowance (832) and may not grow; run `python3 .github/scripts/check_sizes.py` after. `data/model/CLAUDE.md` (858 of
  1,500) has room for its sentence.

**Commit 2 — `Logger.recovering` beside `Logger`.**

- Move the function into `data/model/.../domain/Logger.kt` (recommended over a file of its own: plan 16 keeps `Logger`
  at the root of `:data:model`, and the two travel together), public (`:data:model` has no `internal` contract for its
  callers), with the shared KDoc minus the sync copy's last paragraph. `:data:model` has no coroutines dependency, so
  import the stdlib `kotlin.coroutines.cancellation.CancellationException`; `kotlinx.coroutines.CancellationException`
  is a `typealias` of it, so the catch is the same.

  ```kotlin
  inline fun <T> Logger.recovering(
      describe: (Exception) -> String,
      fallback: (Exception) -> T,
      block: () -> T,
  ): T = try {
      block()
  } catch (exception: CancellationException) {
      throw exception
  } catch (exception: Exception) {
      log(describe(exception))
      fallback(exception)
  }
  ```

- Delete both `Recovering.kt` copies. In the repository module the callers imported
  `com.pandulapeter.campfire.data.repository.implementation.base.recovering`, in the sync module the callers were in the
  same package and imported nothing; every caller listed under **Files** now imports
  `com.pandulapeter.campfire.data.model.domain.recovering` (let the compiler find any missed one).
- Move one `RecoveringTest.kt` to `data/model/src/commonTest/kotlin/com/pandulapeter/campfire/data/model/domain/`, its
  package changed, with a private recording `Logger` inline in it (the modules' `RecordingLogger` classes stay where
  they are; other tests use them), and delete the other copy. The test uses `runTest` and `delay`, so add
  `commonTest.dependencies { implementation(libs.kotlin.coroutines.test) }` to `data/model/build.gradle.kts`, as
  `data/sync/implementation/build.gradle.kts` does. A test-only dependency ships in no build, so no Open-source
  components entry is owed.

Out of scope: the other modules' own `catch (exception: CancellationException) { throw exception }` blocks
(`:data:source:local:implementation`, `:data:source:remote:implementation`) log differently or not at all; folding them
into `recovering` is a separate judgment per call site, not part of removing a duplicate.

## Tests

- `LibraryFilesTest` (`:data:model`): a `songBaseName` test with the cases `FileNamesTest` already pins for
  `songFileName` without an extension: `("Árvíz", "Tükörfúrógép")` → `tukorfurogep-arviz`, `("Árvíz", "")` → `arviz`,
  `("Árvíz", " ")` → `arviz`, `("Группа крови", "Кино")` → `кино-группа_крови`. `FileNamesTest` stays as it is (it pins
  the extension and the folding cases through the local source's entry point).
- `PrintFileNameTest` and `DemoLibraryTest` run unchanged and must pass.
- `RecoveringTest`, moved, runs in `:data:model:desktopTest`.

Verify with the root CLAUDE.md test command, plus `:app:desktop:compileKotlin`, `:app:android:compileDebugKotlin`,
`:app:web:compileKotlinWasmJs`, `:app:ios:linkDebugFrameworkIosSimulatorArm64`, and `spotlessCheck`; then
`git grep -n "NORMALIZED_ARTIST_TITLE_SEPARATOR\|implementation.base.recovering"` must show only `LibraryFiles.kt`.
