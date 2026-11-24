# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Names the legs of a matrix that never got a runner, so that a workflow can run them again.

GitHub gives up on a job it finds no hosted runner for after a quarter of an hour ("The job was not acquired by Runner
of type hosted even after multiple attempts") and cancels it without a step run: macOS runners have run short often
enough to cost releases their screenshots, and once the iOS compile and with it every store build. Such a leg is worth
a second try; one that ran and failed is not, so a single leg that failed any other way (or is missing) retries
nothing, and the run fails as it would have.

The plan is the matrix's `include` list, each entry with the `title` its job is named by; the jobs are the run
attempt's as the API lists them (`gh api --paginate .../attempts/N/jobs --jq '.jobs[]'`, one object a line), where a
job of a called workflow is named "<caller's job> / <title>". Prints `include=<the entries to run again>` for
$GITHUB_OUTPUT, `[]` when there are none.

    unacquired_jobs.py --plan '<json>' --jobs jobs.jsonl
"""

import argparse
import json
import sys


def find_job(jobs, title):
    matches = [job for job in jobs if job.get("name") == title or job.get("name", "").endswith(f" / {title}")]
    return matches[-1] if matches else None


def never_started(job):
    return job.get("conclusion") == "cancelled" and not job.get("runner_name") and not job.get("steps")


def entries_to_retry(plan, jobs):
    """Returns the entries to run again and the reason, when there is one, that none are."""
    retry = []
    for entry in plan:
        job = find_job(jobs, entry["title"])
        if job is None:
            return [], f"{entry['title']} is not among the run's jobs."
        if job.get("conclusion") == "success":
            continue
        if not never_started(job):
            return [], f"{entry['title']} ran and ended {job.get('conclusion')}."
        retry.append(entry)
    return retry, None


def main(arguments):
    parser = argparse.ArgumentParser()
    parser.add_argument("--plan", required=True)
    parser.add_argument("--jobs", required=True)
    options = parser.parse_args(arguments)
    plan = json.loads(options.plan)
    with open(options.jobs, encoding="utf-8") as file:
        jobs = [json.loads(line) for line in file if line.strip()]
    retry, reason = entries_to_retry(plan, jobs)
    if reason:
        print(f"Nothing is run again: {reason}", file=sys.stderr)
    for entry in retry:
        print(f"::warning::{entry['title']} got no runner; it runs again.", file=sys.stderr)
    print(f"include={json.dumps(retry, separators=(',', ':'))}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
