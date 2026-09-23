# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Submits an uploaded build for App Review, which is everything a release used to need App Store Connect open for.

    app_store_submission.py <bundle identifier> <IOS | MAC_OS> <version> <build number> <release notes file>

It waits for App Store Connect to finish processing the build (processing is also where a build is refused, with the
reason mailed; this fails with the state instead of waiting forever), takes the platform's version for the release -
the one that already carries that version string, or else the editable one, renamed, or else a new one - sets it to
be released as soon as it is approved, attaches the build, writes the release notes as its "What's New" and submits
it. A version that is already waiting for review or further along with this very build is left as it is, so a run
that is repeated does not fail on its own success; one that is there with another build is an error, since only one
version of a platform can be in review at a time.

Uses the App Store Connect API the way app_store_signing.py does, and the same environment.
"""

import sys
import time

from app_store_signing import fail, request

# The states in which a version still takes a build, notes and a submission.
EDITABLE_STATES = {"PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED", "METADATA_REJECTED", "INVALID_BINARY"}
PROCESSING_TIMEOUT_SECONDS = 90 * 60
POLL_INTERVAL_SECONDS = 60
# The most App Store Connect takes for "What's New".
WHATS_NEW_LIMIT = 4000


def find_app(bundle_identifier):
    apps = request("GET", f"/apps?filter[bundleId]={bundle_identifier}")["data"]
    app = next((item for item in apps if item["attributes"]["bundleId"] == bundle_identifier), None)
    if app is None:
        fail(f"There is no app with the bundle ID {bundle_identifier} in App Store Connect.")
    return app


def wait_for_build(app_id, platform, version, build_number):
    """The build once it has been processed; a build App Store Connect does not list yet is still being received."""
    path = (
        f"/builds?filter[app]={app_id}&filter[version]={build_number}"
        f"&filter[preReleaseVersion.version]={version}&filter[preReleaseVersion.platform]={platform}"
        "&fields[builds]=version,processingState"
    )
    deadline = time.time() + PROCESSING_TIMEOUT_SECONDS
    while True:
        builds = request("GET", path)["data"]
        state = builds[0]["attributes"]["processingState"] if builds else "NOT_LISTED_YET"
        if state == "VALID":
            print(f"Build {version} ({build_number}) has been processed.")
            return builds[0]
        if state in {"FAILED", "INVALID"}:
            fail(f"App Store Connect refused build {version} ({build_number}): {state}. The reason is in the mail it sent.")
        if time.time() > deadline:
            fail(f"Build {version} ({build_number}) was still {state} after {PROCESSING_TIMEOUT_SECONDS // 60} minutes.")
        print(f"Build {version} ({build_number}) is {state}, asking again in a minute.")
        sys.stdout.flush()
        time.sleep(POLL_INTERVAL_SECONDS)


def version_for_release(app_id, platform, version):
    """The platform's version to submit, and whether an earlier one was ever released, which decides "What's New"."""
    versions = request(
        "GET", f"/apps/{app_id}/appStoreVersions?filter[platform]={platform}&limit=50&include=build"
        "&fields[appStoreVersions]=versionString,appStoreState,releaseType,build",
    )["data"]
    has_earlier = any(
        item["attributes"]["versionString"] != version and item["attributes"]["appStoreState"] not in EDITABLE_STATES
        for item in versions
    )
    existing = next((item for item in versions if item["attributes"]["versionString"] == version), None)
    if existing is None:
        existing = next((item for item in versions if item["attributes"]["appStoreState"] in EDITABLE_STATES), None)
    attributes = {"releaseType": "AFTER_APPROVAL"}
    if existing is None:
        created = request("POST", "/appStoreVersions", {"data": {
            "type": "appStoreVersions",
            "attributes": {"platform": platform, "versionString": version, **attributes},
            "relationships": {"app": {"data": {"type": "apps", "id": app_id}}},
        }})["data"]
        print(f"Created the {platform} version {version}.")
        return created, has_earlier
    if existing["attributes"]["appStoreState"] in EDITABLE_STATES:
        if existing["attributes"]["versionString"] != version:
            print(f"Renaming the editable {platform} version {existing['attributes']['versionString']} to {version}.")
            attributes["versionString"] = version
        request("PATCH", f"/appStoreVersions/{existing['id']}", {"data": {
            "type": "appStoreVersions", "id": existing["id"], "attributes": attributes,
        }})
    return existing, has_earlier


def attached_build_id(version):
    build = request("GET", f"/appStoreVersions/{version['id']}/relationships/build")["data"]
    return build["id"] if build else None


def write_whats_new(version, notes):
    localizations = request("GET", f"/appStoreVersions/{version['id']}/appStoreVersionLocalizations")["data"]
    if not localizations:
        fail("The version has no localizations to write the release notes into.")
    # The listing is in English only; any other localization it gains gets the same text rather than an old one.
    for localization in localizations:
        request("PATCH", f"/appStoreVersionLocalizations/{localization['id']}", {"data": {
            "type": "appStoreVersionLocalizations", "id": localization["id"], "attributes": {"whatsNew": notes},
        }})
        print(f"Wrote the release notes for {localization['attributes']['locale']}.")


def submit(app_id, platform, version):
    """Adds the version to the platform's open review submission, or to a new one, and submits it."""
    open_submissions = request(
        "GET", f"/reviewSubmissions?filter[app]={app_id}&filter[platform]={platform}&filter[state]=READY_FOR_REVIEW",
    )["data"]
    if open_submissions:
        submission = open_submissions[0]
    else:
        submission = request("POST", "/reviewSubmissions", {"data": {
            "type": "reviewSubmissions",
            "attributes": {"platform": platform},
            "relationships": {"app": {"data": {"type": "apps", "id": app_id}}},
        }})["data"]
    items = request("GET", f"/reviewSubmissions/{submission['id']}/items?include=appStoreVersion")["data"]
    listed = any(
        (item["relationships"].get("appStoreVersion", {}).get("data") or {}).get("id") == version["id"] for item in items
    )
    if not listed:
        request("POST", "/reviewSubmissionItems", {"data": {
            "type": "reviewSubmissionItems",
            "relationships": {
                "reviewSubmission": {"data": {"type": "reviewSubmissions", "id": submission["id"]}},
                "appStoreVersion": {"data": {"type": "appStoreVersions", "id": version["id"]}},
            },
        }})
    request("PATCH", f"/reviewSubmissions/{submission['id']}", {"data": {
        "type": "reviewSubmissions", "id": submission["id"], "attributes": {"submitted": True},
    }})


def main(bundle_identifier, platform, version, build_number, notes_file):
    with open(notes_file) as file:
        notes = file.read().strip()
    if len(notes) > WHATS_NEW_LIMIT:
        fail(f"The release notes are {len(notes)} characters long, and App Store Connect takes {WHATS_NEW_LIMIT}.")
    app = find_app(bundle_identifier)
    build = wait_for_build(app["id"], platform, version, build_number)
    store_version, has_earlier = version_for_release(app["id"], platform, version)
    state = store_version["attributes"]["appStoreState"]
    if state not in EDITABLE_STATES:
        if attached_build_id(store_version) == build["id"]:
            print(f"The {platform} version {version} is already {state} with build {build_number}; nothing to do.")
            return
        fail(f"The {platform} version {version} is already {state} with another build. Only one version of a platform can "
             "be in review at a time: submit this one by hand once that has been decided.")
    request("PATCH", f"/appStoreVersions/{store_version['id']}/relationships/build", {
        "data": {"type": "builds", "id": build["id"]},
    })
    print(f"Attached build {build_number} to the {platform} version {version}.")
    # The first version of a platform has nothing to say "new" about, and App Store Connect refuses the field there.
    if has_earlier:
        if not notes:
            fail("An update needs release notes for its \"What's New\", and there are none.")
        write_whats_new(store_version, notes)
    submit(app["id"], platform, store_version)
    print(f"Submitted the {platform} version {version} ({build_number}) for review. It is released as soon as it is approved.")


if __name__ == "__main__":
    if len(sys.argv) != 6 or sys.argv[2] not in {"IOS", "MAC_OS"}:
        fail(__doc__.strip().split("\n\n")[1].strip())
    main(*sys.argv[1:])
