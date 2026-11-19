# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Keeps sync's engine and the remote module's provider core free of Campfire, so that they can become a library another
app syncs its own documents with (data/sync/implementation/CLAUDE.md, documentation/plans/sync-library-extraction.md).

A main source file whose package is one of RULES' packages, or under one, imports nothing its rule forbids: the
engine (`run`, `index`, `scheduling`, `connection`, `merge`) nothing of songs, setlists, preferences, shares, imports,
ChordPro, the repositories, or the adapters through which Campfire plugs its library in; the provider core (`auth`,
`network`, `crypto`) nothing of Dropbox, of Campfire's catalogue features, or of the module's root package, which holds
Campfire's redirect, user agent, generated configuration, and Koin module. A star import fails when it would bring in a
forbidden name. Campfire's side reaches the engine only through the seams the engine declares.

Only main source sets are judged: a test may wire Campfire's values into the code it tests. Each judged file has every
import judged, so that nothing is stored and no list of exceptions is kept; the tree is clean, and a change keeps it so.

With no arguments it judges the staged files (.githooks/pre-commit); `--base <rev>` judges the files changed since that
revision (tests.yml); `--hook` judges the file a Claude Code edit wrote, read from the hook's JSON on stdin
(.claude/settings.json); `--all` judges every tracked file. A deleted file is judged by nothing.
"""

import json
import os
import re
import subprocess
import sys

CAMPFIRE = "com.pandulapeter.campfire"
SYNC = f"{CAMPFIRE}.data.sync.implementation"
REMOTE = f"{CAMPFIRE}.data.source.remote.implementation"

SOURCES = re.compile(r"^(?:.+/)?src/(\w+)/kotlin/.+\.kt$")
PACKAGE = re.compile(r"^package\s+([\w.]+)", re.MULTILINE)
IMPORT = re.compile(r"^import\s+([\w`]+(?:\.[\w`]+)*(?:\.\*)?)", re.MULTILINE)

ENGINE = tuple(f"{SYNC}.{package}" for package in ("run", "index", "scheduling", "connection", "merge"))
ENGINE_FORBIDDEN_PACKAGES = (
    f"{CAMPFIRE}.chordpro",
    f"{CAMPFIRE}.data.repository",
    f"{CAMPFIRE}.data.model.song",
    f"{CAMPFIRE}.data.model.setlist",
    f"{CAMPFIRE}.data.model.preferences",
    f"{CAMPFIRE}.data.model.share",
    f"{CAMPFIRE}.data.model.importing",
    f"{SYNC}.shared",
    f"{SYNC}.preferences",
)
ENGINE_FORBIDDEN_DECLARATIONS = (
    f"{CAMPFIRE}.data.model.library.LibraryCollections",
    f"{CAMPFIRE}.data.model.library.LibrarySyncSteps",
    f"{CAMPFIRE}.data.model.library.havePreferencesFailed",
    f"{CAMPFIRE}.data.source.local.api.SetlistComparison",
    f"{SYNC}.LibrarySyncObserver",
    f"{SYNC}.LibrarySyncRules",
    f"{SYNC}.DemoLibraryRepositoryImpl",
    f"{SYNC}.SyncRepositoryImpl",
    f"{SYNC}.DataSyncModule",
)
ENGINE_WHY = "the sync engine knows nothing of Campfire's library; take it through a seam (data/sync/implementation)"

PROVIDER_CORE = tuple(f"{REMOTE}.{package}" for package in ("auth", "network", "crypto"))
PROVIDER_CORE_FORBIDDEN_PACKAGES = tuple(
    f"{REMOTE}.{package}"
    for package in ("dropbox", "coverArt", "iTunes", "musicBrainz", "songDetails", "importDownload")
)
PROVIDER_CORE_WHY = "the provider core names no provider and no app; pass it in (data/source/remote/implementation)"

# (packages judged, forbidden packages with their subpackages, forbidden declarations, packages whose own declarations
# are all forbidden but not their subpackages', why)
RULES = (
    (ENGINE, ENGINE_FORBIDDEN_PACKAGES, ENGINE_FORBIDDEN_DECLARATIONS, (), ENGINE_WHY),
    (PROVIDER_CORE, PROVIDER_CORE_FORBIDDEN_PACKAGES, (), (REMOTE,), PROVIDER_CORE_WHY),
)


def is_under(name, prefix):
    return name == prefix or name.startswith(prefix + ".")


def is_judged(path):
    match = SOURCES.match(path)
    return bool(match) and not match.group(1).lower().endswith("test")


def forbidden(imported, packages, declarations, member_packages):
    """What forbidden an import reaches, or None. An import names a declaration (its last segment) in a package (the
    rest); a star import names a package and brings in its own declarations, never its subpackages'."""
    name = imported.replace("`", "")
    is_star = name.endswith(".*")
    package = name[:-2] if is_star else name.rpartition(".")[0]
    for forbidden_package in packages:
        if is_under(package, forbidden_package) or (not is_star and name == forbidden_package):
            return f"of {forbidden_package}"
    for declaration in declarations:
        if (is_star and declaration.rpartition(".")[0] == package) or is_under(name, declaration):
            return declaration
    if package in member_packages:
        return f"of the root package {package}"
    return None


def problems(path, text):
    """The failures of one source file's imports."""
    if not is_judged(path):
        return []
    found = PACKAGE.search(text)
    if not found:
        return []
    package = found.group(1)
    result = []
    for judged_packages, packages, declarations, member_packages, why in RULES:
        if not any(is_under(package, judged) for judged in judged_packages):
            continue
        for imported in IMPORT.findall(text):
            reached = forbidden(imported, packages, declarations, member_packages)
            if reached is not None:
                result.append(f"{path}: imports {imported}, {reached}; {why}.")
    return result


def git(*arguments):
    return subprocess.run(["git", *arguments], check=True, capture_output=True).stdout.decode("utf-8", "replace")


def read_file(path):
    with open(path, encoding="utf-8", errors="replace") as file:
        return file.read()


def judged(paths, read):
    failures = []
    for path in paths:
        if is_judged(path):
            text = read(path)
            if text is not None:
                failures.extend(problems(path, text))
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
    if "--all" in arguments:
        paths = git("ls-files", "-z", "--", "*.kt").split("\0")
        failures = judged([path for path in paths if path and os.path.isfile(path)], read_file)
    elif "--base" in arguments:
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
