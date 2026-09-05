package com.forager.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Icon-bar-unify-container dispatch: the cluster container's fill and its children's fill are two
 * layered translucent layers of the same colour, chosen so the *composite* lands on the standing
 * [MAP_CHROME_OVER_MAP_ALPHA] — not summed to it. Layered alpha composites (`top + bottom × (1 −
 * top)`), it does not add: 0.5 under 0.3 reads as ~0.65, not 0.8. This pins the declared constants
 * to that arithmetic so the pair cannot drift apart silently. It verifies the numbers, not the
 * appearance — what the composite looks like over real terrain only a real screen can show.
 */
class MapChromeAlphaTest {

    @Test
    fun `a child fill over the container fill composites to the standing over-map alpha`() {
        val composite = srcOverAlpha(top = MAP_ICON_CLUSTER_CHILD_ALPHA, bottom = MAP_ICON_CLUSTER_CONTAINER_ALPHA)
        assertEquals(MAP_CHROME_OVER_MAP_ALPHA, composite, 0.005f)
    }

    @Test
    fun `the declared fills carry exactly the declared alphas, and the standing chrome fills the standing alpha`() {
        assertEquals(MAP_ICON_CLUSTER_CONTAINER_ALPHA, 0.6f, 0f)
        assertEquals(MAP_ICON_CLUSTER_CHILD_ALPHA, 0.5f, 0f)
        assertEquals(MAP_CHROME_OVER_MAP_ALPHA, MapIconStackButtonColorDark.alpha, 0.0001f)
        assertEquals(MAP_CHROME_OVER_MAP_ALPHA, MapIconStackButtonColorLight.alpha, 0.0001f)
    }

    /** The arithmetic itself, against the worked example the dispatch warned about: summing would say 0.8, compositing says ~0.65. */
    @Test
    fun `srcOverAlpha composites rather than adds`() {
        assertEquals(0.65f, srcOverAlpha(top = 0.3f, bottom = 0.5f), 0.0001f)
        assertEquals(1f, srcOverAlpha(top = 1f, bottom = 0.5f), 0.0001f)
        assertEquals(0.5f, srcOverAlpha(top = 0f, bottom = 0.5f), 0.0001f)
    }
}
