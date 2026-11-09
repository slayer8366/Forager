package com.zynergylabs.forager.app.testing

import com.zynergylabs.forager.app.domain.MgrsConverter
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.MgrsCoordinate
import com.zynergylabs.forager.app.ui.availability.decimalDegreesLabel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [MadeUpPlace]'s text forms, read from the app's own functions, so a test that pins them as literals pins what the app
 * prints. Both were first worked out by an independent script for dispatch 2026-11-05-11: the label from `%.4f`, and the
 * grid reference from a hand-written UTM and MGRS conversion that matches every point `MgrsConverterTest` pins.
 */
class MadeUpPlaceTest {

    @Test
    fun `the label is the app's four-place pair for P`() {
        assertEquals(MadeUpPlace.LABEL, decimalDegreesLabel(MadeUpPlace.LAT, MadeUpPlace.LNG))
    }

    @Test
    fun `the grid reference is MgrsConverter's for P`() {
        assertEquals(MgrsCoordinate.Grid(MadeUpPlace.MGRS), MgrsConverter.convert(LatLng(MadeUpPlace.LAT, MadeUpPlace.LNG)))
    }
}
