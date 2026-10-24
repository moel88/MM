"""Build a small, high-resolution UI bitmap font from OFL Noto Sans CJK KR.
The original font is a development input; only the needed glyph atlas is shipped.
"""
from pathlib import Path
import json,math,re,io,os
from PIL import Image,ImageFont,ImageDraw
root=Path(__file__).resolve().parents[1]
font_path=root.parent/'preview-assets/NotoSansCJKkr-Regular.otf'
font=ImageFont.truetype(str(font_path),53)
text=''.join(p.read_text() for p in (root/'src/main/java/dev/sangwoo/celestialfall/client/title').glob('*.java'))
chars=sorted(set(chr(i) for i in range(33,127))|set(c for c in text if ord(c)>127 and not c.isspace()))
cell=64;cols=16;rows=math.ceil(len(chars)/cols)
atlas=Image.new('RGBA',(cols*cell,rows*cell));draw=ImageDraw.Draw(atlas);metrics={}
for i,ch in enumerate(chars):
 x=i%cols*cell;y=i//cols*cell
 glyph=Image.new('RGBA',(cell,cell));gd=ImageDraw.Draw(glyph);bounds=gd.textbbox((0,48),ch,font=font,anchor='ls')
 gd.text((-bounds[0],48),ch,font=font,fill='white',anchor='ls')
 atlas.paste(glyph,(x,y));box=glyph.getbbox();ink_width=box[2] if box else 0
 metrics[ch]={'x':x,'y':y,'width':ink_width,'advance':int(ink_width*12/cell+.5)+1}
asset=root/'src/main/resources/assets/celestialfall';(asset/'font').mkdir(parents=True,exist_ok=True)
buffer=io.BytesIO();atlas.save(buffer,format='PNG');payload=buffer.getvalue();target=asset/'textures/title/font.png'
with target.open('wb') as f:f.write(payload);f.flush();os.fsync(f.fileno())
assert target.read_bytes()==payload
strings=[''.join(chars[i:i+cols]).ljust(cols,'\x00') for i in range(0,len(chars),cols)]
(asset/'font/title.json').write_text(json.dumps({'providers':[{'type':'bitmap','file':'celestialfall:title/font.png','height':12,'ascent':10,'chars':strings},{'type':'space','advances':{' ':4}},{'type':'reference','id':'minecraft:default'}]},ensure_ascii=False))
(root/'tools/title-font-metrics.json').write_text(json.dumps({'cell':cell,'height':12,'ascent':10,'glyphs':metrics},ensure_ascii=False))
print('Glyphs',len(chars),'atlas',atlas.size,'bytes',(asset/'textures/title/font.png').stat().st_size)
