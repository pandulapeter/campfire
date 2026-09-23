# 47 — The Linux and Windows test scripts point at the wrong folder, a missing command and an import that cannot happen

**Severity:** test script wrong (docs only; a tester following them reports false failures) · **Area:**
`documentation/testing/05-linux.md`, `documentation/testing/04-windows.md`, `app/desktop/CLAUDE.md`

**Read, not run.** Found by comparing the scripts with the code at HEAD; nothing was run. Every claim below quotes the
code it rests on.

## What the user sees

The "user" here is whoever runs the manual test scripts before the release:

1. **Linux, wrong library folder.** The script sends the tester to `~/.local/share/Campfire` (capital C). The app
   writes to `~/.local/share/campfire`. LIN-S02 ("holds the two `.cho` files") and LIN-005 ("Removal leaves
   `~/.local/share/Campfire` in place") then look at a folder that never exists and fail a P0 smoke test for nothing.
2. **Linux, a command that is not there.** LIN-032 says `campfire song.cho` from a terminal imports a song. The `.deb`
   puts no `campfire` on the `PATH`; the launcher is `/opt/campfire/bin/Campfire`, as the same script already says
   in "Record" and in LIN-033. The tester gets "command not found".
3. **Windows, an expectation the design does not meet.** WIN-031 (P0) expects five files opened together from
   Explorer to be "imported in one import". They arrive as five imports, one after another, and the tester fails a P0
   that is behaving as designed.

## Cause

**1.** `presentation/src/desktopMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/Platform.desktop.kt:72-74`
(and the same three lines in `data/source/local/implementation/src/desktopMain/.../storage/file/FileStorage.desktop.kt:33-35`):

```kotlin
        else -> System.getenv("XDG_DATA_HOME").orEmpty()
            .let { if (it.isEmpty()) File(userHome, ".local/share") else File(it) }
            .let { File(it, APPLICATION_NAME.lowercase()) }
```

`desktop-publish.yml:159` agrees (`DATA="$HOME_DIRECTORY/.local/share/campfire"`). The script does not —
`documentation/testing/05-linux.md`:

- `:33` — "**Library folder:** `~/.local/share/Campfire`, or `$XDG_DATA_HOME/Campfire` when that variable is set."
- `:54` (LIN-S02) — "`~/.local/share/Campfire/library/songs` holds the two `.cho` files"
- `:90-91` (LIN-005) — "Removal leaves `~/.local/share/Campfire` in place."

`app/desktop/CLAUDE.md:42` is vague rather than wrong ("`~/.local/share` elsewhere"), which is how the script got it
wrong: the capital C is the macOS and Windows spelling.

**2.** `documentation/testing/05-linux.md:179` (LIN-032): "Open With → Campfire (if it is listed), or
`campfire song.cho` from a terminal, imports it." jpackage installs the app under `/opt/<package name in lowercase>`
with a launcher named after `packageName = "Campfire"` (`app/desktop/build.gradle.kts`, `nativeDistributions`), and
links nothing into `/usr/bin`. `:39` and LIN-033 (`:182`) already use `/opt/campfire/bin/Campfire`.

**3.** `documentation/testing/04-windows.md:203-207`:

```
- [ ] **WIN-031** (P0) Opening several files with the app closed
  1. With Campfire closed, select five `.cho` files in Explorer and press Enter.

  **Expected:** there is exactly one window, and all five are imported in one import.
```

Explorer runs the registered `"%1"` command once per selected file, so five files are five processes — which the
code says itself (`app/desktop/src/main/java/com/pandulapeter/campfire/SingleInstance.kt`, `claimSingleInstance`'s
KDoc: "five files opened together are five processes"). One wins the lock and imports its own argument; each of the
other four hands its one path over separately (`CampfireDesktopApplication.kt`, `onActivated = { paths ->
OpenedFiles.open(paths) … }`), `OpenedFiles` keeps "one list per request" (`OpenedFiles.kt:35`), and every list
becomes its own `ImportRequest(shouldOpenSong = true)` (`CampfireViewModel.kt:1420-1422`). So: five imports, five
result messages, and each single-song import opens its song — replacing the song screen the previous one opened
rather than stacking on it (`openImportedSong`, `CampfireViewModel.kt:1009-1018`), so Back from the last one goes to
the list.

**Why the docs change rather than the code.** Merging the hand-overs would need either a time window ("collect paths
that arrive within 300 ms") or merging whatever `ImportRequest`s are still queued when an import starts. The project's
rule is that races are answered with state, not with time windows, so the first is out; and the second would still
make the outcome depend on timing — the processes arrive over a second or more of JVM start-up, so whether files 2-5
are merged would depend on how long file 1's import took. Five announced imports that each land whole are correct,
deterministic and already what happens; the single-instance guarantee the P0 is about — one window, one process
owning the library, no file lost — holds.

## The change

Docs only.

**`documentation/testing/05-linux.md`**

- `:33` → "**Library folder:** `~/.local/share/campfire`, or `$XDG_DATA_HOME/campfire` when that variable is set —
  lowercase, unlike the `Campfire` folder on macOS and Windows."
- `:54` → "`~/.local/share/campfire/library/songs` holds the two `.cho` files"
- `:91` → "`~/.local/share/campfire` in place."
- `:179` → "Open With → Campfire (if it is listed), or `/opt/campfire/bin/Campfire ~/song.cho` from a terminal,
  imports it. (The package puts nothing on the `PATH`.)"

**`documentation/testing/04-windows.md`**, WIN-031 → keep the step and the P0, replace the expectation:

```
  **Expected:** there is exactly one window and one Campfire process once they settle (Task Manager), and all five
  songs are in the library, each once. They arrive as separate imports — Explorer starts one process per file, and
  each hands its file over on its own — so up to five "imported" messages follow one another, and the song screen
  shows one of the five songs (the last to arrive), with Back going to the song list, not through the other four.
  Nothing is numbered `_2` unless it was already in the library.
```

**`app/desktop/CLAUDE.md:42`**, "(`~/Library/Application Support/Campfire` on macOS, `%APPDATA%` on Windows,
`~/.local/share` elsewhere)" → "(`~/Library/Application Support/Campfire` on macOS, `%APPDATA%\Campfire` on Windows,
`~/.local/share/campfire` — or `$XDG_DATA_HOME/campfire` — elsewhere, in lowercase as Linux names are)".

## Tests

None: documentation only.

## Verification

1. On the Linux test VM with the `.deb` installed: `ls ~/.local/share/` after the first start shows `campfire`, not
   `Campfire`; `which campfire` prints nothing; `/opt/campfire/bin/Campfire ~/song.cho` with the app running hands the
   song over (LIN-033).
2. On Windows with the `.msi` installed and Campfire closed: select five `.cho` files, press Enter. Check the corrected
   WIN-031 expectation holds as written — in particular that exactly one `Campfire.exe` is left and all five songs are
   in the library. If *more* than one window stays open, that is a real single-instance bug and a finding of its own,
   not something this plan papers over.
3. Read the three edited passages once more against `Platform.desktop.kt:64-76`.

## Docs

This plan is the docs change. Root `CLAUDE.md` names no desktop data folder; no change there.

## Files touched

- `documentation/testing/05-linux.md`
- `documentation/testing/04-windows.md`
- `app/desktop/CLAUDE.md`

## Depends on

Nothing. Plan 45 also edits `documentation/testing/05-linux.md` (LIN-015, lines 129-133) and `app/desktop/CLAUDE.md`
(line 12) — different lines, either order.
