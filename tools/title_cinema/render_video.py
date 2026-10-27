from pathlib import Path
import sys,subprocess,json,wave,os
import numpy as np
import render_pc as r
DURATION=23;FPS=30;W=1280;H=720;OUT=r.OUT
# Original timed Foley: exact cue sheet and gains used by CinemaTimeline/CinemaSound.
SR=44100;mix=np.zeros((DURATION*SR,2),dtype=float)
def read(name):
 with wave.open(str(OUT/'audio'/(name+'.wav')),'rb') as f:return np.frombuffer(f.readframes(f.getnframes()),'<i2').reshape(-1,2)/32768
data=json.loads((r.RES/'cinema/performance.json').read_text());amb=read('ambience')
for start in range(0,len(mix),len(amb)):mix[start:min(start+len(amb),len(mix))]+=amb[:min(len(amb),len(mix)-start)]*data['ambience_gain']
events=list(data['events'])
for cycle in range(3):
 for e in data['loop_events']:events.append(dict(e,time=7+12*cycle+e['time']))
for e in events:
 start=round(e['time']*SR)
 if start>=len(mix):continue
 x=read(e['sound'].replace('cinema_',''));end=min(len(mix),start+len(x));mix[start:end]+=x[:end-start]*e['volume']
# Decode the very same shipped streaming asset; start on the shared turn cue.
music=data['music'];start=round(music['start_seconds']*SR)
raw=subprocess.check_output(['ffmpeg','-v','error','-i',str(r.RES/'sounds/cinema/title_satellites.ogg'),'-t',str(DURATION-music['start_seconds']),'-ar',str(SR),'-ac','2','-f','f32le','pipe:1'])
song=np.frombuffer(raw,'<f4').reshape(-1,2)
end=min(len(mix),start+len(song));mix[start:end]+=song[:end-start]*music['gain']
# Unity master/category/interface gains; no music speed, pitch or timing changes.
assert np.max(abs(mix))<.98, 'Review audio would clip'
with wave.open(str(OUT/'cinema-review-audio.wav'),'wb') as f:f.setnchannels(2);f.setsampwidth(2);f.setframerate(SR);f.writeframes((mix*32767).astype('<i2').tobytes())
out=OUT/'Botand_Main_Menu_2.13.2.mp4'
p=subprocess.Popen(['ffmpeg','-v','error','-y','-f','rawvideo','-pix_fmt','rgb24','-s',f'{W}x{H}','-r',str(FPS),'-i','-','-i',str(OUT/'cinema-review-audio.wav'),'-c:v','libx264','-preset','fast','-crf','19','-pix_fmt','yuv420p','-c:a','aac','-b:a','160k','-shortest','-movflags','+faststart',str(out)],stdin=subprocess.PIPE)
for frame in range(DURATION*FPS):
 im=r.render(frame/FPS,W,H);p.stdin.write(im.tobytes())
 if frame%150==0:print('Rendered',frame,'/',DURATION*FPS,flush=True)
p.stdin.close();assert p.wait()==0
with out.open('rb') as f:os.fsync(f.fileno())
print(out)
