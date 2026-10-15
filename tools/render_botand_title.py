"""Render the exact ordered title commands, font atlas and source textures for review.
This exports transparent UI overlays only, not the actual Minecraft world camera. No user artwork is edited.
"""
from pathlib import Path
import json, math, sys, subprocess, io, os
from PIL import Image,ImageDraw
import numpy as np
root=Path(__file__).resolve().parents[1];out=root/'build/botand-title';assets=root/'src/main/resources/assets/celestialfall'
textures={n:Image.open(assets/f'textures/title/{n}.png').convert('RGBA') for n in ['logo']}
font=Image.open(assets/'textures/title/font.png').convert('RGBA')
metrics=json.loads((root/'tools/title-font-metrics.json').read_text());cell=metrics['cell'];glyphs=metrics['glyphs']
def rgba(v):return(v>>16&255,v>>8&255,v&255,v>>24&255)
def text_width(s):return sum(4 if ch==' ' else glyphs.get(ch,{'advance':12})['advance'] for ch in s)
def render(data,pixel_scale=2):
 layout=data['layout'];scale=layout['scale']*pixel_scale;ox=layout['ox']*pixel_scale;oy=layout['oy']*pixel_scale
 im=Image.new('RGBA',(round(data['width']*pixel_scale),round(data['height']*pixel_scale)),(0,0,0,0))
 for cmd in data['scene']['commands']:
  points=cmd['points']
  if cmd['kind']=='image':
   x,y,w,h,alpha=points
   if alpha<=0:continue
   layer=textures[cmd['asset']].resize((max(1,round(w*scale)),max(1,round(h*scale))),Image.Resampling.BILINEAR)
   if alpha<1:layer.putalpha(layer.getchannel('A').point(lambda p:round(p*alpha)))
   im.alpha_composite(layer,(round(ox+x*scale),round(oy+y*scale)))
  elif cmd['kind']=='shape':
   xy=[(ox+points[i]*scale,oy+points[i+1]*scale) for i in range(0,len(points),2)]
   if cmd['top']==cmd['bottom']:
    overlay=Image.new('RGBA',im.size);ImageDraw.Draw(overlay).polygon(xy,fill=rgba(cmd['top']));im.alpha_composite(overlay);continue
   miny=min(y for x,y in xy);maxy=max(y for x,y in xy)
   x0=max(0,math.floor(min(x for x,y in xy)));x1=min(im.width,math.ceil(max(x for x,y in xy)));y0=max(0,math.floor(miny));y1=min(im.height,math.ceil(maxy))
   if x1<=x0 or y1<=y0:continue
   mask=Image.new('L',(x1-x0,y1-y0));ImageDraw.Draw(mask).polygon([(x-x0,y-y0) for x,y in xy],fill=255)
   t=np.clip((np.arange(y0,y1)-miny)/max(.001,maxy-miny),0,1)[:,None]
   a,b=np.array(rgba(cmd['top'])),np.array(rgba(cmd['bottom']));row=(a*(1-t)+b*t).astype(np.uint8)
   arr=np.broadcast_to(row[:,None,:],(y1-y0,x1-x0,4)).copy();arr[:,:,3]=(arr[:,:,3].astype(np.uint16)*np.asarray(mask)//255).astype(np.uint8)
   layer=Image.fromarray(arr,'RGBA');im.alpha_composite(layer,(x0,y0))
  else:
   s=cmd['text'];width=text_width(s);factor=min(cmd['size']/12,cmd['maxWidth']/max(1,width));advance=factor*scale
   x=ox+(points[0]-(width*factor/2 if cmd['centered'] else 0))*scale;y=oy+points[1]*scale
   color=rgba(cmd['top'])
   for ch in s:
    if ch==' ':x+=4*advance;continue
    g=glyphs.get(ch)
    if g is None:continue
    side=max(1,round(12*advance));tile=font.crop((g['x'],g['y'],g['x']+cell,g['y']+cell)).getchannel('A').resize((side,side),Image.Resampling.BILINEAR)
    if color[3]!=255:tile=tile.point(lambda p:round(p*color[3]/255))
    layer=Image.new('RGBA',(side,side),color[:3]+(255,));layer.putalpha(tile);im.alpha_composite(layer,(round(x),round(y)))
    x+=g['advance']*advance
 return im
if __name__=='__main__':
 scenes=json.loads((out/'scenes.json').read_text())
 for name,data in scenes.items():
  buffer=io.BytesIO();render(data).save(buffer,format='PNG');payload=buffer.getvalue();target=out/f'{name}.png'
  with target.open('wb') as f:f.write(payload);f.flush();os.fsync(f.fileno())
  assert target.read_bytes()==payload
  Image.open(target).verify()
 print('Rendered',', '.join(scenes))

