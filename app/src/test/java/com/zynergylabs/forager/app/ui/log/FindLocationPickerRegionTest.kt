package com.zynergylabs.forager.app.ui.log

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [findLocationPickerRegion] — find-location-at-creation dispatch, Fix 3. Headless: the function
 * is the whole decision; `JournalTab`/`LogPanel` call it at their one `CentrePinLocationPicker`
 * site and `AvailabilityScreen` supplies the live fix, wiring that is read, not tested here (the
 * picker test harnesses' stub map slot ignores the region it is given).
 */
class FindLocationPickerRegionTest {

    private val searchRegion = Region(lat = 45.326, lng = -122.634, radiusKm = 15)

    @Test
    fun `with a device position in hand the picker opens there, zoomed to the tightest radius`() {
        val region = findLocationPickerRegion(deviceLocation = LatLng(44.05, -121.31), fallback = searchRegion)

        assertEquals(Region(lat = 44.05, lng = -121.31, radiusKm = Region.MIN_RADIUS_KM), region)
    }

    @Test
    fun `with no device position the picker opens on the fallback, unchanged`() {
        assertEquals(searchRegion, findLocationPickerRegion(deviceLocation = null, fallback = searchRegion))
    }
}
