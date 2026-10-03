// In the name of God, the Most Gracious, the Most Merciful
plugins {
    alias(libs.plugins.herz.android.library.compose)
}

dependencies {
    api(project(":core:designsystem"))
    implementation(project(":core:model"))
}
