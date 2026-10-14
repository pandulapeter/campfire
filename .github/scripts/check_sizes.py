# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Keeps files, functions and CLAUDE.md files from growing past the sizes the code-style skill sets, for tests.yml.

Main Kotlin sources (tests are left out) may be at most FILE_LINES lines long, and a top-level function — a Composable
above all — at most FUNCTION_LINES. A CLAUDE.md may be at most ROOT_WORDS words at the root, MODULE_WORDS in a module,
`.github` or a source set, and PACKAGE_WORDS anywhere else. Data tables are exempt by name.

What was over a limit when the check came in is listed in size_allowances.json at its size then: it may shrink but
never grow, and an allowance larger than what it allows fails too, so that what a change gained is not spent again.
`--update` lowers every allowance to the current size and drops the ones no longer needed; it never raises one.
`--hook` is the Claude Code edit hook (.claude/settings.json): it reads the hook's JSON from stdin and judges only the
file the edit wrote, so that a file is split while it is being written rather than when a commit or CI refuses it.
"""

import fnmatch
import json
import os
import re
import subprocess
import sys

FILE_LINES = 500
FUNCTION_LINES = 120
ROOT_WORDS = 2500
MODULE_WORDS = 1500
PACKAGE_WORDS = 600

EXEMPT = (
    "*ChordTable.kt",
    "*ChordTables.kt",
    "*/PdfGlyphNames.kt",
    "*/MaterialColorSchemes.kt",
)

ALLOWANCES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "size_allowances.json")

DECLARATION = re.compile(
    r"^(?:(?:public|internal|private|protected|inline|suspend|operator|infix|override|actual|expect|tailrec|external)\s+)*fun\s"
)
FUNCTION_NAME = re.compile(r"fun\s+(?:<[^>]*>\s*)?([\w.`]+(?:<[^>]*>)?[\w.`]*)\s*\(")
TEST_SOURCES = re.compile(r"/src/\w*[Tt]est/")
CONTINUATION = tuple(".?:+-*/&|=")


def is_main_kotlin(path):
    return path.endswith(".kt") and "/src/" in path and not TEST_SOURCES.search(path) and not any(
        fnmatch.fnmatch(path, pattern) for pattern in EXEMPT
    )


def code_only(lines):
    """The lines with strings, character literals and comments taken out, so that only real brackets are counted."""
    result = []
    in_comment = False
    in_raw_string = False
    for line in lines:
        kept = []
        index = 0
        while index < len(line):
            if in_comment or in_raw_string:
                end = line.find("*/" if in_comment else '"""', index)
                if end < 0:
                    break
                index = end + (2 if in_comment else 3)
                in_comment = in_raw_string = False
            elif line.startswith("//", index):
                break
            elif line.startswith("/*", index):
                in_comment = True
                index += 2
            elif line.startswith('"""', index):
                in_raw_string = True
                index += 3
            elif line[index] in "\"'":
                quote = line[index]
                index += 1
                while index < len(line) and line[index] != quote:
                    index += 2 if line[index] == "\\" else 1
                index += 1
            else:
                kept.append(line[index])
                index += 1
        result.append("".join(kept))
    return result


def top_level_functions(text):
    """(name, first line, length in lines) of every function declared at the top level of a Kotlin file."""
    lines = text.split("\n")
    code = code_only(lines)
    functions = []
    start = 0
    while start < len(code):
        if not DECLARATION.match(code[start]):
            start += 1
            continue
        depth = 0
        has_opened = False
        end = start
        while end < len(code):
            for character in code[end]:
                if character in "([{":
                    depth += 1
                    has_opened = True
                elif character in ")]}":
                    depth -= 1
            if has_opened and depth == 0 and not code[end].rstrip().endswith("="):
                following = next((line for line in code[end + 1:] if line.strip()), "")
                if not (following[:1].isspace() and following.strip().startswith(CONTINUATION)):
                    break
            end += 1
        name = FUNCTION_NAME.search(lines[start])
        functions.append((name.group(1) if name else lines[start].strip(), start + 1, end - start + 1))
        start = end + 1
    return functions


def words(text):
    """The words of a CLAUDE.md after its license header, which every file carries and no budget should pay for."""
    if text.startswith("<!--"):
        end = text.find("-->")
        text = text[end + 3:] if end >= 0 else text
    return len(text.split())


def word_budget(path, is_module):
    directory = os.path.dirname(path)
    if not directory:
        return ROOT_WORDS
    if is_module(directory) or directory == ".github" or re.search(r"(^|/)src/\w+$", directory):
        return MODULE_WORDS
    return PACKAGE_WORDS


def measure(paths, read, is_module):
    """Every size the check limits, as {key: (size, limit, unit)}; a function's key is "<path>::<name>"."""
    sizes = {}
    for path in paths:
        if os.path.basename(path) == "CLAUDE.md":
            sizes[path] = (words(read(path)), word_budget(path, is_module), "words")
        elif is_main_kotlin(path):
            text = read(path)
            sizes[path] = (text.count("\n") + (0 if text.endswith("\n") else 1), FILE_LINES, "lines")
            for name, _, length in top_level_functions(text):
                key = f"{path}::{name}"
                if key not in sizes or sizes[key][0] < length:
                    sizes[key] = (length, FUNCTION_LINES, "lines")
    return sizes


def problems(sizes, allowances):
    """What fails the check: a size over its limit and its allowance, and an allowance that is stale or too large."""
    found = []
    for key, (size, limit, unit) in sorted(sizes.items()):
        allowed = allowances.get(key)
        if size > max(limit, allowed or 0):
            if allowed:
                found.append(f"{key} is {size} {unit}, over its allowance of {allowed}; split it rather than raise it.")
            else:
                found.append(f"{key} is {size} {unit}, over the limit of {limit}; split it (see the code-style skill).")
    for key, allowed in sorted(allowances.items()):
        size, limit, unit = sizes.get(key, (None, None, None))
        if size is None or size <= limit:
            found.append(f"{key} no longer needs an allowance; run check_sizes.py --update.")
        elif size < allowed:
            found.append(f"{key} shrank to {size} {unit}; run check_sizes.py --update to lower its allowance.")
    return found


def updated(sizes, allowances):
    """The allowances lowered to the current sizes, without the ones no longer needed; none is ever raised."""
    return {
        key: min(allowed, sizes[key][0])
        for key, allowed in sorted(allowances.items())
        if key in sizes and sizes[key][0] > sizes[key][1]
    }


def tracked_files():
    output = subprocess.run(["git", "ls-files", "-z"], check=True, capture_output=True).stdout
    return [path for path in output.decode("utf-8").split("\0") if path and os.path.isfile(path)]


def read_file(path):
    with open(path, encoding="utf-8", errors="replace") as file:
        return file.read()


def is_gradle_module(directory):
    return os.path.isfile(os.path.join(directory, "build.gradle.kts"))


def checked_edit(hook_input):
    """For the Claude Code hook: the problems of the one file an edit wrote, judged in the checkout it belongs to."""
    path = (hook_input.get("tool_input") or {}).get("file_path") or ""
    if not path or not os.path.isfile(path):
        return []
    top = subprocess.run(
        ["git", "-C", os.path.dirname(path), "rev-parse", "--show-toplevel"], capture_output=True, text=True,
    ).stdout.strip()
    allowances_path = os.path.join(top, ".github", "scripts", "size_allowances.json")
    if not top or not os.path.isfile(allowances_path):
        return []
    relative = os.path.relpath(os.path.realpath(path), os.path.realpath(top))
    with open(allowances_path, encoding="utf-8") as file:
        allowances = json.load(file)
    sizes = measure([relative], lambda name: read_file(os.path.join(top, name)),
                    lambda directory: is_gradle_module(os.path.join(top, directory)))
    return [problem for problem in problems(sizes, {key: allowances[key] for key in allowances if key in sizes})
            if "allowance;" not in problem and "shrank" not in problem]


def main(arguments):
    if "--hook" in arguments:
        found = checked_edit(json.load(sys.stdin))
        for problem in found:
            print(problem, file=sys.stderr)
        return 2 if found else 0
    sizes = measure(tracked_files(), read_file, is_gradle_module)
    with open(ALLOWANCES, encoding="utf-8") as file:
        allowances = json.load(file)
    if "--update" in arguments:
        with open(ALLOWANCES, "w", encoding="utf-8") as file:
            json.dump(updated(sizes, allowances), file, indent=2, ensure_ascii=False)
            file.write("\n")
        return 0
    found = problems(sizes, allowances)
    for problem in found:
        print(f"::error::{problem}", file=sys.stderr)
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
