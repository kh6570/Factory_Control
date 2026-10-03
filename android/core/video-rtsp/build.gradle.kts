// In the name of God, the Most Gracious, the Most Merciful
plugins {
    alias(libs.plugins.herz.android.library.compose)
    alias(libs.plugins.herz.hilt)
}

dependencies {
    implementation(project(":core:video"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.rtsp)

    testImplementation(project(":core:testing"))
}
