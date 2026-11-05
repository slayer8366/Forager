package com.zynergylabs.forager.app.data.dropoffmap

import com.zynergylabs.forager.app.domain.mapdata.DropOffMapPass
import com.zynergylabs.forager.app.domain.mapdata.OfflineBasemap
import com.zynergylabs.forager.app.domain.model.GeoBoundingBox
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient

/** STUB (dispatch 2026-11-04-09, tests first): one area's pass. Built in the next commit; it asks for nothing yet. */
internal class DropOffMapFetch(
    @Suppress("unused") private val http: OkHttpClient,
    @Suppress("unused") private val store: DropOffMapStore,
    @Suppress("unused") private val mapLibre: OfflineBasemap,
    @Suppress("unused") private val log: (String, Throwable?) -> Unit,
    @Suppress("unused") private val io: CoroutineDispatcher = Dispatchers.IO,
    @Suppress("unused") private val clock: () -> Long = { System.nanoTime() / 1_000_000L },
    @Suppress("unused") private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun pass(regionId: Long, area: GeoBoundingBox): DropOffMapPass = DropOffMapPass.NotRun("not built yet")
}
