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
    fun `only the line of the entry gets the new hash`() {
        val lines = listOf("aaa  opt/campfire/bin/Campfire", "bbb  opt/campfire/lib/campfire-Campfire.desktop", "ccc  opt/campfire/lib/app.cfg")
        assertEquals(
            "aaa  opt/campfire/bin/Campfire\nddd  opt/campfire/lib/campfire-Campfire.desktop\nccc  opt/campfire/lib/app.cfg\n",
            withUpdatedMd5(lines, "opt/campfire/lib/campfire-Campfire.desktop", "ddd"),
        )
    }

    @Test
    fun `SDK versions are ordered by number rather than by name`() {
        val names = listOf("10.0.9600.0", "10.0.22621.0", "10.0.19041.0")
        assertEquals("10.0.22621.0", names.maxBy(::newestSdkVersionKey))
        assertTrue(newestSdkVersionKey("10.0.22621.0") > newestSdkVersionKey("10.0.9600.0"))
    }
}
