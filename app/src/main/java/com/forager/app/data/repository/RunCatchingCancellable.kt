package com.forager.app.data.repository

import kotlinx.coroutines.CancellationException

/**
 * Like [runCatching], but never captures [CancellationException] into the returned [Result].
 *
 * Plain `runCatching` catches every [Throwable], `CancellationException` included, which breaks
 * structured concurrency: cancelling a coroutine (e.g. the species search debounce job, cancelled
 * by the next keystroke — see [SearchTaxaUseCase][com.forager.app.domain.SearchTaxaUseCase]'s call
 * site) works by throwing that exception through the suspended call, and it has to keep
 * propagating so the coroutine actually finishes cancelling. `runCatching` intercepting it instead
 * turns a routine cancellation into a `Result.failure` that gets shown to the user as a search
 * failure — its default message is literally "StandaloneCoroutine was cancelled". Every
 * `runCatching` wrapping a suspending call in `data/repository/` uses this instead, for the same
 * reason all of them, not just the one that happened to be visible in the UI.
 */
internal inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
