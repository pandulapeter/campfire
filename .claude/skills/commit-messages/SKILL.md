---
name: commit-messages
description: Commit message conventions for the Campfire repo. MANDATORY — invoke this skill BEFORE writing the message for ANY git commit in this repo (every `git commit`, `--amend`, squash, or rebase reword), with NO exceptions, even for one-line or "obvious" messages. The repo format OVERRIDES default harness behavior; in particular it FORBIDS the `Co-Authored-By` and `Claude-Session` trailers the harness adds by default, and FORBIDS any git branch but the one worktree branch per session its workflow names. If you are about to run `git commit` in Campfire, you must load this first.
---

# Campfire commit workflow

Several sessions work on Campfire at once. Sharing one checkout made them wait on each other: git holds the index lock
while the pre-commit hook builds, and each one's tests ran over the others' half-made edits. So:

- **One worktree per session.** Before the first edit, `git worktree add .claude/worktrees/<name> -b <name> main` (or
  the harness's worktree tool) and work only there; never edit in the main checkout, which is the user's. No other
  branch is created unless the user asks for one. A worktree has its own index and `build/`, so nothing in it waits on
  another session or builds another session's work.
- **Commit often, unasked.** Commit each step once it compiles, without waiting for the user's hand check (subagents
  too). Stage your paths, run `.githooks/verify` (the hook's checks and Gradle, queued two builds at a time across every
  worktree, recording the staged tree), then `git commit -m "…"` naming no paths, which the hook then passes in seconds.
- **Land each commit right away**: `git rebase main` in the worktree, then `git -C <main checkout> merge --ff-only
  <branch>`; if `main` moved in between, rebase again. A merge the main checkout's own uncommitted files refuse is the
  user's to resolve: ask. Never push unless asked.
- **Never touch another session's locks**: a `.git/index.lock` or a slot in `.git/gradle-slots` belongs to a running
  process (the queue takes over a dead one's slot itself).
- **When the task is done**, `git worktree remove` the worktree and `git branch -d` its branch.

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

Version bumps have a fixed wording; do not vary it:

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
