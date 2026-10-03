// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

object HerzSdk {
    const val COMPILE = 37
    const val MIN = 26
    val JAVA = JavaVersion.VERSION_11
}

val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** `:core:video-rtsp` becomes `com.raghim.herz.core.video.rtsp`. */
fun Project.herzNamespace(): String =
    "com.raghim.herz." + path.removePrefix(":").replace(':', '.').replace('-', '.')

internal fun Project.configureAndroidLibrary(extension: LibraryExtension) {
    extension.apply {
        namespace = herzNamespace()
        compileSdk = HerzSdk.COMPILE
        defaultConfig.minSdk = HerzSdk.MIN
        defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        compileOptions.sourceCompatibility = HerzSdk.JAVA
        compileOptions.targetCompatibility = HerzSdk.JAVA
    }
}
