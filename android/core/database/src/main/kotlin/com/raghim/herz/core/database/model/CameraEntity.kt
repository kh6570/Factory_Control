// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A saved camera. Credentials are stored only as ciphertext from `CredentialCipher`. */
@Entity(tableName = "cameras")
data class CameraEntity(
    @PrimaryKey val id: String,
    val name: String,
    val host: String,
    val mainStreamUri: String,
    val subStreamUri: String?,
    val manufacturer: String?,
    val model: String?,
    val addedAtEpochMs: Long,
    val encryptedUsername: String?,
    val encryptedPassword: String?,
) {
    override fun toString(): String =
        "CameraEntity(id=$id, name=$name, host=$host, hasCredentials=${encryptedPassword != null})"
}
