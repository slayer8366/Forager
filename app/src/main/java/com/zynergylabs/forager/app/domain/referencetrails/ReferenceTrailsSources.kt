package com.zynergylabs.forager.app.domain.referencetrails

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Where the manifest's text comes from: the Worker's `/reference-trails-manifest.json` in the app
 * (`WorkerReferenceTrailsManifestSource`), a fixture in tests. An interface this project owns, so nothing here
 * depends on the HTTP client (CLAUDE.md, Architecture). A failure is any answer that is not the file: no network, a
 * status other than 200, a body over the size limit.
 */
fun interface ReferenceTrailsManifestSource {
    suspend fun fetch(): Result<String>
}

/**
 * The last copy fetched, kept so the card works offline (-1717). A flat value nothing joins against, so DataStore
 * (CLAUDE.md, "Room for data that relates; DataStore for flat settings"). [read] answers null until a copy was ever
 * written.
 */
interface ReferenceTrailsDatesStore {
    suspend fun read(): Result<ReferenceTrailsSourceDates?>
    suspend fun write(dates: ReferenceTrailsSourceDates): Result<Unit>
}

/** What the card knows. [Loading] lasts only until the kept copy is read, at start. */
sealed interface ReferenceTrailsCopy {
    data object Loading : ReferenceTrailsCopy

    /** No copy has ever been fetched (or the kept one could not be read, which is logged). */
    data object None : ReferenceTrailsCopy

    data class Known(val dates: ReferenceTrailsSourceDates) : ReferenceTrailsCopy
}

/**
 * The card's dates, for the app's life (`AppContainer` holds one). The kept copy is read once at construction;
 * [refresh], which the card calls each time it opens, fetches the manifest and, when it parses, keeps it and shows it.
 *
 * A failed fetch or an unreadable file keeps the copy there was and is logged; good dates are never blanked
 * (the dispatch, "A failed fetch"). A refresh asked for while one runs is dropped, since the one running answers it.
 */
class ReferenceTrailsSources(
    private val source: ReferenceTrailsManifestSource,
    private val store: ReferenceTrailsDatesStore,
    private val scope: CoroutineScope,
    private val log: (message: String, error: Throwable?) -> Unit,
) {
    private val _copy = MutableStateFlow<ReferenceTrailsCopy>(ReferenceTrailsCopy.Loading)
    val copy: StateFlow<ReferenceTrailsCopy> = _copy.asStateFlow()

    private val refreshing = Mutex()

    init {
        scope.launch {
            val kept = store.read().getOrElse { error ->
                if (error is CancellationException) throw error
                log("The kept official-trails dates could not be read; the card shows none until a fetch.", error)
                null
            }
            // A fetch that finished first has already put a newer copy in place; the kept one does not replace it.
            _copy.compareAndSet(ReferenceTrailsCopy.Loading, if (kept == null) ReferenceTrailsCopy.None else ReferenceTrailsCopy.Known(kept))
        }
    }

    /** Fetches the manifest once, unless a fetch is already running. Returns at once; the result arrives on [copy]. */
    fun refresh() {
        scope.launch {
            if (!refreshing.tryLock()) return@launch
            try {
                fetchOnce()
            } finally {
                refreshing.unlock()
            }
        }
    }

    private suspend fun fetchOnce() {
        val text = source.fetch().getOrElse { error ->
            if (error is CancellationException) throw error
            log("The official-trails manifest could not be fetched; the card keeps the dates it had.", error)
            return
        }
        val dates = parseReferenceTrailsManifest(text) { warning -> log(warning, null) }.getOrElse { error ->
            log("The official-trails manifest could not be read; the card keeps the dates it had.", error)
            return
        }
        _copy.value = ReferenceTrailsCopy.Known(dates)
        store.write(dates).onFailure { error ->
            if (error is CancellationException) throw error
            log("The official-trails dates could not be kept; the card shows them until the app closes.", error)
        }
    }
}
