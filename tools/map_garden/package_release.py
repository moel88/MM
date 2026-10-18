from pathlib import Path
import json,zipfile,hashlib,os,xml.etree.ElementTree as ET,shutil
ROOT=Path(__file__).resolve().parents[2];WORK=ROOT.parent;B=ROOT/'build/map-garden';OUT=WORK/'deliverables';OUT.mkdir(exist_ok=True)
VERSION='2.14.0'
def copy(src,dst):
 raw=Path(src).read_bytes()
 with Path(dst).open('wb') as f:f.write(raw);f.flush();os.fsync(f.fileno())
 assert Path(dst).read_bytes()==raw
manifest=json.loads((B/'verification/manifest.json').read_text())
proof=B/'verification/native'
assert (proof/'client-result.txt').read_text().startswith('PASS 2.14.0:')
assert 'mismatches=0' in (proof/'server-result.txt').read_text()
results=[ET.parse(p).getroot() for p in (ROOT/'build/test-results/test').glob('TEST-*.xml')]
tests=sum(int(x.attrib['tests']) for x in results);assert tests>=152
assert sum(int(x.attrib['failures'])+int(x.attrib['errors']) for x in results)==0
verification={'version':VERSION,'unit_tests':tests,'failed_tests':0,'map':manifest,'actual_client':(proof/'client-result.txt').read_text(),'live_patch':(proof/'server-result.txt').read_text(),'phone_tested':False,'preview':'Actual Minecraft screenshots, no external shaderpack or generated image','renderer_crash_fix_preserved':True}
(B/'verification/release.json').write_text(json.dumps(verification,ensure_ascii=False,indent=2))
jar=ROOT/f'build/libs/botand-{VERSION}.jar'
with zipfile.ZipFile(jar) as z:
 assert json.loads(z.read('fabric.mod.json'))['version']==VERSION
 assert not any('botandverify' in n or 'GardenClientCapture' in n for n in z.namelist())
 assert z.read('data/celestialfall/map/azure_garden.bgp.gz')==(ROOT/'src/main/resources/data/celestialfall/map/azure_garden.bgp.gz').read_bytes()
 for name in ['CinemaSupport.class','CinemaCompat.class','MapDecoration.class','GardenPatch.class']:
  assert any(n.endswith('/'+name) for n in z.namelist())
copy(jar,OUT/jar.name)
for src,dest in [('garden-1.png','Botand_Azure_Garden_Day.png'),('garden-6.png','Botand_Azure_Garden_Night.png'),('garden-0.png','Botand_Azure_Garden_Overview.png'),('garden-8.png','Botand_F3_HUD.png')]:copy(proof/src,OUT/dest)
mapzip=OUT/'Botand-Azure-Garden-1.20.1-v1.2.zip'
with zipfile.ZipFile(mapzip,'w',zipfile.ZIP_DEFLATED,compresslevel=7) as z:
 world=B/'Botand_Azure_Garden_1.20.1'
 for p in sorted(world.rglob('*')):
  if p.is_file():z.write(p,str(p.relative_to(B)))
 z.write(ROOT/'MAP_GARDEN_KO.md','INSTALL_KO.md')
 for p in proof.glob('*.png'):z.write(p,'preview/'+p.name)
 for name in ['manifest.json','palette.json','reference-layout.json','minecraft-reader.json','release.json']:
  z.write(B/'verification'/name,'verification/'+name)
 for p in (ROOT/'tools/map_garden').glob('*'):
  if p.is_file() and p.suffix in ['.py','.java']:z.write(p,'source/tools/map_garden/'+p.name)
 z.write(B/'City_Third_Guide.png','Azure_Garden_Coordinate_Guide.png')
sourcezip=OUT/f'Botand-1.20.1-Fabric-v{VERSION}.zip'
with zipfile.ZipFile(OUT/'Botand-1.20.1-Fabric-v2.13.3.zip') as old,zipfile.ZipFile(sourcezip,'w',zipfile.ZIP_DEFLATED,compresslevel=7) as z:
 for name in ['mods/fabric-api-0.92.2+1.20.1.jar','CONFIG_EXAMPLE.json','licenses/Botand.txt','THIRD_PARTY_NOTICES.txt','licenses/Fabric-API.txt']:
  z.writestr(name,old.read(name))
 z.write(jar,'mods/'+jar.name);z.write(ROOT/'MAP_GARDEN_KO.md','INSTALL_KO.md')
 for p in proof.glob('*.png'):z.write(p,'preview/'+p.name)
 for name in ['client-result.txt','server-result.txt']:z.write(proof/name,'verification/'+name)
 z.write(B/'verification/release.json','verification/release.json');z.write(WORK/'map-mod-build.log','verification/build.log')
 for directory in ['src','tools','gradle']:
  for p in sorted((ROOT/directory).rglob('*')):
   if not p.is_file() or '__pycache__' in p.parts or p.suffix in ['.class','.jar','.pyc']:continue
   if p.name in ['Bomi_Stand_Mascot.glb','bomi_model_manifest.json','bomi_face_albedo.png']:continue
   if 'rework' in p.parts and not (p.name=='hourglass.py' or p.name.startswith(('Aurora_Hourglass','hourglass_'))):continue
   z.write(p,'source/celestial-fall/'+str(p.relative_to(ROOT)))
 for name in ['build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','BUILD_WINDOWS.bat','LICENSE','README.md','TITLE_CINEMA.md','MAP_GARDEN_KO.md','CONFIG_EXAMPLE.json','TITLE_ASSETS.txt']:
  p=ROOT/name
  if p.exists():z.write(p,'source/celestial-fall/'+name)
 z.write(ROOT/'src/main/resources/assets/celestialfall/sounds/cinema/music_sources.json','licenses/USER_PROVIDED_MUSIC.json')
for p in [mapzip,sourcezip]:
 with p.open('rb') as f:os.fsync(f.fileno())
 with zipfile.ZipFile(p) as z:assert z.testzip() is None
files=[OUT/jar.name,mapzip,sourcezip,*[OUT/n for n in ['Botand_Azure_Garden_Day.png','Botand_Azure_Garden_Night.png','Botand_Azure_Garden_Overview.png','Botand_F3_HUD.png']]]
report={p.name:{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in files}
(OUT/'Botand_2.14.0_checksums.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
