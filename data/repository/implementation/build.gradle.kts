plugins {
    id("campfire-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":data:repository:api"))
            implementation(project(":data:source:local:api"))
            implementation(project(":data:source:remote:api"))
            implementation(libs.koin.core)
            implementation(libs.kotlin.coroutines)
        }
    }
}
