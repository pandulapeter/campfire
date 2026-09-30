# Settle the references to the release check that no longer exists

**Decided (D1, 2026-09-30): option A — drop the references.**

**Challenged:** amended — facts verified (the three files are gone, `b0e98cfa8^:documentation/testing/release-check.md` exists, `SCROLLING_PERFORMANCE.md` was never in git history, and no other tracked file names `features.md`, `file-format.md`, `sync.md` or the testing tools). One wording change under option A: the checked-in root `CLAUDE.md` must not point at the user's private memory note.

**Kind:** docs / process  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `CLAUDE.md` (root, the testing paragraph around line 286), `.claude/skills/codebase-review/SKILL.md` (line 167),
`presentation/CLAUDE.md` (line 132) — and, under option B, `documentation/testing/README.md`,
`documentation/testing/release-check.md` and `documentation/testing/tools/*` restored

**Decision awaited: D1 in the README.** Carry out the option the user chooses.

## Problem

The root `CLAUDE.md` (at 9ab7ca54e, line 286) prescribes a manual gate before every release and a rule for every
change:

> Before a release, `documentation/testing/release-check.md` is run on a Mac (its `README.md` says how): half an hour
> of the checks whose failure would block one. A change to what it exercises — the first run, importing, sync, the
> packaged builds — updates it in the same change.

`.claude/skills/codebase-review/SKILL.md` line 167 sends the manual checks a release would be blocked by into the same
file, and `presentation/CLAUDE.md` line 132 says "See `documentation/SCROLLING_PERFORMANCE.md` for the audit and device
profiling recipe." None of the three files exists: `documentation/` holds `TO_DO.md`, `images` and `screenshots`.
`documentation/testing/` (the README, `release-check.md`, `tools/dropbox_folder.py` and the desktop driver patch) and
the other documents went in `b0e98cfa8` ("Update the readme, remove outdated documentation.", 2026-09-26, before the
ninth review's range, which is why nine sweeps never saw it). Every agent reading the root `CLAUDE.md` is told to
update a file it cannot find, and the only manual release gate the docs name is not there.

## Fix

**Option A (recommended): drop the references.** The user removed the folder as outdated, four days after having it
written; respect that. In the root `CLAUDE.md`, replace the two sentences quoted above with one that describes what
is true: "The UI itself is untested by code … and is never run by CI; a release is checked by hand, on a Mac, before it
is published." (No pointer to a memory note: it is not part of the repository.) In
`.claude/skills/codebase-review/SKILL.md`, change line 167 so that the manual checks a release would be blocked by go
into the memory note (not into a file). In `presentation/CLAUDE.md` line 132, delete the sentence that names
`SCROLLING_PERFORMANCE.md`; the paragraph's own description of `ListTopFade` stands without it.

**Option B: restore the release check.** `git show b0e98cfa8^:documentation/testing/release-check.md` (and the README
and `tools/`) back into place, then update it for what has changed since 2026-09-26: the one-row-at-a-time default,
the pedal stepping, Manage tags, the library deletion, the web address. The Dropbox snapshot tool and the driver patch
come back with it. Leave the three references as they are, except the `SCROLLING_PERFORMANCE.md` one, which was never
part of the testing folder and is deleted either way.

## Tests

None; documentation only.

## Manual check

None. After option A, `git grep -n 'release-check\|documentation/testing\|SCROLLING_PERFORMANCE'` prints nothing.
