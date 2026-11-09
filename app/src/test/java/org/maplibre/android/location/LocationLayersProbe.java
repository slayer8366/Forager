package org.maplibre.android.location;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Test-only window onto the ids of the layers MapLibre 13.5.0's own position dot adds to a style, in the SDK's own
 * package because {@code LocationComponentConstants} is package-private (dispatch 2026-11-06-01 Amendment 1, I9).
 *
 * <p>The ids are the SDK's own constants, so a renamed layer in a later MapLibre fails here at compile time rather than
 * passing on a stale copy. What is modelled, from {@code javap -c} on the pinned 13.5.0 AAR, is the order and the type:
 * {@code SymbolLocationLayerRenderer.addLayers} adds the shadow, background, foreground and bearing layers as symbol
 * layers ({@code LayerSourceProvider.generateLayer} builds a {@code SymbolLayer}), each below the one added before it,
 * then the accuracy circle below the background and the pulsing circle below the accuracy circle (both
 * {@code CircleLayer}s). Which of the two orders it takes depends on {@code bearingOnTop}; both are given, bottom to top,
 * each entry the id and whether the layer is a symbol layer.
 *
 * <p>When they are in a style: {@code MapLibreMap.notifyStyleLoaded} calls {@code Style.onDidFinishLoadingStyle}, then
 * {@code LocationComponent.onFinishLoadingStyle}, which adds these layers whenever the component was initialised on an
 * earlier style, and only then the app's {@code setStyle} callback. So on every style load after the first activation
 * the callback finds them already in {@code style.layers}, on top of the basemap's own.
 */
public final class LocationLayersProbe {
    private LocationLayersProbe() {}

    /** The order with the bearing on top ({@code bearingOnTop} true), bottom to top. */
    public static List<Object[]> bearingOnTop() {
        return Collections.unmodifiableList(Arrays.asList(
                new Object[] {LocationComponentConstants.SHADOW_LAYER, true},
                new Object[] {LocationComponentConstants.PULSING_CIRCLE_LAYER, false},
                new Object[] {LocationComponentConstants.ACCURACY_LAYER, false},
                new Object[] {LocationComponentConstants.BACKGROUND_LAYER, true},
                new Object[] {LocationComponentConstants.FOREGROUND_LAYER, true},
                new Object[] {LocationComponentConstants.BEARING_LAYER, true}));
    }

    /** The order with the bearing under the background ({@code bearingOnTop} false), bottom to top. */
    public static List<Object[]> bearingBelow() {
        return Collections.unmodifiableList(Arrays.asList(
                new Object[] {LocationComponentConstants.SHADOW_LAYER, true},
                new Object[] {LocationComponentConstants.BEARING_LAYER, true},
                new Object[] {LocationComponentConstants.PULSING_CIRCLE_LAYER, false},
                new Object[] {LocationComponentConstants.ACCURACY_LAYER, false},
                new Object[] {LocationComponentConstants.BACKGROUND_LAYER, true},
                new Object[] {LocationComponentConstants.FOREGROUND_LAYER, true}));
    }
}
