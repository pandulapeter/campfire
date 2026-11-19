# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

import unittest

from check_sync_seams import (CAMPFIRE, ENGINE_FORBIDDEN_DECLARATIONS, ENGINE_FORBIDDEN_PACKAGES,
                              PROVIDER_CORE_FORBIDDEN_PACKAGES, REMOTE, SYNC, problems)

SYNC_MAIN = "data/sync/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/sync/implementation/"
REMOTE_SOURCES = ("data/source/remote/implementation/src/{}/kotlin/com/pandulapeter/campfire/data/source/remote/"
                  "implementation/")


def source(package, *imports):
    return "\n".join([f"package {package}", ""] + [f"import {imported}" for imported in imports]) + "\n"


class CheckSyncSeamsTest(unittest.TestCase):

    def test_an_engine_package_importing_the_engine_and_generic_models_passes(self):
        text = source(f"{SYNC}.run", f"{SYNC}.SyncKey", f"{SYNC}.merge.MergedFolderDocument",
                      f"{CAMPFIRE}.data.model.library.SyncCollection", f"{CAMPFIRE}.data.model.library.LibraryFiles",
                      f"{CAMPFIRE}.data.source.remote.api.sync.SyncFolder")
        self.assertEqual([], problems(SYNC_MAIN + "run/SyncEngine.kt", text))

    def test_every_engine_package_fails_on_every_forbidden_import(self):
        for package in ("run", "index", "scheduling", "connection", "merge", "run.nested"):
            imports = [prefix + ".Thing" for prefix in ENGINE_FORBIDDEN_PACKAGES] + list(ENGINE_FORBIDDEN_DECLARATIONS)
            for imported in imports:
                with self.subTest(package=package, imported=imported):
                    failures = problems(SYNC_MAIN + f"{package}/File.kt", source(f"{SYNC}.{package}", imported))
                    self.assertEqual(1, len(failures))
                    self.assertIn("the sync engine knows nothing of Campfire's library", failures[0])

    def test_a_forbidden_declaration_imported_by_name_fails(self):
        text = source(f"{SYNC}.scheduling", f"{CAMPFIRE}.data.model.library.LibraryCollections")
        self.assertEqual(1, len(problems(SYNC_MAIN + "scheduling/SyncRunner.kt", text)))

    def test_a_name_sharing_only_a_prefix_with_a_forbidden_one_passes(self):
        text = source(f"{SYNC}.run", f"{CAMPFIRE}.data.model.songbook.Thing", f"{SYNC}.sharedState.Thing")
        self.assertEqual([], problems(SYNC_MAIN + "run/File.kt", text))

    def test_a_star_import_bringing_in_a_forbidden_name_fails(self):
        self.assertEqual(1, len(problems(SYNC_MAIN + "run/File.kt",
                                         source(f"{SYNC}.run", f"{CAMPFIRE}.data.model.library.*"))))
        self.assertEqual(1, len(problems(SYNC_MAIN + "run/File.kt", source(f"{SYNC}.run", f"{SYNC}.shared.*"))))
        self.assertEqual(1, len(problems(SYNC_MAIN + "run/File.kt", source(f"{SYNC}.run", f"{SYNC}.*"))))
        sync_models = source(f"{SYNC}.run", f"{CAMPFIRE}.data.model.sync.*")
        self.assertEqual([], problems(SYNC_MAIN + "run/File.kt", sync_models))
        # A star import reaches no subpackage, so the model's root package brings in no song.
        self.assertEqual([], problems(SYNC_MAIN + "run/File.kt", source(f"{SYNC}.run", f"{CAMPFIRE}.data.model.*")))

    def test_campfire_s_side_of_the_sync_module_is_not_judged(self):
        for package in (SYNC, f"{SYNC}.shared", f"{SYNC}.preferences"):
            with self.subTest(package=package):
                text = source(package, f"{CAMPFIRE}.chordpro.edit.libraryFilesNamedBy", f"{SYNC}.shared.SharedCopyStep")
                self.assertEqual([], problems(SYNC_MAIN + "LibrarySyncRules.kt", text))

    def test_every_provider_core_package_fails_on_every_forbidden_import_in_every_main_source_set(self):
        for source_set in ("commonMain", "androidMain", "desktopMain", "iosMain", "wasmJsMain", "jvmSharedMain"):
            for package in ("auth", "network", "crypto"):
                for prefix in PROVIDER_CORE_FORBIDDEN_PACKAGES:
                    with self.subTest(source_set=source_set, package=package, prefix=prefix):
                        path = REMOTE_SOURCES.format(source_set) + f"{package}/File.kt"
                        failures = problems(path, source(f"{REMOTE}.{package}", prefix + ".Thing"))
                        self.assertEqual(1, len(failures))
                        self.assertIn("the provider core names no provider and no app", failures[0])

    def test_the_provider_core_may_not_import_the_module_s_root_package(self):
        for name in ("CAMPFIRE_OAUTH_REDIRECT", "USER_AGENT", "DROPBOX_APP_KEY", "CAMPFIRE_VERSION", "*"):
            with self.subTest(name=name):
                text = source(f"{REMOTE}.network", f"{REMOTE}.{name}")
                failures = problems(REMOTE_SOURCES.format("commonMain") + "network/HttpClientFactory.kt", text)
                self.assertEqual(1, len(failures))
                self.assertIn(f"of the root package {REMOTE};", failures[0])

    def test_the_provider_core_may_import_itself_and_the_api(self):
        text = source(f"{REMOTE}.auth", f"{REMOTE}.network.HttpClientHolder", f"{REMOTE}.crypto.Pkce",
                      f"{CAMPFIRE}.data.source.remote.api.sync.SyncAuthenticator")
        self.assertEqual([], problems(REMOTE_SOURCES.format("desktopMain") + "auth/DesktopSyncAuthenticator.kt", text))

    def test_dropbox_may_import_the_provider_core_and_the_root(self):
        text = source(f"{REMOTE}.dropbox", f"{REMOTE}.network.SyncHttpTransport", f"{REMOTE}.DROPBOX_APP_KEY")
        self.assertEqual([], problems(REMOTE_SOURCES.format("commonMain") + "dropbox/DropboxSyncProvider.kt", text))

    def test_a_test_source_file_is_not_judged(self):
        text = source(f"{REMOTE}.auth", f"{REMOTE}.CAMPFIRE_OAUTH_REDIRECT")
        path = REMOTE_SOURCES.format("desktopTest") + "auth/LoopbackRedirectListenerTest.kt"
        self.assertEqual([], problems(path, text))
        self.assertEqual([], problems(SYNC_MAIN.replace("commonMain", "commonTest") + "run/T.kt",
                                      source(f"{SYNC}.run", f"{CAMPFIRE}.data.model.library.LibraryCollections")))


if __name__ == "__main__":
    unittest.main()
