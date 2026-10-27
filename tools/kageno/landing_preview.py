"""Actual H capture, fixed-camera direction check, original comparison.
Usage: landing_preview.py CINEMATIC_PROOF ORIGINAL_VIDEO FIXED_PROOF OUTPUT_MP4
Requires ffmpeg, ffprobe and numpy. Frames are actual game/reference footage.
"""
from pathlib import Path
import csv,json,math,os,subprocess,sys
import numpy as np
proof,reference,fixed,output=map(Path,sys.argv[1:5])

def landing_anchor(p):
    recorded=float((p/'video-start.txt').read_text())
    rows=list(csv.DictReader((p/'frames.csv').open()))
    landed=next(r for r in rows if 2.737<=float(r['cast_seconds'])<2.85)
    scan=math.floor((float(landed['epoch_seconds'])-recorded-.3)*30)/30
    raw=subprocess.check_output(['ffmpeg','-v','error','-ss',str(scan),'-i',str(p/'native-session.mp4'),
        '-t','0.6','-vf','scale=640:360,fps=30','-pix_fmt','rgb24','-f','rawvideo','-'])
    frames=np.frombuffer(raw,dtype=np.uint8).reshape(-1,360,640,3).astype(float)
    r,g,b=(frames[:,100:340,:,i] for i in range(3))
    counts=((r>25)&(g>40)&(r<190)&(b<110)&(g>r*1.15)&(g>b*1.5)).sum((1,2))
    first=next(i for i,c in enumerate(counts) if c>100)
    assert first>0 and counts[first-1]<100,'First visible landing must be bracketed'
    return scan+first/30

native_land=landing_anchor(proof);fixed_land=landing_anchor(fixed)
reference_land=126.566667 # Manually checked first landing frame, supplied 60fps original.
start=native_land-2.737;ground_start=native_land-.15;reference_start=reference_land-.15
native=proof/'native-session.mp4';temp=output.parent/'preview_parts_2294';temp.mkdir(exist_ok=True)
font='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
common=['-an','-r','30','-c:v','libx264','-preset','veryfast','-crf','20','-pix_fmt','yuv420p','-threads','2','-movflags','+faststart']
clips=[]
def validate(path,duration):
    info=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_entries','format=duration','-of','json',str(path)]))
    assert abs(float(info['format']['duration'])-duration)<.12,(path,info,duration)
def label(title,subtitle):
    return (f"drawbox=x=0:y=0:w=iw:h=66:color=0x10101e@0.88:t=fill,"
        f"drawtext=fontfile={font}:text='{title}':x=24:y=10:fontsize=23:fontcolor=white,"
        f"drawtext=fontfile={font}:text='{subtitle}':x=24:y=40:fontsize=16:fontcolor=0xb9b0cf")

for i,(src,begin,duration,speed,title,subtitle) in enumerate([
    (native,start,6.0,1,'H - Backward skid, hand brake, forward slash','BOTAND 2.29.4 | Actual cutscene camera | No audio'),
    (fixed/'native-session.mp4',fixed_land-.12,1.1,2,'Fixed camera check | Yellow to blue = 5 blocks | 0.5x',
     'Faces left - slides backward to the right - brakes - drives forward to the left')
]):
    out=temp/f'{i}.mp4';vf=f'trim=duration={duration},setpts={speed}*(PTS-STARTPTS),fps=30,'+label(title,subtitle)
    subprocess.run(['ffmpeg','-v','error','-ss',str(begin),'-i',str(src),'-vf',vf,*common,'-y',str(out)],check=True)
    validate(out,duration*speed);clips.append(out)
out=temp/'comparison.mp4'
filters=("[0:v]trim=duration=2.4,setpts=2*(PTS-STARTPTS),fps=30,scale=640:360,setsar=1[l];"
    "[1:v]trim=duration=2.4,setpts=2*(PTS-STARTPTS),fps=30,scale=640:360,setsar=1[r];"
    "[l][r]hstack,pad=1280:720:0:180:color=0x10101e,"
    f"drawtext=fontfile={font}:text='H landing | Both sides 0.5x':x=24:y=28:fontsize=30:fontcolor=white,"
    f"drawtext=fontfile={font}:text='ORIGINAL REFERENCE':x=24:y=142:fontsize=20:fontcolor=0xcbadff,"
    f"drawtext=fontfile={font}:text='BOTAND 2.29.4':x=664:y=142:fontsize=20:fontcolor=0xcbadff,"
    f"drawtext=fontfile={font}:text='First visible landing aligned. Same playback speed. No audio.':x=24:y=588:fontsize=20:fontcolor=0xb9b0cf[v]")
subprocess.run(['ffmpeg','-v','error','-ss',str(reference_start),'-i',str(reference),'-ss',str(ground_start),'-i',str(native),'-filter_complex',filters,'-map','[v]',*common,'-y',str(out)],check=True)
validate(out,4.8);clips.append(out)
concat=temp/'concat.txt';concat.write_text(''.join("file '"+str(p.resolve())+"'\n" for p in clips))
subprocess.run(['ffmpeg','-v','error','-f','concat','-safe','0','-i',str(concat),'-c','copy','-movflags','+faststart','-y',str(output)],check=True)
validate(output,13.0)
alignment={'reference_land':reference_land,'native_land':native_land,'fixed_land':fixed_land,
    'native_H_start':start,'native_ground_start':ground_start,'reference_ground_start':reference_start,
    'comparison_speed':.5,'alignment':'First visible landing frame, 30 fps native / 60 fps original',
    'duration':13.0,'fps':30,'audio':False,'native_proof':str(proof.resolve()),'fixed_proof':str(fixed.resolve()),
    'segments':[{'start':0,'end':6,'view':'actual cutscene'},{'start':6,'end':8.2,'view':'fixed verification camera'},{'start':8.2,'end':13,'view':'original and actual cutscene comparison'}]}
meta=output.parent/'preview_alignment_2294.json';meta.write_text(json.dumps(alignment,indent=2))
for p in [output,meta]:
    with p.open('rb') as f:os.fsync(f.fileno())
print(json.dumps(alignment,indent=2));print(output,output.stat().st_size)
