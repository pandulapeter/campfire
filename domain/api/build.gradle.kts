plugins {
    id("campfire-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":data:model"))
            api(project(":chordpro"))
            // Connecting carries the words the desktop's redirect page shows, see AuthorizationCompletionPage.
            api(project(":data:source:remote:api"))
            implementation(libs.kotlin.coroutines)
        }
    }
}
