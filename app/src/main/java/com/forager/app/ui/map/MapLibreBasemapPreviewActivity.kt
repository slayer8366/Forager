package com.forager.app.ui.map

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.forager.app.ui.theme.ForagerTheme
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView

/**
 * Step 1 of the osmdroid -> MapLibre migration (`docs/plans/maplibre-migration.md` §6, Track 2
 * step 1): "MapLibre + a style behind `MapSlot`, rendering nothing but the basemap. Verify on
 * hardware before anything else lands on it." That verification needs a device this environment
 * doesn't have, so this exists purely to put a real, on-device render in front of the project
 * owner — not wired into [com.forager.app.MainActivity] or [MapSlot] at all, so it cannot regress
 * anything a user already relies on. `SightingsMap`, `Basemap`, and every production screen are
 * completely untouched by this class.
 *
 * Launch it manually once installed:
 * `adb shell am start -n com.forager.app/com.forager.app.ui.map.MapLibreBasemapPreviewActivity`
 *
 * Uses MapLibre's own public demo style (`https://demotiles.maplibre.org/style.json` — no key, no
 * account, hosted by the MapLibre project itself) rather than a USGS/OSM style, because the thing
 * under test here is *whether MapLibre renders at all on this app's `minSdk` and device* — not
 * which basemap catalogue it eventually serves. Converting `Basemap`/`BasemapTileSources` to a
 * style-URL catalogue is step 2+ of the migration, gated on this step being confirmed working.
 *
 * Once confirmed on hardware, this class is deleted — it is scaffolding for one verification step,
 * not a feature.
 */
class MapLibreBasemapPreviewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Required once, before any MapView is created — the same "initialize the vendor SDK
        // before touching it" step every Mapbox-lineage map library needs.
        MapLibre.getInstance(this)

        setContent {
            ForagerTheme {
                Box {
                    MapLibreBasemapPreview(modifier = Modifier)
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            "MapLibre step-1 smoke test — demo style, not USGS/OSM. Not reachable from the real app.",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MapLibreBasemapPreview(modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Mirrors SightingsMap's own osmdroid MapView lifecycle wiring — MapLibre's MapView needs the
    // same onCreate/onResume/onPause/onDestroy forwarding, being the same Mapbox-lineage API shape.
    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map -> map.setStyle(DEMO_STYLE_URL) }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private const val DEMO_STYLE_URL = "https://demotiles.maplibre.org/style.json"
