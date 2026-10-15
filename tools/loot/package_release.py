from pathlib import Path
import json,zipfile,hashlib,os,xml.etree.ElementTree as ET
from PIL import Image
ROOT=Path(__file__).resolve().parents[2];WORK=ROOT.parent;OUT=WORK/'deliverables';OUT.mkdir(exist_ok=True)
VERSION='2.17.0';proof=Path((ROOT/'build/loot-proof-path.txt').read_text().strip())
assert (proof/'client-result.txt').read_text().startswith('PASS 2.17.0:')
assert not (proof/'client-error.txt').exists() and not (proof/'restart-error.txt').exists()
server=(proof/'server-result.txt').read_text();restart=(proof/'restart-result.txt').read_text()
for name in ['scheduled respawn advances once','last key frees slot for guaranteed reward','locked chest refuses missing key','full inventory preserves chest key and rewards','original chest replaced 10','wall blocks rewards','repeat claim rejected','loot disabled outside authorized map']:assert 'PASS '+name in server
for name in ['restart preserves exact deadline and cycle','loot progression persists after restart','keys persist after restart']:assert 'PASS '+name in restart
roots=[ET.parse(p).getroot() for p in (ROOT/'build/test-results/test').glob('TEST-*.xml')]
assert sum(int(p.attrib['failures'])+int(p.attrib['errors']) for p in roots)==0
assert sum(int(p.attrib['tests']) for p in roots)>=167
images=sorted(proof.glob('*.png'));assert len(images)==4
for p in images:
 with Image.open(p) as im:im.verify()
verification={'version':VERSION,'unit_tests':sum(int(p.attrib['tests']) for p in roots),'native_client':(proof/'client-result.txt').read_text(),'native_server':server,'actual_process_restart':restart,'screenshots':[p.name for p in images],'remote_multiplayer_tested':False,'mobile_device_tested':False}
(proof/'release.json').write_text(json.dumps(verification,ensure_ascii=False,indent=2))
def copy(src,dst):
 with Path(dst).open('wb') as f:f.write(Path(src).read_bytes());f.flush();os.fsync(f.fileno())
jar=ROOT/f'build/libs/botand-{VERSION}.jar'
with zipfile.ZipFile(jar) as z:
 assert z.testzip() is None
 assert json.loads(z.read('fabric.mod.json'))['version']==VERSION
 assert not any('botandverify' in n for n in z.namelist())
 assert b'intermediary' in z.read('celestialfall.accesswidener')
 for name in ['LootServer','LootState','LootWorld','FieldLootEntity','ChestKeyItem']:assert f'dev/sangwoo/celestialfall/loot/{name}.class' in z.namelist()
 assert json.loads(z.read('assets/celestialfall/models/item/chest_key.json'))['elements']
 with zipfile.ZipFile(OUT/'botand-2.16.1.jar') as old:
  for n in old.namelist():
   if n.startswith(('data/celestialfall/map/','assets/celestialfall/sounds/','assets/celestialfall/textures/')):assert z.read(n)==old.read(n),n
copy(jar,OUT/jar.name)
for p in images:copy(p,OUT/p.name)
readme=OUT/f'Botand_{VERSION}_사용법.md'
readme.write_text((ROOT/'LOOT_KO.md').read_text()+'\n\n---\n\n'+(ROOT/'COMMERCE_KO.md').read_text())
with readme.open('rb') as f:os.fsync(f.fileno())
with zipfile.ZipFile(OUT/'Botand-1.20.1-Fabric-v2.16.1.zip') as old:
 entries={n:old.read(n) for n in old.namelist() if not n.endswith('/') and not n.startswith(('verification/','mods/botand-')) and n!='INSTALL_KO.md'}
entries['mods/'+jar.name]=jar.read_bytes();entries['INSTALL_KO.md']=readme.read_bytes()
for directory in ['src','tools','gradle']:
 for p in sorted((ROOT/directory).rglob('*')):
  if not p.is_file() or '__pycache__' in p.parts or p.suffix in ['.class','.pyc']:continue
  if p.suffix=='.jar' and p.name!='gradle-wrapper.jar':continue
  if p.name in ['Bomi_Stand_Mascot.glb','bomi_model_manifest.json','bomi_face_albedo.png']:continue
  if 'rework' in p.parts and not (p.name=='hourglass.py' or p.name.startswith(('Aurora_Hourglass','hourglass_'))):continue
  entries['source/celestial-fall/'+str(p.relative_to(ROOT))]=p.read_bytes()
for name in ['build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','BUILD_WINDOWS.bat','LICENSE','README.md','TITLE_CINEMA.md','MAP_GARDEN_KO.md','FLOOR_FIX_KO.md','VENUES_KO.md','COMMERCE_KO.md','LOOT_KO.md','CONFIG_EXAMPLE.json','TITLE_ASSETS.txt']:
 p=ROOT/name
 if p.exists():entries['source/celestial-fall/'+name]=p.read_bytes()
for p in images:entries['preview/'+p.name]=p.read_bytes()
for name in ['client-result.txt','server-result.txt','restart-result.txt','release.json']:entries['verification/'+name]=(proof/name).read_bytes()
entries['verification/build.log']=(WORK/'npc-build.log').read_bytes()
for p in (ROOT/'build/test-results/test').glob('TEST-*.xml'):entries['verification/unit-tests/'+p.name]=p.read_bytes()
source=OUT/f'Botand-1.20.1-Fabric-v{VERSION}.zip'
with zipfile.ZipFile(source,'w',zipfile.ZIP_DEFLATED,compresslevel=7) as z:
 for name,data in entries.items():z.writestr(name,data)
with source.open('rb') as f:os.fsync(f.fileno())
with zipfile.ZipFile(source) as z:assert z.testzip() is None
files=[OUT/jar.name,source,readme]+[OUT/p.name for p in images]
report={p.name:{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in files}
(OUT/f'Botand_{VERSION}_checksums.json').write_text(json.dumps(report,ensure_ascii=False,indent=2));print(json.dumps(report,ensure_ascii=False,indent=2))
