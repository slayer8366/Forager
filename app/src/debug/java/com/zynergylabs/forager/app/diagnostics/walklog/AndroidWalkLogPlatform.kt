package com.zynergylabs.forager.app.diagnostics.walklog

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssCapabilities
import android.location.GnssMeasurement
import android.location.GnssMeasurementsEvent
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * The phone, as the walk logger reads it (dispatch 2026-09-28-532). Every callback arrives on
 * [handler], the logger's own thread, so the session sees one event at a time.
 *
 * **What it asks of the phone, and what it does not.** Fixes come from one listener on the passive
 * provider: it sees every fix any listener on the phone receives, GPS and network, each with its own
 * provider, and asks the GPS chip for nothing the recording does not already ask for. Raw GNSS
 * measurements are asked for with the plain callback, not a full-tracking request (RECORD -559,
 * choice 2: record what the phone offers; forcing full tracking would change the fixes the walker
 * gets). Motion sensors at 25 Hz and rotation vectors at 10 Hz (choice 4); pressure at the
 * platform's normal rate and the step sensors as they report, which is what "as delivered" means
 * here. Nothing here acts on position.
 *
 * Every registration that the platform refuses, or that throws, returns false, and the session
 * writes it as unsupported. Logged, never silent.
 */
class AndroidWalkLogPlatform(
    private val context: Context,
    private val handler: Handler,
    private val freeBytesProvider: () -> Long,
) : WalkLogPlatform {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager?

    private var locationListener: LocationListener? = null
    private var statusCallback: GnssStatus.Callback? = null
    private var measurementsCallback: GnssMeasurementsEvent.Callback? = null
    private var sensorListener: SensorEventListener? = null
    private var minuteTick: Runnable? = null

    override fun elapsedRealtimeNanos(): Long = SystemClock.elapsedRealtimeNanos()

    override fun wallClockMillis(): Long = System.currentTimeMillis()

    override fun headerFacts(): HeaderFacts {
        val (dataNetworkType, dataNetworkTypeReason) = dataNetworkType()
        return HeaderFacts(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            device = Build.DEVICE,
            buildId = Build.ID,
            fingerprint = Build.FINGERPRINT,
            sdkInt = Build.VERSION.SDK_INT,
            appVersionName = appVersion().first,
            appVersionCode = appVersion().second,
            simState = simState(),
            dataNetworkType = dataNetworkType,
            dataNetworkTypeUnsupportedReason = dataNetworkTypeReason,
            activeTransports = activeTransports(),
            gnssHardwareModelName = if (Build.VERSION.SDK_INT >= 28) locationManager.gnssHardwareModelName else null,
            gnssYearOfHardware = if (Build.VERSION.SDK_INT >= 28) locationManager.gnssYearOfHardware else null,
            sensors = sensorManager?.getSensorList(Sensor.TYPE_ALL).orEmpty().map { it.toInfo() },
        )
    }

    override fun gnssCapabilities(): GnssCapabilitiesRecord? {
        if (Build.VERSION.SDK_INT < 30) return null
        return try {
            readGnssCapabilities()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Walk log: the GNSS capabilities could not be read.", e)
            GnssCapabilitiesRecord(linkedMapOf("unreadable" to e.javaClass.simpleName))
        }
    }

    @androidx.annotation.RequiresApi(30)
    private fun readGnssCapabilities(): GnssCapabilitiesRecord {
        val caps = locationManager.gnssCapabilities
        val flags = linkedMapOf<String, Any>()
        fun put(name: String, minSdk: Int, read: () -> Any) {
            flags[name] = if (Build.VERSION.SDK_INT >= minSdk) read() else "unsupported-below-api-$minSdk"
        }
        put("hasMeasurements", 31) { caps.hasMeasurements() }
        put("hasNavigationMessages", 31) { caps.hasNavigationMessages() }
        put("hasAntennaInfo", 31) { caps.hasAntennaInfo() }
        put("hasAccumulatedDeltaRange", 34) { capabilityName(caps.hasAccumulatedDeltaRange()) }
        put("hasMeasurementCorrections", 34) { caps.hasMeasurementCorrections() }
        put("hasMeasurementCorrelationVectors", 34) { caps.hasMeasurementCorrelationVectors() }
        put("hasSatellitePvt", 34) { caps.hasSatellitePvt() }
        put("hasLowPowerMode", 34) { caps.hasLowPowerMode() }
        put("hasSatelliteBlocklist", 34) { caps.hasSatelliteBlocklist() }
        put("hasPowerMultibandTracking", 34) { caps.hasPowerMultibandTracking() }
        put("hasPowerMultibandAcquisition", 34) { caps.hasPowerMultibandAcquisition() }
        put("hasMsa", 34) { caps.hasMsa() }
        put("hasMsb", 34) { caps.hasMsb() }
        // The signal types the chip says it tracks: an L5 or E5a entry (about 1176.45 MHz) is the
        // phone saying it offers the second band, which the satellite lines' cf= then show in use.
        put("signalTypes", 34) {
            caps.gnssSignalTypes.joinToString(",", prefix = "[", postfix = "]") { signal ->
                "${constellationName(signal.constellationType)}:${signal.carrierFrequencyHz.toLong()}:${signal.codeType}"
            }
        }
        return GnssCapabilitiesRecord(flags)
    }

    override fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    override fun freeBytes(): Long = freeBytesProvider()

    override fun battery(): BatteryRecord? {
        val intent: Intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
            0 -> "none"
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            -1 -> "none"
            else -> "other"
        }
        return BatteryRecord(
            levelPercent = if (level >= 0 && scale > 0) level * 100 / scale else null,
            charging = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> true
                -1, BatteryManager.BATTERY_STATUS_UNKNOWN -> null
                else -> false
            },
            plugged = plugged,
        )
    }

    @SuppressLint("MissingPermission")
    override fun registerFixes(sink: WalkLogEvents): Boolean = guarded("fixes") {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) = sink.onFix(location.toRecord(LocationManager.PASSIVE_PROVIDER))

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 0L, 0f, listener, handler.looper)
        locationListener = listener
        true
    }

    @SuppressLint("MissingPermission")
    override fun registerGnssStatus(sink: WalkLogEvents): Boolean = guarded("gnss-status") {
        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                sink.onGnssStatus(
                    (0 until status.satelliteCount).map { i ->
                        SatelliteRecord(
                            constellation = constellationName(status.getConstellationType(i)),
                            svid = status.getSvid(i),
                            cn0DbHz = status.getCn0DbHz(i),
                            elevationDegrees = status.getElevationDegrees(i),
                            azimuthDegrees = status.getAzimuthDegrees(i),
                            usedInFix = status.usedInFix(i),
                            carrierFrequencyHz = if (status.hasCarrierFrequencyHz(i)) status.getCarrierFrequencyHz(i) else null,
                        )
                    },
                )
            }
        }
        val accepted = locationManager.registerGnssStatusCallback(callback, handler)
        if (accepted) statusCallback = callback
        accepted
    }

    @SuppressLint("MissingPermission")
    override fun registerGnssMeasurements(sink: WalkLogEvents): Boolean = guarded("gnss-measurements") {
        val callback = object : GnssMeasurementsEvent.Callback() {
            override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
                val clock = event.clock
                sink.onGnssMeasurements(
                    GnssClockRecord(
                        timeNanos = clock.timeNanos,
                        fullBiasNanos = if (clock.hasFullBiasNanos()) clock.fullBiasNanos else null,
                        biasNanos = if (clock.hasBiasNanos()) clock.biasNanos else null,
                        biasUncertaintyNanos = if (clock.hasBiasUncertaintyNanos()) clock.biasUncertaintyNanos else null,
                        driftNanosPerSecond = if (clock.hasDriftNanosPerSecond()) clock.driftNanosPerSecond else null,
                        hardwareClockDiscontinuityCount = clock.hardwareClockDiscontinuityCount,
                        elapsedRealtimeNanos = if (Build.VERSION.SDK_INT >= 29 && clock.hasElapsedRealtimeNanos()) clock.elapsedRealtimeNanos else null,
                    ),
                    event.measurements.map { it.toRecord() },
                )
            }

            override fun onStatusChanged(status: Int) = sink.onGnssMeasurementsStatus(status)
        }
        val accepted = locationManager.registerGnssMeasurementsCallback(callback, handler)
        if (accepted) measurementsCallback = callback
        accepted
    }

    override fun registerSensor(kind: WalkLogSensorKind, sink: WalkLogEvents): Boolean = guarded("sensor:${kind.label}") {
        val manager = sensorManager ?: return@guarded false
        val sensor = manager.getDefaultSensor(kind.androidType) ?: return@guarded false
        val listener = sensorListener ?: object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val eventKind = kindOf(event.sensor) ?: return
                sink.onSensor(SensorRecord(eventKind, event.timestamp, event.values.copyOf(), event.accuracy))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                val eventKind = kindOf(sensor) ?: return
                sink.onSensorAccuracy(eventKind, accuracy)
            }
        }.also { sensorListener = it }
        val periodMicros = kind.rateHz?.let { 1_000_000 / it } ?: SensorManager.SENSOR_DELAY_NORMAL
        manager.registerListener(listener, sensor, periodMicros, handler)
    }

    override fun everyMinute(tick: () -> Unit) {
        minuteTick?.let(handler::removeCallbacks)
        val runnable = object : Runnable {
            override fun run() {
                tick()
                if (minuteTick === this) handler.postDelayed(this, MINUTE_MILLIS)
            }
        }
        minuteTick = runnable
        handler.postDelayed(runnable, MINUTE_MILLIS)
    }

    override fun unregisterAll() {
        minuteTick?.let(handler::removeCallbacks)
        minuteTick = null
        locationListener?.let { listener -> attempt("remove fixes") { locationManager.removeUpdates(listener) } }
        locationListener = null
        statusCallback?.let { callback -> attempt("remove gnss-status") { locationManager.unregisterGnssStatusCallback(callback) } }
        statusCallback = null
        measurementsCallback?.let { callback -> attempt("remove gnss-measurements") { locationManager.unregisterGnssMeasurementsCallback(callback) } }
        measurementsCallback = null
        sensorListener?.let { listener -> attempt("remove sensors") { sensorManager?.unregisterListener(listener) } }
        sensorListener = null
    }

    private fun kindOf(sensor: Sensor): WalkLogSensorKind? = WalkLogSensorKind.entries.firstOrNull { it.androidType == sensor.type }

    private fun guarded(what: String, block: () -> Boolean): Boolean = try {
        block().also { accepted -> if (!accepted) Log.w(TAG, "The phone refused the walk log's $what; written as unsupported.") }
    } catch (e: SecurityException) {
        Log.w(TAG, "No permission for the walk log's $what; written as unsupported.", e)
        false
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "The walk log's $what could not be registered; written as unsupported.", e)
        false
    }

    private fun attempt(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Walk log: couldn't $what.", e)
        }
    }

    private fun appVersion(): Pair<String, Long> = try {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        (info.versionName ?: "none") to code
    } catch (e: PackageManager.NameNotFoundException) {
        Log.w(TAG, "Walk log: the app's own version could not be read.", e)
        "unreadable" to -1L
    }

    private fun simState(): String {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager? ?: return "unsupported:no-telephony"
        return when (val state = telephony.simState) {
            TelephonyManager.SIM_STATE_ABSENT -> "ABSENT"
            TelephonyManager.SIM_STATE_READY -> "READY"
            TelephonyManager.SIM_STATE_NOT_READY -> "NOT_READY"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN_REQUIRED"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK_REQUIRED"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "NETWORK_LOCKED"
            TelephonyManager.SIM_STATE_PERM_DISABLED -> "PERM_DISABLED"
            TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "CARD_IO_ERROR"
            TelephonyManager.SIM_STATE_CARD_RESTRICTED -> "CARD_RESTRICTED"
            TelephonyManager.SIM_STATE_UNKNOWN -> "UNKNOWN"
            else -> "STATE($state)"
        }
    }

    /** The data network type needs READ_BASIC_PHONE_STATE (debug manifest, granted over USB); without it, said so. */
    @SuppressLint("MissingPermission")
    private fun dataNetworkType(): Pair<String?, String?> {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager? ?: return null to "no-telephony"
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_BASIC_PHONE_STATE else Manifest.permission.READ_PHONE_STATE
        if (!hasPermission(permission)) return null to "permission-not-granted"
        return try {
            networkTypeName(telephony.dataNetworkType) to null
        } catch (e: SecurityException) {
            Log.w(TAG, "Walk log: the data network type was refused.", e)
            null to "refused"
        }
    }

    private fun activeTransports(): List<String>? = try {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
        if (connectivity == null) {
            null
        } else {
            val capabilities = connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities)
            if (capabilities == null) {
                emptyList()
            } else {
                listOfNotNull(
                    "wifi".takeIf { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) },
                    "cellular".takeIf { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) },
                    "ethernet".takeIf { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) },
                    "bluetooth".takeIf { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) },
                    "vpn".takeIf { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) },
                )
            }
        }
    } catch (e: SecurityException) {
        Log.w(TAG, "Walk log: the active network could not be read.", e)
        null
    }

    private companion object {
        const val TAG = "WalkLog"
        const val MINUTE_MILLIS = 60_000L

        fun Location.toRecord(listener: String) = FixRecord(
            listener = listener,
            provider = provider,
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = if (hasAccuracy()) accuracy else null,
            verticalAccuracyMeters = if (hasVerticalAccuracy()) verticalAccuracyMeters else null,
            speedMetersPerSecond = if (hasSpeed()) speed else null,
            speedAccuracyMetersPerSecond = if (hasSpeedAccuracy()) speedAccuracyMetersPerSecond else null,
            bearingDegrees = if (hasBearing()) bearing else null,
            bearingAccuracyDegrees = if (hasBearingAccuracy()) bearingAccuracyDegrees else null,
            altitudeMeters = if (hasAltitude()) altitude else null,
            timeEpochMillis = time,
            elapsedRealtimeNanos = elapsedRealtimeNanos.takeIf { it != 0L },
        )

        fun GnssMeasurement.toRecord() = GnssMeasurementRecord(
            constellation = constellationName(constellationType),
            svid = svid,
            cn0DbHz = cn0DbHz,
            state = state,
            receivedSvTimeNanos = receivedSvTimeNanos,
            receivedSvTimeUncertaintyNanos = receivedSvTimeUncertaintyNanos,
            timeOffsetNanos = timeOffsetNanos,
            pseudorangeRateMetersPerSecond = pseudorangeRateMetersPerSecond,
            pseudorangeRateUncertaintyMetersPerSecond = pseudorangeRateUncertaintyMetersPerSecond,
            accumulatedDeltaRangeState = accumulatedDeltaRangeState,
            accumulatedDeltaRangeMeters = accumulatedDeltaRangeMeters,
            accumulatedDeltaRangeUncertaintyMeters = accumulatedDeltaRangeUncertaintyMeters,
            carrierFrequencyHz = if (hasCarrierFrequencyHz()) carrierFrequencyHz else null,
            multipathIndicator = multipathIndicator,
            codeType = if (Build.VERSION.SDK_INT >= 29 && hasCodeType()) codeType else null,
        )

        fun Sensor.toInfo() = SensorInfo(
            type = type,
            stringType = stringType,
            name = name,
            vendor = vendor,
            version = version,
            maximumRange = maximumRange,
            resolution = resolution,
            powerMilliamps = power,
            minDelayMicros = minDelay,
            maxDelayMicros = maxDelay,
            isWakeUp = isWakeUpSensor,
        )

        fun constellationName(type: Int): String = when (type) {
            GnssStatus.CONSTELLATION_GPS -> "GPS"
            GnssStatus.CONSTELLATION_SBAS -> "SBAS"
            GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
            GnssStatus.CONSTELLATION_QZSS -> "QZSS"
            GnssStatus.CONSTELLATION_BEIDOU -> "BEIDOU"
            GnssStatus.CONSTELLATION_GALILEO -> "GALILEO"
            GnssStatus.CONSTELLATION_IRNSS -> "IRNSS"
            GnssStatus.CONSTELLATION_UNKNOWN -> "UNKNOWN"
            else -> "TYPE$type"
        }

        fun capabilityName(value: Int): String = when (value) {
            GnssCapabilities.CAPABILITY_SUPPORTED -> "supported"
            GnssCapabilities.CAPABILITY_UNSUPPORTED -> "unsupported"
            GnssCapabilities.CAPABILITY_UNKNOWN -> "unknown"
            else -> "value$value"
        }

        fun networkTypeName(type: Int): String = when (type) {
            TelephonyManager.NETWORK_TYPE_UNKNOWN -> "UNKNOWN"
            TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
            TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
            TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
            TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
            TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
            TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPAP"
            TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
            TelephonyManager.NETWORK_TYPE_NR -> "NR"
            TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN"
            else -> "TYPE$type"
        }
    }
}
