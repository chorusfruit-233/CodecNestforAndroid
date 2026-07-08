import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val ffmpegKitNextAar = layout.projectDirectory.file("libs/ffmpeg-kit-next.aar").asFile
val ffmpegKitNextAarPath = ffmpegKitNextAar.absolutePath

android {
    namespace = "com.fruit.ffmpeggui"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.fruit.ffmpeggui"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("Boolean", "HAS_FFMPEG_KIT_NEXT", ffmpegKitNextAar.exists().toString())
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

tasks.register("verifyFfmpegKitNextAar") {
    group = "verification"
    description = "Checks that the local FFmpegKitNext AAR artifact is available."
    val expectedAarPath = ffmpegKitNextAarPath
    doLast {
        if (!File(expectedAarPath).exists()) {
            throw GradleException(
                "FFmpegKitNext AAR not found at app/libs/ffmpeg-kit-next.aar. " +
                    "Build FFmpegKitNext for Android and copy the AAR to that path before release packaging."
            )
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn("verifyFfmpegKitNextAar")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    if (ffmpegKitNextAar.exists()) {
        implementation(files(ffmpegKitNextAar))
    }
    implementation("com.arthenica:smart-exception-java:0.2.1")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
