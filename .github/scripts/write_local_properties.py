# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Writes local.properties for a publish workflow, which is where a developer's machine keeps the secrets too.

Each argument is key=ENV_NAME, the value read from that environment variable, or key=:literal for a value that is not
a secret. A secret reaches this script only through the step's env: on the command line it would be in the runner's
process list, and interpolated into the step's script it would be read again by the shell, so a password holding a $,
a backtick or a quote would be something other than what is in the secret store. Backslashes are doubled, since
java.util.Properties reads one as an escape inside a value; nothing else is, since Gradle reads the file as UTF-8. The
file is written in UTF-8 with LF line endings on every runner, Windows included.

    write_local_properties.py [--append] key=ENV_NAME key=:literal ...
"""

import argparse
import os
import sys

FILE_NAME = "local.properties"


class MissingVariableError(Exception):
    pass


def property_lines(assignments, environment):
    lines = []
    for assignment in assignments:
        key, separator, source = assignment.partition("=")
        if not key or not separator:
            raise ValueError(f"Expected key=ENV_NAME or key=:literal, not {assignment!r}.")
        if source.startswith(":"):
            value = source[1:]
        elif source in environment:
            value = environment[source]
        else:
            raise MissingVariableError(f"{source}, the value of {key}, is not set.")
        lines.append(f"{key}={value.replace(chr(92), chr(92) * 2)}\n")
    return lines


def main(arguments, environment, path=FILE_NAME):
    parser = argparse.ArgumentParser()
    parser.add_argument("--append", action="store_true")
    parser.add_argument("assignments", nargs="+")
    options = parser.parse_args(arguments)
    try:
        lines = property_lines(options.assignments, environment)
    except (MissingVariableError, ValueError) as error:
        print(f"::error::{error}", file=sys.stderr)
        return 1
    with open(path, "a" if options.append else "w", encoding="utf-8", newline="\n") as file:
        file.writelines(lines)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:], os.environ))
