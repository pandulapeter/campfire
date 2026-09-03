plugins {
    id("campfire-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":domain:api"))
            implementation(project(":data:repository:api"))
            implementation(libs.koin.core)
            implementation(libs.kotlin.coroutines)
        }
    }
}
