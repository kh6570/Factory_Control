// In the name of God, the Most Gracious, the Most Merciful
plugins {
    alias(libs.plugins.herz.jvm.library)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
}
