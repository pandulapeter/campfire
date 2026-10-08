# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Stops a publish workflow whose secrets are empty, before anything is built.

`gh secret set` stores an empty value without complaint when it has no terminal to prompt on, and a secret a workflow
cannot see reads as empty too. The empty Dropbox key is worse than a failure: it is the checked-in default, because a
fresh clone has to build without a secret, so a published build would quietly have no sync provider at all. Each name
is that of an environment variable the step sets from the secret of the same name; every empty one is reported, so
that one run names them all. Run as python3 on Linux and macOS and as python on the Windows runner.

    require_secrets.py [--why "What happens without them."] NAME [NAME ...]
"""

import argparse
import os
import sys


def empty_secrets(names, environment):
    return [name for name in names if not environment.get(name)]


def main(arguments, environment):
    parser = argparse.ArgumentParser()
    parser.add_argument("--why", default=None)
    parser.add_argument("names", nargs="+")
    options = parser.parse_args(arguments)
    empty = empty_secrets(options.names, environment)
    for name in empty:
        reason = f" {options.why}" if options.why else ""
        print(f"::error::{name} is empty or not visible to this workflow.{reason}", file=sys.stderr)
    return 1 if empty else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:], os.environ))
