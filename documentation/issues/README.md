# 57th review: architecture, duplication, and unit-test coverage

Reviewed at `67d82f42a` on `main`; the review's own fixes end at `cfc1b961b`. Angle (the owner's): architectural issues
and code duplication worth fixing (a rule that lives twice and must be kept in step), and gaps in unit-test coverage.
Five Opus area reviewers (libs; storage, formats, remote, and sync; domain, repository, model, and DI; presentation
state and wiring; presentation UI), a Sonnet docs check (no false claims), two plan writers, three challengers, and one
regression read (clean).

## Headlines

- **iOS backups miss every picture added through the editor** (plan 05). Save copies a draft picture into
  `library/files/` with `copyItemAtPath`, which keeps the draft folder's exclude-from-backup mark. Confirmed with a
  Foundation probe. Plan 05 fixes it first, then repairs the pictures already left out.
- **A shared link's copy is built by two builders** (plan 01). The export builds the first copy and sync builds every
  later one, and they are kept equal only by `SharedCopyParityTest`.
- **The capo/shape reading of a song is written three times** (plan 06): the page, the PDF, and the chord shape sheet.
- 24 simple fixes landed during the review: mostly one rule in place of two or more copies, and tests of pure logic
  that had none.

## Fixed during the review

- `98a472fb4` Mark an unreadable colour and a picture that names nothing in the editor as the parser reads them.
- `3d8013305` Hold the song summary's library files to the reading the library keeps them by in the generated song tests.
- `261f18e04` Count a chord sheet's columns by one rule for plain text and for a page's right-to-left chord boxes.
- `87c2b4b9f` Test the notation's page format, percussion map, and message rewrites without the engine.
- `993b98618` Remove the unused whole-document preferences save, leaving every preference change to the atomic update.
- `b69a3b343` Keep the import's file writing inside the domain, so every import goes through the use case that records it.
- `6aaa55a98` Pin the shared copy fingerprint against the 5.5.0 values.
- `8b3a55dd2` Test how a sync run's changes to the shared links list apply to a list another device changed.
- `7578583b5` Keep the copy of an unreadable preferences file out of the iOS device backup, as Android already does.
- `d6ad84fbe` Test that every file the local sources write outside the songs, setlists, pictures, and preferences stays out of the iOS device backup.
- `05f341cc5` Name the archive folder of a song's pictures and attachments once for the export, the shared copy, and the import.
- `ab311f930` Decide once which of a song's sections the preferences draw, for the screen and the PDF alike.
- `cfe71f64f` Test the Play update policy as a pure function.
- `faef6b443` Word an import's notification from the import screen's own table of phases and size units.
- `a8d6e1136` Count the desktop's wait for a closing instance from the exit's own settling budget.
- `9088b3f1c` Ask in one place whether an editor holding unsaved text is on the back stack.
- `eab400991` Decide once whether read only mode is in effect, for the screens and the launch address.
- `b4ee0bf63` Say that a picture for a missing one could not be stored, rather than crashing, when its write fails.
- `5e35d8ae8` Fit a picture the user adds and word its refusal in one place for the editor and a missing picture.
- `9fa7da44a` Test that the editor finds the ABC tunes a song draws.
- `bf7732399` Size a chord grid's hold lines, edges, repeat dots, and volta brackets once for the screen and the PDF.
- `bfe8cc838` Fade what scrolls under a pinned search field in one way for the tool pages and the import review.
- `0e28af38f` Name the arrow key a song's step and switch buttons show from the rule the keyboard steps by.
- `cfc1b961b` Name each step of an import in its notification as the import screen does, rather than calling it all unpacking.

All four platforms and `:tools:screenshots` compile at `0e28af38f`; `./gradlew desktopTest` is green.

## Index

| #  | Plan                                                                            | Severity | Lane |
|----|---------------------------------------------------------------------------------|----------|------|
| 01 | Build a shared link's first copy with sync's builder; drop the export's shared-copy mode | medium | H |
| 03 | Inflate PDF streams through the streaming inflater; delete the array decoder     | low      | F    |
| 04 | One bounded public-https GET for covers and linked pictures                      | low      | R    |
| 05 | iOS backup marks decided by the storage from one allow-list (fixes the picture bug first) | medium | S |
| 06 | Read a song named by its shapes in one function for the page, the PDF, and the shape sheet | high | P |
| 07 | Let `:libs:chordpro` decide what a directive value and a link or attachment name hold | medium | P |

## Lanes and merge order

| Lane | Area                         | Plans  | Files owned                                                                 |
|------|------------------------------|--------|-----------------------------------------------------------------------------|
| F    | `:data:formats`              | 03     | `data/formats/**`                                                           |
| R    | remote downloads             | 04     | `data/source/remote/implementation/**`                                      |
| S    | iOS device backup            | 05     | `data/source/local/implementation/**`, the root `CLAUDE.md`'s privacy lines |
| H    | shared copies                | 01     | `data/sync/implementation/**`, `data/repository/api/**`, `data/model/**` (`ExportedFile`, CLAUDE.md), `domain/**` export + sharing, `ExportController.kt` + `ExportControllerTest.kt` (the `isSharedCopy` arguments only) |
| P    | presentation song reading, form sheets | 06 → 07 | `presentation/**` (other than lane H's lines), `libs/chordpro/**/edit/` |

**Merge order: F, R, S, H, P.** F, R, and S are disjoint and run first; two at once at most (Gradle). H goes before P
because both edit `ExportController.kt` (H removes the `isSharedCopy` arguments, P restructures the song reading on
other lines); P touches the most shared UI files, so it goes last.

**Shared files:** `ExportController.kt` / `ExportControllerTest.kt` (H then P; resolve by keeping both sides).
`domain/implementation/CLAUDE.md`, `data/model/CLAUDE.md`: lane H only. Root `CLAUDE.md`: lane S only.

## Decisions

- **05: repair existing files' backup marks on every launch?** Recommended **yes**: otherwise the pictures the bug
  already left out of iCloud backups stay out. The cost is one listing of `library/files/` and `preferences/` per
  launch, and a write only where a mark is wrong. **Owner, 2026-11-22: yes, repair each launch.**
- **01: `sharedCopyAttachments` ends up with one caller.** Default taken: leave it in `:data:model`, update its KDoc.
- **01: `SharedCopyParityTest`** still proves the file-list lookup against the index lookup end to end. Default
  taken: keep it at 200 seeds.
- **Import notification wording (found by the fixer of `faef6b443`).** While an import prepares, the notification says
  one "Unpacking" sentence for the whole stage, but the import screen says Reading, Comparing, or Downloading pictures.
  **Owner, 2026-11-22: the notification follows the screen's phase**; fixed during the review (one commit).

## Checked and found solid

- Image and attachment syntax each live once; `libraryFilesNamedBy` is the one answer to "files a song keeps" (now
  property-tested against the summary). `ChordProColors` is one parser. Roman numeral import reuses chordpro's.
  Instrument selection of tabs and tunes is one function. Metronome and tuner unchanged since the 54th.
- Public-host check, attachment and picture allow-lists, size caps, name folding, the per-platform `FileStorage`s (one
  contract test on JVM, iOS, and OPFS), the shared-links journal, and staging.
- Use cases forwarding to repositories are the required layering; `ImageRepositoryImpl`, the download routing, import
  limits, the import pipeline, and the export use cases are well tested. Shared-link notices come only from the use
  cases that rename or delete.
- `FeatureSwitches` is the single source for destinations and the chrome; `ImportController` is heavily tested; the
  view model's wiring stays inside the documented holder pattern.
- Screen and PDF already share picture fitting, the ABC part, tab selection, section grouping, chord anchors, tab
  staff geometry, diagrams, notation painting, and scale analysis. Every icon button goes through `CampfireIconButton`;
  no raw `ModalBottomSheet` outside `dialogs/sheet`.
- The `CLAUDE.md` files of every directory changed since the 56th review: no false claims.

## Dropped after verification

- **02, move `ImportPlan.withAdded` into the domain planner.** The only rule both would share, the song comparison, is
  already the same rule: every entry's text is prettified before planning and prettifying is idempotent. The move would
  add an interface, an implementation, and a third copy of the rules in `ImportControllerQueueTest`'s fake.
- **"No test pins the two shared-copy builders' parity"** (one reviewer). `SharedCopyParityTest` (desktopTest, 200
  seeds) does; plan 01 still removes the second builder.
- **Rewriting every library file a song names, written three times** (`SaveWithDraftImagesUseCaseImpl`,
  `IncomingImages`). Low value; worth a `withLibraryFilesRewritten` only when that code is next touched.
- **The exit asks about unsaved editor text only with the editor on top.** Deliberate: its answers leave an editor on
  top, and one underneath keeps its text in the stored draft. A comment now says so (`9088b3f1c`).
- **Dropbox's download address built in `:data:model`**: matters only with a second sync provider, which is settled.
