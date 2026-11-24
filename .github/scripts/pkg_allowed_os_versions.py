# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Makes a Mac App Store .pkg allow only the app's own minimum macOS, which App Store Connect refuses a package without
(error 90264): the lowest version the package's Distribution allows has to equal the app's LSMinimumSystemVersion.
jpackage builds a store package with a product definition of its own that adds 10.11, and takes another only through
the --resource-dir the Compose plugin owns, so the finished package is unpacked, its Distribution rewritten, and packed
again. It runs from the workflow rather than the build so that a hand dispatch fixes a package built from an existing
tag. Packing drops the installer signature, so the package is signed again with the identity jpackage signed it with.

    pkg_allowed_os_versions.py PACKAGE MINIMUM [--sign IDENTITY] [--keychain PATH]
"""

import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile

ALLOWED_OS_VERSIONS = re.compile(r"(<allowed-os-versions>)(.*?)(\s*</allowed-os-versions>)", re.DOTALL)


def with_only_allowed_os_version(distribution, minimum):
    """The Distribution with its allowed-os-versions naming minimum alone; one without that element allows any."""

    def replace(match):
        indent = re.search(r"\n([ \t]*)<os-version", match.group(2))
        return f"{match.group(1)}\n{indent.group(1) if indent else ''}<os-version min=\"{minimum}\"/>{match.group(3)}"

    return ALLOWED_OS_VERSIONS.sub(replace, distribution)


def main(arguments):
    parser = argparse.ArgumentParser()
    parser.add_argument("package")
    parser.add_argument("minimum")
    parser.add_argument("--sign", default="")
    parser.add_argument("--keychain", default="")
    options = parser.parse_args(arguments)
    if not re.fullmatch(r"\d+(\.\d+){0,2}", options.minimum):
        print(f"::error::{options.minimum!r} is not a macOS version.", file=sys.stderr)
        return 1
    with tempfile.TemporaryDirectory() as work:
        expanded = os.path.join(work, "expanded")
        flattened = os.path.join(work, "flattened.pkg")
        subprocess.run(["pkgutil", "--expand", options.package, expanded], check=True)
        path = os.path.join(expanded, "Distribution")
        with open(path, encoding="utf-8") as file:
            distribution = file.read()
        rewritten = with_only_allowed_os_version(distribution, options.minimum)
        with open(path, "w", encoding="utf-8") as file:
            file.write(rewritten)
        subprocess.run(["pkgutil", "--flatten", expanded, flattened], check=True)
        result = flattened
        if options.sign:
            result = os.path.join(work, "signed.pkg")
            keychain = ["--keychain", options.keychain] if options.keychain else []
            subprocess.run(["productsign", "--sign", options.sign, *keychain, flattened, result], check=True)
        shutil.copyfile(result, options.package)
    print(f"The package allows macOS {options.minimum} and later" + (f", signed by {options.sign}." if options.sign else "."))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
