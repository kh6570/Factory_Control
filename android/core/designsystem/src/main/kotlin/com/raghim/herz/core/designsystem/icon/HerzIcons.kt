// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Icons missing from material-icons-core. Paths are the Material Symbols 24 dp shapes. */
object HerzIcons {
    val Videocam: ImageVector by lazy {
        icon(
            "Videocam",
            "M17,10.5V7c0,-0.55 -0.45,-1 -1,-1H4c-0.55,0 -1,0.45 -1,1v10c0,0.55 0.45,1 1,1h12" +
                "c0.55,0 1,-0.45 1,-1v-3.5l4,4v-11l-4,4z",
        )
    }

    val GridView: ImageVector by lazy {
        icon(
            "GridView",
            "M3,3v8h8V3H3zM9,9H5V5h4V9zM3,13v8h8v-8H3zM9,19H5v-4h4V19zM13,3v8h8V3H13zM19,9h-4V5h4V9z" +
                "M13,13v8h8v-8H13zM19,19h-4v-4h4V19z",
        )
    }

    val Fullscreen: ImageVector by lazy {
        icon("Fullscreen", "M7,14H5v5h5v-2H7v-3zM5,10h2V7h3V5H5v5zM17,17h-3v2h5v-5h-2v3zM14,5v2h3v3h2V5h-5z")
    }

    val FullscreenExit: ImageVector by lazy {
        icon("FullscreenExit", "M5,16h3v3h2v-5H5v2zM8,8H5v2h5V5H8v3zM14,19h2v-3h3v-2h-5v5zM16,8V5h-2v5h5V8h-3z")
    }

    val Wifi: ImageVector by lazy {
        icon(
            "Wifi",
            "M1,9l2,2c4.97,-4.97 13.03,-4.97 18,0l2,-2C16.93,2.93 7.08,2.93 1,9zM9,17l3,3 3,-3" +
                "c-1.65,-1.66 -4.34,-1.66 -6,0zM5,13l2,2c2.76,-2.76 7.24,-2.76 10,0l2,-2C15.14,9.14 8.87,9.14 5,13z",
        )
    }

    private fun icon(name: String, path: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(pathData = addPathNodes(path), fill = SolidColor(Color.Black)).build()
}
