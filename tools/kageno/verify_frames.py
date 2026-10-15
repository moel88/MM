"""Inspect real native framebuffers and frame clocks; never synthesize verification images."""
from pathlib import Path
import csv,json
import numpy as np
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
p=Path((ROOT/'build/kageno-proof-path.txt').read_text().strip())
rows=list(csv.DictReader((p/'h-camera-frames.csv').open()))
gaps=[];rewinds=0
for a,b in zip(rows,rows[1:]):
 before,after=float(a['age']),float(b['age'])
 if after<before-20:continue # Separate caster/victim runs.
 rewinds+=int(after+.001<before)
 gaps.append((int(b['frame_ns'])-int(a['frame_ns']))/1e6)
assert len(rows)>150 and rewinds==0
stats={'rendered_frames':len(rows),'p50_frame_ms':float(np.percentile(gaps,50)),'p95_frame_ms':float(np.percentile(gaps,95)),'max_frame_ms':max(gaps),'renderer':'software OpenGL with live recording; not user GPU benchmark','clock_rewind_count':rewinds}
(p/'h-camera-performance.json').write_text(json.dumps(stats,indent=2))
gray=[]
for f in sorted(p.glob('*Monochrome.png')):
 pixels=np.asarray(Image.open(f).convert('RGB')).astype(np.int16);spread=pixels.max(axis=2)-pixels.min(axis=2)
 result={'file':f.name,'pixels':int(spread.size),'maximum_rgb_difference':int(spread.max()),'pixels_outside_grayscale_tolerance':int((spread>1).sum())}
 assert result['pixels_outside_grayscale_tolerance']==0,f
 gray.append(result)
assert len(gray)>=3
(p/'framebuffer-gray.json').write_text(json.dumps(gray,indent=2))
print(json.dumps({'camera':stats,'grayscale_frames':len(gray)},indent=2))
