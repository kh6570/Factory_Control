// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.video.rtsp

import com.raghim.herz.core.model.CameraCredentials
import java.net.URLEncoder

internal object RtspUris {

    /**
     * Returns [uri] with percent-encoded `user:password@` userinfo. A uri that already
     * has userinfo, or has no `scheme://` authority, is returned unchanged.
     */
    fun withCredentials(uri: String, credentials: CameraCredentials?): String {
        if (credentials == null) return uri
        val schemeEnd = uri.indexOf("://")
        if (schemeEnd < 0) return uri
        val authorityStart = schemeEnd + 3
        val authorityEnd = uri.indexOfAny(charArrayOf('/', '?', '#'), authorityStart)
            .let { if (it < 0) uri.length else it }
        if (uri.substring(authorityStart, authorityEnd).contains('@')) return uri
        val userInfo = encode(credentials.username) + ":" + encode(credentials.password)
        return uri.substring(0, authorityStart) + userInfo + "@" + uri.substring(authorityStart)
    }

    /** URLEncoder is form encoding; userinfo needs `%20`, not `+`, for spaces. */
    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
