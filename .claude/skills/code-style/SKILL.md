---
name: code-style
description: Code style, commenting, and documentation conventions for the Campfire codebase. MANDATORY — invoke this skill BEFORE writing or editing ANY source in this repo (every Write or Edit to a `.kt`/`.kts`/`strings.xml` file, new file or change to an existing one), with NO exceptions, even for a "trivial" one-line edit. It governs the MPL-2.0 header on new files, the KDoc-for-declarations / `//`-for-statements split, the "why, not what" comment voice, the NO trailing commas rule (Campfire's is the opposite of the sibling repos'), `modifier` as the first parameter, string resources, and keeping the per-module `CLAUDE.md` files in sync. Get these right while writing, not after.
---

# Campfire code style

Conventions for matching the pre-established style of this codebase. Architecture, the module graph
and the build are in the root `CLAUDE.md` and the per-module ones; this skill is about how the code
reads.

## License header

- **Every new file starts with the MPL-2.0 header**, copied verbatim from a sibling file: `.kt`, `.kts`,
  `.xml` (comment syntax), `.md` (HTML comment), `.gitignore`-style config (`#` lines). No exceptions —
  a new file without it is an incomplete change.
- The year range stays as the siblings have it (`2017-2026`); don't invent a new one per file.

## Comments

Campfire is a heavily commented codebase, but only in one direction: comments carry the **why**, never
the what. The bar for a comment is "a reader who understands Kotlin and Compose would still get this
wrong or undo it".

- **Never narrate routine code.** No comment above a `remember`, a `when`, a mapper, a `LaunchedEffect`
  that does the obvious thing, and no section-divider banners. If the code says it, the comment is noise.
- **Do comment the load-bearing decision.** Platform quirks, ordering constraints, API misbehavior,
  why a state is eager instead of `WhileSubscribed`, why a sheet clears `visibleDialog` itself, why a
  file input is wider than it looks like it should be. These are exactly the comments the codebase
  already has, and the ones a future "simplification" would otherwise delete.
- **Write them as prose, in full sentences,** in the voice of the existing comments and `CLAUDE.md`
  files: specific, technical, unhurried, several lines when the reason needs several lines. Wrap at the
  same width as the surrounding file (~130 columns). Not telegraphic fragments, not "// HACK".
- **Never write archaeology.** Once a fix has landed the code is simply how it works; no
  "this used to crash because…", no postmortems, no bug/ticket numbers. Where an unintuitive solution
  invites being optimized away, state the *constraint* that makes it necessary, in the present tense.
- **No TODOs, no commented-out code.** Delete it instead.

## Documentation (KDoc)

- **Declaration-level documentation is always KDoc (`/** … */`), never a `//` block** — including on
  `internal` and `private` declarations. `//` comments are for statements *inside* a function body.
- **`api` modules carry the contract.** Interfaces in `:data:source:*:api`, `:data:repository:api`,
  `:domain:api` and the public surface of `:chordpro` are documented with KDoc that explains the rules
  a second implementation must honor (see `SyncProvider` for the tone): what the type is for, what is
  opaque, what may throw, what the caller must not assume. `@param` only where the parameter is not
  self-explanatory.
- **`implementation` modules are documented by naming and structure**, plus a class-level KDoc where a
  class's job isn't obvious from its name. Don't KDoc every member of an `Impl`.
- Keep KDoc in sync when you change a signature or its behavior; a stale KDoc is worse than none.

## Formatting

- **Always use trailing commas** on the last element of any multi-line comma-separated list — function
  parameters and arguments, constructor parameters, collection literals, `enum` entries, `when` with
  multiple guards. This keeps diffs minimal and reordering clean.
- Much of the existing code predates that and has no trailing commas. Add them to the lists you write
  or edit; leave the ones you are not touching alone. This is not a reformatting project.
- **Expression bodies wherever the function is one expression**, including Composables that are a single
  layout call (`private fun ScreenSurface(...) = Surface(...)`) and one-line overrides that just delegate.
- Match the surrounding file's indentation, import order and idiom rather than reformatting to a
  personal preference — the trailing comma above is the one deliberate exception. Don't reorder imports
  of files you touch.
- Named arguments for anything where the call site would otherwise be a row of positional values —
  Koin wiring, use case invocations, multi-parameter Composables.

## Kotlin Multiplatform rules

- **`commonMain` stays JVM-free**: no `java.*`, no `KoinJavaComponent`, no JVM-only libraries. Use
  `kotlin.uuid.Uuid`, `androidx.compose.ui.text.intl.Locale`, `KoinPlatform.getKoin()` and
  `import kotlinx.coroutines.IO` for `Dispatchers.IO`.
- Platform behavior goes into `androidMain` / `desktopMain` / `iosMain` / `wasmJsMain` behind
  `expect`/`actual` — **all four actuals**, in the same change. A new `expect` that only three platforms
  implement does not build.
- Implementation classes are `internal` and named `<Interface>Impl`; Compose components in
  `:presentation` are `internal` too. Use cases are `operator fun invoke`.
- New Koin bindings go into the module's own top-level `Module.kt`, nowhere else.
- Cross layers through the `mapper/` packages; never leak a document/entity type upwards.

## Compose

- **`modifier: Modifier = Modifier` is the first parameter** of a Composable that takes one — this repo's
  order, even though the Compose guidelines say otherwise. Follow the repo.
- A Composable reads as a short list of named children. When a body grows several distinct visual groups,
  extract each into its own `private @Composable` named for what it *is* in the UI (`SongsControls`,
  `SectionHeader`), not for where it sits. A single focused widget needs no extraction.
- Extraction must not change the rendered output: don't add a `Row`/`Column`/`Box` a group didn't have,
  don't drop one it relied on, and apply parent-scope modifiers (`Modifier.weight`) at the call site.
- Material 3 Expressive only (`org.jetbrains.compose.material3`). Never import `androidx.compose.material`
  (M2). Icons are vector drawables in `composeResources/drawable/`, loaded with `painterResource` and
  passed around as `Painter`.
- Nothing appears or disappears abruptly: new UI states animate in and out the way the neighboring
  screens' empty/error states do.

## User-facing strings

- **No hardcoded user-facing strings in UI code** — `Text`, `contentDescription`, titles, labels, hints,
  dialog and notification text all come from `composeResources/values/strings.xml`.
- Read them with `com.pandulapeter.campfire.presentation.localization.stringResource(Res.string.x)`.
  **Never** the `org.jetbrains.compose.resources` overload: it ignores the in-app language.
- **Add every new key to both `values/strings.xml` and `values-hu/strings.xml`**, in the same
  commented group in both files, and translate the Hungarian properly. A missing key is a build-time
  hole that shows up as "???".
- Formatted strings are always called with their arguments (`%1$s`, `%1$d`); never concatenate a literal
  with a value. Keys are lower_snake_case, grouped by intent, and an existing key is reused rather than
  duplicated.
- Exempt: file names, preference keys, serialization identifiers, ChordPro directive names.

## Clean up after changes

- **Never leave anything unused behind.** When a change removes the last usage of a declaration, delete
  the declaration: unused imports, private/internal functions, properties, classes, `expect`/`actual`
  pairs, drawables, and string keys — **in every locale file**.
- After editing, grep for each symbol and resource you stopped using and confirm there is no remaining
  reference before you call the task done.

## Refactor old code when a change outgrows it

- **Refactor what your change makes wrong.** A new feature often leaves a name, signature or structure
  that no longer fits — fix it as part of the change instead of bolting on.
- **Rename when scope changes.** If `loadFile` starts saving too, or a `…Toggles` Composable gains a
  slider, the name now lies. Rename it and every call site, with no stragglers.
- Stay within the spirit of the change: leave touched code cleaner, don't start unrelated rewrites.

## Tests

- Only pure logic is tested, in `commonTest`, run on the desktop target: `:chordpro`,
  `:data:source:local:implementation` (zip, file storage), `:data:source:remote:*` (hashing, encoders,
  the authorization URL) and `:data:repository:implementation` (`SyncPlanner`). The UI is untested.
- **When you change any of those, add or update the tests in the same change**, and run:
  ```bash
  ./gradlew :chordpro:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
  ```
- Don't add a test module or a UI test framework for a change that doesn't warrant one.

## Keep the CLAUDE.md files in sync

- **After a significant change, update the relevant `CLAUDE.md`** — the module's own and the root one
  where it describes what you changed (module responsibilities, the data flow, a screen's behavior, the
  sync rules, the library layout, build properties). These files are unusually detailed here, and their
  value is that they are true.
- Match their prose voice and keep it terse. Routine edits that change nothing documented need no doc
  update, and don't add a new section for something that was never documented unless it earns one.

## Committing

- **Never commit automatically.** Leave the work in the tree so it can be reviewed. Only run
  `git add` / `git commit` / `git push` when that request explicitly asked for it — finishing the code,
  fixing the build or passing the tests is not an implicit instruction to commit.
- When you are asked to commit, load the `commit-messages` skill first.
