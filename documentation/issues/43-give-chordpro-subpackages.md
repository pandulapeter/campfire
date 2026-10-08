# Group :chordpro's flat package into subpackages, internal syntax first

**Challenged:** amended — ordering with plan 48 made explicit (48 lands after this plan, not before, so "last :chordpro plan" is corrected), and phase 1 now covers the same-package KDoc links that stop resolving once the internal objects move.

**Kind:** architecture  ·  **Severity:** low  ·  **Effort:** M (phase 1 S)  ·  **Risk:** low  ·  **Platforms:** all
**Files:** every file under `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/` (32 files at 2940b0e0a, more after the :chordpro split lane) and `chordpro/src/commonTest` / `desktopTest`; phase 2 also the 66 files outside `:chordpro` that import a non-`model` declaration of it (25 in `presentation/src/commonMain`, 12 in `presentation/src/commonTest`, 19 in `domain/implementation`, 7 in `data/source/local/implementation`, 2 in `domain/api`, 1 desktopTest); `chordpro/CLAUDE.md`; root `CLAUDE.md` (Architecture block names the module only — no change expected)
**Depends on:** the :chordpro split lane, 40, 41, 42, 44 (moving files under open refactors only creates conflicts — this is the last :chordpro *restructuring* plan; only plan 48, which adds visibility modifiers and return types to every file and needs the final package layout, lands after it)

## Problem

`:chordpro` keeps 32 top-level files in one package (`com.pandulapeter.campfire.chordpro`), plus `model/`. After the
concurrent split lane it will be closer to 45: the internal syntax helpers (`ChordProSyntax` and its new pieces
`ChordProLines`, `ChordProDirectives`, `ChordProMetaItems`, `ChordProHeaderLayout`, `ChordProEnvironments`,
`ChordProTokens`, `ChordProVocabulary`, `ChordProChordRewriter`, `ChordProOffsetMapping`, the parser's builders) sit
alphabetically between the public entry points (`ChordProParser`, `ChordProTransposer`, `ChordProTags`, …), so the
directory listing no longer says what the module offers versus how it is built. `chordpro/CLAUDE.md` (≈50 KB) is the
only map.

Internal objects at 2940b0e0a: `ChordProSyntax`, `ChordProChordNames`, `ChordProLanguageCodes`, `ChordProNashville`,
`ChordProTabTransposer`, `ChordVoicingTables` (+ the split lane's new internal objects, + `ChordShapeGeometry` /
`ChordVoicingSearch` from plan 42, `ChordProLineScanner` from 40, `MetadataKind` from 41).

## Fix

**Phase 1 — internal objects into `…chordpro.syntax`** (one commit, no file outside `:chordpro` changes because none can
see these declarations). Move every `internal object` / `internal class` whose job is reading or writing ChordPro text
(the list above, minus `ChordVoicingTables` / `ChordShapeGeometry` / `ChordVoicingSearch`, which go to
`…chordpro.chords` with `internal` visibility in phase 1 only if the user picks option A below). Only imports inside
`:chordpro` and its tests change; `internal` still works across packages of one module. KDoc `[ChordProSyntax.x]`-style
links in the root-package files resolved without an import while both sat in one package; add the import (or the FQN)
for each — `grep -n "\[ChordProSyntax\|\[ChordProNashville\|\[ChordProChordNames\|\[ChordProTabTransposer\|\[ChordProLanguageCodes" -r chordpro/src`
— since an unresolved KDoc link does not fail the build. Update `chordpro/CLAUDE.md` to open with a short package map.

**Phase 2 — public objects into topic packages** (only with the user's go-ahead, see Decision). Proposed grouping:

- `…chordpro` (root) — the entry points the app reads songs through: `ChordProParser`, `ChordProSummaryCache`,
  `ChordNotation`, `ChordProSplitter`, `ChordProSerializer` (if it stays, see plan 48).
- `…chordpro.chords` — `ChordProChords`, `ChordProNotation`, `ChordProNashville`, `ChordVoicings`,
  `ChordProDefinitions`, `ChordProTransposer`, `ChordProTabTransposer`, `ChordProChordNames`, `ChordVoicingTables`.
- `…chordpro.edit` — the text rewriters the editor and the Song defaults sheet call: `ChordProMetadataFields`,
  `ChordProHeader`, `ChordProTags`, `ChordProLanguages`, `ChordProLinks`, `ChordProCoverArt`, `ChordProPrettifier`,
  `ChordProHighlighter`, `ChordProTabWrapper`, `ChordProTextChange`, `ChordProLiteralText`.
- `…chordpro.convert` — `ChordSheet`, `ChordSheetConverter`.
- values (`ChordProTempo`, `ChordProTime`, `ChordProDuration`) stay in the root or join `model/`.

Do it with the IDE's Move refactoring (or `sed` over the import lines — every import is a fully qualified single-name
import), one package per commit, building `./gradlew :app:desktop:compileKotlinDesktop :app:android:compileDebugKotlin`
and the full test command after each. KDoc `[links]` across packages need the new FQNs; grep for `[ChordPro` in KDoc
after each move.

## Tests

No new tests; the whole `desktopTest` command from the root `CLAUDE.md` must pass after each commit, and
`./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:compileKotlinWasmJs` once at the end of phase 2 (the
Native and Wasm compilers resolve imports separately).

## Manual check

none — covered by tests (a package move cannot change behaviour once every target compiles).

## Decision

Phase 1 needs none. For phase 2:

- **A (recommended): phase 1 only.** The internal/public split is what a reader of the directory needs; topic packages
  for the public objects ripple into 66 files of other modules for a navigational gain the per-module `CLAUDE.md`
  already gives, and any open branch touching those imports conflicts.
- **B: phase 1 and phase 2.** Clearer module surface and a natural boundary should `:chordpro` ever be split into
  modules (e.g. a `:chordpro:chords` used by `:presentation`'s diagrams alone); costs one noisy commit per package
  across `:presentation`, `:domain` and `:data`.
- **C: neither.** Keep the flat package and rely on `chordpro/CLAUDE.md`.
