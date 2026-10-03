// In the name of God, the Most Gracious, the Most Merciful
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.raghim.herz"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.raghim.herz"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Wiring only: :app is the one module allowed to see everything (spec D3)
    listOf(
        ":core:model", ":core:common", ":core:domain",
        ":core:data", ":core:network", ":core:database", ":core:datastore",
        ":core:security", ":core:notifications", ":core:designsystem", ":core:ui",
        ":core:video", ":core:video-rtsp", ":core:video-webrtc",
        ":camera:discovery", ":camera:onvif",
        ":feature:auth", ":feature:dashboard", ":feature:cameras", ":feature:liveview",
        ":feature:alarms", ":feature:playback", ":feature:recordings", ":feature:doors",
        ":feature:devices", ":feature:rules", ":feature:users", ":feature:settings",
        ":feature:discovery",
    ).forEach { implementation(project(it)) }
    testImplementation(project(":core:testing"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}