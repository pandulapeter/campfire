# Fourteenth review

Reviewed commit: `a5798b31b` on `master`, clean tree. Angle: a general fresh-eyes sweep (bugs, performance, UX/UI,
logic), medium budget capped at about an hour: five Opus area reviewers (`:chordpro`; data, sync and domain; song
details, editor and export; lists, sheets, settings and navigation; platform shells, web, CI and docs), no live run.
Every finding was verified against HEAD by a writer per lane (probe tests for `:chordpro`), and every plan was then
challenged by a fresh agent that did not write it.

## Headlines

1. **10** — a second device's first sync doubles the demo setlist (and any undated setlist imported on two devices on
   different days): the import dates it with the day it was planted, so the bytes differ and sync makes a ` (2)` copy.
   Hits nearly every user with two devices. Decided: plant the demo undated (b).
2. **11** — a change made while the first library scan is still running (a tag, a save, a cover) publishes the
   half-read library as finished: the cover cache prunes the covers of every song not scanned yet, a web deep link
   resolves against half the library.
3. **30** — a desktop relaunch within 5–17 s of quitting with a sync run going starts unlocked next to the closing
   process, and the two then share the library for the rest of the session.
4. **01** — a Hungarian key written `C-dúr` is never transposed or converted between notations.
5. **23** — rotating a wide phone (or resizing across 840dp) while typing in the editor closes the keyboard.

## Index

| #  | Plan                                                                                    | Severity | Lane |
|----|-----------------------------------------------------------------------------------------|----------|------|
| 01 | Add the Hungarian `dúr` to the spelled-out key words                                    | medium   | A    |
| 02 | End a delegated environment in the splitter on any `{end_of_…}`                         | low      | A    |
| 10 | Stop a second device's first sync from doubling a setlist that differs only in its date | medium   | B    |
| 11 | Keep a change during the first library scan from publishing the partial list as Idle    | medium   | B    |
| 12 | Make the cover cache prune skip files that are not cover keys                           | low      | B    |
| 20 | Fade the welcome sheet and dialog at their scrolled edges                               | low      | C    |
| 21 | Skip the Setlist assignments sheet when no setlist can be ticked                        | low      | C    |
| 22 | Search the import report with the library's search folding                              | low      | C    |
| 23 | Keep the editor field focused across a pane layout change                               | medium   | C    |
| 24 | Fade the export screen's lists at the top only where they reach the window bottom       | low      | C    |
| 30 | Make a desktop relaunch wait for a closing instance                                     | medium   | D    |
| 31 | Add `import` to the web routes                                                          | low      | D    |
| 32 | Bring the backup-exclusion docs up to date                                              | low      | D    |

## Lanes

| Lane | Area                      | Plans, in order | Files owned                                                                                      |
|------|---------------------------|-----------------|--------------------------------------------------------------------------------------------------|
| A    | `:chordpro`               | 01, 02          | `chordpro/**`                                                                                     |
| B    | data, sync, domain        | 12, 11, 10      | `data/**`, `domain/**`, the demo setlist resource and `CampfireViewModel.kt` only if 10 takes (a)/(b) |
| C    | `:presentation` UI        | 20, 21, 22, 23, 24 | `presentation/**` (except the demo resource)                                                 |
| D    | shells, web, docs         | 31, 32, 30      | `app/**`                                                                                          |

**Merge order: A, B, D, C.** A and B are leaf modules the others do not depend on for these fixes; D is docs and
platform code; C touches the most shared UI files and `presentation/CLAUDE.md`, so it goes last.

**Shared files:**
- Root `CLAUDE.md`: plan 10 (the demo setlist's dating / sync's first meeting) and plan 32 (the opening paragraph's
  backup sentence). Different paragraphs; on a conflict keep both sides' sentences.
- `data/repository/implementation/CLAUDE.md`: plans 10 and 11, same lane, sequential.
- `presentation/CLAUDE.md`: plans 21, 22, 23, same lane, sequential.
- `CampfireViewModel.kt`: plan 30 only adds a comment to `EXIT_SYNC_GRACE`; plan 10 touches it only under option
  (a)/(b). Keep both on a conflict.
- No plan adds strings.

## Decisions

- **10 — how to stop the demo setlist doubling:** (b), plant the demo setlist undated from both demo paths (decided by the user 2026-10-04).

## Challenge

Ran on all 13 plans. Sound: 01, 12, 20, 21, 22, 24, 31, 32. Amended:
- **02** — the splitter also copies the parser's handling of a `{start_of_…: label}` inside delegated text, not only
  the ends.
- **10** — recommendation changed from (c) to (b): "no index entry" also follows every Disconnect, an account change,
  a reinstall and a backup restore, where (c) would silently overwrite a date the user moved.
- **11** — `publishPartialData` folds the recorded changes inside `_dataState.update` (a plain assignment raced
  `updateData`); it also cures a change before the first read skipping the read entirely.
- **23** — the focus listener goes directly over the `BasicTextField` (outside `horizontalScroll` it reported unfocused
  while typing) and reads `hasFocus`; the flag is a plain holder.
- **30** — the short "endpoint present but silent" budget counts from when the endpoint appears; an instance whose
  listener failed leaves a placeholder endpoint so it does not cost every launch 30 s.

## Checked and found solid

`:chordpro` at scale (a 500 KB song: every operation linear, the slowest about 100 ms; every regex free of
catastrophic backtracking); `SyncPlanner` (every branch), `SyncEngine` (folding, guards, resolve naming, the deletion
batch), `SyncRepositoryImpl` scheduling, the Dropbox provider, the cover repository and its limiter, `ImportPlanner`
and the import use cases, file naming; the editor's caches, draft and preview; song details stepping and paging;
`PrintPdfWriter`/`PrintRenderer`; the thirteenth review's 31 fixes (sheet closing, Remove-cover cancel, What's new
timing, chip flow, search ranking); the release workflows and scripts; Android/iOS/desktop/web shells apart from the
plans above.

## Dropped after verification

- **25** (Prettify on every keystroke in the editor) — it does re-run per keystroke, but measured at about 0.3 ms on a
  50 000-character song, a quarter of the parse that already runs; not worth moving.

## Manual checks owed

Filled in from the plans' Manual check sections once they land; see each plan.
