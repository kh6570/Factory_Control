// In the name of God, the Most Gracious, the Most Merciful
plugins {
    alias(libs.plugins.herz.android.library.compose)
}

dependencies {
    api(project(":core:domain"))
    api(project(":core:model"))
    api(project(":core:common"))
    api(project(":core:video"))
}
