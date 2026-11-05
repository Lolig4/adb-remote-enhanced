// TV navigation helper: accessibility service that drives touch-only apps (e.g. the Netflix phone
// app) with the D-pad of a normal TV remote. Installed on the TV box, independent of ADB Remote.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.adbremote.navhelper"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.adbremote.navhelper"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
    sourceSets["main"].java.srcDirs("src/main/kotlin")
    buildTypes {
        debug { }
    }
    lint { abortOnError = false; checkReleaseBuilds = false }
}
