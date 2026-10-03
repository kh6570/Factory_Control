// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.security.di

import com.raghim.herz.core.security.CredentialCipher
import com.raghim.herz.core.security.KeystoreCredentialCipher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SecurityModule {
    @Binds
    @Singleton
    abstract fun bindCredentialCipher(impl: KeystoreCredentialCipher): CredentialCipher
}
