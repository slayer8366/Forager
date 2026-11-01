import re,sys,time,urllib.request,urllib.parse,csv
UA="Forager-research/1.0 (Juniper; read-only)"
def page(prefix,tok):
    q={'list-type':'2','prefix':prefix}
    if tok:q['continuation-token']=tok
    r=urllib.request.Request("https://prd-tnm.s3.amazonaws.com/?"+urllib.parse.urlencode(q),headers={'User-Agent':UA})
    return urllib.request.urlopen(r,timeout=60).read().decode()
for name,prefix,lim in (('13','StagedProducts/Elevation/13/TIFF/current/',40),('1','StagedProducts/Elevation/1/TIFF/current/',40)):
    tok=None;n=0;tifs={};pages=0
    while pages<lim:
        x=page(prefix,tok);pages+=1
        for k,s in re.findall(r'<Key>([^<]+)</Key>.*?<Size>(\d+)</Size>',x):
            n+=1
            if k.endswith('.tif'):tifs[k]=int(s)
        m=re.search(r'<NextContinuationToken>([^<]+)',x)
        if not m:break
        tok=m.group(1);time.sleep(0.4)
    print(name,'pages',pages,'keys',n,'tifs',len(tifs),'bytes',sum(tifs.values()),'complete',m is None)
    with open(f'/tmp/s3-{name}.csv','w') as f:
        for k,s in tifs.items():f.write(f"{k.split('/')[-1]},{s}\n")
