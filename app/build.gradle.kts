import java.net.URI
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
}

val engineResources = layout.buildDirectory.dir("generated/engineRes")
val prepareYtDlp by tasks.registering {
    val output = engineResources.map { it.file("raw/ytdlp") }
    outputs.file(output)
    doLast {
        val target = output.get().asFile
        target.parentFile.mkdirs()
        val bytes = URI("https://github.com/yt-dlp/yt-dlp/releases/download/2026.08.19/yt-dlp")
            .toURL()
            .openStream()
            .use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
        check(digest == "1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6") {
            "yt-dlp checksum mismatch"
        }
        target.writeBytes(bytes)
    }
}

android {
    namespace = "com.homesweetlove.instaytgrabber"
    compileSdk = 35
    sourceSets["main"].res.srcDir(engineResources)

    defaultConfig {
        applicationId = "com.homesweetlove.instaytgrabber"
        minSdk = 24
        targetSdk = 35
        versionCode = 5
        versionName = "0.3.1"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

chaquopy {
    defaultConfig {
        version = "3.11"
        pip {
            install("gallery-dl==1.32.14")
            install("instaloader==4.15.3")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
}


tasks.named("preBuild") {
    dependsOn(prepareYtDlp)
}
