// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.raghim.herz.core.datastore.UserPreferencesDataSource
import com.raghim.herz.core.domain.door.HoldToOpen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** In memory: file-backed DataStore cannot replace its file on a Windows JVM. */
private class InMemoryPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

class PreferencesUserSettingsRepositoryTest {

    private val repository = PreferencesUserSettingsRepository(UserPreferencesDataSource(InMemoryPreferences()))

    @Test
    fun `defaults to 2 s`() = runTest {
        assertEquals(HoldToOpen.Default, repository.holdToOpen.first())
        assertEquals(2.seconds, HoldToOpen.Default)
    }

    @Test
    fun `stores a normalized value`() = runTest {
        repository.setHoldToOpen(5.seconds)
        assertEquals(3.seconds, repository.holdToOpen.first())

        repository.setHoldToOpen(1600.milliseconds)
        assertEquals(1500.milliseconds, repository.holdToOpen.first())
    }
}
