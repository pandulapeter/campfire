# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

import unittest

from check_sizes import FUNCTION_LINES, measure, problems, top_level_functions, updated

COMPOSABLE = """@Composable
internal fun SongCard(
    modifier: Modifier = Modifier,
    title: String,
) {
    Column(modifier) {
        Text(title)
        Text("}")
    }
}

private fun String.shortened() = take(
    10,
)
"""


def sizes_of(files, modules=()):
    return measure(list(files), files.__getitem__, lambda directory: directory in modules)


class TopLevelFunctionsTest(unittest.TestCase):

    def test_a_function_ends_at_its_closing_brace_not_at_its_parameter_list(self):
        self.assertEqual([("SongCard", 2, 9), ("String.shortened", 12, 3)], top_level_functions(COMPOSABLE))

    def test_brackets_in_strings_and_comments_are_not_counted(self):
        text = 'fun a() {\n    val b = "{"\n    // {\n    /* { */\n}\nfun c() = 1\n'
        self.assertEqual([("a", 1, 5), ("c", 6, 1)], top_level_functions(text))

    def test_a_chained_expression_body_runs_to_its_last_line(self):
        text = "fun a() = listOf(1)\n    .map { it }\n    .sum()\nfun b() = 2\n"
        self.assertEqual([("a", 1, 3), ("b", 4, 1)], top_level_functions(text))


class SizesTest(unittest.TestCase):

    def test_tests_and_data_tables_are_not_measured(self):
        sizes = sizes_of({
            "m/src/commonTest/kotlin/A.kt": "x\n" * 900,
            "m/src/commonMain/kotlin/GuitarChordTable.kt": "x\n" * 900,
        })
        self.assertEqual({}, sizes)

    def test_a_claude_md_is_judged_by_where_it_is(self):
        sizes = sizes_of({
            "CLAUDE.md": "w ",
            "chordpro/CLAUDE.md": "w ",
            "presentation/src/wasmJsMain/CLAUDE.md": "w ",
            "presentation/src/commonMain/kotlin/ui/CLAUDE.md": "w ",
        }, modules=("chordpro",))
        self.assertEqual([2500, 1500, 1500, 600], [sizes[path][1] for path in (
            "CLAUDE.md",
            "chordpro/CLAUDE.md",
            "presentation/src/wasmJsMain/CLAUDE.md",
            "presentation/src/commonMain/kotlin/ui/CLAUDE.md",
        )])

    def test_the_license_header_is_not_counted(self):
        sizes = sizes_of({"CLAUDE.md": "<!--\n * Mozilla Public License\n-->\n# Campfire\n"})
        self.assertEqual(2, sizes["CLAUDE.md"][0])


class ProblemsTest(unittest.TestCase):

    def test_a_long_function_fails(self):
        sizes = {"A.kt::f": (FUNCTION_LINES + 1, FUNCTION_LINES, "lines")}
        self.assertEqual(1, len(problems(sizes, {})))

    def test_an_allowance_lets_a_size_stay_but_not_grow(self):
        self.assertEqual([], problems({"A.kt": (600, 500, "lines")}, {"A.kt": 600}))
        self.assertEqual(1, len(problems({"A.kt": (601, 500, "lines")}, {"A.kt": 600})))

    def test_an_allowance_larger_than_needed_fails(self):
        self.assertEqual(1, len(problems({"A.kt": (550, 500, "lines")}, {"A.kt": 600})))
        self.assertEqual(1, len(problems({"A.kt": (400, 500, "lines")}, {"A.kt": 600})))
        self.assertEqual(1, len(problems({}, {"Gone.kt": 600})))

    def test_an_update_lowers_allowances_and_never_raises_one(self):
        sizes = {"A.kt": (550, 500, "lines"), "B.kt": (700, 500, "lines"), "C.kt": (400, 500, "lines")}
        self.assertEqual({"A.kt": 550, "B.kt": 600}, updated(sizes, {"A.kt": 600, "B.kt": 600, "C.kt": 600}))


if __name__ == "__main__":
    unittest.main()
