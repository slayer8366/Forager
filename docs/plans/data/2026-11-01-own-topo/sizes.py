#!/usr/bin/env python3
"""Byte models for own topo. Inputs: g7-cells.log, the two S3 listings, tiles.rerun.out, dem-tile-sizes.txt,
dem_slope.out, published-sizes.txt. No network. Every model has a low and a high; sources in comments."""
import re,gzip,math,sys
here=sys.argv[1]
GB=1e9
# --- tile counts (tiles.rerun.out)
T={};L={}
for l in open(here+'/tiles.rerun.out'):
    m=re.match(r'(\d+) (\d+) (\d+)$',l)
    if m:T[int(m[1])]=int(m[2]);L[int(m[1])]=int(m[3])
cum=lambda z:sum(T[i] for i in range(z+1))
print('tiles cum z0-10',cum(10),'z0-11',cum(11),'z0-12',cum(12),' z13',T[13],'z14',T[14])
# --- listings: the one stray 1/3" key inside the 1" prefix is dropped and reported
one=[l.strip().split(',') for l in gzip.open(here+'/s3-1arcsec.csv.gz','rt')]
stray=[r for r in one if not r[0].startswith('USGS_1_')]
one=[r for r in one if r[0].startswith('USGS_1_')]
third=[l.strip().split(',') for l in gzip.open(here+'/s3-13arcsec.csv.gz','rt')]
print('1in listing',len(one),round(sum(int(r[1]) for r in one)/GB,2),'GB; stray rows',stray)
print('1/3in listing',len(third),round(sum(int(r[1]) for r in third)/GB,1),'GB')
# --- G7 log: last ok line per cell
cells={}
for l in open(here+'/g7-cells.log'):
    m=re.match(r'([NS])(\d\d)([EW])(\d\d\d) ok (.*)',l)
    if not m:continue
    lat=int(m[2])*(1 if m[1]=='N' else -1);lon=int(m[4])*(1 if m[3]=='E' else -1)
    r=m[5];d=re.search(r'3dep:\S+:(\d+)',r);ma=re.search(r'MAXIMUM (-?\d+) MINIMUM (-?\d+)',r);v=re.search(r'valid=([\d.]+)',r);t=re.search(r'(\d+)s\s*$',r)
    cells[(lat,lon)]=dict(dep=int(d[1]) if d else 0,in1=' 1in' in r,mrdem='mrdem' in r,relief=(int(ma[1])-int(ma[2])) if ma else 0,valid=float(v[1]) if v else 0,secs=int(t[1]) if t else 0)
dep=[c for c in cells.values() if c['dep']]
print('hgt cells',len(cells),'3DEP cells',len(dep),'bytes',round(sum(c['dep'] for c in dep)/GB,1),'GB')
no1=[c for c in dep if not c['in1']]
print('3DEP cells with no 1in copy',len(no1),round(sum(c['dep'] for c in no1)/GB,1),'GB to re-pull; with copy',len(dep)-len(no1))
print('cells on MRDEM',sum(c['mrdem'] for c in cells.values()),'MRDEM only',sum(c['mrdem'] and not c['dep'] for c in cells.values()))
print('sum of per-cell seconds',sum(c['secs'] for c in cells.values())/3600,'h (3 workers)')
# --- relief, 256 px PNG. High: mean of the four real hilly z12 Terrarium PNGs in the repo is 115.7 KB, an upper
# bound; 90 KB is taken as high for a continent that is not all hills. Low 40 KB: the 10-22 note's "plains compress
# better", unmeasured. Low-zoom tiles are at the same per-tile figures.
for lo,hi,z in ((40e3,90e3,12),(40e3,90e3,11),(40e3,90e3,10)):
    print(f'relief 256px PNG z0-{z}: {cum(z)*lo/GB:.0f}-{cum(z)*hi/GB:.0f} GB')
# 512 px lossless WebP Terrarium, z0-11 = the same pixels as 256 px z0-12. Published archive averages 38.2 KB per
# distinct 512 px tile; ours sits at native 30 m rather than overzoomed, so taken as 1 to 2 times that (inferred).
for lo,hi,z in ((38e3,76e3,11),(38e3,76e3,10)):
    print(f'relief 512px WebP z0-{z}: {cum(z)*lo/GB:.0f}-{cum(z)*hi/GB:.0f} GB')
# --- contours. Exact relation (coarea): line length per area = mean gradient / interval.
# Mean gradient per cell = k * cell relief. k is weakly known: low 6.5e-5 = 0.105 (mean of the four z12 tiles)/1609 m
# (their cell N45W123); high 2.5e-4 = gradient 0.4 for the same relief, a rugged reading. Area weighted by 'valid'.
def lengthkm(k,ci):
    t=0;a=0
    for (lat,lon),c in cells.items():
        A=111.32*110.57*math.cos(math.radians(lat+.5))*c['valid']/100
        g=min(k*c['relief'],0.6); t+=A*(g/ci)*1000; a+=A   # km2 * 1/m = 1e6 m = 1000 km
    return t,a
for ci,name in ((12.192,'40 ft'),(20,'20 m'),(30.48,'100 ft')):
    r={}
    for k,s,b,gz,tag in ((6.5e-5,40,2.0,.7,'low'),(2.5e-4,15,3.0,.85,'high')):
        km,a=lengthkm(k,ci); m=km*1000
        z14=m/s*b*gz*1.1; z13=(m/5)/(2*s)*b*gz*1.1   # index lines one in five, twice the spacing; +10% features, buffers
        r[tag]=(km,(z14+z13)/GB)
    print(name,f"line {r['low'][0]/1e6:.0f}-{r['high'][0]/1e6:.0f} M km; z13-14 {r['low'][1]:.1f}-{r['high'][1]:.1f} GB; area counted {a/1e6:.1f} M km2")
# --- one offline area, r km at 45N, steep ground. Tile sets from tiles.rerun.out's area lines; the zooms follow
# coveringZoomLevel: 256 px raster-dem floor(10+1)..min(floor(15+1),12) = z11-12; 512 px z10-11; vector z13-14.
area={8:{10:1,11:4,12:9,13:30,14:100},10:{10:4,11:9,12:16,13:49,14:169},24:{10:9,11:25,12:64,13:225,14:841}}
for r,n in area.items():
    png=(n[11]+n[12])*115.7e3/1e6; webp=(n[10]+n[11])*76e3/1e6
    A=(2*r)**2*1e6
    con=[]
    for g,s,b,gz in ((.2,40,2,.7),(.5,15,3,.85)):
        m=A*g/12.192; con.append((m/s+m/5/(2*s))*b*gz*1.1/1e6)
    print(f'area r={r} km: relief PNG {n[11]+n[12]} tiles {png:.1f} MB, or WebP {n[10]+n[11]} tiles {webp:.1f} MB; contours {n[13]+n[14]} tiles {con[0]:.1f}-{con[1]:.1f} MB')
# --- R2 and upload
for gb in (20,60,125):
    print(f'{gb} GB: ${gb*.015:.2f}/month; upload at 2.9 MiB/s {gb*1e9/(2.9*2**20)/3600:.1f} h; parts at 16 MiB {gb*1e9/2**24:.0f}')
print('download at 10 GB/h: US re-pull + MRDEM 84 GB:',round((sum(c['dep'] for c in no1)/GB+84)/10,1),'h')
