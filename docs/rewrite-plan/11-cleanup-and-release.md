# Step 11: cleanup and release preparation

**Goal:** no dead code, dependencies, strings or docs left from Campfire 3; version bumped; documentation describes
the new app.

**Depends on:** all previous steps (10 may be partially done).

## 1. Dead code and dependencies

- `grep -rn "TODO(step" --include=*.kt .` must return nothing (excluding `build/`).
- `libs.versions.toml`: confirm `ktor*`, `ktorfit`, `retrosheet`, `softwork-csv`, `androidx-room`,
  `androidx-sqlite`, `kotlin-ksp` are gone; keep `kotlin-browser` (OPFS / DOM interop), `androidx-browser`
  (Custom Tabs), `compose-reorderable` (setlists).
- Root `build.gradle.kts`: remove plugin aliases that no module applies anymore (`ksp`, `ktorfit`,
  `kotlin-serialization` stays: the local source uses it).
- `gradle/build-logic`: nothing should need changing; verify no reference to Room/KSP.
- Unused strings: for each `<string name="x">` in `values/strings.xml`, `grep -rn "Res.string.x"` in `presentation`;
  delete the unused ones from both language files. Check `values-hu` has exactly the same set of names
  (`diff <(grep -o 'name="[^"]*"' values/strings.xml | sort) <(grep -o 'name="[^"]*"' values-hu/strings.xml | sort)`).
- Unused Kotlin: `DataState` variants, `BaseLocalDataRepository` helpers, `Platform` expects, dialog types, icons.
- `kotlin-js-store/yarn.lock`: regenerate with `./gradlew kotlinUpgradeYarnLock` if the wasm dependencies changed.

## 2. Versions

- Root `build.gradle.kts`: `VERSION_NAME` → `4.0.0`, `VERSION_CODE` → 27 (current is 3.0.1 / 26).
- iOS: `MARKETING_VERSION` 4.0.0 / `CURRENT_PROJECT_VERSION` bumped in `app/ios/iosApp/iosApp.xcodeproj/project.pbxproj`
  (both configurations).
- Desktop installers pick the version up from the system property.

## 3. Documentation

- `README.md`: rewrite the description ("Campfire is a lightweight ChordPro viewer and editor…"): offline, local
  `.cho` files, import/export incl. zip, setlists, transposition, all four platforms; remove the "Known issues" entries
  that no longer apply ("Get bundled content" is done by this rewrite) and add a short "File format" section linking
  to chordpro.org and stating the supported directives (from step 01 §3) and the `.setlist.json` format (from the plan
  README). Keep the license and the Play badge. Mark the screenshots as outdated or replace them (see step 12 for how
  to capture).
- `CLAUDE.md`: update the architecture tree, the data-flow sentence (`FileStorage` → local sources → repositories →
  use cases → view model), the conventions (tests exist in `:chordpro` and the local source implementation; run with
  `desktopTest`), delete the whole "Web" section about Room/Retrosheet and replace it with two lines about OPFS and
  the JS interop helpers, and mention `docs/rewrite-plan/` as historical. Keep the build commands.
- Privacy: the app no longer makes network requests; if the website's privacy policy (linked from Settings) describes
  network usage, note in the README that it needs updating (the policy itself is not in this repo).
- Delete `docs/rewrite-plan/samples/` only if the samples were moved somewhere better (e.g. `presentation` test
  resources); otherwise keep them, they document the supported dialect.

## 4. Store metadata reminders (not in the repo, list them in the execution notes for the user)

- Play Store / App Store listing text and screenshots need updating: no more "handpicked library".
- Android: the update wipes existing data (no migration) — release notes should say so.

## Verify

- Full build for all four platforms, all tests pass.
- Fresh install on each platform: empty state → new song → edit → view → setlist → export → delete app data →
  import: everything round-trips.
- `git status` clean after commit, `git grep -i "retrosheet\|google sheets\|database"` only hits this plan folder and
  git history.

## Execution notes

- **Most of section 1 was already done.** The version catalog, the root `build.gradle.kts` and `gradle/build-logic`
  had nothing left of Ktor, Ktorfit, Retrosheet, the CSV library, Room, SQLite or KSP - steps 04 and 05 removed the
  dependencies as they removed the code, rather than leaving them for here. `grep -rn "TODO(step"` finds nothing, and
  neither does a search for `TODO` or `FIXME` anywhere in the project.
- **What was actually still dead:**
  - `close` and `remove` in both `strings.xml` files, and `RadioButtonListItem` in `ListItems.kt`. Every other string
    and every one of the 29 drawables is referenced.
  - `SongLocalSource.renameSong`, and the `FileStorage.rename` it was the only caller of - the expect/actual method,
    its four platform implementations (~50 lines, including an OPFS one that copies the file because Chromium is the
    only browser with `move()`) and its two tests. Nothing in the app renames a file: a setlist's title lives inside
    its JSON, and the editor changing `{title}` does not move the song. Dead code with tests is still dead code, and
    git remembers it if a rename feature ever arrives.
  - The `# Room / SQLite databases` block in `.gitignore`, the Ktor mention in `proguard-rules.pro` and its
    `-dontwarn org.slf4j.**` (slf4j came in with Ktor; the release build with R8 and resource shrinking succeeds
    without it, which is how that was checked rather than guessed).
  - Eight untracked `campfire*.db*` files in `app/desktop/` - Campfire 3's Room cache, left in the working directory
    by earlier runs and invisible until the `.gitignore` block went. Moved out of the repository.
- **A round-trip bug the acceptance run found.** Exporting the library and importing the zip back gave a file one byte
  shorter than the one that went in: `ChordProSplitter.split` trims blank lines at the end of each part, which is right
  between the songs of a collection but also ate the newline a text file ends with. `ImportFilesUseCaseImpl` now puts
  it back, and the round trip is byte for byte on all three platforms it was run on.
- **Documentation was rewritten, not patched.** The root `CLAUDE.md` described an app that fetched song lists from
  Google Sheets and cached them in Room. It now describes the file library, the data flow through `FileStorage`, and a
  "Web" section about OPFS and the `js(...)` interop instead of the Room/Retrosheet hierarchy templates. Every
  per-module `CLAUDE.md` was rewritten for the same reason, and the two modules that never had one - `:chordpro` and
  `:app:web` - got one, since the convention here is one per module.
- **The README** now describes a ChordPro viewer and editor rather than a song library, and documents the file
  format: which directives are understood, the `.setlist.json` shape, and what an exported library zip contains, so
  that somebody can read their files with something else. The "Known issues" list is gone - its last entry was this
  rewrite. The screenshots are 3.x and are **marked as outdated in the README** rather than replaced; new ones need a
  populated library on four platforms, which is a photo shoot rather than a code change.
- **Not in this repository, for the release** (section 4):
  - Play Store and App Store listings still promise "a handpicked library of high quality song lyrics and chords".
    They need rewriting around: offline, your own files, an editor, import/export, no accounts.
  - **The update wipes existing data.** There is no migration from 3.x: the first launch of 4.0 starts with an empty
    library, and the Room database and the web's `localStorage` documents are deleted rather than read. The release
    notes have to say so plainly - a user who updates without knowing will think their songs are gone (they were
    never local to begin with, but that is not how it will feel).
  - The [privacy policy](https://pandulapeter.com/legal/privacy_policy-campfire.html) linked from Settings describes
    the 3.x app's network use. 4.0 makes no requests and collects nothing; the policy needs updating. This is noted in
    the README too, since the page is not in this repository.
- **Versions**: 4.0.0 / 27 in the root `build.gradle.kts`, and `MARKETING_VERSION` 4.0.0 / `CURRENT_PROJECT_VERSION`
  27 in both Xcode configurations. The Android release APK reports `versionCode='27' versionName='4.0.0'`.

### Verified

- All four platforms build, plus the R8 release APK; `:chordpro:desktopTest` (51) and
  `:data:source:local:implementation:desktopTest` (26, down from 28 with the two rename tests) pass.
- `git grep -i "retrosheet\|google sheets\|database"` hits nothing outside `docs/rewrite-plan/`.
- Both `strings.xml` files hold the same 117 names, none of them unused; no drawable is unused; every method of every
  `api` interface has a caller.

#### Acceptance matrix

Run on a **wiped library** on each platform. One library was created on desktop, exported, and that same zip was
imported on all three - so the "round trips" rows are a real cross-platform round trip, not three separate ones.

| Check | Desktop | Android | iOS | Web |
| --- | --- | --- | --- | --- |
| Fresh start shows the empty state with New song / Import | ✅ | ✅ (fresh install, 4.0.0-debug / 27) | ⚠️ not run | ✅ (empty OPFS) |
| New song → editor → autosave → back → song in list with title/artist | ✅ | — (step 09) | ⚠️ not run | — |
| Viewer renders metadata header, labelled verse, chords over syllables, monospace tab | ✅ | — (step 09) | ⚠️ not run | — |
| Setlist: create, add a song, song resolves | ✅ | ✅ (after import) | ⚠️ not run | — |
| Export library zip → `unzip -l` lists `songs/` and `setlists/` | ✅ | — | ⚠️ not run | — |
| Import that zip into a wiped install → files identical (SHA-1) | ✅ | ✅ | ⚠️ not run | ✅ |
| Import the same zip again → ` (2)` copies, nothing overwritten | ✅ | — (step 08) | ⚠️ not run | — |
| "Open with" a `.cho` / a zip handed to the app by the OS | ✅ (`--args`) | ✅ (step 10) | ⚠️ not run | n/a |
| No network requests at all | ✅ (no network code left; `git grep` above) | ✅ | ✅ | ✅ |

Rows marked "— (step NN)" were verified in that step and not repeated here; the checks that could regress from this
step's deletions (the storage layer and the import path) were all re-run. **iOS was again not driven** - the simulator
will not come to the foreground on this machine, which has been the case since step 08. The iOS framework links, the
app builds, and the shared code every other platform exercised is the same code, but nothing on iOS was clicked.
