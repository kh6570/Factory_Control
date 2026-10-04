// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.model

import java.time.Instant

/**
 * A door with a remotely released lock. [lock] comes from the lock controller's acknowledgement,
 * [contact] from the door's reed contact, so the app shows the real state (system design 4.5).
 */
data class Door(
    val id: String,
    val name: String,
    val area: String? = null,
    /** Cameras that show this door. Used to list the doors of the cameras on the wall first. */
    val linkedCameraIds: Set<String> = emptySet(),
    val lock: LockState = LockState.LOCKED,
    val contact: DoorContact = DoorContact.UNKNOWN,
    val isOnline: Boolean = true,
    /** Shown in the fold-out door list on the Live screen. */
    val onLivePanel: Boolean = false,
    /** When an unlocked door locks again on its own. Null while locked. */
    val unlockedUntil: Instant? = null,
)

enum class LockState { LOCKED, UNLOCKED }

enum class DoorContact { CLOSED, OPEN, UNKNOWN }

/** One-time nonce for a door open (spec D11). Valid until [expiresAt], usable once. */
data class DoorChallenge(
    val doorId: String,
    val nonce: String,
    val expiresAt: Instant,
) {
    /** The bytes the device key signs: `doorId|nonce|timestamp` (spec D11). */
    fun payload(timestamp: Instant): ByteArray = "$doorId|$nonce|${timestamp.epochSecond}".encodeToByteArray()
}

/** A challenge signed with the device key after biometric confirmation. */
class SignedDoorCommand(
    val doorId: String,
    val nonce: String,
    val timestamp: Instant,
    val signature: ByteArray,
) {
    override fun toString(): String = "SignedDoorCommand(doorId=$doorId, timestamp=$timestamp, signature=***)"
}
