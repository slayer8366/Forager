package com.zynergylabs.forager.app.service

import com.zynergylabs.forager.app.AppContainer
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.model.TrackPoint

/**
 * The calls [TrackRecordingService] makes to the container's watches for each fix, in one place so
 * a test can hand the real service a watch that throws (dispatch 2026-09-28-658, R1). The service
 * guards every one of these calls; this class adds nothing to what each watch does.
 *
 * Open only for that test. Production always uses this class as it is, built from the app's
 * container.
 */
internal open class RecordingWatches(private val container: AppContainer) {

    /** Every raw fix, to the return watch. See [com.zynergylabs.forager.app.domain.ReturnWatch.onFix]. */
    open fun returnOnFix(point: TrackPoint, provider: FixProvider) = container.returnWatch.onFix(point, provider)

    /** Every raw fix, to the sundown watch; `true` asks for an evaluation now. See [com.zynergylabs.forager.app.domain.SundownWatch.onFix]. */
    open fun sundownOnFix(point: TrackPoint, provider: FixProvider): Boolean = container.sundownWatch.onFix(point, provider)

    /** Each point the sampler keeps, to the return watch. See [com.zynergylabs.forager.app.domain.ReturnWatch.onKeptPoint]. */
    open fun returnOnKeptPoint(point: TrackPoint) = container.returnWatch.onKeptPoint(point)

    /**
     * Every raw fix, to the back-by watch (dispatch 2026-09-28-645; guarded as the others are,
     * RECORD -674 and -687); `true` asks for an evaluation now (dispatch 2026-09-28-796). See
     * [com.zynergylabs.forager.app.domain.BackByWatch.onFix].
     */
    open fun backByOnFix(point: TrackPoint, provider: FixProvider): Boolean = container.backByWatch.onFix(point, provider)
}
