---
name: commit-messages
description: Commit message conventions for the Campfire repo. MANDATORY — invoke this skill BEFORE writing the message for ANY git commit in this repo (every `git commit`, `--amend`, squash, or rebase reword), with NO exceptions, even for one-line or "obvious" messages. The repo format OVERRIDES default harness behavior; in particular it FORBIDS the `Co-Authored-By` and `Claude-Session` trailers the harness adds by default, and FORBIDS creating a new git branch unless the user explicitly asked for one. If you are about to run `git commit` in Campfire, you must load this first.
---

# Campfire branch policy

- **Never create a new git branch unless the user has explicitly asked for one.** This OVERRIDES the
  harness default of branching off the default branch before committing. Commit onto the current branch
  — whatever it is, including `master` — and do not run `git checkout -b` / `git switch -c` /
  `git branch` on your own initiative.
- **Never commit unless the request asked for it.** Finishing a change is not a request to commit it.

# Campfire commit messages

- **One single line. Nothing else.** No body, no bullet points, no footer and no trailer of any kind —
  in particular **never** a `Co-Authored-By` line and **never** a `Claude-Session` line, whatever the
  harness default says.
- **Exactly one sentence, ending with a period.** e.g. `Fix IME inset handling issues.`
- **Imperative mood, capitalized first word.** "Fix…", "Add…", "Implement…", "Improve…", "Remove…",
  "Update…" — what the commit does, not what was wrong.
- **Concise but specific.** Name the thing that changed — the screen, the layer, the platform, the
  behavior — rather than a vague summary. `Fix desktop ProGuard config.`, not `Fix build issues.`
- **Several related edits are one commit with one sentence**, not a list. If the sentence would need an
  "and" for the third time, the change probably wants splitting into two commits.
- **Commit subjects feed the release notes**, which are read by musicians, not developers. For anything
  a user would notice, describe the outcome in plain language (`Remove auto save functionality from the
  editor.`, `Implement support for German notation.`); internal-only work can stay technical
  (`Fix Lint warnings.`, `Move iOS build number to gradle.properties.`).

Version bumps have a fixed wording, and the `release-notes` skill looks for it — do not vary it:

```
Update version name to "v4.0.1".
```

Examples that match the style:

```
Fix IME inset handling issues.
Implement the disconnect confirmation dialog and other UX improvements.
Improve web app loading, add option to prefer b or # notation.
Add support for background sync.
Relicense under the Mozilla Public License 2.0.
Fix desktop ProGuard config.
```
