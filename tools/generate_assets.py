"""Regenerate this project's original procedural graphics/audio. Requires Pillow, NumPy, ffmpeg."""
from pathlib import Path
import math
import tempfile
import subprocess
import wave
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/celestialfall'
(ASSETS / 'textures/item').mkdir(parents=True, exist_ok=True)
(ASSETS / 'sounds').mkdir(parents=True, exist_ok=True)

# Original violet-and-gold star seal, drawn at high resolution then pixel-scaled.
im = Image.new('RGBA', (64,64))
d = ImageDraw.Draw(im)
d.ellipse((8,8,55,55),fill=(28,18,55,255),outline=(226,193,128,255),width=3)
d.ellipse((13,13,50,50),outline=(171,129,243,255),width=1)
pts=[(32+18*math.cos(-math.pi/2+i*math.tau/5),32+18*math.sin(-math.pi/2+i*math.tau/5)) for i in range(5)]
for i in range(5):d.line([pts[i],pts[(i+2)%5]],fill=(202,172,255,255),width=2)
for x,y in pts:d.ellipse((x-2,y-2,x+2,y+2),fill=(246,237,255,255))
d.polygon([(32,23),(35,31),(32,41),(29,31)],fill=(198,238,255,255))
im.resize((32,32),Image.Resampling.NEAREST).save(ASSETS/'textures/item/astral_sigil.png')
icon=Image.new('RGBA',(128,128),(10,7,25,255))
halo=Image.new('RGBA',(128,128));halo.alpha_composite(im.resize((112,112),Image.Resampling.NEAREST),(8,8))
icon.alpha_composite(halo.filter(ImageFilter.GaussianBlur(7)))
icon.alpha_composite(im.resize((96,96),Image.Resampling.NEAREST),(16,16))
icon.save(ASSETS/'icon.png')

RATE=24000
rng=np.random.default_rng(7319)
def tone(f,t):return np.sin(2*np.pi*f*t)
def write(name,a):
    # Mono spatial audio with conservative peak level; no downloaded recordings.
    a=np.asarray(a,dtype=np.float64)
    a=a/max(1e-9,np.max(np.abs(a)))*0.72
    fade=min(720,len(a)//8)
    a[:fade]*=np.linspace(0,1,fade);a[-fade:]*=np.linspace(1,0,fade)
    with tempfile.TemporaryDirectory() as temp:
        src=Path(temp)/'sound.wav'
        with wave.open(str(src),'wb') as w:
            w.setnchannels(1);w.setsampwidth(2);w.setframerate(RATE);w.writeframes((a*32767).astype('<i2').tobytes())
        subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(src),'-c:a','libvorbis','-q:a','4',str(ASSETS/'sounds'/f'{name}.ogg')],check=True)

t=np.arange(RATE*7)/RATE
charge=(tone(110,t)*0.18+tone(165,t)*0.09+tone(220,t)*0.08)*(0.15+0.75*t/7)
for start,f in [(0.1,440),(0.65,554.37),(1.2,659.25),(2,880),(2.8,1108.7),(3.6,1318.5),(4.6,1760),(5.4,2217.46)]:
    tt=np.maximum(0,t-start)
    env=(t>=start)*np.exp(-tt*1.9)*np.minimum(1,tt*40)
    charge+=(tone(f,tt)+0.25*tone(f*2.002,tt))*env*0.35
charge+=np.sin(2*np.pi*(240*t+35*t*t))*0.07*(t/7)**3
write('charge',charge)
t=np.arange(int(RATE*2.5))/RATE
noise=rng.standard_normal(len(t));noise=np.convolve(noise,np.ones(7)/7,mode='same')
fall=(np.sin(2*np.pi*(190*t+100*t*t))+0.2*tone(75,t))*0.2+noise*0.6
fall*=0.08+(t/2.5)**2
write('fall',fall)
t=np.arange(RATE*10)/RATE
noise=rng.standard_normal(len(t))
rumble=np.convolve(noise,np.ones(150)/150,mode='same')*5
impact=0.85*np.sin(2*np.pi*(45*t+18*(1-np.exp(-t*7))))*np.exp(-t*1.15)
impact+=rumble*np.exp(-t*.40)+noise*np.exp(-t*16)*0.42
for f in [220,329.63,440,659.25]:impact+=tone(f,t)*np.exp(-t*2.2)*0.08
impact+=tone(32,t)*np.exp(-t*.85)*.2
write('impact',impact)
# Short, deep constellation collision with metallic shimmer, played at three synchronized beats.
t=np.arange(int(RATE*1.8))/RATE
noise=rng.standard_normal(len(t))
low=np.convolve(noise,np.ones(120)/120,mode='same')*4
clash=np.sin(2*np.pi*(43*t+8*(1-np.exp(-t*12))))*np.exp(-t*3.3)
clash+=low*np.exp(-t*2.8)*.55+noise*np.exp(-t*25)*.16
for f in [110,164.8,247]:clash+=tone(f,t)*np.exp(-t*4.3)*.10
write('clash',clash)
print('Generated sigil, mod icon, charge, fall, impact, and clash sounds.')

# Air-cut accent for the title/blade reveal, plus a separate short aftershock impact.
t=np.arange(int(RATE*1.2))/RATE
noise=rng.standard_normal(len(t));soft=np.convolve(noise,np.ones(12)/12,mode='same')
reveal=soft*np.exp(-((t-.14)/.085)**2)*1.7
reveal+=np.sin(2*np.pi*(110*t+270*t*t))*np.exp(-t*5)*.18
write('reveal',reveal)
t=np.arange(int(RATE*2.2))/RATE
noise=rng.standard_normal(len(t));low=np.convolve(noise,np.ones(180)/180,mode='same')*6
aftershock_wave=low*np.exp(-t*1.9)+np.sin(2*np.pi*(38*t+4*(1-np.exp(-t*10))))*np.exp(-t*2.8)*.65
aftershock_wave+=noise*np.exp(-t*26)*.2
write('aftershock',aftershock_wave)
