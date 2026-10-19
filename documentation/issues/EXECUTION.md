<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->

# Executing the 39th review's plans

One plan, one lane: no worktree or subagent is needed. Run it in the main checkout.

1. **Preconditions** — stop and tell the user if any fails: `git status` is clean apart from `documentation/issues/`;
   plan 01 carries a `**Challenged:**` line; `./gradlew :chordpro:desktopTest` passes. Commit the plans first
   (`git add documentation/issues && git commit -m "Add the 39th review plans."`), then note `START=$(git rev-parse HEAD)`.
2. **Plan 01** — read it and the files it names; load `code-style`; implement the recommended option (as amended by
   the challenge); add its tests; keep `respelledStaffNames`' KDoc true. If its premise no longer holds, revert and
   stop with the reason.
3. **Verify** — `./gradlew :chordpro:desktopTest :chordpro:compileKotlinWasmJs :chordpro:compileKotlinIosSimulatorArm64
   :presentation:desktopTest`; read the failure count from the XML under `build/test-results`.
4. **Commit** — `git rm` the plan file in the same commit; load `commit-messages`; one `-m` sentence, e.g. `Keep a
   tab's B string a B natural when a song is edited in German notation.` Then:

   ```
   test "$(git cat-file commit HEAD | sed '1,/^$/d' | wc -l | tr -d ' ')" = "1" || echo "MORE THAN ONE LINE"
   git log -1 --format=%B | grep -qiE 'co-authored|generated with|claude code|anthropic\.com' && echo "ATTRIBUTION"
   ```
5. **Finish** — delete `documentation/issues/` and commit `Remove the review plans.`; `git status` clean. Update the
   39th review memory. **Do not push.**
