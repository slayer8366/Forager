"""Item 9 (dispatch -446): cut a 60 x 60 km region from the Oregon extract, for building region-only routing
data. Keeps every way with a point in the square (whole, with all its nodes, so lines crossing the edge
stay routable) and the relations among them. Writes region.osm.pbf; prints sizes only."""
import json, math, os, sys
import osmium

src, out = sys.argv[1], sys.argv[2]
r = json.load(open("routes.json"))["r2"]
clat = (r[0][0] + r[1][0]) / 2; clon = (r[0][1] + r[1][1]) / 2
hl = 30_000 / 111_320; ho = 30_000 / (111_320 * math.cos(math.radians(clat)))
s, w, n, e = clat - hl, clon - ho, clat + hl, clon + ho

if os.path.exists(out):
    os.remove(out)
with osmium.BackReferenceWriter(out, ref_src=src, overwrite=True) as writer:
    for obj in osmium.FileProcessor(src).with_locations():
        if obj.is_node():
            if obj.location.valid() and s <= obj.location.lat <= n and w <= obj.location.lon <= e:
                writer.add_node(obj)
        elif obj.is_way():
            if any(nd.location.valid() and s <= nd.lat <= n and w <= nd.lon <= e for nd in obj.nodes):
                writer.add_way(obj)
print("region.osm.pbf %.1f MB (from %.1f MB)" % (os.path.getsize(out) / 1e6, os.path.getsize(src) / 1e6))
