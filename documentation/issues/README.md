<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# The 50th review: a regression review since the 49th

Reviewed commit **54985be95** (branch `review/50th-regression-40901553239`), range `cfcf08150..54985be95`: the 49th
review's fixes and plans, the Hebrew translation and right-to-left layout, the sheet music work, the screenshot tool
rewrite, the AppImage, and the store listings pipeline, 98 commits. **Angle:** did these commits break anything that
worked at cfcf08150 (callers, documented usages, other platforms, other languages, both reading directions,
persistence and sync formats), and are the 49th review's landed fixes right? Run unattended in GitHub Actions: five
area reviewers (`:chordpro` and document import; song view, print, notation, and `abc`; right-to-left layout and
strings; the editor and other screens; platform shells, CI, and tools), the docs check, a check of the review's own
fixes, and two challengers.

## Left for a normal checkout

Every other plan of this review landed (`8ad409304..HEAD`, run unattended, every decision taking its recommended
default). Plan 12 changes `.github/workflows/`, which the unattended workflow's `GITHUB_TOKEN` cannot push: land it
from a normal checkout, one commit, then delete this folder (`Remove the review plans.`).

| # | Plan | Severity |
|---|---|---|
| 12 | Attach the Linux .deb before the AppImage is built | medium |

Files owned: `.github/workflows/publish-linux.yml`, `.github/CLAUDE.md`. Verify: the YAML parses
(`python3 -c 'import yaml,sys; yaml.safe_load(open(sys.argv[1]))' <file>`) and the plan's own check.
