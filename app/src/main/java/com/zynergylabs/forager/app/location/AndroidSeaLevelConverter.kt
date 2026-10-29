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
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
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
 * - **Any other exception** from the platform's call (Amendment 3, N6, RECORD -1797): [SeaLevelHeight.Unsupported],
 *   logged, never thrown into the fix collection. Cancellation is not caught.
 * - **Logged once per reason** (Amendment 3, N7), not once per process: a second, different cause is named too.
 * - The conversion may read the model from disk, so it runs on [io], never on the main thread.
 *
 * [sdkInt] is the phone's API level, a parameter so a test can be a phone below 14. [conversion] is the platform's call,
 * a parameter so a test can stand in for it (dispatch 2026-10-28-13, Amendment 3); `null`, the default, is the platform's.
 */
class AndroidSeaLevelConverter(
    private val context: Context,
    private val errorLog: ErrorLog,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val conversion: MslConversion? = null,
) : SeaLevelConverter {

    /** The reasons already logged: each is logged once. */
    private val loggedReasons: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val platform: MslConversion? by lazy {
        conversion ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) Api34.conversion(context) else null
    }

    override suspend fun seaLevel(lat: Double, lng: Double, rawMeters: Double?): SeaLevelHeight {
        if (rawMeters == null) return SeaLevelHeight.NoHeight
        val convert = platform
        if (sdkInt < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || convert == null) {
            logOnce("below-34", "Sea-level height unsupported: Android API $sdkInt is below 34; heights read \"Alt unavailable\".", UnsupportedOperationException("AltitudeConverter needs API 34"))
            return SeaLevelHeight.Unsupported
        }
        return withContext(io) {
            try {
                convert.convert(lat, lng, rawMeters) ?: SeaLevelHeight.Unsupported.also { logOnce("no-msl", "Sea-level height unsupported: the converter gave no sea-level height.", IllegalStateException("hasMslAltitude() false after conversion")) }
            } catch (e: IOException) {
                logOnce("model", "Sea-level height unsupported: the platform's geoid model did not load.", e)
                SeaLevelHeight.Unsupported
            } catch (e: IllegalArgumentException) {
                logOnce("refused", "Sea-level height unsupported: the platform refused the location.", e)
                SeaLevelHeight.Unsupported
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logOnce("other:${e.javaClass.name}", "Sea-level height unsupported: the platform's conversion failed (${e.javaClass.simpleName}).", e)
                SeaLevelHeight.Unsupported
            }
        }
    }

    private fun logOnce(reason: String, message: String, error: Throwable) {
        if (loggedReasons.add(reason)) errorLog.w(TAG, message, error)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private object Api34 {
        /** One platform converter, held for every call: it loads the geoid model once. */
        fun conversion(context: Context): MslConversion {
            val converter = AltitudeConverter()
            return MslConversion { lat, lng, rawMeters -> convert(converter, context, lat, lng, rawMeters) }
        }

        /** The sea-level height for [rawMeters] here, or `null` if the platform set none. */
        fun convert(converter: AltitudeConverter, context: Context, lat: Double, lng: Double, rawMeters: Double): SeaLevelHeight.Known? {
            val location = Location(LocationManager.GPS_PROVIDER).apply {
                latitude = lat
                longitude = lng
                altitude = rawMeters
            }
            converter.addMslAltitudeToLocation(context, location)
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

/** The platform's sea-level call for one height: the sea-level height, or `null` when the platform set none. May throw. */
fun interface MslConversion {
    fun convert(lat: Double, lng: Double, rawMeters: Double): SeaLevelHeight.Known?
}
