package com.zynergylabs.forager.app.location

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.altitude.AltitudeConverter
import android.os.Build
import androidx.annotation.RequiresApi
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.SeaLevelConverter
import com.zynergylabs.forager.app.domain.SeaLevelHeight
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The platform's sea-level converter, `android.location.altitude.AltitudeConverter` (API 34), behind
 * [SeaLevelConverter] (dispatch 2026-10-28-13, RECORD -1792). The only class that touches it. Its geoid model ships
 * inside the platform, so nothing is downloaded (docs/audits/2026-10-28-altitude-report.md, section 2).
 *
 * - **Below Android 14** the class does not exist: [SeaLevelHeight.Unsupported], logged once.
 * - **The model fails to load** (`IOException`) or the platform refuses the location (`IllegalArgumentException`):
 *   [SeaLevelHeight.Unsupported], logged once. Never the raw height passed off as sea level.
 * - The conversion may read the model from disk, so it runs on [io], never on the main thread.
 *
 * [sdkInt] is the phone's API level, a parameter so a test can be a phone below 14.
 */
class AndroidSeaLevelConverter(
    private val context: Context,
    private val errorLog: ErrorLog,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : SeaLevelConverter {

    private val logged = AtomicBoolean(false)
    private val converter: Any? by lazy { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) Api34.create() else null }

    override suspend fun seaLevel(lat: Double, lng: Double, rawMeters: Double?): SeaLevelHeight {
        if (rawMeters == null) return SeaLevelHeight.NoHeight
        val platform = converter
        if (sdkInt < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || platform == null) {
            logOnce("Sea-level height unsupported: Android API $sdkInt is below 34; heights read \"Alt unavailable\".", UnsupportedOperationException("AltitudeConverter needs API 34"))
            return SeaLevelHeight.Unsupported
        }
        return withContext(io) {
            try {
                val known = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) Api34.convert(platform, context, lat, lng, rawMeters) else null
                known ?: SeaLevelHeight.Unsupported.also { logOnce("Sea-level height unsupported: the converter gave no sea-level height.", IllegalStateException("hasMslAltitude() false after conversion")) }
            } catch (e: IOException) {
                logOnce("Sea-level height unsupported: the platform's geoid model did not load.", e)
                SeaLevelHeight.Unsupported
            } catch (e: IllegalArgumentException) {
                logOnce("Sea-level height unsupported: the platform refused the location.", e)
                SeaLevelHeight.Unsupported
            }
        }
    }

    private fun logOnce(message: String, error: Throwable) {
        if (logged.compareAndSet(false, true)) errorLog.w(TAG, message, error)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private object Api34 {
        fun create(): Any = AltitudeConverter()

        /** The sea-level height for [rawMeters] here, or `null` if the platform set none. */
        fun convert(converter: Any, context: Context, lat: Double, lng: Double, rawMeters: Double): SeaLevelHeight.Known? {
            val location = Location(LocationManager.GPS_PROVIDER).apply {
                latitude = lat
                longitude = lng
                altitude = rawMeters
            }
            (converter as AltitudeConverter).addMslAltitudeToLocation(context, location)
            if (!location.hasMslAltitude()) return null
            val seaLevel = location.mslAltitudeMeters
            return SeaLevelHeight.Known(meters = seaLevel, aboveRawMeters = seaLevel - rawMeters)
        }
    }

    internal companion object {
        /** `adb logcat -s ForagerSeaLevel`. */
        internal const val TAG = "ForagerSeaLevel"
    }
}
