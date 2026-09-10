plugins {
    id("campfire-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":data:model"))
            // The connect call carries the words the desktop's redirect page shows, see AuthorizationCompletionPage.
            api(project(":data:source:remote:api"))
            implementation(libs.kotlin.coroutines)
        }
    }
}
