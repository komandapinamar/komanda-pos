package com.komanda.business.features.projector

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectorCalibrationConfigTest {

    @Test
    fun `rotated180 swaps opposite corners for counter-to-queue floor reading`() {
        val initial = ProjectorCalibrationConfig.presetFloor45()
        val rotated = initial.rotated180()

        assertEquals(initial.bottomRight, rotated.topLeft)
        assertEquals(initial.bottomLeft, rotated.topRight)
        assertEquals(initial.topLeft, rotated.bottomRight)
        assertEquals(initial.topRight, rotated.bottomLeft)
    }

    @Test
    fun `withCorner clamps out of bounds normalized coordinates`() {
        val initial = ProjectorCalibrationConfig.presetFlat()
        val updated = initial.withCorner(0, NormalizedPoint(-0.5f, 1.4f))

        assertEquals(0.02f, updated.topLeft.x, 0.0001f)
        assertEquals(0.98f, updated.topLeft.y, 0.0001f)
    }

    @Test
    fun `AUTO shift mode resolves dark hours slots accurately`() {
        assertEquals(
            ProjectorShiftMode.NIGHT_START,
            ProjectorShiftMode.AUTO.resolveForHour(2, 15)
        )
        assertEquals(
            ProjectorShiftMode.BAJON_MODE,
            ProjectorShiftMode.AUTO.resolveForHour(4, 10)
        )
        assertEquals(
            ProjectorShiftMode.SUNRISE_SPECIAL,
            ProjectorShiftMode.AUTO.resolveForHour(6, 0)
        )
    }
}
