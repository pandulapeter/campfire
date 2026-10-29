# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Reads the store listings in documentation/store-listings for the store scripts, which write them with every release.

    store_listings.py --check

Each store has a folder holding a file per language of the app (`app-store/en.md`, `app-store/de.md`), and a field is
a `## <name> (<limit>)` heading over its fenced blocks, the way check_store_listings.py reads it. What a store's API
calls each heading is FIELDS; a heading written by hand in the console instead is MANUAL. Everything here is keyed by
the app's language (`fr`), never by a store's code for it: each store script asks the store which locales the listing
has and writes a language's text into every one whose language part is that language (match_locales), so Play's
`fr-FR` and `fr-CA` both get `fr`, and a regional copy added in a console is filled on the next release with no change
here. A language's first listing is added by hand in every console, with its screenshots; a store script fails before
uploading anything where one is missing, rather than publish a listing without them.

With --check (run by tests.yml): every store folder holds exactly the app's languages, every heading of every file is
a field this knows, and every field a store writes is in `en.md` and, unless it is English only, in every language's
file. Prints one line per failure and exits 1, or prints nothing and exits 0.
"""

import json
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LISTINGS = ROOT / "documentation/store-listings"
RESOURCES = ROOT / "presentation/src/commonMain/composeResources"

STORES = ["google-play", "app-store", "mac-app-store", "microsoft-store"]

HEADING = re.compile(r"^## (.+?)\s*(?:\(([^)]*)\))?\s*$")
BLOCK = re.compile(r"^```[^\n]*\n(.*?)\n?^```", re.M | re.S)

# Heading -> the store API's name for the field. App Store Connect keeps them on four resources, which
# app_store_submission.py tells apart by the name.
FIELDS = {
    "google-play": {
        "App title": "title",
        "Short description": "shortDescription",
        "Full description": "fullDescription",
    },
    "app-store": {
        "Name": "name",
        "Subtitle": "subtitle",
        "Promotional text": "promotionalText",
        "Description": "description",
        "Keywords": "keywords",
        "Support URL": "supportUrl",
        "Marketing URL": "marketingUrl",
        "Copyright": "copyright",
        "App Review Information: Notes": "notes",
    },
    "mac-app-store": {
        "Promotional text": "promotionalText",
        "Description": "description",
        "Keywords": "keywords",
        "App Review Information: Notes": "notes",
    },
    "microsoft-store": {
        "Short description": "shortDescription",
        "Description": "description",
        "Product features": "features",
        "Search terms": "keywords",
        "Copyright and trademark info": "copyrightAndTrademarkInfo",
    },
}
# Headings pasted by hand: the Mac's sandbox information is not in App Store Connect's public API, the Microsoft title
# is the name reserved for the product, and "Developed by" is set once.
MANUAL = {
    "google-play": set(),
    "app-store": set(),
    "mac-app-store": {"App Sandbox Information"},
    "microsoft-store": {"Title", "Developed by"},
}
# Read from en.md and written into every locale.
ENGLISH_ONLY = {"Support URL", "Marketing URL", "Copyright", "App Review Information: Notes"}
# A field of several blocks, one per item.
ITEMS = {"Product features"}
# A field of one block of comma-separated items.
TERMS = {"Search terms"}

# A store's code for a language that is not the language's ISO 639-1 code: Play still says iw for Hebrew.
ALIASES = {"iw": "he"}


def sections(text):
    """Every `## ` heading of the text with what lies between it and the next one."""
    parts = re.split(r"^(?=## )", text, flags=re.M)
    for part in parts:
        if part.startswith("## "):
            heading, _, body = part.partition("\n")
            yield heading, body


def field_name(heading):
    """The field a heading names, without its limit: `## Description (4,000 characters max)` is `Description`."""
    return HEADING.match(heading).group(1)


def parse(text):
    """{field: [block, …]} of one listing file."""
    return {field_name(heading): BLOCK.findall(body) for heading, body in sections(text)}


def value(field, blocks):
    if field in ITEMS:
        return list(blocks)
    if field in TERMS:
        return [term.strip() for term in blocks[0].split(",") if term.strip()] if blocks else []
    return blocks[0] if blocks else ""


def read(store, folder=LISTINGS):
    """
    {language: {API field: text, or a list for a field of items}} of the store's listing files, the fields the store
    writes only, the English-only ones copied into every language.
    """
    files = {path.stem: parse(path.read_text(encoding="utf-8")) for path in sorted((folder / store).glob("*.md"))}
    english = files["en"]
    listings = {}
    for language, fields in files.items():
        listings[language] = {
            api: value(heading, (english if heading in ENGLISH_ONLY else fields).get(heading, []))
            for heading, api in FIELDS[store].items()
        }
    return listings


def app_languages(resources=RESOURCES):
    """The app's languages, from its `values-*` folders; `values` is English."""
    languages = []
    for path in resources.iterdir():
        if path.is_dir() and path.name == "values":
            languages.append("en")
        elif path.is_dir() and path.name.startswith("values-"):
            languages.append(path.name[len("values-"):])
    return sorted(languages)


def language(locale):
    """The app's language of a store's locale: `fr-CA`, `ro_RO`, and `FR` are all `fr`."""
    code = re.split(r"[-_]", locale)[0].lower()
    return ALIASES.get(code, code)


def match_locales(locales, languages):
    """
    ({locale: language} for every store locale of a language of the app, [the app's languages the store has no locale
    for], [the store's locales of a language the app lacks]).
    """
    matched = {locale: language(locale) for locale in locales if language(locale) in languages}
    missing = [code for code in languages if code not in matched.values()]
    unknown = [locale for locale in locales if locale not in matched]
    return matched, missing, unknown


def missing_listing_message(store_name, missing):
    return (f"{store_name} has no listing in {', '.join(missing)}. A language's first listing is added by hand, once, with "
            "its screenshots; add it there and run this again, or release with listings off for this store.")


def size(text):
    return sum(len(item) for item in text) if isinstance(text, list) else len(text or "")


def changes(locale, old, new):
    """One line naming the fields of a locale that change, with their old and new lengths rather than the text."""
    changed = [f"{field} {size(old.get(field))} -> {size(text)}" for field, text in new.items() if old.get(field) != text]
    return f"{locale}: {', '.join(changed) if changed else 'nothing changed'}."


def notes_by_language(english):
    """
    The release notes per language from RELEASE_NOTES_BY_LANGUAGE (release_description.py's JSON), English for any
    language it does not name, and for a hand dispatch, which passes none.
    """
    by_language = json.loads(os.environ.get("RELEASE_NOTES_BY_LANGUAGE") or "{}")
    return lambda code: by_language.get(code) or english


def check(folder=LISTINGS, resources=RESOURCES):
    errors = []
    languages = app_languages(resources)
    for store in STORES:
        files = {path.stem: path for path in (folder / store).glob("*.md")}
        if sorted(files) != languages:
            errors.append(f"{store}: has {', '.join(sorted(files))}, and the app has {', '.join(languages)}")
        known = set(FIELDS[store]) | MANUAL[store]
        for code, path in sorted(files.items()):
            fields = parse(path.read_text(encoding="utf-8"))
            name = f"{store}/{code}.md"
            for field in fields:
                if field not in known:
                    errors.append(f"{name}: \"{field}\" is neither written by store_listings.py's FIELDS nor listed as MANUAL")
            for field in FIELDS[store]:
                if code != "en" and field in ENGLISH_ONLY:
                    continue
                if not fields.get(field):
                    errors.append(f"{name}: has no \"{field}\", which the release writes")
    return errors


def main():
    if sys.argv[1:] != ["--check"]:
        print(__doc__.strip().split("\n\n")[1].strip(), file=sys.stderr)
        return 2
    errors = check()
    for error in errors:
        print(error)
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
