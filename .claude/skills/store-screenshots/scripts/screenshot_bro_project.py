# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Keeps the Screenshot Bro project the store images are framed in under version control, in tools/screenshots/project/.

`save` copies the project out of the app's container: its project.json, pretty-printed, and the images its image shapes
show (the app icon and logos), which no render can make again. It leaves out what changes on every run without the
layout changing - the screenshot each device frame holds, which a run imports again, and the time the project was last
modified - so a diff shows only what was arranged by hand.

`restore` puts it back into a container, on this Mac or another one with Screenshot Bro installed and opened once: the
project with empty device frames, its images, and its entry in the app's project list. A run of the skill fills the
frames. Screenshot Bro has to be closed, since it keeps its projects in memory and writes them back.

Usage: python3 -I screenshot_bro_project.py save|restore [--force]
"""

import argparse
import json
import pathlib
import shutil
import subprocess
import sys
import time

PROJECT_ID = "22343275-9EE7-449C-8028-DD5D78226936"
APP_SUPPORT = pathlib.Path.home() / "Library/Containers/xyz.tleskiv.screenshot/Data/Library/Application Support/screenshot"
REPOSITORY_PROJECT = pathlib.Path(__file__).resolve().parents[4] / "tools/screenshots/project"

# The keys project.json gives a device frame's screenshot file, an image shape's file, and the project's modification
# time, which the app writes as seconds since the first of January 2001.
SCREENSHOT_FILE = "sfn"
IMAGE_FILE = "ifn"
MODIFIED = "m"
APPLE_EPOCH = 978307200


def image_files(node):
    """Every file an image shape of [node] shows, wherever in the project it is."""
    if isinstance(node, dict):
        return {node[IMAGE_FILE]} if IMAGE_FILE in node else set().union(*(image_files(value) for value in node.values()))
    if isinstance(node, list):
        return set().union(*(image_files(value) for value in node))
    return set()


def without_screenshots(node):
    """[node] with no device frame holding a screenshot, as a frame is before anything is imported into it."""
    if isinstance(node, dict):
        return {key: without_screenshots(value) for key, value in node.items() if key != SCREENSHOT_FILE}
    if isinstance(node, list):
        return [without_screenshots(value) for value in node]
    return node


def stop_if_running():
    if subprocess.run(["pgrep", "-x", "Screenshot Bro"], capture_output=True).returncode == 0:
        sys.exit("Quit Screenshot Bro first: it keeps its projects in memory and would write over the files.")


def save():
    source = APP_SUPPORT / "projects" / PROJECT_ID
    if not (source / "project.json").is_file():
        sys.exit(f"There is no Campfire project in {source}.")
    project = json.loads((source / "project.json").read_text())
    project.pop(MODIFIED, None)
    images = image_files(project)
    resources = REPOSITORY_PROJECT / "resources"
    if resources.exists():
        shutil.rmtree(resources)
    resources.mkdir(parents=True)
    for name in sorted(images):
        shutil.copy2(source / "resources" / name, resources / name)
    (REPOSITORY_PROJECT / "project.json").write_text(json.dumps(without_screenshots(project), indent=2, ensure_ascii=False) + "\n")
    print(f"Saved the project and {len(images)} images to {REPOSITORY_PROJECT}.")


def restore(force):
    stop_if_running()
    if not APP_SUPPORT.is_dir():
        sys.exit(f"There is no {APP_SUPPORT}: install Screenshot Bro and open it once, then quit it.")
    target = APP_SUPPORT / "projects" / PROJECT_ID
    if target.exists():
        if not force:
            sys.exit(f"{target} already exists. Pass --force to replace it; it is moved aside first, not deleted.")
        # Kept outside the projects folder, which the app reads as its list of projects.
        backup = APP_SUPPORT / "project-backups" / f"{PROJECT_ID}-{time.strftime('%Y%m%d-%H%M%S')}"
        backup.parent.mkdir(exist_ok=True)
        target.rename(backup)
        print(f"Moved the existing project aside to {backup}.")
    project = json.loads((REPOSITORY_PROJECT / "project.json").read_text())
    modified = time.time() - APPLE_EPOCH
    project[MODIFIED] = modified
    (target / "resources").mkdir(parents=True)
    for image in (REPOSITORY_PROJECT / "resources").iterdir():
        shutil.copy2(image, target / "resources" / image.name)
    (target / "project.json").write_text(json.dumps(project, ensure_ascii=False))
    index_file = APP_SUPPORT / "projects.json"
    index = json.loads(index_file.read_text()) if index_file.is_file() else {"projects": []}
    index["projects"] = [entry for entry in index["projects"] if entry["id"] != PROJECT_ID] + [
        {"id": PROJECT_ID, "isDeleted": False, "isStarred": False, "modifiedAt": modified, "name": project["n"]},
    ]
    index["activeProjectId"] = PROJECT_ID
    index_file.write_text(json.dumps(index, ensure_ascii=False))
    print(f"Restored the project to {target}. Open Screenshot Bro and run the skill to fill its frames.")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("save", "restore"))
    parser.add_argument("--force", action="store_true")
    arguments = parser.parse_args()
    if arguments.action == "save":
        save()
    else:
        restore(arguments.force)


if __name__ == "__main__":
    main()
