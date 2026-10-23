package com.zynergylabs.forager.app.data.routedata

import com.zynergylabs.forager.app.domain.routedata.RouteDataMissingReason
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What a non-200 answer from the Worker's routing routes means, by the contract PR #381 and the planner set
 * (dispatch 2026-10-23-03): a status plus `{"error","message"}`. The error code decides, and only together with the
 * status it was agreed with: a 404 is "no routes here" only when its code is `outside_coverage`, because a 404 with
 * any other body (a Worker without the route deployed, a proxy page) says nothing about coverage.
 */
internal sealed interface RouteErrorMeaning {
    /** 404 `outside_coverage`. */
    data object OutsideCoverage : RouteErrorMeaning

    data class Missing(val reason: RouteDataMissingReason, val detail: String) : RouteErrorMeaning
}

/** [body] is the response text, or null if it could not be read; [retryAfter] is the raw `Retry-After` header. */
internal fun classifyRouteError(status: Int, body: String?, retryAfter: String?): RouteErrorMeaning {
    val error = body?.let(::parseRouteError)
    val code = error?.error
    val said = "HTTP $status, error ${code ?: "(none)"}, message ${error?.message ?: "(none)"}"
    val agreed = code?.let { AGREED_ERRORS[it] }
    if (agreed == null || agreed.status != status) {
        return RouteErrorMeaning.Missing(RouteDataMissingReason.UNEXPECTED_RESPONSE, "an answer outside the agreed list: $said")
    }
    val reason = agreed.reason ?: return RouteErrorMeaning.OutsideCoverage
    val detail = if (reason == RouteDataMissingReason.RATE_LIMITED) "$said, Retry-After ${retryAfter ?: "(none)"}" else said
    return RouteErrorMeaning.Missing(reason, detail)
}

/** One agreed error: the status it comes with, and its reason; a null [reason] is `outside_coverage`. */
private class AgreedError(val status: Int, val reason: RouteDataMissingReason?)

/** Every error the contract names. */
private val AGREED_ERRORS: Map<String, AgreedError> = mapOf(
    "manifest_missing" to AgreedError(503, RouteDataMissingReason.MANIFEST_MISSING),
    "run_missing_square" to AgreedError(503, RouteDataMissingReason.RUN_MISSING_SQUARE),
    "checksum" to AgreedError(502, RouteDataMissingReason.SERVER_CHECKSUM),
    "outside_coverage" to AgreedError(404, null),
    "too_large" to AgreedError(413, RouteDataMissingReason.TOO_LARGE),
    "bad_request" to AgreedError(400, RouteDataMissingReason.BAD_REQUEST),
    "forbidden" to AgreedError(403, RouteDataMissingReason.FORBIDDEN),
    "rate_limited" to AgreedError(429, RouteDataMissingReason.RATE_LIMITED),
)

@Serializable
internal data class RouteErrorBody(val error: String? = null, val message: String? = null)

private val errorJson = Json { ignoreUnknownKeys = true }

/** Null for a body that is not the agreed JSON: the caller then has a status with no code, an unexpected answer. */
private fun parseRouteError(body: String): RouteErrorBody? = try {
    errorJson.decodeFromString(RouteErrorBody.serializer(), body)
} catch (e: IllegalArgumentException) {
    null
}
