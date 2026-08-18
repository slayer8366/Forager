package com.forager.app.domain.model

/** Overall cap (pileus) shape. */
enum class CapShape(val label: String) {
    CONICAL("Conical"),
    CONVEX("Convex"),
    FLAT("Flat"),
    UMBONATE("Umbonate"),
    INFUNDIBULIFORM("Infundibuliform"),
}

/** Cap surface texture. */
enum class CapSurface(val label: String) {
    VISCID("Viscid"),
    DRY("Dry"),
    VELVETY("Velvety"),
    GLABROUS("Glabrous"),
    FIBRILLOSE("Fibrillose"),
}

/** A kind of decoration on the cap surface — a specimen can carry more than one at once (e.g. warts sitting on patches). */
enum class CapDecoration(val label: String) {
    WARTS("Warts"),
    SCALES("Scales"),
    PATCHES("Patches"),
}

/** Cap margin (edge) shape. */
enum class CapMargin(val label: String) {
    STRIATE("Striate"),
    SULCATE("Sulcate"),
    APPENDICULATE("Appendiculate"),
    INROLLED("Inrolled"),
    UPLIFTED("Uplifted"),
}

/**
 * Field-observable cap characteristics.
 *
 * [decorations] is [Feature] over a *set* rather than a single [CapDecoration]: unlike shape,
 * surface or margin — which a cap has exactly one of — decorations can co-occur (warts sitting on
 * patches is a normal, real combination), so a single-value field would force an arbitrary choice
 * between two things that were both actually seen. Absence of any decoration is itself the
 * diagnostic fact [Feature.Absent] exists for.
 *
 * [notes] is free text alongside the enums, not instead of them: a real cap often sits between two
 * enum values ("between convex and flat"), and forcing a single value would lose that rather than
 * record it honestly.
 */
data class CapSection(
    val shape: Observed<CapShape>,
    val surface: Observed<CapSurface>,
    val decorations: Feature<Set<CapDecoration>>,
    val margin: Observed<CapMargin>,
    val notes: String,
) {
    companion object {
        val EMPTY = CapSection(
            shape = Observed.NotObserved,
            surface = Observed.NotObserved,
            decorations = Feature.NotObserved,
            margin = Observed.NotObserved,
            notes = "",
        )
    }
}
