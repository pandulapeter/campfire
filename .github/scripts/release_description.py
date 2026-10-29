# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Reads what a release's description tells the stores, for publish-all.yml.

The instructions are HTML comments the rendered release page hides (the format is in publish-all.yml's header), and
every one of them has a default that is the stronger action: a store is submitted to and its listing written, the notes
are the visible description. So a comment this script cannot read is never taken as absent - a store name it does not
know, a submit or listings value other than true or false, a priority outside 0-5, a whats-new block in a language the
app does not have stop the release, and so does a comment that looks like one of them but is not written the way one is
read, since reading any of them as the default would submit what was meant to wait, or leave to Play's schedule what
was meant to block the old version. The notes of every language are held against every store's limit here too,
whatever that store's submit value is, because the store scripts check them before they look at whether they were
asked to submit, and they run after the builds: an over-long note found there fails the release at the very end, with
a build already uploaded.

Run with the description in RELEASE_BODY; writes the outputs publish-all.yml hands to the six workflows into the file
GITHUB_OUTPUT names.
"""

import json
import os
import re
import sys

from store_listings import app_languages

STORES = ["play-store", "app-store", "mac-app-store", "microsoft-store"]

# The stores' own limits on the notes: App Store Connect and Partner Center refuse more, Play only has them cut to the
# lines that fit by publish-android.yml, so over its limit is a warning rather than an error.
APP_STORE_CONNECT_LIMIT = 4000
PARTNER_CENTER_LIMIT = 1500
PLAY_LIMIT = 500

# What every store gets where the release says nothing: App Store Connect and Partner Center refuse an update without
# notes, and Play would otherwise be given the commit log meant for a hand dispatch.
DEFAULT_NOTES = "Bug fixes and improvements."

# The three shapes an instruction is read in. The two single-line ones also take a closing on the next line, the shape
# the whats-new block has.
WHATS_NEW = re.compile(r"<!--[ \t]*whats-new[ \t]+(\S+)[ \t]*\n.*-->", re.S | re.I)
PRIORITY = re.compile(r"<!--[ \t]*([^\n]*?)[ \t]+update-priority:[ \t]*([^\n]*?)\s*-->")
SWITCH = re.compile(r"<!--[ \t]*([^\n]*?)[ \t]+(submit|listings):[ \t]*([^\n]*?)\s*-->")
SWITCHES = ["submit", "listings"]


class ReleaseDescriptionError(Exception):
    """Every reason the description cannot be carried out as it is written, one message per problem."""

    def __init__(self, messages):
        super().__init__("\n".join(messages))
        self.messages = messages


def unreadable_instructions(body):
    """Every comment that looks like one of the instructions but is not written the way one is read."""
    messages = []
    for comment in re.finditer(r"<!--.*?-->", body, re.S):
        text = comment.group(0)
        whats_new = WHATS_NEW.fullmatch(text)
        if whats_new:
            tag = whats_new.group(1)
            # A block is named by the app's language alone: each store's script writes it into every locale of that
            # language the store has (fr-FR and fr-CA alike), so a country in the tag would be read as nothing.
            if not re.fullmatch(r"[a-z]{2,3}", tag, re.I):
                language = re.split(r"[-_]", tag)[0]
                messages.append(f"The release has a \"whats-new {tag}\" block; it is named by the language alone, \"whats-new {language}\".")
            continue
        if PRIORITY.fullmatch(text) or SWITCH.fullmatch(text):
            continue
        inner = text[4:-3].strip().lower().replace("_", "-")
        first = inner.split()[0].rstrip(":") if inner.split() else ""
        if first in STORES or first in {"whats-new", "whatsnew"} or "submit" in inner or "listings" in inner \
                or "priority" in inner:
            messages.append(f"The release has a comment I cannot read: \"{text}\". The format is in publish-all.yml's header.")
    return messages


def read(body, languages=None):
    """
    The notes, in English and per language of the app (`languages`, the app's own by default), the update priority, and
    each store's submit and listings values the description asks for, with the warnings about it.

    Raises ReleaseDescriptionError naming every problem at once, so that one edit of the release fixes all of them.
    """
    body = body.replace("\r\n", "\n")
    languages = app_languages() if languages is None else languages
    errors = []
    warnings = []

    # A case slip in the header still reads: whats-new is a word nobody means anything else by. The first block of a
    # language wins, and one in a language the app lacks would be written into no listing at all.
    blocks = {}
    for match in re.finditer(r"<!--[ \t]*whats-new[ \t]+([a-z]{2,3})[ \t]*\n(.*?)-->", body, re.S | re.I):
        code = match.group(1).lower()
        if code not in languages:
            errors.append(f"The release has a \"whats-new {code}\" block, and the app has no such language ({', '.join(languages)}).")
        blocks.setdefault(code, match.group(2).strip())
    notes = blocks.get("en", "")
    if not notes:
        visible = re.sub(r"<!--.*?-->", "", body, flags=re.S)
        visible = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", visible)
        notes = re.sub(r"\*\*|`", "", visible).strip()
    if not notes:
        notes = DEFAULT_NOTES

    errors += unreadable_instructions(body)

    priority = "0"
    priority_found = False
    for words, value in PRIORITY.findall(body):
        if words != "play-store":
            errors.append(f"The release names a store I do not know: \"{words} update-priority: {value}\". Only play-store takes a priority.")
        elif not re.fullmatch(r"[0-5]", value):
            errors.append(f"The release says \"play-store update-priority: {value}\"; it takes a single digit from 0 to 5.")
        elif not priority_found:
            priority, priority_found = value, True

    # A language without a block, or with an empty one, gets the English notes.
    by_language = {code: blocks.get(code) or notes for code in languages}

    # Listings off writes only the notes into that store, for a release made while a listing is reworked in its console.
    switches = {switch: {store: "true" for store in STORES} for switch in SWITCHES}
    seen = set()
    for store, switch, value in SWITCH.findall(body):
        if store not in STORES:
            errors.append(f"The release names a store I do not know: \"{store} {switch}: {value}\". It knows {', '.join(STORES)}.")
        elif value.lower() not in {"true", "false"}:
            errors.append(f"The release says \"{store} {switch}: {value}\"; it takes true or false.")
        elif (store, switch) not in seen:
            switches[switch][store] = value.lower()
            seen.add((store, switch))

    # Characters, not bytes, which is how every one of the stores counts.
    for code in ["en"] + [code for code in languages if code != "en"]:
        text = notes if code == "en" else by_language[code]
        if code != "en" and text == notes:
            continue
        name = "The release notes" if code == "en" else f"The {code} release notes"
        length = len(text)
        for store in ["app-store", "mac-app-store"]:
            if length > APP_STORE_CONNECT_LIMIT:
                errors.append(f"{name} are {length} characters long, and App Store Connect takes {APP_STORE_CONNECT_LIMIT} ({store}).")
        if length > PARTNER_CENTER_LIMIT:
            errors.append(f"{name} are {length} characters long, and Partner Center takes {PARTNER_CENTER_LIMIT} (microsoft-store).")
        if length > PLAY_LIMIT:
            warnings.append(f"{name} are {length} characters long; Play takes {PLAY_LIMIT}, and gets the lines that fit (play-store).")

    if errors:
        raise ReleaseDescriptionError(errors)
    outputs = {
        "release_notes": notes,
        "release_notes_by_language": json.dumps(by_language, ensure_ascii=False),
        "update_priority": priority,
    }
    for switch in SWITCHES:
        for store in STORES:
            outputs[f"{store.replace('-', '_')}_{switch}"] = switches[switch][store]
    return {"outputs": outputs, "warnings": warnings}


def main():
    try:
        result = read(os.environ["RELEASE_BODY"])
    except ReleaseDescriptionError as error:
        for message in error.messages:
            print(f"::error::{message}", file=sys.stderr)
        sys.exit(1)
    for message in result["warnings"]:
        print(f"::warning::{message}")
    outputs = result["outputs"]
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        output.write(f"release_notes<<END_OF_RELEASE_NOTES\n{outputs['release_notes']}\nEND_OF_RELEASE_NOTES\n")
        for name, value in outputs.items():
            if name != "release_notes":
                output.write(f"{name}={value}\n")


if __name__ == "__main__":
    main()
