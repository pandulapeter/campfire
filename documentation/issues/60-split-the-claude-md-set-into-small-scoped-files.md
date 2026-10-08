# Cut the CLAUDE.md set into small, directory-scoped files so a session loads only the rules for what it touches

**Challenged:** amended — step 7 also covers `code-style/SKILL.md` lines 8–9 and `codebase-review/SKILL.md` line 165 (both say where docs live: "the root and the per-module ones", "where the root one describes it"); the Depends line now orders this plan after every other plan of the folder that edits CLAUDE.md text, so their edits land in the old places and move with the cut.

**Kind:** docs  ·  **Severity:** high  ·  **Effort:** L  ·  **Risk:** low  ·  **Platforms:** all
**Files:** `CLAUDE.md`; `presentation/CLAUDE.md`; new `presentation/src/{androidMain,desktopMain,iosMain,wasmJsMain}/CLAUDE.md`; new
`CLAUDE.md` files under `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/` (`ui/`,
`screens/songDetails/`, `screens/songEditor/`, `screens/settings/`, `screens/importReport/`, `screens/` (songs, setlists,
metronome), `components/`, `dialogs/`, `print/`, `theme/`, `platform/`, `navigation/`, `chords/`, `metronome/`); new
`.github/CLAUDE.md`; `app/desktop/CLAUDE.md`; `app/android/CLAUDE.md`; `app/web/CLAUDE.md`; the module `CLAUDE.md` files that receive
moved product-behaviour text (`data/model`, `data/repository/implementation`, `data/source/local/implementation`,
`data/source/remote/implementation`, `domain/implementation`, `chordpro`); `.claude/skills/prepare-release/SKILL.md`
(line 245, "the Updates section of `CLAUDE.md`"), `.claude/skills/code-style/SKILL.md` (§ "Keep the CLAUDE.md files in
sync"), `.claude/skills/codebase-review/SKILL.md` (lines 153–165, "see CLAUDE.md for what may be tested")
**Depends on:** none to start, but land it **after every other plan in this folder that edits a `CLAUDE.md`** (most of 01–35 and 40–71 update the root, `presentation/CLAUDE.md` or a module doc) and after the concurrent file-split/package-move lanes, so the paths named in the docs are final and no plan's "update the X sentence of the root `CLAUDE.md`" points at text this plan has moved. A plan executed after it targets the new location (`.github/CLAUDE.md`, the directory-scoped files).

## Problem

Claude Code always loads the root `CLAUDE.md`, and loads a nested `CLAUDE.md` whenever a file in its directory or below
is read. Measured at 2940b0e0a:

| file | bytes | structure |
|---|---|---|
| `CLAUDE.md` | 104,603 | 9 `##` sections: Conventions 43,572 B (36 bullets, mostly product behaviour), Build 19,887 B (≈15 KB of it the release pipeline), Sync 8,832, Metronome 7,930, Architecture 5,933, Web 5,370, Printing 4,192, Cover art 3,886, Updates 2,749 |
| `presentation/CLAUDE.md` | 274,204 | 4 headings (`# :presentation`, `## Metronome`, `## Export`, `## Scrolling performance`); 30 lines over 1,500 chars, longest 24,246; one bullet list of 45 items is 227,746 B, its largest items `SongLyrics.kt` 46,817 B, `ui/components/` 23,027, `Dialogs.kt` 20,150, `songEditor/` 16,978, `CampfireApp.kt` 14,543, `CampfireViewModel.kt` 11,156 |
| `app/desktop/CLAUDE.md` | 31,242 | 5 lines over 1,500 chars, longest 9,924 |
| `app/android/CLAUDE.md` | 18,078 | 6 lines over 1,500 chars (max 2,914) |
| `data/model/CLAUDE.md` | 14,069 | 4 lines over 1,500 chars (max 2,300) |

So every session starts with ~26k tokens of root text, and the first read of any `:presentation` file adds ~70k more,
most of it about screens the task never touches. Several topics are written twice: `build.json` and the Web Lock in
root `## Web` and `app/web/CLAUDE.md`; class data sharing in root `## Build` and `app/desktop/CLAUDE.md`;
`updatePriority` in root `## Updates` and `presentation/CLAUDE.md`; OPFS in four files. The root's own Sync,
Metronome and Cover art sections open with "The module `CLAUDE.md` files carry the detail; the short version:" — yet
the short versions run to 8 KB each. Long single-line paragraphs also defeat `grep -n` and diff review.

## Fix

Every step is a **pure cut-and-paste** of whole sentences unless it says otherwise, one commit each, verified by the
script under Tests before committing. Do not rewrite prose while moving it; rewording is step 7 alone.

1. **`presentation/CLAUDE.md` split by directory.** Move each bullet of the 45-item list (and the `## Metronome`,
   `## Export`, `## Scrolling performance` sections) into the `CLAUDE.md` of the directory holding the file it is
   about: `SongLyrics.kt`, `SongDisplayControls.kt`, `SongKeyboardShortcuts.kt`, `SongCapo.kt`, the "title scrolls the
   song back", "app bar says what the song sounds like" bullets → `ui/screens/songDetails/CLAUDE.md`; `songEditor/` →
   `ui/screens/songEditor/CLAUDE.md`; `ui/components/*` bullets (incl. `Tags.kt`, `CoverArt.kt`, `FastScroller.kt`,
   `CampfireTopAppBar.kt`, `Languages.kt`, `SaveShortcut.kt`) and Scrolling performance → `ui/components/CLAUDE.md`;
   `Dialogs.kt`, `CoverArtSearchSheet.kt`, `ImportProgressDialog.kt` → `ui/dialogs/CLAUDE.md`; Export → `ui/print/CLAUDE.md`
   (with a one-line pointer in `ui/dialogs/CLAUDE.md` for `ExportScreen.kt`); `ui/theme/*` → `ui/theme/CLAUDE.md`;
   `ui/platform/*` → `ui/platform/CLAUDE.md`; `CampfireDestination.kt` → `ui/navigation/CLAUDE.md`; Chord diagrams →
   `ui/chords/CLAUDE.md`; Metronome → `ui/metronome/CLAUDE.md` (its songDetails-only bullets such as "Where a capo
   lives" to `screens/songDetails/`); settings, importReport likewise; `CampfireApp.kt`, `CampfireViewModel.kt`,
   `DemoLibrary.kt`, `AppUpdateGate.kt` and the other top-level `ui/*.kt` files → `ui/CLAUDE.md`; the
   `androidMain/…`, `desktopMain/…`, `iosMain/…`, `wasmJsMain/…` shell bullets (the 14,417-char paragraph 5) →
   `presentation/src/<sourceSet>/CLAUDE.md`. Every new file starts with the MPL HTML-comment header the existing ones
   carry. What stays in `presentation/CLAUDE.md`: paragraphs 2–4 (module overview, JVM-free rule, Koin wiring) plus a
   new **index**: one line per sub-file naming its directory and what it covers. Target ≤ 15 KB.
   Note the loading rule when choosing a home: `ui/CLAUDE.md` loads for every file under `ui/`, so only text about
   the top-level `ui/*.kt` files goes there (≈ 30 KB); everything about a sub-package goes into that sub-package.
2. **Release pipeline → `.github/CLAUDE.md`.** Move the root `## Build` bullet "Publishing a GitHub release is the
   release." with all its sub-bullets (`publish-web.yml` … `publish-android.yml`, "Nothing Apple signs with expires",
   "Both Apple workflows submit…") and the paragraph about CI writing `local.properties` from secrets. Root `## Build`
   keeps the `campfire.*` property rule, the `local.properties` example, the build commands and one line: "Publishing
   and the store workflows: see `.github/CLAUDE.md`."
3. **Root sections that summarise a module doc.** For `## Sync`, `## Metronome`, `## Cover art`, `## Updates`, `## Web`
   and `## Printing`: move every sentence that is not already said in the named module doc into that module doc
   (`data/repository/implementation`, `metronome/implementation` + `ui/metronome`, `data/source/remote/implementation`,
   `ui/platform`, `app/web`, `ui/print`), then replace the section with 3–6 lines: the invariant a change elsewhere
   could break (e.g. "no server of Campfire's own; a run belongs to the app; content decides, never a clock; an edit
   beats a deletion") and the pointer. A sentence counts as "already said" only if the Tests script finds its
   distinctive terms in the target.
4. **Root `## Conventions`.** Split the 36 bullets in two. *Coding conventions* stay verbatim (convention plugins,
   JVM-free shared code, string resources/`textResource`, Material 3 Expressive, the animation rule, the fade-under-bars
   rule, bottom sheets / pinned-height budget, short windows, plain modules, Koin compiler plugin and "Never inject a
   `List<T>`", `Impl` naming, `BaseLocalDataRepository`, mappers, file-name identity and normalization, "Only pure logic
   is tested" with its test command). *Product behaviour* bullets (setlists show every song, list search, tags,
   language, cover meta, links, how a song is played, Features switches, chord diagrams, demo library, What's new, app
   icon, "says nothing about the other builds", import decides before it writes, documents, other apps' archives, chord
   notation, a song named by its header, renames) move verbatim to the module that implements them (see Decision),
   and each leaves **one line** in a new root `## Product rules` list: the rule in a sentence plus the path of its full
   text. Target root size: 15–20 KB.
5. **Deduplicate** the topics written twice (`build.json`, Web Lock, class data sharing, `updatePriority`, OPFS): keep
   the fuller text in the module doc, cut the other to a pointer. Not pure cut-and-paste: check each removed sentence's
   terms exist in the kept one.
6. **Headings and paragraph cap** in `app/desktop/CLAUDE.md`, `app/android/CLAUDE.md`, `data/model/CLAUDE.md`,
   `app/ios/CLAUDE.md` and every new file: break each line over 1,500 chars at sentence boundaries into paragraphs or
   bullets under `###` headings. Hard-wrap at ~120 columns like the root file. Sentences unchanged.
7. **Skills.** `prepare-release/SKILL.md:245`: point "the Updates section of `CLAUDE.md`" at wherever step 3 put it.
   `codebase-review/SKILL.md` "see CLAUDE.md for what may be tested": still the root (the test list stays there).
   `code-style/SKILL.md` § "Keep the CLAUDE.md files in sync" (line 148, "the module's own and the root one") and its
   opening (lines 8–9, "Architecture, the module graph and the build are in the root `CLAUDE.md` and the per-module
   ones"): add that a change is documented in the nearest directory-scoped `CLAUDE.md`, the root only for a coding
   convention or a product rule line, that the release pipeline is in `.github/CLAUDE.md`, and that no paragraph
   exceeds ~1,500 chars. `codebase-review/SKILL.md` line 165 ("`CLAUDE.md` and, where the root one describes it, the
   root `CLAUDE.md`"): say "the nearest directory-scoped `CLAUDE.md`, and the root one only for a convention or a
   product-rule line"; its lane rule at line 38 (module `CLAUDE.md` files are shared between lanes) applies to the new
   directory files unchanged. `store-screenshots/SKILL.md` names only `tools/screenshots/CLAUDE.md`, which does not
   move. The `.claude/settings.json` hook text says "keep the module CLAUDE.md in sync" and names no section — leave
   it.

## Tests

No unit tests (docs). Each step is checked by a throwaway script (scratchpad, not committed) run on the pre- and
post-commit trees:

- **Sentence multiset** (steps 1, 2, 4, 6): split every `CLAUDE.md` of the repo (excluding `.claude/worktrees`) into
  sentences (on `. ` / newline, whitespace collapsed); the multiset before must equal the multiset after, apart from
  the added index/pointer lines, which the script prints for review.
- **Distinctive terms** (steps 3, 5): extract every backticked token, every `**bold**` phrase and every number with a
  unit from the old set; each must occur in the new set. Print the misses; there must be none.
- Sizes: print bytes per file and the longest line; the root must be ≤ 20 KB, `presentation/CLAUDE.md` ≤ 15 KB.
- `./gradlew desktopTest` is unaffected (no code changes).

## Manual check

Start a fresh Claude Code session in the repo, read a file in `ui/screens/songDetails/` and confirm (`/context` or
`/memory`) that the root, `presentation/CLAUDE.md`, `ui/CLAUDE.md` and `songDetails/CLAUDE.md` are loaded and no other
`:presentation` doc is. Ask one question per moved product rule (e.g. "where does a setlist's tempo override live?")
and check the answer cites the new location.

## Decision

How far to go:

1. **Recommended — all seven steps**, in order; each is useful alone, so stopping after any step is safe. Product
   behaviour bullets go to their implementing module, the root keeps a one-line-per-rule `## Product rules` index.
2. Steps 1, 2 and 6 only (the mechanical splits: `presentation/CLAUDE.md` by directory, the release pipeline to
   `.github/CLAUDE.md`, paragraph cap). Root `## Conventions` stays as it is (~85 KB root).
3. Step 1 only — the single biggest win (274 KB → ≤ 15 KB index + one directory file per task).

Sub-choice for step 4: behaviour bullets that span many modules ("Features are switched on and off", "How a song is
played") either go to `presentation/src/commonMain/.../ui/CLAUDE.md` (recommended: the UI is where they are enforced)
or stay whole in the root.
