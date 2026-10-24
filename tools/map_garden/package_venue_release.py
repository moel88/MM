from pathlib import Path
import json,zipfile,hashlib,os,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2];WORK=ROOT.parent;B=ROOT/'build/map-garden';OUT=WORK/'deliverables';OUT.mkdir(exist_ok=True)
VERSION='2.15.0';proof=Path((ROOT/'build/venue-proof-path.txt').read_text().strip())
assert (proof/'client-result.txt').read_text().startswith('PASS 2.15.0:')
assert 'mismatches=0' in (proof/'server-result.txt').read_text()
assert not (proof/'client-error.txt').exists()
tests=[ET.parse(p).getroot() for p in (ROOT/'build/test-results/test').glob('TEST-*.xml')]
assert sum(int(p.attrib['failures'])+int(p.attrib['errors']) for p in tests)==0
assert sum(int(p.attrib['tests']) for p in tests)>=154
manifest=json.loads((B/'verification/manifest.json').read_text());assert manifest['repaired_floor_inserts']==2355
from PIL import Image
valid_images=[]
for image_path in proof.glob('*.png'):
 try:
  with Image.open(image_path) as im:im.verify()
  valid_images.append(image_path)
 except Exception:pass
assert len(valid_images)>=4
verification={'valid_screenshots':[p.name for p in valid_images],'screenshot_note':'Four screenshots verified; empty optional evening export excluded.', 'version':VERSION,'unit_tests':sum(int(p.attrib['tests']) for p in tests),'map':manifest,'native_client':(proof/'client-result.txt').read_text(),'native_server':(proof/'server-result.txt').read_text(),'phone_tested':False}
(proof/'release.json').write_text(json.dumps(verification,ensure_ascii=False,indent=2))
def copy(src,dst):
 with Path(dst).open('wb') as f:f.write(Path(src).read_bytes());f.flush();os.fsync(f.fileno())
jar=ROOT/f'build/libs/botand-{VERSION}.jar'
with zipfile.ZipFile(jar) as z:
 assert json.loads(z.read('fabric.mod.json'))['version']==VERSION
 assert not any('botandverify' in n for n in z.namelist())
 for n in ['azure_garden.bgp.gz','azure_garden_floor_fix.bgp.gz','azure_venues.bgp.gz','azure_venues_original.bgp.gz']:assert z.read('data/celestialfall/map/'+n)==(ROOT/'src/main/resources/data/celestialfall/map'/n).read_bytes()
 assert any(n.endswith('/VenueRenovation.class') for n in z.namelist())
copy(jar,OUT/jar.name)
for image_path in valid_images:copy(image_path,OUT/('Botand_'+image_path.name))
mapzip=OUT/'Botand-Azure-Garden-1.20.1-v1.3.zip'
with zipfile.ZipFile(mapzip,'w',zipfile.ZIP_DEFLATED,compresslevel=7) as z:
 for p in sorted((B/'Botand_Azure_Garden_1.20.1').rglob('*')):
  if p.is_file():z.write(p,str(p.relative_to(B)))
 z.write(ROOT/'VENUES_KO.md','INSTALL_KO.md');z.write(ROOT/'MAP_GARDEN_KO.md','MAP_GARDEN_KO.md')
 for p in valid_images:z.write(p,'preview/'+p.name)
 z.write(proof/'release.json','verification/release.json')
 z.write(B/'City_Third_Guide.png','Azure_Garden_Coordinate_Guide.png')
 for p in (ROOT/'tools/map_garden').glob('*'):
  if p.is_file() and p.suffix in ['.py','.java','.gz']:z.write(p,'source/tools/map_garden/'+p.name)
sourcezip=OUT/f'Botand-1.20.1-Fabric-v{VERSION}.zip'
with zipfile.ZipFile(WORK/'shop_input/Botand-1.20.1-Fabric-v2.14.1.zip') as old,zipfile.ZipFile(sourcezip,'w',zipfile.ZIP_DEFLATED,compresslevel=7) as z:
 for name in old.namelist():
  if name.startswith('licenses/') or name in ['mods/fabric-api-0.92.2+1.20.1.jar','CONFIG_EXAMPLE.json','THIRD_PARTY_NOTICES.txt']:z.writestr(name,old.read(name))
 z.write(jar,'mods/'+jar.name);z.write(ROOT/'VENUES_KO.md','INSTALL_KO.md')
 for p in valid_images:z.write(p,'preview/'+p.name)
 for name in ['client-result.txt','server-result.txt','release.json']:z.write(proof/name,'verification/'+name)
 z.write(WORK/'shop-build.log','verification/build.log')
 for directory in ['src','tools','gradle']:
  for p in sorted((ROOT/directory).rglob('*')):
   if not p.is_file() or '__pycache__' in p.parts or p.suffix in ['.class','.pyc']:continue
   if p.suffix=='.jar' and p.name!='gradle-wrapper.jar':continue
   if p.name in ['Bomi_Stand_Mascot.glb','bomi_model_manifest.json','bomi_face_albedo.png']:continue
   if 'rework' in p.parts and not (p.name=='hourglass.py' or p.name.startswith(('Aurora_Hourglass','hourglass_'))):continue
   z.write(p,'source/celestial-fall/'+str(p.relative_to(ROOT)))
 for name in ['build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','BUILD_WINDOWS.bat','LICENSE','README.md','TITLE_CINEMA.md','MAP_GARDEN_KO.md','FLOOR_FIX_KO.md','VENUES_KO.md','CONFIG_EXAMPLE.json','TITLE_ASSETS.txt']:
  p=ROOT/name
  if p.exists():z.write(p,'source/celestial-fall/'+name)
for p in [mapzip,sourcezip]:
 with p.open('rb') as f:os.fsync(f.fileno())
 with zipfile.ZipFile(p) as z:assert z.testzip() is None
files=[OUT/jar.name,mapzip,sourcezip]+[OUT/('Botand_'+p.name) for p in valid_images]
report={p.name:{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in files}
(OUT/'Botand_2.15.0_checksums.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
