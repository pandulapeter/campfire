# 34 · Importing a large songbook freezes the window while the app works out what the import would do

**Severity:** performance (all platforms — a frozen window and a progress bar that does not move; an ANR on a slow
Android phone; certain for a collection file of a few thousand songs or a large archive, unnoticeable for a handful
of songs) · **Area:** `:domain:implementation` (`PrepareImportUseCaseImpl`)

## Symptom

1. Import a songbook that is one `.txt` or `.cho` file of a few thousand songs separated by `{new_song}` (a few
   MB), or a zip of a few thousand songs, or re-import an exported library of that size.
2. The progress bar under the app bar appears and stops moving, the list does not scroll, and on Android a slow
   phone offers "Campfire isn't responding" after five seconds. On the web the whole tab is dead for the duration,
   since there is nothing but the one thread.
3. After a few seconds the import carries on as if nothing had happened.

The size of what may be imported at all is plan 15's (`ImportLimits`: 8 MiB per text file, 24 MiB per import); this
plan is about where the work that remains under those limits runs.

## Cause

`CampfireViewModel.import` (`presentation/.../ui/CampfireViewModel.kt:1146-1152`) is run by the consumer of
`importQueue` in `viewModelScope`, which is `Dispatchers.Main.immediate`, and calls `prepareImport(files)` as it is.
`domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/PrepareImportUseCaseImpl.kt:34-65`
switches nowhere, so it runs on whatever thread it resumes on. What the layers below it move elsewhere is only their
own part:

- `archiveRepository.unpack` inflates on `Dispatchers.Default` (`ArchiveLocalSourceImpl.kt:28`);
- `songContentRepository.loadSongContent` reads on `Dispatchers.IO` inside the platform `FileStorage`.

Both return to the caller's dispatcher, and everything between them is the use case's own computation, on the main
thread (`:73-110`, `:120-140`):

```kotlin
val parts = ChordProSplitter.split(file.bytes.decodeLibraryText())      // UTF-8 validation, a directive match per line
…
val text = part + "\n"                                                   // a copy of every song
val fileName = songRepository.importFileName(fallbackTitle = fallbackTitle, text = text)   // not suspending: ChordProParser.parseMetadata, here
…
ChordProSplitter.comparable(existingText) == ChordProSplitter.comparable(text)             // two more passes per name that is taken
```

and `setlistRepository.parseSetlist` (`SetlistLocalSourceImpl.kt:90`) is `suspend` in name only: it decodes the JSON
on the caller's thread too. For a re-import of a 3000-song library that is 3000 header parses and 6000 text
foldings between the reads, every one of them on the thread that draws the window. There is also no suspension
point inside the handling of one collection file, so a 5 MB songbook is a single uninterrupted block.

Plan 02 moves the comparison into `ImportPlanner` and only folds a text when its name's family has something to
compare it with, which makes the all-new import cheaper, but the planner runs on the caller's thread like the rest.

## Fix

Written against `PrepareImportUseCaseImpl` as plans 02 and 15 leave it (02: `planSongs` / `planSetlists` gather the
batch and hand it to `ImportPlanner`; 15: `invoke` holds the size budget). Only step 2's surroundings differ if this
lands on the file as it is at `29820b93`; the places are the same.

1. **`PrepareImportUseCaseImpl.kt` — the dispatcher.** The switch belongs in the use case, not in the view model and
   not in the repositories: the work is the use case's own (it is `GetScreenDataUseCaseImpl`'s reasoning, which
   builds `ScreenData` on `Dispatchers.Default` with `flowOn` for the same reason), the repositories already move
   what is theirs, and a caller should not have to know that preparing is heavy. `Dispatchers.Default`, not `IO`:
   this is computation, and the reads inside it switch to `IO` on their own.

   Do it without re-indenting the body, which plans 02 and 15 both edit: rename the present `invoke` into a private
   function and put a one-line `invoke` above it.

   ```kotlin
   import kotlinx.coroutines.Dispatchers
   import kotlinx.coroutines.withContext
   import kotlinx.coroutines.yield
   ```

   ```kotlin
       /**
        * Planned on [Dispatchers.Default] rather than wherever it was asked for, which for the view model is the main
        * thread. Decoding the files, splitting the collections, reading the header of every song for its name and
        * folding the texts that have to be compared is all computation, and a songbook of a few thousand songs is
        * seconds of it. The repositories only move their own reads and the unpacking elsewhere, so everything between
        * two of those would otherwise run on the thread that draws the window.
        */
       override suspend operator fun invoke(files: List<ImportedFile>): ImportPlan = withContext(Dispatchers.Default) { plan(files) }

       private suspend fun plan(files: List<ImportedFile>): ImportPlan {
           // the body `invoke` has today, unchanged
       }
   ```

   Nothing in the body depends on the thread: the lists it fills are local to the one coroutine, the repositories
   are safe to call from anywhere (the scan already calls the parser from several `Default` threads at once), and
   the `ImportPlan` it returns is immutable. `CampfireViewModel.import` does not change — `pendingImportPlan` and
   `_isImporting` are still touched on the main thread only, before and after the call.

2. **The same file — giving the thread back.** On the web `Dispatchers.Default` is a queue on the page's only thread
   rather than a way off it, so step 1 alone changes nothing there. `yield()` between the units of work does: the
   dispatcher's queue goes back to the browser's event loop after a handful of resumptions (16 in
   kotlinx.coroutines 1.11), which is when the page is painted and input is handled. On the other three platforms a
   `yield()` with nothing else queued costs next to nothing, and everywhere it is the point at which a preparation
   whose view model is gone notices that it was cancelled, instead of finishing a plan nobody will apply.

   In `planSongs`, one at the top of the per-file lambda and one at the top of the per-part lambda (`flatMap` and
   `map` are inline, so suspending inside them compiles — the present code already does):

   ```kotlin
           val incoming = files.flatMap { file ->
               // The web has one thread, and there the dispatcher this runs on is a queue on it rather than a way off
               // it: a plan that never suspends holds the page up for as long as it takes. Handing the thread back
               // between the files, and between the songs of one, keeps the progress bar moving, and it is also where
               // a plan nobody is waiting for any more finds out that it was cancelled.
               yield()
               val parts = ChordProSplitter.split(file.bytes.decodeLibraryText())
               …
               parts.map { part ->
                   yield()
                   …
   ```

   and one in `planSetlists`, at the top of the `mapNotNull` lambda (`yield()`, no second comment).

   Do **not** add a `yield()` to `ImportPlanner` (plan 02): it is the pure, tested half, its per-song work for a
   name nobody else has is a map lookup, and for a name that is taken it suspends on the read anyway.

What is deliberately left alone:

- **The largest block that cannot be interrupted** is now one file's `decodeLibraryText()` + `ChordProSplitter.split`,
  and one archive's `unpack`. Both are pure, non-suspending functions of modules that must stay that way (`:chordpro`
  depends on nothing, coroutines included), and plan 15 bounds them at 8 MiB and 24 MiB. On the JVM and on iOS they
  are off the main thread after step 1; on the web they remain a pause of a fraction of a second to a second or two
  for the very largest input. Making `ZipReader.read` and the splitter lazy sequences so that the web can breathe
  inside them is a bigger change than this finding is worth.
- **`ChordProSplitter.split` matching a directive on every line** is plan 39's: its hand-written `matchDirective`
  turns away a line that does not start with `{` at the first character.
- **`ImportFilesUseCaseImpl`** stays on the caller's dispatcher. Its own work per file is nothing (every step is a
  write on `IO` or a parse on `Default` inside the local source), it has natural suspension points on every platform,
  and its `finally { withContext(NonCancellable) { rescan } }` is easier to reason about where it is.
- **The platform readers** (`FilePicker`, the iOS inbox, the desktop drop) reading on the main thread are plans 15
  and 51.

## Tests

None. A dispatcher switch is not observable from a unit test worth having, and `runTest` replaces neither
`Dispatchers.Default` nor the behaviour of `yield()`. The planning logic itself is covered by plan 02's
`ImportPlannerTest`, which this plan does not touch.

## Verify

1. Make a test songbook: a text file of 5000 songs, each `{title: Song N}`, `{artist: Artist N}`, twenty lines of
   lyrics, separated by `{new_song}` (about 4 MB), and a zip of the same songs as separate files.
2. `./gradlew :app:desktop:run`, import the text file: the progress bar keeps moving and the list still scrolls
   while the plan is being worked out. Import it a second time: every song is reported as already there, and the
   window stays responsive while 5000 files are compared.
3. `./gradlew :app:web:wasmJsBrowserDevelopmentRun`, same two imports in the browser: the progress bar stutters at
   the very start (the decode and split of the one big file) and then moves; the tab does not offer to be killed.
   With the zip, the pause is the unpacking.
4. Android emulator or a slow phone, same imports: no ANR dialog. With a debugger or
   `StrictMode`-style logging, `PrepareImportUseCaseImpl.plan` runs on a `DefaultDispatcher-worker` thread.
5. Start the text-file import on Android and press Back at the root (API 30 and below finishes the activity): the
   preparation stops at the next song instead of running to the end.
6. Compile checks for all four targets.

## Docs

`domain/implementation/CLAUDE.md`, the `PrepareImportUseCaseImpl` / `ImportFilesUseCaseImpl` bullet: after the
sentence ending "… which is the reason for the split — three hundred questions is not a choice." add: "Preparing runs
on `Dispatchers.Default`, because nearly all of it is computation (decoding, splitting, a header parse per song, a
folding per text that has to be compared) that the view model would otherwise do on the main thread, and it yields
between songs, which is what keeps the web's one thread painting and what lets a cancelled preparation stop."

## Touches

- `domain/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/domain/implementation/useCases/PrepareImportUseCaseImpl.kt`
- `domain/implementation/CLAUDE.md`

## Depends on

02 (it rewrites `planSongs` / `planSetlists`, where step 2's `yield()` calls go; 02 names this plan as landing after
it). Shares `PrepareImportUseCaseImpl.kt` with 15, 27 and 28 as well: 15 and 27 edit the body that step 1 leaves
where it is under a new name, 28 the two `decodeLibraryText()` calls — no overlap in lines, but schedule them one
after another. 15 owns the size caps and 39 the cost of `matchDirective`; neither has to land first.
