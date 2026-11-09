package com.zynergylabs.forager.app.domain

/**
 * Small GPX files for plan T16's tests, written by hand from the GPX 1.1 schema in the shape each app is
 * known to write (the dispatch: "write small fixtures by hand ... don't download anyone's data"). The
 * coordinates are made up. Each shape is another app's export by its structure only, not a file any app produced.
 */
internal object GpxImportFixtures {

    /** Shape A, another app's export: a `creator` attribute, a `<metadata><time>`, one named `<trk>` with a `<type>`, whole-second Z times. */
    val SHAPE_A = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx creator="ShapeAGPX" version="1.1" xmlns="http://www.topografix.com/GPX/1/1" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">
         <metadata>
          <time>2026-09-20T15:00:00Z</time>
         </metadata>
         <trk>
          <name>Morning Hike</name>
          <type>hiking</type>
          <trkseg>
           <trkpt lat="45.5000000" lon="-122.6000000">
            <ele>100.2</ele>
            <time>2026-09-20T15:00:00Z</time>
           </trkpt>
           <trkpt lat="45.5010000" lon="-122.6010000">
            <ele>101.0</ele>
            <time>2026-09-20T15:00:10Z</time>
           </trkpt>
           <trkpt lat="45.5020000" lon="-122.6020000">
            <ele>102.4</ele>
            <time>2026-09-20T15:00:20Z</time>
           </trkpt>
          </trkseg>
         </trk>
        </gpx>
    """.trimIndent()

    /**
     * Shape B, another app's export: a `<metadata><name>`, an unnamed `<trk>` in two `<trkseg>`s, millisecond times
     * (.400 rounds down, .600 up), and two waypoints with `<sym>`, one with no `<name>` and no `<time>`.
     */
    val SHAPE_B = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx xmlns="http://www.topografix.com/GPX/1/1" version="1.1" creator="ShapeBGPS">
          <metadata><name>Chanterelle ridge</name></metadata>
          <wpt lat="45.6" lon="-122.7">
            <ele>300.0</ele>
            <time>2026-09-21T16:05:00.700Z</time>
            <name>Patch</name>
            <desc>Golden, under hemlock</desc>
            <sym>Flag, Green</sym>
          </wpt>
          <wpt lat="45.61" lon="-122.71">
            <sym>Pin</sym>
          </wpt>
          <trk>
            <trkseg>
              <trkpt lat="45.6" lon="-122.7"><ele>300.0</ele><time>2026-09-21T16:00:00.400Z</time></trkpt>
              <trkpt lat="45.601" lon="-122.701"><ele>301.5</ele><time>2026-09-21T16:00:05.600Z</time></trkpt>
            </trkseg>
            <trkseg>
              <trkpt lat="45.602" lon="-122.702"><ele>302.0</ele><time>2026-09-21T16:10:00Z</time></trkpt>
            </trkseg>
          </trk>
        </gpx>
    """.trimIndent()

    /** Shape C, another app's export: its own namespace, per-point `<extensions>`, `<hdop>`, and times with a +02:00 offset. No `<name>` anywhere. */
    val SHAPE_C = """
        <?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
        <gpx version="1.1" creator="ShapeC~ 4.7.2" xmlns="http://www.topografix.com/GPX/1/1" xmlns:shapec="https://example.com/gpx/shape-c">
          <trk>
            <trkseg>
              <trkpt lat="46.1" lon="7.1">
                <ele>1200</ele>
                <time>2026-09-22T09:00:00+02:00</time>
                <hdop>4.2</hdop>
                <extensions><shapec:speed>1.2</shapec:speed></extensions>
              </trkpt>
              <trkpt lat="46.101" lon="7.101">
                <ele>1205</ele>
                <time>2026-09-22T09:01:00+02:00</time>
                <hdop>3.9</hdop>
                <extensions><shapec:speed>1.3</shapec:speed></extensions>
              </trkpt>
            </trkseg>
          </trk>
        </gpx>
    """.trimIndent()

    /** Three tracks (the second unnamed) and two loose waypoints. */
    val MULTI_TRACK = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
          <wpt lat="45.0" lon="-122.0"><time>2026-09-23T10:30:00Z</time><name>Car</name></wpt>
          <wpt lat="45.1" lon="-122.1"><time>2026-09-23T11:30:00Z</time><name>Spring</name></wpt>
          <trk><name>Day one</name><trkseg>
            <trkpt lat="45.0" lon="-122.0"><time>2026-09-23T10:00:00Z</time></trkpt>
            <trkpt lat="45.01" lon="-122.01"><time>2026-09-23T11:00:00Z</time></trkpt>
          </trkseg></trk>
          <trk><trkseg>
            <trkpt lat="45.2" lon="-122.2"><time>2026-09-24T10:00:00Z</time></trkpt>
            <trkpt lat="45.21" lon="-122.21"><time>2026-09-24T10:30:00Z</time></trkpt>
          </trkseg></trk>
          <trk><name>Day three</name><trkseg>
            <trkpt lat="45.3" lon="-122.3"><time>2026-09-25T10:00:00Z</time></trkpt>
            <trkpt lat="45.31" lon="-122.31"><time>2026-09-25T10:45:00Z</time></trkpt>
          </trkseg></trk>
        </gpx>
    """.trimIndent()

    /** A drawn line with no times at all, out of file order by coordinates, and an untimed, unnamed waypoint. */
    val NO_TIMES = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="planner" xmlns="http://www.topografix.com/GPX/1/1">
          <wpt lat="45.45" lon="-122.45"/>
          <trk><name>Planned loop</name><trkseg>
            <trkpt lat="45.5" lon="-122.5"><ele>10</ele></trkpt>
            <trkpt lat="45.4" lon="-122.4"><ele>20</ele></trkpt>
            <trkpt lat="45.6" lon="-122.6"><ele>30</ele></trkpt>
          </trkseg></trk>
        </gpx>
    """.trimIndent()

    /** Routes and no track. */
    val ROUTE_ONLY = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="planner" xmlns="http://www.topografix.com/GPX/1/1">
          <rte><name>Plan</name>
            <rtept lat="45.5" lon="-122.5"/>
            <rtept lat="45.6" lon="-122.6"/>
          </rte>
        </gpx>
    """.trimIndent()

    /** Waypoints and nothing else. */
    val WAYPOINTS_ONLY = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
          <wpt lat="45.0" lon="-122.0"><name>Car</name></wpt>
        </gpx>
    """.trimIndent()

    /** Cut off mid-element: not well-formed XML. */
    val BROKEN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
          <trk><trkseg><trkpt lat="45.0" lon="-122.0"><time>2026-09-23T10:00:00Z</ti
    """.trimIndent()

    /** Well-formed, but not GPX. */
    val NOT_GPX = """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2"><Document><name>Not GPX</name></Document></kml>
    """.trimIndent()

    /** A track point with no `lat`. */
    val BAD_COORDINATES = """
        <?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
          <trk><trkseg>
            <trkpt lat="45.0" lon="-122.0"><time>2026-09-23T10:00:00Z</time></trkpt>
            <trkpt lon="-122.1"><time>2026-09-23T10:01:00Z</time></trkpt>
          </trkseg></trk>
        </gpx>
    """.trimIndent()

    /** An external entity in a DOCTYPE, pointing at [secretPath]: the classic XXE shape. Must never be read. */
    fun externalEntity(secretPath: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE gpx [ <!ENTITY secret SYSTEM "file://$secretPath"> ]>
        <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
          <trk><name>&secret;</name><trkseg>
            <trkpt lat="45.0" lon="-122.0"><time>2026-09-23T10:00:00Z</time></trkpt>
          </trkseg></trk>
        </gpx>
    """.trimIndent()

    fun source(xml: String, displayName: String? = "walk.gpx"): GpxFileSource = bytesSource(xml.toByteArray(), displayName)

    /** Honours the limit as the real source does: more than `maxBytes` is [GpxFileRead.TooBig]. */
    fun bytesSource(bytes: ByteArray, displayName: String? = "walk.gpx"): GpxFileSource =
        GpxFileSource { maxBytes -> if (bytes.size > maxBytes) GpxFileRead.TooBig else GpxFileRead.Bytes(bytes, displayName) }
}
