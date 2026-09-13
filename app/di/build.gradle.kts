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
    alias(libs.plugins.koin.compiler)
}

// A failed graph check is reported as the missing definition; the line advertising an AI service after it is left out.
koinCompiler {
    aiAssist = false
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":data:repository:implementation"))
            implementation(project(":data:source:local:implementation"))
            implementation(project(":data:source:remote:implementation"))
            implementation(project(":domain:implementation"))
            implementation(project(":presentation"))
            // The start function takes and returns Koin's own types, which the entry points calling it have to see.
            api(libs.koin.core)
            implementation(libs.koin.annotations)
        }
    }
}
