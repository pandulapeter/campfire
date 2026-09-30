# Say when a store script fix reaches a tagged release, and stop the Mac badge pointing at the releases page

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** CI, README
**Files:** `CLAUDE.md` (root, Build section around line 351), `.github/workflows/publish-windows.yml`,
`.github/workflows/publish-ios.yml`, `.github/workflows/publish-macos.yml` (the step comments), `README.md` (lines 36–38)

## Problem

Two claims in the docs are not what the code does.

**The scripts.** The root `CLAUDE.md` (at 9ab7ca54e, line 351) says:

> What they build is the tag's, but the store scripts in `.github/scripts` come from the workflow's own commit, so a
> fix to one reaches a release already tagged.

and the three store workflows carry the same promise above the step that does it (`publish-windows.yml` lines 101–108,
the iOS and macOS ones alike):

```yaml
      # The tag decides what is built, but talking to the store is the workflow's business: a fix to these scripts has to
      # reach a release that is already tagged, and the one run by hand to repeat it, rather than wait for the next tag.
      - name: Take the store scripts from the workflow's commit
        env:
          WORKFLOW_SHA: ${{ github.workflow_sha }}
        run: |
          git fetch --depth 1 origin "$WORKFLOW_SHA"
          git checkout FETCH_HEAD -- .github/scripts
```

For the `release` event that `publish-all.yml` answers, GitHub runs the workflow file of the tag's commit, and a
called workflow's `github.workflow_sha` is the caller's, so `WORKFLOW_SHA` *is* the tag's commit: a script fixed on
`master` after the tag does not reach the release-triggered run, nor a "re-run failed jobs" of it (same SHA). It
reaches only a `workflow_dispatch` started from `master` with `release_tag` set — which is the case the second half of
the comment describes, and a real one, but not the first. Scenario: the Windows submission fails on the release, the
script is fixed on `master`, the user re-runs the failed job from the run's page and gets the old script.

**The badge.** `README.md` lines 36–38:

```html
macOS is coming really soon (under final review):

<a href="https://github.com/pandulapeter/campfire/releases/latest"><img src="documentation/images/badge_macos.png" alt="Campfire for macOS" height="32px" /></a>
```

The root `CLAUDE.md` says nothing for the Mac is ever attached to a release (the Mac App Store build is the Mac
build), so the badge is a dead end until the listing exists.

## Fix

Reword rather than change the mechanism: a release run should stay reproducible from its tag, and the hand dispatch
is the documented way to repeat half a release. In the root `CLAUDE.md`, make it "the store scripts in
`.github/scripts` come from the commit the workflow itself runs from — the tag's on a release, and the branch's when
the workflow is dispatched by hand with `release_tag` set, which is how a fix to one reaches a release already tagged
without a new tag". Rewrite the step comment in the three workflows to the same effect ("a fix to these scripts
reaches a release that is already tagged when the workflow is dispatched by hand from the branch that has the fix").

For the badge, make it plain text until the listing exists: drop the `<a>` around the image (keep the image and the
sentence), or point it at `https://campfire-songbook.com/#download`, which the root `CLAUDE.md` names as the page
that can say where every version is. Prefer the website link, so that the badge is never a dead end again. When the
listing is approved, `Distribution.listingUrl` and this badge are filled in together (see the memory note on the Mac
App Store launch).

## Tests

None; documentation only.

## Manual check

None.
