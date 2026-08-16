package com.forager.app.domain

import com.forager.app.domain.model.LatLng

/**
 * The outcome of a [Dbscan] run over a list of points.
 *
 * Both fields hold *indices into the input list*, not points, so the caller can map results
 * back onto whatever it was clustering (here: [com.forager.app.domain.model.Sighting]s) without
 * DBSCAN needing to know about that type.
 *
 * [noise] is a first-class part of the result, not a discarded remainder: a lone observation
 * far from everything else is a real answer ("not a repeat-producing spot"), and callers are
 * expected to report it rather than quietly fold it into the nearest cluster.
 */
data class DbscanResult(
    val clusters: List<List<Int>>,
    val noise: List<Int>,
)

/**
 * DBSCAN (density-based spatial clustering of applications with noise) over geographic points.
 *
 * Chosen over k-means because it takes a distance radius rather than a preset cluster count —
 * nobody knows in advance how many foraging spots a region contains — and because it labels
 * isolated points as noise instead of forcing every point into some cluster. A single
 * observation 8 km from anything else is not a foraging spot, and k-means would have promoted
 * it into one.
 *
 * Neighbour tests go through [GeoDistance], i.e. true metres, never degree-space arithmetic;
 * see that class for why.
 *
 * Implementation notes:
 * - Neighbour lookup is a linear scan, making this O(n²) in the number of points. That is fine
 *   at the scale this runs at (the observations already on screen for one map view — hundreds,
 *   not millions) and avoids pulling in a spatial index for no measured need.
 * - Border points — points inside a cluster's radius but without enough neighbours of their own
 *   to seed one — are assigned to whichever qualifying cluster reaches them first, which makes
 *   the result depend on input order in that one case. This is inherent to textbook DBSCAN and
 *   is left as-is rather than papered over.
 */
object Dbscan {

    private const val UNCLASSIFIED = -1
    private const val NOISE = -2

    /**
     * Clusters [points] such that every cluster member is within [epsilonMeters] of a core
     * point, and a core point is one with at least [minPoints] points in its neighbourhood.
     *
     * [minPoints] counts the point itself, per the standard formulation: `minPoints = 4` means
     * a point plus three neighbours within [epsilonMeters].
     */
    fun cluster(points: List<LatLng>, epsilonMeters: Double, minPoints: Int): DbscanResult {
        require(epsilonMeters > 0) { "epsilonMeters must be positive, was $epsilonMeters" }
        require(minPoints >= 1) { "minPoints must be at least 1, was $minPoints" }

        val labels = IntArray(points.size) { UNCLASSIFIED }
        var nextClusterId = 0

        for (seed in points.indices) {
            if (labels[seed] != UNCLASSIFIED) continue

            val seedNeighbours = neighboursOf(points, seed, epsilonMeters)
            if (seedNeighbours.size < minPoints) {
                // Tentative: a later cluster may still reclaim this as a border point.
                labels[seed] = NOISE
                continue
            }

            val clusterId = nextClusterId++
            labels[seed] = clusterId

            val frontier = ArrayDeque(seedNeighbours.filter { it != seed })
            while (frontier.isNotEmpty()) {
                val candidate = frontier.removeFirst()
                if (labels[candidate] == NOISE) {
                    // Border point: joins this cluster but is not dense enough to extend it.
                    labels[candidate] = clusterId
                    continue
                }
                if (labels[candidate] != UNCLASSIFIED) continue

                labels[candidate] = clusterId
                val candidateNeighbours = neighboursOf(points, candidate, epsilonMeters)
                if (candidateNeighbours.size >= minPoints) {
                    frontier.addAll(candidateNeighbours.filter { labels[it] == UNCLASSIFIED })
                }
            }
        }

        val clusters = List(nextClusterId) { mutableListOf<Int>() }
        val noise = mutableListOf<Int>()
        labels.forEachIndexed { index, label ->
            when {
                label >= 0 -> clusters[label].add(index)
                label == NOISE -> noise.add(index)
                // The outer loop visits every index, so nothing can still be unclassified here.
                // Failing loudly beats silently bucketing an unexpected label as noise.
                else -> error("DBSCAN left point $index unclassified (label $label)")
            }
        }
        return DbscanResult(clusters = clusters.map { it.toList() }, noise = noise.toList())
    }

    /** Indices of every point within [epsilonMeters] of `points[index]`, including itself. */
    private fun neighboursOf(points: List<LatLng>, index: Int, epsilonMeters: Double): List<Int> {
        val origin = points[index]
        return points.indices.filter { other ->
            GeoDistance.metersBetween(origin, points[other]) <= epsilonMeters
        }
    }
}
