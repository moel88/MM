"""Export Minecraft's native model, never redesign the supplied Minecraft skin."""
from pathlib import Path
import subprocess,os,hashlib,json
from PIL import Image
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[1]
RES=ROOT/'src/main/resources/assets/celestialfall';OUT=RES/'cinema'
skin=HERE/'assets/bomi_skin.png'
assert Image.open(skin).size==(64,64)
cp=(ROOT/'build/test-classpath.txt').read_text().strip()
classes=ROOT/'build/vanilla-model-export';classes.mkdir(parents=True,exist_ok=True)
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-proc:none','-cp',cp,'-d',str(classes),str(HERE/'ExportVanillaBomi.java')],check=True,cwd=ROOT)
subprocess.run(['java','-cp',str(classes)+os.pathsep+cp,'ExportVanillaBomi',str(OUT)],check=True,cwd=ROOT)
target=RES/'textures/cinema/bomi.png'
with target.open('wb') as f:f.write(skin.read_bytes());f.flush();os.fsync(f.fileno())
meta=json.loads((OUT/'bomi.native.json').read_text());meta['skin_sha256']=hashlib.sha256(skin.read_bytes()).hexdigest()
(OUT/'bomi.native.json').write_text(json.dumps(meta,indent=2)+'\n')
print('Original skin copied without resampling or pixel edits:',meta['skin_sha256'])
