"""Original procedural soft cloud sprite. Requires Pillow and NumPy; no external image assets."""
from pathlib import Path
from PIL import Image, ImageFilter
import numpy as np

rng=np.random.default_rng(481516)
n=128
y,x=np.mgrid[-1:1:complex(n),-1:1:complex(n)]
noise=np.zeros((n,n))
for size,weight in [(5,.50),(10,.28),(20,.15),(40,.07)]:
    layer=Image.fromarray((rng.random((size,size))*255).astype('uint8')).resize((n,n),Image.Resampling.BICUBIC)
    noise+=np.asarray(layer)/255*weight
dist=(x*x+y*y)**.5
alpha=np.clip(1-dist,0,1)**1.35*np.clip(.25+noise*1.45,0,1)
rgba=np.full((n,n,4),255,dtype=np.uint8)
rgba[:,:,3]=(alpha*255).astype('uint8')
out=Path(__file__).resolve().parents[1]/'src/main/resources/assets/celestialfall/textures/effect/smoke.png'
out.parent.mkdir(parents=True,exist_ok=True)
Image.fromarray(rgba).filter(ImageFilter.GaussianBlur(.6)).save(out)
print('Generated original smoke sprite:',out.name)
