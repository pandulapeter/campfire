<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->
# 21 — `printf '%b'` rewrites a release description on its way to Play

## What goes wrong at release time

`release.yml` reads the "what's new" text out of the GitHub release's description — either the
`<!-- whats-new en-US … -->` comment or the visible description with its markdown taken out — and hands it to
`android-publish.yml` verbatim. That workflow then writes it with `printf '%b'`, which interprets backslash escapes
in its argument. A release description is prose somebody typed, so any backslash in it is a character, not an
instruction:

- `\c` makes `printf` **stop producing output immediately**. A description containing, say, `C:\config` or a
  Windows path in a bug fix note truncates the Play listing's "what's new" at that point, silently. The step prints
  what it wrote, so the truncation is visible in the log — to somebody reading a log of a release that succeeded.
- `\t`, `\n`, `\0` and `\\` are rewritten into other characters, so the text on the listing differs from the release
  page.
- An unrecognized escape is left alone by most `printf`s, which is why this has not been noticed: it only bites on
  the handful of sequences that mean something.

The Play "what's new" text is then cut to 500 characters by the Python block below it and uploaded. Nobody diffs it
against the release page.

## Cause

`.github/workflows/android-publish.yml:76-93`, verified at HEAD `984861e4`:

```yaml
      - name: Generate release notes
        env:
          RELEASE_NOTES_OVERRIDE: ${{ inputs.release_notes }}
        run: |
          mkdir -p whatsnew
          if [ -n "$RELEASE_NOTES_OVERRIDE" ]; then
            printf '%b\n' "$RELEASE_NOTES_OVERRIDE" > release_notes.txt
          else
```

The `%b` is there for a reason, and it is the hand-dispatched case: the `workflow_dispatch` input at line 36 is a
single-line text box, and its description says so —

```yaml
      release_notes:
        description: 'Release notes override, use \n for line breaks (leave empty to generate them from the commit log)'
```

— so a person typing notes into the Actions form has no other way to ask for a line break. The mistake is applying
that convenience to the one caller that does not need it: `release.yml` passes a real multi-line string
(`release.yml:76-79` writes it through a `release_notes<<END_OF_RELEASE_NOTES` heredoc, which carries newlines
perfectly well), so `%b` there can only corrupt it.

## The change

Expand escapes only where a human typed the text into the dispatch form. `github.event_name` answers that exactly: it
is `workflow_dispatch` when this workflow was dispatched by hand, and the caller's event (`release`) when
`release.yml` called it.

Replace lines 76-82 of `.github/workflows/android-publish.yml` with:

```yaml
      - name: Generate release notes
        env:
          RELEASE_NOTES_OVERRIDE: ${{ inputs.release_notes }}
          # release.yml passes the release's description as it is, and a backslash in prose is a character: %b would
          # rewrite \t and \n and, on \c, stop writing the text altogether - a Play listing quietly cut in half. Only
          # the dispatch form has no way to type a real line break, so only there are escapes expanded.
          EXPAND_ESCAPES: ${{ github.event_name == 'workflow_dispatch' }}
        run: |
          mkdir -p whatsnew
          if [ -n "$RELEASE_NOTES_OVERRIDE" ]; then
            if [ "$EXPAND_ESCAPES" = 'true' ]; then
              printf '%b\n' "$RELEASE_NOTES_OVERRIDE" > release_notes.txt
            else
              printf '%s\n' "$RELEASE_NOTES_OVERRIDE" > release_notes.txt
            fi
          else
```

Everything below is unchanged: the commit-log fallback, the empty-file fallback and the Python block that cuts to 500
characters.

The dispatch input's description stays as it is — `use \n for line breaks` is still true of the only path that still
expands them.

### Considered and rejected

- **Dropping `%b` altogether** and telling the dispatcher to leave the notes empty. It removes the only way to
  publish a hand-written "what's new" without a GitHub release, which is exactly what the hand dispatch is for.
- **A second input** (`expand_escapes: boolean`) instead of reading `github.event_name`. It adds a question to the
  form whose right answer is always the same, and `workflow_call` would have to declare it too, since a called
  workflow knows only its own inputs.
- **Expanding only `\n`** with `sed 's/\\n/\n/g'` in both cases. It keeps one escape and loses the rest, so a
  description containing a literal `\n` (a code snippet, a regular expression) still comes out wrong — a smaller
  version of the same bug rather than a fix.

## Tests

None; this is workflow configuration. Run the standard suite to confirm nothing else moved:

```bash
./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
```

## Verification

The behavior is a shell one-liner, so prove it locally first:

```bash
NOTES='- Fixed the import of files under C:\config
- Made the search faster'
printf '%b\n' "$NOTES"   # stops after "C:" — the bug
printf '%s\n' "$NOTES"   # the text as written — the fix
```

Then the workflow, which **can only be proven by dispatching it**:

1. Dispatch `Publish Android` by hand with `release_notes` set to `- One\n- Two` and no `release_tag`. The step's
   `cat whatsnew/whatsnew-en-US` must print two lines — the hand-typed `\n` still works.
2. The `release.yml` path needs a real release. On a pre-release (which `release.yml` deliberately ignores) this
   cannot be exercised, so use a throwaway release in a fork: publish one whose description contains a backslash
   (`Fixed importing from C:\songs`) and confirm the Android job's `Generated release notes:` output carries the
   whole description, backslash included and nothing truncated.
3. Read the Play Console's release notes for that upload and compare them to the release page character for
   character. Needs a Play account; the log in step 2 is the same text, so step 3 is confirmation rather than the
   test.

Needs no Mac, no Windows PC and no signing key beyond what the workflow already has.

## Docs

Root `CLAUDE.md`, the `android-publish.yml` bullet. This sentence describes where the text comes from and stays
true, but the mechanism is worth one clause since it is now conditional:

> The "what's new" text comes from the workflow's `release_notes` input, which `release.yml` fills from comments in
> the release's description that the rendered page hides

becomes

> The "what's new" text comes from the workflow's `release_notes` input, which `release.yml` fills from comments in
> the release's description that the rendered page hides — carried through as it is, backslashes included; only the
> hand-dispatched form's `\n` is expanded, since a single-line text box has no other way to ask for a line break

The header comment of `release.yml` needs no change: it describes the comment format, not the transport.

## Files touched

- `.github/workflows/android-publish.yml`
- `CLAUDE.md`

## Depends on

Nothing.

## Rules

- Load the `code-style` skill before the first edit; the workflow comments follow the same voice — the reason, in
  prose, without archaeology.
- No Kotlin, no strings, no `commonMain` changes here.
