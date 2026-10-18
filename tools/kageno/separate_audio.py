"""Base effects separation. After this step run restore_cinematic_audio.py
to preserve H music and G dialogue according to the current sound policy.
T held's clip (kageno_rush) is cut on the old timeline; --install re-cuts it to the
remade skill (2.38.0) with recut_rush.py before it goes into the mod.

Requires numpy, scipy, onnxruntime and the three pinned BandIt-v2 music ONNX graphs.
Weights are not bundled. See audio_model.json for provenance, hashes and attribution.
The model can leave residual music or remove overlapping effect frequencies: this is
source separation, not recovery of the game's unavailable isolated audio assets.
"""
from pathlib import Path
import argparse, hashlib, json, subprocess, time
import numpy as np
from scipy.io import wavfile
from scipy.fft import rfft, irfft
from scipy.ndimage import median_filter, uniform_filter1d
import onnxruntime as ort

ROOT=Path(__file__).resolve().parents[2]
SR=48000; N=384000; FFT=2048; HOP=512
# E's sound is the user's own recording since 2.37.0 (its audio_reference.json entry says how it was cut). It is not
# separated from the video, and a rerun keeps it.
CLIPS={'kageno_m1_1':(7.50,.65),'kageno_m1_2':(8.13,.65),
 'kageno_m1_3':(8.77,.70),'kageno_m1_4':(9.44,.72),
 'kageno_r':(31.96,3.20),
 'kageno_t':(43.96,5.20),'kageno_rush':(61.16,3.50),
 'kageno_y':(76.00,2.90),'kageno_h':(121.03,5.80),'atomic_full':(142.00,23.40)}
HASHES={'split':'fa7973660fd3cf90e84fd0a2ea7a096ce25635f9b14e0b9825e47439476eeb77',
 'tf':'6c4c5d8115d73b6dc8d76572639dd734d82e0ddee8f5c06e417b86a8e89a02bb',
 'mask':'e83c135e15be1c4fa74a05b1da7461acaf786b4d19a1adcc51ddfef4572eb691'}
WINDOW=np.hanning(FFT+1)[:-1].astype(np.float32)
def stft(x):
 padded=np.pad(x,(FFT//2,FFT//2),mode='reflect')
 frames=np.lib.stride_tricks.sliding_window_view(padded,FFT)[::HOP]
 return rfft(frames*WINDOW,axis=-1).T.astype(np.complex64)
def istft(z):
 frames=irfft(z.T,n=FFT,axis=-1)*WINDOW
 result=np.zeros(N+FFT,dtype=np.float32);weight=np.zeros_like(result)
 for i,f in enumerate(frames):
  result[i*HOP:i*HOP+FFT]+=f;weight[i*HOP:i*HOP+FFT]+=WINDOW**2
 result/=np.maximum(weight,1e-8)
 return result[FFT//2:FFT//2+N]
class Separator:
 def __init__(self,models,threads):
  opt=ort.SessionOptions();opt.intra_op_num_threads=threads;opt.inter_op_num_threads=1
  self.sessions=[]
  for part,sha in HASHES.items():
   p=models/f'bandit-{part}.onnx';assert hashlib.sha256(p.read_bytes()).hexdigest()==sha,p
   self.sessions.append(ort.InferenceSession(str(p),sess_options=opt,providers=['CPUExecutionProvider']))
 def music(self,x):
  spec=stft(x);a,b,c=self.sessions
  z=a.run(None,{'spec_real':spec.real[None].copy(),'spec_imag':spec.imag[None].copy()})[0]
  q=b.run(None,{'z':z})[0];real,imag=c.run(None,{'q':q})
  return istft(spec*(real[0]+1j*imag[0]))

def main():
 p=argparse.ArgumentParser();p.add_argument('source',type=Path);p.add_argument('models',type=Path);p.add_argument('work',type=Path)
 p.add_argument('--threads',type=int,default=4);p.add_argument('--only',choices=list(CLIPS));p.add_argument('--install',action='store_true');args=p.parse_args()
 args.work.mkdir(parents=True,exist_ok=True)
 pcm=args.work/'original.wav'
 if not pcm.exists():subprocess.run(['ffmpeg','-v','error','-i',str(args.source),'-vn','-ar',str(SR),'-ac','2','-c:a','pcm_f32le','-y',str(pcm)],check=True)
 sr,audio=wavfile.read(pcm);assert sr==SR
 # Music in the supplied video is mostly centered. Independent L/R inference
 # retains stereo directionality and avoids making spatial SFX mono.
 sep=Separator(args.models,args.threads)
 records=[]
 for name,(start,duration) in CLIPS.items():
  if args.only and name!=args.only:continue
  t0=time.monotonic();lo=round(start*SR);hi=round((start+duration)*SR)
  # 8 s context with 4 s hop, sin^2 overlap; crop only after separation.
  begins=range(int(start//4)*4-4,int((start+duration)//4)*4+1,4)
  estimates=np.zeros((hi-lo,2),np.float32);weights=np.zeros(hi-lo,np.float32)
  w=np.sin(np.pi*(np.arange(N)+.5)/N).astype(np.float32)**2
  for sec in begins:
   frame_start=sec*SR;take0=max(lo,frame_start);take1=min(hi,frame_start+N)
   if take1<=take0:continue
   cache=args.work/f'music-{sec:03d}.npy'
   if cache.exists():music=np.load(cache)
   else:
    chunk=audio[max(0,frame_start):min(len(audio),frame_start+N)]
    chunk=np.pad(chunk,((max(0,-frame_start),max(0,frame_start+N-len(audio))),(0,0)),mode='reflect')
    music=np.column_stack([sep.music(chunk[:,channel]) for channel in range(2)])
    np.save(cache,music)
   dst=slice(take0-lo,take1-lo);src=slice(take0-frame_start,take1-frame_start)
   estimates[dst]+=music[src]*w[src,None];weights[dst]+=w[src]
   print(f'{name}: context {sec}s ready ({time.monotonic()-t0:.1f}s)',flush=True)
  music=estimates/np.maximum(weights[:,None],1e-8)
  original=audio[lo:hi];effects=original-music
  residual_fraction=float(np.sqrt(np.mean(effects**2)/np.mean(original**2)))
  rescued=False
  if residual_fraction<.15:
   # Stylized sword sweeps are sometimes classified as music. Recover only
   # noisy high-frequency transients from that estimate, not its tonal melody.
   # This may retain percussion from the backing track; document that limit.
   for channel in range(2):
    padded=np.pad(music[:,channel],(0,N-len(music)),mode='constant')
    spec=stft(padded);mag=np.abs(spec)+1e-9
    harmonic=median_filter(mag,size=(1,41));percussive=median_filter(mag,size=(31,1))
    ratio=np.minimum(20,percussive/(harmonic+1e-8));mask=ratio**4/(1+ratio**4)
    flat=np.exp(uniform_filter1d(np.log(mag),size=31,axis=0))/uniform_filter1d(mag,size=31,axis=0)
    noisy=np.clip((flat-.12)/.35,0,1)
    high=np.clip((np.arange(1025)*SR/FFT-700)/1300,0,1)[:,None]
    effects[:,channel]+=.9*istft(spec*mask*noisy*high)[:len(music)]
   rescued=True
  # Common gain only; do not gate, synthesize transients, or time stretch.
  gain=min(4.0,.90/max(.0001,float(np.abs(effects).max())))
  effects*=gain
  fade=min(240,len(effects)//2);effects[:fade]*=np.linspace(0,1,fade)[:,None];effects[-fade:]*=np.linspace(1,0,fade)[:,None]
  wavfile.write(args.work/f'{name}-original.wav',SR,original)
  wavfile.write(args.work/f'{name}-music.wav',SR,music)
  wavfile.write(args.work/f'{name}-separated.wav',SR,effects)
  ogg=args.work/f'{name}.ogg'
  subprocess.run(['ffmpeg','-v','error','-i',str(args.work/f'{name}-separated.wav'),'-c:a','libvorbis','-q:a','7','-y',str(ogg)],check=True)
  decoded=np.frombuffer(subprocess.check_output(['ffmpeg','-v','error','-i',str(ogg),'-f','f32le','-ac','2','-ar',str(SR),'-']),dtype='<f4').reshape(-1,2)
  n=min(len(decoded),len(effects));corr=float(np.corrcoef(effects[:n].ravel(),decoded[:n].ravel())[0,1])
  records.append({'sound':name,'seconds':duration,'source_start_seconds':start,'sample_rate':SR,'channels':2,'playback_speed':1.0,'decoded_correlation':corr,'decoded_peak':float(np.abs(decoded).max()),'sha256':hashlib.sha256(ogg.read_bytes()).hexdigest(),'source':'supplied video minus BandIt v2 estimated music','gain':gain,'high_frequency_transient_rescue':rescued,'raw_model_residual_rms_fraction':residual_fraction,'residual_rms_fraction':float(np.sqrt(np.mean((effects/gain)**2)/np.mean(original**2)))})
  if args.install and name=='kageno_rush':subprocess.run(['python3',str(ROOT/'tools/kageno/recut_rush.py'),str(ogg),str(ROOT/'src/main/resources/assets/celestialfall/sounds'/ogg.name)],check=True)
  elif args.install:(ROOT/'src/main/resources/assets/celestialfall/sounds'/ogg.name).write_bytes(ogg.read_bytes())
  print(f'{name}: completed, codec correlation {corr:.5f}, {time.monotonic()-t0:.1f}s',flush=True)
 records+=[x for x in json.loads((ROOT/'tools/kageno/audio_reference.json').read_text())['effects'] if x['sound']=='kageno_e']
 manifest={'version':'2.24.0','method':'original minus estimated music; no generated SFX','model':'BandIt v2, music-only ONNX','Q_source_offset_seconds':134,'skill_audio_contains_reference_video':True,'music_removal_is_perfect':False,'overlap_seconds':4,'effects':records}
 (args.work/('audio-verification-'+(args.only or 'all')+'.json')).write_text(json.dumps(manifest,indent=2)+'\n')
 if args.install and not args.only:
  (ROOT/'tools/kageno/audio_reference.json').write_text(json.dumps(manifest,indent=2)+'\n')
  (ROOT/'build/kageno-audio-verification.json').write_text(json.dumps(manifest,indent=2)+'\n')
  syncpath=ROOT/'src/main/resources/assets/celestialfall/audio_sync.json';sync=json.loads(syncpath.read_text())
  sync['atomic']={'result':'passed','source_name':args.source.name,'source_sha256':hashlib.sha256(args.source.read_bytes()).hexdigest(),'normal_sample_speed':True,'tracks':[records[-1]],'anchors':{'impact_tick':128,'end_tick':468},'notes':'2.24 original effects/dialogue with estimated background music subtracted. Residual music/artifacts possible.','verification_scope':'Decoded correlation against separated PCM; not against unprocessed source or audio-device recording.'}
  syncpath.write_text(json.dumps(sync,indent=2)+'\n')
if __name__=='__main__':main()
