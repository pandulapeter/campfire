<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->
# 19 — An empty `DROPBOX_APP_KEY` silently ships a build with no sync at all

## What goes wrong at release time

If the `DROPBOX_APP_KEY` secret is missing, empty, or not visible to a workflow — a fork, a renamed secret, an
environment the job does not have access to, a hand-dispatched run in a repository where it was never set — every one
of the four publishing workflows builds, passes, and publishes a Campfire that offers **no cloud provider at all**.
Sync is not degraded, it is gone: the settings screen says the build has no sync credentials, as it is meant to for a
developer's checkout, and that is the version that goes to Play, to the website and onto the release.

Nothing fails. The empty string is a legal value — it is the checked-in default, and it has to be, or a fresh clone
could not build. The four workflows pass it through without looking at it, and the Gradle task writes it into the
generated source without looking at it either. The first person to notice is a user whose sync stopped existing after
an update.

The root `CLAUDE.md` already states the requirement the pipeline does not enforce: "Every build passes
`campfire.dropbox.appKey` from the `DROPBOX_APP_KEY` secret, because a published app built without it would quietly
have no sync provider at all."

## Cause

The key travels four different ways and is checked on none of them. Verified at HEAD `984861e4`:

`.github/workflows/android-publish.yml:125-132`:

```yaml
      - name: Build release APK
        run: |
          ./gradlew :app:android:assembleRelease \
            -Pcampfire.dropbox.appKey="${{ secrets.DROPBOX_APP_KEY }}" \
```

`.github/workflows/desktop-publish.yml:106-110`:

```yaml
      - name: Build the installer
        run: |
          ./gradlew :app:desktop:createReleaseDistributable :app:desktop:${{ matrix.task }} \
            -Pcampfire.dropbox.appKey="${{ secrets.DROPBOX_APP_KEY }}"
```

`.github/workflows/web-publish.yml:49-52`:

```yaml
      - name: Build web distribution
        run: |
          ./gradlew :app:web:wasmJsBrowserDistribution \
            -Pcampfire.dropbox.appKey="${{ secrets.DROPBOX_APP_KEY }}"
```

`.github/workflows/ios-publish.yml:51-54`:

```yaml
      - name: Write local.properties
        env:
          DROPBOX_APP_KEY: ${{ secrets.DROPBOX_APP_KEY }}
        run: echo "campfire.dropbox.appKey=$DROPBOX_APP_KEY" > local.properties
```

and the generator, `data/source/remote/implementation/build.gradle.kts:25-36`, which writes whatever it is given:

```kotlin
val generateSyncConfiguration = tasks.register("generateSyncConfiguration") {
    val appKey = project.property("campfire.dropbox.appKey").toString()
    …
                internal const val DROPBOX_APP_KEY = "$appKey"
```

## The change

A check in the workflows, not in Gradle. Two places: once in `release.yml`, so a release fails before any of the four
builds starts, and once in each publishing workflow, so a hand-dispatched run is covered too.

### 1. `release.yml` — one failure instead of four

In the `prepare` job, after `Check the tag against the version`:

```yaml
      # A published build with an empty key has no sync provider at all, and nothing downstream would notice: the
      # empty string is the checked-in default, because a fresh clone has to build without a secret. Asked here as
      # well as in each workflow, so a release fails before four builds have run rather than after.
      - name: Check the sync credentials
        env:
          DROPBOX_APP_KEY: ${{ secrets.DROPBOX_APP_KEY }}
        run: |
          if [ -z "$DROPBOX_APP_KEY" ]; then
            echo "::error::DROPBOX_APP_KEY is empty or not visible to this workflow. A release built without it would have no sync provider at all." >&2
            exit 1
          fi
```

### 2. The same step in each of the four publishing workflows

Identical block, placed immediately after `Set up JDK 21` in `android-publish.yml`, `desktop-publish.yml` and
`web-publish.yml`, and in `ios-publish.yml` folded into the existing `Write local.properties` step, which is already
shaped for it:

```yaml
      - name: Write local.properties
        env:
          DROPBOX_APP_KEY: ${{ secrets.DROPBOX_APP_KEY }}
        run: |
          if [ -z "$DROPBOX_APP_KEY" ]; then
            echo "::error::DROPBOX_APP_KEY is empty or not visible to this workflow. The published app would have no sync provider at all." >&2
            exit 1
          fi
          printf 'campfire.dropbox.appKey=%s\n' "$DROPBOX_APP_KEY" > local.properties
```

The comment above the step in `ios-publish.yml` stays as it is; add the one-sentence reason for the check, worded as
in `release.yml`.

In `desktop-publish.yml` the check belongs in each matrix leg rather than in a job of its own — five jobs each
failing in two seconds is cheaper to read than a sixth job to coordinate, and `fail-fast: false` means the legs do
not cancel each other anyway.

If plan 20 lands first, these workflows already write `local.properties` from `env:`; then the check goes into that
same step, exactly as `ios-publish.yml` does above.

### What is deliberately not done

**The Gradle task is left alone.** It would be possible to add a `campfire.dropbox.appKeyRequired` property that
defaults to `false` and is passed as `true` by the four workflows, and to fail `generateSyncConfiguration` when the
key is empty and it is set. It is rejected for three reasons:

- A local debug build, a fresh clone and every developer machine without a `local.properties` **must** keep building
  with an empty key. That is the whole design the root `CLAUDE.md` describes, and any Gradle-side rule has to carve
  it out, which means a second property whose only job is to say "this build is a real one".
- The iOS build does not pass properties at all: the Xcode project starts Gradle itself, so the flag would have to be
  written into `local.properties` next to the key, where the key already is — at which point the shell check next to
  it is simpler and earlier.
- A Gradle check fails after the toolchain has been set up and the configuration phase reached; the shell check fails
  in the second step of the job, before anything has been downloaded.

Neither form catches a key that is present but **wrong** — that only a real authorization attempt shows, and the
manual check after a release (connect Dropbox in Settings from a store build) remains the way to know.

## Tests

None; this is workflow configuration. Run the standard suite to confirm nothing else moved:

```bash
./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
```

## Verification

The YAML itself can be checked locally:

```bash
python3 -c 'import sys,yaml;[yaml.safe_load(open(f)) for f in sys.argv[1:]]' .github/workflows/*.yml
```

The behavior **can only be proven by dispatching the workflows**, and only in a repository where the secret can be
taken away:

1. In a fork with no `DROPBOX_APP_KEY` secret, dispatch each of `Publish Android`, `Publish Desktop`, `Publish Web`
   and `Publish iOS`. Each must fail within seconds at `Check the sync credentials` (or `Write local.properties`)
   with the `::error::` line in the run's summary, and must not reach a Gradle invocation.
2. In the real repository, dispatch `Publish Web` (the cheapest of the four, and it publishes only to the website's
   `campfire/` folder): the check passes and the run proceeds as before. This is the regression half — a check that
   fails a healthy release is worse than no check.
3. The `release.yml` half can only be seen by publishing a real GitHub release, so take it on the two above: the step
   is the same script against the same secret.

Needs no Mac (except for the iOS leg, which runs on GitHub's macOS runner), no store account and no signing key.

## Docs

Root `CLAUDE.md`, the Build section. This sentence describes the requirement and can now say that it is enforced:

> Every build passes `campfire.dropbox.appKey` from the `DROPBOX_APP_KEY` secret, because a published app built
> without it would quietly have no sync provider at all.

becomes

> Every build passes `campfire.dropbox.appKey` from the `DROPBOX_APP_KEY` secret, because a published app built
> without it would quietly have no sync provider at all — so each workflow, and `release.yml` before it calls any of
> them, refuses to start when that secret is empty. The check is in the workflows rather than in Gradle: an empty key
> is the checked-in default and has to keep building a fresh clone.

No module `CLAUDE.md` changes — `data/source/remote/implementation` is untouched.

## Files touched

- `.github/workflows/release.yml`
- `.github/workflows/android-publish.yml`
- `.github/workflows/desktop-publish.yml`
- `.github/workflows/web-publish.yml`
- `.github/workflows/ios-publish.yml`
- `CLAUDE.md`

## Depends on

Nothing, but it edits the same steps as plan 20 in three of the four workflows. Landing 20 first makes this smaller,
since the `local.properties` step it introduces is where the check belongs.

## Rules

- Load the `code-style` skill before the first edit; the workflow comments follow the same voice as the code.
- Secrets are read through `env:`, never interpolated into a command (see plan 20) — the new steps are written that
  way from the start.
- Everything configurable stays a `campfire.*` Gradle property read with `project.property`; nothing new is added
  here.
