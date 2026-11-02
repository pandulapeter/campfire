/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
plugins {
    id("campfire-library")
}

kotlin {
    // The public surface is written on purpose, as :libs:chordpro's is: a declaration says it is public.
    explicitApi()
    sourceSets {
        commonMain.dependencies {
            api(project(":libs:chordpro"))
        }
        desktopTest {
            // The plain-text round trip reads the song corpus :libs:chordpro's own round trips run over.
            resources.srcDir(rootProject.file("libs/chordpro/src/desktopTest/resources"))
        }
    }
}
