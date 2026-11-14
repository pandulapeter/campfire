# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Publishes a release APK to the production track of the Play Console, with its release notes, the listing text, and
optionally the listing images, in one edit, so that all of it goes to review as one change.

    play_store_release.py <package name> <.apk file> <mapping file> <release name> <update priority> <release notes file>
        [--draft] [--no-listings] [--screenshots <folder>]

The edit writes the title, short description, and full description of documentation/store-listings/google-play into
every listing, replaces the images (with --screenshots), uploads the APK and its mapping file, puts the APK on the
production track under the release name with the in-app update priority (completed, or with --draft a draft to be
rolled out from the Play Console), writes the release notes, then commits it. Nothing reaches the store before the
commit, but the listings and images go first so that a failure among them stops the run before the APK is sent.

Every listing the app has is written by its language (store_listings.py: fr-FR and fr-CA alike). An app language the
listings lack gets its listing created, at the locale store_listings.py's LOCALES names, since an update of a listing
Play does not have creates it; that comes before the notes, which Play refuses in a language the listing lacks. Play
shows the default language's images in a listing without its own, so a new language needs no --screenshots, though
the run warns that it will look English until it has them.

The notes are each language's from RELEASE_NOTES_BY_LANGUAGE (release_description.py's JSON), the notes file's for any
language it does not name, each cut to Play's 500 characters at a line boundary, and go to every locale written.

--screenshots names the store folder the screenshot tool's framing wrote (store_images.py, which has already checked
the images). For every locale written, each set it has images for in that locale's language is emptied and refilled
in the listing's order, so a shot taken out of the listing leaves the store too, and running it again ends the same.
A set with no images is left alone. The Chromebook screenshots are not among them: Play's API has no image type for
them, so they are still uploaded by hand. Without --screenshots no image is touched.

With --no-listings only the notes are written: no listing text, no images (they follow the listings, whatever
--screenshots says), and an app language with no listing is skipped rather than created.

Signs in as the service account PLAY_SERVICE_ACCOUNT_JSON holds (its JSON key), with a token for a JWT signed by
openssl, so that only the Python standard library is needed.
"""

import base64
import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

import store_images
import store_listings
from store_http import fail, request_json

API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
UPLOAD = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"
# The most Play takes for a release's notes in one language; it refuses the whole release over it.
NOTES_LIMIT = 500
# An APK takes longer to send than any JSON call is allowed.
UPLOAD_TIMEOUT_SECONDS = 15 * 60
_token = {"value": None, "expires": 0}


def base64url(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def signed_jwt(account, now):
    """The service account's assertion for Google's token endpoint, signed with its key (RS256) by openssl."""
    header = base64url(json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
    claims = base64url(json.dumps({
        "iss": account["client_email"], "scope": SCOPE, "aud": account["token_uri"], "iat": now, "exp": now + 3600,
    }).encode())
    signing_input = f"{header}.{claims}".encode()
    with tempfile.NamedTemporaryFile("w", suffix=".pem", delete=False) as key:
        key.write(account["private_key"])
    try:
        signature = subprocess.run(
            ["openssl", "dgst", "-sha256", "-sign", key.name], input=signing_input, capture_output=True, check=True,
        ).stdout
    finally:
        os.remove(key.name)
    return f"{header}.{claims}.{base64url(signature)}"


def token():
    """An access token for the API, fetched again a few minutes before it runs out, since one lasts an hour."""
    if time.time() < _token["expires"] - 300:
        return _token["value"]
    account = json.loads(os.environ["PLAY_SERVICE_ACCOUNT_JSON"])
    body = urllib.parse.urlencode({
        "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer", "assertion": signed_jwt(account, int(time.time())),
    }).encode()
    try:
        with urllib.request.urlopen(account["token_uri"], data=body, timeout=60) as response:
            answer = json.loads(response.read())
    except urllib.error.HTTPError as error:
        fail(f"Google refused the service account ({error.code}): {error.read().decode(errors='replace')}")
    _token["value"] = answer["access_token"]
    _token["expires"] = time.time() + int(answer.get("expires_in", 3600))
    return _token["value"]


def request(method, path, body=None):
    return request_json(method, API + path, token, body, label=path)


def upload(path, file, content_type):
    """
    Sends a file's bytes as they are to one of the API's upload addresses (an APK, its mapping file, or a listing's
    image), and returns its JSON answer.
    """
    with open(file, "rb") as data:
        call = urllib.request.Request(UPLOAD + path + "?uploadType=media", data=data.read(), method="POST", headers={
            "Authorization": f"Bearer {token()}", "Content-Type": content_type,
        })
    try:
        with urllib.request.urlopen(call, timeout=UPLOAD_TIMEOUT_SECONDS) as response:
            return json.loads(response.read())
    except urllib.error.HTTPError as error:
        fail(f"POST {path} answered {error.code}: {error.read().decode(errors='replace')}")
    except OSError as error:
        fail(f"POST {path} failed: {error}")


def cut(text, limit=NOTES_LIMIT):
    """
    The notes cut to the lines that fit Play's limit, rather than mid-sentence, and whether anything was cut.
    Characters, not bytes, are what Play counts.
    """
    lines = text.splitlines()
    kept, total = [], 0
    for line in lines:
        total += len(line) + 1
        if total > limit + 1:
            break
        kept.append(line)
    if not kept and lines:
        # A first line over the limit on its own, which a paragraph of the visible description can be, is cut at the
        # last word that fits rather than leaving the listing with nothing to say.
        first = lines[0][:limit - 1]
        kept = [first.rsplit(" ", 1)[0] if " " in first else first]
    result = "\n".join(kept)
    return result, result != "\n".join(lines)


def replace_images(edit_path, locale, sets):
    """Empties each of a listing's image sets the run has images for, then uploads them in the listing's order."""
    for kind, paths in sets.items():
        request("DELETE", f"{edit_path}/listings/{locale}/{kind}")
        for path in paths:
            upload(f"{edit_path}/listings/{locale}/{kind}", path, "image/png")
        print(f"{locale}: replaced {kind} with {len(paths)} image{'' if len(paths) == 1 else 's'}.")


def main(package_name, apk, mapping, release_name, priority, notes_file, draft, write_listings, screenshots=None):
    with open(notes_file, encoding="utf-8") as file:
        notes = store_listings.notes_by_language(file.read().strip())
    listings = store_listings.read("google-play")
    for path in [apk, mapping]:
        if not os.path.isfile(path):
            fail(f"There is no file at {path}.")
    if screenshots is not None and not os.path.isdir(screenshots):
        fail(f"There is no folder at {screenshots}.")
    edit = request("POST", f"/{package_name}/edits", {})["id"]
    edit_path = f"/{package_name}/edits/{edit}"
    current = {listing["language"]: listing for listing in request("GET", f"{edit_path}/listings").get("listings", [])}
    locales, missing, unknown = store_listings.match_locales(list(current), store_listings.app_languages())
    for locale in unknown:
        print(f"::warning::The {locale} listing is in a language the app does not have, so it is left as it is.")

    if write_listings:
        created = store_listings.new_listing_locales("google-play", missing)
        for locale, code in created.items():
            # An update of a listing Play does not have creates it, and must come before the notes in its language.
            request("PUT", f"{edit_path}/listings/{locale}", {"language": locale, **listings[code]})
            print(f"{locale}: created the listing.")
            if screenshots is None:
                print(f"::warning::The new {locale} listing shows the default language's images until a release "
                      f"with screenshots gives it its own.")
        for locale, code in locales.items():
            print(store_listings.changes(locale, current[locale], listings[code]))
            request("PATCH", f"{edit_path}/listings/{locale}", {"language": locale, **listings[code]})
        locales = {**locales, **created}
        if screenshots is not None:
            for locale, code in locales.items():
                sets = store_images.images(screenshots, "google-play", code)
                if not sets:
                    print(f"::warning::{screenshots} has no Play images in {code}, so the {locale} images are left as "
                          f"they are.")
                replace_images(edit_path, locale, sets)
    elif missing:
        print(f"::warning::Play has no listing in {', '.join(missing)}, and with the listings off none is created, so "
              f"those languages get no release notes.")

    version_code = upload(f"{edit_path}/apks", apk, "application/vnd.android.package-archive")["versionCode"]
    print(f"Uploaded {os.path.basename(apk)} as version code {version_code}.")
    upload(f"{edit_path}/apks/{version_code}/deobfuscationFiles/proguard", mapping, "application/octet-stream")
    print(f"Uploaded {os.path.basename(mapping)}.")

    release_notes = []
    for locale, code in locales.items():
        text, was_cut = cut(notes(code))
        if was_cut:
            print(f"::warning::The {locale} release notes were cut to {NOTES_LIMIT} characters.")
        release_notes.append({"language": locale, "text": text})
    request("PUT", f"{edit_path}/tracks/production", {"track": "production", "releases": [{
        "name": release_name,
        "versionCodes": [str(version_code)],
        # A draft is rolled out from the Play Console once whatever the release needs there has been done by hand.
        "status": "draft" if draft else "completed",
        # What the in-app update gate in :presentation reads; see the Updates section of CLAUDE.md for the thresholds.
        "inAppUpdatePriority": int(priority),
        "releaseNotes": release_notes,
    }]})
    print(f"Put {release_name} on the production track ({'a draft' if draft else 'completed'}, update priority "
          f"{priority}) with notes in {', '.join(locales)}.")

    # With a body, since Google's front end refuses a POST without a length.
    request("POST", f"{edit_path}:commit?changesNotSentForReview=false", {})
    print(f"Committed the edit: {release_name} is {'a draft in the Play Console' if draft else 'sent for review'}.")


def arguments_of(arguments):
    """main's arguments from the command line's, or None where they do not read as the usage line says."""
    if len(arguments) < 6 or arguments[4] not in {"0", "1", "2", "3", "4", "5"}:
        return None
    flags, screenshots, rest = set(), None, arguments[6:]
    while rest:
        flag, rest = rest[0], rest[1:]
        if flag in flags or flag not in {"--draft", "--no-listings", "--screenshots"}:
            return None
        flags.add(flag)
        if flag == "--screenshots":
            if not rest or rest[0].startswith("--"):
                return None
            screenshots, rest = rest[0], rest[1:]
    return [*arguments[:6], "--draft" in flags, "--no-listings" not in flags, screenshots]


if __name__ == "__main__":
    parsed = arguments_of(sys.argv[1:])
    if parsed is None:
        fail(__doc__.strip().split("\n\n")[1].strip())
    main(*parsed)
