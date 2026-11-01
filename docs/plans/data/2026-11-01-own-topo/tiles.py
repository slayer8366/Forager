#!/usr/bin/env python3
"""Exact Web Mercator tile counts for the G7 footprint and for one offline area. No network.
Input: g7-cells.log (Aspen's G7 log, one line per 1-degree cell, 'NnnWwww ok ... valid=NN ...').
Footprint: cells logged ok with valid>0. 'valid' is a share of pixels with data, so the land-weighted
line weights a tile by the mean valid share of the cells it touches. It still counts lakes, and the
offshore buffer of the US polygon where 3DEP has a tile."""
import math,re,sys,json
here=sys.argv[1]
cells={}
for l in open(here+'/g7-cells.log'):
    m=re.match(r'([NS])(\d\d)([EW])(\d\d\d) ok .*valid=([\d.]+)',l)
    if not m: continue
    lat=int(m[2])*(1 if m[1]=='N' else -1); lon=int(m[4])*(1 if m[3]=='E' else -1)
    cells[(lat,lon)]=float(m[5])          # later passes overwrite earlier ones
cells={k:v for k,v in cells.items() if v>0}
def tx(lon,z): return (lon+180)/360*2**z
def ty(lat,z):
    r=math.radians(lat); return (1-math.log(math.tan(r)+1/math.cos(r))/math.pi)/2*2**z
out={'cells':len(cells)}
for z in range(0,15):   # was 0,16: z15 is about 66M dict entries; the stopped run never finished it
    t={}
    for (lat,lon),v in cells.items():
        for x in range(int(tx(lon,z)),int(math.ceil(tx(lon+1,z)-1e-9))):
            for y in range(int(ty(lat+1,z)),int(math.ceil(ty(lat,z)-1e-9))):
                a=t.setdefault((x,y),[0,0]); a[0]+=v; a[1]+=1
    w=sum(a[0]/a[1]/100 for a in t.values())
    out[z]=(len(t),round(w))
    if z==12: json.dump(sorted(k for k in t),open('/tmp/z12tiles.json','w'))
    print(z,len(t),round(w),flush=True)
print('cum z0-11',sum(out[z][0] for z in range(12)),'z0-12',sum(out[z][0] for z in range(13)),
      'land-weighted z0-12',sum(out[z][1] for z in range(13)))
# One offline area: the same box GeoDistance.boundingBox gives, by the same tile math.
def area(lat,km):
    dlat=km/110.574; dlon=km/(111.320*math.cos(math.radians(lat)))
    return lat-dlat,lat+dlat,-120-dlon,-120+dlon
def n(box,z):
    s,nn,w,e=box
    return (int(tx(e,z))-int(tx(w,z))+1)*(int(ty(s,z))-int(ty(nn,z))+1)
for km in (8,10,24):
    b=area(45,km); print('area',km,'km',{z:n(b,z) for z in range(10,16)},'z10-15 total',sum(n(b,z) for z in range(10,16)))
