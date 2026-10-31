# pairs2.py SPEC.json OUT.jpg [grid]: rows of (user's top-down frame | game frame), each 800x450, captions and marks
import sys, json, os
from PIL import Image, ImageDraw, ImageFont
W='/home/user/isosang_work'
D=f'{W}/v3rd/all'
ts=[float(x) for x in open(f'{D}/times.txt')]
spec=json.load(open(sys.argv[1])); out=sys.argv[2]; grid=len(sys.argv)>3
R=spec['shot']
fs=sorted(f for f in os.listdir(R) if f.endswith('.png'))
tk=[(float(f.split('_')[1][:-4]),f) for f in fs]
PW,PH=800,450
F=lambda n: ImageFont.truetype('/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc',n)
def ref(t,box):
    i=min(range(len(ts)),key=lambda i:abs(ts[i]-t)); im=Image.open(f'{D}/a_{i+1:03d}.jpg').convert('RGB')
    if box: im=im.crop(box)
    return im.resize((PW,PH),Image.LANCZOS), ts[i]
def mine(s,box=None):
    f=next(f for t,f in tk if t>=s*20-1e-6); im=Image.open(f'{R}/{f}').convert('RGB')
    if box: im=im.crop(box)
    return im.resize((PW,PH),Image.LANCZOS)
rows=spec['rows']; TOP=70; GAP=10; fn=F(20)
def wrap(text,width):
    words=text.split(' '); lines=[]; cur=''
    for w_ in words:
        t=(cur+' '+w_).strip()
        if fn.getlength(t)>width and cur: lines.append(cur); cur=w_
        else: cur=t
    if cur: lines.append(cur)
    return lines
for row in rows:
    row['_lines']=[(line[0],wrap(line[2:],2*PW-60)) for line in row.get('notes',[])]
    row['_cap']=8+sum(27*len(ws) for _,ws in row['_lines'])+4
page=Image.new('RGB',(2*PW+3*GAP, TOP+sum(PH+r['_cap']+GAP for r in rows)),(12,12,16))
d=ImageDraw.Draw(page)
d.text((GAP,14),spec['title'],fill=(255,255,255),font=F(34))
d.text((GAP,46),spec.get('sub','왼쪽: 보내 주신 영상(위에서)    오른쪽: 보탠드 실제 게임 화면(소프트웨어 렌더링), 같은 구도'),fill=(170,176,190),font=F(19))
COL={'1':(255,80,70),'2':(255,210,60),'3':(80,200,255),'4':(120,255,120),'5':(255,140,255),'6':(255,170,90)}
def mark(img,m):
    g=ImageDraw.Draw(img,'RGBA'); c=COL[str(m['n'])]
    x,y,rx,ry=m['at']; g.ellipse((x-rx,y-ry,x+rx,y+ry),outline=c+(255,),width=5)
    lx,ly=x+rx*0.72,y-ry*0.72
    g.ellipse((lx-17,ly-17,lx+17,ly+17),fill=c+(255,)); g.text((lx-7,ly-13),str(m['n']),fill=(0,0,0),font=F(24))
y=TOP
for row in rows:
    a,ta=ref(row['ref'],row.get('refbox')); b=mine(row['mine'],row.get('minebox'))
    if grid:
        for img in (a,b):
            g=ImageDraw.Draw(img)
            for k in range(1,10): g.line((k*PW//10,0,k*PW//10,PH),fill=(255,0,255),width=1); g.line((0,k*PH//10,PW,k*PH//10),fill=(255,0,255),width=1); g.text((k*PW//10+2,2),str(k),fill=(255,0,255),font=F(14)); g.text((2,k*PH//10+2),str(k),fill=(255,0,255),font=F(14))
    for m in row.get('marks',[]): mark(a if m['side']=='ref' else b,m)
    page.paste(a,(GAP,y)); page.paste(b,(2*GAP+PW,y))
    d.rectangle((GAP,y,GAP+360,y+28),fill=(0,0,0)); d.text((GAP+8,y+3),f"영상 {ta:.2f}초 · {row['refcap']}",fill=(242,211,107),font=F(19))
    d.rectangle((2*GAP+PW,y,2*GAP+PW+360,y+28),fill=(0,0,0)); d.text((2*GAP+PW+8,y+3),f"장면 {row['mine']:.2f}초 · {row['minecap']}",fill=(242,211,107),font=F(19))
    yy=y+PH+6
    for n,ws in row['_lines']:
        c=COL.get(n,(230,230,230))
        d.ellipse((GAP+2,yy+3,GAP+24,yy+25),fill=c); d.text((GAP+8,yy+2),n,fill=(0,0,0),font=F(18))
        for wl in ws: d.text((GAP+32,yy+1),wl,fill=(235,235,240),font=fn); yy+=27
    y+=PH+row['_cap']+GAP
page.save(out,quality=90); print(out,page.size)
