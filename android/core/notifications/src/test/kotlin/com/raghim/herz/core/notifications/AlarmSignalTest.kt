// In the name of God, the Most Gracious, the Most Merciful
package com.raghim.herz.core.notifications

import com.raghim.herz.core.model.AlarmSound
import com.raghim.herz.core.model.AlarmStyle
import com.raghim.herz.core.model.RingingAlarm
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmSignalTest {
    private val output = RecordingOutput()
    private val signal = AlarmSignal(output)

    @Test
    fun aSoundAlarmReleasesFirstThenPlaysAndVibrates() {
        signal.start(RingingAlarm("gate", "Gate", AlarmStyle.Sound(AlarmSound.RINGTONE)))

        assertEquals(listOf("release", "play:RINGTONE", "vibrate", "show:gate"), output.events)
    }

    @Test
    fun vibrationOnlyDoesNotPlayASound() {
        signal.start(RingingAlarm("gate", "Gate", AlarmStyle.VibrationOnly))

        assertEquals(listOf("release", "vibrate", "show:gate"), output.events)
    }

    @Test
    fun aSecondAlarmReleasesTheFirstBeforeStarting() {
        signal.start(RingingAlarm("gate", "Gate", AlarmStyle.VibrationOnly))
        output.events.clear()

        signal.start(RingingAlarm("yard", "Yard", AlarmStyle.Sound(AlarmSound.ALARM)))

        assertEquals("release", output.events.first())
        assertEquals(listOf("release", "play:ALARM", "vibrate", "show:yard"), output.events)
    }

    @Test
    fun stopReleasesEverything() {
        signal.stop()

        assertEquals(listOf("release"), output.events)
    }

    private class RecordingOutput : AlarmOutput {
        val events = mutableListOf<String>()

        override fun play(sound: AlarmSound) {
            events += "play:$sound"
        }

        override fun vibrate() {
            events += "vibrate"
        }

        override fun show(alarm: RingingAlarm) {
            events += "show:${alarm.sensorId}"
        }

        override fun release() {
            events += "release"
        }
    }
}
