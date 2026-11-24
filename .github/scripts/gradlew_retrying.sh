# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

# Runs ./gradlew with the given arguments, and runs it again when it failed on a download, for every workflow. Maven
# Central answers the runners' shared addresses with 429 Too Many Requests now and then, and Gradle retries only 5xx
# answers, then disables the repository for the rest of the build. A failure whose output names no download error (a
# compilation, a test) is not retried. Stdout and stderr come out merged.
#
#     bash .github/scripts/gradlew_retrying.sh ARGUMENTS ...
#
# Written for the bash 3.2 macOS ships and for Git Bash alike.

ATTEMPTS=3
NETWORK_ERROR="Could not (GET|HEAD) '|Received status code (408|429|5[0-9][0-9])|Read timed out|Connect timed out|Connection reset"
LOG="$(mktemp)"
trap 'rm -f "$LOG"' EXIT

attempt=1
while true; do
    ./gradlew "$@" 2>&1 | tee "$LOG"
    status=${PIPESTATUS[0]}
    if [ "$status" -eq 0 ]; then
        exit 0
    fi
    if [ "$attempt" -ge "$ATTEMPTS" ] || ! grep -qE "$NETWORK_ERROR" "$LOG"; then
        exit "$status"
    fi
    delay=$((attempt * 60))
    echo "::warning::Gradle failed on a download (attempt $attempt of $ATTEMPTS); trying again in ${delay}s."
    sleep "$delay"
    attempt=$((attempt + 1))
done
