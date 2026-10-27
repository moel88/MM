"""Original code-generated item/model textures and synthesized non-vocal sound effects."""
from pathlib import Path
import json,subprocess,tempfile,wave
import numpy as np
from PIL import Image,ImageDraw
root=Path(__file__).resolve().parents[1]
a=root/'src/main/resources/assets/celestialfall'
(a/'textures/entity').mkdir(parents=True,exist_ok=True)
# Player-layout fabric: dark violet coat, original gold piping and buckles.
im=Image.new('RGBA',(64,64),(19,15,29,255));d=ImageDraw.Draw(im)
for y in range(64):
 for x in range(64):
  if (x+y)%7==0:d.point((x,y),fill=(27,21,39,255))
d.rectangle((20,20,27,31),fill=(28,21,42,255))
for x in (20,27):d.line((x,20,x,31),fill=(189,151,87,255),width=1)
d.line((23,20,23,31),fill=(96,61,131,255),width=1)
d.rectangle((20,29,27,30),fill=(153,122,77,255))
for x,y in ((44,20),(36,52),(4,20),(20,52)):
 d.rectangle((x,y+3,x+3,y+4),fill=(180,140,81,255))
im.save(a/'textures/entity/atomic_costume.png')
im=Image.new('RGBA',(64,64),(28,20,42,255));d=ImageDraw.Draw(im)
d.rectangle((32,0,63,63),fill=(205,126,255,255));d.rectangle((0,0,8,5),fill=(78,53,98,255))
im.save(a/'textures/entity/atomic_blade.png')
# Item texture atlas: core, purple edge and hilt cells.
im=Image.new('RGBA',(16,16),(23,16,33,255));d=ImageDraw.Draw(im)
d.rectangle((8,0,15,7),fill=(183,97,244,255));d.rectangle((0,8,7,15),fill=(146,118,79,255));d.rectangle((8,8,15,15),fill=(62,42,79,255))
(a/'textures/item').mkdir(exist_ok=True);im.save(a/'textures/item/atomic_sword.png')
def element(lo,hi,uv):return {'from':lo,'to':hi,'faces':{f:{'uv':uv,'texture':'#all'} for f in ('north','south','east','west','up','down')}}
model={'textures':{'all':'celestialfall:item/atomic_sword','particle':'celestialfall:item/atomic_sword'},'elements':[
 element([7.35,8,7.7],[8.65,29,8.3],[0,0,8,8]),element([8.55,8,7.65],[8.85,29,8.35],[8,0,16,8]),
 element([7.65,29,7.75],[8.35,31,8.25],[8,0,16,8]),element([4.5,7,7.45],[11.5,8,8.55],[0,8,8,16]),
 element([7.35,2,7.45],[8.65,7,8.55],[8,8,16,16]),element([7.1,1,7.25],[8.9,2,8.75],[0,8,8,16])],
 'display':{'thirdperson_righthand':{'rotation':[0,-90,0],'translation':[0,1,-1],'scale':[.8,.8,.8]},
 'thirdperson_lefthand':{'rotation':[0,90,0],'translation':[0,1,-1],'scale':[.8,.8,.8]},
 'firstperson_righthand':{'rotation':[0,-90,25],'translation':[1,0,0],'scale':[.7,.7,.7]},
 'firstperson_lefthand':{'rotation':[0,90,-25],'translation':[1,0,0],'scale':[.7,.7,.7]},
 'gui':{'rotation':[0,0,-45],'translation':[-2,-3,0],'scale':[.6,.6,.6]},
 'ground':{'translation':[0,2,0],'scale':[.35,.35,.35]},'fixed':{'scale':[.55,.55,.55]}}}
(a/'models/item/atomic_sword.json').write_text(json.dumps(model,indent=2)+'\n')
# Voice is deliberately not fabricated: subtitles carry the phrase; these are original tonal effects.
rate=24000;rng=np.random.default_rng(405)
def write(name,signal):
 signal=np.asarray(signal,dtype=np.float64);signal*=.74/max(1e-9,np.max(np.abs(signal)))
 signal[:480]*=np.linspace(0,1,480);signal[-1440:]*=np.linspace(1,0,1440)
 with tempfile.TemporaryDirectory() as temp:
  p=Path(temp)/'a.wav'
  with wave.open(str(p),'wb') as f:f.setnchannels(1);f.setsampwidth(2);f.setframerate(rate);f.writeframes((signal*32767).astype('<i2').tobytes())
  subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(p),'-c:a','libvorbis','-q:a','4',str(a/'sounds'/f'{name}.ogg')],check=True)
t=np.arange(int(rate*8.4))/rate
charge=(np.sin(2*np.pi*(48*t+1.6*t*t))*.30+np.sin(2*np.pi*73*t)*.13)*(t/8.4)**.7
charge+=np.sin(2*np.pi*(210*t+21*t*t))*.06*(t/8.4)**3
for i in range(20):
 start=.5+i*.33;tt=np.maximum(t-start,0)
 charge+=(t>=start)*np.exp(-tt*8)*np.sin(2*np.pi*(330+i*63)*tt)*.05
charge+=np.convolve(rng.standard_normal(len(t)),np.ones(30)/30,mode='same')*.15*(t/8.4)
write('atomic_charge',charge)
t=np.arange(rate*10)/rate;noise=rng.standard_normal(len(t));low=np.convolve(noise,np.ones(220)/220,mode='same')*6
impact=np.sin(2*np.pi*(34*t+17*(1-np.exp(-t*8))))*np.exp(-t*.9)*.85
impact+=noise*np.exp(-t*19)*.6+low*np.exp(-t*.4)
impact+=np.sin(2*np.pi*(76*t+60*(1-np.exp(-t*3))))*np.exp(-t*2)*.24
for delay in (.45,1.1,1.9):
 tt=np.maximum(t-delay,0);impact+=(t>=delay)*np.exp(-tt*2.4)*np.sin(2*np.pi*42*tt)*.16
write('atomic_impact',impact)
print('Generated atomic sword model, two rig textures and two non-vocal sound effects.')
