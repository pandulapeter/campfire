plugins {
    id("campfire-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":data:model"))
            api(project(":chordpro"))
            implementation(libs.kotlin.coroutines)
        }
    }
}
