// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.camera.onvif

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.Closeable

/**
 * Like [use], but also closes the resource as soon as the calling coroutine is cancelled,
 * which unblocks a thread stuck in a socket connect or read.
 */
internal suspend fun <C : Closeable, T> C.useCancellable(block: suspend (C) -> T): T {
    val resource = this
    return coroutineScope {
        val watcher = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                resource.closeQuietly()
            }
        }
        try {
            block(resource)
        } finally {
            watcher.cancel()
            resource.closeQuietly()
        }
    }
}

internal fun Closeable.closeQuietly() {
    try {
        close()
    } catch (e: Exception) {
        // Closing is best effort.
    }
}
