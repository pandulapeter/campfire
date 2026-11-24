# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

import unittest

from unacquired_jobs import entries_to_retry

PLAN = [
    {"title": "Render Apple (0/2)", "runner": "macos-latest"},
    {"title": "Render Apple (1/2)", "runner": "macos-latest"},
]


def job(name, conclusion, runner_name="GitHub Actions 1000004398", steps=({"name": "Set up job"},)):
    return {"name": name, "conclusion": conclusion, "runner_name": runner_name, "steps": list(steps)}


def unacquired(name):
    return job(name, "cancelled", runner_name=None, steps=())


class UnacquiredJobsTest(unittest.TestCase):

    def test_a_leg_that_never_got_a_runner_runs_again(self):
        jobs = [job("Screenshots / Render Apple (0/2)", "success"), unacquired("Screenshots / Render Apple (1/2)")]
        self.assertEqual(entries_to_retry(PLAN, jobs), ([PLAN[1]], None))

    def test_a_dispatched_run_names_its_jobs_without_the_caller(self):
        jobs = [unacquired("Render Apple (0/2)"), unacquired("Render Apple (1/2)")]
        self.assertEqual(entries_to_retry(PLAN, jobs), (PLAN, None))

    def test_a_leg_that_ran_and_failed_retries_nothing(self):
        jobs = [unacquired("Render Apple (0/2)"), job("Render Apple (1/2)", "failure")]
        self.assertEqual(entries_to_retry(PLAN, jobs), ([], "Render Apple (1/2) ran and ended failure."))

    def test_a_leg_cancelled_after_it_started_retries_nothing(self):
        jobs = [unacquired("Render Apple (0/2)"), job("Render Apple (1/2)", "cancelled")]
        self.assertEqual(entries_to_retry(PLAN, jobs)[0], [])

    def test_a_missing_leg_retries_nothing(self):
        self.assertEqual(entries_to_retry(PLAN, [unacquired("Render Apple (0/2)")])[0], [])

    def test_a_longer_title_with_the_same_ending_is_another_job(self):
        jobs = [job("Render Apple (0/2)", "success"), job("Render Apple (1/2)", "success"),
                unacquired("Screenshots / Re-Render Apple (1/2)")]
        self.assertEqual(entries_to_retry(PLAN, jobs), ([], None))


if __name__ == "__main__":
    unittest.main()
