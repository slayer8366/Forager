package org.maplibre.android.maps

import java.lang.reflect.Proxy

/**
 * A real MapLibre [Style], put through the SDK's own lifecycle calls: the test half of dispatch 2026-10-25-01 (the map
 * crash on a style change). It sits in MapLibre's package because the lifecycle methods are package-private.
 * [Style.Builder.build], `onDidFinishLoadingStyle` and `clear` are what `MapLibreMap.setStyle` and
 * `notifyStyleLoaded` call (read from the 13.5.0 classes with javap). `setStyle` calls `clear()` on the old style before
 * the new one is built, which leaves it with `fullyLoaded = false`. Every getter on that old style then throws the
 * S22's `IllegalStateException`. The native map is a proxy: its methods do nothing and return null, false or zero.
 * A source lookup on it finds nothing, so the style has no sources at all.
 */
object StyleLifecycleFixture {
    private fun inertNativeMap(): NativeMap = Proxy.newProxyInstance(
        NativeMap::class.java.classLoader,
        arrayOf(NativeMap::class.java),
    ) { _, method, _ ->
        when (method.returnType) {
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
    } as NativeMap

    /** A style MapLibre has finished loading: the one a `setStyle` callback is handed. */
    fun loadedStyle(): Style = Style.Builder().build(inertNativeMap()).also { it.onDidFinishLoadingStyle() }

    /** A loaded style after a later `setStyle` has started: what `SightingsMapState.loadedStyle` holds until that load's callback. */
    fun replacedStyle(): Style = loadedStyle().also { it.clear() }
}
