package com.forager.app.domain

import com.forager.app.domain.model.TaxonSearchResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeTaxaRepository(
    private val result: Result<List<TaxonSearchResult>> = Result.success(emptyList()),
) : TaxonSearchRepository {
    var lastQuery: String? = null
    var callCount = 0

    override suspend fun searchTaxa(query: String): Result<List<TaxonSearchResult>> {
        lastQuery = query
        callCount++
        return result
    }
}

private fun result(id: Long, name: String) = TaxonSearchResult(
    taxonId = id,
    scientificName = name,
    commonName = null,
    rank = "species",
    iconicTaxonName = "Fungi",
    photoUrl = null,
)

class SearchTaxaUseCaseTest {

    @Test
    fun `queries shorter than 2 characters return empty without calling the repository`() = runTest {
        val repository = FakeTaxaRepository()
        val useCase = SearchTaxaUseCase(repository)

        val results = useCase("c").getOrThrow()

        assertTrue(results.isEmpty())
        assertEquals(0, repository.callCount)
    }

    @Test
    fun `blank queries return empty without calling the repository`() = runTest {
        val repository = FakeTaxaRepository()
        val useCase = SearchTaxaUseCase(repository)

        useCase("  ").getOrThrow()

        assertEquals(0, repository.callCount)
    }

    @Test
    fun `trims the query before delegating to the repository`() = runTest {
        val repository = FakeTaxaRepository(Result.success(listOf(result(1, "Cantharellus cibarius"))))
        val useCase = SearchTaxaUseCase(repository)

        val results = useCase("  chant  ").getOrThrow()

        assertEquals("chant", repository.lastQuery)
        assertEquals(1, results.size)
    }

    @Test
    fun `repository failure propagates as failure`() = runTest {
        val failure = IllegalStateException("network down")
        val repository = FakeTaxaRepository(Result.failure(failure))
        val useCase = SearchTaxaUseCase(repository)

        val outcome = useCase("chanterelle")

        assertTrue(outcome.isFailure)
        assertEquals(failure, outcome.exceptionOrNull())
    }
}
