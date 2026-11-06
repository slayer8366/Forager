package com.zynergylabs.forager.app.domain

/** Where a volumetric soil moisture figure sits on the plain three-step scale (owner, RECORD -656). */
enum class SoilMoistureLevel { DRY, MOIST, WET }

/**
 * The plain Dry / Moist / Wet scale for a volumetric soil moisture figure (m³/m³), which is what
 * Open-Meteo serves and what [com.zynergylabs.forager.app.domain.model.TripWindow.meanShallowSoilMoistureM3M3]
 * carries. The owner chose "Plain scales + real charts (Recommended)" (RECORD -656): the scale word,
 * with the figure under it.
 *
 * **The two thresholds are a proposal awaiting the owner (dispatch -668: "they are a stop, not a
 * guess").** Source: FAO Irrigation and Drainage Paper 56, Table 19, "Typical soil water
 * characteristics for different soil types" (fao.org/4/x0490e/x0490e0c.htm, read 2026-10-07).
 * - [DRY_BELOW_M3M3] 0.15: inside the wilting-point range of loam (0.07–0.17) and silt loam
 *   (0.09–0.21), where plants can no longer draw water.
 * - [WET_FROM_M3M3] 0.30: the top of loam's field-capacity range (0.20–0.30), the most a loam holds
 *   once it has drained after a soaking.
 *
 * **What this cannot know:** soil texture. The same figure reads differently in sand (field capacity
 * 0.07–0.17, so 0.15 is moist) and clay (wilting point 0.20–0.24, so 0.22 is dry). Open-Meteo serves
 * no texture, so one loam-centred scale is applied everywhere. That is why the figure stays under
 * the word.
 */
object SoilMoistureScale {
    const val DRY_BELOW_M3M3 = 0.15
    const val WET_FROM_M3M3 = 0.30

    fun levelOf(m3m3: Double): SoilMoistureLevel = when {
        m3m3 < DRY_BELOW_M3M3 -> SoilMoistureLevel.DRY
        m3m3 < WET_FROM_M3M3 -> SoilMoistureLevel.MOIST
        else -> SoilMoistureLevel.WET
    }
}
