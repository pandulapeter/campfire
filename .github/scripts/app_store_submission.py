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
        [--prepare] [--no-listings] [--screenshots <folder>]

It waits for App Store Connect to finish processing the build (processing is also where a build is refused, with the
reason mailed; this fails with the state instead of waiting forever), takes the platform's version for the release -
the one that already carries that version string, or else the editable one, renamed, or else a new one, set to be
released as soon as it is approved - attaches the build, writes the release notes as its "What's New" and the listing
text of documentation/store-listings/<app-store | mac-app-store> into it, replaces its screenshots where --screenshots
names a folder of them (unless --no-listings keeps the listing as it is), and submits it. With --prepare it stops before the submission, leaving the version ready in App
Store Connect for somebody to look over and submit there; a later run with the same build submits it as it is.

A draft that is already there - a version prepared in App Store Connect for this release, whether or not it has been
added to a review submission, or one that was rejected - is used as it is: the build is attached to it and its "What's
New" replaced with the notes, everything else in it is kept exactly as it is (its screenshots too, unless
--screenshots is given) and the release option included, and it is submitted, in the review submission it is already
in where there is one.

The notes are each language's from RELEASE_NOTES_BY_LANGUAGE (release_description.py's JSON), the notes file's for any
language it does not name. The listing is written by language (store_listings.py): every version localization gets its
language's description, keywords, promotional text, and, for iOS, the URLs (fr-FR and fr-CA alike), the version its
copyright and App Review notes, and on iOS each app info localization its name and subtitle, which the Mac shares with
iOS and never writes. An app language with no version localization gets one, in the locale store_listings.py names for
it, with the same text, the URLs (the Mac's from the iOS listing, since its files have none), and, on any version but
the platform's first, "What's New", which App Store Connect then requires. Its app info localization is created too
where the language has none, by whichever of the two platforms gets there first, with the iOS name and subtitle and the
website's privacy policy in that language, or the English one's where the website has no page in it yet. Everything is
asked of App Store Connect before anything is written; a name another app holds fails the run with Apple's message,
before the submission. When no app info is editable (a version in review) the name and subtitle wait for the next
version, with a warning. With --no-listings only the notes are written, and a missing language stays missing.

The screenshots are the framing's store folder (store_images.py), which the run that made them has already checked
against App Store Connect's rules. Each display type a language has images of is emptied and refilled in every
localization of that language, in the listing's order, so a shot taken out of the listing goes from the store too and a
repeated run ends where the first did; a display type with no images, the iPhone Duo's among them, which the API has no
type for, is left as it is. App Store Connect processes each image after its upload, and the version is submitted only
once every one has been processed: one it refuses, or one still processing after SCREENSHOT_TIMEOUT_SECONDS, fails the
run first, since a version submitted with half its screenshots would be reviewed and released that way.

A version that is already waiting for review or further along with this very build is left as it is, so a run that is
repeated does not fail on its own success. Where another build is waiting for Apple - under review, or approved
and not on the store yet - this one is not submitted at all, since a platform takes one version at a time: it stays in
TestFlight, the run says so with a warning rather than failing, and it is submitted by hand once the other one has been
decided, or replaced by the next release's build. That is decided before waiting for processing, which a build that is
not going to be submitted has no reason to wait for.

Uses the App Store Connect API the way app_store_signing.py does, and the same environment.
"""

import hashlib
import http.client
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

import store_images
import store_listings
from app_store_signing import request
from store_http import REQUEST_ATTEMPTS, REQUEST_TIMEOUT_SECONDS, fail

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
# The fields a new version localization of the Mac takes from the iOS listing, whose files are the only ones with them.
URL_FIELDS = {"supportUrl", "marketingUrl"}
# The website's privacy policy in each language; a language the website has no pages in yet answers 404.
PRIVACY_POLICY_URL = "https://campfire-songbook.com/{language}/privacy/"
# How long App Store Connect may take to process the screenshots, which is usually a few minutes for all of them.
SCREENSHOT_TIMEOUT_SECONDS = 15 * 60
SCREENSHOT_POLL_INTERVAL_SECONDS = 15


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


def localized(items, where):
    """
    ([(item, the app's language)] of App Store Connect's localizations, [the app's languages with none]); a localization
    of a language the app lacks is left alone.
    """
    if not items:
        fail(f"The {where} has no localizations to write into.")
    by_locale = {item["attributes"]["locale"]: item for item in items}
    matched, missing, unknown = store_listings.match_locales(list(by_locale), store_listings.app_languages())
    for locale in unknown:
        print(f"::warning::The {where}'s {locale} localization is in a language the app does not have, so it is left as it is.")
    return [(by_locale[locale], code) for locale, code in matched.items()], missing


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


def page_exists(url):
    """Whether the page answers 200; anything else, a failed connection included, is a page to do without."""
    try:
        with urllib.request.urlopen(url, timeout=REQUEST_TIMEOUT_SECONDS) as response:
            return response.status == 200
    except (OSError, http.client.HTTPException):
        return False


def create_version_localizations(version, store, missing, attributes_of):
    for locale, code in store_listings.new_listing_locales(store, missing).items():
        request("POST", "/appStoreVersionLocalizations", {"data": {
            "type": "appStoreVersionLocalizations",
            "attributes": {"locale": locale, **attributes_of(code)},
            "relationships": {"appStoreVersion": {"data": {"type": "appStoreVersions", "id": version["id"]}}},
        }})
        print(f"Created the version's {locale} localization, for the app's {code}.")


def create_app_info_localizations(info, localizations, missing, listings):
    """Creates the app info localizations of the languages with none, with the iOS name and subtitle, the Mac's too."""
    english = next((
        item["attributes"].get("privacyPolicyUrl") for item, code in localizations if code == "en"
    ), None)
    for locale, code in store_listings.new_listing_locales("app-store", missing).items():
        attributes = {"locale": locale, **{field: listings[code][field] for field in APP_INFO_FIELDS}}
        own = PRIVACY_POLICY_URL.format(language=code)
        privacy_policy = own if page_exists(own) else english
        if privacy_policy:
            attributes["privacyPolicyUrl"] = privacy_policy
        # A name another app holds is refused here with Apple's message, which fails the run before the submission.
        request("POST", "/appInfoLocalizations", {"data": {
            "type": "appInfoLocalizations", "attributes": attributes,
            "relationships": {"appInfo": {"data": {"type": "appInfos", "id": info["id"]}}},
        }})
        print(f"Created the app info's {locale} localization, for the app's {code}, its privacy policy {privacy_policy}.")


def app_info_localizations(app_id):
    """(the editable app info or None, its localizations as localized() returns them)."""
    info = editable_app_info(app_id)
    if info is None:
        return None, ([], [])
    return info, localized(request("GET", f"/appInfos/{info['id']}/appInfoLocalizations")["data"], "app info")


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


def write_version_fields(version, listings):
    version_attributes = {field: text for field, text in listings["en"].items() if field in VERSION_FIELDS}
    if version_attributes:
        current = request("GET", f"/appStoreVersions/{version['id']}?fields[appStoreVersions]=copyright")["data"]
        print(store_listings.changes("version", current["attributes"], version_attributes))
        request("PATCH", f"/appStoreVersions/{version['id']}", {"data": {
            "type": "appStoreVersions", "id": version["id"], "attributes": version_attributes,
        }})
    review = {field: text for field, text in listings["en"].items() if field in REVIEW_FIELDS}
    if review:
        write_review_notes(version, review["notes"])


def write_listing(app_id, platform, version, notes, has_earlier, write_listings):
    """
    Writes "What's New" and, unless write_listings is off, the listing text into the version, creating the localizations
    of the app's languages it lacks, everything asked of App Store Connect before anything is written.
    """
    if not (write_listings or has_earlier):
        return
    store = "app-store" if platform == "IOS" else "mac-app-store"
    listings = store_listings.read(store)
    ios_listings = store_listings.read("app-store") if write_listings else listings

    def fields(code, names):
        return {field: text for field, text in listings[code].items() if field in names}

    localizations, missing = localized(
        request("GET", f"/appStoreVersions/{version['id']}/appStoreVersionLocalizations")["data"], "version",
    )
    info, (info_localizations, info_missing) = app_info_localizations(app_id) if write_listings else (None, ([], []))
    if write_listings and info is None and (platform == "IOS" or missing):
        print("::warning::No app info can be changed now (a version is in review or live), so the name and the "
              "subtitle are left for the next version.")

    # The first version of a platform has nothing to say "new" about, and App Store Connect refuses the field there.
    def attributes_of(code):
        attributes = fields(code, LOCALIZATION_FIELDS) if write_listings else {}
        if has_earlier:
            attributes["whatsNew"] = notes(code)
        return attributes

    if write_listings:
        create_app_info_localizations(info, info_localizations, info_missing, ios_listings)
        urls = {field: text for field, text in ios_listings["en"].items() if field in URL_FIELDS}
        create_version_localizations(version, store, missing, lambda code: {**urls, **attributes_of(code)})
    write_localizations("appStoreVersionLocalizations", "appStoreVersionLocalizations", localizations, attributes_of)
    if not write_listings:
        return
    if platform == "IOS":
        # A name another app holds is refused here with Apple's message, which fails the run before the submission.
        write_localizations("appInfoLocalizations", "appInfoLocalizations", info_localizations,
                            lambda code: fields(code, APP_INFO_FIELDS))
    write_version_fields(version, listings)


def upload_part(operation, data, name):
    """One of the upload operations App Store Connect hands out for a reserved image: raw bytes to a signed URL."""
    offset = operation["offset"]
    headers = {header["name"]: header["value"] for header in operation.get("requestHeaders") or []}
    call = urllib.request.Request(
        operation["url"], data=data[offset:offset + operation["length"]], headers=headers, method=operation["method"],
    )
    for attempt in range(1, REQUEST_ATTEMPTS + 1):
        try:
            with urllib.request.urlopen(call, timeout=REQUEST_TIMEOUT_SECONDS) as response:
                response.read()
                return
        # A part is the same bytes to the same place however often it is sent, so any failure the service or the
        # network may not repeat is tried again. The URL is signed, and never printed.
        except (OSError, http.client.HTTPException) as error:
            code = getattr(error, "code", None)
            if attempt < REQUEST_ATTEMPTS and (code is None or code == 429 or code >= 500):
                print(f"Uploading {name} failed ({code or error}), trying again in {10 * attempt} s.")
                time.sleep(10 * attempt)
                continue
            fail(f"Uploading {name} failed: {code or error}.")


def upload_screenshot(set_id, path):
    """Reserves, uploads, and commits one image into a screenshot set, returning its ID."""
    path = Path(path)
    data = path.read_bytes()
    reserved = request("POST", "/appScreenshots", {"data": {
        "type": "appScreenshots", "attributes": {"fileName": path.name, "fileSize": len(data)},
        "relationships": {"appScreenshotSet": {"data": {"type": "appScreenshotSets", "id": set_id}}},
    }})["data"]
    for operation in reserved["attributes"]["uploadOperations"]:
        upload_part(operation, data, path.name)
    request("PATCH", f"/appScreenshots/{reserved['id']}", {"data": {
        "type": "appScreenshots", "id": reserved["id"],
        "attributes": {"uploaded": True, "sourceFileChecksum": hashlib.md5(data).hexdigest()},
    }})
    return reserved["id"]


def fill_screenshot_set(screenshot_set, paths):
    """Replaces every screenshot of the set with the images, in their order, returning the new ones' IDs."""
    for old in request("GET", f"/appScreenshotSets/{screenshot_set['id']}/appScreenshots")["data"]:
        request("DELETE", f"/appScreenshots/{old['id']}", missing_ok=True)
    ids = [upload_screenshot(screenshot_set["id"], path) for path in paths]
    # The order the images were reserved in is not promised to be the order the set shows them in.
    request("PATCH", f"/appScreenshotSets/{screenshot_set['id']}/relationships/appScreenshots", {
        "data": [{"type": "appScreenshots", "id": item} for item in ids],
    })
    return ids


def screenshot_sets(localization):
    """{display type: set} of a version localization."""
    sets = request("GET", f"/appStoreVersionLocalizations/{localization['id']}/appScreenshotSets")["data"]
    return {item["attributes"]["screenshotDisplayType"]: item for item in sets}


def create_screenshot_set(localization, display_type):
    return request("POST", "/appScreenshotSets", {"data": {
        "type": "appScreenshotSets", "attributes": {"screenshotDisplayType": display_type},
        "relationships": {"appStoreVersionLocalization": {"data": {
            "type": "appStoreVersionLocalizations", "id": localization["id"],
        }}},
    }})["data"]


def wait_for_screenshots(ids):
    """Returns once App Store Connect has processed every screenshot; one it refused or is still on at the timeout fails."""
    deadline = time.time() + SCREENSHOT_TIMEOUT_SECONDS
    pending = list(ids)
    while True:
        waiting = []
        for item in pending:
            path = f"/appScreenshots/{item}?fields[appScreenshots]=fileName,assetDeliveryState"
            attributes = request("GET", path)["data"]["attributes"]
            delivery = attributes.get("assetDeliveryState") or {}
            if delivery.get("state") == "FAILED":
                reasons = [error.get("description") or error.get("code") or "" for error in delivery.get("errors") or []]
                fail(f"App Store Connect could not process the screenshot {attributes.get('fileName')}: "
                     f"{'; '.join(reasons) or 'no reason given'}.")
            if delivery.get("state") != "COMPLETE":
                waiting.append(item)
        if not waiting:
            print(f"App Store Connect has processed all {len(ids)} screenshots.")
            return
        if time.time() > deadline:
            fail(f"{len(waiting)} of the {len(ids)} screenshots were still being processed after "
                 f"{SCREENSHOT_TIMEOUT_SECONDS // 60} minutes.")
        print(f"{len(waiting)} of the {len(ids)} screenshots are still being processed, asking again in "
              f"{SCREENSHOT_POLL_INTERVAL_SECONDS} s.")
        sys.stdout.flush()
        time.sleep(SCREENSHOT_POLL_INTERVAL_SECONDS)
        pending = waiting


def replace_screenshots(version, platform, folder):
    """
    Empties and refills, in every version localization of an app language, each display type the folder has images of
    in that language, then waits for App Store Connect to process them.
    """
    store = "app-store" if platform == "IOS" else "mac-app-store"
    items = request("GET", f"/appStoreVersions/{version['id']}/appStoreVersionLocalizations")["data"]
    by_locale = {item["attributes"]["locale"]: item for item in items}
    matched, _, _ = store_listings.match_locales(list(by_locale), store_listings.app_languages())
    uploaded = []
    for locale, code in matched.items():
        images = store_images.images(folder, store, code)
        if not images:
            continue
        localization = by_locale[locale]
        existing = screenshot_sets(localization)
        for display_type, paths in images.items():
            screenshot_set = existing.get(display_type) or create_screenshot_set(localization, display_type)
            uploaded += fill_screenshot_set(screenshot_set, paths)
        print(f"{locale}: {', '.join(f'{len(paths)} {kind}' for kind, paths in images.items())}.")
    if uploaded:
        wait_for_screenshots(uploaded)


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


def main(bundle_identifier, platform, version, build_number, notes_file, prepare_only, write_listings, screenshots=None):
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
    # A draft finished by hand wants the new images as much as one submitted here does. A listing kept as it is in App
    # Store Connect keeps its images too.
    if screenshots is not None and write_listings:
        replace_screenshots(store_version, platform, screenshots)
    elif screenshots is not None:
        print("The listings are left as they are, so the screenshots are too.")
    if prepare_only:
        print(f"Prepared the {platform} version {version} ({build_number}) without submitting it: look it over in App "
              "Store Connect and submit it there.")
        return
    submit(app["id"], platform, store_version)
    print(f"Submitted the {platform} version {version} ({build_number}) for review. It is released as soon as it is approved.")


def parse_arguments(arguments):
    """main's arguments from the command line, or None where they do not read as the usage line says."""
    positional, flags, screenshots = arguments[:5], arguments[5:], None
    if "--screenshots" in flags:
        index = flags.index("--screenshots")
        if index + 1 >= len(flags) or flags[index + 1].startswith("--"):
            return None
        screenshots = flags[index + 1]
        flags = flags[:index] + flags[index + 2:]
    if len(positional) < 5 or positional[1] not in {"IOS", "MAC_OS"} \
            or not set(flags) <= {"--prepare", "--no-listings"} or len(set(flags)) != len(flags):
        return None
    return [*positional, "--prepare" in flags, "--no-listings" not in flags, screenshots]


if __name__ == "__main__":
    parsed = parse_arguments(sys.argv[1:])
    if parsed is None:
        fail(__doc__.strip().split("\n\n")[1].strip())
    # A mistyped folder would otherwise read as one with no images, and leave every screenshot as it was without a word.
    if parsed[-1] is not None and not Path(parsed[-1]).is_dir():
        fail(f"There is no folder {parsed[-1]} to take the screenshots from.")
    main(*parsed)
