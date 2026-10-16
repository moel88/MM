"""Actual H cutscene plus original comparison, aligned on first visible landing.
Usage: finisher_preview.py CINEMATIC_PROOF ORIGINAL_VIDEO OUTPUT_MP4
Requires ffmpeg, ffprobe, numpy. Does not synthesize gameplay frames.
"""
from pathlib import Path
import csv, json, math, os, subprocess, sys
import numpy as np

proof, reference, output = map(Path, sys.argv[1:4])
recorded = float((proof/'video-start.txt').read_text())
rows = list(csv.DictReader((proof/'frames.csv').open()))
landed = next(r for r in rows if 2.737 <= float(r['cast_seconds']) < 2.85)
scan = math.floor((float(landed['epoch_seconds'])-recorded-.3)*30)/30
native = proof/'native-session.mp4'
raw = subprocess.check_output(['ffmpeg', '-v', 'error', '-ss', str(scan), '-i', str(native),
    '-t', '0.6', '-vf', 'scale=640:360,fps=30', '-pix_fmt', 'rgb24', '-f', 'rawvideo', '-'])
frames = np.frombuffer(raw, dtype=np.uint8).reshape(-1,360,640,3).astype(float)
r,g,b = (frames[:,100:340,:,i] for i in range(3))
counts = ((r>25)&(g>40)&(r<190)&(b<110)&(g>r*1.15)&(g>b*1.5)).sum((1,2))
first = next(i for i,c in enumerate(counts) if c>100)
assert first>0 and counts[first-1]<100, 'First visible landing must be bracketed'
native_land = scan + first/30
reference_land = 126.566667
start = native_land - 2.737
temp = proof/'preview-parts'
temp.mkdir()
font = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
common = ['-an','-r','30','-c:v','libx264','-preset','veryfast','-crf','20',
          '-pix_fmt','yuv420p','-threads','2','-movflags','+faststart']
def validate(path, duration):
    info = json.loads(subprocess.check_output(['ffprobe','-v','error','-show_entries',
        'format=duration','-of','json',str(path)]))
    assert abs(float(info['format']['duration'])-duration)<.12, (path,info,duration)
full = temp/'full.mp4'
label = ("drawbox=x=0:y=0:w=iw:h=66:color=0x10101e@0.88:t=fill,"
    f"drawtext=fontfile={font}:text='H - Opposite camera side, legs together':x=24:y=10:fontsize=23:fontcolor=white,"
    f"drawtext=fontfile={font}:text='BOTAND 2.29.5 | Actual gameplay | No audio':x=24:y=40:fontsize=16:fontcolor=0xb9b0cf")
subprocess.run(['ffmpeg','-v','error','-ss',str(start),'-i',str(native),'-vf',
    'trim=duration=6,setpts=PTS-STARTPTS,fps=30,'+label,*common,'-y',str(full)],check=True)
validate(full,6)
compare = temp/'comparison.mp4'
filters = ("[0:v]trim=duration=2.4,setpts=2*(PTS-STARTPTS),fps=30,scale=640:360,setsar=1[l];"
    "[1:v]trim=duration=2.4,setpts=2*(PTS-STARTPTS),fps=30,scale=640:360,setsar=1[r];"
    "[l][r]hstack,pad=1280:720:0:180:color=0x10101e,"
    f"drawtext=fontfile={font}:text='H landing and finisher | Both sides 0.5x':x=24:y=28:fontsize=28:fontcolor=white,"
    f"drawtext=fontfile={font}:text='ORIGINAL REFERENCE':x=24:y=142:fontsize=20:fontcolor=0xcbadff,"
    f"drawtext=fontfile={font}:text='BOTAND 2.29.5':x=664:y=142:fontsize=20:fontcolor=0xcbadff,"
    f"drawtext=fontfile={font}:text='First visible landing aligned. Same playback speed. No audio.':x=24:y=588:fontsize=20:fontcolor=0xb9b0cf[v]")
subprocess.run(['ffmpeg','-v','error','-ss',str(reference_land-.15),'-i',str(reference),
    '-ss',str(native_land-.15),'-i',str(native),'-filter_complex',filters,'-map','[v]',
    *common,'-y',str(compare)],check=True)
validate(compare,4.8)
concat = temp/'concat.txt'
concat.write_text(''.join("file '"+str(p.resolve())+"'\n" for p in [full,compare]))
subprocess.run(['ffmpeg','-v','error','-f','concat','-safe','0','-i',str(concat),'-c','copy',
    '-movflags','+faststart','-y',str(output)],check=True)
validate(output,10.8)
alignment = {'reference_land':reference_land,'native_land':native_land,'native_H_start':start,
    'comparison_speed':.5,'alignment':'First visible landing frame, 30 fps native / 60 fps original',
    'duration':10.8,'fps':30,'audio':False,'native_proof':str(proof.resolve()),
    'segments':[{'start':0,'end':6,'view':'actual cutscene'},
                {'start':6,'end':10.8,'view':'original and actual cutscene comparison'}]}
meta = proof/'preview-alignment.json'
meta.write_text(json.dumps(alignment,indent=2))
for p in [output,meta]:
    with p.open('rb') as f:os.fsync(f.fileno())
print(json.dumps(alignment,indent=2))
