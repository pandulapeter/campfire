# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

import unittest

from release_description import ReleaseDescriptionError, read


def notes_of(length):
    return f"<!-- whats-new en-US\n{'x' * length}\n-->\n"


class ReadTest(unittest.TestCase):

    def test_no_comments_give_the_visible_text_and_the_defaults(self):
        outputs = read("Fixes **the** [editor](https://example.com) and `sync`.")["outputs"]
        self.assertEqual(outputs["release_notes"], "Fixes the editor and sync.")
        self.assertEqual(outputs["update_priority"], "0")
        for store in ["play_store", "app_store", "mac_app_store", "microsoft_store"]:
            self.assertEqual(outputs[f"{store}_submit"], "true")

    def test_whats_new_is_read_in_either_case(self):
        for header in ["en-US", "en-us", "EN-US"]:
            body = f"Visible.\n<!-- whats-new {header}\n- **Bold** stays\n-->\n"
            self.assertEqual(read(body)["outputs"]["release_notes"], "- **Bold** stays")

    def test_whats_new_in_another_language_is_left_alone(self):
        body = "Visible.\n<!-- whats-new hu-HU\n- Magyar\n-->\n"
        self.assertEqual(read(body)["outputs"]["release_notes"], "Visible.")

    def test_the_first_whats_new_wins(self):
        body = "<!-- whats-new en-US\n- First\n-->\n<!-- whats-new en-US\n- Second\n-->\n"
        self.assertEqual(read(body)["outputs"]["release_notes"], "- First")

    def test_crlf_reads_like_lf(self):
        lf = "Visible.\n<!-- whats-new en-US\n- One\n- Two\n-->\n<!-- play-store update-priority: 3 -->\n<!-- app-store submit: false -->\n"
        self.assertEqual(read(lf.replace("\n", "\r\n")), read(lf))

    def test_submit_values(self):
        outputs = read("<!-- play-store submit: False -->")["outputs"]
        self.assertEqual(outputs["play_store_submit"], "false")
        with self.assertRaises(ReleaseDescriptionError):
            read("<!-- play-store submit: flase -->")

    def test_an_unknown_store_stops_the_release(self):
        for body in ["<!-- play_store submit: false -->", "<!-- playstore submit: false -->", "<!-- play store submit: false -->"]:
            with self.assertRaises(ReleaseDescriptionError):
                read(body)

    def test_mac_app_store_does_not_touch_app_store(self):
        outputs = read("<!-- mac-app-store submit: false -->")["outputs"]
        self.assertEqual(outputs["mac_app_store_submit"], "false")
        self.assertEqual(outputs["app_store_submit"], "true")

    def test_update_priority(self):
        self.assertEqual(read("<!-- play-store update-priority: 4 -->")["outputs"]["update_priority"], "4")
        for value in ["6", "10", "high"]:
            with self.assertRaises(ReleaseDescriptionError):
                read(f"<!-- play-store update-priority: {value} -->")
        with self.assertRaises(ReleaseDescriptionError):
            read("<!-- app-store update-priority: 4 -->")

    def test_partner_center_limit_whatever_the_submit_value(self):
        for value in ["true", "false"]:
            with self.assertRaises(ReleaseDescriptionError) as context:
                read(notes_of(1501) + f"<!-- microsoft-store submit: {value} -->")
            self.assertTrue(any("Partner Center" in message for message in context.exception.messages))

    def test_app_store_connect_limit_for_either_apple_store(self):
        for store in ["app-store", "mac-app-store"]:
            for value in ["true", "false"]:
                with self.assertRaises(ReleaseDescriptionError) as context:
                    read(notes_of(4001) + f"<!-- {store} submit: {value} -->")
                self.assertTrue(any(f"App Store Connect takes 4000 ({store})" in message for message in context.exception.messages))

    def test_play_limit_only_warns(self):
        result = read(notes_of(501))
        self.assertEqual(len(result["outputs"]["release_notes"]), 501)
        self.assertEqual(len(result["warnings"]), 1)
        self.assertEqual(read(notes_of(500))["warnings"], [])

    def test_a_slip_in_an_instruction_stops_the_release(self):
        for body in [
            "<!-- app-store sumbit: false -->",
            "<!-- play-store submit : false -->",
            "<!-- microsoft-store: submit false -->",
            "<!-- play-store update_priority: 5 -->",
            "Vis\n<!-- whats-new en-US - one line -->",
            "Vis\n<!-- whats-new en\n- n\n-->",
        ]:
            with self.assertRaises(ReleaseDescriptionError):
                read(body)

    def test_a_closing_on_its_own_line_reads(self):
        self.assertEqual(read("<!-- play-store submit: false\n-->")["outputs"]["play_store_submit"], "false")
        self.assertEqual(read("<!-- play-store update-priority: 4\n-->")["outputs"]["update_priority"], "4")

    def test_other_comments_are_left_alone(self):
        result = read("<!-- TODO polish -->\nVisible.")
        self.assertEqual(result["outputs"]["release_notes"], "Visible.")

    def test_the_prepare_release_template_reads(self):
        body = (
            "Visible.\n\n"
            "<!-- whats-new en-US\n- bullet one\n- bullet two\n-->\n"
            "<!-- play-store update-priority: 3 -->\n"
            "<!-- play-store submit: true -->\n"
            "<!-- app-store submit: true -->\n"
            "<!-- mac-app-store submit: true -->\n"
            "<!-- microsoft-store submit: true -->\n"
        )
        outputs = read(body)["outputs"]
        self.assertEqual(outputs["release_notes"], "- bullet one\n- bullet two")
        self.assertEqual(outputs["update_priority"], "3")


if __name__ == "__main__":
    unittest.main()
