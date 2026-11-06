package com.zynergylabs.forager.app.testing

/**
 * The made-up place the tests stand at, P: the summit of Mount Hood, a public summit where nobody lives (dispatch
 * 2026-11-05-11, RECORD -2182, the owner: "Replace them in the repo"). The tests used to stand at a point near the
 * owner's home. Every test value taken from there moved here by one translation, so the points keep their places
 * relative to each other.
 *
 * A new test that needs a place uses this one, or a point a fixed step from it, and never a real address.
 * [MadeUpPlaceTest] pins [LABEL] and [MGRS] against the app's own formatter and converter.
 */
object MadeUpPlace {
    const val LAT: Double = 45.3736
    const val LNG: Double = -121.6959

    /** P to four places, as the app prints a pair (`decimalDegreesLabel`, `coordinatesStripText`). */
    const val LABEL: String = "45.3736, -121.6959"

    /** P's grid reference, as `MgrsConverter` writes it. */
    const val MGRS: String = "10T FR 02112 25281"
}
