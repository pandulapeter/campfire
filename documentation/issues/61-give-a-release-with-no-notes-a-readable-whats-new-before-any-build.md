# Give a release with no notes one "What's new" for every store before any build starts, instead of failing the Apple and Windows jobs after their uploads

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** CI (Android, iOS, macOS, Windows)
**Files:** `.github/scripts/release_description.py`, `.github/scripts/test_release_description.py`,
`.github/workflows/publish-all.yml` (header comment only), root `CLAUDE.md` (one sentence),
`.claude/skills/prepare-release/SKILL.md` (only under option A)

## Problem

`release_description.py` falls back from the `whats-new en-US` block to the visible description, and checks only the
upper limits; an empty result is passed on as it is (8ee010b36, lines 59-63 and 86-94):

```python
notes = match.group(1).strip() if match else ""
if not notes:
    visible = re.sub(r"<!--.*?-->", "", body, flags=re.S)
    ...
    notes = re.sub(r"\*\*|`", "", visible).strip()
```

Running `read("")` and `read("<!-- play-store update-priority: 0 -->\n<!-- app-store submit: true -->\n")` at HEAD
returns `release_notes: ""` with no error. And this is not an exotic input: the prepare-release skill writes exactly
that for a small release — "**An empty block is valid** for a small release with no notes. It has zero characters and
no placeholder bullet" and "For empty notes omit the visible bullets and leave the `whats-new en-US` block empty"
(`.claude/skills/prepare-release/SKILL.md:133-134, 240`).

`prepare` is green, every test passes, and all six jobs start. Then:

- **Android**: `publish-android.yml` "Generate release notes" sees an empty `RELEASE_NOTES_OVERRIDE` and takes the
  hand-dispatch fallback, `git log $LAST_TAG..HEAD --pretty=format:'- %s'` — the commit subjects go to Play
  production as the public "What's new".
- **Windows** builds for ~20 minutes, then `microsoft_store_submission.py` `main` fails: `if not notes: fail("An update
  needs release notes …")`.
- **iOS and macOS** build, upload with `altool`, wait up to 90 minutes for processing, attach the build, and only then
  fail (`app_store_submission.py:204-206`, `if has_earlier: if not notes: fail(...)`). A re-run of the job dies at the
  upload, because App Store Connect has seen the build number, so the release has to be finished by hand in App Store
  Connect or by hand dispatches carrying `build_number` and `release_notes`.

The script's own docstring gives this as the reason the notes are checked up front ("an over-long note found there
fails the release at the very end, with a build already uploaded"); the empty case is the one it does not check.

## Fix

A product choice — **decision for the user**. Either way the change is in `release_description.py`, before any build.

**Option B (recommended): a fixed line stands in for no notes, for every store.** After the visible-description
fallback:

```python
# What every store gets where the release says nothing: App Store Connect and Partner Center refuse an update without
# notes, and Play would otherwise be given the commit log meant for a hand dispatch.
DEFAULT_NOTES = "Bug fixes and improvements."
...
if not notes:
    notes = DEFAULT_NOTES
```

This keeps the skill's "an empty block is valid" meaning something (a small release ships, and its notes are the
generic line), keeps the rule that every default is the stronger action (the release goes out), and means the
release path never reaches Android's commit-log fallback. Update the docs to match: in `publish-all.yml`'s header
(line 33-34, "Without the notes the visible description stands in for them, with its markdown taken out") add "and
where that is empty too, \"Bug fixes and improvements.\""; in the root `CLAUDE.md` ("It falls back to the visible
description with its markdown taken out — or, dispatched by hand with nothing given, to the commit log since the
previous tag.") insert ", and to \"Bug fixes and improvements.\" where the description has no visible text either"
after "markdown taken out". Edit only that sentence.

**Option A: stop the release.** `if not notes: errors.append("The release has no notes: write a whats-new en-US block
or a visible description.")`, and change the prepare-release skill so an empty release is not produced: replace the
two passages quoted above with "A release with nothing to say still writes one bullet, `- Bug fixes and improvements.`".
Costs a re-publish of the release whenever someone forgets, but never ships a generic line unasked.

Neither option touches the hand-dispatch forms, which keep their own fallbacks.

## Tests

In `test_release_description.py`:
- Option B: `test_no_notes_anywhere_give_the_default_line` — `read("")`, `read("<!-- whats-new en-US\n-->\n")` and the
  comment-only body above each give `release_notes == "Bug fixes and improvements."`; a body with visible text still
  gives that text.
- Option A: `test_no_notes_anywhere_stop_the_release` — the same three bodies raise `ReleaseDescriptionError`.

## Manual check

On the next small release written by the prepare-release skill with an empty block: `prepare` either writes the
default line into the run's outputs (B) or stops with the error (A); under B, the Play, App Store, Mac App Store and
Microsoft Store listings show "Bug fixes and improvements." as the new version's notes.
