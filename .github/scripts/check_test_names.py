# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Fails on a backticked function name with a comma in the tests of a module whose tests run on the iOS simulator.
Kotlin/Native refuses a comma in a name ("Name contains illegal characters"), which the JVM accepts, so a test named
that way passes `desktopTest` and stops the module's whole iOS test task from compiling, in CI only, since that task
takes minutes to link. The other characters Kotlin/Native refuses the JVM refuses too, so `desktopTest` finds them.

Only the modules in `IOS_TESTED` are checked: the other modules' tests are never compiled for iOS, and many of their
names have commas. With paths it checks those files, without them every tracked Kotlin file of those modules' common
and iOS tests; exits 1 on any finding.
"""

import os
import re
import subprocess
import sys

IOS_TESTED = ["data/source/local/implementation"]
TEST_SOURCE_SETS = ["commonTest", "iosTest"]
NAME = re.compile(r"\bfun\s+`([^`\n]*,[^`\n]*)`")


def findings(text):
    """(line, name) of every backticked function name with a comma in Kotlin source."""
    return [(text.count("\n", 0, match.start()) + 1, match.group(1)) for match in NAME.finditer(text)]


def main(arguments):
    paths = arguments or subprocess.run(
        ["git", "ls-files", "--", *[f"{module}/src/{source_set}/*.kt" for module in IOS_TESTED
                                    for source_set in TEST_SOURCE_SETS]],
        check=True, capture_output=True, text=True,
    ).stdout.splitlines()
    report = []
    # A tracked file deleted in the working tree (a partial commit beside a pending deletion) has nothing to check.
    for path in filter(os.path.isfile, paths):
        with open(path, encoding="utf-8") as file:
            report += [f"{path}:{line}: `{name}`" for line, name in findings(file.read())]
    if report:
        print("\n".join(report))
        print("Reword the test names above without a comma (\"and\", a dash): Kotlin/Native refuses it.",
              file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
