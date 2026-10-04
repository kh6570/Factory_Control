// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

    /** Null until the user chooses. Callers treat null as "ask for a fingerprint". */
    val requireFingerprint: Flow<Boolean?> = store.data.map { it[REQUIRE_FINGERPRINT] }

    suspend fun setRequireFingerprint(required: Boolean) {
        store.edit { it[REQUIRE_FINGERPRINT] = required }
    }

    /** Null until the user picks a column count. Callers treat null as automatic. */
    val liveGridColumns: Flow<Int?> = store.data.map { it[LIVE_GRID_COLUMNS] }

    suspend fun setLiveGridColumns(columns: Int?) {
        store.edit { prefs ->
            if (columns == null) prefs.remove(LIVE_GRID_COLUMNS) else prefs[LIVE_GRID_COLUMNS] = columns
        }
    }

    private companion object {
        val HOLD_TO_OPEN_MS = longPreferencesKey("hold_to_open_ms")
        val REQUIRE_FINGERPRINT = booleanPreferencesKey("require_fingerprint")
        val LIVE_GRID_COLUMNS = intPreferencesKey("live_grid_columns")
    }
}
