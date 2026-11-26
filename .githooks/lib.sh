# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

# What pre-commit and verify share, sourced by both from the folder they run from (core.hooksPath names the main
# checkout's .githooks, so a worktree branched before a change here still runs this copy). Each function runs at the top
# of the checkout being committed in, whose own scripts and Gradle wrapper it uses.

# The checks tests.yml runs that take seconds.
fast_checks() {
    python3 .github/scripts/check_license_headers.py
    python3 .github/scripts/check_sizes.py
    python3 .github/scripts/check_layers.py
    python3 .github/scripts/check_sync_seams.py
    python3 .github/scripts/check_store_listings.py
    python3 .github/scripts/store_listings.py --check
    python3 .github/scripts/check_doc_names.py --staged
    python3 .github/scripts/check_regexes.py
    python3 .github/scripts/check_test_names.py
    # The IDE drops the shared iOS run configuration now and then, and a commit staging everything has carried that
    # deletion along several times.
    if git diff --cached --name-only --diff-filter=D | grep -qx '.run/iOS.run.xml'; then
        echo "The commit deletes .run/iOS.run.xml; restore it with git restore --staged --worktree .run/iOS.run.xml." >&2
        return 1
    fi
}

# The staged files Gradle has to check: anything but Markdown, and nothing for a version bump alone, which changes no
# code or test, yet invalidates the configuration cache and the generated version constants, which reran every test
# downstream of them.
staged_code() {
    code=$(git diff --cached --name-only --diff-filter=ACMRD | grep -vE '\.md$' || true)
    if [ "$code" = "gradle.properties" ] && ! git diff --cached -U0 -- gradle.properties |
        grep -E '^[+-]' | grep -vE '^(\+\+\+|---) ' | grep -qvE '^[+-]campfire\.(versionName|buildNumber)='; then
        code=""
    fi
    echo "$code"
}

# Where verify records the staged trees it checked, shared by every worktree of the clone.
verified_trees() {
    echo "$(git rev-parse --path-format=absolute --git-common-dir)/verified-trees"
}

# The Gradle step, run once one of GRADLE_SLOTS slots is free, shared by every worktree of the clone. Each build of a
# checkout of its own starts a daemon of its own (6 GB, gradle.properties), so a session per worktree would otherwise run
# as many at once as there are sessions committing; the rest wait here, holding no git lock. A slot whose owner died is
# taken over.
GRADLE_SLOTS=2
gradle_queued() {
    slots="$(git rev-parse --path-format=absolute --git-common-dir)/gradle-slots"
    mkdir -p "$slots"
    announced=""
    while :; do
        n=1
        while [ "$n" -le "$GRADLE_SLOTS" ]; do
            slot="$slots/$n"
            if mkdir "$slot" 2>/dev/null; then
                echo $$ > "$slot/pid"
                trap 'rm -rf "$slot"' EXIT
                trap 'rm -rf "$slot"; exit 130' INT TERM
                if ./gradlew "$@"; then status=0; else status=$?; fi
                rm -rf "$slot"
                trap - EXIT INT TERM
                return $status
            fi
            owner=$(cat "$slot/pid" 2>/dev/null || true)
            if [ -n "$owner" ] && ! kill -0 "$owner" 2>/dev/null; then
                rm -rf "$slot"
                continue
            fi
            n=$((n + 1))
        done
        if [ -z "$announced" ]; then
            echo "Waiting for a Gradle slot: $GRADLE_SLOTS builds of this clone are running." >&2
            announced=1
        fi
        sleep 3
    done
}

# The UI tests (tools/screenshots) of the packages the given files are in, as `--tests` arguments for
# :tools:screenshots:test: a UI test sits in the package of the screen it drives, so a change to a screen's package runs
# the tests of that package (not of the packages under it), and a change to a UI test or its helpers runs its package's.
# The whole suite takes minutes and stays in CI; these few are what a change to a screen most often breaks unnoticed.
ui_test_args() {
    root=tools/screenshots/src/test/kotlin
    dirs=$(echo "$1" | sed -nE "s#^presentation/src/[^/]+/kotlin/(.+)/[^/]+\.kt\$#\1#p; s#^$root/(.+)/[^/]+\.kt\$#\1#p" | sort -u)
    for dir in $dirs; do
        for test in "$root/$dir"/*Test.kt; do
            [ -e "$test" ] || continue
            class=${test#"$root/"}
            printf ' --tests %s' "$(echo "${class%.kt}" | tr / .)"
        done
    done
}

# Spotless, the unit tests, and the UI tests of the packages the given files change, in one queued Gradle run.
gradle_checks() {
    tests=$(ui_test_args "$1")
    if [ -n "$tests" ]; then
        echo "Running the UI tests of the changed packages:$(echo "$tests" | sed 's/ --tests [^ ]*\./ /g')"
    fi
    # shellcheck disable=SC2086 # the test filters are one word each, split on purpose
    gradle_queued spotlessCheck desktopTest ${tests:+:tools:screenshots:test} $tests --quiet
}
