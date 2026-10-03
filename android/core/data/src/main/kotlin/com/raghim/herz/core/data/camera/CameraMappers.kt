// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.data.camera

import com.raghim.herz.core.database.model.ActiveSessionRow
import com.raghim.herz.core.database.model.CameraEntity
import com.raghim.herz.core.model.ActiveCamera
import com.raghim.herz.core.model.Camera
import com.raghim.herz.core.model.SessionSource
import com.raghim.herz.core.model.StreamState
import java.time.Instant

internal fun CameraEntity.toModel(): Camera = Camera(
    id = id,
    name = name,
    host = host,
    mainStreamUri = mainStreamUri,
    subStreamUri = subStreamUri,
    manufacturer = manufacturer,
    model = model,
    addedAt = Instant.ofEpochMilli(addedAtEpochMs),
)

internal fun Camera.toEntity(encryptedUsername: String?, encryptedPassword: String?): CameraEntity = CameraEntity(
    id = id,
    name = name,
    host = host,
    mainStreamUri = mainStreamUri,
    subStreamUri = subStreamUri,
    manufacturer = manufacturer,
    model = model,
    addedAtEpochMs = addedAt.toEpochMilli(),
    encryptedUsername = encryptedUsername,
    encryptedPassword = encryptedPassword,
)

internal fun ActiveSessionRow.toModel(): ActiveCamera = ActiveCamera(
    cameraId = cameraId,
    name = name,
    source = SessionSource.entries.firstOrNull { it.name == source } ?: SessionSource.MANUAL,
    state = StreamState.LIVE,
    startedBy = null,
    startedAt = Instant.ofEpochMilli(startedAtEpochMs),
)
