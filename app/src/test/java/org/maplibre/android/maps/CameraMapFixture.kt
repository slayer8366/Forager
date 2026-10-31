package org.maplibre.android.maps

import android.graphics.PointF
import java.lang.reflect.Proxy
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponent
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.modes.CameraMode

/**
 * A real [MapLibreMap] with a recording native map, for a test that runs the app's own camera code (`MapDataRefresh`) on it
 * (dispatch 2026-10-30-08, Amendment 5, S-1). A real `MapView` cannot be built under Robolectric, so the map is made the way
 * its own package-private constructor makes it, over a [Transform] that never touches its view (read from the 13.5.0 classes
 * with javap: the constructors only store their arguments).
 *
 * The native map is a proxy that holds one camera: `jumpTo` (what `MapLibreMap.setCameraPosition` reaches through
 * `Transform.moveCamera`) sets it and counts the move, `getCameraPosition` returns it, and `getCameraForLatLngBounds` returns
 * [Recording.fit], the camera a fit is taken to land on. Everything else does nothing and returns null, false or zero, as
 * [StyleLifecycleFixture]'s does.
 *
 * The location component is the SDK's own class with its activation and camera mode set by reflection, so that the app's
 * reads (`isLocationComponentActivated`, `cameraMode`) and its write (`cameraMode = CameraMode.NONE`) go through the SDK's
 * own `LocationComponent.setCameraMode` and `LocationCameraController.setCameraMode`. What it cannot show: the puck, or the
 * camera the puck would then drive. [Recording.trackingChanges] counts the SDK reporting a mode change, which is the one
 * thing an app write of the mode produces here.
 */
object CameraMapFixture {
    class Recording(val fit: CameraPosition) {
        var camera: CameraPosition? = null
        var jumps = 0
        var trackingChanges = 0
        lateinit var cameraController: Any

        /** The location component's camera mode, read from the SDK's own controller. */
        val cameraMode: Int get() = field(cameraController, "cameraMode").getInt(cameraController)

        /** Puts the component in [mode] the way a locate tap's own transition leaves it, without a transition to run. */
        fun setModeDirectly(mode: Int) = field(cameraController, "cameraMode").setInt(cameraController, mode)
    }

    // The JVM's own allocator, reached by reflection: the Android compile classpath does not carry sun.misc, the test JVM does.
    private val unsafeType: Class<*> = Class.forName("sun.misc.Unsafe")
    private val unsafe: Any = unsafeType.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)!!
    private val allocateInstance = unsafeType.getMethod("allocateInstance", Class::class.java)

    /** An object of [type] with no constructor run: for the parts of the SDK this map holds but the camera code never calls. */
    @Suppress("UNCHECKED_CAST")
    fun <T> inert(type: Class<T>): T = allocateInstance.invoke(unsafe, type) as T

    private fun field(owner: Any, name: String): java.lang.reflect.Field {
        var type: Class<*>? = owner.javaClass
        while (type != null) {
            runCatching { return type.getDeclaredField(name).apply { isAccessible = true } }
            type = type.superclass
        }
        error("no field $name on ${owner.javaClass}")
    }

    private fun set(owner: Any, name: String, value: Any?) = field(owner, name).set(owner, value)

    private fun defaultFor(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Double.TYPE -> 0.0
        java.lang.Float.TYPE -> 0f
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Character.TYPE -> 0.toChar()
        else -> null
    }

    /** A map whose camera starts at [start], with following on when [cameraMode] is a tracking mode; [fit] is where a fit lands. */
    fun map(start: CameraPosition, fit: CameraPosition, cameraMode: Int = CameraMode.NONE): Pair<MapLibreMap, Recording> {
        val recording = Recording(fit).also { it.camera = start }
        val native = Proxy.newProxyInstance(NativeMap::class.java.classLoader, arrayOf(NativeMap::class.java)) { _, method, args ->
            when (method.name) {
                "jumpTo" -> {
                    val padding = args[4] as DoubleArray?
                    recording.camera = CameraPosition.Builder()
                        .target(args[0] as LatLng)
                        .zoom(args[1] as Double)
                        .tilt(args[2] as Double)
                        .bearing(args[3] as Double)
                        .apply { if (padding != null) padding(padding) }
                        .build()
                    recording.jumps++
                    null
                }
                "getCameraPosition" -> recording.camera
                "getCameraForLatLngBounds" -> recording.fit
                else -> defaultFor(method.returnType)
            }
        } as NativeMap
        val dispatcher = CameraChangeDispatcher::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        val transform = Transform::class.java
            .getDeclaredConstructor(MapView::class.java, NativeMap::class.java, CameraChangeDispatcher::class.java)
            .apply { isAccessible = true }
            .newInstance(null, native, dispatcher)
        val uiSettings = inert(UiSettings::class.java)
        set(uiSettings, "focalPointChangeListener", FocalPointChangeListener { _: PointF? -> })
        val map = MapLibreMap::class.java.declaredConstructors.single { it.parameterCount == 7 }
            .apply { isAccessible = true }
            .newInstance(native, transform, uiSettings, null, null, dispatcher, ArrayList<Any>()) as MapLibreMap

        val controllerType = Class.forName("org.maplibre.android.location.LocationCameraController")
        val controller = inert(controllerType)
        set(controller, "maplibreMap", map)
        set(controller, "options", inert(LocationComponentOptions::class.java))
        set(controller, "internalCameraTrackingChangedListener", object : OnCameraTrackingChangedListener {
            override fun onCameraTrackingDismissed() = Unit
            override fun onCameraTrackingChanged(currentMode: Int) { recording.trackingChanges++ }
        })
        field(controller, "cameraMode").setInt(controller, cameraMode)
        recording.cameraController = controller
        val component = LocationComponent::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        set(component, "isComponentInitialized", true)
        set(component, "locationCameraController", controller)
        set(map, "locationComponent", component)
        return map to recording
    }
}
