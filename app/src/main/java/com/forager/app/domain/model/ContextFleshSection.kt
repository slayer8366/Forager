package com.forager.app.domain.model

/** Texture of the cut/broken flesh. */
enum class FleshTexture(val label: String) {
    BRITTLE("Brittle"),
    CHALKY("Chalky"),
    TOUGH("Tough"),
    WOODY("Woody"),
    GELATINOUS("Gelatinous"),
}

/**
 * Flesh (context) characteristics observed when the specimen is cut or broken.
 *
 * [colorChangeOnCutting] and [exudate] (latex) are [Feature]`<String>` rather than an enum: many
 * specimens show neither, which is itself diagnostic ([Feature.Absent]), but the *colour* of a
 * change or an exudate is not a closed set an app should own — free text is the honest
 * representation once presence is established.
 */
data class ContextFleshSection(
    val texture: Observed<FleshTexture>,
    val colorChangeOnCutting: Feature<String>,
    val exudate: Feature<String>,
    val notes: String,
) {
    companion object {
        val EMPTY = ContextFleshSection(
            texture = Observed.NotObserved,
            colorChangeOnCutting = Feature.NotObserved,
            exudate = Feature.NotObserved,
            notes = "",
        )
    }
}
