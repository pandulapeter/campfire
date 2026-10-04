# Find the last release tag with `git describe` over the numeric tags in the prepare-release skill

**Kind:** tooling  ·  **Severity:** medium  ·  **Platforms:** all (release notes for every store)
**Files:** .claude/skills/prepare-release/SKILL.md

**Challenged:** amended — the `HEAD^` fallback is replaced by `--exclude <version being prepared>`, which also handles a tag that has more commits on top of it.

## Problem

Step 2 of `.claude/skills/prepare-release/SKILL.md` (lines 28-35 at 800ebde0b) tells the agent to find the previous
release boundary with:

```bash
grep -n 'campfire.versionName' gradle.properties
git tag --sort=-v:refname | head -3
```

and then uses `<last tag>..HEAD` for every later command (steps 3, 4 and the contributor lookup at line ~199). At
HEAD that command prints:

```
v1.0.0
4.6.0
4.5.0
```

`v1.0.0` is a 2019 tag (`22fa8a19b`, "Increment version code to 20.", 2019-04-07) from before releases were tagged with
bare numbers; `-v:refname` sorts the `v`-prefixed name above every numeric one. An agent following the skill literally
takes `v1.0.0` as the last release, so `git log v1.0.0..HEAD` covers seven years of history, and the GitHub notes, the
stores' blurb and the in-app What's new message are built from the wrong range — including step 5's "judge every change
against the last release tag" rule, which then lets long-released changes and fixes through as new. Step 1 already says
the GitHub tag is the number without the `v` prefix, so only numeric tags are releases.

`.github/workflows/publish-android.yml:112` already uses `git describe --tags --abbrev=0 HEAD^` for its fallback notes,
which is unaffected (it takes the nearest reachable tag, `4.6.0`). No other place in `.claude` or `.github` derives the
last tag (`grep -rn "git describe\|git tag" .github .claude`).

## Fix

Replace the second command of step 2 with the nearest reachable release tag, restricted to release-shaped names, and
keep a sorted list as a cross-check:

```bash
grep -n 'campfire.versionName' gradle.properties
git describe --tags --abbrev=0 --match '[0-9]*.[0-9]*.[0-9]*' --exclude "$(sed -n 's/^campfire\.versionName=//p' gradle.properties)"
git tag --list '[0-9]*.[0-9]*.[0-9]*' --sort=-v:refname | head -3
```

and add one sentence after the block: the `git describe` line prints `<last tag>`; release tags are bare version
numbers, so older `v`-prefixed tags (`v1.0.0`) are never a boundary, and the version being prepared is excluded so
that a run after that version was already tagged (on HEAD or on a commit before it) still finds the release before
it. Leave every other `<last tag>` use as it is — they all read the value step 2 found.

(The challenge replaced a `HEAD^` fallback: it only worked when the new tag sat on HEAD itself; with commits on top
of the tag, `HEAD^` still reaches it. Checked at 800ebde0b with git 2.54: prints `4.6.0`; with `--exclude 4.6.0` it
prints `4.5.0`, confirming the exclusion works.)

`git describe` is preferred over sorting because it follows the history: a tag on a branch HEAD does not contain (a
hotfix released from elsewhere) is not taken as this branch's boundary.

Do not edit the memory note `release-notes-fixes-vs-last-tag`; it only says to judge against the last tag and stays
consistent with this.

## Tests

None: it is a skill document. Run the three commands at the repository root and confirm the second prints `4.6.0`
(or whichever numeric release tag is nearest at the time) and the third lists no `v`-prefixed tag.

## Manual check

Invoke the prepare-release skill on master and confirm its draft is built from `4.6.0..HEAD` (the commit count it
reports matches `git rev-list --count 4.6.0..HEAD`).
