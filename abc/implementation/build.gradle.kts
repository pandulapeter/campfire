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
            api(project(":abc:api"))
            implementation(libs.kotlin.coroutines)
        }
        // abc2svg runs in QuickJS on the two JVM targets, see QuickJsAbcScriptEngine.
        getByName("jvmSharedMain").dependencies {
            implementation(libs.quickjs)
        }
    }
}
