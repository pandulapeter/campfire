# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

import unittest

from pkg_allowed_os_versions import with_only_allowed_os_version

# The volume check of the Distribution jpackage writes for a store package, with the 10.11 of its product definition.
JPACKAGE_DISTRIBUTION = """<installer-gui-script minSpecVersion="2">
    <volume-check>
        <allowed-os-versions>
            <os-version min="12.0"/>
            <os-version min="10.11" before="10.12"/>
        </allowed-os-versions>
    </volume-check>
</installer-gui-script>"""


class PkgAllowedOsVersionsTest(unittest.TestCase):

    def test_a_package_allows_only_the_apps_minimum(self):
        self.assertEqual(
            """<installer-gui-script minSpecVersion="2">
    <volume-check>
        <allowed-os-versions>
            <os-version min="12.0"/>
        </allowed-os-versions>
    </volume-check>
</installer-gui-script>""",
            with_only_allowed_os_version(JPACKAGE_DISTRIBUTION, "12.0"),
        )

    def test_a_distribution_naming_no_versions_is_left_as_it_is(self):
        distribution = '<installer-gui-script minSpecVersion="2">\n    <title>Campfire</title>\n</installer-gui-script>'
        self.assertEqual(distribution, with_only_allowed_os_version(distribution, "12.0"))


if __name__ == "__main__":
    unittest.main()
