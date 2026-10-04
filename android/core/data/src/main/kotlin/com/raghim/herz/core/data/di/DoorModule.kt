// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.data.di

import android.os.Build
import com.raghim.herz.core.data.door.DoorSignatureVerifier
import com.raghim.herz.core.data.door.SimulatedDoorRepository
import com.raghim.herz.core.data.settings.PreferencesUserSettingsRepository
import com.raghim.herz.core.domain.door.DoorRepository
import com.raghim.herz.core.domain.door.UserSettingsRepository
import com.raghim.herz.core.security.DeviceKey
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.security.GeneralSecurityException
import java.security.Signature

/** Swap [SimulatedDoorRepository] for a server or lock-controller repository here; nothing else changes. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class DoorModule {
    @Binds
    abstract fun bindDoorRepository(impl: SimulatedDoorRepository): DoorRepository

    @Binds
    abstract fun bindUserSettingsRepository(impl: PreferencesUserSettingsRepository): UserSettingsRepository

    companion object {
        /** On the device, the simulator verifies with the public half of the real [DeviceKey]. */
        @Provides
        fun provideDoorSignatureVerifier(): DoorSignatureVerifier = DoorSignatureVerifier { payload, signature ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@DoorSignatureVerifier false
            val key = DeviceKey.publicKey() ?: return@DoorSignatureVerifier false
            try {
                Signature.getInstance("SHA256withECDSA").run {
                    initVerify(key)
                    update(payload)
                    verify(signature)
                }
            } catch (_: GeneralSecurityException) {
                false
            }
        }
    }
}
