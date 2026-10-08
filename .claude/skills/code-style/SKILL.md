---
name: code-style
description: Code style, commenting, and documentation conventions for the Campfire codebase. MANDATORY — invoke this skill BEFORE writing or editing ANY source in this repo (every Write or Edit to a `.kt`/`.kts`/`strings.xml` file, new file or change to an existing one), with NO exceptions, even for a "trivial" one-line edit. It governs the MPL-2.0 header on new files, the KDoc-for-declarations / `//`-for-statements split, the "why, not what" comment voice, the trailing comma rule, `modifier` as the first parameter, one file per Composable and small cohesive files and packages, string resources, and keeping the per-module `CLAUDE.md` files in sync. Get these right while writing, not after.
---

# Campfire code style

Conventions for matching the pre-established style of this codebase. Architecture, the module graph,
the build, the coding conventions and a one-line index of the product rules are in the root `CLAUDE.md`;
the release pipeline is in `.github/CLAUDE.md`; everything else is in the nearest directory-scoped
`CLAUDE.md` (each module's, and inside `:presentation` each package's and source set's). This skill is
about how the code reads.

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

- **The mechanical rules are checked by ktlint** (Spotless, configured in the root `.editorconfig`):
  `./gradlew spotlessApply` fixes them and CI runs `spotlessCheck` with the tests. It checks only the rules
  the code already follows, the trailing comma rules not among them (ktlint cannot express the exceptions
  below), so everything in this skill is still the skill's job; a rule is enabled in a commit of its own that
  also applies it. The license header is checked by `.github/scripts/check_license_headers.py`.
- **Always use trailing commas** on the last element of any multi-line comma-separated list — function
  parameters and arguments, constructor parameters, collection literals, `enum` entries, `when` with
  multiple guards. This keeps diffs minimal and reordering clean.
- The whole codebase has them, so a list without one is an oversight rather than an older style. The
  closing bracket decides: a `)` or `]` that starts a line of its own ends a list that wants a trailing
  comma; one that sits at the end of the last element's line does not. A parameter list broken across
  lines counts even with a single parameter in it, since a second one is the expected next edit — but a
  single-argument *call* written that way does not, and neither does a `js("""…""")` block.
- `enum` entries follow the same rule, except where the list ends in a `;` because members come after it.
  A `when` branch with several conditions keeps the last one on the arrow's line, so there is nothing
  to put a comma after.
- **Expression bodies wherever the function is one expression**, including Composables that are a single
  layout call (`private fun ScreenSurface(...) = Surface(...)`) and one-line overrides that just delegate.
- Match the surrounding file's indentation, import order and idiom rather than reformatting to a
  personal preference. Don't reorder imports of files you touch.
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
- **Never give a defaulted parameter to the constructor of a `@Single` / `@Factory` / `@KoinViewModel`
  class, or to a `@Single` module function.** The Koin compiler plugin runs with `skipDefaultValues = true`:
  such a parameter compiles, passes `:app:di`'s graph check and silently gets its default in production.
  A collaborator that tests replace and production does not is built in a `@Single` module function that
  passes every argument explicitly.
- Cross layers through the `mapper/` packages; never leak a document/entity type upwards.

## Files and packages

- **One file, one thing, named after it.** A top-level class, interface or object gets a file of its own;
  small private helpers used only by it stay with it. Shared non-UI helpers (constants, state classes,
  `Modifier` extensions, `remember…` functions) go into a file named for what they are
  (`SongCardDefaults.kt`, `ListItemMetrics.kt`, `SearchBarMotion.kt`) — never `Utils.kt` / `Helpers.kt`.
- **Files stay small.** Aim for under ~500 lines. A file that outgrows that is split by cohesion as part of
  the change that grows it: private state classes become `internal` top-level classes in files of their
  own, a pure algorithm moves next to its users in a file named for it. A pure algorithm that splitting
  would only scatter (`SectionGrid`, `PrintLayout`) may stay whole.
- **Package by role, not by first user.** A screen's package (`ui.screens.<screen>`) holds only that
  screen; a building block a second screen needs moves to a shared package (`ui.components`, `ui.chords`,
  `ui.metronome`, `ui.playing`, `ui.search`, `ui.songLayout`, …) rather than being imported out of the
  screen that first used it. A full screen lives under `ui.screens` even if it is shown over the app
  (`ui.screens.export`), and `ui.dialogs` holds dialog and sheet content. Tests sit in the package of the
  code they test.
- **No god objects.** When a class gains a responsibility unrelated to the ones it has, give that
  responsibility a collaborator of its own instead of another dozen members. Interfaces are as narrow as
  their clients (split one that two clients use halves of); a long-lived class takes its
  `CoroutineScope`, dispatcher and clocks rather than building them, so a test can pass virtual time.
- **A behaviour-preserving split moves code verbatim**: the same declarations, KDoc with its declaration,
  `private` widened to `internal` only where another file now needs it (check the package for a name clash
  first), and nothing else changed in the same commit.

## Compose

- **`modifier: Modifier = Modifier` is the first parameter** of a Composable that takes one — this repo's
  order, even though the Compose guidelines say otherwise. Follow the repo.
- Material 3 Expressive only (`org.jetbrains.compose.material3`). Never import `androidx.compose.material`
  (M2). Icons are vector drawables in `composeResources/drawable/`, loaded with `painterResource` and
  passed around as `Painter`.
- Nothing appears or disappears abruptly: new UI states animate in and out the way the neighboring
  screens' empty/error states do.

## Composable structure

- **Every top-level UI Composable that is not `private` lives in a file of its own, named exactly after
  it** (`SongCard` in `SongCard.kt`). Its `private` sub-Composables and helpers used only by it stay in that
  file; the moment a second file needs one, it becomes `internal` and moves to a file of its own. Value
  Composables (`rememberX`, `textResource`, `Modifier` extensions) are not UI Composables: group them by
  cohesion in a file named for them.
- **A Composable reads as a short, flat list of named children**, not a deep tree of layout primitives
  interleaved with logic. When a body grows several distinct visual groups, extract each into a
  Composable named for what it *is* in the UI (`SongFilters`, `SectionHeader`, `PlaybackControls`), not
  where it sits (`MiddleRow`). Extract when a group has a clear name, nests more than ~2 layout levels
  inside its parent, or the parent has outgrown a screenful; a single focused widget needs no extraction.
- **Each extracted Composable takes only the state and callbacks it uses**, plus a `modifier` when its
  parent positions it. Shared components take state and lambdas, not the whole `CampfireViewModel` (older ones that
  still take it are converted when a change touches them); only a screen receives the view model. Keep lambdas stable
  (`remember` them or pass method references) so lists don't recompose on every frame.
- **Extraction must not change the rendered output.** A Composable is layout-transparent: one that emits
  several siblings without a wrapper drops them straight into the caller's `Row` / `Column`, so don't add a
  `Row`/`Column`/`Box` a group didn't have, don't drop one it relied on, and apply parent-scope modifiers
  (`Modifier.weight`) at the call site, passing the result in as the child's `modifier`.
- **Decisions are pure functions, not Composable bodies.** Geometry, filtering, ranking, rounding, which
  item index a key is at — anything computed from plain data — is an `internal` function next to its
  Composable, and gets a test in `commonTest`. The Composable only remembers its result and draws it.
- **No service-locator calls inside a Composable** beyond what the root composable wires up; a component
  gets what it needs as a parameter or a `CompositionLocal` provided once near the root.

## User-facing strings

- **No hardcoded user-facing strings in UI code** — `Text`, `contentDescription`, titles, labels, hints,
  dialog and notification text all come from `composeResources/values/strings.xml`.
- Read them with `com.pandulapeter.campfire.presentation.localization.stringResource(Res.string.x)`.
  **Never** the `org.jetbrains.compose.resources` overload: it ignores the in-app language.
- **Add every new key to both `values/strings.xml` and `values-hu/strings.xml`**, in the same
  commented group in both files, and translate the Hungarian properly. A missing key is a build-time
  hole that shows up as "???".
- Formatted strings are always called with their arguments (`%1$s`, `%1$d`); never concatenate a literal
  with a value. A sentence that takes text somebody else wrote — a title, a tag, a header value, a file
  or account name — is read with `textResource(Res.string.x, text)` (`:presentation`'s
  `components/TextResource.kt`) instead: the plugin's formatter scans its own output a second time, and
  the `% s` in `100% sure` is a format specifier to it (`pluralTextResource(Res.plurals.x, count, count.toString(),
  text)` for a `<plurals>` that carries such text). Keys are lower_snake_case, grouped by intent, and an existing key is reused rather than
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

- Only pure logic is tested, in `commonTest`, run on the desktop target: `:data:model` (name identity, tags),
  `:chordpro`, `:domain:implementation` (`ImportPlanner`), `:data:formats` (zip, PDF/Word readers),
  `:data:source:local:implementation` (file storage), `:data:source:remote:*` (hashing, encoders, the authorization
  URL), `:data:repository:implementation` (the caches), `:data:sync:implementation` (`SyncPlanner`, the engine),
  `:metronome:*` and `:presentation` (pure helpers pulled out of the screens and the view model, never a Composable).
  The root `CLAUDE.md` has the full list. The UI itself is untested.
- **When you change any of those, add or update the tests in the same change**, and run:
  ```bash
  ./gradlew :data:model:desktopTest :data:formats:desktopTest :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest :data:sync:implementation:desktopTest :metronome:api:desktopTest :metronome:implementation:desktopTest :presentation:desktopTest
  ```
- Don't add a test module or a UI test framework for a change that doesn't warrant one.
- **Tests assert behaviour, not implementation.** Check what a user or a caller could observe — a returned value, a
  file on disk, an emitted state — never a private constant copied into the test, an exact count of internal calls or
  a pretty-printed format searched as text (decode it). A call order on a fake is asserted only where the order is
  itself the documented behaviour. Time is virtual (`runTest`), never a `delay` on a real dispatcher.
- **Every test is named as a backtick sentence** in the present tense (`` `a keep-both import numbers the new file` ``),
  uses `kotlin.test` only, and sits in the package of the code it tests, in the module that owns that code. A module's
  repository and use case fakes are shared stubs in its test sources (each unused member failing the same way), not a
  copy per test file.

## Keep the CLAUDE.md files in sync

- **After a significant change, update the relevant `CLAUDE.md`** — the nearest directory-scoped one: the
  module's own, or inside `:presentation` the one of the package or source set the change is in (a screen's
  behavior goes into `ui/screens/<screen>/CLAUDE.md`, a shared component's into `ui/components/CLAUDE.md`,
  a rule several modules enforce into `ui/CLAUDE.md`). The root `CLAUDE.md` changes only for a coding
  convention, the architecture, the library layout, the build, or a line of its `## Product rules` index;
  the release pipeline and the workflows are `.github/CLAUDE.md`'s. These files are unusually detailed here,
  and their value is that they are true.
- **No paragraph runs past ~1,500 characters**, and lines are hard-wrapped at ~120 columns: a topic that
  outgrows that is cut at sentence boundaries into paragraphs or bullets under `###` headings, so `grep -n`
  and a diff still point at something readable.
- Match their prose voice and keep it terse. Routine edits that change nothing documented need no doc
  update, and don't add a new section for something that was never documented unless it earns one.

## Committing

- **Never commit automatically.** Leave the work in the tree so it can be reviewed. Only run
  `git add` / `git commit` / `git push` when that request explicitly asked for it — finishing the code,
  fixing the build or passing the tests is not an implicit instruction to commit.
- When you are asked to commit, load the `commit-messages` skill first.
