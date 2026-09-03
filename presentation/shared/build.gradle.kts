plugins {
    id("campfire-compose-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":domain:api"))
            implementation(libs.compose.animation)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material)
            implementation(libs.compose.reorderable)
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.koin.core)
            implementation(libs.kotlin.coroutines)
        }
    }
}
