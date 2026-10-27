package com.zynergylabs.forager.app.domain.referencetrails

import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Step 8d (dispatch 2026-10-26-24; RECORD -1717, -1718): the two dates the official-trails card shows, read from the
 * Worker's `/reference-trails-manifest.json`. Plain Kotlin with no Android or UI type, so it runs headless.
 *
 * Each date is a source's `fetched` field, the day the data was downloaded from the agency (-1718). The manifest's
 * `built_at` and each source's `dates` object are deliberately not read: `built_at` is the archive's build time, and the
 * agency dates mean different things for the two sources (-1718, Aspen's reading of the served file).
 *
 * A null date is a date this copy does not have; the card leaves it out and never stands one in.
 */
data class ReferenceTrailsSourceDates(val forestService: LocalDate?, val blm: LocalDate?)

/** The manifest's `layer` value for each source (the served file, read by Aspen from R2; -1718). */
const val FOREST_SERVICE_LAYER = "fs_trails"
const val BLM_LAYER = "blm_trails"

/**
 * Reads [text] as the manifest. Unknown keys are ignored, because the file will grow (the dispatch). A failure means
 * the text is not a manifest at all (not JSON, not an object, no `sources` array); the caller keeps its old copy.
 *
 * Within a readable manifest, a source that is missing, or whose `fetched` is missing or not an ISO date, gives a null
 * date and one [warn] line saying which; the other source's date stands. Where a layer appears twice, the first is read
 * and the repeat is warned about.
 */
fun parseReferenceTrailsManifest(text: String, warn: (String) -> Unit): Result<ReferenceTrailsSourceDates> = runCatching {
    val root = Json.parseToJsonElement(text) as? JsonObject ?: throw IllegalArgumentException("the manifest is not a JSON object")
    val sources = root["sources"] as? JsonArray ?: throw IllegalArgumentException("the manifest has no sources array")
    val byLayer = mutableMapOf<String, JsonObject>()
    sources.forEach { element ->
        val source = element as? JsonObject ?: return@forEach warn("A manifest source is not a JSON object; it is skipped.")
        val layer = source.string("layer") ?: return@forEach warn("A manifest source has no layer; it is skipped.")
        if (layer in byLayer) warn("The manifest lists layer '$layer' twice; the first is read.") else byLayer[layer] = source
    }
    ReferenceTrailsSourceDates(
        forestService = fetchedDate(byLayer[FOREST_SERVICE_LAYER], FOREST_SERVICE_LAYER, warn),
        blm = fetchedDate(byLayer[BLM_LAYER], BLM_LAYER, warn),
    )
}

private fun fetchedDate(source: JsonObject?, layer: String, warn: (String) -> Unit): LocalDate? {
    if (source == null) return null.also { warn("The manifest has no '$layer' source; its date is left out.") }
    val fetched = source.string("fetched") ?: return null.also { warn("The manifest's '$layer' source has no fetched date; it is left out.") }
    return try {
        LocalDate.parse(fetched)
    } catch (e: DateTimeParseException) {
        warn("The manifest's '$layer' fetched value '${fetched.take(40)}' is not a date; it is left out.")
        null
    }
}

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

/**
 * The card's Sources line, and the line under it when there is no copy at all (owner, RECORD -1717; dispatch
 * 2026-10-26-24, "The dates"). [hint] is null whenever a copy exists, even one with both dates missing: the user has
 * been online then, and the line would be untrue.
 */
data class ReferenceTrailsSourcesText(val line: String, val hint: String?)

/** The bold lead of the Sources line; the card bolds exactly this prefix. */
const val REFERENCE_SOURCES_LEAD = "Sources:"

/** Said under the Sources line until a copy of the manifest has ever been fetched (-1717). */
const val REFERENCE_SOURCES_NO_COPY_HINT = "Dates show once you've been online."

/**
 * The Sources line for [dates] (null: no copy yet), each date written by [formatDate], the app's date style. A source
 * whose date is missing reads as the no-copy line does for it ("Forest Service · CC BY 4.0."), never with a stand-in.
 * The licences are fixed text, as the owner's card gives them, not read from the manifest.
 */
fun referenceTrailsSourcesText(dates: ReferenceTrailsSourceDates?, formatDate: (LocalDate) -> String): ReferenceTrailsSourcesText {
    fun part(name: String, date: LocalDate?, licence: String) =
        if (date == null) "$name · $licence." else "$name data from ${formatDate(date)} · $licence."
    val line = "$REFERENCE_SOURCES_LEAD ${part("Forest Service", dates?.forestService, "CC BY 4.0")} ${part("BLM", dates?.blm, "public domain")}"
    return ReferenceTrailsSourcesText(line = line, hint = null)
}
