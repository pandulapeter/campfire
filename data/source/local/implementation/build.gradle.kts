plugins {
    id("campfire-library")
    alias(libs.plugins.ksp)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":data:source:local:api"))
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.koin.core)
            implementation(libs.kotlin.coroutines)
        }
    }
}

dependencies {
    // Room generates the database implementation separately for every target.
    add("kspAndroid", libs.androidx.room.codegen)
    add("kspDesktop", libs.androidx.room.codegen)
    add("kspIosArm64", libs.androidx.room.codegen)
    add("kspIosSimulatorArm64", libs.androidx.room.codegen)
}
