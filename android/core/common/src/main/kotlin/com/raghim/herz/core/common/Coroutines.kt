// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.common

import java.time.Instant
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val dispatcher: HerzDispatchers)

enum class HerzDispatchers { Default, IO }

/** Process-wide scope that outlives screens (spec D7 `@AppScope`). */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class AppScope

fun interface AppClock {
    fun now(): Instant

    companion object {
        val System = AppClock { Instant.now() }
    }
}
