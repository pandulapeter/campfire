plugins {
    id("campfire-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":data:model"))
            implementation(libs.kotlin.coroutines)
        }
    }
}
