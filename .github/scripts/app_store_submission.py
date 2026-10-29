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
        [--prepare] [--no-listings]

It waits for App Store Connect to finish processing the build (processing is also where a build is refused, with the
reason mailed; this fails with the state instead of waiting forever), takes the platform's version for the release -
the one that already carries that version string, or else the editable one, renamed, or else a new one, set to be
released as soon as it is approved - attaches the build, writes the release notes as its "What's New" and the listing
text of documentation/store-listings/<app-store | mac-app-store> into it, and submits it. With --prepare it stops before the submission, leaving the version ready in App Store Connect for somebody to add the
release's screenshots to and submit it there; a later run with the same build submits it as it is.

A draft that is already there - a version prepared in App Store Connect for this release, with its new screenshots,
whether or not it has been added to a review submission, or one that was rejected - is used as it is: the build is
attached to it and its "What's New" replaced with the notes, everything else in it is kept exactly as it is, the release
option included, and it is submitted, in the review submission it is already in where there is one.

The notes are each language's from RELEASE_NOTES_BY_LANGUAGE (release_description.py's JSON), the notes file's for any
language it does not name. The listing is written by language (store_listings.py): every version localization gets its
language's description, keywords, promotional text, and, for iOS, the URLs (fr-FR and fr-CA alike), the version its
copyright and App Review notes, and on iOS each app info localization its name and subtitle, which the Mac shares with
iOS and never writes. An app language with no localization fails the run before any text is written or the version
submitted (only the build is attached by then), since a language's first listing is added by hand in App Store Connect, with its screenshots; a name another app holds fails it
with Apple's message, before the submission. When no app info is editable (a version in review) the name and subtitle
wait for the next version, with a warning. With --no-listings only the notes are written.

A version that is already waiting for review or further along with this very build is left as it is, so a run that is
repeated does not fail on its own success. Where another build is waiting for Apple - under review, or approved
and not on the store yet - this one is not submitted at all, since a platform takes one version at a time: it stays in
TestFlight, the run says so with a warning rather than failing, and it is submitted by hand once the other one has been
decided, or replaced by the next release's build. That is decided before waiting for processing, which a build that is
not going to be submitted has no reason to wait for.

Uses the App Store Connect API the way app_store_signing.py does, and the same environment.
"""

import sys
import time

import store_listings
from app_store_signing import request
from store_http import fail

# The states in which a version still takes a build, notes and a submission. READY_FOR_REVIEW is a version added to a
# review submission that has not been submitted yet, which is what "Add for Review" in App Store Connect leaves behind.
EDITABLE_STATES = {
    "PREPARE_FOR_SUBMISSION", "READY_FOR_REVIEW", "DEVELOPER_REJECTED", "REJECTED", "METADATA_REJECTED", "INVALID_BINARY",
}
# The states of a version that has been submitted and is not on the store yet: the platform takes no other version until
# it has been decided, and an approved one counts until it is on the store.
AWAITING_STATES = {
    "WAITING_FOR_REVIEW", "IN_REVIEW", "WAITING_FOR_EXPORT_COMPLIANCE", "PENDING_CONTRACT", "ACCEPTED",
    "PROCESSING_FOR_APP_STORE", "PENDING_APPLE_RELEASE", "PENDING_DEVELOPER_RELEASE",
}
# The states of a review submission that has not been handed to App Review, or has been handed back with a rejection:
# the platform has at most one of them, and a new one cannot be started next to it.
OPEN_SUBMISSION_STATES = "READY_FOR_REVIEW,UNRESOLVED_ISSUES"
PROCESSING_TIMEOUT_SECONDS = 90 * 60
POLL_INTERVAL_SECONDS = 60
# The most App Store Connect takes for "What's New".
WHATS_NEW_LIMIT = 4000
# Where App Store Connect keeps each field of store_listings.FIELDS: the version localization, the app info
# localization, the version, and its review detail.
LOCALIZATION_FIELDS = {"description", "keywords", "promotionalText", "supportUrl", "marketingUrl"}
APP_INFO_FIELDS = {"name", "subtitle"}
VERSION_FIELDS = {"copyright"}
REVIEW_FIELDS = {"notes"}


def awaiting_versions(app_id, platform):
    """The platform's versions that have been submitted and are not on the store yet."""
    versions = request(
        "GET", f"/apps/{app_id}/appStoreVersions?filter[platform]={platform}&limit=50"
        "&fields[appStoreVersions]=versionString,appStoreState",
    )["data"]
    return [item for item in versions if item["attributes"]["appStoreState"] in AWAITING_STATES]


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
    if existing is None:
        created = request("POST", "/appStoreVersions", {"data": {
            "type": "appStoreVersions",
            "attributes": {"platform": platform, "versionString": version, "releaseType": "AFTER_APPROVAL"},
            "relationships": {"app": {"data": {"type": "apps", "id": app_id}}},
        }})["data"]
        print(f"Created the {platform} version {version}.")
        return created, has_earlier
    # A draft keeps its release option: whoever prepared it in App Store Connect may have chosen to release it by hand.
    if existing["attributes"]["appStoreState"] in EDITABLE_STATES and existing["attributes"]["versionString"] != version:
        print(f"Renaming the editable {platform} version {existing['attributes']['versionString']} to {version}.")
        request("PATCH", f"/appStoreVersions/{existing['id']}", {"data": {
            "type": "appStoreVersions", "id": existing["id"], "attributes": {"versionString": version},
        }})
    return existing, has_earlier


def attached_build_id(version):
    build = request("GET", f"/appStoreVersions/{version['id']}/relationships/build")["data"]
    return build["id"] if build else None


def attached_build_number(version):
    build = request("GET", f"/appStoreVersions/{version['id']}/build?fields[builds]=version", missing_ok=True)
    return ((build or {}).get("data") or {}).get("attributes", {}).get("version")


def localized(items, write_listings, where):
    """
    [(item, the app's language)] of App Store Connect's localizations, failing where an app language has none and the
    listing is written; a localization of a language the app lacks is left alone.
    """
    if not items:
        fail(f"The {where} has no localizations to write into.")
    by_locale = {item["attributes"]["locale"]: item for item in items}
    matched, missing, unknown = store_listings.match_locales(list(by_locale), store_listings.app_languages())
    if missing and write_listings:
        fail(store_listings.missing_listing_message(f"App Store Connect's {where}", missing))
    for locale in unknown:
        print(f"::warning::The {where}'s {locale} localization is in a language the app does not have, so it is left as it is.")
    return [(by_locale[locale], code) for locale, code in matched.items()]


def editable_app_info(app_id):
    """The app info a new version can change, or None while every one is in review or live."""
    infos = request("GET", f"/apps/{app_id}/appInfos")["data"]
    return next((
        info for info in infos
        if (info["attributes"].get("state") or info["attributes"].get("appStoreState")) in EDITABLE_STATES
    ), None)


def write_localizations(path, kind, localizations, attributes_of):
    for localization, code in localizations:
        attributes = attributes_of(code)
        print(store_listings.changes(localization["attributes"]["locale"], localization["attributes"], attributes))
        request("PATCH", f"/{path}/{localization['id']}", {"data": {
            "type": kind, "id": localization["id"], "attributes": attributes,
        }})


def write_review_notes(version, notes):
    detail = (request("GET", f"/appStoreVersions/{version['id']}/appStoreReviewDetail", missing_ok=True) or {}).get("data")
    if detail is None:
        request("POST", "/appStoreReviewDetails", {"data": {
            "type": "appStoreReviewDetails", "attributes": {"notes": notes},
            "relationships": {"appStoreVersion": {"data": {"type": "appStoreVersions", "id": version["id"]}}},
        }})
        print(f"Wrote the App Review notes ({len(notes)} characters).")
    elif detail["attributes"].get("notes") != notes:
        request("PATCH", f"/appStoreReviewDetails/{detail['id']}", {"data": {
            "type": "appStoreReviewDetails", "id": detail["id"], "attributes": {"notes": notes},
        }})
        print(f"Wrote the App Review notes ({len(detail['attributes'].get('notes') or '')} -> {len(notes)} characters).")


def write_listing(app_id, platform, version, notes, has_earlier, write_listings):
    """
    Writes "What's New" and, unless write_listings is off, the listing text into the version, every localization
    checked before any is written.
    """
    if not (write_listings or has_earlier):
        return
    listings = store_listings.read("app-store" if platform == "IOS" else "mac-app-store")

    def fields(code, names):
        return {field: text for field, text in listings[code].items() if field in names}

    localizations = localized(
        request("GET", f"/appStoreVersions/{version['id']}/appStoreVersionLocalizations")["data"], write_listings, "version",
    )
    info_localizations = []
    if write_listings and platform == "IOS":
        info = editable_app_info(app_id)
        if info is None:
            print("::warning::No app info can be changed now (a version is in review or live), so the name and the "
                  "subtitle are left for the next version.")
        else:
            info_localizations = localized(request("GET", f"/appInfos/{info['id']}/appInfoLocalizations")["data"], True, "app info")

    # The first version of a platform has nothing to say "new" about, and App Store Connect refuses the field there.
    def attributes_of(code):
        attributes = fields(code, LOCALIZATION_FIELDS) if write_listings else {}
        if has_earlier:
            attributes["whatsNew"] = notes(code)
        return attributes

    write_localizations("appStoreVersionLocalizations", "appStoreVersionLocalizations", localizations, attributes_of)
    if not write_listings:
        return
    # A name another app holds is refused here with Apple's message, which fails the run before the submission.
    write_localizations("appInfoLocalizations", "appInfoLocalizations", info_localizations,
                        lambda code: fields(code, APP_INFO_FIELDS))
    version_attributes = fields("en", VERSION_FIELDS)
    if version_attributes:
        current = request("GET", f"/appStoreVersions/{version['id']}?fields[appStoreVersions]=copyright")["data"]
        print(store_listings.changes("version", current["attributes"], version_attributes))
        request("PATCH", f"/appStoreVersions/{version['id']}", {"data": {
            "type": "appStoreVersions", "id": version["id"], "attributes": version_attributes,
        }})
    review = fields("en", REVIEW_FIELDS)
    if review:
        write_review_notes(version, review["notes"])


def submit(app_id, platform, version):
    """Adds the version to the platform's open review submission, or to a new one, and submits it."""
    open_submissions = request(
        "GET", f"/reviewSubmissions?filter[app]={app_id}&filter[platform]={platform}&filter[state]={OPEN_SUBMISSION_STATES}",
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


def main(bundle_identifier, platform, version, build_number, notes_file, prepare_only, write_listings):
    with open(notes_file, encoding="utf-8") as file:
        english = file.read().strip()
    notes = store_listings.notes_by_language(english)
    for code in store_listings.app_languages():
        if len(notes(code)) > WHATS_NEW_LIMIT:
            fail(f"The {code} release notes are {len(notes(code))} characters long, and App Store Connect takes {WHATS_NEW_LIMIT}.")
    app = find_app(bundle_identifier)
    awaiting = awaiting_versions(app["id"], platform)
    if awaiting:
        attributes = awaiting[0]["attributes"]
        awaiting_build = attached_build_number(awaiting[0])
        if attributes["versionString"] == version and awaiting_build == build_number:
            print(f"The {platform} version {version} is already {attributes['appStoreState']} with build {build_number}; nothing to do.")
        else:
            print(f"::warning::The {platform} version {attributes['versionString']} is {attributes['appStoreState']} with "
                  f"build {awaiting_build}, and a platform takes one version at a time, so build {version} ({build_number}) "
                  "stays in TestFlight without being submitted. Submit it by hand once that one has been decided, or let "
                  "the next release replace it.")
        return
    build = wait_for_build(app["id"], platform, version, build_number)
    store_version, has_earlier = version_for_release(app["id"], platform, version)
    state = store_version["attributes"]["appStoreState"]
    if state not in EDITABLE_STATES:
        if attached_build_id(store_version) == build["id"]:
            print(f"The {platform} version {version} is already {state} with build {build_number}; nothing to do.")
            return
        fail(f"The {platform} version {version} is already {state} with another build, and a released version takes no "
             "new one: raise campfire.versionName for this release.")
    request("PATCH", f"/appStoreVersions/{store_version['id']}/relationships/build", {
        "data": {"type": "builds", "id": build["id"]},
    })
    print(f"Attached build {build_number} to the {platform} version {version}.")
    if has_earlier and not english:
        fail("An update needs release notes for its \"What's New\", and there are none.")
    write_listing(app["id"], platform, store_version, notes, has_earlier, write_listings)
    if prepare_only:
        print(f"Prepared the {platform} version {version} ({build_number}) without submitting it: add the screenshots in "
              "App Store Connect and submit it there.")
        return
    submit(app["id"], platform, store_version)
    print(f"Submitted the {platform} version {version} ({build_number}) for review. It is released as soon as it is approved.")


if __name__ == "__main__":
    arguments = sys.argv[1:]
    flags = arguments[5:]
    if len(arguments) < 5 or arguments[1] not in {"IOS", "MAC_OS"} \
            or not set(flags) <= {"--prepare", "--no-listings"} or len(set(flags)) != len(flags):
        fail(__doc__.strip().split("\n\n")[1].strip())
    main(*arguments[:5], "--prepare" in flags, "--no-listings" not in flags)
