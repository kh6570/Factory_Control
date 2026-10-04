// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.data.settings

import com.raghim.herz.core.datastore.UserPreferencesDataSource
import com.raghim.herz.core.domain.door.HoldToOpen
import com.raghim.herz.core.domain.door.UserSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal class PreferencesUserSettingsRepository @Inject constructor(
    private val preferences: UserPreferencesDataSource,
) : UserSettingsRepository {

    override val holdToOpen: Flow<Duration> = preferences.holdToOpenMillis.map { millis ->
        millis?.milliseconds?.let(HoldToOpen::normalize) ?: HoldToOpen.Default
    }

    override suspend fun setHoldToOpen(duration: Duration) {
        preferences.setHoldToOpenMillis(HoldToOpen.normalize(duration).inWholeMilliseconds)
    }
}
