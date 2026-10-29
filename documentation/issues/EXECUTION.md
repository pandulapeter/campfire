<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Executing the 50th review plans

Every lane has landed; only plan 12 is left, since it changes `.github/workflows/`, which the unattended workflow's
token cannot push. From a normal checkout with a clean tree: read the plan and `.github/CLAUDE.md`, implement it,
check that the YAML parses (`python3 -c 'import yaml,sys; yaml.safe_load(open(sys.argv[1]))' <file>`) and the plan's
own check, and `git rm` the plan in the same one-line commit (the `commit-messages` skill). Then delete this folder and
commit `Remove the review plans.` **Do not push.**
