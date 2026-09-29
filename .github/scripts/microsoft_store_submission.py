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

The app is found by its package identity name (campfire.windows.identityName), so no Store ID has to be kept anywhere.
A new submission is a copy of the last published one: this replaces its package with the new one, writes the release
notes as its "What's new in this version", sets it to be published as soon as it passes certification, uploads the
package and commits it, then waits until Partner Center has accepted the commit (which is where a package that does
not match the product's identity is refused). A green run means submitted, not certified: certification answers by
email, usually within a few days.

A draft that is already there - a submission started in Partner Center for this release, with its new screenshots, or one
whose commit failed - is used instead of a new copy: its package is replaced with the new one and its "What's new in this
version" with the notes, everything else in it is kept exactly as it is, the publish mode included, and it is committed.
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
import json
import os
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile

API = "https://manage.devcenter.microsoft.com/v1.0/my"
# The most Partner Center takes for "What's new in this version".
RELEASE_NOTES_LIMIT = 1500
# The largest block every version of the blob service accepts, whichever one the upload URL was signed for.
UPLOAD_BLOCK_BYTES = 4 * 1024 * 1024
COMMIT_TIMEOUT_SECONDS = 30 * 60
POLL_INTERVAL_SECONDS = 30
# The states of a submission that has not been committed yet, and so can still be changed: a draft started in Partner
# Center, or one whose commit Partner Center refused.
DRAFT_STATES = {"PendingCommit", "CommitFailed"}
# The states of a submission that is past its commit and on its way to the Store.
SUBMITTED_STATES = {"PreProcessing", "Certification", "Release", "PendingPublication", "Publishing", "Published"}

_token = {"value": None, "expires": 0}


def fail(message):
    print(f"::error::{message}", file=sys.stderr)
    sys.exit(1)


def github_token():
    """The job's OIDC token from GitHub, which is what the federated credential on the Entra application trusts."""
    url = os.environ.get("ACTIONS_ID_TOKEN_REQUEST_URL")
    if not url:
        fail("GitHub hands out no OIDC token to this job: it needs the id-token: write permission, in the calling "
             "workflow too.")
    url += "&audience=" + urllib.parse.quote("api://AzureADTokenExchange")
    headers = {"Authorization": f"Bearer {os.environ['ACTIONS_ID_TOKEN_REQUEST_TOKEN']}"}
    with urllib.request.urlopen(urllib.request.Request(url, headers=headers)) as response:
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
        with urllib.request.urlopen(url, data=body) as response:
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
    url = url if url.startswith("https://") else API + url
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Authorization": f"Bearer {token()}", "Content-Type": "application/json"}
    try:
        with urllib.request.urlopen(urllib.request.Request(url, data=data, headers=headers, method=method)) as response:
            content = response.read()
            return json.loads(content) if content else None
    except urllib.error.HTTPError as error:
        fail(f"{method} {url} answered {error.code}: {error.read().decode(errors='replace')}")


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


def write_release_notes(submission, notes):
    listings = submission.get("listings", {})
    if not listings:
        fail("The submission has no Store listing to write the release notes into.")
    # The listing is in English only; any other listing it gains gets the same text rather than the last release's.
    for language, listing in listings.items():
        listing.setdefault("baseListing", {})["releaseNotes"] = notes
        print(f"Wrote the release notes for {language}.")


def upload(upload_url, package):
    """Puts the package into a zip at the top level, the way the submission names it, and uploads it block by block."""
    with tempfile.TemporaryDirectory() as directory:
        archive = os.path.join(directory, "submission.zip")
        # Stored rather than compressed: an .msix already is a zip, and deflating it again only costs time.
        with zipfile.ZipFile(archive, "w", zipfile.ZIP_STORED, allowZip64=True) as zip_file:
            zip_file.write(package, os.path.basename(package))
        block_ids = []
        with open(archive, "rb") as file:
            while chunk := file.read(UPLOAD_BLOCK_BYTES):
                block_id = base64.b64encode(f"{len(block_ids):08d}".encode()).decode()
                blob_request(f"{upload_url}&comp=block&blockid={urllib.parse.quote(block_id)}", chunk)
                block_ids.append(block_id)
        block_list = "".join(f"<Latest>{block_id}</Latest>" for block_id in block_ids)
        blob_request(f"{upload_url}&comp=blocklist", f'<?xml version="1.0" encoding="utf-8"?><BlockList>{block_list}</BlockList>'.encode())
        print(f"Uploaded {os.path.basename(package)} in {len(block_ids)} blocks.")


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


def wait_for_commit(app_id, submission_id):
    deadline = time.time() + COMMIT_TIMEOUT_SECONDS
    while True:
        answer = request("GET", f"/applications/{app_id}/submissions/{submission_id}/status")
        status = answer.get("status")
        details = answer.get("statusDetails") or {}
        for warning in details.get("warnings", []):
            print(f"::warning::{warning.get('code')}: {warning.get('details')}")
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


def main(identity_name, version, package, notes_file):
    with open(notes_file, encoding="utf-8") as file:
        notes = file.read().strip()
    if len(notes) > RELEASE_NOTES_LIMIT:
        fail(f"The release notes are {len(notes)} characters long, and Partner Center takes {RELEASE_NOTES_LIMIT}.")
    if not notes:
        fail("An update needs release notes for its \"What's new in this version\", and there are none.")
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
        # A draft is somebody's work on the listing for this release - its screenshots, its description - so only the
        # package and the release notes are replaced and everything else, the publish mode included, is sent back as
        # it came. The whole submission has to be sent, since a PUT replaces it rather than merging.
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
    upload_url = submission.pop("fileUploadUrl", None)
    if not upload_url:
        fail(f"Partner Center gave no upload address for submission {submission_id}.")
    replace_package(submission, os.path.basename(package))
    write_release_notes(submission, notes)
    request("PUT", f"/applications/{app['id']}/submissions/{submission_id}", submission)
    upload(upload_url, package)
    request("POST", f"/applications/{app['id']}/submissions/{submission_id}/commit")
    status = wait_for_commit(app["id"], submission_id)
    print(f"Submitted version {version} for certification ({status}), to be published "
          f"{'as soon as it passes' if submission.get('targetPublishMode') == 'Immediate' else 'as the submission says (' + str(submission.get('targetPublishMode')) + ')'}.")


if __name__ == "__main__":
    if len(sys.argv) != 5:
        fail(__doc__.strip().split("\n\n")[1].strip())
    main(*sys.argv[1:])
