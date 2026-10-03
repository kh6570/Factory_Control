// In the name of God, the Most Gracious, the Most Merciful
plugins {
    alias(libs.plugins.herz.android.library.compose)
}

dependencies {
    implementation(project(":core:video"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
}
