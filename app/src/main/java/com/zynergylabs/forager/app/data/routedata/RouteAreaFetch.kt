package com.zynergylabs.forager.app.data.routedata

import com.zynergylabs.forager.app.domain.model.GeoBoundingBox
import com.zynergylabs.forager.app.domain.routedata.RouteDataMissingReason
import java.io.File
import java.io.IOException

/**
 * Fetches [box]'s route files into [folder] and says what came of it, as the record the folder will carry. Every
 * outcome but [RegionRouteRecord.Stored] is logged with its detail (CLAUDE.md: no failure is silent). Blocking.
 *
 * - An empty [appKey] sends nothing: [RouteDataMissingReason.NO_APP_KEY]. Such a build (CI, any checkout without the
 *   key) would only be refused, and the record should say why the routes are missing, not that the server said no.
 * - 404 `outside_coverage` is [RegionRouteRecord.NoneHere]: a fact about the area, not a failure.
 * - Any file the list names failing, by network, status, size or sha256, fails the whole area: what the list offers is
 *   stored whole or not at all. Squares the list itself names as `missing` (partial coverage, the owner's ruling via
 *   the planner) are recorded on the stored routes, not fetched.
 */
internal fun fetchRouteArea(
    client: RouteAreaClient,
    appKey: String,
    box: GeoBoundingBox,
    folder: File,
    regionId: Long,
    log: (String, Throwable?) -> Unit,
): RegionRouteRecord {
    fun missing(reason: RouteDataMissingReason, detail: String, error: Throwable? = null): RegionRouteRecord {
        log("Routes for region $regionId are missing (${reason.code}): $detail", error)
        return RegionRouteRecord.Missing(reason)
    }

    if (false) return missing(RouteDataMissingReason.NO_APP_KEY, "this build has no routing app key, so nothing was requested")

    return try {
        // One retry on 409 run_superseded, as the contract says ("ask for the area again"): the build changed between
        // the list and a file. A second 409 is recorded as such rather than looping.
        repeat(SUPERSEDED_RETRIES) {
            when (val attempt = fetchOnce(client, box, folder, regionId, log)) {
                is Attempt.Done -> return attempt.record
                is Attempt.Superseded -> {
                    log("Route data for region $regionId: ${attempt.detail}; asking for the area again.", null)
                    folder.listFiles()?.forEach { it.delete() }
                }
            }
        }
        when (val last = fetchOnce(client, box, folder, regionId, log)) {
            is Attempt.Done -> last.record
            is Attempt.Superseded -> missing(RouteDataMissingReason.RUN_SUPERSEDED, last.detail)
        }
    } catch (e: RouteStorageException) {
        missing(RouteDataMissingReason.STORAGE, e.message ?: "a write failed", e)
    } catch (e: IOException) {
        missing(RouteDataMissingReason.NETWORK, e.message ?: e.javaClass.simpleName, e)
    }
}

private const val SUPERSEDED_RETRIES = 1

private sealed interface Attempt {
    data class Done(val record: RegionRouteRecord) : Attempt

    data class Superseded(val detail: String) : Attempt
}

/** One pass: the list, then each file it names, and only those (a missing square's url answers 404). */
private fun fetchOnce(
    client: RouteAreaClient,
    box: GeoBoundingBox,
    folder: File,
    regionId: Long,
    log: (String, Throwable?) -> Unit,
): Attempt {
    fun missing(reason: RouteDataMissingReason, detail: String): Attempt {
        log("Routes for region $regionId are missing (${reason.code}): $detail", null)
        return Attempt.Done(RegionRouteRecord.Missing(reason))
    }
    val list = when (val answer = client.fetchList(box)) {
        RouteAnswer.OutsideCoverage -> {
            log("No routes for region $regionId: the Worker says the area is outside coverage.", null)
            return Attempt.Done(RegionRouteRecord.NoneHere)
        }
        is RouteAnswer.Missing -> return missing(answer.reason, answer.detail)
        is RouteAnswer.Ok -> answer.value
    }
    for (file in list.files) {
        when (val answer = client.fetchFile(file, File(folder, file.name))) {
            is RouteAnswer.Ok -> Unit
            // A file answered 404 outside_coverage after its list named it is not a coverage fact.
            RouteAnswer.OutsideCoverage -> return missing(RouteDataMissingReason.UNEXPECTED_RESPONSE, "${file.name} answered outside_coverage after the list named it")
            is RouteAnswer.Missing ->
                return if (answer.reason == RouteDataMissingReason.RUN_SUPERSEDED) Attempt.Superseded(answer.detail) else missing(answer.reason, answer.detail)
        }
    }
    if (list.missing.isNotEmpty()) {
        log("Routes for region $regionId are stored without ${list.missing.joinToString()}: build ${list.runId} does not cover them.", null)
    }
    return Attempt.Done(RegionRouteRecord.Stored(list.runId, list.osmTimestamp, list.files.map { RecordedFile(it.name, it.size, it.sha256) }, list.missing))
}
