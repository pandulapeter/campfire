# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

import os
import subprocess
import tempfile
import unittest

from check_sizes import (
    CONSTRUCTOR_PARAMETERS,
    FILE_LINES,
    FUNCTION_LINES,
    changed,
    checked_tree,
    constructors,
    judged,
    measure,
    top_level_functions,
)

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


CLASSES = """/** A class(with, three, commas) named in KDoc. */
@Single
internal class Holder<T : Comparable<T>> @Inject constructor(
    @Named("a, b") private val first: Map<String, Int> = mapOf<String, Int>("x" to 1, "y" to 2),
    val second: (Int, Int) -> Unit = { a, b -> println("$a, $b") },
    third: List<Pair<Int, Int>> = listOf(1 to 2, 3 to 4),
    override val fourth: String = ",",
    vararg fifth: Int,
) : Base(first, second) {
    private data class Nested(val a: Int, val b: Int)

    class Bare : Other()

    fun use() = Holder::class.simpleName
}

enum class Mode(val label: String) { One("1, 2"), Two("3") }

class Empty
"""


class ConstructorsTest(unittest.TestCase):

    def test_parameters_are_counted_past_commas_in_defaults_types_lambdas_and_strings(self):
        self.assertEqual([("Holder", 5), ("Mode", 1)], constructors(CLASSES))

    def test_a_data_class_is_not_measured_for_constructors(self):
        record = "data class Settings(" + ", ".join(f"a{index}: Int" for index in range(20)) + ")\n"
        self.assertEqual([], constructors(record))

    def test_tests_are_not_measured_for_constructors(self):
        many = "class Big(" + ", ".join(f"a{index}: Int" for index in range(20)) + ")\n"
        self.assertEqual({}, sizes_of({"m/src/desktopTest/kotlin/Big.kt": many}))
        self.assertEqual((20, CONSTRUCTOR_PARAMETERS, "parameters"),
                         sizes_of({"m/src/commonMain/kotlin/Big.kt": many})["m/src/commonMain/kotlin/Big.kt::class Big"])


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
            "libs/chordpro/CLAUDE.md": "w ",
            "presentation/src/wasmJsMain/CLAUDE.md": "w ",
            "presentation/src/commonMain/kotlin/ui/CLAUDE.md": "w ",
        }, modules=("libs/chordpro",))
        self.assertEqual([2500, 1500, 1500, 1000], [sizes[path][1] for path in (
            "CLAUDE.md",
            "libs/chordpro/CLAUDE.md",
            "presentation/src/wasmJsMain/CLAUDE.md",
            "presentation/src/commonMain/kotlin/ui/CLAUDE.md",
        )])

    def test_the_license_header_is_not_counted(self):
        sizes = sizes_of({"CLAUDE.md": "<!--\n * Mozilla Public License\n-->\n# Campfire\n"})
        self.assertEqual(2, sizes["CLAUDE.md"][0])


class JudgedTest(unittest.TestCase):

    def test_crossing_a_limit_fails(self):
        before = {"A.kt": (FILE_LINES, FILE_LINES, "lines")}
        failures, _ = judged(before, {"A.kt": (FILE_LINES + 1, FILE_LINES, "lines")})
        self.assertEqual(1, len(failures))

    def test_a_new_file_past_a_limit_fails(self):
        failures, _ = judged({}, {"A.kt::f": (FUNCTION_LINES + 1, FUNCTION_LINES, "lines")})
        self.assertEqual(1, len(failures))

    def test_what_was_already_past_a_limit_may_not_grow(self):
        failures, _ = judged({"A.kt": (600, 500, "lines")}, {"A.kt": (601, 500, "lines")})
        self.assertEqual(1, len(failures))
        self.assertIn("grew from 600 to 601", failures[0])

    def test_what_was_already_past_a_limit_may_stay_as_long_or_shrink(self):
        for size in (600, 550):
            self.assertEqual(([], []), judged({"A.kt": (600, 500, "lines")}, {"A.kt": (size, 500, "lines")}))

    def test_a_split_that_shrinks_the_old_file_passes(self):
        before = {"A.kt": (900, 500, "lines"), "A.kt::f": (300, 120, "lines")}
        after = {
            "A.kt": (500, 500, "lines"),
            "A.kt::f": (100, 120, "lines"),
            "B.kt": (420, 500, "lines"),
            "B.kt::g": (110, 120, "lines"),
        }
        self.assertEqual(([], []), judged(before, after))

    def test_a_function_moved_to_another_file_keeps_its_past(self):
        failures, _ = judged({"A.kt::f": (200, 120, "lines")}, {"B.kt::f": (200, 120, "lines")})
        self.assertEqual([], failures)

    def test_a_function_moved_to_another_file_may_not_grow_there(self):
        failures, _ = judged({"A.kt::f": (200, 120, "lines")}, {"B.kt::f": (201, 120, "lines")})
        self.assertEqual(1, len(failures))

    def test_a_constructor_crossing_the_limit_fails(self):
        before = {"A.kt::class A": (12, 12, "parameters")}
        failures, _ = judged(before, {"A.kt::class A": (13, 12, "parameters")})
        self.assertEqual(1, len(failures))

    def test_a_new_class_past_the_limit_fails(self):
        failures, _ = judged({}, {"A.kt::class A": (13, 12, "parameters")})
        self.assertEqual(1, len(failures))

    def test_a_constructor_already_past_the_limit_may_not_gain_parameters_but_may_keep_or_lose_them(self):
        before = {"A.kt::class A": (15, 12, "parameters")}
        self.assertEqual(1, len(judged(before, {"A.kt::class A": (16, 12, "parameters")})[0]))
        for count in (15, 14):
            self.assertEqual(([], []), judged(before, {"A.kt::class A": (count, 12, "parameters")}))

    def test_a_class_moved_to_another_file_keeps_its_past(self):
        failures, _ = judged({"A.kt::class A": (15, 12, "parameters")}, {"B.kt::class A": (15, 12, "parameters")})
        self.assertEqual([], failures)

    def test_a_claude_md_past_its_budget_only_gets_a_note(self):
        failures, notes = judged({}, {"CLAUDE.md": (3000, 2500, "words")})
        self.assertEqual(([], 1), (failures, len(notes)))


class ChangedTest(unittest.TestCase):

    def test_a_renamed_file_is_judged_against_its_old_path(self):
        with tempfile.TemporaryDirectory() as directory:
            def run(*arguments):
                subprocess.run(["git", "-C", directory, *arguments], check=True, capture_output=True)

            run("init", "-q")
            with open(os.path.join(directory, "Old.kt"), "w") as file:
                file.write("x\n" * 600)
            with open(os.path.join(directory, "Edited.kt"), "w") as file:
                file.write("y\n")
            run("add", ".")
            run("-c", "user.name=t", "-c", "user.email=t@t", "commit", "-qm", "base")
            run("mv", "Old.kt", "New.kt")
            with open(os.path.join(directory, "Edited.kt"), "a") as file:
                file.write("z\n")
            with open(os.path.join(directory, "Added.kt"), "w") as file:
                file.write("w\n")
            run("add", ".")
            working_directory = os.getcwd()
            os.chdir(directory)
            try:
                paths = changed("HEAD", True)
            finally:
                os.chdir(working_directory)
            self.assertEqual({"New.kt": "Old.kt", "Edited.kt": "Edited.kt", "Added.kt": None}, paths)

    def test_the_shell_hook_fails_a_file_already_past_its_limit_that_grows_and_passes_one_that_shrinks(self):
        with tempfile.TemporaryDirectory() as directory:
            source = os.path.join(directory, "module", "src", "commonMain")
            os.makedirs(source)
            for name in ("Grown.kt", "Shrunk.kt"):
                with open(os.path.join(source, name), "w") as file:
                    file.write("x\n" * (FILE_LINES + 10))
            subprocess.run(["git", "-C", directory, "init", "-q"], check=True, capture_output=True)
            subprocess.run(["git", "-C", directory, "add", "."], check=True, capture_output=True)
            subprocess.run(
                ["git", "-C", directory, "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-qm", "base"],
                check=True,
                capture_output=True,
            )
            with open(os.path.join(source, "Grown.kt"), "a") as file:
                file.write("y\n")
            with open(os.path.join(source, "Shrunk.kt"), "w") as file:
                file.write("x\n" * FILE_LINES)
            working_directory = os.getcwd()
            os.chdir(directory)
            try:
                failures = checked_tree()
            finally:
                os.chdir(working_directory)
            self.assertEqual(1, len(failures))
            self.assertIn("Grown.kt", failures[0])

    def test_the_shell_hook_judges_files_git_does_not_track_yet(self):
        with tempfile.TemporaryDirectory() as directory:
            subprocess.run(["git", "-C", directory, "init", "-q"], check=True, capture_output=True)
            subprocess.run(
                ["git", "-C", directory, "-c", "user.name=t", "-c", "user.email=t@t", "commit", "-q", "--allow-empty", "-m", "base"],
                check=True,
                capture_output=True,
            )
            os.makedirs(os.path.join(directory, "module", "src", "commonMain"))
            with open(os.path.join(directory, "module", "src", "commonMain", "Untracked.kt"), "w") as file:
                file.write("x\n" * (FILE_LINES + 1))
            working_directory = os.getcwd()
            os.chdir(directory)
            try:
                failures = checked_tree()
            finally:
                os.chdir(working_directory)
            self.assertEqual(1, len(failures))
            self.assertIn("Untracked.kt", failures[0])


if __name__ == "__main__":
    unittest.main()
