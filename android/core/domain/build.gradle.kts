// In the name of God, the Most Gracious, the Most Merciful
plugins {
    alias(libs.plugins.herz.jvm.library)
    alias(libs.plugins.ksp)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.dagger)
    ksp(libs.dagger.compiler)
}
