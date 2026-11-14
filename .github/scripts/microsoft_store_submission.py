# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Submits a new .msix to the Microsoft Store for certification, which is everything an update used to need Partner Center
open for.

    microsoft_store_submission.py <package identity name> <package version> <.msix file> <release notes file>
        [--prepare] [--no-listings] [--screenshots <folder>]

The app is found by its package identity name (campfire.windows.identityName), so no Store ID has to be kept anywhere.
A new submission is a copy of the last published one: this replaces its package with the new one, writes the release
notes as its "What's new in this version" and the listing text from documentation/store-listings/microsoft-store into
every language's listing (store_listings.py; the title and every other field are sent back as they came), replaces the
screenshots when it is given the release's, sets it to be published as soon as it passes certification, uploads the
package and commits it, then waits until Partner Center has accepted the commit (which is where a package that does
not match the product's identity is refused). A green run means submitted, not certified: certification answers by
email, usually within a few days.

The notes are each language's from RELEASE_NOTES_BY_LANGUAGE (release_description.py's JSON), the notes file's for any
language it does not name, and every listing is written by its language (fr-fr and fr-ca alike).

With --screenshots, the folder is the framing's store folder (store/ of the screenshots artifact, store_images.py):
every listing of an app language marks the screenshots it has for deletion and gets the run's in their place, in the
listing's order, their files going into the zip beside the package. The images replace rather than merge, so a shot
taken out of the listing disappears from the Store too; the poster and the box art are set by hand and left as they
are. Without it every image is sent back as it came.

An app language with no listing in Partner Center gets one, at the locale store_listings.py's LOCALES names: a copy of
the English listing, which carries what stays by hand (the title and "Developed by"), with the language's own text,
notes, and screenshots. Partner Center lists a language only for a package that declares it, and the app's .msix
declares every language of the app, so the package this run uploads is what makes room for it. Partner Center takes
no listing without screenshots, so a missing language without the run's images stops the run before anything is
written (a submission the run copied from the last published one stays behind as a draft, which the next run takes
up): the workflow is dispatched again with screenshots_run, the ID of a run of screenshots.yml on this tag or of the
release's run, whose screenshots artifact to use. With --no-listings only the notes are written: a missing listing is
no failure, and the images are left as they are.

With --prepare it writes the release notes, the listings, and the publish mode and leaves the packages and the images
alone, leaving the submission as a draft in Partner Center for somebody to replace the package by hand with the one the
run keeps and submit it there. Neither the package nor the screenshots are uploaded then, because Partner Center only
takes in a file uploaded through the API when the API commits the submission: one sent from Partner Center's own
Submit button goes out with whatever package Partner Center shows, which for a copy is the last release's. So a new
language's listing gets its text there and its screenshots wait for a run that submits. A draft that nobody has changed
in Partner Center can still be taken up and committed by a later run.

A draft this script made and did not get through - one whose commit failed - is used instead of a new copy: its package
is replaced with the new one, its "What's new in this version" with the notes, its listing text with the files', and its
screenshots with the run's (the ones an earlier attempt added and never got in are dropped rather than added twice);
everything else in it is kept exactly as it is, the publish mode included, and it is committed. A draft started in
Partner Center cannot be: the API refuses to change a submission it did not create, and hands out no upload address for
one, so the run stops and says so - that draft is finished in Partner Center with the package this run keeps, or
deleted so that the run can make its own.
A submission that is already past its commit with this very package version is left as it is, so a run that is repeated
does not fail on its own success. One past its commit with anything else - the previous release still in certification
- stops the run instead of being deleted, since the product can only have one submission in progress at a time.

Nothing here signs anything or makes a credential that is later revoked, so nothing a run does can undo a build that is
still in certification: the package is unsigned and Partner Center signs it with a certificate of its own, and the sign-in
is a token that is simply let expire.

Uses the Microsoft Store submission API as a Microsoft Entra application that has the Manager role in Partner Center,
named by MICROSOFT_STORE_TENANT_ID and MICROSOFT_STORE_CLIENT_ID. It proves who it is with no secret at all: the
application trusts the OIDC token GitHub Actions hands a job in this repository's microsoft-store environment (a
federated credential on the application in Entra), so the job needs the id-token: write permission and nothing kept
anywhere expires. Only needs the Python standard library.
"""

import base64
import copy
import json
import os
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile

import store_images
import store_listings
from store_http import REQUEST_TIMEOUT_SECONDS, fail, request_json

API = "https://manage.devcenter.microsoft.com/v1.0/my"
# The most Partner Center takes for "What's new in this version".
RELEASE_NOTES_LIMIT = 1500
# The largest block every version of the blob service accepts, whichever one the upload URL was signed for.
UPLOAD_BLOCK_BYTES = 4 * 1024 * 1024
COMMIT_TIMEOUT_SECONDS = 30 * 60
# Warnings Partner Center attaches to every commit and that ask nothing of a release: the sales resource was retired,
# and every submission copied from one made before that still names it.
IGNORED_COMMIT_WARNINGS = {"SalesUnsupportedWarning"}
POLL_INTERVAL_SECONDS = 30
# The states of a submission that has not been committed yet: a draft started in Partner Center, which the API cannot
# change, or one this script made whose commit Partner Center refused.
DRAFT_STATES = {"PendingCommit", "CommitFailed"}
# The states of a submission that is past its commit and on its way to the Store.
SUBMITTED_STATES = {"PreProcessing", "Certification", "Release", "PendingPublication", "Publishing", "Published"}
_token = {"value": None, "expires": 0}


def github_token():
    """The job's OIDC token from GitHub, which is what the federated credential on the Entra application trusts."""
    url = os.environ.get("ACTIONS_ID_TOKEN_REQUEST_URL")
    if not url:
        fail("GitHub hands out no OIDC token to this job: it needs the id-token: write permission, in the calling "
             "workflow too.")
    url += "&audience=" + urllib.parse.quote("api://AzureADTokenExchange")
    headers = {"Authorization": f"Bearer {os.environ['ACTIONS_ID_TOKEN_REQUEST_TOKEN']}"}
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=REQUEST_TIMEOUT_SECONDS) as response:
        return json.loads(response.read())["value"]


def token():
    """An access token for the API, fetched again a few minutes before it runs out, since one lasts an hour."""
    if time.time() < _token["expires"] - 300:
        return _token["value"]
    tenant = os.environ["MICROSOFT_STORE_TENANT_ID"]
    # GitHub's token is asked for again every time, since it lasts minutes rather than the hour Entra's does.
    body = urllib.parse.urlencode({
        "grant_type": "client_credentials",
        "client_id": os.environ["MICROSOFT_STORE_CLIENT_ID"],
        "client_assertion_type": "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
        "client_assertion": github_token(),
        "scope": "https://manage.devcenter.microsoft.com/.default",
    }).encode()
    try:
        url = f"https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token"
        with urllib.request.urlopen(url, data=body, timeout=REQUEST_TIMEOUT_SECONDS) as response:
            answer = json.loads(response.read())
    except urllib.error.HTTPError as error:
        # AADSTS70021 is a subject nobody trusts: the job is not in the environment the credential names, or the
        # credential's subject is not the one GitHub writes (repo:<owner>/<repository>:environment:<name>).
        fail(f"Microsoft Entra ID refused the GitHub token ({error.code}): {error.read().decode(errors='replace')}\n"
             "The application needs a federated credential with the issuer "
             f"https://token.actions.githubusercontent.com, the subject "
             f"repo:{os.environ.get('GITHUB_REPOSITORY')}:environment:microsoft-store and the audience "
             "api://AzureADTokenExchange.")
    _token["value"] = answer["access_token"]
    _token["expires"] = time.time() + int(answer.get("expires_in", 3600))
    return _token["value"]


def request(method, url, body=None):
    """
    One call of the API. A GET is asked again where the service or the network failed it, since one bad answer among
    the polls that follow the commit would otherwise fail a job whose submission has already gone in.
    """
    url = url if url.startswith("https://") else API + url
    return request_json(method, url, token, body)


def find_app(identity_name):
    url = "/applications?top=100"
    while url:
        page = request("GET", url)
        app = next((item for item in page.get("value", []) if item.get("packageIdentityName") == identity_name), None)
        if app is not None:
            return app
        next_link = page.get("@nextLink")
        url = f"/{next_link}" if next_link else None
    fail(f"There is no app with the package identity name {identity_name} in Partner Center, or the Entra application "
         "cannot see it: it needs the Manager role on the account.")


def pending_submission(app):
    """The submission the app has in progress, or None where there is none."""
    pending = app.get("pendingApplicationSubmission")
    return request("GET", f"/applications/{app['id']}/submissions/{pending['id']}") if pending else None


def package_versions(submission):
    return {package.get("version") for package in submission.get("applicationPackages", [])
            if package.get("fileStatus") != "PendingDelete" and package.get("version")}


def replace_package(submission, package_name):
    """Marks every package of the submission for deletion and names the new one in their place."""
    packages = submission.get("applicationPackages", [])
    for package in packages:
        package["fileStatus"] = "PendingDelete"
    packages.append({
        "fileName": package_name,
        "fileStatus": "PendingUpload",
        # Required by the API and ignored for anything newer than Windows 8.
        "minimumDirectXVersion": "None",
        "minimumSystemRam": "None",
    })
    submission["applicationPackages"] = packages


def matched_locales(submission):
    """
    ({Partner Center locale: the app's language} of the submission's listings, [the app's languages it has none of]).
    A locale of a language the app lacks is left alone.
    """
    listings = submission.get("listings", {})
    if not listings:
        fail("The submission has no Store listing to write the release notes into.")
    matched, missing, unknown = store_listings.match_locales(list(listings), store_listings.app_languages())
    for locale in unknown:
        print(f"::warning::The {locale} listing is in a language the app does not have, so it is left as it is.")
    # Read once, so the next release's step 0 of a field rename shows in the log.
    first = next(iter(listings.values()), {}).get("baseListing", {})
    print(f"Partner Center's listings: {', '.join(listings)}; their fields: {', '.join(sorted(first))}.")
    return matched, missing


def run_screenshots(folder):
    """
    {language: {the file's name in the zip: the image}} of the run's screenshots, in the listing's order, or None
    without a folder. A language the run has no screenshots of maps to an empty dict.
    """
    if folder is None:
        return None
    if not os.path.isdir(folder):
        fail(f"There are no screenshots at {folder}.")
    return {
        code: {f"screenshot-{code}-{path.name}": str(path)
               for path in store_images.images(folder, "microsoft-store", code).get("Screenshot", [])}
        for code in store_listings.app_languages()
    }


def check_new_languages(missing, screenshots, prepare_only):
    """
    Stops the run before anything is written where an app language has no listing and the run cannot create one whole:
    Partner Center takes no listing without screenshots, and takes them only from a run that commits.
    """
    if prepare_only:
        print(f"::warning::Partner Center has no listing in {', '.join(missing)}: it gets its text now, and its "
              "screenshots wait for a run that submits, since Partner Center takes them only from the API's own commit.")
        return
    without = [code for code in missing if not (screenshots or {}).get(code)]
    if without:
        fail(f"Partner Center has no listing in {', '.join(without)}, and it creates none without screenshots, which this "
             "run was not given. Dispatch the workflow again with screenshots_run: the ID of a run of screenshots.yml on "
             "this tag, or of the release's run, whose screenshots artifact to use.")


def add_listings(submission, locales, new_locales, listings, notes):
    """
    Creates a listing for each of the app's languages Partner Center has none of: a copy of the English one, which
    carries what stays by hand (the reserved title and "Developed by"), with the language's own text and notes. Its
    images are left empty for the run's screenshots, since the English ones would show the wrong language.
    """
    english = sorted((locale for locale, code in locales.items() if code == "en"), key=lambda locale: locale != "en-us")
    if not english:
        fail("Partner Center has no English listing to copy into the new languages' listings.")
    for locale, code in new_locales.items():
        base = copy.deepcopy(submission["listings"][english[0]].get("baseListing", {}))
        base.update(listings[code])
        base["releaseNotes"] = notes(code)
        base["images"] = []
        submission["listings"][locale] = {"baseListing": base, "platformOverrides": {}}
        print(f"Created the {locale} listing from the {english[0]} one.")


def replace_screenshots(submission, locales, screenshots):
    """
    Marks every listing's screenshots for deletion and adds the run's of its language in their place, leaving the other
    images as they came. A screenshot still waiting for its upload is one an earlier attempt at this draft added and
    never got in, so it is dropped rather than added twice. Returns {name in the zip: image} of every file named.
    """
    files = {}
    for locale, code in locales.items():
        added = screenshots.get(code, {})
        if not added:
            print(f"::warning::The run has no {code} screenshots, so the {locale} listing keeps the ones it has.")
            continue
        base = submission["listings"][locale].setdefault("baseListing", {})
        images = [image for image in base.get("images", [])
                  if not (image.get("imageType") == "Screenshot" and image.get("fileStatus") == "PendingUpload")]
        for image in images:
            if image.get("imageType") == "Screenshot":
                image["fileStatus"] = "PendingDelete"
        base["images"] = images + [
            {"fileName": name, "fileStatus": "PendingUpload", "imageType": "Screenshot", "description": ""}
            for name in added
        ]
        files.update(added)
        print(f"Replaced the {locale} screenshots with the run's {len(added)}.")
    return files


def write_release_notes(submission, locales, notes):
    """Every listing of a language the app has gets that language's notes rather than keeping the last release's."""
    for locale, code in locales.items():
        submission["listings"][locale].setdefault("baseListing", {})["releaseNotes"] = notes(code)
        print(f"Wrote the release notes for {locale}.")


def write_listings(submission, locales, listings):
    """Writes each language's fields into its listings, leaving every field the files do not name as it came."""
    for locale, code in locales.items():
        base = submission["listings"][locale].setdefault("baseListing", {})
        fields = listings[code]
        for field in fields:
            if field not in base:
                print(f"::warning::Partner Center's {locale} listing has no field {field}; it is written anyway, but "
                      "check in Partner Center that the API still calls it that.")
        print(store_listings.changes(locale, base, fields))
        base.update(fields)


def upload(upload_url, package, images):
    """
    Puts the package and every image ({name in the zip: file}) into a zip at its top level, under the names
    the submission gives them, and uploads it block by block.
    """
    with tempfile.TemporaryDirectory() as directory:
        archive = os.path.join(directory, "submission.zip")
        # Stored rather than compressed: an .msix already is a zip and a PNG is deflated, so deflating them again only
        # costs time.
        with zipfile.ZipFile(archive, "w", zipfile.ZIP_STORED, allowZip64=True) as zip_file:
            zip_file.write(package, os.path.basename(package))
            for name, image in images.items():
                zip_file.write(image, name)
        block_ids = []
        with open(archive, "rb") as file:
            while chunk := file.read(UPLOAD_BLOCK_BYTES):
                block_id = base64.b64encode(f"{len(block_ids):08d}".encode()).decode()
                blob_request(f"{upload_url}&comp=block&blockid={urllib.parse.quote(block_id)}", chunk)
                block_ids.append(block_id)
        block_list = "".join(f"<Latest>{block_id}</Latest>" for block_id in block_ids)
        blob_request(f"{upload_url}&comp=blocklist", f'<?xml version="1.0" encoding="utf-8"?><BlockList>{block_list}</BlockList>'.encode())
        print(f"Uploaded {os.path.basename(package)} and {len(images)} images in {len(block_ids)} blocks.")


def blob_request(url, data, attempts=3):
    for attempt in range(1, attempts + 1):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, data=data, method="PUT")):
                return
        except urllib.error.HTTPError as error:
            if attempt == attempts or error.code < 500:
                fail(f"The upload was refused ({error.code}): {error.read().decode(errors='replace')}")
        except urllib.error.URLError as error:
            if attempt == attempts:
                fail(f"The upload failed: {error.reason}")
        time.sleep(5 * attempt)


def run_url():
    """Where the run's artifacts are, as a clause of a sentence, or nothing outside GitHub Actions."""
    if not os.environ.get("GITHUB_RUN_ID"):
        return ""
    return (f" ({os.environ.get('GITHUB_SERVER_URL', 'https://github.com')}/{os.environ.get('GITHUB_REPOSITORY')}"
            f"/actions/runs/{os.environ['GITHUB_RUN_ID']})")


def wait_for_commit(app_id, submission_id):
    deadline = time.time() + COMMIT_TIMEOUT_SECONDS
    # The status repeats its warnings on every poll, and a warning in the run's summary should be there once.
    reported = set()
    while True:
        answer = request("GET", f"/applications/{app_id}/submissions/{submission_id}/status")
        status = answer.get("status")
        details = answer.get("statusDetails") or {}
        for warning in details.get("warnings", []):
            text = f"{warning.get('code')}: {warning.get('details')}"
            if text in reported:
                continue
            reported.add(text)
            print(text if warning.get("code") in IGNORED_COMMIT_WARNINGS else f"::warning::{text}")
        if status in SUBMITTED_STATES:
            return status
        if status != "CommitStarted":
            errors = "\n".join(f"{error.get('code')}: {error.get('details')}" for error in details.get("errors", []))
            fail(f"Partner Center did not accept the submission ({status}):\n{errors or 'no details given'}\n"
                 "It is left as a draft: fix what Partner Center names in it, and submit it there or run this again.")
        if time.time() > deadline:
            fail(f"The commit was still in progress after {COMMIT_TIMEOUT_SECONDS // 60} minutes; see Partner Center.")
        print("The commit is still in progress, asking again in half a minute.")
        sys.stdout.flush()
        time.sleep(POLL_INTERVAL_SECONDS)


def prepare(app_id, submission, package, has_screenshots):
    """
    Sends the draft without its package and its images: an upload is only taken in by the API's own commit, so Partner
    Center would show the last release's files next to new ones that never arrive.
    """
    request("PUT", f"/applications/{app_id}/submissions/{submission['id']}", submission)
    if has_screenshots:
        print("::warning::The screenshots are left as they are: Partner Center takes them only from a run that submits.")
    print(f"::warning::Prepared submission {submission['id']} without its package: in Partner Center, replace the "
          f"package with {os.path.basename(package)} from this run's campfire-msix artifact{run_url()} and submit it "
          "there.")


def main(identity_name, version, package, notes_file, prepare_only, write_listing_text, screenshots_folder=None):
    with open(notes_file, encoding="utf-8") as file:
        notes = store_listings.notes_by_language(file.read().strip())
    for code in store_listings.app_languages():
        if len(notes(code)) > RELEASE_NOTES_LIMIT:
            fail(f"The {code} release notes are {len(notes(code))} characters long, and Partner Center takes {RELEASE_NOTES_LIMIT}.")
        if not notes(code):
            fail("An update needs release notes for its \"What's new in this version\", and there are none.")
    listings = store_listings.read("microsoft-store")
    screenshots = run_screenshots(screenshots_folder)
    if not os.path.isfile(package):
        fail(f"There is no package at {package}.")
    app = find_app(identity_name)
    name = app.get("primaryName", identity_name)
    submission = pending_submission(app)
    if submission is None:
        submission = request("POST", f"/applications/{app['id']}/submissions")
        print(f"Created submission {submission['id']} for {name}.")
        # A copy of the last published submission keeps that one's publish mode, which may have been a date or a manual
        # release; what a release starts goes out as soon as it passes.
        submission["targetPublishMode"] = "Immediate"
    elif submission.get("status") in DRAFT_STATES:
        # A draft is work on the listing for this release, so only the package, the release notes, and the listing text
        # are replaced and everything else, the screenshots and the publish mode included, is sent back as it came. The
        # whole submission has to be sent, since a PUT replaces it rather than merging.
        print(f"Amending the draft submission {submission['id']} of {name} ({submission.get('status')}, with "
              f"{', '.join(sorted(package_versions(submission))) or 'no package'}).")
    elif submission.get("status") in SUBMITTED_STATES and version in package_versions(submission):
        print(f"Version {version} is already submitted ({submission.get('status')}); nothing to do.")
        return
    else:
        fail(f"The app already has a submission in progress ({submission.get('status')}, with "
             f"{', '.join(sorted(package_versions(submission))) or 'no package'}). The Store takes one at a time: wait "
             "for it to be published, or delete it in Partner Center, and run this again.")
    submission_id = submission["id"]
    # Every submission the API created carries its upload address; one started in Partner Center carries none, and an
    # update of it is refused as being in the state "None", so it is left exactly as it is.
    upload_url = submission.pop("fileUploadUrl", None)
    if not upload_url:
        fail(f"Submission {submission_id} was started in Partner Center, and the submission API cannot change a "
             "submission it did not create. Either finish it there - upload the package this run keeps as its "
             f"campfire-msix artifact ({os.path.basename(package)}), write the release notes and submit it - or delete "
             "it there and run this again, which submits a copy of the last published submission instead.")
    # Before the package, so that a refused listing fails before the upload.
    locales, missing = matched_locales(submission)
    new_locales = {}
    if missing and write_listing_text:
        check_new_languages(missing, screenshots, prepare_only)
        new_locales = store_listings.new_listing_locales("microsoft-store", missing)
    write_release_notes(submission, locales, notes)
    if write_listing_text:
        write_listings(submission, locales, listings)
        add_listings(submission, locales, new_locales, listings, notes)
    elif screenshots is not None:
        print("The listings are not written, so their images are left as they are too.")
    if prepare_only:
        prepare(app["id"], submission, package, screenshots is not None)
        return
    images = {}
    if screenshots is not None and write_listing_text:
        images = replace_screenshots(submission, {**locales, **new_locales}, screenshots)
    replace_package(submission, os.path.basename(package))
    request("PUT", f"/applications/{app['id']}/submissions/{submission_id}", submission)
    upload(upload_url, package, images)
    request("POST", f"/applications/{app['id']}/submissions/{submission_id}/commit")
    status = wait_for_commit(app["id"], submission_id)
    print(f"Submitted version {version} for certification ({status}), to be published "
          f"{'as soon as it passes' if submission.get('targetPublishMode') == 'Immediate' else 'as the submission says (' + str(submission.get('targetPublishMode')) + ')'}.")


def parse_flags(flags):
    """(--prepare, not --no-listings, the --screenshots folder or None), or None where the flags do not read."""
    screenshots = None
    if "--screenshots" in flags:
        index = flags.index("--screenshots")
        if index + 1 >= len(flags) or flags[index + 1].startswith("--"):
            return None
        screenshots = flags[index + 1]
        flags = flags[:index] + flags[index + 2:]
    if not set(flags) <= {"--prepare", "--no-listings"} or len(set(flags)) != len(flags):
        return None
    return "--prepare" in flags, "--no-listings" not in flags, screenshots


if __name__ == "__main__":
    arguments = sys.argv[1:]
    parsed = parse_flags(arguments[4:])
    if len(arguments) < 4 or parsed is None:
        fail(__doc__.strip().split("\n\n")[1].strip())
    main(*arguments[:4], *parsed)
