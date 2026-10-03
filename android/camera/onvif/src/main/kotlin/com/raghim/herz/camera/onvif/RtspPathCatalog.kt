// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.camera.onvif

/** A main stream path and its matching lower-resolution sub stream, if the brand has one. */
data class StreamPaths(val main: String, val sub: String?)

/** Well-known RTSP paths per camera brand. Pure Kotlin. */
object RtspPathCatalog {
    private class Brand(val keywords: List<String>, val paths: List<StreamPaths>)

    /** In rough market-share order; this is the order used when nothing matches a hint. */
    private val brands = listOf(
        Brand(
            keywords = listOf("hikvision", "hikvison", "ds-2"),
            paths = listOf(StreamPaths("/Streaming/Channels/101", "/Streaming/Channels/102")),
        ),
        Brand(
            keywords = listOf("dahua", "amcrest", "empiretech", "empire tech", "lorex", "ipc-h"),
            paths = listOf(
                StreamPaths("/cam/realmonitor?channel=1&subtype=0", "/cam/realmonitor?channel=1&subtype=1"),
            ),
        ),
        Brand(
            keywords = listOf("reolink"),
            paths = listOf(
                StreamPaths("/h264Preview_01_main", "/h264Preview_01_sub"),
                StreamPaths("/Preview_01_main", "/Preview_01_sub"),
            ),
        ),
        Brand(
            keywords = listOf("tapo", "tp-link", "tplink"),
            paths = listOf(StreamPaths("/stream1", "/stream2")),
        ),
        Brand(
            keywords = listOf("uniview"),
            paths = listOf(StreamPaths("/media/video1", "/media/video2")),
        ),
        Brand(
            keywords = listOf("imou", "ezviz"),
            paths = listOf(StreamPaths("/h264/ch1/main/av_stream", "/h264/ch1/sub/av_stream")),
        ),
        Brand(
            keywords = listOf("hanwha", "wisenet", "samsung"),
            paths = listOf(StreamPaths("/profile2/media.smp", "/profile3/media.smp")),
        ),
        Brand(
            keywords = listOf("axis"),
            paths = listOf(StreamPaths("/axis-media/media.amp", null)),
        ),
    )

    private val generic = listOf(
        StreamPaths("/live/ch00_0", "/live/ch00_1"),
        StreamPaths("/11", "/12"),
        StreamPaths("/ch0_0.h264", "/ch0_1.h264"),
        StreamPaths("/onvif1", "/onvif2"),
        StreamPaths("/live", null),
        StreamPaths("/stream", null),
        StreamPaths("/", null),
    )

    /**
     * Every known path pair, those of brands named in [hints] (manufacturer, name, model;
     * case-insensitive) first, then all other brands, then generic paths.
     */
    fun candidates(vararg hints: String?): List<StreamPaths> {
        val text = hints.filterNotNull().joinToString(" ").lowercase()
        val (matching, others) = brands.partition { brand ->
            text.isNotBlank() && brand.keywords.any { it in text }
        }
        return (matching.flatMap { it.paths } + others.flatMap { it.paths } + generic).distinct()
    }
}
