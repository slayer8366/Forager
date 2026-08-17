package com.forager.app.domain

/**
 * Owned abstraction over "what time is it now", in epoch milliseconds.
 *
 * Two things read the clock in the offline-cache feature and both of them need it controllable:
 * [com.forager.app.data.repository.RoomSearchCacheRepository] stamps `fetchedAt` /
 * `lastAccessedAt` on every cached search (and those stamps decide which row gets evicted), and
 * the UI renders them back as relative times ("saved 3 hours ago"). Calling
 * [System.currentTimeMillis] inline at either site would leave the eviction-order test racing the
 * real clock and the Robolectric banner test asserting on text it cannot predict — so the clock is
 * injected, the same way device location is reached only through [LocationProvider].
 *
 * Deliberately one method returning a `Long` rather than wrapping `java.time.Clock` or
 * `java.time.Instant`: epoch millis is exactly what Room stores and what the relative-time
 * formatting subtracts, and a richer type would only be converted back at both ends.
 */
fun interface CurrentTimeProvider {
    fun nowEpochMillis(): Long
}

/**
 * The real clock. Pure JVM — [System.currentTimeMillis] is not an Android API — so this lives in
 * `domain/` next to its interface rather than needing a `location/`-style platform module, and
 * `domain/` stays Android-free.
 */
object SystemCurrentTimeProvider : CurrentTimeProvider {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
