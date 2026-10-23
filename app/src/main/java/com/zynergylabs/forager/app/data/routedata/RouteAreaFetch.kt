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
 * - Any file failing, by network, status, size or sha256, fails the whole area: routes are stored whole or not at all,
 *   since BRouter routing across a square with no file reads it as outside the data.
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

    if (appKey.isEmpty()) return missing(RouteDataMissingReason.NO_APP_KEY, "this build has no routing app key, so nothing was requested")

    return try {
        val list = when (val answer = client.fetchList(box)) {
            RouteAnswer.OutsideCoverage -> {
                log("No routes for region $regionId: the Worker says the area is outside coverage.", null)
                return RegionRouteRecord.NoneHere
            }
            is RouteAnswer.Missing -> return missing(answer.reason, answer.detail)
            is RouteAnswer.Ok -> answer.value
        }
        for (file in list.files) {
            when (val answer = client.fetchFile(file, File(folder, file.name))) {
                is RouteAnswer.Ok -> Unit
                // A file answered 404 outside_coverage after its list named it is not a coverage fact.
                RouteAnswer.OutsideCoverage -> return missing(RouteDataMissingReason.UNEXPECTED_RESPONSE, "${file.name} answered outside_coverage after the list named it")
                is RouteAnswer.Missing -> return missing(answer.reason, answer.detail)
            }
        }
        RegionRouteRecord.Stored(list.runId, list.osmTimestamp, list.files.map { RecordedFile(it.name, it.size, it.sha256) })
    } catch (e: RouteStorageException) {
        missing(RouteDataMissingReason.STORAGE, e.message ?: "a write failed", e)
    } catch (e: IOException) {
        missing(RouteDataMissingReason.NETWORK, e.message ?: e.javaClass.simpleName, e)
    }
}
