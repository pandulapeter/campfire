<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->
# 18 — The Linux `.deb` cannot be installed on Ubuntu 22.04, Mint 21 or Debian 12

## What goes wrong at release time

The Linux package is the whole of how Campfire is handed out on Linux — no store carries it, and the "Every version
of Campfire" row in Settings leads to the release page it is attached to. It is built on `ubuntu-24.04`, and Ubuntu
24.04 is the release that completed the 64-bit `time_t` transition: the system libraries jpackage records as
dependencies are named `libc6`, `libgcc-s1`, `libstdc++6` **and** the renamed `t64` packages there. A `.deb` built
against them asks, on install, for packages that do not exist on any distribution released before the transition:

```
dpkg: dependency problems prevent configuration of campfire:
 campfire depends on libgtk-3-0t64; however:
  Package libgtk-3-0t64 is not installed.
```

Ubuntu 22.04 LTS (supported until 2027), Linux Mint 21, Debian 12 "bookworm" and everything derived from them fail
this way. The user's only way out is `--force-depends`, which is not something to ask of somebody who downloaded a
songbook. Nothing in CI notices: the workflow's smoke test starts the **app image**, not the installed package, so a
`.deb` nobody can install is attached to the release and looks like a success.

## Cause

`.github/workflows/desktop-publish.yml:46-60`, verified at HEAD `984861e4`:

```yaml
      matrix:
        include:
          # Pinned rather than "latest", since a .deb depends on the versions of the system libraries it was built
          # against, and a runner that moves to a newer Ubuntu on its own would raise that bar for everyone who
          # installs it.
          - name: Linux (amd64)
            runner: ubuntu-24.04
            task: packageReleaseDeb
            format: deb
            asset: linux-amd64.deb
          - name: Linux (arm64)
            runner: ubuntu-24.04-arm
            task: packageReleaseDeb
            format: deb
            asset: linux-arm64.deb
```

The comment already states the rule the pin exists for — a `.deb` raises the bar to whatever it was built against —
and then pins to the newest Ubuntu available rather than to the oldest one still worth supporting, which is what the
rule actually asks for.

## The change

`.github/workflows/desktop-publish.yml`: build both Linux legs on the 22.04 runners, and say in the comment what the
pin is *for*, so that the next person to touch it does not "update" it.

```yaml
      matrix:
        include:
          # Pinned to the oldest Ubuntu still worth supporting rather than to the newest one available: a .deb asks
          # for the system libraries it was built against, so the runner decides the lowest distribution the package
          # can be installed on. 24.04 completed the 64-bit time_t transition and renamed those libraries, so a
          # package built there asks for libgtk-3-0t64 and friends and is uninstallable on 22.04, Mint 21 and Debian
          # 12 alike. Moving this forward drops every distribution below the new runner's, so it is a decision rather
          # than an upgrade - and GitHub retires a runner image eventually, which is when it has to be made.
          - name: Linux (amd64)
            runner: ubuntu-22.04
            task: packageReleaseDeb
            format: deb
            asset: linux-amd64.deb
          - name: Linux (arm64)
            runner: ubuntu-22.04-arm
            task: packageReleaseDeb
            format: deb
            asset: linux-arm64.deb
```

(If plan 15 lands first, each of the two entries also carries `distribution: linux`; the two changes touch different
lines of the same block.)

Nothing else in the job needs to move. The `Install packaging tools` step
(`desktop-publish.yml:98-100`) installs `fakeroot xvfb libegl1 libgl1`, all of which exist on 22.04 under those
names, and JDK 21 comes from `actions/setup-java` rather than from the image.

**Note for whoever runs this:** `ubuntu-22.04-arm` is a public preview image on GitHub-hosted runners and, like every
pinned image, is retired some years after the release goes out of standard support. When it is, this is a decision to
be taken again, not a build to be fixed in a hurry: moving to 24.04 drops every pre-`t64` distribution, and the
alternative is building the `.deb` inside a `container: ubuntu:22.04` (or a Debian 12 image), which pins the build
environment independently of the runner image and would survive the retirement. Say so in the comment as above rather
than in a separate file.

## Tests

None; this is workflow configuration. The project's unit suite is unaffected:

```bash
./gradlew :chordpro:desktopTest :domain:implementation:desktopTest :data:source:local:implementation:desktopTest :data:source:remote:api:desktopTest :data:source:remote:implementation:desktopTest :data:repository:implementation:desktopTest
```

## Verification

**This change can only be proven by dispatching the workflow.** Nothing on a Mac reproduces it: jpackage packages for
the machine it runs on, so a `.deb` cannot be built locally from macOS at all.

1. Dispatch `Publish Desktop` by hand against an existing release tag (Actions → Publish Desktop → Run workflow →
   `release_tag`). The two Linux legs must be green and must attach `campfire-<version>-linux-amd64.deb` and
   `…-linux-arm64.deb`.
2. Download the amd64 asset and read the dependencies it declares — this is the check:

   ```bash
   dpkg-deb --field campfire-<version>-linux-amd64.deb Depends
   ```

   No name in the output may end in `t64`. Before the change the same command prints `libgtk-3-0t64`,
   `libglib2.0-0t64`, `libgcc-s1`, `libc6` and so on; after it, the pre-transition names.
3. Install it somewhere that has the problem today, which is the only honest proof:

   ```bash
   docker run --rm -it -v "$PWD:/pkg" ubuntu:22.04 bash -c \
     'apt-get update && apt-get install -y /pkg/campfire-*-linux-amd64.deb && echo INSTALLED'
   ```

   It must reach `INSTALLED` with no `--force-depends`.
4. Optionally, start it on a real 22.04 desktop (or Mint 21) and confirm the window opens and the demo library is
   written — the workflow's own smoke test already covers the app image, so this only adds the packaged launcher.

Needs no Mac, no Windows PC, no store account and no signing key; it does need write access to dispatch a workflow
and, for step 3, Docker or a 22.04 machine.

## Docs

Root `CLAUDE.md`, the `desktop-publish.yml` bullet. The sentence about the Linux legs does not name a runner, so it
stays true as it is:

> `packageReleaseDeb` on amd64 and arm64, which is the whole of how the Linux build is handed out (`Distribution.LINUX`
> links to the latest release's page, and a `.deb` is not something anybody signs on its own)

Add one clause to it, since "the whole of how the Linux build is handed out" is precisely why the floor matters:

> …, built on the oldest supported Ubuntu rather than the newest, since a `.deb` asks for the system libraries it was
> built against and the runner therefore decides the lowest distribution it installs on

(If plan 16 has landed, `Distribution.LINUX` no longer exists; that bullet's parenthesis becomes "the README's
'Get Campfire' section is where it is linked from". Keep whichever is true when this lands.)

`app/desktop/CLAUDE.md` needs no change: it documents the local packaging tasks, not the runners.

## Files touched

- `.github/workflows/desktop-publish.yml`
- `CLAUDE.md`

## Depends on

Nothing. Touches the same matrix block as plan 15, so land them in either order but expect a small merge.

## Rules

- Load the `code-style` skill before the first edit; it governs the comment voice in YAML as much as in Kotlin —
  prose, full sentences, the reason rather than the restatement, and no archaeology.
- Everything configurable stays a `campfire.*` Gradle property; nothing here is one.
