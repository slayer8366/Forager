package com.zynergylabs.forager.app.ui.map

import android.view.Gravity
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Dispatch 2026-10-18-07, RECORD -1309 and -1315 (the owner: "And yes have the maps Libre logo on the right"; then option 1, a row
 * beside the "i" at the bottom end). The logo and the "i" are MapLibre's own views, placed through `UiSettings`, and a real MapView
 * cannot be built under Robolectric, so neither can be measured here: that is a device item. What is held here is the placement
 * `SightingsMap` hands the logo, [logoBesideAttribution], fed with the button's own placement from [attributionMarginsPx] (every
 * map's bottom end) and [attributionBesideCreditMarginsPx] (RECORD -954, the short landscape window), in both layout directions.
 * The rectangles are laid out the way a FrameLayout lays out a gravity and margins, with MapLibre 13.5.0's own sizes: the logo
 * 88 x 23 dp (`maplibre_logo_icon.xml`), the "i" 21 dp (`maplibre_info_icon_default.xml`).
 */
class LogoBesideAttributionTest {

    private val density = 3f
    private fun px(dp: Float) = (dp * density).toInt()

    /** MapLibre's own attribution margins at this density: 92 dp start, 4 dp elsewhere (`MapLibreMapOptions.java:283-291`). */
    private val defaults = intArrayOf(px(92f), px(4f), px(4f), px(4f))
    private val offset = px(ATTRIBUTION_BUTTON_DP + LOGO_ATTRIBUTION_GAP_DP)
    private val buttonPx = px(21f)
    private val logoW = px(88f)
    private val logoH = px(23f)

    private fun rect(gravity: Int, margins: IntArray, w: Int, h: Int, mapW: Int, mapH: Int): Rect {
        val bottom = (mapH - margins[3]).toFloat()
        val atEnd = (gravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK) == Gravity.END
        // Left to right only: END is the right edge, START the left.
        val left = if (atEnd) (mapW - margins[2] - w).toFloat() else margins[0].toFloat()
        return Rect(left, bottom - h, left + w, bottom)
    }

    @Test
    fun `at the bottom end the logo is in a row just inboard of the i, sharing its bottom, in portrait`() {
        val iMargins = attributionMarginsPx(defaults, bottomInsetPx = px(80f), endInsetPx = 0, isRtl = false)
        val logo = logoBesideAttribution(Gravity.BOTTOM or Gravity.END, iMargins, offset, isRtl = false)

        assertEquals(Gravity.BOTTOM or Gravity.END, logo.gravity)
        assertArrayEquals(intArrayOf(iMargins[0], iMargins[1], iMargins[2] + offset, iMargins[3]), logo.margins)

        val mapW = px(384f)
        val mapH = px(800f)
        val i = rect(Gravity.BOTTOM or Gravity.END, iMargins, buttonPx, buttonPx, mapW, mapH)
        val l = rect(logo.gravity, logo.margins, logoW, logoH, mapW, mapH)
        assertFalse("the logo must not cover the i: logo $l, i $i", l.overlaps(i))
        assertEquals("the logo ends a gap short of the i", i.left - px(LOGO_ATTRIBUTION_GAP_DP) - (px(ATTRIBUTION_BUTTON_DP) - buttonPx), l.right)
        assertEquals("one bottom line", i.bottom, l.bottom)
        assertFalse("still on the map", l.left < 0f)
    }

    @Test
    fun `at the bottom end with the rail inset the logo moves inboard with the i`() {
        val iMargins = attributionMarginsPx(defaults, bottomInsetPx = px(24f), endInsetPx = px(80f), isRtl = false)
        val logo = logoBesideAttribution(Gravity.BOTTOM or Gravity.END, iMargins, offset, isRtl = false)
        assertArrayEquals(intArrayOf(iMargins[0], iMargins[1], iMargins[2] + offset, iMargins[3]), logo.margins)
    }

    @Test
    fun `at the bottom end in a right-to-left layout the offset goes on the same side the i's end inset does`() {
        val iMargins = attributionMarginsPx(defaults, bottomInsetPx = px(80f), endInsetPx = px(40f), isRtl = true)
        val logo = logoBesideAttribution(Gravity.BOTTOM or Gravity.END, iMargins, offset, isRtl = true)
        assertEquals(Gravity.BOTTOM or Gravity.END, logo.gravity)
        assertArrayEquals(intArrayOf(iMargins[0] + offset, iMargins[1], iMargins[2], iMargins[3]), logo.margins)
    }

    @Test
    fun `beside the credit strip (RECORD -954) the logo follows the i, just past it, sharing its bottom`() {
        val creditWidth = px(306f)
        val iMargins = attributionBesideCreditMarginsPx(defaults, creditWidth, px(ATTRIBUTION_BESIDE_CREDIT_GAP_DP), creditBottomPx = px(24f), isRtl = false)
        val logo = logoBesideAttribution(Gravity.BOTTOM or Gravity.START, iMargins, offset, isRtl = false)

        assertEquals(Gravity.BOTTOM or Gravity.START, logo.gravity)
        assertArrayEquals(intArrayOf(iMargins[0] + offset, iMargins[1], iMargins[2], iMargins[3]), logo.margins)

        val mapW = px(823f)
        val mapH = px(384f)
        val i = rect(Gravity.BOTTOM or Gravity.START, iMargins, buttonPx, buttonPx, mapW, mapH)
        val l = rect(logo.gravity, logo.margins, logoW, logoH, mapW, mapH)
        val credit = Rect(0f, i.bottom - px(20f), creditWidth.toFloat(), i.bottom)
        assertFalse("the logo must not cover the i: logo $l, i $i", l.overlaps(i))
        assertFalse("the logo must not cover the credit strip: logo $l, strip $credit", l.overlaps(credit))
        assertEquals("one bottom line", i.bottom, l.bottom)
        assertFalse("past the i, not before it", l.left < i.right)
    }

    @Test
    fun `beside the credit strip in a right-to-left layout the offset goes on the i's start side`() {
        val iMargins = attributionBesideCreditMarginsPx(defaults, px(200f), px(ATTRIBUTION_BESIDE_CREDIT_GAP_DP), creditBottomPx = px(24f), isRtl = true)
        val logo = logoBesideAttribution(Gravity.BOTTOM or Gravity.START, iMargins, offset, isRtl = true)
        assertArrayEquals(intArrayOf(iMargins[0], iMargins[1], iMargins[2] + offset, iMargins[3]), logo.margins)
    }

    @Test
    fun `the i's own margins are not changed by placing the logo`() {
        val iMargins = attributionMarginsPx(defaults, bottomInsetPx = px(80f), endInsetPx = 0, isRtl = false)
        val before = iMargins.copyOf()
        logoBesideAttribution(Gravity.BOTTOM or Gravity.END, iMargins, offset, isRtl = false)
        assertArrayEquals(before, iMargins)
    }
}
