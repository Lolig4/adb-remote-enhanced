// On-device server (assets/server_v<serverVersion>.jar, see gradle.properties): an APK started on the TV via `app_process`.
// Reconstructed from the original server_v1.1.0.jar (com.jcapps.adbserver 1.1.0, versionCode 2).
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jcapps.adbserver"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.jcapps.adbserver"
        minSdk = 21
        targetSdk = 35
        versionCode = 2
        versionName = property("serverVersion") as String
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
    sourceSets["main"].java.srcDirs("src/main/kotlin")
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug") // app_process ignores the signature
        }
    }
    lint { abortOnError = false; checkReleaseBuilds = false }
}

dependencies {
    implementation("com.koushikdutta.async:androidasync:3.1.0")   // AsyncHttpServer (HTTP API)
    implementation("com.google.code.gson:gson:2.11.0")             // "GSON (2.11.0)" strings
    implementation("com.guichaguri:minimalftp:1.0.6")              // FTP server for /ftp/start (RECON-UNSURE: library identity)
}
