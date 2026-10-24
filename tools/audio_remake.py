"""Rebuild the user-supplied stereo mixes at 1.0x sample speed.
Procedural voice-free ambience extends Heaven's slow visual insertion; speech,
fall, contact and final impact samples are never stretched or pitch-shifted.
"""
from pathlib import Path
import hashlib, json, subprocess, tempfile, wave
import numpy as np
from scipy.signal import butter, sosfiltfilt, resample_poly, correlate
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/celestialfall'
OUT=ROOT/'build/audio-remake';OUT.mkdir(parents=True,exist_ok=True)
RATE=48000

def decode(path):
    return np.frombuffer(subprocess.check_output(['ffmpeg','-v','error','-i',str(path),'-map','0:a:0','-vn','-ac','2','-ar',str(RATE),'-f','f32le','-']),dtype='<f4').copy().reshape(-1,2)
def cut(pcm,start,end):return pcm[round(start*RATE):round(end*RATE)].copy()
def fade(pcm,first=.003,last=.003):
    a=pcm.copy()
    for size,front in [(min(len(a),round(first*RATE)),True),(min(len(a),round(last*RATE)),False)]:
        if size:
            ramp=np.linspace(0,1,size,dtype=np.float32)[:,None]
            if front:a[:size]*=ramp
            else:a[-size:]*=ramp[::-1]
    return a

def procedural_ambience(seconds,kind,seed):
    """Generated noise only: deliberately accepts no input/source audio."""
    rng=np.random.default_rng(seed);n=round(seconds*RATE);t=np.arange(n)/RATE
    noise=rng.normal(size=(n,2))
    if kind=='wind':
        low,high,rms=90,1800,.055
        envelope=.75+.15*np.sin(t*1.7)+.10*np.sin(t*3.1+.7)
    else:
        low,high,rms=28,430,.095
        envelope=.80+.12*np.sin(t*2.3+.4)+.08*np.sin(t*4.7)
    a=sosfiltfilt(butter(3,[low,high],btype='bandpass',fs=RATE,output='sos'),noise,axis=0)
    a*=rms/np.sqrt(np.mean(a*a));a*=envelope[:,None]
    return a.astype(np.float32)

def chant_repeat_check(source,mix):
    # The broken 2.3.0 mix scores 0.999 here: it reused the spoken 2.0-2.3s samples.
    def fingerprint(a):
        down=resample_poly(a.mean(axis=1),1,6)
        return sosfiltfilt(butter(3,[200,1300],btype='bandpass',fs=8000,output='sos'),down)
    voice=fingerprint(source);after_chant=fingerprint(mix)[round(3.3*8000):]
    checks=[]
    for start,end in [(1.9,2.2),(2.0,2.3),(2.15,2.45)]:
        key=voice[round(start*8000):round(end*8000)].copy();key-=key.mean()
        c=correlate(after_chant,key,mode='valid',method='fft')
        energy=np.convolve(after_chant*after_chant,np.ones(len(key)),mode='valid')
        c/=np.sqrt(np.maximum(energy,1e-15)*np.sum(key*key))
        score=float(np.max(np.abs(c)));assert score<.35,('Chant repeats after initial playback',start,score)
        checks.append({'source_seconds':[start,end],'maximum_repeat_correlation_after_3_3s':score})
    return checks

def place(dest,pcm,start):
    i=round(start*RATE);n=min(len(pcm),len(dest)-i);assert i>=0 and n>0
    dest[i:i+n]+=pcm[:n]

def wav(path,pcm):
    with wave.open(str(path),'wb') as w:
        w.setnchannels(2);w.setsampwidth(2);w.setframerate(RATE)
        w.writeframes((np.clip(pcm,-1,1)*32767).astype('<i2').tobytes())

def export(name,pcm):
    dest=ASSETS/'sounds'/f'{name}.ogg'
    with tempfile.TemporaryDirectory() as tmp:
        path=Path(tmp)/'mix.wav';wav(path,pcm)
        subprocess.run(['ffmpeg','-v','error','-y','-i',str(path),'-c:a','libvorbis','-q:a','7',str(dest)],check=True)
    decoded=decode(dest);assert len(decoded)==len(pcm),(name,len(decoded),len(pcm))
    corr=float(np.corrcoef(decoded.ravel(),pcm.ravel())[0,1])
    assert np.isfinite(decoded).all() and np.max(np.abs(decoded))<1 and corr>.97,(name,corr)
    return {'sound':name,'seconds':len(pcm)/RATE,'sample_rate':RATE,'channels':2,'playback_speed':1.0,
        'decoded_correlation':corr,'decoded_peak':float(np.max(np.abs(decoded))),'sha256':hashlib.sha256(dest.read_bytes()).hexdigest()}

def metadata(source,kind,tracks,anchors,notes):
    sounds=json.loads((ASSETS/'sounds.json').read_text())
    for track in tracks:
        name=track['sound'];sounds[name]={'subtitle':'subtitles.celestialfall.'+name,'sounds':[{'name':'celestialfall:'+name,'stream':False,'preload':True}]}
    (ASSETS/'sounds.json').write_text(json.dumps(sounds,ensure_ascii=False,indent=2)+'\n')
    result={'result':'passed','source_name':source.name,'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),
      'normal_sample_speed':True,'tracks':tracks,'anchors':anchors,'notes':notes,
      'verification_scope':'Decoded sample/length checks and source-frame anchors; not in-game audio-device latency measurement.'}
    (OUT/(kind+'.json')).write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
    manifest=ASSETS/'audio_sync.json'
    existing=json.loads(manifest.read_text()) if manifest.exists() else {}
    existing[kind]=result;manifest.write_text(json.dumps(existing,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps(result,ensure_ascii=False,indent=2))

def atomic(source):
    a=decode(source);assert len(a)>=37*RATE
    gain=min(1,.88/float(np.max(np.abs(a))));a*=gain
    bounds=[('atomic_clip',0,7.8),('atomic_charge',7.8,24.4),('atomic_impact',24.4,37)]
    tracks=[];master=np.zeros((37*RATE,2),np.float32)
    for name,start,end in bounds:
        pcm=fade(cut(a,start,end),.002,.10 if end==37 else .002)
        tracks.append(export(name,pcm));place(master,pcm,start)
    wav(OUT/'Atomic_Sound_Remake.wav',master)
    metadata(source,'atomic',tracks,{'words_tick':8,'am_tick':34,'atomic_tick':74,'slash_start_tick':88,
      'slash_end_tick':98,'ink_tick':156,'dome_tick':488,'column_tick':500,'damage_start_tick_unchanged':540,
      'end_tick':740,'whiteout_tick':154,'ink_visible_tick':235*20/30,'ink_fade_tick':386*20/30,
      'ink_end_tick':398*20/30,'flash_end_tick':452*20/30,
      'reference_flash_frames':{'fps':30,'white_start':231,'ink_start':235,'ink_fade_start':386,'ink_end':398,'white_end_exclusive':452,'white_duration_frames':221,'ink_over_white_frames':163}},'2.4.2: White background and black fragments overlap using the measured reference frames. Original mix at 1.0x. Three chunks reset sync at 0, 7.8 and 24.4 seconds. Final 0.47-second source tail is trimmed to retain the 37-second skill.')

def heaven(source):
    a=decode(source);assert len(a)>7.4*RATE
    source_intervals=[]
    def source_piece(start,end):
        # Every reference sample is used at most once, including crossfade boundaries.
        assert all(end<=x['start'] or start>=x['end'] for x in source_intervals)
        source_intervals.append({'start':start,'end':end})
        return cut(a,start,end)
    # The previous mix still inserted a 1.75 s gap inside the source after 3.2 s.
    # Do not guess another speech boundary: the complete supplied audio stays contiguous.
    master=np.zeros((round(12.15*RATE),2),np.float32)
    opening=source_piece(0,7.4)
    place(master,fade(opening,.002,.03),0)
    place(master,fade(procedural_ambience(1.70,'wind',1481),.10,.10),7.30)
    # A new, voice-free final impact at global 9 s extends the slow insertion.
    impact=procedural_ambience(2.90,'rumble',2603)
    t=np.arange(len(impact))/RATE
    impact*=np.exp(-t*1.25)[:,None]*1.7
    impact+= (.12*np.sin(2*np.pi*(52*t-4*t*t))*np.exp(-t*3))[:,None]
    place(master,fade(impact,.005,.20),8.95)
    gain=min(1,.84/float(np.max(np.abs(master))));master*=gain
    tracks=[export('heaven_full',master)]
    reference=cut(a,.01,7.25)*gain;offset=round(.01*RATE)
    corr=float(np.corrcoef(reference.ravel(),master[offset:offset+len(reference)].ravel())[0,1]);assert corr>.999
    direct=[{'phase':'entire_reference_uninterrupted','source_seconds':[.01,7.25],'sample_correlation':corr}]
    encoded_master=np.zeros((round(12.2*RATE),2),np.float32)
    place(encoded_master,decode(ASSETS/'sounds/heaven_full.ogg'),.05)
    repeat_checks=chant_repeat_check(a,encoded_master)
    audible=np.zeros_like(encoded_master);place(audible,master,.05)
    wav(OUT/'Heaven_Sound_Remake.wav',audible)
    metadata(source,'heaven',tracks,{'charge_tick':1,'contact_tick':100,'release_tick':180,'camera_end_tick':244,
        'complete_opening_source_seconds':[0,7.4],'complete_opening_playback_seconds':[.05,7.45],
        'normal_speed_source_checks':direct,'source_intervals_used_once':source_intervals,
        'chant_repeat_checks':repeat_checks,'ambience_source':'procedural noise only; no source audio'},
        '2.5.0: Entire reference 0--7.4 s plays ONCE at 1.0x with no internal cuts, silence inserts, delayed dialogue or camera-triggered restarts. Only voice-free procedural ambience and final impact extend the master to 12.15 s. Reference sound effects remain in their original uninterrupted positions.')

def atomic_remake_260(source):
    """Keep the whole reference at 1x; add a new voice-free onset, without replaying dialogue."""
    a=decode(source);assert len(a)>=37*RATE
    master=fade(cut(a,0,37),.002,.12)*.83
    onset=7.7
    impact=procedural_ambience(2.3,'rumble',2607)
    t=np.arange(len(impact))/RATE
    impact*=np.exp(-t*2.4)[:,None]*2.8
    impact+=(.20*np.sin(2*np.pi*(58*t-6*t*t))*np.exp(-t*4))[:,None]
    place(master,fade(impact,.003,.12),onset)
    # Sustained power under the uncut music/effects, fading with horizontal contraction.
    bed=procedural_ambience(37-onset,'wind',2608)*.34
    t=np.arange(len(bed))/RATE
    bed*= (.7+.3*np.sin(t*2*np.pi*2)**2)[:,None]
    bed*= (1-np.clip((t+onset-33)/4,0,1))[:,None]
    place(master,fade(bed,.08,.08),onset)
    gain=min(1,.85/float(np.max(np.abs(master))));master*=gain
    tracks=[export('atomic_full',master)];wav(OUT/'Atomic_Sound_Remake.wav',master)
    original=cut(a,.01,7.65)*.83*gain
    corr=float(np.corrcoef(original.ravel(),master[round(.01*RATE):round(7.65*RATE)].ravel())[0,1]);assert corr>.999
    metadata(source,'atomic',tracks,{'words_tick':8,'am_tick':34,'atomic_tick':74,'slash_start_tick':88,
        'slash_end_tick':98,'whiteout_tick':154,'flash_end_tick':452*20/30,
        'column_and_impact_tick':154,'damage_start_tick':154,'damage_interval_ticks':20,
        'shrink_start_tick':660,'end_tick':740,'white_only':True,'fixed_column_height':True,
        'opening_sample_correlation':corr,'source_contiguous_seconds':[0,37],
        'reference_flash_frames':{'fps':30,'white_start':231,'white_end_exclusive':452,'white_duration_frames':221}},
        '2.6.0: Original full 37 s mix remains contiguous at 1x. A voice-free impact is added at 7.7 s with the white exposure, column and damage onset. No delayed 24.4 s spawn, no sound handoffs, no reused speech. Original later impact reinforces the already active column.')
