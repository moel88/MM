"""H-led reference review with matched native clock and uninterrupted Q/G take.
Audio is a preview of shipped assets, not a recording from an audio device.
"""
from pathlib import Path
import subprocess,re,datetime,json,sys
import numpy as np
from scipy.io import wavfile
ROOT=Path(__file__).resolve().parents[2]
ref=Path(sys.argv[1]);out=Path(sys.argv[2]);out.parent.mkdir(parents=True,exist_ok=True)
work=ROOT/'build/kageno-comparison';work.mkdir(exist_ok=True)
proof=Path((ROOT/'build/kageno-proof-path.txt').read_text().strip());atomic=Path((ROOT/'build/kageno-atomic-proof-path.txt').read_text().strip())
sounds=ROOT/'src/main/resources/assets/celestialfall/sounds'
def seconds(iso):return datetime.datetime.fromisoformat(iso.replace('Z','+00:00')).timestamp()
def phases(p):
 start=float((p/'video-start.txt').read_text());log=(p/'phase-times.txt').read_text()
 return {int(phase):seconds(iso)-start for phase,iso in re.findall(r'KAGENO QA phase (\d+) at ([0-9T:.Z-]+)',log)}
def pcm(path):
 return np.frombuffer(subprocess.check_output(['ffmpeg','-v','error','-i',str(path),'-f','f32le','-ar','48000','-ac','2','-']),dtype='<f4').reshape(-1,2)
def mix(name,duration,events):
 y=np.zeros((round(duration*48000),2),np.float32)
 for at,path in events:
  data=pcm(path);i=round(at*48000);n=min(len(data),len(y)-i);assert i>=0
  y[i:i+n]+=data[:n]
 y*=min(1,.92/max(.00001,float(np.abs(y).max())))
 path=work/(name+'.wav');wavfile.write(path,48000,y);return path
ph=phases(proof);ag=phases(atomic)
iso,age=re.findall(r'KAGENO QA H onset at ([0-9T:.Z-]+) age ([0-9.]+)',(proof/'phase-times.txt').read_text())[0]
hstart=seconds(iso)-float((proof/'video-start.txt').read_text())-float(age)*.05
qstart=ag[49]+.08;gstart=ag[50]+.08;lead=gstart-qstart;duration=lead+23.4
qg_audio=mix('continuous_q_g',duration,[(0,sounds/'kageno_theme.ogg'),(lead,sounds/'atomic_full.ogg')])
silent=mix('silent_motion_review',11.6,[])
qg_ref=work/'q_g_reference.mkv'
subprocess.run(['ffmpeg','-v','error','-ss','88','-i',str(ref),'-ss','142','-i',str(ref),'-filter_complex',f'[0:v]trim=duration={lead},setpts=PTS-STARTPTS,fps=30,scale=960:540[a];[1:v]trim=duration=23.4,setpts=PTS-STARTPTS,fps=30,scale=960:540[b];[a][b]concat=n=2:v=1:a=0[v]','-map','[v]','-an','-c:v','libx264','-preset','fast','-crf','20','-y',str(qg_ref)],check=True)
# name, reference file/start, output duration, native proof/start, sound, playback speed
clips=[
 ('H - HIGHEST / ORIGINAL SPEED',ref,121.03,5.8,proof,hstart,sounds/'kageno_h.ogg',1),
 ('H - CAMERA AND POSE / 0.5x',ref,121.03,11.6,proof,hstart,silent,.5),
 ('T - GROUND FLAMES',ref,43.96,5.2,proof,ph[11]+.12,sounds/'kageno_t.ogg',1),
 ('T HOLD - FIRE RUSH',ref,61.16,3.5,proof,ph[12]+.43,sounds/'kageno_rush.ogg',1),
 ('Q TO G - ONE CONTINUOUS TAKE',qg_ref,0,duration,atomic,qstart,qg_audio,1)]
font='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
for i,(name,sourcefile,source,duration,p,native,sound,speed) in enumerate(clips):
 caption='Shipped original SFX preview / reference muted / not an audio-device recording'
 if i==0:caption='H original music and effects preserved at 1x / shipped-asset audio preview'
 if i==1:caption='Half-speed visual review - muted / no generated or interpolated frames'
 if i==4:caption='Original G dialogue + uninterrupted Q theme / asset audio preview / right side is uncut'
 length=duration*speed
 graph=f'[0:v]trim=duration={length},setpts=(PTS-STARTPTS)/{speed},scale=960:540,setsar=1,fps=30[l];[1:v]trim=duration={length},setpts=(PTS-STARTPTS)/{speed},scale=960:540,setsar=1,fps=30[r];[l][r]hstack=inputs=2,pad=1920:606:0:42:color=0x10111b,drawtext=fontfile={font}:text=REFERENCE - {name}:x=18:y=12:fontsize=19:fontcolor=0xd6b5ff,drawtext=fontfile={font}:text=MINECRAFT 2.26.0 - NATIVE CAPTURE:x=978:y=12:fontsize=20:fontcolor=white,drawtext=fontfile={font}:text={caption}:x=18:y=587:fontsize=14:fontcolor=0xbcbccc[v]'
 subprocess.run(['ffmpeg','-xerror','-v','error','-ss',str(source),'-i',str(sourcefile),'-ss',str(native),'-i',str(p/'native-session.mp4'),'-i',str(sound),'-t',str(duration),'-filter_complex',graph,'-map','[v]','-map','2:a','-af','apad,afade=t=in:d=0.01,afade=t=out:st='+str(duration-.03)+':d=0.03','-c:v','libx264','-preset','fast','-crf','20','-c:a','aac','-b:a','192k','-pix_fmt','yuv420p','-y',str(work/f'{i}.mkv')],check=True)
 print(name,flush=True)
for i,(_,_,_,duration,_,_,_,_) in enumerate(clips):
 meta=json.loads(subprocess.check_output(['ffprobe','-v','error','-select_streams','v:0','-count_frames','-show_entries','stream=nb_read_frames','-of','json',str(work/f'{i}.mkv')]))
 assert abs(int(meta['streams'][0]['nb_read_frames'])-round(duration*30))<=2,'Incomplete comparison segment'
(work/'concat.txt').write_text(''.join(f"file '{work/str(i)}.mkv'\n" for i in range(len(clips))))
subprocess.run(['ffmpeg','-xerror','-v','error','-f','concat','-safe','0','-i',str(work/'concat.txt'),'-c','copy','-movflags','+faststart','-y',str(out)],check=True)
(work/'alignment.json').write_text(json.dumps([{'skill':name,'reference_start':source,'duration':duration,'playback_speed':speed,'native_start':native,'native_proof':str(p),'audio_preview_asset':str(sound),'audio_device_recorded':False,'native_Q_G_uncut':i==len(clips)-1,'H_uses_recorded_cast_clock':i<2} for i,(name,_,source,duration,p,native,sound,speed) in enumerate(clips)],indent=2))
meta=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_entries','format=duration','-of','json',str(out)]))
assert abs(float(meta['format']['duration'])-sum(c[3] for c in clips))<.3,'Incomplete comparison output'
print(out)
