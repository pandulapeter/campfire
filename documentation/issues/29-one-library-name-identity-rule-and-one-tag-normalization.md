# Write the library file-name identity rule (NFC + case) once as LibraryFiles.identityKey / isSameLibraryName, and the tag normalization once as normalizedTags, in :data:model

**Kind:** architecture  ·  **Severity:** medium  ·  **Effort:** M  ·  **Risk:** low  ·  **Platforms:** all
**Files:** `data/model/src/commonMain/.../data/model/domain/LibraryFiles.kt` (new `identityKey`, `isSameLibraryName`), new `data/model/src/commonMain/.../data/model/domain/Tags.kt` (`normalizedTags`); `data/model/build.gradle.kts` (a `commonTest` with `kotlin("test")`, since the module has no tests yet); new `data/model/src/commonTest/.../domain/LibraryNameIdentityTest.kt`, `.../domain/TagsTest.kt`; call sites: `data/repository/implementation/.../sync/SyncEngine.kt` (`SyncKey.folded`, the `KEEP_AND_UPLOAD` block's two `normalizedToNfc().lowercase()`), `.../sync/SyncedPreferencesSync.kt` (`String.folded`), `data/source/local/implementation/.../FileNames.kt` (`isSameFileNameAs`, `isNamed`), `domain/implementation/.../ImportPlanner.kt` (`isSpellingOf`, `familyKeys`), `domain/implementation/.../useCases/ImportFilesUseCaseImpl.kt` (`libraryNames`, `isLibraryFileGone`), `data/source/local/implementation/.../mapper/SongMappers.kt` (`tags = …`), `presentation/src/commonMain/.../ui/CampfireViewModel.kt` (`songForLabelEditing`), `presentation/src/commonMain/.../ui/screens/songEditor/SongEditorScreen.kt` (`toEditorSong`); `data/model/CLAUDE.md`, root `CLAUDE.md` (test command list gains `:data:model:desktopTest`; the Conventions bullet on names says "composes too")
**Depends on:** none. The concurrent lane moves sync name folding and splits `ImportFilesUseCaseImpl` / `PrepareImportUseCaseImpl`: find the sites by the quoted expressions, not by file.

## Problem

The rule "two library names are one file when they differ only by Unicode form or case" — the root CLAUDE.md's
"a name that differs from the normalized one only by case or by Unicode form is taken as that name" — is written out
at eight sites in four modules, in **two different spellings**:

| Site | Expression | Rule |
|---|---|---|
| `SyncEngine` `SyncKey.folded()` | `name.normalizedToNfc().lowercase()` | full lowercase |
| `SyncEngine` `KEEP_AND_UPLOAD` (`forgottenSongs`, `withSongsWhere`) | `normalizedToNfc().lowercase()` ×2 | full lowercase |
| `SyncedPreferencesSync` `String.folded()` | `normalizedToNfc().lowercase()` | full lowercase |
| `ImportPlanner.familyKeys` | `dropLast(…).normalizedToNfc().lowercase()` | full lowercase |
| `ImportFilesUseCaseImpl` (`libraryNames`, `isLibraryFileGone`) | `fileName.normalizedToNfc().lowercase()` ×2 | full lowercase |
| `FileNames.isSameFileNameAs` | `normalizedToNfc().equals(other.normalizedToNfc(), ignoreCase = true)` | per-char ignore-case |
| `FileNames.isNamed` | `base.equals(desiredBase, ignoreCase = true)` (base NFC'd; `desired` is already a `normalizedName`) | per-char ignore-case |
| `ImportPlanner.isSpellingOf` | `normalizedToNfc().equals(name.normalizedToNfc(), ignoreCase = true)` | per-char ignore-case |

The two rules are **not** the same relation. Kotlin's `String.equals(other, ignoreCase = true)` compares lengths, then
each `Char` by `uppercaseChar()` and then `lowercaseChar()` — exactly "equal after mapping every char through
`uppercaseChar().lowercaseChar()`" (simple, one-to-one case mapping). `String.lowercase()` is the full, locale-invariant
Unicode lowercase, which can change the length and is context-sensitive. They disagree on, for example:
- `İ` (U+0130) vs `i`: equal under `ignoreCase` (`'İ'.lowercaseChar() == 'i'`); different under `lowercase()`
  (`"İ".lowercase() == "i̇"`, two chars).
- Greek final sigma: `"ΟΔΟΣ"` vs `"οδοσ"`: equal under `ignoreCase`; under `lowercase()` the JVM applies the Final_Sigma
  rule (`"οδος"` ≠ `"οδοσ"`), and whether Kotlin/Native and Kotlin/Wasm do the same is up to each runtime's
  implementation — so the full-lowercase sites may not even agree with themselves across platforms.
- `ς` vs `σ` typed directly: equal under `ignoreCase`, different under `lowercase()`.

`ImportFilesUseCaseImpl` even says its names are "folded the way the storage layer compares names" — but the storage
layer uses the other rule. None of these keys is persisted (all are in-memory matching), so the difference only bites
on exotic hand-named or remote names; the cost today is that each site has to be re-derived and nobody can say which
rule "the" rule is.

Separately, the tag normalization `tags.map { it.normalizedToNfc() }.distinctBy { it.lowercase() }` is written three
times (`SongMappers` at scan time, `CampfireViewModel.songForLabelEditing`, `SongEditorScreen`'s `toEditorSong`); the
three must agree or a tag sheet opened from the editor shows a different set from the list.

## Fix

1. Add to `LibraryFiles` (it is the home of `normalizedName` and `withoutCollisionSuffix`):

   ```kotlin
   /**
    * The one key under which two spellings of a library name are the same file: composed to NFC, then every char
    * mapped through uppercaseChar().lowercaseChar() - exactly what String.equals(ignoreCase = true) compares - so the
    * key and [isSameLibraryName] always agree, on every platform (simple case mappings come from Kotlin's own tables).
    */
   fun identityKey(name: String): String
   fun isSameLibraryName(first: String, second: String): Boolean = identityKey(first) == identityKey(second)
   ```

   (Step 1 alone changes no caller.) Add `data/model`'s `commonTest` source set and `LibraryNameIdentityTest`.
2. Replace the three **ignore-case** sites (`isSameFileNameAs`, `isNamed`'s two comparisons, `isSpellingOf`) with
   `isSameLibraryName` — identical behaviour by construction (prove with the test's equivalence property). One commit.
3. The five **lowercase** sites: see Decision. With the recommended option, replace each `normalizedToNfc().lowercase()`
   (and `SyncKey.folded`/`String.folded` bodies) with `LibraryFiles.identityKey(…)`. One commit, its message naming the
   behaviour change (İ/ı, final sigma, other one-to-many lowercase mappings now match the file-system sites).
   With option B, introduce `LibraryFiles.foldedKey(name) = name.normalizedToNfc().lowercase()` instead, use it at the
   five sites, and document the two rules side by side.
4. Add `fun normalizedTags(tags: Iterable<String>): List<String> = tags.map { it.normalizedToNfc() }.distinctBy { it.lowercase() }`
   in a new `Tags.kt` in `:data:model` (tag identity is a separate question from file names — keep `lowercase()` there,
   unchanged), and use it at the three tag sites. `:presentation` sees `:data:model` through `:domain:api`. One commit.
5. Update `data/model/CLAUDE.md` (the `LibraryFiles.kt` bullet: "`identityKey`/`isSameLibraryName` are the one rule for
   whether two names are one file"), root CLAUDE.md's test command (`:data:model:desktopTest`), and the comment in
   `ImportFilesUseCaseImpl` (it becomes true).

## Tests

- `LibraryNameIdentityTest`: composed vs decomposed (`"é"` vs `"é"`, `"й"` vs `"й"`) are one name; `Song.cho`
  vs `song.cho`; `İ`/`i`, `ΟΔΟΣ`/`οδοσ`, `ς`/`σ`, `ẞ`/`ß` are one name; `a.cho` vs `b.cho` are not; and a property
  check over a fixed list of a few hundred mixed-script strings (Latin, Greek, Cyrillic, Turkish, German, CJK, emoji):
  `isSameLibraryName(a, b) == a.normalizedToNfc().equals(b.normalizedToNfc(), ignoreCase = true)` for every pair —
  this pins step 2 as behaviour-preserving. Keep supplementary-plane cased letters (Adlam, Deseret, Osage) out of that
  property list and test them separately: on the JVM `equals(ignoreCase)` is `java.lang.String.equalsIgnoreCase`,
  which in current JDKs compares by code point, while Kotlin/Native and Kotlin/Wasm compare `Char` by `Char`. Verify
  on the desktop target which way `"𞤀" vs "𞤢"` (Adlam capital/small alif) goes; if the JVM says equal, `identityKey`
  must fold by code point too (decode surrogate pairs and map each through a small table, or accept and document that
  the per-char key is the Native/Wasm behaviour) — decide in the commit, and pin it with a test.
- `TagsTest`: decomposed and composed spellings collapse to one; `Rock`/`rock` keep the first spelling; order is kept.
- Guards: `FileNamesTest` (`isNamed` cases at lines ~136–144), `ImportPlannerTest`, `SyncEngineTest` (fold tests),
  `SyncedPreferencesTest` (spelling tests), `SongMappersTest`, `RenameTest`/`ExportNameTest` (desktop).

## Manual check

none — covered by tests (optionally on a Mac: a song file renamed by hand to `Song.CHO` with a decomposed accent still
reads as the same song, offers no Update file name, and syncs without a duplicate).

## Decision

Which rule should the five full-lowercase sites (sync folding, synced-preferences matching, the import's family keys and
"library file gone" check) follow?

- **A — unify on `identityKey` (per-char simple case fold, what `equals(ignoreCase = true)` does). Recommended.** One
  rule everywhere, the same on every platform, and it is the rule the storage layer already uses to decide what a
  rename or a free name is. Behaviour changes only for names whose full lowercase differs from their per-char fold
  (Turkish dotted capital I, Greek final sigma, and the few one-to-many lowercase mappings); none of these keys is
  stored, so nothing on disk or in the cloud folder changes.
- **B — keep both rules, but name them** (`identityKey` for the ignore-case sites, `foldedKey` for the lowercase ones).
  Zero behaviour change, but the codebase keeps two answers to "is this the same file", and the sync side keeps a rule
  that may differ between platforms for Greek names.
- **C — unify on full `lowercase()`.** Rejected: it would change the storage layer's rename/free-name decisions, the
  part of the app that can overwrite or duplicate a file.
