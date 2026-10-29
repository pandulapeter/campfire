<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Keep an unattended review's work when it touches a workflow file the token cannot push

**Kind:** CI · **Severity:** low · **Platforms:** CI
**Files:** `.github/workflows/regression-review.yml`, `.github/CLAUDE.md` (its `regression-review.yml` section)
**Challenged:** amended — reverting the workflow paths in one extra commit does not make the push succeed: GitHub checks
the new commits a push brings, and the commit that changed the workflow is still among them. The guard now rebuilds the
branch from `START` without the workflow changes (each commit replayed with `.github/workflows` kept as it was),
checks that nothing else was lost, and keeps the original commits in a bundle artifact.

Land it from a normal checkout: the change is itself under `.github/workflows/`. Drop this plan if the workflow has
been deleted as the one-time job it was added as (its header and `.github/CLAUDE.md` say to delete it once its pull
request is settled).

## Problem

The workflow pushes with the checkout's `GITHUB_TOKEN` (`permissions: contents: write`, `pull-requests: write`,
`actions: write`). GitHub refuses a push from that token that creates or changes anything under `.github/workflows/`
("refusing to allow a GitHub App to create or update workflow … without `workflows` permission"), and no
`permissions:` key grants it. The check looks at the commits the push adds, not only the branch's final tree, so a
later commit undoing the change does not help. If the review commits a fix to a workflow, `git push origin "$BRANCH"`
fails, no pull request is opened, and every fix and plan is lost but for the transcript artifact. Nothing in the
prompt warns the agent (the 50th run was warned by its own reviewer and avoided it).

## Fix

Three parts, all in `regression-review.yml`:

1. The prompt gains an override line: never commit a change under `.github/workflows/`; the workflow's token cannot
   push it, so such a fix is a plan.
2. In "Push the branch and open the pull request", before `git push`, if
   `git diff --name-only "$START" HEAD -- .github/workflows` **or** any single commit in the range touches that folder
   (`git log --format=%h "$START..HEAD" -- .github/workflows` is not empty), rebuild the branch without those changes:

   ```bash
   ORIGINAL=$(git rev-parse HEAD)
   # A bundle needs a ref, not a bare commit: the branch, which still points at the original commits here.
   git bundle create "$RUNNER_TEMP/review.bundle" "$START..$BRANCH"
   echo "::warning::Left the workflow changes of $(git log --format=%h "$START..$ORIGINAL" -- .github/workflows | xargs) out of the pushed branch; the original commits are in the review-bundle artifact."
   git checkout --quiet --detach "$START"
   for commit in $(git rev-list --reverse --no-merges "$START..$ORIGINAL"); do
     # A conflict can only be in the workflow folder, which is put back right after.
     git cherry-pick --no-commit "$commit" > /dev/null 2>&1 || true
     git restore --source=HEAD --staged --worktree -- .github/workflows
     if git diff --cached --quiet; then
       git cherry-pick --quit 2> /dev/null || true
       continue
     fi
     # Already checked by the hook when the review made it; the hook needs Gradle, which is not this step's business.
     git commit --quiet --no-verify -C "$commit"
   done
   if ! git diff --quiet "$ORIGINAL" HEAD -- . ':(exclude).github/workflows'; then
     echo "::error::Rebuilding the branch without its workflow changes lost other changes; nothing was pushed. The commits are in the review-bundle artifact."
     exit 1
   fi
   git checkout --quiet -B "$BRANCH"
   ```

   Then the existing count check (`git rev-list --count "$START..HEAD"`, after the rebuild, since a run whose only
   commit touched a workflow leaves nothing), push, and PR as now; the fallback PR body's `git log` already reads
   `$START..HEAD`, which is now the rebuilt branch. `restore` deletes a workflow file the review added, since it is
   not in the source. The step's shell is `bash -e`, so keep the `|| true`s.
   (Checked locally on a scratch repository with a mixed commit, a workflow-only commit, a commit adding a new
   workflow, and a later commit conflicting on the same workflow file: the rebuilt branch has the other changes
   exactly, the workflow-only commit dropped.)
3. A step after it, `if: ${{ always() }}`, uploads `${{ runner.temp }}/review.bundle` as `review-bundle`
   (`actions/upload-artifact@v7`, `if-no-files-found: ignore`, `retention-days: 30`), so the workflow change itself
   can be fetched (`git fetch review.bundle`) and landed from a normal checkout.

Add one sentence to `.github/CLAUDE.md`'s `regression-review.yml` section: a workflow change the review commits is left
out of the pushed branch (the token cannot push it) and kept in the `review-bundle` artifact.

## Tests

None in CI; check the YAML parses (`python3 -c 'import yaml,sys; yaml.safe_load(open(sys.argv[1]))'`), `bash -n` the
extracted step, and run the snippet once in a scratch repository (with `START`, `BRANCH`, and `RUNNER_TEMP` set) holding a
mixed commit, a workflow-only commit, and a later commit touching the same workflow file.
