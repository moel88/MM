"""Original procedural assets for the gold/blue sword; no frames or audio taken from the GIF."""
from pathlib import Path
from PIL import Image,ImageDraw
import io
def save_png(im,path):
 buf=io.BytesIO();im.save(buf,format="PNG");path.write_bytes(buf.getvalue())
import json,numpy as np,subprocess,tempfile,wave,sys
root=Path(__file__).resolve().parents[1];a=root/'src/main/resources/assets/celestialfall'
im=Image.new('RGBA',(64,64),(219,233,239,255));d=ImageDraw.Draw(im)
for y in range(64):
 for x in range(64):
  if (x*3+y)%13==0:d.point((x,y),fill=(194,217,232,255))
for x,y,w,h in [(20,20,8,12),(44,20,4,12),(36,52,4,12),(4,20,4,12),(20,52,4,12)]:
 d.rectangle((x,y,x+w-1,y+h-1),fill=(43,81,145,255));d.line((x,y,x,y+h-1),fill=(230,192,105,255))
 d.line((x+w-1,y,x+w-1,y+h-1),fill=(147,199,227,255))
d.polygon([(22,20),(26,20),(24,29)],fill=(239,242,221,255));d.line((20,29,27,29),fill=(234,199,104,255))
save_png(im,a/'textures/entity/heaven_costume.png')
im=Image.new('RGBA',(16,16),(226,237,240,255));d=ImageDraw.Draw(im)
for y in (3,8,13):d.line((0,y,15,y),fill=(39,75,116,255))
save_png(im,a/'textures/entity/heaven_glove.png')
im=Image.new('RGBA',(16,16),(224,157,68,255));d=ImageDraw.Draw(im)
d.line((2,0,2,7,6,7,6,3),fill=(113,68,40,255));d.rectangle((8,0,15,7),fill=(255,221,138,255));d.rectangle((0,8,7,15),fill=(201,157,77,255));d.rectangle((8,8,15,15),fill=(47,91,156,255))
save_png(im,a/'textures/item/heaven_sword.png')
def element(lo,hi,uv):return {'from':lo,'to':hi,'faces':{f:{'uv':uv,'texture':'#all'} for f in ('north','south','east','west','up','down')}}
old=json.loads((a/'models/item/atomic_sword.json').read_text())
model={'textures':{'all':'celestialfall:item/heaven_sword','particle':'celestialfall:item/heaven_sword'},'elements':[
 element([5.6,8,7.3],[10.4,23,8.7],[0,0,8,8]),element([6.2,23,7.4],[9.8,27,8.6],[0,0,8,8]),
 element([7,27,7.5],[9,30,8.5],[8,0,16,8]),element([7.65,30,7.6],[8.35,31.5,8.4],[8,0,16,8]),
 element([5.45,8,7.15],[5.85,23,8.85],[8,0,16,8]),element([10.15,8,7.15],[10.55,23,8.85],[8,0,16,8]),
 element([3.8,6.8,7],[12.2,8.3,9],[0,8,8,16]),element([7.3,1.2,7.2],[8.7,6.8,8.8],[8,8,16,16]),
 element([6.5,0,6.9],[9.5,1.4,9.1],[0,8,8,16])], 'display':old['display']}
(a/'models/item/heaven_sword.json').write_text(json.dumps(model,indent=2)+'\n')
if '--synthetic-audio' not in sys.argv:
 print('Updated Heaven models/textures. Reference audio retained; use prepare_heaven_audio.py to rebuild it.')
 sys.exit(0)
rate=24000;rng=np.random.default_rng(1644)
def write(name,signal):
 signal=np.asarray(signal);signal*=.72/max(1e-9,np.max(np.abs(signal)))
 signal[:240]*=np.linspace(0,1,240);signal[-1200:]*=np.linspace(1,0,1200)
 with tempfile.TemporaryDirectory() as temp:
  p=Path(temp)/'a.wav'
  with wave.open(str(p),'wb') as f:f.setnchannels(1);f.setsampwidth(2);f.setframerate(rate);f.writeframes((signal*32767).astype('<i2').tobytes())
  subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(p),'-c:a','libvorbis','-q:a','5',str(a/'sounds'/f'{name}.ogg')],check=True)
t=np.arange(int(rate*4.95))/rate;noise=rng.standard_normal(len(t))
charge=np.sin(2*np.pi*(320*t+90*t*t))*.09*(1-np.exp(-t*6))
for start,freq in [(.14,880),(.52,1320),(1.08,1760),(1.4,660)]:
 tt=np.maximum(0,t-start);charge+=(t>=start)*np.exp(-tt*4)*(np.sin(2*np.pi*freq*tt)+.35*np.sin(2*np.pi*freq*2.01*tt))*.24
swirl=np.convolve(noise,np.ones(35)/35,mode='same');charge+=swirl*.45*np.clip((t-1.35)/2.0,0,1)
charge+=np.sin(2*np.pi*(45*t+15*t*t))*.18*np.clip((t-1.35)/1.7,0,1)
write('heaven_charge',charge)
t=np.arange(int(rate*7.0))/rate;noise=rng.standard_normal(len(t));low=np.convolve(noise,np.ones(160)/160,mode='same')*6
impact=noise*np.exp(-t*17)*.45+low*np.exp(-t*.95)+np.sin(2*np.pi*(42*t+26*(1-np.exp(-t*5))))*np.exp(-t*1.5)*.8
# Contact is at 5.0s; later pulses match the 24/48/80-tick pressure bursts.
rumble=np.convolve(noise,np.ones(220)/220,mode='same')*5
impact+=rumble*.55*np.clip(t/.12,0,1)*np.clip((5.5-t)/1.7,0,1)
for start in [1.2,2.4,4.0]:
 tt=np.maximum(t-start,0);impact+=(t>=start)*np.exp(-tt*4)*np.sin(2*np.pi*52*tt)*(.52 if start==4.0 else .32)
impact+=np.sin(2*np.pi*980*t)*np.exp(-t*2.5)*.08
write('heaven_impact',impact)
sounds=json.loads((a/'sounds.json').read_text())
for name in ['heaven_charge','heaven_impact']:sounds[name]={'subtitle':'subtitles.celestialfall.'+name,'sounds':['celestialfall:'+name]}
(a/'sounds.json').write_text(json.dumps(sounds,ensure_ascii=False,indent=2)+'\n')
print('Generated heaven sword, blue/white costume, two-finger glove and two original sounds.')
