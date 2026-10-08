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
    id("campfire-koin")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":data:repository:implementation"))
            implementation(project(":data:source:local:implementation"))
            implementation(project(":data:source:remote:implementation"))
            implementation(project(":data:sync:implementation"))
            implementation(project(":domain:implementation"))
            implementation(project(":metronome:implementation"))
            implementation(project(":presentation"))
            // The start function takes and returns Koin's own types, which the entry points calling it have to see.
            api(libs.koin.core)
            implementation(libs.kotlin.coroutines)
        }
    }
}
