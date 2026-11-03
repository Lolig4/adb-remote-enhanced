buildscript {
    dependencies { classpath("net.lingala.zip4j:zip4j:2.11.5") } // packs the server asset (see packServerAsset)
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("kotlin-parcelize")
}

android {
    namespace = "com.jcapps.adbremote"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jcapps.adbremote"
        minSdk = 23
        targetSdk = 35
        versionCode = 6
        versionName = "1.1.0"
        // Original: AdMob App-ID aus dem Manifest
        manifestPlaceholders["admobAppId"] = "ca-app-pub-8938536730349592~6489945861"
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") } // adb-Binary (libfs*.so)
        vectorDrawables { useSupportLibrary = true } // Original enthält keine gerasterten Vektor-PNGs
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }

    sourceSets["main"].java.srcDirs("src/main/kotlin")
    // server_v1.1.0.jar is built from :server (see packServerAsset), not taken from the original APK
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/serverAsset"))

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        jniLibs { useLegacyPackaging = true }   // libfs*.so nach nativeLibraryDir entpacken (exec)
    }

    signingConfigs {
        create("test") {
            storeFile = rootProject.file("keystore/test.keystore")
            storePassword = "android"
            keyAlias = "testkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // R8 like the original, but app code is kept as-is (proguard-rules.pro): only libraries
            // are shrunk, then unused resources are removed (isShrinkResources needs minify).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            isDebuggable = false             // -> keine 16-KB-Warnung
            signingConfig = signingConfigs.getByName("test")
        }
        // QA: identical to release plus the in-process DebugBridge from src/qa (crash sweeps via tools/ar.sh)
        create("qa") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            applicationIdSuffix = ".qa"       // installs next to the original com.jcapps.adbremote
            isMinifyEnabled = false           // test builds without R8: fast incremental builds
            isShrinkResources = false
        }
    }
    sourceSets["qa"].java.srcDirs("src/qa/kotlin")
}

dependencies {
    // Kotlin / Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // AndroidX UI / Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")   // installSplashScreen() in MainActivity (SplashScreen.Impl im Original)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.fragment:fragment-ktx:1.8.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.2.0")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.documentfile:documentfile:1.0.1")    // DocumentFile in den Transfer-Workern (FilesActivity)
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.emoji2:emoji2:1.5.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-process:2.8.3")   // ProcessLifecycleOwner (AdbRemoteApp)

    // Room (dao.Fav)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Startup / ProfileInstaller / Window
    implementation("androidx.startup:startup-runtime:1.1.1")
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    implementation("androidx.window:window:1.3.0")

    // Custom Tabs / WebKit
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.webkit:webkit:1.11.0")

    // Material & UI-Extras
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.android.flexbox:flexbox:3.0.0")
    implementation("com.airbnb.android:lottie:6.4.0")
    implementation("com.tbuonomo:dotsindicator:5.0")

    // Google Play
    implementation("com.google.android.play:review-ktx:2.0.1")
    implementation("com.google.android.gms:play-services-oss-licenses:17.1.0")

    // AdMob
    implementation("com.google.android.gms:play-services-ads:23.2.0")

    // Krypto (adbkey)
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    // HTTP: Retrofit auf OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.google.code.gson:gson:2.11.0")              // String "Gson 2.11.0" im Original

    // Bildladen (coil.fetch.*, ViewTarget im Original)
    implementation("io.coil-kt:coil:2.7.0")
    // Markdown (what_is_new.md / acknowledgement.md; commonmark + Markwon-Plugins)
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:linkify:4.6.2")      // LinkifyPlugin (C0554 case 0)
    // server_v1.1.0.jar ist ein passwortgeschuetztes Zip (AesExtraDataRecord)
    implementation("net.lingala.zip4j:zip4j:2.11.5")
    // java.nio.file-Backport fuer den FTP-FileSystemProvider (MaterialFiles-Stil)
    implementation("me.zhanghai.android.retrofile:library:1.1.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")
    implementation("commons-net:commons-net:3.11.1")  // FTP-Client (Original: ftp.FTPFileEntryParser)
    // VENDORED: cgutman AndroidADB (als lokales Modul :adblib ODER JitPack)
    // implementation("com.github.cgutman:AdbLib:<rev>")  // oder project(":adblib")
}

// The app ships the on-device server as assets/server_v1.1.0.jar: a password-protected zip
// (password NativeConfig.aes128Key) holding the :server APK, which ServerInstaller unpacks and
// starts on the TV via app_process.
val packServerAsset by tasks.registering {
    val serverApk = project(":server").layout.buildDirectory.file("outputs/apk/release/server-release.apk")
    val outDir = layout.buildDirectory.dir("generated/serverAsset")
    dependsOn(":server:assembleRelease")
    inputs.file(serverApk)
    outputs.dir(outDir)
    doLast {
        val dir = outDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val staged = File(temporaryDir, "server_v1.1.0.jar")
        serverApk.get().asFile.copyTo(staged, overwrite = true)
        val params = net.lingala.zip4j.model.ZipParameters().apply {
            isEncryptFiles = true
            encryptionMethod = net.lingala.zip4j.model.enums.EncryptionMethod.AES
        }
        net.lingala.zip4j.ZipFile(File(dir, "server_v1.1.0.jar"), "y7bK&0lvJh8p1qLU".toCharArray())
            .addFile(staged, params)
    }
}
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach { dependsOn(packServerAsset) }
