"""Render code-native HUD draw commands and unmodified source texture; not a game screenshot."""
from pathlib import Path
import io,json,zipfile,math
import numpy as np
from PIL import Image,ImageDraw,ImageFont
root=Path(__file__).resolve().parents[1];out=root/'build/botand-hud'
art=Image.open(root/'src/main/resources/assets/celestialfall/textures/botand_hud.png').convert('RGBA')
jar=next((root/'.gradle/loom-cache/minecraftMaven').rglob('*.jar'))
with zipfile.ZipFile(jar) as z:
 providers=json.loads(z.read('assets/minecraft/font/include/default.json'))['providers']
 provider=next(p for p in providers if p.get('file')=='minecraft:font/ascii.png')
 font=Image.open(io.BytesIO(z.read('assets/minecraft/textures/font/ascii.png'))).convert('RGBA')
chars={ch:(x,y) for y,row in enumerate(provider['chars']) for x,ch in enumerate(row)}
cw,ch=font.width//len(provider['chars'][0]),font.height//len(provider['chars'])
glyphs={}
for letter,(gx,gy) in chars.items():
 pixels=np.asarray(font)[gy*ch:(gy+1)*ch,gx*cw:(gx+1)*cw]
 cols=np.where((pixels[:,:,3]>0).any(axis=0))[0];width=int(cols[-1]+1) if len(cols) else 0
 glyphs[letter]=(pixels,width+1)
glyphs[' ']=(np.zeros((ch,cw,4),np.uint8),4)
def rgb(v):return (v>>16&255,v>>8&255,v&255,v>>24&255)
def text(im,label):
 content=label['text'];width=sum(glyphs.get(c,glyphs[' '])[1] for c in content)
 scale=min(label['scale'],label['maxWidth']/max(1,width));a=math.radians(label['angle']);cos,sin=math.cos(a),math.sin(a)
 d=ImageDraw.Draw(im)
 def draw_string(dx,dy,color):
  pen=-int(width/2)
  for char in content:
   pix,advance=glyphs.get(char,glyphs[' '])
   for py in range(ch):
    for px in range(cw):
     if pix[py,px,3]<128:continue
     points=[]
     for sx,sy in [(0,0),(1,0),(1,1),(0,1)]:
      x=(pen+px+sx+dx)*scale;y=(py+sy+dy)*scale
      points.append((label['x']+x*cos-y*sin,label['y']+x*sin+y*cos))
     d.polygon(points,fill=rgb(color))
   pen+=advance
 for dx in [-1,0,1]:
  for dy in [-1,0,1]:
   if dx or dy:draw_string(dx,dy,label['outline'])
 draw_string(0,0,label['color'])
def render(frame):
 im=Image.new('RGBA',(2048,683),(0,0,0,0));im.alpha_composite(art.resize(im.size,Image.Resampling.LANCZOS));d=ImageDraw.Draw(im)
 for q in frame['quads']:
  xy=q['xy'];d.polygon([(xy[i],xy[i+1]) for i in range(0,8,2)],fill=rgb(q['top']))
 for label in frame['labels']:text(im,label)
 return im
states=json.loads((out/'layout.json').read_text());large=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',28);small=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',17)
sheet=Image.new('RGB',(1536,992),'#111a29');d=ImageDraw.Draw(sheet)
d.text((30,20),'BOTAND 2.7  /  HEALTH & PROGRESSION HUD',font=large,fill='#d7efff')
d.text((30,61),'Actual HUD layout + supplied artwork + Minecraft bitmap font / review render, not game footage',font=small,fill='#8fa8c7')
for i,state in enumerate(states):
 x=24+(i%2)*768;y=116+(i//2)*288
 d.rounded_rectangle((x-8,y-8,x+720,y+262),radius=12,fill='#1b293e',outline='#2f4563')
 d.text((x+10,y+4),state['caption'],font=small,fill='#b8ddff')
 hud=render(state['frame']);sheet.paste(hud.resize((704,235),Image.Resampling.LANCZOS),(x+8,y+30),hud.resize((704,235),Image.Resampling.LANCZOS))
 # Full-size review used to check that all original baked numbers are covered.
 if i==0:hud.save(out/'HUD_Default_Fullsize.png')
sheet.save(out/'Botand_HUD_2.7.png')
print(out/'Botand_HUD_2.7.png')
