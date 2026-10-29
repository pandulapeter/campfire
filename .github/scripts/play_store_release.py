# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Publishes a release APK to the production track of the Play Console, with its release notes and the listing text, in
one edit, so that all of it goes to review as one change.

    play_store_release.py <package name> <.apk file> <mapping file> <release name> <update priority> <release notes file>
        [--draft] [--no-listings]

The edit uploads the APK and its mapping file, puts the APK on the production track under the release name with the
in-app update priority (completed, or with --draft a draft to be rolled out from the Play Console), writes the release
notes, and writes the title, short description, and full description of documentation/store-listings/google-play
into every listing (Play keeps the images apart, so they are never touched), then commits it. The notes are each
language's from RELEASE_NOTES_BY_LANGUAGE (release_description.py's JSON), the notes file's for any language it does
not name, each cut to Play's 500 characters at a line boundary. Every listing the app has is written by its language
(store_listings.py: fr-FR and fr-CA alike), and the notes go to the same locales, since Play refuses notes in a
language the listing lacks. An app language with no listing fails the run before anything is uploaded, since a
language's first listing is added by hand in the Play Console, with its screenshots. With --no-listings only the notes
are written, and a missing listing is no failure.

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
    """Sends a file's bytes as they are to one of the API's upload addresses, and returns its JSON answer."""
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


def main(package_name, apk, mapping, release_name, priority, notes_file, draft, write_listings):
    with open(notes_file, encoding="utf-8") as file:
        notes = store_listings.notes_by_language(file.read().strip())
    listings = store_listings.read("google-play")
    for path in [apk, mapping]:
        if not os.path.isfile(path):
            fail(f"There is no file at {path}.")
    edit = request("POST", f"/{package_name}/edits", {})["id"]
    edit_path = f"/{package_name}/edits/{edit}"
    current = {listing["language"]: listing for listing in request("GET", f"{edit_path}/listings").get("listings", [])}
    locales, missing, unknown = store_listings.match_locales(list(current), store_listings.app_languages())
    if missing and write_listings:
        fail(store_listings.missing_listing_message("The Play Console", missing))
    for locale in unknown:
        print(f"::warning::The {locale} listing is in a language the app does not have, so it is left as it is.")

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

    if write_listings:
        for locale, code in locales.items():
            print(store_listings.changes(locale, current[locale], listings[code]))
            request("PATCH", f"{edit_path}/listings/{locale}", {"language": locale, **listings[code]})
    # With a body, since Google's front end refuses a POST without a length.
    request("POST", f"{edit_path}:commit?changesNotSentForReview=false", {})
    print(f"Committed the edit: {release_name} is {'a draft in the Play Console' if draft else 'sent for review'}.")


if __name__ == "__main__":
    arguments = sys.argv[1:]
    flags = arguments[6:]
    if len(arguments) < 6 or not set(flags) <= {"--draft", "--no-listings"} or len(set(flags)) != len(flags) \
            or arguments[4] not in {"0", "1", "2", "3", "4", "5"}:
        fail(__doc__.strip().split("\n\n")[1].strip())
    main(*arguments[:6], "--draft" in flags, "--no-listings" not in flags)
