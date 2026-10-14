<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Split `:chordpro`'s `chords` package into chords, voicings, and tabs, and give each package its own CLAUDE.md

**Challenged:** amended — ordering against plans 11–13 stated (their moves and this plan's import rewrite touch the
same `:presentation` files); the plan-15 overlap is one file once plan 08 has landed, not six; the test files outside
the module are counted.

**Kind:** structure · **Severity:** low · **Platforms:** all
**Files:** every file under `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/chords/`,
`chordpro/.../chordpro/ChordNotation.kt`, `chordpro/.../chordpro/edit/ChordProTabWrapper.kt`, their tests under
`chordpro/src/commonTest/.../chordpro/chords/`, `chordpro/src/commonTest/.../edit/ChordProTabWrapperTest.kt`,
`chordpro/src/desktopTest/.../chords/ChordVoicingContactSheetTest.kt`; about 20 other files of `:chordpro` (imports and
KDoc links only); 55 files of `:presentation` and `:domain:implementation` (imports only, listed by the grep below);
`chordpro/CLAUDE.md`; new `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/{chords,voicings,tabs,edit,convert}/CLAUDE.md`

Depends on plan 08 (it rewrites many of the same `:presentation` imports); land 14 after it. It shares
`:domain:implementation` files with plan 15 (six at HEAD, only `PrepareImportUseCaseImpl.kt` once plan 08 has deleted
the forwarders and `NotationMappers.kt`), so run them in one lane, 14 first. Plans 11–13 move many of the `:presentation` files
whose imports this plan rewrites (e.g. `ChordCellsTest`, `RenderSectionsTest`, `ChordShapeGridTest`, every
`screens/songDetails` and `dialogs` importer of `ChordNotation`): merge this lane after theirs and run the
substitutions below on the merged tree, rather than resolving import-block conflicts by hand.

## Problem

`chordpro/.../chords/` holds 20 files (11,000 lines, two thirds of them tables) on three separate concerns:

- reading and writing chord **names** — `ChordProChordNames`, `ChordProChords`, `ChordProNashville`,
  `ChordProNotation`, `ChordProTransposer`;
- the **shapes** a chord is played with — `ChordVoicings`, `ChordVoicingSearch`, `ChordVoicingTables`,
  `ChordShapeGeometry`, `ChordProDefinitions`, and the tables `GuitarChordTable`, `DropDChordTable`,
  `UkuleleChordTable`, `MandolinChordTable`, `BanjoChordTables`, `OpenTuningChordTables`;
- **tablature** — `ChordProTabs`, `ChordProTabTransposer`, `ChordProTabRetuner`, `ChordProStaffNames`.

Meanwhile the fourth tab file, `ChordProTabWrapper`, sits in `edit/` (it edits no text: it cuts a run into rows for the
reader), and `ChordNotation`, the enum the notation code is built around, sits alone at the module root. The module's
`CLAUDE.md` is at 1,471 of its 1,500 words, with the shapes and tabs rules in it rather than beside the code.

One KDoc link is already broken by the earlier move of the wrapper into `edit/`, in `model/ChordProLine.kt`:

```kotlin
     * too wide (see [com.pandulapeter.campfire.chordpro.ChordProTabWrapper]); transposed on the frets, not on the
```

## Fix

Behaviour is unchanged: only packages, imports, and docs move. `:chordpro` is in explicit API mode; every moved
declaration keeps its visibility (`public object ChordProChords`, `internal object ChordProStaffNames`, …). The
cross-package references the split creates are all within the module, where `internal` still reaches, so nothing
becomes public.

### Target layout (`com.pandulapeter.campfire.chordpro.` prefix)

| File | Now | After |
|---|---|---|
| `ChordNotation.kt` | root | `chords` |
| `ChordProChordNames.kt` (with `ChordNameReader`) | `chords` | `chords` |
| `ChordProChords.kt` | `chords` | `chords` |
| `ChordProNashville.kt` | `chords` | `chords` |
| `ChordProNotation.kt` | `chords` | `chords` |
| `ChordProTransposer.kt` | `chords` | `chords` |
| `ChordVoicings.kt` | `chords` | `voicings` |
| `ChordVoicingSearch.kt` | `chords` | `voicings` |
| `ChordVoicingTables.kt` | `chords` | `voicings` |
| `ChordShapeGeometry.kt` | `chords` | `voicings` |
| `ChordProDefinitions.kt` | `chords` | `voicings` |
| `GuitarChordTable.kt`, `DropDChordTable.kt`, `UkuleleChordTable.kt`, `MandolinChordTable.kt`, `BanjoChordTables.kt`, `OpenTuningChordTables.kt` | `chords` | `voicings` |
| `ChordProTabs.kt` | `chords` | `tabs` |
| `ChordProTabTransposer.kt` | `chords` | `tabs` |
| `ChordProTabRetuner.kt` | `chords` | `tabs` |
| `ChordProStaffNames.kt` | `chords` | `tabs` |
| `ChordProTabWrapper.kt` | `edit` | `tabs` |

Unchanged: the root (`ChordProParser`, `ChordProSplitter`, `ChordProSummaryCache`, `ChordProDuration`,
`ChordProTempo`, `ChordProTime`), `model/` (the tab and tuning model types `TabPlayer`, `TabTuning`, `TabNotice`,
`ChordTuning`, `ChordVoicing` stay with the rest of the model: the model package is settled, and the Compose stability
list in `gradle/compose-stability.conf` names model classes by their full names), `syntax/`, `edit/` (minus the
wrapper), `convert/`.

`ChordProStaffNames` goes to `tabs` although `ChordProNotation` also calls it (respelling staff names): it is about
staffs, imports `ChordProTabWrapper` and `ChordProTabTransposer.SEMITONES_IN_OCTAVE`, and is otherwise used only by
`ChordProTabRetuner`.

Tests move with their code (same file names, package lines changed):

| Test | After |
|---|---|
| `commonTest/.../ChordProChordNamesTest`, `ChordProChordsTest`, `ChordProLatinNotationTest`, `ChordProNashvilleTest`, `ChordProNotationTest`, `ChordProTransposerTest` | `chords` (unchanged) |
| `commonTest/.../ChordProDefinitionsTest`, `ChordProDefinitionsTranspositionTest`, `ChordShapeGeometryTest`, `ChordVoicingTablesTest`, `ChordVoicingsTest` | `voicings` |
| `desktopTest/.../ChordVoicingContactSheetTest` | `voicings` |
| `commonTest/.../ChordProTabTransposerTest`, `ChordProTabsTest` | `tabs` |
| `commonTest/.../edit/ChordProTabWrapperTest` | `tabs` |

### Commits (each compiles and passes `:chordpro:desktopTest`)

1. `Move the tablature code of :chordpro into a tabs package.` — the five `tabs` files and three tests (`git mv`, then
   the `package` line). Then rewrite every reference repository-wide with one substitution per name, which covers
   imports and fully qualified KDoc links alike:
   `chordpro.chords.ChordProTabs` → `chordpro.tabs.ChordProTabs` (same for `ChordProTabTransposer`,
   `ChordProTabRetuner`, `ChordProStaffNames`), `chordpro.edit.ChordProTabWrapper` → `chordpro.tabs.ChordProTabWrapper`,
   and fix the broken `[com.pandulapeter.campfire.chordpro.ChordProTabWrapper]` in `model/ChordProLine.kt` to
   `[ChordProTabWrapper]` (its import is there). Files left in `chords` that used these names unqualified
   (`ChordProNotation`, `ChordProTransposer`, `ChordProChords`, tests such as `ChordProLatinNotationTest` and
   `ChordProNashvilleTest`) gain imports; the moved files gain imports of what stayed in `chords`
   (`ChordProChordNames`, `ChordProTransposer`, `ChordVoicings`, …). The compiler finds every code reference; for KDoc,
   grep the moved names in `[...]` in each file of the old and the new package and add the import (ktlint's
   `no-unused-imports` counts a KDoc reference as a use).
2. `Move the chord shapes of :chordpro into a voicings package.` — the eleven `voicings` files and six tests, same
   procedure (`chordpro.chords.ChordVoicings` → `chordpro.voicings.ChordVoicings`, and so on for the other ten names).
   Watch the static imports: `ChordVoicings.kt` imports `ChordVoicingSearch.search` etc. and `ChordVoicingSearch.kt`
   imports `ChordShapeGeometry.*` members by their full path; they move to `chordpro.voicings.…`.
3. `Move ChordNotation into :chordpro's chords package.` — `chordpro.ChordNotation` → `chordpro.chords.ChordNotation`
   (most of the 55 outside files import only this). Root files that used it unqualified (`ChordProParser`,
   `ChordProSplitter`, `ChordProSummaryCache`) gain an import.
4. `Give :chordpro's packages CLAUDE.md files of their own.` — the doc split below.

Find the files outside the module before and after each commit:

```
grep -rlE "chordpro\.(chords\.(ChordVoicings|ChordProDefinitions|ChordShapeGeometry|ChordVoicingSearch|ChordVoicingTables|ChordProTabs|ChordProTabTransposer|ChordProTabRetuner|ChordProStaffNames)|edit\.ChordProTabWrapper|ChordNotation\b)" --include='*.kt' . | grep -v '^./chordpro/' | grep -v /build/
```

(55 main files at the time of writing: 53 in `:presentation`, 2 in `:domain:implementation`, plus 14 test files of
`:presentation`; mostly `ChordNotation` and `ChordVoicings`. Inside `:chordpro`, 22 files. Where `grep` prints paths
without the leading `./`, drop the `./` from the `grep -v` patterns.) Nothing outside Kotlin names these packages: `gradle/compose-stability.conf` lists only `model`
classes, no `size_allowances.json` entry is under `chords/`, and no skill or other `CLAUDE.md` names the moved paths
(`domain/implementation/CLAUDE.md` and `presentation/.../songDetails/CLAUDE.md` name `ChordNotation` and
`ChordProTabWrapper` by class only, which stays true). Check each move with `git diff -M --color-moved=dimmed-zebra`:
only `package` and `import` lines and the one KDoc link may differ.

### CLAUDE.md split (commit 4)

Measured as `check_sizes.py` does (words after the license header): the module file is 1,471 words. Move each
section next to its code, cut nothing, and reword only the pointers:

| Section of `chordpro/CLAUDE.md` | Goes to | ≈ words |
|---|---|---|
| Intro, "Rules that span the module", "Model and parser" (keep the `ChordProSplitter` bullet from "Text editors" here: the splitter is a root file), "Tests" | stays in `chordpro/CLAUDE.md` | 500 |
| "Notation (`ChordNotation`, `ChordProNotation`)"; the `ChordProTransposer` bullet of "Transposition and tabs"; the `ChordProChords.parse` / `Chord.id` bullet of "Chords and shapes" | `chords/CLAUDE.md` | 260 |
| The rest of "Chords and shapes" (the tables, `ChordTuning`, the banjo drone, `ChordProDefinitions`) | `voicings/CLAUDE.md` | 185 |
| The rest of "Transposition and tabs" (`ChordProTabTransposer`, `ChordProTabs.select`, staff names, `TabNotice`, `ChordProTabWrapper`) | `tabs/CLAUDE.md` | 175 |
| "Text editors (`edit/`)" minus the splitter bullet, and "`ChordProHighlighter`" | `edit/CLAUDE.md` | 195 |
| "Chord sheet conversion (`convert/`)" | `convert/CLAUDE.md` | 90 |

Each package file starts with the license header and `# chordpro/<package>`; the section headings that named the
package (`(edit/)`, `(convert/)`) drop it. The module file says once, in its intro, that each package's rules are in
its own `CLAUDE.md`. Run `python3 .github/scripts/check_sizes.py` (every file within budget) and
`python3 .github/scripts/check_license_headers.py`. Pointers elsewhere (`presentation/.../ui/CLAUDE.md`'s "see
`chordpro/CLAUDE.md`'s `ChordProTabs`" and "How a tab is moved … is `chordpro/CLAUDE.md`'s" → `tabs/CLAUDE.md`;
`presentation/.../ui/chords/CLAUDE.md`'s `ChordProDefinitions` pointer → `voicings/CLAUDE.md`; `domain/api/CLAUDE.md`'s
notation rule pointer → `chords/CLAUDE.md`; `documentation/plans/clipboard-import.md` names the module file generally
and stays); grep `chordpro/CLAUDE.md` across all `CLAUDE.md` files and `.claude/skills` and
re-aim each one at the file that now holds the rule.

## Tests

None new: a move changes no logic. The moved tests run in their new packages; `./gradlew :chordpro:desktopTest` must
report the same test count before and after (compare `build/test-results`). Then the root CLAUDE.md's full test
command, `:app:desktop:compileKotlin`, `:app:android:compileDebugKotlin`, `:app:web:compileKotlinWasmJs`,
`:app:ios:linkDebugFrameworkIosSimulatorArm64`, and `./gradlew spotlessCheck`.
