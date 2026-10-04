// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** In memory: file-backed DataStore cannot replace its file on a Windows JVM. */
private class InMemoryPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

class UserPreferencesDataSourceTest {

    private val source = UserPreferencesDataSource(InMemoryPreferences())

    @Test
    fun `hold duration is empty until set, then keeps the latest value`() = runTest {
        assertNull(source.holdToOpenMillis.first())

        source.setHoldToOpenMillis(2500)
        source.setHoldToOpenMillis(3000)

        assertEquals(3000L, source.holdToOpenMillis.first())
    }
}
