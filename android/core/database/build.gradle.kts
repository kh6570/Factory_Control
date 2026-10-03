// In the name of God, the Most Gracious, the Most Merciful
plugins {
    alias(libs.plugins.herz.android.library)
    alias(libs.plugins.herz.hilt)
    alias(libs.plugins.herz.android.room)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
}
