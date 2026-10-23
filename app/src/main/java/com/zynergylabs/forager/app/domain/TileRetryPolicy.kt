package com.zynergylabs.forager.app.domain

import java.util.concurrent.atomic.AtomicInteger

/**
 * What to do with a map tile request that failed (dispatch 2026-10-23-06 Amendment 1; the owner's "A: retry failed map
 * pieces", RECORD -1577). Pure Kotlin, so the rules are tested without OkHttp or Android; the interceptor that applies
 * them is `TileRetryInterceptor`.
 *
 * **Why the app holds and resends a tile at all.** MapLibre Native 13.5.0 never asks for a failed tile again while the
 * camera keeps it in view: the tile's loader drops its request on the first response, error or not
 * (`tile_loader_impl.hpp:210`, tag android-v13.5.0), and with it the retry the network layer had scheduled. The map
 * then draws the tile from a lower zoom for as long as the user stays put, which for Trail Map is forest green with no
 * tree marks (dispatch 2026-10-22-07). Holding the request open in the app's own HTTP client keeps the tile "loading"
 * instead of "failed", so it fills in by itself when the request finally succeeds.
 *
 * **Rejected:** telling MapLibre the network is back (its reachability signal only restarts requests still alive, and
 * a failed tile has none); a camera nudge or a source reload (visible jumps or a whole-map flash). The verify-first
 * report for dispatch 2026-10-23-06 has the source lines.
 */
class TileRetryPolicy(
    val holdLimitMs: Long = TILE_RETRY_HOLD_LIMIT_MS,
    private val backoffMs: List<Long> = TILE_RETRY_BACKOFF_MS,
) {
    init {
        require(holdLimitMs > 0) { "holdLimitMs must be positive" }
        require(backoffMs.isNotEmpty() && backoffMs.all { it > 0 }) { "backoffMs must be non-empty and positive" }
    }

    /**
     * The decision after the [failedAttempts]th failure (1 for the first) of one request, [heldForMs] after it was
     * first held, with the network [networkValidated] now.
     *
     * - Held for [holdLimitMs] or longer: [TileRetryDecision.GiveUp], and the last failure goes to the map as it would
     *   have without this policy.
     * - No validated network: [TileRetryDecision.WaitForNetwork], whatever the failure. A server error cannot be
     *   retried usefully without a network either.
     * - A validated network: [TileRetryDecision.RetryAfter] the backoff for this attempt, never past the hold limit.
     *   This covers a server error, and a connection error or timeout that happened with the network up (a DNS
     *   lookup in the moments after a network validates fails like this, seen on the S22 in dispatch 2026-10-22-07).
     */
    fun decide(failure: TileFailure, failedAttempts: Int, heldForMs: Long, networkValidated: Boolean): TileRetryDecision {
        require(failedAttempts >= 1) { "failedAttempts starts at 1" }
        if (heldForMs >= holdLimitMs) return TileRetryDecision.GiveUp
        if (!networkValidated) return TileRetryDecision.GiveUp
        val backoff = backoffMs[minOf(failedAttempts, backoffMs.size) - 1]
        return TileRetryDecision.RetryAfter(minOf(backoff, holdLimitMs - heldForMs))
    }
}

/** How a tile request failed, as far as the retry rules care. A 404 or any other answer is not a failure here. */
enum class TileFailure { Connection, Timeout, Server }

sealed interface TileRetryDecision {
    /** Send it again after [delayMs], unless the request is cancelled first. */
    data class RetryAfter(val delayMs: Long) : TileRetryDecision

    /** Send it again as soon as the network is validated, unless cancelled or the hold limit comes first. */
    data object WaitForNetwork : TileRetryDecision

    /** Stop holding; the map gets the failure. */
    data object GiveUp : TileRetryDecision
}

/**
 * At most [limit] tile requests held at once. Held requests each occupy one of the map client's per-host request slots
 * (20), so the rest of the map keeps its own; a request beyond the limit fails as it would have without holding.
 * Thread-safe: OkHttp runs each call on its own thread.
 */
class TileHoldGate(val limit: Int = TILE_RETRY_MAX_HELD) {
    private val held = AtomicInteger(0)

    init {
        require(limit > 0) { "limit must be positive" }
    }

    /** Takes a place if one is free; `false` when [limit] are already held. */
    fun tryHold(): Boolean {
        while (true) {
            val now = held.get()
            if (now >= limit) return false
            if (held.compareAndSet(now, now + 1)) return true
        }
    }

    /** Gives back a place taken by [tryHold]. */
    fun release() {
        held.updateAndGet { if (it > 0) it - 1 else error("release() without a hold") }
    }

    /** How many are held now. */
    val heldNow: Int get() = held.get()
}

/**
 * The longest one tile request is held, from its first failure: 5 minutes. An operating limit, the planner's call
 * (dispatch 2026-10-23-06 Amendment 1), not a measured figure. It is also how long an offline-area download waits for
 * signal before its usual error: the owner, "Up to 5 minutes without signal, then the usual error. Nothing to tap if
 * signal comes back." (same amendment).
 */
const val TILE_RETRY_HOLD_LIMIT_MS: Long = 5 * 60_000L

/** The most tile requests held at once: 8. An operating limit, the planner's call (dispatch 2026-10-23-06 Amendment 1). */
const val TILE_RETRY_MAX_HELD: Int = 8

/**
 * The waits between resends while the network is validated: 1 s, 2 s, 4 s, 8 s, 15 s, then every 30 s until the hold
 * limit. Proposed by the coder and named here (dispatch 2026-10-23-06 Amendment 1: "a short backoff, which you propose
 * and name"); not measured. Short at first because the common case is a network that has only just come back; capped
 * so a server that stays down costs one request per tile every 30 s, at most 8 tiles.
 */
val TILE_RETRY_BACKOFF_MS: List<Long> = listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 30_000L)
