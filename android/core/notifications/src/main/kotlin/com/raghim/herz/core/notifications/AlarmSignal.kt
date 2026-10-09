// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.notifications

import com.raghim.herz.core.domain.sensor.AlarmAnnouncer
import com.raghim.herz.core.model.AlarmSound
import com.raghim.herz.core.model.AlarmStyle
import com.raghim.herz.core.model.RingingAlarm

/** What the phone actually plays. [AlarmSignal] decides the order. */
internal interface AlarmOutput {
    fun play(sound: AlarmSound)

    fun vibrate()

    fun show(alarm: RingingAlarm)

    fun release()
}

/**
 * One alarm at a time. A new start releases the previous sound, vibration, and notification
 * before anything new begins, so they never stack.
 */
internal class AlarmSignal(private val output: AlarmOutput) : AlarmAnnouncer {
    override fun start(alarm: RingingAlarm) {
        output.release()
        when (val style = alarm.style) {
            AlarmStyle.VibrationOnly -> output.vibrate()
            is AlarmStyle.Sound -> {
                output.play(style.sound)
                output.vibrate()
            }
        }
        output.show(alarm)
    }

    override fun stop() {
        output.release()
    }
}
