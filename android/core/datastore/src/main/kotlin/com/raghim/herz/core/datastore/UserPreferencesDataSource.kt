// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Raw user settings. Validation and defaults live in `:core:data`. */
class UserPreferencesDataSource @Inject constructor(
    private val store: DataStore<Preferences>,
) {
    val holdToOpenMillis: Flow<Long?> = store.data.map { it[HOLD_TO_OPEN_MS] }

    suspend fun setHoldToOpenMillis(millis: Long) {
        store.edit { it[HOLD_TO_OPEN_MS] = millis }
    }

    private companion object {
        val HOLD_TO_OPEN_MS = longPreferencesKey("hold_to_open_ms")
    }
}
