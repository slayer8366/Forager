#!/usr/bin/env python3
"""Mean gradient from the Terrarium tiles already in the repo
(docs/audits/data/2026-09-30-topo-night/renders/dem-tiles, exported to a scratch folder given as argv[1]).
Central differences on the tile's own grid. No network. Each tile is one sample, in NW Oregon."""
import sys,os,math
from PIL import Image
d=sys.argv[1]
for n in sorted(os.listdir(d)):
    z,x,y=map(int,n[:-4].split('_'))
    im=Image.open(os.path.join(d,n)).convert('RGB'); w,h=im.size; p=im.load()
    H=[[p[i,j][0]*256+p[i,j][1]+p[i,j][2]/256-32768 for i in range(w)] for j in range(h)]
    lat=math.degrees(math.atan(math.sinh(math.pi*(1-2*(y+.5)/2**z))))
    m=40075016.7*math.cos(math.radians(lat))/(2**z*256)
    t=0;c=0;mx=-1e9;mn=1e9
    for j in range(1,h-1):
        for i in range(1,w-1):
            gx=(H[j][i+1]-H[j][i-1])/(2*m); gy=(H[j+1][i]-H[j-1][i])/(2*m)
            t+=math.hypot(gx,gy); c+=1; mx=max(mx,H[j][i]); mn=min(mn,H[j][i])
    print(n,f'{w}px lat={lat:.2f} px={m:.1f}m mean_gradient={t/c:.3f} relief={mx-mn:.0f}m')
