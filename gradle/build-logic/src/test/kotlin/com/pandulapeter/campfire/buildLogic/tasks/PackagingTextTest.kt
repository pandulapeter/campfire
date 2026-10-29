/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.buildLogic.tasks

import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PackagingTextTest {

    @Test
    fun `a package version has four parts the last one zero`() {
        assertEquals("4.7.1.0", msixPackageVersion("4.7.1"))
        assertEquals("5.0.0.0", msixPackageVersion("5"))
        assertEquals("4.7.0.0", msixPackageVersion("4.7"))
    }

    /** The value the code computed before it moved here, for the publisher in gradle.properties. */
    @Test
    fun `the publisher ID of the store publisher is the one it has always been`() {
        assertEquals("cw1kaefgs4rbe", msixPublisherId("CN=AC1B9E39-11E9-4460-B47B-C69CEB0B1DAF"))
    }

    @Test
    fun `XML special characters are escaped`() {
        assertEquals("Rock &amp; &lt;Roll&gt; &quot;Live&quot;", xmlEscaped("Rock & <Roll> \"Live\""))
    }

    @Test
    fun `a Java option goes right after the section header`() {
        val lines = listOf("[Application]", "app.mainclass=Main", "[JavaOptions]", "java-options=-Xmx1g", "[ArgOptions]")
        assertEquals(
            listOf("[Application]", "app.mainclass=Main", "[JavaOptions]", "java-options=-Dx=y", "java-options=-Xmx1g", "[ArgOptions]"),
            withJavaOption(lines, "java-options=-Dx=y") { "unused" },
        )
    }

    @Test
    fun `a configuration without Java options is refused`() {
        val error = assertFailsWith<GradleException> { withJavaOption(listOf("[Application]"), "java-options=-Dx=y") { "No section." } }
        assertEquals("No section.", error.message)
    }

    @Test
    fun `only the matching Java options are removed`() {
        val lines = listOf("[JavaOptions]", "java-options=-XX:SharedArchiveFile=a.jsa", "java-options=-Xmx1g")
        assertEquals(
            listOf("[JavaOptions]", "java-options=-Xmx1g"),
            withoutJavaOptionsStartingWith(lines, "java-options=-XX:SharedArchiveFile="),
        )
    }

    @Test
    fun `an existing StartupWMClass is replaced`() {
        assertEquals(
            "[Desktop Entry]\nName=Campfire\nStartupWMClass=Campfire\n",
            withStartupWmClass(listOf("[Desktop Entry]", "StartupWMClass=Old", "Name=Campfire"), "Campfire"),
        )
    }

    @Test
    fun `a missing StartupWMClass is appended`() {
        val text = withStartupWmClass(listOf("[Desktop Entry]", "Name=Campfire"), "Campfire")
        assertEquals("[Desktop Entry]\nName=Campfire\nStartupWMClass=Campfire\n", text)
        assertTrue(text.endsWith("\n"))
    }

    @Test
    fun `a launcher without a field code is registered for the scheme`() {
        assertEquals(
            listOf("[Desktop Entry]", "Exec=/opt/campfire/bin/Campfire %u", "Name=Campfire", "MimeType=x-scheme-handler/campfire;"),
            withUrlScheme(listOf("[Desktop Entry]", "Exec=/opt/campfire/bin/Campfire", "Name=Campfire"), "campfire"),
        )
    }

    @Test
    fun `a file field code gives way to the address one and existing MIME types are kept`() {
        assertEquals(
            listOf("Exec=\"/opt/campfire/bin/Campfire\" %u", "MimeType=text/plain;x-scheme-handler/campfire;"),
            withUrlScheme(listOf("Exec=\"/opt/campfire/bin/Campfire\" %F", "MimeType=text/plain"), "campfire"),
        )
    }

    @Test
    fun `registering the scheme twice changes nothing`() {
        val once = withUrlScheme(listOf("Exec=/opt/campfire/bin/Campfire", "MimeType=text/plain;"), "campfire")
        assertEquals(once, withUrlScheme(once, "campfire"))
    }

    @Test
    fun `a desktop entry without a launcher is refused`() {
        assertFailsWith<GradleException> { withUrlScheme(listOf("[Desktop Entry]", "Name=Campfire"), "campfire") }
    }

    @Test
    fun `the desktop database is refreshed before the script exits`() {
        val script = withDesktopDatabaseUpdate(listOf("#!/bin/sh", "case \"$1\" in", "    configure)", "    ;;", "esac", "", "exit 0"))
        val lines = script.lines()
        val update = lines.indexOfFirst { "update-desktop-database -q" in it }
        assertTrue(update > lines.indexOf("esac"))
        assertTrue(update < lines.indexOf("exit 0"))
        assertEquals(script, withDesktopDatabaseUpdate(script.lines().dropLast(1)))
    }

    @Test
    fun `a script without an exit refreshes the desktop database at its end`() {
        val lines = withDesktopDatabaseUpdate(listOf("#!/bin/sh", "xdg-desktop-menu install campfire.desktop")).trimEnd().lines()
        assertTrue("update-desktop-database -q" in lines.last())
    }

    @Test
    fun `the menu tool is called only where it exists, and every other line is kept`() {
        val lines = listOf("set -e", "    configure)", "xdg-desktop-menu install /opt/campfire/lib/campfire-Campfire.desktop", "    ;;")
        assertEquals(
            listOf(
                "set -e",
                "    configure)",
                "if command -v xdg-desktop-menu >/dev/null 2>&1; then xdg-desktop-menu install /opt/campfire/lib/campfire-Campfire.desktop; fi",
                "    ;;",
            ),
            withOptionalDesktopMenu(lines),
        )
        assertEquals(withOptionalDesktopMenu(lines), withOptionalDesktopMenu(withOptionalDesktopMenu(lines)))
    }

    @Test
    fun `only the line of the entry gets the new hash`() {
        val lines = listOf("aaa  opt/campfire/bin/Campfire", "bbb  opt/campfire/lib/campfire-Campfire.desktop", "ccc  opt/campfire/lib/app.cfg")
        assertEquals(
            "aaa  opt/campfire/bin/Campfire\nddd  opt/campfire/lib/campfire-Campfire.desktop\nccc  opt/campfire/lib/app.cfg\n",
            withUpdatedMd5(lines, "opt/campfire/lib/campfire-Campfire.desktop", "ddd"),
        )
    }

    @Test
    fun `an AppImage's desktop entry opens campfire links and names the window class`() {
        val entry = appImageDesktopEntry(
            name = "Campfire",
            comment = "Your songbook",
            executable = "Campfire",
            icon = "campfire",
            categories = "AudioVideo;Audio;Music;",
            className = "Campfire",
            scheme = "campfire",
        ).trimEnd().lines()
        assertEquals("[Desktop Entry]", entry.first())
        assertTrue("Exec=Campfire %u" in entry)
        assertTrue("Icon=campfire" in entry)
        assertTrue("MimeType=x-scheme-handler/campfire;" in entry)
        assertEquals("StartupWMClass=Campfire", entry.last())
    }

    @Test
    fun `AppImages are made for the two processors the release is built on`() {
        assertEquals("x86_64", appImageArchitecture("amd64"))
        assertEquals("aarch64", appImageArchitecture("aarch64"))
        assertEquals(null, appImageArchitecture("riscv64"))
    }

    @Test
    fun `SDK versions are ordered by number rather than by name`() {
        val names = listOf("10.0.9600.0", "10.0.22621.0", "10.0.19041.0")
        assertEquals("10.0.22621.0", names.maxBy(::newestSdkVersionKey))
        assertTrue(newestSdkVersionKey("10.0.22621.0") > newestSdkVersionKey("10.0.9600.0"))
    }
}
