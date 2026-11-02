# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Keeps the imports inside `:presentation` pointing one way: down its four package layers (presentation/CLAUDE.md).

A file may import its own layer and lower ones only. A package belongs to the longest prefix listed in LAYERS (the root
packages match exactly, never as a prefix); every `ui.*` package not listed is app. A file under `ui.screens.<a>`
imports nothing under `ui.screens.<b>` for another screen (the `ui.screens` package itself, which holds what several
screens share, is allowed). `ui.CampfireViewModel` is imported only by a destination's entry file (`*Screen.kt` or
`ExportHost.kt` directly in a `ui.screens.<a>` package) and by the `ui` package itself (the shells and the app's own
effects); everything else is handed what it uses. VIEW_MODEL_RULE switches that rule, on for the tree.

Only main source sets are judged: tests import shared helpers from wherever they sit, which is no layering of the app.
Each judged file has every import judged, so that nothing is stored and no list of exceptions is kept; the tree is
clean, and a change keeps it so.

With no arguments it judges the staged files (.githooks/pre-commit); `--base <rev>` judges the files changed since that
revision (tests.yml); `--hook` judges the file a Claude Code edit wrote, read from the hook's JSON on stdin
(.claude/settings.json). A deleted file is judged by nothing.
"""

import json
import os
import re
import subprocess
import sys

ROOT = "com.pandulapeter.campfire.presentation"
SOURCES = re.compile(r"^presentation/src/(\w+)/kotlin/(.+\.kt)$")
PACKAGE = re.compile(r"^package\s+([\w.]+)", re.MULTILINE)
IMPORT = re.compile(r"^import\s+(" + re.escape(ROOT) + r"(?:\.[\w`]+)*)", re.MULTILINE)

ORDER = ("foundation", "components", "app", "surfaces")

# (package relative to ROOT, layer, matched exactly). "" is ROOT itself.
LAYERS = (
    ("", "foundation", True),
    ("localization", "foundation", False),
    ("mapper", "foundation", False),
    ("resources", "foundation", False),
    ("ui.theme", "foundation", False),
    ("ui.platform", "foundation", False),
    ("ui.search", "foundation", False),
    ("ui.components", "components", False),
    ("ui.settingsPage", "components", False),
    ("ui.screens", "surfaces", False),
    ("ui.chrome", "surfaces", False),
    ("ui.dialogs.sheet", "app", False),
    ("ui", "surfaces", True),
)

VIEW_MODEL_RULE = True
VIEW_MODEL = f"{ROOT}.ui.CampfireViewModel"


def layer_of(package):
    """The layer of a fully qualified package of :presentation."""
    relative = package[len(ROOT) + 1:] if package.startswith(ROOT + ".") else "" if package == ROOT else None
    if relative is None:
        return None
    best = None
    for prefix, layer, exact in LAYERS:
        matches = relative == prefix if exact else relative == prefix or relative.startswith(prefix + ".")
        if matches and (best is None or len(prefix) > len(best[0])):
            best = (prefix, layer)
    if best is not None:
        return best[1]
    if relative.startswith("ui.dialogs."):
        return "surfaces"
    return "app" if relative.startswith("ui.") else "foundation"


def table_packages():
    return {ROOT if not prefix else f"{ROOT}.{prefix}" for prefix, _, _ in LAYERS}


def resolved(imported, packages):
    """The longest known package that prefixes an import, or None for one outside :presentation's packages."""
    best = None
    for package in packages:
        if imported == package or imported.startswith(package + "."):
            if best is None or len(package) > len(best):
                best = package
    return best


def screen_of(package):
    """`a` for a package under `ui.screens.<a>`, None elsewhere (`ui.screens` itself included)."""
    prefix = f"{ROOT}.ui.screens."
    return package[len(prefix):].split(".")[0] if package.startswith(prefix) else None


def is_entry_file(path, package):
    name = os.path.basename(path)
    return (
        package.startswith(f"{ROOT}.ui.screens.")
        and package.count(".") == f"{ROOT}.ui.screens.x".count(".")
        and (name.endswith("Screen.kt") or name == "ExportHost.kt")
    )


def is_judged(path):
    match = SOURCES.match(path)
    return bool(match) and not match.group(1).lower().endswith("test")


def problems(path, text, packages, view_model_rule=VIEW_MODEL_RULE):
    """The failures of one source file's imports."""
    if not is_judged(path):
        return []
    found = PACKAGE.search(text)
    if not found or not (found.group(1) == ROOT or found.group(1).startswith(ROOT + ".")):
        return []
    package = found.group(1)
    layer = layer_of(package)
    result = []
    for imported in IMPORT.findall(text):
        if imported == VIEW_MODEL:
            if view_model_rule and package != f"{ROOT}.ui" and not is_entry_file(path, package):
                result.append(f"{path}: imports {imported}; only a destination's entry file (*Screen.kt or "
                              "ExportHost.kt in a ui.screens.<screen> package) or the ui package itself takes the view "
                              "model, pass the rest what it uses.")
            continue
        target = resolved(imported, packages)
        if target is None:
            continue
        target_layer = layer_of(target)
        if ORDER.index(target_layer) > ORDER.index(layer):
            result.append(f"{path}: imports {imported}; {layer} may not import {target_layer}, move the declaration "
                          "down or pass it in.")
            continue
        own, other = screen_of(package), screen_of(target)
        if own is not None and other is not None and own != other:
            result.append(f"{path}: imports {imported}; the screen {own} may not import the screen {other}, move the "
                          "declaration to a shared package.")
    return result


def git(*arguments):
    return subprocess.run(["git", *arguments], check=True, capture_output=True).stdout.decode("utf-8", "replace")


def read_file(path):
    with open(path, encoding="utf-8", errors="replace") as file:
        return file.read()


def known_packages(read):
    """Every package declared under presentation/src plus the table's own (resources is generated and declares none)."""
    packages = table_packages()
    for path in git("ls-files", "-z", "--", "presentation/src").split("\0"):
        if path.endswith(".kt") and os.path.isfile(path):
            found = PACKAGE.search(read(path))
            if found:
                packages.add(found.group(1))
    return packages


def judged(paths, read):
    packages = known_packages(read_file)
    failures = []
    for path in paths:
        if is_judged(path):
            text = read(path)
            if text is not None:
                failures.extend(problems(path, text, packages))
    return failures


def shown(path):
    result = subprocess.run(["git", "show", f":{path}"], capture_output=True)
    return result.stdout.decode("utf-8", "replace") if result.returncode == 0 else None


def main(arguments):
    if "--hook" in arguments:
        path = ((json.load(sys.stdin).get("tool_input") or {}).get("file_path")) or ""
        if not path or not os.path.isfile(path):
            return 0
        top = subprocess.run(
            ["git", "-C", os.path.dirname(path), "rev-parse", "--show-toplevel"], capture_output=True, text=True,
        ).stdout.strip()
        if not top:
            return 0
        os.chdir(top)
        failures = judged([os.path.relpath(os.path.realpath(path), os.path.realpath(top))], read_file)
        if failures:
            print("\n".join(failures), file=sys.stderr)
            return 2
        return 0
    if "--base" in arguments:
        base = arguments[arguments.index("--base") + 1]
        paths = git("diff", "--name-only", "--diff-filter=ACMR", "-z", base).split("\0")
        failures = judged([path for path in paths if path and os.path.isfile(path)], read_file)
    else:
        paths = git("diff", "--cached", "--name-only", "--diff-filter=ACMR", "-z", "HEAD").split("\0")
        failures = judged([path for path in paths if path], shown)
    for problem in failures:
        print(f"::error::{problem}" if "--base" in arguments else problem, file=sys.stderr)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
