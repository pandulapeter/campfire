<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Attach the Linux .deb before the AppImage is built, so an AppImage failure withholds only the AppImage

**Kind:** release pipeline · **Severity:** medium · **Platforms:** Linux release (amd64, arm64)
**Files:** `.github/workflows/publish-linux.yml`, `.github/CLAUDE.md` (only if its AppImage line changes)
**Challenged:** amended — a second Gradle call for `packageReleaseAppImage` would not "only package": the task
depends on `recordClassDataArchive`, which is `doNotTrackState` (always reruns, and needs a display) and edits the app
image in place, which in turn makes `createReleaseDistributable` out of date and rebuilt; the AppImage would then hold a
retrained image other than the one the .deb holds and the start check ran. The AppImage call now excludes
`recordClassDataArchive` (and with it its dependencies) with `-x`.

A change under `.github/workflows/` cannot be pushed by the unattended review workflow's `GITHUB_TOKEN` (plan 13):
land it from a normal checkout.

## Problem

`.github/CLAUDE.md` says the AppImage's "failure withholds only the AppImage". But `publish-linux.yml` downloads
appimagetool and the runtime (`curl --fail` and `sha256sum --check`) in a step before the build, and builds both packages
in one Gradle call with no `--continue`:

```yaml
SKIKO_RENDER_API=SOFTWARE_FAST xvfb-run --auto-servernum ./gradlew :app:desktop:createReleaseDistributable \
  :app:desktop:packageReleaseDeb :app:desktop:packageReleaseAppImage \
```

A GitHub download hiccup, a moved pinned release, a changed digest, or an appimagetool error fails the job before
"Attach the .deb". At cfcf08150 the .deb depended on none of these.

## Fix

Reorder the job:

1. Unchanged: checkout, scripts from the workflow's commit, JDK, the secrets check, "Install packaging tools" (xvfb is
   still needed by the first Gradle call and both start checks).
2. **Build the .deb**: `write_local_properties.py` as now, then
   `SKIKO_RENDER_API=SOFTWARE_FAST xvfb-run --auto-servernum ./gradlew :app:desktop:createReleaseDistributable :app:desktop:packageReleaseDeb`
   (no `-Pcampfire.linux.appImage…`: their defaults in `gradle.properties` are empty, and `packageReleaseAppImage` is
   registered lazily, so nothing reads them), then `./gradlew --stop`. This call still records the class data sharing
   archive into the release app image (`packageReleaseDeb` depends on `recordClassDataArchive`), which is why it keeps
   `xvfb-run`. Keep the comment that is above "Build the packages" today, reworded for the .deb.
3. "Start the release build once" and "Attach the .deb to the GitHub release", unchanged.
4. "Download appimagetool and the AppImage runtime", moved here unchanged, with its comment.
5. **Build the AppImage**:
   `./gradlew :app:desktop:packageReleaseAppImage -x :app:desktop:recordClassDataArchive "-Pcampfire.linux.appImageTool=$TOOLS/appimagetool" "-Pcampfire.linux.appImageRuntime=$TOOLS/runtime"`
   then `./gradlew --stop`. The `-x` is required: `recordClassDataArchive` is `doNotTrackState("Edits the release app
   image in place")`, so it would rerun (needing a display), and its edit makes `createReleaseDistributable` out of date,
   rebuilding and retraining the image; excluded, Gradle also drops `createReleaseDistributable` and ProGuard (needed by
   nothing else requested), and the AppImage packs exactly the image the .deb holds and the start check ran.
   `PackageAppImage` takes the image directory as a path property, so nothing re-adds the dependency. No display is
   needed (it only copies and runs appimagetool). The write of `local.properties` from step 2 is still on disk.
   Comment the `-x` in the step.
6. "Start the AppImage once" and "Attach the AppImage", unchanged; reword the first's comment ("after the `.deb` is
   attached, so a failure here…") to cover the download and the build too.

Do not mark the AppImage steps `continue-on-error`: a failed AppImage should still turn the run red, it just must not
withhold the .deb. `.github/CLAUDE.md`'s line "after the `.deb` is attached, so its failure withholds only the AppImage"
becomes true; extend it to say the AppImage's tools are downloaded and its package built after the attach too. The
file's top comment and the matrix stay as they are.

## Tests

None run locally; check the YAML with `python3 -c 'import yaml,sys; yaml.safe_load(open(sys.argv[1]))'` and read the
step order once more against `.github/CLAUDE.md`.

## Manual check

The next release (or a `workflow_dispatch` of `publish-linux.yml` against a draft release) attaches the .deb before the
AppImage steps run.
