#!/usr/bin/env bash
# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.
#
# Prints a render's progress and the time it has left, every interval, until the run's log stops growing. The
# store-screenshots and website-screenshots skills run it under Monitor beside a render whose output goes to a log:
#
#   ./progress.sh <render log> [seconds between lines, 120 by default]
#
# The tool numbers each worker's shots on its own, "(2/4) [16/138]", so the run's count is the sum of every worker's
# latest number. The estimate is the pace since this script started (since the log appeared on its first line).
set -uo pipefail

log="$1"
interval="${2:-120}"

count() {
  # Prints "done total": per worker, the highest shot started and how many it has.
  grep -oE '^(\([0-9]+/[0-9]+\) )?\[[0-9]+/[0-9]+\]' "$log" 2>/dev/null | sed -E 's/^\(([0-9]+)\/[0-9]+\) /\1 /; s/^\[/0 [/; s/[][]//g; s/\// /' |
    awk '{ if ($2 > done[$1]) done[$1] = $2; total[$1] = $3 } END { d = 0; t = 0; for (w in total) { d += done[w]; t += total[w] }; print d, t }'
}

format() { printf '%dm %02ds' $(($1 / 60)) $(($1 % 60)); }

start=$(date +%s)
born=$(stat -f %B "$log" 2>/dev/null || stat -c %W "$log" 2>/dev/null || echo "$start")
read -r first _ < <(count)
last_size=-1
while true; do
  read -r done total < <(count)
  now=$(date +%s)
  size=$(wc -c <"$log" 2>/dev/null || echo 0)
  if [ "$total" -eq 0 ]; then
    echo "Building, nothing rendered yet ($(format $((now - born))) in)"
  else
    if [ "$now" -gt "$start" ] && [ "$done" -gt "$first" ]; then
      left=$(((total - done) * (now - start) / (done - first)))
    elif [ "$done" -gt 0 ] && [ "$now" -gt "$born" ]; then
      left=$(((total - done) * (now - born) / done))
    else
      left=-1
    fi
    eta=$([ "$left" -ge 0 ] && echo "about $(format "$left") left" || echo "no estimate yet")
    echo "$done/$total shots ($((100 * done / total))%), $(format $((now - born))) in, $eta"
  fi
  if grep -qE 'BUILD (SUCCESSFUL|FAILED)' "$log" 2>/dev/null; then
    echo "Finished: $(grep -oE 'BUILD (SUCCESSFUL|FAILED)' "$log" | tail -1), $(grep -c 'Exception' "$log") lines naming an exception"
    exit 0
  fi
  if [ "$size" -eq "$last_size" ]; then
    echo "The log has not grown in $(format "$interval"); the render may be stuck"
  fi
  last_size=$size
  sleep "$interval"
done
