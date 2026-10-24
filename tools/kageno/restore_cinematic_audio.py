"""Preserve H's original musical cue and G's complete spoken opening at 1x.
Run after separate_audio.py. Remaining effects keep their separated source tracks.
"""
from pathlib import Path
import sys,json,hashlib,subprocess
import numpy as np
from scipy.io import wavfile
ROOT=Path(__file__).resolve().parents[2];SR=48000
source=Path(sys.argv[1]);work=Path(sys.argv[2]);work.mkdir(parents=True,exist_ok=True)
assets=ROOT/'src/main/resources/assets/celestialfall'
manifest=json.loads((ROOT/'tools/kageno/audio_reference.json').read_text())
records={x['sound']:x for x in manifest['effects']}
def decode(p):return np.frombuffer(subprocess.check_output(['ffmpeg','-v','error','-i',str(p),'-ac','2','-ar',str(SR),'-f','f32le','-']),'<f4').reshape(-1,2)
original=decode(source)
for name,start,duration in [('kageno_h',121.03,5.8),('atomic_full',142.,23.4)]:
 raw=original[round(start*SR):round((start+duration)*SR)].copy()
 if name=='kageno_h':
  mix=raw.copy();policy='Unseparated original H music and effects; uninterrupted at 1x'
 else:
  # Use the separator's cached PCM, never a previously patched OGG.
  sep=Path(sys.argv[3])/'atomic_full-separated.wav';rate,tail=wavfile.read(sep);assert rate==SR
  gain=records[name].get('separation_gain',records[name]['gain'])
  tail=tail/gain
  # Source dialogue (I / am / Atomic), breath and onset all remain intact.
  blend=np.clip((np.arange(len(raw))/SR-6.4)/.2,0,1)[:,None]
  mix=raw*(1-blend)+tail*blend
  policy='Original unseparated spoken opening 0-6.4s, 0.2s crossfade into separated explosion; no dialogue replacement'
 gain=min(1.,.88/max(.0001,float(np.abs(mix).max())));mix*=gain
 fade=144;mix[:fade]*=np.linspace(0,1,fade)[:,None];mix[-fade:]*=np.linspace(1,0,fade)[:,None]
 wav=work/f'{name}-protected.wav';wavfile.write(wav,SR,mix.astype(np.float32))
 ogg=assets/'sounds'/f'{name}.ogg'
 subprocess.run(['ffmpeg','-v','error','-i',str(wav),'-c:a','libvorbis','-q:a','7','-y',str(ogg)],check=True)
 decoded=decode(ogg);assert len(decoded)==len(mix)
 n=round((5.79 if name=='kageno_h' else 6.39)*SR);lo=round(.01*SR)
 source_corr=float(np.corrcoef(raw[lo:n].ravel(),decoded[lo:n].ravel())[0,1])
 corr=float(np.corrcoef(mix.ravel(),decoded.ravel())[0,1]);peak=float(np.abs(decoded).max())
 assert source_corr>.985 and corr>.985 and peak<1,(name,source_corr,corr,peak)
 old=records[name]
 records[name]={'sound':name,'seconds':duration,'source_start_seconds':start,'sample_rate':SR,'channels':2,'playback_speed':1.,'decoded_correlation':corr,'decoded_peak':peak,'sha256':hashlib.sha256(ogg.read_bytes()).hexdigest(),'source':policy,'gain':gain,'separation_gain':old.get('separation_gain',old['gain']),'protected_source_seconds':[start+.01,start+n/SR],'protected_source_correlation':source_corr,'music_intentionally_preserved':name=='kageno_h','source_sha256':hashlib.sha256(source.read_bytes()).hexdigest()}
 print(name,'source correlation',round(source_corr,6),'peak',round(peak,4),flush=True)
manifest.update(version='2.25.0',method='Original separated SFX, with H full music preserved and G spoken opening protected',effects=list(records.values()))
for p in [ROOT/'tools/kageno/audio_reference.json',ROOT/'build/kageno-audio-verification.json',work/'cinematic-audio-verification.json']:p.write_text(json.dumps(manifest,indent=2)+'\n')
p=assets/'audio_sync.json';sync=json.loads(p.read_text());sync['atomic']['tracks']=[records['atomic_full']];sync['atomic']['notes']='2.25 original spoken I am Atomic opening preserved without separation; separated explosion tail at 1x';sync['atomic']['verification_scope']='Decoded OGG versus protected PCM plus original source correlation for complete spoken opening; not device audio recording.';p.write_text(json.dumps(sync,indent=2)+'\n')
