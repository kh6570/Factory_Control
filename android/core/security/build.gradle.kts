// In the name of God, the Most Gracious, the Most Merciful
plugins {
    alias(libs.plugins.herz.android.library)
    alias(libs.plugins.herz.hilt)
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    api(libs.androidx.fragment)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.core.ktx)
}
