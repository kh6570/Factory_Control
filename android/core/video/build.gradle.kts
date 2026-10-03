// In the name of God, the Most Gracious, the Most Merciful
plugins {
    alias(libs.plugins.herz.android.library.compose)
    alias(libs.plugins.herz.hilt)
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    testImplementation(project(":core:testing"))
}
