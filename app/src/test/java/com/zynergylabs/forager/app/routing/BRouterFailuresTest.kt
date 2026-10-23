package com.zynergylabs.forager.app.routing

import com.zynergylabs.forager.app.domain.routing.RouteEnd
import com.zynergylabs.forager.app.domain.routing.TrailRouteFailure
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every BRouter v1.7.10 message [BRouterFailures] names, quoted from its source, including the ones the fixture
 * can't produce through the engine (a missing file between the folder check and the engine, a time limit). The
 * ones it can produce are also reached through the real engine in [BRouterTrailRouterTest].
 */
class BRouterFailuresTest {

    @Test
    fun `each message is named as its kind`() {
        val cases = mapOf(
            "from-position not mapped in existing datafile" to TrailRouteFailure.EndOutsideData(RouteEnd.START),
            "to-position not mapped in existing datafile" to TrailRouteFailure.EndOutsideData(RouteEnd.END),
            "target island detected for section 0" to TrailRouteFailure.NoPath("target island detected for section 0"),
            "start island detected for section 0" to TrailRouteFailure.NoPath("start island detected for section 0"),
            "no track found" to TrailRouteFailure.NoPath("no track found"),
            "no track found at pass=1" to TrailRouteFailure.NoPath("no track found at pass=1"),
            "datafile W75_N45.rd5 not found" to TrailRouteFailure.DataMissing("datafile W75_N45.rd5 not found"),
            "segment directory /x does not exist" to TrailRouteFailure.DataMissing("segment directory /x does not exist"),
            "file of size 100000 too short, should be 152664" to
                TrailRouteFailure.DataCorrupt("file of size 100000 too short, should be 152664"),
            "top index checksum error" to TrailRouteFailure.DataCorrupt("top index checksum error"),
            "lookup version mismatch (old rd5?) lookups.dat=10 W75_N40.rd5=9" to
                TrailRouteFailure.DataCorrupt("lookup version mismatch (old rd5?) lookups.dat=10 W75_N40.rd5=9"),
            "sub index checksum error" to TrailRouteFailure.DataCorrupt("sub index checksum error"),
            "checkum error" to TrailRouteFailure.DataCorrupt("checkum error"),
            "old, unsupported data-format" to TrailRouteFailure.DataCorrupt("old, unsupported data-format"),
            "error reading datafile W75_N40.rd5: java.lang.Exception" to
                TrailRouteFailure.DataCorrupt("error reading datafile W75_N40.rd5: java.lang.Exception"),
            "routing timeout after 30 seconds" to TrailRouteFailure.EngineFailed("routing timeout after 30 seconds"),
            "memory limit reached" to TrailRouteFailure.EngineFailed("memory limit reached"),
            "something new" to TrailRouteFailure.EngineFailed("something new"),
        )
        for ((message, expected) in cases) assertEquals(message, expected, BRouterFailures.classify(message))
    }
}
