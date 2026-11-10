// TV navigation helper: accessibility service that drives touch-only apps (e.g. the Netflix phone
// app) with the D-pad of a normal TV remote. Installed on the TV box, independent of ADB Remote.
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing: same machine-local keystore.properties as :app (falls back to the debug key)
val signingProps: Properties? = rootProject.file("keystore.properties").takeIf { it.isFile }
    ?.let { f -> Properties().apply { f.inputStream().use { input -> load(input) } } }

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
    signingConfigs {
        if (signingProps != null) create("release") {
            storeFile = rootProject.file(signingProps.getProperty("storeFile"))
            storePassword = signingProps.getProperty("storePassword")
            keyAlias = signingProps.getProperty("keyAlias")
            keyPassword = signingProps.getProperty("keyPassword")
        }
    }
    buildTypes {
        debug { }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    lint { abortOnError = false; checkReleaseBuilds = false }
}
