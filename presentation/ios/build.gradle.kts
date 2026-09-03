plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        iosMain.dependencies {
            implementation(project(":domain:api"))
            api(project(":presentation:shared")) // TODO: Should be an implementation detail
            implementation(libs.compose.runtime)
            implementation(libs.compose.ui)
            implementation(libs.koin.compose.viewmodel)
        }
    }
}
